package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.tools.ToolCall
import com.skyliner2008.jarvis.tools.ToolExecutor
import com.skyliner2008.jarvis.tools.ToolRegistry
import com.skyliner2008.jarvis.tools.device.DeviceControlHandler
import com.skyliner2008.jarvis.tools.device.DeviceToolDefinitions
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceControlTest {

    @Test
    fun testDeviceToolDefinitionsCompleteness() {
        val defs = DeviceToolDefinitions.allDefinitions
        assertTrue(defs.isNotEmpty(), "Device tool definitions should not be empty")

        val names = defs.map { it.name }.toSet()
        // Hardware
        assertTrue("device_flashlight" in names)
        assertTrue("device_volume" in names)
        assertTrue("device_brightness" in names)
        assertTrue("device_media_control" in names)
        assertTrue("device_always_live" in names)

        // App Launcher
        assertTrue("device_open_app" in names)
        assertTrue("device_navigate" in names)
        assertTrue("device_send_email" in names)
        assertTrue("device_add_calendar" in names)
        assertTrue("device_make_call" in names)
        assertTrue("device_send_sms" in names)
        assertTrue("device_set_alarm" in names)
        assertTrue("device_open_url" in names)
        assertTrue("device_search_web" in names)

        // Screen Interaction (Accessibility)
        assertTrue("device_read_screen" in names)
        assertTrue("device_tap" in names)
        assertTrue("device_type_text" in names)
        assertTrue("device_scroll" in names)
        assertTrue("device_press_button" in names)
        assertTrue("device_get_app_info" in names)

        // System Info
        assertTrue("device_battery_status" in names)
        assertTrue("device_wifi_status" in names)

        // Avatar Emotion & Custom Props
        assertTrue("device_avatar_emotion" in names)
        assertTrue("device_custom_prop" in names)

        // Smart Notifications (Driving Mode)
        assertTrue("device_notification_read" in names)
        assertTrue("device_notification_reply" in names)

        // Location
        assertTrue("device_location" in names)
    }

    @Test
    fun testToolRegistryIntegratesDeviceTools() {
        // isDeviceTool check
        assertTrue(ToolRegistry.isDeviceTool("device_flashlight"))
        assertTrue(ToolRegistry.isDeviceTool("device_volume"))
        assertTrue(ToolRegistry.isDeviceTool("device_navigate"))
        assertTrue(ToolRegistry.isDeviceTool("device_read_screen"))
        assertTrue(ToolRegistry.isDeviceTool("device_battery_status"))
        assertTrue(ToolRegistry.isDeviceTool("device_avatar_emotion"))
        assertTrue(ToolRegistry.isDeviceTool("device_custom_prop"))
        assertTrue(ToolRegistry.isDeviceTool("device_notification_read"))
        assertTrue(ToolRegistry.isDeviceTool("device_notification_reply"))
        assertTrue(ToolRegistry.isDeviceTool("device_location"))

        // allToolNames
        val allNames = ToolRegistry.allToolNames()
        assertTrue("device_flashlight" in allNames)
        assertTrue("device_navigate" in allNames)
        assertTrue("device_read_screen" in allNames)

        // Gemini tool declaration check
        val geminiTool = ToolRegistry.getGeminiTool()
        val geminiDeclNames = geminiTool.functionDeclarations.map { it.name }.toSet()
        assertTrue("device_flashlight" in geminiDeclNames)
        assertTrue("device_volume" in geminiDeclNames)
        assertTrue("device_navigate" in geminiDeclNames)
        assertTrue("device_custom_prop" in geminiDeclNames)

        // Categories check
        val categories = ToolRegistry.getToolCategories()
        val deviceCategory = categories.find { it.name.contains("Device Control") }
        assertNotNull(deviceCategory, "Device Control category should exist in UI categories")
        assertTrue(deviceCategory.tools.isNotEmpty())
    }

    @Test
    fun testToolExecutorRoutesToDeviceHandler() {
        val executedCalls = mutableListOf<Pair<String, Map<String, String>>>()

        val mockHandler = object : DeviceControlHandler {
            override suspend fun execute(toolName: String, args: Map<String, String>): String {
                executedCalls.add(toolName to args)
                return when (toolName) {
                    "device_flashlight" -> "🔦 เปิดไฟฉายแล้ว"
                    "device_volume" -> "🔊 เพิ่มเสียงมีเดีย: 10/15"
                    "device_navigate" -> "🗺️ กำลังเปิด Google Maps นำทางไป ${args["destination"]}"
                    "device_read_screen" -> "📱 ตรวจพบ 5 องค์ประกอบบนหน้าจอ"
                    "device_avatar_emotion" -> "🎭 แสดงเดโม่อารมณ์ 10 แบบแล้ว"
                    "device_always_live" -> "🤖 เปิดโหมด Always AI Live (โหมดควบคุม) เรียบร้อยแล้วค่ะ"
                    "device_notification_read" -> "📬 ตรวจพบข้อความล่าสุด 1 รายการ"
                    "device_notification_reply" -> "💬 ส่งข้อความตอบกลับเรียบร้อยแล้วค่ะ"
                    "device_location" -> "🌐 พิกัดปัจจุบัน: Lat 13.75630, Lng 100.50180"
                    "device_media_control" -> "🎵 เพลง: Starboy - The Weeknd"
                    else -> "OK"
                }
            }
        }

        ToolExecutor.initDeviceExecutor(mockHandler)

        runBlocking {
            // Flashlight
            val flashRes = ToolExecutor.execute(
                ToolCall("device_flashlight", mapOf("action" to "on"))
            )
            assertTrue(flashRes.result.contains("เปิดไฟฉายแล้ว"))

            // Volume
            val volRes = ToolExecutor.execute(
                ToolCall("device_volume", mapOf("action" to "up", "stream" to "media"))
            )
            assertTrue(volRes.result.contains("เพิ่มเสียงมีเดีย"))

            // Navigate
            val navRes = ToolExecutor.execute(
                ToolCall("device_navigate", mapOf("destination" to "Central World", "mode" to "drive"))
            )
            assertTrue(navRes.result.contains("Central World"))

            // Read Screen
            val screenRes = ToolExecutor.execute(
                ToolCall("device_read_screen", emptyMap())
            )
            assertTrue(screenRes.result.contains("ตรวจพบ 5 องค์ประกอบ"))

            // Avatar Emotion
            val emoRes = ToolExecutor.execute(
                ToolCall("device_avatar_emotion", mapOf("action" to "demo"))
            )
            assertTrue(emoRes.result.contains("แสดงเดโม่อารมณ์"))

            // Always Live (Control Mode)
            val liveRes = ToolExecutor.execute(
                ToolCall("device_always_live", mapOf("action" to "on", "mode" to "control"))
            )
            assertTrue(liveRes.result.contains("โหมดควบคุม"))

            // Notification Read
            val notifReadRes = ToolExecutor.execute(
                ToolCall("device_notification_read", mapOf("app_filter" to "line"))
            )
            assertTrue(notifReadRes.result.contains("ตรวจพบข้อความล่าสุด"))

            // Notification Reply
            val notifReplyRes = ToolExecutor.execute(
                ToolCall("device_notification_reply", mapOf("message" to "กำลังขับรถอยู่ครับ"))
            )
            assertTrue(notifReplyRes.result.contains("ส่งข้อความตอบกลับ"))

            // Location
            val locRes = ToolExecutor.execute(
                ToolCall("device_location", mapOf("action" to "get_current"))
            )
            assertTrue(locRes.result.contains("พิกัดปัจจุบัน"))

            // Media Control (Now Playing)
            val mediaRes = ToolExecutor.execute(
                ToolCall("device_media_control", mapOf("action" to "now_playing"))
            )
            assertTrue(mediaRes.result.contains("Starboy"))

            assertEquals(10, executedCalls.size)
            assertEquals("device_flashlight", executedCalls[0].first)
            assertEquals("device_volume", executedCalls[1].first)
            assertEquals("device_navigate", executedCalls[2].first)
            assertEquals("device_read_screen", executedCalls[3].first)
            assertEquals("device_avatar_emotion", executedCalls[4].first)
            assertEquals("device_always_live", executedCalls[5].first)
            assertEquals("control", executedCalls[5].second["mode"])
            assertEquals("device_notification_read", executedCalls[6].first)
            assertEquals("device_notification_reply", executedCalls[7].first)
            assertEquals("device_location", executedCalls[8].first)
            assertEquals("device_media_control", executedCalls[9].first)
        }
    }
}
