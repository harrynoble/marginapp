package com.margin.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.planner.Nudge
import com.margin.app.domain.planner.NudgePlanner
import com.margin.app.domain.planner.NudgeState
import com.margin.app.domain.planner.NudgeType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps exactly one alarm armed: for the next guidance prompt the day needs. When it fires,
 * the prompts are worked out again from the day as it is now, so a session that was already
 * started, moved or finished is never announced.
 *
 * Restraint is built in: one prompt per firing, at least [MIN_GAP_MILLIS] between non-urgent
 * prompts, nothing posted twice, nothing posted stale.
 */
class AlarmScheduler(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dayRepository: DayRepository,
) {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    /** Works out when the next prompt is due and arms an alarm for it. */
    suspend fun rearm(now: LocalDateTime = LocalDateTime.now()) = lock.withLock {
        cancel()
        val prefs = preferencesRepository.current()
        if (!prefs.notificationsEnabled) return@withLock

        val today = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val nudges = nudgesFor(today, nowMinute)

        val pending = NudgePlanner.due(nudges, nowMinute)
        val triggerAt = if (pending.isNotEmpty()) {
            // Something is due but was held back by the gap between prompts.
            val last = dayRepository.lastPostedAt(today) ?: 0L
            maxOf(System.currentTimeMillis() + 60_000L, last + MIN_GAP_MILLIS)
        } else {
            val next = NudgePlanner.next(nudges, nowMinute)
            if (next != null) {
                MarginTime.toEpochMillis(today.toEpochDay(), next.atMinute)
            } else {
                // Nothing more today. Wake at the start of tomorrow to look again.
                MarginTime.toEpochMillis(today.plusDays(1).toEpochDay(), prefs.wakeMinute)
            }
        }
        if (triggerAt > System.currentTimeMillis()) schedule(triggerAt)
    }

    /**
     * Posts the most important prompt that is due right now, if any. Returns true when
     * something was posted.
     */
    suspend fun fireDue(now: LocalDateTime = LocalDateTime.now()): Boolean = lock.withLock {
        val prefs = preferencesRepository.current()
        if (!prefs.notificationsEnabled || !Notifier.canPost(context)) return@withLock false

        val today = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val due = NudgePlanner.due(nudgesFor(today, nowMinute), nowMinute)
        val top = due.firstOrNull() ?: return@withLock false

        val urgent = top.type == NudgeType.TRANSITION || top.type == NudgeType.BREAK_SUGGESTION
        val last = dayRepository.lastPostedAt(today)
        if (!urgent && last != null && System.currentTimeMillis() - last < MIN_GAP_MILLIS) {
            return@withLock false
        }

        val blocks = scheduleRepository.blocksFor(today)
        val content = NudgeContent.build(context, top, blocks, prefs.use24HourTime)
        // Record it either way: a prompt whose block has since changed is simply dropped.
        dayRepository.markPosted(today, top.key)
        if (content == null) return@withLock false

        Notifier.ensureChannels(context)
        Notifier.postGuide(
            context = context,
            title = content.title,
            body = content.body,
            actions = content.actions,
            silent = !prefs.notificationSound,
            openTarget = content.openTarget,
        )
        true
    }

    private suspend fun nudgesFor(date: LocalDate, nowMinute: Int): List<Nudge> {
        val prefs = preferencesRepository.current()
        val blocks = scheduleRepository.blocksFor(date)
        val active = blocks.firstOrNull { it.status == BlockStatus.ACTIVE }
        val activeStart = active?.actualStart?.let { millis ->
            val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            if (time.toLocalDate() == date) time.hour * 60 + time.minute else null
        }
        return NudgePlanner.plan(
            state = NudgeState(
                blocks = blocks,
                dayState = dayRepository.state(date),
                checkInDone = scheduleRepository.checkIn(date) != null,
                activeStartMinute = activeStart,
                posted = dayRepository.postedKeys(date),
            ),
            settings = prefs.toNudgeSettings(date.toEpochDay()),
            nowMinute = nowMinute,
        )
    }

    private fun schedule(triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BlockAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Exact alarms need a user grant from Android 12. Without it a windowed alarm is close
        // enough for a prompt, and the fifteen-minute background check catches anything late.
        val canBeExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canBeExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, WINDOW_MILLIS, intent)
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

    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.canScheduleExactAlarms() else true
    }

    private companion object {
        const val REQUEST_CODE = 4201
        const val WINDOW_MILLIS = 5 * 60 * 1000L
        const val MIN_GAP_MILLIS = 4 * 60 * 1000L
    }
}
