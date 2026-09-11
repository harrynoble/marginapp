package com.margin.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Priority
import com.margin.app.domain.model.Project
import com.margin.app.domain.model.Task
import com.margin.app.domain.model.TaskStatus
import com.margin.app.ui.RegisterAddAction
import com.margin.app.ui.components.ColorDot
import com.margin.app.ui.components.EmptyState
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.Haptics
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.OptionChips
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SectionTitle
import com.margin.app.ui.glass.GlassTextButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor
import java.time.LocalDate

/**
 * Reminders-style: grouped by when things are due, a ring you tap to complete, priority in
 * exclamation marks. Tasks here are inputs; Margin decides when they happen.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backdrop = rememberGlassBackdrop()
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var creatingProject by remember { mutableStateOf(false) }

    RegisterAddAction { creating = true }

    LargeTitleScreen(
        title = "Tasks",
        backdrop = backdrop,
        modifier = modifier,
        actions = {
            GlassTextButton(
                backdrop = backdrop,
                text = if (state.showDone) "Hide Done" else "Show Done",
                onClick = viewModel::toggleDone,
            )
        },
    ) {
        item(key = "filters") {
            OptionChips(
                options = listOf<Category?>(null) + Category.entries,
                selected = state.filter,
                label = { it?.label ?: "All" },
                onSelect = viewModel::setFilter,
                leading = { category ->
                    if (category == null) null else ({ ColorDot(color = accentFor(category), size = 8.dp) })
                },
                modifier = Modifier.padding(horizontal = Space.xs),
            )
        }

        if (state.isEmpty && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.TaskAlt,
                    title = "No tasks",
                    body = "Tap the plus button to add one. Margin finds the time for it.",
                )
            }
        }

        state.groups.forEach { (group, tasks) ->
            item(key = "title-${group.name}") {
                SectionTitle(text = group.label)
            }
            item(key = "list-${group.name}") {
                GroupedSection {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) RowSeparator(inset = 56.dp)
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
            }
        }

        item(key = "projects-title") {
            SectionTitle(text = "Projects", trailing = "New", onTrailing = { creatingProject = true })
        }
        item(key = "projects") {
            GroupedSection {
                if (state.projects.isEmpty()) {
                    GroupedRow(
                        title = "New Project",
                        subtitle = "Group build and learning work so it keeps getting time",
                        titleColor = MarginTheme.colors.tint,
                        onClick = { creatingProject = true },
                    )
                } else {
                    state.projects.forEachIndexed { index, project ->
                        if (index > 0) RowSeparator(inset = 58.dp)
                        GroupedRow(
                            title = project.name,
                            subtitle = if (project.targetMinutesPerWeek > 0) {
                                MarginTime.formatDuration(project.targetMinutesPerWeek) + " a week"
                            } else {
                                project.category.label
                            },
                            leading = { IconTile(Icons.Rounded.Folder, accentFor(project.category)) },
                            showChevron = true,
                            onClick = { editingProject = project },
                        )
                    }
                }
            }
        }
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
        val links by remember(task.id) { viewModel.observeLinks(task.id) }
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
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val colors = MarginTheme.colors
    val done = task.status == TaskStatus.DONE
    val accent = accentFor(task.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(start = Space.m, end = Space.l, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CompletionRing(checked = done, color = accent, onToggle = onToggle)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = AppleType.body,
                color = if (done) colors.secondaryLabel else colors.label,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta(task, colors.destructive, colors.warning),
                style = AppleType.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val marks = when (task.priority) {
            Priority.CRITICAL -> "!!!"
            Priority.HIGH -> "!!"
            else -> null
        }
        if (marks != null && !done) {
            Spacer(Modifier.width(Space.s))
            Text(text = marks, style = AppleType.headline, color = colors.warning)
        }
    }
}

/** The Reminders ring: an empty circle that fills with the category colour and a tick. */
@Composable
private fun CompletionRing(checked: Boolean, color: Color, onToggle: () -> Unit) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox) {
                if (!checked) Haptics.confirm(view) else Haptics.light(view)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .border(width = 1.7.dp, color = colors.tertiaryLabel, shape = CircleShape),
            )
        }
    }
}

private fun meta(task: Task, overdue: Color, soon: Color) = buildAnnotatedString {
    append(task.category.label)
    task.subjectCode?.let { append(" · $it") }
    if (task.isRecurring) {
        append(" · repeats")
    } else {
        append(" · " + MarginTime.formatDuration(task.remainingMinutes) + " left")
    }
    task.deadlineDate?.let { deadline ->
        val today = LocalDate.now()
        append(" · ")
        when {
            deadline.isBefore(today) -> withStyle(SpanStyle(color = overdue)) { append("Overdue") }
            deadline == today -> withStyle(SpanStyle(color = soon)) { append("Due today") }
            deadline == today.plusDays(1) -> withStyle(SpanStyle(color = soon)) { append("Due tomorrow") }
            else -> append("Due " + MarginTime.dayLabel(deadline, today))
        }
    }
}
