package com.example.personalaibot.ui.screen

import androidx.compose.ui.graphics.Color
import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.ToolRegistry

/**
 * Shared catalog used by ToolListScreen.
 * Data is built from ToolRegistry so the UI always reflects actual available tools.
 */
data class ToolInfo(
    val name: String,
    val displayName: String,
    val category: ToolCategory,
    val icon: String,
    val shortDesc: String,
    val capabilities: List<String>,
    val examplePrompts: List<String>
)

enum class ToolCategory(val label: String, val color: Color) {
    BUILTIN("Built-in", Color(0xFF00E5FF)),
    TRADING("Trading", Color(0xFF00C853)),
    SMC("SMC", Color(0xFFFF6D00)),
    FILE("Files", Color(0xFFFDD835)),
    VISION("Vision", Color(0xFF7C4DFF)),
    SYSTEM("System", Color(0xFF607D8B))
}

private data class ToolMetadata(
    val displayName: String? = null,
    val shortDesc: String? = null,
    val examples: List<String> = emptyList()
)

private val TOOL_METADATA: Map<String, ToolMetadata> = mapOf(
    "trading_market_snapshot" to ToolMetadata(
        displayName = "Market Snapshot",
        shortDesc = "ภาพรวมตลาดล่าสุดตามกลุ่ม/ภูมิภาค เช่น Tech, US, Crypto",
        examples = listOf(
            "ภาพรวมตลาดหุ้น กลุ่มเทค -> AI จะเรียก trading_market_snapshot",
            "สรุปตลาดวันนี้ US กับ Crypto -> AI จะเรียก trading_market_snapshot"
        )
    ),
    "trading_price" to ToolMetadata(
        displayName = "Real-time Price",
        shortDesc = "ดึงราคาปัจจุบันของหุ้น/คริปโต/ทอง/ฟอเร็กซ์",
        examples = listOf("ราคาทองตอนนี้ -> AI จะเรียก trading_price")
    ),
    "trading_technical_analysis" to ToolMetadata(
        displayName = "Technical Analysis",
        shortDesc = "วิเคราะห์อินดิเคเตอร์หลัก เช่น RSI/MACD/EMA และสรุปสัญญาณ",
        examples = listOf("วิเคราะห์ TA BTC 1h -> AI จะเรียก trading_technical_analysis")
    ),
    "trading_news" to ToolMetadata(
        displayName = "Financial News",
        shortDesc = "ค้นข่าวการเงิน/เศรษฐกิจล่าสุดที่กระทบตลาด",
        examples = listOf("ข่าวทองล่าสุด -> AI จะเรียก trading_news")
    ),
    "trading_combined" to ToolMetadata(
        displayName = "Combined Analysis",
        shortDesc = "รวม TA + Sentiment + News เพื่อช่วยตัดสินใจ",
        examples = listOf("วิเคราะห์ BTC แบบครบทุกมุม -> AI จะเรียก trading_combined")
    ),
    "trading_multi_timeframe" to ToolMetadata(
        displayName = "Multi-Timeframe Analysis",
        shortDesc = "วิเคราะห์หลาย TF พร้อมกัน เช่น 15m/1h/4h/1d",
        examples = listOf("ดูแนวโน้ม XAUUSD หลาย TF -> AI จะเรียก trading_multi_timeframe")
    ),
    "trading_smc_analysis" to ToolMetadata(
        displayName = "SMC Dashboard",
        shortDesc = "วิเคราะห์เชิง Smart Money Concepts",
        examples = listOf("วิเคราะห์ SMC XAUUSD -> AI จะเรียก trading_smc_analysis")
    ),
    "search_web" to ToolMetadata(
        displayName = "Web Search",
        shortDesc = "ค้นข้อมูลล่าสุดบนอินเทอร์เน็ตแบบเรียลไทม์",
        examples = listOf("มีข่าวอะไรใหม่เกี่ยวกับ Fed -> AI จะเรียก search_web")
    ),
    "summarize_text" to ToolMetadata(
        displayName = "Summarizer",
        shortDesc = "สรุปข้อความยาวให้อ่านง่าย",
        examples = listOf("สรุปรายงานนี้ให้สั้น -> AI จะเรียก summarize_text")
    ),
    "translate_text" to ToolMetadata(
        displayName = "Translator",
        shortDesc = "แปลภาษาระหว่างไทย/อังกฤษ/ภาษาอื่น",
        examples = listOf("แปลเป็นอังกฤษให้หน่อย -> AI จะเรียก translate_text")
    )
)

val ALL_TOOLS: List<ToolInfo> by lazy {
    ToolRegistry.getGeminiTool().functionDeclarations
        .distinctBy { it.name }
        .map { decl ->
            val meta = TOOL_METADATA[decl.name]
            val category = categorize(decl)
            ToolInfo(
                name = decl.name,
                displayName = meta?.displayName ?: prettifyToolName(decl.name),
                category = category,
                icon = iconFor(category),
                shortDesc = meta?.shortDesc ?: decl.description,
                capabilities = capabilitiesFrom(decl),
                examplePrompts = meta?.examples?.ifEmpty { defaultExamplesFor(decl) } ?: defaultExamplesFor(decl)
            )
        }
        .sortedWith(compareBy<ToolInfo> { it.category.ordinal }.thenBy { it.displayName })
}

private fun categorize(decl: FunctionDeclaration): ToolCategory {
    val name = decl.name
    return when {
        ToolRegistry.isSystemTool(name) -> ToolCategory.SYSTEM
        ToolRegistry.isFileTool(name) -> ToolCategory.FILE
        ToolRegistry.isCameraTool(name) -> ToolCategory.VISION
        name.contains("smc", ignoreCase = true) -> ToolCategory.SMC
        ToolRegistry.isTradingTool(name) -> ToolCategory.TRADING
        else -> ToolCategory.BUILTIN
    }
}

private fun iconFor(category: ToolCategory): String = when (category) {
    ToolCategory.BUILTIN -> "*"
    ToolCategory.TRADING -> "$"
    ToolCategory.SMC -> "#"
    ToolCategory.FILE -> "F"
    ToolCategory.VISION -> "V"
    ToolCategory.SYSTEM -> "S"
}

private fun capabilitiesFrom(decl: FunctionDeclaration): List<String> {
    val props = decl.parameters?.properties?.keys?.toList().orEmpty()
    return if (props.isEmpty()) {
        listOf("No parameters (AI can call directly)")
    } else {
        props.map { key -> "Parameter: $key" }
    }
}

private fun defaultExamplesFor(decl: FunctionDeclaration): List<String> {
    val title = prettifyToolName(decl.name)
    return listOf("ขอใช้ $title -> AI จะเรียก ${decl.name}")
}

private fun prettifyToolName(raw: String): String {
    return raw
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            when (token.lowercase()) {
                "smc" -> "SMC"
                "api" -> "API"
                "ta" -> "TA"
                else -> token.replaceFirstChar { it.uppercase() }
            }
        }
}
