# แกะสูตรและเงื่อนไขทั้งหมดจาก Code ปัจจุบัน (V19.6)

นี่คือการถอดสมการและเงื่อนไขทั้งหมดจากไฟล์โค้ดของระบบ (`deterministicEngine.ts`, `autoTradingService.ts`, `risk.ts`, `TradingExecutionService.ts`) ออกมาแบบละเอียดยิบ

---

## 1. เงื่อนไขการเข้าออเดอร์ (Order Entry Logic)

### 1.1 การคำนวณคะแนนเข้าเทรด (MTF Confluence Score)
ระบบคำนวณคะแนนฝั่ง BUY และ SELL แยกกัน โดยให้น้ำหนัก Timeframe ต่างกัน:
- **Weights:** H4 (35%), H1 (35%), M15 (20%), M5 (10%)
- **สมการ:** นำ `Confluence` ของแต่ละ TF คูณด้วย น้ำหนัก 
  - หากสัญญาณเป็น `BULL` คะแนนเข้าฝั่ง `buyScore`
  - หากสัญญาณเป็น `BEAR` คะแนนเข้าฝั่ง `sellScore`
  - หากเป็น `NEUTRAL` คะแนนหารครึ่งให้ทั้งคู่
- **ตัดสินใจ:** หา `scoreDelta = buyScore - sellScore`
  - หาก `scoreDelta > 8` ➡️ Action = **BUY**
  - หาก `scoreDelta < -8` ➡️ Action = **SELL**
  - นอกนั้น ➡️ **SKIP**
- **Confidence:** คำนวณจาก `Math.max(buyScore, sellScore)` จำกัดสูงสุดที่ 95%

### 1.2 SMC & Zone-Aware Gate (การกรองโซนราคา)
- **Zone Penalty:** หากเล่นฝั่ง BUY ในโซน PREMIUM (ของ H4) หรือ SELL ในโซน DISCOUNT จะถูกตัดความมั่นใจ (Confidence) ทิ้ง `-15` แต้ม (ลดต่ำสุดได้ถึง 40)
- **FVG-Align Gate (สำหรับกลยุทธ์ SMC_FVG_SCALP):** 
  - ราคาต้องอยู่ **"ภายใน"** หรือ **"แตะ"** ขอบของ Active FVG ,OB ,LQ ในทิศทางที่จะเล่น (บวกลบระยะเผื่อ 0.5%)
  - หากราคาไม่ได้อยู่ใน FVG ที่ถูกต้อง ➡️ **โดน Block (SKIP) ทันที**

### 1.3 การจัดการไม้แก้ (Sequential Protection)
- **ทริกเกอร์:** AI จะพิจารณาแก้ไม้ (Scale-in) เมื่อสัญญาณตรงกับทิศทางพอร์ตเดิม และ ความมั่นใจ `>= 65%`
- **เงื่อนไข:**
  - `Safe Mode`: ไม้ล่าสุดของฝั่งนั้น กำไรแล้ว (`R >= 0`)
  - `Recovery Mode`: ไม้ล่าสุดของฝั่งนั้น ติดลบหนักเกินเกณฑ์ `recoveryThresholdR` (ค่าตั้งต้นคือ `-0.2R` หรือ -20% ของระยะ SL)
- 🔒 **Hard Enforcement (Risk Gate):** ก่อนส่งออเดอร์ให้โบรกเกอร์ ระบบจะดึง "ไม้ล่าสุด" มาเช็ค หาก R-Value ไม่เข้าเกณฑ์ 2 ข้อด้านบน ระบบจะ **VETO ปฏิเสธการเข้าออเดอร์ทันที** (กัน AI แอบเปิดไม้ 4, 5 ก่อนเวลา)

---

## 2. สูตรการคำนวณขนาดไม้ (Volume / Lot Sizing)

การคำนวณ Lot Size จะถูกประมวลผลผ่าน `TradingExecutionService.ts` โดยมีสมการดังนี้:

