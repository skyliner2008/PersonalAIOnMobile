package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.OhlcvCentralStore
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P1: ความถูกต้องของชั้นเก็บ OHLCV
 *
 * ครอบคลุมบัคที่เจอตอน audit 2026-09-18:
 *  - bucket ที่ align กับ epoch ทำให้ 1D/1W ดู "ขาดแท่ง" ตลอดเวลา
 *  - แท่งที่ยังก่อตัวอยู่ถูกบันทึกเป็นแท่งปิดแล้ว
 *  - in-memory store เลือก source จาก "ซีรีส์ไหนยาวสุด" และโตไม่จำกัด
 */
class OhlcvStoreIntegrityTest {

    private val api = SmcApiService(HttpClient())

    private fun bar(ts: Long, close: Double = 2000.0) =
        Candle(open = close, high = close + 1, low = close - 1, close = close, volume = 1.0, timestamp = ts)

    @BeforeTest
    fun setup() = OhlcvCentralStore.clearForTest()

    @AfterTest
    fun teardown() = OhlcvCentralStore.clearForTest()

    // ─── estimateMissingBars: ต้องไม่ผูกกับกริด epoch ────────────────────────

    /**
     * แท่ง D1 ของทองเปิด 21:00 UTC ไม่ใช่ 00:00 UTC
     *
     * สูตรเดิม `(now / tfMs) * tfMs` ถือว่ากริดเริ่มนับจาก epoch พอดี
     * → แท่งที่เพิ่งเปิดเมื่อ 3 ชม.ที่แล้วจะถูกนับว่า "ขาด" แม้ยังไม่ครบวัน
     */
    @Test
    fun dailyBarsOnNonEpochGridAreNotReportedMissing() {
        val dayMs = 86_400_000L
        // แท่งเปิด 21:00 UTC ของเมื่อวาน
        val barOpen = 1_757_000_000_000L - (1_757_000_000_000L % dayMs) - 3 * 3_600_000L
        val series = listOf(bar(barOpen - dayMs), bar(barOpen))

        // ผ่านไป 3 ชม.หลังแท่งเปิด — ยังอยู่ในแท่งเดียวกัน ต้องไม่ขาด
        assertEquals(0, api.estimateMissingBars(series, "1D", nowMs = barOpen + 3 * 3_600_000L))
        // ผ่านไป 23 ชม. — ยังไม่ครบแท่ง
        assertEquals(0, api.estimateMissingBars(series, "1D", nowMs = barOpen + 23 * 3_600_000L))
        // ครบ 1 วัน + นิดหน่อย — ขาด 1 แท่ง
        assertEquals(1, api.estimateMissingBars(series, "1D", nowMs = barOpen + dayMs + 60_000L))
        // ผ่านไป 3 วัน — ขาด 3 แท่ง
        assertEquals(3, api.estimateMissingBars(series, "1D", nowMs = barOpen + 3 * dayMs))
    }

    /**
     * epoch week เริ่ม "วันพฤหัส" (1 ม.ค. 1970 เป็นวันพฤหัส)
     * แต่แท่ง W ของ TV เริ่มวันอาทิตย์/จันทร์ — สูตรเดิมจึงผิดแน่นอนทุกครั้ง
     */
    @Test
    fun weeklyBarsOnSundayGridAreNotReportedMissing() {
        val weekMs = 604_800_000L
        val sundayOpen = 1_757_030_400_000L // ไม่ตรงกับ epoch-week boundary
        val series = listOf(bar(sundayOpen - weekMs), bar(sundayOpen))

        assertEquals(0, api.estimateMissingBars(series, "1W", nowMs = sundayOpen + 3 * 86_400_000L))
        assertEquals(1, api.estimateMissingBars(series, "1W", nowMs = sundayOpen + weekMs + 1))
    }

    @Test
    fun emptySeriesReportsEverythingMissing() {
        assertEquals(Int.MAX_VALUE, api.estimateMissingBars(emptyList(), "1h"))
    }

    /** 8h ต้องใช้ความยาวแท่ง 8 ชม. — เดิมตกไป 1h ทำให้นับแท่งที่ขาดผิด 8 เท่า */
    @Test
    fun eightHourTimeframeUsesCorrectBarLength() {
        val h8 = 8 * 3_600_000L
        val open = 1_757_000_000_000L
        val series = listOf(bar(open))
        assertEquals(0, api.estimateMissingBars(series, "8h", nowMs = open + 7 * 3_600_000L))
        assertEquals(1, api.estimateMissingBars(series, "8h", nowMs = open + h8 + 1))
    }

    // ─── markClosedState: กันแท่งครึ่งใบเข้าสู่การคำนวณ ──────────────────────

