package com.example.personalaibot.automation.backtest

/**
 * BacktestModels — โครงข้อมูลผล backtest (port จาก OLD_Code/ai-trading-agent backtest/engine.py)
 */

data class BacktestConfig(
    val initialBalance: Double = 10_000.0,
    val riskPerTradePct: Double = 0.01,   // เสี่ยง 1% ของพอร์ตต่อไม้
    val maxLeverage: Double = 10.0,       // จำกัด notional ไม่เกิน 10x พอร์ต
    val includeCosts: Boolean = true,
    val spreadPrice: Double = 0.2,        // spread ครึ่งเดียวถูกหักตอนเข้า (หน่วยราคาของ symbol)
    val commissionPct: Double = 0.0,      // ค่าคอมต่อข้าง — CFD/forex (เช่น XAUUSD) ต้นทุนอยู่ใน spread แล้ว จึง default 0
                                          // (เดิม 4.5 bps จาก moss ซึ่งเป็นสเกล crypto — กับทอง ~4380 กลายเป็น ~0.3-0.6R/ไม้ บิดผล backtest หนัก)
    val maxEquityPoints: Int = 240        // downsample equity curve ให้พอดู
)

data class BacktestTrade(
    val kind: String,
    val strategyName: String,
    val side: String,          // BUY | SELL
    val entryTime: Long,
    val entryPrice: Double,
    val exitTime: Long,
    val exitPrice: Double,
    val sl: Double,
    val tp: Double,
    val exitReason: String,    // TP | SL | TIMEOUT
    val pnlR: Double,          // กำไร/ขาดทุนเป็น R multiple (เทียบระยะเสี่ยง)
    val pnlMoney: Double       // กำไร/ขาดทุนสุทธิหลังหักต้นทุน
)

data class StrategyBacktestStats(
    val kind: String,
    val name: String,
    val signals: Int,          // สัญญาณทั้งหมดที่พบ
    val taken: Int,            // ไม้ที่ได้เข้าจริง (ไม่มีไม้ค้าง)
    val skipped: Int,          // สัญญาณที่ข้ามเพราะมีไม้ค้างอยู่
    val wins: Int,
    val losses: Int,
    val timeouts: Int,
    val winRate: Double,       // wins / taken
    val avgR: Double,
    val totalR: Double,
    val profitFactor: Double
)

data class BacktestResult(
    val symbol: String,
    val interval: String,
    val source: String,
    val bars: Int,
    val fromTs: Long,
    val toTs: Long,
    val config: BacktestConfig,
    // ภาพรวม
    val finalBalance: Double,
    val totalReturnPct: Double,
    val totalTrades: Int,
    val skippedSignals: Int,
    val wins: Int,
    val losses: Int,
    val timeouts: Int,
    val winRate: Double,
    val profitFactor: Double,
    val expectancyR: Double,
    val maxDrawdownPct: Double,
    val sharpe: Double,
    // รายละเอียด
    val perStrategy: List<StrategyBacktestStats>,
    val trades: List<BacktestTrade>,
    val equityCurve: List<Double>,
    // Reproducibility metadata. Optional defaults preserve existing call sites.
    val runId: String = "",
    val datasetId: String = "",
    val strategyVersion: String = "",
    val parameterHash: String = "",
    val engineVersion: String = ""
)
