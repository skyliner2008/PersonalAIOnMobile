package com.example.personalaibot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

// ─── Live Pulse Dot ───────────────────────────────────────────────────────────

@Composable
fun LivePulse() {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alpha by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(JarvisTheme.Red.copy(alpha = alpha), CircleShape)
    )
}

// ─── Typing Indicator ─────────────────────────────────────────────────────────

@Composable
fun TypingIndicator() {
    val infinite = rememberInfiniteTransition(label = "typing")
    val offset by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "typing_offset"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(JarvisTheme.Cyan.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("J", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Surface(color = JarvisTheme.Card, shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                listOf(0f, 0.33f, 0.66f).forEach { phase ->
                    val alpha = (kotlin.math.sin((offset - phase) * 2 * Math.PI.toFloat()) * 0.5f + 0.5f)
                        .coerceIn(0.3f, 1f)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(6.dp)
                            .background(JarvisTheme.Cyan.copy(alpha), CircleShape)
                    )
                }
            }
        }
    }
}
