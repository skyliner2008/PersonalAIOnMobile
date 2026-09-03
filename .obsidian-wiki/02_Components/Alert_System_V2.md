# ระบบ Alert V2 — เงื่อนไข แจ้งเตือน และสถานะ (2026-08-04)

บันทึกการอัปเกรดระบบเฝ้าดูตลาด (AlertJob / Automation) ตามผลทดสอบจริง (XAU 12:09–12:28)

## 1. Notification แบบกดได้ (แทน msg box)

Android ไม่อนุญาตให้ background service เด้ง dialog ลอยได้จริง — จึงใช้ **notification พร้อม action buttons** แทน:

- 🛑 **หยุดแจ้งเตือน** → `automationManager.setJobActive(id, false)` (is_active = 0, หายจากรายการ ACTIVE)
- 🔁 **แจ้งเตือนซ้ำ** → `automationManager.resetTrigger(id)` → รอบเช็คถัดไปประเมินเงื่อนไขใหม่และแจ้งซ้ำได้

Implementation: `JarvisAutomationService.sendJobAlertNotification()` + `alertActionReceiver` (BroadcastReceiver, register ใน `onCreate`, `RECEIVER_NOT_EXPORTED` บน API 33+)

## 2. สถานะ ACTIVE / TRIGGERED (Repeat mode)

พฤติกรรมปัจจุบัน (ตั้งใจไว้): โหมด Repeat — เข้าเงื่อนไข → แจ้ง → `is_triggered = 1` → เมื่อราคาหลุดเงื่อนไข → reset อัตโนมัติ → เข้าเงื่อนไขอีกครั้ง → แจ้งใหม่

การแสดงผลในหน้า Automation:
- `ACTIVE · เฝ้าดูอยู่` (เขียว) — กำลังเฝ้าดู
- `TRIGGERED · แจ้งแล้ว` (แดง) — แจ้งไปแล้ว แต่ job ยัง active และจะแจ้งซ้ำเมื่อเข้าเงื่อนไขใหม่

หมายเหตุ: `TRIGGERED` ไม่ใช่สถานะจบ — งานยังทำงานต่อ (Repeat) จนกว่าผู้ใช้จะกด 🛑 หรือลบ

## 3. แก้ไข Alert ได้

- แก้ได้: **ค่าเปรียบเทียบ** (เช่น ราคาเป้าหมาย) และ **ความถี่ (นาที)** ผ่าน `AlertEditDialog`
- แก้ไม่ได้: field/operator (ลบแล้วสร้างใหม่แทน)
- แก้ค่าแล้วระบบรีเซ็ต `is_triggered = 0, last_value = NULL` ให้เริ่มเฝ้าดูใหม่ทันที
- SQLDelight query ใหม่: `updateAlertJobCondition` (ไม่มี schema change, ไม่ต้อง migration)
- AI แก้ให้ได้ผ่าน `automation_manage_alerts` action `update` (alert_id + condition_value)

## 4. AlertFieldCatalog — เงื่อนไขที่ทำได้จริง (single source of truth)

ไฟล์: `automation/AutomationModels.kt` → `AlertFieldCatalog`

ใช้ทั้งใน UI (dropdown ตอนกด "สร้าง Alert") และเป็นรายการอ้างอิงให้ AI (`automation_manage_alerts` description + validation ใน `JarvisOrchestrator.onManageAlerts` — ถ้า AI ตั้ง field มั่วจะตอบกลับ ❌ พร้อมรายการที่รองรับ)

