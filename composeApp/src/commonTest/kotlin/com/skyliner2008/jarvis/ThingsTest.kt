package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.pet.FaceBanner
import com.skyliner2008.jarvis.pet.PetVisionTargetTracker
import com.skyliner2008.jarvis.pet.ThingSighting
import com.skyliner2008.jarvis.pet.ThingStabilizer
import com.skyliner2008.jarvis.pet.Things
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Things in view from EfficientDet-Lite0 (phone 2026-10-09 01:24: ML Kit's default detector boxed the wall and the
 * user's shirt as "Object #0 … #64" with a made-up 75%).
 */
class ThingsTest {

    // the model's labels.txt (assets/models/efficientdet_lite0.tflite), its "???" gaps left out
    private val coco = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow", "elephant",
        "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
        "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
        "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli",
        "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet",
        "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    @Test
    fun `every class has a Thai name or is left out`() {
        assertEquals(80, coco.size)
        val unnamed = coco.filter { it !in Things.LEFT_OUT && Things.thai(it).startsWith("📦") }
        assertTrue(unnamed.isEmpty(), "no Thai name: $unnamed")
    }

    @Test
    fun `people and furniture are left out, and a weak score`() {
        assertEquals("Thing ☕ แก้ว", Things.label("cup"))
        assertEquals("📱 มือถือ", Things.thai("cell phone"))
        assertTrue(Things.shows("cup", 0.6f))
        assertTrue(Things.shows("cup", 0.4f))
        assertFalse(Things.shows("cup", 0.3f))
        assertFalse(Things.shows("person", 0.95f))
        assertFalse(Things.shows("chair", 0.95f))
        assertFalse(Things.shows("dining table", 0.95f))
    }

    @Test
    fun `a thing goes through the tracker as an object and reaches the bottom-right line`() {
        val face = BoundingBox(0.3f, 0.1f, 0.3f, 0.35f)
        val result = PetVisionTargetTracker().processFrame(
            listOf(
                DetectedObject("บอส Face #1", 0.9f, face),
                DetectedObject(Things.label("cell phone"), 0.7f, BoundingBox(0.62f, 0.5f, 0.2f, 0.3f))
            ),
            faces = listOf(face)
        )
        assertEquals(2, result.size, result.joinToString { it.label })
        assertEquals("📱 มือถือ", FaceBanner.sight(result.map { it.label }))
    }

    @Test
    fun `a thing held at the chin shows`() {
        val face = BoundingBox(0.3f, 0.1f, 0.3f, 0.35f)
        val result = PetVisionTargetTracker().processFrame(
            listOf(
                DetectedObject("บอส Face #1", 0.9f, face),
                // a cup held just under the chin: inside the face-and-neck zone
                DetectedObject(Things.label("cup"), 0.6f, BoundingBox(0.40f, 0.42f, 0.12f, 0.12f))
            ),
            faces = listOf(face)
        )
        assertTrue(result.any { it.label == Things.label("cup") }, result.joinToString { it.label })
    }

    private fun seen(name: String, score: Float, x: Float = 100f) = ThingSighting(name, score, floatArrayOf(x, 100f, x + 80f, 180f))

    @Test
    fun `a different guess every run never shows`() {
        // phone 2026-10-09 19:31: nothing on the desk, the model named one thing after another
        val st = ThingStabilizer()
        val runs = listOf(seen("tv", 0.66f, 300f), seen("book", 0.43f), seen("laptop", 0.57f, 200f), seen("bottle", 0.45f, 50f),
            seen("tv", 0.48f, 300f), seen("handbag", 0.48f, 400f), seen("book", 0.41f))
        runs.forEach { assertTrue(st.offer(listOf(it)).isEmpty(), "showed after ${it.name}") }
    }

    @Test
    fun `a thing that stays shows after three runs and goes after it is gone`() {
        val st = ThingStabilizer()
        assertTrue(st.offer(listOf(seen("cup", 0.62f))).isEmpty())
        assertTrue(st.offer(listOf(seen("cup", 0.58f, 105f))).isEmpty())
        val shown = st.offer(listOf(seen("cup", 0.60f, 110f)))
        assertEquals(listOf("cup"), shown.map { it.name })
        assertTrue(kotlin.math.abs(shown[0].score - 0.60f) < 0.01f, "the mean score")
        // a run or two missed (a hand passing over it): still there; a third: gone
        assertEquals(listOf("cup"), st.offer(emptyList()).map { it.name })
        assertEquals(listOf("cup"), st.offer(emptyList()).map { it.name })
        assertTrue(st.offer(emptyList()).isEmpty())
    }

    @Test
    fun `a weak steady guess and a worn thing need more`() {
        val tv = ThingStabilizer()
        repeat(6) { assertTrue(tv.offer(listOf(seen("tv", 0.48f))).isEmpty(), "a steady 0.48 is not a tv") }
        val bag = ThingStabilizer()
        repeat(6) { assertTrue(bag.offer(listOf(seen("handbag", 0.60f))).isEmpty(), "0.60 is not enough for a handbag") }
        val realBag = ThingStabilizer()
        val shown = (1..3).map { realBag.offer(listOf(seen("handbag", 0.72f))) }.last()
        assertEquals(listOf("handbag"), shown.map { it.name })
    }

    @Test
    fun `the same class somewhere else is another thing`() {
        val st = ThingStabilizer()
        st.offer(listOf(seen("cup", 0.7f, 0f)))
        st.offer(listOf(seen("cup", 0.7f, 300f)))
        assertTrue(st.offer(listOf(seen("cup", 0.7f, 0f))).isEmpty(), "jumping between two places is not one cup seen 3 times")
    }
}
