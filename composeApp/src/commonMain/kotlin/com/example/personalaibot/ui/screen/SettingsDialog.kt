package com.example.personalaibot.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalaibot.JarvisViewModel
import com.example.personalaibot.data.providers.ApiKeyTester
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

// ─── UI Tokens (P1, 2026-04-30) ──────────────────────────────────────────────
// Mobile-friendly defaults: easier to read at arm's length on a phone screen.
private val SectionTitleSize = 16.sp
private val LabelSize = 14.sp
private val BodySize = 14.sp
private val HelperSize = 11.sp
private val FieldHeight = 48.dp
private val CardPadding = 16.dp
private val SectionSpacing = 12.dp

private data class ProviderMeta(
    val id: String,
    val displayName: String,
    val keyPrefixHint: String,
    val placeholder: String,
)

private val ProviderMetas = listOf(
    ProviderMeta("gemini",     "Google Gemini",    "API Key",   "Paste Gemini API key"),
    ProviderMeta("openrouter", "OpenRouter",       "sk-or-…",  "sk-or-…"),
    ProviderMeta("groq",       "Groq",             "gsk_…",    "gsk_…"),
    ProviderMeta("minimax",    "MiniMax",          "minimax-…", "minimax-…"),
)

/** provider ที่ปุ่ม "Show free models only" ใช้ได้ (Groq ทุก model ฟรี ถือว่าผ่าน filter เสมอ) */
private val freeFilterProviders = setOf("openrouter", "groq")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: JarvisViewModel,
    onDismiss: () -> Unit,
    onStartWidget: () -> Unit = {},
    onStopWidget: () -> Unit = {},
    requestAllFilesPermission: () -> Unit = {},
    allFilesAccessGranted: Boolean = false,
) {
    val currentApiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val currentModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentLiveModel by viewModel.liveModelName.collectAsStateWithLifecycle()
    val isWidgetEnabled by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()
    val currentOpenRouterKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val currentGroqKey by viewModel.groqApiKey.collectAsStateWithLifecycle()
    val currentNvidiaNimKey by viewModel.nvidiaNimApiKey.collectAsStateWithLifecycle()
    val currentMinimaxKey by viewModel.minimaxApiKey.collectAsStateWithLifecycle()
    val currentFallbackModels by viewModel.geminiFallbackModels.collectAsStateWithLifecycle()
    val geminiApiKeys by viewModel.geminiApiKeys.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.modelDownloadProgress.collectAsStateWithLifecycle()
    val autoTradingConfig by viewModel.autoTrading.config.collectAsStateWithLifecycle()
    val mt5PairingStatus by viewModel.mt5PairingStatus.collectAsStateWithLifecycle()
    val mt5BridgeBaseUrl by viewModel.mt5BridgeBaseUrl.collectAsStateWithLifecycle()

    var geminiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var openRouterKey by remember(currentOpenRouterKey) { mutableStateOf(currentOpenRouterKey) }
    var groqKey by remember(currentGroqKey) { mutableStateOf(currentGroqKey) }
    var nvidiaNimKey by remember(currentNvidiaNimKey) { mutableStateOf(currentNvidiaNimKey) }
    var minimaxKey by remember(currentMinimaxKey) { mutableStateOf(currentMinimaxKey) }
    var fallbackText by remember(currentFallbackModels) {
        mutableStateOf(currentFallbackModels.joinToString("\n"))
    }
    var apiKeyVisible by remember { mutableStateOf(false) }

    var mainModel by remember(currentModel) { mutableStateOf(currentModel) }
    var liveModel by remember(currentLiveModel) { mutableStateOf(currentLiveModel) }
    var widgetActive by remember(isWidgetEnabled) { mutableStateOf(isWidgetEnabled) }

    var selectedProvider by remember { mutableStateOf(if (mainModel.contains("/")) mainModel.substringBefore("/") else "gemini") }
    var providerExpanded by remember { mutableStateOf(false) }
    var mainExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    val currentVoice by viewModel.voiceName.collectAsStateWithLifecycle()
    val savedShowFreeOnly by viewModel.showFreeModelsOnly.collectAsStateWithLifecycle()
    var showFreeOnly by remember(savedShowFreeOnly) { mutableStateOf(savedShowFreeOnly) }
    var providerModels by remember { mutableStateOf<List<com.example.personalaibot.data.providers.LlmModelInfo>>(emptyList()) }
    var liveModels by remember { mutableStateOf<List<com.example.personalaibot.data.providers.LlmModelInfo>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var modelSearch by remember { mutableStateOf("") }
    var showGeminiKeysDialog by remember { mutableStateOf(false) }
    var autoTestStatus by remember { mutableStateOf<String?>(null) }

    /** key ของ provider ที่เลือก (blank = ยังไม่ได้ตั้งค่า) */
    fun keyFor(providerId: String): String = when (providerId) {
        "gemini" -> geminiKey
        "openrouter" -> openRouterKey
        "groq" -> groqKey
        "nvidia_nim" -> nvidiaNimKey
        "minimax" -> minimaxKey
        else -> ""
    }

    // Main models: reload เมื่อ provider / free-filter / refresh เปลี่ยน
    LaunchedEffect(selectedProvider, showFreeOnly, refreshTick) {
        // free filter ใช้ได้กับ provider ที่มี free tier (Groq/NIM ทุก model ฟรีอยู่แล้ว)
        if (selectedProvider !in freeFilterProviders && showFreeOnly) {
            showFreeOnly = false
            return@LaunchedEffect // รอ trigger รอบใหม่จาก showFreeOnly
        }
        isLoadingModels = true
        providerModels = viewModel.getModelsForProvider(
            selectedProvider,
            showFreeOnly,
            keyFor(selectedProvider).ifBlank { null }
        )
        // Auto-sync: ถ้า model ปัจจุบันไม่ได้อยู่ใน provider ที่เลือก
        // (เช่นเพิ่งสลับ provider) → เลือก model ตัวแรกของ provider ให้อัตโนมัติ
        // กันกรณี mismatch เงียบๆ (routing ใช้ prefix ของ modelName เป็นหลัก)
        if (providerModels.isNotEmpty()) {
            val belongs = if (selectedProvider == "gemini") !mainModel.contains("/")
                          else mainModel.startsWith("$selectedProvider/")
            if (!belongs) {
                val first = providerModels.first()
                mainModel = if (selectedProvider == "gemini") first.id
                            else "$selectedProvider/${first.id}"
            }
        }
        isLoadingModels = false
    }

    // Live models: Live mode รองรับเฉพาะ Google Gemini (Live API)
    // — ดึงจาก gemini เสมอ ไม่ขึ้นกับ provider ที่เลือกในส่วน Main Model
    LaunchedEffect(geminiKey) {
        liveModels = viewModel.getLiveCapableModels("gemini", geminiKey.ifBlank { null })
    }

    var sectApiKeys by remember { mutableStateOf(true) }
    var sectModels by remember { mutableStateOf(true) }
    var sectFallback by remember { mutableStateOf(false) }
    var sectLive by remember { mutableStateOf(false) }
    var sectIdentity by remember { mutableStateOf(false) }
    var sectLocalAi by remember { mutableStateOf(false) }
    var sectWidget by remember { mutableStateOf(false) }
    var sectPermissions by remember { mutableStateOf(false) }

    // ─── Identity state (โหลดจาก JarvisPersona ปัจจุบัน) ───────────────────
    val currentIdentity = com.example.personalaibot.ai.JarvisPersona.identity
    var agentName by remember { mutableStateOf(currentIdentity.agentName) }
    var agentCreature by remember { mutableStateOf(currentIdentity.agentCreature) }
    var agentVibe by remember { mutableStateOf(currentIdentity.agentVibe) }
    var agentGender by remember { mutableStateOf(currentIdentity.agentGender) }
    var userName by remember { mutableStateOf(currentIdentity.userName) }
    var userCallName by remember { mutableStateOf(currentIdentity.userCallName) }
    var userNotes by remember { mutableStateOf(currentIdentity.userNotes) }

    val scope = rememberCoroutineScope()
    val testResults = remember { mutableStateMapOf<String, ApiKeyTester.TestResult?>() }
    val testing = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        // ─── API Keys section ───────────────────────────────────────────────
        SectionCard(
            title = "API Keys",
            icon = Icons.Default.Key,
            expanded = sectApiKeys,
            onToggle = { sectApiKeys = !sectApiKeys },
            trailing = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = JarvisTheme.Cyan,
                    )
                }
            },
        ) {
            ProviderMetas.forEach { meta ->
                run {
                    val key: String
                    val setter: (String) -> Unit
                    when (meta.id) {
                        "gemini" -> { key = geminiKey; setter = { geminiKey = it } }
                        "openrouter" -> { key = openRouterKey; setter = { openRouterKey = it } }
                        "groq" -> { key = groqKey; setter = { groqKey = it } }
                        "nvidia_nim" -> { key = nvidiaNimKey; setter = { nvidiaNimKey = it } }
                        "minimax" -> { key = minimaxKey; setter = { minimaxKey = it } }
                        else -> { key = ""; setter = { } }
                    }
                    ProviderKeyCard(
                        meta = meta,
                        key = key,
                        onKeyChange = setter,
                        visible = apiKeyVisible,
                        testing = testing[meta.id] == true,
                        result = testResults[meta.id],
                        onTest = {
                            scope.launch {
                                testing[meta.id] = true
                                testResults[meta.id] = viewModel.testApiKey(meta.id, key)
                                if (testResults[meta.id]?.ok == true && selectedProvider == meta.id) {
                                    providerModels = viewModel.getModelsForProvider(meta.id, showFreeOnly, key)
                                }
                                if (testResults[meta.id]?.ok == true && meta.id == "gemini") {
                                    liveModels = viewModel.getLiveCapableModels("gemini", key)
                                }
                                testing[meta.id] = false
                            }
                        },
                        trailingAction = if (meta.id == "gemini") {
                            {
                                // ปุ่มจัดการ multi API keys (free tier หลายเมล์)
                                IconButton(onClick = { showGeminiKeysDialog = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "จัดการ Gemini API Keys (${geminiApiKeys.size})",
                                        tint = if (geminiApiKeys.size > 1) JarvisTheme.Green else JarvisTheme.Cyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else null,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ─── Main Model section ─────────────────────────────────────────────
        SectionCard(
            title = "Main Model",
            icon = Icons.Default.Memory,
            expanded = sectModels,
            onToggle = { sectModels = !sectModels },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ปุ่ม Auto Test (Groq / OpenRouter free) — ไล่เทสทุก model ว่า chat/tools ใช้ได้จริง
                    if (selectedProvider == "groq" || selectedProvider == "openrouter") {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    autoTestStatus = "🧪 เริ่มทดสอบ…"
                                    val pass = viewModel.autoTestProviderModels(selectedProvider) { c, t, id ->
                                        autoTestStatus = "🧪 ทดสอบ $c/$t: $id"
                                    }
                                    autoTestStatus = "✅ เสร็จ: chat ผ่าน $pass ตัว (ดูรายละเอียดใน logcat tag ModelAutoTest)"
                                    providerModels = viewModel.getModelsForProvider(
                                        selectedProvider, showFreeOnly, keyFor(selectedProvider).ifBlank { null }
                                    )
                                }
                            },
                            enabled = autoTestStatus?.startsWith("🧪") != true,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Auto test models",
                                tint = if (autoTestStatus?.startsWith("🧪") == true) Color.White.copy(alpha = 0.3f) else JarvisTheme.Green,
                            )
                        }
                    }
                    IconButton(onClick = { refreshTick++ }, enabled = !isLoadingModels) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh model list",
                            tint = if (isLoadingModels) Color.White.copy(alpha = 0.3f) else JarvisTheme.Cyan,
                        )
                    }
                }
            },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = selectedProvider.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider", fontSize = HelperSize) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                            .heightIn(min = FieldHeight),
                        colors = fieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                        containerColor = JarvisTheme.Card,
                    ) {
                        ProviderMetas.forEach { meta ->
                            DropdownMenuItem(
                                text = { Text(meta.id.uppercase(), color = Color.White, fontSize = BodySize) },
                                onClick = { selectedProvider = meta.id; providerExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = mainExpanded,
                    onExpandedChange = { mainExpanded = !mainExpanded },
                    modifier = Modifier.weight(1.5f),
                ) {
                    val display = if (isLoadingModels) "Loading…" else mainModel.substringAfter("/")
                    OutlinedTextField(
                        value = display,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model", fontSize = HelperSize) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                            .heightIn(min = FieldHeight),
                        colors = fieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = mainExpanded,
                        onDismissRequest = { mainExpanded = false },
                        containerColor = JarvisTheme.Card,
                    ) {
                        if (providerModels.isEmpty() && !isLoadingModels) {
                            val emptyHint = when {
                                keyFor(selectedProvider).isBlank() ->
                                    "ยังไม่มี API Key — ใส่ key ด้านบน แล้วกด Test หรือ ↻"
                                else ->
                                    "โหลด models ไม่สำเร็จ — ตรวจ key/เน็ต แล้วกด ↻ ลองใหม่"
                            }
                            DropdownMenuItem(
                                text = { Text(emptyHint, color = Color.White.copy(alpha = 0.5f), fontSize = BodySize) },
                                onClick = {},
                            )
                        }
                        // Search filter — มีประโยชน์มากกับ OpenRouter (200+ models)
                        if (providerModels.size > 8) {
                            DropdownMenuItem(
                                text = {
                                    OutlinedTextField(
                                        value = modelSearch,
                                        onValueChange = { modelSearch = it },
                                        placeholder = { Text("ค้นหา model… (${providerModels.size} ตัว)", color = Color.White.copy(alpha = 0.35f), fontSize = HelperSize) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = fieldColors(),
                                    )
                                },
                                onClick = {},
                            )
                        }
                        val filteredModels = providerModels.filter {
                            modelSearch.isBlank()
                                || it.displayName.contains(modelSearch, ignoreCase = true)
                                || it.id.contains(modelSearch, ignoreCase = true)
                        }
                        filteredModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName, color = Color.White, fontSize = BodySize)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (model.isFree) Text("FREE", color = JarvisTheme.Cyan, fontSize = HelperSize)
                                            if (model.supportsFunctions) Text("🔧 tools", color = JarvisTheme.Green, fontSize = HelperSize)
                                            if (model.supportsVision) Text("👁 vision", color = JarvisTheme.Purple, fontSize = HelperSize)
                                        }
                                    }
                                },
                                onClick = {
                                    mainModel = if (selectedProvider == "gemini") model.id else "$selectedProvider/${model.id}"
                                    mainExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // สถานะ auto-test (แสดงระหว่างเทส/หลังเทสเสร็จ)
            autoTestStatus?.let { status ->
                Text(
                    status,
                    color = if (status.startsWith("✅")) JarvisTheme.Green else JarvisTheme.Amber,
                    fontSize = HelperSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Free-only filter — ใช้ได้กับ provider ที่มี free tier
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = showFreeOnly,
                    onCheckedChange = { showFreeOnly = it; viewModel.setShowFreeModelsOnly(it) },
                    enabled = selectedProvider in freeFilterProviders,
                    colors = CheckboxDefaults.colors(
                        checkedColor = JarvisTheme.Cyan,
                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                        checkmarkColor = JarvisTheme.Card,
                        disabledCheckedColor = Color.White.copy(alpha = 0.2f),
                        disabledUncheckedColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Text(
                    "Show free models only (OpenRouter / Groq)",
                    color = if (selectedProvider in freeFilterProviders) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
                    fontSize = BodySize,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = autoTradingConfig.preferFreeOnly,
                    onCheckedChange = { viewModel.autoTrading.updatePreferFreeOnly(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = JarvisTheme.Cyan,
                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                        checkmarkColor = JarvisTheme.Card,
                    ),
                )
                Column {
                    Text("Smart Free Fallback", color = Color.White, fontSize = BodySize, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Auto-rotate to a free model when the primary fails.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = HelperSize,
                    )
                }
            }
        }

        // ─── Gemini Fallback Chain (จัดลำดับเอง) ───────────────────────────
        SectionCard(
            title = "Gemini Fallback Models",
            icon = Icons.Default.Memory,
            expanded = sectFallback,
            onToggle = { sectFallback = !sectFallback },
        ) {
            Text(
                "เมื่อโมเดลหลักติดลิมิต (429) / ล่ม (503) / timeout ระบบจะไล่ลองโมเดลสำรองตามลำดับนี้ — 1 บรรทัด = 1 โมเดล (ลองจากบนลงล่าง)",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = HelperSize,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = fallbackText,
                onValueChange = { fallbackText = it },
                placeholder = {
                    Text(
                        com.example.personalaibot.data.ModelConfig.GEMINI_FALLBACK_MODELS.joinToString("\n"),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = HelperSize,
                    )
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    fallbackText = com.example.personalaibot.data.ModelConfig.GEMINI_FALLBACK_MODELS.joinToString("\n")
                }) {
                    Text("Reset เป็นค่า default", color = JarvisTheme.Cyan, fontSize = HelperSize)
                }
                TextButton(onClick = { fallbackText = "" }) {
                    Text("ล้าง (ใช้ default)", color = Color.White.copy(alpha = 0.5f), fontSize = HelperSize)
                }
            }
            Text(
                "ว่างไว้ = ใช้ลำดับ default ของระบบ (อิงโควต้า free tier: lite RPD 500 ขึ้นก่อน) • กด Save ด้านล่างเพื่อบันทึก",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = HelperSize,
            )
        }

        // ─── Live / Multi-Modal Model section ──────────────────────────────
        SectionCard(
            title = "Live / Multi-Modal Model",
            icon = Icons.Default.Bolt,
            expanded = sectLive,
            onToggle = { sectLive = !sectLive },
        ) {
            ExposedDropdownMenuBox(
                expanded = liveExpanded,
                onExpandedChange = { liveExpanded = !liveExpanded },
            ) {
                OutlinedTextField(
                    value = liveModel.removePrefix("models/"),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Live Model", fontSize = HelperSize) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = liveExpanded) },
                    modifier = Modifier
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                        .heightIn(min = FieldHeight),
                    colors = fieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = liveExpanded,
                    onDismissRequest = { liveExpanded = false },
                    containerColor = JarvisTheme.Card,
                ) {
                    if (liveModels.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (geminiKey.isBlank()) "ใส่ Gemini API Key ด้านบนก่อน — Live mode ใช้ได้เฉพาะ Gemini"
                                    else "No live-capable models found — กด Test key ด้านบนเพื่อตรวจสอบ",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = BodySize,
                                )
                            },
                            onClick = {},
                        )
                    }
                    liveModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.displayName.ifBlank { model.id }, color = Color.White, fontSize = BodySize) },
                            onClick = { liveModel = model.id; liveExpanded = false },
                        )
                    }
                }
            }
            Text(
                "Live mode รองรับเฉพาะ Google Gemini (Live API) — รายการนี้ดึงจาก Gemini key เสมอ ไม่ขึ้นกับ provider ที่เลือกในส่วน Main Model",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = HelperSize,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ─── Identity section ───────────────────────────────────────────────
        SectionCard(
            title = "Identity (ตัวตน AI & ผู้ใช้)",
            icon = Icons.Default.Person,
            expanded = sectIdentity,
            onToggle = { sectIdentity = !sectIdentity },
        ) {
            Text(
                "ปรับแต่งข้อมูลพื้นฐานของ AI agent และผู้ใช้ — มีผลกับ system prompt ทุก provider ทันทีหลังกด Save (AI ก็เปลี่ยนค่าเหล่านี้เองได้เมื่อคุณสั่ง)",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = HelperSize,
            )
            Text("AGENT IDENTITY", color = JarvisTheme.Cyan, fontSize = HelperSize, fontWeight = FontWeight.Bold)
            IdentityField("Name", agentName, { agentName = it }, "เช่น JARVIS")
            IdentityField("Creature (บทบาท)", agentCreature, { agentCreature = it }, "เช่น ผู้ช่วยส่วนตัว เลขา")
            IdentityField("Vibe (บุคลิก/น้ำเสียง)", agentVibe, { agentVibe = it }, "เช่น พูดสั้น กระชับ สุภาพ ตลก มีอารมณ์ขัน")
            IdentityField("Gender", agentGender, { agentGender = it }, "เช่น Female / Male / ไม่ระบุ")
            // ── Voice Profile (Live mode) — เลือกเสียงแล้ว sync เพศ/บุคลิกอัตโนมัติ ──
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded },
            ) {
                OutlinedTextField(
                    value = currentVoice,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice Profile (เสียง Live)", fontSize = HelperSize) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                    modifier = Modifier
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                        .heightIn(min = FieldHeight),
                    colors = fieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false },
                    containerColor = JarvisTheme.Card,
                ) {
                    com.example.personalaibot.data.GeminiVoiceProfiles.all.forEach { p ->
                        val icon = if (p.gender == com.example.personalaibot.data.VoiceGender.FEMALE) "♀" else "♂"
                        DropdownMenuItem(
                            text = { Text("${p.name} ($icon) — ${p.tone}", color = Color.White, fontSize = BodySize) },
                            onClick = {
                                voiceExpanded = false
                                agentGender = if (p.gender == com.example.personalaibot.data.VoiceGender.FEMALE) "หญิง" else "ชาย"
                                agentVibe = "${p.tone} (โปรไฟล์เสียง ${p.name})"
                                viewModel.selectVoiceProfile(p.name)
                            },
                        )
                    }
                }
            }
            Text(
                "เลือกเสียงแล้วระบบจะปรับ Gender/Vibe และคำลงท้าย (ครับ/ค่ะ) ให้อัตโนมัติ — มีผลทันทีและจำข้ามการเปิดแอป",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = HelperSize,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("USER IDENTITY", color = JarvisTheme.Cyan, fontSize = HelperSize, fontWeight = FontWeight.Bold)
            IdentityField("Name (ชื่อจริง)", userName, { userName = it }, "เช่น บอส")
            IdentityField("What to call them (การเรียก)", userCallName, { userCallName = it }, "เช่น บอส")
            IdentityField("Notes (หมายเหตุ)", userNotes, { userNotes = it }, "เช่น ใช้ภาษาไทยเป็นหลัก", singleLine = false)
        }

        // ─── Local AI section ───────────────────────────────────────────────
        SectionCard(
            title = "Local AI (On-device Embeddings)",
            icon = Icons.Default.Memory,
            expanded = sectLocalAi,
            onToggle = { sectLocalAi = !sectLocalAi },
        ) {
            Text(
                "Download offline AI model for fast embedding (~120MB). Works without internet.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = BodySize,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { viewModel.downloadLocalModel() },
                enabled = downloadProgress < 0f || downloadProgress >= 2f,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (downloadProgress >= 2f) JarvisTheme.Cyan.copy(alpha = 0.4f) else JarvisTheme.Cyan,
                    disabledContainerColor = JarvisTheme.Surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FieldHeight),
            ) {
                val btnText = when {
                    downloadProgress in 0f..1f -> "Downloading ${(downloadProgress * 100).toInt()}%"
                    downloadProgress >= 2f -> "✓ Model Ready (120MB)"
                    else -> "Download Local Model (120MB)"
                }
                Text(
                    btnText,
                    color = if (downloadProgress >= 2f) Color.White else JarvisTheme.Dark,
                    fontWeight = FontWeight.Bold,
                    fontSize = BodySize,
                )
            }
            if (downloadProgress in 0f..1f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisTheme.Cyan,
                    trackColor = JarvisTheme.Surface,
                )
            }
        }

        // ─── Floating Widget section ────────────────────────────────────────
        SectionCard(
            title = "Floating Widget",
            icon = Icons.Default.Bolt,
            expanded = sectWidget,
            onToggle = { sectWidget = !sectWidget },
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Widget", color = Color.White, fontSize = BodySize, fontWeight = FontWeight.Medium)
                    Text(
                        "Enable or disable widget service",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = HelperSize,
                    )
                }
                Switch(
                    checked = widgetActive,
                    onCheckedChange = { active ->
                        widgetActive = active
                        viewModel.setFloatingWidgetEnabled(active)
                        if (active) onStartWidget() else onStopWidget()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisTheme.Card,
                        checkedTrackColor = JarvisTheme.Cyan,
                    ),
                )
            }
        }

        // ─── Permissions section ────────────────────────────────────────────
        SectionCard(
            title = "Permissions",
            icon = Icons.Default.Warning,
            expanded = sectPermissions,
            onToggle = { sectPermissions = !sectPermissions },
        ) {
            val tint = if (allFilesAccessGranted) JarvisTheme.Green else JarvisTheme.Amber
            Surface(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (allFilesAccessGranted) "✓ File permission granted" else "File permission required",
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = BodySize,
                    )
                    if (!allFilesAccessGranted) {
                        Button(
                            onClick = requestAllFilesPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Purple.copy(alpha = 0.35f)),
                            modifier = Modifier.heightIn(min = FieldHeight),
                        ) {
                            Text("Grant permission", color = Color.White, fontSize = BodySize)
                        }
                    }
                }
            }
        }

        // ─── Save / Cancel ──────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).heightIn(min = FieldHeight),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Surface),
            ) {
                Text("Cancel", color = Color.White, fontSize = BodySize)
            }
            Button(
                onClick = {
                    viewModel.updateSettings(
                        key = geminiKey,
                        model = mainModel,
                        liveModel = liveModel,
                        voice = viewModel.voiceName.value,
                        preferFree = autoTradingConfig.preferFreeOnly,
                    )
                    viewModel.updateExternalApiKeys(
                        openRouter = openRouterKey,
                        minimax = minimaxKey,
                        groq = groqKey,
                        nvidiaNim = nvidiaNimKey,
                    )
                    // fallback chain: 1 บรรทัด = 1 โมเดล เรียงจากตัวที่จะลองก่อน
                    viewModel.updateGeminiFallbackModels(
                        fallbackText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    )
                    viewModel.updateIdentity(
                        agentName = agentName,
                        agentCreature = agentCreature,
                        agentVibe = agentVibe,
                        agentGender = agentGender,
                        userName = userName,
                        userCallName = userCallName,
                        userNotes = userNotes,
                    )
                    onDismiss()
                },
                modifier = Modifier.weight(1f).heightIn(min = FieldHeight),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
            ) {
                Text("Save", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold, fontSize = BodySize)
            }
        }

        // ─── Gemini Multi-Key Manager dialog ────────────────────────────────
        if (showGeminiKeysDialog) {
            GeminiKeysDialog(
                keys = geminiApiKeys,
                primaryKey = geminiKey,
                onAdd = { viewModel.addGeminiApiKey(it) },
                onEdit = { old, new -> viewModel.editGeminiApiKey(old, new) },
                onDelete = { viewModel.removeGeminiApiKey(it) },
                onDismiss = { showGeminiKeysDialog = false },
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        color = JarvisTheme.Card,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            ) {
                Icon(icon, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = SectionTitleSize,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderKeyCard(
    meta: ProviderMeta,
    key: String,
    onKeyChange: (String) -> Unit,
    visible: Boolean,
    testing: Boolean,
    result: ApiKeyTester.TestResult?,
    onTest: () -> Unit,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    val formatHint = remember(key, meta.id) { ApiKeyTester.validateFormat(meta.id, key) }
    val geminiSoftHint = remember(key, meta.id) {
        if (meta.id == "gemini") ApiKeyTester.geminiPrefixHint(key) else null
    }
    Surface(
        color = JarvisTheme.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = LabelSize,
                    modifier = Modifier.weight(1f),
                )
                trailingAction?.invoke()
                StatusDot(result = result, testing = testing)
            }
            OutlinedTextField(
                value = key,
                onValueChange = onKeyChange,
                placeholder = {
                    Text(meta.placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = BodySize)
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = FieldHeight),
                colors = fieldColors(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hintColor = when {
                    formatHint != null && key.isNotBlank() -> JarvisTheme.Red
                    result != null && !result.ok -> JarvisTheme.Red
                    result != null && result.ok -> JarvisTheme.Green
                    geminiSoftHint != null && key.isNotBlank() -> JarvisTheme.Amber
                    else -> Color.White.copy(alpha = 0.5f)
                }
                val hintText = when {
                    formatHint != null && key.isNotBlank() -> formatHint
                    result != null -> result.displayMessage
                    geminiSoftHint != null && key.isNotBlank() -> geminiSoftHint
                    key.isBlank() -> "Prefix: ${meta.keyPrefixHint}"
                    else -> "Format OK — tap Test to verify"
                }
                Text(hintText, color = hintColor, fontSize = HelperSize, modifier = Modifier.weight(1f))
                TextButton(onClick = onTest, enabled = !testing && key.isNotBlank()) {
                    Text(
                        if (testing) "Testing…" else "Test",
                        color = JarvisTheme.Cyan,
                        fontSize = BodySize,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(result: ApiKeyTester.TestResult?, testing: Boolean) {
    val color = when {
        testing -> JarvisTheme.Cyan
        result == null -> Color.White.copy(alpha = 0.25f)
        result.ok -> JarvisTheme.Green
        else -> JarvisTheme.Red
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisTheme.Cyan.copy(alpha = 0.6f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = JarvisTheme.Surface,
    unfocusedContainerColor = JarvisTheme.Surface,
    cursorColor = JarvisTheme.Cyan,
)

/** ช่องกรอกข้อมูล Identity — label + placeholder + helper */
@Composable
private fun IdentityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = HelperSize) },
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = BodySize) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
    )
}

@Composable
private fun ServerProviderStatusCard(
    meta: ProviderMeta,
    pairingStatus: String,
    serverUrl: String
) {
    val statusColor = when (pairingStatus) {
        "APPROVED" -> JarvisTheme.Green
        "PENDING", "PAIRING_REQUEST_SENT" -> JarvisTheme.Amber
        else -> JarvisTheme.Red
    }
    val statusLabel = when (pairingStatus) {
        "APPROVED" -> "Connected & Approved"
        "PENDING" -> "Pending Approval"
        "PAIRING_REQUEST_SENT" -> "Pairing Request Sent"
        else -> "Not Connected"
    }
    val statusDesc = when (pairingStatus) {
        "APPROVED" -> "ใช้ข้อมูลสิทธิ์การเข้าถึงจาก Server (MT5 Bridge) โดยอัตโนมัติ"
        "PENDING" -> "กรุณากด Approve อุปกรณ์นี้ในหน้า Dashboard ของ Server:\n$serverUrl"
        "PAIRING_REQUEST_SENT" -> "ส่งคำขอเชื่อมต่อแล้ว กรุณากด Approve บน Server"
        else -> "กรุณาเชื่อมต่อ Server ที่ส่วน 'MT5 Bridge' ด้านล่างก่อนใช้งาน"
    }

    Surface(
        color = JarvisTheme.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = LabelSize,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    statusLabel,
                    color = statusColor,
                    fontSize = HelperSize,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                statusDesc,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = HelperSize,
                lineHeight = 16.sp
            )
        }
    }
}


// ─── Gemini Multi-Key Manager ────────────────────────────────────────────────
/**
 * Dialog จัดการ Gemini API keys หลายอัน (free tier หลายเมล์)
 * — add / edit / del เป็นปุ่มไอคอนเล็กต่อแถว
 * การเปลี่ยนแปลง persist ทันทีผ่าน ViewModel (ไม่ต้องกด Save ของหน้า Settings)
 * key ที่ใช้งานหลัก (primary จากช่อง Gemini ด้านบน) จะถูก merge เป็นหัว rotation chain อัตโนมัติ
 */
@Composable
private fun GeminiKeysDialog(
    keys: List<String>,
    primaryKey: String,
    onAdd: (String) -> Unit,
    onEdit: (old: String, new: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newKeyText by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Gemini API Keys (${keys.size})", color = Color.White, fontSize = SectionTitleSize, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "เพิ่ม key จากหลายเมล์ free tier — เมื่อ key ที่ใช้อยู่ติดลิมิต ระบบจะหมุนไป key ถัดไปอัตโนมัติ (โมเดลเดิม) ก่อนสลับโมเดล",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = HelperSize,
                )

                // ── รายการ keys ──
                if (keys.isEmpty()) {
                    Text(
                        "ยังไม่มี key สำรอง — key หลักจากช่อง Gemini ใช้งานอยู่เสมอ",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = HelperSize,
                    )
                }
                keys.forEach { k ->
                    Surface(color = JarvisTheme.Surface, shape = RoundedCornerShape(8.dp)) {
                        if (editingKey == k) {
                            // ── โหมดแก้ไข ──
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = fieldColors(),
                                )
                                IconButton(onClick = {
                                    onEdit(k, editText)
                                    editingKey = null
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = "บันทึก", tint = JarvisTheme.Green, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { editingKey = null }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "ยกเลิก", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            // ── โหมดแสดง ──
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(
                                        com.example.personalaibot.maskApiKey(k),
                                        color = Color.White,
                                        fontSize = BodySize,
                                    )
                                    if (k == primaryKey) {
                                        Text("PRIMARY — ใช้งานอยู่", color = JarvisTheme.Green, fontSize = HelperSize)
                                    }
                                }
                                IconButton(onClick = {
                                    editingKey = k
                                    editText = k
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "แก้ไข", tint = JarvisTheme.Cyan, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(k) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = JarvisTheme.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // ── เพิ่ม key ใหม่ ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKeyText,
                        onValueChange = { newKeyText = it },
                        placeholder = { Text("วาง Gemini API key ใหม่…", color = Color.White.copy(alpha = 0.35f), fontSize = HelperSize) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.size(4.dp))
                    IconButton(
                        onClick = {
                            if (newKeyText.isNotBlank()) {
                                onAdd(newKeyText)
                                newKeyText = ""
                            }
                        },
                        enabled = newKeyText.isNotBlank(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "เพิ่ม key",
                            tint = if (newKeyText.isNotBlank()) JarvisTheme.Green else Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("เสร็จ", color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold)
            }
        },
    )
}
