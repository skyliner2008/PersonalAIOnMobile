package com.example.personalaibot.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ConditionOperator {
    GT,  // Greater Than (>)
    LT,  // Less Than (<)
    GTE, // Greater Than or Equal (>=)
    LTE, // Less Than or Equal (<=)
    EQ,  // Equal (==)
    CONTAINS // For string matching (News, Sentiment)
}

@Serializable
data class AutomationCondition(
    val field: String,      // e.g. "price", "RSI", "sentiment_score"
    val operator: ConditionOperator,
    val value: String,      // String representation of the threshold
    val delivery: String = "ai" // "ai" = alert→AI quick-check→ผู้ใช้ | "direct" = alert→notification+แชท โดยตรง (ประหยัดโทเคน)
)

val automationJson = Json { 
    ignoreUnknownKeys = true
    isLenient = true
}

// ═══════════════════════════════════════════════════════════════════════
// AlertFieldCatalog — รายการ tool/field ที่ background engine ดึงค่าได้จริง
// (อ้างอิงจาก JarvisAutomationService.checkJob + TradingApiService)
// ใช้ทั้งใน UI (dropdown ตอนสร้าง Alert) และเป็น single source of truth
// ให้ AI tool description ไม่ให้ตั้งเงื่อนไขมั่วที่ระบบทำไม่ได้
// ═══════════════════════════════════════════════════════════════════════
data class AlertFieldOption(
    val field: String,
    val label: String,          // ชื่อแสดงผล เช่น "RSI"
    val hint: String,           // คำอธิบาย/ตัวอย่างการใช้
    val isNumeric: Boolean = true  // false = ฟิลด์ข้อความ ใช้ได้เฉพาะ == / contains
)

data class AlertToolOption(
    val toolName: String,
    val label: String,
    val description: String,
    val fields: List<AlertFieldOption>
)

object AlertFieldCatalog {

    val PRICE = AlertToolOption(
        toolName = "trading_price",
        label = "ราคา (Price)",
        description = "ราคาปัจจุบันแบบเรียลไทม์ (TradingView/Yahoo/SMC fallback)",
        fields = listOf(
            AlertFieldOption("price", "Price — ราคาปัจจุบัน", "เช่น price >= 4100 (ทองแตะ 4100)"),
            AlertFieldOption("change", "Change — การเปลี่ยนแปลง (จุด)", "เช่น change <= -50 (ร่วง 50 จุดจากรอบก่อน)"),
            AlertFieldOption("change_pct", "Change % — เปลี่ยนแปลง %", "เช่น change_pct >= 3 (ขึ้น 3% ขึ้นไป)"),
            AlertFieldOption("prev_close", "Previous Close — ราคาปิดก่อนหน้า", ""),
            AlertFieldOption("high_52w", "52-Week High", "ใช้ได้กับหุ้น (Yahoo source เท่านั้น)"),
            AlertFieldOption("low_52w", "52-Week Low", "ใช้ได้กับหุ้น (Yahoo source เท่านั้น)"),
            AlertFieldOption("direction", "Direction (UP/DOWN)", "ใช้กับ == เช่น direction == UP", isNumeric = false)
        )
    )

    val TA = AlertToolOption(
        toolName = "trading_technical_analysis",
        label = "อินดิเคเตอร์ (Technical Analysis, TF 1h)",
        description = "อินดิเคเตอร์จาก TradingView Scanner: RSI / MACD / Bollinger / ATR / ADX / สัญญาณรวม",
        fields = listOf(
            AlertFieldOption("close", "Close — ราคาปิดแท่ง 1h", ""),
            AlertFieldOption("RSI", "RSI (14)", "Overbought: RSI >= 70 | Oversold: RSI <= 30"),
            AlertFieldOption("MACD.macd", "MACD Line", "เช่น MACD.macd >= 0 (โซนบวก)"),
            AlertFieldOption("MACD.signal", "MACD Signal Line", ""),
            AlertFieldOption("BB.basis", "Bollinger Basis (เส้นกลาง)", ""),
            AlertFieldOption("ATR", "ATR (14)", "วัดความผันผวน เช่น ATR >= 25"),
            AlertFieldOption("ADX", "ADX (14)", "ความแรงของเทรนด์ เช่น ADX >= 25 (เทรนด์แรง)"),
            AlertFieldOption("Recommend.All", "Recommend Score (-1 ถึง 1)", "TV สรุปสัญญาณรวม เช่น >= 0.5 = Strong Buy zone"),
            AlertFieldOption("recommend_score", "Recommend Score (ทศนิยม)", "ค่าละเอียดของ Recommend.All"),
            AlertFieldOption("signal", "TV Signal (STRONG BUY..STRONG SELL)", "ใช้กับ == เช่น signal == STRONG SELL", isNumeric = false),
            AlertFieldOption("volume", "Volume", "")
            // ⚠️ ทดสอบจริง 2026-08-04: BB.width, buy_signals, sell_signals, neutral_signals
            // คืน null เสมอจาก scanner /symbol endpoint — ตัดออกจาก catalog
        )
    )

