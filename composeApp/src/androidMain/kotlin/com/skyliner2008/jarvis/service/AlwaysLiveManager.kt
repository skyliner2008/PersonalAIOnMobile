package com.skyliner2008.jarvis.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.skyliner2008.jarvis.pet.AlwaysLiveProfile
import com.skyliner2008.jarvis.pet.PetMotionBridge
import com.skyliner2008.jarvis.pet.PetMotionDetector
import com.skyliner2008.jarvis.sound.RobotSound
import com.skyliner2008.jarvis.sound.RobotSoundEngine
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AlwaysLiveManager — State Machine สำหรับ Always AI Live Mode
 *
 * จัดการ state transitions ระหว่าง:
 * - OFF → ไม่ได้เปิด Always Live
 * - FULL_SCREEN → แสดง full-screen avatar UI
 * - MINI_FLOATING → แสดง mini robot overlay ทับแอปอื่น
 * - BACKGROUND_LISTEN → จอดับ — ฟัง hotword เท่านั้น
 *
 * ทำหน้าที่:
 * - Manage WakeLock (PARTIAL_WAKE_LOCK for background mic listening)
 * - Coordinate avatar emotion state across UI modes
 * - Handle screen on/off events
 * - Route AI state → avatar emotions
 */
class AlwaysLiveManager(private val context: Context) {

    companion object {
        const val TAG = "AlwaysLiveManager"

        // Singleton for cross-component access
        @Volatile
        private var _instance: AlwaysLiveManager? = null

        fun getInstance(context: Context): AlwaysLiveManager {
            return _instance ?: synchronized(this) {
                _instance ?: AlwaysLiveManager(context.applicationContext).also {
                    _instance = it
                }
            }
        }

        fun getInstanceOrNull(): AlwaysLiveManager? = _instance
    }

    // ─── State Machine ───────────────────────────────────────────────────
    enum class AlwaysLiveState {
        OFF,              // ไม่ได้เปิด Always Live
        FULL_SCREEN,      // แสดง Full-screen mode
        MINI_FLOATING,    // แสดง Mini robot overlay
        BACKGROUND_LISTEN // จอดับ — ฟัง hotword เท่านั้น
    }

    private val _state = MutableStateFlow(AlwaysLiveState.OFF)
    val state: StateFlow<AlwaysLiveState> = _state.asStateFlow()

    // ─── Avatar State (shared across all UI modes) ───────────────────────
    private val _avatarState = MutableStateFlow(AvatarState())
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    // ─── Always Live Profile (Control, Drive, Pet) ────────────────────────
    private val _currentProfile = MutableStateFlow(AlwaysLiveProfile.CONTROL)
    val currentProfile: StateFlow<AlwaysLiveProfile> = _currentProfile.asStateFlow()

    private var petMotionDetector: PetMotionDetector? = null
    private var voiceManager: com.skyliner2008.jarvis.voice.VoiceManager? = null

    private fun getOrCreateVoiceManager(): com.skyliner2008.jarvis.voice.VoiceManager {
        return voiceManager ?: com.skyliner2008.jarvis.voice.VoiceManager(context).also { voiceManager = it }
    }

