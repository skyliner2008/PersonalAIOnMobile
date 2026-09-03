package com.example.personalaibot

import com.example.personalaibot.diagnostic.DiagnosticManager
import com.example.personalaibot.tools.*
import com.example.personalaibot.tools.system.SystemToolExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DynamicToolCreationTest {

    private class MockSideEffectDelegate : SideEffectDelegate {
        val savedFiles = mutableMapOf<String, String>()
        override suspend fun onSaveAgentTool(filename: String, jsonContent: String) {
            savedFiles[filename] = jsonContent
        }
        override suspend fun onReadAgentTool(filename: String): String = savedFiles[filename] ?: ""
        override suspend fun onDeleteAgentTool(filename: String): String {
            savedFiles.remove(filename)
            return "OK"
        }
        override suspend fun onRememberFact(key: String, value: String, importance: String) {}
        override suspend fun onSetReminder(title: String, detail: String, whenStr: String, timestamp: Long) {}
        override suspend fun onDisplayReport(markdown: String, voiceSummary: String) {}
        override suspend fun onVisionToggle(active: Boolean) {}
        override suspend fun onVoiceChange(newVoice: String) {}
        override suspend fun onRecallMemory(query: String): String = ""
        override suspend fun onSaveDiagnosticReport(filename: String, content: String) {}
        override suspend fun onUpdateIdentity(target: String, field: String, value: String): String = "OK"
        override suspend fun onManageAlerts(args: Map<String, String>): String = "OK"
        override suspend fun onManageSchedule(args: Map<String, String>): String = "OK"
        override suspend fun onChartControl(args: Map<String, String>): String = "OK"
    }

    @Test
    fun testDynamicToolCreationWithParametersAndFormulaExecution() {
        runBlocking {
            val mockDelegate = MockSideEffectDelegate()
            val systemExecutor = SystemToolExecutor(delegate = mockDelegate)
            ToolExecutor.initSystemExecutor(systemExecutor)
            ToolExecutor.setSideEffectDelegate(mockDelegate)

            // 1. Create a dynamic formula tool: Lot Size Calculator
            val createResult = systemExecutor.execute("system_create_agent_tool", mapOf(
                "name" to "lot_size_calculator",
                "description" to "คำนวณ Lot Size ตามความเสี่ยง",
                "triggerKeywords" to "lot, risk, size",
                "parameters" to "balance, risk_pct, sl_pips",
                "executionType" to "formula",
                "systemPromptAddon" to "balance * (risk_pct / 100) / (sl_pips * 10)"
            ))

            assertTrue(createResult.contains("✅"), "Creation must succeed: $createResult")
            assertTrue(mockDelegate.savedFiles.containsKey("lot_size_calculator.json"))

            // 2. Verify registered in ToolRegistry with schema
            val skill = ToolRegistry.getSkill("lot_size_calculator")
            assertNotNull(skill)
            assertEquals("formula", skill.executionType)
            assertNotNull(skill.parameters)
            assertEquals(3, skill.parameters?.properties?.size)
            assertTrue(skill.parameters?.properties?.containsKey("balance") == true)
            assertTrue(skill.parameters?.properties?.containsKey("risk_pct") == true)
            assertTrue(skill.parameters?.properties?.containsKey("sl_pips") == true)

            // 3. Execute the tool call through ToolExecutor with arguments: balance=10000, risk_pct=1, sl_pips=20
            // Expected formula: 10000 * (1 / 100) / (20 * 10) = 100 / 200 = 0.5
            val call = ToolCall(
                name = "lot_size_calculator",
                args = mapOf(
                    "balance" to "10000",
                    "risk_pct" to "1",
                    "sl_pips" to "20"
                )
            )
            val toolResult = ToolExecutor.execute(call)
            assertFalse(toolResult.isError)
            assertTrue(toolResult.result.contains("0.5"), "Calculation must yield 0.5: ${toolResult.result}")

            // Cleanup
            systemExecutor.execute("system_delete_agent_tool", mapOf("name" to "lot_size_calculator"))
        }
    }

    @Test
    fun testDynamicToolTemplateInterpolation() {
        runBlocking {
            val mockDelegate = MockSideEffectDelegate()
            val systemExecutor = SystemToolExecutor(delegate = mockDelegate)
            ToolExecutor.initSystemExecutor(systemExecutor)
            ToolExecutor.setSideEffectDelegate(mockDelegate)

            // 1. Create a prompt-based tool with placeholders {{symbol}} and {{timeframe}}
            systemExecutor.execute("system_create_agent_tool", mapOf(
                "name" to "gold_scalp_strategy",
                "description" to "วิเคราะห์กลยุทธ์ Scalping เฉพาะตัว",
                "triggerKeywords" to "scalp, gold",
                "parameters" to "symbol, timeframe",
                "executionType" to "prompt",
                "systemPromptAddon" to "ทำการสแกนสัญญาณ {{symbol}} บน Timeframe {{timeframe}} เพื่อหาจุดเข้า Reversal"
            ))

            val call = ToolCall(
                name = "gold_scalp_strategy",
                args = mapOf(
                    "symbol" to "XAUUSD",
                    "timeframe" to "M15"
                )
            )
            val toolResult = ToolExecutor.execute(call)
            assertFalse(toolResult.isError)
            assertTrue(toolResult.result.contains("ทำการสแกนสัญญาณ XAUUSD บน Timeframe M15 เพื่อหาจุดเข้า Reversal"))
            assertTrue(toolResult.result.contains("XAUUSD"))

            // Cleanup
            systemExecutor.execute("system_delete_agent_tool", mapOf("name" to "gold_scalp_strategy"))
        }
    }
}
