package com.skyliner2008.jarvis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.skyliner2008.jarvis.backup.DatabaseBackupManager
import com.skyliner2008.jarvis.backup.LearningTransfer
import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.tools.trading.OhlcvMaintenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android: ใช้ Storage Access Framework (CreateDocument / OpenDocument)
 * หน้าต่างเลือกไฟล์ของระบบมี Google Drive ให้เลือกเป็นปลายทาง/ต้นทางได้เลย (ไม่ต้องใช้ OAuth)
 */
@Composable
actual fun rememberBackupController(onStatus: (BackupStatus) -> Unit): BackupController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val status by rememberUpdatedState(onStatus)

    fun run(busy: String, block: suspend () -> BackupStatus) {
        status(BackupStatus.Busy(busy))
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
                .getOrElse { BackupStatus.Failed(it.message ?: it::class.simpleName ?: "ผิดพลาด") }
            status(result)
        }
    }

    val exportLearningLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run("กำลังส่งออกการเรียนรู้…") {
            val db = JarvisDatabaseHolder.database ?: error("ฐานข้อมูลยังไม่พร้อม")
            val bundle = LearningTransfer.buildBundle(db)
            val text = LearningTransfer.exportJson(db)
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
                ?: error("เขียนไฟล์ไม่ได้")
            BackupStatus.Done("ส่งออกการเรียนรู้ ${bundle.outcomes.size} แถวแล้ว (${OhlcvMaintenance.formatBytes(text.length.toLong())})")
        }
    }

    val importLearningLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run("กำลังนำเข้าการเรียนรู้…") {
            val db = JarvisDatabaseHolder.database ?: error("ฐานข้อมูลยังไม่พร้อม")
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("อ่านไฟล์ไม่ได้")
            BackupStatus.Done(LearningTransfer.importJson(db, text).describeTh())
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run("กำลังสำรองฐานข้อมูล…") {
            val r = DatabaseBackupManager.backupTo(context, uri)
            BackupStatus.Done(
                "สำรองฐานข้อมูลแล้ว ${OhlcvMaintenance.formatBytes(r.zippedBytes)} " +
                    "(ฐานข้อมูล ${OhlcvMaintenance.formatBytes(r.manifest.sizeBytes)}, schema ${r.manifest.schemaVersion})"
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run("กำลังตรวจไฟล์สำรอง…") {
            val m = DatabaseBackupManager.stageRestore(context, uri)
            val learning = m.tables["AnticipationFactorOutcome"]?.let { " · การเรียนรู้ $it แถว" } ?: ""
            val alerts = m.tables["AlertJob"]?.let { " · alert $it รายการ" } ?: ""
            BackupStatus.RestoreReady(
                "ไฟล์ถูกต้อง: สำรองจาก ${m.device} (แอป ${m.appVersion}, schema ${m.schemaVersion})$alerts$learning\n" +
                    "ข้อมูลปัจจุบันทั้งหมดจะถูกแทนที่เมื่อรีสตาร์ทแอป"
            )
        }
    }

    return BackupController(
        supported = true,
        exportLearning = { exportLearningLauncher.launch(LearningTransfer.suggestedFileName()) },
        importLearning = { importLearningLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        backupDatabase = { backupLauncher.launch(DatabaseBackupManager.suggestedFileName()) },
        restoreDatabase = {
            restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "application/x-sqlite3"))
        },
        restartNow = { DatabaseBackupManager.restartApp(context) },
        cancelRestore = {
            DatabaseBackupManager.cancelPendingRestore(context)
            status(BackupStatus.Done("ยกเลิกการกู้คืนแล้ว"))
        },
        storageSummary = {
            val s = OhlcvMaintenance.stats()
            val dbSize = context.getDatabasePath(DatabaseBackupManager.DB_NAME).length()
            val learning = runCatching {
                JarvisDatabaseHolder.database?.jarvisDatabaseQueries?.countFactorOutcomes()?.executeAsOne()
            }.getOrNull() ?: 0L
            "ฐานข้อมูล ${OhlcvMaintenance.formatBytes(dbSize)} · OHLCV ${s.symbols} สินทรัพย์ / ${s.series.size} ซีรีส์ / " +
                "${s.totalBars} แท่ง (≈${OhlcvMaintenance.formatBytes(s.estimatedBytes)}) · การเรียนรู้ $learning แถว"
        },
        pruneNow = {
            val r = OhlcvMaintenance.prune()
            "ลบ ${r.deletedSeries.size} ซีรีส์ที่ไม่ได้ใช้เกิน ${OhlcvMaintenance.IDLE_DAYS} วัน (${r.deletedBars} แท่ง)"
        }
    )
}
