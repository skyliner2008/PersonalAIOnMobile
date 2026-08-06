package com.example.personalaibot.tools.system

import com.example.personalaibot.diagnostic.DiagnosticManager
import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.SideEffectDelegate
import com.example.personalaibot.tools.SkillDescriptor
import com.example.personalaibot.tools.ToolRegistry
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
            "system_create_agent_tool" -> createAgentTool(args)
            else -> "⚠️ ไม่พบเครื่องมือระบบ: $toolName"
        }
    }

    private suspend fun createAgentTool(args: Map<String, String>): String {
        val rawName = args["name"]?.trim() ?: return "Error: Missing 'name' parameter."
        val description = args["description"]?.trim() ?: return "Error: Missing 'description' parameter."
        val triggerKeywords = args["triggerKeywords"] ?: ""
        val systemPromptAddon = args["systemPromptAddon"] ?: return "Error: Missing 'systemPromptAddon' parameter."

        // Sanitize ชื่อ tool — ต้องเป็น identifier ที่ปลอดภัยสำหรับ function calling
        val name = rawName.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        if (name.isBlank()) return "Error: ชื่อ tool '$rawName' ใช้ไม่ได้ — ใช้ตัวอักษร a-z, 0-9, _ เท่านั้น"
        val finalName = if (name.startsWith("custom_")) name else "custom_$name"

        val keywords = triggerKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }

        // สร้าง JSON ด้วย kotlinx.serialization — ปลอดภัยจาก quote/newline injection
        val jsonContent = buildJsonObject {
            put("name", finalName)
            put("description", description)
            putJsonArray("triggerKeywords") { keywords.forEach { add(it) } }
            put("author", "Jarvis Agent")
            put("systemPromptAddon", systemPromptAddon)
        }.toString()

        val filename = "$finalName.json"

        // 1) บันทึกลงไฟล์ผ่าน delegate (persist ข้าม session)
        delegate?.onSaveAgentTool(filename, jsonContent)

        // 2) ลงทะเบียนเข้า ToolRegistry ทันที — ใช้งานได้เลยโดยไม่ต้อง restart
        ToolRegistry.registerCustomTool(FunctionDeclaration(
            name = finalName,
            description = "[CUSTOM] $description",
            parameters = null
        ))
        ToolRegistry.registerSkill(SkillDescriptor(
            name = finalName,
            description = description,
            systemPromptAddon = systemPromptAddon,
            triggerKeywords = keywords,
            author = "Jarvis Agent"
        ))

        return "✅ สร้างเครื่องมือใหม่ '$finalName' สำเร็จและลงทะเบียนเข้าระบบแล้ว " +
               "— ใช้งานได้ทันทีในแชทนี้ และจะถูกโหลดอัตโนมัติทุกครั้งที่เปิดแอป " +
               "(ไฟล์: custom_agent_tools/$filename)"
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
