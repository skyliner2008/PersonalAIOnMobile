package com.skyliner2008.jarvis.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

/**
 * RobotSoundEngine — เครื่องกำเนิดเสียงสังเคราะห์สไตล์หุ่นยนต์ (Procedural PCM Synthesizer)
 *
 * สังเคราะห์คลื่นเสียง (Sine, Triangle, Pitch Sweep, FM Modulation) ผ่าน Android AudioTrack โดยตรง
 * - Zero Asset Dependency: ไม่ต้องโหลดไฟล์ MP3/WAV ภายนอก
 * - Ultra Low Latency: ตอบสนองเร็ว < 10ms
 * - Cached Waveforms: สังเคราะห์ครั้งแรกแล้วแคชไว้ใน RAM ไม่กิน CPU ซ้ำ
 */
object RobotSoundEngine {

    private const val TAG = "RobotSoundEngine"
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    enum class SoundType {
        CHIRP_HAPPY,
        PURR,
        SURPRISE,
        CONFUSED,
        ALARM,
        YAWN_SLEEP,
        TICKLE_GIGGLE,
        WAKE_UP,
        SNORE,
        CHIRP_START,
        CHIRP_END,
        ACKNOWLEDGE,
        SPARKLE,
        MISSILE_LAUNCH,
        EXPLOSION,
        CRUNCH_EAT,
        BUBBLE_POP,
        BELL_TOY,
        SCAN_RADAR,
        WHOOSH,
        POP,
        SLURP,
        COIN,
        ZAP,
        SIZZLE,
        RAIN,
        GAME_BLIP,
        TYPING,
        SOB,
        FANFARE,
        POWER_UP,
        MELODY,
        HEARTBEAT,
        GHOST
    }

    private val waveformCache = ConcurrentHashMap<SoundType, ShortArray>()
    private var isMuted = false

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    /** เล่นเสียงหุ่นยนต์ตามประเภท */
    fun play(type: SoundType) {
        if (isMuted) return
        logDebug(TAG, "🔊 Robot sound effect: $type")
        scope.launch {
            try {
                val samples = waveformCache.getOrPut(type) {
                    generateWaveform(type)
                }
                playPcmStatic(samples)
            } catch (e: Exception) {
                logError(TAG, "Error playing sound $type: ${e.message}")
            }
        }
    }

    fun playHappyChirp() = play(SoundType.CHIRP_HAPPY)
    fun playPurr() = play(SoundType.PURR)
    fun playSurprise() = play(SoundType.SURPRISE)
    fun playConfused() = play(SoundType.CONFUSED)
    fun playAlarm() = play(SoundType.ALARM)
    fun playYawn() = play(SoundType.YAWN_SLEEP)
    fun playGiggle() = play(SoundType.TICKLE_GIGGLE)
    fun playWakeUp() = play(SoundType.WAKE_UP)
    fun playSnore() = play(SoundType.SNORE)
    fun playChirpStart() = play(SoundType.CHIRP_START)
    fun playChirpEnd() = play(SoundType.CHIRP_END)
    fun playAcknowledge() = play(SoundType.ACKNOWLEDGE)
    fun playSparkle() = play(SoundType.SPARKLE)
    fun playMissileLaunch() = play(SoundType.MISSILE_LAUNCH)
    fun playExplosion() = play(SoundType.EXPLOSION)
    fun playCrunchEat() = play(SoundType.CRUNCH_EAT)
    fun playBubblePop() = play(SoundType.BUBBLE_POP)
    fun playBellToy() = play(SoundType.BELL_TOY)
    fun playScanRadar() = play(SoundType.SCAN_RADAR)

    // ─── Procedural Synthesis Generators ───────────────────────────────────────

