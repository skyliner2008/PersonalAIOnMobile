# Wake Engine Live + Backup/Restore V29 (2026-09-18)

**Links**: [[67_Device_Wake_Alert_Audit]] (ตรวจบนมือถือจริง) | [[70_WakeTrigger_Architecture_V28]] | [[69_OHLCV_Indicator_Foundation_V27]] | [[Trading_Intelligence_MOC]] | [[../03_Tools/catalogue]]

> ต่อจาก V28 (สร้าง engine + ปัจจัย ~115 ตัว) — รอบนี้ **ต่อสายเข้าระบบแจ้งเตือนจริงบนมือถือ**,
> เพิ่มการดูแลขนาด OHLCV store และระบบ **สำรอง/กู้คืน** (การเรียนรู้ + ฐานข้อมูลทั้งหมด, ไฟล์ + Google Drive)

---

## 1. ระบบปลุก AI แยกจาก Signal Alert โดยสิ้นเชิง

| | Signal Alert | ระบบปลุก AI (คาดการณ์ล่วงหน้า) |
|---|---|---|
| tool_name ของ alert job | `trading_signal_alert` | `trading_anticipation` |
| field | `signal_buy_id` / `signal_sell_id` | `wake` (edge = `wake_id` = เวลาแท่ง) |
| ใครตัดสิน | กลยุทธ์ (Unified SMC ฯลฯ) + Supervisor APPROVE/VETO/ADJUST | **AI ตัดสินเอง** NOTIFY / SKIP จาก snapshot 5TF |
| Entry/SL/TP | กลยุทธ์คำนวณ | ไม่มี — AI ระบุระดับที่จับตาเอง (LEVEL_SL/LEVEL_TP) |
| สถิติ | `SignalAlertRecord` (TP/SL) | `AnticipationFactorOutcome` (forward R ต่อปัจจัย ต่อ context) |

- migration 13.sqm ย้าย alert job คาดการณ์เดิม (`trading_signal_alert` + `signal_anticipation*`) → `trading_anticipation` / `wake`
- `AlertFieldCatalog.ANTICIPATION` (field: `wake`, `wake_id`, `wake_event_count`, `wake_buy`, `wake_sell`, `close`)
  และลบ field `signal_anticipation*` ออกจาก `SIGNAL_ALERT`
- `JarvisOrchestrator` (automation_manage_alerts): ถ้าขอ anticipation ด้วยชื่อเก่า → แปลงเป็น `trading_anticipation`/`wake` อัตโนมัติ, TF default 15m

## 2. Flow บนมือถือ

```
TradingAlertEvaluator.checkJob
  └─ trading_anticipation → AnticipationEngine.scan(symbol@tf)
       (5TF + intermarket + macro + F&G + deep score + patterns → learning record → governor)
  └─ edge-trigger: wake_id ใหม่ > ค่าล่าสุด → fire  (isEdgeTriggered = signal_alert | anticipation)
JarvisAutomationService.fireJobAlert
  └─ handleWakeAlert
       direct : notification + การ์ดรายการเหตุการณ์ (ไม่ใช้โทเคน)
       ai     : runWakeAnalysis → WakePrompt.build → generateAiText (Flash Lite, stateless)
                → WakePrompt.parse → WakeLearningStore.setAiDecision(signalId, decision, bias)
                SKIP  → ไม่รบกวนผู้ใช้ (แต่บันทึกไว้วัดว่า AI ตัดสินถูกไหม)
                NOTIFY→ notification + การ์ด + เสียงพูด summary ที่ AI เขียน (liveSummary=false)
```

**ทำไมไม่ให้ Live สรุป**: Live คิดโทเคนแบบ compounding ทั้ง context ทุก turn และ TPM 65K
การวิเคราะห์ใช้ generateContent แบบ stateless (TPM 250K/โมเดล) — Live ใช้แค่ "พูด" ข้อความสั้นที่ได้

**การ์ดแชท** ใช้ `kind: "anticipation"` เดิมของ `MessageBubble`:
side = bias ของ AI (NEUTRAL → สี cyan + ป้าย "⏰ เหตุการณ์"), zone = รหัสปัจจัย, desc = เหตุการณ์ (ตัดสถิติ/รหัสที่มีไว้ให้ AI อ่าน)

