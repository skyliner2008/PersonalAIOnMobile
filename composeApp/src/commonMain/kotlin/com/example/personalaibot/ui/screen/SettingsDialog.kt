package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val currentApiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val currentModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val currentLiveModel by viewModel.liveModelName.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isWidgetEnabled by viewModel.floatingWidgetEnabled.collectAsStateWithLifecycle()
    val currentOpenaiKey by viewModel.openaiApiKey.collectAsStateWithLifecycle()
    val currentClaudeKey by viewModel.claudeApiKey.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf(currentApiKey) }
    var mainModel by remember { mutableStateOf(currentModel) }
    var liveModel by remember { mutableStateOf(currentLiveModel) }
    var openaiApiKey by remember { mutableStateOf(currentOpenaiKey) }
    var claudeApiKey by remember { mutableStateOf(currentClaudeKey) }
    var mainExpanded by remember { mutableStateOf(false) }
    var liveExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var widgetActive by remember(isWidgetEnabled) { mutableStateOf(isWidgetEnabled) }

    val liveModels = availableModels.filter { model ->
        val n = model.name.lowercase()
        n.contains("live") || n.contains("flash") || n.contains("pro")
    }.ifEmpty { availableModels }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key", color = Color.White.copy(alpha = 0.7f)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Text(
                    if (apiKeyVisible) "Hide" else "Show",
                    color = JarvisTheme.Cyan,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            colors = fieldColors()
        )

        Text("Main model", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        ExposedDropdownMenuBox(
            expanded = mainExpanded,
            onExpandedChange = { mainExpanded = !mainExpanded }
        ) {
            OutlinedTextField(
                value = mainModel.removePrefix("models/"),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainExpanded) },
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                colors = fieldColors()
            )
            ExposedDropdownMenu(
                expanded = mainExpanded,
                onDismissRequest = { mainExpanded = false },
                containerColor = JarvisTheme.Card
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName ?: model.name.removePrefix("models/"), color = Color.White) },
                        onClick = { mainModel = model.name; mainExpanded = false }
                    )
                }
            }
        }

        Text("Live model", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        ExposedDropdownMenuBox(
            expanded = liveExpanded,
            onExpandedChange = { liveExpanded = !liveExpanded }
        ) {
            OutlinedTextField(
                value = liveModel.removePrefix("models/"),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = liveExpanded) },
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                colors = fieldColors()
            )
            ExposedDropdownMenu(
                expanded = liveExpanded,
                onDismissRequest = { liveExpanded = false },
                containerColor = JarvisTheme.Card
            ) {
                liveModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName ?: model.name.removePrefix("models/"), color = Color.White) },
                        onClick = { liveModel = model.name; liveExpanded = false }
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Floating Widget", color = Color.White, fontWeight = FontWeight.Medium)
                Text("Enable or disable widget service", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
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
                    checkedTrackColor = JarvisTheme.Cyan
                )
            )
        }

        Surface(
            color = if (allFilesAccessGranted) JarvisTheme.Cyan.copy(alpha = 0.12f) else JarvisTheme.Red.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (allFilesAccessGranted) "File permission granted" else "File permission is required",
                    color = if (allFilesAccessGranted) JarvisTheme.Cyan else JarvisTheme.Red,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                if (!allFilesAccessGranted) {
                    Button(
                        onClick = requestAllFilesPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Purple.copy(alpha = 0.35f))
                    ) {
                        Text("Grant permission", color = Color.White)
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text("OpenAI API Key", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        OutlinedTextField(
            value = openaiApiKey,
            onValueChange = { openaiApiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...", color = Color.White.copy(alpha = 0.35f)) },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            colors = fieldColors()
        )

        Text("Claude API Key", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        OutlinedTextField(
            value = claudeApiKey,
            onValueChange = { claudeApiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-ant-...", color = Color.White.copy(alpha = 0.35f)) },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            colors = fieldColors()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Surface)
            ) {
                Text("Cancel", color = Color.White)
            }
            Button(
                onClick = {
                    viewModel.updateSettings(apiKey, mainModel, liveModel, viewModel.voiceName.value)
                    viewModel.updateExternalApiKeys(openaiApiKey, claudeApiKey)
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan)
            ) {
                Text("Save", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisTheme.Cyan.copy(alpha = 0.6f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = JarvisTheme.Surface,
    unfocusedContainerColor = JarvisTheme.Surface,
    cursorColor = JarvisTheme.Cyan
)
