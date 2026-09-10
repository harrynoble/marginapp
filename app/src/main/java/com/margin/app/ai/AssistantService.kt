package com.margin.app.ai

import com.margin.app.ai.context.ContextBuilder
import com.margin.app.ai.context.Prompts
import com.margin.app.data.db.dao.AiInteractionDao
import com.margin.app.data.db.entity.AiInteractionEntity
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
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
 * The one path from a typed sentence to a changed plan:
 * understand, validate, apply, replan. Each step can fail independently and none of them can
 * put unvalidated model output into the database.
 */
class AssistantService(
    private val aiSettingsRepository: AiSettingsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val planningService: PlanningService,
    private val scheduleActions: ScheduleActions,
    private val contextBuilder: ContextBuilder,
    private val interactionDao: AiInteractionDao,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val validator = CommandValidator(
        taskExists = { id -> taskRepository.task(id) != null },
        blockExists = { id -> scheduleRepository.block(id) != null },
    )

    suspend fun submit(input: String, now: LocalDateTime = LocalDateTime.now()): AssistantResult {
        val text = input.trim()
        if (text.isEmpty()) {
            return AssistantResult(reply = "Type what changed and I will work it in.")
        }

        val today = now.toLocalDate()
        val settings = aiSettingsRepository.current()
        val provider = AiProviderFactory.create(settings)

        val understanding: Understanding = if (provider == null) {
            val local = LocalCommandParser.parse(text, today)
            Understanding(local.commands, local.reply, AssistantSource.LOCAL, null)
        } else {
            val prefs = preferencesRepository.current()
            val context = contextBuilder.build(now, prefs, settings.shareScheduleDetail)
            when (val outcome = provider.complete(Prompts.SYSTEM, context + "\n\nUser: " + text)) {
                is AiOutcome.Success -> parseModelOutput(outcome.text, text, today)
                is AiOutcome.Failure -> {
                    // Falling back keeps the bar useful when the network or the key is not.
                    val local = LocalCommandParser.parse(text, today)
                    Understanding(
                        commands = local.commands,
                        reply = if (local.commands.isEmpty()) {
                            outcome.message + " " + local.reply
                        } else {
                            local.reply
                        },
                        source = AssistantSource.LOCAL,
                        error = outcome.message,
                    )
                }
            }
        }

        val validation = validator.validate(understanding.commands, today)
        val applied = mutableListOf<String>()
        val datesToReplan = linkedSetOf<LocalDate>()

        for (command in validation.commands) {
            runCatching { apply(command, today, now, datesToReplan) }
                .onSuccess { applied += command.summary }
                .onFailure { applied += "Could not apply: " + command.summary }
        }

        for (date in datesToReplan) {
            runCatching { planningService.replan(date, now) }
        }

        val result = AssistantResult(
            reply = understanding.reply.ifBlank {
                if (applied.isEmpty()) "Nothing needed changing." else "Done."
            },
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

    /**
     * Models sometimes wrap JSON in prose or fences. Recover what we can; if the payload is
     * unusable, fall back to the local parser rather than showing the user a parse error.
     */
    private fun parseModelOutput(raw: String, userText: String, today: LocalDate): Understanding {
        val payload = extractJsonObject(raw)
        if (payload == null) {
            val local = LocalCommandParser.parse(userText, today)
            return Understanding(
                local.commands,
                local.reply,
                AssistantSource.LOCAL,
                "The assistant replied in an unexpected format.",
            )
        }
        return try {
            val dto = json.decodeFromString<AiResponseDto>(payload)
            Understanding(dto.commands, dto.reply.trim(), AssistantSource.MODEL, null)
        } catch (e: Exception) {
            val local = LocalCommandParser.parse(userText, today)
            Understanding(
                local.commands,
                local.reply,
                AssistantSource.LOCAL,
                "The assistant reply could not be read.",
            )
        }
    }

    private suspend fun apply(
        command: ValidatedCommand,
        today: LocalDate,
        now: LocalDateTime,
        replanDates: MutableSet<LocalDate>,
    ) {
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
                val task = taskRepository.task(command.taskId) ?: return
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

            is ValidatedCommand.MoveBlock -> {
                scheduleActions.reschedule(
                    blockId = command.blockId,
                    toDate = command.date,
                    toStart = command.startMinute,
                    reason = "You asked for it to move.",
                    now = now,
                )
            }

            is ValidatedCommand.SkipBlock -> {
                scheduleActions.skip(command.blockId, command.resolution, command.reason, now)
            }

            is ValidatedCommand.TakeBreak -> {
                scheduleActions.takeBreak(command.minutes, now)
            }

            is ValidatedCommand.SetEnergy -> {
                preferencesRepository.update {
                    it.copy(
                        energyMode = command.mode,
                        energyModeDate = command.date.toEpochDay(),
                    )
                }
                replanDates += command.date
            }

            is ValidatedCommand.SetLeisureFloor -> {
                preferencesRepository.update { it.copy(minLeisureMinutes = command.minutes) }
                replanDates += today
            }

            is ValidatedCommand.SetBuildTarget -> {
                preferencesRepository.update {
                    if (command.weekend) {
                        it.copy(buildMinutesWeekend = command.minutes)
                    } else {
                        it.copy(buildMinutesWeekday = command.minutes)
                    }
                }
                replanDates += today
            }

            is ValidatedCommand.Replan -> replanDates += command.date
        }
    }

    /** Explains a scheduled block using the stored reason, never an invented one. */
    suspend fun explain(blockId: Long): String {
        val block = scheduleRepository.block(blockId)
            ?: return "That is no longer on the schedule."
        val reason = block.reason
        return if (reason.isNullOrBlank()) {
            "It sits at " + com.margin.app.core.MarginTime.formatTime(block.start, true) +
                " because that is where the day had room for it."
        } else {
            reason
        }
    }

    /** Resets a light or focused day back to normal. */
    suspend fun clearEnergyOverride() {
        preferencesRepository.update { it.copy(energyMode = EnergyMode.NORMAL, energyModeDate = 0L) }
    }

    private companion object {
        /** Pulls the first balanced JSON object out of a response, fences and prose included. */
        fun extractJsonObject(raw: String): String? {
            val start = raw.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until raw.length) {
                val ch = raw[index]
                when {
                    escaped -> escaped = false
                    ch == '\\' && inString -> escaped = true
                    ch == '"' -> inString = !inString
                    inString -> Unit
                    ch == '{' -> depth++
                    ch == '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, index + 1)
                    }
                }
            }
            return null
        }
    }
}
