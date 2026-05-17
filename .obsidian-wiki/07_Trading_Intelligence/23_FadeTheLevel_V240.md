# V24.0 — Fade-the-Level + Sequential Entry + Basket BE

> **2026-05-07** · ตอบสนองต่อ log การรันจริง 16:44-16:53 ที่พบ regression: ระบบ
> เปิด BUY 4 ไม้ใน PREMIUM zone ติดลบรวม -$38 ภายใน 9 นาที + ปิด BE
> ทั้งที่ basket รวมยังขาดทุน

## ปัญหาเชิงโครงสร้าง

ก่อน V24.0:
1. `deterministicEngine.ts` ตัดสินใจ BUY/SELL จาก **MTF score delta** (`scoreDelta > 8 → BUY`)
   ไม่ดูตำแหน่งของราคาเทียบกับ S/R
2. `Zone-Aware Gate` มี breakout escape clause: `pctFromRange > 100 + H4=BULL → ALLOW`
   ทำให้ BUY ที่ราคาทะลุยอด H4 ผ่านได้เสมอ → ไล่ราคาที่ premium zone
3. `concentration_close` ปิดไม้ขาดทุน + เรียกว่า "BE" ทั้งที่ basket รวมยังลบ
4. RRR check ใช้ `>=` แบบ float-naive — `1.4999... >= 1.5` คืน false → SKIP

## ปรัชญา V24.0: Fade, Don't Chase

> เข้าออเดอร์ที่ ราคา**ใกล้** S/R wall (50-100 pip) — รอเด้งกลับ ไม่ไล่ราคา

### Proximity Gate (FADE)
| Side | Condition | Direction |
|------|-----------|-----------|
| BUY  | ราคา ≥ supportWall (price อยู่เหนือแนวรับ ในระยะ ≤100 pip) | คาดเด้งขึ้น |
| SELL | ราคา ≤ resistanceWall (price อยู่ใต้แนวต้าน ในระยะ ≤100 pip) | คาดเด้งลง |

### ★ Rating Policy
- **★★★+** → approve unconditionally
- **★★** → ต้องมี LTF (M5/M15) confirmation: RSI extreme (<35 BUY / >65 SELL) **หรือ** ltf bias เริ่มกลับทิศ
- **★** → reject (ดาวน้อยไป)

### ตัวอย่างจาก Log ผู้ใช้
```
── RESISTANCE ────────────────────
  4753.42 ★★★  M5+M15+H1
  4750.59 ★
  4739.78 ★★★  M5+M15+H4
── PRICE: 4737.19 ──────────────
  4734.29 ★★  M5+M15
  4731.64 ★
  4729.95 ★★  M5+H1
```

V24.0 จะตัดสินใจดังนี้:
- **BUY**: nearestSupport = `4734.29 ★★` ที่ -2.90 (=290 pip × 0.01) **ห่างเกิน 100 pip → reject**
  เว้นแต่ระบบจะลงไปใกล้ 4734.29 ภายใน 100 pip จึงจะเปิด BUY ได้พร้อม LTF confirm
- **SELL**: nearestResistance = `4739.78 ★★★` ที่ +2.59 (=259 pip) **ห่างเกิน 100 pip → reject**

ในเฟรมนี้ระบบจะ SKIP ทั้ง BUY/SELL จนกว่าราคาจะขยับเข้าใกล้ wall

## Sequential Entry Gate

หลังเปิดไม้แรกของ symbol แล้ว ระบบจะ block ไม้ที่ 2+ จนกว่า:

| Trigger | Logic | Purpose |
|---------|-------|---------|
| `isLastSafe` | journal ของไม้ล่าสุดมี TRAIL_/Golden_/BE_ marker | Pyramid ฝั่งกำไร |
| `lastPositionR ≤ -0.25` | ไม้ล่าสุดขาดทุน ≥ 25% ของ SL distance | Recovery DCA |
| `det.management = SCALE_IN/HEDGE` | det engine บอกว่าเป็น defense plan | ผ่าน gate (legitimate defense) |

## Basket BE Guard

`concentration_close` (เดิม) ปิดไม้ขาดทุนแล้วเรียก "BE":
```
[2026-05-07 16:50:49]: Closed Trade #150699884 | XAUUSD | BE | Profit: 0
                       (ขณะ basket = -$38 รวม 4 ไม้)
```

V24.0 เพิ่ม basket guard:
- ถ้า `basketProfit < 0` → SKIP concentration close (รอ recovery / hedge)
- ถ้า `basketProfit ≥ |weakest.profit|` → close weakest OK (basket รับขาดทุนได้)

## RRR Float-Safe Compare

ทั้งสองจุด:
```ts
// เดิม
if (rrr >= minR && rd > 0) { ... }
// ใหม่
if (rrr + 1e-6 >= minR && rd > 0) { ... }
```
แก้กรณี float drift ของ `1.4999...` หลุด threshold

## Config Defaults (PersistenceService.ts)

```ts
adaptive: {
  enableProximityGate: true,
  proximityMaxPip: 100,
  proximityUnconditionalStars: 3,
  proximityConditionalStars: 2,
  enableSequentialEntry: true,
  sequentialAddOnLossPct: 0.25,
  recoveryTriggerPct: 0.25,
  requireBasketBeBeforeClose: true,
}
```

## Pip Unit Reference

| Symbol | 1 pip | 100 pip |
|--------|-------|---------|
| XAUUSD | $0.01 | $1.00   |
| XBTUSD | $1.00 | $100    |
| EURUSD | 0.0001 | 0.01   |
| USDJPY | 0.01  | 1.00    |

## Follow-up (V24.1+)
- BE @ midpoint volume calculator: `vol2 = vol1 × (entry1 - midpoint) / (midpoint - currentPrice)`
- SCALE_IN ใน TradeManagementService รับ recovery hint จาก deterministicEngine
