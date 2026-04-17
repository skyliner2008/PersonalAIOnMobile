# 🕵️ 02_Institutional_Mechanics (ICT Concept)

ทำไมราคาถึงสะบัด? ใครเป็นคนขยับกราฟ? ICT คือการเข้าใจ "Algorithm" ของฝ่ายตรงข้าม (Smart Money) เพื่อเทรดตามรอยเท้าของพวกเขา

---

## 💎 1. Liquidity Pools (แหล่งเงิน)
ราคาขยับจาก "เขตที่ไม่มีสภาพคล่อง" ไปยัง "เขตที่มีสภาพคล่อง" (Stop Loss ของเม่า)

- **BSL (Buy-Side Liquidity)**: เหนือ Old High (เม่าตั้ง Buy Stop / Sell Stop Loss ไว้ที่นั่น)
- **SSL (Sell-Side Liquidity)**: ใต้ Old Low (เม่าตั้ง Sell Stop / Buy Stop Loss ไว้ที่นั่น)
- **Tool**: ใช้ `trading_smc_liquidity` เพื่อหา Stars (★★★) ที่เป็นจุดรวมสภาพคล่อง

## ⏳ 2. Time Control (Kill Zones)
เวลาคือตัวแปรที่สำคัญที่สุด หาก Setup เกิดนอกเวลา มักจะเป็น "สัญญาณหลอก"

| Kill Zone | Time (TH Time) | Focus |
| :--- | :--- | :--- |
| **London Open** | 14:00 - 15:00 | GBP / EUR / Gold |
| **NY Open** | 19:00 - 20:00 | BTC / Crypto / Indices |
| **Silver Bullet** | 21:00 - 22:00 | โมเดลการเทรดที่มีวินเรทสูงสุด |

## 📐 3. Displacement & FVG
การขยับของราคาที่มี "พลัง" (Energetic Move) จะทิ้งช่องว่างไว้ให้เราเข้าเทรด

- **FVG (Fair Value Gap)**: ช่องว่างระหว่างแท่งที่ 1 และ 3 ที่ไม่ซ้อนทับกัน
- **Imbalance**: ราคาต้องลงมาเติม (Fill) ช่องว่างนี้ก่อนจะไปต่อ
- **Tool**: `trading_smc_orderblocks` จะคัดเฉพาะโซนที่มี FVG ยืนยันเท่านั้น

---
> [!IMPORTANT]
> **Silver Bullet Setup**:
> 1. รอราคา Sweep SSL หรือ BSL ในช่วง Kill Zone
> 2. เกิด Market Structure Shift (CHoCH) พร้อม Displacement ทิ้ง FVG ไว้
> 3. กาง Fibonacci หาโซน Discount (<50%)
> 4. เข้าเทรดที่ FVG หรือ Order Block

**Next**: [[03_Execution_SMC_V10]] | [[06_The_Ultimate_Checklist_V12.5]]

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[01_Market_Cycles_Wyckoff]]
