package com.example.personalaibot.tools.trading.auto

/**
 * ─── Types สำหรับ Auto-Trading Engine แบบ modular ───────────────────────────
 *
 * ตระกูลข้อมูลกลางที่ Analyzer, Strategy, RiskManager, และ Engine ใช้ร่วมกัน
 *
 *   Analyzer → AnalyzerReport (แต่ละคน)     ─┐
 *                                              ├→ CompositeAnalysis → Strategy → TradePlan → RiskManager.size/gate → Engine executes
 *   MarketRegime (จาก regime detector)      ─┘
 */

// ════════════════════════════════════════════════════════════════════════════
// Analyzer output
// ════════════════════════════════════════════════════════════════════════════

/** ทิศทางของสัญญาณ */
enum class Bias { BULL, BEAR, NEUTRAL }

/** "ความรุนแรง" ของสัญญาณ (ใช้คำนวณ weight ใน confluence) */
enum class Strength { WEAK, MEDIUM, STRONG }

/** หมวดหมู่ของ Analyzer — ช่วย UI/Journal จัดระเบียบและ Learn วิเคราะห์ */
enum class AnalyzerKind(val code: String, val thai: String) {
    TECHNICAL("TA", "Technical Analysis"),
    FUNDAMENTAL("FA", "Fundamental Analysis"),
    PRICE_ACTION("PA", "Price Action"),
    QUANT("QT", "Quant/Algorithmic"),
    TREND_FOLLOWING("TF", "Trend Following"),
    MEAN_REVERSION("MR", "Mean Reversion"),
    BREAKOUT("BO", "Breakout"),
    RANGE("RG", "Range Trading"),
    SMC("SMC", "Smart Money Concepts"),
    SENTIMENT("SENT", "Market Sentiment"),
    CUSTOM("CUSTOM", "Custom")
}

/** หนึ่งสัญญาณจาก analyzer (ลงรายละเอียด) */
data class AnalysisSignal(
    val name: String,          // eg "RSI<30", "BOS_UP", "EMA20>EMA50"
    val bias: Bias,
    val strength: Strength,
    val evidence: String,      // อธิบายชัด ๆ ใช้แสดง UI + ลง journal
)

/** ผลรวมจากหนึ่ง analyzer */
data class AnalyzerReport(
    val kind: AnalyzerKind,
    val signals: List<AnalysisSignal>,
    val bias: Bias,                      // bias สรุปของ analyzer ตัวเอง
    val strengthScore: Double,           // 0..100
    val notes: String = ""
)

// ════════════════════════════════════════════════════════════════════════════
// Regime & Composite
// ════════════════════════════════════════════════════════════════════════════

enum class MarketRegime(val thai: String) {
    TRENDING_UP("เทรนด์ขาขึ้น"),
    TRENDING_DOWN("เทรนด์ขาลง"),
    RANGING("ไซด์เวย์"),
    VOLATILE_BREAKOUT("ผันผวนสูง/อาจทะลุ"),
    QUIET("เงียบ/ไม่มีโมเมนตัม"),
    UNKNOWN("ไม่ทราบ")
}

