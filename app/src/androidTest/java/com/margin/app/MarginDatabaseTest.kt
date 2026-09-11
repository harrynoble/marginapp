package com.margin.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.margin.app.data.db.MarginDatabase
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MarginDatabaseTest {

    private lateinit var database: MarginDatabase
    private lateinit var timetable: TimetableRepository
    private lateinit var tasks: TaskRepository
    private lateinit var schedule: ScheduleRepository

    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarginDatabase::class.java,
        ).allowMainThreadQueries().build()

        timetable = TimetableRepository(
            timetableDao = database.timetableDao(),
            subjectDao = database.subjectDao(),
            routineDao = database.routineDao(),
        )
        tasks = TaskRepository(
            taskDao = database.taskDao(),
            sessionDao = database.taskSessionDao(),
            projectDao = database.projectDao(),
            eventDao = database.eventDao(),
            linkDao = database.resourceLinkDao(),
        )
        schedule = ScheduleRepository(
            scheduleDao = database.scheduleDao(),
            planDao = database.dailyPlanDao(),
            checkInDao = database.checkInDao(),
            historyDao = database.historyDao(),
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun seededTimetableRoundTripsThroughStorage() = runTest {
        TimetableSeed.subjects.forEach { timetable.upsertSubject(it) }
        timetable.replaceAll(TimetableSeed.entries)

        val monday = timetable.entriesFor(today)
        assertTrue("Monday should have classes", monday.isNotEmpty())

        val lab = monday.firstOrNull { it.title.contains("lab", ignoreCase = true) }
        assertNotNull("Monday afternoon is a lab", lab)
        assertEquals(10 * 60 + 55, lab!!.start)
        assertEquals(13 * 60 + 40, lab.end)
        assertEquals(DayOfWeek.MONDAY, lab.dayOfWeek)
    }

    @Test
    fun cancellingAClassRemovesItForThatDateOnly() = runTest {
        timetable.replaceAll(TimetableSeed.entries)
        val before = timetable.entriesFor(today)
        val target = before.first()

        timetable.cancelClass(today, target.id, "Faculty away")

        val afterToday = timetable.entriesFor(today)
        val nextWeek = timetable.entriesFor(today.plusDays(7))

        assertEquals(before.size - 1, afterToday.size)
        assertEquals("next week is unaffected", before.size, nextWeek.size)
    }

    @Test
    fun aHolidayClearsTheWholeDay() = runTest {
        timetable.replaceAll(TimetableSeed.entries)
        timetable.markHoliday(today, "Onam")
        assertTrue(timetable.entriesFor(today).isEmpty())
    }

    @Test
    fun taskProgressAccumulatesAndSurvivesReads() = runTest {
        val id = tasks.create(
            Task(title = "Assignment", estimatedMinutes = 90, category = Category.ACADEMICS),
        )
        tasks.addProgress(id, 45)

        val stored = tasks.task(id)
        assertNotNull(stored)
        assertEquals(45, stored!!.completedMinutes)
        assertEquals(45, stored.remainingMinutes)
    }

    @Test
    fun replanningKeepsFinishedWorkAndReplacesOnlyPlannedBlocks() = runTest {
        val doneId = schedule.insert(
            ScheduleBlock(
                date = today,
                start = 15 * 60,
                end = 15 * 60 + 45,
                type = BlockType.TASK,
                title = "Finished earlier",
                status = BlockStatus.DONE,
            ),
        )
        schedule.insert(
            ScheduleBlock(
                date = today,
                start = 16 * 60,
                end = 17 * 60,
                type = BlockType.TASK,
                title = "Still planned",
                status = BlockStatus.PLANNED,
            ),
        )

        schedule.replacePlannedFrom(
            today,
            0,
            listOf(
                ScheduleBlock(
                    date = today,
                    start = 18 * 60,
                    end = 19 * 60,
                    type = BlockType.TASK,
                    title = "Rebuilt",
                ),
            ),
        )

        val blocks = schedule.blocksFor(today)
        assertNotNull("finished work must survive", blocks.firstOrNull { it.id == doneId })
        assertNull(
            "the old planned block must be gone",
            blocks.firstOrNull { it.title == "Still planned" },
        )
        assertNotNull(blocks.firstOrNull { it.title == "Rebuilt" })
    }

    @Test
    fun lockedBlocksAreNotWipedByAReplan() = runTest {
        schedule.insert(
            ScheduleBlock(
                date = today,
                start = 20 * 60,
                end = 21 * 60,
                type = BlockType.BUILD,
                title = "Pinned build",
                locked = true,
            ),
        )
        schedule.replacePlannedFrom(today, 0, emptyList())

        assertNotNull(schedule.blocksFor(today).firstOrNull { it.title == "Pinned build" })
    }

    @Test
    fun skipsAndCompletionsAreRecordedAsHistory() = runTest {
        val block = ScheduleBlock(
            id = schedule.insert(
                ScheduleBlock(
                    date = today,
                    start = 17 * 60,
                    end = 18 * 60,
                    type = BlockType.TASK,
                    title = "Maths revision",
                ),
            ),
            date = today,
            start = 17 * 60,
            end = 18 * 60,
            type = BlockType.TASK,
            title = "Maths revision",
        )

        schedule.recordCompletion(block, 55)
        schedule.recordSkip(block, "Too tired", SkipResolution.TOMORROW)
        schedule.recordReschedule(block, today.plusDays(1), 17 * 60, "Moved")

        assertEquals(1, schedule.completions(today, today).size)
        assertEquals(1, schedule.skips(today, today).size)
        assertEquals(1, schedule.reschedules(today, today).size)
        assertEquals(55, schedule.completions(today, today).first().minutes)
    }

    @Test
    fun eventsAreScopedToTheirDate() = runTest {
        tasks.createEvent(
            com.margin.app.domain.model.CalendarEvent(
                title = "Out with friends",
                date = today,
                start = 18 * 60,
                end = 20 * 60,
            ),
        )

        assertEquals(1, tasks.eventsOn(today).size)
        assertEquals(0, tasks.eventsOn(today.plusDays(1)).size)
    }

    @Test
    fun replanningFromNowKeepsTheRecordOfTheMorning() = runTest {
        schedule.insert(
            ScheduleBlock(date = today, start = 9 * 60, end = 10 * 60, type = BlockType.STUDY, title = "Morning study"),
        )
        schedule.insert(
            ScheduleBlock(date = today, start = 17 * 60, end = 18 * 60, type = BlockType.STUDY, title = "Evening study"),
        )

        schedule.replacePlannedFrom(
            today,
            12 * 60,
            listOf(ScheduleBlock(date = today, start = 18 * 60, end = 19 * 60, type = BlockType.STUDY, title = "Replanned")),
        )

        val titles = schedule.blocksFor(today).map { it.title }
        assertTrue("a past session is part of the day's record", "Morning study" in titles)
        assertTrue("Evening study" !in titles)
        assertTrue("Replanned" in titles)
    }

    @Test
    fun theSameWorkIsNeverDeferredTwice() = runTest {
        val day = DayRepository(database.dayStateDao(), database.deferredWorkDao(), database.nudgeLogDao())
        val work = DeferredWork(
            sourceKey = "study:DSA:lab:1",
            fromDate = today,
            toDate = today.plusDays(1),
            subjectCode = "DSA",
            academicType = AcademicType.LAB,
            title = "DSA lab",
            minutes = 45,
            reason = "Missed",
        )
        day.defer(listOf(work))
        day.defer(listOf(work.copy(minutes = 30)))

        val carried = day.deferredFor(today.plusDays(1))
        assertEquals(1, carried.size)
        assertEquals(AcademicType.LAB, carried.single().academicType)
    }

    @Test
    fun choicesAboutTheDayRoundTrip() = runTest {
        val day = DayRepository(database.dayStateDao(), database.deferredWorkDao(), database.nudgeLogDao())
        day.update(today) {
            it.copy(
                lightDay = true,
                essentials = setOf("subject:DSA"),
                buildDecision = Decision.DECLINED,
                outUntilMinute = 21 * 60,
            )
        }

        val state = day.state(today)
        assertTrue(state.lightDay)
        assertEquals(setOf("subject:DSA"), state.essentials)
        assertEquals(Decision.DECLINED, state.buildDecision)
        assertEquals(21 * 60, state.outUntilMinute)
        assertEquals("another day is untouched", false, day.state(today.plusDays(1)).lightDay)
    }

    @Test
    fun examsKeepTheirTypeAndClearOnceDone() = runTest {
        val exams = ExamRepository(database.examDao())
        exams.upsert(
            Exam(subjectCode = "DSA", title = "Data Structures", date = today.plusDays(10), academicType = AcademicType.LAB),
        )

        assertEquals(AcademicType.LAB, exams.upcoming(today).single().academicType)
        exams.clearBefore(today.plusDays(11))
        assertTrue(exams.upcoming(today).isEmpty())
    }

    @Test
    fun mappersPreserveEveryFieldThePlannerReadsBack() = runTest {
        val block = ScheduleBlock(
            date = today,
            start = 9 * 60,
            end = 10 * 60,
            type = BlockType.REVIEW,
            title = "DSA review",
            subtitle = "Today in class",
            category = Category.ACADEMICS,
            status = BlockStatus.PLANNED,
            subjectCode = "DSA",
            locked = true,
            reason = "Because it was taught today.",
        )

        val restored = block.toEntity().toDomain()

        assertEquals(block.type, restored.type)
        assertEquals(block.category, restored.category)
        assertEquals(block.subjectCode, restored.subjectCode)
        assertEquals(block.locked, restored.locked)
        assertEquals(block.reason, restored.reason)
        assertEquals(block.range, restored.range)
    }
}
