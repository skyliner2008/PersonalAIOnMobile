package com.skyliner2008.jarvis.automation.agent

import com.skyliner2008.jarvis.automation.backtest.LongTaskRunner
import com.skyliner2008.jarvis.logDebug
import kotlin.jvm.Volatile

/**
 * AgentTaskManager — Live เป็นเอเจนต์หลัก มอบงานให้ "chat session" ทำเบื้องหลัง
 *
 * flow ที่ออกแบบไว้:
 * 1. ผู้ใช้สั่งงานด้วยเสียง → Live เรียก `agent_task_start` แล้วตอบรับทันที (ไม่ต้องรอผล)
 * 2. งานจริงรันด้วยโมเดลแชท (flash-lite, 500 req/วัน) พร้อม tool ครบ ในเบื้องหลัง
 * 3. เสร็จแล้วผลเข้าแชทเป็นการ์ด และถูกส่งกลับเข้า Live ให้พูดสรุปให้ผู้ใช้ฟังต่อเนื่อง
 *    (ใช้ LongTaskRunner.completions เดิม — JarvisViewModel ต่อท่อไว้แล้ว)
 *
 * โควตา: งานเบื้องหลังใช้โมเดลแชทเดียวกับห้องแชท จึงจำกัดจำนวนงานพร้อมกันไว้ที่
 * [LongTaskRunner.MAX_CONCURRENT] และกันงานซ้ำด้วย dedupe ของ LongTaskRunner
 */
object AgentTaskManager {

    /** ตัวรันงานจริง — JarvisViewModel ผูกกับ orchestrator (โมเดลแชท + tool ครบ) */
    @Volatile
    private var runner: (suspend (String) -> String)? = null

    fun initRunner(block: suspend (String) -> String) {
        runner = block
        logDebug("AgentTask", "✅ Agent task runner attached (chat model executes background work)")
    }

    fun isReady(): Boolean = runner != null

    /**
     * เริ่มงานเบื้องหลัง
     * @return ข้อความตอบกลับให้โมเดล Live พูดต่อกับผู้ใช้
     */
    fun start(title: String, instruction: String): String {
        val run = runner
            ?: return "AGENT_TASK_UNAVAILABLE: ระบบงานเบื้องหลังยังไม่พร้อม — โปรดทำงานนี้ในบทสนทนาปัจจุบันแทน"
        val cleanTitle = title.trim().ifBlank { instruction.trim().take(40) }
        val cleanInstruction = instruction.trim()
        if (cleanInstruction.isBlank()) {
            return "AGENT_TASK_ERROR: ต้องระบุ instruction ว่าให้ทำอะไร"
        }
        if (!LongTaskRunner.canStartNew()) {
            val running = LongTaskRunner.snapshot().values.joinToString(", ")
            return "AGENT_TASK_BUSY: มีงานเบื้องหลังครบ ${LongTaskRunner.MAX_CONCURRENT} งานแล้ว ($running) — " +
                "แจ้งผู้ใช้ว่ารอให้งานใดงานหนึ่งเสร็จก่อน หรือให้สั่งยกเลิกงานที่ไม่ต้องการ"
        }

        val id = LongTaskRunner.launch(kind = "agent", title = cleanTitle) {
            val result = run(cleanInstruction)
            val body = "🤖 **$cleanTitle**\n\n$result"
            val speech = result.take(600)
            body to speech
        }
        logDebug("AgentTask", "▶ Task #$id started: $cleanTitle")
        return "AGENT_TASK_STARTED id=$id title=\"$cleanTitle\" — " +
            "รับงานแล้ว กำลังทำอยู่เบื้องหลัง โปรดตอบรับผู้ใช้สั้นๆ 1 ประโยคว่ากำลังทำให้ " +
            "แล้วคุยเรื่องอื่นต่อได้ตามปกติ เมื่อเสร็จระบบจะส่งผลกลับมาให้พูดรายงานเอง ห้ามเดาผลลัพธ์ล่วงหน้า"
    }

    fun list(): String {
        val running = LongTaskRunner.snapshot()
        if (running.isEmpty()) return "AGENT_TASKS: ไม่มีงานเบื้องหลังที่กำลังทำอยู่"
        val lines = running.entries.joinToString("\n") { (id, title) -> "- #$id: $title" }
        return "AGENT_TASKS (${running.size}/${LongTaskRunner.MAX_CONCURRENT}):\n$lines"
    }

    fun status(id: Long?): String {
        val running = LongTaskRunner.snapshot()
        if (id == null) return list()
        val title = running[id]
            ?: return "AGENT_TASK_DONE_OR_UNKNOWN: ไม่พบงาน #$id ในรายการที่กำลังรัน (อาจเสร็จไปแล้วหรือถูกยกเลิก)"
        return "AGENT_TASK_RUNNING: งาน #$id \"$title\" ยังทำอยู่ — แจ้งผู้ใช้ว่ากำลังดำเนินการ"
    }

    fun cancel(id: Long?): String {
        if (id == null) return "AGENT_TASK_ERROR: ต้องระบุ task_id ที่จะยกเลิก"
        return if (LongTaskRunner.cancel(id)) {
            "AGENT_TASK_CANCELLED: ยกเลิกงาน #$id แล้ว"
        } else {
            "AGENT_TASK_NOT_FOUND: ไม่พบงาน #$id ที่กำลังรัน"
        }
    }
}
