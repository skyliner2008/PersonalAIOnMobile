package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.tools.trading.TaIndicators

/**
 * WakeTfProfile — ปัจจัยไหนประเมินบน TF ไหน และ TF ไหน "ปลุก AI ได้" ตั้งแต่เริ่ม
 *
 * **ประเมินและเก็บการเรียนรู้ทุก TF** ([EVAL_TFS]) สำหรับปัจจัยที่ใช้ได้กับทุก TF — ให้ระบบเรียนรู้เองว่า
 * ปัจจัยไหนเหมาะกับ TF ไหน (การเรียนรู้ไม่ใช้โทเคน)
 * แต่ **การปลุก AI** เริ่มจากชุด TF ที่เหมาะกับธรรมชาติของปัจจัย ([defaultWakeTfs]) แล้วปรับตามผลจริง
 * ([WakeLearningStore.tfVerdict]: ลดชั้นเมื่อผลแย่ / เลื่อนขึ้นเมื่อ TF นอกชุดพิสูจน์ว่าดี)
 *
 * หลักที่ใช้เลือก TF เริ่มต้น:
 *  - **M1** = จังหวะ: แตะ/กวาดระดับสำคัญ (sweep, stop hunt, PDH/PWH, VWAP band, volume profile, OB),
 *    climax volume, ราคาพุ่งผิดปกติ — จับได้เร็วกว่า M15 สูงสุด 14 นาที
 *    (oscillator / impulse / volume spike / delta บน M1 เกิด 70–210 ครั้ง/วันในการจำลอง BTC = noise จึงไม่ปลุก)
 *  - **M5** = ยืนยัน: โครงสร้างย่อย (BOS/CHoCH), divergence, แท่งกลับตัวที่ระดับ, sweep
 *    (oscillator เร็ว MACD hist / %R / Stoch / RSI(5) ปลุกเฉพาะ M15)
 *  - **M15** = setup: ทุกปัจจัย (พฤติกรรมเดิมของระบบ)
 *  - **H1/H4** = บริบทใหญ่: โครงสร้าง, เทรนด์, รูปแบบกราฟ, divergence, ระดับ HTF — ไม่ใช้ oscillator เร็ว
 *
 * ปัจจัย [FIXED] ผูกกับ TF/ข้อมูลเฉพาะอยู่แล้ว (ภาพ 5TF, intermarket, ข่าว, session ของ M15, memory)
 * จึงประเมินครั้งเดียวต่อการสแกน
 */
object WakeTfProfile {
    const val M1 = "1m"
    const val M5 = "5m"
    const val M15 = "15m"
    const val H1 = "1h"
    const val H4 = "4h"

    /** TF ที่ประเมินปัจจัยแบบ "ใช้ได้ทุก TF" ทุกการสแกน (+ TF ของ job ถ้าอยู่นอกชุด เช่น 30m) */
    val EVAL_TFS = listOf(M1, M5, M15, H1, H4)

    /** ปัจจัยที่ประเมินครั้งเดียว (ผูกกับ TF/ข้อมูลเฉพาะ) */
    val FIXED: Set<String> = setOf(
        "ALL_TF_ALIGNED", "M5_CONFIRM_DIVERGENCE", "HTF_CONFLICT", "LTF_PULLBACK_END", "MTF_OSCILLATOR_CONFLUENCE",
        "SESSION_OPEN", "ASIA_RANGE_SET", "KILLZONE_ENTRY", "ASIA_RANGE_BREAK", "WEEK_BOUNDARY",
        "DXY_MOVE", "YIELD_SPIKE", "CORRELATION_BREAK", "CRYPTO_BROAD_MOVE",
        "HIGH_IMPACT_NEWS_SOON", "POST_NEWS_SPIKE", "FEAR_GREED_EXTREME", "SPREAD_WIDENING",
        "HTF_CHOCH_H1", "HTF_CHOCH_H4", "KEY_LEVEL_TOUCH", "PREMIUM_DISCOUNT_EXTREME",
        "DEEP_SCORE_CROSS", "HARMONIC_COMPLETION", "ELLIOTT_STAGE_CHANGE", "OB_MITIGATED"
    )

    /**
     * ปัจจัยตายตัวที่เก็บสถิติต่อแต่ไม่ปลุก — ซ้ำกับ CHOCH ที่ตอนนี้ประเมินบน H1/H4 อยู่แล้ว
     * (เก็บไว้ให้สถิติเดิมต่อเนื่อง)
     */
    val FIXED_RECORD_ONLY: Set<String> = setOf("HTF_CHOCH_H1", "HTF_CHOCH_H4")

