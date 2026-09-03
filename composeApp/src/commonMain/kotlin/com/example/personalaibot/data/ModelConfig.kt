package com.example.personalaibot.data

/**
 * Centralized configuration for AI models used in JARVIS.
 * This avoids hardcoding model names across the codebase.
 */
object ModelConfig {
    /**
     * The default model used for text chat and tool execution.
     * This is used when no setting is found in the database.
     */
    const val DEFAULT_MAIN_MODEL = "gemini-3.1-pro"

    /**
     * The default model used for Real-time Voice and Camera (Live mode).
     * This is used when no setting is found in the database.
     */
    const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"

    /**
     * ลำดับโมเดล Gemini สำรอง — ใช้เมื่อโมเดลหลักติด rate limit (429) / overload (503) / timeout
     * สำหรับ free tier (GeminiService จะไล่ลองทีละตัวจนกว่าจะสำเร็จ)
     * เรียงตามโควต้า free tier จริง (ดูจากหน้า Rate Limit ของ Google AI Studio 2026-08-08):
     * lite กลุ่ม 3.5/3.1 ให้ RPD 500/วัน → ขึ้นก่อน; กลุ่ม flash ปกติ RPD ~20/วัน → ไว้ท้าย
     */
    val GEMINI_FALLBACK_MODELS = listOf(
        "gemini-2.5-flash",      // หลัก: TPM 250K, RPD 20
        "gemini-3.5-flash-lite", // RPD 500, TPM 250K ← สำรองตัวแรก (โควต้าเยอะสุด)
        "gemini-3.1-flash-lite", // RPD 500, TPM 130K
        "gemini-3-flash",        // RPD 20, TPM 150K
        "gemini-2.5-flash-lite", // RPD 20, TPM 250K
        "gemini-3.5-flash",      // RPD 20, TPM 250K
        "gemini-3.6-flash",      // สำรองเพิ่มตาม list โมเดลที่ผู้ใช้ยืนยันใช้ได้ (2026-08-18)
        "gemini-3.7-flash"       // สำรองลำดับสุดท้าย
    )

    /**
     * Checks if a model name is intended for Live mode (bidirectional WebSocket).
     */
    fun isLiveModel(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        return m.contains("live") || m.contains("flash-live") || m.contains("native-audio") || m.contains("realtime")
    }

    /**
     * Checks if a model supports native function calling (Bidi or Tool use).
     * Automatically supports all modern Gemini 1.5, 2.x, 3.x+ models, OpenAI GPT/o-series,
     * Claude, and major open weights on OpenRouter/Groq/NVIDIA NIM.
     */
    fun supportsNativeTools(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        // Gemini: All modern multimodal models (except text embeddings, imagen, and legacy 1.0)
        if (m.contains("gemini")) {
            return !m.contains("embedding") && !m.contains("imagen") && !m.contains("1.0")
        }
        // OpenAI
        if (m.startsWith("gpt-") || m.startsWith("o1-") || m.startsWith("o3-") || m.startsWith("o4-")) return true
        // Claude
        if (m.contains("claude-")) return true
        // OpenRouter / Groq / NIM providers
        return m.startsWith("openai/") ||
               m.startsWith("anthropic/") ||
               m.startsWith("google/") ||
               m.startsWith("meta-llama/") ||
               m.startsWith("qwen/") ||
               m.startsWith("deepseek/")
    }
}
