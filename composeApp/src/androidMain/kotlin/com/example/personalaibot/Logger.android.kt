package com.example.personalaibot

import android.util.Log

actual fun logDebug(tag: String, message: String) {
    // logcat: mask key แบบโชว์หัว-ท้าย เพื่อ debug ได้ว่าใช้ key ไหน (ตามที่ user ต้องการ)
    Log.d(tag, maskSensitiveForLogcat(message))
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) {
        // ไม่ส่ง throwable เข้า Log.e ตรงๆ เพราะ stacktrace อาจมี URL ที่แนบ API key
        // (ktor exception message ใส่ [url=...?key=...] มาด้วย) — mask เองแล้วค่อยพิมพ์
        val stack = maskSensitiveForLogcat(Log.getStackTraceString(throwable))
        Log.e(tag, "${maskSensitiveForLogcat(message)}\n$stack")
    } else {
        Log.e(tag, maskSensitiveForLogcat(message))
    }
}