    private val ALL = setOf(M1, M5, M15, H1, H4)
    private val LTF_TO_H1 = setOf(M1, M5, M15, H1)
    private val TIMING = setOf(M1, M5, M15)
    private val CONFIRM_UP = setOf(M5, M15, H1, H4)
    private val CONFIRM_TO_H1 = setOf(M5, M15, H1)
    private val SETUP_UP = setOf(M15, H1, H4)
    private val SETUP_H1 = setOf(M15, H1)
    private val SETUP_LTF = setOf(M5, M15)

    private val WAKE_TFS: Map<String, Set<String>> = buildMap {
        // โครงสร้าง
        put("BOS", CONFIRM_UP); put("CHOCH", CONFIRM_UP); put("SWING_FAILURE", CONFIRM_UP)
        put("HL_LH_CONFIRMED", SETUP_UP); put("INTERNAL_EXTERNAL_MISMATCH", SETUP_LTF); put("RANGE_FORMED", SETUP_H1)
        put("RANGE_BREAKOUT", CONFIRM_UP); put("FAILED_BREAKOUT", CONFIRM_TO_H1)
        // ระดับราคา
        put("OB_TOUCH", LTF_TO_H1); put("FVG_ENTER", CONFIRM_UP); put("FVG_FILLED", SETUP_H1)
        put("BREAKER_RETEST", CONFIRM_TO_H1); put("PDH_PDL_TOUCH", TIMING); put("PWH_PWL_TOUCH", LTF_TO_H1)
        put("ROUND_NUMBER", SETUP_LTF); put("FIB_GOLDEN_POCKET", SETUP_UP); put("FIB_EXTENSION", SETUP_UP)
        put("VOLUME_PROFILE_LEVEL", TIMING); put("DAILY_PIVOT_TOUCH", TIMING); put("SR_FLIP_RETEST", CONFIRM_TO_H1)
        // สภาพคล่อง
        put("EQ_POOL_FORMED", SETUP_H1); put("EQ_POOL_SWEPT", LTF_TO_H1); put("LIQUIDITY_SWEEP_REJECTION", ALL)
        put("STOP_HUNT_SPIKE", TIMING); put("LIQUIDITY_VOID", CONFIRM_TO_H1); put("SESSION_LEVEL_SWEEP", TIMING)
        // เทรนด์
        put("EMA_CROSS", SETUP_UP); put("EMA_CONVERGENCE", SETUP_UP); put("GOLDEN_DEATH_CROSS", SETUP_UP)
        put("PRICE_CROSS_EMA200", SETUP_UP); put("EMA_RIBBON_ALIGN", SETUP_UP); put("EMA_PULLBACK", SETUP_UP)
        put("SUPERTREND_FLIP", SETUP_UP); put("ICHIMOKU_TK_CROSS", SETUP_UP); put("ICHIMOKU_CLOUD_BREAK", SETUP_UP)
        put("ADX_TREND_BIRTH", SETUP_UP); put("ADX_EXHAUSTION", SETUP_UP); put("TREND_ACCELERATION", CONFIRM_TO_H1)
        // oscillator
        put("RSI_EXIT_EXTREME", CONFIRM_UP); put("RSI_DIVERGENCE", CONFIRM_UP); put("RSI_HIDDEN_DIVERGENCE", SETUP_UP)
        put("RSI_50_CROSS", SETUP_H1); put("MACD_SIGNAL_CROSS", SETUP_UP); put("MACD_ZERO_CROSS", SETUP_UP)
        put("MACD_HISTOGRAM_TURN", setOf(M15)); put("MACD_DIVERGENCE", CONFIRM_UP); put("STOCH_EXTREME_CROSS", setOf(M15))
        put("CCI_100_CROSS", setOf(M15)); put("WILLIAMS_R_EXIT", setOf(M15)); put("MFI_EXTREME", SETUP_H1)
        put("MOMENTUM_EXHAUSTION", CONFIRM_TO_H1); put("FAST_RSI_THRUST", setOf(M15))
        // ความผันผวน
        put("VOLATILITY_SQUEEZE", SETUP_UP); put("SQUEEZE_FIRE", CONFIRM_UP); put("BAND_WALK", SETUP_H1)
        put("BAND_REJECTION", CONFIRM_TO_H1); put("ATR_EXPANSION", CONFIRM_TO_H1); put("ATR_CONTRACTION", SETUP_H1)
        put("NR7_INSIDE_BAR", SETUP_UP); put("VOLATILITY_REGIME_CHANGE", SETUP_UP)
        // volume
        put("VOLUME_SPIKE", CONFIRM_TO_H1); put("VOLUME_ABSORPTION", SETUP_LTF); put("CLIMAX_VOLUME", ALL)
        put("VOLUME_DRYUP_AT_LEVEL", SETUP_LTF); put("OBV_DIVERGENCE", SETUP_UP); put("OBV_LEADING_BREAKOUT", SETUP_H1)
        put("VWAP_RECLAIM", SETUP_LTF); put("VWAP_BAND_TOUCH", TIMING); put("DELTA_SHIFT", SETUP_LTF)
        // แท่งเทียน
        put("ENGULFING_AT_LEVEL", CONFIRM_UP); put("PIN_BAR", CONFIRM_UP); put("INSIDE_BAR_BREAK", SETUP_UP)
        put("STAR_PATTERN", SETUP_UP); put("THREE_SOLDIERS_CROWS", SETUP_UP); put("IMPULSE_CANDLE", CONFIRM_UP)
        put("DOJI_AT_EXTREME", SETUP_UP); put("GAP", CONFIRM_UP)
        // รูปแบบกราฟ
        put("DOUBLE_TOP_BOTTOM", SETUP_UP); put("HEAD_SHOULDERS", SETUP_UP); put("TRIANGLE_WEDGE_BREAK", SETUP_UP)
        put("CHANNEL_TOUCH", SETUP_UP); put("TRENDLINE_BREAK", SETUP_UP)
        // หลาย TF / session / สภาวะ
        put("TF_CONTINUATION", CONFIRM_TO_H1)
        put("DAILY_OPEN_CROSS", CONFIRM_TO_H1); put("WEEKLY_OPEN_CROSS", SETUP_UP); put("MONTHLY_OPEN_CROSS", SETUP_UP)
        put("INSTITUTIONAL_SHIFT", SETUP_H1); put("REGIME_CHANGE", SETUP_UP); put("PRICE_ANOMALY", ALL)
    }

