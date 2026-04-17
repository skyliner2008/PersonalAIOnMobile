package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.components.LivePulse
import com.example.personalaibot.ui.theme.JarvisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisTopBar(
    isListening: Boolean,
    showSettings: () -> Unit,
    showTools: () -> Unit = {},
    showAutomation: () -> Unit = {},
    showChart: () -> Unit = {},
    onClearChat: () -> Unit = {},
    showActions: Boolean = true
) {
    Surface(color = JarvisTheme.Surface) {
        Column {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "J.A.R.V.I.S",
                        color = JarvisTheme.Cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 3.sp
                    )
                },
                navigationIcon = {
                    if (isListening && showActions) {
                        Row(modifier = Modifier.padding(start = 8.dp)) {
                            LivePulse()
                        }
                    }
                },
                actions = {
                    if (showActions) {
                        IconButton(onClick = onClearChat) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat",
                                tint = JarvisTheme.Red.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = showTools) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = "Tool List",
                                tint = JarvisTheme.Cyan.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(onClick = showChart) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "TradingView",
                                tint = JarvisTheme.Cyan
                            )
                        }
                        IconButton(onClick = showAutomation) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Cron Jobs",
                                tint = JarvisTheme.Cyan.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(onClick = showSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    }
}
