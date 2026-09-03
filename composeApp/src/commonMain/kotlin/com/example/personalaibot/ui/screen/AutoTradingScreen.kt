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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.auto.AutoTradingEngine
import com.example.personalaibot.tools.trading.auto.AutoTradingViewModel
import com.example.personalaibot.tools.trading.auto.QualityMetrics
import com.example.personalaibot.tools.trading.auto.StrategyType
import com.example.personalaibot.ui.theme.JarvisTheme

private val AutoPanel = Color(0xFF161C2B)
private val AutoPanelHi = Color(0xFF1E2638)
private val AutoOutline = Color(0xFF2A3247)
private val AutoGreen = Color(0xFF00E676)
private val AutoRed = Color(0xFFFF5252)
private val AutoYellow = Color(0xFFFFB74D)

/**
 * Auto-Trading tab — renders engine state + settings for AutoTradingEngine.
 */
@Composable
fun AutoTradingPanel(vm: AutoTradingViewModel) {
    val cfg by vm.config.collectAsState()
    val state by vm.state.collectAsState()
    val decisions by vm.lastDecisions.collectAsState()
    val learn by vm.learnSummary.collectAsState()
    val open by vm.openJournal.collectAsState()
    val quality by vm.qualityMetrics.collectAsState()
    val mt5Account by vm.mt5Account.collectAsState()
    val accountStatus by vm.accountStatus.collectAsState()

    // P4.1 — seed watchlist from broker's real gold symbol on first launch
    LaunchedEffect(Unit) {
        if (cfg.watchlist.isEmpty()) {
            val brokerSymbols = com.example.personalaibot.tools.trading.Mt5LocalCache.loadSymbols()
                .map { it.symbol }
            val gold = com.example.personalaibot.tools.trading.Mt5LocalCache.detectGoldSymbol(brokerSymbols)
            if (gold != null) {
                vm.addToWatchlist(gold)
            } else if (brokerSymbols.isNotEmpty()) {
                // Fallback: seed with first metal or first available symbol
                val seed = brokerSymbols.firstOrNull { it.uppercase().startsWith("XAU") }
                    ?: brokerSymbols.first()
                vm.addToWatchlist(seed)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item { EngineHeroPanel(state, cfg, onStart = vm::start, onStop = vm::stop, onRunNow = vm::runOnceNow, onLearn = vm::learnNow) }
        item { CycleQualityCard(quality) }
        item {
            ExecutionAccountStatusCard(
                liveMode = cfg.enableLiveTrading,
                engineRunning = state.running,
                accountStatus = accountStatus,
                account = mt5Account,
                lastRiskGate = decisions.lastOrNull()?.riskGate,
            )
        }
        item { TradingExecutionModeCard(cfg.enableLiveTrading, state.running, vm::updateEnableLive) }
        item { AiModeToggleCard(cfg.enableAiMode, vm::updateEnableAiMode) }  // V23.0
        item { SectionTitle("Watchlist") }
        item { WatchlistEditor(cfg.watchlist, vm::addToWatchlist, vm::removeFromWatchlist) }
        item { SectionTitle("Strategy preferences") }
        item { StrategyChipRow(cfg.preferredStrategies, vm::togglePreferredStrategy) }
        // P4.2 — 3-tier collapsible advanced settings (replaces 3 separate always-open sections)
        item { AdvancedSettingsPanel(cfg, vm) }
        item { SectionTitle("Recent decisions (last cycle)") }
        item { DecisionList(decisions) }
        item { SectionTitle("Open paper/live trades") }
        item { OpenList(open) }
        item { SectionTitle("Learn — summary & auto-tuning") }
        item { LearnCard(learn) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────
@Composable
private fun EngineHeroPanel(
    state: AutoTradingEngine.EngineState,
    cfg: AutoTradingEngine.Config,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRunNow: () -> Unit,
    onLearn: () -> Unit
) {
    val dot = when {
        state.running && state.phase == AutoTradingEngine.Phase.IDLE -> AutoGreen
        state.running -> JarvisTheme.Cyan
        else -> AutoRed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AutoPanel)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AUTO-TRADING ENGINE", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(
                    if (state.running) "${state.phase.name} · cycles=${state.cycleCount} · open=${state.openTradesTracked}"
                    else "STOPPED",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                )
            }
            if (state.running) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoRed.copy(alpha = 0.2f), contentColor = AutoRed)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoGreen.copy(alpha = 0.2f), contentColor = AutoGreen)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start", fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.message.isNotEmpty()) {
            Text(
                state.message,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRunNow,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan.copy(alpha = 0.18f), contentColor = JarvisTheme.Cyan)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run one cycle", fontSize = 11.sp)
            }
            Button(
                onClick = onLearn,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AutoYellow.copy(alpha = 0.18f), contentColor = AutoYellow)
            ) {
                Text("Learn now", fontSize = 11.sp)
            }
        }
    }
}


