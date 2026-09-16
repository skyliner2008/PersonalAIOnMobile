package com.skyliner2008.jarvis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import com.skyliner2008.jarvis.data.MeetingRecord
import com.skyliner2008.jarvis.controller.SpecialistSessionController
import com.skyliner2008.jarvis.data.LiveSpecialistService
import com.skyliner2008.jarvis.ui.theme.JarvisTheme
import kotlinx.datetime.toLocalDateTime

/**
 * หน้าบันทึกการประชุม — ถอดเสียงเรียลไทม์ด้วย gemini-3.5-transcribe-live
 *
 * แบ่งหน้าจอเป็นสองส่วน:
 *  - ส่วนบน: บทประชุมที่กำลังถอดอยู่ (เฉพาะเสียงในห้อง)
 *  - ส่วนล่าง: แชทถาม-ตอบกับผู้ช่วย พร้อมช่องพิมพ์ ปุ่มส่ง และปุ่มไมค์
 *
 * ขอบเขตตามความสามารถจริงของโมเดล:
 * - ถอดเสียงสดหลายภาษา สลับภาษากลางประโยคได้ จัดวรรคตอนอัตโนมัติ (โหมด SMART)
 * - แยกเสียงผู้พูดไม่ได้ในโหมดสด (diarization มีเฉพาะการประมวลผลไฟล์)
 * - หนึ่ง session ยาวได้ 10 นาที ระบบต่อ session ใหม่ให้เองทุก 8 นาที
 *
 * ถาม AI ได้ 2 ทาง — พิมพ์ (ตอบเป็นข้อความ บันทึกยังเดินต่อ)
 * หรือกดไมค์ (พักบันทึกอัตโนมัติ ผู้ช่วยตอบเป็นเสียงและคุยต่อได้ จนกดเลิกพัก)
 * ทั้งสองทางแนบบทประชุมจนถึงตอนนั้นไปด้วยเสมอ ผู้ช่วยจึงรู้ว่ากำลังคุยเรื่องอะไรอยู่
 */
