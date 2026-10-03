package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.AvatarActivity
import com.skyliner2008.jarvis.avatar.AvatarCards
import com.skyliner2008.jarvis.avatar.AvatarLiveFeed
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The robot between answers in Avatar mode: listening, pondering, the working card. */
class AvatarActivityTest {
    private val commands = mutableListOf<String>()
    private var hears = 0
    private val activity = AvatarActivity(
        play = { id, data -> commands += if (data == null) "play $id" else "play $id ${tool(data)}" },
        stop = { commands += "stop $it" },
        hear = { hears++ }
    )

    private fun tool(data: JsonObject) = (data["tool"] as? JsonPrimitive)?.content.orEmpty()

    private val deep = AvatarLiveFeed.ToolRun("trading_deep_analysis_suite", mapOf("symbol" to "BTCUSDT", "interval" to "1h"))
    private val price = AvatarLiveFeed.ToolRun("trading_price", mapOf("symbol" to "XAUUSD=X"))

    // the session, as the screen sees it every 100 ms
    private var talking = false
    private var speaking = false
    private var running: AvatarLiveFeed.ToolRun? = null
    private var sceneUp = false
    private var waiting = false

    private fun run(fromMs: Long, toMs: Long) {
        var t = fromMs
        while (t <= toMs) {
            activity.update(t, talking, 0.2f, speaking, running, sceneUp, waiting)
            t += 100
        }
    }

    @Test
    fun `a question with a tool - listening, pondering, the working card, then the tool's own scene`() {
        talking = true
        run(0, 1_000)
        activity.onHeard(800)
        run(1_100, 2_000)
        talking = false
        run(2_100, 2_900)                       // the user has finished: the AI thinks
        running = deep
        run(3_000, 5_900)                       // the AI runs the deep analysis
        running = null
        waiting = true
        run(6_000, 6_400)                       // done, the AI is still silent: the card stays
        speaking = true
        run(6_500, 7_400)                       // the AI talks, its result waits for its words
        sceneUp = true
        waiting = false
        run(7_500, 9_000)                       // the deep analysis scene took over
        assertEquals(
            listOf("play listening", "play pondering", "play working trading_deep_analysis_suite", "stop working"),
            commands
        )
        assertTrue(hears in 15..25, "the meter follows the voice ~12 times a second: $hears")
        assertEquals(AvatarActivity.Kind.NONE, activity.current)
    }

    @Test
    fun `a clatter is not listened to, a short word is not pondered over`() {
        talking = true
        run(0, 200)
        talking = false
        run(300, 2_000)
        assertEquals(emptyList(), commands)
        talking = true
        run(3_000, 3_600)
        talking = false
        run(3_700, 6_000)
        assertEquals(listOf("play listening", "stop listening"), commands)
    }

    @Test
    fun `pondering ends after 3,5 s when the server heard no words, after the AI's first word when it did`() {
        talking = true
        run(0, 1_500)
        talking = false
        run(1_600, 4_900)
        assertEquals(AvatarActivity.Kind.PONDERING, activity.current)
        run(5_000, 5_200)
        assertEquals(listOf("play listening", "play pondering", "stop pondering"), commands)

        commands.clear()
        talking = true
        run(10_000, 11_500)
        activity.onHeard(10_600)
        talking = false
        run(11_600, 15_900)
        assertEquals(AvatarActivity.Kind.PONDERING, activity.current, "heard words: the AI may take longer")
        speaking = true
        run(16_000, 16_100)
        assertEquals(listOf("play listening", "play pondering", "stop pondering"), commands)
    }

    @Test
    fun `the AI talks with no result coming - the card goes at once`() {
        running = price
        run(0, 1_000)
        running = null
        run(1_100, 1_500)
        speaking = true
        run(1_600, 1_700)
        assertEquals(listOf("play working trading_price", "stop working"), commands)
    }

    @Test
    fun `the card waits 2,5 s at most for the result's scene, 8 s for a silent AI`() {
        running = price
        run(0, 500)
        running = null
        waiting = true
        speaking = true
        run(600, 3_000)
        assertEquals(AvatarActivity.Kind.WORKING, activity.current)
        run(3_100, 3_200)
        assertEquals(listOf("play working trading_price", "stop working"), commands)

        commands.clear()
        speaking = false
        running = deep
        run(10_000, 10_500)
        running = null
        run(10_600, 18_500)
        assertEquals(AvatarActivity.Kind.WORKING, activity.current)
        run(18_600, 18_700)
        assertEquals(listOf("play working trading_deep_analysis_suite", "stop working"), commands)
    }

