---
title: Rive Avatar Engine & Compose Multiplatform Dual-Engine Architecture
category: Components
tags:
  - rive
  - avatar
  - state-machine
  - data-binding
  - multiplatform
  - android
  - compose-ui
sources:
  - rive_avatar/scene.rml
  - rive_avatar/tools/build_scene.py
  - rive_avatar/AVATAR_CONTRACT.md
  - rive_avatar/presets.json
  - scripts/build_rive_avatar.ps1
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/RiveAvatarView.kt
  - composeApp/src/androidMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/RiveAvatarView.android.kt
  - composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobotHeadAvatar.kt
created: 2026-09-14
updated: 2026-09-15
---

# Rive Avatar Engine & Dual-Engine Architecture

## 1. ภาพรวม (Overview)
ระบบ Rive Avatar Engine เรนเดอร์ JARVIS Robot Avatar ด้วย **Rive CLI** คู่กับ **Compose Multiplatform**
ในสถาปัตยกรรม **Dual-Engine** ที่สลับได้ระหว่าง Compose Canvas (LOOI moodset เดิม) และ Rive (`.riv`)

**สถานะปัจจุบัน (v6, 2026-09-15):** ฉาก Rive เป็นสไตล์ LOOI — ตาใหญ่ สื่ออารมณ์ด้วยตาเป็นหลัก ไม่มีคิ้ว/ปากเกือบทุกหน้า
ช่องสัญญาณอิสระที่ mix กันได้: **34 หน้า · 56 พร๊อพ · 11 ฉากหลัง · 12 ฉากหน้า · 7 ท่าตา ·
64 เรื่องราว (seq) · 20 สถานะ (state) · 9 ปฏิกิริยาสัมผัส (react)** ควบคุมผ่าน **View Model Data Binding** ทั้งหมด
รวมชุดสถานะ/ฟีเจอร์ AI ของ LOOI (IDs 21–40): Idea, Scanning, Processing, Charging, SoftwareUpdate,
Weather, Alarm, Calendar, StandbyClock (ตัวเลขจาก VM), DrawingMode, InvalidCommand, SystemSecured ฯลฯ

**ผูกกับโหมดสัตว์เลี้ยงแล้ว (2026-09-15)** — เงื่อนไขชุดเดียวกับ Engine Compose Canvas (ดูข้อ 5)

## 2. โครงสร้างโปรเจกต์ Rive (`rive_avatar/`)

| ไฟล์ | บทบาท |
|---|---|
| `tools/build_scene.py` | **แหล่งความจริง** — เจน `scene.rml` + `presets.json` และมี self-check ที่ทำ build ล้ม |
| `scene.rml` | ผลลัพธ์ที่เจน ~36k บรรทัด **ห้ามแก้มือ** |
| `presets.json` | ชื่อ/ดัชนีของทุกช่อง + `durationMs` ของ seq/state/react + `selfPlay` |
| `AVATAR_CONTRACT.md` | สัญญาข้อมูลฉบับเต็ม (ภาษาอังกฤษ) |
| `avatar.luau`, `mathutil.luau`, `avatar_test.luau` | สคริปต์เดิม **ไม่ได้ผูกเข้าฉาก** (ใช้แค่รัน `--test`) |
| `build/rive_avatar.riv` | ไบนารี → คัดลอกไป `composeApp/src/androidMain/res/raw/avatar.riv` |

### โครงฉาก (scene graph)
```
FgSolo                                   หน้าสุด
PropsSolo                                พร๊อพ (Clock มี Solo ตัวเลข 4 หลักอยู่ข้างใน)
FaceTilt        <- ไจโร (data bind x / y / rotation)
  FaceYaw       <- GazeYaw   (x / scaleX / rotation)
    FacePitch   <- GazePitch (y / scaleY)
      FaceSpeak <- Speech    (หัวผงกตอนพูด)
        FaceBreathe
          BrowYaw  > BrowPitch  > BrowSolo
          LEyeYaw  > LEyePitch  > LBlink > LStateBlink > LEyeSolo
          REyeYaw  > REyePitch  > RBlink > RStateBlink > REyeSolo
          MouthYaw > MouthPitch > MouthAudio > MouthScale > MouthSolo
                                                  Mouth_None มี VoiceBar ซ่อนอยู่
BgSolo                                   หลังสุด
```

