---
title: Avatar Mode — หุ่น Godot เป็น Engine เดียวของแอป
category: Components
tags: [avatar, godot, pet, sensors, android, ipc]
sources:
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/avatar/
  - composeApp/src/androidMain/kotlin/com/skyliner2008/jarvis/avatar/
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/screen/AvatarModeScreen.kt
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/RobotSprite.kt
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/GazeNeutral.kt
  - composeApp/src/androidMain/kotlin/com/skyliner2008/jarvis/pet/PetVisionDetector.kt
  - pet_godot/scripts/app_bridge.gd
  - pet_godot/tools/export_to_app.ps1
created: 2026-10-01
updated: 2026-10-01
---

# Avatar Mode (เดิม "โหมดสัตว์เลี้ยง")

หุ่นยนต์ 3D จาก [[Pet_Godot_Lab]] เต็มจอ **ไม่มีปุ่มควบคุม** (ปุ่มย้อนกลับของ Android ปิดโหมด)
แต่ยังตอบสนองเซ็นเซอร์ทั้งหมด: กล้อง (มองตามหน้า), ไจโร (เอียงตัวตามมือถือ), การเขย่า, การจิ้ม
Engine เดิมสามตัว (Canvas / Rive / หุ่น 3D เฟรม) ถูกลบหมดเมื่อ 2026-10-01 — ดู [[Pet_Avatar_Canvas]],
[[Rive_Avatar_Engine]], [[Pet_Robot_3D]] เป็นประวัติ

## สถาปัตยกรรม

```
MainActivity (FragmentActivity)                       process ":avatar"
 ├─ Compose: AlwaysLiveScreen ─ AvatarModeContent      GodotService (Godot 4.7 remote engine)
 │    ├─ PetModeController (สมองสัตว์เลี้ยง)             ├─ pet_godot (assets ของแอป)
 │    ├─ กล้องล่องหน → PetVisionBridge (มองตาม)         │    └─ app_bridge.gd  ←→  plugin "JarvisAvatar"
 │    └─ AvatarStage (commonMain)                        └─ AvatarGodotPlugin ──┐
 └─ container (R.id.avatar_stage) ← RemoteGodotFragment                          │ Messenger
      SurfaceView ← SurfaceControlViewHost ของ engine     AvatarLinkService ←────┘ (process หลัก)
```

- **Engine อยู่ process แยก** (`android:process=":avatar"`): Godot เริ่มซ้ำใน process เดิมไม่ได้
  (ปิด engine = `destroyAndKillProcess`) เลยให้ทุกครั้งที่เปิด Avatar mode ได้ process ใหม่ ปิดโหมด =
  process จบ ไม่กระทบเซอร์วิสเสียง/เทรดของแอป · engine พังก็ไม่พาแอปพัง
- ภาพ: `RemoteGodotFragment` ส่ง host token ให้ engine สร้าง `SurfaceControlViewHost` แล้วแสดง
  SurfacePackage ใน SurfaceView (zOrderOnTop) — การแตะส่งถึง engine โดยตรง · ต้อง Android 11+
  · ล็อกแนวตั้งระหว่างเปิด (ขนาด surface ตั้งครั้งเดียวตอนเริ่ม)
- คำสั่ง/เหตุการณ์: JSON ผ่าน Messenger — ตารางเต็มใน `pet_godot/scenes/SCHEMA.md` "App bridge"
  - แอป → หุ่น: `state` (look ทั้งหมด), `look` (สายตา), `voice` (ปากตามเสียง AI), `react` (ปฏิกิริยาร่างกาย),
    `tool` (ผล tool → ฉากข้อมูล), `scene`/`stop`, `say`, `effect`, `fidget`
  - หุ่น → แอป: `hello`, `ready`, `touch {kind, zone}`, `scene {id, state}`, `back`
  - คิว: ฝั่งแอป (`AvatarLink`) เก็บคำสั่งไว้จน engine ต่อ · `state` ล่าสุดเก็บที่ `AvatarStage` ส่งให้ engine ใหม่ก่อนเสมอ
    · ฝั่ง plugin เก็บไว้จน GDScript ส่ง `hello`

## ใครทำอะไร
| ส่วน | หน้าที่ |
|---|---|
| `PetModeController` | สมองเดิมทั้งหมด: state machine การสัมผัส/เขย่า, needs, idle, ฉาก, sentry, copycat — ผลคือ `AvatarState` |
| `avatar/AvatarLook.kt` | แปลง `AvatarState` (58 อารมณ์ + props 96 แบบ + ฉากหลัง 20 + eye style + foreground) เป็นภาษาของหุ่น: 9 ท่าทาง, 11 ตา, ของสวม 17+เครื่องมือ, ฉาก 13, emote ที่ลอยซ้ำ · มี entry effect (ปาร์ตี้ = confetti) และ gesture → fidget |
| `avatar/AvatarSkits.kt` | ละคร 12 ฉากของหุ่น + เสียงตามไทม์ไลน์ (แทน `RiveMoodStories`) · ฉาก archetype ที่ใกล้เคียง (angry_missile, super_love, dramatic_cry, celebration …) เล่นละครของหุ่นแทน |
| `avatar/AvatarToolData.kt` | tool ส่งข้อมูลดิบ (map ก่อนแปลงเป็นข้อความ) ให้ฉากข้อมูล: อากาศ, ราคา, เทคนิคอล, top gainers/losers, fear & greed, ภาพรวมคริปโต, งบดุล, แบตเตอรี่ · tool ข้อความ (ค้นเว็บ ข่าว แปล …) ไปการ์ด markdown |
| `ui/screen/AvatarModeScreen.kt` | ส่ง state/สายตา/เสียง/ฉาก/ผล tool ให้หุ่น · รับการแตะ → `onAvatarTouch` · กล้องล่องหน (มองตามหน้า + ส่งภาพให้ AI ตอนเปิดตา) · จอโหลด "กำลังปลุกน้อง..." |
| `AlwaysLiveScreen` | ต่อเซ็นเซอร์ (PetMotionBridge): เขย่า/เขย่าแรง/โคลงเรือ/ทุบโต๊ะ/หงายจอ/เสียงดัง → `AvatarStage.react` ทันที + สมองเลือกอารมณ์ |
| `app_bridge.gd` | ฝั่งหุ่น: แสดง state, ปฏิกิริยาร่างกาย, โซนการแตะ (หน้าผาก แก้มซ้าย/ขวา คาง หน้า ตัว), tap/double/stroke/scratch/hold, เอียงตัวตามแรงโน้มถ่วงของมือถือ |
| `RobotSprite` | หน้าหุ่นแบบภาพ (27 webp จาก `--sprites`) ใช้ใน Drive mode, Floating overlay และเครื่อง Android 10 ลงไป |

