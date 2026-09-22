package com.skyliner2008.jarvis.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyliner2008.jarvis.camera.CameraPreviewView
import com.skyliner2008.jarvis.data.ConnectionState
import com.skyliner2008.jarvis.drive.DriveBridge
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.JarvisAvatar
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
import kotlinx.datetime.Clock

/**
 * DriveModeContent — Smart Drive & Control mode UI
 *
 * หน้าจอผู้ช่วยอัจฉริยะสำหรับโหมดขับขี่และควบคุม:
 * 1. GPS Speedometer HUD ขนาดใหญ่มองเห็นชัดเจนขณะขับรถ
 * 2. ป้ายระบุชื่อถนน/สถานที่ปัจจุบัน (Location Pill)
 * 3. Media Controls พร้อมปุ่มสัมผัสขนาดใหญ่พิเศษ (Play/Pause, Next, Prev)
 * 4. Quick Actions สั่งการด่วน: นำทาง (Google Maps), อ่านแจ้งเตือนด้วยเสียง (Voice Reader)
 * 5. รองรับทั้ง Portrait และ Landscape Two-Pane layout
 */
@Composable
internal fun DriveModeContent(
    activeAvatarState: AvatarState,
    connectionState: ConnectionState,
    currentTime: String,
    currentProfile: AlwaysLiveProfile,
    onSelectProfile: ((AlwaysLiveProfile) -> Unit)?,
    auraColor: Color,
    emotionText: String,
    isLandscape: Boolean,
    avatarSize: Dp,
    visualizerSize: Dp,
    auraPulse: Float,
    ringRotation: Float,
    ringPulse: Float,
    micScale: Float,
    isMuted: Boolean,
    isCameraActive: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onMinimize: () -> Unit,
    onEndLive: () -> Unit,
    onLiveVideoFrame: ((String) -> Unit)?,
    lastLiveFrameSendTime: Long,
    onLastLiveFrameSendTimeChanged: (Long) -> Unit
) {
    val telemetry by DriveBridge.telemetry.collectAsStateWithLifecycle()
    val mediaState by DriveBridge.mediaState.collectAsStateWithLifecycle()
    val recentNotification by DriveBridge.recentNotificationText.collectAsStateWithLifecycle()
    val parkingLocation by DriveBridge.parkingLocation.collectAsStateWithLifecycle()
    val isLowGlareMode by DriveBridge.isLowGlareMode.collectAsStateWithLifecycle()

    val onCycleSpeedLimit: () -> Unit = {
        val limits = listOf(80f, 90f, 100f, 110f, 120f)
        val currentIndex = limits.indexOf(telemetry.speedLimitKmh).takeIf { it >= 0 } ?: (limits.size - 1)
        val nextLimit = limits[(currentIndex + 1) % limits.size]
        DriveBridge.setSpeedLimit(nextLimit)
    }

    if (!isLandscape) {
        // ─── PORTRAIT LAYOUT ───
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Status Bar
            TopStatusRow(
                connectionState = connectionState,
                currentTime = currentTime,
                onMinimize = onMinimize,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp)
            )

            // 2. Profile Selector Row
            ProfileSelectorRow(
                currentProfile = currentProfile,
                onSelectProfile = onSelectProfile,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // 3. Realtime Speed & Location HUD Badge
            DriveTelemetryHeader(
                telemetry = telemetry,
                isLowGlareMode = isLowGlareMode,
                onToggleLowGlare = { DriveBridge.toggleLowGlareMode() },
                onCycleSpeedLimit = onCycleSpeedLimit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            // 4. Center — Avatar + 36-bar Visualizer + Ambient Aura
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Ambient Aura (Dimmed in Low Glare Mode)
                Box(
                    modifier = Modifier
                        .size(visualizerSize * 1.05f * (if (isLowGlareMode) 0.8f else auraPulse))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    auraColor.copy(alpha = if (isLowGlareMode) 0.04f else (0.22f + activeAvatarState.audioLevel * 0.25f)),
                                    auraColor.copy(alpha = if (isLowGlareMode) 0.01f else 0.06f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // High-Tech Audio Visualizer Ring (Subdued in Low Glare Mode)
                AudioVisualizerRing(
                    audioLevel = if (isLowGlareMode) activeAvatarState.audioLevel * 0.35f else activeAvatarState.audioLevel,
                    emotion = activeAvatarState.emotion,
                    rotation = ringRotation,
                    pulse = if (isLowGlareMode) 1.0f else ringPulse,
                    modifier = Modifier.size(visualizerSize)
                )

                // 3D Robot Avatar
                JarvisAvatar(
                    state = activeAvatarState,
                    modifier = Modifier.size(if (isLowGlareMode) avatarSize * 0.92f else avatarSize)
                )
            }

            // 5. Emotion Status Pill
            StatusPill(
                emotionText = emotionText,
                emotion = activeAvatarState.emotion,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // 6. Recent Notification Banner (if any)
            if (!recentNotification.isNullOrBlank()) {
                Surface(
                    color = (if (isLowGlareMode) Color(0xFF0C0C14) else Color(0xFF1A1A2E)).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, JarvisTheme.Cyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = recentNotification ?: "",
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { DriveBridge.updateRecentNotification(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(0.6f))
                        }
                    }
                }
            }

            // 7. Driving Media & Quick Action Control Card
            DriveControlPanel(
                mediaState = mediaState,
                parkingLocation = parkingLocation,
                isLowGlareMode = isLowGlareMode,
                onPlayPause = { DriveBridge.triggerPlayPause() },
                onNext = { DriveBridge.triggerNextTrack() },
                onPrev = { DriveBridge.triggerPrevTrack() },
                onReadNotifications = { DriveBridge.triggerReadNotifications() },
                onNavigate = { DriveBridge.triggerStartNavigation() },
                onSaveParking = { DriveBridge.saveCurrentParking() },
                onNavigateToParking = {
                    val p = parkingLocation
                    if (p != null && (p.latitude != 0.0 || p.longitude != 0.0)) {
                        DriveBridge.triggerStartNavigation("${p.latitude},${p.longitude}")
                    } else if (!p?.address.isNullOrBlank()) {
                        DriveBridge.triggerStartNavigation(p?.address)
                    }
                },
                onClearParking = { DriveBridge.clearParkingLocation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Optional Camera Preview
            if (isCameraActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreviewView(
                        modifier = Modifier.fillMaxSize(),
                        isFrontCamera = true,
                        isActive = true,
                        onFrameCapture = { jpegBase64, _ ->
                            val now = Clock.System.now().toEpochMilliseconds()
                            if (now - lastLiveFrameSendTime >= 900L) {
                                onLastLiveFrameSendTimeChanged(now)
                                onLiveVideoFrame?.invoke(jpegBase64)
                            }
                        }
                    )
                }
            }

            // 8. Bottom Phone & Mic Controls
            ControlsRow(
                isMuted = isMuted,
                isCameraActive = isCameraActive,
                micScale = micScale,
                onToggleMute = onToggleMute,
                onToggleCamera = onToggleCamera,
                onEndLive = onEndLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            )
        }
    } else {
        // ─── LANDSCAPE LAYOUT (Two-Pane) ───
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
                    .weight(1.05f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(visualizerSize * 1.15f * (if (isLowGlareMode) 0.8f else auraPulse))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    auraColor.copy(alpha = if (isLowGlareMode) 0.04f else (0.25f + activeAvatarState.audioLevel * 0.25f)),
                                    auraColor.copy(alpha = if (isLowGlareMode) 0.01f else 0.07f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                AudioVisualizerRing(
                    audioLevel = if (isLowGlareMode) activeAvatarState.audioLevel * 0.35f else activeAvatarState.audioLevel,
                    emotion = activeAvatarState.emotion,
                    rotation = ringRotation,
                    pulse = if (isLowGlareMode) 1.0f else ringPulse,
                    modifier = Modifier.size(visualizerSize)
                )

                JarvisAvatar(
                    state = activeAvatarState,
                    modifier = Modifier.size(if (isLowGlareMode) avatarSize * 0.92f else avatarSize)
                )
            }

            // Right Pane: Top Bar, Telemetry HUD, Media, Quick Actions, Controls
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
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

                // Profile Selector Row
                ProfileSelectorRow(
                    currentProfile = currentProfile,
                    onSelectProfile = onSelectProfile,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Speed & Location HUD
                DriveTelemetryHeader(
                    telemetry = telemetry,
                    isLowGlareMode = isLowGlareMode,
                    onToggleLowGlare = { DriveBridge.toggleLowGlareMode() },
                    onCycleSpeedLimit = onCycleSpeedLimit,
                    modifier = Modifier.fillMaxWidth()
                )

                // Driving Media & Quick Actions
                DriveControlPanel(
                    mediaState = mediaState,
                    parkingLocation = parkingLocation,
                    isLowGlareMode = isLowGlareMode,
                    onPlayPause = { DriveBridge.triggerPlayPause() },
                    onNext = { DriveBridge.triggerNextTrack() },
                    onPrev = { DriveBridge.triggerPrevTrack() },
                    onReadNotifications = { DriveBridge.triggerReadNotifications() },
                    onNavigate = { DriveBridge.triggerStartNavigation() },
                    onSaveParking = { DriveBridge.saveCurrentParking() },
                    onNavigateToParking = {
                        val p = parkingLocation
                        if (p != null && (p.latitude != 0.0 || p.longitude != 0.0)) {
                            DriveBridge.triggerStartNavigation("${p.latitude},${p.longitude}")
                        } else if (!p?.address.isNullOrBlank()) {
                            DriveBridge.triggerStartNavigation(p?.address)
                        }
                    },
                    onClearParking = { DriveBridge.clearParkingLocation() },
                    modifier = Modifier.fillMaxWidth()
                )

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

// ═════════════════════════════════════════════════════════════════════════════
// Sub-Composables: Drive Telemetry & Car Media HUD
// ═════════════════════════════════════════════════════════════════════════════

/**
 * DriveTelemetryHeader — แสดงความเร็วรถแบบดิจิทัล, ขีดจำกัดความเร็ว, การแจ้งเตือนความเร็วเกิน, ที่อยู่ปัจจุบัน, และปุ่มเปิด/ปิดโหมดกลางคืน
 */
@Composable
private fun DriveTelemetryHeader(
    telemetry: DriveBridge.DriveTelemetry,
    isLowGlareMode: Boolean,
    onToggleLowGlare: () -> Unit,
    onCycleSpeedLimit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedInt = telemetry.speedKmh.toInt().coerceAtLeast(0)
    val isSpeeding = telemetry.isSpeeding
    val speedColor = when {
        isSpeeding      -> Color(0xFFFF1744) // Over-speed Alert Crimson Red
        speedInt >= 90  -> Color(0xFFFFD700) // Fast Amber Gold
        speedInt > 0    -> Color(0xFF00E676) // Moving Green
        else            -> JarvisTheme.Cyan  // Stationary Cyan
    }

    val cardBgColor = if (isLowGlareMode) Color(0xFF08080C).copy(alpha = 0.88f) else JarvisTheme.Card.copy(alpha = 0.65f)
    val cardBorderColor = if (isSpeeding) Color(0xFFFF1744) else speedColor.copy(alpha = 0.35f)

    Surface(
        color = cardBgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isSpeeding) 2.dp else 1.dp, cardBorderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed & Speed Limit Badge Column
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$speedInt",
                            color = speedColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "KM/H",
                            color = speedColor.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Speed Limit Pill (Clickable to change 80/90/100/110/120)
                    Surface(
                        color = if (isSpeeding) Color(0xFFFF1744).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSpeeding) Color(0xFFFF1744) else Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable(onClick = onCycleSpeedLimit)
                    ) {
                        Text(
                            text = "MAX ${telemetry.speedLimitKmh.toInt()}",
                            color = if (isSpeeding) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                }

                // Road / Location Column + Night Mode Toggle Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (speedInt > 0) "🚗 กำลังเดินทาง" else "🅿️ รถจอดอยู่",
                            color = speedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = telemetry.address ?: "📍 ตรวจจับพิกัด GPS อัตโนมัติ",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Night / Low Glare Toggle
                    Surface(
                        color = if (isLowGlareMode) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (isLowGlareMode) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onToggleLowGlare)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isLowGlareMode) "🌙" else "🕶️",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Speeding Alert Banner (displayed directly below if speeding)
            if (isSpeeding) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = Color(0xFFFF1744).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF1744)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚠️ ขับขี่เกินความเร็วที่กำหนด (${telemetry.speedLimitKmh.toInt()} KM/H) กรุณาลดความเร็ว",
                            color = Color(0xFFFF5252),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * DriveControlPanel — แผงควบคุมมีเดีย, ทางลัดนำทาง, อ่านแจ้งเตือน, และระบบจำจุดจอดรถอัจฉริยะ
 */
@Composable
private fun DriveControlPanel(
    mediaState: DriveBridge.MediaPlaybackState,
    parkingLocation: DriveBridge.ParkingLocation?,
    isLowGlareMode: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReadNotifications: () -> Unit,
    onNavigate: () -> Unit,
    onSaveParking: () -> Unit,
    onNavigateToParking: () -> Unit,
    onClearParking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isLowGlareMode) Color(0xFF08080C).copy(alpha = 0.88f) else JarvisTheme.Card.copy(alpha = 0.75f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            // Track Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaState.title ?: "🎵 เครื่องเล่นเพลงและสื่อ",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mediaState.artist ?: mediaState.appName ?: "พร้อมสั่งงานเสียงเปิดเพลง",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                // Media Action Buttons (Large Touch Targets)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(38.dp)
                            .background(JarvisTheme.Cyan.copy(alpha = 0.25f), CircleShape)
                            .border(1.dp, JarvisTheme.Cyan, CircleShape)
                    ) {
                        Icon(
                            if (mediaState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = JarvisTheme.Cyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quick Driving Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Navigation Shortcut
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigate)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🗺️ นำทาง Google Maps",
                            color = Color(0xFF69F0AE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Notification Voice Reader Shortcut
                Surface(
                    color = Color(0xFFFF9100).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onReadNotifications)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📬 อ่านแจ้งเตือน",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Smart Parking Location Section
            if (parkingLocation != null) {
                Surface(
                    color = Color(0xFF3D5AFE).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF3D5AFE).copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🅿️", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "จุดจอดรถที่จำไว้",
                                    color = Color(0xFF8C9EFF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = parkingLocation.address ?: "พิกัด: ${parkingLocation.latitude.toString().take(6)}, ${parkingLocation.longitude.toString().take(6)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E676)),
                                modifier = Modifier.clickable(onClick = onNavigateToParking)
                            ) {
                                Text(
                                    text = "🗺️ นำทางไปรถ",
                                    color = Color(0xFF69F0AE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            IconButton(
                                onClick = onClearParking,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear Parking",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = Color(0xFF3D5AFE).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF3D5AFE).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSaveParking)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🅿️ บันทึก/จำจุดจอดรถตรงนี้",
                            color = Color(0xFF8C9EFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
