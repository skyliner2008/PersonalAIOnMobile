package com.example.personalaibot.automation.backtest

import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs

/**
 * RegimeClassifier — จำแนกสภาพตลาดต่อแท่ง: BULL / BEAR / SIDEWAYS
 * (port v1 จาก moss-trade-bot scripts/core/regime.py: ADX + EMA50 slope คลาสสิก)
 * ใช้แยกสถิติ backtest ว่ากลยุทธ์ไหนเกิดใน regime ไหน
 */
object RegimeClassifier {

    const val BULL = "BULL"
    const val BEAR = "BEAR"
    const val SIDEWAYS = "SIDEWAYS"

    /**
     * คืน regime ต่อแท่ง (ขนาดเท่า candles) — แท่งต้นๆ ที่ indicator ยังไม่พร้อม = SIDEWAYS
     * กติกา v1: ADX14 ≥ adxMin และ EMA50 slope (window แท่ง) บวก → BULL, ลบ → BEAR, อื่นๆ → SIDEWAYS
     */
    fun classify(candles: List<Candle>, window: Int = 48, adxMin: Double = 25.0): List<String> {
        val n = candles.size
        val out = MutableList(n) { SIDEWAYS }
        if (n < 210) return out

        val closes = candles.map { it.close }
        val ema50 = emaSeries(closes, 50)
        val adxS = adxSeries(candles, 14)

        for (i in window + 50 until n) {
            val e0 = ema50[i - window]
            val e1 = ema50[i]
            if (e0.isNaN() || e1.isNaN() || e0 == 0.0) continue
            val slope = (e1 - e0) / e0
            val adx = adxS[i]
            out[i] = when {
                adx >= adxMin && slope > 0 -> BULL
                adx >= adxMin && slope < 0 -> BEAR
                else -> SIDEWAYS
            }
        }
        return out
    }

    /** สรุปสถิติ trades แยกตาม regime ณ แท่งที่เข้าไม้ */
    data class RegimeStats(
        val regime: String,
        val trades: Int,
        val wins: Int,
        val winRate: Double,
        val avgR: Double,
        val totalR: Double
    )

    fun statsByRegime(
        trades: List<BacktestTrade>,
        candles: List<Candle>,
        regimes: List<String>
    ): List<RegimeStats> {
        val timeToIdx = HashMap<Long, Int>(candles.size)
        for (i in candles.indices) timeToIdx[candles[i].timestamp] = i
        return trades.groupBy { t ->
            val idx = timeToIdx[t.entryTime] ?: return@groupBy SIDEWAYS
            regimes.getOrElse(idx) { SIDEWAYS }
        }.map { (regime, list) ->
            val w = list.count { it.pnlMoney > 0 }
            RegimeStats(
                regime = regime,
                trades = list.size,
                wins = w,
                winRate = if (list.isNotEmpty()) w.toDouble() / list.size else 0.0,
                avgR = if (list.isNotEmpty()) list.sumOf { it.pnlR } / list.size else 0.0,
                totalR = list.sumOf { it.pnlR }
            )
        }.sortedBy { it.regime }
    }

    // ─── Indicators (Wilder) ───

    private fun emaSeries(values: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        val k = 2.0 / (period + 1)
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** ADX (Wilder) ทั้งซีรีส์ — อ้างอิงสูตรเดียวกับ IndicatorAlertProvider.adx */
    private fun adxSeries(candles: List<Candle>, period: Int): DoubleArray {
        val n = candles.size
        val out = DoubleArray(n)
        if (n < period * 2 + 1) return out

        val tr = DoubleArray(n); val pdm = DoubleArray(n); val ndm = DoubleArray(n)
        for (i in 1 until n) {
            val c = candles[i]; val p = candles[i - 1]
            tr[i] = maxOf(c.high - c.low, abs(c.high - p.close), abs(c.low - p.close))
            val up = c.high - p.high
            val down = p.low - c.low
            pdm[i] = if (up > down && up > 0) up else 0.0
            ndm[i] = if (down > up && down > 0) down else 0.0
        }
        var atr = tr.slice(1..period).sum()
        var sPdm = pdm.slice(1..period).sum()
        var sNdm = ndm.slice(1..period).sum()
        val dx = DoubleArray(n)
        for (i in period + 1 until n) {
            atr = atr - atr / period + tr[i]
            sPdm = sPdm - sPdm / period + pdm[i]
            sNdm = sNdm - sNdm / period + ndm[i]
            val pdi = if (atr > 0) 100 * sPdm / atr else 0.0
            val ndi = if (atr > 0) 100 * sNdm / atr else 0.0
            val diSum = pdi + ndi
            dx[i] = if (diSum > 0) 100 * abs(pdi - ndi) / diSum else 0.0
        }
        // ADX = Wilder smooth ของ DX
        var adx = 0.0
        val start = period * 2
        if (start < n) {
            adx = dx.slice(period + 1..start).average()
            out[start] = adx
            for (i in start + 1 until n) {
                adx = (adx * (period - 1) + dx[i]) / period
                out[i] = adx
            }
        }
        return out
    }
}
