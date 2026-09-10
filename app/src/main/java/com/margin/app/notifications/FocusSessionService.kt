package com.margin.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.margin.app.MarginApplication
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the running session visible and its countdown accurate while the app is in the
 * background. It stops itself the moment the block is no longer active, so it never becomes
 * a notification the user has to dismiss.
 */
class FocusSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val blockId = intent?.getLongExtra(EXTRA_BLOCK_ID, -1L) ?: -1L
        if (blockId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val container = (application as? MarginApplication)?.container
        if (container == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        Notifier.ensureChannels(this)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val block = container.scheduleRepository.block(blockId)
                if (block == null || block.status != BlockStatus.ACTIVE) {
                    stopForegroundCompat()
                    stopSelf()
                    return@launch
                }
                val remaining = (block.end - MarginTime.nowMinute()).coerceAtLeast(0)
                val notification = Notifier.focusNotification(
                    this@FocusSessionService,
                    block,
                    remaining,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this@FocusSessionService,
                        Notifier.NOTIFICATION_FOCUS,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } else {
                    startForeground(Notifier.NOTIFICATION_FOCUS, notification)
                }
                delay(TICK_MILLIS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val EXTRA_BLOCK_ID = "block_id"
        private const val TICK_MILLIS = 30_000L

        fun start(context: Context, blockId: Long) {
            if (!Notifier.canPost(context)) return
            val intent = Intent(context, FocusSessionService::class.java)
                .putExtra(EXTRA_BLOCK_ID, blockId)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusSessionService::class.java)) }
        }
    }
}
