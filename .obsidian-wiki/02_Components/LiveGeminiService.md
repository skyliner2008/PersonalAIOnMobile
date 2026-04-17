# 🔌 LiveGeminiService

`LiveGeminiService.kt` คือหัวใจของระบบ Multimodal Live API ที่ทำงานผ่าน WebSocket

## 🎯 หน้าที่หลัก
1. **WebSocket Connection**: จัดการการเชื่อมต่อ bidi-streaming กับ Google Generative API
2. **Data Streaming**: รับ-ส่ง Audio (PCM), Video (JPEG) และ Text แบบ Real-time
3. **Transcription Handling**: จัดการการแปลงเสียงเป็นข้อความทั้งฝั่ง User และ Model
4. **Persistence**: บันทึกบทสนทนาที่เสร็จสมบูรณ์ลง SQLite ผ่าน [[memory_strategy]]

## 🗣️ รองรับเสียง (Voice Config)
ระบบรองรับเสียง prebuilt กว่า 30 แบบ (Aoede เป็นค่าเริ่มต้น)

---
**Links**: [[overview]] | [[JarvisViewModel]] | [[index]]
