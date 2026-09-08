package com.example.personalaibot.automation

import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.db.JarvisDatabaseHolder
import com.example.personalaibot.db.SignalTrackingRecord
import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.Candle
import kotlinx.datetime.Clock
import kotlin.jvm.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * SignalOutcomeTracker — ติดตามผลลัพธ์ของ Signal Alert ที่ถูกปล่อยออกไป (Closed-Loop Learning)
 * บันทึกลง SQLite และคำนวณผลจริงเทียบกับราคาตลาด (Hit TP, Hit SL, MFE, MAE, R-multiple)
 * พร้อมคำนวณ Confidence Adjustment ให้กลยุทธ์นำไปปรับน้ำหนักตัวเองอัตโนมัติ
 */
object SignalOutcomeTracker {

    @Volatile
    private var testDatabase: JarvisDatabase? = null

    /** กำหนด database สำหรับการทดสอบ (Mock/In-memory) */
    fun setTestDatabase(db: JarvisDatabase?) {
        testDatabase = db
    }

    private fun getDb(): JarvisDatabase? = testDatabase ?: JarvisDatabaseHolder.database

    data class StrategyPerformance(
        val strategy: String,
        val totalSignals: Long,
        val wins: Long,
        val losses: Long,
        val winRatePct: Double,
        val avgR: Double,
        val avgMfeR: Double,
        val avgMaeR: Double
    )

    /** บันทึกสัญญาณที่เพิ่งเกิดสถานะ CONFIRMED ลงฐานข้อมูล */
    fun recordSignal(
        signalId: String,
        symbol: String,
        interval: String,
        strategy: String,
        side: String,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        rr: Double,
        createdAt: Long = Clock.System.now().toEpochMilliseconds()
    ) {
        val db = getDb() ?: return
        val risk = abs(entryPrice - stopLoss)
        if (risk <= 0.0 || entryPrice <= 0.0) return

        // ป้องกัน overwrite สัญญาณเดิมที่บันทึกแล้ว (ไม่ reset status/mfe/bars_held และไม่ spam log)
        val existing = runCatching {
            db.jarvisDatabaseQueries.getSignalTrackingRecordById(signalId).executeAsOneOrNull()
        }.getOrNull()
        if (existing != null) return

        runCatching {
            db.jarvisDatabaseQueries.insertSignalTrackingRecord(
                signal_id = signalId,
                symbol = symbol.uppercase(),
                interval = interval.lowercase(),
                strategy = strategy,
                side = side.uppercase(),
                entry_price = entryPrice,
                stop_loss = stopLoss,
                take_profit = takeProfit,
                rr = rr,
                status = "OPEN",
                mfe = 0.0,
                mae = 0.0,
                exit_price = null,
                pnl_r = null,
                bars_held = 0L,
                created_at = createdAt,
                closed_at = null
            )
            logDebug("SignalTracker", "Recorded signal $signalId: $symbol $interval $side $strategy @ $entryPrice (SL: $stopLoss, TP: $takeProfit)")
        }.onFailure {
            logDebug("SignalTracker", "Failed to record signal $signalId: ${it.message}")
        }
    }

    data class OutcomeEvaluation(
        val status: String,
        val exitPrice: Double?,
        val pnlR: Double?,
        val mfeR: Double,
        val maeR: Double,
        val barsHeld: Long,
        val closedAt: Long?
    )

    /** ฟังก์ชันทดสอบผลลัพธ์ของ 1 สัญญาณแบบ Pure Function */
    fun evaluateSingleSignal(
        side: String,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        rr: Double,
        createdAt: Long,
        forwardCandles: List<Candle>
    ): OutcomeEvaluation {
        val isBuy = side.equals("BUY", ignoreCase = true)
        val risk = abs(entryPrice - stopLoss)
        if (risk <= 0.0 || forwardCandles.isEmpty()) {
            return OutcomeEvaluation("OPEN", null, null, 0.0, 0.0, 0L, null)
        }

        var status = "OPEN"
        var exitPrice: Double? = null
        var pnlR: Double? = null
        var maxFavPrice = entryPrice
        var maxAdvPrice = entryPrice
        val barsHeld = forwardCandles.size.toLong()
        var closedAt: Long? = null

        for (candle in forwardCandles) {
            if (isBuy) {
                maxFavPrice = max(maxFavPrice, candle.high)
                maxAdvPrice = min(maxAdvPrice, candle.low)

                val hitSl = candle.low <= stopLoss
                val hitTp = candle.high >= takeProfit

                if (hitSl && hitTp) {
                    status = "LOSS"
                    exitPrice = stopLoss
                    pnlR = -1.0
                    closedAt = candle.timestamp
                    break
                } else if (hitTp) {
                    status = "WIN"
                    exitPrice = takeProfit
                    pnlR = rr
                    closedAt = candle.timestamp
                    break
                } else if (hitSl) {
                    status = "LOSS"
                    exitPrice = stopLoss
                    pnlR = -1.0
                    closedAt = candle.timestamp
                    break
                }
            } else {
                maxFavPrice = min(maxFavPrice, candle.low)
                maxAdvPrice = max(maxAdvPrice, candle.high)

                val hitSl = candle.high >= stopLoss
                val hitTp = candle.low <= takeProfit

                if (hitSl && hitTp) {
                    status = "LOSS"
                    exitPrice = stopLoss
                    pnlR = -1.0
                    closedAt = candle.timestamp
                    break
                } else if (hitTp) {
                    status = "WIN"
                    exitPrice = takeProfit
                    pnlR = rr
                    closedAt = candle.timestamp
                    break
                } else if (hitSl) {
                    status = "LOSS"
                    exitPrice = stopLoss
                    pnlR = -1.0
                    closedAt = candle.timestamp
                    break
                }
            }

            // Timeout หลังจาก 50 แท่ง
            if (barsHeld >= 50) {
                status = "EXPIRED"
                exitPrice = candle.close
                pnlR = if (isBuy) (candle.close - entryPrice) / risk else (entryPrice - candle.close) / risk
                closedAt = candle.timestamp
                break
            }
        }

        val mfeR = if (isBuy) (maxFavPrice - entryPrice) / risk else (entryPrice - maxFavPrice) / risk
        val maeR = if (isBuy) (entryPrice - maxAdvPrice) / risk else (maxAdvPrice - entryPrice) / risk

        return OutcomeEvaluation(
            status = status,
            exitPrice = exitPrice,
            pnlR = pnlR,
            mfeR = max(0.0, mfeR),
            maeR = max(0.0, maeR),
            barsHeld = barsHeld,
            closedAt = closedAt
        )
    }