### 2.1 การคำนวณความเสี่ยงด้วย Kelly Criterion
- **ตั้งต้น:** เริ่มจาก `riskPerTradePct` (เช่น 2% ของพอร์ต)
- **Kelly Sizer:** หากมีข้อมูลสถิติของกลยุทธ์นั้นๆ ในอดีต (Win Rate, Avg R) ระบบจะใช้สมการ Kelly เพื่อ **"ปรับลดหรือเพิ่ม"** % ความเสี่ยงให้เหมาะสมกับความแม่นยำของกลยุทธ์นั้นโดยอัตโนมัติ

### 2.2 สมการคำนวณ Lot Size พื้นฐาน (Base Volume)
- **Risk Cash (เงินที่ยอมเสียได้):** `Equity * (Risk Pct / 100)`
- **Unit Value:** มูลค่าต่อการขยับ 1 จุด (เช่น 1 Lot ทองขยับ 1 จุด = $1)
- **สมการ:** `Base Volume = Risk Cash / (ระยะ SL * Unit Value)`
- **ปัดเศษ (Clamping):** ตัวเลขที่ได้จะถูกปัดเศษตาม `minLotStep` (เช่น 0.01) และจะถูกบังคับให้อยู่ในกรอบไม่ต่ำกว่า `minLot` และไม่เกิน `maxLot`

### 2.3 Session Volume Throttle (การคุมกำเนิดตามช่วงเวลาตลาด)
ก่อนส่งออเดอร์ ระบบจะเช็คช่วงเวลาตลาด (Session Analyzer):
- ช่วง **QUIET** (ตลาดปิด/ไร้วอลลุ่ม): `Multiplier = 0` (งดออกออเดอร์เด็ดขาด)
- ช่วง **CHOPPY**: ลด Lot ลงตาม Multiplier
- ช่วง **ACTIVE / TRENDING**: ปล่อย Lot เต็ม `1.0`
- **Final Volume:** `Base Volume * Multiplier` (แล้วปัดเศษอีกครั้ง)

---

## 3. เงื่อนไขการตั้ง SL / TP (Risk Management)

### 3.1 สมการคำนวณพื้นฐาน (ATR Fallback)
หากไม่มี AI หรือ AI ให้ค่าเพี้ยน ระบบจะคำนวณเองด้วยสูตร:
- **ระยะ SL (rawRiskDistance):** `ATR * riskMultiplier` 
  - *(หากหา ATR ไม่ได้ ใช้ `ราคา * 0.002`)*
  - `riskMultiplier`: `BREAKOUT` = 1.2, `TREND_FOLLOW` = 1.5, `MEAN_REVERSION` = 1.0, กลยุทธ์อื่น = 1.3
- **ระยะ TP:** คำนวณจาก `SL * targetRrr`
  - `targetRrr`: `SCALPING / SMC` = `Max(1.2, scalpingRRR)`
  - `targetRrr`: `TREND_FOLLOW` = `Max(minRRR, swingRRR)`

### 3.2 การตรวจสอบค่าจาก AI (Sanity Check)
ระบบจะดึง SL/TP ที่ AI วิเคราะห์มาตรวจสอบ ถ้าเจอข้อใดข้อหนึ่ง จะปัดตกและกลับไปใช้สูตรพื้นฐานทันที:
- ให้ค่ามาผิดฝั่ง (เช่น เล่น BUY แต่ให้ TP ต่ำกว่าราคาเข้า)
- ระยะแคบเกินไป (TP น้อยกว่า 3.0-5.0 จุด, SL น้อยกว่า 1.5 จุด)
- เป็นค่า % หรือระยะทาง (เช่น ตอบ "0.17" แทนที่จะเป็นราคา "4617") โดยเช็คว่าค่านั้นน้อยกว่า `0.1%` หรือมากกว่า `200%` ของราคาเข้าหรือไม่

