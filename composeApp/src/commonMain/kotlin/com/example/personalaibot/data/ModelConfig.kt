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
     * Checks if a model name is intended for Live mode.
     */
    fun isLiveModel(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        return m.contains("live") || m.contains("flash-live")
    }

    /**
     * Checks if a model supports native function calling (Bidi or Tool use).
     * Modern Gemini 2.x and 3.x models generally support this.
     */
    fun supportsNativeTools(modelName: String): Boolean {
        val m = modelName.lowercase().removePrefix("models/")
        return m.contains("3.1-flash") || 
               m.contains("3.1-pro") || 
               m.contains("2.5-flash") || 
               m.contains("2.5-pro") || 
               m.contains("native-audio") ||
               m.contains("live")
    }
}