    val DEEP_SUITE = AlertToolOption(
        toolName = "trading_deep_analysis_suite",
        label = "วิเคราะห์เชิงลึก 5 มิติ (Deep Analysis Suite)",
        description = "สัญญาณระดับสถาบันครบ 5 มิติ: LSD Trend / Orderflow Delta / Fibo / Momentum Squeeze / Summary Score — เลือก TF ได้ด้วย suffix เช่น XAUUSD@15m (default 1h)",
        fields = listOf(
            AlertFieldOption("summaryScore", "Summary Score (0-100)", "Confluence รวม — >= 85 จะยิง High Confluence Alert อัตโนมัติ"),
            AlertFieldOption("lsdState", "LSD Trend State", "BULLISH/BEARISH/NEUTRAL — ใช้กับ == / contains เช่น contains BULL", isNumeric = false),
            AlertFieldOption("lsdConfluenceTF", "LSD Confluence TF (1-4)", "จำนวน TF ที่เห็นพ้อง — 4 = แข็งแกร่งสุด"),
            AlertFieldOption("deltaLabel", "Orderflow Delta Label", "STRONG BUYING / SELLING PRESSURE ฯลฯ — ใช้กับ == / contains", isNumeric = false),
            AlertFieldOption("deltaValue", "Orderflow Delta (ตัวเลข)", ">= 0 = แรงซื้อนำ | < 0 = แรงขายนำ"),
            AlertFieldOption("fiboScore", "Fibo Strength Score (0-10)", "คะแนน confluence ของระดับ Fibo ที่แข็งแกร่งสุด"),
            AlertFieldOption("momentum", "Momentum Signal", "EXPANSION / SQUEEZE / REVERSAL — ใช้กับ == / contains", isNumeric = false),
            AlertFieldOption("isSqueeze", "Bollinger Squeeze (0/1)", "1 = กำลัง squeeze รอ breakout"),
            AlertFieldOption("close", "Close — ราคาปัจจุบัน", "")
        )
    )

