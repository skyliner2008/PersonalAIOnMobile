package com.example.personalaibot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.personalaibot.MainActivity
import com.example.personalaibot.ui.component.avatar.AvatarState
import com.example.personalaibot.ui.component.avatar.JarvisAvatar
import com.example.personalaibot.ui.theme.JarvisTheme

/**
 * FloatingWidgetService — JARVIS mini robot overlay ที่ลอยทับแอปอื่น
 *
 * Evolution จาก text bubble เดิม → animated robot avatar ตัวเล็ก
 *
 * Features:
 *  - ComposeView ที่ render JarvisAvatar ขนาด ~80dp
 *  - แสดงอารมณ์ตาม AvatarState จาก AlwaysLiveManager
 *  - ลากเพื่อย้ายตำแหน่งได้ + snap to edges
 *  - แตะเพื่อเปิด app หลัก
 *  - Double-tap เพื่อ expand to full-screen
 *  - แตะค้างเพื่อ toggle Live mode
 *
 * ต้องการ permission: SYSTEM_ALERT_WINDOW
 */
class FloatingWidgetService : Service() {

    companion object {
        const val TAG = "FloatingWidgetService"
        const val CHANNEL_ID = "JarvisFloatingWidget"
        const val ACTION_START_LIVE = "com.example.personalaibot.START_LIVE"
        const val ACTION_STOP_LIVE  = "com.example.personalaibot.STOP_LIVE"
        const val ACTION_SET_LISTENING = "com.example.personalaibot.SET_LISTENING"
        const val ACTION_WIDGET_CLOSED = "com.example.personalaibot.WIDGET_CLOSED"
        const val ACTION_UPDATE_AVATAR = "com.example.personalaibot.UPDATE_AVATAR"
        const val EXTRA_LISTENING = "isListening"

        fun startWidget(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start FloatingWidgetService: SYSTEM_ALERT_WINDOW not granted")
                return
            }
            try {
                val intent = Intent(context, FloatingWidgetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FloatingWidgetService", e)
            }
        }

        fun stopWidget(context: Context) {
            try {
                context.stopService(Intent(context, FloatingWidgetService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop FloatingWidgetService", e)
            }
        }

        fun setListeningState(context: Context, listening: Boolean) {
            try {
                context.startService(
                    Intent(context, FloatingWidgetService::class.java).apply {
                        action = ACTION_SET_LISTENING
                        putExtra(EXTRA_LISTENING, listening)
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set listening state on widget", e)
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    // Touch handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false

    // Double-tap detection
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIMEOUT = 300L

    // Lifecycle management for ComposeView in Service
    private val lifecycleOwner = ServiceLifecycleOwner()

    // Avatar state — updated from AlwaysLiveManager
    private val avatarStateMutable = mutableStateOf(AvatarState())

    // Use ComposeView or fallback to classic View?
    private var useComposeView = true

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(2, buildNotification())

        // ตรวจสอบ permission ก่อนเริ่ม
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot start FloatingWidgetService: Permission denied for SYSTEM_ALERT_WINDOW")
            stopSelf()
            return
        }

        // Start lifecycle for ComposeView
        lifecycleOwner.onCreate()

        createFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_LISTENING -> {
                val listening = intent.getBooleanExtra(EXTRA_LISTENING, false)
                updateListeningState(listening)
            }
            ACTION_UPDATE_AVATAR -> {
                // Avatar state is automatically synced via AlwaysLiveManager StateFlow
            }
        }
        return START_STICKY
    }

    private fun createFloatingView() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val widgetSize = AlwaysLiveManager.getInstanceOrNull()?.config?.value?.miniAvatarSizeDp ?: 80
            val sizePixels = (widgetSize * resources.displayMetrics.density).toInt()

            // Try ComposeView first, fallback to classic View
            val view: View = if (useComposeView) {
                try {
                    createComposeFloatingView(sizePixels)
                } catch (e: Exception) {
                    Log.w(TAG, "ComposeView failed, falling back to classic View", e)
                    useComposeView = false
                    createClassicFloatingView()
                }
            } else {
                createClassicFloatingView()
            }

            floatingView = view

            val params = WindowManager.LayoutParams(
                if (useComposeView) sizePixels + 16 else WindowManager.LayoutParams.WRAP_CONTENT,
                if (useComposeView) sizePixels + 16 else WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 24
                y = 200
            }

            windowManager?.addView(floatingView, params)
            setupTouchListener(params)

            // Start observing avatar state from AlwaysLiveManager
            startAvatarStateObservation()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
    }

    /**
     * Create ComposeView-based floating widget with animated JarvisAvatar
     */
    private fun createComposeFloatingView(sizePixels: Int): View {
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val state = avatarStateMutable.value
                val manager = AlwaysLiveManager.getInstanceOrNull()
                val currentState by manager?.avatarState?.collectAsState()
                    ?: remember { mutableStateOf(AvatarState()) }

                // Sync state from manager
                LaunchedEffect(currentState) {
                    avatarStateMutable.value = currentState
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(JarvisTheme.Dark.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    JarvisAvatar(
                        state = currentState,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        return composeView
    }

    /**
     * Classic View fallback (same as original but with status colors)
     */
    private fun createClassicFloatingView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC1A1A2E"))
        }

        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                rightMargin = 10
            }
            setBackgroundColor(Color.parseColor("#00E5FF"))
        }

        val statusText = TextView(this).apply {
            text = "JARVIS"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 8, 0)
        }

        val closeButton = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                leftMargin = 16
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            alpha = 0.5f
            setOnClickListener {
                broadcastWidgetClosed()
                stopSelf()
            }
        }

        layout.addView(statusDot)
        layout.addView(statusText)
        layout.addView(closeButton)

        return layout
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        val view = floatingView ?: return
        var longPressStart = 0L
        var hasMoved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    longPressStart = System.currentTimeMillis()
                    hasMoved = false
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!hasMoved && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) {
                        hasMoved = true
                        isMoving = true
                    }
                    if (isMoving) {
                        params.x = initialX - dx   // END gravity → invert X
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - longPressStart
                    if (!hasMoved) {
                        val now = System.currentTimeMillis()
                        if (elapsed > 600) {
                            // Long press — toggle Live mode
                            broadcastToggleLive()
                        } else if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            // Double tap — expand to full-screen
                            expandToFullScreen()
                            lastTapTime = 0L
                        } else {
                            // Single tap — mark time (wait for possible double tap)
                            lastTapTime = now
                            // Delayed single-tap action (open app)
                            view.postDelayed({
                                if (lastTapTime == now) {
                                    openMainApp()
                                }
                            }, DOUBLE_TAP_TIMEOUT)
                        }
                    } else {
                        // After drag — snap to nearest edge
                        snapToEdge(params)
                    }
                    isMoving = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Snap widget to nearest screen edge after drag
     */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        try {
            val dm = resources.displayMetrics
            val screenWidth = dm.widthPixels
            val viewWidth = floatingView?.width ?: 0

            // Calculate actual X position (END gravity: x=0 means right edge)
            val actualX = screenWidth - params.x - viewWidth

            // Snap to closer edge
            if (actualX < screenWidth / 2) {
                // Closer to left → snap left
                params.x = screenWidth - viewWidth - 8
            } else {
                // Closer to right → snap right
                params.x = 8
            }

            windowManager?.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to snap to edge", e)
        }
    }

    /**
     * Expand to AlwaysLive full-screen mode
     */
    private fun expandToFullScreen() {
        AlwaysLiveManager.getInstanceOrNull()?.expand()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "com.example.personalaibot.EXPAND_ALWAYS_LIVE"
        }
        startActivity(intent)
    }

    /**
     * Start observing avatar state changes from AlwaysLiveManager
     */
    private fun startAvatarStateObservation() {
        // The ComposeView automatically observes via collectAsState()
        // For classic view, we would need a manual observer
        if (!useComposeView) {
            // Classic view doesn't support real-time avatar updates
            // It uses the simple LISTENING/IDLE indicator instead
        }
    }

    private fun updateListeningState(listening: Boolean) {
        if (useComposeView) {
            // ComposeView auto-updates from AlwaysLiveManager
            return
        }

        // Classic view fallback update
        floatingView?.post {
            val layout = floatingView as? LinearLayout ?: return@post
            val statusDot = layout.getChildAt(0)
            val statusText = layout.getChildAt(1) as? TextView

            if (listening) {
                statusDot?.setBackgroundColor(Color.parseColor("#FF4444"))
                statusText?.text = "● LIVE"
                statusText?.setTextColor(Color.parseColor("#FF4444"))
            } else {
                statusDot?.setBackgroundColor(Color.parseColor("#00E5FF"))
                statusText?.text = "JARVIS"
                statusText?.setTextColor(Color.WHITE)
            }
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun broadcastToggleLive() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "com.example.personalaibot.TOGGLE_LIVE"
        }
        startActivity(intent)
    }

    private fun broadcastWidgetClosed() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = ACTION_WIDGET_CLOSED
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        lifecycleOwner.onDestroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating view", e)
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Widget Active")
            .setContentText("Double-tap วิดเจ็ตเพื่อเปิด Always Live · แตะค้างเพื่อ toggle Live")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Floating Widget",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "JARVIS overlay widget"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ServiceLifecycleOwner — Provides Lifecycle for ComposeView in a Service
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Custom LifecycleOwner + SavedStateRegistryOwner for ComposeView inside a Service
 *
 * ComposeView requires a LifecycleOwner and SavedStateRegistryOwner to function.
 * Since Service doesn't implement these, we provide our own.
 * Service calls onCreate/onStart/onResume/onPause/onStop/onDestroy manually.
 */
class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
