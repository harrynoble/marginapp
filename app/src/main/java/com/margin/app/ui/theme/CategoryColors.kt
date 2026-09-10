package com.margin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category

@Composable
@ReadOnlyComposable
fun accentFor(category: Category): Color {
    val accents = LocalCategoryColors.current
    val index = when (category) {
        Category.ACADEMICS -> 0
        Category.BUILD -> 1
        Category.LEARNING -> 2
        Category.PERSONAL -> 3
        Category.HEALTH -> 4
        Category.LEISURE -> 5
        Category.OTHER -> 6
    }
    return accents.getOrElse(index) { accents.last() }
}

/**
 * Structural blocks borrow the neutral outline rather than a category accent, so the
 * timeline reads as work against a quiet background rather than as a stripe chart.
 */
@Composable
@ReadOnlyComposable
fun railFor(type: BlockType, category: Category): Color = when (type) {
    BlockType.FREE, BlockType.SLEEP -> MaterialTheme.colorScheme.outlineVariant
    BlockType.BREAK, BlockType.DECOMPRESS -> MaterialTheme.colorScheme.outline
    BlockType.COMMUTE, BlockType.ROUTINE -> MaterialTheme.colorScheme.outline
    else -> accentFor(category)
}

@Composable
@ReadOnlyComposable
fun subjectAccent(index: Int): Color {
    val accents = LocalCategoryColors.current
    return accents[((index % accents.size) + accents.size) % accents.size]
}
