package com.margin.app.data.db

import com.margin.app.data.db.entity.BreakRecordEntity
import com.margin.app.data.db.entity.CalendarEventEntity
import com.margin.app.data.db.entity.CompletionRecordEntity
import com.margin.app.data.db.entity.DailyCheckInEntity
import com.margin.app.data.db.entity.DailyPlanEntity
import com.margin.app.data.db.entity.DayStateEntity
import com.margin.app.data.db.entity.DeferredWorkEntity
import com.margin.app.data.db.entity.ExamEntity
import com.margin.app.data.db.entity.LearningGoalEntity
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
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.BreakReason
import com.margin.app.domain.model.BreakRecord
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DailyCheckIn
import com.margin.app.domain.model.DailyPlan
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.ExceptionType
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.RescheduleRecord
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.RoutineKind
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipKind
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.SkipResolution
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskSession
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableException
import com.margin.app.domain.model.TimetableKind
import com.margin.app.domain.planner.PlannedWork
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

private fun Set<String>.joined(): String = sorted().joinToString(",")

private fun String.splitSet(): Set<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

// ---- Subject -------------------------------------------------------------------------------

fun SubjectEntity.toDomain() = Subject(
    code = code,
    name = name,
    shortName = shortName,
    reviewWeight = reviewWeight,
    colorIndex = colorIndex,
    active = active,
    importance = importance,
    difficulty = Difficulty.fromKey(difficulty),
)

fun Subject.toEntity() = SubjectEntity(
    code = code,
    name = name,
    shortName = shortName,
    reviewWeight = reviewWeight,
    colorIndex = colorIndex,
    active = active,
    importance = importance,
    difficulty = difficulty.key,
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

// ---- Project and learning ------------------------------------------------------------------

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

fun LearningGoalEntity.toDomain() = LearningGoal(
    id = id,
    name = name,
    weeklyTargetMinutes = weeklyTargetMinutes,
    sessionMinutes = sessionMinutes,
    active = active,
    pauseDuringExams = pauseDuringExams,
    notes = notes,
)

fun LearningGoal.toEntity() = LearningGoalEntity(
    id = id,
    name = name,
    weeklyTargetMinutes = weeklyTargetMinutes,
    sessionMinutes = sessionMinutes,
    active = active,
    pauseDuringExams = pauseDuringExams,
    notes = notes,
)

// ---- Exams ---------------------------------------------------------------------------------

fun ExamEntity.toDomain() = Exam(
    id = id,
    subjectCode = subjectCode,
    title = title,
    date = date(date),
    startMinute = startMinute,
    endMinute = endMinute,
    academicType = AcademicType.fromKey(academicType) ?: AcademicType.THEORY,
    notes = notes,
)

fun Exam.toEntity() = ExamEntity(
    id = id,
    subjectCode = subjectCode,
    title = title,
    date = date.toEpochDay(),
    startMinute = startMinute,
    endMinute = endMinute,
    academicType = academicType.key,
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
    academicType = AcademicType.fromKey(academicType),
    candidateId = planKey.ifBlank { null },
    learningGoalId = learningGoalId,
    extendedMinutes = extendedMinutes,
    optional = optional,
    plannedStart = plannedStart,
    plannedMinutes = plannedMinutes,
    skipResolution = skipResolution?.let { runCatching { SkipResolution.valueOf(it) }.getOrNull() },
)

fun ScheduleBlock.toEntity() = ScheduleBlockEntity(
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
    planKey = candidateId.orEmpty(),
    academicType = academicType?.key,
    extendedMinutes = extendedMinutes,
    optional = optional,
    learningGoalId = learningGoalId,
    plannedStart = plannedStart,
    plannedMinutes = plannedMinutes,
    skipResolution = skipResolution?.name,
)

// ---- Plan, day state and check-in ------------------------------------------------------------

@Serializable
private data class PlannedWorkDto(
    val id: String,
    val title: String,
    val subject: String? = null,
    val type: String? = null,
    val minutes: Int,
)

private val storageJson = Json { ignoreUnknownKeys = true }

fun encodePlannedWork(work: List<PlannedWork>): String =
    storageJson.encodeToString(
        ListSerializer(PlannedWorkDto.serializer()),
        work.map { PlannedWorkDto(it.id, it.title, it.subjectCode, it.academicType?.key, it.minutes) },
    )

fun decodePlannedWork(raw: String?): List<PlannedWork> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        storageJson.decodeFromString(ListSerializer(PlannedWorkDto.serializer()), raw)
            .map { PlannedWork(it.id, it.title, it.subject, AcademicType.fromKey(it.type), it.minutes) }
    }.getOrDefault(emptyList())
}

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
    workload = workload,
)

