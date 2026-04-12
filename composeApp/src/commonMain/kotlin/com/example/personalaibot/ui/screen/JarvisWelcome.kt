package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun JarvisWelcome() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("⬡", fontSize = 64.sp, color = JarvisTheme.Cyan.copy(0.3f))
        Spacer(Modifier.height(16.dp))
        Text(
            "JARVIS ONLINE",
            color = JarvisTheme.Cyan.copy(0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Just A Rather Very Intelligent System",
            color = Color.White.copy(0.4f),
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "พิมพ์ข้อความ หรือแตะไมค์เพื่อเริ่มการสนทนา",
            color = Color.White.copy(0.5f),
            fontSize = 13.sp
        )
    }
}
