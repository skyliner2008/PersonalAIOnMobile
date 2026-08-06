---
title: V26.15 SlippageGuard / Analytics Improvement
category: Trading_Intelligence
tags: [trading, risk-management, analytics, slippage, strategy]
sources: [autoTradingService.ts, analysis.ts, PersistenceService.ts, advanced_analytics.mjs]
created: 2026-05-19
updated: 2026-05-19
---

# V26.15 — Slippage Guard / Analytics Improvement

> **วันที่:** 19 พฤษภาคม 2026  
> **ปัญหาต้นเหตุ:** Loss $-39.05 วันนี้เกิดจากราคา drift จาก decision price → SL กว้าง TP แคบ

---

## 🛡️ 1. Entry Drift / Slippage Guard (ใหม่)

### ปัญหา
AI ตัดสินใจ entry ที่ราคา X แต่กว่าจะส่ง order จริง ราคาขยับไป Y → SL กว้างขึ้น, TP แคบลง → RRR เสื่อม → Loss ใหญ่กว่าที่ควรเป็น

### แก้ไข
เพิ่ม guard ก่อน HARD RRR GATE ตรวจสอบ:
1. ขนาด drift = `|entry_fresh - entry_decision|`
2. ถ้า drift > 15% ของ ATR (≈$4-5 สำหรับ XAUUSD) **และ** RRR หลัง drift < minimum → **BLOCK**
3. Minimum RRR: Scalp-like = 1.0, อื่นๆ = 1.2

### ตำแหน่งในโค้ด
- [[autoTradingService.ts]] → `// V26.15 ENTRY DRIFT / SLIPPAGE GUARD`
- Block category: `ENTRY_DRIFT`

---

## 🔧 2. แก้ UNKNOWN block_category (1,134 records)

### ปัญหา
Analytics รายงาน UNKNOWN 16% (1,134 records) ไม่มี block_category

### สาเหตุ
ทั้ง 1,134 records มี `risk_gate = "market_closed"` แต่ `classifyDecisionBlockCategory()` ไม่มี mapping สำหรับค่านี้

### แก้ไข
- เพิ่ม `MARKET_CLOSED` ใน `classifyDecisionBlockCategory()`
- เพิ่ม `RSI_EXTREME` สำหรับ RSI overbought/oversold gate
- เพิ่ม `ENTRY_DRIFT` สำหรับ slippage guard ใหม่
- เพิ่ม mapping ใน `advanced_analytics.mjs` ทั้ง CATEGORY_LABELS และ SQL CASE expression

### ผลลัพธ์หลังแก้
| ก่อนแก้ | หลังแก้ |
|---------|---------|
| UNKNOWN: 1,134 (16.0%) | Market Closed / Off-Hours: 1,134 (16.0%) ✅ |
| Other Skip: 430 (6.1%) | RSI Overbought/Oversold Gate: 423 (6.0%) ✅ |

---

## 📉 3. ปรับ MEAN_REVERSION Strategy

### ปัญหา
- 16 เทรด, WR 62.5%, **แต่ขาดทุนสุทธิ $-34.25**
- Loss size ใหญ่กว่า Win size → TP target ไกลเกินไป

### แก้ไข
ลด `meanReversionMaxRrr` จาก **1.8** → **1.5**
- บังคับให้ harvest กำไรเร็วขึ้น (snap-back ใกล้ๆ)
- ลดความเสี่ยงที่จะถือนานแล้วกลับมาโดน SL

### ตำแหน่ง
- [[PersistenceService.ts]] → `defaultConfig.adaptive.meanReversionMaxRrr`

---

## 🚫 4. Disable RANGE Strategy

### ปัญหา
- 2 เทรด, **Win Rate 0%**, ขาดทุน $-10.88

### แก้ไข
ใน [[analysis.ts]] → `inferAnalysis()`:
- เงื่อนไขที่เคยเลือก `RANGE` (widthPct ≥ 0.6 ใน RANGING regime) → เปลี่ยนเป็น `MEAN_REVERSION`
- RANGE analyzer ยังทำงานเพื่อสร้าง signals (signal scoring) แต่ไม่ใช่ selected strategy อีกต่อไป
- ถ้าต้องการเปิดใหม่ในอนาคต: เปลี่ยนกลับใน analysis.ts line 77

---

## ไฟล์ที่แก้ไข

| ไฟล์ | การเปลี่ยนแปลง |
|------|---------------|
| `autoTradingService.ts` | +Entry Drift Guard, +MARKET_CLOSED/RSI_EXTREME/ENTRY_DRIFT mapping |
| `PersistenceService.ts` | meanReversionMaxRrr: 1.8 → 1.5 |
| `analysis.ts` | RANGE → MEAN_REVERSION routing |
| `advanced_analytics.mjs` | +3 CATEGORY_LABELS, +3 SQL CASE patterns |

---

## Links
- [[Trading_Intelligence_MOC]]
- [[40_MeanReversion_OrderGuard_V2610]]
- [[42_DecisionGate_AnalyticsObservability_V2612]]
- [[44_WallBreakScalp_FreshDecisionPrice_V2614]]
