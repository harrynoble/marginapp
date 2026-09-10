package com.margin.app.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.components.TimeField
import com.margin.app.ui.theme.Space
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimetableEntrySheet(
    entry: TimetableEntry?,
    day: DayOfWeek,
    subjects: List<Subject>,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit,
    onDelete: (() -> Unit)?,
    onCancelToday: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var faculty by remember { mutableStateOf(entry?.faculty.orEmpty()) }
    var location by remember { mutableStateOf(entry?.location.orEmpty()) }
    var subjectCode by remember { mutableStateOf(entry?.subjectCode) }
    var kind by remember { mutableStateOf(entry?.kind ?: TimetableKind.LECTURE) }
    var selectedDay by remember { mutableStateOf(entry?.dayOfWeek ?: day) }
    var start by remember { mutableIntStateOf(entry?.start ?: (9 * 60)) }
    var end by remember { mutableIntStateOf(entry?.end ?: (10 * 60)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Space.gutter)
                .padding(top = Space.xl, bottom = Space.l)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding(),
        ) {
            SheetGrabber()
            Spacer(Modifier.height(Space.l))
            Text(
                text = if (entry == null) "New class" else "Edit class",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(Space.l))
            MarginTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Subject or activity",
            )

            Spacer(Modifier.height(Space.l))
            LabeledField("Day") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = selectedDay == d,
                            onClick = { selectedDay = d },
                            label = {
                                Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3))
                            },
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

            Spacer(Modifier.height(Space.l))
            LabeledField("Time") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TimeField(
                        minute = start,
                        use24Hour = use24Hour,
                        label = "From",
                        onChange = {
                            start = it
                            if (end <= start) end = (start + 50).coerceAtMost(24 * 60)
                        },
                    )
                    TimeField(
                        minute = end,
                        use24Hour = use24Hour,
                        label = "To",
                        onChange = { end = it.coerceAtLeast(start + 5) },
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Kind") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TimetableKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label) },
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

            Spacer(Modifier.height(Space.l))
            LabeledField("Subject") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(
                        selected = subjectCode == null,
                        onClick = { subjectCode = null },
                        label = { Text("None") },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    subjects.forEach { subject ->
                        FilterChip(
                            selected = subjectCode == subject.code,
                            onClick = { subjectCode = subject.code },
                            label = { Text(subject.code) },
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

            Spacer(Modifier.height(Space.l))
            LabeledField("Faculty") {
                MarginTextField(
                    value = faculty,
                    onValueChange = { faculty = it },
                    placeholder = "Optional",
                )
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Room") {
                MarginTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = "Optional",
                )
            }

            if (onCancelToday != null) {
                Spacer(Modifier.height(Space.l))
                TextButton(onClick = onCancelToday) { Text("Cancel just for today") }
            }

            Spacer(Modifier.height(Space.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        val base = entry ?: TimetableEntry(
                            dayOfWeek = selectedDay,
                            start = start,
                            end = end,
                            subjectCode = subjectCode,
                            title = title,
                            kind = kind,
                        )
                        onSave(
                            base.copy(
                                dayOfWeek = selectedDay,
                                start = start,
                                end = end,
                                subjectCode = subjectCode,
                                title = title.trim().ifBlank { subjectCode ?: "Class" },
                                kind = kind,
                                faculty = faculty.trim().ifBlank { null },
                                location = location.trim().ifBlank { null },
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    enabled = end > start,
                ) { Text("Save") }
            }
        }
    }
}