## การแตะ → สมองสัตว์เลี้ยง
| หุ่นส่ง | เรียก |
|---|---|
| tap หน้าผาก | `onPetHead(FOREHEAD)` |
| tap คาง | `onChinScratch()` |
| tap แก้ม | `onPoke(LEFT/RIGHT_CHEEK)` |
| tap หน้า/ตัว | `onPat()` |
| double แก้ม | `onTickle(cheek)` · double ที่อื่น → `onPetHead` |
| stroke (ลากลงบนหน้าผาก) / hold | `onPetHead(FOREHEAD)` |
| scratch (ลากขึ้นใต้คาง) | `onChinScratch()` |

## Build
1. `powershell -ExecutionPolicy Bypass -File pet_godot\tools\export_to_app.ps1` — ก๊อป AAR ของ Godot
   (จาก export templates ในเครื่อง → `composeApp/libs/`, gitignored) · export โปรเจกต์ → `godot-assets/`
   (gitignored) · เรนเดอร์หน้าหุ่น → `composeResources/files/robot/`
2. `gradlew :composeApp:installDebug` — ไม่มี AAR ในเครื่อง Gradle ใช้ `org.godotengine:godot:4.7.2.stable`
   จาก Maven · ไม่มี godot-assets แอปยัง build ได้และ Avatar mode แสดงหน้าหุ่นแบบภาพแทน
- แพ็กเฉพาะ `libgodot_android.so` arm64 (~75 MB ต่อ ABI) · `ignoreAssetsPattern` ต้องไม่ตัดโฟลเดอร์ `.godot`

## ฉากข้อมูลตามเสียงพูด (`ToolSceneSync`)
ผู้ใช้: "ระยะเวลาที่แสดงฉากอยู่จนพูดจบ และเปลี่ยนฉากถ้ามีการตอบคำสั่งอื่นต่อ" (2026-10-01)
- **ข้อมูลต่อ call**: tool publish ข้อมูลฉาก (`AvatarToolData.publish`, เป็น suspend) ลง `LiveToolCallContext.avatarData` ของ call ตัวเอง
  → `LiveToolBridge` ส่ง `AvatarLiveFeed.result(tool, data, text, later)` ทีละ call (เดิมช่องเดียวต่อชื่อ tool: Tesla กับ Microsoft ทับกัน)
  · feed ไม่มี replay — ผลเก่าไม่โผล่ตอนเปิดโหมดครั้งหน้า · ผล / คิวรับไป (`Deferred`) / ส่งให้ AI เล่า (`Report`) มาทาง `events`
  flow เดียว ลำดับจึงไม่สลับ
- **เวลาที่ได้ยิน**: ข้อความและเสียงของคำตอบมาถึงก่อนได้ยินหลายวินาที (09:48: ข้อความครบใน 5 วิ เสียงเล่นนาน 19 วิ) และเสียง
  ที่มาถึงแล้วล่วงหน้าข้อความไม่เท่ากัน (09:48: มาถึง 46% ของเสียงตอนข้อความถึง 40% — ฉากอากาศเดิมขึ้นช้าไป ~1 วิ) —
  `LiveGeminiService.speechMarks` ส่ง `LiveSpeechMark(ข้อความถึงตอนนี้, เวลาเริ่มของเสียง, ms ต่อหน่วย)` → `AvatarLiveFeed.speech`
  · คำที่ตำแหน่ง i ได้ยินตอน เริ่ม + `LiveProtocol.speechUnits`(ข้อความก่อนหน้า) × ms ต่อหน่วย (สระบน/ล่าง/วรรณยุกต์ = 0, ตัวเลข = 3)
  · ค่าเริ่ม 93 ms (คำตอบยาว 5 ครั้งใน log 2026-10-02 คลาดกัน ±6%) แล้วเรียนจากคำตอบยาว (≥ 6 วิ) ที่พูดจบใน session
  · หมายเหตุ: เทิร์นจบ (`turnComplete`) มาตอนเสียงเล่นจบเสมอ แต่เสียงมาถึงเร็วกว่านั้นมาก
