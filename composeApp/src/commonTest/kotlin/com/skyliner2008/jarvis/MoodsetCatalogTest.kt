package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MoodsetCatalogTest — Unit tests for LooiMoodsetCatalog and Voice Commands
 *
 * ตรวจสอบความถูกต้องของ:
 * 1. Moodset ครบทั้ง 50 รูปแบบ (หน้าที่ 1 ถึง 50)
 * 2. แผ่นที่ 1 (20 Moodset) และ แผ่นที่ 2 (30 Moodset)
 * 3. ระบบค้นหาตามหน้า findByPage(1..50)
 * 4. ระบบถอดรหัสคำสั่งเสียง parsePageNumber (เลขอารบิก, เลขไทย, คำอ่านภาษาไทย, คำสั่งแชต /avatar)
 * 5. ระบบตรวจจับคำสั่งเล่นทุกหน้า isPlayAllCommand ("หน้าทั้งหมด", "เล่นทุกหน้า", "all moods")
 * 6. ระยะเวลาแสดงผลแต่ละหน้าอยู่ในเกณฑ์ 3-5 วินาที (3000ms - 5000ms)
 */
class MoodsetCatalogTest {

    @Test
    fun `LooiMoodsetCatalog contains exactly 50 items with sequential pages 1 to 50`() {
        val items = LooiMoodsetCatalog.ITEMS
        assertEquals(50, items.size, "Catalog must contain exactly 50 moodsets")

        // ตรวจสอบว่าหน้าที่ 1 ถึง 50 ครบและเรียงตามลำดับ
        for (i in 1..50) {
            val item = items[i - 1]
            assertEquals(i, item.pageNumber, "Item at index ${i - 1} must have pageNumber $i")
            assertNotNull(item.nameEn)
            assertTrue(item.nameEn.isNotBlank(), "Page $i nameEn must not be blank")
            assertNotNull(item.nameTh)
            assertTrue(item.nameTh.isNotBlank(), "Page $i nameTh must not be blank")
            assertNotNull(item.description)
            assertTrue(item.description.isNotBlank(), "Page $i description must not be blank")
            assertNotNull(item.emotion)
            // แสดงผล 3-5 วินาที
            assertTrue(item.durationMs in 3000L..5000L, "Page $i duration must be between 3-5 seconds")
        }
    }

    @Test
    fun `Sheet 1 contains 20 items and Sheet 2 contains 30 items`() {
        val sheet1 = LooiMoodsetCatalog.ITEMS.filter { it.sheet == 1 }
        val sheet2 = LooiMoodsetCatalog.ITEMS.filter { it.sheet == 2 }

        assertEquals(20, sheet1.size, "Sheet 1 (LOOI 20 MOODSET) must have 20 items")
        assertEquals(30, sheet2.size, "Sheet 2 (LOOI 30 ADDITIONAL MOODSET) must have 30 items")

        // Sheet 1 covers pages 1..20
        assertEquals(1, sheet1.first().pageNumber)
        assertEquals(20, sheet1.last().pageNumber)

        // Sheet 2 covers pages 21..50
        assertEquals(21, sheet2.first().pageNumber)
        assertEquals(50, sheet2.last().pageNumber)
    }

    @Test
    fun `findByPage returns correct item for valid pages and null for invalid`() {
        for (page in 1..50) {
            val item = LooiMoodsetCatalog.findByPage(page)
            assertNotNull(item, "Page $page must be found")
            assertEquals(page, item.pageNumber)
        }

        assertNull(LooiMoodsetCatalog.findByPage(0), "Page 0 should return null")
        assertNull(LooiMoodsetCatalog.findByPage(51), "Page 51 should return null")
        assertNull(LooiMoodsetCatalog.findByPage(-1), "Page -1 should return null")
        assertNull(LooiMoodsetCatalog.findByPage(100), "Page 100 should return null")
    }

