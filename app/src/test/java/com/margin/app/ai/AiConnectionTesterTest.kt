package com.margin.app.ai

import com.margin.app.data.prefs.AiProviderId
import com.margin.app.data.prefs.AiSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * The connection test must only ever report success after real, authenticated requests
 * succeed, and must say plainly why it failed otherwise.
 */
class AiConnectionTesterTest {

    private class FakeTransport(private val answer: (method: String, url: String) -> HttpResponse) : HttpTransport {
        val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
        val bodies = mutableListOf<String?>()
        override suspend fun send(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?,
            timeoutMillis: Int,
        ): HttpResponse {
            calls += Triple(method, url, headers)
            bodies += body
            return answer(method, url)
        }
    }

    private val key = "sk-test-0123456789abcdef"
    private val settings = AiSettings(
        enabled = true,
        provider = AiProviderId.OPENAI,
        model = "gpt-5-mini",
        baseUrl = "https://api.openai.com",
        apiKey = key,
    )
    private val chatOk = HttpResponse(200, """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""")

    private fun test(transport: HttpTransport, with: AiSettings = settings) =
        runBlocking { AiConnectionTester(transport).test(with) }

    @Test
    fun `a valid key is connected only after the model lookup and a real completion succeed`() {
        val transport = FakeTransport { method, _ -> if (method == "GET") HttpResponse(200, """{"id":"gpt-5-mini"}""") else chatOk }
        val result = test(transport)
        assertTrue(result.connected)
        assertEquals("OpenAI connected successfully", result.message)
        assertEquals(listOf("GET", "POST"), transport.calls.map { it.first })
        assertEquals("https://api.openai.com/v1/models/gpt-5-mini", transport.calls[0].second)
        assertEquals("https://api.openai.com/v1/chat/completions", transport.calls[1].second)
        assertEquals("the key is sent exactly as entered", "Bearer $key", transport.calls[0].third["Authorization"])
    }

    @Test
    fun `an invalid key fails and says so`() {
        val transport = FakeTransport { _, _ -> HttpResponse(401, """{"error":{"message":"Incorrect API key provided: sk-t***cdef","code":"invalid_api_key"}}""") }
        val result = test(transport)
        assertFalse(result.connected)
        assertEquals("OpenAI connection failed: Invalid API key.", result.message)
        assertFalse("the key is never echoed back", result.message.contains("sk-"))
    }

    @Test
    fun `no network fails with a network reason`() {
        val transport = FakeTransport { _, _ -> throw UnknownHostException("api.openai.com") }
        val result = test(transport)
        assertFalse(result.connected)
        assertTrue(result.message, result.message.contains("No internet connection"))
    }

    @Test
    fun `any other network failure also fails`() {
        val transport = FakeTransport { _, _ -> throw IOException("reset") }
        assertEquals("OpenAI connection failed: Network unavailable.", test(transport).message)
    }

    @Test
    fun `an account without credit and a rate limit are told apart`() {
        val quota = FakeTransport { _, _ -> HttpResponse(429, """{"error":{"message":"You exceeded your current quota","code":"insufficient_quota"}}""") }
        assertTrue(test(quota).message.contains("no remaining credit"))
        val limited = FakeTransport { _, _ -> HttpResponse(429, """{"error":{"message":"Rate limit reached","code":"rate_limit_exceeded"}}""") }
        assertTrue(test(limited).message.contains("Rate limited"))
    }

    @Test
    fun `a model the key cannot use is replaced by one it can, and the test says so`() {
        val transport = FakeTransport { method, url ->
            when {
                method == "GET" && url.endsWith("/v1/models/gpt-5-mini") -> HttpResponse(404, """{"error":{"message":"The model does not exist"}}""")
                method == "GET" -> HttpResponse(200, """{"data":[{"id":"gpt-4o"},{"id":"gpt-4.1-mini"}]}""")
                else -> chatOk
            }
        }
        val result = test(transport)
        assertTrue(result.connected)
        assertEquals("gpt-4.1-mini", result.model)
        assertTrue(result.message.startsWith("OpenAI connected successfully"))
    }

    @Test
    fun `a rejected completion is a failure even if the key was accepted`() {
        val transport = FakeTransport { method, _ ->
            if (method == "GET") HttpResponse(200, "{}") else HttpResponse(400, """{"error":{"message":"Unsupported parameter"}}""")
        }
        val result = test(transport)
        assertFalse(result.connected)
        assertTrue(result.message.contains("rejected"))
    }

    @Test
    fun `an empty or damaged key fails before anything is sent`() {
        val transport = FakeTransport { _, _ -> error("must not be called") }
        assertEquals("OpenAI connection failed: Enter an API key first.", test(transport, settings.copy(apiKey = "")).message)
        assertTrue(test(transport, settings.copy(apiKey = "sk-abc def")).message.contains("spaces or line breaks"))
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun `current models get max_completion_tokens, and reasoning models no fixed temperature`() {
        val messages = buildJsonArray { }
        val reasoning = OpenAiRequest.chatBody("gpt-5-mini", 100, official = true, messages = messages)
        assertTrue(reasoning.containsKey("max_completion_tokens"))
        assertFalse(reasoning.containsKey("max_tokens"))
        assertNull(reasoning["temperature"])

        val classic = OpenAiRequest.chatBody("gpt-4o-mini", 100, official = true, messages = messages)
        assertEquals("100", classic["max_completion_tokens"]!!.jsonPrimitive.content)
        assertEquals("0", classic["temperature"]!!.jsonPrimitive.content)

        val selfHosted = OpenAiRequest.chatBody("llama", 100, official = false, messages = messages)
        assertTrue(selfHosted.containsKey("max_tokens"))
    }

    @Test
    fun `the settings never print the key`() {
        assertFalse(settings.toString().contains(key))
        assertFalse(settings.maskedKey.contains("0123456789"))
    }
}