## 3. OHLCV store — ขนาดและการลบ

ตอบคำถาม "หลายสินทรัพย์จะทำให้ฐานข้อมูลใหญ่ไหม":
- 1 แท่ง ≈ 170 ไบต์ (แถว + UNIQUE index) × เพดาน 6,000 แท่ง ≈ **1 MB ต่อซีรีส์**
- 1 สินทรัพย์ที่เฝ้า 5TF + intermarket (DXY/US10Y หรือ BTC/BTC.D) ≈ **6–8 MB**
- ไม่ลบเลย 20 สินทรัพย์ ≈ 150 MB → จึงเพิ่ม `OhlcvMaintenance`

`OhlcvMaintenance` (commonMain):
- `touch()` — `preferredDbSeries` บันทึกเวลาอ่านลง `TvSeriesAccess` (throttle ชม.ละครั้งต่อซีรีส์)
- `prune()` — ลบ **ทั้งซีรีส์** ที่ไม่ถูกอ่านเกิน 30 วัน และไม่ถูกใช้โดย alert ที่เปิดอยู่
  (`protectedSymbolsFor` รวมตลาดที่เกี่ยวข้องของระบบปลุกด้วย) — ไม่ตัดกลางซีรีส์ เพราะประวัติขาดช่วงทำให้อินดิเคเตอร์ผิด
- ลบประวัติการเรียนรู้เก่ากว่า 365 วัน
- `runIfDue()` เรียกจาก automation loop วันละครั้ง (`AppSetting ohlcv.maintenance.last_run`)
- ถ้ากลับมาดูสินทรัพย์ที่ถูกลบ ระบบ backfill ใหม่อัตโนมัติ

## 4. สำรอง / กู้คืน

### 4.1 การเรียนรู้ (`backup/LearningTransfer.kt`, commonMain)
- ไฟล์ JSON `format = jarvis-wake-learning`, `version = 1` — outcome ที่วัดผลแล้ว + การตั้งค่า (ปัจจัยที่ปิด, งบการปลุก)
- ไม่ส่งออกแถว PENDING (ต้องใช้แท่งเทียนเครื่องต้นทางวัดผล)
- นำเข้าแบบ **merge** (`INSERT OR IGNORE` บน UNIQUE(signal_id, factor_id)) → นำเข้าซ้ำได้ รวมหลายเครื่องได้
- ปฏิเสธไฟล์ต่างรูปแบบ / เวอร์ชันใหม่กว่า, ทนต่อ field ที่ไม่รู้จัก

### 4.2 ฐานข้อมูลทั้งหมด (`backup/DatabaseBackupManager.kt`, androidMain)
- zip = `jarvis.db` + `manifest.json` (schema version, เวอร์ชันแอป, อุปกรณ์, จำนวนแถวตารางหลัก)
- snapshot: API 30+ ใช้ `VACUUM INTO` (consistent ระหว่างแอปทำงาน), รุ่นเก่า checkpoint WAL แล้วคัดลอก
- กู้คืน 2 ขั้น:
  1. `stageRestore` — รับ zip หรือ .db ดิบ → ตรวจ header SQLite, `PRAGMA quick_check`, มีตาราง AppSetting/AlertJob,
     `user_version ≤ Schema.version` (ไฟล์จากแอปใหม่กว่าถูกปฏิเสธ) → วางเป็น `files/pending_restore.db`
  2. `applyPendingRestore` — เรียก **ก่อนเปิดฐานข้อมูลทุกจุด** (DatabaseDriverFactory, JarvisAutomationService, AlertActionReceiver)
     สลับไฟล์ ลบ -wal/-shm, เก็บของเดิมเป็น `jarvis.db.pre-restore` → แล้ว `restartApp()`
- ไฟล์จากเวอร์ชันเก่ากว่า: AndroidSqliteDriver migrate อัตโนมัติตอนเปิด

