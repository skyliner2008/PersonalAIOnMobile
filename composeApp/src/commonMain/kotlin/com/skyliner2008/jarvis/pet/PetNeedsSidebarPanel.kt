package com.skyliner2008.jarvis.pet

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PetNeedsSidebarPanel — แถบสถานะสัตว์เลี้ยงแบบ Sidebar สไลด์จากขอบขวา
 *
 * แสดง:
 * - แถบค่าสถานะ 5 ค่า (Animated + color-coded)
 * - ปุ่ม Care Actions (ให้อาหาร, อาบน้ำ, เล่น, นอน)
 * - สรุป Affection Level + Mood Badge
 * - Pet Memory Stats (อายุ, สถิติ, ความชอบ)
 *
 * เปิดได้โดย: ปัดจากขอบขวา / กดปุ่ม 🐾
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetNeedsSidebarPanel(
    isOpen: Boolean,
    onClose: () -> Unit,
    needsState: PetNeedsState,
    memory: PetMemory,
    currentMoodEmoji: String = needsState.moodSummary.emoji,
    currentMoodLabel: String = needsState.moodSummary.label,
    onFeed: () -> Unit,
    onClean: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Slide-in animation
    val slideOffset by animateDpAsState(
        targetValue = if (isOpen) 0.dp else 320.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sidebarSlide"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.3f else 0f,
        animationSpec = tween(300),
        label = "overlayAlpha"
    )

    if (isOpen || slideOffset < 320.dp) {
        // ─── Background Overlay (tap to close) ───
        Box(modifier = Modifier.fillMaxSize()) {
            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                        .clickable { onClose() }
                )
            }

            // ─── Sidebar Panel ───
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .offset(x = slideOffset)
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D1117),
                                Color(0xFF141824),
                                Color(0xFF0D1117)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount > 30f) onClose()
                        }
                    }
            ) {
                PetNeedsDashboardContent(
                    needsState = needsState,
                    memory = memory,
                    currentMoodEmoji = currentMoodEmoji,
                    currentMoodLabel = currentMoodLabel,
                    onFeed = onFeed,
                    onClean = onClean,
                    onPlay = onPlay,
                    onSleep = onSleep,
                    showHeader = true,
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * PetNeedsDashboardContent — แดชบอร์ดแสดงค่าสถานะสัตว์เลี้ยง, อารมณ์, ปุ่มดูแล, และหน่วยความจำ
 * ใช้ทั้งใน Split Screen แถวตั้ง (Portrait) และใน Sidebar สไลด์ขอบจอ (Landscape)
 */
@Composable
fun PetNeedsDashboardContent(
    needsState: PetNeedsState,
    memory: PetMemory,
    currentMoodEmoji: String = needsState.moodSummary.emoji,
    currentMoodLabel: String = needsState.moodSummary.label,
    onFeed: () -> Unit,
    onClean: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    showHeader: Boolean = false,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
        if (showHeader) {
            // ─── Header ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐾", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "สถานะน้อง",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ─── Mood Badge ───
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currentMoodEmoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "อารมณ์ตอนนี้",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                        Text(
                            currentMoodLabel,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Affection Level
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "💖 Lv.${needsState.affectionLevel}",
                        color = Color(0xFFFF4081),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        needsState.affectionLevelName(),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ─── Status Bars ───
        Text(
            "ค่าสถานะ",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedNeedBar("🍖 อิ่ม", needsState.satiety, needBarColor(needsState.satiety))
                AnimatedNeedBar("⚡ พลัง", needsState.energy, needBarColor(needsState.energy))
                AnimatedNeedBar("🧼 สะอาด", needsState.hygiene, needBarColor(needsState.hygiene))
                AnimatedNeedBar("😊 ความสุข", needsState.happiness, needBarColor(needsState.happiness))
                AnimatedNeedBar("💢 เครียด", needsState.stress, stressBarColor(needsState.stress), isNegative = true)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ─── Care Action Buttons ───
        Text(
            "ดูแลน้อง",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SidebarCareButton("🍖", "อาหาร", Color(0xFFFFB300), Modifier.weight(1f), onFeed)
            SidebarCareButton("🧼", "อาบน้ำ", Color(0xFF00E676), Modifier.weight(1f), onClean)
            SidebarCareButton("🎾", "เล่น", Color(0xFF00F0FF), Modifier.weight(1f), onPlay)
            SidebarCareButton("💤", "นอน", Color(0xFF7986CB), Modifier.weight(1f), onSleep)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ─── Pet Memory Stats ───
        Text(
            "📊 หน่วยความจำ",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MemoryStatRow("🎂 อายุ", "${memory.ageInDays} วัน")
                MemoryStatRow("💖 ปฏิสัมพันธ์ทั้งหมด", "${memory.totalInteractions} ครั้ง")
                MemoryStatRow("🔥 แฮปปี้ต่อเนื่อง", "${memory.consecutiveHappyDays} วัน")
                MemoryStatRow("🏆 แฮปปี้สูงสุด", "${memory.longestHappyStreak} วัน")
                MemoryStatRow("❤️ ชอบที่สุด", interactionLabel(memory.favoriteInteraction))
                MemoryStatRow("🍖 ให้อาหาร", "${memory.totalFeeds} ครั้ง")
                MemoryStatRow("🎾 ชวนเล่น", "${memory.totalPlays} ครั้ง")

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(6.dp))

                // Session stats
                Text(
                    "📱 เซสชันนี้",
                    color = Color(0xFF00F0FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                MemoryStatRow("🐾 ปฏิสัมพันธ์", "${memory.sessionInteractions} ครั้ง")
                MemoryStatRow("✋ ลูบหัว", "${memory.sessionPets} ครั้ง")
                MemoryStatRow("👆 จิ้ม", "${memory.sessionPokes} ครั้ง")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Animated Status Bar ──────────────────────────────────────────────────────

@Composable
private fun AnimatedNeedBar(
    label: String,
    value: Float,
    barColor: Color,
    isNegative: Boolean = false
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "needBar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            Text(
                "${animatedValue.toInt()}%",
                color = if (isNegative && value > 60f) Color(0xFFFF1744) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (animatedValue / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF2C344D)
        )
    }
}

// ─── Care Action Button ───────────────────────────────────────────────────────

@Composable
private fun SidebarCareButton(
    emoji: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = Color(0xFF1E2436),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Memory Stat Row ──────────────────────────────────────────────────────────

@Composable
private fun MemoryStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Color Helpers ────────────────────────────────────────────────────────────

private fun needBarColor(value: Float): Color = when {
    value >= 80f -> Color(0xFF00E676)   // เขียว (ดี)
    value >= 40f -> Color(0xFFFFB300)   // เหลือง (ปานกลาง)
    else -> Color(0xFFFF1744)           // แดง (วิกฤต)
}

private fun stressBarColor(value: Float): Color = when {
    value <= 30f -> Color(0xFF00E676)   // เขียว (เครียดน้อย)
    value <= 60f -> Color(0xFFFFB300)   // เหลือง (ปานกลาง)
    else -> Color(0xFFFF1744)           // แดง (เครียดมาก)
}

private fun interactionLabel(type: String): String = when (type) {
    "pet_head" -> "ลูบหัว ✋"
    "poke" -> "จิ้ม 👆"
    "feed" -> "อาหาร 🍖"
    "clean" -> "อาบน้ำ 🧼"
    "play" -> "เล่น 🎾"
    else -> type
}
