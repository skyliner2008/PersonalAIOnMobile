package com.example.personalaibot.ui.component.avatar

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
    EXCITED
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
    val statusText: String? = null
)
