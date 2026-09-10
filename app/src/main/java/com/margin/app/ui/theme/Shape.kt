package com.margin.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Restrained radii. Big pill-shaped cards read as a consumer toy; square corners read as a
 * spreadsheet. Ten to twelve is the range that stays calm at every size used here.
 */
val MarginShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** The 4dp base scale. Screen gutter is [Space.gutter] everywhere, without exception. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val gutter = 20.dp
    val listGap = 8.dp
    val sectionGap = 28.dp
}
