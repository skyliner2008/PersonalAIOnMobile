package com.example.personalaibot.tools

import com.example.personalaibot.tools.trading.TradingToolExecutor
import com.example.personalaibot.tools.camera.CameraToolExecutor
import com.example.personalaibot.tools.system.SystemToolExecutor
import io.ktor.client.*
import kotlinx.datetime.*
import kotlin.math.*

/**
 * ToolExecutor — รัน tool call จาก Gemini แล้วคืนผลลัพธ์
 *
 * ทุก built-in tool ถูก implement ที่นี่ใน pure Kotlin/KMP
 * Trading tools route ไปยัง TradingToolExecutor
 * Custom skills จะถูก route ไปยัง Gemini พร้อม systemPromptAddon ของ skill
 *
 * หมายเหตุ lifecycle: object singleton นี้ถูก init ครั้งเดียวจาก JarvisOrchestrator
 * (init / initFileHandler / setSideEffectDelegate ควรเรียกก่อน execute เสมอ)
 * หากอนาคตต้องสร้าง Orchestrator หลาย instance พร้อมกัน ให้ refactor เป็น injectable class
 */
object ToolExecutor {
    data class Mt5RuntimeConfig(
        val bridgeBaseUrl: String,
        val authToken: String,
        val pairingStatus: String
    )

    // TradingToolExecutor จะถูก initialize เมื่อ HttpClient พร้อม
    private var _tradingExecutor: TradingToolExecutor? = null

    // Bridge สำหรับ File Management (Platform specific)
    private var _fileToolHandler: (suspend (String, Map<String, String>) -> String)? = null

    // Executor สำหรับ Camera & Vision
    private var _cameraExecutor: CameraToolExecutor? = null
    private var _mt5RuntimeConfigProvider: (() -> Mt5RuntimeConfig)? = null

    // Executor สำหรับ System tools (diagnostics / connectivity / create custom tool)
    private var _systemExecutor: SystemToolExecutor? = null

    // Delegate สำหรับ Side-effects (Memory, Reminders, UI)
    private var _sideEffectDelegate: SideEffectDelegate? = null

    fun init(client: HttpClient, geminiService: com.example.personalaibot.data.GeminiService) {
        _tradingExecutor = TradingToolExecutor(client, geminiService)
    }

    fun initFileHandler(handler: suspend (String, Map<String, String>) -> String) {
        _fileToolHandler = handler
    }

    fun initCameraExecutor(executor: CameraToolExecutor) {
        _cameraExecutor = executor
    }

    fun initSystemExecutor(executor: SystemToolExecutor) {
        _systemExecutor = executor
    }

    fun setSideEffectDelegate(delegate: SideEffectDelegate) {
        _sideEffectDelegate = delegate
    }

    fun setMt5RuntimeConfigProvider(provider: () -> Mt5RuntimeConfig) {
        _mt5RuntimeConfigProvider = provider
    }

