package com.margin.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.BlockActionCallbacks
import com.margin.app.ui.components.BlockActionsSheet
import com.margin.app.ui.components.BlockRow
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.MetaChip
import com.margin.app.ui.theme.Space
import java.time.format.TextStyle
import java.util.Locale

/**
 * The whole day, and the week around it. Today answers "what now"; Plan answers
 * "what does the shape of my time look like".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetBlock by remember { mutableStateOf<ScheduleBlock?>(null) }
    var showAddEvent by remember { mutableStateOf(false) }

    val callbacks = remember(viewModel) {
        BlockActionCallbacks(
            onStart = viewModel::start,
            onComplete = viewModel::complete,
            onSkip = viewModel::skip,
            onExtend = viewModel::extend,
            onMove = viewModel::move,
            onMoveToTomorrow = viewModel::moveToTomorrow,
            onTogglePin = viewModel::togglePin,
            onOpenTask = onOpenTask,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Plan", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.toggleStructural() }) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = "Show or hide sleep",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.replan() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Rebuild this day",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEvent = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add an event")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekStrip(
                state = state,
                onSelect = viewModel::select,
                onShift = viewModel::shiftWeek,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.m,
                    bottom = Space.xxxl * 2,
                ),
            ) {
                item(key = "day-summary") {
                    val summary = state.week.firstOrNull { it.date == state.selectedDate }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        MetaChip(
                            text = MarginTime.formatDuration(summary?.classMinutes ?: 0) + " college",
                        )
                        MetaChip(
                            text = MarginTime.formatDuration(summary?.workMinutes ?: 0) + " work",
                        )
                        MetaChip(
                            text = MarginTime.formatDuration(summary?.freeMinutes ?: 0) + " free",
                        )
                    }
                    Spacer(Modifier.height(Space.m))
                }

                if (state.visibleBlocks.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Nothing scheduled here yet",
                            body = "Add a task or let the planner build the day.",
                        )
                    }
                }

                items(state.visibleBlocks, key = { it.id }) { block ->
                    BlockRow(
                        block = block,
                        use24Hour = state.use24Hour,
                        isNow = state.isToday && state.nowMinute in block.start until block.end,
                        onClick = { sheetBlock = block },
                    )
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

    if (showAddEvent) {
        AddEventSheet(
            date = state.selectedDate,
            use24Hour = state.use24Hour,
            onDismiss = { showAddEvent = false },
            onSave = { title, date, start, end, category, notes ->
                viewModel.addEvent(title, date, start, end, category, notes)
                showAddEvent = false
            },
        )
    }
}

@Composable
private fun WeekStrip(
    state: PlanUiState,
    onSelect: (java.time.LocalDate) -> Unit,
    onShift: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "Previous week",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = MarginTime.dayLabel(state.selectedDate),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onShift(1) }) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Next week",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(modifier = Modifier.fillMaxWidth()) {
            state.week.forEach { day ->
                val selected = day.date == state.selectedDate
                val isToday = day.date == java.time.LocalDate.now()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelect(day.date) }
                        .padding(vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.date.dayOfWeek
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (day.workMinutes > 0) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
    }
}
