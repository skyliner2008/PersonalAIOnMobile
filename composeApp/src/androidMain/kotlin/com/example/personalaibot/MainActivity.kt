package com.example.personalaibot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.os.PowerManager
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.service.FloatingWidgetService
import com.example.personalaibot.service.JarvisService
import com.example.personalaibot.service.JarvisAutomationService
import com.example.personalaibot.voice.VoiceManager
import com.example.personalaibot.tools.file.FileToolExecutor

import android.media.AudioManager
import android.content.Context
import android.os.Environment
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope

import android.view.WindowManager
import android.app.KeyguardManager

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var instance: MainActivity? = null
            private set
        var isActivityResumed: Boolean = false
            private set
    }

    private lateinit var voiceManager: VoiceManager

    // Callback ส่งกลับไปที่ Compose เพื่อ toggle live mode จาก widget
    private var onToggleLiveFromWidget: (() -> Unit)? = null
    private var onWidgetClosedCallback: (() -> Unit)? = null
    private var onExpandAlwaysLiveCallback: (() -> Unit)? = null
    private var onCloseAlwaysLiveCallback: (() -> Unit)? = null
    private lateinit var alwaysLiveManager: com.example.personalaibot.service.AlwaysLiveManager

    /**
     * Cached singleton — re-creating this on every download tap leaks the
     * previous OrtSession (held in a static OrtEnvironment) and re-installs
     * a new inference delegate on the same provider for no benefit.
     */
    private var onnxManager: com.example.personalaibot.data.embedding.AndroidLocalOnnxManager? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startJarvisService()
        }
    }

    // ขอ SYSTEM_ALERT_WINDOW permission สำหรับ floating widget
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        if (canDrawOverlay()) {
            FloatingWidgetService.startWidget(applicationContext)
        } else {
            Toast.makeText(this, "Permission denied for Floating Widget", Toast.LENGTH_SHORT).show()
        }
    }

    // ขอ MANAGE_EXTERNAL_STORAGE สำหรับ All Files Access (Android 11+)
    private val allFilesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    private val _allFilesAccessGranted = androidx.compose.runtime.mutableStateOf(false)

    /** สถานะ Setup Checklist: key = notif/mic/camera/overlay/files/battery */
    private val _setupStatus = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    private fun updatePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            _allFilesAccessGranted.value = Environment.isExternalStorageManager()
        } else {
            // Android 10 and below use standard runtime permissions
            _allFilesAccessGranted.value = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        _setupStatus["notif"] = NotificationManagerCompat.from(this).areNotificationsEnabled()
        _setupStatus["mic"] = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _setupStatus["camera"] = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _setupStatus["overlay"] = canDrawOverlay()
        _setupStatus["files"] = _allFilesAccessGranted.value
        _setupStatus["accessibility"] = com.example.personalaibot.service.JarvisAccessibilityService.isEnabled()
        _setupStatus["battery"] = try {
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) { true }
    }

    /** สร้างรายการ Setup Checklist สำหรับ SettingsDialog (อ่าน state ตอน recompose) */
    private fun buildSetupChecks(): List<com.example.personalaibot.ui.screen.SetupCheckItem> {
        fun granted(key: String) = _setupStatus[key] == true
        return listOf(
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "การแจ้งเตือน", "จำเป็นสำหรับ Signal Alert และสถานะบริการพื้นหลัง", granted("notif"),
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "ไมโครโฟน", "ใช้โหมด Live / สั่งงานด้วยเสียง", granted("mic"),
            ) { requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "กล้อง", "ใช้ Vision / เปิดตาดูกล้อง", granted("camera"),
            ) { requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "แสดงทับแอปอื่น (Overlay)", "ใช้ Floating Widget", granted("overlay"),
            ) { requestOverlayPermission() },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "เข้าถึงไฟล์ทั้งหมด", "ใช้อ่าน/สร้างไฟล์ในเครื่อง (เช่น bill, Excel)", granted("files"),
            ) { requestAllFilesPermission() },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "ปิด Battery Optimization", "ให้ Signal Alert เช็คเงื่อนไขต่อเนื่องในเบื้องหลัง", granted("battery"),
            ) {
                // ต้องประกาศ REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ใน manifest ด้วย
                // ไม่งั้น intent นี้ถูกระบบเมินเงียบๆ (ไม่ throw — catch ไม่ทำงาน)
                val candidates = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
                for (intent in candidates) {
                    if (intent.resolveActivity(packageManager) != null) {
                        try {
                            startActivity(intent)
                            break
                        } catch (_: Exception) { /* ลองตัวถัดไป */ }
                    }
                }
            },
            com.example.personalaibot.ui.screen.SetupCheckItem(
                "ควบคุมเครื่อง (Accessibility)", "ให้ JARVIS ควบคุมแอปอื่น, อ่านจอ, แตะปุ่มแทนคุณ", granted("accessibility"),
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        updatePermissionStatus()
        // Ensure screen flags are cleared in normal mode, or kept over lockscreen in Control Mode
        if (::alwaysLiveManager.isInitialized) {
            val state = alwaysLiveManager.state.value
            if (state == com.example.personalaibot.service.AlwaysLiveManager.AlwaysLiveState.OFF) {
                clearScreenFlags()
            } else {
                turnScreenOnTemporarily()
                setKeepScreenOn(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        // If Always Live is running in FULL_SCREEN, minimize to Floating Widget only if user actually
        // leaves to another app (interactive screen, not locked, not finishing)
        if (::alwaysLiveManager.isInitialized &&
            alwaysLiveManager.state.value == com.example.personalaibot.service.AlwaysLiveManager.AlwaysLiveState.FULL_SCREEN) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (pm?.isInteractive == true && km?.isKeyguardLocked != true && !isFinishing) {
                alwaysLiveManager.minimize()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // When device display turns off and app stops in normal mode, clear any lockscreen flags
        if (::alwaysLiveManager.isInitialized &&
            alwaysLiveManager.state.value == com.example.personalaibot.service.AlwaysLiveManager.AlwaysLiveState.OFF) {
            clearScreenFlags()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        instance = this
        com.example.personalaibot.automation.AndroidContextHolder.appContext = applicationContext

        // Clear any leftover screen flags so normal mode never shows over lockscreen
        clearScreenFlags()

        if (intent?.action == "com.example.personalaibot.HOTWORD_WAKE") {
            wakeAndTurnScreenOn()
            setKeepScreenOn(true)
        }
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val speaker = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                audioManager.setCommunicationDevice(speaker)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }

        val driverFactory = DatabaseDriverFactory(applicationContext)
        voiceManager = VoiceManager(applicationContext)
        val fileToolExecutor = FileToolExecutor(applicationContext)
        val deviceControlExecutor = com.example.personalaibot.tools.device.DeviceControlExecutor(applicationContext)
        com.example.personalaibot.tools.ToolExecutor.initDeviceExecutor(deviceControlExecutor)
        alwaysLiveManager = com.example.personalaibot.service.AlwaysLiveManager.getInstance(applicationContext)

        checkAndRequestPermissions()

        setContent {
            App(
                databaseDriverFactory = driverFactory,
                voiceManager = voiceManager,
                onKeepScreenOn = { keepOn ->
                    setKeepScreenOn(keepOn)
                },
                onStartWidget = { 
                    if (canDrawOverlay()) {
                        FloatingWidgetService.startWidget(applicationContext)
                    } else {
                        requestOverlayPermission()
                    }
                },
                onStopWidget = { FloatingWidgetService.stopWidget(applicationContext) },
                onSetWidgetListening = { listening ->
                    FloatingWidgetService.setListeningState(applicationContext, listening)
                },
                registerToggleLive = { callback ->
                    onToggleLiveFromWidget = callback
                },
                registerWidgetClosed = { callback ->
                    onWidgetClosedCallback = callback
                },
                onStartAlwaysLive = {
                    alwaysLiveManager.enable()
                },
                onStopAlwaysLive = {
                    alwaysLiveManager.disable()
                },
                registerExpandAlwaysLive = { callback ->
                    onExpandAlwaysLiveCallback = callback
                },
                registerCloseAlwaysLive = { callback ->
                    onCloseAlwaysLiveCallback = callback
                },
                requestAllFilesPermission = {
                    requestAllFilesPermission()
                },
                allFilesAccessGranted = _allFilesAccessGranted.value,
                setupChecks = buildSetupChecks(),
                fileToolHandler = { name, args ->
                    fileToolExecutor.execute(name, args)
                },
                onDownloadLocalModel = { localProvider, onProgress, checkOnly ->
                    // IMPORTANT: this lambda is `suspend` — caller (JarvisViewModel)
                    // awaits its completion. We MUST NOT fire-and-forget via
                    // `lifecycleScope.launch { ... }`, or the caller will think
                    // the download is done immediately while ONNX is still
                    // streaming the file. Run inline, only `coroutineScope` so
                    // the progress collector runs in parallel and is cancelled
                    // when the download finishes.
                    val mgr = onnxManager ?: com.example.personalaibot.data.embedding
                        .AndroidLocalOnnxManager(applicationContext, localProvider)
                        .also { onnxManager = it }
                    if (checkOnly) {
                        mgr.initOrDownload()
                    } else {
                        kotlinx.coroutines.coroutineScope {
                            val progressJob = launch {
                                mgr.downloadProgress.collect { progress ->
                                    if (progress >= 0f) onProgress(progress)
                                }
                            }
                            try {
                                mgr.downloadModelFiles()
                            } finally {
                                progressJob.cancel()
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Widget long-press → toggle live
        if (intent.action == "com.example.personalaibot.TOGGLE_LIVE") {
            onToggleLiveFromWidget?.invoke()
        }
        if (intent.action == "com.example.personalaibot.WIDGET_CLOSED") {
            onWidgetClosedCallback?.invoke()
        }
        if (intent.action == "com.example.personalaibot.HOTWORD_WAKE") {
            wakeAndTurnScreenOn()
            setKeepScreenOn(true)
            onExpandAlwaysLiveCallback?.invoke()
        } else if (intent.action == "com.example.personalaibot.EXPAND_ALWAYS_LIVE") {
            wakeAndTurnScreenOn()
            setKeepScreenOn(true)
            onExpandAlwaysLiveCallback?.invoke()
        }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        runOnUiThread {
            if (keepOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun turnScreenOnTemporarily() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    fun clearScreenFlags() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            }
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    fun wakeAndTurnScreenOn() {
        turnScreenOnTemporarily()
    }

    fun expandAlwaysLive() {
        runOnUiThread {
            turnScreenOnTemporarily()
            setKeepScreenOn(true)
            alwaysLiveManager.enable()
            onExpandAlwaysLiveCallback?.invoke()
        }
    }

    fun closeAlwaysLive() {
        runOnUiThread {
            alwaysLiveManager.disable()
            onCloseAlwaysLiveCallback?.invoke()
            clearScreenFlags()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            isActivityResumed = false
        }
        alwaysLiveManager.destroy()
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            startJarvisService()
        }
    }

    private fun startJarvisService() {
        // Main Jarvis Service (Voice/Vision)
        val mainIntent = Intent(this, JarvisService::class.java)
        // Automation Engine Service (1-60 min Polling)
        val autoIntent = Intent(this, JarvisAutomationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(mainIntent)
            startForegroundService(autoIntent)
        } else {
            startService(mainIntent)
            startService(autoIntent)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            allFilesPermissionLauncher.launch(intent)
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    private fun canDrawOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
}