package com.margin.app.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.SmoothRoundedCornerShape
import kotlin.math.ceil
import kotlin.math.min

/*
 * Liquid Glass for Compose.
 *
 * How it works: the content that glass floats over is recorded into a GraphicsLayer (a GPU
 * display list) by [glassSource]. Each glass element re-draws that same recording, offset to
 * its own position, through a RenderEffect chain of blur, then a saturation lift, then the
 * AGSL refraction shader, and clips the result to its shape. Tint, specular sheen, a lit rim
 * and a soft shadow go on top. Because the recording is a live reference rather than a copy,
 * scrolling content updates inside the glass on the GPU without the glass re-drawing.
 *
 * Apple's rule is that glass belongs to the navigation layer only: tab bars, toolbars and
 * floating controls. Content never gets glass, and glass never sits on glass.
 *
 * Support by Android version:
 *   33+   blur, vibrancy and edge refraction (full effect)
 *   31-32 blur and vibrancy, no refraction
 *   <31   a near-opaque material, which is also what Apple shows with Reduce Transparency on
 */

internal object GlassSupport {
    val blur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val refraction: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/** The content glass can see. One per scrolling surface. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer?) {
    internal var origin: Offset by mutableStateOf(Offset.Unspecified)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = if (GlassSupport.blur) rememberGraphicsLayer() else null
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks this node's drawing as the backdrop that glass elements refract. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    val layer = backdrop.layer ?: return this
    return this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

@Immutable
data class GlassStyle(
    val blurRadius: Dp,
    val saturation: Float,
    val refractionBand: Dp,
    val refractionStrength: Dp,
    val dispersion: Float,
    val tint: Color,
    val fallback: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val edge: Color,
    val sheen: Color,
    val shadowElevation: Dp,
    val shadowColor: Color,
)

object GlassDefaults {

    /** Apple's "regular" variant: adaptive, legible over anything. The default for bars. */
    @Composable
    @ReadOnlyComposable
    fun regular(): GlassStyle {
        val c = MarginTheme.colors
        return GlassStyle(
            blurRadius = 9.dp,
            saturation = 1.45f,
            refractionBand = 20.dp,
            refractionStrength = 11.dp,
            dispersion = 0.07f,
            tint = c.glassTint,
            fallback = c.glassFallback,
            rimTop = c.glassRimTop,
            rimBottom = c.glassRimBottom,
            edge = c.glassEdge,
            sheen = c.glassSheen,
            shadowElevation = 14.dp,
            shadowColor = if (c.isDark) Color.Black else Color(0x59000000),
        )
    }

    /**
     * Tinted glass for the one primary action on screen. Apple reserves tint for emphasis,
     * so this appears once, on the add button.
     */
    @Composable
    @ReadOnlyComposable
    fun prominent(color: Color): GlassStyle = regular().copy(
        tint = color.copy(alpha = 0.86f),
        fallback = color,
        rimTop = Color.White.copy(alpha = 0.62f),
        rimBottom = Color.White.copy(alpha = 0.14f),
        edge = color.copy(alpha = 0.35f),
        sheen = Color.White.copy(alpha = 0.30f),
    )

    /** Glass over a vivid ground, such as the focus screen. Clearer, as Apple's clear variant. */
    @Composable
    @ReadOnlyComposable
    fun clear(): GlassStyle = regular().copy(
        blurRadius = 6.dp,
        tint = Color.White.copy(alpha = 0.14f),
        fallback = Color.White.copy(alpha = 0.22f),
        rimTop = Color.White.copy(alpha = 0.55f),
        rimBottom = Color.White.copy(alpha = 0.12f),
        edge = Color.White.copy(alpha = 0.10f),
        sheen = Color.White.copy(alpha = 0.18f),
        shadowElevation = 8.dp,
        shadowColor = Color.Black,
    )
}

