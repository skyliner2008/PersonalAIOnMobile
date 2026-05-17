# SQLite Cleanup + Learning Journal Upgrade - 2026-05-15

ตรวจ/ดำเนินการเมื่อ: 2026-05-15 00:47 Asia/Bangkok

## Backup

สำรอง DB ก่อนแก้ไขแล้ว:

- `mt5-core-server/data/backups/mt5-core.before-learning-cleanup.20260515003941.db`
- ขนาด backup: 101,130,240 bytes

ย้าย SQLite เก่า/ว่าง 0 byte ออกจาก root:

- archive: `mt5-core-server/data/backups/legacy-empty-sqlite-20260515004555`
- moved: `journal.db`, `mt5_core.db`, `mt5_trading.db`, `system_memory.db`, `trading.db`, `trading_v3.db`, `vector_db.sqlite`

## Cleanup Result

ก่อน cleanup:

- `auto_trading_decision_feed`: 19,570 rows
- legacy decision feed (`decision_type IS NULL`): 19,207 rows
- `auto_trading_journal`: 1,475 rows
- closed journal: 1,349 rows
- `auto_trading_management_journal`: 2,059 rows

หลัง cleanup:

- `auto_trading_decision_feed`: 363 rows
- legacy decision feed: 0 rows
- deleted legacy decision feed: 19,207 rows
- `auto_trading_journal`: 1,475 rows (ไม่ลบ trade history)
- learning-eligible closed journal: 1,288 rows
- learning-excluded closed journal: 61 rows
- learning-eligible management journal: 481 rows
- `learn_summary_json`: cleared เพื่อให้รอบ learning ถัดไปคำนวณใหม่จาก clean dataset

## Data Quality Layer

เพิ่ม field คุณภาพข้อมูล:

### `auto_trading_decision_feed`

- `quality_score`
- `quality_flags_json`
- `learning_eligible`
- `linked_journal_id`
- `linked_management_count`

### `auto_trading_journal`

- `decision_feed_id`
- `management_count`
- `quality_score`
- `quality_flags_json`
- `learning_eligible`
- `data_version`

### `auto_trading_management_journal`

- `quality_score`
- `quality_flags_json`
- `learning_eligible`

## Quality Flags ที่พบใน Journal เก่า

- `SUSPICIOUS_BE_OUTCOME`: 61 rows
- `SUSPICIOUS_BE_CLOSE_REASON`: 24 rows
- `EXECUTED_MISSING_TICKET`: 8 rows

หมายเหตุ: rows ที่ `close_reason=BE` แต่มีกำไร/ขาดทุนจริง จะยังถูกเรียนรู้ตาม `outcome/profit/profitR` ได้ถ้า outcome ไม่ผิด เพียงติด flag ไว้ไม่ให้ AI เข้าใจว่า close reason สะอาด

## Learning System Changes

ปรับ `runLearn()` ให้ใช้เฉพาะ clean dataset:

- ใช้ `getLearningJournal()` แทน `getDecisionJournal()`
- ใช้เฉพาะ rows ที่ `learning_eligible=1`
- เพิ่ม `dataQuality` เข้า `learnSummary`
- เพิ่ม `blockStats` จาก decision feed ที่ schema ครบ
- เพิ่ม `managementStats` จาก management journal ที่ clean
- AI prompt สำหรับ meta-learning ถูกบอกให้ ignore excluded/legacy rows

ปรับ dashboard analytics / sleep consolidation:

- performance analytics ใช้เฉพาะ `learning_eligible=1`
- weekly memory consolidation ใช้เฉพาะ `learning_eligible=1`
- graph weights ไม่ใช้ trade records ที่ติดปัญหา

## Current Clean Decision Feed Mix

- `ZONE_GATE`: 223
- `SEQUENTIAL_DUPLICATE`: 53
- `NO_DETERMINISTIC_SIGNAL`: 23
- `V25_ONLY_GATE`: 22
- `PROXIMITY_GATE`: 21
- `ORDER_EXECUTED`: 6
- `ORDER_SEND_FAILED`: 1

## Verification

- `npm test` ผ่าน: 14 files, 79 tests
- `npm run build` ผ่าน

## Next Step

หลัง restart/reload service:

- future log จะเขียน quality/link fields อัตโนมัติ
- รอบ learning ถัดไปจะ rebuild `learnSummary` จาก clean dataset
- ถ้าต้องการ backfill `close_reason` ของ historical BE ให้แม่นกว่าเดิม ต้องใช้ raw MT5 history/deal reason ย้อนหลังเป็นแหล่งอ้างอิง ไม่ควรเดาจาก profit เพียงอย่างเดียว
