package com.margin.app.ai.context

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.planner.CoverageCalculator
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Builds the smallest useful description of the situation, one section at a time, and only
 * the sections the message needs. A request about a deadline does not carry subject coverage;
 * a request about going out does not carry the exam timetable.
 *
 * Nothing here sends the database. Notes, history, faculty names and anything the model does
 * not need stay on the device, and when schedule sharing is off the context shrinks to the
 * clock and the free time.
 */
class ContextBuilder(
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val timetableRepository: TimetableRepository,
    private val examRepository: ExamRepository,
    private val dayRepository: DayRepository,
) {

    enum class Section { TODAY, CURRENT_SESSION, DEADLINES, SUBJECT_COVERAGE, EXAMS }

    /** Which sections a message needs, decided by plain keyword checks, not by a model. */
    fun sectionsFor(message: String): Set<Section> {
        val text = message.lowercase()
        val out = mutableSetOf(Section.TODAY, Section.CURRENT_SESSION)
        if (listOf("due", "deadline", "assignment", "task", "finish", "submit", " by ", "project", "homework", "complete")
                .any { text.contains(it) }
        ) {
            out += Section.DEADLINES
        }
        if (listOf("study", "subject", "revise", "review", "haven't", "havent", "lighten", "lighter", "important", "priority", "focus", "today")
                .any { text.contains(it) }
        ) {
            out += Section.SUBJECT_COVERAGE
        }
        if (listOf("exam", "test", "paper", "midterm", "finals").any { text.contains(it) }) {
            out += Section.EXAMS
        }
        return out
    }

    suspend fun build(
        now: LocalDateTime,
        prefs: UserPreferences,
        includeScheduleDetail: Boolean,
        message: String,
    ): String {
        val sections = sectionsFor(message)
        return buildString {
            if (Section.TODAY in sections) append(today(now, prefs, includeScheduleDetail))
            if (Section.CURRENT_SESSION in sections) append(currentSession(now))
            if (Section.DEADLINES in sections) append(deadlines(now.toLocalDate()))
            if (Section.SUBJECT_COVERAGE in sections) append(coverage(now.toLocalDate()))
            if (Section.EXAMS in sections) append(exams(now.toLocalDate()))
        }.trim()
    }

    // ---- sections ------------------------------------------------------------------------------

    private suspend fun today(now: LocalDateTime, prefs: UserPreferences, detail: Boolean): String = buildString {
        val date = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val blocks = scheduleRepository.blocksFor(date)
        val state = dayRepository.state(date)
        appendLine("Date: $date (${date.dayOfWeek})")
        appendLine("Now: ${MarginTime.formatTime(nowMinute, true)}")
        appendLine("Awake: ${MarginTime.formatTime(prefs.wakeMinute, true)} to ${MarginTime.formatTime(prefs.sleepMinute, true)}")
        appendLine("Energy today: ${prefs.energyModeFor(date.toEpochDay()).key}. Light day: ${state.lightDay}.")
        if (detail) {
            val remaining = blocks
                .filter { it.end > nowMinute && it.type != BlockType.SLEEP && it.type != BlockType.FREE }
                .sortedBy { it.start }
            if (remaining.isNotEmpty()) {
                appendLine("Rest of today:")
                remaining.take(MAX_BLOCKS).forEach { block ->
                    append("- id=").append(block.id).append(' ')
                    append(MarginTime.formatTime(block.start, true)).append('-')
                    append(MarginTime.formatTime(block.end, true)).append(' ')
                    append(block.title).append(" [").append(block.type.key)
                    block.academicType?.let { append(", ").append(it.key) }
                    if (block.status != BlockStatus.PLANNED) append(", ").append(block.status.name.lowercase())
                    appendLine("]")
                }
            }
        }
        val free = blocks
            .filter { it.end > nowMinute && (it.type == BlockType.FREE || it.type == BlockType.LEISURE) }
            .sumOf { it.duration }
        appendLine("Free time left today: $free min")
        appendLine()
    }

    private suspend fun currentSession(now: LocalDateTime): String = buildString {
        val active = scheduleRepository.activeBlock() ?: return@buildString
        append("Running now: id=").append(active.id).append(' ').append(active.title)
        active.subjectCode?.let { append(" (").append(it).append(active.academicType?.let { t -> " " + t.key } ?: "").append(')') }
        append(", planned until ").appendLine(MarginTime.formatTime(active.end, true))
        appendLine()
    }

    private suspend fun deadlines(date: LocalDate): String = buildString {
        val tasks = taskRepository.activeTasks()
            .filter { it.status == TaskStatus.ACTIVE && it.remainingMinutes > 0 }
            .filter { it.deadlineDate == null || !it.deadlineDate.isAfter(date.plusDays(DEADLINE_DAYS)) }
            .sortedWith(compareBy({ it.deadlineDate ?: LocalDate.MAX }, { -it.priority.weight }, { it.id }))
        if (tasks.isEmpty()) return@buildString
        appendLine("Open work:")
        tasks.take(MAX_TASKS).forEach { task ->
            append("- id=").append(task.id).append(' ').append(task.title)
            append(" (").append(task.remainingMinutes).append(" min left")
            task.deadlineDate?.let { append(", due ").append(it) }
            appendLine(")")
        }
        appendLine()
    }

    private suspend fun coverage(date: LocalDate): String = buildString {
        val subjects = timetableRepository.subjects()
        if (subjects.isEmpty()) return@buildString
        val weekly = timetableRepository.weeklyEntries()
        val coverage = CoverageCalculator.compute(
            date = date,
            tracks = CoverageCalculator.tracksFrom(weekly),
            completions = scheduleRepository.completions(date.minusDays(30), date),
            skips = emptyList(),
            lastClassByTrack = CoverageCalculator.lastClassDates(date, weekly),
            exams = emptyList(),
        )
        appendLine("Subjects (code: name, days since studied):")
        subjects.forEach { subject ->
            val tracks = coverage.values.filter { it.track.subjectCode == subject.code }
            val gap = tracks.mapNotNull { it.daysSinceStudied }.minOrNull()
            append("- ").append(subject.code).append(": ").append(subject.name)
            append(", ").appendLine(gap?.let { "$it days" } ?: "not yet")
        }
        val today = timetableRepository.entriesFor(date).filter { it.kind.isTeaching }
        if (today.isNotEmpty()) {
            append("Classes today: ")
            appendLine(today.mapNotNull { e -> e.subjectCode?.let { "$it ${e.academicType?.key.orEmpty()}".trim() } }.distinct().joinToString(", "))
        }
        appendLine()
    }

    private suspend fun exams(date: LocalDate): String = buildString {
        val upcoming = examRepository.upcoming(date).take(MAX_EXAMS)
        if (upcoming.isEmpty()) {
            appendLine("No exams on record.")
            return@buildString
        }
        appendLine("Upcoming exams:")
        upcoming.forEach { exam ->
            append("- ").append(exam.subjectCode ?: exam.title).append(' ').append(exam.academicType.key)
            append(" on ").appendLine(exam.date)
        }
        appendLine()
    }

    private companion object {
        const val MAX_BLOCKS = 14
        const val MAX_TASKS = 10
        const val MAX_EXAMS = 8
        const val DEADLINE_DAYS = 14L
    }
}

