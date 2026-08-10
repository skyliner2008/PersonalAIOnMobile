package com.example.personalaibot

expect fun logDebug(tag: String, message: String)
expect fun logError(tag: String, message: String, throwable: Throwable? = null)

/**
 * ลบข้อมูลอ่อนไหวออกจากข้อความก่อนเขียน log / ส่งเข้า tool loop / แสดงในแชท
 * — กัน API key หลุดผ่าน exception message ของ ktor (URL มี ?key=...) เช่นเคสจริง 2026-08-08
 */
fun sanitizeSensitive(text: String): String =
    text.replace(Regex("(?<=[?&])key=[^&\\s\\])}]*"), "key=***")
        .replace(Regex("(?i)bearer [A-Za-z0-9._\\-]+"), "Bearer ***")

/** แสดง key แบบ mask หัว 8 / ท้าย 4 (เช่น AIzaSyBQ***ElLY) — ใช้ debug ใน logcat เท่านั้น */
fun maskApiKey(key: String): String =
    if (key.length > 12) "${key.take(8)}***${key.takeLast(4)}" else "***"

/**
 * สำหรับ logcat เท่านั้น — mask key แบบโชว์หัว-ท้าย (เช่น key=AIza***ElLY)
 * เพื่อให้ debug ได้ว่าใช้ key/โมเดลไหนอยู่ โดยไม่เปิดเผย key ทั้งหมด
 * ห้ามใช้กับข้อความที่จะส่งเข้า model / แสดงในแชท / log ใน app — อันนั้นใช้ sanitizeSensitive
 */
fun maskSensitiveForLogcat(text: String): String =
    text.replace(Regex("(?<=[?&])key=([^&\\s\\])}]{8})([^&\\s\\])}]*?)([^&\\s\\])}]{4})"), "key=$1***$3")
        .replace(Regex("(?<=[?&])key=[^&\\s\\])}]*"), "key=***")
        .replace(Regex("(?i)bearer [A-Za-z0-9._\\-]+"), "Bearer ***")
