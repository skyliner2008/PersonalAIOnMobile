package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.backup.LearningTransfer
import com.skyliner2008.jarvis.tools.trading.OhlcvMaintenance
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ไฟล์การเรียนรู้ (export/import) และการลบ OHLCV ที่ไม่ได้ใช้ */
class BackupAndMaintenanceTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    // ─── Learning file ───────────────────────────────────────────────────────

    private fun sampleBundle() = LearningTransfer.Bundle(
        exportedAt = now,
        settings = LearningTransfer.Settings(disabledFactors = listOf("PIN_BAR"), hourlyBudget = 8),
        outcomes = listOf(
            LearningTransfer.Outcome(
                signalId = "XAUUSD|15m|1", factorId = "EMA_CROSS", symbol = "XAUUSD", interval = "15m",
                side = "BUY", kind = "EVENT", refPrice = 4000.0, refAtr = 5.0, woke = 1,
                mtfAlign = "ALIGNED", adxBucket = "TREND", volBucket = "NORMAL", session = "LONDON",
                aiDecision = "NOTIFY", aiBias = "BUY", status = "RESOLVED",
                forwardR = 1.2, mfeR = 2.0, maeR = 0.4, createdAt = now - day, resolvedAt = now
            )
        )
    )

    @Test
    fun learningFileRoundTrips() {
        val text = Json { encodeDefaults = true }.encodeToString(LearningTransfer.Bundle.serializer(), sampleBundle())
        val parsed = LearningTransfer.parse(text)
        assertEquals(sampleBundle(), parsed)
        assertEquals(LearningTransfer.FORMAT, parsed.format)
    }

    @Test
    fun learningFileRejectsForeignOrNewerFiles() {
        assertFailsWith<IllegalArgumentException> { LearningTransfer.parse("{\"hello\":1}") }
        assertFailsWith<IllegalArgumentException> { LearningTransfer.parse("ไม่ใช่ json") }
        val other = Json { encodeDefaults = true }.encodeToString(
            LearningTransfer.Bundle.serializer(), sampleBundle().copy(format = "something-else")
        )
        assertFailsWith<IllegalArgumentException> { LearningTransfer.parse(other) }
        val newer = Json { encodeDefaults = true }.encodeToString(
            LearningTransfer.Bundle.serializer(), sampleBundle().copy(version = LearningTransfer.VERSION + 1)
        )
        val e = assertFailsWith<IllegalArgumentException> { LearningTransfer.parse(newer) }
        assertTrue(e.message!!.contains("ใหม่กว่า"))
    }

    @Test
    fun learningFileToleratesUnknownFields() {
        val text = Json { encodeDefaults = true }.encodeToString(LearningTransfer.Bundle.serializer(), sampleBundle())
            .replaceFirst("{", "{\"futureField\":\"x\",")
        assertEquals(1, LearningTransfer.parse(text).outcomes.size)
    }

    /** ไฟล์ที่ส่งออกก่อนมีคอลัมน์เหตุผล/ความมั่นใจของ AI (migration 14) ต้องนำเข้าได้ — ค่าใหม่เป็น null */
    @Test
    fun learningFileWithoutAiReasonStillParses() {
        val text = Json { encodeDefaults = true }.encodeToString(LearningTransfer.Bundle.serializer(), sampleBundle())
            .replace(Regex(",\\\"aiConfidence\\\":null,\\\"aiReason\\\":null"), "")
        assertTrue(!text.contains("aiReason"), "ต้องจำลองไฟล์รุ่นเก่าได้จริง")
        val o = LearningTransfer.parse(text).outcomes.single()
        assertEquals(null, o.aiConfidence)
        assertEquals(null, o.aiReason)
    }

    @Test
    fun learningFileKeepsAiReason() {
        val withReason = sampleBundle().copy(outcomes = sampleBundle().outcomes.map { it.copy(aiConfidence = 40, aiReason = "หลักฐานขัดกัน") })
        val text = Json { encodeDefaults = true }.encodeToString(LearningTransfer.Bundle.serializer(), withReason)
        val o = LearningTransfer.parse(text).outcomes.single()
        assertEquals(40L, o.aiConfidence)
        assertEquals("หลักฐานขัดกัน", o.aiReason)
    }

    // ─── OHLCV pruning ───────────────────────────────────────────────────────

    private fun series(sym: String, tf: String, idleDays: Int) =
        OhlcvMaintenance.SeriesInfo(sym, tf, "TV:OANDA", 6000, now - idleDays * day)

    @Test
    fun pruneSelectsOnlyIdleUnprotectedSeries() {
        val all = listOf(
            series("XAUUSD", "15m", 90),      // มี alert เปิดอยู่ → เก็บไว้
            series("TVC:DXY", "1h", 90),      // intermarket ของทอง → เก็บไว้
            series("EURUSD", "1h", 45),       // ไม่ได้ใช้นาน → ลบ
            series("GBPUSD", "1h", 5)         // เพิ่งใช้ → เก็บไว้
        )
        val protected = OhlcvMaintenance.protectedSymbolsFor(listOf("XAUUSD@15m"))
        val stale = OhlcvMaintenance.selectStale(all, protected, now)
        assertEquals(listOf("EURUSD"), stale.map { it.symbol })
    }

    @Test
    fun protectedSymbolsIncludeIntermarketFeeds() {
        val gold = OhlcvMaintenance.protectedSymbolsFor(listOf("XAUUSD@15m"))
        assertTrue("XAUUSD" in gold && "TVC:DXY" in gold && "TVC:US10Y" in gold, gold.toString())
        val crypto = OhlcvMaintenance.protectedSymbolsFor(listOf("ETHUSDT@1h"))
        assertTrue("ETHUSDT" in crypto && "BTCUSDT" in crypto && "CRYPTOCAP:BTC.D" in crypto, crypto.toString())
    }

    @Test
    fun storageEstimate() {
        val stats = OhlcvMaintenance.Stats(listOf(series("XAUUSD", "15m", 0), series("XAUUSD", "1h", 0)))
        assertEquals(12_000, stats.totalBars)
        assertEquals(1, stats.symbols)
        assertEquals(12_000 * OhlcvMaintenance.BYTES_PER_BAR, stats.estimatedBytes)
        assertEquals("1.9 MB", OhlcvMaintenance.formatBytes(stats.estimatedBytes))
    }
}
