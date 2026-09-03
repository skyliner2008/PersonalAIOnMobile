package com.example.personalaibot.automation

import com.example.personalaibot.tools.trading.Candle

/** Bounded process-local history for TradingView signal/outcome evaluation. */
object TradingViewSignalHistory {
    data class Record(
        val key: String,
        val timestamp: Long,
        val decision: TradingViewSignalIntelligence.Decision,
        val entry: Double,
        val confidencePct: Int,
        val dataQualityPct: Int,
        val gate: String,
        val outcome: TradingViewSignalIntelligence.Outcome? = null
    )

    private const val MAX_RECORDS = 500
    private val records = ArrayDeque<Record>()

    fun observe(key: String, timestamp: Long, decision: TradingViewSignalIntelligence.Decision, entry: Double, confidencePct: Int, dataQualityPct: Int, gate: String) {
        if (decision == TradingViewSignalIntelligence.Decision.WAIT) return
        records.removeAll { it.key == key && it.timestamp == timestamp }
        records.addLast(Record(key, timestamp, decision, entry, confidencePct, dataQualityPct, gate))
        while (records.size > MAX_RECORDS) records.removeFirst()
    }

    fun resolve(key: String, candles: List<Candle>, forwardBars: Int = 5) {
        if (candles.size <= forwardBars) return
        val latestTs = candles.last().timestamp
        val updated = records.map { r ->
            if (r.key == key && r.outcome == null && r.timestamp < latestTs) {
                val outcome = TradingViewSignalIntelligence.evaluateOutcome(r.decision, candles, forwardBars)
                if (outcome != null) r.copy(outcome = outcome) else r
            } else r
        }
        records.clear()
        records.addAll(updated)
    }

    fun snapshot(): List<Record> = records.toList()

    fun stats(): Map<String, String> {
        val resolved = records.mapNotNull { it.outcome }
        val wins = resolved.count { it.hit }
        val avgReturn = if (resolved.isEmpty()) 0.0 else resolved.map { it.returnPct }.average()
        return mapOf(
            "records" to records.size.toString(),
            "resolved" to resolved.size.toString(),
            "wins" to wins.toString(),
            "losses" to (resolved.size - wins).toString(),
            "win_rate_pct" to if (resolved.isEmpty()) "0" else (wins * 100.0 / resolved.size).toString(),
            "avg_return_pct" to avgReturn.toString()
        )
    }
}
