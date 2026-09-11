package com.margin.app.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.ui.RegisterAddAction
import com.margin.app.ui.components.BlockActionCallbacks
import com.margin.app.ui.components.BlockActionsSheet
import com.margin.app.ui.components.CircleIconButton
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.GlassTextButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor
import com.margin.app.ui.today.Timeline
import com.margin.app.ui.today.TimelineItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The shape of a day, drawn the way Calendar draws it: an hour grid with blocks placed at their
 * real times and heights. Free time is simply empty grid. College is one block. Tap an empty
 * stretch to put an event there.
 */
@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backdrop = rememberGlassBackdrop()
    var sheetBlock by remember { mutableStateOf<ScheduleBlock?>(null) }
    var addAt by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    RegisterAddAction {
        addAt = null
        adding = true
    }

    val callbacks = remember(viewModel) {
        BlockActionCallbacks(
            onStart = { viewModel.start(it) },
            onComplete = { viewModel.complete(it) },
            onSkip = { id, resolution -> viewModel.skip(id, resolution) },
            onExtend = { id, minutes -> viewModel.extend(id, minutes) },
            onMove = { id, minute -> viewModel.move(id, minute) },
            onMoveToTomorrow = { viewModel.moveToTomorrow(it) },
            onTogglePin = { id, locked -> viewModel.togglePin(id, locked) },
            onOpenTask = onOpenTask,
        )
    }

    val date = state.selectedDate
    val summary = state.week.firstOrNull { it.date == date }

    LargeTitleScreen(
        title = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
        eyebrow = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())),
        backdrop = backdrop,
        modifier = modifier,
        actions = {
            if (!state.isToday) {
                GlassTextButton(backdrop = backdrop, text = "Today", onClick = { viewModel.select(LocalDate.now()) })
            }
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.Rounded.Refresh,
                contentDescription = "Rebuild this day",
                onClick = { viewModel.replan() },
            )
        },
    ) {
        item(key = "week") {
            WeekStrip(
                state = state,
                onSelect = viewModel::select,
                onShift = viewModel::shiftWeek,
            )
        }
        item(key = "summary") {
            Text(
                text = listOf(
                    MarginTime.formatDuration(summary?.classMinutes ?: 0) + " college",
                    MarginTime.formatDuration(summary?.workMinutes ?: 0) + " of work",
                    MarginTime.formatDuration(summary?.freeMinutes ?: 0) + " free",
                ).joinToString("  ·  "),
                style = AppleType.footnote,
                color = MarginTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.s),
            )
        }
        item(key = "grid") {
            DayGrid(
                blocks = state.blocks,
                isToday = state.isToday,
                nowMinute = state.nowMinute,
                use24Hour = state.use24Hour,
                onBlockClick = { sheetBlock = it },
                onEmptyClick = { minute ->
                    addAt = minute
                    adding = true
                },
            )
        }
    }

    sheetBlock?.let { block ->
        BlockActionsSheet(
            block = block,
            use24Hour = state.use24Hour,
            callbacks = callbacks,
            onDismiss = { sheetBlock = null },
        )
    }

    if (adding) {
        AddEventSheet(
            date = date,
            use24Hour = state.use24Hour,
            initialStart = addAt,
            onDismiss = { adding = false },
            onSave = { title, day, start, end, category, notes ->
                adding = false
                viewModel.addEvent(title, day, start, end, category, notes)
            },
        )
    }
}

/** The week above the grid, in Calendar style: today in the tint colour, selection filled. */
@Composable
private fun WeekStrip(
    state: PlanUiState,
    onSelect: (LocalDate) -> Unit,
    onShift: (Long) -> Unit,
) {
    val colors = MarginTheme.colors
    val today = LocalDate.now()
    Column(modifier = Modifier.padding(horizontal = Space.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.weekStart.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())),
                style = AppleType.headline,
                color = colors.label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Previous week",
                onClick = { onShift(-1) },
                size = 34.dp,
                tint = colors.tint,
            )
            Spacer(Modifier.width(Space.s))
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Next week",
                onClick = { onShift(1) },
                size = 34.dp,
                tint = colors.tint,
            )
        }
        Spacer(Modifier.height(Space.m))
        Row(modifier = Modifier.fillMaxWidth()) {
            state.week.forEach { day ->
                val selected = day.date == state.selectedDate
                val isToday = day.date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MarginShape.tile)
                        .clickable { onSelect(day.date) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = AppleType.caption2,
                        color = if (isToday && !selected) colors.tint else colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    selected && isToday -> colors.tint
                                    selected -> colors.label
                                    else -> Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.date.dayOfMonth.toString(),
                            style = AppleType.title3.copy(fontFeatureSettings = "tnum"),
                            color = when {
                                selected -> if (colors.isDark && !isToday) Color.Black else Color.White
                                isToday -> colors.tint
                                else -> colors.label
                            },
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (day.workMinutes > 0) colors.tertiaryLabel else Color.Transparent),
                    )
                }
            }
        }
    }
}

private val HourHeight: Dp = 72.dp

/** Blocks keep their true height; below this they switch to a single line, as Calendar does. */
private val CompactBelow: Dp = 44.dp
private val MinBlockHeight: Dp = 14.dp
private val GutterWidth: Dp = 58.dp

