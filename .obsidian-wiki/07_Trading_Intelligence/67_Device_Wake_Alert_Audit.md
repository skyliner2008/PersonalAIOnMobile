# ตรวจการแจ้งเตือนระบบปลุก AI บนมือถือจริง (adb) — Runbook

**Links**: [[66_WakeEngine_Live_Backup_V29]] | [[58_WakeTrigger_Architecture_V28]] | [[Trading_Intelligence_MOC]]

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
3. **การปลุกแต่ละครั้ง** — แท่งที่ปลุก, ปัจจัยที่ปลุก, คำตัดสินของ AI (NOTIFY/SKIP), การ์ดแชท, ระดับราคาที่ AI ให้
4. **ค่าที่คำนวณใหม่จากแท่งเทียนดิบ** ของแท่งนั้น — ATR14, High/Low วันก่อน, high/low 10 และ 20 แท่ง,
   ไส้เทียน, CCI20, MACD histogram, แรงซื้อขายสุทธิ (delta) 10 แท่ง

## 2. วิธีทำเอง (ถ้าสคริปต์ใช้ไม่ได้)

adb อยู่ที่ `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (ไม่อยู่ใน PATH ของเครื่องนี้)

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" devices -l                                             # ต้องเห็น R5CT42YEMMM ... device (SM-S908E)
"$ADB" exec-out run-as com.skyliner2008.jarvis cat databases/jarvis.db > jarvis.db   # ดึงฐานข้อมูล (อ่านอย่างเดียว)
"$ADB" logcat -d --pid=$("$ADB" shell pidof com.skyliner2008.jarvis) | grep -E "SmcApiService|AutomationService|WakeEngine"
```

- `run-as` ใช้ได้เฉพาะ **debug build** — มือถือไม่มี `sqlite3` จึงต้องดึงไฟล์มาเปิดบน PC (Python `sqlite3`)
- ฐานข้อมูลเป็น rollback journal (ไม่ใช่ WAL) — ไฟล์ที่ `cat` ออกมาสมบูรณ์ ตรวจด้วย `pragma quick_check`
- **ลบไฟล์ `jarvis.db` ที่ดึงมาทิ้งเมื่อเสร็จ** (มีประวัติแชท ความจำ และ API key ใน AppSetting)

### ตาราง/ฟิลด์ที่ใช้

| ตาราง | ใช้ดู |
|---|---|
| `AlertJob` | job (`tool_name = trading_anticipation`, `condition_json`, `is_triggered`, `last_run_at`) |
| `AppSetting` | `alert_voice_engine`, `alert_ai_summary`, `wake.usage` (วัน:จำนวนปลุก), `wake.mem.<SYMBOL@TF>` (memory + สถานะ governor) |
| `AnticipationFactorOutcome` | 1 แถว/ปัจจัย/การปลุก — `signal_id`, `woke`, `ai_decision`, `ai_bias`, `ref_price`, `ref_atr`, `status` (PENDING/RESOLVED/EXPIRED) |
| `ChatMessage` | การ์ดที่ส่งเข้าแชท — `metadata` มี `"type":"wake_ai"` หรือ `"wake_direct"`, `side`, `confidence`, `summary`, `mtf` (snapshot 5TF ที่ AI เห็น), `voice` |
| `TvCandle` | แท่งเทียนดิบ (`symbol`, `interval` = 1m/5m/15m/1h/4h, `source` เช่น TV:OANDA, `ts` = เวลาเปิดแท่ง UTC ms) |

## 3. การตีความ