    private fun installSoundHandler() {
        RobotSoundPlayer.handler = { sound ->
            when (sound) {
                RobotSound.HAPPY -> RobotSoundEngine.playHappyChirp()
                RobotSound.PURR -> RobotSoundEngine.playPurr()
                RobotSound.SURPRISE -> RobotSoundEngine.playSurprise()
                RobotSound.CONFUSED -> RobotSoundEngine.playConfused()
                RobotSound.ALARM -> RobotSoundEngine.playAlarm()
                RobotSound.YAWN -> RobotSoundEngine.playYawn()
                RobotSound.GIGGLE -> RobotSoundEngine.playGiggle()
                RobotSound.WAKE_UP -> RobotSoundEngine.playWakeUp()
                RobotSound.SNORE -> RobotSoundEngine.playSnore()
                RobotSound.CHIRP_START -> RobotSoundEngine.playChirpStart()
                RobotSound.CHIRP_END -> RobotSoundEngine.playChirpEnd()
                RobotSound.ACKNOWLEDGE -> RobotSoundEngine.playAcknowledge()
                RobotSound.SPARKLE -> RobotSoundEngine.playSparkle()
                RobotSound.MISSILE_LAUNCH -> RobotSoundEngine.playMissileLaunch()
                RobotSound.EXPLOSION -> RobotSoundEngine.playExplosion()
                RobotSound.CRUNCH_EAT -> RobotSoundEngine.playCrunchEat()
                RobotSound.BUBBLE_POP -> RobotSoundEngine.playBubblePop()
                RobotSound.BELL_TOY -> RobotSoundEngine.playBellToy()
                RobotSound.SCAN_RADAR -> RobotSoundEngine.playScanRadar()
                RobotSound.WHOOSH -> RobotSoundEngine.play(RobotSoundEngine.SoundType.WHOOSH)
                RobotSound.POP -> RobotSoundEngine.play(RobotSoundEngine.SoundType.POP)
                RobotSound.SLURP -> RobotSoundEngine.play(RobotSoundEngine.SoundType.SLURP)
                RobotSound.COIN -> RobotSoundEngine.play(RobotSoundEngine.SoundType.COIN)
                RobotSound.ZAP -> RobotSoundEngine.play(RobotSoundEngine.SoundType.ZAP)
                RobotSound.SIZZLE -> RobotSoundEngine.play(RobotSoundEngine.SoundType.SIZZLE)
                RobotSound.RAIN -> RobotSoundEngine.play(RobotSoundEngine.SoundType.RAIN)
                RobotSound.GAME_BLIP -> RobotSoundEngine.play(RobotSoundEngine.SoundType.GAME_BLIP)
                RobotSound.TYPING -> RobotSoundEngine.play(RobotSoundEngine.SoundType.TYPING)
                RobotSound.SOB -> RobotSoundEngine.play(RobotSoundEngine.SoundType.SOB)
                RobotSound.FANFARE -> RobotSoundEngine.play(RobotSoundEngine.SoundType.FANFARE)
                RobotSound.POWER_UP -> RobotSoundEngine.play(RobotSoundEngine.SoundType.POWER_UP)
                RobotSound.MELODY -> RobotSoundEngine.play(RobotSoundEngine.SoundType.MELODY)
                RobotSound.HEARTBEAT -> RobotSoundEngine.play(RobotSoundEngine.SoundType.HEARTBEAT)
                RobotSound.GHOST -> RobotSoundEngine.play(RobotSoundEngine.SoundType.GHOST)
            }
        }
    }

    init {
        installSoundHandler()
        com.skyliner2008.jarvis.pet.PetVisionDetector.installBridge()
        com.skyliner2008.jarvis.sound.AmbientSoundEngine.installBridge()
        installDriveBridge()
    }

    private var faceDownSoundJob: Job? = null
    private var faceUpSoundJob: Job? = null
    private var shakeSoundJob: Job? = null

    fun setProfile(profile: AlwaysLiveProfile) {
        val prev = _currentProfile.value
        _currentProfile.value = profile
        Log.i(TAG, "Profile changed: $prev -> $profile")
        com.skyliner2008.jarvis.pet.LiveModeState.update(profile)
        if (profile == AlwaysLiveProfile.PET) {
            stopDriveMode()
            if (_state.value == AlwaysLiveState.FULL_SCREEN) {
                startPetMode()
            }
        } else {
            if (prev == AlwaysLiveProfile.PET) {
                stopPetMode()
            }
            if (_state.value == AlwaysLiveState.FULL_SCREEN) {
                startDriveMode()
            }
        }
    }

    private fun startPetMode() {
        Log.i(TAG, "🐾 Starting Pet Mode (Virtual Desk Pet)")
        // persona flag ต้องตาม profile เสมอ — minimize/screen-off เคยล้าง flag นี้แล้ว expand ไม่ตั้งคืน
        com.skyliner2008.jarvis.pet.LiveModeState.update(_currentProfile.value)
        installSoundHandler()

        // Only activate motion detection sensor when actively in FULL_SCREEN Desk Pet mode
        if (_state.value != AlwaysLiveState.FULL_SCREEN || _currentProfile.value != AlwaysLiveProfile.PET) {
            Log.d(TAG, "Not in FULL_SCREEN PET mode (state=${_state.value}, profile=${_currentProfile.value}) — skipping motion sensor start")
            return
        }

        if (petMotionDetector == null) {
            // The detector reports every event through PetMotionBridge, which
            // AlwaysLiveScreen routes into PetModeController -> PetStateMachine
            // (needs, rage, escalation, sounds). These constructor callbacks used
            // to ALSO write the avatar state directly, so each shake / flip was
            // handled twice and the direct write (always DIZZY / SLEEPING / HAPPY)
            // overwrote the state machine's result (ANGRY, fight-back, grumpy
            // wake-up). They only log now; the bridge is the single source.
            petMotionDetector = PetMotionDetector(
                context = context,
                onShake = { Log.d(TAG, "🌀 shake -> PetMotionBridge") },
                onFaceDown = { Log.d(TAG, "😴 face down -> PetMotionBridge") },
                onFaceUp = { Log.d(TAG, "☀️ face up -> PetMotionBridge") },
                onHeavyShake = { Log.d(TAG, "⚡ heavy shake -> PetMotionBridge") },
                onBoatRocking = { Log.d(TAG, "⛵ boat rocking -> PetMotionBridge") },
                onTableThump = { Log.d(TAG, "💥 table thump -> PetMotionBridge") }
            )
        }
        petMotionDetector?.start()
        RobotSoundEngine.playWakeUp()
    }

