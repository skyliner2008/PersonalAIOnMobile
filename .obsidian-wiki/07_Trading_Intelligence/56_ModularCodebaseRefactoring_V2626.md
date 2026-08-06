---
title: V26.26 Modular Codebase & Verification Pass
category: Trading Intelligence
tags: [trading-engine, codebase-refactor, typescript, unit-testing, type-safety]
sources: [autoTradingService.ts, decisionLogger.ts, strategySelector.ts]
created: 2026-05-23
updated: 2026-05-23
---

# V26.26 ระบบโครงสร้าง Modular Codebase และกระบวนการลดขนาดไฟล์ระบบเทรดหลัก (Refactoring)

วันที่: 2026-05-23  
สถานะ: Production  

---

## 📌 บทนำและปัญหาที่พบ (Background & Problem)

เมื่อระบบเทรดอัตโนมัติ **PersonalAIBot** เติบโตขึ้นและรองรับฟังก์ชันการวิเคราะห์ที่ครอบคลุม (เช่น ระบบ Zone-Aware, PriceMap, Wall State Machine, Multi-Timeframe, slippage guard, และ parameter toggles) ส่งผลให้ไฟล์ระบบหลัก `autoTradingService.ts` มีขนาดบวมโตขึ้นเรื่อยๆ จนมีคว�## 📂 โครงสร้างสถาปัตยกรรมโมดูลใหม่ (Modular Architecture)

เพื่อลดความซับซ้อน ได้มีการจัดเตรียมโฟลเดอร์สำหรับโมดูลช่วยเหลือใหม่ที่ `src/services/auto/helpers/` โดยแยกตรรกะออกเป็น 5 โมดูลช่วยเหลือหลักอย่างชัดเจน:

### 1️⃣ [decisionLogger.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/decisionLogger.ts)
ทำหน้าที่รวบรวมฟังก์ชันสำหรับแปลงและย่นย่อข้อมูลวิเคราะห์ (Analytic Logs) เพื่อส่งเข้าสู่ Dashboard และประวัติการตัดสินใจของบอท:
- **`compactAnalysisForLog`:** ปรับลดและตัดขนาดสัญญาณ MTF เพื่อลดขนาดการจัดเก็บ log
- **`compactMtfForLog`:** จัดเตรียม log รวมผลวิเคราะห์ของทุก Timeframe
- **`compactDeterministicForLog`:** ปรับแต่งการเก็บผลการตัดสินใจฝั่ง Deterministic
- **`classifyDecisionBlockCategory`:** จำแนกประเภทของเกตที่สกัดกั้นสัญญาณเทรด (เช่น `MARKET_CLOSED`, `MTF_CONFLICT`, `ENTRY_DRIFT`, `RSI_EXTREME`) เพื่อใช้ในฐานข้อมูลและประมวลผลบน UI

### 2️⃣ [strategySelector.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/strategySelector.ts)
ทำหน้าที่รวบรวมตรรกะการประเมินกลยุทธ์, คัดเลือกแผนงาน (TradePlan Candidates) และเกตย่อยในการตัดสินใจแบบไม่พึ่งพา AI (Deterministic Quick Checks) รวมทั้งสิ้น 24 ฟังก์ชัน เช่น `isGateEnabled`, `isStrategyEnabled`, `disabledStrategyReason`, `selectUnifiedV25Candidate`, `resolveFreshDecisionPrice`, `v25AlignedDirectEntry`, และ `postTakeProfitCooldownIssue` เป็นต้น

### 3️⃣ [tradingHelpers.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/tradingHelpers.ts)
ทำหน้าที่รวบรวมฟังก์ชันย่อยในการคำนวณและจัดรูปแบบข้อมูลเกี่ยวกับการเทรดทั่วไป:
- **`buildPositionCluster`:** จัดกลุ่มและประมวลผลสถิติฝั่ง BUY/SELL/NetVolume/Profit ตามคู่เงิน
- **`clampFraction`:** ตรวจสอบและบังคับอัตราขนาดออเดอร์ให้ปลอดภัยตามเกณฑ์ขั้นต่ำและสูงสุด
- **`extractTicket`:** สกัดรหัส Ticket ออเดอร์ที่พึ่งส่งผ่าน Execution service
- **`getTfMs`:** แปลงค่าหน่วยเวลาของ Timeframe เป็นมิลลิวินาที
- **`buildSkipDecision`:** ช่วยประกอบข้อมูลผลตัดสินใจประเภทข้ามสถิติ (SKIP) คืนให้เครื่องยนต์เทรด

