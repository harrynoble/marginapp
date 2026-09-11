package com.margin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.phaseFor
import com.margin.app.ui.theme.skyFor

/**
 * The sky behind the top of Today, which changes with the phase of the day: warm at dawn,
 * clear through the day, amber at dusk, deep blue at night. It sits in the content layer and
 * fades into the page, so it gives the floating glass something real to bend and tells the
 * time at a glance. Colour transitions are slow enough to go unnoticed.
 */
@Composable
fun DaySky(
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    height: Dp = 480.dp,
) {
    val colors = MarginTheme.colors
    val sky = skyFor(phaseFor(minuteOfDay), colors.isDark)
    val warm by animateColorAsState(sky.warm, tween(durationMillis = 1600), label = "skyWarm")
    val cool by animateColorAsState(sky.cool, tween(durationMillis = 1600), label = "skyCool")
    val ground = colors.groupedBackground

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                0f to cool,
                1f to cool.copy(alpha = 0f),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(warm, warm.copy(alpha = 0f)),
                center = Offset(w * 0.08f, h * 0.02f),
                radius = w * 0.95f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(cool.copy(alpha = 0.9f), cool.copy(alpha = 0f)),
                center = Offset(w * 1.0f, h * 0.18f),
                radius = w * 0.8f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0.35f to Color.Transparent,
                1f to ground,
            ),
        )
    }
}
