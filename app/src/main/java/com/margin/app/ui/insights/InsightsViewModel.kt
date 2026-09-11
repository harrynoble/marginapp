package com.margin.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SubjectTrack
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.HistoryLearner
import com.margin.app.domain.planner.LearnedPatterns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class InsightsRange(val days: Long, val label: String) {
    TODAY(1, "Today"),
    WEEK(7, "Week"),
    MONTH(30, "Month"),
}

data class DayBar(val date: LocalDate, val workMinutes: Int, val completedMinutes: Int)

data class TimeOfDaySlice(val label: String, val minutes: Int)

/** One kind of time in the day, in real minutes. */
data class BalanceSlice(val label: String, val minutes: Int, val kind: BalanceKind)

enum class BalanceKind { STUDY, BUILD, LEARNING, LEISURE, BREAKS }

/** One subject taught one way. Theory and lab are always reported apart. */
data class TrackInsight(
    val track: SubjectTrack,
    val name: String,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val lastStudied: LocalDate?,
    val daysSince: Int?,
)

/** What the planner has learned, as sentences built from real numbers. */
data class LearnedEstimate(val label: String, val detail: String)

data class InsightsUiState(
    val range: InsightsRange = InsightsRange.WEEK,
    val plannedBlocks: Int = 0,
    val completedBlocks: Int = 0,
    val skippedBlocks: Int = 0,
    val rescheduled: Int = 0,
    val minutesByCategory: List<Pair<Category, Int>> = emptyList(),
    val days: List<DayBar> = emptyList(),
    val timeOfDay: List<TimeOfDaySlice> = emptyList(),
    val daysWithWork: Int = 0,
    val averageSessionMinutes: Int = 0,
    val balance: List<BalanceSlice> = emptyList(),
    val tracks: List<TrackInsight> = emptyList(),
    val estimates: List<LearnedEstimate> = emptyList(),
    val bestWindow: TimeRange? = null,
    val loading: Boolean = true,
) {
    val completionRate: Float
        get() {
            val attempted = completedBlocks + skippedBlocks
            return if (attempted == 0) 0f else completedBlocks.toFloat() / attempted
        }

    val totalCompletedMinutes: Int get() = days.sumOf { it.completedMinutes }

    val bestPeriod: String?
        get() = timeOfDay.maxByOrNull { it.minutes }?.takeIf { it.minutes > 0 }?.label
}

private data class History(val blocks: List<ScheduleBlock>, val learned: LearnedPatterns)

/**
 * Self-awareness, not gamification. There are no streaks to protect and no badges: just what
 * actually happened, per subject and for the day as a whole, and what that taught the planner.
 */
class InsightsViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val timetableRepository: TimetableRepository,
) : ViewModel() {

    private val range = MutableStateFlow(InsightsRange.WEEK)

    /** Two months of history, for "last studied" and the learned estimates. */
    private val history = run {
        val today = LocalDate.now()
        val from = today.minusDays(HISTORY_DAYS)
        scheduleRepository.observeRange(from, today).map { blocks ->
            val learned = runCatching {
                HistoryLearner.learn(scheduleRepository.completions(from, today), scheduleRepository.skips(from, today))
            }.getOrDefault(LearnedPatterns.NONE)
            History(blocks, learned)
        }
    }

    private val catalogue = combine(timetableRepository.observeSubjects(), timetableRepository.observeEntries()) { subjects, entries ->
        val names = subjects.associate { it.code to it.shortName }
        val tracks = entries.mapNotNull { it.track }.distinct()
        names to tracks
    }

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<InsightsUiState> = range.flatMapLatest { selected ->
        val today = LocalDate.now()
        val from = today.minusDays(selected.days - 1)
        combine(
            scheduleRepository.observeRange(from, today),
            scheduleRepository.observeSkips(from, today),
            scheduleRepository.observeReschedules(from, today),
            history,
            catalogue,
        ) { blocks, skips, reschedules, past, (names, knownTracks) ->
            build(selected, from, today, blocks, skips.size, reschedules.size, past, names, knownTracks)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(value: InsightsRange) {
        range.value = value
    }

    private fun build(
        selected: InsightsRange,
        from: LocalDate,
        today: LocalDate,
        blocks: List<ScheduleBlock>,
        skipped: Int,
        rescheduled: Int,
        past: History,
        names: Map<String, String>,
        knownTracks: List<SubjectTrack>,
    ): InsightsUiState {
        val work = blocks.filter { it.type.isWork && it.status != BlockStatus.RESCHEDULED && it.status != BlockStatus.CANCELLED }
        val completed = work.filter { it.status.isWorked }

        val byCategory = blocks
            .filter { it.status.isWorked || it.type == BlockType.CLASS }
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf(::actual) }
            .sortedByDescending { it.second }

        val days = (0 until selected.days).map { offset ->
            val date = from.plusDays(offset)
            val forDay = work.filter { it.date == date }
            DayBar(
                date = date,
                workMinutes = forDay.sumOf(::planned),
                completedMinutes = forDay.filter { it.status.isWorked }.sumOf(::actual),
            )
        }

        val buckets = listOf(
            "Morning" to (5 * 60 until 12 * 60),
            "Afternoon" to (12 * 60 until 17 * 60),
            "Evening" to (17 * 60 until 21 * 60),
            "Night" to (21 * 60 until 24 * 60),
        )
        val timeOfDay = buckets.map { (label, window) ->
            TimeOfDaySlice(label = label, minutes = completed.filter { it.start in window }.sumOf(::actual))
        }

        val worked = blocks.filter { it.status.isWorked }
        val balance = listOf(
            BalanceSlice("Study", worked.filter { it.isAcademic }.sumOf(::actual), BalanceKind.STUDY),
            BalanceSlice("Build", worked.filter { it.type == BlockType.BUILD }.sumOf(::actual), BalanceKind.BUILD),
            BalanceSlice("Learning", worked.filter { it.type == BlockType.LEARN }.sumOf(::actual), BalanceKind.LEARNING),
            BalanceSlice("Leisure", blocks.filter { it.type == BlockType.LEISURE && it.end <= nowOr(it, today) }.sumOf { it.duration }, BalanceKind.LEISURE),
            BalanceSlice("Breaks", worked.filter { it.type == BlockType.BREAK }.sumOf(::actual), BalanceKind.BREAKS),
        ).filter { it.minutes > 0 }

        // Per subject, theory and lab apart. A track with no history still shows, so a
        // neglected subject is visible rather than silently absent.
        val academic = work.filter { it.isAcademic }
        val lastByTrack = past.blocks
            .filter { it.isAcademic && it.status.isWorked }
            .mapNotNull { block -> block.track?.let { it to block.date } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }
        val tracks = (knownTracks + academic.mapNotNull { it.track }).distinct().map { track ->
            val forTrack = academic.filter { it.track == track }
            val last = lastByTrack[track]
            TrackInsight(
                track = track,
                name = names[track.subjectCode] ?: track.subjectCode,
                plannedMinutes = forTrack.sumOf(::planned),
                actualMinutes = forTrack.filter { it.status.isWorked }.sumOf(::actual),
                lastStudied = last,
                daysSince = last?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            )
        }.sortedWith(compareByDescending<TrackInsight> { it.daysSince ?: Int.MAX_VALUE }.thenBy { it.name })

        val estimates = past.learned.durationRatio
            .filter { (key, ratio) -> (past.learned.samples[key] ?: 0) >= MIN_SAMPLES && (ratio >= 1.1f || ratio <= 0.9f) }
            .map { (key, ratio) ->
                val label = labelFor(key, names)
                val percent = kotlin.math.abs(((ratio - 1f) * 100).toInt())
                LearnedEstimate(
                    label = label,
                    detail = if (ratio > 1f) "Takes about $percent% longer than planned" else "Takes about $percent% less time than planned",
                )
            }
            .sortedBy { it.label }

        return InsightsUiState(
            range = selected,
            plannedBlocks = work.size,
            completedBlocks = completed.size,
            skippedBlocks = skipped,
            rescheduled = rescheduled,
            minutesByCategory = byCategory,
            days = days,
            timeOfDay = timeOfDay,
            daysWithWork = days.count { it.completedMinutes > 0 },
            averageSessionMinutes = if (completed.isEmpty()) 0 else completed.sumOf(::actual) / completed.size,
            balance = balance,
            tracks = tracks,
            estimates = estimates,
            bestWindow = past.learned.bestWindow,
            loading = false,
        )
    }

    private fun labelFor(key: String, names: Map<String, String>): String = when (key) {
        "build" -> "Build sessions"
        "learn" -> "Learning sessions"
        else -> SubjectTrack.parse(key)?.let { track ->
            (names[track.subjectCode] ?: track.subjectCode) + " " + track.type.label.lowercase()
        } ?: key
    }

    /** Leisure counts once it has happened; for past days that is all of it. */
    private fun nowOr(block: ScheduleBlock, today: LocalDate): Int =
        if (block.date.isBefore(today)) Int.MAX_VALUE else com.margin.app.core.MarginTime.nowMinute()

    companion object {
        private const val HISTORY_DAYS = 60L
        private const val MIN_SAMPLES = 3

        private fun actual(block: ScheduleBlock): Int = if (block.elapsedMinutes > 0) block.elapsedMinutes else block.duration

        private fun planned(block: ScheduleBlock): Int = if (block.plannedMinutes > 0) block.plannedMinutes else block.duration

        fun create(container: AppContainer) = InsightsViewModel(container.scheduleRepository, container.timetableRepository)
    }
}

private val AcademicType.short: String get() = label
