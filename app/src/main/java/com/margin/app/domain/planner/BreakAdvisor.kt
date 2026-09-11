package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock

/** An unbroken stretch of work, ending with whatever is running now. */
data class WorkRun(
    val startMinute: Int,
    val minutes: Int,
    val sessions: Int,
    /** The run includes a session that was extended past its plan. */
    val extended: Boolean,
)

data class BreakSuggestion(
    /** The minute the run reaches the point where a break is due. */
    val atMinute: Int,
    val run: WorkRun,
    val suggestedMinutes: Int,
)

/**
 * Suggests breaks from what has actually been worked, rather than from a fixed timer. A run
 * that has been extended, or a low-energy day, earns its break sooner; a longer run earns a
 * longer one.
 */
object BreakAdvisor {

    /** Gaps shorter than this between sessions do not count as a rest. */
    const val MAX_GAP = 10
    private const val MIN_THRESHOLD = 30

    /**
     * @param activeStartMinute the minute the running block was last started; null when
     *   nothing is running.
     */
    fun currentRun(
        blocks: List<ScheduleBlock>,
        nowMinute: Int,
        activeStartMinute: Int?,
    ): WorkRun? {
        val active = blocks.firstOrNull { it.status == BlockStatus.ACTIVE && it.type.isWork } ?: return null
        val startedAt = activeStartMinute ?: active.start
        val activeMinutes = active.elapsedMinutes + (nowMinute - startedAt).coerceAtLeast(0)

        var runStart = minOf(startedAt, active.start)
        var total = activeMinutes
        var sessions = 1
        var extended = active.extendedMinutes > 0

        val earlier = blocks
            .filter { it.id != active.id && it.end <= runStart + MAX_GAP }
            .sortedByDescending { it.end }
        for (block in earlier) {
            if (runStart - block.end > MAX_GAP) break
            if (block.type == BlockType.BREAK && block.status != BlockStatus.SKIPPED) break
            if (!block.type.isWork || !block.status.isWorked) {
                if (block.type == BlockType.FREE || block.type == BlockType.LEISURE) break
                continue
            }
            total += if (block.elapsedMinutes > 0) block.elapsedMinutes else block.duration
            sessions += 1
            extended = extended || block.extendedMinutes > 0
            runStart = block.start
        }
        return WorkRun(runStart, total, sessions, extended)
    }

    fun evaluate(
        run: WorkRun?,
        nowMinute: Int,
        baseThreshold: Int,
        lowEnergy: Boolean,
    ): BreakSuggestion? {
        if (run == null) return null
        var threshold = baseThreshold
        if (run.extended) threshold -= 15
        if (lowEnergy) threshold -= 15
        threshold = threshold.coerceAtLeast(MIN_THRESHOLD)

        val at = nowMinute + (threshold - run.minutes).coerceAtLeast(0)
        val projected = maxOf(run.minutes, threshold)
        val length = when {
            projected >= 120 || run.sessions >= 3 -> 20
            else -> 15
        }
        return BreakSuggestion(atMinute = at, run = run, suggestedMinutes = length)
    }
}
