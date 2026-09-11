package com.margin.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import com.margin.app.ui.glass.GlassTab

object Routes {
    const val TODAY = "today"
    const val PLAN = "plan"
    const val TASKS = "tasks"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val TIMETABLE = "timetable"
    const val EXAMS = "exams"
    const val FOCUS = "focus/{blockId}"

    fun focus(blockId: Long) = "focus/$blockId"

    /** Tabs live in the floating tab bar; everything else is pushed on top of them. */
    val tabs = listOf(TODAY, PLAN, TASKS, INSIGHTS)

    fun isTab(route: String?): Boolean = route == null || route in tabs
}

/**
 * Four tabs. The timetable is no longer one of them: it is set up once, lives under Settings,
 * and otherwise stays out of the way, folded into a single "College" line on the timeline.
 */
val TopLevelTabs = listOf(
    GlassTab("Today", Icons.Outlined.Today, Icons.Rounded.Today),
    GlassTab("Plan", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    GlassTab("Tasks", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    GlassTab("Insights", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
)
