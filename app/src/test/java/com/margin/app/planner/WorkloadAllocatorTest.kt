package com.margin.app.planner

import com.margin.app.domain.model.Task
import com.margin.app.domain.planner.PlannerPreferences
import com.margin.app.domain.planner.WorkloadAllocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The multi-day split. This is what stops a 90 minute assignment due Friday from landing on
 * Wednesday as one unbroken block.
 */
class WorkloadAllocatorTest {

    private val prefs = PlannerPreferences()
    private val wednesday: LocalDate = LocalDate.of(2026, 9, 16)
    private val friday: LocalDate = LocalDate.of(2026, 9, 18)

    private fun task(
        minutes: Int,
        deadline: LocalDate? = null,
        done: Int = 0,
        deadlineMinute: Int? = null,
        minSession: Int = 25,
        maxSession: Int = 60,
    ) = Task(
        id = 1,
        title = "Assignment",
        estimatedMinutes = minutes,
        completedMinutes = done,
        minSessionMinutes = minSession,
        maxSessionMinutes = maxSession,
        deadlineDate = deadline,
        deadlineMinute = deadlineMinute,
    )

    @Test
    fun `ninety minutes due friday is spread across wednesday and thursday`() {
        val today = WorkloadAllocator.minutesFor(task(90, friday), wednesday, prefs)
        assertEquals("half the work should land today", 45, today)
    }

    @Test
    fun `work due today is not spread at all`() {
        val today = WorkloadAllocator.minutesFor(task(90, wednesday), wednesday, prefs)
        assertEquals(90, today)
    }

    @Test
    fun `overdue work is taken in full`() {
        val overdue = WorkloadAllocator.minutesFor(
            task(120, wednesday.minusDays(2)),
            wednesday,
            prefs,
        )
        assertEquals(120, overdue)
    }

    @Test
    fun `a morning deadline excludes the deadline day from the split`() {
        // Due Friday at 09:00 leaves Wednesday and Thursday, so the work halves.
        val withMorningDeadline = WorkloadAllocator.minutesFor(
            task(120, friday, deadlineMinute = 9 * 60),
            wednesday,
            prefs,
        )
        assertEquals(60, withMorningDeadline)

        // An evening deadline on the same day leaves three days, and the engine aims to
        // finish one day early, so the split is the same rather than smaller.
        val withEveningDeadline = WorkloadAllocator.minutesFor(
            task(120, friday, deadlineMinute = 22 * 60),
            wednesday,
            prefs,
        )
        assertEquals(60, withEveningDeadline)
    }

    @Test
    fun `work with no deadline gets one session a day`() {
        val today = WorkloadAllocator.minutesFor(task(300, null, maxSession = 60), wednesday, prefs)
        assertEquals(60, today)
    }

    @Test
    fun `progress already made is not scheduled again`() {
        val today = WorkloadAllocator.minutesFor(
            task(120, friday, done = 90),
            wednesday,
            prefs,
        )
        assertTrue("only the remaining 30 minutes are left to place", today <= 30)
    }

    @Test
    fun `a packed day takes a smaller share than an open one`() {
        val capacities = listOf(
            WorkloadAllocator.DayCapacity(wednesday, freeMinutes = 60),
            WorkloadAllocator.DayCapacity(wednesday.plusDays(1), freeMinutes = 300),
            WorkloadAllocator.DayCapacity(friday, freeMinutes = 300),
        )
        val evenSplit = WorkloadAllocator.minutesFor(task(200, friday), wednesday, prefs)
        val weighted = WorkloadAllocator.minutesFor(task(200, friday), wednesday, prefs, capacities)

        assertTrue(
            "a day with an hour free should not take more than an even split ($weighted vs $evenSplit)",
            weighted <= evenSplit,
        )
    }

    @Test
    fun `nothing is allocated for finished work`() {
        assertEquals(0, WorkloadAllocator.minutesFor(task(60, friday, done = 60), wednesday, prefs))
    }

    @Test
    fun `capacity estimate never goes negative`() {
        val estimate = WorkloadAllocator.estimateCapacity(prefs, committedMinutes = 20 * 60)
        assertEquals(0, estimate)
    }
}
