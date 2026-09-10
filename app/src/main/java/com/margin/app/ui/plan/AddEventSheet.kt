package com.margin.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.margin.app.ui.components.CategoryPicker
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.components.TimeField
import com.margin.app.ui.theme.Space
import java.time.LocalDate

/** A one-off commitment. The planner treats it as hard time and works around it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventSheet(
    date: LocalDate,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, Int, Int, Category, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(18 * 60) }
    var end by remember { mutableIntStateOf(20 * 60) }
    var category by remember { mutableStateOf(Category.PERSONAL) }

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
            Text("New event", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = MarginTime.dayLabel(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.l))
            MarginTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "What is it",
            )

            Spacer(Modifier.height(Space.l))
            LabeledField("Time") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TimeField(
                        minute = start,
                        use24Hour = use24Hour,
                        label = "From",
                        onChange = {
                            start = it
                            if (end <= start) end = (start + 60).coerceAtMost(24 * 60)
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
            LabeledField("Category") {
                CategoryPicker(selected = category, onSelect = { category = it })
            }

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
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            title.trim().ifBlank { "Event" },
                            date,
                            start,
                            end,
                            category,
                            notes.trim().ifBlank { null },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    enabled = end > start,
                ) { Text("Save") }
            }
        }
    }
}
