package com.example.personalaibot.tools.system

import com.example.personalaibot.diagnostic.DiagnosticManager
import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty
import com.example.personalaibot.tools.SideEffectDelegate
import com.example.personalaibot.tools.SkillDescriptor
import com.example.personalaibot.tools.ToolRegistry
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.resources.ExperimentalResourceApi
import personalaibot.composeapp.generated.resources.Res

/**
 * SystemToolExecutor — จัดการคำสั่งตรวจสอบสุขภาพและรันการทดสอบระบบอัตโนมัติ
 */
class SystemToolExecutor(
    private val diagnosticManager: DiagnosticManager? = null,
    private val delegate: SideEffectDelegate?
) {

    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return when (toolName) {
            "system_run_diagnostics" -> runDiagnostics()
            "system_check_connectivity" -> checkConnectivity()
            "system_create_agent_tool" -> createAgentTool(args)
            "system_list_agent_tools" -> listAgentTools()
            "system_delete_agent_tool" -> deleteAgentTool(args)
            "system_self_review" -> selfReview()
            else -> "⚠️ ไม่พบเครื่องมือระบบ: $toolName"
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun selfReview(): String {
        return try {
            val content = Res.readBytes("files/self_review.md").decodeToString()
            content + "\n\n[NARRATION MODE] ผู้ใช้ต้องการฟังรีวิวนี้ — ให้เล่าออกเสียงเป็นภาษาไทยแบบสนทนา ไล่ทีละหัวข้อจนครบทุกส่วน ไม่จำกัดความยาว ห้ามสรุปสั้น ห้ามใช้ markdown/ตาราง/สัญลักษณ์ ออกเสียง และห้ามหยุดกลางทางจนกว่าจะเล่าครบ"
        } catch (e: Exception) {
            "⚠️ อ่านเอกสารรีวิวไม่สำเร็จ: ${e.message}"
        }
    }

    private suspend fun createAgentTool(args: Map<String, String>): String {
        val rawName = args["name"]?.trim() ?: return "Error: Missing 'name' parameter."
        val description = args["description"]?.trim() ?: return "Error: Missing 'description' parameter."
        val triggerKeywords = args["triggerKeywords"] ?: ""
        val systemPromptAddon = args["systemPromptAddon"] ?: return "Error: Missing 'systemPromptAddon' parameter."
        val rawParams = args["parameters"] ?: args["parametersJson"]
        val executionType = args["executionType"]?.trim()?.lowercase() ?: "prompt"

        // Sanitize ชื่อ tool — ยืดหยุ่น ไม่บังคับกรอบ custom_ ถ้าชื่อไม่ชนกับ builtin tool
        val finalName = sanitizeToolName(rawName)
        if (finalName.isBlank()) return "Error: ชื่อ tool '$rawName' ใช้ไม่ได้ — ใช้ตัวอักษร a-z, 0-9, _ เท่านั้น"

        val keywords = triggerKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val parsedParams = parseParameters(rawParams)

        // สร้าง JSON ด้วย kotlinx.serialization บันทึกพารามิเตอร์และประเภทการทำงานอย่างสมบูรณ์
        val jsonContent = buildJsonObject {
            put("name", finalName)
            put("description", description)
            putJsonArray("triggerKeywords") { keywords.forEach { add(it) } }
            put("author", "Jarvis Agent")
            put("executionType", executionType)
            put("systemPromptAddon", systemPromptAddon)
            parsedParams?.let { fp ->
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        fp.properties.forEach { (pName, pProp) ->
                            putJsonObject(pName) {
                                put("type", pProp.type)
                                put("description", pProp.description)
                            }
                        }
                    }
                    if (fp.required.isNotEmpty()) {
                        putJsonArray("required") { fp.required.forEach { add(it) } }
                    }
                }
            }
        }.toString()

        val filename = "$finalName.json"

        // 1) บันทึกลงไฟล์ผ่าน delegate (persist ข้าม session)
        delegate?.onSaveAgentTool(filename, jsonContent)

        // 2) ลงทะเบียนเข้า ToolRegistry ทันทีพร้อม Parameter Schema — ใช้งาน function calling ได้เต็มประสิทธิภาพ
        ToolRegistry.registerCustomTool(FunctionDeclaration(
            name = finalName,
            description = "[CUSTOM] $description",
            parameters = parsedParams
        ))
        ToolRegistry.registerSkill(SkillDescriptor(
            name = finalName,
            description = description,
            systemPromptAddon = systemPromptAddon,
            triggerKeywords = keywords,
            author = "Jarvis Agent",
            parameters = parsedParams,
            executionType = executionType
        ))

        val paramSummary = if (parsedParams != null && parsedParams.properties.isNotEmpty()) {
            " (พารามิเตอร์: ${parsedParams.properties.keys.joinToString(", ")})"
        } else ""

        return "✅ สร้างเครื่องมือใหม่ '$finalName' [$executionType]$paramSummary สำเร็จและลงทะเบียนเข้าระบบแล้ว " +
               "— รองรับการส่ง Arguments และประมวลผลทันทีในแชทนี้ " +
               "(ไฟล์: custom_agent_tools/$filename)"
    }

    private fun parseParameters(paramsRaw: String?): FunctionParameters? {
        if (paramsRaw.isNullOrBlank()) return null
        val trimmed = paramsRaw.trim()
        return try {
            if (trimmed.startsWith("{")) {
                val element = Json.parseToJsonElement(trimmed)
                val obj = if (element is JsonObject && element.containsKey("properties")) {
                    element["properties"]?.jsonObject ?: element
                } else if (element is JsonObject) {
                    element
                } else return null

                val props = mutableMapOf<String, ParameterProperty>()
                val required = mutableListOf<String>()

                obj.forEach { (key, valEl) ->
                    when (valEl) {
                        is JsonObject -> {
                            val type = valEl["type"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "STRING"
                            val desc = valEl["description"]?.jsonPrimitive?.contentOrNull ?: key
                            val isReq = valEl["required"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (isReq) required.add(key)
                            props[key] = ParameterProperty(type, desc)
                        }
                        is JsonPrimitive -> {
                            props[key] = ParameterProperty("STRING", valEl.content)
                        }
                        else -> {
                            props[key] = ParameterProperty("STRING", key)
                        }
                    }
                }
                FunctionParameters(type = "OBJECT", properties = props, required = required)
            } else {
                val keys = trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (keys.isEmpty()) return null
                val props = keys.associateWith { key ->
                    ParameterProperty("STRING", "พารามิเตอร์ $key")
                }
                FunctionParameters(type = "OBJECT", properties = props, required = emptyList<String>())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeToolName(rawName: String): String {
        val name = rawName.trim().lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        if (name.isBlank()) return "custom_tool"
        // ยืดหยุ่น: ไม่บังคับ prefix custom_ เว้นแต่ชื่อจะไปซ้ำกับ builtin tool ที่มีอยู่เดิม
        val builtinCollides = ToolRegistry.isTradingTool(name) ||
                ToolRegistry.isFileTool(name) ||
                ToolRegistry.isCameraTool(name) ||
                ToolRegistry.isStrategyTool(name)
        return if (builtinCollides && !name.startsWith("custom_")) "custom_$name" else name
    }

    private suspend fun listAgentTools(): String {
        val tools = ToolRegistry.listCustomTools()
        if (tools.isEmpty()) {
            return "ยังไม่มี custom tool ที่สร้างไว้ — สร้างใหม่ได้ด้วย system_create_agent_tool"
        }
        val sb = StringBuilder()
        sb.appendLine("🔧 Custom Tools ที่สร้างไว้ (${tools.size} ตัว):")
        sb.appendLine()
        tools.keys.sorted().forEach { name ->
            val skill = ToolRegistry.getSkill(name)
            sb.appendLine("• **$name**")
            sb.appendLine("  - ${skill?.description ?: tools[name]}")
            if (!skill?.triggerKeywords.isNullOrEmpty()) {
                sb.appendLine("  - keywords: ${skill!!.triggerKeywords.joinToString(", ")}")
            }
            if (skill?.parameters != null && skill.parameters.properties.isNotEmpty()) {
                sb.appendLine("  - parameters: ${skill.parameters.properties.keys.joinToString(", ")}")
            }
            if (skill?.executionType != null && skill.executionType != "prompt") {
                sb.appendLine("  - type: ${skill.executionType}")
            }
            val fileContent = delegate?.onReadAgentTool("$name.json") ?: ""
            val addonMatch = Regex("\"systemPromptAddon\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(fileContent)
            if (addonMatch != null) {
                val addon = addonMatch.groupValues[1]
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                val preview = if (addon.length > 500) addon.take(500) + "…" else addon
                sb.appendLine("  - logic:")
                preview.lines().forEach { sb.appendLine("    $it") }
            }
            sb.appendLine()
        }
        sb.append("หมายเหตุ: แก้ไข tool ไหนให้เรียก system_create_agent_tool ด้วยชื่อเดิม (เขียนทับ) ส่วนลบใช้ system_delete_agent_tool")
        return sb.toString().trim()
    }

    private suspend fun deleteAgentTool(args: Map<String, String>): String {
        val rawName = args["name"]?.trim() ?: return "Error: Missing 'name' parameter."
        val name = sanitizeToolName(rawName)
        if (name == "custom_") return "Error: ชื่อ tool '$rawName' ใช้ไม่ได้"

        val existed = ToolRegistry.listCustomTools().containsKey(name)
        if (!existed) {
            val available = ToolRegistry.listCustomTools().keys.sorted()
            return "⚠️ ไม่พบ custom tool '$name'" +
                (if (available.isNotEmpty()) " — ที่มีอยู่: ${available.joinToString(", ")}" else " — ยังไม่มี custom tool เลย")
        }

        // 1) ลบไฟล์ผ่าน delegate (persist)
        val fileResult = delegate?.onDeleteAgentTool("$name.json") ?: "(ไม่ได้เชื่อมต่อ file system)"

        // 2) ถอดออกจาก registry ทันที — หายจาก tool list รอบถัดไป
        ToolRegistry.unregisterCustomTool(name)

        return "🗑️ ลบ custom tool '$name' เรียบร้อย — ถอดออกจากระบบแล้ว (ไฟล์: $fileResult)"
    }

    private suspend fun runDiagnostics(): String {
        val results = diagnosticManager?.runFullDiagnostic() ?: return "⚠️ DiagnosticManager ยังไม่ถูกติดตั้ง"
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
        val results = diagnosticManager?.runFullDiagnostic()?.filter { it.category == "Network" }
            ?: return "⚠️ DiagnosticManager ยังไม่ถูกติดตั้ง"
        return results.joinToString("\n") { "${it.status}: ${it.message}" }
    }
}
