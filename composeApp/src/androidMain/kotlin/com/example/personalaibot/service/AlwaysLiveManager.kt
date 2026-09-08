package com.example.personalaibot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.personalaibot.ui.component.avatar.AvatarEmotion
import com.example.personalaibot.ui.component.avatar.AvatarState
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

    private fun startHotwordDetection() {
        if (hotwordDetector == null) {
            hotwordDetector = HotwordDetector(context) {
                onHotwordDetected()
            }
        }
        hotwordDetector?.start(scope)
        Log.d(TAG, "Hotword detector started")
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
    }

    /**
     * Disable Always Live → enter OFF mode
     */
    fun disable() {
        Log.i(TAG, "disable() → OFF")
        transitionTo(AlwaysLiveState.OFF)
        unregisterScreenReceiver()
        stopHotwordDetection()
        releaseWakeLock()
        releaseScreenBrightLock()
        // Stop floating widget if running
        FloatingWidgetService.stopWidget(context)
        // Reset screen flags on MainActivity so the screen sleeps normally in normal mode
        try {
            com.example.personalaibot.MainActivity.instance?.clearScreenFlags()
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
            try {
                if (!com.example.personalaibot.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.example.personalaibot.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.example.personalaibot.HOTWORD_WAKE"
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
            if (_config.value.backgroundListenEnabled) {
                acquireWakeLock()
                startHotwordDetection()
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

            try {
                if (!com.example.personalaibot.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.example.personalaibot.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.example.personalaibot.HOTWORD_WAKE"
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

        try {
            if (!com.example.personalaibot.MainActivity.isActivityResumed) {
                val intent = Intent(context, com.example.personalaibot.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    action = "com.example.personalaibot.HOTWORD_WAKE"
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
        _avatarState.update { it.copy(isSpeaking = speaking) }
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
     * Detect emotion from AI response sentiment
     * Simple keyword-based approach — can be improved with NLP
     */
    fun detectSentiment(text: String) {
        val lower = text.lowercase()
        when {
            lower.containsAny("ยินดี", "สำเร็จ", "เสร็จแล้ว", "ดีใจ", "congratulations", "success", "great", "awesome") ->
                setEmotion(AvatarEmotion.HAPPY)
            lower.containsAny("เสียใจ", "ขอโทษ", "sorry", "unfortunately", "ไม่สามารถ", "failed") ->
                setEmotion(AvatarEmotion.SAD)
            lower.containsAny("ผิดพลาด", "error", "crash", "bug", "ปัญหา") ->
                setEmotion(AvatarEmotion.ANGRY)
            lower.containsAny("รัก", "love", "ขอบคุณ", "thank", "appreciate") ->
                setEmotion(AvatarEmotion.LOVE)
            lower.containsAny("พบแล้ว", "eureka", "breakthrough", "ทำได้", "amazing", "incredible") ->
                setEmotion(AvatarEmotion.EXCITED)
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
                com.example.personalaibot.MainActivity.instance?.turnScreenOnTemporarily()
                if (!com.example.personalaibot.MainActivity.isActivityResumed) {
                    val intent = Intent(context, com.example.personalaibot.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        action = "com.example.personalaibot.HOTWORD_WAKE"
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
