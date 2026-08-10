package com.example.personalaibot.data.providers

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * ModelAutoTester — ทดสอบโมเดลของ provider (Groq/NVIDIA NIM) ทีละตัว
 * แบบเดียวกับ AlertDataTest: ไล่ทุก model, log ผลทาง logcat ให้ user เช็ค
 *
 * ทดสอบ 2 ระดับต่อ model:
 *  1. chat  — ส่ง prompt สั้น ต้องตอบกลับได้ (ไม่ error/ไม่ว่าง)
 *  2. tools — ส่ง tool spec จำลอง ถามเวลา → โมเดลต้องยิง tool_calls กลับมา
 *
 * ผลถูก persist ใน Settings DB ("model_caps_v1") และใช้ซ่อน model ที่ chat ไม่ได้
 * ออกจาก list + ปิด tag 🔧 ของตัวที่เรียก tool ไม่ได้
 *
 * 2026-08-08 — ตาม request: auto-test Groq/NIM ทุกตัว
 */
object ModelAutoTester {

    private const val TAG = "ModelAutoTest"

    data class ModelCapability(
        val chatOk: Boolean,
        val toolsOk: Boolean,
        val latencyMs: Long,
        val note: String = "",
    )

    /** serialize map "provider/model" → "chat,tools,latency" (เก็บ setting เดียว เลี่ยง JSON lib เพิ่ม) */
    fun serializeCaps(caps: Map<String, ModelCapability>): String =
        caps.entries.joinToString("\n") { (k, v) -> "$k|${if (v.chatOk) 1 else 0}${if (v.toolsOk) 1 else 0}|${v.latencyMs}" }

    fun parseCaps(raw: String): Map<String, ModelCapability> =
        raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val flags = parts[1]
            ModelCapability(
                chatOk = flags.getOrNull(0) == '1',
                toolsOk = flags.getOrNull(1) == '1',
                latencyMs = parts[2].toLongOrNull() ?: 0L,
            ).let { parts[0] to it }
        }.toMap()

    /**
     * ทดสอบทุก model ของ provider (เรียงตาม listModels)
     * @param freeOnly true = เทสเฉพาะ model ฟรี (OpenRouter — list เต็มมีหลายร้อยตัว)
     * @param onProgress callback (current, total, modelId) — ใช้อัปเดต UI ว่ากำลังเทสตัวไหน
     * @return map "modelId" → capability
     */
    suspend fun testAllModels(
        client: HttpClient,
        provider: LlmProvider,
        apiKey: String,
        timeoutPerTestMs: Long = 25_000L,
        freeOnly: Boolean = false,
        onProgress: (current: Int, total: Int, modelId: String) -> Unit = { _, _, _ -> },
    ): Map<String, ModelCapability> = withContext(Dispatchers.IO) {
        var models = provider.listModels(apiKey)
        if (freeOnly) models = models.filter { it.isFree }
        logDebug(TAG, "════ เริ่ม auto-test ${provider.displayName}: ${models.size} models${if (freeOnly) " (free only)" else ""} ════")
        val results = mutableMapOf<String, ModelCapability>()

        models.forEachIndexed { index, model ->
            onProgress(index + 1, models.size, model.id)
            results[model.id] = testSingleModel(client, provider, model.id, timeoutPerTestMs)
        }

        val chatPass = results.count { it.value.chatOk }
        val toolsPass = results.count { it.value.toolsOk }
        logDebug(TAG, "════ สรุป ${provider.displayName}: chat ผ่าน $chatPass/${models.size}, tools ผ่าน $toolsPass/${models.size} ════")
        results
    }

    /** ทดสอบ model เดียว: chat test → ถ้าผ่านค่อย tool test (ประหยัดโควต้า) */
    private suspend fun testSingleModel(
        client: HttpClient,
        provider: LlmProvider,
        modelId: String,
        timeoutMs: Long,
    ): ModelCapability {
        val started = System.currentTimeMillis()

        // ── Test 1: chat ──
        val chatOk = try {
            withTimeout(timeoutMs) {
                val result = provider.generate(
                    messages = listOf(LlmMessage("user", "Reply with exactly: OK")),
                    options = LlmOptions(model = modelId, maxTokens = 16, temperature = 0.1f),
                )
                val text = result.text.trim()
                text.isNotEmpty() && !text.startsWith("⚠️")
            }
        } catch (e: Exception) {
            logError(TAG, "❌ ${provider.providerId}/$modelId chat → ${e.message?.take(120)}")
            false
        }

        if (!chatOk) {
            logDebug(TAG, "❌ ${provider.providerId}/$modelId → chat ไม่ผ่าน (${System.currentTimeMillis() - started}ms)")
            return ModelCapability(chatOk = false, toolsOk = false, latencyMs = System.currentTimeMillis() - started)
        }

        // ── Test 2: tool calling ──
        val toolsOk = try {
            withTimeout(timeoutMs) {
                val result = provider.generate(
                    messages = listOf(LlmMessage("user", "What time is it now? Use the get_current_time tool to answer.")),
                    options = LlmOptions(
                        model = modelId,
                        maxTokens = 256,
                        temperature = 0.1f,
                        tools = listOf(LlmToolSpec(
                            name = "get_current_time",
                            description = "Get the current date and time.",
                            parametersJson = """{"type":"object","properties":{}}""",
                        )),
                    ),
                )
                !result.toolCalls.isNullOrEmpty()
            }
        } catch (e: Exception) {
            logError(TAG, "⚠️ ${provider.providerId}/$modelId tools → ${e.message?.take(120)}")
            false
        }

        val latency = System.currentTimeMillis() - started
        logDebug(TAG, "${if (toolsOk) "✅" else "🔶"} ${provider.providerId}/$modelId → chat=OK tools=${if (toolsOk) "OK" else "NO"} (${latency}ms)")
        return ModelCapability(chatOk = true, toolsOk = toolsOk, latencyMs = latency)
    }
}
