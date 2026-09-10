package com.margin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.AiProviderId
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.StepperRow
import com.margin.app.ui.components.TimeField
import com.margin.app.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.prefs

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                bottom = Space.xxxl * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            state.message?.let { message ->
                item(key = "message") {
                    MarginCard(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = false,
                        onClick = viewModel::dismissMessage,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            item(key = "day") {
                MarginCard {
                    SectionHeader("Your day")
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        TimeField(
                            minute = prefs.wakeMinute,
                            use24Hour = prefs.use24HourTime,
                            label = "Wake",
                            onChange = { minute -> viewModel.update { it.copy(wakeMinute = minute) } },
                        )
                        TimeField(
                            minute = prefs.sleepMinute,
                            use24Hour = prefs.use24HourTime,
                            label = "Sleep",
                            onChange = { minute -> viewModel.update { it.copy(sleepMinute = minute) } },
                        )
                    }
                    Spacer(Modifier.height(Space.s))
                    ToggleRow(
                        label = "24 hour clock",
                        checked = prefs.use24HourTime,
                        onChange = { value -> viewModel.update { it.copy(use24HourTime = value) } },
                    )
                }
            }

            item(key = "transitions") {
                MarginCard {
                    SectionHeader("Transitions")
                    Spacer(Modifier.height(Space.s))
                    Text(
                        text = "Time reserved around commitments, so the plan is not back to back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
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
                    StepperRow(
                        label = "Settle in before work",
                        value = MarginTime.formatDuration(prefs.decompressionMinutes),
                        onDecrease = {
                            viewModel.update {
                                it.copy(
                                    decompressionMinutes = (it.decompressionMinutes - 5)
                                        .coerceAtLeast(0),
                                )
                            }
                        },
                        onIncrease = {
                            viewModel.update {
                                it.copy(
                                    decompressionMinutes = (it.decompressionMinutes + 5)
                                        .coerceAtMost(120),
                                )
                            }
                        },
                    )
                }
            }

            item(key = "work") {
                MarginCard {
                    SectionHeader("Work and breaks")
                    Spacer(Modifier.height(Space.s))
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
                                        .coerceAtMost(12 * 60),
                                )
                            }
                        },
                    )
                    StepperRow(
                        label = "Break after working",
                        value = MarginTime.formatDuration(prefs.continuousWorkBeforeBreak),
                        onDecrease = {
                            viewModel.update {
                                it.copy(
                                    continuousWorkBeforeBreak = (it.continuousWorkBeforeBreak - 5)
                                        .coerceAtLeast(20),
                                )
                            }
                        },
                        onIncrease = {
                            viewModel.update {
                                it.copy(
                                    continuousWorkBeforeBreak = (it.continuousWorkBeforeBreak + 5)
                                        .coerceAtMost(180),
                                )
                            }
                        },
                    )
                    StepperRow(
                        label = "Break length",
                        value = MarginTime.formatDuration(prefs.shortBreakMinutes),
                        onDecrease = {
                            viewModel.update {
                                it.copy(
                                    shortBreakMinutes = (it.shortBreakMinutes - 5).coerceAtLeast(5),
                                )
                            }
                        },
                        onIncrease = {
                            viewModel.update {
                                it.copy(
                                    shortBreakMinutes = (it.shortBreakMinutes + 5).coerceAtMost(60),
                                )
                            }
                        },
                    )
                }
            }

            item(key = "protected") {
                MarginCard {
                    SectionHeader("Protected time")
                    Spacer(Modifier.height(Space.s))
                    Text(
                        text = "These are floors. The planner reserves them before it places any work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
                    StepperRow(
                        label = "Leisure every day",
                        value = MarginTime.formatDuration(prefs.minLeisureMinutes),
                        onDecrease = {
                            viewModel.update {
                                it.copy(
                                    minLeisureMinutes = (it.minLeisureMinutes - 15).coerceAtLeast(0),
                                )
                            }
                        },
                        onIncrease = {
                            viewModel.update {
                                it.copy(
                                    minLeisureMinutes = (it.minLeisureMinutes + 15)
                                        .coerceAtMost(8 * 60),
                                )
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        TimeField(
                            minute = prefs.leisureWindowStart,
                            use24Hour = prefs.use24HourTime,
                            label = "From",
                            onChange = { m -> viewModel.update { it.copy(leisureWindowStart = m) } },
                        )
                        TimeField(
                            minute = prefs.leisureWindowEnd,
                            use24Hour = prefs.use24HourTime,
                            label = "To",
                            onChange = { m -> viewModel.update { it.copy(leisureWindowEnd = m) } },
                        )
                    }
                    Spacer(Modifier.height(Space.m))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Space.s))
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
                                        .coerceAtMost(6 * 60),
                                )
                            }
                        },
                    )
                    StepperRow(
                        label = "Build time at weekends",
                        value = MarginTime.formatDuration(prefs.buildMinutesWeekend),
                        onDecrease = {
                            viewModel.update {
                                it.copy(
                                    buildMinutesWeekend = (it.buildMinutesWeekend - 15)
                                        .coerceAtLeast(0),
                                )
                            }
                        },
                        onIncrease = {
                            viewModel.update {
                                it.copy(
                                    buildMinutesWeekend = (it.buildMinutesWeekend + 15)
                                        .coerceAtMost(8 * 60),
                                )
                            }
                        },
                    )
                }
            }

            item(key = "review") {
                MarginCard {
                    SectionHeader("Post-class review")
                    Spacer(Modifier.height(Space.s))
                    ToggleRow(
                        label = "Suggest revision for the classes you had",
                        checked = prefs.reviewEnabled,
                        onChange = { value -> viewModel.update { it.copy(reviewEnabled = value) } },
                    )
                    if (prefs.reviewEnabled) {
                        StepperRow(
                            label = "Per teaching hour",
                            value = MarginTime.formatDuration(prefs.reviewMinutesPerTeachingHour),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(
                                        reviewMinutesPerTeachingHour =
                                        (it.reviewMinutesPerTeachingHour - 5).coerceAtLeast(5),
                                    )
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(
                                        reviewMinutesPerTeachingHour =
                                        (it.reviewMinutesPerTeachingHour + 5).coerceAtMost(60),
                                    )
                                }
                            },
                        )
                        StepperRow(
                            label = "Cap per day",
                            value = MarginTime.formatDuration(prefs.maxReviewMinutesPerDay),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(
                                        maxReviewMinutesPerDay = (it.maxReviewMinutesPerDay - 15)
                                            .coerceAtLeast(15),
                                    )
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(
                                        maxReviewMinutesPerDay = (it.maxReviewMinutesPerDay + 15)
                                            .coerceAtMost(5 * 60),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item(key = "notifications") {
                MarginCard {
                    SectionHeader("Notifications")
                    Spacer(Modifier.height(Space.s))
                    ToggleRow(
                        label = "Remind me what is next",
                        checked = prefs.notificationsEnabled,
                        onChange = { value ->
                            viewModel.update { it.copy(notificationsEnabled = value) }
                        },
                    )
                    if (prefs.notificationsEnabled) {
                        StepperRow(
                            label = "Lead time",
                            value = MarginTime.formatDuration(prefs.notifyLeadMinutes),
                            onDecrease = {
                                viewModel.update {
                                    it.copy(notifyLeadMinutes = (it.notifyLeadMinutes - 1).coerceAtLeast(0))
                                }
                            },
                            onIncrease = {
                                viewModel.update {
                                    it.copy(notifyLeadMinutes = (it.notifyLeadMinutes + 1).coerceAtMost(60))
                                }
                            },
                        )
                        ToggleRow(
                            label = "Tell me when a break is over",
                            checked = prefs.notifyBreakEnd,
                            onChange = { value ->
                                viewModel.update { it.copy(notifyBreakEnd = value) }
                            },
                        )
                        ToggleRow(
                            label = "Tell me when the plan changes",
                            checked = prefs.notifyPlanChanges,
                            onChange = { value ->
                                viewModel.update { it.copy(notifyPlanChanges = value) }
                            },
                        )
                    }
                    Spacer(Modifier.height(Space.s))
                    ToggleRow(
                        label = "Evening check-in",
                        checked = prefs.checkInEnabled,
                        onChange = { value -> viewModel.update { it.copy(checkInEnabled = value) } },
                    )
                    if (prefs.checkInEnabled) {
                        TimeField(
                            minute = prefs.checkInMinute,
                            use24Hour = prefs.use24HourTime,
                            label = "At",
                            onChange = { m -> viewModel.update { it.copy(checkInMinute = m) } },
                        )
                    }
                }
            }

            item(key = "ai") {
                AiSettingsCard(viewModel = viewModel, state = state)
            }

            item(key = "data") {
                MarginCard {
                    SectionHeader("Timetable and data")
                    Spacer(Modifier.height(Space.s))
                    Text(
                        text = "Everything stays on this device. Only the assistant sends anything " +
                            "out, and only when you type into it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
                    TextButton(onClick = viewModel::rebuildToday) { Text("Rebuild today") }
                    TextButton(onClick = viewModel::restoreSeededTimetable) {
                        Text("Restore the shipped timetable")
                    }
                    TextButton(onClick = viewModel::clearHistory) {
                        Text("Delete all schedule history", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSettingsCard(viewModel: SettingsViewModel, state: SettingsUiState) {
    var keyDraft by remember(state.ai.apiKey) { mutableStateOf("") }

    MarginCard {
        SectionHeader("Assistant")
        Spacer(Modifier.height(Space.s))
        Text(
            text = "Margin plans on its own. The assistant only turns what you type into " +
                "structured changes, so the app works with this switched off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.m))

        LabeledField("Provider") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                AiProviderId.entries.forEach { provider ->
                    FilterChip(
                        selected = state.ai.provider == provider,
                        onClick = { viewModel.setProvider(provider) },
                        label = { Text(provider.label) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        if (state.ai.provider != AiProviderId.NONE) {
            Spacer(Modifier.height(Space.l))
            LabeledField("Model") {
                MarginTextField(
                    value = state.ai.model,
                    onValueChange = { value -> viewModel.updateAi { it.copy(model = value) } },
                    placeholder = state.ai.provider.defaultModel,
                )
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("API key") {
                if (state.ai.apiKey.isBlank()) {
                    Column {
                        MarginTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            placeholder = "Paste your key",
                        )
                        Spacer(Modifier.height(Space.s))
                        TextButton(
                            onClick = {
                                viewModel.updateAi {
                                    it.copy(apiKey = keyDraft.trim(), enabled = true)
                                }
                                keyDraft = ""
                            },
                            enabled = keyDraft.isNotBlank(),
                        ) { Text("Save key") }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = state.ai.maskedKey,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = viewModel::clearAiKey) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.s))
            ToggleRow(
                label = "Send the shape of my day for better answers",
                checked = state.ai.shareScheduleDetail,
                onChange = { value ->
                    viewModel.updateAi { it.copy(shareScheduleDetail = value) }
                },
            )
            Text(
                text = if (state.ai.shareScheduleDetail) {
                    "Requests include today block titles and times. No history, no notes."
                } else {
                    "Requests include only the current time and free windows."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
