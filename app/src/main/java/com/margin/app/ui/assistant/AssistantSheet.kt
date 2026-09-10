package com.margin.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.ai.AssistantSource
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.MarginTextField
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.SheetGrabber
import com.margin.app.ui.theme.Space

/**
 * The natural-language bar. It is a way to state a change, not a chat window: every message
 * produces either a concrete change to the plan or a clear reason why it did not.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantSheet(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Space.gutter)
                .padding(top = Space.xl, bottom = Space.l)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            SheetGrabber()
            Spacer(Modifier.height(Space.l))
            Text("Tell Margin what changed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                text = if (state.assistantConfigured) {
                    "Plain sentences work. The plan updates around whatever you say."
                } else {
                    "No assistant key set, so common phrasings are handled on the device. " +
                        "Add a key in Settings for free-form requests."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    state.history.forEach { entry ->
                        AssistantExchange(entry)
                    }
                }
            }

            Spacer(Modifier.height(Space.l))

            if (state.history.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    state.suggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = { draft = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.small,
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Space.l))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                MarginTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "What is going on",
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 1,
                )
                Spacer(Modifier.width(Space.s))
                if (state.sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(
                        onClick = {
                            viewModel.submit(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Send",
                            tint = if (draft.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantExchange(entry: AssistantEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.userText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))
        MarginCard(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = false,
            contentPadding = PaddingValues(Space.m),
        ) {
            Text(
                text = entry.reply,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.applied.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                SectionHeader("Applied")
                Spacer(Modifier.height(Space.xs))
                entry.applied.forEach { line ->
                    Text(
                        text = "- $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.rejected.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                entry.rejected.forEach { line ->
                    Text(
                        text = "- $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (entry.source == AssistantSource.LOCAL && entry.error != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "Handled on the device: " + entry.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
