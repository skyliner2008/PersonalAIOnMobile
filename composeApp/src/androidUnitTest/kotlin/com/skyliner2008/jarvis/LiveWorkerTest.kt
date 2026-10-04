package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.data.LiveWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Live workers: jobs on the Live models the voice does not use, answered through submit_answer. */
class LiveWorkerTest {

    @AfterTest
    fun reset() = LiveWorker.resetForTesting()

    private fun channelOf(vararg frames: String) = Channel<String>(Channel.UNLIMITED).apply {
        frames.forEach { trySend(it) }
        close()
    }

    private val setupComplete = """{"setupComplete":{}}"""

    @Test
    fun `the answer is the exact text of the submit_answer call`() = runBlocking {
        val verdict = "DECISION: SKIP\nBIAS: NEUTRAL\nLEVEL_SL: -\nLEVEL_TP: -\nCONFIDENCE: 0\nREASON_TH: ขัดกัน\nSUMMARY_TH: รอ"
        val sent = mutableListOf<String>()
        val out = LiveWorker.converse(
            send = { sent += it },
            incoming = channelOf(
                setupComplete,
                // the model may speak a little first — the call's text wins
                """{"serverContent":{"outputTranscription":{"text":"ตัดสินแล้วครับ"}}}""",
                """{"toolCall":{"functionCalls":[{"id":"c1","name":"submit_answer","args":{"text":${Json.encodeToString(String.serializer(), verdict)}}}]}}"""
            ),
            setupJson = "SETUP", contentJson = "CONTENT", setupTimeoutMs = 1_000
        )
        val done = assertIs<LiveWorker.Outcome.Done>(out)
        assertEquals(verdict, done.text)
        assertTrue(done.viaTool)
        assertEquals(listOf("SETUP", "CONTENT"), sent)
    }

    @Test
    fun `without the call the spoken transcript is the answer`() = runBlocking {
        val out = LiveWorker.converse(
            send = {},
            incoming = channelOf(
                setupComplete,
                """{"serverContent":{"outputTranscription":{"text":"ราคาทอง "}}}""",
                """{"serverContent":{"outputTranscription":{"text":"ลดลง 0.9%"}}}""",
                """{"serverContent":{"turnComplete":true}}"""
            ),
            setupJson = "S", contentJson = "C", setupTimeoutMs = 1_000
        )
        val done = assertIs<LiveWorker.Outcome.Done>(out)
        assertEquals("ราคาทอง ลดลง 0.9%", done.text)
        assertFalse(done.viaTool)
    }

    @Test
    fun `a refused setup is a setup failure`() = runBlocking {
        val closed = LiveWorker.converse({}, channelOf(), "S", "C", 1_000)
        assertEquals(LiveWorker.Stage.SETUP, assertIs<LiveWorker.Outcome.Failed>(closed).stage)
        val error = LiveWorker.converse({}, channelOf("""{"error":{"message":"models/x is not found"}}"""), "S", "C", 1_000)
        assertTrue(assertIs<LiveWorker.Outcome.Failed>(error).reason.contains("not found"))
        val empty = LiveWorker.converse({}, channelOf(setupComplete, """{"serverContent":{"turnComplete":true}}"""), "S", "C", 1_000)
        assertEquals(LiveWorker.Stage.ANSWER, assertIs<LiveWorker.Outcome.Failed>(empty).stage)
    }

