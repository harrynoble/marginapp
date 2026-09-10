package com.margin.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.margin.app.MarginApplication
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.SkipResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun Context.container() = (applicationContext as? MarginApplication)?.container

/** Fires when the next block is due, posts the reminder, then arms the following one. */
class BlockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(EXTRA_BLOCK_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_DATE, -1L)
        val container = context.container() ?: return
        val pending = goAsync()

        receiverScope.launch {
            try {
                Notifier.ensureChannels(context)
                val prefs = container.preferencesRepository.current()
                val block = container.scheduleRepository.block(blockId)

                if (block != null && block.status == BlockStatus.PLANNED) {
                    val previous = container.scheduleRepository
                        .blocksFor(if (epochDay > 0) LocalDate.ofEpochDay(epochDay) else LocalDate.now())
                        .filter { it.end == block.start }

                    if (previous.any { it.type == BlockType.BREAK } && prefs.notifyBreakEnd) {
                        Notifier.postBreakOver(context, block)
                    } else {
                        Notifier.postUpNext(
                            context = context,
                            block = block,
                            use24Hour = prefs.use24HourTime,
                            leadMinutes = (block.start - MarginTime.nowMinute()).coerceAtLeast(0),
                        )
                    }
                }

                container.alarmScheduler.rearm()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_DATE = "date"
    }
}

/** Start, Complete, Snooze and Skip straight from the notification shade. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(EXTRA_BLOCK_ID, -1L)
        if (blockId <= 0) return
        val container = context.container() ?: return
        val action = intent.action ?: return
        val pending = goAsync()

        receiverScope.launch {
            try {
                when (action) {
                    ACTION_START -> container.scheduleActions.start(blockId)
                    ACTION_COMPLETE -> container.scheduleActions.complete(blockId)
                    ACTION_SKIP -> container.scheduleActions.skip(
                        blockId = blockId,
                        resolution = SkipResolution.LATER_TODAY,
                        reason = "Skipped from a notification",
                    )
                    ACTION_SNOOZE -> {
                        val block = container.scheduleRepository.block(blockId)
                        if (block != null) {
                            container.scheduleActions.reschedule(
                                blockId = blockId,
                                toDate = block.date,
                                toStart = (block.start + SNOOZE_MINUTES).coerceAtMost(24 * 60 - 5),
                                reason = "Snoozed",
                                now = LocalDateTime.now(),
                            )
                        }
                    }
                }
                Notifier.cancel(context, Notifier.NOTIFICATION_UP_NEXT)
                Notifier.cancel(context, Notifier.NOTIFICATION_BREAK_OVER)
                container.alarmScheduler.rearm()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_BLOCK_ID = "block_id"
        const val ACTION_START = "com.margin.app.action.START"
        const val ACTION_COMPLETE = "com.margin.app.action.COMPLETE"
        const val ACTION_SKIP = "com.margin.app.action.SKIP"
        const val ACTION_SNOOZE = "com.margin.app.action.SNOOZE"
        private const val SNOOZE_MINUTES = 10
    }
}

/** Alarms do not survive a reboot, a clock change or an app update. This puts them back. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.container() ?: return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Notifier.ensureChannels(context)
                container.planningService.ensurePlan(LocalDate.now())
                container.alarmScheduler.rearm()
                MarginWorkers.enqueueAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
