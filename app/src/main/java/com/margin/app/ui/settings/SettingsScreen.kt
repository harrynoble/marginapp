package com.margin.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.ui.components.DurationRow
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.OptionChips
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.components.TimeRow
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/** Separators start where row titles do, past the icon tile. */
private val IconInset = Space.l + 30.dp + Space.m

private enum class Confirm(val title: String, val body: String, val action: String) {
    RESTORE_TIMETABLE(
        title = "Restore the original timetable?",
        body = "Any classes you edited go back to how they shipped, and today is rebuilt.",
        action = "Restore",
    ),
    CLEAR_HISTORY(
        title = "Delete schedule history?",
        body = "Past and planned days are cleared and today is rebuilt. Tasks, the timetable and " +
            "your settings stay.",
        action = "Delete",
    ),
}

/**
 * iOS Settings: inset grouped sections, a coloured tile on every row, values on the right and
 * a sentence under each group saying what it changes. Every edit replans today straight away.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.prefs
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val set: ((UserPreferences) -> UserPreferences) -> Unit = { transform -> viewModel.update(transform) }
    val use24 = prefs.use24HourTime

    LargeTitleScreen(
        title = "Settings",
        backdrop = backdrop,
        bottomClearance = Space.xxxl,
        modifier = modifier,
        navigation = {
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                onClick = onBack,
                iconSize = 30.dp,
            )
        },
    ) {
        state.message?.let { message ->
            item(key = "message") {
                GroupedSection(modifier = Modifier.padding(top = Space.s)) {
                    GroupedRow(
                        title = message,
                        leading = {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.positive,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailing = { TextAction(text = "OK", emphasized = true, onClick = viewModel::dismissMessage) },
                    )
                }
            }
        }

        item(key = "day") {
            GroupedSection(header = "Your day") {
                TimeRow(
                    title = "Wake up",
                    minute = prefs.wakeMinute,
                    use24Hour = use24,
                    onChange = { m -> set { it.copy(wakeMinute = m) } },
                    leading = { IconTile(Icons.Rounded.WbSunny, accents.orange) },
                )
                RowSeparator(inset = IconInset)
                TimeRow(
                    title = "Bedtime",
                    minute = prefs.sleepMinute,
                    use24Hour = use24,
                    onChange = { m -> set { it.copy(sleepMinute = m) } },
                    leading = { IconTile(Icons.Rounded.Bedtime, accents.indigo) },
                )
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "24-Hour Time",
                    leading = { IconTile(Icons.Rounded.Schedule, accents.gray) },
                    trailing = {
                        IosSwitch(checked = use24, onCheckedChange = { v -> set { it.copy(use24HourTime = v) } })
                    },
                )
            }
        }

        item(key = "transitions") {
            GroupedSection(
                header = "Transitions",
                footer = "Time kept clear around college, so the day is never back to back.",
            ) {
                DurationRow(
                    title = "Travel home",
                    minutes = prefs.commuteMinutes,
                    onChange = { m -> set { it.copy(commuteMinutes = m) } },
                    max = 180,
                    leading = { IconTile(Icons.Rounded.DirectionsBus, accents.teal) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Settle in",
                    minutes = prefs.decompressionMinutes,
                    onChange = { m -> set { it.copy(decompressionMinutes = m) } },
                    max = 120,
                    leading = { IconTile(Icons.Rounded.SelfImprovement, accents.mint) },
                )
            }
        }

        item(key = "work") {
            GroupedSection(header = "Work and breaks") {
                DurationRow(
                    title = "Daily limit",
                    minutes = prefs.maxWorkMinutesPerDay,
                    onChange = { m -> set { it.copy(maxWorkMinutesPerDay = m) } },
                    step = 15,
                    min = 30,
                    max = 12 * 60,
                    leading = { IconTile(Icons.Rounded.HourglassTop, accents.blue) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Break after",
                    minutes = prefs.continuousWorkBeforeBreak,
                    onChange = { m -> set { it.copy(continuousWorkBeforeBreak = m) } },
                    min = 20,
                    max = 180,
                    leading = { IconTile(Icons.Rounded.Timer, accents.cyan) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Break length",
                    minutes = prefs.shortBreakMinutes,
                    onChange = { m -> set { it.copy(shortBreakMinutes = m) } },
                    min = 5,
                    max = 60,
                    leading = { IconTile(Icons.Rounded.Coffee, accents.brown) },
                )
            }
        }

        item(key = "protected") {
            GroupedSection(
                header = "Protected time",
                footer = "These are floors. Work is placed around them, never through them.",
            ) {
                DurationRow(
                    title = "Daily leisure",
                    minutes = prefs.minLeisureMinutes,
                    onChange = { m -> set { it.copy(minLeisureMinutes = m) } },
                    step = 15,
                    max = 8 * 60,
                    leading = { IconTile(Icons.Rounded.Weekend, accents.green) },
                )
                RowSeparator(inset = IconInset)
                TimeRow(
                    title = "Leisure from",
                    minute = prefs.leisureWindowStart,
                    use24Hour = use24,
                    onChange = { m -> set { it.copy(leisureWindowStart = m) } },
                    leading = { IconTile(Icons.Rounded.WbTwilight, accents.orange) },
                )
                RowSeparator(inset = IconInset)
                TimeRow(
                    title = "Leisure until",
                    minute = prefs.leisureWindowEnd,
                    use24Hour = use24,
                    onChange = { m -> set { it.copy(leisureWindowEnd = m) } },
                    leading = { IconTile(Icons.Rounded.NightsStay, accents.indigo) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Build on weekdays",
                    minutes = prefs.buildMinutesWeekday,
                    onChange = { m -> set { it.copy(buildMinutesWeekday = m) } },
                    step = 15,
                    max = 6 * 60,
                    leading = { IconTile(Icons.Rounded.Construction, accents.orange) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Build at weekends",
                    minutes = prefs.buildMinutesWeekend,
                    onChange = { m -> set { it.copy(buildMinutesWeekend = m) } },
                    step = 15,
                    max = 8 * 60,
                    leading = { IconTile(Icons.Rounded.Handyman, accents.purple) },
                )
            }
        }

        item(key = "review") {
            GroupedSection(
                header = "Revision",
                footer = "Short reviews after the classes you had, sized by how long each class ran.",
            ) {
                GroupedRow(
                    title = "Suggest revision",
                    leading = { IconTile(Icons.AutoMirrored.Rounded.MenuBook, accents.indigo) },
                    trailing = {
                        IosSwitch(checked = prefs.reviewEnabled, onCheckedChange = { v -> set { it.copy(reviewEnabled = v) } })
                    },
                )
                if (prefs.reviewEnabled) {
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "Per hour of class",
                        minutes = prefs.reviewMinutesPerTeachingHour,
                        onChange = { m -> set { it.copy(reviewMinutesPerTeachingHour = m) } },
                        min = 5,
                        max = 60,
                        leading = { IconTile(Icons.Rounded.Timelapse, accents.teal) },
                    )
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "Most in a day",
                        minutes = prefs.maxReviewMinutesPerDay,
                        onChange = { m -> set { it.copy(maxReviewMinutesPerDay = m) } },
                        step = 15,
                        min = 15,
                        max = 5 * 60,
                        leading = { IconTile(Icons.Rounded.DataUsage, accents.gray) },
                    )
                }
            }
        }

        item(key = "notifications") {
            GroupedSection(
                header = "Notifications",
                footer = "Classes are never announced. Margin only tells you about your own work, " +
                    "breaks and changes to the plan.",
            ) {
                GroupedRow(
                    title = "Reminders",
                    leading = { IconTile(Icons.Rounded.Notifications, accents.red) },
                    trailing = {
                        IosSwitch(
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { v -> set { it.copy(notificationsEnabled = v) } },
                        )
                    },
                )
                if (prefs.notificationsEnabled) {
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "Remind me",
                        minutes = prefs.notifyLeadMinutes,
                        onChange = { m -> set { it.copy(notifyLeadMinutes = m) } },
                        max = 30,
                        format = { if (it == 0) "At start" else "$it min before" },
                        leading = { IconTile(Icons.Rounded.Alarm, accents.orange) },
                    )
                    RowSeparator(inset = IconInset)
                    GroupedRow(
                        title = "When a break ends",
                        leading = { IconTile(Icons.Rounded.FreeBreakfast, accents.brown) },
                        trailing = {
                            IosSwitch(
                                checked = prefs.notifyBreakEnd,
                                onCheckedChange = { v -> set { it.copy(notifyBreakEnd = v) } },
                            )
                        },
                    )
                    RowSeparator(inset = IconInset)
                    GroupedRow(
                        title = "When the plan changes",
                        leading = { IconTile(Icons.Rounded.Sync, accents.blue) },
                        trailing = {
                            IosSwitch(
                                checked = prefs.notifyPlanChanges,
                                onCheckedChange = { v -> set { it.copy(notifyPlanChanges = v) } },
                            )
                        },
                    )
                }
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Evening check-in",
                    leading = { IconTile(Icons.Rounded.Checklist, accents.purple) },
                    trailing = {
                        IosSwitch(
                            checked = prefs.checkInEnabled,
                            onCheckedChange = { v -> set { it.copy(checkInEnabled = v) } },
                        )
                    },
                )
                if (prefs.checkInEnabled) {
                    RowSeparator(inset = IconInset)
                    TimeRow(
                        title = "Check in at",
                        minute = prefs.checkInMinute,
                        use24Hour = use24,
                        onChange = { m -> set { it.copy(checkInMinute = m) } },
                        leading = { IconTile(Icons.Rounded.Schedule, accents.gray) },
                    )
                }
            }
        }

        item(key = "assistant") {
            AssistantSettings(state = state, viewModel = viewModel)
        }

        item(key = "college") {
            GroupedSection(
                header = "College",
                footer = "Your timetable is built in. Open it only if something changes.",
            ) {
                GroupedRow(
                    title = "Timetable",
                    leading = { IconTile(Icons.Rounded.School, accents.indigo) },
                    showChevron = true,
                    onClick = onOpenTimetable,
                )
            }
        }

        item(key = "data") {
            GroupedSection(
                header = "Data",
                footer = "Everything stays on this device. Only the assistant sends anything out, and " +
                    "only what you type into it.",
            ) {
                GroupedRow(
                    title = "Rebuild Today",
                    leading = { IconTile(Icons.Rounded.Refresh, accents.blue) },
                    onClick = viewModel::rebuildToday,
                )
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Restore Original Timetable",
                    leading = { IconTile(Icons.Rounded.Restore, accents.gray) },
                    onClick = { confirm = Confirm.RESTORE_TIMETABLE },
                )
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Delete Schedule History",
                    titleColor = colors.destructive,
                    leading = { IconTile(Icons.Rounded.DeleteForever, accents.red) },
                    onClick = { confirm = Confirm.CLEAR_HISTORY },
                )
            }
        }

        item(key = "about") {
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xxl, bottom = Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Margin $version".trim(),
                    style = AppleType.footnoteEmphasized,
                    color = colors.secondaryLabel,
                )
                Text(
                    text = "Set in Inter, under the SIL Open Font License.",
                    style = AppleType.caption1,
                    color = colors.tertiaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(pending.title, style = AppleType.headline) },
            text = { Text(pending.body, style = AppleType.subheadline, color = colors.secondaryLabel) },
            confirmButton = {
                TextAction(text = pending.action, emphasized = true, color = colors.destructive, onClick = {
                    confirm = null
                    when (pending) {
                        Confirm.RESTORE_TIMETABLE -> viewModel.restoreSeededTimetable()
                        Confirm.CLEAR_HISTORY -> viewModel.clearHistory()
                    }
                })
            },
            dismissButton = { TextAction(text = "Cancel", onClick = { confirm = null }) },
            containerColor = colors.surface,
            shape = MarginShape.card,
        )
    }
}

/**
 * The optional assistant. Planning never depends on it: it only turns typed sentences into
 * checked, structured changes. The key is stored on this device and shown masked.
 */
