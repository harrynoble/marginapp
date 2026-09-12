package com.margin.app.ai

import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A raw HTTP answer: status code and body text. */
data class HttpResponse(val code: Int, val body: String)

/** The network, behind an interface so the connection test can be exercised without one. */
fun interface HttpTransport {
    /** Throws [IOException] when no answer arrives at all. */
    suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?, timeoutMillis: Int): HttpResponse
}

object UrlConnectionTransport : HttpTransport {
    override suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMillis: Int,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() } }.orEmpty()
            HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** The outcome of a connection test, in words a person can act on. */
data class ConnectionResult(
    val connected: Boolean,
    /** "OpenAI connected successfully", or the reason it failed. */
    val message: String,
    /** The model that worked, when the test found a different one than configured. */
    val model: String? = null,
)

/**
 * Proves the assistant works before the app says so. Two real, authenticated requests: the
 * configured model is looked up (cheap, and it catches a wrong key or a retired model), then a
 * one-word chat completion is made exactly as the app will make them. Only if both succeed is
 * the connection reported as working.
 */
class AiConnectionTester(private val transport: HttpTransport = UrlConnectionTransport) {

    suspend fun test(settings: AiSettings): ConnectionResult = when (settings.provider) {
        AiProviderId.OPENAI -> testOpenAi(settings)
        AiProviderId.ANTHROPIC -> testAnthropic(settings)
        AiProviderId.NONE -> ConnectionResult(false, "Choose a provider first.")
    }

