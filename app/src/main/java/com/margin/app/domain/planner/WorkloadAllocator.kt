package com.margin.app.domain.planner

import com.margin.app.domain.model.Task
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Decides how much of a task should happen on one particular day.
 *
 * This is what stops a 90-minute assignment due on Friday from being dumped into Wednesday
 * as a single unbroken block. It spreads the work across the days that remain, aiming to
 * finish a day before the deadline when there is room for that.
 *
 * Pure and deterministic; the day being planned is passed in, never read from the clock.
 */
object WorkloadAllocator {

    /** How much free time a future day is expected to have. */
    data class DayCapacity(val date: LocalDate, val freeMinutes: Int)

    /**
     * Minutes of [task] to schedule on [date].
     *
     * @param capacities free-time estimates for the days from [date] up to the deadline.
     *   When empty, the split is purely by day count.
     */
    fun minutesFor(
        task: Task,
        date: LocalDate,
        prefs: PlannerPreferences,
        capacities: List<DayCapacity> = emptyList(),
    ): Int {
        val remaining = task.remainingMinutes
        if (remaining <= 0) return 0

        val minSession = task.minSessionMinutes.coerceAtLeast(5)
        val maxSession = task.maxSessionMinutes.coerceAtLeast(minSession)

        val deadline = task.deadlineDate
        if (deadline == null) {
            // No deadline: a steady one session a day, never the whole thing at once.
            return minOf(remaining, maxSession)
        }

        if (!deadline.isAfter(date)) {
            // Due today or overdue. All of it, capped only by what is genuinely left.
            return remaining
        }

        val daysInclusive = (deadline.toEpochDay() - date.toEpochDay()).toInt() + 1
        // A deadline early on the due date means that day is not usable working time.
        val usableDays = if ((task.deadlineMinute ?: (23 * 60)) < NOON) {
            (daysInclusive - 1).coerceAtLeast(1)
        } else {
            daysInclusive
        }
        // With three or more days in hand, aim to be done a day early.
        val targetDays = if (usableDays >= 3) usableDays - 1 else usableDays

        val byCount = prefs.roundUp(ceil(remaining.toDouble() / targetDays).toInt())

        val byCapacity = if (capacities.isEmpty()) {
            byCount
        } else {
            val window = capacities
                .filter { !it.date.isBefore(date) && !it.date.isAfter(deadline) }
                .sortedBy { it.date }
            val totalCapacity = window.sumOf { it.freeMinutes }
            val todayCapacity = window.firstOrNull { it.date == date }?.freeMinutes ?: 0
            if (totalCapacity <= 0 || todayCapacity <= 0) {
                byCount
            } else {
                // Take a share of the work proportional to how much of the remaining free
                // time today actually holds. A packed Thursday should not get an equal split.
                val share = remaining.toDouble() * todayCapacity / totalCapacity
                prefs.roundUp(ceil(share).toInt())
            }
        }

        val target = maxOf(byCount, byCapacity)
        return target
            .coerceAtMost(remaining)
            .coerceAtLeast(minOf(minSession, remaining))
            .coerceAtMost(maxOf(remaining.coerceAtMost(maxSession * 2), minSession))
    }

    /**
     * A cheap estimate of the free minutes on a day, used to weight the split above.
     * Deliberately approximate: the real number only exists once the day is planned.
     */
    fun estimateCapacity(
        prefs: PlannerPreferences,
        committedMinutes: Int,
    ): Int {
        val waking = if (prefs.sleepMinute <= prefs.wakeMinute) {
            (24 * 60) - prefs.wakeMinute
        } else {
            prefs.sleepMinute - prefs.wakeMinute
        }
        val reserved = prefs.effectiveLeisureFloor + prefs.commuteMinutes + prefs.decompressionMinutes
        return (waking - committedMinutes - reserved).coerceAtLeast(0)
    }

    private const val NOON = 12 * 60
}
