package com.margin.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.margin.app.data.seed.TimetableSeed
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.DailyCheckIn
import com.margin.app.ui.assistant.AssistantSheet
import com.margin.app.ui.assistant.AssistantViewModel
import com.margin.app.ui.focus.FocusScreen
import com.margin.app.ui.focus.FocusViewModel
import com.margin.app.ui.insights.InsightsScreen
import com.margin.app.ui.insights.InsightsViewModel
import com.margin.app.ui.navigation.Routes
import com.margin.app.ui.navigation.TopLevelDestination
import com.margin.app.ui.onboarding.OnboardingScreen
import com.margin.app.ui.plan.PlanScreen
import com.margin.app.ui.plan.PlanViewModel
import com.margin.app.ui.settings.SettingsScreen
import com.margin.app.ui.settings.SettingsViewModel
import com.margin.app.ui.tasks.TasksScreen
import com.margin.app.ui.tasks.TasksViewModel
import com.margin.app.ui.timetable.TimetableScreen
import com.margin.app.ui.timetable.TimetableViewModel
import com.margin.app.ui.today.CheckInSheet
import com.margin.app.ui.today.TodayScreen
import com.margin.app.ui.today.TodayViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MarginApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val settingsViewModel = marginViewModel("settings") { SettingsViewModel.create(container) }
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    var showAssistant by remember { mutableStateOf(false) }
    var showCheckIn by remember { mutableStateOf(false) }
    var onboarded by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(settingsState.loading, settingsState.prefs.onboardingComplete) {
        if (!settingsState.loading) onboarded = settingsState.prefs.onboardingComplete
    }

    when (onboarded) {
        null -> Surface(modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }

        false -> OnboardingScreen(
            viewModel = settingsViewModel,
            timetableSummary = timetableSummary(),
            onFinish = { onboarded = true },
        )

        true -> {
            val showBottomBar = currentRoute.isTopLevel()

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBottomBar) {
                        Box {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.background,
                                tonalElevation = 0.dp,
                            ) {
                                TopLevelDestination.entries.forEach { destination ->
                                    val selected = currentRoute?.hierarchy
                                        ?.any { it.route == destination.route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) {
                                                    destination.selectedIcon
                                                } else {
                                                    destination.icon
                                                },
                                                contentDescription = destination.label,
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = destination.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.TODAY,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                ) {
                    composable(Routes.TODAY) {
                        val viewModel = marginViewModel("today") { TodayViewModel.create(container) }
                        val todayState by viewModel.state.collectAsStateWithLifecycle()
                        TodayScreen(
                            viewModel = viewModel,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenAssistant = { showAssistant = true },
                            onOpenFocus = { navController.navigate(Routes.focus(it)) },
                            onOpenTask = { navController.navigate(Routes.TASKS) },
                            onOpenCheckIn = { showCheckIn = true },
                        )

                        if (showCheckIn) {
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            CheckInSheet(
                                blocks = todayState.earlier + todayState.upcoming +
                                    listOfNotNull(todayState.current),
                                onDismiss = { showCheckIn = false },
                                onSave = { energy, note, carryForward ->
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
                                        if (carryForward) {
                                            container.planningService.replan(LocalDate.now().plusDays(1))
                                        }
                                    }
                                    showCheckIn = false
                                },
                            )
                        }
                    }

                    composable(Routes.PLAN) {
                        val viewModel = marginViewModel("plan") { PlanViewModel.create(container) }
                        PlanScreen(
                            viewModel = viewModel,
                            onOpenTask = { navController.navigate(Routes.TASKS) },
                        )
                    }

                    composable(Routes.TASKS) {
                        val viewModel = marginViewModel("tasks") { TasksViewModel.create(container) }
                        TasksScreen(viewModel = viewModel)
                    }

                    composable(Routes.TIMETABLE) {
                        val viewModel = marginViewModel("timetable") {
                            TimetableViewModel.create(container)
                        }
                        TimetableScreen(viewModel = viewModel)
                    }

                    composable(Routes.INSIGHTS) {
                        val viewModel = marginViewModel("insights") {
                            InsightsViewModel.create(container)
                        }
                        InsightsScreen(viewModel = viewModel)
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(
                        route = Routes.FOCUS,
                        arguments = listOf(navArgument("blockId") { type = NavType.LongType }),
                    ) { entry ->
                        val blockId = entry.arguments?.getLong("blockId") ?: 0L
                        val viewModel = marginViewModel("focus-$blockId") {
                            FocusViewModel.create(container, blockId)
                        }
                        FocusScreen(
                            viewModel = viewModel,
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }

            if (showAssistant) {
                val viewModel = marginViewModel("assistant") { AssistantViewModel.create(container) }
                AssistantSheet(viewModel = viewModel, onDismiss = { showAssistant = false })
            }
        }
    }
}

private fun androidx.navigation.NavDestination?.isTopLevel(): Boolean {
    val route = this?.route ?: return true
    return TopLevelDestination.entries.any { it.route == route }
}

/** A one-line description of the seeded week, shown during setup. */
private fun timetableSummary(): String {
    val subjects = TimetableSeed.subjects.joinToString(", ") { it.shortName }
    val teachingDays = TimetableSeed.entries
        .filter { it.kind.isTeaching }
        .map { it.dayOfWeek }
        .distinct()
        .size
    return "$teachingDays days a week of classes: $subjects."
}
