package com.margin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.ai.AiConnectionTester
import com.margin.app.ai.ConnectionResult
import com.margin.app.data.prefs.AiConnectionState
import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.AiSettings
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.usecase.DataExporter
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.SeedService
import com.margin.app.notifications.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(),
    val ai: AiSettings = AiSettings(),
    val message: String? = null,
    val loading: Boolean = true,
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val planningService: PlanningService,
    private val seedService: SeedService,
    private val dataExporter: DataExporter,
    private val alarmScheduler: AlarmScheduler,
    private val connectionTester: AiConnectionTester = AiConnectionTester(),
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    private val testing = MutableStateFlow(false)

    /** True while a real request to the assistant provider is in flight. */
    val connectionTesting: StateFlow<Boolean> = testing

    private val exportFile = MutableStateFlow<File?>(null)

    /** A finished export waiting to be handed to the share sheet. */
    val exported: StateFlow<File?> = exportFile

    val state: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        aiSettingsRepository.settings,
        message,
    ) { prefs, ai, msg ->
        SettingsUiState(prefs = prefs, ai = ai, message = msg, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Every settings edit replans today, so a change is visible immediately. */
    fun update(transform: (UserPreferences) -> UserPreferences) = viewModelScope.launch {
        preferencesRepository.update(transform)
        planningService.replan(LocalDate.now())
        alarmScheduler.rearm()
    }

    /** Settings that only change how the app looks. Nothing to replan. */
    fun updateDisplay(transform: (UserPreferences) -> UserPreferences) = viewModelScope.launch {
        preferencesRepository.update(transform)
    }

    fun updateAi(transform: (AiSettings) -> AiSettings) = viewModelScope.launch {
        aiSettingsRepository.update(transform)
    }

    fun setProvider(provider: AiProviderId) = viewModelScope.launch {
        aiSettingsRepository.update {
            it.copy(
                provider = provider,
                model = provider.defaultModel,
                baseUrl = provider.defaultBaseUrl,
                enabled = provider != AiProviderId.NONE,
            )
        }
    }

    fun clearAiKey() = viewModelScope.launch {
        val label = aiSettingsRepository.current().provider.label
        aiSettingsRepository.update {
            it.copy(
                apiKey = "",
                enabled = false,
                connection = AiConnectionState.DISCONNECTED,
                connectionMessage = "$label disconnected",
                checkedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Saves the key and proves it works with a real request before calling it connected. */
    fun saveKey(raw: String) = viewModelScope.launch {
        val key = raw.trim()
        if (key.isEmpty()) {
            message.value = "Enter an API key first."
            return@launch
        }
        aiSettingsRepository.update { it.copy(apiKey = key, enabled = true) }
        runConnectionTest()
    }

    fun testConnection() = viewModelScope.launch { runConnectionTest() }

    private suspend fun runConnectionTest() {
        if (testing.value) return
        testing.value = true
        try {
            val settings = aiSettingsRepository.current()
            val result = runCatching { connectionTester.test(settings) }
                .getOrElse { ConnectionResult(false, "${settings.provider.label} connection failed: the test could not run.") }
            aiSettingsRepository.update {
                it.copy(
                    model = result.model ?: it.model,
                    connection = if (result.connected) AiConnectionState.CONNECTED else AiConnectionState.FAILED,
                    connectionMessage = result.message,
                    checkedAt = System.currentTimeMillis(),
                )
            }
        } finally {
            testing.value = false
        }
    }

    fun restoreSeededTimetable() = viewModelScope.launch {
        seedService.resetToSeed()
        planningService.replan(LocalDate.now())
        message.value = "The shipped timetable was restored."
    }

    fun rebuildToday() = viewModelScope.launch {
        planningService.replan(LocalDate.now())
        message.value = "Today was rebuilt."
    }

    /**
     * Wipes every plan and every history record: sessions, skips, breaks and what the planner
     * learned from them. Preferences, tasks, exams and the timetable are kept.
     */
    fun clearHistory() = viewModelScope.launch {
        val today = LocalDate.now()
        var cursor = today.minusDays(365)
        while (!cursor.isAfter(today.plusDays(30))) {
            scheduleRepository.clearDay(cursor)
            cursor = cursor.plusDays(1)
        }
        scheduleRepository.clearHistory()
        planningService.replan(today)
        message.value = "Schedule history cleared."
    }

    fun export() = viewModelScope.launch {
        runCatching { dataExporter.export() }
            .onSuccess { exportFile.value = it }
            .onFailure { message.value = "The export could not be written." }
    }

    fun consumeExport() {
        exportFile.value = null
    }

    fun canScheduleExact(): Boolean = alarmScheduler.canScheduleExact()

    fun dismissMessage() {
        message.value = null
    }

    companion object {
        fun create(container: AppContainer) = SettingsViewModel(
            preferencesRepository = container.preferencesRepository,
            aiSettingsRepository = container.aiSettingsRepository,
            scheduleRepository = container.scheduleRepository,
            planningService = container.planningService,
            seedService = container.seedService,
            dataExporter = container.dataExporter,
            alarmScheduler = container.alarmScheduler,
        )
    }
}
