package com.skyliner2008.jarvis.ui

import androidx.compose.runtime.Composable

/** iOS: ยังไม่รองรับการสำรอง/กู้คืน */
@Composable
actual fun rememberBackupController(onStatus: (BackupStatus) -> Unit): BackupController =
    BackupController(
        supported = false,
        exportLearning = {}, importLearning = {}, backupDatabase = {}, restoreDatabase = {},
        restartNow = {}, cancelRestore = {}, storageSummary = { "" }, pruneNow = { "" }
    )
