package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.drive.DriveBridge
import com.skyliner2008.jarvis.drive.DriveModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DriveModeTest — Unit tests for DriveBridge and DriveModeController
 */
class DriveModeTest {

    @Test
    fun `DriveTelemetry default values are correct`() {
        val defaultTelemetry = DriveBridge.DriveTelemetry()
        assertEquals(0f, defaultTelemetry.speedKmh)
        assertEquals(null, defaultTelemetry.address)
        assertEquals(0.0, defaultTelemetry.latitude)
        assertEquals(0.0, defaultTelemetry.longitude)
        assertFalse(defaultTelemetry.isMoving)
        assertEquals(0L, defaultTelemetry.lastUpdatedTime)
    }

    @Test
    fun `MediaPlaybackState default values are correct`() {
        val defaultMedia = DriveBridge.MediaPlaybackState()
        assertEquals(null, defaultMedia.title)
        assertEquals(null, defaultMedia.artist)
        assertEquals(null, defaultMedia.appName)
        assertFalse(defaultMedia.isPlaying)
    }

    @Test
    fun `DriveBridge telemetry update calculates isMoving correctly`() {
        // Stationary / low speed
        DriveBridge.updateTelemetry(speedKmh = 2.5f, address = "Bangkok", lat = 13.7563, lng = 100.5018)
        assertEquals(2.5f, DriveBridge.telemetry.value.speedKmh)
        assertEquals("Bangkok", DriveBridge.telemetry.value.address)
        assertEquals(13.7563, DriveBridge.telemetry.value.latitude)
        assertEquals(100.5018, DriveBridge.telemetry.value.longitude)
        assertFalse(DriveBridge.telemetry.value.isMoving)
        assertTrue(DriveBridge.telemetry.value.lastUpdatedTime > 0L)

        // Moving above 5 km/h
        DriveBridge.updateTelemetry(speedKmh = 60f, address = "Highway 1", lat = 14.0, lng = 100.6)
        assertEquals(60f, DriveBridge.telemetry.value.speedKmh)
        assertTrue(DriveBridge.telemetry.value.isMoving)
    }

    @Test
    fun `DriveBridge media update updates StateFlow correctly`() {
        DriveBridge.updateMedia(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            appName = "Spotify",
            isPlaying = true
        )
        val state = DriveBridge.mediaState.value
        assertEquals("Bohemian Rhapsody", state.title)
        assertEquals("Queen", state.artist)
        assertEquals("Spotify", state.appName)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `DriveBridge notification update updates StateFlow correctly`() {
        DriveBridge.updateRecentNotification("Line from Mom: Dinner is ready")
        assertEquals("Line from Mom: Dinner is ready", DriveBridge.recentNotificationText.value)

        DriveBridge.updateRecentNotification(null)
        assertEquals(null, DriveBridge.recentNotificationText.value)
    }

    @Test
    fun `DriveModeController invokes DriveBridge action callbacks`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DriveModeController(scope)

        var playPauseCalled = false
        var nextTrackCalled = false
        var prevTrackCalled = false
        var readNotificationsCalled = false
        var navigationDestination: String? = null

        DriveBridge.onPlayPause = { playPauseCalled = true }
        DriveBridge.onNextTrack = { nextTrackCalled = true }
        DriveBridge.onPrevTrack = { prevTrackCalled = true }
        DriveBridge.onReadNotifications = { readNotificationsCalled = true }
        DriveBridge.onStartNavigation = { dest -> navigationDestination = dest }

        controller.playPause()
        assertTrue(playPauseCalled)

        controller.nextTrack()
        assertTrue(nextTrackCalled)

        controller.prevTrack()
        assertTrue(prevTrackCalled)

        controller.readNotifications()
        assertTrue(readNotificationsCalled)

        controller.startNavigation("CentralWorld")
        assertEquals("CentralWorld", navigationDestination)
    }

    @Test
    fun `Structured emotion tag regex correctly extracts valid emotions`() {
        val tagRegex = Regex("\\[(HAPPY|LOVE|EXCITED|SAD|ANGRY|CONFUSED|WINK|POUT|DIZZY|SURPRISED|BORED|ENRAGED|SLEEPING|THINKING|LISTENING|IDLE)\\]", RegexOption.IGNORE_CASE)

        val input1 = "[HAPPY] สวัสดีค่ะ มีอะไรให้ช่วยไหมคะ"
        val match1 = tagRegex.find(input1)
        assertEquals("HAPPY", match1?.groupValues?.get(1)?.uppercase())

        val input2 = "เดินทางปลอดภัยนะคะ [LOVE]"
        val match2 = tagRegex.find(input2)
        assertEquals("LOVE", match2?.groupValues?.get(1)?.uppercase())

        val input3 = "[SURPRISED] ว้าว ถึงที่หมายเร็วกว่ากำหนดมากเลย!"
        val match3 = tagRegex.find(input3)
        assertEquals("SURPRISED", match3?.groupValues?.get(1)?.uppercase())

        val input4 = "ข้อความธรรมดาที่ไม่มีแท็ก"
        val match4 = tagRegex.find(input4)
        assertEquals(null, match4)
    }

    @Test
    fun `Pipe delimited emotion syntax correctly extracts emotion head`() {
        val input1 = "HAPPY|props=sunglasses,sun"
        val head1 = input1.substringBefore("|").trim().uppercase()
        assertEquals("HAPPY", head1)

        val input2 = "SPEAKING|background=rainy"
        val head2 = input2.substringBefore("|").trim().uppercase()
        assertEquals("SPEAKING", head2)
    }

