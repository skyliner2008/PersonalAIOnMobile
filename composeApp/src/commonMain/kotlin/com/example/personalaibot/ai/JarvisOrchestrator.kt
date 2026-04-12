package com.example.personalaibot.ai

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.data.GeminiService
import com.example.personalaibot.data.LiveGeminiService
import com.example.personalaibot.memory.JarvisMemoryManager
import com.example.personalaibot.tools.ToolRegistry
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class JarvisOrchestrator(
    private val client: HttpClient,
    private val memoryManager: JarvisMemoryManager,
    private var apiKey: String,
    private var modelName: String,
    private var liveModelName: String,
    private val fileHandler: (suspend (String, Map<String, String>) -> String)? = null
) {
    private val geminiService = GeminiService(client, apiKey, modelName)
    private val liveService   = LiveGeminiService(client, apiKey, liveModelName)
    private val planner       = JarvisPlanner(geminiService)

    init {
        // เชื่อม HttpClient + GeminiService เข้ากับ TradingToolExecutor
        com.example.personalaibot.tools.ToolExecutor.init(client, geminiService)
        // เชื่อม File handler (Platform specific)
        fileHandler?.let { com.example.personalaibot.tools.ToolExecutor.initFileHandler(it) }
    }

    private val toolBridge = LiveToolBridge(
        liveService    = liveService,
        geminiService  = geminiService,
        scope          = CoroutineScope(Dispatchers.IO)
    )

    val audioOutputFlow: Flow<ByteArray> = liveService.audioOutputFlow
    val textOutputFlow: Flow<com.example.personalaibot.data.LiveGeminiService.LiveTextUpdate> = liveService.textOutputFlow
    val activeToolName: StateFlow<String?> = toolBridge.activeToolName

    fun updateConfig(
        newApiKey: String,
        newModelName: String,
        newLiveModelName: String,
        newVoiceName: String = "Aoede"
    ) {
        apiKey        = newApiKey
        modelName     = newModelName
        liveModelName = newLiveModelName
        geminiService.updateConfig(newApiKey, newModelName)
        liveService.updateConfig(newApiKey, newLiveModelName, newVoiceName)
    }

    suspend fun listAvailableModels(): List<GeminiModel> =
        geminiService.listModels()

    fun chatWithHistory(
        text: String,
        historySnapshot: List<Pair<String, String>> = emptyList(),
        coreContext: String = ""
    ): Flow<String> {
        val history = historySnapshot.map { (role, content) ->
            ConversationTurn(role = role, content = content)
        }
        val intent = IntentClassifier.classify(text)

        // ── ใช้ function calling เสมอ เพื่อให้ Trading tools ทำงานใน Chat mode ──
        // enableGrounding ต้องเป็น false เสมอ — Gemini ไม่อนุญาตให้ mix
        // functionDeclarations + googleSearch ใน request เดียวกัน (Error 400)
        return geminiService.generateResponseWithTools(
            prompt          = text,
            history         = history,
            intentAddon     = IntentClassifier.getSystemPromptAddon(intent),
            coreContext     = coreContext,
            enableGrounding = false
        )
    }

    suspend fun startLiveVoiceSessionWithMemory(coreContext: String, historyContext: String = "") {
        toolBridge.startCollecting(memoryContextProvider = { coreContext })

        val toolsForSetup = if (supportsNativeTools(liveModelName)) {
            ToolRegistry.getGeminiTool()
        } else {
            null
        }

        liveService.connectAndListen(tools = toolsForSetup, historyContext = historyContext, coreContext = coreContext)
    }

    suspend fun sendLiveAudioChunk(base64Pcm: String) =
        liveService.sendAudioChunk(base64Pcm)

    suspend fun sendLiveCameraFrame(base64Jpeg: String) =
        liveService.sendImageChunk(base64Jpeg)

    suspend fun endLiveVoiceSession() {
        liveService.disconnect()
    }

    suspend fun performSleepCycle(): Boolean {
        // ใช้ GeminiService ตัวหลักเพื่อรัน Prompt ในเบื้องหลัง
        return memoryManager.performSleepCycle(geminiService)
    }

    private fun supportsNativeTools(model: String): Boolean =
        com.example.personalaibot.data.ModelConfig.supportsNativeTools(model)

    // ─── Tool interception helpers ────────────────────────────────────────────

    /**
     * ค้นหาข้อมูลผ่าน Gemini พร้อม prompt ที่ระบุว่าให้ search
     * (Gemini จะใช้ Google Search grounding ถ้าเปิดอยู่)
     */
    suspend fun searchWithGrounding(query: String): String {
        return geminiService.generateResponse(
            prompt = "ค้นหาข้อมูลล่าสุดเกี่ยวกับ: $query\nให้สรุปผลลัพธ์อย่างกระชับ ตอบเป็นภาษาไทย",
            intentAddon = "คุณต้องค้นหาข้อมูลให้ครบถ้วนและตอบอย่างถูกต้อง อ้างอิงแหล่งข้อมูลถ้ามี"
        )
    }

    /** แปลข้อความผ่าน LLM */
    suspend fun translateText(text: String, targetLang: String): String {
        return geminiService.generateResponse(
            prompt = "แปลข้อความต่อไปนี้เป็นภาษา $targetLang:\n\n$text",
            intentAddon = "ให้แปลอย่างธรรมชาติ ไม่ต้องอธิบายเพิ่มเติม ตอบเฉพาะคำแปลเท่านั้น"
        )
    }

    /** สรุปข้อความผ่าน LLM */
    suspend fun summarizeText(text: String, length: String): String {
        val lengthPrompt = when (length.lowercase()) {
            "short"    -> "สรุปให้สั้นมาก 1-2 ประโยค"
            "detailed" -> "สรุปอย่างละเอียดพร้อมจุดสำคัญ"
            else       -> "สรุปให้กระชับ 3-5 ประโยค"
        }
        return geminiService.generateResponse(
            prompt = "$lengthPrompt:\n\n$text",
            intentAddon = "ให้สรุปเนื้อหาอย่างครบถ้วน ไม่ต้องใส่คำนำ ตอบเฉพาะสรุปเท่านั้น"
        )
    }
}
