package com.margin.app.planner

import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.Task
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * The day mask is shared between the task editor, the routine editor and the planner, so its
 * meaning has to be pinned down: bit 0 is Monday, matching java.time.DayOfWeek.value.
 */
class RecurrenceTest {

    private fun maskOf(vararg days: DayOfWeek) =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    @Test
    fun `a task with no mask is not recurring`() {
        val task = Task(title = "One off", estimatedMinutes = 30)
        assertFalse(task.isRecurring)
        DayOfWeek.entries.forEach { assertFalse(task.recursOn(it)) }
    }

    @Test
    fun `a weekday task recurs on weekdays only`() {
        val task = Task(
            title = "Daily reading",
            estimatedMinutes = 30,
            recurrenceMask = maskOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )

        assertTrue(task.isRecurring)
        assertTrue(task.recursOn(DayOfWeek.MONDAY))
        assertTrue(task.recursOn(DayOfWeek.FRIDAY))
        assertFalse(task.recursOn(DayOfWeek.SATURDAY))
        assertFalse(task.recursOn(DayOfWeek.SUNDAY))
    }

    @Test
    fun `a single day mask matches only that day`() {
        val task = Task(
            title = "Gym",
            estimatedMinutes = 60,
            recurrenceMask = maskOf(DayOfWeek.WEDNESDAY),
        )
        assertTrue(task.recursOn(DayOfWeek.WEDNESDAY))
        assertFalse(task.recursOn(DayOfWeek.TUESDAY))
        assertFalse(task.recursOn(DayOfWeek.THURSDAY))
    }

    @Test
    fun `routines use the same mask convention as tasks`() {
        val weekdayRoutine = Routine(
            title = "Travel to college",
            kind = com.margin.app.domain.model.RoutineKind.COMMUTE,
            category = com.margin.app.domain.model.Category.PERSONAL,
            daysMask = Routine.WEEKDAYS,
            start = 430,
            end = 480,
        )

        assertTrue(weekdayRoutine.appliesTo(DayOfWeek.MONDAY))
        assertTrue(weekdayRoutine.appliesTo(DayOfWeek.FRIDAY))
        assertFalse(weekdayRoutine.appliesTo(DayOfWeek.SATURDAY))

        val everyDay = weekdayRoutine.copy(daysMask = Routine.EVERY_DAY)
        assertTrue(DayOfWeek.entries.all { everyDay.appliesTo(it) })

        val weekends = weekdayRoutine.copy(daysMask = Routine.WEEKENDS)
        assertTrue(weekends.appliesTo(DayOfWeek.SATURDAY))
        assertTrue(weekends.appliesTo(DayOfWeek.SUNDAY))
        assertFalse(weekends.appliesTo(DayOfWeek.MONDAY))
    }

    @Test
    fun `the helper on Routine builds the same mask as the bit arithmetic`() {
        assertTrue(
            Routine.maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY) ==
                maskOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
    }
}
