package com.example.personalaibot.ai

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.LiveGeminiService
import com.example.personalaibot.data.LiveToolCallEvent
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveToolBridge(
    private val liveService: LiveGeminiService,
    private val geminiService: GeminiService,
    private val memoryManager: com.example.personalaibot.memory.JarvisMemoryManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    var onAiVisionToggle: ((Boolean) -> Unit)? = null,
    var onVoiceChange: ((String) -> Unit)? = null
) {
    private val _activeToolName = MutableStateFlow<String?>(null)
    val activeToolName: StateFlow<String?> = _activeToolName.asStateFlow()

    private var collectionJob: Job? = null

    private suspend fun handleNativeToolCall(event: LiveToolCallEvent, memoryContext: String = "") {
        logDebug("LiveBridge", "▶ Path A: ${event.name}(${event.args})")
        _activeToolName.value = event.name

        val toolCall = ToolCall(name = event.name, args = event.args)
        val rawResult = try {
            ToolExecutor.execute(toolCall, memoryContext)
        } catch (e: Exception) {
            logError("LiveBridge", "Tool execution failed", e)
            com.example.personalaibot.tools.ToolResult(event.name, "Error: ${e.message}", true)
        }

        val finalResultText = processInterceptedRequest(rawResult.result)

        _activeToolName.value = null
        
        // --- UI Optimizations for Live Mode ---
        when {
            event.name == "vision_activate" -> {
                val contextReminder = if (liveService.lastUserText.isNotBlank()) {
                    "เพื่อตอบคำถามล่าสุดของคุณ: \"${liveService.lastUserText}\""
                } else {
                    "เพื่อสังเกตสภาพแวดล้อมรอบตัว"
                }
                onAiVisionToggle?.invoke(true)
                
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "OK_EYES_OPEN. ระบบสตรีมมิ่งเริ่มแล้ว คุณเห็นภาพตอนนี้ทันที **โปรดสังเกตวิดีโออย่างน้อย 1-2 วินาทีเพื่อให้ภาพชัดเจนก่อนเริ่มวิเคราะห์ ห้ามเดาสุ่ม** เมื่อได้ข้อมูลครบแล้วให้เรียก vision_deactivate ทันที"
                )
                
                // Record to history
                scope.launch {
                    memoryManager?.storeMessage("system", "JARVIS activated eyes to observe environment.", metadata = "{\"event\": \"vision_on\"}")
                }
                
                logDebug("LiveBridge", "👁️ Vision activated (Context: ${liveService.lastUserText})")
                return
            }
            event.name == "vision_deactivate" -> {
                onAiVisionToggle?.invoke(false)
                liveService.sendNativeToolResponse(
                    callId   = event.callId,
                    toolName = event.name,
                    result   = "OK_EYES_CLOSED. กล้องปิดแล้ว โปรดสรุปสิ่งที่เห็นให้ผู้ใช้ฟังอย่างเป็นธรรมชาติและกระชับที่สุด"
                )
                logDebug("LiveBridge", "🕶️ Vision deactivated by AI (Silenced)")
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
                    result   = "✅ รายงานถูกส่งเข้าแชทแล้ว โปรดพูดสรุปสั้นๆ และบอกให้ผู้ใช้ดูรายละเอียดในแชท ห้ามอ่านตารางซ้ำ"
                )
                logDebug("LiveBridge", "📊 Report tool executed")
                return
            }
            event.name == "voice_get_profiles" -> {
                val list = com.example.personalaibot.data.GeminiVoiceProfiles.getVoiceListSummary()
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

        liveService.sendNativeToolResponse(
            callId   = event.callId,
            toolName = event.name,
            result   = finalResultText
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

            val toolMatch = Regex(""""tool"\s*:\s*"([^"]+)"""").find(raw)
            toolNameFromModel = toolMatch?.groupValues?.get(1) ?: "none"

            val argsSection = Regex(""""args"\s*:\s*\{([^}]*)\}""").find(raw)?.groupValues?.get(1)
            if (!argsSection.isNullOrBlank()) {
                argsFromModel = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
                    .findAll(argsSection)
                    .associate { it.groupValues[1] to it.groupValues[2] }
            }
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
            com.example.personalaibot.tools.ToolResult(toolNameFromModel, "Error: ${e.message}", true)
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
