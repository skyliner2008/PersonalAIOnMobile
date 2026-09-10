package com.skyliner2008.jarvis

actual fun logDebug(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] ERROR: $message")
    throwable?.printStackTrace()
}