### 3.3 Dynamic SL Buffer (การหลบ FVG)
หลังจากได้จุด SL ระบบจะทำการปรับแก้รอบสุดท้าย:
- **Minimum Distance:** SL ต้องห่างจากราคาเข้าอย่างน้อยสุดคือค่าที่มากที่สุดของ `(ATR * 0.25)`, `(Spread * 3)`, `50 Ticks` หรือ `StopsLevel ของโบรกเกอร์`
- **FVG Avoidance:** ถ้าระยะ SL ดันไปตก **"อยู่ตรงกลาง"** ของโซน FVG พอดี ระบบจะดัน SL ออกไปอยู่ **"นอกโซน FVG 5 Ticks"** เพื่อป้องกันการโดนลากไปกิน SL (Stop Hunt) ในโซน

---

## 4. เงื่อนไข Break-Even และ Trailing Stop

ทุกๆ รอบการทำงาน ระบบจะดึงออเดอร์มาเช็คค่า R-Value:
`R = กำไรของออเดอร์ / ความเสี่ยง (Entry - SL เริ่มต้น)`

### 4.1 Individual Break-Even (กันทุนรายไม้)
- **ทริกเกอร์:** 
  - `R >= 0.5` สำหรับกลยุทธ์ `SCALPING`, `MEAN_REVERSION`
  - `R >= 0.8` (หรือตามตั้งค่า `breakEvenTriggerR`) สำหรับกลยุทธ์อื่นๆ
- **การทำงาน:** เลื่อน SL ไปที่ `ราคาเข้า (Entry) + Buffer` (Buffer = 0.5 จุดสำหรับ XAUUSD, คู่เงินอื่น = 0)

### 4.2 Individual Trailing Stop (ล็อคกำไรรายไม้)
- **ทริกเกอร์:** `R >= trailAfterR` (ค่าตั้งต้นมักจะ 1.0 หรือ 1.5R ขึ้นไป)
- **ระยะตาม (Trail Distance):** `Max( ราคาเข้า * 0.001, ครึ่งหนึ่งของระยะเป้าหมาย TP )`
- **การทำงาน:** เลื่อน SL ตามราคาปัจจุบันโดยเว้นระยะ `Trail Distance` (จะทำงานก็ต่อเมื่อ SL ใหม่ ขยับเข้าข้างเรามากกว่า `ราคาเข้า * 0.00005` เท่านั้น เพื่อไม่ให้ยิงคำสั่งรัวเกินไป)

### 4.3 Unified Cluster Break-Even (ปกป้องกำไรกลุ่ม/รวบไม้แก้)
ทำงานเฉพาะออเดอร์ที่ยังไม่ถึงระยะ Trailing โดยแยกกลุ่มตามคู่เงินและฝั่ง (เช่น `XAUUSD_BUY`):
- **สมการ:** 
  - `clusterR = กำไรรวม / ความเสี่ยงรวม`
  - `avgEntry = ราคาต้นทุนเฉลี่ยถ่วงน้ำหนักตาม Lot`
- **Activation (เริ่มจับตา):** เมื่อกำไรรวมของกลุ่ม `>= $20` (`minDollar`) **หรือ** `clusterR >= 0.75` ระบบจะเริ่มจดจำยอดกำไรสูงสุด (Peak)
- **Trigger (รวบตึง):** เมื่อกำไรรวมร่วงลงมาเหลือ `Peak * 50%` (`dropPct`)
- **การทำงาน:** ตั้ง SL ของ **ทุกไม้ในกลุ่ม** (ทั้งไม้หลักที่ติดลบ และไม้แก้ที่กำไร) ไปที่จุดเดียวกันคือ **`avgEntry + Buffer`** ทำให้ทุกไม้ถูกปิดพร้อมกันและเจ๊ากันพอดี

---

## 5. กฎกรองระดับบัญชี (Account Level Risk Gates)
ด่านสุดท้ายก่อนที่คำสั่งจะถูกส่งออกไป (`risk.ts`):
- **News Gate:** บล็อคออเดอร์หากอยู่ในช่วงเวลาข่าว High/Medium Impact (ยกเว้นเป็นไม้แก้ Defense Override)
- **Drawdown Limit:** บล็อคหาก Drawdown รวมของพอร์ต `>= maxDrawdownPct`
- **Daily Loss Limit:** บล็อคหาก Daily Loss `>= maxDailyLossPct`
- **Free Margin Gate:** บล็อคหาก Free Margin ต่ำกว่า `minFreeMarginPct`
- **Circuit Breaker:** หาก Margin ทะลุเกณฑ์วิกฤต ระบบจะเข้าแทรกแซงขั้นสูงสุด (สั่ง Hedge ทันที)

