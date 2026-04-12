package com.example.personalaibot

actual fun logDebug(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] ERROR: $message")
    throwable?.printStackTrace()
}
