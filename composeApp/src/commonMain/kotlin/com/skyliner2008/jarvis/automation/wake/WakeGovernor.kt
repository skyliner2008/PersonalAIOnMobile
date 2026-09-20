package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.tools.trading.TaIndicators
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
    private const val KEY_MIN_GAP = "wake.min_gap.minutes"
    private const val KEY_MEMORY_PREFIX = "wake.mem."

    /**
     * งบรายวันเริ่มต้น — อิงโควตา Flash Lite 2 โมเดล × 500 RPD = 1,000
     * เผื่อไว้ 10% ให้การใช้งานอื่น (แชท / alert ประเภทอื่น)
     */
    const val DEFAULT_DAILY = 3000
    /**
     * เดิม 6/ชม. + ปลุกได้ครั้งเดียวต่อแท่ง M15 (≈ 4/ชม. จริง, ~144/วัน) ทั้งที่ผู้ใช้มี API key 7 ตัว
     * (ระบบสลับ key อัตโนมัติเมื่อติดลิมิต) — ตอนนี้ปัจจัยถูกประเมินทุก TF ทุกนาที
     * เปิดให้ปลุกถี่ขึ้นในช่วงเรียนรู้ โดยยังคุมด้วยงบรายชั่วโมง/รายวัน
     */
    const val DEFAULT_HOURLY = 40
    const val DEFAULT_COOLDOWN_BARS = 3
    /** ระยะห่างขั้นต่ำระหว่างการปลุก 2 ครั้ง (นาที) = รอบสแกน — ชั้น alert ไม่ throttle งานปลุก AI แล้ว */
    const val DEFAULT_MIN_GAP_MINUTES = 1

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

    var minGapMinutes: Int
        get() = get(KEY_MIN_GAP)?.toIntOrNull()?.coerceIn(1, 60) ?: DEFAULT_MIN_GAP_MINUTES
        set(v) = put(KEY_MIN_GAP, v.coerceIn(1, 60).toString())

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
 * 1. **Cooldown รายปัจจัย × TF** — ปัจจัยเดิม TF เดิม ทิศเดิม ยิงซ้ำภายใน N แท่ง "ของ TF นั้น" ไม่นับ
 *    (ปัจจัยเดียวกันบน TF อื่นเป็นเหตุการณ์แยกกัน — RSI_50_CROSS บน M5 กับบน H1 ไม่ใช่เรื่องเดียวกัน)
 * 2. **State → ใช้เฉพาะตอนเปลี่ยน** — สภาวะที่ค้างหลายแท่งถูกบันทึกเข้าการเรียนรู้แค่ครั้งแรก
 * 3. **ระยะห่างขั้นต่ำ** [WakeSettings.minGapMinutes] ระหว่างการปลุก — เดิม "ครั้งเดียวต่อแท่ง TF หลัก"
 *    ทำให้เหตุการณ์ M1/M5 กลางแท่ง M15 ต้องรอจนแท่งปิด
 * 4. **ไม่ทิ้งเหตุการณ์** — เหตุการณ์ที่เกิดระหว่างรอระยะห่าง ถูกพกไปแสดงในการปลุกครั้งถัดไป ([defer]/[takeDeferred])
 * 5. **เพดาน** — ต่อชั่วโมงต่อสินทรัพย์ และรวมทั้งวัน
 *
 * สถานะต่อ series (cooldown + เวลาที่ปลุกล่าสุด) ถูกเก็บลง memory ของ series ผ่าน [exportState] / [seed]
 * และยอดใช้รายวันเก็บใน AppSetting — รอดการรีสตาร์ทบริการ
 */
object WakeGovernor {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    internal const val MEM_LAST_WAKE = "gov_last_wake"
    internal const val MEM_FIRED = "gov_fired"
    private const val MAX_FIRED_ENTRIES = 1500
    /** เหตุการณ์ที่รอการปลุกเก่าได้ไม่เกินนี้ (เกินแล้วหมดความหมาย) */
    const val CARRY_MS = 10 * 60_000L

    private val lastFired = mutableMapOf<String, Long>()          // series|ID@tf|dir → เวลาเปิดแท่งของ tf นั้น
    private val lastWakeAt = mutableMapOf<String, Long>()         // series → เวลาที่ปลุก (ms)
    private val wakesPerSymbol = mutableMapOf<String, MutableList<Long>>()
    private val deferred = mutableMapOf<String, MutableList<Pair<TriggerEvent, Long>>>()
    private val seeded = mutableSetOf<String>()
    private var dayKey = -1L
    private var dayCount = 0
    private var usageLoaded = false

    private fun key(seriesKey: String, e: TriggerEvent) = "$seriesKey|${e.triggerId}@${e.tf}|${e.direction}"

    /** TF ของคีย์ cooldown ("ID@tf|DIR") */
    private fun tfOfKey(k: String): String = k.substringBefore('|').substringAfter('@', "15m")

    /** คืนสถานะที่บันทึกไว้ของ series (ครั้งแรกที่เห็น series นี้ใน process) */
    @Synchronized
    fun seed(seriesKey: String, memory: Map<String, String>) {
        if (!seeded.add(seriesKey)) return
        memory[MEM_LAST_WAKE]?.toLongOrNull()?.let { if (seriesKey !in lastWakeAt) lastWakeAt[seriesKey] = it }
        val seriesTf = seriesKey.substringAfter('@', "15m")
        memory[MEM_FIRED]?.split(",")?.forEach { e ->
            val at = e.lastIndexOf('@')
            if (at <= 0) return@forEach
            val ts = e.substring(at + 1).toLongOrNull() ?: return@forEach
            var body = e.substring(0, at)
            // รุ่นก่อน P16: "ID|DIR" (เกิดบน TF หลักเสมอ) → "ID@tf|DIR"
            if ('@' !in body) body = body.substringBefore('|') + "@" + seriesTf + "|" + body.substringAfter('|')
            val k = "$seriesKey|$body"
            if (k !in lastFired) lastFired[k] = ts
        }
    }

