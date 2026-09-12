package com.margin.app.timetable

import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.model.TimetableKind.DEPARTMENT
import com.margin.app.domain.model.TimetableKind.LAB
import com.margin.app.domain.model.TimetableKind.LECTURE
import com.margin.app.domain.model.TimetableKind.MENTORING
import com.margin.app.domain.model.TimetableKind.RECESS
import com.margin.app.domain.model.TimetableKind.TUTORIAL
import com.margin.app.domain.model.TimetableValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/**
 * The shipped timetable against the source: MITS Kochi, S3 CS (A), Jul to Dec 2026.
 *
 * [SOURCE] is transcribed directly from the timetable image, cell by cell, independently of
 * the seed, so this test fails if the seed ever drifts from the paper. "(T)" on the timetable
 * is a tutorial (the course legend spells it out). Two things are read from the layout rather
 * than printed: the three-period DELD (Monday) and DSA (Wednesday) blocks with three faculty
 * are labs, and the gap between periods 4 (ends 11:45) and 5 (starts 12:00) is recess.
 */
class TimetableSourceTest {

    private data class Cell(val day: DayOfWeek, val start: String, val end: String, val code: String?, val kind: TimetableKind)

    private val SOURCE = listOf(
        Cell(MONDAY, "08:00", "08:55", "FP", LECTURE),
        Cell(MONDAY, "08:55", "09:15", null, RECESS),
        Cell(MONDAY, "09:15", "10:05", "ToC", LECTURE),
        Cell(MONDAY, "10:05", "10:55", "MIS3", TUTORIAL),
        Cell(MONDAY, "10:55", "13:40", "DELD", LAB),

        Cell(TUESDAY, "08:00", "08:55", "MIS3", LECTURE),
        Cell(TUESDAY, "08:55", "09:15", null, RECESS),
        Cell(TUESDAY, "09:15", "10:05", "EE", TUTORIAL),
        Cell(TUESDAY, "10:05", "10:55", "DSA", LECTURE),
        Cell(TUESDAY, "10:55", "11:45", "ToC", LECTURE),
        Cell(TUESDAY, "11:45", "12:00", null, RECESS),
        Cell(TUESDAY, "12:00", "12:50", "DELD", TUTORIAL),
        Cell(TUESDAY, "12:50", "13:40", "FP", LECTURE),

        Cell(WEDNESDAY, "08:00", "08:55", "DELD", LECTURE),
        Cell(WEDNESDAY, "08:55", "09:15", null, RECESS),
        Cell(WEDNESDAY, "09:15", "10:05", "FP", LECTURE),
        Cell(WEDNESDAY, "10:05", "10:55", null, MENTORING),
        Cell(WEDNESDAY, "10:55", "13:40", "DSA", LAB),

        Cell(THURSDAY, "08:00", "08:55", "ToC", LECTURE),
        Cell(THURSDAY, "08:55", "09:15", null, RECESS),
        Cell(THURSDAY, "09:15", "10:05", "DELD", LECTURE),
        Cell(THURSDAY, "10:05", "10:55", "DSA", LECTURE),
        Cell(THURSDAY, "10:55", "11:45", "EE", LECTURE),
        Cell(THURSDAY, "11:45", "12:00", null, RECESS),
        Cell(THURSDAY, "12:00", "12:50", "MIS3", LECTURE),
        Cell(THURSDAY, "12:50", "13:40", "FP", LECTURE),

        Cell(FRIDAY, "08:00", "08:55", "DSA", TUTORIAL),
        Cell(FRIDAY, "08:55", "09:50", "ToC", TUTORIAL),
        Cell(FRIDAY, "09:50", "10:10", null, RECESS),
        Cell(FRIDAY, "10:10", "11:00", "EE", LECTURE),
        Cell(FRIDAY, "11:00", "11:50", null, DEPARTMENT),
        Cell(FRIDAY, "11:50", "12:40", "MIS3", LECTURE),
    )