- **กติกา** (`avatar/ToolSceneSync.kt`):
  - **ไม่มีฉากขึ้นก่อน AI พูดถึง** — ผลที่กลับมาระหว่าง AI ยังคิดรอไว้ (logcat 09:48: ถามรัว การ์ดทองกับอากาศขึ้นสลับกันตอน AI
    ยังคิด 8 วิ แล้วขึ้นใหม่ตอนพูด ผู้ใช้เห็นเป็น "ฉากสั้นๆ 1 รอบก่อนพูดจริง")
  - คำที่ AI พูด (`ToolSceneWords`: อากาศ/ฝน/องศา/ชื่อที่ · ทอง/Bitcoin/Tesla/Microsoft … · ข่าว/แปล/สรุป …) เลือกฉาก — ขึ้นตอน
    คำนั้น**ได้ยิน** · ใช้เฉพาะคำที่มีแค่ผลนั้น ("ราคา" เป็นของทุกราคา ชื่อเป็นตัวแยก) · ทุกผลที่ถูกเอ่ยในข้อความชุดเดียวกัน
    ได้เวลาของตัวเอง
  - AI พูดไป 1.5 วิแล้วไม่เอ่ยคำของผลใดเลย และมีผลใหม่ (≤ 30 วิ) รออยู่ผลเดียว → ขึ้นผลนั้น (ไม่ใช้ตอนเล่าข้อค้าง)
  - ฉากค้าง (pet_godot `hold`) จนเสียงหยุด ~0.7 วิ (+ hangover 0.85 วิของ `isAiSpeaking`) แล้ว `release` → ช่วงปิดฉากเล่นต่อ
    · เสียงของคำตอบจบแล้ว คำที่คาดว่าจะได้ยินทีหลังถือว่าได้ยินแล้ว (ไม่ขึ้นฉากหลังเสียงจบ)
  - ไม่มีใครพูดถึง 15 วิ → ปล่อย · ผลที่ยังไม่ได้พูดถึงรอได้ 5 นาที (เท่าคิวรายงาน)
  - **ผลที่เล่าทีหลัง** (`AvatarLiveFeed.Result.later`: งานที่ส่งให้ลูกน้อง — ตอนสั่ง AI พูดแค่ "รับทราบ…" — หรือ call ที่ server
    ยกเลิก · `Deferred`: คิวรายงานรับผลไว้ เช่นเทิร์นถูกตัดก่อน AI ได้พูด) คำพูดไม่เรียกขึ้น (คำรับงานอาจเอ่ยชื่อเรื่อง) —
    รอจนแอปส่งข้อค้างนั้นให้ AI เล่า (`LiveGeminiService.reportsSent` → `AvatarLiveFeed.report(tool, บรรทัดแรกของผล)` →
    `onReport`) แล้วขึ้นพร้อมคำแรกของคำตอบถัดไป · ผู้ใช้ถามเรื่องใหม่ก่อน (มีผลใหม่) → ข้อนั้นกลับไปรอรอบส่งซ้ำ ·
    ฉากที่ถูกปล่อยโดยที่ AI ไม่ได้พูดถึง → รอรายงานของมันเช่นกัน · ฉากที่**ขึ้นอยู่**ตอนคิวรับผลของมัน (คำตอบของงานเร็วถูกพูดทับ
    ก่อนได้ยิน 3 วิ, 2026-10-03) → ลงแล้วกลับเข้าคิวรอรายงาน ไม่ทิ้ง (เครื่องจำลองเจอ: เล่าซ้ำโดยไม่มีฉาก)
    (logcat 2026-10-02 00:18: การ์ดปฏิทินจะขึ้นตอน "กำลังให้ผู้ช่วยดึงข้อมูล" แล้วหายก่อน AI เล่าปฏิทินจริง 77 วิให้หลัง —
    ใช้กับทุก tool ใน `LiveIntentMatchers.DELEGATED_TOOLS` ที่มีฉาก: เทคนิค งบดุล Fear & Greed คริปโต อันดับหุ้น ปฏิทิน ข่าว sentiment)
  - คำของฉากใหม่ (2026-10-02): 5 มิติ ("5มิติ", lsd, orderflow, ฟีโบ), หลายไทม์เฟรม, SMC (premium/discount/fvg/โครงสร้าง),
    ตลาดโลก (s&p, nasdaq, vix), สแกน (overbought/oversold/วอลุ่ม/bollinger), elliott, harmonic, ขนาดไม้, วันที่/เวลา
- ฝั่งหุ่น: beat `hold` ในฉากข้อมูลทั้ง 14 (ก่อนเอามือลง) · `play_tool(…, hold)` · `release()` · ค้างได้นานสุด 60 วิ ·
  คำสั่งแอป `{"t":"tool", …, "hold":true}` / `{"t":"release"}` (`scenes/SCHEMA.md`)
- **คำพูดเกินหลังเปลี่ยนหน้า**: ตอบแล้วเรียกแค่ `device_avatar_emotion`/`device_custom_prop` (`LiveProtocol.isTrailingExpression`)
  → เสียง/ข้อความที่พูดต่อในเทิร์นนั้นไม่เล่น (log `🔇 …`) — Gemini 3.1 Flash Live ยังไม่มี async function calling
  (ผล tool ทุกแบบทำให้พูดต่อ และมันพูดคำทักทาย) · persona: เรียกเปลี่ยนหน้าก่อนพูดเท่านั้น

## ทุก tool มีฉาก (2026-10-02)
ผู้ใช้: "ตรวจสอบ Tool อื่นๆ เชื่อมโยงกับฉาก เช่น การวิเคราะห์ 5 มิติ ยังไม่มีการแสดงผลในฉาก และตรวจสอบทุก tool ให้แสดงผลในฉาก
ได้อย่างสวยงาม โดยการทดสอบเรียก tool จริง และ cap รูปตรวจสอบความถูกต้อง"
- **ฉากข้อมูลใหม่** (tool publish ข้อมูลของตัวเองผ่าน `AvatarToolData`):
  - `trading_deep_analysis_suite` → `deep_analysis_report` (`ResearchToolHandler.runLocalDeepSuite`; ทาง bridge MT5 ยังเป็นการ์ด)
  - `trading_multi_timeframe` → `mtf_report` · `trading_sentiment` → `sentiment_report` ·
    `trading_smc_analysis` / `trading_smc_structure` → `smc_report` (`SmcToolExecutor.publishSmc`) ·
    `trading_market_snapshot` → `market_snapshot_report` · สแกน overbought/oversold/volume/bollinger → `market_movers`
  - การ์ดสั้นที่ tool ทำเอง `{title, markdown}`: ข่าว (5 หัวข้อ + แหล่ง), ปฏิทิน (`calendarCard`: ข่าวที่ยังไม่ประกาศก่อน High ก่อน
    Medium, "ศ. 19:30", คาด/ก่อน), สัญญาณกลยุทธ์ (`StrategySignalProvider`: decision + ไทม์เฟรม ไม่เอาตาราง indicator),
    SMC Flow (`SmcFlowAlertProvider`: SMC/EMA/UT Bot/golden zone)
