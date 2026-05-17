# 💻 V20.0: SMC Institutional Integration (Phase 8: Structural Precision)

> **Status: 🟢 Stable (Phase 8: Structural Precision)** | **Release: 2026-05-04**

## 🏗️ SMC Institutional Integration [NEW V20.0]
- **Professional SMC Library (`smartmoneyconcepts`)**: ย้ายจากการใช้ Heuristics พื้นฐานมาเป็นการใช้ Library ระดับสากลบน Python เพื่อระบุ FVG, OB, BOS/CHoCH ที่แม่นยำสูง
- **Structural SL/TP Placement**: AI (slTpAgent) ทำการวางจุดตัดขาดทุนและทำกำไรโดยอ้างอิงจากโซน Order Block และ FVG จริงในตลาด ช่วยลดอัตราการโดน Stop Hunt
- **Structural Analyst Injection**: ส่งข้อมูลโครงสร้างราคาจาก Python Bridge เข้าสู่ AnalystAgent เพื่อให้ AI เข้าใจบริบทการไหลของเงิน (Institutional Flow) ก่อนตัดสินใจ


## 🎯 Entry Quality Hardening [NEW V24.0]
- **Zone-Aware Gate**: ระบบคัดกรองจุดเข้าด้วย โซน Premium / Discount ของ H4 (OTE 62-79%) โดยจะ Block การเข้าเทรดผิดโซนทันที (ไม่ Buy ของแพง / ไม่ Sell ของถูก)
- **Dynamic FVG Buffer**: ระบบขยับ SL ให้อยู่นอกขอบเขตของ M15 FVG เสมอเพื่อลดโอกาสโดนกวาด (Fix Error 10016)
- **Counter-Trend Hard Block**: ยกระดับระบบ Counter-Trend เป็น Hard Block ทันทีหากค่า Confluence < 62 ป้องกันการโดนลากจนต้องปิดเสมอทุน

## 🔄 Management Upgrade [NEW V24.0]
- **Staged BE & Trailing System**:
  - Stage 1 (1R): ปิดล็อกกำไร 25% + ขยับ SL มาที่ BE
  - Stage 2 (2R): ปิดล็อกกำไร 50% + ขยับ Trailing SL มาที่ BE + ATR
  - Stage 3 (3R): ปล่อย Trailing SL ตามราคาที่ระยะ ATR × 1.5
- **Smart Scale-In Guard**: ระบบจะเปิดไม้แก้/ถัว (Scale-In) ก็ต่อเมื่อได้ราคาที่ได้เปรียบกว่าค่าเฉลี่ยเดิม + ATR Buffer เท่านั้น
- **Hedged-BE Priority Close**: เมื่อพอร์ตที่ Hedging ไว้กลับมาที่ระยะเสมอทุน ระบบจะบังคับให้ปิดขาที่ขาดทุนก่อนเสมอเพื่อป้องกันความเสี่ยง

## 🧠 Smart Flip & Learning Loop [NEW V24.0]
- **Regime Reversal Detection (FLIP_CLUSTER)**: เมื่อเทรนด์หลัก H4 เกิดการกลับตัวและสวนทางกับ Cluster ที่ถืออยู่ ระบบจะรีบตัดไม้ที่อ่อนแอที่สุดทิ้งเพื่อลดภาระพอร์ตทันที
- **Pre-Analysis Prior (Vector Store)**: ระบบดึงข้อมูล Similar Failed Setups (ความผิดพลาดที่คล้ายคลึงกันในอดีต) จาก Vector DB ไปเตือนสติ LLM ก่อนตัดสินใจเข้าเทรด

## 📊 Observability & Quality Metrics [NEW V24.0]
- เพิ่ม Metrics ข้อมูลเชิงลึกสำหรับ Grafana Dashboard:
  - `mt5_zone_gate_blocks_total`
  - `mt5_sl_buffer_activations_total`
  - `mt5_counter_trend_blocks_total`
  - `mt5_management_events_total`
  - `mt5_entry_zone_distribution_total`

---

## 🧠 สถาปัตยกรรม Deterministic Prior Engine [V23.0]

ยกระดับความเร็วและความแม่นยำด้วยการนำตรรกะแบบ Deterministic มาช่วย AI:
- **Ultra-Fast Technical Pre-filter**: ระบบตรวจสอบเงื่อนไขทางเทคนิค (FVG, EMA) ด้วยความเร็ว 5ms ก่อนส่งให้ AI ช่วยลด Token และบังคับให้ AI โฟกัสเฉพาะหน้าเทรดที่ได้เปรียบจริงๆ
- **Deterministic Prior**: ส่งผลการวิเคราะห์ทางเทคนิคเบื้องต้นไปเป็น "คำแนะนำ" ให้ AI Agent ทำให้ AI ไม่ต้องคำนวณราคาเอง ลดอาการ Hallucination
- **Smart Logic Buffering**: ระบบคำนวณระยะราคาที่ปลอดภัย (เช่น 0.5 pts buffer สำหรับ Gold) เพื่อครอบคลุมค่าคอมมิชชั่นและสเปรด

