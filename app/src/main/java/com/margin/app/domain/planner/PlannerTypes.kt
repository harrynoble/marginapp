package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.TimeRange
import java.time.LocalDate

/** A block the planner may not move: a class, an event, sleep, a meal, or a locked block. */
data class Commitment(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val range: TimeRange,
    val type: BlockType,
    val category: Category,
    val subjectCode: String? = null,
    val taskId: Long? = null,
    val eventId: Long? = null,
    val timetableEntryId: Long? = null,
    val routineId: Long? = null,
    val projectId: Long? = null,
    val status: BlockStatus = BlockStatus.PLANNED,
    val locked: Boolean = false,
    /** Teaching commitments earn revision suggestions; recess and commutes do not. */
    val generatesReview: Boolean = false,
    /** Buffers derived from another commitment rather than declared by the user. */
    val derived: Boolean = false,
)

sealed interface Candidate {
    val id: String
    val minutes: Int
}

/** Work that wants time today. */
data class WorkCandidate(
    override val id: String,
    override val minutes: Int,
    val title: String,
    val subtitle: String? = null,
    val type: BlockType = BlockType.TASK,
    val category: Category = Category.ACADEMICS,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val subjectCode: String? = null,
    val minSession: Int = 25,
    val maxSession: Int = 60,
    val priority: Priority = Priority.NORMAL,
    val difficulty: Difficulty = Difficulty.MODERATE,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    /** Days from the planned date to the deadline. 0 is due today, negative is overdue. */
    val daysToDeadline: Int? = null,
    /** When the deadline falls today, the last minute at which this work is still useful. */
    val deadlineMinute: Int? = null,
    val preferredWindow: TimeRange? = null,
    /**
     * A hard floor on when this may start. Revision of a subject cannot happen before the
     * class that is being revised, however much room the morning has.
     */
    val earliestStart: Int? = null,
    val splittable: Boolean = true,
    /** Extra weight the caller wants applied, for example a task pinned to today. */
    val importance: Int = 0,
) : Candidate

enum class QuotaKind { LEISURE, BUILD }

/** Time reserved before work is placed, so that productivity cannot eat it. */
data class QuotaCandidate(
    override val id: String,
    override val minutes: Int,
    val kind: QuotaKind,
    val title: String,
    val window: TimeRange,
    val minChunk: Int,
    val category: Category,
    val type: BlockType,
) : Candidate

data class PlannerInput(
    val date: LocalDate,
    val prefs: PlannerPreferences,
    val commitments: List<Commitment> = emptyList(),
    val work: List<WorkCandidate> = emptyList(),
    val quotas: List<QuotaCandidate> = emptyList(),
    /** Set when replanning during the day. Nothing is placed before this minute. */
    val nowMinute: Int? = null,
    /** Blocks already completed, skipped or running today. Preserved verbatim. */
    val settled: List<PlacedBlock> = emptyList(),
    /** The plan being revised. Used only to report what changed. */
    val previous: List<PlacedBlock> = emptyList(),
)

data class PlacedBlock(
    val key: String,
    val range: TimeRange,
    val type: BlockType,
    val title: String,
    val subtitle: String? = null,
    val category: Category = Category.OTHER,
    val status: BlockStatus = BlockStatus.PLANNED,
    val taskId: Long? = null,
    val eventId: Long? = null,
    val timetableEntryId: Long? = null,
    val routineId: Long? = null,
    val projectId: Long? = null,
    val subjectCode: String? = null,
    val locked: Boolean = false,
    val reason: String? = null,
) {
    val start: Int get() = range.start
    val end: Int get() = range.end
    val duration: Int get() = range.duration
}

sealed interface Diagnostic {
    val message: String

    data class HardConflict(
        val firstTitle: String,
        val secondTitle: String,
        val range: TimeRange,
        override val message: String,
    ) : Diagnostic

    data class InsufficientTime(
        val candidateId: String,
        val title: String,
        val needed: Int,
        val placed: Int,
        override val message: String,
    ) : Diagnostic

    data class Overloaded(
        val shortfallMinutes: Int,
        val kind: QuotaKind,
        override val message: String,
    ) : Diagnostic

    data class WorkCeilingReached(
        val ceilingMinutes: Int,
        override val message: String,
    ) : Diagnostic

    data class NothingToPlan(override val message: String) : Diagnostic
}

data class PlannedDay(
    val date: LocalDate,
    val blocks: List<PlacedBlock>,
    val diagnostics: List<Diagnostic> = emptyList(),
    val unplaced: List<WorkCandidate> = emptyList(),
) {
    val workMinutes: Int get() = blocks.filter { it.type.isWork }.sumOf { it.duration }
    val leisureMinutes: Int get() = blocks.filter { it.type == BlockType.LEISURE }.sumOf { it.duration }
    val breakMinutes: Int get() = blocks.filter { it.type == BlockType.BREAK }.sumOf { it.duration }
    val freeMinutes: Int get() = blocks.filter { it.type == BlockType.FREE }.sumOf { it.duration }

    fun minutesIn(category: Category): Int =
        blocks.filter { it.category == category && it.type != BlockType.SLEEP }.sumOf { it.duration }
}
