package com.margin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.TimeGutterStyle
import com.margin.app.ui.theme.railFor

fun iconFor(type: BlockType): ImageVector = when (type) {
    BlockType.CLASS -> Icons.Outlined.School
    BlockType.TASK -> Icons.Outlined.TaskAlt
    BlockType.REVIEW -> Icons.Outlined.MenuBook
    BlockType.BUILD -> Icons.Outlined.Build
    BlockType.BREAK -> Icons.Outlined.Coffee
    BlockType.MEAL -> Icons.Outlined.Restaurant
    BlockType.COMMUTE -> Icons.Outlined.DirectionsWalk
    BlockType.DECOMPRESS -> Icons.Outlined.SelfImprovement
    BlockType.LEISURE -> Icons.Outlined.Weekend
    BlockType.EVENT -> Icons.Outlined.Event
    BlockType.ROUTINE -> Icons.Outlined.Repeat
    BlockType.SLEEP -> Icons.Outlined.Bedtime
    BlockType.FREE -> Icons.Outlined.Schedule
}

fun labelFor(type: BlockType): String = when (type) {
    BlockType.CLASS -> "Class"
    BlockType.TASK -> "Task"
    BlockType.REVIEW -> "Review"
    BlockType.BUILD -> "Build"
    BlockType.BREAK -> "Break"
    BlockType.MEAL -> "Meal"
    BlockType.COMMUTE -> "Travel"
    BlockType.DECOMPRESS -> "Transition"
    BlockType.LEISURE -> "Leisure"
    BlockType.EVENT -> "Event"
    BlockType.ROUTINE -> "Routine"
    BlockType.SLEEP -> "Sleep"
    BlockType.FREE -> "Free"
}

/**
 * One line of the day. The time sits in a fixed-width gutter so every start time lines up in
 * a column, which is what makes a long day scannable without reading a single word.
 */
@Composable
fun BlockRow(
    block: ScheduleBlock,
    use24Hour: Boolean,
    modifier: Modifier = Modifier,
    isNow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val done = block.status == BlockStatus.DONE
    val skipped = block.status == BlockStatus.SKIPPED
    val dim = done || skipped || block.type == BlockType.SLEEP
    val rail = railFor(block.type, block.category)

    val titleColor = when {
        skipped -> MaterialTheme.colorScheme.onSurfaceVariant
        dim -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (isNow) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                },
            )
            .padding(vertical = Space.s, horizontal = if (isNow) Space.s else 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = MarginTime.formatTime(block.start, use24Hour),
            style = TimeGutterStyle,
            color = if (isNow) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .width(if (use24Hour) 46.dp else 66.dp)
                .padding(top = 2.dp),
        )

        Box(
            modifier = Modifier
                .padding(end = Space.m, top = 3.dp)
                .width(3.dp)
                .heightIn(min = 18.dp)
                .height(if (block.subtitle != null) 34.dp else 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (dim) MaterialTheme.colorScheme.outlineVariant else rail),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (skipped) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                add(labelFor(block.type))
                block.subtitle?.let { add(it) }
                if (skipped) add("skipped")
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(Space.s))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MarginTime.formatDurationShort(block.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (block.locked) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(12.dp),
                    )
                }
                if (done) {
                    Icon(
                        imageVector = Icons.Outlined.TaskAlt,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(13.dp),
                    )
                }
            }
        }
    }
}
