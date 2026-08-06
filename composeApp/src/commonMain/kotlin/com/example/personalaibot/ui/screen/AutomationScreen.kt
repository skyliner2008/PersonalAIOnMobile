package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.db.AlertJob
import com.example.personalaibot.db.ScheduledTask
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AutomationScreen(
    jobs: List<AlertJob>,
    scheduledTasks: List<ScheduledTask>,
    alertAiSummary: Boolean,
    alertVoice: Boolean,
    onAlertAiSummaryChange: (Boolean) -> Unit,
    onAlertVoiceChange: (Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onUpdateInterval: (Long, Long) -> Unit,
    onUpdateCondition: (Long, com.example.personalaibot.automation.AutomationCondition) -> Unit = { _, _ -> },
    onRename: (Long, String) -> Unit = { _, _ -> },
    alertTestRunning: Boolean = false,
    alertTestStatus: String = "",
    alertTestResults: List<com.example.personalaibot.automation.AlertDataTester.TestResult> = emptyList(),
    onRunTest: () -> Unit = {},
    onCreateAlert: (String, String, String, String, String, String, Long) -> Unit = { _, _, _, _, _, _, _ -> },
    onCreateScheduledTask: (String, String, String, Long, String?) -> Unit = { _, _, _, _, _ -> }
) {
    var showCreateAlert by remember { mutableStateOf(false) }
    var showCreateTask by remember { mutableStateOf(false) }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisTheme.Dark),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── การตั้งค่าการแจ้งเตือน ───
        item {
            AlertSettingsCard(
                aiSummary = alertAiSummary,
                voice = alertVoice,
                onAiSummaryChange = onAlertAiSummaryChange,
                onVoiceChange = onAlertVoiceChange
            )
        }

        // ─── Alerts (เฝ้าดูตามเงื่อนไข) ───
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 Alerts — เฝ้าดูตามเงื่อนไข", color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRunTest, enabled = !alertTestRunning) {
                    Text(if (alertTestRunning) "🧪 กำลังทดสอบ..." else "🧪 ทดสอบ", color = JarvisTheme.Cyan, fontSize = 13.sp)
                }
                TextButton(onClick = { showCreateAlert = true }) {
                    Text("➕ สร้าง", color = JarvisTheme.Cyan, fontSize = 13.sp)
                }
            }
        }

        // ─── Auto Test status card (แสดงขณะทดสอบ/หลังทดสอบจนกว่าจะเปลี่ยนหน้า) ──
        if (alertTestStatus.isNotBlank()) {
            item {
                AlertTestStatusCard(
                    running = alertTestRunning,
                    status = alertTestStatus,
                    results = alertTestResults
                )
            }
        }
        if (jobs.isEmpty()) {
            item {
                Text(
                    "ยังไม่มี alert — สร้างได้เลย หรือสั่ง JARVIS เช่น \"เฝ้าทอง ถ้าถึง 4100 บอกฉัน\"",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                )
            }
        } else {
            items(jobs.size, key = { jobs[it].id }) { index ->
                val job = jobs[index]
                CronJobCard(
                    job = job,
                    onDelete = { onDelete(job.id) },
                    onUpdateInterval = { interval -> onUpdateInterval(job.id, interval) },
                    onUpdateCondition = { cond -> onUpdateCondition(job.id, cond) },
                    onRename = { name -> onRename(job.id, name) }
                )
            }
        }

        // ─── Scheduled Tasks (ปลุก AI ตามเวลา) ───
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("⏰ Scheduled Tasks — ปลุก AI ตามเวลา", color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showCreateTask = true }) {
                    Text("➕ สร้าง", color = JarvisTheme.Cyan, fontSize = 13.sp)
                }
            }
        }
        if (scheduledTasks.isEmpty()) {
            item {
                Text(
                    "ยังไม่มีงานตามเวลา — สร้างได้เลย หรือสั่ง JARVIS เช่น \"ทุกเช้า 8 โมงสรุปข่าวให้หน่อย\"",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                )
            }
        } else {
            items(scheduledTasks.size) { index ->
                ScheduledTaskCard(
                    task = scheduledTasks[index],
                    onDelete = { onDeleteTask(scheduledTasks[index].id) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showCreateAlert) {
        CreateAlertDialog(
            onDismiss = { showCreateAlert = false },
            onConfirm = { name, symbol, toolName, field, op, value, interval ->
                onCreateAlert(name, symbol, toolName, field, op, value, interval)
                showCreateAlert = false
            }
        )
    }
    if (showCreateTask) {
        CreateScheduledTaskDialog(
            onDismiss = { showCreateTask = false },
            onConfirm = { name, prompt, type, runAt, hhmm ->
                onCreateScheduledTask(name, prompt, type, runAt, hhmm)
                showCreateTask = false
            }
        )
    }
}

@Composable
private fun AlertTestStatusCard(
    running: Boolean,
    status: String,
    results: List<com.example.personalaibot.automation.AlertDataTester.TestResult>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (running) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisTheme.Cyan
                )
            }
            results.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (r.ok) "✅" else "❌",
                        fontSize = 12.sp
                    )
                    Text(
                        " ${r.tool}  ",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
                    )
                    Text(
                        if (r.ok) "${r.fieldCount} fields" else (r.error ?: "fail"),
                        color = if (r.ok) Color(0xFF00C853) else Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            if (!running && results.isNotEmpty()) {
                Text(
                    "ดูผลละเอียดใน logcat tag: AlertDataTest",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AlertSettingsCard(
    aiSummary: Boolean,
    voice: Boolean,
    onAiSummaryChange: (Boolean) -> Unit,
    onVoiceChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⚙️ รูปแบบการแจ้งเตือน", color = Color.White, fontWeight = FontWeight.SemiBold)
            SettingToggleRow(
                label = "🤖 ให้ AI สรุปก่อนแจ้งเตือน",
                checked = aiSummary,
                onChange = onAiSummaryChange
            )
            SettingToggleRow(
                label = "🔊 แจ้งเตือนด้วยเสียงพูด",
                checked = voice,
                onChange = onVoiceChange
            )
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = JarvisTheme.Cyan)
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    onDelete: () -> Unit
) {
    val whenDesc = if (task.schedule_type == "daily") {
        "ทุกวัน ${task.time_hhmm ?: "--:--"} น."
    } else {
        try {
            val lt = Instant.fromEpochMilliseconds(task.run_at)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            "ครั้งเดียว ${lt.date} ${lt.hour.toString().padStart(2, '0')}:${lt.minute.toString().padStart(2, '0')} น."
        } catch (_: Exception) { "ครั้งเดียว" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (task.schedule_type == "daily") "DAILY" else "ONE-TIME",
                    color = JarvisTheme.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("⏰ $whenDesc", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\"${task.prompt}\"",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                }
            }
        }
    }
}


@Composable
private fun CronJobCard(
    job: AlertJob,
    onDelete: () -> Unit,
    onUpdateInterval: (Long) -> Unit,
    onUpdateCondition: (com.example.personalaibot.automation.AutomationCondition) -> Unit,
    onRename: (String) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }

    // ── Countdown timer (อัปเดตทุกวินาที) ──
    var nowMs by remember { mutableStateOf(kotlinx.datetime.Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(job.last_run_at) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }
    val nextCheckMs = job.last_run_at + job.interval_minutes * 60_000L
    val remainingSec = ((nextCheckMs - nowMs) / 1000L).coerceAtLeast(0L)
    val countdownText = when {
        job.last_run_at <= 0L -> "⏳ รอเช็คครั้งแรก..."
        remainingSec <= 0L -> "🔍 เช็คเร็วๆ นี้..."
        remainingSec < 60L -> "⏱️ อีก ${remainingSec} วินาที"
        else -> "⏱️ อีก ${remainingSec / 60L} นาที ${remainingSec % 60L} วินาที"
    }

    val condition = remember(job.condition_json) {
        try {
            com.example.personalaibot.automation.automationJson.decodeFromString(
                com.example.personalaibot.automation.AutomationCondition.serializer(), job.condition_json
            )
        } catch (_: Exception) { null }
    }
    val opText = condition?.let {
        when (it.operator) {
            com.example.personalaibot.automation.ConditionOperator.GT -> ">"
            com.example.personalaibot.automation.ConditionOperator.LT -> "<"
            com.example.personalaibot.automation.ConditionOperator.GTE -> ">="
            com.example.personalaibot.automation.ConditionOperator.LTE -> "<="
            com.example.personalaibot.automation.ConditionOperator.EQ -> "=="
            com.example.personalaibot.automation.ConditionOperator.CONTAINS -> "contains"
        }
    } ?: "?"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(JarvisTheme.Cyan.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(job.symbol.take(8), color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                // สถานะ: TRIGGERED = แจ้งไปแล้ว ระบบยัง ACTIVE อยู่และจะรีเซ็ตอัตโนมัติเมื่อราคาหลุดเงื่อนไข (Repeat mode)
                Text(
                    if (job.is_triggered == 1L) "TRIGGERED · แจ้งแล้ว" else "ACTIVE · เฝ้าดูอยู่",
                    color = if (job.is_triggered == 1L) Color(0xFFFF5252) else Color(0xFF00C853),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(job.name, color = Color.White, fontWeight = FontWeight.SemiBold)

            // ── เงื่อนไขที่เฝ้าดู ──
            if (condition != null) {
                Text(
                    "🎯 ${condition.field} $opText ${condition.value}",
                    color = JarvisTheme.Cyan.copy(alpha = 0.9f), fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = JarvisTheme.Cyan)
                Text(
                    text = " Every ${job.interval_minutes} min",
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Alert", tint = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                }
            }

            // ── แสดงค่าล่าสุด + countdown ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                val valueText = if (job.last_value != null) "${job.last_value}" else "—"
                Text(
                    text = "$valueText  •  $countdownText",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showEditDialog && condition != null) {
        AlertEditDialog(
            currentName = job.name,
            currentInterval = job.interval_minutes,
            field = condition.field,
            operatorText = opText,
            currentValue = condition.value,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newInterval, newValue ->
                if (newName != job.name) onRename(newName)
                onUpdateInterval(newInterval)
                if (newValue != condition.value) {
                    onUpdateCondition(condition.copy(value = newValue))
                }
                showEditDialog = false
            }
        )
    }
}

/**
 * Dialog แก้ไข alert — แก้ได้: ชื่อ (ใช้ในหัว notification และตอน AI พูด),
 * ค่าเปรียบเทียบ (เช่น ราคาเป้าหมาย) และความถี่
 * ส่วน field/operator แก้ไม่ได้ (ลบแล้วสร้างใหม่แทน)
 */
@Composable
private fun AlertEditDialog(
    currentName: String,
    currentInterval: Long,
    field: String,
    operatorText: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, interval: Long, value: String) -> Unit
) {
    var nameText by remember { mutableStateOf(currentName) }
    var intervalText by remember { mutableStateOf(currentInterval.toString()) }
    var valueText by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Surface,
        title = { Text("แก้ไข Alert", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("ชื่อ Alert (ใช้ในหัว notification และตอน AI พูด)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "เงื่อนไข: $field $operatorText  (แก้ไขไม่ได้ — ลบแล้วสร้างใหม่หากต้องการเปลี่ยน)",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("ค่าเปรียบเทียบ (เช่น ราคาเป้าหมาย)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { if (it.all(Char::isDigit)) intervalText = it },
                    label = { Text("ตรวจสอบทุกกี่นาที (1-1440)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newInterval = (intervalText.toLongOrNull() ?: currentInterval).coerceIn(1, 1440)
                    onConfirm(nameText.trim().ifBlank { currentName }, newInterval, valueText.trim().ifBlank { currentValue })
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan)
            ) {
                Text("บันทึก", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ─── Create Alert Dialog ─────────────────────────────────────────────────

// ─── Preset ลัดสำหรับ alert ที่ใช้บ่อย ──────────────────────────────────
private data class AlertPreset(
    val label: String,
    val toolName: String,
    val field: String,
    val op: String,
    val value: String
)

private val ALERT_PRESETS = listOf(
    AlertPreset("💰 ราคาถึงเป้า", "trading_price", "price", ">=", ""),
    AlertPreset("📉 RSI Oversold", "trading_indicators", "rsi14", "<=", "30"),
    AlertPreset("📈 RSI Overbought", "trading_indicators", "rsi14", ">=", "70"),
    AlertPreset("✨ Golden Cross", "trading_indicators", "ema_cross_state", "==", "GOLDEN_CROSS"),
    AlertPreset("💀 Death Cross", "trading_indicators", "ema_cross_state", "==", "DEATH_CROSS"),
    AlertPreset("🟢 โซน Discount", "trading_smc", "smc_zone_pct", "<=", "20"),
    AlertPreset("🔴 โซน Premium", "trading_smc", "smc_zone_pct", ">=", "80"),
    AlertPreset("💥 Bollinger Squeeze", "trading_indicators", "bb_width", "<=", "3"),
    AlertPreset("🚀 เทรนด์แรง ADX", "trading_technical_analysis", "ADX", ">=", "25"),
    AlertPreset("😱 Extreme Fear", "trading_fear_greed", "value", "<=", "20"),
    AlertPreset("🌟 High Confluence", "trading_deep_analysis_suite", "summaryScore", ">=", "85")
)

/**
 * Dialog สร้าง Alert — เลือกเงื่อนไขจากรายการที่ระบบทำได้จริงเท่านั้น
 * (AlertFieldCatalog) เพื่อกันการตั้งค่ามั่วที่ background engine ดึงค่าไม่ได้
 * เนื้อหา scroll ได้ — รองรับหน้าจอเตี้ย/ฟอนต์ใหญ่ (แก้ปัญหาช่องล่างสุดกดไม่ได้)
 */
@Composable
private fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, symbol: String, toolName: String, field: String, op: String, value: String, interval: Long) -> Unit
) {
    val catalog = com.example.personalaibot.automation.AlertFieldCatalog
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf(catalog.INDICATORS) }
    var selectedField by remember { mutableStateOf(catalog.INDICATORS.fields.first()) }
    var op by remember { mutableStateOf(">=") }
    var value by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("15") }

    val operators = listOf(">", "<", ">=", "<=", "==", "contains")
    val allowedOps = if (selectedField.isNumeric) operators else listOf("==", "contains")
    if (op !in allowedOps) op = allowedOps.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Surface,
        title = { Text("สร้าง Alert", color = Color.White, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ── Preset ลัด (เลื่อนขวา-ซ้ายได้) ──
                Text("⚡ Preset ลัด:", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ALERT_PRESETS.forEach { preset ->
                        androidx.compose.material3.SuggestionChip(
                            onClick = {
                                val tool = catalog.tools.firstOrNull { it.toolName == preset.toolName } ?: return@SuggestionChip
                                val field = tool.fields.firstOrNull { it.field == preset.field } ?: return@SuggestionChip
                                selectedTool = tool
                                selectedField = field
                                op = preset.op
                                value = preset.value
                            },
                            label = { Text(preset.label, fontSize = 11.sp) },
                            colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                containerColor = JarvisTheme.Card,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // ── Symbol + ชื่อ (แถวเดียวกัน ประหยัดที่) ──
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.weight(1.2f)) {
                        AlertTextField("Symbol เช่น XAUUSD", symbol) { symbol = it.uppercase() }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AlertTextField("ชื่อ (ไม่บังคับ)", name) { name = it }
                    }
                }
                Text(
                    "💡 ต่อท้าย @TF เลือกกรอบเวลาได้ เช่น XAUUSD@15m (default 1h)",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp
                )

                // ── เลือกประเภทข้อมูล (tool) ──
                AlertDropdown(
                    label = "ประเภทข้อมูล",
                    selected = selectedTool.label,
                    options = catalog.tools.map { it.label }
                ) { idx ->
                    selectedTool = catalog.tools[idx]
                    selectedField = selectedTool.fields.first()
                }

                // ── เลือกฟิลด์ (เฉพาะที่ระบบทำได้จริง) ──
                AlertDropdown(
                    label = "ฟิลด์ / เงื่อนไข",
                    selected = selectedField.label,
                    options = selectedTool.fields.map { it.label }
                ) { idx ->
                    selectedField = selectedTool.fields[idx]
                    // auto-fill ตัวอย่างค่าเพื่อกันตั้งมั่ว
                    if (value.isBlank()) {
                        when (selectedField.field) {
                            "RSI", "rsi14" -> { op = ">="; value = "70" }
                            "value" -> { op = "<="; value = "20" }
                            "summaryScore" -> { op = ">="; value = "85" }
                            "smc_zone_pct" -> { op = "<="; value = "20" }
                            "ema_cross_state" -> { op = "=="; value = "GOLDEN_CROSS" }
                        }
                    }
                }
                if (selectedField.hint.isNotBlank()) {
                    Text(
                        "💡 ${selectedField.hint}",
                        color = JarvisTheme.Cyan.copy(alpha = 0.8f), fontSize = 10.sp,
                        maxLines = 2
                    )
                }

                // ── ตัวดำเนินการ + ค่า (แถวเดียวกัน) ──
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AlertDropdown(
                            label = "ตัวดำเนินการ",
                            selected = op,
                            options = allowedOps
                        ) { idx -> op = allowedOps[idx] }
                    }
                    Box(modifier = Modifier.weight(1.4f)) {
                        AlertTextField("ค่า เช่น 4100, 70", value) { value = it }
                    }
                }

                AlertTextField("ตรวจสอบทุกกี่นาที (1-1440)", interval) {
                    if (it.all(Char::isDigit)) interval = it
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) name = "Alert $symbol"
                    onConfirm(
                        name, symbol, selectedTool.toolName, selectedField.field,
                        op, value, interval.toLongOrNull()?.coerceIn(1, 1440) ?: 15
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
                enabled = symbol.isNotBlank() && value.isNotBlank()
            ) {
                Text("สร้าง", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = Color.White.copy(alpha = 0.7f)) }
        }
    )
}

/** Dropdown แบบง่ายสำหรับ dialog (จำกัดทางเลือกให้อยู่ในรายการที่ทำได้จริง) */
@Composable
private fun AlertDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text(selected, color = Color.White, fontSize = 13.sp)
            }
            Text("▼", color = JarvisTheme.Cyan, fontSize = 11.sp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // ห้ามใส่ verticalScroll/heightIn ตรงนี้ — DropdownMenu วัด popup ด้วย
            // infinity height constraint → scrollable child จะ crash ทันทีที่เปิด
            // (DropdownMenu มี scroll ในตัวอยู่แล้วเมื่อรายการยาว)
            modifier = Modifier.background(JarvisTheme.Card)
        ) {
            options.forEachIndexed { idx, opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt, color = Color.White, fontSize = 13.sp) },
                    onClick = { onSelect(idx); expanded = false }
                )
            }
        }
    }
}

