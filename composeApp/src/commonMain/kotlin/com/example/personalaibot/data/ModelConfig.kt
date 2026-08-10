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
        "gemini-3.5-flash"       // RPD 20, TPM 250K
    )

    /**
     * Checks if a model name is intended for Live mode.
     */
    fun isLiveModel(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        return m.contains("live") || m.contains("flash-live")
    }

    /**
     * Checks if a model supports native function calling (Bidi or Tool use).
     * Supports: Gemini 2.x/3.x, OpenAI GPT/o-series, Claude, OpenRouter models
     */
    fun supportsNativeTools(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        // Gemini
        return m.contains("3.1-flash") ||
               m.contains("3.1-pro") ||
               m.contains("2.5-flash") ||
               m.contains("2.5-pro") ||
               m.contains("native-audio") ||
               m.contains("live") ||
               m.contains("3.5-flash") ||
               // OpenAI
               m.startsWith("gpt-") ||
               m.startsWith("o1-") ||
               m.startsWith("o3-") ||
               m.startsWith("o4-") ||
               // Claude (direct or via OpenRouter)
               m.contains("claude-") ||
               // OpenRouter prefixed models
               m.startsWith("openai/gpt") ||
               m.startsWith("anthropic/claude") ||
               m.startsWith("google/gemini")
    }
}
