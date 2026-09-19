package com.skyliner2008.jarvis.tools.device

/**
 * ScreenSnapshotFormatter — แปลงผลอ่านหน้าจอ (a11y tree) เป็นข้อความสั้นสำหรับ AI
 *
 * แยกจาก androidMain เพื่อให้ทดสอบได้ใน commonTest
 * รูปแบบแต่ละบรรทัด: `3. ยืนยัน [ปุ่ม] @(540,1820) id:btn_ok`
 * — `@(x,y)` คือจุดกึ่งกลางของ element ใช้ส่งต่อให้ device_tap(x, y) ได้ทันที
 *
 * 2026-09-19 — Phase A: ส่งพิกัด + ปุ่มไอคอน + เพดานจำนวน + หลายหน้าต่าง
 */
object ScreenSnapshotFormatter {

    const val DEFAULT_MAX_ELEMENTS = 80
    const val MAX_LABEL_CHARS = 60

    data class Element(
        val label: String?,
        val className: String?,
        val viewId: String?,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isChecked: Boolean?,
        val centerX: Int,
        val centerY: Int
    ) {
        val isInteractive: Boolean get() = isClickable || isEditable
    }

    data class Window(
        val title: String?,
        val packageName: String?,
        val isActive: Boolean,
        val elements: List<Element>
    )

    fun format(
        currentPackage: String?,
        currentActivity: String?,
        windows: List<Window>,
        maxElements: Int = DEFAULT_MAX_ELEMENTS
    ): String {
        val nonEmpty = windows.filter { it.elements.isNotEmpty() }
        if (nonEmpty.isEmpty()) return "ไม่สามารถอ่านหน้าจอได้ (หน้าจออาจล็อคอยู่)"

        val total = nonEmpty.sumOf { it.elements.size }
        var index = 0
        return buildString {
            appendLine("📱 แอปปัจจุบัน: ${currentPackage ?: "ไม่ทราบ"}")
            appendLine("📄 Activity: ${currentActivity ?: "ไม่ทราบ"}")
            appendLine("ℹ️ @(x,y) = จุดกึ่งกลาง ใช้กับ device_tap(x, y) ได้ทันที")
            for (window in nonEmpty) {
                if (index >= maxElements) break
                appendLine()
                appendLine(windowHeader(window, nonEmpty.size))
                for (element in window.elements) {
                    if (index >= maxElements) break
                    index++
                    appendLine("$index. ${summarize(element)}")
                }
            }
            if (total > index) {
                appendLine()
                append("…และอีก ${total - index} รายการที่ไม่ได้แสดง — ใช้ device_scroll แล้วอ่านใหม่ หากหาไม่เจอ")
            }
        }.trimEnd()
    }

    fun summarize(element: Element): String = buildString {
        val label = element.label?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
        when {
            label != null -> append(truncate(label))
            element.isInteractive -> append("(ไม่มีข้อความ ${shortClassName(element.className) ?: "View"})")
        }
        when {
            element.isEditable -> append(" [ช่องพิมพ์]")
            element.isClickable -> append(" [ปุ่ม]")
        }
        element.isChecked?.let { append(if (it) " [✓]" else " [☐]") }
        append(" @(${element.centerX},${element.centerY})")
        shortViewId(element.viewId)?.let { append(" id:$it") }
    }

    private fun windowHeader(window: Window, windowCount: Int): String {
        val name = window.title?.takeIf { it.isNotBlank() } ?: window.packageName ?: "หน้าต่าง"
        val tag = if (window.isActive) " (กำลังใช้งาน)" else ""
        return if (windowCount > 1) "--- หน้าต่าง: $name$tag ---" else "--- เนื้อหาบนหน้าจอ ---"
    }

    private fun truncate(text: String): String =
        if (text.length <= MAX_LABEL_CHARS) text else text.take(MAX_LABEL_CHARS - 1) + "…"

    /** `android.widget.ImageButton` → `ImageButton` */
    internal fun shortClassName(className: String?): String? =
        className?.substringAfterLast('.')?.takeIf { it.isNotBlank() }

    /** `com.whatsapp:id/send` → `send` (ชื่อเต็มยังใช้กับ device_tap(view_id) ได้ผ่าน executor) */
    internal fun shortViewId(viewId: String?): String? =
        viewId?.substringAfter(":id/")?.takeIf { it.isNotBlank() }
}
