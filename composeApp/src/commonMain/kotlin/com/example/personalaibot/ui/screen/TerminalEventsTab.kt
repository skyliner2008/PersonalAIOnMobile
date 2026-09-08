package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import com.example.personalaibot.automation.SignalOutcomeTracker
import com.example.personalaibot.db.SignalTrackingRecord
import com.example.personalaibot.tools.trading.auto.AutoTradingViewModel
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlin.math.roundToInt

// ─── Events Tab (live decision feed) ─────────────────────────────────────────

@Composable
internal fun TerminalEventsTab(vm: AutoTradingViewModel) {
    val decisions by vm.lastDecisions.collectAsState()
    val state by vm.state.collectAsState()
    val recentOutcomes by produceState<List<SignalTrackingRecord>>(initialValue = emptyList()) {
        value = SignalOutcomeTracker.getRecentResolvedSignals(3)
    }

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

        if (recentOutcomes.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧠 Closed-Loop Signal Outcomes", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("Learning Active", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        recentOutcomes.forEach { rec ->
                            val isWin = rec.status == "WIN"
                            val badgeColor = if (isWin) BuyGreen else if (rec.status == "LOSS") SellRed else Color.White.copy(alpha = 0.5f)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${rec.symbol} ${rec.side} (${rec.strategy})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${rec.status} ${rec.pnl_r?.let { "%+.2fR".format(it) } ?: "-"} · MFE ${"%.1fR".format(rec.mfe)}",
                                    color = badgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
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
