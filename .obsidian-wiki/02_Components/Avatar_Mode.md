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
    ฉากที่ถูกปล่อยโดยที่ AI ไม่ได้พูดถึง → รอรายงานของมันเช่นกัน
    (logcat 2026-10-02 00:18: การ์ดปฏิทินจะขึ้นตอน "กำลังให้ผู้ช่วยดึงข้อมูล" แล้วหายก่อน AI เล่าปฏิทินจริง 77 วิให้หลัง —
    ใช้กับทุก tool ใน `LiveIntentMatchers.DELEGATED_TOOLS` ที่มีฉาก: เทคนิค งบดุล Fear & Greed คริปโต อันดับหุ้น ปฏิทิน ข่าว sentiment)
  - วิเคราะห์ 5 มิติ (`trading_deep_analysis_suite`) ยังไม่มีฉากข้อมูล — หุ่นพูดอย่างเดียว
- ฝั่งหุ่น: beat `hold` ในฉากข้อมูลทั้ง 9 (ก่อนเอามือลง) · `play_tool(…, hold)` · `release()` · ค้างได้นานสุด 60 วิ ·
  คำสั่งแอป `{"t":"tool", …, "hold":true}` / `{"t":"release"}` (`scenes/SCHEMA.md`)
- **คำพูดเกินหลังเปลี่ยนหน้า**: ตอบแล้วเรียกแค่ `device_avatar_emotion`/`device_custom_prop` (`LiveProtocol.isTrailingExpression`)
  → เสียง/ข้อความที่พูดต่อในเทิร์นนั้นไม่เล่น (log `🔇 …`) — Gemini 3.1 Flash Live ยังไม่มี async function calling
  (ผล tool ทุกแบบทำให้พูดต่อ และมันพูดคำทักทาย) · persona: เรียกเปลี่ยนหน้าก่อนพูดเท่านั้น

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
- **เสียง ALARM เป็นระยะ** = สมองหิว ไม่ใช่บั๊ก: ทุก ~20 วิตอนว่าง ถ้า satiety < 20 → ANGRY + ALARM · ไม่มีปุ่มแล้ว ให้พูด
  "ให้อาหารน้อง" / "อาบน้ำให้น้อง" / "เล่นกับน้อง" (`device_pet_care`) หรือถาม "หิวไหม"
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
