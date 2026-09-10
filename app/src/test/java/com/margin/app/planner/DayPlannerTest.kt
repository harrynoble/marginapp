package com.margin.app.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.Commitment
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.planner.Diagnostic
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.planner.PlannerInput
import com.margin.app.domain.planner.PlannerPreferences
import com.margin.app.domain.planner.QuotaCandidate
import com.margin.app.domain.planner.QuotaKind
import com.margin.app.domain.planner.WorkCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The engine is the part of Margin that has to be right. These tests describe the behaviour
 * the product promises: protected leisure, realistic transitions, honest refusal, and a plan
 * that does not reshuffle itself for no reason.
 */
class DayPlannerTest {

    private val planner = DayPlanner()
    private val monday: LocalDate = LocalDate.of(2026, 9, 14)

    private fun minutes(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun prefs(
        wake: Int = minutes(6, 30),
        sleep: Int = minutes(23),
        leisureFloor: Int = 90,
        workCeiling: Int = minutes(4),
        commute: Int = 45,
        decompression: Int = 30,
        breakEvery: Int = 85,
        energyMode: EnergyMode = EnergyMode.NORMAL,
    ) = PlannerPreferences(
        wakeMinute = wake,
        sleepMinute = sleep,
        commuteMinutes = commute,
        decompressionMinutes = decompression,
        minLeisureMinutes = leisureFloor,
        leisureWindow = TimeRange(minutes(19), minutes(23)),
        buildWindow = TimeRange(minutes(16), minutes(22)),
        peakWindow = TimeRange(minutes(16), minutes(19)),
        eveningFatigueAfter = minutes(21, 30),
        continuousWorkBeforeBreak = breakEvery,
        maxWorkMinutesPerDay = workCeiling,
        energyMode = energyMode,
    )

    /** The seeded Monday: four periods then a three period lab, 08:00 to 13:40. */
    private fun collegeMonday(): List<Commitment> = listOf(
        classBlock("class:1", "Functional Programming", minutes(8), minutes(8, 55)),
        Commitment(
            id = "class:2",
            title = "Recess",
            range = TimeRange(minutes(8, 55), minutes(9, 15)),
            type = BlockType.BREAK,
            category = Category.LEISURE,
        ),
        classBlock("class:3", "Theory of Computation", minutes(9, 15), minutes(10, 5)),
        classBlock("class:4", "Maths tutorial", minutes(10, 5), minutes(10, 55)),
        classBlock("class:5", "Digital Electronics lab", minutes(10, 55), minutes(13, 40)),
    )

    private fun classBlock(id: String, title: String, start: Int, end: Int) = Commitment(
        id = id,
        title = title,
        range = TimeRange(start, end),
        type = BlockType.CLASS,
        category = Category.ACADEMICS,
        generatesReview = true,
    )

    private fun work(
        id: String,
        minutes: Int,
        title: String = id,
        minSession: Int = 25,
        maxSession: Int = 60,
        deadlineDays: Int? = null,
        priority: Priority = Priority.NORMAL,
        difficulty: Difficulty = Difficulty.MODERATE,
        type: BlockType = BlockType.TASK,
        category: Category = Category.ACADEMICS,
    ) = WorkCandidate(
        id = id,
        minutes = minutes,
        title = title,
        type = type,
        category = category,
        minSession = minSession,
        maxSession = maxSession,
        priority = priority,
        difficulty = difficulty,
        energy = EnergyLevel.MEDIUM,
        daysToDeadline = deadlineDays,
    )

    private fun leisureQuota(minutes: Int) = QuotaCandidate(
        id = "quota:leisure",
        minutes = minutes,
        kind = QuotaKind.LEISURE,
        title = "Leisure",
        window = TimeRange(this.minutes(19), this.minutes(23)),
        minChunk = 30,
        category = Category.LEISURE,
        type = BlockType.LEISURE,
    )

    private fun buildQuota(minutes: Int) = QuotaCandidate(
        id = "quota:build",
        minutes = minutes,
        kind = QuotaKind.BUILD,
        title = "Build",
        window = TimeRange(this.minutes(16), this.minutes(22)),
        minChunk = 30,
        category = Category.BUILD,
        type = BlockType.BUILD,
    )

    // ---- determinism -------------------------------------------------------------------

    @Test
    fun `same input produces an identical plan`() {
        val input = PlannerInput(
            date = monday,
            prefs = prefs(),
            commitments = collegeMonday(),
            work = listOf(work("task:1", 90, deadlineDays = 1), work("task:2", 45)),
            quotas = listOf(leisureQuota(90), buildQuota(60)),
        )

        val first = planner.plan(input)
        val second = planner.plan(input)

        assertEquals(first.blocks, second.blocks)
        assertEquals(first.diagnostics.map { it.message }, second.diagnostics.map { it.message })
    }

    @Test
    fun `candidate order in the input does not change the plan`() {
        val a = work("task:a", 60, deadlineDays = 2)
        val b = work("task:b", 60, deadlineDays = 2)
        val base = PlannerInput(
            date = monday,
            prefs = prefs(),
            commitments = collegeMonday(),
            quotas = listOf(leisureQuota(90)),
        )

        val forward = planner.plan(base.copy(work = listOf(a, b)))
        val reversed = planner.plan(base.copy(work = listOf(b, a)))

        assertEquals(forward.blocks, reversed.blocks)
    }

    // ---- transitions -------------------------------------------------------------------

    @Test
    fun `no work is scheduled in the transition straight after college`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 180, maxSession = 60, deadlineDays = 0)),
                quotas = listOf(leisureQuota(90)),
            ),
        )

        val collegeEnd = minutes(13, 40)
        // A free morning before college is fair game; the minutes immediately after are not.
        val transition = TimeRange(collegeEnd, collegeEnd + 45 + 30)
        val intruding = plan.blocks.filter { it.type.isWork && it.range.overlaps(transition) }
        assertTrue(
            "nothing should be working during travel and settling in, found $intruding",
            intruding.isEmpty(),
        )

        val afterCollege = plan.blocks
            .filter { it.type.isWork && it.start >= collegeEnd }
            .minByOrNull { it.start }
        assertNotNull("expected work in the afternoon", afterCollege)
        assertTrue(afterCollege!!.start >= collegeEnd + 45)
    }

    @Test
    fun `a commute block follows the last class of the day`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 45)),
            ),
        )

        val commute = plan.blocks.firstOrNull { it.type == BlockType.COMMUTE }
        assertNotNull("expected a commute block after college", commute)
        assertEquals(minutes(13, 40), commute!!.start)
        assertEquals(45, commute.duration)
    }

    @Test
    fun `no commute is added on a day with no classes`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = emptyList(),
                work = listOf(work("task:1", 45)),
            ),
        )
        assertTrue(plan.blocks.none { it.type == BlockType.COMMUTE })
    }

    // ---- protected time ----------------------------------------------------------------

    @Test
    fun `leisure is reserved before work is placed`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 90),
                commitments = collegeMonday(),
                // Far more work than the day can hold.
                work = listOf(work("task:big", 600, maxSession = 120, deadlineDays = 0)),
                quotas = listOf(leisureQuota(90)),
            ),
        )

        val leisure = plan.blocks.filter { it.type == BlockType.LEISURE }.sumOf { it.duration }
        assertEquals("the leisure floor must survive an overloaded day", 90, leisure)
    }

    @Test
    fun `work never overlaps reserved leisure`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 300, maxSession = 90, deadlineDays = 0)),
                quotas = listOf(leisureQuota(90)),
            ),
        )

        val leisure = plan.blocks.filter { it.type == BlockType.LEISURE }
        val working = plan.blocks.filter { it.type.isWork }
        for (l in leisure) {
            for (w in working) {
                assertFalse(
                    "work ${w.title} at ${w.start} overlaps leisure at ${l.start}",
                    w.range.overlaps(l.range),
                )
            }
        }
    }

    @Test
    fun `build time is reserved even when academic work is waiting`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 240, maxSession = 60, deadlineDays = 0)),
                quotas = listOf(leisureQuota(90), buildQuota(60)),
            ),
        )

        val build = plan.blocks.filter { it.type == BlockType.BUILD }.sumOf { it.duration }
        assertEquals(60, build)
    }

    @Test
    fun `an impossible leisure floor is reported rather than silently dropped`() {
        val busy = collegeMonday() + Commitment(
            id = "event:1",
            title = "Family thing",
            range = TimeRange(minutes(14, 30), minutes(23)),
            type = BlockType.EVENT,
            category = Category.PERSONAL,
        )

        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 180),
                commitments = busy,
                quotas = listOf(leisureQuota(180)),
            ),
        )

        val overload = plan.diagnostics.filterIsInstance<Diagnostic.Overloaded>()
        assertTrue("expected an overload diagnostic", overload.isNotEmpty())
        assertEquals(QuotaKind.LEISURE, overload.first().kind)
    }

    // ---- breaks ------------------------------------------------------------------------

    @Test
    fun `a break is inserted inside a long unbroken run of work`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(breakEvery = 60, leisureFloor = 0),
                commitments = emptyList(),
                work = listOf(work("task:1", 240, minSession = 30, maxSession = 60, deadlineDays = 0)),
            ),
        )

        val breaks = plan.blocks.filter { it.type == BlockType.BREAK }
        assertTrue("a four hour run needs at least one break", breaks.isNotEmpty())
    }

    @Test
    fun `a light day gets a lower work ceiling`() {
        val heavy = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(energyMode = EnergyMode.NORMAL),
                work = listOf(work("task:1", 600, maxSession = 60, deadlineDays = 0)),
            ),
        )
        val light = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(energyMode = EnergyMode.LIGHT),
                work = listOf(work("task:1", 600, maxSession = 60, deadlineDays = 0)),
            ),
        )

        assertTrue(
            "light day placed ${light.workMinutes}, normal placed ${heavy.workMinutes}",
            light.workMinutes < heavy.workMinutes,
        )
    }

    // ---- splitting and refusal ----------------------------------------------------------

    @Test
    fun `long work is split into sessions rather than one unbroken block`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0),
                work = listOf(work("task:1", 150, minSession = 25, maxSession = 50, deadlineDays = 0)),
            ),
        )

        val sessions = plan.blocks.filter { it.taskId == null && it.type == BlockType.TASK }
        assertTrue("expected more than one session, got ${sessions.size}", sessions.size >= 3)
        assertTrue("no session may exceed the maximum", sessions.all { it.duration <= 50 })
    }

    @Test
    fun `work that cannot fit is reported with what did fit`() {
        val fullDay = listOf(
            Commitment(
                id = "event:1",
                title = "All day thing",
                range = TimeRange(minutes(7), minutes(22, 30)),
                type = BlockType.EVENT,
                category = Category.PERSONAL,
            ),
        )

        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0),
                commitments = fullDay,
                work = listOf(work("task:1", 300, deadlineDays = 0)),
            ),
        )

        val shortfall = plan.diagnostics.filterIsInstance<Diagnostic.InsufficientTime>()
        assertTrue("expected the engine to say what did not fit", shortfall.isNotEmpty())
        assertEquals(1, plan.unplaced.size)
    }

    @Test
    fun `a task is never compressed below its minimum session`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0),
                commitments = listOf(
                    Commitment(
                        id = "event:1",
                        title = "Busy",
                        range = TimeRange(minutes(7), minutes(21, 45)),
                        type = BlockType.EVENT,
                        category = Category.PERSONAL,
                    ),
                ),
                work = listOf(work("task:1", 60, minSession = 45, deadlineDays = 0)),
            ),
        )

        val placed = plan.blocks.filter { it.type == BlockType.TASK }
        assertTrue("a 20 minute stub is worse than nothing", placed.all { it.duration >= 45 })
    }

    // ---- ordering ----------------------------------------------------------------------

    @Test
    fun `work due tomorrow outranks work due next week`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0, workCeiling = 60),
                work = listOf(
                    work("task:later", 60, deadlineDays = 7),
                    work("task:urgent", 60, deadlineDays = 1),
                ),
            ),
        )

        val first = plan.blocks.filter { it.type.isWork }.minByOrNull { it.start }
        assertEquals("task:urgent", first?.title)
    }

    @Test
    fun `demanding work takes the peak window and easy work takes the late slot`() {
        // Free time starts at 16:00, which is the configured peak window, and runs to 23:00.
        // Only one of the two tasks can have the sharp hours.
        val busyUntilFour = listOf(
            Commitment(
                id = "event:1",
                title = "Out all day",
                range = TimeRange(minutes(6, 30), minutes(16)),
                type = BlockType.EVENT,
                category = Category.PERSONAL,
            ),
        )

        val plan = planner.plan(
            PlannerInput(
                date = monday,
                // Decompression off so the comparison is about scoring, not buffers.
                prefs = prefs(leisureFloor = 0, decompression = 0),
                commitments = busyUntilFour,
                work = listOf(
                    // The easy task sorts first by id, so a naive tiebreak would place it first.
                    work("task:a-easy", 60, difficulty = Difficulty.EASY, deadlineDays = 1),
                    work("task:b-hard", 60, difficulty = Difficulty.HARD, deadlineDays = 1),
                ),
            ),
        )

        val hard = plan.blocks.first { it.title == "task:b-hard" }
        val easy = plan.blocks.first { it.title == "task:a-easy" }
        assertTrue(
            "demanding work at ${hard.start} should get the peak hours before ${easy.start}",
            hard.start < easy.start,
        )
        assertTrue("the hard task should start inside the peak window", hard.start >= minutes(16))
    }

    @Test
    fun `revision cannot be scheduled before the class it revises`() {
        // The morning before college is free, and without a floor the engine would happily
        // put the review of a 12:00 class into it.
        val review = work(
            "review:ToC",
            30,
            title = "ToC review",
            minSession = 20,
            maxSession = 30,
            type = BlockType.REVIEW,
        ).copy(earliestStart = minutes(12, 50))

        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0),
                commitments = collegeMonday(),
                work = listOf(review),
            ),
        )

        val placed = plan.blocks.filter { it.type == BlockType.REVIEW }
        assertTrue("the review should still be scheduled", placed.isNotEmpty())
        assertTrue(
            "review placed at ${placed.first().start}, before the class ended",
            placed.all { it.start >= minutes(12, 50) },
        )
    }

    @Test
    fun `an earliest start does not stop other work using the morning`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(leisureFloor = 0),
                commitments = collegeMonday(),
                work = listOf(
                    work("review:ToC", 30, minSession = 20, maxSession = 30, type = BlockType.REVIEW)
                        .copy(earliestStart = minutes(12, 50)),
                    work("task:free", 45, minSession = 30, maxSession = 45, deadlineDays = 0),
                ),
            ),
        )

        val morning = plan.blocks.filter { it.type.isWork && it.start < minutes(8) }
        assertTrue("the free morning should still be usable", morning.isNotEmpty())
        assertTrue(
            "only the unconstrained task may use it",
            morning.all { it.type != BlockType.REVIEW },
        )
    }

    // ---- day boundaries -----------------------------------------------------------------

    @Test
    fun `nothing is scheduled outside the waking frame`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(wake = minutes(7), sleep = minutes(22)),
                work = listOf(work("task:1", 600, maxSession = 120, deadlineDays = 0)),
            ),
        )

        val scheduled = plan.blocks.filter { it.type != BlockType.SLEEP }
        assertTrue(scheduled.all { it.start >= minutes(7) })
        assertTrue(scheduled.all { it.end <= minutes(22) })
    }

    @Test
    fun `a sleep time past midnight clamps the day at midnight`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                // Sleep at 00:30 the next morning.
                prefs = prefs(wake = minutes(7), sleep = minutes(0, 30)),
                work = listOf(work("task:1", 600, maxSession = 120, deadlineDays = 0)),
            ),
        )

        assertTrue("nothing may run past midnight", plan.blocks.all { it.end <= 24 * 60 })
    }

    @Test
    fun `replanning mid-day never places anything in the past`() {
        val nowMinute = minutes(15, 20)
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 90, deadlineDays = 0)),
                quotas = listOf(leisureQuota(90)),
                nowMinute = nowMinute,
            ),
        )

        // Commitments keep their real times; it is the blocks the engine chose that must not
        // land in the past. Those are everything that did not come from a commitment.
        val newlyPlaced = plan.blocks
            .filterNot { it.key.startsWith("class:") }
            .filterNot { it.type == BlockType.SLEEP || it.type == BlockType.COMMUTE }
            .filterNot { it.type == BlockType.DECOMPRESS }
        assertTrue(
            "everything newly placed must start at or after now, got " +
                newlyPlaced.filter { it.start < nowMinute }.map { it.title },
            newlyPlaced.all { it.start >= nowMinute },
        )
    }

    // ---- conflicts and empty days --------------------------------------------------------

    @Test
    fun `overlapping commitments are reported rather than quietly resolved`() {
        val clashing = listOf(
            classBlock("class:1", "Maths", minutes(9), minutes(10)),
            Commitment(
                id = "event:1",
                title = "Dentist",
                range = TimeRange(minutes(9, 30), minutes(10, 30)),
                type = BlockType.EVENT,
                category = Category.HEALTH,
            ),
        )

        val plan = planner.plan(
            PlannerInput(date = monday, prefs = prefs(), commitments = clashing),
        )

        val conflicts = plan.diagnostics.filterIsInstance<Diagnostic.HardConflict>()
        assertEquals(1, conflicts.size)
        assertEquals(30, conflicts.first().range.duration)
    }

    @Test
    fun `an empty day still produces a complete timeline`() {
        val plan = planner.plan(PlannerInput(date = monday, prefs = prefs()))

        assertTrue(plan.blocks.isNotEmpty())
        assertTrue(plan.blocks.any { it.type == BlockType.SLEEP })
        assertTrue(plan.blocks.any { it.type == BlockType.FREE })
        assertTrue(plan.diagnostics.isEmpty())
    }

    @Test
    fun `blocks are returned in chronological order and do not overlap`() {
        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 120, deadlineDays = 1), work("task:2", 60)),
                quotas = listOf(leisureQuota(90), buildQuota(60)),
            ),
        )

        val sorted = plan.blocks.sortedBy { it.start }
        assertEquals(sorted, plan.blocks)

        for (index in 0 until plan.blocks.size - 1) {
            val current = plan.blocks[index]
            val next = plan.blocks[index + 1]
            assertTrue(
                "${current.title} (${current.start}-${current.end}) overlaps " +
                    "${next.title} (${next.start}-${next.end})",
                current.end <= next.start,
            )
        }
    }

    @Test
    fun `settled blocks are preserved untouched`() {
        val done = com.margin.app.domain.planner.PlacedBlock(
            key = "settled:1",
            range = TimeRange(minutes(15), minutes(15, 45)),
            type = BlockType.TASK,
            title = "Already finished",
            status = com.margin.app.domain.model.BlockStatus.DONE,
        )

        val plan = planner.plan(
            PlannerInput(
                date = monday,
                prefs = prefs(),
                commitments = collegeMonday(),
                work = listOf(work("task:1", 90, deadlineDays = 0)),
                nowMinute = minutes(15, 45),
                settled = listOf(done),
            ),
        )

        val preserved = plan.blocks.firstOrNull { it.key == "settled:1" }
        assertNotNull("a finished block must survive replanning", preserved)
        assertEquals(done.range, preserved!!.range)
        assertNull(
            "nothing may be placed on top of finished work",
            plan.blocks.firstOrNull { it.key != "settled:1" && it.range.overlaps(done.range) },
        )
    }
}