- **การ์ดของ tool อื่นทุกตัว** (`avatar/AvatarCards.kt`): ทุก tool ที่ตอบคำถาม (`trading_*` ยกเว้นคำสั่งซื้อขาย/ตั้งค่า, ค้นเว็บ, คำนวณ,
  แปลง, วันเวลา, ตำแหน่ง, กล้องอ่านภาพ …) — เดิมมีแค่ 9 ตัว · ข้อความถูกย่อ: ตัดเส้นคั่น/หมายเหตุถึง AI/`null`/ทศนิยมยาว,
  ตาราง → bullet, หยุดก่อนตาราง indicator / บทวิเคราะห์ AI, ≤ 10 บรรทัด 560 ตัวอักษร, บรรทัดชื่อเป็นหัวข้อ ·
  tool สั่งงาน (เปิดไฟ ส่งออเดอร์ …) ไม่มีการ์ด
- ตรวจด้วยข้อมูลจริง: `AvatarSceneDataLiveDump` (androidUnitTest, `RUN_LIVE_TESTS=1`) เรียก 32 tool จริงแล้วเก็บสิ่งที่แอปจะส่งให้หุ่น →
  `pet_godot/tools/tool_frames.ps1 -Data <dir>` render ทุกฉากเป็น contact sheet (ที่ 720×1280 — หน้าต่างเล็กกว่านี้วาดตัวไทยกว้างกว่า
  ที่วัดไว้ ~6% ทำให้ข้อความดูล้นขอบในภาพทั้งที่บนมือถือไม่ล้น)

## ฉากระหว่างคำตอบ: ฟัง / คิด / ทำงาน (`AvatarActivity`, 2026-10-02)
ผู้ใช้: "ระหว่างที่ AI ยังคิด กำลังคิด กำลังฟัง ฉันอยากให้มีฉากกำลังคิด กำลังฟัง"
- ฉากวนของ pet_godot (`"loop"` ในไฟล์ฉาก) เล่นจนแอปสั่ง `stop` หรือฉากข้อมูลของ tool มาแทน:
  - `listening` — ผู้ใช้พูด (`AvatarState.isUserSpeaking` ← `VoiceController.userVoice` ← `UserVoiceDetector`) นาน ≥ 0.3 วิ: หุ่นขยับเข้ามา เอียงหัว
    คลื่นเสียงออกจากสองข้างหัว แถบวัดเสียงใต้ตัวตามเสียงผู้ใช้ (`{"t":"hear","level"}` ~12 ครั้ง/วิ) พยักหน้าเป็นระยะ
  - `pondering` — ผู้ใช้พูดจบ (พูด ≥ 0.8 วิ) ถึง AI เริ่มตอบ: มองขึ้นซ้าย-ขวา ลูกแสง 3 ดวงหมุนรอบหัว · นานสุด 10 วิ ·
    ถ้า server ไม่ได้ยินคำพูดเลย (ไม่มี transcript — `AvatarLiveFeed.heardAtMs`) จบที่ 3.5 วิ (เสียงรบกวน)
  - `working` — tool ที่ตอบคำถามกำลังรัน (`AvatarLiveFeed.working` ← `LiveToolBridge` ตอนเริ่ม call / `activeToolName` = null):
    ถอยไปมุมล่างซ้าย ยกการ์ด "กำลังวิเคราะห์ 5 มิติ… BTCUSDT · 1h" (วงหมุน + แถบสแกน; คำจาก `task` ใน `tool_scenes.json`) ·
    tool ตัวถัดไป → การ์ดเปลี่ยนข้อความโดยไม่เริ่มฉากใหม่ (`scene` id เดิม + data) · tool เสร็จแล้วการ์ดค้างจน AI พูด (≤ 8 วิ) ·
    AI พูดและมีผลรอขึ้นฉาก → ค้างอีก ≤ 2.5 วิ ให้ฉากข้อมูลมาแทนที่ตำแหน่งเดิม (ไม่เดินกลับบ้านก่อน)
- ลำดับ: ฉากข้อมูลขึ้นอยู่ → ไม่เล่นอะไรทับ · ฟัง > ทำงาน > คิด (ผู้ใช้พูดแทรกตอน tool รัน → ฟังก่อน แล้วกลับไปการ์ดทำงาน)
- log: `AvatarStage: activity NONE → LISTENING` … · เทสต์ `AvatarActivityTest` (8) / `AvatarCardsTest` (4)
- **"กำลังฟัง" ค้างทั้งที่เงียบ** (logcat 2026-10-02 23:29–23:34: ค้าง 57 วิ และ 2 นาทีกว่า, `User speaking: true | Mic: 0%`):
  1. ตัวจับ "ผู้ใช้พูด" เดิมใน App.kt = `delay(700)` ใน `LaunchedEffect(audioLevel, …)` — ระดับไมค์เปลี่ยนทุก buffer (~30 ครั้ง/วิ แม้ห้องเงียบ)
     effect จึงเริ่มใหม่ทุกครั้ง หน่วงไม่เคยครบ → ค้างจน AI พูด (บั๊กเก่า เห็นชัดเพราะหุ่นมีฉากฟังแล้ว)
  2. ไมค์ได้ยินเสียงเอฟเฟกต์ของหุ่นเอง: ALARM (เตือนหิว 23:31:13, Mic 7%), CONFUSED (เขย่าเครื่อง 23:32:24, Mic 13%), WAKE_UP
  - แก้: `controller/UserVoiceDetector` ทำงานกับระดับไมค์ทุก buffer ใน `VoiceController` — ดังกว่า 0.035 และ 3 เท่าของเสียงพื้นหลังห้อง
    (ค่าต่ำสุด 2 วิล่าสุด: พัดลม ฝน เสียงฉากวน) · ต้องดังต่อเนื่อง ≥ 0.25 วิ (คลิก เคาะ ไอ ไม่นับ) · จบ 0.7 วิหลังเสียงสุดท้าย ·
    ไม่นับช่วงเสียง AI / TTS / เสียงเอฟเฟกต์หุ่น (`RobotSoundPlayer.selfSoundUntilMs` ← `RobotSoundEngine` ความยาวเสียง + 0.3 วิ) ·
    App.kt ใช้ `viewModel.userVoice` แทน · เทสต์ `UserVoiceDetectorTest` (6)
