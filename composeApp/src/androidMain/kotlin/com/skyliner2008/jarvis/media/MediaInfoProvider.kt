package com.skyliner2008.jarvis.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Log
import com.skyliner2008.jarvis.service.JarvisNotificationListener

/**
 * MediaInfoProvider — ระบบจัดการเพลงและสื่ออัจฉริยะ (YouTube, YouTube Music, Spotify)
 *
 * ความสามารถ:
 *  1. ตรวจสอบ Now Playing: ชื่อเพลง, ศิลปิน, อัลบั้ม, สถานะกำลังเล่น/หยุด
 *  2. ควบคุมเครื่องเล่นเพลงโดยตรงผ่าน MediaController.transportControls
 *  3. สั่งค้นหาและเปิดเพลง/คลิปบน YouTube, YouTube Music, และ Spotify
 */
class MediaInfoProvider(private val context: Context) {

    companion object {
        private const val TAG = "JarvisMediaInfo"

        private val MEDIA_APP_NAMES = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.spotify.music" to "Spotify",
            "com.apple.android.music" to "Apple Music",
            "com.sound_cloud.android" to "SoundCloud"
        )
    }

    data class NowPlayingInfo(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val appName: String,
        val packageName: String,
        val controller: MediaController
    )

    /**
     * ดึงข้อมูลเพลงที่กำลังเล่นอยู่ (หรือหยุดไว้ล่าสุด) จากทุกแอป
     */
    fun getNowPlaying(): NowPlayingInfo? {
        val listener = JarvisNotificationListener.instance ?: run {
            Log.w(TAG, "JarvisNotificationListener not available")
            return null
        }

        val sessions = listener.getActiveMediaSessions()
        if (sessions.isEmpty()) {
            Log.d(TAG, "No active media sessions found")
            return null
        }

        // หา session ที่กำลังเล่นอยู่ก่อน
        val playingSession = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull() ?: return null

        val metadata = playingSession.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "ไม่ระบุชื่อเพลง"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val isPlaying = playingSession.playbackState?.state == PlaybackState.STATE_PLAYING
        val pkg = playingSession.packageName
        val appName = MEDIA_APP_NAMES[pkg] ?: getAppName(pkg)

        return NowPlayingInfo(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            appName = appName,
            packageName = pkg,
            controller = playingSession
        )
    }

    /**
     * ควบคุมการเล่นเพลงผ่าน MediaController ของ session ที่ active
     */
    fun controlPlayback(action: String): Pair<Boolean, String> {
        val current = getNowPlaying()
        if (current == null) {
            return false to "ไม่พบแอปเพลงที่เปิดอยู่"
        }

        val controls = current.controller.transportControls
        return when (action.lowercase()) {
            "play", "เล่น" -> {
                controls.play()
                true to "▶️ สั่งเล่นเพลงบน ${current.appName} แล้วค่ะ (${current.title})"
            }
            "pause", "หยุด", "พัก" -> {
                controls.pause()
                true to "⏸️ หยุดเล่นเพลงบน ${current.appName} แล้วค่ะ"
            }
            "toggle", "สลับ" -> {
                if (current.isPlaying) {
                    controls.pause()
                    true to "⏸️ หยุดเล่นเพลงบน ${current.appName} แล้วค่ะ"
                } else {
                    controls.play()
                    true to "▶️ เล่นเพลงต่อบน ${current.appName} แล้วค่ะ"
                }
            }
            "next", "ถัดไป", "ข้าม" -> {
                controls.skipToNext()
                true to "⏭️ ข้ามไปเพลงถัดไปบน ${current.appName} แล้วค่ะ"
            }
            "previous", "ก่อนหน้า", "ย้อน" -> {
                controls.skipToPrevious()
                true to "⏮️ ย้อนกลับเพลงก่อนหน้าบน ${current.appName} แล้วค่ะ"
            }
            "stop", "หยุดทั้งหมด" -> {
                controls.stop()
                true to "⏹️ หยุดเพลงบน ${current.appName} แล้วค่ะ"
            }
            else -> false to "คำสั่งไม่ถูกต้อง"
        }
    }

    /**
     * ค้นหาและเล่นเพลงบน YouTube, YouTube Music หรือ Spotify
     */
    fun searchAndPlay(query: String, targetApp: String? = null): String {
        val app = targetApp?.lowercase()?.trim() ?: "auto"
        val cleanQuery = query.trim()

        return when {
            app.contains("spotify") -> searchSpotify(cleanQuery)
            app.contains("music") || app.contains("yt_music") -> searchYouTubeMusic(cleanQuery)
            app.contains("youtube") || app.contains("ยูทูป") -> searchYouTube(cleanQuery)
            else -> {
                // Auto: ลอง YouTube Music ก่อน ถ้าไม่มีลอง YouTube
                searchYouTubeMusic(cleanQuery)
            }
        }
    }

    private fun searchYouTube(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "▶️ ค้นหาและเปิดคลิป \"$query\" บน YouTube เรียบร้อยแล้วค่ะ"
            } else {
                // Fallback to web URL
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                "🌐 เปิดผลค้นหา \"$query\" บน YouTube แล้วค่ะ"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube search: ${e.message}", e)
            "❌ ไม่สามารถเปิด YouTube ได้: ${e.message}"
        }
    }

    private fun searchYouTubeMusic(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.apps.youtube.music")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "🎵 ค้นหาและเปิดเพลง \"$query\" บน YouTube Music เรียบร้อยแล้วค่ะ"
            } else {
                // ถ้าไม่มี YouTube Music ให้ fallback ไป YouTube ปกติ
                searchYouTube(query)
            }
        } catch (e: Exception) {
            searchYouTube(query)
        }
    }

    private fun searchSpotify(query: String): String {
        return try {
            val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "🟢 ค้นหาและเปิดเพลง \"$query\" บน Spotify เรียบร้อยแล้วค่ะ"
            } else {
                searchYouTube(query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Spotify: ${e.message}", e)
            searchYouTube(query)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
