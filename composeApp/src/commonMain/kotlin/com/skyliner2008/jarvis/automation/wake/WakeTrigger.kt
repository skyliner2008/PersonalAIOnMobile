package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.automation.smc.MarketContextDigest
import com.skyliner2008.jarvis.automation.smc.UnifiedSmcSignals
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import com.skyliner2008.jarvis.tools.trading.TradingApiService

/**
 * WakeTrigger — "นาฬิกาปลุก AI"
 *
 * [หลักการของระบบนี้]
 * AI นั่งเฝ้ากราฟตลอดเวลาไม่ได้เพราะเปลืองโทเคน ระบบนี้จึงทำหน้าที่เป็นอินดิเคเตอร์
 * ที่คอยตรวจจับ "เหตุการณ์สำคัญ" แล้วปลุก AI ให้ตื่นมาดู [MarketContextDigest] (ภาพ 5TF)
 *
 * **Trigger ไม่ใช่เทรดเดอร์** — หน้าที่มีอย่างเดียวคือบอกว่า "เกิดอะไรขึ้น"
 * ห้ามคำนวณ Entry/SL/TP/RR หรือสรุปว่าควรซื้อควรขาย เพราะ:
 *  - AI คือคนวิเคราะห์ ถ้าป้อนข้อสรุปไปก่อนจะเป็นการ anchor ให้ AI เห็นด้วยกับอินดิเคเตอร์
 *  - ระดับราคาที่ trigger คำนวณเองมักผิดสเกล (เช่น พารามิเตอร์คริปโตใช้กับทอง)
 *  - ระบบเรียนรู้ต้องวัด "ปลุกแล้วคุ้มไหม" ไม่ใช่ "แผนเทรดสมมติชนะไหม"
 *
 * ระบบนี้แยกจากระบบแจ้งเตือน Signal (strategies / backtest / UnifiedSMC) โดยสิ้นเชิง
 * ใช้ร่วมกันเฉพาะชั้นข้อมูล: OHLCV store, TaIndicators, MarketContextDigest
 *
 * เพิ่มปัจจัยใหม่ = เขียน trigger เดียวแล้วใส่ใน [WakeTriggerRegistry] → ได้สถิติรายตัวอัตโนมัติ
 * แล้วปล่อยให้ระบบเรียนรู้คัดเองว่าตัวไหนควรอยู่ต่อ
 */
interface WakeTrigger {
    /** รหัสเฉพาะ ใช้เป็น key ของสถิติการเรียนรู้ — **ห้ามเปลี่ยนหลังเริ่มเก็บข้อมูลแล้ว** */
    val id: String

    /** ชื่อที่มนุษย์อ่าน */
    val name: String

    /**
     * กลุ่มหลักฐาน — trigger ที่วัด "เรื่องเดียวกัน" ต้องอยู่กลุ่มเดียวกัน
     * ใช้กันการนับหลักฐานซ้ำ (squeeze 2 ตัว / oscillator 3 ตัวมักยิงพร้อมกัน)
     */
    val evidenceGroup: EvidenceGroup

    /** เหตุการณ์ที่เกิดครั้งเดียว vs สภาวะที่เป็นจริงต่อเนื่องหลายแท่ง */
    val kind: TriggerKind

    /** แท่งขั้นต่ำของ TF ที่ trigger ใช้ จึงจะประเมินได้ */
    val minBars: Int get() = 60

    /** @return null = ไม่เกิด */
    fun detect(ctx: WakeContext): TriggerEvent?
}

/**
 * EVENT = เกิดแล้วจบ (EMA ตัดกัน, sweep, BOS) — **ปลุก AI ได้**
 * STATE = เป็นจริงต่อเนื่อง (RSI อยู่ในโซน, BB บีบตัว) — **เป็นบริบท** ไม่ปลุกซ้ำทุกแท่ง
 *         (ระบบบันทึกเข้าการเรียนรู้เฉพาะตอนเปลี่ยนจากเท็จเป็นจริง)
 */
enum class TriggerKind { EVENT, STATE }