    @Test
    fun onlyBarsPastTheirCloseTimeAreMarkedClosed() {
        val tf = "15m"
        val tfMs = TaIndicators.timeframeMillis(tf)
        val t0 = 1_757_000_000_000L
        val raw = listOf(bar(t0), bar(t0 + tfMs), bar(t0 + 2 * tfMs))

        // now อยู่กลางแท่งสุดท้าย → 2 แท่งแรกปิดแล้ว แท่งสุดท้ายยังก่อตัว
        val marked = api.markClosedState(raw, tf, nowMs = t0 + 2 * tfMs + tfMs / 2)
        assertEquals(listOf(true, true, false), marked.map { it.isClosed })

        // now เลยแท่งสุดท้ายไปแล้ว → ปิดทั้งหมด
        val allClosed = api.markClosedState(raw, tf, nowMs = t0 + 3 * tfMs)
        assertTrue(allClosed.all { it.isClosed })
    }

    // ─── OhlcvCentralStore: เลือก source ถูก และไม่โตไม่จำกัด ────────────────

    /**
     * เดิม getAny ใช้ maxByOrNull { it.value.size } — ถ้า BINANCE_PAXG (คนละ instrument)
     * บังเอิญแคชไว้เยอะกว่า TV จะชนะ แล้วคำขอ "ทอง" ได้ราคา PAXG แทนทองจริง
     */
    @Test
    fun getAnyPrefersTrustedSourceOverLongerSeries() {
        val paxg = (0 until 500).map { bar(it * 60_000L, 1990.0) }
        val oanda = (0 until 50).map { bar(it * 60_000L, 2000.0) }

        OhlcvCentralStore.put("XAUUSD", "1m", "BINANCE_PAXG", paxg)
        OhlcvCentralStore.put("XAUUSD", "1m", "TV:OANDA", oanda)

        val best = OhlcvCentralStore.getAny("XAUUSD", "1m", 500)
        assertNotNull(best)
        assertEquals("TV:OANDA", best.first, "ต้องเลือก TV แม้ซีรีส์สั้นกว่า PAXG")
    }

    @Test
    fun getAnyFallsBackToLongestWhenPriorityTies() {
        OhlcvCentralStore.put("BTCUSDT", "1h", "BINANCE", (0 until 10).map { bar(it * 3_600_000L) })
        OhlcvCentralStore.put("BTCUSDT", "1h", "TV:BINANCE", (0 until 20).map { bar(it * 3_600_000L) })
        val best = OhlcvCentralStore.getAny("BTCUSDT", "1h", 100)
        assertNotNull(best)
        assertEquals("TV:BINANCE", best.first)
        assertEquals(20, best.second.size)
    }

    /** ซีรีส์ต้องมีเพดาน ไม่งั้น store โตไปเรื่อยๆ ใน session ยาว */
    @Test
    fun seriesLengthIsCapped() {
        val many = (0 until 5_000).map { bar(it * 60_000L) }
        OhlcvCentralStore.put("EURUSD", "1m", "TV:OANDA", many)
        val stored = OhlcvCentralStore.get("EURUSD", "1m", "TV:OANDA", 10_000)
        assertTrue(stored.size <= 1_200, "เก็บ ${stored.size} แท่ง — เกินเพดาน")
        // ต้องเก็บแท่งล่าสุดไว้ ไม่ใช่แท่งเก่า
        assertEquals(many.last().timestamp, stored.last().timestamp)
    }

    /** แท่งที่ใส่เข้ามาใหม่ต้องทับแท่งเดิมที่ timestamp เดียวกัน (เคสแท่งยังก่อตัวแล้วอัปเดต) */
    @Test
    fun laterPutOverwritesSameTimestamp() {
        val ts = 1_757_000_000_000L
        OhlcvCentralStore.put("XAUUSD", "5m", "TV:OANDA", listOf(bar(ts, 2000.0)))
        OhlcvCentralStore.put("XAUUSD", "5m", "TV:OANDA", listOf(bar(ts, 2050.0)))
        val stored = OhlcvCentralStore.get("XAUUSD", "5m", "TV:OANDA", 10)
        assertEquals(1, stored.size)
        assertEquals(2050.0, stored.single().close)
    }

    // ─── Warm-up table ───────────────────────────────────────────────────────

    @Test
    fun warmupRequirementsCoverTheStandardIndicatorSet() {
        // EMA200 ต้องการ warm-up มากกว่า period มาก ไม่ใช่แค่ 200
        assertTrue(TaIndicators.Warmup.FULL_SET >= 800, "ชุดเต็มต้อง >= 800 แท่ง")
        assertTrue(TaIndicators.Warmup.FULL_SET > TaIndicators.Warmup.LONGEST_STANDARD_PERIOD)
        // SMA ใช้หน้าต่างตายตัว → ต้องการพอดี period
        assertEquals(200, TaIndicators.Warmup.forWindowed(200))
        // EMA/Wilder ต้องเดินต่อหลัง seed
        assertEquals(800, TaIndicators.Warmup.forSmoothed(200))
        // รวมหลายตัว → เอาตัวที่หนักสุด
        assertEquals(
            800,
            TaIndicators.Warmup.forPeriods(windowed = listOf(20, 200), smoothed = listOf(14, 200))
        )
    }
}