| tool_name | field ที่ใช้ได้ |
|---|---|
| trading_indicators ⭐ (แนะนำ — คำนวณเองจากแท่งเทียน 300 แท่ง, เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m) | close, ema20, ema50, ema200, ema_cross_state (==: GOLDEN_CROSS/DEATH_CROSS/BULLISH/BEARISH), ema50_200_spread, ema20_50_spread, rsi14, macd, macd_signal, macd_hist, stoch_k, stoch_d, cci20, bb_upper, bb_basis, bb_lower, bb_width, atr14 |
| trading_smc ⭐ (Smart Money Concepts — symbol@TF ได้เช่นกัน) | close, smc_zone (==: PREMIUM/DISCOUNT/EQUILIBRIUM), smc_zone_pct, smc_trend (==), smc_last_event (==: BOS_UP/BOS_DOWN/CHOCH_UP/CHOCH_DOWN), smc_structure_high/low, smc_equilibrium, smc_premium_bot, smc_discount_top, bull_ob_dist, bear_ob_dist, fvg_dist, liq_above_dist, liq_below_dist, liq_above_stars, liq_below_stars, attack_force (0/1), atr |
| trading_price | price, change, change_pct, prev_close, high_52w, low_52w, direction (==) |
| trading_technical_analysis (1h, scanner — field จำกัด) | close, RSI, MACD.macd, MACD.signal, BB.basis, ATR, ADX, Recommend.All, recommend_score, signal (==), volume |
| trading_deep_analysis_suite (1h) | summaryScore, fiboScore, lsdState, deltaLabel, momentum |
| trading_sentiment | sentiment_score, bullish_posts, bearish_posts, posts_analyzed, sentiment_label (==) |
| trading_fear_greed | value (0-100), classification (==) |
| trading_crypto_overview | btc_dominance, eth_dominance, market_cap_change_24h, total_market_cap_usd, total_volume_24h_usd, active_cryptocurrencies, markets |

⚠️ ~~EMA Cross ยังทำไม่ได้~~ → **ทำได้แล้วตั้งแต่ V2.4** ผ่าน `trading_indicators` (field `ema_cross_state` / `ema50_200_spread`) — คำนวณจากแท่งเทียนเอง ไม่ต้องพึ่ง scanner
⚠️ Reddit sentiment โดนบล็อก (HTTP 403) ช่วงนี้ — หลีกเลี่ยง tool trading_sentiment

ตัวอย่าง preset ใน UI: RSI >= 70 (Overbought) / RSI <= 30 (Oversold) / Fear&Greed <= 20 / summaryScore >= 85

## 5. Adaptive Interval + Async AI (2026-08-04 รอบ 2)

**ปัญหาที่พบจาก log จริง:** ตอน trigger ระบบเรียก Gemini สรุปแบบ serial ใน loop เดียวกัน → job ถัดไปถูกเช็คช้าไป ~10 วินาที

**การแก้ไข:**
- `fireJobAlert()` ถูกย้ายไป `scope.launch` ขนาน — mark trigger synchronous ก่อนเสมอกันยิงซ้ำ แล้วค่อยเรียก AI แบบไม่บล็อก loop
- **Adaptive Interval** (`effectiveIntervalMs`): tick หลักของ loop ลดเหลือ 30 วินาที (อ่าน SQLite อย่างเดียว ถูกมาก) และปรับความถี่เช็คตามระยะห่างจากเป้า (เฉพาะเงื่อนไขตัวเลข GT/LT/GTE/LTE):
  - ห่างเป้า < 0.1% → เช็คทุก 30 วินาที
  - ห่างเป้า < 0.5% → เช็คทุก ≤ 1 นาที
  - ไกลกว่านั้น → ใช้ interval ที่ผู้ใช้ตั้ง (ไม่เคยช้ากว่าที่ตั้ง)
- ออกแบบสำหรับทอง (XAUUSD) ที่วิ่ง $10-100 ต่อ 15 นาที — เมื่อราคาเข้าใกล้เป้า ระบบเร่งเช็คถี่ขึ้นเองโดยไม่เปลืองตลอดวัน

## 6. AlertActionReceiver (manifest-declared)

เดิม receiver ของปุ่ม notification เป็น **dynamic receiver** ที่ผูกกับ service — เมื่อ service/แอปถูกฆ่า broadcast จะไม่ถูกส่ง (ปุ่มกดแล้วไม่มีอะไรเกิดขึ้น)

แก้โดยสร้าง `service/AlertActionReceiver.kt` ประกาศใน AndroidManifest (`exported=false` + intent-filter 2 actions) และเปลี่ยน PendingIntent เป็น **explicit intent** — ระบบจะปลุก receiver ขึ้นมาทำงานเองแม้แอปไม่ได้รันอยู่ โดยเปิด `jarvis.db` อัปเดตสถานะ job โดยตรง (หยุด → is_active=0, แจ้งซ้ำ → reset trigger)