// ─── Create Scheduled Task Dialog ────────────────────────────────────────

@Composable
private fun CreateScheduledTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, prompt: String, type: String, runAt: Long, timeHhmm: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("one_time") }
    var hhmm by remember { mutableStateOf("08:00") }
    var inMinutes by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Surface,
        title = { Text("สร้าง Scheduled Task", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertTextField("ชื่องาน", name) { name = it }
                AlertTextField("คำสั่งที่ให้ AI ทำ", prompt) { prompt = it }
                AlertTextField("ประเภท (one_time / daily)", type) { type = it.lowercase() }
                if (type == "daily") {
                    AlertTextField("เวลา (HH:mm)", hhmm) { hhmm = it }
                } else {
                    AlertTextField("อีกกี่นาที", inMinutes) { if (it.all(Char::isDigit)) inMinutes = it }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = kotlinx.datetime.Clock.System.now()
                    val minutes = inMinutes.toLongOrNull()?.coerceIn(1, 525600) ?: 30
                    val runAt = if (type == "daily") 0L else now.plus(kotlin.time.Duration.parseIsoString("PT${minutes}M")).toEpochMilliseconds()
                    val timeHhmm = if (type == "daily") hhmm.padStart(5, '0') else null
                    onConfirm(name.ifBlank { "Scheduled Task" }, prompt, type, runAt, timeHhmm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
                enabled = prompt.isNotBlank()
            ) {
                Text("สร้าง", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = Color.White.copy(alpha = 0.7f)) }
        }
    )
}

@Composable
private fun AlertTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = JarvisTheme.Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
