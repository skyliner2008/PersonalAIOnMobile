package com.example.personalaibot

import com.example.personalaibot.camera.CameraAnalysisService
import com.example.personalaibot.camera.CameraMode
import com.example.personalaibot.camera.CameraProviderType
import com.example.personalaibot.tools.ToolCall
import com.example.personalaibot.tools.ToolExecutor
import com.example.personalaibot.tools.camera.CameraToolExecutor
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class NonTradingToolsTest {

    @Test
    fun testDateTimeContainsThaiInformation() {
        runBlocking {
            val res = ToolExecutor.execute(ToolCall("get_current_datetime", emptyMap()))
            assertTrue(res.result.contains("วันที่: วัน"))
            assertTrue(res.result.contains("พ.ศ."))
            assertTrue(res.result.contains("เวลา:"))
            assertTrue(res.result.contains("เขตเวลา:"))
        }
    }

    @Test
    fun testConvertUnitsWithThaiNames() {
        runBlocking {
            // กิโลเมตร to ไมล์
            val resKm = ToolExecutor.execute(ToolCall("convert_units", mapOf(
                "value" to "10",
                "from_unit" to "กิโลเมตร",
                "to_unit" to "ไมล์"
            )))
            assertTrue(resKm.result.contains("6.2137"), "Expected ~6.21 miles for 10 km, got: ${resKm.result}")

            // ไร่ to ตารางเมตร
            val resRai = ToolExecutor.execute(ToolCall("convert_units", mapOf(
                "value" to "2",
                "from_unit" to "ไร่",
                "to_unit" to "ตารางเมตร"
            )))
            assertTrue(resRai.result.contains("3200"), "Expected 3200 sqm for 2 rai, got: ${resRai.result}")

            // ไร่ to ตารางวา (1 ไร่ = 400 ตร.วา)
            val resWa = ToolExecutor.execute(ToolCall("convert_units", mapOf(
                "value" to "1",
                "from_unit" to "ไร่",
                "to_unit" to "ตารางวา"
            )))
            assertTrue(resWa.result.contains("400"), "Expected 400 sqwa for 1 rai, got: ${resWa.result}")

            // เซลเซียส to ฟาเรนไฮต์ (100 C = 212 F)
            val resTemp = ToolExecutor.execute(ToolCall("convert_units", mapOf(
                "value" to "100",
                "from_unit" to "เซลเซียส",
                "to_unit" to "ฟาเรนไฮต์"
            )))
            assertTrue(resTemp.result.contains("212"), "Expected 212 F for 100 C, got: ${resTemp.result}")
        }
    }

    @Test
    fun testCameraToolExecutorFlexibility() {
        val service = CameraAnalysisService(HttpClient())
        val executor = CameraToolExecutor(service)

        runBlocking {
            // Flexible provider switching with "gpt-4o"
            val r1 = executor.execute("camera_switch_provider", mapOf("provider" to "gpt-4o"))
            assertTrue(r1.startsWith("✅"), "Should accept gpt-4o: $r1")
            assertTrue(service.activeProvider.value == CameraProviderType.OPENAI_GPT4O)

            // Flexible provider switching with "claude"
            val r2 = executor.execute("camera_switch_provider", mapOf("provider" to "claude"))
            assertTrue(r2.startsWith("✅"), "Should accept claude: $r2")
            assertTrue(service.activeProvider.value == CameraProviderType.CLAUDE_SONNET)

            // Flexible mode switching with "stream"
            val m1 = executor.execute("camera_switch_mode", mapOf("mode" to "stream"))
            assertTrue(m1.startsWith("✅"), "Should accept stream: $m1")
            assertTrue(service.cameraMode.value == CameraMode.LIVE_STREAM)

            // Flexible mode switching with "photo"
            val m2 = executor.execute("camera_switch_mode", mapOf("mode" to "photo"))
            assertTrue(m2.startsWith("✅"), "Should accept photo: $m2")
            assertTrue(service.cameraMode.value == CameraMode.SNAPSHOT)
        }
    }
}

