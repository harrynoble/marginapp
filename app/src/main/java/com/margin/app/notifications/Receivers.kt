package com.margin.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.margin.app.MarginApplication
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.SkipResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun Context.container() = (applicationContext as? MarginApplication)?.container

/** Fires when a prompt is due: posts it from the day as it is now, then arms the next one. */
class BlockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.container() ?: return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Notifier.ensureChannels(context)
                runCatching { container.dayRollover.run() }
                runCatching { container.planningService.ensurePlan(LocalDate.now()) }
                container.alarmScheduler.fireDue()
                container.alarmScheduler.rearm()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Every choice a prompt offers, handled without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(EXTRA_BLOCK_ID, -1L)
        val value = intent.getIntExtra(EXTRA_VALUE, 0)
        val container = context.container() ?: return
        val action = intent.action ?: return
        val pending = goAsync()

        receiverScope.launch {
            try {
                val actions = container.scheduleActions
                val now = LocalDateTime.now()
                val today = now.toLocalDate()
                when (action) {
                    ACTION_START -> actions.start(blockId, now)
                    ACTION_COMPLETE -> actions.complete(blockId, now)
                    ACTION_SKIP -> actions.skip(blockId, SkipResolution.LATER_TODAY, "Skipped from a notification", now)
                    ACTION_SKIP_TODAY -> actions.skip(blockId, SkipResolution.DROP_TODAY, "Skipped for today", now)
                    ACTION_LATER -> {
                        val block = container.scheduleRepository.block(blockId)
                        if (block != null) {
                            val from = maxOf(block.start, MarginTime.nowMinute(now))
                            actions.reschedule(
                                blockId = blockId,
                                toDate = block.date,
                                toStart = (from + value.coerceAtLeast(5)).coerceAtMost(24 * 60 - 5),
                                reason = "Later, from a notification",
                                now = now,
                            )
                        }
                    }
                    ACTION_MOVE_NEXT -> actions.moveToNext(blockId, now)
                    ACTION_CONTINUE -> actions.continueSession(blockId, value.coerceAtLeast(5), now)
                    ACTION_BREAK -> actions.takeBreak(value.coerceIn(5, 120), now, BreakReason.SUGGESTED)
                    ACTION_DISMISS -> Unit
                    ACTION_BUILD_YES -> {
                        actions.setBuildDecision(today, Decision.ACCEPTED, now = now)
                        startSoon(container, blockId, now)
                    }
                    ACTION_BUILD_NO -> actions.setBuildDecision(today, Decision.DECLINED, now = now)
                    ACTION_LEARN_YES -> {
                        actions.setLearningDecision(today, Decision.ACCEPTED, now = now)
                        startSoon(container, blockId, now)
                    }
                    ACTION_LEARN_NO -> actions.setLearningDecision(today, Decision.DECLINED, now = now)
                }
                Notifier.cancel(context, Notifier.NOTIFICATION_GUIDE)
                container.alarmScheduler.rearm()
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * After "yes" to build or learning: if the session is about to begin anyway, start it.
     * Accepting replans the day, so the block is found again by kind rather than by id.
     */
    private suspend fun startSoon(container: com.margin.app.di.AppContainer, blockId: Long, now: LocalDateTime) {
        val original = container.scheduleRepository.block(blockId)
        val nowMinute = MarginTime.nowMinute(now)
        val target = container.scheduleRepository.blocksFor(now.toLocalDate())
            .filter { it.status == BlockStatus.PLANNED && (original == null || it.type == original.type) }
            .filter { it.type.isWork && it.start <= nowMinute + START_WINDOW && it.end > nowMinute }
            .minByOrNull { it.start }
        if (target != null) container.scheduleActions.start(target.id, now)
    }

    companion object {
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_VALUE = "value"
        const val ACTION_START = "com.margin.app.action.START"
        const val ACTION_COMPLETE = "com.margin.app.action.COMPLETE"
        const val ACTION_SKIP = "com.margin.app.action.SKIP"
        const val ACTION_SKIP_TODAY = "com.margin.app.action.SKIP_TODAY"
        const val ACTION_LATER = "com.margin.app.action.LATER"
        const val ACTION_MOVE_NEXT = "com.margin.app.action.MOVE_NEXT"
        const val ACTION_CONTINUE = "com.margin.app.action.CONTINUE"
        const val ACTION_BREAK = "com.margin.app.action.BREAK"
        const val ACTION_DISMISS = "com.margin.app.action.DISMISS"
        const val ACTION_BUILD_YES = "com.margin.app.action.BUILD_YES"
        const val ACTION_BUILD_NO = "com.margin.app.action.BUILD_NO"
        const val ACTION_LEARN_YES = "com.margin.app.action.LEARN_YES"
        const val ACTION_LEARN_NO = "com.margin.app.action.LEARN_NO"
        private const val START_WINDOW = 20
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
                runCatching { container.dayRollover.run() }
                runCatching { container.planningService.ensurePlan(LocalDate.now()) }
                container.alarmScheduler.rearm()
                MarginWorkers.enqueueAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
