# Deep Audit: 94 Skips Before Order

วันที่วิเคราะห์: 2026-05-14  
แหล่งข้อมูล: `C:\Users\JOJO\AndroidStudioProjects\PersonalAIBot\log.txt`  
ช่วงเวลาใน log: 2026-05-13 23:10:05 ถึง 2026-05-14 01:26:30  
Scope: AutoEngine cycles ที่จบด้วย `Cycle #... completed`

## Executive Summary

ตัวเลข `94 skipped` ถูกต้องตาม log แต่ไม่ควรตีความว่าเป็น "94 order signal ที่พร้อมยิงแล้วโดนบล็อก" ทั้งหมด

จาก 97 cycles:

| กลุ่ม | จำนวน | ความหมายจริง |
|---|---:|---|
| เปิด order ได้ | 3 | ส่งคำสั่งจริง และ broker ตอบ success |
| AI unavailable / no fallback | 37 | ส่วนใหญ่ deterministic เป็น `SKIP` อยู่แล้ว ไม่ใช่ signal พร้อมเปิด |
| V25-only direct disabled | 30 | deterministic มี SELL แต่ถูกบังคับให้รอ V25 wall state |
| MTF zone gate | 15 | BUY ถูกบล็อกเพราะ H4/H1 อยู่ PREMIUM |
| Sequential duplicate | 12 | SELL ซ้ำใกล้ order เดิมน้อยกว่า 5.0 เหรียญ |

ประเด็นใหญ่ที่สุดมี 3 เรื่อง:

1. `AI unavailable and EA Fallback...` ทำให้รายงานดูเหมือน AI block order ทั้งที่ 36/37 รอบ deterministic action เป็น `SKIP` ตั้งแต่ต้น
2. `enableV25Only` ทำให้ V24 deterministic SELL จำนวนมากถูกยับยั้ง เพราะ V25 wall state stale/IDLE/APPROACH ไม่ผ่าน
3. Sequential gate มีจุดน่าสงสัยใน code: `SCALE_IN` ถูกนับเป็น planned defense แต่ยังโดน `nearDuplicateEntryIssue` ก่อนถึง logic ที่ยกเว้น defense

## Parser / Count Correctness

วิธีนับที่ใช้:

- นับ 1 event ต่อ `Cycle #... completed`
- ผูก context ตั้งแต่ `MTF Analysis ready` ถึง `Cycle completed`
- อ่าน `Deterministic`, `Final Side`, `Side=... Gate=...`
- ถ้ามี `Sequential Entry blocked` ก่อน `Final Side` จะจัดเข้ากลุ่ม Sequential แม้บรรทัด `Gate=` ยังแสดง rationale เดิม

ผลนับ:

```text
total cycles: 97
executed: 3
skipped: 94
```

Breakdown:

```text
OPENED                      3
AI unavailable/no fallback 37
V25 stale                  25
MTF zone gate              15
Sequential duplicate       12
V25 approach distance       2
V25 IDLE                    2
V25 wall stars              1
```

ข้อควรระวัง: ถ้านับจาก `Gate=` อย่างเดียวจะนับ Sequential ผิด เพราะ final gate ยังเป็นข้อความ deterministic เดิม เช่น `[EA-ONLY] AI disabled...` ทั้งที่มี warning ว่า `Sequential Entry blocked` แล้ว

## 1. AI Unavailable / No Fallback: 37 รอบ

### Log Pattern

`Gate=AI unavailable and EA Fallback conditions not met (no actionable deterministic fallback)`

### Deep Breakdown

ใน 37 รอบนี้:

| deterministic action | จำนวน |
|---|---:|
| SKIP | 36 |
| SELL | 1 |

ดังนั้นกลุ่มนี้ส่วนใหญ่ไม่ใช่ order signal ที่ถูก block แต่เป็นรอบที่ engine ยังไม่มี deterministic trade action

ตัวอย่างที่เป็น trade signal จริงมีแค่ 1 รอบ:

| cycle | time | deterministic | regime | เหตุผล |
|---:|---|---|---|---|
| 11752 | 2026-05-13 23:42:11 | SELL 34.9 | RANGING | confidence ต่ำกว่า fallback threshold |

### Code Audit

ไฟล์: `mt5-core-server/src/services/autoTradingService.ts`

จุดสำคัญ:

- `enableAiMode` default เป็น true ที่ `PersistenceService.ts:35`
- EA fallback threshold:
  - AI mode on: 45
  - AI mode off: 35
- fallback เข้าเฉพาะเมื่อ `detDecision.action !== 'SKIP'` และ confidence ผ่าน threshold
- ถ้าไม่มี `aiDecision` จะ hard fallback เป็น SKIP พร้อมข้อความ `AI unavailable and EA Fallback...`

