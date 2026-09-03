# 64 — Unified SMC V5: ฝังใน App มือถือ (แทน Strategy เดิม)

**วันที่:** 2026-08-27 · **สถานะ:** ใช้งานใน app แล้ว (research/forward-test — ห้ามถือว่าพิสูจน์ edge แล้ว)

## สรุป

ยุบรวม strategy ทั้งหมดใน app มือถือ: ตัด **TR / DC / 52H / E / UT / 3BR** ออกจาก live pipeline
(cross-TF forensics บน XAUUSD/MT5 พบว่าไม่มีตัวไหนบวกครบ 3 phase ยกเว้น MOM/REV — ดู
[[Strategy_Lab_TradingView]]) เก็บ **MOM + REV** ไว้ และเพิ่ม **Unified SMC Multi-TF V5**
เป็น engine หลักตัวเดียวที่รวมทุก TF เป็น setup เดียว แทนการยิง signal แยก TF ที่ขัดแย้งกัน

## ไฟล์ที่เปลี่ยน

| ไฟล์ | การเปลี่ยน |
|---|---|
| `automation/smc/UnifiedSmcSignals.kt` | **ใหม่** — port 1:1 จาก `tools/unified_smc_lab.py` (V5 basin params เป็น consts) |
| `automation/SignalAlertProvider.kt` | เรียก `UnifiedSmcSignals.evaluate()` บน **ทุก job ไม่ว่า TF ไหน** (base timeline = M15 เสมอ; เดิม lock เฉพาะ job 15m ทำให้เงียบสนิทเพราะผู้ใช้มีแต่ job 1m/1h — พบจาก logcat 2026-08-27 19:07); unified เป็น primary signal; kind `UNIFIED_SMC`; signal id/bar_time ผูกกับเวลาแท่ง M15; gate เดิมใช้ได้ (UNKNOWN=ผ่าน); `signal_entry` ใช้ราคา limit/market ของ unified; fetchStats default = ENABLED_KINDS |
| `automation/SignalMarkerProvider.kt` | `ENABLED_KINDS = setOf("MOM","REV")` กรองท้าย `compute()`; เพิ่ม `kindsOverride` สำหรับ forensics/backtest ย้อนหลัง |
| `tools/trading/TradingToolExecutionBackend.kt` | default lists → MOM/REV (+SMC ใน backtest); default mix → MOM+REV; ส่ง kindsOverride ให้ forensics ตาม kind ที่ระบุ |
| `automation/backtest/EntryParams.kt` | `TUNABLE_KINDS = MOM, REV` |
| `automation/backtest/StrategyForensics.kt` | ส่ง kindsOverride ครบ 8 kinds (forensics ต้องวัดทุกตัว) |
| `automation/AutomationModels.kt`, `tools/trading/TradingToolDefinitions.kt`, `automation/backtest/BacktestEngine.kt` | อัปเดต label/description ให้ตรง pipeline ใหม่ |

## Unified SMC ใน app — mapping จาก Python

- **TF stack:** H4 (resample จาก H1) + H1 = context · M30 (resample จาก M15) + H1 = trend ·
  M15 = setup · M5 = confirmation · entry = LIMIT @ FVG mid (fallback MARKET @ open แท่งถัดไป)
- **Regime gate:** trend_only — H1 ADX≥18 และ H1 trend≠0; CHAOTIC (ATR pct >0.90 หรือ <0.10) = HOLD
- **Hard rule:** ไม่มี setup (zone tap + displacement ใน 4 แท่ง) = HOLD เสมอ
- **Edge trigger:** ยิงเฉพาะแท่งปิดที่ "เพิ่งเข้าเงื่อนไข" (แท่งก่อนไม่เข้า)
- **Heartbeat log (2026-08-27 รอบ 2):** `evaluate()` คืน `EvalResult(signal, reason)` — HOLD ทุกกรณีมีเหตุผลไทย
  (ข้อมูลไม่พอ / regime=CHAOTIC / regime≠TREND+ADX / ไม่มี setup / score ไม่ผ่าน / ไม่ใช่ edge ใหม่ / risk_dist นอกกรอบ)
  และ `SignalAlertProvider` log ครั้งเดียวต่อแท่ง M15 ปิด: `UNIFIED_SMC HOLD: <reason> m15bar=<ts>`
  — ทำให้พิสูจน์จาก logcat ได้ว่า evaluator มีชีวิต (แก้ปัญหา "HOLD เงียบ แยกไม่ออกว่ารันอยู่จริง")
- **2026-08-27 รอบ 3:** เปลี่ยนจาก "เฉพาะ job 15m" เป็น "ทุก job" — logcat รอบ 19:07 พบว่าผู้ใช้มีแต่ job
  1m/1h ไม่มี 15m เลย ทำให้ engine หลักไม่เคยถูกเรียก; M15 fetch ใช้ candle DB cache ต้นทุนต่ำ
- **Causality:** แท่ง HTF ใช้ได้เมื่อปิดแล้วเท่านั้น (as-of: open + duration ≤ t)
- **จุดต่างจาก research ที่รับทราบ:** H4/M30 bin ชนเที่ยงคืน UTC (research ใช้ start_day ของ timezone ไฟล์);
  rolling-rank percentile ใช้ method=max; ประเมินจาก window สด (M15 300 / H1 500 / M5 500 แท่ง) แทน history เต็ม
- **ดึงข้อมูลผ่าน** `TradingSignalMarketDataRouter` → MT5 เมื่อ LIVE+connected ไม่งั้น TradingView (policy เดิม)

## สถานะความเชื่อมั่น (สำคัญ)

- V5 basin ผ่าน **3/5 gates**: holdout +0.49R (PF 1.73, n=16), WFO 3/4, adverse-cost บวก
- **ตก 2 gates:** holdout n<30 และ permutation p=0.38 → `promotion_ready=false`
- ดังนั้นใน app ถือเป็น **forward-test เท่านั้น** — alert pipeline ไม่ส่งออเดอร์เองอยู่แล้ว;
  เก็บ shadow P/L ผ่าน `SignalAlertRecord` + `trackSignalOutcomes()` (ทุก 30 วิ) เพื่อประเมินจริงต่อ

## วิธี re-enable strategy ที่ถูกตัด

แก้ `SignalMarkerProvider.ENABLED_KINDS` จุดเดียว — โค้ดทุก strategy ยังอยู่ครบ และ tools
backtest/forensics วัดตัวที่ถูกตัดได้ตลอดผ่าน `kindsOverride`

## Build

`./gradlew :composeApp:compileDebugKotlinAndroid` — BUILD SUCCESSFUL (2026-08-27, ทั้ง 2 รอบ: หลัง port และหลังเพิ่ม heartbeat)

## วิธีตรวจจาก logcat

```
SignalAlert: XAUUSD/15m UNIFIED_SMC HOLD: regime≠TREND (H1 ADX 12.30 < 18 หรือ H1 trend=0) bar=...
SignalAlert: XAUUSD/15m UNIFIED_SMC BUY score=0.80 entry=... (LIMIT_FVG_MID) SL=... TP=... bar=...
```

เห็น HOLD ทุกแท่ง = evaluator มีชีวิตปกติ (engine นี้เลือกไม้มากโดยดีไซน์) · ไม่เห็นเลยทั้งที่มี job 15m = ผิดปกติ ให้เช็ก `UnifiedSMC error:`