    /** ตรวจสอบและอัปเดตผลลัพธ์ของสัญญาณที่เปิดอยู่เทียบกับแท่งเทียนราคาตลาด */
    fun evaluateOpenSignals(symbol: String, candles: List<Candle>): Int {
        if (candles.size < 2) return 0
        val db = getDb() ?: return 0
        val openSignals = runCatching {
            db.jarvisDatabaseQueries.getOpenSignalTrackingRecordsBySymbol(symbol.uppercase()).executeAsList()
        }.getOrElse { emptyList() }

        if (openSignals.isEmpty()) return 0
        var resolvedCount = 0

        for (sig in openSignals) {
            val forwardCandles = candles.filter { it.timestamp >= sig.created_at }
            if (forwardCandles.isEmpty()) continue

            val result = evaluateSingleSignal(
                side = sig.side,
                entryPrice = sig.entry_price,
                stopLoss = sig.stop_loss,
                takeProfit = sig.take_profit,
                rr = sig.rr,
                createdAt = sig.created_at,
                forwardCandles = forwardCandles
            )

            if (result.status != "OPEN") {
                db.jarvisDatabaseQueries.updateSignalTrackingOutcome(
                    status = result.status,
                    mfe = result.mfeR,
                    mae = result.maeR,
                    exitPrice = result.exitPrice,
                    pnlR = result.pnlR,
                    barsHeld = result.barsHeld,
                    closedAt = result.closedAt ?: Clock.System.now().toEpochMilliseconds(),
                    signalId = sig.signal_id
                )
                resolvedCount++
                logDebug("SignalTracker", "Signal ${sig.signal_id} resolved as ${result.status}: pnlR=${"%.2f".format(result.pnlR ?: 0.0)}R, MFE=${"%.2f".format(result.mfeR)}R, MAE=${"%.2f".format(result.maeR)}R")
            }
        }
        return resolvedCount
    }

    /** ดึงสถิติผลการดำเนินงานย้อนหลังของแต่ละกลยุทธ์ */
    fun getStrategyPerformance(strategy: String): StrategyPerformance? {
        val db = getDb() ?: return null
        val records = runCatching {
            db.jarvisDatabaseQueries.getSignalTrackingPerformance().executeAsList()
        }.getOrElse { emptyList() }

        val row = records.firstOrNull { it.strategy.equals(strategy, ignoreCase = true) } ?: return null
        val total = row.total_signals
        val wins = row.wins ?: 0L
        val losses = row.losses ?: 0L
        val decided = wins + losses
        val winRate = if (decided > 0) (wins * 100.0 / decided) else 0.0

        return StrategyPerformance(
            strategy = row.strategy,
            totalSignals = total,
            wins = wins,
            losses = losses,
            winRatePct = winRate,
            avgR = row.avg_r ?: 0.0,
            avgMfeR = row.avg_mfe ?: 0.0,
            avgMaeR = row.avg_mae ?: 0.0
        )
    }

    /** Pure function สำหรับคำนวณ Confidence Adjustment จากสถิติ */
    fun computeConfidenceAdjustment(totalSignals: Long, wins: Long, losses: Long, winRatePct: Double, avgR: Double): Double {
        if (wins + losses < 3) return 0.0
        return when {
            winRatePct >= 65.0 && avgR >= 0.3 -> 0.20
            winRatePct >= 55.0 && avgR >= 0.1 -> 0.10
            winRatePct <= 30.0 || avgR <= -0.3 -> -0.25
            winRatePct <= 40.0 || avgR < 0.0 -> -0.15
            else -> 0.0
        }
    }

    /**
     * คำนวณค่าปรับน้ำหนัก (Confidence Adjustment) ให้กับกลยุทธ์ (Closed-loop Reinforcement):
     * - ชนะต่อเนื่อง / WinRate >= 60% และ avgR >= 0.2R -> คืนค่าบวก (+0.10 ถึง +0.20)
     * - ขาดทุนต่อเนื่อง / WinRate < 35% หรือ avgR < -0.2R -> คืนค่าลบ (-0.15 ถึง -0.30)
     * - ข้อมูลยังไม่พอ (< 3 ไม้) -> คืนค่า 0.0
     */
    fun getStrategyConfidenceAdjustment(strategy: String): Double {
        val perf = getStrategyPerformance(strategy) ?: return 0.0
        return computeConfidenceAdjustment(perf.totalSignals, perf.wins, perf.losses, perf.winRatePct, perf.avgR)
    }

    /** ดึงรายการสัญญาณที่ resolve แล้วล่าสุด */
    fun getRecentResolvedSignals(limit: Long = 20): List<SignalTrackingRecord> {
        val db = getDb() ?: return emptyList()
        return runCatching {
            db.jarvisDatabaseQueries.getRecentResolvedSignals(limit).executeAsList()
        }.getOrElse { emptyList() }
    }
}
