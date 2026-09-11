package com.margin.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import com.margin.app.ui.components.CapsuleChip
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
    /**
     * Something you do: a study session, a review, build time, learning, a break or leisure.
     * [overdueMinutes] is set when it should have started a while ago and has not; [overrun]
     * when it is running and has reached its planned end.
     */
    data class Work(
        val block: ScheduleBlock,
        val overdueMinutes: Int? = null,
        val overrun: Boolean = false,
        val nextTitle: String? = null,
    ) : NowSubject

    /** Something that happens to you: travel, an event, free time. */
    data class Fixed(val block: ScheduleBlock) : NowSubject

    /** At college. Shown as one thing, never as the timetable. */
    data class AtCollege(val college: TimelineItem.College) : NowSubject

    /** Nothing scheduled this minute, or outside waking hours. */
    data class Open(val outsideHours: Boolean, val wakeMinute: Int, val freeUntil: Int?) : NowSubject
}

/** Everything the Now card can ask for. */
data class NowActions(
    val onStart: (ScheduleBlock) -> Unit,
    val onFinish: (ScheduleBlock) -> Unit,
    val onPause: (ScheduleBlock) -> Unit,
    val onSkip: (ScheduleBlock) -> Unit,
    val onMore: (ScheduleBlock) -> Unit,
    val onRebuild: () -> Unit,
    val onOut: () -> Unit,
    val onLater: (ScheduleBlock) -> Unit,
    val onSkipToday: (ScheduleBlock) -> Unit,
    val onContinue: (ScheduleBlock) -> Unit,
    val onMoveNext: (ScheduleBlock) -> Unit,
)

/**
 * The answer to "what should I be doing now", and the only place in the app with a title this
 * large. It changes shape with the moment: ready to start, running, finished its planned time,
 * or late to start.
 */
@Composable
fun NowCard(
    subject: NowSubject,
    nowMinute: Int,
    use24Hour: Boolean,
    nextLabel: String?,
    actions: NowActions,
    modifier: Modifier = Modifier,
) {
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val view = LocalView.current

    val content = describe(subject, nowMinute, use24Hour, nextLabel)
    val accent: Color = when (subject) {
        is NowSubject.Work -> when {
            subject.overdueMinutes != null -> colors.warning
            subject.overrun -> colors.positive
            else -> railFor(subject.block.type, subject.block.category)
        }
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
                    maxLines = 3,
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
            is NowSubject.Work -> WorkControls(subject, actions, view)

            is NowSubject.Open -> if (!subject.outsideHours) {
                Spacer(Modifier.height(Space.l))
                SecondaryButton(
                    text = "Rebuild the day",
                    icon = Icons.Rounded.Refresh,
                    onClick = actions.onRebuild,
                    height = 44.dp,
                )
            }

            else -> Unit
        }

        if (nextLabel != null && subject !is NowSubject.Open &&
            !(subject is NowSubject.Work && subject.overrun)
        ) {
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

@Composable
private fun WorkControls(subject: NowSubject.Work, actions: NowActions, view: android.view.View) {
    val block = subject.block
    Spacer(Modifier.height(Space.xl))
    when {
        subject.overdueMinutes != null -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = "Start now",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { actions.onStart(block) },
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(Icons.Rounded.MoreHoriz, "More options", { actions.onMore(block) })
            }
            Spacer(Modifier.height(Space.m))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                CapsuleChip(text = "I'm out", selected = false, onClick = actions.onOut)
                CapsuleChip(text = "Later", selected = false, onClick = { actions.onLater(block) })
                CapsuleChip(text = "Skip today", selected = false, onClick = { actions.onSkipToday(block) })
            }
        }

        subject.overrun -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (subject.nextTitle != null) {
                    PrimaryButton(
                        text = "Move to next",
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = {
                            Haptics.confirm(view)
                            actions.onMoveNext(block)
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrimaryButton(
                        text = "Finish",
                        icon = Icons.Rounded.CheckCircle,
                        onClick = {
                            Haptics.confirm(view)
                            actions.onFinish(block)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(text = "Continue", onClick = { actions.onContinue(block) })
            }
            if (subject.nextTitle != null) {
                Spacer(Modifier.height(Space.s))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    CapsuleChip(text = "Finish without moving on", selected = false, onClick = { actions.onFinish(block) })
                }
            }
        }

        block.status == BlockStatus.PAUSED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = "Resume",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { actions.onStart(block) },
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(Icons.Rounded.CheckCircle, "Finish", {
                    Haptics.confirm(view)
                    actions.onFinish(block)
                })
                CircleIconButton(Icons.Rounded.MoreHoriz, "More options", { actions.onMore(block) })
            }
        }

        else -> {
            val running = block.status == BlockStatus.ACTIVE
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            actions.onFinish(block)
                        } else {
                            actions.onStart(block)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    CircleIconButton(Icons.Rounded.Pause, "Pause", { actions.onPause(block) })
                } else {
                    CircleIconButton(Icons.Rounded.SkipNext, "Skip", { actions.onSkip(block) })
                }
                CircleIconButton(Icons.Rounded.MoreHoriz, "More options", { actions.onMore(block) })
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
            val kind = listOfNotNull(labelFor(b.type).uppercase(), b.academicType?.label?.uppercase())
                .joinToString(" · ")
            val planned = if (b.plannedMinutes > 0) b.plannedMinutes else b.duration
            when {
                subject.overdueMinutes != null -> NowContent(
                    eyebrow = "DUE ${subject.overdueMinutes} MIN AGO",
                    title = b.title,
                    detail = "Are you out? Start now, or tell me when you'll get to it.",
                    progress = null,
                    remaining = null,
                )
                subject.overrun -> NowContent(
                    eyebrow = "${MarginTime.formatDuration(planned + b.extendedMinutes).uppercase()} COMPLETE",
                    title = subject.nextTitle?.let { "Move to $it?" } ?: b.title,
                    detail = if (subject.nextTitle != null) {
                        "Or keep going with ${b.title}. Extra time is kept."
                    } else {
                        "Keep going, or finish here. Extra time is kept."
                    },
                    progress = 1f,
                    remaining = 0,
                )
                b.status == BlockStatus.PAUSED -> NowContent(
                    eyebrow = "PAUSED · $kind",
                    title = b.title,
                    detail = MarginTime.formatDuration(b.elapsedMinutes) + " done of " + MarginTime.formatDuration(planned),
                    progress = if (planned <= 0) 0f else (b.elapsedMinutes.toFloat() / planned).coerceIn(0f, 1f),
                    remaining = (planned - b.elapsedMinutes).coerceAtLeast(0),
                )
                else -> NowContent(
                    eyebrow = if (b.status == BlockStatus.ACTIVE) "IN PROGRESS · $kind" else "NOW · $kind",
                    title = b.title,
                    detail = listOfNotNull(range(b.start, b.end), b.subtitle).joinToString("  ·  "),
                    progress = progress(b.start, b.end),
                    remaining = (b.end - nowMinute).coerceAtLeast(0),
                )
            }
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
            detail = nextLabel?.let { "First up: $it" } ?: "The rest of the day is yours.",
            progress = null,
            remaining = null,
        )
    }
}
