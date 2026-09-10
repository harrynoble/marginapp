package com.margin.app.di

import android.content.Context
import com.margin.app.ai.AssistantService
import com.margin.app.ai.context.ContextBuilder
import com.margin.app.data.db.MarginDatabase
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.ScheduleActions
import com.margin.app.domain.usecase.SeedService
import com.margin.app.notifications.AlarmScheduler

/**
 * One app, one user, a handful of singletons. A DI framework here would be ceremony;
 * this container is the whole object graph and it fits on one screen.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val database: MarginDatabase = MarginDatabase.get(appContext)

    val preferencesRepository = PreferencesRepository(appContext)
    val aiSettingsRepository = AiSettingsRepository(appContext)

    val timetableRepository = TimetableRepository(
        timetableDao = database.timetableDao(),
        subjectDao = database.subjectDao(),
        routineDao = database.routineDao(),
    )

    val taskRepository = TaskRepository(
        taskDao = database.taskDao(),
        sessionDao = database.taskSessionDao(),
        projectDao = database.projectDao(),
        eventDao = database.eventDao(),
        linkDao = database.resourceLinkDao(),
    )

    val scheduleRepository = ScheduleRepository(
        scheduleDao = database.scheduleDao(),
        planDao = database.dailyPlanDao(),
        checkInDao = database.checkInDao(),
        historyDao = database.historyDao(),
    )

    val planner = DayPlanner()

    val planningService = PlanningService(
        timetableRepository = timetableRepository,
        taskRepository = taskRepository,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
        planner = planner,
    )

    val scheduleActions = ScheduleActions(
        scheduleRepository = scheduleRepository,
        taskRepository = taskRepository,
        planningService = planningService,
    )

    val seedService = SeedService(
        timetableRepository = timetableRepository,
        preferencesRepository = preferencesRepository,
    )

    val alarmScheduler = AlarmScheduler(
        context = appContext,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
    )

    private val contextBuilder = ContextBuilder(
        scheduleRepository = scheduleRepository,
        taskRepository = taskRepository,
        timetableRepository = timetableRepository,
    )

    val assistantService = AssistantService(
        aiSettingsRepository = aiSettingsRepository,
        preferencesRepository = preferencesRepository,
        taskRepository = taskRepository,
        scheduleRepository = scheduleRepository,
        planningService = planningService,
        scheduleActions = scheduleActions,
        contextBuilder = contextBuilder,
        interactionDao = database.aiInteractionDao(),
    )
}