## 🛡️ ระบบ Parallel Risk Management [V23.0]

แยกส่วนการจัดการความเสี่ยงออกจากวงรอบการคิดของ AI เพื่อความเร็วสูงสุด:
- **Trade Manager Parallelization**: ระบบ `TradeManagementService` (SL trailing, Break-Even, Hedging) ถูกแยกการทำงานแบบขนาน (Parallel) ทำให้สามารถปกป้องพอร์ตได้ทันทีโดยไม่ต้องรอ AI วิเคราะห์ตลาด
- **Dynamic Break-Even (BE)**: ปรับปรุงการเลื่อน SL กันหน้าทุนแบบฉลาด:
    - **Scalp Strategy**: เลื่อนกันทุนที่ **0.5R**
    - **Trend Strategy**: เลื่อนกันทุนที่ **0.8R**
- **Management Journal Sync**: [CRITICAL FIX] แก้ไขปัญหา `NO_JOURNAL_MATCH` สำหรับไม้ Hedge และ Scale-In ทำให้ไม้ที่เปิดเพื่อแก้พอร์ตถูกบันทึกและติดตามโดยระบบ BE/Trail ได้ 100%
- **Backfill Guard**: เพิ่มระบบหน่วงเวลา 10 วินาที (10s Deferral Guard) เพื่อรอให้ข้อมูลจากโบรกเกอร์ Sync กับระบบ Journal ป้องกันปัญหาข้อมูลขัดแย้งกัน

## Patch Notes

### [2026-04-25] V24.0: Smart Upgrade
- **Feature**: **Zone-Aware Gate** บล็อกการยิงไม้ BUY ใน Premium / SELL ใน Discount
- **Feature**: **Dynamic SL Buffer** ดัน SL ออกนอก FVG + เช็ค min distance
- **Feature**: **Staged BE/Trail** จัดการไม้กำไรแบบขั้นบันได (1R: 25%, 2R: 50%, 3R: Trail ATRx1.5)
- **Feature**: **FLIP_CLUSTER** ปิดไม้ขาดทุนสุดเมื่อ H4 regime สวนทาง
- **Feature**: **Pre-Analysis Prior** ดึงข้อมูลไม้ที่เคยเสียแบบเดียวกันจาก Vector Store มาเตือน AI
- **Feature**: **Prometheus Metrics** เพิ่มการนับจำนวน Block และการจัดการพอร์ตอย่างละเอียด

### [2026-04-24] V23.0: Risk Management Overhaul
- **Feature**: **Trade Manager Parallelization** แยก Thread การทำงานของระบบจัดการไม้ (BE/Trail) ออกจาก AI Cycle
- **Feature**: **Deterministic Prior Engine** ระบบ Pre-filter ข้อมูลทางเทคนิคด้วยความเร็ว 5ms ก่อนส่ง AI
- **Feature**: **Dynamic BE Trigger** ระบบเลื่อน SL กันหน้าทุนแยกตามกลยุทธ์ (0.5R สำหรับ Scalp, 0.8R สำหรับ Trend)
- **Feature**: **XAUUSD Spread Buffer** เพิ่มระยะ 0.5 จุด ในการตั้ง BE เพื่อครอบคลุมค่าธรรมเนียมโบรกเกอร์
- **Fix**: **Management Journal Sync** แก้ไขปัญหาไม้ Hedge ไม่เขียน Journal (ทำให้ระบบมองไม่เห็นและไม่ทำ BE ให้)
- **Fix**: **Backfill Race Condition Guard** ระบบรอข้อมูลโบรกเกอร์ 10 วินาทีก่อนเริ่มจัดการไม้ใหม่
- **Fix**: **Anti-Hedge Gate Enforcement** ป้องกันการเปิดไม้สวนทิศทางเดิมโดยไม่ตั้งใจ (ยกเว้นโหมด Defense)

### [2026-04-23] V22.2: Adaptive Spread Guard & Precision SL
- **Feature**: **Adaptive Spread Guard** ระบบเช็ค Spread โบรกเกอร์ Real-time และปรับระยะ SL ขั้นต่ำอัตโนมัติ
- **Feature**: **Hard RRR Gate** บังคับ RRR ขั้นต่ำ >= 1.0 และระยะ TP ขั้นต่ำ 500 จุด
- **Fix**: **MT5 Comment Length** ตัดความยาวคำอธิบายออเดอร์ให้เหลือ 26-31 ตัวอักษรตามมาตรฐาน MT5

---
**Links**: [[Trading_Intelligence_MOC]] | [[11_MT5_Full_Agent_Control_V17.0]]


### [2026-04-25] V23: Smart Upgrade — Phase 1-4 (100%)

