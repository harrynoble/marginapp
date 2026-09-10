package com.margin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.margin.app.data.db.dao.AiInteractionDao
import com.margin.app.data.db.dao.CheckInDao
import com.margin.app.data.db.dao.DailyPlanDao
import com.margin.app.data.db.dao.EventDao
import com.margin.app.data.db.dao.HistoryDao
import com.margin.app.data.db.dao.ProjectDao
import com.margin.app.data.db.dao.ResourceLinkDao
import com.margin.app.data.db.dao.RoutineDao
import com.margin.app.data.db.dao.ScheduleDao
import com.margin.app.data.db.dao.SubjectDao
import com.margin.app.data.db.dao.TaskDao
import com.margin.app.data.db.dao.TaskSessionDao
import com.margin.app.data.db.dao.TimetableDao
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

@Database(
    entities = [
        SubjectEntity::class,
        TimetableEntryEntity::class,
        TimetableExceptionEntity::class,
        RoutineEntity::class,
        ProjectEntity::class,
        TaskEntity::class,
        TaskSessionEntity::class,
        CalendarEventEntity::class,
        ScheduleBlockEntity::class,
        DailyPlanEntity::class,
        DailyCheckInEntity::class,
        CompletionRecordEntity::class,
        SkipRecordEntity::class,
        RescheduleRecordEntity::class,
        ResourceLinkEntity::class,
        AiInteractionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MarginDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun timetableDao(): TimetableDao
    abstract fun routineDao(): RoutineDao
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun taskSessionDao(): TaskSessionDao
    abstract fun eventDao(): EventDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun dailyPlanDao(): DailyPlanDao
    abstract fun checkInDao(): CheckInDao
    abstract fun historyDao(): HistoryDao
    abstract fun resourceLinkDao(): ResourceLinkDao
    abstract fun aiInteractionDao(): AiInteractionDao

    companion object {
        const val NAME = "margin.db"

        @Volatile
        private var instance: MarginDatabase? = null

        fun get(context: Context): MarginDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): MarginDatabase =
            Room.databaseBuilder(context, MarginDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
