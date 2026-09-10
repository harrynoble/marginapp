package com.margin.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val TODAY = "today"
    const val PLAN = "plan"
    const val TASKS = "tasks"
    const val TIMETABLE = "timetable"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val FOCUS = "focus/{blockId}"
    const val TASK_DETAIL = "task/{taskId}"

    fun focus(blockId: Long) = "focus/$blockId"
    fun taskDetail(taskId: Long) = "task/$taskId"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Today(Routes.TODAY, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    Plan(Routes.PLAN, "Plan", Icons.Filled.ViewAgenda, Icons.Outlined.ViewAgenda),
    Tasks(Routes.TASKS, "Tasks", Icons.Outlined.Checklist, Icons.Outlined.Checklist),
    Week(Routes.TIMETABLE, "Week", Icons.Filled.CalendarViewWeek, Icons.Outlined.CalendarViewWeek),
    Insights(Routes.INSIGHTS, "Insights", Icons.Filled.Insights, Icons.Outlined.Insights),
}
