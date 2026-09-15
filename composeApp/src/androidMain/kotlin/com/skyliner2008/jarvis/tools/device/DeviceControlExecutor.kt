package com.skyliner2008.jarvis.tools.device

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
import com.skyliner2008.jarvis.MainActivity
import com.skyliner2008.jarvis.service.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val locationProvider = com.skyliner2008.jarvis.location.LocationProvider(context)
    private val mediaInfoProvider = com.skyliner2008.jarvis.media.MediaInfoProvider(context)

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
                "device_avatar_emotion" -> executeAvatarEmotion(args)
                "device_custom_prop"   -> executeCustomProp(args)
                "device_pet_care"      -> executePetCare(args)

                // ── Smart Notifications (Driving Mode) ──
                "device_notification_read"  -> executeNotificationRead(args)
                "device_notification_reply" -> executeNotificationReply(args)

                // ── Location & GPS & Weather ──
                "device_location"           -> executeLocation(args)
                "device_weather"            -> executeWeather(args)

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

        // 1. Now Playing check
        if (action == "now_playing" || action == "status" || action == "สถานะ" || action == "เพลงอะไร") {
            val info = mediaInfoProvider.getNowPlaying()
            return if (info != null) {
                val state = if (info.isPlaying) "กำลังเล่น ▶️" else "หยุดชั่วคราว ⏸️"
                "🎵 เพลง: ${info.title}\n👤 ศิลปิน: ${if (info.artist.isNotBlank()) info.artist else "ไม่ระบุ"}\n💿 อัลบั้ม: ${if (info.album.isNotBlank()) info.album else "ไม่ระบุ"}\n📱 เล่นผ่าน: ${info.appName} ($state)"
            } else {
                "📭 ไม่พบแอปเพลงที่กำลังเล่นอยู่ในขณะนี้ค่ะ"
            }
        }

        // 2. Search & Play (YouTube / YouTube Music / Spotify)
        if (action == "search_play" || action == "search" || action == "ค้นหา") {
            val query = args["query"] ?: args["destination"] ?: ""
            if (query.isBlank()) {
                return "❌ กรุณาระบุชื่อเพลงหรือคำค้นหาที่ต้องการเล่น"
            }
            val app = args["app"]
            return mediaInfoProvider.searchAndPlay(query, app)
        }

        // 3. Media control: Try targeted session first, fallback to hardware key
        val (handled, sessionMsg) = mediaInfoProvider.controlPlayback(action)
        if (handled) {
            return sessionMsg
        }

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
            else -> "❌ action ที่รองรับ: play, pause, toggle, next, previous, stop, now_playing, search_play"
        }
    }

    private fun executeNotificationRead(args: Map<String, String>): String {
        val appFilter = args["app_filter"] ?: args["app"]
        val count = args["count"]?.toIntOrNull() ?: 5
        return com.skyliner2008.jarvis.notification.NotificationBridge.readRecent(appFilter, count)
    }

    private fun executeNotificationReply(args: Map<String, String>): String {
        val message = args["message"] ?: args["text"] ?: ""
        val key = args["notification_key"] ?: args["key"]
        return com.skyliner2008.jarvis.notification.NotificationBridge.reply(message, key)
    }

    private suspend fun executeLocation(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "get_current"
        if (action == "status") {
            val hasPerm = locationProvider.hasPermission()
            val isGps = locationProvider.isGpsEnabled()
            return "🛰️ สถานะ GPS: สิทธิ์เข้าถึง = ${if (hasPerm) "อนุญาตแล้ว ✅" else "ยังไม่อนุญาต ❌"}, เปิด GPS = ${if (isGps) "เปิดอยู่ ✅" else "ปิดอยู่ ❌"}"
        }
        val loc = locationProvider.getCurrentLocation()
            ?: return if (!locationProvider.hasPermission()) {
                "⚠️ ยังไม่ได้รับสิทธิ์เข้าถึงพิกัด GPS กรุณาเปิดสิทธิ์ 'ตำแหน่งที่ตั้ง' ในหน้าตั้งค่าก่อนนะคะ"
            } else {
                "❌ ไม่สามารถดึงพิกัด GPS ในขณะนี้ได้ กรุณาตรวจสอบว่าเปิด GPS แล้วหรือยังค่ะ"
            }
        val query = args["query"]?.trim()
        val baseSummary = loc.formatSummary()
        if (!query.isNullOrBlank()) {
            val locationKeyword = loc.address ?: "Lat ${loc.latitude}, Lng ${loc.longitude}"
            return "NEARBY_SEARCH_REQUEST::query=$query::location=$locationKeyword::lat=${loc.latitude}::lng=${loc.longitude}::summary=$baseSummary"
        }
        return baseSummary
    }

    private suspend fun executeWeather(args: Map<String, String>): String {
        // 1. Resolve Location & Coordinates
        var lat = args["latitude"]?.toDoubleOrNull()
        var lng = args["longitude"]?.toDoubleOrNull()
        val queryLoc = args["location"]?.trim()
        var locationName: String? = queryLoc

        if ((lat == null || lng == null) && !queryLoc.isNullOrBlank()) {
            // Try resolving via Android Geocoder
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale("th", "TH"))
                @Suppress("DEPRECATION")
                val addresses = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(queryLoc, 1)
                }
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    lat = addr.latitude
                    lng = addr.longitude
                    locationName = addr.locality ?: addr.adminArea ?: queryLoc
                    Log.d(TAG, "executeWeather: Resolved '$queryLoc' via Geocoder -> ($lat, $lng, $locationName)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "executeWeather: Geocoder lookup failed: ${e.message}")
            }
        }

        // If still null, query current GPS location
        if (lat == null || lng == null) {
            val curLoc = locationProvider.getCurrentLocation()
            if (curLoc != null) {
                lat = curLoc.latitude
                lng = curLoc.longitude
                locationName = curLoc.address ?: "พิกัดปัจจุบัน (Lat %.3f, Lng %.3f)".format(lat, lng)
            } else {
                // Fallback default: Bangkok, Thailand
                lat = 13.7563
                lng = 100.5018
                locationName = if (!queryLoc.isNullOrBlank()) queryLoc else "กรุงเทพมหานคร (พิกัดเริ่มต้น)"
            }
        }

        // 2. Fetch Open-Meteo Weather Forecast (Free, No Key Needed, Global Coverage)
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&timezone=auto"
                val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "JARVIS-Android/1.0")

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext "❌ ไม่สามารถดึงข้อมูลสภาพอากาศได้ (HTTP $responseCode)"
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootJson = Json.parseToJsonElement(responseText).jsonObject
                val current = rootJson["current"]?.jsonObject
                val daily = rootJson["daily"]?.jsonObject

                if (current == null) {
                    return@withContext "❌ ข้อมูลสภาพอากาศไม่สมบูรณ์"
                }

                val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val apparentTemp = current["apparent_temperature"]?.jsonPrimitive?.doubleOrNull ?: temp
                val humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: 0
                val precipitation = current["precipitation"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val weatherCode = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
                val windSpeed = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                val maxTemp = daily?.get("temperature_2m_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull ?: temp
                val minTemp = daily?.get("temperature_2m_min")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull ?: temp
                val rainProb = daily?.get("precipitation_probability_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull ?: 0

                // WMO Weather Interpretation Codes
                val weatherDesc: String
                val emoji: String
                val isRain: Boolean
                val isThunder: Boolean
                val isSunny: Boolean

                when (weatherCode) {
                    0 -> { weatherDesc = "ท้องฟ้าแจ่มใส แดดออก"; emoji = "☀️"; isRain = false; isThunder = false; isSunny = true }
                    1, 2, 3 -> { weatherDesc = if (weatherCode == 1) "ท้องฟ้าโปร่ง มีเมฆเล็กน้อย" else "มีเมฆเป็นส่วนมาก"; emoji = "⛅"; isRain = false; isThunder = false; isSunny = false }
                    45, 48 -> { weatherDesc = "มีหมอกลง ทัศนวิสัยลดลง"; emoji = "🌫️"; isRain = false; isThunder = false; isSunny = false }
                    51, 53, 55 -> { weatherDesc = "มีฝนละออง/ฝนปรอยๆ"; emoji = "🌦️"; isRain = true; isThunder = false; isSunny = false }
                    61, 63, 65 -> { weatherDesc = if (weatherCode == 65) "มีฝนตกหนัก" else "มีฝนตก"; emoji = "🌧️"; isRain = true; isThunder = false; isSunny = false }
                    71, 73, 75 -> { weatherDesc = "มีหิมะตก"; emoji = "❄️"; isRain = false; isThunder = false; isSunny = false }
                    80, 81, 82 -> { weatherDesc = "ฝนฟ้าคะนองสั้นๆ/ฝนซู่"; emoji = "🌧️"; isRain = true; isThunder = false; isSunny = false }
                    95, 96, 99 -> { weatherDesc = "พายุฝนฟ้าคะนองและลมกระโชกแรง"; emoji = "⛈️"; isRain = true; isThunder = true; isSunny = false }
                    else -> { weatherDesc = "สภาพอากาศปกติ"; emoji = "🌤️"; isRain = false; isThunder = false; isSunny = false }
                }

                // Trigger contextual props and background on Pet/Avatar
                val mainActivity = MainActivity.instance
                if (isRain || isThunder) {
                    val cmd = "SPEAKING|background=rainy|props=umbrella,cloud,rain_drops"
                    mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
                    com.skyliner2008.jarvis.sound.RobotSoundEngine.play(com.skyliner2008.jarvis.sound.RobotSoundEngine.SoundType.SURPRISE)
                } else if (isSunny || temp >= 33.0) {
                    val cmd = "SPEAKING|background=sunny|props=sun,sunglasses"
                    mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
                    com.skyliner2008.jarvis.sound.RobotSoundEngine.play(com.skyliner2008.jarvis.sound.RobotSoundEngine.SoundType.CHIRP_HAPPY)
                } else {
                    com.skyliner2008.jarvis.sound.RobotSoundEngine.play(com.skyliner2008.jarvis.sound.RobotSoundEngine.SoundType.SPARKLE)
                }

                buildString {
                    appendLine("$emoji สภาพอากาศ: $locationName")
                    appendLine("🌡️ อุณหภูมิ: ${temp.toInt()}°C (รู้สึกเหมือน ${apparentTemp.toInt()}°C)")
                    appendLine("☁️ ลักษณะอากาศ: $weatherDesc")
                    appendLine("💧 ความชื้นสัมพัทธ์: $humidity%")
                    if (rainProb > 0 || precipitation > 0.0) {
                        appendLine("☔ โอกาสฝนตก: $rainProb% (ปริมาณน้ำฝน: ${precipitation} มม.)")
                    }
                    appendLine("📈 สูงสุด/ต่ำสุดวันนี้: ${maxTemp.toInt()}°C / ${minTemp.toInt()}°C")
                    appendLine("💨 ความเร็วลม: ${windSpeed.toInt()} กม./ชม.")
                    if (isRain || rainProb >= 50) {
                        appendLine("💡 ข้อแนะนำ: วันนี้มีแนวโน้มฝนตก อย่าลืมพกร่มติดตัวด้วยนะคะ!")
                    } else if (temp >= 35.0) {
                        appendLine("💡 ข้อแนะนำ: อากาศค่อนข้างร้อน ดื่มน้ำเยอะๆ และหลีกเลี่ยงแดดจัดนะคะ!")
                    }
                }.trim()
            } catch (e: Exception) {
                Log.e(TAG, "executeWeather error: ${e.message}", e)
                "❌ ไม่สามารถตรวจสอบสภาพอากาศได้ในขณะนี้: ${e.message}"
            }
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
        val rawAction = args["action"]?.lowercase()?.trim() ?: "on"
        val mode = args["mode"]?.lowercase()?.trim()
        val isDriveMode = mode in setOf("drive", "car", "ขับขี่", "รถยนต์") || rawAction in setOf("drive", "car")
        val isPetMode = mode in setOf("pet", "animal", "สัตว์เลี้ยง", "แก้เบื่อ", "desk_pet", "toy") || rawAction in setOf("pet", "สัตว์เลี้ยง")

        val isExplicitOff = rawAction in setOf("off", "ปิด", "stop", "disable", "exit", "ออก", "close")

        Log.i(TAG, "🐾 executeAlwaysLive: rawAction=$rawAction, mode=$mode, isPetMode=$isPetMode, isDriveMode=$isDriveMode, isExplicitOff=$isExplicitOff")

        if (isExplicitOff) {
            MainActivity.instance?.closeAlwaysLive()
                ?: com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.disable()
            return if (isPetMode) {
                "🐾 ปิดโหมดสัตว์เลี้ยงเรียบร้อยแล้วค่ะ ไว้มาเล่นกับน้องใหม่น้าา บ๊ายบายค่ะ"
            } else if (isDriveMode) {
                "🚗 ปิดโหมดขับขี่เรียบร้อยแล้วค่ะ"
            } else {
                "🤖 ปิดโหมด Always AI Live (โหมดควบคุม) เรียบร้อยแล้วค่ะ"
            }
        }

        // Pure toggle ONLY when no specific mode or profile is requested
        if (!isPetMode && !isDriveMode && (mode == null || mode == "control") && rawAction in setOf("toggle", "สลับ")) {
            val current = com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.state?.value
            return if (current == com.skyliner2008.jarvis.service.AlwaysLiveManager.AlwaysLiveState.FULL_SCREEN ||
                current == com.skyliner2008.jarvis.service.AlwaysLiveManager.AlwaysLiveState.MINI_FLOATING) {
                MainActivity.instance?.closeAlwaysLive()
                    ?: com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.disable()
                "🤖 ปิดโหมด Always AI Live เรียบร้อยแล้วค่ะ"
            } else {
                MainActivity.instance?.expandAlwaysLive(com.skyliner2008.jarvis.pet.AlwaysLiveProfile.CONTROL)
                    ?: com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.enable()
                "🤖 เปิดโหมด Always AI Live เรียบร้อยแล้วค่ะ"
            }
        }

        // All other actions (on, open, start, enable, switch, change, or any call specifying a mode like pet/drive)
        // MUST open or switch profile into Always Live FULL_SCREEN without disconnecting or closing.
        val mgr = com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()
        val profile = when {
            isPetMode -> com.skyliner2008.jarvis.pet.AlwaysLiveProfile.PET
            isDriveMode -> com.skyliner2008.jarvis.pet.AlwaysLiveProfile.DRIVE
            else -> com.skyliner2008.jarvis.pet.AlwaysLiveProfile.CONTROL
        }
        mgr?.setProfile(profile)
        mgr?.wakeScreen()
        mgr?.enable()
        MainActivity.instance?.expandAlwaysLive(profile)

        return if (isPetMode) {
            "🐾 เปิดโหมดสัตว์เลี้ยงตั้งโต๊ะ (Virtual Desk Pet) เรียบร้อยแล้วค่ะ! พร้อมเล่น ลูบหัว และอยู่เป็นเพื่อนแล้วน้า งุ้ยย ✨"
        } else if (isDriveMode) {
            "🚗 เปิดโหมดขับขี่ / โหมดรถยนต์ (โหมดควบคุม) เรียบร้อยแล้วค่ะ พร้อมดูแลและรับคำสั่งด้วยเสียงตลอดการเดินทางนะคะ"
        } else {
            "🤖 เปิดโหมด Always AI Live (โหมดควบคุม) เรียบร้อยแล้วค่ะ พร้อมรับคำสั่งตลอดเวลา"
        }
    }

    private suspend fun executeAvatarEmotion(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "demo"
        val rawEmotion = (args["emotion"] ?: args["name"])?.lowercase()?.trim()
        val mainActivity = MainActivity.instance

        // 1. ตรวจสอบการสั่งเจาะจงหน้าที่ 1 ถึง 50 (LOOI Robot Moodset Catalog)
        val targetPage = args["page"]?.toIntOrNull()
            ?: args["page_number"]?.toIntOrNull()
            ?: if (action in listOf("page", "หน้า", "หน้าที่")) rawEmotion?.toIntOrNull() else null
            ?: rawEmotion?.toIntOrNull()?.takeIf { it in 1..50 }

        if (targetPage != null && targetPage in 1..50) {
            mainActivity?.triggerTestEmotion("PAGE|$targetPage")
                ?: broadcastEmotionIntent("PAGE|$targetPage")
            val item = com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.findByPage(targetPage)
            return "📖 แสดง Moodset หน้าที่ $targetPage: ${item?.nameEn} (${item?.nameTh}) บนหน้าจอเรียบร้อยแล้วค่ะบอส! [แผ่นที่ ${item?.sheet}: ${item?.description}]"
        }

        return when (action) {
            "demo", "โชว์", "แสดงทั้งหมด", "all", "play_all", "หน้าทั้งหมด", "ทุกหน้า" -> {
                mainActivity?.triggerTestEmotion("ALL")
                    ?: broadcastEmotionIntent("ALL")
                "🎭 เริ่มเล่นแสดง Moodset ครบทั้งหมด 50 หน้า (แผ่นที่ 1: หน้า 1-20 และ แผ่นที่ 2: หน้า 21-50) วนตรวจหน้าจอแบบละ 4 วินาทีเรียบร้อยแล้วค่ะบอส!"
            }
            "reset", "clear", "ปกติ", "รีเซ็ต", "auto" -> {
                mainActivity?.triggerTestEmotion("RESET")
                    ?: broadcastEmotionIntent("RESET")
                "🎭 รีเซ็ตเรียบร้อยค่ะ Avatar กลับสู่โหมดตรวจจับอัตโนมัติตามธรรมชาติแล้วค่ะ"
            }
            "scene", "ฉาก", "set", "ตั้งค่า", "เปลี่ยน", "ทำหน้า" -> {
                val emotionKey = when {
                    rawEmotion.isNullOrBlank() -> "HAPPY"
                    rawEmotion.contains("happy") || rawEmotion.contains("ดีใจ") || rawEmotion.contains("ยิ้ม") -> "HAPPY"
                    rawEmotion.contains("excited") || rawEmotion.contains("ตื่นเต้น") -> "EXCITED"
                    rawEmotion.contains("love") || rawEmotion.contains("รัก") || rawEmotion.contains("หัวใจ") -> "LOVE"
                    rawEmotion.contains("angry") || rawEmotion.contains("โกรธ") || rawEmotion.contains("โมโห") -> "ANGRY"
                    rawEmotion.contains("sad") || rawEmotion.contains("เศร้า") || rawEmotion.contains("ร้องไห้") -> "SAD"
                    rawEmotion.contains("sleeping") || rawEmotion.contains("หลับ") || rawEmotion.contains("นอน") -> "SLEEPING"
                    rawEmotion.contains("listening") || rawEmotion.contains("ฟัง") -> "LISTENING"
                    rawEmotion.contains("thinking") || rawEmotion.contains("คิด") -> "THINKING"
                    rawEmotion.contains("speaking") || rawEmotion.contains("พูด") -> "SPEAKING"
                    rawEmotion.contains("idle") || rawEmotion.contains("พร้อม") -> "IDLE"
                    rawEmotion.contains("wink") || rawEmotion.contains("ขยิบ") -> "WINK"
                    rawEmotion.contains("confused") || rawEmotion.contains("งง") || rawEmotion.contains("สงสัย") -> "CONFUSED"
                    rawEmotion.contains("pout") || rawEmotion.contains("บูด") || rawEmotion.contains("หน้าบูด") -> "POUT"
                    rawEmotion.contains("dizzy") || rawEmotion.contains("เวียนหัว") -> "DIZZY"
                    else -> rawEmotion.uppercase()
                }

                // Build extended face state command with layer parameters
                val eyeStyle = args["eye_style"]?.lowercase()?.trim() ?: ""
                val background = args["background"]?.lowercase()?.trim() ?: ""
                val props = args["props"]?.lowercase()?.trim() ?: ""
                val gesture = args["gesture"]?.lowercase()?.trim() ?: ""
                val sceneName = args["scene"] ?: args["scene_name"]

                // ถ้ามีการระบุ scene ให้ดึงค่าจาก PetSceneEngine
                if (action == "scene" || action == "ฉาก" || !sceneName.isNullOrBlank()) {
                    val detectedProp = com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState.resolvePropType(props)
                        ?: com.skyliner2008.jarvis.pet.PetSceneEngine.detectSpecificPropFromText(props)
                        ?: sceneName?.let { com.skyliner2008.jarvis.pet.PetSceneEngine.detectSpecificPropFromText(it) }

                    val keyword = sceneName?.takeIf { it.isNotBlank() }
                        ?: props.takeIf { it.isNotBlank() }
                        ?: rawEmotion?.takeIf { it.isNotBlank() }
                        ?: "eating"
                    // the model often opens Pet mode and asks for a scene in the same round:
                    // give the pet screen a moment to start its controller
                    var activePet = com.skyliner2008.jarvis.pet.PetModeController.activeInstance
                    var waited = 0
                    while (activePet == null && waited < 3000) {
                        kotlinx.coroutines.delay(100)
                        waited += 100
                        activePet = com.skyliner2008.jarvis.pet.PetModeController.activeInstance
                    }
                    if (activePet != null) {
                        val played = activePet.playSceneByNameOrKeyword(keyword, detectedProp)
                        if (played != null) {
                            return "🎬 เริ่มเล่นฉาก '$played' แล้ว จะเล่นจนจบเองค่ะบอส (ไม่ต้องสั่งซ้ำ)"
                        }
                    }
                    val resolved = com.skyliner2008.jarvis.pet.PetSceneEngine.resolveFromKeyword(keyword, detectedProp)
                    if (resolved != null) {
                        val (sceneFace, spec) = resolved
                        val faceCmd = buildString {
                            append(sceneFace.emotion.name.uppercase())
                            val extras = mutableListOf<String>()
                            if (sceneFace.eyeStyleName.isNotBlank() && sceneFace.eyeStyleName != "default") extras.add("eye_style=${sceneFace.eyeStyleName}")
                            if (sceneFace.backgroundName.isNotBlank() && sceneFace.backgroundName != "default") extras.add("background=${sceneFace.backgroundName}")
                            if (sceneFace.propsRaw.isNotBlank()) extras.add("props=${sceneFace.propsRaw}")
                            if (sceneFace.gestureName.isNotBlank() && sceneFace.gestureName != "idle") extras.add("gesture=${sceneFace.gestureName}")
                            if (extras.isNotEmpty()) {
                                append("|")
                                append(extras.joinToString("|"))
                            }
                        }
                        mainActivity?.triggerTestEmotion(faceCmd) ?: broadcastEmotionIntent(faceCmd)
                        spec.sound.let { com.skyliner2008.jarvis.sound.RobotSoundPlayer.play(it) }
                        // (the missile barrage overlay only exists inside Pet mode, handled above)
                        return "🎬 เริ่มเล่นฉาก '${spec.nameTh}' เรียบร้อยแล้วค่ะบอส!"
                    }
                }

                // Encode all parameters into the emotion command string as JSON-like
                val faceCmd = buildString {
                    append(emotionKey)
                    val extras = mutableListOf<String>()
                    if (eyeStyle.isNotBlank()) extras.add("eye_style=$eyeStyle")
                    if (background.isNotBlank()) extras.add("background=$background")
                    if (props.isNotBlank()) extras.add("props=$props")
                    if (gesture.isNotBlank()) extras.add("gesture=$gesture")
                    if (extras.isNotEmpty()) {
                        append("|")
                        append(extras.joinToString("|"))
                    }
                }

                mainActivity?.triggerTestEmotion(faceCmd)
                    ?: broadcastEmotionIntent(faceCmd)

                val desc = buildString {
                    append("🎭 ปรับ Avatar: อารมณ์=$emotionKey")
                    if (eyeStyle.isNotBlank()) append(" ตา=$eyeStyle")
                    if (background.isNotBlank()) append(" ฉากหลัง=$background")
                    if (props.isNotBlank()) append(" Props=$props")
                    if (gesture.isNotBlank()) append(" ท่าทาง=$gesture")
                    append(" เรียบร้อยแล้วค่ะบอส!")
                }
                desc
            }
            else -> {
                if (!rawEmotion.isNullOrBlank()) {
                    mainActivity?.triggerTestEmotion(rawEmotion.uppercase())
                        ?: broadcastEmotionIntent(rawEmotion.uppercase())
                    "🎭 ปรับสีหน้า Avatar เป็นโหมด ${rawEmotion.uppercase()} เรียบร้อยแล้วค่ะบอส!"
                } else {
                    mainActivity?.triggerTestEmotion("DEMO")
                    "🎭 เริ่มโหมด Emotion Showcase Demo เรียบร้อยแล้วค่ะ!"
                }
            }
        }
    }

    private fun executeCustomProp(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "add"
        val name = args["name"]?.trim().orEmpty().ifBlank { "prop" }
        val svgPath = args["svg_path"]?.trim().orEmpty()
        val mainActivity = MainActivity.instance
        val pet = com.skyliner2008.jarvis.pet.PetModeController.activeInstance

        fun sendCustomPropCommand(extra: Map<String, String>) {
            val cmd = "CUSTOM_PROP|" + extra.entries.joinToString("|") { "${it.key}=${it.value.replace("|", " ")}" }
            if (pet != null) pet.updateRobotFace(cmd)
            else mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
        }

        return when (action) {
            "clear", "ล้าง", "ถอดหมด", "reset" -> {
                if (pet != null) {
                    pet.clearProps()
                } else {
                    val cmd = "IDLE|props=|background=default"
                    mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
                }
                "✨ ถอดอุปกรณ์เสริมทั้งหมดเรียบร้อยแล้วค่ะ"
            }
            "remove", "ถอด", "ลบ", "delete" -> {
                // remove only the named item (it used to clear every prop and reset the face)
                val stock = com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState.resolvePropType(name)
                if (pet != null) {
                    if (stock != null) pet.removeStockProp(stock)
                    pet.removeCustomProp(name)
                } else {
                    sendCustomPropCommand(mapOf("action" to "remove", "name" to name))
                }
                "✨ ถอดอุปกรณ์เสริม '$name' ออกเรียบร้อยแล้วค่ะ"
            }
            "add", "ใส่", "เพิ่ม", "สวม", "set" -> {
                if (svgPath.isNotBlank()) {
                    // a freshly conjured vector prop (persisted by PetCustomPropStore)
                    val extra = linkedMapOf("action" to "add", "name" to name, "svg_path" to svgPath)
                    args["color"]?.let { extra["color"] = it }
                    args["position"]?.let { extra["position"] = it }
                    args["animation"]?.let { extra["animation"] = it }
                    args["size"]?.let { extra["size"] = it }
                    sendCustomPropCommand(extra)
                    return "✨ เสกอุปกรณ์เสริม '$name' จากเวกเตอร์ SVG และสวมให้เรียบร้อยแล้วค่ะ!"
                }
                val stored = com.skyliner2008.jarvis.pet.PetCustomPropStore.findPropByNameOrId(name)
                if (stored != null) {
                    sendCustomPropCommand(mapOf("action" to "add", "name" to name))
                    return "✨ หยิบ '$name' ที่เคยเสกไว้มาสวมให้แล้วค่ะ!"
                }
                val stock = com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState.resolvePropType(name)
                if (stock != null) {
                    if (pet != null) {
                        pet.wearStockProp(stock)
                    } else {
                        val cmd = "HAPPY|props=${stock.name.lowercase()}"
                        mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
                    }
                    return "✨ สวมใส่อุปกรณ์เสริม '${stock.name.lowercase()}' จากคลังสำเร็จรูปเรียบร้อยแล้วค่ะ!"
                }
                val resolved = com.skyliner2008.jarvis.pet.PetSceneEngine.resolveFromKeyword(name)
                if (resolved != null) {
                    val (_, spec) = resolved
                    if (pet != null) {
                        pet.playSceneByNameOrKeyword(name)
                    } else {
                        val (faceState, _) = resolved
                        val cmd = faceState.emotion.name.uppercase() +
                            (if (faceState.propsRaw.isNotBlank()) "|props=${faceState.propsRaw}" else "")
                        mainActivity?.triggerTestEmotion(cmd) ?: broadcastEmotionIntent(cmd)
                    }
                    return "✨ ไม่มี '$name' ในคลัง เลยเล่นฉาก '${spec.nameTh}' ที่ใกล้เคียงให้แทนค่ะ"
                }
                "❌ ไม่พบอุปกรณ์เสริม '$name' ในคลัง — ส่ง svg_path มาเพื่อเสกชิ้นใหม่ได้ค่ะ"
            }
            else -> "❌ action ที่รองรับ: add, remove, clear"
        }
    }

    /** Pet care through the real needs system (PetModeController / PetStateMachine). */
    private suspend fun executePetCare(args: Map<String, String>): String {
        val action = args["action"]?.lowercase()?.trim() ?: "status"
        // the model often opens Pet mode and cares for the pet in the same round:
        // give the pet screen a moment to start its controller
        var pet = com.skyliner2008.jarvis.pet.PetModeController.activeInstance
        var waited = 0
        while (pet == null && waited < 3000) {
            kotlinx.coroutines.delay(100)
            waited += 100
            pet = com.skyliner2008.jarvis.pet.PetModeController.activeInstance
        }
        pet ?: return "❌ ยังไม่ได้อยู่ในโหมดสัตว์เลี้ยง — เปิดด้วย device_always_live(action='on', mode='pet') ก่อนค่ะ"
        val food = args["food"]?.let { com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState.resolvePropType(it) }
        when (action) {
            "feed" -> pet.feedPet(food)
            "clean" -> pet.cleanPet()
            "play" -> pet.playWithPet()
            "sleep" -> pet.putToSleep()
            "wake" -> pet.wakeUpFromTool()
            "status" -> Unit
            else -> return "❌ action ที่รองรับ: feed, clean, play, sleep, wake, status"
        }
        val n = pet.needsState.value
        fun pct(v: Float) = "${v.toInt()}%"
        val done = when (action) {
            "feed" -> "ให้อาหารแล้ว"
            "clean" -> "อาบน้ำแล้ว"
            "play" -> "เล่นด้วยแล้ว"
            "sleep" -> "พานอนแล้ว"
            "wake" -> "ปลุกแล้ว"
            else -> "ค่าสถานะปัจจุบัน"
        }
        return "🐾 $done — อิ่ม ${pct(n.satiety)} · พลังงาน ${pct(n.energy)} · สะอาด ${pct(n.hygiene)} · " +
            "สุข ${pct(n.happiness)} · เครียด ${pct(n.stress)} · โกรธสะสม ${pct(n.rage)} · " +
            "อารมณ์รวม ${n.moodSummary.label} ${n.moodSummary.emoji} · ความสนิท Lv.${n.affectionLevel} (${n.affectionLevelName()})"
    }

    private fun broadcastEmotionIntent(cmd: String) {
        val intent = android.content.Intent("com.skyliner2008.jarvis.TEST_EMOTION").apply {
            putExtra("emotion", cmd)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
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
                com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()
                com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.minimize()
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
            "youtube music" to "com.google.android.apps.youtube.music",
            "ยูทูปมิวสิค" to "com.google.android.apps.youtube.music",
            "yt music" to "com.google.android.apps.youtube.music",
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
            com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.wakeScreen()
            com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.minimize()
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
                val mgr = com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()
                mgr?.wakeScreen()
                mgr?.enable()
                MainActivity.instance?.expandAlwaysLive()
                return "👁️ เปิดหน้าจอเรียบร้อยแล้วค่ะ"
            }
            "lock", "ล็อค", "ล็อคหน้าจอ", "sleep", "พัก", "พักหน้าจอ", "ปิดหน้าจอ", "พักผ่อน", "นอน", "สั่งให้พัก" -> {
                com.skyliner2008.jarvis.MainActivity.instance?.setKeepScreenOn(false)
                com.skyliner2008.jarvis.service.AlwaysLiveManager.getInstanceOrNull()?.onScreenOff()
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
