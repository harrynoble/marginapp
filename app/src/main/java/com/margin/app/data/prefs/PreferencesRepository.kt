package com.margin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.margin.app.domain.planner.EnergyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "margin_settings")

/**
 * Reads and writes [UserPreferences]. A corrupt or missing store yields defaults rather than
 * an exception, because losing settings must never stop the planner from running.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val seedVersion = intPreferencesKey("seed_version")
        val wake = intPreferencesKey("wake_minute")
        val sleep = intPreferencesKey("sleep_minute")
        val commute = intPreferencesKey("commute_minutes")
        val decompression = intPreferencesKey("decompression_minutes")
        val minGap = intPreferencesKey("min_gap_minutes")
        val minSlot = intPreferencesKey("min_useful_slot")
        val workBeforeBreak = intPreferencesKey("work_before_break")
        val shortBreak = intPreferencesKey("short_break_minutes")
        val leisureFloor = intPreferencesKey("leisure_floor")
        val leisureStart = intPreferencesKey("leisure_window_start")
        val leisureEnd = intPreferencesKey("leisure_window_end")
        val buildWeekday = intPreferencesKey("build_weekday")
        val buildWeekend = intPreferencesKey("build_weekend")
        val buildStart = intPreferencesKey("build_window_start")
        val buildEnd = intPreferencesKey("build_window_end")
        val buildEnabled = booleanPreferencesKey("build_enabled")
        val learningEnabled = booleanPreferencesKey("learning_enabled")
        val learningWeekday = intPreferencesKey("learning_weekday")
        val learningWeekend = intPreferencesKey("learning_weekend")
        val learningStart = intPreferencesKey("learning_window_start")
        val learningEnd = intPreferencesKey("learning_window_end")
        val minSession = intPreferencesKey("default_min_session")
        val maxSession = intPreferencesKey("default_max_session")
        val peakStart = intPreferencesKey("peak_start")
        val peakEnd = intPreferencesKey("peak_end")
        val fatigueAfter = intPreferencesKey("fatigue_after")
        val studyStart = intPreferencesKey("preferred_study_start")
        val studySession = intPreferencesKey("study_session_minutes")
        val coverageGap = intPreferencesKey("coverage_gap_days")
        val reviewEnabled = booleanPreferencesKey("review_enabled")
        val reviewPerHour = intPreferencesKey("review_per_hour")
        val reviewMax = intPreferencesKey("review_max")
        val workCeiling = intPreferencesKey("work_ceiling")
        val carryCap = intPreferencesKey("carry_forward_cap")
        val energyMode = stringPreferencesKey("energy_mode")
        val energyModeDate = longPreferencesKey("energy_mode_date")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val themeMode = stringPreferencesKey("theme_mode")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val notifyLead = intPreferencesKey("notify_lead")
        val notifyBreakEnd = booleanPreferencesKey("notify_break_end")
        val notifyPlanChanges = booleanPreferencesKey("notify_plan_changes")
        val nudgeStudyStart = booleanPreferencesKey("nudge_study_start")
        val nudgeTransitions = booleanPreferencesKey("nudge_transitions")
        val nudgeBreaks = booleanPreferencesKey("nudge_breaks")
        val nudgeMissed = booleanPreferencesKey("nudge_missed")
        val nudgeBuildLearning = booleanPreferencesKey("nudge_build_learning")
        val missedGrace = intPreferencesKey("missed_grace_minutes")
        val notificationSound = booleanPreferencesKey("notification_sound")
        val morningPlan = intPreferencesKey("morning_plan_minute")
        val checkIn = intPreferencesKey("check_in_minute")
        val checkInEnabled = booleanPreferencesKey("check_in_enabled")
        val lastRollover = longPreferencesKey("last_rollover_day")
    }

    val preferences: Flow<UserPreferences> = context.settingsStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toUserPreferences() }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.settingsStore.edit { prefs ->
            val next = transform(prefs.toUserPreferences())
            prefs[Keys.onboardingComplete] = next.onboardingComplete
            prefs[Keys.seedVersion] = next.seedVersion
            prefs[Keys.wake] = next.wakeMinute
            prefs[Keys.sleep] = next.sleepMinute
            prefs[Keys.commute] = next.commuteMinutes
            prefs[Keys.decompression] = next.decompressionMinutes
            prefs[Keys.minGap] = next.minGapMinutes
            prefs[Keys.minSlot] = next.minUsefulSlotMinutes
            prefs[Keys.workBeforeBreak] = next.continuousWorkBeforeBreak
            prefs[Keys.shortBreak] = next.shortBreakMinutes
            prefs[Keys.leisureFloor] = next.minLeisureMinutes
            prefs[Keys.leisureStart] = next.leisureWindowStart
            prefs[Keys.leisureEnd] = next.leisureWindowEnd
            prefs[Keys.buildWeekday] = next.buildMinutesWeekday
            prefs[Keys.buildWeekend] = next.buildMinutesWeekend
            prefs[Keys.buildStart] = next.buildWindowStart
            prefs[Keys.buildEnd] = next.buildWindowEnd
            prefs[Keys.buildEnabled] = next.buildEnabled
            prefs[Keys.learningEnabled] = next.learningEnabled
            prefs[Keys.learningWeekday] = next.learningMinutesWeekday
            prefs[Keys.learningWeekend] = next.learningMinutesWeekend
            prefs[Keys.learningStart] = next.learningWindowStart
            prefs[Keys.learningEnd] = next.learningWindowEnd
            prefs[Keys.minSession] = next.defaultMinSession
            prefs[Keys.maxSession] = next.defaultMaxSession
            prefs[Keys.peakStart] = next.peakWindowStart
            prefs[Keys.peakEnd] = next.peakWindowEnd
            prefs[Keys.fatigueAfter] = next.eveningFatigueAfter
            prefs[Keys.studyStart] = next.preferredStudyStart
            prefs[Keys.studySession] = next.studySessionMinutes
            prefs[Keys.coverageGap] = next.coverageGapDays
            prefs[Keys.reviewEnabled] = next.reviewEnabled
            prefs[Keys.reviewPerHour] = next.reviewMinutesPerTeachingHour
            prefs[Keys.reviewMax] = next.maxReviewMinutesPerDay
            prefs[Keys.workCeiling] = next.maxWorkMinutesPerDay
            prefs[Keys.carryCap] = next.carryForwardCap
            prefs[Keys.energyMode] = next.energyMode.key
            prefs[Keys.energyModeDate] = next.energyModeDate
            prefs[Keys.use24Hour] = next.use24HourTime
            prefs[Keys.themeMode] = next.themeMode
            prefs[Keys.notificationsEnabled] = next.notificationsEnabled
            prefs[Keys.notifyLead] = next.notifyLeadMinutes
            prefs[Keys.notifyBreakEnd] = next.notifyBreakEnd
            prefs[Keys.notifyPlanChanges] = next.notifyPlanChanges
            prefs[Keys.nudgeStudyStart] = next.nudgeStudyStart
            prefs[Keys.nudgeTransitions] = next.nudgeTransitions
            prefs[Keys.nudgeBreaks] = next.nudgeBreaks
            prefs[Keys.nudgeMissed] = next.nudgeMissed
            prefs[Keys.nudgeBuildLearning] = next.nudgeBuildLearning
            prefs[Keys.missedGrace] = next.missedGraceMinutes
            prefs[Keys.notificationSound] = next.notificationSound
            prefs[Keys.morningPlan] = next.morningPlanMinute
            prefs[Keys.checkIn] = next.checkInMinute
            prefs[Keys.checkInEnabled] = next.checkInEnabled
            prefs[Keys.lastRollover] = next.lastRolloverDay
        }
    }

    suspend fun clear() {
        context.settingsStore.edit { it.clear() }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val d = UserPreferences()
        return UserPreferences(
            onboardingComplete = this[Keys.onboardingComplete] ?: d.onboardingComplete,
            seedVersion = this[Keys.seedVersion] ?: d.seedVersion,
            wakeMinute = this[Keys.wake] ?: d.wakeMinute,
            sleepMinute = this[Keys.sleep] ?: d.sleepMinute,
            commuteMinutes = this[Keys.commute] ?: d.commuteMinutes,
            decompressionMinutes = this[Keys.decompression] ?: d.decompressionMinutes,
            minGapMinutes = this[Keys.minGap] ?: d.minGapMinutes,
            minUsefulSlotMinutes = this[Keys.minSlot] ?: d.minUsefulSlotMinutes,
            continuousWorkBeforeBreak = this[Keys.workBeforeBreak] ?: d.continuousWorkBeforeBreak,
            shortBreakMinutes = this[Keys.shortBreak] ?: d.shortBreakMinutes,
            minLeisureMinutes = this[Keys.leisureFloor] ?: d.minLeisureMinutes,
            leisureWindowStart = this[Keys.leisureStart] ?: d.leisureWindowStart,
            leisureWindowEnd = this[Keys.leisureEnd] ?: d.leisureWindowEnd,
            buildMinutesWeekday = this[Keys.buildWeekday] ?: d.buildMinutesWeekday,
            buildMinutesWeekend = this[Keys.buildWeekend] ?: d.buildMinutesWeekend,
            buildWindowStart = this[Keys.buildStart] ?: d.buildWindowStart,
            buildWindowEnd = this[Keys.buildEnd] ?: d.buildWindowEnd,
            buildEnabled = this[Keys.buildEnabled] ?: d.buildEnabled,
            learningEnabled = this[Keys.learningEnabled] ?: d.learningEnabled,
            learningMinutesWeekday = this[Keys.learningWeekday] ?: d.learningMinutesWeekday,
            learningMinutesWeekend = this[Keys.learningWeekend] ?: d.learningMinutesWeekend,
            learningWindowStart = this[Keys.learningStart] ?: d.learningWindowStart,
            learningWindowEnd = this[Keys.learningEnd] ?: d.learningWindowEnd,
            defaultMinSession = this[Keys.minSession] ?: d.defaultMinSession,
            defaultMaxSession = this[Keys.maxSession] ?: d.defaultMaxSession,
            peakWindowStart = this[Keys.peakStart] ?: d.peakWindowStart,
            peakWindowEnd = this[Keys.peakEnd] ?: d.peakWindowEnd,
            eveningFatigueAfter = this[Keys.fatigueAfter] ?: d.eveningFatigueAfter,
            preferredStudyStart = this[Keys.studyStart] ?: d.preferredStudyStart,
            studySessionMinutes = this[Keys.studySession] ?: d.studySessionMinutes,
            coverageGapDays = this[Keys.coverageGap] ?: d.coverageGapDays,
            reviewEnabled = this[Keys.reviewEnabled] ?: d.reviewEnabled,
            reviewMinutesPerTeachingHour = this[Keys.reviewPerHour] ?: d.reviewMinutesPerTeachingHour,
            maxReviewMinutesPerDay = this[Keys.reviewMax] ?: d.maxReviewMinutesPerDay,
            maxWorkMinutesPerDay = this[Keys.workCeiling] ?: d.maxWorkMinutesPerDay,
            carryForwardCap = this[Keys.carryCap] ?: d.carryForwardCap,
            energyMode = EnergyMode.fromKey(this[Keys.energyMode]),
            energyModeDate = this[Keys.energyModeDate] ?: d.energyModeDate,
            use24HourTime = this[Keys.use24Hour] ?: d.use24HourTime,
            themeMode = this[Keys.themeMode] ?: d.themeMode,
            notificationsEnabled = this[Keys.notificationsEnabled] ?: d.notificationsEnabled,
            notifyLeadMinutes = this[Keys.notifyLead] ?: d.notifyLeadMinutes,
            notifyBreakEnd = this[Keys.notifyBreakEnd] ?: d.notifyBreakEnd,
            notifyPlanChanges = this[Keys.notifyPlanChanges] ?: d.notifyPlanChanges,
            nudgeStudyStart = this[Keys.nudgeStudyStart] ?: d.nudgeStudyStart,
            nudgeTransitions = this[Keys.nudgeTransitions] ?: d.nudgeTransitions,
            nudgeBreaks = this[Keys.nudgeBreaks] ?: d.nudgeBreaks,
            nudgeMissed = this[Keys.nudgeMissed] ?: d.nudgeMissed,
            nudgeBuildLearning = this[Keys.nudgeBuildLearning] ?: d.nudgeBuildLearning,
            missedGraceMinutes = this[Keys.missedGrace] ?: d.missedGraceMinutes,
            notificationSound = this[Keys.notificationSound] ?: d.notificationSound,
            morningPlanMinute = this[Keys.morningPlan] ?: d.morningPlanMinute,
            checkInMinute = this[Keys.checkIn] ?: d.checkInMinute,
            checkInEnabled = this[Keys.checkInEnabled] ?: d.checkInEnabled,
            lastRolloverDay = this[Keys.lastRollover] ?: d.lastRolloverDay,
        )
    }
}
