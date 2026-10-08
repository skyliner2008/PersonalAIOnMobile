package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.pet.FaceBanner
import com.skyliner2008.jarvis.pet.HandGesture
import com.skyliner2008.jarvis.pet.HandGestureReader
import com.skyliner2008.jarvis.pet.HandPose
import com.skyliner2008.jarvis.pet.HandSighting
import com.skyliner2008.jarvis.pet.PetVisionTargetTracker
import com.skyliner2008.jarvis.pet.SideToSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hands from the hand model (phone 2026-10-09 00:33): the skin-colour blob it replaced took arms and the desk for a
 * hand, read one hand as 👍 then ✌️ within a second, and a hand over one eye came out as a wink.
 */
class HandPoseTest {

    // an upright open hand, frame pixels: wrist (100,300), middle knuckle (100,200) → palm 100 px
    private val openHand = listOf(
        100f to 300f,                                                   // 0 wrist
        70f to 280f, 50f to 250f, 35f to 225f, 25f to 200f,            // 1-4 thumb
        75f to 205f, 70f to 160f, 68f to 130f, 66f to 105f,            // 5-8 index
        100f to 200f, 100f to 150f, 100f to 120f, 100f to 90f,         // 9-12 middle
        125f to 205f, 128f to 160f, 130f to 130f, 132f to 108f,        // 13-16 ring
        148f to 215f, 155f to 180f, 158f to 160f, 160f to 140f         // 17-20 pinky
    )

    private fun hand(
        points: List<Pair<Float, Float>> = openHand,
        gesture: String = "None",
        score: Float = 0.9f,
        dx: Float = 0f,
        side: String = "Right"
    ) = HandSighting(
        points = FloatArray(points.size * 2) { i -> if (i % 2 == 0) points[i / 2].first + dx else points[i / 2].second },
        modelGesture = gesture,
        gestureScore = score,
        handScore = 0.95f,
        side = side
    )

    // index curled onto the thumb, the other three standing
    private val okHand = openHand.toMutableList().apply {
        this[4] = 55f to 210f
        this[6] = 65f to 170f; this[7] = 55f to 190f; this[8] = 50f to 205f
    }

    @Test
    fun `the model's gestures become the pet's, only when it is sure`() {
        assertEquals(HandGesture.V_SIGN, HandPose.gesture(hand(gesture = "Victory"), swinging = false, still = true))
        assertEquals(HandGesture.THUMBS_UP, HandPose.gesture(hand(gesture = "Thumb_Up"), swinging = false, still = true))
        assertEquals(HandGesture.THUMBS_DOWN, HandPose.gesture(hand(gesture = "Thumb_Down"), swinging = false, still = true))
        assertEquals(HandGesture.NONE, HandPose.gesture(hand(gesture = "Closed_Fist"), swinging = false, still = true))
        assertEquals(HandGesture.NONE, HandPose.gesture(hand(gesture = "Victory", score = 0.4f), swinging = false, still = true))
    }

    @Test
    fun `an open palm held is a high five, swinging a wave, a pointing finger swinging a no`() {
        val palm = hand(gesture = "Open_Palm")
        assertEquals(HandGesture.HIGH_FIVE, HandPose.gesture(palm, swinging = false, still = true))
        assertEquals(HandGesture.NONE, HandPose.gesture(palm, swinging = false, still = false), "on its way: neither yet")
        assertEquals(HandGesture.BYE, HandPose.gesture(palm, swinging = true, still = false))
        val point = hand(gesture = "Pointing_Up")
        assertEquals(HandGesture.NO, HandPose.gesture(point, swinging = true, still = false))
        assertEquals(HandGesture.NONE, HandPose.gesture(point, swinging = false, still = true))
        assertEquals("☝️ ชี้นิ้ว", HandPose.poseLabel(point))
    }

    @Test
    fun `OK is thumb and index touching with three fingers up`() {
        assertTrue(HandPose.isOk(hand(okHand)))
        assertEquals(HandGesture.OK, HandPose.gesture(hand(okHand, gesture = "None", score = 0f), swinging = false, still = true))
        assertFalse(HandPose.isOk(hand()), "an open hand is not OK")
        // a fist pinching: the middle finger folded down
        val pinchFist = okHand.toMutableList().apply { this[10] = 100f to 160f; this[11] = 100f to 175f; this[12] = 100f to 190f }
        assertFalse(HandPose.isOk(hand(pinchFist)))
    }

