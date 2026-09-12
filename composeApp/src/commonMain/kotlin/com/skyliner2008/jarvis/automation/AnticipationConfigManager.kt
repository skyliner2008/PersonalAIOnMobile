package com.skyliner2008.jarvis.automation

/**
 * AnticipationConfigManager — ผู้จัดการปัจจัยสำหรับการคาดการณ์สัญญาณล่วงหน้า (Signal Anticipation)
 *
 * ทำหน้าที่:
 * 1. กำหนด Curated Factor Whitelist (คลัง 10 ปัจจัยมาตรฐานที่ผ่านการพิสูจน์ทางคณิตศาสตร์และเทคนิคอล)
 * 2. ป้องกันผู้ใช้หรือ AI กำหนดปัจจัยมั่ว/ผิดพลาด โดยบังคับให้เลือกเฉพาะปัจจัยใน Whitelist เท่านั้น
 * 3. จัดเก็บสถานะปัจจัยที่เปิดใช้งานแยกตาม Symbol (หรือใช้ Core 4 ปัจจัยเป็นค่าเริ่มต้น)
 * 4. ให้คำแนะนำปัจจัย (Recommendation) ที่เหมาะสมกับสภาวะตลาด
 */
object AnticipationConfigManager {

    data class FactorDefinition(
        val id: String,
        val name: String,
        val category: String,
        val description: String,
        val isCoreDefault: Boolean,
        val defaultConfidence: Int
    )

    // ─── Curated Factor Whitelist (13 ปัจจัยมาตรฐาน) ───────────────────────────

    val FACTOR_KEYZONE_PROXIMITY = FactorDefinition(
        id = "KEYZONE_PROXIMITY",
        name = "Keyzone Proximity (SMC)",
        category = "Smart Money Concepts",
        description = "ราคาเคลื่อนเข้าใกล้โซน Demand / Supply Order Block, Fair Value Gap (FVG) หรือ Swing Liquidity (0.3x ATR)",
        isCoreDefault = true,
        defaultConfidence = 78
    )

    val FACTOR_WICK_SWEEP_REJECTION = FactorDefinition(
        id = "WICK_SWEEP_REJECTION",
        name = "Intra-bar Wick Sweep Rejection",
        category = "Price Action",
        description = "ราคา Sweep กวาดทะลุ High/Low ย่อย 10 แท่งล่าสุด แล้วเกิดไส้ยาวปฏิเสธราคา (Wick Rejection >= 1.5x Body)",
        isCoreDefault = true,
        defaultConfidence = 75
    )

    val FACTOR_RSI_EXTREME = FactorDefinition(
        id = "RSI_EXTREME",
        name = "RSI Extreme / Divergence",
        category = "Oscillator",
        description = "RSI14 เข้าโซนสุดโต่ง Oversold (<= 28) คาดการณ์เตรียมดีดตัวขึ้น หรือ Overbought (>= 72) คาดการณ์เตรียมเทขาย",
        isCoreDefault = true,
        defaultConfidence = 70
    )

    val FACTOR_EMA_NEAR_CROSS = FactorDefinition(
        id = "EMA_NEAR_CROSS",
        name = "EMA 14/60 Dynamic Convergence",
        category = "Trend Velocity",
        description = "เส้น EMA 14 และ EMA 60 บีบตัวแคบเข้าหากันในระยะกระชั้นชิด (Distance <= Dynamic Threshold) พร้อมพุ่งตัดกัน",
        isCoreDefault = true,
        defaultConfidence = 76
    )

    val FACTOR_BOLLINGER_SQUEEZE = FactorDefinition(
        id = "BOLLINGER_SQUEEZE",
        name = "Bollinger Bands Squeeze & Compression",
        category = "Volatility",
        description = "Bollinger Bandwidth บีบตัวแคบผิดปกติ (Bandwidth <= 2.2x ATR) สภาวะสะสมพลังเตรียมระเบิด Breakout รุนแรง",
        isCoreDefault = true,
        defaultConfidence = 74
    )