    @Test
    fun `an empty turn gets the job once more as realtime text`() = runBlocking {
        // phone 2026-10-04 00:30: gemini-3.8-live-extended-thinking ended its clientContent turn with nothing
        val sent = mutableListOf<String>()
        val out = LiveWorker.converse(
            send = { sent += it },
            incoming = channelOf(
                setupComplete,
                """{"serverContent":{"turnComplete":true}}""",
                """{"toolCall":{"functionCalls":[{"id":"c2","name":"submit_answer","args":{"text":"DECISION: SKIP"}}]}}"""
            ),
            setupJson = "S", contentJson = "C", setupTimeoutMs = 1_000, resendJson = "R"
        )
        val done = assertIs<LiveWorker.Outcome.Done>(out)
        assertEquals("DECISION: SKIP", done.text)
        assertTrue(done.note!!.contains("realtime"))
        assertEquals(listOf("S", "C", "R"), sent)

        // empty twice: a failure, and the resend went out only once
        val twice = mutableListOf<String>()
        val failed = LiveWorker.converse(
            send = { twice += it },
            incoming = channelOf(setupComplete, """{"serverContent":{"turnComplete":true}}""", """{"serverContent":{"turnComplete":true}}"""),
            setupJson = "S", contentJson = "C", setupTimeoutMs = 1_000, resendJson = "R"
        )
        assertTrue(assertIs<LiveWorker.Outcome.Failed>(failed).reason.contains("twice"))
        assertEquals(listOf("S", "C", "R"), twice)

        // the resend is realtime text that ends with the ask
        val rt = Json.parseToJsonElement(LiveWorker.realtimeTextJson("งาน")).jsonObject["realtimeInput"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(rt.startsWith("งาน") && rt.endsWith(LiveWorker.SUBMIT_REMINDER))
    }

    private val filler = """{"serverContent":{"outputTranscription":{"text":"กำลังวิเคราะห์ข้อมูลกราฟครับ"}}}"""
    private val turnComplete = """{"serverContent":{"turnComplete":true}}"""
    private val verdictCall = """{"toolCall":{"functionCalls":[{"id":"c3","name":"submit_answer","args":{"text":"DECISION: SKIP"}}]}}"""

    @Test
    fun `a model that talks while it thinks answers in a later turn`() = runBlocking {
        // phone 2026-10-04 02:10: gemini-3.8-live-extended-thinking said this and ended its turn — the session closed
        val out = LiveWorker.converse(
            send = {}, incoming = channelOf(setupComplete, filler, turnComplete, """{"usageMetadata":{"totalTokenCount":9}}""", verdictCall),
            setupJson = "S", contentJson = "C", setupTimeoutMs = 1_000, resendJson = "R", followUpMs = 2_000
        )
        val done = assertIs<LiveWorker.Outcome.Done>(out)
        assertEquals("DECISION: SKIP", done.text)
        assertTrue(done.viaTool)
        assertTrue(done.note!!.contains("turn 2") && done.note!!.contains("กำลังวิเคราะห์"), done.note)
    }

    @Test
    fun `its words alone are no answer, and silence after them ends the job`() = runBlocking {
        // the channel stays open: the model said its line, then nothing — a usage report does not hold the session open
        val open = Channel<String>(Channel.UNLIMITED).apply {
            listOf(setupComplete, filler, turnComplete, """{"usageMetadata":{"totalTokenCount":9}}""").forEach { trySend(it) }
        }
        val t0 = System.currentTimeMillis()
        val out = LiveWorker.converse({}, open, "S", "C", 1_000, resendJson = "R", followUpMs = 300)
        val failed = assertIs<LiveWorker.Outcome.Failed>(out)
        assertEquals(LiveWorker.Stage.ANSWER, failed.stage)
        assertTrue(failed.reason.contains("no answer in the") && failed.reason.contains("กำลังวิเคราะห์"), failed.reason)
        assertTrue(System.currentTimeMillis() - t0 < 3_000)
    }

    @Test
    fun `an answer outside the asked format is no answer`() = runBlocking {
        val isVerdict = { t: String -> t.startsWith("DECISION:") }
        // speech: the filler is not a verdict — the job fails at once, so the next model gets it
        val spoken = LiveWorker.converse({}, channelOf(setupComplete, filler, turnComplete), "S", "C", 1_000, accept = isVerdict)
        assertTrue(assertIs<LiveWorker.Outcome.Failed>(spoken).reason.contains("asked format"))
        // a call with the wrong text is no answer either
        val call = """{"toolCall":{"functionCalls":[{"id":"c4","name":"submit_answer","args":{"text":"รอสักครู่"}}]}}"""
        val called = LiveWorker.converse({}, channelOf(setupComplete, call), "S", "C", 1_000, accept = isVerdict)
        assertTrue(assertIs<LiveWorker.Outcome.Failed>(called).reason.contains("asked format"))
        // and the right one is
        val good = LiveWorker.converse({}, channelOf(setupComplete, verdictCall), "S", "C", 1_000, accept = isVerdict)
        assertEquals("DECISION: SKIP", assertIs<LiveWorker.Outcome.Done>(good).text)
    }

    @Test
    fun `quota moves to the next key, anything else to the next model`() {
        fun f(reason: String) = LiveWorker.Outcome.Failed(LiveWorker.Stage.SETUP, reason)
        assertEquals(LiveWorker.Next.KEY, LiveWorker.nextStep(f("closed: 1011 RESOURCE_EXHAUSTED: You exceeded your current quota")))
        assertEquals(LiveWorker.Next.KEY, LiveWorker.nextStep(f("HTTP 429")))
        assertEquals(LiveWorker.Next.MODEL, LiveWorker.nextStep(f("closed: 1008 models/gemini-3.8-live is not found")))
        assertEquals(LiveWorker.Next.MODEL, LiveWorker.nextStep(f("no setupComplete in 8000ms")))
    }

    @Test
    fun `the setup asks for audio with the answer tool, and search only for search jobs`() {
        val general = Json.parseToJsonElement(LiveWorker.setupJson("gemini-3.8-live", LiveWorker.Kind.GENERAL, "สรุปสั้นๆ")).jsonObject["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", general["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", general["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        val tools = general["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertEquals("submit_answer", tools[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        val system = general["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(system.startsWith("สรุปสั้นๆ") && system.contains("submit_answer"))

        val search = Json.parseToJsonElement(LiveWorker.setupJson("gemini-3.8-live", LiveWorker.Kind.SEARCH, "")).jsonObject["setup"]!!.jsonObject
        assertTrue(search["tools"]!!.jsonArray.any { it.jsonObject.containsKey("googleSearch") })
        assertFalse(general["generationConfig"]!!.jsonObject.containsKey("thinkingConfig"))
    }

    @Test
    fun `the extended-thinking model gets a thinking level, and every job ends with the ask to call`() {
        // phone 2026-10-04: "1007 Thinking level must be specified for this model"
        val thinking = Json.parseToJsonElement(
            LiveWorker.setupJson("gemini-3.8-live-extended-thinking", LiveWorker.Kind.THINKING, "")
        ).jsonObject["setup"]!!.jsonObject
        val level = thinking["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content
        assertTrue(level.isNotBlank())
        // phone 2026-10-04: gemini-3.8-live spoke its answers — the job itself now ends with the ask
        val content = Json.parseToJsonElement(LiveWorker.contentJson("วิเคราะห์")).jsonObject["clientContent"]!!.jsonObject
        val text = content["turns"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.startsWith("วิเคราะห์") && text.endsWith(LiveWorker.SUBMIT_REMINDER))
    }

    @Test
    fun `workers never take the voice model, and each kind starts with the model that answers it`() {
        LiveWorker.Kind.values().forEach { kind ->
            assertFalse(LiveWorker.VOICE_LIVE in LiveWorker.usableModels(kind), "$kind")
        }
        // phone 2026-10-04: the extended-thinking model answered no job — the last resort, after the two that do
        assertEquals("gemini-3.8-live", LiveWorker.usableModels(LiveWorker.Kind.THINKING).first())
        assertEquals("gemini-3.8-live-extended-thinking", LiveWorker.usableModels(LiveWorker.Kind.THINKING).last())
        assertEquals("gemini-2.5-flash-native-audio-preview-12-2025", LiveWorker.usableModels(LiveWorker.Kind.HEAVY).first())
        assertEquals("gemini-3.8-live", LiveWorker.usableModels(LiveWorker.Kind.GENERAL).first())
    }
}