### 4️⃣ [marketHelpers.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/marketHelpers.ts)
ย้ายตรรกะการประเมินเวลาเปิดทำการของตลาด (Market Hours Check) และสถานะความพร้อมส่งออเดอร์ของคู่เงิน:
- **`isSymbolTradable`:** ตรวจสอบรหัสคู่เงิน สถานะการเทรด (trade_mode) และเช็ค tick ล่าสุดจาก broker ว่าสดใหม่หรือไม่ พร้อมจัดการ Cache ในระดับโมดูล (`tradableCache`) เพื่อป้องกันการทับซ้อนและการโหลด API หนักเกินไป (TRADABLE_TTL = 60 วินาที)
- **`isMarketOpen`:** ตรวจเช็ค watchlist ทุกสัญลักษณ์เพื่อตรวจสอบว่าตลาดเปิดทำการอยู่หรือไม่

### 5️⃣ [closedTradeService.ts](file:///c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/src/services/auto/helpers/closedTradeService.ts)
จัดการวงรอบตรวจจับออเดอร์ปิดและประมวลผลวิเคราะห์ผลลัพธ์ผ่าน AI Post-Mortem:
- **`detectAndReviewClosedTrades`:** ตรวจหาออเดอร์ที่ถูกปิดลงและทำการประมวลผล AI Review, ปรับเรตติ้งโมเดล, จดจำบทเรียนลง Vector Database และส่งการแจ้งเตือน alert ไปยังแอปพลิเคชันมือถือ
- **`logDeferredClosedDeal` / `clearDeferredLog`:** ระบบควบคุมความถี่การแจ้งเตือน (Throttle Logs) สำหรับออเดอร์ที่ปิดแต่ยังไม่พบประวัติปิดดีลชั่วคราว ป้องกัน log สแปมถี่เกินไป

---

## 🛠️ รายละเอียดการปรับปรุงไฟล์หลัก (autoTradingService.ts)

1. **การทำลาย Legacy Helpers ส่วนเกินท้ายไฟล์อย่างสมบูรณ์:**
   - ลบฟังก์ชันช่วยเหลือเดิม ได้แก่ `extractTicket`, `_tradableCache`, `isSymbolTradable`, `isMarketOpen` และ `getTfMs` ออกจากไฟล์หลัก `autoTradingService.ts` โดยมีขนาดบรรทัดลดลงเหลือ **5,417 บรรทัด** (ประหยัดพื้นที่และช่วยลดความซับซ้อนของหน่วยความจำ)
   - ปรับการเรียกใช้งานในรอบ Cycle (`runCycle()`), การจัดการออเดอร์ (`runManage()`) และกระบวนการเรียนรู้บอท (`runLearn()`) โดยสลับมานำเข้า (Import) และเรียกใช้งานฟังก์ชันของโมดูลย่อยภายนอกแทนอย่างถูกต้องครบถ้วน
2. **รักษาความเข้ากันได้ย้อนหลัง (Backward Compatibility):**
   - รักษา Class `AutoTradingService` และอินสแตนซ์ Singleton `autoTradingService` ไว้ที่เดิม ไร้ผลกระทบต่อเราเตอร์ภายนอกและการเชื่อมต่อของ API ฝั่ง Mobile App
   - ตัวแปรหรือ callback สำคัญที่ต้องพึ่งพาสภาวะการทำงานของคลาส (เช่น `fetchPositions`, `fetchHistory`, `markManagementOutcome`) ถูกส่งผ่านการผูกบริบท `.bind(this)` เข้าไปยังโมดูลย่อยได้อย่างสมบูรณ์แบบ
3. **แก้ไขความสอดคล้องของ Imports:**
   - การคอมไพล์รอบแรกพบข้อผิดพลาดเนื่องจาก `asRecord` และ `unwrapData` นำเข้าผิดที่ในโมดูลช่วยเหลือ ได้ดำเนินการแก้ไขไปนำเข้าจาก `../utils.js` (โมดูล Utility หลัก) ส่งผลให้ระบบ Type Safety ปลอดภัย 100%.ts)
