package com.margin.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
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
import java.time.LocalDate
import java.time.LocalDateTime

data class FocusUiState(
    val block: ScheduleBlock? = null,
    val task: Task? = null,
    val links: List<ResourceLink> = emptyList(),
    val use24Hour: Boolean = false,
    val nowMinute: Int = 0,
    val finished: Boolean = false,
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
            delay(10_000L)
        }
    }

    private val finished = MutableStateFlow(false)

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
        finished,
        linksForBlock,
    ) { blocks, prefs, now, isFinished, links ->
        val block = blocks.firstOrNull { it.id == blockId }
        FocusUiState(
            block = block,
            task = null,
            links = links,
            use24Hour = prefs.use24HourTime,
            nowMinute = MarginTime.nowMinute(now),
            finished = isFinished || block == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            val block = scheduleRepository.block(blockId)
            if (block != null && block.status == BlockStatus.PLANNED) {
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

    fun skip(resolution: SkipResolution) = viewModelScope.launch {
        actions.skip(blockId, resolution)
        finished.value = true
    }

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
