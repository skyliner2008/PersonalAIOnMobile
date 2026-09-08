package com.example.personalaibot.data

/**
 * Centralized dynamic configuration for AI models used in JARVIS.
 * Synchronizes with Google Gemini API's ModelService.ListModels (/v1beta/models)
 * to avoid hardcoded model name traps, prevent 404 errors, and provide self-healing fallbacks.
 */
object ModelConfig {
    /**
     * Initial safe default main model used before API sync or when DB has no setting.
     */
    const val DEFAULT_MAIN_MODEL = "gemini-3.6-flash"

    /**
     * Initial safe default model used for Real-time Voice and Camera (Live mode).
     * gemini-3.1-flash-live-preview delivers superior real-time speech prosody, sub-second latency, and native tools.
     */
    const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"

    // Seed list of known verified models in order of priority (used only before first API sync)
    private val SEED_FALLBACK_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-flash-latest"
    )

    // Dynamically discovered active models from Google API
    private val dynamicLock = Any()
    private var _dynamicGeminiModels: List<String> = emptyList()
    private var _dynamicLiveModels: List<String> = emptyList()
    private val _deadModels = mutableSetOf<String>()
    private var _promotedLiveModel: String? = null
    private val _penalizedLiveModels = mutableMapOf<String, Long>()

    /**
     * ลำดับโมเดล Gemini สำรอง — ดึงจาก dynamic active models ที่ API คืนมาจริง
     * คัดกรองโมเดลที่ตาย (404) ออกทั้งหมดโดยอัตโนมัติ
     */
    val GEMINI_FALLBACK_MODELS: List<String>
        get() = getFallbackChain()

    /**
     * อัปเดตรายการโมเดลจริงที่ได้จาก Google ModelService.ListModels API
     */
    fun updateAvailableModels(models: List<GeminiModel>) {
        if (models.isEmpty()) return
        val genContentModels = models.filter { model ->
            val methods = model.supportedGenerationMethods ?: emptyList()
            methods.contains("generateContent")
        }.map { it.name.removePrefix("models/").trim() }

        val bidiModels = models.filter { model ->
            val methods = model.supportedGenerationMethods ?: emptyList()
            methods.contains("bidiGenerateContent")
        }.map { it.name.removePrefix("models/").trim() }

        // กรองเฉพาะโมเดลแชททั่วไป (ตัด embedding, image generator, robotics, tts-only)
        val filteredChatModels = genContentModels.filter { id ->
            val lc = id.lowercase()
            !lc.contains("embedding") &&
            !lc.contains("image") &&
            !lc.contains("banana") &&
            !lc.contains("robotics") &&
            !lc.contains("lyria") &&
            !lc.contains("clip") &&
            !lc.contains("transcribe") &&
            !lc.contains("tts") &&
            !lc.contains("1.0")
        }

        // จัดลำดับความสำคัญ:
        // 1. Flash / Flash-Lite models (เร็ว, โควต้าสูง, เหมาะกับแอพมือถือ) เรียงตามเวอร์ชันใหม่ -> เก่า
        // 2. Pro / Thinking / General preview models
        val sortedChatModels = filteredChatModels.sortedWith(
            compareByDescending<String> { isFlashModel(it) }
                .thenByDescending { extractModelVersion(it) }
                .thenBy { it.contains("lite") }
        )

        // Live models: รวม bidiGenerateContent + preview endpoints (3.1 flash live -> 2.5 native audio 09-2025 -> latest)
        val liveCandidates = (listOf(
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-09-2025",
            "gemini-2.5-flash-native-audio-latest",
            "gemini-2.0-flash-exp"
        ) + bidiModels).distinct().filter { id ->
            val lc = id.lowercase()
            !lc.contains("transcribe") && id != "gemini-2.5-flash-native-audio-preview-12-2025"
        }

        synchronized(dynamicLock) {
            _dynamicGeminiModels = sortedChatModels
            _dynamicLiveModels = liveCandidates
        }

        com.example.personalaibot.logDebug(
            "ModelConfig",
            "Updated dynamic models from API (${sortedChatModels.size} chat, ${liveCandidates.size} live). Top: ${sortedChatModels.take(5)}"
        )
    }

    val SEED_LIVE_MODELS = listOf(
        "gemini-3.1-flash-live-preview",
        "gemini-2.5-flash-native-audio-preview-09-2025",
        "gemini-2.5-flash-native-audio-latest",
        "gemini-2.0-flash-exp"
    )

    /**
     * เลื่อนโมเดล Live ที่เชื่อมต่อสำเร็จและสุขภาพดีขึ้นเป็นอันดับ 1
     */
    fun promoteHealthyLiveModel(modelName: String) {
        val clean = modelName.removePrefix("models/").trim()
        if (clean.isBlank() || isModelDead(clean)) return
        synchronized(dynamicLock) {
            _promotedLiveModel = clean
            _penalizedLiveModels.remove(clean)
            if (_dynamicLiveModels.isNotEmpty()) {
                val reordered = mutableListOf(clean)
                _dynamicLiveModels.forEach { if (it != clean) reordered.add(it) }
                _dynamicLiveModels = reordered
            }
        }
        com.example.personalaibot.logDebug("ModelConfig", "🚀 Promoted healthy live model: '$clean' to top priority")
    }

    /**
     * ทำโทษโมเดล Live ที่เกิด timeout/ขัดข้องชั่วคราว ให้ถอยไปอยู่ท้าย chain
     * ป้องกันไม่ให้การเชื่อมต่อถัดไปต้องเสียเวลากับโมเดลเดิมซ้ำๆ
     */
    fun penalizeLiveModel(modelName: String, durationMs: Long = 60_000L) {
        val clean = modelName.removePrefix("models/").trim()
        if (clean.isBlank()) return
        val expiry = System.currentTimeMillis() + durationMs
        synchronized(dynamicLock) {
            _penalizedLiveModels[clean] = expiry
            if (_promotedLiveModel == clean) {
                _promotedLiveModel = null
            }
        }
        com.example.personalaibot.logDebug("ModelConfig", "⏳ Penalized live model '$clean' for ${durationMs / 1000}s due to timeout/failure")
    }

    fun isLiveModelPenalized(modelName: String): Boolean {
        val clean = modelName.removePrefix("models/").trim()
        val now = System.currentTimeMillis()
        return synchronized(dynamicLock) {
            val exp = _penalizedLiveModels[clean] ?: return@synchronized false
            if (exp > now) {
                true
            } else {
                _penalizedLiveModels.remove(clean)
                false
            }
        }
    }

    /**
     * ดึง Live fallback chain แบบ dynamic — นำ primaryModel / promoted model ขึ้นต้น (ถ้ามี)
     * ตามด้วยโมเดล Live จริงที่ active โดยตัด dead models ทิ้ง และดันโมเดลที่ติด penalty ไปท้ายสุด
     */
    fun getLiveFallbackChain(primaryModel: String? = null): List<String> {
        val now = System.currentTimeMillis()
        val (pool, promoted, penalized) = synchronized(dynamicLock) {
            _penalizedLiveModels.entries.removeAll { it.value <= now }
            val active = if (_dynamicLiveModels.isNotEmpty()) _dynamicLiveModels else SEED_LIVE_MODELS
            val dead = _deadModels.toSet()
            val available = active.filter { it !in dead }
            Triple(available, _promotedLiveModel, _penalizedLiveModels.keys.toSet())
        }

        val primaryClean = primaryModel?.removePrefix("models/")?.trim()
        val unpenalizedResult = mutableListOf<String>()
        val penalizedResult = mutableListOf<String>()

        fun addCandidate(m: String) {
            if (m.isBlank() || isModelDead(m)) return
            if (m in unpenalizedResult || m in penalizedResult) return
            if (m in penalized) {
                penalizedResult.add(m)
            } else {
                unpenalizedResult.add(m)
            }
        }

        // 1. Primary requested model
        if (!primaryClean.isNullOrBlank()) {
            addCandidate(primaryClean)
        }
        // 2. Previously promoted healthy live model
        if (!promoted.isNullOrBlank()) {
            addCandidate(promoted)
        }
        // 3. Dynamic or seed models
        for (m in pool) {
            addCandidate(m)
        }
        for (m in SEED_LIVE_MODELS) {
            addCandidate(m)
        }

        val combined = unpenalizedResult + penalizedResult
        return if (combined.isNotEmpty()) combined else SEED_LIVE_MODELS.filter { !isModelDead(it) }
    }

    /**
     * ดึง fallback chain แบบ dynamic — นำ primaryModel ขึ้นต้น (ถ้ามี)
     * ตามด้วยโมเดลจริงที่ active ทั้งหมด โดยตัด dead models ทิ้ง
     */
    fun getFallbackChain(primaryModel: String? = null): List<String> {
        val pool = synchronized(dynamicLock) {
            val active = if (_dynamicGeminiModels.isNotEmpty()) _dynamicGeminiModels else SEED_FALLBACK_MODELS
            val dead = _deadModels.toSet()
            active.filter { it !in dead }
        }

        val primaryClean = primaryModel?.removePrefix("models/")?.trim()
        val result = mutableListOf<String>()
        if (!primaryClean.isNullOrBlank() && !isModelDead(primaryClean)) {
            result.add(primaryClean)
        }
        for (m in pool) {
            if (m !in result) {
                result.add(m)
            }
        }
        return if (result.isNotEmpty()) result else listOf(DEFAULT_MAIN_MODEL)
    }

    /**
     * บันทึกว่าโมเดลนี้ตาย/ถูกปลดระวาง/404 เพื่อตัดออกจากทุก chain
     */
    fun markModelDead(modelName: String) {
        val clean = modelName.removePrefix("models/").trim()
        if (clean.isBlank()) return
        synchronized(dynamicLock) {
            _deadModels.add(clean)
            _dynamicGeminiModels = _dynamicGeminiModels.filter { it != clean }
            _dynamicLiveModels = _dynamicLiveModels.filter { it != clean }
        }
        com.example.personalaibot.logError(
            "ModelConfig",
            "⚠️ Model '$clean' marked as DEAD (404/unsupported). Purged from active pool."
        )
    }

    /**
     * ตรวจสอบว่าโมเดลนี้ถูก blacklist หรือไม่
     */
    fun isModelDead(modelName: String): Boolean {
        val clean = modelName.removePrefix("models/").trim()
        return synchronized(dynamicLock) { clean in _deadModels }
    }

    /**
     * ตรวจสอบว่าโมเดลนี้รองรับ generateContent ใน API ปัจจุบันหรือไม่
     */
    fun isModelSupported(modelName: String): Boolean {
        val clean = modelName.removePrefix("models/").trim()
        if (isModelDead(clean)) return false
        return synchronized(dynamicLock) {
            if (_dynamicGeminiModels.isEmpty()) true // ยังไม่ได้ sync จาก API -> allow
            else clean in _dynamicGeminiModels
        }
    }

    /**
     * โมเดลหลักที่ดีที่สุด ณ ปัจจุบัน (Active Flash ล่าสุด)
     */
    fun getBestActiveModel(): String {
        return synchronized(dynamicLock) {
            _dynamicGeminiModels.firstOrNull { it !in _deadModels }
                ?: DEFAULT_MAIN_MODEL
        }
    }

    /**
     * โมเดล Live ที่ดีที่สุด ณ ปัจจุบัน
     */
    fun getBestLiveModel(): String {
        return synchronized(dynamicLock) {
            _dynamicLiveModels.firstOrNull { it !in _deadModels }
                ?: DEFAULT_LIVE_MODEL
        }
    }

    /**
     * รายชื่อโมเดล active ทั้งหมด
     */
    fun getActiveGeminiModels(): List<String> {
        return synchronized(dynamicLock) {
            val dead = _deadModels.toSet()
            if (_dynamicGeminiModels.isNotEmpty()) _dynamicGeminiModels.filter { it !in dead }
            else SEED_FALLBACK_MODELS.filter { it !in dead }
        }
    }

    private fun isFlashModel(id: String): Boolean {
        val lc = id.lowercase()
        return lc.contains("flash")
    }

    private fun extractModelVersion(id: String): Double {
        val regex = Regex("""gemini-(\d+(?:\.\d+)?)""")
        val match = regex.find(id.lowercase())
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

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

    /**
     * Resets internal in-memory caches and sets for unit tests.
     */
    fun resetForTesting() {
        synchronized(dynamicLock) {
            _deadModels.clear()
            _dynamicGeminiModels = emptyList()
            _dynamicLiveModels = emptyList()
            _promotedLiveModel = null
            _penalizedLiveModels.clear()
        }
    }
}
