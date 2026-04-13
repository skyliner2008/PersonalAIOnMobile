package com.example.personalaibot.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalaibot.JarvisViewModel
import com.example.personalaibot.data.GeminiVoiceProfiles
import com.example.personalaibot.data.VoiceGender
import com.example.personalaibot.ui.theme.JarvisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: JarvisViewModel,
    onDismiss: () -> Unit,
    onStartWidget: () -> Unit = {},
    onStopWidget: () -> Unit = {},
    requestAllFilesPermission: () -> Unit = {},
    allFilesAccessGranted: Boolean = false
) {
    val currentApiKey    by viewModel.apiKey.collectAsStateWithLifecycle()
    val currentModel     by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentLiveModel by viewModel.liveModelName.collectAsStateWithLifecycle()
    val currentVoice   by viewModel.voiceName.collectAsStateWithLifecycle()
    val availableModels  by viewModel.availableModels.collectAsStateWithLifecycle()
    val isWidgetEnabled  by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()

    val currentOpenaiKey  by viewModel.openaiApiKey.collectAsStateWithLifecycle()
    val currentClaudeKey  by viewModel.claudeApiKey.collectAsStateWithLifecycle()

    var apiKey       by remember { mutableStateOf(currentApiKey) }
    var mainModel    by remember { mutableStateOf(currentModel) }
    var liveModel    by remember { mutableStateOf(currentLiveModel) }
    var voiceName    by remember { mutableStateOf(currentVoice) }
    var openaiApiKey by remember { mutableStateOf(currentOpenaiKey) }
    var claudeApiKey by remember { mutableStateOf(currentClaudeKey) }
    var mainExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var widgetActive by remember(isWidgetEnabled) { mutableStateOf(isWidgetEnabled) }

    // Filter live-compatible models
    val liveModels = availableModels.filter { model ->
        val n = model.name.lowercase()
        n.contains("live") || n.contains("flash") || n.contains("pro")
    }.ifEmpty { availableModels }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Card,
        title = {
            Text(
                "JARVIS SETTINGS",
                color = JarvisTheme.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── API Key ──
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key", color = Color.White.copy(0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Text(
                                if (apiKeyVisible) "Hide" else "Show",
                                color = JarvisTheme.Cyan,
                                fontSize = 12.sp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan.copy(0.5f),
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = JarvisTheme.Surface,
                        unfocusedContainerColor = JarvisTheme.Surface
                    )
                )

                // ── Main Model (text/tools) ──
                Text("โมเดลหลัก (Text / Tools)", color = Color.White.copy(0.7f), fontSize = 12.sp)
                ExposedDropdownMenuBox(
                    expanded = mainExpanded,
                    onExpandedChange = { mainExpanded = !mainExpanded }
                ) {
                    OutlinedTextField(
                        value = mainModel.removePrefix("models/"),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisTheme.Cyan.copy(0.5f),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = JarvisTheme.Surface,
                            unfocusedContainerColor = JarvisTheme.Surface
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = mainExpanded,
                        onDismissRequest = { mainExpanded = false },
                        containerColor = JarvisTheme.Card
                    ) {
                        if (availableModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("กำลังโหลด... ตรวจสอบ API Key", color = Color.White.copy(0.5f)) },
                                onClick = {}
                            )
                        }
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName ?: model.name.removePrefix("models/"), color = Color.White) },
                                onClick = { mainModel = model.name; mainExpanded = false }
                            )
                        }
                    }
                }

                // ── Live Model (voice/camera) ──
                Text("โมเดล Live (เสียง / กล้อง)", color = JarvisTheme.Red.copy(0.8f), fontSize = 12.sp)
                ExposedDropdownMenuBox(
                    expanded = liveExpanded,
                    onExpandedChange = { liveExpanded = !liveExpanded }
                ) {
                    OutlinedTextField(
                        value = liveModel.removePrefix("models/"),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = liveExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisTheme.Red.copy(0.5f),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = JarvisTheme.Surface,
                            unfocusedContainerColor = JarvisTheme.Surface
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = liveExpanded,
                        onDismissRequest = { liveExpanded = false },
                        containerColor = JarvisTheme.Card
                    ) {
                        if (liveModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("กำลังโหลด... ตรวจสอบ API Key", color = Color.White.copy(0.5f)) },
                                onClick = {}
                            )
                        }
                        liveModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName ?: model.name.removePrefix("models/"), color = Color.White) },
                                onClick = { liveModel = model.name; liveExpanded = false }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))

                // [REMOVED] Manual Voice Selection — System now manages persona via verbal interaction.

                // ── Floating Widget toggle ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Widget", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("วิดเจ็ตลอยบนหน้าจอสำหรับ Live mode", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                    Switch(
                        checked = widgetActive,
                        onCheckedChange = { active ->
                            widgetActive = active
                            viewModel.setFloatingWidgetEnabled(active)
                            if (active) {
                                onStartWidget()
                            } else {
                                onStopWidget()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisTheme.Card,
                            checkedTrackColor = JarvisTheme.Cyan
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                
                // ── Permissions ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("สิทธิ์การเข้าถึง (Permissions)", color = JarvisTheme.Cyan.copy(0.8f), fontSize = 12.sp)
                    Surface(
                        color = if (allFilesAccessGranted) JarvisTheme.Cyan.copy(0.1f) else JarvisTheme.Red.copy(0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (allFilesAccessGranted) "  ได้รับสิทธิ์แล้ว  " else "  ยังไม่มีสิทธิ์  ",
                            color = if (allFilesAccessGranted) JarvisTheme.Cyan else JarvisTheme.Red,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                
                if (!allFilesAccessGranted) {
                    Button(
                        onClick = { requestAllFilesPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Purple.copy(0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ขอสิทธิ์เข้าถึงไฟล์", color = Color.White, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // ── OpenAI API Key ──
                Text("OpenAI API Key (สำหรับ Camera Vision)", color = JarvisTheme.Cyan.copy(0.8f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = openaiApiKey,
                    onValueChange = { openaiApiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = Color.White.copy(0.3f), fontSize = 13.sp) },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        cursorColor = JarvisTheme.Cyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Claude API Key ──
                Text("Claude API Key (สำหรับ Camera Vision)", color = JarvisTheme.Cyan.copy(0.8f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = claudeApiKey,
                    onValueChange = { claudeApiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-ant-...", color = Color.White.copy(0.3f), fontSize = 13.sp) },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        cursorColor = JarvisTheme.Cyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateSettings(apiKey, mainModel, liveModel, voiceName)
                    viewModel.updateExternalApiKeys(openaiApiKey, claudeApiKey)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan)
            ) {
                Text("บันทึก", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = Color.White.copy(0.7f))
            }
        }
    )
}