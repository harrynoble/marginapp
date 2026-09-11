package com.margin.app.domain.usecase

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.GoalRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.model.BlockStateMachine
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
import com.margin.app.domain.planner.CandidateBuilder
import com.margin.app.domain.planner.DayMode
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.planner.Diagnostic
import com.margin.app.domain.planner.ExamPressure
import com.margin.app.domain.planner.PlacedBlock
import com.margin.app.domain.planner.PlanDiff
import com.margin.app.domain.planner.PlannedDay
import com.margin.app.domain.planner.PlannerInput
import com.margin.app.domain.planner.PlanningContext
import com.margin.app.domain.planner.WorkCandidate
import com.margin.app.domain.planner.WorkloadAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

data class PlanResult(
    val date: LocalDate,
    val blocks: List<ScheduleBlock>,
    val diff: PlanDiff,
    val diagnostics: List<Diagnostic>,
    val headline: String,
    /** Work that did not fit, with what was left of it. */
    val unplaced: List<WorkCandidate> = emptyList(),
    val notes: List<String> = emptyList(),
    val mode: DayMode = DayMode.NORMAL,
    val exam: ExamPressure = ExamPressure.NONE,
)

/**
 * Turns the database into a [PlanningContext], lets [CandidateBuilder] decide what the day
 * should hold, lets [DayPlanner] decide where it goes, and writes the result back.
 *
 * Replans are serialised: a notification action and a tap in the app can arrive at the same
 * moment, and two replans interleaving would leave a day half one plan and half the other.
 */
