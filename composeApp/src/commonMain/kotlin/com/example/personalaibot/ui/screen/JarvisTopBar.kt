package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
fun JarvisTopBar(isListening: Boolean, showSettings: () -> Unit) {
    TopAppBar(
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
            IconButton(onClick = showSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(0.7f))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = JarvisTheme.Surface
        )
    )
}
