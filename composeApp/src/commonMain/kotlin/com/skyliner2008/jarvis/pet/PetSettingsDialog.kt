package com.skyliner2008.jarvis.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEngineType
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation
import com.skyliner2008.jarvis.ui.component.avatar.DynamicVectorProp
import com.skyliner2008.jarvis.ui.component.avatar.PropPosition
import com.skyliner2008.jarvis.ui.component.avatar.PropType

/**
 * PetSettingsDialog — เมนูตั้งค่าระบบสัตว์เลี้ยงครบวงจร
 *
 * 1. แท็บหน้าจอ & ดีบัก: เปิด/ปิด HUD ข้อมูลเซนเซอร์ + ตั้งเวลา Idle Screensaver Trick + เอนจิน Avatar (Canvas / Rive)
 * 2. แท็บจดจำใบหน้า: จัดการ 5 สล็อตใบหน้า (สแกนจำหน้าเจ้านาย, ลบ, แก้ไขชื่อ)
 * 3. แท็บจิตวิทยา & การดูแล: หลอดค่าความต้องการ (อิ่ม, พลัง, สะอาด, สุข, เครียด) + ปุ่มดูแลด่วน
 * 4. แท็บคลังพร็อพ & ธีมฉาก: ตัวอย่างธีมฉากหลัง 8 รูปแบบ + พร็อพ/สติกเกอร์ 54 ชนิด + Dynamic SVG Parser
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetSettingsDialog(
    onDismissRequest: () -> Unit,
    showDebugHud: Boolean,
    onToggleDebugHud: (Boolean) -> Unit,
    avatarEngineType: AvatarEngineType = AvatarEngineType.COMPOSE_CANVAS,
    onSelectAvatarEngine: (AvatarEngineType) -> Unit = {},
    screensaverDelaySeconds: Int,
    onSetScreensaverDelay: (Int) -> Unit,
    faceProfiles: List<PetFaceProfile>,
    currentRecognizedPerson: String?,
    hasDetectedFace: Boolean,
    onEnrollFace: (slotIndex: Int, name: String) -> Boolean,
    onDeleteFace: (slotIndex: Int) -> Unit,
    onRenameFace: (slotIndex: Int, newName: String) -> Unit,
    needsState: PetNeedsState,
    onFeed: () -> Unit,
    onClean: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    currentTheme: BackgroundTheme = BackgroundTheme.DEFAULT,
    onSelectTheme: (BackgroundTheme) -> Unit = {},
    activeProps: List<PropType> = emptyList(),
    onToggleProp: (PropType) -> Unit = {},
    onClearProps: () -> Unit = {},
    customProps: List<DynamicVectorProp> = emptyList(),
    onRemoveCustomProp: (String) -> Unit = {},
    onAddCustomProp: (DynamicVectorProp) -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var editingSlotName by remember { mutableStateOf("") }
    var enrollSuccessMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF141824),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ─── Dialog Header ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐾", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ตั้งค่าโหมดสัตว์เลี้ยง",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ─── Tab Row ───
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color(0xFF1E2436),
                    contentColor = Color(0xFF00F0FF),
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                "ดีบัก",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                "จำหน้า",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            Text(
                                "🎨 พร็อพ & ธีม",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Tab Content ───
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTabIndex) {
                        0 -> TabDisplayAndScreensaver(
                            showDebugHud = showDebugHud,
                            onToggleDebugHud = onToggleDebugHud,
                            screensaverDelaySeconds = screensaverDelaySeconds,
                            onSetScreensaverDelay = onSetScreensaverDelay,
                            avatarEngineType = avatarEngineType,
                            onSelectAvatarEngine = onSelectAvatarEngine
                        )
                        1 -> TabFaceProfiles(
                            faceProfiles = faceProfiles,
                            currentRecognizedPerson = currentRecognizedPerson,
                            hasDetectedFace = hasDetectedFace,
                            onEnrollFace = { idx, name ->
                                val success = onEnrollFace(idx, name)
                                if (success) {
                                    enrollSuccessMessage = "บันทึกใบหน้าลงในสล็อต ${idx + 1} เรียบร้อยแล้ว! ✨"
                                }
                                success
                            },
                            onDeleteFace = onDeleteFace,
                            onStartRename = { idx, curName ->
                                editingSlotIndex = idx
                                editingSlotName = curName
                            }
                        )
                        2 -> TabPropsAndThemesCatalog(
                            currentTheme = currentTheme,
                            onSelectTheme = onSelectTheme,
                            activeProps = activeProps,
                            onToggleProp = onToggleProp,
                            onClearProps = onClearProps,
                            customProps = customProps,
                            onRemoveCustomProp = onRemoveCustomProp,
                            onAddCustomProp = onAddCustomProp
                        )
                    }
                }
            }
        }
    }

    // ─── Rename Face Slot Modal Dialog ───
    if (editingSlotIndex != null) {
        AlertDialog(
            onDismissRequest = { editingSlotIndex = null },
            title = {
                Text(
                    "ตั้งชื่อใบหน้า (สล็อต ${(editingSlotIndex ?: 0) + 1})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "ระบุชื่อบุคคลที่สัตว์เลี้ยงจะจดจำและทักทาย เช่น บอส, แม่, น้องมุก",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingSlotName,
                        onValueChange = { editingSlotName = it },
                        label = { Text("ชื่อบุคคล") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = editingSlotIndex ?: return@Button
                        onRenameFace(idx, editingSlotName.trim())
                        editingSlotIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("บันทึก", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSlotIndex = null }) {
                    Text("ยกเลิก", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E2436)
        )
    }

    // ─── Enroll Success Snackbar / Dialog ───
    if (enrollSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { enrollSuccessMessage = null },
            title = { Text("🎉 สำเร็จ", color = Color(0xFF00F0FF)) },
            text = { Text(enrollSuccessMessage ?: "", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { enrollSuccessMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("ตกลง", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E2436)
        )
    }
}

// ─── TAB 1: Display & Screensaver ─────────────────────────────────────────────
@Composable
private fun TabDisplayAndScreensaver(
    showDebugHud: Boolean,
    onToggleDebugHud: (Boolean) -> Unit,
    screensaverDelaySeconds: Int,
    onSetScreensaverDelay: (Int) -> Unit,
    avatarEngineType: AvatarEngineType = AvatarEngineType.COMPOSE_CANVAS,
    onSelectAvatarEngine: (AvatarEngineType) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Avatar Engine Selector
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "เอนจินเรนเดอร์ Avatar (Rendering Engine)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "เลือกรูปแบบการประมวลผลกราฟิกหุ่นยนต์ AI",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCanvas = avatarEngineType == AvatarEngineType.COMPOSE_CANVAS
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectAvatarEngine(AvatarEngineType.COMPOSE_CANVAS) },
                        color = if (isCanvas) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFF151928),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCanvas) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎨 Compose Canvas", color = if (isCanvas) Color(0xFF00F0FF) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("50 LOOI Moodsets / Zero Binary", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }

                    val isRive = avatarEngineType == AvatarEngineType.RIVE_STATE_MACHINE
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectAvatarEngine(AvatarEngineType.RIVE_STATE_MACHINE) },
                        color = if (isRive) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFF151928),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRive) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("⚡ Rive (.riv)", color = if (isRive) Color(0xFF00F0FF) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("State Machine / 60fps Native", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Debug HUD Toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "แสดงข้อมูลดีบักบนหน้าจอ (Debug HUD)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "แสดงป้ายตรวจจับกล้อง สแกนใบหน้า และท่าทางมือ (ซ่อนเป็นค่าเริ่มต้น)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = showDebugHud,
                    onCheckedChange = onToggleDebugHud,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00F0FF),
                        uncheckedTrackColor = Color(0xFF2C344D)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Screensaver Idle Tricks Setting
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "เวลาเริ่มเล่นลูกเล่นตาพักหน้าจอ (Idle Screensaver)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "เมื่อปล่อยเครื่องทิ้งไว้ น้องจะเล่นลูกเล่นตา: ปิงปองเด้งขอบจอ, กระโดดจนเหนื่อย, แทงสนุ๊กเกอร์",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { sec ->
                        val isSelected = screensaverDelaySeconds == sec
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetScreensaverDelay(sec) },
                            color = if (isSelected) Color(0xFF00F0FF).copy(alpha = 0.18f) else Color(0xFF2C344D),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00F0FF)) else null
                        ) {
                            Text(
                                text = "$sec วินาที",
                                color = if (isSelected) Color(0xFF00F0FF) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound & Physics Guide
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✨ ระบบแอนิเมชันลูกบอลยาง (Squash and Stretch)",
                    color = Color(0xFF00F0FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• ดวงตามีความนุ่มนิ่ม ยืดหดตามแรงเด้งดึ๋งเมื่อกะพริบตาและหายใจ\n• คิ้วและปากจะถูกซ่อนอัตโนมัติในโหมดปกติเพื่อให้หน้าตาดูน่ารักเป็นธรรมชาติ\n• แตะสัมผัสหรือพูดคุยเพื่อยกเลิกลูกเล่นตาพักหน้าจอได้ทันที",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── TAB 2: Face Profiles (5 Slots) ───────────────────────────────────────────
@Composable
private fun TabFaceProfiles(
    faceProfiles: List<PetFaceProfile>,
    currentRecognizedPerson: String?,
    hasDetectedFace: Boolean,
    onEnrollFace: (slotIndex: Int, name: String) -> Boolean,
    onDeleteFace: (slotIndex: Int) -> Unit,
    onStartRename: (slotIndex: Int, currentName: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Recognition Status Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (hasDetectedFace) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF9100).copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasDetectedFace) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9100).copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (hasDetectedFace) "👁️" else "🔍", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (hasDetectedFace) "ตรวจพบใบหน้า: ${currentRecognizedPerson ?: "บุคคลไม่ระบุชื่อ"}" else "ยังตรวจไม่พบใบหน้าหน้ากล้อง",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasDetectedFace) "หันหน้าตรงเพื่อบันทึกโครงสร้างใบหน้าลงสล็อต" else "กรุณาหันหน้าเข้าหากล้องในระยะที่พอดี",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5 Slots List
        Text(
            text = "สล็อตจดจำใบหน้า (5 สล็อต)",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        faceProfiles.forEach { profile ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = Color(0xFF1E2436),
                shape = RoundedCornerShape(12.dp)
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
                            color = if (profile.isEnrolled) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFF2C344D),
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
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onStartRename(profile.slotIndex, profile.name) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (profile.isEnrolled) "✅ บันทึกแล้ว" else "⚪ ยังว่าง",
                                color = if (profile.isEnrolled) Color(0xFF00E676) else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Enroll Button
                        Button(
                            onClick = { onEnrollFace(profile.slotIndex, profile.name) },
                            enabled = hasDetectedFace,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00F0FF),
                                disabledContainerColor = Color(0xFF2C344D)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (profile.isEnrolled) "สแกนทับ" else "บันทึก",
                                color = if (hasDetectedFace) Color.Black else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (profile.isEnrolled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDeleteFace(profile.slotIndex) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── TAB 3: Pet Needs & Tamagotchi Care ────────────────────────────────────────
@Composable
private fun TabPetNeedsAndCare(
    needsState: PetNeedsState,
    onFeed: () -> Unit,
    onClean: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Psychological Relationship Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💖 ระดับความผูกพัน (Affection)",
                        color = Color(0xFFFF4081),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lv. ${needsState.affectionLevel}/10 (${needsState.affectionLevelName()})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (needsState.affectionLevel / 10f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFFF4081),
                    trackColor = Color(0xFF2C344D)
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("บุคลิกภาพ", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = if (needsState.isHyper) "⚡ ไฮเปอร์ คึกคัก" else "🌿 สงบนิ่ง อ่อนโยน",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column {
                        Text("พฤติกรรม", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = if (needsState.isClingy) "🐾 ขี้อ้อน ชอบคลอเคลีย" else " 독립 รักอิสระ มั่นใจ",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column {
                        Text("ความเชื่อฟัง", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Text(
                            text = "${(needsState.obedience * 100).toInt()}%",
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Physical Needs Bars
        Text(
            text = "ความต้องการทางกายภาพ (Physical Needs)",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E2436),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeedBarItem("🍖 ความอิ่ม (Satiety)", needsState.satiety, Color(0xFFFFB300))
                NeedBarItem("⚡ พลังงาน (Energy)", needsState.energy, Color(0xFF00F0FF))
                NeedBarItem("🧼 ความสะอาด (Hygiene)", needsState.hygiene, Color(0xFF00E676))
                NeedBarItem("😊 ความสุข (Happiness)", needsState.happiness, Color(0xFFFF4081))
                NeedBarItem("💢 ความเครียด (Stress)", needsState.stress, Color(0xFFFF1744), isNegative = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Care Action Buttons
        Text(
            text = "กิจกรรมดูแลสัตว์เลี้ยง (Pet Care Actions)",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CareActionButton("🍖 ให้อาหาร", Color(0xFFFFB300), Modifier.weight(1f), onFeed)
            CareActionButton("🧼 อาบน้ำ", Color(0xFF00E676), Modifier.weight(1f), onClean)
            CareActionButton("🎾 ชวนเล่น", Color(0xFF00F0FF), Modifier.weight(1f), onPlay)
            CareActionButton("💤 นอนพัก", Color(0xFF7986CB), Modifier.weight(1f), onSleep)
        }
    }
}

@Composable
private fun NeedBarItem(
    label: String,
    value: Float,
    barColor: Color,
    isNegative: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            Text(
                "${value.toInt()}%",
                color = if (isNegative && value > 60f) Color(0xFFFF1744) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF2C344D)
        )
    }
}

@Composable
private fun CareActionButton(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = Color(0xFF1E2436),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── TAB 4: PROPS & THEMES CATALOG (ตัวอย่างฉากหลัง & พร็อพ 54 ชนิด & SVG Parser) ─
// ═══════════════════════════════════════════════════════════════════════════════

private data class PropItemInfo(
    val prop: PropType,
    val emoji: String,
    val nameThai: String,
    val category: String,
    val description: String
)

private val ALL_PROPS_INFO = listOf(
    // ─── อารมณ์ (17 ชนิด) ───
    PropItemInfo(PropType.HEARTS, "💖", "หัวใจสีชมพู", "อารมณ์", "ลอยขึ้นแสดงความรักและผูกพัน"),
    PropItemInfo(PropType.BROKEN_HEART, "💔", "หัวใจสลาย", "อารมณ์", "เศร้า / อกหัก / ผิดหวัง"),
    PropItemInfo(PropType.SPARKLES, "✨", "ประกายดาว", "อารมณ์", "เปล่งประกายวิบวับรอบศีรษะ"),
    PropItemInfo(PropType.SWEAT_DROP, "💧", "หยดเหงื่อ", "อารมณ์", "เขินอาย / ประหม่า / งานเข้า"),
    PropItemInfo(PropType.QUESTION_MARK, "❓", "เครื่องหมายสงสัย", "อารมณ์", "สงสัย / งงงวย / ฉงนใจ"),
    PropItemInfo(PropType.EXCLAMATION, "❗", "เครื่องหมายตกใจ", "อารมณ์", "ตื่นตัว / แจ้งเตือนด่วน"),
    PropItemInfo(PropType.ZZZZZ, "💤", "Zzz หลับปุ๋ย", "อารมณ์", "ง่วงนอน / โหมดสแตนด์บาย"),
    PropItemInfo(PropType.FIRE, "🔥", "เปลวไฟร้อนแรง", "อารมณ์", "ไฟลุก / มุ่งมั่นขั้นสุด"),
    PropItemInfo(PropType.ANGER_VEIN, "💢", "เส้นเลือดปูด", "อารมณ์", "โมโห / หงุดหงิดนิดๆ"),
    PropItemInfo(PropType.TEARS, "😭", "น้ำตาไหลพราก", "อารมณ์", "ซาบซึ้ง / ร้องไห้เสียใจ"),
    PropItemInfo(PropType.DIZZY_STARS, "💫", "ดาวหมุนวน", "อารมณ์", "มึนหัว / ตาลาย / เขย่าแรง"),
    PropItemInfo(PropType.SKULL, "💀", "หัวกะโหลก", "อารมณ์", "เหนื่อยล้าหมดแรง"),
    PropItemInfo(PropType.LIGHTBULB, "💡", "หลอดไฟไอเดีย", "อารมณ์", "คิดออก / ปิ๊งแว๊บฉับไว"),
    PropItemInfo(PropType.PARTY_POPPER, "🎉", "พลุกระดาษ", "อารมณ์", "เฉลิมฉลอง / ยินดีด้วยนะ"),
    PropItemInfo(PropType.BALLOONS, "🎈", "ลูกโป่งหลากสี", "อารมณ์", "สดใส / ร่าเริงเบิกบาน"),
    PropItemInfo(PropType.CROWN, "👑", "มงกุฎทองคำ", "อารมณ์", "ราชา / บอสผู้ยิ่งใหญ่"),
    PropItemInfo(PropType.SUNGLASSES, "😎", "แว่นกันแดด", "อารมณ์", "สุดคูล / เท่เกินต้าน"),

    // ─── อาหาร & ไลฟ์สไตล์ (13 ชนิด) ───
    PropItemInfo(PropType.COFFEE, "☕", "กาแฟร้อน", "อาหาร", "เริ่มต้นวันใหม่ควันฉุย"),
    PropItemInfo(PropType.TEA_CUP, "🍵", "ถ้วยชาเขียว", "อาหาร", "ผ่อนคลายจิตใจในยามบ่าย"),
    PropItemInfo(PropType.BEER, "🍺", "เบียร์ชนแก้ว", "อาหาร", "ปาร์ตี้ Cheers สนุกสนาน"),
    PropItemInfo(PropType.PIZZA, "🍕", "พิซซ่าชีส", "อาหาร", "ของว่างชีสยืดแสนอร่อย"),
    PropItemInfo(PropType.BURGER, "🍔", "เบอร์เกอร์", "อาหาร", "มื้อด่วนจุใจชิ้นโต"),
    PropItemInfo(PropType.CAKE, "🎂", "เค้กวันเกิด", "อาหาร", "สุขสันต์วันพิเศษ"),
    PropItemInfo(PropType.ICE_CREAM, "🍦", "ไอศกรีมโคน", "อาหาร", "หวานเย็นสดชื่นดับร้อน"),
    PropItemInfo(PropType.POPCORN, "🍿", "ถังป๊อปคอร์น", "อาหาร", "ดูหนังหรือซีรีส์เพลินๆ"),
    PropItemInfo(PropType.BOBA_TEA, "🧋", "ชานมไข่มุก", "อาหาร", "เครื่องดื่มแก้วโปรด"),
    PropItemInfo(PropType.GIFT, "🎁", "กล่องของขวัญ", "อาหาร", "เซอร์ไพรส์คนพิเศษ"),
    PropItemInfo(PropType.GAMING_CONTROLLER, "🎮", "จอยเกม", "อาหาร", "เวลาสนุกกับเกม"),
    PropItemInfo(PropType.BOOK, "📖", "หนังสือเรียนรู้", "อาหาร", "อ่านหนังสือและหาความรู้"),
    PropItemInfo(PropType.MUSIC_NOTES, "🎵", "โน้ตดนตรี", "อาหาร", "เสียงเพลงบรรเลงไพเราะ"),

    // ─── ธรรมชาติ & อากาศ (10 ชนิด) ───
    PropItemInfo(PropType.SUN, "☀️", "พระอาทิตย์", "ธรรมชาติ", "แสงแดดเจิดจ้าสดใส"),
    PropItemInfo(PropType.RAINBOW, "🌈", "สายรุ้งงาม", "ธรรมชาติ", "สายรุ้งหลังฝนซา"),
    PropItemInfo(PropType.UMBRELLA, "☂️", "ร่มกันฝน", "ธรรมชาติ", "พร้อมรับมือกับสายฝน"),
    PropItemInfo(PropType.CLOUD, "☁️", "ก้อนเมฆนุ่ม", "ธรรมชาติ", "ลอยละล่องบนท้องฟ้า"),
    PropItemInfo(PropType.SNOW, "❄️", "เกล็ดหิมะ", "ธรรมชาติ", "หนาวเย็นยะเยือกจับใจ"),
    PropItemInfo(PropType.LIGHTNING, "⚡", "สายฟ้าฟาด", "ธรรมชาติ", "พลังงานล้นเหลือแปลบปลาบ"),
    PropItemInfo(PropType.LEAF, "🍃", "ใบไม้ปลิว", "ธรรมชาติ", "สายลมแห่งธรรมชาติ"),
    PropItemInfo(PropType.CHERRY_BLOSSOM, "🌸", "ดอกซากุระ", "ธรรมชาติ", "กลีบซากุระฤดูใบไม้ผลิ"),
    PropItemInfo(PropType.MOON_STARS, "🌙", "พระจันทร์เสี้ยว", "ธรรมชาติ", "ค่ำคืนอันเงียบสงบ"),
    PropItemInfo(PropType.GHOST, "👻", "ผีน้อยน่ารัก", "ธรรมชาติ", "หยอกล้อขี้เล่นกุ๊กกู๋"),

    // ─── ไอที & เครื่องมือ (14 ชนิด) ───
    PropItemInfo(PropType.ROCKET, "🚀", "จรวดทะยาน", "ไอที", "รวดเร็วสู่เป้าหมาย"),
    PropItemInfo(PropType.GEARS, "⚙️", "ฟันเฟืองกลไก", "ไอที", "ระบบกำลังคิดคำนวณ"),
    PropItemInfo(PropType.LAPTOP, "💻", "คอมพิวเตอร์", "ไอที", "ลุยงานโค้ดดิ้งเต็มที่"),
    PropItemInfo(PropType.BATTERY_CHARGING, "⚡🔋", "ชาร์จแบตเตอรี่", "ไอที", "กำลังเติมพลังไฟ"),
    PropItemInfo(PropType.BATTERY_LOW, "🪫", "แบตเตอรี่ต่ำ", "ไอที", "ต้องการการชาร์จไฟด่วน"),
    PropItemInfo(PropType.CLOCK_ALARM, "⏰", "นาฬิกาปลุก", "ไอที", "เตือนความจำตรงเวลา"),
    PropItemInfo(PropType.MAGNIFYING_GLASS, "🔍", "แว่นขยาย", "ไอที", "สแกนค้นหาข้อมูลเชิงลึก"),
    PropItemInfo(PropType.SHIELD, "🛡️", "โล่ป้องกัน", "ไอที", "ระบบความปลอดภัยไซเบอร์"),
    PropItemInfo(PropType.WARNING_TRIANGLE, "⚠️", "ป้ายเตือนภัย", "ไอที", "ตรวจพบสิ่งผิดปกติ"),
    PropItemInfo(PropType.SIREN_LIGHT, "🚨", "ไซเรนฉุกเฉิน", "ไอที", "แจ้งเตือนระดับวิกฤต"),
    PropItemInfo(PropType.CHECK_MARK, "✅", "เครื่องหมายถูก", "ไอที", "งานสำเร็จเรียบร้อยดี"),
    PropItemInfo(PropType.TARGET_RETICLE, "🎯", "เป้าเล็งโฟกัส", "ไอที", "ล็อกเป้าหมายแม่นยำ"),
    PropItemInfo(PropType.CAT_PAW, "🐾", "รอยเท้าแมว", "ไอที", "อุ้งเท้าน่ารักตะปบใจ"),
    PropItemInfo(PropType.GOLD_COIN, "🪙", "เหรียญทองคำ", "ไอที", "ความมั่งคั่งและโชคลาภ"),
    PropItemInfo(PropType.RAIN_DROPS, "💧", "หยาดฝน", "ไอที", "หยดน้ำฝนโปรยปราย")
)

private data class ThemeDisplayInfo(
    val theme: BackgroundTheme,
    val icon: String,
    val nameThai: String,
    val description: String,
    val cardColor: Color
)

private val ALL_THEMES_INFO = listOf(
    ThemeDisplayInfo(BackgroundTheme.DEFAULT, "⬛", "Pure Dark OLED", "มืดสนิท ประหยัดพลังงาน แผงวงจร Visor", Color(0xFF141824)),
    ThemeDisplayInfo(BackgroundTheme.RAINY, "🌧️", "ฝนตกโปรยปราย", "ท้องฟ้าครึ้ม เม็ดฝนเคลื่อนไหวตามแรงลม", Color(0xFF182438)),
    ThemeDisplayInfo(BackgroundTheme.SUNNY, "☀️", "แสงแดดอบอุ่น", "ประกายรัศมีแสงอาทิตย์ โทนส้ม-ทองสดใส", Color(0xFF2E2218)),
    ThemeDisplayInfo(BackgroundTheme.NIGHT, "🌌", "ราตรีประดับดาว", "ท้องฟ้าราตรีสีน้ำเงินเข้ม ดวงดาวระยิบระยับ", Color(0xFF101838)),
    ThemeDisplayInfo(BackgroundTheme.SAKURA, "🌸", "สวนซากุระ", "กลีบดอกซากุระสีชมพูร่วงหล่นพลิ้วไหว", Color(0xFF331628)),
    ThemeDisplayInfo(BackgroundTheme.MATRIX, "💻", "ไซเบอร์เรน", "ตัวอักษรสีเขียวนีออน Matrix ไหลอย่างรวดเร็ว", Color(0xFF042212)),
    ThemeDisplayInfo(BackgroundTheme.LOVE_BG, "💖", "ดินแดนหัวใจ", "หัวใจสีชมพูอ่อนลอยฟุ้ง บรรยากาศอบอุ่น", Color(0xFF331024)),
    ThemeDisplayInfo(BackgroundTheme.THUNDER, "⚡", "พายุสายฟ้า", "เมฆฝนฟ้าคะนอง แสงฟ้าแลบแปลบปลาบสะใจ", Color(0xFF222030))
)

@Composable
private fun TabPropsAndThemesCatalog(
    currentTheme: BackgroundTheme,
    onSelectTheme: (BackgroundTheme) -> Unit,
    activeProps: List<PropType>,
    onToggleProp: (PropType) -> Unit,
    onClearProps: () -> Unit,
    customProps: List<DynamicVectorProp>,
    onRemoveCustomProp: (String) -> Unit,
    onAddCustomProp: (DynamicVectorProp) -> Unit
) {
    var subSectionIndex by remember { mutableStateOf(0) } // 0: Themes, 1: Props, 2: SVG Parser
    var propCategoryFilter by remember { mutableStateOf("ทั้งหมด") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ─── Sub-Section Selector ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2436), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val subTabs = listOf(
                "🌌 ธีมฉาก (8)" to 0,
                "✨ พร็อพ (55)" to 1,
                "🪄 เวกเตอร์ SVG" to 2
            )
            subTabs.forEach { (title, idx) ->
                val isSelected = subSectionIndex == idx
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { subSectionIndex = idx },
                    color = if (isSelected) Color(0xFF00F0FF).copy(alpha = 0.20f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF)) else null
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (subSectionIndex) {
            0 -> {
                // ═══════════════════════════════════════════════════════════════
                // SUB 0: BACKGROUND THEMES (8 THEMES)
                // ═══════════════════════════════════════════════════════════════
                Text(
                    "🌌 ธีมฉากหลังรอบตัวหุ่นยนต์ (8 แบบ)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "แตะเพื่อเปลี่ยนบรรยากาศฉากหลัง แสง สี และอนุภาค (Particles) ได้แบบเรียลไทม์",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALL_THEMES_INFO.forEach { info ->
                        val isActive = currentTheme == info.theme
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectTheme(info.theme) },
                            color = info.cardColor,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(info.icon, fontSize = 26.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = info.nameThai,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = info.description,
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Surface(
                                    color = if (isActive) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isActive) "ใช้งานอยู่" else "แตะใส่",
                                        color = if (isActive) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // ═══════════════════════════════════════════════════════════════
                // SUB 1: PROPS & STICKERS (55 BUILT-IN PROPS)
                // ═══════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "✨ คลังอุปกรณ์ & สติกเกอร์ (55 แบบ)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "สวมใส่ได้หลายชิ้นพร้อมกัน ลอยตามหน้าหุ่นยนต์",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    if (activeProps.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClearProps() },
                            color = Color(0xFFFF1744).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744))
                        ) {
                            Text(
                                text = "ล้างทั้งหมด (${activeProps.size})",
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Categories
                val categories = listOf("ทั้งหมด", "อารมณ์", "อาหาร", "ธรรมชาติ", "ไอที")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = propCategoryFilter == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { propCategoryFilter = cat },
                            color = if (isSelected) Color(0xFF00F0FF) else Color(0xFF1E2436),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val filteredProps = ALL_PROPS_INFO.filter {
                    propCategoryFilter == "ทั้งหมด" || it.category == propCategoryFilter
                }

                // Chunk into rows of 2 cards each for clean layout
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredProps.chunked(2).forEach { rowProps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowProps.forEach { info ->
                                val isEquipped = activeProps.contains(info.prop)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onToggleProp(info.prop) },
                                    color = if (isEquipped) Color(0xFF00F0FF).copy(alpha = 0.15f) else Color(0xFF1E2436),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isEquipped) 1.5.dp else 1.dp,
                                        color = if (isEquipped) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(info.emoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = info.nameThai,
                                                color = if (isEquipped) Color(0xFF00F0FF) else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = info.description,
                                                color = Color.White.copy(alpha = 0.55f),
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        }
                                        if (isEquipped) {
                                            Text("✓", color = Color(0xFF00F0FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            if (rowProps.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            2 -> {
                // ═══════════════════════════════════════════════════════════════
                // SUB 2: DYNAMIC SVG PATH PARSER & PRESETS
                // ═══════════════════════════════════════════════════════════════
                Text(
                    "🪄 เสกพร็อพเวกเตอร์อิสระ (Dynamic SVG Path Parser)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Explanatory Card (เงื่อนไข & ขอบเขต)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E2436),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📋 เงื่อนไข & ขอบเขตการทำงาน:",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• รูปแบบ SVG: รองรับ String SVG Path 'd=\"...\"' มาตรฐาน (M, L, C, Q, A, Z)\n" +
                            "• การปรับขนาด: ระบบคำนวณ Path.getBounds() และย่อ/ขยาย (Auto-Fit) ให้อัตโนมัติ โดยหากใส่ขนาด 0dp ที่จุดยึดดวงตา (LEFT_EYE, RIGHT_EYE) ระบบจะขยายขนาดเท่าเส้นผ่านศูนย์กลางดวงตาหุ่นยนต์ 1:1 พอดีเป๊ะ\n" +
                            "• จุดยึดบนใบหน้า (Anchor): 7 ตำแหน่ง ได้แก่ หน้าผาก (FOREHEAD), รอบตาซ้าย/ขวา (LEFT_EYE, RIGHT_EYE), แก้ม (CHEEKS), คาง (CHIN), ลอยซ้าย/ขวา (FLOATING_LEFT, FLOATING_RIGHT)\n" +
                            "• แอนิเมชัน: 5 รูปแบบ ได้แก่ STATIC, FLOAT_BOB (ลอยขึ้นลง), PULSE (เต้นตุบๆ), ROTATE_CONTINUOUS (หมุน 360°), SWAY (แกว่ง)\n" +
                            "• การบันทึกถาวร: พร็อพทุกชิ้นที่ AI เสกหรือสร้างจะถูกบันทึกลงคลังถาวร (SQLite) อัตโนมัติ สามารถหยิบมาใส่ซ้ำได้ทันทีโดยไม่ต้องวาดโค้ด SVG ใหม่",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "⚡ ตัวอย่างพร็อพเวกเตอร์ SVG สำเร็จรูป (กดเพื่อทดสอบเสกทันที):",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Presets
                val presets = listOf(
                    DynamicVectorProp(
                        id = "preset_crown",
                        name = "👑 มงกุฎทองคำ (Golden Crown)",
                        svgPath = "M 5 20 L 5 10 L 10 15 L 15 5 L 20 15 L 25 10 L 25 20 Z",
                        fillColor = "#FFD700",
                        strokeColor = "#FFA000",
                        strokeWidth = 2f,
                        position = PropPosition.FOREHEAD,
                        sizeDp = 56f,
                        animation = DynamicPropAnimation.FLOAT_BOB
                    ),
                    DynamicVectorProp(
                        id = "preset_neon_visor",
                        name = "🕶️ แว่นไซเบอร์นีออน (Cyber Visor)",
                        svgPath = "M 2 8 L 28 8 L 26 18 L 18 18 L 15 12 L 12 18 L 4 18 Z",
                        fillColor = "#00F0FF",
                        strokeColor = "#FFFFFF",
                        strokeWidth = 2f,
                        position = PropPosition.LEFT_EYE,
                        offsetXRatio = 0.45f,
                        sizeDp = 76f,
                        animation = DynamicPropAnimation.STATIC
                    ),
                    DynamicVectorProp(
                        id = "preset_bandage",
                        name = "🩹 พลาสเตอร์แปะแก้ม (Cute Bandage)",
                        svgPath = "M 4 8 C 4 5.8 5.8 4 8 4 L 20 4 C 22.2 4 24 5.8 24 8 C 24 10.2 22.2 12 20 12 L 8 12 C 5.8 12 4 10.2 4 8 Z",
                        fillColor = "#FF8A80",
                        strokeColor = "#FF5252",
                        strokeWidth = 1.5f,
                        position = PropPosition.CHEEKS,
                        sizeDp = 44f,
                        animation = DynamicPropAnimation.STATIC
                    ),
                    DynamicVectorProp(
                        id = "preset_lightning",
                        name = "⚡ สายฟ้านีออน (Neon Bolt)",
                        svgPath = "M 15 2 L 5 16 L 13 16 L 9 26 L 21 12 L 13 12 Z",
                        fillColor = "#FFEA00",
                        strokeColor = "#FFD600",
                        strokeWidth = 1.5f,
                        position = PropPosition.FLOATING_RIGHT,
                        sizeDp = 46f,
                        animation = DynamicPropAnimation.PULSE
                    ),
                    DynamicVectorProp(
                        id = "preset_diving_mask",
                        name = "🤿 หน้ากากดำน้ำ (Diving Mask)",
                        svgPath = "M 4 12 C 4 8 8 6 12 6 C 16 6 18 8 20 8 C 22 8 24 6 28 6 C 32 6 36 8 36 12 C 36 16 32 18 28 18 C 24 18 22 16 20 16 C 18 16 16 18 12 18 C 8 18 4 16 4 12 Z",
                        fillColor = "#00E5FF",
                        strokeColor = "#00B0FF",
                        strokeWidth = 2f,
                        position = PropPosition.CHIN,
                        offsetYRatio = -0.15f,
                        sizeDp = 52f,
                        animation = DynamicPropAnimation.FLOAT_BOB
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        val isEquipped = customProps.any { it.id == preset.id }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isEquipped) {
                                        onRemoveCustomProp(preset.id)
                                    } else {
                                        onAddCustomProp(preset)
                                    }
                                },
                            color = if (isEquipped) Color(0xFF00F0FF).copy(alpha = 0.18f) else Color(0xFF1E2436),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isEquipped) 1.5.dp else 1.dp,
                                color = if (isEquipped) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.10f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        color = if (isEquipped) Color(0xFF00F0FF) else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "จุดยึด: ${preset.position.name} • แอนิเมชัน: ${preset.animation.name} • สี: ${preset.fillColor}",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 9.sp
                                    )
                                }
                                Surface(
                                    color = if (isEquipped) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isEquipped) "ถอดออก" else "เสกใส่เลย ✨",
                                        color = if (isEquipped) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Custom Props count & list
                if (customProps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "📌 พร็อพเวกเตอร์ที่กำลังแสดงผลอยู่ (${customProps.size} ชิ้น):",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customProps.forEach { cp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF141824), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cp.name} [${cp.position.name}]",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp
                                )
                                IconButton(
                                    onClick = { onRemoveCustomProp(cp.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Persistent Saved Props Library (SQLite)
                val savedCustomProps by PetCustomPropStore.savedCustomProps.collectAsState()
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "💾 คลังพร็อพเวกเตอร์ที่บันทึกถาวร (${savedCustomProps.size} ชิ้น):",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (savedCustomProps.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF141824),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ยังไม่มีพร็อพที่บันทึกถาวร เมื่อ AI เสกพร็อพใหม่ ระบบจะจัดเก็บไว้ที่นี่อัตโนมัติเพื่อให้หยิบมาใช้ซ้ำได้ทันที",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedCustomProps.forEach { sp ->
                            val isCurrentlyEquipped = customProps.any { it.id == sp.id || it.name.equals(sp.name, ignoreCase = true) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF141824), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${sp.name} [${sp.position.name}]",
                                        color = if (isCurrentlyEquipped) Color(0xFF00F0FF) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ขนาด: ${if (sp.sizeDp <= 0f) "Auto-Fit ตา 1:1" else "${sp.sizeDp.toInt()}dp"} • แอนิเมชัน: ${sp.animation.name}",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 8.5.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                if (isCurrentlyEquipped) {
                                                    onRemoveCustomProp(sp.id)
                                                } else {
                                                    onAddCustomProp(sp)
                                                }
                                            },
                                        color = if (isCurrentlyEquipped) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isCurrentlyEquipped) "ถอด" else "สวมใส่",
                                            color = if (isCurrentlyEquipped) Color(0xFF00F0FF) else Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { PetCustomPropStore.deleteCustomProp(sp.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete from vault",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

