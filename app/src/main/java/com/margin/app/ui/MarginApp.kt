package com.margin.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.CalendarEvent
import com.margin.app.domain.model.DailyCheckIn
import com.margin.app.ui.assistant.AddSheet
import com.margin.app.ui.assistant.AssistantViewModel
import com.margin.app.ui.focus.FocusScreen
import com.margin.app.ui.focus.FocusViewModel
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.GlassDefaults
import com.margin.app.ui.glass.GlassTabBar
import com.margin.app.ui.glass.glassSource
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.insights.InsightsScreen
import com.margin.app.ui.insights.InsightsViewModel
import com.margin.app.ui.navigation.Routes
import com.margin.app.ui.navigation.TopLevelTabs
import com.margin.app.ui.onboarding.OnboardingScreen
import com.margin.app.ui.plan.AddEventSheet
import com.margin.app.ui.plan.PlanScreen
import com.margin.app.ui.plan.PlanViewModel
import com.margin.app.ui.settings.SettingsScreen
import com.margin.app.ui.settings.SettingsViewModel
import com.margin.app.ui.tasks.TaskEditorSheet
import com.margin.app.ui.tasks.TasksScreen
import com.margin.app.ui.tasks.TasksViewModel
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.timetable.TimetableScreen
import com.margin.app.ui.timetable.TimetableViewModel
import com.margin.app.ui.today.CheckInSheet
import com.margin.app.ui.today.TodayScreen
import com.margin.app.ui.today.TodayViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Screens pushed on top of the tabs, which slide in from the edge and hide the tab bar. */
private val PushedRoutes = setOf(Routes.SETTINGS, Routes.TIMETABLE, Routes.FOCUS)

private fun NavBackStackEntry.isPushed() = destination.route in PushedRoutes

/** Close to the curve UIKit uses for navigation pushes: quick off the mark, long settle. */
private val PushEasing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
private val PushSpec = tween<IntOffset>(durationMillis = 420, easing = PushEasing)

