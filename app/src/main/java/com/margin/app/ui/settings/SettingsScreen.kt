package com.margin.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.data.prefs.AiConnectionState
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
        body = "Past and planned days, finished sessions, skips and breaks are deleted, and what " +
            "Margin learned from them is forgotten. Tasks, exams, the timetable and your settings stay.",
        action = "Delete",
    ),
}

private val ThemeOptions = listOf("system", "light", "dark")

/**
 * iOS Settings: inset grouped sections, a coloured tile on every row, values on the right and
 * a sentence under each group saying what it changes. Every edit replans today straight away.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exported by viewModel.exported.collectAsStateWithLifecycle()
    val prefs = state.prefs
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val set: ((UserPreferences) -> UserPreferences) -> Unit = { transform -> viewModel.update(transform) }
    val use24 = prefs.use24HourTime

    // A finished export goes straight to the share sheet; the file never leaves the app otherwise.
    LaunchedEffect(exported) {
        val file = exported ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Export Margin data"))
        }
        viewModel.consumeExport()
    }

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
                        IosSwitch(checked = use24, onCheckedChange = { v -> viewModel.updateDisplay { it.copy(use24HourTime = v) } })
                    },
                )
            }
        }

        item(key = "study") {
            GroupedSection(
                header = "Study",
                footer = "Every subject, theory and lab, gets a session when it has gone untouched this long.",
            ) {
                GroupedRow(
                    title = "Prefer studying after",
                    leading = { IconTile(Icons.Rounded.PlayCircle, accents.blue) },
                    trailing = {
                        IosSwitch(
                            checked = prefs.hasStudyPreference,
                            onCheckedChange = { v -> set { it.copy(preferredStudyStart = if (v) 16 * 60 else -1) } },
                        )
                    },
                )
                if (prefs.hasStudyPreference) {
                    RowSeparator(inset = IconInset)
                    TimeRow(
                        title = "From",
                        minute = prefs.preferredStudyStart,
                        use24Hour = use24,
                        onChange = { m -> set { it.copy(preferredStudyStart = m) } },
                        leading = { IconTile(Icons.Rounded.Schedule, accents.gray) },
                    )
                }
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Session length",
                    minutes = prefs.studySessionMinutes,
                    onChange = { m -> set { it.copy(studySessionMinutes = m) } },
                    min = 20,
                    max = 120,
                    leading = { IconTile(Icons.Rounded.AutoStories, accents.indigo) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Cover every subject within",
                    minutes = prefs.coverageGapDays,
                    onChange = { d -> set { it.copy(coverageGapDays = d) } },
                    step = 1,
                    min = 1,
                    max = 14,
                    format = { if (it == 1) "1 day" else "$it days" },
                    leading = { IconTile(Icons.Rounded.CalendarMonth, accents.teal) },
                )
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Most carried into a day",
                    minutes = prefs.carryForwardCap,
                    onChange = { m -> set { it.copy(carryForwardCap = m) } },
                    step = 15,
                    max = 4 * 60,
                    format = { if (it == 0) "Nothing" else com.margin.app.core.MarginTime.formatDuration(it) },
                    leading = { IconTile(Icons.Rounded.SwapHoriz, accents.purple) },
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

        item(key = "build") {
            GroupedSection(
                header = "Build",
                footer = "Project time, offered once academics are handled. Saying not today is always fine.",
            ) {
                GroupedRow(
                    title = "Offer build time",
                    leading = { IconTile(Icons.Rounded.Construction, accents.orange) },
                    trailing = { IosSwitch(checked = prefs.buildEnabled, onCheckedChange = { v -> set { it.copy(buildEnabled = v) } }) },
                )
                if (prefs.buildEnabled) {
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "On weekdays",
                        minutes = prefs.buildMinutesWeekday,
                        onChange = { m -> set { it.copy(buildMinutesWeekday = m) } },
                        step = 15,
                        max = 6 * 60,
                        leading = { IconTile(Icons.Rounded.Handyman, accents.orange) },
                    )
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "At weekends",
                        minutes = prefs.buildMinutesWeekend,
                        onChange = { m -> set { it.copy(buildMinutesWeekend = m) } },
                        step = 15,
                        max = 8 * 60,
                        leading = { IconTile(Icons.Rounded.Handyman, accents.purple) },
                    )
                }
            }
        }

        item(key = "learning") {
            GroupedSection(
                header = "Learning",
                footer = "Time for a new skill, apart from college work. Paused automatically when exams are close.",
            ) {
                GroupedRow(
                    title = "Offer learning time",
                    leading = { IconTile(Icons.Rounded.Lightbulb, accents.teal) },
                    trailing = { IosSwitch(checked = prefs.learningEnabled, onCheckedChange = { v -> set { it.copy(learningEnabled = v) } }) },
                )
                if (prefs.learningEnabled) {
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "On weekdays",
                        minutes = prefs.learningMinutesWeekday,
                        onChange = { m -> set { it.copy(learningMinutesWeekday = m) } },
                        step = 15,
                        max = 3 * 60,
                        leading = { IconTile(Icons.Rounded.Lightbulb, accents.teal) },
                    )
                    RowSeparator(inset = IconInset)
                    DurationRow(
                        title = "At weekends",
                        minutes = prefs.learningMinutesWeekend,
                        onChange = { m -> set { it.copy(learningMinutesWeekend = m) } },
                        step = 15,
                        max = 4 * 60,
                        leading = { IconTile(Icons.Rounded.Lightbulb, accents.mint) },
                    )
                }
            }
        }

        item(key = "protected") {
            GroupedSection(
                header = "Leisure",
                footer = "A floor, not a leftover. Work is placed around it, never through it.",
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
            }
        }

        item(key = "notifications") {
            NotificationSettings(
                prefs = prefs,
                use24 = use24,
                exact = remember(prefs) { viewModel.canScheduleExact() },
                set = set,
                onOpenAlarmSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                            )
                        }
                    }
                },
            )
        }

        item(key = "appearance") {
            GroupedSection(header = "Appearance") {
                Column {
                    GroupedRow(title = "Theme", leading = { IconTile(Icons.Rounded.Contrast, accents.gray) })
                    OptionChips(
                        options = ThemeOptions,
                        selected = prefs.themeMode,
                        label = { it.replaceFirstChar { c -> c.uppercase() } },
                        onSelect = { mode -> viewModel.updateDisplay { it.copy(themeMode = mode) } },
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
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Exams",
                    leading = { IconTile(Icons.Rounded.EventBusy, accents.red) },
                    showChevron = true,
                    onClick = onOpenExams,
                )
            }
        }

        item(key = "data") {
            GroupedSection(
                header = "Data",
                footer = "Everything stays on this device. Only the assistant sends anything out, and " +
                    "only what you type into it. Exports never include your API key.",
            ) {
                GroupedRow(
                    title = "Rebuild Today",
                    leading = { IconTile(Icons.Rounded.Refresh, accents.blue) },
                    onClick = viewModel::rebuildToday,
                )
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Export My Data",
                    leading = { IconTile(Icons.Rounded.FileUpload, accents.green) },
                    onClick = viewModel::export,
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
 * Notifications are part of the product, so each kind can be turned off on its own. Classes
 * are never announced.
 */
