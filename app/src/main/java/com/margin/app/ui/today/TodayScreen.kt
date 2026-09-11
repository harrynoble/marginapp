package com.margin.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.planner.ExamPlanner
import com.margin.app.notifications.LaunchRequests
import com.margin.app.ui.components.BlockActionCallbacks
import com.margin.app.ui.components.BlockActionsSheet
import com.margin.app.ui.components.CapsuleChip
import com.margin.app.ui.components.CollegeRow
import com.margin.app.ui.components.DaySky
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.PrimaryButton
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SecondaryButton
import com.margin.app.ui.components.SectionTitle
import com.margin.app.ui.components.StatTile
import com.margin.app.ui.components.TimelineRow
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TodaySheet { LIGHTEN, OUT, BUILD, LEARN }

private enum class QuickChoice { NONE, BREAK, ENERGY }

/**
 * The screen the app opens to. In order: what is happening now, the few controls that reshape
 * the day, anything that needs a decision, and how the rest of the day looks. College appears
 * as a single line; the timetable itself never does.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onOpenFocus: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val pending by LaunchRequests.pending.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val backdrop = rememberGlassBackdrop()

    var sheetBlock by remember { mutableStateOf<ScheduleBlock?>(null) }
    var sheet by remember { mutableStateOf<TodaySheet?>(null) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(QuickChoice.NONE) }

    // A notification that needs an answer opens straight into the question it asked.
    LaunchedEffect(pending) {
        when (pending) {
            LaunchRequests.TARGET_OUT -> sheet = TodaySheet.OUT
            LaunchRequests.TARGET_BUILD -> sheet = TodaySheet.BUILD
            LaunchRequests.TARGET_LEARN -> sheet = TodaySheet.LEARN
            LaunchRequests.TARGET_CHECK_IN -> onOpenCheckIn()
            else -> return@LaunchedEffect
        }
        LaunchRequests.consume()
    }

    val callbacks = remember(viewModel) {
        BlockActionCallbacks(
            onStart = { viewModel.start(it) },
            onComplete = { viewModel.complete(it) },
            onSkip = { id, resolution -> viewModel.skip(id, resolution) },
            onExtend = { id, minutes -> viewModel.extend(id, minutes) },
            onMove = { id, minute -> viewModel.move(id, minute) },
            onMoveToTomorrow = { viewModel.moveToTomorrow(it) },
            onTogglePin = { id, locked -> viewModel.togglePin(id, locked) },
            onOpenTask = onOpenTask,
        )
    }

    val view = remember(state) { TodayView.from(state) }

    val nowActions = NowActions(
        onStart = { block ->
            viewModel.start(block.id)
            if (block.type.isWork) onOpenFocus(block.id)
        },
        onFinish = { viewModel.complete(it.id) },
        onPause = { viewModel.pause(it.id) },
        onSkip = { sheetBlock = it },
        onMore = { sheetBlock = it },
        onRebuild = { viewModel.replan() },
        onOut = { sheet = TodaySheet.OUT },
        onLater = { viewModel.later(it.id) },
        onSkipToday = { viewModel.skip(it.id, SkipResolution.DROP_TODAY, "Skipped today") },
        onContinue = { viewModel.continueSession(it.id) },
        onMoveNext = { block ->
            val next = state.nextWork
            viewModel.moveToNext(block.id)
            if (next != null) onOpenFocus(next.id)
        },
    )

    LargeTitleScreen(
        title = "Today",
        eyebrow = state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        backdrop = backdrop,
        modifier = modifier,
        background = { DaySky(minuteOfDay = state.nowMinute) },
        actions = {
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
            )
        },
    ) {
        // Until the day has loaded, say nothing rather than claim the day is empty.
        if (state.loading) {
            item(key = "loading") { Spacer(Modifier.height(LoadingHeight)) }
            return@LargeTitleScreen
        }

        item(key = "now") {
            NowCard(
                subject = view.now,
                nowMinute = state.nowMinute,
                use24Hour = state.use24Hour,
                nextLabel = view.nextLabel(state.use24Hour),
                actions = nowActions,
                modifier = Modifier.padding(top = Space.xs),
            )
        }

        item(key = "quick") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter, vertical = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (choosing) {
                    QuickChoice.BREAK -> {
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            CapsuleChip(
                                text = "$minutes min",
                                selected = false,
                                onClick = {
                                    choosing = QuickChoice.NONE
                                    viewModel.takeBreak(minutes)
                                },
                            )
                        }
                        CapsuleChip(
                            text = "Meal",
                            selected = false,
                            onClick = {
                                choosing = QuickChoice.NONE
                                viewModel.takeBreak(MEAL_MINUTES, meal = true)
                            },
                        )
                        CapsuleChip(text = "Cancel", selected = false, onClick = { choosing = QuickChoice.NONE })
                    }

                    QuickChoice.ENERGY -> {
                        listOf(
                            EnergyMode.LIGHT to "Low",
                            EnergyMode.NORMAL to "Normal",
                            EnergyMode.FOCUSED to "High",
                        ).forEach { (mode, label) ->
                            CapsuleChip(
                                text = label,
                                selected = state.energyMode == mode,
                                onClick = {
                                    choosing = QuickChoice.NONE
                                    if (state.energyMode != mode) viewModel.setEnergyMode(mode)
                                },
                            )
                        }
                        CapsuleChip(text = "Cancel", selected = false, onClick = { choosing = QuickChoice.NONE })
                    }

                    QuickChoice.NONE -> {
                        if (state.isLightDay) {
                            QuickChip("Light day · Undo", Icons.Rounded.Spa, selected = true) { viewModel.clearLighten() }
                        } else {
                            QuickChip("Lighten today", Icons.Rounded.Spa) { sheet = TodaySheet.LIGHTEN }
                        }
                        QuickChip("Take a break", Icons.Rounded.Coffee) { choosing = QuickChoice.BREAK }
                        QuickChip(
                            text = "Energy: " + when (state.energyMode) {
                                EnergyMode.LIGHT -> "Low"
                                EnergyMode.NORMAL -> "Normal"
                                EnergyMode.FOCUSED -> "High"
                            },
                            icon = Icons.Rounded.BatteryChargingFull,
                        ) { choosing = QuickChoice.ENERGY }
                        QuickChip("I'm out", Icons.AutoMirrored.Rounded.DirectionsWalk) { sheet = TodaySheet.OUT }
                    }
                }
            }
        }

        banner?.let { change ->
            item(key = "banner") {
                ChangeCard(banner = change, onDismiss = viewModel::dismissBanner)
            }
        }

        if (state.isMinimumDay && !state.dayOver) {
            item(key = "minimum") {
                NoticeCard(
                    eyebrow = "MINIMUM DAY",
                    eyebrowColor = accents.teal,
                    title = "Only what matters most",
                    body = "Today holds the essentials and nothing else. Anything left is carried, not lost.",
                    primary = null,
                    secondary = "Plan normally" to { viewModel.setMinimumDay(false) },
                )
            }
        }

        val pressure = ExamPlanner.pressure(state.date, state.exams)
        pressure.nearest?.let { exam ->
            item(key = "exam") {
                val days = pressure.nearestDays ?: 0
                NoticeCard(
                    eyebrow = pressure.intensity.label.uppercase(),
                    eyebrowColor = if (pressure.active) accents.red else accents.orange,
                    title = exam.title + when (days) {
                        0 -> " is today"
                        1 -> " is tomorrow"
                        else -> " in $days days"
                    },
                    body = if (pressure.upcoming.size > 1) {
                        "${pressure.upcoming.size} exams in the next three weeks. Every subject still gets its turn."
                    } else {
                        "Revision is spread across the days left, alongside everything else."
                    },
                    onClick = onOpenExams,
                )
            }
        }

        state.buildOffer?.let { offer ->
            item(key = "build-offer") {
                NoticeCard(
                    eyebrow = "BUILD",
                    eyebrowColor = accents.orange,
                    title = "Work on ${offer.subtitle ?: "your project"} today?",
                    body = MarginTime.formatDuration(offer.duration) + " is free at " +
                        MarginTime.formatTime(offer.start, state.use24Hour) + ".",
                    primary = "Yes" to { viewModel.buildDecision(Decision.ACCEPTED, offer.projectId, offer.duration) },
                    secondary = "Not today" to { viewModel.buildDecision(Decision.DECLINED) },
                    tertiary = "Change" to { sheet = TodaySheet.BUILD },
                )
            }
        }

        state.learningOffer?.let { offer ->
            item(key = "learn-offer") {
                NoticeCard(
                    eyebrow = "LEARNING",
                    eyebrowColor = accents.teal,
                    title = "Learn something new today?",
                    body = (offer.subtitle?.let { "$it, " } ?: "") + MarginTime.formatDuration(offer.duration) +
                        " at " + MarginTime.formatTime(offer.start, state.use24Hour) + ".",
                    primary = "Yes" to { viewModel.learningDecision(Decision.ACCEPTED, offer.learningGoalId, offer.duration) },
                    secondary = "Not today" to { viewModel.learningDecision(Decision.DECLINED) },
                    tertiary = "Change" to { sheet = TodaySheet.LEARN },
                )
            }
        }

        if (state.checkInDue) {
            item(key = "checkin") {
                GroupedSection(modifier = Modifier.padding(top = Space.s)) {
                    GroupedRow(
                        title = "Close out the day",
                        subtitle = "A short review. Unfinished work is carried, not lost.",
                        leading = { IconTile(Icons.Rounded.Bedtime, accents.indigo) },
                        showChevron = true,
                        onClick = onOpenCheckIn,
                    )
                }
            }
        }

        view.sections.forEach { (part, items) ->
            item(key = "title-${part.name}") { SectionTitle(part.label) }
            item(key = "list-${part.name}") {
                GroupedSection {
                    items.forEachIndexed { index, item ->
                        if (index > 0) RowSeparator(inset = TimelineSeparatorInset)
                        when (item) {
                            is TimelineItem.College -> CollegeRow(
                                college = item,
                                use24Hour = state.use24Hour,
                                nowMinute = state.nowMinute,
                            )
                            is TimelineItem.Single -> TimelineRow(
                                block = item.block,
                                use24Hour = state.use24Hour,
                                onClick = if (item.block.type == BlockType.FREE) null else ({ sheetBlock = item.block }),
                            )
                        }
                    }
                }
            }
        }

        if (view.sections.isEmpty() && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    title = when {
                        state.dayOver -> "That's the day"
                        state.academicsDone -> "Free tonight"
                        else -> "Nothing else today"
                    },
                    body = when {
                        state.dayOver && state.missedToday > 0 ->
                            "What you didn't get to is carried to tomorrow, within a sensible limit."
                        state.dayOver -> "Rest well. Tomorrow is already planned."
                        state.academicsDone -> "Everything academic is done. The rest of the day is yours."
                        else -> "The rest of the day is yours. Add something with the plus button if you want."
                    },
                )
            }
        }

        if (state.notes.isNotEmpty() && !state.dayOver) {
            item(key = "notes") {
                Column(modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.m)) {
                    state.notes.take(MAX_NOTES).forEach { note ->
                        Text(
                            text = note,
                            style = AppleType.footnote,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (view.earlier.isNotEmpty()) {
            item(key = "earlier-title") {
                SectionTitle(
                    text = "Earlier",
                    trailing = if (showEarlier) "Hide" else "Show",
                    onTrailing = { showEarlier = !showEarlier },
                )
            }
            item(key = "earlier-list") {
                GroupedSection {
                    if (showEarlier) {
                        view.earlier.forEachIndexed { index, item ->
                            if (index > 0) RowSeparator(inset = TimelineSeparatorInset)
                            when (item) {
                                is TimelineItem.College -> CollegeRow(item, state.use24Hour, state.nowMinute)
                                is TimelineItem.Single -> TimelineRow(
                                    block = item.block,
                                    use24Hour = state.use24Hour,
                                    onClick = if (item.block.isMissedWork()) ({ sheetBlock = item.block }) else null,
                                )
                            }
                        }
                    } else {
                        GroupedRow(
                            title = buildString {
                                append("${view.earlierDone} finished")
                                if (view.earlierSkipped > 0) append(" · ${view.earlierSkipped} skipped")
                                if (view.earlierMissed > 0) append(" · ${view.earlierMissed} missed")
                            },
                            onClick = { showEarlier = true },
                            showChevron = true,
                        )
                    }
                }
            }
        }

        item(key = "summary") {
            SectionTitle("So far")
            Row(
                modifier = Modifier.padding(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    value = MarginTime.formatDurationShort(state.completedWorkMinutes),
                    label = "Done",
                    accent = colors.positive,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = MarginTime.formatDurationShort(
                        (state.plannedWorkMinutes - state.completedWorkMinutes).coerceAtLeast(0),
                    ),
                    label = "Planned",
                    accent = colors.tint,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = MarginTime.formatDurationShort(state.freeRemainingMinutes),
                    label = "Free",
                    accent = accents.green,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    sheetBlock?.let { block ->
        BlockActionsSheet(
            block = block,
            use24Hour = state.use24Hour,
            callbacks = callbacks,
            onDismiss = { sheetBlock = null },
        )
    }

    when (sheet) {
        TodaySheet.LIGHTEN -> LightenSheet(
            options = state.lightenOptions,
            hasBuild = state.upcoming.any { it.type == BlockType.BUILD } || state.buildOffer != null,
            hasLearning = state.upcoming.any { it.type == BlockType.LEARN } || state.learningOffer != null,
            lowEnergy = state.energyMode == EnergyMode.LIGHT,
            onDismiss = { sheet = null },
            onApply = { essentials, priorities, dropBuild, dropLearning, lowEnergy ->
                sheet = null
                viewModel.lighten(essentials, priorities, dropBuild, dropLearning, lowEnergy)
            },
        )

        TodaySheet.OUT -> OutSheet(
            nowMinute = state.nowMinute,
            sleepMinute = state.sleepMinute,
            use24Hour = state.use24Hour,
            onDismiss = { sheet = null },
            onChoose = { back ->
                sheet = null
                viewModel.goOut(back)
            },
        )

        TodaySheet.BUILD -> {
            val offer = state.buildOffer ?: state.upcoming.firstOrNull { it.type == BlockType.BUILD }
            OfferSheet(
                title = "Build Time",
                question = "Which project, and for how long?",
                options = state.projects.map { it.id to it.name },
                initialId = state.dayState.buildProjectId ?: offer?.projectId,
                initialMinutes = state.dayState.buildMinutes ?: offer?.duration ?: DEFAULT_OFFER_MINUTES,
                emptyHint = "No projects yet. Add one in Tasks.",
                acceptLabel = "Plan it",
                onDismiss = { sheet = null },
                onAccept = { id, minutes ->
                    sheet = null
                    viewModel.buildDecision(Decision.ACCEPTED, id, minutes)
                },
                onDecline = {
                    sheet = null
                    viewModel.buildDecision(Decision.DECLINED)
                },
            )
        }

        TodaySheet.LEARN -> {
            val offer = state.learningOffer ?: state.upcoming.firstOrNull { it.type == BlockType.LEARN }
            OfferSheet(
                title = "Learning",
                question = "What would you like to learn, and for how long?",
                options = state.goals.map { it.id to it.name },
                initialId = state.dayState.learningGoalId ?: offer?.learningGoalId,
                initialMinutes = state.dayState.learningMinutes ?: offer?.duration ?: DEFAULT_OFFER_MINUTES,
                emptyHint = "No learning goals yet. Add one in Tasks.",
                acceptLabel = "Plan it",
                onDismiss = { sheet = null },
                onAccept = { id, minutes ->
                    sheet = null
                    viewModel.learningDecision(Decision.ACCEPTED, id, minutes)
                },
                onDecline = {
                    sheet = null
                    viewModel.learningDecision(Decision.DECLINED)
                },
            )
        }

        null -> Unit
    }
}

private const val MEAL_MINUTES = 40
private val LoadingHeight = 360.dp
private const val DEFAULT_OFFER_MINUTES = 60
private const val MAX_NOTES = 3

/** Separators in the timeline start where the title does, past the time and the rail. */
private val TimelineSeparatorInset = Space.l + 76.dp + 4.dp + Space.m

