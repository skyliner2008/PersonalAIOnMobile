package com.skyliner2008.jarvis.sim

import com.skyliner2008.jarvis.LogTap
import com.skyliner2008.jarvis.ai.LiveToolBridge
import com.skyliner2008.jarvis.avatar.AvatarActivity
import com.skyliner2008.jarvis.avatar.AvatarCards
import com.skyliner2008.jarvis.avatar.AvatarLiveFeed
import com.skyliner2008.jarvis.avatar.AvatarTasks
import com.skyliner2008.jarvis.avatar.ToolSceneSync
import com.skyliner2008.jarvis.avatar.ToolSceneWords
import com.skyliner2008.jarvis.data.GeminiService
import com.skyliner2008.jarvis.data.LiveGeminiService
import com.skyliner2008.jarvis.data.PendingAnswerQueue
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.LiveModeState
import com.skyliner2008.jarvis.tools.ToolCall
import com.skyliner2008.jarvis.tools.ToolExecutor
import com.skyliner2008.jarvis.tools.ToolResult
import com.skyliner2008.jarvis.avatar.AvatarToolData
import io.ktor.client.HttpClient
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * A live session in Avatar mode, replayed in real time on the JVM: the app's own [LiveGeminiService] (frames,
 * turns, the report queue), [LiveToolBridge] (tool calls, the boss/worker hand-off) and Avatar mode's
 * [ToolSceneSync] + [AvatarActivity], against a fake Live server that behaves like the one in the phone's logs
 * (2026-10-02):
 * - the user's words arrive as one transcript at the end of their turn, the tool call right after it;
 * - talking over the model interrupts it: `interrupted`, a 0-byte `turnComplete`, and the calls it has not had
 *   answers to are cancelled;
 * - audio arrives 3× faster than it plays; `turnComplete` comes when the voice has played out;
 * - a delegated job's answer (`DELEGATED:`) gets the one-sentence acknowledgement; a result gets an answer;
 *   a report the app sends (`[PENDING QUESTION]`) is told about 1.8 s later.
 * Tools are stubs ([FakeTool]: a delay, the text, the scene data). The run writes a timeline: the user, the
 * model (what it said and when it was heard), the app's own log lines, the robot's scenes and activity.
 */
