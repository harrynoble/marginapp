package com.margin.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor
import com.margin.app.ui.today.TimelineItem

fun iconFor(type: BlockType): ImageVector = when (type) {
    BlockType.CLASS -> Icons.Rounded.School
    BlockType.TASK -> Icons.Rounded.TaskAlt
    BlockType.REVIEW -> Icons.AutoMirrored.Rounded.MenuBook
    BlockType.BUILD -> Icons.Rounded.Construction
    BlockType.BREAK -> Icons.Rounded.Coffee
    BlockType.MEAL -> Icons.Rounded.Restaurant
    BlockType.COMMUTE -> Icons.AutoMirrored.Rounded.DirectionsWalk
    BlockType.DECOMPRESS -> Icons.Rounded.SelfImprovement
    BlockType.LEISURE -> Icons.Rounded.Weekend
    BlockType.EVENT -> Icons.Rounded.Event
    BlockType.ROUTINE -> Icons.Rounded.Repeat
    BlockType.SLEEP -> Icons.Rounded.Bedtime
    BlockType.FREE -> Icons.Rounded.Schedule
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
 * One line of the day, calendar style: start time in a fixed tabular column, a capsule of the
 * category colour, then the title. The time column is what makes a long day scannable.
 */
@Composable
fun TimelineRow(
    block: ScheduleBlock,
    use24Hour: Boolean,
    modifier: Modifier = Modifier,
    isNow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    val done = block.status == BlockStatus.DONE
    val skipped = block.status == BlockStatus.SKIPPED
    val quiet = block.type == BlockType.FREE || block.type == BlockType.SLEEP

    TimelineLine(
        time = MarginTime.formatTime(block.start, use24Hour),
        title = block.title,
        subtitle = buildList {
            // "Build · Build" says nothing; the type only earns a place when the title is different.
            labelFor(block.type)
                .takeIf { block.type != BlockType.FREE && !it.equals(block.title, ignoreCase = true) }
                ?.let { add(it) }
            block.subtitle?.takeIf { !it.equals(block.title, ignoreCase = true) }?.let { add(it) }
            if (skipped) add("Skipped")
        }.joinToString(" · ").ifBlank { null },
        duration = MarginTime.formatDurationShort(block.duration),
        rail = if (done || skipped) colors.quaternaryLabel else railFor(block.type, block.category),
        titleColor = when {
            skipped || quiet -> colors.secondaryLabel
            done -> colors.secondaryLabel
            else -> colors.label
        },
        strike = skipped,
        isNow = isNow,
        trailingIcon = when {
            done -> Icons.Rounded.CheckCircle
            block.locked -> Icons.Rounded.Lock
            else -> null
        },
        modifier = modifier,
        onClick = onClick,
    )
}

/**
 * College, folded into one line. The person knows their timetable; the timeline only needs
 * to say they are at college, until when, and what is happening if it is happening now.
 */
@Composable
fun CollegeRow(
    college: TimelineItem.College,
    use24Hour: Boolean,
    nowMinute: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    val isNow = nowMinute in college.start until college.end
    val period = if (isNow) college.periodAt(nowMinute) else null
    val subtitle = when {
        period != null -> "Now: " + period.title
        else -> {
            val count = college.classCount
            "$count ${if (count == 1) "class" else "classes"} · until " +
                MarginTime.formatTime(college.end, use24Hour)
        }
    }
    TimelineLine(
        time = MarginTime.formatTime(college.start, use24Hour),
        title = "College",
        subtitle = subtitle,
        duration = MarginTime.formatDurationShort(college.end - college.start),
        rail = railFor(BlockType.CLASS, com.margin.app.domain.model.Category.ACADEMICS),
        titleColor = if (nowMinute >= college.end) colors.secondaryLabel else colors.label,
        strike = false,
        isNow = isNow,
        trailingIcon = null,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun TimelineLine(
    time: String,
    title: String,
    subtitle: String?,
    duration: String,
    rail: androidx.compose.ui.graphics.Color,
    titleColor: androidx.compose.ui.graphics.Color,
    strike: Boolean,
    isNow: Boolean,
    trailingIcon: ImageVector?,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val colors = MarginTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = AppleType.timeGutter,
            color = if (isNow) colors.tint else colors.secondaryLabel,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
        )
        CategoryRail(color = rail)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.body.copy(
                    fontWeight = if (isNow) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                ),
                color = titleColor,
                textDecoration = if (strike) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppleType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Space.s))
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (trailingIcon == Icons.Rounded.CheckCircle) colors.positive else colors.tertiaryLabel,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = duration,
            style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"),
            color = colors.secondaryLabel,
            maxLines = 1,
        )
    }
}
