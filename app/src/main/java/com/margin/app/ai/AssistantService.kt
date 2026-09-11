package com.margin.app.ai

import com.margin.app.ai.context.ContextBuilder
import com.margin.app.ai.context.Prompts
import com.margin.app.core.MarginTime
import com.margin.app.data.db.dao.AiInteractionDao
import com.margin.app.data.db.entity.AiInteractionEntity
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.ScheduleActions
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

enum class AssistantSource { MODEL, LOCAL, NONE }

data class AssistantResult(
    val reply: String,
    val applied: List<String> = emptyList(),
    val rejected: List<String> = emptyList(),
    val source: AssistantSource = AssistantSource.NONE,
    val error: String? = null,
)

/**
 * The one path from a typed sentence to a changed plan: understand, validate, apply, replan.
 * Each step can fail independently and none of them can put unvalidated model output into the
 * database.
 *
 * Cost stays low by design: sentences the offline parser understands never reach the model,
 * and those that do carry only the context sections they need. Questions about the plan
 * ("what now", "why") are answered from the plan itself.
 */
class AssistantService(
    private val aiSettingsRepository: AiSettingsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val timetableRepository: TimetableRepository,
    private val examRepository: ExamRepository,
    private val planningService: PlanningService,
    private val scheduleActions: ScheduleActions,
    private val contextBuilder: ContextBuilder,
    private val interactionDao: AiInteractionDao,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val validator = CommandValidator(
        taskExists = { id -> taskRepository.task(id) != null },
        blockExists = { id -> scheduleRepository.block(id) != null },
        subjects = { timetableRepository.subjects() },
    )

    suspend fun submit(input: String, now: LocalDateTime = LocalDateTime.now()): AssistantResult {
        val text = input.trim()
        if (text.isEmpty()) {
            return AssistantResult(reply = "Type what changed and I will work it in.")
        }

        val today = now.toLocalDate()
        val settings = aiSettingsRepository.current()
        val provider = AiProviderFactory.create(settings)
        val local = LocalCommandParser.parse(text, today)

        val understanding: Understanding = when {
            // Clear, single-intent sentences are handled on the device and never sent anywhere.
            provider == null || (local.commands.isNotEmpty() && isSimple(text)) ->
                Understanding(local.commands, local.reply, AssistantSource.LOCAL, null)
            else -> {
                val prefs = preferencesRepository.current()
                val context = contextBuilder.build(now, prefs, settings.shareScheduleDetail, text)
                when (val outcome = provider.complete(Prompts.SYSTEM, context + "\n\nUser: " + text)) {
                    is AiOutcome.Success -> parseModelOutput(outcome.text, local)
                    is AiOutcome.Failure -> Understanding(
                        commands = local.commands,
                        reply = if (local.commands.isEmpty()) "AI unavailable. " + outcome.message + " " + local.reply else local.reply,
                        source = AssistantSource.LOCAL,
                        error = outcome.message,
                    )
                }
            }
        }

        val validation = validator.validate(understanding.commands, today)
        val applied = mutableListOf<String>()
        val answers = mutableListOf<String>()
        val datesToReplan = linkedSetOf<LocalDate>()

        for (command in validation.commands) {
            runCatching { apply(command, today, now, datesToReplan) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is Applied.Done -> applied += outcome.summary ?: command.summary
                        is Applied.Answer -> answers += outcome.text
                        is Applied.Refused -> answers += outcome.text
                    }
                }
                .onFailure { applied += "Could not apply: " + command.summary }
        }

        for (date in datesToReplan) {
            runCatching { planningService.replan(date, now) }
        }

        val reply = when {
            answers.isNotEmpty() -> answers.joinToString(" ")
            understanding.reply.isNotBlank() -> understanding.reply
            applied.isEmpty() -> "Nothing needed changing."
            else -> "Done."
        }
        val result = AssistantResult(
            reply = reply,
            applied = applied,
            rejected = validation.rejections.map { it.reason },
            source = understanding.source,
            error = understanding.error,
        )

        runCatching {
            interactionDao.insert(
                AiInteractionEntity(
                    at = System.currentTimeMillis(),
                    userText = text,
                    commandJson = understanding.commands
                        .takeIf { it.isNotEmpty() }
                        ?.let { json.encodeToString(ListSerializer(AiCommandDto.serializer()), it) },
                    reply = result.reply,
                    applied = applied.isNotEmpty(),
                    source = understanding.source.name.lowercase(),
                    error = result.error,
                ),
            )
        }

        return result
    }

    private data class Understanding(
        val commands: List<AiCommandDto>,
        val reply: String,
        val source: AssistantSource,
        val error: String?,
    )

    private sealed interface Applied {
        data class Done(val summary: String? = null) : Applied
        data class Answer(val text: String) : Applied
        data class Refused(val text: String) : Applied
    }

    /** One clear request, not several joined together; safe to handle without the model. */
    private fun isSimple(text: String): Boolean {
        val lower = " " + text.lowercase() + " "
        val joined = listOf(" and ", " but ", " also ", " then ", ";", " plus ").any { lower.contains(it) }
        return !joined && text.length <= SIMPLE_LENGTH
    }

    /**
     * Models sometimes wrap JSON in prose or fences. Recover what we can; if the payload is
     * unusable, fall back to the local parser rather than showing the user a parse error.
     */
    private fun parseModelOutput(raw: String, local: LocalCommandParser.Parsed): Understanding {
        val payload = JsonText.firstObject(raw)
            ?: return Understanding(local.commands, local.reply, AssistantSource.LOCAL, "The assistant replied in an unexpected format.")
        return try {
            val dto = json.decodeFromString<AiResponseDto>(payload)
            Understanding(dto.commands, dto.reply.trim(), AssistantSource.MODEL, null)
        } catch (e: Exception) {
            Understanding(local.commands, local.reply, AssistantSource.LOCAL, "The assistant reply could not be read.")
        }
    }

    private suspend fun apply(
        command: ValidatedCommand,
        today: LocalDate,
        now: LocalDateTime,
        replanDates: MutableSet<LocalDate>,
    ): Applied {
        when (command) {
            is ValidatedCommand.CreateTask -> {
                taskRepository.create(command.task)
                replanDates += today
            }

            is ValidatedCommand.CreateEvent -> {
                taskRepository.createEvent(command.event)
                replanDates += command.event.date
            }

            is ValidatedCommand.UpdateTask -> {
                val task = taskRepository.task(command.taskId) ?: return Applied.Refused("That task no longer exists.")
                taskRepository.update(
                    task.copy(
                        title = command.title ?: task.title,
                        estimatedMinutes = command.minutes ?: task.estimatedMinutes,
                        deadlineDate = command.deadline ?: task.deadlineDate,
                        priority = command.priority ?: task.priority,
                        difficulty = command.difficulty ?: task.difficulty,
                        energy = command.energy ?: task.energy,
                        category = command.category ?: task.category,
                        notes = command.notes ?: task.notes,
                        splittable = command.splittable ?: task.splittable,
                    ),
                )
                replanDates += today
            }

            is ValidatedCommand.CompleteTask -> {
                taskRepository.markDone(command.taskId)
                replanDates += today
            }

            is ValidatedCommand.DeleteTask -> {
                taskRepository.delete(command.taskId)
                replanDates += today
            }

            is ValidatedCommand.MoveBlock -> scheduleActions.reschedule(
                blockId = command.blockId,
                toDate = command.date,
                toStart = command.startMinute,
                reason = "You asked for it to move.",
                now = now,
            )

            is ValidatedCommand.SkipBlock -> scheduleActions.skip(command.blockId, command.resolution, command.reason, now)

            is ValidatedCommand.TakeBreak -> scheduleActions.takeBreak(command.minutes, now)

            is ValidatedCommand.SetEnergy -> {
                preferencesRepository.update {
                    it.copy(energyMode = command.mode, energyModeDate = command.date.toEpochDay())
                }
                replanDates += command.date
            }

            is ValidatedCommand.SetLeisureFloor -> {
                preferencesRepository.update { it.copy(minLeisureMinutes = command.minutes) }
                replanDates += today
            }

            is ValidatedCommand.SetBuildTarget -> {
                preferencesRepository.update {
                    if (command.weekend) it.copy(buildMinutesWeekend = command.minutes)
                    else it.copy(buildMinutesWeekday = command.minutes)
                }
                replanDates += today
            }

            is ValidatedCommand.Replan -> replanDates += command.date

            is ValidatedCommand.GoOut -> {
                val result = scheduleActions.goOut(command.backMinute, now)
                return Applied.Answer(result.message)
            }

            is ValidatedCommand.LightenDay -> scheduleActions.lightenToday(
                date = today,
                essentials = command.essentialSubjects.map { DayState.subjectKey(it) }.toSet(),
                priorities = command.essentialSubjects,
                dropBuild = command.dropBuild,
                dropLearning = command.dropLearning,
                lowEnergy = false,
                now = now,
            )

            is ValidatedCommand.MinimumDay -> scheduleActions.setMinimumDay(today, true, now)

            is ValidatedCommand.SetBuildToday ->
                scheduleActions.setBuildDecision(today, command.decision, minutes = command.minutes, now = now)

            is ValidatedCommand.SetLearningToday ->
                scheduleActions.setLearningDecision(today, command.decision, minutes = command.minutes, now = now)

            is ValidatedCommand.ExcludeSubject -> {
                val result = scheduleActions.excludeSubjectToday(today, command.subjectCode, now)
                val note = result.notes.firstOrNull { it.contains(timetableRepository.subject(command.subjectCode)?.name ?: command.subjectCode) }
                return Applied.Done(note ?: command.summary)
            }

            is ValidatedCommand.ExtendCurrent -> {
                val active = scheduleRepository.activeBlock()
                    ?: return Applied.Refused("Nothing is running right now. Start a session and I'll keep it going.")
                scheduleActions.continueSession(active.id, command.minutes, now)
                return Applied.Done("Added ${MarginTime.formatDuration(command.minutes)} to ${active.title}")
            }

            is ValidatedCommand.AddExam -> {
                examRepository.upsert(command.exam)
                replanDates += today
            }

            is ValidatedCommand.MissedSession -> {
                val nowMinute = MarginTime.nowMinute(now)
                val overdue = scheduleRepository.blocksFor(today)
                    .filter { it.status == BlockStatus.PLANNED && it.type.isWork && it.start <= nowMinute }
                    .maxByOrNull { it.start }
                    ?: return Applied.Refused("No session is overdue right now.")
                scheduleActions.markMissed(overdue.id, "You said you missed it.", now)
                return Applied.Done("Recorded ${overdue.title} as missed. Its work goes back into the day where it fits.")
            }

            is ValidatedCommand.WhatNow -> return Applied.Answer(whatNow(now))

            is ValidatedCommand.Explain -> return Applied.Answer(explain(command.blockId, now))
        }
        return Applied.Done()
    }

    /** "What should I do now?", answered from the plan with nothing invented. */
    suspend fun whatNow(now: LocalDateTime = LocalDateTime.now()): String {
        val prefs = preferencesRepository.current()
        val use24 = prefs.use24HourTime
        val nowMinute = MarginTime.nowMinute(now)
        val blocks = scheduleRepository.blocksFor(now.toLocalDate()).sortedBy { it.start }
        val active = blocks.firstOrNull { it.status == BlockStatus.ACTIVE }
        if (active != null) {
            val left = (active.end - nowMinute).coerceAtLeast(0)
            return "You're on ${active.title}, ${MarginTime.formatDuration(left)} left, until ${MarginTime.formatTime(active.end, use24)}."
        }
        val current = blocks.firstOrNull { it.status == BlockStatus.PLANNED && nowMinute in it.start until it.end && it.type != BlockType.SLEEP }
        val next = blocks.firstOrNull { it.status == BlockStatus.PLANNED && it.start > nowMinute && it.type != BlockType.FREE && it.type != BlockType.SLEEP }
        return when {
            current != null && current.type.isWork ->
                "Now: ${current.title}, until ${MarginTime.formatTime(current.end, use24)}. Start it when you're ready."
            current != null && (current.type == BlockType.FREE || current.type == BlockType.LEISURE) ->
                "This is free time" + (next?.let { ", until ${it.title} at ${MarginTime.formatTime(it.start, use24)}." } ?: ". Nothing else is planned.")
            current != null -> "Now: ${current.title}, until ${MarginTime.formatTime(current.end, use24)}." +
                (next?.let { " Then ${it.title}." } ?: "")
            next != null -> "Nothing right now. Next is ${next.title} at ${MarginTime.formatTime(next.start, use24)}."
            else -> "Nothing else is planned today. The rest of the time is yours."
        }
    }

    /** Explains a block using the reason the planner stored when it placed it, never an invented one. */
    suspend fun explain(blockId: Long?, now: LocalDateTime = LocalDateTime.now()): String {
        val nowMinute = MarginTime.nowMinute(now)
        val block: ScheduleBlock = (blockId?.let { scheduleRepository.block(it) })
            ?: scheduleRepository.blocksFor(now.toLocalDate())
                .filter { it.type.isWork && it.status.isOpen && it.end > nowMinute }
                .minByOrNull { it.start }
            ?: return "There's nothing planned to explain right now."
        val reason = block.reason
        return if (reason.isNullOrBlank()) {
            "${block.title} sits at ${MarginTime.formatTime(block.start, true)} because that's where the day had room for it."
        } else {
            "${block.title}: " + reason.replaceFirstChar { it.lowercase() }
        }
    }

    /** Resets a light or focused day back to normal. */
    suspend fun clearEnergyOverride() {
        preferencesRepository.update { it.copy(energyMode = EnergyMode.NORMAL, energyModeDate = 0L) }
    }

    private companion object {
        const val SIMPLE_LENGTH = 90
    }
}
