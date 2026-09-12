package com.margin.app.di

import android.content.Context
import com.margin.app.ai.AssistantService
import com.margin.app.ai.VisionImporter
import com.margin.app.ai.context.ContextBuilder
import com.margin.app.data.db.MarginDatabase
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.prefs.TimetableReferenceRepository
import com.margin.app.data.repository.DayRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.GoalRepository
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.data.repository.TaskRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.domain.planner.DayPlanner
import com.margin.app.domain.usecase.DataExporter
import com.margin.app.domain.usecase.DayRollover
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

    val dayRepository = DayRepository(
        dayStateDao = database.dayStateDao(),
        deferredDao = database.deferredWorkDao(),
        nudgeDao = database.nudgeLogDao(),
    )

    val goalRepository = GoalRepository(database.learningGoalDao())
    val examRepository = ExamRepository(database.examDao())

    val planner = DayPlanner()

    val planningService = PlanningService(
        timetableRepository = timetableRepository,
        taskRepository = taskRepository,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
        dayRepository = dayRepository,
        goalRepository = goalRepository,
        examRepository = examRepository,
        planner = planner,
    )

    val scheduleActions = ScheduleActions(
        scheduleRepository = scheduleRepository,
        taskRepository = taskRepository,
        planningService = planningService,
        dayRepository = dayRepository,
        preferencesRepository = preferencesRepository,
    )

    val dayRollover = DayRollover(
        scheduleRepository = scheduleRepository,
        dayRepository = dayRepository,
        preferencesRepository = preferencesRepository,
        planningService = planningService,
    )

    val timetableReferenceRepository = TimetableReferenceRepository(appContext)

    val seedService = SeedService(
        timetableRepository = timetableRepository,
        preferencesRepository = preferencesRepository,
        referenceRepository = timetableReferenceRepository,
    )

    val alarmScheduler = AlarmScheduler(
        context = appContext,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
        dayRepository = dayRepository,
        timetableRepository = timetableRepository,
        examRepository = examRepository,
    )

    private val contextBuilder = ContextBuilder(
        scheduleRepository = scheduleRepository,
        taskRepository = taskRepository,
        timetableRepository = timetableRepository,
        examRepository = examRepository,
        dayRepository = dayRepository,
    )

    val assistantService = AssistantService(
        aiSettingsRepository = aiSettingsRepository,
        preferencesRepository = preferencesRepository,
        taskRepository = taskRepository,
        scheduleRepository = scheduleRepository,
        timetableRepository = timetableRepository,
        examRepository = examRepository,
        planningService = planningService,
        scheduleActions = scheduleActions,
        contextBuilder = contextBuilder,
        interactionDao = database.aiInteractionDao(),
    )

    val visionImporter = VisionImporter(aiSettingsRepository)

    val dataExporter = DataExporter(
        context = appContext,
        timetableRepository = timetableRepository,
        taskRepository = taskRepository,
        scheduleRepository = scheduleRepository,
        goalRepository = goalRepository,
        examRepository = examRepository,
    )

    /** Deletes every record of what happened; tasks, timetable and settings stay. */
    suspend fun clearHistory() {
        scheduleRepository.clearHistory()
    }
}
