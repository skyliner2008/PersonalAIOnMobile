# ⬡ คลังเครื่องมือ JARVIS (Tool Catalogue) ⬡

หน้านี้รวบรวมรายละเอียดของเครื่องมือ (Tools) ทั้งหมดที่ JARVIS สามารถเรียกใช้ได้ เพื่อให้ AI เข้าใจความสามารถและข้อจำกัดของแต่ละเครื่องมือ

## 🛠️ หมวดหมู่เครื่องมือ

### 🧠 1. Built-in & Logic
- **`get_current_datetime`**: ดึงวันที่และเวลาปัจจุบัน (ISO 8601)
- **`calculate`**: เครื่องคิดเลขสำหรับสมการทางคณิตศาสตร์
- **`convert_units`**: แปลงหน่วยต่างๆ (Metric/Imperial)
- **[[memory_strategy]]**: ชุดเครื่องมือสำหรับจัดการความจำ (`remember_fact`, `recall_memory`)

### 📊 2. Trading & Market Analysis
- **`trading_price`**: เช็คราคาหุ้น/Crypto ล่าสุด (Real-time)
- **`trading_market_snapshot`**: ดูภาพรวมตลาด (Gainers/Losers, Volume)
- **`trading_technical_analysis`**: คํานวณอินดิเคเตอร์เชิงเทคนิค (RSI, MACD, EMA)
- **[[00_Tool_to_Strategy_Map]]**: เครื่องมือวิเคราะห์ตามแนวทาง Smart Money Concepts (BOS, CHoCH, Liquidity)

### 📁 3. File & Data Management
- **`file_list`**: แสดงรายชื่อไฟล์ในโปรเจค
- **`file_read` / `file_write`**: อ่านและแก้ไขไฟล์
- **`file_analyze`**: วิเคราะห์เนื้อหาในไฟล์ด้วย AI แยกส่วน

### 📷 4. Camera & Vision
- **`vision_activate`**: เปิดตาระบบ Live Vision (Path A)
- **`vision_deactivate`**: ปิดตาและหยุดสตรีม
- **`camera_analyze_scene`**: ถ่ายภาพนิ่งและวิเคราะห์ฉาก (Non-Live)
- **[[vision_system]]**: การจัดการภาพลักษณ์ การมองเห็น และการประมวลผลฉาก

---
## 🚦 สถานะเครื่องมือ (Tool Status)
| รหัสเครื่องมือ | สถานะ | หมายเหตุ |
| :--- | :--- | :--- |
| `trading_*` | ✅ Verified | เชื่อมต่อกับ API การเงินจริง |
| `vision_*` | ✅ Verified | ทำงานร่วมกับ Adaptive FPS |
| `memory_*` | 🚧 Tuning | กำลังปรับจูน Semantic Search |

---
**Links**: [[index]] | [[overview]] | [[LiveToolBridge]] | [[00_Tool_to_Strategy_Map]]