@Composable
private fun NotificationSettings(
    prefs: UserPreferences,
    use24: Boolean,
    exact: Boolean,
    set: ((UserPreferences) -> UserPreferences) -> Unit,
    onOpenAlarmSettings: () -> Unit,
) {
    val accents = MarginTheme.accents
    GroupedSection(
        header = "Notifications",
        footer = "At most one Margin notification is on screen at a time, and nothing arrives between bedtime and waking.",
    ) {
        GroupedRow(
            title = "Notifications",
            leading = { IconTile(Icons.Rounded.Notifications, accents.red) },
            trailing = {
                IosSwitch(checked = prefs.notificationsEnabled, onCheckedChange = { v -> set { it.copy(notificationsEnabled = v) } })
            },
        )
        if (prefs.notificationsEnabled) {
            RowSeparator(inset = IconInset)
            ToggleRow("Time to study", Icons.Rounded.PlayCircle, accents.blue, prefs.nudgeStudyStart) { v -> set { it.copy(nudgeStudyStart = v) } }
            RowSeparator(inset = IconInset)
            ToggleRow("Between sessions", Icons.Rounded.SwapHoriz, accents.teal, prefs.nudgeTransitions) { v -> set { it.copy(nudgeTransitions = v) } }
            RowSeparator(inset = IconInset)
            ToggleRow("Break suggestions", Icons.Rounded.Coffee, accents.brown, prefs.nudgeBreaks) { v -> set { it.copy(nudgeBreaks = v) } }
            RowSeparator(inset = IconInset)
            ToggleRow("When a break ends", Icons.Rounded.FreeBreakfast, accents.brown, prefs.notifyBreakEnd) { v -> set { it.copy(notifyBreakEnd = v) } }
            RowSeparator(inset = IconInset)
            ToggleRow("Missed sessions", Icons.Rounded.EventBusy, accents.orange, prefs.nudgeMissed) { v -> set { it.copy(nudgeMissed = v) } }
            if (prefs.nudgeMissed) {
                RowSeparator(inset = IconInset)
                DurationRow(
                    title = "Ask after",
                    minutes = prefs.missedGraceMinutes,
                    onChange = { m -> set { it.copy(missedGraceMinutes = m) } },
                    min = 5,
                    max = 60,
                    leading = { IconTile(Icons.Rounded.Timer, accents.gray) },
                )
            }
            RowSeparator(inset = IconInset)
            ToggleRow("Build and learning offers", Icons.Rounded.Construction, accents.orange, prefs.nudgeBuildLearning) { v ->
                set { it.copy(nudgeBuildLearning = v) }
            }
            RowSeparator(inset = IconInset)
            ToggleRow("When the plan changes", Icons.Rounded.Sync, accents.blue, prefs.notifyPlanChanges) { v -> set { it.copy(notifyPlanChanges = v) } }
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
            ToggleRow("Sound", Icons.AutoMirrored.Rounded.VolumeUp, accents.pink, prefs.notificationSound) { v -> set { it.copy(notificationSound = v) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowSeparator(inset = IconInset)
                GroupedRow(
                    title = "Precise timing",
                    subtitle = if (exact) null else "Off: reminders may arrive a few minutes late",
                    value = if (exact) "On" else "Allow",
                    leading = { IconTile(Icons.Rounded.Alarm, accents.red) },
                    showChevron = !exact,
                    onClick = if (exact) null else onOpenAlarmSettings,
                )
            }
        }
        RowSeparator(inset = IconInset)
        GroupedRow(
            title = "Evening review",
            leading = { IconTile(Icons.Rounded.Checklist, accents.purple) },
            trailing = {
                IosSwitch(checked = prefs.checkInEnabled, onCheckedChange = { v -> set { it.copy(checkInEnabled = v) } })
            },
        )
        if (prefs.checkInEnabled) {
            RowSeparator(inset = IconInset)
            TimeRow(
                title = "Review at",
                minute = prefs.checkInMinute,
                use24Hour = use24,
                onChange = { m -> set { it.copy(checkInMinute = m) } },
                leading = { IconTile(Icons.Rounded.Schedule, accents.gray) },
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    GroupedRow(
        title = title,
        leading = { IconTile(icon, tint) },
        trailing = { IosSwitch(checked = checked, onCheckedChange = onChange) },
    )
}

/**
 * The optional assistant. Planning never depends on it: it only turns typed sentences into
 * checked, structured changes. The key is stored on this device and shown masked.
 */
@Composable
private fun AssistantSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    val colors = MarginTheme.colors
    val ai = state.ai
    val testing by viewModel.connectionTesting.collectAsStateWithLifecycle()
    var modelDraft by remember(ai.provider) { mutableStateOf(ai.model) }
    var keyDraft by remember { mutableStateOf("") }

    GroupedSection(
        header = "Assistant",
        footer = "Margin plans on its own. The assistant only turns what you type into checked " +
            "changes, so everything works with it off. Simple requests never leave the device.",
    ) {
        OptionChips(
            options = listOf(AiProviderId.NONE, AiProviderId.ANTHROPIC, AiProviderId.OPENAI),
            selected = ai.provider,
            label = { it.label },
            onSelect = { viewModel.setProvider(it) },
        )
    }

    if (ai.provider != AiProviderId.NONE) {
        val label = ai.provider.label
        // Connected only ever means a real, authenticated request succeeded.
        val (status, statusColor) = when {
            testing -> "Testing…" to colors.secondaryLabel
            ai.apiKey.isBlank() -> "Not connected" to colors.secondaryLabel
            ai.connection == AiConnectionState.CONNECTED -> "Connected" to colors.positive
            ai.connection == AiConnectionState.FAILED -> "Not connected" to colors.destructive
            else -> "Not tested" to colors.warning
        }
        GroupedSection(
            header = "Connection",
            footer = when {
                testing -> "Making a real request to $label to check the key and the model."
                ai.connectionMessage.isNotBlank() -> ai.connectionMessage
                ai.apiKey.isBlank() -> "The key is encrypted on this device and only ever sent to $label."
                else -> "This key has not been tested yet."
            },
        ) {
            GroupedRow(
                title = label,
                trailing = { Text(text = status, style = AppleType.body, color = statusColor) },
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
                    title = if (testing) "Testing…" else "Save and Test",
                    titleColor = if (keyDraft.isNotBlank() && !testing) colors.tint else colors.tertiaryLabel,
                    onClick = {
                        if (keyDraft.isNotBlank() && !testing) {
                            viewModel.saveKey(keyDraft)
                            keyDraft = ""
                        }
                    },
                )
            } else {
                GroupedRow(title = "API key", value = ai.maskedKey)
                RowSeparator()
                GroupedRow(
                    title = if (testing) "Testing…" else "Test Connection",
                    titleColor = if (testing) colors.secondaryLabel else colors.tint,
                    onClick = if (testing) null else ({ viewModel.testConnection() }),
                )
                RowSeparator()
                GroupedRow(title = "Remove Key", titleColor = colors.destructive, onClick = viewModel::clearAiKey)
            }
        }

        GroupedSection(
            header = "Model",
            footer = "The connection test checks that this model is available to your key.",
        ) {
            FormTextField(
                value = modelDraft,
                onValueChange = { value ->
                    modelDraft = value
                    viewModel.updateAi { it.copy(model = value.trim()) }
                },
                placeholder = ai.provider.defaultModel,
            )
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
