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
        val minSession = intPreferencesKey("default_min_session")
        val maxSession = intPreferencesKey("default_max_session")
        val peakStart = intPreferencesKey("peak_start")
        val peakEnd = intPreferencesKey("peak_end")
        val fatigueAfter = intPreferencesKey("fatigue_after")
        val reviewEnabled = booleanPreferencesKey("review_enabled")
        val reviewPerHour = intPreferencesKey("review_per_hour")
        val reviewMax = intPreferencesKey("review_max")
        val workCeiling = intPreferencesKey("work_ceiling")
        val energyMode = stringPreferencesKey("energy_mode")
        val energyModeDate = longPreferencesKey("energy_mode_date")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val notifyLead = intPreferencesKey("notify_lead")
        val notifyBreakEnd = booleanPreferencesKey("notify_break_end")
        val notifyPlanChanges = booleanPreferencesKey("notify_plan_changes")
        val morningPlan = intPreferencesKey("morning_plan_minute")
        val checkIn = intPreferencesKey("check_in_minute")
        val checkInEnabled = booleanPreferencesKey("check_in_enabled")
    }

    val preferences: Flow<UserPreferences> = context.settingsStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toUserPreferences() }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(current())
        context.settingsStore.edit { prefs ->
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
            prefs[Keys.minSession] = next.defaultMinSession
            prefs[Keys.maxSession] = next.defaultMaxSession
            prefs[Keys.peakStart] = next.peakWindowStart
            prefs[Keys.peakEnd] = next.peakWindowEnd
            prefs[Keys.fatigueAfter] = next.eveningFatigueAfter
            prefs[Keys.reviewEnabled] = next.reviewEnabled
            prefs[Keys.reviewPerHour] = next.reviewMinutesPerTeachingHour
            prefs[Keys.reviewMax] = next.maxReviewMinutesPerDay
            prefs[Keys.workCeiling] = next.maxWorkMinutesPerDay
            prefs[Keys.energyMode] = next.energyMode.key
            prefs[Keys.energyModeDate] = next.energyModeDate
            prefs[Keys.use24Hour] = next.use24HourTime
            prefs[Keys.notificationsEnabled] = next.notificationsEnabled
            prefs[Keys.notifyLead] = next.notifyLeadMinutes
            prefs[Keys.notifyBreakEnd] = next.notifyBreakEnd
            prefs[Keys.notifyPlanChanges] = next.notifyPlanChanges
            prefs[Keys.morningPlan] = next.morningPlanMinute
            prefs[Keys.checkIn] = next.checkInMinute
            prefs[Keys.checkInEnabled] = next.checkInEnabled
        }
    }

    suspend fun clear() {
        context.settingsStore.edit { it.clear() }
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            onboardingComplete = this[Keys.onboardingComplete] ?: defaults.onboardingComplete,
            seedVersion = this[Keys.seedVersion] ?: defaults.seedVersion,
            wakeMinute = this[Keys.wake] ?: defaults.wakeMinute,
            sleepMinute = this[Keys.sleep] ?: defaults.sleepMinute,
            commuteMinutes = this[Keys.commute] ?: defaults.commuteMinutes,
            decompressionMinutes = this[Keys.decompression] ?: defaults.decompressionMinutes,
            minGapMinutes = this[Keys.minGap] ?: defaults.minGapMinutes,
            minUsefulSlotMinutes = this[Keys.minSlot] ?: defaults.minUsefulSlotMinutes,
            continuousWorkBeforeBreak = this[Keys.workBeforeBreak] ?: defaults.continuousWorkBeforeBreak,
            shortBreakMinutes = this[Keys.shortBreak] ?: defaults.shortBreakMinutes,
            minLeisureMinutes = this[Keys.leisureFloor] ?: defaults.minLeisureMinutes,
            leisureWindowStart = this[Keys.leisureStart] ?: defaults.leisureWindowStart,
            leisureWindowEnd = this[Keys.leisureEnd] ?: defaults.leisureWindowEnd,
            buildMinutesWeekday = this[Keys.buildWeekday] ?: defaults.buildMinutesWeekday,
            buildMinutesWeekend = this[Keys.buildWeekend] ?: defaults.buildMinutesWeekend,
            buildWindowStart = this[Keys.buildStart] ?: defaults.buildWindowStart,
            buildWindowEnd = this[Keys.buildEnd] ?: defaults.buildWindowEnd,
            defaultMinSession = this[Keys.minSession] ?: defaults.defaultMinSession,
            defaultMaxSession = this[Keys.maxSession] ?: defaults.defaultMaxSession,
            peakWindowStart = this[Keys.peakStart] ?: defaults.peakWindowStart,
            peakWindowEnd = this[Keys.peakEnd] ?: defaults.peakWindowEnd,
            eveningFatigueAfter = this[Keys.fatigueAfter] ?: defaults.eveningFatigueAfter,
            reviewEnabled = this[Keys.reviewEnabled] ?: defaults.reviewEnabled,
            reviewMinutesPerTeachingHour = this[Keys.reviewPerHour] ?: defaults.reviewMinutesPerTeachingHour,
            maxReviewMinutesPerDay = this[Keys.reviewMax] ?: defaults.maxReviewMinutesPerDay,
            maxWorkMinutesPerDay = this[Keys.workCeiling] ?: defaults.maxWorkMinutesPerDay,
            energyMode = EnergyMode.fromKey(this[Keys.energyMode]),
            energyModeDate = this[Keys.energyModeDate] ?: defaults.energyModeDate,
            use24HourTime = this[Keys.use24Hour] ?: defaults.use24HourTime,
            notificationsEnabled = this[Keys.notificationsEnabled] ?: defaults.notificationsEnabled,
            notifyLeadMinutes = this[Keys.notifyLead] ?: defaults.notifyLeadMinutes,
            notifyBreakEnd = this[Keys.notifyBreakEnd] ?: defaults.notifyBreakEnd,
            notifyPlanChanges = this[Keys.notifyPlanChanges] ?: defaults.notifyPlanChanges,
            morningPlanMinute = this[Keys.morningPlan] ?: defaults.morningPlanMinute,
            checkInMinute = this[Keys.checkIn] ?: defaults.checkInMinute,
            checkInEnabled = this[Keys.checkInEnabled] ?: defaults.checkInEnabled,
        )
    }
}