สรุปความถูกต้อง:

- Logic ป้องกัน raw technical bias ไม่ให้ยิงเองแบบไม่ผ่าน fallback ถือว่าถูกต้อง
- แต่ข้อความ log ไม่แม่น เพราะเมื่อ deterministic เป็น `SKIP` อยู่แล้ว ไม่ควรบอกเหมือน AI unavailable เป็นสาเหตุหลัก

### Recommended Fix

ปรับ reason taxonomy:

```ts
if (detDecision.action === 'SKIP') {
  reason = `No deterministic trade signal (${detDecision.rationale})`;
} else if (detDecision.confidence < eaConfidenceThreshold) {
  reason = `EA fallback confidence ${detDecision.confidence} < ${eaConfidenceThreshold}`;
} else {
  reason = `AI unavailable and EA fallback blocked: ${eaFallbackMissReason}`;
}
```

ผลลัพธ์: รายงานจะไม่ inflate ว่า AI block 37 รอบ ทั้งที่ signal จริงมีเพียง 1 รอบ

## 2. V25-only Direct Disabled: 30 รอบ

### Breakdown

| V25 reason | จำนวน |
|---|---:|
| stale wall state | 25 |
| APPROACH distance เกิน | 2 |
| IDLE state | 2 |
| wall stars ต่ำ | 1 |

ทุกเคสในกลุ่มนี้ deterministic เป็น `SELL` ทั้งหมด

Regime:

| regime | จำนวน |
|---|---:|
| TRENDING_DOWN | 18 |
| RANGING | 12 |

### Stale Age

สำหรับ stale 25 รอบ:

```text
min    217,128 ms
median 1,214,688 ms
max    2,383,314 ms
```

default code ยอมให้ fresh แค่ 90,000 ms เท่านั้น

### Code Audit

ไฟล์: `mt5-core-server/src/services/autoTradingService.ts`

จุดสำคัญ:

- `v25AlignedDirectEntry()` ใช้ wall state จาก state machine
- SELL ต้องดู `snap.above` หรือ resistance wall
- state ที่ยอมรับ: `REACT`, `CONFIRM`, `RETEST`
- `APPROACH` ผ่านได้เฉพาะกรณี qualified
- `hybridDirectMaxStateAgeMs` default = 90,000 ms
- ถ้า `enableV25Only=true` และ V25 ไม่ aligned จะบังคับ `side='SKIP'`

สรุปความถูกต้อง:

- Code ทำงานตรงกับ config: เมื่อเปิด V25-only, V24 deterministic ไม่ควรยิงเองถ้า V25 wall ไม่สดพอ
- แต่สำหรับตลาดที่เป็น `TRENDING_DOWN/TREND_FOLLOW`, V25 wall-touch logic อาจเข้มเกิน เพราะ V25 มักออกแบบมาทาง wall reaction/reversal มากกว่า continuation
- ใน code มี comment ยอม bypass เฉพาะ `BREAKOUT` เพราะ V25 wall-touch ทำ continuation ไม่ได้ แต่ log ชุดนี้ปัญหาเกิดหนักใน `TRENDING_DOWN/TREND_FOLLOW` เช่นกัน

### Recommended Fix

เลือกตามเจตนาระบบ:

#### ทางเลือก A: strict V25 จริง

คง logic เดิม แต่ปรับ freshness:

```ts
adaptive.v25.hybridDirectMaxStateAgeMs = 180_000 ถึง 300_000
```

และให้ wall state machine refresh `enteredAt` เมื่อมี continued valid confirm/retest ไม่ใช่ปล่อย stale ยาวจน deterministic ถูกล็อก

#### ทางเลือก B: allow trend-follow direct

เพิ่ม bypass แบบมีเงื่อนไขสำหรับ trend-follow:

```ts
allowV24TrendFollowDirectWhenV25Stale = true
minTrendFollowConfluence = 80
requireHtfAligned = true
requireNoStrongOpposingWallWithinAtr = 0.5
```

เหมาะกับ log นี้ เพราะช่วงที่โดน block จำนวนมากเป็น SELL ตาม `TRENDING_DOWN` และ confluence สูง

## 3. MTF Zone Gate: 15 รอบ

### Log Pattern

`MTF zone gate veto: BUY blocked (H4:PREMIUM | H1:PREMIUM)`

### Breakdown

| deterministic | จำนวน |
|---|---:|
| BUY 40 | 15 |

Management:

| mgmt | จำนวน |
|---|---:|
| HEDGE | 8 |
| HOLD | 7 |

Regime ทั้งหมดเป็น `RANGING`

### Code Audit

ไฟล์: `mt5-core-server/src/services/autoTradingService.ts`

Logic:

