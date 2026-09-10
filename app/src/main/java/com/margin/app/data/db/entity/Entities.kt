package com.margin.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Dates are epoch days and times are minutes from midnight. Enums are stored as their
 * stable string keys rather than ordinals so that reordering an enum cannot corrupt data.
 */

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val code: String,
    val name: String,
    val shortName: String,
    val reviewWeight: Float = 1f,
    val colorIndex: Int = 0,
    val active: Boolean = true,
)

@Entity(
    tableName = "timetable_entries",
    indices = [Index("dayOfWeek"), Index("subjectCode")],
)
data class TimetableEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** java.time.DayOfWeek.value, Monday = 1. */
    val dayOfWeek: Int,
    val start: Int,
    val end: Int,
    val subjectCode: String?,
    val title: String,
    val kind: String,
    val faculty: String? = null,
    val location: String? = null,
    val active: Boolean = true,
)

@Entity(tableName = "timetable_exceptions", indices = [Index("date")])
data class TimetableExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val type: String,
    /** Null means the exception covers the whole day. */
    val entryId: Long? = null,
    val note: String? = null,
    val title: String? = null,
    val subjectCode: String? = null,
    val start: Int = 0,
    val end: Int = 0,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val category: String,
    val daysMask: Int,
    val start: Int,
    val end: Int,
    val active: Boolean = true,
    val protectedTime: Boolean = true,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val colorIndex: Int = 0,
    val targetMinutesPerWeek: Int = 0,
    val active: Boolean = true,
    val notes: String? = null,
)

@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("deadlineDate"), Index("projectId"), Index("subjectCode")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val category: String,
    val subjectCode: String? = null,
    val projectId: Long? = null,
    val estimatedMinutes: Int,
    val completedMinutes: Int = 0,
    val minSessionMinutes: Int = 25,
    val maxSessionMinutes: Int = 60,
    val priority: String,
    val difficulty: String,
    val energy: String,
    val deadlineDate: Long? = null,
    val deadlineMinute: Int? = null,
    val preferredStart: Int? = null,
    val preferredEnd: Int? = null,
    val splittable: Boolean = true,
    val recurrenceMask: Int = 0,
    val status: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val pinnedDate: Long? = null,
)

@Entity(
    tableName = "task_sessions",
    indices = [Index("taskId"), Index("date")],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val date: Long,
    val plannedMinutes: Int,
    val completedMinutes: Int = 0,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
)

@Entity(tableName = "events", indices = [Index("date")])
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val date: Long,
    val start: Int,
    val end: Int,
    val category: String,
    val allDay: Boolean = false,
    val hard: Boolean = true,
    val createdBySuggestion: Boolean = false,
)

@Entity(
    tableName = "schedule_blocks",
    indices = [Index("date"), Index("taskId"), Index("status")],
)
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val start: Int,
    val end: Int,
    val type: String,
    val title: String,
    val subtitle: String? = null,
    val category: String,
    val status: String,
    val taskId: Long? = null,
    val eventId: Long? = null,
    val timetableEntryId: Long? = null,
    val routineId: Long? = null,
    val projectId: Long? = null,
    val subjectCode: String? = null,
    val locked: Boolean = false,
    val actualStart: Long? = null,
    val actualEnd: Long? = null,
    val elapsedMinutes: Int = 0,
    val reason: String? = null,
    val planVersion: Int = 0,
    /** Stable identity across replans, so a moved block keeps its history. */
    val planKey: String = "",
)

@Entity(tableName = "daily_plans")
data class DailyPlanEntity(
    @PrimaryKey val date: Long,
    val version: Int = 1,
    val generatedAt: Long = 0,
    val headline: String? = null,
    val energyNote: String? = null,
    val energyMode: String = "normal",
)

@Entity(tableName = "check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val date: Long,
    val energy: Int? = null,
    val note: String? = null,
    val unexpected: String? = null,
    val carryForward: Boolean = true,
    val completedAt: Long = 0,
)

@Entity(tableName = "completion_records", indices = [Index("date"), Index("taskId")])
data class CompletionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val blockId: Long? = null,
    val taskId: Long? = null,
    val category: String,
    val type: String,
    val minutes: Int,
    val at: Long,
)

@Entity(tableName = "skip_records", indices = [Index("date"), Index("taskId")])
data class SkipRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val blockId: Long? = null,
    val taskId: Long? = null,
    val title: String,
    val reason: String? = null,
    val resolution: String,
    val at: Long,
)

@Entity(tableName = "reschedule_records", indices = [Index("date"), Index("taskId")])
data class RescheduleRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val blockId: Long? = null,
    val taskId: Long? = null,
    val title: String,
    val fromStart: Int,
    val toDate: Long,
    val toStart: Int,
    val reason: String? = null,
    val at: Long,
)

@Entity(tableName = "resource_links", indices = [Index("taskId"), Index("projectId")])
data class ResourceLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val label: String,
    val url: String,
)

/**
 * Metadata only. The user text is kept so the assistant history is readable, but no
 * model output is ever replayed as state: only the validated command is stored.
 */
@Entity(tableName = "ai_interactions", indices = [Index("at")])
data class AiInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val userText: String,
    val commandJson: String? = null,
    val reply: String? = null,
    val applied: Boolean = false,
    val source: String,
    val error: String? = null,
)
