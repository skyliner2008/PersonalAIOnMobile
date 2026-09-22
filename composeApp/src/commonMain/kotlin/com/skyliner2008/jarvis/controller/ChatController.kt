package com.skyliner2008.jarvis.controller

import com.skyliner2008.jarvis.Message
import com.skyliner2008.jarvis.ai.JarvisOrchestrator
import com.skyliner2008.jarvis.db.JarvisDatabase
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.maskApiKey
import com.skyliner2008.jarvis.memory.JarvisMemoryManager
import com.skyliner2008.jarvis.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.skyliner2008.jarvis.data.LiveProtocol
import kotlinx.datetime.Clock

/**
 * Phase-4 refactor: แยก Chat pipeline (messages, streaming, memory post-processing)
 * ออกจาก JarvisViewModel — coupling กับ settings/voice/mt5 ผ่าน lambda providers
 */
class ChatController(
    private val scope: CoroutineScope,
    private val database: JarvisDatabase,
    private val memoryManager: JarvisMemoryManager,
    private val orchestrator: JarvisOrchestrator,
    private val voiceManager: VoiceManager,
    private val selectedModelProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val coreContextProvider: suspend () -> String,
    private val sleepCycleTrigger: () -> Unit
) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /** ให้ VoiceController เขียน text output ของ live session ลงแชทโดยตรง */
    val messagesMutable: MutableStateFlow<List<Message>> get() = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val maxContextTurns = 10

    suspend fun loadHistory() {
        // โหลดเฉพาะประวัติข้อความที่ยังไม่ครบ 24 ชั่วโมง (< 24 ชม.) ตามเงื่อนไขเวลา
        val cutoff = Clock.System.now().toEpochMilliseconds() - 24 * 60 * 60 * 1000L
        val history = memoryManager.getHistoryAfter(cutoff)
        // คำพูดสดของ Live (ทั้งผู้ใช้และ AI) ไม่แสดงในแชท — ตรงกับระหว่าง Live ที่แสดงเฉพาะรายงาน/ผล tool
        // (เดิมซ่อนแค่ฝั่งผู้ใช้ เปิดแอปใหม่คำพูด AI ทุกประโยครวมคำทักทายจึงโผล่ขึ้นมา)
        _messages.value = history
            .filterNot { LiveProtocol.isLiveTranscript(it.metadata) }
            .map { Message(it.role, it.content, metadata = it.metadata, timestamp = it.timestamp) }
    }

    /**
     * บทสนทนาล่าสุดจากฐานข้อมูล (รวมคำพูดสดของ Live ที่ไม่แสดงในแชท) — ใช้เป็นบริบทตอนเปิด Live session
     * ไม่รวมผล tool ดิบ (markdown ยาว — คำตอบที่พูดจริงถูกบันทึกแยกไว้แล้ว)
     */
    suspend fun recentConversationTurns(limit: Long = 40): List<Pair<String, String>> {
        val cutoff = Clock.System.now().toEpochMilliseconds() - 24 * 60 * 60 * 1000L
        return memoryManager.getHistoryAfter(cutoff, limit)
            .filterNot { it.metadata?.contains("live_voice_tool_result") == true }
            .map { it.role to it.content }
    }



    fun clearChat() {
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.deleteAllMessages()
            withContext(Dispatchers.Main) {
                _messages.value = emptyList()
            }
        }
    }

    var onTestEmotion: ((com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion?, String?) -> Unit)? = null
    var onStartDemo: (() -> Unit)? = null
    var onStopDemo: (() -> Unit)? = null
    var onPlayMoodsetPage: ((Int) -> Unit)? = null
    var onPlayAllMoodsets: (() -> Unit)? = null

    fun sendMessage(
        text: String,
        speakResponse: Boolean = false,
        attachments: List<com.skyliner2008.jarvis.ui.ChatAttachment> = emptyList()
    ) {
        if (text.isBlank() && attachments.isEmpty()) return

        val cleanText = text.trim()
        val isAvatarCmd = cleanText.startsWith("/avatar", ignoreCase = true) ||
            cleanText.startsWith("/emotion", ignoreCase = true) ||
            cleanText.startsWith("/demo", ignoreCase = true) ||
            cleanText.startsWith("/test", ignoreCase = true) ||
            cleanText.startsWith("ทำหน้า", ignoreCase = true) ||
            com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(cleanText) ||
            com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(cleanText) != null ||
            cleanText.equals("ทดสอบเดโม", ignoreCase = true) ||
            cleanText.equals("เดโม", ignoreCase = true) ||
            cleanText.equals("demo", ignoreCase = true) ||
            cleanText.contains("ทดสอบเดโม") ||
            cleanText.contains("แสดงอารมณ์") ||
            cleanText.contains("โชว์อารมณ์") ||
            cleanText.contains("ทดสอบอารมณ์") ||
            cleanText.contains("เดโม่อารมณ์") ||
            cleanText.contains("เดโมอารมณ์") ||
            cleanText.contains("ซะแดงเดโมอารมณ์") ||
            cleanText.contains("แสดงเดโม่อารมณ์") ||
            cleanText.contains("รีเซ็ตอารมณ์")
        if (isAvatarCmd) {
            handleAvatarTestCommand(cleanText)
            return
        }

        val lowerText = cleanText.lowercase()
        val isAlwaysLiveCmd = cleanText.startsWith("/always", ignoreCase = true) ||
            cleanText.startsWith("/control", ignoreCase = true) ||
            cleanText.startsWith("/drive", ignoreCase = true) ||
            cleanText.startsWith("/car", ignoreCase = true) ||
            lowerText in setOf("โหมดควบคุม", "โหมดขับขี่", "โหมดรถยนต์", "control mode", "drive mode", "car mode") ||
            lowerText.startsWith("เปิดโหมดควบคุม") || lowerText.startsWith("เปิดโหมดขับขี่") || lowerText.startsWith("เปิดโหมดรถยนต์") ||
            lowerText.startsWith("เข้าโหมดควบคุม") || lowerText.startsWith("เข้าโหมดขับขี่") || lowerText.startsWith("เข้าโหมดรถยนต์") ||
            lowerText.startsWith("เปิดโหมด always") || lowerText.startsWith("เข้าโหมด always") || lowerText == "เปิด always" ||
            lowerText.startsWith("ปิดโหมดควบคุม") || lowerText.startsWith("ปิดโหมดขับขี่") || lowerText.startsWith("ปิดโหมดรถยนต์") ||
            lowerText.startsWith("ออกจากโหมดควบคุม") || lowerText.startsWith("ออกจากโหมดขับขี่") || lowerText.startsWith("ออกจากโหมดรถยนต์") ||
            lowerText.startsWith("ปิดโหมด always") || lowerText.startsWith("ออกจากโหมด always") || lowerText == "ปิด always"
        if (isAlwaysLiveCmd) {
            handleAlwaysLiveCommand(cleanText)
            return
        }

        scope.launch {
            try {
                val (promptForAi, inlineFiles) = processUserAttachments(text, attachments)
                val displayText = buildString {
                    append(text)
                    if (attachments.isNotEmpty()) {
                        append("\n📎 แนบ: ")
                        append(attachments.joinToString(", ") { it.name })
                    }
                }
                _messages.value = _messages.value + Message("user", displayText)
                memoryManager.storeMessage("user", displayText)

                _isTyping.value = true
                val historySnapshot = buildHistorySnapshot()
                val coreContext = coreContextProvider()
                _messages.value = _messages.value + Message("model", "")
                var currentAiMessage = ""

                logDebug("JarvisVM", "[Chat] Sending: model=${selectedModelProvider()}, apiKey=${maskApiKey(apiKeyProvider())}, attachments=${attachments.size} (binary=${inlineFiles.size})")
                val responseFlow = orchestrator.chatWithHistory(promptForAi, historySnapshot, coreContext, inlineFiles)

                responseFlow.collect { event ->
                    when (event) {
                        is com.skyliner2008.jarvis.ai.ChatStreamEvent.Text -> {
                            currentAiMessage += event.content
                            appendAssistantMessage(currentAiMessage)
                        }
                        is com.skyliner2008.jarvis.ai.ChatStreamEvent.ToolStarted -> {
                            logDebug("JarvisVM", "[Chat] Tool started: ${event.toolName}")
                        }
                        is com.skyliner2008.jarvis.ai.ChatStreamEvent.ToolResult -> {
                            logDebug("JarvisVM", "[Chat] Tool completed: ${event.toolName}")
                        }
                        is com.skyliner2008.jarvis.ai.ChatStreamEvent.System -> {
                            logDebug("JarvisVM", "[Chat] System event: ${event.message}")
                            currentAiMessage += "\\n\\n${event.message}\\n"
                            appendAssistantMessage(currentAiMessage)
                        }
                    }
                }
                logDebug("JarvisVM", "[Chat] Response complete (${currentAiMessage.length} chars)\n>>> $currentAiMessage")

                if (currentAiMessage.isBlank()) {
                    val model = selectedModelProvider()
                    val providerId = if (model.contains("/")) model.substringBefore("/") else "gemini"
                    appendAssistantMessage("⚠️ ไม่ได้รับ response จาก $providerId\n• ตรวจสอบว่า API Key ถูกต้องและกด Save แล้ว\n• Model: $model\n• ดู Logcat (tag: Orchestrator) สำหรับรายละเอียด")
                    logError("JarvisVM", "[Chat] Empty response — model=$model, apiKey=${maskApiKey(apiKeyProvider())}")
                } else {
                    memoryManager.storeMessage("model", currentAiMessage)
                    memoryManager.updateKnowledgeGraph("User: $text\nJARVIS: $currentAiMessage")
                    memoryManager.extractAndUpdateCoreMemory(text, currentAiMessage)
                    runCatching {
                        if (memoryManager.getMessageCount() >= 200) sleepCycleTrigger()
                    }.onFailure { logError("JarvisVM", "Auto sleep-cycle check failed", it) }
                }
                _isTyping.value = false
                if (speakResponse && currentAiMessage.isNotBlank() && voiceManager.isAvailable()) {
                    voiceManager.speak(currentAiMessage, null)
                }
            } catch (e: Exception) {
                logError("JarvisVM", "Error in sendMessage", e)
                _isTyping.value = false
                _messages.value = _messages.value + Message("model", "⚠️ ขออภัย เกิดข้อผิดพลาดทางเทคนิค: ${e.message}")
            }
        }
    }

    private fun appendAssistantMessage(content: String) {
        val currentList = _messages.value.toMutableList()
        if (currentList.isNotEmpty()) {
            val last = currentList.last()
            currentList[currentList.lastIndex] = last.copy(content = content)
            _messages.value = currentList
        } else {
            _messages.value = _messages.value + Message("model", content)
        }
    }

    private fun processUserAttachments(
        text: String,
        attachments: List<com.skyliner2008.jarvis.ui.ChatAttachment>
    ): Pair<String, List<com.skyliner2008.jarvis.data.InlineData>> {
        val textAtts = attachments.filter { it.isText }
        val binaryFiles = attachments.filterNot { it.isText }
        val prompt = buildString {
            append(text)
            textAtts.forEach { att ->
                append("\n\n[ไฟล์แนบ: ${att.name}]\n```\n")
                append(att.textContent ?: "")
                append("\n```")
            }
        }
        val inlineFiles = binaryFiles.mapNotNull { att ->
            att.base64?.let { com.skyliner2008.jarvis.data.InlineData(att.mimeType, it) }
        }
        return prompt to inlineFiles
    }

    private fun buildHistorySnapshot(): List<Pair<String, String>> {
        val current = _messages.value
        val withoutLatest = if (current.isNotEmpty()) current.dropLast(1) else current
        val maxMessages = maxContextTurns * 2
        val recentMsgs = withoutLatest
            .takeLast(maxMessages)
            .map { Pair(it.role, it.content) }
            .filter { it.second.isNotBlank() }

        // ป้องกัน Input Tokens ล้น (เช่น Claude Haiku limit 50K tokens)
        // จำกัด history ให้ไม่เกิน 20,000 characters (ประมาณ 5,000-7,000 tokens)
        val maxChars = 20000
        var totalChars = 0
        val result = mutableListOf<Pair<String, String>>()
        for (msg in recentMsgs.reversed()) {
            if (totalChars + msg.second.length > maxChars && result.isNotEmpty()) {
                break
            }
            totalChars += msg.second.length
            result.add(0, msg) // ใส่ที่หัวเพื่อให้ลำดับเก่า -> ใหม่เหมือนเดิม
        }
        return result
    }

    private fun handleAvatarTestCommand(cmd: String) {
        val lower = cmd.lowercase().trim()
        val parts = cmd.split(Regex("\\s+"))
        val target = if (parts.size > 1) parts[1].lowercase() else ""

        val isPlayAll = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(cmd)
        val pageNumber = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(cmd)

        val isDemo = isPlayAll || target in listOf("demo", "all", "start") ||
            lower == "demo" || lower == "/demo" || lower == "เดโม" || lower == "ทดสอบเดโม" ||
            lower.contains("ทดสอบเดโม") || lower.contains("แสดงเดโม") ||
            lower.contains("ทดสอบระบบ") || lower.contains("ทดสอบหุ่นยนต์") ||
            lower.contains("แสดงอารมณ์ทั้งหมด") || lower.contains("เดโม่อารมณ์") ||
            lower.contains("เดโมอารมณ์") || lower.contains("ซะแดงเดโมอารมณ์") ||
            lower.contains("โชว์อารมณ์") || lower.contains("ทดสอบอารมณ์") ||
            lower.contains("แสดงเดโม่อารมณ์") || lower.contains("แสดงอารมณ์")
        val isReset = target in listOf("reset", "auto", "stop", "off") ||
            lower.contains("หยุดเดโม") || lower.contains("หยุดทดสอบ") ||
            lower.contains("รีเซ็ต") || lower.contains("โหมดปกติ") || lower.contains("กลับสู่ปกติ") ||
            lower == "หยุด" || lower.startsWith("หยุด")

        val matchedEmotion = when {
            isPlayAll || pageNumber != null || isDemo || isReset -> null
            target in listOf("happy", "smile") || lower.contains("ดีใจ") || lower.contains("ยิ้ม") || lower.contains("มีความสุข") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HAPPY
            target in listOf("excited", "star") || lower.contains("ตื่นเต้น") || lower.contains("ดาว") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EXCITED
            target in listOf("love", "heart") || lower.contains("รัก") || lower.contains("หัวใจ") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LOVE
            target in listOf("romantic", "rose") || lower.contains("โรแมนติก") || lower.contains("กุหลาบ") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ROMANTIC
            target in listOf("enraged", "fight", "rage") || lower.contains("โกรธจัด") || lower.contains("สู้กลับ") || lower.contains("ยิงจรวด") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ENRAGED
            target in listOf("angry", "mad") || lower.contains("โกรธ") || lower.contains("โมโห") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ANGRY
            target in listOf("sad", "cry") || lower.contains("เศร้า") || lower.contains("เสียใจ") || lower.contains("ร้องไห้") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SAD
            target in listOf("sleeping", "sleep") || lower.contains("ง่วง") || lower.contains("นอน") || lower.contains("หลับ") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SLEEPING
            target in listOf("listening", "listen", "mic") || lower.contains("กำลังฟัง") || lower.contains("ฟัง") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LISTENING
            target in listOf("thinking", "think") || lower.contains("กำลังคิด") || lower.contains("คิด") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.THINKING
            target in listOf("speaking", "speak", "talk") || lower.contains("กำลังพูด") || lower.contains("สนทนา") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SPEAKING
            target in listOf("idle", "ready") || lower.contains("พร้อม") || lower.contains("สแตนด์บาย") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.IDLE
            else -> null
        }

        _messages.value = _messages.value + Message("user", cmd)

        when {
            isPlayAll -> {
                _messages.value = _messages.value + Message(
                    "model",
                    "▶️ **เริ่มโหมดทดสอบ LOOI Moodset ทั้งหมด 50 หน้า!**\n" +
                    "กำลังสลับแสดงทุกอารมณ์เปรียบเทียบกับภาพอ้างอิงทีละหน้า (แสดงหน้าละ 4 วินาที ครบ 50 หน้า)\n" +
                    "(พิมพ์ `หยุด` หรือ `หยุดเดโม` เพื่อหยุดได้ทุกเมื่อค่ะ)",
                    isStatic = true
                )
                onPlayAllMoodsets?.invoke() ?: onStartDemo?.invoke()
            }
            pageNumber != null -> {
                val moodItem = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.findByPage(pageNumber)
                if (moodItem != null) {
                    _messages.value = _messages.value + Message(
                        "model",
                        "🎭 **แสดง Moodset หน้าที่ ${moodItem.pageNumber}/50: ${moodItem.nameEn} (${moodItem.nameTh})**\n" +
                        "• ภาพอ้างอิง: แผ่นที่ ${moodItem.sheet} (ช่องที่ ${moodItem.sheetIndex})\n" +
                        "• อารมณ์ (Emotion): `${moodItem.emotion.name}`\n" +
                        "• รายละเอียด: ${moodItem.description}\n" +
                        "• แสดงผล: ${moodItem.durationMs / 1000} วินาทีสำหรับตรวจสอบ",
                        isStatic = true
                    )
                    onPlayMoodsetPage?.invoke(pageNumber)
                }
            }
            isDemo -> {
                _messages.value = _messages.value + Message(
                    "model",
                    "▶️ **เริ่มโหมด Living Avatar Showcase Demo!**\nกำลังสลับแสดงฉากหลังไดนามิก 8 ธีม, ท่าทางภาษากาย, อุปกรณ์เสริมลอย และเสียง FX ครบทั้ง 8 ซีน (เปลี่ยนทุก 3.5 วินาที)\n(พิมพ์ `หยุดเดโม` หรือ `/demo stop` เพื่อหยุดได้ทุกเมื่อค่ะ)",
                    isStatic = true
                )
                onStartDemo?.invoke()
            }
            isReset -> {
                _messages.value = _messages.value + Message(
                    "model",
                    "⏹️ **รีเซ็ตเรียบร้อยค่ะ**\nAvatar กลับสู่โหมดตรวจจับอัตโนมัติตามปกติแล้วค่ะ",
                    isStatic = true
                )
                onStopDemo?.invoke() ?: onTestEmotion?.invoke(null, null)
            }
            matchedEmotion != null -> {
                val (title, detail) = when (matchedEmotion) {
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HAPPY ->
                        "HAPPY (ดีใจ/มีความสุข)" to "ตาโค้งยิ้มเปี่ยมสุข (^.^) + ปากยิ้มหวาน + พื้นหลัง Oceanic Teal"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EXCITED ->
                        "EXCITED (ตื่นเต้น)" to "ตาดาว 4 แฉกสีทอง (★.★) + ปากยิ้มกว้างตัว D + ประกายทอง Solar Gold"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LOVE ->
                        "LOVE (ส่งหัวใจ/ความรัก)" to "ตารูปหัวใจสีชมพูนีออน (♥.♥) + หัวใจลอย + แสง Hot Pink"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ANGRY ->
                        "ANGRY (เตือน/โกรธ)" to "คิ้วเฉียงขมวดคม + ปากซิกแซก + พื้นหลังเตือนภัย Alert Flame Red"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ENRAGED ->
                        "ENRAGED (โกรธจัด/สู้กลับ)" to "ตาขวางแดงเพลิง (ò.ó) + ปากขบฟันแหลม + ยิงจรวดมิสซายระเบิดหน้าจอ + แสง Alert Flame Red"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SAD ->
                        "SAD (เศร้า/เห็นใจ)" to "ตาละห้อยคว่ำ (︵.︵) + หยดน้ำตาสีฟ้าเรืองแสง + แสง Ice Slate Blue"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SLEEPING ->
                        "SLEEPING (หลับ/สแตนด์บาย)" to "ตาปิดสนิทเป็นเส้นโค้ง (─.─) + อักษร ZZZ ลอยหมุน + แสง Lavender Void"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LISTENING ->
                        "LISTENING (กำลังฟัง)" to "ตากลมโตสว่างไสว (O O) มีประกายตา + ปาก 'o' + แสง Deep Neon Aqua"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.THINKING ->
                        "THINKING (กำลังคิด)" to "สายตามองเยื้องขวาบน + ตาขวาหรี่ + บอลลูนความคิด '...' + แสง Cyber Violet"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SPEAKING ->
                        "SPEAKING (กำลังพูด)" to "ตาแคปซูลมีมิติ + ปากวงรีสั่นไหวตามเสียง + แสง Electric Emerald"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.IDLE ->
                        "IDLE (พร้อมทำงาน)" to "ตากลมรี Capsule Pill LED (❚ ❚) + ลูกเล่นขยิบตาวิ้ง (Wink) + แสง Cyber Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.WINK ->
                        "WINK (ขยิบตา)" to "ตาซ้ายขยิบเส้นตรง + ตาขวากลมโตวิ้ง + แสง Vibrant Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.CONFUSED ->
                        "CONFUSED (สงสัย)" to "เครื่องหมายคำถาม '?' ลอยตรงกลาง + ปากตรง + แสง Amber Orange"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.POUT ->
                        "POUT (งอน/แก้มป่อง)" to "แก้มป่องสีชมพูเรืองแสง + ปากงอน 'Hmph' + แสง Rose Violet"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DIZZY ->
                        "DIZZY (ตาลาย/มึนงง)" to "ตาวนเข็มนาฬิการูปก้นหอย Spiral (@_@) + ปากคลื่น + แสง Mystic Purple"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SURPRISED ->
                        "SURPRISED (ตกใจ)" to "ตาเบิกกว้างสุดขีด (O_O) + ปากอ้า 'O' + แสง Electric White-Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.BORED ->
                        "BORED (เบื่อ/ง่วง)" to "ตาลู่ครึ่งปิด (─.─) + ปากเส้นตรงเฉยเมย + แสง Cool Slate Gray"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DEAD ->
                        "DEAD (สลบ/หมดแรง)" to "ตาลายกากบาทคู่ (X X) + แสง Cyber Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LAUGHING ->
                        "LAUGHING (หัวเราะร่าเริง)" to "ตาหยีแหลมสามเหลี่ยม (> <) + ปากยิ้มกว้าง + แสง Neon Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.MUSIC ->
                        "MUSIC (ฟังเพลง)" to "ตากลม Squircle + หูฟังครอบศีรษะสีเหลืองดำ + แสง Neon Cyan"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.VR_MODE ->
                        "VR_MODE (แว่น VR/ดูหนัง)" to "แว่นตามิติ Vision Pro + ถังป๊อปคอร์นสีแดงขาว"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DIVING ->
                        "DIVING (ดำน้ำ)" to "หน้ากากดำน้ำ Snorkel Mask + ท่อหายใจฟองอากาศ"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EVIL ->
                        "EVIL (ตัวร้าย/แสบซน)" to "ตาเฉียงแดงเพลิง + เขี้ยวขาวคู่ + มินิไอคอนปิศาจสีม่วง 😈"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.FOCUSED ->
                        "FOCUSED (โฟกัส/สแกน)" to "ตาทรงลิ่มเฉียงคมกริบ + เส้นเลเซอร์ตาราง Synthwave Grid"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SHY ->
                        "SHY (เขินอาย)" to "ตากลม Squircle + แก้มชมพูระเรื่อขีดสามเส้น /// ///"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DISGUSTED ->
                        "DISGUSTED (รังเกียจ/เหม็น)" to "ตาหยี (> <) + ถังขยะเปิดฝา + ไอคอนถังขยะสีฟ้า"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.CAMERA_MODE ->
                        "CAMERA_MODE (ถ่ายรูป)" to "ตาหรี่เส้นตรง (─ ─) + ไอคอนกล้อง DSLR ส้มดำ 📷"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EATING ->
                        "EATING (กินเบอร์เกอร์)" to "ตากลม Squircle + มินิเบอร์เกอร์ชีสคั่นกลาง 🍔"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DRINKING ->
                        "DRINKING (ดื่มเบียร์ชนแก้ว)" to "ตากลม Squircle + แก้วเบียร์ฟองนุ่ม 🍺"
                    // ─── 30 Additional Moodset ───
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SICK ->
                        "SICK (ป่วย/วัดไข้)" to "ตาก้นหอยลู่ลง + ปรอทวัดไข้แก้ว 🌡️"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.RICH ->
                        "RICH (รวย/เศรษฐี)" to "ตาเครื่องหมายเงิน ($$) + ถุงเงิน + เหรียญทองคำร่วง 💰"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.CRYING ->
                        "CRYING (ร้องไห้หนัก)" to "ตาโค้งคว่ำเศร้า + น้ำตาไหลพรากเป็นสาย 😭"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.READING ->
                        "READING (อ่านหนังสือ)" to "แว่นตาทรงเหลี่ยมมน + หนังสือเปิดกาง 📖"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.GAMING ->
                        "GAMING (เล่นเกม)" to "หูฟังเกมมิ่งมีไมค์ + จอยคอนโทรลเลอร์ 🎮"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.TRAVELING ->
                        "TRAVELING (ท่องเที่ยว)" to "หมวกบัคเก็ตนักเดินทาง + พาสปอร์ต + ลูกโลก ✈️"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.WORKING ->
                        "WORKING (ทำงาน)" to "แว่นตากลม + แล็ปท็อปเปิดจอ + แก้วกาแฟ 💻"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.COLD ->
                        "COLD (หนาวจัด)" to "ตาสั่นสะท้าน (> <) + ปากสั่น + น้ำแข็งย้อย + เกล็ดหิมะ 🥶"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HOT ->
                        "HOT (ร้อนอบอ้าว)" to "ตาลู่เหงื่อหยด + พระอาทิตย์เปลวเพลิง + ปรอทแดง 🥵"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DETECTIVE ->
                        "DETECTIVE (นักสืบ)" to "หมวกเฟโดร่า + ตาหรี่สงสัย + แว่นขยายส่อง 🕵️"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.COOKING ->
                        "COOKING (ทำอาหาร)" to "หมวกเชฟสีขาว + ตายิ้มหวาน + กระทะด้ามยาว 👨‍🍳"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ART_MODE ->
                        "ART_MODE (ศิลปิน)" to "หมวกเบเรต์สีแดง + จานสีแต้ม + พู่กัน 🎨"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SPACE ->
                        "SPACE (นักบินอวกาศ)" to "หมวกนักบินอวกาศกระจกสะท้อน + ดวงดาวโคจร 🚀"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.PARTY ->
                        "PARTY (ปาร์ตี้ฉลอง)" to "หมวกปาร์ตี้ทรงกรวย + แตรเป่า + กระดาษโปรย 🥳"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.DREAMING ->
                        "DREAMING (ฝันหวาน)" to "ตาปิดพริ้ม (─ ─) + ก้อนเมฆฝันนุ่มฟู + อักษร Zzz ☁️"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EXHAUSTED ->
                        "EXHAUSTED (หมดแรง)" to "ตาหรี่ลู่หนัก + ลิ้นห้อย + หยดเหงื่อเหนื่อยล้า 🫠"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ELECTRIC ->
                        "ELECTRIC (ไฟฟ้าช็อต)" to "ตาสายฟ้าซิกแซก (⚡ ⚡) + ประกายไฟประกายดาว ⚡"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SNEAKY ->
                        "SNEAKY (แอบย่อง)" to "หน้ากากโจรคาดตาสีดำ + ตาชำเลืองข้าง + ยิ้มมุมปาก 🦹"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HERO ->
                        "HERO (ซูเปอร์ฮีโร่)" to "หน้ากากฮีโร่ติดปีกสีน้ำเงิน + ตาฮึกเหิม + ประกายดาว 🦸"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.GLITCHED ->
                        "GLITCHED (กลิตช์ดิจิทัล)" to "เส้นสแกนรบกวน + สีแยก RGB Shift แดง-ฟ้า 👾"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.MAGIC ->
                        "MAGIC (พ่อมด/เวทมนตร์)" to "หมวกพ่อมดทรงแหลมสีม่วง + ไม้กายสิทธิ์ดาวเปล่งแสง 🧙"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SPORTY ->
                        "SPORTY (นักกีฬา)" to "ผ้าคาดศีรษะสามสี + ตาเอาจริง + ลูกฟุตบอลขาวดำ ⚽"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SCIENTIST ->
                        "SCIENTIST (นักวิทย์)" to "แว่นตานิรภัยแล็บ + ขวดแก้วทดลองมีฟองฟู่สีเขียว 🧪"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.SCARED ->
                        "SCARED (หวาดกลัว)" to "ตากลมเล็กสั่นระริก + ปากสั่น + วิญญาณหลอน 👻"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.WARRIOR ->
                        "WARRIOR (นักรบ)" to "ผ้าคาดหัวสีแดงตราทอง + ตาขวางคม + ดาบซามูไรคู่ ⚔️"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LOW_BATTERY ->
                        "LOW_BATTERY (แบตเตอรี่ต่ำ)" to "ตาหรี่แสงริบหรี่ + ไอคอนแบตเตอรี่สีแดงกะพริบ 🪫"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.ROMANTIC ->
                        "ROMANTIC (โรแมนติก)" to "แก้มชมพูระเรื่อ + ปากจู๋ '3' + คาบดอกกุหลาบแดง 🌹💋"
                    com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.PUZZLED ->
                        "PUZZLED (งุนงง)" to "ตาหยักคลื่นซิกแซก (~ ~) + เครื่องหมายคำถามกลับด้าน ¿ และ ?? สีแดง ❓"
                }
                _messages.value = _messages.value + Message(
                    "model",
                    "🎭 **ทดสอบ Avatar โหมด: $title**\n• รายละเอียด: $detail\n• พิมพ์ `/avatar reset` เพื่อกลับสู่โหมดตรวจจับอัตโนมัติ",
                    isStatic = true
                )
                onTestEmotion?.invoke(matchedEmotion, "🧪 [TEST] $title: $detail")
            }
            else -> {
                _messages.value = _messages.value + Message(
                    "model",
                    "💡 **คำสั่งทดสอบ 50 LOOI Robot Moodsets & Facial Expressions:**\n" +
                    "• `/avatar all` หรือ `/avatar demo` — รันการแสดงโชว์วนลูปครบทั้ง 50 หน้า (แผ่น 1: 1-20, แผ่น 2: 21-50)\n" +
                    "• `/avatar <1-50>` — แสดง Moodset เฉพาะหน้าที่ 1 ถึง 50 (เช่น `/avatar 10`, `/avatar 11`)\n" +
                    "• `/avatar <ชื่ออารมณ์>` เช่น `/avatar vr_mode`, `/avatar diving`, `/avatar sick`, `/avatar rich`\n" +
                    "• `/avatar reset` — ยกเลิกการทดสอบ กลับสู่โหมดอัตโนมัติ",
                    isStatic = true
                )
            }
        }
    }

    private fun handleAlwaysLiveCommand(cmd: String) {
        val lower = cmd.lowercase()
        // ในภาษาไทย "เปิด" มี substring "ปิด" อยู่ข้างในเสมอ ต้องตัด "เปิด" ออกก่อนเช็ค "ปิด"
        val lowerWithoutOpen = lower.replace("เปิด", "")
        val isOff = lowerWithoutOpen.contains("ปิด") || lowerWithoutOpen.contains("ออก") || lowerWithoutOpen.contains("off") || lowerWithoutOpen.contains("stop") || lowerWithoutOpen.contains("disable")
        val isDrive = lower.contains("ขับขี่") || lower.contains("รถยนต์") || lower.contains("drive") || lower.contains("car")
        val isPet = lower.contains("สัตว์เลี้ยง") || lower.contains("แก้เบื่อ") || lower.contains("pet")
        val action = if (isOff) "off" else "on"
        val mode = when {
            isPet -> "pet"
            isDrive -> "drive"
            else -> "control"
        }

        _messages.value = _messages.value + Message("user", cmd)

        scope.launch {
            val res = com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                com.skyliner2008.jarvis.tools.ToolCall(
                    name = "device_always_live",
                    args = mapOf("action" to action, "mode" to mode)
                )
            )
            _messages.value = _messages.value + Message("model", res.result, isStatic = true)
        }
    }
}
