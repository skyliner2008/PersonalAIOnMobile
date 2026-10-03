package com.skyliner2008.jarvis.sim

import com.skyliner2008.jarvis.sim.LiveSessionSim.FakeTool
import com.skyliner2008.jarvis.sim.LiveSessionSim.Options
import com.skyliner2008.jarvis.sim.LiveSessionSim.Plan
import com.skyliner2008.jarvis.sim.LiveSessionSim.Say
import com.skyliner2008.jarvis.sim.LiveSessionSim.Talk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assume
import org.junit.Before
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The boss/worker flow in Avatar mode, replayed in real time against a fake Live server ([LiveSessionSim]):
 * questions in a row, a report the user talks over, a report the server sits on, a tool the user talks over.
 * Every question must be told exactly once, each result's scene must come with its own words.
 *
 *   RUN_SIM_TESTS=1 gradlew :composeApp:testDebugUnitTest --tests "*AvatarMultiTaskSimTest*"
 *
 * Timelines: composeApp/build/sim/<scenario>.txt. About a minute per scenario — not part of the normal run.
 */
class AvatarMultiTaskSimTest {

    @Before
    fun requireFlag() {
        Assume.assumeTrue("ตั้ง RUN_SIM_TESTS=1 เพื่อจำลอง session (เวลาจริง)", System.getenv("RUN_SIM_TESTS") == "1")
    }

    private val out = File("build/sim").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private fun sample(tool: String): JsonElement? =
        File("../pet_godot/data_samples/$tool.json").takeIf { it.exists() }?.let { json.parseToJsonElement(it.readText()) }

    private val tools = mapOf(
        "trading_deep_analysis_suite" to FakeTool(
            1_200,
            "═══ Deep Analysis Suite (Local Engine): BTCUSDT 1h ═══\n💵 ราคาปัจจุบัน: 86154.43\n🏛️ LSD Trend: BULLISH (Confluence TF: 1/4)\n⭐ Summary Score: 50/100",
            sample("trading_deep_analysis_suite")
        ),
        "trading_macro_calendar" to FakeTool(
            300,
            "📅 **Economic Calendar (ForexFactory — Real-time, เวลาไทย)**\n🔴 **Non-Farm Employment Change** (USD) ⏳ [รอประกาศ]",
            sample("trading_macro_calendar")
        ),
        "trading_price" to FakeTool(
            400,
            "**XAUUSD=X** - Real-time Price\nPrice: 4183.8950 USD\nChange: DOWN -0.2887 (-0.01%)",
            sample("trading_price")
        ),
        "device_weather" to FakeTool(
            1_100,
            "⛅ สภาพอากาศ: ตำบล บานา, ปัตตานี, ประเทศไทย\n🌡️ อุณหภูมิ: 29°C (รู้สึกเหมือน 35°C)",
            sample("device_weather")
        ),
    )

    private val talk = mapOf(
        "trading_price" to Talk("ราคาทองคำ ตอนนี้อยู่ที่ 4183 ดอลลาร์ ลดลงเล็กน้อย ถือว่าทรงตัวฮับบอส", 9_000),
        "trading_deep_analysis_suite" to Talk("วิเคราะห์ BTC 5 มิติ ภาพรวมเป็นขาขึ้น แรงซื้อเข้ามาเยอะ คะแนนรวม 50 เต็ม 100 ฮับ", 14_000),
        "trading_macro_calendar" to Talk("ปฏิทิน ตัวเลขเศรษฐกิจ วันนี้ทุ่มครึ่ง มีตัวเลขจ้างงานสหรัฐ คาด 89K ฮับ", 10_000),
        "device_weather" to Talk("สภาพอากาศ ปัตตานี ตอนนี้ 29 องศา ฝนมีโอกาสตก พกร่มด้วยนะฮับ", 7_000),
    )

    private val face = "device_avatar_emotion" to mapOf("action" to "set", "emotion" to "focused")
    private val plans = listOf(
        Plan("5 มิติ", listOf("trading_deep_analysis_suite" to mapOf("symbol" to "BTCUSDT"), face)),
        Plan("ปฏิทิน", listOf("trading_macro_calendar" to mapOf("filter" to "all"))),
        Plan("ทองคำ", listOf("trading_price" to mapOf("symbol" to "XAUUSD=X"), face)),
        Plan("อากาศ", listOf("device_weather" to emptyMap())),
    )

    private val btc = "วิเคราะห์ BTC 5 มิติ"
    private val calendar = "ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้"
    private val gold = "ราคาทองคำปัจจุบันเท่าไหร่"
    private val weather = "สภาพอากาศวันนี้เป็นยังไง"