- **server ไม่ได้ยินผู้ใช้** (logcat 2026-10-03 08:39–08:41: พูด 6 ครั้ง หุ่นฟัง → คิด → ว่าง ไม่มีคำตอบ): `LiveGeminiService.deafUtterances` /
  `deafReconnects` → [[LiveToolBridge]] → `AvatarLiveFeed.notices` → `AvatarStage.say(…, 3.5 วิ)` — หุ่นบอกในบอลลูน
  "ไม่ได้ยินเลยฮับ พูดอีกทีได้ไหม" (ครั้งที่ 1–2) · "ขอต่อสายใหม่แป๊บนะฮับ…" (ครั้งที่ 3 เปิด session ใหม่) ·
  "ยังไม่ได้ยินเสียงเลยฮับ ลองเช็คเน็ต หรือเปิดใหม่อีกทีนะ" (เปิดใหม่แล้วยังไม่ได้ยิน และทุกครั้งต่อจากนั้น) — ไม่ใส่ emoji
  (บอลลูนใช้ฟอนต์ตั้งต้นของ Godot ยังไม่ได้ตรวจว่า emoji ขึ้นบนมือถือ) · กลไกดู [[LiveGeminiService]]

## งานหลายอย่างพร้อมกัน (หัวหน้า/ลูกน้อง): รายการงานบนจอหุ่น (`AvatarTasks`, 2026-10-02)
ผู้ใช้: "ตรวจสอบเชิงลึก Avatar_Mode เกี่ยวการเรียกใช้ tool และระบบ multi task อีกครั้ง มันยังสับสน และทำงานถูกต้อง ไม่ถูกต้อง"
- **ตรวจด้วยเครื่องจำลอง** (`androidUnitTest/.../sim/LiveSessionSim.kt`, `AvatarMultiTaskSimTest`, `RUN_SIM_TESTS=1`, เวลาจริง ~1 นาที/สถานการณ์):
  `LiveGeminiService` (เฟรม เทิร์น คิวรายงาน) + `LiveToolBridge` (ส่งลูกน้อง) + `ToolSceneSync` + `AvatarActivity` + `AvatarTasks` ของจริง
  กับ server ปลอมที่ทำตัวเหมือนใน log 2026-10-02: transcript ของผู้ใช้มาทีเดียวตอนจบเทิร์น tool call ตามมา · พูดทับ = `interrupted` +
  `turnComplete` 0 byte + ยกเลิก call ที่ยังไม่ได้คำตอบ · เสียงมาเร็วกว่าเล่น 3 เท่า `turnComplete` มาตอนเสียงเล่นจบ · รับงานลูกน้อง =
  พูด "รับทราบ…" · `[PENDING QUESTION]` = เล่าใน ~1.8 วิ · tool เป็นของปลอม (หน่วงเวลา + ข้อความ + ข้อมูลฉากจาก `data_samples`)
  → timeline ใน `composeApp/build/sim/<สถานการณ์>.txt` (ผู้ใช้ เสียง AI ที่ได้ยินจริง log ของแอป ฉาก ท่าหุ่น รายการงาน) + ตรวจว่า
  ทุกเรื่องถูกเล่าครบครั้งเดียว ฉากขึ้นตรงคำของมัน รายการงานว่างตอนจบ · ต้องมี seam: `LogTap.sink` (log ใน JVM test),
  `ToolExecutor.testOverride`
- **สถานการณ์** (จาก log จริง): ถามรัว 3 ข้อ (15:26) · ถามเว้นจังหวะ · พูดทับรายงานก่อนเสียงมา / กลางเสียง 3.6 วิ · server นิ่งจนแอปดัน
  `audioStreamEnd` (12:57) · พูดทับ tool ตรง (อากาศช้า 3 วิ) · คำถามถูกตัดระหว่าง AI ยังคิด (ยังไม่เรียก tool, 14:54:02 คิด 1.5 วิ)
- **ผล**: flow ถูกตามที่ออกแบบทุกสถานการณ์ — ทุกเรื่องได้เล่าครั้งเดียว ฉากขึ้นกับคำของมัน · สิ่งที่ทำให้ "สับสน":
  1. คำ "รับทราบ" ของงานที่ส่งลูกน้องถูกคำถามถัดไปตัดภายใน 0.1–0.5 วิ (log จริง 15:26 ก็เช่นนี้) — ผู้ใช้ไม่รู้ว่ารับงานแล้ว ไม่รู้ว่า
     เสร็จแล้วรอเล่า (BTC เสร็จวินาทีที่ 5.8 แต่เล่าวินาทีที่ 27 หลังตอบราคาทองที่ถามทีหลัง)
  2. **บั๊ก**: การ์ด "กำลังทำงาน" ตามช่องเดียว (tool ล่าสุด) — call เปลี่ยนหน้า (`device_avatar_emotion`) ที่โมเดลเรียกพร้อมกันทับงาน 5 มิติ
     การ์ดไม่ขึ้นเลย · call ที่ server ยกเลิกแล้ว (รันต่อเบื้องหลัง) ยังยึดการ์ดตอนผู้ใช้ถามข้อถัดไป
  3. คำถามที่ถูกตัดก่อนเรียก tool → แอปถามซ้ำทีหลัง → โมเดลเรียก tool ที่ปกติส่งลูกน้อง → ได้แค่ "รับทราบ…" แล้วรอรายงานอีกรอบ
