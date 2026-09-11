package com.margin.app.domain.model

import com.margin.app.domain.model.BlockStatus.ACTIVE
import com.margin.app.domain.model.BlockStatus.CANCELLED
import com.margin.app.domain.model.BlockStatus.DONE
import com.margin.app.domain.model.BlockStatus.INTERRUPTED
import com.margin.app.domain.model.BlockStatus.MISSED
import com.margin.app.domain.model.BlockStatus.PARTIAL
import com.margin.app.domain.model.BlockStatus.PAUSED
import com.margin.app.domain.model.BlockStatus.PLANNED
import com.margin.app.domain.model.BlockStatus.RESCHEDULED
import com.margin.app.domain.model.BlockStatus.SKIPPED

/**
 * Which status changes are allowed. Every action goes through [canMove], so a double tap on
 * "Finish", a notification action arriving after the block already ended, or an assistant
 * command about a skipped block cannot put a block into a state that makes no sense.
 *
 * The finished states (done, skipped, cancelled, rescheduled, interrupted, partly done) are
 * terminal: history is not rewritten. Work that still needs doing is planned again as a new
 * block rather than by reopening the old one.
 */
object BlockStateMachine {

    private val transitions: Map<BlockStatus, Set<BlockStatus>> = mapOf(
        PLANNED to setOf(ACTIVE, DONE, SKIPPED, MISSED, CANCELLED, RESCHEDULED),
        ACTIVE to setOf(PAUSED, DONE, SKIPPED, PARTIAL, INTERRUPTED),
        PAUSED to setOf(ACTIVE, DONE, SKIPPED, PARTIAL, INTERRUPTED),
        // "Start now" after the app has already recorded the session as missed.
        MISSED to setOf(ACTIVE, DONE, SKIPPED, RESCHEDULED),
        DONE to emptySet(),
        SKIPPED to emptySet(),
        PARTIAL to emptySet(),
        CANCELLED to emptySet(),
        RESCHEDULED to emptySet(),
        INTERRUPTED to emptySet(),
    )

    fun canMove(from: BlockStatus, to: BlockStatus): Boolean =
        from == to || to in transitions.getValue(from)

    fun allowedFrom(from: BlockStatus): Set<BlockStatus> = transitions.getValue(from)

    fun isTerminal(status: BlockStatus): Boolean = transitions.getValue(status).isEmpty()

    /**
     * Blocks the planner must keep exactly where they are when it rebuilds the day: anything
     * that has started, finished or been decided, and anything the user pinned.
     */
    fun isSettled(block: ScheduleBlock): Boolean = block.status != PLANNED || block.locked

    /**
     * Whether a settled block still takes up its time. A skipped, missed, cancelled or moved
     * block stays in the record, but its time is free again for whatever comes next.
     */
    fun occupiesTime(block: ScheduleBlock): Boolean =
        block.status != SKIPPED && block.status != MISSED &&
            block.status != CANCELLED && block.status != RESCHEDULED
}
