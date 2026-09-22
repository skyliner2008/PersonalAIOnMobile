# ตรวจการแจ้งเตือนระบบปลุก AI บนมือถือจริง (adb) — Runbook

**Links**: [[66_WakeEngine_Live_Backup_V29]] | [[70_WakeTrigger_Architecture_V28]] | [[Trading_Intelligence_MOC]]

> ใช้เมื่อต้องการยืนยันว่าแจ้งเตือนคาดการณ์ล่วงหน้าที่ได้รับ "ค่าถูกต้องไหม" และ "ระบบยังสแกนอยู่ไหม"
> โดยอ่านข้อมูลจริงจากมือถือ แล้วคำนวณใหม่จากแท่งเทียนดิบ — ไม่ต้องเชื่อตัวเลขในการ์ด

---

## 1. วิธีเร็ว — สคริปต์เดียวจบ

ต่อมือถือเข้ากับ PC (เปิด USB debugging และกดอนุญาตบนมือถือ) แล้วรันที่ root ของโปรเจค:

```bash
python tools/device_wake_audit.py --last 5 --logcat
```

| option | ความหมาย |
|---|---|
| `--last N` | จำนวนการปลุกล่าสุดที่แสดง (default 5) |
| `--logcat` | สรุป log ล่าสุดของแอป + ระยะห่างการดึงแท่งเทียน |
| `--keep` | ไม่ลบสำเนาฐานข้อมูลตอนจบ (ปกติลบ เพราะมีประวัติแชทส่วนตัว) |
| `--db path` | ใช้ไฟล์ `jarvis.db` ที่ดึงมาแล้ว ไม่ต้องต่อมือถือ |

สคริปต์ ([`tools/device_wake_audit.py`](../../tools/device_wake_audit.py)) แสดง:
1. **Alert jobs** — tool, เงื่อนไข, active/triggered, เวลาเช็กล่าสุด
2. **การตั้งค่า** — `alert_voice_engine` (device = Android TTS / live = Gemini Live), `alert_ai_summary`, งบการปลุก `wake.*`
3. **การปลุกแต่ละครั้ง** — แท่งที่ปลุก, ปัจจัยที่ปลุก, คำตัดสินของ AI (NOTIFY/SKIP) **พร้อมความมั่นใจและเหตุผล**, การ์ดแชท, ระดับราคาที่ AI ให้
   (จับคู่การ์ดด้วยรหัสปัจจัยที่ปลุก — การปลุกที่ AI ตอบ SKIP ไม่มีการ์ด; ถ้า NOTIFY แต่หาการ์ดไม่เจอ สคริปต์จะเตือน)
4. **ค่าที่คำนวณใหม่จากแท่งเทียนดิบ** ของแท่งนั้น — ATR14, High/Low วันก่อน, high/low 10 และ 20 แท่ง,
   ไส้เทียน, CCI20, MACD histogram, แรงซื้อขายสุทธิ (delta) 10 แท่ง
   — เลือกแหล่งแท่งเทียนและขอบวันเทรดด้วยกติกาเดียวกับแอป (ดูข้อ 3)
5. **ตรวจเงื่อนไขปัจจัยที่ปลุก** — คำนวณสูตรเดียวกับ `triggers/*.kt` ใหม่จากแท่งดิบ แล้วขึ้น ✓ / ✗ พร้อมตัวเลข
   (MACD 3 แบบ, RSI 3 แบบ, CCI, %R, MFI, Stochastic, Bollinger rejection, volume spike/absorption, delta,
   EMA cross/pullback, VWAP reclaim/band, OBV, ยอดคู่/ก้นคู่, inside bar, impulse, range/failed breakout,
   pin bar, doji, NR7, sweep, trend acceleration, PDH/PDL)
   "— ต้องใช้ข้อมูลภายในแอป" = ต้องใช้โครงสร้างตลาด/digest/engine/ข้อมูลภายนอก · "— ยังไม่มีสูตรในสคริปต์" = คำนวณได้แต่ยังไม่ได้ใส่
   (Ichimoku, Squeeze fire, ADX)
   ราคาอ้างอิงใช้ของแถวที่ปลุก AI จริง; "บันทึกเพิ่ม (ไม่ได้ปลุก AI)" = สภาวะใหม่ [STATE] หรือเหตุการณ์ที่เกิดทีหลังในแท่งเดียวกัน [EVENT]