    val FACTOR_MACD_HISTOGRAM_TURN = FactorDefinition(
        id = "MACD_HISTOGRAM_TURN",
        name = "MACD Histogram Momentum Turn",
        category = "Momentum",
        description = "MACD Histogram หดตัวกลับทิศใกล้เส้น Zero Line (จุดเริ่มต้นของการหมุนรอบ Momentum ใหม่)",
        isCoreDefault = true,
        defaultConfidence = 72
    )

    val FACTOR_VOLUME_ABSORPTION = FactorDefinition(
        id = "VOLUME_ABSORPTION",
        name = "Smart Money Volume Absorption",
        category = "Volume Spread Analysis",
        description = "ปริมาณ Volume สูงกว่าค่าเฉลี่ย 1.8 เท่า แต่สเปรดแท่งเทียนแคบ (Doji / Absorption) แสดงถึงการซุ่มเก็บของหรือรับแรงขาย",
        isCoreDefault = true,
        defaultConfidence = 77
    )

    val FACTOR_FIBONACCI_GOLDEN_POCKET = FactorDefinition(
        id = "FIBONACCI_GOLDEN_POCKET",
        name = "Fibonacci Golden Pocket Touch",
        category = "Market Geometry",
        description = "ราคาย่อตัวลงมาแตะแนวรับ/แนวต้าน Fibonacci Golden Pocket (0.618 - 0.650) ในรอบ Swing ปัจจุบัน",
        isCoreDefault = true,
        defaultConfidence = 75
    )

    val FACTOR_STOCHASTIC_OVERSOLD_TURN = FactorDefinition(
        id = "STOCHASTIC_OVERSOLD_TURN",
        name = "Stochastic Extreme Reversal",
        category = "Cycle Oscillator",
        description = "Stochastic (%K, %D) ตัดขึ้นจากโซนต่ำกว่า 20 หรือตัดลงจากโซนสูงกว่า 80 ชี้จุดกลับตัวของรอบระยะสั้น",
        isCoreDefault = true,
        defaultConfidence = 71
    )

    val FACTOR_SESSION_OPEN_SWEEP = FactorDefinition(
        id = "SESSION_OPEN_SWEEP",
        name = "Session High/Low Liquidity Sweep",
        category = "Session Timing",
        description = "ราคา Sweep กวาด High หรือ Low ของ Session ก่อนหน้า (เช่น Asia High/Low Sweep ในช่วง London/US Session)",
        isCoreDefault = true,
        defaultConfidence = 79
    )

    val FACTOR_VEYRA_SHIFT = FactorDefinition(
        id = "VEYRA_SHIFT",
        name = "Veyra Institutional Shift Ledger",
        category = "Institutional Engine",
        description = "ระบบประเมิน 6 เสาหลักเชิงสถาบัน (Trend, Pressure, Auction, Structure, Volatility, HTF) Shift Score >= 70 คะแนน",
        isCoreDefault = true,
        defaultConfidence = 84
    )

    val FACTOR_BB_KC_SQUEEZE = FactorDefinition(
        id = "BB_KC_SQUEEZE",
        name = "Bollinger vs Keltner Channels Squeeze",
        category = "Volatility Breakout",
        description = "Bollinger Bands บีบตัวแคบใน Keltner Channels สะสมพลังเตรียมระเบิด หรือเพิ่งระเบิด Fired ตามทิศทาง LinReg Slope",
        isCoreDefault = true,
        defaultConfidence = 80
    )

    val FACTOR_FAST_RSI_REVERSAL = FactorDefinition(
        id = "FAST_RSI_REVERSAL",
        name = "Fast RSI(5) Momentum Thrust (ABQ1)",
        category = "Momentum Reversal",
        description = "Fast RSI(5) ตัดทะลุ 35 ขึ้น (จุดกลับตัวฉับไว) หรือตัดหลุด 75 ลง (ล็อกกำไร)",
        isCoreDefault = true,
        defaultConfidence = 76
    )

