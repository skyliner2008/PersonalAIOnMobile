package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.ChatAttachment
import com.example.personalaibot.ui.rememberAttachmentPicker
import com.example.personalaibot.ui.theme.JarvisTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    onSend: (String, List<ChatAttachment>) -> Unit,
    onStartLive: () -> Unit,
    enabled: Boolean,
    voiceAvailable: Boolean
) {
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }

    val openPicker = rememberAttachmentPicker { picked ->
        attachments = (attachments + picked).distinctBy { it.name }.take(5)
    }

    fun send() {
        if (text.isBlank() && attachments.isEmpty()) return
        val body = text.ifBlank { "ช่วยวิเคราะห์ไฟล์ที่แนบมาให้หน่อย" }
        onSend(body, attachments)
        text = ""
        attachments = emptyList()
    }

    Surface(
        color = JarvisTheme.Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // แถว chips ไฟล์แนบที่เลือกไว้ (ลบทีละไฟล์ได้)
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachments.forEach { att ->
                        Surface(
                            color = JarvisTheme.Cyan.copy(0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = (if (att.isText) "📄 " else "🖼️ ") + att.name,
                                    color = Color.White.copy(0.9f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { attachments = attachments - att },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White.copy(0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                // ปุ่มแนบไฟล์
                IconButton(
                    onClick = openPicker,
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .background(JarvisTheme.Cyan.copy(0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = JarvisTheme.Cyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))

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
                val canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty())
                IconButton(
                    onClick = { send() },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (canSend) JarvisTheme.Cyan else Color.White.copy(0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.Black else Color.White.copy(0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
