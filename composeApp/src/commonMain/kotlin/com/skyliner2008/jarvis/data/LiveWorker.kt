package com.skyliner2008.jarvis.data

import com.skyliner2008.jarvis.logDebug
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Live workers: jobs for the Live models the voice does not use. Live models have no daily limit (RPM/RPD
 * unlimited), unlike the chat models (Flash-Lite 500 a day, others 20, per project). The voice keeps
 * gemini-3.1-flash-live-preview, which talks best. Workers take gemini-3.8-live, gemini-3.8-live-extended-thinking
 * and gemini-2.5-flash-native-audio-preview-12-2025 (1M TPM, 128K context).
 *
 * Live models answer in audio only ("native audio models only support AUDIO response modality", Live API
 * capabilities), so a worker answers by calling `submit_answer(text)`: the exact text arrives as the call's
 * argument, with no speech-to-text in between, so a 7-line DECISION/BIAS/… format survives. The spoken transcript
 * is the fallback — except on a model that talks while it thinks (3.8 Extended Thinking): only its call counts, and
 * it may come in a later turn. An answer the caller cannot use (not its format) passes the job to the next model.
 * Each job is one session; jobs run one at a time, so the voice session keeps room. Every key is tried in turn; a
 * model that fails is rested. Callers fall back to the chat models when a worker returns null.
 */
object LiveWorker {

    enum class Kind {
        /** summaries, alerts, short analysis */
        GENERAL,
        /** decisions that need reasoning (wake analysis, the strategy supervisor) */
        THINKING,
        /** long input (memory, long text) — the 1M TPM / 128K-context model first */
        HEAVY,
        /** fresh facts — Google Search in the session */
        SEARCH
    }

    data class Answer(val text: String, val model: String, val viaTool: Boolean, val ms: Long)

    const val SUBMIT_TOOL = "submit_answer"

    private const val THINKING_LIVE = "gemini-3.8-live-extended-thinking"
    private const val GENERAL_LIVE = "gemini-3.8-live"
    private const val HEAVY_LIVE = "gemini-2.5-flash-native-audio-preview-12-2025"
    /** the voice's model — never a worker, its TPM is the conversation's */
    const val VOICE_LIVE = "gemini-3.1-flash-live-preview"

    /**
     * THINKING used to start with gemini-3.8-live-extended-thinking. On the phone (2026-10-04 00:26–02:22) it gave no
     * answer to any job: turns ended empty, or it said "กำลังวิเคราะห์ข้อมูลกราฟครับ" and stopped, or — listened to
     * for a later turn — said "ขออภัย เกิดข้อผิดพลาดขึ้นในระบบขณะประมวลผลข้อมูลครับ"; each try held a wake up to 25 s,
     * while gemini-3.8-live answered every wake through the call in 3–7 s. It is the last resort now.
     */
    fun candidates(kind: Kind): List<String> = when (kind) {
        Kind.HEAVY -> listOf(HEAVY_LIVE, GENERAL_LIVE)
        Kind.THINKING, Kind.GENERAL, Kind.SEARCH -> listOf(GENERAL_LIVE, HEAVY_LIVE, THINKING_LIVE)
    }

    /** off = every caller goes straight to the chat models */
    @kotlin.jvm.Volatile var enabled: Boolean = true

    // the app's client and key pool (JarvisOrchestrator.updateGeminiApiKeys); services that run without the UI pass their own
    @kotlin.jvm.Volatile private var defaultClient: HttpClient? = null
    @kotlin.jvm.Volatile private var defaultKeys: List<String> = emptyList()

