package com.example.personalaibot.tools.system

import com.example.personalaibot.diagnostic.DiagnosticManager
import com.example.personalaibot.tools.SideEffectDelegate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * SystemToolExecutor — จัดการคำสั่งตรวจสอบสุขภาพและรันการทดสอบระบบอัตโนมัติ
 */
class SystemToolExecutor(
    private val diagnosticManager: DiagnosticManager,
    private val delegate: SideEffectDelegate?
) {

    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return when (toolName) {
            "system_run_diagnostics" -> runDiagnostics()
            "system_check_connectivity" -> checkConnectivity()
            else -> "⚠️ ไม่พบเครื่องมือระบบ: $toolName"
        }
    }

    private suspend fun runDiagnostics(): String {
        val results = diagnosticManager.runFullDiagnostic()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        
        val report = buildString {
            append("## 🏥 JARVIS System Health Report\n")
            append("Generated: ${now.date} ${now.hour}:${now.minute}\n\n")
            
            results.forEach { res ->
                val emoji = when(res.status) {
                    "PASS" -> "✅"
                    "FAIL" -> "❌"
                    else -> "⚠️"
                }
                append("### $emoji ${res.category}\n")
                append("- **Status**: ${res.status}\n")
                append("- **Summary**: ${res.message}\n")
                if (res.details.isNotBlank()) {
                    append("- **Details**:\n```\n${res.details}\n```\n")
                }
                append("\n")
            }
        }

        // Save to Wiki through delegate
        delegate?.onSaveDiagnosticReport(
            filename = "Diagnostic_${now.date}_${now.hour}${now.minute}.md",
            content = report
        )

        return "📡 การตรวจสอบระบบเสร็จสมบูรณ์\n\n$report"
    }

    private suspend fun checkConnectivity(): String {
        val results = diagnosticManager.runFullDiagnostic().filter { it.category == "Network" }
        return results.joinToString("\n") { "${it.status}: ${it.message}" }
    }
}
