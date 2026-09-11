package com.margin.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
import com.margin.app.domain.planner.BreakAdvisor
import com.margin.app.domain.planner.BreakSuggestion
import com.margin.app.domain.planner.EnergyMode
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class FocusUiState(
    val block: ScheduleBlock? = null,
    val task: Task? = null,
    val links: List<ResourceLink> = emptyList(),
    val use24Hour: Boolean = false,
    val nowMinute: Int = 0,
    /** Seconds since midnight, so the focus timer can count down to the second. */
    val nowSecond: Int = 0,
    val finished: Boolean = false,
    /** The next piece of work today, offered when this one reaches its planned end. */
    val next: ScheduleBlock? = null,
    /** Set once the unbroken run of work has earned a break. */
    val breakDue: BreakSuggestion? = null,
    /** A new session was started from here; the screen moves to it. */
    val startedNext: Long? = null,
) {
    val running: Boolean get() = block?.status == BlockStatus.ACTIVE

    val remainingMinutes: Int
        get() = block?.let { (it.end - nowMinute).coerceAtLeast(0) } ?: 0

    val elapsedMinutes: Int
        get() = block?.let { (nowMinute - it.start).coerceIn(0, it.duration) } ?: 0

    val progress: Float
        get() = block?.let {
            if (it.duration <= 0) 0f else (elapsedMinutes.toFloat() / it.duration).coerceIn(0f, 1f)
        } ?: 0f

    val overrun: Boolean
        get() = block != null && nowMinute > block.end

    /** What the session was originally planned for, before any extensions. */
    val plannedMinutes: Int
        get() = block?.let { if (it.plannedMinutes > 0) it.plannedMinutes else it.duration } ?: 0
}

class FocusViewModel(
    private val blockId: Long,
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val preferencesRepository: PreferencesRepository,
    private val actions: ScheduleActions,
) : ViewModel() {

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000L)
        }
    }

    private val finished = MutableStateFlow(false)
    private val startedNext = MutableStateFlow<Long?>(null)

    /** Links depend on which task this block belongs to, so only that re-subscribes. */
    @Suppress("OPT_IN_USAGE")
    private val linksForBlock: Flow<List<ResourceLink>> =
        scheduleRepository.observeDay(LocalDate.now())
            .map { blocks -> blocks.firstOrNull { it.id == blockId }?.taskId }
            .distinctUntilChanged()
            .flatMapLatest { taskId ->
                if (taskId == null) flowOf(emptyList()) else taskRepository.observeLinks(taskId)
            }

    val state: StateFlow<FocusUiState> = combine(
        scheduleRepository.observeDay(LocalDate.now()),
        preferencesRepository.preferences,
        clock,
        combine(finished, startedNext) { done, next -> done to next },
        linksForBlock,
    ) { blocks, prefs, now, (isFinished, nextId), links ->
        val block = blocks.firstOrNull { it.id == blockId }
        val nowMinute = MarginTime.nowMinute(now)
        val next = block?.let { current ->
            blocks
                .filter { it.id != current.id && it.status == BlockStatus.PLANNED && it.type.isWork && it.end > nowMinute }
                .minByOrNull { it.start }
        }
        val breakDue = if (block?.status == BlockStatus.ACTIVE) {
            val run = BreakAdvisor.currentRun(blocks, nowMinute, block.actualStart?.let(::minuteOf))
            BreakAdvisor.evaluate(
                run = run,
                nowMinute = nowMinute,
                baseThreshold = prefs.continuousWorkBeforeBreak,
                lowEnergy = prefs.energyModeFor(now.toLocalDate().toEpochDay()) == EnergyMode.LIGHT,
            )?.takeIf { it.atMinute <= nowMinute }
        } else {
            null
        }
        FocusUiState(
            block = block,
            task = null,
            links = links,
            use24Hour = prefs.use24HourTime,
            nowMinute = nowMinute,
            nowSecond = now.toLocalTime().toSecondOfDay(),
            finished = isFinished || block == null,
            next = next,
            breakDue = breakDue,
            startedNext = nextId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            val block = scheduleRepository.block(blockId)
            if (block != null && (block.status == BlockStatus.PLANNED || block.status == BlockStatus.PAUSED)) {
                actions.start(blockId)
            }
        }
    }

    fun pause() = viewModelScope.launch { actions.pause(blockId) }

    fun resume() = viewModelScope.launch { actions.start(blockId) }

    fun complete() = viewModelScope.launch {
        actions.complete(blockId)
        finished.value = true
    }

    fun extend(minutes: Int) = viewModelScope.launch { actions.extend(blockId, minutes) }

    /** Keep going past the planned end. The extra time is recorded, not lost. */
    fun continueSession(minutes: Int = 15) = viewModelScope.launch { actions.continueSession(blockId, minutes) }

    /** Finish this one and start the next piece of work straight away. */
    fun moveToNext() = viewModelScope.launch {
        val next = actions.moveToNext(blockId)
        if (next != null) startedNext.value = next.id else finished.value = true
    }

    fun takeBreak(minutes: Int) = viewModelScope.launch {
        actions.takeBreak(minutes, reason = BreakReason.SUGGESTED)
        finished.value = true
    }

    fun skip(resolution: SkipResolution) = viewModelScope.launch {
        actions.skip(blockId, resolution)
        finished.value = true
    }

    private fun minuteOf(epochMillis: Long): Int =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60

    companion object {
        fun create(container: AppContainer, blockId: Long) = FocusViewModel(
            blockId = blockId,
            scheduleRepository = container.scheduleRepository,
            taskRepository = container.taskRepository,
            preferencesRepository = container.preferencesRepository,
            actions = container.scheduleActions,
        )
    }
}
