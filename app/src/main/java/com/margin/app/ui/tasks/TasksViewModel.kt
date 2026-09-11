package com.margin.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.GoalRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.LearningGoal
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import com.margin.app.domain.usecase.PlanningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TaskGroup(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Due today"),
    SOON("Next 7 days"),
    LATER("Later"),
    UNDATED("No deadline"),
    DONE("Finished"),
}

data class TasksUiState(
    val groups: Map<TaskGroup, List<Task>> = emptyMap(),
    val projects: List<Project> = emptyList(),
    val goals: List<LearningGoal> = emptyList(),
    val filter: Category? = null,
    val showDone: Boolean = false,
    val use24Hour: Boolean = false,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = groups.values.all { it.isEmpty() }
}

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
) : ViewModel() {

    private val filter = MutableStateFlow<Category?>(null)
    private val showDone = MutableStateFlow(false)

    val state: StateFlow<TasksUiState> = combine(
        taskRepository.observeAllTasks(),
        combine(taskRepository.observeProjects(), goalRepository.observeGoals()) { p, g -> p to g },
        preferencesRepository.preferences,
        filter,
        showDone,
    ) { tasks, (projects, goals), prefs, category, done ->
        val today = LocalDate.now()
        val relevant = tasks
            .filter { it.status != TaskStatus.ARCHIVED }
            .filter { category == null || it.category == category }

        val grouped = linkedMapOf<TaskGroup, List<Task>>()
        val active = relevant.filter { it.status == TaskStatus.ACTIVE }

        grouped[TaskGroup.OVERDUE] = active.filter {
            it.deadlineDate != null && it.deadlineDate.isBefore(today)
        }.sortedBy { it.deadlineDate }
        grouped[TaskGroup.TODAY] = active.filter { it.deadlineDate == today }
            .sortedByDescending { it.priority.weight }
        grouped[TaskGroup.SOON] = active.filter {
            it.deadlineDate != null &&
                it.deadlineDate.isAfter(today) &&
                !it.deadlineDate.isAfter(today.plusDays(7))
        }.sortedBy { it.deadlineDate }
        grouped[TaskGroup.LATER] = active.filter {
            it.deadlineDate != null && it.deadlineDate.isAfter(today.plusDays(7))
        }.sortedBy { it.deadlineDate }
        grouped[TaskGroup.UNDATED] = active.filter { it.deadlineDate == null }
            .sortedByDescending { it.priority.weight }
        if (done) {
            grouped[TaskGroup.DONE] = relevant.filter { it.status == TaskStatus.DONE }
                .sortedByDescending { it.completedAt ?: 0 }
                .take(40)
        }

        TasksUiState(
            groups = grouped.filterValues { it.isNotEmpty() },
            projects = projects,
            goals = goals,
            filter = category,
            showDone = done,
            use24Hour = prefs.use24HourTime,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(category: Category?) {
        filter.value = category
    }

    fun toggleDone() {
        showDone.value = !showDone.value
    }

    fun save(task: Task) = viewModelScope.launch {
        if (task.id == 0L) taskRepository.create(task) else taskRepository.update(task)
        planningService.replan(LocalDate.now())
    }

    fun markDone(taskId: Long) = viewModelScope.launch {
        taskRepository.markDone(taskId)
        planningService.replan(LocalDate.now())
    }

    fun reopen(taskId: Long) = viewModelScope.launch {
        taskRepository.reopen(taskId)
        planningService.replan(LocalDate.now())
    }

    fun delete(taskId: Long) = viewModelScope.launch {
        taskRepository.delete(taskId)
        planningService.replan(LocalDate.now())
    }

    fun pinToToday(taskId: Long) = viewModelScope.launch {
        taskRepository.task(taskId)?.let {
            taskRepository.update(it.copy(pinnedDate = LocalDate.now()))
        }
        planningService.replan(LocalDate.now())
    }

    fun saveProject(project: Project) = viewModelScope.launch {
        taskRepository.upsertProject(project)
    }

    fun deleteProject(projectId: Long) = viewModelScope.launch {
        taskRepository.deleteProject(projectId)
        planningService.replan(LocalDate.now())
    }

    fun saveGoal(goal: LearningGoal) = viewModelScope.launch {
        goalRepository.upsert(goal)
        planningService.replan(LocalDate.now())
    }

    fun deleteGoal(goalId: Long) = viewModelScope.launch {
        goalRepository.delete(goalId)
        planningService.replan(LocalDate.now())
    }

    fun observeLinks(taskId: Long) = taskRepository.observeLinks(taskId)

    fun addLink(taskId: Long, label: String, url: String) = viewModelScope.launch {
        if (label.isBlank() && url.isBlank()) return@launch
        taskRepository.addLink(taskId, label.ifBlank { url }, url)
    }

    fun deleteLink(linkId: Long) = viewModelScope.launch { taskRepository.deleteLink(linkId) }

    companion object {
        fun create(container: AppContainer) = TasksViewModel(
            taskRepository = container.taskRepository,
            goalRepository = container.goalRepository,
            preferencesRepository = container.preferencesRepository,
            planningService = container.planningService,
        )
    }
}
