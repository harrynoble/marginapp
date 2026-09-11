package com.margin.app.ui.timetable

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
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
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.OptionChips
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.components.TimeRow
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** One recurring class. Changes apply to every week from now on. */
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
    val colors = MarginTheme.colors
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var faculty by remember { mutableStateOf(entry?.faculty.orEmpty()) }
    var location by remember { mutableStateOf(entry?.location.orEmpty()) }
    var subjectCode by remember { mutableStateOf(entry?.subjectCode) }
    var kind by remember { mutableStateOf(entry?.kind ?: TimetableKind.LECTURE) }
    var selectedDay by remember { mutableStateOf(entry?.dayOfWeek ?: day) }
    var start by remember { mutableIntStateOf(entry?.start ?: (9 * 60)) }
    var end by remember { mutableIntStateOf(entry?.end ?: (10 * 60)) }
    var confirmingDelete by remember { mutableStateOf(false) }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (entry == null) "New Class" else "Class",
        trailingText = if (entry == null) "Add" else "Done",
        trailingEnabled = end > start,
        onTrailing = {
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
    ) {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = title, onValueChange = { title = it }, placeholder = "Subject or activity")
            RowSeparator()
            FormTextField(value = faculty, onValueChange = { faculty = it }, placeholder = "Faculty")
            RowSeparator()
            FormTextField(value = location, onValueChange = { location = it }, placeholder = "Room")
        }

        GroupedSection(header = "Day") {
            OptionChips(
                options = DayOfWeek.entries,
                selected = selectedDay,
                label = { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                onSelect = { selectedDay = it },
            )
        }

        GroupedSection(header = "Time") {
            TimeRow(
                title = "Starts",
                minute = start,
                use24Hour = use24Hour,
                onChange = {
                    start = it
                    if (end <= start) end = (start + 50).coerceAtMost(24 * 60 - 5)
                },
            )
            RowSeparator()
            TimeRow(
                title = "Ends",
                minute = end,
                use24Hour = use24Hour,
                onChange = { end = it.coerceAtLeast(start + 5) },
            )
        }

        GroupedSection(header = "Kind") {
            OptionChips(
                options = TimetableKind.entries,
                selected = kind,
                label = { it.label },
                onSelect = { kind = it },
            )
        }

        if (subjects.isNotEmpty()) {
            GroupedSection(header = "Subject") {
                OptionChips(
                    options = listOf<String?>(null) + subjects.map { it.code },
                    selected = subjectCode,
                    label = { it ?: "None" },
                    onSelect = { subjectCode = it },
                )
            }
        }

        if (onCancelToday != null) {
            GroupedSection(
                modifier = Modifier.padding(top = Space.xl),
                footer = "Only today changes. Next week the class is back as usual.",
            ) {
                GroupedRow(title = "Cancel Just for Today", titleColor = colors.tint, onClick = onCancelToday)
            }
        }

        if (onDelete != null) {
            GroupedSection(modifier = Modifier.padding(top = Space.xl)) {
                GroupedRow(
                    title = "Delete Class",
                    titleColor = colors.destructive,
                    onClick = { confirmingDelete = true },
                )
            }
        }
    }

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this class?", style = AppleType.headline) },
            text = {
                Text(
                    "It comes off every week. To skip it once, cancel it just for today instead.",
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
