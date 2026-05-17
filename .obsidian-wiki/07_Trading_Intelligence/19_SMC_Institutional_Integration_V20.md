# SMC Engine V2 — การรวม Institutional SMC Integration

> **Version:** V21.0  
> **Date:** 2026-05-06  
> **Port จาก:** Pine Script "SMC & Multi-TF Order Blocks Sweeps V8.3"

---

## สถาปัตยกรรมใหม่

SMC Engine V2 ถูก port จาก Pine Script V8.3 (TradingView) มาเป็น TypeScript modules สำหรับ MT5 Server:

```
📁 analyzers/smc/
  ├── types.ts           — Shared types (Candle, FVG, OB, LiquidityZone, etc.)
  ├── utils.ts           — ATR, Swing Detection, Candle helpers
  ├── marketStructure.ts — BOS / CHoCH / SMS / BMS + IDM Filter
  ├── fvgDetection.ts    — FVG zones + ATR filter + mitigation + average lines
  ├── orderBlocks.ts     — OB w/ FVG-confirm + mitigation + invalidation
  ├── liquidityZones.ts  — Equal H/L + Swing + confluence stars (0-5★)
  ├── sweepDetection.ts  — MTF sweep + Attack Force (momentum candles)
  └── index.ts           — buildSmcSnapshot() รวมทุกอย่าง
```

## Features ที่ port มาใหม่

### 1. Market Structure (BOS/CHoCH/SMS/BMS)
- **State-based processing** — ตรวจจับ swing break แบบ real-time
- **IDM Filter** — ต้อง sweep inducement ก่อนถึงจะนับเป็น valid break
- **SMS/BMS** — แยกประเภท continuation (SMS = ครั้งแรก, BMS = ครั้งถัดไป)

### 2. FVG Detection (Enhanced)
- **ATR-filtered** — FVG ต้องใหญ่กว่า ATR × 0.25 ถึงนับ
- **Displacement check** — c1 ต้อง close beyond c2 (true displacement)
- **Mitigation tracking** — ติดตามว่า FVG ถูก fill แล้วหรือยัง
- **Average lines** — ค่าเฉลี่ย FVG levels เหมือน TradingView

### 3. Order Blocks (Strict SMC)
- **FVG Confirmation** — OB ต้องมี FVG ยืนยัน (displacement) ถึงจะ valid
- **Mitigation tracking** — ราคาเคย test OB แล้วหรือยัง
- **Invalidation** — OB ที่ราคาทะลุไปแล้วจะถูกลบอัตโนมัติ
- **Trend filter** — แสดงเฉพาะ OB ที่ align กับ trend

### 4. Liquidity Zones + Confluence Stars
- **Equal Highs/Lows** — หาจุดราคาที่ซ้ำกัน (liquidity pool)
- **Swing H/L** — Pivot highs/lows เป็นจุด liquidity
- **Confluence Stars (0-5★)**:
  - ★ Base score
  - ★★ OB overlap (zone อยู่ใน OB)
  - ★★★ Structure confluence
  - ★★★★ Premium/Discount alignment
  - ★★★★★ Trend alignment

### 5. Multi-TF Sweeps + Attack Force
- **MTF Sweep** — ตรวจ sweep ข้าม timeframe, aggregate เมื่อ ≥2 TFs
- **Attack Force** — momentum candle ที่ body > 2× avg + volume > 1.5× avg

## การเรียกใช้

```typescript
import { getSmcSnapshotV2 } from './analyzers/smc.js';

const snapshot = getSmcSnapshotV2(candles, 'XAUUSD', 'M5');

// ใช้ snapshot.liquidityZones สำหรับ SL/TP
// ใช้ snapshot.structure.lastEvent สำหรับ regime change
// ใช้ snapshot.bullOBs / bearOBs สำหรับ entry zones
```

## Backward Compatibility
- `smcAnalyzer()` — ยังทำงานเหมือนเดิม
- `getSmcStructure()` — ยังทำงานเหมือนเดิม
- `classifyPremiumDiscount()` — ยังทำงานเหมือนเดิม
- **ใหม่:** `getSmcSnapshotV2()` / `buildSmcSnapshot()` — เพิ่มมาไม่กระทบของเดิม

---
**Status:** 🟢 Phase 1 Complete — Build Passed  
**Next:** Phase 2 — Event Bus + Indicator Pipeline
