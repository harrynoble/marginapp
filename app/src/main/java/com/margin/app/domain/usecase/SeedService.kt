package com.margin.app.domain.usecase

import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.prefs.TimetableReferenceRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.data.seed.TimetableSeed
import com.margin.app.domain.model.RoutineKind
import com.margin.app.domain.model.TimetableValidator

/**
 * Puts the known timetable in place on first run so the app opens with a real week already
 * loaded instead of an onboarding form. Runs once; the version guard means a later seed
 * change can be rolled out without wiping edits the user has made.
 *
 * Version 2: meals are no longer fixed blocks. Existing meal routines are switched off rather
 * than deleted, so anyone who wants them back can turn them on in the timetable screen.
 *
 * It also records the confirmed timetable the stored week is checked against, so a class that
 * goes missing or changes without the user doing it is caught rather than silently planned around.
 */
class SeedService(
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: PreferencesRepository,
    private val referenceRepository: TimetableReferenceRepository,
) {

    suspend fun seedIfNeeded(): Boolean {
        val seeded = seedOnce()
        ensureReference()
        return seeded
    }

    private suspend fun seedOnce(): Boolean {
        val prefs = preferencesRepository.current()
        if (prefs.seedVersion >= CURRENT_SEED_VERSION) return false

        if (prefs.seedVersion >= 1) {
            // Upgrading an existing install: keep the user's week, make meals optional.
            timetableRepository.allRoutines()
                .filter { it.kind == RoutineKind.MEAL && it.active }
                .forEach { timetableRepository.upsertRoutine(it.copy(active = false)) }
            preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
            return false
        }

        if (!timetableRepository.isEmpty()) {
            // The user already built their own week. Record the version and leave it alone.
            preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
            return false
        }

        TimetableSeed.subjects.forEach { timetableRepository.upsertSubject(it) }
        timetableRepository.replaceAll(TimetableSeed.entries)
        TimetableSeed.routines.forEach { timetableRepository.upsertRoutine(it) }
        referenceRepository.set(TimetableSeed.entries, TimetableReferenceRepository.SOURCE_SEED)

        preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
        return true
    }

    /**
     * Installs from before the check existed have no confirmed timetable yet. If the stored week
     * is essentially the shipped one, the shipped one (checked against the paper timetable) is
     * the reference, so any drift shows up for review. A week the user built themselves is
     * taken as confirmed as it is.
     */
    suspend fun ensureReference() {
        if (referenceRepository.get() != null) return
        val stored = timetableRepository.weeklyEntries()
        val check = TimetableValidator.validate(TimetableSeed.entries, stored)
        val mostlySeed = stored.isNotEmpty() && check.issues.size <= TimetableSeed.entries.size / 4
        if (mostlySeed || stored.isEmpty()) {
            referenceRepository.set(TimetableSeed.entries, TimetableReferenceRepository.SOURCE_SEED)
        } else {
            referenceRepository.set(stored, TimetableReferenceRepository.SOURCE_EDIT)
        }
    }

    /** Restores the shipped timetable, discarding the current one. Used from Settings. */
    suspend fun resetToSeed() {
        TimetableSeed.subjects.forEach { timetableRepository.upsertSubject(it) }
        timetableRepository.replaceAll(TimetableSeed.entries)
        referenceRepository.set(TimetableSeed.entries, TimetableReferenceRepository.SOURCE_SEED)
        preferencesRepository.update { it.copy(seedVersion = CURRENT_SEED_VERSION) }
    }

    private companion object {
        const val CURRENT_SEED_VERSION = 2
    }
}
