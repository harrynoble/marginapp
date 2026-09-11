package com.margin.app.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.today.Timeline
import com.margin.app.ui.today.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The user knows their timetable and does not want to read it back every day. These tests pin
 * down that a college day reads as one entry, and that nothing else is swallowed by it.
 */
class TimelineTest {

    private val friday: LocalDate = LocalDate.of(2026, 9, 18)
    private var nextId = 1L

    private fun m(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun block(
        start: Int,
        end: Int,
        type: BlockType,
        title: String = type.name,
        fromTimetable: Boolean = false,
    ) = ScheduleBlock(
        id = nextId++,
        date = friday,
        start = start,
        end = end,
        type = type,
        title = title,
        category = Category.ACADEMICS,
        timetableEntryId = if (fromTimetable) nextId else null,
    )

    private fun lecture(start: Int, end: Int, title: String) =
        block(start, end, BlockType.CLASS, title, fromTimetable = true)

    private fun recess(start: Int, end: Int) =
        block(start, end, BlockType.BREAK, "Recess", fromTimetable = true)

    /** The seeded Friday: travel, six periods with a recess, travel home, then the afternoon. */
    private fun seededFriday() = listOf(
        block(m(7, 10), m(8), BlockType.COMMUTE, "Travel to college"),
        lecture(m(8), m(8, 55), "Data Structures tutorial"),
        lecture(m(8, 55), m(9, 50), "Theory of Computation tutorial"),
        recess(m(9, 50), m(10, 10)),
        lecture(m(10, 10), m(11), "Engineering Economics"),
        lecture(m(11), m(11, 50), "Department hour"),
        lecture(m(11, 50), m(12, 40), "Maths"),
        block(m(12, 40), m(13, 25), BlockType.COMMUTE, "Travel home"),
        block(m(14, 20), m(14, 45), BlockType.REVIEW, "ToC review"),
    )

    @Test
    fun `a college day collapses into a single entry`() {
        val items = Timeline.collapse(seededFriday())
        val college = items.filterIsInstance<TimelineItem.College>()

        assertEquals("one college block, not six periods", 1, college.size)
        assertEquals(5, college.single().classCount)
        assertEquals(m(8), college.single().start)
        assertEquals(m(12, 40), college.single().end)
    }

    @Test
    fun `travel and afternoon work stay visible around college`() {
        val titles = Timeline.collapse(seededFriday()).map {
            when (it) {
                is TimelineItem.Single -> it.block.title
                is TimelineItem.College -> "College"
            }
        }
        assertEquals(
            listOf("Travel to college", "College", "Travel home", "ToC review"),
            titles,
        )
    }

    @Test
    fun `a short free period between classes is still college`() {
        val blocks = listOf(
            lecture(m(8), m(9), "A"),
            block(m(9), m(9, 50), BlockType.FREE, "Free"),
            lecture(m(9, 50), m(10, 40), "B"),
        )
        val items = Timeline.collapse(blocks)
        assertEquals(1, items.size)
        assertTrue(items.single() is TimelineItem.College)
    }

    @Test
    fun `free time after the last class is not folded into college`() {
        val blocks = listOf(
            lecture(m(8), m(9), "A"),
            lecture(m(9), m(10), "B"),
            block(m(10), m(10, 30), BlockType.FREE, "Free"),
            block(m(10, 30), m(11), BlockType.TASK, "Assignment"),
        )
        val items = Timeline.collapse(blocks)
        assertTrue(items[0] is TimelineItem.College)
        assertEquals(m(10), items[0].end)
        assertEquals("Free", (items[1] as TimelineItem.Single).block.title)
    }

    @Test
    fun `a day with no classes passes through untouched`() {
        val blocks = listOf(
            block(m(9), m(10), BlockType.TASK, "Write"),
            block(m(10), m(10, 15), BlockType.BREAK, "Break"),
            block(m(19), m(20), BlockType.LEISURE, "Leisure"),
        )
        val items = Timeline.collapse(blocks)
        assertEquals(3, items.size)
        assertTrue(items.all { it is TimelineItem.Single })
    }

    @Test
    fun `an ordinary break is never mistaken for a college recess`() {
        val blocks = listOf(
            block(m(16), m(17), BlockType.TASK, "Study"),
            block(m(17), m(17, 15), BlockType.BREAK, "Break"),
        )
        assertTrue(Timeline.collapse(blocks).all { it is TimelineItem.Single })
    }

    @Test
    fun `a recess on its own is not presented as college`() {
        val items = Timeline.collapse(listOf(recess(m(9, 50), m(10, 10))))
        assertTrue(items.single() is TimelineItem.Single)
    }

    @Test
    fun `the current period is found inside the collapsed block`() {
        val college = Timeline.collapse(seededFriday())
            .filterIsInstance<TimelineItem.College>()
            .single()

        assertEquals("Engineering Economics", college.periodAt(m(10, 30))?.title)
        assertEquals("Department hour", college.nextPeriodAfter(m(10, 30))?.title)
        assertEquals("Recess", college.periodAt(m(10))?.title)
    }

    @Test
    fun `order is preserved even when the input is unsorted`() {
        val items = Timeline.collapse(seededFriday().shuffled(java.util.Random(7)))
        val starts = items.map { it.start }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `parts of the day split at noon and five`() {
        assertEquals(Timeline.Part.MORNING, Timeline.partOf(m(11, 59)))
        assertEquals(Timeline.Part.AFTERNOON, Timeline.partOf(m(12)))
        assertEquals(Timeline.Part.EVENING, Timeline.partOf(m(17)))
    }
}
