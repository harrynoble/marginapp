package com.margin.app.domain.model

/**
 * Top-level classification for everything that consumes time. The set is deliberately
 * small; projects and subjects provide the finer grain.
 */
enum class Category(val key: String, val label: String) {
    ACADEMICS("academics", "Academics"),
    BUILD("build", "Build"),
    LEARNING("learning", "Learning"),
    PERSONAL("personal", "Personal"),
    HEALTH("health", "Health"),
    LEISURE("leisure", "Leisure"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String?): Category =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?: entries.firstOrNull { it.label.equals(key, ignoreCase = true) }
                ?: OTHER
    }
}

enum class Priority(val key: String, val label: String, val weight: Int) {
    LOW("low", "Low", 0),
    NORMAL("normal", "Normal", 10),
    HIGH("high", "High", 22),
    CRITICAL("critical", "Critical", 40);

    companion object {
        fun fromKey(key: String?): Priority =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NORMAL
    }
}

enum class Difficulty(val key: String, val label: String) {
    EASY("easy", "Easy"),
    MODERATE("moderate", "Moderate"),
    HARD("hard", "Hard");

    companion object {
        fun fromKey(key: String?): Difficulty =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MODERATE
    }
}

/** How much of the user's capacity a piece of work asks for. */
enum class EnergyLevel(val key: String, val label: String) {
    LOW("low", "Light"),
    MEDIUM("medium", "Moderate"),
    HIGH("high", "Demanding");

    companion object {
        fun fromKey(key: String?): EnergyLevel =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MEDIUM
    }
}

enum class TaskStatus { ACTIVE, DONE, ARCHIVED }

/** What a block on the timeline represents. */
enum class BlockType(val key: String) {
    CLASS("class"),
    TASK("task"),
    /** Revision of a class that happened today. */
    REVIEW("review"),
    /** Study of a subject for coverage or an exam, not tied to a class today. */
    STUDY("study"),
    BUILD("build"),
    /** Time spent on a learning goal: a new skill, not coursework. */
    LEARN("learn"),
    BREAK("break"),
    MEAL("meal"),
    COMMUTE("commute"),
    DECOMPRESS("decompress"),
    LEISURE("leisure"),
    EVENT("event"),
    ROUTINE("routine"),
    SLEEP("sleep"),
    FREE("free");

    /** Free time and sleep are shown but never counted as planned work. */
    val isWork: Boolean
        get() = this == TASK || this == REVIEW || this == STUDY || this == BUILD || this == LEARN

    /** Blocks the user can start, complete or skip. */
    val isActionable: Boolean get() = isWork || this == BREAK || this == LEISURE

    val isProtected: Boolean get() = this == BREAK || this == LEISURE || this == MEAL

    companion object {
        fun fromKey(key: String?): BlockType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FREE
    }
}

/**
 * The life of a block. PLANNED is shown as "Upcoming" and DONE as "Completed"; the stored
 * names stay as they were so existing rows keep their meaning.
 */
enum class BlockStatus(val label: String) {
    PLANNED("Upcoming"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    DONE("Completed"),
    SKIPPED("Skipped"),
    /** Started, worked on, closed before the planned time was used. */
    PARTIAL("Partly done"),
    /** Its time passed without it being started. It is recorded, never silently dropped. */
    MISSED("Missed"),
    CANCELLED("Cancelled"),
    /** Moved to another day; the new block carries on, this one keeps the history. */
    RESCHEDULED("Rescheduled"),
    /** Running or paused when the day ended. Whatever was worked is still credited. */
    INTERRUPTED("Interrupted");

    /** Still part of the live day: a planned block can move, a running one is in progress. */
    val isOpen: Boolean get() = this == PLANNED || this == ACTIVE || this == PAUSED

    /** Some or all of the planned time was actually worked. */
    val isWorked: Boolean get() = this == DONE || this == PARTIAL || this == INTERRUPTED
}

enum class TimetableKind(val key: String, val label: String) {
    LECTURE("lecture", "Lecture"),
    LAB("lab", "Lab"),
    TUTORIAL("tutorial", "Tutorial"),
    MENTORING("mentoring", "Mentoring"),
    DEPARTMENT("department", "Department hour"),
    RECESS("recess", "Recess");

    /** Recess and department hours do not generate revision work. */
    val isTeaching: Boolean get() = this == LECTURE || this == LAB || this == TUTORIAL

    companion object {
        fun fromKey(key: String?): TimetableKind =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LECTURE
    }
}

/**
 * Theory and lab are different kinds of academic work and are tracked apart: a subject with
 * both a lecture and a lab has two coverage tracks, two sets of study time and two histories.
 */
enum class AcademicType(val key: String, val label: String) {
    THEORY("theory", "Theory"),
    LAB("lab", "Lab");

    companion object {
        fun fromKey(key: String?): AcademicType? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }

        /** Lectures and tutorials are theory; labs are labs; recess and the rest are neither. */
        fun of(kind: TimetableKind): AcademicType? = when (kind) {
            TimetableKind.LAB -> LAB
            TimetableKind.LECTURE, TimetableKind.TUTORIAL -> THEORY
            else -> null
        }
    }
}

enum class ExceptionType(val key: String) {
    HOLIDAY("holiday"),
    CANCELLED("cancelled"),
    EXTRA("extra");

    companion object {
        fun fromKey(key: String?): ExceptionType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CANCELLED
    }
}

enum class RoutineKind(val key: String) {
    SLEEP("sleep"),
    MEAL("meal"),
    COMMUTE("commute"),
    COMMITMENT("commitment");

    companion object {
        fun fromKey(key: String?): RoutineKind =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: COMMITMENT
    }
}

enum class SkipResolution { LATER_TODAY, TOMORROW, DROP_TODAY, REMOVED, UNRESOLVED }

/** Whether a skip record was a choice the user made or a session whose time simply passed. */
enum class SkipKind(val key: String) {
    SKIPPED("skipped"),
    MISSED("missed");

    companion object {
        fun fromKey(key: String?): SkipKind =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SKIPPED
    }
}

/** The answer to "build today?" or "learn something today?". Unasked is not a no. */
enum class Decision(val key: String) {
    UNASKED("unasked"),
    ACCEPTED("accepted"),
    DECLINED("declined");

    companion object {
        fun fromKey(key: String?): Decision =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: UNASKED
    }
}

enum class BreakReason(val key: String, val label: String) {
    SUGGESTED("suggested", "Suggested"),
    MANUAL("manual", "Taken"),
    MEAL("meal", "Meal"),
    PLANNED("planned", "Planned");

    companion object {
        fun fromKey(key: String?): BreakReason =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MANUAL
    }
}
