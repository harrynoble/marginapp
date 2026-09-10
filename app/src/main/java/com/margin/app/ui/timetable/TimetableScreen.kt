package com.margin.app.ui.timetable

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.MetaChip
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.subjectAccent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The recurring week. It is seeded from the timetable the app shipped with, and every cell
 * here is editable, because a printed timetable is a starting point rather than the truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TimetableEntry?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Week", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add a class")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DayTabs(
                selected = state.selectedDay,
                onSelect = viewModel::selectDay,
                minutesFor = { state.teachingMinutes(it) },
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
                val entries = state.entriesForSelected
                if (entries.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No classes on this day",
                            body = "Add one, or leave the day clear for your own work.",
                        )
                    }
                }

                items(entries, key = { "entry-${it.id}" }) { entry ->
                    TimetableRow(
                        entry = entry,
                        subjectIndex = state.subjects.indexOfFirst { it.code == entry.subjectCode },
                        use24Hour = state.use24Hour,
                        onClick = { editing = entry },
                    )
                }

                if (state.upcomingExceptions.isNotEmpty()) {
                    item(key = "exceptions") {
                        Spacer(Modifier.height(Space.xl))
                        SectionHeader("Changes ahead")
                        Spacer(Modifier.height(Space.s))
                    }
                    items(state.upcomingExceptions, key = { "ex-${it.id}" }) { exception ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = MarginTime.dayLabel(exception.date),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = listOfNotNull(
                                        exception.type.key.replaceFirstChar { it.uppercase() },
                                        exception.title,
                                        exception.note,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.removeException(exception) }) {
                                Text("Undo", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                item(key = "subjects") {
                    Spacer(Modifier.height(Space.xl))
                    SectionHeader("Subjects")
                    Spacer(Modifier.height(Space.s))
                    MarginCard {
                        state.subjects.forEachIndexed { index, subject ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Space.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(subjectAccent(index)),
                                )
                                Spacer(Modifier.width(Space.m))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(subject.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Review weight " + "%.1f".format(subject.reviewWeight),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    TextButton(
                                        onClick = {
                                            viewModel.saveSubject(
                                                subject.copy(
                                                    reviewWeight = (subject.reviewWeight - 0.1f)
                                                        .coerceAtLeast(0f),
                                                ),
                                            )
                                        },
                                    ) { Text("-") }
                                    TextButton(
                                        onClick = {
                                            viewModel.saveSubject(
                                                subject.copy(
                                                    reviewWeight = (subject.reviewWeight + 0.1f)
                                                        .coerceAtMost(3f),
                                                ),
                                            )
                                        },
                                    ) { Text("+") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TimetableEntrySheet(
            entry = null,
            day = state.selectedDay,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { creating = false },
            onSave = {
                viewModel.saveEntry(it)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { entry ->
        TimetableEntrySheet(
            entry = entry,
            day = entry.dayOfWeek,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveEntry(it)
                editing = null
            },
            onDelete = {
                viewModel.deleteEntry(entry)
                editing = null
            },
            onCancelToday = {
                viewModel.cancelOn(LocalDate.now(), entry.id)
                editing = null
            },
        )
    }
}

@Composable
private fun DayTabs(
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    minutesFor: (DayOfWeek) -> Int,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s)) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(day) }
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    )
                    .padding(vertical = Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                val minutes = minutesFor(day)
                Text(
                    text = if (minutes > 0) MarginTime.formatDurationShort(minutes) else "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimetableRow(
    entry: TimetableEntry,
    subjectIndex: Int,
    use24Hour: Boolean,
    onClick: () -> Unit,
) {
    val isRecess = entry.kind == TimetableKind.RECESS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(if (use24Hour) 50.dp else 70.dp)) {
            Text(
                text = MarginTime.formatTime(entry.start, use24Hour),
                style = com.margin.app.ui.theme.TimeGutterStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = MarginTime.formatTime(entry.end, use24Hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(end = Space.m, top = 3.dp)
                .width(3.dp)
                .heightIn(min = 30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isRecess || subjectIndex < 0) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        subjectAccent(subjectIndex)
                    },
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRecess) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            val meta = listOfNotNull(
                entry.subjectCode,
                entry.kind.label.takeIf { entry.kind != TimetableKind.LECTURE },
                entry.faculty,
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MetaChip(text = MarginTime.formatDurationShort(entry.range.duration))
    }
}
