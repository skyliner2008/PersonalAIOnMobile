package com.example.personalaibot.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// ═══════════════════════════════════════════════════════════════════
// CameraProvider — Abstract interface for multi-model camera analysis
//
// Supports: Gemini Live (3.1 Flash Live / 2.5 Flash / 2.0 Flash)
//           OpenAI   (GPT-4o / GPT-4.1 Vision)
//           Claude   (Sonnet 4.6 / Opus 4.6 Vision)
// ═══════════════════════════════════════════════════════════════════

/**
 * ผลลัพธ์จากการวิเคราะห์ภาพ 1 frame
 */
data class CameraAnalysisResult(
    val provider: CameraProviderType,
    val description: String,
    val detectedObjects: List<DetectedObject> = emptyList(),
    val labels: List<AnalysisLabel> = emptyList(),
    val confidence: Float = 0f,
    val processingTimeMs: Long = 0,
    val timestamp: Long = 0
)

/**
 * วัตถุที่ตรวจจับได้ในภาพ (สำหรับ AR Overlay + Object Detection)
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
    val color: String = "#00E5FF"    // Overlay color (default = Cyan)
)

/**
 * Bounding box สำหรับวาด overlay บนภาพ
 * ค่าเป็น normalized (0.0 - 1.0) เทียบกับขนาดภาพ
 */
data class BoundingBox(
    val x: Float,       // left
    val y: Float,       // top
    val width: Float,
    val height: Float
)

/**
 * Label/Tag สำหรับแสดงบน AR overlay
 */
data class AnalysisLabel(
    val text: String,
    val category: LabelCategory,
    val position: LabelPosition = LabelPosition.TOP_LEFT
)

enum class LabelCategory {
    OBJECT, TEXT, SCENE, EMOTION, ACTION, WARNING, INFO
}

enum class LabelPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * ประเภทของ Camera Provider ที่รองรับ
 */
enum class CameraProviderType(val displayName: String, val icon: String) {
    GEMINI_LIVE("Gemini Live", "🟢"),
    GEMINI_FLASH("Gemini Flash", "⚡"),
    OPENAI_GPT4O("GPT-4o Vision", "🔵"),
    OPENAI_GPT41("GPT-4.1 Vision", "🔵"),
    CLAUDE_SONNET("Claude Sonnet", "🟠"),
    CLAUDE_OPUS("Claude Opus", "🟠")
}

/**
 * โหมดการทำงานของกล้อง
 */
enum class CameraMode {
    LIVE_STREAM,    // ส่ง frame ต่อเนื่องตาม FPS
    SNAPSHOT,       // กดถ่ายภาพแยกวิเคราะห์ทีละ frame
    OBJECT_DETECT,  // ตรวจจับวัตถุอัตโนมัติ + bounding box
    AR_OVERLAY      // แสดง annotation/label ซ้อนบนภาพกล้อง
}

/**
 * สถานะการทำงานของ Camera Provider
 */
sealed class CameraProviderState {
    data object Idle : CameraProviderState()
    data object Connecting : CameraProviderState()
    data object Ready : CameraProviderState()
    data object Analyzing : CameraProviderState()
    data class Error(val message: String) : CameraProviderState()
}

/**
 * Interface หลักที่ทุก Camera Provider ต้อง implement
 */
interface CameraProvider {
    /** ชื่อ Provider */
    val providerType: CameraProviderType

    /** สถานะปัจจุบัน */
    val state: StateFlow<CameraProviderState>

    /** รองรับ Live streaming หรือไม่ (Gemini Live = true, OpenAI/Claude = snapshot only) */
    val supportsLiveStream: Boolean

    /** รองรับ Object Detection หรือไม่ */
    val supportsObjectDetection: Boolean

    /**
     * เริ่มต้น provider (ตั้งค่า API key, model name ฯลฯ)
     */
    suspend fun initialize(config: CameraProviderConfig)

    /**
     * วิเคราะห์ภาพ 1 frame (Snapshot mode หรือ non-live providers)
     * @param jpegBase64 ภาพ JPEG เข้ารหัส Base64
     * @param prompt     คำสั่ง/คำถามเกี่ยวกับภาพ (optional)
     * @return ผลวิเคราะห์
     */
    suspend fun analyzeFrame(jpegBase64: String, prompt: String = ""): CameraAnalysisResult

    /**
     * ส่ง frame ในโหมด Live streaming (เฉพาะ provider ที่รองรับ)
     * @param jpegBase64 ภาพ JPEG เข้ารหัส Base64
     */
    suspend fun sendLiveFrame(jpegBase64: String)

    /**
     * ตรวจจับวัตถุในภาพ + bounding boxes
     */
    suspend fun detectObjects(jpegBase64: String): List<DetectedObject>

    /**
     * ปิดการเชื่อมต่อ / คืนทรัพยากร
     */
    suspend fun release()
}

/**
 * Configuration สำหรับแต่ละ Provider
 */
data class CameraProviderConfig(
    val apiKey: String,
    val modelName: String = "",
    val maxTokens: Int = 1024,
    val temperature: Float = 0.3f,
    val language: String = "th",     // ภาษาที่ต้องการให้ตอบ
    val systemPrompt: String = "",
    val detectObjects: Boolean = false
)
