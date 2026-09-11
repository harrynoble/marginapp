package com.margin.app.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.planner.CandidateBuilder
import com.margin.app.domain.planner.DayMode
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.planner.PlannerInput
import com.margin.app.domain.planner.PlannerPreferences
import com.margin.app.domain.planner.PlanningContext
import com.margin.app.domain.planner.QuotaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The product rules that decide what a day holds: theory and lab kept apart, no subject left
 * behind, exams reshaping the balance without tunnel vision, and the user's choices respected.
 */
class CandidateBuilderTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)
    private val epoch = monday.toEpochDay()

    private fun m(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val prefs = PlannerPreferences(
        wakeMinute = m(6, 30),
        sleepMinute = m(23),
        leisureWindow = TimeRange(m(19), m(23)),
        buildWindow = TimeRange(m(16), m(22)),
        maxWorkMinutesPerDay = m(4),
    )

    private val subjects = listOf(
        Subject("DSA", "Data Structures and Algorithms", "DSA"),
        Subject("MIS3", "Mathematics for Information Science 3", "Maths"),
        Subject("DELD", "Digital Electronics and Logic Design", "DELD"),
    )

    private fun entry(day: DayOfWeek, start: Int, end: Int, code: String, kind: TimetableKind, id: Long) =
        TimetableEntry(id = id, dayOfWeek = day, start = start, end = end, subjectCode = code, title = code, kind = kind)

    /** Monday: a DSA lecture and the DSA lab. Tuesday: maths. */
    private val weekly = listOf(
        entry(DayOfWeek.MONDAY, m(9, 15), m(10, 5), "DSA", TimetableKind.LECTURE, 1),
        entry(DayOfWeek.MONDAY, m(10, 55), m(13, 40), "DSA", TimetableKind.LAB, 2),
        entry(DayOfWeek.TUESDAY, m(8), m(8, 55), "MIS3", TimetableKind.LECTURE, 3),
    )
    private val mondayClasses = weekly.filter { it.dayOfWeek == DayOfWeek.MONDAY }

    private fun studied(code: String, type: AcademicType, date: LocalDate, minutes: Int = 45) = CompletionRecord(
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

    private fun context(
        dayState: DayState = DayState(monday),
        exams: List<Exam> = emptyList(),
        completions: List<CompletionRecord> = listOf(
            studied("MIS3", AcademicType.THEORY, monday.minusDays(1)),
            studied("DELD", AcademicType.THEORY, monday.minusDays(1)),
        ),
        tasks: List<Task> = emptyList(),
        settled: List<ScheduleBlock> = emptyList(),
        projects: List<Project> = emptyList(),
        goals: List<LearningGoal> = emptyList(),
        deferred: List<DeferredWork> = emptyList(),
        weeklyEntries: List<TimetableEntry> = weekly,
        classes: List<TimetableEntry> = mondayClasses,
    ) = PlanningContext(
        date = monday,
        prefs = prefs,
        dayState = dayState,
        classes = classes,
        weeklyEntries = weeklyEntries,
        subjects = subjects,
        tasks = tasks,
        projects = projects,
        learningGoals = goals,
        exams = exams,
        completions = completions,
        deferred = deferred,
        settled = settled,
    )

    // ---- theory and lab ------------------------------------------------------------------

    @Test
    fun `theory and lab of the same subject become separate reviews`() {
        val built = CandidateBuilder.build(context())

        val theory = built.work.firstOrNull { it.id == "review:DSA:theory:$epoch" }
        val lab = built.work.firstOrNull { it.id == "review:DSA:lab:$epoch" }
        assertNotNull("expected a theory review", theory)
        assertNotNull("expected a lab review", lab)
        assertEquals(AcademicType.THEORY, theory!!.academicType)
        assertEquals(AcademicType.LAB, lab!!.academicType)
        assertTrue(lab.title.contains("lab", ignoreCase = true))
    }

    @Test
    fun `a review cannot start before the class it covers ends`() {
        val built = CandidateBuilder.build(context())
        val lab = built.work.first { it.id == "review:DSA:lab:$epoch" }
        assertEquals(m(13, 40), lab.earliestStart)
    }

    // ---- coverage --------------------------------------------------------------------------

    @Test
    fun `a subject left alone for days gets its own study session`() {
        val built = CandidateBuilder.build(
            context(
                completions = listOf(
                    studied("MIS3", AcademicType.THEORY, monday.minusDays(5)),
                    studied("DELD", AcademicType.THEORY, monday.minusDays(1)),
                ),
            ),
        )

        val maths = built.work.firstOrNull { it.id == "study:MIS3:theory:$epoch" }
        assertNotNull("maths has gone five days and should be planned", maths)
        assertTrue(maths!!.reasons.any { it.contains("5 days") })
    }

    @Test
    fun `a subject studied yesterday is not pushed again`() {
        val built = CandidateBuilder.build(context())
        assertTrue(built.work.none { it.id.startsWith("study:MIS3") })
    }

    @Test
    fun `studying the lab does not count as studying the theory`() {
        val built = CandidateBuilder.build(
            context(
                completions = listOf(
                    studied("MIS3", AcademicType.LAB, monday.minusDays(1)),
                    studied("MIS3", AcademicType.THEORY, monday.minusDays(6)),
                ),
                weeklyEntries = weekly,
            ),
        )
        assertTrue(built.work.any { it.id == "study:MIS3:theory:$epoch" })
    }

    // ---- exams -----------------------------------------------------------------------------

    @Test
    fun `an exam in two days takes priority and switches build off`() {
        val built = CandidateBuilder.build(
            context(
                exams = listOf(Exam(id = 9, subjectCode = "DSA", title = "DSA", date = monday.plusDays(2))),
                projects = listOf(Project(id = 1, name = "W++", category = Category.BUILD)),
                goals = listOf(LearningGoal(id = 2, name = "Kotlin")),
            ),
        )

        val prep = built.work.firstOrNull { it.id == "exam:9:$epoch" }
        assertNotNull(prep)
        assertTrue("exam prep this close must be urgent", prep!!.urgent)
        assertTrue(built.quotas.none { it.kind == QuotaKind.BUILD })
        assertTrue(built.quotas.none { it.kind == QuotaKind.LEARNING })
        assertTrue(built.quotas.any { it.kind == QuotaKind.LEISURE })
        assertTrue(built.notes.any { it.startsWith("Exam mode") })
    }

    @Test
    fun `exam mode does not abandon a neglected subject`() {
        val built = CandidateBuilder.build(
            context(
                exams = listOf(Exam(id = 9, subjectCode = "DSA", title = "DSA", date = monday.plusDays(2))),
                completions = listOf(
                    studied("MIS3", AcademicType.THEORY, monday.minusDays(6)),
                    studied("DELD", AcademicType.THEORY, monday.minusDays(1)),
                ),
            ),
        )
        assertTrue(built.work.any { it.id == "exam:9:$epoch" })
        assertTrue("maths still needs coverage", built.work.any { it.id == "study:MIS3:theory:$epoch" })
    }

    @Test
    fun `the nearer exam gets more weight`() {
        val built = CandidateBuilder.build(
            context(
                exams = listOf(
                    Exam(id = 1, subjectCode = "DSA", title = "DSA", date = monday.plusDays(1)),
                    Exam(id = 2, subjectCode = "MIS3", title = "Maths", date = monday.plusDays(8)),
                ),
            ),
        )
        val near = built.work.first { it.id == "exam:1:$epoch" }
        val far = built.work.first { it.id == "exam:2:$epoch" }
        assertTrue(near.importance > far.importance)
        assertTrue(near.minutes > far.minutes)
    }

    // ---- the user's choices ---------------------------------------------------------------

    @Test
    fun `a light day keeps only what was marked essential`() {
        val built = CandidateBuilder.build(
            context(
                dayState = DayState(monday, lightDay = true, essentials = setOf(DayState.subjectKey("DSA"))),
                completions = listOf(studied("MIS3", AcademicType.THEORY, monday.minusDays(6))),
                tasks = listOf(Task(id = 4, title = "Essay", estimatedMinutes = 60, deadlineDate = monday.plusDays(5))),
            ),
        )
        assertEquals(DayMode.LIGHT, built.mode)
        assertTrue(built.work.isNotEmpty())
        assertTrue(built.work.all { it.subjectCode == "DSA" })
        assertEquals(com.margin.app.domain.planner.EnergyMode.LIGHT, built.prefs.energyMode)
    }

    @Test
    fun `a light day still keeps work that is due today`() {
        val built = CandidateBuilder.build(
            context(
                dayState = DayState(monday, lightDay = true, essentials = setOf(DayState.subjectKey("DSA"))),
                tasks = listOf(Task(id = 4, title = "Lab record", estimatedMinutes = 45, deadlineDate = monday)),
            ),
        )
        assertTrue(built.work.any { it.taskId == 4L })
    }

    @Test
    fun `a subject left out today is removed and explained`() {
        val built = CandidateBuilder.build(context(dayState = DayState(monday, excludedSubjects = setOf("DSA"))))
        assertTrue(built.work.none { it.subjectCode == "DSA" })
        assertTrue(built.notes.any { it.contains("Data Structures") })
    }

    @Test
    fun `declined build is simply not planned`() {
        val projects = listOf(Project(id = 1, name = "W++", category = Category.BUILD))
        val declined = CandidateBuilder.build(
            context(dayState = DayState(monday, buildDecision = Decision.DECLINED), projects = projects),
        )
        assertTrue(declined.quotas.none { it.kind == QuotaKind.BUILD })

        val unasked = CandidateBuilder.build(context(projects = projects))
        val offered = unasked.quotas.first { it.kind == QuotaKind.BUILD }
        assertTrue("an unanswered build block is only offered", offered.optional)
        assertEquals("W++", offered.title)
        assertEquals(1L, offered.projectId)

        val accepted = CandidateBuilder.build(
            context(dayState = DayState(monday, buildDecision = Decision.ACCEPTED), projects = projects),
        )
        assertFalse(accepted.quotas.first { it.kind == QuotaKind.BUILD }.optional)
    }

    @Test
    fun `learning uses a configured goal and is skipped without one`() {
        val none = CandidateBuilder.build(context())
        assertTrue(none.quotas.none { it.kind == QuotaKind.LEARNING })

        val withGoal = CandidateBuilder.build(context(goals = listOf(LearningGoal(id = 3, name = "Kotlin", sessionMinutes = 30))))
        val learning = withGoal.quotas.first { it.kind == QuotaKind.LEARNING }
        assertEquals("Kotlin", learning.title)
        assertEquals(30, learning.minutes)
        assertEquals(Category.LEARNING, learning.category)
    }

    @Test
    fun `an overloaded day becomes a minimum day that keeps the urgent work`() {
        val built = CandidateBuilder.build(
            context(
                tasks = listOf(
                    Task(id = 1, title = "Project report", estimatedMinutes = 600, maxSessionMinutes = 120, deadlineDate = monday),
                ),
                projects = listOf(Project(id = 1, name = "W++", category = Category.BUILD)),
            ),
        )
        assertEquals(DayMode.MINIMUM, built.mode)
        assertTrue(built.work.any { it.taskId == 1L })
        assertTrue(built.quotas.none { it.kind == QuotaKind.BUILD })
        assertTrue(built.notes.isNotEmpty())
    }

    // ---- what today has already used ----------------------------------------------------

    @Test
    fun `a finished review is not planned again`() {
        val done = ScheduleBlock(
            id = 50,
            date = monday,
            start = m(15),
            end = m(15, 20),
            type = BlockType.REVIEW,
            title = "DSA review",
            status = BlockStatus.DONE,
            candidateId = "review:DSA:theory:$epoch",
            plannedMinutes = 20,
            elapsedMinutes = 20,
        )
        val built = CandidateBuilder.build(context(settled = listOf(done)))
        assertNull(built.work.firstOrNull { it.id == "review:DSA:theory:$epoch" })
    }

    @Test
    fun `a missed review is planned again`() {
        val missed = ScheduleBlock(
            id = 51,
            date = monday,
            start = m(15),
            end = m(15, 20),
            type = BlockType.REVIEW,
            title = "DSA review",
            status = BlockStatus.MISSED,
            candidateId = "review:DSA:theory:$epoch",
        )
        val built = CandidateBuilder.build(context(settled = listOf(missed)))
        assertNotNull(built.work.firstOrNull { it.id == "review:DSA:theory:$epoch" })
    }

    @Test
    fun `carried work from yesterday is planned today`() {
        val built = CandidateBuilder.build(
            context(
                deferred = listOf(
                    DeferredWork(
                        id = 5,
                        sourceKey = "study:MIS3:theory:${epoch - 1}",
                        fromDate = monday.minusDays(1),
                        toDate = monday,
                        subjectCode = "MIS3",
                        academicType = AcademicType.THEORY,
                        title = "Maths",
                        minutes = 45,
                        reason = "Unfinished",
                    ),
                ),
            ),
        )
        val carried = built.work.firstOrNull { it.id == "carry:5" }
        assertNotNull(carried)
        assertTrue(carried!!.reasons.any { it.contains("carried over") })
    }

    // ---- the shape of the day, end to end -------------------------------------------------

    @Test
    fun `academics come first, then build, then learning, then leisure`() {
        val built = CandidateBuilder.build(
            context(
                projects = listOf(Project(id = 1, name = "W++", category = Category.BUILD)),
                goals = listOf(LearningGoal(id = 3, name = "Kotlin", sessionMinutes = 30)),
            ),
        )
        val plan = DayPlanner().plan(
            PlannerInput(
                date = monday,
                prefs = built.prefs,
                commitments = built.commitments,
                work = built.work,
                quotas = built.quotas,
            ),
        )
        val academic = plan.blocks.filter { it.type == BlockType.REVIEW }.minOf { it.start }
        val build = plan.blocks.first { it.type == BlockType.BUILD }.start
        val learn = plan.blocks.first { it.type == BlockType.LEARN }.start
        val leisure = plan.blocks.first { it.type == BlockType.LEISURE }.start
        assertTrue("academics $academic before build $build", academic < build)
        assertTrue("build $build before learning $learn", build < learn)
        assertTrue("learning $learn before leisure $leisure", learn < leisure)
        assertTrue(plan.blocks.first { it.type == BlockType.BUILD }.optional)
    }

    @Test
    fun `every placed session carries a reason built from real data`() {
        val built = CandidateBuilder.build(context())
        val plan = DayPlanner().plan(
            PlannerInput(date = monday, prefs = built.prefs, commitments = built.commitments, work = built.work, quotas = built.quotas),
        )
        val review = plan.blocks.first { it.candidateId == "review:DSA:theory:$epoch" }
        assertTrue(review.reason!!.contains("you had DSA class today"))
    }
}
