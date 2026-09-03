package com.example.personalaibot

import com.example.personalaibot.ai.IntentClassifier
import com.example.personalaibot.ai.TaskType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentClassifierTest {

    @Test
    fun classify_codingPrompts_identifiesCodeTask() {
        val result = IntentClassifier.classify("fix this kotlin compiler error in ViewModel")
        assertEquals(TaskType.CODE, result.taskType)
        assertTrue(result.isComplex)

        val thaiResult = IntentClassifier.classify("ช่วยเขียนโค้ด ฟังก์ชัน สำหรับดึงข้อมูล API หน่อย")
        assertEquals(TaskType.CODE, thaiResult.taskType)
    }

    @Test
    fun classify_researchPrompts_identifiesResearchTask() {
        val result = IntentClassifier.classify("ค้นหา ประวัติ ความเป็นมาของทองคำ")
        assertEquals(TaskType.RESEARCH, result.taskType)
    }

    @Test
    fun classify_tradingAndAnalysis_identifiesAnalysisTask() {
        val result = IntentClassifier.classify("วิเคราะห์ราคาทอง XAUUSD วันนี้")
        assertEquals(TaskType.ANALYSIS, result.taskType)
        assertTrue(result.isComplex)
    }

    @Test
    fun classify_creativePrompts_identifiesCreativeTask() {
        val result = IntentClassifier.classify("ช่วยเขียนนิทาน และ แต่งกลอน ให้หน่อย")
        assertEquals(TaskType.CREATIVE, result.taskType)
    }

    @Test
    fun classify_planningPrompts_identifiesPlanningTask() {
        val result = IntentClassifier.classify("ช่วยวางแผน todo และ จัดการ ตาราง งานวันนี้")
        assertEquals(TaskType.PLANNING, result.taskType)
    }

    @Test
    fun classify_memoryPrompts_identifiesMemoryTask() {
        val result = IntentClassifier.classify("จำได้ไหม ที่คุยกัน เรื่องงบประมาณ")
        assertEquals(TaskType.MEMORY, result.taskType)
    }

    @Test
    fun classify_generalCasualChat_identifiesGeneralTask() {
        val result = IntentClassifier.classify("สวัสดีตอนเช้า สบายดีไหม")
        assertEquals(TaskType.GENERAL, result.taskType)
    }
}
