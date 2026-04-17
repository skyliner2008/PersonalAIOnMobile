# 💾 The 4-Layer Memory Strategy

ระบบหน่วยความจำถูกออกแบบมาเพื่อให้ JARVIS มีความต่อเนื่องในการสนทนาและไม่สูญเสียบริบทสำคัญ

## ชั้นของหน่วยความจำ (Layers)

1. **Layer 1: Core Memory (Long-term)**
   - เก็บข้อมูล "ข้อเท็จจริง" (Facts) ลงใน SQLite เป็น Key-Value
   - ตัวอย่าง: ความชอบของผู้ใช้, API Keys, ข้อมูลส่วนตัวเบื้องต้น
2. **Layer 2: Working Memory (Short-term)**
   - เก็บประวัติการสนทนาล่าสุด 10-20 Turns ใน Context Window ของ AI
3. **Layer 3: Archival Memory (Deep-term)**
   - ใช้ระบบ **Vector Embeddings** สำหรับการค้นหาความหมาย (Semantic Search) ในข้อมูลมหาศาล
4. **Layer 4: GraphRAG Layer**
   - เชื่อมโยงความสัมพันธ์ของข้อมูลแบบ Knowledge Graph เพื่อให้เข้าใจบริบทที่มีความซับซ้อนและซ้อนทับกัน

> [!IMPORTANT]
> **Persistence Fix (V2026.4)**: ข้อมูลดิบจากเครื่องมือ (Static Boxes) จะถูกบันทึกลง SQLite ทันทีเพื่อให้ประวัติไม่หายเมื่อปิดแอป

---
**Links**: [[index]] | [[overview]]
