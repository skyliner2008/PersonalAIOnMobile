package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * BacktestEngine — จำลองเทรด bar-by-bar (port จาก OLD_Code/ai-trading-agent backtest/engine.py)
 * ปรับให้เข้ากับระบบ Signal ของแอป:
 *  - สัญญาณจาก SignalMarkerProvider.compute() (8 กลยุทธ์ edge-triggered)
 *  - SL/TP จาก SignalAlertProvider.computeTpSl() (สูตรเฉพาะกลยุทธ์ ตัวเดียวกับ live alert)
 *  - กติกาสอดคล้อง fetchStats เดิม: เข้าที่ราคาปิดแท่งสัญญาณ, แท่งชนทั้ง SL+TP ถือว่าแพ้ (SL ก่อน),
 *    ค้างถึงแท่งสุดท้าย = TIMEOUT ปิดที่ราคาปิดสุดท้าย
 *  - ถือได้ทีละ 1 ไม้ (สัญญาณที่มาระหว่างมีไม้ค้าง = skipped) + คิดต้นทุน spread/commission
 *  - Money model: เสี่ยงคงที่ riskPerTradePct ของพอร์ตต่อไม้, จำกัดเลเวอเรจ maxLeverage
 */
class BacktestEngine {

    private class OpenPos(
        val kind: String,
        val side: String,
        val entryTime: Long,
        val entry: Double,
        val sl: Double,
        val tp: Double,
        val qty: Double,
        val riskAmount: Double,
        val entryCommission: Double
    ) {
        val isBuy get() = side == "BUY"
        fun grossPnl(price: Double): Double =
            qty * (if (isBuy) price - entry else entry - price)
    }