/**
 * The system prompt. It defines the vocabulary and, importantly, the boundaries: the model
 * proposes structured commands and never decides where anything goes on the timeline.
 */
object Prompts {

    val SYSTEM = """
You convert what a user says into structured commands for Margin, a day planner.

You do NOT schedule anything. A deterministic engine decides where blocks go. Your only job
is to turn the message into commands and write one short, plain reply.

Answer with JSON only, no prose outside it, no code fences:
{"reply": "one or two calm sentences", "commands": [ ... ]}

Each command is an object with an "action" and only the fields that action needs:

create_task        title, minutes, deadline (YYYY-MM-DD or today/tomorrow/friday), subject,
                   category (academics|build|learning|personal|health|leisure|other),
                   priority (low|normal|high|critical), difficulty (easy|moderate|hard)
create_event       title, date, start ("HH:MM" 24h), end ("HH:MM"), category
update_task        task_id, plus any of title, minutes, deadline, priority, difficulty, category
complete_task      task_id
delete_task        task_id
move_block         block_id, start ("HH:MM"), date
skip_block         block_id, resolution (later_today|tomorrow|drop_today|remove), reason
take_break         minutes
set_energy         mode (light|normal|focused), date      -- "I'm tired" is light
go_out             until ("HH:MM", or "tomorrow" when not back today)
lighten_day        subjects (list of subject codes that must still happen), drop_build, drop_learning
minimum_day        when the user wants only the bare essentials
build_today        minutes                                   -- "one hour on my project"
no_build_today     learn_today  minutes     no_learning_today
skip_subject_today subject (code)                            -- "I don't want to study maths today"
extend_current     minutes                                   -- "another 30 minutes of this"
add_exam           subject, date, start, end, type (theory|lab)
missed_session     when the user says they missed a session
what_now           when the user asks what to do now
explain            block_id, when the user asks why something is scheduled
set_leisure_floor  minutes        set_build_target  minutes
replan             date
none               when the message needs no change

Rules:
- Use only ids and subject codes that appear in the context. Never invent one.
- Ambiguous clock times mean the sensible hour for a student: "out from 6 to 8" is the evening.
- For what_now and explain, leave "reply" empty; the app answers from the plan itself.
- If the user sounds tired or overloaded, prefer set_energy light or lighten_day over deleting work.
- Never suggest removing leisure or breaks to fit more work in.
- Keep "reply" under 40 words, factual, no exclamation marks, no emoji, no encouragement.
""".trim()
}
