package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    onStartLive: () -> Unit,
    enabled: Boolean,
    voiceAvailable: Boolean
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = JarvisTheme.Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Voice / Live button
            if (voiceAvailable) {
                IconButton(
                    onClick = onStartLive,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .background(JarvisTheme.Cyan.copy(0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Start Live",
                        tint = JarvisTheme.Cyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            // Text field
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("พิมพ์ข้อความ...", color = Color.White.copy(0.4f), fontSize = 14.sp) },
                enabled = enabled,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisTheme.Cyan.copy(0.5f),
                    unfocusedBorderColor = Color.White.copy(0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = JarvisTheme.Cyan,
                    focusedContainerColor = JarvisTheme.Card,
                    unfocusedContainerColor = JarvisTheme.Card
                ),
                shape = RoundedCornerShape(20.dp)
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            IconButton(
                onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (text.isNotBlank() && enabled) JarvisTheme.Cyan else Color.White.copy(0.1f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank() && enabled) Color.Black else Color.White.copy(0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
