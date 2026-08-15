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

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager

    // Callback ส่งกลับไปที่ Compose เพื่อ toggle live mode จาก widget
    private var onToggleLiveFromWidget: (() -> Unit)? = null
    private var onWidgetClosedCallback: (() -> Unit)? = null

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
        )
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
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
        com.example.personalaibot.automation.AndroidContextHolder.appContext = applicationContext
        
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

        checkAndRequestPermissions()

        setContent {
            App(
                databaseDriverFactory = driverFactory,
                voiceManager = voiceManager,
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