package com.example.personalaibot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
                        activeToolName = activeToolName,
                        onToggleCamera = { viewModel.toggleCamera() },
                        onEndLive      = { viewModel.stopVoiceInput() }
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
                            JarvisWelcome()
                        }
                    }
                }

                // Typing indicator
                if (isTyping) {
                    TypingIndicator()
                }
            }
        }

        // Handle widget close from service
        LaunchedEffect(Unit) {
            registerWidgetClosed {
                viewModel.setFloatingWidgetEnabled(false)
            }
        }

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

        if (showToolList) {
            ToolListDialog(onDismiss = { showToolList = false })
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                containerColor = JarvisTheme.Card,
                title = { Text("ล้างประวัติการสนทนา?", color = JarvisTheme.Red, fontWeight = FontWeight.Bold) },
                text = { 
                    Column {
                        Text("ประวัติในหน้าจอจะหายไปทั้งหมด", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "💡 ความจำหลัก (Core Memory) จะยังอยู่ครบถ้วน แต่ความจำล่าสุดอาจจะยังไม่ได้ประมวลผล แนะนำให้กด 'จำศีล' ก่อนถ้าอยากให้ JARVIS จำเนื้อหาล่าสุดได้แม่นยำครับ",
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            viewModel.clearChat()
                            showClearConfirm = false 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Red)
                    ) {
                        Text("ล้างเลย", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("ยกเลิก", color = Color.White.copy(0.6f))
                    }
                }
            )
        }
    }
}
