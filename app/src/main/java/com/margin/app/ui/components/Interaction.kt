package com.margin.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * The iOS press behaviour for rows: a grey wash that appears the moment a finger lands and
 * fades as it lifts. It replaces Material ripples everywhere through LocalIndication.
 */
class HighlightIndication(private val color: Color) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HighlightNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is HighlightIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class HighlightNode(
    private val interactions: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {

    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { strength.snapTo(1f) }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> launch { strength.animateTo(0f, tween(durationMillis = 280)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = strength.value
        if (alpha > 0f) drawRect(color = color, alpha = alpha)
    }
}

/**
 * Buttons compress under the finger and spring back, the tactile response iOS gives every
 * tappable control. Glass controls use a gentle scale-up instead, because glass lifts.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Short, deliberate haptics. Used sparingly: selection changes and completions only. */
object Haptics {
    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            },
        )
    }

    fun light(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
