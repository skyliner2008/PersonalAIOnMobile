package com.skyliner2008.jarvis.pet

/** One thing the object model found (ObjectSpotter on Android): its COCO name, score and box in frame pixels. */
class ThingSighting(
    val name: String,
    val score: Float,
    /** left, top, right, bottom — frame pixels */
    val box: FloatArray
)

/**
 * The things in view, in Thai — from EfficientDet-Lite0 (the 80 COCO classes, real scores). It replaced ML Kit's
 * default object detector, which knows five broad classes: everything else came out as "Object #N" with a made-up 75%,
 * a new number every few seconds, boxed on the wall and on the user's shirt (phone 2026-10-09 01:24: #0 … #64 in 100 s,
 * "Object #64" held 20 s after the user left).
 */
object Things {
    /**
     * a thing is a candidate from this score; it shows only once [ThingStabilizer] has seen it in the same place run
     * after run with a mean of [SHOW_SCORE] (phone 2026-10-09 19:31: with nothing on the desk the model reported tv,
     * book, laptop, bottle, handbag, car … at 0.41–0.66, a different one every second — showing from 0.4 alone was wrong)
     */
    const val MIN_SCORE = 0.4f
    /** the mean score a thing needs over its runs to show */
    const val SHOW_SCORE = 0.55f
    /** worn or carried things the model sees in a shirt, an arm or a raised hand: they need more */
    const val WEARABLE_SHOW_SCORE = 0.65f
    val WEARABLES = setOf("handbag", "backpack", "tie", "suitcase", "umbrella")

    fun showScore(name: String): Float = if (name in WEARABLES) WEARABLE_SHOW_SCORE else SHOW_SCORE
    /** how a thing's label starts in the detection list */
    const val PREFIX = "Thing "

    /** not shown: people (the face and hand lines have them) and the room's furniture and fixtures, always there */
    val LEFT_OUT = setOf(
        "person", "chair", "couch", "bed", "dining table", "toilet", "sink", "refrigerator", "oven", "microwave",
        "bench", "traffic light", "fire hydrant", "stop sign", "parking meter", "???"
    )

    private val THAI = mapOf(
        "bicycle" to "🚲 จักรยาน", "car" to "🚗 รถยนต์", "motorcycle" to "🏍️ มอเตอร์ไซค์", "airplane" to "✈️ เครื่องบิน",
        "bus" to "🚌 รถบัส", "train" to "🚆 รถไฟ", "truck" to "🚚 รถบรรทุก", "boat" to "⛵ เรือ",
        "bird" to "🐦 นก", "cat" to "🐱 แมว", "dog" to "🐶 สุนัข", "horse" to "🐴 ม้า", "sheep" to "🐑 แกะ", "cow" to "🐮 วัว",
        "elephant" to "🐘 ช้าง", "bear" to "🐻 หมี", "zebra" to "🦓 ม้าลาย", "giraffe" to "🦒 ยีราฟ",
        "backpack" to "🎒 กระเป๋าเป้", "umbrella" to "☂️ ร่ม", "handbag" to "👜 กระเป๋าถือ", "tie" to "👔 เนคไท",
        "suitcase" to "🧳 กระเป๋าเดินทาง", "frisbee" to "🥏 จานร่อน", "skis" to "🎿 สกี", "snowboard" to "🏂 สโนว์บอร์ด",
        "sports ball" to "⚽ ลูกบอล", "kite" to "🪁 ว่าว", "baseball bat" to "🏏 ไม้เบสบอล", "baseball glove" to "🧤 ถุงมือเบสบอล",
        "skateboard" to "🛹 สเก็ตบอร์ด", "surfboard" to "🏄 กระดานโต้คลื่น", "tennis racket" to "🎾 ไม้เทนนิส",
        "bottle" to "🧴 ขวด", "wine glass" to "🍷 แก้วไวน์", "cup" to "☕ แก้ว", "fork" to "🍴 ส้อม", "knife" to "🔪 มีด",
        "spoon" to "🥄 ช้อน", "bowl" to "🥣 ชาม", "banana" to "🍌 กล้วย", "apple" to "🍎 แอปเปิล", "sandwich" to "🥪 แซนด์วิช",
        "orange" to "🍊 ส้ม", "broccoli" to "🥦 บรอกโคลี", "carrot" to "🥕 แครอท", "hot dog" to "🌭 ฮอตดอก", "pizza" to "🍕 พิซซ่า",
        "donut" to "🍩 โดนัท", "cake" to "🍰 เค้ก", "potted plant" to "🪴 ต้นไม้", "tv" to "📺 จอทีวี", "laptop" to "💻 แล็ปท็อป",
        "mouse" to "🖱️ เมาส์", "remote" to "🎛️ รีโมต", "keyboard" to "⌨️ คีย์บอร์ด", "cell phone" to "📱 มือถือ",
        "toaster" to "🍞 เครื่องปิ้งขนมปัง", "book" to "📖 หนังสือ", "clock" to "⏰ นาฬิกา", "vase" to "🏺 แจกัน",
        "scissors" to "✂️ กรรไกร", "teddy bear" to "🧸 ตุ๊กตาหมี", "hair drier" to "💨 ไดร์เป่าผม", "toothbrush" to "🪥 แปรงสีฟัน"
    )

