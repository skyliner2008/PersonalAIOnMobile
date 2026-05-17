# Second Brain Tool Creator (Agent-Native Tools)

## 1. Overview
ระบบ Second Brain Agent ในมือถือที่สามารถ "คิดค้น" และ "สร้าง" Tool ใหม่ๆ ของตัวเองได้ (เหมือนกับการสร้าง Skill) โดย Agent จะเรียนรู้จากข้อมูลประวัติการเทรด แล้วสร้างเป็นไฟล์ **JSON** หรือ **Markdown** ที่ระบุโครงสร้างการทำงานและชุดคำสั่ง (Prompt Logic) เพื่อให้ Agent นำมาอ่านและประมวลผลได้ด้วยตัวเอง

## 2. Workflow (การทำงานของ Agent)
1. **Research (การวิจัย):** Agent ค้นคว้าหลักการของ Indicator/Strategy ที่ผู้ใช้ต้องการ
2. **Analyze (การวิเคราะห์):** Agent ดึงประวัติการเทรดจาก `mt5-core-server` (ผ่าน endpoint `/history`) มาวิเคราะห์หาความเหมาะสมกับตรรกะที่ค้นคว้ามา
3. **Generate Tool (สร้างไฟล์):** Agent จะทำการสังเคราะห์ข้อมูลทั้งหมดและสร้าง Tool ขึ้นมาในรูปแบบไฟล์ **JSON** 
   - ตัวอย่างข้อมูลใน JSON: `name`, `description`, `triggerKeywords`, `systemPromptAddon` (คำสั่งให้ Agent ดึงข้อมูลมาวิเคราะห์ต่ออย่างไร)
4. **Deployment (การติดตั้ง):** Agent จะบันทึกไฟล์ JSON นี้ไปที่โฟลเดอร์กลาง เช่น `C:\Users\JOJO\AndroidStudioProjects\PersonalAIBot\custom_agent_tools`
5. **UI Update (การแสดงผลบนแอป):** แอปมือถือ (ToolRegistry) จะอ่านไฟล์จากโฟลเดอร์นี้ แล้วดึงเข้าสู่หมวดหมู่ "Create tool" ทันที เมื่อผู้ใช้กดเรียกใช้ Agent ก็จะอ่านคำสั่งจากไฟล์นี้เพื่อเริ่มวิเคราะห์

## 3. ตัวอย่างโครงสร้างไฟล์ Tool (JSON)
```json
{
  "name": "custom_rsi_divergence",
  "description": "วิเคราะห์ RSI Divergence แบบอัตโนมัติจากข้อมูลประวัติ MT5",
  "triggerKeywords": ["วิเคราะห์ rsi", "หา rsi divergence"],
  "author": "Jarvis Agent",
  "systemPromptAddon": "1. ดึงข้อมูลจาก mt5_history \n2. คำนวณค่า RSI 14 \n3. เปรียบเทียบจุดสวิงของราคาและ RSI \n4. สรุปผลว่าเกิด Divergence หรือไม่"
}
```

## 4. App UI Integration
เพิ่ม `ToolCategory.CUSTOM` (แสดงผลว่า "Create tool") ใน `ToolListDialog.kt` เพื่อให้รองรับ Tool ใหม่ที่ถูก Agent สร้างขึ้นมา โดยเชื่อมโยงกับรายการในโฟลเดอร์ `custom_agent_tools`