    private fun generateWaveform(type: SoundType): ShortArray {
        return when (type) {
            SoundType.CHIRP_HAPPY -> generateChirpHappy()
            SoundType.PURR -> generatePurr()
            SoundType.SURPRISE -> generateSurprise()
            SoundType.CONFUSED -> generateConfused()
            SoundType.ALARM -> generateAlarm()
            SoundType.YAWN_SLEEP -> generateYawnSleep()
            SoundType.TICKLE_GIGGLE -> generateGiggle()
            SoundType.WAKE_UP -> generateWakeUp()
            SoundType.SNORE -> generateSnore()
            SoundType.CHIRP_START -> generateChirpStart()
            SoundType.CHIRP_END -> generateChirpEnd()
            SoundType.ACKNOWLEDGE -> generateAcknowledge()
            SoundType.SPARKLE -> generateSparkle()
            SoundType.MISSILE_LAUNCH -> generateMissileLaunch()
            SoundType.EXPLOSION -> generateExplosion()
            SoundType.CRUNCH_EAT -> generateCrunchEat()
            SoundType.BUBBLE_POP -> generateBubblePop()
            SoundType.BELL_TOY -> generateBellToy()
            SoundType.SCAN_RADAR -> generateScanRadar()
            SoundType.WHOOSH -> generateWhoosh()
            SoundType.POP -> generatePop()
            SoundType.SLURP -> generateSlurp()
            SoundType.COIN -> generateCoin()
            SoundType.ZAP -> generateZap()
            SoundType.SIZZLE -> generateSizzle()
            SoundType.RAIN -> generateRain()
            SoundType.GAME_BLIP -> generateGameBlip()
            SoundType.TYPING -> generateTyping()
            SoundType.SOB -> generateSob()
            SoundType.FANFARE -> generateFanfare()
            SoundType.POWER_UP -> generatePowerUp()
            SoundType.MELODY -> generateMelody()
            SoundType.HEARTBEAT -> generateHeartbeat()
            SoundType.GHOST -> generateGhost()
        }
    }