/** รวมผลจากทุก analyzer + ข้อมูลตลาดพื้นฐาน */
data class CompositeAnalysis(
    val symbol: String,
    val timeframe: String,
    val price: Double?,
    val reports: List<AnalyzerReport>,
    val regime: MarketRegime,
    val atr: Double?,            // Average True Range — ใช้คำนวณ SL
    val dailyRangePct: Double?,  // กรอบวัน (% ของ price)
    val rawBlobs: Map<String, String> = emptyMap() // เก็บ raw text จาก tool ไว้ debug
) {
    val bullVotes: Int get() = reports.count { it.bias == Bias.BULL }
    val bearVotes: Int get() = reports.count { it.bias == Bias.BEAR }
    val neutralVotes: Int get() = reports.count { it.bias == Bias.NEUTRAL }

    val overallBias: Bias
        get() = when {
            // ≥1 เพียงพอ — ≥2 เข้มเกินสำหรับตลาดที่ analyzer หลายตัวเป็น NEUTRAL
            // (news/sentiment/macro มักไม่มีสัญญาณชัด) ทำให้ net votes ไม่ถึง 2
            bullVotes - bearVotes >= 1 -> Bias.BULL
            bearVotes - bullVotes >= 1 -> Bias.BEAR
            else -> Bias.NEUTRAL
        }

    /** confluence score 0..100 รวมน้ำหนักจากทุก analyzer */
    val confluenceScore: Double
        get() {
            if (reports.isEmpty()) return 0.0
            val aligned = reports.filter { it.bias == overallBias }
            val avgStrength = aligned.map { it.strengthScore }.average().takeIf { !it.isNaN() } ?: 0.0
            val coverage = aligned.size.toDouble() / reports.size
            return (avgStrength * 0.7 + coverage * 100 * 0.3).coerceIn(0.0, 100.0)
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Strategy output
// ════════════════════════════════════════════════════════════════════════════

enum class StrategyType(val thai: String) {
    SCALPING("Scalp สั้น"),
    SWING("Swing กลาง-ยาว"),
    GRID("Grid/DCA"),
    TRAILING("Trailing Stop"),
    TREND_FOLLOW("Trend Following"),
    MEAN_REVERSION("Mean Reversion"),
    BREAKOUT("Breakout"),
    RANGE("Range Trading"),
    HOLD_CASH("พักดูตลาด")
}

/** การเข้าหนึ่งไม้ (หรือชุด grid) ที่ strategy เสนอ */
data class TradePlan(
    val decisionId: String,
    val symbol: String,
    val timeframe: String,
    val strategy: StrategyType,
    val side: String,                  // BUY | SELL | SKIP
    val entry: Double?,
    val sl: Double?,
    val tp: Double?,
    val tpLegs: List<Double> = emptyList(), // สำหรับ grid/partial-TP
    val volume: Double?,
    val trailingDistance: Double? = null,   // สำหรับ trailing strategy
    val gridLevels: List<Double> = emptyList(), // สำหรับ grid strategy
    val rationale: String,
    val expectedRR: Double,
    val holdingHint: String            // "< 1h", "4-12h", "days"
)

// ════════════════════════════════════════════════════════════════════════════
// Account / Risk
// ════════════════════════════════════════════════════════════════════════════

data class AccountSnapshot(
    val balance: Double,
    val equity: Double,
    val margin: Double,
    val freeMargin: Double,
    val currency: String,
    val openPositions: Int,
    val todayPnL: Double
) {
    val drawdownPct: Double
        get() = if (balance > 0) (balance - equity).coerceAtLeast(0.0) / balance * 100.0 else 0.0
    val marginLevel: Double
        get() = if (margin > 0) equity / margin * 100.0 else Double.POSITIVE_INFINITY
}

data class RiskGate(
    val allowed: Boolean,
    val reason: String,
    val suggestedVolume: Double? = null,
    val riskPct: Double? = null
)

// ════════════════════════════════════════════════════════════════════════════
// Decision record for Journal
// ════════════════════════════════════════════════════════════════════════════

enum class TradeOutcome { OPEN, WIN, LOSS, BE, PAPER, CANCELLED }

data class DecisionRecord(
    val decisionId: String,
    val symbol: String,
    val timeframe: String,
    val side: String,
    val strategy: StrategyType,
    val analyzersUsed: List<AnalyzerKind>,
    val signalsJson: String,
    val confluenceScore: Double,
    val entry: Double?,
    val sl: Double?,
    val tp: Double?,
    val volume: Double?,
    val riskPct: Double?,
    val rrr: Double,
    val regime: MarketRegime,
    val marketSnapshot: String,
    val wasExecuted: Boolean,
    val mt5Ticket: Long?,
    val outcome: TradeOutcome,
    val createdAt: Long
)