    /**
     * ออกจาก Pet profile จริง (สลับ profile / ปิด Always Live) — ล้าง persona flag และเสียงหุ่นยนต์
     * ถ้าแค่พับจอหรือปิดหน้าจอให้ใช้ [stopPetSensors] (profile ยังเป็น PET อยู่)
     */
    private fun stopPetMode() {
        Log.i(TAG, "🐾 Stopping Pet Mode")
        com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = false
        RobotSoundPlayer.handler = null
        stopPetSensors()
    }

    /** หยุด sensor/เสียงแวดล้อมของ Pet ชั่วคราว (minimize / screen off) โดยไม่เปลี่ยน persona */
    private fun stopPetSensors() {
        com.skyliner2008.jarvis.sound.AmbientSoundEngine.stop()
        faceDownSoundJob?.cancel()
        faceDownSoundJob = null
        faceUpSoundJob?.cancel()
        faceUpSoundJob = null
        shakeSoundJob?.cancel()
        shakeSoundJob = null
        petMotionDetector?.stop()
        petMotionDetector = null
    }

    // ─── Drive Mode & Telemetry Controller ───────────────────────────────
    private var drivePollingJob: Job? = null

    private fun startDriveMode() {
        // setProfile ถูกเรียกซ้ำหลายรอบตอนสลับโหมด (DRIVE -> DRIVE) — เดิมรีสตาร์ท polling ทุกครั้ง
        // ทำให้ job เก่าถูก cancel กลางคันและขึ้น warning ทั้งที่ไม่มีอะไรผิด
        if (drivePollingJob?.isActive == true) {
            Log.d(TAG, "🚗 Drive & Control Mode ทำงานอยู่แล้ว — ข้ามการเริ่มซ้ำ")
            return
        }
        Log.i(TAG, "🚗 Starting Drive & Control Mode")
        installDriveBridge()
        startDriveTelemetryPolling()
    }

    private fun stopDriveMode() {
        Log.i(TAG, "🚗 Stopping Drive & Control Mode")
        drivePollingJob?.cancel()
        drivePollingJob = null
    }