    /**
     * Execute a single tool call
     * @param call  ToolCall ที่ได้จาก Gemini function calling response
     * @return ToolResult ที่จะส่งกลับไปให้ Gemini
     */
    suspend fun execute(call: ToolCall, memoryContext: String = ""): ToolResult {
        return try {
            val routedToolName = when (call.name) {
                "mt5_place_order" -> "trading_mt5_order"
                "mt5_close_position" -> "trading_mt5_close_position"
                "mt5_modify_position" -> "trading_mt5_modify_position"
                else -> call.name
            }
            val routedArgs = enrichMt5Args(routedToolName, call.args)
            val result = when {
                // ─── Automation Alerts (intercept ก่อน trading — ใช้ AutomationManager ผ่าน delegate) ──
                routedToolName == "automation_manage_alerts" -> executeManageAlerts(routedArgs)
                // ─── Scheduled Tasks (งานตามเวลา — ปลุก AI เมื่อถึงเวลา) ──
                routedToolName == "automation_manage_schedule" -> executeManageSchedule(routedArgs)
                // ─── Live Control (Vision/Voice) — intercept ก่อน camera branch ──
                routedToolName in liveControlToolNames -> executeLiveControl(routedToolName, routedArgs)
                // ─── System Tools (Diagnostics / Connectivity / Create Tool) ────
                ToolRegistry.isSystemTool(routedToolName) -> {
                    _systemExecutor?.execute(routedToolName, routedArgs)
                        ?: "⚠️ System module ยังไม่พร้อมใช้งาน (SystemToolExecutor ยังไม่ถูก init)"
                }
                // ─── Trading Tools ─────────────────────────────────────────
                ToolRegistry.isTradingTool(routedToolName) -> {
                    val trader = _tradingExecutor
                        ?: return ToolResult(call.name, "⚠️ Trading module ยังไม่พร้อม กรุณาตั้งค่า API Key ก่อน", true)
                    trader.execute(routedToolName, routedArgs)
                }
                // ─── File Management Tools ─────────────────────────────────
                ToolRegistry.isFileTool(routedToolName) -> {
                    _fileToolHandler?.invoke(routedToolName, routedArgs)
                        ?: "⚠️ ระบบจัดการไฟล์ยังไม่พร้อม หรือยังไม่ได้รับ Permission"
                }
                // ─── Camera & Vision Tools ─────────────────────────────────
                ToolRegistry.isCameraTool(routedToolName) -> {
                    _cameraExecutor?.execute(routedToolName, routedArgs)
                        ?: "⚠️ ระบบกล้องยังไม่พร้อม กรุณาเปิดกล้องก่อนใช้งาน"
                }
                // ─── Strategy Library (Quantpedia knowledge base) ──────────
                ToolRegistry.isStrategyTool(routedToolName) -> {
                    com.example.personalaibot.tools.strategy.StrategyToolExecutor.execute(routedToolName, routedArgs)
                }
                // ─── Built-in Tools ────────────────────────────────────────
                else -> when (routedToolName) {
                "get_current_datetime" -> executeDateTime()
                "calculate"            -> executeCalculate(routedArgs["expression"] ?: "")
                "remember_fact"        -> executeRememberFact(routedArgs)
                "recall_memory"        -> executeRecallMemory(routedArgs["query"] ?: "", memoryContext)
                "convert_units"        -> executeConvertUnits(routedArgs)
                "set_reminder"         -> executeSetReminder(routedArgs)
                "format_json"          -> executeFormatJson(routedArgs["data"] ?: "")
                "translate_text"       -> executeTranslate(routedArgs)
                "summarize_text"       -> executeSummarize(routedArgs)
                "search_web"           -> executeSearchWeb(routedArgs["query"] ?: "")
                "identity_update"      -> executeIdentityUpdate(routedArgs)
                "analyze_and_display_report" -> executeDisplayReport(routedArgs)
                "chart_dashboard_control" -> _sideEffectDelegate?.onChartControl(routedArgs)
                    ?: "⚠️ ระบบกราฟยังไม่พร้อมใช้งาน"
                else                   -> executeCustomSkill(ToolCall(routedToolName, routedArgs))
                } // end inner when
            } // end outer when
            ToolResult(call.name, result)
        } catch (e: Exception) {
            ToolResult(call.name, "Error: ${e.message}", isError = true)
        }
    }

