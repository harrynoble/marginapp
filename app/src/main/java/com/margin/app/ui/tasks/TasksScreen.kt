package com.margin.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import com.margin.app.ui.components.CategoryDot
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.SectionHeader
import com.margin.app.ui.theme.Space
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var creatingProject by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tasks", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(onClick = { viewModel.toggleDone() }) {
                        Text(
                            text = if (state.showDone) "Hide done" else "Show done",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New task")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                bottom = Space.xxxl * 2,
            ),
        ) {
            item(key = "filters") {
                FlowRow(
                    modifier = Modifier.padding(bottom = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    FilterChip(
                        selected = state.filter == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("All") },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    Category.entries.forEach { category ->
                        FilterChip(
                            selected = state.filter == category,
                            onClick = { viewModel.setFilter(category) },
                            label = { Text(category.label) },
                            leadingIcon = { CategoryDot(category) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }

            if (state.isEmpty && !state.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = "No tasks yet",
                        body = "Add what needs doing and Margin will find the time for it.",
                    )
                }
            }

            state.groups.forEach { (group, tasks) ->
                item(key = "header-${group.name}") {
                    Spacer(Modifier.height(Space.s))
                    SectionHeader(group.label)
                    Spacer(Modifier.height(Space.xs))
                }
                items(tasks, key = { "task-${it.id}" }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = {
                            if (task.status == TaskStatus.DONE) viewModel.reopen(task.id)
                            else viewModel.markDone(task.id)
                        },
                        onClick = { editing = task },
                    )
                }
            }

            item(key = "projects-header") {
                Spacer(Modifier.height(Space.xl))
                SectionHeader(
                    text = "Projects",
                    trailing = {
                        TextButton(onClick = { creatingProject = true }) {
                            Text("New", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                )
                Spacer(Modifier.height(Space.xs))
                if (state.projects.isEmpty()) {
                    Text(
                        text = "Group build and learning work under a project to keep it visible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.projects, key = { "project-${it.id}" }) { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingProject = project }
                        .padding(vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryDot(project.category)
                    Spacer(Modifier.width(Space.m))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (project.targetMinutesPerWeek > 0) {
                                MarginTime.formatDuration(project.targetMinutesPerWeek) + " a week"
                            } else {
                                project.category.label
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creatingProject) {
        ProjectSheet(
            project = null,
            onDismiss = { creatingProject = false },
            onSave = {
                viewModel.saveProject(it)
                creatingProject = false
            },
        )
    }

    editingProject?.let { project ->
        ProjectSheet(
            project = project,
            onDismiss = { editingProject = null },
            onSave = {
                viewModel.saveProject(it)
                editingProject = null
            },
            onDelete = {
                viewModel.deleteProject(project.id)
                editingProject = null
            },
        )
    }

    if (creating) {
        TaskEditorSheet(
            task = null,
            projects = state.projects,
            use24Hour = state.use24Hour,
            onDismiss = { creating = false },
            onSave = {
                viewModel.save(it)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { task ->
        val links by viewModel.observeLinks(task.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        TaskEditorSheet(
            task = task,
            projects = state.projects,
            use24Hour = state.use24Hour,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(task.id)
                editing = null
            },
            links = links,
            onAddLink = { label, url -> viewModel.addLink(task.id, label, url) },
            onDeleteLink = { viewModel.deleteLink(it) },
        )
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val done = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (done) "Reopen" else "Mark done",
                tint = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(task.category)
                Spacer(Modifier.width(Space.s))
                Text(
                    text = buildMeta(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun buildMeta(task: Task): String {
    val parts = mutableListOf<String>()
    parts += task.category.label
    task.subjectCode?.let { parts += it }
    parts += MarginTime.formatDuration(task.remainingMinutes) + " left"
    task.deadlineDate?.let { deadline ->
        val today = LocalDate.now()
        parts += when {
            deadline.isBefore(today) -> "overdue"
            deadline == today -> "due today"
            deadline == today.plusDays(1) -> "due tomorrow"
            else -> "due " + MarginTime.dayLabel(deadline, today)
        }
    }
    return parts.joinToString(" · ")
}
