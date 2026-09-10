package com.margin.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.margin.app.data.db.entity.AiInteractionEntity
import com.margin.app.data.db.entity.CalendarEventEntity
import com.margin.app.data.db.entity.CompletionRecordEntity
import com.margin.app.data.db.entity.DailyCheckInEntity
import com.margin.app.data.db.entity.DailyPlanEntity
import com.margin.app.data.db.entity.ProjectEntity
import com.margin.app.data.db.entity.RescheduleRecordEntity
import com.margin.app.data.db.entity.ResourceLinkEntity
import com.margin.app.data.db.entity.RoutineEntity
import com.margin.app.data.db.entity.ScheduleBlockEntity
import com.margin.app.data.db.entity.SkipRecordEntity
import com.margin.app.data.db.entity.SubjectEntity
import com.margin.app.data.db.entity.TaskEntity
import com.margin.app.data.db.entity.TaskSessionEntity
import com.margin.app.data.db.entity.TimetableEntryEntity
import com.margin.app.data.db.entity.TimetableExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY code")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE active = 1 ORDER BY code")
    suspend fun activeSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE code = :code")
    suspend fun byCode(code: String): SubjectEntity?

    @Upsert
    suspend fun upsert(subject: SubjectEntity)

    @Upsert
    suspend fun upsertAll(subjects: List<SubjectEntity>)

    @Query("DELETE FROM subjects WHERE code = :code")
    suspend fun delete(code: String)

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries ORDER BY dayOfWeek, start")
    fun observeAll(): Flow<List<TimetableEntryEntity>>

    @Query("SELECT * FROM timetable_entries WHERE active = 1 ORDER BY dayOfWeek, start")
    suspend fun allActive(): List<TimetableEntryEntity>

    @Query("SELECT * FROM timetable_entries WHERE dayOfWeek = :day AND active = 1 ORDER BY start")
    suspend fun forDay(day: Int): List<TimetableEntryEntity>

    @Query("SELECT * FROM timetable_entries WHERE id = :id")
    suspend fun byId(id: Long): TimetableEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimetableEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TimetableEntryEntity>)

    @Update
    suspend fun update(entry: TimetableEntryEntity)

    @Delete
    suspend fun delete(entry: TimetableEntryEntity)

    @Query("DELETE FROM timetable_entries")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM timetable_entries")
    suspend fun count(): Int

    // Exceptions

    @Query("SELECT * FROM timetable_exceptions WHERE date = :date")
    suspend fun exceptionsOn(date: Long): List<TimetableExceptionEntity>

    @Query("SELECT * FROM timetable_exceptions WHERE date >= :from ORDER BY date")
    fun observeExceptionsFrom(from: Long): Flow<List<TimetableExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: TimetableExceptionEntity): Long

    @Query("DELETE FROM timetable_exceptions WHERE id = :id")
    suspend fun deleteException(id: Long)

    @Query("DELETE FROM timetable_exceptions WHERE date = :date AND entryId = :entryId")
    suspend fun clearException(date: Long, entryId: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY start")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE active = 1 ORDER BY start")
    suspend fun allActive(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineEntity>)

    @Update
    suspend fun update(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY active DESC, name")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE active = 1 ORDER BY name")
    suspend fun allActive(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: Long): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status != 'ARCHIVED' ORDER BY deadlineDate IS NULL, deadlineDate, priority DESC, id")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE'")
    suspend fun activeTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND status != 'ARCHIVED'")
    fun observeForProject(projectId: Long): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completedMinutes = completedMinutes + :minutes WHERE id = :id")
    suspend fun addProgress(id: Long, minutes: Int)

    @Query("UPDATE tasks SET status = :status, completedAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, at: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int
}

@Dao
interface TaskSessionDao {
    @Query("SELECT * FROM task_sessions WHERE taskId = :taskId ORDER BY date")
    fun observeForTask(taskId: Long): Flow<List<TaskSessionEntity>>

    @Query("SELECT * FROM task_sessions WHERE date = :date")
    suspend fun forDate(date: Long): List<TaskSessionEntity>

    @Insert
    suspend fun insert(session: TaskSessionEntity): Long

    @Update
    suspend fun update(session: TaskSessionEntity)

