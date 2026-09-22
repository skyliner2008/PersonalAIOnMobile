package com.skyliner2008.jarvis.automation

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.db.SignalTrackingRecord
import com.skyliner2008.jarvis.logDebug
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.round

/**
 * SignalDatasetManager — ผู้จัดการการส่งออก (Export) และนำเข้า (Import) ข้อมูลสัญญาณการเทรด
 * ทำหน้าที่:
 * 1. Export Dataset: รวบรวมสัญญาณเทรดจริง + Candlestick Features Snapshot + ผลลัพธ์ (Win/Loss/MFE/MAE)
 *    ส่งออกเป็น JSON หรือ CSV สำหรับส่งต่อให้ AI ภายนอกหรือ ML Model นำไปค้นหาความสัมพันธ์และจูนพารามิเตอร์
 * 2. Import Config: รับค่าพารามิเตอร์ที่ AI ปรับจูนแล้ว (StrategyTuning, EntryTuning, StrategyGate)
 *    ตรวจสอบความถูกต้องและบันทึกกลับเข้าสู่ระบบ SQLite เพื่อใช้งานใน Live Alert ทันที
 */
object SignalDatasetManager {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    data class ExportResult(
        val totalCount: Int,
        val winCount: Int,
        val lossCount: Int,
        val winRatePct: Double,
        val avgR: Double,
        val avgMfe: Double,
        val avgMae: Double,
        val dataFormat: String,
        val payload: String,
        val summaryTh: String
    )

    data class ImportResult(
        val success: Boolean,
        val updatedTunings: Int,
        val updatedEntryParams: Int,
        val messageTh: String,
        val details: List<String>
    )

