package com.margin.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.margin.app.di.AppContainer
import com.margin.app.notifications.MarginWorkers
import com.margin.app.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class MarginApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannels(this)

        // First run seeds the timetable and builds today. Wrapped so that a failure here
        // degrades to an empty day rather than a crash on launch.
        scope.launch {
            runCatching {
                container.seedService.seedIfNeeded()
                container.planningService.ensurePlan(LocalDate.now())
                container.planningService.ensurePlan(LocalDate.now().plusDays(1))
                container.alarmScheduler.rearm()
                MarginWorkers.enqueueAll(this@MarginApplication)
            }.onFailure { Log.w(TAG, "Startup planning failed", it) }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private companion object {
        const val TAG = "Margin"
    }
}
