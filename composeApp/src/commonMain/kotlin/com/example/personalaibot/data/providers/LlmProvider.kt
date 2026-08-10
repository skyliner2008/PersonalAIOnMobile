package com.example.personalaibot.data.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * LlmProvider — Interface หลักสำหรับ Text Generation ทุก Provider
 *
 * ทุก Provider (Gemini, OpenAI, Claude, OpenRouter, LiteLLM) ต้อง implement
 * interface นี้เพื่อให้ JarvisOrchestrator สามารถสลับ Provider ได้อิสระ
 *
 * Features:
 * - Text generation (streaming + non-streaming)
 * - Function/Tool calling
 * - Model listing
 *
 * @since 2026-04-29
 */
interface LlmProvider {
    /** Provider ID เช่น "gemini", "openai", "claude", "openrouter", "litellm" */
    val providerId: String

    /** ชื่อแสดงใน UI */
    val displayName: String

    /** รองรับ streaming response หรือไม่ */
    val supportsStreaming: Boolean

    /** รองรับ function/tool calling หรือไม่ */
    val supportsFunctionCalling: Boolean

    /**
     * Generate text response พร้อม streaming
     * @return Flow<LlmChunk> สตรีมทีละ chunk
     */
    fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions = LlmOptions()
    ): Flow<LlmChunk>

    /**
     * Generate text response แบบไม่ stream (รอผลลัพธ์ทั้งหมด)
     */
    suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions = LlmOptions()
    ): LlmResult

    /**
     * ดึงรายการ models ที่ใช้ได้จาก provider นี้
     * @param apiKey API key ของ provider
     * @return รายการ models พร้อมข้อมูล pricing (ถ้ามี)
     */
    suspend fun listModels(apiKey: String): List<LlmModelInfo>

    /**
     * ตรวจสอบว่า provider พร้อมใช้งานหรือไม่
     */
    suspend fun isAvailable(): Boolean
}

// ─── Data Classes ────────────────────────────────────────────────────────────

/** ข้อความใน conversation */
data class LlmMessage(
    val role: String,        // "system", "user", "assistant", "tool"
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<LlmToolCall>? = null
)

/** Tool call ที่ LLM ร้องขอ */
data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String    // JSON string
)

/** Options สำหรับ generation */
data class LlmOptions(
    val model: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val tools: List<LlmToolSpec>? = null,
    val responseFormat: String? = null,   // "json" สำหรับ structured output
    // เดิม 60s — reasoning models บน OpenRouter (nemotron) คิดนานเกิน 60s ทำ stream ขาดกลางประโยค
    // (เคสจริง 2026-08-08: nemotron-3-ultra ตอบ "↓ -0.0" แล้วตัด) → ยืดเป็น 120s
    val timeoutMs: Long = 120_000L
)

/** Tool specification สำหรับ function calling */
data class LlmToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String    // JSON Schema
)

/** Streaming chunk */
data class LlmChunk(
    val text: String = "",
    val toolCalls: List<LlmToolCall>? = null,
    val finishReason: String? = null   // "stop", "tool_calls", "length"
)

/** ผลลัพธ์จาก non-streaming generation */
data class LlmResult(
    val text: String,
    val toolCalls: List<LlmToolCall>? = null,
    val finishReason: String? = null,
    val modelUsed: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

/** ข้อมูล model สำหรับแสดงใน UI */
@Serializable
data class LlmModelInfo(
    val id: String,
    val displayName: String,
    val contextLength: Int? = null,
    val isFree: Boolean = false,
    val pricing: LlmPricing? = null,
    val supportsFunctions: Boolean = false,
    val supportsVision: Boolean = false,
    /** true เฉพาะ models ที่รองรับ Live / Realtime voice session (Gemini Live, OpenAI Realtime ฯลฯ) */
    val supportsLive: Boolean = false
)

/** Pricing info สำหรับ model */
@Serializable
data class LlmPricing(
    val promptPer1M: Double = 0.0,        // $/1M input tokens
    val completionPer1M: Double = 0.0     // $/1M output tokens
)
