# 🌉 LiveToolBridge

`LiveToolBridge.kt` ทำหน้าที่เป็น "ตัวจัดคิวเครื่องมือ" (Tool Orchestrator) สำหรับโหมด Live (Multimodal) โดยเฉพาะ

## 🎯 หน้าที่หลัก
1. **Tool Redirection**: แยกการทำงานระหว่าง Path A (Native) และ Path B (Bridge)
2. **Context Injection**: เชื่อมโยงบริบทจากคำพูดล่าสุดของผู้ใช้เข้ากับเครื่องมือ
3. **UI Hooking**: ควบคุมสถานะกล้อง (`vision_activate/deactivate`) และการเปลี่ยนเสียง

## ⚙️ การประมวลผล (Processing Paths)
- **Path A (Native)**: ใช้ Gemini Function Calling ดั้งเดิม (เช่น สั่งเปิดกล้อง)
  - แต่ละ call รันใน job ของตัวเอง (tool ช้าไม่บล็อก call อื่น) และถูกยกเลิกได้จาก `toolCallCancellation`
  - ส่งผลผ่าน `respond(event, text, deliverIfStale)` — ถ้า session เปลี่ยนระหว่างรัน tool จะส่งผลเป็น realtime text เข้า session ใหม่แทน (ผลไม่หายเงียบ)
- **Path B (Bridge)**: ใช้ Model Text-to-Intent (เช่น "ช่วยสรุปไฟล์นี้หน่อย")

## 🛡️ Guards (`LiveIntentMatchers`)
- Keyword matchers ย้ายไป `ai/LiveIntentMatchers.kt` (มี unit test)
- Redirect guard ทุกตัวผ่าน `canRedirectMisroutedTool(tool, prompt)` = tool อยู่ในกลุ่มที่ model มักเรียกผิด **และคำขอไม่ใช่คำถามเทรด** (กันเคส "ราคาทองจะไปที่ 2400" เปิด Google Maps)
- Navigation ต้องมีคำกริยาเดินทางชัดเจน (ไม่ใช้ "ไปที่" เดี่ยวๆ)
- Trading AI Profile: TF เริ่มต้น M15/H1/H4 แต่ **อนุญาต D1/W1 เมื่อผู้ใช้ระบุเอง**; counter ต่อ turn อยู่ใต้ mutex
- `device_notification_reply` ข้อความเดิมภายใน 20 วินาทีถูกบล็อก (กันส่งซ้ำ)

## 👁️ การจัดการสายตา (Vision Logic)
มีการป้องกันความผิดพลาด (Anti-Hallucination) โดยการบังคับฉีด Prompt ให้ AI รอสังเกตการณ์ 1-2 วินาทีหลังเปิดกล้อง

---
**Links**: [[overview]] | [[LiveGeminiService]] | [[Changelog_2026-09-16_Live_Voice_Review_Fixes]] | [[index]]