    val INDICATORS = AlertToolOption(
        toolName = "trading_indicators",
        label = "อินดิเคเตอร์จากแท่งเทียน (คำนวณเอง ⭐)",
        description = "คำนวณจากแท่งเทียน 300 แท่งในเครื่อง (แม่นกว่า scanner — มี EMA/Stoch/CCI/BB ครบ) — เปลี่ยน TF ได้ด้วย suffix เช่น XAUUSD@15m (default 1h)",
        fields = listOf(
            AlertFieldOption("close", "Close — ราคาปิดแท่งล่าสุด", ""),
            AlertFieldOption("ema20", "EMA 20", "เช่น close ทะลุ EMA20 → ตั้ง 2 alert เปรียบ close กับค่า ema20"),
            AlertFieldOption("ema50", "EMA 50", ""),
            AlertFieldOption("ema200", "EMA 200", ""),
            AlertFieldOption("ema_cross_state", "EMA Cross (50/200)", "GOLDEN_CROSS / DEATH_CROSS = เพิ่งตัดแท่งนี้ | BULLISH / BEARISH = โซนปัจจุบัน — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("ema50_200_spread", "EMA50-200 Spread", "ตัวเลข: >= 0 = โซน Golden, < 0 = โซน Death"),
            AlertFieldOption("ema20_50_spread", "EMA20-50 Spread", "ตัวเลข: >= 0 = แนวโน้มสั้นขาขึ้น"),
            AlertFieldOption("rsi14", "RSI (14)", "Overbought: >= 70 | Oversold: <= 30"),
            AlertFieldOption("macd", "MACD Line (12,26)", ""),
            AlertFieldOption("macd_signal", "MACD Signal (9)", ""),
            AlertFieldOption("macd_hist", "MACD Histogram", ">= 0 = โมเมนตัมบวก"),
            AlertFieldOption("stoch_k", "Stochastic %K (14)", ">= 80 = Overbought | <= 20 = Oversold"),
            AlertFieldOption("stoch_d", "Stochastic %D (3)", ""),
            AlertFieldOption("cci20", "CCI (20)", ">= 100 = แรงซื้อ | <= -100 = แรงขาย"),
            AlertFieldOption("bb_upper", "Bollinger Upper (20,2)", "ราคาแตะ upper → ระวังแรงขาย"),
            AlertFieldOption("bb_basis", "Bollinger Basis (20)", ""),
            AlertFieldOption("bb_lower", "Bollinger Lower (20,2)", "ราคาแตะ lower → ระวังแรงซื้อกลับ"),
            AlertFieldOption("bb_width", "Bollinger Width (%)", "ค่าต่ำ = squeeze รอ breakout | ค่าสูง = ผันผวนแรง"),
            AlertFieldOption("atr14", "ATR (14)", "วัดความผันผวน เช่น atr14 >= 25")
        )
    )

    val SMC = AlertToolOption(
        toolName = "trading_smc",
        label = "SMC Zones (Smart Money Concepts ⭐)",
        description = "โครงสร้างตลาด / Premium-Discount / Order Block / FVG / Liquidity — คำนวณจากแท่งเทียนในเครื่อง เลือก TF ได้ด้วย suffix เช่น XAUUSD@15m",
        fields = listOf(
            AlertFieldOption("close", "Close — ราคาปัจจุบัน", ""),
            AlertFieldOption("smc_zone", "SMC Zone", "PREMIUM / DISCOUNT / EQUILIBRIUM — ใช้กับ == เช่น smc_zone == DISCOUNT (รอซื้อโซนถูก)", isNumeric = false),
            AlertFieldOption("smc_zone_pct", "Zone % (0-100)", "ตำแหน่งราคาในโครงสร้าง — >= 80 = พรีเมียม, <= 20 = ดิสเคาน์"),
            AlertFieldOption("smc_trend", "Market Structure Trend", "BULLISH / BEARISH / NEUTRAL — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("smc_last_event", "Last Structure Event", "BOS_UP / BOS_DOWN / CHOCH_UP / CHOCH_DOWN — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("smc_structure_high", "Structure High", "ราคาแตะ → เสี่ยง sweep ฝั่งขาขึ้น"),
            AlertFieldOption("smc_structure_low", "Structure Low", ""),
            AlertFieldOption("smc_equilibrium", "Equilibrium (50%)", "เส้นกลางพรีเมียม/ดิสเคาน์"),
            AlertFieldOption("smc_premium_bot", "Premium Zone ขอบล่าง", "ราคา >= ค่านี้ = เข้าโซนพรีเมียม"),
            AlertFieldOption("smc_discount_top", "Discount Zone ขอบบน", "ราคา <= ค่านี้ = เข้าโซนดิสเคาน์"),
            AlertFieldOption("bull_ob_dist", "ระยะถึง Bullish OB", "ใกล้ 0 = ราคาถึงโซน demand แล้ว (หน่วยเป็นราคา)"),
            AlertFieldOption("bear_ob_dist", "ระยะถึง Bearish OB", "ใกล้ 0 = ราคาถึงโซน supply แล้ว"),
            AlertFieldOption("fvg_dist", "ระยะถึง FVG ใกล้สุด", "ใกล้ 0 = ราคาเข้าโซน imbalance"),
            AlertFieldOption("liq_above_dist", "ระยะถึง Liquidity เหนือราคา (EQH)", "ใกล้ 0 = ใกล้ sweep ฝั่งบน"),
            AlertFieldOption("liq_below_dist", "ระยะถึง Liquidity ใต้ราคา (EQL)", "ใกล้ 0 = ใกล้ sweep ฝั่งล่าง"),
            AlertFieldOption("liq_above_stars", "คะแนน EQH (0-5)", "ความแข็งแกร่งของ liquidity pool"),
            AlertFieldOption("liq_below_stars", "คะแนน EQL (0-5)", ""),
            AlertFieldOption("attack_force", "Attack Force (0/1)", "1 = พบแท่งโมเมนตัมแรง"),
            AlertFieldOption("atr", "ATR", "")
        )
    )

    val SENTIMENT = AlertToolOption(
        toolName = "trading_sentiment",
        label = "Reddit Sentiment (⚠️ ไม่เสถียร)",
        description = "อารมณ์ตลาดจาก Reddit — ⚠️ ช่วงนี้ Reddit มักบล็อก (HTTP 403) ทำให้ดึงค่าไม่ได้เป็นบางครั้ง",
        fields = listOf(
            AlertFieldOption("sentiment_score", "Sentiment Score (-1 ถึง 1)", "เช่น sentiment_score >= 0.3 (bullish ชัด)"),
            AlertFieldOption("bullish_posts", "Bullish Posts (จำนวน)", ""),
            AlertFieldOption("bearish_posts", "Bearish Posts (จำนวน)", ""),
            AlertFieldOption("posts_analyzed", "Posts Analyzed (จำนวน)", ""),
            AlertFieldOption("sentiment_label", "Sentiment Label", "ใช้กับ == เช่น sentiment_label == Bullish", isNumeric = false)
        )
    )

    val FEAR_GREED = AlertToolOption(
        toolName = "trading_fear_greed",
        label = "Fear & Greed Index (คริปโต)",
        description = "ดัชนีความกลัว/โลภของตลาดคริปโต (alternative.me)",
        fields = listOf(
            AlertFieldOption("value", "Fear & Greed (0-100)", "เช่น value <= 20 (Extreme Fear), value >= 80 (Extreme Greed)"),
            AlertFieldOption("classification", "Classification", "ใช้กับ == เช่น classification == Extreme Fear", isNumeric = false)
        )
    )

    val CRYPTO_GLOBAL = AlertToolOption(
        toolName = "trading_crypto_overview",
        label = "ภาพรวมตลาดคริปโต (CoinGecko)",
        description = "Market cap รวม / Dominance / Volume ของตลาดคริปโตทั้งระบบ",
        fields = listOf(
            AlertFieldOption("btc_dominance", "BTC Dominance (%)", "เช่น btc_dominance >= 60"),
            AlertFieldOption("eth_dominance", "ETH Dominance (%)", ""),
            AlertFieldOption("market_cap_change_24h", "Market Cap Change 24h (%)", "เช่น market_cap_change_24h <= -5 (ตลาดร่วง 5%)"),
            AlertFieldOption("total_market_cap_usd", "Total Market Cap (USD)", ""),
            AlertFieldOption("total_volume_24h_usd", "Total Volume 24h (USD)", ""),
            AlertFieldOption("active_cryptocurrencies", "Active Cryptocurrencies (จำนวน)", ""),
            AlertFieldOption("markets", "Markets (จำนวน)", "")
        )
    )

    val SMC_FLOW = AlertToolOption(
        toolName = "trading_smc_flow",
        label = "SMC Flow Signals (UT Bot + EMA14/60 + Confluence ⭐)",
        description = "port จาก TradingView 'SMC Flow System v2' — สัญญาณ BUY/SELL จาก UT Bot trigger, EMA 14/60 cross และ SMC Confluence (structure + OB/FVG + Fib golden zone) — เลือก TF ได้ด้วย suffix เช่น XAUUSD@15m",
        fields = listOf(
            AlertFieldOption("close", "Close — ราคาปัจจุบัน", ""),
            AlertFieldOption("smc_signal", "SMC Confluence Signal", "BUY / SELL / NONE — ใช้กับ == เช่น smc_signal == BUY (confluence ครบ: trigger + structure + zone)", isNumeric = false),
            AlertFieldOption("smc_recipes", "SMC Recipes ที่เข้า", "A=OB, B=FVG+OB, C=Fib golden — ใช้กับ contains เช่น contains C", isNumeric = false),
            AlertFieldOption("ema14_60_signal", "EMA 14/60 Cross Signal", "BUY / SELL / NONE — ใช้กับ == (ตัด + แท่งยืนยันแท่งนี้พอดี)", isNumeric = false),
            AlertFieldOption("ema14_60_cross", "EMA 14/60 Cross Event", "GOLDEN_CROSS / DEATH_CROSS / NONE — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("ema14_60_state", "EMA 14/60 State", "BULLISH (14 เหนือ 60) / BEARISH — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("ema14", "EMA 14", ""),
            AlertFieldOption("ema60", "EMA 60", ""),
            AlertFieldOption("ema14_60_spread", "EMA14-60 Spread", ">= 0 = โซนขาขึ้น"),
            AlertFieldOption("utbot_signal", "UT Bot Signal", "BUY / SELL / NONE — ใช้กับ == (ATR trailing stop flip)", isNumeric = false),
            AlertFieldOption("utbot_trend", "UT Bot Trend", "BULL / BEAR — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("utbot_stop", "UT Bot Trailing Stop", "ราคาหลุดเส้นนี้ = หมดเทรนด์"),
            AlertFieldOption("structure_bias", "Structure Bias (SMC)", "BULLISH / BEARISH / NEUTRAL — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("in_fib_golden", "อยู่ใน Fib Golden Zone (0/1)", "1 = ราคาอยู่ในโซน 0.618–0.786 ของ swing"),
            AlertFieldOption("fib_golden_top", "Fib Golden Zone ขอบบน", ""),
            AlertFieldOption("fib_golden_bottom", "Fib Golden Zone ขอบล่าง", ""),
            AlertFieldOption("fib_swing_high", "Swing High ปัจจุบัน", ""),
            AlertFieldOption("fib_swing_low", "Swing Low ปัจจุบัน", "")
        )
    )

    val STRATEGY = AlertToolOption(
        toolName = "trading_strategy_signal",
        label = "Strategy Signals (5 กลยุทธ์วิชาการ + Consensus ⭐)",
        description = "แปลงจาก Strategy Library (Quantpedia) มาคำนวณในเครื่อง — Time-Series Momentum, Trend Following (EMA50/200), Short-Term Reversal (RSI+BB), Donchian Breakout, 52W High proximity — เลือก TF ได้ด้วย suffix เช่น XAUUSD@15m",
        fields = listOf(
            AlertFieldOption("close", "Close — ราคาปัจจุบัน", ""),
            AlertFieldOption("consensus_signal", "Consensus Signal (รวม 5 กลยุทธ์)", "STRONG_BUY / BUY / NEUTRAL / SELL / STRONG_SELL — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("consensus_score", "Consensus Score", "-5 ถึง +5 (BUY=+1 ต่อกลยุทธ์) เช่น consensus_score >= 3"),
            AlertFieldOption("tsmom_signal", "Time-Series Momentum Signal", "BUY / SELL / NONE — ROC 20 แท่ง ใช้กับ ==", isNumeric = false),
            AlertFieldOption("tsmom_roc_pct", "TS Momentum ROC (%)", ">= 0 = โมเมนตัมขาขึ้น"),
            AlertFieldOption("trend_signal", "Trend Following Signal", "BUY / SELL / NONE — EMA50/200 + ราคาเทียบ EMA200 ใช้กับ ==", isNumeric = false),
            AlertFieldOption("trend_state", "Trend State", "UPTREND / DOWNTREND / RANGE — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("trend_ema50", "EMA 50", ""),
            AlertFieldOption("trend_ema200", "EMA 200", ""),
            AlertFieldOption("reversal_signal", "Short-Term Reversal Signal", "BUY / SELL / NONE — RSI สุดโต่ง + แตะ BB ใช้กับ ==", isNumeric = false),
            AlertFieldOption("reversal_rsi", "RSI (14)", "เช่น reversal_rsi <= 30"),
            AlertFieldOption("donchian_signal", "Donchian Breakout Signal", "BUY / SELL / NONE — ทะลุช่อง 20 แท่ง ใช้กับ ==", isNumeric = false),
            AlertFieldOption("donchian_upper", "Donchian Upper (High 20 แท่ง)", "ทะลุเส้นนี้ = breakout ขึ้น"),
            AlertFieldOption("donchian_lower", "Donchian Lower (Low 20 แท่ง)", "หลุดเส้นนี้ = breakout ลง"),
            AlertFieldOption("donchian_mid", "Donchian Mid", ""),
            AlertFieldOption("w52_signal", "52W High Signal", "BUY (ใกล้จุดสูงสุด ≥98%) / SELL (หลุด ≤90%) / NONE — ใช้กับ ==", isNumeric = false),
            AlertFieldOption("w52_high", "Period High", "จุดสูงสุดของข้อมูลที่มี"),
            AlertFieldOption("w52_proximity_pct", "Proximity to High (%)", "100 = อยู่ที่จุดสูงสุดพอดี")
        )
    )

    val SIGNAL_ALERT = AlertToolOption(
        toolName = "trading_signal_alert",
        label = "📡 Signal Alert (สัญญาณเทรดใหม่ 8 กลยุทธ์ + SMC ⭐)",
        description = "ตรวจสัญญาณ BUY/SELL ที่ 'เพิ่งเกิด' ในแท่งปิดล่าสุดจาก 8 กลยุทธ์ (Momentum / Trend EMA50-200 / Reversal / Donchian / 52W High / EMA14-60 / UT Bot / 3-Bar Reversal) + SMC Engine จาก mt5-core-server (OB Bounce / CHoCH / SMS-BMS / FVG Fill / Liquidity Sweep / RSI Divergence — SL/TP ตามโครงสร้างตลาด + confluence stars) — พร้อม Entry/SL/TP เฉพาะกลยุทธ์ เลือก TF ด้วย suffix เช่น XAUUSD@15m",
        fields = listOf(
            AlertFieldOption("signal_buy", "สัญญาณ BUY ใหม่ (1=เกิด)", "ตั้ง signal_buy >= 1 → ระบบแปลงเป็นเฝ้าเฉพาะสัญญาณ BUY ที่เกิด 'หลัง' สร้าง alert อัตโนมัติ (ไม่เด้งจากสัญญาณเก่า)"),
            AlertFieldOption("signal_sell", "สัญญาณ SELL ใหม่ (1=เกิด)", "ตั้ง signal_sell >= 1 → ระบบแปลงเป็นเฝ้าเฉพาะสัญญาณ SELL ที่เกิด 'หลัง' สร้าง alert อัตโนมัติ (ไม่เด้งจากสัญญาณเก่า)"),
            AlertFieldOption("signal_event", "Signal Event", "BUY / SELL / NONE — ใช้กับ == (สร้าง 2 job เพื่อครบทั้งสองฝั่ง)", isNumeric = false),
            AlertFieldOption("signal_strategy", "กลยุทธ์ที่เกิดสัญญาณ", "ข้อความ เช่น 'UT Bot' — ใช้กับ contains", isNumeric = false),
            AlertFieldOption("signal_entry", "ราคาจุดเข้าของสัญญาณ", "ค่าของสัญญาณล่าสุด (informational)"),
            AlertFieldOption("signal_sl", "Stop Loss ของสัญญาณ", "คำนวณเฉพาะกลยุทธ์ (informational)"),
            AlertFieldOption("signal_tp", "Take Profit ของสัญญาณ", "คำนวณเฉพาะกลยุทธ์ (informational)"),
            AlertFieldOption("signal_rr", "Risk:Reward ของสัญญาณ", "informational")
        )
    )

    val tools: List<AlertToolOption> = listOf(INDICATORS, SMC, SMC_FLOW, STRATEGY, SIGNAL_ALERT, PRICE, TA, DEEP_SUITE, SENTIMENT, FEAR_GREED, CRYPTO_GLOBAL)

    fun toolFor(toolName: String): AlertToolOption? = tools.firstOrNull { it.toolName == toolName }

    fun isFieldSupported(toolName: String, field: String): Boolean =
        toolFor(toolName)?.fields?.any { it.field.equals(field, ignoreCase = true) } == true

    /** true = ฟิลด์ตัวเลข ใช้ operator >, <, >=, <= ได้; false = ข้อความ ใช้ได้เฉพาะ == / contains */
    fun isNumericField(toolName: String, field: String): Boolean =
        toolFor(toolName)?.fields?.firstOrNull { it.field.equals(field, ignoreCase = true) }?.isNumeric ?: true

    /** คำอธิบายสำหรับ AI tool description — แจงชัดว่าตั้งเงื่อนไขอะไรได้บ้าง */
    fun describeForAi(): String = tools.joinToString("\n") { t ->
        "- ${t.toolName}: " + t.fields.joinToString(", ") { it.field }
    }
}