@Composable
fun MarginApp(container: AppContainer) {
    val colors = MarginTheme.colors
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    val settingsViewModel = marginViewModel("settings") { SettingsViewModel.create(container) }
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val use24Hour = settingsState.prefs.use24HourTime

    var onboarded by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(settingsState.loading, settingsState.prefs.onboardingComplete) {
        if (!settingsState.loading) onboarded = settingsState.prefs.onboardingComplete
    }

    val addAction = remember { AddActionHolder() }
    val appBackdrop = rememberGlassBackdrop()

    var showAdd by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf(false) }
    var newEvent by remember { mutableStateOf(false) }
    var showCheckIn by remember { mutableStateOf(false) }

    when (onboarded) {
        null -> Box(Modifier.fillMaxSize().background(colors.groupedBackground))

        false -> OnboardingScreen(viewModel = settingsViewModel, onFinish = { onboarded = true })

        true -> CompositionLocalProvider(LocalAddAction provides addAction) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.groupedBackground),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.TODAY,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(appBackdrop),
                    enterTransition = {
                        if (targetState.isPushed()) {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, PushSpec)
                        } else {
                            fadeIn(tween(180))
                        }
                    },
                    exitTransition = {
                        if (targetState.isPushed()) {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                PushSpec,
                                targetOffset = { it / 4 },
                            )
                        } else {
                            fadeOut(tween(120))
                        }
                    },
                    popEnterTransition = {
                        if (initialState.isPushed()) {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                PushSpec,
                                initialOffset = { it / 4 },
                            )
                        } else {
                            fadeIn(tween(180))
                        }
                    },
                    popExitTransition = {
                        if (initialState.isPushed()) {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, PushSpec)
                        } else {
                            fadeOut(tween(120))
                        }
                    },
                ) {
                    composable(Routes.TODAY) {
                        val viewModel = marginViewModel("today") { TodayViewModel.create(container) }
                        TodayScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenFocus = { navController.navigate(Routes.focus(it)) },
                            onOpenTask = { navigateToTab(navController, Routes.TASKS) },
                            onOpenCheckIn = { showCheckIn = true },
                        )
                    }
                    composable(Routes.PLAN) {
                        val viewModel = marginViewModel("plan") { PlanViewModel.create(container) }
                        PlanScreen(
                            viewModel = viewModel,
                            onOpenTask = { navigateToTab(navController, Routes.TASKS) },
                        )
                    }
                    composable(Routes.TASKS) {
                        val viewModel = marginViewModel("tasks") { TasksViewModel.create(container) }
                        TasksScreen(viewModel = viewModel)
                    }
                    composable(Routes.INSIGHTS) {
                        val viewModel = marginViewModel("insights") { InsightsViewModel.create(container) }
                        InsightsScreen(viewModel = viewModel)
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenTimetable = { navController.navigate(Routes.TIMETABLE) },
                        )
                    }
                    composable(Routes.TIMETABLE) {
                        val viewModel = marginViewModel("timetable") { TimetableViewModel.create(container) }
                        TimetableScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Routes.FOCUS,
                        arguments = listOf(navArgument("blockId") { type = NavType.LongType }),
                    ) { entry ->
                        val blockId = entry.arguments?.getLong("blockId") ?: 0L
                        val viewModel = marginViewModel("focus-$blockId") {
                            FocusViewModel.create(container, blockId)
                        }
                        FocusScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
                    }
                }

                AnimatedVisibility(
                    visible = Routes.isTab(route),
                    enter = fadeIn(tween(220)) + slideInVertically(tween(320, easing = PushEasing)) { it / 2 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(260)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = Space.gutter, end = Space.gutter, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassTabBar(
                            backdrop = appBackdrop,
                            tabs = TopLevelTabs,
                            selectedIndex = Routes.tabs.indexOf(route ?: Routes.TODAY).coerceAtLeast(0),
                            onSelect = { index -> navigateToTab(navController, Routes.tabs[index]) },
                            modifier = Modifier.weight(1f),
                        )
                        GlassCircleButton(
                            backdrop = appBackdrop,
                            icon = Icons.Rounded.Add,
                            contentDescription = "Add",
                            onClick = { addAction.action?.invoke() ?: run { showAdd = true } },
                            size = Space.tabBarHeight,
                            iconSize = 28.dp,
                            style = GlassDefaults.prominent(colors.tint),
                            iconTint = Color.White,
                        )
                    }
                }
            }

            if (showAdd) {
                val assistant = marginViewModel("assistant") { AssistantViewModel.create(container) }
                AddSheet(
                    viewModel = assistant,
                    onDismiss = { showAdd = false },
                    onNewTask = {
                        showAdd = false
                        newTask = true
                    },
                    onNewEvent = {
                        showAdd = false
                        newEvent = true
                    },
                    onBreak = { minutes ->
                        showAdd = false
                        scope.launch { container.scheduleActions.takeBreak(minutes) }
                    },
                )
            }

            if (newTask) {
                val projectsFlow = remember { container.taskRepository.observeProjects() }
                val projects by projectsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                TaskEditorSheet(
                    task = null,
                    projects = projects,
                    use24Hour = use24Hour,
                    onDismiss = { newTask = false },
                    onSave = { task ->
                        newTask = false
                        scope.launch {
                            container.taskRepository.create(task)
                            container.planningService.replan(LocalDate.now())
                        }
                    },
                    onDelete = null,
                )
            }

            if (newEvent) {
                AddEventSheet(
                    date = LocalDate.now(),
                    use24Hour = use24Hour,
                    onDismiss = { newEvent = false },
                    onSave = { title, date, start, end, category, notes ->
                        newEvent = false
                        scope.launch {
                            container.taskRepository.createEvent(
                                CalendarEvent(
                                    title = title,
                                    date = date,
                                    start = start,
                                    end = end,
                                    category = category,
                                    notes = notes,
                                ),
                            )
                            container.planningService.replan(date)
                        }
                    },
                )
            }

            if (showCheckIn) {
                val dayFlow = remember { container.scheduleRepository.observeDay(LocalDate.now()) }
                val blocks by dayFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                CheckInSheet(
                    blocks = blocks,
                    onDismiss = { showCheckIn = false },
                    onSave = { energy, note, carryForward ->
                        showCheckIn = false
                        scope.launch {
                            container.scheduleRepository.saveCheckIn(
                                DailyCheckIn(
                                    date = LocalDate.now(),
                                    energy = energy,
                                    note = note,
                                    carryForward = carryForward,
                                    completedAt = System.currentTimeMillis(),
                                ),
                            )
                            if (carryForward) container.planningService.replan(LocalDate.now().plusDays(1))
                        }
                    },
                )
            }
        }
    }
}

private fun navigateToTab(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