    @Test
    fun `a hand over the face covers it, one beside it does not`() {
        // the hand spans x 25..160, y 90..300
        assertTrue(HandPose.covers(hand(), 0f, 50f, 200f, 350f))
        assertFalse(HandPose.covers(hand(), 300f, 50f, 500f, 350f))
        // only the fingertips reach into the face's edge
        assertFalse(HandPose.covers(hand(), 0f, 0f, 200f, 110f))
    }

    @Test
    fun `side to side counts turns, not jitter`() {
        val swing = SideToSide()
        listOf(0f, 30f, 60f, 30f, 0f, 30f, 60f).forEachIndexed { i, x -> swing.offer(x, i * 100L) }
        assertEquals(2, swing.reversals(50f))
        val jitter = SideToSide()
        listOf(0f, 10f, -5f, 8f, -10f, 5f).forEachIndexed { i, x -> jitter.offer(x, i * 100L) }
        assertEquals(0, jitter.reversals(50f))
        assertTrue(jitter.span(500L, 400L) < 35f)
        // the hand gone for longer than the gap starts over
        swing.offer(0f, 1_400L)
        assertEquals(0, swing.reversals(50f))
    }

    @Test
    fun `the reader waits for a held palm and sees a wave`() {
        val reader = HandGestureReader()
        // held: not a high five before 400 ms of stillness
        assertEquals(HandGesture.NONE, reader.read(listOf(hand(gesture = "Open_Palm")), 0L)!!.gesture)
        assertEquals(HandGesture.NONE, reader.read(listOf(hand(gesture = "Open_Palm")), 200L)!!.gesture)
        assertEquals(HandGesture.HIGH_FIVE, reader.read(listOf(hand(gesture = "Open_Palm")), 500L)!!.gesture)
        assertNull(reader.read(emptyList(), 600L))

        // waved: the fingertips swing 0 → 80 → 0 → 80 px with a 100 px palm
        val waver = HandGestureReader()
        val gestures = listOf(0f, 40f, 80f, 40f, 0f, 40f, 80f).mapIndexed { i, dx ->
            waver.read(listOf(hand(gesture = "Open_Palm", dx = dx)), i * 100L)!!.gesture
        }
        assertFalse(HandGesture.HIGH_FIVE in gestures, "a wave never passes for a high five: $gestures")
        assertEquals(HandGesture.BYE, gestures.last())
    }

    @Test
    fun `with two hands the one making a gesture is read`() {
        val reader = HandGestureReader()
        val reading = reader.read(listOf(hand(gesture = "None", side = "Left"), hand(gesture = "Victory", side = "Right", dx = 300f)), 0L)
        assertEquals(HandGesture.V_SIGN, reading!!.gesture)
        assertEquals("Right", reading.hand.side)
    }

    @Test
    fun `gestures sort with the hands and a plain face with the faces`() {
        // gesture labels lacked "Hand" and plain faces lacked "Face": both went in with the objects — the face was
        // dropped as an object on a face, the gesture lost to the biggest object
        val face = BoundingBox(0.3f, 0.1f, 0.3f, 0.35f)
        val result = PetVisionTargetTracker().processFrame(
            listOf(
                DetectedObject("สล็อต 1 Face #3", 0.9f, face),
                DetectedObject("Hand ✌️ ชูสองนิ้ว (V Sign)", 0.88f, BoundingBox(0.65f, 0.15f, 0.15f, 0.25f)),
                DetectedObject("Accessory / Item 👓", 0.63f, BoundingBox(0.0f, 0.55f, 0.5f, 0.45f))
            ),
            faces = listOf(face)
        )
        assertEquals(3, result.size, result.joinToString { it.label })
    }

    @Test
    fun `the banner names the hand's pose`() {
        assertEquals(listOf("✌️ ชูสองนิ้ว"), FaceBanner.handEvents(listOf("Hand ✌️ ชูสองนิ้ว (V Sign)")))
        assertEquals(listOf("✊ กำมือ"), FaceBanner.handEvents(listOf("Hand ✊ กำมือ")))
        assertEquals(listOf("✋ ยกมือ"), FaceBanner.handEvents(listOf("Hand ✋")))
        assertEquals(emptyList<String>(), FaceBanner.handEvents(listOf("สล็อต 1 Face #3")))
    }
}