    @Query("DELETE FROM task_sessions WHERE taskId = :taskId AND date >= :from AND completedMinutes = 0")
    suspend fun clearPlanned(taskId: Long, from: Long)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE date = :date ORDER BY start")
    suspend fun forDate(date: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM events WHERE date BETWEEN :from AND :to ORDER BY date, start")
    fun observeRange(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): CalendarEventEntity?

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_blocks WHERE date = :date ORDER BY start")
    fun observeForDate(date: Long): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE date = :date ORDER BY start")
    suspend fun forDate(date: Long): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE date BETWEEN :from AND :to ORDER BY date, start")
    fun observeRange(from: Long, to: Long): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE date BETWEEN :from AND :to ORDER BY date, start")
    suspend fun range(from: Long, to: Long): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE id = :id")
    suspend fun byId(id: Long): ScheduleBlockEntity?

    @Query("SELECT * FROM schedule_blocks WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveBlock(): Flow<ScheduleBlockEntity?>

    @Query("SELECT * FROM schedule_blocks WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun activeBlock(): ScheduleBlockEntity?

    @Insert
    suspend fun insert(block: ScheduleBlockEntity): Long

    @Insert
    suspend fun insertAll(blocks: List<ScheduleBlockEntity>)

    @Update
    suspend fun update(block: ScheduleBlockEntity)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Replans wipe only the blocks that are still open; history is never destroyed. */
    @Query("DELETE FROM schedule_blocks WHERE date = :date AND status IN ('PLANNED') AND locked = 0")
    suspend fun clearPlanned(date: Long)

    @Query("DELETE FROM schedule_blocks WHERE date = :date")
    suspend fun clearDate(date: Long)

    @Transaction
    suspend fun replacePlanned(date: Long, blocks: List<ScheduleBlockEntity>) {
        clearPlanned(date)
        insertAll(blocks)
    }

    @Query("SELECT * FROM schedule_blocks WHERE date = :date AND start >= :minute AND status = 'PLANNED' ORDER BY start LIMIT 1")
    suspend fun nextBlockAfter(date: Long, minute: Int): ScheduleBlockEntity?
}

@Dao
interface DailyPlanDao {
    @Query("SELECT * FROM daily_plans WHERE date = :date")
    suspend fun forDate(date: Long): DailyPlanEntity?

    @Query("SELECT * FROM daily_plans WHERE date = :date")
    fun observeForDate(date: Long): Flow<DailyPlanEntity?>

    @Upsert
    suspend fun upsert(plan: DailyPlanEntity)

    @Query("DELETE FROM daily_plans WHERE date < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE date = :date")
    suspend fun forDate(date: Long): DailyCheckInEntity?

    @Query("SELECT * FROM check_ins WHERE date = :date")
    fun observeForDate(date: Long): Flow<DailyCheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE date >= :from ORDER BY date DESC")
    fun observeFrom(from: Long): Flow<List<DailyCheckInEntity>>

    @Upsert
    suspend fun upsert(checkIn: DailyCheckInEntity)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertCompletion(record: CompletionRecordEntity): Long

    @Insert
    suspend fun insertSkip(record: SkipRecordEntity): Long

    @Insert
    suspend fun insertReschedule(record: RescheduleRecordEntity): Long

    @Query("SELECT * FROM completion_records WHERE date BETWEEN :from AND :to ORDER BY at")
    fun observeCompletions(from: Long, to: Long): Flow<List<CompletionRecordEntity>>

    @Query("SELECT * FROM completion_records WHERE date BETWEEN :from AND :to")
    suspend fun completions(from: Long, to: Long): List<CompletionRecordEntity>

    @Query("SELECT * FROM skip_records WHERE date BETWEEN :from AND :to ORDER BY at")
    fun observeSkips(from: Long, to: Long): Flow<List<SkipRecordEntity>>

    @Query("SELECT * FROM skip_records WHERE date BETWEEN :from AND :to")
    suspend fun skips(from: Long, to: Long): List<SkipRecordEntity>

    @Query("SELECT * FROM reschedule_records WHERE date BETWEEN :from AND :to ORDER BY at")
    fun observeReschedules(from: Long, to: Long): Flow<List<RescheduleRecordEntity>>

    @Query("SELECT * FROM reschedule_records WHERE date BETWEEN :from AND :to")
    suspend fun reschedules(from: Long, to: Long): List<RescheduleRecordEntity>

    @Query("UPDATE skip_records SET resolution = :resolution WHERE id = :id")
    suspend fun resolveSkip(id: Long, resolution: String)
}

@Dao
interface ResourceLinkDao {
    @Query("SELECT * FROM resource_links WHERE taskId = :taskId")
    fun observeForTask(taskId: Long): Flow<List<ResourceLinkEntity>>

    @Insert
    suspend fun insert(link: ResourceLinkEntity): Long

    @Query("DELETE FROM resource_links WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiInteractionDao {
    @Query("SELECT * FROM ai_interactions ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiInteractionEntity>>

    @Insert
    suspend fun insert(interaction: AiInteractionEntity): Long

    @Query("DELETE FROM ai_interactions")
    suspend fun clear()
}
