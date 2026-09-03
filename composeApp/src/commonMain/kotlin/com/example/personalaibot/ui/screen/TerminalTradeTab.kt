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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.BrokerSymbolCache
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.ui.theme.JarvisTheme

private val DefaultQuickSymbols = listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD", "US30", "NAS100", "XAGUSD")

// ─── Trade Tab ───────────────────────────────────────────────────────────────

@Composable
internal fun TerminalTradeTab(
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
    var quickSymbols by remember(brokerId) { mutableStateOf(DefaultQuickSymbols) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(brokerId) {
        val favs = BrokerSymbolCache.favoritesFirst(brokerId)
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
