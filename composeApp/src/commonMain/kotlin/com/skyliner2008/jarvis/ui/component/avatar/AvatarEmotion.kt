package com.skyliner2008.jarvis.ui.component.avatar

/**
 * AvatarEmotion — อารมณ์/สถานะของ JARVIS Robot Avatar
 *
 * แต่ละ emotion จะเปลี่ยนลักษณะตา ปาก และ animation ของหุ่นยนต์
 * ใช้ร่วมกันทั้ง Full-Screen mode และ Mini Floating overlay
 */
enum class AvatarEmotion {
    /** Default — วงกลมตาสีฟ้า + breathing animation */
    IDLE,
    /** Mic active — ตาใหญ่ขึ้น + audio wave visualizer ใต้ตา */
    LISTENING,
    /** AI กำลังประมวลผล — ตากะพริบเร็ว + thinking dots */
    THINKING,
    /** AI กำลังพูด — ปากเปิด-ปิดตาม audio + body bounce */
    SPEAKING,
    /** บวก — ตาเป็นเส้นโค้งยิ้ม ^_^ + bounce */
    HAPPY,
    /** ลบ — ตาลู่ลง + slow sway */
    SAD,
    /** Error / frustration — ตาสีแดง + คิ้วเฉียง + shake */
    ANGRY,
    /** Affection — ตาเป็นหัวใจ + hearts floating */
    LOVE,
    /** Screen off / idle นาน — ตาปิด + ZZZ floating */
    SLEEPING,
    /** Breakthrough / success — ตาดาว + jump + spin */
    EXCITED,
    /** แอบขยิบตาข้างเดียว (Wink) */
    WINK,
    /** สับสน / สงสัย ตาโตข้างเล็กข้าง พร้อมเครื่องหมาย ? (Confused) */
    CONFUSED,
    /** หน้าบูด แก้มป่อง มีสีแดงที่แก้ม (Pout / Hmph) */
    POUT,
    /** วิงเวียน ตาลายก้นหอยหมุนๆ เมื่อถูกเขย่า (Dizzy) */
    DIZZY,
    /** ตกใจ: ตาเบิกกว้างสุด + กระดอนขึ้น + ปากอ้า O + เสียงตกใจ */
    SURPRISED,
    /** เบื่อ: ตาลู่ครึ่งปิด + กะพริบช้า + หาวบ่อย + เอียงหัว */
    BORED,
    /** โกรธจัด ต่อสู้กลับ (Fight Back): ตาขวางแดงเข้มเพลิง + จรวดมิสซายยิงถล่มหน้าจอ + ระเบิด */
    ENRAGED
}

/**
 * AvatarState — สถานะ realtime ของ avatar ที่ใช้ render
 *
 * @param emotion อารมณ์ปัจจุบัน
 * @param audioLevel ระดับเสียง 0..1 (สำหรับ audio visualizer bars)
 * @param isSpeaking AI กำลังพูดอยู่หรือไม่ (สำหรับ mouth animation)
 * @param glowIntensity ความเข้มของ ambient glow รอบหุ่นยนต์ (0..1)
 * @param statusText ข้อความสั้นแสดงใต้ avatar (เช่น "Listening...", "Thinking...")
 */
data class AvatarState(
    val emotion: AvatarEmotion = AvatarEmotion.IDLE,
    val audioLevel: Float = 0f,
    val isSpeaking: Boolean = false,
    val glowIntensity: Float = 0.5f,
    val statusText: String? = null,
    val gazeOffsetX: Float = 0f,
    val gazeOffsetY: Float = 0f,
    val isDizzy: Boolean = false,
    /** Dynamic face state from AI (background, props, gesture, eye style) */
    val faceState: RobotFaceState = RobotFaceState()
)
