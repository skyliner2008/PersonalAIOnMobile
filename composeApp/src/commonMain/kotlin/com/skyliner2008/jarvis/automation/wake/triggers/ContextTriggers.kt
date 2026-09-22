package com.skyliner2008.jarvis.automation.wake.triggers

import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.ANOMALY
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.FUNDAMENTAL
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.INSTITUTIONAL
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.INTERMARKET
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.MTF_DIVERGENCE
import com.skyliner2008.jarvis.automation.wake.EvidenceGroup.SESSION
import com.skyliner2008.jarvis.automation.wake.Series
import com.skyliner2008.jarvis.automation.wake.TriggerEvent
import com.skyliner2008.jarvis.automation.wake.TriggerKind.EVENT
import com.skyliner2008.jarvis.automation.wake.TriggerKind.STATE
import com.skyliner2008.jarvis.automation.wake.WakeContext
import com.skyliner2008.jarvis.automation.wake.WakeTrigger
import com.skyliner2008.jarvis.automation.wake.p
import com.skyliner2008.jarvis.automation.wake.tfLabel
import com.skyliner2008.jarvis.automation.wake.trigger
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.DAY
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.hourUtc
import com.skyliner2008.jarvis.automation.wake.triggers.WakeMath.minuteUtc
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

// ═════════════════════════════════════════════════════════════════════════════
// J. ความสอดคล้องหลาย TF  (M5 = คัดกรองการหลอก, H1/H4 = บริบท)
// ═════════════════════════════════════════════════════════════════════════════

