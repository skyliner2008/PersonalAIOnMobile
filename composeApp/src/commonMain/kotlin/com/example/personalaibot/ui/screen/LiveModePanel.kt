package com.example.personalaibot.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.camera.CameraPreviewView
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun LiveModePanel(
    isCameraActive: Boolean,
    isFrontCamera: Boolean,
    isMuted: Boolean,
    isAiVisionRequested: Boolean,
    activeToolName: String?,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleMute: () -> Unit,
    onEndLive: () -> Unit,
    onFrameCapture: (String, ByteArray) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "live_ui")
    
    // Mic pulsing animation (only when NOT muted)
    val micScale by if (!isMuted) {
        infinite.animateFloat(
            initialValue = 1f, targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
            label = "mic_scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Eye pulsing animation (when AI is looking)
    val eyeAlpha by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "eye_alpha"
    )

    Surface(
        color = JarvisTheme.Surface.copy(0.95f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Header: Status & Eye Icon + Exit ───────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Eye Icon (AI Vision Status) - Explicitly shows if AI can see
                    Box(contentAlignment = Alignment.Center) {
                        if (isAiVisionRequested) {
                            // Pulsing glow when AI is watching
                            Surface(
                                modifier = Modifier.size(28.dp).alpha(eyeAlpha * 0.3f),
                                shape = CircleShape,
                                color = JarvisTheme.Cyan
                            ) {}
                        }
                        Text(
                            if (isAiVisionRequested) "👁️" else "🕶️",
                            modifier = Modifier.alpha(if (isAiVisionRequested) 1f else 0.4f),
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (activeToolName != null) "⚙ $activeToolName" 
                        else if (isAiVisionRequested) "👁 AI WATCHING" 
                        else "● LIVE MODE",
                        color = when {
                            activeToolName != null -> JarvisTheme.Purple
                            isAiVisionRequested -> JarvisTheme.Cyan
                            else -> JarvisTheme.Red
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Exit Button (X)
                IconButton(
                    onClick = onEndLive,
                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(Icons.Default.Close, "Exit", tint = Color.White.copy(0.4f), modifier = Modifier.size(20.dp))
                }
            }

            // ─── Content: Optional Camera Preview ───────────────────────────
            AnimatedVisibility(
                visible = isCameraActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreviewView(
                        modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f),
                        isFrontCamera = isFrontCamera,
                        isActive = isCameraActive,
                        onFrameCapture = onFrameCapture
                    )
                    
                    // AR Overlay Hint
                    if (isAiVisionRequested) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                "AI กำลังวิเคราะห์ภาพ...",
                                color = JarvisTheme.Cyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Footer: Main Controls ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Camera Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (isCameraActive) JarvisTheme.Cyan.copy(0.15f) else JarvisTheme.Card,
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Camera",
                            tint = if (isCameraActive) JarvisTheme.Cyan else Color.White.copy(0.4f)
                        )
                    }
                    Text("กล้อง", color = Color.White.copy(0.4f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }

                // Center: Big Pulsing Mic (Mute Toggle)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                if (isMuted) Color.White.copy(0.05f) else JarvisTheme.Red.copy(0.1f),
                                CircleShape
                            )
                    ) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    if (isMuted) JarvisTheme.Card else JarvisTheme.Red.copy(0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                modifier = Modifier.scale(if (isMuted) 1f else micScale).size(32.dp),
                                tint = if (isMuted) Color.White.copy(0.5f) else JarvisTheme.Red
                            )
                        }
                    }
                }

                // Right: Switch Camera
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onSwitchCamera,
                        enabled = isCameraActive,
                        modifier = Modifier
                            .size(56.dp)
                            .background(JarvisTheme.Card, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch",
                            tint = if (isCameraActive) Color.White.copy(0.7f) else Color.White.copy(0.2f)
                        )
                    }
                    Text("สลับกล้อง", color = Color.White.copy(0.4f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
