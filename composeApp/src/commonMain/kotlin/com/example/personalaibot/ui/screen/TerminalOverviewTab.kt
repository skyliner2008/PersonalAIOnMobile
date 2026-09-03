package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlin.math.roundToInt

// ─── Overview Tab ────────────────────────────────────────────────────────────

@Composable
internal fun TerminalOverviewTab(
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
