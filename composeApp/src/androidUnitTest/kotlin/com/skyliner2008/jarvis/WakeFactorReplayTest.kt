package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeLearningStore
import com.skyliner2008.jarvis.automation.wake.WakeTfProfile
import com.skyliner2008.jarvis.automation.wake.WakeTriggerRegistry
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import java.io.File
import kotlin.test.Test

/**
 * เล่นซ้ำปัจจัยปลุก AI ทั้ง 115 ตัวบนแท่งเทียนจริงจากมือถือ — ใช้คู่กับ `tools/wake_factor_replay.py`
 *
 * ข้ามเงียบๆ เมื่อไม่มี `build/wake_replay/meta.txt` (สคริปต์เป็นผู้เตรียมไฟล์)
 * แต่ละจุดเวลา = เวลาปิดของแท่ง TF หลัก ใช้เฉพาะแท่งที่ปิดแล้ว ณ เวลานั้นของทุก TF (ไม่มองอนาคต)
 * ผลลัพธ์ `out.jsonl`: ค่าอินดิเคเตอร์ต่อ TF (ทั้งของ trigger และของภาพ 5TF) + ปัจจัยที่เกิด
 */
class WakeFactorReplayTest {
    private val dir = File("build/wake_replay")

    private fun load(tf: String): List<Candle> {
        val f = File(dir, "$tf.csv")
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map { line ->
            val p = line.split(',')
            Candle(p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble(), p[0].toLong(), true)
        }
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun n(v: Double?) = if (v == null || v.isNaN() || v.isInfinite()) "null" else v.toString()

    @Test
    fun replay() {
        val meta = File(dir, "meta.txt").takeIf { it.exists() }?.readLines() ?: return
        val symbol = meta[0].trim()
        val primaryTf = meta[1].trim()
        val points = meta[2].trim().toInt()
        val tfs = listOf("1m", "5m", "15m", "1h", "4h")
        // เหมือน AnticipationEngine: ทุก TF ใช้ FULL_SET (ปัจจัยถูกประเมินทุก TF)
        val limits = tfs.associateWith { TaIndicators.Warmup.FULL_SET }
        val all = tfs.associateWith { load(it) }
        val ms = tfs.associateWith { TaIndicators.timeframeMillis(it) }
        // จุดเวลาที่เล่นซ้ำ = เวลาปิดแท่งของ step TF (บรรทัดที่ 4 ของ meta, เริ่มต้น = TF ของ job)
        // step 1m = เหมือนมือถือที่สแกนทุกนาที
        val stepTf = meta.getOrNull(3)?.trim()?.takeIf { it in tfs } ?: primaryTf
        val primary = all.getValue(stepTf)
        val pMs = ms.getValue(stepTf)

        val out = File(dir, "out.jsonl").bufferedWriter()
        out.use { w ->
            for (bar in primary.takeLast(points)) {
                val t = bar.timestamp + pMs   // เวลาปิดแท่ง
                val cut = tfs.associateWith { tf ->
                    all.getValue(tf).filter { it.timestamp + ms.getValue(tf) <= t }.takeLast(limits.getValue(tf))
                }
                val digest = runCatching {
                    MarketContextDigest.build(symbol, cut.getValue("4h"), cut.getValue("1h"), cut.getValue("15m"),
                        cut.getValue("5m"), cut.getValue("1m"), closedAware = true, tradeRefs = false)
                }.getOrNull()
                val ctx = WakeContext(symbol, primaryTf, cut.getValue("1m"), cut.getValue("5m"), cut.getValue("15m"),
                    cut.getValue("1h"), cut.getValue("4h"), digest = digest, nowMs = t)

                // ค่าอินดิเคเตอร์ที่ trigger ใช้ (Series) ต่อ TF
                for (tf in tfs) {
                    val s = ctx.series(tf)
                    if (s.n < 60) continue
                    val bb = TaIndicators.bollingerBands(s.closes, 20, 2.0)
                    val adx = TaIndicators.adx(s.highs, s.lows, s.closes, 14)
                    w.write("{\"t\":$t,\"k\":\"series\",\"tf\":\"$tf\",\"n\":${s.n},\"last_ts\":${s.last!!.timestamp}," +
                        "\"ema20\":${n(s.ema(20))},\"ema50\":${n(s.ema(50))},\"ema200\":${n(s.ema(200))}," +
                        "\"rsi14\":${n(s.rsi(14))},\"atr14\":${n(s.atr())},\"macd_hist\":${n(s.macdHist.lastOrNull())}," +
                        "\"adx\":${n(adx?.adx)},\"bb_pb\":${n(bb?.percentB)}}\n")
                }
                // ค่าในภาพ 5TF ที่ AI เห็น
                digest?.tfLines?.forEach { l ->
                    w.write("{\"t\":$t,\"k\":\"digest\",\"tf\":\"${l.tf}\",\"trend\":${l.trend},\"event\":${q(l.lastEvent)}," +
                        "\"close\":${n(l.close)},\"rsi14\":${n(l.rsi)},\"ema50\":${n(l.ema50)},\"atr14\":${n(l.atr)}," +
                        "\"adx\":${n(l.adx)},\"macd_hist\":${n(l.macdHist)},\"bb_pb\":${n(l.bbPercentB)}," +
                        "\"bbw_atr\":${n(l.bbWidthAtr)},\"vol_ratio\":${n(l.volumeRatio)}," +
                        "\"swing_high\":${n(l.swingHigh)},\"swing_low\":${n(l.swingLow)}," +
                        "\"seq\":${l.detail?.swingSeq?.let { q(it) } ?: "null"},\"pos50\":${n(l.detail?.rangePos50)}," +
                        "\"ema20\":${n(l.detail?.ema20)},\"ema200\":${n(l.detail?.ema200)},\"rsi_prev3\":${n(l.detail?.rsiPrev3)}," +
                        "\"div\":${l.detail?.divergence?.let { q(it) } ?: "null"},\"move12\":${n(l.detail?.move12Atr)}," +
                        "\"streak\":${l.detail?.streak ?: "null"},\"atr_pct\":${n(l.detail?.atrPercentile)}," +
                        "\"candle\":${l.detail?.candle?.let { q(it) } ?: "null"}}\n")
                }
                digest?.let { d ->
                    val lv = (d.levelsAbove + d.levelsBelow).joinToString(",") { "{\"kind\":${q(it.kind)},\"price\":${it.price}}" }
                    w.write("{\"t\":$t,\"k\":\"levels\",\"levels\":[$lv]}\n")
                }
                if (bar === primary.last()) digest?.let { w.write("{\"t\":$t,\"k\":\"digest_text\",\"text\":${q(it.text)}}\n") }
                // โครงสร้าง (BOS/CHoCH) ของแท่งปิดล่าสุดแต่ละ TF + ทุกเหตุการณ์ใน 30 แท่งล่าสุด (ตรวจ repaint)
                for (tf in tfs) {
                    val s = ctx.series(tf)
                    val st = s.structure ?: continue
                    val evs = (maxOf(0, s.n - 30) until s.n).flatMap { i ->
                        listOfNotNull(
                            if (st.chochUp[i]) "CHoCH_UP" else null, if (st.chochDn[i]) "CHoCH_DN" else null,
                            if (st.bosUp[i]) "BOS_UP" else null, if (st.bosDn[i]) "BOS_DN" else null
                        ).map { "${s.bars[i].timestamp}:$it" }
                    }
                    w.write("{\"t\":$t,\"k\":\"struct\",\"tf\":\"$tf\",\"trend\":${st.trend[s.n - 1]},\"events\":[${evs.joinToString(",") { q(it) }}]}\n")
                }

                // ปัจจัยทั้งหมดบนทุก TF — เหมือน AnticipationEngine (scanAllTf) + เวลาที่ใช้ต่อรอบ
                val started = System.nanoTime()
                val scan = WakeTriggerRegistry.scanAllTf(ctx, tfs)
                val scanMs = (System.nanoTime() - started) / 1e6
                w.write("{\"t\":$t,\"k\":\"perf\",\"scan_ms\":$scanMs}\n")
                scan.errors.forEach { w.write("{\"t\":$t,\"k\":\"error\",\"id\":${q(it.substringBefore('@'))},\"msg\":${q(it)}}\n") }
                for (e in scan.events + scan.states) {
                    val tr = WakeTriggerRegistry.find(e.triggerId) ?: continue
                    val wakes = WakeLearningStore.tfVerdict(e.triggerId, e.tf, primaryTf).wakes
                    w.write("{\"t\":$t,\"k\":\"fire\",\"id\":\"${tr.id}\",\"kind\":\"${tr.kind}\",\"group\":\"${tr.evidenceGroup}\"," +
                        "\"tf\":\"${e.tf}\",\"dir\":\"${e.direction}\",\"wakes\":$wakes,\"fixed\":${WakeTfProfile.isFixed(tr.id)}," +
                        "\"what\":${q(e.what)}}\n")
                }
            }
            // ทะเบียนปัจจัย
            for (tr in WakeTriggerRegistry.ALL)
                w.write("{\"k\":\"trigger\",\"id\":\"${tr.id}\",\"name\":${q(tr.name)},\"kind\":\"${tr.kind}\",\"group\":\"${tr.evidenceGroup}\"}\n")
        }
    }
}
