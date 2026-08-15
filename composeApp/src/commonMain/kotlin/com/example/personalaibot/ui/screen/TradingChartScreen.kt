package com.example.personalaibot.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcAnalysisResult
import com.example.personalaibot.ui.theme.JarvisTheme
import com.example.personalaibot.ui.components.buildSmcZonesJson
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * TradingChartScreen V2 — AI-controllable chart dashboard
 *
 * 2 โหมด:
 * - "dashboard"   → Lightweight Charts multi-pane (offline, เร็ว, วาด indicator/SMC ได้, AI ปรับ layout ได้)
 * - "tradingview" → TradingView Advanced Chart widget เดิม (online, full tools)
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TradingChartScreen(
    symbol: String,
    interval: String,
    locale: String,
    hideSideToolbar: Boolean,
    refreshToken: Long,
    openSettingsSignal: Long,
    viewMode: String,
    layout: String,
    overlays: Set<String>,
    candles: List<Candle>,
    smcResult: SmcAnalysisResult?,
    signalMarkers: List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker> = emptyList(),
    dataLoading: Boolean,
    onSetViewMode: (String) -> Unit,
    onSetLayout: (String) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onSetLocale: (String) -> Unit,
    onSetHideSideToolbar: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(openSettingsSignal) {
        if (openSettingsSignal > 0L) showSettings = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Control bar (mobile-first: scroll แนวนอนได้) ─────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardChip("📊 Dashboard", viewMode == "dashboard") { onSetViewMode("dashboard") }
            DashboardChip("🌐 TradingView", viewMode == "tradingview") { onSetViewMode("tradingview") }

            if (viewMode == "dashboard") {
                Text("│", color = Color.White.copy(alpha = 0.3f))
                DashboardChip("Single", layout == "single") { onSetLayout("single") }
                DashboardChip("RSI", layout == "rsi") { onSetLayout("rsi") }
                DashboardChip("MACD", layout == "macd") { onSetLayout("macd") }
                DashboardChip("RSI+MACD", layout == "rsi_macd") { onSetLayout("rsi_macd") }
                DashboardChip("Vol", layout == "volume") { onSetLayout("volume") }
                DashboardChip("Full", layout == "full") { onSetLayout("full") }
                Text("│", color = Color.White.copy(alpha = 0.3f))
                DashboardChip("EMA14", "ema14" in overlays) { onToggleOverlay("ema14") }
                DashboardChip("EMA20", "ema20" in overlays) { onToggleOverlay("ema20") }
                DashboardChip("EMA50", "ema50" in overlays) { onToggleOverlay("ema50") }
                DashboardChip("EMA60", "ema60" in overlays) { onToggleOverlay("ema60") }
                DashboardChip("EMA200", "ema200" in overlays) { onToggleOverlay("ema200") }
                DashboardChip("BB", "bb" in overlays) { onToggleOverlay("bb") }
                DashboardChip("DC20", "donchian" in overlays) { onToggleOverlay("donchian") }
                DashboardChip("SMC", "smc" in overlays) { onToggleOverlay("smc") }
                DashboardChip("SIG", "signals" in overlays) { onToggleOverlay("signals") }
            }
        }

        // ─── Content ──────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (viewMode == "dashboard") {
                DashboardWebView(
                    symbol = symbol,
                    interval = interval,
                    layout = layout,
                    overlays = overlays,
                    candles = candles,
                    smcResult = smcResult,
                    signalMarkers = signalMarkers,
                    refreshToken = refreshToken
                )
                if (dataLoading && candles.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = JarvisTheme.Cyan
                    )
                }
            } else {
                TradingViewWebView(
                    symbol = symbol,
                    interval = interval,
                    locale = locale,
                    hideSideToolbar = hideSideToolbar,
                    refreshToken = refreshToken
                )
            }
        }
    }

    if (showSettings) {
        WidgetSettingsDialog(
            locale = locale,
            hideSideToolbar = hideSideToolbar,
            onSetLocale = onSetLocale,
            onSetHideSideToolbar = onSetHideSideToolbar,
            onClose = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JarvisTheme.Cyan.copy(alpha = 0.25f),
            selectedLabelColor = JarvisTheme.Cyan
        )
    )
}

