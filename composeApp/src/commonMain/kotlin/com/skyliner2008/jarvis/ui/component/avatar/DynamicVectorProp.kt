package com.skyliner2008.jarvis.ui.component.avatar

import kotlinx.serialization.Serializable

/**
 * ตำแหน่งจุดยึดของอุปกรณ์เสริมบนใบหน้าหุ่นยนต์
 */
@Serializable
enum class PropPosition {
    FOREHEAD,       // หน้าผาก / บนดวงตา (หมวก, มงกุฎ, รัศมี)
    LEFT_EYE,       // รอบตาซ้าย (แว่นตาข้างเดียว, ผ้าปิดตา, น้ำตา)
    RIGHT_EYE,      // รอบตาขวา (ดาวกระพริบตา, แว่นขยาย)
    CHEEKS,         // พวงแก้ม / ใต้ดวงตา (แก้มแดง, พลาสเตอร์ยา)
    CHIN,           // คาง / ปาก (หนวด, เครา, ไปป์, แก้วกาแฟ)
    FLOATING_LEFT,  // ลอยอยู่ข้างศีรษะฝั่งซ้าย (ผี, นก, สัญญาณเตือน)
    FLOATING_RIGHT  // ลอยอยู่ข้างศีรษะฝั่งขวา (หลอดไฟไอเดีย, โน้ตเพลง)
}

/**
 * รูปแบบแอนิเมชันของอุปกรณ์เสริม
 */
@Serializable
enum class DynamicPropAnimation {
    STATIC,             // นิ่งสนิทตามการเคลื่อนไหวของหัว
    FLOAT_BOB,          // ลอยขึ้นลงช้าๆ นุ่มนวล (Default)
    PULSE,              // เต้นตุบๆ ย่อขยายเป็นจังหวะ
    ROTATE_CONTINUOUS,  // หมุนวนต่อเนื่อง 360 องศา
    SWAY                // แกว่งไปมาซ้ายขวาเหมือนลูกตุ้ม
}

/**
 * DynamicVectorProp — ข้อมูลอุปกรณ์เสริมเวกเตอร์ SVG ที่ AI สามารถเสกขึ้นมาได้แบบ Real-time
 *
 * @param id รหัสอ้างอิงของพร็อพ
 * @param name ชื่อภาษาอังกฤษหรือไทยของพร็อพ เช่น "cowboy_hat", "angel_wings"
 * @param svgPath ข้อมูลคำสั่ง SVG Path Data เช่น "M12 2 C8 2 4 6 4 10 L20 10 Z"
 * @param fillColor รหัสสีเติม Hex e.g. "#FF9800", "#00E5FF"
 * @param strokeColor รหัสสีเส้นขอบ Hex (ถ้าต้องการ)
 * @param strokeWidth ความหนาของเส้นขอบ (dp)
 * @param position ตำแหน่งที่ตั้งบนใบหน้า
 * @param sizeDp ขนาดเป้าหมายของพร็อพ (dp)
 * @param offsetXRatio ปรับเลื่อนแกน X เพิ่มเติม (-1.0 .. 1.0)
 * @param offsetYRatio ปรับเลื่อนแกน Y เพิ่มเติม (-1.0 .. 1.0)
 * @param animation รูปแบบการเคลื่อนไหว
 */
@Serializable
data class DynamicVectorProp(
    val id: String = "",
    val name: String = "",
    val svgPath: String = "",
    val fillColor: String = "#00E5FF",
    val strokeColor: String? = null,
    val strokeWidth: Float = 2f,
    val position: PropPosition = PropPosition.FOREHEAD,
    val sizeDp: Float = 48f,
    val offsetXRatio: Float = 0f,
    val offsetYRatio: Float = 0f,
    val animation: DynamicPropAnimation = DynamicPropAnimation.FLOAT_BOB
)
