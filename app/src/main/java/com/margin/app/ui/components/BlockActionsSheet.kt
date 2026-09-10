package com.margin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.ui.theme.Space

data class BlockActionCallbacks(
    val onStart: (Long) -> Unit = {},
    val onComplete: (Long) -> Unit = {},
    val onSkip: (Long, SkipResolution) -> Unit = { _, _ -> },
    val onExtend: (Long, Int) -> Unit = { _, _ -> },
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onMoveToTomorrow: (Long) -> Unit = {},
    val onTogglePin: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpenTask: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
)

/**
 * Everything that can be done to one block. Skipping asks what should happen to the work
 * rather than silently deleting it, which is the difference between a planner and a list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockActionsSheet(
    block: ScheduleBlock,
    use24Hour: Boolean,
    nowMinute: Int,
    callbacks: BlockActionCallbacks,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var askingSkip by remember { mutableStateOf(false) }

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
                .navigationBarsPadding(),
        ) {
            SheetGrabber()
            Spacer(Modifier.height(Space.l))

            Text(
                text = block.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = MarginTime.formatTime(block.start, use24Hour) + " - " +
                    MarginTime.formatTime(block.end, use24Hour) + " · " +
                    MarginTime.formatDuration(block.duration) + " · " + labelFor(block.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (block.reason != null) {
                Spacer(Modifier.height(Space.m))
                MarginCard(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = false,
                    contentPadding = PaddingValues(Space.m),
                ) {
                    Text(
                        text = "Why here",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = block.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            if (askingSkip) {
                Text(
                    text = "Skipped. What should happen to it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Space.m))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SkipOption("Move later today") {
                        callbacks.onSkip(block.id, SkipResolution.LATER_TODAY); onDismiss()
                    }
                    SkipOption("Move to tomorrow") {
                        callbacks.onSkip(block.id, SkipResolution.TOMORROW); onDismiss()
                    }
                    SkipOption("Leave it off today") {
                        callbacks.onSkip(block.id, SkipResolution.DROP_TODAY); onDismiss()
                    }
                    SkipOption("Remove the task entirely") {
                        callbacks.onSkip(block.id, SkipResolution.REMOVED); onDismiss()
                    }
                }
                Spacer(Modifier.height(Space.m))
                OutlinedButton(
                    onClick = { askingSkip = false },
                    shape = MaterialTheme.shapes.small,
                ) { Text("Back") }
            } else {
                if (block.type.isActionable && block.status != BlockStatus.DONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Button(
                            onClick = {
                                if (block.status == BlockStatus.ACTIVE) callbacks.onComplete(block.id)
                                else callbacks.onStart(block.id)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(
                                imageVector = if (block.status == BlockStatus.ACTIVE) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.s))
                            Text(if (block.status == BlockStatus.ACTIVE) "Finish" else "Start")
                        }
                        OutlinedButton(
                            onClick = { callbacks.onComplete(block.id); onDismiss() },
                            shape = MaterialTheme.shapes.small,
                        ) { Text("Done") }
                    }
                    Spacer(Modifier.height(Space.m))
                }

                SectionHeader("Adjust")
                Spacer(Modifier.height(Space.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    listOf(15, 30, 45).forEach { extra ->
                        AssistChip(
                            onClick = { callbacks.onExtend(block.id, extra); onDismiss() },
                            label = { Text("+$extra min") },
                            shape = MaterialTheme.shapes.small,
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                    AssistChip(
                        onClick = { callbacks.onMove(block.id, block.start + 60); onDismiss() },
                        label = { Text("Push 1 hour") },
                        shape = MaterialTheme.shapes.small,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    AssistChip(
                        onClick = { callbacks.onMoveToTomorrow(block.id); onDismiss() },
                        label = { Text("Tomorrow") },
                        shape = MaterialTheme.shapes.small,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }

                Spacer(Modifier.height(Space.m))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.s))

                SheetAction(
                    icon = Icons.Outlined.PushPin,
                    label = if (block.locked) "Unpin from this time" else "Pin to this time",
                ) {
                    callbacks.onTogglePin(block.id, !block.locked); onDismiss()
                }

                if (block.type.isActionable && block.status == BlockStatus.PLANNED) {
                    SheetAction(icon = Icons.Outlined.SkipNext, label = "Skip") { askingSkip = true }
                }

                block.taskId?.let { taskId ->
                    SheetAction(icon = Icons.Outlined.CheckCircle, label = "Open task") {
                        callbacks.onOpenTask(taskId); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipOption(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Space.m))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SheetGrabber(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
