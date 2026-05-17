# บันทึกการปรับปรุง Web Dashboard JARVIS MT5 (17 พฤษภาคม 2026)

## รายละเอียดการแก้ไข
1. **ยกระดับ UI/UX เป็นแบบ Premium**:
   - ปรับปรุง Palette สีให้มืดและมีมิติมากขึ้น (Deep OLED Black).
   - เพิ่ม Effect Glassmorphism (Backdrop-filter: blur) ให้กับ Panel ต่างๆ.
   - เพิ่ม Gradient ให้กับปุ่มและองค์ประกอบสำคัญ.
   - ใช้ Font Inter ร่วมกับ Fira Code สำหรับข้อมูลตัวเลข.

2. **เพิ่มระบบจัดการ Symbol**:
   - เพิ่มปุ่ม "Watch/Unwatch" ในตาราง Market Symbols.
   - เชื่อมต่อกับ API `POST /api/mt5/auto/config` เพื่ออัปเดต `watchlist` แบบ Real-time.

3. **เพิ่มระบบจัดการ MT5 Client**:
   - เพิ่ม Panel "MT5 Terminal" ในหน้า System.
   - แสดงรายการ Client ที่กำลังทำงานอยู่ (PID และ Path).
   - รองรับการเปิด Client ใหม่โดยระบุ Path และการปิด Client ตาม PID.

## ไฟล์ที่แก้ไข
- `mt5-core-server/public/dashboard.css`
- `mt5-core-server/public/index.html`
- `mt5-core-server/public/dashboard.js`

## สถานะ
- ดำเนินการแก้ไขโค้ดเรียบร้อยแล้ว รอการทดสอบและยืนยันจาก User.
