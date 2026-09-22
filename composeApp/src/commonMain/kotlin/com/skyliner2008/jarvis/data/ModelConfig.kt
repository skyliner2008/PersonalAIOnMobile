package com.skyliner2008.jarvis.data

import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Centralized dynamic configuration for AI models used in JARVIS.
 * Synchronizes with Google Gemini API's ModelService.ListModels (/v1beta/models)
 * to avoid hardcoded model name traps, prevent 404 errors, and provide self-healing fallbacks.
 */
object ModelConfig {
    /**
     * โมเดลแชทหลัก — free tier ใช้เฉพาะตระกูล flash-lite เท่านั้น
     *
     * เหตุผล (ยืนยันกับ AI Studio console 2026-09-16):
     * - flash-lite: 15 RPM / 250K TPM / **500 req ต่อวัน** ต่อโมเดล → 2 ตัวรวม 1,000 req/วัน
     * - flash (3.x): 5 RPM / 250K TPM / **20 req ต่อวัน** → ไม่พอสำหรับผู้ช่วยที่ใช้ tool เยอะ
     * งานวนลูป (เฝ้าราคา/เงื่อนไขตลาด) ใช้ automation engine ของแอปเอง ไม่เรียกโมเดล
     * โมเดลถูกเรียกเฉพาะตอนมีเหตุการณ์จริงเท่านั้น
     */
    const val DEFAULT_MAIN_MODEL = "gemini-3.5-flash-lite"

    /** โมเดลแชทที่อนุญาตบน free tier (เรียงตามลำดับที่ใช้) */
    val CHAT_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    /**
     * Initial safe default model used for Real-time Voice and Camera (Live mode).
     * gemini-3.1-flash-live-preview delivers superior real-time speech prosody, sub-second latency, and native tools.
     *
     * ใช้เฉพาะก่อน sync กับ ListModels — หลัง sync ระบบจะจัดอันดับจากโมเดล bidi จริงที่ project มองเห็น
     * (โมเดล Live ใหม่ๆ จะถูกใช้เองโดยไม่ต้องแก้โค้ด)
     */
    const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"

    /** โมเดล Live ที่ใช้เมื่อผู้ใช้ขอให้ "คิดลึก" — ช้ากว่าแต่เหตุผลแน่นกว่า */
    const val DEEP_THINKING_LIVE_MODEL = "gemini-3.8-live-extended-thinking"

    // ─── Free tier reality check (ตรวจกับ AI Studio console 2026-09-16) ─────────
    // • RPD ของโมเดล text-out ตระกูล flash = 20/วัน ต่อโมเดล, flash-lite = 500/วัน
    // • Live API: RPD ไม่จำกัด แต่จำกัด TPM (65K สำหรับตระกูล Live รุ่นใหม่)
    // • โควตาเป็น "ต่อ project" ไม่ใช่ต่อ key — key หลายใบใน project เดียวกันไม่ได้เพิ่ม RPD
    // • RPD รีเซ็ตเที่ยงคืน Pacific time (เอกสาร rate-limits)
    /** โมเดลที่โควตา "ต่อวัน" หมดแล้ว → ดันไปท้าย chain จนกว่าจะรีเซ็ต */
    private val _quotaExhaustedModels = mutableMapOf<String, Long>()

    // Seed list — ใช้ก่อน sync กับ API เท่านั้น; ตระกูล flash-lite มาก่อนเสมอเพราะโควตาต่อวันสูงกว่า 25 เท่า
    private val SEED_FALLBACK_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
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

        // จัดลำดับความสำคัญ (free tier):
        // 1. flash-lite — 500 req/วัน ต่อโมเดล คือสายหลักของแอป
        // 2. flash — 20 req/วัน เก็บไว้เป็นตัวเลือกให้ผู้ใช้เลือกเองใน Settings เท่านั้น
        // ภายในกลุ่มเดียวกันเรียงตามเวอร์ชันใหม่ -> เก่า
        val sortedChatModels = filteredChatModels.sortedWith(
            compareByDescending<String> { isChatAllowedModel(it) }
                .thenByDescending { isFlashModel(it) }
                .thenByDescending { extractModelVersion(it) }
        )

        // Live models: ใช้ bidiGenerateContent จริงจาก API เป็นหลัก แล้วค่อยเติม seed ที่รู้จัก
        // (เดิม hardcode 4 ตัวไว้หน้าสุด โมเดล Live ใหม่ที่ Google เพิ่มเข้ามาจึงไม่เคยถูกเลือกเลย)
        val liveCandidates = (bidiModels + SEED_LIVE_MODELS)
            .distinct()
            .filter { isConversationalLiveModel(it) }
            .sortedWith(
                // คุยโต้ตอบได้ก่อน → เวอร์ชันใหม่ก่อน → ตัว extended thinking ไว้ท้าย (latency สูงกว่าสำหรับสนทนาสด)
                compareByDescending<String> { extractModelVersion(it) }
                    .thenBy { it.lowercase().contains("thinking") }
                    .thenBy { it.lowercase().contains("native-audio") }
            )

