package com.example.personalaibot

import android.util.Log

actual fun logDebug(tag: String, message: String) {
    // logcat: mask key แบบโชว์หัว-ท้าย เพื่อ debug ได้ว่าใช้ key ไหน (ตามที่ user ต้องการ)
    val masked = maskSensitiveForLogcat(message)
    // Log.d จำกัด ~4000 bytes/บรรทัด — แบ่ง chunk ให้ log ยาวแสดงครบ ไม่ถูกตัดกลางข้อความ
    // Android's local JVM test stubs throw for android.util.Log.*. Logging must
    // never change application/test control flow, so keep the real logcat path
    // but swallow only the platform-stub failure.
    runCatching {
        if (masked.length <= 3500) {
            Log.d(tag, masked)
        } else {
            var i = 0
            var part = 1
            val total = (masked.length + 3499) / 3500
            while (i < masked.length) {
                val end = minOf(i + 3500, masked.length)
                Log.d(tag, "[part $part/$total] ${masked.substring(i, end)}")
                i = end
                part++
            }
        }
    }
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    runCatching {
        if (throwable != null) {
            // ไม่ส่ง throwable เข้า Log.e ตรงๆ เพราะ stacktrace อาจมี URL ที่แนบ API key
            // (ktor exception message ใส่ [url=...?key=...] มาด้วย) — mask เองแล้วค่อยพิมพ์
            val stack = maskSensitiveForLogcat(Log.getStackTraceString(throwable))
            Log.e(tag, "${maskSensitiveForLogcat(message)}\n$stack")
        } else {
            Log.e(tag, maskSensitiveForLogcat(message))
        }
    }
}
