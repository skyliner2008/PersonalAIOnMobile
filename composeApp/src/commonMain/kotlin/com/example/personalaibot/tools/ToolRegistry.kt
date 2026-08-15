package com.example.personalaibot.tools

import com.example.personalaibot.tools.trading.TradingToolDefinitions
import com.example.personalaibot.tools.trading.SmcToolDefinitions
import com.example.personalaibot.tools.file.FileToolDefinitions
import com.example.personalaibot.tools.strategy.StrategyToolDefinitions
import kotlinx.serialization.json.*

object ToolRegistry {
    private const val STRICT_TV_ONLY_TRADING_MODE = false

    val tvOnlyTradingFunctionNames = setOf(
        "trading_price",
        "trading_market_snapshot",
        "trading_top_gainers",
        "trading_top_losers",
        "trading_technical_analysis",
        "trading_multi_timeframe",
        "trading_bollinger_scan",
        "trading_oversold_scan",
        "trading_overbought_scan",
        "trading_volume_breakout",
        "trading_sentiment",
        "trading_news",
        "trading_combined",
        "trading_fundamental_analysis",
        "trading_fear_greed",
        "trading_macro_calendar",
        "trading_correlation_matrix",
        "trading_position_sizing",
        "automation_manage_alerts",
        "trading_deep_analysis_suite",
        "trading_harmonic_scan",
        "trading_elliot_modern_analysis",
        "trading_smc_analysis",
        "trading_smc_sweeps",
        "trading_smc_liquidity",
        "trading_smc_orderblocks",
        "trading_smc_structure",
        "trading_smc_flow",
        "trading_strategy_signal",
        "trading_signal_stats"
    )

    val mt5OnlyTradingFunctionNames = setOf(
        "trading_mt5_account_info",
        "trading_mt5_list_positions",
        "trading_mt5_list_orders",
        "trading_mt5_list_history",
        "trading_mt5_candles",
        "trading_mt5_analyze",
        "trading_mt5_symbol_info",
        "trading_mt5_symbol_search",
        "trading_mt5_snapshot",
        "trading_mt5_trade_actions",
        "trading_mt5_market_scanner",
        "trading_mt5_correlation_radar",
        "trading_mt5_sentiment_gauge",
        "trading_mt5_institutional_flow",
        "trading_mt5_economic_radar",
        "trading_mt5_trade_journal",
        "trading_mt5_order",
        "trading_mt5_close_position",
        "trading_mt5_modify_position",
        "trading_mt5_close_all",
        "trading_mt5_break_even_all"
    )