### 4.3 Google Drive
ใช้ Storage Access Framework (`CreateDocument` / `OpenDocument`) — Google Drive ปรากฏในหน้าต่างเลือกไฟล์ของระบบเมื่อติดตั้งแอป Drive
**ไม่ต้องตั้ง OAuth / Google Cloud project** (การเรียก Drive REST API ตรงต้องมี OAuth client + consent screen — ไม่คุ้มสำหรับแอปส่วนตัว)

### 4.4 UI
Settings → **สำรอง / กู้คืนข้อมูล**: ขนาดฐานข้อมูล/OHLCV/การเรียนรู้, ส่งออก/นำเข้าการเรียนรู้, สำรอง/กู้คืนฐานข้อมูล
(ยืนยัน 2 ชั้น: ก่อนเลือกไฟล์ และก่อนรีสตาร์ท), ปุ่มลบซีรีส์ OHLCV ที่ไม่ได้ใช้

## 5. ไฟล์ที่เกี่ยวข้อง

| ไฟล์ | บทบาท |
|---|---|
| `androidMain/.../service/TradingAlertEvaluator.kt` | dispatch `trading_anticipation`, `isEdgeTriggered`, `runWakeAnalysis` |
| `androidMain/.../service/JarvisAutomationService.kt` | `handleWakeAlert`, `OhlcvMaintenance.runIfDue` |
| `androidMain/.../service/AlertPresentationFormatter.kt` | `wakeChatMeta`, `buildWakeChatCard`, `buildWakeNotification`, `buildWakeSpeech` |
| `commonMain/.../tools/trading/OhlcvMaintenance.kt` | touch / stats / prune |
| `commonMain/.../backup/LearningTransfer.kt` | export/import การเรียนรู้ |
| `androidMain/.../backup/DatabaseBackupManager.kt` | backup/restore ฐานข้อมูล |
| `commonMain/.../ui/BackupController.kt` + `.android.kt` / `.ios.kt` | ปุ่มและ file picker |
| `commonMain/.../ui/screen/SettingsDialog.kt` | `BackupSection` |

ลบแล้ว: `runAnticipationSupervisor`, `buildAnticipationChatCard`, `anticipationChatMeta`, `buildAnticipationSpeech`,
`AnticipationConfigManager` (+ test)

## 6. Tests
- `WakeTriggerRegistryTest` (เขียนใหม่ตาม API ใหม่ + ครอบคลุม ≥100 ปัจจัย, scan ไม่ throw ทุกรูปแบบข้อมูล)
- `WakeSystemTest` — cooldown, 1 ปลุก/แท่ง, งบรายชั่วโมง, forward R หน่วย ATR, context bucket, prompt ไม่มีแผนเทรดสำเร็จรูป, parse คำตอบ AI
- `SignalAnticipationTest` — แยก tool จาก signal alert, action create/config/scan, การ์ดแชท
- `BackupAndMaintenanceTest` — ไฟล์การเรียนรู้ round-trip/ปฏิเสธไฟล์ผิด, เลือกซีรีส์ที่จะลบ, ป้องกัน intermarket
- ผล: `:composeApp:testDebugUnitTest` **433/433** ✅

## 7. ยังไม่ได้ทำ / ข้อจำกัด
- iOS: `rememberBackupController` เป็น stub (ซ่อนส่วนสำรอง)
- ยังไม่มีสำรองอัตโนมัติตามเวลา (ต้องกดเอง) — ถ้าต้องการสามารถใช้ `ACTION_OPEN_DOCUMENT_TREE` เก็บสิทธิ์โฟลเดอร์ Drive แล้วสำรองรายวันได้
- ยังไม่ได้ทดสอบบนเครื่องจริง: การกู้คืน + รีสตาร์ท และการเลือก Google Drive ในหน้าต่างไฟล์

## 8. Review รอบ 2 — บัคที่แก้ (2026-09-18)