- **แก้**:
  - **รายการงานบนจอหุ่น** (`AvatarTasks.board` ← `AvatarLiveFeed.jobs` + `reports` → `AvatarStage.tasks` → pet_godot `task_hud.gd`):
    เม็ดละงานมุมขวาล่าง ชื่อ = คำถามของผู้ใช้ · กำลังทำ (ส่งลูกน้อง หรือ call ที่ server ยกเลิกแต่ยังรัน) → เสร็จ รอเล่า (อยู่ในคิว)
    → กำลังเล่า (ส่งให้ AI แล้ว) → หายเมื่อเล่าจบ · id เดียวตลอด (call id ติดไปกับข้อในคิว: `PendingAnswerQueue.Item.key`) ·
    สูงสุด 3 เม็ด + "+N งาน" · call ที่ AI รอผลก่อนพูด (อากาศ ราคา) ไม่อยู่ในรายการ: การ์ดทำงานแสดงแทน
  - การ์ดทำงาน = call ล่าสุดที่ AI รอผล (`AvatarTasks.foreground`: ไม่ส่งลูกน้อง ไม่ถูกยกเลิก) — งานเบื้องหลังอยู่ในรายการงาน
  - ข้อค้างที่แอปถามซ้ำ: tool ที่ปกติส่งลูกน้องรันในเทิร์นนั้นเลย (`LiveGeminiService.answeringReport`, log `🧾`) ดู [[LiveToolBridge]]
- **2026-10-03 งานเร็วหัวหน้าตอบเอง**: งานของลูกน้องที่เสร็จใน 1.5 วิตอบในเทิร์นนั้น (การ์ดทำงานแสดงระหว่างรอ) · ไม่ทัน → ส่งต่อ งานย้ายไป
  รายการงานตอนนั้น · ถามรัวจนข้อก่อนถูกตัด → เข้ารายการงานเหมือนเดิม ดู [[LiveToolBridge]]

## ชีวิตตอนว่าง: เล่น กิน อาบน้ำ ง่วง หลับ (`PetSelfCare`, 2026-10-03)
ผู้ใช้: "ตรวจสอบระบบพารามิเตอร์ หิว ง่วงนอน อาบน้ำ เล่น … เราได้นำปุ่มพวกนั้นออกไปแล้ว แต่เหมือนฟังก์ชันยังอยู่ ฉันต้องการให้หุ่นจัดการเอง
ได้เลย กิน นอน เล่น" แล้วกำหนดไทม์ไลน์: "ไม่มีการคุย 30 วินาทีเริ่มเล่นกับตัวเอง 60 วินาทีเริ่มง่วง สัปหงก หัวตก ตาจะหลับ 90 วินาที
หน้าจอบนล่างหรี่เข้าหากันเหมือนตาปิด/เปิด 120 วินาทีปิดสนิท หน้าจอมือถือลดแสงเหลือ 1% มีเสียงพูดหรือจิ้มหน้าจอ แสงกลับมาเท่าเดิม
ฉากค่อยๆ เปิดออกเหมือนลืมตา หุ่นตื่นนอน"
- **เดิม**: ค่าความต้องการ (`PetNeedsState`) ลดตามเวลาต่อ แต่ไม่มีปุ่มดูแลแล้ว (หน้าสัตว์เลี้ยงถูกแทนด้วยหุ่น Godot 2026-10-02) — หิวจัด
  (อิ่ม < 20) → ANGRY + เสียง ALARM ทุกนาที ไม่มีใครให้อาหารได้นอกจากสั่งเสียง (logcat 2026-10-03 00:16:37, 00:17:42)
- **ความเงียบ** = ไม่มีเสียงคุย (ผู้ใช้หรือ AI) ไม่มี tool รัน/ผลรอเล่า/ฉากข้อมูล ไม่มีการแตะ/เขย่า/ท่ามือ — หน้าในกล้องไม่นับ
  (`PetModeController.onAvatarTick(active, userVoice)` ทุก 0.1 วิจาก `AvatarModeScreen`, `noteTouch()` ทุกครั้งที่แตะหุ่น)
  - **30 วิ** ทำอะไรของตัวเองหนึ่งอย่าง (`PetSelfCare.act`): หิว (อิ่ม < 35) → กินของว่าง (ฉาก `eat`, ของสุ่ม: โดนัท เบอร์เกอร์ พิซซ่า เค้ก
    ไอศกรีม ป๊อปคอร์น; อิ่ม +50) · สกปรก (< 35) → อาบน้ำ (`bath`; สะอาด 100) · ไม่งั้นเล่นเอง (`play_ball` / `idle_play`; สุข +15
    พลังงาน −5) · เหนื่อยจัด (< 25) ไม่เล่น · ทำเองไม่เพิ่มความสนิท
  - **60 วิ** ง่วง (`drowsy` วน): ตาปรือ สัปหงก หัวตก ตาปิด สะดุ้งลืมตา แล้วหัวตกอีก
  - **90 วิ** หนังตาของจอ (`AvatarStage.eyelidsDrowsy(30)` → `eyelids.gd`): หนังตาบนล่างปิดเข้าหากัน ขอบโค้งแบบตา กะพริบหนักขึ้นเรื่อยๆ
  - **120 วิ** หลับ (`sleep` วน, emotion SLEEPING พลังงานฟื้น): หนังตาปิดสนิท (1.5 วิ) แล้วหรี่จอเหลือ 1% (`ScreenDimmer` → MainActivity
    `window.attributes.screenBrightness` เฉพาะหน้าต่างแอป ไม่แตะการตั้งค่าระบบ)
  - ขั้นที่เริ่มช้า (มีฉากอื่นค้าง) ยังอยู่อย่างน้อย 15 / 20 วิก่อนขั้นถัดไป · ทีละขั้น (`PetSelfCare.nextStage`)
