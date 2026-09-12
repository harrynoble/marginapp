package com.margin.app.timetable

import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.TimetableIssue.Kind
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.model.TimetableValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/** The check that stops a timetable from changing silently. */
class TimetableValidatorTest {

    private val confirmed = TimetableSeed.entries
    private val tuesdayEconomics = confirmed.first { it.subjectCode == "EE" && it.dayOfWeek == DayOfWeek.TUESDAY }

    private fun kinds(stored: List<com.margin.app.domain.model.TimetableEntry>) =
        TimetableValidator.validate(confirmed, stored).issues.map { it.kind }

    @Test
    fun `an identical week is verified`() {
        val check = TimetableValidator.validate(confirmed, confirmed.map { it.copy(id = 99) })
        assertTrue(check.verified)
        assertEquals(confirmed.size, check.confirmedCount)
    }

    @Test
    fun `a dropped class is reported as missing`() {
        val issues = TimetableValidator.validate(confirmed, confirmed - tuesdayEconomics).issues
        assertEquals(listOf(Kind.MISSING), issues.map { it.kind })
        assertTrue(issues.single().description.contains("EE"))
        assertTrue(issues.single().description.startsWith("Tuesday 09:15"))
    }

    @Test
    fun `an invented class is reported as unexpected`() {
        val extra = tuesdayEconomics.copy(dayOfWeek = DayOfWeek.MONDAY, start = 14 * 60, end = 15 * 60)
        assertEquals(listOf(Kind.UNEXPECTED), kinds(confirmed + extra))
    }

    @Test
    fun `a class stored twice is reported as a duplicate`() {
        assertEquals(listOf(Kind.DUPLICATE), kinds(confirmed + tuesdayEconomics))
    }

    @Test
    fun `a shifted time, a changed type and a swapped subject are each caught`() {
        val shifted = confirmed.map { if (it == tuesdayEconomics) it.copy(end = it.end + 10) else it }
        assertEquals(listOf(Kind.WRONG_TIME), kinds(shifted))

        val retyped = confirmed.map { if (it == tuesdayEconomics) it.copy(kind = TimetableKind.LAB) else it }
        assertEquals(listOf(Kind.WRONG_TYPE), kinds(retyped))

        val swapped = confirmed.map { if (it == tuesdayEconomics) it.copy(subjectCode = "DSA") else it }
        assertEquals(listOf(Kind.WRONG_SUBJECT), kinds(swapped))

        val moved = confirmed.map { if (it == tuesdayEconomics) it.copy(start = 16 * 60, end = 16 * 60 + 50) else it }
        assertEquals(listOf(Kind.WRONG_TIME), kinds(moved))
    }

    @Test
    fun `subject codes are compared without regard to case`() {
        val lower = confirmed.map { it.copy(subjectCode = it.subjectCode?.lowercase()) }
        assertTrue(TimetableValidator.validate(confirmed, lower).verified)
    }
}
