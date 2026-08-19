package com.example.personalaibot.controller

import com.example.personalaibot.Message
import com.example.personalaibot.ai.JarvisOrchestrator
import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import com.example.personalaibot.maskApiKey
import com.example.personalaibot.memory.JarvisMemoryManager
import com.example.personalaibot.voice.VoiceManager
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

    fun sendMessage(
        text: String,
        speakResponse: Boolean = false,
        attachments: List<com.example.personalaibot.ui.ChatAttachment> = emptyList()
    ) {
        if (text.isBlank() && attachments.isEmpty()) return

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
                        is com.example.personalaibot.ai.ChatStreamEvent.Text -> {
                            currentAiMessage += event.content
                            appendAssistantMessage(currentAiMessage)
                        }
                        is com.example.personalaibot.ai.ChatStreamEvent.ToolStarted -> {
                            logDebug("JarvisVM", "[Chat] Tool started: ${event.toolName}")
                        }
                        is com.example.personalaibot.ai.ChatStreamEvent.ToolResult -> {
                            logDebug("JarvisVM", "[Chat] Tool completed: ${event.toolName}")
                        }
                        is com.example.personalaibot.ai.ChatStreamEvent.System -> {
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
        attachments: List<com.example.personalaibot.ui.ChatAttachment>
    ): Pair<String, List<com.example.personalaibot.data.InlineData>> {
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
            att.base64?.let { com.example.personalaibot.data.InlineData(att.mimeType, it) }
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
}
