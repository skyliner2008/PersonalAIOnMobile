package com.example.personalaibot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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
import androidx.core.app.NotificationCompat
import com.example.personalaibot.MainActivity

/**
 * FloatingWidgetService — วิดเจ็ต JARVIS แบบ overlay ลอยบนหน้าจอ
 *
 * Features:
 *  - ฟองสนทนาขนาดเล็กลอยอยู่มุมจอ
 *  - แสดงสถานะ LIVE / IDLE ด้วยสีที่ต่างกัน
 *  - ลากเพื่อย้ายตำแหน่งได้
 *  - แตะเพื่อเปิด app หลัก
 *  - แตะค้างเพื่อเปิด/ปิด Live mode
 *
 * ต้องการ permission: SYSTEM_ALERT_WINDOW (android.permission.SYSTEM_ALERT_WINDOW)
 * User ต้องไป Settings > Apps > Special app access > Display over other apps เพื่ออนุญาต
 */
class FloatingWidgetService : Service() {

    companion object {
        const val TAG = "FloatingWidgetService"
        const val CHANNEL_ID = "JarvisFloatingWidget"
        const val ACTION_START_LIVE = "com.example.personalaibot.START_LIVE"
        const val ACTION_STOP_LIVE  = "com.example.personalaibot.STOP_LIVE"
        const val ACTION_SET_LISTENING = "com.example.personalaibot.SET_LISTENING"
        const val EXTRA_LISTENING = "isListening"

        fun startWidget(context: Context) {
            val intent = Intent(context, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopWidget(context: Context) {
            context.stopService(Intent(context, FloatingWidgetService::class.java))
        }

        fun setListeningState(context: Context, listening: Boolean) {
            context.startService(
                Intent(context, FloatingWidgetService::class.java).apply {
                    action = ACTION_SET_LISTENING
                    putExtra(EXTRA_LISTENING, listening)
                }
            )
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var statusDot: View? = null
    private var statusText: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false

    override fun onCreate() {
        super.onCreate()
        
        // ตรวจสอบ permission ก่อนเริ่ม
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot start FloatingWidgetService: Permission denied for SYSTEM_ALERT_WINDOW")
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(2, buildNotification())
        createFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_LISTENING -> {
                val listening = intent.getBooleanExtra(EXTRA_LISTENING, false)
                updateListeningState(listening)
            }
        }
        return START_STICKY
    }

    private fun createFloatingView() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(Color.parseColor("#CC1A1A2E"))  // Dark navy translucent
            }

            // Pulsing dot
            statusDot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    rightMargin = 10
                }
                setBackgroundColor(Color.parseColor("#00E5FF"))  // Jarvis cyan idle
            }

            // Status text
            statusText = TextView(this).apply {
                text = "JARVIS"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 8, 0)
            }

            layout.addView(statusDot)
            layout.addView(statusText)

            floatingView = layout

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
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
                        if (elapsed > 600) {
                            // Long press — toggle Live mode
                            broadcastToggleLive()
                        } else {
                            // Tap — open app
                            openMainApp()
                        }
                    }
                    isMoving = false
                    true
                }
                else -> false
            }
        }
    }

    private fun updateListeningState(listening: Boolean) {
        floatingView?.post {
            if (listening) {
                statusDot?.setBackgroundColor(Color.parseColor("#FF4444"))  // Red listening
                statusText?.text = "● LIVE"
                statusText?.setTextColor(Color.parseColor("#FF4444"))
            } else {
                statusDot?.setBackgroundColor(Color.parseColor("#00E5FF"))  // Cyan idle
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

    override fun onDestroy() {
        super.onDestroy()
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
            .setContentText("แตะค้างที่วิดเจ็ตเพื่อเปิด Live Mode")
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
