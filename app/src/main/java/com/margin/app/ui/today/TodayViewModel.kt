package com.margin.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.GoalRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Subject
import com.margin.app.domain.planner.ChangeKind
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.planner.ExamPlanner
import com.margin.app.domain.usecase.PlanResult
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.ScheduleActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Something the user can mark as mattering on a lighter day. */
data class LightenOption(val key: String, val label: String, val detail: String?, val subjectCode: String?)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val nowMinute: Int = 0,
    val use24Hour: Boolean = false,
    val wakeMinute: Int = 0,
    val sleepMinute: Int = 24 * 60,
    val energyMode: EnergyMode = EnergyMode.NORMAL,
    val current: ScheduleBlock? = null,
    val upcoming: List<ScheduleBlock> = emptyList(),
    val earlier: List<ScheduleBlock> = emptyList(),
    val headline: String = "",
    val plannedWorkMinutes: Int = 0,
    val completedWorkMinutes: Int = 0,
    val freeRemainingMinutes: Int = 0,
    val checkInDue: Boolean = false,
    val dayState: DayState = DayState(LocalDate.now()),
    /** normal, light or minimum. */
    val mode: String = "normal",
    val notes: List<String> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val graceMinutes: Int = 15,
    val buildOffer: ScheduleBlock? = null,
    val learningOffer: ScheduleBlock? = null,
    val academicsDone: Boolean = false,
    /** Work sessions whose time passed unstarted today. They are carried, not lost. */
    val missedToday: Int = 0,
    val projects: List<Project> = emptyList(),
    val goals: List<LearningGoal> = emptyList(),
    val lightenOptions: List<LightenOption> = emptyList(),
    val subjects: List<Subject> = emptyList(),
) {
    val progress: Float
        get() = if (plannedWorkMinutes <= 0) 0f
        else (completedWorkMinutes.toFloat() / plannedWorkMinutes).coerceIn(0f, 1f)

    val next: ScheduleBlock? get() = upcoming.firstOrNull()

    /** The next block worth naming. Free time is not an answer to "what is next". */
    val nextMeaningful: ScheduleBlock?
        get() = upcoming.firstOrNull { it.type != BlockType.FREE } ?: next

    val nextWork: ScheduleBlock? get() = upcoming.firstOrNull { it.type.isWork }

    /** True before the user is up, or after they should be in bed. */
    val outsideWakingHours: Boolean
        get() = nowMinute < wakeMinute || (sleepMinute > wakeMinute && nowMinute >= sleepMinute)

    /** A session that should have started and has not: the "are you out?" moment. */
    val overdue: ScheduleBlock?
        get() = current?.takeIf {
            it.status == BlockStatus.PLANNED && it.type.isWork && nowMinute >= it.start + graceMinutes
        }

    /** The running session has reached its planned end: move on, or keep going. */
    val overrun: Boolean
        get() = current?.status == BlockStatus.ACTIVE && current.type.isWork && nowMinute >= current.end

    /** Past bedtime: nothing more will be planned today, so nothing should ask for a decision. */
    val dayOver: Boolean get() = sleepMinute > wakeMinute && nowMinute >= sleepMinute

    val isLightDay: Boolean get() = dayState.lightDay
    val isMinimumDay: Boolean get() = mode == "minimum"

    val nearestExamDays: Int?
        get() = exams.minOfOrNull { ExamPlanner.daysUntil(date, it) }
}

/** A short, human sentence describing what a replan did. Shown once, then dismissed. */
data class ChangeBanner(val lines: List<String>, val protectedNote: String?)

class TodayViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
    private val actions: ScheduleActions,
    private val dayRepository: DayRepository,
    private val examRepository: ExamRepository,
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val timetableRepository: TimetableRepository,
) : ViewModel() {

    /** Emits every half minute, and immediately on subscribe, so "now" stays honest. */
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MILLIS)
        }
    }

    /** The date follows the clock, so the screen moves to the new day at midnight on its own. */
    private val date: Flow<LocalDate> = clock.map { it.toLocalDate() }.distinctUntilChanged()

    private val _banner = MutableStateFlow<ChangeBanner?>(null)
    val banner: StateFlow<ChangeBanner?> = _banner

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            val today = combine(
                scheduleRepository.observeDay(day),
                scheduleRepository.observePlanMeta(day),
                scheduleRepository.observeCheckIn(day),
                dayRepository.observeState(day),
                preferencesRepository.preferences,
            ) { blocks, meta, checkIn, dayState, prefs -> Snapshot(blocks, meta, checkIn != null, dayState, prefs) }
            val context = combine(
                examRepository.observeUpcoming(day),
                taskRepository.observeProjects(),
                goalRepository.observeGoals(),
                timetableRepository.observeSubjects(),
            ) { exams, projects, goals, subjects -> Extras(exams, projects, goals, subjects) }
            combine(today, context, clock) { snapshot, extras, now ->
                val nowMinute = if (day == now.toLocalDate()) MarginTime.nowMinute(now) else 0
                buildState(day, nowMinute, snapshot, extras)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(),
        )

    private data class Snapshot(
        val blocks: List<ScheduleBlock>,
        val meta: com.margin.app.data.repository.PlanMeta?,
        val checkedIn: Boolean,
        val dayState: DayState,
        val prefs: com.margin.app.data.prefs.UserPreferences,
    )

    private data class Extras(
        val exams: List<Exam>,
        val projects: List<Project>,
        val goals: List<LearningGoal>,
        val subjects: List<Subject>,
    )

    init {
        viewModelScope.launch {
            runCatching { planningService.ensurePlan(LocalDate.now()) }
        }
        // Sessions whose time passed unstarted are recorded and their work placed again,
        // without the user having to open anything.
        viewModelScope.launch {
            clock.collect { now -> runCatching { planningService.refreshIfStale(now) } }
        }
    }

    private fun buildState(
        date: LocalDate,
        nowMinute: Int,
        snapshot: Snapshot,
        extras: Extras,
    ): TodayUiState {
        val prefs = snapshot.prefs
        val visible = snapshot.blocks
            .filter { it.type != BlockType.SLEEP }
            .sortedBy { it.start }

        val running = visible.firstOrNull { it.status == BlockStatus.ACTIVE }
        val paused = visible.filter { it.status == BlockStatus.PAUSED }.maxByOrNull { it.start }
        val containingNow = visible.firstOrNull {
            it.status == BlockStatus.PLANNED && nowMinute in it.start until it.end
        }
        val current = running ?: paused ?: containingNow

        val upcoming = visible.filter {
            it.status == BlockStatus.PLANNED && it.start >= nowMinute && it.id != current?.id
        }
        val earlier = visible.filter {
            it.id != current?.id && (it.status != BlockStatus.PLANNED || it.end <= nowMinute) &&
                !(it.status == BlockStatus.PLANNED && it.start >= nowMinute)
        }

        // Work still to come or actually done. Missed and skipped sessions are shown under
        // Earlier and carried forward; counting them here would promise time that is gone.
        val plannedWork = visible
            .filter { it.type.isWork && (it.status.isOpen || it.status.isWorked) }
            .sumOf { it.duration }
        val doneWork = visible
            .filter { it.type.isWork && it.status.isWorked }
            .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }
        val freeRemaining = visible
            .filter {
                (it.type == BlockType.FREE || it.type == BlockType.LEISURE) &&
                    it.end > nowMinute && it.status == BlockStatus.PLANNED
            }
            .sumOf { it.end - maxOf(it.start, nowMinute) }

        // "Done" means work actually happened and nothing academic was missed or is still to come.
        val academic = visible.filter {
            it.isAcademic && it.status != BlockStatus.RESCHEDULED && it.status != BlockStatus.CANCELLED
        }
        val academicsDone = academic.any { it.status.isWorked } &&
            academic.none { it.status.isOpen || it.status == BlockStatus.MISSED }
        val missedToday = visible.count { it.type.isWork && it.status == BlockStatus.MISSED }
        val dayState = snapshot.dayState
        fun offer(type: BlockType, decision: Decision) = if (decision != Decision.UNASKED) {
            null
        } else {
            visible.firstOrNull { it.type == type && it.optional && it.status == BlockStatus.PLANNED && it.end > nowMinute }
        }

        val subjects = extras.subjects.associateBy { it.code }
        val lightenOptions = buildList {
            visible.filter { it.isAcademic && it.status.isOpen && it.subjectCode != null }
                .map { it.subjectCode!! }
                .distinct()
                .forEach { code ->
                    val subject = subjects[code]
                    add(
                        LightenOption(
                            key = DayState.subjectKey(code),
                            label = subject?.name ?: code,
                            detail = visible.filter { it.subjectCode == code && it.status.isOpen && it.isAcademic }
                                .joinToString(", ") { it.academicType?.label ?: it.type.key },
                            subjectCode = code,
                        ),
                    )
                }
            visible.filter { it.taskId != null && it.status.isOpen && it.subjectCode == null }
                .distinctBy { it.taskId }
                .forEach { add(LightenOption(DayState.taskKey(it.taskId!!), it.title, "Task", null)) }
        }

        return TodayUiState(
            loading = false,
            date = date,
            nowMinute = nowMinute,
            use24Hour = prefs.use24HourTime,
            wakeMinute = prefs.wakeMinute,
            sleepMinute = prefs.sleepMinute,
            energyMode = prefs.energyModeFor(date.toEpochDay()),
            current = current,
            upcoming = upcoming,
            earlier = earlier,
            headline = snapshot.meta?.headline ?: "",
            plannedWorkMinutes = plannedWork,
            completedWorkMinutes = doneWork,
            freeRemainingMinutes = freeRemaining,
            checkInDue = !snapshot.checkedIn && prefs.checkInEnabled && nowMinute >= prefs.checkInMinute,
            dayState = dayState,
            mode = snapshot.meta?.mode ?: if (dayState.lightDay) "light" else "normal",
            notes = snapshot.meta?.notes.orEmpty(),
            exams = extras.exams.filter { ExamPlanner.daysUntil(date, it) in 0..ExamPlanner.HORIZON_DAYS },
            graceMinutes = prefs.missedGraceMinutes,
            buildOffer = offer(BlockType.BUILD, dayState.buildDecision),
            learningOffer = offer(BlockType.LEARN, dayState.learningDecision),
            academicsDone = academicsDone,
            missedToday = missedToday,
            projects = extras.projects.filter { it.active },
            goals = extras.goals.filter { it.active },
            lightenOptions = lightenOptions,
            subjects = extras.subjects,
        )
    }

    // ---- sessions ------------------------------------------------------------------------------

    fun start(blockId: Long) = launchAction { actions.start(blockId); null }

    fun pause(blockId: Long) = launchAction { actions.pause(blockId); null }

    fun complete(blockId: Long) = launchAction { actions.complete(blockId) }

    fun skip(blockId: Long, resolution: SkipResolution, reason: String? = null) =
        launchAction { actions.skip(blockId, resolution, reason) }

    fun extend(blockId: Long, minutes: Int) = launchAction { actions.extend(blockId, minutes) }

    fun continueSession(blockId: Long, minutes: Int = 15) = launchAction { actions.continueSession(blockId, minutes) }

    fun moveToNext(blockId: Long) = launchAction { actions.moveToNext(blockId); null }

    fun later(blockId: Long, minutes: Int = 30) = launchAction {
        val block = scheduleRepository.block(blockId) ?: return@launchAction null
        val from = maxOf(block.start, MarginTime.nowMinute())
        actions.reschedule(blockId, block.date, (from + minutes).coerceAtMost(24 * 60 - 5), "Later")
    }

    fun takeBreak(minutes: Int, meal: Boolean = false) =
        launchAction { actions.takeBreak(minutes, reason = if (meal) BreakReason.MEAL else BreakReason.MANUAL) }

    fun move(blockId: Long, toMinute: Int) = launchAction {
        actions.reschedule(blockId, LocalDate.now(), toMinute)
    }

    fun moveToTomorrow(blockId: Long) = launchAction {
        val block = scheduleRepository.block(blockId) ?: return@launchAction null
        actions.reschedule(blockId, block.date.plusDays(1), block.start)
    }

    fun togglePin(blockId: Long, locked: Boolean) = launchAction {
        actions.setLocked(blockId, locked)
        planningService.replan(LocalDate.now())
    }

    fun replan() = launchAction { planningService.replan(LocalDate.now()) }

    // ---- the day --------------------------------------------------------------------------------

    fun setEnergyMode(mode: EnergyMode) = launchAction { actions.setEnergy(LocalDate.now(), mode) }

    fun goOut(backMinute: Int?) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { actions.goOut(backMinute) }.getOrNull()
            _busy.value = false
            if (result != null) _banner.value = ChangeBanner(listOf(result.message), null)
        }
    }

    fun lighten(essentials: Set<String>, priorities: Set<String>, dropBuild: Boolean, dropLearning: Boolean, lowEnergy: Boolean) =
        launchAction { actions.lightenToday(LocalDate.now(), essentials, priorities, dropBuild, dropLearning, lowEnergy) }

    fun clearLighten() = launchAction { actions.clearLighten(LocalDate.now()) }

    fun setMinimumDay(on: Boolean) = launchAction { actions.setMinimumDay(LocalDate.now(), on) }

    fun buildDecision(decision: Decision, projectId: Long? = null, minutes: Int? = null) =
        launchAction { actions.setBuildDecision(LocalDate.now(), decision, projectId, minutes) }

    fun learningDecision(decision: Decision, goalId: Long? = null, minutes: Int? = null) =
        launchAction { actions.setLearningDecision(LocalDate.now(), decision, goalId, minutes) }

    fun excludeSubject(code: String) = launchAction { actions.excludeSubjectToday(LocalDate.now(), code) }

    fun dismissBanner() {
        _banner.value = null
    }

    private fun launchAction(block: suspend () -> PlanResult?) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { block() }.getOrNull()
            _busy.value = false
            result?.let { showChanges(it) }
        }
    }

    private fun showChanges(result: PlanResult) {
        val meaningful = result.diff.meaningful.filter {
            it.kind == ChangeKind.MOVED || it.kind == ChangeKind.ADDED || it.kind == ChangeKind.REMOVED
        }
        if (meaningful.isEmpty() && result.diagnostics.isEmpty()) return

        val use24 = state.value.use24Hour
        val lines = meaningful.take(MAX_CHANGE_LINES).map { change ->
            when (change.kind) {
                ChangeKind.MOVED -> {
                    val to = change.to?.start?.let { MarginTime.formatTime(it, use24) }
                    "Moved ${change.title} to $to"
                }
                ChangeKind.ADDED -> {
                    val at = change.to?.start?.let { MarginTime.formatTime(it, use24) }
                    "Added ${change.title} at $at"
                }
                ChangeKind.REMOVED -> "Took ${change.title} off today"
                else -> change.title
            }
        } + result.diagnostics.take(2).map { it.message }

        val protectedMinutes = result.blocks
            .filter { it.type == BlockType.LEISURE || it.type == BlockType.BREAK }
            .filter { it.status == BlockStatus.PLANNED }
            .sumOf { it.duration }
        val note = if (protectedMinutes > 0) {
            "Kept ${MarginTime.formatDuration(protectedMinutes)} of breaks and downtime."
        } else {
            null
        }

        _banner.value = ChangeBanner(lines, note)
    }

    companion object {
        private const val TICK_MILLIS = 30_000L
        private const val MAX_CHANGE_LINES = 4

        fun create(container: AppContainer) = TodayViewModel(
            scheduleRepository = container.scheduleRepository,
            preferencesRepository = container.preferencesRepository,
            planningService = container.planningService,
            actions = container.scheduleActions,
            dayRepository = container.dayRepository,
            examRepository = container.examRepository,
            taskRepository = container.taskRepository,
            goalRepository = container.goalRepository,
            timetableRepository = container.timetableRepository,
        )
    }
}