6. **(`--logcat`)** log 30 บรรทัดล่าสุด, ระยะห่างเฉลี่ยของการดึงแท่งเทียน, และจำนวนครั้งที่ TradingView ตอบ error

### ตรวจค่าปัจจัยทั้ง 115 ตัวแบบเล่นซ้ำ (ไม่ใช่แค่ตัวที่ปลุก)

```bash
python tools/wake_factor_replay.py BTCUSDT --bars 300
```
`--step 1m` = เล่นซ้ำทุกนาทีเหมือนมือถือ (ส่วน 3b จำลองความถี่การปลุกตามกติกาจริง)

ดึงแท่ง 1m/5m/15m/1h/4h จากมือถือ → รัน `WakeFactorReplayTest` (โค้ดจริงของแอป, ไฟล์ทำงานอยู่ `composeApp/build/wake_replay/`)
ทีละแท่ง TF หลักด้วยแท่งที่ปิดแล้ว ณ เวลานั้น แล้วเทียบกับสูตรอิสระ: (1) อินดิเคเตอร์ทุก TF ทั้งของปัจจัยและภาพ 5TF
(1b) trend/`ล่าสุด=` ในภาพ 5TF (2) ปัจจัยที่มีสูตรใน `trigger_checks` ทุกแท่ง ทั้งตอนเกิดและไม่เกิด (3) อัตราการเกิดทุกตัว + error
(4) ภาพ 5TF ล่าสุดที่ AI เห็น — ไม่มีข้อมูลภายนอก/memory จึงเกิดไม่ได้: DXY/ข่าว/F&G/Deep score/Harmonic/Elliott/OB_MITIGATED/ALL_TF_ALIGNED

## 2. วิธีทำเอง (ถ้าสคริปต์ใช้ไม่ได้)

adb อยู่ที่ `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (ไม่อยู่ใน PATH ของเครื่องนี้)

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" devices -l                                             # ต้องเห็น R5CT42YEMMM ... device (SM-S908E)
"$ADB" exec-out run-as com.skyliner2008.jarvis cat databases/jarvis.db > jarvis.db   # ดึงฐานข้อมูล (อ่านอย่างเดียว)
"$ADB" logcat -d --pid=$("$ADB" shell pidof com.skyliner2008.jarvis) | grep -E "SmcApiService|AutomationService|WakeEngine"
```

- `run-as` ใช้ได้เฉพาะ **debug build** — มือถือไม่มี `sqlite3` จึงต้องดึงไฟล์มาเปิดบน PC (Python `sqlite3`)
- ⚠️ ถ้า `run-as` ใช้ไม่ได้ (release build / ชื่อแพ็กเกจผิด) มันพิมพ์ error **ลงในไฟล์ที่ได้** และ exit code เป็น 0
  → ต้องตรวจว่าไฟล์ขึ้นต้นด้วย `SQLite format 3` (สคริปต์ตรวจให้แล้ว)
- ฐานข้อมูลใช้ rollback journal (ไม่ใช่ WAL) จึง `cat` ไฟล์เดียวได้ครบ — ถ้าแอปกำลังเขียนอยู่พอดี สำเนาอาจไม่ตรงกัน
  ตรวจด้วย `pragma quick_check` (ต้องได้ `ok`) ถ้าไม่ ok ให้ดึงใหม่
- **ลบไฟล์ `jarvis.db` ที่ดึงมาทิ้งเมื่อเสร็จ** (มีประวัติแชท ความจำ และ API key ใน AppSetting)

### ตาราง/ฟิลด์ที่ใช้

