package com.example.personalaibot.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalaibot.ui.theme.JarvisTheme
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TradingChartScreen(
    symbol: String,
    interval: String,
    locale: String,
    hideSideToolbar: Boolean,
    refreshToken: Long,
    openSettingsSignal: Long,
    onSetLocale: (String) -> Unit,
    onSetHideSideToolbar: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    var showSettings by remember { mutableStateOf(false) }

    val state = remember { WebViewState(WebContent.Url("file:///android_asset/chart_widget/index.html")) }
    val navigator = rememberWebViewNavigator()

    LaunchedEffect(symbol, interval, locale, hideSideToolbar, refreshToken) {
        navigator.evaluateJavaScript(
            """
            if (window.jarvisWidgetBridge && window.jarvisWidgetBridge.setAll) {
                window.jarvisWidgetBridge.setAll("$symbol", "$interval", "$locale", ${hideSideToolbar});
            }
            """.trimIndent()
        )
    }

    LaunchedEffect(openSettingsSignal) {
        if (openSettingsSignal > 0L) showSettings = true
    }

    state.webSettings.isJavaScriptEnabled = true
    state.webSettings.androidWebSettings.safeBrowsingEnabled = true
    state.webSettings.androidWebSettings.domStorageEnabled = true
    state.webSettings.androidWebSettings.isAlgorithmicDarkeningAllowed = true

    Box(modifier = Modifier.fillMaxSize()) {
        WebView(
            state = state,
            navigator = navigator,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showSettings) {
        WidgetSettingsDialog(
            locale = locale,
            hideSideToolbar = hideSideToolbar,
            onSetLocale = onSetLocale,
            onSetHideSideToolbar = onSetHideSideToolbar,
            onClose = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsDialog(
    locale: String,
    hideSideToolbar: Boolean,
    onSetLocale: (String) -> Unit,
    onSetHideSideToolbar: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Widget Settings", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Locale",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = locale.lowercase().startsWith("th"),
                        onClick = { onSetLocale("th_TH") },
                        label = { Text("Thai") }
                    )
                    FilterChip(
                        selected = !locale.lowercase().startsWith("th"),
                        onClick = { onSetLocale("en") },
                        label = { Text("English") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hide Side Toolbar",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = hideSideToolbar,
                        onCheckedChange = onSetHideSideToolbar
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        containerColor = JarvisTheme.Dark
    )
}
