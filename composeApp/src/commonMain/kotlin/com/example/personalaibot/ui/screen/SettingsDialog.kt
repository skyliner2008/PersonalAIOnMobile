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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
    ProviderMeta("gemini",     "Google Gemini",    "AIza…",   "AIza…"),
    ProviderMeta("vertexai",   "Vertex AI (GCP)",  "ADC",     "ADC — ใช้ผ่าน Server"),
    ProviderMeta("openai",     "OpenAI",           "sk-…",    "sk-…"),
    ProviderMeta("claude",     "Anthropic Claude", "sk-ant-…", "sk-ant-…"),
    ProviderMeta("openrouter", "OpenRouter",       "sk-or-…", "sk-or-…"),
    ProviderMeta("minimax",    "MiniMax",          "minimax-…", "minimax-…"),
)

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
    val currentOpenaiKey by viewModel.openaiApiKey.collectAsStateWithLifecycle()
    val currentClaudeKey by viewModel.claudeApiKey.collectAsStateWithLifecycle()
    val currentOpenRouterKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val currentMinimaxKey by viewModel.minimaxApiKey.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.modelDownloadProgress.collectAsStateWithLifecycle()
    val autoTradingConfig by viewModel.autoTrading.config.collectAsStateWithLifecycle()

    var geminiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var openaiKey by remember(currentOpenaiKey) { mutableStateOf(currentOpenaiKey) }
    var claudeKey by remember(currentClaudeKey) { mutableStateOf(currentClaudeKey) }
    var openRouterKey by remember(currentOpenRouterKey) { mutableStateOf(currentOpenRouterKey) }
    var minimaxKey by remember(currentMinimaxKey) { mutableStateOf(currentMinimaxKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    var mainModel by remember(currentModel) { mutableStateOf(currentModel) }
    var liveModel by remember(currentLiveModel) { mutableStateOf(currentLiveModel) }
    var widgetActive by remember(isWidgetEnabled) { mutableStateOf(isWidgetEnabled) }

    var selectedProvider by remember { mutableStateOf(if (mainModel.contains("/")) mainModel.substringBefore("/") else "gemini") }
    var providerExpanded by remember { mutableStateOf(false) }
    var mainExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var showFreeOnly by remember { mutableStateOf(false) }
    var providerModels by remember { mutableStateOf<List<com.example.personalaibot.data.providers.LlmModelInfo>>(emptyList()) }
    var liveModels by remember { mutableStateOf<List<com.example.personalaibot.data.providers.LlmModelInfo>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProvider, showFreeOnly) {
        isLoadingModels = true
        val keyOverride = when (selectedProvider) {
            "gemini" -> geminiKey
            "openai" -> openaiKey
            "claude" -> claudeKey
            "openrouter" -> openRouterKey
            "minimax" -> minimaxKey
            else -> null
        }
        providerModels = viewModel.getModelsForProvider(selectedProvider, showFreeOnly, keyOverride)
        liveModels = viewModel.getLiveCapableModels(selectedProvider, keyOverride)
        isLoadingModels = false
    }

    var sectApiKeys by remember { mutableStateOf(true) }
    var sectModels by remember { mutableStateOf(true) }
    var sectLive by remember { mutableStateOf(false) }
    var sectLocalAi by remember { mutableStateOf(false) }
    var sectWidget by remember { mutableStateOf(false) }
    var sectPermissions by remember { mutableStateOf(false) }

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
                val key: String
                val setter: (String) -> Unit
                when (meta.id) {
                    "gemini" -> { key = geminiKey; setter = { geminiKey = it } }
                    "openai" -> { key = openaiKey; setter = { openaiKey = it } }
                    "claude" -> { key = claudeKey; setter = { claudeKey = it } }
                    "openrouter" -> { key = openRouterKey; setter = { openRouterKey = it } }
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
                                liveModels = viewModel.getLiveCapableModels(meta.id, key)
                            }
                            testing[meta.id] = false
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ─── Main Model section ─────────────────────────────────────────────
        SectionCard(
            title = "Main Model",
            icon = Icons.Default.Memory,
            expanded = sectModels,
            onToggle = { sectModels = !sectModels },
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
                            DropdownMenuItem(
                                text = { Text("No models found", color = Color.White.copy(alpha = 0.5f)) },
                                onClick = {},
                            )
                        }
                        providerModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName, color = Color.White, fontSize = BodySize)
                                        if (model.isFree) Text("FREE", color = JarvisTheme.Cyan, fontSize = HelperSize)
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
            // Free-only filter — always visible, enabled only for OpenRouter
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = showFreeOnly,
                    onCheckedChange = { showFreeOnly = it },
                    enabled = selectedProvider == "openrouter",
                    colors = CheckboxDefaults.colors(
                        checkedColor = JarvisTheme.Cyan,
                        uncheckedColor = Color.White.copy(alpha = 0.5f),
                        checkmarkColor = JarvisTheme.Card,
                        disabledCheckedColor = Color.White.copy(alpha = 0.2f),
                        disabledUncheckedColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Text(
                    "Show free models only (OpenRouter)",
                    color = if (selectedProvider == "openrouter") Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
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
                                    "No live-capable models found",
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
                "Filtered by capability (vision / realtime / multimodal). Falls back to keyword match when capability flags aren't published.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = HelperSize,
                modifier = Modifier.padding(top = 6.dp),
            )
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
                    viewModel.updateExternalApiKeys(openaiKey, claudeKey, openRouterKey, minimaxKey)
                    onDismiss()
                },
                modifier = Modifier.weight(1f).heightIn(min = FieldHeight),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan),
            ) {
                Text("Save", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold, fontSize = BodySize)
            }
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
) {
    val formatHint = remember(key, meta.id) { ApiKeyTester.validateFormat(meta.id, key) }
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
                    formatHint != null && key.isNotBlank() -> JarvisTheme.Amber
                    result != null && !result.ok -> JarvisTheme.Red
                    result != null && result.ok -> JarvisTheme.Green
                    else -> Color.White.copy(alpha = 0.5f)
                }
                val hintText = when {
                    formatHint != null && key.isNotBlank() -> formatHint
                    result != null -> result.displayMessage
                    key.isBlank() -> "Prefix: ${meta.keyPrefixHint}"
                    else -> "Prefix OK — tap Test to verify"
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