/** P6.3 — single source of truth for what the mobile app believes is executable. */
@Composable
private fun ExecutionAccountStatusCard(
    liveMode: Boolean,
    engineRunning: Boolean,
    accountStatus: String,
    account: com.example.personalaibot.tools.trading.Mt5AccountInfo?,
    lastRiskGate: String?,
) {
    val modeLabel = if (liveMode) "MT5 LIVE" else "DEMO / PAPER"
    val modeIcon = if (liveMode) "🔴" else "🟢"
    val connectionLabel = when {
        !liveMode -> "LOCAL DEMO"
        accountStatus == "CONNECTED" -> "MT5 CONNECTED"
        else -> "MT5 UNAVAILABLE"
    }
    val connectionColor = when {
        !liveMode -> AutoGreen
        accountStatus == "CONNECTED" -> AutoGreen
        else -> AutoRed
    }
    val marginLevel = account?.let { if (it.margin > 0.0) it.equity / it.margin * 100.0 else null }
    val riskLabel = when {
        lastRiskGate.isNullOrBlank() -> "—"
        lastRiskGate.equals("PASS", true) || lastRiskGate.contains("PASS", true) -> "PASS"
        else -> lastRiskGate.take(32)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AutoPanel)
            .border(1.dp, AutoOutline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Execution & Account Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(modeIcon + " " + modeLabel, color = if (liveMode) AutoRed else AutoGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(connectionColor))
            Spacer(Modifier.width(7.dp))
            Text(connectionLabel, color = connectionColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(if (engineRunning) "ENGINE RUNNING" else "ENGINE STOPPED", color = if (engineRunning) AutoYellow else Color.LightGray, fontSize = 11.sp)
        }
        if (liveMode && account != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountMetric("Balance", "%.2f %s".format(account.balance, account.currency), Modifier.weight(1f))
                AccountMetric("Equity", "%.2f %s".format(account.equity, account.currency), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountMetric("Free Margin", "%.2f".format(account.freeMargin), Modifier.weight(1f))
                AccountMetric("Margin", "%.2f".format(account.margin), Modifier.weight(1f))
                AccountMetric("Margin Level", marginLevel?.let { "%.1f%%".format(it) } ?: "—", Modifier.weight(1f))
            }
            Text(
                "Account ${account.login.ifBlank { "—" }}  •  ${account.server.ifBlank { "server —" }}  •  1:${account.leverage}",
                color = Color.LightGray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else if (!liveMode) {
            Text("Demo execution is local and uses the P6 broker-aware Risk Engine; MT5 account values are not used for Demo execution.", color = Color.LightGray, fontSize = 11.sp)
        } else {
            Text("MT5 account telemetry unavailable — LIVE execution status is not assumed from the UI toggle.", color = AutoRed, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Risk Gate", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text(riskLabel, color = if (riskLabel == "PASS") AutoGreen else AutoYellow, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AccountMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

// ─── Trading execution mode ──────────────────────────────────────────────────
@Composable
private fun TradingExecutionModeCard(enableLive: Boolean, engineRunning: Boolean, onChange: (Boolean) -> Unit) {
    var confirmLive by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (enableLive) AutoRed.copy(alpha = 0.1f) else AutoPanel).border(1.dp, if (enableLive) AutoRed else AutoGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TRADING EXECUTION MODE", color = JarvisTheme.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (enableLive) "MT5 LIVE" else "DEMO / PAPER", color = if (enableLive) AutoRed else AutoGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Switch(checked = enableLive, enabled = !engineRunning, onCheckedChange = { next -> if (next) confirmLive = true else onChange(false) }, colors = SwitchDefaults.colors(checkedThumbColor = AutoRed, checkedTrackColor = AutoRed.copy(alpha = 0.4f), uncheckedThumbColor = AutoGreen, uncheckedTrackColor = AutoGreen.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.height(6.dp))
        Text(when { engineRunning -> "หยุด Auto-Trading ก่อนจึงจะสลับบัญชี/โหมดได้"; enableLive -> "คำสั่งสามารถส่งไปยัง MT5 จริงได้ — Risk Gate และ live_execution_enabled ยังเป็นเงื่อนไขบังคับ"; else -> "โหมดปลอดภัย: วิเคราะห์และจำลองคำสั่งโดยไม่ส่งออเดอร์จริง" }, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEMO", color = if (!enableLive) AutoGreen else Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (!enableLive) AutoGreen.copy(alpha = 0.12f) else AutoPanelHi).padding(horizontal = 10.dp, vertical = 6.dp))
            Text("MT5 LIVE", color = if (enableLive) AutoRed else Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (enableLive) AutoRed.copy(alpha = 0.12f) else AutoPanelHi).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
    if (confirmLive) {
        AlertDialog(onDismissRequest = { confirmLive = false }, title = { Text("เปิด MT5 LIVE Trading?") }, text = { Text("โหมดนี้อนุญาตให้ Auto-Trading ส่งคำสั่งไปยัง MT5 จริงได้เมื่อผ่านเงื่อนไข live execution และ Risk Gate ทั้งหมด\n\nหากต้องการทดสอบระบบ ให้ใช้ DEMO / PAPER เป็นค่าเริ่มต้น") }, confirmButton = { Button(onClick = { confirmLive = false; onChange(true) }, colors = ButtonDefaults.buttonColors(containerColor = AutoRed)) { Text("ยืนยัน MT5 LIVE") } }, dismissButton = { Button(onClick = { confirmLive = false }) { Text("ยกเลิก") } })
    }
}

// ─── V23.0 — AI Mode Toggle ──────────────────────────────────────────────────
@Composable
private fun AiModeToggleCard(enableAiMode: Boolean, onChange: (Boolean) -> Unit) {
    val accent = if (enableAiMode) Color(0xFF4CAF50) else Color(0xFFFF9800)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (enableAiMode) "AI SUPERVISOR — ENABLED" else "EA-ONLY MODE (ไม่ใช้ AI)",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                if (enableAiMode) "AI วิเคราะห์และอนุมัติ signal ก่อนส่งออเดอร์ทุกครั้ง"
                else "Deterministic EA ทำงานอัตโนมัติ ไม่เรียก AI — ประหยัด token 100%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
        Switch(
            checked = enableAiMode,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.4f),
                uncheckedThumbColor = Color(0xFFFF9800),
                uncheckedTrackColor = Color(0xFFFF9800).copy(alpha = 0.4f)
            )
        )
    }
}

// ─── Watchlist ───────────────────────────────────────────────────────────────
@Composable
private fun WatchlistEditor(
    watchlist: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val brokerId = remember {
            // Best-effort: derive brokerId synchronously from local cache; falls back to "default"
            runCatching {
                com.example.personalaibot.tools.trading.Mt5LocalCache.brokerIdFor(null)
            }.getOrDefault("default")
        }
        SymbolPickerDialog(
            brokerId = brokerId,
            onSymbolSelected = { sym ->
                onAdd(sym)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase() },
                label = { Text("ADD SYMBOL", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                colors = autoFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            // Type-in add button
            Button(
                onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            // Browse broker catalogue button
            Button(
                onClick = { showPicker = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisTheme.Cyan.copy(alpha = 0.15f),
                    contentColor = JarvisTheme.Cyan,
                )
            ) {
                Text("Browse", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (watchlist.isEmpty()) {
            Text(
                "Watchlist is empty — type a symbol above or Browse broker catalogue",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(watchlist) { sym ->
                    SymbolPill(sym, removable = true) { onRemove(sym) }
                }
            }
        }
    }
}

// ─── Strategy chips ──────────────────────────────────────────────────────────
@Composable
private fun StrategyChipRow(preferred: Set<StrategyType>, onToggle: (StrategyType) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(StrategyType.entries.toList()) { type ->
            val on = type in preferred
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) JarvisTheme.Cyan.copy(alpha = 0.18f) else AutoPanelHi)
                    .border(1.dp, if (on) JarvisTheme.Cyan else AutoOutline, RoundedCornerShape(16.dp))
                    .clickable { onToggle(type) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    type.thai,
                    color = if (on) JarvisTheme.Cyan else Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Thresholds ──────────────────────────────────────────────────────────────
@Composable
private fun ThresholdSection(cfg: AutoTradingEngine.Config, vm: AutoTradingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DoubleField("Min Confluence (0..100)", cfg.minConfluence, vm::updateMinConfluence)
        DoubleField("Min R:R (reward/risk)", cfg.minRRR, vm::updateMinRRR)
        DoubleField("Risk per trade (% equity)", cfg.risk.riskPerTradePct, vm::updateRiskPerTrade)
        IntField("Max open positions", cfg.risk.maxOpenPositions, vm::updateMaxOpenPositions)
        DoubleField("Min Lot Size", cfg.risk.minLot, vm::updateMinLot)
        DoubleField("Max Lot Size", cfg.risk.maxLot, vm::updateMaxLot)
        DoubleField("Max Drawdown (%)", cfg.risk.maxDrawdownPct, vm::updateMaxDrawdown)
        DoubleField("Max Daily Loss (%)", cfg.risk.maxDailyLossPct, vm::updateMaxDailyLoss)
    }
}

// ─── Manage (BE / Trail) ─────────────────────────────────────────────────────
@Composable
private fun ManageSection(cfg: AutoTradingEngine.Config, vm: AutoTradingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DoubleField("Move SL to BE after R≥", cfg.breakEvenTriggerR, vm::updateBreakEvenR)
        DoubleField("Start trailing after R≥", cfg.trailAfterR, vm::updateTrailAfterR)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-tune threshold (Learn phase)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = cfg.autoTune, onCheckedChange = vm::updateAutoTune)
        }
    }
}

// ─── AI Intelligence ────────────────────────────────────────────────────────
@Composable
private fun AiIntelligenceSection(cfg: AutoTradingEngine.Config, vm: AutoTradingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Model Selection
        Column {
            Text("AI REASONING MODEL", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))

            // Model options: (modelId, label, badge)
            val modelOptions = listOf(
                Triple("gemini-2.5-flash-lite", "2.5 Flash Lite", "FAST"),
                Triple("gemini-2.5-flash",      "2.5 Flash",      "SMART"),
                Triple("gemini-2.5-pro",        "2.5 Pro",        "BEST"),
            )

            // Warn if current model is deprecated
            val isDeprecated = cfg.aiModel.isNotBlank() &&
                modelOptions.none { it.first == cfg.aiModel }
            if (isDeprecated) {
                Text(
                    "⚠ Model \"${cfg.aiModel}\" ถูก deprecated — เลือก model ใหม่",
                    color = Color(0xFFFFB300),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(4.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modelOptions.forEach { (modelId, label, badge) ->
                    val sel = cfg.aiModel == modelId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) JarvisTheme.Cyan.copy(alpha = 0.12f) else AutoPanelHi)
                            .border(1.dp, if (sel) JarvisTheme.Cyan else AutoOutline, RoundedCornerShape(10.dp))
                            .clickable { vm.updateAiModel(modelId) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                label,
                                color = if (sel) JarvisTheme.Cyan else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                modelId,
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 9.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (badge) {
                                        "FAST" -> Color(0xFF00C853).copy(alpha = 0.2f)
                                        "SMART" -> Color(0xFF2979FF).copy(alpha = 0.2f)
                                        else   -> Color(0xFFAA00FF).copy(alpha = 0.2f)
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                badge,
                                color = when (badge) {
                                    "FAST"  -> Color(0xFF69F0AE)
                                    "SMART" -> Color(0xFF82B1FF)
                                    else    -> Color(0xFFEA80FC)
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        PasswordField("Gemini API Key", cfg.apiKey, vm::updateApiKey)
        
        // Custom Prompt
        Column {
            Text("AGENT SYSTEM PROMPT (พฤติกรรม AI)", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
            OutlinedTextField(
                value = cfg.agentPrompt,
                onValueChange = vm::updateAgentPrompt,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                colors = autoFieldColors(),
                placeholder = { Text("เช่น 'เน้นเทรด Scalping ตามแนวโน้มหลักเท่านั้น'", color = Color.White.copy(0.3f), fontSize = 11.sp) }
            )
        }

        // Smart Free Fallback
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AutoPanelHi)
                .clickable { vm.updatePreferFreeOnly(!cfg.preferFreeOnly) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Free Fallback", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("หากโมเดลฟรีขัดข้อง ระบบจะสลับไปใช้ตัวอื่นทันที", color = Color.White.copy(alpha=0.5f), fontSize = 10.sp)
            }
            Switch(
                checked = cfg.preferFreeOnly,
                onCheckedChange = vm::updatePreferFreeOnly,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisTheme.Cyan,
                    checkedTrackColor = JarvisTheme.Cyan.copy(alpha = 0.4f)
                ),
                modifier = Modifier.scale(0.8f)
            )
        }

        HorizontalDivider(color = AutoOutline, thickness = 1.dp)

        Text("PROACTIVE NOTIFICATIONS (AI วิเคราะห์และส่งเอง)", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        
        NotificationSwitch("แจ้งเตือนเมื่อวิเคราะห์แผนเทรดเสร็จ", cfg.notificationSettings.onAnalysis) { vm.updateNotificationSettings(onAnalysis = it) }
        NotificationSwitch("แจ้งเตือนมือถือก่อนเปิดออเดอร์", cfg.notificationSettings.onOrder) { vm.updateNotificationSettings(onOrder = it) }
        NotificationSwitch("แจ้งเตือนเมื่อออเดอร์ติดลบมาก (แก้ไม้)", cfg.notificationSettings.onLoss) { vm.updateNotificationSettings(onLoss = it) }
        NotificationSwitch("สรุปผลเมื่อออเดอร์ปิด", cfg.notificationSettings.onClose) { vm.updateNotificationSettings(onClose = it) }
    }
}

@Composable
private fun NotificationSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisTheme.Cyan,
                checkedTrackColor = JarvisTheme.Cyan.copy(alpha = 0.4f)
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

// ─── Blacklist ───────────────────────────────────────────────────────────────
@Composable
private fun BlacklistEditor(
    blacklist: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase() },
                label = { Text("BLACKLIST SYMBOL", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                colors = autoFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = AutoRed.copy(alpha = 0.4f), contentColor = Color.White)
            ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
        }
        if (blacklist.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(blacklist.toList()) { sym -> SymbolPill(sym, removable = true, accent = AutoRed) { onRemove(sym) } }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text("(ไม่มี symbol ถูก blacklist)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

// ─── Advanced Settings (3-tier collapsible) ──────────────────────────────────
/**
 * P4.2 — 3-tier collapsible panel that replaces three separate always-open
 * sections (Thresholds & Risk / Breakeven & Trailing / AI Agent & Memory).
 *
 * Tiers:
 *   Tier 1 (Basic)        — Thresholds & Risk  [unlocked by default]
 *   Tier 2 (Intermediate) — Breakeven & Trailing
 *   Tier 3 (Expert)       — AI Agent & Memory (4-layer)
 *
 * Each tier is hidden until the user expands it, reducing visual clutter for
 * casual users while keeping full power accessible.
 */
@Composable
private fun AdvancedSettingsPanel(cfg: AutoTradingEngine.Config, vm: AutoTradingViewModel) {
    var tier1Open by remember { mutableStateOf(false) }
    var tier2Open by remember { mutableStateOf(false) }
    var tier3Open by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "⚙  Advanced Settings",
            color = AutoYellow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))

        // ── Tier 1: Thresholds & Risk ─────────────────────────────────────────
        DisclosureTier(
            label = "Thresholds & Risk",
            badge = "BASIC",
            badgeColor = JarvisTheme.Cyan,
            expanded = tier1Open,
            onToggle = { tier1Open = !tier1Open },
        ) {
            ThresholdSection(cfg, vm)
        }

        HorizontalDivider(color = AutoOutline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

        // ── Tier 2: Breakeven & Trailing ──────────────────────────────────────
        DisclosureTier(
            label = "Breakeven & Trailing",
            badge = "INTERMEDIATE",
            badgeColor = AutoYellow,
            expanded = tier2Open,
            onToggle = { tier2Open = !tier2Open },
        ) {
            ManageSection(cfg, vm)
        }

        HorizontalDivider(color = AutoOutline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

        // ── Tier 3: AI Agent & Memory ─────────────────────────────────────────
        DisclosureTier(
            label = "AI Agent & Memory",
            badge = "EXPERT",
            badgeColor = AutoRed,
            expanded = tier3Open,
            onToggle = { tier3Open = !tier3Open },
        ) {
            AiIntelligenceSection(cfg, vm)
        }
    }
}

@Composable
private fun DisclosureTier(
    label: String,
    badge: String,
    badgeColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Badge pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(badge, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                if (expanded) "▲" else "▼",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

// ─── Shared helpers ──────────────────────────────────────────────────────────
@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AutoPanelHi)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
    }
}

// ─── Decisions ───────────────────────────────────────────────────────────────
@Composable
private fun DecisionList(decisions: List<AutoTradingEngine.CycleDecision>) {
    if (decisions.isEmpty()) {
        EmptyCard("ยังไม่มี decision — กด Run one cycle เพื่อเริ่ม")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        decisions.forEach { d ->
            val accent = when (d.side) {
                "BUY" -> AutoGreen
                "SELL" -> AutoRed
                else -> Color.White.copy(alpha = 0.5f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AutoPanel)
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${d.side} ${d.symbol}",
                        color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(d.strategy.thai, color = JarvisTheme.Cyan, fontSize = 10.sp)
                }
                Text(
                    "regime=${d.regime.thai} · conf=${d.confluence} · rrr=${d.rrr} · ${if (d.executed) "EXECUTED" else "—"}${d.mt5Ticket?.let { " #$it" } ?: ""}",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp
                )
                Text(
                    d.rationale, color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "risk: ${d.riskGate}", color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OpenList(rows: List<com.example.personalaibot.tools.trading.auto.TradeJournalStore.JournalRow>) {
    if (rows.isEmpty()) {
        EmptyCard("ยังไม่มีไม้เปิด")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            val accent = if (r.side == "BUY") AutoGreen else AutoRed
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AutoPanel)
                    .padding(10.dp)
            ) {
                Row {
                    Text(
                        "${r.side} ${r.symbol} vol=${r.volume ?: "-"}",
                        color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(r.strategy, color = JarvisTheme.Cyan, fontSize = 10.sp)
                }
                Text(
                    "entry=${r.entry ?: "-"} sl=${r.sl ?: "-"} tp=${r.tp ?: "-"} rrr=${r.rrr ?: "-"}",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
                )
                Text(
                    "analyzers: ${r.analyzersUsed}",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Learn card ──────────────────────────────────────────────────────────────
@Composable
private fun LearnCard(learn: AutoTradingEngine.LearnSummary?) {
    if (learn == null) {
        EmptyCard("ยังไม่ได้ Learn — กด Learn now เพื่อสรุปจาก journal")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AutoPanel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Total ${learn.totalDeals} · WR ${(learn.winRate * 100).toInt()}% · Profit ${learn.totalProfit}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        learn.bestStrategy?.let {
            Text("best strategy: $it", color = AutoGreen, fontSize = 11.sp)
        }
        learn.worstStrategy?.let {
            Text("worst strategy: $it", color = AutoRed, fontSize = 11.sp)
        }
        learn.bestAnalyzerCombo?.let {
            Text("best analyzer combo: $it", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
        }
        Text(learn.aiNote, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        if (learn.strategyStats.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            learn.strategyStats.forEach { s ->
                Text(
                    "• ${s.strategy}: ${s.total} trade, WR=${(s.winRate * 100).toInt()}%, profit=${s.totalProfit}, avgR=${s.avgR}",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp
                )
            }
        }
    }
}

// ─── Shared widgets ──────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(label: String) {
    Text(
        label,
        color = JarvisTheme.Cyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SymbolPill(
    symbol: String,
    removable: Boolean = false,
    accent: Color = JarvisTheme.Cyan,
    onRemove: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(start = 10.dp, end = if (removable) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(symbol, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        if (removable) {
            IconButton(onClick = onRemove, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun DoubleField(label: String, value: Double, onSet: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                new.toDoubleOrNull()?.let(onSet)
            },
            label = { Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            colors = autoFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IntField(label: String, value: Int, onSet: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toIntOrNull()?.let(onSet)
        },
        label = { Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        colors = autoFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(label: String, value: String, onSet: (String) -> Unit) {
    var hidden by remember { mutableStateOf(true) }
    OutlinedTextField(
        value = value,
        onValueChange = onSet,
        label = { Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp) },
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(
                    if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        },
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        colors = autoFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun autoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = JarvisTheme.Cyan,
    unfocusedBorderColor = AutoOutline,
    cursorColor          = JarvisTheme.Cyan,
    focusedLabelColor    = JarvisTheme.Cyan,
    unfocusedLabelColor  = Color.White.copy(alpha = 0.4f),
)


// ─── P4.2 — Cycle Quality Card (Smart-Upgrade observability) ────────────────
@Composable
private fun CycleQualityCard(q: QualityMetrics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AutoPanelHi)
            .border(1.dp, AutoOutline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cycle Quality",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (q.generatedAt.isNotBlank()) q.generatedAt.take(19).replace("T", " ") else "no data yet",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        // Top counters row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QualityChip("Zone blocks", q.zoneGateBlocks.toString(), AutoYellow, Modifier.weight(1f))
            QualityChip("SL buffered", q.slBufferActivations.toString(), AutoGreen, Modifier.weight(1f))
            QualityChip("CT blocks", q.counterTrendBlocks.toString(), AutoYellow, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QualityChip("Mgmt events", q.managementEvents.toString(), Color(0xFF80D8FF), Modifier.weight(1f))
            QualityChip("Trades", q.tradesPlaced.toString(), AutoGreen, Modifier.weight(1f))
            QualityChip("Rejected", q.tradesRejected.toString(), AutoRed, Modifier.weight(1f))
        }

        // Distribution mini-bars
        if (q.entryZoneDistribution.isNotEmpty()) {
            HorizontalDivider(color = AutoOutline)
            Text(
                "Entry zone distribution",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            DistributionRow(q.entryZoneDistribution)
        }
        if (q.mgmtByEvent.isNotEmpty()) {
            HorizontalDivider(color = AutoOutline)
            Text(
                "Management events",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            DistributionRow(q.mgmtByEvent)
        }
    }
}

@Composable
private fun QualityChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AutoPanel)
            .border(1.dp, AutoOutline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DistributionRow(map: Map<String, Int>) {
    val total = map.values.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((k, v) in map.entries.sortedByDescending { it.value }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    k,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.width(110.dp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(v.toFloat() / total.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AutoGreen.copy(alpha = 0.85f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    v.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}


