package com.margin.app.planner

import com.margin.app.core.MarginTime
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.subtractAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangeTest {

    @Test
    fun `touching ranges do not overlap`() {
        val morning = TimeRange(540, 600)
        val next = TimeRange(600, 660)
        assertFalse(morning.overlaps(next))
        assertNull(morning.intersect(next))
    }

    @Test
    fun `removing a middle slice leaves two pieces`() {
        val pieces = TimeRange(540, 720).minus(TimeRange(600, 630))
        assertEquals(listOf(TimeRange(540, 600), TimeRange(630, 720)), pieces)
    }

    @Test
    fun `removing a range that covers everything leaves nothing`() {
        assertTrue(TimeRange(600, 660).minus(TimeRange(540, 720)).isEmpty())
    }

    @Test
    fun `subtracting a list of blocks yields the gaps between them`() {
        val day = listOf(TimeRange(480, 1320))
        val busy = listOf(TimeRange(480, 820), TimeRange(1200, 1260))
        val free = day.subtractAll(busy)
        assertEquals(listOf(TimeRange(820, 1200), TimeRange(1260, 1320)), free)
    }

    @Test
    fun `contains is inclusive of the start and exclusive of the end`() {
        val range = TimeRange(600, 660)
        assertTrue(range.contains(600))
        assertTrue(range.contains(659))
        assertFalse(range.contains(660))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a backwards range is rejected at construction`() {
        TimeRange(700, 600)
    }
}

class MarginTimeTest {

    @Test
    fun `times parse in the forms a person actually types`() {
        assertEquals(16 * 60 + 30, MarginTime.parseTime("16:30"))
        assertEquals(16 * 60 + 30, MarginTime.parseTime("4:30 pm"))
        assertEquals(16 * 60, MarginTime.parseTime("4pm"))
        assertEquals(16 * 60 + 30, MarginTime.parseTime("1630"))
        assertEquals(0, MarginTime.parseTime("12am"))
        assertEquals(12 * 60, MarginTime.parseTime("12pm"))
        assertEquals(9 * 60 + 5, MarginTime.parseTime("9.05"))
    }

    @Test
    fun `nonsense times are rejected rather than guessed`() {
        assertNull(MarginTime.parseTime(""))
        assertNull(MarginTime.parseTime("half past"))
        assertNull(MarginTime.parseTime("25:00"))
        assertNull(MarginTime.parseTime("10:75"))
    }

    @Test
    fun `durations read the way a person would say them`() {
        assertEquals("45 min", MarginTime.formatDuration(45))
        assertEquals("1h", MarginTime.formatDuration(60))
        assertEquals("1h 30m", MarginTime.formatDuration(90))
        assertEquals("0 min", MarginTime.formatDuration(0))
        assertEquals("0 min", MarginTime.formatDuration(-10))
    }

    @Test
    fun `clock formatting handles both conventions and the edges`() {
        assertEquals("00:00", MarginTime.formatTime(0, use24Hour = true))
        assertEquals("12:00 AM", MarginTime.formatTime(0, use24Hour = false))
        assertEquals("12:00 PM", MarginTime.formatTime(720, use24Hour = false))
        assertEquals("11:59 PM", MarginTime.formatTime(1439, use24Hour = false))
        assertEquals("23:59", MarginTime.formatTime(1439, use24Hour = true))
    }

    @Test
    fun `a minute past midnight wraps rather than throwing`() {
        assertEquals("00:00", MarginTime.formatTime(1440, use24Hour = true))
    }
}