    /**
     * ส่งออกชุดข้อมูลสัญญาณการเทรด (Export Dataset)
     */
    fun exportDataset(
        symbol: String? = null,
        interval: String? = null,
        strategy: String? = null,
        status: String? = null,
        format: String = "json",
        limit: Int = 1000
    ): ExportResult {
        val db = JarvisDatabaseHolder.database
            ?: return ExportResult(
                0, 0, 0, 0.0, 0.0, 0.0, 0.0,
                format.lowercase(),
                if (format.lowercase() == "csv") buildCsv(emptyList()) else "[]",
                "❌ ยังไม่สามารถเชื่อมต่อฐานข้อมูลได้"
            )

        val allRecords = runCatching {
            db.jarvisDatabaseQueries.getAllSignalTrackingRecords().executeAsList()
        }.getOrElse { emptyList() }

        // กรองตามเงื่อนไข (ไม่รวม anticipation เว้นแต่จะระบุ strategy="anticipation" หรือ ANTICIPATION_*)
        val isAnticipationOnly = strategy?.equals("anticipation", ignoreCase = true) == true
        val isAnticipationPrefix = strategy?.startsWith("ANTICIPATION_", ignoreCase = true) == true

        val filtered = allRecords.filter { r ->
            val isAnticipationRecord = r.strategy.startsWith("ANTICIPATION_")
            val matchesAnticipationFilter = when {
                isAnticipationOnly -> isAnticipationRecord
                isAnticipationPrefix -> r.strategy.equals(strategy.trim(), ignoreCase = true)
                else -> !isAnticipationRecord
            }
            matchesAnticipationFilter &&
            (symbol.isNullOrBlank() || r.symbol.equals(symbol.trim(), ignoreCase = true)) &&
            (interval.isNullOrBlank() || r.interval.equals(interval.trim(), ignoreCase = true)) &&
            (strategy.isNullOrBlank() || strategy.equals("all", ignoreCase = true) || isAnticipationOnly || r.strategy.equals(strategy.trim(), ignoreCase = true)) &&
            (status.isNullOrBlank() || status.equals("all", ignoreCase = true) ||
                (status.equals("resolved", ignoreCase = true) && r.status in listOf("WIN", "LOSS", "BE", "EXPIRED")) ||
                r.status.equals(status.trim(), ignoreCase = true))
        }.take(limit)

        val total = filtered.size
        val wins = filtered.count { it.status == "WIN" }
        val losses = filtered.count { it.status == "LOSS" }
        val decided = wins + losses
        val winRate = if (decided > 0) (wins * 100.0 / decided) else 0.0
        val avgR = if (decided > 0) filtered.mapNotNull { it.pnl_r }.average().takeIf { !it.isNaN() } ?: 0.0 else 0.0
        val avgMfe = filtered.map { it.mfe }.average().takeIf { !it.isNaN() } ?: 0.0
        val avgMae = filtered.map { it.mae }.average().takeIf { !it.isNaN() } ?: 0.0

        val payload = if (format.lowercase() == "csv") {
            buildCsv(filtered)
        } else {
            buildJson(filtered)
        }

        val summary = buildString {
            appendLine("📦 **ส่งออก Dataset สัญญาณการเทรดสำเร็จ**")
            appendLine("• **จำนวนรายการ**: $total สัญญาณ (Win: $wins, Loss: $losses, Win Rate: ${round2(winRate)}%)")
            appendLine("• **ค่าเฉลี่ยสถิติ**: Avg R = ${round2(avgR)}R | Avg MFE = ${round2(avgMfe)}R | Avg MAE = ${round2(avgMae)}R")
            appendLine("• **รูปแบบข้อมูล**: ${format.uppercase()}")
            if (filtered.isNotEmpty()) {
                val stratCounts = filtered.groupBy { it.strategy }.mapValues { it.value.size }
                appendLine("• **แยกตามกลยุทธ์**: " + stratCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" })
            }
        }.trim()

        return ExportResult(
            totalCount = total,
            winCount = wins,
            lossCount = losses,
            winRatePct = round2(winRate),
            avgR = round2(avgR),
            avgMfe = round2(avgMfe),
            avgMae = round2(avgMae),
            dataFormat = format.lowercase(),
            payload = payload,
            summaryTh = summary
        )
    }

    /**
     * นำเข้าชุดพารามิเตอร์การตั้งค่า (Import Config) ที่ AI ปรับจูนแล้ว
     */
    fun importConfiguration(configJson: String): ImportResult {
        val rootElement = runCatching {
            jsonParser.parseToJsonElement(configJson).jsonObject
        }.getOrElse {
            return ImportResult(false, 0, 0, "❌ รูปแบบ JSON ไม่ถูกต้อง: ${it.message}", emptyList())
        }

        val mgr = runCatching { JarvisDatabaseHolder.getAutomationManager() }.getOrNull()
            ?: return ImportResult(false, 0, 0, "❌ AutomationManager ยังไม่พร้อมใช้งาน", emptyList())

        val details = mutableListOf<String>()
        var updatedTunings = 0
        var updatedEntry = 0

        // 1. Process "tunings" array
        val tuningsArray = rootElement["tunings"]?.jsonArray
        if (tuningsArray != null) {
            for (elem in tuningsArray) {
                val obj = elem.jsonObject
                val sym = obj["symbol"]?.jsonPrimitive?.content ?: rootElement["symbol"]?.jsonPrimitive?.content ?: "XAUUSD"
                val interval = obj["interval"]?.jsonPrimitive?.content ?: rootElement["interval"]?.jsonPrimitive?.content ?: "15m"
                val kind = obj["kind"]?.jsonPrimitive?.content ?: obj["strategy"]?.jsonPrimitive?.content ?: continue
                val slMult = obj["sl_mult"]?.jsonPrimitive?.doubleOrNull ?: 1.5
                val tpMult = obj["tp_mult"]?.jsonPrimitive?.doubleOrNull ?: 2.0
                val score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val grade = obj["grade"]?.jsonPrimitive?.content ?: "healthy"
                val source = obj["source"]?.jsonPrimitive?.content ?: "ai_import"
                val riskGateEligible = obj["risk_gate_eligible"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

                val ok = mgr.saveStrategyTuning(
                    symbol = sym,
                    interval = interval,
                    kind = kind,
                    slMult = slMult,
                    tpMult = tpMult,
                    score = score,
                    grade = grade,
                    source = source,
                    riskGateEligible = riskGateEligible
                )
                if (ok) {
                    updatedTunings++
                    details.add("✅ Updated Tuning: $sym/$interval/$kind (SL: $slMult, TP: $tpMult, Grade: $grade)")
                }
            }
        }

        // 2. Process "entry_params" array
        val entryArray = rootElement["entry_params"]?.jsonArray
        if (entryArray != null) {
            for (elem in entryArray) {
                val obj = elem.jsonObject
                val sym = obj["symbol"]?.jsonPrimitive?.content ?: rootElement["symbol"]?.jsonPrimitive?.content ?: "XAUUSD"
                val interval = obj["interval"]?.jsonPrimitive?.content ?: rootElement["interval"]?.jsonPrimitive?.content ?: "15m"
                val kind = obj["kind"]?.jsonPrimitive?.content ?: continue
                val paramsObj = obj["params"]?.jsonObject ?: JsonObject(emptyMap())
                val paramsJson = paramsObj.toString()
                val score = obj["score"]?.jsonPrimitive?.doubleOrNull
                val expR = obj["expectancy_r"]?.jsonPrimitive?.doubleOrNull
                val pf = obj["profit_factor"]?.jsonPrimitive?.doubleOrNull
                val trades = obj["trades"]?.jsonPrimitive?.longOrNull?.toInt()
                val grade = obj["grade"]?.jsonPrimitive?.content ?: "healthy"

                val ok = mgr.saveEntryTuning(
                    symbol = sym,
                    interval = interval,
                    kind = kind,
                    paramsJson = paramsJson,
                    score = score,
                    expectancyR = expR,
                    profitFactor = pf,
                    trades = trades,
                    grade = grade,
                    source = "ai_import",
                    riskGateEligible = true
                )
                if (ok) {
                    updatedEntry++
                    details.add("✅ Updated EntryParams: $sym/$interval/$kind $paramsJson")
                }
            }
        }

        val msg = buildString {
            appendLine("📥 **นำเข้าการตั้งค่าพารามิเตอร์กลยุทธ์สำเร็จ!**")
            appendLine("• Strategy Tuning: $updatedTunings รายการ")
            appendLine("• Entry Tuning: $updatedEntry รายการ")
            appendLine("• การตั้งค่าใหม่จะมีผลต่อ Live Alert และ Paper Trading ทันทีในรอบถัดไป")
        }.trim()

        return ImportResult(
            success = updatedTunings > 0 || updatedEntry > 0,
            updatedTunings = updatedTunings,
            updatedEntryParams = updatedEntry,
            messageTh = msg,
            details = details
        )
    }

    private fun buildJson(records: List<SignalTrackingRecord>): String {
        val array = buildJsonArray {
            for (r in records) {
                add(buildJsonObject {
                    put("signal_id", r.signal_id)
                    put("symbol", r.symbol)
                    put("interval", r.interval)
                    put("strategy", r.strategy)
                    put("side", r.side)
                    put("entry_price", r.entry_price)
                    put("stop_loss", r.stop_loss)
                    put("take_profit", r.take_profit)
                    put("rr", r.rr)
                    put("status", r.status)
                    put("mfe_r", r.mfe)
                    put("mae_r", r.mae)
                    if (r.exit_price != null) put("exit_price", r.exit_price)
                    if (r.pnl_r != null) put("pnl_r", r.pnl_r)
                    put("bars_held", r.bars_held)
                    put("created_at", r.created_at)
                    if (r.closed_at != null) put("closed_at", r.closed_at)

                    // Embed parsed features if available
                    val featRaw = r.features_json
                    if (!featRaw.isNullOrBlank()) {
                        val parsedFeat = runCatching { jsonParser.parseToJsonElement(featRaw) }.getOrNull()
                        if (parsedFeat != null) {
                            put("features", parsedFeat)
                        } else {
                            put("features_raw", featRaw)
                        }
                    }
                })
            }
        }
        return jsonParser.encodeToString(JsonArray.serializer(), array)
    }

    private fun buildCsv(records: List<SignalTrackingRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("signal_id,symbol,interval,strategy,side,entry_price,stop_loss,take_profit,rr,status,pnl_r,mfe_r,mae_r,bars_held,created_at,closed_at,features_json")
        for (r in records) {
            val escFeat = (r.features_json ?: "").replace("\"", "\"\"")
            sb.appendLine("${r.signal_id},${r.symbol},${r.interval},${r.strategy},${r.side},${r.entry_price},${r.stop_loss},${r.take_profit},${r.rr},${r.status},${r.pnl_r ?: ""},${r.mfe},${r.mae},${r.bars_held},${r.created_at},${r.closed_at ?: ""},\"$escFeat\"")
        }
        return sb.toString()
    }

    private fun round2(v: Double): Double = (round(v * 100.0) / 100.0).takeIf { !it.isNaN() } ?: 0.0
}
