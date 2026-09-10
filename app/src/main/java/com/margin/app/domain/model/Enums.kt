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
    REVIEW("review"),
    BUILD("build"),
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
    val isWork: Boolean get() = this == TASK || this == REVIEW || this == BUILD

    /** Blocks the user can start, complete or skip. */
    val isActionable: Boolean
        get() = this == TASK || this == REVIEW || this == BUILD || this == BREAK || this == LEISURE

    val isProtected: Boolean get() = this == BREAK || this == LEISURE || this == MEAL

    companion object {
        fun fromKey(key: String?): BlockType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FREE
    }
}

enum class BlockStatus { PLANNED, ACTIVE, DONE, SKIPPED, PARTIAL }

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
