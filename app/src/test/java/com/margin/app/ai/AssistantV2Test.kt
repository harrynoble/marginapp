package com.margin.app.ai

import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The sentences from the brief, and the gate they pass through. Understanding is loose;
 * validation is strict, and a subject is only ever one the user actually has.
 */
class AssistantV2Test {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)

    private val subjects = listOf(
        Subject("DSA", "Data Structures and Algorithms", "DSA"),
        Subject("MIS3", "Mathematics for Information Science 3", "Maths"),
        Subject("DELD", "Digital Electronics and Logic Design", "DELD"),
    )

    private val validator = CommandValidator(
        taskExists = { false },
        blockExists = { false },
        subjects = { subjects },
    )

    private fun action(text: String) = LocalCommandParser.parse(text, monday).commands.single()

    // ---- understanding -------------------------------------------------------------------

    @Test
    fun `going out tonight blocks the rest of the day`() {
        val command = action("I'm going out tonight")
        assertEquals(AiActions.GO_OUT, command.action)
        assertNull(command.until)
    }

    @Test
    fun `going out until a time keeps the time`() {
        val command = action("I'm out until 9")
        assertEquals(AiActions.GO_OUT, command.action)
        assertEquals("9", command.until)
        assertEquals(21 * 60, CommandValidator.eveningTime("9"))
    }

    @Test
    fun `lighter days, less learning and project time are understood`() {
        assertEquals(AiActions.LIGHTEN_DAY, action("Make today lighter").action)
        assertEquals(AiActions.NO_LEARNING_TODAY, action("I don't want to learn anything today").action)
        val build = action("I want one hour to work on my project")
        assertEquals(AiActions.BUILD_TODAY, build.action)
        assertEquals(60, build.minutes)
    }

    @Test
    fun `leaving a subject out today is understood`() {
        val command = action("I don't want to study Mathematics today")
        assertEquals(AiActions.SKIP_SUBJECT_TODAY, command.action)
        assertEquals("Mathematics", command.subject)
    }

    @Test
    fun `keeping going on a subject extends the session`() {
        val command = action("I want to study Digital Electronics for another 30 minutes")
        assertEquals(AiActions.EXTEND_CURRENT, command.action)
        assertEquals(30, command.minutes)
    }

    @Test
    fun `an exam with a date is added`() {
        val command = action("DS exam on Nov 12")
        assertEquals(AiActions.ADD_EXAM, command.action)
        assertEquals("DS", command.subject)
        assertEquals("2026-11-12", command.date)
    }

    @Test
    fun `missed sessions and why questions are recognised`() {
        assertEquals(AiActions.MISSED_SESSION, action("I missed my study session").action)
        assertEquals(AiActions.EXPLAIN, action("why is maths at 5").action)
    }

    @Test
    fun `the rest of the day is not mistaken for a break`() {
        val command = action("I'm out for the rest of the day")
        assertEquals(AiActions.GO_OUT, command.action)
    }

    // ---- validation ------------------------------------------------------------------------

    @Test
    fun `a subject named loosely is resolved to its real code`() = runBlocking {
        val result = validator.validate(
            listOf(AiCommandDto(action = AiActions.SKIP_SUBJECT_TODAY, subject = "Mathematics")),
            monday,
        )
        val command = result.commands.single() as ValidatedCommand.ExcludeSubject
        assertEquals("MIS3", command.subjectCode)
    }

    @Test
    fun `a subject that does not exist is refused`() = runBlocking {
        val result = validator.validate(
            listOf(AiCommandDto(action = AiActions.SKIP_SUBJECT_TODAY, subject = "Chemistry")),
            monday,
        )
        assertTrue(result.commands.isEmpty())
        assertEquals(1, result.rejections.size)
    }

    @Test
    fun `an exam in the past is refused and a future one is accepted`() = runBlocking {
        val past = validator.validate(listOf(AiCommandDto(action = AiActions.ADD_EXAM, subject = "DS", date = "2026-09-01")), monday)
        assertTrue(past.commands.isEmpty())

        val future = validator.validate(listOf(AiCommandDto(action = AiActions.ADD_EXAM, subject = "DS", date = "nov 12")), monday)
        val exam = (future.commands.single() as ValidatedCommand.AddExam).exam
        assertEquals("DSA", exam.subjectCode)
        assertEquals(LocalDate.of(2026, 11, 12), exam.date)
    }

    @Test
    fun `back tomorrow means out for the rest of today`() = runBlocking {
        val result = validator.validate(listOf(AiCommandDto(action = AiActions.GO_OUT, until = "tomorrow")), monday)
        assertNull((result.commands.single() as ValidatedCommand.GoOut).backMinute)
    }

    @Test
    fun `an unreadable return time is refused rather than guessed`() = runBlocking {
        val result = validator.validate(listOf(AiCommandDto(action = AiActions.GO_OUT, until = "whenever")), monday)
        assertTrue(result.commands.isEmpty())
    }

    // ---- subjects ---------------------------------------------------------------------------

    @Test
    fun `subjects are matched by code, short name, initials and words`() {
        assertEquals("MIS3", SubjectMatcher.match("maths", subjects)?.code)
        assertEquals("DELD", SubjectMatcher.match("DELD", subjects)?.code)
        assertEquals("DELD", SubjectMatcher.match("digital electronics", subjects)?.code)
        assertEquals("DSA", SubjectMatcher.match("DS", subjects)?.code)
        assertNull(SubjectMatcher.match("chemistry", subjects))
    }

    // ---- timetable import ------------------------------------------------------------------

    @Test
    fun `an imported timetable keeps theory and lab apart and reads afternoon times`() {
        val result = TimetableImportValidator.validate(
            rows = listOf(
                RawEntry("Mon", "09:15", "10:05", "DSA", "Data Structures", "lecture", null, null),
                RawEntry("Mon", "10:55", "1:40", "DSA", "Data Structures lab", "practical", null, null),
                RawEntry("Funday", "09:00", "10:00", "DELD", "Digital", "lecture", null, null),
                RawEntry("Tue", "11:00", "10:00", "MIS3", "Maths", "lecture", null, null),
            ),
            subjectNames = listOf("DSA" to "Data Structures and Algorithms"),
            known = emptyList(),
        )
        assertEquals(2, result.entries.size)
        assertEquals(TimetableKind.LECTURE, result.entries[0].kind)
        assertEquals(TimetableKind.LAB, result.entries[1].kind)
        assertEquals(13 * 60 + 40, result.entries[1].end)
        assertEquals(DayOfWeek.MONDAY, result.entries[1].dayOfWeek)
        assertEquals(2, result.warnings.size)
        assertEquals("Data Structures and Algorithms", result.subjects.single().name)
    }
}