    fun run(
        symbol: String,
        interval: String,
        source: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        kindFilter: Set<String>,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        tpSl: (kind: String, side: String, candles: List<Candle>, i: Int, atr14: Double, atr6: Double) -> Pair<Double, Double>,
        config: BacktestConfig = BacktestConfig(),
        startIndex: Int = 0   // เริ่มจำลองจากแท่งนี้ (สำหรับ walk-forward OOS: indicator warm จาก prefix แต่เทรดเฉพาะช่วงทดสอบ)
    ): BacktestResult {
        val n = candles.size
        require(n >= 62) { "แท่งเทียนไม่พอ ($n < 62)" }
        val startBar = startIndex.coerceIn(1, n - 2)

        // ── เตรียมสัญญาณ: map เวลา → index ──
        val timeToIdx = HashMap<Long, Int>(n)
        for (i in 0 until n) timeToIdx[candles[i].timestamp] = i
        val signalsByBar = HashMap<Int, MutableList<SignalMarkerProvider.SignalMarker>>()
        for (m in markers) {
            val kind = kindOf(m.label)
            if (kind !in kindFilter) continue
            val idx = timeToIdx[m.time] ?: continue
            if (idx < startBar || idx >= n - 1) continue // นอกช่วงจำลอง / แท่งสุดท้ายไม่มีอนาคตให้จำลอง
            signalsByBar.getOrPut(idx) { mutableListOf() }.add(m)
        }

        val atr14S = atrSeries(candles, 14)
        val atr6S = atrSeries(candles, 6)

        // ── จำลอง bar-by-bar ──
        var balance = config.initialBalance
        val equity = ArrayList<Double>(n)
        equity.add(balance)
        val trades = mutableListOf<BacktestTrade>()
        var open: OpenPos? = null

        val sigCount = HashMap<String, Int>()
        val skipCount = HashMap<String, Int>()

        for (bar in startBar until n) {
            val row = candles[bar]

            // 1) จัดการไม้ที่ค้างอยู่ (SL ก่อน TP — conservative)
            open?.let { t ->
                val hitSl = if (t.isBuy) row.low <= t.sl else row.high >= t.sl
                val hitTp = if (t.isBuy) row.high >= t.tp else row.low <= t.tp
                val isLast = bar == n - 1
                if (hitSl || hitTp || isLast) {
                    val exitPrice = when {
                        hitSl -> t.sl
                        hitTp -> t.tp
                        else -> row.close
                    }
                    val reason = when {
                        hitSl -> "SL"
                        hitTp -> "TP"
                        else -> "TIMEOUT"
                    }
                    val exitCommission = if (config.includeCosts) t.qty * exitPrice * config.commissionPct else 0.0
                    val pnl = t.grossPnl(exitPrice) - t.entryCommission - exitCommission
                    balance += pnl
                    val pnlR = if (t.riskAmount > 0) pnl / t.riskAmount else 0.0
                    trades += BacktestTrade(
                        kind = t.kind, strategyName = strategyName(t.kind), side = t.side,
                        entryTime = t.entryTime, entryPrice = t.entry,
                        exitTime = row.timestamp, exitPrice = exitPrice,
                        sl = t.sl, tp = t.tp, exitReason = reason,
                        pnlR = pnlR, pnlMoney = pnl
                    )
                    open = null
                }
            }

            // 2) สัญญาณใหม่ที่แท่งนี้ (เข้าที่ราคาปิด — ตัวเดียวกับ fetchStats)
            signalsByBar[bar]?.forEach { m ->
                val kind = kindOf(m.label)
                sigCount[kind] = (sigCount[kind] ?: 0) + 1
                if (open != null) {
                    skipCount[kind] = (skipCount[kind] ?: 0) + 1
                    return@forEach
                }
                var entry = row.close
                if (config.includeCosts) {
                    val half = config.spreadPrice * 0.5
                    entry += if (m.side == "BUY") half else -half
                }
                val (sl, tp) = tpSl(kind, m.side, candles, bar, atr14S[bar], atr6S[bar])
                // กันพลาด: SL/TP ผิดฝั่งกับทิศทาง (เช่น structure level ข้ามฝั่ง) = สัญญาณเสีย ข้าม
                val directionValid = if (m.side == "BUY") sl < entry && tp > entry else sl > entry && tp < entry
                if (!directionValid) return@forEach
                val slDist = abs(entry - sl)
                if (slDist <= 0) return@forEach
                val riskAmount = balance * config.riskPerTradePct
                var qty = riskAmount / slDist
                // จำกัดเลเวอเรจ: notional ≤ balance × maxLeverage
                val maxQty = if (entry > 0) balance * config.maxLeverage / entry else qty
                if (qty > maxQty) qty = maxQty
                val entryCommission = if (config.includeCosts) qty * entry * config.commissionPct else 0.0
                // หมายเหตุ: ห้าม balance -= entryCommission ตรงนี้ — commission ขาเข้าถูกหักใน pnl ตอนปิดไม้แล้ว
                // (เดิมหักซ้ำ 2 รอบ ทำ equity curve/balance ต่ำกว่าความจริง และไม่ตรง finalBalance)
                open = OpenPos(kind, m.side, row.timestamp, entry, sl, tp, qty, riskAmount, entryCommission)
            }

            // 3) equity mark-to-market
            val unrealized = open?.let { it.grossPnl(row.close) - it.entryCommission } ?: 0.0
            equity.add(balance + unrealized)
        }

        return buildResult(symbol, interval, source, candles, config, trades, equity, sigCount, skipCount, strategyName)
    }

