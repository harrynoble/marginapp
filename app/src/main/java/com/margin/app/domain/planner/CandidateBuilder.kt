package com.margin.app.domain.planner

import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.CompletionRecord
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.DeferredWork
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.RoutineKind
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.model.SkipRecord
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.SubjectTrack
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The knobs the candidate builder reads, beyond the ones the engine reads. */
data class PlanSettings(
    /** A track untouched for this many days starts asking for its own study session. */
    val coverageGapDays: Int = 3,
    val studySessionMinutes: Int = 45,
    /** Labs are long; revising one takes a fraction of its length compared with a lecture. */
    val labReviewFactor: Float = 0.5f,
    val maxCoveragePerDay: Int = 2,
    /** "I prefer studying after 4 PM". Null when the user has no preference. */
    val preferredStudyStart: Int? = null,
    val learningWindow: TimeRange = TimeRange(17 * 60, 22 * 60 + 30),
    val carryForwardCap: Int = 90,
    val buildEnabled: Boolean = true,
    val learningEnabled: Boolean = true,
    /** Learning time on days without a goal-specific session length. */
    val learningMinutesWeekday: Int = 30,
    val learningMinutesWeekend: Int = 45,
)

/** Everything the builder needs, as plain data. The service gathers it; the builder decides. */
data class PlanningContext(
    val date: LocalDate,
    val prefs: PlannerPreferences,
    val settings: PlanSettings = PlanSettings(),
    val dayState: DayState = DayState(date),
    /** The user marked this date as a holiday: no college, planned like a weekend. */
    val holiday: Boolean = false,
    /** Set when replanning today; capacity is only counted from here. */
    val nowMinute: Int? = null,
    val classes: List<TimetableEntry> = emptyList(),
    val weeklyEntries: List<TimetableEntry> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val learningGoals: List<LearningGoal> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val completions: List<CompletionRecord> = emptyList(),
    val skips: List<SkipRecord> = emptyList(),
    val deferred: List<DeferredWork> = emptyList(),
    val settled: List<ScheduleBlock> = emptyList(),
    val taskCapacities: List<WorkloadAllocator.DayCapacity> = emptyList(),
    val weekBuildByProject: Map<Long, Int> = emptyMap(),
    val weekLearningByGoal: Map<Long, Int> = emptyMap(),
)

enum class DayMode(val label: String) {
    NORMAL("Normal day"),
    LIGHT("Light day"),
    MINIMUM("Minimum day"),
}

data class BuiltPlan(
    /** The engine preferences with exam mode and the day's choices applied. */
    val prefs: PlannerPreferences,
    val commitments: List<Commitment>,
    val work: List<WorkCandidate>,
    val quotas: List<QuotaCandidate>,
    val mode: DayMode,
    val exam: ExamPressure,
    val dayType: DayType,
    /** Plain sentences for the user about decisions made today. */
    val notes: List<String>,
    /** What the day intends, before today's progress is subtracted. Used for carryover. */
    val intended: List<PlannedWork>,
    val coverage: Map<SubjectTrack, TrackCoverage>,
    val learned: LearnedPatterns,
)

/**
 * Decides what should be considered today and how much of it: tasks, revision of today's
 * classes, study for neglected subjects, exam preparation, carried work, build, learning and
 * leisure. It never decides where anything goes; that is [DayPlanner].
 *
 * Pure and deterministic, so every product rule here (theory and lab kept apart, no subject
 * forgotten, exams reshaping the balance, light and minimum days) is unit tested directly.
 */
object CandidateBuilder {

