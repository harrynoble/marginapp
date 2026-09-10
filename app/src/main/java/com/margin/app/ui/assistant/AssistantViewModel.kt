package com.margin.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.ai.AssistantResult
import com.margin.app.ai.AssistantService
import com.margin.app.ai.AssistantSource
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssistantEntry(
    val userText: String,
    val reply: String,
    val applied: List<String>,
    val rejected: List<String>,
    val source: AssistantSource,
    val error: String?,
)

data class AssistantUiState(
    val history: List<AssistantEntry> = emptyList(),
    val sending: Boolean = false,
    val assistantConfigured: Boolean = false,
    val suggestions: List<String> = DEFAULT_SUGGESTIONS,
)

private val DEFAULT_SUGGESTIONS = listOf(
    "I am out from 6 to 8 tonight",
    "Finish the DSA assignment by Friday",
    "I am tired today, keep it light",
    "Take a 30 minute break",
)

class AssistantViewModel(
    private val assistantService: AssistantService,
    aiSettingsRepository: AiSettingsRepository,
) : ViewModel() {

    private val history = MutableStateFlow<List<AssistantEntry>>(emptyList())
    private val sending = MutableStateFlow(false)

    val state: StateFlow<AssistantUiState> = combine(
        history,
        sending,
        aiSettingsRepository.settings.map { it.isConfigured },
    ) { entries, isSending, configured ->
        AssistantUiState(
            history = entries,
            sending = isSending,
            assistantConfigured = configured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun submit(text: String) {
        if (text.isBlank() || sending.value) return
        viewModelScope.launch {
            sending.value = true
            val result: AssistantResult = runCatching { assistantService.submit(text) }
                .getOrElse {
                    AssistantResult(
                        reply = "Something went wrong handling that. Nothing was changed.",
                        error = it.message,
                    )
                }
            history.value = history.value + AssistantEntry(
                userText = text.trim(),
                reply = result.reply,
                applied = result.applied,
                rejected = result.rejected,
                source = result.source,
                error = result.error,
            )
            sending.value = false
        }
    }

    fun clear() {
        history.value = emptyList()
    }

    companion object {
        fun create(container: AppContainer) = AssistantViewModel(
            assistantService = container.assistantService,
            aiSettingsRepository = container.aiSettingsRepository,
        )
    }
}
