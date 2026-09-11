package com.margin.app.data.repository

import com.margin.app.data.db.dao.CheckInDao
import com.margin.app.data.db.dao.DailyPlanDao
import com.margin.app.data.db.dao.HistoryDao
import com.margin.app.data.db.dao.ScheduleDao
import com.margin.app.data.db.decodePlannedWork
import com.margin.app.data.db.encodePlannedWork
import com.margin.app.data.db.entity.BreakRecordEntity
import com.margin.app.data.db.entity.CompletionRecordEntity
import com.margin.app.data.db.entity.DailyPlanEntity
import com.margin.app.data.db.entity.RescheduleRecordEntity
import com.margin.app.data.db.entity.ScheduleBlockEntity
import com.margin.app.data.db.entity.SkipRecordEntity
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.BreakRecord
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DailyCheckIn
import com.margin.app.domain.model.DailyPlan
import com.margin.app.domain.model.RescheduleRecord
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.planner.PlannedWork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What the last plan of a day said about itself. */
data class PlanMeta(
    val date: LocalDate,
    val version: Int,
    val headline: String?,
    /** The day mode key: normal, light or minimum. */
    val mode: String?,
    val notes: List<String>,
)

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val planDao: DailyPlanDao,
    private val checkInDao: CheckInDao,
    private val historyDao: HistoryDao,
) {

    fun observeDay(date: LocalDate): Flow<List<ScheduleBlock>> =
        scheduleDao.observeForDate(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ScheduleBlock>> =
        scheduleDao.observeRange(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    suspend fun blocksFor(date: LocalDate): List<ScheduleBlock> =
        scheduleDao.forDate(date.toEpochDay()).map { it.toDomain() }

    suspend fun range(from: LocalDate, to: LocalDate): List<ScheduleBlock> =
        scheduleDao.range(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    suspend fun block(id: Long): ScheduleBlock? = scheduleDao.byId(id)?.toDomain()

    fun observeActiveBlock(): Flow<ScheduleBlock?> =
        scheduleDao.observeActiveBlock().map { it?.toDomain() }

    suspend fun activeBlock(): ScheduleBlock? = scheduleDao.activeBlock()?.toDomain()

    suspend fun insert(block: ScheduleBlock): Long = scheduleDao.insert(block.toEntity())

    suspend fun update(block: ScheduleBlock) = scheduleDao.update(block.toEntity())

    suspend fun delete(id: Long) = scheduleDao.delete(id)

    /**
     * Replaces the still-planned, unlocked blocks that end after [fromMinute]. Done, skipped,
     * running and past blocks survive, so the record of the day is never rewritten.
     */
    suspend fun replacePlannedFrom(date: LocalDate, fromMinute: Int, blocks: List<ScheduleBlock>) {
        val entities: List<ScheduleBlockEntity> = blocks.map { it.copy(id = 0).toEntity() }
        scheduleDao.replacePlannedFrom(date.toEpochDay(), fromMinute, entities)
    }

    suspend fun clearDay(date: LocalDate) = scheduleDao.clearDate(date.toEpochDay())

    suspend fun nextPlannedAfter(date: LocalDate, minute: Int): ScheduleBlock? =
        scheduleDao.nextBlockAfter(date.toEpochDay(), minute)?.toDomain()

    // ---- Daily plan metadata --------------------------------------------------------------

    suspend fun plan(date: LocalDate): DailyPlan? = planDao.forDate(date.toEpochDay())?.toDomain()

    fun observePlan(date: LocalDate): Flow<DailyPlan?> =
        planDao.observeForDate(date.toEpochDay()).map { it?.toDomain() }

    fun observePlanMeta(date: LocalDate): Flow<PlanMeta?> =
        planDao.observeForDate(date.toEpochDay()).map { entity ->
            entity?.let {
                PlanMeta(
                    date = date,
                    version = it.version,
                    headline = it.headline,
                    mode = it.mode,
                    notes = it.notes?.lines()?.filter { line -> line.isNotBlank() }.orEmpty(),
                )
            }
        }

    suspend fun savePlan(
        date: LocalDate,
        version: Int,
        headline: String?,
        energyMode: String,
        work: List<PlannedWork> = emptyList(),
        mode: String? = null,
        notes: List<String> = emptyList(),
    ) {
        planDao.upsert(
            DailyPlanEntity(
                date = date.toEpochDay(),
                version = version,
                generatedAt = System.currentTimeMillis(),
                headline = headline,
                energyMode = energyMode,
                workJson = encodePlannedWork(work),
                mode = mode,
                notes = notes.joinToString("\n").ifBlank { null },
            ),
        )
    }

    /** The work the last plan of [date] intended, before anything was done. */
    suspend fun plannedWork(date: LocalDate): List<PlannedWork> =
        decodePlannedWork(planDao.forDate(date.toEpochDay())?.workJson)

    // ---- Check-in -------------------------------------------------------------------------

    suspend fun checkIn(date: LocalDate): DailyCheckIn? =
        checkInDao.forDate(date.toEpochDay())?.toDomain()

    fun observeCheckIn(date: LocalDate): Flow<DailyCheckIn?> =
        checkInDao.observeForDate(date.toEpochDay()).map { it?.toDomain() }

    suspend fun saveCheckIn(checkIn: DailyCheckIn) = checkInDao.upsert(checkIn.toEntity())

    suspend fun allCheckIns(): List<DailyCheckIn> = checkInDao.all().map { it.toDomain() }

    // ---- History ---------------------------------------------------------------------------

    /**
     * Records a finished session with everything the planner learns from: what it was, how
     * long it was planned for, how long it took, and when in the day it started.
     */
    suspend fun recordCompletion(block: ScheduleBlock, minutes: Int, startMinute: Int? = null) {
        historyDao.insertCompletion(
            CompletionRecordEntity(
                date = block.date.toEpochDay(),
                blockId = block.id,
                taskId = block.taskId,
                category = block.category.key,
                type = block.type.key,
                minutes = minutes,
                at = System.currentTimeMillis(),
                subjectCode = block.subjectCode,
                academicType = block.academicType?.key,
                plannedMinutes = if (block.plannedMinutes > 0) block.plannedMinutes else block.duration,
                startMinute = startMinute ?: block.start,
                projectId = block.projectId,
                learningGoalId = block.learningGoalId,
                extendedMinutes = block.extendedMinutes,
                title = block.title,
            ),
        )
    }

    suspend fun recordSkip(
        block: ScheduleBlock,
        reason: String?,
        resolution: SkipResolution,
        kind: SkipKind = SkipKind.SKIPPED,
    ): Long =
        historyDao.insertSkip(
            SkipRecordEntity(
                date = block.date.toEpochDay(),
                blockId = block.id,
                taskId = block.taskId,
                title = block.title,
                reason = reason,
                resolution = resolution.name,
                at = System.currentTimeMillis(),
                kind = kind.key,
                subjectCode = block.subjectCode,
                academicType = block.academicType?.key,
                plannedStart = block.plannedStart ?: block.start,
                plannedMinutes = if (block.plannedMinutes > 0) block.plannedMinutes else block.duration,
                category = block.category.key,
                type = block.type.key,
            ),
        )

    suspend fun recordReschedule(
        block: ScheduleBlock,
        toDate: LocalDate,
        toStart: Int,
        reason: String?,
    ) {
        historyDao.insertReschedule(
            RescheduleRecordEntity(
                date = block.date.toEpochDay(),
                blockId = block.id,
                taskId = block.taskId,
                title = block.title,
                fromStart = block.start,
                toDate = toDate.toEpochDay(),
                toStart = toStart,
                reason = reason,
                at = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordBreak(
        date: LocalDate,
        startMinute: Int,
        plannedMinutes: Int,
        reason: BreakReason,
        actualMinutes: Int = plannedMinutes,
    ) {
        historyDao.insertBreak(
            BreakRecordEntity(
                date = date.toEpochDay(),
                startMinute = startMinute,
                plannedMinutes = plannedMinutes,
                actualMinutes = actualMinutes,
                reason = reason.key,
                at = System.currentTimeMillis(),
            ),
        )
    }

    fun observeCompletions(from: LocalDate, to: LocalDate): Flow<List<CompletionRecord>> =
        historyDao.observeCompletions(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeSkips(from: LocalDate, to: LocalDate): Flow<List<SkipRecord>> =
        historyDao.observeSkips(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeReschedules(from: LocalDate, to: LocalDate): Flow<List<RescheduleRecord>> =
        historyDao.observeReschedules(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun observeBreaks(from: LocalDate, to: LocalDate): Flow<List<BreakRecord>> =
        historyDao.observeBreaks(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    suspend fun completions(from: LocalDate, to: LocalDate): List<CompletionRecord> =
        historyDao.completions(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    suspend fun skips(from: LocalDate, to: LocalDate): List<SkipRecord> =
        historyDao.skips(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    suspend fun reschedules(from: LocalDate, to: LocalDate): List<RescheduleRecord> =
        historyDao.reschedules(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    suspend fun breaks(from: LocalDate, to: LocalDate): List<BreakRecord> =
        historyDao.breaks(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    /** Blocks that were finished, skipped or are running. The planner must not touch these. */
    suspend fun settledBlocks(date: LocalDate): List<ScheduleBlock> =
        blocksFor(date).filter { it.status != BlockStatus.PLANNED || it.locked }

    /** Deletes every record of what happened. Tasks, the timetable and settings are kept. */
    suspend fun clearHistory() {
        historyDao.clearCompletions()
        historyDao.clearSkips()
        historyDao.clearReschedules()
        historyDao.clearBreaks()
    }
}