class LiveSessionSim(
    private val name: String,
    private val plans: List<Plan>,
    private val tools: Map<String, FakeTool>,
    private val talk: Map<String, Talk>,
    private val options: Options = Options(),
) {
    /** One thing the user says: starts [atMs] after the session is ready, lasts [durMs]. */
    data class Say(val atMs: Long, val text: String, val durMs: Long)

    /** What the model does when the user says something containing [match]: these tool calls, or [speak] without tools. */
    data class Plan(val match: String, val calls: List<Pair<String, Map<String, String>>>, val speak: Talk? = null)

    /** A stub tool: answers after [delayMs] with [text]; [data] = what it publishes for the robot's scene. */
    data class FakeTool(val delayMs: Long, val text: String, val data: JsonElement? = null)

    /** What the model says: [text] (its words) over [durMs] of voice. */
    data class Talk(val text: String, val durMs: Long)

    data class Options(
        val vadSilenceMs: Long = 800,
        val ackLatencyMs: Long = 700,
        val answerLatencyMs: Long = 2_200,
        val reportLatencyMs: Long = 1_800,
        /** the server sits on a report until the app sends audioStreamEnd (seen 12:57: 9.9 s and 27.8 s) */
        val reportStuckUntilStreamEnd: Boolean = false,
        val bargeInMs: Long = 250,
        /** the model thinks this long after the user's words before it calls a tool (logcat 14:54:02: 1.5 s) */
        val thinkMs: Long = 5,
        /** the server hears nothing the user says (logcat 2026-10-03 08:39–08:41): no transcript, no answer */
        val deafServer: Boolean = false,
    )

    /** One thing the model said and when the user heard it (playback), and whether it was cut. */
    data class Heard(val tag: String, val text: String, val fromMs: Long, var toMs: Long, var cut: Boolean = false)

    class Result(
        val name: String,
        val timeline: List<String>,
        val heard: List<Heard>,
        val scenes: List<Pair<Long, String>>,
        val activities: List<Pair<Long, String>>,
        val problems: List<String>,
        val boards: List<Pair<Long, String>> = emptyList(),
    ) {
        /** how often the model told [tool]'s result (an answer or a report) all the way through */
        fun toldFully(tool: String) = heard.count { !it.cut && (it.tag == "answer:$tool" || it.tag == "report:$tool") }
        fun told(tool: String) = heard.count { it.tag == "answer:$tool" || it.tag == "report:$tool" }
        fun report(): String = buildString {
            appendLine("══ $name")
            timeline.forEach { appendLine(it) }
            appendLine("── heard")
            heard.forEach { appendLine("  %6.2f–%6.2f %-34s %s%s".format(it.fromMs / 1000.0, it.toMs / 1000.0, it.tag, it.text.take(50), if (it.cut) "  [CUT]" else "")) }
            appendLine("── problems (${problems.size})")
            problems.forEach { appendLine("  ✗ $it") }
        }
    }

    // ---------------------------------------------------------------- clock and timeline

    private var t0 = 0L
    private fun now() = System.currentTimeMillis() - t0
    private val timeline = Collections.synchronizedList(mutableListOf<String>())
    private val problems = Collections.synchronizedList(mutableListOf<String>())
    private val heard = Collections.synchronizedList(mutableListOf<Heard>())
    private val scenes = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())
    private val activities = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())
    private val boards = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())
    @Volatile private var lastBoard = ""

    private fun note(kind: String, msg: String) {
        timeline += "%7.2f %-8s %s".format(now() / 1000.0, kind, msg)
    }

    private fun problem(msg: String) {
        problems += "%.2f %s".format(now() / 1000.0, msg)
        note("PROBLEM", msg)
    }

    // ---------------------------------------------------------------- the app's side

    private val client = HttpClient()
    private val service = LiveGeminiService(client, "sim-key", "gemini-3.1-flash-live-preview")
    private val bridge = LiveToolBridge(service, GeminiService(client, "", "sim"))
    private val ws = FakeWsSession()
    private val json = Json { ignoreUnknownKeys = true }

    private val serverThread = Executors.newSingleThreadExecutor { r -> Thread(r, "fake-live-server") }.asCoroutineDispatcher()
    private val server = CoroutineScope(SupervisorJob() + serverThread)
    private val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toApp = Channel<String>(Channel.UNLIMITED)

    // playback: what the speaker plays, as VoiceController does (paced to real time, cut on interrupt)
    @Volatile private var playEndAt = 0L
    @Volatile private var aiSpeaking = false
    // the user's voice as the phone's mic hears it (App.kt userSpeakingHold: held 700 ms over pauses)
    @Volatile private var userVoiceUntil = 0L
    @Volatile private var userHoldUntil = 0L

    // ---------------------------------------------------------------- the fake server's state (server thread only)

    private var nextCall = 1
    /** the model's current turn: what it is answering, the calls it waits on, the speech it is giving */
    private var turnOpen = false
    private val waitingCalls = linkedMapOf<String, String>()          // id → tool name
    private val turnResults = mutableListOf<Pair<String, String>>()   // (tool, response) this turn
    private var turnJob: Job? = null
    private var speaking: Heard? = null
    private val inbox = ArrayDeque<String>()                          // app texts waiting for the turn to end
    private var stuckReport: String? = null
    /** bumped when the user talks over the model: a turn it was still thinking about is gone */
    private var turnSeq = 0

    suspend fun run(script: List<Say>, totalMs: Long): Result {
        LiveModeState.update(AlwaysLiveProfile.PET)          // Avatar mode (also JarvisPersona.isPetMode)
        AvatarLiveFeed.jobs(emptyList())
        AvatarLiveFeed.reports(emptyList())
        ToolExecutor.testOverride = { call -> runTool(call) }
        LogTap.sink = { tag, msg -> appLog(tag, msg) }
        LiveGeminiService::class.java.getDeclaredField("webSocketSession").apply { isAccessible = true }.set(service, ws)
        service.onInterrupted = { playEndAt = System.currentTimeMillis(); aiSpeaking = false }
        t0 = System.currentTimeMillis()
        try {
            startApp()
            server.launch {
                for (frame in ws.sent) {
                    if (frame is Frame.Close) {
                        note("server", "⬇ the app closed the session")
                        continue
                    }
                    onClientFrame((frame as Frame.Text).readText())
                }
            }
            service.pendingGreetingOnReady = "สวัสดีฮับ พร้อมเล่นแล้ว"
            send("""{"setupComplete":{}}""")
            for (say in script) server.launch {
                delay(say.atMs - now())
                userSays(say)
            }
            delay(totalMs)
            finishOpenSpeech()
        } finally {
            bridge.stopCollecting()
            LogTap.sink = null
            ToolExecutor.testOverride = null
            server.cancel()
            app.cancel()
            serverThread.close()
            client.close()
        }
        checkScenes()
        if (lastBoard.isNotEmpty()) problems += "the task list still shows [$lastBoard] at the end"
        if (!options.deafServer && timeline.any { it.contains("🙉") }) problems += "the app thought the server did not hear the user"
        return Result(name, timeline.toList(), heard.toList(), scenes.toList(), activities.toList(), problems.toList(), boards.toList())
    }

    private fun appLog(tag: String, msg: String) {
        if (tag !in setOf("LiveGemini", "LiveBridge", "AvatarStage")) return
        val line = msg.lineSequence().first()
        if (listOf("🤖 JARVIS +", "Session resumption", "Mic streaming", "Audio chunks", "First audio", "🏁 Turn Complete (audio: 0")
                .any { line.contains(it) }) return
        note(if (tag == "LiveGemini") "app" else "bridge", line.take(170))
    }

    // ---------------------------------------------------------------- tools

    private suspend fun runTool(call: ToolCall): ToolResult {
        val tool = tools[call.name] ?: return ToolResult(call.name, "🎭 ปรับ Avatar เรียบร้อยแล้วค่ะบอส!", false)
        delay(tool.delayMs)
        tool.data?.let { AvatarToolData.publishJson(call.name, it) }
        return ToolResult(call.name, tool.text, false)
    }

    // ---------------------------------------------------------------- Avatar mode (as AvatarModeScreen wires it)

    private val sceneSync = ToolSceneSync(
        show = { item -> scenes += now() to item.tool; note("SCENE", "▲ ${item.tool}") },
        release = { scenes += now() to "release"; note("SCENE", "▼ release") }
    )
    private val activity = AvatarActivity(
        play = { id, data -> activities += now() to "play $id"; note("ROBOT", "▶ $id${data?.get("tool")?.let { " ($it)" } ?: ""}") },
        stop = { id -> activities += now() to "stop $id"; note("ROBOT", "■ $id") },
        hear = { }
    )

    private fun startApp() {
        // server frames go through one reader, as the WebSocket loop does
        app.launch { for (frame in toApp) service.handleServerFrame(frame) }
        bridge.startCollecting { "" }
        app.launch {
            AvatarLiveFeed.events.collect { e ->
                val t = now()
                when (e) {
                    is AvatarLiveFeed.Result -> {
                        val data = e.data ?: if (AvatarCards.wants(e.tool) && e.text.isNotBlank()) JsonPrimitive(AvatarCards.text(e.text)) else null
                        if (data != null) {
                            sceneSync.onResult(
                                ToolSceneSync.Item(e.tool, data, ToolSceneWords.of(e.tool, data), t, e.later, PendingAnswerQueue.subject(e.text).orEmpty()), t
                            )
                        }
                    }
                    is AvatarLiveFeed.Deferred -> sceneSync.onDeferred(e.tool, e.subject)
                    is AvatarLiveFeed.Report -> sceneSync.onReport(e.tool, e.subject, t)
                }
            }
        }
        app.launch { AvatarLiveFeed.heardAtMs.collect { if (it > 0L) activity.onHeard(it - t0) } }
        // the robot's task list, as AvatarModeScreen sends it
        app.launch {
            kotlinx.coroutines.flow.combine(AvatarLiveFeed.jobs, AvatarLiveFeed.reports) { jobs, reports -> AvatarTasks.board(jobs, reports) }
                .collect { board ->
                    val shown = board.joinToString { "${it.tool?.removePrefix("trading_") ?: "ask"}:${it.state.word}" }
                    if (shown != lastBoard) {
                        lastBoard = shown
                        boards += now() to shown
                        note("TASKS", "[${shown.ifEmpty { "-" }}]")
                    }
                }
        }
        app.launch {
            AvatarLiveFeed.speech.collect { sceneSync.onSpeech(it.text, it.startMs - t0, it.msPerUnit, now()) }
        }
        app.launch {
            var spoke = false
            while (true) {
                delay(50)
                val abs = System.currentTimeMillis()
                val playing = abs < playEndAt
                val speakingNow = playing || (aiSpeaking && abs < playEndAt + 850)
                if (aiSpeaking != speakingNow) aiSpeaking = speakingNow
                service.setLocalOutputActive(speakingNow)
                // the mic hears the user (VoiceController: not while the AI's voice plays)
                if (abs < userVoiceUntil && !speakingNow) {
                    service.noteLocalUserVoice()
                    userHoldUntil = abs + 700
                }
                val t = now()
                if (speakingNow != spoke) {
                    sceneSync.onSpeaking(speakingNow, t)
                    spoke = speakingNow
                }
            }
        }
        app.launch {
            while (true) {
                delay(100)
                val t = now()
                sceneSync.tick(t)
                val abs = System.currentTimeMillis()
                val tool = AvatarTasks.foreground(AvatarLiveFeed.jobs.value)
                activity.update(t, abs < userHoldUntil && !aiSpeaking, 0.2f, aiSpeaking, tool, sceneSync.current != null, sceneSync.hasWaiting)
            }
        }
        app.launch {
            service.audioOutputFlow.collect { chunk ->
                if (chunk.epoch != service.audioEpoch) return@collect
                val abs = System.currentTimeMillis()
                val dur = chunk.pcm.size * 1000L / 48_000
                playEndAt = maxOf(playEndAt, abs) + dur
                aiSpeaking = true
            }
        }
    }

    // ---------------------------------------------------------------- the fake server

    private fun send(frame: String) {
        toApp.trySend(frame)
    }

    private suspend fun userSays(say: Say) {
        note("USER", "🗣 \"${say.text}\" (${say.durMs} ms)")
        val spokeFrom = System.currentTimeMillis()
        userVoiceUntil = spokeFrom + say.durMs
        // the phone's voice detector: the utterance ends 0.7 s after the voice (VoiceController → noteLocalUtterance)
        app.launch {
            delay(say.durMs + 700)
            service.noteLocalUtterance(spokeFrom, System.currentTimeMillis())
        }
        if (options.deafServer) {
            note("server", "… hears nothing")
            return
        }
        if (stuckReport != null) note("server", "a report waits unanswered while the user talks")
        if (System.currentTimeMillis() - lastReportAt < 1_500) note("server", "the user starts talking ${System.currentTimeMillis() - lastReportAt} ms after a report")
        delay(options.bargeInMs)
        if (turnOpen) bargeIn("the user talks")
        delay(say.durMs - options.bargeInMs + options.vadSilenceMs)
        if (System.currentTimeMillis() < userVoiceUntil) return            // a longer utterance took over
        turnOpen = true
        send(serverContent { put("inputTranscription", buildJsonObject { put("text", say.text) }) })
        val turn = ++turnSeq
        delay(options.thinkMs)
        if (turn != turnSeq) return                                         // talked over while it thought
        answerQuestion(say.text)
    }

    /** The model answers [question]: the tool calls of its plan, or words. */
    private suspend fun answerQuestion(question: String) {
        turnOpen = true
        val plan = plans.firstOrNull { question.contains(it.match) }
        if (plan == null || (plan.calls.isEmpty() && plan.speak == null)) {
            speakLater(Talk("ฮับบอส", 1_200), "chat", options.answerLatencyMs)
            return
        }
        if (plan.calls.isEmpty()) {
            speakLater(plan.speak!!, "chat", options.answerLatencyMs)
            return
        }
        val calls = plan.calls.map { (tool, args) -> "fc_${nextCall++}" to (tool to args) }
        calls.forEach { (id, c) -> waitingCalls[id] = c.first }
        turnResults.clear()
        delay(5)
        send(buildJsonObject {
            put("toolCall", buildJsonObject {
                put("functionCalls", buildJsonArray {
                    calls.forEach { (id, c) ->
                        add(buildJsonObject {
                            put("id", id); put("name", c.first)
                            put("args", buildJsonObject { c.second.forEach { (k, v) -> put(k, v) } })
                        })
                    }
                })
            })
        }.toString())
        note("server", "🔧 ${calls.joinToString { "${it.second.first}#${it.first}" }}")
    }

    private var lastReportAt = 0L

    /** The user talks over the model: it stops, cancels the calls it waited on, ends the turn with no voice. */
    private suspend fun bargeIn(why: String) {
        turnSeq++
        val ids = waitingCalls.keys.toList()
        turnJob?.cancel()
        turnJob = null
        waitingCalls.clear()
        if (ids.isNotEmpty()) send("""{"toolCallCancellation":{"ids":[${ids.joinToString { "\"$it\"" }}]}}""")
        send(serverContent { put("interrupted", true) })
        speaking?.let { h ->
            h.toMs = now(); h.cut = true
            note("MODEL", "✂ cut: ${h.tag} after ${(h.toMs - h.fromMs) / 1000.0}s")
        }
        speaking = null
        delay(30)
        send(serverContent { put("turnComplete", true) })
        note("server", "⚡ interrupted ($why)${if (ids.isNotEmpty()) " — cancelled $ids" else ""}")
        turnOpen = false
        stuckReport = null
    }

    private suspend fun onClientFrame(text: String) {
        val msg = json.parseToJsonElement(text).jsonObject
        msg["realtimeInput"]?.jsonObject?.let { ri ->
            ri["text"]?.jsonPrimitive?.contentOrNull?.let { onAppText(it) }
            if (ri["audioStreamEnd"]?.jsonPrimitive?.contentOrNull == "true") onStreamEnd()
        }
        msg["toolResponse"]?.jsonObject?.get("functionResponses")?.jsonArray?.forEach { r ->
            val o = r.jsonObject
            val id = o["id"]!!.jsonPrimitive.content
            val tool = o["name"]!!.jsonPrimitive.content
            val result = o["response"]?.jsonObject?.get("result")?.jsonPrimitive?.contentOrNull.orEmpty()
            onToolResponse(id, tool, result)
        }
    }

    private suspend fun onAppText(text: String) {
        val isReport = text.contains("[PENDING QUESTION]") || text.contains("ผลจากเครื่องมือ")
        note("server", "⬇ app text: ${text.take(90)}")
        if (isReport) lastReportAt = System.currentTimeMillis()
        if (System.currentTimeMillis() < userVoiceUntil) problem("app text arrived while the user was talking: ${text.take(50)}")
        if (turnOpen) {
            inbox.addLast(text)
            return
        }
        answerAppText(text)
    }

    private suspend fun answerAppText(text: String) {
        turnOpen = true
        if (text.startsWith("สวัสดี")) {
            speakLater(Talk("สวัสดีฮับบอส พร้อมเล่นแล้วฮับ", 2_200), "greeting", 1_500)
            return
        }
        // a question the app asks again: the model answers it as if the user had just asked
        Regex("""ก่อนหน้านี้ผู้ใช้ถามว่า "(.+?)"""").find(text)?.let {
            delay(options.thinkMs)
            answerQuestion(it.groupValues[1])
            return
        }
        val tool = Regex("""เครื่องมือ (\S+)""").find(text)?.groupValues?.get(1)
        val t = tool?.let { talk[it] }
        if (tool == null || t == null) {
            speakLater(Talk("รับทราบฮับ", 1_200), "chat", options.reportLatencyMs)
            return
        }
        if (options.reportStuckUntilStreamEnd) {
            stuckReport = tool
            note("server", "… sits on the report of $tool until audioStreamEnd")
            return
        }
        speakLater(Talk("ส่วนเรื่องที่บอสขอไว้ " + t.text, t.durMs), "report:$tool", options.reportLatencyMs)
    }

    private suspend fun onStreamEnd() {
        note("server", "⬇ audioStreamEnd")
        val tool = stuckReport ?: return
        stuckReport = null
        val t = talk.getValue(tool)
        speakLater(Talk("ส่วนเรื่องที่บอสขอไว้ " + t.text, t.durMs), "report:$tool", 900)
    }

    private suspend fun onToolResponse(id: String, tool: String, result: String) {
        if (waitingCalls.remove(id) == null) {
            problem("the app answered call $id ($tool) that the server no longer waits for")
            return
        }
        turnResults += tool to result
        if (waitingCalls.isNotEmpty()) return
        val real = turnResults.filter { it.first != "device_avatar_emotion" }
        when {
            real.isEmpty() -> finishTurnSilently()
            real.any { it.second.startsWith("DELEGATED") } ->
                speakLater(Talk("รับทราบฮับ กำลังให้ผู้ช่วยดึงข้อมูลให้", 2_400), "ack:${real.first().first}", options.ackLatencyMs)
            else -> {
                val answered = real.first().first
                val t = talk[answered] ?: Talk("เช็คให้แล้วฮับ", 3_000)
                speakLater(t, "answer:$answered", options.answerLatencyMs)
            }
        }
    }

    private suspend fun finishTurnSilently() {
        delay(300)
        send(serverContent { put("turnComplete", true) })
        endTurn()
    }

    private fun speakLater(t: Talk, tag: String, latencyMs: Long) {
        turnOpen = true
        turnJob = server.launch {
            delay(latencyMs)
            speak(t, tag)
        }
    }

    /** Voice 3× faster than it plays, the words with it, `turnComplete` when it has played out. */
    private suspend fun speak(t: Talk, tag: String) {
        val chunkMs = 250L
        val chunks = (t.durMs / chunkMs).toInt().coerceAtLeast(1)
        val words = t.text.split(" ").filter { it.isNotBlank() }
        val pcm = Base64.getEncoder().encodeToString(ByteArray((chunkMs * 48).toInt()))
        val h = Heard(tag, t.text, now(), now() + t.durMs)
        speaking = h
        heard += h
        note("MODEL", "🔊 $tag: \"${t.text.take(60)}\" (${t.durMs / 1000.0}s)")
        val start = System.currentTimeMillis()
        var w = 0
        for (i in 0 until chunks) {
            send(serverContent {
                put("modelTurn", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject { put("mimeType", "audio/pcm;rate=24000"); put("data", pcm) })
                        })
                    })
                })
            })
            val upTo = ((i + 1) * words.size) / chunks
            while (w < upTo) {
                send(serverContent { put("outputTranscription", buildJsonObject { put("text", words[w] + " ") }) })
                w++
            }
            delay(chunkMs / 3)
        }
        delay((start + t.durMs - System.currentTimeMillis()).coerceAtLeast(0))
        h.toMs = now()
        speaking = null
        send(serverContent { put("turnComplete", true) })
        endTurn()
    }

    private suspend fun endTurn() {
        turnOpen = false
        turnJob = null
        waitingCalls.clear()
        val next = inbox.removeFirstOrNull() ?: return
        delay(200)
        answerAppText(next)
    }

    private fun finishOpenSpeech() {
        speaking?.let { it.toMs = now() }
    }

    private fun serverContent(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject { put("serverContent", buildJsonObject(block)) }.toString()

    // ---------------------------------------------------------------- checks

    /** A scene that goes up must be about what the model is saying at that moment (as heard). */
    private fun checkScenes() {
        for ((at, what) in scenes) {
            if (what == "release") continue
            val playing = heard.firstOrNull { at >= it.fromMs - 300 && at <= it.toMs + 300 }
            when {
                playing == null -> problems += "%.2f the %s scene went up while the model said nothing".format(at / 1000.0, what)
                !playing.tag.endsWith(":$what") ->
                    problems += "%.2f the %s scene went up while the model was on %s".format(at / 1000.0, what, playing.tag)
            }
        }
        for (h in heard) {
            val tool = h.tag.substringAfter(":")
            if ((h.tag.startsWith("answer:") || h.tag.startsWith("report:")) && tool in tools && !h.cut) {
                val shown = scenes.any { (at, what) -> what == tool && at >= h.fromMs - 300 && at <= h.toMs }
                if (!shown) problems += "%.2f the model told %s with no scene".format(h.fromMs / 1000.0, tool)
            }
        }
    }

    private class FakeWsSession : DefaultWebSocketSession {
        val sent = Channel<Frame>(Channel.UNLIMITED)
        override val coroutineContext: CoroutineContext = Job()
        override var masking: Boolean = false
        override var maxFrameSize: Long = Long.MAX_VALUE
        override val incoming: ReceiveChannel<Frame> = Channel()
        override val outgoing: SendChannel<Frame> = sent
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override suspend fun flush() {}
        @Deprecated("not used", level = DeprecationLevel.ERROR)
        override fun terminate() {}
        override var pingIntervalMillis: Long = 0L
        override var timeoutMillis: Long = 0L
        override val closeReason: Deferred<CloseReason?> = CompletableDeferred()
        @io.ktor.utils.io.InternalAPI
        override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {}
    }
}
