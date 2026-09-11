package com.margin.app.data.prefs

import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.planner.EnergyMode
import com.margin.app.domain.planner.NudgeSettings
import com.margin.app.domain.planner.PlanSettings
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
    val buildEnabled: Boolean = true,

    val learningEnabled: Boolean = true,
    val learningMinutesWeekday: Int = 30,
    val learningMinutesWeekend: Int = 45,
    val learningWindowStart: Int = 17 * 60,
    val learningWindowEnd: Int = 22 * 60 + 30,

    val defaultMinSession: Int = 25,
    val defaultMaxSession: Int = 60,

    val peakWindowStart: Int = 16 * 60,
    val peakWindowEnd: Int = 19 * 60,
    val eveningFatigueAfter: Int = 21 * 60 + 30,

    /** "I prefer studying after 4 PM". -1 means no preference. */
    val preferredStudyStart: Int = -1,
    val studySessionMinutes: Int = 45,
    /** A subject untouched for this many days gets a session of its own. */
    val coverageGapDays: Int = 3,

    val reviewEnabled: Boolean = true,
    val reviewMinutesPerTeachingHour: Int = 20,
    val maxReviewMinutesPerDay: Int = 90,

    val maxWorkMinutesPerDay: Int = 4 * 60,
    /** The most missed work moved into any one day, so a missed day never doubles the next. */
    val carryForwardCap: Int = 90,

    /** A per-day energy override, applied only while [energyModeDate] is today. */
    val energyMode: EnergyMode = EnergyMode.NORMAL,
    val energyModeDate: Long = 0L,

    val use24HourTime: Boolean = false,
    /** system, light or dark. */
    val themeMode: String = "system",

    val notificationsEnabled: Boolean = true,
    val notifyLeadMinutes: Int = 5,
    val notifyBreakEnd: Boolean = true,
    val notifyPlanChanges: Boolean = true,
    val nudgeStudyStart: Boolean = true,
    val nudgeTransitions: Boolean = true,
    val nudgeBreaks: Boolean = true,
    val nudgeMissed: Boolean = true,
    val nudgeBuildLearning: Boolean = true,
    /** Minutes after a session was due before asking whether the user is out. */
    val missedGraceMinutes: Int = 15,
    val notificationSound: Boolean = true,
    val morningPlanMinute: Int = 6 * 60,
    val checkInMinute: Int = 21 * 60 + 30,
    val checkInEnabled: Boolean = true,

    /** The last day whose leftovers were closed out and carried. */
    val lastRolloverDay: Long = 0L,
) {
    val hasStudyPreference: Boolean get() = preferredStudyStart >= 0

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
        energyMode = energyModeFor(forDate),
    )

    fun energyModeFor(epochDay: Long): EnergyMode =
        if (energyModeDate == epochDay) energyMode else EnergyMode.NORMAL

    fun toPlanSettings(): PlanSettings = PlanSettings(
        coverageGapDays = coverageGapDays.coerceIn(1, 14),
        studySessionMinutes = studySessionMinutes.coerceIn(20, 120),
        preferredStudyStart = preferredStudyStart.takeIf { it >= 0 },
        learningWindow = safeRange(learningWindowStart, learningWindowEnd, 17 * 60, 22 * 60 + 30),
        carryForwardCap = carryForwardCap.coerceIn(0, 240),
        buildEnabled = buildEnabled,
        learningEnabled = learningEnabled,
        learningMinutesWeekday = learningMinutesWeekday,
        learningMinutesWeekend = learningMinutesWeekend,
    )

    fun toNudgeSettings(epochDay: Long): NudgeSettings = NudgeSettings(
        enabled = notificationsEnabled,
        leadMinutes = notifyLeadMinutes,
        missedGraceMinutes = missedGraceMinutes.coerceIn(5, 60),
        studyStart = nudgeStudyStart,
        transitions = nudgeTransitions,
        breaks = nudgeBreaks,
        missed = nudgeMissed,
        upNext = true,
        breakOver = notifyBreakEnd,
        buildPrompts = nudgeBuildLearning,
        learningPrompts = nudgeBuildLearning,
        dailyReview = checkInEnabled,
        reviewMinute = checkInMinute,
        wakeMinute = wakeMinute,
        sleepMinute = sleepMinute,
        breakThreshold = continuousWorkBeforeBreak,
        lowEnergy = energyModeFor(epochDay) == EnergyMode.LIGHT,
    )

    private fun safeRange(start: Int, end: Int, fallbackStart: Int, fallbackEnd: Int): TimeRange =
        if (end > start) TimeRange(start, end) else TimeRange(fallbackStart, fallbackEnd)
}
