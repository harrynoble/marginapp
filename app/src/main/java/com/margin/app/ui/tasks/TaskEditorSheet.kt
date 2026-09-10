package com.margin.app.ui.tasks

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.Task
import com.margin.app.ui.components.CategoryPicker
import com.margin.app.ui.components.DifficultyPicker
import com.margin.app.ui.components.DurationPicker
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.PriorityPicker
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.theme.Space
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * One sheet for creating and editing. Duration, deadline and difficulty are the fields the
 * planner actually uses, so they are the ones given room here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    task: Task?,
    projects: List<Project>,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)?,
    links: List<ResourceLink> = emptyList(),
    onAddLink: ((String, String) -> Unit)? = null,
    onDeleteLink: ((Long) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember { mutableStateOf(task?.notes.orEmpty()) }
    var category by remember { mutableStateOf(task?.category ?: Category.ACADEMICS) }
    var minutes by remember { mutableIntStateOf(task?.estimatedMinutes ?: 45) }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NORMAL) }
    var difficulty by remember { mutableStateOf(task?.difficulty ?: Difficulty.MODERATE) }
    var deadline by remember { mutableStateOf(task?.deadlineDate) }
    var projectId by remember { mutableStateOf(task?.projectId) }
    var splittable by remember { mutableStateOf(task?.splittable ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var recurrence by remember { mutableIntStateOf(task?.recurrenceMask ?: 0) }
    var linkLabel by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }

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
                text = if (task == null) "New task" else "Edit task",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(Space.l))
            MarginTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "What needs doing",
            )

            Spacer(Modifier.height(Space.l))
            LabeledField("How long will it take") {
                DurationPicker(minutes = minutes, onChange = { minutes = it })
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Deadline") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    listOf(
                        "None" to null,
                        "Today" to LocalDate.now(),
                        "Tomorrow" to LocalDate.now().plusDays(1),
                        "In 3 days" to LocalDate.now().plusDays(3),
                    ).forEach { (label, date) ->
                        FilterChip(
                            selected = deadline == date,
                            onClick = { deadline = date },
                            label = { Text(label) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                    FilterChip(
                        selected = deadline != null &&
                            deadline !in listOf(
                                LocalDate.now(),
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(3),
                            ),
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                deadline?.let { MarginTime.dayLabel(it) } ?: "Pick a date",
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Category") {
                CategoryPicker(selected = category, onSelect = { category = it })
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Priority") {
                PriorityPicker(selected = priority, onSelect = { priority = it })
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Difficulty") {
                DifficultyPicker(selected = difficulty, onSelect = { difficulty = it })
            }

            if (projects.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                LabeledField("Project") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        FilterChip(
                            selected = projectId == null,
                            onClick = { projectId = null },
                            label = { Text("None") },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        projects.forEach { project ->
                            FilterChip(
                                selected = projectId == project.id,
                                onClick = { projectId = project.id },
                                label = { Text(project.name) },
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
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Repeat") {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            val on = recurrence and bit != 0
                            FilterChip(
                                selected = on,
                                onClick = {
                                    recurrence = if (on) recurrence and bit.inv() else recurrence or bit
                                },
                                label = {
                                    Text(
                                        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                            .take(3),
                                    )
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
                    if (recurrence != 0) {
                        Spacer(Modifier.height(Space.s))
                        Text(
                            text = "This comes back on the days you picked. A deadline does not " +
                                "apply to repeating work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Sessions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(
                        selected = splittable,
                        onClick = { splittable = true },
                        label = { Text("Can be split") },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = !splittable,
                        onClick = { splittable = false },
                        label = { Text("One sitting") },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            LabeledField("Notes") {
                MarginTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Anything worth remembering",
                    singleLine = false,
                    minLines = 2,
                )
            }

            if (task != null && onAddLink != null) {
                Spacer(Modifier.height(Space.l))
                LabeledField("Resources") {
                    Column {
                        links.forEach { link ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = link.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (onDeleteLink != null) {
                                    TextButton(onClick = { onDeleteLink(link.id) }) { Text("Remove") }
                                }
                            }
                        }
                        MarginTextField(
                            value = linkLabel,
                            onValueChange = { linkLabel = it },
                            placeholder = "Label",
                        )
                        Spacer(Modifier.height(Space.s))
                        MarginTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            placeholder = "Link or reference",
                        )
                        Spacer(Modifier.height(Space.s))
                        TextButton(
                            onClick = {
                                onAddLink(linkLabel.trim(), linkUrl.trim())
                                linkLabel = ""
                                linkUrl = ""
                            },
                            enabled = linkUrl.isNotBlank() || linkLabel.isNotBlank(),
                        ) { Text("Add resource") }
                    }
                }
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
                        val base = task ?: Task(title = "", estimatedMinutes = minutes)
                        onSave(
                            base.copy(
                                title = title.trim().ifBlank { "Untitled task" },
                                notes = notes.trim().ifBlank { null },
                                category = category,
                                estimatedMinutes = minutes,
                                priority = priority,
                                difficulty = difficulty,
                                deadlineDate = if (recurrence == 0) deadline else null,
                                recurrenceMask = recurrence,
                                projectId = projectId,
                                splittable = splittable,
                                minSessionMinutes = if (splittable) {
                                    minOf(25, minutes)
                                } else {
                                    minutes
                                },
                                maxSessionMinutes = if (splittable) {
                                    minOf(60, minutes).coerceAtLeast(15)
                                } else {
                                    minutes
                                },
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    enabled = title.isNotBlank(),
                ) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        deadline = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            DatePicker(state = pickerState)
        }
    }
}