อ้างอิง [[../09_Roadmap/02_AutoTrading_Smart_Upgrade_Plan_2026_04_25]] และ [[../09_Roadmap/03_AutoTrading_Implementation_Audit_2026_04_25]]

**Phase 1 — Entry Quality Hardening**
- **P1.1** `classifyPremiumDiscount(candles, price)` ใน `analyzers/smc.ts` — OTE 62/38% zone classifier
- **P1.2** Zone-Aware Gate (dual-layer): pre-AI confidence penalty (-15) ใน `deterministicEngine.ts` + post-AI HARD BLOCK ใน `autoTradingService.ts`. ครอบทุกกรณี `BUY in PREMIUM` / `SELL in DISCOUNT` (ไม่ผูก H4 bias อีกต่อไป)
- **P1.3** FVG Entry Alignment สำหรับ `SMC_FVG_SCALP` — entry ต้องอยู่ใน/ใกล้ FVG ทิศทางเดียวกัน
- **P1.4** Dynamic SL Buffer — `max(ATR×0.25, spread×3, 50 ticks, brokerStopsLevel)` + push SL ออกจาก FVG zone (5-tick buffer)
- **P1.5** Counter-trend HARD BLOCK เมื่อ confluence < `counterTrendConfluenceMin` (default 62)

**Phase 2 — Management Upgrade**
- **P2.1** Staged BE/Trail (1R partial 25 % + BE, 2R partial 50 %, 3R trail ATR×1.5) — เก็บ marker ใน `aiReview` เพื่อกัน trigger ซ้ำ
- **P2.2** Smart Scale-In — ราคาต้อง *ดีกว่า* weighted avg − ATR×0.25 จึงจะถัวได้
- **P2.3** Close-Weakest-Loser — เมื่อ gate ปฏิเสธด้วย `max open positions` และ confluence ≥ `closeWeakestMinConfluence` (default 70) → ปิดไม้ profit ต่ำสุดของ symbol นั้น (cooldown `closeWeakestCooldownMs`, default 1 ชม.)
- **P2.4** Hedge Override Gate — heat ≥ 1.0R + AI confidence < `hedgeOverrideMinConfidence` (default 75) จึงจะ suppress; AI conf ≥ 75 = อนุญาต hedge แม้ heat สูง
- **P2.5** Hedged-BE Close — return ครั้งละหนึ่ง action ต่อ cycle ด้วย early return

**Phase 3 — Smart Flip & Learning Loop**
- **P3.1** FLIP_CLUSTER แบบเต็ม — ปิด worst loser + เปิดไม้สวนทิศที่ `flipOppositeFraction` (default 0.5) ของ cluster เดิม. orchestrator ยิง opposite leg ผ่าน `triggerOpposite` field ของ `ManagementPlan`
- **P3.2** Cluster Concentration Guard — > 3 ไม้ฝั่งเดียวกันภายใน 10 pips → ปิดไม้อ่อนสุด
- **P3.3** Pre-Analysis Prior — query top-5 trades คล้ายปัจจุบันจาก vectorStore. avgR < −0.3 → หัก 10 คะแนน confluence + inject context
- **P3.4** Failure Pattern Tagging — `detectFailurePattern()` ใน `agents/postMortem.ts` ติด tag เช่น `BUY_IN_PREMIUM`, `COUNTERTREND_BUY`, `SCALE_UP_LOSER`, `SL_INSIDE_FVG`, `BE_STOP_ERASED_GAIN`. Tag เก็บใน vector metadata + log
- **P3.5** Analyst Prompt — failedSetupContext + patternsLine inject เข้า history context ก่อน LLM ตัดสิน

**Phase 4 — Observability & KPI**
- **P4.1** Prometheus Counters: `mt5_zone_gate_blocks_total`, `mt5_sl_buffer_activations_total`, `mt5_counter_trend_blocks_total`, `mt5_management_events_total`, `mt5_entry_zone_distribution_total`
- **P4.2** UI: `CycleQualityCard` ใน `AutoTradingScreen.kt` แสดง zone blocks / SL buffered / CT blocks / mgmt events / trades placed/rejected + distribution mini-bars
- **P4.3** Endpoint `GET /api/mt5/auto/metrics/quality` คืน JSON ที่จัดกลุ่มแล้ว (summary + breakdown + raw)

**Feature flags ใน `cfg.adaptive`** (ทุกตัวเปิดเป็น default true):
`enableZoneAwareGate`, `enableFvgSlBuffer`, `enableStagedPartials`, `enableSmartScaleIn`, `enableCloseWeakest`, `closeWeakestMinConfluence`, `closeWeakestCooldownMs`, `hedgeOverrideMinConfidence`, `enableFlipCluster`, `flipClusterCooldownMs`, `flipOppositeFraction`, `maxClusterConcentration`, `enablePreAnalysisPrior`

**Verification**: `tsc --noEmit` exit 0 (no type errors)
