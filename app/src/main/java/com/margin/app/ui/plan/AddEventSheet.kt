package com.margin.app.ui.plan

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.ui.components.CategoryChips
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.TimeRow
import com.margin.app.ui.theme.Space
import java.time.LocalDate

/** A one-off commitment. The planner treats it as fixed time and works around it. */
@Composable
fun AddEventSheet(
    date: LocalDate,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, Int, Int, Category, String?) -> Unit,
    initialStart: Int? = null,
) {
    val startDefault = initialStart ?: (18 * 60)
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(startDefault) }
    var end by remember { mutableIntStateOf((startDefault + 60).coerceAtMost(24 * 60 - 5)) }
    var category by remember { mutableStateOf(Category.PERSONAL) }

    MarginSheet(
        onDismiss = onDismiss,
        title = "New Event",
        trailingText = "Add",
        trailingEnabled = end > start,
        onTrailing = {
            onSave(title.trim().ifBlank { "Event" }, date, start, end, category, notes.trim().ifBlank { null })
        },
    ) {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = title, onValueChange = { title = it }, placeholder = "Title")
        }
        GroupedSection(header = MarginTime.dayLabel(date)) {
            TimeRow(
                title = "Starts",
                minute = start,
                use24Hour = use24Hour,
                onChange = {
                    start = it
                    if (end <= start) end = (start + 60).coerceAtMost(24 * 60 - 5)
                },
            )
            RowSeparator()
            TimeRow(
                title = "Ends",
                minute = end,
                use24Hour = use24Hour,
                onChange = { end = it.coerceAtLeast(start + 5) },
            )
        }
        GroupedSection(header = "Category") {
            CategoryChips(selected = category, onSelect = { category = it })
        }
        GroupedSection(header = "Notes") {
            FormTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Optional",
                singleLine = false,
                minLines = 3,
            )
        }
    }
}
