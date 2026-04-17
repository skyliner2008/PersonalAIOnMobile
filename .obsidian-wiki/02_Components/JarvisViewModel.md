# 🧠 JarvisViewModel

`JarvisViewModel.kt` เป็นตัวประสานงาน (Mediator) ระหว่าง UI (Compose) และ Business Logic (AI Services)

## 🎯 หน้าที่หลัก
1. **State Management**: จัดการ `_messages`, `_isListening`, `_isTyping` และ Camera States
2. **Settings**: โหลดและบันทึก API Keys และ Model Configurations
3. **Bridge Lifecycle**: ควบคุมการเริ่ม/หยุด Lifecycle ของ Mic, Camera และ Live API
4. **Task Persistence**: บันทึกและดึงข้อมูลประวัติจากฐานข้อมูลตอนเริ่มต้น

## 🔃 การสลับเสียง (Dynamic Voice Change)
รองรับการเปลี่ยนเสียงขณะใช้งานโดยการ Restart Session และส่ง "Pending Command" ไปยัง Session ใหม่เพื่อให้การทำงานไม่สะดุด

---
**Links**: [[index]] | [[LiveGeminiService]]
