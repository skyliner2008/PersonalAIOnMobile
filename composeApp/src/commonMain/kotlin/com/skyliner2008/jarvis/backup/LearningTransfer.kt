package com.skyliner2008.jarvis.backup

import com.skyliner2008.jarvis.automation.wake.WakeLearningStore
import com.skyliner2008.jarvis.automation.wake.WakeSettings
import com.skyliner2008.jarvis.db.JarvisDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ส่งออก / นำเข้า "การเรียนรู้" ของระบบปลุก AI เป็นไฟล์ JSON
 *
 * ใช้ย้ายสิ่งที่ระบบเรียนรู้มาแล้วจากเครื่อง A → เครื่อง B หรือเก็บไว้ก่อนถอนการติดตั้ง
 * - นำเข้าแบบ **รวม** (merge) ไม่ใช่เขียนทับ: แถวที่มีอยู่แล้ว (signal_id + factor_id ซ้ำ) ถูกข้าม
 *   จึงนำเข้าไฟล์เดิมซ้ำได้ปลอดภัย และรวมการเรียนรู้จากหลายเครื่องเข้าด้วยกันได้
 * - แถวที่ยังรอผล (PENDING) ไม่ถูกส่งออก — ต้องใช้แท่งเทียนของเครื่องต้นทางวัดผล
 * - การตั้งค่า (ปัจจัยที่ปิด / งบการปลุก) นำเข้าด้วยได้ถ้าเลือก
 */
object LearningTransfer {
    const val FORMAT = "jarvis-wake-learning"
    const val VERSION = 1

    @Serializable
    data class Outcome(
        val signalId: String,
        val factorId: String,
        val symbol: String,
        val interval: String,
        val side: String,
        val kind: String,
        val refPrice: Double,
        val refAtr: Double,
        val woke: Long,
        val mtfAlign: String,
        val adxBucket: String,
        val volBucket: String,
        val session: String,
        val aiDecision: String? = null,
        val aiBias: String? = null,
        val status: String,
        val forwardR: Double? = null,
        val mfeR: Double? = null,
        val maeR: Double? = null,
        val createdAt: Long,
        val resolvedAt: Long? = null,
        /** เพิ่มภายหลัง (P14) — ไฟล์รุ่นเก่าไม่มี จึงเป็น null */
        val aiConfidence: Long? = null,
        val aiReason: String? = null
    )

    @Serializable
    data class Settings(
        val disabledFactors: List<String> = emptyList(),
        val hourlyBudget: Int? = null,
        val dailyBudget: Int? = null,
        val cooldownBars: Int? = null
    )

    @Serializable
    data class Bundle(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exportedAt: Long,
        val settings: Settings,
        val outcomes: List<Outcome>
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun buildBundle(db: JarvisDatabase, nowMs: Long = Clock.System.now().toEpochMilliseconds()): Bundle {
        val rows = db.jarvisDatabaseQueries.getAllFactorOutcomes().executeAsList()
            .filter { it.status != "PENDING" }
        return Bundle(
            exportedAt = nowMs,
            settings = Settings(
                disabledFactors = WakeSettings.disabled.sorted(),
                hourlyBudget = WakeSettings.hourlyBudget,
                dailyBudget = WakeSettings.dailyBudget,
                cooldownBars = WakeSettings.cooldownBars
            ),
            outcomes = rows.map {
                Outcome(
                    it.signal_id, it.factor_id, it.symbol, it.interval, it.side, it.kind,
                    it.ref_price, it.ref_atr, it.woke, it.mtf_align, it.adx_bucket, it.vol_bucket, it.session,
                    it.ai_decision, it.ai_bias, it.status, it.forward_r, it.mfe_r, it.mae_r,
                    it.created_at, it.resolved_at, it.ai_confidence, it.ai_reason
                )
            }
        )
    }

    fun exportJson(db: JarvisDatabase): String = json.encodeToString(Bundle.serializer(), buildBundle(db))

    data class ImportResult(val total: Int, val inserted: Int, val skipped: Int, val settingsApplied: Boolean) {
        fun describeTh(): String = buildString {
            append("นำเข้าการเรียนรู้ $inserted แถว")
            if (skipped > 0) append(" (ข้าม $skipped แถวที่มีอยู่แล้ว)")
            append(" จากทั้งหมด $total")
            if (settingsApplied) append(" · นำเข้าการตั้งค่าปัจจัย/งบการปลุกแล้ว")
        }
    }

    /** @throws IllegalArgumentException ถ้าไม่ใช่ไฟล์การเรียนรู้ของแอปนี้ หรือเวอร์ชันใหม่กว่าที่รองรับ */
    fun parse(text: String): Bundle {
        val bundle = runCatching { json.decodeFromString(Bundle.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("ไฟล์ไม่ใช่รูปแบบการเรียนรู้ของ Jarvis") }
        require(bundle.format == FORMAT) { "ไฟล์ไม่ใช่รูปแบบการเรียนรู้ของ Jarvis (${bundle.format})" }
        require(bundle.version <= VERSION) { "ไฟล์มาจากแอปเวอร์ชันใหม่กว่า (v${bundle.version}) — กรุณาอัปเดตแอปก่อน" }
        return bundle
    }

    fun importJson(db: JarvisDatabase, text: String, applySettings: Boolean = true): ImportResult {
        val bundle = parse(text)
        val q = db.jarvisDatabaseQueries
        val before = q.countFactorOutcomes().executeAsOne()
        db.transaction {
            bundle.outcomes.forEach { o ->
                q.insertFactorOutcome(
                    o.signalId, o.factorId.uppercase(), o.symbol, o.interval, o.side, o.kind,
                    o.refPrice, o.refAtr, o.woke, o.mtfAlign, o.adxBucket, o.volBucket, o.session,
                    o.aiDecision, o.aiBias, o.status, o.forwardR, o.mfeR, o.maeR, o.createdAt, o.resolvedAt,
                    o.aiConfidence, o.aiReason
                )
            }
        }
        val inserted = (q.countFactorOutcomes().executeAsOne() - before).toInt()
        if (applySettings) {
            val s = bundle.settings
            WakeSettings.disabled = s.disabledFactors.map { it.uppercase() }.toSet()
            s.hourlyBudget?.let { WakeSettings.hourlyBudget = it }
            s.dailyBudget?.let { WakeSettings.dailyBudget = it }
            s.cooldownBars?.let { WakeSettings.cooldownBars = it }
        }
        WakeLearningStore.invalidateCache()
        return ImportResult(bundle.outcomes.size, inserted, bundle.outcomes.size - inserted, applySettings)
    }

    fun suggestedFileName(nowMs: Long = Clock.System.now().toEpochMilliseconds()): String =
        "jarvis-learning-${BackupNaming.stamp(nowMs)}.json"
}

object BackupNaming {
    /** yyyyMMdd-HHmm (เวลาเครื่อง) สำหรับชื่อไฟล์สำรอง */
    fun stamp(nowMs: Long): String {
        val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMs)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "${ldt.year}${p(ldt.monthNumber)}${p(ldt.dayOfMonth)}-${p(ldt.hour)}${p(ldt.minute)}"
    }
}
