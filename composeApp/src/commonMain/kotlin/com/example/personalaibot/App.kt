package com.example.personalaibot

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.ui.components.ErrorBanner
import com.example.personalaibot.ui.components.MessageBubble
import com.example.personalaibot.ui.components.TypingIndicator
import com.example.personalaibot.ui.screen.AutomationScreen
import com.example.personalaibot.ui.screen.BacktestScreen
import com.example.personalaibot.ui.screen.ChatInputBar
import com.example.personalaibot.ui.screen.LiveModePanel
import com.example.personalaibot.ui.screen.SettingsDialog
import com.example.personalaibot.ui.screen.ToolListScreen
import com.example.personalaibot.ui.screen.TradingChartScreen
import com.example.personalaibot.ui.screen.TradingTerminalScreen
import com.example.personalaibot.ui.theme.JarvisTheme
import com.example.personalaibot.voice.VoiceManager
import kotlinx.coroutines.launch

@Composable
fun App(
    databaseDriverFactory: DatabaseDriverFactory,
    voiceManager: VoiceManager,
    onStartWidget: () -> Unit = {},
    onStopWidget: () -> Unit = {},
    onSetWidgetListening: (Boolean) -> Unit = {},
    registerToggleLive: (() -> Unit) -> Unit = {},
    registerWidgetClosed: (() -> Unit) -> Unit = {},
    requestAllFilesPermission: () -> Unit = {},
    allFilesAccessGranted: Boolean = false,
    setupChecks: List<com.example.personalaibot.ui.screen.SetupCheckItem> = emptyList(),
    fileToolHandler: (suspend (String, Map<String, String>) -> String)? = null,
    onDownloadLocalModel: (suspend (com.example.personalaibot.data.embedding.LocalOnnxEmbeddingProvider, (Float) -> Unit, Boolean) -> Unit)? = null
) {
    val viewModel: JarvisViewModel = viewModel {
        JarvisViewModel(databaseDriverFactory, voiceManager, fileToolHandler, onDownloadLocalModel)
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isCameraActive by viewModel.isCameraActive.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val liveConnectionState by viewModel.liveConnectionState.collectAsStateWithLifecycle()
    val isAiVisionRequested by viewModel.isAiVisionRequested.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val activeToolName by viewModel.activeToolName.collectAsStateWithLifecycle()
    val isWidgetEnabled by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showToolList by remember { mutableStateOf(false) }
    var showAutomation by remember { mutableStateOf(false) }
    var showTradingTerminal by remember { mutableStateOf(false) }
    var showBacktest by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var chartSettingsSignal by remember { mutableLongStateOf(0L) }
    val backtestRuns by com.example.personalaibot.automation.backtest.BacktestResultStore.runs.collectAsStateWithLifecycle()

    // มีผล backtest ใหม่เข้ามาระหว่างเปิดหน้าอื่นอยู่ → ไม่เด้ง แต่ถ้าผู้ใช้เปิดหน้า Backtest ไว้จะเห็นอัปเดตเอง (StateFlow)

    val showChart by viewModel.showChart.collectAsStateWithLifecycle()
    val chartSymbol by viewModel.chartSymbol.collectAsStateWithLifecycle()
    val chartInterval by viewModel.chartInterval.collectAsStateWithLifecycle()
    val chartLocale by viewModel.chartLocale.collectAsStateWithLifecycle()
    val chartHideSideToolbar by viewModel.chartHideSideToolbar.collectAsStateWithLifecycle()
    val chartRefreshToken by viewModel.chartRefreshToken.collectAsStateWithLifecycle()
    val chartViewMode by viewModel.chartViewMode.collectAsStateWithLifecycle()
    val chartLayout by viewModel.chartLayout.collectAsStateWithLifecycle()
    val chartOverlays by viewModel.chartOverlays.collectAsStateWithLifecycle()
    val chartCandles by viewModel.chartCandles.collectAsStateWithLifecycle()
    val chartSmcResult by viewModel.chartSmcResult.collectAsStateWithLifecycle()
    val chartSignalMarkers by viewModel.chartSignalMarkers.collectAsStateWithLifecycle()
    val chartCardCache by viewModel.chartCardCache.collectAsStateWithLifecycle()
    val chartDataLoading by viewModel.chartDataLoading.collectAsStateWithLifecycle()
    val jobs by viewModel.activeJobs.collectAsStateWithLifecycle()
    val scheduledTasks by viewModel.scheduledTasks.collectAsStateWithLifecycle()
    val alertTestRunning by viewModel.alertTestRunning.collectAsStateWithLifecycle()
    val alertTestStatus by viewModel.alertTestStatus.collectAsStateWithLifecycle()
    val alertTestResults by viewModel.alertTestResults.collectAsStateWithLifecycle()
    val alertAiSummary by viewModel.alertAiSummaryEnabled.collectAsStateWithLifecycle()
    val alertVoice by viewModel.alertVoiceEnabled.collectAsStateWithLifecycle()
    val alertVoiceEngine by viewModel.alertVoiceEngine.collectAsStateWithLifecycle()
    val mt5BridgeBaseUrl by viewModel.mt5BridgeBaseUrl.collectAsStateWithLifecycle()
    val mt5AuthToken by viewModel.mt5AuthToken.collectAsStateWithLifecycle()
    val mt5PairingStatus by viewModel.mt5PairingStatus.collectAsStateWithLifecycle()
    val mt5IsSyncing by viewModel.mt5IsSyncing.collectAsStateWithLifecycle()
    val mt5LastSyncAt by viewModel.mt5LastSyncAt.collectAsStateWithLifecycle()
    // 2026-04-30 (P6) — server reachability + first-paint cache loading flag
    val mt5ServerOnline by viewModel.mt5ServerOnline.collectAsStateWithLifecycle()
    val mt5CacheLoading by viewModel.mt5CacheLoading.collectAsStateWithLifecycle()
    val mt5Error by viewModel.mt5Error.collectAsStateWithLifecycle()
    val mt5ActionResult by viewModel.mt5ActionResult.collectAsStateWithLifecycle()
    val mt5Account by viewModel.mt5Account.collectAsStateWithLifecycle()
    val mt5Positions by viewModel.mt5Positions.collectAsStateWithLifecycle()
    val mt5Deals by viewModel.mt5Deals.collectAsStateWithLifecycle()
    val mt5Clients by viewModel.mt5Clients.collectAsStateWithLifecycle()
    val mt5ClientsLoading by viewModel.mt5ClientsLoading.collectAsStateWithLifecycle()
    val mt5SelectedClientExe by viewModel.mt5SelectedClientExe.collectAsStateWithLifecycle()
    val mt5TerminalFeed by viewModel.mt5TerminalFeed.collectAsStateWithLifecycle()
    val mt5DefaultLot by viewModel.mt5DefaultLot.collectAsStateWithLifecycle()
    val mt5DefaultTpPoints by viewModel.mt5DefaultTpPoints.collectAsStateWithLifecycle()
    val mt5DefaultSlPoints by viewModel.mt5DefaultSlPoints.collectAsStateWithLifecycle()
    val mt5MaxDdPercent by viewModel.mt5MaxDdPercent.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentOverlayTitle = when {
        showToolList -> "Tool List"
        showAutomation -> "Cron Jobs"
        showTradingTerminal -> "MT5 Terminal"
        showBacktest -> "Backtest Lab"
        showSettings -> "Settings"
        showChart -> "TradingView"
        else -> null
    }
    val hasOverlayScreen = currentOverlayTitle != null

    fun closeOverlay() {
        when {
            showToolList -> showToolList = false
            showAutomation -> showAutomation = false
            showTradingTerminal -> {
                showTradingTerminal = false
                viewModel.closeTradingTerminal()
            }
            showBacktest -> showBacktest = false
            showSettings -> showSettings = false
            showChart -> viewModel.closeChart()
        }
    }

    LaunchedEffect(isListening, isWidgetEnabled) {
        if (isWidgetEnabled) onSetWidgetListening(isListening)
    }

    LaunchedEffect(isWidgetEnabled) {
        if (isWidgetEnabled) onStartWidget() else onStopWidget()
    }

    LaunchedEffect(Unit) {
        registerToggleLive {
            if (viewModel.isListening.value) viewModel.stopVoiceInput() else viewModel.startVoiceInput()
        }
    }

    LaunchedEffect(messages.size, hasOverlayScreen) {
        if (!hasOverlayScreen && messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    MaterialTheme(colorScheme = JarvisTheme.ColorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisTheme.Dark,
            topBar = {
                Surface(color = JarvisTheme.Surface) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "J.A.R.V.I.S",
                                color = JarvisTheme.Cyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = 3.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!hasOverlayScreen) {
                                IconButton(onClick = { showClearConfirm = true }) {
                                    Icon(Icons.Default.DeleteSweep, "Clear Chat", tint = JarvisTheme.Red.copy(alpha = 0.8f))
                                }
                                IconButton(onClick = { showToolList = true }) {
                                    Icon(Icons.Default.Apps, "Tool List", tint = JarvisTheme.Cyan)
                                }
                                IconButton(onClick = { viewModel.openChart() }) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, "TradingView", tint = JarvisTheme.Cyan)
                                }
                                IconButton(onClick = { showAutomation = true }) {
                                    Icon(Icons.Default.NotificationsActive, "Cron Jobs", tint = JarvisTheme.Cyan)
                                }
                                IconButton(onClick = { showBacktest = true }) {
                                    Icon(Icons.Default.BarChart, "Backtest Lab", tint = JarvisTheme.Cyan)
                                }
                                IconButton(onClick = {
                                    showTradingTerminal = true
                                    viewModel.openTradingTerminal()
                                }) {
                                    Text("MT5", color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.75f))
                                }
                            } else {
                                Text(
                                    text = currentOverlayTitle.orEmpty(),
                                    color = JarvisTheme.Cyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (showChart) {
                                    IconButton(onClick = { chartSettingsSignal += 1L }) {
                                        Icon(Icons.Default.Settings, "Widget Settings", tint = Color.White)
                                    }
                                }
                                IconButton(onClick = { closeOverlay() }) {
                                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!hasOverlayScreen) {
                    if (isListening) {
                        LiveModePanel(
                            isCameraActive = isCameraActive,
                            isFrontCamera = isFrontCamera,
                            isMuted = isMuted,
                            isAiVisionRequested = isAiVisionRequested,
                            liveConnectionState = liveConnectionState,
                            activeToolName = activeToolName,
                            onToggleCamera = { viewModel.toggleCamera() },
                            onSwitchCamera = { viewModel.switchCamera() },
                            onToggleMute = { viewModel.toggleMute() },
                            onEndLive = { viewModel.stopVoiceInput() },
                            onFrameCapture = { jpeg, raw -> viewModel.onCameraFrame(jpeg, raw) }
                        )
                    } else {
                        ChatInputBar(
                            onSend = { msg, atts -> viewModel.sendMessage(msg, attachments = atts) },
                            onStartLive = { viewModel.startVoiceInput() },
                            enabled = !isTyping,
                            voiceAvailable = voiceManager.isAvailable()
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(JarvisTheme.Dark)
            ) {
                when {
                    showSettings -> {
                        SettingsDialog(
                            viewModel = viewModel,
                            onDismiss = { showSettings = false },
                            onStartWidget = onStartWidget,
                            onStopWidget = onStopWidget,
                            requestAllFilesPermission = requestAllFilesPermission,
                            allFilesAccessGranted = allFilesAccessGranted,
                            setupChecks = setupChecks
                        )
                    }

                    showToolList -> {
                        ToolListScreen()
                    }

                    showAutomation -> {
                        AutomationScreen(
                            jobs = jobs,
                            scheduledTasks = scheduledTasks,
                            alertAiSummary = alertAiSummary,
                            alertVoice = alertVoice,
                            onAlertAiSummaryChange = { viewModel.setAlertAiSummaryEnabled(it) },
                            onAlertVoiceChange = { viewModel.setAlertVoiceEnabled(it) },
                            alertVoiceEngine = alertVoiceEngine,
                            onAlertVoiceEngineChange = { viewModel.setAlertVoiceEngine(it) },
                            onDelete = { viewModel.automationManager.deleteJob(it) },
                            onDeleteTask = { viewModel.automationManager.deleteScheduledTask(it) },
                            onRepeatAlert = { viewModel.automationManager.resetTrigger(it) },
                            onUpdateInterval = { id, interval -> viewModel.automationManager.updateInterval(id, interval) },
                            onUpdateCondition = { id, cond -> viewModel.automationManager.updateCondition(id, cond) },
                            onRename = { id, name -> viewModel.automationManager.renameJob(id, name) },
                            alertTestRunning = alertTestRunning,
                            alertTestStatus = alertTestStatus,
                            alertTestResults = alertTestResults,
                            onRunTest = { viewModel.runAlertDataTest() },
                            onCreateAlert = { name, symbol, toolName, field, op, value, interval, delivery ->
                                viewModel.createAlert(name, symbol, toolName, field, op, value, interval, delivery)
                            },
                            onCreateScheduledTask = { name, prompt, type, runAt, hhmm ->
                                viewModel.createScheduledTask(name, prompt, type, runAt, hhmm)
                            }
                        )
                    }

                    showBacktest -> {
                        BacktestScreen(runs = backtestRuns)
                    }

                    showTradingTerminal -> {
                        TradingTerminalScreen(
                            bridgeBaseUrl = mt5BridgeBaseUrl,
                            pairingStatus = mt5PairingStatus,
                            isSyncing = mt5IsSyncing,
                            lastSyncAt = mt5LastSyncAt,
                            // 2026-04-30 (P6) — wire offline / cache state
                            serverOnline = mt5ServerOnline,
                            cacheLoading = mt5CacheLoading,
                            onRetrySync = { viewModel.refreshMt5Terminal() },
                            error = mt5Error,
                            actionResult = mt5ActionResult,
                            account = mt5Account,
                            positions = mt5Positions,
                            deals = mt5Deals,
                            clients = mt5Clients,
                            clientsLoading = mt5ClientsLoading,
                            selectedClientExe = mt5SelectedClientExe,
                            terminalFeed = mt5TerminalFeed,
                            defaultLot = mt5DefaultLot,
                            defaultTpPoints = mt5DefaultTpPoints,
                            defaultSlPoints = mt5DefaultSlPoints,
                            maxDdPercent = mt5MaxDdPercent,
                            onBridgeBaseUrlChange = { viewModel.updateMt5BridgeBaseUrl(it) },
                            onConnectToggle = { viewModel.toggleMt5Connection(it) },
                            onClearError = { viewModel.clearMt5Error() },
                            onClearActionResult = { viewModel.clearMt5ActionResult() },
                            onSetDefaultLot = { viewModel.setMt5DefaultLot(it) },
                            onSetDefaultTpPoints = { viewModel.setMt5DefaultTpPoints(it) },
                            onSetDefaultSlPoints = { viewModel.setMt5DefaultSlPoints(it) },
                            onSetMaxDdPercent = { viewModel.setMt5MaxDdPercent(it) },
                            onSelectClient = { viewModel.selectMt5ClientExe(it) },
                            onRefreshClients = { viewModel.refreshMt5Clients() },
                            onStartClient = { viewModel.startSelectedMt5Client() },
                            onStopClient = { viewModel.stopSelectedMt5Client() },
                            onPlaceOrder = { action, symbol, volume, sl, tp, comment ->
                                viewModel.placeMt5Order(action, symbol, volume, sl, tp, comment)
                            },
                            onClosePosition = { symbol, ticket ->
                                viewModel.closeMt5Position(symbol, ticket)
                            },
                            onCloseAll = { side -> viewModel.closeMt5AllPositions(side) },
                            onBreakEvenAll = { viewModel.setMt5BreakEvenAll() },
                            onEditPosition = { symbol, ticket, sl, tp ->
                                viewModel.modifyMt5Position(symbol, ticket, sl, tp)
                            },
                            autoTrading = viewModel.autoTrading
                        )
                    }

                    showChart -> {
                        TradingChartScreen(
                            symbol = chartSymbol,
                            interval = chartInterval,
                            locale = chartLocale,
                            hideSideToolbar = chartHideSideToolbar,
                            refreshToken = chartRefreshToken,
                            openSettingsSignal = chartSettingsSignal,
                            viewMode = chartViewMode,
                            layout = chartLayout,
                            overlays = chartOverlays,
                            candles = chartCandles,
                            smcResult = chartSmcResult,
                            signalMarkers = chartSignalMarkers,
                            dataLoading = chartDataLoading,
                            onSetViewMode = { viewModel.setChartViewMode(it) },
                            onSetLayout = { viewModel.setChartLayout(it) },
                            onToggleOverlay = { viewModel.toggleChartOverlay(it) },
                            onSetLocale = { viewModel.updateChartLocale(it) },
                            onSetHideSideToolbar = { viewModel.setChartHideSideToolbar(it) },
                            onClose = { viewModel.closeChart() }
                        )
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            voiceError?.let { error ->
                                ErrorBanner(error = error, onDismiss = { viewModel.clearVoiceError() })
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                reverseLayout = true,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(messages.asReversed()) { message ->
                                    MessageBubble(
                                        message = message,
                                        onOpenChart = { cfg -> viewModel.openChartWithConfig(cfg.symbol, cfg.interval, cfg.layout, cfg.overlays) },
                                        chartCardCache = chartCardCache,
                                        onChartCardShown = { cfg -> viewModel.ensureChartCardData(cfg.symbol, cfg.interval, needsSmc = "smc" in cfg.overlays, needsMarkers = "signals" in cfg.overlays) }
                                    )
                                }

                                if (messages.isEmpty()) {
                                    item {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            color = JarvisTheme.Card,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp)) {
                                                Text(
                                                    "Hello, I am JARVIS",
                                                    color = JarvisTheme.Cyan,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    "Your AI assistant is ready. Type a message to start.",
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isTyping) {
                                    item { TypingIndicator() }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear chat history?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("This action cannot be undone.", color = Color.White.copy(alpha = 0.75f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearChat()
                            showClearConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                    ) {
                        Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF1C1C2E),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}