### ทำให้ AI ไม่ถูกปลุก / ถูกปลุกผิด
| บัค | ผล | แก้ |
|---|---|---|
| สแกนจากแชท (`action=scan`) ใช้ cooldown + "การปลุกของแท่งนั้น" + บันทึกการเรียนรู้ร่วมกับ alert เบื้องหลัง | สแกนดูครั้งเดียว alert ของแท่งนั้นเงียบ | `scan(preview = true)` — ดูอย่างเดียว ไม่แตะ governor/การเรียนรู้/memory |
| `wake_event_count` / `wake_buy` / `wake_sell` นับเหตุการณ์ที่ติดงบด้วย | alert แบบนับเหตุการณ์ยิงทุกแท่งและเรียก AI เกินงบ | นับเฉพาะรอบที่ปลุกจริง + `handleWakeAlert` ข้ามถ้า `wake != 1` |
| governor เก็บในหน่วยความจำอย่างเดียว | รีสตาร์ทบริการ = งบรายวันกลับเป็นศูนย์ และแท่งที่เพิ่งปลุกถูกปลุกซ้ำ | cooldown + แท่งที่ปลุกล่าสุดเก็บใน memory ของ series, ยอดรายวันเก็บใน AppSetting |
| job @30m ถูกประเมินบน M15 เงียบๆ | การ์ดบอก 30M แต่ข้อมูลเป็น 15M | รองรับ 30m เป็น TF หลักจริง (`WakeContext.extra`) |
| job คาดการณ์รุ่นเก่าที่ใช้ field อื่น (เช่น `signal_anticipation_side`) | เงียบถาวร | แปลงเป็น `wake >= 1` อัตโนมัติ |
| job แบบ edge-trigger ไม่อัปเดต `last_run_at` | สแกนทุก 30 วิแทนทุก 1 นาที | `touchAlertJobRun` |
| สแกนหลายที่พร้อมกัน (alert + แชท) แก้สถานะร่วมโดยไม่ล็อก | สถานะ/แคชชนกัน | `scanLock` (Mutex) |

### ทำให้การเรียนรู้ผิด
- **ราคาอ้างอิงเก่า**: ใช้ราคาปิดของแท่ง TF หลักที่ปิดไปแล้ว แต่เหตุการณ์ M1/M5/ข่าวเกิดกลางแท่ง (บน H4 เก่าได้หลายชั่วโมง)
  → ใช้ราคา ณ ตอนตรวจพบ + วัดด้วยแท่งที่ปิดหลังเวลาตรวจพบ (`forwardWindow`)
- **แถวค้าง PENDING ตลอดไป** เมื่อแท่งในมือไม่ครอบคลุมจุดเริ่มวัด (แอปปิดนาน) — แถมถูกวัดด้วยแท่งผิดช่วง
  และดึงทีละ 500 แถวจากเก่าสุด แถวค้างจึงบังแถวใหม่ → ปิดเป็น `EXPIRED`, รอผลเกิน 30 วันก็ EXPIRED
- **ปัจจัยที่ยิงได้ทั้งมีทิศและไม่มีทิศ** (เช่น EQ_POOL_SWEPT): ขนาดการเคลื่อนที่ (บวกเสมอ) ของแถว NEUTRAL ปนใน avg R → แยกสะสม
- **ความแม่นของ AI วัดผิดทิศ**: ใช้ forward_r ตามทิศของ "ปัจจัย" ไม่ใช่ของ AI และนับซ้ำทุกแถวของการปลุกเดียวกัน
  → query ใหม่วัดตามทิศที่ AI มอง ต่อ 1 การปลุก (แถว NEUTRAL เก็บการเคลื่อนที่แบบมีเครื่องหมายแล้ว)

