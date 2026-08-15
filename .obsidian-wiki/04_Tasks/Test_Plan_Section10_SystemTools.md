# 🧪 ชุดทดสอบหมวด 10 — System Tools

> สร้าง: 2026-08-11 | ติดตั้ง APK ล่าสุดก่อนเทส | เก็บ logcat tag: Orchestrator, JarvisVM, GeminiService, LiveGemini

## A. Diagnostics & Connectivity

| # | คำสั่ง | คาดหวัง | ผล |
|---|--------|---------|-----|
| 1 | `ตรวจสอบสุขภาพระบบหน่อย` (แชท) | เรียก `system_run_diagnostics` → Health Report ครบหมวด พร้อมจำนวน jobs/tasks/archival จริง + บันทึกลง wiki | ✅ 2026-08-11 |
| 2 | `เช็คการเชื่อมต่อ API ทั้งหมด` | เรียก `system_check_connectivity` → PASS/FAIL รายบรรทัด (Yahoo/TV) |✅ 2026-08-11 |
| 3 | "ตรวจสอบระบบให้หน่อย" (Live) | tool ทำงานผ่าน bridge + พูดสรุป 5-8 ประโยค ไม่เงียบ |✅ 2026-08-11 |

## B. Custom Tool CRUD (ทบทวนแบบเร็ว)

| # | คำสั่ง | คาดหวัง | ผล |
|---|--------|---------|-----|
| 4 | `สร้าง tool ชื่อ btc_quick_check ไว้ดูราคาและ RSI ของ BTCUSDT` | ✅ สร้างสำเร็จ ไม่มี 400 duplicate รอบถัดไป |✅ 2026-08-11 |
| 5 | `มี custom tool อะไรบ้าง` | ลิสต์ครบพร้อม logic ข้างใน |✅ 2026-08-11 |
| 6 | `แก้ btc_quick_check เพิ่ม MACD ด้วย` | เขียนทับชื่อเดิม ใช้งานได้ MACD จริง |✅ 2026-08-11 |
| 7 | "เช็คทองคำแบบเร็วให้หน่อย" (Live) | custom_gold_check ทำงานใน live + พูดผล |✅ 2026-08-11 |
| 8 | `ลบ btc_quick_check` | ลบสำเร็จ list ซ้ำไม่เหลือ เปิดแอปใหม่ไม่กลับมา | ☐ |
| 9 | ปิด/เปิดแอป → `มี custom tool อะไรบ้าง` | custom_gold_check persist ข้าม session |✅ 2026-08-11 |

## C. Edge Cases

| # | คำสั่ง | คาดหวัง | ผล |
|---|--------|---------|-----|
| 10 | `ลบ tool ชื่อ not_exist_tool` | ตอบสุภาพว่าไม่พบ + แนะนำ tool ที่มี ไม่ crash |✅ 2026-08-11 (หลัง fix LIVE_RULES ข้อ 9) |

## เกณฑ์ผ่าน
- ผ่านครบ 10/10 → ปิดหมวด 10 เป็น ✅
- ข้อไหน fail เก็บ logcat ช่วงเวลาที่เทสส่งมา