    @Test
    fun `parsePageNumber parses standard Arabic digit commands`() {
        assertEquals(1, LooiMoodsetCatalog.parsePageNumber("หน้าที่ 1"))
        assertEquals(5, LooiMoodsetCatalog.parsePageNumber("หน้าที่ 5"))
        assertEquals(12, LooiMoodsetCatalog.parsePageNumber("หน้าที่12"))
        assertEquals(20, LooiMoodsetCatalog.parsePageNumber("หน้า 20"))
        assertEquals(21, LooiMoodsetCatalog.parsePageNumber("แบบที่ 21"))
        assertEquals(30, LooiMoodsetCatalog.parsePageNumber("แบบ 30"))
        assertEquals(45, LooiMoodsetCatalog.parsePageNumber("page 45"))
        assertEquals(50, LooiMoodsetCatalog.parsePageNumber("mood 50"))
        assertEquals(18, LooiMoodsetCatalog.parsePageNumber("face 18"))
        assertEquals(25, LooiMoodsetCatalog.parsePageNumber("moodset 25"))
    }

    @Test
    fun `parsePageNumber parses avatar slash commands`() {
        assertEquals(15, LooiMoodsetCatalog.parsePageNumber("/avatar 15"))
        assertEquals(24, LooiMoodsetCatalog.parsePageNumber("/avatar page 24"))
        assertEquals(50, LooiMoodsetCatalog.parsePageNumber("/avatar 50"))
    }

    @Test
    fun `parsePageNumber parses Thai numerals`() {
        assertEquals(1, LooiMoodsetCatalog.parsePageNumber("หน้าที่ ๑"))
        assertEquals(9, LooiMoodsetCatalog.parsePageNumber("หน้าที่ ๙"))
        assertEquals(20, LooiMoodsetCatalog.parsePageNumber("หน้าที่ ๒๐"))
        assertEquals(35, LooiMoodsetCatalog.parsePageNumber("หน้า ๓๕"))
        assertEquals(50, LooiMoodsetCatalog.parsePageNumber("หน้าที่ ๕๐"))
    }

    @Test
    fun `parsePageNumber parses spoken Thai number words`() {
        assertEquals(1, LooiMoodsetCatalog.parsePageNumber("หน้าที่หนึ่ง"))
        assertEquals(2, LooiMoodsetCatalog.parsePageNumber("หน้าที่สอง"))
        assertEquals(5, LooiMoodsetCatalog.parsePageNumber("หน้าที่ห้า"))
        assertEquals(10, LooiMoodsetCatalog.parsePageNumber("หน้าที่สิบ"))
        assertEquals(11, LooiMoodsetCatalog.parsePageNumber("หน้าที่สิบเอ็ด"))
        assertEquals(20, LooiMoodsetCatalog.parsePageNumber("หน้าที่ยี่สิบ"))
        assertEquals(21, LooiMoodsetCatalog.parsePageNumber("หน้าที่ยี่สิบเอ็ด"))
        assertEquals(22, LooiMoodsetCatalog.parsePageNumber("หน้าที่ยี่สิบสอง"))
        assertEquals(30, LooiMoodsetCatalog.parsePageNumber("หน้าที่สามสิบ"))
        assertEquals(42, LooiMoodsetCatalog.parsePageNumber("หน้าที่สี่สิบสอง"))
        assertEquals(50, LooiMoodsetCatalog.parsePageNumber("หน้าที่ห้าสิบ"))
    }

    @Test
    fun `parsePageNumber returns null for out of range or unrelated text`() {
        assertNull(LooiMoodsetCatalog.parsePageNumber("หน้าที่ 0"))
        assertNull(LooiMoodsetCatalog.parsePageNumber("หน้าที่ 51"))
        assertNull(LooiMoodsetCatalog.parsePageNumber("หน้าที่ 99"))
        assertNull(LooiMoodsetCatalog.parsePageNumber("สวัสดีตอนเช้า"))
        assertNull(LooiMoodsetCatalog.parsePageNumber("ราคาทองคำวันนี้เท่าไร"))
    }

