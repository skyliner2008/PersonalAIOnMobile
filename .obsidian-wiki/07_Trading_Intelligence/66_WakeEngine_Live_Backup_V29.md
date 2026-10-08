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

## 9. ตรวจ flow เชิงลึก (2026-10-03)

ผลการคำนวณ: ดู [[67_Device_Wake_Alert_Audit]] (เล่นซ้ำแท่ง BTC จริง 50 ชม. ตรงสูตรอิสระทั้งหมด)

**แก้แล้ว**
- `TradingAlertEvaluator.generateAiText`: โมเดลหลักล้มครั้งเดียว (เช่น 429) แล้วโมเดลสำรองตอบได้ → เดิมเขียนทับ `model_name`
  ซึ่งเป็นโมเดลที่ทั้งแอปใช้คุย (ผู้ใช้ถูกเปลี่ยนโมเดลแชทถาวรโดยไม่รู้ตัว) → ตอนนี้จำไว้ใช้เฉพาะงานแจ้งเตือน 1 ชม. (`alertFallback`)
  แล้วลองโมเดลของผู้ใช้ใหม่
- alert แบบ `trading_technical_analysis` @1h เคยประเมินด้วยค่ารายวัน (TradingView scanner — ดู [[../03_Tools/catalogue]]
  "อัปเดต 2026-10-03") → ตอนนี้เป็นค่า 1h จริง: alert เดิมที่ตั้งไว้อาจยิงต่างจากเดิม

- **AI วิเคราะห์ไม่ได้ → แจ้งแบบเงียบ** (ผู้ใช้อนุมัติ 2026-10-03, `WakeAiFallback`): เดิม `handleWakeAlert` แจ้งเต็ม (notification +
  การ์ด + เสียง) ทุกการปลุกเมื่อ AI ไม่ตอบ — ตอนนี้ (ทั้ง Live worker และโมเดลแชทไม่ตอบ) ส่งรายการเหตุการณ์**ไม่มีเสียง** notification
  แบบ silent ไม่เกิน 1 ครั้ง/30 นาที/สินทรัพย์ ครั้งแรกบอก "ตอนนี้ AI วิเคราะห์การปลุกไม่ได้" ครั้งเดียวจน AI ตอบได้อีก · log
  `⏰ AI วิเคราะห์ไม่ได้ → แจ้งรายการเหตุการณ์แบบเงียบ` / `— ไม่แจ้ง … (แจ้งแบบเงียบไปแล้วเมื่อ N นาทีก่อน)` · โหมด direct ที่ผู้ใช้เลือกเองไม่เปลี่ยน
- การวิเคราะห์ของระบบปลุกย้ายไป Live worker (`THINKING`: 3.8-live-extended-thinking ก่อน — ไม่จำกัด RPD) → โมเดลแชทเป็นทางสำรอง
- งบ 40/ชม., 3,000/วัน: ผู้ใช้มี 7 บัญชีแยก project (ช่วงพัฒนาใส่ key เดียว) + วิเคราะห์ด้วย Live แล้ว → งบคุมแค่ความถี่การแจ้ง

- **ตลาดปิดตอนตั้งงาน** (2026-10-04: ผู้ใช้ตั้งงานทองคืนวันเสาร์ หน้าแจ้งเตือนค้าง "รอเช็คครั้งแรก"): engine ข้ามการสแกนถูกต้อง แต่ (1) หน้า
  Automation ไม่อ่านฐานข้อมูลใหม่ (`AutomationManager.activeJobs` เป็น snapshot — service เขียน `last_run_at` ตรงลงฐานข้อมูล) → อ่านใหม่ทุก
  15 วิขณะเปิดหน้า (`onRefresh`) (2) การ์ดงานปลุกขึ้น "⏸ ตลาดปิดสุดสัปดาห์ — เริ่มสแกนเมื่อเปิด จ. 05:00 น." (`marketClosedNote`)
  (3) คำตอบตอนสร้างงานบอกว่าตลาดปิดและเริ่มสแกนเมื่อไหร่
- เสียงแจ้งเตือนที่ session Live พูดขึ้นต้นด้วยชื่อสินทรัพย์ (เดิม BTC ถูกอ่านว่า "ทองคำ")
- **การปลุกไม่มีเสียงเพราะคำพูดถ่วงเวลาของ AI** (2026-10-04 02:10 ผู้ใช้: "มีการปลุก แต่ไม่มีเสียง"): Live worker
  3.8-live-extended-thinking ตอบ "กำลังวิเคราะห์ข้อมูลกราฟครับ" → `WakePrompt.parse` ไม่ได้ → ทางแจ้งเงียบ "AI ใช้ไม่ได้" โดยไม่ลองโมเดลอื่น
  → `runWakeAnalysis` ส่ง `accept = { WakePrompt.parse(it) != null }` ให้ `generateAiText`: คำตอบที่อ่านไม่ได้ = โมเดลถัดไป (worker แล้วแชท)
  ทางแจ้งเงียบเหลือเฉพาะตอนไม่มีโมเดลไหนตอบเป็นรูปแบบได้เลย ([[Model_Policy_Free_Tier]] "Live workers")
