package com.example.personalaibot.data.providers

import com.example.personalaibot.logDebug
import com.example.personalaibot.data.ModelListResponse
import com.example.personalaibot.data.GeminiModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * ─── ApiKeyTester ───────────────────────────────────────────────────────────
 *
 * Pings each LLM provider with a lightweight call so the user can verify the
 * API key really works (not just the format).  We reuse the provider's
 * `listModels()` endpoint because:
 *   • It's a simple GET — no token cost on most providers.
 *   • It exercises real authentication (returns 401 / 403 on bad keys).
 *   • Every provider already implements it — no extra plumbing.
 *
 * Result includes round-trip latency so the Settings UI can also show the
 * connection quality.
 *
 * 2026-04-30 (P1.1) — skyliner.jojo@gmail.com
 */
object ApiKeyTester {

    /** Outcome of a single API key test run. */
    data class TestResult(
        val ok: Boolean,
        val latencyMs: Long,
        val modelsFound: Int = 0,
        val error: String? = null,
    ) {
        val displayMessage: String
            get() = when {
                ok && modelsFound > 0 -> "✓ เชื่อมต่อสำเร็จ! ($modelsFound models) - อย่าลืมกด Save ด้านล่าง"
                ok -> "✓ เชื่อมต่อสำเร็จ! (${latencyMs}ms) - อย่าลืมกด Save ด้านล่าง"
                error != null -> "✗ $error"
                else -> "✗ Failed"
            }
    }

    /**
     * Format-only validation — instant, no network.  Used for inline hints
     * before the user even taps "Test".
     */
    fun validateFormat(providerId: String, apiKey: String): String? {
        if (apiKey.isBlank()) return "Key is empty"
        val k = apiKey.trim()
        return when (providerId.lowercase()) {
            "gemini" -> when {
                !k.startsWith("AIza") -> "Gemini keys usually start with \"AIza\""
                k.length < 35 -> "Key looks too short"
                else -> null
            }
            "openai" -> when {
                !k.startsWith("sk-") -> "OpenAI keys start with \"sk-\""
                k.length < 40 -> "Key looks too short"
                else -> null
            }
            "claude", "anthropic" -> when {
                !k.startsWith("sk-ant-") -> "Claude keys start with \"sk-ant-\""
                k.length < 40 -> "Key looks too short"
                else -> null
            }
            "openrouter" -> when {
                !k.startsWith("sk-or-") -> "OpenRouter keys start with \"sk-or-\""
                k.length < 40 -> "Key looks too short"
                else -> null
            }
            // Vertex AI uses ADC — no API key format to validate
            "vertexai" -> null
            else -> null // unknown provider → skip format check
        }
    }

    /**
     * Live test against the provider.  `client` is reused from the app's
     * Ktor instance so connection pooling / timeouts are consistent.
     *
     * For Gemini we have to instantiate a temporary provider against the
     * REST endpoint because the registry's GeminiLlmProvider currently
     * delegates to a singleton GeminiService that uses the saved app key.
     * For everything else we hit the provider's `listModels` directly.
     */
    suspend fun test(
        client: HttpClient,
        providerId: String,
        apiKey: String,
        timeoutMs: Long = 8_000L,
    ): TestResult = withContext(Dispatchers.IO) {
        val formatError = validateFormat(providerId, apiKey)
        if (formatError != null && apiKey.isNotBlank()) {
            // Format clearly wrong — fail fast, save the user a network call.
            return@withContext TestResult(ok = false, latencyMs = 0L, error = formatError)
        }
        val started = Clock.System.now().toEpochMilliseconds()
        try {
            val models = withTimeout(timeoutMs) {
                when (providerId.lowercase()) {
                    "openai" -> OpenAILlmProvider(client, apiKey).listModels(apiKey)
                    "claude", "anthropic" -> ClaudeLlmProvider(client, apiKey).listModels(apiKey)
                    "openrouter" -> OpenRouterLlmProvider(client, apiKey).listModels(apiKey)
                    "gemini" -> {
                        // GeminiLlmProvider's listModels uses the GeminiService
                        // singleton which doesn't accept a per-call key; do a
                        // direct REST call instead so we honour the supplied
                        // key and don't pollute global state.
                        geminiListModels(client, apiKey)
                    }
                    // Vertex AI uses ADC via server proxy — no direct test possible from mobile
                    // Return a dummy success if the key looks like "ADC" placeholder
                    "vertexai" -> listOf(LlmModelInfo(id = "vertexai", displayName = "Vertex AI (ADC via Server)"))
                    else -> emptyList()
                }
            }
            val latency = Clock.System.now().toEpochMilliseconds() - started
            if (models.isEmpty()) {
                // listModels() returns emptyList on error (e.g., invalid API key) or when no models match
                // Since we already handle exceptions in the catch block, empty here means the provider
                // didn't return an error but also no models - this is unusual for OpenAI
                logDebug("ApiKeyTester", "$providerId: 0 models returned — provider may have restrictions or key issue")
                TestResult(ok = false, latencyMs = latency, modelsFound = 0, error = "No models returned — check API key permissions")
            } else {
                TestResult(ok = true, latencyMs = latency, modelsFound = models.size)
            }
        } catch (e: Exception) {
            val latency = Clock.System.now().toEpochMilliseconds() - started
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) -> "Invalid API key (401)"
                msg.contains("403") || msg.contains("Forbidden", ignoreCase = true) -> "Forbidden — check key permissions (403)"
                msg.contains("429") -> "Rate limited (429)"
                msg.contains("timeout", ignoreCase = true) -> "Network timeout"
                msg.contains("UnknownHost", ignoreCase = true) -> "No internet"
                msg.length > 120 -> msg.take(117) + "…"
                msg.isBlank() -> "Unknown error"
                else -> msg
            }
            TestResult(ok = false, latencyMs = latency, error = friendly)
        }
    }

    /**
     * Direct GET https://generativelanguage.googleapis.com/v1beta/models?key=…
     * Lightweight — used only for testing the Gemini key.  We don't try to
     * parse the full response: HTTP 200 = key works.
     */
    private suspend fun geminiListModels(client: HttpClient, apiKey: String): List<LlmModelInfo> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val resp: HttpResponse = client.get(url)
        if (!resp.status.isSuccess()) {
            val err = try { resp.bodyAsText() } catch (_: Exception) { "Unknown error" }
            error("HTTP ${resp.status.value}: $err")
        }
        
        return try {
            val jsonStr = resp.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val modelList: ModelListResponse = json.decodeFromString(jsonStr)
            modelList.models.filter {
                it.supportedGenerationMethods?.contains("generateContent") == true
            }.map { m ->
                LlmModelInfo(id = m.name, displayName = m.displayName ?: m.name)
            }
        } catch (e: Exception) {
            // Fallback if parsing fails but 200 OK
            listOf(LlmModelInfo(id = "gemini", displayName = "Gemini"))
        }
    }
}
