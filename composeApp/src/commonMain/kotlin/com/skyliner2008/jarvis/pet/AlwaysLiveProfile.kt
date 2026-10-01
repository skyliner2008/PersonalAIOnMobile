package com.skyliner2008.jarvis.pet

/**
 * AlwaysLiveProfile — โปรไฟล์การทำงานของโหมด Always Live
 */
enum class AlwaysLiveProfile {
    /** โหมดควบคุมทั่วไป (Default) */
    CONTROL,
    /** โหมดขับขี่ / โหมดรถยนต์ (Hands-free, GPS, Media, Notifications) */
    DRIVE,
    /**
     * โหมดอวตาร (เดิม "โหมดสัตว์เลี้ยง"): หุ่น Godot เต็มจอ ไม่มีปุ่ม ตอบสนองกล้อง ไจโร การเขย่า การจิ้ม
     * (ui/screen/AvatarModeScreen.kt) — ชื่อ PET คงไว้ให้คำสั่งเสียง/ค่าที่บันทึกเดิมยังใช้ได้
     */
    PET
}
