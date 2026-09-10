package com.skyliner2008.jarvis.ui.screen

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.JarvisAvatar
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
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
    connectionState: com.skyliner2008.jarvis.data.ConnectionState,
    isMuted: Boolean,
    isCameraActive: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onMinimize: () -> Unit,
    onEndLive: () -> Unit,
    currentTime: String = ""
) {
    val infinite = rememberInfiniteTransition(label = "always_live")

    // ─── Dynamic Ambient Gradient with Smooth Emotion Transitions ────────
    val targetBgColor1 = when (avatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF031A24)
        AvatarEmotion.SPEAKING -> Color(0xFF041D17)
        AvatarEmotion.THINKING -> Color(0xFF130726)
        AvatarEmotion.HAPPY -> Color(0xFF041D1E)
        AvatarEmotion.EXCITED -> Color(0xFF211502)
        AvatarEmotion.LOVE -> Color(0xFF240718)
        AvatarEmotion.ANGRY -> Color(0xFF260505)
        AvatarEmotion.SAD -> Color(0xFF070E1A)
        AvatarEmotion.SLEEPING -> Color(0xFF030514)
        AvatarEmotion.IDLE -> Color(0xFF060B1C)
    }
    val targetBgColor2 = when (avatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF063142)
        AvatarEmotion.SPEAKING -> Color(0xFF083B2F)
        AvatarEmotion.THINKING -> Color(0xFF220C42)
        AvatarEmotion.HAPPY -> Color(0xFF0A3A36)
        AvatarEmotion.EXCITED -> Color(0xFF3D2704)
        AvatarEmotion.LOVE -> Color(0xFF3D0B27)
        AvatarEmotion.ANGRY -> Color(0xFF420A0A)
        AvatarEmotion.SAD -> Color(0xFF0E1A33)
        AvatarEmotion.SLEEPING -> Color(0xFF060B24)
        AvatarEmotion.IDLE -> Color(0xFF0E1638)
    }
    val targetBgColor3 = when (avatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF010D14)
        AvatarEmotion.SPEAKING -> Color(0xFF010E0B)
        AvatarEmotion.THINKING -> Color(0xFF07020E)
        AvatarEmotion.HAPPY -> Color(0xFF020F0F)
        AvatarEmotion.EXCITED -> Color(0xFF0E0901)
        AvatarEmotion.LOVE -> Color(0xFF0F020A)
        AvatarEmotion.ANGRY -> Color(0xFF100101)
        AvatarEmotion.SAD -> Color(0xFF03060C)
        AvatarEmotion.SLEEPING -> Color(0xFF010208)
        AvatarEmotion.IDLE -> Color(0xFF03040A)
    }

    val animBg1 by animateColorAsState(targetBgColor1, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg1")
    val animBg2 by animateColorAsState(targetBgColor2, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg2")
    val animBg3 by animateColorAsState(targetBgColor3, animationSpec = tween(900, easing = EaseInOutCubic), label = "bg3")

    val auraColor = when (avatarState.emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00F0FF) // Neon Aqua Cyan
        AvatarEmotion.SPEAKING -> Color(0xFF00E676) // Electric Emerald
        AvatarEmotion.THINKING -> Color(0xFFB388FF) // Cyber Violet
        AvatarEmotion.HAPPY -> Color(0xFF00E5FF) // Sky Turquoise
        AvatarEmotion.EXCITED -> Color(0xFFFFD700) // Solar Amber Gold
        AvatarEmotion.LOVE -> Color(0xFFFF4081) // Hot Neon Pink
        AvatarEmotion.ANGRY -> Color(0xFFFF1744) // Alert Flame Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF) // Ice Slate Blue
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB) // Calm Lavender
        AvatarEmotion.IDLE -> Color(0xFF00F0FF) // Signature Jarvis Cyan
    }

    // Mic pulsing
    val micScale by if (!isMuted) {
        infinite.animateFloat(
            initialValue = 1f, targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
            label = "mic_pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Ring rotation
    val ringRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing)),
        label = "ring_rotation"
    )

    // Ring pulse based on audio
    val ringPulse by infinite.animateFloat(
        initialValue = 0.97f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(550, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring_pulse"
    )

    // Ambient aura pulse
    val auraPulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "aura_pulse"
    )

    // Emotion status text
    val emotionText = avatarState.statusText ?: when (avatarState.emotion) {
        AvatarEmotion.IDLE -> "พร้อมรับคำสั่ง (Ready)"
        AvatarEmotion.LISTENING -> "กำลังฟัง... (Listening)"
        AvatarEmotion.THINKING -> "กำลังประมวลผล... (Thinking)"
        AvatarEmotion.SPEAKING -> "กำลังสนทนา... (Speaking)"
        AvatarEmotion.HAPPY -> "ยินดีให้บริการ 😊"
        AvatarEmotion.SAD -> "ต้องการความช่วยเหลือไหมคะ 😢"
        AvatarEmotion.ANGRY -> "ตรวจพบปัญหา 😤"
        AvatarEmotion.LOVE -> "พร้อมดูแลคุณเสมอ 💕"
        AvatarEmotion.SLEEPING -> "โหมดสแตนด์บาย 💤"
        AvatarEmotion.EXCITED -> "ยอดเยี่ยมมาก! 🎉"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animBg1, animBg2, animBg3)
                )
            )
            .statusBarsPadding()
    ) {
        val isLandscape = maxWidth > maxHeight

        // Dynamic Sizing based on orientation & available space
        val avatarSize = if (isLandscape) {
            minOf(maxHeight * 0.65f, 240.dp)
        } else {
            minOf(maxWidth * 0.62f, 240.dp)
        }
        val visualizerSize = avatarSize * 1.48f

        if (!isLandscape) {
            // ═══════════════════════════════════════════════════════════════
            // PORTRAIT LAYOUT
            // ═══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                TopStatusRow(
                    connectionState = connectionState,
                    currentTime = currentTime,
                    onMinimize = onMinimize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                // Center — Avatar + 36-bar Visualizer + Ambient Aura
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Ambient Radial Aura behind avatar
                    Box(
                        modifier = Modifier
                            .size(visualizerSize * 1.1f * auraPulse)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        auraColor.copy(alpha = 0.22f + avatarState.audioLevel * 0.25f),
                                        auraColor.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )

                    // 36-Bar High-Tech Audio Visualizer Ring
                    AudioVisualizerRing(
                        audioLevel = avatarState.audioLevel,
                        emotion = avatarState.emotion,
                        rotation = ringRotation,
                        pulse = ringPulse,
                        modifier = Modifier.size(visualizerSize)
                    )

                    // 3D Robot Avatar
                    JarvisAvatar(
                        state = avatarState,
                        modifier = Modifier.size(avatarSize)
                    )
                }

                // Emotion Status Pill
                StatusPill(
                    emotionText = emotionText,
                    emotion = avatarState.emotion,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Bottom Controls
                ControlsRow(
                    isMuted = isMuted,
                    isCameraActive = isCameraActive,
                    micScale = micScale,
                    onToggleMute = onToggleMute,
                    onToggleCamera = onToggleCamera,
                    onEndLive = onEndLive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp)
                        .navigationBarsPadding()
                )
            }
        } else {
            // ═══════════════════════════════════════════════════════════════
            // LANDSCAPE LAYOUT (Two-Pane)
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Pane: Avatar & 36-bar Visualizer
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Ambient Radial Aura
                    Box(
                        modifier = Modifier
                            .size(visualizerSize * 1.15f * auraPulse)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        auraColor.copy(alpha = 0.25f + avatarState.audioLevel * 0.25f),
                                        auraColor.copy(alpha = 0.07f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )

                    AudioVisualizerRing(
                        audioLevel = avatarState.audioLevel,
                        emotion = avatarState.emotion,
                        rotation = ringRotation,
                        pulse = ringPulse,
                        modifier = Modifier.size(visualizerSize)
                    )

                    JarvisAvatar(
                        state = avatarState,
                        modifier = Modifier.size(avatarSize)
                    )
                }

                // Right Pane: Top Bar, Telemetry Card, and Controls
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    TopStatusRow(
                        connectionState = connectionState,
                        currentTime = currentTime,
                        onMinimize = onMinimize,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Cyber HUD Status Card
                    Surface(
                        color = JarvisTheme.Card.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            auraColor.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "AI TELEMETRY",
                                color = auraColor.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                emotionText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (avatarState.audioLevel > 0.05f) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Mic Level: ${(avatarState.audioLevel * 100).toInt()}%",
                                    color = JarvisTheme.Cyan.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Bottom Controls
                    ControlsRow(
                        isMuted = isMuted,
                        isCameraActive = isCameraActive,
                        micScale = micScale,
                        onToggleMute = onToggleMute,
                        onToggleCamera = onToggleCamera,
                        onEndLive = onEndLive,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Sub-Composables
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopStatusRow(
    connectionState: com.skyliner2008.jarvis.data.ConnectionState,
    currentTime: String,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (statusText, statusColor, statusBgColor) = when (connectionState) {
            is com.skyliner2008.jarvis.data.ConnectionState.Connected ->
                Triple("● LIVE", Color(0xFF69F0AE), Color(0xFF1B5E20).copy(alpha = 0.9f))
            is com.skyliner2008.jarvis.data.ConnectionState.Error ->
                Triple("● ERROR", Color(0xFFFF8A80), Color(0xFF5D1A1A).copy(alpha = 0.9f))
            is com.skyliner2008.jarvis.data.ConnectionState.Connecting,
            is com.skyliner2008.jarvis.data.ConnectionState.Reconnecting ->
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

        if (currentTime.isNotEmpty()) {
            Text(
                currentTime,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(
            onClick = onMinimize,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Minimize,
                contentDescription = "Minimize to floating",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(
    emotionText: String,
    emotion: AvatarEmotion,
    modifier: Modifier = Modifier
) {
    val pillColor = when (emotion) {
        AvatarEmotion.LISTENING -> JarvisTheme.Cyan
        AvatarEmotion.SPEAKING -> JarvisTheme.Green
        AvatarEmotion.THINKING -> Color(0xFFB388FF)
        AvatarEmotion.HAPPY -> Color(0xFF00E5FF)
        AvatarEmotion.EXCITED -> Color(0xFFFFD700)
        AvatarEmotion.LOVE -> JarvisTheme.HeartPink
        AvatarEmotion.ANGRY -> JarvisTheme.Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF)
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB)
        AvatarEmotion.IDLE -> JarvisTheme.Cyan
    }

    Surface(
        color = JarvisTheme.Card.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            pillColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = emotionText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "status_text",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        ) { text ->
            Text(
                text,
                color = pillColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun ControlsRow(
    isMuted: Boolean,
    isCameraActive: Boolean,
    micScale: Float,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
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
                .size(86.dp)
                .background(
                    if (isMuted) Color.White.copy(0.06f) else JarvisTheme.Red.copy(0.12f),
                    CircleShape
                )
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        if (isMuted) JarvisTheme.Card else JarvisTheme.Red.copy(0.25f),
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


// ═════════════════════════════════════════════════════════════════════════════
// 36-Bar High-Tech Radial Audio Visualizer Ring
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
        AvatarEmotion.LISTENING -> Color(0xFF00F0FF) // Neon Cyan
        AvatarEmotion.SPEAKING -> Color(0xFF00E676) // Electric Emerald
        AvatarEmotion.THINKING -> Color(0xFFB388FF) // Cyber Violet
        AvatarEmotion.HAPPY -> Color(0xFF00E5FF) // Sky Turquoise
        AvatarEmotion.EXCITED -> Color(0xFFFFD700) // Solar Gold
        AvatarEmotion.LOVE -> Color(0xFFFF4081) // Hot Pink
        AvatarEmotion.ANGRY -> Color(0xFFFF1744) // Alert Red
        AvatarEmotion.SAD -> Color(0xFF80D8FF) // Ice Slate Blue
        AvatarEmotion.SLEEPING -> Color(0xFF7986CB) // Calm Lavender
        AvatarEmotion.IDLE -> Color(0xFF00F0FF) // Signature Jarvis Cyan
    }

    val secondaryColor = when (emotion) {
        AvatarEmotion.LISTENING -> Color(0xFF00B0FF) // Deep Aqua
        AvatarEmotion.SPEAKING -> Color(0xFF69F0AE) // Mint Neon
        AvatarEmotion.THINKING -> Color(0xFF7C4DFF) // Deep Violet
        AvatarEmotion.HAPPY -> Color(0xFF1DE9B6) // Teal Emerald
        AvatarEmotion.EXCITED -> Color(0xFFFF9100) // Deep Amber
        AvatarEmotion.LOVE -> Color(0xFFFF80AB) // Soft Rose
        AvatarEmotion.ANGRY -> Color(0xFFFF5252) // Coral Crimson
        AvatarEmotion.SAD -> Color(0xFF448AFF) // Cool Indigo
        AvatarEmotion.SLEEPING -> Color(0xFF3F51B5) // Midnight Blue
        AvatarEmotion.IDLE -> Color(0xFF7C4DFF) // Purple Cyber Accent
    }

    Canvas(modifier = modifier.scale(pulse)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = size.minDimension
        val baseRadius = minDim / 2f

        // ─── 1. Outer Radial Glow ─────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    ringColor.copy(alpha = 0.08f + audioLevel * 0.15f),
                    secondaryColor.copy(alpha = 0.18f + audioLevel * 0.25f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        // ─── 2. Dual Rotating HUD Reticle Rings ───────────────────────────
        // Inner tech reticle (counter-clockwise)
        rotate(-rotation * 0.5f, pivot = center) {
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = baseRadius * 0.88f,
                center = center,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), 0f)
                )
            )
        }

        // Outer tech reticle (clockwise)
        rotate(rotation * 0.7f, pivot = center) {
            drawCircle(
                color = secondaryColor.copy(alpha = 0.35f),
                radius = baseRadius * 0.98f,
                center = center,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 24f), 0f)
                )
            )
        }

        // ─── 3. 36-Bar Radial Equalizer Bars ─────────────────────────────
        val barCount = 36
        val innerRadius = baseRadius * 0.72f
        val maxBarLength = baseRadius * 0.22f
        val baseBarLength = baseRadius * 0.04f

        for (i in 0 until barCount) {
            val angleDeg = i * (360f / barCount)
            val angleRad = (angleDeg * PI / 180f).toFloat()

            // Wave frequency harmonic per bar
            val harmonic = sin((i * 10f * (PI / 180f) * 3f + rotation * 0.08f).toFloat()).absoluteValue
            val dynamicLevel = (audioLevel * (0.55f + harmonic * 0.45f)).coerceIn(0f, 1f)
            val currentBarLength = baseBarLength + maxBarLength * dynamicLevel

            val cosA = cos(angleRad)
            val sinA = sin(angleRad)

            val startX = center.x + innerRadius * cosA
            val startY = center.y + innerRadius * sinA
            val endX = center.x + (innerRadius + currentBarLength) * cosA
            val endY = center.y + (innerRadius + currentBarLength) * sinA

            val barAlpha = (0.35f + dynamicLevel * 0.65f).coerceIn(0f, 1f)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ringColor.copy(alpha = barAlpha * 0.7f),
                        secondaryColor.copy(alpha = barAlpha),
                        Color.White.copy(alpha = barAlpha)
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = (minDim * 0.011f).coerceIn(2.5f, 6f),
                cap = StrokeCap.Round
            )
        }

        // ─── 4. Cardinal Tech Dial Marks (0°, 90°, 180°, 270°) ────────────
        for (deg in listOf(0f, 90f, 180f, 270f)) {
            val rad = (deg * PI / 180f).toFloat()
            val tickR1 = baseRadius * 0.68f
            val tickR2 = baseRadius * 0.73f
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(center.x + tickR1 * cos(rad), center.y + tickR1 * sin(rad)),
                end = Offset(center.x + tickR2 * cos(rad), center.y + tickR2 * sin(rad)),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // ─── 5. Inner Core Base Circle ───────────────────────────────────
        drawCircle(
            color = ringColor.copy(alpha = 0.2f),
            radius = innerRadius * 0.98f,
            center = center,
            style = Stroke(width = 1.5f)
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