/**
 * A piece of Liquid Glass. [cornerRadius] null means a capsule, which is what Apple uses for
 * any control near the screen edge.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    style: GlassStyle = GlassDefaults.regular(),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape: Shape = remember(cornerRadius) {
        if (cornerRadius == null) RoundedCornerShape(percent = 50) else SmoothRoundedCornerShape(cornerRadius)
    }
    val effectLayer = if (GlassSupport.blur && backdrop?.layer != null) rememberGraphicsLayer() else null
    val cache = remember { EffectCache() }
    var position by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = modifier
            .shadow(
                elevation = style.shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = style.shadowColor,
                spotColor = style.shadowColor,
            )
            .onGloballyPositioned { position = it.positionInWindow() }
            .drawBehind {
                drawGlass(
                    shape = shape,
                    style = style,
                    backdrop = backdrop,
                    effectLayer = effectLayer,
                    position = position,
                    cache = cache,
                    cornerRadiusPx = cornerRadius?.toPx(),
                )
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

private fun DrawScope.drawGlass(
    shape: Shape,
    style: GlassStyle,
    backdrop: GlassBackdrop?,
    effectLayer: GraphicsLayer?,
    position: Offset,
    cache: EffectCache,
    cornerRadiusPx: Float?,
) {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawGlass)) }
    val source = backdrop?.layer
    val origin = backdrop?.origin ?: Offset.Unspecified

    if (source != null && effectLayer != null && origin.isSpecified && position.isSpecified) {
        val blurPx = style.blurRadius.toPx()
        val pad = ceil(blurPx * 2f).toInt().coerceAtLeast(2)
        val layerSize = IntSize(ceil(size.width).toInt() + pad * 2, ceil(size.height).toInt() + pad * 2)
        val dx = position.x - origin.x
        val dy = position.y - origin.y

        effectLayer.record(size = layerSize) {
            translate(left = pad - dx, top = pad - dy) { drawLayer(source) }
        }
        effectLayer.topLeft = IntOffset(-pad, -pad)
        val radius = cornerRadiusPx ?: (min(size.width, size.height) / 2f)
        effectLayer.renderEffect = cache.effectFor(
            blurPx = blurPx,
            saturation = style.saturation,
            padding = pad.toFloat(),
            width = size.width,
            height = size.height,
            cornerRadius = radius,
            band = min(style.refractionBand.toPx(), min(size.width, size.height) / 2f),
            strength = style.refractionStrength.toPx(),
            dispersion = style.dispersion,
        )
        clipPath(path) { drawLayer(effectLayer) }
    } else {
        drawPath(path, style.fallback)
    }

    // Tint, then a specular sheen across the upper half, as light catching the top surface.
    drawPath(path, style.tint)
    clipPath(path) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to style.sheen,
                0.55f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            ),
        )
    }

    // The lit rim: brightest where light enters at the top-left, fading, then catching again
    // at the bottom-right. Drawn inside the shape only, so it reads as the glass edge.
    val rim = 1.3.dp.toPx()
    clipPath(path) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                0f to style.rimTop,
                0.45f to style.rimBottom,
                1f to style.rimTop.copy(alpha = style.rimTop.alpha * 0.55f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = rim * 2f),
        )
    }
    // A hairline outside, which is what keeps glass defined against a white page.
    drawPath(path, color = style.edge, style = Stroke(width = 0.7.dp.toPx()))
}

/** Rebuilding a RenderEffect is cheap but not free; only do it when the geometry changes. */
private class EffectCache {
    private var key: List<Any>? = null
    private var effect: RenderEffect? = null

    fun effectFor(
        blurPx: Float,
        saturation: Float,
        padding: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        band: Float,
        strength: Float,
        dispersion: Float,
    ): RenderEffect? {
        if (!GlassSupport.blur) return null
        val next = listOf(blurPx, saturation, padding, width, height, cornerRadius, band, strength, dispersion)
        if (next == key) return effect
        key = next
        effect = GlassEffects.build(
            blurPx, saturation, padding, width, height, cornerRadius, band, strength, dispersion,
        )
        return effect
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private object GlassEffects {
    fun build(
        blurPx: Float,
        saturation: Float,
        padding: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        band: Float,
        strength: Float,
        dispersion: Float,
    ): RenderEffect {
        val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
        val frosted = if (blurPx > 0.5f) {
            val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            android.graphics.RenderEffect.createColorFilterEffect(saturate, blur)
        } else {
            android.graphics.RenderEffect.createColorFilterEffect(saturate)
        }
        val result = if (GlassSupport.refraction && strength > 0f && band > 0f) {
            GlassShader.wrap(frosted, padding, width, height, cornerRadius, band, strength, dispersion)
                ?: frosted
        } else {
            frosted
        }
        return result.asComposeRenderEffect()
    }
}
