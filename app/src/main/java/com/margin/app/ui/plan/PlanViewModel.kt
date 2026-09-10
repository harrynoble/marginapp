package com.margin.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.ScheduleActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class DaySummary(
    val date: LocalDate,
    val workMinutes: Int,
    val classMinutes: Int,
    val freeMinutes: Int,
    val hasPlan: Boolean,
)

data class PlanUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()),
    val blocks: List<ScheduleBlock> = emptyList(),
    val week: List<DaySummary> = emptyList(),
    val use24Hour: Boolean = false,
    val nowMinute: Int = MarginTime.nowMinute(),
    val showStructural: Boolean = false,
    val loading: Boolean = true,
) {
    val visibleBlocks: List<ScheduleBlock>
        get() = if (showStructural) {
            blocks
        } else {
            blocks.filter { it.type != BlockType.SLEEP }
        }

    val isToday: Boolean get() = selectedDate == LocalDate.now()
}

class PlanViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val taskRepository: TaskRepository,
    private val planningService: PlanningService,
    private val actions: ScheduleActions,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val showStructural = MutableStateFlow(false)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<PlanUiState> = combine(
        selectedDate,
        preferencesRepository.preferences,
        showStructural,
    ) { date, prefs, structural -> Triple(date, prefs, structural) }
        .flatMapLatest { (date, prefs, structural) ->
            val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
            combine(
                scheduleRepository.observeDay(date),
                scheduleRepository.observeRange(weekStart, weekStart.plusDays(6)),
            ) { dayBlocks, weekBlocks ->
                PlanUiState(
                    selectedDate = date,
                    weekStart = weekStart,
                    blocks = dayBlocks.sortedBy { it.start },
                    week = (0..6).map { offset ->
                        val d = weekStart.plusDays(offset.toLong())
                        val forDay = weekBlocks.filter { it.date == d }
                        DaySummary(
                            date = d,
                            workMinutes = forDay.filter { it.type.isWork }.sumOf { it.duration },
                            classMinutes = forDay.filter { it.type == BlockType.CLASS }.sumOf { it.duration },
                            freeMinutes = forDay
                                .filter { it.type == BlockType.FREE || it.type == BlockType.LEISURE }
                                .sumOf { it.duration },
                            hasPlan = forDay.isNotEmpty(),
                        )
                    },
                    use24Hour = prefs.use24HourTime,
                    nowMinute = MarginTime.nowMinute(),
                    showStructural = structural,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun select(date: LocalDate) {
        selectedDate.value = date
        viewModelScope.launch {
            runCatching { planningService.ensurePlan(date) }
        }
    }

    fun shiftWeek(weeks: Long) {
        select(selectedDate.value.plusWeeks(weeks))
    }

    fun toggleStructural() {
        showStructural.value = !showStructural.value
    }

    fun replan() = run {
        _busy.value = true
        viewModelScope.launch {
            runCatching { planningService.replan(selectedDate.value) }
            _busy.value = false
        }
    }

    fun start(blockId: Long) = viewModelScope.launch { actions.start(blockId) }

    fun complete(blockId: Long) = viewModelScope.launch { actions.complete(blockId) }

    fun skip(blockId: Long, resolution: SkipResolution) =
        viewModelScope.launch { actions.skip(blockId, resolution) }

    fun extend(blockId: Long, minutes: Int) =
        viewModelScope.launch { actions.extend(blockId, minutes) }

    fun move(blockId: Long, toMinute: Int) = viewModelScope.launch {
        actions.reschedule(blockId, selectedDate.value, toMinute)
    }

    fun moveToTomorrow(blockId: Long) = viewModelScope.launch {
        val block = scheduleRepository.block(blockId) ?: return@launch
        actions.reschedule(blockId, block.date.plusDays(1), block.start)
    }

    fun togglePin(blockId: Long, locked: Boolean) = viewModelScope.launch {
        actions.setLocked(blockId, locked)
        planningService.replan(selectedDate.value)
    }

    fun addEvent(
        title: String,
        date: LocalDate,
        start: Int,
        end: Int,
        category: Category,
        notes: String?,
    ) = viewModelScope.launch {
        taskRepository.createEvent(
            CalendarEvent(
                title = title,
                date = date,
                start = start,
                end = end,
                category = category,
                notes = notes,
            ),
        )
        planningService.replan(date, LocalDateTime.now())
    }

    companion object {
        fun create(container: AppContainer) = PlanViewModel(
            scheduleRepository = container.scheduleRepository,
            preferencesRepository = container.preferencesRepository,
            taskRepository = container.taskRepository,
            planningService = container.planningService,
            actions = container.scheduleActions,
        )
    }
}