private fun ScheduleBlock.isMissedWork() =
    type.isWork && (status == BlockStatus.MISSED || status == BlockStatus.SKIPPED)

@Composable
private fun QuickChip(text: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MarginTheme.colors
    CapsuleChip(
        text = text,
        selected = selected,
        onClick = onClick,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.onTint else colors.label,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/**
 * Something that needs a glance or a decision: an offer, exam mode, a minimum day. Plain
 * words, at most three answers, and never more than one card of each kind.
 */
@Composable
private fun NoticeCard(
    eyebrow: String,
    eyebrowColor: Color,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    tertiary: Pair<String, () -> Unit>? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    Column(
        modifier = Modifier
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .fillMaxWidth()
            .clip(MarginShape.card)
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.l),
    ) {
        Text(text = eyebrow, style = AppleType.footnoteEmphasized, color = eyebrowColor)
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = AppleType.headline, color = colors.label)
        Spacer(Modifier.height(2.dp))
        Text(text = body, style = AppleType.subheadline, color = colors.secondaryLabel)
        if (primary != null || secondary != null || tertiary != null) {
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                primary?.let { (label, action) ->
                    PrimaryButton(text = label, onClick = action, height = 40.dp, modifier = Modifier.weight(1f))
                }
                secondary?.let { (label, action) ->
                    SecondaryButton(
                        text = label,
                        onClick = action,
                        height = 40.dp,
                        modifier = if (primary == null) Modifier.weight(1f) else Modifier,
                    )
                }
                tertiary?.let { (label, action) ->
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = label,
                        style = AppleType.subheadline,
                        color = colors.tint,
                        modifier = Modifier
                            .clip(MarginShape.capsule)
                            .clickable(onClick = action)
                            .padding(horizontal = Space.s, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** A plan change explained in plain words, then dismissed. */
@Composable
private fun ChangeCard(banner: ChangeBanner, onDismiss: () -> Unit) {
    val colors = MarginTheme.colors
    Column(
        modifier = Modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .clip(MarginShape.card)
            .background(colors.surface)
            .padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Schedule updated",
                style = AppleType.headline,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = colors.tertiaryLabel,
                modifier = Modifier
                    .size(28.dp)
                    .clip(MarginShape.capsule)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(Space.xs))
        banner.lines.forEach { line ->
            Text(text = line, style = AppleType.subheadline, color = colors.secondaryLabel)
        }
        banner.protectedNote?.let {
            Spacer(Modifier.height(Space.s))
            Text(text = it, style = AppleType.footnoteEmphasized, color = colors.positive)
        }
    }
}

/** The Today screen, arranged for display. */
private class TodayView(
    val now: NowSubject,
    val next: TimelineItem?,
    val sections: List<Pair<Timeline.Part, List<TimelineItem>>>,
    val earlier: List<TimelineItem>,
    val earlierDone: Int,
    val earlierSkipped: Int,
    val earlierMissed: Int,
) {
    fun nextLabel(use24Hour: Boolean): String? = when (val n = next) {
        null -> null
        is TimelineItem.College -> "College at " + MarginTime.formatTime(n.start, use24Hour)
        is TimelineItem.Single -> n.block.title + " at " + MarginTime.formatTime(n.block.start, use24Hour)
    }

    companion object {
        fun from(state: TodayUiState): TodayView {
            val nowMinute = state.nowMinute
            val live = Timeline.collapse(listOfNotNull(state.current) + state.upcoming)

            val nowItem = live.firstOrNull { item ->
                nowMinute >= item.start && nowMinute < item.end && when (item) {
                    is TimelineItem.College -> true
                    is TimelineItem.Single -> item.block.id == state.current?.id
                }
            } ?: live.firstOrNull { item ->
                item is TimelineItem.Single && item.block.id == state.current?.id
            }

            val rest = live.filter { it != nowItem }
            val next = rest.firstOrNull {
                it !is TimelineItem.Single || it.block.type != BlockType.FREE
            }

            val now: NowSubject = when (nowItem) {
                is TimelineItem.College -> NowSubject.AtCollege(nowItem)
                is TimelineItem.Single -> {
                    val block = nowItem.block
                    if (block.type.isActionable) {
                        NowSubject.Work(
                            block = block,
                            overdueMinutes = state.overdue?.takeIf { it.id == block.id }?.let { nowMinute - it.start },
                            overrun = state.overrun && state.current?.id == block.id,
                            nextTitle = state.nextWork?.title,
                        )
                    } else {
                        NowSubject.Fixed(block)
                    }
                }
                null -> NowSubject.Open(
                    outsideHours = state.outsideWakingHours,
                    wakeMinute = state.wakeMinute,
                    freeUntil = next?.start,
                )
            }

            val sections = rest
                .groupBy { Timeline.partOf(it.start) }
                .toList()
                .sortedBy { it.first.ordinal }

            // Rescheduled blocks live on in their new place; showing the old one too is noise.
            val earlierBlocks = state.earlier.filter {
                it.type != BlockType.SLEEP && it.status != BlockStatus.RESCHEDULED && it.status != BlockStatus.CANCELLED
            }
            return TodayView(
                now = now,
                next = next,
                sections = sections,
                earlier = Timeline.collapse(earlierBlocks),
                earlierDone = earlierBlocks.count { it.status == BlockStatus.DONE },
                earlierSkipped = earlierBlocks.count { it.status == BlockStatus.SKIPPED },
                earlierMissed = earlierBlocks.count { it.status == BlockStatus.MISSED },
            )
        }
    }
}