    @Test
    fun `a second tool updates the card, the user talking over a tool is listened to`() {
        running = deep
        run(0, 1_000)
        running = price
        run(1_100, 1_500)
        talking = true
        run(1_600, 2_500)
        talking = false
        run(2_600, 2_800)
        assertEquals(
            listOf("play working trading_deep_analysis_suite", "play working trading_price", "play listening", "play working trading_price"),
            commands
        )
    }

    @Test
    fun `nothing starts over a tool's scene`() {
        sceneUp = true
        talking = true
        running = price
        run(0, 2_000)
        assertEquals(emptyList(), commands)
    }

    @Test
    fun `answers get the working card and a text card, actions do not`() {
        assertTrue(AvatarActivity.showsWork("trading_deep_analysis_suite"))
        assertTrue(AvatarActivity.showsWork("device_weather"))
        assertTrue(AvatarActivity.showsWork("calculate"))
        assertFalse(AvatarActivity.showsWork("device_flashlight"))
        assertFalse(AvatarActivity.showsWork("trading_mt5_order"))
        assertFalse(AvatarActivity.showsWork("device_avatar_emotion"))
        assertTrue(AvatarCards.wants("trading_elliot_modern_analysis"))
        assertFalse(AvatarCards.wants("device_open_app"))
    }
}

/** Tool texts made into the robot's short markdown cards (real tool output, 2026-10-02). */
class AvatarCardsTest {
    @Test
    fun `the strategy signal card stops before the indicator dump`() {
        val text = """
            ## 📡 TradingView Signal Intelligence — BTCUSDT [TV-ONLY]
            Decision=WAIT | score=0.519 | confidence=83% | gate=NO_CONFLUENCE
            Data quality=100% | MTF alignment=3/3 | close=86274.54 | source=TV:BINANCE

            ### Multi-Timeframe
            15m: BUY score=2.2 | trend=BUY momentum=BUY reversal=WAIT breakout=WAIT | quality=100% bars=800
            1h: BUY score=2.2 | trend=BUY momentum=BUY reversal=WAIT breakout=WAIT | quality=100% bars=800

            ### Indicator Matrix — derived values only
            [15m] Trend: EMA20=86010.7378 EMA50=85522.6587 EMA200=84534.5607 BULLISH_STACK
        """.trimIndent()
        val card = AvatarCards.text(text)
        assertTrue(card.startsWith("## 📡 TradingView Signal Intelligence"), card)
        assertFalse(card.contains("EMA20"), card)
        assertTrue(card.endsWith("…"), card)
    }

    @Test
    fun `rule lines, notes for the AI and long decimals go, a table becomes bullets`() {
        val text = """
            🌊 **Modern Elliot Wave Insight - BTCUSDT (1h)**
            ----------------------------------------
            Market Stage: **CORRECTIVE**
            | เสาหลัก | คะแนน |
            | :--- | :---: |
            | 📰 ข่าวสาร | **69.5** |
            ผลลัพธ์: (4185.10 - 4160) * 2.5 = 62.75000000000091

            🧠 **JARVIS Intelligence Summary**
            (โหมดเสียง: ข้ามบทวิเคราะห์เสริม — ให้ผู้ช่วยวิเคราะห์จากข้อมูลด้านบนเอง)
        """.trimIndent()
        assertEquals(
            listOf(
                "## 🌊 Modern Elliot Wave Insight - BTCUSDT (1h)",
                "Market Stage: **CORRECTIVE**",
                "- เสาหลัก · คะแนน",
                "- 📰 ข่าวสาร · **69.5**",
                "ผลลัพธ์: (4185.10 - 4160) * 2.5 = 62.75",
                "…",
            ),
            AvatarCards.text(text).lines()
        )
    }

    @Test
    fun `a long answer is cut to ten lines`() {
        val text = (1..30).joinToString("\n") { "$it. BTCUSDT - 0.0427 9.6484 | RSI: 86.387" }
        val lines = AvatarCards.text(text).lines()
        assertEquals(AvatarCards.MAX_LINES + 1, lines.size)
        assertEquals("…", lines.last())
    }

    @Test
    fun `a short answer stays as it is`() {
        assertEquals("30.0 celsius = 86 fahrenheit", AvatarCards.text("30.0 celsius = 86 fahrenheit"))
    }
}
