package com.margin.app.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStateMachine
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.planner.Carryover
import com.margin.app.domain.planner.PlannedWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Block states can only move in ways that make sense, and missed work never simply vanishes. */
class StateAndCarryoverTest {

    private val day: LocalDate = LocalDate.of(2026, 9, 14)

    private fun block(
        status: BlockStatus,
        minutes: Int = 45,
        candidate: String = "review:DSA:theory:1",
        elapsed: Int = 0,
        planned: Int = 0,
        resolution: SkipResolution? = null,
        start: Int = 900,
    ) = ScheduleBlock(
        date = day,
        start = start,
        end = start + minutes,
        type = BlockType.REVIEW,
        title = "DSA review",
        status = status,
        candidateId = candidate,
        elapsedMinutes = elapsed,
        plannedMinutes = planned,
        skipResolution = resolution,
    )

    @Test
    fun `the state machine allows the real life of a session and nothing else`() {
        assertTrue(BlockStateMachine.canMove(BlockStatus.PLANNED, BlockStatus.ACTIVE))
        assertTrue(BlockStateMachine.canMove(BlockStatus.ACTIVE, BlockStatus.PAUSED))
        assertTrue(BlockStateMachine.canMove(BlockStatus.PAUSED, BlockStatus.ACTIVE))
        assertTrue(BlockStateMachine.canMove(BlockStatus.ACTIVE, BlockStatus.DONE))
        assertTrue("start now after being marked missed", BlockStateMachine.canMove(BlockStatus.MISSED, BlockStatus.ACTIVE))
        assertFalse(BlockStateMachine.canMove(BlockStatus.DONE, BlockStatus.ACTIVE))
        assertFalse(BlockStateMachine.canMove(BlockStatus.SKIPPED, BlockStatus.PLANNED))
        assertTrue(BlockStateMachine.isTerminal(BlockStatus.RESCHEDULED))
        assertTrue(BlockStateMachine.isTerminal(BlockStatus.INTERRUPTED))
    }

    @Test
    fun `skipped and missed blocks free their time`() {
        assertFalse(BlockStateMachine.occupiesTime(block(BlockStatus.SKIPPED)))
        assertFalse(BlockStateMachine.occupiesTime(block(BlockStatus.MISSED)))
        assertTrue(BlockStateMachine.occupiesTime(block(BlockStatus.DONE)))
        assertTrue(BlockStateMachine.occupiesTime(block(BlockStatus.ACTIVE)))
    }

    @Test
    fun `finishing early still counts the whole session`() {
        // Planned for 45, finished after 30: the block was shortened, the work was done.
        val done = block(BlockStatus.DONE, minutes = 30, elapsed = 30, planned = 45)
        assertEquals(45, Carryover.consumedMinutes(done))
    }

    @Test
    fun `only a decision about today uses up a skipped session`() {
        assertEquals(0, Carryover.consumedMinutes(block(BlockStatus.SKIPPED, resolution = SkipResolution.LATER_TODAY)))
        assertEquals(45, Carryover.consumedMinutes(block(BlockStatus.SKIPPED, resolution = SkipResolution.TOMORROW)))
        assertEquals(0, Carryover.consumedMinutes(block(BlockStatus.MISSED)))
        assertEquals(45, Carryover.consumedMinutes(block(BlockStatus.RESCHEDULED)))
    }

    @Test
    fun `missed work that was made up later is not carried`() {
        val intended = listOf(PlannedWork("review:DSA:theory:1", "DSA review", "DSA", AcademicType.THEORY, 45))
        val blocks = listOf(
            block(BlockStatus.MISSED, start = 900),
            block(BlockStatus.DONE, start = 1100, elapsed = 45, planned = 45),
        )
        assertTrue(Carryover.unfinished(intended, blocks, capMinutes = 90).isEmpty())
    }

    @Test
    fun `missed work is carried, within the daily cap`() {
        val intended = listOf(
            PlannedWork("study:MIS3:theory:1", "Maths", "MIS3", AcademicType.THEORY, 60),
            PlannedWork("study:DELD:theory:1", "DELD", "DELD", AcademicType.THEORY, 60),
            PlannedWork("task:4", "Essay", null, null, 60),
        )
        val blocks = listOf(
            block(BlockStatus.MISSED, minutes = 60, candidate = "study:MIS3:theory:1"),
            block(BlockStatus.MISSED, minutes = 60, candidate = "study:DELD:theory:1"),
        )
        val carried = Carryover.unfinished(intended, blocks, capMinutes = 90)
        assertEquals(2, carried.size)
        assertEquals(90, carried.sumOf { it.minutes })
        assertTrue("tasks carry themselves and are never deferred", carried.none { it.sourceKey.startsWith("task:") })
    }

    @Test
    fun `carried work is not carried a second time`() {
        val intended = listOf(PlannedWork("carry:5", "Maths", "MIS3", AcademicType.THEORY, 45))
        val blocks = listOf(block(BlockStatus.MISSED, candidate = "carry:5"))
        assertTrue(Carryover.unfinished(intended, blocks, capMinutes = 90).isEmpty())
    }
}