## 3. Data Contract (View Model `Avatar`)

| property | type | range | ผล |
|---|---|---|---|
| `face` | number | 0–34 | สีหน้า (32 OneEye, 33 Recognized, 34 Starry) |
| `prop` | number | 0–55 | พร๊อพ (37–55 ชุด LOOI status ใหม่) |
| `bg` / `fg` | number | 0–10 / 0–11 | ฉากหลัง / ฉากหน้า |
| `eyeAct` | number | 0–6 | ท่าตา — Squint เปลี่ยนเป็นตา ^ ^, Closed เป็นเส้นที่ยังมองเห็น |
| `state` | number | 0–19 | สถานะการทำงาน — ลูปไม่รู้จบ |
| `react` | number | 0–8 | ปฏิกิริยาสัมผัส — **เล่นครั้งเดียวแล้วค้างเฟรมสุดท้าย** |
| `seq` | number | 0–100 | เรื่องราว — **เล่นครั้งเดียวแล้วค้างเฟรมสุดท้าย** · 65–81 = ฉากสัตว์เลี้ยง ([[Pet_Scene_Scripts]]) · 82–100 = เรื่องอารมณ์ตาเยลลี่ ([[Pet_Mood_Scripts]]) |
| `item` | number | 0–4 | ของในฉาก: อาหาร (Burger/Pizza/Cake/IceCream/Popcorn) · เครื่องดื่ม (Coffee/Boba/Tea/Beer) · Laptop/Book |
| `gazeX` / `gazeY` | number | −1…1 | หันหน้า 2.5D (ระยะกว้างขึ้น: หน้า ±40 px) |
| `tiltX` / `tiltY` | number | −1…1 | ไจโร |
| `audioLevel` | number | 0…1 | ปาก ×0.94…×1.22, แถบเสียง ×1…×2.2 |
| `isSpeaking` | boolean | | แถบเสียงโผล่ใต้ตา + หัวผงก (หน้าที่ไม่มีปากก็เห็นว่าพูด) |
| `clockD0..clockD3` | number | 0–9 | ตัวเลข HH:MM ของ StandbyClock (ค่าเริ่ม 12:00) |

**ลำดับความสำคัญ:** `react` > `seq` > `state` > ช่อง manual · gaze/tilt/audio/isSpeaking ทำงานเสมอ

**กฎที่โฮสต์ต้องทำ:** เขียน `seq`/`react` → รอ `durationMs` → เขียน `0` (มาช้าไม่เป็นไร ไม่เล่นซ้ำ) ·
จะเล่นเรื่องเดิมซ้ำให้เขียน `0` ก่อนแล้วค่อยเขียนดัชนีเดิมในเฟรมถัดไป (ทุก state มี `reset="true"`)

## 4. กลไกของ engine (บทเรียนจาก review 2026-09-15)

