---
title: V26.25 Unified Zone & Execution Quality Gate Simplification
category: Trading Intelligence
tags: [trading-gate, execution-quality, risk-management, scalping, performance]
sources: [autoTradingService.ts, gate_flow_audit.mjs]
created: 2026-05-23
updated: 2026-05-23
---

# V26.25 ระบบตรวจสอบ Unified Zone & Execution Quality และการจัดกลุ่มเกตเทรด (Simplification)

วันที่: 2026-05-23  
สถานะ: Production  

## 📌 บทนำและปัญหาที่พบ (Background & Problem)

หลังจากใช้งานระบบ **Trading Gates** มาถึงเวอร์ชัน V26.24 พบว่าระบบการกรองมีจำนวนเกตมากเกินไป (21 Gates) ทำให้เกิดความซับซ้อนและบล็อกออเดอร์ในขั้นตอนต่าง ๆ โดยไม่จำเป็น (Over-filtering) โดยเฉพาะการบล็อกซ้ำซ้อนกันในส่วนของโซนราคาและสภาวะตลาด ตัวอย่างเช่น อัตราการปฏิเสธการเทรด (Rejection Rate) สูงถึง **98.8%** (ข้าม 11,599 ครั้ง จากทั้งหมด 11,743 รอบการเทรด) 

นอกจากนี้ ยังมีปัญหาจากสเกลค่าคะแนนระบบ Deterministic (Scoring) ที่ไม่ยืดหยุ่นตามสภาพสภาวะตลาด (Market Regime) และระบบ Trade Management ที่จำกัดประสิทธิภาพการทำ Scalping จากปัญหา TP Cap ที่เมื่อโดนจำกัดระยะ TP แล้ว แต่อัตราส่วน SL ไม่ได้รับการย่อตามสัดส่วน ทำให้ RRR (Reward-Risk Ratio) ที่จะส่งเข้าระบบจริงเกิดการผิดเพี้ยนไป

---

## 🛠️ รายละเอียดการปรับปรุงในเวอร์ชัน V26.25 (Key Changes)

ระบบได้รับการพัฒนาและลดรูปเกตแบบบูรณาการ (Gate Simplification) โดยลดจำนวนเกตลงให้เหลือเกตหลักที่ทำงานอย่างมีประสิทธิภาพ รวมถึงปรับปรุงคะแนนและการบริหารความเสี่ยงดังนี้:

### 1️⃣ การรวมและลดความซับซ้อนของเกต (Gate Simplification - Phase 1)
- **Unified Zone Gate:** รวม *Pre-AI Zone Gate* และ *Zone-Aware Gate* เข้าด้วยกัน เพื่อให้การพิจารณาโซนราคา HTF (H4/H1) และการทับซ้อนใน LTF (M30/M15/M5) อยู่ในเกตเดียว ป้องกันไม่ให้สัญญาณเทรดถูกปฏิเสธซ้ำซ้อนกันก่อนที่ระบบ AI จะทันได้ทำการวิเคราะห์ความคุ้มค่า
- **Execution Quality Gate:** รวม *Entry Drift* (ค่าการเลื่อนราคาเข้าทำ) และ *Hard Risk* เข้าด้วยกัน เพื่อวิเคราะห์ความคุ้มค่าและความผันผวนของราคา ณ จุดส่งคำสั่ง (Execution Quality) แบบ Real-time
- **ลดการตรวจสอบซ้ำซ้อน (Duplicate Checks):** ทำการ Clean-up โค้ดในส่วน *Sequential Entry* และ *Order Preflight* เพื่อหลีกเลี่ยงกระบวนการตรวจสอบเดิมซ้ำ ๆ ซึ่งช่วยลดค่า Latency ในการทำงานของ Auto-Trading Engine

### 2️⃣ การปรับปรุงเกณฑ์คะแนนแบบปรับตัว (Regime-Adaptive Scoring - Phase 2)
- พัฒนาฟังก์ชัน `scoreSides()` ให้คิดน้ำหนักความสำคัญแบบแปรผันตามสภาพสภาวะตลาด (Regime-adaptive weights) เพื่อให้เหมาะสมทั้งในช่วงมีเทรนด์ (Trending) และช่วงตลาดสวิงในกรอบ (Ranging)
- ปรับปรุง Score Threshold ให้สเกลขยายหรือบีบแคบลงตาม Regime ความผันผวนของตลาด เพื่อไม่ให้การเข้าทำตึงหรือหย่อนเกินไปในสภาวะตลาดที่แตกต่างกัน

### 3️⃣ การพัฒนา SL/TP Quality & Trade Management (Phase 3)
- **Proportional TP Cap Logic:** เมื่อระยะ Take Profit (TP) ถูกชนเข้ากับกำแพงราคาเชิงโครงสร้าง (Structural Wall) และจำเป็นต้องบีบ TP ลง (Cap TP) ระบบจะคำนวณและ **ย่อระยะ Stop Loss (SL) ลงตามสัดส่วนโดยอัตโนมัติ** เพื่อคงรักษาค่า RRR ที่คาดหวังไว้ไม่ให้เสียเปรียบ
- **Early Invalidation & Aggressive Trailing:** ปรับปรุง Trade Management สำหรับกลยุทธ์ `SCALPING` และ `SMC_FVG_SCALP` ให้ระบบสามารถยอมรับความสูญเสียต่ำสุดและยกเลิกออเดอร์ก่อนเวลาอันควรเมื่อแนวโน้มเปลี่ยน (Early Invalidation) ควบคู่กับการเลื่อนจุดตัดขาดทุนทำกำไรที่ไวขึ้น (Aggressive Trailing) เพื่อรีดประสิทธิภาพในการทำกำไรจาก Scalping ได้สูงสุด