| ตาราง | ใช้ดู |
|---|---|
| `AlertJob` | job (`tool_name = trading_anticipation`, `condition_json`, `is_triggered`, `last_run_at`) |
| `AppSetting` | `alert_voice_engine`, `alert_ai_summary`, `wake.usage` (วัน:จำนวนปลุก), `wake.mem.<SYMBOL@TF>` (memory + สถานะ governor) |
| `AnticipationFactorOutcome` | 1 แถว/ปัจจัย/การปลุก — `signal_id`, `woke`, `ai_decision`, `ai_bias`, `ai_confidence`, `ai_reason`, `ref_price`, `ref_atr`, `status` (PENDING/RESOLVED/EXPIRED) |
| `ChatMessage` | การ์ดที่ส่งเข้าแชท — `metadata` มี `"type":"wake_ai"` หรือ `"wake_direct"`, `side`, `confidence`, `summary`, `mtf` (snapshot 5TF ที่ AI เห็น), `voice` |
| `AnticipationFactorOutcome.factor_tf` | TF ที่ปัจจัยเกิด (migration 16) — ปัจจัยเดียวเกิดได้หลาย TF ในการสแกนเดียว, `signal_id` = `SYMBOL|TF|นาทีที่สแกน` (เดิม = เวลาเปิดแท่ง TF หลัก) |
| `AiWakeView` | มุมมอง AI 1 แถว/การปลุก — `decision`, `bias`, `level_sl/tp`, `status` (OPEN/TP/SL/EXPIRED/CLOSED), `result_r`, `move_atr`, `mfe_atr`, `mae_atr` (migration 15) |
| `TvCandle` | แท่งเทียนดิบ (`symbol`, `interval` = 1m/5m/15m/1h/4h, `source` เช่น TV:OANDA, `ts` = เวลาเปิดแท่ง UTC ms) |

## 3. การตีความ

- เวลาในฐานข้อมูลเป็น **UTC ms** — เวลาไทย = UTC + 7
- `signal_id = SYMBOL|TF|ts` — ตั้งแต่ 2026-09-20 (P16) `ts` = **นาทีที่สแกน** (ปลุกได้ทุกนาที);
  ก่อนหน้านั้น = เวลาเปิดของแท่ง TF หลักที่ปิดล่าสุด — สคริปต์หาแท่ง TF หลักจากเวลาที่ตรวจพบให้เอง
- ปัจจัยที่ปลุกแสดงเป็น `ID@TF(ทิศ)` และถูกตรวจบนแท่งปิดล่าสุดของ **TF ที่มันเกิด** ณ เวลาที่ตรวจพบ
- `created_at` ของแถวการเรียนรู้ = เวลาเริ่มรอบสแกนที่ตรวจพบ — การ์ดเข้าแชทหลังจากนั้นไม่กี่วินาที (+ เวลาที่ AI วิเคราะห์ ~2–3 วิ)
  (ก่อนแก้เรื่องดึงแท่งช้า ห่างกัน 2–3 นาที — ดูข้อ 4)
- **วันก่อนหน้า (PDH/PDL)**: ทอง/เงิน/FX (ทั้งสองฝั่งเป็น USD/EUR/GBP/JPY/CHF/AUD/CAD/NZD) เริ่มวันเทรด 22:00 UTC (= 05:00 ไทย)
  อย่างอื่น (คริปโต) ใช้วัน UTC — ตรงกับ `TaIndicators.sessionOffsetHoursFor`
