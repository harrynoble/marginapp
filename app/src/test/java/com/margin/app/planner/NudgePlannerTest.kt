package com.margin.app.planner

import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.planner.BreakAdvisor
import com.margin.app.domain.planner.NudgePlanner
import com.margin.app.domain.planner.NudgeSettings
import com.margin.app.domain.planner.NudgeState
import com.margin.app.domain.planner.NudgeType
import com.margin.app.domain.planner.WorkRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Notifications guide the day, and restraint is part of that: each thing is said once, at the
 * right moment, never overnight and never stale.
 */
class NudgePlannerTest {

    private val day: LocalDate = LocalDate.of(2026, 9, 14)
    private fun m(hour: Int, minute: Int = 0) = hour * 60 + minute
    private val settings = NudgeSettings(wakeMinute = m(6, 30), sleepMinute = m(23), leadMinutes = 5, missedGraceMinutes = 15)

    private fun block(
        id: Long,
        start: Int,
        end: Int,
        type: BlockType,
        status: BlockStatus = BlockStatus.PLANNED,
        category: Category = Category.ACADEMICS,
        optional: Boolean = false,
    ) = ScheduleBlock(
        id = id,
        date = day,
        start = start,
        end = end,
        type = type,
        title = "block $id",
        category = category,
        status = status,
        optional = optional,
    )

    private val college = block(1, m(8), m(13, 40), BlockType.CLASS)

    private fun plan(
        blocks: List<ScheduleBlock>,
        now: Int,
        posted: Set<String> = emptySet(),
        dayState: DayState = DayState(day),
        activeStart: Int? = null,
    ) = NudgePlanner.plan(
        NudgeState(blocks, dayState, checkInDone = false, activeStartMinute = activeStart, posted = posted),
        settings,
        now,
    )

    @Test
    fun `the first study session after college gets a ready-to-study prompt`() {
        val review = block(2, m(15), m(15, 45), BlockType.REVIEW)
        val nudges = plan(listOf(college, review), now = m(14))
        val start = nudges.firstOrNull { it.type == NudgeType.STUDY_START }
        assertNotNull(start)
        assertEquals(m(14, 55), start!!.atMinute)
        assertEquals(2L, start.blockId)
    }

    @Test
    fun `a prompt already posted today is never posted again`() {
        val review = block(2, m(15), m(15, 45), BlockType.REVIEW)
        val nudges = plan(listOf(college, review), now = m(14), posted = setOf("study-start"))
        assertTrue(nudges.none { it.type == NudgeType.STUDY_START })
    }

    @Test
    fun `the end of a running session asks whether to move on`() {
        val active = block(2, m(15), m(15, 45), BlockType.REVIEW, BlockStatus.ACTIVE)
        val next = block(3, m(15, 45), m(16, 30), BlockType.STUDY)
        val nudges = plan(listOf(college, active, next), now = m(15, 20), activeStart = m(15))

        val transition = nudges.first { it.type == NudgeType.TRANSITION }
        assertEquals(m(15, 45), transition.atMinute)
        assertEquals(3L, transition.nextBlockId)
        // The session straight after is covered by the transition prompt, not announced again.
        assertTrue(nudges.none { it.type == NudgeType.UP_NEXT && it.blockId == 3L })
    }

    @Test
    fun `a session that has not started gets one are-you-out check, until it ends`() {
        val review = block(2, m(15), m(15, 45), BlockType.REVIEW)
        val nudges = plan(listOf(college, review), now = m(15, 16))
        val missed = NudgePlanner.due(nudges, m(15, 16)).firstOrNull { it.type == NudgeType.MISSED_CHECK }
        assertNotNull(missed)

        val later = plan(listOf(college, review), now = m(15, 50))
        assertTrue(NudgePlanner.due(later, m(15, 50)).none { it.type == NudgeType.MISSED_CHECK })
    }

    @Test
    fun `nothing is posted during sleeping hours`() {
        val late = block(2, m(23, 30), m(23, 55), BlockType.TASK)
        val nudges = plan(listOf(late), now = m(22))
        assertTrue(nudges.none { it.blockId == 2L })
    }

    @Test
    fun `build is offered once academics are done, and not after a no`() {
        val review = block(2, m(15), m(15, 45), BlockType.REVIEW, BlockStatus.DONE)
        val build = block(3, m(17), m(18), BlockType.BUILD, category = Category.BUILD, optional = true)
        val offered = plan(listOf(college, review, build), now = m(16))
        val prompt = offered.firstOrNull { it.type == NudgeType.BUILD_PROMPT }
        assertNotNull(prompt)
        assertEquals(m(16), prompt!!.atMinute)

        val declined = plan(
            listOf(college, review, build),
            now = m(16),
            dayState = DayState(day, buildDecision = Decision.DECLINED),
        )
        assertNull(declined.firstOrNull { it.type == NudgeType.BUILD_PROMPT })
    }

    @Test
    fun `a break is suggested from the work actually done`() {
        val earlier = block(2, m(14), m(15), BlockType.STUDY, BlockStatus.DONE)
        val active = block(3, m(15), m(16, 30), BlockType.REVIEW, BlockStatus.ACTIVE)
        val nudges = plan(listOf(earlier, active), now = m(15, 20), activeStart = m(15))
        val suggestion = nudges.firstOrNull { it.type == NudgeType.BREAK_SUGGESTION }
        assertNotNull(suggestion)
        assertEquals(m(15, 25), suggestion!!.atMinute)
        assertEquals(15, suggestion.suggestedMinutes)
    }

    @Test
    fun `the highest-ranked due prompt comes first`() {
        val active = block(2, m(15), m(15, 45), BlockType.REVIEW, BlockStatus.ACTIVE)
        val next = block(3, m(15, 45), m(16, 30), BlockType.STUDY)
        val nudges = plan(listOf(active, next), now = m(15, 45), activeStart = m(15))
        assertEquals(NudgeType.TRANSITION, NudgePlanner.due(nudges, m(15, 45)).first().type)
    }

    @Test
    fun `the evening review is offered only on a day with something to review`() {
        val quiet = plan(listOf(college), now = m(21, 30))
        assertTrue(quiet.none { it.type == NudgeType.DAILY_REVIEW })

        val done = block(2, m(15), m(15, 45), BlockType.REVIEW, BlockStatus.DONE)
        val worked = plan(listOf(college, done), now = m(21, 30))
        assertTrue(worked.any { it.type == NudgeType.DAILY_REVIEW })
    }

    @Test
    fun `an extended run or a low energy day earns its break sooner`() {
        val run = WorkRun(startMinute = m(15), minutes = 60, sessions = 1, extended = false)
        val normal = BreakAdvisor.evaluate(run, m(16), 85, lowEnergy = false)!!
        val tired = BreakAdvisor.evaluate(run, m(16), 85, lowEnergy = true)!!
        val extended = BreakAdvisor.evaluate(run.copy(extended = true), m(16), 85, lowEnergy = false)!!
        assertEquals(m(16) + 25, normal.atMinute)
        assertEquals(m(16) + 10, tired.atMinute)
        assertEquals(m(16) + 10, extended.atMinute)
    }
}