    fun shows(name: String, score: Float): Boolean = score >= MIN_SCORE && name !in LEFT_OUT

    /** "☕ แก้ว"; a name with no Thai one as it is */
    fun thai(name: String): String = THAI[name] ?: "📦 $name"

    /** the detection list's label: "Thing ☕ แก้ว" */
    fun label(name: String): String = PREFIX + thai(name)
}

/**
 * Things that stay (phone 2026-10-09 19:31): one run of the model is not a thing on the desk — with nothing there it
 * named a tv, a book, a laptop, a bottle, a handbag, one after another. A thing shows when the same class is seen in
 * about the same place (IoU ≥ [minIou]) in [minHits] of the last [window] runs, at a mean score of
 * [Things.showScore]; once shown it stays while seen in [keepHits] of them. Runs come every ~300 ms, so a real
 * thing shows after ~1 s.
 */
class ThingStabilizer(
    private val window: Int = 4,
    private val minHits: Int = 3,
    private val keepHits: Int = 2,
    private val minIou: Float = 0.3f
) {
    private class Track(var sighting: ThingSighting) {
        val hits = ArrayDeque<Float?>()   // the score of each of the last runs, null where it was missing
        var shown = false
        val hitCount: Int get() = hits.count { it != null }
        val meanScore: Float get() = hits.filterNotNull().let { if (it.isEmpty()) 0f else it.sum() / it.size }
    }

    private val tracks = mutableListOf<Track>()

    /** one run's sightings → the things to show (with their mean score and latest box) */
    fun offer(sightings: List<ThingSighting>): List<ThingSighting> {
        val free = tracks.toMutableList()
        val seen = mutableMapOf<Track, ThingSighting>()
        for (s in sightings.sortedByDescending { it.score }) {
            val track = free.filter { it.sighting.name == s.name }
                .maxByOrNull { iou(it.sighting.box, s.box) }
                ?.takeIf { iou(it.sighting.box, s.box) >= minIou }
            if (track != null) {
                free.remove(track)
                seen[track] = s
            } else {
                val t = Track(s)
                tracks += t
                seen[t] = s
            }
        }
        for (t in tracks) {
            val s = seen[t]
            if (s != null) t.sighting = s
            t.hits.addLast(s?.score)
            while (t.hits.size > window) t.hits.removeFirst()
        }
        tracks.removeAll { it.hitCount == 0 }
        for (t in tracks) {
            val enough = t.meanScore >= Things.showScore(t.sighting.name)
            t.shown = enough && t.hitCount >= (if (t.shown) keepHits else minHits)
        }
        return tracks.filter { it.shown }.map { ThingSighting(it.sighting.name, it.meanScore, it.sighting.box) }
    }

    fun clear() = tracks.clear()

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val iw = minOf(a[2], b[2]) - maxOf(a[0], b[0])
        val ih = minOf(a[3], b[3]) - maxOf(a[1], b[1])
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
        return if (union > 0f) inter / union else 0f
    }
}
