package com.example.personalaibot.data.embedding

/**
 * EmbeddingProvider — Interface หลักสำหรับ Embedding ทุก Provider
 *
 * ทุก Provider ต้อง implement interface นี้เพื่อให้ระบบ Memory/Vector/GraphRAG
 * ทำงานได้แบบ provider-agnostic — สามารถสลับ Provider ได้อิสระ
 * ทั้ง Local (ONNX on-device) และ Cloud (Gemini, OpenAI, etc.)
 *
 * Dimension: ทุก provider ต้องส่งคืน embedding ที่มี dimension 768 หรือ
 * ใกล้เคียง (ผู้เรียกจะใช้ fitDimensions เพื่อ normalize ถ้าจำเป็น)
 *
 * @since 2026-04-29
 */
interface EmbeddingProvider {
    /** Provider ID เช่น "gemini", "local_onnx", "openai" */
    val providerId: String

    /** ชื่อที่แสดงใน UI */
    val displayName: String

    /** true = ทำงาน offline ได้ไม่ต้องใช้ internet */
    val isLocal: Boolean

    /** Dimension ที่ provider นี้ส่งคืน (native, ก่อน normalize) */
    val nativeDimensions: Int

    /**
     * สร้าง embedding vector จากข้อความ
     *
     * @param text ข้อความที่ต้องการแปลง
     * @param taskType ประเภทงาน: "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY" | "SEMANTIC_SIMILARITY"
     * @return List<Float> embedding vector — empty list หาก error
     */
    suspend fun embed(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT"
    ): List<Float>

    /**
     * ตรวจสอบว่า provider นี้พร้อมใช้งานหรือไม่
     * - Cloud provider: ต้องมี API key + internet
     * - Local provider: ต้องมี model file โหลดเสร็จแล้ว
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Normalize embedding vector ให้มี target dimension (default 768)
 * - ถ้า vector ยาวกว่า → Matryoshka truncate + L2 normalize
 * - ถ้า vector สั้นกว่า → zero-pad + L2 normalize
 * - ถ้าเท่ากัน → L2 normalize เฉยๆ
 */
fun List<Float>.fitToTargetDimension(target: Int = 768): List<Float> {
    val sized = when {
        size > target -> subList(0, target)
        size < target -> this + List(target - size) { 0f }
        else -> this.toList()
    }
    // L2 normalize
    var sumSq = 0.0
    for (v in sized) sumSq += v * v
    val norm = kotlin.math.sqrt(sumSq).toFloat()
    return if (norm > 0f && norm.isFinite()) {
        sized.map { it / norm }
    } else sized
}
