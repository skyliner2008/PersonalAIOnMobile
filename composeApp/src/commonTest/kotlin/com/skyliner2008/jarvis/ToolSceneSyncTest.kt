package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.avatar.ToolSceneSync
import com.skyliner2008.jarvis.avatar.ToolSceneWords
import com.skyliner2008.jarvis.data.LiveProtocol
import com.skyliner2008.jarvis.data.PendingAnswerQueue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tool scenes follow the AI's voice in Avatar mode — timings and words from logcat 2026-10-01 and 2026-10-02. */
class ToolSceneSyncTest {
    private val shows = mutableListOf<String>()
    private var releases = 0
    private val sync = ToolSceneSync(show = { shows += label(it) }, release = { releases++ })
    private val msPerUnit = LiveProtocol.DEFAULT_MS_PER_SPEECH_UNIT

    private fun label(item: ToolSceneSync.Item) =
        ((item.data as? JsonObject)?.get("symbol") as? JsonPrimitive)?.content ?: item.tool

    private fun result(tool: String, atMs: Long, vararg fields: Pair<String, String>, subject: String = tool) {
        val data = JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v) })
        sync.onResult(ToolSceneSync.Item(tool, data, ToolSceneWords.of(tool, data), atMs, subject = subject), atMs)
    }

    /** a result the AI tells later from the report queue (a job handed to the assistant), on a text card */
    private fun later(tool: String, atMs: Long, text: String) {
        val data = JsonPrimitive(text)
        sync.onResult(
            ToolSceneSync.Item(tool, data, ToolSceneWords.of(tool, data), atMs, later = true, subject = PendingAnswerQueue.subject(text)!!),
            atMs
        )
    }

    /** the AI's answer so far; its voice starts at [startMs] */
    private fun says(text: String, startMs: Long, nowMs: Long) = sync.onSpeech(text, startMs, msPerUnit, nowMs)

    /** when the words after [before] are heard, the voice having started at [startMs] */
    private fun heardAfter(before: String, startMs: Long) = startMs + (LiveProtocol.speechUnits(before) * msPerUnit).toLong()

    /** the screen's tick, every 100 ms */
    private fun ticks(fromMs: Long, toMs: Long) {
        var t = fromMs
        while (t <= toMs) {
            sync.tick(t)
            t += 100
        }
    }

    private val gold = "**XAUUSD=X** - Real-time Price"
    private val bitcoin = "**BTC-USD** - Real-time Price"
    private val calendar = "📅 **Economic Calendar (ForexFactory — Real-time, เวลาไทย)**\nศุกร์ 19:30 USD Non-Farm Employment Change"
    private val calendarSubject = PendingAnswerQueue.subject(calendar)!!

    /**
     * logcat 2026-10-02 09:48 (Avatar mode): "ราคา Bitcoin วันนี้" (talked over, its result queued) → "ราคาทองคำวันนี้
     * สภาพอากาศเป็นยังไง". The gold and weather cards flashed by while the AI was still thinking, then came up again
     * with its voice.
     */
    @Test
    fun `results wait while the AI thinks, and each goes up when it is heard`() {
        result("trading_price", 24_440, "symbol" to "BTC-USD", subject = bitcoin)    // the call the server cancelled…
        sync.onDeferred("trading_price", bitcoin)                                    // …is told later, from the queue
        result("trading_price", 30_681, "symbol" to "XAUUSD=X", subject = gold)
        result("device_weather", 32_591, "location" to "ตำบล บานา, ปัตตานี, ประเทศไทย", "temp" to "29")
        ticks(24_500, 37_600)
        assertEquals(emptyList(), shows)                                             // nothing while the AI thinks (8 s)
        says("ราคาทองคำตอนนี้อยู่ที่", startMs = 37_611, nowMs = 37_611)
        sync.onSpeaking(true, 37_700)
        ticks(37_700, 37_800)
        assertEquals(listOf("XAUUSD=X"), shows)
        val beforeWeather = "ราคาทองคำตอนนี้อยู่ที่ 4159.43 ดอลลาร์ฮับบอส ขึ้นมา นิดหน่อย จากราคา ปิดก่อนหน้า นะฮับ ส่วนสภาพ"
        says("$beforeWeather อากาศที่", startMs = 37_611, nowMs = 40_571)
        val weatherHeard = heardAfter(beforeWeather, 37_611)
        assertTrue(weatherHeard in 43_000..47_000, "the weather is heard ~7 s into the answer: $weatherHeard")
        ticks(37_900, weatherHeard - 100)
        assertEquals(listOf("XAUUSD=X"), shows)                                      // its words are here, its voice is not
        ticks(weatherHeard, weatherHeard + 100)
        assertEquals(listOf("XAUUSD=X", "device_weather"), shows)
        says(
            "$beforeWeather อากาศที่ ปัตตานี ตอนนี้ มีเมฆ เป็นส่วนมาก นะฮะ อุณหภูมิ 29 องศา แต่รู้สึก เหมือน 36 แหน่ะ " +
                "แถม มีโอกาส ฝนตก 100% เลย บอสอย่าลืม พกร่ม ด้วยน้า น้องเป็น ห่วงงับ",
            startMs = 37_611, nowMs = 42_688
        )
        ticks(weatherHeard + 200, 57_600)
        assertEquals(0, releases)
        sync.onSpeaking(false, 57_650)                                               // the voice ends at 56.8 (+ the face's hangover)
        ticks(57_700, 58_400)
        assertEquals(1, releases)
        assertEquals(listOf("XAUUSD=X", "device_weather"), shows)                    // Bitcoin waits for its report
        // 59.0 s: the queued Bitcoin price is handed to the AI — up with its first words
        sync.onReport("trading_price", bitcoin, 59_024)
        ticks(58_500, 60_800)
        assertEquals(2, shows.size)
        says("ส่วนเรื่อง", startMs = 60_870, nowMs = 60_870)
        sync.onSpeaking(true, 60_950)
        ticks(60_900, 61_000)
        assertEquals(listOf("XAUUSD=X", "device_weather", "BTC-USD"), shows)
        says("ส่วนเรื่อง ราคา Bitcoin ตอนนี้ อยู่ที่ และ 85,054 ดอลลาร์ ฮับบอส", startMs = 60_870, nowMs = 62_191)
        ticks(61_100, 76_500)
        sync.onSpeaking(false, 76_580)
        ticks(76_600, 77_400)
        assertEquals(2, releases)
        assertEquals(3, shows.size)
    }

    /**
     * logcat 2026-10-02 00:18 (live): "วิเคราะห์ BTC 5 มิติ" → "ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้" → "ราคาทองคำปัจจุบันเท่าไหร่"
     * in a row. BTC (no info scene) and the calendar went to the assistant, gold was answered at once, then the
     * AI told BTC and the calendar from the report queue.
     */
    @Test
    fun `a job handed to the assistant gets its card when the AI tells it, not while it hands it over`() {
        later("trading_macro_calendar", 46_771, calendar)                          // done while the user asks about gold
        says("กำลังให้ผู้ช่วย ดึงข้อมูล", startMs = 47_318, nowMs = 47_548)
        sync.onSpeaking(true, 47_400)
        ticks(47_400, 47_600)
        sync.onSpeaking(false, 47_610)                                             // the user talked over it
        ticks(47_700, 51_000)
        assertEquals(emptyList(), shows)
        result("trading_price", 51_016, "symbol" to "XAUUSD=X", "price" to "4170.5500", subject = gold)
        ticks(51_100, 54_300)
        assertEquals(emptyList(), shows)                                            // the AI is still thinking
        says("ราคาทองคำสปอตตอนนึ้อยู่ที่ประมาณ", startMs = 54_345, nowMs = 54_349)
        sync.onSpeaking(true, 54_400)
        ticks(54_400, 77_000)
        assertEquals(listOf("XAUUSD=X"), shows)
        sync.onSpeaking(false, 77_900)
        ticks(77_100, 78_700)
        assertEquals(1, releases)
        // 79.1 s: the BTC report has no card, and its "ราคาล่าสุด…ดอลลาร์" brings nothing back
        sync.onReport("trading_deep_analysis_suite", "🏛️ LSD Trend: NEUTRAL (Confluence TF: 2/4)", 79_134)
        says(
            "ส่วนเรื่องวิเคราะห์ Bitcoin ที่บอสขอไว้ก่อนหน้านี้ ภาพรวมตอนนี้ยังดูเป็นกลางนะคะ โดยราคาล่าสุดวิ่งอยู่ที่ประมาณ " +
                "แปดหมื่นสี่พันสองร้อยสองดอลลาร์ค่ะ ตัวเลขเดลต้าอยู่ที่ประมาณ 52.64",
            startMs = 82_906, nowMs = 85_000
        )
        sync.onSpeaking(true, 83_000)
        ticks(83_000, 119_000)
        sync.onSpeaking(false, 119_880)
        ticks(119_100, 121_200)
        assertEquals(listOf("XAUUSD=X"), shows)
        // 121.3 s: the calendar is handed over — up with the AI's first words, not before
        sync.onReport("trading_macro_calendar", calendarSubject, 121_285)
        ticks(121_300, 123_600)
        assertEquals(listOf("XAUUSD=X"), shows)
        says("ส่วนเรื่องปฏิทิน", startMs = 123_667, nowMs = 123_669)
        sync.onSpeaking(true, 123_750)
        ticks(123_700, 123_800)
        assertEquals(listOf("XAUUSD=X", "trading_macro_calendar"), shows)
        says("ส่วนเรื่องปฏิทิน เศรษฐกิจ สัปดาห์ นี้ นะคะ บอส วันศุกร์นี้ มีข่าว ใหญ่ ของดอลลาร์", startMs = 123_667, nowMs = 124_868)
        ticks(123_900, 145_000)
        assertEquals(1, releases)                                                   // up for the whole report
        sync.onSpeaking(false, 145_100)
        ticks(145_100, 145_900)
        assertEquals(2, releases)
    }

    @Test
    fun `naming the job while handing it over does not bring its card up`() {
        val data = JsonObject(mapOf("value" to JsonPrimitive("34"), "label" to JsonPrimitive("Fear")))
        val fearGreed = "😨 Fear & Greed Index: 34 (Fear)"
        sync.onResult(
            ToolSceneSync.Item("trading_fear_greed", data, ToolSceneWords.of("trading_fear_greed", data), 300, later = true, subject = fearGreed),
            300
        )
        says("รับทราบ กำลังให้ผู้ช่วยดู Fear and Greed ให้นะคะ", startMs = 900, nowMs = 1_000)
        sync.onSpeaking(true, 950)
        ticks(1_000, 2_700)
        sync.onSpeaking(false, 2_800)
        ticks(2_800, 4_000)
        assertEquals(emptyList(), shows)
        sync.onReport("trading_fear_greed", fearGreed, 5_000)
        says("ส่วนเรื่อง Fear and Greed ตอนนี้อยู่ที่ 34 โซนกลัว", startMs = 7_400, nowMs = 7_450)
        sync.onSpeaking(true, 7_500)
        ticks(7_500, 7_600)
        assertEquals(listOf("trading_fear_greed"), shows)
    }

    @Test
    fun `a report the user talks over keeps its card for when it is told again`() {
        later("trading_macro_calendar", 0, calendar)
        sync.onReport("trading_macro_calendar", calendarSubject, 10_000)
        result("trading_price", 13_000, "symbol" to "XAUUSD=X", subject = gold)      // the user asked about gold instead
        says("ราคาทองคำตอนนี้ สี่พันหนึ่งร้อยเจ็ดสิบดอลลาร์", startMs = 15_000, nowMs = 15_050)
        sync.onSpeaking(true, 15_100)
        ticks(15_100, 15_200)
        assertEquals(listOf("XAUUSD=X"), shows)                                      // gold, not the calendar
        ticks(15_300, 24_900)
        sync.onSpeaking(false, 25_000)
        ticks(25_000, 25_800)
        assertEquals(1, releases)
        sync.onReport("trading_macro_calendar", calendarSubject, 30_000)             // sent again
        says("ส่วนเรื่องปฏิทิน เศรษฐกิจ", startMs = 32_000, nowMs = 32_050)
        sync.onSpeaking(true, 32_100)
        ticks(32_100, 32_200)
        assertEquals(listOf("XAUUSD=X", "trading_macro_calendar"), shows)
    }

    @Test
    fun `a result the queue took comes up with its report, not with the next answer`() {
        result("trading_price", 0, "symbol" to "XAUUSD=X", subject = gold)           // its turn was cut before the AI spoke…
        sync.onDeferred("trading_price", gold)                                      // …so the queue took it
        says("เปิดเพลงให้แล้วค่ะบอส", startMs = 1_000, nowMs = 1_050)                // the AI answers something else
        sync.onSpeaking(true, 1_100)
        ticks(1_100, 2_900)
        sync.onSpeaking(false, 3_000)
        ticks(3_000, 4_000)
        assertEquals(emptyList(), shows)
        sync.onReport("trading_price", gold, 6_000)
        says("ส่วนเรื่องราคาทองคำ", startMs = 8_000, nowMs = 8_050)
        sync.onSpeaking(true, 8_100)
        ticks(8_100, 8_200)
        assertEquals(listOf("XAUUSD=X"), shows)
    }

    @Test
    fun `the only new result goes up when the AI talks a while without naming it`() {
        val rows = JsonObject(mapOf("rows" to JsonPrimitive("NVDA +8.1%")))
        sync.onResult(ToolSceneSync.Item("trading_top_gainers", rows, ToolSceneWords.of("trading_top_gainers", rows), 0), 0)
        says("วันนี้หุ้นที่พุ่งเยอะสุดคือ NVDA บวกไปแปดเปอร์เซ็นต์", startMs = 3_000, nowMs = 3_000)
        sync.onSpeaking(true, 3_050)
        ticks(3_100, 4_500)
        assertEquals(emptyList(), shows)
        ticks(4_600, 4_700)
        assertEquals(listOf("trading_top_gainers"), shows)
    }

    @Test
    fun `a name the estimate puts after the end of the voice is not shown late`() {
        result("trading_price", 0, "symbol" to "XAUUSD=X", subject = gold)
        result("device_weather", 0, "location" to "ปัตตานี")
        // a voice far faster than estimated: the weather, said last, would come after the voice has ended
        sync.onSpeech("ราคาทองคำวันนี้ 4159 ดอลลาร์ ส่วนอากาศ ฝนตก", startMs = 1_000, msPerUnit = 400.0, nowMs = 1_000)
        sync.onSpeaking(true, 1_100)
        ticks(1_100, 4_900)
        sync.onSpeaking(false, 5_000)
        ticks(5_000, 6_000)
        assertEquals(listOf("XAUUSD=X"), shows)
        assertEquals(1, releases)
    }

    /** 21:37 "สภาพอากาศวันนี้ แล้วก็ราคาทองคำวันนี้": the price came first, the AI spoke about the weather first */
    @Test
    fun `the scene follows the topic the AI is speaking about, when it is heard`() {
        result("trading_price", 32_395, "symbol" to "XAUUSD=Y", "price" to "4169.6650")
        result("device_weather", 34_372, "location" to "ตำบล บานา, ปัตตานี, ประเทศไทย", "temp" to "27")
        ticks(32_400, 38_000)
        assertEquals(emptyList(), shows)                                             // nothing said yet
        sync.onSpeaking(true, 38_041)
        says("สภาพอากาศที่ ปัตตานี 27 องศา", startMs = 38_041, nowMs = 38_498)
        ticks(38_500, 38_600)
        assertEquals(listOf("device_weather"), shows)
        val beforeGold = "สภาพอากาศที่ ปัตตานี 27 องศา ฝนมี โอกาส ตกหนัก ตั้ง 92% แน่ะ บอส อย่าลืม พกร่ม นะฮับ ส่วน"
        says("$beforeGold ราคาทองคำ", startMs = 38_041, nowMs = 40_281)
        val goldHeard = heardAfter(beforeGold, 38_041)
        ticks(38_700, goldHeard - 100)
        assertEquals(listOf("device_weather"), shows)                                // "ราคาทองคำ" is not heard yet
        ticks(goldHeard, goldHeard + 100)
        assertEquals(listOf("device_weather", "XAUUSD=Y"), shows)
        ticks(goldHeard + 200, 52_300)
        sync.onSpeaking(false, 52_400)
        ticks(52_400, 52_800)
        assertEquals(0, releases)                                                    // a short pause is not the end
        ticks(52_900, 53_200)
        assertEquals(1, releases)
        assertEquals(2, shows.size)
    }

    /** 21:38 "ราคาหุ้น Tesla ราคาหุ้น Microsoft": two price cards, chosen by name ("ราคา" is in both) */
    @Test
    fun `two results of one tool each come up when their name is heard`() {
        result("trading_price", 13_790, "symbol" to "MSFT")
        result("trading_price", 13_932, "symbol" to "TSLA")
        sync.onSpeaking(true, 16_923)
        val beforeTesla = "ราคาหุ้นที่ บอส ถาม มาได้ แล้วฮับ!"
        says("$beforeTesla Tesla ตอนนี้", startMs = 16_923, nowMs = 17_745)
        val teslaHeard = heardAfter(beforeTesla, 16_923)
        ticks(17_800, teslaHeard + 100)
        assertEquals(listOf("TSLA"), shows)
        val beforeMicrosoft = "$beforeTesla Tesla ตอนนี้ อยู่ที่ 354.4220 ดอลลาร์ ส่วน"
        says("$beforeMicrosoft Microsoft", startMs = 16_923, nowMs = 18_887)
        val microsoftHeard = heardAfter(beforeMicrosoft, 16_923)
        ticks(teslaHeard + 200, microsoftHeard - 100)
        assertEquals(listOf("TSLA"), shows)
        ticks(microsoftHeard, microsoftHeard + 100)
        assertEquals(listOf("TSLA", "MSFT"), shows)
    }

    /** 21:32–21:33: gold cut off before the AI spoke, then "ราคา Bitcoin" — gold is told later */
    @Test
    fun `a result the AI did not get to comes up when it is told later`() {
        result("trading_price", 57_146, "symbol" to "XAUUSD=X", subject = gold)
        result("trading_price", 60_979, "symbol" to "BTC-USD", subject = bitcoin)
        ticks(57_200, 64_000)
        assertEquals(emptyList(), shows)
        sync.onSpeaking(true, 64_042)
        says("จาวิสเช็คให้แล้วฮับ! ราคา Bitcoin ตอนนี้ อยู่ที่ 84200", startMs = 64_042, nowMs = 64_996)
        ticks(65_000, 78_900)
        assertEquals(listOf("BTC-USD"), shows)
        sync.onSpeaking(false, 79_000)
        ticks(79_000, 79_800)
        assertEquals(1, releases)
        assertEquals(listOf("BTC-USD"), shows)                                       // the old gold card does not pop up after
        sync.onSpeaking(true, 90_000)
        says("ส่วนเรื่อง ราคาทองคำ ที่บอสถามไว้", startMs = 90_000, nowMs = 90_100)
        ticks(90_100, 90_900)
        assertEquals(listOf("BTC-USD", "XAUUSD=X"), shows)
    }

    @Test
    fun `a scene stays until the voice stops`() {
        result("device_weather", 0, "location" to "ปัตตานี")
        ticks(0, 9_000)                                                              // the AI is still thinking (tool, latency)
        assertEquals(emptyList(), shows)
        sync.onSpeaking(true, 9_500)
        says("วันนี้ที่ปัตตานี ฝนตกทั้งวันเลย", startMs = 9_500, nowMs = 9_500)
        ticks(9_500, 29_900)                                                         // a long answer: still up
        assertEquals(listOf("device_weather"), shows)
        assertEquals(0, releases)
        sync.onSpeaking(false, 30_000)
        ticks(30_000, 30_800)
        assertEquals(1, releases)
    }

    @Test
    fun `how long words take to say`() {
        assertEquals(4.0, LiveProtocol.speechUnits("ราคา"))
        assertEquals(1.0, LiveProtocol.speechUnits("ที่"))                             // vowel and tone marks take no time
        assertEquals(18.0, LiveProtocol.speechUnits("4159.43"))                       // a digit is read as a word
        assertEquals(9.0, LiveProtocol.speechUnits("ราคา ทองคำ!"))
        // logcat 09:48: gold and weather, 19.2 s of voice
        val answer = "ราคาทองคำตอนนี้อยู่ที่ 4159.43 ดอลลาร์ฮับบอส ขึ้นมา นิดหน่อย จากราคา ปิดก่อนหน้า นะฮับ ส่วนสภาพ อากาศที่ " +
            "ปัตตานี ตอนนี้ มีเมฆ เป็นส่วนมาก นะฮะ อุณหภูมิ 29 องศา แต่รู้สึก เหมือน 36 แหน่ะ แถม มีโอกาส ฝนตก 100% เลย " +
            "บอสอย่าลืม พกร่ม ด้วยน้า น้องเป็น ห่วงงับ"
        val estimate = LiveProtocol.speechUnits(answer) * LiveProtocol.DEFAULT_MS_PER_SPEECH_UNIT
        assertTrue(estimate in 17_000.0..21_500.0, "estimated $estimate ms, the voice took 19 201 ms")
    }

    @Test
    fun `the words the AI says for a result`() {
        assertTrue("ทอง" in ToolSceneWords.symbolWords("XAUUSD=Y"))
        assertTrue("bitcoin" in ToolSceneWords.symbolWords("BTC-USD"))
        assertTrue("tesla" in ToolSceneWords.symbolWords("TSLA"))
        assertEquals(listOf("บานา", "ปัตตานี"), ToolSceneWords.placeWords("ตำบล บานา, ปัตตานี, ประเทศไทย"))
        assertEquals("ราคาทองคำ", ToolSceneWords.normalize("ราคา ทองคำ"))
    }

    /** 21:33, 21:38: the AI answered, changed its face, then greeted ("สวัสดีฮับบอส! วันนี้มีอะไรให้น้องช่วย…") */
    @Test
    fun `speech after a face change made once the AI has answered is not played`() {
        assertTrue(LiveProtocol.isTrailingExpression(listOf("device_avatar_emotion"), modelSpokeThisTurn = true))
        assertFalse(LiveProtocol.isTrailingExpression(listOf("device_avatar_emotion"), modelSpokeThisTurn = false))
        assertFalse(LiveProtocol.isTrailingExpression(listOf("device_avatar_emotion", "trading_price"), modelSpokeThisTurn = true))
        assertFalse(LiveProtocol.isTrailingExpression(emptyList(), modelSpokeThisTurn = true))
    }
}
