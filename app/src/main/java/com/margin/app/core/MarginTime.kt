package com.margin.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Margin stores every time as minutes from midnight and every date as an epoch day.
 * The planner therefore never touches a timezone; this file is the only bridge between
 * the engine's integers and the platform's calendar.
 */
object MarginTime {

    const val MINUTES_PER_DAY = 24 * 60

    fun nowMinute(clock: LocalDateTime = LocalDateTime.now()): Int =
        clock.hour * 60 + clock.minute

    fun today(): LocalDate = LocalDate.now()

    fun epochDay(date: LocalDate): Long = date.toEpochDay()

    fun dateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun toLocalTime(minute: Int): LocalTime =
        LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59))

    fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

    fun toEpochMillis(epochDay: Long, minute: Int): Long {
        val date = dateOf(epochDay)
        val clamped = minute.coerceIn(0, MINUTES_PER_DAY - 1)
        return date.atTime(clamped / 60, clamped % 60)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /** "9:15 AM" or "21:45" depending on the device locale preference passed in. */
    fun formatTime(minute: Int, use24Hour: Boolean): String {
        val m = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = m / 60
        val mm = m % 60
        return if (use24Hour) {
            "%02d:%02d".format(h, mm)
        } else {
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            "%d:%02d %s".format(h12, mm, suffix)
        }
    }

    /** "45 min", "1h", "1h 30m". Never "0h 45m". */
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0 min"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Compact form for dense rows: "45m", "1h", "1h30". */
    fun formatDurationShort(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h${m}"
        }
    }

    fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
            ", " + date.dayOfMonth + " " + date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    fun dayName(day: DayOfWeek, short: Boolean = false): String =
        day.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /**
     * Parses "16:30", "4:30 pm", "4pm", "1630". Returns null rather than guessing.
     * Used by the offline command parser and by manual time entry.
     */
    fun parseTime(raw: String): Int? {
        val text = raw.trim().lowercase(Locale.US)
        if (text.isEmpty()) return null
        var pm = false
        var am = false
        var body = text
        if (body.endsWith("pm") || body.endsWith("p.m.")) { pm = true; body = body.removeSuffix("p.m.").removeSuffix("pm") }
        if (body.endsWith("am") || body.endsWith("a.m.")) { am = true; body = body.removeSuffix("a.m.").removeSuffix("am") }
        body = body.trim().replace('.', ':')
        val hour: Int
        val minute: Int
        if (body.contains(':')) {
            val parts = body.split(':')
            if (parts.size != 2) return null
            hour = parts[0].trim().toIntOrNull() ?: return null
            minute = parts[1].trim().toIntOrNull() ?: return null
        } else {
            val digits = body.filter { it.isDigit() }
            if (digits.isEmpty() || digits.length > 4) return null
            when (digits.length) {
                1, 2 -> { hour = digits.toInt(); minute = 0 }
                3 -> { hour = digits.substring(0, 1).toInt(); minute = digits.substring(1).toInt() }
                else -> { hour = digits.substring(0, 2).toInt(); minute = digits.substring(2).toInt() }
            }
        }
        if (minute !in 0..59) return null
        var h = hour
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0
        if (h !in 0..23) return null
        return h * 60 + minute
    }
}
