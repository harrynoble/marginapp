package com.margin.app.domain.usecase

import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.data.seed.TimetableSeed

/**
 * Puts the known timetable in place on first run so the app opens with a real week already
 * loaded instead of an onboarding form. Runs once; the version guard means a later seed
 * change can be rolled out without wiping edits the user has made.
 */
class SeedService(
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: PreferencesRepository,
) {

    suspend fun seedIfNeeded(): Boolean {
        val prefs = preferencesRepository.current()
        if (prefs.seedVersion >= CURRENT_SEED_VERSION) return false
        if (!timetableRepository.isEmpty()) {
            // The user already built their own week. Record the version and leave it alone.
            preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
            return false
        }

        TimetableSeed.subjects.forEach { timetableRepository.upsertSubject(it) }
        timetableRepository.replaceAll(TimetableSeed.entries)
        TimetableSeed.routines.forEach { timetableRepository.upsertRoutine(it) }

        preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
        return true
    }

    /** Restores the shipped timetable, discarding the current one. Used from Settings. */
    suspend fun resetToSeed() {
        TimetableSeed.subjects.forEach { timetableRepository.upsertSubject(it) }
        timetableRepository.replaceAll(TimetableSeed.entries)
        preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
    }

    private companion object {
        const val CURRENT_SEED_VERSION = 1
    }
}
