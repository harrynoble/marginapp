package com.margin.app.data.prefs

import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.planner.PlannerPreferences

/**
 * Everything the user can tune. Held as one immutable value so a screen can edit a copy and
 * save it in a single write, and so the planner receives a consistent snapshot.
 */
data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val seedVersion: Int = 0,

    val wakeMinute: Int = 6 * 60 + 30,
    val sleepMinute: Int = 23 * 60,

    val commuteMinutes: Int = 45,
    val decompressionMinutes: Int = 30,
    val minGapMinutes: Int = 5,
    val minUsefulSlotMinutes: Int = 20,

    val continuousWorkBeforeBreak: Int = 85,
    val shortBreakMinutes: Int = 15,

    val minLeisureMinutes: Int = 90,
    val leisureWindowStart: Int = 19 * 60,
    val leisureWindowEnd: Int = 23 * 60,

    val buildMinutesWeekday: Int = 60,
    val buildMinutesWeekend: Int = 120,
    val buildWindowStart: Int = 16 * 60,
    val buildWindowEnd: Int = 22 * 60,

    val defaultMinSession: Int = 25,
    val defaultMaxSession: Int = 60,

    val peakWindowStart: Int = 16 * 60,
    val peakWindowEnd: Int = 19 * 60,
    val eveningFatigueAfter: Int = 21 * 60 + 30,

    val reviewEnabled: Boolean = true,
    val reviewMinutesPerTeachingHour: Int = 20,
    val maxReviewMinutesPerDay: Int = 90,

    val maxWorkMinutesPerDay: Int = 4 * 60,

    /** A per-day energy override, applied only while [energyModeDate] is today. */
    val energyMode: EnergyMode = EnergyMode.NORMAL,
    val energyModeDate: Long = 0L,

    val use24HourTime: Boolean = false,

    val notificationsEnabled: Boolean = true,
    val notifyLeadMinutes: Int = 5,
    val notifyBreakEnd: Boolean = true,
    val notifyPlanChanges: Boolean = true,
    val morningPlanMinute: Int = 6 * 60,
    val checkInMinute: Int = 21 * 60 + 30,
    val checkInEnabled: Boolean = true,
) {
    /** Translates the stored settings into the shape the engine reads. */
    fun toPlannerPreferences(forDate: Long): PlannerPreferences = PlannerPreferences(
        wakeMinute = wakeMinute,
        sleepMinute = sleepMinute,
        commuteMinutes = commuteMinutes,
        decompressionMinutes = decompressionMinutes,
        minGapMinutes = minGapMinutes,
        minUsefulSlotMinutes = minUsefulSlotMinutes,
        continuousWorkBeforeBreak = continuousWorkBeforeBreak,
        shortBreakMinutes = shortBreakMinutes,
        minLeisureMinutes = minLeisureMinutes,
        leisureWindow = safeRange(leisureWindowStart, leisureWindowEnd, 19 * 60, 23 * 60),
        buildWindow = safeRange(buildWindowStart, buildWindowEnd, 16 * 60, 22 * 60),
        buildMinutesWeekday = buildMinutesWeekday,
        buildMinutesWeekend = buildMinutesWeekend,
        defaultMinSession = defaultMinSession,
        defaultMaxSession = defaultMaxSession,
        peakWindow = safeRange(peakWindowStart, peakWindowEnd, 16 * 60, 19 * 60),
        eveningFatigueAfter = eveningFatigueAfter,
        reviewEnabled = reviewEnabled,
        reviewMinutesPerTeachingHour = reviewMinutesPerTeachingHour,
        maxReviewMinutesPerDay = maxReviewMinutesPerDay,
        maxWorkMinutesPerDay = maxWorkMinutesPerDay,
        energyMode = if (energyModeDate == forDate) energyMode else EnergyMode.NORMAL,
    )

    private fun safeRange(start: Int, end: Int, fallbackStart: Int, fallbackEnd: Int): TimeRange =
        if (end > start) TimeRange(start, end) else TimeRange(fallbackStart, fallbackEnd)
}