    private fun run(name: String, script: List<Say>, totalMs: Long, options: Options = Options(), slowTools: Map<String, Long> = emptyMap()): LiveSessionSim.Result {
        val t = tools.mapValues { (k, v) -> slowTools[k]?.let { v.copy(delayMs = it) } ?: v }
        val result = runBlocking { LiveSessionSim(name, plans, t, talk, options).run(script, totalMs) }
        File(out, "$name.txt").writeText(result.report())
        println(result.report())
        return result
    }

    private fun LiveSessionSim.Result.expectTold(vararg toolNames: String) {
        for (tool in toolNames) assertEquals(1, toldFully(tool), "$name: $tool told all the way through once\n${report()}")
    }

    /** seconds into the run of the first timeline line containing [text] (after [afterS] seconds) */
    private fun LiveSessionSim.Result.at(text: String, afterS: Double = -1.0): Double =
        timeline.map { it.trim().substringBefore(" ").toDouble() to it }
            .first { (t, line) -> t > afterS && line.contains(text) }.first

    /** BTC's analysis when it takes longer than LiveToolBridge's QUICK_ANSWER_MS (1.5 s): it goes to the assistant */
    private val slowBtc = mapOf("trading_deep_analysis_suite" to 4_000L)

    /**
     * logcat 2026-10-02 15:26 and 14:56: three questions a second or so apart. Each next question cuts the one before
     * while it still runs (BTC) or before its answer is heard (the calendar): both wait on the robot's task list while
     * gold is answered, each is told in turn, and nothing is left at the end.
     */
    @Test
    fun `three questions in a row`() {
        val r = run("rapid_three", listOf(Say(2_000, btc, 1_600), Say(5_200, calendar, 2_000), Say(9_000, gold, 1_800)), 85_000)
        r.expectTold("trading_price", "trading_deep_analysis_suite", "trading_macro_calendar")
        assertTrue(r.problems.isEmpty(), r.report())
        val boards = r.boards.map { it.second }
        assertTrue(boards.any { it == "deep_analysis_suite:ready, macro_calendar:ready" }, "both jobs wait on the list\n${r.report()}")
        assertTrue(boards.any { it.startsWith("deep_analysis_suite:telling") }, "the BTC job is being told\n${r.report()}")
        // cut while it runs (the server cancels the call) or just after its result went in (the turn ends unheard) —
        // the next question's interruption, not the one of the greeting the BTC question talked over
        val btcListed = r.boards.first { it.second.contains("deep_analysis_suite") }.first / 1000.0
        val btcCut = r.at("⚡ interrupted (the user talks)", afterS = r.at("🔧 trading_deep_analysis_suite"))
        assertTrue(btcListed - btcCut < 0.5, "on the list within half a second of the next question cutting it\n${r.report()}")
    }

    /**
     * The user waits for each answer before the next question: quick jobs (BTC 1.2 s, the calendar 0.3 s) are answered
     * in their own turn — no "รับทราบ…", no report later, nothing on the task list.
     */
    @Test
    fun `three questions with pauses`() {
        val r = run("paced_three", listOf(Say(2_000, btc, 1_600), Say(23_000, calendar, 2_000), Say(40_000, gold, 1_800)), 65_000)
        r.expectTold("trading_price", "trading_deep_analysis_suite", "trading_macro_calendar")
        assertTrue(r.problems.isEmpty(), r.report())
        assertTrue(r.heard.none { it.tag.startsWith("ack:") }, "no acknowledgement\n${r.report()}")
        assertTrue(r.heard.none { it.tag.startsWith("report:") }, "every answer in its own turn\n${r.report()}")
        assertTrue(r.timeline.none { it.contains("👷 ส่งต่อ") }, "nothing handed over\n${r.report()}")
        assertEquals(2, r.timeline.count { it.contains("หัวหน้าตอบเอง") }, r.report())
        assertTrue(r.boards.none { it.second.isNotEmpty() }, "the task list stays empty\n${r.report()}")
    }

