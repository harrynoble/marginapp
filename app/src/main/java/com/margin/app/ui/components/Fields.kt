package com.margin.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.Priority
import com.margin.app.ui.theme.Space

@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(label)
        Spacer(Modifier.height(Space.s))
        content()
    }
}

@Composable
fun MarginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** A time value shown as a chip that opens the system-style picker. */
@Composable
fun TimeField(
    minute: Int,
    use24Hour: Boolean,
    label: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { showPicker = true },
        modifier = modifier,
        label = { Text("$label ${MarginTime.formatTime(minute, use24Hour)}") },
        leadingIcon = {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
    if (showPicker) {
        MarginTimePickerDialog(
            initialMinute = minute,
            use24Hour = use24Hour,
            onDismiss = { showPicker = false },
            onConfirm = {
                onChange(it)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarginTimePickerDialog(
    initialMinute: Int,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60).coerceIn(0, 23),
        initialMinute = (initialMinute % 60).coerceIn(0, 59),
        is24Hour = use24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPicker(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = listOf(15, 25, 30, 45, 60, 90, 120),
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { option ->
            FilterChip(
                selected = minutes == option,
                onClick = { onChange(option) },
                label = { Text(MarginTime.formatDurationShort(option)) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    selected: Category,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Category.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    CategoryDot(category = category, modifier = Modifier.padding(start = 2.dp))
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriorityPicker(selected: Priority, onSelect: (Priority) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Priority.entries.forEach { priority ->
            FilterChip(
                selected = selected == priority,
                onClick = { onSelect(priority) },
                label = { Text(priority.label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyPicker(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Difficulty.entries.forEach { difficulty ->
            FilterChip(
                selected = selected == difficulty,
                onClick = { onSelect(difficulty) },
                label = { Text(difficulty.label) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** A number the user nudges rather than types. Used for every minutes setting. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.s),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onDecrease) { Text("-", style = MaterialTheme.typography.titleMedium) }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(72.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = onIncrease) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
