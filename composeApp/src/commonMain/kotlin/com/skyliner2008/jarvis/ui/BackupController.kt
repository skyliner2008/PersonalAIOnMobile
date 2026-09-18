package com.skyliner2008.jarvis.ui

import androidx.compose.runtime.Composable

/**
 * ปุ่มสำรอง/กู้คืนข้อมูลในหน้าตั้งค่า
 *
 * ทุกคำสั่งเปิดหน้าต่างเลือกไฟล์ของระบบ — เลือกเก็บในเครื่อง หรือ Google Drive ได้
 * @property supported false บนแพลตฟอร์มที่ยังไม่รองรับ (ซ่อนส่วนนี้ใน UI)
 */
class BackupController(
    val supported: Boolean,
    /** ส่งออกการเรียนรู้ของระบบปลุก AI เป็นไฟล์ .json */
    val exportLearning: () -> Unit,
    /** นำเข้าไฟล์การเรียนรู้ (รวมกับของเดิม) */
    val importLearning: () -> Unit,
    /** สำรองฐานข้อมูลทั้งหมดเป็นไฟล์ .zip */
    val backupDatabase: () -> Unit,
    /** เลือกไฟล์สำรองเพื่อกู้คืน — ต้องรีสตาร์ทแอปจึงจะมีผล */
    val restoreDatabase: () -> Unit,
    /** รีสตาร์ทแอปเพื่อใช้ฐานข้อมูลที่กู้คืน */
    val restartNow: () -> Unit,
    /** ยกเลิกการกู้คืนที่รออยู่ */
    val cancelRestore: () -> Unit,
    /** ขนาดฐานข้อมูล / OHLCV store / การเรียนรู้ สำหรับแสดงผล */
    val storageSummary: () -> String,
    /** ลบซีรีส์ OHLCV ที่ไม่ได้ใช้นานทันที */
    val pruneNow: () -> String
)

/** สถานะที่ UI แสดง */
sealed interface BackupStatus {
    data class Busy(val message: String) : BackupStatus
    data class Done(val message: String) : BackupStatus
    data class Failed(val message: String) : BackupStatus
    /** ไฟล์ผ่านการตรวจแล้ว รอรีสตาร์ท */
    data class RestoreReady(val message: String) : BackupStatus
}

@Composable
expect fun rememberBackupController(onStatus: (BackupStatus) -> Unit): BackupController