    /** สถานะของ series สำหรับเก็บลง memory — ตัดรายการที่พ้น cooldown ของ TF ตัวเองแล้วทิ้ง */
    @Synchronized
    fun exportState(seriesKey: String, nowMs: Long): Map<String, String> {
        val prefix = "$seriesKey|"
        val fired = lastFired.entries
            .filter { e ->
                if (!e.key.startsWith(prefix)) return@filter false
                val tfMs = TaIndicators.timeframeMillis(tfOfKey(e.key.removePrefix(prefix)))
                nowMs - e.value <= (WakeSettings.cooldownBars + 2) * tfMs
            }
            .sortedByDescending { it.value }
            .take(MAX_FIRED_ENTRIES)
            .joinToString(",") { "${it.key.removePrefix(prefix)}@${it.value}" }
        return buildMap {
            lastWakeAt[seriesKey]?.let { put(MEM_LAST_WAKE, it.toString()) }
            put(MEM_FIRED, fired)
        }
    }

    /**
     * เหตุการณ์ที่ผ่าน cooldown แล้ว
     * @param barTs เวลาเปิดแท่งปิดล่าสุดของแต่ละ TF — cooldown นับเป็นแท่งของ TF ที่เหตุการณ์เกิด
     * @param commit false = ดูอย่างเดียว ไม่บันทึกว่ายิงแล้ว (ใช้กับการสแกนด้วยมือจากแชท
     *   — เดิมการสแกนด้วยมือ "กิน" cooldown และการปลุกของแท่งนั้นไป alert เบื้องหลังจึงเงียบ)
     */
    @Synchronized
    fun freshEvents(seriesKey: String, events: List<TriggerEvent>, barTs: Map<String, Long>, commit: Boolean = true): List<TriggerEvent> {
        val fallback = barTs.values.maxOrNull() ?: 0L
        return events.filter { e ->
            val k = key(seriesKey, e)
            val ts = barTs[e.tf] ?: fallback
            val cd = WakeSettings.cooldownBars * TaIndicators.timeframeMillis(e.tf)
            val prev = lastFired[k]
            val ok = prev == null || ts - prev > cd
            if (ok && commit) lastFired[k] = ts
            ok
        }
    }

    /** พักเหตุการณ์ที่ควรปลุกแต่ติดระยะห่าง/งบ ไว้แสดงในการปลุกครั้งถัดไป */
    @Synchronized
    fun defer(seriesKey: String, events: List<TriggerEvent>, nowMs: Long) {
        if (events.isEmpty()) return
        val list = deferred.getOrPut(seriesKey) { mutableListOf() }
        list.removeAll { nowMs - it.second > CARRY_MS }
        events.forEach { e -> if (list.none { it.first.triggerId == e.triggerId && it.first.tf == e.tf && it.first.direction == e.direction }) list += e to nowMs }
    }

    /** มีเหตุการณ์ที่พักไว้รอปลุกไหม (ไม่ล้าง) — เหตุการณ์ที่รออยู่ปลุกได้เองเมื่อพ้นระยะห่าง แม้ไม่มีเหตุการณ์ใหม่ */
    @Synchronized
    fun hasDeferred(seriesKey: String, nowMs: Long): Boolean =
        deferred[seriesKey]?.any { nowMs - it.second <= CARRY_MS } == true

    /** เหตุการณ์ที่พักไว้ (ไม่เก่ากว่า [CARRY_MS]) พร้อมเวลาที่เกิด — ดึงแล้วล้าง */
    @Synchronized
    fun takeDeferred(seriesKey: String, nowMs: Long): List<Pair<TriggerEvent, Long>> {
        val list = deferred.remove(seriesKey) ?: return emptyList()
        return list.filter { nowMs - it.second <= CARRY_MS }
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
    fun decide(seriesKey: String, symbol: String, nowMs: Long): WakeDecision {
        val gapMs = WakeSettings.minGapMinutes * 60_000L
        lastWakeAt[seriesKey]?.let { last ->
            if (nowMs - last < gapMs) return WakeDecision(false, "เพิ่งปลุกไปเมื่อ ${(nowMs - last) / 1000} วิ (เว้น ${WakeSettings.minGapMinutes} นาที)")
        }
        ensureUsage(nowMs)
        if (dayCount >= WakeSettings.dailyBudget) return WakeDecision(false, "ครบงบรายวัน ${WakeSettings.dailyBudget} ครั้ง")
        val list = wakesPerSymbol.getOrPut(symbol) { mutableListOf() }
        list.removeAll { nowMs - it > HOUR_MS }
        if (list.size >= WakeSettings.hourlyBudget) return WakeDecision(false, "ครบงบ ${WakeSettings.hourlyBudget} ครั้ง/ชม. ของ $symbol")
        return WakeDecision(true, null)
    }

    @Synchronized
    fun register(seriesKey: String, symbol: String, nowMs: Long) {
        ensureUsage(nowMs)
        lastWakeAt[seriesKey] = nowMs
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
        lastFired.clear(); lastWakeAt.clear(); wakesPerSymbol.clear(); seeded.clear(); deferred.clear()
        dayKey = -1L; dayCount = 0; usageLoaded = true
    }
}
