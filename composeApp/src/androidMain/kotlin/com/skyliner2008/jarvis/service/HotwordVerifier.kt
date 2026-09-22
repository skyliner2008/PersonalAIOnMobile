package com.skyliner2008.jarvis.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * ยืนยัน "คำปลุก" หลังจาก [HotwordDetector] (energy VAD) จับเสียงได้
 *
 * เดิมเสียงดังอะไรก็ปลุกจอ (ทีวี เสียงคุยในรถ) — ตัวนี้ถอดเสียงสั้นๆ ด้วย SpeechRecognizer
 * (พยายามใช้โหมด offline ก่อน) แล้วเทียบกับคำปลุกก่อนปลุกหน้าจอจริง (review 2026-09-16)
 *
 * คืนค่า:
 * - `true`  = ได้ยินคำปลุก
 * - `false` = ถอดเสียงได้แต่ไม่มีคำปลุก
 * - `null`  = ตรวจไม่ได้ (device ไม่รองรับ / error) → caller ควรกลับไปใช้พฤติกรรมเดิม
 */
internal class HotwordVerifier(private val context: Context) {

    companion object {
        const val TAG = "HotwordVerifier"
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun heardWakeWord(wakeWord: String, timeoutMs: Long = 5_000L): Boolean? {
        if (!isAvailable()) {
            Log.d(TAG, "SpeechRecognizer unavailable — falling back to energy-only wake")
            return null
        }
        val outcome = recognizeOnce(timeoutMs)
        if (outcome is RecognizeOutcome.Unavailable) return null
        val transcript = (outcome as RecognizeOutcome.Heard).text
        val matched = com.skyliner2008.jarvis.voice.WakeWordMatcher.matches(transcript, wakeWord)
        Log.i(TAG, "Hotword verification: heard='$transcript' matched=$matched")
        return matched
    }

    private sealed interface RecognizeOutcome {
        /** ถอดเสียงสำเร็จ (text ว่าง = ไม่ได้ยินคำพูด) */
        data class Heard(val text: String) : RecognizeOutcome
        /** recognizer ใช้ไม่ได้บนเครื่องนี้ */
        data object Unavailable : RecognizeOutcome
    }

    private suspend fun recognizeOnce(timeoutMs: Long): RecognizeOutcome = withContext(Dispatchers.Main) {
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "createSpeechRecognizer failed", e)
            return@withContext RecognizeOutcome.Unavailable
        }
        try {
            // timeout = ไม่ได้ยินคำปลุก (ไม่ใช่ "ใช้ไม่ได้") — จะได้ไม่ปลุกจอจากเสียงรบกวน
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<RecognizeOutcome> { cont ->
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // ปลุกตอนจอดับต้องไม่พึ่งเน็ต — ถ้าเครื่องไม่มีโมเดล offline ระบบจะ error แล้วเรา fallback เอง
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        }
                    }
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                        override fun onPartialResults(partialResults: Bundle?) {}

                        override fun onError(error: Int) {
                            if (!cont.isActive) return
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume(RecognizeOutcome.Heard(""))
                                else -> {
                                    Log.d(TAG, "Recognition error=$error — treating as unavailable")
                                    cont.resume(RecognizeOutcome.Unavailable)
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            if (!cont.isActive) return
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            cont.resume(RecognizeOutcome.Heard(matches?.joinToString(" ")?.trim().orEmpty()))
                        }
                    })
                    cont.invokeOnCancellation { runCatching { recognizer.cancel() } }
                    try {
                        recognizer.startListening(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "startListening failed", e)
                        if (cont.isActive) cont.resume(RecognizeOutcome.Unavailable)
                    }
                }
            } ?: RecognizeOutcome.Heard("")
        } finally {
            runCatching { recognizer.destroy() }
        }
    }
}
