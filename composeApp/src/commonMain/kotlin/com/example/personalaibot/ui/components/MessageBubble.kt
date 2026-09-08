package com.example.personalaibot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.Message
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcAnalysisResult
import com.example.personalaibot.ui.theme.JarvisTheme
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** config ของ chart card ที่ AI ฝังในแชท (```chart fence) */
data class ChartCardConfig(
    val symbol: String,
    val interval: String,
    val layout: String,
    val overlays: Set<String>
)

/** key cache ของการ์ด — normalize ให้ตรงกับ JarvisViewModel.chartCardKey */
private fun chartCardKey(symbol: String, interval: String): String {
    val sym = symbol.trim().uppercase().replace(" ", "")
    val tf = interval.trim().lowercase().let { if (it == "d") "1d" else it }
    return "$sym/$tf"
}

@Composable
fun MessageBubble(
    message: Message,
    onOpenChart: ((ChartCardConfig) -> Unit)? = null,
    chartCardCache: Map<String, Triple<List<Candle>, SmcAnalysisResult?, List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>>> = emptyMap(),
    onChartCardShown: ((ChartCardConfig) -> Unit)? = null
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // JARVIS avatar dot
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .background(JarvisTheme.Cyan.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = if (isUser) JarvisTheme.Cyan.copy(0.15f) else JarvisTheme.Card,
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Text(
                        "JARVIS",
                        color = JarvisTheme.Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                // ถ้า metadata เป็น alert/signal card → render การ์ด 3D แทนข้อความ markdown
                val alertCard = remember(message.metadata, message.content) { parseAlertCardMeta(message.metadata, message.content) }
                if (alertCard != null) {
                    when (alertCard) {
                        is AlertCardMeta.Signal -> SignalAlertCard3D(alertCard)
                        is AlertCardMeta.Anticipation -> AnticipationAlertCard3D(alertCard)
                        is AlertCardMeta.Keyzone -> KeyzoneAlertCard3D(alertCard)
                        is AlertCardMeta.Generic -> GenericAlertCard(alertCard)
                    }
                } else {
                    SelectionContainer {
                        RenderedMessage(
                            content = message.content,
                            color = if (isUser) Color.White else Color.White.copy(0.9f),
                            onOpenChart = onOpenChart,
                            chartCardCache = chartCardCache,
                            onChartCardShown = onChartCardShown
                        )
                    }
                }
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .background(JarvisTheme.Cyan.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("U", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Content parsing: text / markdown table / chart fence ────────────────────

private sealed class MsgBlock {
    data class TextBlock(val lines: List<String>) : MsgBlock()
    data class TableBlock(val rows: List<List<String>>) : MsgBlock()
    data class ChartBlock(val config: ChartCardConfig) : MsgBlock()
}

private fun parseBlocks(content: String): List<MsgBlock> {
    val blocks = mutableListOf<MsgBlock>()
    val textBuf = mutableListOf<String>()
    val tableBuf = mutableListOf<List<String>>()
    val lines = content.lines()
    var i = 0

    fun flushText() {
        if (textBuf.isNotEmpty()) { blocks.add(MsgBlock.TextBlock(textBuf.toList())); textBuf.clear() }
    }
    fun flushTable() {
        if (tableBuf.isNotEmpty()) { blocks.add(MsgBlock.TableBlock(tableBuf.toList())); tableBuf.clear() }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            // ```chart {json}``` fence
            trimmed.startsWith("```chart") -> {
                flushText(); flushTable()
                val jsonBuf = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    jsonBuf.appendLine(lines[i]); i++
                }
                parseChartConfig(jsonBuf.toString())?.let { blocks.add(MsgBlock.ChartBlock(it)) }
                    ?: run { textBuf.add("(chart block อ่านไม่สำเร็จ)") }
            }
            // markdown table row
            trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                flushText()
                val cells = trimmed.trim('|').split("|").map { it.trim() }
                // ข้าม separator row (|---|---|)
                if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) tableBuf.add(cells)
            }
            else -> {
                flushTable()
                textBuf.add(line)
            }
        }
        i++
    }
    flushText(); flushTable()
    return blocks
}

