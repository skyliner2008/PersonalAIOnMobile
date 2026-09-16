package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ai.LiveIntentMatchers
import com.skyliner2008.jarvis.controller.LiveLocalCommandParser
import com.skyliner2008.jarvis.data.LiveAudioChunk
import com.skyliner2008.jarvis.data.LiveGeminiService
import com.skyliner2008.jarvis.data.LiveProtocol
import com.skyliner2008.jarvis.data.LiveRealtimeInputData
import com.skyliner2008.jarvis.data.LiveRealtimeInputMessage
import com.skyliner2008.jarvis.data.LiveServerMessage
import com.skyliner2008.jarvis.data.LiveSessionResumptionConfig
import com.skyliner2008.jarvis.data.LiveSetup
import com.skyliner2008.jarvis.data.LiveSetupMessage
import com.skyliner2008.jarvis.data.LiveToolCallEvent
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import com.skyliner2008.jarvis.data.MeetingArchive
import com.skyliner2008.jarvis.data.MeetingRecord
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests สำหรับ review ระบบ Live ฝั่งมือถือ (2026-09-16)
 */
class LiveSessionReviewFixesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // ── LiveProtocol ────────────────────────────────────────────────

    @Test
    fun `buildUrl uses the key passed for each attempt`() {
        assertTrue(LiveProtocol.buildUrl("KEY_A").endsWith("?key=KEY_A"))
        assertTrue(LiveProtocol.buildUrl("KEY_B").endsWith("?key=KEY_B"))
    }

    @Test
    fun `redactSecrets hides api keys in urls and raw keys`() {
        val msg = "Handshake failed: wss://host/path?key=AIzaSyAbcdefghijklmnopqrstuvwxyz012345&alt=1"
        val redacted = LiveProtocol.redactSecrets(msg)
        assertFalse(redacted.contains("AIzaSyAbcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(redacted.contains("key=***"))
        assertFalse(LiveProtocol.redactSecrets("token AIzaSyAbcdefghijklmnopqrstuvwxyz012345").contains("Abcdefghij"))
        assertEquals("", LiveProtocol.redactSecrets(null))
    }

    @Test
    fun `parseToolArgs tolerates arrays and nested objects`() {
        val raw = """{"toolCall":{"functionCalls":[{"id":"c1","name":"trading_price","args":{"symbols":["XAUUSD","BTCUSD"],"opts":{"tf":"1h"},"n":3}}]}}"""
        val msg = json.decodeFromString<LiveServerMessage>(raw)
        val call = msg.toolCall!!.functionCalls.single()
        val args = LiveProtocol.parseToolArgs(call.args)
        assertEquals("XAUUSD,BTCUSD", args["symbols"])
        assertEquals("3", args["n"])
        assertTrue(args["opts"]!!.contains("1h"))
    }

    @Test
    fun `toolCallCancellation is decoded`() {
        val msg = json.decodeFromString<LiveServerMessage>("""{"toolCallCancellation":{"ids":["a","b"]}}""")
        assertEquals(listOf("a", "b"), msg.toolCallCancellation?.ids)
    }

    @Test
    fun `mergeTranscript supports chunked and cumulative transcripts`() {
        assertEquals("ราคา ทอง", LiveProtocol.mergeTranscript("ราคา", " ทอง"))
        assertEquals("ราคา ทอง", LiveProtocol.mergeTranscript("ราคา", "ราคา ทอง"))
        assertEquals("ทอง", LiveProtocol.mergeTranscript(null, "ทอง"))
    }

    @Test
    fun `reconnect history keeps newest content within limit`() {
        val turns = (1..50).map { "user" to "คำถามที่ $it" }
        val history = LiveProtocol.buildReconnectHistory("เก่า", turns, maxChars = 200)
        assertTrue(history.length <= 200)
        assertTrue(history.contains("คำถามที่ 50"))
    }

    @Test
    fun `audioStreamEnd serializes as realtimeInput flag`() {
        val out = json.encodeToString(LiveRealtimeInputMessage(realtimeInput = LiveRealtimeInputData(audioStreamEnd = true)))
        assertEquals("""{"realtimeInput":{"audioStreamEnd":true}}""", out)
    }

    @Test
    fun `setup enables session resumption with empty config on first connect`() {
        val setup = LiveSetupMessage(LiveSetup(model = "models/x", sessionResumption = LiveSessionResumptionConfig(handle = null)))
        assertTrue(json.encodeToString(setup).contains("\"session_resumption\":{}"))
        val resume = LiveSetupMessage(LiveSetup(model = "models/x", sessionResumption = LiveSessionResumptionConfig(handle = "h1")))
        assertTrue(json.encodeToString(resume).contains("\"session_resumption\":{\"handle\":\"h1\"}"))
    }

    // ── LiveIntentMatchers ──────────────────────────────────────────

    @Test
    fun `price question with ไปที่ is not navigation`() {
        val prompt = "ราคาทองจะไปที่ 2400 ไหม"
        assertFalse(LiveIntentMatchers.isNavigationRequest(prompt))
        assertFalse(LiveIntentMatchers.canRedirectMisroutedTool("trading_price", prompt))
    }

    @Test
    fun `explicit navigation still detected and destination extracted`() {
        val prompt = "นำทางไปเซ็นทรัลเวิลด์"
        assertTrue(LiveIntentMatchers.isNavigationRequest(prompt))
        assertEquals("เซ็นทรัลเวิลด์", LiveIntentMatchers.extractNavigationDestination(prompt))
    }

    @Test
    fun `redirect guards never hijack trading questions`() {
        assertFalse(LiveIntentMatchers.canRedirectMisroutedTool("trading_price", "ราคาทองตอนนี้ แล้วอ่านข้อความด้วย"))
        assertTrue(LiveIntentMatchers.canRedirectMisroutedTool("trading_price", "อ่านแจ้งเตือนหน่อย"))
        assertFalse(LiveIntentMatchers.canRedirectMisroutedTool("device_navigate", "อ่านแจ้งเตือนหน่อย"))
    }

    @Test
    fun `D1 is blocked by default but allowed when user asks for it`() {
        val args = mapOf("symbol" to "XAUUSD@1d")
        assertFalse(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ทองหน่อย"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ทอง D1"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "ขอกราฟรายวันของทอง"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(mapOf("symbol" to "XAUUSD@4h"), "วิเคราะห์ทอง"))
    }

    // ── LiveLocalCommandParser ──────────────────────────────────────

    @Test
    fun `open pet mode is not parsed as close`() {
        val cmd = LiveLocalCommandParser.parse("เปิดโหมดสัตว์เลี้ยง")
        assertEquals(LiveLocalCommandParser.AlwaysLiveCommand(turnOn = true, mode = "pet"), cmd.alwaysLive)
        val off = LiveLocalCommandParser.parse("ปิดโหมดขับขี่")
        assertEquals(LiveLocalCommandParser.AlwaysLiveCommand(turnOn = false, mode = "drive"), off.alwaysLive)
    }

    @Test
    fun `open camera is not parsed as close camera`() {
        assertEquals(true, LiveLocalCommandParser.parse("เปิดกล้องหน่อย").eyeOpen)
        assertEquals(false, LiveLocalCommandParser.parse("ปิดกล้องได้แล้ว").eyeOpen)
        assertEquals(true, LiveLocalCommandParser.parse("นี่คืออะไร").eyeOpen)
    }

    @Test
    fun `read message does not open camera and reply has no local side effect`() {
        assertNull(LiveLocalCommandParser.parse("อ่านข้อความล่าสุดให้หน่อย").eyeOpen)
        val reply = LiveLocalCommandParser.parse("ตอบว่า ได้ครับ เดี๋ยวไป")
        assertNull(reply.alwaysLive)
        assertNull(reply.eyeOpen)
        assertNull(reply.avatar)
    }

    @Test
    fun `avatar emotion command parsed`() {
        val cmd = LiveLocalCommandParser.parse("ทำหน้าดีใจหน่อย")
        assertEquals(LiveLocalCommandParser.AvatarCommand.Emotion(AvatarEmotion.HAPPY), cmd.avatar)
    }

    // ── LiveGeminiService frame handling ────────────────────────────

    @Test
    fun `interrupted bumps audio epoch so buffered chunks are stale`() = runBlocking {
        val service = LiveGeminiService(HttpClient(), "k", "gemini-3.1-flash-live-preview")
        val firstChunk = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.audioOutputFlow.first() }
        }
        service.handleServerFrame("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAAA"}}]}}}""")
        val chunk: LiveAudioChunk = firstChunk.await()
        assertEquals(service.audioEpoch, chunk.epoch)

        service.handleServerFrame("""{"serverContent":{"interrupted":true}}""")
        assertTrue(chunk.epoch != service.audioEpoch, "chunk emitted before barge-in must be stale")
    }

    @Test
    fun `new model turn opens a new chat bubble instead of appending`() = runBlocking {
        val service = LiveGeminiService(HttpClient(), "k", "gemini-3.1-flash-live-preview")
        val updates = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.textOutputFlow.take(3).toList() }
        }
        service.handleServerFrame("""{"serverContent":{"outputTranscription":{"text":"สวัสดี"}}}""")
        service.handleServerFrame("""{"serverContent":{"outputTranscription":{"text":"ค่ะ"}}}""")
        service.handleServerFrame("""{"serverContent":{"turnComplete":true}}""")
        service.handleServerFrame("""{"serverContent":{"outputTranscription":{"text":"เรื่องใหม่"}}}""")
        val list = updates.await()
        assertFalse(list[0].append || list[0].replace, "first chunk must open a new bubble")
        assertTrue(list[1].replace)
        assertEquals("สวัสดีค่ะ", list[1].text)
        assertFalse(list[2].append || list[2].replace, "next turn must not append onto previous bubble")
    }

    @Test
    fun `user turn is finalized once when model starts answering`() = runBlocking {
        val service = LiveGeminiService(HttpClient(), "k", "gemini-3.1-flash-live-preview")
        val finals = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.userTurnFinalFlow.take(1).toList() }
        }
        service.handleServerFrame("""{"serverContent":{"inputTranscription":{"text":"ตอบว่า"}}}""")
        service.handleServerFrame("""{"serverContent":{"inputTranscription":{"text":" ได้ครับ"}}}""")
        service.handleServerFrame("""{"serverContent":{"outputTranscription":{"text":"รับทราบ"}}}""")
        service.handleServerFrame("""{"serverContent":{"outputTranscription":{"text":"ค่ะ"}}}""")
        assertEquals(listOf("ตอบว่า ได้ครับ"), finals.await())
    }

    @Test
    fun `tool call with array args reaches bridge with session generation`() = runBlocking {
        val service = LiveGeminiService(HttpClient(), "k", "gemini-3.1-flash-live-preview")
        val event = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.nativeToolCallFlow.first() }
        }
        service.handleServerFrame("""{"toolCall":{"functionCalls":[{"id":"x1","name":"trading_price","args":{"symbols":["XAUUSD"]}}]}}""")
        val received: LiveToolCallEvent = event.await()
        assertEquals("x1", received.callId)
        assertEquals("XAUUSD", received.args["symbols"])
        assertEquals(service.currentSessionGeneration, received.sessionGeneration)
    }

    // ── รอบที่สอง: tool set ตามโหมด, compression, wake word ─────────

    @Test
    fun `every live profile keeps the same tools except device control which is drive only`() {
        val registry = com.skyliner2008.jarvis.tools.ToolRegistry
        val drive = registry.getLiveGeminiTool(com.skyliner2008.jarvis.pet.AlwaysLiveProfile.DRIVE).functionDeclarations
        val assistant = registry.getLiveGeminiTool(com.skyliner2008.jarvis.pet.AlwaysLiveProfile.CONTROL).functionDeclarations
        val pet = registry.getLiveGeminiTool(com.skyliner2008.jarvis.pet.AlwaysLiveProfile.PET).functionDeclarations

        // ผู้ช่วย/สัตว์เลี้ยง = ผู้ช่วยตัวเดียวกัน ใช้ tool ได้เหมือนกันหมด
        assertEquals(assistant.map { it.name }.toSet(), pet.map { it.name }.toSet())
        // เทรด/MT5 ต้องอยู่ครบทุกโหมด
        assertTrue(pet.any { it.name.startsWith("trading_") }, "pet mode must still be able to use trading tools")
        // ควบคุมเครื่องเต็มรูปแบบ = โหมดขับรถเท่านั้น
        assertTrue(drive.any { it.name == "device_tap" }, "drive mode controls the phone")
        assertTrue(assistant.none { it.name in registry.DEVICE_CONTROL_TOOLS })
        assertEquals(drive.size - registry.DEVICE_CONTROL_TOOLS.size, assistant.size)
    }

    @Test
    fun `chat chain uses only flash-lite models`() {
        com.skyliner2008.jarvis.data.ModelConfig.resetForTesting()
        val chain = com.skyliner2008.jarvis.data.ModelConfig.getFallbackChain()
        assertEquals("gemini-3.5-flash-lite", chain.first())
        assertTrue(chain.contains("gemini-3.1-flash-lite"))
        assertTrue(
            chain.none { !com.skyliner2008.jarvis.data.ModelConfig.isChatAllowedModel(it) },
            "chat chain must not contain 20-per-day flash models: $chain"
        )
    }

    @Test
    fun `exhausted daily quota pushes a model to the end of the chat chain`() {
        val config = com.skyliner2008.jarvis.data.ModelConfig
        config.resetForTesting()
        config.markModelQuotaExhausted("gemini-3.5-flash-lite", dailyQuota = true)
        val chain = config.getFallbackChain()
        assertEquals("gemini-3.1-flash-lite", chain.first())
        assertTrue(chain.contains("gemini-3.5-flash-lite"))
        config.resetForTesting()
    }

    @Test
    fun `live seeds use the models available on this project and skip single-purpose ones`() {
        val config = com.skyliner2008.jarvis.data.ModelConfig
        config.resetForTesting()
        val chain = config.getLiveFallbackChain()
        assertEquals("gemini-3.8-live", chain.first())
        assertTrue(chain.contains("gemini-3.1-flash-live-preview"))
        assertTrue(chain.contains("gemini-3.8-live-extended-thinking"))
        assertTrue(chain.none { it.contains("transcribe") || it.contains("translate") })
        assertFalse(config.isConversationalLiveModel(config.TRANSCRIBE_LIVE_MODEL))
        assertFalse(config.isConversationalLiveModel(config.TRANSLATE_LIVE_MODEL))
        assertTrue(config.isConversationalLiveModel("gemini-3.8-live"))
    }

    @Test
    fun `context window compression serializes a sliding window`() {
        val setup = LiveSetupMessage(
            LiveSetup(
                model = "models/x",
                contextWindowCompression = com.skyliner2008.jarvis.data.LiveContextWindowCompressionConfig.default()
            )
        )
        val encoded = json.encodeToString(setup)
        assertTrue(
            encoded.contains("\"context_window_compression\":{\"slidingWindow\":{}}"),
            "compression config must carry slidingWindow, got: $encoded"
        )
    }

    @Test
    fun `wake word matcher accepts thai transcriptions and rejects noise`() {
        assertTrue(com.skyliner2008.jarvis.voice.WakeWordMatcher.matches("จาวิส ช่วยหน่อย", "JARVIS"))
        assertTrue(com.skyliner2008.jarvis.voice.WakeWordMatcher.matches("hey Jarvis", "JARVIS"))
        assertFalse(com.skyliner2008.jarvis.voice.WakeWordMatcher.matches("เปิดทีวีให้หน่อย", "JARVIS"))
        assertFalse(com.skyliner2008.jarvis.voice.WakeWordMatcher.matches("", "JARVIS"))
        assertTrue(com.skyliner2008.jarvis.voice.WakeWordMatcher.matches("น้องหมี อยู่ไหม", "น้องหมี"))
    }

    @Test
    fun `agent task tools are registered and degrade safely without a runner`() = runBlocking {
        val names = com.skyliner2008.jarvis.tools.ToolRegistry.allToolNames()
        assertTrue(names.containsAll(setOf("agent_task_start", "agent_task_list", "agent_task_status", "agent_task_cancel")))

        val manager = com.skyliner2008.jarvis.automation.agent.AgentTaskManager
        if (!manager.isReady()) {
            val res = com.skyliner2008.jarvis.tools.ToolExecutor.execute(
                com.skyliner2008.jarvis.tools.ToolCall("agent_task_start", mapOf("instruction" to "สรุปข่าวทอง"))
            )
            assertTrue(res.result.contains("AGENT_TASK_UNAVAILABLE"))
        }
        val empty = com.skyliner2008.jarvis.tools.ToolExecutor.execute(
            com.skyliner2008.jarvis.tools.ToolCall("agent_task_list", emptyMap())
        )
        assertTrue(empty.result.contains("AGENT_TASKS"))
        val unknown = com.skyliner2008.jarvis.tools.ToolExecutor.execute(
            com.skyliner2008.jarvis.tools.ToolCall("agent_task_cancel", mapOf("task_id" to "12345"))
        )
        assertTrue(unknown.result.contains("AGENT_TASK_NOT_FOUND"))
    }

    @Test
    fun `agent task runner executes work in background and reports completion`() = runBlocking {
        val manager = com.skyliner2008.jarvis.automation.agent.AgentTaskManager
        manager.initRunner { instruction -> "ผลของ: $instruction" }
        val completion = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(10_000) {
                com.skyliner2008.jarvis.automation.backtest.LongTaskRunner.completions.first()
            }
        }
        val ack = manager.start(title = "ทดสอบงานเบื้องหลัง", instruction = "รวบรวมข้อมูล")
        assertTrue(ack.startsWith("AGENT_TASK_STARTED"), ack)
        val done = completion.await()
        assertEquals("agent", done.kind)
        assertTrue(done.ok)
        assertTrue(done.chatBody.contains("ผลของ: รวบรวมข้อมูล"))
    }

    @Test
    fun `tool response for an old session generation is not sent`(): Unit = runBlocking {
        val service = LiveGeminiService(HttpClient(), "k", "gemini-3.1-flash-live-preview")
        yield()
        assertFalse(service.sendNativeToolResponse("id", "tool", "result", sessionGeneration = 99L))
        assertNotNull(service.connectionState.value)
    }

    // ── จาก log เครื่องจริง 2026-09-16 ──────────────────────────────

    @Test
    fun `spaced thai transcript still matches intent keywords`() {
        // Live transcription ส่ง "ราคา ทองคำ เท่า ไหร่ ตอน นี้" (มีช่องว่างคั่นคำ)
        val spoken = "ราคา ทองคำ เท่า ไหร่ ตอน นี้"
        assertEquals("USER_QUERY", LiveIntentMatchers.tradingProfileFor(spoken))
        // profile นี้อนุญาตเฉพาะ technical analysis — SMC ต้องถูกบล็อก (เดิมหลุดไปรัน แล้วรอ 14 วิ)
        assertFalse(
            LiveIntentMatchers.profileToolAllowed(spoken, "trading_smc_analysis", mapOf("symbol" to "XAUUSD", "interval" to "m15"))
        )
        assertTrue(
            LiveIntentMatchers.profileToolAllowed(spoken, "trading_technical_analysis", mapOf("symbol" to "XAUUSD", "interval" to "m15"))
        )
        assertTrue(LiveIntentMatchers.isTradingQuestion(spoken))
    }

    @Test
    fun `timeframe guard reads the interval argument too`() {
        val args = mapOf("symbol" to "XAUUSD", "interval" to "1d")
        assertFalse(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ ทอง หน่อย"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(args, "วิเคราะห์ ทอง ราย วัน"))
        assertTrue(LiveIntentMatchers.allowedTradingTimeframe(mapOf("symbol" to "XAUUSD", "interval" to "m15"), "วิเคราะห์ ทอง"))
    }

    @Test
    fun `local commands survive spaced transcription`() {
        val on = LiveLocalCommandParser.parse("เปิด โหมด สัตว์ เลี้ยง")
        assertEquals(LiveLocalCommandParser.AlwaysLiveCommand(turnOn = true, mode = "pet"), on.alwaysLive)
        val off = LiveLocalCommandParser.parse("ปิด โหมด ขับขี่")
        assertEquals(LiveLocalCommandParser.AlwaysLiveCommand(turnOn = false, mode = "drive"), off.alwaysLive)
        assertEquals(true, LiveLocalCommandParser.parse("นี่ คือ อะไร").eyeOpen)
        assertEquals(false, LiveLocalCommandParser.parse("ปิด กล้อง ได้ แล้ว").eyeOpen)
    }

    // ── โหมดประชุม / โหมดแปลภาษา (โมเดลเฉพาะทาง) ────────────────────

    @Test
    fun `meeting setup uses transcribe-live with smart text transcription`() {
        val service = com.skyliner2008.jarvis.data.LiveSpecialistService(HttpClient())
        val setup = service.buildSetup(
            mode = com.skyliner2008.jarvis.data.LiveSpecialistService.Mode.MEETING,
            model = com.skyliner2008.jarvis.data.ModelConfig.TRANSCRIBE_LIVE_MODEL,
            targetLanguageCode = "en",
            echoTargetLanguage = false,
            languageCodes = emptyList(),
            customVocabulary = listOf("JARVIS", "XAUUSD")
        )
        assertTrue(setup.contains("\"model\":\"models/gemini-3.5-transcribe-live\""), setup)
        assertTrue(setup.contains("\"response_modalities\":[\"TEXT\"]"), setup)
        assertTrue(setup.contains("\"languageCodes\":[]"), "ปล่อยว่าง = ตรวจภาษาเอง: $setup")
        assertTrue(setup.contains("\"mode\":\"SMART\""), setup)
        assertTrue(setup.contains("\"customVocabulary\":[\"JARVIS\",\"XAUUSD\"]"), setup)
        // โหมดประชุมไม่ส่ง translationConfig
        assertFalse(setup.contains("translationConfig"), setup)
    }

    @Test
    fun `translate setup carries translationConfig and both transcripts`() {
        val service = com.skyliner2008.jarvis.data.LiveSpecialistService(HttpClient())
        val setup = service.buildSetup(
            mode = com.skyliner2008.jarvis.data.LiveSpecialistService.Mode.TRANSLATE,
            model = com.skyliner2008.jarvis.data.ModelConfig.TRANSLATE_LIVE_MODEL,
            targetLanguageCode = "th",
            echoTargetLanguage = true,
            languageCodes = emptyList(),
            customVocabulary = emptyList()
        )
        assertTrue(setup.contains("\"model\":\"models/gemini-3.5-live-translate-preview\""), setup)
        assertTrue(setup.contains("\"response_modalities\":[\"AUDIO\"]"), setup)
        assertTrue(setup.contains("\"translationConfig\":{\"targetLanguageCode\":\"th\",\"echoTargetLanguage\":true}"), setup)
        assertTrue(setup.contains("\"input_audio_transcription\":{}"), setup)
        assertTrue(setup.contains("\"output_audio_transcription\":{}"), setup)
    }

    @Test
    fun `specialist models never leak into the assistant live chain`() {
        val config = com.skyliner2008.jarvis.data.ModelConfig
        config.resetForTesting()
        val chain = config.getLiveFallbackChain()
        assertFalse(chain.contains(config.TRANSCRIBE_LIVE_MODEL))
        assertFalse(chain.contains(config.TRANSLATE_LIVE_MODEL))
    }

    @Test
    fun `asking during a meeting sends the transcript as context`() {
        val transcript = "เรากำลังออกแบบอาคาร 4 ชั้น\nตอนนี้คุยเรื่องปั๊มน้ำของอาคาร"
        val prompt = com.skyliner2008.jarvis.controller.SpecialistSessionController
            .buildAskPrompt("ปั๊มขนาดเท่าไรที่เหมาะสม", transcript)
        assertTrue(prompt.contains("อาคาร 4 ชั้น"), "ต้องแนบบทประชุมไปด้วย โมเดลถึงจะรู้บริบท")
        assertTrue(prompt.contains("ปั๊มน้ำของอาคาร"))
        assertTrue(prompt.contains("ปั๊มขนาดเท่าไรที่เหมาะสม"))
        assertTrue(prompt.contains("ยังไม่ได้พูดถึง"), "ต้องสั่งให้บอกตรงๆ เมื่อบทประชุมไม่มีข้อมูล")
    }

    @Test
    fun `ask prompt keeps the newest part of a very long meeting`() {
        val long = (1..5000).joinToString("\n") { "บรรทัดที่ $it" }
        val prompt = com.skyliner2008.jarvis.controller.SpecialistSessionController
            .buildAskPrompt("สรุปให้หน่อย", long)
        assertTrue(prompt.contains("บรรทัดที่ 5000"), "ต้องเก็บช่วงท้ายไว้ เพราะเป็นเรื่องที่กำลังคุยกันอยู่")
        assertTrue(prompt.contains("ตัดช่วงต้นออก"))
        assertTrue(
            prompt.length < com.skyliner2008.jarvis.controller.SpecialistSessionController.ASK_CONTEXT_MAX_CHARS + 2_000
        )
    }

    @Test
    fun `voice ask prompt carries meeting context and forbids markdown`() {
        val prompt = com.skyliner2008.jarvis.controller.SpecialistSessionController
            .buildVoiceAskPrompt("ปั๊มขนาดเท่าไรที่เหมาะสม", "อาคาร 4 ชั้น\nกำลังคุยเรื่องปั๊มน้ำ")
        assertTrue(prompt.contains("อาคาร 4 ชั้น"))
        assertTrue(prompt.contains("ปั๊มขนาดเท่าไรที่เหมาะสม"))
        assertTrue(prompt.contains("ห้ามใช้ markdown"), "คำตอบถูกอ่านออกเสียง จึงห้าม markdown")
        assertTrue(prompt.contains("ยังไม่ได้พูดถึงในที่ประชุม"))
    }

    @Test
    fun `voice ask without a typed question asks the assistant to wait for speech`() {
        val prompt = com.skyliner2008.jarvis.controller.SpecialistSessionController
            .buildVoiceAskPrompt("", "ประชุมเรื่องงบประมาณ")
        assertTrue(prompt.contains("ประชุมเรื่องงบประมาณ"))
        assertTrue(prompt.contains("รอคำถาม"), "ถ้ายังไม่พิมพ์คำถาม ให้ผู้ช่วยทักสั้นๆ แล้วรอฟัง")
    }

    @Test
    fun `turn complete closes both source and translated segments`() = runBlocking {
        val service = com.skyliner2008.jarvis.data.LiveSpecialistService(HttpClient())
        service.currentMode = com.skyliner2008.jarvis.data.LiveSpecialistService.Mode.TRANSLATE
        val events = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.transcripts.take(2).toList() }
        }
        service.handleFrame("""{"serverContent":{"turnComplete":true}}""")
        val closed = events.await()
        // เดิมปิดเฉพาะ SOURCE ทำให้คำแปลทุกประโยคถูกต่อกันเป็นย่อหน้าเดียว
        assertEquals(
            setOf(
                com.skyliner2008.jarvis.data.LiveSpecialistService.TranscriptKind.SOURCE,
                com.skyliner2008.jarvis.data.LiveSpecialistService.TranscriptKind.TRANSLATED
            ),
            closed.map { it.kind }.toSet()
        )
        assertTrue(closed.all { it.segmentClosed })
    }

    @Test
    fun `meeting mode ignores modelTurn text so lines are not duplicated`() = runBlocking {
        val service = com.skyliner2008.jarvis.data.LiveSpecialistService(HttpClient())
        service.currentMode = com.skyliner2008.jarvis.data.LiveSpecialistService.Mode.MEETING
        val events = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { service.transcripts.take(1).toList() }
        }
        service.handleFrame(
            """{"serverContent":{"modelTurn":{"parts":[{"text":"ซ้ำ"}]},"inputTranscription":{"text":"ประชุมเรื่องปั๊มน้ำ"}}}"""
        )
        val received = events.await()
        assertEquals(1, received.size)
        assertEquals("ประชุมเรื่องปั๊มน้ำ", received.first().text)
    }
    @Test
    fun `Meeting archive keeps newest records first and caps the list`() {
        val many = (1..60).map {
            MeetingRecord(
                id = it.toLong(),
                title = "rec$it",
                durationMs = 1000L,
                summary = "s$it",
                transcript = "t$it"
            )
        }
        val decoded = MeetingArchive.decode(MeetingArchive.encode(many))
        assertEquals(MeetingArchive.MAX_RECORDS, decoded.size)
        assertEquals(60L, decoded.first().id, "ต้องเก็บรายการใหม่สุดไว้ก่อน")
        assertTrue(decoded.none { it.id <= 10L }, "รายการเก่าสุดต้องถูกตัดทิ้ง")
    }

    @Test
    fun `Meeting read-aloud prompt carries the summary and bans markdown reading`() {
        val prompt = MeetingArchive.buildReadAloudPrompt(
            MeetingRecord(1L, "17/09/2026-01:19", 60_000L, "### หัวข้อ\n- ปั๊มน้ำอาคาร 4 ชั้น", "raw")
        )
        assertTrue(prompt.contains("ปั๊มน้ำอาคาร 4 ชั้น"), "ต้องแนบเนื้อหาสรุปไปด้วย")
        assertTrue(prompt.contains("ห้ามอ่าน markdown"), "ต้องสั่งห้ามอ่าน markdown ตามตัวอักษร")
    }

    @Test
    fun `Corrupt meeting archive falls back to an empty list`() {
        assertEquals(0, MeetingArchive.decode("{not json").size)
        assertEquals(0, MeetingArchive.decode("").size)
    }
}
