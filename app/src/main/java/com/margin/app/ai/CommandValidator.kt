package com.margin.app.ai

import com.margin.app.core.MarginTime
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
import com.margin.app.domain.planner.EnergyMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The gate between the model and the database.
 *
 * Every field is bounds-checked, every referenced id is looked up, and anything that fails
 * becomes a [CommandRejection] with a readable reason rather than a silent no-op or a crash.
 * A model that hallucinates a task id or a 47-hour event cannot do damage here.
 */
class CommandValidator(
    private val taskExists: suspend (Long) -> Boolean,
    private val blockExists: suspend (Long) -> Boolean,
) {

    suspend fun validate(commands: List<AiCommandDto>, today: LocalDate): ValidationResult {
        val accepted = mutableListOf<ValidatedCommand>()
        val rejected = mutableListOf<CommandRejection>()

        for (dto in commands.take(MAX_COMMANDS)) {
            val action = dto.action.trim().lowercase()
            if (action !in AiActions.all) {
                rejected += CommandRejection(action, "Unknown action.")
                continue
            }
            when (val outcome = validateOne(dto, action, today)) {
                is Outcome.Ok -> accepted += outcome.command
                is Outcome.Rejected -> rejected += CommandRejection(action, outcome.reason)
                Outcome.Ignored -> Unit
            }
        }

        return ValidationResult(accepted, rejected)
    }

    private sealed interface Outcome {
        data class Ok(val command: ValidatedCommand) : Outcome
        data class Rejected(val reason: String) : Outcome
        data object Ignored : Outcome
    }

    private suspend fun validateOne(dto: AiCommandDto, action: String, today: LocalDate): Outcome =
        when (action) {
            AiActions.NONE -> Outcome.Ignored

            AiActions.CREATE_TASK -> {
                val title = dto.title?.trim()
                val minutes = dto.minutes ?: DEFAULT_TASK_MINUTES
                when {
                    title.isNullOrBlank() -> Outcome.Rejected("A task needs a title.")
                    minutes !in MIN_MINUTES..MAX_TASK_MINUTES ->
                        Outcome.Rejected("A task of $minutes minutes is not realistic.")
                    else -> {
                        val deadline = parseDate(dto.deadline ?: dto.date, today)
                        if (deadline != null && !withinHorizon(deadline, today)) {
                            Outcome.Rejected("That deadline is outside the next year.")
                        } else {
                            val splittable = dto.splittable ?: (minutes > 60)
                            Outcome.Ok(
                                ValidatedCommand.CreateTask(
                                    task = Task(
                                        title = title,
                                        notes = dto.notes?.trim()?.ifBlank { null },
                                        category = Category.fromKey(dto.category),
                                        subjectCode = dto.subject?.trim()?.ifBlank { null },
                                        estimatedMinutes = minutes,
                                        minSessionMinutes = if (splittable) {
                                            minOf(25, minutes)
                                        } else {
                                            minutes
                                        },
                                        maxSessionMinutes = if (splittable) {
                                            minOf(60, minutes).coerceAtLeast(15)
                                        } else {
                                            minutes
                                        },
                                        priority = Priority.fromKey(dto.priority),
                                        difficulty = Difficulty.fromKey(dto.difficulty),
                                        energy = EnergyLevel.fromKey(dto.energy),
                                        deadlineDate = deadline,
                                        deadlineMinute = dto.end?.let { MarginTime.parseTime(it) },
                                        splittable = splittable,
                                        createdAt = System.currentTimeMillis(),
                                    ),
                                    summary = buildString {
                                        append("Added ")
                                        append(title)
                                        append(", ")
                                        append(MarginTime.formatDuration(minutes))
                                        deadline?.let {
                                            append(", due ")
                                            append(MarginTime.dayLabel(it, today))
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            AiActions.CREATE_EVENT -> {
                val title = dto.title?.trim()
                val start = dto.start?.let { MarginTime.parseTime(it) }
                val end = dto.end?.let { MarginTime.parseTime(it) }
                val date = parseDate(dto.date, today) ?: today
                when {
                    title.isNullOrBlank() -> Outcome.Rejected("An event needs a title.")
                    start == null -> Outcome.Rejected("An event needs a start time.")
                    end == null -> Outcome.Rejected("An event needs an end time.")
                    end <= start -> Outcome.Rejected("The event ends before it starts.")
                    end - start > MAX_EVENT_MINUTES ->
                        Outcome.Rejected("An event longer than 14 hours is probably a mistake.")
                    !withinHorizon(date, today) -> Outcome.Rejected("That date is too far away.")
                    else -> Outcome.Ok(
                        ValidatedCommand.CreateEvent(
                            event = CalendarEvent(
                                title = title,
                                notes = dto.notes?.trim()?.ifBlank { null },
                                date = date,
                                start = start,
                                end = end,
                                category = Category.fromKey(dto.category ?: Category.PERSONAL.key),
                                createdBySuggestion = true,
                            ),
                            summary = "Blocked out " + title + " on " +
                                MarginTime.dayLabel(date, today) + ", " +
                                MarginTime.formatTime(start, true) + " to " +
                                MarginTime.formatTime(end, true),
                        ),
                    )
                }
            }

            AiActions.UPDATE_TASK -> {
                val id = dto.taskId
                when {
                    id == null -> Outcome.Rejected("No task was identified.")
                    !taskExists(id) -> Outcome.Rejected("That task no longer exists.")
                    dto.minutes != null && dto.minutes !in MIN_MINUTES..MAX_TASK_MINUTES ->
                        Outcome.Rejected("That duration is not realistic.")
                    else -> {
                        val deadline = parseDate(dto.deadline, today)
                        if (deadline != null && !withinHorizon(deadline, today)) {
                            Outcome.Rejected("That deadline is outside the next year.")
                        } else {
                            Outcome.Ok(
                                ValidatedCommand.UpdateTask(
                                    taskId = id,
                                    title = dto.title?.trim()?.ifBlank { null },
                                    minutes = dto.minutes,
                                    deadline = deadline,
                                    priority = dto.priority?.let { Priority.fromKey(it) },
                                    difficulty = dto.difficulty?.let { Difficulty.fromKey(it) },
                                    energy = dto.energy?.let { EnergyLevel.fromKey(it) },
                                    category = dto.category?.let { Category.fromKey(it) },
                                    notes = dto.notes?.trim()?.ifBlank { null },
                                    splittable = dto.splittable,
                                    summary = "Updated the task",
                                ),
                            )
                        }
                    }
                }
            }

            AiActions.COMPLETE_TASK -> {
                val id = dto.taskId
                when {
                    id == null -> Outcome.Rejected("No task was identified.")
                    !taskExists(id) -> Outcome.Rejected("That task no longer exists.")
                    else -> Outcome.Ok(ValidatedCommand.CompleteTask(id, "Marked it done"))
                }
            }

            AiActions.DELETE_TASK -> {
                val id = dto.taskId
                when {
                    id == null -> Outcome.Rejected("No task was identified.")
                    !taskExists(id) -> Outcome.Rejected("That task no longer exists.")
                    else -> Outcome.Ok(ValidatedCommand.DeleteTask(id, "Removed the task"))
                }
            }

            AiActions.MOVE_BLOCK -> {
                val id = dto.blockId
                val start = dto.start?.let { MarginTime.parseTime(it) }
                val date = parseDate(dto.date, today) ?: today
                when {
                    id == null -> Outcome.Rejected("No scheduled item was identified.")
                    !blockExists(id) -> Outcome.Rejected("That item is no longer on the schedule.")
                    start == null -> Outcome.Rejected("No new time was given.")
                    !withinHorizon(date, today) -> Outcome.Rejected("That date is too far away.")
                    else -> Outcome.Ok(
                        ValidatedCommand.MoveBlock(
                            blockId = id,
                            date = date,
                            startMinute = start,
                            summary = "Moved it to " + MarginTime.formatTime(start, true),
                        ),
                    )
                }
            }

            AiActions.SKIP_BLOCK -> {
                val id = dto.blockId
                when {
                    id == null -> Outcome.Rejected("No scheduled item was identified.")
                    !blockExists(id) -> Outcome.Rejected("That item is no longer on the schedule.")
                    else -> Outcome.Ok(
                        ValidatedCommand.SkipBlock(
                            blockId = id,
                            resolution = parseResolution(dto.resolution),
                            reason = dto.reason?.trim()?.ifBlank { null },
                            summary = "Skipped it",
                        ),
                    )
                }
            }

            AiActions.TAKE_BREAK -> {
                val minutes = dto.minutes ?: DEFAULT_BREAK_MINUTES
                if (minutes !in MIN_MINUTES..MAX_BREAK_MINUTES) {
                    Outcome.Rejected("A break of $minutes minutes is out of range.")
                } else {
                    Outcome.Ok(
                        ValidatedCommand.TakeBreak(
                            minutes = minutes,
                            summary = "Taking " + MarginTime.formatDuration(minutes) + " off",
                        ),
                    )
                }
            }

            AiActions.SET_ENERGY -> {
                val mode = EnergyMode.fromKey(dto.mode)
                val date = parseDate(dto.date, today) ?: today
                Outcome.Ok(
                    ValidatedCommand.SetEnergy(
                        mode = mode,
                        date = date,
                        summary = when (mode) {
                            EnergyMode.LIGHT -> "Keeping the day light"
                            EnergyMode.FOCUSED -> "Making room for a heavier day"
                            EnergyMode.NORMAL -> "Back to a normal day"
                        },
                    ),
                )
            }

            AiActions.SET_LEISURE -> {
                val minutes = dto.minutes
                when {
                    minutes == null -> Outcome.Rejected("No amount of leisure time was given.")
                    minutes !in 0..MAX_LEISURE_MINUTES ->
                        Outcome.Rejected("That much protected leisure is out of range.")
                    else -> Outcome.Ok(
                        ValidatedCommand.SetLeisureFloor(
                            minutes = minutes,
                            summary = "Protecting " + MarginTime.formatDuration(minutes) +
                                " of downtime a day",
                        ),
                    )
                }
            }

            AiActions.SET_BUILD -> {
                val minutes = dto.minutes
                when {
                    minutes == null -> Outcome.Rejected("No amount of build time was given.")
                    minutes !in 0..MAX_BUILD_MINUTES ->
                        Outcome.Rejected("That much build time is out of range.")
                    else -> Outcome.Ok(
                        ValidatedCommand.SetBuildTarget(
                            minutes = minutes,
                            weekend = dto.date?.contains("weekend", ignoreCase = true) == true,
                            summary = "Reserving " + MarginTime.formatDuration(minutes) +
                                " for building",
                        ),
                    )
                }
            }

            AiActions.REPLAN -> Outcome.Ok(
                ValidatedCommand.Replan(
                    date = parseDate(dto.date, today) ?: today,
                    summary = "Rebuilt the day",
                ),
            )

            else -> Outcome.Rejected("Unsupported action.")
        }

    private fun parseResolution(raw: String?): SkipResolution = when (raw?.trim()?.lowercase()) {
        "later", "later_today", "today" -> SkipResolution.LATER_TODAY
        "tomorrow" -> SkipResolution.TOMORROW
        "drop", "drop_today" -> SkipResolution.DROP_TODAY
        "remove", "delete" -> SkipResolution.REMOVED
        else -> SkipResolution.LATER_TODAY
    }

    private fun withinHorizon(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today.minusDays(PAST_HORIZON_DAYS)) &&
            !date.isAfter(today.plusDays(FUTURE_HORIZON_DAYS))

    companion object {
        private const val MAX_COMMANDS = 8
        private const val MIN_MINUTES = 5
        private const val MAX_TASK_MINUTES = 12 * 60
        private const val MAX_EVENT_MINUTES = 14 * 60
        private const val MAX_BREAK_MINUTES = 6 * 60
        private const val MAX_LEISURE_MINUTES = 10 * 60
        private const val MAX_BUILD_MINUTES = 10 * 60
        private const val DEFAULT_TASK_MINUTES = 45
        private const val DEFAULT_BREAK_MINUTES = 15
        private const val PAST_HORIZON_DAYS = 30L
        private const val FUTURE_HORIZON_DAYS = 365L

        /** Accepts an ISO date, or the words the model is told to prefer. */
        fun parseDate(raw: String?, today: LocalDate): LocalDate? {
            val text = raw?.trim()?.lowercase() ?: return null
            if (text.isEmpty()) return null
            return when (text) {
                "today" -> today
                "tomorrow" -> today.plusDays(1)
                "yesterday" -> today.minusDays(1)
                else -> {
                    weekdayOffset(text, today) ?: try {
                        LocalDate.parse(text)
                    } catch (e: DateTimeParseException) {
                        null
                    }
                }
            }
        }

        private fun weekdayOffset(text: String, today: LocalDate): LocalDate? {
            val names = listOf(
                "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            )
            val index = names.indexOfFirst { text == it || text == "next $it" }
            if (index < 0) return null
            val target = index + 1
            var days = target - today.dayOfWeek.value
            if (days <= 0 || text.startsWith("next ")) days += 7
            return today.plusDays(days.toLong())
        }
    }
}