### ปัจจัย / ภาพตลาด
- **Digest ตัดระดับราคาซ้ำด้วย `(price*10).toInt()`** → คู่เงิน FX (1.0850 / 1.0890) ถูกรวมเป็นระดับเดียว → รวมด้วยระยะ 0.1×ATR
- Digest สมมติแท่งสุดท้ายยังก่อตัว (n-2) — ตลาดปิดสุดสัปดาห์ภาพช้าไป 1 แท่ง → `closedAware`
- Digest ของระบบปลุกไม่ใส่บรรทัด "อ้างอิงโครงสร้าง BUY → SL/TP" แล้ว (ไม่ป้อนแผนเทรดให้ AI ก่อนวิเคราะห์)
- PDH/PDL/Pivot/PWH/PWL: ชั่วโมงแรกของวันได้ระดับของ "เมื่อวานซืน" → อ้างอิงวันจากเวลาปัจจุบัน
- ราคาเปิดวัน/สัปดาห์/เดือน: ต้นวันยังไม่มีแท่ง H1 ปิดจึงหาไม่เจอ → ใช้แท่ง H1 + TF หลักรวมกัน
- เส้นแนวโน้ม/สามเหลี่ยม/channel/neckline ลากด้วย index ของแท่ง "ยืนยัน" swing (ช้ากว่าจุดยอด 5 แท่ง)
  → เส้นเลื่อนขวา จุดหลุด/ทะลุคลาดเคลื่อน → `swingHighPivots` / `swingLowPivots`
- CORRELATION_BREAK เทียบ 21 แท่งท้ายของทองกับ DXY ตรงๆ โดยไม่จับคู่เวลา → จับคู่ตาม timestamp
- DXY_MOVE / YIELD_SPIKE / CRYPTO_BROAD_MOVE: ข้อมูล intermarket ที่ค้าง (feed ล่าช้า) นับเป็นเหตุการณ์ → ต้องเป็นแท่งที่เพิ่งปิด
- POST_NEWS_SPIKE: แท่ง H1/H4 ที่ปิด "ก่อน" ข่าวถูกนับเป็นผลของข่าว → ต้องปิดหลังข่าว
- SESSION_OPEN / KILLZONE ใช้เวลา UTC ฤดูร้อนตายตัว → เวลาท้องถิ่น London/New York (รองรับ DST) และข้ามเสาร์-อาทิตย์
- WEEK_BOUNDARY: FX เปิดคืนวันอาทิตย์ ~21:00 UTC ไม่ใช่เช้าวันจันทร์
- ป้าย TF: เหตุการณ์หลาย TF / ข่าว ขึ้น `[H1]` ผิด → `[MTF]` / `[ข่าว]`
- รูปแบบราคา: คริปโตราคาต่ำ (0.00002) แสดงเป็น 0.0000 → `formatPrice`

### โทเคน
- การวิเคราะห์ของระบบปลุกเคยส่ง **system prompt แชทของ JARVIS ทั้งชุด** (ประมาณ 14,000 ตัวอักษร มีกฎ tool/กราฟ/อุปกรณ์)
  ทุกครั้งที่ปลุก → ใช้ `WakePrompt.SYSTEM_PROMPT` ย่อ (< 600 ตัวอักษร) ผ่าน `GeminiService.systemPromptOverride`
- timeout ของการวิเคราะห์ 8 → 20 วินาที (prompt ใหญ่กว่าสรุปเสียง และไม่เร่งเท่า)
- เคารพสวิตช์ "AI สรุปการแจ้งเตือน" — ปิดอยู่ = แจ้งเป็นรายการเหตุการณ์โดยไม่เรียก AI
- ระดับราคาที่ AI ให้แต่ผิดตรรกะ (BUY แต่ SL เหนือราคา / เลขผิดหลัก) ถูกตัดทิ้ง (`WakePrompt.sanitize`)

### ยังไม่ได้แก้ (ต้องเปลี่ยน schema หรือเป็นข้อจำกัดที่ยอมรับได้)
- UNIQUE(signal_id, factor_id): ถ้าปัจจัยเดียวกันยิงทั้ง BUY และ SELL ในแท่ง TF หลักเดียวกัน แถวที่สองถูกทิ้ง
- งบรายวันนับตามวัน UTC ส่วนโควตา Gemini รีเซ็ตเที่ยงคืนเวลาแปซิฟิก
- งบรายชั่วโมงต่อสินทรัพย์ยังอยู่ในหน่วยความจำ (รีสตาร์ทแล้วเริ่มนับใหม่)

ผล: `:composeApp:testDebugUnitTest` **444/444** ✅ (เพิ่มเทสต์ preview, governor หลังรีสตาร์ท, หน้าต่างวัดผล,
sanitize, ป้าย TF, รูปแบบราคา, swing pivot, 30m, PDH ต้นวัน)