    fun configure(client: HttpClient, keys: List<String>) {
        defaultClient = client
        defaultKeys = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    /** one worker session at a time — a second Live session next to the voice is the most this asks of the project */
    private val gate = Mutex()
    /** model → rested until (ms): setup refused, model missing, quota on every key */
    private val rested = mutableMapOf<String, Long>()
    private val restLock = Any()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** [ask] with the app's client and keys; null when not configured. */
    suspend fun askDefault(prompt: String, system: String, kind: Kind, timeoutMs: Long, accept: (String) -> Boolean = { true }): Answer? {
        val client = defaultClient ?: return null
        return ask(client, defaultKeys, prompt, system, kind, timeoutMs, accept)
    }

    /**
     * Run one job on a Live worker. [timeoutMs] covers waiting for the gate too. null = no worker answered
     * (disabled, no key, every model failed, or out of time) — use the chat models.
     * [accept]: whether a text answers this job (the caller's format) — a model that gives anything else does not
     * answer, and the next model gets the job.
     */
    suspend fun ask(
        client: HttpClient,
        keys: List<String>,
        prompt: String,
        system: String,
        kind: Kind,
        timeoutMs: Long,
        accept: (String) -> Boolean = { true }
    ): Answer? {
        if (!enabled) return null
        val pool = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (pool.isEmpty() || prompt.isBlank()) return null
        val t0 = System.currentTimeMillis()
        return withTimeoutOrNull(timeoutMs) {
            gate.withLock {
                val waited = System.currentTimeMillis() - t0
                if (waited > 500) logDebug("LiveWorker", "⏳ $kind waited ${waited}ms for the worker")
                runJob(client, pool, prompt, system, kind, t0, accept)
            }
        } ?: run {
            logDebug("LiveWorker", "⌛ $kind no answer in ${timeoutMs}ms — chat models take it")
            null
        }
    }

    private suspend fun runJob(
        client: HttpClient,
        keys: List<String>,
        prompt: String,
        system: String,
        kind: Kind,
        t0: Long,
        accept: (String) -> Boolean
    ): Answer? {
        val models = usableModels(kind)
        if (models.isEmpty()) {
            logDebug("LiveWorker", "⏭ $kind every worker model is resting — chat models take it")
            return null
        }
        for (model in models) {
            for ((i, key) in keys.withIndex()) {
                val outcome = runCatching { session(client, key, model, kind, system, prompt, accept) }
                    .getOrElse { Outcome.Failed(Stage.CONNECT, it::class.simpleName + ": " + LiveProtocol.redactSecrets(it.message)) }
                when (outcome) {
                    is Outcome.Done -> {
                        synchronized(restLock) { missedJobs.remove(model) }
                        val ms = System.currentTimeMillis() - t0
                        logDebug(
                            "LiveWorker",
                            "✅ $kind model=$model key=${i + 1}/${keys.size} ${ms}ms ${outcome.text.length} chars via=${if (outcome.viaTool) "tool" else "speech"}" +
                                (outcome.note?.let { " ($it)" } ?: "")
                        )
                        return Answer(outcome.text, model, outcome.viaTool, ms)
                    }
                    is Outcome.Failed -> {
                        val next = nextStep(outcome)
                        logDebug("LiveWorker", "⚠️ $kind model=$model key=${i + 1}/${keys.size} ${outcome.stage}: ${outcome.reason.take(160)} → $next")
                        when (next) {
                            Next.KEY -> continue
                            Next.MODEL -> { rest(model, restFor(model, outcome)); break }
                        }
                    }
                }
            }
        }
        return null
    }

    // ─── one session ─────────────────────────────────────────────────────────

    internal enum class Stage { CONNECT, SETUP, ANSWER }
    internal enum class Next { KEY, MODEL }

    internal sealed interface Outcome {
        /** [note]: how the answer came when it was not the first turn (a later turn, a resend) — for the log */
        data class Done(val text: String, val viaTool: Boolean, val note: String? = null) : Outcome
        data class Failed(val stage: Stage, val reason: String) : Outcome
    }

    /** quota / rate → the next key (another project); anything else → the next model */
    internal fun nextStep(f: Outcome.Failed): Next {
        val r = f.reason.lowercase()
        return if ("quota" in r || "resource_exhausted" in r || "exceeded" in r || "rate limit" in r || "429" in r) Next.KEY else Next.MODEL
    }

    private suspend fun session(
        client: HttpClient,
        key: String,
        model: String,
        kind: Kind,
        system: String,
        prompt: String,
        accept: (String) -> Boolean
    ): Outcome {
        var outcome: Outcome = Outcome.Failed(Stage.CONNECT, "no session")
        client.webSocket(LiveProtocol.buildUrl(key)) {
            val texts = Channel<String>(Channel.UNLIMITED)
            val reader = launch {
                try {
                    for (frame in incoming) when (frame) {
                        is Frame.Text -> texts.send(frame.readText())
                        is Frame.Binary -> texts.send(frame.readBytes().decodeToString())
                        else -> Unit
                    }
                } finally {
                    texts.close()
                }
            }
            outcome = converse(
                send = { send(Frame.Text(it)) },
                incoming = texts,
                setupJson = setupJson(model, kind, system),
                contentJson = contentJson(prompt),
                setupTimeoutMs = 8_000,
                resendJson = realtimeTextJson(prompt),
                accept = accept,
                followUpMs = FOLLOW_UP_MS[model] ?: 0L
            )
            if (outcome is Outcome.Failed && (outcome as Outcome.Failed).reason.startsWith("closed")) {
                // the server's reason for closing ("quota", "not found" …) decides the next step
                val why = withTimeoutOrNull(1_000) { closeReason.await() }
                if (why != null) outcome = (outcome as Outcome.Failed).copy(reason = "closed: ${why.code} ${why.message}")
            }
            reader.cancel()
            runCatching { close(CloseReason(CloseReason.Codes.NORMAL, "worker done")) }
        }
        return outcome
    }

    /**
     * The worker's side of one session: setup, the job, then the answer — the submit_answer call (exact text), or
     * the spoken transcript when the turn ends without one. Pure over [send]/[incoming], so it is tested without a
     * network.
     */
    internal suspend fun converse(
        send: suspend (String) -> Unit,
        incoming: ReceiveChannel<String>,
        setupJson: String,
        contentJson: String,
        setupTimeoutMs: Long,
        /**
         * The job again as realtime text, sent once when a turn ends with nothing at all (the Live API's way to send
         * text in a session; on 3.x Live models clientContent is meant for the opening history).
         */
        resendJson: String? = null,
        /** whether a text answers this job (the caller's format); a call or a turn with anything else is no answer */
        accept: (String) -> Boolean = { true },
        /**
         * For a model that talks while it thinks: its speech is never the answer, and after a turn that ends without
         * one the session listens this long (of silence) for the answer in a later turn. 0 = speech is the fallback
         * answer and a turn's end is final. gemini-3.8-live-extended-thinking said "กำลังวิเคราะห์ข้อมูลกราฟครับ" and
         * ended its turn, and before that ended turns with nothing (phone 2026-10-04 00:26–02:10) — the session used
         * to close right there.
         */
        followUpMs: Long = 0
    ): Outcome {
        send(setupJson)
        val ready = withTimeoutOrNull(setupTimeoutMs) {
            for (raw in incoming) {
                val msg = parse(raw) ?: continue
                msg.error?.let { return@withTimeoutOrNull "error: ${it.message}" }
                if (msg.setupComplete != null) return@withTimeoutOrNull "ok"
            }
            "closed before setupComplete"
        } ?: return Outcome.Failed(Stage.SETUP, "no setupComplete in ${setupTimeoutMs}ms")
        if (ready != "ok") return Outcome.Failed(Stage.SETUP, ready)

        send(contentJson)
        val speechAnswers = followUpMs <= 0L
        val spoken = StringBuilder()
        // what the session held, for a job with no answer: audio chunks, thought parts, other calls, what it said
        var audio = 0
        var thoughts = 0
        val otherCalls = mutableListOf<String>()
        val said = mutableListOf<String>()
        var turns = 0
        var resent = false
        var firstTurnEnd = 0L
        // > 0 while listening for a later turn: when the model last sent something of a turn
        var listenSince = 0L
        fun failed(why: String) = Outcome.Failed(
            Stage.ANSWER,
            "$why (audio $audio, thoughts $thoughts" +
                (if (otherCalls.isNotEmpty()) ", calls ${otherCalls.joinToString()}" else "") +
                (if (said.isNotEmpty()) ", said \"${said.joinToString(" / ").take(80)}\"" else "") + ")"
        )
        fun note(): String? = when {
            resent -> "after resending as realtime text"
            turns > 0 -> "in turn ${turns + 1}, ${System.currentTimeMillis() - firstTurnEnd}ms after turn 1 ended" +
                (said.firstOrNull()?.let { " saying \"${it.take(40)}\"" } ?: " empty")
            else -> null
        }
        while (true) {
            val next = if (listenSince == 0L) incoming.receiveCatching()
            else withTimeoutOrNull(maxOf(1L, listenSince + followUpMs - System.currentTimeMillis())) { incoming.receiveCatching() }
                ?: return failed("no answer in the ${followUpMs / 1000}s after turn $turns ended")
            val raw = next.getOrNull() ?: break
            if ("\"inlineData\"" in raw) audio++
            if ("\"thought\":true" in raw.replace(" ", "")) thoughts++
            val msg = parse(raw) ?: continue
            msg.error?.let { return Outcome.Failed(Stage.ANSWER, "error: ${it.message}") }
            val calls = msg.toolCall?.functionCalls.orEmpty()
            calls.filter { it.name != SUBMIT_TOOL }.forEach { otherCalls += it.name }
            val sc = msg.serverContent
            // a later turn is under way — usage reports and the like do not hold the session open
            if (listenSince > 0L && (calls.isNotEmpty() || sc?.modelTurn != null || sc?.outputTranscription != null)) {
                listenSince = System.currentTimeMillis()
            }
            calls.firstOrNull { it.name == SUBMIT_TOOL }?.let { call ->
                val text = call.args?.get("text")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (text.isNotBlank()) {
                    return if (accept(text)) Outcome.Done(text, viaTool = true, note = note())
                    else failed("its $SUBMIT_TOOL was not in the asked format: \"${text.take(60)}\"")
                }
            }
            if (sc == null) continue
            sc.outputTranscription?.text?.let { spoken.append(it) }
            sc.modelTurn?.parts?.forEach { p -> p.text?.let { spoken.append(it) } }
            if (sc.turnComplete != true) continue
            val text = spoken.toString().trim()
            spoken.clear()
            if (speechAnswers && text.isNotBlank() && accept(text)) return Outcome.Done(text, viaTool = false, note = note())
            if (text.isNotBlank()) said += text
            turns++
            if (turns == 1) firstTurnEnd = System.currentTimeMillis()
            when {
                // a model that talks while it thinks: listen for its answer in a later turn (up to 3 turns)
                !speechAnswers && turns < 3 -> listenSince = System.currentTimeMillis()
                speechAnswers && text.isBlank() && said.isEmpty() && resendJson != null && !resent -> {
                    resent = true
                    send(resendJson)
                }
                else -> return failed(
                    if (resent) "turn ended with no answer twice"
                    else if (text.isBlank()) "turn ended with no answer"
                    else "turn $turns ended with no answer in the asked format"
                )
            }
        }
        val text = spoken.toString().trim()
        return if (speechAnswers && text.isNotBlank() && accept(text)) Outcome.Done(text, viaTool = false, note = note())
        else failed("closed before an answer")
    }

    private fun parse(raw: String): LiveServerMessage? =
        runCatching { json.decodeFromString(LiveServerMessage.serializer(), raw) }.getOrNull()

    // ─── messages ────────────────────────────────────────────────────────────

    /**
     * Models that refuse a session without a thinking level: gemini-3.8-live-extended-thinking closed the setup with
     * "1007 Thinking level must be specified for this model" (phone, 2026-10-04).
     */
    private val THINKING_LEVEL = mapOf(THINKING_LIVE to "medium")

    /**
     * Models that talk while they think — their speech is no answer, and a turn's end is not the job's end: the
     * session listens this long (of silence) for the submit_answer call in a later turn (see [converse] followUpMs).
     */
    private val FOLLOW_UP_MS = mapOf(THINKING_LIVE to 15_000L)

    internal fun setupJson(model: String, kind: Kind, system: String): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", if (model.startsWith("models/")) model else "models/$model")
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                THINKING_LEVEL[model.removePrefix("models/")]?.let { level ->
                    putJsonObject("thinkingConfig") { put("thinkingLevel", level) }
                }
            }
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", workerSystem(system, kind)) }) }
            }
            put("outputAudioTranscription", JsonObject(emptyMap()))
            putJsonArray("tools") {
                add(buildJsonObject {
                    putJsonArray("functionDeclarations") {
                        add(buildJsonObject {
                            put("name", SUBMIT_TOOL)
                            put(
                                "description",
                                "ช่องทางเดียวที่คำตอบถึงระบบ — ต้องเรียกเสมอ ครั้งเดียว ด้วยคำตอบฉบับเต็มตามรูปแบบที่งานกำหนด " +
                                    "(เสียงพูดไม่มีใครได้ยิน)"
                            )
                            putJsonObject("parameters") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("text") { put("type", "STRING"); put("description", "คำตอบฉบับเต็ม") }
                                }
                                putJsonArray("required") { add("text") }
                            }
                        })
                    }
                })
                if (kind == Kind.SEARCH) add(buildJsonObject { putJsonObject("googleSearch") {} })
            }
        }
    }.toString()

    internal fun contentJson(prompt: String): String = buildJsonObject {
        putJsonObject("clientContent") {
            putJsonArray("turns") {
                add(buildJsonObject {
                    put("role", "user")
                    // the ask again at the end of the job: on the phone gemini-3.8-live spoke its answers instead of
                    // calling (20 s of speech for 375 characters, and spoken numbers come back through speech-to-text)
                    putJsonArray("parts") { add(buildJsonObject { put("text", prompt.trimEnd() + "\n\n" + SUBMIT_REMINDER) }) }
                })
            }
            put("turnComplete", true)
        }
    }.toString()

    /** the job as realtime text (how the voice session sends text) — the resend of an empty turn */
    internal fun realtimeTextJson(prompt: String): String = buildJsonObject {
        putJsonObject("realtimeInput") { put("text", prompt.trimEnd() + "\n\n" + SUBMIT_REMINDER) }
    }.toString()

    internal const val SUBMIT_REMINDER = "(ส่งคำตอบทั้งหมดด้วยการเรียก $SUBMIT_TOOL(text) ทันที — ไม่ต้องพูด)"

    internal fun workerSystem(system: String, kind: Kind): String = buildString {
        if (system.isNotBlank()) appendLine(system.trim())
        appendLine()
        if (kind == Kind.SEARCH) appendLine("ค้นข้อมูลล่าสุดด้วย Google Search ก่อนตอบ")
        append(
            "งานนี้ไม่มีคนฟังเสียง: ส่งคำตอบฉบับเต็มผ่านฟังก์ชัน $SUBMIT_TOOL(text) ครั้งเดียวเท่านั้น ไม่ต้องพูด " +
                "ข้อความใน text ต้องเป็นคำตอบทั้งหมดตามรูปแบบที่งานขอ"
        )
    }

    // ─── models ──────────────────────────────────────────────────────────────

    /** this kind's worker models the project can use now: not the voice's, not dead, not resting */
    fun usableModels(kind: Kind): List<String> {
        val now = System.currentTimeMillis()
        val seen = runCatching { ModelConfig.getLiveFallbackChain() }.getOrDefault(emptyList())
        return candidates(kind).filter { m ->
            m != VOICE_LIVE && !ModelConfig.isModelDead(m) &&
                synchronized(restLock) { (rested[m] ?: 0L) <= now } &&
                (seen.isEmpty() || m in seen || m in ModelConfig.SEED_LIVE_MODELS)
        }
    }

    /** model → jobs in a row it gave no answer to (an empty turn, words that are not the asked format) */
    private val missedJobs = mutableMapOf<String, Int>()

    /**
     * How long a failed model rests: a refused setup 30 min; a job with no answer 2 min the first time, then 30 min —
     * gemini-3.8-live-extended-thinking ended every turn empty on the phone (2026-10-04 00:26) and was tried again on
     * every wake, 3.5 s each time.
     */
    private fun restFor(model: String, f: Outcome.Failed): Long {
        if (f.stage == Stage.SETUP) return 30 * 60_000L
        if (f.stage != Stage.ANSWER) return 2 * 60_000L
        val n = synchronized(restLock) { ((missedJobs[model] ?: 0) + 1).also { missedJobs[model] = it } }
        return if (n >= 2) 30 * 60_000L else 2 * 60_000L
    }

    private fun rest(model: String, ms: Long) {
        synchronized(restLock) { rested[model] = System.currentTimeMillis() + ms }
    }

    internal fun resetForTesting() {
        synchronized(restLock) { rested.clear(); missedJobs.clear() }
        enabled = true
    }
}
