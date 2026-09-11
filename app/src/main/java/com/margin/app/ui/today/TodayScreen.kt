package com.margin.app.ui.today

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.planner.EnergyMode
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
import com.margin.app.ui.components.RowSeparator
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

/**
 * The screen the app opens to. In order: what is happening now, what is next, and how the rest
 * of the day looks. College appears as a single line; the timetable itself never does.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onOpenFocus: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val backdrop = rememberGlassBackdrop()

    var sheetBlock by remember { mutableStateOf<ScheduleBlock?>(null) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    var choosingBreak by remember { mutableStateOf(false) }

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
        item(key = "now") {
            NowCard(
                subject = view.now,
                nowMinute = state.nowMinute,
                use24Hour = state.use24Hour,
                nextLabel = view.nextLabel(state.use24Hour),
                onStart = { block ->
                    viewModel.start(block.id)
                    if (block.type.isWork) onOpenFocus(block.id)
                },
                onFinish = { viewModel.complete(it.id) },
                onPause = { viewModel.pause(it.id) },
                onSkip = { sheetBlock = it },
                onMore = { sheetBlock = it },
                onRebuild = { viewModel.replan() },
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
                if (choosingBreak) {
                    listOf(5, 10, 15, 30, 45).forEach { minutes ->
                        CapsuleChip(
                            text = "$minutes min",
                            selected = false,
                            onClick = {
                                choosingBreak = false
                                viewModel.takeBreak(minutes)
                            },
                        )
                    }
                    CapsuleChip(text = "Cancel", selected = false, onClick = { choosingBreak = false })
                } else {
                    CapsuleChip(
                        text = "Take a break",
                        selected = false,
                        onClick = { choosingBreak = true },
                        leading = {
                            Icon(
                                Icons.Rounded.Coffee,
                                contentDescription = null,
                                tint = colors.label,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    val light = state.energyMode == EnergyMode.LIGHT
                    CapsuleChip(
                        text = if (light) "Light day" else "Keep today light",
                        selected = light,
                        onClick = {
                            viewModel.setEnergyMode(if (light) EnergyMode.NORMAL else EnergyMode.LIGHT)
                        },
                        leading = {
                            Icon(
                                Icons.Rounded.Spa,
                                contentDescription = null,
                                tint = if (light) colors.onTint else colors.label,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        banner?.let { change ->
            item(key = "banner") {
                ChangeCard(banner = change, onDismiss = viewModel::dismissBanner)
            }
        }

        if (state.checkInDue) {
            item(key = "checkin") {
                GroupedSection(modifier = Modifier.padding(top = Space.s)) {
                    GroupedRow(
                        title = "Close out the day",
                        subtitle = "A short review, then unfinished work carries forward.",
                        leading = { IconTile(Icons.Rounded.Bedtime, MarginTheme.accents.indigo) },
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
                    title = "Nothing else today",
                    body = "The rest of the day is yours. Add something with the plus button if you want.",
                )
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
                                is TimelineItem.Single -> TimelineRow(item.block, state.use24Hour)
                            }
                        }
                    } else {
                        GroupedRow(
                            title = buildString {
                                append("${view.earlierDone} finished")
                                if (view.earlierSkipped > 0) append(" · ${view.earlierSkipped} skipped")
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
                    accent = MarginTheme.accents.green,
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
}

/** Separators in the timeline start where the title does, past the time and the rail. */
private val TimelineSeparatorInset = Space.l + 76.dp + 4.dp + Space.m

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
                    if (block.type.isActionable) NowSubject.Work(block) else NowSubject.Fixed(block)
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

            val earlierBlocks = state.earlier.filter { it.type != BlockType.SLEEP }
            return TodayView(
                now = now,
                next = next,
                sections = sections,
                earlier = Timeline.collapse(earlierBlocks),
                earlierDone = earlierBlocks.count { it.status == BlockStatus.DONE },
                earlierSkipped = earlierBlocks.count { it.status == BlockStatus.SKIPPED },
            )
        }
    }
}
