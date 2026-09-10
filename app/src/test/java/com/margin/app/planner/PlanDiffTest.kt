package com.margin.app.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.ChangeKind
import com.margin.app.domain.planner.PlacedBlock
import com.margin.app.domain.planner.PlanDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling the user what moved is a product requirement, not a nicety. A move must read as a
 * move rather than as a deletion and an unrelated addition.
 */
class PlanDiffTest {

    private fun block(
        key: String,
        start: Int,
        duration: Int = 60,
        title: String = key,
        type: BlockType = BlockType.TASK,
        taskId: Long? = null,
    ) = PlacedBlock(
        key = key,
        range = TimeRange(start, start + duration),
        type = type,
        title = title,
        taskId = taskId,
    )

    @Test
    fun `a task that shifts later is reported as a move`() {
        val before = listOf(block("a", 960, taskId = 1))
        val after = listOf(block("a", 1215, taskId = 1))

        val diff = PlanDiff.of(before, after)
        val change = diff.meaningful.single()

        assertEquals(ChangeKind.MOVED, change.kind)
        assertEquals(960, change.from?.start)
        assertEquals(1215, change.to?.start)
    }

    @Test
    fun `a block with no task identity is still recognised across a move`() {
        val before = listOf(block("x", 960, title = "DSA review", type = BlockType.REVIEW))
        val after = listOf(block("y", 1140, title = "DSA review", type = BlockType.REVIEW))

        val diff = PlanDiff.of(before, after)
        assertEquals(ChangeKind.MOVED, diff.meaningful.single().kind)
    }

    @Test
    fun `repeats of the same thing are matched in time order`() {
        val before = listOf(
            block("b1", 900, 30, "Break", BlockType.BREAK),
            block("b2", 1080, 30, "Break", BlockType.BREAK),
        )
        val after = listOf(
            block("b1", 900, 30, "Break", BlockType.BREAK),
            block("b2", 1140, 30, "Break", BlockType.BREAK),
        )

        val diff = PlanDiff.of(before, after)
        assertEquals("only the second break moved", 1, diff.meaningful.size)
        assertEquals(1080, diff.meaningful.single().from?.start)
    }

    @Test
    fun `a shorter block is reported as shortened rather than moved`() {
        val before = listOf(block("a", 960, 90, taskId = 1))
        val after = listOf(block("a", 960, 45, taskId = 1))

        assertEquals(ChangeKind.SHORTENED, PlanDiff.of(before, after).meaningful.single().kind)
    }

    @Test
    fun `dropped and added work are both reported`() {
        val before = listOf(block("a", 960, taskId = 1))
        val after = listOf(block("b", 960, taskId = 2))

        val diff = PlanDiff.of(before, after)
        assertEquals(2, diff.meaningful.size)
        assertTrue(diff.meaningful.any { it.kind == ChangeKind.REMOVED })
        assertTrue(diff.meaningful.any { it.kind == ChangeKind.ADDED })
    }

    @Test
    fun `free time and sleep shuffling is not reported as news`() {
        val before = listOf(
            block("f", 1200, 60, "Free", BlockType.FREE),
            block("s", 1380, 60, "Sleep", BlockType.SLEEP),
        )
        val after = listOf(
            block("f", 1260, 60, "Free", BlockType.FREE),
            block("s", 1380, 60, "Sleep", BlockType.SLEEP),
        )

        assertTrue(PlanDiff.of(before, after).isQuiet)
    }

    @Test
    fun `an unchanged plan is quiet`() {
        val blocks = listOf(block("a", 960, taskId = 1), block("b", 1080, taskId = 2))
        assertTrue(PlanDiff.of(blocks, blocks).isQuiet)
    }

    @Test
    fun `a first plan reports everything as added`() {
        val after = listOf(block("a", 960, taskId = 1), block("b", 1080, taskId = 2))
        val diff = PlanDiff.of(emptyList(), after)

        assertEquals(2, diff.meaningful.size)
        assertTrue(diff.meaningful.all { it.kind == ChangeKind.ADDED })
    }
}
