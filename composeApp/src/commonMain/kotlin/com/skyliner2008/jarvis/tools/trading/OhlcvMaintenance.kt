package com.skyliner2008.jarvis.tools.trading

import com.skyliner2008.jarvis.automation.IndicatorAlertProvider
import com.skyliner2008.jarvis.automation.wake.AnticipationEngine
import com.skyliner2008.jarvis.automation.wake.WakeLearningStore
import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import kotlinx.datetime.Clock

/**
 * ดูแลขนาด OHLCV store (TvCandle) — ลบซีรีส์ที่ไม่ได้ใช้นาน
 *
 * ขนาดโดยประมาณ: 1 แท่ง ≈ 170 ไบต์ (แถว + UNIQUE index) → เพดาน 6,000 แท่ง ≈ 1 MB ต่อซีรีส์
 * 1 สินทรัพย์ที่ถูกเฝ้า 5TF + intermarket ≈ 6–8 MB — 20 สินทรัพย์ ≈ 150 MB ถ้าไม่ลบเลย
 *
 * กติกาการลบ:
 * - ซีรีส์ที่ไม่ถูกอ่านเกิน [IDLE_DAYS] วัน **และ** ไม่ได้ถูกใช้โดย alert ที่ยังเปิดอยู่
 *   (รวมตลาดที่เกี่ยวข้องของระบบปลุก เช่น DXY/US10Y ของทอง)
 * - ลบทั้งซีรีส์ (ไม่ตัดกลางซีรีส์ — ประวัติที่ขาดช่วงจะทำให้อินดิเคเตอร์ผิด)
 * - ถ้าเปิดดูสินทรัพย์นั้นอีก ระบบจะดึงใหม่อัตโนมัติ (backfill)
 */
object OhlcvMaintenance {
    const val IDLE_DAYS = 30
    const val BYTES_PER_BAR = 170L
    private const val DAY_MS = 86_400_000L
    private const val TOUCH_THROTTLE_MS = 60 * 60_000L
    private const val RUN_EVERY_MS = DAY_MS
    private const val KEY_LAST_RUN = "ohlcv.maintenance.last_run"

    private val lastTouch = mutableMapOf<String, Long>()

    /** บันทึกเวลาที่ซีรีส์ถูกอ่าน (เขียน DB อย่างมากชั่วโมงละครั้งต่อซีรีส์) */
    fun touch(symbol: String, interval: String, source: String, nowMs: Long = Clock.System.now().toEpochMilliseconds()) {
        val key = "$symbol|$interval|$source"
        kotlin.synchronized(lastTouch) {
            val prev = lastTouch[key]
            if (prev != null && nowMs - prev < TOUCH_THROTTLE_MS) return
            lastTouch[key] = nowMs
        }
        val db = JarvisDatabaseHolder.database ?: return
        runCatching { db.jarvisDatabaseQueries.touchTvSeries(symbol, interval, source, nowMs) }
    }

    data class SeriesInfo(val symbol: String, val interval: String, val source: String, val bars: Long, val lastUsed: Long)

    data class Stats(val series: List<SeriesInfo>) {
        val totalBars: Long get() = series.sumOf { it.bars }
        val estimatedBytes: Long get() = totalBars * BYTES_PER_BAR
        val symbols: Int get() = series.map { it.symbol }.distinct().size
    }

    fun stats(): Stats {
        val db = JarvisDatabaseHolder.database ?: return Stats(emptyList())
        val rows = runCatching { db.jarvisDatabaseQueries.getTvSeriesUsage().executeAsList() }.getOrDefault(emptyList())
        return Stats(rows.map { SeriesInfo(it.symbol, it.interval, it.source, it.bars, it.last_used ?: 0L) })
    }

    /** symbol (คีย์ใน store) ที่ยังถูกใช้โดย alert ที่เปิดอยู่ — ห้ามลบ */
    fun protectedSymbols(): Set<String> {
        val db = JarvisDatabaseHolder.database ?: return emptySet()
        val jobs = runCatching { db.jarvisDatabaseQueries.getActiveAlertSymbols().executeAsList() }.getOrDefault(emptyList())
        return protectedSymbolsFor(jobs)
    }

    internal fun protectedSymbolsFor(jobSymbols: List<String>): Set<String> = jobSymbols.flatMap { raw ->
        val base = IndicatorAlertProvider.splitSymbolAndTf(raw).first
        listOf(base) + AnticipationEngine.intermarketSymbolsFor(base).values
    }.map { SmcApiService.normalizeSymbolKey(it).uppercase() }.toSet()

