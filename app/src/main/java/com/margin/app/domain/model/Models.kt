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
    /** The user's own sense of importance, 0 (normal) to 3 (top). Adds planner weight. */
    val importance: Int = 0,
    val difficulty: Difficulty = Difficulty.MODERATE,
)

/** One subject taught in one way. Theory and lab of the same subject are separate tracks. */
data class SubjectTrack(val subjectCode: String, val type: AcademicType) {
    val key: String get() = "$subjectCode:${type.key}"

    companion object {
        fun parse(key: String): SubjectTrack? {
            val code = key.substringBefore(':', "")
            val type = AcademicType.fromKey(key.substringAfter(':', "")) ?: return null
            return if (code.isBlank()) null else SubjectTrack(code, type)
        }
    }
}

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

    /** Theory for lectures and tutorials, lab for labs, null for recess and the like. */
    val academicType: AcademicType? get() = AcademicType.of(kind)

    val track: SubjectTrack?
        get() {
            val code = subjectCode ?: return null
            val type = academicType ?: return null
            return SubjectTrack(code, type)
        }
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

/** A skill the user wants to pick up. Learning is its own category, not coursework. */
data class LearningGoal(
    val id: Long = 0,
    val name: String,
    val weeklyTargetMinutes: Int = 0,
    val sessionMinutes: Int = 30,
    val active: Boolean = true,
    /** Most people want new-skill time paused while exams are close. */
    val pauseDuringExams: Boolean = true,
    val notes: String? = null,
)

/** One paper in an exam timetable. Separate from the weekly college timetable. */
data class Exam(
    val id: Long = 0,
    val subjectCode: String?,
    val title: String,
    val date: LocalDate,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val academicType: AcademicType = AcademicType.THEORY,
    val notes: String? = null,
) {
    val track: SubjectTrack? get() = subjectCode?.let { SubjectTrack(it, academicType) }
}

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
    val academicType: AcademicType? = null,
    /** The planner candidate this block came from; stable across replans and sessions. */
    val candidateId: String? = null,
    val learningGoalId: Long? = null,
    /** Minutes added past the plan by choosing to continue. */
    val extendedMinutes: Int = 0,
    /** Build and learning are offered, never imposed. Optional blocks can be declined. */
    val optional: Boolean = false,
    /** Where the block sat when it was first planned; kept for missed-session records. */
    val plannedStart: Int? = null,
    /** Length when planned. Finishing early shortens the block but not what it was worth. */
    val plannedMinutes: Int = 0,
    val skipResolution: SkipResolution? = null,
) {
    val range: TimeRange get() = TimeRange(start, end)
    val duration: Int get() = end - start
    val isOpen: Boolean get() = status.isOpen

    /** Coursework: reviews, subject study and academic tasks. Build and learning are not. */
    val isAcademic: Boolean
        get() = type == BlockType.REVIEW || type == BlockType.STUDY ||
            (type == BlockType.TASK && category == Category.ACADEMICS)

    val track: SubjectTrack?
        get() {
            val code = subjectCode ?: return null
            val type = academicType ?: return null
            return SubjectTrack(code, type)
        }
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
    /** 1 too light, 2 about right, 3 too much. */
    val workload: Int? = null,
)

/**
 * Everything the user decided about one particular day: a lighter day and what still matters,
 * subjects left out, whether build and learning are on, and whether they are out.
 */
data class DayState(
    val date: LocalDate,
    val lightDay: Boolean = false,
    /** "subject:DSA" or "task:12". On a light day only these, plus anything critical, stay. */
    val essentials: Set<String> = emptySet(),
    val prioritySubjects: Set<String> = emptySet(),
    val excludedSubjects: Set<String> = emptySet(),
    val buildDecision: Decision = Decision.UNASKED,
    val buildProjectId: Long? = null,
    val buildMinutes: Int? = null,
    val learningDecision: Decision = Decision.UNASKED,
    val learningGoalId: Long? = null,
    val learningMinutes: Int? = null,
    /** The user asked for the bare minimum today. */
    val minimumDay: Boolean = false,
    /** The planner suggested a minimum day and the user said no. */
    val minimumDayDismissed: Boolean = false,
    val outUntilMinute: Int? = null,
) {
    companion object {
        fun subjectKey(code: String) = "subject:$code"
        fun taskKey(id: Long) = "task:$id"
    }
}

/** Academic work that did not happen and has been moved to a later day on purpose. */
data class DeferredWork(
    val id: Long = 0,
    /** The planner candidate it came from, so the same work is never deferred twice. */
    val sourceKey: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val subjectCode: String?,
    val academicType: AcademicType?,
    val title: String,
    val minutes: Int,
    val reason: String,
    val consumed: Boolean = false,
)

data class CompletionRecord(
    val id: Long = 0,
    val date: LocalDate,
    val blockId: Long?,
    val taskId: Long?,
    val category: Category,
    val minutes: Int,
    val at: Long,
    val type: BlockType = BlockType.TASK,
    val subjectCode: String? = null,
    val academicType: AcademicType? = null,
    val plannedMinutes: Int = 0,
    /** Minute of the day the session started, used to learn when the user works well. */
    val startMinute: Int? = null,
    val projectId: Long? = null,
    val learningGoalId: Long? = null,
    val extendedMinutes: Int = 0,
    val title: String? = null,
) {
    val track: SubjectTrack?
        get() {
            val code = subjectCode ?: return null
            val type = academicType ?: return null
            return SubjectTrack(code, type)
        }
}

data class SkipRecord(
    val id: Long = 0,
    val date: LocalDate,
    val blockId: Long?,
    val taskId: Long?,
    val title: String,
    val reason: String? = null,
    val resolution: SkipResolution = SkipResolution.UNRESOLVED,
    val at: Long,
    val kind: SkipKind = SkipKind.SKIPPED,
    val subjectCode: String? = null,
    val academicType: AcademicType? = null,
    val plannedStart: Int? = null,
    val plannedMinutes: Int = 0,
    val category: Category? = null,
    val type: BlockType? = null,
) {
    val track: SubjectTrack?
        get() {
            val code = subjectCode ?: return null
            val type = academicType ?: return null
            return SubjectTrack(code, type)
        }
}

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

data class BreakRecord(
    val id: Long = 0,
    val date: LocalDate,
    val startMinute: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int = plannedMinutes,
    val reason: BreakReason = BreakReason.MANUAL,
    val at: Long,
)

data class ResourceLink(
    val id: Long = 0,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val label: String,
    val url: String,
)