---

## 6. Audit Results — 2026-05-01 (Code vs Spec Review)

ผลตรวจสอบ code จริง 4 ไฟล์หลัก: `risk.ts`, `deterministicEngine.ts`, `TradingExecutionService.ts`, `autoTradingService.ts`

### ✅ ส่วนที่ถูกต้องตาม Spec ทั้งหมด

| หัวข้อ | ไฟล์ | สถานะ |
|--------|------|-------|
| MTF Weights H4(35%)/H1(35%)/M15(20%)/M5(10%) | deterministicEngine.ts | ✅ |
| scoreDelta >8 → BUY, <-8 → SELL | deterministicEngine.ts | ✅ |
| Confidence = max(buyScore,sellScore) capped 95% | deterministicEngine.ts | ✅ |
| Zone Penalty -15 (min 40): BUY in PREMIUM / SELL in DISCOUNT | deterministicEngine.ts | ✅ |
| FVG-Align Gate: SMC_FVG_SCALP ต้องอยู่ใน Active FVG → SKIP | autoTradingService.ts ~L1270 | ✅ |
| Sequential Protection: Safe (R≥0) / Recovery (R≤-0.2) | risk.ts | ✅ |
| Kelly Half-Kelly (min 10 trades, clamp 0.1%-2.5%) | risk/kelly.ts | ✅ |
| Base Volume = RiskCash / (SL_dist × UnitValue) | TradingExecutionService.ts | ✅ |
| Session Throttle: QUIET=0, CHOPPY=reduced, ACTIVE=1.0 | TradingExecutionService.ts | ✅ |
| ATR Multipliers: BREAKOUT=1.2, TREND_FOLLOW=1.5, MR=1.0, other=1.3 | autoTradingService.ts ~L1289 | ✅ |
| AI SL/TP Sanity Check (wrong side, too narrow, is-distance) | autoTradingService.ts ~L1316 | ✅ |
| Dynamic SL Buffer: max(ATR×0.25, Spread×3, 50 ticks, StopsLevel) | autoTradingService.ts ~L1372 | ✅ |
| FVG Avoidance: ดัน SL ออกนอก FVG 5 ticks | autoTradingService.ts ~L1383 | ✅ |
| BE Trigger: R≥0.5 (SCALP), R≥0.8 (others) | autoTradingService.ts ~L2360 | ✅ |
| BE Buffer: 0.5 pts สำหรับ XAUUSD, 0 สำหรับ symbol อื่น | autoTradingService.ts ~L2309 | ✅ |
| Staged Partials: 1R→25%, 2R→50%, 3R→trail, Golden→tight trail | TradeManagementService.ts | ✅ |
| Cluster Profit Protection: activationR=0.75 / $20, dropPct=50% | autoTradingService.ts ~L2289 | ✅ |
| Risk Gates: News / Drawdown / Daily Loss / Free Margin / Circuit Breaker | risk.ts | ✅ |

### 🔴 Bugs พบและแก้แล้ว (2026-05-01)

#### Bug #1 — CRITICAL: `symbolUpper` TDZ crash ใน `risk.ts`
- **ปัญหา:** `symbolUpper` ถูกใช้ที่ line 53 (Sequential Protection block) แต่ `const symbolUpper = symbol.toUpperCase()` อยู่ที่ line 102 → JavaScript TDZ → `ReferenceError` ทุกครั้งที่ `gateTrade()` ถูกเรียกพร้อม `options.side`
- **ผล:** Sequential Protection Hard Gate ไม่เคยทำงานจริงในทางปฏิบัติ
- **การแก้:** ย้าย `const symbolUpper` ขึ้นมาเป็น statement แรกในฟังก์ชัน
- **ไฟล์:** `risk.ts` line 26

