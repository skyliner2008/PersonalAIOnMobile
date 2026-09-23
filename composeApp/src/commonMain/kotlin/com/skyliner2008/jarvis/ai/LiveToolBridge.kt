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
import kotlinx.coroutines.sync.withLock
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

    /** เวลาที่เปิดกล้องรอบล่าสุด — กันโมเดลเรียก vision_activate ซ้ำจนวนลูป (พบจาก log จริง 2026-09-20: เรียก 4 รอบ ตอบช้า 15 วินาที) */
    private var visionActivatedAtMs: Long = 0L

    /** ช่วงเวลาที่บล็อกการสั่งเปิดกล้องซ้ำ — ยาวกว่ารอบ 2-turn pipeline (รอ turn 15s + 20s) */
    private val VISION_REACTIVATE_BLOCK_MS = 40_000L

    // Trading AI Profile guard: keep one analysis family per user turn and only M15/H1/H4 by default.
    // tool call รันขนานกันแล้ว → counter ต้องอยู่ใต้ mutex
    private val tradingProfileMutex = kotlinx.coroutines.sync.Mutex()
    private var tradingProfilePromptKey: String = ""
    private var tradingProfileCallCount: Int = 0

    /** tool call ที่กำลังรัน (callId → job) — ใช้ยกเลิกเมื่อ server ส่ง toolCallCancellation */
    private val runningCallsMutex = kotlinx.coroutines.sync.Mutex()
    private val runningCalls = mutableMapOf<String, Job>()

    /** id ที่ server ยกเลิกแล้ว — ห้ามส่ง toolResponse กลับไปอีก (ตาม Live API) */
    private val cancelledCallIds = ArrayDeque<String>()

    private suspend fun markCancelled(ids: List<String>) = runningCallsMutex.withLock {
        ids.forEach { id ->
            cancelledCallIds.addLast(id)
            while (cancelledCallIds.size > 64) cancelledCallIds.removeFirst()
        }
    }

    private suspend fun isCancelled(callId: String): Boolean =
        runningCallsMutex.withLock { callId in cancelledCallIds }

    /** turn ของผู้ใช้และคำถาม ณ ตอนที่ call เริ่ม — ใช้ตัดสินว่าผลของ call ที่ถูกยกเลิกยังควรส่งไหม */
    private data class CallOrigin(val turnSerial: Int, val question: String)
    private val callOrigins = mutableMapOf<String, CallOrigin>()
    /** call ล่าสุดของแต่ละ tool (id to args) — โมเดลเรียก tool เดิมด้วย args เดิมซ้ำเองหลังถูกยกเลิก = ผลเก่าไม่ต้องส่ง */
    private val latestCallByTool = mutableMapOf<String, Pair<String, Map<String, String>>>()
    /** call ที่ตอบรับไปแล้วและส่งต่อให้ลูกน้องทำ — ผลจริงเข้าคิวรายงาน ไม่ส่งเป็น toolResponse */
    private val delegatedCallIds = mutableSetOf<String>()
    /** กันเรียก tool เดิมด้วยค่าเดิมวนซ้ำในเทิร์นเดียวกัน (ใช้ภายใต้ runningCallsMutex) */
    private val duplicateGuard = DuplicateCallGuard()

    /** turn ที่ผู้ใช้เรียก custom tool / skill แบบ chain — ขั้นตอนของ skill กำหนด TF เอง จึงไม่ให้ profile guard ไปขวาง */
    private var skillChainTurnKey: String = ""

    /** กันส่งข้อความตอบกลับซ้ำเมื่อ model เรียก device_notification_reply ด้วยข้อความเดิมติดกัน */
    private var lastNotificationReply: Pair<String, Long>? = null

    private fun tradingProfileFor(prompt: String) = LiveIntentMatchers.tradingProfileFor(prompt)
    private fun isTradingAnalysisTool(name: String) = LiveIntentMatchers.isTradingAnalysisTool(name)
    private fun isSignalAlertRequest(prompt: String) = LiveIntentMatchers.isSignalAlertRequest(prompt)
    private fun isTradingQuestion(prompt: String) = LiveIntentMatchers.isTradingQuestion(prompt)
    private fun isAvatarEmotionRequest(prompt: String) = LiveIntentMatchers.isAvatarEmotionRequest(prompt)
    private fun isSceneRequest(prompt: String) = LiveIntentMatchers.isSceneRequest(prompt)
    private fun isAlwaysLiveRequest(prompt: String) = LiveIntentMatchers.isAlwaysLiveRequest(prompt)
    private fun isMediaRequest(prompt: String) = LiveIntentMatchers.isMediaRequest(prompt)
    private fun isNavigationRequest(prompt: String) = LiveIntentMatchers.isNavigationRequest(prompt)
    private fun isNotificationRequest(prompt: String) = LiveIntentMatchers.isNotificationRequest(prompt)
    private fun isLocationOrSpeedRequest(prompt: String) = LiveIntentMatchers.isLocationOrSpeedRequest(prompt)
    private fun isParkingRequest(prompt: String) = LiveIntentMatchers.isParkingRequest(prompt)
    private fun isNightModeRequest(prompt: String) = LiveIntentMatchers.isNightModeRequest(prompt)

    /**
     * ส่งผล tool กลับ Live model
     * - ส่งเข้า session ที่ออก call เท่านั้น (ถ้า reconnect ระหว่างรัน tool, id เก่าใช้กับ session ใหม่ไม่ได้)
     * - [deliverIfStale] = true: ถ้าส่ง toolResponse ไม่ได้ ให้ส่งผลเป็น realtime text เข้า session ใหม่แทน
     *   (เดิมผล tool ยาวๆ หายเงียบเมื่อโดน GoAway ระหว่างรัน)
     */
    /** tool ที่ผลลัพธ์คือการเปลี่ยน UI/โหมด — ถ้า session restart ไปแล้วไม่ต้องเล่าผลย้อนหลัง (greeting ของ session ใหม่พูดแทน) */
    private val uiOnlyTools = setOf(
        "device_avatar_emotion", "device_custom_prop", "device_pet_care",
        "device_always_live", "vision_activate", "vision_deactivate"
    )

    /**
     * server ยกเลิก call เมื่อ VAD ได้ยินเสียงแทรก (เสียงรอบข้าง หรือผู้ใช้พูดคำถามถัดไป) — ห้ามทิ้งคำถาม:
     * ผลเข้าคิวของ LiveGeminiService ซึ่งส่งให้โมเดลตอบเฉพาะตอนเงียบจริง
     * (เดิมส่ง realtime text ทันทีเมื่อ "ยังไม่มี transcript ใหม่" — แต่ transcript มาช้ากว่าเสียงพูด 2–3 วิ
     *  จึงส่งแทรกขณะผู้ใช้พูดคำถามถัดไป แล้ว session ค้างไม่ตอบอะไรอีก, logcat 2026-09-24 01:48)
     * โมเดลเรียก tool เดิมด้วย args เดิมซ้ำเอง → ทิ้ง (call ใหม่ตอบให้แล้ว)
     * ห้ามส่งเป็น toolResponse ของ id ที่ถูกยกเลิก (ตาม Live API)
     */
    private suspend fun deliverCancelledResult(event: LiveToolCallEvent, result: String) {
        val (origin, latest) = runningCallsMutex.withLock { callOrigins[event.callId] to latestCallByTool[event.name] }
        val superseded = latest != null && latest.first != event.callId && latest.second == event.args
        // guard ของ call ที่ส่งต่อให้ลูกน้องต้องรายงาน (หัวหน้าตอบรับไปแล้ว ถ้าทิ้งจะเงียบหาย) — ของ call ที่ถูกยกเลิกทิ้งได้
        val delegated = runningCallsMutex.withLock { event.callId in delegatedCallIds }
        val droppableGuard = result.startsWith("PROFILE_GUARD") && !delegated
        if (origin == null || superseded || event.name in uiOnlyTools || droppableGuard) {
            logDebug("LiveBridge", "🚫 ไม่ส่งผล ${event.name} callId=${event.callId} — ถูกยกเลิก (superseded=$superseded)")
            return
        }
        liveService.enqueuePendingToolResult(origin.question, event.name, result)
    }

    private suspend fun respond(event: LiveToolCallEvent, result: String, deliverIfStale: Boolean = false) {
        // tool บางตัวจับ CancellationException ไว้เองแล้วคืน error ปกติ — ถ้าไม่เช็คตรงนี้เราจะส่งผล
        // ของ call ที่ server ยกเลิกไปแล้ว (เคสจริง 2026-09-16: SMC analysis ถูกยกเลิกแต่ยังส่ง response)
        // งานของลูกน้อง (ตอบรับไปแล้ว) หรือ call ที่ server ยกเลิก → ผลเข้าคิวรายงาน
        val delegated = runningCallsMutex.withLock { event.callId in delegatedCallIds }
        if (delegated || isCancelled(event.callId)) {
            deliverCancelledResult(event, result)
            return
        }
        runningCallsMutex.withLock { duplicateGuard.recordResult(event.callId, result) }
        val sent = liveService.sendNativeToolResponse(
            callId = event.callId,
            toolName = event.name,
            result = result,
            sessionGeneration = event.sessionGeneration
        )
        if (!sent && deliverIfStale && event.name !in uiOnlyTools) {
            val delivered = liveService.sendRealtimeTextWhenReady(
                "[SYSTEM] ผลจากเครื่องมือ ${event.name} ที่ผู้ใช้ขอไว้ก่อนการเชื่อมต่อใหม่: ${result.take(3000)}\n" +
                    "โปรดแจ้งผู้ใช้สั้นๆ แบบสนทนา ห้ามใช้ markdown",
                timeoutMs = 20_000L
            )
            logDebug("LiveBridge", "↪️ Stale tool response for ${event.name} delivered via realtime text: $delivered")
        }
    }

    private suspend fun handleNativeToolCall(event: LiveToolCallEvent, memoryContext: String = "") {
        logDebug("LiveBridge", "▶ Path A: ${event.name} callId=${event.callId} (${event.args})")

        // Enforce the profile selected from the user's actual request before executing tools.
        // This prevents the Live model from expanding one analysis request into Deep+SMC+TA+D1 chains.
        val userPrompt = liveService.lastUserText
        val turnKey = "turn-${liveService.userTurnSerial}"

        // Always Live Guard: If Gemini mistakenly calls trading tools when user meant Always Live / Control / Drive mode
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isAlwaysLiveRequest(userPrompt)) {
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
            respond(event, result.result + voiceGuide,
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Avatar Emotion & Smart Scene Guard: If Gemini mistakenly calls Fear & Greed or Sentiment when user meant Avatar face or Scene
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && (isAvatarEmotionRequest(userPrompt) || isSceneRequest(userPrompt))) {
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
            respond(event, result.result + voiceRule,
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Media Control Guard: If Gemini mistakenly calls trading/other tools when user wants music control
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isMediaRequest(userPrompt)) {
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
            respond(event, result.result + "\n\n[VOICE RULE - MEDIA] ควบคุมการเล่นเพลงเรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยคอย่างกระชับ เช่น 'กำลังเล่นเพลงให้แล้วนะคะ' หรือ 'หยุดเล่นเพลงแล้วค่ะ' ห้ามอธิบายยาว ห้ามใช้ markdown",
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Navigation Guard: If Gemini mistakenly calls trading tools when user asks for navigation
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isNavigationRequest(userPrompt)) {
            val destination = LiveIntentMatchers.extractNavigationDestination(userPrompt)
            val args = mapOf("destination" to destination, "action" to "navigate", "mode" to "drive")
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_navigate($args)")
            val redirectCall = ToolCall(name = "device_navigate", args = args)
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected navigate execution failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_navigate", "Error: ${e.message}", true)
            }
            respond(event, result.result + "\n\n[VOICE RULE - NAVIGATION] เปิดระบบนำทางไปยัง $destination เรียบร้อยแล้ว — โปรดตอบรับสั้นๆ 1 ประโยค เช่น 'เปิดระบบนำทางไป $destination ให้แล้วค่ะ เดินทางปลอดภัยนะคะ' ห้ามอธิบายยาว ห้ามใช้ markdown",
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Notification Read Guard: If Gemini mistakenly calls trading tools when user asks for notifications
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isNotificationRequest(userPrompt)) {
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_notification_read")
            val redirectCall = ToolCall(name = "device_notification_read", args = mapOf("count" to "3"))
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected notification read failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_notification_read", "Error: ${e.message}", true)
            }
            respond(event, result.result + "\n\n[VOICE RULE - NOTIFICATIONS] อ่านการแจ้งเตือนล่าสุดเรียบร้อยแล้ว — สรุปหรือแจ้งเตือนสั้นๆ ให้ผู้ใช้ทราบอย่างกระชับและเป็นธรรมชาติ",
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Location & Speed Guard: If Gemini mistakenly calls trading tools when user asks for speed / current location
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isLocationOrSpeedRequest(userPrompt)) {
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Redirecting to device_location")
            val redirectCall = ToolCall(name = "device_location", args = mapOf("action" to "get_current"))
            val result = try {
                ToolExecutor.execute(redirectCall, memoryContext)
            } catch (e: Exception) {
                logError("LiveBridge", "Redirected location failed", e)
                com.skyliner2008.jarvis.tools.ToolResult("device_location", "Error: ${e.message}", true)
            }
            respond(event, result.result + "\n\n[VOICE RULE - LOCATION] ได้ข้อมูลพิกัด/ตำแหน่งเรียบร้อยแล้ว — ตอบความเร็วหรือตำแหน่งปัจจุบันให้ผู้ใช้ทราบอย่างกระชับและชัดเจน",
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Parking Location Guard: If Gemini mistakenly calls trading tools when user asks about parking
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isParkingRequest(userPrompt)) {
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
            respond(event, responseText,
                deliverIfStale = true
            )
            _activeToolName.value = null
            return
        }

        // Night / Low-Glare Driving Mode Guard
        if (LiveIntentMatchers.canRedirectMisroutedTool(event.name, userPrompt) && isNightModeRequest(userPrompt)) {
            val p = userPrompt.lowercase()
            val isTurnOff = listOf("ปิด", "ยกเลิก", "off", "disable").any { p.contains(it) }
            DriveBridge.setLowGlareMode(!isTurnOff)
            val msg = if (isTurnOff) "ปิดโหมดกลางคืนและปรับความสว่างปกติแล้วค่ะ" else "เปิดโหมดลดแสงสะท้อนสำหรับการขับขี่ตอนกลางคืนเรียบร้อยแล้วค่ะ"
            logDebug("LiveBridge", "🛡️ Intercepted ${event.name} -> Handled Low Glare Mode: $msg")
            respond(event, "$msg\n\n[VOICE RULE - NIGHT_MODE] ยืนยันการปรับโหมดลดแสงสะท้อนสำหรับการขับขี่สั้นๆ 1 ประโยค",
                deliverIfStale = true
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
                    respond(event, "โหมด$activeMode ยังคงเปิดทำงานอยู่ตามปกติค่ะ (ผู้ใช้ไม่ได้สั่งให้ปิดโหมด หากต้องการปิดกรุณาสั่ง 'ปิดโหมด' ชัดเจนนะคะ)\n\n[VOICE RULE] โหมด$activeMode ยังคงทำงานอยู่ตามปกติ — ให้ตอบรับหรือช่วยเหลือผู้ใช้ตามคำพูดล่าสุด ('$userPrompt') อย่างเป็นธรรมชาติ ห้ามบอกว่าปิดโหมดแล้วเด็ดขาด"
                    )
                    _activeToolName.value = null
                    return
                }
            }
        }

        // Vision Activate Guard: Prevent camera opening if user did not ask to see/look
        if (event.name == "vision_activate") {
            val p = userPrompt.lowercase().trim()

            // 1. ผู้ใช้หมายถึง "แอปกล้อง" ของเครื่อง ไม่ใช่ตาของ JARVIS → เปิดแอปแทน
            val wantsCameraApp = listOf("แอปกล้อง", "แอพกล้อง", "กล้องถ่ายรูป", "camera app", "ถ่ายรูป", "ถ่ายภาพ")
                .any { p.contains(it) }
            if (wantsCameraApp) {
                logDebug("LiveBridge", "🛡️ vision_activate → device_open_app (ผู้ใช้ขอแอปกล้อง): '$userPrompt'")
                val result = try {
                    ToolExecutor.execute(ToolCall("device_open_app", mapOf("app_name" to "กล้อง")), memoryContext)
                } catch (e: Exception) {
                    logError("LiveBridge", "Redirected open camera app failed", e)
                    com.skyliner2008.jarvis.tools.ToolResult("device_open_app", "Error: ${e.message}", true)
                }
                respond(event, result.result + "\n\n[VOICE RULE - CAMERA APP] เปิดแอปกล้องของเครื่องให้แล้ว — ยืนยันสั้นๆ 1 ประโยค (ไม่ใช่การเปิดตาของจาวิสเอง)",
                    deliverIfStale = true
                )
                _activeToolName.value = null
                return
            }

            // 2. กล้องเปิดสตรีมอยู่แล้ว — สั่งซ้ำจะรีสตาร์ท 2-turn pipeline แล้ววนลูปไม่รู้จบ
            val sinceActivated = System.currentTimeMillis() - visionActivatedAtMs
            if (visionActivatedAtMs > 0L && sinceActivated < VISION_REACTIVATE_BLOCK_MS) {
                logDebug("LiveBridge", "🛡️ Duplicate vision_activate blocked (${sinceActivated}ms ago)")
                respond(event, "EYES_ALREADY_OPEN: กล้องเปิดและกำลังสตรีมภาพสดอยู่แล้ว ห้ามเรียก vision_activate ซ้ำ — " +
                    "ให้ดูภาพวิดีโอสดล่าสุดที่เข้ามาแล้วตอบสิ่งที่เห็นจริงทันที 1-2 ประโยค ถ้ายังไม่ชัดให้บอกผู้ใช้ตรงๆ ว่ายังไม่ชัด แล้วเรียก vision_deactivate เมื่อตอบจบ"
                )
                _activeToolName.value = null
                return
            }
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
                respond(event, "EYES_NOT_NEEDED: ผู้ใช้ไม่ได้สั่งให้เปิดกล้องหรือมองดูสิ่งใด (คำพูดล่าสุด: \"$userPrompt\") — โปรดสนทนาหรือตอบคำถามของผู้ใช้ตามปกติโดยไม่ต้องเปิดกล้อง"
                )
                _activeToolName.value = null
                return
            }
        }

        // Typing Guard: กันโมเดล "พิมพ์+กดส่ง" เองทั้งที่ผู้ใช้ไม่ได้สั่ง
        // พบจริง 2026-09-20: ผู้ใช้พูดแค่ "เปิด YouTube" แต่โมเดลไล่ไปแตะช่องค้นหาแล้วพิมพ์ "เพลงลูกทุ่ง"
        // (ข้อความค้างจากบทสนทนาก่อนหน้า) แล้วกดค้นหาให้เอง
        if (event.name == "device_type_text") {
            val p = userPrompt.trim()
            if (p.isNotBlank() && !LiveIntentMatchers.hasTypingIntent(p)) {
                logDebug("LiveBridge", "🛡️ Blocked unrequested device_type_text — userPrompt='$userPrompt' text='${event.args["text"]}'")
                respond(event, "TYPING_NOT_REQUESTED: ผู้ใช้ไม่ได้สั่งให้พิมพ์หรือค้นหาอะไร (คำพูดล่าสุด: \"$userPrompt\") — " +
                    "ห้ามพิมพ์ข้อความเองเด็ดขาด โดยเฉพาะข้อความที่ค้างจากบทสนทนาก่อนหน้า " +
                    "ให้ทำเฉพาะสิ่งที่ผู้ใช้สั่งในประโยคล่าสุดแล้วหยุด รายงานสั้นๆ ว่าทำอะไรไปแล้ว"
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
                respond(event, "VOICE_COMMAND_NOT_REQUESTED: ผู้ใช้ไม่ได้สั่งเปลี่ยนเสียงหรือขอดูรายชื่อเสียง (คำพูด: \"$userPrompt\") — โปรดตอบรับหรือคุยกับผู้ใช้ตามปกติ"
                )
                _activeToolName.value = null
                return
            }
        }

        val inSkillChain = turnKey.isNotBlank() && turnKey == skillChainTurnKey
        if (isTradingAnalysisTool(event.name) && !inSkillChain &&
            !LiveIntentMatchers.allowedTradingTimeframe(event.args, userPrompt)) {
            val fixed = LiveIntentMatchers.withDefaultTimeframe(event.args)
            logDebug("LiveBridge", "🔧 ${event.name}: TF ที่ผู้ใช้ไม่ได้ขอ (${event.args}) → ใช้ 15m แทน")
            return handleNativeToolCall(event.copy(args = fixed), memoryContext)
        }
        if (isTradingAnalysisTool(event.name) && !inSkillChain) {
            val profile = tradingProfileFor(userPrompt)
            if (!LiveIntentMatchers.profileToolAllowed(userPrompt, event.name, event.args)) {
                val reason = when {
                    !LiveIntentMatchers.allowedTradingTimeframe(event.args, userPrompt) -> "AI Profile จำกัด timeframe เริ่มต้นไว้ที่ M15, H1, H4; โปรดไม่เรียก D1/1D เว้นแต่ผู้ใช้ระบุเอง"
                    profile == "USER_QUERY" -> "คำถามนี้ถามค่าเฉพาะ ไม่ใช่ full analysis; ใช้ trading_technical_analysis หรือ trading_price แทน"
                    profile == "SMC" -> "ผู้ใช้เลือก SMC Profile แล้ว ไม่ต้องเรียก Technical/Deep Analysis ซ้ำ"
                    else -> "AI Profile ใช้ trading_deep_analysis_suite เป็น consolidated analysis path เท่านั้น"
                }
                respond(event, "PROFILE_GUARD: $reason — tool นี้ไม่ได้ดึงข้อมูลให้ ห้ามแต่งราคาหรือตัวเลขเอง ถ้ายังไม่มีข้อมูลจาก tool ใน turn นี้ให้เรียก tool ที่ระบุ หรือบอกผู้ใช้ตรงๆ ว่ายังไม่มีข้อมูล")
                logDebug("LiveBridge", "🛡️ Profile guard blocked ${event.name} for profile=$profile")
                return
            }
        }
        if (isTradingAnalysisTool(event.name)) {
            val callCountThisTurn = tradingProfileMutex.withLock {
                if (turnKey != tradingProfilePromptKey) {
                    tradingProfilePromptKey = turnKey
                    tradingProfileCallCount = 0
                }
                ++tradingProfileCallCount
            }
            // skill chain ระบุจำนวน TF เอง (เช่น 15m/1h/4h/1D) จึงให้โควตามากกว่าปกติ
            val callCap = if (inSkillChain) 6 else 3
            if (callCountThisTurn > callCap) {
                respond(event, "PROFILE_GUARD: เรียก analysis tool ครบ $callCap ครั้งใน turn นี้แล้ว โปรดสังเคราะห์จากผล tool ที่ได้รับแล้วเท่านั้น ห้ามเพิ่มตัวเลขที่ไม่มีในผล tool")
                logDebug("LiveBridge", "🛡️ Profile guard capped analysis chain at $callCap calls (skillChain=$inSkillChain)")
                return
            }
        }

        _activeToolName.value = event.name

        // Signal alerts must never probe MT5 symbol discovery first. The alert pipeline owns
        // source selection: DEMO/PAPER -> TradingView; LIVE+connected -> MT5; LIVE+offline -> TV.
        if (event.name == "trading_mt5_symbol_search" && isSignalAlertRequest(userPrompt)) {
            val guard = "SIGNAL_DATA_SOURCE_GUARD: ไม่ต้องค้นหา symbol ผ่าน MT5 สำหรับ Signal Alert — ให้สร้าง trading_signal_alert แล้ว TradingSignalMarketDataRouter จะเลือก MT5 LIVE หรือ TradingView ตาม runtime policy อัตโนมัติ"
            respond(event, guard)
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

        // Outgoing message guard: model บางครั้งเรียก reply ซ้ำด้วยข้อความเดิม (เช่นหลัง reconnect) — ห้ามส่งซ้ำ
        if (event.name == "device_notification_reply") {
            val message = (effectiveArgs["message"] ?: effectiveArgs["text"] ?: "").trim()
            val now = System.currentTimeMillis()
            val previous = lastNotificationReply
            if (message.isNotBlank() && previous != null && previous.first == message && now - previous.second < 20_000L) {
                logDebug("LiveBridge", "🛡️ Duplicate device_notification_reply blocked: '$message'")
                respond(event, "ALREADY_SENT: ข้อความ \"$message\" ถูกส่งตอบกลับไปแล้วเมื่อสักครู่ ไม่ต้องส่งซ้ำ — ยืนยันกับผู้ใช้สั้นๆ ว่าส่งแล้ว")
                _activeToolName.value = null
                return
            }
            if (message.isNotBlank()) lastNotificationReply = message to now
        }

        val toolCall = ToolCall(name = event.name, args = effectiveArgs)
        val rawResult = try {
            ToolExecutor.execute(toolCall, memoryContext)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _activeToolName.value = null
            throw e
        } catch (e: Exception) {
            logError("LiveBridge", "Tool execution failed", e)
            com.skyliner2008.jarvis.tools.ToolResult(event.name, "Error: ${e.message}", true)
        }

        val finalResultText = processInterceptedRequest(rawResult.result)

        // ผลของ custom tool / skill แบบ chain ไม่ใช่ "ผลลัพธ์" แต่เป็น "ขั้นตอนที่ต้องทำต่อ"
        // ถ้าปล่อยให้ VOICE PRESENTATION POLICY ต่อท้าย โมเดลจะสรุปให้ผู้ใช้ฟังแล้วหยุด แทนที่จะเรียก tool ตามขั้นตอน
        // (เคสจริง 2026-09-16: custom_gold_check ตอบขั้นตอนมาแล้วจบเลย ไม่ได้ทำ deep analysis ต่อ)
        val isSkillInstruction = finalResultText.trimStart().startsWith("🛠️ เปิดใช้งานเครื่องมือ")
        if (isSkillInstruction) {
            // ขั้นตอนของ skill ระบุ TF/ชุด tool เอง (เช่น deep analysis 15m/1h/4h/1D) — ปลด profile guard ให้ turn นี้
            skillChainTurnKey = turnKey
            logDebug("LiveBridge", "🔗 Skill chain started for $turnKey (${event.name}) — profile guard relaxed")
        }

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
                visionActivatedAtMs = System.currentTimeMillis()
                com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(true)

                respond(event, "OK_EYES_OPEN. กล้องกำลังเปิดและเริ่มสตรีมภาพสดเข้าสู่ระบบ... ในเทิร์นนี้โปรดพูดตอบรับสั้นๆ 1 ประโยคเท่านั้น เช่น 'ไหนขอน้องจาวิสดูก่อนนะฮับบอส ถือของไว้ใกล้ๆ กล้องนะฮับ' ห้ามเดาสุ่มหรือตอบสิ่งที่เห็นในเทิร์นนี้เด็ดขาด ให้รอรับภาพสดที่ชัดเจนในอีก 1-2 วินาทีข้างหน้า"
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
                    visionActivatedAtMs = 0L
                    com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                }

                logDebug("LiveBridge", "👁️ Vision activated (Context: ${liveService.lastUserText})")
                return
            }
            event.name == "vision_deactivate" -> {
                visionPromptJob?.cancel()
                visionActivatedAtMs = 0L
                onAiVisionToggle?.invoke(false)
                com.skyliner2008.jarvis.pet.PetVisionBridge.requestEyeOpen(false)
                respond(event, "OK_EYES_CLOSED. กล้องปิดเรียบร้อยแล้ว"
                )
                logDebug("LiveBridge", "🕶️ Vision deactivated by AI")
                return
            }
            event.name == "camera_analyze_scene" || event.name == "camera_read_text" -> {
                // In Live mode, the video frames are already streaming through the WebSocket.
                // A separate REST API call would fail because it's a different session.
                // Tell the AI to just look at the stream it's already receiving.
                respond(event, "เครื่องมือ ${event.name} ไม่จำเป็นต้องใช้ในตอนนี้ เนื่องจากคุณได้เปิดโหมด Vision และกำลังรับวิดีโอสด (Live Stream) อยู่แล้ว โปรดประมวลผลสิ่งที่คุณเห็นจากสตรีมวิดีโอที่ได้รับในปัจจุบันและอธิบายให้ผู้ใช้ฟังทันที"
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
                respond(event, "✅ รายงานถูกส่งเข้าแชทแล้ว โปรดอธิบายสาระสำคัญให้ผู้ใช้ฟังเป็นภาษาไทยแบบสนทนาอย่างครบถ้วน โดยครอบคลุมข้อสรุป เหตุผล ตัวเลขสำคัญ และจุดที่ควรระวัง ไม่ต้องอ่านตารางหรือ markdown ตามตัวอักษร และบอกผู้ใช้ว่าสามารถดูรายละเอียดเต็มในแชทได้"
                )
                logDebug("LiveBridge", "📊 Report tool executed")
                return
            }
            event.name == "voice_get_profiles" -> {
                val list = com.skyliner2008.jarvis.data.GeminiVoiceProfiles.getVoiceListSummary()
                respond(event, "📋 รายชื่อเสียงที่สามารถใช้ได้:\n$list")
                return
            }
            event.name == "voice_set_profile" -> {
                val name = event.args["name"] ?: ""
                if (name.isNotBlank()) {
                    // ตอบ tool ก่อน แล้วค่อยสั่งเปลี่ยนเสียง (ซึ่ง restart session) — ไม่งั้น response ส่งไม่ทันก่อน socket ปิด
                    respond(event, "✅ รับทราบครับ ผมกำลังเปลี่ยนเสียงเป็น '$name' กรุณารอสักครู่ขณะผมปรับจูนระบบ...")
                    onVoiceChange?.invoke(name)
                } else {
                    respond(event, "❌ ผิดพลาด: ไม่ระบุชื่อเสียง")
                }
                return
            }
            else -> {
                // เครื่องมือทั่วไป ให้พ่นลงแชทตามปกติ (ใช้ระบบ isStatic อัตโนมัติ)
                // ยกเว้นขั้นตอนของ skill/custom tool ซึ่งเป็นคำสั่งภายใน ไม่ใช่เนื้อหาสำหรับผู้ใช้
                if (!isSkillInstruction) liveService.emitTextToChat(finalResultText)
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
            isSkillInstruction ->
                "\n\n[CHAIN RULE — ห้ามหยุดตรงนี้] ข้อความข้างบนคือ *ขั้นตอนที่ต้องทำต่อ* ไม่ใช่ผลลัพธ์ " +
                    "ให้เรียก tool ตามขั้นตอนทันทีจนครบทุกข้อ แล้วค่อยสรุปผลจริงให้ผู้ใช้ฟัง " +
                    "ระหว่างนี้พูดกับผู้ใช้สั้นๆ 1 ประโยคว่ากำลังดึงข้อมูลให้ (เช่น 'กำลังวิเคราะห์ให้อยู่ค่ะ รอสักครู่นะคะ') " +
                    "ห้ามสรุปหรือเดาผลก่อนได้ข้อมูลจริงจาก tool เด็ดขาด"
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
        respond(event, finalResultText + profileFinalization + dataFailureRule + voiceRule,
            deliverIfStale = true
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
            // แต่ละ call รันใน job ของตัวเอง — tool ช้า (deep analysis / grounding) ต้องไม่บล็อก call อื่น
            // และต้องยกเลิกได้เมื่อ server ส่ง toolCallCancellation
            launch {
                liveService.nativeToolCallFlow.collect { event ->
                    // กันโมเดลวนเรียก tool เดิมด้วยค่าเดิมในเทิร์นเดียวกัน — ตอบผลเดิม ไม่รันใหม่
                    val turn = liveService.userTurnSerial
                    val dup = runningCallsMutex.withLock {
                        duplicateGuard.check(event.callId, event.name, event.args, turn, System.currentTimeMillis())
                    }
                    if (dup !is DuplicateCallGuard.Decision.Run) {
                        val reply = when (dup) {
                            is DuplicateCallGuard.Decision.Repeat -> DuplicateCallGuard.repeatNote(event.name, dup.previousResult)
                            else -> DuplicateCallGuard.stillRunningNote(event.name)
                        }
                        logDebug("LiveBridge", "🔁 ${event.name} ถูกเรียกซ้ำด้วยค่าเดิมในเทิร์นเดียวกัน — ไม่รันใหม่ (${event.args})")
                        liveService.sendNativeToolResponse(event.callId, event.name, reply, event.sessionGeneration)
                        return@collect
                    }
                    runningCallsMutex.withLock {
                        callOrigins[event.callId] = CallOrigin(turn, liveService.lastUserText)
                        latestCallByTool[event.name] = event.callId to event.args
                    }
                    // หัวหน้า (Live) ส่งต่องานให้ลูกน้อง: ตอบรับทันที ไม่มีช่วงให้ server ยกเลิก —
                    // งานจริงรันเบื้องหลังผ่านเส้นทางเดิมทั้งหมด (guard, TF, การ์ดแชท) แล้วรายงานผลผ่านคิวตอนเงียบ
                    val inSkillChain = skillChainTurnKey.isNotBlank() && skillChainTurnKey == "turn-${liveService.userTurnSerial}"
                    if (LiveIntentMatchers.isDelegatedTool(event.name) && !inSkillChain) {
                        runningCallsMutex.withLock { delegatedCallIds.add(event.callId) }
                        liveService.sendNativeToolResponse(
                            callId = event.callId,
                            toolName = event.name,
                            result = com.skyliner2008.jarvis.data.LiveProtocol.delegatedAck(event.name),
                            sessionGeneration = event.sessionGeneration
                        )
                        logDebug("LiveBridge", "👷 ส่งต่อ ${event.name} ให้ผู้ช่วยทำเบื้องหลัง (${event.args})")
                        launch(com.skyliner2008.jarvis.data.LiveToolCallContext()) {
                            try {
                                handleNativeToolCall(event, memoryContextProvider())
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logError("LiveBridge", "Delegated tool ${event.name} failed", e)
                                liveService.enqueuePendingToolResult(
                                    liveService.lastUserText, event.name,
                                    "ดึงข้อมูลไม่สำเร็จ: ${e.message} — บอกผู้ใช้ตรงๆ ห้ามเดาตัวเลข"
                                )
                            } finally {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    runningCallsMutex.withLock {
                                        delegatedCallIds.remove(event.callId)
                                        callOrigins.remove(event.callId)
                                    }
                                }
                                _activeToolName.value = null
                            }
                        }
                        return@collect
                    }
                    val job = launch(com.skyliner2008.jarvis.data.LiveToolCallContext(), start = kotlinx.coroutines.CoroutineStart.LAZY) {
                        try {
                            handleNativeToolCall(event, memoryContextProvider())
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            logDebug("LiveBridge", "🚫 Tool call ${event.name} callId=${event.callId} cancelled")
                            _activeToolName.value = null
                            throw e
                        } catch (e: Exception) {
                            logError("LiveBridge", "Native tool handling error", e)
                        } finally {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                runningCallsMutex.withLock {
                                    runningCalls.remove(event.callId)
                                    callOrigins.remove(event.callId)
                                }
                            }
                        }
                    }
                    runningCallsMutex.withLock { runningCalls[event.callId] = job }
                    job.start()
                }
            }

            // server ยกเลิก function call เมื่อ VAD ได้ยินเสียงแทรก → ไม่ส่ง toolResponse ของ id นั้นอีก
            // แต่ปล่อยงานรันต่อ: ถ้าเป็นแค่เสียงรบกวน (ผู้ใช้ไม่ได้พูดต่อ) ผลจะถูกส่งเป็น realtime text แทน
            // (ดู deliverCancelledResult — เดิม cancel ทิ้งทันที คำถามจึงหายเงียบ)
            launch {
                liveService.toolCallCancellationFlow.collect { ids ->
                    markCancelled(ids)
                    val running = runningCallsMutex.withLock { ids.count { it in runningCalls } }
                    if (running > 0) logDebug("LiveBridge", "🚫 Server cancelled $running running tool call(s): $ids — รันต่อเพื่อส่งผลถ้าเป็นเสียงรบกวน")
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
