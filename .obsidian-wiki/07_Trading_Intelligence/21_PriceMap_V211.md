# V21.1 — Price Map Builder (MTF Wall Map)

> **Version:** V21.1  
> **Date:** 2026-05-07  
> **Status:** ✅ Build Passed — 0 errors

---

## แนวคิด

ระบบ "กำแพงราคา" ที่รวม zone ทุกประเภทจากทุก Timeframe มาเป็นภาพรวมเดียว คล้ายกับที่ **Pine Script SMC V8.3 แสดงทางขวาของกราฟ**:

```
── RESISTANCE ──────────────────────
  4718.2 ★★★★★  M5+M15+H1+H4
  4710.1 ★★★  M5+M15+M30
  4705.1 M5+M15
── PRICE: 4708.2 ──────────────────
  4700.2 ★  M15+M5
  4692.5 ★★  M30+H1+M5
── SUPPORT ─────────────────────────
```

EA จะรู้ว่า:
- **จุดเด้งอยู่ตรงไหน** — OB+FVG+MTF ซ้อนกัน = stars สูง
- **ถ้าเด้งขึ้น มีอะไรขวางอยู่** — obstacle map
- **TP ที่เหมาะสมคือเท่าไร** — ก่อนกำแพง 3★+ แรก

---

## ไฟล์ใหม่

```
📁 mt5-core-server/src/services/auto/
├── analyzers/smc/
│   ├── types.ts              # เพิ่ม WallSource, PriceWall, PathObstacle, PathAnalysis, PriceMap
│   └── priceMapBuilder.ts    # NEW — MTF aggregator + path analysis
└── core/
    ├── IndicatorPipeline.ts  # เพิ่ม PriceMap build + M30 TF
    └── SignalDetector.ts     # ใช้ PriceMap หา TP แทน findNearestLevel()
```

---

## โครงสร้าง Types ใหม่

### PriceWall
```typescript
interface PriceWall {
  price: number;
  confluenceStars: number;     // 0-5★
  timeframes: string[];        // ['M5','M15','H1']
  side: 'RESISTANCE' | 'SUPPORT' | 'BOTH';
  hasOB: boolean;
  hasFVG: boolean;
  label: string;               // "4718.2 ★★★★★  M5+M15+H1"
}
```

### PathAnalysis
```typescript
interface PathAnalysis {
  entryPrice: number;
  direction: 'UP' | 'DOWN';
  suggestedTP: number;         // ก่อนกำแพง 3★+
  conservativeTP: number;      // ก่อนกำแพง 2★+
  aggressiveTP: number;        // liquidity sweep target
  obstacles: PathObstacle[];   // กำแพงระหว่างทาง
  clearPathProbability: number; // โอกาสถึง TP (0-100)
  biggestObstacle: PathObstacle | null;
  note: string;
}
```

---

## Scoring (ดาว) — วิธีคำนวณ

| เงื่อนไข | Stars |
|---------|-------|
| Base (มี level อย่างน้อย 1) | +1★ |
| แต่ละ TF เพิ่มเติม (max +3) | +1★ ต่อ TF |
| มี Order Block | +1★ |
| OB + FVG ยืนยัน | +1★ |
| cap | 5★ สูงสุด |

ตัวอย่าง: M5+M15+H1 (3 TF) + OB + FVG = 1+2+1+1 = **5★**

---

## Path Analysis — วิธีใช้

```typescript
// ตัวอย่าง: ราคาเด้งจาก 4718 ลงมา (SELL)
const path = analyzePath(priceMap, 4718, 'DOWN');

// path.suggestedTP  = 4700.17  (ก่อนกำแพง 3★ แรก)
// path.conservativeTP = 4705 (ก่อนกำแพง 2★ แรก)
// path.aggressiveTP   = 4690  (liquidity sweep)
// path.obstacles = [
//   { wall: { price: 4710.14, stars: 3 }, blockProbability: 60 },
//   { wall: { price: 4705.07, stars: 1 }, blockProbability: 20 },
// ]
// path.clearPathProbability = 52  (โอกาส 52% ที่จะถึง suggestedTP)
// path.note = "Moderate path DOWN — 3★ wall at 4710 may slow momentum"
```

---

## Integration

### IndicatorPipeline
- สร้าง SmcSnapshot **ทุก TF** (M5, M15, M30, H1, H4) แยกต่างหาก
- เรียก `buildPriceMapFromRecord(smcSnapshots, lastPrice, atr)`
- Log แผนที่กำแพงออกมาทุกรอบ (ดู console สำหรับ debug)

### SignalDetector
- ทุก entry function รับ `priceMap?: PriceMap` 
- ถ้ามี PriceMap → ใช้ `findTPFromPriceMap()` (แม่นกว่า)
- ถ้าไม่มี → fallback `findNearestLevel()` (เหมือนเดิม)
- EntrySignal มี `pathAnalysis` เพิ่มมาให้ AI agents ดูด้วย

---

## ผลที่คาดหวัง

| ปัญหาเดิม | แก้ได้อย่างไร |
|---------|------------|
| TP ตั้งไว้ไม่ถูก เจอกำแพงขวางก่อน | Path analysis หา TP ก่อนกำแพงแรกที่แข็ง |
| EA ไม่รู้ว่า zone แรงแค่ไหน | Confluence stars จาก MTF aggregate |
| เข้าออเดอร์แล้วโดน fakeout | OB+FVG+MTF ต้องซ้อนกัน ≥ 3★ ถึงเข้า |
| ไม่รู้ว่าหลัง bounce มีอะไรขวาง | obstacles list พร้อม blockProbability |

---
**Created:** 2026-05-07  
**Build:** ✅ PASSED (0 errors)
