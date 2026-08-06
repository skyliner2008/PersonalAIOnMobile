package com.example.personalaibot.automation

import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.AdvancedTradingEngine
import com.example.personalaibot.tools.trading.SmcApiService
import com.example.personalaibot.tools.trading.TradingApiService
import io.ktor.client.HttpClient

/**
 * AlertDataTester — ไล่ทดสอบดึงข้อมูลจากทุก tool ที่ระบบ alert รองรับ
 *
 * กดจากหน้า Automation (ปุ่ม 🧪 Auto Test) — รันทีละตัวตามลำดับ
 * ผลละเอียดดูใน logcat ด้วย tag "AlertDataTest" (รูปแบบอ่านง่าย ✅/❌)
 * สถานะระหว่างรันส่งกลับผ่าน onProgress เพื่อแสดงในแอป
 */
class AlertDataTester(client: HttpClient) {

    private val tradingApi = TradingApiService(client)
    private val smcApi = SmcApiService(client)
    private val indicatorProvider = IndicatorAlertProvider(smcApi)
    private val smcAlertProvider = SmcAlertProvider(smcApi)
    private val advancedEngine = AdvancedTradingEngine(smcApi)

    data class TestResult(
        val tool: String,
        val ok: Boolean,
        val fieldCount: Int,
        val sample: String,
        val error: String? = null
    )

    /** รันครบทุก tool — onProgress(ข้อความสถานะ, ผลที่เสร็จแล้ว) */
    suspend fun runFullTest(
        symbol: String = "XAUUSD",
        onProgress: (status: String, done: List<TestResult>) -> Unit
    ): List<TestResult> {
        val results = mutableListOf<TestResult>()
        logDebug("AlertDataTest", "════════ เริ่มทดสอบระบบดึงข้อมูลทั้งหมด (symbol=$symbol) ════════")

        suspend fun step(name: String, block: suspend () -> Map<String, String>) {
            onProgress("⏳ กำลังทดสอบ $name ...", results.toList())
            val started = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val result = try {
                val data = block()
                val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - started
                val err = data["error"]
                when {
                    data.isEmpty() -> TestResult(name, false, 0, "-", "empty result")
                    err != null -> TestResult(name, false, 0, "-", err)
                    else -> {
                        val samples = data.entries
                            .filter { it.key !in setOf("symbol", "timeframe", "source") }
                            .take(4)
                            .joinToString { "${it.key}=${it.value}" }
                        logDebug("AlertDataTest", "✅ $name → OK (${data.size} fields, ${elapsed}ms)\n   $samples")
                        TestResult(name, true, data.size, samples)
                    }
                }
            } catch (e: Exception) {
                logDebug("AlertDataTest", "❌ $name → EXCEPTION: ${e.message}")
                TestResult(name, false, 0, "-", e.message ?: "exception")
            }
            if (!result.ok) {
                logDebug("AlertDataTest", "❌ $name → FAIL: ${result.error}")
            }
            results.add(result)
            onProgress("✔ เสร็จ $name (${if (result.ok) "OK" else "FAIL"})", results.toList())
        }

        // 1) ราคาเรียลไทม์
        step("trading_price") { tradingApi.getBestEffortPrice(symbol) }

        // 2) TV scanner TA (fallback เหมือน service จริง)
        step("trading_technical_analysis") {
            var r: Map<String, String> = mapOf("error" to "no data")
            for (ex in listOf("OANDA", "FX_IDC", "TVC")) {
                r = tradingApi.getTechnicalAnalysis(symbol, ex)
                if (!r.containsKey("error") && r["close"] != "N/A") break
            }
            r
        }

        // 3) อินดิเคเตอร์คำนวณเองจากแท่งเทียน
        step("trading_indicators") { indicatorProvider.fetch(symbol) }

        // 4) SMC zones
        step("trading_smc") { smcAlertProvider.fetch(symbol) }

        // 5) Deep analysis suite (ครบทั้ง 5 มิติ — เลือก TF ได้ด้วย @TF)
        step("trading_deep_analysis_suite") {
            val (sym, tf) = IndicatorAlertProvider.splitSymbolAndTf(symbol)
            val r = advancedEngine.analyze(sym, tf)
            if (r == null) mapOf("error" to "null result")
            else mapOf(
                "summaryScore" to r.summaryScore.toString(),
                "lsdState" to r.lsdTrend.state,
                "lsdConfluenceTF" to r.lsdTrend.confluenceTF.toString(),
                "deltaLabel" to r.orderflow.deltaLabel,
                "deltaValue" to r.orderflow.lastDelta.toString(),
                "fiboScore" to (r.fiboStrength.maxOfOrNull { it.score }?.toString() ?: "0"),
                "momentum" to r.momentum.signal,
                "isSqueeze" to (if (r.momentum.isSqueeze) "1" else "0"),
                "close" to r.currentPrice.toString()
            )
        }

        // 6) Fear & Greed
        step("trading_fear_greed") { tradingApi.getFearGreedIndex(1) }

        // 7) ภาพรวมตลาดคริปโต
        step("trading_crypto_overview") { tradingApi.getCryptoGlobal() }

        // 8) Reddit sentiment (คาดว่าอาจโดน 403 — ทดสอบยืนยันสถานะ)
        step("trading_sentiment") { tradingApi.getRedditSentiment("BTC").mapValues { it.value.toString() } }

        val ok = results.count { it.ok }
        logDebug("AlertDataTest", "════════ สรุป: ผ่าน $ok/${results.size} tools ════════")
        onProgress("🏁 เสร็จสิ้น: ผ่าน $ok/${results.size}", results.toList())
        return results
    }
}