    private fun installDriveBridge() {
        val mediaInfo = com.skyliner2008.jarvis.media.MediaInfoProvider(context)

        com.skyliner2008.jarvis.drive.DriveBridge.onPlayPause = {
            val (handled, _) = mediaInfo.controlPlayback("toggle")
            if (!handled) {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            refreshMediaState()
        }

        com.skyliner2008.jarvis.drive.DriveBridge.onNextTrack = {
            val (handled, _) = mediaInfo.controlPlayback("next")
            if (!handled) {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            refreshMediaState()
        }

        com.skyliner2008.jarvis.drive.DriveBridge.onPrevTrack = {
            val (handled, _) = mediaInfo.controlPlayback("previous")
            if (!handled) {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            refreshMediaState()
        }

        com.skyliner2008.jarvis.drive.DriveBridge.onReadNotifications = {
            val notifText = com.skyliner2008.jarvis.notification.NotificationBridge.readRecent(count = 3)
            com.skyliner2008.jarvis.drive.DriveBridge.updateRecentNotification(notifText)
            if (notifText.isNotBlank()) {
                getOrCreateVoiceManager().speak(notifText, null)
            }
        }

        com.skyliner2008.jarvis.drive.DriveBridge.onStartNavigation = { destination ->
            val uri = if (!destination.isNullOrBlank()) {
                android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(destination)}")
            } else {
                android.net.Uri.parse("geo:0,0?q=")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { context.startActivity(fallback) } catch (_: Exception) {}
            }
        }
    }

    private fun refreshMediaState() {
        val mediaInfo = com.skyliner2008.jarvis.media.MediaInfoProvider(context)
        val nowPlaying = mediaInfo.getNowPlaying()
        if (nowPlaying != null) {
            com.skyliner2008.jarvis.drive.DriveBridge.updateMedia(
                title = nowPlaying.title,
                artist = nowPlaying.artist,
                appName = nowPlaying.appName,
                isPlaying = nowPlaying.isPlaying
            )
        } else {
            com.skyliner2008.jarvis.drive.DriveBridge.updateMedia(null, null, null, false)
        }
    }

    private fun startDriveTelemetryPolling() {
        drivePollingJob?.cancel()
        drivePollingJob = scope.launch(Dispatchers.IO) {
            val locationProvider = com.skyliner2008.jarvis.location.LocationProvider(context)
            while (isActive) {
                try {
                    val loc = locationProvider.getCurrentLocation()
                    if (loc != null) {
                        com.skyliner2008.jarvis.drive.DriveBridge.updateTelemetry(
                            speedKmh = loc.speedKmh,
                            address = loc.address,
                            lat = loc.latitude,
                            lng = loc.longitude
                        )
                    }
                    refreshMediaState()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // ปิดโหมด/สลับโปรไฟล์ ไม่ใช่ error
                } catch (e: Exception) {
                    Log.w(TAG, "Drive telemetry polling error: ${e.message}")
                }
                delay(4000L)
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    // ─── Configuration ───────────────────────────────────────────────────
    data class AlwaysLiveConfig(
        val wakeWord: String = "JARVIS",
        val backgroundListenEnabled: Boolean = true,
        /** Duty cycle: listen interval in ms (0 = continuous) */
        val listenIntervalMs: Long = 2000,
        /** Duty cycle: pause interval in ms */
        val pauseIntervalMs: Long = 1000,
        /** Mini avatar size in dp */
        val miniAvatarSizeDp: Int = 80
    )

    private val _config = MutableStateFlow(AlwaysLiveConfig())
    val config: StateFlow<AlwaysLiveConfig> = _config.asStateFlow()

    // ─── WakeLock ────────────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ─── Screen Receiver ─────────────────────────────────────────────────
    private var screenReceiver: BroadcastReceiver? = null
    private var isScreenReceiverRegistered = false

    // ─── Hotword Detector ────────────────────────────────────────────────
    private var hotwordDetector: HotwordDetector? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    private var hotwordVerifier: HotwordVerifier? = null
    @Volatile
    private var verifyingHotword = false

    private fun startHotwordDetection() {
        if (hotwordDetector == null) {
            hotwordDetector = HotwordDetector(context) {
                onHotwordEnergyDetected()
            }
        }
        hotwordDetector?.start(scope)
        Log.d(TAG, "Hotword detector started")
    }

    /**
     * HotwordDetector จับได้แค่ "เสียงดังพอ" — เสียงทีวี/เสียงคุยในรถก็เข้าเงื่อนไข
     * จึงถอดเสียงสั้นๆ ยืนยันคำปลุกก่อนปลุกจอ; ถ้าเครื่องถอดเสียงไม่ได้ ใช้พฤติกรรมเดิม (ปลุกเลย)
     * (review 2026-09-16)
     */
    private fun onHotwordEnergyDetected() {
        if (verifyingHotword) return
        verifyingHotword = true
        // ปล่อยไมค์จาก detector ก่อน — SpeechRecognizer ต้องใช้ไมค์เดียวกัน
        stopHotwordDetection()
        scope.launch {
            try {
                val verifier = hotwordVerifier ?: HotwordVerifier(context).also { hotwordVerifier = it }
                val heard = verifier.heardWakeWord(_config.value.wakeWord)
                when (heard) {
                    true -> onHotwordDetected()
                    null -> {
                        Log.d(TAG, "Wake word verification unavailable — waking on energy (legacy behaviour)")
                        onHotwordDetected()
                    }
                    false -> {
                        Log.d(TAG, "Sound was not the wake word — staying asleep")
                        if (_state.value == AlwaysLiveState.BACKGROUND_LISTEN &&
                            _config.value.backgroundListenEnabled &&
                            !com.skyliner2008.jarvis.ai.LiveSessionBridge.isActive()
                        ) {
                            startHotwordDetection()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Hotword verification failed — waking on energy", e)
                onHotwordDetected()
            } finally {
                verifyingHotword = false
            }
        }
    }

    private fun stopHotwordDetection() {
        hotwordDetector?.stop()
        Log.d(TAG, "Hotword detector stopped")
    }

    // ─── Screen Bright WakeLock (keeps screen on across all apps during Live mode) ───
    @Suppress("DEPRECATION")
    private var screenBrightWakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    fun acquireScreenBrightLock() {
        if (screenBrightWakeLock?.isHeld == true) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            screenBrightWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "jarvis:always_live_bright"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours limit
            }
            Log.d(TAG, "ScreenBrightWakeLock acquired (screen stays awake across all apps)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire ScreenBrightWakeLock", e)
        }
    }

    fun releaseScreenBrightLock() {
        try {
            screenBrightWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "ScreenBrightWakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release ScreenBrightWakeLock", e)
        }
        screenBrightWakeLock = null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — State Transitions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Enable Always Live → enter FULL_SCREEN mode
     */
    fun enable() {
        if (_state.value == AlwaysLiveState.FULL_SCREEN) {
            Log.d(TAG, "enable() already in FULL_SCREEN — ignoring")
            return
        }
        Log.i(TAG, "enable() → FULL_SCREEN")
        transitionTo(AlwaysLiveState.FULL_SCREEN)
        registerScreenReceiver()
        acquireScreenBrightLock()
        FloatingWidgetService.stopWidget(context)
        wakeScreen()
        if (_currentProfile.value == AlwaysLiveProfile.PET) {
            startPetMode()
        } else {
            startDriveMode()
        }
    }

    /**
     * Disable Always Live → enter OFF mode
     */
    fun disable() {
        Log.i(TAG, "disable() → OFF")
        transitionTo(AlwaysLiveState.OFF)
        unregisterScreenReceiver()
        stopHotwordDetection()
        stopDriveMode()
        stopPetMode()
        _currentProfile.value = AlwaysLiveProfile.CONTROL
        com.skyliner2008.jarvis.pet.LiveModeState.update(AlwaysLiveProfile.CONTROL)
        releaseWakeLock()
        releaseScreenBrightLock()
        // Stop floating widget if running
        FloatingWidgetService.stopWidget(context)
        // Reset screen flags on MainActivity so the screen sleeps normally in normal mode
        try {
            com.skyliner2008.jarvis.MainActivity.instance?.clearScreenFlags()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call MainActivity.clearScreenFlags()", e)
        }
    }

    /**
     * Minimize from full-screen to mini floating
     */
    fun minimize() {
        if (_state.value == AlwaysLiveState.FULL_SCREEN) {
            Log.i(TAG, "minimize() → MINI_FLOATING")
            transitionTo(AlwaysLiveState.MINI_FLOATING)
            acquireScreenBrightLock()
            // Stop pet motion sensor when minimized to floating widget (profile/persona ยังเป็น PET)
            stopPetSensors()
            FloatingWidgetService.startWidget(context)
        }
    }

    /**
     * Expand from mini floating to full-screen
     */
    fun expand() {
        if (_state.value == AlwaysLiveState.MINI_FLOATING ||
            _state.value == AlwaysLiveState.BACKGROUND_LISTEN) {
            Log.i(TAG, "expand() → FULL_SCREEN")
            stopHotwordDetection()
            transitionTo(AlwaysLiveState.FULL_SCREEN)
            acquireScreenBrightLock()
            FloatingWidgetService.stopWidget(context)
            wakeScreen()
            if (_currentProfile.value == AlwaysLiveProfile.PET) {
                startPetMode()
            }
            try {
                if (!com.skyliner2008.jarvis.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.skyliner2008.jarvis.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.skyliner2008.jarvis.HOTWORD_WAKE"
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MainActivity from expand", e)
            }
        }
    }

    /**
     * Screen turned off — enter background listen mode
     */
    fun onScreenOff() {
        if (_state.value == AlwaysLiveState.FULL_SCREEN ||
            _state.value == AlwaysLiveState.MINI_FLOATING) {
            Log.i(TAG, "onScreenOff() → BACKGROUND_LISTEN")
            transitionTo(AlwaysLiveState.BACKGROUND_LISTEN)
            setEmotion(AvatarEmotion.SLEEPING)
            releaseScreenBrightLock()
            // Immediately stop pet motion sensor and sound routines when screen turns off!
            // (ไม่ล้าง persona — เดิม stopPetMode() ตั้ง isPetMode=false ทำให้ reconnect ถัดไปกลายเป็น JARVIS)
            stopPetSensors()
            if (_config.value.backgroundListenEnabled) {
                acquireWakeLock()
                if (com.skyliner2008.jarvis.ai.LiveSessionBridge.isActive()) {
                    // Live session ฟังผ่านไมค์ VOICE_COMMUNICATION อยู่แล้ว — HotwordDetector (MIC 8k) จะแย่งไมค์
                    // ทำให้ตัวใดตัวหนึ่งได้ความเงียบ จึงไม่เปิดซ้อน
                    Log.i(TAG, "Live session active — skipping energy hotword detector (Live keeps listening)")
                } else {
                    startHotwordDetection()
                }
            }
            // Stop floating widget when screen is off
            FloatingWidgetService.stopWidget(context)
        }
    }

    /**
     * Screen turned on — in Control Mode (BACKGROUND_LISTEN), restore FULL_SCREEN and show above lockscreen
     */
    fun onScreenOn() {
        if (_state.value == AlwaysLiveState.BACKGROUND_LISTEN) {
            Log.i(TAG, "onScreenOn() → FULL_SCREEN (Control Mode active, restoring over lockscreen)")
            stopHotwordDetection()
            transitionTo(AlwaysLiveState.FULL_SCREEN)
            setEmotion(AvatarEmotion.IDLE)
            releaseWakeLock()
            acquireScreenBrightLock()
            FloatingWidgetService.stopWidget(context)
            if (_currentProfile.value == AlwaysLiveProfile.PET) {
                startPetMode()
            }

            try {
                if (!com.skyliner2008.jarvis.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.skyliner2008.jarvis.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.skyliner2008.jarvis.HOTWORD_WAKE"
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MainActivity onScreenOn", e)
            }
        }
    }

    /**
     * Hotword detected — wake screen and expand to full-screen
     */
    fun onHotwordDetected() {
        Log.i(TAG, "onHotwordDetected() — waking up!")
        wakeScreen()
        stopHotwordDetection()
        setEmotion(AvatarEmotion.LISTENING)
        transitionTo(AlwaysLiveState.FULL_SCREEN)
        releaseWakeLock()
        acquireScreenBrightLock()
        FloatingWidgetService.stopWidget(context)
        if (_currentProfile.value == AlwaysLiveProfile.PET) {
            startPetMode()
        }

        try {
            if (!com.skyliner2008.jarvis.MainActivity.isActivityResumed) {
                val intent = Intent(context, com.skyliner2008.jarvis.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    action = "com.skyliner2008.jarvis.HOTWORD_WAKE"
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity from hotword wake", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API — Avatar State Updates
    // ═══════════════════════════════════════════════════════════════════════

    fun setEmotion(emotion: AvatarEmotion) {
        _avatarState.update { it.copy(emotion = emotion) }
    }

    fun setAudioLevel(level: Float) {
        _avatarState.update { it.copy(audioLevel = level.coerceIn(0f, 1f)) }
    }

    fun setSpeaking(speaking: Boolean) {
        val wasSpeaking = _avatarState.value.isSpeaking
        _avatarState.update { it.copy(isSpeaking = speaking) }
        if (speaking && !wasSpeaking && _currentProfile.value == AlwaysLiveProfile.PET) {
            RobotSoundEngine.playHappyChirp()
        }
    }

    fun setStatusText(text: String?) {
        _avatarState.update { it.copy(statusText = text) }
    }

    fun setGlowIntensity(intensity: Float) {
        _avatarState.update { it.copy(glowIntensity = intensity.coerceIn(0f, 1f)) }
    }

    /**
     * Route AI processing state → avatar emotion
     * Called by JarvisAutomationService during live sessions
     */
    fun onAiStateChanged(aiState: String) {
        when (aiState) {
            "listening" -> setEmotion(AvatarEmotion.LISTENING)
            "thinking", "processing" -> setEmotion(AvatarEmotion.THINKING)
            "speaking", "responding" -> {
                setEmotion(AvatarEmotion.SPEAKING)
                setSpeaking(true)
            }
            "idle" -> {
                setEmotion(AvatarEmotion.IDLE)
                setSpeaking(false)
            }
            "error" -> setEmotion(AvatarEmotion.ANGRY)
            "success" -> setEmotion(AvatarEmotion.HAPPY)
            "sleeping" -> setEmotion(AvatarEmotion.SLEEPING)
        }
    }

    /**
     * Detect emotion from AI response sentiment or explicit structured tags.
     * Supports:
     * 1. Bracketed structured tags: [HAPPY], [LOVE], [EXCITED], [SAD], [ANGRY], [CONFUSED], [WINK], [POUT], [DIZZY], [SURPRISED], [BORED], [ENRAGED], [SLEEPING], [THINKING], [LISTENING], [IDLE]
     * 2. Pipe-delimited commands: HAPPY|..., SPEAKING|...
     * 3. Comprehensive natural sentiment keyword matching
     */
    fun detectSentiment(text: String) {
        if (text.isBlank()) return

        // 1. Check for bracketed structured tags: [EMOTION]
        val tagRegex = Regex("\\[(HAPPY|LOVE|EXCITED|SAD|ANGRY|CONFUSED|WINK|POUT|DIZZY|SURPRISED|SHOCK|BORED|ENRAGED|SLEEPING|THINKING|LISTENING|IDLE|DEAD|LAUGHING|LAUGH|MUSIC|VR_MODE|VR|DIVING|EVIL|FOCUSED|FOCUS|SHY|DISGUSTED|DISGUST|CAMERA_MODE|CAMERA|EATING|EAT|DRINKING|DRINK)\\]", RegexOption.IGNORE_CASE)
        val tagMatch = tagRegex.find(text)
        if (tagMatch != null) {
            val rawName = tagMatch.groupValues[1].uppercase()
            val mappedEmotion = when (rawName) {
                "LAUGH" -> AvatarEmotion.LAUGHING
                "VR" -> AvatarEmotion.VR_MODE
                "FOCUS" -> AvatarEmotion.FOCUSED
                "DISGUST" -> AvatarEmotion.DISGUSTED
                "CAMERA" -> AvatarEmotion.CAMERA_MODE
                "EAT" -> AvatarEmotion.EATING
                "DRINK" -> AvatarEmotion.DRINKING
                "SHOCK" -> AvatarEmotion.SURPRISED
                else -> try { AvatarEmotion.valueOf(rawName) } catch (_: Exception) { null }
            }
            if (mappedEmotion != null) {
                setEmotion(mappedEmotion)
                return
            }
        }

        // 2. Check for pipe-delimited syntax: EMOTION|...
        if (text.contains("|")) {
            val head = text.substringBefore("|").trim().uppercase()
            try {
                val emotion = AvatarEmotion.valueOf(head)
                setEmotion(emotion)
                return
            } catch (_: Exception) {}
        }

        // 3. Fallback to comprehensive natural sentiment keyword matching
        val lower = text.lowercase()
        when {
            lower.containsAny("ยินดี", "สำเร็จ", "เสร็จแล้ว", "ดีใจ", "ยิ้ม", "congratulations", "success", "great", "awesome", "perfect", "สุขสันต์", "มีความสุข") ->
                setEmotion(AvatarEmotion.HAPPY)
            lower.containsAny("หัวเราะ", "ขำ", "555", "ฮา", "laugh", "funny", "lol", "kpop") ->
                setEmotion(AvatarEmotion.LAUGHING)
            lower.containsAny("ตื่นเต้น", "ว้าว", "สุดยอด", "พบแล้ว", "eureka", "breakthrough", "ทำได้", "amazing", "incredible", "excited") ->
                setEmotion(AvatarEmotion.EXCITED)
            lower.containsAny("รัก", "love", "ขอบคุณ", "thank", "appreciate", "ชอบคุณ", "หัวใจ", "เอ็นดู", "น่ารัก") ->
                setEmotion(AvatarEmotion.LOVE)
            lower.containsAny("เสียใจ", "ขอโทษ", "sorry", "unfortunately", "ไม่สามารถ", "failed", "ร้องไห้", "เศร้า", "ผิดหวัง") ->
                setEmotion(AvatarEmotion.SAD)
            lower.containsAny("ผิดพลาด", "error", "crash", "bug", "ปัญหา", "โกรธ", "โมโห", "หงุดหงิด", "angry", "furious") ->
                setEmotion(AvatarEmotion.ANGRY)
            lower.containsAny("ตัวร้าย", "ปีศาจ", "ปิศาจ", "evil", "demon", "devil") ->
                setEmotion(AvatarEmotion.EVIL)
            lower.containsAny("สงสัย", "งง", "ไม่เข้าใจ", "คืออะไรนะ", "confused", "puzzled", "hmm") ->
                setEmotion(AvatarEmotion.CONFUSED)
            lower.containsAny("ตกใจ", "ประหลาดใจ", "surprised", "shocked", "เหวอ") ->
                setEmotion(AvatarEmotion.SURPRISED)
            lower.containsAny("เบื่อ", "เซ็ง", "bored", "dull") ->
                setEmotion(AvatarEmotion.BORED)
            lower.containsAny("ง่วง", "นอน", "พักผ่อน", "ฝันดี", "sleep", "goodnight", "zzz") ->
                setEmotion(AvatarEmotion.SLEEPING)
            lower.containsAny("เวียนหัว", "มึน", "ตาลาย", "dizzy") ->
                setEmotion(AvatarEmotion.DIZZY)
            lower.containsAny("ตาย", "สลบ", "สลบเหมือด", "dead", "knockout") ->
                setEmotion(AvatarEmotion.DEAD)
            lower.containsAny("งอน", "หน้าบูด", "pout") ->
                setEmotion(AvatarEmotion.POUT)
            lower.containsAny("เขิน", "อาย", "หน้าแดง", "แก้มแดง", "shy", "blush") ->
                setEmotion(AvatarEmotion.SHY)
            lower.containsAny("รังเกียจ", "อี๋", "ขยะ", "เหม็น", "disgusted", "gross", "yuck") ->
                setEmotion(AvatarEmotion.DISGUSTED)
            lower.containsAny("ฟังเพลง", "เปิดเพลง", "เพลง", "หูฟัง", "music", "song", "headphones") ->
                setEmotion(AvatarEmotion.MUSIC)
            lower.containsAny("แว่น vr", "vision pro", "โลกเสมือน", "ดูหนัง", "vr") ->
                setEmotion(AvatarEmotion.VR_MODE)
            lower.containsAny("ดำน้ำ", "ว่ายน้ำ", "ท่อหายใจ", "diving", "snorkel") ->
                setEmotion(AvatarEmotion.DIVING)
            lower.containsAny("สมาธิ", "โฟกัส", "สแกน", "focus", "scanning") ->
                setEmotion(AvatarEmotion.FOCUSED)
            lower.containsAny("ถ่ายรูป", "แชะ", "กล้อง", "camera", "photo") ->
                setEmotion(AvatarEmotion.CAMERA_MODE)
            lower.containsAny("กิน", "อร่อย", "หิวข้าว", "เบอร์เกอร์", "eat", "yummy", "burger", "food") ->
                setEmotion(AvatarEmotion.EATING)
            lower.containsAny("ดื่ม", "จิบ", "หิวน้ำ", "กาแฟ", "เบียร์", "drink", "coffee", "beer") ->
                setEmotion(AvatarEmotion.DRINKING)
        }
    }

    fun updateConfig(update: (AlwaysLiveConfig) -> AlwaysLiveConfig) {
        _config.update { update(it) }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — State Transition
    // ═══════════════════════════════════════════════════════════════════════

    private fun transitionTo(newState: AlwaysLiveState) {
        val oldState = _state.value
        if (oldState == newState) return
        Log.d(TAG, "Transition: $oldState → $newState")
        _state.value = newState
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — Screen Receiver
    // ═══════════════════════════════════════════════════════════════════════

    private fun registerScreenReceiver() {
        if (isScreenReceiverRegistered) return

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenReceiver, filter)
        }
        isScreenReceiverRegistered = true
        Log.d(TAG, "Screen receiver registered")
    }

    private fun unregisterScreenReceiver() {
        if (!isScreenReceiverRegistered) return
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister screen receiver", e)
        }
        screenReceiver = null
        isScreenReceiverRegistered = false
        Log.d(TAG, "Screen receiver unregistered")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — WakeLock
    // ═══════════════════════════════════════════════════════════════════════

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "jarvis:always_live_listen"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // Max 4 hours to be safe
            }
            Log.d(TAG, "WakeLock acquired (PARTIAL_WAKE_LOCK)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock", e)
        }
        wakeLock = null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal — Screen Wake
    // ═══════════════════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    fun wakeScreen() {
        // Only keep screen permanently bright if already in active control mode
        if (_state.value == AlwaysLiveState.FULL_SCREEN || _state.value == AlwaysLiveState.MINI_FLOATING) {
            acquireScreenBrightLock()
        }
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "jarvis:temporary_wake"
            )
            wl.acquire(10000) // Wake screen for 10 seconds
            Log.i(TAG, "Screen temporary wakeup triggered with WakeLock (10s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen with WakeLock", e)
        }
        // Only show MainActivity over lockscreen if actively in Control Mode
        if (_state.value == AlwaysLiveState.FULL_SCREEN || _state.value == AlwaysLiveState.BACKGROUND_LISTEN) {
            try {
                com.skyliner2008.jarvis.MainActivity.instance?.turnScreenOnTemporarily()
                if (!com.skyliner2008.jarvis.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.skyliner2008.jarvis.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.skyliner2008.jarvis.HOTWORD_WAKE"
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call MainActivity.turnScreenOnTemporarily() or startActivity", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════════════

    fun destroy() {
        disable()
        voiceManager?.shutdown()
        voiceManager = null
        _instance = null
        Log.i(TAG, "AlwaysLiveManager destroyed")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
