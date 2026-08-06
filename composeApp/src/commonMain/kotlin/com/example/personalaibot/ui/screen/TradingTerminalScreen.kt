package com.example.personalaibot.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.Mt5ClientRuntimeInfo
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.tools.trading.auto.AutoTradingViewModel
import com.example.personalaibot.tools.trading.auto.AutoTradingEngine
import com.example.personalaibot.tools.trading.auto.StrategyType
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.Clock

private enum class Mt5Tab(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Default.Dashboard),
    Events("Even", Icons.Default.Notifications),
    Trade("Trade", Icons.Default.CandlestickChart),
    Auto("Auto", Icons.Default.PlayArrow),
    History("History", Icons.Default.History),
    Settings("Settings", Icons.Default.Settings),
    Connection("Connection", Icons.Default.Link)
}

private val TerminalSurface = Color(0xFF0D1220)
private val PanelSurface = Color(0xFF171C2A)
private val PanelSurfaceHi = Color(0xFF1E2536)
private val BuyGreen = Color(0xFF00E676)
private val SellRed = Color(0xFFFF5252)
private val PositiveGreen = Color(0xFFB9F6CA)
private val NegativeRed = Color(0xFFFF8A80)
private val OutlineSubtle = Color(0xFF2A3247)

private val QuickSymbols = listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD", "US30", "NAS100", "XAGUSD")

