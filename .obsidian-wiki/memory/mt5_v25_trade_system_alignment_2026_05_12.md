---
name: MT5 V25 Trade System Alignment 2026-05-12
type: project-memory
source: mt5-core-server + log.txt + .obsidian-wiki
---

# MT5 V25 Trade System Alignment 2026-05-12

## Wiki Source Of Truth

เอกสารที่ใช้เป็นแกนตรวจสอบระบบเทรด:

- `.obsidian-wiki/01_Architecture/04_AutoTrading_Workflow.md`
- `.obsidian-wiki/07_Trading_Intelligence/21_PriceMap_V211.md`
- `.obsidian-wiki/07_Trading_Intelligence/23_FadeTheLevel_V240.md`
- `.obsidian-wiki/07_Trading_Intelligence/25_RealTimeWallEngine_V25.md`
- `.obsidian-wiki/07_Trading_Intelligence/25_TradeFlow_Diagrams.md`
- `.obsidian-wiki/memory/project_v25_real_time_wall_engine.md`

## Trading Principles Confirmed

1. V25 ต้องเป็นระบบ wall-first: no wall, no trade.
2. Entry ต้องเกิดใกล้ reaction zone ไม่ใช่กลาง channel.
3. Playbook priority คือ PB3 sweep, PB2 OB+FVG, PB5 breakout retest, PB4 range fade, PB1 wall touch.
4. SL/TP ต้องสัมพันธ์กับ swing, wall, ATR และ spread ไม่ใช่ปล่อยให้ RRR ถูกบีบจาก TP cap หรือ SL ไกลเกินเหตุ.
5. AI/AutoEngine decision เป็น directional mandate สำหรับ V25 execution เมื่อ `enableV25Only` เปิดอยู่.
6. Trade journal ต้องเก็บ structured context เพื่อให้ Trade Manager และ learning/post-mortem อ่านกลับได้จริง.

## Log Finding

จาก `log.txt` พบ flow สำคัญ:

- AutoEngine ให้ final side เป็น SELL แต่ถูกเปลี่ยนเป็น SKIP เพราะ V25-only delegation.
- V25 PB4 ออก BUY หลัง wall event เพราะ gate เดิมอ่านเพียง `side=SKIP` และ `overallBias=BULL` ทำให้ไม่เห็น mandate ฝั่ง SELL ที่อยู่ใน rationale.
- หลัง order ปิด BE/profit 0 มีความเสี่ยงที่ learning จะเรียนจากข้อมูลไม่ครบ เพราะ journal เดิมเป็น mock/minimal context.

## Code Alignment Applied

ไฟล์หลัก: `mt5-core-server/src/services/auto/v25/playbooks/index.ts`

- เพิ่ม `inferDirectionalMandate()` เพื่ออ่าน side จาก `decision.side`, rationale tags เช่น `[AI SELL]`, `[DET BUY]`, `FINAL SIDE=...`, และ fallback จาก bias.
- เพิ่ม `maxDecisionAgeMs` เพื่อกันการใช้ decision เก่าเกินไป.
- เพิ่ม Decision Gate เพื่อ block V25 side ที่ขัดกับ AutoEngine mandate.
- เพิ่ม `planRiskIssue()` เป็น hard plan sanity gate ก่อน execute.
- เพิ่ม live per-symbol cap check ก่อนส่งคำสั่งจริง.
- เปลี่ยน V25 execution journal จาก mock เป็น structured journal ที่มี playbook, wall, state event, mandate, regime, ATR, spread, trigger และ market snapshot.

## Next Best Improvements

1. ปิดวงจร learning ให้ใช้ broker history เป็นแหล่ง PnL จริงก่อนสรุป outcome.
2. ให้ V25 order สร้าง decision-feed event ด้วย เพื่อ dashboard เห็นเหตุผลเดียวกับ journal.
3. ทำ one-trade-per-wall guard ตาม V25 failure modes.
4. เพิ่ม test เฉพาะ Decision Gate และ Plan Risk Gate.
5. รวม legacy AI Bias Gate กับ Directional Mandate Gate ให้เหลือ gate เดียว อ่านง่ายและลดความซ้ำซ้อน.

