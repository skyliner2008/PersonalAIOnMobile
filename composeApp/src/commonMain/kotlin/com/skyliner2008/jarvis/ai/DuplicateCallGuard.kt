package com.skyliner2008.jarvis.ai

/**
 * กันโมเดล Live เรียก tool เดิมด้วย args เดิมวนซ้ำในเทิร์นเดียวกัน
 * (logcat 2026-09-24 03:33: trading_price XAUUSD ถูกเรียก 7 ครั้งติดกันทุก 0.85 วิ ไม่พูดคำตอบเลย)
 *
 * เทิร์นใหม่ของผู้ใช้ (turn serial เปลี่ยน) ถามซ้ำได้ตามปกติ — pure logic เพื่อให้เทสต์ได้
 */
class DuplicateCallGuard(
    private val windowMs: Long = 20_000L,
    private val maxEntries: Int = 16
) {
    private data class Entry(val key: String, val turn: Int, val atMs: Long, val callId: String, var result: String?)

    private val entries = ArrayDeque<Entry>()

    sealed interface Decision {
        /** call ใหม่ — รันตามปกติ */
        data object Run : Decision
        /** เรียกซ้ำระหว่างที่ call เดิมยังไม่เสร็จ */
        data object StillRunning : Decision
        /** เรียกซ้ำหลังได้ผลแล้ว — ส่งผลเดิมกลับ */
        data class Repeat(val previousResult: String) : Decision
    }

    fun check(callId: String, name: String, args: Map<String, String>, turn: Int, nowMs: Long): Decision {
        prune(nowMs)
        val k = key(name, args)
        val prev = entries.lastOrNull { it.key == k && it.turn == turn } ?: run {
            entries.addLast(Entry(k, turn, nowMs, callId, null))
            while (entries.size > maxEntries) entries.removeFirst()
            return Decision.Run
        }
        val result = prev.result
        return if (result == null) Decision.StillRunning else Decision.Repeat(result)
    }

    /** อ้างด้วย callId — args อาจถูกแก้ระหว่างทาง (เช่น TF ที่ผู้ใช้ไม่ได้ขอถูกเปลี่ยนเป็น 15m) */
    fun recordResult(callId: String, result: String) {
        entries.lastOrNull { it.callId == callId }?.result = result
    }

    private fun prune(nowMs: Long) {
        while (entries.isNotEmpty() && nowMs - entries.first().atMs > windowMs) entries.removeFirst()
    }

    companion object {
        fun key(name: String, args: Map<String, String>): String =
            name + "|" + args.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }

        fun repeatNote(name: String, previousResult: String): String =
            "[DUPLICATE CALL] เรียก $name ด้วยค่าเดิมซ้ำในเทิร์นเดียวกัน — ผลไม่เปลี่ยน ห้ามเรียกซ้ำอีก " +
                "ให้พูดตอบผู้ใช้จากผลนี้ทันที\n\n$previousResult"

        fun stillRunningNote(name: String): String =
            "[DUPLICATE CALL] $name ด้วยค่าเดิมกำลังทำอยู่แล้ว — ห้ามเรียกซ้ำ รอผลเดิม"
    }
}
