package com.example.personalaibot.tools.file

import android.content.Context
import android.os.Environment
import com.example.personalaibot.service.FileAnalyzer
import java.io.File

/**
 * FileToolExecutor — ตัวรัน Tool จัดการไฟล์สำหรับ Android
 * จัดการ I/O จริงและเรียกใช้ FileAnalyzer สำหรับงานซับซ้อน
 *
 * Security model (2026-07-29 hardening):
 * - ทุก operation (read/write/list/analyze/delete/move) ต้องผ่าน path guard
 * - Read/List/Analyze: ต้องอยู่ใน allowedRoots (shared storage + โฟลเดอร์สาธารณะมาตรฐาน)
 * - Write/Delete/Move: ต้องอยู่ใน allowedRoots และห้ามเป็น root เปล่า (canMutatePath)
 * - Write: จำกัดนามสกุลไฟล์ที่อนุญาต (text-based เท่านั้น) กันเขียนทับ binary/system
 * - ทุก path ถูก canonicalize ก่อนเช็ค กัน path traversal ด้วย ".."
 */
class FileToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "FileToolExecutor"

        /** นามสกุลที่อนุญาตให้ file_write เขียนได้ (text-based + xlsx ที่สร้างผ่าน XlsxWriter) */
        private val WRITABLE_EXTENSIONS = setOf(
            "txt", "md", "log", "json", "csv", "yaml", "yml", "xml",
            "html", "htm", "css", "ini", "cfg", "conf", "properties", "toml",
            "kt", "kts", "py", "js", "ts", "java", "sql", "sh", "bat", "pine",
            "c", "cpp", "h", "hpp", "dart", "go", "rs", "swift", "php", "svg", "env",
            "xlsx"
        )
    }

    private val analyzer = FileAnalyzer(context)
    private val rootPath: String by lazy {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val isManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        android.util.Log.d(TAG, "Storage Root: $path | isManager: $isManager")
        path
    }

    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return try {
            // ดึงค่า path และทำการ Resolve ให้เป็นพาธจริงของเครื่อง
            val rawPath = args["path"] ?: args["source_path"] ?: rootPath
            val resolvedPath = resolvePath(rawPath)

            val updatedArgs = args.toMutableMap().apply {
                if (containsKey("path")) put("path", resolvedPath)
                if (containsKey("source_path")) put("source_path", resolvedPath)
                if (containsKey("target_path")) put("target_path", resolvePath(get("target_path") ?: ""))
            }

            when (toolName) {
                "file_list"   -> executeList(updatedArgs)
                "file_read"   -> executeRead(updatedArgs)
                "file_write"  -> executeWrite(updatedArgs)
                "file_delete" -> executeDelete(updatedArgs)
                "file_analyze" -> executeAnalyze(updatedArgs)
                "file_move"   -> executeMove(updatedArgs)
                "file_search" -> executeSearch(updatedArgs)
                else -> "ไม่พบ Tool: $toolName"
            }
        } catch (e: Exception) {
            "Error executing $toolName: ${e.message}"
        }
    }

    /**
     * แปลง Virtual Path (เช่น /sdcard/) ให้เป็น Real Path ของเครื่อง
     */
    private fun resolvePath(input: String): String {
        android.util.Log.d(TAG, "Resolving path: '$input'")

        // ถ้าว่าง หรือเป็น root ให้คืน rootPath ทันที
        if (input.isBlank() || input == "/" || input == "/sdcard" || input == "sdcard" || input == "/storage/emulated/0") {
            return rootPath
        }
        var path = input.trim()

        // 1. แทนที่ Virtual Path ยอดฮิต (รองรับทั้งมีและไม่มี / ต่อท้าย)
        if (path.startsWith("/sdcard")) {
            path = path.replaceFirst("/sdcard", rootPath)
        } else if (path.startsWith("sdcard")) {
            path = path.replaceFirst("sdcard", rootPath)
        } else if (!path.startsWith("/") && !path.startsWith(rootPath)) {
            path = "$rootPath/$path"
        }

        // 2. จัดการเรื่อง // สองอัน
        path = path.replace("//", "/")

        // 3. ลบ trailing slash (ยกเว้นกรณีเป็น root เฉยๆ)
        if (path.length > rootPath.length && path.endsWith("/")) {
            path = path.substring(0, path.length - 1)
        }

        // 4. จัดการกรณีพิเศษสำหรับโฟลเดอร์มาตรฐาน
        val file = File(path)
        if (!file.exists()) {
            android.util.Log.d(TAG, "Path '$path' not found, trying fallback...")

            // ลองหา Download/ Downloads
            if (path.lowercase().endsWith("/download") || path.lowercase().endsWith("/downloads")) {
                val systemDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (systemDownload.exists()) {
                    android.util.Log.d(TAG, "Fallback to System Downloads: ${systemDownload.absolutePath}")
                    return systemDownload.absolutePath
                }
            }
        }

        android.util.Log.d(TAG, "Resolved path result: '$path'")
        return path
    }

    private fun executeList(args: Map<String, String>): String {
        val path = args["path"] ?: rootPath
        val dir = File(path)

        android.util.Log.d(TAG, "executeList on: ${dir.absolutePath}")

        if (!canReadPath(dir)) return "Error: access blocked for path outside allowed roots '$path'"
        if (!dir.exists()) return "ไม่พบโฟลเดอร์: $path (System Path: ${dir.absolutePath})"
        if (!dir.isDirectory) return "$path ไม่ใช่โฟลเดอร์"
        if (!dir.canRead()) return "มีโฟลเดอร์ในระบบ (${dir.absolutePath}) แต่ Android ไม่อนุญาตให้แอปอ่านไฟล์"

        val files = dir.listFiles() ?: return "ไม่สามารถดึงรายชื่อไฟล์ใน ${dir.absolutePath} ได้ (listFiles returned null)"
        if (files.isEmpty()) return "โฟลเดอร์ว่างเปล่า (ตำแหน่งจริง: ${dir.absolutePath})"

        return buildString {
            append("รายการไฟล์ใน $path (System Path: ${dir.absolutePath}):\n")
            files.sortedByDescending { it.isDirectory }.forEach { file ->
                val type = if (file.isDirectory) "[DIR]" else "[FILE]"
                val size = if (file.isFile) " (${formatSize(file.length())})" else ""
                append("$type ${file.name}$size\n")
            }
        }
    }

    private fun executeRead(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val file = File(path)
        if (!canReadPath(file)) return "Error: read blocked for path outside allowed roots '$path'"
        if (!file.exists()) return "ไม่พบไฟล์: $path"
        if (file.isDirectory) return "$path เป็นโฟลเดอร์ ไม่สามารถอ่านเป็น text ได้"

        // กัน context บวม — ไฟล์ใหญ่เกิน 200 KB ให้อ่านเฉพาะส่วนหัว + แจ้งขนาดจริง
        val MAX_READ_CHARS = 200_000
        val text = file.readText()
        return if (text.length > MAX_READ_CHARS) {
            text.take(MAX_READ_CHARS) +
                "\n\n...[truncated — ไฟล์มี ${text.length} ตัวอักษร แสดงเฉพาะ ${MAX_READ_CHARS} แรก " +
                "ใช้ file_analyze ถ้าต้องการให้ AI อ่านทั้งไฟล์]..."
        } else text
    }

    private fun executeWrite(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val content = args["content"] ?: ""
        val file = File(path)

        // Guard: ต้องอยู่ใน allowed roots เท่านั้น (กันเขียนทับไฟล์ระบบ/แอปอื่น)
        if (!canMutatePath(file)) {
            return "Error: write blocked for unsafe path '$path' (outside allowed roots หรือเป็น root เปล่า)"
        }

        // Guard: จำกัดนามสกุล — อนุญาตเฉพาะ text-based files
        val ext = file.extension.lowercase()
        if (ext.isNotEmpty() && ext !in WRITABLE_EXTENSIONS) {
            return "Error: write blocked — ไม่อนุญาตให้เขียนไฟล์นามสกุล '.$ext' " +
                   "(อนุญาตเฉพาะ: ${WRITABLE_EXTENSIONS.joinToString(", ")})"
        }

        // สร้างโฟลเดอร์ถ้ายังไม่มี
        file.parentFile?.mkdirs()

        // .xlsx: content คือ CSV/TSV (บรรทัดละ row) → แปลงเป็น Excel จริงผ่าน XlsxWriter
        val isXlsx = ext == "xlsx"
        var xlsxInfo = ""
        if (isXlsx) {
            val rows = XlsxWriter.parseDelimited(content)
            if (rows.isEmpty()) return "Error: content สำหรับ .xlsx ต้องเป็น CSV/TSV อย่างน้อย 1 แถว"
            XlsxWriter.write(file, rows, sheetName = file.nameWithoutExtension.take(31).ifBlank { "Sheet1" })
            xlsxInfo = " (${rows.size} แถว x ${rows.maxOf { it.size }} คอลัมน์ — Excel)"
        } else {
            file.writeText(content)
        }

        // Verify หลังเขียน — scoped storage บางเครื่องเขียนเงียบๆ ไม่ติด ต้องเช็คผลจริง
        val verified = file.exists() && (content.isEmpty() || file.length() > 0)
        android.util.Log.d(TAG, "Write verify: path=${file.absolutePath} exists=${file.exists()} size=${file.length()}")
        if (!verified) {
            return "Error: เขียนไฟล์ไม่สำเร็จ — หลังเขียนแล้วไฟล์ไม่มีอยู่จริง (path: ${file.absolutePath}) อาจติดสิทธิ์ scoped storage"
        }

        // Trigger media scan เพื่อให้ file manager / gallery เห็นไฟล์ใหม่ทันที (ไม่ scan = บางเครื่องไม่แสดง)
        try {
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            android.util.Log.d(TAG, "Media scan requested: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Media scan failed: ${e.message}")
        }

        return "เขียนไฟล์สำเร็จ: $path (${content.length} characters)$xlsxInfo"
    }

    private fun executeDelete(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val file = File(path)
        if (!file.exists()) return "ไม่พบไฟล์ที่ต้องการลบ: $path"
        if (!canMutatePath(file)) return "Error: mutation blocked for unsafe path '$path'"

        // Guard พิเศษ: ห้ามลบโฟลเดอร์สาธารณะมาตรฐานทั้งโฟลเดอร์ (Download/Documents/DCIM/Pictures)
        // — deleteRecursively บนโฟลเดอร์เหล่านี้ = ลบไฟล์ผู้ใช้หมดเครื่อง
        if (file.isDirectory && isProtectedPublicDir(file)) {
            return "Error: delete blocked — ห้ามลบโฟลเดอร์สาธารณะมาตรฐานทั้งโฟลเดอร์ (${file.name}) ลบได้เฉพาะไฟล์/โฟลเดอร์ย่อยข้างใน"
        }

        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return if (success) "ลบสำเร็จ: $path" else "ไม่สามารถลบได้ (อาจติด Permission)"
    }

    /** โฟลเดอร์สาธารณะมาตรฐานที่ห้ามลบทั้งโฟลเดอร์ */
    private fun isProtectedPublicDir(file: File): Boolean {
        val protectedDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        )
        val normalized = normalizePath(file)
        return protectedDirs.any { normalizePath(it) == normalized }
    }

    private suspend fun executeAnalyze(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val file = File(path)
        if (!canReadPath(file)) return "Error: access blocked for path outside allowed roots '$path'"
        if (!file.exists()) return "ไม่พบไฟล์: $path"

        return "ผลการวิเคราะห์ไฟล์ $path:\n---\n" + analyzer.analyze(file)
    }

    private fun executeMove(args: Map<String, String>): String {
        val source = args["source_path"] ?: return "ต้องระบุ source_path"
        val target = args["target_path"] ?: return "ต้องระบุ target_path"

        val srcFile = File(source)
        val dstFile = File(target)

        if (!srcFile.exists()) return "ไม่พบไฟล์ต้นทาง: $source"
        if (!canMutatePath(srcFile) || !isPathInAllowedRoots(dstFile)) {
            return "Error: move blocked for unsafe path(s)"
        }

        dstFile.parentFile?.mkdirs()

        val success = srcFile.renameTo(dstFile)
        return if (success) "ย้าย/เปลี่ยนชื่อสำเร็จ: $source -> $target"
               else "ย้ายไม่สำเร็จ (อาจข้าม Partition หรือติด Permission)"
    }

    private fun executeSearch(args: Map<String, String>): String {
        val query = args["query"]?.lowercase() ?: return "ต้องระบุ query"
        val ext = args["extension"]?.lowercase()?.removePrefix(".")

        val root = File(rootPath)
        val results = mutableListOf<File>()

        // ค้นหาแบบตื้นๆ ในโฟลเดอร์มาตรฐานของ Android เพื่อความเร็ว
        val searchDirs = listOf(
            File(rootPath),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        ).filter { it.exists() }.distinct()

        searchDirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val nameMatch = file.name.lowercase().contains(query)
                val extMatch = ext == null || file.extension.lowercase() == ext
                if (nameMatch && extMatch) {
                    results.add(file)
                }
            }
        }

        if (results.isEmpty()) return "ไม่พบไฟล์ที่ตรงกับ '$query'${if(ext!=null)" (.$ext)" else ""}"

        return buildString {
            append("พบ ${results.size} ไฟล์:\n")
            results.take(20).forEach { file ->
                append("- ${file.absolutePath}\n")
            }
            if (results.size > 20) append("...และอื่นๆ อีก ${results.size - 20} ไฟล์")
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return "%.1f %sB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    // ─── Path Security Guards ─────────────────────────────────────────────

    /** Read guard — อนุญาตเฉพาะ path ใน allowed roots (รวม root เองด้วย) */
    private fun canReadPath(file: File): Boolean = isPathInAllowedRoots(file)

    /** Mutation guard — ต้องอยู่ใน allowed roots และห้ามเป็น root เปล่า */
    private fun canMutatePath(file: File): Boolean {
        if (!isPathInAllowedRoots(file)) return false
        val normalized = normalizePath(file)
        val rootNormalized = normalizePath(File(rootPath))
        return normalized != rootNormalized
    }

    private fun isPathInAllowedRoots(file: File): Boolean {
        val normalized = normalizePath(file)
        return allowedRoots().any { root ->
            val rootNormalized = normalizePath(root)
            normalized == rootNormalized || normalized.startsWith("$rootNormalized/")
        }
    }

    private fun normalizePath(file: File): String {
        return try {
            // canonicalFile resolve ".." และ symlink ก่อนเช็ค — กัน path traversal
            file.canonicalFile.absolutePath.replace("\\", "/").trimEnd('/')
        } catch (_: Exception) {
            file.absolutePath.replace("\\", "/").trimEnd('/')
        }
    }

    private fun allowedRoots(): List<File> {
        val roots = mutableListOf<File>()
        roots.add(File(rootPath))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        return roots.filter { it.exists() }.distinctBy { normalizePath(it) }
    }
}
