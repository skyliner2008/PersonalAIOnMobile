# ตรวจสอบความเสี่ยง Trade Decision Log ใหม่ - 2026-05-15

ตรวจเมื่อ: 2026-05-15 00:36 Asia/Bangkok

## Snapshot ล่าสุด

ไฟล์ที่พบใน `mt5-core-server/data/trade_decision_logs`:

- `2026-05-15.jsonl`
- 16 records ณ snapshot ตอนตรวจ
- Parse errors: 0
- Records ทั้งหมดเป็น `TRADE_DECISION`
- `decisionType`: `ORDER_BLOCKED` 16
- `blockCategory`: `ZONE_GATE` 16
- `side`: `SKIP` 16
- `TRADE_OUTCOME`: 0
- `ORDER_EXECUTED`: 0

## จุดที่สมบูรณ์แล้ว

- JSONL parse ผ่านทุกบรรทัด
- มี `atLocal` และ `timeZone` ครบทุก record
- วันที่ไฟล์ตรงกับวันไทย (`Asia/Bangkok`)
- ไม่มี duplicate `decisionId`
- ไม่มี `ORDER_BLOCKED` ที่ `wasExecuted=true`
- ไม่มี `NO_SIGNAL` ที่มี order payload
- `gateTrace` ไม่ว่าง และมีลำดับชัดเจน: `analysis > deterministic > final_entry_gate`
- ข้อมูลภาษาไทยใน `translatedTh` อ่านได้ ไม่เป็น mojibake

## จุดเสี่ยง / ยังไม่สมบูรณ์

### 1. Coverage ยังแคบ

log ล่าสุดมีแต่สถานการณ์เดียว:

- deterministic เห็น SELL
- H4/H1 อยู่ `DISCOUNT`
- final gate จึง block ด้วย `ZONE_GATE`

ยังไม่มีตัวอย่างจริงหลัง patch สำหรับ:

- `ORDER_EXECUTED`
- `TRADE_OUTCOME`
- `ORDER_SEND_FAILED`
- `NO_SIGNAL`
- `PROXIMITY_GATE`
- `SEQUENTIAL_DUPLICATE`
- management cases เช่น `SCALE_IN`, `HEDGE`, `BREAK_EVEN`

ดังนั้นยืนยันได้ว่า block log แข็งแรง แต่ยังยืนยัน end-to-end order/outcome log หลัง patch ไม่ครบทุก path

### 2. top-level `fitness` ใน records ปัจจุบันยังผิด

ทุก record ล่าสุดมี:

- top-level `fitness = 0`
- แต่ `analysis.fitness` จริงประมาณ `82-89`

ผลกระทบ: AI ที่อ่านเฉพาะ top-level อาจเข้าใจผิดว่า setup อ่อน ทั้งที่จริง setup ถูก block เพราะ zone gate

แก้ code แล้วใน `autoTradingService.ts`: future log จะ sync `fitness/confluence` จาก analysis จริง

### 3. `SKIP` ยังมี trade metrics เหมือน order จริง

records ปัจจุบันของ `SKIP` ยังมี:

- `marketSnapshot.actualRrr`
- `marketSnapshot.riskDistance`

แต่ไม่ได้บอกชัดว่าเป็น candidate plan ไม่ใช่ออเดอร์ที่ถูกส่ง

ผลกระทบ: AI อาจเอา RRR ของแผนที่ถูก block ไปคิดเหมือน trade ที่ execute จริง

แก้ code แล้ว:

- `actualRrr/riskDistance` จะเป็น metric ของ order จริงเท่านั้น
- เพิ่ม `candidateSide`
- เพิ่ม `candidateRrr`
- เพิ่ม `candidateRiskDistance`
- เพิ่ม `tradePlanStatus` เช่น `BLOCKED`, `EXECUTED`, `ALLOWED`, `PAPER`

### 4. Historical SQLite ยังมีข้อมูลเก่าที่ schema ไม่ครบ

ใน `auto_trading_decision_feed` มี rows เก่าจำนวนมากที่:

- `decision_type = null`
- `block_category = null`

เป็นข้อมูลก่อนเพิ่ม schema ใหม่ ไม่ใช่ปัญหาของ log ล่าสุด แต่ถ้า AI ดึงทั้ง DB ย้อนหลังโดยไม่ filter จะเจอข้อมูลเก่าไม่ครบ

แนวทาง: ให้ agent ใช้เฉพาะ rows ที่ `decision_type IS NOT NULL` สำหรับ training/replay แบบใหม่ หรือทำ backfill แยกภายหลัง

### 5. Historical journal ยังมี BE เก่าที่น่าสงสัย

ใน `auto_trading_journal` ยังมีข้อมูลเก่าที่ `close_reason = BE` แต่ profit/profitR ไม่ใกล้ศูนย์ เช่นบางรายการเป็น `-1R`

future parser แก้แล้ว แต่ historical DB ยังไม่ได้ backfill

แนวทาง: ถ้าจะใช้ข้อมูลเก่า train model ควร normalize อีกชั้น:

- `close_reason=BE` แต่ `profitR <= -0.15` ให้ถือว่า suspicious และอย่า train เป็น BE
- ถ้ามี MT5 reason code/history deal ให้ reclassify ตาม `reason: 4 = SL`, `reason: 5 = TP`

## การแก้ไขที่ทำในรอบนี้

- แก้ future log ให้ top-level `fitness/confluence` sync จาก `analysis`
- แยก metrics ของ candidate plan ออกจาก metrics ของ order จริง
- เพิ่ม fields: `candidateSide`, `candidateRrr`, `candidateRiskDistance`, `tradePlanStatus`

ต้อง restart/reload service เพื่อให้ process ที่กำลังเขียน log ใช้ code ใหม่

## Verification

- `npm run build` ผ่าน
- targeted tests ผ่าน: 2 files, 9 tests
- full tests ผ่าน: 14 files, 79 tests
