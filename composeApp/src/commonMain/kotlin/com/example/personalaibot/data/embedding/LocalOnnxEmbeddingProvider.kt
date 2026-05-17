package com.example.personalaibot.data.embedding

import com.example.personalaibot.logDebug
import com.example.personalaibot.logError

/**
 * LocalOnnxEmbeddingProvider — On-device embedding ด้วย ONNX Runtime
 *
 * Model: Multilingual MiniLM-L12-v2 (quantized ~120MB)
 * - 384 dims native
 * - รองรับ 50+ ภาษารวมภาษาไทย
 * - ทำงาน offline 100%
 * - Context: 512 tokens
 *
 * NOTE: ไฟล์นี้เป็น commonMain stub — actual ONNX inference อยู่ใน
 * androidMain (expect/actual pattern) ผ่าน onnxruntime-android library
 *
 * @since 2026-04-29
 */
class LocalOnnxEmbeddingProvider : EmbeddingProvider {

    override val providerId = "local_onnx"
    override val displayName = "Local (Multilingual AI)"
    override val isLocal = true
    /**
     * MiniLM-L12-v2 produces 384-dim raw vectors; we pad+L2-norm to 768
     * via fitToTargetDimension() so this provider is interchangeable with
     * cloud providers in the same vector store / cosine search.
     */
    override val nativeDimensions = 768
    private val rawDimensions = 384

    /** สถานะว่า model ถูกโหลดแล้วหรือยัง */
    private var modelLoaded = false

    /**
     * Delegate สำหรับ platform-specific ONNX inference
     * ถูก set จาก androidMain ผ่าน [setInferenceDelegate]
     */
    private var inferenceDelegate: (suspend (String) -> List<Float>)? = null

    /**
     * กำหนด delegate สำหรับ ONNX inference (เรียกจาก platform-specific code)
     *
     * ตัวอย่าง androidMain:
     * ```kotlin
     * localProvider.setInferenceDelegate { text ->
     *     // ORT Session inference
     *     val tokenized = tokenizer.encode(text)
     *     val result = ortSession.run(tokenized)
     *     result.toFloatList()
     * }
     * ```
     */
    fun setInferenceDelegate(delegate: suspend (String) -> List<Float>) {
        inferenceDelegate = delegate
        modelLoaded = true
    }

    override suspend fun embed(text: String, taskType: String): List<Float> {
        val delegate = inferenceDelegate
        if (delegate == null) {
            logError("LocalOnnx", "Model not loaded — inference delegate not set")
            return emptyList()
        }
        if (text.isBlank()) return emptyList()

        return try {
            // MiniLM-L12-v2 works best with direct text or simple prefix
            val raw = delegate(text)
            if (raw.isEmpty()) {
                logError("LocalOnnx", "Empty embedding returned")
                return emptyList()
            }
            // Pad 384→768 + L2-normalize so this vector is interchangeable
            // with cloud-provider vectors in the same store.
            raw.fitToTargetDimension(nativeDimensions)
        } catch (e: Exception) {
            logError("LocalOnnx", "Inference failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun isAvailable(): Boolean = modelLoaded && inferenceDelegate != null
}