- เวลาในฐานข้อมูลเป็น **UTC ms** — เวลาไทย = UTC + 7
- `signal_id = SYMBOL|TF|ts` — `ts` คือเวลาเปิดของแท่ง TF หลักที่ **ปิดล่าสุด** ตอนปลุก (เช่น ปลุก 23:21 ไทย → แท่ง M15 23:00)
- `created_at` ของแถวการเรียนรู้ = เวลาเริ่มรอบสแกนที่ตรวจพบ (ก่อนการ์ดจะเข้าแชทราว 2–3 นาที ถ้าการดึงแท่งยังช้า — ดูข้อ 4)
- **วันก่อนหน้า (PDH/PDL)** ของทอง/FX เริ่ม 22:00 UTC (= 05:00 ไทย) — คริปโตใช้วัน UTC
- การ์ดที่ AI ตอบ **SKIP** จะไม่มีในแชท (ไม่รบกวนผู้ใช้) แต่ยังมีแถวใน `AnticipationFactorOutcome`
- เสียงแจ้งเตือน: ถ้าคุยกับ Live อยู่ → session นั้นพูด / ไม่ได้คุย → ตาม `alert_voice_engine`
  (log `🔊 speakAlert engine=device` = ตั้งค่าเป็นเสียงเครื่อง ไม่ใช่บัค)

### log ที่ควรเห็นเมื่อระบบปกติ
- `SmcApiService: TV delta XAUUSD/<tf>` ทุกรอบสแกน (~1 นาที)
- `SIGNAL_EVENT ... field=wake ... action=FIRE` ตอนปลุก → `⏰ WAKE ...` → `🧠 AI summary OK` → `🔊 speakAlert` → `💬 pushToChat`
- รอบถัดไป `action=RESET` (job กลับเป็น ACTIVE)
- ถ้าเห็น `No active alert jobs ... Stopping service.` ทั้งที่มี job → job หลุดจากรอบสแกน (เคยเป็นบัค — แก้ใน `9a843c3`)

## 4. ผลการตรวจครั้งแรก (2026-09-18 23:21 และ 23:56 ไทย)

- ค่าในการ์ดทั้ง 2 ใบตรงกับการคำนวณใหม่ทุกตัว (ATR, PDH 4381.065, sweep 4378.715, ไส้บน 61%, CCI 81→131,
  MACD −0.227→+0.504, delta −7,715→+11,980) และแท่ง 23:30 ตรงกับ TradingView สดทุกตัวเลข
- ครั้งที่ 3 (00:01) AI ตอบ SKIP — ไม่แจ้ง ถูกต้อง
- ตรวจไม่ได้: `DEEP_SCORE_CROSS` 30→80 (ต้องใช้ AdvancedTradingEngine ของแอป)
- ⚠️ **การดึงแท่งเทียนจาก TradingView ใช้ ~13–14 วิ/ครั้ง** (ปกติ < 1 วิ — ทดสอบจาก PC ด้วยโปรโตคอลและ header เดียวกัน)
  สแกน 1 รอบจึงใช้ 1–2 นาที แจ้งเตือนช้าราว 2 นาที — สงสัย `fetchTvHistoryBars` (native bridge, timeout 12 วิ)
  ไม่เคยได้ข้อมูลแล้วค่อยไปเส้นสำรอง (log "TV native bridge bars loaded" ไม่เคยขึ้น) ยังไม่ได้แก้
  สคริปต์ `--logcat` แสดงค่าเฉลี่ยระยะห่างไว้ติดตาม
  - ✅ **แก้แล้ว (2026-09-19)**: สาเหตุคือ native bridge ต่อ string เฟรม `resolve_symbol` เอง
    เครื่องหมาย `"` ใน JSON ซ้อนไม่ถูก escape → TradingView ตอบ `protocol_error` ใน 0.3 วิ แต่ bridge ไม่ฟัง error จึงรอ timeout 12 วิ
    แก้ด้วย `TvProtocol` (commonMain) สร้างเฟรมด้วย JSON encoder + เลิกรอทันทีเมื่อเจอ error
    ผลบนเครื่องจริง: **0.5–0.9 วิ/ครั้ง** (จาก 13–14 วิ), สแกน 1 รอบ **4–6 วิ** (จาก 1.5–2.5 นาที)
