package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.device.ScreenSnapshotFormatter
import com.skyliner2008.jarvis.tools.device.ScreenSnapshotFormatter.Element
import com.skyliner2008.jarvis.tools.device.ScreenSnapshotFormatter.Window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenSnapshotFormatterTest {

    private fun el(
        label: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        className: String? = "android.widget.TextView",
        viewId: String? = null,
        x: Int = 100,
        y: Int = 200
    ) = Element(label, className, viewId, clickable, editable, null, x, y)

    @Test
    fun summaryIncludesTapCoordinates() {
        val line = ScreenSnapshotFormatter.summarize(
            el("ยืนยัน", clickable = true, viewId = "com.app:id/btn_ok", x = 540, y = 1820)
        )
        assertEquals("ยืนยัน [ปุ่ม] @(540,1820) id:btn_ok", line)
    }

    @Test
    fun iconButtonWithoutTextIsStillListed() {
        val line = ScreenSnapshotFormatter.summarize(
            el(null, clickable = true, className = "android.widget.ImageButton", x = 980, y = 2100)
        )
        assertEquals("(ไม่มีข้อความ ImageButton) [ปุ่ม] @(980,2100)", line)
    }

    @Test
    fun editableIsMarkedAsInputNotButton() {
        val line = ScreenSnapshotFormatter.summarize(el("พิมพ์ข้อความ", clickable = true, editable = true))
        assertTrue(line.contains("[ช่องพิมพ์]"))
        assertFalse(line.contains("[ปุ่ม]"))
    }

    @Test
    fun longLabelsAndWhitespaceAreCompacted() {
        val line = ScreenSnapshotFormatter.summarize(el("a\n\n  b " + "x".repeat(200)))
        assertTrue(line.startsWith("a b x"))
        assertTrue(line.substringBefore(" @(").length <= ScreenSnapshotFormatter.MAX_LABEL_CHARS)
    }

    @Test
    fun outputIsCappedAndReportsRemainder() {
        val elements = (1..120).map { el("item $it") }
        val text = ScreenSnapshotFormatter.format("com.app", "Main", listOf(Window(null, "com.app", true, elements)), maxElements = 80)
        assertTrue(text.contains("80. item 80"))
        assertFalse(text.contains("81. item 81"))
        assertTrue(text.contains("…และอีก 40 รายการ"))
    }

    @Test
    fun dialogWindowIsListedFirstWithHeaders() {
        val dialog = Window("ขอสิทธิ์", "com.android.permissioncontroller", true, listOf(el("อนุญาต", clickable = true)))
        val app = Window(null, "com.app", false, listOf(el("หน้าหลัก")))
        val text = ScreenSnapshotFormatter.format("com.app", "Main", listOf(dialog, app))
        assertTrue(text.indexOf("--- หน้าต่าง: ขอสิทธิ์ (กำลังใช้งาน) ---") < text.indexOf("--- หน้าต่าง: com.app ---"))
        assertTrue(text.contains("1. อนุญาต [ปุ่ม]"))
        assertTrue(text.contains("2. หน้าหลัก"))
    }

    @Test
    fun graphicalScreenTellsAiToUseScreenshot() {
        // หน้าแบบ Google Maps: ปุ่มไอคอนล้วน อ่าน a11y tree แล้วไม่รู้ว่าบนจอมีอะไร
        val icons = (1..20).map { el(null, clickable = true, className = "android.widget.ImageView") }
        val text = ScreenSnapshotFormatter.format("com.google.android.apps.maps", "MapsActivity", listOf(Window(null, "com.google.android.apps.maps", true, icons)))
        assertTrue(text.contains("device_screenshot"))
    }

    @Test
    fun textScreenDoesNotSuggestScreenshot() {
        val rows = (1..20).map { el("ข้อความที่ $it") }
        val text = ScreenSnapshotFormatter.format("com.app", "Main", listOf(Window(null, "com.app", true, rows)))
        assertFalse(text.contains("device_screenshot"))
    }

    @Test
    fun emptyScreenReturnsLockedMessage() {
        val text = ScreenSnapshotFormatter.format(null, null, listOf(Window(null, null, true, emptyList())))
        assertTrue(text.startsWith("ไม่สามารถอ่านหน้าจอได้"))
    }
}

class TypingIntentGuardTest {

    @Test
    fun plainOpenAppHasNoTypingIntent() {
        // เคสจริง 2026-09-20: พูดแค่ "เปิด YouTube" แต่โมเดลพิมพ์คำค้นหาเก่าให้เอง
        assertFalse(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("เปิด YouTube"))
        assertFalse(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("เปิดแผนที่ให้หน่อย"))
        assertFalse(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("เลื่อนหน้าจอลงมา"))
    }

    @Test
    fun explicitTypingCommandsPassGuard() {
        assertTrue(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("พิมพ์ในช่องค้นหาว่าเพลงลูกทุ่ง"))
        assertTrue(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("เปิด YouTube แล้วค้นหาเพลงลูกทุ่ง"))
        assertTrue(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("ตอบไลน์ว่ากำลังไป"))
        assertTrue(com.skyliner2008.jarvis.ai.LiveIntentMatchers.hasTypingIntent("search for jazz music"))
    }
}
