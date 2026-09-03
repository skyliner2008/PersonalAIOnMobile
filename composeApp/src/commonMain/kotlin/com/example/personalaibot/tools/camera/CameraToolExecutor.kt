package com.example.personalaibot.tools.camera

import com.example.personalaibot.camera.CameraAnalysisService
import com.example.personalaibot.camera.CameraMode
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError

class CameraToolExecutor(
    private val cameraService: CameraAnalysisService
) {
    suspend fun execute(name: String, args: Map<String, String>): String {
        return try {
            when (name) {
                "camera_analyze_scene" -> executeAnalyzeScene(args["prompt"])
                "camera_detect_objects" -> executeDetectObjects(args["target"])
                "camera_read_text" -> executeReadText()
                "camera_switch_provider" -> executeSwitchProvider(args["provider"])
                "camera_switch_mode" -> executeSwitchMode(args["mode"])
                else -> "Unknown camera tool: $name"
            }
        } catch (e: Exception) {
            logError("CameraExecutor", "Error executing $name", e)
            "Error: ${e.message}"
        }
    }

    private suspend fun executeAnalyzeScene(customPrompt: String?): String {
        logDebug("CameraExecutor", "Analyzing scene...")
        val prompt = customPrompt ?: "อธิบายสิ่งที่เห็นในกล้องตอนนี้อย่างละเอียด"
        val result = cameraService.captureAndAnalyze("", prompt)
        return result.description
    }

    private suspend fun executeDetectObjects(target: String?): String {
        logDebug("CameraExecutor", "Switching to Object Detection mode...")
        cameraService.switchMode(CameraMode.OBJECT_DETECT)
        val targetText = if (target != null) " (กำลังค้นหา: $target)" else ""
        return "✅ สลับเข้าสู่โหมดตรวจจับวัตถุแล้ว$targetText หน้าจอของคุณจะแสดงกรอบสี่เหลี่ยมรอบวัตถุที่ตรวจพบ"
    }

    private suspend fun executeReadText(): String {
        logDebug("CameraExecutor", "Reading text from camera...")
        val result = cameraService.captureAndAnalyze("", "อ่านข้อความทั้งหมดที่ปรากฏในกล้อง (OCR) และสรุปใจความสำคัญ")
        return result.description
    }

    private suspend fun executeSwitchProvider(provider: String?): String {
        val clean = provider?.lowercase()?.trim()?.replace("-", "_")?.replace(" ", "_")
        val type = when {
            clean == null || clean.isEmpty() -> return "❌ กรุณาระบุชื่อ Provider (เช่น gemini_live, gemini_flash, openai_gpt4o, claude_sonnet)"
            clean.contains("live") -> CameraProviderType.GEMINI_LIVE
            clean.contains("flash") || clean == "gemini" -> CameraProviderType.GEMINI_FLASH
            clean.contains("41") -> CameraProviderType.OPENAI_GPT41
            clean.contains("4o") || clean.contains("openai") || clean.contains("gpt") -> CameraProviderType.OPENAI_GPT4O
            clean.contains("opus") -> CameraProviderType.CLAUDE_OPUS
            clean.contains("sonnet") || clean.contains("claude") || clean.contains("anthropic") -> CameraProviderType.CLAUDE_SONNET
            else -> return "❌ ไม่รองรับ Provider '$provider' กรุณาเลือก: gemini_live, gemini_flash, openai_gpt4o, claude_sonnet"
        }
        cameraService.switchProvider(type)
        return "✅ สลับไปใช้ ${type.displayName} สำหรับวิเคราะห์ภาพแล้ว"
    }

    private suspend fun executeSwitchMode(mode: String?): String {
        val cleanMode = mode?.lowercase()?.trim()?.replace("-", "_")?.replace(" ", "_")
        val cameraMode = when {
            cleanMode == null || cleanMode.isEmpty() -> return "❌ กรุณาระบุโหมดกล้อง (เช่น live_stream, snapshot, object_detect, ar_overlay)"
            cleanMode.contains("live") || cleanMode.contains("stream") -> CameraMode.LIVE_STREAM
            cleanMode.contains("snap") || cleanMode.contains("photo") || cleanMode.contains("picture") -> CameraMode.SNAPSHOT
            cleanMode.contains("object") || cleanMode.contains("detect") -> CameraMode.OBJECT_DETECT
            cleanMode.contains("ar") || cleanMode.contains("overlay") -> CameraMode.AR_OVERLAY
            else -> return "❌ ไม่รองรับโหมด '$mode' กรุณาเลือก: live_stream, snapshot, object_detect, ar_overlay"
        }
        cameraService.switchMode(cameraMode)
        return "✅ เปลี่ยนโหมดกล้องเป็น ${cameraMode.name} เรียบร้อยแล้ว"
    }
}
