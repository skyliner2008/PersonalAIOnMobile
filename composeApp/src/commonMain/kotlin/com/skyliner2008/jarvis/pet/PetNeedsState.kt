package com.skyliner2008.jarvis.pet

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * PetMood — สรุปอารมณ์รวมของ Pet จากค่าสถานะทั้งหมด
 */
enum class PetMood(val emoji: String, val label: String) {
    ECSTATIC("🤩", "สุขสุดๆ"),
    CONTENT("😊", "สบายดี"),
    NEUTRAL("😐", "เฉยๆ"),
    BORED("😑", "เบื่อ"),
    HUNGRY("🍖", "หิว"),
    TIRED("💤", "เหนื่อย"),
    DIRTY("🧼", "สกปรก"),
    STRESSED("😰", "เครียด"),
    ENRAGED("😡💥", "โกรธจัด!"),
    MISERABLE("😢", "แย่มาก")
}

/**
 * PetNeedsState — ข้อมูลสถานะและจิตวิทยาของสัตว์เลี้ยง (Tamagotchi Engine)
 */
@Serializable
data class PetNeedsState(
    // ─── 1. Physical Needs (สถานะทางกายภาพ) ────────────────────────────
    /** ความอิ่ม (0..100f): เต็ม = ร่าเริง, ต่ำ = หิว ร้องขอกิน */
    val satiety: Float = 85f,
    /** พลังงาน (0..100f): ลดลงเมื่อเล่น/ทำกิจกรรม, หมด = สัปหงก นอนหลับ */
    val energy: Float = 90f,
    /** ความสะอาด (0..100f): ต่ำ = เลอะเทอะ มอมแมม หน้าบึ้ง */
    val hygiene: Float = 95f,

    // ─── 2. Mood & Emotional States (สภาวะอารมณ์ชั่วคราว) ───────────────
    /** ความสุข (0..100f): เพิ่มขึ้นเมื่อเล่น/ลูบหัว/ให้ขนม, สูง = กระโดดโลดเต้น มีประกายวิ้ง */
    val happiness: Float = 80f,
    /** ความเครียด (0..100f): เพิ่มขึ้นเมื่อถูกแกล้ง/อดอาหาร/โดนละเลย, สูง = งอน บึ้งตึง */
    val stress: Float = 10f,
    /** ความโกรธสะสม (0..100f): เพิ่มขึ้นเมื่อโดนเขย่ารัวๆ/ตะคอก/แกล้ง, เต็ม 100 = ENRAGED ยิงมิสซายสู้กลับ */
    val rage: Float = 0f,

    // ─── 3. Relationship & Psychology (ความสัมพันธ์และจิตวิทยาระยะยาว) ──
    /** แต้มความสนิทสนมสะสม (0..1000): ยิ่งสูงยิ่งปลดล็อกท่าทางอ้อนพิเศษ */
    val affectionPoints: Int = 120,
    /** ระดับการฝึกฝน / ความเชื่อฟัง (0..100f): ต่ำ = เมินเฉย ยักไหล่ ดื้อ */
    val obedience: Float = 75f,
    /** นิสัย: เงียบขรึม (-100f) ↔ ไฮเปอร์ (+100f) */
    val hyperCalmTrait: Float = 10f,
    /** นิสัย: รักอิสระ (-100f) ↔ ขี้อ้อน (+100f) */
    val clingyIndependentTrait: Float = 20f,

    /** เวลาที่อัปเดตล่าสุด (Timestamp มิลลิวินาที) */
    val lastUpdateTimestamp: Long = 0L
) {
    /** เลเวลความสนิทสนม (1..10) */
    val affectionLevel: Int
        get() = ((affectionPoints / 100) + 1).coerceIn(1, 10)

    val isHungry: Boolean
        get() = satiety < 30f

    val isExhausted: Boolean
        get() = energy < 20f

    val isDirty: Boolean
        get() = hygiene < 35f

    val isStressed: Boolean
        get() = stress > 60f

    val isSuperHappy: Boolean
        get() = happiness > 85f && !isHungry && !isExhausted

    val isHyper: Boolean
        get() = hyperCalmTrait >= 0f

    val isClingy: Boolean
        get() = clingyIndependentTrait >= 0f

    /** Pet รู้สึกเบื่อ: ไม่มีความสุข + ไม่หิว ไม่เหนื่อย (แค่ไม่มีอะไรทำ) */
    val isBored: Boolean
        get() = happiness < 25f && !isHungry && !isExhausted && stress < 40f

    /** Pet กลัว/ตกใจง่าย: ความเครียดสูง */
    val isScared: Boolean
        get() = stress > 80f

    /** Pet โกรธจัด สู้กลับ: หลอดความโกรธเต็ม 100% */
    val isEnraged: Boolean
        get() = rage >= 100f

    /** สรุปอารมณ์รวมจากค่าทั้งหมด */
    val moodSummary: PetMood
        get() = when {
            isEnraged -> PetMood.ENRAGED
            isSuperHappy -> PetMood.ECSTATIC
            isStressed && isHungry -> PetMood.MISERABLE
            isStressed -> PetMood.STRESSED
            isHungry -> PetMood.HUNGRY
            isExhausted -> PetMood.TIRED
            isDirty -> PetMood.DIRTY
            isBored -> PetMood.BORED
            happiness > 60f -> PetMood.CONTENT
            else -> PetMood.NEUTRAL
        }

    fun affectionLevelName(): String = when (affectionLevel) {
        in 1..2 -> "คนแปลกหน้าคุ้นเคย"
        in 3..4 -> "เพื่อนสนิท"
        in 5..6 -> "คู่หูรู้ใจ"
        in 7..8 -> "ครอบครัวที่รัก"
        in 9..10 -> "สายใยนิรันดร์"
        else -> "เพื่อน"
    }

    /**
     * อัปเดตการลดลงตามเวลาที่ผ่านไป (Time Decay)
     * @param elapsedMs เวลาที่ผ่านไปนับจากครั้งก่อนหน้า
     */
    fun decay(elapsedMs: Long): PetNeedsState {
        if (elapsedMs <= 0L) return this
        val minutes = elapsedMs / 60_000f

        // ความอิ่มลดลง ~1 แต้มทุก 3 นาที
        val newSatiety = (satiety - (minutes * 0.33f)).coerceIn(0f, 100f)
        // พลังงานลดลงเบาๆ เมื่อตื่น ~0.2 แต้มต่อนาที
        val newEnergy = (energy - (minutes * 0.15f)).coerceIn(0f, 100f)
        // ความสะอาดลดลง ~0.1 แต้มต่อนาที
        val newHygiene = (hygiene - (minutes * 0.10f)).coerceIn(0f, 100f)

        // ความเครียดเพิ่มขึ้นถ้าหิวจัดหรือตัวสกปรก
        val stressDelta = if (newSatiety < 25f || newHygiene < 25f) minutes * 0.25f else -minutes * 0.05f
        val newStress = (stress + stressDelta).coerceIn(0f, 100f)

        // ความสุขลดลงช้าๆ หากละเลย
        val happyDelta = if (newStress > 40f) -minutes * 0.20f else -minutes * 0.05f
        val newHappiness = (happiness + happyDelta).coerceIn(0f, 100f)

        // ความโกรธค่อยๆ ลดลงตามเวลา
        val newRage = (rage - (minutes * 2.0f)).coerceIn(0f, 100f)

        return copy(
            satiety = newSatiety,
            energy = newEnergy,
            hygiene = newHygiene,
            happiness = newHappiness,
            stress = newStress,
            rage = newRage,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** เพิ่มความโกรธสะสม 💢 */
    fun addRage(amount: Float): PetNeedsState {
        return copy(
            rage = (rage + amount).coerceIn(0f, 100f),
            stress = (stress + amount * 0.5f).coerceIn(0f, 100f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** ปลดปล่อยความโกรธ (หลังยิงมิสซายสู้กลับ) 🚀💥 */
    fun dischargeRage(): PetNeedsState {
        return copy(
            rage = 0f,
            stress = (stress - 30f).coerceIn(0f, 100f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** ให้อาหาร 🍖 */
    fun feed(): PetNeedsState {
        return copy(
            satiety = (satiety + 30f).coerceIn(0f, 100f),
            happiness = (happiness + 12f).coerceIn(0f, 100f),
            stress = (stress - 15f).coerceIn(0f, 100f),
            affectionPoints = (affectionPoints + 5).coerceAtMost(1000),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** อาบน้ำ / เช็ดตัว 🧼 */
    fun clean(): PetNeedsState {
        return copy(
            hygiene = 100f,
            happiness = (happiness + 10f).coerceIn(0f, 100f),
            stress = (stress - 10f).coerceIn(0f, 100f),
            affectionPoints = (affectionPoints + 5).coerceAtMost(1000),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** ชวนเล่นกิจกรรม 🎾 */
    fun play(): PetNeedsState {
        return copy(
            happiness = (happiness + 20f).coerceIn(0f, 100f),
            energy = (energy - 15f).coerceIn(0f, 100f),
            satiety = (satiety - 6f).coerceIn(0f, 100f),
            stress = (stress - 20f).coerceIn(0f, 100f),
            affectionPoints = (affectionPoints + 8).coerceAtMost(1000),
            hyperCalmTrait = (hyperCalmTrait + 2f).coerceIn(-100f, 100f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** ลูบหัว / เกาคาง / เล่นแปะมือ 💕 */
    fun interactLove(): PetNeedsState {
        return copy(
            happiness = (happiness + 8f).coerceIn(0f, 100f),
            stress = (stress - 12f).coerceIn(0f, 100f),
            affectionPoints = (affectionPoints + 4).coerceAtMost(1000),
            clingyIndependentTrait = (clingyIndependentTrait + 1f).coerceIn(-100f, 100f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    /** พักผ่อน / นอนหลับ 💤 */
    fun rest(minutes: Float = 10f): PetNeedsState {
        return copy(
            energy = (energy + minutes * 3.5f).coerceIn(0f, 100f),
            stress = (stress - minutes * 1.5f).coerceIn(0f, 100f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }
}