⚠️ ต้อง build & install APK ใหม่ก่อนปุ่มถึงจะขึ้น (code เดิมในเครื่องยังเป็นเวอร์ชันเก่า)
`n`n## 7. Signal Alert Market-Data Source Policy (2026-08-25)`n`n- `TradingSignalMarketDataRouter` เป็น source-of-truth สำหรับแท่งเทียนของ `trading_signal_alert`.`n- DEMO/PAPER → TradingView เท่านั้น; LIVE + MT5 runtime connected → MT5 candles; LIVE + MT5 offline/unavailable → TradingView.`n- ห้าม fallback จาก TradingView กลับ MT5 ภายใน pipeline เดียวกันเมื่อ MT5 ถูกตัดสินว่า offline.`n- Signal payload บันทึก `signal_data_source` และ `signal_data_source_reason` เพื่อ audit provenance.`n- Live Tool Guard ป้องกัน `trading_mt5_symbol_search` ถูกเรียกก่อนสร้าง Signal Alert; symbol discovery ของ MT5 ใช้เฉพาะคำขอเรื่อง broker symbol โดยตรง.`n- Live tool-response logs เพิ่ม `callId` เพื่อแยก native tool calls และตรวจสอบ response mismatch ได้ชัดเจน.


## 8. Signal Alert Multi-Timeframe Hardening (2026-08-25)

- `automation_manage_alerts` รองรับ `timeframe=all` สำหรับ Signal Alert.
- `all` ครอบคลุม 1m, 5m, 15m, 30m, 1h, 4h และ 1D โดย map TF เข้า `XAUUSD@TF` เพื่อให้ `SignalAlertProvider` วิเคราะห์แยก timeframe จริง.
- ถ้าไม่ระบุ timeframe ยังคง default เป็น 1h; ถ้าระบุ TF เดียวจะเฝ้าเฉพาะ TF นั้น.
- เพิ่ม idempotent/dedup check ป้องกันสร้าง alert ซ้ำใน symbol/tool/field เดียวกัน.
- Persona ถูก harden ให้ตีความ “ทุก timeframe” เป็น `timeframe=all` ไม่ใช่เพียงสร้าง Buy/Sell สองฝั่ง.
- Android debug compile ผ่านหลัง patch.

## 9. Signal Alert Check Interval Baseline (2026-08-25)

- ค่าเริ่มต้นของ `automation_manage_alerts.interval_minutes` สำหรับ Signal Alert เปลี่ยนจาก 15 → **1 นาที** เพื่อให้การตรวจสอบสัญญาณใกล้ real-time มากขึ้น.
- ขอบเขตที่ผู้ใช้ระบุเองยังคงใช้ได้ 1–1440 นาที; การระบุ `interval_minutes` จะ override ค่า default.
- การเปลี่ยนนี้มีผลกับ **alert ที่สร้างใหม่**; alert เดิมไม่ถูกแก้ไขอัตโนมัติเพื่อป้องกันการเปลี่ยนพฤติกรรมของงานที่ผู้ใช้สร้างไว้แล้วโดยไม่ตั้งใจ.
- การตรวจสอบทุก 1 นาทีไม่ได้หมายความว่าทุก timeframe จะมีแท่งใหม่ทุกนาที; provider ยังคงเคารพ bucket/candle freshness และ incremental refresh ของแต่ละ TF.

### Follow-up: Per-Signal Re-arm & Final Log Noise Hardening (2026-08-25)

- `trading_signal_alert` ใช้ `signal_buy_id` / `signal_sell_id` เป็น dedup key ต่อ signal candle.
- `signal_id` เดิม → `SUPPRESS_ALREADY_TRIGGERED`; `signal_id` ใหม่ → `FIRE` ได้ 1 ครั้ง แม้ `is_triggered=1` จาก signal ก่อนหน้า.
- `last_value` ของ signal alert เก็บ `last_triggered_signal_id` เพื่อไม่ให้ polling sample เขียนทับ dedup state.
- `AutomationService` ไม่ log `Checking job:` และไม่ log action=`WAIT` ใน normal polling; เก็บเฉพาะ transition/action ที่มีความหมาย.
- `SmcApiService` ไม่ log TradingView packet lifecycle (`series_loading`, `symbol_resolved`, `timescale_update`, `TV bars loaded`) ใน normal debug; คงเฉพาะ `TV new candles ...` และ event ที่จำเป็นต่อการตรวจ error.