#### Bug #2: BE Buffer hardcode 0.5 สำหรับทุก symbol ใน `TradingExecutionService.ts`
- **ปัญหา:** `const buffer = 0.5` ใน BREAKEVEN handler ไม่ตรวจ symbol → ทำให้ Forex pairs เช่น EURUSD โดน BE ที่ entry + 0.5 (= เลื่อน SL ไป 50 pips!)
- **Spec กำหนด:** 0.5 เฉพาะ XAUUSD/GOLD, symbol อื่น = 0
- **การแก้:** `const buffer = symbol.toUpperCase().includes('XAU') || symbol.toUpperCase().includes('GOLD') ? 0.5 : 0`
- **ไฟล์:** `TradingExecutionService.ts` BREAKEVEN handler

### 🟡 Improvement — Circuit Breaker Priority
- **เดิม:** Circuit Breaker check อยู่หลัง News Gate
- **แก้:** ย้าย Circuit Breaker ขึ้นมาเป็น gate แรกสุด (Priority 0) ก่อน Anti-Hedge และ Sequential Protection
- **เหตุผล:** Margin crisis ต้องหยุดก่อนทุกอย่าง — ไม่ควรให้ order ผ่าน News Gate ก่อนแล้วค่อย trip Circuit Breaker

### Gate Priority Order หลังแก้ (`risk.ts`)
```
1. Circuit Breaker      [PRIORITY 0 — Emergency]
2. Anti-Hedge Rule      [ป้องกันเปิดสองฝั่งโดยไม่ตั้งใจ]
3. Sequential Protection [Hard VETO ไม้แก้]
4. News Gate            [บล็อคช่วงข่าว]
5. Drawdown Limit       [maxDrawdownPct]
6. Daily Loss Limit     [maxDailyLossPct]
7. Free Margin Gate     [minFreeMarginPct]
8. Per-Symbol Cap       [maxPositionsPerSymbol]
9. Correlation Guard    [dynamic correlation]
10. Fitness Gate        [minFitness]
11. Spread Guard        [maxSpreadIncreasePct]
12. StopDistance/Volume [validity]
13. Total Exposure Cap  [maxTotalExposurePct]
```

---
**Last Updated:** 2026-05-01
**Version:** V19.6
**Status:** 🟢 Complete Logical Summary + Audit Passed (5 fixes applied)

---

## 7. Token Usage Optimization & Multi-Provider Expansion (V20.5)

อัพเดทล่าสุด (2026-05-04) เพื่อเพิ่มความเสถียรด้านต้นทุนและความยืดหยุ่นของระบบ:

### 7.1 Token Pruning Gate (การคุมกำเนิดข้อมูล)
ระบบทำการ "หั่น" ข้อมูลก่อนส่งให้ LLM ใน `JarvisOrchestrator.kt` (Mobile) และ `agentOrchestrator.ts` (Server):
- **History Pruning:** ตัดประวัติการสนทนาเหลือเพียง 20 turns ล่าสุด
- **Core Memory Capping:** จำกัดความจำระยะยาว (Core Context) ที่ 15,000 ตัวอักษร
- **Tool Result Truncation:** ตัดผลลัพธ์จาก Tool ที่ยาวเกิน 8,000 ตัวอักษร เพื่อป้องกันค่าใช้จ่ายบานปลาย
- **Anthropic Beta Header:** เปิดใช้ `prompt-caching-2024-07-31` สำหรับ Claude เพื่อลดราคา Input Tokens ซ้ำซ้อนสูงสุด 90%

### 7.2 MiniMax Provider Integration
เพิ่ม Provider **MiniMax** (`abab6.5s`) เข้าสู่หัวใจของระบบ:
- **Registry Integration:** ลงทะเบียนผ่าน `LlmProviderRegistry.kt` (Mobile) และ `registry.ts` (Server)
- **Settings Persistence:** รองรับ `minimax_api_key` ใน SQLite และ `.env`
- **Native Implementation:** `MinimaxLlmProvider.kt` พัฒนาบนพื้นฐาน KMP (Kotlin Multiplatform) รองรับ Streaming และ Function Calling 100%