@Composable
fun TradingTerminalScreen(
    bridgeBaseUrl: String,
    pairingStatus: String,
    isSyncing: Boolean,
    lastSyncAt: Long,
    // 2026-04-30 (P6) — server reachability + cache loading state, plus a
    // retry callback for the offline banner.  Defaults keep older callers
    // working until they're updated.
    serverOnline: Boolean = true,
    cacheLoading: Boolean = false,
    onRetrySync: () -> Unit = {},
    error: String?,
    actionResult: String?,
    account: Mt5AccountInfo?,
    positions: List<Mt5TradeItem>,
    deals: List<Mt5TradeItem>,
    clients: List<Mt5ClientRuntimeInfo>,
    clientsLoading: Boolean,
    selectedClientExe: String,
    terminalFeed: List<String>,
    defaultLot: String,
    defaultTpPoints: String,
    defaultSlPoints: String,
    maxDdPercent: String,
    onBridgeBaseUrlChange: (String) -> Unit,
    onConnectToggle: (String) -> Unit,
    onClearError: () -> Unit,
    onClearActionResult: () -> Unit,
    onSetDefaultLot: (String) -> Unit,
    onSetDefaultTpPoints: (String) -> Unit,
    onSetDefaultSlPoints: (String) -> Unit,
    onSetMaxDdPercent: (String) -> Unit,
    onSelectClient: (String) -> Unit,
    onRefreshClients: () -> Unit,
    onStartClient: () -> Unit,
    onStopClient: () -> Unit,
    onPlaceOrder: (action: String, symbol: String, volume: String, sl: String, tp: String, comment: String) -> Unit,
    onClosePosition: (symbol: String, ticket: String) -> Unit,
    onCloseAll: (side: String) -> Unit,
    onBreakEvenAll: () -> Unit,
    onEditPosition: (symbol: String, ticket: String, sl: String, tp: String) -> Unit,
    autoTrading: AutoTradingViewModel? = null
) {
    var selectedTab by remember { mutableStateOf(Mt5Tab.Overview) }
    var endpointInput by remember(bridgeBaseUrl) { mutableStateOf(bridgeBaseUrl) }
    var symbolInput by remember { mutableStateOf("XAUUSD") }
    var volumeInput by remember(defaultLot) { mutableStateOf(defaultLot.ifBlank { "0.01" }) }
    var slInput by remember { mutableStateOf("") }
    var tpInput by remember { mutableStateOf("") }
    var commentInput by remember { mutableStateOf("JARVIS") }

    // Dialog state — selected position for SL/TP editing
    var editTarget by remember { mutableStateOf<Mt5TradeItem?>(null) }

    val isConnected = pairingStatus.equals("APPROVED", ignoreCase = true)
    val runningPnl = positions.sumOf { it.netPnl }
    // History: only show exit deals (entry>0) which have the real P&L.
    // Entry deals (entry=0) always have profit=0 and pollute counts.
    val exitDeals = remember(deals) { deals.filter { it.isExitDeal } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisTheme.Dark)
    ) {
        // Sticky status ribbon (always visible across tabs)
        TopStatusRibbon(
            isConnected = isConnected,
            pairingStatus = pairingStatus,
            isSyncing = isSyncing,
            positionsCount = positions.size,
            runningPnl = runningPnl,
            currency = account?.currency ?: "USD"
        )

        // Tab bar
        TabBar(selected = selectedTab, onSelect = { selectedTab = it })

        // 2026-04-30 (P6) — Offline / stale-data banner.  Shows automatically
        // when the bridge can't be reached, or when the cached snapshot is
        // older than 30s.  Cached MT5 view below still renders so the user
        // never sees a blank screen.
        com.example.personalaibot.ui.component.OfflineBanner(
            serverOnline = serverOnline,
            lastSyncMs = lastSyncAt,
            onRetry = onRetrySync,
        )

        // Inline transient banners
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            error?.let {
                TransientBanner(
                    message = it,
                    accent = NegativeRed,
                    icon = Icons.Default.Warning,
                    onClose = onClearError
                )
            }
        }
        AnimatedVisibility(visible = actionResult != null, enter = fadeIn(), exit = fadeOut()) {
            actionResult?.let {
                TransientBanner(
                    message = it,
                    accent = PositiveGreen,
                    icon = Icons.Default.PlayArrow,
                    onClose = onClearActionResult
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (selectedTab) {
                Mt5Tab.Overview -> OverviewTab(
                    account = account,
                    positions = positions,
                    deals = exitDeals,
                    runningPnl = runningPnl,
                    lastSyncAt = lastSyncAt
                )
                Mt5Tab.Events -> {
                    if (autoTrading != null) {
                        EventsTab(vm = autoTrading)
                    } else {
                        EmptyState("Decision Feed ยังไม่พร้อม")
                    }
                }
                Mt5Tab.Trade -> TradeTab(
                    brokerId = com.example.personalaibot.tools.trading.Mt5LocalCache.brokerIdFor(account),
                    symbolInput = symbolInput,
                    volumeInput = volumeInput,
                    slInput = slInput,
                    tpInput = tpInput,
                    commentInput = commentInput,
                    positions = positions,
                    runningPnl = runningPnl,
                    currency = account?.currency ?: "USD",
                    onSymbolChange = { symbolInput = it },
                    onVolumeChange = { volumeInput = it },
                    onSlChange = { slInput = it },
                    onTpChange = { tpInput = it },
                    onCommentChange = { commentInput = it },
                    onBuy = { onPlaceOrder("BUY", symbolInput, volumeInput, slInput, tpInput, commentInput) },
                    onSell = { onPlaceOrder("SELL", symbolInput, volumeInput, slInput, tpInput, commentInput) },
                    onClosePosition = onClosePosition,
                    onCloseAll = onCloseAll,
                    onBreakEvenAll = onBreakEvenAll,
                    onEditPosition = { sym, tk ->
                        editTarget = positions.firstOrNull {
                            it.symbol.equals(sym, ignoreCase = true) && it.ticket == tk
                        } ?: Mt5TradeItem(
                            recordType = "position",
                            ticket = tk,
                            symbol = sym
                        )
                    }
                )
                Mt5Tab.Auto -> {
                    if (autoTrading != null) {
                        AutoTradingPanel(vm = autoTrading)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Auto-Trading Engine ยังไม่พร้อม",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Mt5Tab.History -> HistoryTab(deals = exitDeals)
                Mt5Tab.Settings -> SettingsTab(
                    defaultLot = defaultLot,
                    defaultTpPoints = defaultTpPoints,
                    defaultSlPoints = defaultSlPoints,
                    maxDdPercent = maxDdPercent,
                    onSetDefaultLot = onSetDefaultLot,
                    onSetDefaultTpPoints = onSetDefaultTpPoints,
                    onSetDefaultSlPoints = onSetDefaultSlPoints,
                    onSetMaxDdPercent = onSetMaxDdPercent
                )
                Mt5Tab.Connection -> ConnectionTab(
                    endpointInput = endpointInput,
                    isConnected = isConnected,
                    terminalFeed = terminalFeed,
                    clients = clients,
                    clientsLoading = clientsLoading,
                    selectedClientExe = selectedClientExe,
                    onEndpointChange = {
                        endpointInput = it
                        onBridgeBaseUrlChange(it)
                    },
                    onConnectToggle = { onConnectToggle(endpointInput) },
                    onSelectClient = onSelectClient,
                    onRefreshClients = onRefreshClients,
                    onStartClient = onStartClient,
                    onStopClient = onStopClient
                )
            }
        }
    }

    // Edit SL / TP dialog
    editTarget?.let { target ->
        EditPositionDialog(
            target = target,
            onDismiss = { editTarget = null },
            onSave = { newSl, newTp ->
                onEditPosition(target.symbol, target.ticket, newSl, newTp)
                editTarget = null
            }
        )
    }
}

@Composable
private fun EditPositionDialog(
    target: Mt5TradeItem,
    onDismiss: () -> Unit,
    onSave: (sl: String, tp: String) -> Unit
) {
    val initialSl = if (target.sl > 0.0) formatPrice(target.sl) else ""
    val initialTp = if (target.tp > 0.0) formatPrice(target.tp) else ""
    var slInput by remember { mutableStateOf(initialSl) }
    var tpInput by remember { mutableStateOf(initialTp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Card,
        titleContentColor = JarvisTheme.Cyan,
        textContentColor = Color.White.copy(alpha = 0.9f),
        title = {
            Text(
                "Edit SL / TP — ${target.symbol} #${target.ticket}",
                color = JarvisTheme.Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniLabel(modifier = Modifier.weight(1f), label = "Side", value = target.side.ifBlank { "-" })
                    MiniLabel(modifier = Modifier.weight(1f), label = "Open", value = formatPrice(target.priceOpen))
                    MiniLabel(modifier = Modifier.weight(1f), label = "Now", value = formatPrice(target.priceCurrent))
                }
                OutlinedTextField(
                    value = slInput,
                    onValueChange = { slInput = it },
                    label = { Text("Stop Loss (ราคา)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = OutlineSubtle,
                        cursorColor = JarvisTheme.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tpInput,
                    onValueChange = { tpInput = it },
                    label = { Text("Take Profit (ราคา)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = OutlineSubtle,
                        cursorColor = JarvisTheme.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "ปล่อยว่างเพื่อคงค่าเดิมใน MT5",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(slInput.trim(), tpInput.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisTheme.Cyan,
                    contentColor = Color.Black
                )
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ─── Status ribbon & tabs ────────────────────────────────────────────────────

@Composable
private fun TopStatusRibbon(
    isConnected: Boolean,
    pairingStatus: String,
    isSyncing: Boolean,
    positionsCount: Int,
    runningPnl: Double,
    currency: String
) {
    val statusDot = when {
        isConnected && isSyncing -> JarvisTheme.Cyan
        isConnected -> PositiveGreen
        else -> NegativeRed
    }
    val pnlColor = if (runningPnl >= 0) PositiveGreen else NegativeRed

    Surface(color = JarvisTheme.Surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusDot)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MT5 TERMINAL",
                    color = JarvisTheme.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (isConnected) "$pairingStatus · ${if (isSyncing) "SYNCING" else "IDLE"}" else pairingStatus,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
            StatChip(label = "Positions", value = positionsCount.toString())
            Spacer(Modifier.width(6.dp))
            StatChip(
                label = "PnL",
                value = formatMoney(runningPnl, currency),
                valueColor = pnlColor
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color = Color.White) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .background(JarvisTheme.Card, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TabBar(selected: Mt5Tab, onSelect: (Mt5Tab) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(Mt5Tab.entries.toList()) { tab ->
            val isSelected = tab == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) JarvisTheme.Cyan.copy(alpha = 0.15f) else JarvisTheme.Card)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) JarvisTheme.Cyan else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.label,
                    color = if (isSelected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TransientBanner(
    message: String,
    accent: Color,
    icon: ImageVector,
    onClose: () -> Unit
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Overview Tab ────────────────────────────────────────────────────────────

// ── Period selector enum (shared by Overview + History) ───────────────────────
private enum class PeriodFilter(val label: String, val days: Int) {
    Today("Today", 1),
    Week("7D", 7),
    Month("30D", 30),
    All("All", Int.MAX_VALUE),
}

private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

private fun List<Mt5TradeItem>.filterByPeriod(period: PeriodFilter): List<Mt5TradeItem> {
    if (period == PeriodFilter.All) return this
    val cutoffMs = nowMs() - period.days.toLong() * 86_400_000L
    return filter { it.eventTime >= cutoffMs }
}

private data class PeriodStats(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val totalProfit: Double,
    val avgProfit: Double,
    val bestTrade: Double,
    val worstTrade: Double,
    val winRate: Double,
)

private fun computeStats(deals: List<Mt5TradeItem>): PeriodStats {
    if (deals.isEmpty()) return PeriodStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
    val profits = deals.map { it.netPnl }
    val wins = profits.count { it > 0 }
    val losses = profits.count { it < 0 }
    val total = profits.sumOf { it }
    return PeriodStats(
        total = deals.size,
        wins = wins,
        losses = losses,
        totalProfit = total,
        avgProfit = total / deals.size,
        bestTrade = profits.maxOrNull() ?: 0.0,
        worstTrade = profits.minOrNull() ?: 0.0,
        winRate = if (deals.isNotEmpty()) wins.toDouble() / deals.size * 100.0 else 0.0,
    )
}

@Composable
private fun OverviewTab(
    account: Mt5AccountInfo?,
    positions: List<Mt5TradeItem>,
    deals: List<Mt5TradeItem>,
    runningPnl: Double,
    lastSyncAt: Long
) {
    val balance = account?.balance ?: 0.0
    val equity = account?.equity ?: 0.0
    val margin = account?.margin ?: 0.0
    val freeMargin = account?.freeMargin ?: 0.0
    val currency = account?.currency ?: "USD"
    val marginLevelPct = if (margin > 0.0) (equity / margin) * 100.0 else 0.0
    val marginHealthRatio = when {
        margin <= 0.0 -> 1.0
        marginLevelPct >= 1000.0 -> 1.0
        marginLevelPct <= 100.0 -> 0.15
        else -> (marginLevelPct / 1000.0).coerceIn(0.1, 1.0)
    }
    val marginHealthColor = when {
        marginLevelPct <= 0.0 && margin <= 0.0 -> PositiveGreen
        marginLevelPct >= 500.0 -> PositiveGreen
        marginLevelPct >= 150.0 -> JarvisTheme.Cyan
        else -> NegativeRed
    }
    val lossCount = positions.count { it.netPnl < 0.0 }
    val winCount = positions.count { it.netPnl > 0.0 }

    // P2 — period selector state
    var selectedPeriod by remember { mutableStateOf(PeriodFilter.Today) }
    val periodDeals = remember(deals, selectedPeriod) { deals.filterByPeriod(selectedPeriod) }
    val stats = remember(periodDeals) { computeStats(periodDeals) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        // Hero PnL card
        item {
            HeroPnlCard(runningPnl = runningPnl, currency = currency, equity = equity, lastSyncAt = lastSyncAt)
        }

        // 2x2 metric grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Balance",
                    value = formatMoney(balance, currency),
                    icon = Icons.Default.AccountBalance,
                    accent = JarvisTheme.Cyan
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Equity",
                    value = formatMoney(equity, currency),
                    icon = Icons.Default.Balance,
                    accent = JarvisTheme.Purple
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Margin",
                    value = formatMoney(margin, currency),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accent = Color(0xFFFFB74D)
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Free Margin",
                    value = formatMoney(freeMargin, currency),
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    accent = PositiveGreen
                )
            }
        }

        // Margin level bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Margin Level", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (margin <= 0.0) "—" else "${marginLevelPct.roundToInt()}%",
                            color = marginHealthColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { marginHealthRatio.toFloat() },
                        color = marginHealthColor,
                        trackColor = OutlineSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = when {
                            margin <= 0.0 -> "No margin used"
                            marginLevelPct >= 500.0 -> "Healthy"
                            marginLevelPct >= 150.0 -> "Caution"
                            else -> "At risk"
                        },
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // P2 — Period selector + stats card
        item {
            PeriodStatsCard(
                currency = currency,
                selectedPeriod = selectedPeriod,
                onPeriodChange = { selectedPeriod = it },
                stats = stats,
            )
        }

        // Account details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Account", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    KvRow("Login", account?.login?.ifBlank { "—" } ?: "—")
                    KvRow("Server", account?.server?.ifBlank { "—" } ?: "—")
                    KvRow("Name", account?.accountName?.ifBlank { "—" } ?: "—")
                    KvRow("Currency", currency)
                    KvRow("Leverage", if ((account?.leverage ?: 0) > 0) "1 : ${account?.leverage}" else "—")
                }
            }
        }

        // Open positions summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Open Positions", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${positions.size} active", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PnlMiniStat(modifier = Modifier.weight(1f), label = "Winning", value = winCount.toString(), color = PositiveGreen)
                        PnlMiniStat(modifier = Modifier.weight(1f), label = "Losing", value = lossCount.toString(), color = NegativeRed)
                        PnlMiniStat(modifier = Modifier.weight(1f), label = "History", value = deals.size.toString(), color = JarvisTheme.Cyan)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

// ── Period Stats Card (P2) ─────────────────────────────────────────────────────
@Composable
private fun PeriodStatsCard(
    currency: String,
    selectedPeriod: PeriodFilter,
    onPeriodChange: (PeriodFilter) -> Unit,
    stats: PeriodStats,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Period chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PeriodFilter.entries.forEach { period ->
                    val selected = period == selectedPeriod
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.18f) else PanelSurfaceHi)
                            .border(1.dp, if (selected) JarvisTheme.Cyan else OutlineSubtle, RoundedCornerShape(6.dp))
                            .clickable { onPeriodChange(period) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            period.label,
                            color = if (selected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (stats.total == 0) {
                Text(
                    "No closed trades in this period",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                // Win-rate gauge row
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Win Rate", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${stats.winRate.roundToInt()}%",
                                color = if (stats.winRate >= 50) PositiveGreen else NegativeRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (stats.winRate / 100.0).toFloat().coerceIn(0f, 1f) },
                            color = if (stats.winRate >= 50) PositiveGreen else NegativeRed,
                            trackColor = OutlineSubtle,
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Stats 2x3 grid
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMini(modifier = Modifier.weight(1f), label = "Trades", value = stats.total.toString(), color = JarvisTheme.Cyan)
                    StatMini(modifier = Modifier.weight(1f), label = "Wins", value = stats.wins.toString(), color = PositiveGreen)
                    StatMini(modifier = Modifier.weight(1f), label = "Losses", value = stats.losses.toString(), color = NegativeRed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMini(
                        modifier = Modifier.weight(1f),
                        label = "Total PnL",
                        value = formatSignedMoney(stats.totalProfit, currency),
                        color = if (stats.totalProfit >= 0) PositiveGreen else NegativeRed,
                    )
                    StatMini(
                        modifier = Modifier.weight(1f),
                        label = "Best Trade",
                        value = formatSignedMoney(stats.bestTrade, currency),
                        color = PositiveGreen,
                    )
                    StatMini(
                        modifier = Modifier.weight(1f),
                        label = "Worst",
                        value = formatSignedMoney(stats.worstTrade, currency),
                        color = NegativeRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMini(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PanelSurfaceHi)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroPnlCard(runningPnl: Double, currency: String, equity: Double, lastSyncAt: Long) {
    val positive = runningPnl >= 0.0
    val accent = if (positive) BuyGreen else SellRed
    val gradient = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.35f), JarvisTheme.Card.copy(alpha = 0.9f))
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient, RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (positive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE PnL", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (lastSyncAt > 0) PositiveGreen.copy(alpha = 0.2f) else NegativeRed.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (lastSyncAt > 0) "SYNCED" else "OFFLINE",
                            color = if (lastSyncAt > 0) PositiveGreen else NegativeRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatSignedMoney(runningPnl, currency),
                    color = accent,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Equity", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(formatMoney(equity, currency), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (lastSyncAt > 0) {
                        Text("updated ${formatRelativeTs(lastSyncAt)}", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PnlMiniStat(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KvRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(96.dp))
        Text(value, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Trade Tab ───────────────────────────────────────────────────────────────

@Composable
private fun TradeTab(
    brokerId: String,
    symbolInput: String,
    volumeInput: String,
    slInput: String,
    tpInput: String,
    commentInput: String,
    positions: List<Mt5TradeItem>,
    runningPnl: Double,
    currency: String,
    onSymbolChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onSlChange: (String) -> Unit,
    onTpChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onClosePosition: (symbol: String, ticket: String) -> Unit,
    onCloseAll: (side: String) -> Unit,
    onBreakEvenAll: () -> Unit,
    onEditPosition: (symbol: String, ticket: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // Order entry card
        item {
            OrderEntryCard(
                brokerId = brokerId,
                symbolInput = symbolInput,
                volumeInput = volumeInput,
                slInput = slInput,
                tpInput = tpInput,
                commentInput = commentInput,
                onSymbolChange = onSymbolChange,
                onVolumeChange = onVolumeChange,
                onSlChange = onSlChange,
                onTpChange = onTpChange,
                onCommentChange = onCommentChange,
                onBuy = onBuy,
                onSell = onSell
            )
        }

        // Quick actions
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Actions", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SoftButton(
                            modifier = Modifier.weight(1f),
                            text = "Break-Even",
                            color = JarvisTheme.Cyan,
                            onClick = onBreakEvenAll
                        )
                        SoftButton(
                            modifier = Modifier.weight(1f),
                            text = "Close Buys",
                            color = BuyGreen,
                            onClick = { onCloseAll("BUY") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SoftButton(
                            modifier = Modifier.weight(1f),
                            text = "Close Sells",
                            color = SellRed,
                            onClick = { onCloseAll("SELL") }
                        )
                        SoftButton(
                            modifier = Modifier.weight(1f),
                            text = "Close All",
                            color = NegativeRed,
                            onClick = { onCloseAll("ALL") }
                        )
                    }
                }
            }
        }

        // Open positions header
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                Text("Open Positions", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("PnL ${formatMoney(runningPnl, currency)}", color = if (runningPnl >= 0) PositiveGreen else NegativeRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (positions.isEmpty()) {
            item { EmptyState("No open positions — place your first order above") }
        } else {
            items(positions) { row ->
                PositionCard(
                    row = row,
                    onClose = { onClosePosition(row.symbol, row.ticket) },
                    onEdit = { onEditPosition(row.symbol, row.ticket) }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun OrderEntryCard(
    brokerId: String,
    symbolInput: String,
    volumeInput: String,
    slInput: String,
    tpInput: String,
    commentInput: String,
    onSymbolChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onSlChange: (String) -> Unit,
    onTpChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    // ── Dynamic chip list: broker favorites + fallback to hardcoded defaults ──
    var quickSymbols by remember(brokerId) { mutableStateOf(QuickSymbols) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(brokerId) {
        val favs = com.example.personalaibot.tools.trading.BrokerSymbolCache.favoritesFirst(brokerId)
        if (favs.isNotEmpty()) quickSymbols = favs
    }

    // ── Symbol Picker Dialog ──────────────────────────────────────────────────
    if (showPicker) {
        SymbolPickerDialog(
            brokerId = brokerId,
            onSymbolSelected = { sym ->
                onSymbolChange(sym)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row: "New Order" title + Browse button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "New Order",
                    color = JarvisTheme.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PanelSurfaceHi)
                        .border(1.dp, OutlineSubtle, RoundedCornerShape(6.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "Browse…",
                        color = JarvisTheme.Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Quick symbol chips (broker favorites or hardcoded fallback)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(quickSymbols) { sym ->
                    val selected = sym.equals(symbolInput, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.2f) else PanelSurfaceHi)
                            .border(
                                width = 1.dp,
                                color = if (selected) JarvisTheme.Cyan else OutlineSubtle,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSymbolChange(sym) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            sym,
                            color = if (selected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                // + Browse shortcut chip at end of row
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PanelSurfaceHi)
                            .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { showPicker = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("+ More", color = JarvisTheme.Cyan.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }

            // Symbol + volume stepper
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                JarvisTextField(
                    value = symbolInput,
                    onValueChange = onSymbolChange,
                    label = "Symbol",
                    modifier = Modifier.weight(1f)
                )
                VolumeStepper(
                    value = volumeInput,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1.2f)
                )
            }

            // SL / TP
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                JarvisTextField(
                    value = slInput,
                    onValueChange = onSlChange,
                    label = "Stop Loss",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                JarvisTextField(
                    value = tpInput,
                    onValueChange = onTpChange,
                    label = "Take Profit",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            JarvisTextField(
                value = commentInput,
                onValueChange = onCommentChange,
                label = "Comment",
                modifier = Modifier.fillMaxWidth()
            )

            // BUY/SELL buttons (big & bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSell,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SellRed, contentColor = Color.White)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SELL", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Button(
                    onClick = onBuy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BuyGreen, contentColor = JarvisTheme.Dark)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("BUY", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}

@Composable
private fun VolumeStepper(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val step = 0.01
    fun applyDelta(delta: Double) {
        val current = value.toDoubleOrNull() ?: 0.0
        val next = (current + delta).coerceAtLeast(0.0)
        onValueChange(formatVolume(next))
    }
    Column(modifier = modifier) {
        Text("Volume (lot)", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PanelSurfaceHi)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
        ) {
            StepperButton(text = "−", onClick = { applyDelta(-step) })
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JarvisTheme.Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = JarvisTheme.Cyan,
                        unfocusedTextColor = JarvisTheme.Cyan,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxHeight()
                )
            }
            StepperButton(text = "+", onClick = { applyDelta(step) })
        }
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = JarvisTheme.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun JarvisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = JarvisTheme.Cyan,
            unfocusedBorderColor = OutlineSubtle,
            focusedLabelColor = JarvisTheme.Cyan,
            unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
            cursorColor = JarvisTheme.Cyan
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
private fun SoftButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color
        )
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PositionCard(
    row: Mt5TradeItem,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    val isBuy = row.side.equals("BUY", ignoreCase = true)
    val sideColor = if (isBuy) BuyGreen else SellRed
    val pnlColor = if (row.netPnl >= 0) PositiveGreen else NegativeRed

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = Modifier.fillMaxWidth().border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(sideColor.copy(alpha = 0.18f))
                        .border(1.dp, sideColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isBuy) "BUY" else "SELL",
                        color = sideColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.symbol, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("#${row.ticket} · ${formatVolume(row.volume)} lot", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatSignedMoney(row.netPnl), color = pnlColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    if (row.swap != 0.0 || row.commission != 0.0) {
                        Text("fee ${formatSignedMoney(row.swap + row.commission)}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    }
                }
            }

            // Visual Risk/Reward Bar (if SL/TP exist)
            if (row.sl > 0.0 && row.tp > 0.0) {
                val totalDist = kotlin.math.abs(row.tp - row.sl)
                if (totalDist > 0) {
                    val currentDist = kotlin.math.abs(row.priceCurrent - row.sl)
                    val progress = (currentDist / totalDist).toFloat().coerceIn(0f, 1f)
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SL", color = NegativeRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("TP", color = PositiveGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = JarvisTheme.Cyan,
                            trackColor = OutlineSubtle,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            // Price Labels
            Row(modifier = Modifier.fillMaxWidth().background(PanelSurfaceHi, RoundedCornerShape(8.dp)).padding(8.dp)) {
                MiniLabel(modifier = Modifier.weight(1f), label = "Open", value = formatPrice(row.priceOpen))
                MiniLabel(modifier = Modifier.weight(1f), label = "Current", value = formatPrice(row.priceCurrent))
                MiniLabel(modifier = Modifier.weight(1f), label = "SL", value = if (row.sl > 0) formatPrice(row.sl) else "—")
                MiniLabel(modifier = Modifier.weight(1f), label = "TP", value = if (row.tp > 0) formatPrice(row.tp) else "—")
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SoftButton(
                    modifier = Modifier.weight(1f),
                    text = "Edit Position",
                    color = JarvisTheme.Cyan,
                    onClick = onEdit
                )
                SoftButton(
                    modifier = Modifier.weight(1f),
                    text = "Close Position",
                    color = NegativeRed,
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun MiniLabel(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

// ─── History Tab ─────────────────────────────────────────────────────────────

private enum class HistoryFilter(val label: String) {
    All("All"),
    Buy("Buys"),
    Sell("Sells"),
    Winners("Winners"),
    Losers("Losers")
}

// P5 — HistoryTab with period selector + extended stats strip
@Composable
private fun HistoryTab(deals: List<Mt5TradeItem>) {
    var sideFilter by remember { mutableStateOf(HistoryFilter.All) }
    var periodFilter by remember { mutableStateOf(PeriodFilter.All) }

    // Apply period first, then side/outcome filter
    val periodDeals = remember(deals, periodFilter) { deals.filterByPeriod(periodFilter) }
    val filtered = remember(sideFilter, periodDeals) {
        when (sideFilter) {
            HistoryFilter.All     -> periodDeals
            HistoryFilter.Buy     -> periodDeals.filter { it.side.equals("BUY", ignoreCase = true) }
            HistoryFilter.Sell    -> periodDeals.filter { it.side.equals("SELL", ignoreCase = true) }
            HistoryFilter.Winners -> periodDeals.filter { it.netPnl > 0.0 }
            HistoryFilter.Losers  -> periodDeals.filter { it.netPnl < 0.0 }
        }
    }
    val stats = remember(filtered) { computeStats(filtered) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // ── Period chips ──────────────────────────────────────────────────────
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(PeriodFilter.entries.toList()) { p ->
                    val selected = p == periodFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) JarvisTheme.Purple.copy(alpha = 0.22f) else PanelSurfaceHi)
                            .border(1.dp, if (selected) JarvisTheme.Purple else OutlineSubtle, RoundedCornerShape(8.dp))
                            .clickable { periodFilter = p }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            p.label,
                            color = if (selected) JarvisTheme.Purple else Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // ── Side/Outcome chips ────────────────────────────────────────────────
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(HistoryFilter.entries.toList()) { f ->
                    val selected = f == sideFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.2f) else PanelSurfaceHi)
                            .border(1.dp, if (selected) JarvisTheme.Cyan else OutlineSubtle, RoundedCornerShape(8.dp))
                            .clickable { sideFilter = f }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            f.label,
                            color = if (selected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── Extended stats card ───────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (stats.total == 0) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No trades in this period", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Win-rate bar
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Win Rate", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.width(72.dp))
                            LinearProgressIndicator(
                                progress = { (stats.winRate / 100.0).toFloat().coerceIn(0f, 1f) },
                                color = if (stats.winRate >= 50) PositiveGreen else NegativeRed,
                                trackColor = OutlineSubtle,
                                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${stats.winRate.roundToInt()}%",
                                color = if (stats.winRate >= 50) PositiveGreen else NegativeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(38.dp),
                            )
                        }
                        // Stats grid row 1
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMini(Modifier.weight(1f), "Trades", stats.total.toString(), JarvisTheme.Cyan)
                            StatMini(Modifier.weight(1f), "Wins", stats.wins.toString(), PositiveGreen)
                            StatMini(Modifier.weight(1f), "Losses", stats.losses.toString(), NegativeRed)
                        }
                        // Stats grid row 2
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMini(
                                Modifier.weight(1f), "Total PnL",
                                formatSignedMoney(stats.totalProfit),
                                if (stats.totalProfit >= 0) PositiveGreen else NegativeRed,
                            )
                            StatMini(
                                Modifier.weight(1f), "Avg/Trade",
                                formatSignedMoney(stats.avgProfit),
                                if (stats.avgProfit >= 0) PositiveGreen else NegativeRed,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMini(Modifier.weight(1f), "Best Trade", formatSignedMoney(stats.bestTrade), PositiveGreen)
                            StatMini(Modifier.weight(1f), "Worst Trade", formatSignedMoney(stats.worstTrade), NegativeRed)
                        }
                    }
                }
            }
        }

        // ── Deal rows ─────────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            item { EmptyState("No history matches this filter") }
        } else {
            items(filtered) { row -> HistoryRow(row) }
        }
    }
}

@Composable
private fun HistoryRow(row: Mt5TradeItem) {
    val isBuy = row.side.equals("BUY", ignoreCase = true)
    val sideColor = if (isBuy) BuyGreen else SellRed
    val pnlColor = if (row.netPnl >= 0) PositiveGreen else NegativeRed

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(sideColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBuy) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = sideColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.symbol, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "#${row.ticket} · ${formatVolume(row.volume)} lot · @${formatPrice(row.priceOpen)}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatSignedMoney(row.netPnl), color = pnlColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (row.commission != 0.0 || row.swap != 0.0) {
                        Text(
                            "fee ${formatSignedMoney(row.commission + row.swap)}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp
                        )
                    }
                }
            }
            if (row.state.isNotBlank() || row.sl > 0 || row.tp > 0) {
                Row {
                    if (row.state.isNotBlank()) {
                        Text(row.state, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (row.sl > 0) {
                        Text("SL ${formatPrice(row.sl)}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (row.tp > 0) {
                        Text("TP ${formatPrice(row.tp)}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ─── Settings Tab ────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(
    defaultLot: String,
    defaultTpPoints: String,
    defaultSlPoints: String,
    maxDdPercent: String,
    onSetDefaultLot: (String) -> Unit,
    onSetDefaultTpPoints: (String) -> Unit,
    onSetDefaultSlPoints: (String) -> Unit,
    onSetMaxDdPercent: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Default Trade Settings", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    SettingRow(
                        label = "Default lot size",
                        helper = "Initial volume used for new orders (e.g. 0.01)",
                        value = defaultLot,
                        onValueChange = onSetDefaultLot,
                        keyboardType = KeyboardType.Decimal
                    )
                    SettingRow(
                        label = "Default TP (points)",
                        helper = "0 disables auto Take-Profit",
                        value = defaultTpPoints,
                        onValueChange = onSetDefaultTpPoints,
                        keyboardType = KeyboardType.Number
                    )
                    SettingRow(
                        label = "Default SL (points)",
                        helper = "0 disables auto Stop-Loss",
                        value = defaultSlPoints,
                        onValueChange = onSetDefaultSlPoints,
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Risk Controls", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    SettingRow(
                        label = "Max Drawdown %",
                        helper = "Auto-close all positions when drawdown exceeds this value",
                        value = maxDdPercent,
                        onValueChange = onSetMaxDdPercent,
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }
        }

        item {
            Text(
                "ค่าจะถูกบันทึกอัตโนมัติ · Changes save immediately",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    helper: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(helper, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        JarvisTextField(
            value = value,
            onValueChange = onValueChange,
            label = "",
            keyboardType = keyboardType,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Connection Tab ──────────────────────────────────────────────────────────

@Composable
private fun ConnectionTab(
    endpointInput: String,
    isConnected: Boolean,
    terminalFeed: List<String>,
    clients: List<Mt5ClientRuntimeInfo>,
    clientsLoading: Boolean,
    selectedClientExe: String,
    onEndpointChange: (String) -> Unit,
    onConnectToggle: () -> Unit,
    onSelectClient: (String) -> Unit,
    onRefreshClients: () -> Unit,
    onStartClient: () -> Unit,
    onStopClient: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Endpoint + connect toggle
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bridge Endpoint", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisTextField(
                        value = endpointInput,
                        onValueChange = onEndpointChange,
                        label = "Base URL",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onConnectToggle,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) NegativeRed.copy(alpha = 0.2f) else JarvisTheme.Cyan,
                            contentColor = if (isConnected) NegativeRed else JarvisTheme.Dark
                        ),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(
                            if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isConnected) "Disconnect" else "Connect",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // MT5 clients
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MT5 Clients", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefreshClients, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (clientsLoading) JarvisTheme.Cyan.copy(alpha = 0.5f) else JarvisTheme.Cyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onStartClient, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = BuyGreen, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onStopClient, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = SellRed, modifier = Modifier.size(20.dp))
                    }
                }
                if (clients.isEmpty()) {
                    Text(
                        if (clientsLoading) "Scanning..." else "No MT5 client found",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                } else {
                    clients.forEach { client ->
                        ClientRow(
                            client = client,
                            selected = selectedClientExe.equals(client.exePath, ignoreCase = true),
                            onSelect = { onSelectClient(client.exePath) }
                        )
                    }
                }
            }
        }

        // Server terminal feed
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Server Terminal", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${terminalFeed.size} lines", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                }
                Surface(
                    color = TerminalSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp),
                ) {
                    if (terminalFeed.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No server output yet", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(terminalFeed.asReversed()) { line ->
                                val color = when {
                                    line.contains("[ERR", ignoreCase = true) ||
                                    line.contains("ERROR", ignoreCase = true) -> Color(0xFFFF5252)
                                    line.contains("[WARN", ignoreCase = true) -> Color(0xFFFFB74D)
                                    line.contains("[OK", ignoreCase = true) ||
                                    line.contains("SUCCESS", ignoreCase = true) -> Color(0xFF69F0AE)
                                    else -> Color(0xFF9EA7C0)
                                }
                                Text(
                                    line,
                                    color = color,
                                    fontSize = 9.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Client Row ──────────────────────────────────────────────────────────────

@Composable
private fun ClientRow(
    client: com.example.personalaibot.Mt5ClientRuntimeInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.12f) else PanelSurfaceHi)
            .border(1.dp, if (selected) JarvisTheme.Cyan else OutlineSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (client.running) BuyGreen else NegativeRed),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                client.exePath.substringAfterLast('\\').substringAfterLast('/'),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                client.exePath,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(14.dp))
        }
    }
}

// ─── Events Tab (live decision feed) ─────────────────────────────────────────

@Composable
private fun EventsTab(vm: com.example.personalaibot.tools.trading.auto.AutoTradingViewModel) {
    val decisions by vm.lastDecisions.collectAsState()
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        // Engine status mini-bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelSurfaceHi)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.running) BuyGreen else NegativeRed),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.phase.name} · cycle #${state.cycleCount} · open=${state.openTradesTracked}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
        }

        if (decisions.isEmpty()) {
            item { EmptyState("No decisions yet — Start engine or Run one cycle") }
        } else {
            items(decisions) { d ->
                val accent = when (d.side) {
                    "BUY" -> BuyGreen
                    "SELL" -> SellRed
                    else -> Color.White.copy(alpha = 0.35f)
                }
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(d.symbol, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(d.side, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(d.strategy.thai, color = JarvisTheme.Cyan.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip("Confluence", "${d.confluence.roundToInt()}%", JarvisTheme.Cyan)
                            StatChip("RRR", "%.2f".format(d.rrr), if (d.rrr >= 1.5) BuyGreen else Color.White.copy(alpha = 0.6f))
                            StatChip("Regime", d.regime.name.replace('_', ' '), JarvisTheme.Purple)
                        }
                        if (d.entry != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Entry", formatPrice(d.entry), Color.White.copy(alpha = 0.7f))
                                if (d.sl != null) StatChip("SL", formatPrice(d.sl), SellRed.copy(alpha = 0.8f))
                                if (d.tp != null) StatChip("TP", formatPrice(d.tp), BuyGreen.copy(alpha = 0.8f))
                            }
                        }
                        val rationale = d.translatedTh ?: d.rationale
                        if (rationale.isNotBlank()) {
                            Text(
                                rationale,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (d.riskGate.isNotBlank() && d.side == "SKIP") {
                            Text(
                                "⛔ ${d.riskGate}",
                                color = NegativeRed.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                d.at,
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 9.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (d.executed) {
                                Text(
                                    "EXECUTED #${d.mt5Ticket}",
                                    color = BuyGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

// ─── Format helpers ──────────────────────────────────────────────────────────

private fun formatMoney(amount: Double, currency: String = ""): String {
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else  -> if (currency.isNotBlank()) "$currency " else ""
    }
    return if (amount >= 0)
        "$symbol${"%,.2f".format(amount)}"
    else
        "-$symbol${"%,.2f".format(-amount)}"
}

private fun formatSignedMoney(amount: Double, currency: String = ""): String {
    val abs = "%,.2f".format(kotlin.math.abs(amount))
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"
        else  -> if (currency.isNotBlank()) "$currency " else ""
    }
    return if (amount >= 0) "+$symbol$abs" else "-$symbol$abs"
}

private fun formatRelativeTs(ms: Long): String {
    val delta = nowMs() - ms
    return when {
        delta < 0          -> "just now"
        delta < 60_000     -> "${delta / 1_000}s ago"
        delta < 3_600_000  -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else               -> "${delta / 86_400_000}d ago"
    }
}

private fun formatVolume(volume: Double): String =
    if (volume == kotlin.math.floor(volume) && volume < 1000) "%.2f".format(volume)
    else "%.2f".format(volume)

private fun formatPrice(price: Double?): String {
    if (price == null || price == 0.0) return "—"
    return when {
        price >= 1_000 -> "%,.2f".format(price)
        price >= 1     -> "%.4f".format(price)
        else           -> "%.5f".format(price)
    }
}
