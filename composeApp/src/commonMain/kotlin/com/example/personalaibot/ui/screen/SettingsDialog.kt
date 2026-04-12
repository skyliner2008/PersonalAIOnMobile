package com.example.personalaibot.ui.screen

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
    onStopWidget: () -> Unit = {}
) {
    val currentApiKey    by viewModel.apiKey.collectAsStateWithLifecycle()
    val currentModel     by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentLiveModel by viewModel.liveModelName.collectAsStateWithLifecycle()
    val currentVoice   by viewModel.voiceName.collectAsStateWithLifecycle()
    val availableModels  by viewModel.availableModels.collectAsStateWithLifecycle()

    var apiKey       by remember { mutableStateOf(currentApiKey) }
    var mainModel    by remember { mutableStateOf(currentModel) }
    var liveModel    by remember { mutableStateOf(currentLiveModel) }
    var voiceName    by remember { mutableStateOf(currentVoice) }
    var mainExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var widgetActive by remember { mutableStateOf(false) }

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

                // ── Voice Profile (Live) — 30 voices ──
                Text("เสียงผู้ช่วย (Live Profile)", color = JarvisTheme.Cyan.copy(0.8f), fontSize = 12.sp)

                // Gender filter tabs
                var selectedGender by remember { mutableStateOf<VoiceGender?>(null) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGender == null,
                        onClick = { selectedGender = null },
                        label = { Text("ทั้งหมด (30)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisTheme.Cyan.copy(0.2f),
                            selectedLabelColor = JarvisTheme.Cyan,
                            containerColor = JarvisTheme.Surface,
                            labelColor = Color.White.copy(0.6f)
                        )
                    )
                    FilterChip(
                        selected = selectedGender == VoiceGender.FEMALE,
                        onClick = { selectedGender = VoiceGender.FEMALE },
                        label = { Text("หญิง (14)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF69B4).copy(0.2f),
                            selectedLabelColor = Color(0xFFFF69B4),
                            containerColor = JarvisTheme.Surface,
                            labelColor = Color.White.copy(0.6f)
                        )
                    )
                    FilterChip(
                        selected = selectedGender == VoiceGender.MALE,
                        onClick = { selectedGender = VoiceGender.MALE },
                        label = { Text("ชาย (16)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4FC3F7).copy(0.2f),
                            selectedLabelColor = Color(0xFF4FC3F7),
                            containerColor = JarvisTheme.Surface,
                            labelColor = Color.White.copy(0.6f)
                        )
                    )
                }

                val filteredVoices = when (selectedGender) {
                    VoiceGender.FEMALE -> GeminiVoiceProfiles.females
                    VoiceGender.MALE -> GeminiVoiceProfiles.males
                    null -> GeminiVoiceProfiles.all
                }

                ExposedDropdownMenuBox(
                    expanded = voiceExpanded,
                    onExpandedChange = { voiceExpanded = !voiceExpanded }
                ) {
                    val currentProfile = GeminiVoiceProfiles.findByName(voiceName)
                    val genderIcon = if (currentProfile?.gender == VoiceGender.FEMALE) "♀" else "♂"
                    val displayText = "$genderIcon ${voiceName} — ${currentProfile?.tone ?: ""}"
                    OutlinedTextField(
                        value = displayText,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
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
                        expanded = voiceExpanded,
                        onDismissRequest = { voiceExpanded = false },
                        containerColor = JarvisTheme.Card
                    ) {
                        filteredVoices.forEach { profile ->
                            val icon = if (profile.gender == VoiceGender.FEMALE) "♀" else "♂"
                            val genderColor = if (profile.gender == VoiceGender.FEMALE) Color(0xFFFF69B4) else Color(0xFF4FC3F7)
                            val isSelected = voiceName == profile.name
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$icon ${profile.name}",
                                            color = if (isSelected) JarvisTheme.Cyan else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            profile.tone,
                                            color = genderColor.copy(0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                onClick = { voiceName = profile.name; voiceExpanded = false }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))

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

                val isSleeping by viewModel.isSleeping.collectAsStateWithLifecycle()
                Button(
                    onClick = { viewModel.triggerSleepCycle() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSleeping,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisTheme.Cyan.copy(0.2f), 
                        contentColor = JarvisTheme.Cyan,
                        disabledContainerColor = Color.White.copy(0.1f),
                        disabledContentColor = Color.White.copy(0.5f)
                    )
                ) {
                    Text(if (isSleeping) "💤 กำลังจัดเรียงความจำ (Sleeping)..." else "💤 เริ่มกระบวนการจำศีล (Sleep)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateSettings(apiKey, mainModel, liveModel, voiceName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisTheme.Cyan,
                    contentColor = Color.Black
                )
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(0.5f))
            }
        }
    )
}
