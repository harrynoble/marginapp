package com.margin.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The validator is the only thing standing between model output and the database, so these
 * tests are mostly about what it refuses.
 */
class CommandValidatorTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 14)

    private fun validator(
        knownTasks: Set<Long> = setOf(1L),
        knownBlocks: Set<Long> = setOf(10L),
    ) = CommandValidator(
        taskExists = { it in knownTasks },
        blockExists = { it in knownBlocks },
    )

    @Test
    fun `an unknown action is rejected`() = runTest {
        val result = validator().validate(listOf(AiCommandDto(action = "drop_database")), today)
        assertTrue(result.commands.isEmpty())
        assertEquals(1, result.rejections.size)
    }

    @Test
    fun `a task without a title is rejected`() = runTest {
        val result = validator().validate(
            listOf(AiCommandDto(action = AiActions.CREATE_TASK, minutes = 60)),
            today,
        )
        assertTrue(result.commands.isEmpty())
        assertTrue(result.rejections.first().reason.contains("title"))
    }

    @Test
    fun `an absurd duration is rejected`() = runTest {
        val result = validator().validate(
            listOf(AiCommandDto(action = AiActions.CREATE_TASK, title = "Read", minutes = 5000)),
            today,
        )
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `a task with a sensible shape is accepted`() = runTest {
        val result = validator().validate(
            listOf(
                AiCommandDto(
                    action = AiActions.CREATE_TASK,
                    title = "Finish DSA assignment",
                    minutes = 90,
                    deadline = "2026-09-18",
                    category = "academics",
                    priority = "high",
                ),
            ),
            today,
        )
        assertEquals(1, result.commands.size)
        val command = result.commands.first() as ValidatedCommand.CreateTask
        assertEquals("Finish DSA assignment", command.task.title)
        assertEquals(90, command.task.estimatedMinutes)
        assertEquals(LocalDate.of(2026, 9, 18), command.task.deadlineDate)
    }

    @Test
    fun `an event that ends before it starts is rejected`() = runTest {
        val result = validator().validate(
            listOf(
                AiCommandDto(
                    action = AiActions.CREATE_EVENT,
                    title = "Out",
                    start = "20:00",
                    end = "18:00",
                ),
            ),
            today,
        )
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `an event spanning most of two days is rejected`() = runTest {
        val result = validator().validate(
            listOf(
                AiCommandDto(
                    action = AiActions.CREATE_EVENT,
                    title = "Marathon",
                    start = "00:30",
                    end = "23:30",
                ),
            ),
            today,
        )
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `a hallucinated task id cannot reach the database`() = runTest {
        val result = validator().validate(
            listOf(AiCommandDto(action = AiActions.COMPLETE_TASK, taskId = 999L)),
            today,
        )
        assertTrue(result.commands.isEmpty())
        assertTrue(result.rejections.first().reason.contains("no longer exists"))
    }

    @Test
    fun `a hallucinated block id cannot be moved`() = runTest {
        val result = validator().validate(
            listOf(AiCommandDto(action = AiActions.MOVE_BLOCK, blockId = 42L, start = "19:00")),
            today,
        )
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `a deadline decades away is rejected`() = runTest {
        val result = validator().validate(
            listOf(
                AiCommandDto(
                    action = AiActions.CREATE_TASK,
                    title = "Someday",
                    minutes = 30,
                    deadline = "2099-01-01",
                ),
            ),
            today,
        )
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `relative dates resolve against the day being planned`() {
        assertEquals(today, CommandValidator.parseDate("today", today))
        assertEquals(today.plusDays(1), CommandValidator.parseDate("tomorrow", today))
        // 14 September 2026 is a Monday, so Friday is the 18th.
        assertEquals(LocalDate.of(2026, 9, 18), CommandValidator.parseDate("friday", today))
        assertEquals(null, CommandValidator.parseDate("someday", today))
    }

    @Test
    fun `the number of commands applied at once is capped`() = runTest {
        val many = List(30) { AiCommandDto(action = AiActions.TAKE_BREAK, minutes = 10) }
        val result = validator().validate(many, today)
        assertTrue("a single message must not apply 30 changes", result.commands.size <= 8)
    }

    @Test
    fun `action none is silently ignored rather than rejected`() = runTest {
        val result = validator().validate(listOf(AiCommandDto(action = AiActions.NONE)), today)
        assertTrue(result.commands.isEmpty())
        assertTrue(result.rejections.isEmpty())
    }
}