### 4️⃣ การกู้คืนระบบควบคุมความปลอดภัยและการแก้ไขวิเคราะห์สถิติ (Phase 4)
- **Pre-AI Defense Cap Recovery:** แก้ไขข้อผิดพลาดเชิง Syntax ในไฟล์ `autoTradingService.ts` และกู้คืนเกตตรวจสอบเพดานการป้องกันความเสี่ยงสูงสุด ป้องกันไม่ให้ส่งสัญญาณ LLM เกินลิมิต (2x Cap Limit)
- **SQLite Database Fix:** แก้ไขปัญหาการเข้าถึงฐานข้อมูลในสคริปต์วิเคราะห์ประสิทธิภาพ `gate_flow_audit.mjs` โดยเปลี่ยนชื่อเรียกตาราง SQLite จากเดิม `journal` เป็นชื่อที่ใช้จริง `auto_trading_journal` ทำให้อัปเดตสถานะวิเคราะห์ได้อย่างแม่นยำ

### 5️⃣ การบูรณาการฝั่ง Web UI และระบบ Auto Trade (Execution Gates & Strategy Switches)
- **Web UI Dashboard Alignment:** ปรับปรุงไฟล์ [dashboard.js](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/public/dashboard.js) โดยการนำเกตที่ล้าสมัยออก และเพิ่ม `unifiedZoneGate` (Unified Zone Gate) และ `executionQualityGate` (Execution Quality Gate) เข้าสู่ระบบควบคุมสวิตช์อย่างเป็นทางการ ทำให้ผู้ใช้สามารถสั่งเปิด-ปิดเกตหลักแบบบูรณาการผ่านหน้าเว็บได้อย่างสมบูรณ์แบบ
- **Auto Trade Backend Integration:** อัปเดตโครงสร้างระบบและชนิดข้อมูลใน [types.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/types.ts) และลอจิกการทำงานใน [MarketCycleEngine.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/core/MarketCycleEngine.ts) ให้การตัดสินใจประมวลผลและการลงบันทึกการทำงาน (Decision Traces) เกิดขึ้นบนเกตบูรณาการตัวใหม่ทั้งคู่แทนเกตตัวเดิม
- **Backward Compatibility Shield:** ติดตั้งกลไกการรับรองความเข้ากันได้ย้อนหลัง 100% ใน [strategySelector.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/strategySelector.ts) เพื่อแปลงพารามิเตอร์เกตแบบเก่า (เช่น `preAiMtfZoneGate`, `zoneAwareGate`, `entryDriftGate`, `hardRiskGate`) ไปยังเกตบูรณาการตัวใหม่โดยอัตโนมัติหากตรวจพบใน Config เก่า ป้องกันปัญหาระบบหยุดชะงัก (Zero Disruptions)

---

## 📊 ผลลัพธ์จากการรัน Analytics (Key Stats & Analytics)

จากการใช้สคริปต์ `npm run analyze` และ `npm run analytics:audit` หลังการทำงาน:
- **Net Profit ทั้งหมด:** $80.69
- **Win Rate โดยรวม:** 60.27% (ชนะ 88 ครั้ง / แพ้ 58 ครั้ง)
- **ประสิทธิภาพของกลยุทธ์ (Strategy Breakdown):**
  - `SCALPING`: Win Rate 66.7%, PnL +$40.85 (เห็นผลดีขึ้นอย่างมีนัยสำคัญจาก Trade Management ใหม่)
  - `SMC_FVG_SCALP`: Win Rate 76.5%, PnL +$22.80
  - `MEAN_REVERSION`: Win Rate 54.7%, PnL -$161.39 (เป็นกลยุทธ์ที่ต้องติดตามใกล้ชิดจากการโดน Cap บ่อย)
  - `BREAKOUT`: Win Rate 53.3%, PnL +$201.88

---

## 🧪 แผนการตรวจสอบและทดสอบ (Verification)

1. **Unit Testing:** รันคำสั่ง `npm test` เพื่อตรวจสอบ Logic ของเกตและ Trade Management ทั้งหมด
   - *ผลลัพธ์:* **ผ่าน 100% (ทั้งหมด 103 Test Cases ใน 19 ไฟล์ทดสอบ)**
2. **Build Compilation:** รันคำสั่ง `npm run build` เพื่อตรวจหาข้อผิดพลาดทาง Syntax หรือ TypeScript Types
   - *ผลลัพธ์:* **บิวด์สำเร็จ 100% ปราศจาก Errors**
3. **Analytics Audit:** รัน `npm run analyze` เพื่อดูผลการบล็อกของเกตในปัจจุบัน

---

## 🔗 ลิงก์เชื่อมโยงในระบบ (Related Wiki Pages)

- [[54_GateStrategyRuntimeControls_V2624]] — ระบบควบคุม Runtime Controls ตัวเปิด-ปิดเกตแยกชิ้นส่วน
- [[53_EntryDriftSyncedDecisionPrice_V2623]] — การประสานงานราคา Decision Price กับจุด Entry Drift
- [[50_PriceMapExecutionTightening_V2620]] — รายละเอียด RRR และการจัดเกตก่อนหน้านี้
- [[Trading_Intelligence_MOC]] — สารบัญรวมด้าน Trading Intelligence
