package com.margin.app.ai

import com.margin.app.core.MarginTime
import java.time.LocalDate

/**
 * The offline half of the natural-language input.
 *
 * When no key is configured, or the network is gone, the common phrasings still work. This is
 * not a language model and does not pretend to be: it recognises a handful of shapes, and when
 * it recognises nothing it says so plainly instead of guessing.
 *
 * Subjects are passed through as the words the user used; the validator resolves them against
 * the real subject list.
 */
object LocalCommandParser {

    data class Parsed(val commands: List<AiCommandDto>, val reply: String)

    fun parse(input: String, today: LocalDate): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed(emptyList(), "Nothing to do with an empty message.")
        val lower = text.lowercase().replace("’", "'")

        whatNow(lower)?.let { return it }
        why(lower)?.let { return it }
        missed(lower)?.let { return it }
        minimumDay(lower)?.let { return it }
        lighten(lower)?.let { return it }
        optionalToday(lower)?.let { return it }
        excludeSubject(text, lower)?.let { return it }
        breakRequest(lower)?.let { return it }
        extendCurrent(text, lower)?.let { return it }
        energyRequest(lower)?.let { return it }
        exam(text, lower, today)?.let { return it }
        outEvent(lower)?.let { return it }
        goOut(lower)?.let { return it }
        leisureRequest(lower)?.let { return it }
        buildRequest(lower)?.let { return it }
        deadlineTask(text, lower, today)?.let { return it }
        replanRequest(lower)?.let { return it }

