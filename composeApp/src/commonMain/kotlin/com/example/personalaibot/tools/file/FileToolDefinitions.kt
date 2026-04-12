package com.example.personalaibot.tools.file

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

/**
 * FileToolDefinitions — รายการ Tool definitions สำหรับ Gemini เพื่อจัดการไฟล์ในมือถือ
 */
object FileToolDefinitions {

    val allDefinitions: List<FunctionDeclaration> = listOf(

        // ── 1. List Files ────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "file_list",
            description = """[FILES] ดูรายชื่อไฟล์และโฟลเดอร์ในตำแหน่งที่ระบุ
                |ใช้เพื่อสำรวจไฟล์ในเครื่องมือถือ
                |Path เริ่มต้น: /sdcard/ (Storage หลัก)
                |ตัวอย่าง: "ขอดูไฟล์ในโฟลเดอร์ Downloads", "มีไฟล์อะไรในเครื่องรอบ้าง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "path" to ParameterProperty("STRING", "Path ของโฟลเดอร์ที่ต้องการดู (Default: /sdcard/)")
                ),
                required = emptyList()
            )
        ),

        // ── 2. Read Text File ────────────────────────────────────────────────
        FunctionDeclaration(
            name = "file_read",
            description = """[FILES] อ่านเนื้อหาจากไฟล์ที่เป็นข้อความ (Text-based)
                |รองรับ: .txt, .log, .md, .kt, .py, .yaml, .json, .csv
                |ใช้เพื่ออ่านบันทึก, config หรือไฟล์โค้ด
                |ตัวอย่าง: "อ่านไฟล์ note.txt ให้หน่อย", "ดูเนื้อหาในข้อมุลล่าสุด" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "path" to ParameterProperty("STRING", "Path ของไฟล์ที่ต้องการอ่าน")
                ),
                required = listOf("path")
            )
        ),

        // ── 3. Write/Create File ─────────────────────────────────────────────
        FunctionDeclaration(
            name = "file_write",
            description = """[FILES] เขียนเนื้อหาลงไฟล์ หรือสร้างไฟล์ใหม่
                |ใช้เพื่อจดบันทึก, แก้ไข config หรือสร้างรายงาน
                |ตัวอย่าง: "บันทึกสรุปการประชุมลงไฟล์ summary.txt", "สร้างไฟล์ Hello.kt" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "path"    to ParameterProperty("STRING", "Path ของไฟล์ที่ต้องการเขียน"),
                    "content" to ParameterProperty("STRING", "เนื้อหาที่ต้องการบันทึก")
                ),
                required = listOf("path", "content")
            )
        ),

        // ── 4. Delete File ──────────────────────────────────────────────────
        FunctionDeclaration(
            name = "file_delete",
            description = """[FILES] ลบไฟล์หรือโฟลเดอร์ออกจากเครื่อง
                |ใช้เมื่อผู้ใช้สั่งลบไฟล์ที่ไม่ต้องการแล้ว
                |ตัวอย่าง: "ลบไฟล์ temp.txt", "จัดการลบไฟล์ขยะในเครื่อง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "path" to ParameterProperty("STRING", "Path ของไฟล์ที่ต้องการลบ")
                ),
                required = listOf("path")
            )
        ),

        // ── 5. Analyze Complex File (OCR/PDF/Office) ───────────────────────
        FunctionDeclaration(
            name = "file_analyze",
            description = """[FILES] เครื่องมืออัจฉริยะสำหรับอ่านไฟล์ที่ซับซ้อน (PDF, Word, Excel, รูปภาพ)
                |ความสามารถ:
                |  - Extract text จาก PDF
                |  - อ่านเนื้อหาจาก Word (.docx) และ Excel (.xlsx)
                |  - ทำ OCR อ่านข้อความจากรูปภาพ (JPG, PNG)
                |ใช้เพื่อสรุปเนื้อหาเอกสารหรือรูปภาพ
                |ตัวอย่าง: "สรุปรายละเอียดใน PDF ใบแจ้งหนี้", "อ่านค่าใน Excel งบประมาณ", "รูปนี้เขียนว่าอะไร" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "path" to ParameterProperty("STRING", "Path ของไฟล์เอกสารหรือรูปภาพ")
                ),
                required = listOf("path")
            )
        ),

        // ── 6. Organize/Move Files ──────────────────────────────────────────
        FunctionDeclaration(
            name = "file_move",
            description = """[FILES] ย้ายไฟล์หรือเปลี่ยนชื่อไฟล์
                |ใช้เพื่อจัดระเบียบไฟล์
                |ตัวอย่าง: "ย้ายไฟล์ PDF ทั้งหมดไปที่โฟลเดอร์ Documents/PDFs" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "source_path" to ParameterProperty("STRING", "Path เดิม"),
                    "target_path" to ParameterProperty("STRING", "Path ใหม่")
                ),
                required = listOf("source_path", "target_path")
            )
        ),

        // ── 7. Search Files ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "file_search",
            description = """[FILES] ค้นหาไฟล์ด้วยชื่อหรือนามสกุลไฟล์
                |ใช้เพื่อหาไฟล์ที่จำตำแหน่งไม่ได้
                |ตัวอย่าง: "หาไฟล์ PDF ทั้งหมดในเครื่อง", "ค้นหาไฟล์ที่ชื่อว่า budget" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query"     to ParameterProperty("STRING", "คำที่ต้องการค้นหาในชื่อไฟล์"),
                    "extension" to ParameterProperty("STRING", "นามสกุลไฟล์ที่ต้องการ (ถ้ามี) เช่น pdf, docx")
                ),
                required = listOf("query")
            )
        )
    )
}
