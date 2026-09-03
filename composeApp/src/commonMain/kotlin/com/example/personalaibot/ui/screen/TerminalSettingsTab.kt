package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

// ─── Settings Tab ────────────────────────────────────────────────────────────

@Composable
internal fun TerminalSettingsTab(
    defaultLot: String,
    defaultTpPoints: String,
    defaultSlPoints: String,
    maxDdPercent: String,
    onSetDefaultLot: (String) -> Unit,
    onSetDefaultTpPoints: (String) -> Unit,
    onSetDefaultSlPoints: (String) -> Unit,
    onSetMaxDdPercent: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Default Trade Settings", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    SettingRow(
                        label = "Default lot size",
                        helper = "Initial volume used for new orders (e.g. 0.01)",
                        value = defaultLot,
                        onValueChange = onSetDefaultLot,
                        keyboardType = KeyboardType.Decimal
                    )
                    SettingRow(
                        label = "Default TP (points)",
                        helper = "0 disables auto Take-Profit",
                        value = defaultTpPoints,
                        onValueChange = onSetDefaultTpPoints,
                        keyboardType = KeyboardType.Number
                    )
                    SettingRow(
                        label = "Default SL (points)",
                        helper = "0 disables auto Stop-Loss",
                        value = defaultSlPoints,
                        onValueChange = onSetDefaultSlPoints,
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Risk Controls", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    SettingRow(
                        label = "Max Drawdown %",
                        helper = "Auto-close all positions when drawdown exceeds this value",
                        value = maxDdPercent,
                        onValueChange = onSetMaxDdPercent,
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }
        }

        item {
            Text(
                "ค่าจะถูกบันทึกอัตโนมัติ · Changes save immediately",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    helper: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(helper, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        JarvisTextField(
            value = value,
            onValueChange = onValueChange,
            label = "",
            keyboardType = keyboardType,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
