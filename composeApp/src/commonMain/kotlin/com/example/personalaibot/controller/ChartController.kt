package com.example.personalaibot.controller

import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.trading.SmcApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase-2 refactor: แยก Chart/Dashboard state + logic ออกจาก JarvisViewModel
 * (เดิม VM บรรทัด ~339-383 state + ~1476-1770 functions)
 * JarvisViewModel คง public API เดิมทั้งหมดผ่าน forwarders — UI ไม่ต้องแก้
 */
class ChartController(
    private val scope: CoroutineScope,
    private val database: JarvisDatabase,
    private val smcApiService: SmcApiService
) {
    // ─── Charting System (V15.0) ─────────────────────────────────────────────────
    private val _showChart = MutableStateFlow(false)
    val showChart: StateFlow<Boolean> = _showChart.asStateFlow()

    private val _chartSymbol = MutableStateFlow("XAUUSD")
    val chartSymbol: StateFlow<String> = _chartSymbol.asStateFlow()

    private val _chartCandles = MutableStateFlow<List<com.example.personalaibot.tools.trading.Candle>>(emptyList())
    val chartCandles: StateFlow<List<com.example.personalaibot.tools.trading.Candle>> = _chartCandles.asStateFlow()

    private val _chartSmcResult = MutableStateFlow<com.example.personalaibot.tools.trading.SmcAnalysisResult?>(null)
    val chartSmcResult: StateFlow<com.example.personalaibot.tools.trading.SmcAnalysisResult?> = _chartSmcResult.asStateFlow()

    // ─── Signal markers (BUY/SELL arrows จากทุกกลยุทธ์) ─────────────────────
    private val signalMarkerProvider = com.example.personalaibot.automation.SignalMarkerProvider(smcApiService)
    private val _chartSignalMarkers = MutableStateFlow<List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>>(emptyList())
    val chartSignalMarkers: StateFlow<List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>> = _chartSignalMarkers.asStateFlow()

    private val _chartInterval = MutableStateFlow("1h")
    val chartInterval: StateFlow<String> = _chartInterval.asStateFlow()

    private val _chartLocale = MutableStateFlow("th_TH")
    val chartLocale: StateFlow<String> = _chartLocale.asStateFlow()

    private val _chartHideSideToolbar = MutableStateFlow(false)
    val chartHideSideToolbar: StateFlow<Boolean> = _chartHideSideToolbar.asStateFlow()

    private val _chartRefreshToken = MutableStateFlow(0L)
    val chartRefreshToken: StateFlow<Long> = _chartRefreshToken.asStateFlow()

    // ─── Chart Dashboard (multi-pane Lightweight Charts, AI-controllable) ──────
    /** "dashboard" = LWC multi-pane engine (offline) | "tradingview" = TV widget (online) */
    private val _chartViewMode = MutableStateFlow("dashboard")
    val chartViewMode: StateFlow<String> = _chartViewMode.asStateFlow()

    /** single | rsi | macd | rsi_macd | volume | full */
    private val _chartLayout = MutableStateFlow("rsi_macd")
    val chartLayout: StateFlow<String> = _chartLayout.asStateFlow()

    /** overlay indicators บน main pane: ema20, ema50, ema200, bb */
    private val _chartOverlays = MutableStateFlow(emptySet<String>())
    val chartOverlays: StateFlow<Set<String>> = _chartOverlays.asStateFlow()

    private val _chartDataLoading = MutableStateFlow(false)
    val chartDataLoading: StateFlow<Boolean> = _chartDataLoading.asStateFlow()

    // ─── Chart card cache (mini-chart ในแชท) ────────────────────────────────
    // แยกข้อมูลตาม symbol+interval ของการ์ดแต่ละใบ — การ์ดเก่าในประวัติแชทจะไม่เปลี่ยนตามการ์ดใหม่
    // key = "SYMBOL/interval" (normalized), value = Triple(candles, smcResult?, signalMarkers)
    private val _chartCardCache = MutableStateFlow<Map<String, Triple<List<com.example.personalaibot.tools.trading.Candle>, com.example.personalaibot.tools.trading.SmcAnalysisResult?, List<com.example.personalaibot.automation.SignalMarkerProvider.SignalMarker>>>>(emptyMap())
    val chartCardCache = _chartCardCache.asStateFlow()
    private val chartCardLoading = mutableSetOf<String>()

    init {
        // ─── Charting Sync (V15.0) — sync จาก ChartStateManager (global) ───
        scope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentSymbol.collect {
                _chartSymbol.value = it
            }
        }
        scope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentCandles.collect {
                _chartCandles.value = it
            }
        }
        scope.launch {
            com.example.personalaibot.tools.trading.ChartStateManager.currentSmcResult.collect {
                _chartSmcResult.value = it
            }
        }
    }

    /** โหลด chart dashboard settings (view mode / layout / overlays) — เรียกจาก VM.loadSettings */
    suspend fun loadPersistedSettings() {
        _chartViewMode.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("chart_view_mode").executeAsOneOrNull()
        }?.takeIf { it == "dashboard" || it == "tradingview" } ?: "dashboard"
        _chartLayout.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("chart_layout").executeAsOneOrNull()
        }?.takeIf { it in setOf("single", "rsi", "macd", "rsi_macd", "volume", "full") } ?: "rsi_macd"
        _chartOverlays.value = withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.getSetting("chart_overlays").executeAsOneOrNull()
        }?.split(",")?.map { it.trim() }?.filter { it in setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals") }?.toSet()
            ?: setOf("ema50")
    }

    fun openChart(symbol: String, candles: List<com.example.personalaibot.tools.trading.Candle>, smc: com.example.personalaibot.tools.trading.SmcAnalysisResult? = null) {
        _chartSymbol.value = symbol
        _chartCandles.value = candles
        _chartSmcResult.value = smc
        _showChart.value = true
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun openChart() {
        _showChart.value = true
        _chartRefreshToken.value = _chartRefreshToken.value + 1
        // dashboard mode ต้องมีข้อมูลแท่งเทียน — ดึงใหม่ถ้ายังไม่มี
        if (_chartCandles.value.isEmpty()) refreshChartCandles()
    }

    fun updateChartSymbol(symbol: String) {
        val normalized = normalizeChartSymbol(symbol)
        if (normalized.isBlank()) return
        _chartSymbol.value = normalized
        _chartRefreshToken.value = _chartRefreshToken.value + 1
        refreshChartCandles(force = true)
    }

    fun updateChartInterval(interval: String) {
        val normalized = normalizeChartInterval(interval)
        if (_chartInterval.value == normalized) return
        _chartInterval.value = normalized
        _chartRefreshToken.value = _chartRefreshToken.value + 1
        refreshChartCandles(force = true)
    }

    fun refreshChart() {
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun updateChartLocale(locale: String) {
        _chartLocale.value = if (locale.lowercase().startsWith("th")) "th_TH" else "en"
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    fun setChartHideSideToolbar(hidden: Boolean) {
        _chartHideSideToolbar.value = hidden
        _chartRefreshToken.value = _chartRefreshToken.value + 1
    }

    // ─── Chart Dashboard controls ────────────────────────────────────────────

    fun setChartViewMode(mode: String) {
        val normalized = if (mode == "tradingview") "tradingview" else "dashboard"
        if (_chartViewMode.value == normalized) return
        _chartViewMode.value = normalized
        _chartRefreshToken.value = _chartRefreshToken.value + 1
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("chart_view_mode", normalized)
        }
    }

    fun setChartLayout(layout: String) {
        val valid = setOf("single", "rsi", "macd", "rsi_macd", "volume", "full")
        if (layout !in valid || _chartLayout.value == layout) return
        _chartLayout.value = layout
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("chart_layout", layout)
        }
    }

    fun toggleChartOverlay(name: String) {
        val valid = setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")
        if (name !in valid) return
        val current = _chartOverlays.value
        _chartOverlays.value = if (name in current) current - name else current + name
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("chart_overlays", _chartOverlays.value.joinToString(","))
        }
        // เปิด SMC แล้วยังไม่มีผลวิเคราะห์ → ดึงเลย
        if (name == "smc" && "smc" in _chartOverlays.value && _chartSmcResult.value == null) {
            refreshChartCandles(force = true)
        }
        // เปิด signals แล้วยังไม่มี markers → คำนวณเลย
        if (name == "signals" && "signals" in _chartOverlays.value && _chartSignalMarkers.value.isEmpty()) {
            refreshChartCandles(force = true)
        }
    }

    /**
     * ดึงแท่งเทียนสำหรับ dashboard (symbol/interval ปัจจุบัน) — incremental cache ใน SmcApiService
     * เรียกตอนเปิดกราฟแบบไม่มีข้อมูล หรือเปลี่ยน symbol/interval
     */
    fun refreshChartCandles(force: Boolean = false) {
        if (_chartDataLoading.value) return
        val symbol = _chartSymbol.value
        val interval = _chartInterval.value
        scope.launch(Dispatchers.IO) {
            _chartDataLoading.value = true
            try {
                val result = smcApiService.fetchCandlesWithSource(symbol, interval, 300)
                _chartCandles.value = result.candles
                logDebug("ChartCtl", "Chart dashboard candles: ${result.candles.size} bars ($symbol $interval) source=${result.source}")
                // วาด SMC zones เฉพาะเมื่อผู้ใช้เปิด overlay "smc" เท่านั้น (ไม่ดึงทุกครั้ง ประหยัดทรัพยากร)
                if ("smc" in _chartOverlays.value) {
                    runCatching {
                        val smc = smcApiService.getSmcAnalysis(symbol, interval, strictTvSource = false)
                        _chartSmcResult.value = smc
                    }
                } else {
                    _chartSmcResult.value = null
                }
                // Signal markers เฉพาะเมื่อเปิด overlay "signals" (คำนวณในเครื่องจากแท่งเทียน)
                if ("signals" in _chartOverlays.value) {
                    runCatching {
                        _chartSignalMarkers.value = signalMarkerProvider.fetch("$symbol@$interval")
                    }.onFailure { logDebug("ChartCtl", "Signal markers failed: ${it.message}") }
                } else {
                    _chartSignalMarkers.value = emptyList()
                }
            } catch (e: Exception) {
                logDebug("ChartCtl", "Chart candles fetch failed: ${e.message}")
            } finally {
                _chartDataLoading.value = false
            }
        }
    }

    private fun chartCardKey(symbol: String, interval: String): String =
        "${normalizeChartSymbol(symbol)}/${normalizeChartInterval(interval)}"

    /**
     * โหลดข้อมูลให้ mini-chart ของการ์ดใบนั้นโดยเฉพาะ — ไม่แตะ state กราฟหลัก (dashboard)
     * กันการ์ดเก่าในแชทเปลี่ยนข้อมูล/indicator ตามคำสั่งเปิดกราฟใหม่
     */
    fun ensureChartCardData(symbol: String, interval: String, needsSmc: Boolean, needsMarkers: Boolean = false) {
        val key = chartCardKey(symbol, interval)
        val cached = _chartCardCache.value[key]
        if (cached != null && cached.first.isNotEmpty()
            && (!needsSmc || cached.second != null)
            && (!needsMarkers || cached.third.isNotEmpty())
        ) return
        if (key in chartCardLoading) return
        scope.launch(Dispatchers.IO) {
            chartCardLoading.add(key)
            try {
                val sym = normalizeChartSymbol(symbol)
                val tf = normalizeChartInterval(interval)
                val result = smcApiService.fetchCandlesWithSource(sym, tf, 300)
                val smc = if (needsSmc) {
                    runCatching { smcApiService.getSmcAnalysis(sym, tf, strictTvSource = false) }.getOrNull()
                } else null
                val markers = if (needsMarkers) {
                    runCatching { signalMarkerProvider.fetch("$sym@$tf") }.getOrElse { emptyList() }
                } else emptyList()
                _chartCardCache.value = _chartCardCache.value + (key to Triple(result.candles, smc, markers))
                logDebug("ChartCtl", "Chart card cache: ${result.candles.size} bars ($key) source=${result.source} smc=${smc != null} markers=${markers.size}")
            } catch (e: Exception) {
                logDebug("ChartCtl", "Chart card fetch failed ($key): ${e.message}")
            } finally {
                chartCardLoading.remove(key)
            }
        }
    }

    /**
     * เปิดกราฟจาก chart card ในแชท (Rich Chat Rendering) — ใช้ config ที่ AI ฝังมา
     */
    fun openChartWithConfig(symbol: String, interval: String, layout: String, overlays: Set<String>) {
        updateChartSymbol(symbol)
        updateChartInterval(interval)
        setChartLayout(layout)
        val validOverlays = overlays.filter { it in setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals") }.toSet()
        _chartOverlays.value = validOverlays
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("chart_overlays", validOverlays.joinToString(","))
        }
        setChartViewMode("dashboard")
        _showChart.value = true
        _chartRefreshToken.value = _chartRefreshToken.value + 1
        refreshChartCandles(force = true)
    }

    fun closeChart() {
        _showChart.value = false
    }

    /**
     * รับคำสั่งจาก tool chart_dashboard_control — ปรับหน้ากราฟตามที่ AI สั่ง
     * @return สรุปผลภาษาไทย (ส่งกลับเข้า tool loop ให้ AI ตอบผู้ใช้)
     */
    fun applyChartControl(args: Map<String, String>): String {
        val action = args["action"]?.trim()?.lowercase() ?: return "⚠️ ไม่ระบุ action"
        logDebug("ChartCtl", "Chart control: action=$action args=$args")
        return when (action) {
            "open" -> {
                args["symbol"]?.takeIf { it.isNotBlank() }?.let { updateChartSymbol(it) }
                args["interval"]?.takeIf { it.isNotBlank() }?.let { updateChartInterval(it) }
                // รีเซ็ต dashboard ทั้งจอทุกครั้งที่ "เปิดกราฟ" — กัน indicator เก่าค้างเต็มจอ
                // ไม่ระบุ layout = กลับไป single (กราฟเปล่า), ไม่ระบุ overlays = ปิดทั้งหมด
                setChartLayout(args["layout"]?.takeIf { it.isNotBlank() } ?: "single")
                val validOverlayNames = setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")
                val wantOverlays = (args["overlays"] ?: args["overlay"])
                    ?.split(",")?.map { it.trim().lowercase() }
                    ?.filter { it in validOverlayNames }?.toSet()
                    ?: emptySet()
                if (wantOverlays != _chartOverlays.value) {
                    _chartOverlays.value = wantOverlays
                    scope.launch(Dispatchers.IO) {
                        database.jarvisDatabaseQueries.insertSetting("chart_overlays", wantOverlays.joinToString(","))
                    }
                }
                // ไม่สลับหน้าจออัตโนมัติ — กราฟแสดงเป็นการ์ด mini-chart ในแชท ผู้ใช้แตะการ์ดเองถ้าต้องการเต็มจอ
                _chartRefreshToken.value = _chartRefreshToken.value + 1
                if ("smc" in wantOverlays) refreshChartCandles(force = true)
                else if (_chartCandles.value.isEmpty()) refreshChartCandles()
                "✅ เตรียมกราฟ ${_chartSymbol.value} (${_chartInterval.value}) แล้ว — layout=${_chartLayout.value}" +
                    (if (wantOverlays.isNotEmpty()) ", overlays=${wantOverlays.joinToString(",")}" else "") +
                    " (แสดงเป็นการ์ดกราฟในแชท — ผู้ใช้แตะการ์ดเพื่อเปิดเต็มจอ ไม่ต้องสลับหน้าจอให้)"
            }
            "close" -> {
                _showChart.value = false
                "✅ ปิดหน้ากราฟแล้ว"
            }
            "set_layout" -> {
                val layout = args["layout"] ?: return "⚠️ ต้องระบุ layout (single/rsi/macd/rsi_macd/volume/full)"
                if (layout !in setOf("single", "rsi", "macd", "rsi_macd", "volume", "full")) {
                    return "⚠️ layout '$layout' ไม่ถูกต้อง — เลือกจาก single, rsi, macd, rsi_macd, volume, full"
                }
                setChartLayout(layout)
                "✅ เปลี่ยน layout กราฟเป็น $layout แล้ว"
            }
            "set_symbol" -> {
                val symbol = args["symbol"] ?: return "⚠️ ต้องระบุ symbol"
                updateChartSymbol(symbol)
                "✅ เปลี่ยนกราฟเป็น ${_chartSymbol.value} แล้ว"
            }
            "set_interval" -> {
                val interval = args["interval"] ?: return "⚠️ ต้องระบุ interval (1m/5m/15m/30m/1h/4h/1d)"
                updateChartInterval(interval)
                "✅ เปลี่ยน timeframe เป็น ${_chartInterval.value} แล้ว"
            }
            "set_overlay" -> {
                val name = args["overlay"] ?: return "⚠️ ต้องระบุ overlay (ema14/ema20/ema50/ema60/ema200/bb/smc)"
                if (name !in setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")) {
                    return "⚠️ overlay '$name' ไม่ถูกต้อง — เลือกจาก ema14, ema20, ema50, ema60, ema200, bb, smc"
                }
                val visible = args["visible"]?.lowercase() != "false"
                val current = _chartOverlays.value
                val want = if (visible) current + name else current - name
                if (want != current) {
                    _chartOverlays.value = want
                    scope.launch(Dispatchers.IO) {
                        database.jarvisDatabaseQueries.insertSetting("chart_overlays", want.joinToString(","))
                    }
                }
                if (name == "smc" && visible && _chartSmcResult.value == null) refreshChartCandles(force = true)
                if (name == "signals" && visible && _chartSignalMarkers.value.isEmpty()) refreshChartCandles(force = true)
                "✅ ${if (visible) "เปิด" else "ปิด"}อินดิเคเตอร์ $name บนกราฟแล้ว (ที่เปิดอยู่: ${want.joinToString(", ").ifBlank { "ไม่มี" }})"
            }
            "set_view" -> {
                val view = args["view"] ?: return "⚠️ ต้องระบุ view (dashboard/tradingview)"
                setChartViewMode(view)
                if (view == "dashboard" && _chartCandles.value.isEmpty()) refreshChartCandles()
                "✅ สลับโหมดกราฟเป็น ${if (view == "dashboard") "Dashboard (multi-pane)" else "TradingView"} แล้ว"
            }
            else -> "⚠️ ไม่รู้จัก action '$action' — ใช้ open/close/set_layout/set_symbol/set_interval/set_overlay/set_view"
        }
    }

    private fun normalizeChartInterval(interval: String): String {
        val normalized = interval.trim().lowercase()
        return when (normalized) {
            "1m", "5m", "15m", "30m", "1h", "4h", "1d" -> normalized
            "d" -> "1d"
            else -> "1h"
        }
    }

    private fun normalizeChartSymbol(symbol: String): String {
        return symbol
            .trim()
            .uppercase()
            .replace(" ", "")
    }
}
