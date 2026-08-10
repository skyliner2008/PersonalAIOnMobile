package com.example.personalaibot.ui

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ขนาดไฟล์สูงสุดต่อไฟล์ (base64 inline ให้ Gemini) */
private const val MAX_FILE_BYTES = 15 * 1024 * 1024 // 15 MB
/** text file ฝังเข้า prompt ได้สูงสุด (กัน context บวม) */
private const val MAX_TEXT_CHARS = 100_000

@Composable
actual fun rememberAttachmentPicker(onPicked: (List<ChatAttachment>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                uris.take(5).mapNotNull { uri ->
                    try {
                        val resolver = context.contentResolver
                        val name = queryDisplayName(context, uri) ?: "attachment"
                        val ext = name.substringAfterLast('.', "").lowercase()
                        var mime = resolver.getType(uri)
                            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            ?: "application/octet-stream"

                        if (ext in TEXT_ATTACHMENT_EXTENSIONS || mime.startsWith("text/")) {
                            val text = resolver.openInputStream(uri)?.use {
                                it.readBytes().toString(Charsets.UTF_8)
                            } ?: return@mapNotNull null
                            val truncated = if (text.length > MAX_TEXT_CHARS)
                                text.take(MAX_TEXT_CHARS) + "\n...[truncated]..." else text
                            ChatAttachment(name = name, mimeType = "text/plain", textContent = truncated)
                        } else {
                            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@mapNotNull null
                            if (bytes.size > MAX_FILE_BYTES) {
                                android.util.Log.w("AttachmentPicker", "Skip $name — too large (${bytes.size} bytes)")
                                return@mapNotNull null
                            }
                            // Gemini รองรับ native: image/*, application/pdf, audio/*, video/*
                            if (mime == "application/octet-stream" && ext == "pdf") mime = "application/pdf"
                            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            ChatAttachment(name = name, mimeType = mime, base64 = b64)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AttachmentPicker", "Read failed: $uri", e)
                        null
                    }
                }
            }
            if (result.isNotEmpty()) onPicked(result)
        }
    }

    return { launcher.launch(arrayOf("*/*")) }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    }
} catch (_: Exception) { null }
