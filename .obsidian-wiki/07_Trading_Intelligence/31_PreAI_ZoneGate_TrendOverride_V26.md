# V26.1 — Pre-AI Zone Gate: Breakdown + FVG Override Fix

> **วันที่:** 2026-05-15
> **ไฟล์ที่แก้ไข:**
> - `autoTradingService.ts` — Pre-AI Zone Gate logic (~L1234-1360)
> - `auto/types.ts` — เพิ่ม `zoneBreakoutConfluenceMin`
> **สถานะ:** ✅ Build passed

---

## ปัญหาที่พบ (V26.0 ไม่เพียงพอ)

หลังใช้งาน V26.0 Fix ตั้งแต่ 03:10 พบว่า:
- **134 records ยังถูก Zone Gate block** (เพิ่มจาก 152 ก่อน fix)
- **Override ไม่เคยทำงาน** เพราะ **3 เงื่อนไข** fail พร้อมกัน:

| เงื่อนไข | สถานะ | ค่า |
|---|---|---|
| HTF Aligned | ✅ PASS | H4=BEAR, H1=BEAR → ตรง SELL |
| Trending Regime | ✅ PASS | VOLATILE_BREAKOUT |
| Continuation Strategy | ✅ PASS | BREAKOUT (match) |
| Not Counter-Trend | ✅ PASS | counterTrend = false |
| **Not Deep Wrong Zone** | ❌ FAIL | pctFromRange ~9.8% ≤ 25% |
| **Confluence ≥ 68** | ❌ FAIL | confluence ~56.7 (max 62.9) |
| **FVG Inside Price** | ❌ FAIL* | FVG at 4656-4666, price at 4651 (ต่ำกว่า) |

> [!WARNING]
> `*` FVG proximity check V26.0 เข้มเกินไป — ราคา 4651 อยู่ห่างจาก FVG bottom (4656) แค่ $5 แต่ต้อง "อยู่ใน" FVG เท่านั้น

### Root Cause Analysis

```
H4 Structure: BULLISH | High: 4773.39 | Low: 4638.16
Price: 4651.47
pctFromRange = ((4651 - 4638) / (4773 - 4638)) × 100 = 9.8%
hardBlockPct = 25%
isDeepWrongZone = 9.8% ≤ 25% → TRUE → notDeepWrong = FALSE
```

**ปัญหา:** H4 Market Structure ค้างเป็น BULLISH (High: 4773) ทำให้ pctFromRange ต่ำมากเพราะ range กว้าง → deep wrong zone ตลอด

---

## แนวทาง V26.1

### Fix 1: Breakdown Detection
เมื่อราคา break ต่ำกว่า H4 swing low (pctFromRange < 0) → bypass deep zone check

### Fix 2: Confluence Threshold ลดลงสำหรับ Breakout
- `VOLATILE_BREAKOUT` regime → ใช้ threshold 55 (แทน 68)
- เพิ่ม config `zoneBreakoutConfluenceMin`

### Fix 3: FVG Proximity Widened
ขยายจาก "ราคาอยู่ภายใน FVG" → "ราคาอยู่ภายใน 1 ATR ของ FVG"

### Fix 4: FVG เป็น Independent Override Path
```
trendContinuationPass = (base conditions) AND (
  Path 1: notDeepWrong AND confluenceOk  → 0.3x lot
  Path 2: fvgAligned (independent)       → 0.15x lot  ← สำคัญ!
  Path 3: isBreakdown AND confluenceOk   → 0.3x lot
)
```

---

## Simulation V26.1 (dry-run กับ 134 records)

ถ้าใช้ logic V26.1 ใหม่:
- **FVG proximity** ผ่าน 134/134 (ทุก record มี Bear FVG ภายใน 1 ATR)
- **Confluence ≥ 55** ผ่าน 56/134
- **Path 2 (FVG_INSTITUTIONAL)** จะทำให้ **134/134 records ผ่าน override** ด้วย lot 0.15x
- **Path 1 + 3** จะผ่าน 56/134 records ด้วย lot 0.3x

### Expected Behavior

| สถานการณ์ | Before V26.1 | After V26.1 |
|---|---|---|
| SELL ใน Discount + FVG nearby + HTF BEAR | ❌ BLOCKED | ✅ PASS (0.15x lot) |
| SELL ใน Discount + Conf 57 + No FVG | ❌ BLOCKED | ❌ BLOCKED (ถูกต้อง) |
| SELL ใน Discount + Conf 57 + FVG 5 ATR away | ❌ BLOCKED | ❌ BLOCKED (ถูกต้อง) |
| BUY ใน Premium + H4 BEAR | ❌ BLOCKED | ❌ BLOCKED (ถูกต้อง) |

---

## Safety Guards

1. **FVG_INSTITUTIONAL path ใช้ lot 0.15x** — เล็กที่สุดใน 3 paths
2. **ต้องมี HTF aligned + Regime trending + Continuation strategy** — 4 เงื่อนไขพื้นฐานยังเหมือนเดิม
3. **ต้องไม่ใช่ counter-trend**
4. **FVG ต้องอยู่ภายใน 1 ATR** — ไม่ใช่ FVG ห่างออกไป
5. **Post-AI Zone Gate ยังทำงานเป็นชั้นที่ 2** — signal ที่ผ่าน Pre-AI ยังต้องผ่าน Post-AI gate

---

## Config Parameters (ใหม่)

| Parameter | Default | คำอธิบาย |
|---|---|---|
| `adaptive.zoneBreakoutConfluenceMin` | 55 | Confluence ขั้นต่ำสำหรับ VOLATILE_BREAKOUT regime |
| `adaptive.zoneContinuationConfluenceMin` | 68 | Confluence ขั้นต่ำสำหรับ TRENDING regime |
| `adaptive.zoneHardBlockPct` | 25 | pctFromRange ที่ถือว่า deep wrong zone |

---

## ไฟล์ที่เกี่ยวข้อง

- [[16_Complete_Code_Logic_V19.6|Code Logic V19.6]]
- [[25_TradeFlow_Diagrams|Trade Flow]]
- [[27_Skip_Blocker_Code_Audit_2026-05-14|Skip Blocker Audit]]
