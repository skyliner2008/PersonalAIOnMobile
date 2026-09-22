package com.skyliner2008.jarvis.voice

/**
 * เทียบคำปลุกจากข้อความที่ถอดเสียงได้ — ใช้ยืนยันหลัง energy VAD ก่อนปลุกหน้าจอ
 * (แยกจาก HotwordVerifier ฝั่ง Android เพื่อให้ unit test ได้ — review 2026-09-16)
 */
object WakeWordMatcher {

    /** การถอดเสียง "จาวิส" ที่พบบ่อยทั้งไทยและอังกฤษ */
    private val BASE_VARIANTS = listOf(
        "จาวิส", "จาร์วิส", "จาวิด", "จารวิส", "จ๊าวิส", "จาวิท", "จ้าวิส",
        "jarvis", "javis", "jervis"
    )

    fun variants(configuredWakeWord: String): List<String> {
        val extra = configuredWakeWord.trim().lowercase()
        return if (extra.isNotBlank() && extra !in BASE_VARIANTS) BASE_VARIANTS + extra else BASE_VARIANTS
    }

    fun matches(transcript: String, configuredWakeWord: String): Boolean {
        if (transcript.isBlank()) return false
        val t = transcript.lowercase()
        return variants(configuredWakeWord).any { t.contains(it) }
    }
}
