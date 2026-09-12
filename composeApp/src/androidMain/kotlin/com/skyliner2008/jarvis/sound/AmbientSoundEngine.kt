package com.skyliner2008.jarvis.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * AmbientSoundEngine — เครื่องกำเนิดเสียงบรรยากาศพื้นหลังสังเคราะห์ (Procedural Ambient Sound Generator)
 *
 * ทำงานบน Android โดยสังเคราะห์คลื่นเสียง PCM ในหน่วยความจำ แล้วเล่นแบบไร้รอยต่อ (Seamless Loop)
 * ผ่าน `AudioTrack.MODE_STATIC` + `setLoopPoints(0, size, -1)` ซึ่งรันในระดับ AudioFlinger Hardware:
 * - Zero Asset: ไม่ต้องโหลดไฟล์ MP3/WAV ภายนอกแม้แต่ไฟล์เดียว
 * - Zero CPU: การเล่นลูปฮาร์ดแวร์ไม่กิน CPU ระหว่างเล่น
 * - Audio Ducking: หรี่เสียงลงอัตโนมัติเมื่อ AI หรือผู้ใช้กำลังพูด เพื่อไม่ให้รบกวนเสียงสนทนา
 */
object AmbientSoundEngine {

    private const val TAG = "AmbientSoundEngine"
    private const val SAMPLE_RATE = 22050
    private const val LOOP_DURATION_SEC = 2.5 // ความยาวลูป 2.5 วินาที ไร้รอยต่อ

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val loopCache = ConcurrentHashMap<BackgroundTheme, ShortArray>()

    private var currentTrack: AudioTrack? = null
    private var currentTheme: BackgroundTheme? = null
    private var isDucked: Boolean = false
    private var isMuted: Boolean = false

    private const val NORMAL_VOLUME = 0.18f
    private const val DUCKED_VOLUME = 0.04f

    /** ติดตั้ง Bridge เข้ากับ AmbientSoundPlayer ใน commonMain */
    fun installBridge() {
        AmbientSoundPlayer.onThemeChanged = { theme ->
            setTheme(theme)
        }
        AmbientSoundPlayer.onDuckingChanged = { ducked ->
            setDucking(ducked)
        }
        AmbientSoundPlayer.onStop = {
            stop()
        }
    }

    /** สลับธีมเสียงบรรยากาศ */
    fun setTheme(theme: BackgroundTheme) {
        if (isMuted) return
        if (currentTheme == theme && currentTrack != null) return

        logDebug(TAG, "🌿 Ambient theme switch: $theme")
        currentTheme = theme

        scope.launch {
            try {
                // 1. หยุด Track เดิม
                stopInternal()

                if (theme == BackgroundTheme.DEFAULT) {
                    // ธีมเริ่มต้นเป็นเสียงเงียบสงบ/เรียบง่าย
                    return@launch
                }

                // 2. ดึงหรือสังเคราะห์เสียงลูป
                val samples = loopCache.getOrPut(theme) {
                    generateAmbientLoop(theme)
                }

                // 3. เริ่มเล่นแบบวนซ้ำไม่รู้จบ
                playLoop(samples)
            } catch (e: Exception) {
                logError(TAG, "Error playing ambient theme $theme: ${e.message}")
            }
        }
    }

    /** ปรับระดับเสียง Ducking (หรี่เสียงเมื่อมีคนพูด) */
    fun setDucking(ducked: Boolean) {
        isDucked = ducked
        val vol = if (ducked) DUCKED_VOLUME else NORMAL_VOLUME
        try {
            currentTrack?.setVolume(vol)
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) stop() else currentTheme?.let { setTheme(it) }
    }