## 10. Signal Alert Observability & Log Noise Hardening (2026-08-25)

- `AutomationService` log เพิ่ม `triggered` และ `action` เพื่อแยก `FIRE`, `SUPPRESS_ALREADY_TRIGGERED`, `WAIT`, `RESET` ได้ทันทีจาก logcat.
- `SignalAlertProvider`/`TradingSignalMarketDataRouter` ส่ง provenance reason แบบ explicit: `DEMO_FORCES_TV`, `MT5_LIVE_CONNECTED`, `MT5_LIVE_OFFLINE`, `MT5_CANDLE_UNAVAILABLE`.
- `SmcApiService` ไม่ log cache-fresh / `missing=0` / skip / no-new-bars ใน normal cycle แล้ว; log ปกติจะแสดงเฉพาะเมื่อมีแท่งใหม่ถูกบันทึกจริง (`TV new candles ...`).
- `SignalAlertProvider` dedup เฉพาะ log ของ signal เดิมบน closed-bar เดิม; `NEW signal` จะ log เมื่อ `signal_bar_time` เปลี่ยน และแนบ `source/reason` เพื่อ audit provenance.
- ไม่เปลี่ยน signal strategy; ปรับ trigger semantics เฉพาะ `trading_signal_alert` ให้ dedup ด้วย `signal_id` เพื่อให้ signal candle ใหม่สามารถ FIRE ได้ 1 ครั้ง แม้ job เดิมยัง `is_triggered=1`.
- `last_value` ของ `trading_signal_alert` สงวนเป็น `last_triggered_signal_id`; ห้าม `recordJobCheckResult()` เขียนทับด้วยค่า boolean/sample.
- `SmcApiService` ปิด routine TradingView packet lifecycle logs (`series_loading`, `symbol_resolved`, `timescale_update`, `TV bars loaded`) และ `AutomationService` ปิด `Checking job:`/`WAIT` polling noise; คงเฉพาะ event สำคัญ เช่น NEW signal, Gate, FIRE/SUPPRESS/RESET, AI, Live และ Chat.
- Build รอบนี้ยังไม่ได้ยืนยันจาก Gradle tool ใน session; ต้อง build APK จริงก่อนติดตั้งเพื่อทดสอบ runtime semantics ใหม่.


## 12. Strategy Optimization V2 (2026-08-26)

- `trading_backtest_optimize` ใช้ `StrategyOptimizationV2` เป็น discovery path หลัก
- Optimize **EntryParams + strategy-native TpSlParams ร่วมกัน** เพื่อวัด edge ของ strategy ทั้งระบบ ไม่ใช่ optimize SL/TP บนจุดเข้าแบบคงที่
- Validation แบ่งเป็น Train 60% → Validation 20% → Holdout OOS 20% และใช้ prefix candles เพื่อรักษา indicator/ATR warm-up โดยไม่ให้ข้อมูลอนาคตรั่วเข้าไป
- Candidate ต้องมี positive expectancy, PF ≥ 1 และ DD อยู่ใน hard limit ก่อนผ่านแต่ละ stage
- เพิ่ม `Parameter Neighborhood Stability`: ตรวจ candidate รอบ parameter point เพื่อแยก robust basin ออกจาก parameter cliff/single-point optimum
- ผล V2 เป็น **discovery-only**; ไม่เขียน `StrategyTuning`/`EntryTuning` production และต้องผ่าน permutation + Monte Carlo + Robustness/Risk Gate ก่อน promote
- Strategy-specific grids: MOM/TR/REV/DC/52H/E/UT; 3BR เป็น pattern strategy จึงไม่มี entry parameter grid

