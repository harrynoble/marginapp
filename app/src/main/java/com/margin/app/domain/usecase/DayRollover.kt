package com.margin.app.domain.usecase

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.planner.Carryover
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Closes out days that have ended. Planned work that never started is recorded as missed,
 * anything left running or paused is recorded as interrupted with the time actually worked,
 * and yesterday's unfinished academic work is carried into today, capped so today is not
 * punished for it.
 *
 * Idempotent: it remembers the last day it closed, so running it on every app open is free.
 */
class DayRollover(
    private val scheduleRepository: ScheduleRepository,
    private val dayRepository: DayRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
) {

    suspend fun run(now: LocalDateTime = LocalDateTime.now()): Int {
        val today = now.toLocalDate()
        val prefs = preferencesRepository.current()
        val yesterday = today.minusDays(1)
        if (prefs.lastRolloverDay >= yesterday.toEpochDay()) return 0

        val first = if (prefs.lastRolloverDay <= 0L) {
            yesterday
        } else {
            LocalDate.ofEpochDay(prefs.lastRolloverDay + 1)
        }
        var day = maxOf(first, today.minusDays(MAX_DAYS_BACK))
        var carried = 0
        while (day.isBefore(today)) {
            carried += closeOut(day, today, prefs.carryForwardCap)
            day = day.plusDays(1)
        }

        dayRepository.consumeDeferredBefore(today)
        dayRepository.prune(today.minusDays(KEEP_DAYS))
        preferencesRepository.update { it.copy(lastRolloverDay = yesterday.toEpochDay()) }
        if (carried > 0) planningService.replan(today, now)
        return carried
    }

    private suspend fun closeOut(day: LocalDate, today: LocalDate, cap: Int): Int {
        for (block in scheduleRepository.blocksFor(day)) {
            when {
                block.status == BlockStatus.PLANNED && block.type.isWork && !block.optional -> {
                    val missed = block.copy(status = BlockStatus.MISSED, locked = false)
                    scheduleRepository.update(missed)
                    scheduleRepository.recordSkip(
                        block = missed,
                        reason = "The day ended before it started.",
                        resolution = SkipResolution.UNRESOLVED,
                        kind = SkipKind.MISSED,
                    )
                }

                block.status == BlockStatus.ACTIVE || block.status == BlockStatus.PAUSED -> {
                    val sinceStart = block.actualStart?.let { started ->
                        val endMillis = MarginTime.toEpochMillis(day.toEpochDay(), block.end)
                        ((minOf(System.currentTimeMillis(), endMillis) - started) / 60_000L).toInt()
                    } ?: 0
                    val worked = (block.elapsedMinutes + sinceStart.coerceAtLeast(0))
                        .coerceAtMost(block.duration * 3)
                    val interrupted = block.copy(
                        status = BlockStatus.INTERRUPTED,
                        elapsedMinutes = worked,
                        actualStart = null,
                        actualEnd = System.currentTimeMillis(),
                    )
                    scheduleRepository.update(interrupted)
                    if (worked > 0) scheduleRepository.recordCompletion(interrupted, worked, startMinute = block.start)
                }
            }
        }

        // Only yesterday carries into today. Older gaps are not piled on at once.
        if (day != today.minusDays(1)) return 0
        val carry = scheduleRepository.checkIn(day)?.carryForward ?: true
        if (!carry) return 0

        val deferrals = Carryover.unfinished(
            intended = scheduleRepository.plannedWork(day),
            blocks = scheduleRepository.blocksFor(day),
            capMinutes = cap,
        )
        val name = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return dayRepository.defer(
            deferrals.map {
                DeferredWork(
                    sourceKey = it.sourceKey,
                    fromDate = day,
                    toDate = today,
                    subjectCode = it.subjectCode,
                    academicType = it.academicType,
                    title = it.title,
                    minutes = it.minutes,
                    reason = "Unfinished on $name",
                )
            },
        )
    }

    private companion object {
        const val MAX_DAYS_BACK = 7L
        const val KEEP_DAYS = 90L
    }
}
