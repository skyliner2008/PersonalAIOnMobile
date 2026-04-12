package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onClearChat: () -> Unit = {}
) {
    Surface(color = JarvisTheme.Surface) {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "J·A·R·V·I·S",
                            color = JarvisTheme.Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 3.sp
                        )
                        if (isListening) {
                            Spacer(Modifier.width(12.dp))
                            LivePulse()
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                color = JarvisTheme.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                },
                actions = {
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
                            contentDescription = "View All Tools",
                            tint = JarvisTheme.Cyan.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = showSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent // Surface handles the color
                )
            )
        }
    }
}
