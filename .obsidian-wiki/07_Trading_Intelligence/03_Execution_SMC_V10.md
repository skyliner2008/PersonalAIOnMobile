# 🎯 03_Execution (Smart Money Concepts V10)

นี่คือขั้นตอนสุดท้าย (The Trigger) เมื่อเรามี Bias จาก Wyckoff และเห็น Liquidity จาก ICT แล้ว เราจะรันเครื่องมือ SMC เพื่อหาจุดเข้าที่ "คม" ที่สุด

---

## ⚡ 1. โครงสร้างตลาด (Market Structure)
การมองให้ออกว่าเทรนกำลังไปต่อ หรือกำลังจะกลับตัว

- **BOS (Break of Structure)**: ราคาเบรก High/Low เดิมในทิศทางเดียวกับเทรนหลัก (สัญญาณความต่อเนื่อง)
- **CHoCH (Change of Character)**: ราคาเบรก Low ของขาขึ้น หรือ High ของขาลงเป็นครั้งแรก (สัญญาณกลับตัว)
- **Tool**: `trading_smc_structure` (หา CHoCH ใน TF เล็กเพื่อเข้าเทรด)

## 📦 2. โซนเข้าเทรด (Order Blocks)
สถาบัน "ทิ้ง" รอยเท้าไว้ในรูปแบบของแท่งเทียนที่เต็มไปด้วยออเดอร์มหาศาล

- **Bullish OB**: แท่ง Bearish สุดท้ายก่อนราคาจะพุ่งพรวดเบรกโครงสร้างขึ้นไป
- **Bearish OB**: แท่ง Bullish สุดท้ายก่อนราคาจะทุบพรวดเบรกโครงสร้างลงมา
- **Confirmations**: 
    1. ต้องมี **FVG** ติดอยู่กับ OB
    2. ต้องทำให้เกิด **BOS/CHoCH**
    3. ราคาต้องอยู่ในโซน **Discount** (<50%) สำหรับ Long หรือ **Premium** (>50%) สำหรับ Short
- **Tool**: `trading_smc_orderblocks` (คัดกรอง Order Blocks ที่มีคุณภาพให้ทันที)

## 🧹 3. Liquidity Sweeps (The Hunt)
จังหวะที่ "เจ้ามือ" กิน Stop Loss ของคนอื่น คือจังหวะที่เราควรเข้า

- **Sweep**: ไส้เทียน (Wick) จิ้มลงไปใต้แนวรับสำคัญแล้วดันทิ้งตัวกลับขึ้นมาอย่างรวดเร็ว (>50% Reclaim)
- **Tool**: `trading_smc_sweeps` (เช็คสัญญาณ Sweep ทั่วทุก Timeframe)

---
> [!CAUTION]
> **Warning**: อย่าเข้าเทรดที่ Order Block ที่มีคนวาง Stop Loss ไว้หนาแน่น (Equal Lows) เพราะเจ้ามักจะลงมา "Sweep" ให้เรียบร้อยก่อนจะพุ่งจริง เสมอ!

**Next**: [[04_Momentum_and_Scanners]] | [[06_The_Ultimate_Checklist_V12.5]]

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[02_Institutional_Mechanics_ICT]]
