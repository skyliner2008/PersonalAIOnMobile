package com.example.personalaibot.tools

/**
 * Interface for handling side effects triggered by AI tools.
 * This decouples the ToolExecutor from the UI/ViewModel and Memory layers.
 */
interface SideEffectDelegate {
    /** บันทึกข้อมูลสำคัญลงในหน่วยความจำระยะยาว */
    suspend fun onRememberFact(key: String, value: String, importance: String)
    
    /** ตั้งค่าการแจ้งเตือน */
    suspend fun onSetReminder(title: String, detail: String, whenStr: String, timestamp: Long)
    
    /** แสดงรายงานข้อมูลขนาดใหญ่ในหน้าแชท */
    suspend fun onDisplayReport(markdown: String, voiceSummary: String)

    /** ควบคุมสถานะการมองเห็น (Vision) */
    suspend fun onVisionToggle(active: Boolean)

    /** ควบคุมการเปลี่ยนเสียง */
    suspend fun onVoiceChange(newVoice: String)
}