    private fun buildResult(
        symbol: String, interval: String, source: String, candles: List<Candle>,
        config: BacktestConfig, trades: List<BacktestTrade>, equity: List<Double>,
        sigCount: Map<String, Int>, skipCount: Map<String, Int>,
        strategyName: (String) -> String
    ): BacktestResult {
        val wins = trades.filter { it.pnlMoney > 0 }
        val losses = trades.filter { it.pnlMoney <= 0 }
        val grossWin = wins.sumOf { it.pnlMoney }
        val grossLoss = abs(losses.sumOf { it.pnlMoney })
        val finalBalance = config.initialBalance + trades.sumOf { it.pnlMoney }

        // Max drawdown จาก equity curve
        var peak = equity.firstOrNull() ?: config.initialBalance
        var maxDd = 0.0
        for (v in equity) {
            if (v > peak) peak = v
            val dd = if (peak > 0) (peak - v) / peak else 0.0
            if (dd > maxDd) maxDd = dd
        }

        // Sharpe แบบง่าย (per-bar returns × √252 — สูตรเดียวกับ engine.py ต้นฉบับ)
        val returns = ArrayList<Double>(equity.size - 1)
        for (i in 1 until equity.size) {
            val prev = equity[i - 1]
            returns.add(if (prev > 0) (equity[i] - prev) / prev else 0.0)
        }
        val sharpe = if (returns.size > 1) {
            val mean = returns.average()
            val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
            val std = sqrt(variance)
            if (std > 0) mean / std * sqrt(252.0) else 0.0
        } else 0.0

        // แยกตามกลยุทธ์
        val perStrategy = trades.groupBy { it.kind }.map { (kind, list) ->
            val w = list.filter { it.pnlMoney > 0 }
            val l = list.filter { it.pnlMoney <= 0 }
            val gw = w.sumOf { it.pnlMoney }
            val gl = abs(l.sumOf { it.pnlMoney })
            StrategyBacktestStats(
                kind = kind, name = strategyName(kind),
                signals = sigCount[kind] ?: list.size,
                taken = list.size,
                skipped = skipCount[kind] ?: 0,
                wins = w.size, losses = l.size,
                timeouts = list.count { it.exitReason == "TIMEOUT" },
                winRate = if (list.isNotEmpty()) w.size.toDouble() / list.size else 0.0,
                avgR = if (list.isNotEmpty()) list.sumOf { it.pnlR } / list.size else 0.0,
                totalR = list.sumOf { it.pnlR },
                profitFactor = if (gl > 0) gw / gl else if (gw > 0) 99.0 else 0.0
            )
        }.sortedByDescending { it.avgR }

        // downsample equity curve
        val maxPts = config.maxEquityPoints
        val curve = if (equity.size <= maxPts) equity
        else {
            val step = equity.size.toDouble() / maxPts
            (0 until maxPts).map { equity[(it * step).toInt().coerceAtMost(equity.size - 1)] } + equity.last()
        }

        return BacktestResult(
            symbol = symbol, interval = interval, source = source,
            bars = candles.size,
            fromTs = candles.first().timestamp, toTs = candles.last().timestamp,
            config = config,
            finalBalance = finalBalance,
            totalReturnPct = (finalBalance - config.initialBalance) / config.initialBalance * 100.0,
            totalTrades = trades.size,
            skippedSignals = skipCount.values.sum(),
            wins = wins.size, losses = losses.size,
            timeouts = trades.count { it.exitReason == "TIMEOUT" },
            winRate = if (trades.isNotEmpty()) wins.size.toDouble() / trades.size else 0.0,
            profitFactor = if (grossLoss > 0) grossWin / grossLoss else if (grossWin > 0) 99.0 else 0.0,
            expectancyR = if (trades.isNotEmpty()) trades.sumOf { it.pnlR } / trades.size else 0.0,
            maxDrawdownPct = maxDd * 100.0,
            sharpe = sharpe,
            perStrategy = perStrategy,
            trades = trades,
            equityCurve = curve
        )
    }

    // ─── ATR (สูตรเดียวกับ SignalMarkerProvider/SignalAlertProvider — คงผลให้ตรงกัน) ───

    private fun atrSeries(candles: List<Candle>, period: Int): DoubleArray {
        val out = DoubleArray(candles.size)
        if (candles.size <= period) return out
        var atr = 0.0
        for (i in 1..period) {
            val c = candles[i]; val p = candles[i - 1]
            atr += maxOf(c.high - c.low, abs(c.high - p.close), abs(c.low - p.close))
        }
        atr /= period
        out[period] = atr
        for (i in period + 1 until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            val tr = maxOf(c.high - c.low, abs(c.high - p.close), abs(c.low - p.close))
            atr = (atr * (period - 1) + tr) / period
            out[i] = atr
        }
        return out
    }
}