    private val _builtinTools: Map<String, FunctionDeclaration> = buildMap {
        put("get_current_datetime", FunctionDeclaration(
            name = "get_current_datetime",
            description = "Gets the current date and time.",
            parameters = null
        ))
        put("calculate", FunctionDeclaration(
            name = "calculate",
            description = "Calculates a mathematical expression.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "expression" to ParameterProperty(type = "STRING", description = "The mathematical expression to evaluate.")
                ),
                required = listOf("expression")
            )
        ))
        put("remember_fact", FunctionDeclaration(
            name = "remember_fact",
            description = "Saves an important piece of information to long-term memory.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "key"   to ParameterProperty("STRING", "The topic of the fact to remember."),
                    "value" to ParameterProperty("STRING", "The content of the fact to save."),
                    "importance" to ParameterProperty("STRING", "Importance level: low, medium, high")
                ),
                required = listOf("key", "value")
            )
        ))
        put("recall_memory", FunctionDeclaration(
            name = "recall_memory",
            description = "Retrieves information from long-term memory.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to ParameterProperty("STRING", "The topic to search for in memory.")
                ),
                required = listOf("query")
            )
        ))
        put("convert_units", FunctionDeclaration(
            name = "convert_units",
            description = "Converts a value from one unit to another (e.g., meters to feet, Celsius to Fahrenheit).",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "value"     to ParameterProperty("NUMBER", "The numerical value to convert."),
                    "from_unit" to ParameterProperty("STRING", "The source unit (e.g., km, celsius, kg)."),
                    "to_unit"   to ParameterProperty("STRING", "The target unit (e.g., miles, fahrenheit, pounds).")
                ),
                required = listOf("value", "from_unit", "to_unit")
            )
        ))
        put("set_reminder", FunctionDeclaration(
            name = "set_reminder",
            description = "Sets a reminder or TODO for the user.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "title"   to ParameterProperty("STRING", "The title of the reminder."),
                    "detail"  to ParameterProperty("STRING", "Additional details for the reminder."),
                    "when"    to ParameterProperty("STRING", "When to remind (e.g., 'tomorrow at 5pm', 'in 1 hour').")
                ),
                required = listOf("title")
            )
        ))
        put("translate_text", FunctionDeclaration(
            name = "translate_text",
            description = "Translates text from one language to another.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text"          to ParameterProperty("STRING", "The text to translate."),
                    "target_lang"   to ParameterProperty("STRING", "The target language (e.g., 'Thai', 'English', 'Japanese').")
                ),
                required = listOf("text", "target_lang")
            )
        ))
        put("summarize_text", FunctionDeclaration(
            name = "summarize_text",
            description = "Summarizes a long piece of text.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "text"   to ParameterProperty("STRING", "The text to summarize."),
                    "length" to ParameterProperty("STRING", "Desired length: 'short', 'medium', or 'detailed'.",
                        enum = listOf("short", "medium", "detailed"))
                ),
                required = listOf("text")
            )
        ))
        put("search_web", FunctionDeclaration(
            name = "search_web",
            description = "Searches the internet for up-to-date or real-time information.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to ParameterProperty("STRING", "The search query or question.")
                ),
                required = listOf("query")
            )
        ))
        put("identity_update", FunctionDeclaration(
            name = "identity_update",
            description = """Updates the AI agent's or user's identity profile. Use when the user asks to change your name/personality/style or how you address them (e.g., 'เปลี่ยนชื่อเป็น...', 'เรียกฉันว่าบอส', 'พูดตลกๆ หน่อย', 'เป็นเพศหญิง').
                |agent fields: name (ชื่อ AI), creature (บทบาท/สายพันธุ์), vibe (บุคลิก/น้ำเสียง), gender (เพศ)
                |user fields: name (ชื่อจริงผู้ใช้), call_name (การเรียกผู้ใช้), notes (หมายเหตุ เช่น ภาษาที่ใช้)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "target" to ParameterProperty("STRING", "Whose identity to update", enum = listOf("agent", "user")),
                    "field"  to ParameterProperty("STRING", "Field to update — agent: name, creature, vibe, gender | user: name, call_name, notes"),
                    "value"  to ParameterProperty("STRING", "New value for the field")
                ),
                required = listOf("target", "field", "value")
            )
        ))
        put("system_create_agent_tool", FunctionDeclaration(
            name = "system_create_agent_tool",
            description = "Creates a new custom tool/skill for the Agent by generating a JSON definition. Use this when the user asks you to create a new indicator, strategy, or capability. The tool will be saved locally and become available in the mobile app.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to ParameterProperty("STRING", "The unique name of the tool, starting with 'custom_' (e.g., 'custom_rsi_divergence')."),
                    "description" to ParameterProperty("STRING", "A short description of what the tool does."),
                    "triggerKeywords" to ParameterProperty("STRING", "Comma-separated keywords that trigger this tool (e.g., 'rsi, divergence, วิเคราะห์ rsi')."),
                    "systemPromptAddon" to ParameterProperty("STRING", "The step-by-step logic, prompt, or instructions the agent should follow when executing this tool. Be extremely detailed.")
                ),
                required = listOf("name", "description", "triggerKeywords", "systemPromptAddon")
            )
        ))
        put("system_list_agent_tools", FunctionDeclaration(
            name = "system_list_agent_tools",
            description = "Lists all custom tools/skills the Agent has previously created (name, description, trigger keywords, and internal logic). Use when the user asks what custom tools exist or wants to review/edit one.",
            parameters = null
        ))
        put("system_delete_agent_tool", FunctionDeclaration(
            name = "system_delete_agent_tool",
            description = "Permanently deletes a custom tool created by the Agent — removes both the file and its registry entry. Use when the user asks to remove a custom tool. To EDIT a tool, call system_create_agent_tool again with the same name to overwrite it.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to ParameterProperty("STRING", "The custom tool name to delete (with or without 'custom_' prefix).")
                ),
                required = listOf("name")
            )
        ))
        put("system_run_diagnostics", FunctionDeclaration(
            name = "system_run_diagnostics",
            description = "Runs a comprehensive system health check and generates a diagnostic report. Use this to troubleshoot price discrepancies, connection issues, or automation failures.",
            parameters = null
        ))
        put("system_check_connectivity", FunctionDeclaration(
            name = "system_check_connectivity",
            description = "Checks the internet connection and connectivity to key financial APIs (Yahoo, TradingView).",
            parameters = null
        ))
        put("system_self_review", FunctionDeclaration(
            name = "system_self_review",
            description = "Returns the bundled self-review document of JARVIS/PersonalAIBot (identity, 8 core capabilities, key numbers, roadmap). Use when the user asks you to review yourself, introduce your capabilities, or read/summarize the project README aloud (e.g. 'รีวิวตัวเองให้ฟังหน่อย', 'แนะนำตัวเอง'). NARRATION MODE: the user wants to HEAR the full review — narrate it aloud in natural spoken Thai, section by section, with NO length limit. Do NOT use analyze_and_display_report for this. Do NOT cut it short.",
            parameters = null
        ))
        put("chart_dashboard_control", FunctionDeclaration(
            name = "chart_dashboard_control",
            description = """Controls the on-screen chart dashboard (Lightweight Charts multi-pane). Use when the user asks to open/show/close a chart, change chart symbol or timeframe, change the pane layout, or toggle indicators — e.g. 'เปิดกราฟทองคำ', 'เปลี่ยนเป็น 4h', 'เพิ่ม RSI กับ MACD', 'เปิด EMA200', 'เอา Bollinger Bands ออก', 'สลับไปกราฟ TradingView'.
                |Layouts: single (chart only), rsi, macd, rsi_macd, volume, full (volume+rsi+macd subpanes).
                |Overlays on main pane: ema14, ema20, ema50, ema60, ema200, bb (Bollinger Bands), smc (SMC zones: Order Block / FVG / Liquidity / Premium-Discount — เปิดเมื่อผู้ใช้ขอ SMC เท่านั้น ไม่เปิดอัตโนมัติ).
                |open จะรีเซ็ต overlay ทั้งหมดตามพารามิเตอร์ overlays (ไม่ระบุ = ปิดทั้งหมด).""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "What to do", enum = listOf("open", "close", "set_layout", "set_symbol", "set_interval", "set_overlay", "set_view")),
                    "symbol" to ParameterProperty("STRING", "Symbol for set_symbol/open, e.g. XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty("STRING", "Timeframe for set_interval/open: 1m 5m 15m 30m 1h 4h 1d"),
                    "layout" to ParameterProperty("STRING", "Layout for set_layout", enum = listOf("single", "rsi", "macd", "rsi_macd", "volume", "full")),
                    "overlay" to ParameterProperty("STRING", "Indicator for set_overlay", enum = listOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")),
                    "overlays" to ParameterProperty("STRING", "Comma-separated overlays for open (e.g. 'ema14,ema60' — replaces all; omit = none)"),
                    "visible" to ParameterProperty("BOOLEAN", "true=show overlay, false=hide (set_overlay only, default true)"),
                    "view" to ParameterProperty("STRING", "set_view: dashboard (offline multi-pane) or tradingview (online TV widget)", enum = listOf("dashboard", "tradingview"))
                ),
                required = listOf("action")
            )
        ))
        put("analyze_and_display_report", FunctionDeclaration(
            name = "analyze_and_display_report",
            description = """Displays a detailed markdown report in the chat UI while you continue speaking a short voice summary.
                |IMPORTANT for Live Voice mode: whenever the answer requires long details, tables, or many numbers, call this tool with the full markdown report, then speak ONLY a short conversational summary (2-4 sentences) of the key findings. Never read tables aloud.
                |Use after gathering data from other tools (trading analysis, SMC, news, etc.).""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "detailed_markdown" to ParameterProperty("STRING", "The full markdown report to display in the chat (tables, headers, bullet points allowed here)."),
                    "voice_summary" to ParameterProperty("STRING", "A short 2-4 sentence conversational Thai summary of the key findings (this is what you speak).")
                ),
                required = listOf("detailed_markdown", "voice_summary")
            )
        ))
        put("mt5_place_order", FunctionDeclaration(
            name = "mt5_place_order",
            description = "Sends a live BUY/SELL order command to an MT5 bridge service.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "BUY or SELL", enum = listOf("BUY", "SELL")),
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD, EURUSD, BTCUSD"),
                    "volume" to ParameterProperty("NUMBER", "Lot size, e.g., 0.01"),
                    "sl" to ParameterProperty("NUMBER", "Stop loss price (optional)"),
                    "tp" to ParameterProperty("NUMBER", "Take profit price (optional)"),
                    "comment" to ParameterProperty("STRING", "Optional order comment"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("action", "symbol", "volume")
            )
        ))
        put("mt5_close_position", FunctionDeclaration(
            name = "mt5_close_position",
            description = "Sends a close-position command to an MT5 bridge service.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD"),
                    "ticket" to ParameterProperty("STRING", "Optional position ticket to close"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ))
    }

    // Copy-on-write immutable maps — mutation เกิดเฉพาะตอน register (เหตุการณ์หายาก)
    // ผู้อ่านจะไม่เห็น map ที่ถูกแก้ครึ่งทาง แม้ถูกเรียกจากหลาย coroutine พร้อมกัน
    // @Volatile กัน reader thread เห็น reference เก่าค้าง (visibility guarantee)
    @Volatile
    private var _customTools: Map<String, FunctionDeclaration> = emptyMap()
    @Volatile
    private var _skills: Map<String, SkillDescriptor> = emptyMap()

    // ─── Trading Tools (Real-time, TA, Sentiment, News) ──────────────────────
    private val supportedTradingToolNames = setOf(
        "trading_price",
        "trading_market_snapshot",
        "trading_top_gainers",
        "trading_top_losers",
        "trading_technical_analysis",
        "trading_multi_timeframe",
        "trading_bollinger_scan",
        "trading_oversold_scan",
        "trading_overbought_scan",
        "trading_volume_breakout",
        "trading_sentiment",
        "trading_news",
        "trading_combined",
        // V16.0 Advanced Suite
        "trading_fundamental_analysis",
        "trading_fear_greed",
        "trading_crypto_overview",
        "trading_macro_calendar",
        "trading_economic_data",
        "trading_correlation_matrix",
        "trading_position_sizing",
        "automation_manage_alerts",
        "automation_manage_schedule",
        "trading_deep_analysis_suite",
        "trading_harmonic_scan",
        "trading_elliot_modern_analysis",
        "trading_smc_flow",
        "trading_strategy_signal",
        "trading_signal_stats"
    )

    private val tvOnlyTradingToolNames = setOf(
        "trading_price",
        "trading_news",
        "trading_macro_calendar",
        "trading_harmonic_scan",
        "trading_elliot_modern_analysis"
    )

    private val activeTradingToolNames: Set<String> =
        if (STRICT_TV_ONLY_TRADING_MODE) tvOnlyTradingToolNames else supportedTradingToolNames

    private val _tradingTools: Map<String, FunctionDeclaration> =
        TradingToolDefinitions.allDefinitions
            .filter { it.name in activeTradingToolNames }
            .associateBy { it.name }

    // ─── MT5 Bridge Tools (separate category) ────────────────────────────────
    private val supportedMt5ToolNames = setOf(
        // MT5 Core Actions
        "trading_mt5_order",
        "trading_mt5_close_position",
        "trading_mt5_modify_position",
        // MT5 Core Agent (Query)
        "trading_mt5_account_info",
        "trading_mt5_list_positions",
        "trading_mt5_list_orders",
        "trading_mt5_list_history",
        "trading_mt5_candles",
        "trading_mt5_analyze",
        "trading_mt5_symbol_info",
        "trading_mt5_symbol_search",
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
        "trading_mt5_trade_journal"
    )

    private val _mt5Tools: Map<String, FunctionDeclaration> =
        TradingToolDefinitions.allDefinitions
            .filter { it.name in supportedMt5ToolNames }
            .associateBy { it.name }

    // ─── SMC (Smart Money Concepts) Tools ────────────────────────────────────
    private val _smcTools: Map<String, FunctionDeclaration> =
        SmcToolDefinitions.allDefinitions.associateBy { it.name }

    // ─── File Management Tools ───────────────────────────────────────────────
    private val _fileTools: Map<String, FunctionDeclaration> =
        FileToolDefinitions.allDefinitions.associateBy { it.name }

    // ─── Strategy Library (Quantpedia knowledge base) ────────────────────────
    private val _strategyTools: Map<String, FunctionDeclaration> =
        StrategyToolDefinitions.allDefinitions.associateBy { it.name }

    fun getGeminiTool(): GeminiTool = GeminiTool(
        functionDeclarations = _builtinTools.values.toList() +
                               _tradingTools.values.toList() +
                               _mt5Tools.values.toList() +
                               _smcTools.values.toList() +
                               _fileTools.values.toList() +
                               _strategyTools.values.toList() +
                               _cameraTools.values.toList() +
                               _customTools.values.toList() +
                               // skill ทุกตัวมี custom tool คู่กันอยู่แล้ว (register คู่กัน) —
                               // ส่งเฉพาะ skill ที่ไม่มี custom tool ชื่อซ้ำ กัน Gemini 400 "Duplicate function declaration"
                               _skills.values.filter { it.name !in _customTools }.map { skill ->
                                   FunctionDeclaration(
                                       name        = skill.name,
                                       description = skill.description,
                                       parameters  = null
                                   )
                               }
    )

    fun allToolNames(): Set<String> =
        _builtinTools.keys + _tradingTools.keys + _mt5Tools.keys + _smcTools.keys + _fileTools.keys + _strategyTools.keys + _cameraTools.keys + _customTools.keys + _skills.keys

    fun isTradingTool(name: String): Boolean =
        name in _tradingTools || name in _smcTools || name in _mt5Tools

    fun isMt5Tool(name: String): Boolean =
        name in _mt5Tools

    fun isFileTool(name: String): Boolean =
        name in _fileTools

    fun isStrategyTool(name: String): Boolean =
        name in _strategyTools

    fun isCameraTool(name: String): Boolean =
        name in _cameraTools

    fun isSystemTool(name: String): Boolean =
        name.startsWith("system_")

    fun registerCustomTool(decl: FunctionDeclaration) {
        _customTools = _customTools + (decl.name to decl)
    }

    fun registerSkill(skill: SkillDescriptor) {
        _skills = _skills + (skill.name to skill)
    }

    /** ลบ custom tool/skill ออกจาก registry (ใช้คู่กับลบไฟล์ใน custom_agent_tools/) */
    fun unregisterCustomTool(name: String) {
        _customTools = _customTools - name
        _skills = _skills - name
    }

    /** รายการ custom tool ที่ลงทะเบียนอยู่ (name → description) */
    fun listCustomTools(): Map<String, String> =
        _customTools.mapValues { it.value.description }

    fun getSkill(name: String): SkillDescriptor? = _skills[name]

    fun toJsonSchema(params: FunctionParameters?): String {
        if (params == null) return """{"type":"object","properties":{}}"""
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                params.properties.forEach { (name, prop) ->
                    putJsonObject(name) {
                        put("type", prop.type.lowercase())
                        put("description", prop.description)
                        prop.enum?.let { enumList ->
                            putJsonArray("enum") {
                                enumList.forEach { add(it) }
                            }
                        }
                    }
                }
            }
            if (params.required.isNotEmpty()) {
                putJsonArray("required") {
                    params.required.forEach { add(it) }
                }
            }
        }.toString()
    }

    // ─── Camera / Vision Tools ──────────────────────────────────────────────
    private val _cameraTools: Map<String, FunctionDeclaration> = buildMap {
        put("vision_activate", FunctionDeclaration(
            name = "vision_activate",
            description = "Turns ON the AI's eyes for real-time video analysis. IMPORTANT: Once active, you will receive a continuous live video stream. DO NOT call 'camera_analyze_scene' or any other camera tools while this is active, as you already have the visual data in your multimodal input.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "duration_seconds" to ParameterProperty("NUMBER", "How long to keep the eyes open (default 10s).")
                ),
                required = emptyList()
            )
        ))
        put("vision_deactivate", FunctionDeclaration(
            name = "vision_deactivate",
            description = "Turns OFF the AI's eyes. Call this immediately after you have gathered enough visual information to save the user's tokens.",
            parameters = null
        ))
        put("camera_analyze_scene", FunctionDeclaration(
            name = "camera_analyze_scene",
            description = "Analyzes a single camera frame (Snapshot mode). ONLY use this if 'vision_activate' is NOT active. If you are already in Live Vision mode, ignore this tool and use your live video input instead.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "prompt" to ParameterProperty("STRING", "Optional custom prompt to guide the analysis (e.g., 'read the text on the sign').")
                ),
                required = emptyList()
            )
        ))
        put("camera_detect_objects", FunctionDeclaration(
            name = "camera_detect_objects",
            description = "Detects and locates objects in the camera view with bounding boxes and confidence scores.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "target" to ParameterProperty("STRING", "Optional specific object to look for (e.g., 'cat', 'license plate').")
                ),
                required = emptyList()
            )
        ))
        put("camera_read_text", FunctionDeclaration(
            name = "camera_read_text",
            description = "Reads and extracts text (OCR) from the camera view — signs, documents, screens, labels.",
            parameters = null
        ))
        put("camera_switch_provider", FunctionDeclaration(
            name = "camera_switch_provider",
            description = "Switches the active AI vision provider for camera analysis.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "provider" to ParameterProperty(
                        "STRING",
                        "The provider to switch to.",
                        enum = listOf("gemini_live", "gemini_flash", "openai_gpt4o", "openai_gpt41", "claude_sonnet", "claude_opus")
                    )
                ),
                required = listOf("provider")
            )
        ))
        put("camera_switch_mode", FunctionDeclaration(
            name = "camera_switch_mode",
            description = "Changes the camera operating mode.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "mode" to ParameterProperty(
                        "STRING",
                        "The mode to switch to.",
                        enum = listOf("live_stream", "snapshot", "object_detect", "ar_overlay")
                    )
                ),
                required = listOf("mode")
            )
        ))

        // --- Voice / Persona Tools ---
        put("voice_get_profiles", FunctionDeclaration(
            name = "voice_get_profiles",
            description = "Returns a list of all 30 available Gemini Live voice profiles with their gender and tone descriptions.",
            parameters = null
        ))
        put("voice_set_profile", FunctionDeclaration(
            name = "voice_set_profile",
            description = "Changes the current assistant voice profile. You MUST call this tool whenever the user asks to change/try a voice — NEVER claim the voice has changed without calling this tool. The session reconnects briefly (~2s) and the new voice applies after reconnect.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to ParameterProperty("STRING", "The name of the voice profile to switch to (e.g., 'Puck', 'Kore', 'Aoede').")
                ),
                required = listOf("name")
            )
        ))
    }

    // ─── Tool Catalogue (for ToolListDialog) ────────────────────────────────

    data class ToolCategory(
        val name: String,
        val icon: String,
        val tools: List<FunctionDeclaration>
    )

    fun getToolCategories(): List<ToolCategory> = listOf(
        ToolCategory("🧠 Built-in Tools", "🧠", _builtinTools.values.toList()),
        ToolCategory("📊 Trading Tools", "📊", _tradingTools.values.toList()),
        ToolCategory("🔗 MT5 Bridge", "🔗", _mt5Tools.values.toList()),
        ToolCategory("📈 SMC Tools", "📈", _smcTools.values.toList()),
        ToolCategory("📚 Strategy Library", "📚", _strategyTools.values.toList()),
        ToolCategory("📁 File Management", "📁", _fileTools.values.toList()),
        ToolCategory("📷 Camera & Vision", "📷", _cameraTools.values.toList()),
        ToolCategory("🛠️ System Tools", "🛠️", _builtinTools.filter { it.key.startsWith("system_") }.values.toList()),
        ToolCategory("🛠 Custom Tools", "🛠", _customTools.values.toList())
    )

    fun totalToolCount(): Int =
        _builtinTools.size + _tradingTools.size + _mt5Tools.size + _smcTools.size + _fileTools.size + _strategyTools.size + _cameraTools.size + _customTools.size
}
