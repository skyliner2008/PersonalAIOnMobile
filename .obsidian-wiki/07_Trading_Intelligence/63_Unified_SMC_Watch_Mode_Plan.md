# Unified SMC — WATCH Mode Production Plan (V5 Basin)

> สถานะ: **แผนพร้อม + Python watcher ใช้งานได้แล้ว** — เหลือเพิ่ม endpoint ฝั่ง server (รอ maintenance window เพราะ server อาจกำลัง live)
> Basin config: `adx_th=18, entry=limit(FVG mid), idm=on, tp_r=3.0, setup_win=4, score_th=0.65, regime=trend_only, swing_L=5, session=off`
> Backtest ผ่าน 3/5 gates (ตกที่ sample size + permutation) → **WATCH เท่านั้น ห้ามเทรดจริงจนกว่า forward sample ≥ 30 ไม้และผ่าน gates**

## เป้าหมาย

สะสม forward evidence ของ Unified SMC V5 basin โดย:

1. สร้าง signal จากแท่ง M15 ที่ปิดแล้วเท่านั้น (closed-bar, no look-ahead)
2. log ทุก decision (BUY/SELL/HOLD + regime + entry/SL/TP) ลงระบบจริง
3. วัด shadow P/L เทียบราคาตลาดจริง (limit fill ถูก track ว่า fill ไหม)
4. ห้ามแตะ order path เด็ดขาด (`was_executed=0` เสมอ)

## สถาปัตยกรรม

```
MT5 Terminal
   │  (bridge :5001, /candles — ไม่ต้อง auth)
   ▼
tools/unified_smc_watch.py        ← ทำแล้ว ✅
   │  poll ทุก M15 bar close (ครอน/Automation)
   │  refresh strategy_lab_data_mt5 → rebuild features → score basin params
   │  decision JSON → strategy_lab_output/watch_log.jsonl (local truth)
   ▼  --post (x-client-token)
POST /api/mt5/auto/external-signal   ← ต้องเพิ่ม (code ด้านล่าง)
   ▼
auto_trading_decision_feed (decision_type='PAPER', was_executed=0, strategy='UNIFIED_SMC_PY')
auto_trading_journal (outcome='OPEN', shadow tracking)
   ▼
App อ่าน decision-feed เดิม (AutoTradingRemoteService) → เห็น signal โดยไม่แก้ app
```

## ชิ้นที่ทำแล้ว ✅

### `tools/unified_smc_watch.py`

- `--poll [bridge_url]` — ดึงแท่งใหม่จาก bridge (`GET /candles?symbol=XAUUSD&timeframe=M15&count=5000`) append ลง CSV cache และลบ feature cache เพื่อ rebuild
- offline default — คำนวณจาก CSV ล่าสุด, print decision JSON, append `watch_log.jsonl`
- `--post server_url --token XXX` — POST ไปยัง endpoint เมื่อพร้อม
- decision JSON มี: direction, regime (ADX/vol percentile/ทิศ H4·H1·M30), entry type (limit_fvg_mid / market_next_open), entry/SL/TP, RR, fill_valid_bars
- ทดสอบแล้ว 2026-08-27: ตอบ HOLD (H1 trend −1 ขัด M30 +1) — confluence conflict → HOLD ตามดีไซน์

### ตั้งรันทุก 15 นาที

ครอนบนเครื่อง (หรือ Blueprint Automation local_conversation) ให้รันหลัง M15 ปิดแท่ง ~30 วิ:

```text
python tools/unified_smc_watch.py --poll --post http://127.0.0.1:8090 --token <CLIENT_TOKEN>
```

## ชิ้นที่ต้องเพิ่มฝั่ง mt5-core-server (ยังไม่ได้แกะ — รอ window)

### `POST /api/mt5/auto/external-signal`

เพิ่ม route ใน `src/routes/autoTrading.ts` (ผ่าน `requireClientToken` เหมือน route อื่น):

```ts
// WATCH-mode ingestion for external (Python) research signals.
// Writes decision_feed + journal only; NEVER touches order paths.
router.post("/external-signal", requireClientToken, (req, res) => {
  const b = req.body ?? {};
  if (!["BUY", "SELL", "HOLD"].includes(b.direction)) {
    return res.status(400).json({ success: false, error: "direction must be BUY|SELL|HOLD" });
  }
  const id = `EXT-${Date.now()}`;
  insertDecisionFeed({                      // ใช้ PersistenceService helper เดิม
    decision_id: id,
    decision_type: "PAPER",
    strategy: b.strategy ?? "UNIFIED_SMC_PY",
    side: b.direction,
    entry: b.entry ?? null, sl: b.sl ?? null, tp: b.tp ?? null,
    confluence: JSON.stringify(b.regime ?? {}),
    rationale: `external WATCH signal ${b.signal_bar ?? ""}`,
    was_executed: 0,
  });
  if (b.direction !== "HOLD") {
    insertJournal({ decision_id: id, outcome: "OPEN", was_executed: 0, /* shadow fields */ });
  }
  res.json({ success: true, decision_id: id });
});
```

หมายเหตุ implementer: ใช้ helper insert ของ `PersistenceService.ts` (บรรทัด ~440 decision_feed, ~538 journal) — อย่างเขียน SQL เองถ้า helper รองรับ

### Shadow P/L resolver (เล็ก)

service ตัวเล็ก (หรือ hook ใน cycle loop เดิม) ที่:

1. อ่าน journal rows ที่ `outcome='OPEN' AND was_executed=0 AND strategy='UNIFIED_SMC_PY'`
2. สำหรับ limit entry: เช็กว่าราคาแตะ entry ภายใน `fill_valid_bars` ไหม (ไม่ fill → outcome='EXPIRED')
3. ถ้า fill: track SL/TP เทียบ candle ถัดไป → อัปเดต outcome=WIN/LOSS/BE + profit_r
4. ใช้ `mt5_candle_cache` หรือ bridge `/candles` เป็นแหล่งราคา

## Gates สำหรับ promote จาก WATCH → LIVE

| Gate | เกณฑ์ |
|---|---|
| Forward sample | ≥ 30 shadow trades ที่ fill จริง |
| Forward expectancy | > 0 R หลัง cost (spread จริงจาก journal) |
| Consistency | ไม่ขัด backtest basin (PF forward ≥ 1.3) |
| Fill quality | limit fill rate ≥ 50% (ถ้าต่ำกว่า = entry model คลาดจากสมมติฐาน) |
| เวลา | ≥ 4 สัปดาห์ forward |

## ความเสี่ยง / ข้อจำกัด

- **ห้าม** ให้ watch runner ยิงตอน bridge กำลังหนัก (bridge serialize mt5.* อยู่แล้ว แต่อย่าเพิ่มโหลดช่วงข่าวแรง)
- CSV cache เป็น source of truth ของ watcher — ถ้า bridge ล่ม signal จะ stale; runner log stale ไว้ใน JSON (`bar_time`)
- decision JSON ไม่มี position sizing — โดยเจตนา (watch-only)
- ถ้าจะใช้ M1 entry timing จริงในอนาคต ต้องรัน poll ถี่ขึ้นและ M1 history จาก MT5 (cap 100k แท่ง ≈ 3.3 เดือน) จะค่อยๆ สะสมยาวขึ้นเอง
