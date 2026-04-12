package com.example.personalaibot.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

actual class VoiceManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var _isListening = false
    actual val isListening: Boolean get() = _isListening

    init {
        // เริ่มต้น Text-to-Speech
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                // ลองตั้งภาษาไทยก่อน ถ้าไม่มีให้ fallback เป็น EN
                val thLocale = Locale("th", "TH")
                val result = tts?.setLanguage(thLocale)
                tts?.setPitch(0.95f) // เสียงทุ้มขึ้นเล็กน้อยให้ดูสุขุม
                tts?.setSpeechRate(1.1f) // พูดเร็วขึ้นนิดนึงให้เป็นธรรมชาติ
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.language = Locale.ENGLISH
                }
            }
        }

        // เตรียม SpeechRecognizer ถ้า device รองรับ
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    actual fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val recognizer = speechRecognizer ?: run {
            onError("Speech recognition ไม่พร้อมใช้งานบน device นี้")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening = true
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening = false
            }

            override fun onError(error: Int) {
                _isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH       -> "ไม่พบเสียงที่ตรงกัน — ลองพูดใหม่"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "หมดเวลารอเสียง"
                    SpeechRecognizer.ERROR_NETWORK        -> "เครือข่ายขัดข้อง"
                    SpeechRecognizer.ERROR_AUDIO          -> "Audio error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ไม่มี permission ใช้ mic"
                    else -> "Recognition error: $error"
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                _isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) onResult(text) else onError("ไม่ได้ยินเสียง")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // partial results สามารถ show ใน UI ได้ในอนาคต
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    actual fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening = false
    }

    actual fun speak(text: String, onDone: (() -> Unit)?) {
        if (!isTtsReady) {
            onDone?.invoke()
            return
        }

        val utteranceId = "jarvis_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) onDone?.invoke()
            }
            @Deprecated("Deprecated in API 21")
            override fun onError(id: String?) {
                onDone?.invoke()
            }
        })

        // ทำความสะอาดข้อความก่อนอ่าน เพื่อให้เป็นธรรมชาติที่สุด
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "(ขออนุญาตข้ามการอ่านโค้ดโปรแกรมนะครับ)") // ข้าม Code block
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // เอาเครื่องหมาย bold ออก
            .replace(Regex("\\*(.*?)\\*"), "$1") // เอา italic ออก
            .replace("#", "") // เอา hashtag/heading ออก
            .replace("_", " ")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1") // อ่านเฉพาะข้อความของ Link ข้าม URL
            
        // ตัดข้อความยาวเกินไป (TTS มักสะดุดกับข้อความยาวมากๆ)
        val trimmed = cleanText.take(500).let {
            if (cleanText.length > 500) "$it..." else it
        }

        tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    actual fun isAvailable(): Boolean =
        speechRecognizer != null

    actual fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        tts = null
        speechRecognizer = null
        isTtsReady = false
    }
}