## 11. Signal Alert Snapshot Consistency (2026-08-25)
- `JarvisAutomationService` now caches `trading_signal_alert` data per polling cycle by `symbol@TF`.
- BUY/SELL jobs for the same timeframe therefore evaluate one coherent closed-candle snapshot instead of fetching TradingView twice.
- This removes duplicate strategy-gate/SignalAlert computation while preserving signal-id deduplication and next-cycle refresh.


## 2026-08-26 — Live Alert Scheduler
- แทนการให้แต่ละ Signal Alert รอ `liveVoiceMutex` สูงสุด 90s ด้วย `Live Alert Scheduler` กลาง
- Scheduler serialize เฉพาะ physical audio output; signal detection, AI summary และ notification ยังทำงานขนานได้
- ใช้ priority queue: M1=100, M5=90, M15=80, M30=70, TF ใหญ่กว่า=60, งานทั่วไป=10 เพื่อให้ signal ที่เร่งด่วนแซง scheduled announcement ที่รอคิว
- Live session ไม่ทำ queue timeout/fallback ระหว่างรออีกต่อไป; fallback Android TTS เกิดจาก Live chain ล้มจริงหรือ service ถูกยกเลิก
- เพิ่ม log `Live Scheduler ENQUEUE` / `DISPATCH` พร้อม `priority`, `tf`, `queue`, `remaining`, `seq` สำหรับวัด latency และ queue contention

## 2026-08-25 — Gate diagnostic per-candle dedup
- `SignalAlertProvider` ยังคง evaluate Strategy Gate ทุก automation cycle เพื่อให้ BLOCK/WEAK state เป็นปัจจุบัน
- ปรับเฉพาะ observability: `Gate WEAK/BLOCK` และ tuned-parameter diagnostics จะ log สูงสุดครั้งเดียวต่อ closed candle (`symbol/tf/kind/bar`)
- `NEW signal` dedup เดิมยังคงทำงานตาม `signal bar timestamp` และไม่เปลี่ยน signal/automation semantics
- เป้าหมาย: ลด M15 log spam โดยไม่ลดความถี่ในการตรวจ Gate หรือทำให้ risk/signal decision stale


## 12. Signal Alert Reliability Hardening (2026-08-25)

- เพิ่ม throttle ระดับ `symbol + timeframe + side` 90 วินาที แยกจาก `job.is_triggered`/`signal_id` เพื่อกันสัญญาณใหม่จากคนละ strategy ยิงถี่เกินไป
- เพิ่ม alert AI mutex ให้ Gemini summary ทำงานแบบคิว ลด burst/429 และ concurrent summary
- Alert Gemini ใช้ timeout 8 วินาที, ปิด long retry 45 วินาที และจำกัด fallback เหลือ 4 models เพื่อรักษา latency
- Live voice ยังคง serialized ด้วย `liveVoiceMutex`
- log ใหม่ `SIGNAL THROTTLE` และ `AI summary queued` ใช้ตรวจสอบ suppression/queue ได้โดยตรง

## 12. Live Alert Voice Timeout / Queue Hardening (2026-08-25)
- `liveVoiceMutex` มี queue timeout 4 วินาที; หากมี session ค้างจะไม่บล็อก Signal Alert ต่อ แต่ fallback เป็น Android TTS พร้อม `APP_QUEUE_BUSY`.
- Live one-shot session จำกัด hard timeout 25 วินาที เพื่อกัน WebSocket ที่ไม่ปิดหลัง watchdog ทำให้ chain ค้างเป็นสิบวินาที.
- เพิ่ม diagnostic phase/timestamps: `SETUP`, `READY`, `REQUEST_SENT`, `AUDIO_STREAMING`, `TRANSCRIPT_NO_AUDIO`, `TURN_COMPLETE` และ `ws close reason`.
- แยกสาเหตุ timeout ใน log เป็น `API_OR_NETWORK_BEFORE_SETUP`, `API_AUDIO_OUTPUT_STALL`, `API_GENERATION_OR_NETWORK_STALL`, `APP_OR_API_TURN_CLOSE_STALL` หรือ `API_ERROR` เพื่อแยกปัญหา API/network กับ app queue/close lifecycle.
- รักษา first-audio watchdog 8 วินาทีและ AudioTrack drain เดิม; ไม่ตัดคำตอบหลังได้ audio chunk แรกโดยไม่จำเป็น.