    val ALL_FACTORS: List<FactorDefinition> = listOf(
        FACTOR_KEYZONE_PROXIMITY,
        FACTOR_WICK_SWEEP_REJECTION,
        FACTOR_RSI_EXTREME,
        FACTOR_EMA_NEAR_CROSS,
        FACTOR_BOLLINGER_SQUEEZE,
        FACTOR_MACD_HISTOGRAM_TURN,
        FACTOR_VOLUME_ABSORPTION,
        FACTOR_FIBONACCI_GOLDEN_POCKET,
        FACTOR_STOCHASTIC_OVERSOLD_TURN,
        FACTOR_SESSION_OPEN_SWEEP,
        FACTOR_VEYRA_SHIFT,
        FACTOR_BB_KC_SQUEEZE,
        FACTOR_FAST_RSI_REVERSAL
    )

    private val FACTOR_MAP: Map<String, FactorDefinition> = ALL_FACTORS.associateBy { it.id.uppercase() }

    val CORE_DEFAULT_FACTOR_IDS: Set<String> = ALL_FACTORS.filter { it.isCoreDefault }.map { it.id }.toSet()

    // ─── Dynamic Factor Reinforcement Learning State ──────────────────────────

    private val factorConfidenceAdjustments = mutableMapOf<String, Int>()

    /**
     * คืนค่าความเชื่อมั่นหลังคำนวณการเรียนรู้แบบ Closed-Loop
     */
    fun getEffectiveConfidence(factorId: String): Int {
        val def = findFactor(factorId) ?: return 70
        val adj = factorConfidenceAdjustments[factorId.trim().uppercase()] ?: 0
        return (def.defaultConfidence + adj).coerceIn(50, 98)
    }

    /**
     * ปรับค่าน้ำหนักความเชื่อมั่นของ Factor จากสถิติ Win/Loss จริง (Reinforcement Learning)
     */
    fun applyFactorWeightAdjustment(factorId: String, delta: Int) {
        val cleanId = factorId.trim().uppercase()
        val current = factorConfidenceAdjustments[cleanId] ?: 0
        factorConfidenceAdjustments[cleanId] = (current + delta).coerceIn(-25, 25)
    }

    fun getDynamicWeight(factorId: String): Int =
        factorConfidenceAdjustments[factorId.trim().uppercase()] ?: 0

    /**
     * ดึงค่าการปรับแต่งทั้งหมด
     */
    fun getFactorAdjustments(): Map<String, Int> = factorConfidenceAdjustments.toMap()

    // ─── Per-Symbol Factor Configuration State ────────────────────────────────

    private val symbolFactorConfig = mutableMapOf<String, MutableSet<String>>()

    private fun normalizeSymbol(symbol: String): String =
        symbol.trim().uppercase().replace("/", "").replace(":", "").substringBefore("@")

    /**
     * ดึงรายการปัจจัยที่เปิดใช้งานสำหรับ symbol นั้น
     * หากยังไม่เคยตั้งค่า จะคืนค่า Core 4 ปัจจัยหลักเป็นค่าเริ่มต้น
     */
    fun getActiveFactors(symbol: String): Set<String> {
        val sym = normalizeSymbol(symbol)
        val factors = symbolFactorConfig[sym]
        return if (!factors.isNullOrEmpty()) {
            factors.toSet()
        } else {
            CORE_DEFAULT_FACTOR_IDS
        }
    }

    /**
     * ตั้งค่ารายการปัจจัยสำหรับ symbol นั้น (ตรวจสอบกับ Whitelist)
     * คืนค่ารายการปัจจัยที่ตั้งสำเร็จ
     */
    fun setFactors(symbol: String, factorIds: Collection<String>): Set<String> {
        val sym = normalizeSymbol(symbol)
        val validIds = factorIds.map { it.trim().uppercase() }.filter { it in FACTOR_MAP }.toSet()
        val targetSet = if (validIds.isNotEmpty()) validIds else CORE_DEFAULT_FACTOR_IDS
        symbolFactorConfig[sym] = targetSet.toMutableSet()
        return targetSet
    }

