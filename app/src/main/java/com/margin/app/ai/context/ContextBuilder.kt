package com.margin.app.ai.context

import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Builds the smallest useful description of the situation.
 *
 * Nothing here sends the database. Notes, history, faculty names and anything the model does
 * not need to answer the question stay on the device, and when the user turns off schedule
 * sharing the context shrinks to the clock and the free windows.
 */
class ContextBuilder(
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val timetableRepository: TimetableRepository,
) {

    suspend fun build(
        now: LocalDateTime,
        prefs: UserPreferences,
        includeScheduleDetail: Boolean,
    ): String {
        val today = now.toLocalDate()
        val nowMinute = MarginTime.nowMinute(now)
        val blocks = scheduleRepository.blocksFor(today)

        return buildString {
            appendLine("Date: $today (${today.dayOfWeek})")
            appendLine("Now: ${MarginTime.formatTime(nowMinute, true)}")
            appendLine(
                "Awake: ${MarginTime.formatTime(prefs.wakeMinute, true)} to " +
                    MarginTime.formatTime(prefs.sleepMinute, true),
            )
            appendLine(
                "Protected daily: ${prefs.minLeisureMinutes} min leisure, " +
                    "${prefs.buildMinutesWeekday} min build. " +
                    "Work ceiling ${prefs.maxWorkMinutesPerDay} min.",
            )

            if (includeScheduleDetail) {
                val remaining = blocks
                    .filter { it.end > nowMinute && it.type != BlockType.SLEEP }
                    .sortedBy { it.start }
                if (remaining.isNotEmpty()) {
                    appendLine()
                    appendLine("Rest of today:")
                    remaining.take(MAX_BLOCKS).forEach { block ->
                        append("- id=")
                        append(block.id)
                        append(" ")
                        append(MarginTime.formatTime(block.start, true))
                        append("-")
                        append(MarginTime.formatTime(block.end, true))
                        append(" ")
                        append(block.title)
                        append(" [")
                        append(block.type.key)
                        if (block.status != BlockStatus.PLANNED) {
                            append(", ")
                            append(block.status.name.lowercase())
                        }
                        appendLine("]")
                    }
                }
            } else {
                val free = blocks
                    .filter {
                        it.end > nowMinute &&
                            (it.type == BlockType.FREE || it.type == BlockType.LEISURE)
                    }
                    .sumOf { it.duration }
                appendLine("Free time left today: $free min")
            }

            val tasks = taskRepository.activeTasks()
                .filter { it.status == TaskStatus.ACTIVE && it.remainingMinutes > 0 }
                .sortedWith(
                    compareBy(
                        { it.deadlineDate ?: LocalDate.MAX },
                        { -it.priority.weight },
                        { it.id },
                    ),
                )
            if (tasks.isNotEmpty()) {
                appendLine()
                appendLine("Open work:")
                tasks.take(MAX_TASKS).forEach { task ->
                    append("- id=")
                    append(task.id)
                    append(" ")
                    append(task.title)
                    append(" (")
                    append(task.remainingMinutes)
                    append(" min left, ")
                    append(task.category.key)
                    task.deadlineDate?.let {
                        append(", due ")
                        append(it)
                    }
                    appendLine(")")
                }
            }

            val classes = timetableRepository.entriesFor(today).filter { it.kind.isTeaching }
            if (classes.isNotEmpty()) {
                appendLine()
                append("Classes today: ")
                appendLine(classes.mapNotNull { it.subjectCode }.distinct().joinToString(", "))
            }
        }.trim()
    }

    private companion object {
        const val MAX_BLOCKS = 16
        const val MAX_TASKS = 12
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

create_task    title, minutes, deadline (YYYY-MM-DD or today/tomorrow/friday), category
               (academics|build|learning|personal|health|leisure|other), priority
               (low|normal|high|critical), difficulty (easy|moderate|hard), splittable (bool)
create_event   title, date, start ("HH:MM" 24h), end ("HH:MM"), category
update_task    task_id, plus any of title, minutes, deadline, priority, difficulty, category
complete_task  task_id
delete_task    task_id
move_block     block_id, start ("HH:MM"), date
skip_block     block_id, resolution (later_today|tomorrow|drop_today|remove), reason
take_break     minutes
set_energy     mode (light|normal|focused), date
set_leisure_floor  minutes
set_build_target   minutes
replan         date
none           when the message needs no change

Rules:
- Use only ids that appear in the context. Never invent one.
- Ambiguous clock times mean the sensible hour for a student: "out from 6 to 8" is the evening.
- If the user is only asking a question, answer it in "reply" and use action "none".
- If the user sounds tired or overloaded, prefer set_energy light over deleting their work.
- Never suggest removing leisure or breaks to fit more work in.
- Keep "reply" under 40 words, factual, no exclamation marks, no emoji, no encouragement.
""".trim()
}
