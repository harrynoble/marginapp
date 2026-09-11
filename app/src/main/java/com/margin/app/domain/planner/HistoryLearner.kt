package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.TimeRange

/**
 * What the history says, in plain numbers the planner can use and the user can be told.
 * No model guesses here: medians and rates over what actually happened.
 */
data class LearnedPatterns(
    /** Actual over planned minutes, by track key ("DELD:theory"), "build" or "learn". */
    val durationRatio: Map<String, Float>,
    val samples: Map<String, Int>,
    /** Share of sessions started in that hour that were skipped or missed. */
    val skipRateByHour: Map<Int, Float>,
    /** The two-hour stretch in which most academic work actually got done. */
    val bestWindow: TimeRange?,
) {
    fun ratioFor(key: String): Float = durationRatio[key] ?: 1f

    /** An hour the user skips most sessions in. Demanding work is kept out of it. */
    fun avoids(hour: Int): Boolean = (skipRateByHour[hour] ?: 0f) >= AVOID_RATE

    companion object {
        val NONE = LearnedPatterns(emptyMap(), emptyMap(), emptyMap(), null)
        const val AVOID_RATE = 0.6f
    }
}

object HistoryLearner {

    const val MIN_SAMPLES = 3
    private const val MIN_RATIO = 0.75f
    private const val MAX_RATIO = 1.6f
    private const val MIN_WINDOW_SESSIONS = 5
    private const val WINDOW_SHARE = 0.4f

    fun learn(completions: List<CompletionRecord>, skips: List<SkipRecord>): LearnedPatterns {
        // ---- planned against actual --------------------------------------------------------
        val ratios = completions
            .filter { it.plannedMinutes > 0 && it.minutes > 0 }
            .groupBy { keyFor(it) }
            .mapValues { (_, list) -> list.map { it.minutes.toFloat() / it.plannedMinutes } }

        val durationRatio = ratios
            .filter { it.value.size >= MIN_SAMPLES }
            .mapValues { (_, values) -> median(values).coerceIn(MIN_RATIO, MAX_RATIO) }
            // A ratio within a few percent of the plan is noise, not a pattern.
            .filter { kotlin.math.abs(it.value - 1f) >= 0.08f }

        // ---- when sessions get skipped ------------------------------------------------------
        val completedByHour = completions
            .mapNotNull { it.startMinute?.div(60) }
            .groupingBy { it }
            .eachCount()
        val skippedByHour = skips
            .mapNotNull { it.plannedStart?.div(60) }
            .groupingBy { it }
            .eachCount()
        val skipRate = (completedByHour.keys + skippedByHour.keys).associateWith { hour ->
            val done = completedByHour[hour] ?: 0
            val missed = skippedByHour[hour] ?: 0
            if (done + missed < MIN_SAMPLES) null else missed.toFloat() / (done + missed)
        }.filterValues { it != null }.mapValues { it.value!! }

        // ---- when academic work actually gets done -----------------------------------------
        val academic = completions.filter { it.category == Category.ACADEMICS && it.startMinute != null }
        val bestWindow = if (academic.size < MIN_WINDOW_SESSIONS) {
            null
        } else {
            val byHour = academic.groupBy { it.startMinute!! / 60 }.mapValues { e -> e.value.sumOf { it.minutes } }
            val total = byHour.values.sum().coerceAtLeast(1)
            val best = (0..22).maxByOrNull { hour -> (byHour[hour] ?: 0) + (byHour[hour + 1] ?: 0) }
            best?.let { hour ->
                val share = ((byHour[hour] ?: 0) + (byHour[hour + 1] ?: 0)).toFloat() / total
                if (share >= WINDOW_SHARE) TimeRange(hour * 60, (hour + 2) * 60) else null
            }
        }

        return LearnedPatterns(
            durationRatio = durationRatio,
            samples = ratios.mapValues { it.value.size },
            skipRateByHour = skipRate,
            bestWindow = bestWindow,
        )
    }

    fun keyFor(record: CompletionRecord): String = record.track?.key ?: when (record.type) {
        BlockType.BUILD -> "build"
        BlockType.LEARN -> "learn"
        else -> record.type.key
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }
}
