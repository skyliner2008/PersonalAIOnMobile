package com.example.personalaibot.data.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.example.personalaibot.logDebug
import com.example.personalaibot.logError
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * AndroidLocalOnnxManager — จัดการดาวน์โหลดและรัน ONNX Runtime + Tokenizer บน Android
 * รุ่นรองรับภาษาไทย: paraphrase-multilingual-MiniLM-L12-v2
 */
class AndroidLocalOnnxManager(
    private val context: Context,
    private val provider: LocalOnnxEmbeddingProvider
) {
    // ใช้โมเดล Multilingual ที่รองรับภาษาไทย (Standard Ops 100%)
    private val modelUrl = "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_q4.onnx"
    private val tokenizerUrl = "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json"

    private val modelFile = File(context.filesDir, "multilingual_mini_lm_q4.onnx")
    private val tokenizerFile = File(context.filesDir, "multilingual_tokenizer.json")

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: LocalKotlinTokenizer? = null

    val downloadProgress = MutableStateFlow(-1f)

    suspend fun initOrDownload() {
        if (modelFile.exists() && tokenizerFile.exists()) {
            loadModel()
        } else {
            // ยังไม่โหลดอัตโนมัติ รอให้ UI กดดาวน์โหลดเอง
            logDebug("AndroidONNX", "Model files not found. Ready to download.")
        }
    }

    suspend fun downloadModelFiles() {
        withContext(Dispatchers.IO) {
            try {
                downloadProgress.value = 0f
                HttpClient(Android).use { client ->
                    logDebug("AndroidONNX", "Downloading Multilingual model: $modelUrl")
                    downloadFile(client, modelUrl, modelFile) { p -> 
                        downloadProgress.value = p * 0.9f 
                    }

                    logDebug("AndroidONNX", "Downloading tokenizer: $tokenizerUrl")
                    downloadFile(client, tokenizerUrl, tokenizerFile) { p ->
                        downloadProgress.value = 0.9f + (p * 0.1f) 
                    }
                }
                
                downloadProgress.value = 1f
                loadModel()
            } catch (e: Exception) {
                logError("AndroidONNX", "Failed to download model", e)
                downloadProgress.value = -1f
            }
        }
    }

    private suspend fun downloadFile(client: HttpClient, url: String, dest: File, onProgress: (Float) -> Unit) {
        client.prepareGet(url).execute { response ->
            val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: 1L
            var bytesReadTotal = 0L

            val channel: ByteReadChannel = response.bodyAsChannel()
            dest.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesReadTotal += read
                    onProgress(bytesReadTotal.toFloat() / contentLength.toFloat())
                }
            }
        }
    }

    private suspend fun loadModel() {
        withContext(Dispatchers.IO) {
            try {
                logDebug("AndroidONNX", "Loading Multilingual tokenizer...")
                tokenizer = LocalKotlinTokenizer(tokenizerFile.absolutePath)

                logDebug("AndroidONNX", "Loading ONNX model...")
                ortEnv = OrtEnvironment.getEnvironment()
                ortSession = ortEnv?.createSession(modelFile.absolutePath)
                
                logDebug("AndroidONNX", "Multilingual model loaded successfully.")
                provider.setInferenceDelegate { text -> runInference(text) }
            } catch (e: Exception) {
                logError("AndroidONNX", "Failed to load model or tokenizer", e)
            }
        }
    }

    private fun runInference(text: String): List<Float> {
        val session = ortSession ?: return emptyList()
        val env = ortEnv ?: return emptyList()
        val tok = tokenizer ?: return emptyList()

        return try {
            // 1. Tokenize (Pure Kotlin)
            var tokenIds = tok.encode(text)
            // Truncate to 512 for MiniLM
            if (tokenIds.size > 512) {
                tokenIds = tokenIds.take(512)
            }
            
            // 2. Prepare Inputs
            val inputIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
            val attentionMask = LongArray(tokenIds.size) { 1L }

            val shape = longArrayOf(1, tokenIds.size.toLong())
            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )

            // 3. Run Inference
            val result = session.run(inputs)
            val outputValue = result.get(0).value as Array<*>
            
            val embeddings = if (outputValue.size == 1 && outputValue[0] is FloatArray) {
                (outputValue[0] as FloatArray).toList()
            } else if (outputValue.size == 1 && outputValue[0] is Array<*>) {
                val seq = outputValue[0] as Array<*>
                val seqLen = seq.size
                val firstRow = seq[0] as FloatArray
                val hiddenSize = firstRow.size
                val mean = FloatArray(hiddenSize)
                for (i in 0 until seqLen) {
                    val row = seq[i] as FloatArray
                    for (j in 0 until hiddenSize) {
                        mean[j] += row[j]
                    }
                }
                for (j in 0 until hiddenSize) {
                    mean[j] /= seqLen.toFloat()
                }
                mean.toList()
            } else {
                emptyList()
            }

            inputIdsTensor.close()
            attentionMaskTensor.close()
            result.close()
            embeddings
        } catch (e: Exception) {
            logError("AndroidONNX", "Inference error: ${e.message}", e)
            emptyList()
        }
    }
}
