package com.skyliner2008.jarvis.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * DriveBridge — สะพานเชื่อมข้อมูลและการควบคุมสำหรับโหมดขับขี่และโหมดควบคุมอัจฉริยะ (Drive & Control Mode)
 *
 * ทำหน้าที่เชื่อมโยงระหว่างระบบ Android (LocationProvider, MediaInfoProvider, NotificationBridge)
 * กับ Compose Multiplatform UI (DriveModeScreen / DriveModeController) ใน commonMain
 */
object DriveBridge {

    data class DriveTelemetry(
        val speedKmh: Float = 0f,
        val address: String? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val isMoving: Boolean = false,
        val speedLimitKmh: Float = 120f,
        val isSpeeding: Boolean = false,
        val lastUpdatedTime: Long = 0L
    )

    data class MediaPlaybackState(
        val title: String? = null,
        val artist: String? = null,
        val appName: String? = null,
        val isPlaying: Boolean = false
    )

    data class ParkingLocation(
        val latitude: Double,
        val longitude: Double,
        val address: String? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    // ─── StateFlows สำหรับ UI สังเกตการณ์แบบ Real-time ───
    private val _speedLimitKmh = MutableStateFlow(120f)
    val speedLimitKmh: StateFlow<Float> = _speedLimitKmh.asStateFlow()

    private val _telemetry = MutableStateFlow(DriveTelemetry())
    val telemetry: StateFlow<DriveTelemetry> = _telemetry.asStateFlow()

    private val _mediaState = MutableStateFlow(MediaPlaybackState())
    val mediaState: StateFlow<MediaPlaybackState> = _mediaState.asStateFlow()

    private val _recentNotificationText = MutableStateFlow<String?>(null)
    val recentNotificationText: StateFlow<String?> = _recentNotificationText.asStateFlow()

    private val _parkingLocation = MutableStateFlow<ParkingLocation?>(null)
    val parkingLocation: StateFlow<ParkingLocation?> = _parkingLocation.asStateFlow()

    private val _isLowGlareMode = MutableStateFlow(false)
    val isLowGlareMode: StateFlow<Boolean> = _isLowGlareMode.asStateFlow()

    // ─── Callbacks จาก UI ส่งกลับไปประมวลผลบน Platform (Android) ───
    var onPlayPause: (() -> Unit)? = null
    var onNextTrack: (() -> Unit)? = null
    var onPrevTrack: (() -> Unit)? = null
    var onReadNotifications: (() -> Unit)? = null
    var onStartNavigation: ((destination: String?) -> Unit)? = null

    /**
     * อัปเดตข้อมูลพิกัดและความเร็ว GPS
     */
    fun updateTelemetry(speedKmh: Float, address: String?, lat: Double = 0.0, lng: Double = 0.0) {
        val currentLimit = _speedLimitKmh.value
        _telemetry.value = DriveTelemetry(
            speedKmh = speedKmh,
            address = address,
            latitude = lat,
            longitude = lng,
            isMoving = speedKmh > 5f,
            speedLimitKmh = currentLimit,
            isSpeeding = speedKmh > currentLimit,
            lastUpdatedTime = Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * กำหนดขีดจำกัดความเร็ว (กม./ชม.)
     */
    fun setSpeedLimit(limitKmh: Float) {
        _speedLimitKmh.value = limitKmh
        val current = _telemetry.value
        _telemetry.value = current.copy(
            speedLimitKmh = limitKmh,
            isSpeeding = current.speedKmh > limitKmh
        )
    }

    /**
     * บันทึกพิกัดตำแหน่งจอดรถปัจจุบัน
     */
    fun saveCurrentParking() {
        val cur = _telemetry.value
        _parkingLocation.value = ParkingLocation(
            latitude = cur.latitude,
            longitude = cur.longitude,
            address = cur.address
        )
    }

    /**
     * บันทึกพิกัดจุดจอดรถแบบระบุค่า
     */
    fun saveParkingLocation(lat: Double, lng: Double, address: String?) {
        _parkingLocation.value = ParkingLocation(
            latitude = lat,
            longitude = lng,
            address = address
        )
    }

    /**
     * ล้างข้อมูลจุดจอดรถ
     */
    fun clearParkingLocation() {
        _parkingLocation.value = null
    }

    /**
     * เปิด/ปิด โหมดขับขี่กลางคืนลดแสงสะท้อน
     */
    fun setLowGlareMode(enabled: Boolean) {
        _isLowGlareMode.value = enabled
    }

    fun toggleLowGlareMode() {
        _isLowGlareMode.value = !_isLowGlareMode.value
    }

    /**
     * อัปเดตสถานะเครื่องเล่นสื่อ (YouTube, Spotify, YouTube Music)
     */
    fun updateMedia(title: String?, artist: String?, appName: String?, isPlaying: Boolean) {
        _mediaState.value = MediaPlaybackState(
            title = title,
            artist = artist,
            appName = appName,
            isPlaying = isPlaying
        )
    }

    /**
     * อัปเดตข้อความแจ้งเตือนล่าสุด
     */
    fun updateRecentNotification(text: String?) {
        _recentNotificationText.value = text
    }

    fun triggerPlayPause() = onPlayPause?.invoke()
    fun triggerNextTrack() = onNextTrack?.invoke()
    fun triggerPrevTrack() = onPrevTrack?.invoke()
    fun triggerReadNotifications() = onReadNotifications?.invoke()
    fun triggerStartNavigation(destination: String? = null) = onStartNavigation?.invoke(destination)
}
