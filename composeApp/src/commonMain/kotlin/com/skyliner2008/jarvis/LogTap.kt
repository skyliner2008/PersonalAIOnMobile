package com.skyliner2008.jarvis

/**
 * A copy of every log line for tests that replay a whole flow and read it back as a timeline (the live-session
 * simulation in androidUnitTest: there android.util.Log is a stub, so logcat sees nothing). Null in the app.
 */
object LogTap {
    @kotlin.jvm.Volatile
    var sink: ((tag: String, message: String) -> Unit)? = null
}
