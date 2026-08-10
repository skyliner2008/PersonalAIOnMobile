package com.example.personalaibot.ui

import androidx.compose.runtime.Composable

/**
 * ไฟล์แนบในแชท
 * - textContent != null → ไฟล์ text (txt/md/csv/json/log/code) จะถูกฝังเข้า prompt ตรงๆ (ประหยัด token)
 * - base64 != null → ไฟล์ binary (รูป/PDF/DOCX) ส่งเป็น inline_data ให้ Gemini วิเคราะห์ native
 */
data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val base64: String? = null,
    val textContent: String? = null
) {
    val isText: Boolean get() = textContent != null
}

/**
 * คืน lambda สำหรับเปิด system file picker (เลือกได้หลายไฟล์)
 * เมื่อเลือกเสร็จจะเรียก onPicked พร้อมรายการไฟล์ที่แปลงแล้ว
 * (แพลตฟอร์มที่ยังไม่รองรับ → picker ว่างเปล่า ไม่ทำอะไร)
 */
@Composable
expect fun rememberAttachmentPicker(onPicked: (List<ChatAttachment>) -> Unit): () -> Unit

/** นามสกุลที่ถือเป็น text file (ฝังเข้า prompt แทน base64) */
val TEXT_ATTACHMENT_EXTENSIONS = setOf(
    "txt", "md", "csv", "json", "xml", "log", "yaml", "yml", "ini", "cfg", "toml",
    "kt", "kts", "py", "js", "ts", "java", "sql", "html", "htm", "css", "pine", "sh"
)
