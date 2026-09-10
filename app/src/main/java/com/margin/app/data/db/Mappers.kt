package com.margin.app.data.db

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
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DailyCheckIn
import com.margin.app.domain.model.DailyPlan
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.ExceptionType
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.RescheduleRecord
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.RoutineKind
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskSession
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableException
import com.margin.app.domain.model.TimetableKind
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Storage keeps stable strings and integers; the domain keeps types. Every conversion in
 * this file tolerates unknown values by falling back to a sane default rather than throwing,
 * so a corrupt row degrades one item instead of crashing the app.
 */

private fun day(value: Int): DayOfWeek =
    DayOfWeek.of(value.coerceIn(1, 7))

private fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

// ---- Subject -------------------------------------------------------------------------------

fun SubjectEntity.toDomain() = Subject(
    code = code,
    name = name,
    shortName = shortName,
    reviewWeight = reviewWeight,
    colorIndex = colorIndex,
    active = active,
)

fun Subject.toEntity() = SubjectEntity(
    code = code,
    name = name,
    shortName = shortName,
    reviewWeight = reviewWeight,
    colorIndex = colorIndex,
    active = active,
)

// ---- Timetable -----------------------------------------------------------------------------

fun TimetableEntryEntity.toDomain() = TimetableEntry(
    id = id,
    dayOfWeek = day(dayOfWeek),
    start = start,
    end = end,
    subjectCode = subjectCode,
    title = title,
    kind = TimetableKind.fromKey(kind),
    faculty = faculty,
    location = location,
    active = active,
)

fun TimetableEntry.toEntity() = TimetableEntryEntity(
    id = id,
    dayOfWeek = dayOfWeek.value,
    start = start,
    end = end,
    subjectCode = subjectCode,
    title = title,
    kind = kind.key,
    faculty = faculty,
    location = location,
    active = active,
)

fun TimetableExceptionEntity.toDomain() = TimetableException(
    id = id,
    date = date(date),
    type = ExceptionType.fromKey(type),
    entryId = entryId,
    note = note,
    title = title,
    subjectCode = subjectCode,
    start = start,
    end = end,
)

fun TimetableException.toEntity() = TimetableExceptionEntity(
    id = id,
    date = date.toEpochDay(),
    type = type.key,
    entryId = entryId,
    note = note,
    title = title,
    subjectCode = subjectCode,
    start = start,
    end = end,
)

// ---- Routine -------------------------------------------------------------------------------

fun RoutineEntity.toDomain() = Routine(
    id = id,
    title = title,
    kind = RoutineKind.fromKey(kind),
    category = Category.fromKey(category),
    daysMask = daysMask,
    start = start,
    end = end,
    active = active,
    protectedTime = protectedTime,
)

fun Routine.toEntity() = RoutineEntity(
    id = id,
    title = title,
    kind = kind.key,
    category = category.key,
    daysMask = daysMask,
    start = start,
    end = end,
    active = active,
    protectedTime = protectedTime,
)

// ---- Project -------------------------------------------------------------------------------

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    category = Category.fromKey(category),
    colorIndex = colorIndex,
    targetMinutesPerWeek = targetMinutesPerWeek,
    active = active,
    notes = notes,
)

fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    category = category.key,
    colorIndex = colorIndex,
    targetMinutesPerWeek = targetMinutesPerWeek,
    active = active,
    notes = notes,
)

// ---- Task ----------------------------------------------------------------------------------

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    category = Category.fromKey(category),
    subjectCode = subjectCode,
    projectId = projectId,
    estimatedMinutes = estimatedMinutes,
    completedMinutes = completedMinutes,
    minSessionMinutes = minSessionMinutes,
    maxSessionMinutes = maxSessionMinutes,
    priority = Priority.fromKey(priority),
    difficulty = Difficulty.fromKey(difficulty),
    energy = EnergyLevel.fromKey(energy),
    deadlineDate = deadlineDate?.let { date(it) },
    deadlineMinute = deadlineMinute,
    preferredStart = preferredStart,
    preferredEnd = preferredEnd,
    splittable = splittable,
    recurrenceMask = recurrenceMask,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.ACTIVE),
    createdAt = createdAt,
    completedAt = completedAt,
    pinnedDate = pinnedDate?.let { date(it) },
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    category = category.key,
    subjectCode = subjectCode,
    projectId = projectId,
    estimatedMinutes = estimatedMinutes,
    completedMinutes = completedMinutes,
    minSessionMinutes = minSessionMinutes,
    maxSessionMinutes = maxSessionMinutes,
    priority = priority.key,
    difficulty = difficulty.key,
    energy = energy.key,
    deadlineDate = deadlineDate?.toEpochDay(),
    deadlineMinute = deadlineMinute,
    preferredStart = preferredStart,
    preferredEnd = preferredEnd,
    splittable = splittable,
    recurrenceMask = recurrenceMask,
    status = status.name,
    createdAt = createdAt,
    completedAt = completedAt,
    pinnedDate = pinnedDate?.toEpochDay(),
)

