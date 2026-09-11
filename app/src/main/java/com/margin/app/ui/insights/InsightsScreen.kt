package com.margin.app.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.ui.components.ColorDot
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.ProgressRing
import com.margin.app.ui.components.ProportionBar
import com.margin.app.ui.components.SegmentedControl
import com.margin.app.ui.components.StatTile
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What actually happened, in the manner of Health: one card per question, each with a coloured
 * label, one large number and a chart. No streaks and no scores to protect.
 */
@Composable
fun InsightsScreen(viewModel: InsightsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents

    LargeTitleScreen(title = "Insights", modifier = modifier) {
        item(key = "range") {
            SegmentedControl(
                options = InsightsRange.entries,
                selected = state.range,
                label = { it.label },
                onSelect = viewModel::setRange,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
        }

        if (state.plannedBlocks == 0 && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.BarChart,
                    title = "Not enough history yet",
                    body = "Finish or skip a few sessions and patterns will show up here.",
                )
            }
        } else {
            item(key = "follow") {
                val attempted = state.completedBlocks + state.skippedBlocks
                InsightCard(title = "Follow through", accent = colors.positive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (attempted == 0) "–" else "${(state.completionRate * 100).roundToInt()}%",
                                style = AppleType.numeral,
                                color = colors.label,
                            )
                            Text(
                                text = if (attempted == 0) {
                                    "Nothing finished or skipped yet"
                                } else {
                                    "${state.completedBlocks} finished · ${state.skippedBlocks} skipped · " +
                                        "${state.rescheduled} moved"
                                },
                                style = AppleType.subheadline,
                                color = colors.secondaryLabel,
                            )
                        }
                        Spacer(Modifier.width(Space.m))
                        ProgressRing(
                            progress = state.completionRate,
                            color = colors.positive,
                            stroke = 9.dp,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
            }

            item(key = "work") {
                val single = state.range == InsightsRange.TODAY
                InsightCard(title = "Work", accent = colors.tint, trailing = if (single) null else "Per day") {
                    Text(
                        text = MarginTime.formatDuration(state.totalCompletedMinutes),
                        style = AppleType.numeral,
                        color = colors.label,
                    )
                    Text(
                        text = if (single) {
                            "Done of " + MarginTime.formatDuration(state.days.sumOf { it.workMinutes }) + " planned"
                        } else {
                            "Finished on ${state.daysWithWork} of ${state.days.size} days"
                        },
                        style = AppleType.subheadline,
                        color = colors.secondaryLabel,
                    )
                    if (!single) {
                        Spacer(Modifier.height(Space.l))
                        DayChart(days = state.days)
                        Spacer(Modifier.height(Space.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            Legend(color = colors.tint, label = "Finished")
                            Legend(color = colors.fill, label = "Planned")
                        }
                    }
                }
            }

            if (state.balance.isNotEmpty()) {
                item(key = "balance") {
                    val total = state.balance.sumOf { it.minutes }
                    InsightCard(title = "Day balance", accent = accents.green) {
                        Text(text = MarginTime.formatDuration(total), style = AppleType.numeral, color = colors.label)
                        Text(
                            text = "Study, building, learning and rest, as they actually happened",
                            style = AppleType.subheadline,
                            color = colors.secondaryLabel,
                        )
                        Spacer(Modifier.height(Space.l))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(MarginShape.capsule),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            state.balance.forEach { slice ->
                                Box(
                                    modifier = Modifier
                                        .weight(slice.minutes.toFloat())
                                        .fillMaxHeight()
                                        .background(balanceColor(slice.kind)),
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.l))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.balance.forEach { slice ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ColorDot(color = balanceColor(slice.kind), size = 10.dp)
                                    Spacer(Modifier.width(Space.s))
                                    Text(slice.label, style = AppleType.subheadline, color = colors.label, modifier = Modifier.weight(1f))
                                    Text(
                                        text = MarginTime.formatDuration(slice.minutes),
                                        style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"),
                                        color = colors.secondaryLabel,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.tracks.isNotEmpty()) {
                item(key = "subjects") {
                    InsightCard(title = "Subjects", accent = accents.indigo, trailing = "Planned · done") {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            state.tracks.forEach { track -> TrackRow(track) }
                        }
                    }
                }
            }

            item(key = "learned") {
                InsightCard(title = "What Margin has learned", accent = accents.purple) {
                    if (state.estimates.isEmpty() && state.bestWindow == null) {
                        Text(
                            text = "After a few finished sessions, Margin learns how long things really take you and plans with that.",
                            style = AppleType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.bestWindow?.let { window ->
                                LearnedRow(
                                    label = "Best time to study",
                                    detail = MarginTime.formatTime(window.start, false) + " – " + MarginTime.formatTime(window.end, false),
                                )
                            }
                            state.estimates.forEach { LearnedRow(it.label, it.detail) }
                        }
                        Spacer(Modifier.height(Space.m))
                        Text(
                            text = "Planned times are adjusted by these amounts, within limits.",
                            style = AppleType.footnote,
                            color = colors.tertiaryLabel,
                        )
                    }
                }
            }

            item(key = "categories") {
                val entries = state.minutesByCategory.filter { it.second > 0 }
                val total = entries.sumOf { it.second }
                InsightCard(title = "Where the time went", accent = accents.indigo) {
                    if (total == 0) {
                        Text(
                            text = "Nothing logged in this range.",
                            style = AppleType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    } else {
                        Text(text = MarginTime.formatDuration(total), style = AppleType.numeral, color = colors.label)
                        Text(
                            text = "College and finished work",
                            style = AppleType.subheadline,
                            color = colors.secondaryLabel,
                        )
                        Spacer(Modifier.height(Space.l))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(MarginShape.capsule),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            entries.forEach { (category, minutes) ->
                                Box(
                                    modifier = Modifier
                                        .weight(minutes.toFloat())
                                        .fillMaxHeight()
                                        .background(accentFor(category)),
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.l))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            entries.forEach { (category, minutes) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ColorDot(color = accentFor(category), size = 10.dp)
                                    Spacer(Modifier.width(Space.s))
                                    Text(
                                        text = category.label,
                                        style = AppleType.subheadline,
                                        color = colors.label,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = MarginTime.formatDuration(minutes),
                                        style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"),
                                        color = colors.secondaryLabel,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "timeofday") {
                val peak = state.timeOfDay.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                InsightCard(title = "Time of day", accent = accents.orange) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        state.timeOfDay.forEach { slice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = slice.label,
                                    style = AppleType.subheadline,
                                    color = colors.label,
                                    modifier = Modifier.width(88.dp),
                                )
                                ProportionBar(
                                    fraction = slice.minutes.toFloat() / peak,
                                    color = accents.orange,
                                    height = 8.dp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = MarginTime.formatDurationShort(slice.minutes),
                                    style = AppleType.footnote.copy(fontFeatureSettings = "tnum"),
                                    color = colors.secondaryLabel,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                        }
                    }
                    state.bestPeriod?.let { period ->
                        Spacer(Modifier.height(Space.m))
                        Text(
                            text = "Most of your finished work happens in the ${period.lowercase()}.",
                            style = AppleType.footnote,
                            color = colors.secondaryLabel,
                        )
                    }
                }
            }

            item(key = "sessions") {
                Row(
                    modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        value = MarginTime.formatDurationShort(state.averageSessionMinutes),
                        label = "Avg session",
                        accent = accents.teal,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.plannedBlocks.toString(),
                        label = "Planned",
                        accent = colors.tint,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.rescheduled.toString(),
                        label = "Moved",
                        accent = accents.purple,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A Health-style card: coloured label, then whatever the card has to say. */
@Composable
private fun InsightCard(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MarginTheme.colors
    Column(
        modifier = modifier
            .padding(start = Space.gutter, end = Space.gutter, top = Space.m)
            .fillMaxWidth()
            .clip(MarginShape.card)
            .background(colors.surface)
            .padding(horizontal = Space.xl, vertical = Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AppleType.subheadlineEmphasized,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(text = trailing, style = AppleType.footnote, color = colors.secondaryLabel)
            }
        }
        Spacer(Modifier.height(Space.s))
        content()
    }
}

@Composable
private fun balanceColor(kind: BalanceKind): Color {
    val accents = MarginTheme.accents
    return when (kind) {
        BalanceKind.STUDY -> accents.indigo
        BalanceKind.BUILD -> accents.orange
        BalanceKind.LEARNING -> accents.teal
        BalanceKind.LEISURE -> accents.green
        BalanceKind.BREAKS -> accents.gray
    }
}

@Composable
private fun TrackRow(track: TrackInsight) {
    val colors = MarginTheme.colors
    val neglected = track.daysSince == null || track.daysSince >= NEGLECT_DAYS
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.name + " · " + track.track.type.label,
                style = AppleType.subheadline,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MarginTime.formatDurationShort(track.plannedMinutes) + " · " + MarginTime.formatDurationShort(track.actualMinutes),
                style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(4.dp))
        ProportionBar(
            fraction = if (track.plannedMinutes <= 0) 0f else (track.actualMinutes.toFloat() / track.plannedMinutes).coerceIn(0f, 1f),
            color = MarginTheme.accents.indigo,
            height = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (val days = track.daysSince) {
                null -> "Not studied yet"
                0 -> "Studied today"
                1 -> "Last studied yesterday"
                else -> "Last studied $days days ago"
            },
            style = AppleType.footnote,
            color = if (neglected) colors.warning else colors.secondaryLabel,
        )
    }
}

private const val NEGLECT_DAYS = 4

@Composable
private fun LearnedRow(label: String, detail: String) {
    val colors = MarginTheme.colors
    Column {
        Text(text = label, style = AppleType.subheadline, color = colors.label)
        Text(text = detail, style = AppleType.footnote, color = colors.secondaryLabel)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color = color, size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = AppleType.footnote, color = MarginTheme.colors.secondaryLabel)
    }
}

private val ChartHeight = 132.dp
private val AxisWidth = 34.dp

/**
 * Planned time as a pale capsule and finished time as a solid one in front of it, so a day
 * where the plan was ambitious and the reality was not shows at a glance. Axis on the right,
 * as Health draws it.
 */
@Composable
private fun DayChart(days: List<DayBar>) {
    val colors = MarginTheme.colors
    val peak = (days.maxOfOrNull { max(it.workMinutes, it.completedMinutes) } ?: 0).coerceAtLeast(60)
    val gap = if (days.size > 14) 3.dp else 8.dp

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight),
            ) {
                val n = days.size.coerceAtLeast(1)
                val gapPx = gap.toPx()
                val barWidth = ((size.width - gapPx * (n - 1)) / n).coerceAtLeast(1f)
                val radius = CornerRadius(min(barWidth / 2f, 6.dp.toPx()))
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))

                for (i in 0..2) {
                    val y = size.height * i / 2f
                    drawLine(
                        color = colors.separator,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = if (i < 2) dash else null,
                    )
                }
                days.forEachIndexed { index, day ->
                    val x = index * (barWidth + gapPx)
                    val planned = size.height * day.workMinutes / peak
                    val finished = size.height * day.completedMinutes / peak
                    if (planned > 0f) {
                        drawRoundRect(
                            color = colors.fill,
                            topLeft = Offset(x, size.height - planned),
                            size = Size(barWidth, planned),
                            cornerRadius = radius,
                        )
                    }
                    if (finished > 0f) {
                        drawRoundRect(
                            color = colors.tint,
                            topLeft = Offset(x, size.height - finished),
                            size = Size(barWidth, finished),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                days.forEachIndexed { index, day ->
                    val label = when {
                        days.size <= 14 -> day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        (days.size - 1 - index) % 7 == 0 -> day.date.dayOfMonth.toString()
                        else -> ""
                    }
                    Text(
                        text = label,
                        style = AppleType.caption2,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(AxisWidth)
                .height(ChartHeight),
        ) {
            AxisLabel(MarginTime.formatDurationShort(peak), Alignment.TopEnd)
            AxisLabel(MarginTime.formatDurationShort(peak / 2), Alignment.CenterEnd)
            AxisLabel("0", Alignment.BottomEnd)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.AxisLabel(text: String, alignment: Alignment) {
    Text(
        text = text,
        style = AppleType.caption2.copy(fontFeatureSettings = "tnum"),
        color = MarginTheme.colors.secondaryLabel,
        modifier = Modifier.align(alignment),
    )
}
