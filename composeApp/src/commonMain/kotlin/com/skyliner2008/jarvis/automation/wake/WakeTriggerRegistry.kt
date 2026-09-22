package com.skyliner2008.jarvis.automation.wake

import com.skyliner2008.jarvis.automation.wake.triggers.ANOMALY_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.COMPOSITE_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.FUNDAMENTAL_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.INTERMARKET_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.LEVEL_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.LIQUIDITY_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.MOMENTUM_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.MTF_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.PATTERN_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.PRICE_ACTION_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.SESSION_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.STRUCTURE_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.TREND_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.VOLATILITY_TRIGGERS
import com.skyliner2008.jarvis.automation.wake.triggers.VOLUME_TRIGGERS

/**
 * WakeTriggerRegistry — ทะเบียนกลางของตัวปลุก AI (15 หมวด)
 *
 * เพิ่มปัจจัยใหม่ = เขียน trigger ในไฟล์หมวดที่เหมาะสม (`automation/wake/triggers/`)
 * แล้วใส่ใน list ของหมวดนั้น — ระบบเก็บสถิติรายตัวให้อัตโนมัติ
 * ไม่ต้องแก้ engine / prompt / ตาราง DB
 *
 * หลักการ: ครอบคลุมทุกด้านไว้ก่อน แล้วให้ระบบเรียนรู้คัดเองว่าตัวไหนควรอยู่ต่อ
 * (ตัวที่พิสูจน์แล้วว่าไร้ประโยชน์จะถูก "ลดชั้น" — ยังเก็บสถิติแต่ไม่ปลุก AI)
 */
object WakeTriggerRegistry {

    /** จัดเรียงตามหมวด — ลำดับนี้ใช้แสดงผลให้ผู้ใช้ด้วย */
    val BY_GROUP: Map<EvidenceGroup, List<WakeTrigger>> by lazy {
        (STRUCTURE_TRIGGERS + LEVEL_TRIGGERS + LIQUIDITY_TRIGGERS + TREND_TRIGGERS + MOMENTUM_TRIGGERS +
            VOLATILITY_TRIGGERS + VOLUME_TRIGGERS + PRICE_ACTION_TRIGGERS + PATTERN_TRIGGERS + MTF_TRIGGERS +
            SESSION_TRIGGERS + COMPOSITE_TRIGGERS + INTERMARKET_TRIGGERS + FUNDAMENTAL_TRIGGERS + ANOMALY_TRIGGERS)
            .groupBy { it.evidenceGroup }
    }

    val ALL: List<WakeTrigger> by lazy { EvidenceGroup.entries.flatMap { BY_GROUP[it].orEmpty() } }

    private val byId: Map<String, WakeTrigger> by lazy { ALL.associateBy { it.id.uppercase() } }

    fun find(id: String): WakeTrigger? = byId[id.trim().uppercase()]

    /** trigger ที่เป็น "เหตุการณ์" — ใช้ตัดสินว่าจะปลุก AI ไหม */
    val events: List<WakeTrigger> get() = ALL.filter { it.kind == TriggerKind.EVENT }

    /** trigger ที่เป็น "สภาวะ" — ใช้เป็นบริบท ไม่ปลุกเอง */
    val states: List<WakeTrigger> get() = ALL.filter { it.kind == TriggerKind.STATE }

    data class ScanResult(
        val events: List<TriggerEvent>,
        val states: List<TriggerEvent>,
        /** trigger ที่ throw ระหว่างประเมิน — ไม่ทำให้ทั้งรอบล่ม แต่รายงานให้ตรวจสอบได้ */
        val errors: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = events.isEmpty() && states.isEmpty()
        val all: List<TriggerEvent> get() = events + states
    }

    /**
     * ประเมินทุก trigger แล้วคืนเหตุการณ์ที่เกิด
     * trigger ตัวใดพังจะไม่ทำให้ตัวอื่นหยุด (บันทึกไว้ใน [ScanResult.errors])
     */
    fun scan(ctx: WakeContext, enabledIds: Set<String>? = null): ScanResult {
        val ev = mutableListOf<TriggerEvent>()
        val st = mutableListOf<TriggerEvent>()
        val errors = mutableListOf<String>()
        for (t in ALL) {
            if (enabledIds != null && t.id.uppercase() !in enabledIds) continue
            val hit = try {
                t.detect(ctx)
            } catch (e: Throwable) {
                errors += "${t.id}: ${e::class.simpleName} ${e.message}"
                null
            } ?: continue
            if (t.kind == TriggerKind.EVENT) ev += hit else st += hit
        }
        return ScanResult(ev, st, errors)
    }

    /** แท่งขั้นต่ำของ TF ที่จะประเมินปัจจัยบน TF นั้น */
    private const val MIN_TF_BARS = 60

    /**
     * ประเมินทุกปัจจัยบนหลาย TF — ปัจจัยที่ใช้ได้ทุก TF ถูกประเมินบนแต่ละ TF ใน [tfs] (TriggerEvent.tf = TF นั้น)
     * ปัจจัยตายตัว ([WakeTfProfile.FIXED]) ประเมินครั้งเดียว และ tf ของเหตุการณ์ถูกปรับเป็น TF จริง
     */
    fun scanAllTf(ctx: WakeContext, tfs: List<String>, enabledIds: Set<String>? = null): ScanResult {
        val ev = mutableListOf<TriggerEvent>()
        val st = mutableListOf<TriggerEvent>()
        val errors = mutableListOf<String>()
        val views = tfs.distinct().filter { ctx.series(it).n >= MIN_TF_BARS }.map { ctx.forTf(it) }
        for (t in ALL) {
            if (enabledIds != null && t.id.uppercase() !in enabledIds) continue
            val fixed = WakeTfProfile.isFixed(t.id)
            for (c in if (fixed) listOf(ctx) else views) {
                val hit = try {
                    t.detect(c)
                } catch (e: Throwable) {
                    errors += "${t.id}@${c.primaryTf}: ${e::class.simpleName} ${e.message}"
                    null
                } ?: continue
                val tf = if (fixed) WakeTfProfile.normalizeEventTf(hit.tf, ctx.primaryTf) else c.primaryTf
                val e = if (hit.tf == tf) hit else hit.copy(tf = tf)
                if (t.kind == TriggerKind.EVENT) ev += e else st += e
            }
        }
        return ScanResult(ev, st, errors)
    }
}
