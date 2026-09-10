package com.margin.app.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.AccentRail
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.ProportionBar
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.labelFor
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor

/**
 * The answer to "what should I be doing now". It is the largest thing on the screen and it
 * is the only place in the app that uses display type.
 */
@Composable
fun NowCard(
    block: ScheduleBlock,
    nowMinute: Int,
    use24Hour: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onComplete: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = block.status == BlockStatus.ACTIVE
    val elapsed = (nowMinute - block.start).coerceAtLeast(0)
    val remaining = (block.end - nowMinute).coerceAtLeast(0)
    val fraction by animateFloatAsState(
        targetValue = if (block.duration <= 0) 0f else (elapsed.toFloat() / block.duration).coerceIn(0f, 1f),
        label = "nowProgress",
    )

    MarginCard(modifier = modifier, contentPadding = PaddingValues(Space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                text = if (running) "In progress" else "Now",
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MarginTime.formatTime(block.start, use24Hour) + " - " +
                    MarginTime.formatTime(block.end, use24Hour),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.m))

        Row(verticalAlignment = Alignment.Top) {
            AccentRail(
                color = railFor(block.type, block.category),
                height = 52,
                modifier = Modifier.padding(top = 4.dp, end = Space.m),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = listOfNotNull(labelFor(block.type), block.subtitle).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.l))

        ProportionBar(fraction = fraction)
        Spacer(Modifier.height(Space.s))
        Text(
            text = if (remaining > 0) {
                MarginTime.formatDuration(remaining) + " left of " + MarginTime.formatDuration(block.duration)
            } else {
                "Time is up. " + MarginTime.formatDuration(block.duration) + " was planned."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.l))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (block.type.isActionable) {
                Button(
                    onClick = if (running) onComplete else onStart,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(
                        horizontal = Space.l,
                        vertical = Space.m,
                    ),
                ) {
                    if (!running) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Space.s))
                    }
                    Text(
                        text = if (running) "Finish" else if (block.type == BlockType.BREAK) "Start break" else "Start",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (running) {
                    OutlinedButton(
                        onClick = onPause,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("Pause", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text(
                    text = "This is a fixed commitment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shown when nothing is scheduled at this minute. Calm, not celebratory. */
@Composable
fun OpenNowCard(
    nextBlock: ScheduleBlock?,
    use24Hour: Boolean,
    onPlan: () -> Unit,
    modifier: Modifier = Modifier,
    outsideWakingHours: Boolean = false,
    wakeMinute: Int = 0,
) {
    val headline = when {
        outsideWakingHours -> "Day starts at " + MarginTime.formatTime(wakeMinute, use24Hour)
        nextBlock != null -> "Open until " + MarginTime.formatTime(nextBlock.start, use24Hour)
        else -> "Open"
    }
    val body = when {
        outsideWakingHours && nextBlock != null ->
            "You are outside your waking hours. First up is " + nextBlock.title + "."
        outsideWakingHours -> "You are outside your waking hours. Nothing is planned yet."
        nextBlock != null -> "Nothing is scheduled right now. Next is " + nextBlock.title + "."
        else -> "Nothing scheduled for the rest of the day."
    }

    MarginCard(modifier = modifier, contentPadding = PaddingValues(Space.xl)) {
        SectionHeader(text = if (outsideWakingHours) "Ahead" else "Now")
        Spacer(Modifier.height(Space.m))
        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.l))
        OutlinedButton(onClick = onPlan, shape = MaterialTheme.shapes.small) {
            Text("Rebuild the day", style = MaterialTheme.typography.labelLarge)
        }
    }
}