@Composable
fun MeetingScreen(
    controller: SpecialistSessionController,
    onClose: () -> Unit
) {
    val segments by controller.segments.collectAsState()
    val state by controller.state.collectAsState()
    val isPaused by controller.isPaused.collectAsState()
    val elapsed by controller.elapsedMs.collectAsState()
    val level by controller.audioLevel.collectAsState()
    val summary by controller.summary.collectAsState()
    val isSummarizing by controller.isSummarizing.collectAsState()
    val isAsking by controller.isAsking.collectAsState()
    val voiceAsk by controller.voiceAskActive.collectAsState()
    val mode by controller.mode.collectAsState()
    val records by controller.records.collectAsState()
    val lastSavedId by controller.lastSavedRecordId.collectAsState()

    // 0 = กำลังประชุม, 1 = สรุปประชุมที่เก็บไว้
    var tab by remember { mutableStateOf(0) }
    var openedRecordId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { controller.loadRecords() }
    // สรุปเสร็จแล้วพาไปดูผลทันที (เดิมกล่องสรุปโผล่ทับปุ่มควบคุมจนกดอะไรไม่ได้)
    LaunchedEffect(lastSavedId) {
        lastSavedId?.let {
            tab = 1
            openedRecordId = it
        }
    }

    val meetingLines = segments.filter { it.kind != LiveSpecialistService.TranscriptKind.ASSISTANT }
    val assistantLines = segments.filter { it.kind == LiveSpecialistService.TranscriptKind.ASSISTANT }

    var question by remember { mutableStateOf("") }
    val transcriptState = rememberLazyListState()
    val chatState = rememberLazyListState()
    LaunchedEffect(meetingLines.size) {
        if (meetingLines.isNotEmpty()) transcriptState.animateScrollToItem(meetingLines.size - 1)
    }
    LaunchedEffect(assistantLines.size) {
        if (assistantLines.isNotEmpty()) chatState.animateScrollToItem(assistantLines.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisTheme.Dark)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("🎙️ บันทึกการประชุม", color = JarvisTheme.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(formatElapsed(elapsed), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        }

        Spacer(Modifier.height(6.dp))
        StatusLine(state = state, isPaused = isPaused, running = mode != null, level = level)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MeetingTab("ประชุม", selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            MeetingTab(
                if (records.isEmpty()) "สรุปประชุม" else "สรุปประชุม (${records.size})",
                selected = tab == 1,
                modifier = Modifier.weight(1f)
            ) { tab = 1 }
        }

        if (tab == 1) {
            MeetingRecordsPane(
                records = records,
                openedRecordId = openedRecordId,
                onOpen = { openedRecordId = if (openedRecordId == it) null else it },
                onPlay = { controller.readRecordAloud(it) },
                onStopPlay = { controller.stopReadAloud() },
                onDelete = {
                    if (openedRecordId == it) openedRecordId = null
                    controller.deleteRecord(it)
                },
                onClose = onClose
            )
            return@Column
        }

        if (isSummarizing) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = JarvisTheme.Cyan, strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("กำลังสรุปการประชุม…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }

        // ── ส่วนบน: บทประชุม ──────────────────────────────────────
        Spacer(Modifier.height(10.dp))
        Text("บทประชุม", color = JarvisTheme.Cyan.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f)
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisTheme.Surface)
                .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (meetingLines.isEmpty()) {
                Text(
                    "ยังไม่มีข้อความ — กดเริ่มบันทึกแล้วพูดได้เลย\n(ถอดเสียงสดหลายภาษา แต่ยังแยกเสียงผู้พูดไม่ได้)",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(state = transcriptState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(meetingLines) { seg ->
                        Column {
                            Text(
                                formatClock(seg.startedAtMs),
                                color = JarvisTheme.Cyan.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                            Text(seg.text, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // ── ส่วนล่าง: แชทกับผู้ช่วย ────────────────────────────────
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ถามผู้ช่วย", color = JarvisTheme.Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(
                "ผู้ช่วยเห็นบทประชุมทั้งหมด",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisTheme.Card)
                .border(1.dp, JarvisTheme.Purple.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (assistantLines.isEmpty()) {
                Text(
                    "พิมพ์คำถามแล้วกดส่ง (ตอบเป็นข้อความ บันทึกยังเดินต่อ)\n" +
                        "หรือกดปุ่มไมค์เพื่อถามด้วยเสียง — ระบบจะพักบันทึกให้อัตโนมัติ",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(state = chatState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(assistantLines) { seg ->
                        val isQuestion = seg.text.startsWith("❓") || seg.text.startsWith("🎤")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isQuestion) Color.White.copy(alpha = 0.06f)
                                    else JarvisTheme.Purple.copy(alpha = 0.18f)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                if (isQuestion) "คุณ" else "🤖 ผู้ช่วย",
                                color = if (isQuestion) Color.White.copy(alpha = 0.6f) else JarvisTheme.Purple,
                                fontSize = 11.sp
                            )
                            Text(seg.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }


        // ── ช่องพิมพ์ + ปุ่มส่ง + ปุ่มไมค์ ─────────────────────────
        if (mode == LiveSpecialistService.Mode.MEETING) {
            Spacer(Modifier.height(10.dp))
            if (voiceAsk) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(JarvisTheme.Purple.copy(alpha = 0.22f))
                        .padding(12.dp)
                ) {
                    Text(
                        "🎤 กำลังคุยกับผู้ช่วยด้วยเสียง — บันทึกการประชุมพักอยู่",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ถามต่อด้วยเสียงได้เรื่อยๆ ผู้ช่วยใช้เครื่องมือได้ครบ กดเลิกพักเมื่อจะกลับไปบันทึกต่อ",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { controller.endVoiceAsk() },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Green),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶ เลิกพัก — กลับไปบันทึกประชุมต่อ", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("ถามผู้ช่วยเกี่ยวกับสิ่งที่ประชุมอยู่", fontSize = 13.sp)
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = JarvisTheme.Card,
                            unfocusedContainerColor = JarvisTheme.Card,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.size(6.dp))
                    IconButton(
                        onClick = {
                            controller.askDuringSession(question)
                            question = ""
                        },
                        enabled = !isAsking && question.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = JarvisTheme.Purple)
                    ) {
                        if (isAsking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, "ส่งคำถาม", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    // ปุ่มไมค์: พักบันทึกแล้วคุยกับผู้ช่วยด้วยเสียง
                    IconButton(
                        onClick = {
                            controller.askByVoice(question)
                            question = ""
                        },
                        enabled = !isAsking,
                        modifier = Modifier.clip(CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = JarvisTheme.Cyan)
                    ) {
                        Icon(Icons.Default.Mic, "ถามด้วยเสียง (พักบันทึกอัตโนมัติ)", tint = JarvisTheme.Dark)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (mode == null) {
                Button(
                    onClick = { controller.startMeeting() },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
                    modifier = Modifier.weight(1f)
                ) { Text("● เริ่มบันทึก", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { if (isPaused) controller.resume() else controller.pause() },
                    // ระหว่างคุยด้วยเสียงให้ใช้ปุ่ม "เลิกพัก" ด้านบนแทน (ต้องปิด session ผู้ช่วยก่อน)
                    enabled = !voiceAsk,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) JarvisTheme.Green else JarvisTheme.Amber
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isPaused) "▶ พูดต่อ" else "❚❚ พัก", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (voiceAsk) controller.endVoiceAsk()
                        controller.stop(summarize = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Red),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("หยุดและสรุป", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(onClick = {
                if (voiceAsk) controller.endVoiceAsk()
                controller.stop(summarize = false)
                onClose()
            }) { Text("ปิด", color = Color.White.copy(alpha = 0.8f)) }
        }
    }
}

@Composable
internal fun StatusLine(
    state: LiveSpecialistService.State,
    isPaused: Boolean,
    running: Boolean,
    level: Float
) {
    val (text, color) = when {
        !running -> "พร้อมเริ่ม" to Color.White.copy(alpha = 0.6f)
        isPaused -> "พักอยู่ — ไมค์หยุดส่งเสียงชั่วคราว" to JarvisTheme.Amber
        state is LiveSpecialistService.State.Connecting -> "กำลังเชื่อมต่อ…" to JarvisTheme.Cyan
        state is LiveSpecialistService.State.Rotating -> "ต่อ session ใหม่ (ครบ 8 นาที) …" to JarvisTheme.Cyan
        state is LiveSpecialistService.State.Listening -> "กำลังฟังและถอดเสียง" to JarvisTheme.Green
        state is LiveSpecialistService.State.Error -> "ขัดข้อง: ${state.message}" to JarvisTheme.Red
        else -> "หยุดแล้ว" to Color.White.copy(alpha = 0.6f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = if (running && !isPaused) (0.4f + level).coerceAtMost(1f) else 0.5f))
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}

internal fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

internal fun formatClock(epochMs: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
    val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}"
}

/** ปุ่มแท็บของหน้าประชุม */
@Composable
private fun MeetingTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.22f) else JarvisTheme.Card)
            .border(
                1.dp,
                if (selected) JarvisTheme.Cyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) JarvisTheme.Cyan else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * แท็บ "สรุปประชุม" — รายการบันทึกที่เก็บไว้ในเครื่อง
 *
 * กด "อ่าน" เพื่อกางสรุปอ่านเอง (เลื่อนได้), "เล่น" ให้ผู้ช่วยอ่านออกเสียง, "ลบ" เพื่อลบทิ้ง
 */
@Composable
private fun ColumnScope.MeetingRecordsPane(
    records: List<MeetingRecord>,
    openedRecordId: Long?,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onStopPlay: () -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit
) {
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }

    Spacer(Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(JarvisTheme.Surface)
            .border(1.dp, JarvisTheme.Cyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (records.isEmpty()) {
            Text(
                "ยังไม่มีบันทึกที่สรุปไว้\nกดหยุดและสรุปหลังประชุม แล้วสรุปจะมาอยู่ที่นี่",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records, key = { it.id }) { record ->
                    val opened = openedRecordId == record.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JarvisTheme.Card)
                            .padding(12.dp)
                    ) {
                        Text(record.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "ยาว ${formatElapsed(record.durationMs)} • สรุป ${record.summary.length} ตัวอักษร",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlay(record.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Green),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("▶ เล่น", color = JarvisTheme.Dark, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { onOpen(record.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (opened) "ปิดอ่าน" else "อ่าน",
                                    color = JarvisTheme.Dark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    if (confirmDeleteId == record.id) {
                                        confirmDeleteId = null
                                        onDelete(record.id)
                                    } else confirmDeleteId = record.id
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Red),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (confirmDeleteId == record.id) "ยืนยันลบ?" else "ลบ",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (opened) {
                            Spacer(Modifier.height(10.dp))
                            Text(record.summary, color = Color.White, fontSize = 14.sp)
                            if (record.transcript.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text("บันทึกคำต่อคำ", color = JarvisTheme.Cyan.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text(record.transcript, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onStopPlay, modifier = Modifier.weight(1f)) {
            Text("■ หยุดอ่าน", color = Color.White.copy(alpha = 0.8f))
        }
        OutlinedButton(onClick = onClose) { Text("ปิด", color = Color.White.copy(alpha = 0.8f)) }
    }
}
