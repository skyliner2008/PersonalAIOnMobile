package com.example.personalaibot.tools

import com.example.personalaibot.tools.trading.TradingToolExecutor
import com.example.personalaibot.tools.camera.CameraToolExecutor
import io.ktor.client.*
import kotlinx.datetime.*
import kotlin.math.*

/**
 * ToolExecutor — รัน tool call จาก Gemini แล้วคืนผลลัพธ์
 *
 * ทุก built-in tool ถูก implement ที่นี่ใน pure Kotlin/KMP
 * Trading tools route ไปยัง TradingToolExecutor
 * Custom skills จะถูก route ไปยัง Gemini พร้อม systemPromptAddon ของ skill
 */
object ToolExecutor {

    // TradingToolExecutor จะถูก initialize เมื่อ HttpClient พร้อม
    private var _tradingExecutor: TradingToolExecutor? = null

    // Bridge สำหรับ File Management (Platform specific)
    private var _fileToolHandler: (suspend (String, Map<String, String>) -> String)? = null

    // Executor สำหรับ Camera & Vision
    private var _cameraExecutor: CameraToolExecutor? = null

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

    fun setSideEffectDelegate(delegate: SideEffectDelegate) {
        _sideEffectDelegate = delegate
    }

    /**
     * Execute a single tool call
     * @param call  ToolCall ที่ได้จาก Gemini function calling response
     * @return ToolResult ที่จะส่งกลับไปให้ Gemini
     */
    suspend fun execute(call: ToolCall, memoryContext: String = ""): ToolResult {
        return try {
            val result = when {
                // ─── Trading Tools ─────────────────────────────────────────
                ToolRegistry.isTradingTool(call.name) -> {
                    val trader = _tradingExecutor
                        ?: return ToolResult(call.name, "⚠️ Trading module ยังไม่พร้อม กรุณาตั้งค่า API Key ก่อน", true)
                    trader.execute(call.name, call.args)
                }
                // ─── File Management Tools ─────────────────────────────────
                ToolRegistry.isFileTool(call.name) -> {
                    _fileToolHandler?.invoke(call.name, call.args)
                        ?: "⚠️ ระบบจัดการไฟล์ยังไม่พร้อม หรือยังไม่ได้รับ Permission"
                }
                // ─── Camera & Vision Tools ─────────────────────────────────
                ToolRegistry.isCameraTool(call.name) -> {
                    _cameraExecutor?.execute(call.name, call.args)
                        ?: "⚠️ ระบบกล้องยังไม่พร้อม กรุณาเปิดกล้องก่อนใช้งาน"
                }
                // ─── Built-in Tools ────────────────────────────────────────
                else -> when (call.name) {
                "get_current_datetime" -> executeDateTime()
                "calculate"            -> executeCalculate(call.args["expression"] ?: "")
                "remember_fact"        -> executeRememberFact(call.args)
                "recall_memory"        -> executeRecallMemory(call.args["query"] ?: "", memoryContext)
                "convert_units"        -> executeConvertUnits(call.args)
                "set_reminder"         -> executeSetReminder(call.args)
                "format_json"          -> executeFormatJson(call.args["data"] ?: "")
                "translate_text"       -> executeTranslate(call.args)
                "summarize_text"       -> executeSummarize(call.args)
                "search_web"           -> executeSearchWeb(call.args["query"] ?: "")
                else                   -> executeCustomSkill(call)
                } // end inner when
            } // end outer when
            ToolResult(call.name, result)
        } catch (e: Exception) {
            ToolResult(call.name, "Error: ${e.message}", isError = true)
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

    private fun executeRecallMemory(query: String, memoryContext: String): String {
        if (memoryContext.isBlank()) return "ยังไม่มีข้อมูลใน memory"
        // Search for relevant lines in context
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

    private suspend fun executeCustomSkill(call: ToolCall): String {
        val skill = ToolRegistry.getSkill(call.name)
            ?: return "ไม่พบ skill '${call.name}'"
        // สำหรับ Phase ถัดไป: จัดการ Skill ผ่าน delegate
        return "กำลังดึงข้อมูลจากทักษะ '${skill.name}'..."
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