- **แหล่งแท่งเทียน**: ถ้ามีหลายแหล่ง เลือกตามลำดับ OANDA → FX_IDC → TVC → BINANCE → … แล้วจึงดูจำนวนแท่ง (เหมือนแอป)
- ตาราง `TvCandle` อาจมีซีรีส์ชื่อ interval แบบเก่า (`m15`, `h1`) จากเวอร์ชันก่อน — แอปไม่ใช้แล้ว จะถูกลบเองเมื่อไม่ได้ใช้ครบ 30 วัน
- การ์ดที่ AI ตอบ **SKIP** จะไม่มีในแชท (ไม่รบกวนผู้ใช้) แต่ยังมีแถวใน `AnticipationFactorOutcome`
- `ai_reason` / `ai_confidence` (เพิ่มใน migration 14, schema 15 — 2026-09-19) เก็บเหตุผลและความมั่นใจของ AI ทุกการปลุก
  ทั้ง NOTIFY และ SKIP — การปลุกก่อนหน้านั้นเป็น null (เหตุผลเดิมอยู่แค่ใน logcat ซึ่งถูกเขียนทับภายในไม่กี่ชั่วโมง)
- เสียงแจ้งเตือน: ถ้าคุยกับ Live อยู่ → session นั้นพูด / ไม่ได้คุย → ตาม `alert_voice_engine`
  (log `🔊 speakAlert engine=device` = ตั้งค่าเป็นเสียงเครื่อง ไม่ใช่บัค)

### log ที่ควรเห็นเมื่อระบบปกติ
- `SmcApiService: TV native bridge bars loaded: N for OANDA:XAUUSD/<res>` ตามด้วย `TV delta XAUUSD/<tf>` —
  ห่างกันไม่เกิน ~1 วิ ทั้งรอบสแกนจบใน ~5 วิ และเริ่มรอบใหม่ทุก ~1 นาที
- `TvHistoryBridge: ... protocol_error/symbol_error` = TradingView ปฏิเสธคำสั่ง (ถ้าเห็นบ่อย ให้ตรวจ `TvProtocol`)
- `WakeEngine: ⏸ XAUUSD@15m ตลาดปิดสุดสัปดาห์ ... — ข้ามการสแกน` / `▶ ... ตลาดเปิด — เริ่มสแกน` = log ตอนสถานะตลาดเปลี่ยน
  (ทอง/FX ปิดศุกร์ 17:00 → อาทิตย์ 17:00 FX / 18:00 ทอง เวลานิวยอร์ก, ทองพักทุกวัน 17:00–18:00 NY — ระหว่างปิดไม่มี `TV delta` เลย)
- การปลุกที่ถูกระงับในชั่วโมงสุดท้ายก่อนปิดสิ้นสัปดาห์จะมีแถวการเรียนรู้แต่ไม่มี `ai_decision` (ไม่ได้เรียก AI)
- การ์ดแจ้งเตือนแสดง `ผิดทาง · เป้า · RR 1:x` — ถ้า RR < 1 ขึ้น "⚠️ ต่ำกว่า 1:1" (สคริปต์อ่านจากบรรทัด "ระดับที่ AI จับตา")
- `AiViewTracker: BTCUSDT/15m ปิดผลมุมมอง AI N รายการ` = มุมมองชน TP/SL/หมดเวลา — สคริปต์แสดงเป็นบรรทัด "ผลมุมมอง AI" ใต้แต่ละการปลุก
- `SIGNAL_EVENT ... field=wake ... action=FIRE` ตอนปลุก → `⏰ WAKE ...` → `🧠 AI summary OK` → `🔊 speakAlert` → `💬 pushToChat`
- รอบถัดไป `action=RESET` (job กลับเป็น ACTIVE)
- ถ้าเห็น `No active alert jobs ... Stopping service.` ทั้งที่มี job → job หลุดจากรอบสแกน (เคยเป็นบัค — แก้ใน `9a843c3`)

## 4. ผลการตรวจครั้งแรก (2026-09-18 23:21 และ 23:56 ไทย)

- ค่าในการ์ดทั้ง 2 ใบตรงกับการคำนวณใหม่ทุกตัว (ATR, PDH 4381.065, sweep 4378.715, ไส้บน 61%, CCI 81→131,
  MACD −0.227→+0.504, delta −7,715→+11,980) และแท่ง 23:30 ตรงกับ TradingView สดทุกตัวเลข