enum class EvidenceGroup(val labelTh: String) {
    STRUCTURE("โครงสร้างตลาด"),
    KEY_LEVEL("จุดสำคัญ/โซนราคา"),
    LIQUIDITY_SWEEP("สภาพคล่อง"),
    TREND_MOMENTUM("เทรนด์/เส้นค่าเฉลี่ย"),
    OSCILLATOR_EXTREME("โมเมนตัม/Oscillator"),
    VOLATILITY_SQUEEZE("ความผันผวน"),
    VOLUME("ปริมาณ/แรงซื้อขาย"),
    PRICE_ACTION("Price Action/แท่งเทียน"),
    PATTERN("รูปแบบกราฟ"),
    MTF_DIVERGENCE("ความสอดคล้องหลาย TF"),
    SESSION("เวลา/Session"),
    INSTITUTIONAL("Engine รวม/สภาวะตลาด"),
    INTERMARKET("ตลาดที่เกี่ยวข้อง"),
    FUNDAMENTAL("ข่าว/ปัจจัยพื้นฐาน"),
    ANOMALY("ความผิดปกติ")
}

/**
 * เหตุการณ์ที่ตรวจพบ — **ไม่มีระดับราคาเข้า/ออก ไม่มีข้อสรุป**
 *
 * `direction` เป็นเพียงข้อสังเกตว่าเหตุการณ์นี้มักสัมพันธ์กับทิศทางไหน ไม่ใช่คำแนะนำ
 * `referencePrice` คือข้อเท็จจริง (เช่น ระดับที่ถูก sweep) ไม่ใช่จุดเข้า
 */
data class TriggerEvent(
    val triggerId: String,
    val tf: String,
    val what: String,
    val direction: String = "NEUTRAL",
    val referencePrice: Double? = null
)

// ═════════════════════════════════════════════════════════════════════════════
// Series — แคชการคำนวณต่อ TF (กัน 110 trigger คำนวณอินดิเคเตอร์ซ้ำกันเอง)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * แท่งที่ **ปิดแล้ว** ของ TF หนึ่ง พร้อมอินดิเคเตอร์ที่คำนวณครั้งเดียวแล้วแชร์ให้ทุก trigger
 *
 * ใช้เฉพาะแท่งปิด — แท่งที่ยังก่อตัวทำให้ค่าขยับทุกครั้งที่เรียก (repainting)
 * ยกเว้น [live] ที่ให้ trigger ที่ต้องการราคาสด (เช่น แตะระดับ) ใช้ได้
 */
class Series(val tf: String, all: List<Candle>) {
    val bars: List<Candle> = all.filter { it.isClosed }
    /** แท่งล่าสุดทั้งหมด (รวมแท่งที่ยังไม่ปิด) — ใช้เฉพาะกรณีต้องการราคาปัจจุบัน */
    val live: Candle? = all.lastOrNull()
    val n: Int get() = bars.size
    val last: Candle? get() = bars.lastOrNull()
    val prev: Candle? get() = bars.getOrNull(bars.size - 2)

    val closes: List<Double> by lazy { bars.map { it.close } }
    val highs: List<Double> by lazy { bars.map { it.high } }
    val lows: List<Double> by lazy { bars.map { it.low } }
    val opens: List<Double> by lazy { bars.map { it.open } }
    val volumes: List<Double> by lazy { bars.map { it.volume.coerceAtLeast(0.0) } }

    private val emaCache = mutableMapOf<Int, List<Double?>>()
    fun emaSeries(p: Int): List<Double?> = emaCache.getOrPut(p) { TaIndicators.emaSeries(closes, p) }
    fun ema(p: Int, back: Int = 0): Double? = emaSeries(p).getOrNull(n - 1 - back)

    private val rsiCache = mutableMapOf<Int, List<Double?>>()
    fun rsiSeries(p: Int = 14): List<Double?> = rsiCache.getOrPut(p) { TaIndicators.rsiSeries(closes, p) }
    fun rsi(p: Int = 14, back: Int = 0): Double? = rsiSeries(p).getOrNull(n - 1 - back)

    val atrSeries: List<Double?> by lazy { TaIndicators.atrSeries(highs, lows, closes, 14) }
    fun atr(back: Int = 0): Double? = atrSeries.getOrNull(n - 1 - back)

    val macdHist: List<Double?> by lazy { TaIndicators.macdHistSeries(closes) }

    /** swing ที่ยืนยันแล้ว (causal) — ค่าปรากฏที่แท่งยืนยัน = center + L */
    internal val swings: Pair<DoubleArray, DoubleArray> by lazy {
        if (n < 2 * UnifiedSmcSignals.SWING_L + 1) DoubleArray(n) { Double.NaN } to DoubleArray(n) { Double.NaN }
        else UnifiedSmcSignals.confirmedSwings(bars, UnifiedSmcSignals.SWING_L)
    }

