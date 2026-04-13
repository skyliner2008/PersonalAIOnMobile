package com.example.personalaibot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.personalaibot.camera.CameraMode
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.ui.screen.*
import com.example.personalaibot.ui.theme.JarvisTheme
import com.example.personalaibot.voice.VoiceManager
import kotlinx.coroutines.launch

// ─── App Entry Point ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    val messages       by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping       by viewModel.isTyping.collectAsStateWithLifecycle()
    val isListening    by viewModel.isListening.collectAsStateWithLifecycle()
    val isCameraActive by viewModel.isCameraActive.collectAsStateWithLifecycle()
    val isFrontCamera  by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isMuted        by viewModel.isMuted.collectAsStateWithLifecycle()
    val isAiVisionRequested by viewModel.isAiVisionRequested.collectAsStateWithLifecycle()
    val voiceError     by viewModel.voiceError.collectAsStateWithLifecycle()
    val activeToolName by viewModel.activeToolName.collectAsStateWithLifecycle()
    val isWidgetEnabled by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()

    var showSettings  by remember { mutableStateOf(false) }
    var showToolList  by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Sync widget listening state
    LaunchedEffect(isListening, isWidgetEnabled) {
        if (isWidgetEnabled) {
            onSetWidgetListening(isListening)
        }
    }

    // Sync widget service with persistent setting
    LaunchedEffect(isWidgetEnabled) {
        if (isWidgetEnabled) {
            onStartWidget()
        } else {
            onStopWidget()
        }
    }

    LaunchedEffect(Unit) {
        registerToggleLive {
            if (viewModel.isListening.value) {
                viewModel.stopVoiceInput()
            } else {
                viewModel.startVoiceInput()
            }
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    MaterialTheme(colorScheme = JarvisTheme.ColorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisTheme.Dark,
            topBar = {
                JarvisTopBar(
                    isListening  = isListening,
                    showSettings = { showSettings = true },
                    showTools    = { showToolList  = true },
                    onClearChat  = { showClearConfirm = true }
                )
            },
            bottomBar = {
                if (isListening) {
                    LiveModePanel(
                        isCameraActive = isCameraActive,
                        isFrontCamera  = isFrontCamera,
                        isMuted        = isMuted,
                        isAiVisionRequested = isAiVisionRequested,
                        activeToolName = activeToolName,
                        onToggleCamera = { viewModel.toggleCamera() },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onToggleMute   = { viewModel.toggleMute() },
                        onEndLive      = { viewModel.stopVoiceInput() },
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
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(JarvisTheme.Dark)
            ) {
                // Error banner
                voiceError?.let { error ->
                    ErrorBanner(error = error, onDismiss = { viewModel.clearVoiceError() })
                }

                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages.asReversed()) { message ->
                        MessageBubble(message)
                    }

                    // Welcome message
                    if (messages.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                color = JarvisTheme.Card,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        "สวัสดีครับ ผม JARVIS",
                                        color = JarvisTheme.Cyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "ผู้ช่วย AI ส่วนตัวของคุณพร้อมให้บริการแล้ว\nพิมพ์ข้อความหรือกดไมค์เพื่อเริ่มสนทนา",
                                        color = Color.White.copy(0.6f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Typing indicator
                    if (isTyping) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }
        }

        // ─── Settings Dialog ──────────────────────────────────────
        if (showSettings) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettings = false },
                onStartWidget = onStartWidget,
                onStopWidget = onStopWidget,
                requestAllFilesPermission = requestAllFilesPermission,
                allFilesAccessGranted = allFilesAccessGranted
            )
        }

        // ─── Tool List Dialog ─────────────────────────────────────
        if (showToolList) {
            ToolListDialog(onDismiss = { showToolList = false })
        }

        // ─── Clear Chat Confirmation ──────────────────────────────
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("ลบประวัติการสนทนา?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("ข้อความทั้งหมดจะถูกลบ ไม่สามารถกู้คืนได้", color = Color.White.copy(0.7f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearChat()
                            showClearConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                    ) {
                        Text("ลบ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("ยกเลิก", color = Color.White.copy(0.7f))
                    }
                },
                containerColor = Color(0xFF1C1C2E),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // ─── Camera Screen - REMOVED (Merged into LiveModePanel) ─────
    }
}