package com.margin.app.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.SubjectTrack
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.planner.CoverageCalculator
import com.margin.app.domain.planner.ExamIntensity
import com.margin.app.domain.planner.ExamPlanner
import com.margin.app.domain.planner.HistoryLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ExamCoverageHistoryTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)

    private fun exam(days: Long, code: String = "DSA") =
        Exam(id = days, subjectCode = code, title = code, date = monday.plusDays(days))

    private fun completion(
        code: String?,
        type: AcademicType?,
        date: LocalDate,
        minutes: Int,
        planned: Int = minutes,
        start: Int? = 16 * 60,
        category: Category = Category.ACADEMICS,
    ) = CompletionRecord(
        date = date,
        blockId = null,
        taskId = null,
        category = category,
        minutes = minutes,
        at = 0,
        type = BlockType.STUDY,
        subjectCode = code,
        academicType = type,
        plannedMinutes = planned,
        startMinute = start,
    )

    // ---- exam pressure ---------------------------------------------------------------------

    @Test
    fun `exam intensity follows the nearest exam`() {
        assertEquals(ExamIntensity.IMMINENT, ExamPlanner.pressure(monday, listOf(exam(2))).intensity)
        assertEquals(ExamIntensity.CLOSE, ExamPlanner.pressure(monday, listOf(exam(7))).intensity)
        assertEquals(ExamIntensity.APPROACHING, ExamPlanner.pressure(monday, listOf(exam(15))).intensity)
        assertEquals(ExamIntensity.NONE, ExamPlanner.pressure(monday, listOf(exam(30))).intensity)
        assertEquals(ExamIntensity.NONE, ExamPlanner.pressure(monday, listOf(exam(-1))).intensity)
    }

    @Test
    fun `closer exams want more time and more weight`() {
        val days = listOf(1, 3, 7, 14, 21)
        val minutes = days.map { ExamPlanner.studyMinutesFor(it) }
        val weights = days.map { ExamPlanner.importanceFor(it) }
        assertEquals(minutes.sortedDescending(), minutes)
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `exam mode trims build and learning but never removes leisure`() {
        val imminent = ExamPlanner.pressure(monday, listOf(exam(1)))
        assertEquals(0f, imminent.buildFactor)
        assertEquals(0f, imminent.learningFactor(pauseDuringExams = true))
        assertTrue(imminent.leisureFactor > 0f)
        assertTrue(imminent.ceilingFactor > 1f)
    }

    // ---- coverage --------------------------------------------------------------------------

    @Test
    fun `the last class date is found from the weekly timetable`() {
        val weekly = listOf(
            TimetableEntry(dayOfWeek = DayOfWeek.WEDNESDAY, start = 480, end = 535, subjectCode = "DSA", title = "DSA", kind = TimetableKind.LAB),
        )
        val dates = CoverageCalculator.lastClassDates(monday, weekly)
        assertEquals(LocalDate.of(2026, 9, 9), dates[SubjectTrack("DSA", AcademicType.LAB)])
    }

    @Test
    fun `theory and lab coverage are tracked separately`() {
        val theory = SubjectTrack("DSA", AcademicType.THEORY)
        val lab = SubjectTrack("DSA", AcademicType.LAB)
        val coverage = CoverageCalculator.compute(
            date = monday,
            tracks = listOf(theory, lab),
            completions = listOf(completion("DSA", AcademicType.LAB, monday.minusDays(1), 60)),
            skips = emptyList(),
            lastClassByTrack = mapOf(theory to monday.minusDays(4), lab to monday.minusDays(5)),
            exams = emptyList(),
        )
        assertEquals(1, coverage.getValue(lab).daysSinceStudied)
        assertNull(coverage.getValue(theory).daysSinceStudied)
        assertEquals(5, coverage.getValue(theory).neglectDays)
        assertEquals(60, coverage.getValue(lab).weekMinutes)
    }

    @Test
    fun `neglect grows with the days and falls with the time already given`() {
        val track = SubjectTrack("MIS3", AcademicType.THEORY)
        fun score(completions: List<CompletionRecord>): Int {
            val coverage = CoverageCalculator.compute(monday, listOf(track), completions, emptyList(), emptyMap(), emptyList())
            return CoverageCalculator.neglectScore(coverage.getValue(track), importance = 0)
        }
        val twoDays = score(listOf(completion("MIS3", AcademicType.THEORY, monday.minusDays(2), 45)))
        val sixDays = score(listOf(completion("MIS3", AcademicType.THEORY, monday.minusDays(6), 45)))
        assertTrue(sixDays > twoDays)

        val busyWeek = score(
            listOf(
                completion("MIS3", AcademicType.THEORY, monday.minusDays(6), 120),
                completion("MIS3", AcademicType.THEORY, monday.minusDays(5), 120),
            ),
        )
        assertTrue(busyWeek < sixDays)
    }

    // ---- learning from history -----------------------------------------------------------

    @Test
    fun `a subject that keeps running long gets a longer estimate`() {
        val records = listOf(60, 65, 70).mapIndexed { index, actual ->
            completion("DELD", AcademicType.THEORY, monday.minusDays(index + 1L), actual, planned = 45)
        }
        val learned = HistoryLearner.learn(records, emptyList())
        val ratio = learned.ratioFor("DELD:theory")
        assertTrue("ratio $ratio should reflect roughly 65 over 45", ratio in 1.4f..1.5f)
    }

    @Test
    fun `two sessions are not a pattern`() {
        val records = listOf(60, 70).mapIndexed { index, actual ->
            completion("DELD", AcademicType.THEORY, monday.minusDays(index + 1L), actual, planned = 45)
        }
        assertEquals(1f, HistoryLearner.learn(records, emptyList()).ratioFor("DELD:theory"))
    }

    @Test
    fun `an hour that is usually skipped is recognised`() {
        val skips = (1..3).map { day ->
            SkipRecord(
                date = monday.minusDays(day.toLong()),
                blockId = null,
                taskId = null,
                title = "Study",
                at = 0,
                kind = SkipKind.MISSED,
                plannedStart = 14 * 60,
            )
        }
        val learned = HistoryLearner.learn(emptyList(), skips)
        assertTrue(learned.avoids(14))
    }

    @Test
    fun `the best study window comes from when work was actually done`() {
        val records = (1..6).map { day ->
            completion("DSA", AcademicType.THEORY, monday.minusDays(day.toLong()), 45, start = 16 * 60 + 10)
        }
        val window = HistoryLearner.learn(records, emptyList()).bestWindow
        assertNotNull(window)
        assertTrue(window!!.contains(16 * 60 + 10))
    }
}
