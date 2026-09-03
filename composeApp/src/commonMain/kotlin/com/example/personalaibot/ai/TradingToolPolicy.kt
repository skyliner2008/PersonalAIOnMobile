package com.example.personalaibot.ai

import com.example.personalaibot.tools.ToolRegistry

/**
 * TradingToolPolicy — ศูนย์กลางการตัดสินใจเรื่อง trading tool selection
 *
 * รวม logic ที่เคยซ้ำกัน 3 จุด (JarvisOrchestrator x2, GeminiService x2)
 * ให้เป็น module เดียว — ทุก provider path ใช้ policy เดียวกัน
 * จึงได้ semantics ตรงกันเสมอ:
 *
 * - prompt ธรรมดา       → ซ่อน trading tools ออกจาก tool spec (ประหยัด token)
 * - prompt trading ทั่วไป → อนุญาตทั้ง TV + MT5 tools ให้ model เลือกเอง
 * - prompt MT5 (strict)  → อนุญาตทั้งสองฝั่ง แต่ **suppress ผลลัพธ์ของ TV tools**
 *                          พร้อม system prompt บังคับให้ใช้ trading_mt5_analyze
 * - deep analysis        → ยกเลิก strict MT5 suppression (ต้องการข้อมูลรอบด้าน)
 */
data class TradingToolPolicy(
    val isTradingContext: Boolean,
    val mt5Mode: Boolean,
    val smcMode: Boolean,
    val deepAnalysis: Boolean,
    val signalAlertMode: Boolean,
    val analysisProfile: TradingIntentUtility.TradingAnalysisProfile = TradingIntentUtility.TradingAnalysisProfile.AI,
    val allowedTradingToolNames: Set<String>?,
    val policyLabel: String?
) {
    /**
     * tool นี้ควรถูกส่งใน tool spec หรือไม่
     * non-trading tool ผ่านเสมอ; trading tool ผ่านเมื่ออยู่ใน allowed set
     */
    fun isToolAllowed(name: String): Boolean {
        if (!ToolRegistry.isTradingTool(name)) return true
        val allowed = allowedTradingToolNames ?: return false
        return name in allowed
    }

    /**
     * Strict MT5 mode: ซ่อนผลลัพธ์ของ TV-derived tools ไม่ให้ model เห็น
     * (ยกเว้น deep analysis ที่ต้องการข้อมูลรอบด้าน)
     */
    fun shouldSuppressToolResult(toolName: String): Boolean =
        mt5Mode && !deepAnalysis && toolName in ToolRegistry.tvOnlyTradingFunctionNames

    /** ข้อความที่ส่งกลับเข้า tool loop เมื่อผลลัพธ์ถูก suppress */
    val suppressedResultMessage: String =
        "This tool result was suppressed because strict MT5 mode is active. " +
        "Please use trading_mt5_analyze for broker-specific analysis."

    /** System prompt addon สำหรับ strict MT5 mode (external provider path) */
    fun signalAlertSystemPromptAddon(): String = if (signalAlertMode) buildString {
        appendLine("\n[IMPORTANT: TRADINGVIEW SIGNAL ALERT MODE ACTIVE]")
        appendLine("- User is asking to create/modify a trading signal alert, not to query an MT5 broker.")
        appendLine("- Use automation_manage_alerts with tool_name='trading_signal_alert'.")
        appendLine("- Use TradingView-derived market data for the signal alert path.")
        appendLine("- DO NOT call trading_mt5_symbol_search, trading_mt5_candles, or other MT5 broker discovery tools.")
        appendLine("- If the user says all timeframes, create alerts for the configured canonical timeframes rather than asking for MT5 symbol names.")
    } else ""

    fun strictMt5SystemPromptAddon(): String = if (mt5Mode && !deepAnalysis) buildString {
        appendLine("\n[IMPORTANT: STRICT MT5 MODE ACTIVE]")
        appendLine("- User explicitly asked for MT5/Broker data.")
        appendLine("- Use 'trading_mt5_analyze' for all technical and SMC analysis (FVG, OB, Structure).")
        appendLine("- DO NOT use TradingView-derived tools (e.g., trading_smc_analysis, trading_price).")
    } else ""

    companion object {
        /** วิเคราะห์ user text (+ intent addon) แล้วคืน policy ที่เหมาะสม */
        fun evaluate(text: String, intentAddon: String = ""): TradingToolPolicy {
            val trading = TradingIntentUtility.isTradingPrompt(text, intentAddon)
            val smc     = TradingIntentUtility.isSmcPrompt(text, intentAddon)
            val mt5     = TradingIntentUtility.isMt5Prompt(text, intentAddon)
            val deep    = TradingIntentUtility.isDeepAnalysisPrompt(text, intentAddon)
            val signalAlert = TradingIntentUtility.isSignalAlertPrompt(text, intentAddon)
            val profile = TradingIntentUtility.tradingAnalysisProfile(text, intentAddon)

            val isTradingContext = trading || smc || mt5 || deep
            val allowed = if (isTradingContext) {
                when {
                    signalAlert -> ToolRegistry.tvOnlyTradingFunctionNames
                    mt5 -> ToolRegistry.tvOnlyTradingFunctionNames + ToolRegistry.mt5OnlyTradingFunctionNames
                    (profile == TradingIntentUtility.TradingAnalysisProfile.USER_QUERY || profile == TradingIntentUtility.TradingAnalysisProfile.COMPOSITE) -> ToolRegistry.tvOnlyTradingFunctionNames
                    else -> ToolRegistry.tvOnlyTradingFunctionNames + ToolRegistry.mt5OnlyTradingFunctionNames
                }
            } else null

            val label = when {
                signalAlert -> "TradingView Signal Alert mode"
                deep -> "Deep Confluence Suite mode"
                mt5  -> "MT5-only broker mode"
                profile == TradingIntentUtility.TradingAnalysisProfile.COMPOSITE -> "TradingView Composite Analysis + User Query mode"
                profile == TradingIntentUtility.TradingAnalysisProfile.USER_QUERY -> "TradingView User Query mode"
                isTradingContext -> "TradingView AI Analysis mode"
                else -> null
            }

            return TradingToolPolicy(
                isTradingContext = isTradingContext,
                mt5Mode = mt5,
                smcMode = smc,
                deepAnalysis = deep,
                signalAlertMode = signalAlert,
                analysisProfile = profile,
                allowedTradingToolNames = allowed,
                policyLabel = label
            )
        }
    }
}