fun DailyCheckIn.toEntity() = DailyCheckInEntity(
    date = date.toEpochDay(),
    energy = energy,
    note = note,
    unexpected = unexpected,
    carryForward = carryForward,
    completedAt = completedAt,
    workload = workload,
)

fun DayStateEntity.toDomain() = DayState(
    date = date(date),
    lightDay = lightDay,
    essentials = essentials.splitSet(),
    prioritySubjects = prioritySubjects.splitSet(),
    excludedSubjects = excludedSubjects.splitSet(),
    buildDecision = Decision.fromKey(buildDecision),
    buildProjectId = buildProjectId,
    buildMinutes = buildMinutes,
    learningDecision = Decision.fromKey(learningDecision),
    learningGoalId = learningGoalId,
    learningMinutes = learningMinutes,
    minimumDay = minimumDay,
    minimumDayDismissed = minimumDayDismissed,
    outUntilMinute = outUntilMinute,
)

fun DayState.toEntity() = DayStateEntity(
    date = date.toEpochDay(),
    lightDay = lightDay,
    essentials = essentials.joined(),
    prioritySubjects = prioritySubjects.joined(),
    excludedSubjects = excludedSubjects.joined(),
    buildDecision = buildDecision.key,
    buildProjectId = buildProjectId,
    buildMinutes = buildMinutes,
    learningDecision = learningDecision.key,
    learningGoalId = learningGoalId,
    learningMinutes = learningMinutes,
    minimumDay = minimumDay,
    minimumDayDismissed = minimumDayDismissed,
    outUntilMinute = outUntilMinute,
)

fun DeferredWorkEntity.toDomain() = DeferredWork(
    id = id,
    sourceKey = sourceKey,
    fromDate = date(fromDate),
    toDate = date(toDate),
    subjectCode = subjectCode,
    academicType = AcademicType.fromKey(academicType),
    title = title,
    minutes = minutes,
    reason = reason,
    consumed = consumed,
)

fun DeferredWork.toEntity() = DeferredWorkEntity(
    id = id,
    sourceKey = sourceKey,
    fromDate = fromDate.toEpochDay(),
    toDate = toDate.toEpochDay(),
    subjectCode = subjectCode,
    academicType = academicType?.key,
    title = title,
    minutes = minutes,
    reason = reason,
    consumed = consumed,
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
    type = BlockType.fromKey(type),
    subjectCode = subjectCode,
    academicType = AcademicType.fromKey(academicType),
    plannedMinutes = plannedMinutes,
    startMinute = startMinute,
    projectId = projectId,
    learningGoalId = learningGoalId,
    extendedMinutes = extendedMinutes,
    title = title,
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
    kind = SkipKind.fromKey(kind),
    subjectCode = subjectCode,
    academicType = AcademicType.fromKey(academicType),
    plannedStart = plannedStart,
    plannedMinutes = plannedMinutes,
    category = category?.let { Category.fromKey(it) },
    type = type?.let { BlockType.fromKey(it) },
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

fun BreakRecordEntity.toDomain() = BreakRecord(
    id = id,
    date = date(date),
    startMinute = startMinute,
    plannedMinutes = plannedMinutes,
    actualMinutes = actualMinutes,
    reason = BreakReason.fromKey(reason),
    at = at,
)

fun ResourceLinkEntity.toDomain() = ResourceLink(
    id = id,
    taskId = taskId,
    projectId = projectId,
    label = label,
    url = url,
)
