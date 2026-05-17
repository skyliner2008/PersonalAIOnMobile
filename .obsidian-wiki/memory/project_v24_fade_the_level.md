---
name: V24.0 Fade-the-Level + Sequential Entry + Basket BE
description: รื้อ entry strategy จาก BREAKOUT chasing → FADE THE LEVEL, ใส่ Sequential Entry Gate, แก้ Hedge Guard "BE" ที่ปิดทั้งที่ basket ขาดทุน, แก้ float bug RRR < 1.5
type: project
date: 2026-05-07
tags: [autoengine, smc, hedge, bugfix, V24]
---

# V24.0 — Fade-the-Level + Sequential Entry (2026-05-07)

## ปัญหาที่พบจาก log การรันจริง 16:44-16:53

1. **เปิด BUY ซ้ำๆ ใน PREMIUM zone** — 4 ไม้ติดลบรวม -$38 ภายใน 9 นาที
   - `Strategy=BREAKOUT` + `Zone-Aware Gate: BUY ALLOWED — trend-aligned breakout`
   - ระบบไล่ราคาแทนที่จะรอเด้งกลับ
2. **Hedge Guard ปิดไม้ขาดทุนแล้วเรียก "BE"** ทั้งที่ basket รวมยังลบ
   - `Closed #150699884 | BE | Profit: 0` ขณะ basket = -$38
3. **XBTUSD EA Fallback skipped — RRR 1.50 < 1.5** (float-precision bug)
4. **ไม้แรก-ไม้ที่ 5 ในเวลาไม่ถึง 5 นาที** — ไม่มี sequencing rule

## การเปลี่ยนแปลง

### 1. ProximityGate (`auto/core/ProximityGate.ts`) — ใหม่
- เข้าเทรดเฉพาะเมื่อราคาอยู่ใน 50-100 pip ของ S/R wall
- BUY → ใกล้ supportWall (price ลงไปจาก wall)
- SELL → ใกล้ resistanceWall (price ขึ้นไปถึง wall)
- ★ rating policy:
  - ★★★+ → approve
  - ★★ → ต้องมี LTF (M5/M15) confirm: RSI extreme หรือ bias เริ่มกลับ
  - ★ → reject (ดาวน้อยไป)
- pip unit per symbol: XAUUSD=$0.01, XBTUSD=$1.00, JPY=0.01, FX=0.0001

### 2. Sequential Entry Gate (`autoTradingService.ts`)
- ไม้แรก/symbol → เปิดได้ตามปกติ
- ไม้ที่ 2+ ต้องผ่านอย่างใดอย่างหนึ่ง:
  - `isLastSafe` (ไม้ก่อนหน้า TRAIL/Golden แล้ว) → pyramid ฝั่งกำไร
  - `lastPositionR <= -0.25` (ขาดทุน ≥ 25% ของ SL) → recovery DCA
- ถ้า `detDecision.management === SCALE_IN/HEDGE` → ผ่าน Gate (defense plan)

### 3. Basket BE Guard (`auto/core/TradeManagementService.ts`)
- เดิม: concentration_close ปิดไม้ขาดทุนเป็น "BE" → สะสมขาดทุน
- ใหม่: ตรวจ basket profit ของฝั่งเดียวกันก่อน
  - basket < 0 → SKIP concentration close (รอ recovery / ปล่อยให้ HEDGE ทำหน้าที่)
  - basket ≥ 0 หรือคุ้มขาดทุนของ weakest → ปิดได้

### 4. RRR float-safe compare (`autoTradingService.ts`)
- จุด 1: V21.0 EA Fallback (line ~1237): `rrr + 1e-6 >= minR`
- จุด 2: V23.0 EA-Only (line ~1411): `eaRRR + 1e-6 >= minRRR`
- แก้กรณี 1.4999... ถูก compare กับ 1.5 แล้วล้มเหลว

### 5. Default config (`auto/core/PersistenceService.ts`)
```ts
adaptive: {
  // V24.0
  enableProximityGate: true,
  proximityMaxPip: 100,
  proximityUnconditionalStars: 3,
  proximityConditionalStars: 2,
  enableSequentialEntry: true,
  sequentialAddOnLossPct: 0.25,
  recoveryTriggerPct: 0.25,    // sync ขึ้นจาก 0.2
  requireBasketBeBeforeClose: true,
}
```

### V24.2.1 (2026-05-08) — Proximity Gate Optimization
- **Fix 1★ Wall Masking Bug**: แก้ไขบั๊กใน `ProximityGate.ts` ที่ตัวกรองดึง "Wall ที่ใกล้ที่สุด" มาตรวจเงื่อนไขดาว ทำให้ถ้ามี 1★ Wall บัง 5★ Wall ที่อยู่ด้านหลังแต่ยังอยู่ในระยะ `maxPipDist` ระบบจะดึง 1★ มาเช็คแล้วปฏิเสธการเข้าเทรด (BLOCKED) ปรับปรุงให้หา Wall ที่ "ตรงตามเงื่อนไขดาว" ที่ดีที่สุดในระยะแทน
- **Dynamic Max Pip Distance (ATR-Scaled)**: แก้ไขให้ `maxPipDist` ใน `autoTradingService.ts` ไม่ถูกล็อกตายตัวที่ 100 pips ($1.00 สำหรับ XAUUSD) ซึ่งแคบเกินไปสำหรับคู่เงินที่มีความผันผวนสูง (โดยเฉพาะหลังเพิ่ม Context Levels ที่ทำให้มี 1★ Wall เต็มไปหมด) ปรับให้คำนวณแบบ Dynamic โดยใช้สูตร `Math.max(baseMaxPip, 1.5 * ATR / pipUnit)` เพื่อให้มีระยะการเข้าเทรดสอดคล้องกับความผันผวน (เช่น XAUUSD M15 ATR=$3.00 จะถูกขยาย `maxPipDist` เป็น ~300-400 pips อัตโนมัติ)

## ไฟล์ที่แก้

- `mt5-core-server/src/services/auto/core/ProximityGate.ts` (สร้างใหม่)
- `mt5-core-server/src/services/auto/core/TradeManagementService.ts` (Basket BE)
- `mt5-core-server/src/services/auto/core/PersistenceService.ts` (default config)
- `mt5-core-server/src/services/auto/types.ts` (config types)
- `mt5-core-server/src/services/autoTradingService.ts` (proximity gate + sequential gate + RRR fixes × 2)

## งาน follow-up (ยังไม่ทำใน V24.0)

- **BE @ midpoint volume calc** — ตอนนี้ไม้แก้ใช้ volume จาก risk% ปกติ ผู้ใช้ต้องการ
  คำนวณ volume ให้ avg_entry = midpoint ระหว่าง entry1 กับ price ปัจจุบัน:
  ```
  vol2 = vol1 × (entry1 - midpoint) / (midpoint - currentPrice)
  ```
  ต้อง refactor SCALE_IN ใน TradeManagementService.ts ให้รับ recovery hint จาก
  deterministicEngine แล้ว switch volume formula

- **AI re-enable confirmation** — V23.0 EA-only mode ใช้งานชั่วคราวระหว่าง test
  ผู้ใช้บอกตอนนี้เปิด AI กลับแล้ว แต่ log ยังขึ้น "[EA-ONLY MODE]" — ต้อง verify
  ว่า `enableAiMode=true` ที่ผู้ใช้ตั้ง persist เข้า DB จริง

- **OpenRouter quota** — log แสดง 403 "Key limit exceeded (total limit)" สำหรับ
  google/lyria-3-* — ผู้ใช้น่าจะใกล้ quota OpenRouter แล้ว
