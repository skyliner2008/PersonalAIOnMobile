package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.math.min

/**
 * ParamOptimizer — grid search บน TpSlParams (port จาก OLD_Code backtest/optimizer.py)
 *
 * Objective (ปรับ 2026-08-19 — เดิม Sharpe × PF(cap10) × WinRate ซึ่งไม่มี expectancy/DD
 * และคูณปัจจัยที่ correlate กันซ้ำซ้อน ทำ optimizer เลือกผิดตัว):
 *
 *   hard constraints (ตกทันที): trades < minTrades | PF < 1.0 | expectancyR ≤ 0 | DD > 35%
 *   score = expectancyR × pfWeight × sharpeWeight × ddPenalty × tradeConfidence
 *
 * expectancy เป็นตัวตั้ง (คำตอบตรงๆ ว่าไม้นึงได้กี่ R) ส่วน PF/Sharpe/DD/จำนวนไม้
 * เป็นน้ำหนักประกอบ ไม่เอามาคูณแข่งกับ expectancy ตรงๆ
 */
object ParamOptimizer {

    /** hard constraint: กำไรสุทธิต้องมีจริง และ DD ต้องไม่ลึกเกินรับได้ */
    const val HARD_MIN_PF = 1.0
    const val HARD_MAX_DD_PCT = 35.0

    data class ComboResult(
        val params: TpSlParams,
        val score: Double,
        val trades: Int,
        val winRate: Double,
        val profitFactor: Double,
        val sharpe: Double,
        val maxDrawdownPct: Double,
        val totalReturnPct: Double,
        val expectancyR: Double
    )

    fun score(r: BacktestResult, minTrades: Int = 5): Double {
        // ── hard constraints: แพ้ข้อใดข้อหนึ่งตกทันที ──
        if (r.totalTrades < minTrades) return -999.0
        if (r.profitFactor < HARD_MIN_PF) return -999.0
        if (r.expectancyR <= 0) return -999.0
        if (r.maxDrawdownPct > HARD_MAX_DD_PCT) return -999.0

        // ── expectancy-first objective ──
        val pfW = 0.5 + 0.5 * (min(r.profitFactor, 3.0) / 3.0)              // 0.67–1.0 (PF ≥3 ถือว่าพอ ไม่ให้ PF สูงเวอร์ครอบงำ)
        val sharpeW = 1.0 + (r.sharpe.coerceIn(-2.0, 3.0) / 3.0) * 0.3      // 0.8–1.3
        val ddW = 1.0 / (1.0 + r.maxDrawdownPct / 20.0)                     // DD 20% → หักครึ่งนึง
        val tradeW = min(1.0, r.totalTrades / 20.0)                         // ไม้น้อย = หลักฐานน้อย คะแนนหดตาม
        return r.expectancyR * pfW * sharpeW * ddW * tradeW
    }


    /** Stable guard used by Optimize/Evolve: a candidate must satisfy hard constraints before it can be promoted. */
    fun isEligible(r: BacktestResult, minTrades: Int = 5): Boolean =
        r.totalTrades >= minTrades &&
            r.profitFactor >= HARD_MIN_PF &&
            r.expectancyR > 0.0 &&
            r.maxDrawdownPct <= HARD_MAX_DD_PCT

    /**
     * รัน backtest ทุก combo ใน grid สำหรับ kind เดียว แล้วจัดอันดับจาก score
     * markers ต้องคำนวณมาแล้ว (ชุดเดียวกับ candles) — engine จะ filter เฉพาะ kind นี้
     */
    fun gridSearch(
        kind: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        config: BacktestConfig = BacktestConfig(),
        minTrades: Int = 5
    ): List<ComboResult> {
        val results = mutableListOf<ComboResult>()
        for (params in TpSlParams.grid()) {
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = candles, markers = markers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, s, c, i, a14, a6 -> parameterizedTpSl(k, s, c, i, a14, a6, params) },
                    config = config
                )
            }.getOrNull() ?: continue
            results += ComboResult(
                params = params,
                score = score(r, minTrades),
                trades = r.totalTrades,
                winRate = r.winRate,
                profitFactor = r.profitFactor,
                sharpe = r.sharpe,
                maxDrawdownPct = r.maxDrawdownPct,
                totalReturnPct = r.totalReturnPct,
                expectancyR = r.expectancyR
            )
        }
        return results
            .sortedWith(compareByDescending<ComboResult> { it.score }
                .thenByDescending { it.expectancyR }
                .thenBy { it.maxDrawdownPct }
                .thenByDescending { it.totalReturnPct })
    }
}
