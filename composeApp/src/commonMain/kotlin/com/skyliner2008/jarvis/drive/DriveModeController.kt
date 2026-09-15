package com.skyliner2008.jarvis.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * DriveModeController — ผู้จัดการตรรกะสำหรับโหมดขับขี่และควบคุมอัจฉริยะ (Drive & Control Mode)
 *
 * ช่วยให้ Composable UI สามารถเรียกใช้งานฟังก์ชันควบคุมเพลง, การอ่านแจ้งเตือนเสียง,
 * และเปิดระบบนำทางได้อย่างสะดวก
 */
class DriveModeController(
    val scope: CoroutineScope
) {
    val telemetry: StateFlow<DriveBridge.DriveTelemetry> = DriveBridge.telemetry
    val mediaState: StateFlow<DriveBridge.MediaPlaybackState> = DriveBridge.mediaState
    val recentNotificationText: StateFlow<String?> = DriveBridge.recentNotificationText
    val parkingLocation: StateFlow<DriveBridge.ParkingLocation?> = DriveBridge.parkingLocation
    val isLowGlareMode: StateFlow<Boolean> = DriveBridge.isLowGlareMode

    fun playPause() {
        DriveBridge.triggerPlayPause()
    }

    fun nextTrack() {
        DriveBridge.triggerNextTrack()
    }

    fun prevTrack() {
        DriveBridge.triggerPrevTrack()
    }

    fun readNotifications() {
        DriveBridge.triggerReadNotifications()
    }

    fun startNavigation(destination: String? = null) {
        DriveBridge.triggerStartNavigation(destination)
    }

    fun saveCurrentParking() {
        DriveBridge.saveCurrentParking()
    }

    fun clearParking() {
        DriveBridge.clearParkingLocation()
    }

    fun navigateToParking() {
        val parking = DriveBridge.parkingLocation.value
        if (parking != null && (parking.latitude != 0.0 || parking.longitude != 0.0)) {
            DriveBridge.triggerStartNavigation("${parking.latitude},${parking.longitude}")
        } else if (!parking?.address.isNullOrBlank()) {
            DriveBridge.triggerStartNavigation(parking?.address)
        }
    }

    fun toggleLowGlareMode() {
        DriveBridge.toggleLowGlareMode()
    }

    fun setLowGlareMode(enabled: Boolean) {
        DriveBridge.setLowGlareMode(enabled)
    }

    fun setSpeedLimit(limitKmh: Float) {
        DriveBridge.setSpeedLimit(limitKmh)
    }
}