- ถ้า deterministic เป็น BUY/SELL และ `enableZoneAwareGate=true`
- classify H4, H1, active TF เป็น PREMIUM/DISCOUNT
- BUY ใน PREMIUM = wrong zone
- SELL ใน DISCOUNT = wrong zone
- block เฉพาะเมื่อทุก layer wrong และไม่ใช่ breakout pass

สรุปความถูกต้อง:

- สำหรับ signal BUY ใน PREMIUM พร้อม bias BEAR การ block ถือว่าถูกต้องตาม SMC/zone logic
- จุดที่ต้องตัดสินเชิงกลยุทธ์คือ `mgmt=HEDGE`: log มี HEDGE BUY 8 รอบที่ถูก block เช่นกัน ถ้า HEDGE ตั้งใจเป็น defensive hedge ของ SELL ที่ติดลบ อาจควรมี exception แบบจำกัดความเสี่ยง

### Recommended Fix

อย่าเปิด HEDGE ผ่าน zone gate แบบ blanket

ควรอนุญาตเฉพาะเมื่อ:

- มี position ฝั่งตรงข้ามจริง
- last/net exposure ติดลบเกิน threshold เช่น `-0.25R`
- hedge lot จำกัด เช่น 25-50% ของ exposure
- ไม่ชนข่าว/high spread
- มี stop/exit plan ชัดเจน

ถ้า HEDGE ในระบบเป็นเพียง signal ฝั่งตรงข้าม ไม่ใช่ defensive position จริง ให้คง block เดิม

## 4. Sequential Duplicate: 12 รอบ

### Breakdown

ทั้งหมดเป็น SELL

| mgmt | จำนวน |
|---|---:|
| SCALE_IN | 9 |
| HOLD | 3 |

ระยะห่างจาก order เดิม:

```text
min    0.29
median 2.62
max    4.18
threshold 5.00
```

ตัวอย่าง:

| cycle | time | mgmt | entry | distance | open ticket |
|---:|---|---|---:|---:|---|
| 11729 | 2026-05-13 23:18:01 | HOLD | 4700.48 | 0.82 | 154708894 |
| 11731 | 2026-05-13 23:20:02 | HOLD | 4699.37 | 0.29 | 154708894 |
| 11785 | 2026-05-14 00:46:53 | SCALE_IN | 4693.99 | 1.86 | 154745446 |
| 11800 | 2026-05-14 01:05:06 | SCALE_IN | 4687.95 | 4.18 | 154745446 |

### Code Audit

ไฟล์หลัก:

- `mt5-core-server/src/services/autoTradingService.ts`
- `mt5-core-server/src/services/auto/core/TradingExecutionService.ts`

จุดสำคัญ:

- `isPlannedDefense` ถูก set true เมื่อ `detDecision.management === 'SCALE_IN' || 'HEDGE'`
- แต่ duplicate check ใช้เงื่อนไข `sameSide.length > 0 && detDecision.management !== 'HEDGE'`
- แปลว่า `SCALE_IN` ยังโดน `nearDuplicateEntryIssue()` ก่อน
- `nearDuplicateEntryIssue()` ใช้ threshold XAU default 5.0 หรือ ATR * 0.10 แล้วเลือกค่าที่มากกว่า

สรุปความถูกต้อง:

- สำหรับ `HOLD` ที่ยิงซ้ำใกล้ order เดิม 0.29-0.82 การ block ถูกต้อง
- สำหรับ `SCALE_IN` มีโอกาสเป็น bug/logic mismatch เพราะ comment บอกว่า `SCALE_IN/HEDGE` เป็น defense plan แต่ implementation ยกเว้นแค่ `HEDGE`

### Recommended Fix

ควรแยก duplicate policy ของ normal entry กับ scale-in:

```ts
if (sameSide.length > 0 && !isPlannedDefense) {
  // ใช้ nearDuplicateEntryIssue แบบเดิม min 5.0
}

if (sameSide.length > 0 && detDecision.management === 'SCALE_IN') {
  // ใช้ scale-in rule แยก เช่น min 1.5-2.0,
  // ต้องมี BE/trail safe หรือ loss gate,
  // จำกัดจำนวน add-on และ total heat
}
```

หรือถ้าต้องการ conservative:

```ts
const minDistance =
  detDecision.management === 'SCALE_IN'
    ? cfg.adaptive?.scaleInMinEntryDistanceXAU ?? 2.0
    : cfg.adaptive?.sequentialMinEntryDistanceXAU ?? 5.0;
```

พร้อมเพิ่ม test ว่า `SCALE_IN` ไม่โดน normal duplicate guard โดยไม่ตั้งใจ

## 5. V25 PlaybookSelector Skips นอก AutoEngine 94 รอบ

มี V25 skip เพิ่มอีก 10 events ที่ไม่ได้อยู่ในตัวเลข AutoEngine `94 skipped`

