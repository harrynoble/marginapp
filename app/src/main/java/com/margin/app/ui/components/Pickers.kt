package com.margin.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.margin.app.domain.model.Category
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor

/**
 * A single-choice row of capsules that scrolls sideways when it runs out of room. Used inside
 * grouped sections, where it takes the place of a menu for short option lists. [leading]
 * returns the adornment for an option, or null for none.
 */
@Composable
fun <T> OptionChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: ((T) -> (@Composable () -> Unit)?)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.m, vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            CapsuleChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                leading = leading?.invoke(option),
            )
        }
    }
}

@Composable
fun CategoryChips(selected: Category, onSelect: (Category) -> Unit, modifier: Modifier = Modifier) {
    OptionChips(
        options = Category.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelect,
        modifier = modifier,
        leading = { category -> { ColorDot(color = accentFor(category), size = 8.dp) } },
    )
}
