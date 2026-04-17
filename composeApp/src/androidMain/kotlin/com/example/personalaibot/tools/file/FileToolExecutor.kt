package com.example.personalaibot.tools.file

import android.content.Context
import android.os.Environment
import com.example.personalaibot.service.FileAnalyzer
import java.io.File

/**
 * FileToolExecutor — ตัวรัน Tool จัดการไฟล์สำหรับ Android
 * จัดการ I/O จริงและเรียกใช้ FileAnalyzer สำหรับงานซับซ้อน
 */
class FileToolExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "FileToolExecutor"
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

        // 3. จัดการกรณีพิเศษสำหรับโฟลเดอร์มาตรฐาน
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
        
        android.util.Log.d("FileToolExecutor", "executeList on: ${dir.absolutePath}")

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
        if (!file.exists()) return "ไม่พบไฟล์: $path"
        if (file.isDirectory) return "$path เป็นโฟลเดอร์ ไม่สามารถอ่านเป็น text ได้"
        
        return file.readText()
    }

    private fun executeWrite(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val content = args["content"] ?: ""
        val file = File(path)
        
        // สร้างโฟลเดอร์ถ้ายังไม่มี
        file.parentFile?.mkdirs()
        file.writeText(content)
        
        return "เขียนไฟล์สำเร็จ: $path (${content.length} characters)"
    }

    private fun executeDelete(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val file = File(path)
        if (!file.exists()) return "ไม่พบไฟล์ที่ต้องการลบ: $path"
        if (!canMutatePath(file)) return "Error: mutation blocked for unsafe path '$path'"
        
        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return if (success) "ลบสำเร็จ: $path" else "ไม่สามารถลบได้ (อาจติด Permission)"
    }

    private suspend fun executeAnalyze(args: Map<String, String>): String {
        val path = args["path"] ?: return "ต้องระบุ path"
        val file = File(path)
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