    private fun enrichMt5Args(toolName: String, args: Map<String, String>): Map<String, String> {
        val mt5Tools = setOf(
            "trading_mt5_order",
            "trading_mt5_close_position",
            "trading_mt5_modify_position",
            // MT5 Core Agent Tools
            "trading_mt5_account_info",
            "trading_mt5_list_positions",
            "trading_mt5_list_orders",
            "trading_mt5_list_history",
            "trading_mt5_candles",
            "trading_mt5_analyze",
            "trading_mt5_symbol_info",
            "trading_mt5_close_all",
            "trading_mt5_break_even_all",
            "trading_mt5_snapshot",
            "trading_mt5_trade_actions",
            // MT5 Advanced Intelligence
            "trading_mt5_market_scanner",
            "trading_mt5_correlation_radar",
            "trading_mt5_sentiment_gauge",
            "trading_mt5_institutional_flow",
            "trading_mt5_economic_radar",
            "trading_mt5_trade_journal",
            "trading_deep_analysis_suite",
            "trading_fear_greed",
            "trading_fundamental_analysis",
            "trading_mt5_symbol_search"
        )
        if (toolName !in mt5Tools) return args
        val runtime = _mt5RuntimeConfigProvider?.invoke() ?: return args
        val out = args.toMutableMap()
        if (runtime.authToken.isNotBlank() && out["token"].isNullOrBlank()) {
            out["token"] = runtime.authToken.trim()
        }
        if (out["endpoint"].isNullOrBlank() && runtime.bridgeBaseUrl.isNotBlank()) {
            val base = normalizeMt5ApiBase(runtime.bridgeBaseUrl)
            out["endpoint"] = when (toolName) {
                // POST-based action tools
                "trading_mt5_order" -> "$base/order"
                "trading_mt5_close_position" -> "$base/close"
                "trading_mt5_modify_position" -> "$base/modify"
                "trading_mt5_close_all" -> "$base/close"
                "trading_mt5_break_even_all" -> "$base/modify"
                // GET-based query tools
                "trading_mt5_account_info" -> "$base/account"
                "trading_mt5_list_positions" -> "$base/positions"
                "trading_mt5_list_orders" -> "$base/orders"
                "trading_mt5_list_history" -> "$base/history"
                "trading_mt5_candles" -> "$base/candles"
                "trading_mt5_analyze" -> "$base/analyze"
                "trading_mt5_symbol_info" -> "$base/symbols"
                "trading_mt5_snapshot" -> "$base/snapshot"
                "trading_mt5_trade_actions" -> "$base/trade-actions"
                // Advanced intelligence tools — use base path for multi-endpoint access
                "trading_mt5_market_scanner" -> "$base/candles"
                "trading_mt5_correlation_radar" -> "$base/candles"
                "trading_mt5_sentiment_gauge" -> base
                "trading_mt5_institutional_flow" -> "$base/candles"
                "trading_mt5_economic_radar" -> base
                "trading_mt5_trade_journal" -> "$base/auto"
                "trading_deep_analysis_suite" -> "$base/auto/deep-analysis"
                "trading_fear_greed" -> "$base/auto"
                "trading_fundamental_analysis" -> base
                else -> "$base/order"
            }
        }
        return out
    }

