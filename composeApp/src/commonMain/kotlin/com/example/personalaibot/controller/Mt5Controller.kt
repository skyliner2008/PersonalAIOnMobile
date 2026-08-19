package com.example.personalaibot.controller

import com.example.personalaibot.db.JarvisDatabase
import com.example.personalaibot.logDebug
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.trading.AiTrackingInsight
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import com.example.personalaibot.tools.trading.Mt5SymbolInfo
import com.example.personalaibot.tools.trading.Mt5TerminalService
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.tools.trading.SmcApiService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

data class Mt5ClientRuntimeInfo(
    val id: String,
    val name: String,
    val exePath: String,
    val running: Boolean,
    val pids: List<Long>
)

/**
 * Mt5Controller — แยกจาก JarvisViewModel (Refactor Phase 1, 2026-08-19)
 * ครอบคลุม: MT5 bridge connection/pairing, realtime websocket channel, snapshot sync,
 * order/position management, local cache hydration, AI Tracking loop
 *
 * โค้ดย้ายมาแบบ verbatim (viewModelScope → scope) — behavior ไม่เปลี่ยน
 */
class Mt5Controller(
    private val scope: CoroutineScope,
    private val client: HttpClient,
    private val database: JarvisDatabase,
    private val mt5TerminalService: Mt5TerminalService,
    private val smcApiService: SmcApiService,
    private val defaultBridgeBaseUrl: String
) {
    private val plainJson = Json { ignoreUnknownKeys = true }

    // ─── MT5 Trading Terminal ─────────────────────────────────────────────
    private val _showTradingTerminal = MutableStateFlow(false)
    val showTradingTerminal: StateFlow<Boolean> = _showTradingTerminal.asStateFlow()

    private val _mt5BridgeBaseUrl = MutableStateFlow(defaultBridgeBaseUrl)
    val mt5BridgeBaseUrl: StateFlow<String> = _mt5BridgeBaseUrl.asStateFlow()

    private val _mt5AuthToken = MutableStateFlow("")
    val mt5AuthToken: StateFlow<String> = _mt5AuthToken.asStateFlow()

    private val _mt5PairingStatus = MutableStateFlow("NOT_CONNECTED")
    val mt5PairingStatus: StateFlow<String> = _mt5PairingStatus.asStateFlow()

    private val _mt5IsSyncing = MutableStateFlow(false)
    val mt5IsSyncing: StateFlow<Boolean> = _mt5IsSyncing.asStateFlow()

    private val _mt5LastSyncAt = MutableStateFlow(0L)
    val mt5LastSyncAt: StateFlow<Long> = _mt5LastSyncAt.asStateFlow()

    // 2026-04-30 (P6) — server reachability flag.  False after the most recent
    // fetch attempt failed, true after a successful round-trip.  When false
    // the cached MT5 view is still rendered (no blank screen) and an offline
    // banner is shown above the screens via TradingTerminalScreen.
    private val _mt5ServerOnline = MutableStateFlow(true)
    val mt5ServerOnline: StateFlow<Boolean> = _mt5ServerOnline.asStateFlow()

    // 2026-04-30 (P6) — true while the very first cache load on boot is still
    // in flight.  Screens use this to show a small skeleton instead of an
    // empty state when the DB hasn't been read yet.
    private val _mt5CacheLoading = MutableStateFlow(true)
    val mt5CacheLoading: StateFlow<Boolean> = _mt5CacheLoading.asStateFlow()

    private val _mt5Error = MutableStateFlow<String?>(null)
    val mt5Error: StateFlow<String?> = _mt5Error.asStateFlow()

    private val _mt5ActionResult = MutableStateFlow<String?>(null)
    val mt5ActionResult: StateFlow<String?> = _mt5ActionResult.asStateFlow()

    private val _mt5Account = MutableStateFlow<Mt5AccountInfo?>(null)
    val mt5Account: StateFlow<Mt5AccountInfo?> = _mt5Account.asStateFlow()

    private val _mt5Symbols = MutableStateFlow<List<Mt5SymbolInfo>>(emptyList())
    val mt5Symbols: StateFlow<List<Mt5SymbolInfo>> = _mt5Symbols.asStateFlow()

    private val _mt5Positions = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Positions: StateFlow<List<Mt5TradeItem>> = _mt5Positions.asStateFlow()

    private val _mt5Orders = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Orders: StateFlow<List<Mt5TradeItem>> = _mt5Orders.asStateFlow()

    private val _mt5Deals = MutableStateFlow<List<Mt5TradeItem>>(emptyList())
    val mt5Deals: StateFlow<List<Mt5TradeItem>> = _mt5Deals.asStateFlow()

    private val _mt5Clients = MutableStateFlow<List<Mt5ClientRuntimeInfo>>(emptyList())
    val mt5Clients: StateFlow<List<Mt5ClientRuntimeInfo>> = _mt5Clients.asStateFlow()

    private val _mt5ClientsLoading = MutableStateFlow(false)
    val mt5ClientsLoading: StateFlow<Boolean> = _mt5ClientsLoading.asStateFlow()

    private val _mt5SelectedClientExe = MutableStateFlow("")
    val mt5SelectedClientExe: StateFlow<String> = _mt5SelectedClientExe.asStateFlow()

    private val _mt5TerminalFeed = MutableStateFlow<List<String>>(emptyList())
    val mt5TerminalFeed: StateFlow<List<String>> = _mt5TerminalFeed.asStateFlow()

    private val _mt5DefaultLot = MutableStateFlow("0.01")
    val mt5DefaultLot: StateFlow<String> = _mt5DefaultLot.asStateFlow()

    private val _mt5DefaultTpPoints = MutableStateFlow("300")
    val mt5DefaultTpPoints: StateFlow<String> = _mt5DefaultTpPoints.asStateFlow()

    private val _mt5DefaultSlPoints = MutableStateFlow("200")
    val mt5DefaultSlPoints: StateFlow<String> = _mt5DefaultSlPoints.asStateFlow()

    private val _mt5MaxDdPercent = MutableStateFlow("10")
    val mt5MaxDdPercent: StateFlow<String> = _mt5MaxDdPercent.asStateFlow()

    private val _aiTrackingActive = MutableStateFlow(false)
    val aiTrackingActive: StateFlow<Boolean> = _aiTrackingActive.asStateFlow()

    private val _aiTrackingIntervalSec = MutableStateFlow(8)
    val aiTrackingIntervalSec: StateFlow<Int> = _aiTrackingIntervalSec.asStateFlow()

    private val _aiTrackingWatchlist = MutableStateFlow("XAUUSD")
    val aiTrackingWatchlist: StateFlow<String> = _aiTrackingWatchlist.asStateFlow()

    private val _aiTrackingInsights = MutableStateFlow<List<AiTrackingInsight>>(emptyList())
    val aiTrackingInsights: StateFlow<List<AiTrackingInsight>> = _aiTrackingInsights.asStateFlow()

    private val _aiTrackingFeed = MutableStateFlow<List<String>>(emptyList())
    val aiTrackingFeed: StateFlow<List<String>> = _aiTrackingFeed.asStateFlow()

    private var aiTrackingJob: Job? = null
    private var mt5AutoSyncJob: Job? = null
    private var mt5RealtimeJob: Job? = null
    private var mt5SnapshotRevision: String = ""

    // ─── Settings loading (เรียกจาก JarvisViewModel.loadSettings) ────────────

    /**
     * อ่าน MT5 settings จาก DB แล้ว apply เข้า StateFlows
     * @return auth token ที่โหลดได้ (VM ใช้ตัดสินใจว่าจะ refresh pairing/realtime ไหม)
     */
    suspend fun loadPersistedSettings(): String = withContext(Dispatchers.IO) {
        val savedMt5BridgeBaseUrl =
            database.jarvisDatabaseQueries.getSetting("mt5_bridge_base_url").executeAsOneOrNull()
                ?: defaultBridgeBaseUrl
        val savedMt5AuthToken =
            database.jarvisDatabaseQueries.getSetting("mt5_auth_token").executeAsOneOrNull() ?: ""
        val savedAiTrackingIntervalSec =
            database.jarvisDatabaseQueries.getSetting("ai_tracking_interval_sec").executeAsOneOrNull()
                ?.toIntOrNull() ?: 8
        val savedAiTrackingWatchlist =
            database.jarvisDatabaseQueries.getSetting("ai_tracking_watchlist").executeAsOneOrNull()
                ?: "XAUUSD"
        val savedMt5DefaultLot =
            database.jarvisDatabaseQueries.getSetting("mt5_default_lot").executeAsOneOrNull() ?: "0.01"
        val savedMt5DefaultTpPoints =
            database.jarvisDatabaseQueries.getSetting("mt5_default_tp_points").executeAsOneOrNull() ?: "300"
        val savedMt5DefaultSlPoints =
            database.jarvisDatabaseQueries.getSetting("mt5_default_sl_points").executeAsOneOrNull() ?: "200"
        val savedMt5MaxDdPercent =
            database.jarvisDatabaseQueries.getSetting("mt5_max_dd_percent").executeAsOneOrNull() ?: "10"
        val savedMt5SelectedClientExe =
            database.jarvisDatabaseQueries.getSetting("mt5_selected_client_exe").executeAsOneOrNull() ?: ""

        _mt5BridgeBaseUrl.value = savedMt5BridgeBaseUrl
        _mt5AuthToken.value = savedMt5AuthToken
        _mt5PairingStatus.value = if (savedMt5AuthToken.isBlank()) "NOT_CONNECTED" else "TOKEN_READY"
        _aiTrackingIntervalSec.value = savedAiTrackingIntervalSec.coerceIn(3, 120)
        _aiTrackingWatchlist.value = savedAiTrackingWatchlist
        _mt5DefaultLot.value = savedMt5DefaultLot
        _mt5DefaultTpPoints.value = savedMt5DefaultTpPoints
        _mt5DefaultSlPoints.value = savedMt5DefaultSlPoints
        _mt5MaxDdPercent.value = savedMt5MaxDdPercent
        _mt5SelectedClientExe.value = savedMt5SelectedClientExe

        savedMt5AuthToken
    }

    /** บล็อก [MT5_RUNTIME_CONTEXT] สำหรับ core context ของ AI — VM เอาไปต่อท้าย memory core */
    fun runtimeContextSnippet(): String {
        val serverRoot = normalizeServerRoot(_mt5BridgeBaseUrl.value)
        val mt5ApiBase = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)
        val hasToken = _mt5AuthToken.value.isNotBlank()
        val paired = _mt5PairingStatus.value == "APPROVED" && hasToken
        return buildString {
            appendLine("")
            appendLine("[MT5_RUNTIME_CONTEXT]")
            appendLine("mt5_paired=$paired")
            appendLine("mt5_pairing_status=${_mt5PairingStatus.value}")
            appendLine("mt5_server_root=$serverRoot")
            appendLine("mt5_api_base=$mt5ApiBase")
            appendLine("mt5_has_auth_token=$hasToken")
            appendLine("mt5_last_sync_at=${_mt5LastSyncAt.value}")
            appendLine("mt5_positions_count=${_mt5Positions.value.size}")
            appendLine("mt5_orders_count=${_mt5Orders.value.size}")
            appendLine("mt5_symbols_cached=${_mt5Symbols.value.size}")
            appendLine("When user asks to place/close MT5 orders, use trading_mt5_order/trading_mt5_close_position directly without asking for endpoint or token.")
            appendLine("If mt5_paired=false, first tell user to connect/approve in MT5 dashboard.")
        }
    }

    /** ค่า config ปัจจุบันสำหรับ ToolExecutor (MT5 tools ใช้ตอน execute) */
    fun currentRuntimeConfig(): ToolExecutor.Mt5RuntimeConfig = ToolExecutor.Mt5RuntimeConfig(
        bridgeBaseUrl = _mt5BridgeBaseUrl.value,
        authToken = _mt5AuthToken.value,
        pairingStatus = _mt5PairingStatus.value
    )

    /** cleanup — เรียกจาก ViewModel.onCleared */
    fun shutdown() {
        stopAiTracking()
        stopMt5RealtimeChannel()
        mt5AutoSyncJob?.cancel()
        mt5AutoSyncJob = null
    }

    // ─── Terminal UI ──────────────────────────────────────────────────────

    fun openTradingTerminal() {
        _showTradingTerminal.value = true
        refreshMt5Clients()
        if (_mt5PairingStatus.value != "APPROVED" && _mt5AuthToken.value.isNotBlank()) {
            refreshMt5PairingStatus()
        }
        if (_mt5PairingStatus.value == "APPROVED") {
            startMt5RealtimeChannel()
            if (_mt5LastSyncAt.value == 0L) {
                refreshMt5Terminal()
            }
        } else if (_mt5AuthToken.value.isBlank() && _mt5LastSyncAt.value == 0L) {
            refreshMt5Terminal()
        }
    }

    fun closeTradingTerminal() {
        _showTradingTerminal.value = false
        stopMt5AutoSync()
    }

    fun clearMt5ActionResult() {
        _mt5ActionResult.value = null
    }

    fun clearMt5Error() {
        _mt5Error.value = null
    }

    fun updateMt5BridgeBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return
        _mt5BridgeBaseUrl.value = normalized
        mt5SnapshotRevision = ""
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_bridge_base_url", normalized)
        }
    }

    fun updateMt5AuthToken(token: String) {
        val normalized = token.trim()
        _mt5AuthToken.value = normalized
        _mt5PairingStatus.value = if (normalized.isBlank()) "NOT_CONNECTED" else "TOKEN_READY"
        mt5SnapshotRevision = ""
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_auth_token", normalized)
        }
    }

    fun connectMt5Terminal(serverUrl: String) {
        val normalized = serverUrl.trim().trimEnd('/')
        if (normalized.isBlank()) return
        scope.launch {
            _mt5Error.value = null
            _mt5ActionResult.value = null
            updateMt5BridgeBaseUrl(normalized)
            appendMt5TerminalLine("connect_request url=$normalized")

            val token = if (_mt5AuthToken.value.isNotBlank()) {
                _mt5AuthToken.value
            } else {
                val generated = generateMt5ClientToken()
                updateMt5AuthToken(generated)
                generated
            }

            _mt5PairingStatus.value = "PAIRING_REQUEST_SENT"
            val pairUrl = "${normalizeServerRoot(_mt5BridgeBaseUrl.value)}/api/auth/pair/request"
            try {
                val response = client.post(pairUrl) {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        buildJsonObject {
                            put("token", token)
                            put("deviceId", "android-${token.take(12)}")
                            put("deviceName", "PersonalAIBot Android")
                            put("appVersion", "composeApp")
                        }.toString()
                    )
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    _mt5PairingStatus.value = "PAIRING_FAILED"
                    _mt5Error.value = "Pair request failed (${response.status.value}): $body"
                    return@launch
                }

                val root = runCatching { plainJson.parseToJsonElement(body).jsonObject }.getOrNull()
                val approved = root?.get("approved")?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                val status = root?.get("status")?.jsonPrimitive?.content.orEmpty()

                if (approved || status.equals("APPROVED", ignoreCase = true)) {
                    _mt5PairingStatus.value = "APPROVED"
                    _mt5ActionResult.value = "MT5 connected and approved"
                    appendMt5TerminalLine("pairing=APPROVED")
                    startMt5RealtimeChannel()
                    refreshMt5Terminal()
                } else {
                    _mt5PairingStatus.value = "PENDING_APPROVAL"
                    _mt5ActionResult.value = "Pair request sent. Please approve this device in server dashboard."
                    appendMt5TerminalLine("pairing=PENDING_APPROVAL")
                }
            } catch (e: Exception) {
                _mt5PairingStatus.value = "PAIRING_FAILED"
                _mt5Error.value = "Connect failed: ${e.message}"
                appendMt5TerminalLine("connect_failed ${e.message}")
            }
        }
    }

    fun disconnectMt5Terminal() {
        stopMt5AutoSync()
        stopMt5RealtimeChannel()
        _mt5PairingStatus.value = "NOT_CONNECTED"
        mt5SnapshotRevision = ""
        _mt5ActionResult.value = "Disconnected"
        appendMt5TerminalLine("disconnected")
    }

    fun refreshMt5PairingStatus() {
        val token = _mt5AuthToken.value.trim()
        if (token.isBlank()) return
        scope.launch {
            runCatching {
                val url = "${normalizeServerRoot(_mt5BridgeBaseUrl.value)}/api/auth/pair/status?token=$token"
                val body = client.get(url).bodyAsText()
                val root = plainJson.parseToJsonElement(body).jsonObject
                val approved = root["approved"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                val status = root["status"]?.jsonPrimitive?.content.orEmpty()
                if (approved || status.equals("APPROVED", ignoreCase = true)) {
                    _mt5PairingStatus.value = "APPROVED"
                    startMt5RealtimeChannel()
                } else if (status.isNotBlank()) {
                    _mt5PairingStatus.value = status
                }
            }
        }
    }

    fun toggleMt5Connection(serverUrl: String) {
        if (_mt5PairingStatus.value == "APPROVED") {
            disconnectMt5Terminal()
        } else {
            connectMt5Terminal(serverUrl)
        }
    }

    fun selectMt5ClientExe(exePath: String) {
        _mt5SelectedClientExe.value = exePath
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_selected_client_exe", exePath)
        }
    }

    fun refreshMt5Clients() {
        if (_mt5ClientsLoading.value) return
        scope.launch {
            _mt5ClientsLoading.value = true
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients"
                val body = client.get(endpoint) {
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                }.bodyAsText()
                val root = plainJson.parseToJsonElement(body).jsonObject
                val rows = root["data"]?.jsonObject?.get("rows")?.jsonArray.orEmpty()
                val mapped = rows.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val exePath = obj["exePath"]?.jsonPrimitive?.content.orEmpty()
                    if (exePath.isBlank()) return@mapNotNull null
                    val running = obj["running"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
                    val pids = obj["pids"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
                    Mt5ClientRuntimeInfo(
                        id = id,
                        name = if (name.isBlank()) exePath else name,
                        exePath = exePath,
                        running = running,
                        pids = pids
                    )
                }
                _mt5Clients.value = mapped
                if (_mt5SelectedClientExe.value.isBlank() && mapped.isNotEmpty()) {
                    selectMt5ClientExe(mapped.first().exePath)
                }
                appendMt5TerminalLine("clients_refresh count=${mapped.size}")
            }.onFailure { e ->
                _mt5Error.value = "MT5 client list failed: ${e.message}"
                appendMt5TerminalLine("clients_refresh_failed ${e.message}")
            }
            _mt5ClientsLoading.value = false
        }
    }

    fun startSelectedMt5Client() {
        val exePath = _mt5SelectedClientExe.value.trim()
        if (exePath.isBlank()) {
            _mt5Error.value = "Please select MT5 client first"
            return
        }
        scope.launch {
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients/start"
                client.post(endpoint) {
                    header(HttpHeaders.ContentType, "application/json")
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    setBody(buildJsonObject { put("exePath", exePath) }.toString())
                }.bodyAsText()
                _mt5ActionResult.value = "Started MT5 client"
                appendMt5TerminalLine("client_start $exePath")
                delay(1200)
                refreshMt5Clients()
            }.onFailure { e ->
                _mt5Error.value = "Start MT5 client failed: ${e.message}"
                appendMt5TerminalLine("client_start_failed ${e.message}")
            }
        }
    }

    fun stopSelectedMt5Client() {
        val selected = _mt5Clients.value.find { it.exePath == _mt5SelectedClientExe.value }
        if (selected == null || selected.pids.isEmpty()) {
            _mt5ActionResult.value = "Selected MT5 client is not running"
            return
        }
        val pid = selected.pids.first()
        scope.launch {
            runCatching {
                val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/clients/stop"
                client.post(endpoint) {
                    header(HttpHeaders.ContentType, "application/json")
                    if (_mt5AuthToken.value.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    setBody(buildJsonObject { put("pid", pid) }.toString())
                }.bodyAsText()
                _mt5ActionResult.value = "Stopped MT5 client (pid=$pid)"
                appendMt5TerminalLine("client_stop pid=$pid")
                delay(800)
                refreshMt5Clients()
            }.onFailure { e ->
                _mt5Error.value = "Stop MT5 client failed: ${e.message}"
                appendMt5TerminalLine("client_stop_failed ${e.message}")
            }
        }
    }

    fun refreshMt5Terminal(historyLimit: Int = 200, waitMs: Int = 0) {
        if (_mt5IsSyncing.value) return
        if (_mt5PairingStatus.value != "APPROVED") {
            if (_mt5AuthToken.value.isNotBlank()) {
                refreshMt5PairingStatus()
                appendMt5TerminalLine("sync_skipped waiting_for_approval status=${_mt5PairingStatus.value}")
            } else {
                appendMt5TerminalLine("sync_skipped not_connected")
            }
            return
        }
        scope.launch {
            _mt5IsSyncing.value = true
            _mt5Error.value = null
            logDebug("JarvisVM", "MT5 refresh start base=${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)} status=${_mt5PairingStatus.value}")
            try {
                val delta = mt5TerminalService.fetchSnapshotDelta(
                    baseUrl = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value),
                    authToken = _mt5AuthToken.value,
                    historyLimit = historyLimit,
                    sinceRevision = mt5SnapshotRevision,
                    waitMs = waitMs
                )
                if (delta.revision.isNotBlank()) {
                    mt5SnapshotRevision = delta.revision
                }
                _mt5PairingStatus.value = "APPROVED"

                // 2026-04-30 (P6) — successful round-trip: server is reachable.
                _mt5ServerOnline.value = true

                if (!delta.changed || delta.snapshot == null) {
                    if (delta.syncedAt > 0L) _mt5LastSyncAt.value = delta.syncedAt
                    appendMt5TerminalLine("sync_no_change revision=${if (mt5SnapshotRevision.isBlank()) "n/a" else mt5SnapshotRevision}")
                    logDebug("JarvisVM", "MT5 refresh no-change revision=${if (mt5SnapshotRevision.isBlank()) "n/a" else mt5SnapshotRevision}")
                    return@launch
                }

                val snapshot = delta.snapshot
                val changedSectionsRaw = delta.changedSections
                val changedSections = if (changedSectionsRaw.isEmpty()) {
                    setOf("account", "symbols", "positions", "orders", "history")
                } else {
                    changedSectionsRaw
                }
                val merged = applyMt5SnapshotUpdate(snapshot, changedSections)
                refreshMt5ServerFeed()
                appendMt5TerminalLine(
                    "sync_ok changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
                logDebug(
                    "JarvisVM",
                    "MT5 refresh ok changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
            } catch (e: Exception) {
                _mt5Error.value = "MT5 sync failed: ${e.message}"
                // 2026-04-30 (P6) — fetch failed.  Mark server as offline; UI
                // shows the cached view + amber banner instead of a blank
                // screen.  We still try to hydrate from the local cache so
                // the user sees the last-known state.
                _mt5ServerOnline.value = false
                if (e.message?.contains("unavailable", ignoreCase = true) == true ||
                    e.message?.contains("failed", ignoreCase = true) == true) {
                    _mt5PairingStatus.value = "BRIDGE_OFFLINE"
                }
                loadMt5FromDatabase()
                appendMt5TerminalLine("sync_failed ${e.message}")
                logDebug("JarvisVM", "MT5 refresh failed: ${e.message} -> status=${_mt5PairingStatus.value}")
            } finally {
                _mt5IsSyncing.value = false
            }
        }
    }

    fun placeMt5Order(
        action: String,
        symbol: String,
        volume: String,
        sl: String = "",
        tp: String = "",
        comment: String = ""
    ) {
        scope.launch {
            val args = mutableMapOf(
                "action" to action.uppercase(),
                "symbol" to symbol.uppercase().trim(),
                "volume" to volume.trim(),
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/order"
            )
            if (sl.isNotBlank()) args["sl"] = sl.trim()
            if (tp.isNotBlank()) args["tp"] = tp.trim()
            if (comment.isNotBlank()) args["comment"] = comment.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_order", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine("order ${action.uppercase()} ${symbol.uppercase().trim()} volume=${volume.trim()} result=${if (result.isError) "error" else "ok"}")
            refreshMt5Terminal()
        }
    }

    fun closeMt5Position(symbol: String = "", ticket: String = "") {
        scope.launch {
            val args = mutableMapOf(
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/close"
            )
            if (symbol.isNotBlank()) args["symbol"] = symbol.uppercase().trim()
            if (ticket.isNotBlank()) args["ticket"] = ticket.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_close_position", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine("close symbol=${symbol.uppercase().trim()} ticket=${ticket.trim()} result=${if (result.isError) "error" else "ok"}")
            refreshMt5Terminal()
        }
    }

    fun closeMt5AllPositions(side: String = "ALL") {
        val normalizedSide = side.uppercase()
        scope.launch {
            val rows = _mt5Positions.value.filter {
                when (normalizedSide) {
                    "BUY" -> it.side.contains("BUY", ignoreCase = true)
                    "SELL" -> it.side.contains("SELL", ignoreCase = true)
                    else -> true
                }
            }
            if (rows.isEmpty()) {
                _mt5ActionResult.value = "No positions to close for $normalizedSide"
                return@launch
            }
            rows.forEach { row ->
                closeMt5Position(row.symbol, row.ticket)
                delay(120)
            }
        }
    }

    /**
     * แก้ไข SL / TP ของ position ที่เปิดอยู่ผ่าน tool trading_mt5_modify_position
     * sl หรือ tp ถ้าปล่อยว่าง → คงค่าเดิมใน bridge
     */
    fun modifyMt5Position(symbol: String, ticket: String, sl: String = "", tp: String = "") {
        scope.launch {
            val args = mutableMapOf(
                "endpoint" to "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/modify"
            )
            if (symbol.isNotBlank()) args["symbol"] = symbol.uppercase().trim()
            if (ticket.isNotBlank()) args["ticket"] = ticket.trim()
            if (sl.isNotBlank()) args["sl"] = sl.trim()
            if (tp.isNotBlank()) args["tp"] = tp.trim()
            if (_mt5AuthToken.value.isNotBlank()) args["token"] = _mt5AuthToken.value.trim()

            val result = ToolExecutor.execute(ToolCall("trading_mt5_modify_position", args))
            _mt5ActionResult.value = result.result
            if (result.isError) _mt5Error.value = result.result
            appendMt5TerminalLine(
                "modify symbol=${symbol.uppercase().trim()} ticket=${ticket.trim()} sl=${sl.trim()} tp=${tp.trim()} result=${if (result.isError) "error" else "ok"}"
            )
            refreshMt5Terminal()
        }
    }

    /**
     * Break-Even: เลื่อน SL ไปที่ราคาเปิดของทุก position ที่กำลังกำไร
     */
    fun setMt5BreakEvenAll() {
        scope.launch {
            val rows = _mt5Positions.value.filter { it.profit > 0.0 && it.priceOpen > 0.0 }
            if (rows.isEmpty()) {
                _mt5ActionResult.value = "ไม่มี position ที่กำไรอยู่ตอนนี้"
                appendMt5TerminalLine("break_even noop (no profitable positions)")
                return@launch
            }
            appendMt5TerminalLine("break_even start count=${rows.size}")
            rows.forEach { row ->
                modifyMt5Position(
                    symbol = row.symbol,
                    ticket = row.ticket,
                    sl = row.priceOpen.toString(),
                    tp = ""
                )
                delay(150)
            }
            _mt5ActionResult.value = "Break-even ส่งคำสั่งสำเร็จ ${rows.size} ตำแหน่ง"
        }
    }

    /**
     * Backward-compat stub — เรียก modifyMt5Position โดยไม่ส่ง sl/tp
     * (คงไว้เผื่อมีจุดที่ยังเรียกเก่าอยู่)
     */
    @Deprecated("ใช้ modifyMt5Position แทน")
    fun editMt5PositionNotSupported(symbol: String, ticket: String) {
        _mt5ActionResult.value = "กรุณาระบุ SL/TP ใหม่ก่อน ($symbol/$ticket)"
        appendMt5TerminalLine("edit_noop symbol=$symbol ticket=$ticket")
    }

    fun setMt5DefaultLot(value: String) {
        _mt5DefaultLot.value = value
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_lot", value)
        }
    }

    fun setMt5DefaultTpPoints(value: String) {
        _mt5DefaultTpPoints.value = value
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_tp_points", value)
        }
    }

    fun setMt5DefaultSlPoints(value: String) {
        _mt5DefaultSlPoints.value = value
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_default_sl_points", value)
        }
    }

    fun setMt5MaxDdPercent(value: String) {
        _mt5MaxDdPercent.value = value
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("mt5_max_dd_percent", value)
        }
    }

    private fun startMt5AutoSync() {
        // Kept for compatibility with existing calls; realtime channel is primary flow.
        startMt5RealtimeChannel()
    }

    private fun stopMt5AutoSync() {
        mt5AutoSyncJob?.cancel()
        mt5AutoSyncJob = null
    }

    fun startMt5RealtimeChannel() {
        if (mt5RealtimeJob?.isActive == true) return
        if (_mt5PairingStatus.value != "APPROVED") return
        if (_mt5AuthToken.value.isBlank()) return

        mt5RealtimeJob = scope.launch(Dispatchers.IO) {
            appendMt5TerminalLine("realtime_connecting")
            while (_mt5AuthToken.value.isNotBlank() && _mt5PairingStatus.value == "APPROVED") {
                try {
                    val wsUrl = buildMt5WsUrl(normalizeServerRoot(_mt5BridgeBaseUrl.value))
                    val session = client.webSocketSession {
                        url(wsUrl)
                        header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                    }
                    appendMt5TerminalLine("realtime_connected")
                    session.send(Frame.Text("""{"type":"force_snapshot"}"""))

                    val heartbeat = launch {
                        while (true) {
                            delay(15_000)
                            session.send(Frame.Text("""{"type":"ping"}"""))
                        }
                    }

                    for (frame in session.incoming) {
                        if (frame is Frame.Text) {
                            handleMt5RealtimeMessage(frame.readText())
                        }
                    }
                    heartbeat.cancel()
                } catch (e: Exception) {
                    appendMt5TerminalLine("realtime_disconnected ${e.message}")
                    logDebug("JarvisVM", "MT5 realtime disconnected: ${e.message}")
                }
                delay(2500)
            }
        }
    }

    fun stopMt5RealtimeChannel() {
        mt5RealtimeJob?.cancel()
        mt5RealtimeJob = null
        appendMt5TerminalLine("realtime_stopped")
    }

    private suspend fun handleMt5RealtimeMessage(text: String) {
        val root = runCatching { plainJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = root["type"]?.jsonPrimitive?.content.orEmpty().lowercase()
        when (type) {
            "hello" -> {
                appendMt5TerminalLine("realtime_hello")
            }
            "pong" -> {
                // ignore
            }
            "error" -> {
                val message = root["message"]?.jsonPrimitive?.content.orEmpty()
                if (message.isNotBlank()) {
                    _mt5Error.value = message
                    appendMt5TerminalLine("realtime_error $message")
                }
            }
            "snapshot_delta" -> {
                val delta = mt5TerminalService.parseSnapshotDeltaElement(root) ?: return
                if (delta.revision.isNotBlank()) {
                    mt5SnapshotRevision = delta.revision
                }
                if (!delta.changed || delta.snapshot == null) return

                val changedSections = if (delta.changedSections.isEmpty()) {
                    setOf("account", "symbols", "positions", "orders", "history")
                } else {
                    delta.changedSections
                }
                val merged = applyMt5SnapshotUpdate(delta.snapshot, changedSections)
                refreshMt5ServerFeed()
                appendMt5TerminalLine(
                    "realtime_update changed=${changedSections.joinToString(",")} positions=${merged.positions.size} orders=${merged.orders.size} deals=${merged.deals.size}"
                )
            }
        }
    }

    private fun buildMt5WsUrl(serverRoot: String): String {
        val root = serverRoot.trim().trimEnd('/')
        val wsRoot = when {
            root.startsWith("https://", ignoreCase = true) -> "wss://${root.removePrefix("https://")}"
            root.startsWith("http://", ignoreCase = true) -> "ws://${root.removePrefix("http://")}"
            root.startsWith("wss://", ignoreCase = true) || root.startsWith("ws://", ignoreCase = true) -> root
            else -> "ws://$root"
        }
        return "$wsRoot/ws/mt5"
    }

    private fun appendMt5TerminalLine(line: String) {
        val stamp = kotlinx.datetime.Clock.System.now().toString()
        _mt5TerminalFeed.value = (_mt5TerminalFeed.value + "$stamp  $line").takeLast(300)
    }

    private suspend fun refreshMt5ServerFeed() {
        val endpoint = "${normalizeMt5ApiBase(_mt5BridgeBaseUrl.value)}/trade-actions"
        runCatching {
            val text = client.get(endpoint) {
                if (_mt5AuthToken.value.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${_mt5AuthToken.value.trim()}")
                }
            }.bodyAsText()
            val root = plainJson.parseToJsonElement(text).jsonObject
            val rows = root["data"]?.jsonObject?.get("rows")?.jsonArray.orEmpty()
            val lines = rows.take(30).mapNotNull { el ->
                val obj = el.jsonObject
                val ts = obj["created_at"]?.jsonPrimitive?.content.orEmpty()
                val action = obj["action_type"]?.jsonPrimitive?.content.orEmpty()
                val status = obj["status"]?.jsonPrimitive?.content.orEmpty()
                val symbol = obj["symbol"]?.jsonPrimitive?.content.orEmpty()
                val ticket = obj["ticket"]?.jsonPrimitive?.content.orEmpty()
                if (action.isBlank()) null else "$ts  [$action/$status] $symbol ${if (ticket.isBlank()) "" else "ticket=$ticket"}".trim()
            }.distinct()
            if (lines.isNotEmpty()) {
                _mt5TerminalFeed.value = lines
            }
        }
    }

    private data class Mt5MergedSnapshot(
        val account: Mt5AccountInfo?,
        val symbols: List<Mt5SymbolInfo>,
        val positions: List<Mt5TradeItem>,
        val orders: List<Mt5TradeItem>,
        val deals: List<Mt5TradeItem>
    )

    private suspend fun applyMt5SnapshotUpdate(
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot,
        changedSections: Set<String>
    ): Mt5MergedSnapshot {
        val mergedAccount = if (changedSections.contains("account")) snapshot.account else _mt5Account.value
        val mergedSymbols = if (changedSections.contains("symbols")) snapshot.symbols else _mt5Symbols.value
        val mergedPositions = if (changedSections.contains("positions")) snapshot.positions else _mt5Positions.value
        val mergedOrders = if (changedSections.contains("orders")) snapshot.orders else _mt5Orders.value
        val mergedDeals = if (changedSections.contains("history")) snapshot.deals else _mt5Deals.value

        if (changedSections.contains("account")) _mt5Account.value = mergedAccount
        if (changedSections.contains("symbols")) _mt5Symbols.value = mergedSymbols
        if (changedSections.contains("positions")) _mt5Positions.value = mergedPositions
        if (changedSections.contains("orders")) _mt5Orders.value = mergedOrders
        if (changedSections.contains("history")) _mt5Deals.value = mergedDeals
        _mt5LastSyncAt.value = snapshot.syncedAt

        persistMt5Snapshot(
            com.example.personalaibot.tools.trading.Mt5TerminalSnapshot(
                account = mergedAccount,
                symbols = mergedSymbols,
                positions = mergedPositions,
                orders = mergedOrders,
                deals = mergedDeals,
                syncedAt = snapshot.syncedAt
            ),
            changedSections = changedSections
        )
        return Mt5MergedSnapshot(
            account = mergedAccount,
            symbols = mergedSymbols,
            positions = mergedPositions,
            orders = mergedOrders,
            deals = mergedDeals
        )
    }

    /**
     * 2026-04-30 (P6) — delegates to [Mt5LocalCache.saveSnapshot] which mirrors
     * the same SQLDelight inserts but is reusable from non-ViewModel callers
     * (e.g. AutoTradingEngine background sync).  Persistence is in a single
     * place now, easier to evolve.
     */
    private suspend fun persistMt5Snapshot(
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot,
        changedSections: Set<String> = setOf("account", "symbols", "positions", "orders", "history")
    ) {
        com.example.personalaibot.tools.trading.Mt5LocalCache.saveSnapshot(snapshot, changedSections)
    }

    /**
     * 2026-04-30 (P6) — replaced inline DB reads with [Mt5LocalCache.loadSnapshot]
     * so the same cache layer can be reused from any screen.  Behaviour
     * preserved: on first paint we hydrate the StateFlows from local DB so the
     * UI shows the *latest known* MT5 view even if the bridge / core-server
     * is offline.  `_mt5CacheLoading` is set false at the end so screens can
     * stop the skeleton.
     */
    suspend fun loadMt5FromDatabase() {
        withContext(Dispatchers.IO) {
            val q = database.jarvisDatabaseQueries
            val accountRow = q.getLatestMt5Account().executeAsOneOrNull()
            val symbolRows = q.getMt5Symbols().executeAsList()
            val positionRows = q.getRecentMt5TradeRecordsByType("POSITION", 200).executeAsList()
            val orderRows = q.getRecentMt5TradeRecordsByType("ORDER", 200).executeAsList()
            val dealRows = q.getRecentMt5TradeRecordsByType("DEAL", 400).executeAsList()

            withContext(Dispatchers.Main) {
                _mt5Account.value = accountRow?.let {
                    Mt5AccountInfo(
                        login = it.login ?: "",
                        accountName = it.account_name ?: "",
                        server = it.server ?: "",
                        currency = it.currency ?: "USD",
                        leverage = (it.leverage ?: 0L).toInt(),
                        balance = it.balance ?: 0.0,
                        equity = it.equity ?: 0.0,
                        margin = it.margin ?: 0.0,
                        freeMargin = it.free_margin ?: 0.0,
                        payloadJson = it.payload_json ?: "",
                        updatedAt = it.updated_at
                    )
                }

                _mt5Symbols.value = symbolRows.map {
                    Mt5SymbolInfo(
                        symbol = it.symbol,
                        description = it.description ?: "",
                        digits = (it.digits ?: 0L).toInt(),
                        point = it.point ?: 0.0,
                        tradeMode = it.trade_mode ?: "",
                        bid = it.bid ?: 0.0,
                        ask = it.ask ?: 0.0,
                        spread = it.spread ?: 0.0,
                        payloadJson = it.payload_json ?: "",
                        updatedAt = it.updated_at
                    )
                }

                fun mapTradeRows(rows: List<com.example.personalaibot.db.Mt5TradeRecord>): List<Mt5TradeItem> {
                    return rows.map {
                        Mt5TradeItem(
                            recordType = it.record_type,
                            ticket = it.ticket,
                            positionTicket = it.position_ticket ?: "",
                            symbol = it.symbol,
                            side = it.side ?: "",
                            volume = it.volume ?: 0.0,
                            priceOpen = it.price_open ?: 0.0,
                            priceCurrent = it.price_current ?: 0.0,
                            profit = it.profit ?: 0.0,
                            swap = it.swap ?: 0.0,
                            commission = it.commission ?: 0.0,
                            sl = it.sl ?: 0.0,
                            tp = it.tp ?: 0.0,
                            state = it.state ?: "",
                            comment = it.comment ?: "",
                            eventTime = it.event_time ?: 0L,
                            payloadJson = it.payload_json ?: "",
                            syncedAt = it.synced_at
                        )
                    }
                }

                _mt5Positions.value = mapTradeRows(positionRows)
                _mt5Orders.value = mapTradeRows(orderRows)
                _mt5Deals.value = mapTradeRows(dealRows)
                _mt5LastSyncAt.value = listOf(
                    accountRow?.updated_at ?: 0L,
                    symbolRows.maxOfOrNull { it.updated_at } ?: 0L,
                    positionRows.maxOfOrNull { it.synced_at } ?: 0L,
                    orderRows.maxOfOrNull { it.synced_at } ?: 0L,
                    dealRows.maxOfOrNull { it.synced_at } ?: 0L
                ).maxOrNull() ?: 0L
                // 2026-04-30 (P6) — first cache hydration is done.  Screens
                // can now drop the skeleton even if the network never replies.
                _mt5CacheLoading.value = false
            }
        }
    }

    // ─── AI Tracking ──────────────────────────────────────────────────────

    fun updateAiTrackingIntervalSec(seconds: Int) {
        val value = seconds.coerceIn(3, 120)
        _aiTrackingIntervalSec.value = value
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("ai_tracking_interval_sec", value.toString())
        }
    }

    fun updateAiTrackingWatchlist(input: String) {
        _aiTrackingWatchlist.value = input
        scope.launch(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertSetting("ai_tracking_watchlist", input)
        }
    }

    fun startAiTracking() {
        if (_aiTrackingActive.value) return
        _aiTrackingActive.value = true
        _mt5Error.value = null
        aiTrackingJob?.cancel()
        aiTrackingJob = scope.launch(Dispatchers.IO) {
            while (_aiTrackingActive.value) {
                try {
                    val snapshot = mt5TerminalService.fetchSnapshot(
                        baseUrl = normalizeMt5ApiBase(_mt5BridgeBaseUrl.value),
                        authToken = _mt5AuthToken.value,
                        historyLimit = 300
                    )
                    withContext(Dispatchers.Main) {
                        _mt5Account.value = snapshot.account
                        _mt5Symbols.value = snapshot.symbols
                        _mt5Positions.value = snapshot.positions
                        _mt5Orders.value = snapshot.orders
                        _mt5Deals.value = snapshot.deals
                        _mt5LastSyncAt.value = snapshot.syncedAt
                    }
                    persistMt5Snapshot(snapshot)

                    val trackedSymbols = buildTrackedSymbols(snapshot)
                    val insights = trackedSymbols.mapNotNull { symbol ->
                        runCatching { analyzeSymbolRealtime(symbol, snapshot) }.getOrNull()
                    }
                    withContext(Dispatchers.Main) {
                        _aiTrackingInsights.value = insights.sortedBy { it.symbol }
                        appendAiTrackingFeed(
                            "AI Tracking: synced ${insights.size} symbols @ ${snapshot.syncedAt}"
                        )
                    }
                    database.jarvisDatabaseQueries.insertTradeSyncSnapshot(
                        source = "ai_tracking",
                        payload_json = insights.joinToString(prefix = "[", postfix = "]") {
                            """{"symbol":"${it.symbol}","price":${it.lastPrice},"bias":"${it.bias}","history_ok":${it.canFetchHistory},"indicators_ok":${it.indicatorsReady}}"""
                        },
                        synced_at = snapshot.syncedAt
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _mt5Error.value = "AI tracking error: ${e.message}"
                        appendAiTrackingFeed("AI Tracking error: ${e.message}")
                    }
                }
                delay((_aiTrackingIntervalSec.value.coerceIn(3, 120) * 1000L))
            }
        }
    }

    fun stopAiTracking() {
        _aiTrackingActive.value = false
        aiTrackingJob?.cancel()
        aiTrackingJob = null
        appendAiTrackingFeed("AI Tracking stopped")
    }

    private fun appendAiTrackingFeed(line: String) {
        val updated = (_aiTrackingFeed.value + line).takeLast(120)
        _aiTrackingFeed.value = updated
    }

    private fun buildTrackedSymbols(snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot): List<String> {
        val fromPositions = snapshot.positions.map { it.symbol.uppercase() }
        val fromOrders = snapshot.orders.map { it.symbol.uppercase() }
        val fromWatchlist = _aiTrackingWatchlist.value
            .split(",", ";", "\n", " ")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
        return (fromPositions + fromOrders + fromWatchlist)
            .distinct()
            .take(12)
            .ifEmpty { listOf("XAUUSD") }
    }

    private suspend fun analyzeSymbolRealtime(
        symbol: String,
        snapshot: com.example.personalaibot.tools.trading.Mt5TerminalSnapshot
    ): AiTrackingInsight {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val positions = snapshot.positions.filter { it.symbol.equals(symbol, ignoreCase = true) }
        val orders = snapshot.orders.filter { it.symbol.equals(symbol, ignoreCase = true) }

        val f1 = smcApiService.fetchCandlesWithSource(symbol, "1m", 200)
        val f5 = smcApiService.fetchCandlesWithSource(symbol, "5m", 200)
        val f15 = smcApiService.fetchCandlesWithSource(symbol, "15m", 200)
        val candles15 = f15.candles

        val lastPrice = when {
            f1.candles.isNotEmpty() -> f1.candles.last().close
            f5.candles.isNotEmpty() -> f5.candles.last().close
            f15.candles.isNotEmpty() -> f15.candles.last().close
            else -> 0.0
        }

        val canFetchHistory = f1.candles.size >= 60 || f5.candles.size >= 60 || f15.candles.size >= 60

        val ema20 = ema(candles15.map { it.close }, 20)
        val ema50 = ema(candles15.map { it.close }, 50)
        val rsi14 = rsi(candles15.map { it.close }, 14)
        val atr14 = if (candles15.size >= 20) smcApiService.calcATR(candles15, 14) else null
        val indicatorsReady = ema20 != null && ema50 != null && rsi14 != null && atr14 != null

        val bias = when {
            !indicatorsReady -> "DATA_PENDING"
            ema20!! > ema50!! && rsi14!! >= 55.0 -> "BULLISH"
            ema20 < ema50 && rsi14 <= 45.0 -> "BEARISH"
            else -> "NEUTRAL"
        }

        val totalPos = positions.size.coerceAtLeast(1)
        val slCoverage = positions.count { it.sl > 0.0 }.toDouble() / totalPos.toDouble() * 100.0
        val tpCoverage = positions.count { it.tp > 0.0 }.toDouble() / totalPos.toDouble() * 100.0

        return AiTrackingInsight(
            symbol = symbol,
            lastPrice = lastPrice,
            positionCount = positions.size,
            orderCount = orders.size,
            slCoveragePct = if (positions.isNotEmpty()) slCoverage else 100.0,
            tpCoveragePct = if (positions.isNotEmpty()) tpCoverage else 100.0,
            candles1m = f1.candles.size,
            candles5m = f5.candles.size,
            candles15m = f15.candles.size,
            canFetchHistory = canFetchHistory,
            indicatorsReady = indicatorsReady,
            ema20 = ema20,
            ema50 = ema50,
            rsi14 = rsi14,
            atr14 = atr14,
            bias = bias,
            candleSourceSummary = "1m:${f1.source}, 5m:${f5.source}, 15m:${f15.source}",
            updatedAt = now
        )
    }

    private fun ema(values: List<Double>, period: Int): Double? {
        if (values.size < period || period <= 1) return null
        var out = values.take(period).average()
        val k = 2.0 / (period + 1.0)
        for (i in period until values.size) {
            out = values[i] * k + out * (1.0 - k)
        }
        return out
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val diff = values[i] - values[i - 1]
            if (diff >= 0) gain += diff else loss += -diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until values.size) {
            val diff = values[i] - values[i - 1]
            val g = if (diff > 0) diff else 0.0
            val l = if (diff < 0) -diff else 0.0
            avgGain = ((avgGain * (period - 1)) + g) / period
            avgLoss = ((avgLoss * (period - 1)) + l) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun normalizeServerRoot(rawUrl: String): String {
        val url = rawUrl.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url.removeSuffix("/api/mt5")
            url.endsWith("/mt5", ignoreCase = true) -> url.removeSuffix("/mt5")
            else -> url
        }
    }

    fun normalizeMt5ApiBase(rawUrl: String): String {
        val url = rawUrl.trim().trimEnd('/')
        return when {
            url.endsWith("/api/mt5", ignoreCase = true) -> url
            url.endsWith("/mt5", ignoreCase = true) -> url
            else -> "$url/api/mt5"
        }
    }

    private fun generateMt5ClientToken(): String {
        val bytes = Random.Default.nextBytes(24)
        val hex = bytes.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16)).padStart(2, '0') }
        return "pab_${hex}_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
    }
}
