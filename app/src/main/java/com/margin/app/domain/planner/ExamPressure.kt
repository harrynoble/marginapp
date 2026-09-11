package com.margin.app.domain.planner

import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.SubjectTrack
import java.time.LocalDate

/** How close the next exam is, which is what exam mode keys off. */
enum class ExamIntensity(val label: String) {
    NONE("No exams soon"),
    APPROACHING("Exams approaching"),
    CLOSE("Exam mode"),
    IMMINENT("Exam mode");
}

/**
 * Exam mode does not run a second planner. It changes the weights of the one that exists:
 * academic work gains priority and room, build and learning shrink, and leisure narrows a
 * little but never disappears.
 */
data class ExamPressure(
    val intensity: ExamIntensity,
    val nearest: Exam?,
    val nearestDays: Int?,
    /** Days to the next exam per track. 0 means the exam is today. */
    val daysByTrack: Map<SubjectTrack, Int>,
    val upcoming: List<Exam>,
) {
    val active: Boolean get() = intensity == ExamIntensity.CLOSE || intensity == ExamIntensity.IMMINENT

    val buildFactor: Float
        get() = when (intensity) {
            ExamIntensity.NONE -> 1f
            ExamIntensity.APPROACHING -> 0.75f
            ExamIntensity.CLOSE -> 0.5f
            ExamIntensity.IMMINENT -> 0f
        }

    fun learningFactor(pauseDuringExams: Boolean): Float = when (intensity) {
        ExamIntensity.NONE -> 1f
        ExamIntensity.APPROACHING -> if (pauseDuringExams) 0.5f else 0.75f
        ExamIntensity.CLOSE -> if (pauseDuringExams) 0f else 0.5f
        ExamIntensity.IMMINENT -> 0f
    }

    /** Leisure is reduced, never removed. The floor stays a floor. */
    val leisureFactor: Float
        get() = when (intensity) {
            ExamIntensity.NONE, ExamIntensity.APPROACHING -> 1f
            ExamIntensity.CLOSE -> 0.85f
            ExamIntensity.IMMINENT -> 0.65f
        }

    val ceilingFactor: Float
        get() = when (intensity) {
            ExamIntensity.NONE -> 1f
            ExamIntensity.APPROACHING -> 1.1f
            ExamIntensity.CLOSE -> 1.25f
            ExamIntensity.IMMINENT -> 1.4f
        }

    companion object {
        val NONE = ExamPressure(ExamIntensity.NONE, null, null, emptyMap(), emptyList())
    }
}

object ExamPlanner {

    /** Exams further out than this do not change today. */
    const val HORIZON_DAYS = 21

    fun pressure(date: LocalDate, exams: List<Exam>): ExamPressure {
        val upcoming = exams
            .filter { !it.date.isBefore(date) }
            .filter { daysUntil(date, it) <= HORIZON_DAYS }
            .sortedWith(compareBy({ it.date }, { it.startMinute ?: 0 }, { it.id }))
        if (upcoming.isEmpty()) return ExamPressure.NONE

        val nearest = upcoming.first()
        val nearestDays = daysUntil(date, nearest)
        val byTrack = mutableMapOf<SubjectTrack, Int>()
        for (exam in upcoming) {
            val track = exam.track ?: continue
            val days = daysUntil(date, exam)
            if ((byTrack[track] ?: Int.MAX_VALUE) > days) byTrack[track] = days
        }

        return ExamPressure(
            intensity = when {
                nearestDays <= 3 -> ExamIntensity.IMMINENT
                nearestDays <= 10 -> ExamIntensity.CLOSE
                else -> ExamIntensity.APPROACHING
            },
            nearest = nearest,
            nearestDays = nearestDays,
            daysByTrack = byTrack,
            upcoming = upcoming,
        )
    }

    /** Study wanted today for an exam [days] away. The closer it is, the more. */
    fun studyMinutesFor(days: Int): Int = when {
        days <= 1 -> 120
        days <= 3 -> 90
        days <= 7 -> 60
        days <= 14 -> 45
        else -> 30
    }

    /** Planner weight for exam study [days] away. Above 50 it may use time kept for build. */
    fun importanceFor(days: Int): Int = when {
        days <= 1 -> 80
        days <= 3 -> 55
        days <= 7 -> 35
        days <= 14 -> 20
        else -> 10
    }

    fun daysUntil(date: LocalDate, exam: Exam): Int =
        (exam.date.toEpochDay() - date.toEpochDay()).toInt()
}