    private fun normalizeMt5ApiBase(rawUrl: String): String {
        val url = rawUrl.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url
            url.endsWith("/mt5", ignoreCase = true) -> url
            else -> "$url/api/mt5"
        }
    }

    // ─── Built-in Implementations ─────────────────────────────────────────────

    private fun executeDateTime(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return buildString {
            append("วันที่: ${now.date}\n")
            append("เวลา: ${now.hour.toString().padStart(2,'0')}:${now.minute.toString().padStart(2,'0')}\n")
            append("วันใน week: ${now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}")
        }
    }

    private fun executeCalculate(expression: String): String {
        if (expression.isBlank()) return "กรุณาระบุนิพจน์ที่ต้องการคำนวณ"
        return try {
            val result = evalMath(expression.trim())
            "ผลลัพธ์: $expression = $result"
        } catch (e: Exception) {
            "ไม่สามารถคำนวณได้: ${e.message}"
        }
    }

    private suspend fun executeRememberFact(args: Map<String, String>): String {
        val key = args["key"] ?: return "ต้องระบุ key"
        val value = args["value"] ?: return "ต้องระบุ value"
        val importance = args["importance"] ?: "medium"
        
        _sideEffectDelegate?.onRememberFact(key, value, importance)
        
        return "บันทึกข้อมูลเรียบร้อยแล้ว: $key = $value"
    }

    /**
     * recall_memory — ใช้ semantic search (embedding) ผ่าน SideEffectDelegate เป็นหลัก
     * fallback เป็น substring match ใน memoryContext เมื่อยังไม่มี delegate
     */
    private suspend fun executeRecallMemory(query: String, memoryContext: String): String {
        if (query.isBlank()) return "ต้องระบุ query"

        // Primary: semantic search ด้วย embedding (JarvisMemoryManager Archival layer)
        val delegate = _sideEffectDelegate
        if (delegate != null) {
            return try {
                delegate.onRecallMemory(query)
            } catch (e: Exception) {
                "ค้นหา memory ไม่สำเร็จ: ${e.message}"
            }
        }

        // Fallback: substring match ใน context ที่ส่งมา
        if (memoryContext.isBlank()) return "ยังไม่มีข้อมูลใน memory"
        val lines = memoryContext.lines()
        val relevant = lines.filter { line ->
            query.lowercase().split(" ").any { word -> line.lowercase().contains(word) }
        }
        return if (relevant.isNotEmpty()) {
            "ข้อมูลที่เกี่ยวข้องจาก memory:\n${relevant.take(5).joinToString("\n")}"
        } else {
            "ไม่พบข้อมูลที่เกี่ยวข้องกับ '$query' ใน memory"
        }
    }

    private fun executeConvertUnits(args: Map<String, String>): String {
        val valueStr = args["value"] ?: return "ต้องระบุค่าตัวเลข"
        val fromUnit = args["from_unit"]?.lowercase() ?: return "ต้องระบุหน่วยต้นทาง"
        val toUnit = args["to_unit"]?.lowercase() ?: return "ต้องระบุหน่วยปลายทาง"

        val value = valueStr.toDoubleOrNull() ?: return "ค่าตัวเลขไม่ถูกต้อง: $valueStr"

        val result = convertUnit(value, fromUnit, toUnit)
            ?: return "ไม่รองรับการแปลงจาก '$fromUnit' เป็น '$toUnit'"

        val formatted = if (result == result.toLong().toDouble()) result.toLong().toString()
                        else "%.4f".format(result).trimEnd('0').trimEnd('.')
        return "$value $fromUnit = $formatted $toUnit"
    }

    private suspend fun executeSetReminder(args: Map<String, String>): String {
        val title = args["title"] ?: return "ต้องระบุหัวข้อ"
        val detail = args["detail"] ?: ""
        val whenStr = args["when"] ?: "ตามที่สะดวก"
        val timestamp = Clock.System.now().toEpochMilliseconds()
        
        _sideEffectDelegate?.onSetReminder(title, detail, whenStr, timestamp)
        
        return "ตั้งการแจ้งเตือน '$title' เรียบร้อยแล้ว (เวลา: $whenStr)"
    }

    private fun executeFormatJson(data: String): String {
        if (data.isBlank()) return "ไม่มีข้อมูลที่จะแปลง"
        // Simple key:value → JSON converter
        return try {
            if (data.trimStart().startsWith("{") || data.trimStart().startsWith("[")) {
                "JSON (ดูเหมือนเป็น JSON แล้ว):\n$data"
            } else {
                val pairs = data.split(",", "\n").mapNotNull { line ->
                    val parts = line.split(":", "=", limit = 2)
                    if (parts.size == 2) "  \"${parts[0].trim()}\": \"${parts[1].trim()}\"" else null
                }
                "JSON output:\n{\n${pairs.joinToString(",\n")}\n}"
            }
        } catch (e: Exception) {
            "แปลงเป็น JSON ไม่ได้: ${e.message}"
        }
    }

    private fun executeTranslate(args: Map<String, String>): String {
        val text = args["text"] ?: return "ต้องระบุข้อความ"
        val targetLang = args["target_lang"] ?: return "ต้องระบุภาษาปลายทาง"
        // Gemini จะ handle การแปลเอง — tool นี้แค่ forward คำขอ
        return "TRANSLATE_REQUEST::text=$text::to=$targetLang"
    }

    private fun executeSummarize(args: Map<String, String>): String {
        val text = args["text"] ?: return "ต้องระบุข้อความ"
        val length = args["length"] ?: "medium"
        return "SUMMARIZE_REQUEST::length=$length::text=$text"
    }

    private fun executeSearchWeb(query: String): String {
        if (query.isBlank()) return "ต้องระบุคำค้นหา"
        // Gemini Grounding จะถูกเปิดใช้งานผ่าน GeminiService
        return "WEB_SEARCH_REQUEST::query=$query"
    }

    /** identity_update — อัปเดตตัวตน AI/ผู้ใช้ผ่าน SideEffectDelegate (persist ลง Core Memory) */
    private suspend fun executeIdentityUpdate(args: Map<String, String>): String {
        val target = args["target"] ?: return "ต้องระบุ target (agent หรือ user)"
        val field  = args["field"]  ?: return "ต้องระบุ field"
        val value  = args["value"]  ?: return "ต้องระบุ value"
        val delegate = _sideEffectDelegate ?: return "⚠️ ระบบ identity ยังไม่พร้อมใช้งาน"
        return delegate.onUpdateIdentity(target, field, value)
    }

    /**
     * executeCustomSkill — custom tool ที่ Agent สร้างเอง (ผ่าน system_create_agent_tool)
     * คืน systemPromptAddon ของ tool กลับเข้า tool loop ให้ model ทำตามขั้นตอน
     * (model สามารถเรียก tool จริงอื่นประกอบได้ เช่น trading_price แล้วค่อยสรุปผล)
     */
    private suspend fun executeCustomSkill(call: ToolCall): String {
        val skill = ToolRegistry.getSkill(call.name)
            ?: return "❌ ไม่พบเครื่องมือ '${call.name}' ในระบบ — หากผู้ใช้ต้องการเครื่องมือนี้ " +
                    "ให้สร้างใหม่ด้วย system_create_agent_tool"
        return buildString {
            append("🛠️ เปิดใช้งานเครื่องมือ '${skill.name}' แล้ว\n")
            append("ให้ทำตามคำสั่งของเครื่องมือนี้เพื่อประมวลผลคำขอของผู้ใช้ต่อทันที ")
            append("(เรียกใช้ tool อื่นประกอบได้ตามความเหมาะสม แล้วสรุปผลให้ผู้ใช้):\n\n")
            append(skill.systemPromptAddon)
            append("\n\n[STRICT - สำคัญที่สุด]: ห้ามพูดตอบผู้ใช้ ห้ามบอกว่า \"รอสักครู่/กำลังประมวลผล\" ")
            append("และห้ามจบ turn ก่อนที่จะเรียก tool ตามขั้นตอนข้างต้นครบแล้วเด็ดขาด — ")
            append("ให้เรียก tool ทันทีใน turn นี้ (เรียกหลาย tool พร้อมกันได้) ")
            append("เมื่อได้ข้อมูลครบแล้วเท่านั้นจึงค่อยพูดสรุปให้ผู้ใช้ฟัง")
        }
    }

    // ─── Live Control (Vision / Voice) ──────────────────────────────────────

    /** เครื่องมือกลุ่มนี้ถูก declare อยู่ใน _cameraTools แต่ทำงานผ่าน delegate ไม่ใช่ CameraToolExecutor */
    private val liveControlToolNames = setOf(
        "vision_activate", "vision_deactivate", "voice_get_profiles", "voice_set_profile"
    )

    private suspend fun executeLiveControl(toolName: String, args: Map<String, String>): String {
        val delegate = _sideEffectDelegate
        return when (toolName) {
            "vision_activate" -> {
                if (delegate == null) return "⚠️ ระบบ Vision ยังไม่พร้อมใช้งาน"
                delegate.onVisionToggle(true)
                "✅ เปิดระบบมองเห็น (Vision) แล้ว — กล้องจะสตรีมภาพให้วิเคราะห์แบบเรียลไทม์"
            }
            "vision_deactivate" -> {
                if (delegate == null) return "⚠️ ระบบ Vision ยังไม่พร้อมใช้งาน"
                delegate.onVisionToggle(false)
                "✅ ปิดระบบมองเห็น (Vision) แล้ว"
            }
            "voice_get_profiles" ->
                "📋 รายชื่อเสียงที่ใช้ได้:\n${com.example.personalaibot.data.GeminiVoiceProfiles.getVoiceListSummary()}"
            "voice_set_profile" -> {
                val name = args["name"]?.trim()
                if (name.isNullOrBlank()) return "❌ กรุณาระบุชื่อเสียง (เรียก voice_get_profiles เพื่อดูรายชื่อ)"
                if (delegate == null) return "⚠️ ระบบเปลี่ยนเสียงยังไม่พร้อมใช้งาน"
                delegate.onVoiceChange(name)
                "✅ กำลังเปลี่ยนเสียงเป็น '$name' — ระบบเสียงจะ reconnect สั้นๆ เพื่อใช้เสียงใหม่"
            }
            else -> "❌ ไม่รู้จัก live control tool: $toolName"
        }
    }

    // ─── Report Display ──────────────────────────────────────────────────────

    /**
     * analyze_and_display_report — ส่งรายงาน markdown ยาวไปแสดงในแชทผ่าน delegate
     * แล้วคืนคำสั่งให้ model พูด/ตอบเฉพาะสรุปสั้นๆ (กัน model อ่านตารางออกเสียงจนเสียงขาด)
     */
    private suspend fun executeDisplayReport(args: Map<String, String>): String {
        val markdown = args["detailed_markdown"] ?: return "❌ ต้องระบุ detailed_markdown"
        val voiceSummary = args["voice_summary"] ?: ""
        _sideEffectDelegate?.onDisplayReport(markdown, voiceSummary)
        return "✅ รายงานฉบับเต็มถูกส่งแสดงในแชทแล้ว — ให้ตอบผู้ใช้เป็นภาษาสนทนา สรุปประเด็นสำคัญของรายงานให้ครบถ้วน: " +
               "ผลสรุป/ทิศทางหลัก + ตัวเลขและระดับราคาสำคัญ 3-5 จุด (แนวรับ-แนวต้าน จุดเข้า-ออก ตัวชี้วัดสำคัญ) + จุดที่ต้องระวัง รวมประมาณ 6-10 ประโยค " +
               "(อ้างอิง: $voiceSummary) — ไม่ต้องอ่านตารางหรือลิสต์ยาวๆ ซ้ำทุกบรรทัด เพราะผู้ใช้เห็นรายงานเต็มในแชทแล้ว"
    }

    // ─── Automation Alerts ───────────────────────────────────────────────────

    private suspend fun executeManageAlerts(args: Map<String, String>): String {
        val delegate = _sideEffectDelegate
            ?: return "⚠️ ระบบ Automation ยังไม่พร้อมใช้งาน"
        return delegate.onManageAlerts(args)
    }

    private suspend fun executeManageSchedule(args: Map<String, String>): String {
        val delegate = _sideEffectDelegate
            ?: return "⚠️ ระบบ Automation ยังไม่พร้อมใช้งาน"
        return delegate.onManageSchedule(args)
    }

    // ─── Math Evaluator ──────────────────────────────────────────────────────

    private fun evalMath(expr: String): Double {
        // Handle percentage
        val normalized = expr
            .replace("%", "/100")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", "${PI}")
            .replace("pi", "${PI}")
            .replace("e", "${E}")

        // Handle sqrt, abs, ceil, floor
        val withFunctions = normalized
            .replace(Regex("sqrt\\(([^)]+)\\)")) { sqrt(evalMath(it.groupValues[1])).toString() }
            .replace(Regex("abs\\(([^)]+)\\)"))  { abs(evalMath(it.groupValues[1])).toString() }
            .replace(Regex("ceil\\(([^)]+)\\)"))  { ceil(evalMath(it.groupValues[1])).toString() }
            .replace(Regex("floor\\(([^)]+)\\)")) { floor(evalMath(it.groupValues[1])).toString() }
            .replace(Regex("pow\\(([^,]+),([^)]+)\\)")) {
                (evalMath(it.groupValues[1])).pow(evalMath(it.groupValues[2])).toString()
            }

        return evalBasicMath(withFunctions)
    }

    /** Simple recursive descent parser for +, -, *, /, ^, () */
    private fun evalBasicMath(expr: String): Double {
        val tokens = tokenize(expr)
        val iter = tokens.iterator()
        return parseAddSub(iter)
    }

    private fun tokenize(expr: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val clean = expr.replace(" ", "")
        while (i < clean.length) {
            when {
                clean[i].isDigit() || clean[i] == '.' -> {
                    val start = i
                    while (i < clean.length && (clean[i].isDigit() || clean[i] == '.')) i++
                    result.add(clean.substring(start, i))
                }
                clean[i] in listOf('+', '-', '*', '/', '^', '(', ')') -> {
                    result.add(clean[i].toString())
                    i++
                }
                else -> i++
            }
        }
        return result
    }

    private fun parseAddSub(iter: Iterator<String>): Double {
        // Simplified — for complex expressions Gemini handles naturally
        var result = 0.0
        var tokens = mutableListOf<String>()
        for (t in iter) tokens.add(t)
        return evalTokens(tokens)
    }

    private fun evalTokens(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        if (tokens.size == 1) return tokens[0].toDoubleOrNull() ?: 0.0

        // Find last +/- outside parentheses (lowest precedence)
        var depth = 0
        var lastAddSub = -1
        for (i in tokens.indices.reversed()) {
            when (tokens[i]) {
                ")" -> depth++
                "(" -> depth--
                "+", "-" -> if (depth == 0 && i > 0) { lastAddSub = i; break }
            }
        }
        if (lastAddSub > 0) {
            val left = evalTokens(tokens.subList(0, lastAddSub))
            val right = evalTokens(tokens.subList(lastAddSub + 1, tokens.size))
            return if (tokens[lastAddSub] == "+") left + right else left - right
        }

        // Find last */ outside parentheses
        var lastMulDiv = -1
        depth = 0
        for (i in tokens.indices.reversed()) {
            when (tokens[i]) {
                ")" -> depth++
                "(" -> depth--
                "*", "/" -> if (depth == 0) { lastMulDiv = i; break }
            }
        }
        if (lastMulDiv >= 0) {
            val left = evalTokens(tokens.subList(0, lastMulDiv))
            val right = evalTokens(tokens.subList(lastMulDiv + 1, tokens.size))
            return if (tokens[lastMulDiv] == "*") left * right else left / right
        }

        // Handle parentheses
        if (tokens.first() == "(" && tokens.last() == ")") {
            return evalTokens(tokens.subList(1, tokens.size - 1))
        }

        // Handle power
        val powIdx = tokens.indexOf("^")
        if (powIdx >= 0) {
            val base = evalTokens(tokens.subList(0, powIdx))
            val exp = evalTokens(tokens.subList(powIdx + 1, tokens.size))
            return base.pow(exp)
        }

        return tokens.joinToString("").toDoubleOrNull() ?: 0.0
    }

    // ─── Unit Converter ──────────────────────────────────────────────────────

    private fun convertUnit(value: Double, from: String, to: String): Double? {
        // Normalize to SI base then convert to target
        return when {
            // Length
            from in lengthToMeter && to in lengthToMeter ->
                value * (lengthToMeter[from] ?: 1.0) / (lengthToMeter[to] ?: 1.0)
            // Mass
            from in massToKg && to in massToKg ->
                value * (massToKg[from] ?: 1.0) / (massToKg[to] ?: 1.0)
            // Temperature
            from == "celsius" || from == "c" || from == "°c" -> celsiusTo(value, to)
            from == "fahrenheit" || from == "f" || from == "°f" -> celsiusTo((value - 32) * 5/9, to)
            from == "kelvin" || from == "k" -> celsiusTo(value - 273.15, to)
            // Area
            from in areaToM2 && to in areaToM2 ->
                value * (areaToM2[from] ?: 1.0) / (areaToM2[to] ?: 1.0)
            // Speed
            from in speedToMs && to in speedToMs ->
                value * (speedToMs[from] ?: 1.0) / (speedToMs[to] ?: 1.0)
            else -> null
        }
    }

    private fun celsiusTo(celsius: Double, to: String): Double? = when (to) {
        "celsius", "c", "°c"         -> celsius
        "fahrenheit", "f", "°f"       -> celsius * 9/5 + 32
        "kelvin", "k"                  -> celsius + 273.15
        else -> null
    }

    private val lengthToMeter = mapOf(
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "cm" to 0.01, "mm" to 0.001,
        "mile" to 1609.344, "miles" to 1609.344,
        "yard" to 0.9144, "yards" to 0.9144,
        "foot" to 0.3048, "feet" to 0.3048, "ft" to 0.3048,
        "inch" to 0.0254, "inches" to 0.0254, "in" to 0.0254,
        "nautical_mile" to 1852.0
    )

    private val massToKg = mapOf(
        "kg" to 1.0, "kilogram" to 1.0, "kilograms" to 1.0,
        "g" to 0.001, "gram" to 0.001, "grams" to 0.001,
        "mg" to 0.000001, "milligram" to 0.000001,
        "lb" to 0.453592, "lbs" to 0.453592, "pound" to 0.453592, "pounds" to 0.453592,
        "oz" to 0.0283495, "ounce" to 0.0283495, "ounces" to 0.0283495,
        "ton" to 1000.0, "tonne" to 1000.0
    )

    private val areaToM2 = mapOf(
        "m2" to 1.0, "sqm" to 1.0, "square_meter" to 1.0, "square_meters" to 1.0,
        "km2" to 1_000_000.0, "square_km" to 1_000_000.0,
        "cm2" to 0.0001, "mm2" to 0.000001,
        "ft2" to 0.092903, "sqft" to 0.092903, "square_foot" to 0.092903, "square_feet" to 0.092903,
        "in2" to 0.00064516, "sqin" to 0.00064516,
        "yard2" to 0.836127, "sqyard" to 0.836127,
        "acre" to 4046.86, "acres" to 4046.86,
        "hectare" to 10000.0, "hectares" to 10000.0, "ha" to 10000.0,
        "rai" to 1600.0  // Thai unit
    )

    private val speedToMs = mapOf(
        "m/s" to 1.0, "ms" to 1.0, "meter_per_second" to 1.0,
        "km/h" to 0.277778, "kph" to 0.277778, "kmh" to 0.277778,
        "mph" to 0.44704, "mile_per_hour" to 0.44704,
        "knot" to 0.514444, "knots" to 0.514444,
        "ft/s" to 0.3048, "fps" to 0.3048
    )

    private fun convertUnits(value: Double, fromUnit: String, toUnit: String): String {
        val from = fromUnit.lowercase().trim()
        val to = toUnit.lowercase().trim()

        // Temperature — special case
        if (from in listOf("celsius", "c", "°c") || to in listOf("celsius", "c", "°c") ||
            from in listOf("fahrenheit", "f", "°f") || to in listOf("fahrenheit", "f", "°f") ||
            from in listOf("kelvin", "k") || to in listOf("kelvin", "k")) {
            return convertTemperature(value, from, to)
        }

        // Length
        val fromM = lengthToMeter[from]
        val toM = lengthToMeter[to]
        if (fromM != null && toM != null) {
            val result = value * fromM / toM
            return "$value $fromUnit = ${"%.6g".format(result)} $toUnit"
        }

        // Area
        val fromA = areaToM2[from]
        val toA = areaToM2[to]
        if (fromA != null && toA != null) {
            val result = value * fromA / toA
            return "$value $fromUnit = ${"%.6g".format(result)} $toUnit"
        }

        // Speed
        val fromS = speedToMs[from]
        val toS = speedToMs[to]
        if (fromS != null && toS != null) {
            val result = value * fromS / toS
            return "$value $fromUnit = ${"%.6g".format(result)} $toUnit"
        }

        // Mass
        val fromKg = massToKg[from]
        val toKg = massToKg[to]
        if (fromKg != null && toKg != null) {
            val result = value * fromKg / toKg
            return "$value $fromUnit = ${"%.6g".format(result)} $toUnit"
        }

        return "ไม่รองรับการแปลงจาก $fromUnit เป็น $toUnit"
    }

    private fun convertTemperature(value: Double, from: String, to: String): String {
        val celsius = when {
            from in listOf("celsius", "c", "°c") -> value
            from in listOf("fahrenheit", "f", "°f") -> (value - 32) * 5.0 / 9.0
            from in listOf("kelvin", "k") -> value - 273.15
            else -> return "ไม่รู้จักหน่วย $from"
        }
        val result = when {
            to in listOf("celsius", "c", "°c") -> celsius
            to in listOf("fahrenheit", "f", "°f") -> celsius * 9.0 / 5.0 + 32
            to in listOf("kelvin", "k") -> celsius + 273.15
            else -> return "ไม่รู้จักหน่วย $to"
        }
        return "$value $from = ${"%.2f".format(result)} $to"
    }
}