    private fun minute(text: String): Int = text.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    private val source: List<TimetableEntry> = SOURCE.map {
        TimetableEntry(
            dayOfWeek = it.day,
            start = minute(it.start),
            end = minute(it.end),
            subjectCode = it.code,
            title = it.code ?: it.kind.label,
            kind = it.kind,
        )
    }

    private fun key(entry: TimetableEntry) =
        listOf(entry.dayOfWeek, entry.start, entry.end, entry.subjectCode, entry.kind).joinToString("|")

    @Test
    fun `every class in the source is in the seed, and nothing else`() {
        val seed = TimetableSeed.entries.map(::key).sorted()
        val expected = source.map(::key).sorted()
        assertEquals(expected, seed)
    }

    @Test
    fun `the validator finds the seed identical to the source`() {
        val check = TimetableValidator.validate(source, TimetableSeed.entries)
        assertTrue(check.issues.joinToString("\n") { it.description }, check.verified)
    }

    @Test
    fun `no class is duplicated`() {
        val keys = TimetableSeed.entries.map(::key)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `each day has the number of entries the source shows, and the weekend has none`() {
        val perDay = TimetableSeed.entries.groupingBy { it.dayOfWeek }.eachCount()
        assertEquals(mapOf(MONDAY to 5, TUESDAY to 8, WEDNESDAY to 5, THURSDAY to 8, FRIDAY to 6), perDay)
    }

    @Test
    fun `economics appears exactly where the timetable says, and nowhere else`() {
        val economics = TimetableSeed.entries.filter { it.subjectCode == "EE" }
            .map { Triple(it.dayOfWeek, it.start, it.kind) }
            .toSet()
        assertEquals(
            setOf(
                Triple(TUESDAY, minute("09:15"), TUTORIAL),
                Triple(THURSDAY, minute("10:55"), LECTURE),
                Triple(FRIDAY, minute("10:10"), LECTURE),
            ),
            economics,
        )
        assertTrue(TimetableSeed.subjects.any { it.code == "EE" && it.name == "Engineering Economics" })
    }

    @Test
    fun `every subject is on exactly the days the source shows`() {
        val daysBySubject = TimetableSeed.entries.filter { it.subjectCode != null }
            .groupBy({ it.subjectCode!! }, { it.dayOfWeek })
            .mapValues { it.value.toSet() }
        assertEquals(
            mapOf(
                "FP" to setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY),
                "ToC" to setOf(MONDAY, TUESDAY, THURSDAY, FRIDAY),
                "MIS3" to setOf(MONDAY, TUESDAY, THURSDAY, FRIDAY),
                "DELD" to setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY),
                "EE" to setOf(TUESDAY, THURSDAY, FRIDAY),
                "DSA" to setOf(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
            ),
            daysBySubject,
        )
    }

    @Test
    fun `theory and lab stay distinct, only the two lab blocks are labs and tutorials are theory`() {
        val labs = TimetableSeed.entries.filter { it.academicType == AcademicType.LAB }
            .map { it.dayOfWeek to it.subjectCode }
        assertEquals(listOf(MONDAY to "DELD", WEDNESDAY to "DSA"), labs)
        TimetableSeed.entries.filter { it.kind == TUTORIAL }.forEach {
            assertEquals(AcademicType.THEORY, it.academicType)
        }
        val tracks = TimetableSeed.entries.mapNotNull { it.track }.map { it.key }.toSet()
        assertEquals(
            setOf("DSA:theory", "DSA:lab", "DELD:theory", "DELD:lab", "ToC:theory", "FP:theory", "MIS3:theory", "EE:theory"),
            tracks,
        )
    }

    @Test
    fun `every subject in the timetable has a subject record`() {
        val codes = TimetableSeed.entries.mapNotNull { it.subjectCode }.toSet()
        assertEquals(codes, TimetableSeed.subjects.map { it.code }.toSet())
    }
}