    fun stop() {
        currentTheme = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            currentTrack?.stop()
            currentTrack?.release()
        } catch (_: Exception) {}
        currentTrack = null
    }

    // ─── Procedural Synthesis of Ambient Loops ────────────────────────────────

    private fun generateAmbientLoop(theme: BackgroundTheme): ShortArray {
        val totalSamples = (SAMPLE_RATE * LOOP_DURATION_SEC).toInt()
        val buffer = ShortArray(totalSamples)

        when (theme) {
            BackgroundTheme.RAINY -> generateRainyLoop(buffer, totalSamples)
            BackgroundTheme.NIGHT -> generateNightLoop(buffer, totalSamples)
            BackgroundTheme.SUNNY -> generateSunnyLoop(buffer, totalSamples)
            BackgroundTheme.SAKURA -> generateSakuraLoop(buffer, totalSamples)
            BackgroundTheme.MATRIX -> generateMatrixLoop(buffer, totalSamples)
            BackgroundTheme.LOVE_BG -> generateLoveLoop(buffer, totalSamples)
            BackgroundTheme.THUNDER -> generateThunderLoop(buffer, totalSamples)
            BackgroundTheme.DEFAULT -> { /* Silent */ }
        }

        // Apply seamless circular crossfade at endpoints (50ms) to ensure zero pop/click on loop boundary
        applyBoundaryCrossfade(buffer, totalSamples)
        return buffer
    }

    /** เสียงฝนตกเปาะแปะ: Soft pink noise + random droplet impulses */
    private fun generateRainyLoop(buffer: ShortArray, totalSamples: Int) {
        val random = Random(42)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0

        for (i in 0 until totalSamples) {
            val white = random.nextDouble(-1.0, 1.0)
            // Simplified Pink Noise filter
            b0 = 0.99765 * b0 + white * 0.0990460
            b1 = 0.96300 * b1 + white * 0.2965164
            b2 = 0.57000 * b2 + white * 1.0526913
            val pink = (b0 + b1 + b2 + white * 0.1848) * 0.12

            // Occasional raindrop ping
            val isDrop = (i % 680 == 0 && random.nextDouble() > 0.45)
            val dropSample = if (isDrop) {
                sin(i * 0.25) * 0.35
            } else 0.0

            val sample = (pink + dropSample) * Short.MAX_VALUE * 0.7
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียงจิ้งหรีดราตรี: 4400Hz pulsed chirps with calm night air */
    private fun generateNightLoop(buffer: ShortArray, totalSamples: Int) {
        val chirpRateHz = 3.0 // 3 กลุ่มเสียงจิ้งหรีดต่อวินาที
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val pulse = sin(2.0 * PI * chirpRateHz * t)
            val chirpEnv = if (pulse > 0.3) sin(pulse * PI) else 0.0

            phase += 2.0 * PI * 4450.0 / SAMPLE_RATE
            // 4450Hz carrier with 65Hz vibrato flutter
            val flutter = sin(2.0 * PI * 65.0 * t)
            val carrier = sin(phase + flutter * 0.4)

            // Low background night air
            val air = sin(2.0 * PI * 95.0 * t) * 0.06

            val sample = (carrier * chirpEnv * 0.35 + air) * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียงลมอุ่นๆ + นกดิจิทัลเบาๆ ในวันแดดออก */
    private fun generateSunnyLoop(buffer: ShortArray, totalSamples: Int) {
        val random = Random(101)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Warm breeze modulation
            val breeze = sin(2.0 * PI * 0.4 * t) * sin(2.0 * PI * 1.2 * t) * 0.15

            // Occasional gentle bird chirp whistle around 2800Hz
            val birdWindow = (t in 0.5..0.75) || (t in 1.6..1.82)
            val bird = if (birdWindow) {
                val bt = (t % 0.8) / 0.25
                val f = 2600.0 + sin(bt * PI) * 500.0
                sin(2.0 * PI * f * t) * sin(bt * PI) * 0.25
            } else 0.0

            val sample = (breeze + bird) * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียงสายลมพัดใบไม้ไหวแผ่วเบา สไตล์ซากุระ */
    private fun generateSakuraLoop(buffer: ShortArray, totalSamples: Int) {
        val random = Random(77)
        var filteredNoise = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = random.nextDouble(-1.0, 1.0)
            // Low pass filter for soft whispering wind
            filteredNoise = 0.94 * filteredNoise + 0.06 * noise
            val windSway = 0.6 + 0.4 * sin(2.0 * PI * 0.5 * t)
            val sample = filteredNoise * windSway * 0.45 * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียง electronic data drone สไตล์ Matrix: 55Hz fundamental + 110Hz / 220Hz harmonics */
    private fun generateMatrixLoop(buffer: ShortArray, totalSamples: Int) {
        var p1 = 0.0
        var p2 = 0.0
        var p3 = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            p1 += 2.0 * PI * 55.0 / SAMPLE_RATE
            p2 += 2.0 * PI * 110.0 / SAMPLE_RATE
            p3 += 2.0 * PI * 220.0 / SAMPLE_RATE

            val tremolo = 0.85 + 0.15 * sin(2.0 * PI * 8.0 * t)
            val s1 = sin(p1) * 0.45
            val s2 = sin(p2) * 0.25
            val s3 = sin(p3) * 0.12

            val sample = (s1 + s2 + s3) * tremolo * 0.5 * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียง warm shimmer pad คอร์ดอบอุ่นสำหรับธีม LOVE */
    private fun generateLoveLoop(buffer: ShortArray, totalSamples: Int) {
        // C4 (261.63Hz), E4 (329.63Hz), G4 (392.00Hz) warm chord pad
        var pC = 0.0
        var pE = 0.0
        var pG = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            pC += 2.0 * PI * 261.63 / SAMPLE_RATE
            pE += 2.0 * PI * 329.63 / SAMPLE_RATE
            pG += 2.0 * PI * 392.00 / SAMPLE_RATE

            val pulse = 0.75 + 0.25 * sin(2.0 * PI * 0.6 * t) // slow heartbeat-like breathe
            val s = (sin(pC) * 0.35 + sin(pE) * 0.28 + sin(pG) * 0.22) * pulse

            val sample = s * 0.4 * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** เสียงพายุฝนโปรยปราย + ฟ้าร้องครืนๆ ความถี่ต่ำ */
    private fun generateThunderLoop(buffer: ShortArray, totalSamples: Int) {
        val random = Random(999)
        var filteredNoise = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = random.nextDouble(-1.0, 1.0)
            filteredNoise = 0.92 * filteredNoise + 0.08 * noise // rain wash

            // Distant low thunder rumble at 1.0s..1.8s
            val thunder = if (t in 0.8..1.9) {
                val tt = (t - 0.8) / 1.1
                val freq = 45.0 + sin(tt * 20.0) * 15.0
                sin(2.0 * PI * freq * t) * sin(tt * PI) * 0.45
            } else 0.0

            val sample = (filteredNoise * 0.3 + thunder) * 0.6 * Short.MAX_VALUE
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** ปรับขอบรอยต่อหัว-ท้าย (Crossfade 50ms) เพื่อป้องกันเสียงแก๊ก (Zero-crossing/Pop) ขณะวนลูป */
    private fun applyBoundaryCrossfade(buffer: ShortArray, totalSamples: Int) {
        val fadeSamples = (SAMPLE_RATE * 0.05).toInt()
        for (i in 0 until fadeSamples) {
            val factor = i.toDouble() / fadeSamples
            val startVal = buffer[i].toDouble()
            val endVal = buffer[totalSamples - fadeSamples + i].toDouble()
            // Blend
            buffer[i] = (startVal * factor + endVal * (1.0 - factor)).toInt().toShort()
        }
    }

    // ─── AudioTrack Hardware Looping ──────────────────────────────────────────

    private fun playLoop(samples: ShortArray) {
        try {
            val sizeInBytes = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(sizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.setLoopPoints(0, samples.size, -1) // -1 = Loop infinitely
            val vol = if (isDucked) DUCKED_VOLUME else NORMAL_VOLUME
            track.setVolume(vol)
            track.play()

            currentTrack = track
            logDebug(TAG, "🎶 Ambient loop playing (samples=${samples.size}, vol=$vol)")
        } catch (e: Exception) {
            logError(TAG, "Failed to start AudioTrack loop: ${e.message}")
        }
    }
}