@Composable
private fun AssistantSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val colors = MarginTheme.colors
    val ai = state.ai
    var modelDraft by remember(ai.provider) { mutableStateOf(ai.model) }
    var keyDraft by remember { mutableStateOf("") }

    GroupedSection(
        header = "Assistant",
        footer = "Margin plans on its own. The assistant only turns what you type into checked " +
            "changes, so everything works with it off.",
    ) {
        OptionChips(
            options = listOf(AiProviderId.NONE, AiProviderId.ANTHROPIC, AiProviderId.OPENAI),
            selected = ai.provider,
            label = { it.label },
            onSelect = { viewModel.setProvider(it) },
        )
    }

    if (ai.provider != AiProviderId.NONE) {
        GroupedSection(header = "Connection") {
            FormTextField(
                value = modelDraft,
                onValueChange = { value ->
                    modelDraft = value
                    viewModel.updateAi { it.copy(model = value.trim()) }
                },
                placeholder = ai.provider.defaultModel,
            )
            RowSeparator()
            if (ai.apiKey.isBlank()) {
                FormTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    placeholder = "API key",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                RowSeparator()
                GroupedRow(
                    title = "Save Key",
                    titleColor = if (keyDraft.isNotBlank()) colors.tint else colors.tertiaryLabel,
                    onClick = {
                        if (keyDraft.isNotBlank()) {
                            viewModel.updateAi { it.copy(apiKey = keyDraft.trim(), enabled = true) }
                            keyDraft = ""
                        }
                    },
                )
            } else {
                GroupedRow(title = "API key", value = ai.maskedKey)
                RowSeparator()
                GroupedRow(title = "Remove Key", titleColor = colors.destructive, onClick = viewModel::clearAiKey)
            }
        }

        GroupedSection(
            modifier = Modifier.padding(top = Space.xl),
            footer = if (ai.shareScheduleDetail) {
                "Requests include today's block titles and times. No history and no notes."
            } else {
                "Requests include only the current time and your free windows."
            },
        ) {
            GroupedRow(
                title = "Share the shape of my day",
                trailing = {
                    IosSwitch(
                        checked = ai.shareScheduleDetail,
                        onCheckedChange = { v -> viewModel.updateAi { it.copy(shareScheduleDetail = v) } },
                    )
                },
            )
        }
    }
}
