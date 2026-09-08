package com.example.personalaibot.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.component.avatar.AvatarEmotion
import com.example.personalaibot.ui.component.avatar.AvatarState
import com.example.personalaibot.ui.component.avatar.JarvisAvatar
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlin.math.*

/**
 * AlwaysLiveScreen — Full-screen AI Live mode
 *
 * แสดง JARVIS Robot Avatar เต็มจอ พร้อม:
 * - Animated audio visualizer ring รอบ avatar
 * - Dark gradient background ที่เปลี่ยนตาม emotion
 * - Status badge (LIVE / CONNECTING / ERROR)
 * - Controls: Mic, Camera, Minimize, End
 * - Keep screen on (ตั้งค่าจากภายนอก)
 *
 * @param avatarState สถานะปัจจุบันของ avatar
 * @param connectionState สถานะ WebSocket connection
 * @param isMuted microphone muted?
 * @param isCameraActive camera on?
 * @param onToggleMute toggle mic
 * @param onToggleCamera toggle camera
 * @param onMinimize ย่อเป็น mini floating mode
 * @param onEndLive จบ Always Live mode
 * @param currentTime เวลาปัจจุบัน (เช่น "14:30")
 */
@Composable
fun AlwaysLiveScreen(
    avatarState: AvatarState,
    connectionState: com.example.personalaibot.data.ConnectionState,
    isMuted: Boolean,
    isCameraActive: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onMinimize: () -> Unit,
    onEndLive: () -> Unit,
    currentTime: String = ""
) {
    val infinite = rememberInfiniteTransition(label = "always_live")

    // ─── Background gradient that shifts with emotion ─────────────────────
    val bgColor1 = when (avatarState.emotion) {
        AvatarEmotion.ANGRY -> Color(0xFF1A0505)
        AvatarEmotion.LOVE -> Color(0xFF1A0510)
        AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> Color(0xFF0A1414)
        AvatarEmotion.SLEEPING -> Color(0xFF050510)
        else -> JarvisTheme.Dark
    }
    val bgColor2 = when (avatarState.emotion) {
        AvatarEmotion.ANGRY -> Color(0xFF200808)
        AvatarEmotion.LOVE -> Color(0xFF200818)
        AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> Color(0xFF081A1A)
        AvatarEmotion.SLEEPING -> Color(0xFF080818)
        else -> Color(0xFF0A0A1E)
    }

    // Mic pulsing
    val micScale by if (!isMuted) {
        infinite.animateFloat(
            initialValue = 1f, targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
            label = "mic_pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Ring rotation
    val ringRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ring_rotation"
    )

    // Ring pulse based on audio
    val ringPulse by infinite.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgColor1, bgColor2, bgColor1)
                )
            )
            .statusBarsPadding()
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // Top Bar — Status + Clock
        // ═══════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Badge
            val (statusText, statusColor, statusBgColor) = when (connectionState) {
                is com.example.personalaibot.data.ConnectionState.Connected ->
                    Triple("● LIVE", Color(0xFF69F0AE), Color(0xFF1B5E20).copy(alpha = 0.9f))
                is com.example.personalaibot.data.ConnectionState.Error ->
                    Triple("● ERROR", Color(0xFFFF8A80), Color(0xFF5D1A1A).copy(alpha = 0.9f))
                is com.example.personalaibot.data.ConnectionState.Connecting,
                is com.example.personalaibot.data.ConnectionState.Reconnecting ->
                    Triple("● CONNECTING", Color.White.copy(alpha = 0.55f), JarvisTheme.Card)
                else ->
                    Triple("● OFFLINE", Color.White.copy(alpha = 0.4f), JarvisTheme.Card)
            }

            Surface(
                color = statusBgColor,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
            ) {
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Clock
            if (currentTime.isNotEmpty()) {
                Text(
                    currentTime,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Minimize Button
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Minimize,
                    contentDescription = "Minimize to floating",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // Center — Avatar + Audio Ring
        // ═══════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-30).dp),
            contentAlignment = Alignment.Center
        ) {
            // Audio Visualizer Ring (behind avatar)
            AudioVisualizerRing(
                audioLevel = avatarState.audioLevel,
                emotion = avatarState.emotion,
                rotation = ringRotation,
                pulse = ringPulse,
                modifier = Modifier.size(340.dp)
            )

            // Robot Avatar
            JarvisAvatar(
                state = avatarState,
                modifier = Modifier.size(240.dp)
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // Status Text (below avatar)
        // ═══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emotion status text
            val emotionText = avatarState.statusText ?: when (avatarState.emotion) {
                AvatarEmotion.IDLE -> "Ready"
                AvatarEmotion.LISTENING -> "Listening..."
                AvatarEmotion.THINKING -> "Thinking..."
                AvatarEmotion.SPEAKING -> "Speaking..."
                AvatarEmotion.HAPPY -> "😊"
                AvatarEmotion.SAD -> "😢"
                AvatarEmotion.ANGRY -> "😤"
                AvatarEmotion.LOVE -> "💕"
                AvatarEmotion.SLEEPING -> "💤"
                AvatarEmotion.EXCITED -> "🎉"
            }

            AnimatedContent(
                targetState = emotionText,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "status_text"
            ) { text ->
                Text(
                    text,
                    color = when (avatarState.emotion) {
                        AvatarEmotion.LISTENING -> JarvisTheme.Cyan
                        AvatarEmotion.SPEAKING -> JarvisTheme.Green
                        AvatarEmotion.ANGRY -> JarvisTheme.Red
                        AvatarEmotion.LOVE -> JarvisTheme.HeartPink
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // Bottom Controls
        // ═══════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Toggle
            ControlButton(
                icon = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = "กล้อง",
                isActive = isCameraActive,
                activeColor = JarvisTheme.Cyan,
                onClick = onToggleCamera
            )

            // Mic Toggle (center, bigger)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        if (isMuted) Color.White.copy(0.05f) else JarvisTheme.Red.copy(0.12f),
                        CircleShape
                    )
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            if (isMuted) JarvisTheme.Card else JarvisTheme.Red.copy(0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        modifier = Modifier
                            .scale(if (isMuted) 1f else micScale)
                            .size(36.dp),
                        tint = if (isMuted) Color.White.copy(0.5f) else JarvisTheme.Red
                    )
                }
            }

            // End Button
            ControlButton(
                icon = Icons.Default.CallEnd,
                label = "End",
                isActive = true,
                activeColor = JarvisTheme.Red,
                onClick = onEndLive
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Audio Visualizer Ring — Circular wave around avatar
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AudioVisualizerRing(
    audioLevel: Float,
    emotion: AvatarEmotion,
    rotation: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    val ringColor = when (emotion) {
        AvatarEmotion.ANGRY -> JarvisTheme.Red.copy(alpha = 0.4f)
        AvatarEmotion.LOVE -> JarvisTheme.HeartPink.copy(alpha = 0.4f)
        AvatarEmotion.SLEEPING -> JarvisTheme.SleepBlue.copy(alpha = 0.2f)
        AvatarEmotion.EXCITED -> JarvisTheme.ExcitedGold.copy(alpha = 0.5f)
        else -> JarvisTheme.Cyan.copy(alpha = 0.3f + audioLevel * 0.4f)
    }

    val secondaryColor = when (emotion) {
        AvatarEmotion.ANGRY -> JarvisTheme.Red.copy(alpha = 0.15f)
        AvatarEmotion.LOVE -> JarvisTheme.HeartPink.copy(alpha = 0.15f)
        else -> JarvisTheme.Purple.copy(alpha = 0.15f + audioLevel * 0.2f)
    }

    Canvas(modifier = modifier.scale(pulse)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2f

        // Outer glow ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, secondaryColor, Color.Transparent),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        // Main ring segments — 24 arcs with varying thickness based on audio
        val segmentCount = 24
        val segmentGap = 3f
        val segmentSweep = (360f / segmentCount) - segmentGap

        for (i in 0 until segmentCount) {
            val startAngle = rotation + i * (360f / segmentCount)
            // Vary thickness with audio and position
            val wavePhase = sin((i + rotation * 0.1f) * (PI.toFloat() / 12f))
            val thickness = 2f + (audioLevel * 6f * (0.5f + wavePhase * 0.5f)).coerceIn(0f, 8f)

            drawArc(
                color = ringColor.copy(alpha = ringColor.alpha * (0.5f + wavePhase * 0.5f)),
                startAngle = startAngle,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = Offset(
                    center.x - baseRadius * 0.92f,
                    center.y - baseRadius * 0.92f
                ),
                size = androidx.compose.ui.geometry.Size(
                    baseRadius * 1.84f,
                    baseRadius * 1.84f
                ),
                style = Stroke(width = thickness, cap = StrokeCap.Round)
            )
        }

        // Inner ring (thin, constant)
        drawCircle(
            color = ringColor.copy(alpha = 0.15f),
            radius = baseRadius * 0.78f,
            center = center,
            style = Stroke(width = 1f)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Control Button
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) activeColor.copy(0.15f) else JarvisTheme.Card,
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White.copy(0.4f)
            )
        }
        Text(
            label,
            color = Color.White.copy(0.4f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
