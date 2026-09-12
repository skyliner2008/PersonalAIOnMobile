package com.skyliner2008.jarvis.pet

/**
 * HandGesture — ท่าทางมือที่กล้องตรวจจับได้ เพื่อโต้ตอบกับสัตว์เลี้ยง
 */
enum class HandGesture(val icon: String, val displayNameTh: String) {
    NONE("", "ไม่มีท่าทาง"),
    HIGH_FIVE("✋", "แปะมือ (High-Five)"),
    OK("👌", "โอเค (OK)"),
    BYE("👋", "บ๊ายบาย (Bye)"),
    NO("☝️", "ส่ายนิ้ว (No)"),
    V_SIGN("✌️", "ชูสองนิ้ว (V Sign)"),
    THUMBS_UP("👍", "ยกนิ้วโป้ง (Thumbs Up)"),
    THUMBS_DOWN("👎", "คว่ำนิ้วโป้ง (Thumbs Down)")
}