    @Test
    fun `isPlayAllCommand recognizes Thai and English loop triggers`() {
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("หน้าทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("เล่นหน้าทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("แสดงหน้าทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("โชว์หน้าทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("ทดสอบหน้าทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("เล่นทุกหน้า"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("แสดงทุกหน้า"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("โชว์ moodset ทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("เล่น moodset ทั้งหมด"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("all moods"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("play all"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("show all"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("play all moods"))
        assertTrue(LooiMoodsetCatalog.isPlayAllCommand("/avatar all"))

        // Negatives
        assertFalse(LooiMoodsetCatalog.isPlayAllCommand("หน้าที่ 1"))
        assertFalse(LooiMoodsetCatalog.isPlayAllCommand("ยิ้มหน่อย"))
        assertFalse(LooiMoodsetCatalog.isPlayAllCommand("สวัสดี"))
    }

    @Test
    fun `Page 10 is Laughing and Page 11 is Music, and both exist distinctly`() {
        // ตรวจสอบหน้าที่ 10
        val page10 = LooiMoodsetCatalog.findByPage(10)
        assertNotNull(page10, "Page 10 must exist in catalog")
        assertEquals(10, page10.pageNumber)
        assertEquals("Laughing", page10.nameEn)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.LAUGHING, page10.emotion)
        assertTrue(page10.description.contains("หัวเราะ") || page10.nameTh.contains("หัวเราะ"))

        // ตรวจสอบหน้าที่ 11
        val page11 = LooiMoodsetCatalog.findByPage(11)
        assertNotNull(page11, "Page 11 must exist in catalog (AI must never claim page 11 does not exist)")
        assertEquals(11, page11.pageNumber)
        assertEquals("Music", page11.nameEn)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion.MUSIC, page11.emotion)
        assertTrue(page11.description.contains("หูฟัง") || page11.nameTh.contains("เพลง"))

        // ตรวจสอบคำสั่งเสียงตรงกับหน้า
        assertEquals(10, LooiMoodsetCatalog.parsePageNumber("หน้าที่ 10"))
        assertEquals(11, LooiMoodsetCatalog.parsePageNumber("หน้าที่ 11"))
    }

    @Test
    fun `All 50 Moodset items have dynamic background and foreground depth assigned`() {
        val items = LooiMoodsetCatalog.ITEMS
        for (item in items) {
            assertNotNull(item.backgroundTheme, "Page ${item.pageNumber} must have backgroundTheme")
            assertNotNull(item.foregroundEffect, "Page ${item.pageNumber} must have foregroundEffect")
        }

        // Test key representative 2.5D visual depth themes
        val p31 = LooiMoodsetCatalog.findByPage(31)!! // Cold
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.WINTER_BLIZZARD, p31.backgroundTheme)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect.FROST_VIGNETTE, p31.foregroundEffect)

        val p36 = LooiMoodsetCatalog.findByPage(36)!! // Space
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.SPACE_NEBULA, p36.backgroundTheme)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect.STAR_DUST, p36.foregroundEffect)

        val p37 = LooiMoodsetCatalog.findByPage(37)!! // Party
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.PARTY_CONFETTI, p37.backgroundTheme)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect.CONFETTI_TUMBLE, p37.foregroundEffect)

        val p45 = LooiMoodsetCatalog.findByPage(45)!! // Magic
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.MAGIC_MYSTIC, p45.backgroundTheme)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect.MAGIC_SPARKLES, p45.foregroundEffect)

        val p50 = LooiMoodsetCatalog.findByPage(50)!! // Low Battery
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme.LOW_POWER_CRT, p50.backgroundTheme)
        assertEquals(com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect.CRT_SCANLINES, p50.foregroundEffect)
    }
}