- ครั้งที่ 3 (00:01) AI ตอบ SKIP — ไม่แจ้ง ถูกต้อง
- ตรวจไม่ได้: `DEEP_SCORE_CROSS` 30→80 (ต้องใช้ AdvancedTradingEngine ของแอป)
- ✅ **พบ: ดึงแท่งเทียนจาก TradingView ช้า 13–14 วิ/ครั้ง → แก้แล้ว 2026-09-19 (`2a67adb`)**
  - สาเหตุ: native bridge ต่อ string เฟรม `resolve_symbol` เอง เครื่องหมาย `"` ใน JSON ซ้อนไม่ถูก escape
    → TradingView ตอบ `protocol_error` ใน 0.3 วิ แต่ bridge ไม่ฟัง error จึงรอ timeout 12 วิ ก่อนไปเส้นสำรอง
  - แก้: `TvProtocol` (commonMain) สร้างเฟรมด้วย JSON encoder + เลิกรอทันทีเมื่อเจอ error
  - ผลบนเครื่องจริง: **0.5–0.9 วิ/ครั้ง** (เฉลี่ย 0.7 วิ จาก 262 ครั้ง), สแกน 1 รอบ **4–6 วิ** (จาก 1.5–2.5 นาที)

### ผลการตรวจ 2026-09-20 02:55 ไทย (หลังติดตั้ง D1 + กติกาสวนเทรนด์)

- 8 การปลุก BTC ล่าสุด (00:45–02:45) ปัจจัยที่มีสูตร ✓ ทั้งหมด (17 รายการ, ✗ 0); เล่นซ้ำ 16 แท่งล่าสุดด้วย
  `wake_factor_replay.py` ตรงสูตรอิสระทุกค่า (ปัจจัย 30 ตัว, D1–M1, รายละเอียดต่อ TF, PDH/PWH)
- AI ตอบ SKIP ทั้ง 8 ครั้ง — เหตุผลตรงกับภาพตลาดขณะนั้นทุกข้อ (H4/H1/M15 UP, M5/M1 DOWN, H1 อยู่ 91–98% ของกรอบ,
  volume M15 0.2–0.5x) ไม่มี NOTIFY สวนเทรนด์อีก (ก่อนแก้: SELL สวนขาขึ้น 9 ครั้ง)
- ปลุกทุกแท่ง M15 เป็นไปตามออกแบบ (cooldown 3 แท่งต่อปัจจัย, งบ 6/ชม.) — ปัจจัย oscillator/VWAP ยิงสลับทิศเกือบทุกแท่ง
  จึงใช้ ~4 ครั้ง/ชม. ที่ส่วนใหญ่จบด้วย SKIP; ถ้าต้องการประหยัดโทเคน พิจารณาไม่ปลุกเมื่อปัจจัยใหม่ขัดกันเองทั้งหมด
- CONFIDENCE ของ SKIP เป็น 0% เกือบทุกครั้ง (AI ใส่ 0 เมื่อไม่มีทิศ) — ไม่ใช่บัค แต่ใช้ประเมินคุณภาพ SKIP ไม่ได้
- ดึงแท่งเทียนเฉลี่ย 0.8 วิ (n=555)

## 5. ประวัติการแก้สคริปต์
- 2026-09-19: แสดงความมั่นใจ + เหตุผลของ AI ทุกการปลุก (รองรับฐานข้อมูลรุ่นก่อน migration 14),
  ปิด connection ก่อนลบสำเนาฐานข้อมูล (เดิมถ้า error กลางทาง ไฟล์จะค้างบน Windows)
- 2026-09-19 ตรวจทานรอบ 2: ตรวจ header SQLite แทนขนาดไฟล์ (run-as ส่ง error ทาง stdout + exit 0),
  กติกาขอบวันเทรดและการเลือกแหล่งแท่งเทียนให้ตรงกับแอป, จับคู่การ์ดด้วยรหัสปัจจัย (เดิมการปลุกที่ SKIP
  อาจไปหยิบการ์ดของการปลุกถัดไป), log ของ `TvHistoryBridge` + สรุป error, ลบค่าคงที่ที่ไม่ได้ใช้
