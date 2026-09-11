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
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Project
import com.margin.app.ui.components.CategoryChips
import com.margin.app.ui.components.DurationRow
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/** Projects group build and learning work. A weekly target keeps them from being crowded out. */
@Composable
fun ProjectSheet(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (Project) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(project?.name.orEmpty()) }
    var notes by remember { mutableStateOf(project?.notes.orEmpty()) }
    var category by remember { mutableStateOf(project?.category ?: Category.BUILD) }
    var weekly by remember { mutableIntStateOf(project?.targetMinutesPerWeek ?: 0) }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (project == null) "New Project" else "Project",
        trailingText = if (project == null) "Add" else "Done",
        trailingEnabled = name.isNotBlank(),
        onTrailing = {
            onSave(
                (project ?: Project(name = "")).copy(
                    name = name.trim().ifBlank { "Untitled project" },
                    notes = notes.trim().ifBlank { null },
                    category = category,
                    targetMinutesPerWeek = weekly,
                ),
            )
        },
    ) {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = name, onValueChange = { name = it }, placeholder = "What are you building")
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
        GroupedSection(header = "Category") {
            CategoryChips(selected = category, onSelect = { category = it })
        }
        GroupedSection(header = "Weekly target") {
            DurationRow(
                title = "Each week",
                minutes = weekly,
                onChange = { weekly = it },
                step = 30,
                max = 20 * 60,
                format = { if (it == 0) "None" else MarginTime.formatDuration(it) },
            )
        }
        if (onDelete != null) {
            GroupedSection(modifier = Modifier.padding(top = Space.xl)) {
                GroupedRow(title = "Delete Project", titleColor = MarginTheme.colors.destructive, onClick = onDelete)
            }
        }
    }
}