    /** เสียงบี๊บดีใจ ไต่คอร์ด 750Hz -> 1800Hz แล้วจบด้วยปิ๊บสั้น 2200Hz */
    private fun generateChirpHappy(): ShortArray {
        val durationMs = 280
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = 750.0 + (1050.0 * progress * progress) // quadratic upward sweep
            val amp = when {
                progress < 0.1 -> progress / 0.1
                progress > 0.85 -> (1.0 - progress) / 0.15
                else -> 1.0
            }
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * amp * 0.75 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงครางออดอ้อน (Robot Purr) คลื่นต่ำ 125Hz มอดูเลตด้วย 26Hz นุ่มนวล */
    private fun generatePurr(): ShortArray {
        val durationMs = 650
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var carrierPhase = 0.0
        var modPhase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            carrierPhase += 2.0 * PI * 125.0 / SAMPLE_RATE
            modPhase += 2.0 * PI * 26.0 / SAMPLE_RATE

            // Amplitude envelope
            val envelope = sin(PI * progress) // smooth rise and fall
            val modulation = 0.5 + 0.5 * sin(modPhase) // tremolo flutter
            val sampleVal = sin(carrierPhase) * modulation * envelope * 0.65 * Short.MAX_VALUE
            buffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงสะดุ้งตกใจ ไต่ระดับเร็ว 1400Hz -> 2500Hz */
    private fun generateSurprise(): ShortArray {
        val durationMs = 220
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = 1400.0 + 1100.0 * progress
            val amp = (1.0 - progress) // decay
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * amp * 0.8 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงมึนงง สั่นไหวลง 950Hz -> 550Hz สลับไปมา */
    private fun generateConfused(): ShortArray {
        val durationMs = 450
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        var vibratoPhase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            vibratoPhase += 2.0 * PI * 14.0 / SAMPLE_RATE
            val baseFreq = 950.0 - 400.0 * progress
            val currentFreq = baseFreq + sin(vibratoPhase) * 90.0
            val amp = sin(PI * progress)
            phase += 2.0 * PI * currentFreq / SAMPLE_RATE
            val sample = (sin(phase) * amp * 0.7 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงสัญญาณเตือนภัย Desk Sentry: ปิ๊บ-ปิ๊บ-ปิ๊บ สองโทน (900Hz / 1300Hz) */
    private fun generateAlarm(): ShortArray {
        val durationMs = 500
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val isHighTone = (progress * 6.0).toInt() % 2 == 0 // 6 pulses
            val freq = if (isHighTone) 1300.0 else 900.0
            val pulseEnvelope = sin((progress * 6.0 % 1.0) * PI)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * pulseEnvelope * 0.75 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงหาวนอน สโลว์ดาวน์ 550Hz -> 180Hz นุ่มนวล */
    private fun generateYawnSleep(): ShortArray {
        val durationMs = 900
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = 550.0 - (370.0 * progress)
            val envelope = sin(PI * progress) * (1.0 - progress * 0.4)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * envelope * 0.6 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงจั๊กจี้/หัวเราะ: 4 บิ๊บสั้นๆ ถี่ๆ ไต่ระดับ */
    private fun generateGiggle(): ShortArray {
        val durationMs = 320
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        val tones = listOf(1450.0, 1750.0, 1950.0, 2250.0)
        val samplesPerTone = totalSamples / tones.size

        for (toneIdx in tones.indices) {
            val freq = tones[toneIdx]
            var phase = 0.0
            val start = toneIdx * samplesPerTone
            val end = (start + samplesPerTone).coerceAtMost(totalSamples)
            for (i in start until end) {
                val localProgress = (i - start).toDouble() / samplesPerTone
                val envelope = sin(PI * localProgress)
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val sample = (sin(phase) * envelope * 0.7 * Short.MAX_VALUE).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return buffer
    }

    /** เสียงตื่นนอน (Major triad arpeggio C5 -> E5 -> G5 -> C6) */
    private fun generateWakeUp(): ShortArray {
        val durationMs = 450
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        val notes = listOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val samplesPerNote = totalSamples / notes.size

        for (noteIdx in notes.indices) {
            val freq = notes[noteIdx]
            var phase = 0.0
            val start = noteIdx * samplesPerNote
            val end = (start + samplesPerNote).coerceAtMost(totalSamples)
            for (i in start until end) {
                val localProgress = (i - start).toDouble() / samplesPerNote
                val envelope = sin(PI * localProgress)
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val sample = (sin(phase) * envelope * 0.75 * Short.MAX_VALUE).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return buffer
    }

    /** เสียงกรนน่ารักๆ เป็นจังหวะหายใจเข้า-ออกเบาๆ สไตล์หุ่นยนต์ */
    private fun generateSnore(): ShortArray {
        val durationMs = 1200
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        var flutterPhase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            flutterPhase += 2.0 * PI * 18.0 / SAMPLE_RATE

            // Inhale (0..0.45), Pause (0.45..0.55), Exhale (0.55..1.0)
            val (freq, amp) = when {
                progress < 0.45 -> {
                    val p = progress / 0.45
                    val f = 130.0 + 70.0 * p
                    val a = sin(PI * p) * 0.45
                    Pair(f, a)
                }
                progress < 0.55 -> {
                    Pair(160.0, 0.05)
                }
                else -> {
                    val p = (progress - 0.55) / 0.45
                    val f = 190.0 - 95.0 * p
                    val flutter = 0.8 + 0.2 * sin(flutterPhase)
                    val a = sin(PI * p) * 0.4 * flutter
                    Pair(f, a)
                }
            }
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * amp * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงบี๊บขึ้นต้นประโยคเมื่อ AI เริ่มพูด: บี๊บสองโทนสั้นๆ ไต่ระดับ R2D2 สไตล์ (1550Hz -> 2350Hz, 120ms) */
    private fun generateChirpStart(): ShortArray {
        val durationMs = 120
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        val split = (totalSamples * 0.45).toInt()
        val gap = (totalSamples * 0.12).toInt()

        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until totalSamples) {
            when {
                // Tone 1: 1500Hz -> 1800Hz
                i < split -> {
                    val p = i.toDouble() / split
                    val f = 1500.0 + 300.0 * p
                    val env = sin(PI * p) * 0.75
                    phase1 += 2.0 * PI * f / SAMPLE_RATE
                    val s = (sin(phase1) * env * Short.MAX_VALUE).toInt()
                    buffer[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                // Brief pause
                i < split + gap -> {
                    buffer[i] = 0
                }
                // Tone 2: 2100Hz -> 2550Hz
                else -> {
                    val p = (i - split - gap).toDouble() / (totalSamples - split - gap)
                    val f = 2100.0 + 450.0 * p
                    val env = sin(PI * p) * 0.85
                    phase2 += 2.0 * PI * f / SAMPLE_RATE
                    val s = (sin(phase2) * env * Short.MAX_VALUE).toInt()
                    buffer[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }
        return buffer
    }

    /** เสียงปิ๊บปิดท้ายประโยคเมื่อ AI พูดเสร็จ: ปิ๊บนุ่มนวลสั้นๆ ลดระดับลง (2000Hz -> 1400Hz, 85ms) */
    private fun generateChirpEnd(): ShortArray {
        val durationMs = 85
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = 2000.0 - 600.0 * progress
            val env = sin(PI * progress) * (1.0 - progress * 0.3) * 0.65
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val s = (sin(phase) * env * Short.MAX_VALUE).toInt()
            buffer[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียง blip รับทราบ (Acknowledge blip, 1400Hz, 65ms) */
    private fun generateAcknowledge(): ShortArray {
        val durationMs = 65
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val env = sin(PI * progress) * 0.7
            phase += 2.0 * PI * 1400.0 / SAMPLE_RATE
            val s = (sin(phase) * env * Short.MAX_VALUE).toInt()
            buffer[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงประกายวิ้งๆ (Sparkle chime arpeggio C7 -> E7 -> G7 -> C8, 160ms) */
    private fun generateSparkle(): ShortArray {
        val durationMs = 160
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        val notes = listOf(2093.0, 2637.0, 3136.0, 4186.0)
        val perNote = totalSamples / notes.size

        for (n in notes.indices) {
            val f = notes[n]
            var phase = 0.0
            val start = n * perNote
            val end = (start + perNote).coerceAtMost(totalSamples)
            for (i in start until end) {
                val p = (i - start).toDouble() / perNote
                val env = sin(PI * p) * 0.6
                phase += 2.0 * PI * f / SAMPLE_RATE
                val s = (sin(phase) * env * Short.MAX_VALUE).toInt()
                buffer[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return buffer
    }

    /** เสียงขีปนาวุธพุ่ง Whoosh + Pitch Sweep 300Hz -> 2000Hz + White Noise */
    private fun generateMissileLaunch(): ShortArray {
        val durationMs = 450
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)
        var phase = 0.0
        val random = java.util.Random(42)

        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = 300.0 + 1700.0 * (progress * progress)
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.35

            val env = when {
                progress < 0.15 -> progress / 0.15
                progress > 0.80 -> (1.0 - progress) / 0.20
                else -> 1.0
            } * 0.85

            val sample = ((sin(phase) * 0.65 + noise) * env * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงระเบิดตูม Bass thump 85Hz -> 25Hz + Exponential decaying noise burst */
    private fun generateExplosion(): ShortArray {
        val durationMs = 700
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)
        var phase = 0.0
        val random = java.util.Random(1337)

        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples
            val freq = (85.0 - 60.0 * progress).coerceAtLeast(25.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val noise = (random.nextDouble() * 2.0 - 1.0)

            val env = kotlin.math.exp(-3.8 * progress) * 0.95

            val bassWeight = if (progress < 0.25) 0.75 else 0.4
            val sample = ((sin(phase) * bassWeight + noise * (1.0 - bassWeight)) * env * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /** เสียงเคี้ยวอาหารกรุบกรอบ (3 crunchy chomps/bites) 🍖 */
    private fun generateCrunchEat(): ShortArray {
        val durationMs = 280
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)
        val biteStarts = listOf(0, (0.080 * SAMPLE_RATE).toInt(), (0.165 * SAMPLE_RATE).toInt())
        val biteDurationSamples = (0.065 * SAMPLE_RATE).toInt()
        val random = java.util.Random(42)

        for (bIndex in biteStarts.indices) {
            val start = biteStarts[bIndex]
            var phase = 0.0
            for (i in 0 until biteDurationSamples) {
                val sampleIdx = start + i
                if (sampleIdx >= totalSamples) break
                val t = i.toDouble() / biteDurationSamples
                // Frequency drops rapidly from ~340Hz to 110Hz per chomp
                val freq = 340.0 - 230.0 * t
                phase += 2.0 * PI * freq / SAMPLE_RATE

                // Resonant crunchy texture
                val noise = random.nextDouble() * 2.0 - 1.0
                val crunchMod = sin(2.0 * PI * 950.0 * (i.toDouble() / SAMPLE_RATE))
                val texture = noise * 0.65 + noise * crunchMod * 0.35

                // Sharp attack (3ms), then steep exponential decay
                val attack = if (t < 0.05) (t / 0.05) else 1.0
                val decay = kotlin.math.exp(-4.5 * t)
                val env = attack * decay * 0.90

                val sample = ((sin(phase) * 0.40 + texture * 0.60) * env * Short.MAX_VALUE).toInt()
                buffer[sampleIdx] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return buffer
    }

    /** เสียงฟองสบู่แตกเปาะแปะ (5 playful ascending water droplet bubble chirps) 🧼 */
    private fun generateBubblePop(): ShortArray {
        val durationMs = 320
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        data class Bubble(val startMs: Double, val durMs: Double, val f0: Double, val f1: Double)
        val bubbles = listOf(
            Bubble(0.0, 45.0, 650.0, 1450.0),
            Bubble(60.0, 45.0, 850.0, 1850.0),
            Bubble(125.0, 40.0, 720.0, 1600.0),
            Bubble(185.0, 45.0, 1000.0, 2150.0),
            Bubble(245.0, 50.0, 1150.0, 2400.0)
        )

        for (bubble in bubbles) {
            val startSample = ((bubble.startMs / 1000.0) * SAMPLE_RATE).toInt()
            val durSamples = ((bubble.durMs / 1000.0) * SAMPLE_RATE).toInt()
            var phase = 0.0

            for (i in 0 until durSamples) {
                val sampleIdx = startSample + i
                if (sampleIdx >= totalSamples) break
                val t = i.toDouble() / durSamples
                // Rapid upward frequency sweep (characteristic acoustic water droplet)
                val freq = bubble.f0 + (bubble.f1 - bubble.f0) * kotlin.math.sqrt(t)
                phase += 2.0 * PI * freq / SAMPLE_RATE

                // Natural bubble decay envelope
                val env = kotlin.math.exp(-4.2 * t) * sin(PI * t.coerceIn(0.0, 1.0)) * 0.88
                val sample = ((sin(phase) + 0.15 * sin(phase * 2.0)) * env * Short.MAX_VALUE).toInt()

                // Add to buffer (allow subtle overlap of bubbles)
                val existing = buffer[sampleIdx].toInt()
                val blended = (existing + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[sampleIdx] = blended.toShort()
            }
        }
        return buffer
    }

    /** เสียงกระดิ่ง/ลูกบอลของเล่น (Two-tone bright metallic chime G6 1568Hz + C7 2093Hz) 🎾 */
    private fun generateBellToy(): ShortArray {
        val durationMs = 420
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(totalSamples)

        data class Chime(val startMs: Double, val freq: Double, val weight: Double)
        val chimes = listOf(
            Chime(0.0, 1568.0, 0.85),   // G6
            Chime(85.0, 2093.0, 0.95)   // C7
        )

        for (chime in chimes) {
            val startSample = ((chime.startMs / 1000.0) * SAMPLE_RATE).toInt()
            val remainingSamples = totalSamples - startSample
            var phase0 = 0.0
            var phase1 = 0.0 // Inharmonic metallic overtone (2.76x)
            var phase2 = 0.0 // Octave overtone (2.0x)

            for (i in 0 until remainingSamples) {
                val sampleIdx = startSample + i
                val t = i.toDouble() / SAMPLE_RATE

                phase0 += 2.0 * PI * chime.freq / SAMPLE_RATE
                phase1 += 2.0 * PI * (chime.freq * 2.76) / SAMPLE_RATE
                phase2 += 2.0 * PI * (chime.freq * 2.0) / SAMPLE_RATE

                // Sweet bell ring decay with tremolo
                val tremolo = 1.0 + 0.08 * sin(2.0 * PI * 13.0 * t)
                val env = kotlin.math.exp(-7.2 * t) * tremolo * chime.weight * 0.78

                val tone = (sin(phase0) * 0.65 + sin(phase1) * 0.22 + sin(phase2) * 0.13)
                val sample = (tone * env * Short.MAX_VALUE).toInt()

                val existing = buffer[sampleIdx].toInt()
                val blended = (existing + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[sampleIdx] = blended.toShort()
            }
        }
        return buffer
    }

    /**
     * สังเคราะห์เสียงเปิดตาแสกนเรดาร์ไซไฟ (Cyber Radar Scan Sweep):
     * เสียงกวาดเรดาร์ความถี่สูง (1400Hz -> 2800Hz) พร้อม FM Modulation ความเร็ว 35Hz
     * และมี Sub-pulse สะท้อนสไตล์ Holographic Viewfinder (~420ms)
     */
    private fun generateScanRadar(): ShortArray {
        val totalMs = 420
        val totalSamples = (SAMPLE_RATE * totalMs / 1000)
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            // Dual ping: Main sweep (0..240ms), Echo response (240..420ms)
            val (baseFreq, amp) = if (t < 0.24) {
                val p = t / 0.24
                val f = 1400.0 + (1200.0 * p * p) // 1400Hz -> 2600Hz ascending sweep
                val env = sin(p * PI).pow(0.8) // smooth bell envelope
                f to env * 0.70
            } else {
                val p = (t - 0.24) / 0.18
                val f = 2400.0 + (400.0 * (1.0 - p)) // 2800Hz -> 2400Hz settling echo
                val env = kotlin.math.exp(-p * 4.0) * sin(p * PI)
                f to env * 0.45
            }

            // High-tech FM modulation
            val fm = sin(2.0 * PI * 35.0 * t) * 60.0
            val instantaneousFreq = baseFreq + fm

            val wave = sin(2.0 * PI * instantaneousFreq * t) + 
                       0.25 * sin(4.0 * PI * instantaneousFreq * t) // cyber harmonic

            val sample = (wave * amp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    // ─── Scene cue synthesis ───────────────────────────────────────────────────

    /** Render [durationMs] of audio from a per-sample function of (time s, progress 0..1). */
    private inline fun render(durationMs: Int, fn: (t: Double, p: Double) -> Double): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = fn(i.toDouble() / SAMPLE_RATE, i.toDouble() / n)
            out[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private val noiseRng = java.util.Random(7L)
    private fun noise(): Double = noiseRng.nextDouble() * 2.0 - 1.0

    /** Rising-falling filtered noise sweep: something flying past. */
    private fun generateWhoosh(): ShortArray {
        var lp = 0.0
        return render(420) { _, p ->
            val cutoff = 0.04 + 0.30 * sin(PI * p)
            lp += (noise() - lp) * cutoff
            lp * sin(PI * p).pow(1.5) * 1.6
        }
    }

    /** Short bubbly pop. */
    private fun generatePop(): ShortArray {
        var ph = 0.0
        return render(130) { _, p ->
            ph += 2.0 * PI * (900.0 - 600.0 * p) / SAMPLE_RATE
            sin(ph) * kotlin.math.exp(-p * 7.0) * 0.8
        }
    }

    /** Two slurpy wobbles. */
    private fun generateSlurp(): ShortArray {
        var ph = 0.0
        var lp = 0.0
        return render(520) { t, p ->
            ph += 2.0 * PI * (320.0 + 180.0 * sin(2.0 * PI * 9.0 * t)) / SAMPLE_RATE
            lp += (noise() - lp) * 0.12
            (sin(ph) * 0.45 + lp * 0.5) * sin(PI * p) * (0.6 + 0.4 * sin(2.0 * PI * 4.0 * t))
        }
    }

    /** Classic coin: two bright square-ish tones. */
    private fun generateCoin(): ShortArray = render(360) { t, _ ->
        val f = if (t < 0.08) 988.0 else 1319.0
        val env = if (t < 0.08) 0.6 else kotlin.math.exp(-(t - 0.08) * 9.0) * 0.6
        kotlin.math.sign(sin(2.0 * PI * f * t)) * env * 0.5 + sin(2.0 * PI * f * 2 * t) * env * 0.2
    }

    /** Electric crackle. */
    private fun generateZap(): ShortArray = render(380) { t, p ->
        val buzz = kotlin.math.sign(sin(2.0 * PI * (120.0 + 60.0 * sin(2.0 * PI * 30.0 * t)) * t))
        val crackle = if (noiseRng.nextDouble() > 0.93) noise() else 0.0
        (buzz * 0.35 + crackle * 0.9) * (1.0 - p)
    }

    /** Fire sizzle: bright hissing noise with pops. */
    private fun generateSizzle(): ShortArray {
        var hp = 0.0
        var last = 0.0
        return render(700) { _, p ->
            val n = noise()
            hp = 0.85 * (hp + n - last)
            last = n
            val pop = if (noiseRng.nextDouble() > 0.995) 0.8 else 0.0
            (hp * 0.35 + pop) * sin(PI * p)
        }
    }

    /** Rain patter: soft noise plus random droplets. */
    private fun generateRain(): ShortArray {
        var lp = 0.0
        return render(900) { _, p ->
            lp += (noise() - lp) * 0.25
            val drop = if (noiseRng.nextDouble() > 0.992) noise() * 0.7 else 0.0
            (lp * 0.35 + drop) * sin(PI * p).pow(0.4)
        }
    }

    /** 8-bit jump blip. */
    private fun generateGameBlip(): ShortArray = render(160) { t, p ->
        kotlin.math.sign(sin(2.0 * PI * (440.0 + 880.0 * p) * t)) * (1.0 - p) * 0.35
    }

    /** Three quick key clicks. */
    private fun generateTyping(): ShortArray = render(330) { t, _ ->
        val local = (t % 0.11)
        if (local < 0.012) noise() * kotlin.math.exp(-local * 400.0) * 0.8 else 0.0
    }

    /** Sob: three falling hiccup tones. */
    private fun generateSob(): ShortArray {
        var ph = 0.0
        return render(720) { t, _ ->
            val k = (t / 0.24).toInt()
            val local = t - k * 0.24
            ph += 2.0 * PI * (560.0 - k * 60.0 - local * 700.0) / SAMPLE_RATE
            sin(ph) * sin(PI * (local / 0.24)).pow(2.0) * 0.55
        }
    }

    /** Fanfare: C-E-G-C arpeggio. */
    private fun generateFanfare(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        return render(760) { t, _ ->
            val k = minOf((t / 0.14).toInt(), 3)
            val local = t - k * 0.14
            val env = if (k == 3) kotlin.math.exp(-local * 3.0) else kotlin.math.exp(-local * 10.0)
            (kotlin.math.sign(sin(2.0 * PI * notes[k] * t)) * 0.25 + sin(2.0 * PI * notes[k] * t) * 0.3) * env
        }
    }

    /** Power up: rising hum with shimmer. */
    private fun generatePowerUp(): ShortArray {
        var ph = 0.0
        return render(650) { t, p ->
            ph += 2.0 * PI * (180.0 + 900.0 * p * p) / SAMPLE_RATE
            (sin(ph) * 0.5 + sin(ph * 2.01) * 0.2) * sin(PI * p).pow(0.6) * (0.8 + 0.2 * sin(2.0 * PI * 25.0 * t))
        }
    }

    /** Short happy melody (G-A-B-D). */
    private fun generateMelody(): ShortArray {
        val notes = doubleArrayOf(783.99, 880.0, 987.77, 1174.66, 987.77)
        return render(900) { t, _ ->
            val k = minOf((t / 0.18).toInt(), notes.size - 1)
            val local = t - k * 0.18
            val env = kotlin.math.exp(-local * 6.0)
            (sin(2.0 * PI * notes[k] * t) * 0.55 + sin(2.0 * PI * notes[k] * 2 * t) * 0.12) * env
        }
    }

    /** Heartbeat: lub-dub. */
    private fun generateHeartbeat(): ShortArray = render(620) { t, _ ->
        fun thump(at: Double, f: Double): Double {
            val d = t - at
            return if (d < 0) 0.0 else sin(2.0 * PI * f * d) * kotlin.math.exp(-d * 28.0)
        }
        (thump(0.0, 70.0) + thump(0.18, 58.0) * 0.8) * 0.9
    }

    /** Ghostly wail: slow vibrato glide. */
    private fun generateGhost(): ShortArray {
        var ph = 0.0
        return render(1000) { t, p ->
            ph += 2.0 * PI * (420.0 + 160.0 * sin(PI * p) + 18.0 * sin(2.0 * PI * 6.0 * t)) / SAMPLE_RATE
            (sin(ph) * 0.45 + sin(ph * 1.5) * 0.12) * sin(PI * p)
        }
    }

    // ─── Playback using MODE_STATIC AudioTrack ─────────────────────────────────

    private fun playPcmStatic(samples: ShortArray) {
        var track: AudioTrack? = null
        try {
            val sizeInBytes = samples.size * 2
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_GAME plays on the MEDIA volume: the user's media slider and the
                        // volume keys control the pet's sound effects (ASSISTANCE_SONIFICATION
                        // followed the system/notification volume and ignored the media slider)
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
            track.play()

            // Schedule release after playback duration
            val durationMs = (samples.size * 1000L / SAMPLE_RATE) + 100L
            val trackToRelease = track
            scope.launch {
                delay(durationMs)
                try {
                    trackToRelease.stop()
                    trackToRelease.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            logError(TAG, "AudioTrack playback error: ${e.message}")
            try {
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
