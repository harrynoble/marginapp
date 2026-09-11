package com.margin.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Continuous-curvature corners, the "squircle" Apple uses for every rounded rectangle.
 *
 * A plain rounded rectangle joins a straight edge to a quarter circle, and the sudden jump in
 * curvature is visible as a faint kink where the corner starts. Apple eases into the curve
 * instead. This is a port of the well known Figma corner-smoothing construction: each corner
 * is a Bezier lead-in, a shortened circular arc, and a Bezier lead-out. A smoothing of 0.6
 * matches iOS.
 *
 * When a corner has no room to smooth (a capsule, where the radius is half the height) the
 * smoothing collapses to zero and the shape is an exact capsule, so one shape covers both.
 */
@Immutable
class SmoothRoundedCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {

    constructor(radius: Dp, smoothing: Float = 0.6f) :
        this(radius, radius, radius, radius, smoothing)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))

        val budget = min(w, h) / 2f
        fun px(value: Dp) = with(density) { value.toPx() }.coerceIn(0f, budget)

        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = corner(px(if (ltr) topStart else topEnd), budget)
        val tr = corner(px(if (ltr) topEnd else topStart), budget)
        val br = corner(px(if (ltr) bottomEnd else bottomStart), budget)
        val bl = corner(px(if (ltr) bottomStart else bottomEnd), budget)

        val path = Path().apply {
            moveTo(w - tr.p, 0f)
            topRight(this, tr, w)
            lineTo(w, h - br.p)
            bottomRight(this, br, w, h)
            lineTo(bl.p, h)
            bottomLeft(this, bl, h)
            lineTo(0f, tl.p)
            topLeft(this, tl)
            close()
        }
        return Outline.Generic(path)
    }

    private class Corner(
        val r: Float,
        val p: Float,
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val arcLength: Float,
        val arcDegrees: Float,
    )

    private fun corner(radius: Float, budget: Float): Corner {
        if (radius <= 0f) return Corner(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        // Smoothing needs straight edge to borrow from. Clamp it so the lead-in never runs
        // past the midpoint of the side.
        val s = min(smoothing, budget / radius - 1f).coerceAtLeast(0f)
        val p = min((1f + s) * radius, budget)

        val arcDegrees = 90f * (1f - s)
        val arcLength = sin(rad(arcDegrees / 2f)) * radius * sqrt(2f)
        val alpha = (90f - arcDegrees) / 2f
        val p3ToP4 = radius * tan(rad(alpha / 2f))
        val beta = 45f * s
        val c = p3ToP4 * cos(rad(beta))
        val d = c * tan(rad(beta))
        val b = (p - arcLength - c - d) / 3f
        val a = 2f * b
        return Corner(radius, p, a, b, c, d, arcLength, arcDegrees)
    }

    private fun topRight(path: Path, k: Corner, w: Float) {
        if (k.r <= 0f) { path.lineTo(w, 0f); return }
        val sx = w - k.p
        path.cubicTo(sx + k.a, 0f, sx + k.a + k.b, 0f, sx + k.a + k.b + k.c, k.d)
        path.arcTo(circle(w - k.r, k.r, k.r), -45f - k.arcDegrees / 2f, k.arcDegrees, false)
        val ex = sx + k.a + k.b + k.c + k.arcLength
        val ey = k.d + k.arcLength
        path.cubicTo(ex + k.d, ey + k.c, ex + k.d, ey + k.b + k.c, ex + k.d, ey + k.a + k.b + k.c)
    }

    private fun bottomRight(path: Path, k: Corner, w: Float, h: Float) {
        if (k.r <= 0f) { path.lineTo(w, h); return }
        val sy = h - k.p
        path.cubicTo(w, sy + k.a, w, sy + k.a + k.b, w - k.d, sy + k.a + k.b + k.c)
        path.arcTo(circle(w - k.r, h - k.r, k.r), 45f - k.arcDegrees / 2f, k.arcDegrees, false)
        val ex = w - k.d - k.arcLength
        val ey = sy + k.a + k.b + k.c + k.arcLength
        path.cubicTo(ex - k.c, ey + k.d, ex - k.b - k.c, ey + k.d, ex - k.a - k.b - k.c, ey + k.d)
    }

    private fun bottomLeft(path: Path, k: Corner, h: Float) {
        if (k.r <= 0f) { path.lineTo(0f, h); return }
        val sx = k.p
        path.cubicTo(sx - k.a, h, sx - k.a - k.b, h, sx - k.a - k.b - k.c, h - k.d)
        path.arcTo(circle(k.r, h - k.r, k.r), 135f - k.arcDegrees / 2f, k.arcDegrees, false)
        val ex = sx - k.a - k.b - k.c - k.arcLength
        val ey = h - k.d - k.arcLength
        path.cubicTo(ex - k.d, ey - k.c, ex - k.d, ey - k.b - k.c, ex - k.d, ey - k.a - k.b - k.c)
    }

    private fun topLeft(path: Path, k: Corner) {
        if (k.r <= 0f) { path.lineTo(0f, 0f); return }
        val sy = k.p
        path.cubicTo(0f, sy - k.a, 0f, sy - k.a - k.b, k.d, sy - k.a - k.b - k.c)
        path.arcTo(circle(k.r, k.r, k.r), 225f - k.arcDegrees / 2f, k.arcDegrees, false)
        val ex = k.d + k.arcLength
        val ey = sy - k.a - k.b - k.c - k.arcLength
        path.cubicTo(ex + k.c, ey - k.d, ex + k.b + k.c, ey - k.d, ex + k.a + k.b + k.c, ey - k.d)
    }

    private fun circle(cx: Float, cy: Float, r: Float) = Rect(cx - r, cy - r, cx + r, cy + r)

    private fun rad(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)

    override fun equals(other: Any?): Boolean =
        other is SmoothRoundedCornerShape &&
            topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            smoothing == other.smoothing

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }
}

/**
 * The shape vocabulary. Radii are concentric: a control inset 16dp inside a 28dp card gets a
 * 12dp corner, so nested curves stay parallel, which is Apple's concentricity rule.
 */
object MarginShape {
    val hero = SmoothRoundedCornerShape(28.dp)
    val card = SmoothRoundedCornerShape(24.dp)
    val tile = SmoothRoundedCornerShape(18.dp)
    val field = SmoothRoundedCornerShape(12.dp)
    val block = SmoothRoundedCornerShape(10.dp)
    val iconTile = SmoothRoundedCornerShape(8.dp)
    val small = SmoothRoundedCornerShape(6.dp)
    val sheet = SmoothRoundedCornerShape(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
    val capsule = RoundedCornerShape(percent = 50)
}

/**
 * Material components (date picker internals, menus) fall back to these. Material requires
 * corner-based shapes here, so they are plain rounded rectangles on the same radii; every
 * surface Margin draws itself uses the squircles above.
 */
val MarginShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The 4dp grid. Screen margin is 16dp, the iOS inset-grouped margin on a standard iPhone. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val gutter = 16.dp
    val sectionGap = 28.dp
    val rowMinHeight = 44.dp
    val tabBarHeight = 62.dp
    val topBarHeight = 52.dp
}
