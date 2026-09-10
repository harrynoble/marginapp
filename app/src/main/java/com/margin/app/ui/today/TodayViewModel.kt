package com.margin.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.planner.ChangeKind
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.usecase.PlanResult
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.ScheduleActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val nowMinute: Int = 0,
    val use24Hour: Boolean = false,
    val energyMode: EnergyMode = EnergyMode.NORMAL,
    val current: ScheduleBlock? = null,
    val upcoming: List<ScheduleBlock> = emptyList(),
    val earlier: List<ScheduleBlock> = emptyList(),
    val headline: String = "",
    val plannedWorkMinutes: Int = 0,
    val completedWorkMinutes: Int = 0,
    val freeRemainingMinutes: Int = 0,
    val notices: List<String> = emptyList(),
    val checkInDue: Boolean = false,
) {
    val progress: Float
        get() = if (plannedWorkMinutes <= 0) 0f
        else (completedWorkMinutes.toFloat() / plannedWorkMinutes).coerceIn(0f, 1f)

    val next: ScheduleBlock? get() = upcoming.firstOrNull()
}

/** A short, human sentence describing what a replan did. Shown once, then dismissed. */
data class ChangeBanner(val lines: List<String>, val protectedNote: String?)

class TodayViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
    private val actions: ScheduleActions,
) : ViewModel() {

    /** Emits every half minute, and immediately on subscribe, so "now" stays honest. */
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MILLIS)
        }
    }

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _banner = MutableStateFlow<ChangeBanner?>(null)
    val banner: StateFlow<ChangeBanner?> = _banner

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /**
     * The date is the only thing that re-subscribes the database. The clock lives in the
     * inner combine so a tick recomputes the state without tearing down the Room queries.
     */
    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<TodayUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                scheduleRepository.observeDay(date),
                scheduleRepository.observePlan(date),
                scheduleRepository.observeCheckIn(date),
                preferencesRepository.preferences,
                clock,
            ) { blocks, plan, checkIn, prefs, now ->
                val nowMinute = if (date == now.toLocalDate()) MarginTime.nowMinute(now) else 0
                buildState(
                    date = date,
                    nowMinute = nowMinute,
                    use24Hour = prefs.use24HourTime,
                    energyMode = prefs.energyModeFor(date),
                    blocks = blocks,
                    headline = plan?.headline,
                    checkInDue = checkIn == null &&
                        prefs.checkInEnabled &&
                        nowMinute >= prefs.checkInMinute,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(),
        )

    init {
        viewModelScope.launch {
            runCatching { planningService.ensurePlan(LocalDate.now()) }
        }
    }

    private fun com.margin.app.data.prefs.UserPreferences.energyModeFor(date: LocalDate): EnergyMode =
        if (energyModeDate == date.toEpochDay()) energyMode else EnergyMode.NORMAL

    private fun buildState(
        date: LocalDate,
        nowMinute: Int,
        use24Hour: Boolean,
        energyMode: EnergyMode,
        blocks: List<ScheduleBlock>,
        headline: String?,
        checkInDue: Boolean,
    ): TodayUiState {
        val visible = blocks
            .filter { it.type != BlockType.SLEEP }
            .sortedBy { it.start }

        val running = visible.firstOrNull { it.status == BlockStatus.ACTIVE }
        val containingNow = visible.firstOrNull {
            it.status == BlockStatus.PLANNED && nowMinute in it.start until it.end
        }
        val current = running ?: containingNow

        val upcoming = visible.filter {
            it.status == BlockStatus.PLANNED && it.start >= nowMinute && it.id != current?.id
        }
        val earlier = visible.filter {
            it.id != current?.id && (it.status != BlockStatus.PLANNED || it.end <= nowMinute)
        }

        val plannedWork = visible.filter { it.type.isWork }.sumOf { it.duration }
        val doneWork = visible
            .filter { it.type.isWork && it.status == BlockStatus.DONE }
            .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }
        val freeRemaining = visible
            .filter {
                (it.type == BlockType.FREE || it.type == BlockType.LEISURE) &&
                    it.end > nowMinute && it.status == BlockStatus.PLANNED
            }
            .sumOf { it.duration }

        return TodayUiState(
            loading = false,
            date = date,
            nowMinute = nowMinute,
            use24Hour = use24Hour,
            energyMode = energyMode,
            current = current,
            upcoming = upcoming,
            earlier = earlier,
            headline = headline ?: "",
            plannedWorkMinutes = plannedWork,
            completedWorkMinutes = doneWork,
            freeRemainingMinutes = freeRemaining,
            checkInDue = checkInDue,
        )
    }

    // ---- actions ---------------------------------------------------------------------------

    fun start(blockId: Long) = launchAction { actions.start(blockId); null }

    fun pause(blockId: Long) = launchAction { actions.pause(blockId); null }

    fun complete(blockId: Long) = launchAction { actions.complete(blockId) }

    fun skip(blockId: Long, resolution: SkipResolution, reason: String? = null) =
        launchAction { actions.skip(blockId, resolution, reason) }

    fun extend(blockId: Long, minutes: Int) = launchAction { actions.extend(blockId, minutes) }

    fun takeBreak(minutes: Int) = launchAction { actions.takeBreak(minutes) }

    fun move(blockId: Long, toMinute: Int) = launchAction {
        actions.reschedule(blockId, selectedDate.value, toMinute)
    }

    fun moveToTomorrow(blockId: Long) = launchAction {
        val block = scheduleRepository.block(blockId) ?: return@launchAction null
        actions.reschedule(blockId, block.date.plusDays(1), block.start)
    }

    fun togglePin(blockId: Long, locked: Boolean) = launchAction {
        actions.setLocked(blockId, locked)
        planningService.replan(selectedDate.value)
    }

    fun replan() = launchAction { planningService.replan(selectedDate.value) }

    fun setEnergyMode(mode: EnergyMode) = launchAction {
        preferencesRepository.update {
            it.copy(energyMode = mode, energyModeDate = selectedDate.value.toEpochDay())
        }
        planningService.replan(selectedDate.value)
    }

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
                ChangeKind.REMOVED -> "Dropped ${change.title} from today"
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
        )
    }
}
