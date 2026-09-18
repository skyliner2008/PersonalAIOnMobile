package com.skyliner2008.jarvis.automation.wake.triggers

import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.OSCILLATOR_EXTREME
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.TREND_MOMENTUM
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.VOLATILITY_SQUEEZE
import com.skyliner2008.jarvis.automation.wake.Series
import com.skyliner2008.jarvis.automation.wake.TriggerEvent
import com.skyliner2008.jarvis.automation.wake.TriggerKind.EVENT
import com.skyliner2008.jarvis.automation.wake.TriggerKind.STATE
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeTrigger
import com.skyliner2008.jarvis.automation.wake.p
import com.skyliner2008.jarvis.automation.wake.tfLabel
import com.skyliner2008.jarvis.automation.wake.trigger
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.crossDown
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.crossUp
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import com.skyliner2008.jarvis.tools.trading.TaIndicators.Warmup
import kotlin.math.abs

// ═════════════════════════════════════════════════════════════════════════════
// D. เทรนด์ / เส้นค่าเฉลี่ย
// ═════════════════════════════════════════════════════════════════════════════

/** เส้นค่าเฉลี่ยสองเส้นตัดกันที่แท่งปิดล่าสุด */
private fun maCross(ctx: WakeContext, id: String, fast: Int, slow: Int, upText: String, dnText: String): TriggerEvent? {
    val s = ctx.primary
    val f0 = s.ema(fast, 1) ?: return null; val f1 = s.ema(fast) ?: return null
    val s0 = s.ema(slow, 1) ?: return null; val s1 = s.ema(slow) ?: return null
    return when {
        crossUp(f0, f1, s0, s1) -> TriggerEvent(id, s.tf, upText, "BUY", s1)
        crossDown(f0, f1, s0, s1) -> TriggerEvent(id, s.tf, dnText, "SELL", s1)
        else -> null
    }
}

private fun ribbon(s: Series, back: Int): Int? {
    val a = s.ema(20, back) ?: return null
    val b = s.ema(50, back) ?: return null
    val c = s.ema(200, back) ?: return null
    return when { a > b && b > c -> 1; a < b && b < c -> -1; else -> 0 }
}

