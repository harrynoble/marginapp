package com.margin.app.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.planner.DayType
import com.margin.app.domain.planner.NudgePlanner
import com.margin.app.domain.planner.NudgeSettings
import com.margin.app.domain.planner.NudgeState
import com.margin.app.domain.planner.NudgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Notifications that understand the kind of day. */
class DayPlanNudgeTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 14)

    private val review = ScheduleBlock(
        id = 10,
        date = date,
        start = 9 * 60,
        end = 9 * 60 + 45,
        type = BlockType.STUDY,
        title = "Engineering Economics review",
        subjectCode = "EE",
        academicType = AcademicType.THEORY,
    )

    private fun plan(dayType: DayType, blocks: List<ScheduleBlock> = listOf(review)) = NudgePlanner.plan(
        state = NudgeState(blocks, DayState(date), checkInDone = false, activeStartMinute = null, posted = emptySet()),
        settings = NudgeSettings(wakeMinute = 6 * 60 + 30, dayType = dayType),
        nowMinute = 7 * 60,
    )

    @Test
    fun `a holiday, a weekend and an exam day each announce their plan, with the first session`() {
        for (type in listOf(DayType.HOLIDAY, DayType.WEEKEND, DayType.EXAM_PERIOD)) {
            val nudge = plan(type).single { it.type == NudgeType.DAY_PLAN }
            assertEquals(type, nudge.dayType)
            assertEquals(review.id, nudge.blockId)
            assertEquals("an hour before the first session", 8 * 60, nudge.atMinute)
        }
    }

    @Test
    fun `an ordinary weekday does not`() {
        assertTrue(plan(DayType.WEEKDAY).none { it.type == NudgeType.DAY_PLAN })
    }

    @Test
    fun `nothing is ever said about a class`() {
        val lecture = ScheduleBlock(id = 20, date = date, start = 8 * 60, end = 8 * 60 + 55, type = BlockType.CLASS, title = "Functional Programming")
        val nudges = plan(DayType.WEEKDAY, listOf(lecture, review)) + plan(DayType.HOLIDAY, listOf(review))
        assertTrue(nudges.none { it.blockId == lecture.id })
    }
}