    data class PruneResult(val deletedSeries: List<SeriesInfo>, val learningRowsBefore: Long, val learningRowsAfter: Long) {
        val deletedBars: Long get() = deletedSeries.sumOf { it.bars }
    }

    /** เลือกซีรีส์ที่ควรลบ (แยกจากการลบจริง เพื่อทดสอบได้) */
    internal fun selectStale(series: List<SeriesInfo>, protected: Set<String>, nowMs: Long, idleDays: Int = IDLE_DAYS): List<SeriesInfo> {
        val cutoff = nowMs - idleDays * DAY_MS
        return series.filter { it.lastUsed < cutoff && it.symbol.uppercase() !in protected }
    }

    fun prune(nowMs: Long = Clock.System.now().toEpochMilliseconds(), idleDays: Int = IDLE_DAYS): PruneResult {
        val db = JarvisDatabaseHolder.database ?: return PruneResult(emptyList(), 0, 0)
        val stale = selectStale(stats().series, protectedSymbols(), nowMs, idleDays)
        runCatching {
            db.transaction {
                stale.forEach {
                    db.jarvisDatabaseQueries.deleteTvSeries(it.symbol, it.interval, it.source)
                    db.jarvisDatabaseQueries.deleteTvSeriesAccess(it.symbol, it.interval, it.source)
                }
            }
        }.onFailure { logDebug("OhlcvMaintenance", "prune failed: ${it.message}") }
        stale.forEach { OhlcvCentralStore.invalidate(it.symbol, it.interval) }

        // แถวการเรียนรู้ที่รอผลเกิน 30 วัน (job ถูกลบ / ไม่มีใครสแกนแล้ว) — ปิดเป็น EXPIRED
        runCatching { db.jarvisDatabaseQueries.expireStalePendingFactorOutcomes(nowMs, nowMs - 30 * DAY_MS) }
        // มุมมองของ AI ที่ติดตามไม่จบใน 30 วัน / เก่ากว่า 1 ปี
        runCatching { db.jarvisDatabaseQueries.expireStaleOpenAiViews(nowMs, nowMs - 30 * DAY_MS) }
        runCatching {
            db.jarvisDatabaseQueries.deleteAiViewsBefore(nowMs - com.skyliner2008.jarvis.automation.wake.AiViewTracker.RETENTION_DAYS * DAY_MS)
            db.jarvisDatabaseQueries.deleteOldSkipAiViews(
                nowMs - com.skyliner2008.jarvis.automation.wake.AiViewTracker.SKIP_RETENTION_DAYS * DAY_MS)
        }

        // ประวัติการเรียนรู้ของระบบปลุกเก่ากว่า 1 ปี
        val before = runCatching { db.jarvisDatabaseQueries.countFactorOutcomes().executeAsOne() }.getOrDefault(0L)
        runCatching {
            // ยุบก่อนลบ ใน transaction เดียว — ถ้ายุบสำเร็จแต่ลบพลาด สถิติจะถูกนับซ้ำ
            db.transaction {
                WakeLearningStore.foldAgedBeforeDelete(nowMs)
                db.jarvisDatabaseQueries.deleteFactorOutcomesBefore(nowMs - WakeLearningStore.RETENTION_DAYS * DAY_MS)
            }
        }
        // ยุบแถวที่วัดผลแล้วและไม่ได้ปลุก AI เป็นสถิติสะสม (5 TF ทุกนาที = ~6,000 แถว/วัน/สินทรัพย์)
        runCatching { WakeLearningStore.foldOldOutcomes(nowMs) }
        val after = runCatching { db.jarvisDatabaseQueries.countFactorOutcomes().executeAsOne() }.getOrDefault(before)
        if (after != before) WakeLearningStore.invalidateCache()

        if (stale.isNotEmpty() || after != before) {
            logDebug("OhlcvMaintenance",
                "🧹 ลบ ${stale.size} ซีรีส์ (${stale.sumOf { it.bars }} แท่ง) + learning ${before - after} แถว")
        }
        return PruneResult(stale, before, after)
    }

    /** เรียกจาก automation loop — ทำงานจริงวันละครั้ง */
    fun runIfDue(nowMs: Long = Clock.System.now().toEpochMilliseconds()): PruneResult? {
        val db = JarvisDatabaseHolder.database ?: return null
        val last = runCatching { db.jarvisDatabaseQueries.getSetting(KEY_LAST_RUN).executeAsOneOrNull()?.toLongOrNull() }
            .getOrNull() ?: 0L
        if (nowMs - last < RUN_EVERY_MS) return null
        runCatching { db.jarvisDatabaseQueries.insertSetting(KEY_LAST_RUN, nowMs.toString()) }
        return prune(nowMs)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
