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
import com.margin.app.domain.model.ScheduleBlock

/**
 * All notification plumbing in one place.
 *
 * The rule the app follows: a notification either tells the user what to do next or explains
 * a change they did not make. Guidance shares one slot, so a new prompt replaces the last one
 * instead of piling up in the shade.
 */
object Notifier {

    const val CHANNEL_SCHEDULE = "schedule"
    const val CHANNEL_FOCUS = "focus"
    const val CHANNEL_PLANNING = "planning"

    /** The single slot every guidance prompt uses. */
    const val NOTIFICATION_GUIDE = 1001
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

    data class Action(val label: String, val intent: PendingIntent)

    /** Posts a guidance prompt into the shared slot, replacing whatever was there. */
    fun postGuide(
        context: Context,
        title: String,
        body: String,
        actions: List<Action>,
        silent: Boolean,
        openTarget: String? = null,
    ) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent(context, openTarget))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(silent)
            .setOnlyAlertOnce(true)
        // Android shows at most three buttons; the rest of the choices live in the app.
        actions.take(3).forEach { builder.addAction(0, it.label, it.intent) }
        post(context, NOTIFICATION_GUIDE, builder.build())
    }

    fun postPlanChanged(context: Context, summary: String, detail: String?) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_PLANNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your schedule changed")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail ?: summary))
            .setContentIntent(openIntent(context, null))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        post(context, NOTIFICATION_PLAN_CHANGED, builder.build())
    }

    fun focusNotification(context: Context, block: ScheduleBlock, remainingMinutes: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(block.title)
            .setContentText(MarginTime.formatDuration(remainingMinutes) + " left")
            .setContentIntent(openIntent(context, null))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                "Finish",
                actionIntent(context, NotificationActionReceiver.ACTION_COMPLETE, block.id),
            )
            .build()

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        // Declined notifications are a choice, not an error: post nothing and carry on.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Opens the app, optionally straight into a particular sheet such as "I'm out". */
    fun openIntent(context: Context, target: String?): PendingIntent =
        PendingIntent.getActivity(
            context,
            (target ?: "open").hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (target != null) putExtra(LaunchRequests.EXTRA_TARGET, target)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun actionIntent(context: Context, actionName: String, blockId: Long, extra: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (actionName + blockId + ":" + extra).hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = actionName
                putExtra(NotificationActionReceiver.EXTRA_BLOCK_ID, blockId)
                putExtra(NotificationActionReceiver.EXTRA_VALUE, extra)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
