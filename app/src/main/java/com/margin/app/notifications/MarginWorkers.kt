package com.margin.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.margin.app.MarginApplication
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Two pieces of scheduled background work: build tomorrow before the user wakes up, and ask
 * for the evening check-in. Both are idempotent, so a duplicate run changes nothing.
 */
object MarginWorkers {

    private const val DAILY_PLAN = "margin-daily-plan"
    private const val CHECK_IN = "margin-check-in"

    fun enqueueAll(context: Context) {
        val manager = WorkManager.getInstance(context)

        manager.enqueueUniquePeriodicWork(
            DAILY_PLAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutesUntil(PLAN_HOUR), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build(),
        )

        manager.enqueueUniquePeriodicWork(
            CHECK_IN,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutesUntil(CHECK_IN_HOUR), TimeUnit.MINUTES)
                .build(),
        )
    }

    private fun minutesUntil(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(hour, 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }

    private const val PLAN_HOUR = 5
    private const val CHECK_IN_HOUR = 21
}

/**
 * Builds today, and tomorrow so the morning is already planned. Only fills in gaps: a day
 * that already has blocks is left alone, so this can never overwrite a plan in progress.
 */
class DailyPlanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MarginApplication)?.container
            ?: return Result.success()

        return runCatching {
            Notifier.ensureChannels(applicationContext)
            container.seedService.seedIfNeeded()

            val today = LocalDate.now()
            container.planningService.ensurePlan(today)
            container.planningService.ensurePlan(today.plusDays(1))
            container.alarmScheduler.rearm()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** Asks for the evening review, but only when there is something worth reviewing. */
class CheckInWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MarginApplication)?.container
            ?: return Result.success()

        return runCatching {
            val prefs = container.preferencesRepository.current()
            if (!prefs.checkInEnabled || !prefs.notificationsEnabled) return Result.success()

            val today = LocalDate.now()
            if (container.scheduleRepository.checkIn(today) != null) return Result.success()

            val blocks = container.scheduleRepository.blocksFor(today)
            val done = blocks.filter { it.status == BlockStatus.DONE }
            val skipped = blocks.count { it.status == BlockStatus.SKIPPED }
            if (done.isEmpty() && skipped == 0) return Result.success()

            val minutes = done.sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }
            Notifier.ensureChannels(applicationContext)
            Notifier.postCheckIn(
                applicationContext,
                buildString {
                    append(MarginTime.formatDuration(minutes))
                    append(" finished")
                    if (skipped > 0) {
                        append(", ")
                        append(skipped)
                        append(" skipped")
                    }
                    append(".")
                },
            )
            Result.success()
        }.getOrElse { Result.success() }
    }
}