    @Test
    fun `Driving voice intents match correctly`() {
        val mediaTerms = listOf("เปิดเพลง", "เล่นเพลง", "หยุดเพลง", "ข้ามเพลง", "เพลงถัดไป")
        val navTerms = listOf("นำทางไป", "นำทาง", "เปิดแผนที่ไป", "พาไปที่")
        val notifTerms = listOf("อ่านแจ้งเตือน", "อ่านข้อความ", "มีแจ้งเตือนอะไร")
        val speedTerms = listOf("ขับเร็วเท่าไหร่", "ความเร็วเท่าไหร่", "ตอนนี้อยู่ที่ไหน")

        assertTrue(mediaTerms.any { "ช่วยเปิดเพลง bodyslam หน่อย".contains(it) })
        assertTrue(navTerms.any { "นำทางไป สยามพารากอน".contains(it) })
        assertTrue(notifTerms.any { "มีแจ้งเตือนอะไรบ้าง".contains(it) })
        assertTrue(speedTerms.any { "ตอนนี้ขับเร็วเท่าไหร่แล้ว".contains(it) })
    }

    @Test
    fun `Speed limit and speeding detection calculate accurately`() {
        DriveBridge.setSpeedLimit(120f)
        assertEquals(120f, DriveBridge.speedLimitKmh.value)

        // Below speed limit
        DriveBridge.updateTelemetry(speedKmh = 105f, address = "Motorway")
        assertEquals(105f, DriveBridge.telemetry.value.speedKmh)
        assertFalse(DriveBridge.telemetry.value.isSpeeding)

        // Exceeding speed limit
        DriveBridge.updateTelemetry(speedKmh = 128f, address = "Motorway")
        assertTrue(DriveBridge.telemetry.value.isSpeeding)

        // Lowering speed limit updates current telemetry status immediately
        DriveBridge.updateTelemetry(speedKmh = 95f, address = "City Road")
        assertFalse(DriveBridge.telemetry.value.isSpeeding)
        DriveBridge.setSpeedLimit(90f)
        assertEquals(90f, DriveBridge.telemetry.value.speedLimitKmh)
        assertTrue(DriveBridge.telemetry.value.isSpeeding)

        // Reset to default
        DriveBridge.setSpeedLimit(120f)
    }

    @Test
    fun `Smart parking location memory saves, navigates, and clears correctly`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DriveModeController(scope)

        DriveBridge.clearParkingLocation()
        assertEquals(null, DriveBridge.parkingLocation.value)

        // Update current GPS telemetry
        DriveBridge.updateTelemetry(speedKmh = 0f, address = "Mega Bangna", lat = 13.6468, lng = 100.6802)

        // Save current spot
        controller.saveCurrentParking()
        val parking = DriveBridge.parkingLocation.value
        assertTrue(parking != null)
        assertEquals(13.6468, parking.latitude)
        assertEquals(100.6802, parking.longitude)
        assertEquals("Mega Bangna", parking.address)

        // Navigate back to parking
        var navDestination: String? = null
        DriveBridge.onStartNavigation = { dest -> navDestination = dest }
        controller.navigateToParking()
        assertEquals("13.6468,100.6802", navDestination)

        // Clear parking
        controller.clearParking()
        assertEquals(null, DriveBridge.parkingLocation.value)
    }

    @Test
    fun `Low-glare night driving mode toggles state correctly`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DriveModeController(scope)

        DriveBridge.setLowGlareMode(false)
        assertFalse(DriveBridge.isLowGlareMode.value)

        controller.toggleLowGlareMode()
        assertTrue(DriveBridge.isLowGlareMode.value)

        controller.toggleLowGlareMode()
        assertFalse(DriveBridge.isLowGlareMode.value)

        controller.setLowGlareMode(true)
        assertTrue(DriveBridge.isLowGlareMode.value)
        DriveBridge.setLowGlareMode(false)
    }

    @Test
    fun `Parking and night mode voice intents match correctly`() {
        val parkingTerms = listOf(
            "จอดรถอยู่ที่ไหน", "จอดรถไว้ตรงไหน", "รถจอดอยู่ที่ไหน", "รถจอดที่ไหน",
            "หาที่จอดรถ", "รถอยู่ไหน", "จำที่จอดรถ", "บันทึกที่จอดรถ", "บันทึกจุดจอด",
            "จอดรถตรงนี้", "where did i park", "where is my car", "save parking", "remember parking"
        )
        val nightTerms = listOf(
            "เปิดโหมดกลางคืน", "ปิดโหมดกลางคืน", "โหมดกลางคืน", "ลดแสงสะท้อน", "หรี่แสง",
            "night mode", "low glare"
        )

        assertTrue(parkingTerms.any { "ช่วยจำที่จอดรถตรงนี้ให้หน่อย".contains(it) })
        assertTrue(parkingTerms.any { "ตอนนี้รถจอดอยู่ที่ไหนนะ".contains(it) })
        assertTrue(parkingTerms.any { "where is my car parked".contains(it) })
        assertTrue(nightTerms.any { "เปิดโหมดกลางคืนลดแสงสะท้อนหน่อย".contains(it) })
        assertTrue(nightTerms.any { "turn on low glare mode please".contains(it) })
    }
}
