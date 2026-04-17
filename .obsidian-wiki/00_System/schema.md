# ⬡ Wiki Schema & AI Instructions ⬡

เอกสารนี้คือกฎเหล็กสำหรับ AI (Antigravity/JARVIS) ในการบำรุงรักษาและใช้งาน Wiki นี้

## 🎯 วัตถุประสงค์
เพื่อให้ AI มีบริบทที่แม่นยำที่สุดเกี่ยวกับโปรเจค โดยไม่ต้องโหลดโค้ดทั้งหมดใหม่ทุกครั้ง

## 📜 กฎการเขียน Note
1. **Atomic Notes**: 1 หัวข้อ = 1 ไฟล์ อย่าเขียนยาวเกินไป
2. **Double Brackets**: ใช้ `[[Link]]` ทุกครั้งที่อ้างอิงถึง Component อื่นเพื่อให้ Obsidian สร้าง Graph View ได้
3. **Status Tags**: ใช้ Metadata ในการระบุสถานะ เช่น `status: verified`, `status: deprecated`, `status: experimental`
4. **No Code Spams**: บันทึกเฉพาะ Pattern การเขียนโค้ดและ Business Logic สำคัญ อย่า Copy โค้ดทังไฟล์ลงมา

## 🔄 กระบวนการทำงาน (Operations)
- **Ingest**: เมื่อมีการเพิ่มฟีเจอร์ใหม่ AI ต้องอัปเดต Note ที่เกี่ยวข้องและเพิ่มประวัติใน `log.md`
- **Link**: ตรวจสอบเสมอว่า Notes ใหม่มี Link กลับไปยัง `index.md` หรือหน้าหมวดหมู่ที่เกี่ยวข้อง
- **Lint**: ตรวจสอบความขัดแย้งของข้อมูล (Contradiction) เมื่อข้อมูลใน Wiki ไม่ตรงกับโค้ดจริง

## 🧭 การเรียกใช้บริบท
เมื่อผู้ใช้สั่งให้ "อ่าน Wiki" หรือ "Start Session" ให้ AI เริ่มอ่านจาก `00_System/index.md` เสมอ
