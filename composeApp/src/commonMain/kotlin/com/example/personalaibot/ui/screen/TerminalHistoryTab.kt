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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlin.math.roundToInt

// ─── History Tab ─────────────────────────────────────────────────────────────

@Composable
internal fun TerminalHistoryTab(deals: List<Mt5TradeItem>) {
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
