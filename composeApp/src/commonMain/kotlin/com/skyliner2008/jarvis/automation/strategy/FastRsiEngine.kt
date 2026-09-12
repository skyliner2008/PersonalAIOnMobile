package com.skyliner2008.jarvis.automation.strategy

import com.skyliner2008.jarvis.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.max

/**
 * FastRsiEngine — Port จาก `ABQ1.txt` (Jesse / TradingView RSI Trend Crypto)
 * ระบบจับโมเมนตัมแบบฉับไวด้วย Fast RSI(5):
 * - เข้า Long เมื่อ RSI(5) ตัดทะลุ 35 ขึ้น (Crosses Over 35) ชี้จุดสิ้นสุดของแรงเทขายระยะสั้น
 * - ปิดทำกำไรเมื่อ RSI(5) ตัดทะลุ 75 ลง (Crosses Under 75)
 * - Emergency Exit เมื่อ RSI(5) หลุด 10 ลง (Crosses Under 10)
 * - จุด Stop Loss 5% และ Take Profit 10% (หรือคำนวณตาม ATR)
 */
object FastRsiEngine {

    data class FastRsiResult(
        val rsi5: Double,
        val rsi5Prev: Double,
        val isCrossOver35: Boolean,
        val isCrossUnder75: Boolean,
        val isEmergencyExit10: Boolean,
        val signalSide: String, // "BUY", "EXIT", "EMERGENCY_EXIT", "ANTICIPATE_BUY", "NONE"
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double,
        val reasonTh: String
    )

    fun evaluate(
        candles: List<Candle>,
        rsiPeriod: Int = 5,
        oversoldCross: Double = 35.0,
        overboughtExit: Double = 75.0,
        emergencyExit: Double = 10.0,
        slPct: Double = 0.05,
        tpPct: Double = 0.10
    ): FastRsiResult? {
        if (candles.size <= rsiPeriod + 2) return null

        val n = candles.size
        val liveIdx = n - 1
        val live = candles[liveIdx]
        val closes = candles.map { it.close }

        val rsiArr = calculateRsi(closes, rsiPeriod)
        val rsiNow = rsiArr[liveIdx]
        val rsiPrev = rsiArr[liveIdx - 1]

        val isCrossOver35 = rsiPrev <= oversoldCross && rsiNow > oversoldCross
        val isCrossUnder75 = rsiPrev >= overboughtExit && rsiNow < overboughtExit
        val isEmergencyExit10 = rsiPrev >= emergencyExit && rsiNow < emergencyExit

        var signalSide = "NONE"
        var reason = ""

        if (isCrossOver35) {
            signalSide = "BUY"
            reason = "Fast RSI(5) ตัดทะลุแดน Oversold 35 ขึ้น (${"%.1f".format(rsiPrev)} ➔ ${"%.1f".format(rsiNow)}) เกิดจังหวะเด้งตัวฉับพลัน"
        } else if (isEmergencyExit10) {
            signalSide = "EMERGENCY_EXIT"
            reason = "Fast RSI(5) หลุดระดับวิกฤต 10 (${"%.1f".format(rsiNow)}) แจ้งเตือนคัตลอสฉุกเฉิน"
        } else if (isCrossUnder75) {
            signalSide = "EXIT"
            reason = "Fast RSI(5) ตัดกลับจากแดน Overbought 75 ลง (${"%.1f".format(rsiNow)}) แนะนำล็อกกำไร"
        } else if (rsiNow <= 25.0) {
            // Anticipation state: RSI ดิ่งลงต่ำกว่า 25 เตรียมตัวรอดีดทะลุ 35
            signalSide = "ANTICIPATE_BUY"
            reason = "Fast RSI(5) จมอยู่ในโซนต่ำมาก (${"%.1f".format(rsiNow)}) สะสมแรงเตรียมตัด 35 เด้งกลับ"
        }

        val entryPrice = live.close
        val stopLoss = entryPrice * (1.0 - slPct)
        val takeProfit = entryPrice * (1.0 + tpPct)

        return FastRsiResult(
            rsi5 = rsiNow,
            rsi5Prev = rsiPrev,
            isCrossOver35 = isCrossOver35,
            isCrossUnder75 = isCrossUnder75,
            isEmergencyExit10 = isEmergencyExit10,
            signalSide = signalSide,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            reasonTh = reason
        )
    }

    private fun calculateRsi(closes: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size <= period) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return out
    }
}
