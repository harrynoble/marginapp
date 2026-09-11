package com.margin.app.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.ai.AssistantSource
import com.margin.app.ui.components.CapsuleChip
import com.margin.app.ui.components.CapsuleTextField
import com.margin.app.ui.components.CircleIconButton
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/**
 * What the add button opens outside Tasks and Plan: the three things you add most, and a line
 * to tell Margin what changed in plain words. It is an input, not a chat: every message ends
 * in a concrete change to the plan or a clear reason there was none.
 */
@Composable
fun AddSheet(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
    onNewTask: () -> Unit,
    onNewEvent: () -> Unit,
    onBreak: (Int) -> Unit,
    onGoingOut: () -> Unit,
    onAddExam: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    var draft by remember { mutableStateOf("") }
    var choosingBreak by remember { mutableStateOf(false) }

    fun send() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            viewModel.submit(text)
            draft = ""
        }
    }

    MarginSheet(onDismiss = onDismiss, title = "Add", leadingText = "Close") {
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            GroupedRow(
                title = "New task",
                subtitle = "Margin finds the time for it",
                leading = { IconTile(Icons.Rounded.TaskAlt, accents.blue) },
                showChevron = true,
                onClick = onNewTask,
            )
            RowSeparator(inset = 58.dp)
            GroupedRow(
                title = "New event",
                subtitle = "Something at a fixed time",
                leading = { IconTile(Icons.Rounded.Event, accents.red) },
                showChevron = true,
                onClick = onNewEvent,
            )
            RowSeparator(inset = 58.dp)
            GroupedRow(
                title = "Take a break",
                subtitle = "The rest of the day moves back",
                leading = { IconTile(Icons.Rounded.Coffee, accents.brown) },
                onClick = { choosingBreak = !choosingBreak },
            )
            AnimatedVisibility(visible = choosingBreak) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 58.dp, end = Space.l, bottom = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    listOf(5, 10, 15, 30, 45).forEach { minutes ->
                        CapsuleChip(text = "$minutes min", selected = false, onClick = { onBreak(minutes) })
                    }
                }
            }
            RowSeparator(inset = 58.dp)
            GroupedRow(
                title = "Going out",
                subtitle = "Say when you'll be back; the rest of the day is replanned",
                leading = { IconTile(Icons.AutoMirrored.Rounded.DirectionsWalk, accents.purple) },
                showChevron = true,
                onClick = onGoingOut,
            )
            RowSeparator(inset = 58.dp)
            GroupedRow(
                title = "Add an exam",
                subtitle = "Revision spreads out across the days left",
                leading = { IconTile(Icons.Rounded.School, accents.indigo) },
                showChevron = true,
                onClick = onAddExam,
            )
        }

        GroupedSection(
            header = "Tell Margin",
            footer = if (state.assistantConfigured) {
                "Plain sentences work. The plan updates around whatever you say."
            } else {
                "Common phrasings work on the device. Add an assistant key in Settings for anything free-form."
            },
        ) {
            if (state.history.isEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = Space.m, end = Space.m, top = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    state.suggestions.forEach { suggestion ->
                        CapsuleChip(text = suggestion, selected = false, onClick = { draft = suggestion })
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(start = Space.m, end = Space.m, top = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    state.history.forEach { entry -> Exchange(entry) }
                }
            }

            Row(
                modifier = Modifier.padding(Space.m),
                verticalAlignment = Alignment.Bottom,
            ) {
                CapsuleTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "What changed?",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.s))
                if (state.sending) {
                    Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = colors.tint,
                        )
                    }
                } else {
                    val ready = draft.isNotBlank()
                    CircleIconButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        onClick = ::send,
                        size = 46.dp,
                        tint = if (ready) colors.onTint else colors.tertiaryLabel,
                        background = if (ready) colors.tint else colors.tertiaryFill,
                    )
                }
            }
        }
    }
}

/** One message and what came of it: your words on the right, the outcome on the left. */
@Composable
private fun Exchange(entry: AssistantEntry) {
    val colors = MarginTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(40.dp))
            Text(
                text = entry.userText,
                style = AppleType.subheadline,
                color = colors.onTint,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(MarginShape.tile)
                    .background(colors.tint)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(MarginShape.tile)
                    .background(colors.tertiaryFill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = entry.reply, style = AppleType.subheadline, color = colors.label)
                entry.applied.forEach { line -> Outcome(line, ok = true) }
                entry.rejected.forEach { line -> Outcome(line, ok = false) }
                if (entry.source == AssistantSource.LOCAL && entry.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Handled on the device. " + entry.error,
                        style = AppleType.caption1,
                        color = colors.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.width(40.dp))
        }
    }
}

@Composable
private fun Outcome(text: String, ok: Boolean) {
    val colors = MarginTheme.colors
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) colors.positive else colors.destructive,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = AppleType.footnote,
            color = if (ok) colors.secondaryLabel else colors.destructive,
        )
    }
}
