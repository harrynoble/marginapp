package com.margin.app.planner

import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.planner.BuiltPlan
import com.margin.app.domain.planner.CandidateBuilder
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.planner.DayType
import com.margin.app.domain.planner.PlacedBlock
import com.margin.app.domain.planner.PlanSettings
import com.margin.app.domain.planner.PlannedDay
import com.margin.app.domain.planner.PlannerInput
import com.margin.app.domain.planner.PlannerPreferences
import com.margin.app.domain.planner.PlanningContext
import com.margin.app.domain.planner.QuotaKind
import com.margin.app.domain.planner.WorkCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Weekdays, weekends, holidays and exam periods, planned by the same engine on the real
 * timetable. What each kind of day must hold, and what it must never become.
 */
class DayTypePlanningTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)
    private val saturday: LocalDate = LocalDate.of(2026, 9, 19)
    private val sunday: LocalDate = LocalDate.of(2026, 9, 20)

    private fun m(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val prefs = PlannerPreferences(
        wakeMinute = m(6, 30),
        sleepMinute = m(23),
        leisureWindow = TimeRange(m(19), m(23)),
        buildWindow = TimeRange(m(16), m(22)),
        maxWorkMinutesPerDay = m(4),
        buildMinutesWeekday = 60,
        buildMinutesWeekend = 120,
    )

    private val weekly: List<TimetableEntry> = TimetableSeed.entries.mapIndexed { index, entry -> entry.copy(id = index + 1L) }
    private val project = Project(id = 1, name = "Portfolio site", category = Category.BUILD)
    private val goal = LearningGoal(id = 1, name = "Spanish", sessionMinutes = 30)

    private fun studied(code: String, type: AcademicType, date: LocalDate, minutes: Int) = CompletionRecord(
        date = date,
        blockId = null,
        taskId = null,
        category = Category.ACADEMICS,
        minutes = minutes,
        at = 0,
        type = BlockType.STUDY,
        subjectCode = code,
        academicType = type,
        plannedMinutes = minutes,
        startMinute = m(16),
    )

    /** A week where DSA theory was studied every day and Economics not once. */
    private val week = (1L..5L).map { studied("DSA", AcademicType.THEORY, saturday.minusDays(it), 45) }

    private fun context(
        date: LocalDate,
        holiday: Boolean = false,
        completions: List<CompletionRecord> = week,
        dayState: DayState = DayState(date),
        exams: List<Exam> = emptyList(),
    ) = PlanningContext(
        date = date,
        prefs = prefs,
        settings = PlanSettings(),
        dayState = dayState,
        holiday = holiday,
        classes = if (holiday) emptyList() else weekly.filter { it.dayOfWeek == date.dayOfWeek },
        weeklyEntries = weekly,
        routines = TimetableSeed.routines,
        subjects = TimetableSeed.subjects,
        projects = listOf(project),
        learningGoals = listOf(goal),
        completions = completions,
        exams = exams,
    )

    private val allTracks = setOf(
        "DSA:theory", "DSA:lab", "DELD:theory", "DELD:lab", "ToC:theory", "FP:theory", "MIS3:theory", "EE:theory",
    )

    private fun trackOf(candidate: WorkCandidate) = "${candidate.subjectCode}:${candidate.academicType!!.key}"
    private fun weekly(built: BuiltPlan) = built.work.filter { it.id.startsWith("weekly:") }
    private fun quota(built: BuiltPlan, kind: QuotaKind) = built.quotas.firstOrNull { it.kind == kind }

    private fun plan(built: BuiltPlan, date: LocalDate): PlannedDay = DayPlanner().plan(
        PlannerInput(date = date, prefs = built.prefs, commitments = built.commitments, work = built.work, quotas = built.quotas),
    )

    // ---- weekdays ------------------------------------------------------------------------------

    @Test
    fun `every class taught on a college day earns a review, economics included`() {
        for (offset in 0L..4L) {
            val day = monday.plusDays(offset)
            val built = CandidateBuilder.build(context(day, completions = emptyList()))
            val taught = weekly.filter { it.dayOfWeek == day.dayOfWeek && it.kind.isTeaching }.mapNotNull { it.track?.key }.toSet()
            val reviewed = built.work.filter { it.id.startsWith("review:") }.map(::trackOf).toSet()
            assertEquals("${day.dayOfWeek}", taught, reviewed)
            built.work.filter { it.id.startsWith("review:") }.forEach { assertTrue(it.minutes >= prefs.minReviewSession) }
        }
        val tuesday = CandidateBuilder.build(context(monday.plusDays(1), completions = emptyList()))
        assertNotNull("Economics on Tuesday", tuesday.work.firstOrNull { it.id.startsWith("review:EE:") })
        val thursday = CandidateBuilder.build(context(monday.plusDays(3), completions = emptyList()))
        assertNotNull("Economics on Thursday", thursday.work.firstOrNull { it.id.startsWith("review:EE:") })
    }

    @Test
    fun `a college day is a weekday with travel and classes`() {
        val built = CandidateBuilder.build(context(monday))
        assertEquals(DayType.WEEKDAY, built.dayType)
        assertTrue(built.commitments.any { it.type == BlockType.CLASS })
        assertTrue(built.commitments.any { it.type == BlockType.COMMUTE })
    }

    // ---- weekends ------------------------------------------------------------------------------

    @Test
    fun `saturday is a weekly review of every subject, theory and lab apart`() {
        val built = CandidateBuilder.build(context(saturday))
        assertEquals(DayType.WEEKEND, built.dayType)
        assertEquals(allTracks, weekly(built).map(::trackOf).toSet())
        assertTrue(weekly(built).sumOf { it.minutes } <= built.prefs.effectiveWorkCeiling)
        assertTrue(built.commitments.none { it.type == BlockType.CLASS || it.type == BlockType.COMMUTE })
    }

    @Test
    fun `a subject studied too little gets more weekend time than one studied heavily`() {
        val built = CandidateBuilder.build(context(saturday))
        val economics = weekly(built).single { trackOf(it) == "EE:theory" }.minutes
        val dsaTheory = weekly(built).single { trackOf(it) == "DSA:theory" }.minutes
        assertTrue("Economics $economics vs DSA $dsaTheory", economics > dsaTheory)
    }

    @Test
    fun `weekends give more build, learning and leisure than a college day`() {
        val weekend = CandidateBuilder.build(context(saturday))
        val weekday = CandidateBuilder.build(context(monday))
        assertEquals(120, quota(weekend, QuotaKind.BUILD)!!.minutes)
        assertEquals(60, quota(weekday, QuotaKind.BUILD)!!.minutes)
        assertTrue(quota(weekend, QuotaKind.LEARNING)!!.minutes > quota(weekday, QuotaKind.LEARNING)!!.minutes)
        assertTrue(quota(weekend, QuotaKind.LEISURE)!!.minutes > quota(weekday, QuotaKind.LEISURE)!!.minutes)
    }

    @Test
    fun `not today is respected on a weekend`() {
        val built = CandidateBuilder.build(
            context(saturday, dayState = DayState(saturday, buildDecision = Decision.DECLINED, learningDecision = Decision.DECLINED)),
        )
        assertNull(quota(built, QuotaKind.BUILD))
        assertNull(quota(built, QuotaKind.LEARNING))
        assertNotNull("leisure is still kept", quota(built, QuotaKind.LEISURE))
    }

    @Test
    fun `sunday puts tomorrow's subjects first and says so`() {
        val built = CandidateBuilder.build(context(sunday, completions = emptyList()))
        val fp = weekly(built).single { trackOf(it) == "FP:theory" }
        val economics = weekly(built).single { trackOf(it) == "EE:theory" }
        assertTrue("FP is on Monday, Economics is not", fp.importance > economics.importance)
        assertTrue(fp.reasons.contains("you have it tomorrow"))
        assertTrue(built.notes.any { it.startsWith("Tomorrow's subjects come first") && it.contains("FP") })
    }

    // ---- holidays ------------------------------------------------------------------------------

    @Test
    fun `a holiday takes one date off college without touching the timetable`() {
        val holiday = CandidateBuilder.build(context(monday, holiday = true))
        assertEquals(DayType.HOLIDAY, holiday.dayType)
        assertTrue("no classes on the holiday", holiday.commitments.none { it.type == BlockType.CLASS })
        assertTrue("no travel to college either", holiday.commitments.none { it.type == BlockType.COMMUTE })
        assertEquals(allTracks, weekly(holiday).map(::trackOf).toSet())
        assertEquals(120, quota(holiday, QuotaKind.BUILD)!!.minutes)
        assertNotNull(quota(holiday, QuotaKind.LEARNING))
        assertTrue(holiday.notes.any { it.startsWith("Holiday") })

        val nextMonday = CandidateBuilder.build(context(monday.plusDays(7)))
        assertEquals(DayType.WEEKDAY, nextMonday.dayType)
        assertEquals(
            "the weekly timetable is unchanged",
            weekly.count { it.dayOfWeek == DayOfWeek.MONDAY },
            nextMonday.commitments.count { it.type == BlockType.CLASS || it.type == BlockType.BREAK },
        )
    }

    @Test
    fun `a holiday covers more subjects than the college day it replaces`() {
        val holiday = CandidateBuilder.build(context(monday, holiday = true, completions = emptyList()))
        val weekday = CandidateBuilder.build(context(monday, completions = emptyList()))
        val holidaySubjects = holiday.work.filter { it.category == Category.ACADEMICS }.mapNotNull { it.subjectCode }.toSet()
        val weekdaySubjects = weekday.work.filter { it.category == Category.ACADEMICS }.mapNotNull { it.subjectCode }.toSet()
        assertTrue(holidaySubjects.size > weekdaySubjects.size)
        assertEquals(setOf("DSA", "DELD", "ToC", "FP", "MIS3", "EE"), holidaySubjects)
    }

    // ---- exam periods --------------------------------------------------------------------------

    @Test
    fun `an exam close by makes any day an exam period`() {
        val exam = Exam(id = 1, subjectCode = "DSA", title = "Data Structures", date = saturday.plusDays(3))
        val built = CandidateBuilder.build(context(saturday, exams = listOf(exam)))
        assertEquals(DayType.EXAM_PERIOD, built.dayType)
        assertTrue(built.work.any { it.id.startsWith("exam:") })
        assertTrue(quota(built, QuotaKind.BUILD)?.minutes ?: 0 < 120)
    }

    // ---- the whole day, placed -------------------------------------------------------------------

    @Test
    fun `weekend and holiday plans are realistic, with no overlaps, a slow morning, breaks and rest`() {
        for ((date, holiday) in listOf(saturday to false, sunday to false, monday to true)) {
            val built = CandidateBuilder.build(context(date, holiday = holiday))
            val day = plan(built, date)
            val blocks = day.blocks.filter { it.duration > 0 }.sortedBy { it.start }
            blocks.zipWithNext().forEach { (a, b) -> assertTrue("$date: ${a.title} overlaps ${b.title}", a.end <= b.start) }

            val academic = blocks.filter { it.type == BlockType.STUDY || it.type == BlockType.REVIEW || it.type == BlockType.TASK }
            assertTrue("$date: academic work planned", academic.isNotEmpty())
            assertTrue("$date: nothing before 8:00", academic.all { it.start >= prefs.wakeMinute + 90 })
            assertTrue("$date: within the ceiling", academic.sumOf { it.duration } <= built.prefs.effectiveWorkCeiling)
            assertTrue("$date: longest run ${longestRun(academic)}", longestRun(academic) <= 90)
            assertTrue("$date: leisure kept", day.leisureMinutes >= quota(built, QuotaKind.LEISURE)!!.minutes)
            assertTrue("$date: build planned", day.buildMinutes > 0)
        }
    }

    private fun longestRun(blocks: List<PlacedBlock>): Int {
        var longest = 0
        var run = 0
        var lastEnd = -1
        for (block in blocks.sortedBy { it.start }) {
            run = if (lastEnd >= 0 && block.start - lastEnd <= 10) run + block.duration else block.duration
            longest = maxOf(longest, run)
            lastEnd = block.end
        }
        return longest
    }

    // ---- breaks across a replan ------------------------------------------------------------------

    @Test
    fun `work finished just before a replan counts towards the next break`() {
        val sessions = listOf(
            WorkCandidate(id = "study:a", minutes = 45, title = "A", type = BlockType.STUDY, minSession = 45, maxSession = 45, splittable = false),
            WorkCandidate(id = "study:b", minutes = 45, title = "B", type = BlockType.STUDY, minSession = 45, maxSession = 45, splittable = false),
        )
        val finished = PlacedBlock("settled:1", TimeRange(m(14, 5), m(15, 27)), BlockType.STUDY, "Earlier session", category = Category.ACADEMICS)

        fun firstAfterNow(settled: List<PlacedBlock>) = DayPlanner().plan(
            PlannerInput(date = monday, prefs = prefs, work = sessions, nowMinute = m(15, 30), settled = settled),
        ).blocks.filter { it.start >= m(15, 30) }.minByOrNull { it.start }!!

        assertEquals("82 minutes already worked: rest first", BlockType.BREAK, firstAfterNow(listOf(finished)).type)
        assertEquals("nothing worked yet: start straight away", BlockType.STUDY, firstAfterNow(emptyList()).type)
    }
}
