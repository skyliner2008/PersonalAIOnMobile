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
        val history = memoryManager.getRecentHistory(20).reversed()
        // ซ่อนข้อความฝั่ง user ที่มาจาก live voice (transcription) — ตอนคุยสดไม่แสดง เปิดแอปใหม่ก็ไม่ควรโผล่
        _messages.value = history
            .filterNot { it.role == "user" && it.metadata?.contains("live_voice") == true }
            .map { Message(it.role, it.content, metadata = it.metadata) }
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

    fun sendMessage(
        text: String,
        speakResponse: Boolean = false,
        attachments: List<com.skyliner2008.jarvis.ui.ChatAttachment> = emptyList()
    ) {
        if (text.isBlank() && attachments.isEmpty()) return

        val cleanText = text.trim()
        if (cleanText.startsWith("/avatar", ignoreCase = true) ||
            cleanText.startsWith("/emotion", ignoreCase = true) ||
            cleanText.startsWith("/test", ignoreCase = true) ||
            cleanText.startsWith("ทำหน้า", ignoreCase = true) ||
            cleanText.contains("แสดงอารมณ์ทั้งหมด") ||
            cleanText.contains("เดโม่อารมณ์")) {
            handleAvatarTestCommand(cleanText)
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
            currentList[currentList.lastIndex] = Message("model", content)
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
        val lower = cmd.lowercase()
        val parts = cmd.split(Regex("\\s+"))
        val target = if (parts.size > 1) parts[1].lowercase() else ""

        val isDemo = target in listOf("demo", "all") || lower.contains("แสดงอารมณ์ทั้งหมด") || lower.contains("เดโม่อารมณ์")
        val isReset = target in listOf("reset", "auto", "stop", "off") || lower.contains("รีเซ็ต") || lower.contains("โหมดปกติ")

        val matchedEmotion = when {
            isDemo || isReset -> null
            target in listOf("happy", "smile") || lower.contains("ดีใจ") || lower.contains("ยิ้ม") || lower.contains("มีความสุข") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.HAPPY
            target in listOf("excited", "star") || lower.contains("ตื่นเต้น") || lower.contains("ดาว") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.EXCITED
            target in listOf("love", "heart") || lower.contains("รัก") || lower.contains("หัวใจ") ->
                com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LOVE
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
            isDemo -> {
                _messages.value = _messages.value + Message(
                    "model",
                    "▶️ **เริ่มโหมด Emotion Showcase Demo!**\nกำลังสลับแสดงท่าทาง ตา ปาก และชุดสีพื้นหลังครบทั้ง 10 อารมณ์ (เปลี่ยนทุก 3.2 วินาที)\n(พิมพ์ `/avatar reset` เพื่อกลับสู่โหมดปกติได้ทุกเมื่อ)",
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
                onTestEmotion?.invoke(null, null)
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
                    "💡 **คำสั่งทดสอบ 10 Facial Expressions & Color Palettes:**\n" +
                    "• `/avatar demo` — รันการแสดงโชว์วนลูปครบทั้ง 10 อารมณ์\n" +
                    "• `/avatar <ชื่ออารมณ์>` หรือสั่งสั้นๆ เช่น:\n" +
                    "  - `/avatar happy` (มีความสุข)\n" +
                    "  - `/avatar excited` (ตื่นเต้น/ตาดาว)\n" +
                    "  - `/avatar love` (ตาหัวใจ)\n" +
                    "  - `/avatar angry` (โกรธ/เตือนภัย)\n" +
                    "  - `/avatar sad` (เศร้า/น้ำตาไหล)\n" +
                    "  - `/avatar sleeping` (หลับ/ZZZ)\n" +
                    "  - `/avatar listening` (กำลังฟัง)\n" +
                    "  - `/avatar thinking` (กำลังคิด/บอลลูน)\n" +
                    "  - `/avatar speaking` (กำลังพูด)\n" +
                    "  - `/avatar idle` (พร้อมทำงาน/วิ้งตา)\n" +
                    "• `/avatar reset` — ยกเลิกการทดสอบ กลับสู่โหมดอัตโนมัติ",
                    isStatic = true
                )
            }
        }
    }
}
