package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
internal fun EditPositionDialog(
    target: Mt5TradeItem,
    onDismiss: () -> Unit,
    onSave: (sl: String, tp: String) -> Unit
) {
    val initialSl = if (target.sl > 0.0) formatPrice(target.sl) else ""
    val initialTp = if (target.tp > 0.0) formatPrice(target.tp) else ""
    var slInput by remember { mutableStateOf(initialSl) }
    var tpInput by remember { mutableStateOf(initialTp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Card,
        titleContentColor = JarvisTheme.Cyan,
        textContentColor = Color.White.copy(alpha = 0.9f),
        title = {
            Text(
                "Edit SL / TP — ${target.symbol} #${target.ticket}",
                color = JarvisTheme.Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniLabel(modifier = Modifier.weight(1f), label = "Side", value = target.side.ifBlank { "-" })
                    MiniLabel(modifier = Modifier.weight(1f), label = "Open", value = formatPrice(target.priceOpen))
                    MiniLabel(modifier = Modifier.weight(1f), label = "Now", value = formatPrice(target.priceCurrent))
                }
                OutlinedTextField(
                    value = slInput,
                    onValueChange = { slInput = it },
                    label = { Text("Stop Loss (ราคา)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = OutlineSubtle,
                        cursorColor = JarvisTheme.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tpInput,
                    onValueChange = { tpInput = it },
                    label = { Text("Take Profit (ราคา)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = OutlineSubtle,
                        cursorColor = JarvisTheme.Cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "ปล่อยว่างเพื่อคงค่าเดิมใน MT5",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(slInput.trim(), tpInput.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisTheme.Cyan,
                    contentColor = Color.Black
                )
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
