package com.margin.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.StepperRow
import com.margin.app.ui.components.TimeField
import com.margin.app.ui.settings.SettingsViewModel
import com.margin.app.ui.theme.Space

/**
 * Deliberately short. The timetable is already loaded, so setup is four questions about how
 * the user wants their own time treated, and every one of them has a working default.
 */
@Composable
fun OnboardingScreen(
    viewModel: SettingsViewModel,
    timetableSummary: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.prefs
    var step by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.update { it.copy(notificationsEnabled = granted) }
            step += 1
        },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Space.xxxl))
            Text("Margin", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(Space.s))
            Text(
                text = "A planner that decides what you should be doing next, so you do not " +
                    "have to rebuild your day every morning.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xxxl))

            when (step) {
                0 -> {
                    MarginCard {
                        SectionHeader("Your timetable is already in")
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = timetableSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = "Every class is editable in the Week tab, and one-off changes " +
                                "like a cancelled period or a holiday take two taps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                1 -> {
                    MarginCard {
                        SectionHeader("When are you awake")
                        Spacer(Modifier.height(Space.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            TimeField(
                                minute = prefs.wakeMinute,
                                use24Hour = prefs.use24HourTime,
                                label = "Wake",
                                onChange = { m -> viewModel.update { it.copy(wakeMinute = m) } },
                            )
                            TimeField(
                                minute = prefs.sleepMinute,
                                use24Hour = prefs.use24HourTime,
                                label = "Sleep",
                                onChange = { m -> viewModel.update { it.copy(sleepMinute = m) } },
                            )
                        }
                        Spacer(Modifier.height(Space.m))
                        StepperRow(
                            label = "Travel home after college",
                            value = MarginTime.formatDuration(prefs.commuteMinutes),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(commuteMinutes = (it.commuteMinutes - 5).coerceAtLeast(0))
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(commuteMinutes = (it.commuteMinutes + 5).coerceAtMost(180))
                                }
                            },
                        )
                    }
                }

                2 -> {
                    MarginCard {
                        SectionHeader("What the planner protects")
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = "These are floors. Work is placed around them, never through them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.m))
                        StepperRow(
                            label = "Leisure every day",
                            value = MarginTime.formatDuration(prefs.minLeisureMinutes),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(
                                        minLeisureMinutes = (it.minLeisureMinutes - 15)
                                            .coerceAtLeast(0),
                                    )
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(
                                        minLeisureMinutes = (it.minLeisureMinutes + 15)
                                            .coerceAtMost(480),
                                    )
                                }
                            },
                        )
                        StepperRow(
                            label = "Build time on weekdays",
                            value = MarginTime.formatDuration(prefs.buildMinutesWeekday),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(
                                        buildMinutesWeekday = (it.buildMinutesWeekday - 15)
                                            .coerceAtLeast(0),
                                    )
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(
                                        buildMinutesWeekday = (it.buildMinutesWeekday + 15)
                                            .coerceAtMost(360),
                                    )
                                }
                            },
                        )
                        StepperRow(
                            label = "Daily work ceiling",
                            value = MarginTime.formatDuration(prefs.maxWorkMinutesPerDay),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(
                                        maxWorkMinutesPerDay = (it.maxWorkMinutesPerDay - 15)
                                            .coerceAtLeast(30),
                                    )
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(
                                        maxWorkMinutesPerDay = (it.maxWorkMinutesPerDay + 15)
                                            .coerceAtMost(720),
                                    )
                                }
                            },
                        )
                    }
                }

                else -> {
                    MarginCard {
                        SectionHeader("Reminders")
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = "Margin can tell you what is starting next and when a break " +
                                "is over. It will not send anything else.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xxxl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        viewModel.update { it.copy(onboardingComplete = true) }
                        onFinish()
                    },
                ) { Text("Skip setup") }

                Button(
                    onClick = {
                        if (step >= LAST_STEP) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.update { it.copy(onboardingComplete = true) }
                            onFinish()
                        } else {
                            step += 1
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (step >= LAST_STEP) "Start using Margin" else "Next")
                }
            }

            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

private const val LAST_STEP = 3
