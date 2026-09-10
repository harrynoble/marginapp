package com.margin.app.ui.today

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.BlockActionCallbacks
import com.margin.app.ui.components.BlockActionsSheet
import com.margin.app.ui.components.BlockRow
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.MetaChip
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.theme.Space

/**
 * The screen the app opens to. In order: what is happening now, what is next, and how the
 * rest of the day looks. Nothing else competes for that hierarchy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenFocus: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var sheetBlock by remember { mutableStateOf<ScheduleBlock?>(null) }
    var showEarlier by remember { mutableStateOf(false) }
    var showBreakOptions by remember { mutableStateOf(false) }

    val callbacks = remember(viewModel) {
        BlockActionCallbacks(
            onStart = { viewModel.start(it) },
            onComplete = { viewModel.complete(it) },
            onSkip = { id, resolution -> viewModel.skip(id, resolution) },
            onExtend = { id, minutes -> viewModel.extend(id, minutes) },
            onMove = { id, minute -> viewModel.move(id, minute) },
            onMoveToTomorrow = { id -> viewModel.moveToTomorrow(id) },
            onTogglePin = { id, locked -> viewModel.togglePin(id, locked) },
            onOpenTask = onOpenTask,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = MarginTime.dayLabel(state.date),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (state.headline.isNotBlank()) {
                            Text(
                                text = state.headline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAssistant) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Tell Margin something",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.replan() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rebuild the plan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.s,
                    bottom = Space.xxxl * 2,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                banner?.let { change ->
                    item(key = "banner") {
                        ChangeBannerCard(banner = change, onDismiss = viewModel::dismissBanner)
                        Spacer(Modifier.height(Space.s))
                    }
                }

                if (state.checkInDue) {
                    item(key = "checkin") {
                        MarginCard(onClick = onOpenCheckIn) {
                            Text(
                                text = "Ready to close out the day?",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                text = "A short review, then Margin carries the unfinished work forward.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Space.s))
                    }
                }

                item(key = "now") {
                    val current = state.current
                    if (current != null) {
                        NowCard(
                            block = current,
                            nowMinute = state.nowMinute,
                            use24Hour = state.use24Hour,
                            onStart = {
                                viewModel.start(current.id)
                                if (current.type.isWork) onOpenFocus(current.id)
                            },
                            onPause = { viewModel.pause(current.id) },
                            onComplete = { viewModel.complete(current.id) },
                            onMore = { sheetBlock = current },
                        )
                    } else {
                        OpenNowCard(
                            nextBlock = state.next,
                            use24Hour = state.use24Hour,
                            onPlan = { viewModel.replan() },
                            outsideWakingHours = state.outsideWakingHours,
                            wakeMinute = state.wakeMinute,
                        )
                    }
                }

                item(key = "quick") {
                    Spacer(Modifier.height(Space.xs))
                    QuickActions(
                        showBreakOptions = showBreakOptions,
                        onToggleBreak = { showBreakOptions = !showBreakOptions },
                        onBreak = { minutes ->
                            showBreakOptions = false
                            viewModel.takeBreak(minutes)
                        },
                        onEnergy = viewModel::setEnergyMode,
                        currentMode = state.energyMode,
                    )
                    Spacer(Modifier.height(Space.m))
                }

                if (state.upcoming.isNotEmpty()) {
                    item(key = "next-header") {
                        SectionHeader("Next")
                        Spacer(Modifier.height(Space.xs))
                    }
                    items(
                        items = state.upcoming,
                        key = { "up-${it.id}" },
                    ) { block ->
                        BlockRow(
                            block = block,
                            use24Hour = state.use24Hour,
                            onClick = { sheetBlock = block },
                        )
                    }
                } else if (!state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Nothing left on the schedule",
                            body = "Add a task or let Margin rebuild the day.",
                        )
                    }
                }

                if (state.earlier.isNotEmpty()) {
                    item(key = "earlier-header") {
                        Spacer(Modifier.height(Space.m))
                        SectionHeader(
                            text = "Earlier",
                            trailing = {
                                TextButton(onClick = { showEarlier = !showEarlier }) {
                                    Text(
                                        text = if (showEarlier) "Hide" else "Show",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                        )
                    }
                    if (showEarlier) {
                        items(items = state.earlier, key = { "past-${it.id}" }) { block ->
                            BlockRow(block = block, use24Hour = state.use24Hour)
                        }
                    } else {
                        item(key = "earlier-summary") {
                            val done = state.earlier.count { it.status == BlockStatus.DONE }
                            val skipped = state.earlier.count { it.status == BlockStatus.SKIPPED }
                            Text(
                                text = buildString {
                                    append("$done finished")
                                    if (skipped > 0) append(", $skipped skipped")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Space.xs),
                            )
                        }
                    }
                }

                item(key = "summary") {
                    Spacer(Modifier.height(Space.l))
                    DaySummaryStrip(state = state)
                }
            }
        }
    }

    sheetBlock?.let { block ->
        BlockActionsSheet(
            block = block,
            use24Hour = state.use24Hour,
            nowMinute = state.nowMinute,
            callbacks = callbacks,
            onDismiss = { sheetBlock = null },
        )
    }
}

@Composable
private fun ChangeBannerCard(banner: ChangeBanner, onDismiss: () -> Unit) {
    MarginCard(color = MaterialTheme.colorScheme.surfaceContainerHighest, border = false) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Schedule updated",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Space.xs))
                banner.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                banner.protectedNote?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    showBreakOptions: Boolean,
    onToggleBreak: () -> Unit,
    onBreak: (Int) -> Unit,
    onEnergy: (com.margin.app.domain.planner.EnergyMode) -> Unit,
    currentMode: com.margin.app.domain.planner.EnergyMode,
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            AssistChip(
                onClick = onToggleBreak,
                label = { Text("Take a break") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Coffee,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = MaterialTheme.shapes.small,
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            AssistChip(
                onClick = {
                    onEnergy(
                        if (currentMode == com.margin.app.domain.planner.EnergyMode.LIGHT) {
                            com.margin.app.domain.planner.EnergyMode.NORMAL
                        } else {
                            com.margin.app.domain.planner.EnergyMode.LIGHT
                        },
                    )
                },
                label = {
                    Text(
                        if (currentMode == com.margin.app.domain.planner.EnergyMode.LIGHT) {
                            "Light day on"
                        } else {
                            "Keep today light"
                        },
                    )
                },
                shape = MaterialTheme.shapes.small,
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
        AnimatedVisibility(visible = showBreakOptions) {
            FlowRow(
                modifier = Modifier.padding(top = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                listOf(5, 10, 15, 30, 45).forEach { minutes ->
                    AssistChip(
                        onClick = { onBreak(minutes) },
                        label = { Text("$minutes min") },
                        shape = MaterialTheme.shapes.small,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySummaryStrip(state: TodayUiState) {
    MarginCard(color = MaterialTheme.colorScheme.surfaceContainerHighest, border = false) {
        SectionHeader("Today so far")
        Spacer(Modifier.height(Space.m))
        com.margin.app.ui.components.ProportionBar(fraction = state.progress)
        Spacer(Modifier.height(Space.m))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            MetaChip(
                text = MarginTime.formatDuration(state.completedWorkMinutes) + " done",
            )
            MetaChip(
                text = MarginTime.formatDuration(
                    (state.plannedWorkMinutes - state.completedWorkMinutes).coerceAtLeast(0),
                ) + " planned left",
            )
            MetaChip(text = MarginTime.formatDuration(state.freeRemainingMinutes) + " free left")
        }
    }
}