fun TaskSessionEntity.toDomain() = TaskSession(
    id = id,
    taskId = taskId,
    date = date(date),
    plannedMinutes = plannedMinutes,
    completedMinutes = completedMinutes,
    startedAt = startedAt,
    endedAt = endedAt,
)

// ---- Event ---------------------------------------------------------------------------------

fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id,
    title = title,
    notes = notes,
    date = date(date),
    start = start,
    end = end,
    category = Category.fromKey(category),
    allDay = allDay,
    hard = hard,
    createdBySuggestion = createdBySuggestion,
)

fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    title = title,
    notes = notes,
    date = date.toEpochDay(),
    start = start,
    end = end,
    category = category.key,
    allDay = allDay,
    hard = hard,
    createdBySuggestion = createdBySuggestion,
)

// ---- Schedule block ------------------------------------------------------------------------

fun ScheduleBlockEntity.toDomain() = ScheduleBlock(
    id = id,
    date = date(date),
    start = start,
    end = end,
    type = BlockType.fromKey(type),
    title = title,
    subtitle = subtitle,
    category = Category.fromKey(category),
    status = runCatching { BlockStatus.valueOf(status) }.getOrDefault(BlockStatus.PLANNED),
    taskId = taskId,
    eventId = eventId,
    timetableEntryId = timetableEntryId,
    routineId = routineId,
    projectId = projectId,
    subjectCode = subjectCode,
    locked = locked,
    actualStart = actualStart,
    actualEnd = actualEnd,
    elapsedMinutes = elapsedMinutes,
    reason = reason,
    planVersion = planVersion,
)

fun ScheduleBlock.toEntity(planKey: String = "") = ScheduleBlockEntity(
    id = id,
    date = date.toEpochDay(),
    start = start,
    end = end,
    type = type.key,
    title = title,
    subtitle = subtitle,
    category = category.key,
    status = status.name,
    taskId = taskId,
    eventId = eventId,
    timetableEntryId = timetableEntryId,
    routineId = routineId,
    projectId = projectId,
    subjectCode = subjectCode,
    locked = locked,
    actualStart = actualStart,
    actualEnd = actualEnd,
    elapsedMinutes = elapsedMinutes,
    reason = reason,
    planVersion = planVersion,
    planKey = planKey,
)

// ---- Plan and check-in ---------------------------------------------------------------------

fun DailyPlanEntity.toDomain() = DailyPlan(
    date = date(date),
    version = version,
    generatedAt = generatedAt,
    headline = headline,
    energyNote = energyNote,
)

fun DailyCheckInEntity.toDomain() = DailyCheckIn(
    date = date(date),
    energy = energy,
    note = note,
    unexpected = unexpected,
    carryForward = carryForward,
    completedAt = completedAt,
)

fun DailyCheckIn.toEntity() = DailyCheckInEntity(
    date = date.toEpochDay(),
    energy = energy,
    note = note,
    unexpected = unexpected,
    carryForward = carryForward,
    completedAt = completedAt,
)

// ---- History -------------------------------------------------------------------------------

fun CompletionRecordEntity.toDomain() = CompletionRecord(
    id = id,
    date = date(date),
    blockId = blockId,
    taskId = taskId,
    category = Category.fromKey(category),
    minutes = minutes,
    at = at,
)

fun SkipRecordEntity.toDomain() = SkipRecord(
    id = id,
    date = date(date),
    blockId = blockId,
    taskId = taskId,
    title = title,
    reason = reason,
    resolution = runCatching { SkipResolution.valueOf(resolution) }
        .getOrDefault(SkipResolution.UNRESOLVED),
    at = at,
)

fun RescheduleRecordEntity.toDomain() = RescheduleRecord(
    id = id,
    date = date(date),
    blockId = blockId,
    taskId = taskId,
    title = title,
    fromStart = fromStart,
    toDate = date(toDate),
    toStart = toStart,
    reason = reason,
    at = at,
)

fun ResourceLinkEntity.toDomain() = ResourceLink(
    id = id,
    taskId = taskId,
    projectId = projectId,
    label = label,
    url = url,
)
