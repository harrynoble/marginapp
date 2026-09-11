package com.margin.app.data.repository

import com.margin.app.data.db.dao.EventDao
import com.margin.app.data.db.dao.ProjectDao
import com.margin.app.data.db.dao.ResourceLinkDao
import com.margin.app.data.db.dao.TaskDao
import com.margin.app.data.db.dao.TaskSessionDao
import com.margin.app.data.db.entity.ResourceLinkEntity
import com.margin.app.data.db.entity.TaskSessionEntity
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.ResourceLink
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TaskRepository(
    private val taskDao: TaskDao,
    private val sessionDao: TaskSessionDao,
    private val projectDao: ProjectDao,
    private val eventDao: EventDao,
    private val linkDao: ResourceLinkDao,
) {

    fun observeTasks(): Flow<List<Task>> =
        taskDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    suspend fun task(id: Long): Task? = taskDao.byId(id)?.toDomain()

    suspend fun activeTasks(): List<Task> = taskDao.activeTasks().map { it.toDomain() }

    suspend fun allTasks(): List<Task> = taskDao.all().map { it.toDomain() }

    suspend fun allEvents(): List<CalendarEvent> = eventDao.all().map { it.toDomain() }

    suspend fun create(task: Task): Long {
        val now = System.currentTimeMillis()
        return taskDao.insert(task.copy(createdAt = if (task.createdAt == 0L) now else task.createdAt).toEntity())
    }

    suspend fun update(task: Task) = taskDao.update(task.toEntity())

    /**
     * Credits time to a task. Reaching the estimate is deliberately not the same as being
     * finished: the estimate was a guess, and only the user decides when the work is done.
     */
    suspend fun addProgress(taskId: Long, minutes: Int) {
        if (minutes <= 0) return
        taskDao.addProgress(taskId, minutes)
    }

    suspend fun markDone(taskId: Long) =
        taskDao.setStatus(taskId, TaskStatus.DONE.name, System.currentTimeMillis())

    suspend fun reopen(taskId: Long) = taskDao.setStatus(taskId, TaskStatus.ACTIVE.name, null)

    suspend fun archive(taskId: Long) =
        taskDao.setStatus(taskId, TaskStatus.ARCHIVED.name, System.currentTimeMillis())

    suspend fun delete(taskId: Long) = taskDao.delete(taskId)

    suspend fun recordSession(taskId: Long, date: LocalDate, planned: Int, completed: Int) {
        sessionDao.insert(
            TaskSessionEntity(
                taskId = taskId,
                date = date.toEpochDay(),
                plannedMinutes = planned,
                completedMinutes = completed,
                startedAt = System.currentTimeMillis(),
                endedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ---- Projects ----------------------------------------------------------------------

    fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun projects(): List<Project> = projectDao.allActive().map { it.toDomain() }

    suspend fun project(id: Long?): Project? = id?.let { projectDao.byId(it)?.toDomain() }

    suspend fun upsertProject(project: Project): Long = projectDao.upsert(project.toEntity())

    suspend fun deleteProject(id: Long) = projectDao.delete(id)

    // ---- Events ------------------------------------------------------------------------

    suspend fun eventsOn(date: LocalDate): List<CalendarEvent> =
        eventDao.forDate(date.toEpochDay()).map { it.toDomain() }

    fun observeEvents(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        eventDao.observeRange(from.toEpochDay(), to.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    suspend fun event(id: Long): CalendarEvent? = eventDao.byId(id)?.toDomain()

    suspend fun createEvent(event: CalendarEvent): Long = eventDao.insert(event.toEntity())

    suspend fun updateEvent(event: CalendarEvent) = eventDao.update(event.toEntity())

    suspend fun deleteEvent(id: Long) = eventDao.delete(id)

    // ---- Links -------------------------------------------------------------------------

    fun observeLinks(taskId: Long): Flow<List<ResourceLink>> =
        linkDao.observeForTask(taskId).map { list -> list.map { it.toDomain() } }

    suspend fun addLink(taskId: Long, label: String, url: String): Long =
        linkDao.insert(ResourceLinkEntity(taskId = taskId, label = label, url = url))

    suspend fun deleteLink(id: Long) = linkDao.delete(id)
}