| category | จำนวน |
|---|---:|
| Playbook filters failed | 4 |
| Decision Gate | 2 |
| AI Bias Gate | 2 |
| Plan Risk Gate | 1 |
| Price drift / live RRR below min | 1 |

สรุป:

- V25 playbook gate ส่วนใหญ่ทำงานสมเหตุผล
- แต่เมื่อผูกกับ `enableV25Only` จะเกิด "สองชั้น" คือ AutoEngine ถูกห้ามยิง direct และ V25 เองก็ไม่ emit live order เพราะ playbook/reversal filters ไม่ผ่าน

## Test Status

รันคำสั่ง:

```powershell
npm test
```

ผลลัพธ์:

```text
Test Files  13 passed
Tests       73 passed
```

ข้อสังเกต:

- มี test สำหรับ V25 decision skip, AI bias gate, live preflight, proximity gate, risk, journal
- ยังไม่พบ test เฉพาะสำหรับ sequential gate ที่ `SCALE_IN` ถูก `nearDuplicateEntryIssue` block ก่อน defense logic
- ควรเพิ่ม test ก่อนแก้ logic จุดนี้

## Priority Fix Plan

### P0: ปรับ reporting/reason ให้ไม่หลอกตา

เป้าหมาย: แยก "ไม่มี signal" ออกจาก "signal ถูก block"

ควรเปลี่ยน hard fallback reason:

- `NO_DETERMINISTIC_SIGNAL`
- `EA_FALLBACK_CONFIDENCE_LOW`
- `EA_FALLBACK_RRR_LOW`
- `AI_UNAVAILABLE`

ผลที่คาดหวัง: จำนวน block จริงจะลดจากภาพ 94 ลงเหลือกลุ่ม actionable ชัดเจน เช่น V25, zone, duplicate

### P1: แก้ Sequential SCALE_IN policy

เป้าหมาย: ไม่ให้ scale-in ที่ตั้งใจไว้ถูก normal duplicate guard ฆ่าก่อน

เพิ่ม config:

```ts
adaptive.scaleInMinEntryDistanceXAU = 2.0
adaptive.scaleInRequiresSafeOrLossGate = true
adaptive.maxScaleInPerSymbol = 2
```

และเพิ่ม unit test:

- HOLD near duplicate ต้อง block
- SCALE_IN ใกล้กว่า 5.0 แต่เกิน scale-in min และผ่าน safety gate ต้อง allow
- SCALE_IN ใกล้เกิน min หรือ heat เกิน ต้อง block

### P1: ปรับ V25-only ให้เหมาะกับ trend-follow

ถ้าเป้าคือให้ระบบเทรด trend-follow ได้จริง:

- เพิ่ม `hybridDirectMaxStateAgeMs` เป็น 180-300 วินาที
- refresh wall state เมื่อมี continued confirmation
- เพิ่ม trend-follow direct bypass เฉพาะ confluence สูง + HTF aligned + no strong opposing wall

ถ้าเป้าคือ strict V25:

- ให้ AutoEngine report ว่า "delegated to V25 but V25 not executable" ชัดเจน
- ลดการนับเป็น missed order ใน dashboard

### P2: ทบทวน MTF zone gate สำหรับ HEDGE

ไม่ควรปิด zone gate ทั้งหมด

แต่ถ้า HEDGE คือ defensive hedge จริง ให้เพิ่ม exception แบบมีเงื่อนไข:

- opposite exposure exists
- current exposure losing
- hedge size capped
- hedge has exit plan

### P2: เพิ่ม observability ของ order close outcome

log มีหลายครั้งที่ position ปิดแล้วแต่หา closing history ไม่เจอ ทำให้แยก SL/BE/closed outcome ไม่แม่น

ควรเก็บ:

- close deal id
- close reason จาก MT5 ถ้ามี
- closed by SL/TP/manual/BE/trailing
- before/after SL ตอนปิด

## Final Judgment

Code block ส่วนใหญ่ "ทำงานตามที่ถูกเขียนไว้" ไม่ใช่ broker reject และไม่ใช่ส่ง order ไม่สำเร็จ

แต่มี 2 จุดที่ควรแก้จริง:

1. Reason/reporting ของ `AI unavailable` ทำให้ตีความผิดอย่างมาก
2. Sequential gate น่าจะมี logic mismatch กับ `SCALE_IN` เพราะประกาศว่าเป็น planned defense แต่ยังถูก near-duplicate block แบบ normal entry

ส่วน V25-only เป็น config/architecture bottleneck มากกว่า bug ตรงๆ: ถ้าเปิด strict V25-only แต่ V25 wall state stale เป็นหลัก ระบบจะ skip เยอะเป็นธรรมชาติ
