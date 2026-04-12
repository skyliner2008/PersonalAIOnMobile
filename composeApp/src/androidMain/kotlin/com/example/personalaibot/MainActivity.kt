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
import androidx.core.content.ContextCompat
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.service.FloatingWidgetService
import com.example.personalaibot.service.JarvisService
import com.example.personalaibot.voice.VoiceManager
import com.example.personalaibot.tools.file.FileToolExecutor

import android.media.AudioManager
import android.content.Context
import android.os.Environment

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager

    // Callback ส่งกลับไปที่ Compose เพื่อ toggle live mode จาก widget
    private var onToggleLiveFromWidget: (() -> Unit)? = null
    private var onWidgetClosedCallback: (() -> Unit)? = null

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

    private fun updatePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            _allFilesAccessGranted.value = Environment.isExternalStorageManager()
        } else {
            // Android 10 and below use standard runtime permissions
            _allFilesAccessGranted.value = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
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
                fileToolHandler = { name, args ->
                    fileToolExecutor.execute(name, args)
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
        // Widget close button → disable everywhere
        if (intent.action == FloatingWidgetService.ACTION_WIDGET_CLOSED) {
            onWidgetClosedCallback?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startJarvisService()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    fun canDrawOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this)
        else true

    fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                allFilesPermissionLauncher.launch(intent)
            }
        }
    }
}
