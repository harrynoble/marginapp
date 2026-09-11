package com.margin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.margin.app.core.MarginTime
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * A text field the way iOS forms draw one: no outline, no floating label, just text and a
 * placeholder sitting in a grouped row.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    style: TextStyle = AppleType.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MarginTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 6,
        textStyle = style.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = Space.l, vertical = 14.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = style, color = colors.tertiaryLabel)
                }
                inner()
            }
        },
    )
}

/** A filled capsule field, for a single line of input outside a form. */
@Composable
fun CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val colors = MarginTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = 4,
        textStyle = AppleType.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(SmoothFieldShape)
            .background(colors.tertiaryFill)
            .padding(horizontal = Space.l, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = AppleType.body, color = colors.tertiaryLabel)
                }
                inner()
            }
        },
    )
}

private val SmoothFieldShape = com.margin.app.ui.theme.SmoothRoundedCornerShape(23.dp)

/** A form row showing a time as a pill; tapping it opens the wheel picker. */
@Composable
fun TimeRow(
    title: String,
    minute: Int,
    use24Hour: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    GroupedRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = leading,
        trailing = {
            ValuePill(text = MarginTime.formatTime(minute, use24Hour), onClick = { picking = true })
        },
        onClick = { picking = true },
    )
    if (picking) {
        TimePickerSheet(
            title = title,
            initialMinute = minute,
            use24Hour = use24Hour,
            onDismiss = { picking = false },
            onConfirm = {
                onChange(it)
                picking = false
            },
        )
    }
}

@Composable
fun TimePickerSheet(
    title: String,
    initialMinute: Int,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableIntStateOf(initialMinute) }
    MarginSheet(
        onDismiss = onDismiss,
        title = title,
        trailingText = "Done",
        onTrailing = { onConfirm(value) },
        scrollable = false,
    ) {
        Spacer(Modifier.height(Space.s))
        WheelTimePicker(
            minute = value,
            use24Hour = use24Hour,
            onChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The iOS wheel time picker: three columns that scroll independently, settle on a value and
 * tick as each value passes the selection band. Minutes move in fives, which is the precision
 * a planner needs and the interval iOS itself offers for scheduling.
 */
@Composable
fun WheelTimePicker(
    minute: Int,
    use24Hour: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarginTheme.colors
    val hour24 = (minute / 60).coerceIn(0, 23)
    val hours = remember(use24Hour) {
        if (use24Hour) (0..23).map { "%02d".format(it) } else (1..12).map { it.toString() }
    }
    val minutes = remember { (0 until 60 step 5).map { "%02d".format(it) } }

    var hourIndex by remember { mutableIntStateOf(if (use24Hour) hour24 else (hour24 % 12 + 11) % 12) }
    var minuteIndex by remember { mutableIntStateOf(((minute % 60) / 5).coerceIn(0, 11)) }
    var afternoon by remember { mutableStateOf(hour24 >= 12) }

    fun emit() {
        val hour = if (use24Hour) hourIndex else (hourIndex + 1) % 12 + if (afternoon) 12 else 0
        onChange(hour * 60 + minuteIndex * 5)
    }

    Box(
        modifier = modifier.height(WheelRow * WheelVisible),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Space.gutter)
                .fillMaxWidth()
                .height(WheelRow)
                .clip(MarginShape.field)
                .background(colors.tertiaryFill),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelColumn(items = hours, selectedIndex = hourIndex, width = 72.dp) {
                hourIndex = it
                emit()
            }
            WheelColumn(items = minutes, selectedIndex = minuteIndex, width = 72.dp) {
                minuteIndex = it
                emit()
            }
            if (!use24Hour) {
                WheelColumn(
                    items = listOf("AM", "PM"),
                    selectedIndex = if (afternoon) 1 else 0,
                    width = 72.dp,
                ) {
                    afternoon = it == 1
                    emit()
                }
            }
        }
    }
}

private val WheelRow: Dp = 40.dp
private const val WheelVisible = 5

@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    width: Dp,
    onSelected: (Int) -> Unit,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }

    val centered by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2f) 1 else 0)
                .coerceIn(0, items.lastIndex)
        }
    }

    // Tick as values pass the band, as the physical wheel does.
    LaunchedEffect(centered) {
        if (state.isScrollInProgress) Haptics.selection(view)
    }
    // Settle exactly on a row once the finger lifts and the fling ends.
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val target = centered
            if (state.firstVisibleItemIndex != target || state.firstVisibleItemScrollOffset != 0) {
                state.animateScrollToItem(target)
            }
            if (target != selectedIndex) onSelected(target)
        }
    }

    Box(modifier = Modifier.width(width).height(WheelRow * WheelVisible)) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(vertical = WheelRow * (WheelVisible / 2)),
            modifier = Modifier.fillMaxWidth().height(WheelRow * WheelVisible),
        ) {
            itemsIndexed(items) { index, text ->
                val isCentered = index == centered
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelRow)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = AppleType.body.copy(
                            fontSize = 21.sp,
                            fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (isCentered) colors.label else colors.tertiaryLabel,
                    )
                }
            }
        }
        // The wheel curves away: fade rows toward the top and bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelRow * 2)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.groupedBackground, colors.groupedBackground.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelRow * 2)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.groupedBackground.copy(alpha = 0f), colors.groupedBackground),
                    ),
                ),
        )
    }
}

/** Minutes shown and nudged in fives. */
@Composable
fun DurationRow(
    title: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    min: Int = 0,
    max: Int = 12 * 60,
    subtitle: String? = null,
    format: (Int) -> String = { MarginTime.formatDuration(it) },
    leading: (@Composable () -> Unit)? = null,
) {
    GroupedRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = leading,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format(minutes),
                    style = AppleType.body.copy(fontFeatureSettings = "tnum"),
                    color = MarginTheme.colors.secondaryLabel,
                )
                Spacer(Modifier.width(Space.m))
                IosStepper(
                    onDecrement = { onChange((minutes - step).coerceAtLeast(min)) },
                    onIncrement = { onChange((minutes + step).coerceAtMost(max)) },
                    canDecrement = minutes > min,
                    canIncrement = minutes < max,
                )
            }
        },
    )
}
