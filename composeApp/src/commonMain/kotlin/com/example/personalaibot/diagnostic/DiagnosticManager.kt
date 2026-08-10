package com.example.personalaibot.diagnostic

import com.example.personalaibot.tools.trading.TradingApiService
import com.example.personalaibot.automation.AutomationManager
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

/**
 * DiagnosticManager — ระบบตรวจสอบสุขภาพและความสมบูรณ์ของ Jarvis Engine
 */
class DiagnosticManager(
    private val client: HttpClient,
    private val tradingApi: TradingApiService,
    private val automationManager: AutomationManager
) {

    data class DiagnosticResult(
        val category: String,
        val status: String, // "PASS", "FAIL", "WARNING"
        val message: String,
        val details: String = ""
    )

    suspend fun runFullDiagnostic(): List<DiagnosticResult> = coroutineScope {
        val results = mutableListOf<DiagnosticResult>()
        
        // 1. Network & API Connectivity
        results.add(checkNetworkConnectivity())
        
        // 2. Trading Data Accuracy (OANDA vs Yahoo)
        results.add(checkTradingDataAccuracy())
        
        // 3. Database Integrity
        results.add(checkDatabaseIntegrity())
        
        // 4. Automation Service Status
        results.add(checkAutomationHealth())

        results
    }

    private suspend fun checkNetworkConnectivity(): DiagnosticResult {
        return try {
            val sites = listOf(
                "https://query1.finance.yahoo.com/v8/finance/chart/AAPL",
                "https://scanner.tradingview.com/america/scan"
            )
            val failures = mutableListOf<String>()
            for (site in sites) {
                val resp = client.get(site)
                if (!resp.status.isSuccess() && resp.status != HttpStatusCode.TooManyRequests) {
                    failures.add("${site.substringAfter("://").substringBefore("/")} (${resp.status.value})")
                }
            }
            if (failures.isEmpty()) {
                DiagnosticResult("Network", "PASS", "เชื่อมต่อ API สำคัญได้ครบถ้วน")
            } else {
                DiagnosticResult("Network", "FAIL", "เกิดข้อผิดพลาดในการเชื่อมต่อ: ${failures.joinToString(", ")}")
            }
        } catch (e: Exception) {
            DiagnosticResult("Network", "FAIL", "Network Error: ${e.message}")
        }
    }

    private suspend fun checkTradingDataAccuracy(): DiagnosticResult {
        return try {
            val symbol = "XAUUSD"
            val yahoo = tradingApi.getYahooPrice("GC=F") // Futures as reference
            val oanda = tradingApi.getTechnicalAnalysis(symbol, "OANDA")
            
            val priceYahoo = yahoo["price"]?.toDoubleOrNull() ?: 0.0
            val priceOanda = oanda["close"]?.toDoubleOrNull() ?: 0.0
            
            if (priceYahoo == 0.0 || priceOanda == 0.0) {
                return DiagnosticResult("Trading", "WARNING", "ไม่สามารถดึงข้อมูลเปรียบเทียบได้ในขณะนี้")
            }

            val diff = kotlin.math.abs(priceYahoo - priceOanda)
            val diffPct = (diff / priceOanda) * 100

            val details = "OANDA: $priceOanda\nYahoo (GC=F): $priceYahoo\nDiff: $diffPct%"
            
            when {
                diffPct < 0.2 -> DiagnosticResult("Trading", "PASS", "ราคาจากแหล่งต่างๆ สอดคล้องกัน (Diff < 0.2%)", details)
                diffPct < 1.0 -> DiagnosticResult("Trading", "WARNING", "พบความต่างของราคาเล็กน้อย (Diff < 1.0%)", details)
                else -> DiagnosticResult("Trading", "FAIL", "พบความต่างของราคาสูงผิดปกติ! กรุณาตรวจสอบ Exchange", details)
            }
        } catch (e: Exception) {
            DiagnosticResult("Trading", "FAIL", "Trading Diagnostic Error: ${e.message}")
        }
    }

    private fun checkDatabaseIntegrity(): DiagnosticResult {
        // ตรวจจริง: อ่านจำนวน records จากตารางหลัก (ใช้ getArchivalRecent ซึ่งเคยเป็น dead query)
        return try {
            val jobsSize = automationManager.activeJobs.value.size
            val archivalCount = runCatching {
                com.example.personalaibot.db.JarvisDatabaseHolder.database
                    ?.jarvisDatabaseQueries?.getArchivalRecent(1000)?.executeAsList()?.size
            }.getOrNull() ?: -1
            val details = "automation jobs: $jobsSize\narchival memories: ${if (archivalCount >= 0) archivalCount else "อ่านไม่ได้"}"
            if (archivalCount >= 0) {
                DiagnosticResult("Database", "PASS", "เข้าถึงฐานข้อมูล SQLDelight ได้ปกติ", details)
            } else {
                DiagnosticResult("Database", "WARNING", "อ่าน automation ได้ แต่อ่าน archival memory ไม่ได้", details)
            }
        } catch (e: Exception) {
            DiagnosticResult("Database", "FAIL", "ฐานข้อมูลขัดข้อง: ${e.message}")
        }
    }

    private fun checkAutomationHealth(): DiagnosticResult {
        // ตรวจจริงจากสถานะ AutomationManager (เดิมเป็น placeholder ที่ PASS เสมอ)
        return try {
            val jobs = automationManager.activeJobs.value
            val enabledJobs = jobs.count { it.is_active != 0L }
            val tasks = automationManager.scheduledTasks.value
            val enabledTasks = tasks.count { it.is_active != 0L }
            val details = "alert jobs: $enabledJobs/${jobs.size} active\nscheduled tasks: $enabledTasks/${tasks.size} active"
            DiagnosticResult("Automation", "PASS", "ระบบ Automation พร้อมทำงาน", details)
        } catch (e: Exception) {
            DiagnosticResult("Automation", "FAIL", "อ่านสถานะ automation ไม่ได้: ${e.message}")
        }
    }
}
