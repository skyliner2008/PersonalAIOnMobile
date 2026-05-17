# 🔄 Auto Trading Cycle: System vs AI/API Breakdown

ใน 1 รอบการทำงาน (ทุกๆ 1-5 นาที) ระบบจะทำตามขั้นตอนดังนี้:

## 0. 💤 ขั้นตอนการเตรียมการ (Preparation & Market Guard)
เช็คความพร้อมของระบบและตลาดก่อนเริ่มงาน

| ลำดับ | สิ่งที่ทำ | ใครทำ | หน้าที่ |
| :--- | :--- | :--- | :--- |
| 0.1 | **Market Guard** | 🖥️ **System** | เช็คสถานะตลาด (Session) ถ้าตลาดปิดจะเข้าโหมด `SLEEPING` ทันทีเพื่อประหยัดทรัพยากรและลด Error |

## 1. 🛡️ ขั้นตอนการดูแลพอร์ต (Portfolio Management) — V24.1
ขั้นตอนนี้เน้นความเร็วและความแม่นยำ เพื่อปกป้องเงินทุน

| ลำดับ | สิ่งที่ทำ | ใครทำ | หน้าที่ |
| :--- | :--- | :--- | :--- |
| 1.1 | **Audit Positions** | 🖥️ **System** | ดึงข้อมูลออเดอร์ทั้งหมดจาก MT5, คำนวณ P&L/Swap/Commission/R-Multiple, **แสดง position age (`age=Nm`)** |
| 1.2 | **Stale-Position Time Stop** ⏱️ | 🖥️ **System** | (V24.1) ปิด position ที่ค้างใน R∈[`stalePositionMinR`, `stalePositionMaxR`] นานกว่า `stalePositionMaxAgeMs` (default 45m) เพื่อตัด churn |
| 1.3 | **Regime-Flip Exit** 🔄 | 🖥️ **System** | (V24.1) เรียก `detectRegimeFlipAction()` ตรวจ CHoCH against cluster → ปิด losing leg ก่อน SL hit |
| 1.4 | **SMC-Aware Trail** | 🖥️ **System** | (V24.1) เรียก `computeSmcTrailTargets()` เลื่อน SL ไปขอบ OB/FVG/Swing/Liquidity ที่ใกล้ที่สุด (เหนือกว่าสูตร ATR) |
| 1.5 | **BE / ATR Trail Fallback** | 🖥️ **System** | BE@0.5R (scalp 0.3R), TRAIL@0.6R (scalp 0.4R) — skip ATR trail ถ้า SMC trail จัดการแล้ว |
| 1.6 | **CPP / CLP** | 🖥️ **System** | Cluster Profit/Loss Protection (เดิม): lock peak profit, emergency close cluster ที่ R<-1.5 หรือ PnL<-$150 |
| 1.7 | **Post-Mortem** | 🧠 **API (AI)** | **(เฉพาะเมื่อมีไม้ปิด)** สรุปบทเรียนว่าทำไมถึงปิด และบันทึกเข้าความจำ (Vector Store) |

---

## 2. 📊 ขั้นตอนการอ่านตลาด (Market Analysis)
เตรียมข้อมูลดิบและสร้างเรื่องราวของตลาด

| ลำดับ | สิ่งที่ทำ | ใครทำ | หน้าที่ |
| :--- | :--- | :--- | :--- |
| 2.1 | **Data Fetching** | 🖥️ **System** | ดึงข่าวเศรษฐกิจ (ForexFactory) และข้อมูลกราฟ (Candles) หลาย Timeframe |
| 2.2 | **Indicators** | 🖥️ **System** | คำนวณ RSI, ATR, SMA, SMC (FVG/OB) และ Session (ตลาดยุโรป/เมกา) |
| 2.3 | **Narrative Build** | 🧠 **API (AI)** | **(Analyst Agent)** นำข้อมูลเทคนิค + ข่าว + ความจำในอดีต มาเขียนเป็นวิเคราะห์เชิงลึกและกำหนด Bias |

---

## 3. ⚖️ ขั้นตอนการคุมความเสี่ยง (Risk Assessment)
เป็นด่านตรวจคนเข้าเมืองก่อนจะส่งออเดอร์

| ลำดับ | สิ่งที่ทำ          | ใครทำ           | หน้าที่                                                                                  |
| :---- | :----------------- | :-------------- | :--------------------------------------------------------------------------------------- |
| 3.1   | **Risk Review**    | 🧠 **API (AI)** | **(Risk Officer)** ตรวจสอบข้อเสนอจาก Analyst เทียบกับเงินในพอร์ต และจำนวนออเดอร์         |
| 3.2   | **Override Logic** | 🧠 **API (AI)** | ตัดสินใจว่าจะอนุญาตให้ "ข้ามขีดจำกัด" หรือไม่ (ในกรณีที่เป็นการ Hedge เพื่อป้องกันพอร์ต) |
| 3.3   | **Execution Plan** | 🧠 **API (AI)** | **(Execution Agent)** เลือกประเภทคำสั่ง (Market/Limit/Stop) และกลยุทธ์การส่งราคา         |

---

## 4. 🚀 ขั้นตอนการส่งคำสั่ง (Execution & Logging)
การลงมือทำจริงและบันทึกข้อมูล

| ลำดับ | สิ่งที่ทำ | ใครทำ | หน้าที่ |
| :--- | :--- | :--- | :--- |
| 4.1 | **Hard Gate** | 🖥️ **System** | **(Strict Enforcement)** เช็คด่านความเสี่ยง (Risk Gate) และ Circuit Breaker หากไม่ผ่านจะ SKIP ทันที 100% |
| 4.2 | **Order Send** | 🖥️ **System** | ส่งคำสั่งผ่าน MT5 Bridge ไปยัง MetaTrader 5 |
| 4.3 | **Persistence** | 🖥️ **System** | บันทึก Log, บันทึก Journal ลง SQLite และบันทึก Embedding Snapshots ลง VectorStore |

---
## 5. 🔄 ระบบสำรองข้อมูลและการสลับโมเดล (AI Resilience)
เพื่อให้แน่ใจว่าระบบจะไม่หยุดเทรดเมื่อโมเดลหลักมีปัญหา:

- **Smart Free Fallback**: เมื่อโมเดลหลักล้มเหลว (Rate Limit/Timeout) ระบบจะเปิดโหมดสำรองทันที
- **OpenRouter Free-to-Free Rotation**: หากใช้ OpenRouter ระบบจะคัดกรองโมเดลที่เป็น `isFree: true` ตัวอื่นมาลองใหม่ทันที 2 รอบ
- **Universal Backup**: หากโมเดลสำรองใน OpenRouter ยังคงล้มเหลว ระบบจะสลับไปใช้ **Gemini 1.5 Flash (Free)** เป็นด่านสุดท้ายเพื่อการันตีความเสถียร

---
## 💡 สรุปภาพรวม
- **System (🖥️)**: ทำหน้าที่เป็น "แขนขา" และ "เครื่องคิดเลข" (รวดเร็ว, แม่นยำ, ไม่มีความรู้สึก)
- **API/AI (🧠)**: ทำหน้าที่เป็น "สมอง" และ "หัวหน้าเทรดเดอร์" (เข้าใจบริบท, ยืดหยุ่นตามนโยบาย, เรียนรู้จากอดีต)

---
**Last Updated**: 2026-04-28 (Phase 4.1 Stability Update)
**Related**: [[13_AI_Pro_Trader_Roadmap]], [[01_Architecture/overview]]