    /**
     * A job that runs past 1.5 s goes to the assistant: the acknowledgement, the job on the task list from that moment,
     * the report when it is done.
     */
    @Test
    fun `a slow job goes to the assistant`() {
        val r = run("slow_handed_over", listOf(Say(2_000, btc, 1_600)), 40_000, slowTools = slowBtc)
        r.expectTold("trading_deep_analysis_suite")
        assertTrue(r.problems.isEmpty(), r.report())
        assertEquals(1, r.heard.count { it.tag == "ack:trading_deep_analysis_suite" }, r.report())
        assertEquals(1, r.heard.count { it.tag == "report:trading_deep_analysis_suite" }, r.report())
        val handed = r.at("👷 ส่งต่อ trading_deep_analysis_suite")
        val called = r.at("🔧 trading_deep_analysis_suite")
        assertTrue(handed - called in 1.4..2.0, "handed over at the end of the 1.5 s wait (${handed - called} s)\n${r.report()}")
        val listed = r.boards.first { it.second.contains("deep_analysis_suite") }.first / 1000.0
        assertTrue(listed - handed < 0.5, "on the list as it is handed over\n${r.report()}")
    }

    /**
     * A quick job's answer the user talks over 1.5 s in: they barely heard it — told again in full after the new
     * question (as the report it used to be).
     */
    @Test
    fun `a quick answer the user talks over`() {
        val r = run("quick_answer_cut", listOf(Say(2_000, btc, 1_600), Say(9_000, weather, 1_800)), 60_000)
        assertEquals(1, r.heard.count { it.tag == "answer:trading_deep_analysis_suite" && it.cut }, r.report())
        r.expectTold("trading_deep_analysis_suite", "device_weather")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /** A report the user talks over a second in: it is told again in full after the new question. */
    @Test
    fun `a report the user talks over`() {
        val r = run("cut_report", listOf(Say(2_000, btc, 1_600), Say(13_000, weather, 1_800)), 75_000, slowTools = slowBtc)
        r.expectTold("trading_deep_analysis_suite", "device_weather")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /** A report the user talks over five seconds into its voice: it counts as heard and is not told again. */
    @Test
    fun `a report cut halfway`() {
        val r = run("cut_report_midway", listOf(Say(2_000, btc, 1_600), Say(17_500, weather, 1_800)), 60_000, slowTools = slowBtc)
        assertEquals(1, r.told("trading_deep_analysis_suite"), r.report())
        r.expectTold("device_weather")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /**
     * The next question cuts the AI while it still thinks about BTC (no tool called yet): the app asks BTC again later.
     * The AI then runs the analysis in that turn and answers — no "รับทราบ…" and a second wait for a report.
     */
    @Test
    fun `a question cut before its tool call`() {
        val r = run("question_cut_before_call", listOf(Say(2_000, btc, 1_600), Say(4_900, calendar, 2_000)), 75_000,
            Options(thinkMs = 1_500))
        r.expectTold("trading_deep_analysis_suite", "trading_macro_calendar")
        assertEquals(0, r.heard.count { it.tag == "ack:trading_deep_analysis_suite" }, "no acknowledgement for a question asked again\n${r.report()}")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /** The server sits on a report until the app says the user's audio has ended (seen 12:57: 9.9 s, 27.8 s). */
    @Test
    fun `a report the server sits on`() {
        val r = run("stuck_report", listOf(Say(2_000, btc, 1_600)), 45_000, Options(reportStuckUntilStreamEnd = true), slowTools = slowBtc)
        r.expectTold("trading_deep_analysis_suite")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /**
     * logcat 2026-10-03 08:39–08:41: the phone hears the user, the server hears nothing — no transcript, no answer.
     * Each miss is logged; three in a row and the app opens a fresh session.
     */
    @Test
    fun `a session that does not hear the user is opened afresh`() {
        // after the greeting (it plays 1.8–4 s): the AI's own words count as the server being alive
        val r = run("deaf_server", listOf(Say(6_000, gold, 1_500), Say(18_000, gold, 1_500), Say(30_000, gold, 1_500)), 46_000,
            Options(deafServer = true))
        assertEquals(3, r.timeline.count { it.contains("🙉") }, "each utterance the server missed\n${r.report()}")
        assertEquals(1, r.timeline.count { it.contains("🔁 server ไม่ได้ยินผู้ใช้") }, r.report())
        assertTrue(r.timeline.any { it.contains("the app closed the session") }, "the deaf socket is closed\n${r.report()}")
        assertTrue(r.problems.isEmpty(), r.report())
    }

    /** The user asks the next question while a direct tool (the weather) still runs: the server cancels the call. */
    @Test
    fun `a direct tool the user talks over`() {
        val r = run("talk_over_tool", listOf(Say(2_000, weather, 1_800), Say(5_200, gold, 1_800)), 70_000,
            slowTools = mapOf("device_weather" to 3_000))
        r.expectTold("device_weather", "trading_price")
        assertTrue(r.problems.isEmpty(), r.report())
    }
}
