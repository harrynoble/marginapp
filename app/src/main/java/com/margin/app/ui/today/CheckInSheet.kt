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
 * The end-of-day review. What happened, in real minutes; then at most three questions, every
 * one optional. The point is to decide what carries forward, not to fill in a form.
 */
@Composable
fun CheckInSheet(
    blocks: List<ScheduleBlock>,
    onDismiss: () -> Unit,
    onSave: (energy: Int?, note: String?, carryForward: Boolean, workload: Int?) -> Unit,
) {
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    var energy by remember { mutableStateOf<Int?>(null) }
    var workload by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var carryForward by remember { mutableStateOf(true) }

    val worked = blocks.filter { it.status.isWorked && it.type != BlockType.SLEEP }
    val skipped = blocks.filter { (it.status == BlockStatus.SKIPPED || it.status == BlockStatus.MISSED) && it.type.isWork }
    val rescheduled = blocks.count { it.status == BlockStatus.RESCHEDULED }
    val unfinished = blocks.filter { it.status.isOpen && it.type.isWork }

    fun minutes(filter: (ScheduleBlock) -> Boolean) =
        worked.filter(filter).sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }

    val totals = listOf(
        Triple("Study", minutes { it.isAcademic }, accents.indigo),
        Triple("Build", minutes { it.type == BlockType.BUILD }, accents.orange),
        Triple("Learning", minutes { it.type == BlockType.LEARN }, accents.teal),
        Triple("Leisure", minutes { it.type == BlockType.LEISURE }, accents.green),
    ).filter { it.second > 0 }

    MarginSheet(
        onDismiss = onDismiss,
        title = "Your Day",
        trailingText = "Save",
        onTrailing = { onSave(energy, note.trim().ifBlank { null }, carryForward, workload) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                value = worked.count { it.type.isWork }.toString(),
                label = "Completed",
                accent = colors.positive,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = skipped.size.toString(),
                label = "Skipped",
                accent = colors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = rescheduled.toString(),
                label = "Moved",
                accent = colors.tint,
                modifier = Modifier.weight(1f),
            )
        }

        if (totals.isNotEmpty()) {
            GroupedSection(header = "Time spent") {
                totals.forEachIndexed { index, (label, total, _) ->
                    if (index > 0) RowSeparator()
                    GroupedRow(title = label, value = MarginTime.formatDuration(total))
                }
            }
        }

        val finished = worked.filter { it.type.isWork }
        if (finished.isNotEmpty()) {
            GroupedSection(header = "Finished") {
                finished.take(8).forEachIndexed { index, block ->
                    if (index > 0) RowSeparator(inset = 52.dp)
                    GroupedRow(
                        title = block.title,
                        subtitle = block.academicType?.label,
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
            GroupedSection(header = "Not done", footer = "Nothing here is lost. Academic work comes back on a later day.") {
                skipped.take(6).forEachIndexed { index, block ->
                    if (index > 0) RowSeparator(inset = 52.dp)
                    GroupedRow(
                        title = block.title,
                        subtitle = block.status.label,
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

        GroupedSection(header = "Was today too much?") {
            OptionChips(
                options = WorkloadLabels.indices.toList(),
                selected = workload?.minus(1),
                label = { WorkloadLabels[it] },
                onSelect = { index -> workload = if (workload == index + 1) null else index + 1 },
            )
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
                footer = "${unfinished.size} still planned for today. Carried work is capped, so tomorrow stays realistic.",
            ) {
                GroupedRow(
                    title = "Carry unfinished work forward",
                    trailing = { IosSwitch(checked = carryForward, onCheckedChange = { carryForward = it }) },
                )
            }
        }
    }
}

private val WorkloadLabels = listOf("Too light", "About right", "Too much")
private val EnergyLabels = listOf("Drained", "Low", "Okay", "Good", "Sharp")
