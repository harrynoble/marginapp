package com.margin.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.components.CircleIconButton
import com.margin.app.ui.components.ColorDot
import com.margin.app.ui.components.Hairline
import com.margin.app.ui.components.Haptics
import com.margin.app.ui.components.PrimaryButton
import com.margin.app.ui.components.ProgressRing
import com.margin.app.ui.components.SecondaryButton
import com.margin.app.ui.components.labelFor
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor

/** What the Now card is describing. */
sealed interface NowSubject {
    /** Something you do: a task, review, build session, break or leisure. */
    data class Work(val block: ScheduleBlock) : NowSubject

    /** Something that happens to you: a meal, travel, an event, free time. */
    data class Fixed(val block: ScheduleBlock) : NowSubject

    /** At college. Shown as one thing, never as the timetable. */
    data class AtCollege(val college: TimelineItem.College) : NowSubject

    /** Nothing scheduled this minute, or outside waking hours. */
    data class Open(val outsideHours: Boolean, val wakeMinute: Int, val freeUntil: Int?) : NowSubject
}

/**
 * The answer to "what should I be doing now", and the only place in the app with a title
 * this large. A ring shows how far through the current block you are.
 */
@Composable
fun NowCard(
    subject: NowSubject,
    nowMinute: Int,
    use24Hour: Boolean,
    nextLabel: String?,
    onStart: (ScheduleBlock) -> Unit,
    onFinish: (ScheduleBlock) -> Unit,
    onPause: (ScheduleBlock) -> Unit,
    onSkip: (ScheduleBlock) -> Unit,
    onMore: (ScheduleBlock) -> Unit,
    onRebuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val view = LocalView.current

    val content = describe(subject, nowMinute, use24Hour, nextLabel)
    val accent: Color = when (subject) {
        is NowSubject.Work -> railFor(subject.block.type, subject.block.category)
        is NowSubject.Fixed -> railFor(subject.block.type, subject.block.category)
        is NowSubject.AtCollege -> accents.indigo
        is NowSubject.Open -> colors.tint
    }

    Column(
        modifier = modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = MarginShape.hero,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(MarginShape.hero)
            .background(colors.surface)
            .padding(Space.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(color = accent, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = content.eyebrow,
                        style = AppleType.footnoteEmphasized,
                        color = accent,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = content.title,
                    style = AppleType.title1,
                    color = colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = content.detail,
                    style = AppleType.subheadline,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.progress != null && content.remaining != null) {
                Spacer(Modifier.width(Space.m))
                ProgressRing(
                    progress = content.progress,
                    color = accent,
                    stroke = 7.dp,
                    modifier = Modifier.size(72.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = MarginTime.formatDurationShort(content.remaining),
                            style = AppleType.headline.copy(fontFeatureSettings = "tnum"),
                            color = colors.label,
                            maxLines = 1,
                        )
                        Text(text = "left", style = AppleType.caption2, color = colors.secondaryLabel)
                    }
                }
            }
        }

        when (subject) {
            is NowSubject.Work -> {
                val block = subject.block
                val running = block.status == BlockStatus.ACTIVE
                Spacer(Modifier.height(Space.xl))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        text = when {
                            running -> "Finish"
                            block.type == BlockType.BREAK -> "Start break"
                            else -> "Start"
                        },
                        icon = if (running) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                        onClick = {
                            if (running) {
                                Haptics.confirm(view)
                                onFinish(block)
                            } else {
                                onStart(block)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (running) {
                        CircleIconButton(
                            icon = Icons.Rounded.Pause,
                            contentDescription = "Pause",
                            onClick = { onPause(block) },
                        )
                    } else {
                        CircleIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Skip",
                            onClick = { onSkip(block) },
                        )
                    }
                    CircleIconButton(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More options",
                        onClick = { onMore(block) },
                    )
                }
            }

            is NowSubject.Open -> if (!subject.outsideHours) {
                Spacer(Modifier.height(Space.l))
                SecondaryButton(
                    text = "Rebuild the day",
                    icon = Icons.Rounded.Refresh,
                    onClick = onRebuild,
                    height = 44.dp,
                )
            }

            else -> Unit
        }

        if (nextLabel != null && subject !is NowSubject.Open) {
            Spacer(Modifier.height(Space.l))
            Hairline()
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Next", style = AppleType.footnoteEmphasized, color = colors.secondaryLabel)
                Spacer(Modifier.width(Space.s))
                Text(
                    text = nextLabel,
                    style = AppleType.footnote,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class NowContent(
    val eyebrow: String,
    val title: String,
    val detail: String,
    val progress: Float?,
    val remaining: Int?,
)

private fun describe(
    subject: NowSubject,
    nowMinute: Int,
    use24Hour: Boolean,
    nextLabel: String?,
): NowContent {
    fun range(start: Int, end: Int) =
        MarginTime.formatTime(start, use24Hour) + " – " + MarginTime.formatTime(end, use24Hour)

    fun progress(start: Int, end: Int) =
        if (end <= start) 0f else ((nowMinute - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    return when (subject) {
        is NowSubject.Work -> {
            val b = subject.block
            NowContent(
                eyebrow = if (b.status == BlockStatus.ACTIVE) "IN PROGRESS" else "NOW · " + labelFor(b.type).uppercase(),
                title = b.title,
                detail = listOfNotNull(range(b.start, b.end), b.subtitle).joinToString("  ·  "),
                progress = progress(b.start, b.end),
                remaining = (b.end - nowMinute).coerceAtLeast(0),
            )
        }

        is NowSubject.Fixed -> {
            val b = subject.block
            NowContent(
                eyebrow = "NOW · " + labelFor(b.type).uppercase(),
                title = if (b.type == BlockType.FREE) "Free time" else b.title,
                detail = "Until " + MarginTime.formatTime(b.end, use24Hour),
                progress = progress(b.start, b.end),
                remaining = (b.end - nowMinute).coerceAtLeast(0),
            )
        }

        is NowSubject.AtCollege -> {
            val c = subject.college
            val period = c.periodAt(nowMinute)
            NowContent(
                eyebrow = "AT COLLEGE",
                title = "College",
                detail = if (period != null) {
                    period.title + " · until " + MarginTime.formatTime(period.end, use24Hour)
                } else {
                    "Until " + MarginTime.formatTime(c.end, use24Hour)
                },
                progress = progress(c.start, c.end),
                remaining = (c.end - nowMinute).coerceAtLeast(0),
            )
        }

        is NowSubject.Open -> NowContent(
            eyebrow = if (subject.outsideHours) "AHEAD" else "NOW",
            title = when {
                subject.outsideHours -> "Day starts at " + MarginTime.formatTime(subject.wakeMinute, use24Hour)
                subject.freeUntil != null -> "Free until " + MarginTime.formatTime(subject.freeUntil, use24Hour)
                else -> "Nothing planned"
            },
            detail = nextLabel?.let { "First up: $it" } ?: "The rest of the day is open.",
            progress = null,
            remaining = null,
        )
    }
}
