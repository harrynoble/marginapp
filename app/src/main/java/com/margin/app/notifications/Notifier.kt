package com.margin.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.margin.app.MainActivity
import com.margin.app.R
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock

/**
 * All notification text and channels in one place.
 *
 * The rule the app follows: a notification either tells the user what to do next or explains
 * a change they did not make. Nothing else is ever posted.
 */
object Notifier {

    const val CHANNEL_SCHEDULE = "schedule"
    const val CHANNEL_FOCUS = "focus"
    const val CHANNEL_PLANNING = "planning"

    const val NOTIFICATION_UP_NEXT = 1001
    const val NOTIFICATION_BREAK_OVER = 1002
    const val NOTIFICATION_PLAN_CHANGED = 1003
    const val NOTIFICATION_CHECK_IN = 1004
    const val NOTIFICATION_FOCUS = 1005

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCHEDULE,
                context.getString(R.string.notification_channel_schedule),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_schedule_desc)
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOCUS,
                context.getString(R.string.notification_channel_focus),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_focus_desc)
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLANNING,
                context.getString(R.string.notification_channel_planning),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_planning_desc)
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /** "Up next: Data Structures review, 35 min" with Start, Snooze and Skip actions. */
    fun postUpNext(context: Context, block: ScheduleBlock, use24Hour: Boolean, leadMinutes: Int) {
        if (!canPost(context)) return

        val title = when {
            leadMinutes <= 0 -> "Starting now: " + block.title
            block.type == BlockType.BREAK -> "Break in " + leadMinutes + " min"
            else -> "Up next: " + block.title
        }
        val body = buildString {
            append(MarginTime.formatTime(block.start, use24Hour))
            append(" to ")
            append(MarginTime.formatTime(block.end, use24Hour))
            append(" · ")
            append(MarginTime.formatDuration(block.duration))
            block.subtitle?.let {
                append(" · ")
                append(it)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (block.type.isActionable) {
            builder.addAction(
                0,
                "Start",
                action(context, NotificationActionReceiver.ACTION_START, block.id),
            )
            builder.addAction(
                0,
                "Snooze 10",
                action(context, NotificationActionReceiver.ACTION_SNOOZE, block.id),
            )
            builder.addAction(
                0,
                "Skip",
                action(context, NotificationActionReceiver.ACTION_SKIP, block.id),
            )
        }

        post(context, NOTIFICATION_UP_NEXT, builder.build())
    }

    fun postBreakOver(context: Context, next: ScheduleBlock?) {
        if (!canPost(context)) return
        val body = next?.let { "Ready for " + it.title + "?" }
            ?: "Nothing scheduled next. The rest of the time is yours."
        val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your break is over")
            .setContentText(body)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
        if (next != null && next.type.isActionable) {
            builder.addAction(
                0,
                "Start",
                action(context, NotificationActionReceiver.ACTION_START, next.id),
            )
        }
        post(context, NOTIFICATION_BREAK_OVER, builder.build())
    }

    fun postPlanChanged(context: Context, summary: String, detail: String?) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_PLANNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Schedule updated")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail ?: summary))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        post(context, NOTIFICATION_PLAN_CHANGED, builder.build())
    }

    fun postCheckIn(context: Context, summary: String) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_PLANNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How did today go?")
            .setContentText(summary)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        post(context, NOTIFICATION_CHECK_IN, builder.build())
    }

    fun focusNotification(context: Context, block: ScheduleBlock, remainingMinutes: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(block.title)
            .setContentText(MarginTime.formatDuration(remainingMinutes) + " left")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                "Finish",
                action(context, NotificationActionReceiver.ACTION_COMPLETE, block.id),
            )
            .build()

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, actionName: String, blockId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (actionName + blockId).hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = actionName
                putExtra(NotificationActionReceiver.EXTRA_BLOCK_ID, blockId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
