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
import com.example.personalaibot.ui.screen.ChatInputBar
import com.example.personalaibot.ui.screen.LiveModePanel
import com.example.personalaibot.ui.screen.SettingsDialog
import com.example.personalaibot.ui.screen.ToolListScreen
import com.example.personalaibot.ui.screen.TradingChartScreen
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
    fileToolHandler: (suspend (String, Map<String, String>) -> String)? = null
) {
    val viewModel: JarvisViewModel = viewModel {
        JarvisViewModel(databaseDriverFactory, voiceManager, fileToolHandler)
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isCameraActive by viewModel.isCameraActive.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isAiVisionRequested by viewModel.isAiVisionRequested.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val activeToolName by viewModel.activeToolName.collectAsStateWithLifecycle()
    val isWidgetEnabled by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showToolList by remember { mutableStateOf(false) }
    var showAutomation by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var chartSettingsSignal by remember { mutableLongStateOf(0L) }

    val showChart by viewModel.showChart.collectAsStateWithLifecycle()
    val chartSymbol by viewModel.chartSymbol.collectAsStateWithLifecycle()
    val chartInterval by viewModel.chartInterval.collectAsStateWithLifecycle()
    val chartLocale by viewModel.chartLocale.collectAsStateWithLifecycle()
    val chartHideSideToolbar by viewModel.chartHideSideToolbar.collectAsStateWithLifecycle()
    val chartRefreshToken by viewModel.chartRefreshToken.collectAsStateWithLifecycle()
    val jobs by viewModel.activeJobs.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentOverlayTitle = when {
        showToolList -> "Tool List"
        showAutomation -> "Cron Jobs"
        showSettings -> "Settings"
        showChart -> "TradingView"
        else -> null
    }
    val hasOverlayScreen = currentOverlayTitle != null

    fun closeOverlay() {
        when {
            showToolList -> showToolList = false
            showAutomation -> showAutomation = false
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
                            activeToolName = activeToolName,
                            onToggleCamera = { viewModel.toggleCamera() },
                            onSwitchCamera = { viewModel.switchCamera() },
                            onToggleMute = { viewModel.toggleMute() },
                            onEndLive = { viewModel.stopVoiceInput() },
                            onFrameCapture = { jpeg, raw -> viewModel.onCameraFrame(jpeg, raw) }
                        )
                    } else {
                        ChatInputBar(
                            onSend = { viewModel.sendMessage(it) },
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
                            allFilesAccessGranted = allFilesAccessGranted
                        )
                    }

                    showToolList -> {
                        ToolListScreen()
                    }

                    showAutomation -> {
                        AutomationScreen(
                            jobs = jobs,
                            onDelete = { viewModel.automationManager.deleteJob(it) },
                            onUpdateInterval = { id, interval -> viewModel.automationManager.updateInterval(id, interval) }
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
                                    MessageBubble(message)
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





