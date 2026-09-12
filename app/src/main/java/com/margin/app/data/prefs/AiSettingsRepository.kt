package com.margin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
    OPENAI("openai", "OpenAI", "gpt-5-mini", "https://api.openai.com"),
    NONE("none", "Off", "", "");

    companion object {
        fun fromKey(key: String?): AiProviderId =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NONE
    }
}

/** Whether the assistant has been proven to work with the saved key, by a real request. */
enum class AiConnectionState(val key: String) {
    /** A key is saved but has not been tested since it or the model last changed. */
    UNKNOWN("unknown"),
    /** An authenticated request succeeded. */
    CONNECTED("connected"),
    /** The last test failed; the message says why. */
    FAILED("failed"),
    /** The key was removed. */
    DISCONNECTED("disconnected");

    companion object {
        fun fromKey(key: String?): AiConnectionState = entries.firstOrNull { it.key == key } ?: UNKNOWN
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
    val connection: AiConnectionState = AiConnectionState.UNKNOWN,
    /** A human sentence from the last test: "OpenAI connected successfully", or why it failed. */
    val connectionMessage: String = "",
    val checkedAt: Long = 0L,
) {
    val isConfigured: Boolean
        get() = enabled && provider != AiProviderId.NONE && apiKey.isNotBlank()

    val isConnected: Boolean get() = isConfigured && connection == AiConnectionState.CONNECTED

    /** Never log or display the key itself. */
    val maskedKey: String
        get() = when {
            apiKey.isBlank() -> ""
            apiKey.length <= 8 -> "********"
            else -> apiKey.take(3) + "..." + apiKey.takeLast(4)
        }

    override fun toString(): String =
        "AiSettings(provider=$provider, model=$model, configured=$isConfigured, connection=$connection)"
}

/**
 * The assistant settings live in their own DataStore file, excluded from cloud backup, and
 * the key itself is encrypted with a Keystore key before it is written. It is the user's key
 * on the user's device: never in the source, never in an export, never in a log.
 */
class AiSettingsRepository(private val context: Context) {

    private object Keys {
        val enabled = booleanPreferencesKey("ai_enabled")
        val provider = stringPreferencesKey("ai_provider")
        val model = stringPreferencesKey("ai_model")
        val baseUrl = stringPreferencesKey("ai_base_url")
        val apiKey = stringPreferencesKey("ai_api_key")
        val shareDetail = booleanPreferencesKey("ai_share_detail")
        val connection = stringPreferencesKey("ai_connection")
        val connectionMessage = stringPreferencesKey("ai_connection_message")
        val checkedAt = longPreferencesKey("ai_checked_at")
    }

    val settings: Flow<AiSettings> = context.aiStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            val provider = AiProviderId.fromKey(prefs[Keys.provider])
            val stored = prefs[Keys.apiKey].orEmpty()
            val key = KeyCipher.decrypt(stored)
            AiSettings(
                enabled = prefs[Keys.enabled] ?: false,
                provider = provider,
                model = prefs[Keys.model].orEmpty().ifBlank { provider.defaultModel },
                baseUrl = prefs[Keys.baseUrl].orEmpty().ifBlank { provider.defaultBaseUrl },
                apiKey = key.orEmpty(),
                shareScheduleDetail = prefs[Keys.shareDetail] ?: true,
                connection = if (key == null) AiConnectionState.FAILED else AiConnectionState.fromKey(prefs[Keys.connection]),
                connectionMessage = if (key == null) {
                    "The saved key could not be read on this device. Enter it again."
                } else {
                    prefs[Keys.connectionMessage].orEmpty()
                },
                checkedAt = prefs[Keys.checkedAt] ?: 0L,
            )
        }

    suspend fun current(): AiSettings = settings.first()

    /**
     * Saves the settings. Changing the key, the model or the provider makes any earlier test
     * result meaningless, so the connection goes back to untested unless the change set it.
     */
    suspend fun update(transform: (AiSettings) -> AiSettings) {
        val before = current()
        var next = transform(before)
        val changed = next.apiKey != before.apiKey || next.model != before.model ||
            next.provider != before.provider || next.baseUrl != before.baseUrl
        if (changed && next.connection == before.connection) {
            next = next.copy(connection = AiConnectionState.UNKNOWN, connectionMessage = "", checkedAt = 0L)
        }
        context.aiStore.edit { prefs ->
            prefs[Keys.enabled] = next.enabled
            prefs[Keys.provider] = next.provider.key
            prefs[Keys.model] = next.model
            prefs[Keys.baseUrl] = next.baseUrl
            prefs[Keys.apiKey] = KeyCipher.encrypt(next.apiKey)
            prefs[Keys.shareDetail] = next.shareScheduleDetail
            prefs[Keys.connection] = next.connection.key
            prefs[Keys.connectionMessage] = next.connectionMessage
            prefs[Keys.checkedAt] = next.checkedAt
        }
    }

    /** Re-saves a key written before encryption existed, so it no longer sits in plain text. */
    suspend fun ensureEncrypted() {
        val stored = context.aiStore.data.first()[Keys.apiKey].orEmpty()
        if (stored.isNotEmpty() && !KeyCipher.isEncrypted(stored)) {
            context.aiStore.edit { prefs -> prefs[Keys.apiKey] = KeyCipher.encrypt(stored) }
        }
    }

    suspend fun clear() {
        context.aiStore.edit { it.clear() }
    }
}