    fun isFixed(id: String): Boolean = id.uppercase() in FIXED

    /** มีชุด TF เริ่มต้นที่กำหนดไว้ (ใช้ตรวจว่าปัจจัยใหม่ถูกเพิ่มในโปรไฟล์แล้ว) */
    fun hasProfile(id: String): Boolean = id.uppercase() in WAKE_TFS

    /**
     * TF ที่ปัจจัยนี้ปลุก AI ได้ตั้งแต่เริ่ม (ก่อนมีสถิติ)
     * ปัจจัยที่ไม่มีในตาราง (เพิ่มใหม่) ปลุกได้เฉพาะ M15 จนกว่าสถิติจะพิสูจน์ TF อื่น
     */
    fun defaultWakeTfs(id: String): Set<String> = WAKE_TFS[id.uppercase()] ?: setOf(M15)

    /** TF ของ job ปลุกได้เสมอ (พฤติกรรมเดิม) — ปัจจัยตายตัวปลุกได้ยกเว้นตัวที่ซ้ำ */
    fun isDefaultWakeTf(id: String, tf: String, jobTf: String): Boolean {
        val u = id.uppercase()
        if (u in FIXED) return u !in FIXED_RECORD_ONLY
        val t = TaIndicators.normalizeTimeframe(tf)
        return t == TaIndicators.normalizeTimeframe(jobTf) || t in defaultWakeTfs(u)
    }

    /** TF ที่เหตุการณ์ของปัจจัยตายตัวถูกนับ — ใช้ TF ที่ปัจจัยบอกถ้าเป็น TF จริง ไม่งั้นใช้ TF ของ job */
    fun normalizeEventTf(tf: String, jobTf: String): String {
        // timeframeSpecOrNull: "multi" ต้องไม่กลายเป็น 1h (normalizeTimeframe เดาเป็น 1h เมื่อไม่รู้จัก)
        val t = TaIndicators.timeframeSpecOrNull(tf)?.canonical
        return if (t != null && t in setOf(M1, M5, M15, "30m", H1, H4)) t else TaIndicators.normalizeTimeframe(jobTf)
    }
}
