package com.example.personalaibot.service

import android.webkit.MimeTypeMap
import java.io.File
import java.util.Base64

/**
 * FileAnalyzer — ผู้ช่วยอัจฉริยะในการแกะข้อความจากไฟล์ PDF, DOCX, และรูปภาพ (OCR)
 */
class FileAnalyzer(private val context: android.content.Context) {

    /**
     * ดึงข้อมูลไฟล์เพื่อส่งให้ Gemini ประมวลผลโดยตรง (Native Document Processing)
     */
    fun readFileForGemini(file: File): String {
        return try {
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) 
                ?: "application/octet-stream"

            // หากเป็นไฟล์ข้อความทั่วไป ให้อ่านเป็น Text ปกติ (ประหยัด Token)
            if (isTextFile(extension)) {
                return file.readText()
            }

            // หากเป็นไฟล์ Binary (PDF, Image, Word) ให้แปลงเป็น Base64
            val bytes = file.readBytes()
            val base64Data = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                java.util.Base64.getEncoder().encodeToString(bytes)
            } else {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }

            "GEMINI_FILE::mime=$mimeType::data=$base64Data"
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    private fun isTextFile(ext: String): Boolean {
        return listOf("txt", "md", "csv", "json", "xml", "log").contains(ext)
    }

    suspend fun analyze(file: File): String {
        // ในระบบใหม่ เราจะส่งข้อมูลดิบให้ Gemini ไปวิเคราะห์เอง
        return readFileForGemini(file)
    }
}