    /** รายการ swing high (index ของแท่งยืนยัน, ราคา) เรียงเก่า→ใหม่ */
    val swingHighs: List<Pair<Int, Double>> by lazy {
        swings.first.withIndex().filter { !it.value.isNaN() }.map { it.index to it.value }
    }
    val swingLows: List<Pair<Int, Double>> by lazy {
        swings.second.withIndex().filter { !it.value.isNaN() }.map { it.index to it.value }
    }

    /**
     * swing ตามตำแหน่ง "จุดยอดจริง" (center = แท่งยืนยัน − L) — ใช้กับงานเรขาคณิต
     * (เส้นแนวโน้ม, สามเหลี่ยม, channel, neckline) ที่ต้องรู้ว่าจุดอยู่แท่งไหน
     * [swingHighs] เก็บ index ของแท่งที่ "ยืนยัน" ซึ่งช้ากว่าจุดยอด L แท่ง — ถ้าลากเส้นด้วย index นั้น
     * เส้นทั้งเส้นเลื่อนขวา L แท่ง ค่าบนเส้น ณ แท่งปัจจุบันจึงผิด และจุดหลุด/ทะลุคลาดเคลื่อน
     */
    val swingHighPivots: List<Pair<Int, Double>> by lazy {
        swingHighs.map { (it.first - UnifiedSmcSignals.SWING_L) to it.second }
    }
    val swingLowPivots: List<Pair<Int, Double>> by lazy {
        swingLows.map { (it.first - UnifiedSmcSignals.SWING_L) to it.second }
    }

    internal val structure: UnifiedSmcSignals.Struct? by lazy {
        if (n < 30) null else UnifiedSmcSignals.structure(bars, UnifiedSmcSignals.SWING_L)
    }

    /** เพิ่งยืนยัน swing high ที่แท่งล่าสุด */
    val swingHighJustConfirmed: Boolean get() = n > 0 && !swings.first[n - 1].isNaN()
    val swingLowJustConfirmed: Boolean get() = n > 0 && !swings.second[n - 1].isNaN()

    fun avgVolume(period: Int = 20, excludeLast: Boolean = true): Double? {
        val end = if (excludeLast) n - 1 else n
        if (end - period < 0) return null
        return volumes.subList(end - period, end).average().takeIf { it > 0 }
    }

    fun highest(period: Int, excludeLast: Boolean = false): Double? {
        val end = if (excludeLast) n - 1 else n
        if (end - period < 0) return null
        return highs.subList(end - period, end).max()
    }