- **ปลุก**: เสียงผู้ใช้ (`userVoice`) หรือแตะตรงไหนก็ได้ (หนังตาขึ้นอยู่ → แตะมุมจอก็ส่ง `touch tap zone=screen`) → จอสว่างเท่าเดิม
  หนังตาเปิดช้าๆ 2.2 วิ หุ่นลุกจากท่านอนด้วย `wake_up` (`keep_pose`: ไม่กระโดดกลับบ้าน, ฟ้าสว่าง ยืดเส้นยืดสาย) — **เสียง AI เองไม่ปลุก**
  (คำ "ฝันดีฮับ" หลัง "พาน้องนอน", แจ้งเตือนกลางคืน) · ระหว่างง่วง (60–120 วิ) อะไรก็ตามที่เป็นการคุยทำให้ตื่นทันที (หนังตาเปิด 0.5 วิ) ·
  เขย่า/ทุบโต๊ะตอนหลับ → ตื่นเป็นท่าตกใจ
- สั่งเสียงยังใช้ได้ (`device_pet_care`): ให้อาหาร/อาบน้ำ/เล่น เล่นฉากเดียวกัน · "พาน้องนอน" → หลับทันที (จอมืด) ถ้าไม่ง่วงเลยจะบอก ·
  `status` บอกว่าล่าสุดทำอะไร (`selfCareNote`: "กิน 12 นาทีก่อน · นอน 40 นาทีก่อน") · persona ข้อ 13 บอก AI ว่าน้องดูแลตัวเองได้
- เลิกบ่น: `PetStateMachine.resolvePassiveEmotion` ไม่มีหิว → ANGRY+ALARM, หมดแรง → SLEEPING, เบื่อ → BORED แล้ว (เหลือเศร้ารวม,
  งอนเครียด) · หน้าหายจากกล้องนานตอนพลังงานต่ำ → เหงา (BORED) ไม่ใช่หลับ — การหลับมาจากความเงียบ · ท่าหาวของ idle loop เดิมเอาออก
- log: `PetModeController: 🤖 ว่าง 30 วิ: PLAY …` · `🥱 ง่วง สัปหงก` · `😪 หนังตาหย่อน` · `😴 หลับ (เงียบ 120 วิ)` · `⏰ ตื่น (มีเสียงพูด)
  จาก ASLEEP` · `AvatarStage: idle DROWSY → EYELIDS` · `ScreenDimmer: 🌙 screen → 1%` / `☀️ screen → its own brightness`
- ฉาก/เอฟเฟกต์ใหม่ใน pet_godot: `care_fx.gd` (ของว่างกัดสามคำ + เศษขนม ปากหงับตาม, ฟองสบู่ ฝักบัว น้ำกระเด็น, ลูกบอลชายหาดโหม่งบนหัว
  แล้วพุ่งชนจอ) · `eyelids.gd` + `shaders/eyelids.gdshader` · beat `head_nod`, `blink` · ฉาก `max_loop: 0` (วนจนสั่งหยุด), `keep_pose`
  · ตรวจภาพ: `tools/sleep_frames.gd`, `tools/scene_frames.ps1 -Scenes eat,bath,play_ball`
- เทสต์: `PetSelfCareTest` (6), `PetIdleLifeTest` (6: ไทม์ไลน์ครบ 2 นาที, หิวกิน/สกปรกอาบ, เสียงผู้ใช้ปลุกแต่เสียง AI ไม่ปลุก, แตะปลุก,
  คุยตอนง่วง, สั่งนอน) · pet_godot `scene_test.gd` 191 PASS
- **ยังต้องดูบนมือถือ**: ความสว่าง 1% จริงของ SM-S908E, การแตะตอนจอมืด, เสียงหาว/กรน

## วงจรชีวิตของ engine (`AvatarStageHost`)
- เปิดโหมด → ล็อกแนวตั้ง, ขอจอ 60 Hz (engine วาดจาก process อื่น จอ Samsung เลยลดเหลือ 30 Hz เอง), เริ่ม engine ·
  ระหว่างโหลดเห็น boot splash (หน้าหุ่น) ทับจอ "กำลังปลุกน้อง..."
- แอปอยู่เบื้องหลัง 30 วิ → ปล่อย engine (ตอนพักกิน ~1.2 GB PSS) · กลับมา → engine ใหม่ได้ `state` ล่าสุดก่อน
- engine ตายเอง (LMK / crash) → เริ่มใหม่อัตโนมัติ · **ตาย 3 ครั้งใน 60 วิ → เลิกเริ่ม** (`AvatarStage.failed`) แล้วแสดงหน้าหุ่นแบบภาพ
  "หุ่น 3D เปิดไม่ขึ้น — แสดงหน้าหุ่นแทน" จนกว่าจะเปิดแอปใหม่ (กัน crash loop กินแบต/หน่วยความจำ) · ทดสอบบนเครื่องแล้ว:
  `adb shell run-as com.skyliner2008.jarvis kill -9 $(adb shell pidof com.skyliner2008.jarvis:avatar)` สามครั้ง
- ปิดโหมด → หยุด engine (process จบ) + ล้างคำสั่งที่ค้างคิว (`AvatarLink.clearPending()`) ไม่ให้ไปโผล่ตอนเปิดครั้งหน้า

## ข้อควรระวัง (จากการตรวจ 2026-10-01)
- **ทิศการเอียง**: แรงโน้มถ่วงของ Godot = ค่า Android กลับเครื่องหมาย — ตั้งตรงอ่านได้ (0, -9.8, 0), หมุนตามเข็มนาฬิกา θ อ่านได้
  9.8·(sin θ, -cos θ) → มุมเอียง = `atan2(g.x, -g.y)` แล้วหมุนตัวทวนเข็มกลับมาตั้งตรง (เดิมกลับด้าน) · นอนราบบนโต๊ะไม่เอียง
- **`stop` ระบุ id**: แอปหยุดเฉพาะละครที่ตัวเองสั่ง — ถ้าฉากข้อมูลของ tool เข้ามาแทนระหว่างนั้น ฉากนั้นเล่นต่อไม่โดนตัด ·
  สมองเปลี่ยนไปฉากที่ไม่มีละคร → ละครเดิมหยุดทันที (ไม่ค้างเล่นจนจบ)