    private suspend fun testOpenAi(settings: AiSettings): ConnectionResult {
        val label = AiProviderId.OPENAI.label
        keyProblem(settings.apiKey)?.let { return fail(label, it) }
        val base = settings.baseUrl.ifBlank { AiProviderId.OPENAI.defaultBaseUrl }.trimEnd('/')
        val auth = mapOf("Authorization" to "Bearer " + settings.apiKey)
        val configured = settings.model.ifBlank { AiProviderId.OPENAI.defaultModel }

        var model = configured
        val lookup = call { transport.send("GET", "$base/v1/models/$configured", auth, null, LOOKUP_TIMEOUT) }
            ?: return fail(label, networkReason ?: "Network error.")
        when (lookup.code) {
            200 -> Unit
            404 -> {
                // The configured model is not available to this key. Pick one that is, rather
                // than leaving the assistant broken.
                val list = call { transport.send("GET", "$base/v1/models", auth, null, LOOKUP_TIMEOUT) }
                    ?: return fail(label, networkReason ?: "Network error.")
                if (list.code != 200) return fail(label, describe(label, list))
                val available = modelIds(list.body)
                model = PREFERRED_OPENAI_MODELS.firstOrNull { it in available }
                    ?: return fail(label, "The model \"$configured\" isn't available for this key.")
            }
            else -> return fail(label, describe(label, lookup))
        }

        val body = OpenAiRequest.chatBody(
            model = model,
            maxTokens = 16,
            official = OpenAiRequest.isOfficial(base),
            messages = buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "Reply with the single word OK.") }) },
        )
        val chat = call { transport.send("POST", "$base/v1/chat/completions", auth, body.toString(), CHAT_TIMEOUT) }
            ?: return fail(label, networkReason ?: "Network error.")
        if (chat.code != 200) return fail(label, describe(label, chat))
        val choices = parse(chat.body)?.get("choices") as? JsonArray
        if (choices.isNullOrEmpty()) return fail(label, "$label answered, but not in the expected format.")

        val switched = model != configured
        return ConnectionResult(
            connected = true,
            message = "$label connected successfully" + if (switched) ". Using $model, because $configured isn't available for this key." else "",
            model = model.takeIf { switched },
        )
    }

    private suspend fun testAnthropic(settings: AiSettings): ConnectionResult {
        val label = AiProviderId.ANTHROPIC.label
        keyProblem(settings.apiKey)?.let { return fail(label, it) }
        val base = settings.baseUrl.ifBlank { AiProviderId.ANTHROPIC.defaultBaseUrl }.trimEnd('/')
        val headers = mapOf("x-api-key" to settings.apiKey, "anthropic-version" to "2023-06-01")
        val model = settings.model.ifBlank { AiProviderId.ANTHROPIC.defaultModel }

        val lookup = call { transport.send("GET", "$base/v1/models/$model", headers, null, LOOKUP_TIMEOUT) }
            ?: return fail(label, networkReason ?: "Network error.")
        if (lookup.code == 404) return fail(label, "The model \"$model\" isn't available for this key.")
        if (lookup.code != 200) return fail(label, describe(label, lookup))

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "Reply with the single word OK.") }) })
        }
        val reply = call { transport.send("POST", "$base/v1/messages", headers, body.toString(), CHAT_TIMEOUT) }
            ?: return fail(label, networkReason ?: "Network error.")
        if (reply.code != 200) return fail(label, describe(label, reply))
        if (parse(reply.body)?.get("content") !is JsonArray) return fail(label, "$label answered, but not in the expected format.")
        return ConnectionResult(true, "$label connected successfully")
    }

    // ---- helpers -------------------------------------------------------------------------------

    private var networkReason: String? = null

    /** Runs a request, turning "no answer at all" into a reason instead of an exception. */
    private suspend fun call(block: suspend () -> HttpResponse): HttpResponse? {
        networkReason = null
        return try {
            block()
        } catch (e: UnknownHostException) {
            networkReason = "No internet connection, or the server can't be found."
            null
        } catch (e: SocketTimeoutException) {
            networkReason = "The server didn't respond in time."
            null
        } catch (e: SSLException) {
            networkReason = "A secure connection couldn't be made."
            null
        } catch (e: IOException) {
            networkReason = "Network unavailable."
            null
        } catch (e: IllegalArgumentException) {
            networkReason = "The server address is not valid."
            null
        }
    }

    private fun fail(label: String, reason: String) = ConnectionResult(false, "$label connection failed: $reason")

    private fun describe(label: String, response: HttpResponse): String {
        val error = parse(response.body)?.get("error")
        val detail = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        val code = (error as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
            ?: (error as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        return when (response.code) {
            401 -> "Invalid API key."
            403 -> "This key isn't allowed to make that request. Check its permissions."
            429 -> if (code == "insufficient_quota" || detail?.contains("quota", ignoreCase = true) == true) {
                "The account has no remaining credit."
            } else {
                "Rate limited. Try again in a minute."
            }
            400 -> "The request was rejected" + (detail?.let { ": ${it.take(160)}" } ?: ".")
            404 -> "Not found. Check the model name" + (detail?.let { ": ${it.take(160)}" } ?: ".")
            in 500..599 -> "$label is having trouble right now. Try again shortly."
            else -> "The request failed (${response.code})."
        }
    }

    private fun keyProblem(key: String): String? = when {
        key.isBlank() -> "Enter an API key first."
        key.any { it.isWhitespace() } -> "The key contains spaces or line breaks. Paste it again without them."
        else -> null
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun modelIds(text: String): Set<String> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]!!.jsonArray
            .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            .toSet()
    }.getOrDefault(emptySet())

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private const val LOOKUP_TIMEOUT = 15_000
        private const val CHAT_TIMEOUT = 45_000

        /** Tried in order when the configured model is not available to a key. */
        val PREFERRED_OPENAI_MODELS = listOf("gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini", "gpt-5", "gpt-4.1", "gpt-4o")
    }
}

/**
 * The shape of an OpenAI chat request. Current models reject the older `max_tokens` field in
 * favour of `max_completion_tokens`, and reasoning models (gpt-5, o-series) reject a fixed
 * temperature; sending either made every request fail with a 400. Compatible self-hosted
 * servers still expect the older fields, so those keep them.
 */
internal object OpenAiRequest {

    fun isOfficial(baseUrl: String): Boolean = baseUrl.contains("api.openai.com")

    fun isReasoningModel(model: String): Boolean {
        val id = model.lowercase()
        return id.startsWith("gpt-5") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4")
    }

    fun chatBody(model: String, maxTokens: Int, official: Boolean, messages: JsonArray): JsonObject = buildJsonObject {
        put("model", model)
        when {
            !official -> {
                put("max_tokens", maxTokens)
                put("temperature", 0)
            }
            // Reasoning models spend tokens thinking before they answer; leave room for both.
            isReasoningModel(model) -> put("max_completion_tokens", maxOf(maxTokens * 4, REASONING_FLOOR))
            else -> {
                put("max_completion_tokens", maxTokens)
                put("temperature", 0)
            }
        }
        put("messages", messages)
    }

    private const val REASONING_FLOOR = 2048
}
