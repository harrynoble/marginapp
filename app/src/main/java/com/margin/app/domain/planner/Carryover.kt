package com.margin.app.domain.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution

/** Work the planner wanted on a day, as it last planned it. */
data class PlannedWork(
    val id: String,
    val title: String,
    val subjectCode: String?,
    val academicType: AcademicType?,
    val minutes: Int,
)

/** A piece of work to move to another day, and why. */
data class Deferral(
    val sourceKey: String,
    val subjectCode: String?,
    val academicType: AcademicType?,
    val title: String,
    val minutes: Int,
)

/**
 * The bookkeeping that keeps missed work from vanishing without dumping it all on tomorrow.
 *
 * Reviews, subject study and exam study are regenerated every day, so if they do not happen
 * they need carrying explicitly. Tasks carry themselves: their remaining minutes simply stay
 * on the task.
 */
object Carryover {

    /** Less than this left over is not worth moving. */
    const val MIN_MINUTES = 15
    /** No single item carries more than this into a day. */
    const val MAX_ITEM_MINUTES = 60

    fun isCarryable(candidateId: String?): Boolean =
        candidateId != null && (
            candidateId.startsWith("review:") ||
                candidateId.startsWith("study:") ||
                candidateId.startsWith("exam:")
            )

    /**
     * How much of its candidate a block has used up. Finished, running and pinned work
     * counts; a skip counts only when it was a decision about today; a missed or moved
     * session counts for nothing, which is exactly why its work gets placed again.
     */
    fun consumedMinutes(block: ScheduleBlock): Int {
        val planned = maxOf(block.plannedMinutes, block.duration)
        return when (block.status) {
            BlockStatus.DONE -> maxOf(block.elapsedMinutes, planned)
            BlockStatus.PARTIAL, BlockStatus.INTERRUPTED ->
                if (block.elapsedMinutes > 0) block.elapsedMinutes else block.duration
            BlockStatus.ACTIVE, BlockStatus.PAUSED -> maxOf(block.elapsedMinutes, block.duration)
            BlockStatus.SKIPPED ->
                if (block.skipResolution == SkipResolution.LATER_TODAY) 0 else block.duration
            BlockStatus.PLANNED -> if (block.locked) block.duration else 0
            // Moved to another day: handled for this one, it lives on where it was moved to.
            BlockStatus.RESCHEDULED -> block.duration
            BlockStatus.MISSED, BlockStatus.CANCELLED -> 0
        }
    }

    fun consumedByCandidate(blocks: List<ScheduleBlock>): Map<String, Int> =
        blocks.filter { it.candidateId != null }
            .groupBy { it.candidateId!! }
            .mapValues { (_, list) -> list.sumOf { consumedMinutes(it) } }

    /**
     * What a finished day left undone, capped so tomorrow is not punished for today.
     * Items dropped on purpose (a light day, a subject left out) were never planned, so they
     * never appear here.
     */
    fun unfinished(
        intended: List<PlannedWork>,
        blocks: List<ScheduleBlock>,
        capMinutes: Int,
    ): List<Deferral> {
        val consumed = consumedByCandidate(blocks)
        val pending = intended
            .filter { isCarryable(it.id) && !it.id.startsWith("carry:") }
            .mapNotNull { work ->
                val left = work.minutes - (consumed[work.id] ?: 0)
                if (left < MIN_MINUTES) null else work to left.coerceAtMost(MAX_ITEM_MINUTES)
            }
            .sortedWith(compareByDescending<Pair<PlannedWork, Int>> { it.second }.thenBy { it.first.id })

        var budget = capMinutes
        val out = mutableListOf<Deferral>()
        for ((work, left) in pending) {
            if (budget < MIN_MINUTES) break
            val minutes = minOf(left, budget)
            out += Deferral(
                sourceKey = work.id,
                subjectCode = work.subjectCode,
                academicType = work.academicType,
                title = work.title,
                minutes = minutes,
            )
            budget -= minutes
        }
        return out
    }
}
