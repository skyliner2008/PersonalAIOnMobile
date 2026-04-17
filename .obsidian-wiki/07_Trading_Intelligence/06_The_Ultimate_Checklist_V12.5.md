# 🏆 06_The Ultimate Checklist (SOP V12.5)

นี่คือ "มาตรฐานการปฏิบัติงาน" (Standard Operating Procedure) ของ JARVIS เมื่อได้รับคำสั่งให้วิเคราะห์การเทรด ห้ามข้ามขั้นตอนเพื่อให้ได้ผลลัพธ์ที่เป็นมืออาชีพที่สุด (Intelligence V12.5)

---

## 🚦 1. ขั้นตอนการวิเคราะห์ (The Workflow)

### 🧐 Phase 1: Market Environment (The Context)
*เช็คสภาพแวดล้อมก่อนลงรายละเอียด*
- [ ] รัน `trading_market_snapshot` เพื่อดูทิศทางตลาดรวม
- [ ] รัน `trading_sentiment` และ `trading_news` (มีข่าวร้าย/ข่าวดีไหม?)
- [ ] รัน `trading_fear_greed` เพื่อดูอารมณ์ตลาด (Extreme Fear/Greed?)
- [ ] **Goal**: ระบุความน่าจะเป็นว่าตลาดอยู่ในเฟสไหนของ **Wyckoff**

### 🌏 Phase 2: High Timeframe Bias (The Map)
*หาเทรนหลักเพื่อไม่ให้หลงทาง*
- [ ] รัน `trading_multi_timeframe` (W, D, 4H)
- [ ] สรุป Bias: **BULLISH**, **BEARISH**, หรือ **NEUTRAL**
- [ ] **Goal**: เทรดตามเทรน HTF เท่านั้น

### 🎯 Phase 3: Point of Interest (The Hunt)
*หา "โซนสังหาร" ที่เจ้ามือซ่อนอยู่*
- [ ] รัน `trading_smc_liquidity` เพื่อมองหาจุดที่มี Stars ★ สูง
- [ ] รัน `trading_smc_orderblocks` เพื่อหาโซนสะสมที่มี FVG ยืนยัน
- [ ] **Goal**: ระบุพยากรณ์ราคา (POI) ที่น่าสนใจที่สุด

### 🏛️ Phase 4: Institutional Deep Analysis (The Precision) - [V12.5]
*ตรวจสอบความสอดคล้องระดับสถาบัน (Triple Confirmation)*
- [ ] รัน **`trading_deep_analysis_suite`** 🏛️
- [ ] ตรวจสอบ **LSD Trend State**: ราคาต้องประคองตัวเหนือ Baseline (LSD Bullish)
- [ ] ตรวจสอบ **Orderflow Delta**: มีแรงซื้อ/ขายสะสมยืนยันการกลับตัวจริง (Delta Label)
- [ ] ตรวจสอบ **Confluence Score**: ต้องได้คะแนน **80+** สำหรับการเข้าเทรดคุณภาพสูง
- [ ] **Goal**: ยืนยันว่า Smart Money กำลังเข้าทำในโซนที่เราหาไว้ใน Phase 3

### ⚡ Phase 5: Execution & Trigger (The Kill)
*หาจังหวะกดออเดอร์ใน Timeframe เล็ก*
- [ ] รัน `trading_smc_sweeps` (เกิดการกวาดสภาพคล่องหรือยัง?)
- [ ] รัน `trading_smc_structure` (เกิด **CHoCH** ยืนยันหรือยัง?)
- [ ] คำนวณความเสี่ยงด้วย `trading_position_sizing`
- [ ] **Goal**: เข้าเทรดพร้อมการป้องกันความเสี่ยง (RR 1:2+)

---

## 🛡️ 2. กฎเหล็ก 4 ข้อ (The Golden Rules)

1.  **NO FVG, NO TRADE**: ถ้า Order Block ไม่มี Displacement (FVG) ให้ละเว้น
2.  **LSD ALIGNMENT**: อย่าเข้าซื้อถ้า LSD Trend ยังเป็น Bearish แม้ราคาจะแตะ Demand
3.  **DELTA CONFIRMATION**: สัญญาณกลับตัวที่ดีต้องมาพร้อม Delta Volume ที่พุ่งสูงสวนทาง
4.  **SCORE MATTERS**: ถ้า Confluence Score < 50 ให้รอ (Patience is a skill)

---
> [!IMPORTANT]
> **Intelligence V12.5**: จาร์วิสต้องใช้ `trading_deep_analysis_suite` เป็นตัวตัดสินสุดท้ายก่อนสรุปผลการวิเคราะห์เสมอ

**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[00_Tool_to_Strategy_Map]] | [[07_Advanced_Analysis_V12.5]]
