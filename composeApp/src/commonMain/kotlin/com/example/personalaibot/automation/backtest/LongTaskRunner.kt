package com.example.personalaibot.automation.backtest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * LongTaskRunner — "multi-session" สำหรับคำสั่งที่ใช้เวลานาน (backtest/optimize/evolve)
 *
 * ปัญหาเดิม: tool backtest บล็อก turn ของ AI หลายนาที — ผู้ใช้ไม่รู้ว่ายังทำงานอยู่หรือค้าง
 * วิธีใหม่: tool ตอบ ack ทันที ("รับคำสั่งแล้ว กำลังทำในพื้นหลัง") แล้วงานจริงรันใน scope แยก
 * เมื่อเสร็จ → emit Completion ให้ JarvisViewModel ส่งต่อ:
 *   - live ยังเปิดอยู่ → ส่งข้อความเข้า live session ให้ AI พูดสรุป + การ์ดลงแชท
 *   - live ปิดแล้ว → แจ้งเตือน notification + พูดสรุปผ่าน alert voice chain (one-shot announce)
 */
object LongTaskRunner {

    data class Completion(
        val id: Long,
        val kind: String,        // backtest | optimize | evolve
        val title: String,       // เช่น "Backtest XAUUSD (all TF)"
        val chatBody: String,    // รายละเอียดเต็มสำหรับการ์ดในแชท
        val chatMeta: String,    // metadata json ของข้อความแชท
        val speech: String,      // สรุปสั้นสำหรับพูด/notification
        val ok: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _completions = MutableSharedFlow<Completion>(extraBufferCapacity = 8)
    val completions: SharedFlow<Completion> = _completions.asSharedFlow()

    /** งานที่กำลังรันอยู่ (id → title) — ให้ UI แสดงสถานะได้ */
    private val _running = MutableStateFlow<Map<Long, String>>(emptyMap())
    val running: StateFlow<Map<Long, String>> = _running.asStateFlow()

    // กัน tool call ซ้ำจาก Live model / reconnect: งานเดียวกันที่ยังรันอยู่ต้องมีเพียง 1 instance
    // key ผูกกับ kind + title ซึ่ง caller สร้างจาก symbol/TF/strategy/apply แล้ว
    private val activeKeys = mutableMapOf<String, Long>()

    /**
     * เริ่มงานพื้นหลัง — block คืน (chatBody, speech)
     * คืน task id ทันที (caller เอาไปตอบ ack ให้ผู้ใช้ได้เลย)
     */
    fun launch(kind: String, title: String, block: suspend () -> Pair<String, String>): Long {
        val key = "$kind|${title.trim().lowercase()}"
        synchronized(activeKeys) {
            activeKeys[key]?.let { existingId ->
                com.example.personalaibot.logDebug("LongTask", "♻️ DEDUP: ข้ามงานซ้ำ [$kind] $title — ใช้งาน #$existingId ที่กำลังรันอยู่")
                return existingId
            }
        }
        val id = Clock.System.now().toEpochMilliseconds() * 1000 + Random.nextInt(1000)
        synchronized(activeKeys) { activeKeys[key] = id }
        _running.value = _running.value + (id to title)
        com.example.personalaibot.logDebug("LongTask", "▶ เริ่มงานพื้นหลัง #$id [$kind] $title (กำลังรัน ${_running.value.size} งาน)")
        scope.launch {
            val t0 = Clock.System.now().toEpochMilliseconds()
            val (body, speech, ok) = try {
                val (b, s) = block()
                Triple(b, s, true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.example.personalaibot.logError("LongTask", "❌ งาน #$id [$kind] พัง: ${e.message}", e)
                Triple("❌ **$title** ไม่สำเร็จ: ${e.message}", "งาน $title ไม่สำเร็จครับ", false)
            }
            synchronized(activeKeys) {
                if (activeKeys[key] == id) activeKeys.remove(key)
            }
            _running.value = _running.value - id
            val elapsed = Clock.System.now().toEpochMilliseconds() - t0
            com.example.personalaibot.logDebug("LongTask", "✅ งาน #$id [$kind] เสร็จใน ${elapsed}ms — ส่งต่อให้ผู้ใช้")
            _completions.emit(Completion(id, kind, title, body, buildChatMeta(kind, id), speech, ok))
        }
        return id
    }

    private fun buildChatMeta(kind: String, id: Long): String =
        """{"type":"long_task","kind":"$kind","task_id":$id}"""
}
