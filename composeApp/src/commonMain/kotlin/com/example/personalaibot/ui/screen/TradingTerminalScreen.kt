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
                Mt5Tab.Overview -> TerminalOverviewTab(
                    account = account,
                    positions = positions,
                    deals = exitDeals,
                    runningPnl = runningPnl,
                    lastSyncAt = lastSyncAt
                )
                Mt5Tab.Events -> {
                    if (autoTrading != null) {
                        TerminalEventsTab(vm = autoTrading)
                    } else {
                        EmptyState("Decision Feed ยังไม่พร้อม")
                    }
                }
                Mt5Tab.Trade -> TerminalTradeTab(
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
                Mt5Tab.History -> TerminalHistoryTab(deals = exitDeals)
                Mt5Tab.Settings -> TerminalSettingsTab(
                    defaultLot = defaultLot,
                    defaultTpPoints = defaultTpPoints,
                    defaultSlPoints = defaultSlPoints,
                    maxDdPercent = maxDdPercent,
                    onSetDefaultLot = onSetDefaultLot,
                    onSetDefaultTpPoints = onSetDefaultTpPoints,
                    onSetDefaultSlPoints = onSetDefaultSlPoints,
                    onSetMaxDdPercent = onSetMaxDdPercent
                )
                Mt5Tab.Connection -> TerminalConnectionTab(
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
            TopRibbonChip(label = "Positions", value = positionsCount.toString())
            Spacer(Modifier.width(6.dp))
            TopRibbonChip(
                label = "PnL",
                value = formatMoney(runningPnl, currency),
                valueColor = pnlColor
            )
        }
    }
}

@Composable
private fun TopRibbonChip(label: String, value: String, valueColor: Color = Color.White) {
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



