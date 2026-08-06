---
title: ระบบกรองกำแพงราคาประชิด (Proximity Suppression) และการควบคุมเกณฑ์คะแนนดาว (Stars Scoring Cap)
category: Trading_Intelligence
tags: [PriceMap, SMC, Wyckoff, ProximitySuppression, StarsScoring, V26.31]
sources: [priceMapBuilder.ts, priceMapBuilder.test.ts]
created: 2026-05-23
updated: 2026-05-23
---

# ระบบควบคุมความหนาแน่น PriceMap & Stars Cap (V26.31)

เอกสารฉบับนี้บันทึกโครงสร้างทางทฤษฎีและการติดตั้งระบบระงับกำแพงราคาประชิด (Proximity Suppression) และตัวสะกดคะแนนดาว (Institutional Wall Stars Cap) ในกลไก `PriceMapBuilder` เพื่อยกระดับความแม่นยำในการตรวจจับแนวรับ-แนวต้านระดับสถาบันบนทองคำ (XAUUSD) 

---

## 🧐 ปัญหาเชิงโครงสร้างและทฤษฎี (The Problem)

ในระบบวิเคราะห์ราคาดั้งเดิม (SMC V26.25) บ่อยครั้งที่บอทจะคำนวณและแสดงผลกำแพงราคา (Price Wall) ซ้อนประชิดกันมากเกินไป (เช่น ทองคำห่างกันไม่ถึง 1.5 - 2 ดอลลาร์) และเป็นกำแพงที่มีระดับคะแนนความแข็งแกร่งสูง (4★ หรือ 5★) พร้อมกันหลายจุดดักราคาหน้าด่าน ซึ่งก่อให้เกิดปัญหา:
1. **กำแพงดาวเยอะเทียม (Fake Institutional Walls):** เกิดจากสัญญาณขนาดเล็กย่อย (M1, M5, M15, M30) รวมตัวกันแบบกระจุกตัวจนมีคะแนน confluence พุ่งขึ้นสูงเป็น 5 ดาวโดยปราศจากระดับกรอบเวลาขนาดใหญ่ (HTF) คอยยึดเหนี่ยว
2. **ขัดต่อทฤษฎีธรรมชาติของตลาด (Market Structure Conflict):** ตามหลักการ SMC/Wyckoff เมื่อราคาเคลื่อนที่ข้ามผ่านแนวต้านระดับสถาบัน (Institutional Resistance) ไปแล้ว พื้นที่ใกล้เคียงไม่ควรมีแนวต้านระดับเดียวกันขวางซ้อนขึ้นมาทันที เนื่องจากคลื่นการกวาดสภาพคล่อง (Liquidity Sweep) ย่อมทำให้พื้นที่ดังกล่าวกว้างและโล่งพอสำหรับโมเมนตัมราคา

---

## 💡 แนวทางการแก้ปัญหาใน V26.31

ระบบได้รับการปรับปรุงด้วยสองกลไกประสานการทำงาน ได้แก่:

### 1. การสะกดคะแนนดาวกรอบเล็ก (Institutional Wall Cap)
* **กฎควบคุม:** หากกำแพงราคาใดที่คำนวณขึ้นมา **ไม่มีการทับซ้อนหรือคอนฟลูเอนซ์ร่วมกับกรอบเวลาใหญ่ (HTF)** อันได้แก่ `H1`, `H4`, `D1` หรือสัญญาณ `SWING` เลย
* **ผลลัพธ์:** คะแนนความแข็งแกร่งของกำแพงดังกล่าวจะถูกจำกัดสูงสุดไว้ที่ **3 ดาว (3★)** เท่านั้น ซึ่งช่วยคัดกรอง "กำแพงชั่วคราวระดับย่อย" ออกจากกำแพงแนวต้านหลักได้อย่างหมดจด

### 2. ระบบการยุบระงับกำแพงระยะประชิด (Proximity Suppression Filter)
* **อัลกอริทึม Non-Maximum Suppression (NMS):**
  1. จัดเรียงแนวราคาที่ถูกคำนวณได้ทั้งหมดตามน้ำหนักความแข็งแกร่ง:
     $$\text{Stars (ดาว)} \rightarrow \text{Timeframe Count} \rightarrow \text{Source Count}$$
  2. วนลูปคัดเลือกกำแพงที่แข็งแกร่งที่สุดในระนาบเป็นตัวแทนหลัก
  3. ค้นหากำแพงราคาที่เป็น **ฝั่งการทำงานเดียวกัน** (Resistance ยุบ Resistance, Support ยุบ Support) หรือเป็น `BOTH`
  4. หากกำแพงใดมีระยะห่างจากตัวหลักน้อยกว่าระยะปลอดภัยขั้นต่ำ:
     $$\text{Spacing Limit} = \text{ATR} \times \text{minWallSpacingAtrPct} \quad (\text{default: } 0.75)$$
     จะทำการ **ระงับการทำงาน (Suppress)** กำแพงย่อยนั้นทันที ส่งผลให้เกิดความโปร่งโล่งและเพิ่มความลื่นไหลในการเคลื่อนที่และตัดสินใจออกออเดอร์ของบอท

---

## 🛠️ โครงสร้างฟังก์ชันการยุบระงับ (Code Implementation)

```typescript
function suppressProximityWalls(
  walls: PriceWall[],
  atr: number,
  cfg: PriceMapConfig
): PriceWall[] {
  if (!cfg.enableProximitySuppression || walls.length === 0) {
    return walls;
  }

  const spacingLimit = atr * cfg.minWallSpacingAtrPct;

  // จัดเรียงตามระดับความแข็งแกร่งเป็นหลัก
  const sorted = [...walls].sort((a, b) => {
    if (b.confluenceStars !== a.confluenceStars) {
      return b.confluenceStars - a.confluenceStars;
    }
    if (b.timeframes.length !== a.timeframes.length) {
      return b.timeframes.length - a.timeframes.length;
    }
    if (b.sources.length !== a.sources.length) {
      return b.sources.length - a.sources.length;
    }
    return 0;
  });

  const suppressed = new Set<number>();
  const kept: PriceWall[] = [];

  for (let i = 0; i < sorted.length; i++) {
    if (suppressed.has(i)) continue;

    const current = sorted[i];
    kept.push(current);

    for (let j = i + 1; j < sorted.length; j++) {
      if (suppressed.has(j)) continue;

      const other = sorted[j];

      // ยุบเฉพาะแนวราคาที่เป็นการทำงานฝั่งเดียวกัน (RESISTANCE/SUPPORT)
      const isSameSide =
        current.side === 'BOTH' ||
        other.side === 'BOTH' ||
        current.side === other.side;

      if (isSameSide) {
        const dist = Math.abs(current.price - other.price);
        if (dist < spacingLimit) {
          suppressed.add(j);
        }
      }
    }
  }

  return kept;
}
```

---

## 🧪 ผลลัพธ์และการทวนสอบ (Verification Summary)

* **Unit Testing:** ผ่านการรันทดสอบ Vitest สมบูรณ์ 7/7 เคสในโมดูล และ 106/106 เคสในระบบรวม ปราศจากข้อผิดพลาดและ regression ใดๆ
* **Analytics Simulation:** ระบบเทรดสามารถแยกแยะแนวกำแพงได้สะอาด ลื่นไหล และสอดคล้องกับพฤติกรรมเคลื่อนที่ราคาจริงของทองคำมากยิ่งขึ้น