private fun parseChartConfig(jsonStr: String): ChartCardConfig? {
    return try {
        val obj = Json.parseToJsonElement(jsonStr.trim()).jsonObject
        val symbol = obj["symbol"]?.jsonPrimitive?.content ?: return null
        val interval = obj["interval"]?.jsonPrimitive?.content ?: "1h"
        val layout = obj["layout"]?.jsonPrimitive?.content ?: "rsi_macd"
        val overlays = obj["overlays"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        ChartCardConfig(symbol, interval, layout, overlays)
    } catch (_: Exception) { null }
}

@Composable
fun RenderedMessage(
    content: String,
    color: Color,
    onOpenChart: ((ChartCardConfig) -> Unit)? = null,
    chartCardCache: Map<String, Triple<List<Candle>, SmcAnalysisResult?, List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>>> = emptyMap(),
    onChartCardShown: ((ChartCardConfig) -> Unit)? = null
) {
    val blocks = parseBlocks(content)
    Column {
        blocks.forEach { block ->
            when (block) {
                is MsgBlock.TextBlock -> block.lines.forEach { line ->
                    TextLine(line, color)
                }
                is MsgBlock.TableBlock -> MarkdownTable(block.rows, color)
                is MsgBlock.ChartBlock -> ChartCard(block.config, onOpenChart, chartCardCache, onChartCardShown)
            }
        }
    }
}

@Composable
private fun TextLine(line: String, color: Color) {
    val annotatedString = buildAnnotatedString {
        var current = line
        while (current.contains("**")) {
            val start = current.indexOf("**")
            val end = current.indexOf("**", start + 2)
            if (end != -1) {
                append(current.substring(0, start))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(current.substring(start + 2, end))
                }
                current = current.substring(end + 2)
            } else {
                break
            }
        }
        append(current)
    }

    Text(
        text = annotatedString,
        color = color,
        fontSize = 14.sp,
        fontFamily = FontFamily.Default,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

/** ตาราง markdown → ตารางจริง (header เน้นสี, zebra rows, scroll แนวนอนได้บนจอแคบ) */
@Composable
private fun MarkdownTable(rows: List<List<String>>, color: Color) {
    if (rows.isEmpty()) return
    val colCount = rows.maxOf { it.size }
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            rows.forEachIndexed { rowIdx, row ->
                val isHeader = rowIdx == 0
                Row(
                    modifier = Modifier
                        .background(
                            when {
                                isHeader -> JarvisTheme.Cyan.copy(alpha = 0.18f)
                                rowIdx % 2 == 0 -> Color.White.copy(alpha = 0.04f)
                                else -> Color.Transparent
                            }
                        )
                ) {
                    for (col in 0 until colCount) {
                        val cell = row.getOrNull(col) ?: ""
                        Text(
                            text = cell,
                            color = if (isHeader) JarvisTheme.Cyan else color,
                            fontSize = 12.sp,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/** เทียบ symbol แบบหละหลวม: "XAUUSD=X" == "XAUUSD", "BINANCE:BTCUSDT" == "BTCUSDT" */
private fun symbolsMatch(a: String, b: String): Boolean {
    fun norm(s: String) = s.uppercase()
        .substringAfter(':')
        .replace("=X", "")
        .filter { it.isLetterOrDigit() }
    return norm(a) == norm(b)
}

/**
 * การ์ดกราฟในแชท (Phase 3) —
 * ถ้า symbol ตรงกับกราฟที่ระบบโหลดข้อมูลอยู่ → ฝัง mini-chart สด (Lightweight Charts) ในแชทเลย
 * ถ้าไม่ตรง/ยังไม่มีข้อมูล → fallback เป็นการ์ดลิงก์ (แตะเปิดกราฟเต็มจอ)
 */
@Composable
private fun ChartCard(
    config: ChartCardConfig,
    onOpenChart: ((ChartCardConfig) -> Unit)?,
    chartCardCache: Map<String, Triple<List<Candle>, SmcAnalysisResult?, List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>>>,
    onChartCardShown: ((ChartCardConfig) -> Unit)?
) {
    // การ์ดปรากฏแต่ข้อมูลของการ์ดใบนี้ยังไม่มีใน cache → ขอให้ ViewModel โหลด (ไม่เปิดหน้ากราฟ)
    LaunchedEffect(config.symbol, config.interval, config.overlays) {
        onChartCardShown?.invoke(config)
    }

    // อ่านข้อมูลเฉพาะของการ์ดใบนี้จาก cache (key = symbol/interval) — การ์ดเก่าไม่เปลี่ยนตามการ์ดใหม่
    val entry = chartCardCache[chartCardKey(config.symbol, config.interval)]
    val candles = entry?.first ?: emptyList()
    val smcResult = entry?.second
    val signalMarkers = entry?.third ?: emptyList()
    val showLiveChart = candles.isNotEmpty()

    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(enabled = onOpenChart != null) { onOpenChart?.invoke(config) }
            .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${config.symbol} · ${config.interval}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (showLiveChart) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "LIVE",
                        color = Color(0xFF26A69A),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showLiveChart) {
                Spacer(modifier = Modifier.height(8.dp))
                MiniChart(config = config, candles = candles, smcResult = smcResult, markers = signalMarkers)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "แตะเพื่อเปิดกราฟเต็มจอ →",
                    color = JarvisTheme.Cyan.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "layout: ${config.layout}" +
                        if (config.overlays.isNotEmpty()) " · ${config.overlays.joinToString(", ").uppercase()}" else "",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "แตะเพื่อเปิดกราฟเต็มจอ →",
                    color = JarvisTheme.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** mini live chart ฝังในแชท — engine เดียวกับ dashboard เต็มจอ, guard pageReady เหมือนกัน */
@Composable
private fun MiniChart(
    config: ChartCardConfig,
    candles: List<Candle>,
    smcResult: SmcAnalysisResult? = null,
    markers: List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker> = emptyList()
) {
    val state = remember { WebViewState(WebContent.Url("file:///android_asset/chart_dashboard/index.html")) }
    val navigator = rememberWebViewNavigator()

    state.webSettings.isJavaScriptEnabled = true
    state.webSettings.androidWebSettings.safeBrowsingEnabled = true
    state.webSettings.androidWebSettings.domStorageEnabled = true

    // evaluateJavaScript ก่อนหน้าเว็บโหลดเสร็จจะถูกทิ้งเงียบๆ — ต้องรอ Finished ก่อนเสมอ
    val pageReady = state.loadingState is com.multiplatform.webview.web.LoadingState.Finished

    LaunchedEffect(pageReady) {
        if (!pageReady) return@LaunchedEffect
        val cfg = buildJsonObject {
            put("symbol", config.symbol)
            put("interval", config.interval)
            put("layout", config.layout)
            put("overlays", buildJsonObject {
                config.overlays.forEach { put(it, true) }
                listOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "donchian", "signals").forEach {
                    if (it !in config.overlays) put(it, false)
                }
            })
        }
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.init('${cfg.toString().replace("'", "\\'")}');"
        )
    }

    LaunchedEffect(pageReady, candles) {
        if (!pageReady || candles.isEmpty()) return@LaunchedEffect
        val json = buildJsonArray {
            candles.forEach { c ->
                add(buildJsonObject {
                    put("time", c.timestamp)
                    put("open", c.open)
                    put("high", c.high)
                    put("low", c.low)
                    put("close", c.close)
                    put("volume", c.volume)
                })
            }
        }
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.setCandles('${json.toString().replace("'", "\\'")}');"
        )
    }

    // วาด SMC zones เฉพาะเมื่อ config ขอ overlay "smc" (OB/FVG + Liquidity + Premium/Discount/EQ)
    LaunchedEffect(pageReady, smcResult, candles) {
        if (!pageReady || "smc" !in config.overlays) return@LaunchedEffect
        val smc = smcResult ?: return@LaunchedEffect
        if (candles.isEmpty()) return@LaunchedEffect
        val zonesJson = buildSmcZonesJson(smc, candles)
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.drawSMC('${zonesJson.replace("'", "\\'")}');"
        )
    }

    // วาด signal markers (BUY/SELL arrows) เฉพาะเมื่อ config ขอ overlay "signals"
    LaunchedEffect(pageReady, markers, candles) {
        if (!pageReady || "signals" !in config.overlays) return@LaunchedEffect
        if (markers.isEmpty() || candles.isEmpty()) return@LaunchedEffect
        val json = buildJsonArray {
            markers.forEach { m ->
                add(buildJsonObject {
                    put("time", m.time)
                    put("side", m.side)
                    put("label", m.label)
                    put("color", m.color)
                })
            }
        }
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.drawMarkers('${json.toString().replace("'", "\\'")}');"
        )
    }

    WebView(
        state = state,
        navigator = navigator,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}


// ─── Alert Cards (3D) — render จาก metadata JSON ของข้อความ alert ─────────────
// service แนบ metadata {kind:"signal"|"alert", ...} มากับข้อความ (persist ใน DB ด้วย)
// UI parse แล้ว render เป็นการ์ดมีมิติ (gradient + เงา + ป้ายสี) แทนข้อความ markdown

internal sealed class AlertCardMeta {
    data class Signal(
        val side: String, val symbol: String, val strategy: String,
        val entry: String, val tp: String, val sl: String, val rr: String,
        val atr: String?, val reason: String, val summary: String?, val voice: String?
    ) : AlertCardMeta()

    data class Anticipation(
        val name: String, val symbol: String, val side: String,
        val zone: String, val desc: String, val confidence: String,
        val price: String, val mtf: String?, val summary: String?, val voice: String?
    ) : AlertCardMeta()

    data class Keyzone(
        val name: String, val symbol: String, val desc: String,
        val price: String, val mtf: String?, val summary: String?, val voice: String?
    ) : AlertCardMeta()

    data class Generic(
        val name: String, val symbol: String, val condition: String,
        val current: String, val summary: String?, val voice: String?,
        val rawContent: String? = null
    ) : AlertCardMeta()
}

internal fun parseAlertCardMeta(metadata: String?, rawContent: String? = null): AlertCardMeta? {
    if (metadata.isNullOrBlank()) return null
    return try {
        val obj = Json.parseToJsonElement(metadata).jsonObject
        fun str(k: String) = obj[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        when (str("kind")) {
            "signal" -> AlertCardMeta.Signal(
                side = str("side") ?: "-", symbol = str("symbol") ?: "-",
                strategy = str("strategy") ?: "-", entry = str("entry") ?: "-",
                tp = str("tp") ?: "-", sl = str("sl") ?: "-", rr = str("rr") ?: "-",
                atr = str("atr"), reason = str("reason") ?: "-",
                summary = str("summary"), voice = str("voice")
            )
            "anticipation" -> AlertCardMeta.Anticipation(
                name = str("name") ?: "คาดการณ์สัญญาณ",
                symbol = str("symbol") ?: "-",
                side = str("side") ?: "BUY",
                zone = str("zone") ?: "Keyzone",
                desc = str("desc") ?: "เฝ้าระวังการกลับตัวในโซนสำคัญ",
                confidence = str("confidence") ?: "75",
                price = str("price") ?: "-",
                mtf = str("mtf"),
                summary = str("summary"),
                voice = str("voice")
            )
            "keyzone" -> AlertCardMeta.Keyzone(
                name = str("name") ?: "Keyzone Hit",
                symbol = str("symbol") ?: "-",
                desc = str("desc") ?: "ราคาแตะจุดสำคัญของโครงสร้างตลาด",
                price = str("price") ?: "-",
                mtf = str("mtf"),
                summary = str("summary"),
                voice = str("voice")
            )
            "alert" -> {
                val cond = str("condition") ?: "-"
                // รองรับประวัติแจ้งเตือนเก่าที่ condition คือ signal_anticipation แต่ metadata ถูกบันทึกเป็น alert
                if (cond.contains("signal_anticipation")) {
                    val isBuy = rawContent?.contains("BUY", ignoreCase = true) == true ||
                        rawContent?.contains("ซื้อ") == true ||
                        rawContent?.contains("🟢") == true
                    val isSell = rawContent?.contains("SELL", ignoreCase = true) == true ||
                        rawContent?.contains("ขาย") == true ||
                        rawContent?.contains("🔴") == true
                    val side = if (isSell && !isBuy) "SELL" else "BUY"
                    val desc = rawContent?.lineSequence()
                        ?.firstOrNull { it.contains("เฝ้าระวัง") || it.contains("โซน") || it.contains("Rejection") }
                        ?.replace("**", "")?.trim()
                        ?: "เฝ้าระวังการกลับตัวในโซนสำคัญ"
                    AlertCardMeta.Anticipation(
                        name = str("name") ?: "คาดการณ์สัญญาณ",
                        symbol = str("symbol") ?: "-",
                        side = side,
                        zone = if (rawContent?.contains("Demand", ignoreCase = true) == true) "Demand Zone"
                               else if (rawContent?.contains("Supply", ignoreCase = true) == true) "Supply Zone"
                               else "Keyzone",
                        desc = desc,
                        confidence = "75",
                        price = "-",
                        mtf = null,
                        summary = str("summary"),
                        voice = str("voice")
                    )
                } else {
                    AlertCardMeta.Generic(
                        name = str("name") ?: "Alert", symbol = str("symbol") ?: "-",
                        condition = cond, current = str("current") ?: "-",
                        summary = str("summary"), voice = str("voice"),
                        rawContent = rawContent
                    )
                }
            }
            else -> null
        }
    } catch (_: Exception) { null }
}

/** shell การ์ดมีมิติ — เงา + gradient accent → Card → Surface + ขอบสี accent */
@Composable
private fun AlertCardShell(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            JarvisTheme.Card,
                            JarvisTheme.Surface
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) { content() }
    }
}

@Composable
private fun CardFieldRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(0.55f), fontSize = 12.sp, modifier = Modifier.width(84.dp))
        Text(
            value, color = valueColor, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CardSummaryBlock(accent: Color, summary: String) {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(0.3f)))
    Spacer(Modifier.height(8.dp))
    Text("JARVIS quick-check", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Text(summary, color = Color.White.copy(0.85f), fontSize = 12.sp, lineHeight = 17.sp)
}

/** footer ล่างการ์ด — บอก engine เสียงที่พูดจริง (Live 2.5 Native / Live 3.1 / Android TTS) */
@Composable
private fun CardVoiceFooter(accent: Color, voice: String) {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(0.2f)))
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔊", fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            "เสียง: $voice",
            color = Color.White.copy(0.5f),
            fontSize = 10.sp
        )
    }
}

