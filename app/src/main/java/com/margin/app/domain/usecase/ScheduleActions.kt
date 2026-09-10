package com.margin.app.domain.usecase

import com.margin.app.core.MarginTime
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.TimeRange
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Everything the user can do to a block while the day is running. Each action writes history
 * first and then asks for a replan, so the rest of the day adapts to what actually happened.
 */
class ScheduleActions(
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val planningService: PlanningService,
) {

    /** Starts a block. Any other running block is closed first so only one can be active. */
    suspend fun start(blockId: Long, now: LocalDateTime = LocalDateTime.now()): ScheduleBlock? {
        val block = scheduleRepository.block(blockId) ?: return null
        scheduleRepository.activeBlock()?.let { running ->
            if (running.id != blockId) pause(running.id, now)
        }
        val updated = block.copy(
            status = BlockStatus.ACTIVE,
            actualStart = block.actualStart ?: System.currentTimeMillis(),
        )
        scheduleRepository.update(updated)
        return updated
    }

    /** Pauses without finishing: the elapsed time is banked and the block returns to planned. */
    suspend fun pause(blockId: Long, now: LocalDateTime = LocalDateTime.now()): ScheduleBlock? {
        val block = scheduleRepository.block(blockId) ?: return null
        val elapsed = block.elapsedMinutes + minutesSince(block.actualStart, now)
        val updated = block.copy(
            status = BlockStatus.PLANNED,
            elapsedMinutes = elapsed.coerceAtMost(block.duration * 3),
            actualStart = null,
        )
        scheduleRepository.update(updated)
        return updated
    }

    /**
     * Finishes a block. Progress is credited to the task, history is written, and the rest of
     * the day is replanned so finishing early actually gives the time back.
     */
    suspend fun complete(
        blockId: Long,
        now: LocalDateTime = LocalDateTime.now(),
        replan: Boolean = true,
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        val worked = when {
            block.actualStart != null -> block.elapsedMinutes + minutesSince(block.actualStart, now)
            block.elapsedMinutes > 0 -> block.elapsedMinutes
            else -> block.duration
        }.coerceIn(1, block.duration * 3)

        val nowMinute = MarginTime.nowMinute(now)
        val finished = block.copy(
            status = BlockStatus.DONE,
            actualEnd = System.currentTimeMillis(),
            elapsedMinutes = worked,
            // Finishing early shortens the block so the freed time is visibly available.
            end = if (block.date == now.toLocalDate() && nowMinute in block.start until block.end) {
                maxOf(block.start + 5, nowMinute)
            } else {
                block.end
            },
        )
        scheduleRepository.update(finished)
        scheduleRepository.recordCompletion(finished, worked)

        block.taskId?.let { taskId ->
            taskRepository.addProgress(taskId, worked)
            taskRepository.recordSession(taskId, block.date, block.duration, worked)
            val task = taskRepository.task(taskId)
            if (task != null && task.remainingMinutes <= 0) taskRepository.markDone(taskId)
        }

        return if (replan) planningService.replan(block.date, now) else null
    }

    /**
     * Skipping is not deletion. The skip is recorded with its reason and the chosen
     * resolution, and the day is replanned around the gap it leaves.
     */
    suspend fun skip(
        blockId: Long,
        resolution: SkipResolution,
        reason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        val skipped = block.copy(status = BlockStatus.SKIPPED, actualEnd = System.currentTimeMillis())
        scheduleRepository.update(skipped)
        scheduleRepository.recordSkip(skipped, reason, resolution)

        when (resolution) {
            SkipResolution.TOMORROW -> block.taskId?.let { id ->
                taskRepository.task(id)?.let { task ->
                    taskRepository.update(task.copy(pinnedDate = block.date.plusDays(1)))
                }
            }
            SkipResolution.DROP_TODAY -> block.taskId?.let { id ->
                taskRepository.task(id)?.let { task ->
                    // Keep the task, but stop it competing for the rest of today.
                    taskRepository.update(task.copy(pinnedDate = block.date.plusDays(1)))
                }
            }
            SkipResolution.REMOVED -> block.taskId?.let { taskRepository.archive(it) }
            SkipResolution.LATER_TODAY, SkipResolution.UNRESOLVED -> Unit
        }

        return planningService.replan(block.date, now)
    }

    /** Adds time to the block that is running, pushing the rest of the day back. */
    suspend fun extend(
        blockId: Long,
        extraMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        if (extraMinutes <= 0) return null
        val block = scheduleRepository.block(blockId) ?: return null
        val updated = block.copy(end = (block.end + extraMinutes).coerceAtMost(24 * 60), locked = true)
        scheduleRepository.update(updated)
        return planningService.replan(block.date, now)
    }

    /** Moves a block to a new start time on a possibly different date. */
    suspend fun reschedule(
        blockId: Long,
        toDate: LocalDate,
        toStart: Int,
        reason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        scheduleRepository.recordReschedule(block, toDate, toStart, reason)

        if (toDate == block.date) {
            scheduleRepository.update(
                block.copy(start = toStart, end = toStart + block.duration, locked = true),
            )
        } else {
            scheduleRepository.delete(block.id)
            scheduleRepository.insert(
                block.copy(
                    id = 0,
                    date = toDate,
                    start = toStart,
                    end = toStart + block.duration,
                    status = BlockStatus.PLANNED,
                    locked = true,
                ),
            )
            block.taskId?.let { id ->
                taskRepository.task(id)?.let { taskRepository.update(it.copy(pinnedDate = toDate)) }
            }
        }
        planningService.replan(block.date, now)
        return if (toDate != block.date) planningService.replan(toDate, now) else null
    }

    /** Pins a block so replanning leaves it alone, or releases it again. */
    suspend fun setLocked(blockId: Long, locked: Boolean) {
        val block = scheduleRepository.block(blockId) ?: return
        scheduleRepository.update(block.copy(locked = locked))
    }

    /**
     * Takes a break right now. The break is inserted at the current minute and the rest of
     * the day is replanned around it, which is what makes "I need another 30 minutes" work.
     */
    suspend fun takeBreak(
        minutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        val date = now.toLocalDate()
        val start = MarginTime.nowMinute(now)
        val end = (start + minutes).coerceAtMost(24 * 60)

        scheduleRepository.activeBlock()?.let { running ->
            if (running.date == date) pause(running.id, now)
        }

        // Make room, but never at the expense of a hard commitment: a break cannot delete a
        // class or an appointment. Only the blocks the planner chose give way.
        val window = TimeRange(start, end)
        for (block in scheduleRepository.blocksFor(date)) {
            if (block.status != BlockStatus.PLANNED) continue
            if (!block.range.overlaps(window)) continue
            if (block.type == BlockType.CLASS || block.type == BlockType.EVENT) continue
            if (block.start >= start) {
                scheduleRepository.delete(block.id)
            } else {
                scheduleRepository.update(block.copy(end = start))
            }
        }

        scheduleRepository.insert(
            ScheduleBlock(
                date = date,
                start = start,
                end = end,
                type = BlockType.BREAK,
                title = "Break",
                category = Category.LEISURE,
                status = BlockStatus.PLANNED,
                locked = true,
                reason = "You asked for a ${minutes} minute break.",
            ),
        )
        return planningService.replan(date, now.plusMinutes(minutes.toLong()))
    }

    private fun minutesSince(startedAtMillis: Long?, now: LocalDateTime): Int {
        if (startedAtMillis == null) return 0
        val nowMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ((nowMillis - startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
    }
}
