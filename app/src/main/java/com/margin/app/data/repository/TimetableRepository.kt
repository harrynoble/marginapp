package com.margin.app.data.repository

import com.margin.app.data.db.dao.RoutineDao
import com.margin.app.data.db.dao.SubjectDao
import com.margin.app.data.db.dao.TimetableDao
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.domain.model.ExceptionType
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableException
import com.margin.app.domain.model.TimetableKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The recurring week: classes, routines and the exceptions that bend them for one date.
 */
class TimetableRepository(
    private val timetableDao: TimetableDao,
    private val subjectDao: SubjectDao,
    private val routineDao: RoutineDao,
) {

    fun observeEntries(): Flow<List<TimetableEntry>> =
        timetableDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSubjects(): Flow<List<Subject>> =
        subjectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun subjects(): List<Subject> = subjectDao.activeSubjects().map { it.toDomain() }

    suspend fun subject(code: String?): Subject? =
        code?.let { subjectDao.byCode(it)?.toDomain() }

    suspend fun routines(): List<Routine> = routineDao.allActive().map { it.toDomain() }

    /**
     * The classes that actually happen on [date]: the weekly pattern, minus anything
     * cancelled or covered by a holiday, plus any one-off extras added for that date.
     */
    suspend fun entriesFor(date: LocalDate): List<TimetableEntry> {
        val exceptions = timetableDao.exceptionsOn(date.toEpochDay()).map { it.toDomain() }
        if (exceptions.any { it.type == ExceptionType.HOLIDAY && it.entryId == null }) {
            return extrasFrom(exceptions)
        }
        val cancelled = exceptions
            .filter { it.type == ExceptionType.CANCELLED }
            .mapNotNull { it.entryId }
            .toSet()

        val regular = timetableDao.forDay(date.dayOfWeek.value)
            .map { it.toDomain() }
            .filterNot { it.id in cancelled }

        return (regular + extrasFrom(exceptions)).sortedBy { it.start }
    }

    private fun extrasFrom(exceptions: List<TimetableException>): List<TimetableEntry> =
        exceptions.filter { it.type == ExceptionType.EXTRA && it.end > it.start }
            .map { extra ->
                TimetableEntry(
                    id = -extra.id,
                    dayOfWeek = extra.date.dayOfWeek,
                    start = extra.start,
                    end = extra.end,
                    subjectCode = extra.subjectCode,
                    title = extra.title ?: "Extra class",
                    kind = TimetableKind.LECTURE,
                )
            }

    suspend fun exceptionsOn(date: LocalDate): List<TimetableException> =
        timetableDao.exceptionsOn(date.toEpochDay()).map { it.toDomain() }

    fun observeExceptionsFrom(date: LocalDate): Flow<List<TimetableException>> =
        timetableDao.observeExceptionsFrom(date.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    suspend fun addException(exception: TimetableException): Long =
        timetableDao.insertException(exception.toEntity())

    suspend fun removeException(id: Long) = timetableDao.deleteException(id)

    suspend fun cancelClass(date: LocalDate, entryId: Long, note: String? = null) {
        timetableDao.insertException(
            TimetableException(
                date = date,
                type = ExceptionType.CANCELLED,
                entryId = entryId,
                note = note,
            ).toEntity(),
        )
    }

    suspend fun restoreClass(date: LocalDate, entryId: Long) =
        timetableDao.clearException(date.toEpochDay(), entryId)

    suspend fun markHoliday(date: LocalDate, note: String?) {
        timetableDao.insertException(
            TimetableException(date = date, type = ExceptionType.HOLIDAY, note = note).toEntity(),
        )
    }

    suspend fun upsertEntry(entry: TimetableEntry): Long =
        if (entry.id == 0L) timetableDao.insert(entry.toEntity())
        else {
            timetableDao.update(entry.toEntity())
            entry.id
        }

    suspend fun deleteEntry(entry: TimetableEntry) = timetableDao.delete(entry.toEntity())

    suspend fun replaceAll(entries: List<TimetableEntry>) {
        timetableDao.clear()
        timetableDao.insertAll(entries.map { it.toEntity() })
    }

    suspend fun upsertSubject(subject: Subject) = subjectDao.upsert(subject.toEntity())

    suspend fun deleteSubject(code: String) = subjectDao.delete(code)

    suspend fun upsertRoutine(routine: Routine): Long =
        if (routine.id == 0L) routineDao.insert(routine.toEntity())
        else {
            routineDao.update(routine.toEntity())
            routine.id
        }

    suspend fun deleteRoutine(id: Long) = routineDao.delete(id)

    suspend fun isEmpty(): Boolean = timetableDao.count() == 0
}
