package com.margin.app.ui.tasks

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.LearningGoal
import com.margin.app.ui.components.DurationRow
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/**
 * A skill to learn alongside college. Learning is its own category: it is offered, never
 * imposed, and it steps aside on its own when exams are close.
 */
@Composable
fun GoalSheet(
    goal: LearningGoal?,
    onDismiss: () -> Unit,
    onSave: (LearningGoal) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    var name by remember { mutableStateOf(goal?.name.orEmpty()) }
    var weekly by remember { mutableIntStateOf(goal?.weeklyTargetMinutes ?: 150) }
    var session by remember { mutableIntStateOf(goal?.sessionMinutes ?: 30) }
    var pauseForExams by remember { mutableStateOf(goal?.pauseDuringExams ?: true) }
    var active by remember { mutableStateOf(goal?.active ?: true) }
    var notes by remember { mutableStateOf(goal?.notes.orEmpty()) }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (goal == null) "New Learning Goal" else "Learning Goal",
        trailingText = "Save",
        trailingEnabled = name.isNotBlank(),
        onTrailing = {
            onSave(
                LearningGoal(
                    id = goal?.id ?: 0,
                    name = name.trim(),
                    weeklyTargetMinutes = weekly,
                    sessionMinutes = session,
                    active = active,
                    pauseDuringExams = pauseForExams,
                    notes = notes.trim().ifBlank { null },
                ),
            )
        },
    ) {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = name, onValueChange = { name = it }, placeholder = "What you want to learn")
        }

        GroupedSection(
            header = "Time",
            footer = "A target, not a quota. Missing it is never held against you.",
        ) {
            DurationRow(
                title = "Each week",
                minutes = weekly,
                onChange = { weekly = it },
                step = 15,
                max = 10 * 60,
                format = { if (it == 0) "No target" else MarginTime.formatDuration(it) },
            )
            RowSeparator()
            DurationRow(title = "Session length", minutes = session, onChange = { session = it }, min = 15, max = 120)
        }

        GroupedSection {
            GroupedRow(
                title = "Pause when exams are close",
                trailing = { IosSwitch(checked = pauseForExams, onCheckedChange = { pauseForExams = it }) },
            )
            if (goal != null) {
                RowSeparator()
                GroupedRow(title = "Active", trailing = { IosSwitch(checked = active, onCheckedChange = { active = it }) })
            }
        }

        GroupedSection(header = "Notes") {
            FormTextField(value = notes, onValueChange = { notes = it }, placeholder = "Course, book, where you left off", singleLine = false, minLines = 2)
        }

        if (onDelete != null) {
            GroupedSection(modifier = Modifier.padding(top = Space.l)) {
                GroupedRow(title = "Delete Goal", titleColor = colors.destructive, onClick = onDelete)
            }
        }
    }
}
