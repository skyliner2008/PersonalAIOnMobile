package com.example.personalaibot.ui

import androidx.compose.runtime.Composable

/** iOS: ยังไม่รองรับ file picker — คืน no-op (ซ่อนปุ่มแนบไฟล์ฝั่ง UI ได้ในอนาคต) */
@Composable
actual fun rememberAttachmentPicker(onPicked: (List<ChatAttachment>) -> Unit): () -> Unit {
    return { }
}