class PlanningService(
    private val timetableRepository: TimetableRepository,
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dayRepository: DayRepository,
    private val goalRepository: GoalRepository,
    private val examRepository: ExamRepository,
    private val planner: DayPlanner = DayPlanner(),
) {

    private val mutex = Mutex()

    /** Generates the plan only if the day has none yet. Cheap to call on every app open. */
    suspend fun ensurePlan(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): PlanResult? {
        val existing = scheduleRepository.blocksFor(date)
        if (existing.isNotEmpty()) return null
        return replan(date, now)
    }

    /**
     * Replans today only if the plan has gone stale: a planned session whose whole slot has
     * passed without starting. That marks it missed and puts its work back where it fits,
     * without the user having to do anything.
     */
    suspend fun refreshIfStale(now: LocalDateTime = LocalDateTime.now()): PlanResult? {
        val today = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val stale = scheduleRepository.blocksFor(today).any {
            it.status == BlockStatus.PLANNED && it.type.isWork && it.end <= nowMinute
        }
        return if (stale) replan(today, now) else null
    }

    /**
     * Rebuilds the part of [date] that is still ahead. Completed, skipped, running, pinned
     * and past blocks are carried through untouched; only what is still planned and still to
     * come can move.
     */
    suspend fun replan(
        date: LocalDate,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult = mutex.withLock {
        withContext(Dispatchers.Default) { rebuild(date, now) }
    }

    private suspend fun rebuild(date: LocalDate, now: LocalDateTime): PlanResult {
        val prefs = preferencesRepository.current()
        val plannerPrefs = prefs.toPlannerPreferences(date.toEpochDay())

        val isToday = date == now.toLocalDate()
        val nowMinute = if (isToday) MarginTime.nowMinute(now) else null
        val fromMinute = nowMinute?.let { plannerPrefs.roundUp(it) } ?: 0

        // Sessions whose whole slot has passed without being started are recorded as missed,
        // not silently dropped. Their work is still wanted, so the plan below places it again.
        if (nowMinute != null) markMissed(date, nowMinute)

        val previous = scheduleRepository.blocksFor(date)
        val settled = previous.filter { block ->
            BlockStateMachine.isSettled(block) || (nowMinute != null && block.end <= fromMinute)
        }
        val occupying = settled.filter { BlockStateMachine.occupiesTime(it) }

        val tasks = taskRepository.activeTasks()
        val completions = scheduleRepository.completions(date.minusDays(HISTORY_DAYS), date)
        val weekStart = date.minusDays(6)
        val weekCompletions = completions.filter { !it.date.isBefore(weekStart) }

        val context = PlanningContext(
            date = date,
            prefs = plannerPrefs,
            settings = prefs.toPlanSettings(),
            dayState = dayRepository.state(date),
            nowMinute = nowMinute,
            classes = timetableRepository.entriesFor(date),
            weeklyEntries = timetableRepository.weeklyEntries(),
            routines = timetableRepository.routines(),
            events = taskRepository.eventsOn(date),
            subjects = timetableRepository.subjects(),
            tasks = tasks,
            projects = taskRepository.projects(),
            learningGoals = goalRepository.activeGoals(),
            exams = examRepository.upcoming(date),
            completions = completions,
            skips = scheduleRepository.skips(date.minusDays(SKIP_DAYS), date),
            deferred = dayRepository.deferredFor(date),
            settled = settled,
            taskCapacities = estimateCapacities(date, tasks),
            weekBuildByProject = weekCompletions
                .filter { it.type == BlockType.BUILD && it.projectId != null }
                .groupBy { it.projectId!! }
                .mapValues { (_, list) -> list.sumOf { it.minutes } },
            weekLearningByGoal = weekCompletions
                .filter { it.type == BlockType.LEARN && it.learningGoalId != null }
                .groupBy { it.learningGoalId!! }
                .mapValues { (_, list) -> list.sumOf { it.minutes } },
        )

        val built = CandidateBuilder.build(context)
        val planned: PlannedDay = planner.plan(
            PlannerInput(
                date = date,
                prefs = built.prefs,
                commitments = built.commitments,
                work = built.work,
                quotas = built.quotas,
                nowMinute = nowMinute,
                settled = occupying.map { it.toPlaced(SETTLED_PREFIX) },
                previous = previous
                    .filter { it.status == BlockStatus.PLANNED && it.end > fromMinute }
                    .map { it.toPlaced("prev") },
            ),
        )

        val fresh = planned.blocks
            .filterNot { it.key.startsWith(SETTLED_PREFIX) }
            // Anything in the past that the record already covers is not added a second time.
            .filterNot { block -> block.end <= fromMinute && settled.any { it.range.overlaps(block.range) } }
            .map { it.toScheduleBlock(date) }

        scheduleRepository.replacePlannedFrom(date, fromMinute, fresh)

        val diff = PlanDiff.of(
            previous = previous
                .filter { it.status == BlockStatus.PLANNED && !it.locked && it.end > fromMinute }
                .map { it.toPlaced("prev") },
            next = fresh.filter { it.end > fromMinute }.map { it.toPlaced("next") },
        )
        val headline = headlineFor(planned)
        val version = (scheduleRepository.plan(date)?.version ?: 0) + 1
        scheduleRepository.savePlan(
            date = date,
            version = version,
            headline = headline,
            energyMode = built.prefs.energyMode.key,
            work = built.intended,
            mode = built.mode.name.lowercase(),
            notes = built.notes,
        )

        return PlanResult(
            date = date,
            blocks = scheduleRepository.blocksFor(date),
            diff = diff,
            diagnostics = planned.diagnostics,
            headline = headline,
            unplaced = planned.unplaced,
            notes = built.notes,
            mode = built.mode,
            exam = built.exam,
        )
    }

    /** Closes out planned work whose time has fully passed without it being started. */
    private suspend fun markMissed(date: LocalDate, nowMinute: Int) {
        val passed = scheduleRepository.blocksFor(date).filter {
            it.status == BlockStatus.PLANNED && it.type.isWork && it.end <= nowMinute
        }
        for (block in passed) {
            val missed = block.copy(status = BlockStatus.MISSED, locked = false)
            scheduleRepository.update(missed)
            scheduleRepository.recordSkip(
                block = missed,
                reason = "Its time passed without it being started.",
                resolution = SkipResolution.UNRESOLVED,
                kind = SkipKind.MISSED,
            )
        }
    }

    private suspend fun estimateCapacities(
        date: LocalDate,
        tasks: List<Task>,
    ): List<WorkloadAllocator.DayCapacity> {
        val prefs = preferencesRepository.current()
        val furthest = tasks.mapNotNull { it.deadlineDate }.maxOrNull() ?: return emptyList()
        val horizon = minOf(furthest, date.plusDays(RELEVANCE_HORIZON_DAYS.toLong()))
        val routines = timetableRepository.routines()
        val out = mutableListOf<WorkloadAllocator.DayCapacity>()
        var cursor = date
        while (!cursor.isAfter(horizon)) {
            val committed = timetableRepository.entriesFor(cursor).sumOf { it.range.duration } +
                taskRepository.eventsOn(cursor).sumOf { it.range.duration } +
                routines.filter { it.appliesTo(cursor.dayOfWeek) }.sumOf { it.range.duration }
            out += WorkloadAllocator.DayCapacity(
                date = cursor,
                freeMinutes = WorkloadAllocator.estimateCapacity(
                    prefs.toPlannerPreferences(cursor.toEpochDay()),
                    committed,
                ),
            )
            cursor = cursor.plusDays(1)
        }
        return out
    }

    // ---------------------------------------------------------------------------------------
    // Conversions and copy
    // ---------------------------------------------------------------------------------------

    private fun ScheduleBlock.toPlaced(prefix: String) = PlacedBlock(
        key = "$prefix:$id",
        range = range,
        type = type,
        title = title,
        subtitle = subtitle,
        category = category,
        status = status,
        taskId = taskId,
        eventId = eventId,
        timetableEntryId = timetableEntryId,
        routineId = routineId,
        projectId = projectId,
        subjectCode = subjectCode,
        locked = locked,
        reason = reason,
        academicType = academicType,
        candidateId = candidateId,
        learningGoalId = learningGoalId,
        optional = optional,
    )

    private fun PlacedBlock.toScheduleBlock(date: LocalDate) = ScheduleBlock(
        date = date,
        start = range.start,
        end = range.end,
        type = type,
        title = title,
        subtitle = subtitle,
        category = category,
        status = BlockStatus.PLANNED,
        taskId = taskId,
        eventId = eventId,
        timetableEntryId = timetableEntryId,
        routineId = routineId,
        projectId = projectId,
        subjectCode = subjectCode,
        locked = locked,
        reason = reason,
        academicType = academicType,
        candidateId = candidateId,
        learningGoalId = learningGoalId,
        optional = optional,
        plannedStart = range.start,
        plannedMinutes = range.duration,
    )

    private fun headlineFor(day: PlannedDay): String {
        val work = day.workMinutes
        val free = day.leisureMinutes + day.freeMinutes
        return when {
            work == 0 && free == 0 -> "Nothing scheduled."
            work == 0 -> "No work planned. ${MarginTime.formatDuration(free)} free."
            else -> "${MarginTime.formatDuration(work)} of work, ${MarginTime.formatDuration(free)} free."
        }
    }

    private companion object {
        const val SETTLED_PREFIX = "settled"
        const val RELEVANCE_HORIZON_DAYS = 14
        const val HISTORY_DAYS = 60L
        const val SKIP_DAYS = 28L
    }
}
