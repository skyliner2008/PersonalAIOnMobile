package com.example.personalaibot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    registerToggleLive: (() -> Unit) -> Unit = {}
) {
    val viewModel: JarvisViewModel = viewModel {
        JarvisViewModel(databaseDriverFactory, voiceManager)
    }
    val messages       by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping       by viewModel.isTyping.collectAsStateWithLifecycle()
    val isListening    by viewModel.isListening.collectAsStateWithLifecycle()
    val isCameraActive by viewModel.isCameraActive.collectAsStateWithLifecycle()
    val voiceError     by viewModel.voiceError.collectAsStateWithLifecycle()
    val activeToolName by viewModel.activeToolName.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Sync widget listening state
    LaunchedEffect(isListening) {
        onSetWidgetListening(isListening)
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
            containerColor = JarvisTheme.Dark,
            topBar = {
                JarvisTopBar(
                    isListening  = isListening,
                    showSettings = { showSettings = true }
                )
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

                // Bottom input area
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
        }

        if (showSettings) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSettings = false },
                onStartWidget = onStartWidget,
                onStopWidget = onStopWidget
            )
        }
    }
}
