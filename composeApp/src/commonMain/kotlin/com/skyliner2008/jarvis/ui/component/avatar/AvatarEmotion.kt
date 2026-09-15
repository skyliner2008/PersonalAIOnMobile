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
    ENRAGED,

    // ─── LOOI Robot: 20 Moodset (Neon Cyan Style) ───
    /** 9. Dead: ตากากบาทมน X X สีนีออนไซแอน */
    DEAD,
    /** 10. Laughing: ตาลูกศรมน > < สีนีออนไซแอน */
    LAUGHING,
    /** 11. Music: หูฟังครอบศีรษะสีทอง-ดำ Over-Ear Headphones */
    MUSIC,
    /** 12. VR Mode: แว่นตา VR Headset กระจกโค้งเงา + ถังป๊อปคอร์น */
    VR_MODE,
    /** 13. Diving: หน้ากากดำน้ำ Snorkel Mask + ท่อหายใจ + ฟองอากาศ */
    DIVING,
    /** 14. Evil: ตาปิศาจสีแดงเพลิง + เขี้ยวจิ๋ว v v + ไอคอนปิศาจม่วง 😈 */
    EVIL,
    /** 15. Focused: ตาทรงลิ่มคมกริบ + ตารางเลเซอร์ Synthwave Grid */
    FOCUSED,
    /** 17. Shy: ตากลม Squircle + ขีดแก้มชมพูระเรื่อ /// */
    SHY,
    /** 19. Disgusted: ตา > < + ถังขยะเปิดฝาด้านล่าง + ไอคอนถังขยะ 🗑️ */
    DISGUSTED,
    /** 20. Camera Mode: ตาขีด — — + ไอคอนกล้องถ่ายรูป 📷 */
    CAMERA_MODE,
    /** 6. Eating: ตากลม Squircle + เบอร์เกอร์จิ๋วคั่นกลาง */
    EATING,
    /** 7. Drinking: ตากลม Squircle + แก้วเบียร์มีฟองนุ่มคั่นกลาง */
    DRINKING,

    // ─── LOOI Robot: 30 Additional Moodset (Neon Cyan Style) ───
    /** 21. สับสนมึนงง: ตาสควีร์เคิลคลื่น ~ ~, ปากคลื่นหยัก, เครื่องหมาย ¿ ล่างซ้าย และ ?? สีแดงบนขวา */
    PUZZLED,
    /** ป่วย: ตาก้นหอยลู่ลง + ปรอทวัดไข้ */
    SICK,
    /** รวย: ตาเครื่องหมายเงินดอลลาร์ $$ + ถุงเงิน + เหรียญร่วง */
    RICH,
    /** ร้องไห้: ตาโค้งลงเศร้า + น้ำตาไหลพราก */
    CRYING,
    /** อ่านหนังสือ: แว่นสี่เหลี่ยม + หนังสือเปิด */
    READING,
    /** เล่นเกม: ชุดหูฟังเกมมิ่ง + จอยคอนโทรลเลอร์ */
    GAMING,
    /** ท่องเที่ยว: หมวกบัคเก็ต + กล้อง + พาสปอร์ต + เป้ */
    TRAVELING,
    /** ทำงาน: แว่นตา + แล็ปท็อป + กาแฟ */
    WORKING,
    /** หนาว: ตาสั่นหนาวสะท้าน + น้ำแข็งย้อย + เกล็ดหิมะ */
    COLD,
    /** ร้อน: ตาลู่เหงื่อไหล + คลื่นความร้อน + พระอาทิตย์ */
    HOT,
    /** นักสืบ: ตาหรี่สงสัย + หมวก Fedora + แว่นขยาย */
    DETECTIVE,
    /** ทำอาหาร: ตายิ้ม + หมวกเชฟ + ตะหลิว + กระทะ */
    COOKING,
    /** โหมดศิลปะ: ตากลม + หมวกเบเรต์ + จานสี + พู่กัน */
    ART_MODE,
    /** อวกาศ: ตากลมในหมวกนักบินอวกาศ + ดาว + วงโคจร */
    SPACE,
    /** ปาร์ตี้: ตาโค้งยิ้ม + หมวกปาร์ตี้ + แตร + กระดาษสี */
    PARTY,
    /** ฝัน: ตาปิด + Zzz + ก้อนเมฆฝัน + พระจันทร์ */
    DREAMING,
    /** เหนื่อยล้า: ตาหนักลู่ลง + เหงื่อ + ก้นหอยมึน */
    EXHAUSTED,
    /** ไฟฟ้า: ตาสายฟ้าซิกแซก + ประกายไฟ */
    ELECTRIC,
    /** แอบซ่อน: ตาหรี่เหลือบข้าง + หน้ากากโจร */
    SNEAKY,
    /** โรแมนติก: แก้มชมพูระเรื่อ + ปากจู๋ '3' + คาบดอกกุหลาบแดง 🌹 + หัวใจลอย */
    ROMANTIC,
    /** ฮีโร่: หน้ากากซูเปอร์ฮีโร่ + ผ้าคลุม */
    HERO,
    /** กลิตช์: ตาบิดเบี้ยว scanlines + สัญญาณรบกวนดิจิทัล */
    GLITCHED,
    /** เวทมนตร์: หมวกพ่อมด + ไม้กายสิทธิ์ + ประกายดาว */
    MAGIC,
    /** กีฬา: ตาเอาจริง + ผ้าคาดหัว + ลูกบาส */
    SPORTY,
    /** นักวิทยาศาสตร์: แว่นแล็บ + บีกเกอร์ + ขวดทดลอง */
    SCIENTIST,
    /** กลัว: ตาจิ๋วสั่น + ตัวสะท้าน */
    SCARED,
    /** นักรบ: ตาดุ + หมวกไวกิ้ง + ดาบ + โล่ */
    WARRIOR,
    /** แบตหมด: ตาอ่อนล้าจะดับ + ไอคอนแบตแดง */
    LOW_BATTERY
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
    val faceState: RobotFaceState = RobotFaceState(),
    /** สัดส่วนขยายใบหน้าตามระยะห่าง (Distance scaling: 1f = normal, >1f = face close / curious) */
    val faceScaleFactor: Float = 1f
)
