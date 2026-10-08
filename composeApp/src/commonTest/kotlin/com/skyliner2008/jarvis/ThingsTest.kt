package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import com.skyliner2008.jarvis.pet.FaceBanner
import com.skyliner2008.jarvis.pet.PetVisionTargetTracker
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
        assertFalse(Things.shows("cup", 0.4f))
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
}
