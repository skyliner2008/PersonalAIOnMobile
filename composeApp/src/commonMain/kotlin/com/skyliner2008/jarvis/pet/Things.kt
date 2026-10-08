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
    /** a thing counts from this score */
    const val MIN_SCORE = 0.5f
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
