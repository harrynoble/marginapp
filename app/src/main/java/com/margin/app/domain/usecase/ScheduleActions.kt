package com.margin.app.domain.usecase

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.domain.model.BlockStateMachine
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.Carryover
import com.margin.app.domain.planner.EnergyMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** What going out did to the rest of the day. */
data class GoOutResult(
    val plan: PlanResult,
    /** Work moved to tomorrow because today no longer had room for it. */
    val movedToTomorrow: Int,
    val message: String,
)

/**
 * Everything the user can do to a block, or to the day, while it is running. Each action
 * checks the block's state first, writes history, and then asks for a replan, so the rest of
 * the day adapts to what actually happened.
 */
class ScheduleActions(
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val planningService: PlanningService,
    private val dayRepository: DayRepository,
    private val preferencesRepository: PreferencesRepository,
) {

    // ---------------------------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------------------------

    /**
     * Starts a block, or resumes a paused one. Anything else running is paused first so only
     * one thing is ever active. Starting noticeably early or late moves the block to now, so
     * the timeline and the end-of-session prompt tell the truth.
     */
    suspend fun start(blockId: Long, now: LocalDateTime = LocalDateTime.now()): ScheduleBlock? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (!BlockStateMachine.canMove(block.status, BlockStatus.ACTIVE)) return null
        if (block.status == BlockStatus.ACTIVE) return block

        scheduleRepository.activeBlock()?.let { running ->
            if (running.id != blockId) pause(running.id, now)
        }

        val today = block.date == now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        var start = block.start
        var end = block.end
        var moved = false
        if (today && block.status == BlockStatus.PAUSED) {
            val left = (planned(block) - block.elapsedMinutes).coerceAtLeast(MIN_RESUME)
            if (nowMinute + left > end) {
                end = (nowMinute + left).coerceAtMost(DAY_END)
                moved = true
            }
        } else if (today && abs(nowMinute - block.start) >= MOVE_THRESHOLD && block.type.isActionable) {
            start = nowMinute
            end = (nowMinute + block.duration).coerceAtMost(DAY_END)
            moved = true
        }

        val updated = block.copy(
            status = BlockStatus.ACTIVE,
            actualStart = System.currentTimeMillis(),
            start = start,
            end = maxOf(end, start + 5),
            locked = block.locked || moved,
            plannedMinutes = planned(block),
        )
        scheduleRepository.update(updated)
        if (moved) planningService.replan(block.date, now)
        return scheduleRepository.block(blockId)
    }

    /** Pauses without finishing. The time worked so far is banked. */
    suspend fun pause(blockId: Long, now: LocalDateTime = LocalDateTime.now()): ScheduleBlock? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (block.status != BlockStatus.ACTIVE) return null
        val elapsed = block.elapsedMinutes + minutesSince(block.actualStart, now)
        val updated = block.copy(
            status = BlockStatus.PAUSED,
            elapsedMinutes = elapsed.coerceAtMost(block.duration * 3),
            actualStart = null,
        )
        scheduleRepository.update(updated)
        return updated
    }

    /**
     * Finishes a block. Actual time is recorded against planned time, progress is credited to
     * the task, and the rest of the day is replanned so finishing early gives time back.
     */
    suspend fun complete(
        blockId: Long,
        now: LocalDateTime = LocalDateTime.now(),
        replan: Boolean = true,
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (!BlockStateMachine.canMove(block.status, BlockStatus.DONE) || block.status == BlockStatus.DONE) return null
        val worked = when {
            block.actualStart != null -> block.elapsedMinutes + minutesSince(block.actualStart, now)
            block.elapsedMinutes > 0 -> block.elapsedMinutes
            else -> block.duration
        }.coerceIn(1, maxOf(block.duration, planned(block)) * 3)

        val nowMinute = MarginTime.nowMinute(now)
        val today = block.date == now.toLocalDate()
        val end = when {
            // Finishing early shortens the block so the freed time is visibly available.
            today && nowMinute in block.start until block.end -> maxOf(block.start + 5, nowMinute)
            // Running over is recorded as it happened.
            today && nowMinute > block.end && block.status == BlockStatus.ACTIVE -> nowMinute.coerceAtMost(DAY_END)
            else -> block.end
        }
        val finished = block.copy(
            status = BlockStatus.DONE,
            actualEnd = System.currentTimeMillis(),
            elapsedMinutes = worked,
            end = end,
            plannedMinutes = planned(block),
        )
        scheduleRepository.update(finished)
        scheduleRepository.recordCompletion(finished, worked, startMinute = block.start)

        block.taskId?.let { taskId ->
            taskRepository.recordSession(taskId, block.date, planned(block), worked)
            val task = taskRepository.task(taskId)
            // A recurring task is never finished by one occurrence, so its progress is not
            // banked against a total that would eventually close it.
            if (task != null && !task.isRecurring) {
                taskRepository.addProgress(taskId, worked)
                val updated = taskRepository.task(taskId)
                if (updated != null && updated.remainingMinutes <= 0) {
                    taskRepository.markDone(taskId)
                }
            }
        }

        return if (replan) planningService.replan(block.date, now) else null
    }

    /**
     * Keeps going past the planned end. The extra time is tracked on the block and credited
     * when it finishes; nothing is lost, and the rest of the day moves back to make room.
     */
    suspend fun continueSession(
        blockId: Long,
        minutes: Int = CONTINUE_MINUTES,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (block.status != BlockStatus.ACTIVE && block.status != BlockStatus.PAUSED) return null
        val nowMinute = if (block.date == now.toLocalDate()) MarginTime.nowMinute(now) else block.end
        val newEnd = (maxOf(block.end, nowMinute) + minutes).coerceAtMost(DAY_END)
        scheduleRepository.update(
            block.copy(
                end = newEnd,
                extendedMinutes = block.extendedMinutes + (newEnd - block.end).coerceAtLeast(0),
                locked = true,
                plannedMinutes = planned(block),
            ),
        )
        return planningService.replan(block.date, now)
    }

    /** Finishes what is running and starts the next planned session straight away. */
    suspend fun moveToNext(blockId: Long, now: LocalDateTime = LocalDateTime.now()): ScheduleBlock? {
        val current = scheduleRepository.block(blockId) ?: return null
        complete(current.id, now, replan = true)
        val nowMinute = MarginTime.nowMinute(now)
        val next = scheduleRepository.blocksFor(current.date)
            .filter { it.status == BlockStatus.PLANNED && it.type.isWork && it.end > nowMinute }
            .minByOrNull { it.start }
            ?: return null
        return start(next.id, now)
    }

    /**
     * Skipping is not deletion. The skip is recorded with its reason and resolution, and the
     * day is replanned around the gap. Moving to tomorrow carries the work there explicitly.
     */
    suspend fun skip(
        blockId: Long,
        resolution: SkipResolution,
        reason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (!BlockStateMachine.canMove(block.status, BlockStatus.SKIPPED) || block.status == BlockStatus.SKIPPED) return null
        val worked = block.elapsedMinutes + minutesSince(block.actualStart, now)
        val skipped = block.copy(
            status = BlockStatus.SKIPPED,
            actualEnd = System.currentTimeMillis(),
            actualStart = null,
            elapsedMinutes = worked,
            skipResolution = resolution,
            plannedMinutes = planned(block),
        )
        scheduleRepository.update(skipped)
        scheduleRepository.recordSkip(skipped, reason, resolution)

        val tomorrow = block.date.plusDays(1)
        when (resolution) {
            SkipResolution.TOMORROW -> {
                block.taskId?.let { id ->
                    taskRepository.task(id)?.let { taskRepository.update(it.copy(pinnedDate = tomorrow)) }
                }
                if (Carryover.isCarryable(block.candidateId)) {
                    dayRepository.defer(listOf(deferral(block, tomorrow, planned(block), "Moved from ${dayName(block.date)}")))
                }
            }
            SkipResolution.DROP_TODAY -> block.taskId?.let { id ->
                taskRepository.task(id)?.let { task ->
                    // Keep the task, but stop it competing for the rest of today.
                    taskRepository.update(task.copy(pinnedDate = tomorrow))
                }
            }
            SkipResolution.REMOVED -> block.taskId?.let { taskRepository.archive(it) }
            SkipResolution.LATER_TODAY, SkipResolution.UNRESOLVED -> Unit
        }

        return planningService.replan(block.date, now)
    }

    /** Records a session as missed. Its work is placed again later if the day has room. */
    suspend fun markMissed(
        blockId: Long,
        reason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (!BlockStateMachine.canMove(block.status, BlockStatus.MISSED) || block.status == BlockStatus.MISSED) return null
        val missed = block.copy(status = BlockStatus.MISSED, locked = false, plannedMinutes = planned(block))
        scheduleRepository.update(missed)
        scheduleRepository.recordSkip(missed, reason, SkipResolution.UNRESOLVED, SkipKind.MISSED)
        return planningService.replan(block.date, now)
    }

    /** Adds time to a block, pushing the rest of the day back. */
    suspend fun extend(
        blockId: Long,
        extraMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        if (extraMinutes <= 0) return null
        val block = scheduleRepository.block(blockId) ?: return null
        if (!block.status.isOpen) return null
        val running = block.status == BlockStatus.ACTIVE || block.status == BlockStatus.PAUSED
        val updated = block.copy(
            end = (block.end + extraMinutes).coerceAtMost(DAY_END),
            locked = true,
            extendedMinutes = if (running) block.extendedMinutes + extraMinutes else block.extendedMinutes,
            plannedMinutes = if (running) planned(block) else planned(block) + extraMinutes,
        )
        scheduleRepository.update(updated)
        return planningService.replan(block.date, now)
    }

    /**
     * Moves a block to a new start on the same or another day. A move to another day leaves
     * the original in the record as rescheduled and creates the new one where it went.
     */
    suspend fun reschedule(
        blockId: Long,
        toDate: LocalDate,
        toStart: Int,
        reason: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult? {
        val block = scheduleRepository.block(blockId) ?: return null
        if (!block.status.isOpen && block.status != BlockStatus.MISSED) return null
        scheduleRepository.recordReschedule(block, toDate, toStart, reason)
        val start = toStart.coerceIn(0, DAY_END - 5)

        if (toDate == block.date) {
            scheduleRepository.update(
                block.copy(
                    start = start,
                    end = (start + block.duration).coerceAtMost(DAY_END),
                    status = if (block.status == BlockStatus.MISSED) BlockStatus.PLANNED else block.status,
                    locked = true,
                ),
            )
        } else {
            scheduleRepository.update(block.copy(status = BlockStatus.RESCHEDULED, plannedMinutes = planned(block)))
            scheduleRepository.insert(
                block.copy(
                    id = 0,
                    date = toDate,
                    start = start,
                    end = (start + block.duration).coerceAtMost(DAY_END),
                    status = BlockStatus.PLANNED,
                    locked = true,
                    actualStart = null,
                    actualEnd = null,
                    elapsedMinutes = 0,
                    plannedStart = start,
                    plannedMinutes = block.duration,
                    skipResolution = null,
                ),
            )
            block.taskId?.let { id ->
                taskRepository.task(id)?.let { taskRepository.update(it.copy(pinnedDate = toDate)) }
            }
        }
        val result = planningService.replan(block.date, now)
        return if (toDate != block.date) planningService.replan(toDate, now) else result
    }

    /** Pins a block so replanning leaves it alone, or releases it again. */
    suspend fun setLocked(blockId: Long, locked: Boolean) {
        val block = scheduleRepository.block(blockId) ?: return
        scheduleRepository.update(block.copy(locked = locked))
    }

    // ---------------------------------------------------------------------------------------
    // Breaks
    // ---------------------------------------------------------------------------------------

    /**
     * Takes a break right now. The break is inserted at the current minute and the rest of
     * the day is replanned around it. Hard commitments are never moved to make room.
     */
    suspend fun takeBreak(
        minutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
        reason: BreakReason = BreakReason.MANUAL,
    ): PlanResult {
        val date = now.toLocalDate()
        val start = MarginTime.nowMinute(now)
        val end = (start + minutes).coerceAtMost(DAY_END)

        scheduleRepository.activeBlock()?.let { running ->
            if (running.date == date) pause(running.id, now)
        }

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
                title = if (reason == BreakReason.MEAL) "Meal break" else "Break",
                category = Category.LEISURE,
                status = BlockStatus.PLANNED,
                locked = true,
                reason = "You took a $minutes minute break.",
                plannedStart = start,
                plannedMinutes = end - start,
            ),
        )
        scheduleRepository.recordBreak(date, start, end - start, reason)
        return planningService.replan(date, now)
    }

    // ---------------------------------------------------------------------------------------
    // The day
    // ---------------------------------------------------------------------------------------

    /**
     * The user is out until [backMinute] today, or for the rest of the day when [backMinute]
     * is null. The time is blocked, the day is replanned around it, and academic work that no
     * longer fits is moved to tomorrow rather than silently dropped.
     */
    suspend fun goOut(
        backMinute: Int?,
        now: LocalDateTime = LocalDateTime.now(),
    ): GoOutResult {
        val date = now.toLocalDate()
        val prefs = preferencesRepository.current()
        val nowMinute = MarginTime.nowMinute(now)
        val sleep = if (prefs.sleepMinute > nowMinute) prefs.sleepMinute else DAY_END
        val end = (backMinute ?: sleep).coerceIn(nowMinute + 5, DAY_END)

        scheduleRepository.activeBlock()?.let { running ->
            if (running.date == date) pause(running.id, now)
        }
        taskRepository.createEvent(
            CalendarEvent(
                title = "Out",
                date = date,
                start = nowMinute,
                end = end,
                category = Category.PERSONAL,
                hard = true,
            ),
        )
        dayRepository.update(date) { it.copy(outUntilMinute = end) }

        val plan = planningService.replan(date, now)
        val tomorrow = date.plusDays(1)
        val moves = plan.unplaced
            .filter { Carryover.isCarryable(it.id) && it.minutes >= Carryover.MIN_MINUTES }
            .map { candidate ->
                DeferredWork(
                    sourceKey = candidate.id,
                    fromDate = date,
                    toDate = tomorrow,
                    subjectCode = candidate.subjectCode,
                    academicType = candidate.academicType,
                    title = candidate.title,
                    minutes = candidate.minutes.coerceAtMost(Carryover.MAX_ITEM_MINUTES),
                    reason = "You were out on ${dayName(date)}",
                )
            }
        val moved = dayRepository.defer(moves)
        if (moved > 0) planningService.replan(tomorrow, now)

        val back = if (backMinute == null) "for the rest of the day" else "until " + MarginTime.formatTime(end, prefs.use24HourTime)
        val message = when {
            moved > 0 -> "Blocked out $back. There isn't room for everything today, so I'll move the remaining work to tomorrow."
            else -> "Blocked out $back. The rest of the day fits around it."
        }
        return GoOutResult(plan, moved, message)
    }

    suspend fun setBuildDecision(
        date: LocalDate,
        decision: Decision,
        projectId: Long? = null,
        minutes: Int? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        dayRepository.update(date) {
            it.copy(
                buildDecision = decision,
                buildProjectId = projectId ?: it.buildProjectId,
                buildMinutes = minutes ?: it.buildMinutes,
            )
        }
        return planningService.replan(date, now)
    }

    suspend fun setLearningDecision(
        date: LocalDate,
        decision: Decision,
        goalId: Long? = null,
        minutes: Int? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        dayRepository.update(date) {
            it.copy(
                learningDecision = decision,
                learningGoalId = goalId ?: it.learningGoalId,
                learningMinutes = minutes ?: it.learningMinutes,
            )
        }
        return planningService.replan(date, now)
    }

    /**
     * "Lighten today". Only what the user marks essential stays, plus anything that genuinely
     * cannot wait; build and learning can be switched off for the day. Nothing dropped here is
     * recorded as skipped or piled onto tomorrow.
     */
    suspend fun lightenToday(
        date: LocalDate,
        essentials: Set<String>,
        priorities: Set<String>,
        dropBuild: Boolean,
        dropLearning: Boolean,
        lowEnergy: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        dayRepository.update(date) {
            it.copy(
                lightDay = true,
                essentials = essentials,
                prioritySubjects = priorities,
                buildDecision = if (dropBuild) Decision.DECLINED else it.buildDecision,
                learningDecision = if (dropLearning) Decision.DECLINED else it.learningDecision,
            )
        }
        if (lowEnergy) setEnergyPreference(date, EnergyMode.LIGHT)
        return planningService.replan(date, now)
    }

    suspend fun clearLighten(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): PlanResult {
        dayRepository.update(date) { it.copy(lightDay = false, essentials = emptySet(), prioritySubjects = emptySet()) }
        setEnergyPreference(date, EnergyMode.NORMAL)
        return planningService.replan(date, now)
    }

    /**
     * "I don't want to study Mathematics today." Respected, and the work it had today moves
     * to tomorrow rather than disappearing.
     */
    suspend fun excludeSubjectToday(
        date: LocalDate,
        subjectCode: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        val intended = scheduleRepository.plannedWork(date).filter { it.subjectCode == subjectCode }
        val consumed = Carryover.consumedByCandidate(scheduleRepository.blocksFor(date))
        val tomorrow = date.plusDays(1)
        dayRepository.defer(
            intended.filter { Carryover.isCarryable(it.id) }.mapNotNull { work ->
                val left = work.minutes - (consumed[work.id] ?: 0)
                if (left < Carryover.MIN_MINUTES) {
                    null
                } else {
                    DeferredWork(
                        sourceKey = work.id,
                        fromDate = date,
                        toDate = tomorrow,
                        subjectCode = work.subjectCode,
                        academicType = work.academicType,
                        title = work.title,
                        minutes = left.coerceAtMost(Carryover.MAX_ITEM_MINUTES),
                        reason = "Left out on ${dayName(date)}",
                    )
                }
            },
        )
        dayRepository.update(date) { it.copy(excludedSubjects = it.excludedSubjects + subjectCode) }
        planningService.replan(tomorrow, now)
        return planningService.replan(date, now)
    }

    suspend fun includeSubjectToday(
        date: LocalDate,
        subjectCode: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        dayRepository.update(date) { it.copy(excludedSubjects = it.excludedSubjects - subjectCode) }
        return planningService.replan(date, now)
    }

    /** Switches the minimum day on, or turns down the planner's suggestion of one. */
    suspend fun setMinimumDay(
        date: LocalDate,
        on: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        dayRepository.update(date) {
            if (on) it.copy(minimumDay = true, minimumDayDismissed = false)
            else it.copy(minimumDay = false, minimumDayDismissed = true)
        }
        return planningService.replan(date, now)
    }

    suspend fun setEnergy(
        date: LocalDate,
        mode: EnergyMode,
        now: LocalDateTime = LocalDateTime.now(),
    ): PlanResult {
        setEnergyPreference(date, mode)
        return planningService.replan(date, now)
    }

    private suspend fun setEnergyPreference(date: LocalDate, mode: EnergyMode) {
        preferencesRepository.update { it.copy(energyMode = mode, energyModeDate = date.toEpochDay()) }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun deferral(block: ScheduleBlock, to: LocalDate, minutes: Int, reason: String) = DeferredWork(
        sourceKey = block.candidateId ?: "block:${block.id}",
        fromDate = block.date,
        toDate = to,
        subjectCode = block.subjectCode,
        academicType = block.academicType,
        title = block.title,
        minutes = minutes.coerceAtMost(Carryover.MAX_ITEM_MINUTES),
        reason = reason,
    )

    private fun planned(block: ScheduleBlock): Int =
        if (block.plannedMinutes > 0) block.plannedMinutes else block.duration

    private fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun minutesSince(startedAtMillis: Long?, now: LocalDateTime): Int {
        if (startedAtMillis == null) return 0
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ((nowMillis - startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val DAY_END = 24 * 60
        const val MOVE_THRESHOLD = 5
        const val MIN_RESUME = 10
        const val CONTINUE_MINUTES = 15
    }
}
