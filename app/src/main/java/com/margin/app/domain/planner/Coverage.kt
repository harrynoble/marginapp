package com.margin.app.domain.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.SubjectTrack
import java.time.LocalDate

/**
 * Where one subject track stands: when it was last studied, how much it has had lately, and
 * what is coming up for it. Theory and lab of the same subject each get their own.
 */
data class TrackCoverage(
    val track: SubjectTrack,
    val lastStudied: LocalDate?,
    /** Whole days since it was last studied, or null if it never has been. */
    val daysSinceStudied: Int?,
    val weekMinutes: Int,
    val weekSessions: Int,
    val totalMinutes: Int,
    val sessions: Int,
    /** Minutes that were planned this week, including sessions that were skipped or missed. */
    val weekPlannedMinutes: Int,
    val weekSkipped: Int,
    val lastClass: LocalDate?,
    val nextExamDays: Int?,
    /**
     * How long it has effectively gone untouched: days since it was studied, or, if it never
     * has been, days since its most recent class.
     */
    val neglectDays: Int,
) {
    val averageSession: Int get() = if (sessions == 0) 0 else totalMinutes / sessions
}

/**
 * Subject coverage, the mechanism that stops a subject from quietly disappearing.
 *
 * Pure and deterministic: history and the timetable are passed in, the date is passed in.
 */
object CoverageCalculator {

    /** How far back history is read. Older sessions no longer say much about coverage. */
    const val WINDOW_DAYS = 60L

    fun compute(
        date: LocalDate,
        tracks: Collection<SubjectTrack>,
        completions: List<CompletionRecord>,
        skips: List<SkipRecord>,
        lastClassByTrack: Map<SubjectTrack, LocalDate>,
        exams: List<Exam>,
    ): Map<SubjectTrack, TrackCoverage> {
        val windowStart = date.minusDays(WINDOW_DAYS)
        val weekStart = date.minusDays(6)

        val studied = completions
            .filter { !it.date.isAfter(date) && !it.date.isBefore(windowStart) }
            .filter { it.category == Category.ACADEMICS && it.subjectCode != null }
            .groupBy { SubjectTrack(it.subjectCode!!, it.academicType ?: AcademicType.THEORY) }

        val skipped = skips
            .filter { !it.date.isAfter(date) && !it.date.isBefore(weekStart) && it.subjectCode != null }
            .groupBy { SubjectTrack(it.subjectCode!!, it.academicType ?: AcademicType.THEORY) }

        return tracks.associateWith { track ->
            val records = studied[track].orEmpty()
            val week = records.filter { !it.date.isBefore(weekStart) }
            val lastStudied = records.maxOfOrNull { it.date }
            val daysSince = lastStudied?.let { (date.toEpochDay() - it.toEpochDay()).toInt() }
            val lastClass = lastClassByTrack[track]
            val sinceClass = lastClass?.let { (date.toEpochDay() - it.toEpochDay()).toInt() }
            val exam = exams
                .filter { it.subjectCode == track.subjectCode && it.academicType == track.type }
                .filter { !it.date.isBefore(date) }
                .minByOrNull { it.date }
            val weekSkips = skipped[track].orEmpty()

            TrackCoverage(
                track = track,
                lastStudied = lastStudied,
                daysSinceStudied = daysSince,
                weekMinutes = week.sumOf { it.minutes },
                weekSessions = week.size,
                totalMinutes = records.sumOf { it.minutes },
                sessions = records.size,
                weekPlannedMinutes = week.sumOf { maxOf(it.plannedMinutes, it.minutes) } +
                    weekSkips.sumOf { it.plannedMinutes },
                weekSkipped = weekSkips.size,
                lastClass = lastClass,
                nextExamDays = exam?.let { (it.date.toEpochDay() - date.toEpochDay()).toInt() },
                neglectDays = daysSince ?: sinceClass?.plus(1) ?: NEVER_STUDIED_DAYS,
            )
        }
    }

    /**
     * How strongly a track is asking for time. Grows by the day while it goes unstudied,
     * rises further as its exam gets closer, and falls with the time it has already had this
     * week, so the planner balances subjects instead of rotating them blindly.
     */
    fun neglectScore(coverage: TrackCoverage, importance: Int): Int {
        var score = coverage.neglectDays.coerceAtMost(MAX_COUNTED_DAYS) * PER_DAY
        score += importance * PER_IMPORTANCE
        coverage.nextExamDays?.let { days ->
            score += when {
                days <= 3 -> 30
                days <= 7 -> 18
                days <= 14 -> 8
                else -> 0
            }
        }
        score -= (coverage.weekMinutes / 30) * PER_HALF_HOUR_STUDIED
        return score.coerceAtLeast(0)
    }

    /** Tracks come from the weekly timetable: every subject taught as theory, as lab, or both. */
    fun tracksFrom(entries: List<com.margin.app.domain.model.TimetableEntry>): Set<SubjectTrack> =
        entries.mapNotNull { it.track }.toSet()

    /** The most recent date on or before [date] on which each track had a class. */
    fun lastClassDates(
        date: LocalDate,
        weekly: List<com.margin.app.domain.model.TimetableEntry>,
    ): Map<SubjectTrack, LocalDate> {
        val out = mutableMapOf<SubjectTrack, LocalDate>()
        for (entry in weekly) {
            val track = entry.track ?: continue
            val back = ((date.dayOfWeek.value - entry.dayOfWeek.value) + 7) % 7
            val occurred = date.minusDays(back.toLong())
            val known = out[track]
            if (known == null || occurred.isAfter(known)) out[track] = occurred
        }
        return out
    }

    private const val NEVER_STUDIED_DAYS = 3
    private const val MAX_COUNTED_DAYS = 10
    private const val PER_DAY = 7
    private const val PER_IMPORTANCE = 6
    private const val PER_HALF_HOUR_STUDIED = 3
}
