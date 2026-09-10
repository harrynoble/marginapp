package com.margin.app.domain.usecase

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.planner.Commitment
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.planner.Diagnostic
import com.margin.app.domain.planner.PlacedBlock
import com.margin.app.domain.planner.PlanDiff
import com.margin.app.domain.planner.PlannedDay
import com.margin.app.domain.planner.PlannerInput
import com.margin.app.domain.planner.PlannerPreferences
import com.margin.app.domain.planner.QuotaCandidate
import com.margin.app.domain.planner.QuotaKind
import com.margin.app.domain.planner.WorkCandidate
import com.margin.app.domain.planner.WorkloadAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class PlanResult(
    val date: LocalDate,
    val blocks: List<ScheduleBlock>,
    val diff: PlanDiff,
    val diagnostics: List<Diagnostic>,
    val headline: String,
)

/**
 * Turns the database into a [PlannerInput], runs the engine, and writes the result back.
 *
 * All of the "what should be considered today" judgement lives here; all of the "where does
 * it go" judgement lives in [DayPlanner]. Keeping the two apart is what makes the engine
 * testable without a database.
 */
class PlanningService(
    private val timetableRepository: TimetableRepository,
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planner: DayPlanner = DayPlanner(),
) {

    /** Generates the plan only if the day has none yet. Cheap to call on every app open. */
    suspend fun ensurePlan(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): PlanResult? {
        val existing = scheduleRepository.blocksFor(date)
        if (existing.isNotEmpty()) return null
        return replan(date, now)
    }

    /**
     * Rebuilds the open part of [date]. Completed, skipped, running and locked blocks are
     * carried through untouched; only what is still merely planned can move.
     */
    suspend fun replan(
        date: LocalDate,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult = withContext(Dispatchers.Default) {
        val prefs = preferencesRepository.current()
        val plannerPrefs = prefs.toPlannerPreferences(date.toEpochDay())

        val previousBlocks = scheduleRepository.blocksFor(date)
        val settledDomain = previousBlocks.filter { it.status != BlockStatus.PLANNED || it.locked }
        val settled = settledDomain.map { it.toPlaced(SETTLED_PREFIX) }

        val isToday = date == now.toLocalDate()
        val nowMinute = if (isToday) MarginTime.nowMinute(now) else null

        val commitments = buildCommitments(date, settledDomain)
        val work = buildWorkCandidates(date, plannerPrefs, settledDomain)
        val quotas = buildQuotas(date, plannerPrefs, work, settledDomain)

        val input = PlannerInput(
            date = date,
            prefs = plannerPrefs,
            commitments = commitments,
            work = work,
            quotas = quotas,
            nowMinute = nowMinute,
            settled = settled,
            previous = previousBlocks.map { it.toPlaced("prev") },
        )

        val planned: PlannedDay = planner.plan(input)

        val fresh = planned.blocks
            .filterNot { it.key.startsWith(SETTLED_PREFIX) }
            .map { it.toScheduleBlock(date) }

        scheduleRepository.replacePlanned(date, fresh)

        val diff = PlanDiff.of(
            previous = previousBlocks.filter { it.status == BlockStatus.PLANNED }.map { it.toPlaced("prev") },
            next = planned.blocks.filterNot { it.key.startsWith(SETTLED_PREFIX) },
        )
        val headline = headlineFor(planned)
        val version = (scheduleRepository.plan(date)?.version ?: 0) + 1
        scheduleRepository.savePlan(date, version, headline, plannerPrefs.energyMode.key)

        PlanResult(
            date = date,
            blocks = scheduleRepository.blocksFor(date),
            diff = diff,
            diagnostics = planned.diagnostics,
            headline = headline,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Commitments
    // ---------------------------------------------------------------------------------------

    private suspend fun buildCommitments(
        date: LocalDate,
        settled: List<ScheduleBlock>,
    ): List<Commitment> {
        val out = mutableListOf<Commitment>()
        val settledRanges = settled.map { it.range }

        for (entry in timetableRepository.entriesFor(date)) {
            val type = if (entry.kind == TimetableKind.RECESS) BlockType.BREAK else BlockType.CLASS
            out += Commitment(
                id = "class:${entry.id}",
                title = entry.title,
                subtitle = listOfNotNull(
                    entry.subjectCode.takeIf { entry.kind != TimetableKind.RECESS },
                    entry.location,
                ).joinToString(" - ").ifBlank { null },
                range = entry.range,
                type = type,
                category = if (type == BlockType.BREAK) Category.LEISURE else Category.ACADEMICS,
                subjectCode = entry.subjectCode,
                timetableEntryId = entry.id.takeIf { it > 0 },
                generatesReview = entry.kind.isTeaching,
            )
        }

        for (routine in timetableRepository.routines()) {
            if (!routine.appliesTo(date.dayOfWeek)) continue
            out += Commitment(
                id = "routine:${routine.id}",
                title = routine.title,
                range = routine.range,
                type = when (routine.kind) {
                    com.margin.app.domain.model.RoutineKind.MEAL -> BlockType.MEAL
                    com.margin.app.domain.model.RoutineKind.COMMUTE -> BlockType.COMMUTE
                    com.margin.app.domain.model.RoutineKind.SLEEP -> BlockType.SLEEP
                    com.margin.app.domain.model.RoutineKind.COMMITMENT -> BlockType.ROUTINE
                },
                category = routine.category,
                routineId = routine.id,
            )
        }

        for (event in taskRepository.eventsOn(date)) {
            out += Commitment(
                id = "event:${event.id}",
                title = event.title,
                subtitle = event.notes,
                range = if (event.allDay) TimeRange(0, 24 * 60) else event.range,
                type = BlockType.EVENT,
                category = event.category,
                eventId = event.id,
            )
        }

        // Settled blocks (done, skipped, running, pinned) are handed to the planner separately
        // and already occupy their time. Drop any commitment that one of them represents,
        // otherwise a class marked done would be placed twice.
        val settledClassIds = settled.mapNotNull { it.timetableEntryId }.toSet()
        val settledEventIds = settled.mapNotNull { it.eventId }.toSet()
        val settledRoutineIds = settled.mapNotNull { it.routineId }.toSet()

        return out.filterNot { commitment ->
            val classId = commitment.timetableEntryId
            val eventId = commitment.eventId
            val routineId = commitment.routineId
            (classId != null && classId in settledClassIds) ||
                (eventId != null && eventId in settledEventIds) ||
                (routineId != null && routineId in settledRoutineIds) ||
                settledRanges.any { it.contains(commitment.range) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Work candidates
    // ---------------------------------------------------------------------------------------

    private suspend fun buildWorkCandidates(
        date: LocalDate,
        prefs: PlannerPreferences,
        settled: List<ScheduleBlock>,
    ): List<WorkCandidate> {
        val out = mutableListOf<WorkCandidate>()
        val doneToday = settled
            .filter { it.status == BlockStatus.DONE && it.taskId != null }
            .groupBy { it.taskId!! }
            .mapValues { entry -> entry.value.sumOf { it.elapsedMinutes.takeIf { m -> m > 0 } ?: it.duration } }

        val tasks = taskRepository.activeTasks()
        val capacities = estimateCapacities(date, tasks)

        for (task in tasks) {
            if (task.status != TaskStatus.ACTIVE) continue
            val already = doneToday[task.id] ?: 0
            val candidate = task.toCandidate(date, prefs, capacities, already) ?: continue
            out += candidate
        }

        if (prefs.reviewEnabled) out += reviewCandidates(date, prefs, doneToday.keys)

        return out.sortedBy { it.id }
    }

    private suspend fun Task.toCandidate(
        date: LocalDate,
        prefs: PlannerPreferences,
        capacities: List<WorkloadAllocator.DayCapacity>,
        alreadyDoneToday: Int,
    ): WorkCandidate? {
        if (isRecurring && !recursOn(date.dayOfWeek)) return null

        val wanted = if (isRecurring) {
            (estimatedMinutes - alreadyDoneToday).coerceAtLeast(0)
        } else {
            WorkloadAllocator.minutesFor(this, date, prefs, capacities) - alreadyDoneToday
        }
        if (wanted < MIN_PLACEABLE) return null

        // Only look at tasks that are relevant now: due within the horizon, pinned to today,
        // or with no deadline at all. Otherwise a long backlog would crowd out today.
        val days = deadlineDate?.let { (it.toEpochDay() - date.toEpochDay()).toInt() }
        if (days != null && days > RELEVANCE_HORIZON_DAYS && pinnedDate != date) return null

        val project = taskRepository.project(projectId)

        return WorkCandidate(
            id = "task:$id",
            minutes = wanted,
            title = title,
            subtitle = project?.name ?: subjectCode,
            type = if (category == Category.BUILD) BlockType.BUILD else BlockType.TASK,
            category = category,
            taskId = id,
            projectId = projectId,
            subjectCode = subjectCode,
            minSession = minSessionMinutes.coerceAtMost(wanted).coerceAtLeast(5),
            maxSession = maxSessionMinutes.coerceAtLeast(minSessionMinutes),
            priority = priority,
            difficulty = difficulty,
            energy = energy,
            daysToDeadline = days,
            deadlineMinute = deadlineMinute?.takeIf { days == 0 },
            preferredWindow = preferredWindow,
            splittable = splittable,
            importance = if (pinnedDate == date) 30 else 0,
        )
    }

    /**
     * Revision for the classes that actually happened today, weighted per subject. This is
     * the timetable-to-study link: the app knows what was taught and proposes proportionate
     * review, rather than assuming every subject needs the same half hour.
     */
    private suspend fun reviewCandidates(
        date: LocalDate,
        prefs: PlannerPreferences,
        alreadyDone: Set<Long>,
    ): List<WorkCandidate> {
        val teaching = timetableRepository.entriesFor(date).filter { it.kind.isTeaching }
        if (teaching.isEmpty()) return emptyList()

        val subjects = timetableRepository.subjects().associateBy { it.code }
        val bySubject = teaching.filter { it.subjectCode != null }.groupBy { it.subjectCode!! }
        val perSubject = bySubject.mapValues { entry -> entry.value.sumOf { it.range.duration } }
        // Revision of a subject cannot be scheduled before the class it revises.
        val taughtBy = bySubject.mapValues { entry -> entry.value.maxOf { it.range.end } }

        val raw = perSubject.map { (code, taughtMinutes) ->
            val subject = subjects[code]
            val weight = subject?.reviewWeight ?: 1f
            val minutes = (taughtMinutes / 60f) * prefs.reviewMinutesPerTeachingHour * weight
            code to minutes
        }.sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })

        val total = raw.sumOf { it.second.toDouble() }.toFloat()
        val scale = if (total > prefs.maxReviewMinutesPerDay) prefs.maxReviewMinutesPerDay / total else 1f

        var budget = prefs.maxReviewMinutesPerDay
        val out = mutableListOf<WorkCandidate>()
        for ((code, rawMinutes) in raw) {
            if (budget < prefs.minReviewSession) break
            val minutes = prefs.roundUp((rawMinutes * scale).roundToInt())
                .coerceAtLeast(prefs.minReviewSession)
                .coerceAtMost(budget)
            if (minutes < prefs.minReviewSession) continue
            val subject = subjects[code]
            out += WorkCandidate(
                id = "review:$code:${date.toEpochDay()}",
                minutes = minutes,
                title = "${subject?.shortName ?: code} review",
                subtitle = "Today in class",
                type = BlockType.REVIEW,
                category = Category.ACADEMICS,
                subjectCode = code,
                minSession = prefs.minReviewSession.coerceAtMost(minutes),
                maxSession = minutes,
                earliestStart = taughtBy[code],
                priority = Priority.NORMAL,
                difficulty = Difficulty.MODERATE,
                energy = EnergyLevel.MEDIUM,
                splittable = false,
            )
            budget -= minutes
        }
        return out
    }

    private suspend fun estimateCapacities(
        date: LocalDate,
        tasks: List<Task>,
    ): List<WorkloadAllocator.DayCapacity> {
        val prefs = preferencesRepository.current()
        val furthest = tasks.mapNotNull { it.deadlineDate }.maxOrNull() ?: return emptyList()
        val horizon = minOf(furthest, date.plusDays(RELEVANCE_HORIZON_DAYS.toLong()))
        val out = mutableListOf<WorkloadAllocator.DayCapacity>()
        var cursor = date
        while (!cursor.isAfter(horizon)) {
            val committed = timetableRepository.entriesFor(cursor).sumOf { it.range.duration } +
                taskRepository.eventsOn(cursor).sumOf { it.range.duration } +
                timetableRepository.routines().filter { it.appliesTo(cursor.dayOfWeek) }
                    .sumOf { it.range.duration }
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
    // Quotas
    // ---------------------------------------------------------------------------------------

    private fun buildQuotas(
        date: LocalDate,
        prefs: PlannerPreferences,
        work: List<WorkCandidate>,
        settled: List<ScheduleBlock>,
    ): List<QuotaCandidate> {
        val quotas = mutableListOf<QuotaCandidate>()

        val leisureAlready = settled
            .filter { it.type == BlockType.LEISURE && it.status != BlockStatus.SKIPPED }
            .sumOf { it.duration }
        val leisureNeeded = (prefs.effectiveLeisureFloor - leisureAlready).coerceAtLeast(0)
        if (leisureNeeded >= prefs.minLeisureChunk) {
            quotas += QuotaCandidate(
                id = "quota:leisure",
                minutes = leisureNeeded,
                kind = QuotaKind.LEISURE,
                title = "Leisure",
                window = prefs.leisureWindow,
                minChunk = prefs.minLeisureChunk,
                category = Category.LEISURE,
                type = BlockType.LEISURE,
            )
        }

        val weekend = MarginTime.isWeekend(date)
        val buildTarget = if (weekend) prefs.buildMinutesWeekend else prefs.buildMinutesWeekday
        val buildFromTasks = work.filter { it.category == Category.BUILD }.sumOf { it.minutes }
        val buildAlready = settled
            .filter { it.type == BlockType.BUILD && it.status != BlockStatus.SKIPPED }
            .sumOf { it.duration }
        val buildNeeded = (buildTarget - buildFromTasks - buildAlready).coerceAtLeast(0)
        if (buildNeeded >= prefs.minBuildChunk) {
            quotas += QuotaCandidate(
                id = "quota:build",
                minutes = buildNeeded,
                kind = QuotaKind.BUILD,
                title = "Build",
                window = prefs.buildWindow,
                minChunk = prefs.minBuildChunk,
                category = Category.BUILD,
                type = BlockType.BUILD,
            )
        }

        return quotas
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
        const val MIN_PLACEABLE = 10
        const val RELEVANCE_HORIZON_DAYS = 14
    }
}
