package com.example.personalaibot.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun LiveModePanel(
    isCameraActive: Boolean,
    activeToolName: String?,
    onToggleCamera: () -> Unit,
    onEndLive: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "live_scale")
    val micScale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "mic_scale"
    )

    Surface(
        color = JarvisTheme.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status — แสดง tool execution หรือ listening
            if (activeToolName != null) {
                // Tool executing indicator
                val toolAlpha by infinite.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                    label = "tool_alpha"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(JarvisTheme.Purple.copy(toolAlpha), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "⚙ กำลังใช้เครื่องมือ: $activeToolName",
                        color = JarvisTheme.Purple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Text(
                    "● LIVE MODE — กำลังฟัง",
                    color = JarvisTheme.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(20.dp))

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (isCameraActive) JarvisTheme.Cyan.copy(0.2f) else JarvisTheme.Card,
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Camera",
                            tint = if (isCameraActive) JarvisTheme.Cyan else Color.White.copy(0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isCameraActive) "กล้องเปิด" else "กล้องปิด",
                        color = Color.White.copy(0.5f),
                        fontSize = 11.sp
                    )
                }

                // Pulsing mic (center)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(JarvisTheme.Red.copy(0.15f), CircleShape)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .background(JarvisTheme.Red.copy(0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Listening",
                            modifier = Modifier.scale(micScale).size(28.dp),
                            tint = JarvisTheme.Red
                        )
                    }
                }

                // End live button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onEndLive,
                        modifier = Modifier
                            .size(52.dp)
                            .background(JarvisTheme.Card, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.MicOff,
                            contentDescription = "End Live",
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("จบการสนทนา", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}
