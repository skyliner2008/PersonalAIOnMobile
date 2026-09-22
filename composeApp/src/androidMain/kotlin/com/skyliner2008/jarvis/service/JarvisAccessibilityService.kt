package com.skyliner2008.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.skyliner2008.jarvis.tools.device.ScreenSnapshotFormatter

/**
 * JarvisAccessibilityService — สมองกลที่ควบคุมมือถือทั้งเครื่องแทนผู้ใช้
 *
 * ความสามารถ:
 *  1. Global Actions — กด Back / Home / Recent / Notification / Quick Settings / Screenshot
 *  2. Screen Reader — อ่านข้อมูล/ข้อความบนหน้าจอปัจจุบัน (traverse UI tree)
 *  3. UI Automation — คลิกปุ่ม, พิมพ์ข้อความ, เลื่อนหน้าจอ ในแอปอื่น
 *  4. App Context — รู้ว่าผู้ใช้กำลังเปิดแอปอะไรอยู่
 *
 * หมายเหตุ: ผู้ใช้ต้องเปิด Accessibility Service ในตั้งค่า Android ด้วยตัวเอง (ครั้งเดียว)
 * Settings > Accessibility > JARVIS > เปิดใช้งาน
 *
 * 2026-09-06 — Initial implementation for JARVIS Device Control
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11y"

        /** Singleton reference — ใช้เช็คว่า service ทำงานอยู่หรือไม่ */
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        /** ตรวจสอบว่า Accessibility Service เปิดอยู่หรือไม่ */
        fun isEnabled(): Boolean = instance != null

        /** Broadcast action เมื่อ service เปิด/ปิด */
        const val ACTION_SERVICE_CONNECTED = "com.skyliner2008.jarvis.A11Y_CONNECTED"
        const val ACTION_SERVICE_DISCONNECTED = "com.skyliner2008.jarvis.A11Y_DISCONNECTED"
    }

    /** Package ของแอปที่ผู้ใช้เปิดอยู่ล่าสุด */
    @Volatile
    var currentPackage: String? = null
        private set

    /** ClassName ของ Activity ที่เปิดอยู่ */
    @Volatile
    var currentActivity: String? = null
        private set


    // ─── Lifecycle ──────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // อ่าน dialog / popup / แผงแจ้งเตือนที่อยู่คนละ window ได้ (ตั้งใน XML แล้ว — ย้ำที่นี่กันกรณี config เก่าค้าง)
        serviceInfo?.let { info ->
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        }
        Log.i(TAG, "✅ JarvisAccessibilityService CONNECTED — device control ready")
        sendBroadcast(Intent(ACTION_SERVICE_CONNECTED).setPackage(packageName))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Track ว่าผู้ใช้อยู่ที่แอปไหน
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let { pkg ->
                    currentPackage = pkg
                    currentActivity = event.className?.toString()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ JarvisAccessibilityService INTERRUPTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "❌ JarvisAccessibilityService DESTROYED")
        sendBroadcast(Intent(ACTION_SERVICE_DISCONNECTED).setPackage(packageName))
    }

    // ─── Global Actions ────────────────────────────────────────────────────

    fun pressBack(): Boolean {
        Log.d(TAG, "ACTION: pressBack")
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        Log.d(TAG, "ACTION: pressHome")
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun openRecents(): Boolean {
        Log.d(TAG, "ACTION: openRecents")
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openNotifications(): Boolean {
        Log.d(TAG, "ACTION: openNotifications")
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings(): Boolean {
        Log.d(TAG, "ACTION: openQuickSettings")
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun takeScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "takeScreenshot requires API 28+")
            return false
        }
        Log.d(TAG, "ACTION: takeScreenshot")
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun lockScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "lockScreen requires API 28+")
            return false
        }
        Log.d(TAG, "ACTION: lockScreen")
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    /**
     * จับภาพหน้าจอจริงแล้วคืน Bitmap ให้ AI ดู (API 30+)
     * ต่างจาก [takeScreenshot] ที่เป็น GLOBAL_ACTION — อันนั้นแค่เซฟลงแกลเลอรี ไม่คืนภาพ
     */
    fun captureScreenBitmap(callback: (android.graphics.Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "captureScreenBitmap requires API 30+")
            callback(null)
            return
        }
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = try {
                            val hardware = android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            // hardware bitmap อ่าน pixel ตรงๆ ไม่ได้ → copy เป็น ARGB_8888 ก่อนบีบ JPEG
                            hardware?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                .also { hardware?.recycle() }
                        } catch (e: Exception) {
                            Log.w(TAG, "screenshot convert failed: ${e.message}")
                            null
                        } finally {
                            buffer.close()
                        }
                        callback(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        // ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3 (เรียกถี่กว่า 1 ครั้ง/วินาที)
                        Log.w(TAG, "takeScreenshot failed: code=$errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "takeScreenshot threw: ${e.message}")
            callback(null)
        }
    }

    // ─── Screen Reading ────────────────────────────────────────────────────

    /**
     * Root ของทุกหน้าต่างที่ผู้ใช้โต้ตอบได้ เรียงจากชั้นบนสุดลงล่าง (dialog/popup มาก่อน)
     * ข้าม status bar / nav bar / overlay — ถ้าอ่าน windows ไม่ได้ ใช้ rootInActiveWindow แทน
     * ผู้เรียกต้อง recycle root ที่ได้เอง
     */
    private fun interactiveRoots(): List<Pair<AccessibilityWindowInfo?, AccessibilityNodeInfo>> {
        val result = mutableListOf<Pair<AccessibilityWindowInfo?, AccessibilityNodeInfo>>()
        val allWindows = try { windows } catch (e: Exception) { emptyList<AccessibilityWindowInfo>() }
        allWindows
            .filter { w ->
                w.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                    (w.type == AccessibilityWindowInfo.TYPE_SYSTEM && (w.isActive || w.isFocused))
            }
            .sortedByDescending { it.layer }
            .forEach { w -> w.root?.let { result += w to it } }

        // วิดเจ็ตลอยของ JARVIS เองไม่ใช่เนื้อหาที่ผู้ใช้สนใจ — ตัดออกถ้ายังมีหน้าต่างแอปอื่นให้อ่าน
        val others = result.filter { it.second.packageName?.toString() != packageName }
        if (others.isNotEmpty() && others.size < result.size) {
            result.filterNot { it in others }.forEach { it.second.recycle() }
            return others
        }

        if (result.isEmpty()) rootInActiveWindow?.let { result += null to it }
        return result
    }

    /**
     * อ่าน UI tree ของทุกหน้าต่างที่โต้ตอบได้ — เก็บ node ที่มีข้อความ หรือกด/พิมพ์ได้ (รวมปุ่มไอคอนที่ไม่มีข้อความ)
     * @param maxDepth ความลึกสูงสุดที่จะ traverse (Compose/RecyclerView ลึกเกิน 10 ชั้นบ่อย)
     */
    fun readScreen(maxDepth: Int = 30): List<ScreenSnapshotFormatter.Window> =
        interactiveRoots().map { (window, root) ->
            val elements = mutableListOf<ScreenSnapshotFormatter.Element>()
            traverseNode(root, elements, 0, maxDepth, coveredByAncestor = false)
            val snapshot = ScreenSnapshotFormatter.Window(
                title = window?.title?.toString(),
                packageName = root.packageName?.toString(),
                isActive = window?.isActive ?: true,
                elements = elements
            )
            root.recycle()
            snapshot
        }

    /**
     * สรุปหน้าจอเป็นข้อความสำหรับ AI อ่าน — มีพิกัด @(x,y) ทุก element และจำกัดจำนวนบรรทัด
     */
    fun readScreenAsText(maxElements: Int = ScreenSnapshotFormatter.DEFAULT_MAX_ELEMENTS): String =
        ScreenSnapshotFormatter.format(currentPackage, currentActivity, readScreen(), maxElements)

    /**
     * @param coveredByAncestor true เมื่อ parent ที่กดได้ยืมข้อความของลูกไปเป็นชื่อปุ่มแล้ว
     *        → ลูกที่เป็นแค่ข้อความ (กดไม่ได้) จะไม่ถูกแสดงซ้ำ
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<ScreenSnapshotFormatter.Element>,
        depth: Int,
        maxDepth: Int,
        coveredByAncestor: Boolean
    ) {
        if (depth > maxDepth) return
        if (!node.isVisibleToUser) return

        val ownLabel = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .firstOrNull { it.isNotBlank() }
        val interactive = node.isClickable || node.isEditable
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        var coverChildren = coveredByAncestor

        if (!bounds.isEmpty && (interactive || (ownLabel != null && !coveredByAncestor))) {
            // ปุ่มที่ไม่มีข้อความของตัวเอง (เช่น LinearLayout ห่อ TextView) → ยืมข้อความจากลูก
            val label = ownLabel ?: if (node.isClickable) descendantLabel(node) else null
            if (ownLabel == null && label != null) coverChildren = true
            result += ScreenSnapshotFormatter.Element(
                label = label,
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isChecked = if (node.isCheckable) node.isChecked else null,
                centerX = bounds.centerX(),
                centerY = bounds.centerY()
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, result, depth + 1, maxDepth, coverChildren)
            child.recycle()
        }
    }

    /** รวมข้อความจากลูกหลานสูงสุด 3 ชิ้น (ลึก 4 ชั้น) เพื่อใช้เป็นชื่อปุ่ม */
    private fun descendantLabel(node: AccessibilityNodeInfo): String? {
        val parts = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo, depth: Int) {
            if (parts.size >= 3 || depth > 4) return
            for (i in 0 until n.childCount) {
                if (parts.size >= 3) return
                val child = n.getChild(i) ?: continue
                val text = listOfNotNull(child.text?.toString(), child.contentDescription?.toString())
                    .firstOrNull { it.isNotBlank() }
                if (text != null) parts += text.trim() else collect(child, depth + 1)
                child.recycle()
            }
        }
        collect(node, 1)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    // ─── UI Automation ─────────────────────────────────────────────────────

    /**
     * คลิก node ที่มีข้อความตรงกับ text — ค้นทุกหน้าต่าง เริ่มจากชั้นบนสุด (dialog ก่อน)
     * @return true ถ้าคลิกสำเร็จ
     */
    fun clickByText(text: String): Boolean =
        clickFirst("text '$text'") { root -> root.findAccessibilityNodeInfosByText(text) }

    /**
     * คลิก node ด้วย view ID — รับได้ทั้งแบบเต็ม (`pkg:id/name`) และแบบสั้น (`name` ตามที่ read_screen แสดง)
     */
    fun clickById(viewId: String): Boolean =
        clickFirst("id '$viewId'") { root ->
            val fullId = if (viewId.contains(":id/")) viewId else "${root.packageName}:id/$viewId"
            root.findAccessibilityNodeInfosByViewId(fullId)
        }

    private fun clickFirst(what: String, find: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>?): Boolean {
        val roots = interactiveRoots()
        try {
            for ((_, root) in roots) {
                val nodes = find(root) ?: continue
                for (node in nodes) {
                    if (node.isVisibleToUser && performClickOnNode(node)) {
                        Log.d(TAG, "CLICK by $what — OK")
                        return true
                    }
                }
            }
        } finally {
            roots.forEach { it.second.recycle() }
        }
        Log.w(TAG, "CLICK by $what — ไม่พบ node ที่คลิกได้")
        return false
    }

    /**
     * แตะตำแหน่งพิกัด (x, y) บนหน้าจอ ผ่าน gesture API
     * (ต้อง API 24+)
     */
    fun tapAtPosition(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        Log.d(TAG, "TAP at ($x, $y)")
        return dispatchStroke(Path().apply { moveTo(x, y) }, 100, "TAP ($x, $y)", callback)
    }

    /**
     * Swipe gesture (เลื่อนหน้าจอ)
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300, callback: ((Boolean) -> Unit)? = null): Boolean {
        Log.d(TAG, "SWIPE ($startX,$startY) → ($endX,$endY)")
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return dispatchStroke(path, durationMs, "SWIPE", callback)
    }

    /**
     * ส่ง gesture เส้นเดียว — รับประกันว่า callback ถูกเรียก "ครั้งเดียวเสมอ" แม้ dispatch ไม่ผ่าน
     * (เดิม dispatchGesture คืน false โดยไม่เรียก callback → coroutine ฝั่ง executor ค้างถาวร)
     */
    private fun dispatchStroke(path: Path, durationMs: Long, label: String, callback: ((Boolean) -> Unit)?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "$label requires API 24+")
            callback?.invoke(false)
            return false
        }
        val dispatched = try {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "$label — cancelled")
                    callback?.invoke(false)
                }
            }, null)
        } catch (e: IllegalArgumentException) {
            // พิกัดติดลบ / path ไม่ถูกต้อง — StrokeDescription โยน exception
            Log.w(TAG, "$label — invalid gesture: ${e.message}")
            false
        }
        if (!dispatched) {
            Log.w(TAG, "$label — dispatchGesture rejected")
            callback?.invoke(false)
        }
        return dispatched
    }

    /**
     * กดค้าง (long press) ที่พิกัด — เปิดเมนูบริบท เลือกข้อความ ลากไอคอน ฯลฯ
     */
    fun longPressAtPosition(x: Float, y: Float, durationMs: Long = 800, callback: ((Boolean) -> Unit)? = null): Boolean {
        Log.d(TAG, "LONG PRESS at ($x, $y) ${durationMs}ms")
        return dispatchStroke(Path().apply { moveTo(x, y) }, durationMs, "LONG PRESS ($x, $y)", callback)
    }

    /**
     * ปัดหน้าจอตามทิศทาง โดยอิงสัดส่วนของจอ (ไม่ต้องให้ AI คำนวณพิกัดเอง)
     * @param direction up / down / left / right — ทิศที่ "นิ้วลาก" ไป
     */
    fun swipeDirection(direction: String, durationMs: Long = 350, callback: ((Boolean) -> Unit)? = null): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        return when (direction) {
            "up", "ขึ้น" -> swipe(w * 0.5f, h * 0.75f, w * 0.5f, h * 0.25f, durationMs, callback)
            "down", "ลง" -> swipe(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.75f, durationMs, callback)
            "left", "ซ้าย" -> swipe(w * 0.8f, h * 0.5f, w * 0.2f, h * 0.5f, durationMs, callback)
            "right", "ขวา" -> swipe(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f, durationMs, callback)
            else -> {
                Log.w(TAG, "swipeDirection: ทิศทางไม่รองรับ '$direction'")
                callback?.invoke(false)
                false
            }
        }
    }

    /**
     * เลื่อนเนื้อหา "ภายใน list/scroll view" ด้วย ACTION_SCROLL_FORWARD/BACKWARD
     * แม่นยำกว่าการปัดกลางจอ (ไม่ไปโดนปุ่มหรือ pull-to-refresh) — คืน false ถ้าไม่เจอ node ที่เลื่อนได้
     */
    fun scrollNode(forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val roots = interactiveRoots()
        try {
            for ((_, root) in roots) {
                // เดิมใช้ node ที่เลื่อนได้ "ตัวแรกที่เจอ" ซึ่งมักเป็นแถบชิปแนวนอนด้านบน (เช่นตัวกรองใน YouTube)
                // สั่งแล้ว performAction คืน true แต่หน้าจอหลักไม่ขยับ — พบจากทดสอบจริง 2026-09-20
                val target = findMainScrollable(root) ?: continue
                val ok = target.performAction(action)
                target.recycle()
                if (ok) {
                    Log.d(TAG, "SCROLL node ${if (forward) "forward" else "backward"} — OK")
                    return true
                }
            }
        } finally {
            roots.forEach { it.second.recycle() }
        }
        return false
    }

    /**
     * หา list หลักของหน้า: node ที่เลื่อนได้ "แนวตั้ง" และกินพื้นที่มากที่สุด
     * (ข้ามแถบเลื่อนแนวนอนอย่างแถบชิป/แถบสตอรี่ที่เตี้ยกว่ากว้าง)
     */
    private fun findMainScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 30) return
            if (node.isScrollable && node.isVisibleToUser) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val area = bounds.width() * bounds.height()
                if (bounds.height() >= bounds.width() / 2 && area > bestArea) {
                    best?.recycle()
                    best = AccessibilityNodeInfo.obtain(node)
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
                child.recycle()
            }
        }

        visit(root, 0)
        return best
    }

    /**
     * กดปุ่ม "ส่ง/ค้นหา/Enter" บนคีย์บอร์ดของช่องพิมพ์ที่โฟกัสอยู่ (API 30+)
     * ใช้ต่อจาก typeText เพื่อส่งข้อความจริง ไม่ใช่แค่พิมพ์ค้างไว้
     */
    fun pressImeAction(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "pressImeAction requires API 30+")
            return false
        }
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
            Log.w(TAG, "pressImeAction: ไม่พบช่องพิมพ์ที่โฟกัสอยู่")
            return false
        }
        val result = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        node.recycle()
        Log.d(TAG, "IME ENTER — ${if (result) "OK" else "FAIL"}")
        return result
    }

    /**
     * เลื่อนลง (Scroll Down) — ใช้ swipe จาก 70% ของจอ ขึ้น 30%
     */
    fun scrollDown(callback: ((Boolean) -> Unit)? = null): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.7f
        val endY = displayMetrics.heightPixels * 0.3f
        return swipe(centerX, startY, centerX, endY, 400, callback)
    }

    /**
     * เลื่อนขึ้น (Scroll Up)
     */
    fun scrollUp(callback: ((Boolean) -> Unit)? = null): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.3f
        val endY = displayMetrics.heightPixels * 0.7f
        return swipe(centerX, startY, centerX, endY, 400, callback)
    }

    /**
     * พิมพ์ข้อความลงในช่อง input ที่ focus อยู่
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // service-level findFocus: ค้นทุกหน้าต่าง (ช่องพิมพ์ใน dialog)
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "TYPE text: '${text.take(50)}...' — ${if (result) "OK" else "FAIL"}")
            focusedNode.recycle()
            root.recycle()
            return result
        }

        // Fallback: หาช่อง editable แรกที่เจอ
        val editableNode = findFirstEditable(root)
        if (editableNode != null) {
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "TYPE text (fallback): '${text.take(50)}...' — ${if (result) "OK" else "FAIL"}")
            editableNode.recycle()
            root.recycle()
            return result
        }

        root.recycle()
        Log.w(TAG, "TYPE text: ไม่พบช่อง input ที่แก้ไขได้")
        return false
    }

    /**
     * ล้างข้อความในช่อง input ที่ focus อยู่ แล้วพิมพ์ข้อความใหม่
     */
    fun clearAndType(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // service-level findFocus: ค้นทุกหน้าต่าง (ช่องพิมพ์ใน dialog)
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
            root.recycle()
            return false
        }
        if (!focusedNode.isEditable) {
            focusedNode.recycle()
            root.recycle()
            return false
        }
        // ล้างก่อน
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        // พิมพ์ใหม่
        val typeArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typeArgs)
        focusedNode.recycle()
        root.recycle()
        return result
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * คลิก node หรือ parent ที่ clickable ที่ใกล้ที่สุด
     */
    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // ไต่ขึ้น parent จนเจอ clickable
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val grandParent = parent.parent
            parent.recycle()
            parent = grandParent
            depth++
        }
        parent?.recycle()
        return false
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditable(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * ข้อมูลแอปที่เปิดอยู่ — ส่งกลับให้ AI
     */
    fun getAppContext(): String = buildString {
        appendLine("📱 Package: ${currentPackage ?: "N/A"}")
        appendLine("📄 Activity: ${currentActivity ?: "N/A"}")
    }
}
