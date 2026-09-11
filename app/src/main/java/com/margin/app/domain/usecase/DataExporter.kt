package com.margin.app.domain.usecase

import android.content.Context
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.GoalRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Writes everything the user owns to one JSON file they can keep or share. Settings and the
 * assistant key are deliberately not included: the export is the user's data, not secrets.
 */
class DataExporter(
    private val context: Context,
    private val timetableRepository: TimetableRepository,
    private val taskRepository: TaskRepository,
    private val scheduleRepository: ScheduleRepository,
    private val goalRepository: GoalRepository,
    private val examRepository: ExamRepository,
) {

    suspend fun export(today: LocalDate = LocalDate.now()): File = withContext(Dispatchers.IO) {
        val from = today.minusDays(HISTORY_DAYS)
        val subjects = timetableRepository.allSubjects()
        val entries = timetableRepository.weeklyEntries()
        val routines = timetableRepository.allRoutines()
        val tasks = taskRepository.allTasks()
        val projects = taskRepository.projects()
        val goals = goalRepository.activeGoals()
        val exams = examRepository.upcoming(from)
        val events = taskRepository.allEvents()
        val completions = scheduleRepository.completions(from, today)
        val skips = scheduleRepository.skips(from, today)
        val breaks = scheduleRepository.breaks(from, today)
        val checkIns = scheduleRepository.allCheckIns()

        val document = buildJsonObject {
            put("app", "Margin")
            put("format", 2)
            put("exportedAt", Instant.now().toString())
            putJsonArray("subjects") {
                subjects.forEach { s ->
                    obj {
                        put("code", s.code); put("name", s.name); put("shortName", s.shortName)
                        put("reviewWeight", s.reviewWeight); put("importance", s.importance)
                        put("difficulty", s.difficulty.key)
                    }
                }
            }
            putJsonArray("timetable") {
                entries.forEach { e ->
                    obj {
                        put("day", e.dayOfWeek.name); put("start", e.start); put("end", e.end)
                        put("subject", e.subjectCode); put("title", e.title); put("kind", e.kind.key)
                        put("type", e.academicType?.key); put("faculty", e.faculty); put("location", e.location)
                    }
                }
            }
            putJsonArray("routines") {
                routines.forEach { r ->
                    obj {
                        put("title", r.title); put("kind", r.kind.key); put("days", r.daysMask)
                        put("start", r.start); put("end", r.end); put("active", r.active)
                    }
                }
            }
            putJsonArray("tasks") {
                tasks.forEach { t ->
                    obj {
                        put("title", t.title); put("category", t.category.key); put("subject", t.subjectCode)
                        put("estimatedMinutes", t.estimatedMinutes); put("completedMinutes", t.completedMinutes)
                        put("deadline", t.deadlineDate?.toString()); put("priority", t.priority.key)
                        put("status", t.status.name); put("notes", t.notes)
                    }
                }
            }
            putJsonArray("projects") {
                projects.forEach { p -> obj { put("name", p.name); put("weeklyTarget", p.targetMinutesPerWeek) } }
            }
            putJsonArray("learningGoals") {
                goals.forEach { g -> obj { put("name", g.name); put("weeklyTarget", g.weeklyTargetMinutes); put("session", g.sessionMinutes) } }
            }
            putJsonArray("exams") {
                exams.forEach { x ->
                    obj {
                        put("subject", x.subjectCode); put("title", x.title); put("date", x.date.toString())
                        put("start", x.startMinute); put("type", x.academicType.key)
                    }
                }
            }
            putJsonArray("events") {
                events.forEach { ev ->
                    obj { put("title", ev.title); put("date", ev.date.toString()); put("start", ev.start); put("end", ev.end) }
                }
            }
            putJsonArray("sessions") {
                completions.forEach { c ->
                    obj {
                        put("date", c.date.toString()); put("title", c.title); put("category", c.category.key)
                        put("type", c.type.key); put("subject", c.subjectCode); put("academicType", c.academicType?.key)
                        put("plannedMinutes", c.plannedMinutes); put("actualMinutes", c.minutes)
                        put("startMinute", c.startMinute); put("extendedMinutes", c.extendedMinutes)
                    }
                }
            }
            putJsonArray("skipped") {
                skips.forEach { s ->
                    obj {
                        put("date", s.date.toString()); put("title", s.title); put("kind", s.kind.key)
                        put("resolution", s.resolution.name); put("reason", s.reason); put("subject", s.subjectCode)
                    }
                }
            }
            putJsonArray("breaks") {
                breaks.forEach { b ->
                    obj { put("date", b.date.toString()); put("start", b.startMinute); put("minutes", b.actualMinutes); put("reason", b.reason.key) }
                }
            }
            putJsonArray("checkIns") {
                checkIns.forEach { c ->
                    obj {
                        put("date", c.date.toString()); put("workload", c.workload); put("note", c.note)
                        put("carryForward", c.carryForward)
                    }
                }
            }
        }

        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        File(folder, "margin-export-$today.json").apply { writeText(document.toString()) }
    }

    private fun JsonArrayBuilder.obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        add(buildJsonObject(block))
    }

    private companion object {
        const val HISTORY_DAYS = 365L
    }
}