ทำหน้าที่รวบรวมตรรกะการประเมินกลยุทธ์, คัดเลือกแผนงาน (TradePlan Candidates) และเกตย่อยในการตัดสินใจแบบไม่พึ่งพา AI (Deterministic Quick Checks) รวมทั้งสิ้น 24 ฟังก์ชัน:
- **`isGateEnabled`:** ตรวจสอบว่าระบบคุมเกตชิ้นส่วนนั้นๆ ได้รับการตั้งค่าให้เปิดทำงานอยู่หรือไม่ (เชื่อมต่อร่วมกับ `DEFAULT_GATE_TOGGLES`)
- **`isStrategyEnabled` / `disabledStrategyReason`:** ตรวจจับและคัดกรองการปิดระบบกลยุทธ์ชั่วคราว
- **`selectUnifiedV25Candidate`:** คัดเลือกแผน Candidate ที่แข็งแกร่งที่สุดตามค่า RRR และ Stars ของตรรกะ V25
- **`resolveFreshDecisionPrice`:** ตรวจสอบและดึงราคา ณ ปัจจุบันที่สดใหม่ที่สุด (Tick Mid > Symbol Mid > Broker Price > Candle Close) เพื่อใช้ส่งเข้าเกตตัดสินใจ
- **`v25AlignedDirectEntry`:** ตรวจสอบการทับซ้อนและจุดเข้าทำออเดอร์ร่วมกับสภาวะกำแพงราคา (Wall State Machine)
- **`postTakeProfitCooldownIssue`:** สกัดกั้นการเปิดออเดอร์ซ้ำซ้อนในทิศทางเดิมทันทีหลังจากที่เพิ่งปิดทำกำไร (WIN/TP) เพื่อลด Drawdown
- ฟังก์ชันระบุพฤติกรรมกลยุทธ์อื่นๆ เช่น `isScalpLikeStrategy`, `isV25Strategy`, `targetRrrForStrategy`, `finalRrrFloorForStrategy`, `describeZoneMissingEvidence` เป็นต้น

---

## 🛠️ รายละเอียดการปรับปรุงไฟล์หลัก (autoTradingService.ts)

1. **กู้คืนระบบความปลอดภัยและ Type Safety:** 
   - แก้ไขข้อผิดพลาด `TS2305` ที่หาอินเทอร์เฟซ `TradePlan` ไม่พบในสถาปัตยกรรม V25 โดยดึงการนำเข้าจากโมดูล `src/services/auto/v25/playbooks/types.ts` อย่างถูกต้อง
   - ปรับปรุงการส่งออกระบบ `isGateEnabled` จาก `strategySelector.ts` นำเข้ากลับไปยังไฟล์ `autoTradingService.ts` เพื่อคุ้มครองระบบเกตเทรดทั้ง 21 จุดให้ทำงานได้อย่างมีเสถียรภาพสูงสุด
2. **รักษาความเข้ากันได้ย้อนหลัง (Backward Compatibility):**
   - รักษา Class `AutoTradingService` และอินสแตนซ์ Singleton ไว้ที่เดิม ไร้ผลกระทบต่อเราเตอร์ภายนอกและการเชื่อมต่อกับแอปพลิเคชันมือถือ
   - โค้ดที่ประมวลผลตัวแปรแวดล้อมภายใน เช่น `pushDecisionTrace` ยังถูกทิ้งไว้ในไฟล์หลักอย่างเหมาะสม

---

## 🧪 ผลการยืนยันประสิทธิภาพและความถูกต้อง (Verification Pass)

- **TypeScript Compilation:** รันคำสั่งบิวด์แปลงไฟล์ด้วย `npm run build` ผ่านสำเร็จ 100% ปราศจาก Error ด้าน Typings
- **Unit Testing Pass:** รันกระบวนการทดสอบทั้งหมด `npm test` ผ่านสำเร็จ **103 เคสเต็ม 100%** ทั้งระบบเกต, ตัวตรวจสอบ slippage, และกลยุทธ์ V25
- **Advanced Performance Audit:** ทดสอบเรียกใช้รายงานสถิติ All-time `npm run analyze` สามารถคำนวณและแจกแจงอัตราการ Skip/Veto ของเกตและแสดง Net Profit ยอดรวม **+$80.69** ได้อย่างไร้ที่ติ

---

## 🔗 ลิงก์เชื่อมโยงในระบบ (Related Wiki Pages)

- [[55_UnifiedZoneExecutionQuality_V2625]] — ข้อมูลการรวมเกตแบบบูรณาการ Unified Zone & Execution Quality
- [[54_GateStrategyRuntimeControls_V2624]] — ระบบสวิตช์เปิด-ปิดเกตย่อยแบบ Dynamic
- [[Trading_Intelligence_MOC]] — สารบัญใหญ่ของสมองส่วนนอกระบบเทรด
