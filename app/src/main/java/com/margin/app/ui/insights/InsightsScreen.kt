package com.margin.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.MarginCard
import com.margin.app.ui.components.ProportionBar
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.components.StatRow
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                bottom = Space.xxxl * 2,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(key = "range") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    InsightsRange.entries.forEach { option ->
                        FilterChip(
                            selected = state.range == option,
                            onClick = { viewModel.setRange(option) },
                            label = { Text(option.label) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            if (state.plannedBlocks == 0 && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = "Not enough history yet",
                        body = "Finish or skip a few sessions and patterns will show up here.",
                    )
                }
                return@LazyColumn
            }

            item(key = "headline") {
                MarginCard {
                    SectionHeader("Follow through")
                    Spacer(Modifier.height(Space.m))
                    Text(
                        text = "${(state.completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = "${state.completedBlocks} finished, ${state.skippedBlocks} skipped, " +
                            "${state.rescheduled} moved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.m))
                    ProportionBar(fraction = state.completionRate)
                }
            }

            item(key = "days") {
                MarginCard {
                    SectionHeader("Work per day")
                    Spacer(Modifier.height(Space.l))
                    DayChart(days = state.days)
                    Spacer(Modifier.height(Space.m))
                    Text(
                        text = "${MarginTime.formatDuration(state.totalCompletedMinutes)} completed " +
                            "across ${state.daysWithWork} of ${state.days.size} days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "categories") {
                MarginCard {
                    SectionHeader("Where the time went")
                    Spacer(Modifier.height(Space.s))
                    val total = state.minutesByCategory.sumOf { it.second }.coerceAtLeast(1)
                    state.minutesByCategory.forEach { (category, minutes) ->
                        StatRow(
                            label = category.label,
                            value = MarginTime.formatDuration(minutes),
                            accent = accentFor(category),
                        )
                        ProportionBar(
                            fraction = minutes.toFloat() / total,
                            color = accentFor(category),
                            height = 4,
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                }
            }

            item(key = "timeofday") {
                MarginCard {
                    SectionHeader("When you actually work")
                    Spacer(Modifier.height(Space.s))
                    val peak = state.timeOfDay.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                    state.timeOfDay.forEach { slice ->
                        StatRow(
                            label = slice.label,
                            value = MarginTime.formatDuration(slice.minutes),
                        )
                        ProportionBar(fraction = slice.minutes.toFloat() / peak, height = 4)
                        Spacer(Modifier.height(Space.s))
                    }
                    state.bestPeriod?.let {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = "Most of your finished work happens in the $it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "sessions") {
                MarginCard {
                    SectionHeader("Sessions")
                    Spacer(Modifier.height(Space.s))
                    StatRow(
                        label = "Average length",
                        value = MarginTime.formatDuration(state.averageSessionMinutes),
                    )
                    StatRow(label = "Planned sessions", value = state.plannedBlocks.toString())
                    StatRow(label = "Moved to another time", value = state.rescheduled.toString())
                }
            }
        }
    }
}

/**
 * A plain bar chart. Planned time is the light bar, completed time the solid one, so a day
 * where the plan was ambitious and the reality was not is visible at a glance.
 */
@Composable
private fun DayChart(days: List<DayBar>) {
    val peak = days.maxOfOrNull { maxOf(it.workMinutes, it.completedMinutes) }?.coerceAtLeast(60) ?: 60
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(day.workMinutes.toFloat() / peak)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(day.completedMinutes.toFloat() / peak)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(Space.xs))
                if (days.size <= 14) {
                    Text(
                        text = day.date.dayOfWeek
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