| ปัญหาเดิม | สาเหตุ | แก้ด้วย |
|---|---|---|
| โมชั่นแข็งเป็นเส้นตรงทั้งไฟล์ | keyframe `cubic` 3,960 ตัวไม่มี `CubicEaseInterpolator` → Rive ไม่ ease | ทุก keyframe ผ่าน `kf()` ที่แนบ curve (`cubic`/`easeIn`/`easeOut`/`soft`) + self-check |
| เรื่อง/ปฏิกิริยาเล่นซ้ำถ้าโฮสต์เคลียร์ช้า, เรียกซ้ำแล้วเล่นต่อกลางเรื่อง | `loopValue="loop"` และ state ไม่มี `reset` | seq/react = `oneShot`, ทุก channel state มี `reset="true"` |
| ท่าค้างหลังตัดเรื่อง (ตาเข้าหากัน/หัวเอียงค้าง) | Rive ไม่คืนค่า property ที่ไม่มีใครคีย์แล้ว | เลเยอร์ `RestPose` แรกสุด คีย์ค่าพักของทุก property ที่ถูก animate (คำนวณอัตโนมัติ) |
| แว่นตก/chyron/รูกระสุน/น้ำท่วม ไม่ตรงจังหวะที่โผล่ | ใช้นาฬิกากลาง `PropLoops` | ท่าเข้าลงทะเบียนด้วย `intro()` แล้วอบลงใน channel anim และทุก beat ที่สลับไปหาพร๊อพนั้น |
| Idle บีบตากากบาท/ตาหัวใจแบน | State layer คีย์ blink ทับ noblink ของ Face | motion-only state ใช้โหนด `StateBlink` + เลเยอร์ `BlinkGate` (ตาม `face`) ปัดตก |
| พูดแล้วไม่เห็นอะไร | หน้าไม่มีปาก + `VoiceIdle` คีย์ปาก=1 ทับตลอด | `VoiceBar` ใน `Mouth_None`, `VoiceIdle` ว่าง, หัวผงกผ่าน `FaceSpeak` |
| ของบางชิ้นหาย (เลนส์กล้อง, ซี่ถัง, ดาวบนเหรียญ, ตัวหนังสือ chyron) | ลำดับวาดใน mover กลับด้าน | `mover()` ใช้ `reverse_blocks` เหมือนพร๊อพ (ประกาศหลัง = อยู่หน้า) |
| ปากซิกแซกขาดเป็นขีด | สี่เหลี่ยมเอียงวางไม่ต่อกัน | `g_zigzag()` เป็น polyline เส้นเดียว |
| เรื่องเริ่มด้วยจอว่าง 1–2 วิ | `LEADIN` Neutral→Blank | Neutral→Sleepy (ตื่น), หน้า Blank เปลี่ยนพร้อมพร๊อพที่มาแทนตา |

**ลำดับเลเยอร์ (ตัวหลังชนะ):** RestPose → Blink → Breathe → Face → EyeAct → Prop → Bg → Fg → Clock0..3 →
PropLoops → State → BlinkGate → Seq → React → GazeYaw → GazePitch → Speech

**กับดัก anchor ของ mover (เดิม):** หมุน/สเกลอ้างจุดกำเนิดโหนด → ใช้ `mover(..., anchor=(x, y))`
และคีย์ x/y ที่ยิงใส่ mover ที่มี anchor ต้อง `rebase()`

## 5. การผูกกับโหมดสัตว์เลี้ยง (Pet Mode binding)
ทั้งสอง Engine อ่าน `AvatarState` และเหตุการณ์สัมผัส/เซนเซอร์ชุดเดียวกันจาก `PetModeScreen`

| ไฟล์ | หน้าที่ |
|---|---|
| `RiveAvatarBinding.kt` | ค่าคงที่ดัชนีตรงกับ `presets.json` · `AvatarState.effectiveEmotion()` (ใช้ร่วมกับ Canvas) · `RiveAvatarMapper.plan()` แปลงสถานะ → ช่อง Rive + รายการที่ต้องให้ Compose วาดต่อ · `reactFor()` |
| `RiveAvatarView.kt` | `expect RiveAvatarView(inputs, fallbackState)` · `RiveRuntime` (สถานะ native) · `RivePetAvatar` ตัวจับเวลา react / seq / Detected |
| `RiveAvatarView.android.kt` | `RiveAnimationView` + `autoBind = true` + `touchPassThrough = true` (ไม่งั้นกินทุก touch — พบบนเครื่องจริง) · cache property + เขียนเฉพาะค่าที่เปลี่ยน · ไจโร → `tiltX/Y` · ล้มแล้วถอยไป Canvas |
| `PetRobotHeadAvatar.kt` | สลับ Engine + ส่ง `riveReaction` / `isMissileBarrageActive` / `lastShakeTime` / `lastFaceTime` |
| `PetModeScreen.kt` | จำ Engine ที่เลือก (`pet.avatar_engine`) · แปลงมือโฮโลแกรม → `RiveReactionEvent` · ฉาก/พร๊อพที่ Rive ไม่มีให้ Compose วาดต่อ · ปิด overlay มือ/มิสซายของ Canvas เมื่อใช้ Rive |