        return Parsed(
            commands = emptyList(),
            reply = "I could not turn that into a change on my own. Connect an assistant key in " +
                "Settings for free-form requests, or add it from the Tasks or Plan tab.",
        )
    }

    // ---- questions ---------------------------------------------------------------------------

    private fun whatNow(lower: String): Parsed? {
        val asks = listOf("what should i do", "what now", "what's next", "whats next", "what do i do")
        if (asks.none { lower.contains(it) }) return null
        return Parsed(listOf(AiCommandDto(action = AiActions.WHAT_NOW)), "")
    }

    private fun why(lower: String): Parsed? {
        if (!lower.startsWith("why")) return null
        return Parsed(listOf(AiCommandDto(action = AiActions.EXPLAIN)), "")
    }

    // ---- the day -----------------------------------------------------------------------------

    private fun missed(lower: String): Parsed? {
        if (!lower.contains("missed my") && !lower.startsWith("i missed") && !lower.contains("missed the session")) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.MISSED_SESSION)),
            "Recorded. The work goes back into the day where it fits, or tomorrow.",
        )
    }

    private fun minimumDay(lower: String): Parsed? {
        if (!lower.contains("minimum day") && !lower.contains("bare minimum")) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.MINIMUM_DAY)),
            "Minimum day: only the most urgent academic work, with breaks and some downtime kept.",
        )
    }

    private fun lighten(lower: String): Parsed? {
        val asks = listOf("lighten", "lighter", "make today light", "make it light", "go easy")
        if (asks.none { lower.contains(it) }) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.LIGHTEN_DAY)),
            "Lightened today. Only what can't wait is planned. Open Lighten Today to choose what matters.",
        )
    }

    private fun optionalToday(lower: String): Parsed? {
        val refusing = listOf("don't want to", "dont want to", "no ", "skip ", "not ", "without ")
            .any { lower.contains(it) }
        val learning = lower.contains("learn") || lower.contains("new skill")
        val build = lower.contains("build") || lower.contains("project") || lower.contains("coding")
        val minutes = firstDuration(lower)
        return when {
            refusing && learning && (lower.contains("today") || lower.startsWith("i don")) -> Parsed(
                listOf(AiCommandDto(action = AiActions.NO_LEARNING_TODAY)),
                "No new-skill learning today. That time stays free.",
            )
            refusing && build && (lower.contains("today") || lower.startsWith("i don")) -> Parsed(
                listOf(AiCommandDto(action = AiActions.NO_BUILD_TODAY)),
                "No building today. That time stays free.",
            )
            !refusing && learning && minutes != null && !lower.contains("every") -> Parsed(
                listOf(AiCommandDto(action = AiActions.LEARN_TODAY, minutes = minutes)),
                "Making " + MarginTime.formatDuration(minutes) + " for learning today.",
            )
            !refusing && build && minutes != null && !lower.contains("every") && !lower.contains("daily") -> Parsed(
                listOf(AiCommandDto(action = AiActions.BUILD_TODAY, minutes = minutes)),
                "Making " + MarginTime.formatDuration(minutes) + " for your project today.",
            )
            else -> null
        }
    }

    /** "I don't want to study Mathematics today", "skip DS today". */
    private fun excludeSubject(original: String, lower: String): Parsed? {
        val markers = listOf("don't want to study ", "dont want to study ", "skip ", "no ", "not studying ")
        val marker = markers.firstOrNull { lower.contains(it) } ?: return null
        if (!lower.contains("today")) return null
        val start = lower.indexOf(marker) + marker.length
        val end = lower.indexOf("today", start).takeIf { it > start } ?: return null
        val subject = original.substring(start, end).trim().trimEnd(',', '.').ifBlank { return null }
        if (subject.contains("break") || subject.contains("build") || subject.contains("learn")) return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.SKIP_SUBJECT_TODAY, subject = subject)),
            "$subject is off today. It moves to tomorrow so it isn't forgotten.",
        )
    }

    /** "I want to study Digital Electronics for another 30 minutes", "10 more minutes". */
    private fun extendCurrent(original: String, lower: String): Parsed? {
        val extending = lower.contains("another ") || lower.contains(" more min") || lower.contains("more minutes") ||
            lower.contains("keep going")
        if (!extending) return null
        val minutes = firstDuration(lower) ?: if (lower.contains("keep going")) 15 else return null
        return Parsed(
            listOf(AiCommandDto(action = AiActions.EXTEND_CURRENT, minutes = minutes, title = original)),
            "Added " + MarginTime.formatDuration(minutes) + ". The rest of the day moves back.",
        )
    }

    private fun breakRequest(lower: String): Parsed? {
        // "The rest of the day" is not a request to rest.
        val mentions = Regex("\\bbreak\\b").containsMatchIn(lower) ||
            (Regex("\\brest\\b").containsMatchIn(lower) && !lower.contains("rest of"))
        if (!mentions) return null
        val minutes = firstDuration(lower) ?: 15
        return Parsed(
            commands = listOf(AiCommandDto(action = AiActions.TAKE_BREAK, minutes = minutes)),
            reply = "Taking " + MarginTime.formatDuration(minutes) + ". The rest of the day moves back.",
        )
    }

    private fun energyRequest(lower: String): Parsed? {
        val tired = listOf("tired", "exhausted", "drained", "low energy", "sleepy", "worn out")
            .any { lower.contains(it) }
        val sharp = listOf("focused day", "heavy day", "push hard", "lots of energy", "high energy")
            .any { lower.contains(it) }
        return when {
            tired -> Parsed(
                listOf(AiCommandDto(action = AiActions.SET_ENERGY, mode = "light", date = "today")),
                "Low energy today: shorter sessions, lighter work, more breaks.",
            )
            sharp -> Parsed(
                listOf(AiCommandDto(action = AiActions.SET_ENERGY, mode = "focused", date = "today")),
                "Making room for a heavier day.",
            )
            else -> null
        }
    }

    /** "DS exam on Nov 12", "maths exam on 2026-11-17 at 10". */
    private fun exam(original: String, lower: String, today: LocalDate): Parsed? {
        val index = lower.indexOf(" exam")
        if (index < 0 && !lower.startsWith("exam")) return null
        val onIndex = lower.indexOf(" on ", startIndex = maxOf(index, 0))
        if (onIndex < 0) return null
        val subject = original.substring(0, maxOf(index, 0)).trim()
            .removePrefix("my ").removePrefix("My ").removePrefix("the ").removePrefix("The ")
            .ifBlank { return null }
        val rest = lower.substring(onIndex + 4).trim()
        val atIndex = rest.indexOf(" at ")
        val dateText = if (atIndex >= 0) rest.substring(0, atIndex) else rest
        val date = CommandValidator.parseDate(dateText.trim(), today) ?: return null
        val start = if (atIndex >= 0) {
            MarginTime.parseTime(rest.substring(atIndex + 4).trim().takeWhile { !it.isWhitespace() })
        } else {
            null
        }
        return Parsed(
            listOf(
                AiCommandDto(
                    action = AiActions.ADD_EXAM,
                    subject = subject,
                    date = date.toString(),
                    start = start?.let { MarginTime.formatTime(it, true) },
                    type = if (lower.contains("lab")) "lab" else "theory",
                ),
            ),
            "Added the exam. Exam mode will shift the balance as it gets closer.",
        )
    }

    /** "I have to go out from 6 to 8", "busy 18:00 to 20:00 tomorrow". */
    private fun outEvent(lower: String): Parsed? {
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
        if (!statesMorning && start < 12 * 60 && end < 12 * 60 && start < end && start < 9 * 60) {
            start += 12 * 60
            end += 12 * 60
        }
        if (end <= start) return null

        val date = if (lower.contains("tomorrow")) "tomorrow" else "today"
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

    /** "I'm going out tonight", "I'm out until 9". */
    private fun goOut(lower: String): Parsed? {
        val out = listOf("going out", "i'm out", "im out", "i am out", "heading out", "stepping out")
            .any { lower.contains(it) }
        if (!out) return null
        val until = lower.substringAfter("until ", "").trim().takeWhile { !it.isWhitespace() }
            .ifBlank { lower.substringAfter("till ", "").trim().takeWhile { !it.isWhitespace() } }
            .ifBlank { null }
        return Parsed(
            listOf(AiCommandDto(action = AiActions.GO_OUT, until = until)),
            if (until == null) "Blocked out the rest of the day. Anything that doesn't fit moves to tomorrow."
            else "Blocked out until $until. The rest of the day works around it.",
        )
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

    /** "finish the DS assignment by friday", "study maths for 90 minutes tomorrow". */
    private fun deadlineTask(original: String, lower: String, today: LocalDate): Parsed? {
        val verbs = listOf("finish", "complete", "do ", "write", "study", "revise", "read", "need to", "submit")
        if (verbs.none { lower.contains(it) }) return null

        val byIndex = lower.indexOf(" by ")
        val deadlineWord = if (byIndex >= 0) {
            lower.substring(byIndex + 4).trim().trimEnd('.', '!', '?')
        } else {
            listOf("tomorrow", "today").firstOrNull { lower.contains(it) }
        }
        val deadline = CommandValidator.parseDate(deadlineWord, today)
            ?: deadlineWord?.split(' ')?.firstOrNull()?.let { CommandValidator.parseDate(it, today) }
        val minutes = firstDuration(lower)
        if (deadline == null && minutes == null) return null

        val title = original
            .trim()
            .removePrefix("I need to ")
            .removePrefix("i need to ")
            .removePrefix("I have to ")
            .removePrefix("i have to ")
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
                    subject = title,
                    category = if (lower.contains("build") || lower.contains("project") || lower.contains("code")) {
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
                append(". It's spread across the days you have.")
            },
        )
    }

    private fun replanRequest(lower: String): Parsed? {
        val asks = listOf("replan", "rebuild", "redo my day", "plan my day")
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
        if (lower.contains("an hour") || lower.contains("a hour") || lower.contains("one hour")) return 60

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
                tail.startsWith("m") && !tail.startsWith("month") && !tail.startsWith("more") -> value
                tail.startsWith("more min") -> value
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