    fun lowest(period: Int, excludeLast: Boolean = false): Double? {
        val end = if (excludeLast) n - 1 else n
        if (end - period < 0) return null
        return lows.subList(end - period, end).min()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// WakeContext — สิ่งที่ trigger ทุกตัวเห็นตอนประเมิน
// ═════════════════════════════════════════════════════════════════════════════

/** Series ของทุก TF — สร้างครั้งเดียวต่อรอบสแกน แล้วแชร์ให้ทุกมุมมอง TF ([WakeContext.forTf]) */
class SeriesSet(
    m1: List<Candle>, m5: List<Candle>, m15: List<Candle>, h1: List<Candle>, h4: List<Candle>,
    intermarket: Map<String, List<Candle>>, extra: Map<String, List<Candle>>
) {
    val m1 = Series("1m", m1)
    val m5 = Series("5m", m5)
    val m15 = Series("15m", m15)
    val h1 = Series("1h", h1)
    val h4 = Series("4h", h4)
    val inter: Map<String, Series> = intermarket.mapValues { Series("1h", it.value) }
    val extra: Map<String, Series> = extra.entries.associate { (tf, bars) ->
        TaIndicators.normalizeTimeframe(tf).let { it to Series(it, bars) }
    }
}

/**
 * ครบทั้ง 5 TF ตามสเปกของระบบ:
 *   M1  → ราคาปัจจุบัน
 *   M5  → ยืนยัน / คัดกรองการหลอก
 *   M15 → setup ระยะสั้น
 *   H1  → บริบทระยะกลาง
 *   H4  → บริบทระยะยาว
 * พร้อมข้อมูลเสริม: intermarket (DXY / US10Y / BTC.D), ปฏิทินข่าว, Fear&Greed
 * และ [memory] ค่าจากการสแกนรอบก่อน (ใช้ตรวจ "การตัดผ่าน" ของค่าภายนอก)
 */
class WakeContext private constructor(
    val symbol: String,
    /**
     * TF ที่ trigger แบบ "ใช้ได้ทุก TF" กำลังถูกประเมิน ([primary]) — เริ่มต้น = TF ของ alert job
     * [forTf] สร้างมุมมองของ TF อื่นโดยใช้ Series ชุดเดิม (ไม่คำนวณอินดิเคเตอร์ซ้ำ)
     */
    val primaryTf: String,
    private val set: SeriesSet,
    val digest: MarketContextDigest.Digest?,
    /** แท่ง H1 ของตลาดที่เกี่ยวข้อง เช่น "DXY", "US10Y", "BTC.D" */
    val intermarket: Map<String, List<Candle>>,
    val macroEvents: List<TradingApiService.MacroEvent>,
    val fearGreed: Int?,
    /** คะแนน Deep Analysis ปัจจุบัน (0-100) ถ้ามี */
    val deepScore: Int?,
    /** Harmonic pattern ที่ตรวจพบบน TF ของ job */
    val harmonics: List<com.skyliner2008.jarvis.tools.trading.HarmonicPattern>,
    /** สถานะ Elliott wave ปัจจุบัน */
    val elliott: com.skyliner2008.jarvis.tools.trading.ElliotWaveModern?,
    /** ค่าจากการสแกนครั้งก่อนของ symbol นี้ */
    val memory: Map<String, String>,
    val nowMs: Long
) {
    constructor(
        symbol: String,
        primaryTf: String,
        m1: List<Candle>,
        m5: List<Candle>,
        m15: List<Candle>,
        h1: List<Candle>,
        h4: List<Candle>,
        digest: MarketContextDigest.Digest?,
        intermarket: Map<String, List<Candle>> = emptyMap(),
        macroEvents: List<TradingApiService.MacroEvent> = emptyList(),
        fearGreed: Int? = null,
        deepScore: Int? = null,
        harmonics: List<com.skyliner2008.jarvis.tools.trading.HarmonicPattern> = emptyList(),
        elliott: com.skyliner2008.jarvis.tools.trading.ElliotWaveModern? = null,
        memory: Map<String, String> = emptyMap(),
        nowMs: Long,
        /**
         * TF หลักที่อยู่นอกชุด 5TF (เช่น 30m) — ให้ trigger ที่ใช้ [primary] ทำงานบน TF จริงของ job
         * เดิม job @30m ถูกประเมินบน M15 เงียบๆ ทั้งที่การ์ดแสดงว่า 30M
         */
        extra: Map<String, List<Candle>> = emptyMap()
    ) : this(
        symbol, TaIndicators.normalizeTimeframe(primaryTf), SeriesSet(m1, m5, m15, h1, h4, intermarket, extra),
        digest, intermarket, macroEvents, fearGreed, deepScore, harmonics, elliott, memory, nowMs
    )

    /** มุมมองเดียวกันแต่ให้ [primary] เป็น [tf] — ใช้ประเมินปัจจัยตัวเดียวกันบนหลาย TF */
    fun forTf(tf: String): WakeContext {
        val t = TaIndicators.normalizeTimeframe(tf)
        return if (t == primaryTf) this else WakeContext(
            symbol, t, set, digest, intermarket, macroEvents, fearGreed, deepScore, harmonics, elliott, memory, nowMs
        )
    }

    val m1: Series get() = set.m1
    val m5: Series get() = set.m5
    val m15: Series get() = set.m15
    val h1: Series get() = set.h1
    val h4: Series get() = set.h4

    fun inter(key: String): Series? = set.inter[key]

    fun series(tf: String): Series = when (val t = TaIndicators.normalizeTimeframe(tf)) {
        "1m" -> m1
        "5m" -> m5
        "15m" -> m15
        "1h" -> h1
        "4h" -> h4
        else -> set.extra[t] ?: m15
    }

    /** Series ของ TF หลัก */
    val primary: Series get() = series(primaryTf)

    /** ATR ของ TF หลัก — ใช้ normalize ระยะทางให้เทียบข้ามสินทรัพย์ได้ */
    val atr: Double get() = primary.atr() ?: 0.0

    /** ราคาปัจจุบัน (แท่งสด M1 ถ้ามี) */
    val price: Double get() = m1.live?.close ?: primary.live?.close ?: 0.0

    /** เทรนด์ของ TF นั้นจาก digest (UP / DOWN / RANGE / null) */
    fun trendOf(tf: String): String? = digest?.tfLines
        ?.firstOrNull { it.tf.equals(tf, ignoreCase = true) }
        ?.let { if (it.trend > 0) "UP" else if (it.trend < 0) "DOWN" else "RANGE" }

    /**
     * เทรนด์ H4/H1/M15 เรียงทิศเดียวกันไหม: 1 = ขาขึ้นทั้งหมด, −1 = ขาลงทั้งหมด, 0 = ไม่เรียง/ไม่มีข้อมูล
     * (M15 เกิด CHoCH สวนทางเมื่อไร trend ของ M15 จะกลับทิศเอง → ไม่นับว่าเรียงกันอีก)
     */
    val htfAlignment: Int by lazy {
        val t = listOf("H4", "H1", "M15").map { trendOf(it) }
        when {
            t.all { it == "UP" } -> 1
            t.all { it == "DOWN" } -> -1
            else -> 0
        }
    }

    val isMetalOrFx: Boolean by lazy { TaIndicators.sessionOffsetHoursFor(symbol) != 0 }
    val isCrypto: Boolean by lazy {
        val s = symbol.uppercase()
        s.endsWith("USDT") || s.startsWith("BTC") || s.startsWith("ETH")
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// DSL — เขียน trigger ให้สั้น กระชับ อ่านง่าย
// ═════════════════════════════════════════════════════════════════════════════

internal fun trigger(
    id: String,
    name: String,
    group: EvidenceGroup,
    kind: TriggerKind,
    minBars: Int = 60,
    detect: (WakeContext) -> TriggerEvent?
): WakeTrigger = object : WakeTrigger {
    override val id = id
    override val name = name
    override val evidenceGroup = group
    override val kind = kind
    override val minBars = minBars
    override fun detect(ctx: WakeContext): TriggerEvent? = detect(ctx)
}

/** รูปแบบราคาให้อ่านง่าย */
internal fun p(v: Double): String = formatPrice(v)

/**
 * ป้าย TF สำหรับแสดงผล — TF ที่ไม่ใช่ timeframe จริง ("multi", "event") ต้องไม่ถูกแปลง
 * (เดิม normalizeTimeframe คืน 1h เมื่อไม่รู้จัก ทำให้เหตุการณ์หลาย TF / ข่าว ขึ้นป้าย [H1] ผิดๆ)
 */
internal fun tfLabel(tf: String): String {
    when (tf.lowercase()) {
        "multi" -> return "MTF"
        "event" -> return "ข่าว"
    }
    val spec = TaIndicators.timeframeSpecOrNull(tf) ?: return tf.uppercase()
    return when (spec.canonical) {
        "1m" -> "M1"; "5m" -> "M5"; "15m" -> "M15"; "30m" -> "M30"; "1h" -> "H1"; "4h" -> "H4"; "1D" -> "D1"; "1W" -> "W1"
        else -> spec.canonical.uppercase()
    }
}

/**
 * รูปแบบราคาให้อ่านง่ายทุกระดับราคา
 * ≥100 → 2 ตำแหน่ง (ทอง/BTC/USDJPY) · 1–100 → 5 ตำแหน่ง (EURUSD 1.08523) · <1 → 6 หลักนัยสำคัญ
 * (เดิมใช้ 4 ตำแหน่งตายตัว — คริปโตราคาต่ำอย่าง 0.00002 แสดงเป็น 0.0000)
 */
internal fun formatPrice(v: Double): String {
    val a = kotlin.math.abs(v)
    val decimals = when {
        a >= 100 -> 2
        a >= 1 -> 5
        a == 0.0 -> 2
        else -> (kotlin.math.ceil(-kotlin.math.log10(a)).toInt() + 5).coerceIn(2, 12)
    }
    val s = "%.${decimals}f".format(v)
    if (decimals <= 2 || !s.contains('.')) return s
    // ตัด 0 ท้ายแต่เหลืออย่างน้อย 2 ตำแหน่ง
    val dot = s.indexOf('.')
    var end = s.length
    while (end > dot + 3 && s[end - 1] == '0') end--
    return s.substring(0, end)
}