| สัญญาณ (เหมือน Canvas) | ช่อง Rive |
|---|---|
| อารมณ์ 58 แบบ (+ eyeStyle override) | `face` + prop/bg/fg ประจำอารมณ์ · อุปกรณ์ที่ Rive ไม่มีวาดด้วย `PetPropsOverlay` |
| props / ฉากหลัง / ฉากหน้าจาก AI | ช่อง Rive ถ้ามีของเทียบเท่า ไม่งั้นชั้น Compose (artboard โปร่งใส) |
| LISTENING / THINKING / พูด / SLEEPING / DIZZY / BORED | `state` 3 / 4 / 5 / 19 / 13 / 8 |
| eye trick พักหน้าจอ · gesture เอียงหัว | `state` PlayBounce·PlayPeek·PlayChase · TiltL/TiltR |
| ลูบหัว·ตบ / จิ้ม·จั๊กจี้ / เกาคาง | `react` HeadPat / PokeL·R → PokeAnnoyed (POUT) → PokeAngry (ANGRY) / ChinScratch |
| ตกใจโดยไม่ได้แตะ (เสียงดัง/ทุบโต๊ะ) · เขย่าจนโกรธ | `react` Startled · ShakeAngry |
| เจอหน้าหลังหายไป > 10 วิ | `state` Detected |
| ยิงมิสซายสู้กลับ | `seq` AngryMissile แล้วเรียก `onMissileBarrageFinished` |

ทดสอบ: `RiveAvatarBindingTest` (8 เคส) · ตรวจดัชนี Kotlin เทียบ `presets.json` แล้วตรงทั้งหมด

## 6. Workflow สำหรับนักพัฒนา
```bat
scripts\build_rive_avatar.ps1     REM เจน → verify → inspect → test → compile → copy (ล้มกลางทางจะไม่ทับไฟล์ Android)
```
หรือทีละขั้นใน `rive_avatar/`: `python tools\build_scene.py scene.rml` · `rive . --verify` · `rive inspect . --summary` ·
`rive . --screenshot=build/x.png --data=seq=3 --advance=300`

เพิ่มสีหน้า = ต่อท้าย `FACES` · เพิ่มพร๊อพ = `elif` ใน `emit_prop` + ต่อท้าย `P_NAMES` (ลูปใช้ `track()`, ท่าเข้าใช้ `intro()`) ·
เพิ่มสถานะ = แถวใน `STATES` · เพิ่มเรื่อง = `HAND_STORIES` หรือเติมท่าให้ auto story ใน `PRESET_EXTRAS`

**ตัวตรวจที่ทำ build ล้ม:** cubic ไม่มี interpolator · channel state ไม่มี reset · seq/react ไม่ใช่ oneShot ·
ตาชนกัน (`max_converge` / `max_eye_scale` วัดจากขนาดตาที่เรนเดอร์จริง) · คิ้วอ่านเป็นตาคู่ที่สอง (`check_faces`)

**ข้อควรระวังจาก Rive CLI:** build ผ่านไม่ได้แปลว่าถูก — ต้องดู `rive inspect` และ *ดูภาพ* เสมอ

## 7. การผสานรวมเข้ากับ Compose Multiplatform
- `gradle/libs.versions.toml`: `app.rive:rive-android:11.12.0`
- Expect/Actual: `commonMain/RiveAvatarView.kt` · `androidMain/RiveAvatarView.android.kt` (AndroidView + RiveAnimationView) · `iosMain` ฟอลแบ็ก Canvas
- Dual-Engine Toggle: `AvatarEngineType` ใน `PetRobotHeadAvatar.kt`, สลับจาก `PetSettingsDialog.kt` (บันทึกค่าใน `pet.avatar_engine`)

