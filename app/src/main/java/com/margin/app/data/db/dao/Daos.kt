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
import com.margin.app.data.db.entity.BreakRecordEntity
import com.margin.app.data.db.entity.CalendarEventEntity
import com.margin.app.data.db.entity.CompletionRecordEntity
import com.margin.app.data.db.entity.DailyCheckInEntity
import com.margin.app.data.db.entity.DailyPlanEntity
import com.margin.app.data.db.entity.DayStateEntity
import com.margin.app.data.db.entity.DeferredWorkEntity
import com.margin.app.data.db.entity.ExamEntity
import com.margin.app.data.db.entity.LearningGoalEntity
import com.margin.app.data.db.entity.NudgeLogEntity
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

    @Query("SELECT * FROM subjects ORDER BY code")
    suspend fun all(): List<SubjectEntity>

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

    @Transaction
    suspend fun replaceAll(entries: List<TimetableEntryEntity>) {
        clear()
        insertAll(entries)
    }

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

    @Query("SELECT * FROM routines ORDER BY start")
    suspend fun all(): List<RoutineEntity>

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
interface LearningGoalDao {
    @Query("SELECT * FROM learning_goals ORDER BY active DESC, name")
    fun observeAll(): Flow<List<LearningGoalEntity>>

    @Query("SELECT * FROM learning_goals WHERE active = 1 ORDER BY name")
    suspend fun allActive(): List<LearningGoalEntity>

    @Query("SELECT * FROM learning_goals WHERE id = :id")
    suspend fun byId(id: Long): LearningGoalEntity?

    @Upsert
    suspend fun upsert(goal: LearningGoalEntity): Long

    @Query("DELETE FROM learning_goals WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY date, startMinute")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE date >= :from ORDER BY date, startMinute")
    fun observeFrom(from: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE date >= :from ORDER BY date, startMinute")
    suspend fun from(from: Long): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun byId(id: Long): ExamEntity?

    @Upsert
    suspend fun upsert(exam: ExamEntity): Long

    @Insert
    suspend fun insertAll(exams: List<ExamEntity>)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM exams WHERE date < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status != 'ARCHIVED' ORDER BY deadlineDate IS NULL, deadlineDate, priority DESC, id")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

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

    @Query("SELECT * FROM events ORDER BY date, start")
    suspend fun all(): List<CalendarEventEntity>

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

    /**
     * Replans wipe only the blocks that are still open and still ahead. Anything in the past
     * stays as the record of the day; history is never destroyed.
     */
    @Query("DELETE FROM schedule_blocks WHERE date = :date AND status = 'PLANNED' AND locked = 0 AND `end` > :fromMinute")
    suspend fun clearPlannedFrom(date: Long, fromMinute: Int)

    @Query("DELETE FROM schedule_blocks WHERE date = :date")
    suspend fun clearDate(date: Long)

    @Query("DELETE FROM schedule_blocks WHERE date < :before")
    suspend fun clearBefore(before: Long)

    @Transaction
    suspend fun replacePlannedFrom(date: Long, fromMinute: Int, blocks: List<ScheduleBlockEntity>) {
        clearPlannedFrom(date, fromMinute)
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

    @Query("SELECT * FROM check_ins ORDER BY date")
    suspend fun all(): List<DailyCheckInEntity>

    @Upsert
    suspend fun upsert(checkIn: DailyCheckInEntity)
}

@Dao
interface DayStateDao {
    @Query("SELECT * FROM day_states WHERE date = :date")
    suspend fun forDate(date: Long): DayStateEntity?

    @Query("SELECT * FROM day_states WHERE date = :date")
    fun observeForDate(date: Long): Flow<DayStateEntity?>

    @Upsert
    suspend fun upsert(state: DayStateEntity)

    @Query("DELETE FROM day_states WHERE date < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface DeferredWorkDao {
    @Query("SELECT * FROM deferred_work WHERE toDate = :date ORDER BY id")
    suspend fun forDate(date: Long): List<DeferredWorkEntity>

    @Query("SELECT * FROM deferred_work WHERE toDate = :date ORDER BY id")
    fun observeForDate(date: Long): Flow<List<DeferredWorkEntity>>

    /** The same work is never deferred to the same day twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: DeferredWorkEntity): Long

    @Query("UPDATE deferred_work SET consumed = 1 WHERE toDate < :before")
    suspend fun consumeBefore(before: Long)

    @Query("SELECT * FROM deferred_work WHERE fromDate = :date")
    suspend fun fromDate(date: Long): List<DeferredWorkEntity>
}

@Dao
interface NudgeLogDao {
    @Query("SELECT `key` FROM nudge_log WHERE date = :date")
    suspend fun keysFor(date: Long): List<String>

    @Query("SELECT MAX(at) FROM nudge_log WHERE date = :date")
    suspend fun lastAt(date: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: NudgeLogEntity)

    @Query("DELETE FROM nudge_log WHERE date < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertCompletion(record: CompletionRecordEntity): Long

    @Insert
    suspend fun insertSkip(record: SkipRecordEntity): Long

    @Insert
    suspend fun insertReschedule(record: RescheduleRecordEntity): Long

    @Insert
    suspend fun insertBreak(record: BreakRecordEntity): Long

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

    @Query("SELECT * FROM break_records WHERE date BETWEEN :from AND :to ORDER BY at")
    fun observeBreaks(from: Long, to: Long): Flow<List<BreakRecordEntity>>

    @Query("SELECT * FROM break_records WHERE date BETWEEN :from AND :to")
    suspend fun breaks(from: Long, to: Long): List<BreakRecordEntity>

    @Query("UPDATE skip_records SET resolution = :resolution WHERE id = :id")
    suspend fun resolveSkip(id: Long, resolution: String)

    @Query("DELETE FROM completion_records")
    suspend fun clearCompletions()

    @Query("DELETE FROM skip_records")
    suspend fun clearSkips()

    @Query("DELETE FROM reschedule_records")
    suspend fun clearReschedules()

    @Query("DELETE FROM break_records")
    suspend fun clearBreaks()
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
