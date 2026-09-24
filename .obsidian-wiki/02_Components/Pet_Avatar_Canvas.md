# Pet Avatar — Compose Canvas engine (eyes & motion)

เอนจินวาดหน้าสัตว์เลี้ยงแบบ Compose Canvas (อีกเอนจินคือ Rive — ดู [[Rive_Avatar_Engine]])
หน้า LOOI: จอดำ OLED + ดวงตานีออน ไม่มีหัวหุ่นยนต์ให้เห็น

> ปรับปรุงล่าสุด 2026-09-24 จากการตรวจระบบทั้งหมด (ดู [[log]] รายการ 2026-09-24 "ตรวจระบบสัตว์เลี้ยง")

## ไฟล์หลัก (`composeApp/src/commonMain/.../ui/component/avatar/`)
| ไฟล์ | หน้าที่ |
|------|--------|
| `PetRobotHeadAvatar.kt` | composable หลัก + `renderPetEmotion` (ทุกอารมณ์ใน `AvatarEmotion` มี renderer ของตัวเอง) + `renderEyeTrick` + helper วาดตา |
| `VirtualHeadKinematics.kt` | ตำแหน่งตา: `backCenter` (เบ้าตา), `frontCenter` (ลูกตา), `lookCenter` (ทั้งดวงตา) |
| `EyeMetrics.kt` | ค่าคงที่กลางทั้งหมดของการวาดตา (blink floor, pupil travel, head shift, parallax, resting offset, mouth Y, สีเงา) |
| `AvatarLivingEngine.kt` | `LivingMotionSource` (อ่านค่าใน draw phase) + `livingMotionAt()` (pure, เทสต์ได้) |
| `PetRobotNeonDraw.kt` | `drawSoftBloom` (brush cache), `getEyeDeepShadowColor`, `PetParticleMotionSource` |
| `PetRobotMoodsetDraw.kt` | renderer ของ moodset (PUZZLED … ROMANTIC) |
| `LayerClock.kt` | นาฬิกาต่อเนื่อง + `loopFrac()` สำหรับอนุภาคฉากหลัง/หน้า |
| `PetGestureAnimations.kt` | BOUNCE / JUMP / WOBBLE / SHAKE / NOD แบบผสมน้ำหนัก (TILT หมุนใน avatar ที่เดียว) |

## Pipeline: state → ตำแหน่งตา → Canvas
1. `PetModeScreen` → `PetRobotHeadAvatar(state)` · `effectiveEmotion()` (ใช้ร่วมกับ Rive)
2. **Composition** อ่านเฉพาะค่าที่เปลี่ยนเมื่อ `AvatarState` เปลี่ยน — เป้าของ spring/tween ทั้งหมด
3. **Draw phase** (ใน `Canvas {}`): `livingSource.snapshot()`, `particleSource.snapshot()`, breathing, blink,
   head roll = pose + gaze×3°×weight + dizzy wobble×weight + sway, yaw/pitch + nod/shake×weight
4. `VirtualHeadKinematics.calculate(...)` → `EyeAnchors`
   - `leftX/rightX/y` = `lookCenter` → renderer ที่ไม่มีชั้นลูกตาแยก (หัวใจ, ตาเฉียง, moodset, พร็อพ)
   - `leftSocketX/rightSocketX/socketY` = `backCenter` → `drawDualCircleEye(centerX, …, gazeX, gazeY)` แบบพิกัดเดิม ซึ่งบวกสายตาให้ลูกตาเอง
   - IDLE / LISTENING / SPEAKING / HAPPY ใช้ `kinematics` ตรง (`drawKinematicEyes`, `drawHappyEye(back, front)`)
5. Crossfade อารมณ์ 280 ms และ fade eye trick 200 ms ผ่าน `withLayerAlpha` (offscreen layer เฉพาะตอนจาง) — renderer วาดทึบเสมอ (`effAlpha = 1`)

## กฎที่ต้องรักษา
- **ห้ามอ่านค่าที่ขยับทุกเฟรมใน composition** (เช่น `living.*`, `by animateFloat` ที่เป็นลูป) — อ่านใน `Canvas`/`graphicsLayer`/`offset {}` เท่านั้น
  ไม่งั้น avatar recompose 60 ครั้ง/วินาที
- **ห้ามใช้ `(x + progress * k) % 1f` กับ k ไม่เป็นจำนวนเต็ม** — วาร์ปทุกครั้งที่ลูปเริ่มใหม่ ใช้ลูปแยกของตัวเอง (`loopWink`, `loopLaugh`, …) หรือ `LayerClock` + `loopFrac`
- **ส่งพิกัดเบ้าตาให้ `drawDualCircleEye(centerX, …, gazeX…)`** — ส่ง `lookCenter` จะบวกสายตาซ้ำ
- Path ต่อเฟรมใช้ `EyePaths.obtain()` (pool 32 ตัว) — ห้ามเก็บ path ข้ามเฟรม
- สายตา: ไม่ใช้ `GazeStabilizer` ใน UI อีกแล้ว (กล้องกรองใน `PetVisionDetector` แล้ว; spring ไล่ถึงเป้าเสมอ)

## การกะพริบตา
`effectiveBlink` ส่งให้ IDLE/LISTENING/SPEAKING (ผ่าน kinematics), HAPPY, SAD, MUSIC, VR_MODE, DIVING, SHY, THINKING, POUT
ไม่กะพริบโดยตั้งใจ: SLEEPING, WINK, DIZZY, DEAD, LAUGHING, DISGUSTED, SURPRISED, ENRAGED, EVIL, BORED (มีเปลือกตาของตัวเอง)

## Sensor / controller ที่เกี่ยวข้อง
- `pet/PetMotionClassifier.kt` (pure, commonMain) — shake/heavy shake/boat rocking/table thump/face-down/up จากเวลา monotonic;
  `androidMain/.../PetMotionDetector.kt` แค่ต่อเซนเซอร์
- `PetModeController.showTransientEmotion` — ทุกปฏิกิริยาชั่วคราว (hand gesture, pout, wink, mimic) ใช้ generation guard
- `PetNeedsState.decayLive` — ช่วงห่างเกิน 60 วิ (Doze) คิดแบบ offline
- `PetModeScreen` ล้าง eye trick ที่ค้างเกิน 4.8 วิ (trick จาก AI tool ไม่มีใครล้างให้)

## ปัญหาที่ยังเหลือ (ยังไม่แก้ — ต้องตัดสินใจเรื่องดีไซน์)
- `EyeStyle.SPIRAL` → DIZZY วาดตา X (ตาก้นหอยมีแค่ใน SICK), `STAR` → EXCITED ไม่มีตาดาว, `QUESTION`/`CURIOUS` → CONFUSED เหมือนกัน
- renderer ของ moodset (PUZZLED … ROMANTIC) ยังไม่กะพริบตา
- เมื่อ Rive โหลดไม่ได้และ fallback มา Canvas ด้วย modifier ของ Rive (`requiredSize(riveSide)`) layout อาจเยื้อง (`LocalAvatarLayout` ยังเป็นของพื้นที่เต็ม)
- ใบหน้าที่ลงทะเบียน (`PetFaceProfile`) ไม่ถูกบันทึกถาวร, `PetFeatureTab` ไม่มี UI ใช้
- กล้อง Live ไม่ส่งเฟรมเมื่อย่อแอป
- ยังไม่ได้ทดสอบบนเครื่องจริงหลังแก้ (ดู checklist ใน log)
