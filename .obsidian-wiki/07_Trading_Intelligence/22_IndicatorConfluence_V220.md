# V22.0 — Indicator Confluence (Active Decision Components)

> **Version:** V22.0  
> **Date:** 2026-05-07  
> **Status:** ✅ Build Passed — 0 errors

---

## แนวคิด

ก่อนหน้านี้ RSI, MACD, Stochastic, Bollinger Bands ถูกคำนวณทุกรอบ แต่ถูกนำไปใช้แค่เป็น **HTF bias filter** ตรงๆ ใน SignalDetector (นับ BULL/BEAR จาก H4+H1+M15) เท่านั้น

V22.0 เปลี่ยนให้ indicator ทุกตัวมีบทบาท **active** ในการตัดสินใจ entry:

```
SMC Signal (OB/FVG/Sweep/Structure)
  ↓
PriceMap Path Analysis  (V21.1)
  ↓
Indicator Confluence Check  (V22.0) ← NEW
  ├── RSI: oversold/overbought/momentum zone
  ├── MACD: cross + zero-line position
  ├── Stochastic: extreme zone + %K/%D crossover
  ├── Bollinger Bands: price at band edges
  ├── EMA/MA trend structure
  └── Market Regime bonus/penalty
  ↓
adjustedConfidence ±20 points  OR  shouldBlock → signal dropped
```

---

## ไฟล์ใหม่

```
📁 mt5-core-server/src/services/auto/core/
└── IndicatorConfluence.ts    # NEW — V22.0
```

## ไฟล์แก้ไข

```
📁 mt5-core-server/src/services/auto/core/
└── SignalDetector.ts         # เพิ่ม import + applyIndicatorConfluence() หลัง basic filter
```

---

## Scoring Table

| Signal | BUY score | SELL score | Source |
|--------|-----------|------------|--------|
| RSI<30 | +15 | -12 | technical.ts |
| RSI<45 | +6 | -5 | technical.ts |
| RSI-BULL-MOMENTUM (55-70) | +5 | -8 | technical.ts |
| RSI>70 | -12 | +15 | technical.ts |
| BB-LOWER | +8 | -8 | technical.ts |
| BB-UPPER | -8 | +8 | technical.ts |
| EMA20>MA50 | +6 | -5 | technical.ts |
| EMA20<MA50 | -5 | +6 | technical.ts |
| MACD-BULL | +12 | -10 | momentum.ts |
| MACD-ABOVE-ZERO | +5 | -5 | momentum.ts |
| MACD-BEAR | -10 | +12 | momentum.ts |
| MACD-BELOW-ZERO | -5 | +5 | momentum.ts |
| STOCH-OVERSOLD | +12 | -12 | momentum.ts |
| STOCH-CROSS-UP | +6 | -5 | momentum.ts |
| STOCH-OVERBOUGHT | -12 | +12 | momentum.ts |
| STOCH-CROSS-DN | -5 | +6 | momentum.ts |

### Regime Bonus

| Regime | BUY | SELL |
|--------|-----|------|
| TRENDING_UP | +8 | -6 |
| TRENDING_DOWN | -6 | +8 |
| RANGING | -5 | -5 |
| VOLATILE_BREAKOUT | 0 | 0 |
| QUIET | -3 | -3 |

---

## Block Conditions (Hard Block)

1. **RSI ≥ 78 + BUY** → block (extreme overbought)
2. **RSI ≤ 22 + SELL** → block (extreme oversold)
3. **STOCH-OVERBOUGHT + MACD-BEAR + BUY** → block (double-confirm reversal down)
4. **STOCH-OVERSOLD + MACD-BULL + SELL** → block (double-confirm bounce)

### Soft Block

- `indicatorScore < -25` AND `conflictedBy.length >= 3` → block signal

---

## Confidence Adjustment

```
confidenceAdjustment = clamp(indicatorScore / 5, -20, +20)
finalConfidence = clamp(baseConfidence + adjustment, 10, 100)
```

ตัวอย่าง:
- RSI<30 (BUY) + MACD-BULL + STOCH-OVERSOLD + TRENDING_UP regime
  → score = 15+12+12+8 = **+47** → adj = +9 → confidence ยิ่งขึ้น

- RSI>70 (BUY) + MACD-BEAR + STOCH-OVERBOUGHT
  → **Hard Block** → signal ถูก drop ทันที

---

## ตัวอย่าง Signal (triggers array)

```
triggers: [
  "OB_BOUNCE at 4700.0-4705.0",
  "FVG confirmed",
  "HTF bias: BULL",
  "IND: ✓ RSI<30, MACD-BULL, STOCH-OVERSOLD | ✗ MACD-BELOW-ZERO",
  "conf adj +8"
]
```

---

## ผลที่คาดหวัง

| ปัญหาเดิม | แก้ได้อย่างไร |
|---------|------------|
| RSI overbought แต่ยังเข้า BUY | Hard block เมื่อ RSI ≥ 78 |
| MACD เป็น bear แต่ SMC ให้ BUY signal | ลด confidence -10 หรือ block ถ้าซ้ำกับ Stochastic |
| Stochastic oversold + sell → ขาดทุน | Block: STOCH-OVERSOLD + MACD-BULL + SELL |
| ไม่รู้ว่า indicator สนับสนุนหรือขัดแย้ง | triggers[] บอก ✓/✗ ทุกตัวอย่างชัดเจน |
| Signal ที่ทุก indicator ขัดแย้ง ≥ 3 ตัว | Soft block เมื่อ score < -25 |

---

**Created:** 2026-05-07  
**Build:** ✅ PASSED (0 errors)