internal val TREND_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("EMA_CROSS", "EMA 14/60 ตัดกัน", TREND_MOMENTUM, EVENT, minBars = Warmup.forSmoothed(60)) {
        maCross(it, "EMA_CROSS", 14, 60, "EMA14 ตัด EMA60 ขึ้น (Golden Cross ระยะสั้น)", "EMA14 ตัด EMA60 ลง (Death Cross ระยะสั้น)")
    },

    trigger("EMA_CONVERGENCE", "EMA 14/60 บีบเข้าหากัน ใกล้ตัด", TREND_MOMENTUM, STATE, minBars = Warmup.forSmoothed(60)) { ctx ->
        val s = ctx.primary
        val f = s.ema(14) ?: return@trigger null; val sl = s.ema(60) ?: return@trigger null
        val fp = s.ema(14, 1) ?: return@trigger null; val slp = s.ema(60, 1) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val gap = abs(f - sl); val gapPrev = abs(fp - slp)
        if (gap > 0.35 * atr || gap >= gapPrev) return@trigger null
        val dir = if (f < sl) "BUY" else "SELL"
        TriggerEvent("EMA_CONVERGENCE", s.tf, "EMA14 กับ EMA60 บีบเข้าหากัน (ห่าง ${"%.2f".format(gap / atr)}×ATR) ใกล้${if (dir == "BUY") " Golden" else " Death"} Cross", dir)
    },

    trigger("GOLDEN_DEATH_CROSS", "EMA 50/200 ตัดกัน", TREND_MOMENTUM, EVENT, minBars = Warmup.FULL_SET) {
        maCross(it, "GOLDEN_DEATH_CROSS", 50, 200, "EMA50 ตัด EMA200 ขึ้น (Golden Cross)", "EMA50 ตัด EMA200 ลง (Death Cross)")
    },

    trigger("PRICE_CROSS_EMA200", "ราคาทะลุ EMA200", TREND_MOMENTUM, EVENT, minBars = Warmup.FULL_SET) { ctx ->
        val s = ctx.primary
        val c0 = s.prev?.close ?: return@trigger null; val c1 = s.last?.close ?: return@trigger null
        val e0 = s.ema(200, 1) ?: return@trigger null; val e1 = s.ema(200) ?: return@trigger null
        when {
            crossUp(c0, c1, e0, e1) -> TriggerEvent("PRICE_CROSS_EMA200", s.tf, "ราคาปิดเหนือ EMA200 (${p(e1)})", "BUY", e1)
            crossDown(c0, c1, e0, e1) -> TriggerEvent("PRICE_CROSS_EMA200", s.tf, "ราคาปิดใต้ EMA200 (${p(e1)})", "SELL", e1)
            else -> null
        }
    },

    trigger("EMA_RIBBON_ALIGN", "EMA 20/50/200 เรียงตัวครั้งแรก", TREND_MOMENTUM, EVENT, minBars = Warmup.FULL_SET) { ctx ->
        val s = ctx.primary
        val now = ribbon(s, 0) ?: return@trigger null
        val before = ribbon(s, 1) ?: return@trigger null
        when {
            now == 1 && before != 1 -> TriggerEvent("EMA_RIBBON_ALIGN", s.tf, "EMA20 > EMA50 > EMA200 เรียงตัวขาขึ้นสมบูรณ์", "BUY")
            now == -1 && before != -1 -> TriggerEvent("EMA_RIBBON_ALIGN", s.tf, "EMA20 < EMA50 < EMA200 เรียงตัวขาลงสมบูรณ์", "SELL")
            else -> null
        }
    },

    trigger("EMA_PULLBACK", "ย่อแตะ EMA ในเทรนด์", TREND_MOMENTUM, EVENT, minBars = Warmup.FULL_SET) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null
        val trend = ribbon(s, 0) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val e20 = s.ema(20) ?: return@trigger null; val e50 = s.ema(50) ?: return@trigger null
        if (trend == 1) {
            val lvl = listOf(e20, e50).firstOrNull { last.low <= it + 0.1 * atr && last.close > it } ?: return@trigger null
            return@trigger TriggerEvent("EMA_PULLBACK", s.tf, "ราคาย่อแตะ EMA${if (lvl == e20) "20" else "50"} (${p(lvl)}) ในเทรนด์ขาขึ้นแล้วยืนได้", "BUY", lvl)
        }
        if (trend == -1) {
            val lvl = listOf(e20, e50).firstOrNull { last.high >= it - 0.1 * atr && last.close < it } ?: return@trigger null
            return@trigger TriggerEvent("EMA_PULLBACK", s.tf, "ราคาดีดแตะ EMA${if (lvl == e20) "20" else "50"} (${p(lvl)}) ในเทรนด์ขาลงแล้วถูกกดลง", "SELL", lvl)
        }
        null
    },

    trigger("SUPERTREND_FLIP", "Supertrend กลับทิศ", TREND_MOMENTUM, EVENT, minBars = Warmup.forSmoothed(10) + 2) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.supertrend(s.highs, s.lows, s.closes) ?: return@trigger null
        val before = TaIndicators.supertrend(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        if (now.isBullish == before.isBullish) return@trigger null
        TriggerEvent(
            "SUPERTREND_FLIP", s.tf,
            "Supertrend พลิกเป็น${if (now.isBullish) "ขาขึ้น" else "ขาลง"} (เส้น ${p(now.value)})",
            if (now.isBullish) "BUY" else "SELL", now.value
        )
    },

    trigger("ICHIMOKU_TK_CROSS", "Ichimoku Tenkan/Kijun ตัดกัน", TREND_MOMENTUM, EVENT, minBars = Warmup.ICHIMOKU_SET) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.ichimoku(s.highs, s.lows, s.closes) ?: return@trigger null
        val before = TaIndicators.ichimoku(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        when {
            crossUp(before.tenkan, now.tenkan, before.kijun, now.kijun) -> TriggerEvent("ICHIMOKU_TK_CROSS", s.tf, "Tenkan ตัด Kijun ขึ้น${if (ctx.price > now.cloudTop) " เหนือเมฆ" else ""}", "BUY")
            crossDown(before.tenkan, now.tenkan, before.kijun, now.kijun) -> TriggerEvent("ICHIMOKU_TK_CROSS", s.tf, "Tenkan ตัด Kijun ลง${if (ctx.price < now.cloudBottom) " ใต้เมฆ" else ""}", "SELL")
            else -> null
        }
    },

    trigger("ICHIMOKU_CLOUD_BREAK", "ราคาทะลุเมฆ Ichimoku", TREND_MOMENTUM, EVENT, minBars = Warmup.ICHIMOKU_SET) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.ichimoku(s.highs, s.lows, s.closes) ?: return@trigger null
        val before = TaIndicators.ichimoku(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        val c0 = s.prev?.close ?: return@trigger null; val c1 = s.last?.close ?: return@trigger null
        when {
            crossUp(c0, c1, before.cloudTop, now.cloudTop) -> TriggerEvent("ICHIMOKU_CLOUD_BREAK", s.tf, "ราคาปิดทะลุเหนือเมฆ (${p(now.cloudTop)})", "BUY", now.cloudTop)
            crossDown(c0, c1, before.cloudBottom, now.cloudBottom) -> TriggerEvent("ICHIMOKU_CLOUD_BREAK", s.tf, "ราคาปิดหลุดใต้เมฆ (${p(now.cloudBottom)})", "SELL", now.cloudBottom)
            else -> null
        }
    },

    trigger("ADX_TREND_BIRTH", "ADX ตัดขึ้นเหนือ 25 (เทรนด์เกิด)", TREND_MOMENTUM, EVENT, minBars = Warmup.forSmoothed(14) + 2) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.adx(s.highs, s.lows, s.closes) ?: return@trigger null
        val before = TaIndicators.adx(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        if (!(before.adx < 25.0 && now.adx >= 25.0)) return@trigger null
        val dir = if (now.diPlus > now.diMinus) "BUY" else "SELL"
        TriggerEvent("ADX_TREND_BIRTH", s.tf, "ADX ตัดขึ้นเหนือ 25 (${"%.0f".format(now.adx)}) — เทรนด์${WakeMath.dirWord(dir)}เริ่มมีแรง", dir)
    },

    trigger("ADX_EXHAUSTION", "ADX กลับหัวจากระดับสูง (เทรนด์อ่อนแรง)", TREND_MOMENTUM, EVENT, minBars = Warmup.forSmoothed(14) + 4) { ctx ->
        val s = ctx.primary
        val a0 = TaIndicators.adx(s.highs, s.lows, s.closes) ?: return@trigger null
        val a1 = TaIndicators.adx(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        val a2 = TaIndicators.adx(s.highs.dropLast(2), s.lows.dropLast(2), s.closes.dropLast(2)) ?: return@trigger null
        // จุดยอด: เพิ่งกลับหัวที่แท่งนี้ หลังขึ้นไปสูงกว่า 40
        if (!(a1.adx >= 40.0 && a1.adx > a2.adx && a0.adx < a1.adx)) return@trigger null
        val trendDir = if (a0.diPlus > a0.diMinus) "SELL" else "BUY" // เทรนด์เดิมอ่อน → ทิศตรงข้ามน่าจับตา
        TriggerEvent("ADX_EXHAUSTION", s.tf, "ADX กลับหัวลงจาก ${"%.0f".format(a1.adx)} — เทรนด์เดิมเริ่มหมดแรง", trendDir)
    },

    trigger("TREND_ACCELERATION", "เทรนด์เร่งความเร็วผิดปกติ", TREND_MOMENTUM, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        if (s.n < 31) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val fast = WakeMath.linregSlope(s.closes.takeLast(10)) ?: return@trigger null
        val slow = WakeMath.linregSlope(s.closes.subList(s.n - 30, s.n - 10)) ?: return@trigger null
        val fastPrev = WakeMath.linregSlope(s.closes.subList(s.n - 11, s.n - 1)) ?: return@trigger null
        val accelerating = abs(fast) > 0.3 * atr && abs(fast) > 2 * abs(slow) && (slow == 0.0 || fast * slow > 0)
        val wasAccelerating = abs(fastPrev) > 0.3 * atr && abs(fastPrev) > 2 * abs(slow)
        if (!accelerating || wasAccelerating) return@trigger null
        val dir = if (fast > 0) "BUY" else "SELL"
        TriggerEvent("TREND_ACCELERATION", s.tf, "ราคาเร่งตัว${WakeMath.dirWord(dir)} ${"%.2f".format(abs(fast) / atr)}×ATR ต่อแท่ง (เร็วกว่าเดิม ${"%.1f".format(abs(fast) / maxOf(abs(slow), 1e-9))} เท่า)", dir)
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// E. โมเมนตัม / Oscillator
// ═════════════════════════════════════════════════════════════════════════════

internal val MOMENTUM_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("RSI_EXIT_EXTREME", "RSI ออกจากโซนสุดโต่ง", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val now = s.rsi() ?: return@trigger null; val prev = s.rsi(back = 1) ?: return@trigger null
        when {
            prev <= 30.0 && now > 30.0 -> TriggerEvent("RSI_EXIT_EXTREME", s.tf, "RSI ตัดขึ้นจากโซน Oversold (${"%.0f".format(now)})", "BUY")
            prev >= 70.0 && now < 70.0 -> TriggerEvent("RSI_EXIT_EXTREME", s.tf, "RSI ตัดลงจากโซน Overbought (${"%.0f".format(now)})", "SELL")
            else -> null
        }
    },

    trigger("RSI_DIVERGENCE", "RSI Divergence", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val dir = WakeMath.divergence(s, s.rsiSeries(), hidden = false) ?: return@trigger null
        TriggerEvent("RSI_DIVERGENCE", s.tf, if (dir == "BUY") "Bullish RSI divergence — ราคาทำ low ใหม่แต่ RSI ไม่ทำตาม" else "Bearish RSI divergence — ราคาทำ high ใหม่แต่ RSI ไม่ทำตาม", dir)
    },

    trigger("RSI_HIDDEN_DIVERGENCE", "RSI Hidden Divergence", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val dir = WakeMath.divergence(s, s.rsiSeries(), hidden = true) ?: return@trigger null
        TriggerEvent("RSI_HIDDEN_DIVERGENCE", s.tf, "Hidden ${if (dir == "BUY") "bullish" else "bearish"} divergence — สัญญาณเทรนด์เดิมไปต่อ", dir)
    },

    trigger("RSI_50_CROSS", "RSI ตัดเส้น 50", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val now = s.rsi() ?: return@trigger null; val prev = s.rsi(back = 1) ?: return@trigger null
        when {
            prev <= 50.0 && now > 50.0 -> TriggerEvent("RSI_50_CROSS", s.tf, "RSI ตัดขึ้นเหนือ 50 — โมเมนตัมเปลี่ยนเป็นบวก", "BUY")
            prev >= 50.0 && now < 50.0 -> TriggerEvent("RSI_50_CROSS", s.tf, "RSI ตัดลงใต้ 50 — โมเมนตัมเปลี่ยนเป็นลบ", "SELL")
            else -> null
        }
    },

    trigger("MACD_SIGNAL_CROSS", "MACD ตัดเส้น Signal", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.MACD_SET) { ctx ->
        val s = ctx.primary
        val h = s.macdHist
        val now = h.getOrNull(s.n - 1) ?: return@trigger null; val prev = h.getOrNull(s.n - 2) ?: return@trigger null
        when {
            prev <= 0 && now > 0 -> TriggerEvent("MACD_SIGNAL_CROSS", s.tf, "MACD ตัด Signal ขึ้น", "BUY")
            prev >= 0 && now < 0 -> TriggerEvent("MACD_SIGNAL_CROSS", s.tf, "MACD ตัด Signal ลง", "SELL")
            else -> null
        }
    },

    trigger("MACD_ZERO_CROSS", "MACD ตัดเส้นศูนย์", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.MACD_SET) { ctx ->
        val s = ctx.primary
        val m1 = (s.ema(12) ?: return@trigger null) - (s.ema(26) ?: return@trigger null)
        val m0 = (s.ema(12, 1) ?: return@trigger null) - (s.ema(26, 1) ?: return@trigger null)
        when {
            m0 <= 0 && m1 > 0 -> TriggerEvent("MACD_ZERO_CROSS", s.tf, "MACD line ตัดขึ้นเหนือศูนย์ — เทรนด์ระยะกลางเป็นบวก", "BUY")
            m0 >= 0 && m1 < 0 -> TriggerEvent("MACD_ZERO_CROSS", s.tf, "MACD line ตัดลงใต้ศูนย์ — เทรนด์ระยะกลางเป็นลบ", "SELL")
            else -> null
        }
    },

    trigger("MACD_HISTOGRAM_TURN", "MACD Histogram กลับทิศ", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.MACD_SET) { ctx ->
        val s = ctx.primary
        val h = s.macdHist
        val now = h.getOrNull(s.n - 1) ?: return@trigger null
        val p1 = h.getOrNull(s.n - 2) ?: return@trigger null
        val p2 = h.getOrNull(s.n - 3) ?: return@trigger null
        when {
            now < 0 && p1 < 0 && p1 < p2 && now > p1 -> TriggerEvent("MACD_HISTOGRAM_TURN", s.tf, "MACD histogram เงยหัวขึ้นจากแดนลบ", "BUY")
            now > 0 && p1 > 0 && p1 > p2 && now < p1 -> TriggerEvent("MACD_HISTOGRAM_TURN", s.tf, "MACD histogram ปักหัวลงจากแดนบวก", "SELL")
            else -> null
        }
    },

    trigger("MACD_DIVERGENCE", "MACD Divergence", OSCILLATOR_EXTREME, EVENT, minBars = Warmup.MACD_SET) { ctx ->
        val s = ctx.primary
        val dir = WakeMath.divergence(s, s.macdHist, hidden = false) ?: return@trigger null
        TriggerEvent("MACD_DIVERGENCE", s.tf, "${if (dir == "BUY") "Bullish" else "Bearish"} MACD divergence", dir)
    },

    trigger("STOCH_EXTREME_CROSS", "Stochastic ตัดกันในโซนสุดโต่ง", OSCILLATOR_EXTREME, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.stochastic(s.closes, s.highs, s.lows) ?: return@trigger null
        val before = TaIndicators.stochastic(s.closes.dropLast(1), s.highs.dropLast(1), s.lows.dropLast(1)) ?: return@trigger null
        when {
            before.k <= before.d && now.k > now.d && now.d < 20 -> TriggerEvent("STOCH_EXTREME_CROSS", s.tf, "Stochastic %K ตัด %D ขึ้นในโซน Oversold (${"%.0f".format(now.k)})", "BUY")
            before.k >= before.d && now.k < now.d && now.d > 80 -> TriggerEvent("STOCH_EXTREME_CROSS", s.tf, "Stochastic %K ตัด %D ลงในโซน Overbought (${"%.0f".format(now.k)})", "SELL")
            else -> null
        }
    },

    trigger("CCI_100_CROSS", "CCI ทะลุ ±100", OSCILLATOR_EXTREME, EVENT, minBars = 40) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.cci(s.highs, s.lows, s.closes, 20) ?: return@trigger null
        val before = TaIndicators.cci(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1), 20) ?: return@trigger null
        when {
            before <= 100 && now > 100 -> TriggerEvent("CCI_100_CROSS", s.tf, "CCI ทะลุ +100 — โมเมนตัมขาขึ้นแรง", "BUY")
            before >= -100 && now < -100 -> TriggerEvent("CCI_100_CROSS", s.tf, "CCI หลุด −100 — โมเมนตัมขาลงแรง", "SELL")
            else -> null
        }
    },

    trigger("WILLIAMS_R_EXIT", "Williams %R ออกจากโซนสุดโต่ง", OSCILLATOR_EXTREME, EVENT, minBars = 30) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.williamsR(s.highs, s.lows, s.closes) ?: return@trigger null
        val before = TaIndicators.williamsR(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1)) ?: return@trigger null
        when {
            before <= -80 && now > -80 -> TriggerEvent("WILLIAMS_R_EXIT", s.tf, "Williams %R ออกจากโซน Oversold", "BUY")
            before >= -20 && now < -20 -> TriggerEvent("WILLIAMS_R_EXIT", s.tf, "Williams %R ออกจากโซน Overbought", "SELL")
            else -> null
        }
    },

    trigger("MFI_EXTREME", "Money Flow Index เข้าโซนสุดโต่ง", OSCILLATOR_EXTREME, EVENT, minBars = 30) { ctx ->
        val s = ctx.primary
        val now = TaIndicators.mfi(s.highs, s.lows, s.closes, s.volumes) ?: return@trigger null
        val before = TaIndicators.mfi(s.highs.dropLast(1), s.lows.dropLast(1), s.closes.dropLast(1), s.volumes.dropLast(1)) ?: return@trigger null
        when {
            before < 80 && now >= 80 -> TriggerEvent("MFI_EXTREME", s.tf, "MFI เข้าโซน Overbought (${"%.0f".format(now)}) — เงินไหลเข้าหนักผิดปกติ", "SELL")
            before > 20 && now <= 20 -> TriggerEvent("MFI_EXTREME", s.tf, "MFI เข้าโซน Oversold (${"%.0f".format(now)}) — เงินไหลออกหนักผิดปกติ", "BUY")
            else -> null
        }
    },

    trigger("MOMENTUM_EXHAUSTION", "โมเมนตัมล้า (แท่งสีเดียวติดกัน + RSI สุดโต่ง)", OSCILLATOR_EXTREME, STATE, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        if (s.n < 6) return@trigger null
        val rsi = s.rsi() ?: return@trigger null
        val tail = s.bars.takeLast(5)
        when {
            tail.all { WakeMath.isBull(it) } && rsi > 75 -> TriggerEvent("MOMENTUM_EXHAUSTION", s.tf, "แท่งเขียวติดกัน 5 แท่ง RSI ${"%.0f".format(rsi)} — ขาขึ้นเริ่มล้า", "SELL")
            tail.all { WakeMath.isBear(it) } && rsi < 25 -> TriggerEvent("MOMENTUM_EXHAUSTION", s.tf, "แท่งแดงติดกัน 5 แท่ง RSI ${"%.0f".format(rsi)} — ขาลงเริ่มล้า", "BUY")
            else -> null
        }
    },

    trigger("MTF_OSCILLATOR_CONFLUENCE", "RSI ทุก TF อยู่โซนสุดโต่งพร้อมกัน", OSCILLATOR_EXTREME, STATE) { ctx ->
        val rs = listOf(ctx.m5, ctx.m15, ctx.h1).map { it.rsi() ?: return@trigger null }
        when {
            rs.all { it >= 70 } -> TriggerEvent("MTF_OSCILLATOR_CONFLUENCE", "multi", "RSI M5/M15/H1 Overbought พร้อมกัน (${rs.joinToString("/") { "%.0f".format(it) }})", "SELL")
            rs.all { it <= 30 } -> TriggerEvent("MTF_OSCILLATOR_CONFLUENCE", "multi", "RSI M5/M15/H1 Oversold พร้อมกัน (${rs.joinToString("/") { "%.0f".format(it) }})", "BUY")
            else -> null
        }
    },

    trigger("FAST_RSI_THRUST", "Fast RSI(5) พุ่งออกจากโซน", OSCILLATOR_EXTREME, EVENT, minBars = 40) { ctx ->
        // ย้ายมาจาก FAST_RSI_REVERSAL เดิม — ตัดส่วน SL/TP แบบคริปโต (±5%/10%) ทิ้ง
        // และเพิ่มฝั่ง SELL ให้สมมาตร (ตัวเดิม long-only ทำให้ระบบเอียงไป BUY)
        val s = ctx.primary
        val now = s.rsi(5) ?: return@trigger null; val prev = s.rsi(5, back = 1) ?: return@trigger null
        when {
            prev <= 35 && now > 35 -> TriggerEvent("FAST_RSI_THRUST", s.tf, "RSI(5) พุ่งขึ้นผ่าน 35 — แรงขายระยะสั้นหมด", "BUY")
            prev >= 65 && now < 65 -> TriggerEvent("FAST_RSI_THRUST", s.tf, "RSI(5) ร่วงลงผ่าน 65 — แรงซื้อระยะสั้นหมด", "SELL")
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// F. ความผันผวน
// ═════════════════════════════════════════════════════════════════════════════

/** Bollinger ของ ณ แท่ง n-1-back */
private fun bbAt(s: Series, back: Int) = TaIndicators.bollingerBands(s.closes.dropLast(back), 20, 2.0)

internal val VOLATILITY_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("VOLATILITY_SQUEEZE", "Bollinger บีบตัว รอ Breakout", VOLATILITY_SQUEEZE, STATE, minBars = Warmup.FAST_SET) { ctx ->
        val bb = bbAt(ctx.primary, 0) ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val w = (bb.upper - bb.lower) / atr
        if (w > 2.2) return@trigger null
        TriggerEvent("VOLATILITY_SQUEEZE", ctx.primaryTf, "Bollinger บีบตัวแคบ (${"%.1f".format(w)}×ATR) สะสมพลังรอ breakout")
    },

    trigger("SQUEEZE_FIRE", "Bollinger หลุดออกจาก Keltner (ระเบิด)", VOLATILITY_SQUEEZE, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        fun inside(back: Int): Boolean? {
            val bb = bbAt(s, back) ?: return null
            val mid = s.ema(20, back) ?: return null
            val atr = s.atr(back) ?: return null
            return bb.upper < mid + 1.5 * atr && bb.lower > mid - 1.5 * atr
        }
        val was = inside(1) ?: return@trigger null
        val now = inside(0) ?: return@trigger null
        if (!(was && !now)) return@trigger null
        val basis = bbAt(s, 0)?.basis ?: return@trigger null
        val dir = if ((s.last?.close ?: basis) >= basis) "BUY" else "SELL"
        TriggerEvent("SQUEEZE_FIRE", s.tf, "Squeeze ระเบิดออก (BB หลุดจาก Keltner) ทาง${WakeMath.dirWord(dir)}", dir)
    },

    trigger("BAND_WALK", "ราคาเดินเกาะขอบ Bollinger", VOLATILITY_SQUEEZE, STATE, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val outs = (0..2).map { back ->
            val bb = bbAt(s, back) ?: return@trigger null
            val c = s.bars[s.n - 1 - back].close
            when { c > bb.upper -> 1; c < bb.lower -> -1; else -> 0 }
        }
        when {
            outs.all { it == 1 } -> TriggerEvent("BAND_WALK", s.tf, "ปิดเหนือ Bollinger บน 3 แท่งติด — เทรนด์ขาขึ้นแรง", "BUY")
            outs.all { it == -1 } -> TriggerEvent("BAND_WALK", s.tf, "ปิดใต้ Bollinger ล่าง 3 แท่งติด — เทรนด์ขาลงแรง", "SELL")
            else -> null
        }
    },

    trigger("BAND_REJECTION", "แตะขอบ Bollinger แล้วถูกปฏิเสธ", VOLATILITY_SQUEEZE, EVENT, minBars = Warmup.FAST_SET) { ctx ->
        val s = ctx.primary
        val last = s.last ?: return@trigger null; val prev = s.prev ?: return@trigger null
        val bbPrev = bbAt(s, 1) ?: return@trigger null
        val bb = bbAt(s, 0) ?: return@trigger null
        when {
            prev.high >= bbPrev.upper && last.close < bb.upper && WakeMath.isBear(last) -> TriggerEvent("BAND_REJECTION", s.tf, "ราคาแตะ Bollinger บนแล้วถูกกดกลับ", "SELL", bb.upper)
            prev.low <= bbPrev.lower && last.close > bb.lower && WakeMath.isBull(last) -> TriggerEvent("BAND_REJECTION", s.tf, "ราคาแตะ Bollinger ล่างแล้วเด้งกลับ", "BUY", bb.lower)
            else -> null
        }
    },

    trigger("ATR_EXPANSION", "ความผันผวนขยายตัวแรง", VOLATILITY_SQUEEZE, EVENT, minBars = 80) { ctx ->
        val s = ctx.primary
        val now = s.atr() ?: return@trigger null; val prev = s.atr(1) ?: return@trigger null
        val base = (2..51).mapNotNull { s.atr(it) }.takeIf { it.size >= 40 }?.average() ?: return@trigger null
        if (!(now > 1.5 * base && prev <= 1.5 * base)) return@trigger null
        TriggerEvent("ATR_EXPANSION", s.tf, "ATR ขยายเป็น ${"%.1f".format(now / base)} เท่าของค่าเฉลี่ย — ตลาดเริ่มผันผวนแรง")
    },

    trigger("ATR_CONTRACTION", "ความผันผวนต่ำสุดในรอบ 100 แท่ง", VOLATILITY_SQUEEZE, STATE, minBars = 120) { ctx ->
        val s = ctx.primary
        val now = s.atr() ?: return@trigger null
        val hist = (1..100).mapNotNull { s.atr(it) }.takeIf { it.size >= 90 } ?: return@trigger null
        if (now > hist.min()) return@trigger null
        TriggerEvent("ATR_CONTRACTION", s.tf, "ATR ต่ำสุดในรอบ 100 แท่ง — ตลาดเงียบผิดปกติ มักตามด้วยการเคลื่อนไหวแรง")
    },

    trigger("NR7_INSIDE_BAR", "แท่งแคบสุดในรอบ 7 / Inside bar", VOLATILITY_SQUEEZE, EVENT, minBars = 10) { ctx ->
        val s = ctx.primary
        if (s.n < 8) return@trigger null
        val last = s.bars[s.n - 1]; val prev = s.bars[s.n - 2]
        val nr7 = s.bars.takeLast(7).all { WakeMath.range(it) >= WakeMath.range(last) }
        val inside = last.high < prev.high && last.low > prev.low
        if (!nr7 && !inside) return@trigger null
        TriggerEvent("NR7_INSIDE_BAR", s.tf, if (nr7 && inside) "แท่งแคบสุดในรอบ 7 และเป็น inside bar — บีบตัวรอหลุด" else if (nr7) "แท่งแคบสุดในรอบ 7 แท่ง (NR7)" else "เกิด inside bar", "NEUTRAL")
    },

    trigger("VOLATILITY_REGIME_CHANGE", "สภาวะความผันผวนเปลี่ยน", VOLATILITY_SQUEEZE, EVENT, minBars = 60) { ctx ->
        val s = ctx.primary
        val now = s.atr() ?: return@trigger null; val prev = s.atr(1) ?: return@trigger null
        val old = s.atr(20) ?: return@trigger null
        if (old <= 0) return@trigger null
        val r = now / old; val rp = prev / old
        when {
            r >= 2.0 && rp < 2.0 -> TriggerEvent("VOLATILITY_REGIME_CHANGE", s.tf, "ความผันผวนเพิ่มเป็น 2 เท่าของ 20 แท่งก่อน — เข้าสู่ช่วงผันผวนสูง")
            r <= 0.5 && rp > 0.5 -> TriggerEvent("VOLATILITY_REGIME_CHANGE", s.tf, "ความผันผวนลดเหลือครึ่งหนึ่งของ 20 แท่งก่อน — เข้าสู่ช่วงเงียบ")
            else -> null
        }
    }
)

/** ใช้ใน MTF/Composite — ใส่ไว้ที่นี่เพราะใช้ primitive ของ Series ร่วมกัน */
internal fun structureTrend(s: Series): Int? = s.structure?.trend?.lastOrNull()

internal fun tfName(tf: String) = tfLabel(tf)