- **ผล tool เก่า**: flow `lastToolResult` เก็บผลล่าสุดไว้ (อาจมาจากแชทเก่า) — Avatar mode เล่นเฉพาะผลที่มา *ระหว่าง* เปิดโหมด
- **สายตาจากกล้อง** (`PetVisionDetector` → `GazeNeutral` → `GazeStabilizer` → `onGazeTouch` → `AvatarStage.look`):
  - ขึ้น-ลง: "มองตรง" คือที่ที่หน้าผู้ใช้อยู่ประจำ — หน้าแรกหลังหายไป >5 วิ = มองตรง แล้วเส้นค่อยๆ ตามหน้า (~8 วิ); ขยับเร็วยังมองตาม ·
    เดิมเป็นเส้นตายตัว -0.45 (สำหรับวางบนขาตั้ง) ถือในมือแล้วหุ่นก้มมองพื้นตลอด (log ผู้ใช้: normY 0.84–0.99) ·
    ซ้าย-ขวา: ค่าตรงๆ (กล้องอยู่กลางเครื่อง)
  - หน้านิ่งค่าไม่เปลี่ยน → แอปส่ง `look` ซ้ำทุก 1 วิ ไม่งั้นหุ่นคิดว่าไม่มีใครแล้วมองไปทั่วเองหลัง 2.5 วิ (`LOOK_HOLD`) ·
    (0, 0) = กล้องไม่เห็นหน้า → ปล่อยให้หุ่นมองเอง
- **ท่าทางของ AI** (`gesture`): TILT → tilt, BOUNCE/JUMP → hop, WOBBLE/SHAKE → wiggle, NOD → nod (พยักหน้า 2 ครั้ง)
- ~~เสียง ALARM เป็นระยะ = สมองหิว~~ — เลิกแล้ว (2026-10-03): หุ่นกินเองตอนว่าง 30 วิ ดู "ชีวิตตอนว่าง"
- ใช้เวลาเปิด: ครั้งแรกหลังติดตั้ง ~5.5 วิ, ครั้งต่อไป ~3.5 วิ (process ~1 วิ + โหลดโปรเจกต์ ~2 วิ + warm-up)

## ทดสอบ
- `pet_godot`: `Godot --headless --path pet_godot --script res://tools/scene_test.gd` (bridge: state/gear/backdrop/voice/รอฉากจบ/
  stop ตาม id/โซนแตะ/กล่องคำพูด/เอียง) · แอป: `AvatarLookTest` (58 อารมณ์, props/ตา/ฉาก, การแตะ → สมอง, หน้าหุ่นแบบภาพ, tool data),
  `AvatarStageTest` (รูปแบบคำสั่ง: stop + id, ส่ง look ให้ engine ใหม่, ช่วงค่า gaze/voice, ready),
  `PetSceneScriptTest`/`AvatarSkitsTest`, `PetSceneDemoVoiceTest`
- บนมือถือ: `adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION --es cmd 5 -p com.skyliner2008.jarvis` เปิดโหมด ·
  `--es cmd 'PARTY|scene=godot:party'` เล่นละคร · `--es cmd RESET` หยุด · log: `adb logcat -s AvatarStage AvatarStageHost AvatarLink AvatarGodotPlugin`
- **อ่าน log**: `adb logcat -s AvatarStage` — `→ {...}` คือคำสั่งที่ส่งให้หุ่น (state / react / tool <ชื่อ> / scene / stop / fidget /
  effect / say; look กับ voice ไม่ log เพราะถี่), `event ...` คือสิ่งที่หุ่นตอบกลับ (hello, ready, touch, scene started/finished, back),
  `tool X: no info scene for it` = tool ที่ไม่มีฉาก · ตอนส่ง log ให้ตรวจ อย่ากรองแท็ก `AvatarStage` / `LiveBridge` ออก
  (buffer ของมือถือเก็บได้แค่ ~2 นาทีเพราะ log กล้องถี่มาก)
- **`PROCESS STARTED (pid)` / `PROCESS ENDED (pid)` ใน Logcat ของ Android Studio** ตอนเข้า/ออกโหมดอวตาร = process `:avatar`
  ของหุ่น (เริ่มตอน `Profile changed: CONTROL -> PET`, จบตอนกลับ CONTROL) — ไม่ใช่แอปรีสตาร์ต: บรรทัดของแอปยังเป็น pid เดิมตลอด
  (log ผู้ใช้ 2026-10-01 16:23:33 → 16:25:22: แอป 9818 ทั้งหมด, หุ่น 12961)
- **AI ในโหมดอวตาร**: persona ไม่มีประโยคทักทายแบบคำต่อคำแล้ว (ทักทายครั้งเดียวด้วยคำพูดของตัวเอง แล้วเริ่มที่เนื้อหาทุกคำตอบ)
  เพราะโมเดลเคยหยิบประโยคนั้นมาพูดแทนผลราคา/อากาศ — ดู [[LiveToolBridge]] "ข้อความกำกับเสียง", [[LiveGeminiService]]
- **`device_weather` แต่งหุ่นตามอากาศเฉพาะโหมดอวตาร** (ฉาก sunny/rainy + ของ) — เดิมสั่งทุกโหมดผ่าน `triggerTestEmotion` ซึ่งเปิดหน้า
  Live เต็มจอ: โหมดปกติจึงเด้งไปหน้า Drive ทั้งที่หน้าหุ่นแบบภาพไม่แสดงฉาก/ของ (logcat 2026-10-01 16:51)
- ผู้ใช้ทดสอบเองแล้ว (2026-10-01): กล้องจับหน้า, ท่ามือ, โคลงเรือ, ทุบโต๊ะ, เขย่า, AI เรียก tool — สมองตอบครบ ·
  **ยังต้องดูด้วยตา**: หุ่นมองตามหน้าหลังแก้ (ไม่ก้มมองพื้น), การ์ดราคา/อากาศขึ้นตอนถาม AI, ทิศการเอียงจริง, ปากตามเสียง AI
