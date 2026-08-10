package com.example.personalaibot.tools.file

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * XlsxWriter — เขียนไฟล์ .xlsx จริง (Office Open XML) แบบไม่พึ่ง library ภายนอก
 * รองรับ 1 sheet, inline strings + ตัวเลขอัตโนมัติ (พิมพ์ number เป็น numeric cell)
 *
 * เหตุผลที่เขียนเอง: Apache POI หนัก ~10MB+ และมีปัญหากับ Android/dex
 * โครงสร้าง xlsx = zip ของ XML ไม่กี่ไฟล์ เขียนเองเบาและเสถียรกว่า
 */
object XlsxWriter {

    fun write(file: File, rows: List<List<String>>, sheetName: String = "Sheet1") {
        require(rows.isNotEmpty()) { "rows must not be empty" }

        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")

            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")

            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${escapeXml(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets></workbook>""")

            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")

            put("xl/worksheets/sheet1.xml", buildSheetXml(rows))
        }
    }

    private fun buildSheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = "${colLetters(c)}${r + 1}"
                val trimmed = value.trim()
                val numeric = trimmed.toDoubleOrNull()
                if (numeric != null && trimmed.isNotEmpty()) {
                    append("""<c r="$ref"><v>$trimmed</v></c>""")
                } else {
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun colLetters(index: Int): String {
        var n = index
        var s = ""
        while (true) {
            s = ('A' + (n % 26)) + s
            n = n / 26 - 1
            if (n < 0) break
        }
        return s
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    /**
     * Parse CSV/TSV text → rows (รองรับ quoted cells ที่มี comma/newline ข้างใน)
     * ตัวคั่น: auto-detect ระหว่าง tab, semicolon, comma จากบรรทัดแรก
     */
    fun parseDelimited(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val firstLine = text.lineSequence().firstOrNull() ?: ""
        val delimiter = when {
            firstLine.contains('\t') -> '\t'
            firstLine.contains(';') -> ';'
            else -> ','
        }

        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        fun flushRow() {
            cells.add(cell.toString()); cell.clear()
            rows.add(cells.toList()); cells.clear()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> cell.append(ch)
                }
                ch == '"' && cell.isEmpty() -> inQuotes = true
                ch == delimiter -> { cells.add(cell.toString()); cell.clear() }
                ch == '\n' -> flushRow()
                ch == '\r' -> { /* skip */ }
                else -> cell.append(ch)
            }
            i++
        }
        // บรรทัดสุดท้าย (ถ้าไม่ลงท้าย newline)
        if (cell.isNotEmpty() || cells.isNotEmpty()) flushRow()
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
