package com.margin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.AiSettings
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.data.repository.ScheduleRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.SeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

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
        aiSettingsRepository.update { it.copy(apiKey = "", enabled = false) }
        message.value = "The API key was removed from this device."
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

    /** Wipes every plan and history record. Preferences and the timetable are kept. */
    fun clearHistory() = viewModelScope.launch {
        val today = LocalDate.now()
        var cursor = today.minusDays(365)
        while (!cursor.isAfter(today.plusDays(30))) {
            scheduleRepository.clearDay(cursor)
            cursor = cursor.plusDays(1)
        }
        planningService.replan(today)
        message.value = "Schedule history cleared."
    }

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
        )
    }
}
