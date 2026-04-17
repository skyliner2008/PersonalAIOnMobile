package com.example.personalaibot.tools

import com.example.personalaibot.tools.trading.TradingToolDefinitions
import com.example.personalaibot.tools.trading.SmcToolDefinitions
import com.example.personalaibot.tools.file.FileToolDefinitions

object ToolRegistry {

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
    }

    private val _customTools = mutableMapOf<String, FunctionDeclaration>()
    private val _skills = mutableMapOf<String, SkillDescriptor>()

    // ─── Trading Tools (Real-time, TA, Sentiment, News) ──────────────────────
    private val _tradingTools: Map<String, FunctionDeclaration> =
        TradingToolDefinitions.allDefinitions.associateBy { it.name }

    // ─── SMC (Smart Money Concepts) Tools ────────────────────────────────────
    private val _smcTools: Map<String, FunctionDeclaration> =
        SmcToolDefinitions.allDefinitions.associateBy { it.name }

    // ─── File Management Tools ───────────────────────────────────────────────
    private val _fileTools: Map<String, FunctionDeclaration> =
        FileToolDefinitions.allDefinitions.associateBy { it.name }

    fun getGeminiTool(): GeminiTool = GeminiTool(
        functionDeclarations = _builtinTools.values.toList() +
                               _tradingTools.values.toList() +
                               _smcTools.values.toList() +
                               _fileTools.values.toList() +
                               _cameraTools.values.toList() +
                               _customTools.values.toList() +
                               _skills.values.map { skill ->
                                   FunctionDeclaration(
                                       name        = skill.name,
                                       description = skill.description,
                                       parameters  = null
                                   )
                               }
    )

    fun allToolNames(): Set<String> =
        _builtinTools.keys + _tradingTools.keys + _smcTools.keys + _fileTools.keys + _cameraTools.keys + _customTools.keys + _skills.keys

    fun isTradingTool(name: String): Boolean =
        name in _tradingTools || name in _smcTools

    fun isFileTool(name: String): Boolean =
        name in _fileTools

    fun isCameraTool(name: String): Boolean =
        name in _cameraTools

    fun isSystemTool(name: String): Boolean =
        name.startsWith("system_")

    fun registerCustomTool(decl: FunctionDeclaration) {
        _customTools[decl.name] = decl
    }

    fun registerSkill(skill: SkillDescriptor) {
        _skills[skill.name] = skill
    }

    fun getSkill(name: String): SkillDescriptor? = _skills[name]

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
            description = "Changes the current assistant voice profile. Note: This will cause a brief 2-second reconnect to apply the new voice.",
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
        ToolCategory("📈 SMC Tools", "📈", _smcTools.values.toList()),
        ToolCategory("📁 File Management", "📁", _fileTools.values.toList()),
        ToolCategory("📷 Camera & Vision", "📷", _cameraTools.values.toList()),
        ToolCategory("🛠️ System Tools", "🛠️", _builtinTools.filter { it.key.startsWith("system_") }.values.toList())
    )

    fun totalToolCount(): Int =
        _builtinTools.size + _tradingTools.size + _smcTools.size + _fileTools.size + _cameraTools.size + _customTools.size
}