    fun build(ctx: PlanningContext): BuiltPlan {
        val date = ctx.date
        val epoch = date.toEpochDay()
        val subjects = ctx.subjects.associateBy { it.code }
        val exam = ExamPlanner.pressure(date, ctx.exams)
        val learned = HistoryLearner.learn(ctx.completions, ctx.skips)
        val notes = mutableListOf<String>()

        // ---- the day's shape -----------------------------------------------------------
        // A weekend or a holiday has no college, so it is an open day: a review of the whole
        // week, more build and learning, and more rest. Exams still outrank all of that.
        val openDay = DayType.isWeekend(date) || ctx.holiday
        val dayType = DayType.of(date, ctx.holiday, exam.active)
        val light = ctx.dayState.lightDay
        val leisureFloor = if (openDay && !exam.active) {
            (ctx.prefs.minLeisureMinutes * OPEN_DAY_LEISURE_FACTOR).roundToInt()
        } else {
            ctx.prefs.minLeisureMinutes
        }
        var prefs = ctx.prefs.copy(
            maxWorkMinutesPerDay = (ctx.prefs.maxWorkMinutesPerDay * exam.ceilingFactor).roundToInt(),
            minLeisureMinutes = scaledLeisure(leisureFloor, exam.leisureFactor),
            energyMode = if (light) EnergyMode.LIGHT else ctx.prefs.energyMode,
        )

        val tracks = CoverageCalculator.tracksFrom(ctx.weeklyEntries) +
            ctx.exams.mapNotNull { it.track }
        val coverage = CoverageCalculator.compute(
            date = date,
            tracks = tracks,
            completions = ctx.completions,
            skips = ctx.skips,
            lastClassByTrack = CoverageCalculator.lastClassDates(date, ctx.weeklyEntries),
            exams = ctx.exams,
        )

        val consumed = Carryover.consumedByCandidate(ctx.settled)
        val commitments = commitments(ctx)
        val studyWindow = ctx.settings.preferredStudyStart?.let { start ->
            val end = if (prefs.sleepMinute > start) prefs.sleepMinute else 24 * 60
            if (end > start) TimeRange(start, end) else null
        } ?: learned.bestWindow

        // ---- candidates ---------------------------------------------------------------
        val tasks = taskCandidates(ctx, prefs, exam, subjects)
        val reviews = if (prefs.reviewEnabled) reviewCandidates(ctx, prefs, subjects, learned, studyWindow) else emptyList()
        val examStudy = examCandidates(ctx, prefs, exam, subjects, learned, studyWindow, epoch)
        val carried = carriedCandidates(ctx, studyWindow)
        val coveredTracks = (reviews + examStudy + carried).mapNotNull { trackOf(it) }.toSet()
        // On an open day the weekly review covers every subject, so it replaces the daily
        // top-up for neglected ones rather than doubling it.
        val coverageStudy = if (openDay) {
            emptyList()
        } else {
            coverageCandidates(
                ctx = ctx,
                prefs = prefs,
                coverage = coverage,
                subjects = subjects,
                learned = learned,
                studyWindow = studyWindow,
                exclude = coveredTracks,
                examActive = exam.active,
                epoch = epoch,
            )
        }
        val weekly = if (openDay) {
            weeklyReviewCandidates(
                ctx = ctx,
                prefs = prefs,
                coverage = coverage,
                subjects = subjects,
                learned = learned,
                exam = exam,
                studyWindow = studyWindow,
                exclude = coveredTracks,
                otherWork = (tasks + examStudy + carried).sumOf { it.minutes },
                epoch = epoch,
            )
        } else {
            WeeklyReview(emptyList(), emptyList())
        }
        if (openDay) notes += openDayNotes(ctx, dayType, weekly, subjects)

        var candidates = (tasks + reviews + examStudy + carried + coverageStudy + weekly.candidates)

        // ---- the user's choices for today ---------------------------------------------
        val excluded = ctx.dayState.excludedSubjects
        if (excluded.isNotEmpty()) {
            candidates = candidates.filterNot { it.subjectCode != null && it.subjectCode in excluded }
            for (code in excluded.sorted()) {
                val worst = coverage.values.filter { it.track.subjectCode == code }.maxOfOrNull { it.neglectDays } ?: 0
                val name = subjects[code]?.name ?: code
                notes += if (worst >= ctx.settings.coverageGapDays) {
                    "$name has gone $worst days without study. It is off today and moves to tomorrow."
                } else {
                    "$name is off today, as you asked."
                }
            }
        }

        val priorities = ctx.dayState.prioritySubjects
        if (priorities.isNotEmpty()) {
            candidates = candidates.map { candidate ->
                if (candidate.subjectCode != null && candidate.subjectCode in priorities) {
                    candidate.copy(
                        importance = candidate.importance + PRIORITY_BONUS,
                        reasons = candidate.reasons + "you made it a priority today",
                    )
                } else {
                    candidate
                }
            }
        }

        if (light) {
            candidates = lighten(candidates, ctx.dayState, exam)
            notes += "A lighter day: only what matters today is planned, with longer breaks."
        }

        // ---- is there enough time? --------------------------------------------------
        val leisureAlready = ctx.settled
            .filter { it.type == BlockType.LEISURE && it.status != BlockStatus.SKIPPED }
            .sumOf { it.duration }
        val leisureNeeded = (prefs.effectiveLeisureFloor - leisureAlready).coerceAtLeast(0)

        var mode = if (light) DayMode.LIGHT else DayMode.NORMAL
        val remainingWork = candidates.sumOf { (it.minutes - (consumed[it.id] ?: 0)).coerceAtLeast(0) }
        val capacity = capacity(ctx, prefs, commitments, leisureNeeded)
        val overloaded = remainingWork > capacity + OVERLOAD_TOLERANCE
        if (ctx.dayState.minimumDay || (overloaded && !ctx.dayState.minimumDayDismissed)) {
            mode = DayMode.MINIMUM
            candidates = minimum(candidates, ctx.dayState, exam)
            notes += if (ctx.dayState.minimumDay) {
                "Minimum day: only the most urgent academic work is planned. Breaks and some " +
                    "downtime are kept."
            } else {
                "There isn't enough time for everything today. I've kept the most important " +
                    "academic work and left build and learning out."
            }
        }

        val intended = candidates.map {
            PlannedWork(it.id, it.title, it.subjectCode, it.academicType, it.minutes)
        }

        // Subtract what today has already used, and drop what is left too small to plan.
        val work = candidates.mapNotNull { candidate ->
            val left = candidate.minutes - (consumed[candidate.id] ?: 0)
            if (left < MIN_PLACEABLE) {
                null
            } else {
                candidate.copy(
                    minutes = left,
                    minSession = candidate.minSession.coerceAtMost(left),
                )
            }
        }.sortedBy { it.id }

        // ---- protected and offered time ----------------------------------------------
        val quotas = mutableListOf<QuotaCandidate>()
        if (leisureNeeded >= prefs.minLeisureChunk) {
            quotas += QuotaCandidate(
                id = "quota:leisure",
                minutes = leisureNeeded,
                kind = QuotaKind.LEISURE,
                title = "Leisure",
                window = prefs.leisureWindow,
                minChunk = prefs.minLeisureChunk,
                category = Category.LEISURE,
                type = BlockType.LEISURE,
                reason = "Your downtime. Reserved before any work was placed.",
            )
        }
        if (mode != DayMode.MINIMUM) {
            buildQuota(ctx, prefs, exam, work)?.let { quotas += it }
            learningQuota(ctx, exam)?.let { quotas += it }
        }

        if (exam.active && exam.nearest != null) {
            val days = exam.nearestDays ?: 0
            val name = exam.nearest.subjectCode?.let { subjects[it]?.name } ?: exam.nearest.title
            notes += "Exam mode: $name is ${daysPhrase(days)}. Academic work comes first; " +
                "build and learning are scaled back."
        }

        return BuiltPlan(
            prefs = prefs,
            commitments = commitments,
            work = work,
            quotas = quotas,
            mode = mode,
            exam = exam,
            dayType = dayType,
            notes = notes.distinct(),
            intended = intended,
            coverage = coverage,
            learned = learned,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Commitments
    // ---------------------------------------------------------------------------------------

    private fun commitments(ctx: PlanningContext): List<Commitment> {
        val out = mutableListOf<Commitment>()
        for (entry in ctx.classes) {
            val type = if (entry.kind == TimetableKind.RECESS) BlockType.BREAK else BlockType.CLASS
            val academic = entry.academicType
            out += Commitment(
                id = "class:${entry.id}",
                title = entry.title,
                subtitle = listOfNotNull(
                    academic?.label,
                    entry.subjectCode.takeIf { entry.kind != TimetableKind.RECESS },
                    entry.location,
                ).joinToString(" · ").ifBlank { null },
                range = entry.range,
                type = type,
                category = if (type == BlockType.BREAK) Category.LEISURE else Category.ACADEMICS,
                subjectCode = entry.subjectCode,
                timetableEntryId = entry.id.takeIf { it > 0 },
                generatesReview = entry.kind.isTeaching,
                academicType = academic,
            )
        }
        // Travel exists because of college. On a holiday, or any day without classes, there
        // is nothing to travel to.
        val collegeToday = ctx.classes.any { it.kind.isTeaching }
        for (routine in ctx.routines) {
            if (!routine.active || !routine.appliesTo(ctx.date.dayOfWeek)) continue
            if (routine.kind == RoutineKind.COMMUTE && !collegeToday) continue
            out += Commitment(
                id = "routine:${routine.id}",
                title = routine.title,
                range = routine.range,
                type = when (routine.kind) {
                    RoutineKind.MEAL -> BlockType.MEAL
                    RoutineKind.COMMUTE -> BlockType.COMMUTE
                    RoutineKind.SLEEP -> BlockType.SLEEP
                    RoutineKind.COMMITMENT -> BlockType.ROUTINE
                },
                category = routine.category,
                routineId = routine.id,
            )
        }
        for (event in ctx.events) {
            out += Commitment(
                id = "event:${event.id}",
                title = event.title,
                subtitle = event.notes,
                range = if (event.allDay) TimeRange(0, 24 * 60) else event.range,
                type = BlockType.EVENT,
                category = event.category,
                eventId = event.id,
            )
        }

        // Settled blocks already occupy their time; a commitment one of them stands for
        // must not be placed twice.
        val settledClassIds = ctx.settled.mapNotNull { it.timetableEntryId }.toSet()
        val settledEventIds = ctx.settled.mapNotNull { it.eventId }.toSet()
        val settledRoutineIds = ctx.settled.mapNotNull { it.routineId }.toSet()
        val settledRanges = ctx.settled
            .filter { com.margin.app.domain.model.BlockStateMachine.occupiesTime(it) }
            .map { it.range }
        return out.filterNot { commitment ->
            val classId = commitment.timetableEntryId
            val eventId = commitment.eventId
            val routineId = commitment.routineId
            (classId != null && classId in settledClassIds) ||
                (eventId != null && eventId in settledEventIds) ||
                (routineId != null && routineId in settledRoutineIds) ||
                settledRanges.any { it.contains(commitment.range) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------------------------------

    private fun taskCandidates(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        exam: ExamPressure,
        subjects: Map<String, Subject>,
    ): List<WorkCandidate> {
        val date = ctx.date
        val creditedToday = ctx.settled
            .filter { it.taskId != null && it.status.isWorked }
            .groupBy { it.taskId!! }
            .mapValues { (_, list) -> list.sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration } }
        val projects = ctx.projects.associateBy { it.id }

        return ctx.tasks.mapNotNull { task ->
            if (task.status != TaskStatus.ACTIVE) return@mapNotNull null
            if (task.isRecurring && !task.recursOn(date.dayOfWeek)) return@mapNotNull null

            val days = task.deadlineDate?.let { (it.toEpochDay() - date.toEpochDay()).toInt() }
            // Only work that is relevant now: due within the horizon, pinned to today, or undated.
            if (days != null && days > RELEVANCE_HORIZON_DAYS && task.pinnedDate != date) return@mapNotNull null
            // Pinned to a later day: it waits for that day.
            if (task.pinnedDate != null && task.pinnedDate.isAfter(date)) return@mapNotNull null

            val wanted = if (task.isRecurring) {
                task.estimatedMinutes
            } else {
                // Progress credited earlier today already shrank the remaining minutes; add it
                // back before sharing out, or today's share would be counted twice.
                val before = task.copy(
                    completedMinutes = (task.completedMinutes - (creditedToday[task.id] ?: 0)).coerceAtLeast(0),
                )
                WorkloadAllocator.minutesFor(before, date, prefs, ctx.taskCapacities)
            }
            if (wanted < MIN_PLACEABLE) return@mapNotNull null

            val reasons = mutableListOf<String>()
            var importance = if (task.pinnedDate == date) 30 else 0
            if (task.pinnedDate == date) reasons += "you set it for today"
            val examDays = task.subjectCode?.let { code ->
                exam.daysByTrack.filterKeys { it.subjectCode == code }.values.minOrNull()
            }
            if (examDays != null && examDays <= 7) {
                importance += ExamPlanner.importanceFor(examDays) / 2
                reasons += "the ${subjects[task.subjectCode]?.shortName ?: task.subjectCode} exam is ${daysPhrase(examDays)}"
            }

            val project = projects[task.projectId]
            WorkCandidate(
                id = "task:${task.id}",
                minutes = wanted,
                title = task.title,
                subtitle = project?.name ?: task.subjectCode,
                type = if (task.category == Category.BUILD) BlockType.BUILD else BlockType.TASK,
                category = task.category,
                taskId = task.id,
                projectId = task.projectId,
                subjectCode = task.subjectCode,
                minSession = task.minSessionMinutes.coerceAtMost(wanted).coerceAtLeast(5),
                maxSession = task.maxSessionMinutes.coerceAtLeast(task.minSessionMinutes),
                priority = task.priority,
                difficulty = task.difficulty,
                energy = task.energy,
                daysToDeadline = days,
                deadlineMinute = task.deadlineMinute?.takeIf { days == 0 },
                preferredWindow = task.preferredWindow,
                splittable = task.splittable,
                importance = importance,
                reasons = reasons,
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Revision of today's classes, theory and lab apart
    // ---------------------------------------------------------------------------------------

    private fun reviewCandidates(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        subjects: Map<String, Subject>,
        learned: LearnedPatterns,
        studyWindow: TimeRange?,
    ): List<WorkCandidate> {
        val teaching = ctx.classes.filter { it.kind.isTeaching && it.track != null }
        if (teaching.isEmpty()) return emptyList()

        val byTrack = teaching.groupBy { it.track!! }
        val raw = byTrack.map { (track, entries) ->
            val taught = entries.sumOf { it.range.duration }
            val weight = subjects[track.subjectCode]?.reviewWeight ?: 1f
            val labFactor = if (track.type == AcademicType.LAB) ctx.settings.labReviewFactor else 1f
            val minutes = (taught / 60f) * prefs.reviewMinutesPerTeachingHour * weight * labFactor *
                learned.ratioFor(track.key)
            Triple(track, minutes, entries.maxOf { it.range.end })
        }.sortedWith(compareByDescending<Triple<SubjectTrack, Float, Int>> { it.second }.thenBy { it.first.key })

        // Every class taught today earns a review. The budget used to be handed out largest
        // first, which starved the lightest subject on the busiest days (Economics on Tuesday
        // and Thursday). Now each track gets the minimum first, and only then does the rest
        // go to the subjects that need more.
        val floor = prefs.minReviewSession
        val budget = maxOf(prefs.maxReviewMinutesPerDay, floor * raw.size)
        var left = budget - floor * raw.size
        val allocated = raw.associate { (track, rawMinutes, _) ->
            val wanted = prefs.roundUp(rawMinutes.roundToInt()).coerceAtLeast(floor)
            val extra = prefs.roundDown(minOf(wanted - floor, left).coerceAtLeast(0))
            left -= extra
            track to floor + extra
        }

        val out = mutableListOf<WorkCandidate>()
        for ((track, _, classEnd) in raw) {
            val minutes = allocated.getValue(track)
            val subject = subjects[track.subjectCode]
            val short = subject?.shortName ?: track.subjectCode
            val lab = track.type == AcademicType.LAB
            val reasons = mutableListOf(if (lab) "you had the $short lab today" else "you had $short class today")
            val ratio = learned.ratioFor(track.key)
            if (ratio >= 1.15f) reasons += "you usually need longer than planned for it"
            out += WorkCandidate(
                id = "review:${track.subjectCode}:${track.type.key}:${ctx.date.toEpochDay()}",
                minutes = minutes,
                title = if (lab) "$short lab work" else "$short review",
                subtitle = if (lab) "Lab · write up and prepare" else "Theory · review today's class",
                type = BlockType.REVIEW,
                category = Category.ACADEMICS,
                subjectCode = track.subjectCode,
                academicType = track.type,
                minSession = prefs.minReviewSession.coerceAtMost(minutes),
                maxSession = minutes,
                earliestStart = classEnd,
                priority = Priority.NORMAL,
                difficulty = subject?.difficulty ?: Difficulty.MODERATE,
                energy = EnergyLevel.MEDIUM,
                splittable = false,
                importance = (subject?.importance ?: 0) * 5,
                preferredWindow = studyWindow,
                reasons = reasons,
            )
        }
        return out
    }

    // ---------------------------------------------------------------------------------------
    // Weekends and holidays: a review of the whole week
    // ---------------------------------------------------------------------------------------

    private data class WeeklyReview(val candidates: List<WorkCandidate>, val deferred: List<SubjectTrack>)

    /**
     * On a day without college, every subject track gets a session sized by how the week went:
     * little or no study against a lot of teaching earns an hour, a subject already studied well
     * gets a light touch. Exams and long neglect add weight; on Sunday, tomorrow's subjects come
     * first. The total stays inside the day's work ceiling so the day never becomes a marathon;
     * if even the minimums do not fit, the least urgent tracks wait and say so.
     */
    private fun weeklyReviewCandidates(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        coverage: Map<SubjectTrack, TrackCoverage>,
        subjects: Map<String, Subject>,
        learned: LearnedPatterns,
        exam: ExamPressure,
        studyWindow: TimeRange?,
        exclude: Set<SubjectTrack>,
        otherWork: Int,
        epoch: Long,
    ): WeeklyReview {
        val date = ctx.date
        val weekStart = date.minusDays(7)
        val tomorrow = date.plusDays(1).dayOfWeek
        val tracks = coverage.keys
            .filter { it !in exclude }
            .filter { subjects[it.subjectCode]?.active != false }
        if (tracks.isEmpty()) return WeeklyReview(emptyList(), emptyList())

        data class Need(
            val track: SubjectTrack,
            val floor: Int,
            val target: Int,
            val importance: Int,
            val studied: Int,
            val reasons: List<String>,
        )

        val needs = tracks.map { track ->
            val subject = subjects[track.subjectCode]
            val short = subject?.shortName ?: track.subjectCode
            val lab = track.type == AcademicType.LAB
            val taught = ctx.weeklyEntries
                .filter { it.kind.isTeaching && it.track == track }
                .sumOf { it.range.duration }
            val studied = ctx.completions
                .filter { !it.date.isBefore(weekStart) && it.date.isBefore(date) }
                .filter { it.subjectCode == track.subjectCode && it.academicType == track.type }
                .sumOf { it.minutes }
            val weight = subject?.reviewWeight ?: 1f
            val labFactor = if (lab) ctx.settings.labReviewFactor else 1f
            val expected = (taught / 60f * prefs.reviewMinutesPerTeachingHour * weight * labFactor).roundToInt()
            val deficit = (expected - studied).coerceAtLeast(0)
            val entry = coverage[track]
            val neglected = entry != null && entry.neglectDays >= ctx.settings.coverageGapDays
            val examDays = exam.daysByTrack[track]
            val hasTomorrow = date.dayOfWeek == DayOfWeek.SUNDAY &&
                ctx.weeklyEntries.any { it.dayOfWeek == tomorrow && it.kind.isTeaching && it.track == track }

            val caughtUp = studied > 0 && deficit == 0
            val floor = when {
                caughtUp -> WEEKLY_LIGHT_FLOOR
                lab -> WEEKLY_LAB_FLOOR
                else -> WEEKLY_FLOOR
            }
            var target = when {
                deficit >= 50 -> 60
                deficit >= 25 -> 45
                else -> floor
            }
            if (neglected) target += 15
            if (examDays != null && examDays <= 10) target = maxOf(target, 60)
            target = prefs.roundUp((target * learned.ratioFor(track.key)).roundToInt())
                .coerceIn(floor, WEEKLY_MAX)

            val importance = (entry?.let { CoverageCalculator.neglectScore(it, subject?.importance ?: 0) } ?: 0)
                .plus(deficit / 4)
                .plus(if (hasTomorrow) SUNDAY_TOMORROW_BONUS else 0)
                .coerceAtMost(MAX_COVERAGE_IMPORTANCE) +
                (examDays?.let { ExamPlanner.importanceFor(it) } ?: 0)

            val reasons = buildList {
                add("it is part of the weekly review")
                when {
                    studied == 0 -> add("$short had no study this week")
                    caughtUp -> add("$short is already well covered this week")
                    else -> add("$short had ${studied} min of study against $expected expected")
                }
                if (neglected) add("it has not been studied for ${entry!!.neglectDays} days")
                if (hasTomorrow) add("you have it tomorrow")
                if (examDays != null && examDays <= 10) add("the exam is ${daysPhrase(examDays)}")
            }
            Need(track, floor, target, importance, studied, reasons)
        }.sortedWith(compareByDescending<Need> { it.importance }.thenBy { it.track.key })

        // Everyone gets their minimum first, most urgent first; the rest of the budget then
        // tops up the tracks that need more. The ceiling keeps room for build, learning and rest.
        var left = (prefs.effectiveWorkCeiling - otherWork.coerceAtMost(prefs.effectiveWorkCeiling / 2)).coerceAtLeast(0)
        val allocated = linkedMapOf<SubjectTrack, Int>()
        for (need in needs) {
            if (left >= need.floor) {
                allocated[need.track] = need.floor
                left -= need.floor
            }
        }
        for (need in needs) {
            val have = allocated[need.track] ?: continue
            val extra = prefs.roundDown(minOf(need.target - have, left).coerceAtLeast(0))
            allocated[need.track] = have + extra
            left -= extra
        }

        val slowStart = prefs.wakeMinute + OPEN_DAY_SLOW_START
        val candidates = needs.filter { it.track in allocated }.map { need ->
            val track = need.track
            val subject = subjects[track.subjectCode]
            val name = subject?.name ?: track.subjectCode
            val lab = track.type == AcademicType.LAB
            val minutes = allocated.getValue(track)
            WorkCandidate(
                id = "weekly:${track.subjectCode}:${track.type.key}:$epoch",
                minutes = minutes,
                title = if (lab) "$name lab review" else "$name review",
                subtitle = track.type.label + " · " + when {
                    need.studied == 0 -> "no study this week"
                    else -> "${need.studied} min this week"
                },
                type = BlockType.STUDY,
                category = Category.ACADEMICS,
                subjectCode = track.subjectCode,
                academicType = track.type,
                minSession = minutes,
                maxSession = minutes,
                earliestStart = slowStart,
                difficulty = subject?.difficulty ?: Difficulty.MODERATE,
                splittable = false,
                importance = need.importance,
                preferredWindow = studyWindow,
                reasons = need.reasons,
            )
        }
        return WeeklyReview(candidates, needs.map { it.track }.filter { it !in allocated })
    }

    private fun openDayNotes(
        ctx: PlanningContext,
        dayType: DayType,
        weekly: WeeklyReview,
        subjects: Map<String, Subject>,
    ): List<String> = buildList {
        when {
            ctx.holiday -> add(
                "Holiday: no college today. The extra time goes to reviewing your subjects, building, " +
                    "learning and rest. Your weekly timetable is unchanged.",
            )
            dayType == DayType.WEEKEND || DayType.isWeekend(ctx.date) -> add(
                "Weekend review: every subject gets time, weighted by how much it had this week. " +
                    "Build and learning get more room, and leisure is kept.",
            )
        }
        if (ctx.date.dayOfWeek == DayOfWeek.SUNDAY) {
            val tomorrow = ctx.date.plusDays(1).dayOfWeek
            val names = ctx.weeklyEntries
                .filter { it.dayOfWeek == tomorrow && it.kind.isTeaching && it.subjectCode != null }
                .mapNotNull { subjects[it.subjectCode]?.shortName ?: it.subjectCode }
                .distinct()
            if (names.isNotEmpty()) add("Tomorrow's subjects come first: ${names.joinToString(", ")}.")
        }
        if (weekly.deferred.isNotEmpty()) {
            val names = weekly.deferred.map { subjects[it.subjectCode]?.shortName ?: it.subjectCode }.distinct()
            add("Not everything fits today; ${names.joinToString(", ")} come first next time.")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Exam preparation
    // ---------------------------------------------------------------------------------------

    private fun examCandidates(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        exam: ExamPressure,
        subjects: Map<String, Subject>,
        learned: LearnedPatterns,
        studyWindow: TimeRange?,
        epoch: Long,
    ): List<WorkCandidate> {
        if (exam.upcoming.isEmpty()) return emptyList()
        return exam.upcoming.mapNotNull { paper ->
            val days = ExamPlanner.daysUntil(ctx.date, paper)
            // On the day itself, study only makes sense before the paper starts.
            if (days == 0 && paper.startMinute == null) return@mapNotNull null
            val subject = paper.subjectCode?.let { subjects[it] }
            val difficultyFactor = when (subject?.difficulty) {
                Difficulty.HARD -> 1.15f
                Difficulty.EASY -> 0.85f
                else -> 1f
            }
            val ratio = paper.track?.let { learned.ratioFor(it.key) } ?: 1f
            val minutes = prefs.roundUp(
                (ExamPlanner.studyMinutesFor(days) * difficultyFactor * ratio).roundToInt(),
            )
            val name = subject?.name ?: paper.title
            val lab = paper.academicType == AcademicType.LAB
            WorkCandidate(
                id = "exam:${paper.id}:$epoch",
                minutes = minutes,
                title = if (lab) "$name lab exam prep" else "$name exam prep",
                subtitle = when (days) {
                    0 -> "Exam today" + (paper.startMinute?.let { " at " + clock(it) } ?: "")
                    1 -> "Exam tomorrow"
                    else -> "Exam in $days days"
                },
                type = BlockType.STUDY,
                category = Category.ACADEMICS,
                subjectCode = paper.subjectCode,
                academicType = paper.academicType,
                minSession = 30,
                maxSession = 60,
                difficulty = subject?.difficulty ?: Difficulty.MODERATE,
                energy = EnergyLevel.HIGH,
                daysToDeadline = null,
                deadlineMinute = if (days == 0) paper.startMinute else null,
                splittable = true,
                importance = ExamPlanner.importanceFor(days) + (subject?.importance ?: 0) * 5,
                preferredWindow = studyWindow,
                reasons = listOf("the exam is ${daysPhrase(days)}"),
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Coverage: no subject is forgotten
    // ---------------------------------------------------------------------------------------

    private fun coverageCandidates(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        coverage: Map<SubjectTrack, TrackCoverage>,
        subjects: Map<String, Subject>,
        learned: LearnedPatterns,
        studyWindow: TimeRange?,
        exclude: Set<SubjectTrack>,
        examActive: Boolean,
        epoch: Long,
    ): List<WorkCandidate> {
        val limit = ctx.settings.maxCoveragePerDay + if (examActive) 1 else 0
        return coverage.values
            .filter { it.track !in exclude }
            .filter { subjects[it.track.subjectCode]?.active != false }
            .filter { it.neglectDays >= ctx.settings.coverageGapDays }
            .map { it to CoverageCalculator.neglectScore(it, subjects[it.track.subjectCode]?.importance ?: 0) }
            .sortedWith(compareByDescending<Pair<TrackCoverage, Int>> { it.second }.thenBy { it.first.track.key })
            .take(limit)
            .map { (entry, score) ->
                val track = entry.track
                val subject = subjects[track.subjectCode]
                val lab = track.type == AcademicType.LAB
                val base = if (lab) LAB_STUDY_MINUTES else ctx.settings.studySessionMinutes
                val minutes = prefs.roundUp((base * learned.ratioFor(track.key)).roundToInt())
                val name = subject?.name ?: track.subjectCode
                val gap = entry.daysSinceStudied
                WorkCandidate(
                    id = "study:${track.subjectCode}:${track.type.key}:$epoch",
                    minutes = minutes,
                    title = if (lab) "$name lab" else name,
                    subtitle = track.type.label + " · " + when (gap) {
                        null -> "not studied yet"
                        1 -> "last studied yesterday"
                        else -> "last studied $gap days ago"
                    },
                    type = BlockType.STUDY,
                    category = Category.ACADEMICS,
                    subjectCode = track.subjectCode,
                    academicType = track.type,
                    minSession = minOf(30, minutes),
                    maxSession = minutes,
                    difficulty = subject?.difficulty ?: Difficulty.MODERATE,
                    splittable = false,
                    importance = score.coerceAtMost(MAX_COVERAGE_IMPORTANCE),
                    preferredWindow = studyWindow,
                    reasons = listOf(
                        if (gap == null) "it has not been studied yet" else "it has not been studied for $gap days",
                        "it keeps every subject covered",
                    ),
                )
            }
    }

    // ---------------------------------------------------------------------------------------
    // Carried work
    // ---------------------------------------------------------------------------------------

    private fun carriedCandidates(ctx: PlanningContext, studyWindow: TimeRange?): List<WorkCandidate> =
        ctx.deferred
            .filter { it.toDate == ctx.date && !it.consumed && it.minutes >= MIN_PLACEABLE }
            .map { item ->
                val from = item.fromDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                WorkCandidate(
                    id = "carry:${item.id}",
                    minutes = item.minutes,
                    title = item.title,
                    subtitle = "Carried over from $from",
                    type = BlockType.STUDY,
                    category = Category.ACADEMICS,
                    subjectCode = item.subjectCode,
                    academicType = item.academicType,
                    minSession = minOf(20, item.minutes),
                    maxSession = item.minutes,
                    splittable = false,
                    importance = CARRY_IMPORTANCE,
                    preferredWindow = studyWindow,
                    reasons = listOf("it was carried over from $from"),
                )
            }

    // ---------------------------------------------------------------------------------------
    // Light and minimum days
    // ---------------------------------------------------------------------------------------

    /**
     * Keeps only what the user said matters, plus anything that genuinely cannot wait. What is
     * dropped is simply not planned: it is not recorded as skipped and it is not piled onto
     * tomorrow, so a light day never costs a heavier one.
     */
    private fun lighten(
        candidates: List<WorkCandidate>,
        state: DayState,
        exam: ExamPressure,
    ): List<WorkCandidate> {
        val essentials = state.essentials
        if (essentials.isEmpty()) {
            return candidates.filter { isCritical(it, exam) || it.type == BlockType.REVIEW }
                .map { if (it.type == BlockType.REVIEW) it.copy(minutes = (it.minutes / 2).coerceAtLeast(MIN_PLACEABLE * 2)) else it }
        }
        return candidates.filter { candidate ->
            val subjectKey = candidate.subjectCode?.let { DayState.subjectKey(it) }
            val taskKey = candidate.taskId?.let { DayState.taskKey(it) }
            (subjectKey != null && subjectKey in essentials) ||
                (taskKey != null && taskKey in essentials) ||
                isCritical(candidate, exam)
        }.map { it.copy(reasons = it.reasons + "you marked it essential today") }
    }

    private fun minimum(
        candidates: List<WorkCandidate>,
        state: DayState,
        exam: ExamPressure,
    ): List<WorkCandidate> {
        val kept = candidates.filter { candidate ->
            val subjectKey = candidate.subjectCode?.let { DayState.subjectKey(it) }
            val taskKey = candidate.taskId?.let { DayState.taskKey(it) }
            isCritical(candidate, exam) ||
                (subjectKey != null && subjectKey in state.essentials) ||
                (taskKey != null && taskKey in state.essentials)
        }.toMutableList()
        // One essential review: the day's biggest, so the classes are not lost entirely.
        if (kept.none { it.type == BlockType.REVIEW }) {
            candidates.filter { it.type == BlockType.REVIEW }
                .maxWithOrNull(compareBy({ it.minutes }, { it.id }))
                ?.let { kept += it.copy(reasons = it.reasons + "it is the day's essential review") }
        }
        return kept
    }

    private fun isCritical(candidate: WorkCandidate, exam: ExamPressure): Boolean {
        val deadline = candidate.daysToDeadline
        if (deadline != null && deadline <= 1) return true
        if (candidate.priority == Priority.CRITICAL) return true
        if (candidate.id.startsWith("exam:")) {
            val track = trackOf(candidate)
            val days = track?.let { exam.daysByTrack[it] } ?: return false
            return days <= 2
        }
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Build and learning
    // ---------------------------------------------------------------------------------------

    private fun buildQuota(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        exam: ExamPressure,
        work: List<WorkCandidate>,
    ): QuotaCandidate? {
        val state = ctx.dayState
        if (!ctx.settings.buildEnabled || state.buildDecision == Decision.DECLINED) return null
        val accepted = state.buildDecision == Decision.ACCEPTED
        // A holiday has the same open time as a weekend, so it gets the weekend's build time.
        val openDay = DayType.isWeekend(ctx.date) || ctx.holiday
        val base = state.buildMinutes ?: if (openDay) prefs.buildMinutesWeekend else prefs.buildMinutesWeekday
        val target = if (accepted) base else (base * exam.buildFactor).roundToInt()
        val fromTasks = work.filter { it.category == Category.BUILD }.sumOf { it.minutes }
        val already = ctx.settled
            .filter { it.type == BlockType.BUILD && it.status != BlockStatus.SKIPPED }
            .sumOf { it.duration }
        val needed = prefs.roundDown((target - fromTasks - already).coerceAtLeast(0))
        if (needed < prefs.minBuildChunk) return null

        val project = state.buildProjectId?.let { id -> ctx.projects.firstOrNull { it.id == id } }
            ?: ctx.projects
                .filter { it.active && it.category == Category.BUILD }
                .sortedWith(
                    compareByDescending<Project> { it.targetMinutesPerWeek - (ctx.weekBuildByProject[it.id] ?: 0) }
                        .thenBy { it.id },
                )
                .firstOrNull()
        return QuotaCandidate(
            id = "quota:build",
            minutes = needed,
            kind = QuotaKind.BUILD,
            title = project?.name ?: "Build",
            subtitle = if (project == null) "Build · pick a project" else "Build",
            window = prefs.buildWindow,
            minChunk = prefs.minBuildChunk,
            category = Category.BUILD,
            type = BlockType.BUILD,
            projectId = project?.id,
            optional = !accepted,
            reason = if (project != null) {
                "Build time for ${project.name}, after the academic work."
            } else {
                "Your build time, after the academic work."
            },
        )
    }

    private fun learningQuota(ctx: PlanningContext, exam: ExamPressure): QuotaCandidate? {
        val state = ctx.dayState
        if (!ctx.settings.learningEnabled || state.learningDecision == Decision.DECLINED) return null
        val goals = ctx.learningGoals.filter { it.active }
        if (goals.isEmpty()) return null
        val accepted = state.learningDecision == Decision.ACCEPTED
        val goal = state.learningGoalId?.let { id -> goals.firstOrNull { it.id == id } }
            ?: goals.sortedWith(
                compareByDescending<LearningGoal> { it.weeklyTargetMinutes - (ctx.weekLearningByGoal[it.id] ?: 0) }
                    .thenBy { it.id },
            ).first()
        // Open days offer more learning time than a college day, never less than the goal's session.
        val openDay = DayType.isWeekend(ctx.date) || ctx.holiday
        val session = goal.sessionMinutes.takeIf { it > 0 }
        val base = state.learningMinutes ?: if (openDay) {
            maxOf(session ?: 0, ctx.settings.learningMinutesWeekend)
        } else {
            session ?: ctx.settings.learningMinutesWeekday
        }
        val factor = if (accepted) 1f else exam.learningFactor(goal.pauseDuringExams)
        val already = ctx.settled
            .filter { it.type == BlockType.LEARN && it.status != BlockStatus.SKIPPED }
            .sumOf { it.duration }
        val needed = ctx.prefs.roundDown(((base * factor).roundToInt() - already).coerceAtLeast(0))
        if (needed < MIN_LEARNING_CHUNK) return null
        return QuotaCandidate(
            id = "quota:learning",
            minutes = needed,
            kind = QuotaKind.LEARNING,
            title = goal.name,
            subtitle = "Learning",
            window = ctx.settings.learningWindow,
            minChunk = MIN_LEARNING_CHUNK,
            category = Category.LEARNING,
            type = BlockType.LEARN,
            learningGoalId = goal.id,
            optional = !accepted,
            reason = "Time for ${goal.name}, once the day's other work is done.",
        )
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** Free minutes left today once commitments, travel, recovery and leisure are counted. */
    private fun capacity(
        ctx: PlanningContext,
        prefs: PlannerPreferences,
        commitments: List<Commitment>,
        leisureNeeded: Int,
    ): Int {
        val wake = prefs.wakeMinute
        val sleep = if (prefs.sleepMinute <= wake) 24 * 60 else prefs.sleepMinute
        val from = maxOf(wake, ctx.nowMinute ?: wake)
        if (from >= sleep) return 0
        val window = TimeRange(from, sleep)
        val busy = (commitments.map { it.range } + ctx.settled.map { it.range })
            .mapNotNull { it.intersect(window) }
        val free = listOf(window).subtractAllRanges(busy).sumOf { it.duration }
        val hasClasses = commitments.any { it.type == BlockType.CLASS }
        val buffers = if (hasClasses) prefs.commuteMinutes + prefs.decompressionMinutes else 0
        return (free - buffers - leisureNeeded).coerceAtLeast(0)
    }

    private fun List<TimeRange>.subtractAllRanges(blocks: List<TimeRange>): List<TimeRange> {
        var result = this
        for (block in blocks) {
            result = result.flatMap { it.minus(block) }.filter { !it.isEmpty }
        }
        return result
    }

    private fun trackOf(candidate: WorkCandidate): SubjectTrack? {
        val code = candidate.subjectCode ?: return null
        val type = candidate.academicType ?: return null
        return SubjectTrack(code, type)
    }

    private fun scaledLeisure(floor: Int, factor: Float): Int {
        if (floor <= 0 || factor >= 1f) return floor
        return (floor * factor).roundToInt().coerceAtLeast(minOf(floor, MIN_EXAM_LEISURE))
    }

    fun daysPhrase(days: Int): String = when (days) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "in $days days"
    }

    private fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    private const val MIN_PLACEABLE = 10
    private const val RELEVANCE_HORIZON_DAYS = 14
    private const val PRIORITY_BONUS = 35
    private const val CARRY_IMPORTANCE = 25
    private const val MAX_COVERAGE_IMPORTANCE = 45
    private const val LAB_STUDY_MINUTES = 30
    private const val MIN_LEARNING_CHUNK = 20
    private const val MIN_EXAM_LEISURE = 45
    private const val OVERLOAD_TOLERANCE = 20

    // Open days: weekends and holidays.
    private const val OPEN_DAY_LEISURE_FACTOR = 1.5f
    /** No review session before this long after waking on a day without college. */
    private const val OPEN_DAY_SLOW_START = 90
    private const val WEEKLY_FLOOR = 30
    private const val WEEKLY_LAB_FLOOR = 25
    private const val WEEKLY_LIGHT_FLOOR = 20
    private const val WEEKLY_MAX = 75
    private const val SUNDAY_TOMORROW_BONUS = 10
}
