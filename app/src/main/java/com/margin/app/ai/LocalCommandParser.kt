package com.margin.app.ai

import com.margin.app.core.MarginTime
import java.time.LocalDate

/**
 * The offline half of the natural-language bar.
 *
 * When no key is configured, or the network is gone, the common phrasings still work. This is
 * not a language model and does not pretend to be: it recognises a handful of shapes, and when
 * it recognises nothing it says so plainly instead of guessing.
 *
 * Deliberately character-based rather than regex-heavy so the behaviour is easy to follow.
 */
object LocalCommandParser {

    data class Parsed(val commands: List<AiCommandDto>, val reply: String)

    fun parse(input: String, today: LocalDate): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed(emptyList(), "Nothing to do with an empty message.")
        val lower = text.lowercase()

        breakRequest(lower)?.let { return it }
        energyRequest(lower)?.let { return it }
        leisureRequest(lower)?.let { return it }
        buildRequest(lower)?.let { return it }
        outEvent(text, lower, today)?.let { return it }
        deadlineTask(text, lower, today)?.let { return it }
        replanRequest(lower)?.let { return it }

        return Parsed(
            commands = emptyList(),
            reply = "I could not turn that into a change on my own. Connect an assistant key in " +
                "Settings for free-form requests, or add it directly from the Tasks or Plan tab.",
        )
    }

    // ---- shapes ---------------------------------------------------------------------------

    private fun breakRequest(lower: String): Parsed? {
        if (!lower.contains("break") && !lower.contains("rest")) return null
        val minutes = firstDuration(lower) ?: 15
        return Parsed(
            commands = listOf(AiCommandDto(action = AiActions.TAKE_BREAK, minutes = minutes)),
            reply = "Taking " + MarginTime.formatDuration(minutes) + ". The rest of the day moves back.",
        )
    }

    private fun energyRequest(lower: String): Parsed? {
        val tired = listOf("tired", "exhausted", "drained", "keep it light", "light day", "low energy")
            .any { lower.contains(it) }
        val sharp = listOf("focused day", "heavy day", "push hard", "lots of energy")
            .any { lower.contains(it) }
        return when {
            tired -> Parsed(
                listOf(AiCommandDto(action = AiActions.SET_ENERGY, mode = "light", date = "today")),
                "Keeping today light. Less work, longer breaks, downtime protected.",
            )
            sharp -> Parsed(
                listOf(AiCommandDto(action = AiActions.SET_ENERGY, mode = "focused", date = "today")),
                "Making room for a heavier day.",
            )
            else -> null
        }
    }

    private fun leisureRequest(lower: String): Parsed? {
        val wantsLeisure = listOf("game", "gaming", "play", "relax", "chill", "free time", "leisure")
            .any { lower.contains(it) }
        if (!wantsLeisure) return null
        val minutes = firstDuration(lower) ?: return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.SET_LEISURE, minutes = minutes)),
            "Protecting " + MarginTime.formatDuration(minutes) + " of downtime a day.",
        )
    }

    private fun buildRequest(lower: String): Parsed? {
        val wantsBuild = listOf("build", "project", "coding", "code", "side project")
            .any { lower.contains(it) }
        if (!wantsBuild) return null
        val minutes = firstDuration(lower) ?: return null
        if (!lower.contains("every") && !lower.contains("each") && !lower.contains("daily")) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.SET_BUILD, minutes = minutes)),
            "Reserving " + MarginTime.formatDuration(minutes) + " for building each day.",
        )
    }

    /** "I am out from 6 to 8", "going out 18:00 to 20:00 tomorrow". */
    private fun outEvent(original: String, lower: String, today: LocalDate): Parsed? {
        val fromIndex = lower.indexOf("from ")
        val toIndex = lower.indexOf(" to ", startIndex = if (fromIndex >= 0) fromIndex else 0)
        if (fromIndex < 0 || toIndex < 0) return null

        val startRaw = lower.substring(fromIndex + 5, toIndex).trim()
        val rest = lower.substring(toIndex + 4).trim()
        val endRaw = rest.takeWhile { !it.isWhitespace() }

        var start = MarginTime.parseTime(startRaw) ?: return null
        var end = MarginTime.parseTime(endRaw) ?: return null

        // "6 to 8" almost always means the evening for a student, not dawn.
        val statesMorning = startRaw.contains("am") || endRaw.contains("am")
        if (!statesMorning && start < 12 * 60 && end < 12 * 60 && start < end) {
            start += 12 * 60
            end += 12 * 60
        }
        if (end <= start) return null

        val date = when {
            lower.contains("tomorrow") -> "tomorrow"
            else -> "today"
        }
        val title = when {
            lower.contains("friend") -> "Friend visiting"
            lower.contains("class") -> "Extra class"
            lower.contains("out") -> "Out"
            else -> "Busy"
        }
        return Parsed(
            commands = listOf(
                AiCommandDto(
                    action = AiActions.CREATE_EVENT,
                    title = title,
                    date = date,
                    start = MarginTime.formatTime(start, true),
                    end = MarginTime.formatTime(end, true),
                    category = "personal",
                ),
            ),
            reply = "Blocked out " + MarginTime.formatTime(start, true) + " to " +
                MarginTime.formatTime(end, true) + " " + date + ". The plan works around it.",
        )
    }

    /** "finish the DS assignment by friday", "study maths for 90 minutes tomorrow". */
    private fun deadlineTask(original: String, lower: String, today: LocalDate): Parsed? {
        val verbs = listOf("finish", "complete", "do ", "write", "study", "revise", "read", "need to")
        if (verbs.none { lower.contains(it) }) return null

        val byIndex = lower.indexOf(" by ")
        val deadlineWord = if (byIndex >= 0) {
            lower.substring(byIndex + 4).trim().takeWhile { it.isLetterOrDigit() || it == '-' }
        } else {
            listOf("tomorrow", "today").firstOrNull { lower.contains(it) }
        }
        val deadline = CommandValidator.parseDate(deadlineWord, today)
        val minutes = firstDuration(lower)
        if (deadline == null && minutes == null) return null

        val title = original
            .trim()
            .removePrefix("I need to ")
            .removePrefix("i need to ")
            .let { if (byIndex >= 0) it.substring(0, minOf(it.length, byIndex)) else it }
            .trim()
            .trimEnd('.', ',')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "New task" }

        return Parsed(
            commands = listOf(
                AiCommandDto(
                    action = AiActions.CREATE_TASK,
                    title = title,
                    minutes = minutes ?: 60,
                    deadline = deadline?.toString(),
                    category = if (
                        lower.contains("build") || lower.contains("project") || lower.contains("code")
                    ) {
                        "build"
                    } else {
                        "academics"
                    },
                ),
            ),
            reply = buildString {
                append("Added ")
                append(title)
                append(" (")
                append(MarginTime.formatDuration(minutes ?: 60))
                append(")")
                deadline?.let {
                    append(", due ")
                    append(MarginTime.dayLabel(it, today))
                }
                append(". I split it across the days you have.")
            },
        )
    }

    private fun replanRequest(lower: String): Parsed? {
        val asks = listOf("replan", "rebuild", "redo my day", "plan my day", "what should i do")
        if (asks.none { lower.contains(it) }) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.REPLAN, date = "today")),
            "Rebuilt the rest of the day.",
        )
    }

    // ---- helpers --------------------------------------------------------------------------

    /** Finds the first duration in the text: "90 minutes", "2 hours", "1h30", "an hour". */
    internal fun firstDuration(lower: String): Int? {
        // Order matters: "half an hour" contains "an hour".
        if (lower.contains("half an hour")) return 30
        if (lower.contains("an hour") || lower.contains("a hour")) return 60

        var index = 0
        while (index < lower.length) {
            if (!lower[index].isDigit()) {
                index++
                continue
            }
            var end = index
            while (end < lower.length && lower[end].isDigit()) end++
            val value = lower.substring(index, end).toIntOrNull()
            if (value == null || value <= 0) {
                index = end
                continue
            }
            val tail = lower.substring(end).trimStart()
            val minutes = when {
                tail.startsWith("min") -> value
                tail.startsWith("m") && !tail.startsWith("month") -> value
                tail.startsWith("hour") || tail.startsWith("hr") -> value * 60
                tail.startsWith("h") -> value * 60
                else -> null
            }
            if (minutes != null && minutes in 5..720) return minutes
            index = end
        }
        return null
    }
}
