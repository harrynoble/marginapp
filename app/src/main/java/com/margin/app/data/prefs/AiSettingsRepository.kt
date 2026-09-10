package com.margin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.aiStore: DataStore<Preferences> by preferencesDataStore(name = "ai_secrets")

enum class AiProviderId(val key: String, val label: String, val defaultModel: String, val defaultBaseUrl: String) {
    ANTHROPIC("anthropic", "Anthropic", "claude-sonnet-5", "https://api.anthropic.com"),
    OPENAI("openai", "OpenAI compatible", "gpt-4o-mini", "https://api.openai.com"),
    NONE("none", "Off", "", "");

    companion object {
        fun fromKey(key: String?): AiProviderId =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NONE
    }
}

data class AiSettings(
    val enabled: Boolean = false,
    val provider: AiProviderId = AiProviderId.NONE,
    val model: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    /** Off by default. When on, the schedule shape is included in requests for better answers. */
    val shareScheduleDetail: Boolean = true,
) {
    val isConfigured: Boolean
        get() = enabled && provider != AiProviderId.NONE && apiKey.isNotBlank()

    /** Never log or display the key itself. */
    val maskedKey: String
        get() = when {
            apiKey.isBlank() -> ""
            apiKey.length <= 8 -> "********"
            else -> apiKey.take(4) + "..." + apiKey.takeLast(4)
        }
}

/**
 * The API key lives in its own DataStore file so it can be excluded from cloud backup and
 * wiped independently of the rest of the settings. It is the user key on the user device;
 * it is never bundled into the source and never leaves the device except to the chosen
 * provider endpoint.
 */
class AiSettingsRepository(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("ai_enabled")
        val provider = stringPreferencesKey("ai_provider")
        val model = stringPreferencesKey("ai_model")
        val baseUrl = stringPreferencesKey("ai_base_url")
        val apiKey = stringPreferencesKey("ai_api_key")
        val shareDetail = booleanPreferencesKey("ai_share_detail")
    }

    val settings: Flow<AiSettings> = context.aiStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            val provider = AiProviderId.fromKey(prefs[Keys.provider])
            AiSettings(
                enabled = prefs[Keys.enabled] ?: false,
                provider = provider,
                model = prefs[Keys.model].orEmpty().ifBlank { provider.defaultModel },
                baseUrl = prefs[Keys.baseUrl].orEmpty().ifBlank { provider.defaultBaseUrl },
                apiKey = prefs[Keys.apiKey].orEmpty(),
                shareScheduleDetail = prefs[Keys.shareDetail] ?: true,
            )
        }

    suspend fun current(): AiSettings = settings.first()

    suspend fun update(transform: (AiSettings) -> AiSettings) {
        val next = transform(current())
        context.aiStore.edit { prefs ->
            prefs[Keys.enabled] = next.enabled
            prefs[Keys.provider] = next.provider.key
            prefs[Keys.model] = next.model
            prefs[Keys.baseUrl] = next.baseUrl
            prefs[Keys.apiKey] = next.apiKey
            prefs[Keys.shareDetail] = next.shareScheduleDetail
        }
    }

    suspend fun clear() {
        context.aiStore.edit { it.clear() }
    }
}
