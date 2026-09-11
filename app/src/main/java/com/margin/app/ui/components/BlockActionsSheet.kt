package com.margin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.automirrored.rounded.NextPlan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipResolution
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor

data class BlockActionCallbacks(
    val onStart: (Long) -> Unit = {},
    val onComplete: (Long) -> Unit = {},
    val onSkip: (Long, SkipResolution) -> Unit = { _, _ -> },
    val onExtend: (Long, Int) -> Unit = { _, _ -> },
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onMoveToTomorrow: (Long) -> Unit = {},
    val onTogglePin: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpenTask: (Long) -> Unit = {},
)

/**
 * Everything that can be done to one block. Skipping asks what should happen to the work
 * rather than silently deleting it, which is the difference between a planner and a list.
 */
@Composable
fun BlockActionsSheet(
    block: ScheduleBlock,
    use24Hour: Boolean,
    callbacks: BlockActionCallbacks,
    onDismiss: () -> Unit,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    var askingSkip by remember { mutableStateOf(false) }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (askingSkip) "Skip" else null,
        leadingText = if (askingSkip) "Back" else "Close",
        onLeading = if (askingSkip) ({ askingSkip = false }) else onDismiss,
    ) {
        if (askingSkip) {
            Text(
                text = "It stays on your list. Choose where it goes.",
                style = AppleType.subheadline,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.s),
            )
            GroupedSection {
                GroupedRow(title = "Later today", onClick = {
                    callbacks.onSkip(block.id, SkipResolution.LATER_TODAY); onDismiss()
                })
                RowSeparator()
                GroupedRow(title = "Tomorrow", onClick = {
                    callbacks.onSkip(block.id, SkipResolution.TOMORROW); onDismiss()
                })
                RowSeparator()
                GroupedRow(title = "Leave it off today", onClick = {
                    callbacks.onSkip(block.id, SkipResolution.DROP_TODAY); onDismiss()
                })
            }
            GroupedSection(modifier = Modifier.padding(top = Space.xl)) {
                GroupedRow(
                    title = "Remove the task",
                    titleColor = colors.destructive,
                    onClick = { callbacks.onSkip(block.id, SkipResolution.REMOVED); onDismiss() },
                )
            }
            return@MarginSheet
        }

        // Header: what this is and when.
        Row(
            modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryRail(color = railFor(block.type, block.category), height = 44.dp)
            Spacer(Modifier.width(Space.m))
            Column {
                Text(text = block.title, style = AppleType.title2, color = colors.label)
                Text(
                    text = MarginTime.formatTime(block.start, use24Hour) + " – " +
                        MarginTime.formatTime(block.end, use24Hour) + "  ·  " +
                        MarginTime.formatDuration(block.duration),
                    style = AppleType.subheadline,
                    color = colors.secondaryLabel,
                )
            }
        }

        val actionable = block.type.isActionable && block.status != BlockStatus.DONE
        if (actionable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                val running = block.status == BlockStatus.ACTIVE
                PrimaryButton(
                    text = if (running) "Finish" else "Start",
                    icon = if (running) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                    onClick = {
                        if (running) {
                            Haptics.confirm(view); callbacks.onComplete(block.id)
                        } else {
                            callbacks.onStart(block.id)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!running) {
                    SecondaryButton(
                        text = "Done",
                        onClick = { Haptics.confirm(view); callbacks.onComplete(block.id); onDismiss() },
                    )
                }
            }
        }

        block.reason?.let { reason ->
            GroupedSection(header = "Why it is here") {
                Text(
                    text = reason,
                    style = AppleType.body,
                    color = colors.label,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = 14.dp),
                )
            }
        }

        if (actionable) {
            FormHeader("Adjust", modifier = Modifier.padding(start = Space.gutter))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                listOf(15, 30, 45).forEach { extra ->
                    CapsuleChip(text = "+$extra min", selected = false, onClick = {
                        callbacks.onExtend(block.id, extra); onDismiss()
                    })
                }
                CapsuleChip(text = "Push an hour", selected = false, onClick = {
                    callbacks.onMove(block.id, block.start + 60); onDismiss()
                })
            }
        }

        GroupedSection(modifier = Modifier.padding(top = Space.xl)) {
            GroupedRow(
                title = if (block.locked) "Unpin from this time" else "Pin to this time",
                leading = { IconTile(Icons.Rounded.PushPin, MarginTheme.accents.orange) },
                onClick = { callbacks.onTogglePin(block.id, !block.locked); onDismiss() },
            )
            if (actionable) {
                RowSeparator(inset = 58.dp)
                GroupedRow(
                    title = "Move to tomorrow",
                    leading = { IconTile(Icons.AutoMirrored.Rounded.NextPlan, MarginTheme.accents.blue) },
                    onClick = { callbacks.onMoveToTomorrow(block.id); onDismiss() },
                )
                RowSeparator(inset = 58.dp)
                GroupedRow(
                    title = "Skip",
                    leading = { IconTile(Icons.Rounded.SkipNext, MarginTheme.accents.gray) },
                    showChevron = true,
                    onClick = { askingSkip = true },
                )
            }
            block.taskId?.let { taskId ->
                RowSeparator(inset = 58.dp)
                GroupedRow(
                    title = "Open task",
                    leading = { IconTile(Icons.Rounded.Checklist, MarginTheme.accents.indigo) },
                    showChevron = true,
                    onClick = { callbacks.onOpenTask(taskId); onDismiss() },
                )
            }
        }
    }
}
