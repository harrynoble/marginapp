package com.margin.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.margin.app.MarginApplication
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background work, in two pieces. Early each morning: close out yesterday, carry what is owed,
 * and build today and tomorrow. Every fifteen minutes: a light check that posts any prompt an
 * alarm may have missed. Android can delay or drop a single alarm; together these make the
 * guidance reliable without relying on the app being open.
 */
object MarginWorkers {

    private const val DAILY_PLAN = "margin-daily-plan"
    private const val NUDGE_CHECK = "margin-nudge-check"
    /** Replaced in version 2 by the evening review prompt. */
    private const val LEGACY_CHECK_IN = "margin-check-in"

    fun enqueueAll(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(LEGACY_CHECK_IN)

        manager.enqueueUniquePeriodicWork(
            DAILY_PLAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyPlanWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(minutesUntil(PLAN_HOUR), TimeUnit.MINUTES)
                .build(),
        )

        manager.enqueueUniquePeriodicWork(
            NUDGE_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private fun minutesUntil(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var target = now.toLocalDate().atTime(hour, 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }

    private const val PLAN_HOUR = 4
}

/**
 * Closes out the day that ended, then builds today and tomorrow. Only fills gaps: a day that
 * already has blocks is left alone, so this never overwrites a plan in progress.
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
            container.dayRollover.run()
            val today = LocalDate.now()
            container.planningService.ensurePlan(today)
            container.planningService.ensurePlan(today.plusDays(1))
            container.alarmScheduler.rearm()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** The safety net: posts anything due that an alarm did not deliver, and re-arms. */
class NudgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MarginApplication)?.container
            ?: return Result.success()
        return runCatching {
            container.dayRollover.run()
            container.planningService.ensurePlan(LocalDate.now())
            container.alarmScheduler.fireDue()
            container.alarmScheduler.rearm()
            Result.success()
        }.getOrElse { Result.success() }
    }
}