    /**
     * เพิ่มปัจจัยเข้าไปในชุดของ symbol นั้น
     * คืนค่า true หากเพิ่มสำเร็จ (อยู่ใน Whitelist)
     */
    fun addFactor(symbol: String, factorId: String): Boolean {
        val cleanId = factorId.trim().uppercase()
        if (cleanId !in FACTOR_MAP) return false
        val sym = normalizeSymbol(symbol)
        val current = symbolFactorConfig.getOrPut(sym) { CORE_DEFAULT_FACTOR_IDS.toMutableSet() }
        current.add(cleanId)
        return true
    }

    /**
     * นำปัจจัยออกจากชุดของ symbol นั้น
     * คืนค่า true หากนำออกสำเร็จ
     */
    fun removeFactor(symbol: String, factorId: String): Boolean {
        val cleanId = factorId.trim().uppercase()
        val sym = normalizeSymbol(symbol)
        val current = symbolFactorConfig[sym] ?: CORE_DEFAULT_FACTOR_IDS.toMutableSet()
        val removed = current.remove(cleanId)
        // ถ้าเหลือว่าง ให้คืนกลับเป็น Core Defaults
        if (current.isEmpty()) {
            symbolFactorConfig[sym] = CORE_DEFAULT_FACTOR_IDS.toMutableSet()
        } else {
            symbolFactorConfig[sym] = current
        }
        return removed
    }

    /**
     * รีเซ็ตกลับเป็น 4 ปัจจัยหลัก (Core Defaults)
     */
    fun resetToDefaults(symbol: String): Set<String> {
        val sym = normalizeSymbol(symbol)
        symbolFactorConfig.remove(sym)
        return CORE_DEFAULT_FACTOR_IDS
    }

    /**
     * รีเซ็ตการตั้งค่าของทุก Symbol (ใช้สำหรับการทดสอบ)
     */
    fun resetAll() {
        symbolFactorConfig.clear()
    }

    /**
     * ค้นหาคำอธิบายปัจจัยตาม ID
     */
    fun findFactor(factorId: String): FactorDefinition? =
        FACTOR_MAP[factorId.trim().uppercase()]

    /**
     * รายการปัจจัยทั้งหมดพร้อมสถานะ Active ของ symbol นั้น
     */
    fun listFactorsWithStatus(symbol: String): List<Pair<FactorDefinition, Boolean>> {
        val active = getActiveFactors(symbol)
        return ALL_FACTORS.map { factor ->
            factor to (factor.id in active)
        }
    }

    /**
     * แนะนำชุดปัจจัยที่เหมาะสมตามประเภทสินทรัพย์หรือสภาวะตลาด
     */
    fun getRecommendedFactors(symbol: String, regime: String = "NORMAL"): Set<String> {
        val sym = normalizeSymbol(symbol)
        val isGold = sym.contains("XAU") || sym.contains("GOLD")
        val isCrypto = sym.contains("BTC") || sym.contains("ETH") || sym.contains("USDT")

        val rec = CORE_DEFAULT_FACTOR_IDS.toMutableSet()
        rec.add("VEYRA_SHIFT")
        rec.add("BB_KC_SQUEEZE")
        if (isGold) {
            // ทองคำเคลื่อนไหวตาม SMC Liquidity, Veyra Shift และ Session ได้ดีมาก
            rec.add("FIBONACCI_GOLDEN_POCKET")
            rec.add("SESSION_OPEN_SWEEP")
        }
        if (isCrypto) {
            // คริปโตตอบสนองต่อ Volume Absorption, BB Squeeze และ Fast RSI ดี
            rec.add("VOLUME_ABSORPTION")
            rec.add("FAST_RSI_REVERSAL")
        }
        if (regime.contains("CHAOTIC", ignoreCase = true) || regime.contains("COMPRESSION", ignoreCase = true)) {
            rec.add("BB_KC_SQUEEZE")
            rec.add("BOLLINGER_SQUEEZE")
        }
        return rec
    }
}
