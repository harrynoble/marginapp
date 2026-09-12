package com.margin.app.ai

import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

sealed interface AiOutcome {
    data class Success(val text: String) : AiOutcome
    data class Failure(val message: String, val retryable: Boolean) : AiOutcome
}

/** An image to send with a request: base64 bytes and their media type. */
data class EncodedImage(val base64: String, val mediaType: String)

/**
 * One shape per capability. Swapping providers means adding a class here and an entry in
 * [AiProviderFactory]; nothing else in the app knows which model answered.
 */
interface AiProvider {
    val id: String
    suspend fun complete(systemPrompt: String, userText: String, maxTokens: Int = 900): AiOutcome
    suspend fun completeWithImage(
        systemPrompt: String,
        userText: String,
        image: EncodedImage,
        maxTokens: Int = 3000,
    ): AiOutcome
}

object AiProviderFactory {
    fun create(settings: AiSettings): AiProvider? = when {
        !settings.isConfigured -> null
        settings.provider == AiProviderId.ANTHROPIC -> AnthropicProvider(settings)
        settings.provider == AiProviderId.OPENAI -> OpenAiCompatibleProvider(settings)
        else -> null
    }
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Plain HttpURLConnection rather than a networking library: two endpoints do not justify a
 * dependency, and this keeps the request body visible so it is obvious what leaves the device.
 */
private suspend fun postJson(
    url: String,
    headers: Map<String, String>,
    body: JsonObject,
    timeoutMillis: Int = 30_000,
): Result<JsonObject> = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
        }.orEmpty()

        if (code !in 200..299) {
            return@withContext Result.failure(HttpError(code, text.take(300)))
        }
        Result.success(json.parseToJsonElement(text).jsonObject)
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } finally {
        connection?.disconnect()
    }
}

class HttpError(val code: Int, val body: String) : IOException("HTTP $code: $body") {
    val retryable: Boolean get() = code == 429 || code in 500..599
}

class AnthropicProvider(private val settings: AiSettings) : AiProvider {

    override val id: String = "anthropic"

    override suspend fun complete(systemPrompt: String, userText: String, maxTokens: Int): AiOutcome =
        send(systemPrompt, maxTokens, 30_000) {
            put("role", "user")
            put("content", userText)
        }

    override suspend fun completeWithImage(
        systemPrompt: String,
        userText: String,
        image: EncodedImage,
        maxTokens: Int,
    ): AiOutcome = send(systemPrompt, maxTokens, 90_000) {
        put("role", "user")
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", image.mediaType)
                        put("data", image.base64)
                    }
                },
            )
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", userText)
                },
            )
        }
    }

    private suspend fun send(
        systemPrompt: String,
        maxTokens: Int,
        timeout: Int,
        message: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): AiOutcome {
        val body = buildJsonObject {
            put("model", settings.model.ifBlank { AiProviderId.ANTHROPIC.defaultModel })
            put("max_tokens", maxTokens)
            put("system", systemPrompt)
            putJsonArray("messages") { add(buildJsonObject(message)) }
        }
        val result = postJson(
            url = settings.baseUrl.trimEnd('/') + "/v1/messages",
            headers = mapOf(
                "x-api-key" to settings.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            body = body,
            timeoutMillis = timeout,
        )
        return result.fold(
            onSuccess = { payload ->
                val text = payload["content"]
                    ?.let { it as? JsonArray }
                    ?.firstOrNull { element -> element.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                    ?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content
                if (text.isNullOrBlank()) {
                    AiOutcome.Failure("The model returned an empty answer.", retryable = true)
                } else {
                    AiOutcome.Success(text)
                }
            },
            onFailure = { it.toFailure() },
        )
    }
}

/** Works with the OpenAI chat completions shape, including compatible self-hosted endpoints. */
class OpenAiCompatibleProvider(private val settings: AiSettings) : AiProvider {

    override val id: String = "openai"

    override suspend fun complete(systemPrompt: String, userText: String, maxTokens: Int): AiOutcome =
        send(systemPrompt, maxTokens, 30_000) {
            put("role", "user")
            put("content", userText)
        }

    override suspend fun completeWithImage(
        systemPrompt: String,
        userText: String,
        image: EncodedImage,
        maxTokens: Int,
    ): AiOutcome = send(systemPrompt, maxTokens, 90_000) {
        put("role", "user")
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", userText)
                },
            )
            add(
                buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", "data:${image.mediaType};base64,${image.base64}") }
                },
            )
        }
    }

    private suspend fun send(
        systemPrompt: String,
        maxTokens: Int,
        timeout: Int,
        message: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): AiOutcome {
        val body = OpenAiRequest.chatBody(
            model = settings.model.ifBlank { AiProviderId.OPENAI.defaultModel },
            maxTokens = maxTokens,
            official = OpenAiRequest.isOfficial(settings.baseUrl),
            messages = kotlinx.serialization.json.buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    },
                )
                add(buildJsonObject(message))
            },
        )
        val result = postJson(
            url = settings.baseUrl.trimEnd('/') + "/v1/chat/completions",
            headers = mapOf("Authorization" to "Bearer " + settings.apiKey),
            body = body,
            timeoutMillis = timeout,
        )
        return result.fold(
            onSuccess = { payload ->
                val text = payload["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content
                if (text.isNullOrBlank()) {
                    AiOutcome.Failure("The model returned an empty answer.", retryable = true)
                } else {
                    AiOutcome.Success(text)
                }
            },
            onFailure = { it.toFailure() },
        )
    }
}

private fun Throwable.toFailure(): AiOutcome.Failure = when (this) {
    is HttpError -> AiOutcome.Failure(
        message = when (code) {
            401 -> "The assistant key was rejected. Test the connection in Settings."
            403 -> "The assistant key isn't allowed to make this request."
            429 -> "The assistant is rate limited or out of credit. Try again shortly."
            404 -> "The assistant model isn't available. Test the connection in Settings."
            400 -> "The assistant rejected the request. Test the connection in Settings."
            in 500..599 -> "The assistant service is having trouble."
            else -> "The assistant request failed ($code)."
        },
        retryable = retryable,
    )
    is IOException -> AiOutcome.Failure("No connection to the assistant.", retryable = true)
    else -> AiOutcome.Failure("The assistant could not be reached.", retryable = false)
}

/** Models sometimes wrap JSON in prose or fences. This pulls out the first balanced object. */
internal object JsonText {
    fun firstObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val ch = raw[index]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
