package com.margin.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.OptionChips
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.StatTile
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/**
 * The evening review. Three questions at most and every one optional: the point is to decide
 * what carries forward, not to fill in a form.
 */
@Composable
fun CheckInSheet(
    blocks: List<ScheduleBlock>,
    onDismiss: () -> Unit,
    onSave: (energy: Int?, note: String?, carryForward: Boolean) -> Unit,
) {
    val colors = MarginTheme.colors
    var energy by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var carryForward by remember { mutableStateOf(true) }

    val finished = blocks.filter { it.status == BlockStatus.DONE && it.type != BlockType.SLEEP }
    val skipped = blocks.filter { it.status == BlockStatus.SKIPPED }
    val unfinished = blocks.filter { it.status == BlockStatus.PLANNED && it.type.isWork }
    val worked = finished
        .filter { it.type.isWork }
        .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }

    MarginSheet(
        onDismiss = onDismiss,
        title = "Your Day",
        trailingText = "Save",
        onTrailing = { onSave(energy, note.trim().ifBlank { null }, carryForward) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                value = MarginTime.formatDurationShort(worked),
                label = "Worked",
                accent = colors.positive,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = finished.size.toString(),
                label = "Finished",
                accent = colors.tint,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = skipped.size.toString(),
                label = "Skipped",
                accent = colors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
        }

        if (finished.isNotEmpty()) {
            GroupedSection(header = "Finished") {
                finished.take(8).forEachIndexed { index, block ->
                    if (index > 0) RowSeparator(inset = 52.dp)
                    GroupedRow(
                        title = block.title,
                        leading = {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.positive,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                    )
                }
            }
        }

        if (skipped.isNotEmpty()) {
            GroupedSection(header = "Skipped") {
                skipped.take(6).forEachIndexed { index, block ->
                    if (index > 0) RowSeparator(inset = 52.dp)
                    GroupedRow(
                        title = block.title,
                        titleColor = colors.secondaryLabel,
                        leading = {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = colors.tertiaryLabel,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                    )
                }
            }
        }

        GroupedSection(header = "How was your energy") {
            OptionChips(
                options = EnergyLabels.indices.toList(),
                selected = energy?.minus(1),
                label = { EnergyLabels[it] },
                onSelect = { index -> energy = if (energy == index + 1) null else index + 1 },
            )
        }

        GroupedSection(header = "Anything unexpected") {
            FormTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Optional",
                singleLine = false,
                minLines = 3,
            )
        }

        if (unfinished.isNotEmpty()) {
            GroupedSection(
                modifier = Modifier.padding(top = Space.xl),
                footer = "${unfinished.size} still planned for today.",
            ) {
                GroupedRow(
                    title = "Carry unfinished work forward",
                    trailing = { IosSwitch(checked = carryForward, onCheckedChange = { carryForward = it }) },
                )
            }
        }
    }
}

private val EnergyLabels = listOf("Drained", "Low", "Okay", "Good", "Sharp")
