package com.margin.app.ai

import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.TimetableKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Subject identity: the same course must never become two subjects, and two different courses
 * must never be merged because their names look alike.
 */
class SubjectIdentityTest {

    private val known = TimetableSeed.subjects

    private fun row(day: String, start: String, end: String, code: String?, title: String, kind: String = "lecture") =
        RawEntry(day, start, end, code, title, kind, null, null)

    @Test
    fun `a printed tutorial suffix becomes the tutorial kind of the existing subject`() {
        val result = TimetableImportValidator.validate(
            rows = listOf(
                row("Tue", "09:15", "10:05", "EE(T)", "Engineering Economics(T)"),
                row("Mon", "10:05", "10:55", "MIS3(T)", "Mathematics For Information science-3(T)"),
                row("Fri", "08:00", "08:55", "DSA (T)", "Data Structures Algorithms"),
            ),
            subjectNames = emptyList(),
            known = known,
        )
        val byDay = result.entries.associateBy { it.dayOfWeek }
        assertEquals("EE", byDay.getValue(DayOfWeek.TUESDAY).subjectCode)
        assertEquals(TimetableKind.TUTORIAL, byDay.getValue(DayOfWeek.TUESDAY).kind)
        assertEquals("Engineering Economics", byDay.getValue(DayOfWeek.TUESDAY).title)
        assertEquals("MIS3", byDay.getValue(DayOfWeek.MONDAY).subjectCode)
        assertEquals(TimetableKind.TUTORIAL, byDay.getValue(DayOfWeek.MONDAY).kind)
        assertEquals("DSA", byDay.getValue(DayOfWeek.FRIDAY).subjectCode)
        assertTrue("no new subjects for courses the app already has", result.newSubjectCodes.isEmpty())
    }

    @Test
    fun `a lab stays a lab and a lecture stays a lecture`() {
        val result = TimetableImportValidator.validate(
            rows = listOf(
                row("Mon", "10:55", "1:40", "DELD", "Digital Electronics lab", "lab"),
                row("Thu", "10:55", "11:45", "EE", "Engineering Economics", "lecture"),
            ),
            subjectNames = emptyList(),
            known = known,
        )
        assertEquals(listOf(TimetableKind.LAB, TimetableKind.LECTURE), result.entries.map { it.kind })
    }

    @Test
    fun `an unfamiliar name is flagged for the user, not merged with a lookalike`() {
        val result = TimetableImportValidator.validate(
            rows = listOf(row("Thu", "10:55", "11:45", "ECONOMICS", "Economics")),
            subjectNames = emptyList(),
            known = known,
        )
        assertEquals("ECONOMICS", result.entries.single().subjectCode)
        assertEquals(setOf("ECONOMICS"), result.newSubjectCodes)
    }

    @Test
    fun `the ways people name economics all find the same subject`() {
        listOf("Economics", "economics", "ECO", "eco", "Econ", "EE", "Engineering Economics").forEach { said ->
            assertEquals(said, "EE", SubjectMatcher.match(said, known)?.code)
        }
    }

    @Test
    fun `different subjects are not confused`() {
        assertEquals("DSA", SubjectMatcher.match("data structures", known)?.code)
        assertEquals("DELD", SubjectMatcher.match("digital electronics", known)?.code)
        assertEquals("MIS3", SubjectMatcher.match("maths", known)?.code)
        assertEquals("ToC", SubjectMatcher.match("theory of computation", known)?.code)
        assertNull(SubjectMatcher.match("chemistry", known))
    }

    @Test
    fun `codes are split from their printed type`() {
        assertEquals(SubjectCodes.Split("EE", TimetableKind.TUTORIAL), SubjectCodes.split("EE(T)"))
        assertEquals(SubjectCodes.Split("DSA", TimetableKind.TUTORIAL), SubjectCodes.split("DSA (T)"))
        assertEquals(SubjectCodes.Split("DELD", TimetableKind.LAB), SubjectCodes.split("DELD (Lab)"))
        assertEquals(SubjectCodes.Split("TOC", null), SubjectCodes.split("ToC"))
        assertEquals(SubjectCodes.Split(null, null), SubjectCodes.split("  "))
    }
}