@Composable
private fun DayGrid(
    blocks: List<ScheduleBlock>,
    isToday: Boolean,
    nowMinute: Int,
    use24Hour: Boolean,
    onBlockClick: (ScheduleBlock) -> Unit,
    onEmptyClick: (Int) -> Unit,
) {
    val colors = MarginTheme.colors
    val density = LocalDensity.current

    val visible = blocks.filter { it.type != BlockType.SLEEP && it.type != BlockType.FREE }
    val items = Timeline.collapse(visible)

    val firstHour = ((items.minOfOrNull { it.start } ?: (7 * 60)) / 60).coerceAtMost(7)
    val gridStart = firstHour * 60
    val gridEnd = 24 * 60
    val perMinute = HourHeight / 60f
    val totalHeight = perMinute * (gridEnd - gridStart).toFloat()

    fun y(minute: Int): Dp = perMinute * (minute - gridStart).coerceAtLeast(0).toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.m)
            .height(totalHeight + 16.dp)
            .pointerInput(gridStart, items) {
                detectTapGestures { offset ->
                    val minutesFromTop = offset.y / with(density) { perMinute.toPx() }
                    val minute = gridStart + minutesFromTop.roundToInt()
                    // Only empty grid opens a new event; a tap on college or a block does not.
                    if (items.none { minute in it.start until it.end }) {
                        onEmptyClick(((minute / 15) * 15).coerceIn(0, 23 * 60 + 45))
                    }
                }
            },
    ) {
        // Hour lines and labels.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight),
        ) {
            val lineStart = (GutterWidth - 4.dp).toPx()
            for (hour in firstHour..24) {
                val yPx = (perMinute * ((hour * 60 - gridStart).toFloat())).toPx()
                drawLine(
                    color = colors.separator,
                    start = Offset(lineStart, yPx),
                    end = Offset(size.width, yPx),
                    strokeWidth = 1f,
                )
            }
        }
        for (hour in firstHour until 24) {
            Text(
                text = hourLabel(hour, use24Hour),
                style = AppleType.caption2.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryLabel,
                modifier = Modifier
                    .offset(x = Space.gutter, y = y(hour * 60) - 7.dp)
                    .width(GutterWidth - Space.gutter - 6.dp),
            )
        }

        // Blocks.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GutterWidth, end = Space.gutter),
        ) {
            items.forEach { item ->
                val top = y(item.start)
                // True to the clock, so neighbouring blocks never overlap; 2dp keeps them apart.
                val height = (perMinute * (item.end - item.start).toFloat() - 2.dp).coerceAtLeast(MinBlockHeight)
                val compact = height < CompactBelow
                when (item) {
                    is TimelineItem.College -> GridBlock(
                        title = "College",
                        detail = "${item.classCount} ${if (item.classCount == 1) "class" else "classes"}",
                        time = MarginTime.formatTime(item.start, use24Hour) + " – " +
                            MarginTime.formatTime(item.end, use24Hour),
                        color = railFor(BlockType.CLASS, Category.ACADEMICS),
                        faded = false,
                        struck = false,
                        compact = compact,
                        modifier = Modifier
                            .offset(y = top)
                            .fillMaxWidth()
                            .height(height),
                        onClick = null,
                    )

                    is TimelineItem.Single -> {
                        val block = item.block
                        GridBlock(
                            title = block.title,
                            detail = block.subtitle,
                            time = MarginTime.formatTime(block.start, use24Hour),
                            color = railFor(block.type, block.category),
                            faded = block.status == BlockStatus.DONE || block.status == BlockStatus.SKIPPED,
                            struck = block.status == BlockStatus.SKIPPED,
                            compact = compact,
                            modifier = Modifier
                                .offset(y = top)
                                .fillMaxWidth()
                                .height(height),
                            onClick = { onBlockClick(block) },
                        )
                    }
                }
            }
        }

        // Now line.
        if (isToday && nowMinute in gridStart until gridEnd) {
            Row(
                modifier = Modifier
                    .offset(y = y(nowMinute) - 5.dp)
                    .fillMaxWidth()
                    .padding(start = GutterWidth - 5.dp, end = Space.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.nowLine),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(colors.nowLine),
                )
            }
        }
    }
}

@Composable
private fun GridBlock(
    title: String,
    detail: String?,
    time: String,
    color: Color,
    faded: Boolean,
    struck: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val colors = MarginTheme.colors
    Row(
        modifier = modifier
            .alpha(if (faded) 0.55f else 1f)
            .clip(if (compact) MarginShape.small else MarginShape.block)
            .background(color.copy(alpha = if (colors.isDark) 0.30f else 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color),
        )
        if (compact) {
            // A short block gets one line: the title, then its time, Calendar style.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = time,
                    style = AppleType.caption1,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = title,
                    style = AppleType.footnoteEmphasized,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                )
                Text(
                    text = listOfNotNull(time, detail).joinToString(" · "),
                    style = AppleType.caption1,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun hourLabel(hour: Int, use24Hour: Boolean): String = when {
    use24Hour -> "%02d:00".format(hour)
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "Noon"
    else -> "${hour - 12} PM"
}