/** Lightweight Charts multi-pane dashboard (offline engine ในตัวแอป) */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun DashboardWebView(
    symbol: String,
    interval: String,
    layout: String,
    overlays: Set<String>,
    candles: List<Candle>,
    smcResult: SmcAnalysisResult?,
    signalMarkers: List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>,
    refreshToken: Long
) {
    val state = remember { WebViewState(WebContent.Url("file:///android_asset/chart_dashboard/index.html")) }
    val navigator = rememberWebViewNavigator()

    state.webSettings.isJavaScriptEnabled = true
    state.webSettings.androidWebSettings.safeBrowsingEnabled = true
    state.webSettings.androidWebSettings.domStorageEnabled = true

    // สำคัญ: evaluateJavaScript ก่อนหน้าเว็บโหลดเสร็จจะถูกทิ้งเงียบๆ (เคสจริง: candles ไม่ขึ้น เหลือแต่ FVG
    // เพราะ smcResult มาช้ากว่าเลยยิงทีหลังตอน page พร้อมแล้ว) — ต้องรอ LoadingState.Finished ก่อนเสมอ
    val pageReady = state.loadingState is com.multiplatform.webview.web.LoadingState.Finished

    // init / re-init เมื่อ config เปลี่ยน (JS เก็บ candles ไว้ใน state เอง ปลอดภัย)
    LaunchedEffect(pageReady, symbol, interval, layout, overlays) {
        if (!pageReady) return@LaunchedEffect
        val overlaysJson = buildJsonObject {
            put("ema14", "ema14" in overlays)
            put("ema20", "ema20" in overlays)
            put("ema50", "ema50" in overlays)
            put("ema60", "ema60" in overlays)
            put("ema200", "ema200" in overlays)
            put("bb", "bb" in overlays)
            put("donchian", "donchian" in overlays)
            put("signals", "signals" in overlays)
        }
        val cfg = buildJsonObject {
            put("symbol", symbol)
            put("interval", interval)
            put("layout", layout)
            put("overlays", overlaysJson)
        }
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.init('${cfg.toString().replace("'", "\\'")}');"
        )
    }

    // ส่งแท่งเทียน (incremental ฝั่ง engine จัดการเอง)
    LaunchedEffect(pageReady, candles, refreshToken) {
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

    // วาด SMC zones เฉพาะเมื่อเปิด overlay "smc" (ไม่แสดงอัตโนมัติ; ปิดแล้วล้างทิ้ง)
    LaunchedEffect(pageReady, smcResult, overlays) {
        if (!pageReady) return@LaunchedEffect
        if ("smc" !in overlays) {
            navigator.evaluateJavaScript("window.jarvisDashboard && window.jarvisDashboard.drawSMC('[]');")
            return@LaunchedEffect
        }
        val smc = smcResult ?: return@LaunchedEffect
        if (candles.isEmpty()) return@LaunchedEffect
        val zonesJson = buildSmcZonesJson(smc, candles)
        navigator.evaluateJavaScript(
            "window.jarvisDashboard && window.jarvisDashboard.drawSMC('${zonesJson.replace("'", "\\'")}');"
        )
    }

    // วาด signal markers (BUY/SELL arrows ทุกกลยุทธ์) เฉพาะเมื่อเปิด overlay "signals"
    LaunchedEffect(pageReady, signalMarkers, overlays, candles) {
        if (!pageReady) return@LaunchedEffect
        if ("signals" !in overlays || signalMarkers.isEmpty() || candles.isEmpty()) {
            navigator.evaluateJavaScript("window.jarvisDashboard && window.jarvisDashboard.drawMarkers('[]');")
            return@LaunchedEffect
        }
        val json = buildJsonArray {
            signalMarkers.forEach { m ->
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

    WebView(state = state, navigator = navigator, modifier = Modifier.fillMaxSize())
}

/** TradingView Advanced Chart widget เดิม (online) */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun TradingViewWebView(
    symbol: String,
    interval: String,
    locale: String,
    hideSideToolbar: Boolean,
    refreshToken: Long
) {
    val state = remember { WebViewState(WebContent.Url("file:///android_asset/chart_widget/index.html")) }
    val navigator = rememberWebViewNavigator()

    LaunchedEffect(symbol, interval, locale, hideSideToolbar, refreshToken) {
        navigator.evaluateJavaScript(
            """
            if (window.jarvisWidgetBridge && window.jarvisWidgetBridge.setAll) {
                window.jarvisWidgetBridge.setAll("$symbol", "$interval", "$locale", ${hideSideToolbar});
            }
            """.trimIndent()
        )
    }

    state.webSettings.isJavaScriptEnabled = true
    state.webSettings.androidWebSettings.safeBrowsingEnabled = true
    state.webSettings.androidWebSettings.domStorageEnabled = true
    state.webSettings.androidWebSettings.isAlgorithmicDarkeningAllowed = true

    WebView(state = state, navigator = navigator, modifier = Modifier.fillMaxSize())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsDialog(
    locale: String,
    hideSideToolbar: Boolean,
    onSetLocale: (String) -> Unit,
    onSetHideSideToolbar: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Widget Settings", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Locale",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = locale.lowercase().startsWith("th"),
                        onClick = { onSetLocale("th_TH") },
                        label = { Text("Thai") }
                    )
                    FilterChip(
                        selected = !locale.lowercase().startsWith("th"),
                        onClick = { onSetLocale("en") },
                        label = { Text("English") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hide Side Toolbar (TradingView)",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = hideSideToolbar,
                        onCheckedChange = onSetHideSideToolbar
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        containerColor = JarvisTheme.Dark
    )
}
