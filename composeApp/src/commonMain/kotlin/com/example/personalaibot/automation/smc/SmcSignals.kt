package com.example.personalaibot.automation.smc

import com.example.personalaibot.tools.trading.Candle

/**
 * SmcSignals — เดินหน้าทีละแท่งผ่านชุดแท่งเทียน สร้าง snapshot จาก window ย้อนหลัง
 * (กัน lookahead — เหมือนการเห็นกราฟ ณ แท่งนั้นจริงๆ) แล้วรัน SmcSignalDetector
 *
 * ใช้กับ:
 *  - Backtest: generate() คืนสัญญาณทุกจุดย้อนหลังพร้อม SL/TP ของตัวเอง (structure-based)
 *  - Live alert: newSignalsAt() คืนเฉพาะสัญญาณที่ "เพิ่งเกิด" ที่แท่ง idx (เทียบ key กับแท่งก่อน)
 */
object SmcSignals {

    data class SmcBarSignal(
        val barIndex: Int,
        val time: Long,
        val signal: SmcSignalDetector.SmcEntrySignal
    )

    const val WINDOW = 300
    const val WARMUP = 120   // ต้องการ ≥60 แท่งต่อ snapshot + เผื่อ swing/structure
    const val COOLDOWN_BARS = 10   // ห้ามสัญญาณ strategy+side เดิมซ้ำภายใน N แท่ง (แทน duplicate guard ของ MT5)

    /** สัญญาณทั้งหมดในชุด (edge-dedupe: คีย์ซ้ำกับแท่งก่อนหน้า + cooldown ต่อ strategy/side) */
    fun generate(candles: List<Candle>, symbol: String, timeframe: String): List<SmcBarSignal> {
        if (candles.size < WARMUP + 2) return emptyList()
        val out = mutableListOf<SmcBarSignal>()
        var prevKeys: Set<String> = emptySet()
        val lastFired = HashMap<String, Int>()
        // หยุดก่อนแท่งสุดท้าย — แท่ง n-1 อาจยังไม่ปิด และไม่มีอนาคตให้จำลอง
        for (i in WARMUP until candles.size - 1) {
            val window = candles.subList(maxOf(0, i - WINDOW + 1), i + 1)
            val signals = SmcSignalDetector.detect(symbol, timeframe, window)
            val keys = signals.map { it.key }.toSet()
            for (s in signals) {
                val cooldownKey = "${s.side}|${s.strategy}"
                val lastBar = lastFired[cooldownKey]
                if (s.key !in prevKeys && (lastBar == null || i - lastBar >= COOLDOWN_BARS)) {
                    out += SmcBarSignal(i, candles[i].timestamp, s)
                    lastFired[cooldownKey] = i
                }
            }
            prevKeys = keys
        }
        return out
    }

    /** เฉพาะสัญญาณใหม่ ณ แท่ง idx (เทียบกับแท่ง idx-1) — ใช้กับ live alert */
    fun newSignalsAt(candles: List<Candle>, idx: Int, symbol: String, timeframe: String): List<SmcSignalDetector.SmcEntrySignal> {
        if (idx < WARMUP || idx >= candles.size) return emptyList()
        val cur = SmcSignalDetector.detect(symbol, timeframe, candles.subList(maxOf(0, idx - WINDOW + 1), idx + 1))
        if (cur.isEmpty()) return emptyList()
        val prev = SmcSignalDetector.detect(symbol, timeframe, candles.subList(maxOf(0, idx - WINDOW), idx))
        val prevKeys = prev.map { it.key }.toSet()
        return cur.filter { it.key !in prevKeys }
    }
}
