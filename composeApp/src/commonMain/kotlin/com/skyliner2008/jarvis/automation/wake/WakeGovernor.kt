package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * การตั้งค่าของระบบปลุก — เก็บใน AppSetting (อยู่ใน backup/export ด้วย)
 */
object WakeSettings {
    private const val KEY_DISABLED = "wake.disabled"
    private const val KEY_HOURLY = "wake.budget.hourly"
    private const val KEY_DAILY = "wake.budget.daily"
    private const val KEY_COOLDOWN = "wake.cooldown.bars"
    private const val KEY_MEMORY_PREFIX = "wake.mem."

    /**
     * งบรายวันเริ่มต้น — อิงโควตา Flash Lite 2 โมเดล × 500 RPD = 1,000
     * เผื่อไว้ 10% ให้การใช้งานอื่น (แชท / alert ประเภทอื่น)
     */
    const val DEFAULT_DAILY = 900
    const val DEFAULT_HOURLY = 6
    const val DEFAULT_COOLDOWN_BARS = 3

    private fun get(key: String): String? = runCatching {
        JarvisDatabaseHolder.database?.jarvisDatabaseQueries?.getSetting(key)?.executeAsOneOrNull()
    }.getOrNull()

    private fun put(key: String, value: String) {
        runCatching { JarvisDatabaseHolder.database?.jarvisDatabaseQueries?.insertSetting(key, value) }
    }

    /** ปัจจัยที่ผู้ใช้ปิดเอง (ต่างจาก "ลดชั้น" ที่ระบบเรียนรู้ตัดสิน) */
    var disabled: Set<String>
        get() = get(KEY_DISABLED)?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
        set(v) = put(KEY_DISABLED, v.joinToString(","))

    var hourlyBudget: Int
        get() = get(KEY_HOURLY)?.toIntOrNull()?.coerceIn(1, 60) ?: DEFAULT_HOURLY
        set(v) = put(KEY_HOURLY, v.coerceIn(1, 60).toString())

    var dailyBudget: Int
        get() = get(KEY_DAILY)?.toIntOrNull()?.coerceIn(1, 5000) ?: DEFAULT_DAILY
        set(v) = put(KEY_DAILY, v.coerceIn(1, 5000).toString())

    var cooldownBars: Int
        get() = get(KEY_COOLDOWN)?.toIntOrNull()?.coerceIn(0, 50) ?: DEFAULT_COOLDOWN_BARS
        set(v) = put(KEY_COOLDOWN, v.coerceIn(0, 50).toString())

    private const val KEY_USAGE = "wake.usage"

    /** ยอดปลุกของวัน (วัน UTC, จำนวนครั้ง) — ให้งบรายวันคงอยู่แม้บริการรีสตาร์ท */
    fun loadUsage(): Pair<Long, Int>? = get(KEY_USAGE)?.split(":")?.takeIf { it.size == 2 }?.let { (d, c) ->
        val day = d.toLongOrNull() ?: return@let null
        val count = c.toIntOrNull() ?: return@let null
        day to count
    }

    fun saveUsage(day: Long, count: Int) = put(KEY_USAGE, "$day:$count")

    /** ความจำระหว่างรอบสแกน (ค่าก่อนหน้าสำหรับตรวจ "การตัดผ่าน") ต่อ symbol@tf */
    fun loadMemory(key: String): Map<String, String> = runCatching {
        val raw = get(KEY_MEMORY_PREFIX + key) ?: return emptyMap()
        Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrElse { emptyMap() }

    fun saveMemory(key: String, mem: Map<String, String>) {
        put(KEY_MEMORY_PREFIX + key, JsonObject(mem.mapValues { JsonPrimitive(it.value) }).toString())
    }

    /** ปัจจัยที่เปิดใช้ตรวจจับ (ทุกตัว ยกเว้นที่ผู้ใช้ปิด) */
    fun enabledIds(): Set<String> {
        val off = disabled
        return WakeTriggerRegistry.ALL.map { it.id.uppercase() }.filter { it !in off }.toSet()
    }
}

/**
 * WakeGovernor — คุมจังหวะการปลุก AI ให้อยู่ในงบโทเคน
 *
 * 1. **Cooldown รายปัจจัย** — ปัจจัยเดิมทิศเดิม ยิงซ้ำภายใน N แท่งไม่นับ
 * 2. **State → ใช้เฉพาะตอนเปลี่ยน** — สภาวะที่ค้างหลายแท่งถูกบันทึกเข้าการเรียนรู้แค่ครั้งแรก
 * 3. **Batching** — เหตุการณ์ทั้งหมดในแท่งเดียวกัน = ปลุก 1 ครั้ง (ไม่ปลุกซ้ำในแท่งเดิม)
 * 4. **เพดาน** — ต่อชั่วโมงต่อสินทรัพย์ และรวมทั้งวัน
 *
 * สถานะต่อ series (cooldown + แท่งที่ปลุกล่าสุด) ถูกเก็บลง memory ของ series ผ่าน [exportState] / [seed]
 * และยอดใช้รายวันเก็บใน AppSetting — เดิมอยู่ในหน่วยความจำอย่างเดียว บริการรีสตาร์ทเมื่อไร
 * งบรายวันกลับเป็นศูนย์ และแท่งที่เพิ่งปลุกไปถูกปลุกซ้ำ
 */
object WakeGovernor {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    internal const val MEM_LAST_WAKE = "gov_last_wake"
    internal const val MEM_FIRED = "gov_fired"
    private const val MAX_FIRED_ENTRIES = 300

