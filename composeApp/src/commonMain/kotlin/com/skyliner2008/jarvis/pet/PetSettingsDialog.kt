package com.skyliner2008.jarvis.pet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skyliner2008.jarvis.ui.theme.JarvisTheme

/**
 * PetSettingsDialog — หน้าต่างตั้งค่า Avatar Mode
 *
 * 1. แท็บโปรไฟล์ใบหน้า (Face Profiles): ลงทะเบียน 5 สล็อต, ตรวจสอบและแยกแยะใบหน้าได้จริง
 * 2. แท็บแสดงผล & ตรวจจับ (Display & Vision): เปิด/ปิด Debug HUD, Desk Sentry, สลับกล้องหน้า/หลัง
 */
@Composable
fun PetSettingsDialog(
    onDismissRequest: () -> Unit,
    showDebugHud: Boolean,
    onToggleDebugHud: (Boolean) -> Unit,
    faceProfiles: List<PetFaceProfile>,
    currentRecognizedPerson: String?,
    hasDetectedFace: Boolean,
    onStartEnrollment: (slotIndex: Int, name: String) -> Unit,
    onDeleteFace: (slotIndex: Int) -> Unit,
    onRenameFace: (slotIndex: Int, newName: String) -> Unit,
    isSentryActive: Boolean = false,
    onToggleSentry: () -> Unit = {},
    isFrontCam: Boolean = true,
    onToggleCamera: () -> Unit = {},
    /** the slot of the face in front of the camera, -1 = no enrolled match ([PetModeController.currentRecognizedSlot]) */
    recognizedSlot: Int = -1,
    /** faces are recognized by SFace embedding ([FaceRecognition]) — profiles saved without one need a new scan */
    embeddingAvailable: Boolean = false,
    /** opens the vision test screen ([VisionTestScreen]); null hides its button */
    onOpenVisionTest: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var editingSlotName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "การตั้งค่าโหมด Avatar",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf("👤 โปรไฟล์ใบหน้า", "🖥️ แสดงผล & ตรวจจับ")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            onClick = { selectedTab = index },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) Color(0xFF00F0FF).copy(alpha = 0.20f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f)) else null
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> TabFaceProfiles(
                            faceProfiles = faceProfiles,
                            currentRecognizedPerson = currentRecognizedPerson,
                            recognizedSlot = recognizedSlot,
                            embeddingAvailable = embeddingAvailable,
                            hasDetectedFace = hasDetectedFace,
                            onStartEnrollment = onStartEnrollment,
                            onDeleteFace = onDeleteFace,
                            onStartRename = { idx, name ->
                                editingSlotIndex = idx
                                editingSlotName = name
                            }
                        )
                        1 -> TabDisplayAndVision(
                            showDebugHud = showDebugHud,
                            onToggleDebugHud = onToggleDebugHud,
                            isSentryActive = isSentryActive,
                            onToggleSentry = onToggleSentry,
                            isFrontCam = isFrontCam,
                            onToggleCamera = onToggleCamera,
                            onOpenVisionTest = onOpenVisionTest
                        )
                    }
                }
            }
        }
    }

    // Modal: Rename Slot
    if (editingSlotIndex != null) {
        Dialog(onDismissRequest = { editingSlotIndex = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "แก้ไขชื่อสล็อตที่ ${(editingSlotIndex ?: 0) + 1}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingSlotName,
                        onValueChange = { editingSlotName = it },
                        singleLine = true,
                        placeholder = { Text("ใส่ชื่อ เช่น บอส, คุณแม่, เพื่อน", color = Color.Gray, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingSlotIndex = null }) {
                            Text("ยกเลิก", color = Color.Gray, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                editingSlotIndex?.let { idx ->
                                    onRenameFace(idx, editingSlotName.trim())
                                }
                                editingSlotIndex = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                        ) {
                            Text("บันทึกชื่อ", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── TAB 0: Face Profiles ──────────────────────────────────────────────────
@Composable
private fun TabFaceProfiles(
    faceProfiles: List<PetFaceProfile>,
    currentRecognizedPerson: String?,
    recognizedSlot: Int,
    embeddingAvailable: Boolean,
    hasDetectedFace: Boolean,
    onStartEnrollment: (slotIndex: Int, name: String) -> Unit,
    onDeleteFace: (slotIndex: Int) -> Unit,
    onStartRename: (slotIndex: Int, currentName: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Recognition Live Status Card
        // by slot, not by the label: "ตรวจพบใบหน้า (ยังไม่ลงทะเบียน)" used to show as recognized (green, the text twice)
        val isRecognized = recognizedSlot >= 0
        val statusBgColor = when {
            hasDetectedFace && isRecognized -> Color(0xFF00E676).copy(alpha = 0.15f)
            hasDetectedFace -> Color(0xFFFFB300).copy(alpha = 0.15f)
            else -> Color(0xFF334155).copy(alpha = 0.35f)
        }
        val statusBorderColor = when {
            hasDetectedFace && isRecognized -> Color(0xFF00E676).copy(alpha = 0.6f)
            hasDetectedFace -> Color(0xFFFFB300).copy(alpha = 0.6f)
            else -> Color.White.copy(alpha = 0.15f)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = statusBgColor,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, statusBorderColor)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        hasDetectedFace && isRecognized -> "🟢"
                        hasDetectedFace -> "🟡"
                        else -> "⚪"
                    },
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            hasDetectedFace && isRecognized -> "ตรวจพบใบหน้า: $currentRecognizedPerson"
                            hasDetectedFace -> currentRecognizedPerson ?: "ตรวจพบใบหน้าใหม่ (ยังไม่ได้ลงทะเบียน)"
                            else -> "ยังตรวจไม่พบใบหน้าหน้ากล้อง"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasDetectedFace)
                            "หันหน้าตรงเข้าหากล้อง แล้วกด \"บันทึก\" เพื่อจดจำใบหน้าลงในสล็อต"
                        else
                            "กรุณาหันหน้าเข้าหากล้องในระยะ 30-70 ซม. เพื่อเริ่มการตรวจจับ",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "สล็อตโปรไฟล์ใบหน้า (5 สล็อต)",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (embeddingAvailable)
                "จดจำใบหน้าด้วยโมเดล SFace ในเครื่อง (ไม่ส่งภาพออกนอกเครื่อง) — แยกคนได้แม้ยิ้มหรือพูด ตอนสแกนให้มองตรงในที่สว่าง"
            else
                "ระบบจะคำนวณสัดส่วนเรขาคณิต (Landmarks) และแยกแยะใบหน้าที่มีความใกล้เคียงที่สุดอัตโนมัติ",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        faceProfiles.forEach { profile ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (profile.isEnrolled) Color(0xFF00F0FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (profile.isEnrolled) Color(0xFF00F0FF).copy(alpha = 0.22f) else Color(0xFF334155),
                            border = if (profile.isEnrolled) BorderStroke(1.dp, Color(0xFF00F0FF)) else null,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${profile.slotIndex + 1}",
                                    color = if (profile.isEnrolled) Color(0xFF00F0FF) else Color.Gray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onStartRename(profile.slotIndex, profile.name) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            // saved by the old landmark ratios: the embedding matcher cannot use it until scanned again
                            val needsRescan = profile.isEnrolled && embeddingAvailable && !profile.usesEmbedding
                            Text(
                                text = when {
                                    needsRescan -> "⚠️ บันทึกด้วยระบบเก่า — กด \"สแกนทับ\" เพื่อใช้ระบบจดจำใหม่"
                                    profile.isEnrolled -> "✅ ลงทะเบียนแล้ว"
                                    else -> "⚪ ยังว่าง"
                                },
                                color = when {
                                    needsRescan -> Color(0xFFFFB300)
                                    profile.isEnrolled -> Color(0xFF00E676)
                                    else -> Color.Gray
                                },
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onStartEnrollment(profile.slotIndex, profile.name) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (profile.isEnrolled) Color(0xFF2563EB) else Color(0xFF00F0FF)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (profile.isEnrolled) "สแกนทับ" else "บันทึก",
                                color = if (profile.isEnrolled) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (profile.isEnrolled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { onDeleteFace(profile.slotIndex) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── TAB 1: Display & Vision ───────────────────────────────────────────────
@Composable
private fun TabDisplayAndVision(
    showDebugHud: Boolean,
    onToggleDebugHud: (Boolean) -> Unit,
    isSentryActive: Boolean,
    onToggleSentry: () -> Unit,
    isFrontCam: Boolean,
    onToggleCamera: () -> Unit,
    onOpenVisionTest: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Vision test: the camera with every Bounding Box
        if (onOpenVisionTest != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧪 ทดสอบการมองเห็น (Bounding Box)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ภาพกล้องสด + กรอบใบหน้า มือ (จุด 21 จุด) และสิ่งของ พร้อมคะแนนของแต่ละโมเดล",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onOpenVisionTest,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(text = "เปิด", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Debug HUD Toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E293B).copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "แสดงข้อมูลดีบักบนหน้าจอ (Debug HUD)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "แสดงป้าย [👀 Face tracked] และ [🎯 Detections] มุมบนซ้าย",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = showDebugHud,
                    onCheckedChange = onToggleDebugHud,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00F0FF),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }

        // Desk Sentry Toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E293B).copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ระบบเฝ้าโต๊ะ (Desk Sentry)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ส่งเสียงแจ้งเตือนเมื่อตรวจพบคนแปลกหน้าเข้าใกล้โต๊ะ",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = isSentryActive,
                    onCheckedChange = { onToggleSentry() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF9100),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }

        // Camera Switcher
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E293B).copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "กล้องที่ใช้งานสำหรับ Vision",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isFrontCam) "📷 กำลังใช้กล้องหน้า (ส่องหน้าผู้ใช้)" else "📷 กำลังใช้กล้องหลัง (ส่องโต๊ะ/ห้อง)",
                        color = if (isFrontCam) Color(0xFF00F0FF) else Color(0xFFFF9100),
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onToggleCamera,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("สลับกล้อง", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
