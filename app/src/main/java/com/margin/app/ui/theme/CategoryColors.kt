package com.margin.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category

/**
 * Categories map onto Apple's system accents, the same colours Calendar offers for its
 * calendars, so the palette reads as familiar rather than invented.
 */
@Composable
@ReadOnlyComposable
fun accentFor(category: Category): Color {
    val a = LocalAccents.current
    return when (category) {
        Category.ACADEMICS -> a.indigo
        Category.BUILD -> a.orange
        Category.LEARNING -> a.teal
        Category.PERSONAL -> a.blue
        Category.HEALTH -> a.pink
        Category.LEISURE -> a.green
        Category.OTHER -> a.gray
    }
}

/** Structural blocks (travel, breaks, meals) are quiet greys and browns so work stands out. */
@Composable
@ReadOnlyComposable
fun railFor(type: BlockType, category: Category): Color {
    val a = LocalAccents.current
    return when (type) {
        BlockType.FREE, BlockType.SLEEP -> LocalMarginColors.current.tertiaryLabel
        BlockType.BREAK, BlockType.DECOMPRESS, BlockType.COMMUTE, BlockType.ROUTINE -> a.gray
        BlockType.MEAL -> a.brown
        BlockType.CLASS -> a.indigo
        else -> accentFor(category)
    }
}

/** A stable colour per subject, cycling through the accents in a pleasing order. */
@Composable
@ReadOnlyComposable
fun subjectAccent(index: Int): Color {
    val a = LocalAccents.current
    val cycle = listOf(a.indigo, a.orange, a.teal, a.pink, a.green, a.blue, a.purple, a.brown)
    return cycle[((index % cycle.size) + cycle.size) % cycle.size]
}