    private val lastFired = mutableMapOf<String, Long>()          // series|trigger|dir → bar ts
    private val lastWakeBar = mutableMapOf<String, Long>()        // series → bar ts
    private val wakesPerSymbol = mutableMapOf<String, MutableList<Long>>()
    private val seeded = mutableSetOf<String>()
    private var dayKey = -1L
    private var dayCount = 0
    private var usageLoaded = false

    /** คืนสถานะที่บันทึกไว้ของ series (ครั้งแรกที่เห็น series นี้ใน process) */
    @Synchronized
    fun seed(seriesKey: String, memory: Map<String, String>) {
        if (!seeded.add(seriesKey)) return
        memory[MEM_LAST_WAKE]?.toLongOrNull()?.let { if (seriesKey !in lastWakeBar) lastWakeBar[seriesKey] = it }
        memory[MEM_FIRED]?.split(",")?.forEach { e ->
            val at = e.lastIndexOf('@')
            if (at <= 0) return@forEach
            val ts = e.substring(at + 1).toLongOrNull() ?: return@forEach
            val k = "$seriesKey|${e.substring(0, at)}"
            if (k !in lastFired) lastFired[k] = ts
        }
    }

    /** สถานะของ series สำหรับเก็บลง memory — ตัดรายการที่พ้น cooldown แล้วทิ้ง */
    @Synchronized
    fun exportState(seriesKey: String, barTs: Long, tfMs: Long): Map<String, String> {
        val prefix = "$seriesKey|"
        val keep = (WakeSettings.cooldownBars + 1) * tfMs
        val fired = lastFired.entries
            .filter { it.key.startsWith(prefix) && barTs - it.value <= keep }
            .sortedByDescending { it.value }
            .take(MAX_FIRED_ENTRIES)
            .joinToString(",") { "${it.key.removePrefix(prefix)}@${it.value}" }
        return buildMap {
            lastWakeBar[seriesKey]?.let { put(MEM_LAST_WAKE, it.toString()) }
            put(MEM_FIRED, fired)
        }
    }

    /**
     * เหตุการณ์ที่ผ่าน cooldown แล้ว
     * @param commit false = ดูอย่างเดียว ไม่บันทึกว่ายิงแล้ว (ใช้กับการสแกนด้วยมือจากแชท
     *   — เดิมการสแกนด้วยมือ "กิน" cooldown และการปลุกของแท่งนั้นไป alert เบื้องหลังจึงเงียบ)
     */
    @Synchronized
    fun freshEvents(seriesKey: String, events: List<TriggerEvent>, barTs: Long, tfMs: Long, commit: Boolean = true): List<TriggerEvent> {
        val cd = WakeSettings.cooldownBars * tfMs
        return events.filter { e ->
            val k = "$seriesKey|${e.triggerId}|${e.direction}"
            val prev = lastFired[k]
            val ok = prev == null || barTs - prev > cd
            if (ok && commit) lastFired[k] = barTs
            ok
        }
    }

    data class WakeDecision(val allowed: Boolean, val reason: String?)

    private fun ensureUsage(nowMs: Long) {
        if (!usageLoaded) {
            usageLoaded = true
            WakeSettings.loadUsage()?.let { (day, count) -> dayKey = day; dayCount = count }
        }
        val today = nowMs / DAY_MS
        if (today != dayKey) { dayKey = today; dayCount = 0 }
    }

    @Synchronized
    fun decide(seriesKey: String, symbol: String, barTs: Long, nowMs: Long): WakeDecision {
        if (lastWakeBar[seriesKey] == barTs) return WakeDecision(false, "ปลุกไปแล้วในแท่งนี้")
        ensureUsage(nowMs)
        if (dayCount >= WakeSettings.dailyBudget) return WakeDecision(false, "ครบงบรายวัน ${WakeSettings.dailyBudget} ครั้ง")
        val list = wakesPerSymbol.getOrPut(symbol) { mutableListOf() }
        list.removeAll { nowMs - it > HOUR_MS }
        if (list.size >= WakeSettings.hourlyBudget) return WakeDecision(false, "ครบงบ ${WakeSettings.hourlyBudget} ครั้ง/ชม. ของ $symbol")
        return WakeDecision(true, null)
    }

    @Synchronized
    fun register(seriesKey: String, symbol: String, barTs: Long, nowMs: Long) {
        ensureUsage(nowMs)
        lastWakeBar[seriesKey] = barTs
        wakesPerSymbol.getOrPut(symbol) { mutableListOf() }.add(nowMs)
        dayCount++
        WakeSettings.saveUsage(dayKey, dayCount)
    }

    data class Usage(val today: Int, val dailyBudget: Int, val perSymbolLastHour: Map<String, Int>)

    @Synchronized
    fun usage(nowMs: Long): Usage {
        ensureUsage(nowMs)
        return Usage(
            today = dayCount,
            dailyBudget = WakeSettings.dailyBudget,
            perSymbolLastHour = wakesPerSymbol.mapValues { (_, l) -> l.count { nowMs - it <= HOUR_MS } }
        )
    }

    /** ใช้ในเทสต์ */
    @Synchronized
    fun resetForTest() {
        lastFired.clear(); lastWakeBar.clear(); wakesPerSymbol.clear(); seeded.clear()
        dayKey = -1L; dayCount = 0; usageLoaded = true
    }
}
