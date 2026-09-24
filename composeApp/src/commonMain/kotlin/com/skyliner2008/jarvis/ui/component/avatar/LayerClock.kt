package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos

/**
 * LayerClock — นาฬิกาต่อเนื่องสำหรับอนุภาคของฉากหลัง/ฉากหน้า
 *
 * เดิมอนุภาคคำนวณตำแหน่งจาก `(y + progress * speed) % 1f` โดย progress วน 0→1 แล้วกลับ 0
 * เมื่อ speed ไม่ใช่จำนวนเต็ม อนุภาคจะ "วาร์ป" ทุกครั้งที่ลูปเริ่มใหม่
 * นาฬิกานี้นับเวลาเป็น Double ต่อเนื่องไม่วนกลับ จึงไม่มีจุดกระโดด
 * และจำกัดช่วงห่างของเฟรมไว้ที่ [MAX_FRAME_STEP_SEC] — กลับจากพื้นหลังแล้วอนุภาคไม่พุ่งข้ามจอ
 *
 * ค่าอ่านได้ใน draw phase เท่านั้น (ภายใน Canvas) — ขยับทุกเฟรมโดยไม่ recompose
 */
@Stable
class LayerClock internal constructor(private val seconds: State<Double>) {
    /** Loop count since the layer appeared: +1 every [periodMs], never wraps. */
    fun cycles(periodMs: Int): Double = seconds.value * 1000.0 / periodMs

    companion object {
        const val MAX_FRAME_STEP_SEC = 0.1
    }
}

@Composable
fun rememberLayerClock(): LayerClock {
    val seconds = produceState(0.0) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val step = ((now - last) / 1_000_000_000.0).coerceIn(0.0, LayerClock.MAX_FRAME_STEP_SEC)
                last = now
                value += step
            }
        }
    }
    return LayerClock(seconds)
}

/**
 * Position on a 0..1 loop: `(base + cycles * speed) mod 1`, computed in Double so it stays
 * continuous for any speed and any uptime. Negative [speed] moves the other way (rising particles).
 */
fun loopFrac(base: Float, cycles: Double, speed: Float): Float {
    val v = (base + cycles * speed) % 1.0
    return (if (v < 0.0) v + 1.0 else v).toFloat()
}
