package com.example.personalaibot.tools.device

import android.app.AlarmManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.personalaibot.MainActivity
import com.example.personalaibot.service.JarvisAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * DeviceControlExecutor — ชั้นประมวลผลสำหรับ AI ควบคุมมือถือทั้งเครื่อง
 *
 * แบ่ง 4 กลุ่มหลัก:
 *  1. Hardware Controls  — ไฟฉาย, เสียง, ความสว่าง, เพลง
 *  2. App Launcher       — เปิดแอป, นำทาง, อีเมล, ปฏิทิน, โทร, SMS, ปลุก
 *  3. Screen Interaction — อ่านจอ, คลิก, พิมพ์, เลื่อน, กดปุ่ม (ต้อง A11y)
 *  4. System Info        — แบตเตอรี่, WiFi, Bluetooth, แอปที่เปิดอยู่
 *
 * 2026-09-06 — Initial implementation for JARVIS Device Control
 */
class DeviceControlExecutor(private val context: Context) : DeviceControlHandler {

    companion object {
        private const val TAG = "DeviceControl"
    }

    override suspend fun execute(toolName: String, args: Map<String, String>): String {
        Log.d(TAG, "🔧 Execute tool: $toolName, args=$args")
        val result = try {
            when (toolName) {
                // ── Hardware Controls ──
                "device_flashlight"    -> executeFlashlight(args)
                "device_volume"        -> executeVolume(args)
                "device_brightness"    -> executeBrightness(args)
                "device_media_control" -> executeMediaControl(args)
                "device_always_live"   -> executeAlwaysLive(args)

                // ── App Launcher ──
                "device_open_app"      -> executeOpenApp(args)
                "device_navigate"      -> executeNavigate(args)
                "device_send_email"    -> executeSendEmail(args)
                "device_add_calendar"  -> executeAddCalendar(args)
                "device_make_call"     -> executeMakeCall(args)
                "device_send_sms"      -> executeSendSms(args)
                "device_set_alarm"     -> executeSetAlarm(args)
                "device_open_url"      -> executeOpenUrl(args)
                "device_search_web"    -> executeSearchWeb(args)

                // ── Screen Interaction (Accessibility) ──
                "device_read_screen"   -> executeReadScreen(args)
                "device_tap"           -> executeTap(args)
                "device_type_text"     -> executeTypeText(args)
                "device_scroll"        -> executeScroll(args)
                "device_press_button"  -> executePressButton(args)
                "device_get_app_info"  -> executeGetAppInfo(args)

                // ── System Info ──
                "device_battery_status"    -> executeBatteryStatus()
                "device_wifi_status"       -> executeWifiStatus()

                else -> "❌ ไม่พบ device tool: $toolName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName: ${e.message}", e)
            "❌ เกิดข้อผิดพลาด: ${e.message}"
        }
        Log.i(TAG, "✅ Tool result ($toolName): $result")
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ███ 1. HARDWARE CONTROLS ███
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeFlashlight(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "toggle"
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
            ?: return "❌ ไม่พบกล้องที่รองรับไฟฉาย"

        return try {
            when (action) {
                "on", "เปิด"  -> {
                    cameraManager.setTorchMode(cameraId, true)
                    "🔦 เปิดไฟฉายแล้ว"
                }
                "off", "ปิด" -> {
                    cameraManager.setTorchMode(cameraId, false)
                    "🔦 ปิดไฟฉายแล้ว"
                }
                "toggle", "สลับ" -> {
                    // Android ไม่มี API อ่านสถานะ torch โดยตรง
                    // ใช้ callback listener แทน — เบื้องต้นลองเปิด ถ้า error แปลว่าเปิดอยู่แล้ว
                    cameraManager.setTorchMode(cameraId, true)
                    "🔦 สลับไฟฉาย (เปิด)"
                }
                else -> "❌ action ที่รองรับ: on, off, toggle"
            }
        } catch (e: Exception) {
            "❌ ควบคุมไฟฉายไม่ได้: ${e.message}"
        }
    }

    private fun executeVolume(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: return "❌ ต้องระบุ action (up/down/mute/unmute/set)"
        val streamStr = args["stream"]?.lowercase()?.trim() ?: "media"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val stream = when (streamStr) {
            "media", "เพลง"      -> AudioManager.STREAM_MUSIC
            "ring", "เสียงเรียกเข้า"  -> AudioManager.STREAM_RING
            "notification", "แจ้งเตือน" -> AudioManager.STREAM_NOTIFICATION
            "alarm", "ปลุก"      -> AudioManager.STREAM_ALARM
            "system", "ระบบ"     -> AudioManager.STREAM_SYSTEM
            "call", "โทรศัพท์"    -> AudioManager.STREAM_VOICE_CALL
            else                  -> AudioManager.STREAM_MUSIC
        }

        val maxVol = audioManager.getStreamMaxVolume(stream)
        val currentVol = audioManager.getStreamVolume(stream)

        return when (action) {
            "up", "เพิ่ม" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                val newVol = audioManager.getStreamVolume(stream)
                "🔊 เพิ่มเสียง${streamName(streamStr)}: ${newVol}/${maxVol}"
            }
            "down", "ลด" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                val newVol = audioManager.getStreamVolume(stream)
                "🔉 ลดเสียง${streamName(streamStr)}: ${newVol}/${maxVol}"
            }
            "mute", "เงียบ", "ปิดเสียง" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                "🔇 ปิดเสียง${streamName(streamStr)}"
            }
            "unmute", "เปิดเสียง" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                "🔊 เปิดเสียง${streamName(streamStr)}"
            }
            "vibrate", "สั่น" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "📳 เปลี่ยนเป็นโหมดสั่น"
            }
            "normal", "ปกติ" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                "🔊 เปลี่ยนเป็นโหมดปกติ"
            }
            "silent", "ไม่มีเสียง" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                "🔇 เปลี่ยนเป็นโหมดเงียบ"
            }
            "set", "ตั้ง" -> {
                val level = args["level"]?.toIntOrNull()
                if (level != null) {
                    val targetVol = (level * maxVol / 100).coerceIn(0, maxVol)
                    audioManager.setStreamVolume(stream, targetVol, AudioManager.FLAG_SHOW_UI)
                    "🔊 ตั้งเสียง${streamName(streamStr)}ที่ ${level}% (${targetVol}/${maxVol})"
                } else {
                    "❌ ต้องระบุ level (0-100)"
                }
            }
            "max", "เต็ม" -> {
                audioManager.setStreamVolume(stream, maxVol, AudioManager.FLAG_SHOW_UI)
                "🔊 ตั้งเสียง${streamName(streamStr)}เต็ม: ${maxVol}/${maxVol}"
            }
            "status", "สถานะ" -> {
                "🔊 เสียง${streamName(streamStr)}: ${currentVol}/${maxVol} (${currentVol * 100 / maxVol}%)"
            }
            else -> "❌ action ที่รองรับ: up, down, mute, unmute, vibrate, silent, normal, set, max, status"
        }
    }

    private fun streamName(stream: String): String = when (stream) {
        "media", "เพลง" -> "มีเดีย"
        "ring", "เสียงเรียกเข้า" -> "เสียงเรียกเข้า"
        "notification", "แจ้งเตือน" -> "แจ้งเตือน"
        "alarm", "ปลุก" -> "ปลุก"
        else -> ""
    }

    private fun executeBrightness(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "set"
        return when (action) {
            "set", "ตั้ง" -> {
                val level = args["level"]?.toIntOrNull()
                    ?: return "❌ ต้องระบุ level (0-100)"
                try {
                    // ปิด auto-brightness ก่อน
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    val brightness = (level * 255 / 100).coerceIn(1, 255)
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        brightness
                    )
                    "🔆 ตั้งความสว่างที่ ${level}%"
                } catch (e: SecurityException) {
                    // ต้อง WRITE_SETTINGS permission
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "⚠️ ต้องอนุญาตให้ JARVIS แก้ไขการตั้งค่า — กำลังเปิดหน้าตั้งค่าให้"
                }
            }
            "auto", "อัตโนมัติ" -> {
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    )
                    "🔆 เปลี่ยนเป็นความสว่างอัตโนมัติ"
                } catch (e: SecurityException) {
                    "⚠️ ต้องอนุญาตให้ JARVIS แก้ไขการตั้งค่าก่อน"
                }
            }
            else -> "❌ action ที่รองรับ: set (+ level), auto"
        }
    }

    private fun executeMediaControl(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "toggle"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return when (action) {
            "play", "เล่น"   -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                "▶️ เล่นเพลง"
            }
            "pause", "หยุด", "พัก" -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                "⏸️ หยุดเพลง"
            }
            "toggle", "play_pause", "สลับ" -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "⏯️ สลับเล่น/หยุดเพลง"
            }
            "next", "ถัดไป", "ข้าม" -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                "⏭️ ข้ามไปเพลงถัดไป"
            }
            "previous", "ก่อนหน้า", "ย้อน" -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "⏮️ ย้อนกลับเพลงก่อนหน้า"
            }
            "stop", "หยุดทั้งหมด" -> {
                dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_STOP)
                "⏹️ หยุดเพลงทั้งหมด"
            }
            else -> "❌ action ที่รองรับ: play, pause, toggle, next, previous, stop"
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    private fun executeAlwaysLive(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "on"
        return when (action) {
            "on", "เปิด", "start", "enable" -> {
                val mgr = com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()
                mgr?.wakeScreen()
                mgr?.enable()
                MainActivity.instance?.expandAlwaysLive()
                "🤖 เปิดโหมด Always AI Live (โหมดควบคุม) เรียบร้อยแล้วค่ะ พร้อมรับคำสั่งตลอดเวลา"
            }
            "off", "ปิด", "stop", "disable" -> {
                MainActivity.instance?.closeAlwaysLive()
                    ?: com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.disable()
                "🤖 ปิดโหมด Always AI Live (โหมดควบคุม) เรียบร้อยแล้วค่ะ"
            }
            "toggle", "สลับ" -> {
                val current = com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.state?.value
                if (current == com.example.personalaibot.service.AlwaysLiveManager.AlwaysLiveState.FULL_SCREEN ||
                    current == com.example.personalaibot.service.AlwaysLiveManager.AlwaysLiveState.MINI_FLOATING) {
                    MainActivity.instance?.closeAlwaysLive()
                        ?: com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.disable()
                    "🤖 ปิดโหมด Always AI Live เรียบร้อยแล้วค่ะ"
                } else {
                    MainActivity.instance?.expandAlwaysLive()
                        ?: com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.enable()
                    "🤖 เปิดโหมด Always AI Live เรียบร้อยแล้วค่ะ"
                }
            }
            else -> "❌ action ที่รองรับ: on (เปิด), off (ปิด), toggle (สลับ)"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ███ 2. APP LAUNCHER ███
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeOpenApp(args: Map<String, String>): String {
        val appName = args["app_name"]?.trim()
        val packageName = args["package_name"]?.trim()

        // ลองเปิดด้วย package name ก่อน
        if (!packageName.isNullOrBlank()) {
            return launchByPackage(packageName)
        }

        if (appName.isNullOrBlank()) return "❌ ต้องระบุ app_name หรือ package_name"

        // แปลงชื่อแอปเป็น package (ค้นหาจาก installed apps)
        val resolvedPackage = resolveAppPackage(appName)
        if (resolvedPackage != null) {
            return launchByPackage(resolvedPackage)
        }

        return "❌ ไม่พบแอป '$appName' ในเครื่อง"
    }

    private fun launchByPackage(pkg: String): String {
        Log.d(TAG, "launchByPackage: attempting to launch '$pkg'")
        // 1. Try standard getLaunchIntentForPackage
        var intent = context.packageManager.getLaunchIntentForPackage(pkg)

        // 2. Fallback: Query by package with ACTION_MAIN + CATEGORY_LAUNCHER
        if (intent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
            val matches = context.packageManager.queryIntentActivities(mainIntent, 0)
            if (matches.isNotEmpty()) {
                val activityInfo = matches[0].activityInfo
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = android.content.ComponentName(activityInfo.packageName, activityInfo.name)
                }
                Log.d(TAG, "launchByPackage: resolved via queryIntentActivities -> ${activityInfo.name}")
            }
        }

        // 3. Fallback: Domain-specific intents if package is Google Maps, Gmail, YouTube, etc.
        if (intent == null) {
            intent = when (pkg) {
                "com.google.android.apps.maps" -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                }
                "com.google.android.gm" -> {
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                    }
                }
                "com.google.android.youtube" -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                }
                "com.android.chrome" -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                }
                else -> null
            }
            if (intent != null) {
                Log.d(TAG, "launchByPackage: resolved via domain fallback intent for $pkg")
            }
        }

        return if (intent != null) {
            try {
                com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()
                com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.minimize()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                context.startActivity(intent)
                Log.i(TAG, "launchByPackage: successfully launched '$pkg'")
                "📱 เปิดแอป ${getAppLabel(pkg)} แล้ว"
            } catch (e: Exception) {
                Log.e(TAG, "launchByPackage: failed to startActivity for '$pkg'", e)
                "❌ ไม่สามารถเริ่มแอป $pkg ได้: ${e.message}"
            }
        } else {
            Log.e(TAG, "launchByPackage: cannot resolve launch intent for '$pkg'")
            "❌ ไม่สามารถเปิดแอป $pkg ได้ (ไม่พบในเครื่องหรือไม่มี Launcher Activity)"
        }
    }

    private fun resolveAppPackage(appName: String): String? {
        val lowerName = appName.lowercase()
        // ชื่อแอปยอดนิยม → package mapping
        val knownApps = mapOf(
            "google maps" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "แผนที่" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "อีเมล" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "youtube" to "com.google.android.youtube",
            "ยูทูป" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "โครม" to "com.android.chrome",
            "camera" to "com.android.camera",
            "กล้อง" to "com.android.camera",
            "calendar" to "com.google.android.calendar",
            "ปฏิทิน" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "นาฬิกา" to "com.google.android.deskclock",
            "settings" to "com.android.settings",
            "ตั้งค่า" to "com.android.settings",
            "phone" to "com.google.android.dialer",
            "โทรศัพท์" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "รายชื่อ" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "ข้อความ" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "photos" to "com.google.android.apps.photos",
            "รูปภาพ" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "line" to "jp.naver.line.android",
            "ไลน์" to "jp.naver.line.android",
            "facebook" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "instagram" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "discord" to "com.discord",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.google.android.calculator",
            "เครื่องคิดเลข" to "com.google.android.calculator",
            "files" to "com.google.android.documentsui",
            "ไฟล์" to "com.google.android.documentsui",
            "notes" to "com.google.android.keep",
            "keep" to "com.google.android.keep",
            "google keep" to "com.google.android.keep",
            "โน้ต" to "com.google.android.keep",
            "translate" to "com.google.android.apps.translate",
            "แปลภาษา" to "com.google.android.apps.translate",
        )

        // ตรงจาก mapping
        knownApps[lowerName]?.let { return it }

        // ค้นหาจากชื่อแอปที่ติดตั้ง
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == lowerName || label.contains(lowerName)) {
                return app.packageName
            }
        }
        return null
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun executeNavigate(args: Map<String, String>): String {
        val destination = args["destination"]?.trim()
            ?: return "❌ ต้องระบุ destination (จุดหมายหรือสถานที่)"
        val action = args["action"]?.lowercase()?.trim() ?: "view" // view=ดูพิกัด/ค้นหา, navigate=นำทาง
        val mode = args["mode"]?.lowercase()?.trim() ?: "d" // d=driving, w=walking, b=bicycling, t=transit

        val modeParam = when (mode) {
            "drive", "driving", "ขับรถ", "d" -> "d"
            "walk", "walking", "เดิน", "w" -> "w"
            "bike", "bicycling", "จักรยาน", "b" -> "b"
            "transit", "ขนส่ง", "t" -> "t"
            else -> "d"
        }

        val uri = if (action == "navigate") {
            Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$modeParam")
        } else {
            // View location or search: แสดงพิกัด/ข้อมูลสถานที่บนแผนที่ ไม่คำนวณเส้นทางขับรถ (ไม่ error ข้ามประเทศ)
            Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()
            com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.minimize()
            context.startActivity(intent)
            if (action == "navigate") {
                val modeText = when (modeParam) {
                    "d" -> "ขับรถ"
                    "w" -> "เดินเท้า"
                    "b" -> "จักรยาน"
                    "t" -> "ขนส่งสาธารณะ"
                    else -> ""
                }
                "🗺️ เปิด Google Maps นำทางไป \"$destination\" โหมด$modeText"
            } else {
                "🗺️ เปิด Google Maps ดูพิกัด \"$destination\" เรียบร้อยแล้วค่ะ"
            }
        } catch (e: Exception) {
            // Fallback: เปิดผ่าน Web Maps หากไม่มี Google Maps app
            try {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                "🗺️ เปิดแผนที่ดู \"$destination\" แล้วค่ะ"
            } catch (err: Exception) {
                "❌ เปิด Google Maps ไม่ได้: ${e.message}"
            }
        }
    }

    private fun executeSendEmail(args: Map<String, String>): String {
        val to = args["to"]?.trim() ?: ""
        val subject = args["subject"]?.trim() ?: ""
        val body = args["body"]?.trim() ?: ""

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(to)}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "📧 เปิดอีเมลร่างถึง $to แล้ว (Subject: $subject)"
        } catch (e: Exception) {
            "❌ เปิดอีเมลไม่ได้: ${e.message}"
        }
    }

    private fun executeAddCalendar(args: Map<String, String>): String {
        val title = args["title"]?.trim() ?: return "❌ ต้องระบุ title (หัวข้อนัด)"
        val description = args["description"]?.trim() ?: ""
        val location = args["location"]?.trim() ?: ""
        // ผู้ใช้ระบุเวลาเป็น epoch ms หรือให้ AI แปลงมา
        val beginTime = args["begin_time"]?.toLongOrNull() ?: System.currentTimeMillis() + 3600_000L
        val endTime = args["end_time"]?.toLongOrNull() ?: (beginTime + 3600_000L)

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "📅 เปิดปฏิทินเพิ่มนัด \"$title\" แล้ว"
        } catch (e: Exception) {
            "❌ เปิดปฏิทินไม่ได้: ${e.message}"
        }
    }

    private fun executeMakeCall(args: Map<String, String>): String {
        val number = args["number"]?.trim() ?: return "❌ ต้องระบุ number (เบอร์โทร)"

        // Intent-based: เปิดแอปโทรแล้วผู้ใช้กดโทรเอง (ปลอดภัย)
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "📞 เปิดแอปโทรศัพท์ กดเบอร์ $number ให้แล้ว — กดโทรได้เลย"
        } catch (e: Exception) {
            "❌ เปิดแอปโทรศัพท์ไม่ได้: ${e.message}"
        }
    }

    private fun executeSendSms(args: Map<String, String>): String {
        val number = args["number"]?.trim() ?: return "❌ ต้องระบุ number"
        val message = args["message"]?.trim() ?: ""

        // Intent-based: เปิดแอป SMS แล้วผู้ใช้กดส่งเอง (ปลอดภัย)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(number)}")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "💬 เปิดแอป SMS ร่างข้อความถึง $number แล้ว"
        } catch (e: Exception) {
            "❌ เปิดแอป SMS ไม่ได้: ${e.message}"
        }
    }

    private fun executeSetAlarm(args: Map<String, String>): String {
        val hour = args["hour"]?.toIntOrNull()
        val minute = args["minute"]?.toIntOrNull() ?: 0
        val message = args["message"]?.trim() ?: "JARVIS Alarm"

        if (hour == null) return "❌ ต้องระบุ hour (ชั่วโมง 0-23)"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true) // ตั้งเลยไม่ต้องเปิด UI
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "⏰ ตั้งนาฬิกาปลุกเวลา ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} แล้ว ($message)"
        } catch (e: Exception) {
            "❌ ตั้งนาฬิกาปลุกไม่ได้: ${e.message}"
        }
    }

    private fun executeOpenUrl(args: Map<String, String>): String {
        val url = args["url"]?.trim() ?: return "❌ ต้องระบุ url"
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "🌐 เปิด URL: $fullUrl"
        } catch (e: Exception) {
            "❌ เปิด URL ไม่ได้: ${e.message}"
        }
    }

    private fun executeSearchWeb(args: Map<String, String>): String {
        val query = args["query"]?.trim() ?: return "❌ ต้องระบุ query (คำค้นหา)"

        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            "🔍 ค้นหา \"$query\" บนเว็บ"
        } catch (e: Exception) {
            // Fallback: เปิด Chrome โดยตรง
            val chromeIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(chromeIntent)
            "🔍 ค้นหา \"$query\" ด้วย Google"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ███ 3. SCREEN INTERACTION (Accessibility) ███
    // ═══════════════════════════════════════════════════════════════════════

    private fun requireA11y(): JarvisAccessibilityService? {
        val svc = JarvisAccessibilityService.instance
        if (svc == null) {
            Log.w(TAG, "AccessibilityService not enabled")
        }
        return svc
    }

    private fun executeReadScreen(args: Map<String, String>): String {
        val svc = requireA11y()
            ?: return "⚠️ ต้องเปิด Accessibility Service ก่อน — ไปที่ ตั้งค่า > การเข้าถึง > JARVIS แล้วเปิด"
        return svc.readScreenAsText()
    }

    private suspend fun executeTap(args: Map<String, String>): String {
        val svc = requireA11y()
            ?: return "⚠️ ต้องเปิด Accessibility Service ก่อน"

        // วิธี 1: แตะด้วยข้อความ
        val text = args["text"]?.trim()
        if (!text.isNullOrBlank()) {
            val result = svc.clickByText(text)
            return if (result) "👆 แตะ \"$text\" สำเร็จ" else "❌ ไม่พบ \"$text\" บนหน้าจอ"
        }

        // วิธี 2: แตะด้วย view ID
        val viewId = args["view_id"]?.trim()
        if (!viewId.isNullOrBlank()) {
            val result = svc.clickById(viewId)
            return if (result) "👆 แตะ id=$viewId สำเร็จ" else "❌ ไม่พบ id=$viewId บนหน้าจอ"
        }

        // วิธี 3: แตะด้วยพิกัด (x, y)
        val x = args["x"]?.toFloatOrNull()
        val y = args["y"]?.toFloatOrNull()
        if (x != null && y != null) {
            val result = suspendCancellableCoroutine<Boolean> { cont ->
                svc.tapAtPosition(x, y) { success -> cont.resume(success) }
            }
            return if (result) "👆 แตะตำแหน่ง ($x, $y) สำเร็จ" else "❌ แตะตำแหน่ง ($x, $y) ไม่สำเร็จ"
        }

        return "❌ ต้องระบุ text, view_id, หรือ x+y"
    }

    private fun executeTypeText(args: Map<String, String>): String {
        val svc = requireA11y()
            ?: return "⚠️ ต้องเปิด Accessibility Service ก่อน"
        val text = args["text"]?.trim()
            ?: return "❌ ต้องระบุ text (ข้อความที่จะพิมพ์)"
        val clear = args["clear"]?.lowercase() in listOf("true", "1", "yes", "ล้าง")

        val result = if (clear) svc.clearAndType(text) else svc.typeText(text)
        return if (result) {
            "⌨️ พิมพ์ข้อความ \"${text.take(50)}${if (text.length > 50) "..." else ""}\" สำเร็จ"
        } else {
            "❌ ไม่สามารถพิมพ์ได้ — ไม่พบช่อง input ที่แก้ไขได้"
        }
    }

    private suspend fun executeScroll(args: Map<String, String>): String {
        val svc = requireA11y()
            ?: return "⚠️ ต้องเปิด Accessibility Service ก่อน"
        val direction = args["direction"]?.lowercase()?.trim() ?: "down"

        val result = suspendCancellableCoroutine<Boolean> { cont ->
            when (direction) {
                "down", "ลง" -> svc.scrollDown { cont.resume(it) }
                "up", "ขึ้น" -> svc.scrollUp { cont.resume(it) }
                else -> cont.resume(false)
            }
        }

        return if (result) {
            "📜 เลื่อนหน้าจอ${if (direction in listOf("down", "ลง")) "ลง" else "ขึ้น"}สำเร็จ"
        } else {
            "❌ เลื่อนหน้าจอไม่สำเร็จ"
        }
    }

    private fun executePressButton(args: Map<String, String>): String {
        val svc = requireA11y()
            ?: return "⚠️ ต้องเปิด Accessibility Service ก่อน"
        val button = args["button"]?.lowercase()?.trim()
            ?: return "❌ ต้องระบุ button (back/home/recent/notifications/quick_settings/screenshot/lock)"

        val result = when (button) {
            "back", "กลับ", "ย้อน"                          -> svc.pressBack()
            "home", "หน้าหลัก", "โฮม"                       -> svc.pressHome()
            "recent", "recents", "แอปล่าสุด", "ล่าสุด"    -> svc.openRecents()
            "notifications", "แจ้งเตือน", "notification"    -> svc.openNotifications()
            "quick_settings", "ตั้งค่าด่วน", "quick"        -> svc.openQuickSettings()
            "screenshot", "จับภาพหน้าจอ", "สกรีนช็อต"      -> svc.takeScreenshot()
            "wake", "เปิด", "เปิดจอ", "เปิดหน้าจอ", "ตื่น", "ปลุก" -> {
                val mgr = com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()
                mgr?.wakeScreen()
                mgr?.enable()
                MainActivity.instance?.expandAlwaysLive()
                return "👁️ เปิดหน้าจอเรียบร้อยแล้วค่ะ"
            }
            "lock", "ล็อค", "ล็อคหน้าจอ", "sleep", "พัก", "พักหน้าจอ", "ปิดหน้าจอ", "พักผ่อน", "นอน", "สั่งให้พัก" -> {
                com.example.personalaibot.MainActivity.instance?.setKeepScreenOn(false)
                com.example.personalaibot.service.AlwaysLiveManager.getInstanceOrNull()?.onScreenOff()
                val locked = svc.lockScreen()
                if (locked) {
                    return "😴 พักหน้าจอเรียบร้อยแล้วค่ะ สั่งเรียกจาวิสได้ตลอดเวลานะคะ"
                }
                locked
            }
            else -> return "❌ ปุ่มที่รองรับ: back, home, recent, notifications, quick_settings, screenshot, lock, wake"
        }

        return if (result) {
            "✅ กดปุ่ม $button สำเร็จ"
        } else {
            "❌ กดปุ่ม $button ไม่สำเร็จ (อาจต้อง API 28+)"
        }
    }

    private fun executeGetAppInfo(args: Map<String, String>): String {
        val svc = JarvisAccessibilityService.instance
        return if (svc != null) {
            svc.getAppContext()
        } else {
            "⚠️ AccessibilityService ปิดอยู่ — ไม่สามารถตรวจสอบแอปที่เปิดอยู่ได้"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ███ 4. SYSTEM INFO ███
    // ═══════════════════════════════════════════════════════════════════════

    private fun executeBatteryStatus(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bm.isCharging
        } else {
            false
        }
        val chargingStr = if (isCharging) "กำลังชาร์จ ⚡" else "ไม่ได้ชาร์จ"
        return "🔋 แบตเตอรี่: ${level}% ($chargingStr)"
    }

    @Suppress("DEPRECATION")
    private fun executeWifiStatus(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return "⚠️ ไม่สามารถเข้าถึง WiFi Manager"
        val isEnabled = wifiManager.isWifiEnabled
        val info = wifiManager.connectionInfo
        val ssid = info?.ssid?.replace("\"", "") ?: "N/A"
        val rssi = info?.rssi ?: 0
        val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)

        return buildString {
            appendLine("📶 WiFi: ${if (isEnabled) "เปิด" else "ปิด"}")
            if (isEnabled && ssid != "<unknown ssid>" && ssid != "N/A") {
                appendLine("   เครือข่าย: $ssid")
                appendLine("   ความแรงสัญญาณ: ${"▓".repeat(signalLevel)}${"░".repeat(4 - signalLevel)} ($rssi dBm)")
            }
        }.trim()
    }
}
