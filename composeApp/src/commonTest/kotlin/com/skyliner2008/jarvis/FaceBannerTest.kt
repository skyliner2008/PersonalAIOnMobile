package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.pet.FaceBanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The lines at the bottom of Avatar mode (ผู้ใช้ 2026-10-09: "ให้แสดงชื่อ หรือ event ใบหน้าที่ตรวจจับได้" → "ตรวจจับใบหน้า
 * อยู่ด้านล่างสุดชิดซ้าย, ตรวจจับมือ วัตถุ ให้อยู่ด้านล่างชิดขวา")
 */
class FaceBannerTest {

    @Test
    fun `the face line names who it is and what the face does, nothing of the hands`() {
        val labels = listOf("บอส Smile 😊 91%", "Hand 👋 บ๊ายบาย (Bye)", "Object #2 📦")
        assertEquals("👤 บอส (72%) · 😊 ยิ้ม", FaceBanner.face(0, "บอส (72%)", true, labels))
        assertEquals("👤 บอส (72%) · 😉 ขยิบตา", FaceBanner.face(0, "บอส (72%)", true, listOf("บอส Wink 😉")))
        assertEquals("👤 บอส (72%)", FaceBanner.face(0, "บอส (72%)", true, listOf("บอส Face #3")))
    }

    @Test
    fun `other faces say who they are not`() {
        assertEquals("👤 บุคคลอื่น", FaceBanner.face(-1, "บุคคลอื่น / ไม่ตรงกับสล็อตใด", true, emptyList()))
        assertEquals("👤 ใบหน้าใหม่ (ยังไม่ลงทะเบียน)", FaceBanner.face(-1, "ตรวจพบใบหน้า (ยังไม่ลงทะเบียน)", true, emptyList()))
        assertEquals("👤 ต้องสแกนใบหน้าใหม่", FaceBanner.face(-1, "ใบหน้าที่บันทึกไว้ต้องสแกนใหม่ (ระบบจดจำใหม่)", true, emptyList()))
        assertEquals("👤 ตรวจพบใบหน้า", FaceBanner.face(-1, null, true, emptyList()))
        assertNull(FaceBanner.face(0, "บอส (72%)", false, listOf("บอส Smile 😊 91%")))
    }

    @Test
    fun `the sight line has the hands first, then things, none of the face`() {
        assertEquals(
            "👋 บ๊ายบาย · ☕ แก้ว",
            FaceBanner.sight(listOf("บอส Smile 😊 91%", "Hand 👋 บ๊ายบาย (Bye)", "Thing ☕ แก้ว"))
        )
        assertEquals("✊ กำมือ", FaceBanner.sight(listOf("บอส Face #3", "Hand ✊ กำมือ")))
        assertEquals(
            "📱 มือถือ · 📖 หนังสือ",
            FaceBanner.sight(listOf("Thing 📱 มือถือ", "Thing 📖 หนังสือ", "Thing ⏰ นาฬิกา"))
        )
        assertNull(FaceBanner.sight(listOf("บอส Smile 😊 91%", "Guest Face #1")))
    }

    @Test
    fun `at most two events on a line, each once`() {
        assertEquals(listOf("😊 ยิ้ม"), FaceBanner.faceEvents(listOf("บอส Smile 😊 91%", "Guest Smile 😊 80%")))
        assertEquals("✌️ ชูสองนิ้ว · ✋ ยกมือ", FaceBanner.sight(listOf("Hand ✌️ ชูสองนิ้ว (V Sign)", "Hand ✋", "Object #5 📦")))
    }

    @Test
    fun `the lines rise above the robot's task pills`() {
        assertEquals(0, FaceBanner.taskListHeightUnits(0))
        assertEquals(88, FaceBanner.taskListHeightUnits(1))
        assertEquals(208, FaceBanner.taskListHeightUnits(3))
        assertEquals(247, FaceBanner.taskListHeightUnits(5), "3 pills and the +N line")
    }
}
