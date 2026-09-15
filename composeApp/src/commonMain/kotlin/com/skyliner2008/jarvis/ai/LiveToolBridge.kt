package com.skyliner2008.jarvis.ai

import com.skyliner2008.jarvis.data.ConversationTurn
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.data.LiveGeminiService
import com.skyliner2008.jarvis.data.LiveToolCallEvent
import com.skyliner2008.jarvis.drive.DriveBridge
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.tools.ToolCall
import com.skyliner2008.jarvis.tools.ToolExecutor
import com.skyliner2008.jarvis.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LiveToolBridge(
    private val liveService: LiveGeminiService,
    private val geminiService: GeminiService,
    private val memoryManager: com.skyliner2008.jarvis.memory.JarvisMemoryManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    var onAiVisionToggle: ((Boolean) -> Unit)? = null,
    var onVoiceChange: ((String) -> Unit)? = null
) {
    private val _activeToolName = MutableStateFlow<String?>(null)
    val activeToolName: StateFlow<String?> = _activeToolName.asStateFlow()

    private val _lastToolResult = MutableStateFlow<Pair<String, String>?>(null)
    val lastToolResult: StateFlow<Pair<String, String>?> = _lastToolResult.asStateFlow()

    fun clearLastToolResult() {
        _lastToolResult.value = null
    }

    private var collectionJob: Job? = null
    private var visionPromptJob: Job? = null

    // Trading AI Profile guard: keep one analysis family per user turn and only M15/H1/H4 by default.
    private var tradingProfilePromptKey: String = ""
    private var tradingProfileCallCount: Int = 0

    private fun tradingProfileFor(prompt: String): String {
        val p = prompt.lowercase()
        return when {
            p.contains("smc") || p.contains("smart money") || p.contains("order block") || p.contains("fvg") -> "SMC"
            p.contains("rsi") || p.contains("ema") || p.contains("sma") || p.contains("atr") ||
                p.contains("แนวรับ") || p.contains("แนวต้าน") || p.contains("support") || p.contains("resistance") ||
                p.contains("เท่าไร") || p.contains("เท่าไหร่") -> "USER_QUERY"
            p.contains("วิเคราะห์") || p.contains("analysis") || p.contains("overview") ||
                p.contains("ภาพรวม") || p.contains("5 มิติ") || p.contains("5มิติ") || p.contains("confluence") -> "AI"
            else -> "NONE"
        }
    }

    private fun isTradingAnalysisTool(name: String): Boolean = name in setOf(
        "trading_deep_analysis_suite",
        "trading_technical_analysis",
        "trading_smc_analysis"
    )

    private fun allowedTradingTimeframe(args: Map<String, String>): Boolean {
        val raw = args["symbol"] ?: args["timeframe"] ?: ""
        val tf = raw.substringAfter("@", "").lowercase().ifBlank { "1h" }
        return tf in setOf("15m", "m15", "1h", "h1", "4h", "h4")
    }

    private fun profileToolAllowed(prompt: String, toolName: String, args: Map<String, String>): Boolean {
        val profile = tradingProfileFor(prompt)
        if (profile == "NONE" || !isTradingAnalysisTool(toolName)) return true
        if (!allowedTradingTimeframe(args)) return false
        return when (profile) {
            "SMC" -> toolName == "trading_smc_analysis"
            // "RSI / แนวรับ / เท่าไหร่" needs real indicator values: one technical analysis, no deep suite
            "USER_QUERY" -> toolName == "trading_technical_analysis"
            else -> toolName == "trading_deep_analysis_suite"
        }
    }

    private fun isSignalAlertRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val alertTerms = listOf("แจ้งเตือน", "signal alert", "signal", "สัญญาณ")
        val tradeTerms = listOf("ทอง", "gold", "xau", "buy", "sell", "ซื้อ", "ขาย")
        return alertTerms.any { p.contains(it) } && tradeTerms.any { p.contains(it) }
    }

    /** คำขอเรื่องตลาด/เทรดจริง — guard ที่เปลี่ยน trading tool เป็นอย่างอื่นต้องไม่ทำงาน */
    private fun isTradingQuestion(prompt: String): Boolean =
        TradingIntentUtility.isTradingPrompt(prompt) || TradingIntentUtility.isMt5Prompt(prompt) ||
            TradingIntentUtility.isSmcPrompt(prompt) ||
            listOf("ราคา", "หุ้น", "ตลาด", "กราฟ", "เทรด", "xau", "btc", "บิทคอยน์", "ดัชนี", "set50", "nasdaq", "ดาวโจนส์")
                .any { prompt.lowercase().contains(it) }

    private fun isAvatarEmotionRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        // ("หน้า" / "แบบ" / "mood" alone matched ordinary sentences like "แบบไหนดี" or "หน้าจอ")
        val avatarTerms = listOf(
            "หน้าที่", "แบบที่", "moodset",
            "เดโม", "เดโม่", "demo",
            "แสดงอารมณ์", "โชว์อารมณ์", "ทดสอบอารมณ์", "อารมณ์ทั้งหมด",
            "ซะแดงเดโมอารมณ์", "แสดงเดโม่อารมณ์", "ซะแดงอารมณ์",
            "ทำหน้า", "สีหน้า", "avatar", "อวาตาร์", "ขยิบตา", "ยิ้มหน่อย", "หน้าตา",
            "ทุกหน้า", "ทุกแบบ", "หน้าทั้งหมด", "all pages", "all moods", "play all", "show all"
        )
        return avatarTerms.any { p.contains(it) } ||
                com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(prompt) != null ||
                com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(prompt)
    }

    private fun isSceneRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val sceneTerms = listOf(
            "ฉาก", "กินข้าว", "ให้อาหาร", "กินพิซซ่า", "กินเบอร์เกอร์", "กินเค้ก", "กินไอติม", "กินป๊อปคอร์น", "หิวข้าว",
            "ดื่มน้ำ", "ขอดื่ม", "กินกาแฟ", "ดื่มกาแฟ", "กินชา", "ดื่มชา", "กินโค้ก", "กินชานม", "หิวน้ำ",
            "อาบน้ำ", "ถูสบู่", "แปรงฟัน", "สระผม",
            "เล่นเกม", "จอยเกม", "ทำงาน", "อ่านหนังสือ",
            // (no "ทองคำ" / "เหรียญทอง" / "คนรวย": those are gold-price questions far more often than scenes)
            "ใส่แว่น", "แว่นตา", "thug life", "แว่นดำ", "มงกุฎ", "ราชา", "เจ้าหญิง",
            "ไฟลุก", "วิ่งหนีไฟ", "ไฟไหม้", "โดนช็อต", "ฟ้าผ่า", "ไฟดูด", "วิญญาณหลุด", "เหนื่อยมาก", "หมดแรง", "ตายแป๊บ",
            "ยิงจรวด", "มิสไซล์", "ถล่มจอ", "ซุปเปอร์เลิฟ", "คลั่งรัก", "หัวใจเต็มจอ", "อกหัก", "ร้องไห้หนักมาก", "ปาร์ตี้", "ฉลอง"
        )
        return sceneTerms.any { p.contains(it) }
    }

    private fun isAlwaysLiveRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "โหมดควบคุม", "โหมดขับขี่", "โหมดรถยนต์", "โหมดสัตว์เลี้ยง",
            "เปิดโหมดควบคุม", "เปิดโหมดขับขี่", "เปิดโหมดรถยนต์", "เปิดโหมดสัตว์เลี้ยง",
            "เข้าโหมดควบคุม", "เข้าโหมดขับขี่", "เข้าโหมดรถยนต์", "เข้าโหมดสัตว์เลี้ยง",
            "โหมด always", "always live", "drive mode", "car mode", "pet mode"
        )
        return terms.any { p.contains(it) }
    }

    private fun isMediaRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "เปิดเพลง", "เล่นเพลง", "หยุดเพลง", "ข้ามเพลง", "เพลงถัดไป", "เพลงก่อนหน้า",
            "เพลงอะไร", "พักเพลง", "สลับเพลง", "play music", "stop music", "next song", "previous song"
        )
        return terms.any { p.contains(it) }
    }

    private fun isNavigationRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "นำทางไป", "นำทาง", "เปิดแผนที่ไป", "เปิด google maps ไป", "พาไปที่", "ไปที่",
            "navigate to", "directions to"
        )
        return terms.any { p.contains(it) }
    }

    private fun isNotificationRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "อ่านแจ้งเตือน", "อ่านข้อความ", "มีแจ้งเตือนอะไร", "เช็คแจ้งเตือน", "มีไลน์เข้าไหม",
            "read notifications", "read notification", "check notifications"
        )
        return terms.any { p.contains(it) }
    }

    private fun isLocationOrSpeedRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "ขับเร็วเท่าไหร่", "ความเร็วเท่าไหร่", "วิ่งเร็วเท่าไหร่", "ตอนนี้อยู่ที่ไหน",
            "พิกัดปัจจุบัน", "เช็คตำแหน่ง", "ตำแหน่งปัจจุบัน", "current speed", "where am i"
        )
        return terms.any { p.contains(it) }
    }

    private fun isParkingRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "จอดรถอยู่ที่ไหน", "จอดรถไว้ตรงไหน", "รถจอดอยู่ที่ไหน", "รถจอดที่ไหน",
            "หาที่จอดรถ", "รถอยู่ไหน", "จำที่จอดรถ", "บันทึกที่จอดรถ", "บันทึกจุดจอด",
            "จอดรถตรงนี้", "where did i park", "where is my car", "save parking", "remember parking"
        )
        return terms.any { p.contains(it) }
    }

    private fun isNightModeRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "เปิดโหมดกลางคืน", "ปิดโหมดกลางคืน", "โหมดกลางคืน", "ลดแสงสะท้อน", "หรี่แสง",
            "night mode", "low glare"
        )
        return terms.any { p.contains(it) }
    }

    private suspend fun handleNativeToolCall(event: LiveToolCallEvent, memoryContext: String = "") {
        logDebug("LiveBridge", "▶ Path A: ${event.name} callId=${event.callId} (${event.args})")

        // Enforce the profile selected from the user's actual request before executing tools.
        // This prevents the Live model from expanding one analysis request into Deep+SMC+TA+D1 chains.
        val userPrompt = liveService.lastUserText
        val turnKey = "turn-${liveService.userTurnSerial}"
        if (turnKey != tradingProfilePromptKey) {
            tradingProfilePromptKey = turnKey
            tradingProfileCallCount = 0
        }

        // Always Live Guard: If Gemini mistakenly calls trading tools when user meant Always Live / Control / Drive mode
        val tradingQuestion = isTradingQuestion(userPrompt)
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price") && !tradingQuestion && isAlwaysLiveRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val pWithoutOpen = p.replace("เปิด", "")
            val action = if (pWithoutOpen.contains("ปิด") || pWithoutOpen.contains("ออก") || pWithoutOpen.contains("off") || pWithoutOpen.contains("stop")) "off" else "on"
            val mode = when {
                p.contains("สัตว์เลี้ยง") || p.contains("pet") -> "pet"
                p.contains("ขับขี่") || p.contains("drive") -> "drive"
                p.contains("รถยนต์") || p.contains("car") -> "car"
                else -> "control"
            }
            val redirectCall = ToolCall(name = "device_always_live", args = mapOf("action" to action, "mode" to mode))
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_always_live(action=$action, mode=$mode)")
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected always live execution failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_always_live", "Error: ${e.message}", true)
            }
            val voiceGuide = if (mode == "pet") {
                "\n\n[VOICE RULE - PET MODE] สลับเข้าสู่โหมดสัตว์เลี้ยงตั้งโต๊ะ (Virtual Desk Pet) แล้ว! — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารักสดใส เป็นธรรมชาติ เช่น 'เข้าโหมดสัตว์เลี้ยงแล้วฮับ พร้อมเล่นกับเจ้านายแล้ว!' (ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ เด็ดขาด) ห้ามตอบเป็นทางการ ห้ามใช้ markdown"
            } else {
                "\n\n[VOICE RULE - ALWAYS LIVE] สลับโหมดควบคุม/โหมดขับขี่/Always AI Live เรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยคอย่างมั่นใจและกระชับ (เช่น 'เข้าสู่โหมดควบคุมแล้วค่ะ พร้อมรับคำสั่งตลอดเวลา' หรือ 'เปิดโหมดขับขี่เรียบร้อยแล้วค่ะ เดินทางปลอดภัยนะคะ') ห้ามอธิบายยาว ห้ามใช้ markdown"
            }
            liveService.sendNativeToolResponse(
                callId   = event.callId,
                toolName = event.name,
                result   = result.result + voiceGuide
            )
            _activeToolName.value = null
            return
        }

        // Avatar Emotion & Smart Scene Guard: If Gemini mistakenly calls Fear & Greed or Sentiment when user meant Avatar face or Scene
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price") && !tradingQuestion && (isAvatarEmotionRequest(userPrompt) || isSceneRequest(userPrompt))) {
            val p = userPrompt.lowercase()
            val isScene = isSceneRequest(userPrompt)
            val args = if (isScene) {
                mapOf("action" to "scene", "scene" to userPrompt)
            } else {
                val targetPage = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(userPrompt)
                val isPlayAll = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(userPrompt)
                when {
                    targetPage != null -> mapOf("action" to "page", "page" to targetPage.toString())
                    isPlayAll -> mapOf("action" to "all")
                    p.contains("รีเซ็ต") || p.contains("ปกติ") || p.contains("reset") -> mapOf("action" to "reset")
                    p.contains("เดโม") || p.contains("demo") || p.contains("แสดงอารมณ์") || p.contains("โชว์อารมณ์") || p.contains("อารมณ์ทั้งหมด") || p.contains("ทดสอบอารมณ์") -> mapOf("action" to "all")
                    else -> {
                        val emotion = when {
                            p.contains("ดีใจ") || p.contains("happy") || p.contains("ยิ้ม") -> "happy"
                            p.contains("ตื่นเต้น") || p.contains("excited") -> "excited"
                            p.contains("รัก") || p.contains("love") || p.contains("หัวใจ") -> "love"
                            p.contains("โกรธ") || p.contains("angry") || p.contains("โมโห") -> "angry"
                            p.contains("เศร้า") || p.contains("sad") || p.contains("เสียใจ") || p.contains("ร้องไห้") -> "sad"
                            p.contains("หลับ") || p.contains("sleeping") || p.contains("ง่วง") || p.contains("นอน") -> "sleeping"
                            p.contains("คิด") || p.contains("thinking") || p.contains("สงสัย") -> "thinking"
                            else -> "happy"
                        }
                        mapOf("action" to "set", "emotion" to emotion)
                    }
                }
            }
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_avatar_emotion($args)")
            val redirectCall = ToolCall(name = "device_avatar_emotion", args = args)
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected avatar emotion execution failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_avatar_emotion", "Error: ${e.message}", true)
            }
            val voiceRule = if (isScene) {
                "\n\n[VOICE RULE - PET SCENE] เริ่มเล่นฉากอนิเมชันและเอฟเฟกต์บนหน้าจอเรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารักสดใสและมีอารมณ์ร่วมตามฉากที่เล่น เช่น กินอาหาร ดื่มน้ำ ยิงจรวด หรือใส่แว่นตา (ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ เด็ดขาด) ห้ามใช้ markdown"
            } else {
                val page = args["page"]?.toIntOrNull()
                val isAll = args["action"] in listOf("all", "demo")
                if (page != null) {
                    val item = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.findByPage(page)
                    "\n\n[VOICE RULE - MOODSET PAGE] แสดง Moodset หน้าที่ $page: ${item?.nameEn} (${item?.nameTh}) บนหน้าจอ 3-5 วินาทีเรียบร้อยแล้วค่ะ (ระบบมีสารบัญครบทั้ง 50 หน้า แผ่นที่ 1: หน้า 1-20, แผ่นที่ 2: หน้า 21-50) — โปรดตอบรับสั้นๆ 1 ประโยคอย่างสดใส เช่น 'แสดงหน้าที่ $page ${item?.nameTh} ให้บอสตรวจสอบแล้วค่ะ' ห้ามบอกว่าไม่มีหน้าที่ 11 หรือมีแค่ 10 หน้าเด็ดขาด ห้ามใช้ markdown"
                } else if (isAll) {
                    "\n\n[VOICE RULE - MOODSET ALL] เริ่มเล่นแสดง Moodset ครบทั้งหมด 50 หน้าวนตรวจบนหน้าจอสลับทุก 4 วินาทีเรียบร้อยแล้วค่ะ — โปรดตอบรับสั้นๆ 1 ประโยคอย่างสดใส เช่น 'เริ่มแสดง Moodset ทุกหน้าครบทั้ง 50 หน้าให้บอสตรวจสอบแล้วนะคะ' ห้ามบอกว่ามีแค่ 10 หน้า ห้ามใช้ markdown"
                } else {
                    "\n\n[VOICE RULE - AVATAR EMOTION] แสดงสีหน้า Avatar บนหน้าจอเรียบร้อยแล้ว (ระบบมี Moodset ทั้งหมด 50 หน้า) — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารัก สดใส และเป็นธรรมชาติ ห้ามตอบว่าไม่มีหน้าตา ห้ามพูดว่ามีแค่ 10 หน้า ห้ามใช้ markdown"
                }
            }
            liveService.sendNativeToolResponse(
                callId   = event.callId,
                toolName = event.name,
                result   = result.result + voiceRule
            )
            _activeToolName.value = null
            return
        }

        // Media Control Guard: If Gemini mistakenly calls trading/other tools when user wants music control
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && !tradingQuestion && isMediaRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val action = when {
                p.contains("หยุด") || p.contains("pause") || p.contains("พัก") -> "pause"
                p.contains("ข้าม") || p.contains("ถัดไป") || p.contains("next") -> "next"
                p.contains("ก่อนหน้า") || p.contains("ย้อน") || p.contains("previous") -> "previous"
                p.contains("เพลงอะไร") || p.contains("ชื่อเพลง") || p.contains("now playing") -> "now_playing"
                else -> "play"
            }
            val query = if (action == "play") {
                p.replace("เปิดเพลง", "").replace("เล่นเพลง", "").replace("play", "").trim()
            } else ""
            val args = buildMap {
                if (query.isNotBlank()) {
                    put("action", "search_play")
                    put("query", query)
                } else {
                    put("action", action)
                }
            }
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_media_control($args)")
            val redirectCall = ToolCall(name = "device_media_control", args = args)
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected media control execution failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_media_control", "Error: ${e.message}", true)
            }
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = result.result + "\n\n[VOICE RULE - MEDIA] ควบคุมการเล่นเพลงเรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยคอย่างกระชับ เช่น 'กำลังเล่นเพลงให้แล้วนะคะ' หรือ 'หยุดเล่นเพลงแล้วค่ะ' ห้ามอธิบายยาว ห้ามใช้ markdown"
            )
            _activeToolName.value = null
            return
        }

        // Navigation Guard: If Gemini mistakenly calls trading tools when user asks for navigation
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && isNavigationRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val destination = p.replace("นำทางไป", "")
                .replace("เปิดแผนที่ไป", "")
                .replace("เปิด google maps ไป", "")
                .replace("พาไปที่", "")
                .replace("ไปที่", "")
                .replace("navigate to", "")
                .replace("directions to", "")
                .trim()
            val args = mapOf("destination" to destination, "action" to "navigate", "mode" to "drive")
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_navigate($args)")
            val redirectCall = ToolCall(name = "device_navigate", args = args)
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected navigate execution failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_navigate", "Error: ${e.message}", true)
            }
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = result.result + "\n\n[VOICE RULE - NAVIGATION] เปิดระบบนำทางไปยัง $destination เรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยค เช่น 'เปิดระบบนำทางไป $destination ให้แล้วค่ะ เดินทางปลอดภัยนะคะ' ห้ามอธิบายยาว ห้ามใช้ markdown"
            )
            _activeToolName.value = null
            return
        }

        // Notification Read Guard: If Gemini mistakenly calls trading tools when user asks for notifications
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && isNotificationRequest(userPrompt)) {
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_notification_read")
            val redirectCall = ToolCall(name = "device_notification_read", args = mapOf("count" to "3"))
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected notification read failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_notification_read", "Error: ${e.message}", true)
            }
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = result.result + "\n\n[VOICE RULE - NOTIFICATIONS] อ่านการแจ้งเตือนล่าสุดเรียบร้อยแล้ว — สรุปหรือแจ้งเตือนสั้นๆ ให้ผู้ใช้ทราบอย่างกระชับและเป็นธรรมชาติ"
            )
            _activeToolName.value = null
            return
        }

        // Location & Speed Guard: If Gemini mistakenly calls trading tools when user asks for speed / current location
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && isLocationOrSpeedRequest(userPrompt)) {
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_location")
            val redirectCall = ToolCall(name = "device_location", args = mapOf("action" to "get_current"))
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected location failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_location", "Error: ${e.message}", true)
            }
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = result.result + "\n\n[VOICE RULE - LOCATION] ได้ข้อมูลพิกัด/ตำแหน่งเรียบร้อยแล้ว — ตอบความเร็วหรือตำแหน่งปัจจุบันให้ผู้ใช้ทราบอย่างกระชับและชัดเจน"
            )
            _activeToolName.value = null
            return
        }

        // Parking Location Guard: If Gemini mistakenly calls trading tools when user asks about parking
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && isParkingRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val isSave = listOf("จำ", "บันทึก", "ตรงนี้", "save", "remember").any { p.contains(it) }
            val responseText = if (isSave) {
                DriveBridge.saveCurrentParking()
                val saved = DriveBridge.parkingLocation.value
                val addr = saved?.address ?: "พิกัดปัจจุบัน"
                "บันทึกพิกัดจุดจอดรถเรียบร้อยแล้วค่ะ ที่ $addr เมื่อต้องการกลับมาที่รถ ให้ถามว่า 'รถจอดอยู่ที่ไหน' ได้ตลอดนะคะ\n\n[VOICE RULE - PARKING] บันทึกจุดจอดรถเรียบร้อยแล้ว — ตอบยืนยันสั้นๆ 1 ประโยคอย่างเป็นธรรมชาติ"
            } else {
                val parking = DriveBridge.parkingLocation.value
                if (parking != null) {
                    val addr = parking.address ?: "พิกัดที่บันทึกไว้ (${parking.latitude}, ${parking.longitude})"
                    "รถของคุณจอดอยู่ที่ $addr ค่ะ สามารถกดปุ่มนำทางกลับไปที่รถบนหน้าจอได้ทันที\n\n[VOICE RULE - PARKING] บอกตำแหน่งจุดจอดรถให้ผู้ใช้ทราบสั้นๆ 1-2 ประโยคอย่างชัดเจนและเป็นมิตร"
                } else {
                    "ยังไม่มีข้อมูลจุดจอดรถที่บันทึกไว้ค่ะ สามารถพูดว่า 'จำที่จอดรถ' เพื่อบันทึกพิกัดปัจจุบันได้นะคะ\n\n[VOICE RULE - PARKING] แจ้งว่ายังไม่ได้บันทึกจุดจอดรถ และแนะนำให้บันทึกสั้นๆ 1 ประโยค"
                }
            }
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Handled Parking Intent: $responseText")
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = responseText
            )
            _activeToolName.value = null
            return
        }

        // Night / Low-Glare Driving Mode Guard
        if (event.name in setOf("trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators") && !tradingQuestion && isNightModeRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val isTurnOff = listOf("ปิด", "ยกเลิก", "off", "disable").any { p.contains(it) }
            DriveBridge.setLowGlareMode(!isTurnOff)
            val msg = if (isTurnOff) "ปิดโหมดกลางคืนและปรับความสว่างปกติแล้วค่ะ" else "เปิดโหมดลดแสงสะท้อนสำหรับการขับขี่ตอนกลางคืนเรียบร้อยแล้วค่ะ"
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Handled Low Glare Mode: $msg")
            liveService.sendNativeToolResponse(
                callId = event.callId,
                toolName = event.name,
                result = "$msg\n\n[VOICE RULE - NIGHT_MODE] ยืนยันการปรับโหมดลดแสงสะท้อนสำหรับการขับขี่สั้นๆ 1 ประโยค"
            )
            _activeToolName.value = null
            return
        }

        // Always Live Off Guard: Protect against hallucinated close commands when user didn't ask to exit
        if (event.name == "device_always_live") {
            val action = event.args["action"]?.lowercase()?.trim() ?: "on"
            if (action in setOf("off", "ปิด", "stop", "disable", "exit", "ออก", "close")) {
                val p = userPrompt.lowercase()
                val pWithoutOpen = p.replace("เปิด", "")
                val isExplicitUserClose = listOf(
                    "ปิด", "ออก", "เลิก", "พอแล้ว", "หยุด", "บาย", "พักผ่อน", "นอนได้แล้ว",
                    "off", "stop", "exit", "close", "quit", "bye", "shutdown", "disable"
                ).any { pWithoutOpen.contains(it) }

                if (!isExplicitUserClose) {
                    logDebug("LiveBridge", "🛡️ Blocked hallucinated device_always_live(action=off) — userPrompt='$userPrompt'")
                    val activeMode = if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) "สัตว์เลี้ยง" else "Always AI Live"
                    liveService.sendNativeToolResponse(
                        callId   = event.callId,
                        toolName = event.name,
                        result   = "โหมด$activeMode ยังคงเปิดทำงานอยู่ตามปกติค่ะ (ผู้ใช้ไม่ได้สั่งให้ปิดโหมด หากต้องการปิดกรุณาสั่ง 'ปิดโหมด' ชัดเจนนะคะ)\n\n[VOICE RULE] โหมด$activeMode ยังคงทำงานอยู่ตามปกติ — ให้ตอบรับหรือช่วยเหลือผู้ใช้ตามคำพูดล่าสุด ('$userPrompt') อย่างเป็นธรรมชาติ ห้ามบอกว่าปิดโหมดแล้วเด็ดขาด"
                    )
                    _activeToolName.value = null
                    return
                }
            }
        }

        // Vision Activate Guard: Prevent camera opening if user did not ask to see/look
        if (event.name == "vision_activate") {
            val p = userPrompt.lowercase().trim()
            val hasVisionIntent = listOf(
                "ดู", "มอง", "เห็น", "กล้อง", "ตา", "ตรวจ", "ส่อง", "อ่าน", "เช็คภาพ", "ภาพ", "รูป",
                "นิ้ว", "มือ", "ชู", "อันนี้", "อันไหน", "นี่", "นี้", "ตรงนี้", "คืออะไร", "อะไร",
                "กี่", "สี", "ตัวไหน", "คนไหน", "เสื้อ", "แว่น", "ถือ", "ใส่", "ทำท่า", "ท่าทาง",
                "ใคร", "ไหน", "เท่าไหร่", "นับ", "ชี้", "เขียนว่า",
                "see", "look", "watch", "camera", "eye", "vision", "view", "read", "scan", "photo", "pic",
                "finger", "hand", "hold", "wear", "color", "how many", "what", "where", "who", "count"
            ).any { p.contains(it) }

            if (userPrompt.isNotBlank() && !hasVisionIntent) {
                logDebug("LiveBridge", "🛡️ Blocked hallucinated vision_activate — userPrompt='$userPrompt'")
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "EYES_NOT_NEEDED: ผู้ใช้ไม่ได้สั่งให้เปิดกล้องหรือมองดูสิ่งใด (คำพูดล่าสุด: \"$userPrompt\") — โปรดสนทนาหรือตอบคำถามของผู้ใช้ตามปกติโดยไม่ต้องเปิดกล้อง"
                )
                _activeToolName.value = null
                return
            }
        }

        // Voice Profile Guard: Prevent hallucinated voice profile browsing if user did not mention voice
        if (event.name in setOf("voice_get_profiles", "voice_set_profile")) {
            val p = userPrompt.lowercase()
            val hasVoiceIntent = listOf("เสียง", "voice", "สำเนียง", "โทน", "เปลี่ยนเสียง").any { p.contains(it) }
            if (userPrompt.isNotBlank() && !hasVoiceIntent) {
                logDebug("LiveBridge", "🛡️ Blocked hallucinated ${event.name} — userPrompt='$userPrompt'")
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "VOICE_COMMAND_NOT_REQUESTED: ผู้ใช้ไม่ได้สั่งเปลี่ยนเสียงหรือขอดูรายชื่อเสียง (คำพูด: \"$userPrompt\") — โปรดตอบรับหรือคุยกับผู้ใช้ตามปกติ"
                )
                _activeToolName.value = null
                return
            }
        }

        if (isTradingAnalysisTool(event.name)) {
            val profile = tradingProfileFor(userPrompt)
            if (!profileToolAllowed(userPrompt, event.name, event.args)) {
                val reason = when {
                    !allowedTradingTimeframe(event.args) -> "AI Profile จำกัด timeframe เริ่มต้นไว้ที่ M15, H1, H4; โปรดไม่เรียก D1/1D เว้นแต่ผู้ใช้ระบุเอง"
                    profile == "USER_QUERY" -> "คำถามนี้ถามค่าเฉพาะ ไม่ใช่ full analysis; ใช้ trading_technical_analysis หรือ trading_price แทน"
                    profile == "SMC" -> "ผู้ใช้เลือก SMC Profile แล้ว ไม่ต้องเรียก Technical/Deep Analysis ซ้ำ"
                    else -> "AI Profile ใช้ trading_deep_analysis_suite เป็น consolidated analysis path เท่านั้น"
                }
                liveService.sendNativeToolResponse(event.callId, event.name, "PROFILE_GUARD: $reason — tool นี้ไม่ได้ดึงข้อมูลให้ ห้ามแต่งราคาหรือตัวเลขเอง ถ้ายังไม่มีข้อมูลจาก tool ใน turn นี้ให้เรียก tool ที่ระบุ หรือบอกผู้ใช้ตรงๆ ว่ายังไม่มีข้อมูล")
                logDebug("LiveBridge", "🛡️ Profile guard blocked ${event.name} for profile=$profile")
                return
            }
            tradingProfileCallCount++
            if (tradingProfileCallCount > 3) {
                liveService.sendNativeToolResponse(event.callId, event.name, "PROFILE_GUARD: ได้ข้อมูลครบ 3 TF (M15/H1/H4) ใน turn นี้แล้ว ไม่ต้องเรียก analysis tool เพิ่ม โปรดสังเคราะห์จากผล tool ที่ได้รับแล้วเท่านั้น ห้ามเพิ่มตัวเลขที่ไม่มีในผล tool")
                logDebug("LiveBridge", "🛡️ Profile guard capped analysis chain at 3 calls")
                return
            }
        }

        _activeToolName.value = event.name

        // Signal alerts must never probe MT5 symbol discovery first. The alert pipeline owns
        // source selection: DEMO/PAPER -> TradingView; LIVE+connected -> MT5; LIVE+offline -> TV.
        if (event.name == "trading_mt5_symbol_search" && isSignalAlertRequest(userPrompt)) {
            val guard = "SIGNAL_DATA_SOURCE_GUARD: ไม่ต้องค้นหา symbol ผ่าน MT5 สำหรับ Signal Alert — ให้สร้าง trading_signal_alert แล้ว TradingSignalMarketDataRouter จะเลือก MT5 LIVE หรือ TradingView ตาม runtime policy อัตโนมัติ"
            liveService.sendNativeToolResponse(event.callId, event.name, guard)
            logDebug("LiveBridge", "🛡️ Signal alert blocked premature MT5 symbol search callId=${event.callId}")
            _activeToolName.value = null
            return
        }

        val effectiveArgs = if (event.name == "device_avatar_emotion") {
            val targetPage = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(userPrompt)
                ?: event.args["page"]?.toIntOrNull()
                ?: event.args["page_number"]?.toIntOrNull()
                ?: if (event.args["action"] in listOf("page", "หน้า", "หน้าที่")) event.args["emotion"]?.toIntOrNull() else null
                ?: event.args["emotion"]?.toIntOrNull()?.takeIf { it in 1..50 }

            val isPlayAll = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(userPrompt) ||
                    event.args["action"] in listOf("all", "play_all", "demo_all", "all_pages", "หน้าทั้งหมด")

            if (targetPage != null) {
                logDebug("LiveBridge", "📖 Detected Moodset Page $targetPage from userPrompt='$userPrompt' or args=${event.args} -> overriding args")
                mapOf("action" to "page", "page" to targetPage.toString())
            } else if (isPlayAll) {
                logDebug("LiveBridge", "🎭 Detected Play All Moodsets from userPrompt='$userPrompt' or args=${event.args} -> overriding args")
                mapOf("action" to "all")
            } else {
                val detectedProp = com.skyliner2008.jarvis.pet.PetSceneEngine.detectSpecificPropFromText(userPrompt)
                if (detectedProp != null && event.args["props"].isNullOrBlank()) {
                    logDebug("LiveBridge", "🍔 Detected specific prop '${detectedProp.name}' from userPrompt='$userPrompt' -> enriching tool args")
                    event.args + ("props" to detectedProp.name.lowercase())
                } else {
                    event.args
                }
            }
        } else {
            event.args
        }

        val toolCall = ToolCall(name = event.name, args = effectiveArgs)
        val rawResult = try {
            ToolExecutor.execute(toolCall, memoryContext)
        } catch (e: Exception) {
            logError("LiveBridge", "Tool execution failed", e)
            com.skyliner2008.jarvis.tools.ToolResult(event.name, "Error: ${e.message}", true)
        }

        val finalResultText = processInterceptedRequest(rawResult.result)

        val isInternalUiTool = event.name in setOf(
            "device_avatar_emotion",
            "device_custom_prop",
            "device_pet_care",
            "device_always_live",
            "vision_activate"
        )
        if (!isInternalUiTool && finalResultText.isNotBlank()) {
            _lastToolResult.value = event.name to finalResultText
        }

        _activeToolName.value = null
        
        // --- UI Optimizations for Live Mode ---
        when {
            event.name == "vision_activate" -> {
                onAiVisionToggle?.invoke(true)
                com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(true)

                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "OK_EYES_OPEN. กล้องกำลังเปิดและเริ่มสตรีมภาพสดเข้าสู่ระบบ... ในเทิร์นนี้โปรดพูดตอบรับสั้นๆ 1 ประโยคเท่านั้น เช่น 'ไหนขอน้องจาวิสดูก่อนนะฮับบอส ถือของไว้ใกล้ๆ กล้องนะฮับ' ห้ามเดาสุ่มหรือตอบสิ่งที่เห็นในเทิร์นนี้เด็ดขาด ให้รอรับภาพสดที่ชัดเจนในอีก 1-2 วินาทีข้างหน้า"
                )
                
                // Record to history
                scope.launch {
                    memoryManager?.storeMessage("system", "JARVIS activated eyes to observe environment.", metadata = "{\"event\": \"vision_on\"}")
                }

                // 2-Turn Vision Pipeline:
                // Turn 1: AI พูดประโยคตอบรับเปิดตัว (1-2 วิ) ระหว่างนี้ฮาร์ดแวร์กล้องจะจับโฟกัสและส่งวิดีโอสด 2-3 เฟรมเข้าสู่ WebSocket
                // Turn 2: เมื่อ Turn 1 จบลง ส่งคำสั่งกระตุ้นผ่าน realtimeInput ให้ AI สรุปสิ่งที่เห็นจากภาพสดและสั่ง vision_deactivate ทันที
                visionPromptJob?.cancel()
                visionPromptJob = scope.launch {
                    // 1. รอให้ Turn 1 (Intro phrase) จบลง
                    kotlinx.coroutines.withTimeoutOrNull(15_000) {
                        liveService.turnCompleteFlow.first()
                    }
                    logDebug("LiveBridge", "👁️ Vision auto-prompt: กระตุ้น Turn 2 ให้ AI วิเคราะห์ภาพสดและตอบทันที")

                    // 2. ส่งข้อความผ่าน realtimeInput (กระตุ้นโมเดลให้พูดตอบ Turn 2 ทันทีเหมือน user พูด)
                    val question = liveService.lastUserText.ifBlank { "บอสถามว่าถือหรือโชว์อะไรอยู่" }
                    liveService.sendRealtimeText("บอสถามว่า: \"$question\" — ตอนนี้ภาพจากกล้องสดเข้ามาอย่างชัดเจนแล้ว โปรดสังเกตภาพวิดีโอสดในปัจจุบันแล้วตอบคำถามของบอสทันทีอย่างแม่นยำและกระชับ ตอบสิ่งที่เห็นจริง 1-2 ประโยค เมื่อตอบจบให้เรียก vision_deactivate ทันที")

                    // 3. รอให้ Turn 2 (การตอบสรุปสิ่งที่เห็น) จบลง
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        liveService.turnCompleteFlow.first()
                    }
                    logDebug("LiveBridge", "👁️ Turn 2 completed. Ensuring camera closes cleanly.")

                    // 4. Fallback: หากโมเดลลืมเรียก vision_deactivate หลังพูดตอบจบ ให้ปิดกล้องและพับตาลงอัตโนมัติ
                    kotlinx.coroutines.delay(1000L)
                    onAiVisionToggle?.invoke(false)
                    com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                }

                logDebug("LiveBridge", "👁️ Vision activated (Context: ${liveService.lastUserText})")
                return
            }
            event.name == "vision_deactivate" -> {
                visionPromptJob?.cancel()
                onAiVisionToggle?.invoke(false)
                com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "OK_EYES_CLOSED. กล้องปิดเรียบร้อยแล้ว"
                )
                logDebug("LiveBridge", "🕶️ Vision deactivated by AI")
                return
            }
            event.name == "camera_analyze_scene" || event.name == "camera_read_text" -> {
                // In Live mode, the video frames are already streaming through the WebSocket.
                // A separate REST API call would fail because it's a different session.
                // Tell the AI to just look at the stream it's already receiving.
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "เครื่องมือ ${event.name} ไม่จำเป็นต้องใช้ในตอนนี้ เนื่องจากคุณได้เปิดโหมด Vision และกำลังรับวิดีโอสด (Live Stream) อยู่แล้ว โปรดประมวลผลสิ่งที่คุณเห็นจากสตรีมวิดีโอที่ได้รับในปัจจุบันและอธิบายให้ผู้ใช้ฟังทันที"
                )
                logDebug("LiveBridge", "📹 Redirected ${event.name} to Live Stream (no separate API needed)")
                return
            }
            event.name == "analyze_and_display_report" -> {
                // Extract detailed markdown and emit to chat
                val report = event.args["detailed_markdown"] ?: ""
                if (report.isNotBlank()) {
                    liveService.emitTextToChat(report)
                }
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "✅ รายงานถูกส่งเข้าแชทแล้ว โปรดอธิบายสาระสำคัญให้ผู้ใช้ฟังเป็นภาษาไทยแบบสนทนาอย่างครบถ้วน โดยครอบคลุมข้อสรุป เหตุผล ตัวเลขสำคัญ และจุดที่ควรระวัง ไม่ต้องอ่านตารางหรือ markdown ตามตัวอักษร และบอกผู้ใช้ว่าสามารถดูรายละเอียดเต็มในแชทได้"
                )
                logDebug("LiveBridge", "📊 Report tool executed")
                return
            }
            event.name == "voice_get_profiles" -> {
                val list = com.skyliner2008.jarvis.data.GeminiVoiceProfiles.getVoiceListSummary()
                liveService.sendNativeToolResponse(event.callId, event.name, "📋 รายชื่อเสียงที่สามารถใช้ได้:\n$list")
                return
            }
            event.name == "voice_set_profile" -> {
                val name = event.args["name"] ?: ""
                if (name.isNotBlank()) {
                    onVoiceChange?.invoke(name)
                    liveService.sendNativeToolResponse(event.callId, event.name, "✅ รับทราบครับ ผมกำลังเปลี่ยนเสียงเป็น '$name' กรุณารอสักครู่ขณะผมปรับจูนระบบ...")
                } else {
                    liveService.sendNativeToolResponse(event.callId, event.name, "❌ ผิดพลาด: ไม่ระบุชื่อเสียง")
                }
                return
            }
            else -> {
                // เครื่องมือทั่วไป ให้พ่นลงแชทตามปกติ (ใช้ระบบ isStatic อัตโนมัติ)
                liveService.emitTextToChat(finalResultText)
            }
        }

        // ผลเต็มแสดงในแชทแล้ว (emitTextToChat ด้านบน) — แนบกฎเสียงกำกับไม่ให้ model อ่านตาราง/markdown ออกเสียง
        // ยกเว้น system_self_review: โหมดเล่ายาว (narration) ผู้ใช้ต้องการฟังรีวิวเต็ม ไม่จำกัดประโยค
        // ยกเว้น long-task ack (backtest/optimize/evolve): แค่รับคำสั่ง งานจริงรันเบื้องหลัง — ตอบสั้นๆ พอ
        val isLongTaskAck = event.name in setOf("trading_backtest", "trading_backtest_optimize", "trading_backtest_evolve")
        val dataFailureRule = if (ToolRegistry.isTradingTool(event.name) &&
            (rawResult.isError || finalResultText.startsWith("Error") || finalResultText.startsWith("❌"))
        ) {
            "\n\n[DATA ERROR] ดึงข้อมูลตลาดไม่สำเร็จ — บอกผู้ใช้ตรงๆ ว่าดึงข้อมูลไม่ได้และสาเหตุสั้นๆ ห้ามเดาหรือแต่งราคา/ตัวเลขใดๆ เด็ดขาด"
        } else ""
        val profileFinalization = if (
            isTradingAnalysisTool(event.name) && tradingProfileCallCount >= 3
        ) {
            "\n\n[PROFILE COMPLETE] ได้ข้อมูลครบ canonical TF แล้ว (M15/H1/H4) โปรดหยุดเรียก Trading analysis tools เพิ่มและสังเคราะห์คำตอบสุดท้ายให้ผู้ใช้ทันที"
        } else ""
        val voiceRule = when {
            event.name == "device_always_live" -> {
                val mode = event.args["mode"]?.lowercase() ?: ""
                if (mode == "pet") {
                    "\n\n[VOICE RULE - PET MODE] สลับเข้าสู่โหมดสัตว์เลี้ยงตั้งโต๊ะ (Virtual Desk Pet) แล้ว! — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารักสดใส เป็นธรรมชาติ เช่น 'เข้าโหมดสัตว์เลี้ยงแล้วฮับ พร้อมเล่นกับเจ้านายแล้ว!' (ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ เด็ดขาด) ห้ามตอบเป็นทางการ ห้ามใช้ markdown"
                } else {
                    "\n\n[VOICE RULE - ALWAYS LIVE] สลับโหมดควบคุม/โหมดขับขี่/Always AI Live เรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยคอย่างมั่นใจและกระชับ (เช่น 'เข้าสู่โหมดควบคุมแล้วค่ะ พร้อมรับคำสั่งตลอดเวลา' หรือ 'เปิดโหมดขับขี่เรียบร้อยแล้วค่ะ เดินทางปลอดภัยนะคะ') ห้ามอธิบายยาว ห้ามใช้ markdown"
                }
            }
            event.name == "device_avatar_emotion" -> {
                val action = effectiveArgs["action"]?.lowercase() ?: event.args["action"]?.lowercase() ?: ""
                val scene = effectiveArgs["scene"] ?: event.args["scene"] ?: ""
                val page = effectiveArgs["page"]?.toIntOrNull() ?: event.args["page"]?.toIntOrNull()
                val isAll = action in listOf("all", "demo", "play_all")
                if (action == "scene" || action == "ฉาก" || scene.isNotBlank()) {
                    "\n\n[VOICE RULE - PET SCENE] เริ่มเล่นฉากอนิเมชันและเอฟเฟกต์บนหน้าจอเรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารักสดใสและมีอารมณ์ร่วมตามฉากที่เล่น เช่น กินอาหาร ดื่มน้ำ ยิงจรวด หรือใส่แว่นตา (ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ เด็ดขาด) ห้ามใช้ markdown"
                } else if (page != null) {
                    val item = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.findByPage(page)
                    "\n\n[VOICE RULE - MOODSET PAGE] แสดง Moodset หน้าที่ $page: ${item?.nameEn} (${item?.nameTh}) บนหน้าจอ 3-5 วินาทีเรียบร้อยแล้วค่ะ (ระบบมีสารบัญครบทั้ง 50 หน้า แผ่นที่ 1: หน้า 1-20, แผ่นที่ 2: หน้า 21-50) — โปรดตอบรับสั้นๆ 1 ประโยคอย่างสดใส เช่น 'แสดงหน้าที่ $page ${item?.nameTh} ให้บอสตรวจสอบแล้วค่ะ' ห้ามบอกว่าไม่มีหน้าที่ 11 หรือมีแค่ 10 หน้าเด็ดขาด ห้ามใช้ markdown"
                } else if (isAll) {
                    "\n\n[VOICE RULE - MOODSET ALL] เริ่มเล่นแสดง Moodset ครบทั้งหมด 50 หน้าวนตรวจบนหน้าจอสลับทุก 4 วินาทีเรียบร้อยแล้วค่ะ — โปรดตอบรับสั้นๆ 1 ประโยคอย่างสดใส เช่น 'เริ่มแสดง Moodset ทุกหน้าครบทั้ง 50 หน้าให้บอสตรวจสอบแล้วนะคะ' ห้ามบอกว่ามีแค่ 10 หน้า ห้ามใช้ markdown"
                } else {
                    "\n\n[VOICE RULE - AVATAR EMOTION] แสดงสีหน้า Avatar บนหน้าจอเรียบร้อยแล้ว (ระบบมี Moodset ทั้งหมด 50 หน้า) — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารัก สดใส และเป็นธรรมชาติ ห้ามตอบว่าไม่มีหน้าตา ห้ามพูดว่ามีแค่ 10 หน้า ห้ามใช้ markdown"
                }
            }
            event.name == "device_custom_prop" -> {
                val action = event.args["action"]?.lowercase() ?: "add"
                val name = event.args["name"] ?: "อุปกรณ์เสริม"
                if (action in listOf("clear", "remove")) {
                    "\n\n[VOICE RULE - CUSTOM PROP] ถอดอุปกรณ์เสริมออกเรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยคอย่างน่ารักสดใส เช่น 'ถอด $name ออกเรียบร้อยแล้วฮับ!' ห้ามใช้ markdown"
                } else {
                    "\n\n[VOICE RULE - CUSTOM PROP] เสกและสวมใส่อุปกรณ์เสริมเวกเตอร์ SVG เรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1-2 ประโยคอย่างภูมิใจ ขี้เล่น น่ารัก เช่น 'เสก $name มาใส่ให้แล้วฮับ! น่ารักไหมฮับเจ้านาย' ห้ามอ่านโค้ด SVG ห้ามใช้ markdown"
                }
            }
            event.name == "device_pet_care" -> {
                "\n\n[VOICE RULE - PET CARE] ผลลัพธ์ข้างบนคือค่าสถานะจริงของน้องหลังดูแล — ตอบรับสั้นๆ 1-2 ประโยคอย่างน่ารักตามตัวเลขจริง (เช่น อิ่มแล้ว ยังง่วง สะอาดแล้ว) ห้ามอ่านตัวเลขทุกค่าเรียงกัน ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ ห้ามใช้ markdown"
            }
            event.name == "device_notification_read" -> {
                "\n\n[VOICE RULE - NOTIFICATION READ] สรุปข้อความแจ้งเตือนที่ตรวจพบให้ผู้ใช้ฟังเป็นภาษาไทยอย่างกระชับ ระบุแอป ผู้ส่ง และเนื้อหาสำคัญ ห้ามอ่าน timestamp หรือ ID ยาวๆ"
            }
            event.name == "device_notification_reply" -> {
                "\n\n[VOICE RULE - NOTIFICATION REPLY] ตอบกลับข้อความเรียบร้อยแล้ว — โปรดยืนยันกับผู้ใช้สั้นๆ 1 ประโยค เช่น 'ส่งข้อความตอบกลับไปยังคุณ [ชื่อ] เรียบร้อยแล้วค่ะ'"
            }
            event.name == "device_media_control" -> {
                "\n\n[VOICE RULE - MEDIA] รายงานหรือตอบรับการควบคุมเพลงสั้นๆ 1-2 ประโยค เช่น 'ตอนนี้กำลังเล่นเพลง [ชื่อเพลง] ของ [ศิลปิน] ค่ะ' หรือ 'เปิดเพลงบน YouTube ให้แล้วค่ะ'"
            }
            event.name == "device_location" -> {
                if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                    "\n\n[VOICE RULE - PET LOCATION & NEARBY] รายงานสถานที่ใกล้เคียงหรือที่อยู่ให้เจ้านายฟังอย่างน่ารักสดใส กระชับ 1-2 ประโยค แนะนำ 2-3 ร้าน/สถานที่เด็ด และชวนเปิดดูแผนที่ได้ฮับ ห้ามอ่านตัวเลขทศนิยมพิกัด GPS ยาวๆ ห้ามพูดคำว่า ปิ๊บๆ หรือ บี๊บๆ"
                } else {
                    "\n\n[VOICE RULE - LOCATION] รายงานพิกัดและที่อยู่ปัจจุบันให้ผู้ใช้ฟังเป็นภาษาไทยอย่างกระชับ ระบุตำบล/ย่าน อำเภอ และจังหวัด ห้ามอ่านตัวเลขทศนิยมพิกัด GPS ยาวๆ"
                }
            }
            event.name == "device_weather" -> {
                if (com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode) {
                    "\n\n[VOICE RULE - PET WEATHER] เล่าสภาพอากาศ อุณหภูมิ และบอกว่าฝนจะตกไหมให้เจ้านายฟังอย่างน่ารักสดใส เป็นห่วงเป็นใย 1-2 ประโยค เช่น ชวนพกร่ม หรือเตือนแดดร้อน ห้ามอ่านตัวเลขทศนิยม ห้ามใช้ markdown"
                } else {
                    "\n\n[VOICE RULE - WEATHER] รายงานสภาพอากาศ อุณหภูมิ โอกาสฝนตก และคำแนะนำการเดินทาง/การแต่งตัวให้ผู้ใช้ฟังเป็นภาษาไทยอย่างกระชับ 2-3 ประโยค ห้ามใช้ markdown"
                }
            }
            event.name == "system_self_review" -> {
                "\n\n[VOICE RULE - NARRATION] นี่คือโหมดรีวิวตัวเอง ผู้ใช้ต้องการฟังเนื้อหาทั้งหมด — โปรดเล่าออกเสียงเป็นภาษาไทยแบบสนทนา ไล่ทีละหัวข้อตามเอกสารจนครบทุกส่วน ไม่จำกัดความยาว ห้ามสรุปย่อ ห้ามหยุดกลางทางจนกว่าจะเล่าครบ ห้ามใช้ markdown หรืออ่านสัญลักษณ์ออกเสียง"
            }
            isLongTaskAck -> {
                "\n\n[VOICE RULE - ACK] นี่เป็นเพียงการรับคำสั่งงานเบื้องหลัง — ตอบผู้ใช้สั้นๆ 1-2 ประโยคเท่านั้นว่ากำลังดำเนินการอยู่ (เช่น 'รับทราบครับ กำลังรัน backtest ให้อยู่ เสร็จแล้วจะรายงานครับ') ห้ามสรุปยาว ห้ามชวนคุยยาว ห้ามใช้ markdown"
            }
            else -> {
                "\n\n[VOICE PRESENTATION POLICY] รายละเอียดเต็มแสดงในแชทแล้ว โปรดอธิบายให้ผู้ใช้ฟังเป็นภาษาไทยแบบสนทนา ไม่อ่านรายงาน ตาราง หรือลิสต์ตามตัวอักษร และไม่ใช้ markdown ในเสียงพูด ให้เริ่มจากข้อสรุปหลัก แล้วอธิบายเหตุผลพร้อมตัวเลขสำคัญประมาณ 3-5 จุด ความหมายของโซน/สัญญาณที่สำคัญ และจุดที่ควรระวังหรือเงื่อนไขยืนยัน สรุปให้ครบทุกส่วนที่มีนัยสำคัญ โดยทั่วไปประมาณ 8-12 ประโยคสำหรับผลวิเคราะห์ที่ซับซ้อน แต่ลดหรือเพิ่มได้ตามความจำเป็น ห้ามตัดข้อมูลสำคัญเพียงเพื่อให้สั้น"
            }
        }
        liveService.sendNativeToolResponse(
            callId   = event.callId,
            toolName = event.name,
            result   = finalResultText + profileFinalization + dataFailureRule + voiceRule
        )
        logDebug("LiveBridge", "✅ Path A done: ${event.name} → ${finalResultText.take(80)}")
    }

    private suspend fun handleBridgeRequest(intentText: String, memoryContext: String = "") {
        logDebug("LiveBridge", "▶ Path B bridge request: ${intentText.take(100)}")

        val toolList = ToolRegistry.allToolNames().joinToString(", ")
        val analysisPrompt = """
Analyze the following intent and identify the required tool:
"$intentText"

Available tools: $toolList

Respond in JSON format only (no extra explanation):
{"tool": "tool_name", "args": {"key": "value"}}

If no tool is needed, respond: {"tool": "none", "args": {}}
""".trimIndent()

        var toolNameFromModel = "none"
        var argsFromModel = emptyMap<String, String>()

        try {
            val chunks = StringBuilder()
            geminiService.generateResponseFlow(
                prompt = analysisPrompt,
                history = listOf(ConversationTurn("user", intentText)),
                intentAddon = "You are a tool router. Respond only with JSON."
            ).collect { chunks.append(it) }

            val raw = chunks.toString().trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            // Parse ด้วย kotlinx.serialization จริง (ไม่ใช่ regex)
            // — รองรับ nested args, number/boolean/array และ text นำหน้า JSON
            val parsed = runCatching {
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
                } else null
            }.getOrNull()

            toolNameFromModel = parsed?.get("tool")?.jsonPrimitive?.contentOrNull ?: "none"
            argsFromModel = (parsed?.get("args") as? JsonObject)
                ?.let { com.skyliner2008.jarvis.tools.ToolArgParser.fromJsonObject(it) }
                ?: emptyMap()
        } catch (e: Exception) {
            logError("LiveBridge", "Model analysis failed", e)
        }

        if (toolNameFromModel == "none" || toolNameFromModel.isBlank()) {
            logDebug("LiveBridge", "Path B: no tool needed")
            return
        }

        logDebug("LiveBridge", "Path B executing: $toolNameFromModel($argsFromModel)")
        _activeToolName.value = toolNameFromModel

        val toolCall = ToolCall(name = toolNameFromModel, args = argsFromModel)
        val rawResult = try {
            ToolExecutor.execute(toolCall, memoryContext)
        } catch (e: Exception) {
            com.skyliner2008.jarvis.tools.ToolResult(toolNameFromModel, "Error: ${e.message}", true)
        }

        val finalResultText = processInterceptedRequest(rawResult.result)

        _activeToolName.value = null
        
        // Finalize: Show the data in Chat UI before sending to Gemini for voice summary
        liveService.emitTextToChat(finalResultText)
        
        liveService.sendBridgeToolResult(toolNameFromModel, finalResultText)
        logDebug("LiveBridge", "✅ Path B done: $toolNameFromModel → ${finalResultText.take(80)}")
    }

    private suspend fun processInterceptedRequest(resultData: String): String {
        return when {
            resultData.startsWith("NEARBY_SEARCH_REQUEST::") -> {
                val query = resultData.substringAfter("query=").substringBefore("::location=")
                val loc = resultData.substringAfter("::location=").substringBefore("::lat=")
                val summary = resultData.substringAfter("::summary=")
                logDebug("LiveBridge", "Intercept nearby search: $query in $loc")
                geminiService.generateResponse(
                    prompt = "ผู้ใช้กำลังอยู่ที่พิกัด/ย่าน: $loc ($summary)\nต้องการค้นหาหรือแนะนำ: $query ในบริเวณใกล้เคียงนี้\nโปรดแนะนำร้านอาหารหรือสถานที่จริงที่เป็นที่นิยมและเปิดบริการอยู่ในย่านนี้ 3-4 แห่ง พร้อมบอกเมนูเด็ดหรือจุดเด่นสั้นๆ เป็นภาษาไทย",
                    intentAddon = "หาข้อมูลสถานที่จริงในย่านนี้ สรุปให้กระชับ ชัดเจน พร้อมจุดเด่นและชื่อร้าน",
                    enableGrounding = true
                )
            }
            resultData.startsWith("WEB_SEARCH_REQUEST::query=") -> {
                val query = resultData.substringAfter("query=")
                logDebug("LiveBridge", "Intercept search: $query")
                geminiService.generateResponse(
                    prompt = "ค้นหาข้อมูลล่าสุดเกี่ยวกับ: $query",
                    intentAddon = "หาคำตอบที่เจาะจง สรุปสั้นๆ และเน้นข้อมูลตัวเลขหรือข้อเท็จจริงล่าสุด",
                    enableGrounding = true
                )
            }
            resultData.startsWith("TRANSLATE_REQUEST::") -> {
                val text = resultData.substringAfter("text=").substringBefore("::to=")
                val to = resultData.substringAfter("::to=")
                logDebug("LiveBridge", "Intercept translate: to $to")
                geminiService.generateResponse(
                    prompt = "แปลข้อความต่อไปนี้เป็นภาษา $to:\n\n$text",
                    intentAddon = "ให้แปลอย่างธรรมชาติ ไม่ต้องอธิบายเพิ่มเติม ตอบเฉพาะคำแปลเท่านั้น"
                )
            }
            resultData.startsWith("SUMMARIZE_REQUEST::") -> {
                val length = resultData.substringAfter("length=").substringBefore("::text=")
                val text = resultData.substringAfter("::text=")
                logDebug("LiveBridge", "Intercept summarize: length $length")
                val lengthPrompt = when (length.lowercase()) {
                    "short"    -> "สรุปให้สั้นมาก 1-2 ประโยค"
                    "detailed" -> "สรุปอย่างละเอียดพร้อมจุดสำคัญ"
                    else       -> "สรุปให้กระชับ 3-5 ประโยค"
                }
                geminiService.generateResponse(
                    prompt = "$lengthPrompt:\n\n$text",
                    intentAddon = "สรุปอย่างเดียว ห้ามอธิบายการกระทำของคุณ"
                )
            }
            resultData.startsWith("GEMINI_FILE::") -> {
                val mime = resultData.substringAfter("mime=").substringBefore("::data=")
                val data = resultData.substringAfter("::data=")
                logDebug("LiveBridge", "Intercept file: $mime")
                val chunks = StringBuilder()
                try {
                    geminiService.generateResponseWithFile(
                        prompt = "ช่วยสรุปเนื้อหาสำคัญของไฟล์นี้ให้หน่อย",
                        mimeType = mime,
                        base64Data = data
                    ).collect { chunks.append(it) }
                    chunks.toString()
                } catch (e: Exception) {
                    "เกิดข้อผิดพลาดในการวิเคราะห์ไฟล์: ${e.message}"
                }
            }
            else -> resultData
        }
    }

    fun startCollecting(memoryContextProvider: () -> String) {
        // เคลียร์ Job เก่าออกก่อนเพื่อป้องกันการเรียก Tool ซ้ำซ้อน (Duplicate Collectors)
        collectionJob?.cancel()
        visionPromptJob?.cancel()
        
        collectionJob = scope.launch {
            // Path A: Native Tool Calls (Function Calling)
            launch {
                liveService.nativeToolCallFlow.collect { event ->
                    try {
                        handleNativeToolCall(event, memoryContextProvider())
                    } catch (e: Exception) {
                        logError("LiveBridge", "Native tool handling error", e)
                    }
                }
            }

            // Path B: Bridge Tool Requests (Model text instructions)
            launch {
                liveService.toolRequestFlow.collect { intentText ->
                    try {
                        handleBridgeRequest(intentText, memoryContextProvider())
                    } catch (e: Exception) {
                        logError("LiveBridge", "Bridge tool handling error", e)
                    }
                }
            }
        }

        logDebug("LiveBridge", "✅ Bridge collectors started (Cleaned & Restarted)")
    }
}
