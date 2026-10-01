package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.GazeNeutral
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Camera gaze, up and down: straight ahead is where the user's face usually is (log 2026-10-01). */
class GazeNeutralTest {
    private val frameMs = 80L        // PetVisionDetector processes ~12 frames a second

    @Test
    fun `a hand-held face low in the frame is looked at straight, not at the floor`() {
        val n = GazeNeutral()
        // the old desk line (-0.45) made this face +0.83 * 1.35 = clamped to the bottom edge
        assertEquals(0f, n.offsetY(0.38f, 0L))
        var t = 0L
        repeat(50) { t += frameMs; assertTrue(abs(n.offsetY(0.38f, t)) < 0.01f) }
    }

    @Test
    fun `a quick move still turns the eyes`() {
        val n = GazeNeutral()
        n.offsetY(0.38f, 0L)
        val up = n.offsetY(-0.1f, frameMs)       // the user lifts their head
        assertTrue(up < -0.45f, "offset $up")
    }

    @Test
    fun `a new way of holding the phone becomes straight ahead within seconds`() {
        val n = GazeNeutral()
        n.offsetY(-0.45f, 0L)                     // on a desk stand
        var t = 0L
        var off = 0f
        repeat((25_000L / frameMs).toInt()) { t += frameMs; off = n.offsetY(0.35f, t) }   // picked up
        assertTrue(abs(off) < 0.08f, "offset after 25 s $off")
    }

    @Test
    fun `after a while away the next face is straight ahead again`() {
        val n = GazeNeutral()
        n.offsetY(-0.45f, 0L)
        assertEquals(0f, n.offsetY(0.3f, 6_000L))
        assertTrue(abs(n.offsetY(0.3f, 6_080L)) < 0.001f)
    }
}
