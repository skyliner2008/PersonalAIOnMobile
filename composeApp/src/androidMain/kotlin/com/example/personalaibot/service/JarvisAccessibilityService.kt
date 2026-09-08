package com.example.personalaibot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
        const val ACTION_SERVICE_CONNECTED = "com.example.personalaibot.A11Y_CONNECTED"
        const val ACTION_SERVICE_DISCONNECTED = "com.example.personalaibot.A11Y_DISCONNECTED"
    }

    /**
     * ข้อมูล UI node ที่อ่านจากหน้าจอ — ส่งกลับให้ AI วิเคราะห์
     */
    data class ScreenNode(
        val text: String?,
        val contentDescription: String?,
        val className: String?,
        val viewId: String?,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isChecked: Boolean?,
        val bounds: Rect?,
        val childCount: Int
    ) {
        /** สรุปสั้นๆ สำหรับ AI */
        fun toSummary(): String = buildString {
            val label = text ?: contentDescription ?: ""
            if (label.isNotBlank()) append(label)
            if (isClickable) append(" [คลิกได้]")
            if (isEditable) append(" [แก้ไขได้]")
            if (isChecked != null) append(if (isChecked) " [✓]" else " [☐]")
            if (viewId != null) append(" (id:$viewId)")
        }
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

    // ─── Screen Reading ────────────────────────────────────────────────────

    /**
     * อ่าน UI tree ของหน้าจอปัจจุบัน — คืนเป็น list ของ ScreenNode
     * @param maxDepth ความลึกสูงสุดที่จะ traverse (กัน infinite loop)
     * @param textOnly ถ้า true จะเก็บเฉพาะ node ที่มีข้อความ
     */
    fun readScreen(maxDepth: Int = 10, textOnly: Boolean = true): List<ScreenNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<ScreenNode>()
        traverseNode(root, nodes, 0, maxDepth, textOnly)
        root.recycle()
        return nodes
    }

    /**
     * สรุปหน้าจอเป็นข้อความสำหรับ AI อ่าน
     */
    fun readScreenAsText(): String {
        val nodes = readScreen(textOnly = true)
        if (nodes.isEmpty()) return "ไม่สามารถอ่านหน้าจอได้ (หน้าจออาจล็อคอยู่)"

        return buildString {
            appendLine("📱 แอปปัจจุบัน: ${currentPackage ?: "ไม่ทราบ"}")
            appendLine("📄 Activity: ${currentActivity ?: "ไม่ทราบ"}")
            appendLine()
            appendLine("--- เนื้อหาบนหน้าจอ ---")
            nodes.forEachIndexed { i, node ->
                val summary = node.toSummary()
                if (summary.isNotBlank()) {
                    appendLine("${i + 1}. $summary")
                }
            }
        }
    }

    /**
     * ค้นหา node ที่มีข้อความตรงกัน
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val found = root.findAccessibilityNodeInfosByText(text) ?: emptyList()
        return found.toList()
    }

    /**
     * ค้นหา node ด้วย view ID (เช่น "com.google.android.apps.maps:id/search_omnibox")
     */
    fun findNodesById(viewId: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val found = root.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
        return found.toList()
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        result: MutableList<ScreenNode>,
        depth: Int,
        maxDepth: Int,
        textOnly: Boolean
    ) {
        if (depth > maxDepth) return

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank()

        if (!textOnly || hasContent) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            result.add(ScreenNode(
                text = text,
                contentDescription = desc,
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isChecked = if (node.isCheckable) node.isChecked else null,
                bounds = bounds,
                childCount = node.childCount
            ))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, result, depth + 1, maxDepth, textOnly)
            child.recycle()
        }
    }

    // ─── UI Automation ─────────────────────────────────────────────────────

    /**
     * คลิก node ที่มีข้อความตรงกับ text (หา match แรก)
     * @return true ถ้าคลิกสำเร็จ
     */
    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text) ?: return false
        for (node in nodes) {
            if (performClickOnNode(node)) {
                Log.d(TAG, "CLICK by text: '$text' — OK")
                root.recycle()
                return true
            }
        }
        root.recycle()
        Log.w(TAG, "CLICK by text: '$text' — ไม่พบ node ที่คลิกได้")
        return false
    }

    /**
     * คลิก node ด้วย view ID
     */
    fun clickById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        for (node in nodes) {
            if (performClickOnNode(node)) {
                Log.d(TAG, "CLICK by id: '$viewId' — OK")
                root.recycle()
                return true
            }
        }
        root.recycle()
        Log.w(TAG, "CLICK by id: '$viewId' — ไม่พบ node ที่คลิกได้")
        return false
    }

    /**
     * แตะตำแหน่งพิกัด (x, y) บนหน้าจอ ผ่าน gesture API
     * (ต้อง API 24+)
     */
    fun tapAtPosition(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "tapAtPosition requires API 24+")
            return false
        }
        Log.d(TAG, "TAP at ($x, $y)")
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "TAP at ($x, $y) — completed")
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "TAP at ($x, $y) — cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Swipe gesture (เลื่อนหน้าจอ)
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "swipe requires API 24+")
            return false
        }
        Log.d(TAG, "SWIPE ($startX,$startY) → ($endX,$endY)")
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
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
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
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
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
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
