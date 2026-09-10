package com.margin.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Project
import com.margin.app.ui.components.CategoryPicker
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.components.StepperRow
import com.margin.app.ui.theme.Space

/**
 * Projects group build and learning work. A weekly target is optional; when it is set, the
 * planner uses it as a reason to keep reserving time for the project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSheet(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (Project) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(project?.name.orEmpty()) }
    var notes by remember { mutableStateOf(project?.notes.orEmpty()) }
    var category by remember { mutableStateOf(project?.category ?: Category.BUILD) }
    var weekly by remember { mutableIntStateOf(project?.targetMinutesPerWeek ?: 0) }

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
                .imePadding()
                .navigationBarsPadding(),
        ) {
            SheetGrabber()
            Spacer(Modifier.height(Space.l))
            Text(
                text = if (project == null) "New project" else "Edit project",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(Space.l))
            MarginTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "What are you building",
            )

            Spacer(Modifier.height(Space.l))
            LabeledField("Category") {
                CategoryPicker(selected = category, onSelect = { category = it })
            }

            Spacer(Modifier.height(Space.l))
            StepperRow(
                label = "Target each week",
                value = if (weekly == 0) "None" else MarginTime.formatDuration(weekly),
                onDecrease = { weekly = (weekly - 30).coerceAtLeast(0) },
                onIncrease = { weekly = (weekly + 30).coerceAtMost(20 * 60) },
            )

            Spacer(Modifier.height(Space.l))
            LabeledField("Notes") {
                MarginTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Optional",
                    singleLine = false,
                    minLines = 2,
                )
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
                        onSave(
                            (project ?: Project(name = "")).copy(
                                name = name.trim().ifBlank { "Untitled project" },
                                notes = notes.trim().ifBlank { null },
                                category = category,
                                targetMinutesPerWeek = weekly,
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            }
        }
    }
}
