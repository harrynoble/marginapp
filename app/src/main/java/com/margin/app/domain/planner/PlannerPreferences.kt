package com.margin.app.domain.planner

import com.margin.app.domain.model.TimeRange

/** How light or heavy today should be. Set by the user, or by "I am tired today". */
enum class EnergyMode(val key: String, val label: String) {
    LIGHT("light", "Light day"),
    NORMAL("normal", "Normal"),
    FOCUSED("focused", "Focused day");

    companion object {
        fun fromKey(key: String?): EnergyMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * Every knob the engine reads. Held here rather than inside the engine so that a test can
 * construct an exact scenario, and so the settings screen has one thing to edit.
 */
data class PlannerPreferences(
    val wakeMinute: Int = 7 * 60,
    val sleepMinute: Int = 23 * 60 + 30,

    // Transitions. These are what stop the plan reading like a factory line.
    val commuteMinutes: Int = 45,
    val decompressionMinutes: Int = 30,
    val longCommitmentMinutes: Int = 150,
    val minGapMinutes: Int = 5,
    val minUsefulSlotMinutes: Int = 20,

    // Breaks.
    val continuousWorkBeforeBreak: Int = 85,
    val shortBreakMinutes: Int = 15,

    // Protected time. Floors, not targets.
    val minLeisureMinutes: Int = 90,
    val leisureWindow: TimeRange = TimeRange(19 * 60, 23 * 60),
    val minLeisureChunk: Int = 30,

    val buildMinutesWeekday: Int = 60,
    val buildMinutesWeekend: Int = 120,
    val buildWindow: TimeRange = TimeRange(16 * 60, 22 * 60),
    val minBuildChunk: Int = 30,

    // Sessions.
    val defaultMinSession: Int = 25,
    val defaultMaxSession: Int = 60,

    // Energy shape of the day.
    val peakWindow: TimeRange = TimeRange(9 * 60, 12 * 60),
    val eveningFatigueAfter: Int = 21 * 60,

    // Post-class revision.
    val reviewEnabled: Boolean = true,
    val reviewMinutesPerTeachingHour: Int = 20,
    val maxReviewMinutesPerDay: Int = 90,
    val minReviewSession: Int = 20,

    val granularityMinutes: Int = 5,
    val maxWorkMinutesPerDay: Int = 6 * 60,
    val energyMode: EnergyMode = EnergyMode.NORMAL,
) {
    /** The work ceiling once the energy mode for the day is applied. */
    val effectiveWorkCeiling: Int
        get() = when (energyMode) {
            EnergyMode.LIGHT -> (maxWorkMinutesPerDay * 0.55f).toInt()
            EnergyMode.NORMAL -> maxWorkMinutesPerDay
            EnergyMode.FOCUSED -> (maxWorkMinutesPerDay * 1.2f).toInt()
        }

    val effectiveBreakInterval: Int
        get() = when (energyMode) {
            EnergyMode.LIGHT -> (continuousWorkBeforeBreak * 0.65f).toInt().coerceAtLeast(30)
            EnergyMode.NORMAL -> continuousWorkBeforeBreak
            EnergyMode.FOCUSED -> (continuousWorkBeforeBreak * 1.25f).toInt()
        }

    val effectiveLeisureFloor: Int
        get() = when (energyMode) {
            EnergyMode.LIGHT -> (minLeisureMinutes * 1.4f).toInt()
            else -> minLeisureMinutes
        }

    fun roundUp(minutes: Int): Int {
        val g = granularityMinutes.coerceAtLeast(1)
        return ((minutes + g - 1) / g) * g
    }

    fun roundDown(minutes: Int): Int {
        val g = granularityMinutes.coerceAtLeast(1)
        return (minutes / g) * g
    }
}
