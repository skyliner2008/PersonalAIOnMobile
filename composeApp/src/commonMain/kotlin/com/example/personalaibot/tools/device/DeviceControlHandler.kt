package com.example.personalaibot.tools.device

/**
 * DeviceControlHandler — Interface สำหรับ platform-specific device control
 * ให้ ToolExecutor (commonMain) สั่งงานฮาร์ดแวร์และแอปผ่าน DeviceControlExecutor (androidMain)
 */
interface DeviceControlHandler {
    suspend fun execute(toolName: String, args: Map<String, String>): String
}