        synchronized(dynamicLock) {
            _dynamicGeminiModels = sortedChatModels
            _dynamicLiveModels = liveCandidates
        }

        com.skyliner2008.jarvis.logDebug(
            "ModelConfig",
            "Updated dynamic models from API (${sortedChatModels.size} chat, ${liveCandidates.size} live) | chat=${sortedChatModels.take(5)} | live=$liveCandidates"
        )
    }

    /**
     * Seed สำหรับ Live — ใช้เฉพาะก่อน sync ListModels เท่านั้น
     * (2026-09-16: ตัด `gemini-2.0-flash-exp` และ `...native-audio-preview-09-2025` ที่ปิดบริการ/ไม่อยู่ใน console แล้ว
     *  และเลิกกันโมเดล native audio 12-2025 ซึ่งตอนนี้คือ "Gemini 2.5 Flash Native Audio Dialog" ที่ใช้ได้บน free tier)
     */
    val SEED_LIVE_MODELS = listOf(
        "gemini-3.8-live",                                // ใหม่สุด 65K TPM, RPD ไม่จำกัด
        "gemini-3.1-flash-live-preview",                  // ตัวที่ผ่านการใช้งานจริงมาแล้ว
        "gemini-3.8-live-extended-thinking",              // เหตุผลแน่นกว่า แต่ latency สูงกว่า
        "gemini-2.5-flash-native-audio-preview-12-2025"   // native audio dialog — สำรองสุดท้าย
    )

    /** โมเดล Live เฉพาะทาง — ใช้เฉพาะโหมดของมัน ไม่เอามาเป็นผู้ช่วยทั่วไป */
    const val TRANSLATE_LIVE_MODEL = "gemini-3.5-live-translate-preview"
    const val TRANSCRIBE_LIVE_MODEL = "gemini-3.5-transcribe-live"

    /**
     * Live model ที่ "คุยโต้ตอบ" ได้จริง — ตัดรุ่นเฉพาะทางที่เปิด WebSocket ได้แต่ใช้เป็นผู้ช่วยไม่ได้
     * (transcribe = แปลงเสียงเป็นข้อความอย่างเดียว, live-translate = แปลภาษาอย่างเดียว, tts = อ่านออกเสียงอย่างเดียว)
     */
    fun isConversationalLiveModel(modelId: String): Boolean {
        val lc = modelId.removePrefix("models/").lowercase()
        if (lc.isBlank()) return false
        if (lc.contains("transcribe") || lc.contains("translate") || lc.contains("tts")) return false
        if (lc.contains("embedding") || lc.contains("image") || lc.contains("banana")) return false
        return lc.contains("live") || lc.contains("native-audio") || lc.contains("realtime") || lc.contains("dialog")
    }

    /**
     * โควตารายวันของโมเดลนี้หมดแล้ว (429 แบบ PerDay) — ดันไปท้าย chain จนกว่าจะรีเซ็ต
     * free tier: flash = 20 req/วัน, flash-lite = 500 req/วัน, รีเซ็ตเที่ยงคืน Pacific
     */
    fun markModelQuotaExhausted(modelName: String, dailyQuota: Boolean) {
        val clean = modelName.removePrefix("models/").trim()
        if (clean.isBlank()) return
        val until = if (dailyQuota) nextPacificResetMs() else System.currentTimeMillis() + 60_000L
        synchronized(dynamicLock) { _quotaExhaustedModels[clean] = until }
        val minutesLeft = (until - System.currentTimeMillis()) / 60_000L
        com.skyliner2008.jarvis.logDebug(
            "ModelConfig",
            "🪫 Model '$clean' quota exhausted (${if (dailyQuota) "daily" else "per-minute"}) — deprioritized for ~${minutesLeft} min"
        )
    }

    fun isModelQuotaExhausted(modelName: String): Boolean {
        val clean = modelName.removePrefix("models/").trim()
        val now = System.currentTimeMillis()
        return synchronized(dynamicLock) {
            val until = _quotaExhaustedModels[clean] ?: return@synchronized false
            if (until > now) true else {
                _quotaExhaustedModels.remove(clean)
                false
            }
        }
    }

    /** โมเดลที่ใช้เป็นสายแชทได้บน free tier — ตระกูล lite เท่านั้น (โควตาต่อวันสูงพอ) */
    fun isChatAllowedModel(modelId: String): Boolean {
        val clean = modelId.removePrefix("models/").lowercase()
        if (clean in CHAT_MODELS) return true
        return clean.contains("lite") && clean.startsWith("gemini-") && !isLiveModel(clean)
    }

    /**
     * โควตารายวันของโมเดลแชทหลักหมดทั้งหมดหรือยัง — ใช้แจ้งผู้ใช้แทนการปล่อยให้ error ดิบๆ
     * @return ข้อความแจ้งเตือน หรือ null ถ้ายังใช้ได้
     */
    fun chatQuotaExhaustedMessage(): String? {
        val usable = getFallbackChain().filter { !isModelQuotaExhausted(it) }
        if (usable.isNotEmpty()) return null
        return "⚠️ โควตาแชทรายวันของ Gemini free tier หมดแล้ว (flash-lite 500 ครั้ง/วัน ต่อโมเดล) " +
            "โควตาจะรีเซ็ตเที่ยงคืนเวลา Pacific (ประมาณ 14:00–15:00 ตามเวลาไทย) — " +
            "ระหว่างนี้ยังใช้โหมดเสียง (Live) ได้ตามปกติเพราะไม่จำกัดจำนวนครั้ง"
    }

    /**
     * ชื่อที่อ่านง่ายจาก model id — ใช้แทน label ที่ hardcode ไว้ (ชื่อโมเดลเปลี่ยนบ่อย)
     * เช่น "gemini-3.8-flash" → "Gemini 3.8 Flash", "gemini-3.1-flash-live-preview" → "Gemini 3.1 Flash Live (preview)"
     */
    fun displayNameFor(modelId: String): String {
        val clean = modelId.removePrefix("models/").trim()
        if (clean.isBlank()) return modelId
        val isPreview = clean.contains("preview")
        val words = clean.split("-").filter { it.isNotBlank() && it != "preview" }
        val pretty = words.joinToString(" ") { part ->
            when {
                part.equals("gemini", true) -> "Gemini"
                part.equals("tts", true) -> "TTS"
                part.firstOrNull()?.isDigit() == true -> part
                else -> part.replaceFirstChar { it.uppercase() }
            }
        }
        return if (isPreview) "$pretty (preview)" else pretty
    }

    /**
     * โมเดลสำหรับงานปริมาณมาก/เบื้องหลัง บน free tier
     * — ตระกูล flash-lite มีโควตา 500 req/วัน เทียบกับ flash ที่มีแค่ 20 req/วัน
     */
    fun getHighVolumeModel(): String {
        val pool = getActiveGeminiModels()
        return pool.firstOrNull { it.contains("lite", ignoreCase = true) && !isModelQuotaExhausted(it) }
            ?: pool.firstOrNull { it.contains("lite", ignoreCase = true) }
            ?: SEED_FALLBACK_MODELS.first { it.contains("lite") }
    }

    /** เที่ยงคืนถัดไปตามเวลา Pacific — เวลารีเซ็ต RPD ของ Gemini API */
    private fun nextPacificResetMs(): Long {
        val zone = runCatching { kotlinx.datetime.TimeZone.of("America/Los_Angeles") }
            .getOrElse { kotlinx.datetime.TimeZone.UTC }
        val now = kotlinx.datetime.Clock.System.now()
        val localDate = now.toLocalDateTime(zone).date
        val nextMidnight = localDate.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
            .atStartOfDayIn(zone)
        return nextMidnight.toEpochMilliseconds()
    }

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
        com.skyliner2008.jarvis.logDebug("ModelConfig", "🚀 Promoted healthy live model: '$clean' to top priority")
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
        com.skyliner2008.jarvis.logDebug("ModelConfig", "⏳ Penalized live model '$clean' for ${durationMs / 1000}s due to timeout/failure")
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
        // 1. โมเดลที่ผู้ใช้เลือกเองใน Settings มาก่อนเสมอ (แม้จะไม่ใช่ lite ก็เคารพการตั้งค่า)
        if (!primaryClean.isNullOrBlank() && !isModelDead(primaryClean)) {
            result.add(primaryClean)
        }
        // 2. โมเดลแชทที่อนุญาต: flash-lite เท่านั้น (500 req/วัน) โดย 2 ตัวหลักมาก่อน
        for (m in CHAT_MODELS) {
            if (m !in result && !isModelDead(m)) result.add(m)
        }
        for (m in pool) {
            if (m !in result && isChatAllowedModel(m)) result.add(m)
        }
        // ตระกูล flash (20 req/วัน) ไม่ถูกใส่ในสายแชท — งานวนลูปใช้ automation engine ของแอปแทน
        if (result.isEmpty()) return listOf(DEFAULT_MAIN_MODEL)
        // โมเดลที่โควตารายวันหมดแล้วถูกดันไปท้าย (ไม่ตัดทิ้ง — เผื่อโควตารีเซ็ตเร็วกว่าที่คำนวณ)
        // free tier: flash 20 req/วัน หมดเร็วมาก ถ้าไม่ลดลำดับจะเสียเวลาไล่ยิงตัวที่ตายไปแล้วทุกครั้ง
        val (available, exhausted) = result.partition { !isModelQuotaExhausted(it) }
        return available + exhausted
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
        com.skyliner2008.jarvis.logError(
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
            _quotaExhaustedModels.clear()
        }
    }
}
