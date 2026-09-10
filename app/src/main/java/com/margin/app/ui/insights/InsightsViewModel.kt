package com.margin.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class InsightsRange(val days: Long, val label: String) {
    WEEK(7, "7 days"),
    FORTNIGHT(14, "14 days"),
    MONTH(30, "30 days"),
}

data class DayBar(val date: LocalDate, val workMinutes: Int, val completedMinutes: Int)

data class TimeOfDaySlice(val label: String, val minutes: Int)

data class InsightsUiState(
    val range: InsightsRange = InsightsRange.WEEK,
    val plannedBlocks: Int = 0,
    val completedBlocks: Int = 0,
    val skippedBlocks: Int = 0,
    val rescheduled: Int = 0,
    val minutesByCategory: List<Pair<Category, Int>> = emptyList(),
    val days: List<DayBar> = emptyList(),
    val timeOfDay: List<TimeOfDaySlice> = emptyList(),
    val daysWithWork: Int = 0,
    val averageSessionMinutes: Int = 0,
    val loading: Boolean = true,
) {
    val completionRate: Float
        get() {
            val attempted = completedBlocks + skippedBlocks
            return if (attempted == 0) 0f else completedBlocks.toFloat() / attempted
        }

    val totalCompletedMinutes: Int get() = days.sumOf { it.completedMinutes }

    val bestPeriod: String?
        get() = timeOfDay.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }?.label
}

/**
 * Self-awareness, not gamification. There are no streaks to protect and no badges: just what
 * actually happened, so patterns like "Thursdays are always overloaded" become visible.
 */
class InsightsViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val range = MutableStateFlow(InsightsRange.WEEK)

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<InsightsUiState> = range.flatMapLatest { selected ->
        val today = LocalDate.now()
        val from = today.minusDays(selected.days - 1)
        combine(
            scheduleRepository.observeRange(from, today),
            scheduleRepository.observeSkips(from, today),
            scheduleRepository.observeReschedules(from, today),
        ) { blocks, skips, reschedules ->
            val work = blocks.filter { it.type.isWork }
            val completed = work.filter { it.status == BlockStatus.DONE }

            val byCategory = blocks
                .filter { it.status == BlockStatus.DONE || it.type == BlockType.CLASS }
                .groupBy { it.category }
                .map { (category, list) ->
                    category to list.sumOf {
                        if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration
                    }
                }
                .sortedByDescending { it.second }

            val days = (0 until selected.days).map { offset ->
                val date = from.plusDays(offset)
                val forDay = work.filter { it.date == date }
                DayBar(
                    date = date,
                    workMinutes = forDay.sumOf { it.duration },
                    completedMinutes = forDay
                        .filter { it.status == BlockStatus.DONE }
                        .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration },
                )
            }

            val buckets = listOf(
                "Morning" to (5 * 60 until 12 * 60),
                "Afternoon" to (12 * 60 until 17 * 60),
                "Evening" to (17 * 60 until 21 * 60),
                "Night" to (21 * 60 until 24 * 60),
            )
            val timeOfDay = buckets.map { (label, window) ->
                TimeOfDaySlice(
                    label = label,
                    minutes = completed.filter { it.start in window }
                        .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration },
                )
            }

            InsightsUiState(
                range = selected,
                plannedBlocks = work.size,
                completedBlocks = completed.size,
                skippedBlocks = skips.size,
                rescheduled = reschedules.size,
                minutesByCategory = byCategory,
                days = days,
                timeOfDay = timeOfDay,
                daysWithWork = days.count { it.completedMinutes > 0 },
                averageSessionMinutes = if (completed.isEmpty()) {
                    0
                } else {
                    completed.sumOf {
                        if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration
                    } / completed.size
                },
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(value: InsightsRange) {
        range.value = value
    }

    companion object {
        fun create(container: AppContainer) = InsightsViewModel(container.scheduleRepository)
    }
}
