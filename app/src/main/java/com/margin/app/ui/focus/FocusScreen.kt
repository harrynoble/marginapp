package com.margin.app.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.SkipResolution
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.labelFor
import com.margin.app.ui.theme.Space

/**
 * One thing, full screen, with the clock and the exits. The ring is the only expressive
 * element in the app and it moves once a minute.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    val block = state.block
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (block == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "That session is no longer on the schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.xl))

            Text(
                text = labelFor(block.type).uppercase(),
                style = com.margin.app.ui.theme.SectionLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                text = block.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            block.subtitle?.let {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.xxxl))

            ProgressRing(
                progress = state.progress,
                centerLabel = if (state.overrun) {
                    "+" + MarginTime.formatDurationShort(state.nowMinute - block.end)
                } else {
                    MarginTime.formatDurationShort(state.remainingMinutes)
                },
                subLabel = if (state.overrun) "over" else "left",
                overrun = state.overrun,
            )

            Spacer(Modifier.height(Space.xl))
            Text(
                text = MarginTime.formatTime(block.start, state.use24Hour) + " to " +
                    MarginTime.formatTime(block.end, state.use24Hour) + " · " +
                    MarginTime.formatDuration(block.duration) + " planned",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xxxl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Button(
                    onClick = viewModel::complete,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        vertical = Space.m,
                    ),
                ) { Text("Finish", style = MaterialTheme.typography.labelLarge) }

                OutlinedButton(
                    onClick = { if (state.running) viewModel.pause() else viewModel.resume() },
                    shape = MaterialTheme.shapes.small,
                ) { Text(if (state.running) "Pause" else "Resume") }
            }

            Spacer(Modifier.height(Space.l))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(10, 15, 30).forEach { extra ->
                    AssistChip(
                        onClick = { viewModel.extend(extra) },
                        label = { Text("+$extra min") },
                        shape = MaterialTheme.shapes.small,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                AssistChip(
                    onClick = { viewModel.skip(SkipResolution.LATER_TODAY) },
                    label = { Text("Skip") },
                    shape = MaterialTheme.shapes.small,
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                AssistChip(
                    onClick = { viewModel.skip(SkipResolution.TOMORROW) },
                    label = { Text("Move to tomorrow") },
                    shape = MaterialTheme.shapes.small,
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            if (state.links.isNotEmpty()) {
                Spacer(Modifier.height(Space.xl))
                MarginCard {
                    SectionHeader("Resources")
                    Spacer(Modifier.height(Space.s))
                    state.links.forEach { link ->
                        TextButton(onClick = { }) { Text(link.label) }
                    }
                }
            }

            block.reason?.let { reason ->
                Spacer(Modifier.height(Space.xl))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    centerLabel: String,
    subLabel: String,
    overrun: Boolean,
) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "focusRing",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = if (overrun) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(208.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val diameter = size.minDimension - stroke
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animated.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
