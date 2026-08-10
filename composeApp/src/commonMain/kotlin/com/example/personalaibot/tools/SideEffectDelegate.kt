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

    /** ค้นหาสิ่งที่จำได้จากความทรงจำระยะยาว */
    suspend fun onRecallMemory(query: String): String

    /** บันทึกรายงานการตรวจสอบระบบ (Diagnostic) */
    suspend fun onSaveDiagnosticReport(filename: String, content: String)

    /** บันทึกเครื่องมือ (Tool/Skill) ที่สร้างโดย Agent */
    suspend fun onSaveAgentTool(filename: String, jsonContent: String)

    /** อ่านเนื้อหาไฟล์ custom tool ที่เคยสร้าง (คืน error string ถ้าอ่านไม่ได้) */
    suspend fun onReadAgentTool(filename: String): String

    /** ลบไฟล์ custom tool (คืนข้อความผลลัพธ์) */
    suspend fun onDeleteAgentTool(filename: String): String

    /**
     * อัปเดตตัวตนของ AI agent หรือผู้ใช้ (จาก tool `identity_update`)
     * @param target "agent" | "user"
     * @param field  agent: name/creature/vibe/gender — user: name/call_name/notes
     * @return ข้อความยืนยันผลลัพธ์ (จะถูกส่งกลับเข้า tool loop)
     */
    suspend fun onUpdateIdentity(target: String, field: String, value: String): String

    /**
     * สร้าง/ลบการแจ้งเตือนอัตโนมัติ (จาก tool `automation_manage_alerts`)
     * @param args อาร์กิวเมนต์จาก tool call (action, name, symbol, condition_*, interval_minutes, alert_id)
     * @return ข้อความยืนยันผลลัพธ์ (จะถูกส่งกลับเข้า tool loop)
     */
    suspend fun onManageAlerts(args: Map<String, String>): String

    /**
     * สร้าง/ลบ/list งานตามเวลา (จาก tool `automation_manage_schedule`)
     * @param args อาร์กิวเมนต์จาก tool call (action, name, prompt, schedule_type, time_hhmm, run_at, in_minutes, task_id)
     * @return ข้อความยืนยันผลลัพธ์ (จะถูกส่งกลับเข้า tool loop)
     */
    suspend fun onManageSchedule(args: Map<String, String>): String
}
