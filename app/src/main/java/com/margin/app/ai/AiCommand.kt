package com.margin.app.ai

import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Task
import com.margin.app.domain.planner.EnergyMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * The wire shape. Deliberately one flat, fully optional record rather than a polymorphic
 * hierarchy: a model that omits a field or invents one should produce a validation error,
 * not a deserialisation crash.
 */
@Serializable
data class AiResponseDto(
    val reply: String = "",
    val commands: List<AiCommandDto> = emptyList(),
)

@Serializable
data class AiCommandDto(
    val action: String = "",
    val title: String? = null,
    val notes: String? = null,
    val category: String? = null,
    val subject: String? = null,
    val subjects: List<String>? = null,
    val minutes: Int? = null,
    val priority: String? = null,
    val difficulty: String? = null,
    val energy: String? = null,
    val date: String? = null,
    val deadline: String? = null,
    val start: String? = null,
    val end: String? = null,
    val until: String? = null,
    @SerialName("task_id") val taskId: Long? = null,
    @SerialName("block_id") val blockId: Long? = null,
    val mode: String? = null,
    val resolution: String? = null,
    val reason: String? = null,
    val splittable: Boolean? = null,
    @SerialName("drop_build") val dropBuild: Boolean? = null,
    @SerialName("drop_learning") val dropLearning: Boolean? = null,
    val type: String? = null,
)

/** The only vocabulary the assistant is allowed to speak in. */
object AiActions {
    const val CREATE_TASK = "create_task"
    const val CREATE_EVENT = "create_event"
    const val UPDATE_TASK = "update_task"
    const val COMPLETE_TASK = "complete_task"
    const val DELETE_TASK = "delete_task"
    const val MOVE_BLOCK = "move_block"
    const val SKIP_BLOCK = "skip_block"
    const val TAKE_BREAK = "take_break"
    const val SET_ENERGY = "set_energy"
    const val SET_LEISURE = "set_leisure_floor"
    const val SET_BUILD = "set_build_target"
    const val REPLAN = "replan"
    const val GO_OUT = "go_out"
    const val LIGHTEN_DAY = "lighten_day"
    const val MINIMUM_DAY = "minimum_day"
    const val BUILD_TODAY = "build_today"
    const val NO_BUILD_TODAY = "no_build_today"
    const val LEARN_TODAY = "learn_today"
    const val NO_LEARNING_TODAY = "no_learning_today"
    const val SKIP_SUBJECT_TODAY = "skip_subject_today"
    const val EXTEND_CURRENT = "extend_current"
    const val ADD_EXAM = "add_exam"
    const val MISSED_SESSION = "missed_session"
    const val WHAT_NOW = "what_now"
    const val EXPLAIN = "explain"
    const val NONE = "none"

    val all = setOf(
        CREATE_TASK, CREATE_EVENT, UPDATE_TASK, COMPLETE_TASK, DELETE_TASK,
        MOVE_BLOCK, SKIP_BLOCK, TAKE_BREAK, SET_ENERGY, SET_LEISURE, SET_BUILD,
        REPLAN, GO_OUT, LIGHTEN_DAY, MINIMUM_DAY, BUILD_TODAY, NO_BUILD_TODAY,
        LEARN_TODAY, NO_LEARNING_TODAY, SKIP_SUBJECT_TODAY, EXTEND_CURRENT, ADD_EXAM,
        MISSED_SESSION, WHAT_NOW, EXPLAIN, NONE,
    )
}

/**
 * A command that has passed validation. Nothing else can reach the database, which is what
 * keeps arbitrary model output from becoming application state.
 */
sealed interface ValidatedCommand {
    /** A short sentence describing what happened, shown to the user after it is applied. */
    val summary: String

    data class CreateTask(val task: Task, override val summary: String) : ValidatedCommand

    data class CreateEvent(val event: CalendarEvent, override val summary: String) : ValidatedCommand

    data class UpdateTask(
        val taskId: Long,
        val title: String? = null,
        val minutes: Int? = null,
        val deadline: LocalDate? = null,
        val priority: Priority? = null,
        val difficulty: Difficulty? = null,
        val energy: EnergyLevel? = null,
        val category: Category? = null,
        val notes: String? = null,
        val splittable: Boolean? = null,
        override val summary: String,
    ) : ValidatedCommand

    data class CompleteTask(val taskId: Long, override val summary: String) : ValidatedCommand

    data class DeleteTask(val taskId: Long, override val summary: String) : ValidatedCommand

    data class MoveBlock(
        val blockId: Long,
        val date: LocalDate,
        val startMinute: Int,
        override val summary: String,
    ) : ValidatedCommand

    data class SkipBlock(
        val blockId: Long,
        val resolution: SkipResolution,
        val reason: String?,
        override val summary: String,
    ) : ValidatedCommand

    data class TakeBreak(val minutes: Int, override val summary: String) : ValidatedCommand

    data class SetEnergy(
        val mode: EnergyMode,
        val date: LocalDate,
        override val summary: String,
    ) : ValidatedCommand

    data class SetLeisureFloor(val minutes: Int, override val summary: String) : ValidatedCommand

    data class SetBuildTarget(
        val minutes: Int,
        val weekend: Boolean,
        override val summary: String,
    ) : ValidatedCommand

    data class Replan(val date: LocalDate, override val summary: String) : ValidatedCommand

    /** Out until [backMinute] today, or for the rest of the day when null. */
    data class GoOut(val backMinute: Int?, override val summary: String) : ValidatedCommand

    data class LightenDay(
        val essentialSubjects: Set<String>,
        val dropBuild: Boolean,
        val dropLearning: Boolean,
        override val summary: String,
    ) : ValidatedCommand

    data class MinimumDay(override val summary: String) : ValidatedCommand

    data class SetBuildToday(
        val decision: Decision,
        val minutes: Int?,
        override val summary: String,
    ) : ValidatedCommand

    data class SetLearningToday(
        val decision: Decision,
        val minutes: Int?,
        override val summary: String,
    ) : ValidatedCommand

    data class ExcludeSubject(val subjectCode: String, override val summary: String) : ValidatedCommand

    data class ExtendCurrent(val minutes: Int, override val summary: String) : ValidatedCommand

    data class AddExam(val exam: Exam, override val summary: String) : ValidatedCommand

    data class MissedSession(override val summary: String) : ValidatedCommand

    /** Answered from the plan itself, never by the model. */
    data class WhatNow(override val summary: String) : ValidatedCommand

    /** Explained from the reason stored on the block, never invented. */
    data class Explain(val blockId: Long?, override val summary: String) : ValidatedCommand
}

/** Why a command was refused. The user sees this rather than a silent no-op. */
data class CommandRejection(val action: String, val reason: String)

data class ValidationResult(
    val commands: List<ValidatedCommand> = emptyList(),
    val rejections: List<CommandRejection> = emptyList(),
)
