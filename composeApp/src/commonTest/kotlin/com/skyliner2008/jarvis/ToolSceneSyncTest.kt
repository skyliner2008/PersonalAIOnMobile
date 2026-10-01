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

/** Tool scenes follow the AI's voice in Avatar mode — timings and words from logcat 2026-10-01 21:32–21:38. */
class ToolSceneSyncTest {
    private val shows = mutableListOf<String>()
    private var releases = 0
    private val sync = ToolSceneSync(show = { shows += label(it) }, release = { releases++ })

    private fun label(item: ToolSceneSync.Item) =
        ((item.data as? JsonObject)?.get("symbol") as? JsonPrimitive)?.content ?: item.tool

    private fun result(tool: String, atMs: Long, vararg fields: Pair<String, String>) {
        val data = JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v) })
        sync.onResult(ToolSceneSync.Item(tool, data, ToolSceneWords.of(tool, data), atMs), atMs)
    }

    /** a result the AI tells later from the report queue (a job handed to the assistant), on a text card */
    private fun later(tool: String, atMs: Long, text: String) {
        val data = JsonPrimitive(text)
        sync.onResult(
            ToolSceneSync.Item(tool, data, ToolSceneWords.of(tool, data), atMs, later = true, subject = PendingAnswerQueue.subject(text)!!),
            atMs
        )
    }

    private val calendar = "📅 **Economic Calendar (ForexFactory — Real-time, เวลาไทย)**\nศุกร์ 19:30 USD Non-Farm Employment Change"
    private val calendarSubject = PendingAnswerQueue.subject(calendar)!!

    /**
     * logcat 2026-10-02 00:18 (live): "วิเคราะห์ BTC 5 มิติ" → "ปฏิทินตัวเลขเศรษฐกิจสัปดาห์นี้" → "ราคาทองคำปัจจุบันเท่าไหร่"
     * in a row. BTC (no info scene) and the calendar went to the assistant, gold was answered at once, then the
     * AI told BTC and the calendar from the report queue.
     */
    @Test
    fun `a job handed to the assistant gets its card when the AI tells it, not while it hands it over`() {
        later("trading_macro_calendar", 46_771, calendar)                // done while the user asks about gold
        assertEquals(emptyList(), shows)
        sync.onSpeaking(true, 47_318)
        sync.onSpeech("กำลังให้ผู้ช่วย ดึงข้อมูล", heardAtMs = 47_400, nowMs = 47_548)
        sync.onSpeaking(false, 47_610)                                   // the user talked over it
        sync.tick(48_400)
        assertEquals(0, releases)
        result("trading_price", 51_016, "symbol" to "XAUUSD=X", "price" to "4170.5500")
        assertEquals(listOf("XAUUSD=X"), shows)
        sync.onSpeaking(true, 54_345)
        sync.onSpeech("ราคาทองคำสปอตตอนนึ้อยู่ที่ประมาณ สี่พันหนึ่งร้อยเจ็ดสิบจุดห้าห้า ดอลลาร์ต่อออนซ์ค่ะ", heardAtMs = 58_000, nowMs = 55_649)
        sync.onSpeaking(false, 77_048)
        sync.tick(77_800)
        assertEquals(1, releases)
        // 79.1 s: the BTC report has no card, and its "ราคาล่าสุด…ดอลลาร์" brings nothing back
        sync.onReport("trading_deep_analysis_suite", "🏛️ LSD Trend: NEUTRAL (Confluence TF: 2/4)", 79_134)
        sync.onSpeaking(true, 82_906)
        sync.onSpeech("ส่วนเรื่องวิเคราะห์ Bitcoin ที่บอสขอไว้ก่อนหน้านี้ ภาพรวมตอนนี้ยังดูเป็นกลางนะคะ โดยราคาล่าสุดวิ่งอยู่ที่ประมาณ " +
            "แปดหมื่นสี่พันสองร้อยสองดอลลาร์ค่ะ ตัวเลขเดลต้าอยู่ที่ประมาณ 52.64", heardAtMs = 95_000, nowMs = 85_000)
        sync.tick(95_100)
        sync.onSpeaking(false, 119_030)
        sync.tick(119_800)
        assertEquals(listOf("XAUUSD=X"), shows)
        // 121.3 s: the calendar is handed over — up with the AI's first words, not before
        sync.onReport("trading_macro_calendar", calendarSubject, 121_285)
        sync.tick(122_000)
        assertEquals(listOf("XAUUSD=X"), shows)
        sync.onSpeaking(true, 123_667)
        sync.onSpeech("ส่วนเรื่องปฏิทิน", heardAtMs = 123_700, nowMs = 123_669)
        sync.tick(123_800)
        assertEquals(listOf("XAUUSD=X", "trading_macro_calendar"), shows)
        sync.onSpeech("ส่วนเรื่องปฏิทิน เศรษฐกิจ สัปดาห์ นี้ นะคะ บอส วันศุกร์นี้ มีข่าว ใหญ่ ของดอลลาร์", heardAtMs = 125_000, nowMs = 124_868)
        sync.tick(140_000)
        assertEquals(1, releases)                                        // up for the whole report
        sync.onSpeaking(false, 144_266)
        sync.tick(145_000)
        assertEquals(2, releases)
    }

    @Test
    fun `naming the job while handing it over does not bring its card up`() {
        val data = JsonObject(mapOf("value" to JsonPrimitive("34"), "label" to JsonPrimitive("Fear")))
        sync.onResult(ToolSceneSync.Item("trading_fear_greed", data, ToolSceneWords.of("trading_fear_greed", data), 300,
            later = true, subject = "😨 Fear & Greed Index: 34 (Fear)"), 300)
        sync.onSpeaking(true, 900)
        sync.onSpeech("รับทราบ กำลังให้ผู้ช่วยดู Fear and Greed ให้นะคะ", heardAtMs = 2_400, nowMs = 1_000)
        sync.tick(2_500)
        sync.onSpeaking(false, 2_800)
        sync.tick(4_000)
        assertEquals(emptyList(), shows)
        sync.onReport("trading_fear_greed", "😨 Fear & Greed Index: 34 (Fear)", 5_000)
        sync.onSpeaking(true, 7_400)
        sync.onSpeech("ส่วนเรื่อง Fear and Greed ตอนนี้อยู่ที่ 34 โซนกลัว", heardAtMs = 7_500, nowMs = 7_450)
        sync.tick(7_600)
        assertEquals(listOf("trading_fear_greed"), shows)
    }

    @Test
    fun `a report the user talks over keeps its card for when it is told again`() {
        later("trading_macro_calendar", 0, calendar)
        sync.onReport("trading_macro_calendar", calendarSubject, 10_000)
        result("trading_price", 13_000, "symbol" to "XAUUSD=X")         // the user asked about gold instead
        assertEquals(listOf("XAUUSD=X"), shows)
        sync.onSpeaking(true, 15_000)
        sync.onSpeech("ราคาทองคำตอนนี้ สี่พันหนึ่งร้อยเจ็ดสิบดอลลาร์", heardAtMs = 15_100, nowMs = 15_050)
        sync.tick(15_200)
        assertEquals(listOf("XAUUSD=X"), shows)                          // gold, not the calendar
        sync.onSpeaking(false, 25_000)
        sync.tick(25_800)
        sync.onReport("trading_macro_calendar", calendarSubject, 30_000) // sent again
        sync.onSpeaking(true, 32_000)
        sync.onSpeech("ส่วนเรื่องปฏิทิน เศรษฐกิจ", heardAtMs = 32_100, nowMs = 32_050)
        sync.tick(32_200)
        assertEquals(listOf("XAUUSD=X", "trading_macro_calendar"), shows)
    }

    @Test
    fun `a card taken down before the AI talked about it comes back with its report`() {
        result("trading_price", 0, "symbol" to "XAUUSD=X")              // up at once…
        sync.onSpeaking(true, 1_000)
        sync.onSpeech("เปิดเพลงให้แล้วค่ะบอส", heardAtMs = 1_100, nowMs = 1_050)   // …but the AI answered something else
        sync.onSpeaking(false, 3_000)
        sync.tick(3_800)
        assertEquals(1, releases)
        sync.onReport("trading_price", "**XAUUSD=X** - Real-time Price", 6_000)
        sync.onSpeaking(true, 8_000)
        sync.onSpeech("ส่วนเรื่องราคาทองคำ", heardAtMs = 8_100, nowMs = 8_050)
        sync.tick(8_200)
        assertEquals(listOf("XAUUSD=X", "XAUUSD=X"), shows)
    }

    /** 21:37 "สภาพอากาศวันนี้ แล้วก็ราคาทองคำวันนี้": the price came first, the AI spoke about the weather first */
    @Test
    fun `the scene follows the topic the AI is speaking about, when it is heard`() {
        result("trading_price", 32_395, "symbol" to "XAUUSD=Y", "price" to "4169.6650")
        result("device_weather", 34_372, "location" to "ตำบล บานา, ปัตตานี, ประเทศไทย", "temp" to "27")
        assertEquals(listOf("XAUUSD=Y", "device_weather"), shows)        // nothing said yet: the newest is up
        sync.onSpeaking(true, 38_041)
        sync.onSpeech("สภาพอากาศที่ ปัตตานี 27 องศา", heardAtMs = 38_900, nowMs = 38_498)
        sync.onSpeech("สภาพอากาศที่ ปัตตานี 27 องศา ฝนมี โอกาส ตกหนัก ตั้ง 92% แน่ะ บอส อย่าลืม พกร่ม นะฮับ ส่วน ราคาทองคำ",
            heardAtMs = 46_500, nowMs = 40_281)
        sync.tick(40_300)
        assertEquals("device_weather", shows.last())                    // "ราคาทองคำ" is not heard yet
        sync.tick(46_600)
        assertEquals("XAUUSD=Y", shows.last())
        assertEquals(0, releases)
        sync.onSpeaking(false, 52_400)
        sync.tick(52_800)
        assertEquals(0, releases)                                        // a short pause is not the end
        sync.tick(53_200)
        assertEquals(1, releases)
        assertEquals(3, shows.size)
    }

    /** 21:38 "ราคาหุ้น Tesla ราคาหุ้น Microsoft": two price cards, chosen by name ("ราคา" is in both) */
    @Test
    fun `two results of one tool each come up when their name is heard`() {
        result("trading_price", 13_790, "symbol" to "MSFT")
        result("trading_price", 13_932, "symbol" to "TSLA")
        sync.onSpeaking(true, 16_923)
        sync.onSpeech("ราคาหุ้นที่ บอส ถาม มาได้ แล้วฮับ! Tesla ตอนนี้", heardAtMs = 18_200, nowMs = 17_745)
        sync.tick(18_300)
        assertEquals("TSLA", shows.last())
        sync.onSpeech("ราคาหุ้นที่ บอส ถาม มาได้ แล้วฮับ! Tesla ตอนนี้ อยู่ที่ 354.4220 ดอลลาร์ ส่วน Microsoft",
            heardAtMs = 21_800, nowMs = 18_887)
        sync.tick(21_900)
        assertEquals(listOf("MSFT", "TSLA", "MSFT"), shows)
    }

    /** 21:32–21:33: gold cut off before the AI spoke, then "ราคา Bitcoin" — gold is told later from the queue */
    @Test
    fun `a result the AI did not get to comes up when it is told later`() {
        result("trading_price", 57_146, "symbol" to "XAUUSD=X")
        result("trading_price", 60_979, "symbol" to "BTC-USD")            // gold was never spoken about: replaced
        assertEquals("BTC-USD", shows.last())
        sync.onSpeaking(true, 64_042)
        sync.onSpeech("จาวิสเช็คให้แล้วฮับ! ราคา Bitcoin ตอนนี้ อยู่ที่ 84200", heardAtMs = 65_000, nowMs = 64_996)
        sync.onSpeaking(false, 79_000)
        sync.tick(79_800)
        assertEquals(1, releases)
        assertEquals("BTC-USD", shows.last())                            // the old gold card does not pop up after
        sync.onSpeaking(true, 90_000)
        sync.onSpeech("ส่วนเรื่อง ราคาทองคำ ที่บอสถามไว้", heardAtMs = 90_400, nowMs = 90_100)
        sync.tick(90_500)
        assertEquals("XAUUSD=X", shows.last())
    }

    @Test
    fun `a scene stays until the voice stops, and goes if nobody talks about it`() {
        result("device_weather", 0, "location" to "ปัตตานี")
        sync.tick(9_000)                                                 // the AI is still thinking (tool, latency)
        assertEquals(0, releases)
        sync.onSpeaking(true, 9_500)
        sync.tick(30_000)                                                // a long answer: still up
        assertEquals(0, releases)
        sync.onSpeaking(false, 30_000)
        sync.tick(30_800)
        assertEquals(1, releases)
        result("device_battery_status", 40_000, "level" to "42")
        sync.tick(56_000)                                                // no speech at all
        assertEquals(2, releases)
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