/** การ์ดสัญญาณเทรด — เขียว BUY / แดง SELL, แสดงเฉพาะ field ที่จำเป็น (Entry/TP/SL/RR/ATR + เหตุผล) */
@Composable
private fun SignalAlertCard3D(meta: AlertCardMeta.Signal) {
    val isBuy = meta.side.equals("BUY", ignoreCase = true)
    val accent = if (isBuy) JarvisTheme.Green else JarvisTheme.Red
    AlertCardShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    meta.side.uppercase(),
                    color = Color.Black, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                val (baseSym, tf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(meta.symbol)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(baseSym, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            tf.uppercase(),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
                Text("กลยุทธ์: ${meta.strategy}", color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        CardFieldRow("Entry", meta.entry, accent)
        CardFieldRow("TP", meta.tp, JarvisTheme.Green)
        CardFieldRow("SL", meta.sl, JarvisTheme.Red)
        CardFieldRow("RR", "1:${meta.rr}", accent)
        meta.atr?.let { CardFieldRow("ATR14", it, accent) }
        Spacer(Modifier.height(8.dp))
        Text("เหตุผล", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(meta.reason, color = Color.White.copy(0.9f), fontSize = 12.sp, lineHeight = 17.sp)
        meta.summary?.let { CardSummaryBlock(accent, it) }
        meta.voice?.let { CardVoiceFooter(accent, it) }
    }
}

/** การ์ดคาดการณ์สัญญาณล่วงหน้า (Pre-Signal) — สไตล์กะทัดรัด แสดงเฉพาะปัจจัยที่เกิด ไม่รกพื้นที่ */
@Composable
private fun AnticipationAlertCard3D(meta: AlertCardMeta.Anticipation) {
    val isBuy = meta.side.equals("BUY", ignoreCase = true)
    val accent = if (isBuy) JarvisTheme.Green else JarvisTheme.Red
    AlertCardShell(accent) {
        // บรรทัดที่ 1: Badge ⚡ + Symbol + Timeframe (ไม่ให้มี % เบียดด้านข้าง)
        val (baseSym, tf) = com.example.personalaibot.automation.IndicatorAlertProvider.splitSymbolAndTf(meta.symbol)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "⚡ คาดการณ์ ${meta.side.uppercase()}",
                    color = Color.Black, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 0.5.sp,
                    softWrap = false
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(baseSym, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    tf.uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    softWrap = false
                )
            }
        }

        // บรรทัดที่ 2: ความเชื่อมั่น % และ ราคา (อ่านง่าย ชัดเจน ไม่ซ้ำซ้อนกับปัจจัยที่เกิดด้านล่าง)
        val confVal = meta.confidence.trim().removeSuffix("%")
        val confDisplay = if (confVal.isNotBlank() && confVal != "-") "$confVal%" else "-"
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ความเชื่อมั่น",
                    color = Color.White.copy(0.65f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    confDisplay,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false
                )
            }
            if (meta.price != "-" && meta.price.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ราคา",
                        color = Color.White.copy(0.65f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        meta.price,
                        color = JarvisTheme.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // แสดงเฉพาะปัจจัยที่เกิด (Triggered Factors Only)
        val factorItems = parseAnticipationFactors(meta.desc)
        Text("ปัจจัยที่เกิด", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (factorItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                factorItems.forEach { factor ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(factor, color = Color.White.copy(0.9f), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        } else {
            Text(meta.desc, color = Color.White.copy(0.9f), fontSize = 11.sp, lineHeight = 15.sp)
        }

        meta.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            CardSummaryBlock(accent, it)
        }
        meta.voice?.let { CardVoiceFooter(accent, it) }
    }
}

private fun parseAnticipationFactors(desc: String): List<String> {
    if (desc.isBlank()) return emptyList()
    val clean = desc.replace(Regex("^⚡?\\s*\\[Confluence\\s*\\d+\\s*ปัจจัย\\]:?\\s*"), "")
    return clean.split(Regex("[;\\n]+"))
        .map { it.trim().removePrefix("-").removePrefix("•").trim() }
        .filter { it.isNotBlank() }
}

/** การ์ดแจ้งเตือนราคาแตะโซนสำคัญ (Keyzone Hit) — สไตล์ 3D Amber */
@Composable
private fun KeyzoneAlertCard3D(meta: AlertCardMeta.Keyzone) {
    val accent = JarvisTheme.Amber
    AlertCardShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(accent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "KEYZONE HIT",
                        color = Color.Black, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(meta.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("แตะจุดสำคัญของโครงสร้างตลาด", color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (meta.price != "-") {
            CardFieldRow("ราคาปัจจุบัน", meta.price, JarvisTheme.Cyan)
        }
        Spacer(Modifier.height(6.dp))
        Text("จุดสำคัญที่แตะ", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(meta.desc, color = Color.White.copy(0.9f), fontSize = 12.sp, lineHeight = 17.sp)
        meta.mtf?.takeIf { it.isNotBlank() }?.let { mtfText ->
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text("โครงสร้างตลาด 5 ไทม์เฟรม", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(mtfText, color = Color.White.copy(0.8f), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        meta.summary?.let { CardSummaryBlock(accent, it) }
        meta.voice?.let { CardVoiceFooter(accent, it) }
    }
}

/** การ์ด alert ทั่วไป (ราคา/indicator/ฯลฯ) — accent cyan, field เฉพาะ เงื่อนไข + ค่าปัจจุบัน + รายละเอียด */
@Composable
private fun GenericAlertCard(meta: AlertCardMeta.Generic) {
    val accent = JarvisTheme.Cyan
    AlertCardShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(accent.copy(0.2f), CircleShape)
                    .size(34.dp),
                contentAlignment = Alignment.Center
            ) { Text("🎯", fontSize = 16.sp) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(meta.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(meta.symbol, color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        CardFieldRow("เงื่อนไข", meta.condition, accent)
        CardFieldRow("ค่าปัจจุบัน", meta.current, accent)
        if (!meta.summary.isNullOrBlank()) {
            CardSummaryBlock(accent, meta.summary)
        } else if (!meta.rawContent.isNullOrBlank()) {
            val trimmed = meta.rawContent.trim()
            val hasDetails = !trimmed.startsWith("🎯") || trimmed.contains("\n\n") || trimmed.length > 80
            if (hasDetails) {
                val detailText = trimmed.replace(Regex("^🎯\\s*\\*\\*[^\n]+\\*\\*\\s*"), "")
                    .replace(Regex("^.*\\|.*\\|.*(\r?\n)+", RegexOption.MULTILINE), "")
                    .replace("**JARVIS quick-check:**", "")
                    .trim()
                if (detailText.isNotBlank()) {
                    CardSummaryBlock(accent, detailText)
                }
            }
        }
        meta.voice?.let { CardVoiceFooter(accent, it) }
    }
}
