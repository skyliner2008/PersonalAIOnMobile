package com.skyliner2008.jarvis.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        // กู้คืนฐานข้อมูลที่รอไว้ (ถ้ามี) ก่อนเปิดไฟล์
        com.skyliner2008.jarvis.backup.DatabaseBackupManager.applyPendingRestore(context)
        return AndroidSqliteDriver(JarvisDatabase.Schema, context, "jarvis.db")
    }
    actual fun getAppDir(): String {
        return context.filesDir.absolutePath
    }
}
