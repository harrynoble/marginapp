package com.margin.app.ui.today

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.LabeledField
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.theme.Space

/**
 * The evening review. Three questions at most, all skippable. The point is to decide what
 * carries forward, not to fill in a form.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheckInSheet(
    blocks: List<ScheduleBlock>,
    onDismiss: () -> Unit,
    onSave: (energy: Int?, note: String?, carryForward: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var energy by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var carryForward by remember { mutableStateOf(true) }

    val done = blocks.filter { it.status == BlockStatus.DONE }
    val skipped = blocks.filter { it.status == BlockStatus.SKIPPED }
    val unfinished = blocks.filter { it.status == BlockStatus.PLANNED && it.type.isWork }

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
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = MarginTime.formatDuration(
                    done.sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration },
                ) + " of work finished.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (done.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                SectionHeader("Completed")
                Spacer(Modifier.height(Space.xs))
                done.take(8).forEach {
                    Text(
                        text = "- " + it.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (skipped.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                SectionHeader("Skipped")
                Spacer(Modifier.height(Space.xs))
                skipped.take(6).forEach {
                    Text(
                        text = "- " + it.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))
            LabeledField("How was your energy") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    listOf(1 to "Low", 2 to "Below par", 3 to "Fine", 4 to "Good", 5 to "Sharp")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = energy == value,
                                onClick = { energy = if (energy == value) 0 else value },
                                label = { Text(label) },
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
            LabeledField("Anything unexpected") {
                MarginTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Optional",
                    singleLine = false,
                    minLines = 2,
                )
            }

            if (unfinished.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Carry the unfinished work forward",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = unfinished.size.toString() + " left on today plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = carryForward, onCheckedChange = { carryForward = it })
                }
            }

            Spacer(Modifier.height(Space.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Not now") }
                Button(
                    onClick = {
                        onSave(
                            energy.takeIf { it > 0 },
                            note.trim().ifBlank { null },
                            carryForward,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text("Save") }
            }
        }
    }
}
