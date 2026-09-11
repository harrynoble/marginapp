package com.margin.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.margin.app.domain.model.BlockStatus
import com.margin.app.notifications.FocusSessionService
import com.margin.app.notifications.Notifier
import com.margin.app.ui.MarginApp
import com.margin.app.ui.theme.MarginTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge is enforced from Android 15; calling it explicitly keeps the behaviour
        // identical on older releases rather than depending on the target SDK.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MarginApplication).container
        Notifier.ensureChannels(this)

        // The schedule changes constantly. Rather than remembering to re-arm the reminder
        // after every action, watch the day and re-arm whenever it actually changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.scheduleRepository
                    .observeDay(LocalDate.now())
                    .debounce(REARM_DEBOUNCE_MILLIS)
                    .collect { container.alarmScheduler.rearm() }
            }
        }

        // Keep the running session visible in the shade, and stop it the moment it is not.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.scheduleRepository
                    .observeActiveBlock()
                    .distinctUntilChanged()
                    .collect { block ->
                        if (block != null && block.status == BlockStatus.ACTIVE) {
                            FocusSessionService.start(this@MainActivity, block.id)
                        } else {
                            FocusSessionService.stop(this@MainActivity)
                        }
                    }
            }
        }

        setContent {
            val dark = isSystemInDarkTheme()
            // System bar icons follow the app theme, including when it flips while running;
            // otherwise the clock goes dark-on-dark the moment the system switches to night.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            MarginTheme(darkTheme = dark) {
                MarginApp(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as MarginApplication).container
        lifecycleScope.launch {
            // Coming back after midnight, or after a day away, should not show a stale plan.
            runCatching { container.planningService.ensurePlan(LocalDate.now()) }
        }
    }

    private companion object {
        const val REARM_DEBOUNCE_MILLIS = 1_500L

        // The scrims enableEdgeToEdge uses by default, kept for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
