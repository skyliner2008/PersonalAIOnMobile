package com.skyliner2008.jarvis.data

import com.skyliner2008.jarvis.logError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * บันทึกการประชุมที่เก็บไว้ในเครื่อง
 *
 * เดิมสรุปการประชุมถูกยิงเข้าห้องแชทหลัก ทำให้บทสนทนากับผู้ช่วยปนกับบันทึกงาน
 * และย้อนกลับมาอ่านทีหลังยาก — ตอนนี้เก็บเป็นรายการในหน้าประชุมแทน (แท็บ "สรุปประชุม")
 */
@Serializable
data class MeetingRecord(
    /** เวลาเริ่มบันทึก (epoch ms) — ใช้เป็น id ด้วย */
    val id: Long,
    /** ชื่อรายการรูปแบบ "วัน/เดือน/ปี-เวลา" */
    val title: String,
    /** เวลาที่บันทึกจริง (ไม่รวมช่วงกดพัก) หน่วยมิลลิวินาที */
    val durationMs: Long,
    /** สรุปแบบ markdown จากโมเดลแชท */
    val summary: String,
    /** บทประชุมคำต่อคำ */
    val transcript: String
)

/** ตัวช่วยแปลงรายการเป็น JSON ก้อนเดียว เก็บใน settings ได้โดยไม่ต้องเพิ่มตารางใหม่ */
object MeetingArchive {

    private val json = Json { ignoreUnknownKeys = true }

    /** จำนวนบันทึกสูงสุดที่เก็บไว้ — เก่ากว่านี้จะถูกตัดทิ้งเพื่อไม่ให้ settings บวม */
    const val MAX_RECORDS = 50

    fun decode(raw: String): List<MeetingRecord> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<MeetingRecord>>(raw)
        } catch (e: Exception) {
            logError("MeetingArchive", "อ่านบันทึกการประชุมไม่สำเร็จ: ${e.message}", e)
            emptyList()
        }
    }

    fun encode(records: List<MeetingRecord>): String =
        json.encodeToString(records.sortedByDescending { it.id }.take(MAX_RECORDS))

    /** ข้อความที่ให้ผู้ช่วยอ่านออกเสียง — ห้าม markdown เพราะระบบเสียงอ่านสัญลักษณ์ไม่ได้ */
    fun buildReadAloudPrompt(record: MeetingRecord): String = buildString {
        appendLine("[SYSTEM] ผู้ใช้กดปุ่มให้อ่านสรุปการประชุมให้ฟัง")
        appendLine("ชื่อบันทึก: ${record.title}")
        appendLine()
        appendLine("เนื้อหาสรุป:")
        appendLine(record.summary)
        appendLine()
        appendLine(
            "[VOICE RULE] อ่านสรุปนี้ให้ฟังเป็นภาษาไทยแบบเล่าเรื่อง ไล่ทีละหัวข้อจนครบ " +
                "ห้ามอ่าน markdown ตามตัวอักษร (ห้ามอ่านเครื่องหมาย # * - หรือชื่อหัวข้อภาษาอังกฤษ) " +
                "แปลงหัวข้อและ bullet เป็นประโยคพูดธรรมชาติ และห้ามสรุปให้สั้นลงกว่าเนื้อหาที่ให้มา"
        )
    }
}
