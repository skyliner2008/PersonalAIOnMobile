package com.example.personalaibot.tools

import com.example.personalaibot.tools.trading.TradingToolDefinitions
import com.example.personalaibot.tools.trading.SmcToolDefinitions

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
        put("analyze_and_display_report", FunctionDeclaration(
            name = "analyze_and_display_report",
            description = "Displays a high-quality Markdown analysis report to the Chat UI. Use this when you have gathered enough data to provide a comprehensive analysis.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "detailed_markdown" to ParameterProperty("STRING", "The full, detailed report for the Chat UI (like in Chat mode). Include tables and analysis."),
                    "voice_summary_hint" to ParameterProperty("STRING", "A short summary of what you will say to the user via voice (3-5 sentences).")
                ),
                required = listOf("detailed_markdown", "voice_summary_hint")
            )
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

    fun getGeminiTool(): GeminiTool = GeminiTool(
        functionDeclarations = _builtinTools.values.toList() +
                               _tradingTools.values.toList() +
                                              _smcTools.values.toList() +
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
        _builtinTools.keys + _tradingTools.keys + _smcTools.keys + _customTools.keys + _skills.keys

    fun isTradingTool(name: String): Boolean =
        name in _tradingTools || name in _smcTools

    fun getSkill(name: String): SkillDescriptor? = _skills[name]

    fun registerCustomTool(declaration: FunctionDeclaration) {
        _customTools[declaration.name] = declaration
    }

    fun registerSkill(descriptor: SkillDescriptor) {
        _skills[descriptor.name] = descriptor
    }

    fun unregisterSkill(name: String) {
        _skills.remove(name)
    }

    fun clearCustomTools() {
        _customTools.clear()
    }
}
