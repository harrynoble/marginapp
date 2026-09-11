package com.margin.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The offline parser. It only needs to handle the phrases a person actually types, and it
 * must say so plainly when it does not understand rather than inventing a change.
 */
class LocalCommandParserTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 14)

    @Test
    fun `going out in the evening becomes a hard event`() {
        val parsed = LocalCommandParser.parse("I have to go out from 6 to 8", monday)
        val command = parsed.commands.single()
        assertEquals(AiActions.CREATE_EVENT, command.action)
        assertEquals("18:00", command.start)
        assertEquals("20:00", command.end)
        assertEquals("today", command.date)
    }

    @Test
    fun `an explicit morning is not pushed into the evening`() {
        val parsed = LocalCommandParser.parse("out from 6am to 8am", monday)
        val command = parsed.commands.single()
        assertEquals("06:00", command.start)
        assertEquals("08:00", command.end)
    }

    @Test
    fun `tomorrow is carried through`() {
        val parsed = LocalCommandParser.parse("I am out from 6 to 8 tomorrow", monday)
        assertEquals("tomorrow", parsed.commands.single().date)
    }

    @Test
    fun `a deadline becomes a task with a due date`() {
        val parsed = LocalCommandParser.parse(
            "I need to finish my data structures assignment by friday",
            monday,
        )
        val command = parsed.commands.single()
        assertEquals(AiActions.CREATE_TASK, command.action)
        assertEquals("2026-09-18", command.deadline)
        assertTrue(command.title!!.contains("assignment", ignoreCase = true))
    }

    @Test
    fun `a stated duration is used instead of the default`() {
        val parsed = LocalCommandParser.parse("study maths for 90 minutes tomorrow", monday)
        assertEquals(90, parsed.commands.single().minutes)
    }

    @Test
    fun `asking for a break produces a break of the length asked for`() {
        val parsed = LocalCommandParser.parse("I need another 30 minute break", monday)
        val command = parsed.commands.single()
        assertEquals(AiActions.TAKE_BREAK, command.action)
        assertEquals(30, command.minutes)
    }

    @Test
    fun `a bare break request uses a sensible default`() {
        val parsed = LocalCommandParser.parse("taking a break", monday)
        assertEquals(15, parsed.commands.single().minutes)
    }

    @Test
    fun `being tired makes the day lighter rather than deleting work`() {
        val parsed = LocalCommandParser.parse("I am tired today", monday)
        val command = parsed.commands.single()
        assertEquals(AiActions.SET_ENERGY, command.action)
        assertEquals("light", command.mode)
    }

    @Test
    fun `an hour of building every weekday sets the build target`() {
        val parsed = LocalCommandParser.parse(
            "I want at least 1 hour for building every weekday",
            monday,
        )
        val command = parsed.commands.single()
        assertEquals(AiActions.SET_BUILD, command.action)
        assertEquals(60, command.minutes)
    }

    @Test
    fun `asking what to do is answered from the plan`() {
        val parsed = LocalCommandParser.parse("what should I do now", monday)
        assertEquals(AiActions.WHAT_NOW, parsed.commands.single().action)
    }

    @Test
    fun `asking to rebuild the day replans`() {
        val parsed = LocalCommandParser.parse("replan my afternoon", monday)
        assertEquals(AiActions.REPLAN, parsed.commands.single().action)
    }

    @Test
    fun `something it cannot parse produces no commands and says so`() {
        val parsed = LocalCommandParser.parse("ponder the nature of Tuesdays", monday)
        assertTrue(parsed.commands.isEmpty())
        assertTrue(parsed.reply.contains("could not", ignoreCase = true))
    }

    @Test
    fun `an empty message changes nothing`() {
        val parsed = LocalCommandParser.parse("   ", monday)
        assertTrue(parsed.commands.isEmpty())
    }

    @Test
    fun `durations are read in the forms people write them`() {
        assertEquals(90, LocalCommandParser.firstDuration("for 90 minutes"))
        assertEquals(120, LocalCommandParser.firstDuration("2 hours of work"))
        assertEquals(60, LocalCommandParser.firstDuration("an hour"))
        assertEquals(30, LocalCommandParser.firstDuration("half an hour"))
        assertEquals(45, LocalCommandParser.firstDuration("45m"))
        assertNull(LocalCommandParser.firstDuration("sometime later"))
    }
}