## 8. งานที่ยังเหลือ
- [x] ~~ทดสอบบนเครื่องจริง~~ (Galaxy S22 Ultra: แสดงผล, จิ้ม, PokeAngry, tool ผ่านแชท) — เหลือเขย่า/คว่ำจอ/เสียงดังที่ต้องทดสอบด้วยมือ
- [x] ~~ผูก Android + เซนเซอร์/ทัช/timer กับโหมดสัตว์เลี้ยง~~ (2026-09-15)
- [ ] เขียน beat เองให้ preset ที่ยังใช้ `auto_story()`
- [x] ~~easing, oneShot/reset, RestPose, ท่าเข้าผูกกับการโผล่, BlinkGate, voice bar~~ (2026-09-15)
- [x] ~~สไตล์ LOOI: ตาใหญ่ ตัดคิ้ว/ปาก สีเดียวต่อหน้า + moodset IDs 21–40~~ (2026-09-15)

## 9. การเชื่อมโยงเอกสารที่เกี่ยวข้อง
- [[Always_AI_Live_Mode]]: สถาปัตยกรรมโหมด Live และการทำงานของระบบสัตว์เลี้ยง AI
- [[log]]: ประวัติการอัปเดตระบบและการเปลี่ยนแปลงโค้ด

## กฎการจัดวางพร็อพ (ตรวจ 2026-09-15)
ตา: ศูนย์กลาง (154, 230) / (346, 230) รัศมี 67 → ช่วงตา y 163–297
- **พร็อพบนหัว** (เขา มงกุฎ หมวก Zzz ไอคอนมุมจอ) ต้องอยู่เหนือ y 163 ทั้งชิ้น หรืออยู่นอกแนวตา
- **แว่น/ของสวมตา** (แว่นดำ แว่นพิกเซล VR หน้ากากดำน้ำ) สูง ≥ 150 กว้าง ≥ 170 ต่อข้าง คลุมตาทั้งสองข้าง
- **ต้องมองเห็นชัดบนมือถือ**: ไอคอนหลัก ≥ ~70 px ของประดับ ≥ ~30 px — ไอคอนเล็กขยายรอบจุดศูนย์กลางตัวเองผ่าน `PROP_FIT` / `fit()` และไม่ล้นกรอบ 500×500
- พร็อพที่ "แทนตา" (Bulb, Barcode, Gears, Sun…) ใช้คู่กับหน้า OneEye/Blank — แอปสลับเป็น OneEye อัตโนมัติเมื่อแสดง Bulb
- **Transition ต้องเป็น 0 สำหรับ layer ที่พาพร็อพ** (`Prop`, `Bg`, `Fg`, `Seq`, `React`): ถ้า blend ชิ้นส่วนจะเริ่มจาก rest pose (ท่าพีคบนจอ) ทำให้พร็อพกองโผล่แวบตอนเริ่มฉาก

## โครงดวงตา (2026-09-15)
- ตาแต่ละข้าง = ชั้น **หน้า** (`LEyeSolo` ใต้ `LIrisGaze` > `LIrisLook`) + ชั้น **หลัง** (`LEyeBackSolo`, เยื้องลง 5 px) — สลับรูปตาพร้อมกันเสมอ
- `IrisGaze` ←→ gazeX/gazeY (±13 px), `IrisLook` ← การขยับตาในเรื่อง/สถานะ/ปฏิกิริยา (×0.45, ±13 px) → ขอบชั้นหลังโผล่ฝั่งตรงข้ามกับทิศที่มอง
- ตากลมทั้งหมดเป็นวงรีตั้ง (×0.90 / ×1.06) · ไม่มีแววตาจุดเล็ก
- คีย์ตา/หัว/ปากในเรื่องใช้ easing `jelly` (overshoot) · แอปส่ง gaze/tilt ผ่าน spring หน่วงต่ำ
- **ห้ามวาดสี่เหลี่ยมเต็ม artboard**: บนมือถือ artboard เป็นสี่เหลี่ยมจัตุรัสในพื้นที่ที่สูงกว่า → ใช้ `wash()` (วงกลมไล่จาง) และ glow รัศมี ≤ 245 ที่กลางจอ
- วางบนจอ: `PetModeScreen` ค่าคงที่ `RIVE_PORTRAIT_SCALE` / `RIVE_PORTRAIT_EYE_LINE` (แถวตา 40% จากบนของพื้นที่สัตว์เลี้ยง)

