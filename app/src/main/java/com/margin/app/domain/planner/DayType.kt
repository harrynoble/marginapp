package com.margin.app.domain.planner

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What kind of day it is, which decides how the same engine shapes it.
 *
 * - [WEEKDAY]: college comes first; review, breaks, build, learning and leisure fit after it.
 * - [WEEKEND]: a weekly review across every subject, with more room for build, learning and rest.
 * - [HOLIDAY]: a college day the user has taken off; planned like a weekend, for that date only.
 * - [EXAM_PERIOD]: exams are close, so academics lead and build and learning shrink.
 */
enum class DayType(val key: String, val label: String) {
    WEEKDAY("weekday", "Weekday"),
    WEEKEND("weekend", "Weekend review"),
    HOLIDAY("holiday", "Holiday"),
    EXAM_PERIOD("exam", "Exam period");

    companion object {
        fun isWeekend(date: LocalDate): Boolean =
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

        /** Exams outrank everything; a holiday outranks the weekend it might fall on. */
        fun of(date: LocalDate, holiday: Boolean, examActive: Boolean): DayType = when {
            examActive -> EXAM_PERIOD
            holiday -> HOLIDAY
            isWeekend(date) -> WEEKEND
            else -> WEEKDAY
        }

        fun fromKey(key: String?): DayType = entries.firstOrNull { it.key == key } ?: WEEKDAY
    }
}