---

## 8. Log Analysis Fixes (V20.6 — 2026-05-05)

ผลจากการวิเคราะห์ `log.txt` 6,559 บรรทัด (174 cycles, 3 ชม.) พบและแก้ไขปัญหา 3 รายการ:

### 🔴 Fix #1 — Trailing Stop ไม่เคยทำงาน (trailAfterR 1.5R → 1.0R)
- **ปัญหา:** ทุก position แสดง `TRAIL<1.5` ตลอด log — ไม่มี trade ใดถูก trail
- **ผลกระทบ:** XAUUSD SELL cluster มี unrealized profit ~$700+ (5 positions × $140) แต่ไม่ถูก lock → ราคา bounce → ปิด BE $0 ทั้งหมด
- **สาเหตุ:** threshold 1.5R สูงเกินไปสำหรับ SCALP/MEAN_REVERSION ที่ดีที่สุดแค่ ~1.3R
- **การแก้:** `trailAfterR: 1.0` ใน `PersistenceService.ts`
- **สมการใหม่:** Trail จะ activate เมื่อ R ≥ 1.0 (กำไร ≥ 100% ของ SL distance)

### 🔴 Fix #2 — Post-Mortem Label ผิด ("WIN" สำหรับ BE trades)
- **ปัญหา:** AI Post-Mortem ติด label "WIN" ให้ trades ที่ปิดที่ BE ($0 profit) ถึง 5+ ครั้ง
- **ผลกระทบ:** สถิติเรียนรู้ถูกบิดเบือน → strategy stats มี win rate สูงเกินจริง
- **สาเหตุ:** PostMortemAgent ส่ง raw closedTrade data ให้ LLM เดา outcome → hallucinate ว่า BE เป็น WIN
- **การแก้:** Inject `ACTUAL_OUTCOME` (pre-computed by server) ลงใน prompt พร้อมกำชับ "DO NOT OVERRIDE"
- **ไฟล์:** `postMortem.ts`

### 🔴 Fix #3 — RSI Overbought/Oversold Gate (ใหม่)
- **ปัญหา:** XBTUSD BUY เปิดซ้ำ 5+ ครั้งใน PREMIUM zone ขณะ RSI > 70 (overbought) → ทุก trade ปิด BE $0 → CLP triggered 5 ครั้ง (สูญเสีย ~$226)
- **การแก้:** เพิ่ม deterministic RSI gate ใน `risk.ts`:
  - BUY blocked เมื่อ RSI > 70
  - SELL blocked เมื่อ RSI < 30
- **ยกเว้น:** Defense Override (hedge/scale-in) ยังผ่านได้

### Gate Priority Order (อัพเดท V20.6)
```
1.  Circuit Breaker      [PRIORITY 0 — Emergency]
2.  Anti-Hedge Rule      [ป้องกันเปิดสองฝั่งโดยไม่ตั้งใจ]
3.  Sequential Protection [Hard VETO ไม้แก้]
4.  News Gate            [บล็อคช่วงข่าว]
5.  RSI Overbought/Oversold Gate [ใหม่ — BUY>70 / SELL<30]
6.  Drawdown Limit       [maxDrawdownPct]
7.  Daily Loss Limit     [maxDailyLossPct]
8.  Free Margin Gate     [minFreeMarginPct]
9.  Per-Symbol Cap       [maxPositionsPerSymbol]
10. Correlation Guard    [dynamic correlation]
11. Fitness Gate         [minFitness]
12. Spread Guard         [maxSpreadIncreasePct]
13. StopDistance/Volume  [validity]
14. Total Exposure Cap   [maxTotalExposurePct]
```

---
**Last Updated:** 2026-05-05
**Version:** V20.6
**Status:** 🟢 Complete Logical Summary + Log Analysis Fixes Applied (3 critical fixes)

