# ตรวจสอบ Trade Decision Log ใหม่ - 2026-05-15

ตรวจเมื่อ: 2026-05-15 00:22 Asia/Bangkok

## สรุปผล

ไฟล์ JSONL ที่สร้างใหม่ parse ผ่านทั้งหมด ไม่มี JSON เสีย และ decision records มี field หลักครบสำหรับให้ AI agent วิเคราะห์ซ้ำ ได้แก่ `decisionId`, `decisionType`, `blockCategory`, `analysis`, `signals`, `gateTrace`, `marketSnapshot`, `order`, `outcome`.

พบประเด็นที่ต้องแก้ 2 จุด:

1. `closeReason` ของ outcome บางรายการถูกตีเป็น `BE` ผิด ทั้งที่ MT5 ระบุ reason code `4` และ comment เป็น `[sl ...]`
2. ไฟล์ log เดิมใช้วันที่ UTC ทำให้ช่วงหลังเที่ยงคืนไทยยังไปลงไฟล์วันก่อนหน้า

แก้ code แล้ว:

- `detectCloseReason()` แยก MT5 reason code `4 = SL`, `5 = TP` และไม่ใช้ heuristic แบบ `sl + entry = BE` อีกต่อไป
- JSONL ใหม่เพิ่ม `atLocal` และ `timeZone`
- ชื่อไฟล์ JSONL ใหม่ใช้วันตาม `Asia/Bangkok`

## Snapshot ของไฟล์

### `mt5-core-server/data/trade_decision_logs/2026-05-13.jsonl`

- Lines: 69
- Parse errors: 0
- `TRADE_DECISION`: 69
- `ORDER_EXECUTED`: 2
- `ORDER_BLOCKED`: 48
- `NO_SIGNAL`: 19
- Top blockers:
  - `SEQUENTIAL_DUPLICATE`: 35
  - `NO_DETERMINISTIC_SIGNAL`: 19
  - `V25_ONLY_GATE`: 13
- Missing core fields: 0

### `mt5-core-server/data/trade_decision_logs/2026-05-14.jsonl`

- Lines: 283
- Parse errors: 0
- `TRADE_DECISION`: 273
- `TRADE_OUTCOME`: 10
- `ORDER_EXECUTED`: 4
- `ORDER_BLOCKED`: 265
- `NO_SIGNAL`: 4
- Top blockers:
  - `ZONE_GATE`: 201
  - `PROXIMITY_GATE`: 21
  - `SEQUENTIAL_DUPLICATE`: 18
  - `V25_ONLY_GATE`: 9
  - `ORDER_SEND_FAILED`: 1
- Outcome close reason ก่อนแก้ code:
  - `closed`: 5
  - `BE`: 5
- Suspicious BE: 5 records มี profit ไม่ใกล้ 0 และ MT5 comment เป็น `[sl ...]`; ควรอ่านเป็น SL ในการวิเคราะห์ย้อนหลัง
- Missing core fields: 0

### `mt5-core-server/data/trade_decision_logs/2026-05-15.jsonl`

- Lines: 4 ณ snapshot ล่าสุดที่ตรวจ
- Parse errors: 0
- `TRADE_DECISION`: 4
- `ORDER_BLOCKED`: 4
- `blockCategory`: `ZONE_GATE`
- มี `atLocal` และ `timeZone` แล้ว เช่น `2026-05-15T00:20:22`, `Asia/Bangkok`
- Missing core fields: 0

## ข้อสังเกตเชิงคุณภาพ

- Log ใหม่ดีพอสำหรับ AI replay เพราะมีทั้ง raw signals, deterministic signal, gate trace, market snapshot, order payload/error, และ outcome link
- `gateTrace` มีทุก decision ที่สุ่มตรวจ ทำให้ย้อนดูได้ว่า block ที่ stage ใด
- บาง `TRADE_OUTCOME` ใน `2026-05-14.jsonl` อ้าง `decisionId` จาก order เก่าที่ไม่ได้อยู่ใน JSONL ใหม่ เพราะเป็น order ก่อนเริ่มระบบ log แบบละเอียด
- หลังแก้ code แล้ว log ตั้งแต่ `2026-05-15.jsonl` จะตรงวันไทยและมีเวลาท้องถิ่น

## Verification

- `npm run build` ผ่าน
- `npm test` ผ่าน: 14 test files, 79 tests
- เพิ่ม tests สำหรับ `detectCloseReason()`:
  - MT5 reason `4` + `[sl ...]` => `SL`
  - MT5 reason `5` + `[tp ...]` => `TP`
  - explicit `sl_to_be` / `break_even` => `BE`
