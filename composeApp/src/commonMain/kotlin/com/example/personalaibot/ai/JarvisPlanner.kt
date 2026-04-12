package com.example.personalaibot.ai

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.data.GeminiService

/**
 * JarvisPlanner — ระบบวางแผนและ Swarm routing (Phase 1 — Basic)
 *
 * หน้าที่:
 * 1. รับ user message + intent result
 * 2. สำหรับ task ซับซ้อน: แบ่งเป็น sub-tasks และเรียก specialist agents
 * 3. สำหรับ task ทั่วไป: route ตรงไปยัง Gemini พร้อม prompt ที่เหมาะสม
 *
 * Phase 1: Sequential specialist chains (ยังไม่ใช่ parallel swarm จริงๆ)
 * Phase 3+: Parallel multi-agent execution via coroutines
 */
class JarvisPlanner(private val geminiService: GeminiService) {

    /**
     * ประมวลผล task ตาม intent
     * - GENERAL/CREATIVE/MEMORY: ส่งตรงพร้อม intent-aware prompt
     * - CODE/RESEARCH: ใช้ sequential specialist chain
     * - ANALYSIS/PLANNING: ใช้ chain-of-thought step
     *
     * @return Flow<String> ของ response text chunks
     */
    fun execute(
        userMessage: String,
        history: List<ConversationTurn>,
        intent: IntentResult,
        coreContext: String = ""
    ) = when {
        intent.taskType == TaskType.CODE && intent.isComplex ->
            executeCodeChain(userMessage, history, intent, coreContext)

        intent.taskType == TaskType.ANALYSIS ->
            executeAnalysisChain(userMessage, history, intent, coreContext)

        intent.taskType == TaskType.RESEARCH ->
            executeResearchChain(userMessage, history, intent, coreContext)

        else ->
            geminiService.generateResponseFlow(
                prompt = userMessage,
                history = history,
                intentAddon = IntentClassifier.getSystemPromptAddon(intent),
                coreContext = coreContext,
                enableGrounding = true
            )
    }

    /**
     * Code Chain: Think → Plan → Implement
     * ส่งขั้นตอนการคิดก่อน แล้วตามด้วยการ implement จริง
     */
    private fun executeCodeChain(
        userMessage: String,
        history: List<ConversationTurn>,
        intent: IntentResult,
        coreContext: String
    ) = geminiService.generateResponseFlow(
        prompt = buildCodeChainPrompt(userMessage),
        history = history,
        intentAddon = IntentClassifier.getSystemPromptAddon(intent),
        coreContext = coreContext
    )

    /**
     * Analysis Chain: คิดก่อน สรุปทีหลัง
     */
    private fun executeAnalysisChain(
        userMessage: String,
        history: List<ConversationTurn>,
        intent: IntentResult,
        coreContext: String
    ) = geminiService.generateResponseFlow(
        prompt = buildAnalysisChainPrompt(userMessage),
        history = history,
        intentAddon = IntentClassifier.getSystemPromptAddon(intent),
        coreContext = coreContext
    )

    /**
     * Research Chain: ประมวลผลข้อมูลอย่างมีโครงสร้าง
     */
    private fun executeResearchChain(
        userMessage: String,
        history: List<ConversationTurn>,
        intent: IntentResult,
        coreContext: String
    ) = geminiService.generateResponseFlow(
        prompt = buildResearchChainPrompt(userMessage),
        history = history,
        intentAddon = IntentClassifier.getSystemPromptAddon(intent),
        coreContext = coreContext,
        enableGrounding = true
    )

    // ─── Prompt builders ──────────────────────────────────────────────────

    private fun buildCodeChainPrompt(original: String): String = """
$original

[Format Guide]
1. วิเคราะห์ requirement สั้นๆ
2. แสดงโค้ดพร้อม code block ที่ครบถ้วน
3. อธิบาย key points ที่สำคัญ
4. ระบุ edge cases หรือข้อควรระวัง (ถ้ามี)
""".trimIndent()

    private fun buildAnalysisChainPrompt(original: String): String = """
$original

[Analysis Guide]
- วิเคราะห์ข้อมูลโดยแยกแยะปัจจัยที่เกี่ยวข้องอย่างเป็นระบบ
- เปรียบเทียบข้อมูลและสรุปพร้อมให้คำแนะนำที่นำไปใช้ได้จริง
- แสดงข้อมูลในรูปแบบที่อ่านง่ายที่สุด (เช่น ตารางหรือลิส) ถ้ามีข้อมูลตัวเลข
""".trimIndent()

    private fun buildResearchChainPrompt(original: String): String = """
$original

[Research Guide]
- ให้ข้อมูลที่ถูกต้องและครอบคลุม
- จัดระเบียบข้อมูลเป็นจุดๆ หรือตารางให้ดูง่าย
- ให้ตัวอย่างหรือรายละเอียดเพิ่มเติมที่น่าสนใจ
""".trimIndent()
}
