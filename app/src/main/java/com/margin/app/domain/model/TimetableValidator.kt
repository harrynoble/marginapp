package com.margin.app.domain.model

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** One way the stored timetable differs from the one the user confirmed. */
data class TimetableIssue(
    val kind: Kind,
    val day: DayOfWeek,
    val start: Int,
    /** The class as confirmed; null for an entry that should not be there at all. */
    val expected: TimetableEntry?,
    /** The class as stored; null when it is missing. */
    val actual: TimetableEntry?,
) {
    enum class Kind { MISSING, UNEXPECTED, DUPLICATE, WRONG_TIME, WRONG_TYPE, WRONG_SUBJECT }

    /** A sentence a person can check against the paper timetable. */
    val description: String
        get() {
            val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val name = (expected ?: actual)?.let { it.subjectCode ?: it.title } ?: "A class"
            return when (kind) {
                Kind.MISSING -> "$dayName ${clock(expected!!.start)}–${clock(expected.end)}: $name ${expected.kind.label.lowercase()} is missing."
                Kind.UNEXPECTED -> "$dayName ${clock(actual!!.start)}–${clock(actual.end)}: $name is not in the confirmed timetable."
                Kind.DUPLICATE -> "$dayName ${clock(actual!!.start)}: $name appears more than once."
                Kind.WRONG_TIME -> "$dayName: $name should be ${clock(expected!!.start)}–${clock(expected.end)}, " +
                    "not ${clock(actual!!.start)}–${clock(actual.end)}."
                Kind.WRONG_TYPE -> "$dayName ${clock(expected!!.start)}: $name should be a ${expected.kind.label.lowercase()}, " +
                    "not a ${actual!!.kind.label.lowercase()}."
                Kind.WRONG_SUBJECT -> "$dayName ${clock(expected!!.start)}: should be ${expected.subjectCode ?: expected.title}, " +
                    "not ${actual!!.subjectCode ?: actual.title}."
            }
        }

    private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}

data class TimetableCheck(
    val issues: List<TimetableIssue>,
    val confirmedCount: Int,
    val storedCount: Int,
) {
    val verified: Boolean get() = issues.isEmpty()
}

/**
 * Compares the stored week with the week the user last confirmed (the shipped timetable, a
 * reviewed import, or their own edits) and names every difference: a class dropped, added,
 * duplicated, moved, retyped or swapped for another subject. Nothing is fixed silently; the
 * user sees the list and decides. Pure, so it is unit tested directly.
 */
object TimetableValidator {

    fun validate(confirmed: List<TimetableEntry>, stored: List<TimetableEntry>): TimetableCheck {
        val reference = confirmed.filter { it.active }.sortedWith(ORDER)
        val actual = stored.filter { it.active }.sortedWith(ORDER)
        val issues = mutableListOf<TimetableIssue>()

        // The same class stored twice in the same slot.
        val seen = mutableSetOf<Triple<DayOfWeek, Int, String>>()
        val remaining = mutableListOf<TimetableEntry>()
        for (entry in actual) {
            if (!seen.add(Triple(entry.dayOfWeek, entry.start, identity(entry)))) {
                issues += TimetableIssue(TimetableIssue.Kind.DUPLICATE, entry.dayOfWeek, entry.start, null, entry)
            } else {
                remaining += entry
            }
        }

        for (expected in reference) {
            val exact = remaining.firstOrNull {
                it.dayOfWeek == expected.dayOfWeek && it.start == expected.start && sameSubject(it, expected)
            }
            if (exact != null) {
                remaining.remove(exact)
                when {
                    exact.end != expected.end ->
                        issues += TimetableIssue(TimetableIssue.Kind.WRONG_TIME, expected.dayOfWeek, expected.start, expected, exact)
                    exact.kind != expected.kind ->
                        issues += TimetableIssue(TimetableIssue.Kind.WRONG_TYPE, expected.dayOfWeek, expected.start, expected, exact)
                }
                continue
            }
            val sameSlot = remaining.firstOrNull { it.dayOfWeek == expected.dayOfWeek && it.start == expected.start }
            if (sameSlot != null) {
                remaining.remove(sameSlot)
                issues += TimetableIssue(TimetableIssue.Kind.WRONG_SUBJECT, expected.dayOfWeek, expected.start, expected, sameSlot)
                continue
            }
            val moved = remaining.firstOrNull {
                it.dayOfWeek == expected.dayOfWeek && sameSubject(it, expected) && it.kind == expected.kind
            }
            if (moved != null) {
                remaining.remove(moved)
                issues += TimetableIssue(TimetableIssue.Kind.WRONG_TIME, expected.dayOfWeek, expected.start, expected, moved)
                continue
            }
            issues += TimetableIssue(TimetableIssue.Kind.MISSING, expected.dayOfWeek, expected.start, expected, null)
        }

        for (extra in remaining) {
            issues += TimetableIssue(TimetableIssue.Kind.UNEXPECTED, extra.dayOfWeek, extra.start, null, extra)
        }

        return TimetableCheck(
            issues = issues.sortedWith(compareBy({ it.day.value }, { it.start }, { it.kind.ordinal })),
            confirmedCount = reference.size,
            storedCount = actual.size,
        )
    }

    private val ORDER = compareBy<TimetableEntry>({ it.dayOfWeek.value }, { it.start }, { it.end }, { identity(it) })

    /** Subject code when there is one; otherwise the kind and title, for recess and the like. */
    private fun identity(entry: TimetableEntry): String =
        entry.subjectCode?.trim()?.uppercase() ?: (entry.kind.key + ":" + entry.title.trim().lowercase())

    private fun sameSubject(a: TimetableEntry, b: TimetableEntry): Boolean = identity(a) == identity(b)
}
