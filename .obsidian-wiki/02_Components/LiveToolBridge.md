# 🌉 LiveToolBridge

`LiveToolBridge.kt` ทำหน้าที่เป็น "ตัวจัดคิวเครื่องมือ" (Tool Orchestrator) สำหรับโหมด Live (Multimodal) โดยเฉพาะ

## 🎯 หน้าที่หลัก
1. **Tool Redirection**: แยกการทำงานระหว่าง Path A (Native) และ Path B (Bridge)
2. **Context Injection**: เชื่อมโยงบริบทจากคำพูดล่าสุดของผู้ใช้เข้ากับเครื่องมือ
3. **UI Hooking**: ควบคุมสถานะกล้อง (`vision_activate/deactivate`) และการเปลี่ยนเสียง

## ⚙️ การประมวลผล (Processing Paths)
- **Path A (Native)**: ใช้ Gemini Function Calling ดั้งเดิม (เช่น สั่งเปิดกล้อง)
- **Path B (Bridge)**: ใช้ Model Text-to-Intent (เช่น "ช่วยสรุปไฟล์นี้หน่อย")

## 👁️ การจัดการสายตา (Vision Logic)
มีการป้องกันความผิดพลาด (Anti-Hallucination) โดยการบังคับฉีด Prompt ให้ AI รอสังเกตการณ์ 1-2 วินาทีหลังเปิดกล้อง

---
**Links**: [[overview]] | [[LiveGeminiService]] | [[index]]
