package com.margin.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

data class Subject(
    val code: String,
    val name: String,
    val shortName: String = code,
    /** Relative amount of revision this subject earns per teaching hour. 1.0 is average. */
    val reviewWeight: Float = 1f,
    val colorIndex: Int = 0,
    val active: Boolean = true,
)

data class TimetableEntry(
    val id: Long = 0,
    val dayOfWeek: DayOfWeek,
    val start: Int,
    val end: Int,
    val subjectCode: String?,
    val title: String,
    val kind: TimetableKind,
    val faculty: String? = null,
    val location: String? = null,
    val active: Boolean = true,
) {
    val range: TimeRange get() = TimeRange(start, end)
}

data class TimetableException(
    val id: Long = 0,
    val date: LocalDate,
    val type: ExceptionType,
    /** Null means the whole day (a holiday). */
    val entryId: Long? = null,
    val note: String? = null,
    /** Only used for EXTRA: an added one-off class. */
    val title: String? = null,
    val subjectCode: String? = null,
    val start: Int = 0,
    val end: Int = 0,
)

data class Routine(
    val id: Long = 0,
    val title: String,
    val kind: RoutineKind,
    val category: Category,
    /** Bit set of DayOfWeek.value (Mon = 1 .. Sun = 7). */
    val daysMask: Int,
    val start: Int,
    val end: Int,
    val active: Boolean = true,
    val protectedTime: Boolean = true,
) {
    fun appliesTo(day: DayOfWeek): Boolean = (daysMask shr (day.value - 1)) and 1 == 1

    val range: TimeRange get() = TimeRange(start, end)

    companion object {
        val EVERY_DAY = 0b1111111
        val WEEKDAYS = 0b0011111
        val WEEKENDS = 0b1100000
        fun maskOf(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    }
}

data class Project(
    val id: Long = 0,
    val name: String,
    val category: Category = Category.BUILD,
    val colorIndex: Int = 0,
    val targetMinutesPerWeek: Int = 0,
    val active: Boolean = true,
    val notes: String? = null,
)

data class Task(
    val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val category: Category = Category.ACADEMICS,
    val subjectCode: String? = null,
    val projectId: Long? = null,
    val estimatedMinutes: Int = 45,
    val completedMinutes: Int = 0,
    val minSessionMinutes: Int = 25,
    val maxSessionMinutes: Int = 60,
    val priority: Priority = Priority.NORMAL,
    val difficulty: Difficulty = Difficulty.MODERATE,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    val deadlineDate: LocalDate? = null,
    val deadlineMinute: Int? = null,
    val preferredStart: Int? = null,
    val preferredEnd: Int? = null,
    val splittable: Boolean = true,
    val recurrenceMask: Int = 0,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val createdAt: Long = 0,
    val completedAt: Long? = null,
    val pinnedDate: LocalDate? = null,
) {
    val remainingMinutes: Int get() = (estimatedMinutes - completedMinutes).coerceAtLeast(0)

    val isRecurring: Boolean get() = recurrenceMask != 0

    val preferredWindow: TimeRange?
        get() {
            val s = preferredStart
            val e = preferredEnd
            return if (s != null && e != null && e > s) TimeRange(s, e) else null
        }

    fun recursOn(day: DayOfWeek): Boolean =
        recurrenceMask != 0 && (recurrenceMask shr (day.value - 1)) and 1 == 1
}

data class TaskSession(
    val id: Long = 0,
    val taskId: Long,
    val date: LocalDate,
    val plannedMinutes: Int,
    val completedMinutes: Int = 0,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
)

data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val category: Category = Category.PERSONAL,
    val allDay: Boolean = false,
    /** Hard events block the planner outright; soft events can be worked around. */
    val hard: Boolean = true,
    val createdBySuggestion: Boolean = false,
) {
    val range: TimeRange get() = TimeRange(start, end)
}

data class ScheduleBlock(
    val id: Long = 0,
    val date: LocalDate,
    val start: Int,
    val end: Int,
    val type: BlockType,
    val title: String,
    val subtitle: String? = null,
    val category: Category = Category.OTHER,
    val status: BlockStatus = BlockStatus.PLANNED,
    val taskId: Long? = null,
    val eventId: Long? = null,
    val timetableEntryId: Long? = null,
    val routineId: Long? = null,
    val projectId: Long? = null,
    val subjectCode: String? = null,
    /** A locked block was placed by the user and the planner must not move it. */
    val locked: Boolean = false,
    val actualStart: Long? = null,
    val actualEnd: Long? = null,
    val elapsedMinutes: Int = 0,
    val reason: String? = null,
    val planVersion: Int = 0,
) {
    val range: TimeRange get() = TimeRange(start, end)
    val duration: Int get() = end - start
    val isOpen: Boolean get() = status == BlockStatus.PLANNED || status == BlockStatus.ACTIVE
}

data class DailyPlan(
    val date: LocalDate,
    val version: Int = 1,
    val generatedAt: Long = 0,
    val headline: String? = null,
    val energyNote: String? = null,
)

data class DailyCheckIn(
    val date: LocalDate,
    val energy: Int?,
    val note: String? = null,
    val unexpected: String? = null,
    val carryForward: Boolean = true,
    val completedAt: Long = 0,
)

data class CompletionRecord(
    val id: Long = 0,
    val date: LocalDate,
    val blockId: Long?,
    val taskId: Long?,
    val category: Category,
    val minutes: Int,
    val at: Long,
)

data class SkipRecord(
    val id: Long = 0,
    val date: LocalDate,
    val blockId: Long?,
    val taskId: Long?,
    val title: String,
    val reason: String? = null,
    val resolution: SkipResolution = SkipResolution.UNRESOLVED,
    val at: Long,
)

data class RescheduleRecord(
    val id: Long = 0,
    val date: LocalDate,
    val blockId: Long?,
    val taskId: Long?,
    val title: String,
    val fromStart: Int,
    val toDate: LocalDate,
    val toStart: Int,
    val reason: String? = null,
    val at: Long,
)

data class ResourceLink(
    val id: Long = 0,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val label: String,
    val url: String,
)