- **SL ต้องห่างราคาอย่างน้อย 1 ATR** (2026-10-08, ผู้ใช้: "ปรับปรุงได้" — หลังพบ BUY BTCUSDT 2026-10-04 11:04 @84,844 ชน SL
  ใน 6 นาทีหลังราคาลงแค่ 36 (ATR M15 ~50) แล้ววิ่งขึ้น +214): (1) prompt บอกกติกาพร้อมค่า ATR14 ของ TF หลัก (2) ตาข่าย
  `WakePrompt.enforceStopDistance` (หลัง `sanitize` ก่อน `enforceTrend`): NOTIFY BUY/SELL ที่ SL ห่างไม่ถึง `MIN_SL_ATR` = 1 ATR →
  ขยาย SL เป็น 1 ATR แล้วคิด RR กับ TP เดิม · ≥ 1:1 แจ้งต่อด้วย SL ใหม่ (ต่อท้ายเหตุผล + SUMMARY_TH ที่เสียงอ่าน "ระบบปรับจุดผิดทาง…")
  · < 1:1 → SKIP "ระบบไม่แจ้ง: SL … ห่างราคาแค่ x ATR — ขยายเป็น 1 ATR … RR เหลือ 1:y" · ไม่มี TP → ขยายอย่างเดียว · log
  `⏰ SL BUY … ห่างไม่ถึง 1 ATR → ขยายเป็น …` · `AiWakeView` เก็บ SL ที่ขยายแล้ว (ผลที่ติดตาม = สิ่งที่ผู้ใช้ได้รับจริง) · สรุปผล
  (`summary`) นับ SKIP นี้เป็นหมวด "ระบบตัดเพราะ SL แคบกว่า 1 ATR" แยกจากหมวดสวนเทรนด์ · ต่างจาก `sanitize` ที่ไม่แก้ระดับแทน AI:
  ระยะ SL ขั้นต่ำเป็นกติกาความเสี่ยงของระบบเหมือน `MIN_RR`
- **ถามผลการคาดการณ์ด้วยเสียงได้ทุกโหมด Live** (2026-10-04, ผู้ใช้: "โหมด live ทุกโหมด สามารถสั่งให้ตรวจสอบผลของคาดการณ์
  ล่วงหน้าได้หรือไม่ เช่น มีแจ้งเตือนกี่ครั้งแล้ว, skip กี่ครั้ง, skip เพราะอะไร, คาดการณ์ buy/sell กี่ครั้ง, เพราะเหตุผลอะไร"):
  ทุกโหมดเรียก `trading_signal_anticipation` ได้อยู่แล้ว แต่ไม่มี action ที่ตอบคำถามนี้ (`status` นับแค่การปลุก, `learning` เป็นตาราง
  รายปัจจัย) → action `summary` ([[../03_Tools/catalogue]]) อ่าน `AiWakeView` ตามช่วงเวลา/สินทรัพย์ · คำอธิบาย tool + กฎ persona
  บอกให้ใช้ · ค่าเริ่มต้นงบใน tool definition แก้เป็นของจริง (40/ชม., 3000/วัน — เดิมเขียน 6 และ 900)
- **ถอดสายชาร์จแล้วระบบปลุกหยุด** (2026-10-04, ผู้ใช้: "ยังไม่มีการแจ้งเตือนอะไรใหม่" + log 04:34–08:32): ถอดสาย 07:30:24 →
  สแกนครั้งสุดท้าย 07:36 → ตื่นเป็นพักๆ 08:00/08:11 (DNS ล้ม) 08:18/08:21/08:28 → เสียบสาย 08:30:36 แล้วกลับมาทุกนาที · แอปอยู่ในรายการ
  ยกเว้น Battery Optimization และ standby bucket EXEMPTED แล้ว แต่ `JarvisAutomationService` ไม่ถือ wake lock — ลูป `delay()` หยุดเมื่อ CPU
  หลับ → ถือ `PARTIAL_WAKE_LOCK "jarvis:market_watch"` ขณะมี alert job (ผู้ใช้เลือกแลกแบต) ต่ออายุทุกรอบ = เวลารอของรอบ + 5 นาที
  (ลูปตายแล้วปล่อยเอง) · คืนเมื่อไม่มี job / service หยุด · log `🔋 CPU wake lock on/off`
  - ช่วงนั้นที่ไม่มีแจ้งเตือนเป็นเพราะตลาด: BTC 04:30–08:45 วิ่งในกรอบ 0.21% (Binance) — ปลุก 61 ครั้ง วิเคราะห์ด้วย 3.8-live ทุกครั้ง
    2.3–5 วิ ตอบ SKIP ทั้งหมด ("กรอบใหญ่ขาขึ้น กรอบเล็กขาลง ตลาดเงียบ") · ปลุกถี่ ~17 ครั้ง/ชม. จากปัจจัย M1 (SPREAD_WIDENING,
    EQ_POOL_SWEPT) ตามงบช่วงเรียนรู้ (40/ชม., เว้น 1 นาที)
- **อัปเดตแอปแล้วระบบปลุกหยุด**: การติดตั้งทับฆ่า `JarvisAutomationService` และเดิมมันกลับมาเฉพาะหลังรีบูทหรือเมื่อผู้ใช้เปิดแอป →
  `BootReceiver` รับ `MY_PACKAGE_REPLACED` ด้วย (ได้รับยกเว้นให้ start foreground service จากเบื้องหลังเหมือน `BOOT_COMPLETED`) —
  ยืนยันบนเครื่อง: log `BootReceiver: MY_PACKAGE_REPLACED — restarting JarvisAutomationService` แล้ว `▶ BTCUSDT@15m ตลาดเปิด — เริ่มสแกน`
  ภายใน 30 วิหลังติดตั้ง

**ยังไม่ได้แก้**
- NOTIFY ที่ BUY/SELL แต่ระดับราคาถูก `sanitize` ตัดทิ้งหมด ยังแจ้งต่อโดยไม่มีระดับ
