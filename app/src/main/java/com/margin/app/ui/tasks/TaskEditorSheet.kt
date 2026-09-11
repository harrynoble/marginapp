package com.margin.app.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import com.margin.app.ui.components.CapsuleChip
import com.margin.app.ui.components.CategoryChips
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.OptionChips
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SegmentedControl
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * One sheet for creating and editing a task. The fields the planner actually reads (time
 * needed, deadline, effort, whether it can be split) get the most room.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = MarginTheme.colors
    val today = LocalDate.now()

    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember { mutableStateOf(task?.notes.orEmpty()) }
    var category by remember { mutableStateOf(task?.category ?: Category.ACADEMICS) }
    var minutes by remember { mutableIntStateOf(task?.estimatedMinutes ?: 45) }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NORMAL) }
    var difficulty by remember { mutableStateOf(task?.difficulty ?: Difficulty.MODERATE) }
    var deadline by remember { mutableStateOf(task?.deadlineDate) }
    var projectId by remember { mutableStateOf(task?.projectId) }
    var splittable by remember { mutableStateOf(task?.splittable ?: true) }
    var recurrence by remember { mutableIntStateOf(task?.recurrenceMask ?: 0) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var linkLabel by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }

    fun save() {
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
                minSessionMinutes = if (splittable) minOf(25, minutes) else minutes,
                maxSessionMinutes = if (splittable) minOf(60, minutes).coerceAtLeast(15) else minutes,
            ),
        )
    }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (task == null) "New Task" else "Details",
        trailingText = if (task == null) "Add" else "Done",
        trailingEnabled = title.isNotBlank(),
        onTrailing = ::save,
    ) {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = title, onValueChange = { title = it }, placeholder = "Title")
            RowSeparator()
            FormTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Notes",
                singleLine = false,
                minLines = 2,
                style = AppleType.subheadline,
            )
        }

        GroupedSection(header = "Time needed") {
            OptionChips(
                options = listOf(15, 25, 30, 45, 60, 90, 120, 180),
                selected = minutes,
                label = { MarginTime.formatDurationShort(it) },
                onSelect = { minutes = it },
            )
        }

        if (recurrence == 0) {
            val presets = listOf(
                "None" to null,
                "Today" to today,
                "Tomorrow" to today.plusDays(1),
                "In 3 days" to today.plusDays(3),
                "Next week" to today.plusWeeks(1),
            )
            GroupedSection(header = "Due") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    presets.forEach { (label, date) ->
                        CapsuleChip(text = label, selected = deadline == date, onClick = { deadline = date })
                    }
                    val custom = deadline != null && presets.none { it.second == deadline }
                    CapsuleChip(
                        text = if (custom) MarginTime.dayLabel(deadline!!, today) else "Pick a date",
                        selected = custom,
                        onClick = { pickingDate = true },
                    )
                }
            }
        }

        GroupedSection(
            header = "Repeat",
            footer = if (recurrence != 0) "It comes back on the days you picked, with no deadline." else null,
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    val on = recurrence and bit != 0
                    CapsuleChip(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        selected = on,
                        onClick = { recurrence = if (on) recurrence and bit.inv() else recurrence or bit },
                    )
                }
            }
        }

        GroupedSection(header = "Category") {
            CategoryChips(selected = category, onSelect = { category = it })
        }

        GroupedSection(header = "Priority") {
            SegmentedControl(
                options = Priority.entries,
                selected = priority,
                label = { it.label },
                onSelect = { priority = it },
                modifier = Modifier.padding(Space.m),
            )
        }

        GroupedSection(header = "Effort") {
            SegmentedControl(
                options = Difficulty.entries,
                selected = difficulty,
                label = { it.label },
                onSelect = { difficulty = it },
                modifier = Modifier.padding(Space.m),
            )
        }

        if (projects.isNotEmpty()) {
            GroupedSection(header = "Project") {
                OptionChips(
                    options = listOf<Long?>(null) + projects.map { it.id },
                    selected = projectId,
                    label = { id -> projects.firstOrNull { it.id == id }?.name ?: "None" },
                    onSelect = { projectId = it },
                )
            }
        }

        GroupedSection(
            modifier = Modifier.padding(top = Space.xl),
            footer = "Split work is spread across the days before it is due, a session at a time.",
        ) {
            GroupedRow(
                title = "Can be split into sessions",
                trailing = { IosSwitch(checked = splittable, onCheckedChange = { splittable = it }) },
            )
        }

        if (task != null && onAddLink != null) {
            GroupedSection(header = "Resources") {
                links.forEach { link ->
                    GroupedRow(
                        title = link.label,
                        subtitle = link.url.takeIf { it.isNotBlank() && it != link.label },
                        trailing = {
                            if (onDeleteLink != null) {
                                TextAction(text = "Remove", color = colors.destructive, onClick = { onDeleteLink(link.id) })
                            }
                        },
                    )
                    RowSeparator()
                }
                FormTextField(value = linkLabel, onValueChange = { linkLabel = it }, placeholder = "Label")
                RowSeparator()
                FormTextField(value = linkUrl, onValueChange = { linkUrl = it }, placeholder = "Link or reference")
                RowSeparator()
                GroupedRow(
                    title = "Add Resource",
                    titleColor = if (linkUrl.isNotBlank() || linkLabel.isNotBlank()) colors.tint else colors.tertiaryLabel,
                    onClick = {
                        if (linkUrl.isNotBlank() || linkLabel.isNotBlank()) {
                            onAddLink(linkLabel.trim(), linkUrl.trim())
                            linkLabel = ""
                            linkUrl = ""
                        }
                    },
                )
            }
        }

        if (onDelete != null) {
            GroupedSection(modifier = Modifier.padding(top = Space.xl)) {
                GroupedRow(
                    title = "Delete Task",
                    titleColor = colors.destructive,
                    onClick = { confirmingDelete = true },
                )
            }
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (deadline ?: today)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextAction(text = "Done", emphasized = true, onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        deadline = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    pickingDate = false
                })
            },
            dismissButton = { TextAction(text = "Cancel", onClick = { pickingDate = false }) },
            shape = MarginShape.card,
            colors = DatePickerDefaults.colors(containerColor = colors.surface),
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this task?", style = AppleType.headline) },
            text = {
                Text(
                    "Its sessions come off the plan. History you have already logged stays.",
                    style = AppleType.subheadline,
                    color = colors.secondaryLabel,
                )
            },
            confirmButton = {
                TextAction(text = "Delete", emphasized = true, color = colors.destructive, onClick = {
                    confirmingDelete = false
                    onDelete()
                })
            },
            dismissButton = { TextAction(text = "Cancel", onClick = { confirmingDelete = false }) },
            containerColor = colors.surface,
            shape = MarginShape.card,
        )
    }
}