internal val MTF_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("ALL_TF_ALIGNED", "H4 ถึง M5 เทรนด์เดียวกัน (เพิ่งเกิด)", MTF_DIVERGENCE, EVENT) { ctx ->
        val ts = listOf("H4", "H1", "M15", "M5").map { ctx.trendOf(it) ?: return@trigger null }
        val now = when { ts.all { it == "UP" } -> "UP"; ts.all { it == "DOWN" } -> "DOWN"; else -> "MIXED" }
        val before = ctx.memory["mtf_alignment"] ?: return@trigger null
        if (now == "MIXED" || now == before) return@trigger null
        TriggerEvent("ALL_TF_ALIGNED", "multi", "ทุก TF (H4/H1/M15/M5) เรียงเป็น${if (now == "UP") "ขาขึ้น" else "ขาลง"}พร้อมกัน", if (now == "UP") "BUY" else "SELL")
    },

    trigger("M5_CONFIRM_DIVERGENCE", "M5 ขัดแย้งกับ M15 (เสี่ยงสัญญาณหลอก)", MTF_DIVERGENCE, STATE) { ctx ->
        val m5 = ctx.trendOf("M5") ?: return@trigger null
        val m15 = ctx.trendOf("M15") ?: return@trigger null
        if (m5 == "RANGE" || m15 == "RANGE" || m5 == m15) return@trigger null
        TriggerEvent("M5_CONFIRM_DIVERGENCE", "5m", "M5 เป็น $m5 สวนทางกับ M15 ที่เป็น $m15 — ระวังสัญญาณหลอก")
    },

    trigger("HTF_CONFLICT", "H4 กับ H1 ขัดแย้งกัน", MTF_DIVERGENCE, STATE) { ctx ->
        val h4 = ctx.trendOf("H4") ?: return@trigger null
        val h1 = ctx.trendOf("H1") ?: return@trigger null
        if (h4 == "RANGE" || h1 == "RANGE" || h4 == h1) return@trigger null
        TriggerEvent("HTF_CONFLICT", "4h", "H4 เป็น $h4 แต่ H1 เป็น $h1 — บริบทใหญ่ยังไม่ชัด")
    },

    trigger("LTF_PULLBACK_END", "M5 กลับตัวตามทิศ H1 (จบการย่อ)", MTF_DIVERGENCE, EVENT) { ctx ->
        val h1 = ctx.trendOf("H1") ?: return@trigger null
        val st = ctx.m5.structure ?: return@trigger null
        val i = ctx.m5.n - 1
        when {
            h1 == "UP" && st.chochUp[i] -> TriggerEvent("LTF_PULLBACK_END", "5m", "M5 เกิด CHoCH ขาขึ้นในเทรนด์ขาขึ้นของ H1 — การย่อตัวน่าจะจบ", "BUY")
            h1 == "DOWN" && st.chochDn[i] -> TriggerEvent("LTF_PULLBACK_END", "5m", "M5 เกิด CHoCH ขาลงในเทรนด์ขาลงของ H1 — การดีดตัวน่าจะจบ", "SELL")
            else -> null
        }
    },

    trigger("TF_CONTINUATION", "BOS ตามทิศ H4", MTF_DIVERGENCE, EVENT) { ctx ->
        val h4 = ctx.trendOf("H4") ?: return@trigger null
        val s = ctx.primary
        val st = s.structure ?: return@trigger null
        val i = s.n - 1
        when {
            h4 == "UP" && st.bosUp[i] -> TriggerEvent("TF_CONTINUATION", s.tf, "${tfLabel(s.tf)} เกิด BOS ขาขึ้นตามทิศเทรนด์ H4", "BUY")
            h4 == "DOWN" && st.bosDn[i] -> TriggerEvent("TF_CONTINUATION", s.tf, "${tfLabel(s.tf)} เกิด BOS ขาลงตามทิศเทรนด์ H4", "SELL")
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// K. เวลา / Session  (UTC)
// ═════════════════════════════════════════════════════════════════════════════

private val LONDON: TimeZone? = runCatching { TimeZone.of("Europe/London") }.getOrNull()
private val NEW_YORK: TimeZone? = runCatching { TimeZone.of("America/New_York") }.getOrNull()

/**
 * แท่ง M15 ล่าสุดเปิดตรงเวลาท้องถิ่นของตลาดนั้นพอดี (= เพิ่งเข้าช่วงเวลานั้น) — ข้ามเสาร์-อาทิตย์
 *
 * ใช้เวลาท้องถิ่น London / New York เพื่อรองรับ Daylight Saving
 * (เดิมใช้ UTC ตายตัวตามเวลาฤดูร้อน — ช่วงฤดูหนาว session open / killzone คลาดไป 1 ชั่วโมง)
 * [utcFallback] ใช้เมื่อเครื่องไม่มีฐานข้อมูล timezone
 */
private fun justOpened(ctx: WakeContext, zone: TimeZone?, hour: Int, minute: Int, utcFallback: Pair<Int, Int>): Boolean {
    val last = ctx.m15.last ?: return false
    if (zone == null) return hourUtc(last.timestamp) == utcFallback.first && minuteUtc(last.timestamp) == utcFallback.second
    val t = Instant.fromEpochMilliseconds(last.timestamp).toLocalDateTime(zone)
    if (t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY) return false
    return t.hour == hour && t.minute == minute
}

/**
 * ราคาปิดตัดผ่านราคาเปิดของช่วงเวลานั้น
 *
 * ราคาเปิด = แท่งแรกของช่วง จากแท่ง H1 และแท่ง TF หลักรวมกัน — เดิมใช้เฉพาะ H1
 * ต้นวันใหม่ที่ยังไม่มีแท่ง H1 ปิดจึงหาราคาเปิดวันไม่เจอ (trigger เงียบช่วงที่สำคัญที่สุด)
 */
private fun openCross(ctx: WakeContext, id: String, label: String, keyOf: (Long) -> Long): TriggerEvent? {
    val s = ctx.primary
    val last = s.last ?: return null; val prev = s.prev ?: return null
    val key = keyOf(last.timestamp)
    val bars = (ctx.h1.bars + s.bars).sortedBy { it.timestamp }
    // ต้องมีข้อมูลก่อนเริ่มช่วง ไม่งั้นแท่งแรกที่เห็นอาจไม่ใช่แท่งเปิดจริง
    if (bars.isEmpty() || keyOf(bars.first().timestamp) >= key) return null
    val open = bars.firstOrNull { keyOf(it.timestamp) == key }?.open ?: return null
    return when {
        prev.close <= open && last.close > open -> TriggerEvent(id, s.tf, "ราคาตัดขึ้นเหนือราคาเปิด$label (${p(open)})", "BUY", open)
        prev.close >= open && last.close < open -> TriggerEvent(id, s.tf, "ราคาตัดลงใต้ราคาเปิด$label (${p(open)})", "SELL", open)
        else -> null
    }
}

internal val SESSION_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("SESSION_OPEN", "Session เปิด (London / New York)", SESSION, EVENT, minBars = 10) { ctx ->
        when {
            justOpened(ctx, LONDON, 8, 0, 7 to 0) -> TriggerEvent("SESSION_OPEN", "15m", "ตลาด London เปิด (08:00 เวลาลอนดอน) — สภาพคล่องและความผันผวนเพิ่มขึ้น")
            justOpened(ctx, NEW_YORK, 9, 30, 13 to 30) -> TriggerEvent("SESSION_OPEN", "15m", "ตลาดหุ้น New York เปิด (09:30 เวลานิวยอร์ก) — สภาพคล่องและความผันผวนเพิ่มขึ้น")
            else -> null
        }
    },

    trigger("ASIA_RANGE_SET", "กรอบ Asia ก่อตัวเสร็จ", SESSION, STATE, minBars = 40) { ctx ->
        val last = ctx.m15.last ?: return@trigger null
        if (hourUtc(last.timestamp) !in 7..11) return@trigger null
        val day = last.timestamp.floorDiv(DAY)
        val asia = ctx.m15.bars.filter { it.timestamp.floorDiv(DAY) == day && hourUtc(it.timestamp) in 0..6 }
        if (asia.size < 8) return@trigger null
        val hi = asia.maxOf { it.high }; val lo = asia.minOf { it.low }
        TriggerEvent("ASIA_RANGE_SET", "15m", "กรอบ Asia วันนี้ ${p(lo)}–${p(hi)} — จุดอ้างอิงสำหรับการ breakout/sweep ช่วง London", "NEUTRAL")
    },

    trigger("KILLZONE_ENTRY", "เข้า Killzone (ICT)", SESSION, EVENT, minBars = 10) { ctx ->
        // ICT killzone นิยามตามเวลานิวยอร์ก: London 02:00, New York 07:00
        when {
            justOpened(ctx, NEW_YORK, 2, 0, 6 to 0) -> TriggerEvent("KILLZONE_ENTRY", "15m", "เข้า London killzone (02:00 เวลานิวยอร์ก) — ช่วงที่มักเกิด sweep และตั้งทิศทางของวัน")
            justOpened(ctx, NEW_YORK, 7, 0, 11 to 0) -> TriggerEvent("KILLZONE_ENTRY", "15m", "เข้า New York killzone (07:00 เวลานิวยอร์ก) — ช่วงที่มักเกิดการกลับตัวหรือไปต่อของ London")
            else -> null
        }
    },

    trigger("DAILY_OPEN_CROSS", "ราคาตัดราคาเปิดวัน", SESSION, EVENT) { ctx ->
        val shift = WakeMath.sessionShiftMs(ctx.symbol)
        openCross(ctx, "DAILY_OPEN_CROSS", "วัน") { WakeMath.dayKey(it, shift) }
    },

    trigger("WEEKLY_OPEN_CROSS", "ราคาตัดราคาเปิดสัปดาห์", SESSION, EVENT) { ctx ->
        val shift = WakeMath.sessionShiftMs(ctx.symbol)
        openCross(ctx, "WEEKLY_OPEN_CROSS", "สัปดาห์") { WakeMath.weekKey(it, shift) }
    },

    trigger("MONTHLY_OPEN_CROSS", "ราคาตัดราคาเปิดเดือน", SESSION, EVENT) { ctx ->
        openCross(ctx, "MONTHLY_OPEN_CROSS", "เดือน") { WakeMath.monthKey(it) }
    },

    trigger("WEEK_BOUNDARY", "ช่วงปิดสัปดาห์ / เปิดสัปดาห์", SESSION, STATE, minBars = 1) { ctx ->
        if (ctx.isCrypto) return@trigger null // คริปโตเทรด 24/7 ไม่มีขอบสัปดาห์
        val now = ctx.nowMs
        val dow = ((now.floorDiv(DAY) + 3) % 7).toInt() // 0 = จันทร์
        val h = hourUtc(now)
        when {
            dow == 4 && h >= 18 -> TriggerEvent("WEEK_BOUNDARY", "1h", "ใกล้ปิดตลาดวันศุกร์ — สภาพคล่องลด ระวังการปิดสถานะก่อนสุดสัปดาห์")
            // FX/ทองเปิดสัปดาห์คืนวันอาทิตย์ ~21:00–22:00 UTC
            (dow == 6 && h >= 21) || (dow == 0 && h < 4) ->
                TriggerEvent("WEEK_BOUNDARY", "1h", "ต้นสัปดาห์ — ระวัง gap และสภาพคล่องบางช่วงเปิดตลาด")
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// L. Engine รวม / สภาวะตลาด
// ═════════════════════════════════════════════════════════════════════════════

internal val COMPOSITE_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("INSTITUTIONAL_SHIFT", "Veyra Institutional Shift", INSTITUTIONAL, STATE, minBars = 70) { ctx ->
        val c = ctx.primary.bars
        if (c.size < 70) return@trigger null
        val v = com.skyliner2008.jarvis.automation.strategy.VeyraShiftEngine.evaluate(c) ?: return@trigger null
        if (v.dominantScore < 66.0 || v.dominantDir == 0) return@trigger null
        TriggerEvent(
            "INSTITUTIONAL_SHIFT", ctx.primaryTf,
            "Veyra Shift ${"%.0f".format(v.dominantScore)}/100 (${v.auctionState}) — ${v.reasonTh}",
            if (v.dominantDir == 1) "BUY" else "SELL"
        )
    },

    trigger("DEEP_SCORE_CROSS", "คะแนน Deep Analysis ตัดเกณฑ์", INSTITUTIONAL, EVENT, minBars = 1) { ctx ->
        val now = ctx.deepScore ?: return@trigger null
        val before = ctx.memory["deep_score"]?.toIntOrNull() ?: return@trigger null
        when {
            before < 70 && now >= 70 -> TriggerEvent("DEEP_SCORE_CROSS", ctx.primaryTf, "คะแนน Deep Analysis ขึ้นเป็น $now (จาก $before) — confluence ฝั่งบวกสูง", "BUY")
            before > 30 && now <= 30 -> TriggerEvent("DEEP_SCORE_CROSS", ctx.primaryTf, "คะแนน Deep Analysis ลงเป็น $now (จาก $before) — confluence ฝั่งลบสูง", "SELL")
            else -> null
        }
    },

    trigger("REGIME_CHANGE", "ตลาดเปลี่ยนสภาวะ (เทรนด์ ↔ ไซด์เวย์)", INSTITUTIONAL, EVENT, minBars = 80) { ctx ->
        val s = ctx.primary
        fun regime(back: Int): String? {
            val a = TaIndicators.adx(s.highs.dropLast(back), s.lows.dropLast(back), s.closes.dropLast(back))?.adx ?: return null
            return when { a >= 25 -> "TREND"; a < 20 -> "RANGE"; else -> "MID" }
        }
        val now = regime(0) ?: return@trigger null
        val before = regime(5) ?: return@trigger null
        if (now == before || now == "MID" || before == "MID") return@trigger null
        TriggerEvent("REGIME_CHANGE", s.tf, if (now == "TREND") "ตลาดเปลี่ยนจากไซด์เวย์เป็นเทรนด์ (ADX ≥ 25)" else "ตลาดเปลี่ยนจากเทรนด์เป็นไซด์เวย์ (ADX < 20)")
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// M. ตลาดที่เกี่ยวข้อง (Intermarket)  — แท่ง H1
// ═════════════════════════════════════════════════════════════════════════════

/**
 * ทิศทางที่ "ดอลลาร์แข็ง" มักส่งผลกับสินทรัพย์นี้
 * ทอง/เงิน/EURUSD/GBPUSD → ลง, USDJPY/USDCHF/USDCAD → ขึ้น
 */
private fun usdStrengthEffect(symbol: String): String? {
    val s = symbol.uppercase().replace("/", "")
    return when {
        s.contains("XAU") || s.contains("XAG") || s.contains("GOLD") || s.contains("SILVER") -> "SELL"
        s.length == 6 && s.endsWith("USD") -> "SELL"
        s.length == 6 && s.startsWith("USD") -> "BUY"
        else -> null
    }
}

/**
 * แท่ง H1 ล่าสุดของตลาดที่เกี่ยวข้องวิ่ง ≥ 1.5 ATR
 * ต้องเป็นแท่งที่เพิ่งปิด (ภายใน 2 ชม.) — ข้อมูลที่ค้าง (feed ล่าช้า/ตลาดปิด) ไม่นับเป็นเหตุการณ์ใหม่
 */
private fun bigMove(s: Series, nowMs: Long): Double? {
    val last = s.last ?: return null
    if (nowMs - (last.timestamp + WakeMath.HOUR) > 2 * WakeMath.HOUR) return null
    val atr = s.atr()?.takeIf { it > 0 } ?: return null
    val move = last.close - last.open
    return if (abs(move) >= 1.5 * atr) move / atr else null
}

private fun flip(d: String) = when (d) { "BUY" -> "SELL"; "SELL" -> "BUY"; else -> d }

internal val INTERMARKET_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("DXY_MOVE", "ดอลลาร์ (DXY) ขยับแรง", INTERMARKET, EVENT, minBars = 1) { ctx ->
        val effect = usdStrengthEffect(ctx.symbol) ?: return@trigger null
        val dxy = ctx.inter("DXY") ?: return@trigger null
        val m = bigMove(dxy, ctx.nowMs) ?: return@trigger null
        val dir = if (m > 0) effect else flip(effect)
        TriggerEvent("DXY_MOVE", "1h", "DXY ${if (m > 0) "พุ่ง" else "ร่วง"} ${"%.1f".format(abs(m))}×ATR ในแท่ง H1 ล่าสุด — มักกดดัน ${ctx.symbol} ทาง${WakeMath.dirWord(dir)}", dir)
    },

    trigger("YIELD_SPIKE", "ผลตอบแทนพันธบัตร US10Y ขยับแรง", INTERMARKET, EVENT, minBars = 1) { ctx ->
        val effect = usdStrengthEffect(ctx.symbol) ?: return@trigger null
        val y = ctx.inter("US10Y") ?: return@trigger null
        val m = bigMove(y, ctx.nowMs) ?: return@trigger null
        val dir = if (m > 0) effect else flip(effect)
        TriggerEvent("YIELD_SPIKE", "1h", "US10Y ${if (m > 0) "พุ่ง" else "ร่วง"} ${"%.1f".format(abs(m))}×ATR — ต้นทุนการถือ${if (m > 0) "สูงขึ้น" else "ลดลง"}", dir)
    },

    trigger("CORRELATION_BREAK", "ความสัมพันธ์กับดอลลาร์ผิดปกติ", INTERMARKET, STATE, minBars = 1) { ctx ->
        val effect = usdStrengthEffect(ctx.symbol) ?: return@trigger null
        val dxy = ctx.inter("DXY") ?: return@trigger null
        if (dxy.n < 22 || ctx.h1.n < 22) return@trigger null
        // จับคู่ตามเวลาแท่ง — เดิมเอา 21 แท่งท้ายของแต่ละฝั่งมาเทียบกันตรงๆ ถ้าชั่วโมงเทรดหรือ
        // ความสดของข้อมูลต่างกัน (DXY ของ TVC หยุดคนละช่วงกับทอง) แท่งจะเหลื่อมกันและได้ค่า corr ปลอม
        val dxyAt = dxy.bars.associate { it.timestamp to it.close }
        val paired = ctx.h1.bars.filter { it.timestamp in dxyAt }.takeLast(21)
        if (paired.size < 21) return@trigger null
        if (ctx.h1.bars.last().timestamp - paired.last().timestamp > 3 * WakeMath.HOUR) return@trigger null
        val a = WakeMath.returns(paired.map { it.close })
        val b = WakeMath.returns(paired.map { dxyAt.getValue(it.timestamp) })
        val corr = WakeMath.correlation(a, b) ?: return@trigger null
        // ปกติ "ดอลลาร์แข็ง → SELL" คือ corr ติดลบ; ถ้าเป็นบวกชัด = ผิดปกติ (และกลับกัน)
        val abnormal = if (effect == "SELL") corr > 0.5 else corr < -0.5
        if (!abnormal) return@trigger null
        TriggerEvent("CORRELATION_BREAK", "1h", "${ctx.symbol} กับ DXY เคลื่อนไหวผิดปกติ (corr ${"%.2f".format(corr)} ใน 20 ชม.) — มีแรงอื่นนอกจากดอลลาร์")
    },

    trigger("CRYPTO_BROAD_MOVE", "ตลาดคริปโตทั้งกระดานขยับ", INTERMARKET, EVENT, minBars = 1) { ctx ->
        if (!ctx.isCrypto) return@trigger null
        val isBtc = ctx.symbol.uppercase().startsWith("BTC")
        val ref = (if (isBtc) ctx.inter("BTC.D") else ctx.inter("BTC")) ?: return@trigger null
        val m = bigMove(ref, ctx.nowMs) ?: return@trigger null
        if (isBtc) {
            TriggerEvent("CRYPTO_BROAD_MOVE", "1h", "BTC Dominance ${if (m > 0) "พุ่ง" else "ร่วง"} ${"%.1f".format(abs(m))}×ATR — เงิน${if (m > 0) "ไหลเข้า BTC ออกจาก altcoin" else "ไหลจาก BTC ไป altcoin"}", "NEUTRAL")
        } else {
            val dir = if (m > 0) "BUY" else "SELL"
            TriggerEvent("CRYPTO_BROAD_MOVE", "1h", "BTC ${if (m > 0) "พุ่ง" else "ร่วง"} ${"%.1f".format(abs(m))}×ATR — altcoin มักวิ่งตาม", dir)
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// N. ข่าว / ปัจจัยพื้นฐาน
// ═════════════════════════════════════════════════════════════════════════════

/** สกุลเงินที่เกี่ยวข้องกับสินทรัพย์ (ข่าวของสกุลเหล่านี้มีผล) */
private fun relevantCurrencies(symbol: String): Set<String> {
    val s = symbol.uppercase().replace("/", "")
    return when {
        s.contains("XAU") || s.contains("XAG") || s.contains("GOLD") -> setOf("USD")
        s.length == 6 && s.all { it.isLetter() } -> setOf(s.take(3), s.takeLast(3))
        s.endsWith("USDT") || s.startsWith("BTC") || s.startsWith("ETH") -> setOf("USD")
        else -> setOf("USD")
    }
}

internal val FUNDAMENTAL_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("HIGH_IMPACT_NEWS_SOON", "ข่าวแรงภายใน 30 นาที", FUNDAMENTAL, EVENT, minBars = 1) { ctx ->
        val cur = relevantCurrencies(ctx.symbol)
        val nowS = ctx.nowMs / 1000
        val warned = ctx.memory["news_warned"]?.split(",")?.toSet().orEmpty()
        val ev = ctx.macroEvents.firstOrNull {
            it.isHighImpact && it.country.uppercase() in cur &&
                it.epochSeconds in nowS..(nowS + 30 * 60) && it.epochSeconds.toString() !in warned
        } ?: return@trigger null
        val mins = (ev.epochSeconds - nowS) / 60
        TriggerEvent("HIGH_IMPACT_NEWS_SOON", "event", "ข่าวแรง ${ev.country} \"${ev.title}\" จะออกในอีก $mins นาที (คาด ${ev.forecast.ifBlank { "-" }} / ก่อนหน้า ${ev.previous.ifBlank { "-" }})")
    },

    trigger("POST_NEWS_SPIKE", "ผันผวนแรงหลังข่าวออก", FUNDAMENTAL, EVENT, minBars = 20) { ctx ->
        val cur = relevantCurrencies(ctx.symbol)
        val nowS = ctx.nowMs / 1000
        val ev = ctx.macroEvents.firstOrNull {
            it.isHighImpact && it.country.uppercase() in cur && it.epochSeconds in (nowS - 30 * 60)..nowS
        } ?: return@trigger null
        val last = ctx.primary.last ?: return@trigger null
        // แท่งต้องปิด "หลัง" ข่าวออก — แท่ง TF ใหญ่ (H1/H4) ที่ปิดก่อนข่าวไม่ใช่ผลของข่าว
        if (last.timestamp + TaIndicators.timeframeMillis(ctx.primaryTf) < ev.epochSeconds * 1000) return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        if (WakeMath.range(last) < 2 * atr) return@trigger null
        val dir = if (last.close > last.open) "BUY" else "SELL"
        TriggerEvent("POST_NEWS_SPIKE", ctx.primaryTf, "หลังข่าว \"${ev.title}\" ราคา${if (dir == "BUY") "พุ่ง" else "ร่วง"} ${"%.1f".format(WakeMath.range(last) / atr)}×ATR", dir)
    },

    trigger("FEAR_GREED_EXTREME", "Fear & Greed สุดขั้ว", FUNDAMENTAL, STATE, minBars = 1) { ctx ->
        if (!ctx.isCrypto) return@trigger null
        val v = ctx.fearGreed ?: return@trigger null
        when {
            v <= 20 -> TriggerEvent("FEAR_GREED_EXTREME", "1D", "Fear & Greed = $v (Extreme Fear) — ตลาดกลัวสุดขีด มักใกล้จุดกลับตัว", "BUY")
            v >= 80 -> TriggerEvent("FEAR_GREED_EXTREME", "1D", "Fear & Greed = $v (Extreme Greed) — ตลาดโลภสุดขีด ระวังการย่อ", "SELL")
            else -> null
        }
    }
)

// ═════════════════════════════════════════════════════════════════════════════
// O. ความผิดปกติ
// ═════════════════════════════════════════════════════════════════════════════

internal val ANOMALY_TRIGGERS: List<WakeTrigger> = listOf(
    trigger("PRICE_ANOMALY", "แท่งเดียววิ่งผิดปกติ (>4 ATR)", ANOMALY, EVENT, minBars = 20) { ctx ->
        val last = ctx.primary.last ?: return@trigger null
        val atr = ctx.atr.takeIf { it > 0 } ?: return@trigger null
        val r = WakeMath.range(last)
        if (r < 4 * atr) return@trigger null
        TriggerEvent("PRICE_ANOMALY", ctx.primaryTf, "แท่งล่าสุดวิ่ง ${"%.1f".format(r / atr)}×ATR — ผิดปกติรุนแรง (ข่าว/สภาพคล่องหาย/ข้อมูลผิดพลาด)")
    },

    trigger("SPREAD_WIDENING", "สภาพคล่องบางผิดปกติ (แท่ง M1 กว้าง)", ANOMALY, EVENT, minBars = 60) { ctx ->
        val m1 = ctx.m1
        if (m1.n < 61) return@trigger null
        val ranges = m1.bars.takeLast(61).dropLast(1).map { WakeMath.range(it) }.sorted()
        val median = ranges[ranges.size / 2].takeIf { it > 0 } ?: return@trigger null
        val last = m1.last ?: return@trigger null
        val r = WakeMath.range(last) / median
        if (r < 4.0) return@trigger null
        TriggerEvent("SPREAD_WIDENING", "1m", "แท่ง M1 กว้าง ${"%.1f".format(r)} เท่าของค่ากลาง 60 นาที — สภาพคล่องบาง ระวัง slippage")
    }
)

