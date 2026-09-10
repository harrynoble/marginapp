package com.margin.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Arms a single alarm for the next thing that needs announcing, and re-arms after it fires.
 *
 * One pending alarm rather than dozens: the schedule changes constantly, and a pile of stale
 * alarms is how a planner ends up notifying about work it already moved.
 */
class AlarmScheduler(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    suspend fun rearm(now: LocalDateTime = LocalDateTime.now()) {
        cancel()
        val prefs = preferencesRepository.current()
        if (!prefs.notificationsEnabled) return

        val today = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val target = findNextAnnounceable(today, nowMinute, prefs.notifyLeadMinutes)
            ?: findNextAnnounceable(today.plusDays(1), -1, prefs.notifyLeadMinutes)
            ?: return

        val fireMinute = (target.block.start - prefs.notifyLeadMinutes).coerceAtLeast(0)
        val triggerAt = MarginTime.toEpochMillis(target.date.toEpochDay(), fireMinute)
        if (triggerAt <= System.currentTimeMillis()) return

        schedule(triggerAt, target.block.id, target.date.toEpochDay())
    }

    private data class Target(val block: com.margin.app.domain.model.ScheduleBlock, val date: LocalDate)

    private suspend fun findNextAnnounceable(
        date: LocalDate,
        afterMinute: Int,
        leadMinutes: Int,
    ): Target? {
        val blocks = scheduleRepository.blocksFor(date)
            .filter { it.status == BlockStatus.PLANNED }
            .filter { it.type != BlockType.SLEEP && it.type != BlockType.FREE }
            .filter { it.start - leadMinutes > afterMinute }
            .sortedBy { it.start }
        return blocks.firstOrNull()?.let { Target(it, date) }
    }

    private fun schedule(triggerAtMillis: Long, blockId: Long, epochDay: Long) {
        val manager = alarmManager ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BlockAlarmReceiver::class.java).apply {
                putExtra(BlockAlarmReceiver.EXTRA_BLOCK_ID, blockId)
                putExtra(BlockAlarmReceiver.EXTRA_DATE, epochDay)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Exact alarms need a user grant from Android 12. Without it an inexact alarm is
        // close enough for a reminder, and far better than crashing or nagging for the grant.
        val canBeExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canBeExact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    intent,
                )
            } else {
                manager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    WINDOW_MILLIS,
                    intent,
                )
            }
        }
    }

    fun cancel() {
        val manager = alarmManager ?: return
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BlockAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) {
            manager.cancel(existing)
            existing.cancel()
        }
    }

    private companion object {
        const val REQUEST_CODE = 4201
        const val WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
