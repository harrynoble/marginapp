package com.margin.app.data.repository

import com.margin.app.data.db.dao.DayStateDao
import com.margin.app.data.db.dao.DeferredWorkDao
import com.margin.app.data.db.dao.NudgeLogDao
import com.margin.app.data.db.entity.NudgeLogEntity
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.DeferredWork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The per-day layer: what the user decided about a day, work carried into it, and which
 * notifications it has already had.
 */
class DayRepository(
    private val dayStateDao: DayStateDao,
    private val deferredDao: DeferredWorkDao,
    private val nudgeDao: NudgeLogDao,
) {

    suspend fun state(date: LocalDate): DayState =
        dayStateDao.forDate(date.toEpochDay())?.toDomain() ?: DayState(date)

    fun observeState(date: LocalDate): Flow<DayState> =
        dayStateDao.observeForDate(date.toEpochDay()).map { it?.toDomain() ?: DayState(date) }

    suspend fun update(date: LocalDate, transform: (DayState) -> DayState): DayState {
        val next = transform(state(date)).copy(date = date)
        dayStateDao.upsert(next.toEntity())
        return next
    }

    // ---- carried work -----------------------------------------------------------------------

    suspend fun deferredFor(date: LocalDate): List<DeferredWork> =
        deferredDao.forDate(date.toEpochDay()).map { it.toDomain() }

    fun observeDeferred(date: LocalDate): Flow<List<DeferredWork>> =
        deferredDao.observeForDate(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    /** Returns how many items were newly deferred; work already deferred to that day is skipped. */
    suspend fun defer(items: List<DeferredWork>): Int =
        items.count { deferredDao.insert(it.toEntity()) > 0 }

    suspend fun deferredFrom(date: LocalDate): List<DeferredWork> =
        deferredDao.fromDate(date.toEpochDay()).map { it.toDomain() }

    /** Carried work for days that have passed is spent, whether or not it happened. */
    suspend fun consumeDeferredBefore(date: LocalDate) = deferredDao.consumeBefore(date.toEpochDay())

    // ---- notification ledger ----------------------------------------------------------------

    suspend fun postedKeys(date: LocalDate): Set<String> = nudgeDao.keysFor(date.toEpochDay()).toSet()

    suspend fun markPosted(date: LocalDate, key: String) {
        nudgeDao.insert(NudgeLogEntity(date = date.toEpochDay(), key = key, at = System.currentTimeMillis()))
    }

    suspend fun lastPostedAt(date: LocalDate): Long? = nudgeDao.lastAt(date.toEpochDay())

    suspend fun prune(before: LocalDate) {
        nudgeDao.pruneBefore(before.toEpochDay())
        dayStateDao.pruneBefore(before.toEpochDay())
    }
}
