# Pet Robot Lab — ตัวละครเกมแบบ Godot (ต้นแบบบนคอม)

หุ่นยนต์สัตว์เลี้ยงในรูปแบบ "ตัวละครเกม": ลอยได้ มีท่าทางตามอารมณ์ ตาสดบนจอ เปลี่ยนชุด เปลี่ยนฉาก
สร้าง 2026-09-30 (ดู [[log]]) · ต่อจาก [[Pet_Robot_3D]] (ตัวหุ่นจาก Blender) · ผู้ใช้เลือก "Godot: ต้นแบบบนคอมก่อน"
**เป็น Engine เดียวของแอปแล้ว (2026-10-01)** — Avatar mode รันโปรเจกต์นี้ใน process แยก ดู [[Avatar_Mode]] และ "รอบ 8" ด้านล่าง

## ภาพรวม pipeline
```
reference/robot_model_sheet_v2.jpg, robot_views_closeup.jpg   (ชีทของผู้ใช้ — วัดสัดส่วนด้วยตาราง)
        │
rive_avatar/blender/robot_build.py    โมเดล (subdivision cage ทั้งหมด, quad, UV, จอ = object แยก UV 0..1)
rive_avatar/blender/robot_game.py     pivot (empty) + 8 คลิป (NLA track) + อุปกรณ์ 7 ชิ้น + export glTF
rive_avatar/blender/export_game.py    runner แบบ blender -b (ห้าม export ผ่าน MCP — ดูกับดัก)
        │  robot.glb (2.9 MB) · accessories/*.glb · robot_meta.json
pet_godot/                            Godot 4.7.2 (Mobile renderer) — PetLab.bat / PetLabEditor.bat
```

## โครง rig (ไม่มี skin — แต่ละชิ้นลอยแยกกันอยู่แล้ว)
`PetRobot` (เกมหมุนตาม gaze) → `PR_Float` (ลอย/เด้งทั้งตัว) → `PR_HeadPivot` (จุดคอ, ตามช้ากว่าตัว) → หัว จอ หู + `PR_AntennaPivotL/R`
· `PR_BodyPivot` (อก) → เสื้อกั๊ก ท้อง แกนไฟ หมุดแผ่นหลัง + `PR_ArmL/R` (ไหล่ — **กระดูกเดียวต่อแขน**) → `PR_ArmMesh*` + `PR_HandSocket*`

คลิป (30 fps, ชื่อลงท้าย `_loop`): Idle 2.4 s · Happy 1.6 · Wave 1.6 · Sad 3.2 · Surprised 2.0 · Love 2.0 · Sleep 4.0 · Talk 1.6
— คำนวณจากฟังก์ชัน `_pose(t, clip)` (sin หลายเฟส: หัวตามช้ากว่าตัว, แขนแกว่งคนละเฟส, เสาอากาศสั่น)

## อุปกรณ์ (map กับ `PropType` ในแอป)
| ชิ้น | ช่อง | ตาม pivot | PropType |
|---|---|---|---|
| crown / party_hat / wizard_hat / chef_hat | hat | PR_HeadPivot | CROWN / PARTY_HAT / WIZARD_HAT / CHEF_HAT |
| sunglasses | face | PR_HeadPivot | SUNGLASSES |
| headphones | ears | PR_HeadPivot | HEADPHONES |
| backpack | back | PR_BodyPivot | BACKPACK |
โมเดลวางบนท่ายืน (rest) ใน world space → Godot ใส่ไว้ใต้ pivot ที่ตำแหน่ง `-pivots_rest[pivot]` (จาก `robot_meta.json`)

## Godot (`pet_godot/`)
- `scripts/pet_character.gd` — API: `set_emotion` (normal/happy/sad/wink/love/surprised/sleepy/angry/talk → คลิป + ตา + สีตา), `wear/take_off/toggle`, `look(Vector2)`, `poke()`; กะพริบเอง, ปากตอนพูด, เงาวงรีหดตามความสูงที่ลอย
- `shaders/robot_eyes.gdshader` — SDF บน UV ของ `PR_Screen`: ตากลม, ^ ^, เศร้า, ขยิบ, หัวใจ, ตกใจ (+ปาก o), ง่วง, โกรธ, มุม UI, scanline
- `shaders/backgrounds.gdshader` — studio / night / sunny / cyber / sakura / space (CanvasLayer -1 + `BG_CANVAS`)
- `scripts/main.gd` — เวที (AgX, glow, ไฟ 3 ดวง, ท้องฟ้าให้แสงสะท้อน), แผงปุ่มภาษาไทย (SystemFont Leelawadee UI), เมาส์ = สายตา, คลิก = poke;
  CLI `--shot=… --state=emotion,acc+acc,bg --wait= --hide-ui`
- `tools/inspect.gd` — headless: พิมพ์ตำแหน่งชิ้นส่วน/อุปกรณ์ใน world (ใช้จับบั๊กตำแหน่งอุปกรณ์)

## กับดักที่เจอ (อย่าพลาดซ้ำ)
- **Blender 5.2 แครช** (EXCEPTION_ACCESS_VIOLATION ใน `BKE_base_eval_flags`) เมื่อเรียก `export_scene.gltf` ภายใต้ `context.temp_override` → เรียก operator ตรงๆ
  และรัน export ด้วย `blender -b` (ผ่าน MCP ก็ timeout เพราะใช้เวลานาน)
- **object ที่ `hide_viewport=True` ไม่ถูก depsgraph คำนวณ** → `matrix_world` ค้างค่าเก่า (อุปกรณ์ถูก export ในมุมของภาพพรีวิวล่าสุด) → เปิดแสดงแล้ว `view_layer.update()` ก่อนอ่าน
- `_reparent` ต้อง `view_layer.update()` หลังตั้ง `matrix_world` ไม่งั้นลูกๆ ใช้ world เก่า (เคยทำให้จุดศอกไปอยู่สูง 1.57)
- `bmesh.ops.extrude_face_region` **ไม่ลบหน้าเดิม** → ต้อง delete เอง (เคยทำให้ช่องจอถูกปิด)
- glTF exporter ข้าม object ที่ซ่อน → `_select_only` เปิด `hide_viewport` ของที่เลือก
- shader hash แบบ `sin(x)*43758` เป็นบล็อกบน Mobile renderer → ใช้ hash ของ Dave Hoskins

## สัดส่วนที่วัดจากชีท (1 ช่อง ≈ 0.097 หน่วย, สูงรวม 1.88)
หัว 1.15 × 0.90 ลึก ~0.9 ลอยเหนือเสื้อกั๊กเล็กน้อย · จอ 76% × 63% ของหัว · ตากลม ~41% ความสูงจอ · จานหู r 0.16 ·
เสาอากาศบางจากหน้า กว้างจากข้าง ปลายมน

**แขน (2026-09-30, `reference/arm_component_sheet.jpg`)**: ชิ้นเดียว 2 สี กระดูกเดียว ลอยข้างลำตัวไม่ติดเสื้อกั๊ก —
ท่อทรงรี sweep ตามเส้น Bezier (`ARM_PATH`: ออกจากไหล่ทแยงลงแล้วโค้งห้อย) หน้าตัดบาง 0.27 จากหน้า / 0.4 จากข้าง, ปลายทั้งสองมนทู่,
ส่วนบนฟ้า (material 0) ส่วนล่างขาว (material 1) แบ่งที่ `ARM_SEAM` มีขอบฟ้ายื่น (`ARM_LIP`) · เสื้อกั๊กมีแผ่นหลังรูปโล่เรียวลง + หมุด 3 ตัว
ด้านข้างหัว: หน้าตรงมุมเล็ก หลังม้วนมนใหญ่ (`HEAD_EDGE_BACK`, `HEAD_BACK_TAPER`)

**จอนูน (2026-09-30, ผู้ใช้: "กรอบดวงตาสีดำควรนูนโค้งออกมา ไม่ยุบเข้าไป")**: ขอบเทายกขึ้นจากหน้า 0.012 (`SCREEN_DEPTH` ติดลบ) และกระจกเป็นโดมนูนแบบ CRT
`glass_bulge(u, v) = SCREEN_BULGE · (1-u²)^0.7 · (1-v²)^0.7` (0.075 ตรงกลาง, 0 ที่ขอบ) — UV 0..1 เดิม, ตาในภาพรีวิว/มุม UI/แว่นดำขยับออกตามโดม

## รอบ 3 (2026-09-30): ชุดเกราะ, เด้งดึ๋ง, สีชุดตามอารมณ์
ผู้ใช้: "ผิวมันเงาเกินไป ให้ออกด้าน · แขนไม่ทะลุหัว · ชุดสีฟ้าปรับสีตามอารมณ์ · น่ารัก กวนๆ เด้งดึ๋ง ·
อุปกรณ์แบบ Iron Man: มือเปลี่ยนเป็นปืนใหญ่ยิงจรวด เลเซอร์ ไฟฉาย · ลำตัวเป็น jetpack ผ้าคลุม · อุปกรณ์หัว · ฉากเพิ่ม"

**ผิวกึ่งด้าน**: ขาว rough 0.46 coat 0.1, ฟ้า rough 0.55 coat 0.05 (เดิม coat 0.7 เงาเหมือนกระจก) · กระจกจอยังเงา

**แขนไม่ทะลุหัว**: `check_arms.py` (blender -b) ไล่ทุกเฟรมทุกคลิป เทียบ vertex แขนกับกล่องหัว/จานหู/เสาอากาศใน space ของหัว
— เจอ 6 คลิปชน → ย้ายจุดหมุนแขนเข้าไปกลางส่วนฟ้า (`ARM_PIVOT` 0.6, 0.83: ยกแขนแล้วปลายบนหมุนลงแทนเหวี่ยงเข้าหัว), ลดแขนลง 0.05,
จูนมุมคลิป (Happy 90°, Wave 86°) → ผล "clear" ทุกคลิป (เผื่อระยะ 3 ซม.) · เพิ่มคลิป `Fly_loop`

**อุปกรณ์ใหม่ `robot_gear.py`** (merge เข้า `ACCESSORIES`, export glb ละชิ้น, meta มี `kind` / `tool` / `side` / `muzzle` / `nozzles` / `wave` / `spin` / `bob`)
| หมวด | ชิ้น | ช่อง | ใน Godot |
|---|---|---|---|
| มือ (ซ้าย/ขวา) | cannon, laser, flashlight, shield | hand_l / hand_r | ประกอบเข้ามือ (ขยาย+บิด+แฟลช), ยกแขนเล็ง `TOOL_AIM`, `use_tool(side)` |
| หลัง | jetpack · cape (SUPERHERO_CAPE) | back · cape | ไฟพ่นที่ `*_nozzle`, `toggle_fly()` · ผ้าพลิ้ว `cape_wave.gdshader` (ลมมากขึ้นตอนบิน) |
| หัว | propeller_cap (ใบพัดหมุน), cat_ears, halo (ลอยขึ้นลง), devil_horns, viking_helmet, astronaut_helmet (โดมแก้ว), fedora, vr_headset | hat / face | |
เครื่องมือวางตามเส้นแกนแขนจริง (`_hand_frame` ที่ t 0.8 ของ `ARM_PATH`) ทำทั้งซ้ายขวา

**เอฟเฟกต์ (`effects.gd`)**: จรวด (พุ่งขึ้นโค้งเข้ากลางจอ + ควัน + ระเบิดลูกไฟ/แสง/สะเก็ด), เลเซอร์ (ลำแสง 2 ชั้นเล็งจุดหน้าหุ่น),
ไฟฉาย (SpotLight + กรวยแสง `light_cone.gdshader` ส่องเฉียงลงหน้า), โล่ (`energy_shield.gdshader` ลายรังผึ้ง + ขอบเรือง), ไฟพ่น jetpack —
อนุภาคเป็นจุดกลมนุ่ม (`soft_dot()` GradientTexture2D) ไม่ใช่สี่เหลี่ยม

**สีชุดตามอารมณ์**: material `PR_LightBlue` + `ACC_SuitBlue` ใช้ตัวเดียวกัน (`_suit_mat`) → tween สี: ปกติ ฟ้า · ดีใจ เหลือง · เศร้า คราม ·
ขยิบ มิ้นต์ · รัก ชมพู · ตกใจ ฟ้าอมเขียว · ง่วง ลาเวนเดอร์ · โกรธ แดง · พูด ฟ้าสด; แกนไฟที่อกตามสีตา · `set_suit_color()` / `suit_follows_emotion`

**เด้งดึ๋ง/กวน**: AnimationPlayer โหมด manual (`advance`) แล้วซ้อนชั้น procedural ทุกเฟรม — ยืดหดตัว (spring จากความเร็วลอย), หัวสปริง,
เสาอากาศสปริง (ตามการลอย+การหันมอง), `boing()` ตอนเปลี่ยนอารมณ์/ใส่ของ/ยิง (แรงถีบ), ท่ากวนสุ่มเอง 5–9 วิ:
hop · spin (หมุนตัว 360°) · tilt · wiggle · peek · cheeky (ขยิบตา+เอียงหัว+กระโดดเบาๆ) · nod (พยักหน้า 2 ครั้ง — แอปสั่งตอน AI ส่ง gesture=nod, ไม่สุ่มเอง); คลิกหุ่น = ตกใจเด้ง

**ฉาก 12 แบบ**: + ใต้ทะเล (caustics/แสง/ฟอง), เมืองยามค่ำ (ตึก 2 ชั้น หน้าต่างกะพริบ), ทะเลยามเย็น, หิมะ, แคนดี้, ห้องแล็บ (hangar)

**กับดักรอบนี้**: จอแนวตั้งแคบมาก (ครึ่งจอกว้าง ~1.2 หน่วย) ยิงออกข้าง = หลุดจอทันที → เล็งเข้าหากล้อง/ยิงขึ้น/เล็งจุดหน้าหุ่น ·
ลำดับหมุนแขน `Qx(fwd) * Qz(out)` · lab บางรอบค้างตอนปิด (หลังเปิดไฟฉาย) → `tools/capture.ps1` มี timeout ·
`get_parent()` อาจเป็น Window (ใน inspect) → `_world()` · dictionary key tween (`"_aim:L"`) ไม่ปลอดภัย → ตัวแปรแยก `_aim_l/_aim_r`

## รอบ 4 (2026-09-30): บทละคร (skits) — พร้อมให้สมอง AI เลือก/เขียนเอง
ผู้ใช้: "สร้างบทละคร แสดงฉากต่างๆ 8-10 วินาที เน้นมีมๆ ตลกๆ น่ารัก เคลื่อนไหวได้ 360 องศา ขยับเข้ามาใกล้หรือถอยออกไปห่าง ไม่จำกัด ·
อนาคตจะใส่สมอง AI เข้าไปในหุ่น — ซีนว่างงาน เบื่อ โกรธ ความรัก กำลังคิด สงสัย ง่วงนอน อื่นๆ"

**ฉาก = ข้อมูล JSON** (`pet_godot/scenes/<id>.json`, สเปก + คำสั่งทั้งหมดใน `scenes/SCHEMA.md`) → AI เลือกจาก `list()` (mood/tags/description) แล้ว `play(id)`
หรือเขียนฉากใหม่ส่ง `play_json(text)` ได้เลย · beat = `{t, do, ...}` รันตามเวลา
- `scripts/scene_player.gd` (ผู้กำกับ): caption มีม (ตัวขาวขอบดำด้านบน) · บับเบิลพูดตามหัว · move/orbit/turn/roll/pitch/lean/hop/vibrate · หันตามทางเดิน
  แล้วหันกลับหากล้องเอง (`"then": "keep"` ถ้าเดินต่อ) · `turn yaw` ไปทางสั้น · ease มี `_in/_out` · กล้องสั่น/ซูม · จบฉากพาหุ่นกลับบ้าน คืนชุด/อารมณ์/สี
- `scripts/emotes.gd`: สัญลักษณ์ลอย ♥ ? ! z Z Z ♪ ✦ ★ … ⚡ + 💢 (เต้นตุบๆ) + เหงื่อ · หลอดไฟไอเดีย · กระดาษโปรย · ฟองน้ำมูก · หัวใจปาใส่จอ ·
  ดาววนรอบหัว · เมฆฝนตามหัว · แบตเตอรี่ (กะพริบแดงเมื่อต่ำกว่า 20%) · `effects.gd` เพิ่มจรวดยิงตรงใส่เลนส์ (`rocket_at_viewer` → จอวาบ)
- ตาใหม่ 3 แบบ (`EYES` + `set_eyes`): dizzy ก้นหอยหมุน @ @ · cry T T น้ำตาไหลเป็นสาย · squint > <
- `pet_character.gd`: `body_roll/body_pitch` (ตีลังกา/กลับหัวรอบกลางตัว `BODY_CENTER` 1.0), เงาอยู่บนพื้นเสมอ, `scene_active`, `muzzle_position()`
- โล่พลังงานย้ายมากางหน้าตัว (แขนเล็งเข้า `TOOL_AIM shield [-24, -80]`, โล่ตามแนวแขน) — เดิมยื่นออกข้างแขนหลุดจอแนวตั้ง

**12 ฉาก**: idle_play · bored · angry · love · thinking · curious · sleepy · sad (ใหม่: เมฆฝน T T) · low_battery (ใหม่: แบต 1% → ชาร์จ) · party · hero · dizzy
— ตรวจด้วย `tools/scene_frames.ps1` (เฟรมทุก 1 วิ + contact sheet), อัดคลิปด้วย `tools/record_scenes.ps1 -Reel` (720×1280 MP4 + รวมทุกฉาก),
ทดสอบผู้กำกับแบบ headless `tools/scene_test.gd` (31 ข้อ: catalogue, ตัดฉากกลางคัน, JSON ที่ AI เขียนมีคำสั่งแปลก → ข้ามแล้วเล่นต่อ, หุ่นกลับบ้านครบ)

**ตำแหน่งบนเวที** (กล้อง (0,1.35,6.8) fov 40 แนวตั้ง): บ้าน [0,0,0] · ข้างที่ปลอดภัย x ±0.5 · ขอบจอ x ±1.3 · close-up [0,-0.15,2.6] ·
หน้าชิดกระจก [0,-0.4,3.6–4.0] · ไกล z −1.5…−3 · บิน (+0.8 สูงเอง) ให้ใช้ y ต่ำ

**กับดักรอบนี้**: `_ease()` ต้องรับ PropertyTweener/MethodTweener (ไม่มี base type ร่วม) → ไม่ใส่ type · `--write-movie` ไม่สน `--resolution` เมื่อมี
window override → เขียน `override.cfg` ชั่วคราวแล้วลบทิ้ง (อยู่ใน .gitignore) · ไม่มี ffmpeg ในเครื่อง → ใช้ Blender VSE (มี FFmpeg ในตัว) แปลง AVI → MP4
(ตั้ง view transform = Standard ไม่งั้นสีเพี้ยนจาก AgX) · Label3D ขอบดำตัวใหญ่ใกล้กล้องกลายเป็นก้อนดำ → ขอบสีอ่อน · Zzz ต้องไล่ขั้นบันได

## รอบ 5 (2026-09-30): ฉากข้อมูล — การ์ดโฮโลแกรม 3D กับข้อมูลจริง
ผู้ใช้ถามว่าเรื่องไหนควรทดลองก่อนรวมแอป (เต็มจอ, กล่องข้อความ 3D/กราฟ, เซ็นเซอร์, overlay) → แนะนำ: ทดลองก่อนเฉพาะที่อยู่ใน Godot
(กล่อง 3D + ตาราง reaction เซ็นเซอร์), เต็มจอทดสอบด้วย APK แยก, **overlay ใช้ sprite ไม่ใช่ Godot** (Godot 1 engine ต่อแอป + ต้องมี Activity,
กินแบตถ้าลอยทับแอปอื่นตลอด; แอปมี `FloatingWidgetService` 2D อยู่แล้ว) · ผู้ใช้: "ถามสภาพอากาศวันนี้ → ฉากแสดงอากาศพร้อมอุณหภูมิที่ถูกต้อง ·
ถามงบดุล MSFT → ฉาก Pie Chart / แผนภูมิงบการเงิน · กล่องข้อความ 3D แสดง markdown สั้นๆ"

**ฉากข้อมูล (มี `needs`)**: แอปรัน tool เดิมของตัวเองแล้วส่งผลให้ `director.play(id, data)` — ตัวเลขมาจาก tool เสมอ, AI เขียนแค่ข้อความเสริม (`advice`/`summary`)
- `weather_report` (Open-Meteo แบบเดียวกับ `DeviceControlExecutor.executeWeather`): การ์ดอากาศ + ไอคอน 3D (ดวงอาทิตย์/พระจันทร์/เมฆ/ฝน/ฟ้าร้อง/หิมะ/หมอก),
  ฉากหลังตามกลางวัน-คืน, ฝน/หิมะทั้งฉาก, หุ่นทำท่าตาม `situation` (thunder → rain → snow → hot → cold → rain_likely → fog → nice → cloudy)
- `stock_balance` (`StockFundamentalData` จาก TradingView scanner แบบ `TradingApiService`): เสา 3D งบดุล (สินทรัพย์ = หนี้สิน + ทุน) → โดนัท 3D
  โครงสร้างเงินทุน → แท่ง 3D รายได้/กำไรสุทธิ/FCF (TTM) → กล่องสรุป markdown · หุ่นใส่เฟโดร่าชี้อธิบาย
- `answer_card`: การ์ด markdown ทั่วไป (`title, markdown, say`)
- ตัวอย่างข้อมูลจริง `data_samples/` (ดึงเมื่อ 2026-09-30 18:39: กรุงเทพ 28.5°C มีเมฆมาก ฝน 73% · MSFT สินทรัพย์ $758.4B หนี้สิน $316.0B ทุน $442.4B)
  — ดึงใหม่ด้วย `tools/save_samples.gd` (รอบ 6 แทน `fetch_info_data.ps1`) · แท็บ **ข้อมูล** ใน lab ดึงสดผ่าน `info_sources.gd` (HTTPRequest)

**ระบบ**: `holo_panel.gd` (weather/balance/pie/bars/kpi/text + `derive()` ข้อมูลเสริม + รูปแบบเงิน `$758.4B`) · `holo_card.gdshader` (กระจก ขอบเรือง
สแกนไลน์ ปรากฏจากล่างขึ้นบน) · ผู้กำกับ: เติม `{temp:0}` `{totalAssets:money}`, เงื่อนไข `"if": "situation == rain_likely"` (ภาษาเล็กๆ ไม่รันโค้ด),
beat `panel` / `panel_close` / `present` (ยกแขนชี้การ์ด + ลำแสงโปรเจกเตอร์), บับเบิลย้ายข้างหัวเองเมื่อมีการ์ด · markdown → BBCode ใน SubViewport
· ทดสอบ headless 46/46 (`scene_test.gd` รวมฉากข้อมูล) + `info_live_test.gd` (แหล่งข้อมูลสดยังตอบ)

**กับดักรอบนี้**: Label3D จัดซ้ายแล้วยังอยู่กลาง origin → คำนวณความกว้างด้วย `get_string_size` เอง · ตัวหนังสือบนการ์ดต้องสูง ≥ 0.07 ม. ไม่งั้นอ่านไม่ออกบนมือถือ ·
`.ps1` ที่มีภาษาไทยต้องบันทึกแบบ UTF-8 **มี BOM** (PowerShell 5.1) · หน้าต่าง 720×1280 บนจอ 1080p ถูกย่อเป็น 720×1061 → canvas กว้าง 868 →
บับเบิลในวิดีโอตกขอบ → อัดแบบไร้ขอบ 576×1024 · `_ease`/Label3D: ตัวแปรจาก Array/Dictionary ต้องใส่ type เอง (`:=` เดาไม่ได้)

## รอบ 6 (2026-09-30): ทุก tool มีฉากของตัวเอง (`play_tool`)
ผู้ใช้: "ประมาณนี้แหละที่ต้องการ คือเป็นฉากที่รับข้อมูลสดมาแสดงได้ โดยแสดงผลสอดคล้องกับ tool ที่เรียกใช้งาน"

**`director.play_tool(tool, result, extra)`** — แอปส่งผลที่ tool คืนมา "ตามที่เป็น" (map / list ของแถว / ข้อความ) → `scenes/tool_scenes.json`
เลือกฉากประจำ tool (tool เดิม = หน้าตาเดิมทุกครั้ง) · tool ที่ไม่มีฉากเฉพาะ → การ์ด markdown · AI ใส่ได้แค่ `advice`/`summary` ผ่าน `extra`
| tool ของแอป | ฉาก |
|---|---|
| device_weather | weather_report |
| trading_fundamental_analysis | stock_balance |
| trading_price | price_report — ราคาใหญ่นับขึ้น ▲▼ เขียว/แดง ปิดก่อนหน้า แถบช่วง 52 สัปดาห์ |
| trading_technical_analysis | technical_report — หน้าปัดสัญญาณรวม → หน้าปัด RSI 30/70 → ช่อง MACD/ADX/ATR/RSI/BB/ราคาปิด |
| trading_deep_analysis_suite | deep_analysis_report — หน้าปัดคะแนนรวม 0–100 (สีตามทิศ LSD) → 4 มิติ (LSD/Orderflow/Momentum/Fibo) → แท่งความแรง Fibonacci (ทอง = Hot) (2026-10-02) |
| trading_multi_timeframe | mtf_report — แท่งสัญญาณรายไทม์เฟรม → ช่องสัญญาณ + RSI |
| trading_sentiment | sentiment_report — หน้าปัด 0–100 → แท่ง 4 เสาหลัก → สัดส่วน Long รายย่อย/รายใหญ่ + Taker |
| trading_smc_analysis / structure | smc_report — ตำแหน่งราคาในกรอบ (Discount/Premium) → เทรนด์/BOS/ระดับ/FVG/OB → สภาพคล่องใกล้ราคา |
| trading_market_snapshot | market_snapshot_report — ช่องดัชนีหลัก → แท่ง % วันนี้ |
| trading_top_gainers / losers + สแกน overbought/oversold/volume/bollinger | market_movers — แท่ง 3D 5 เหรียญ (คู่ USDT, ไม่ซ้ำเหรียญ; สแกน RSI ใช้ค่า RSI) ท่าทางตามรายการ (`react`) |
| trading_fear_greed | fear_greed_report — หน้าปัด 0-100 ห้าโซน + แท่ง 7 วัน |
| trading_crypto_overview | crypto_overview — ช่องตัวเลข → โดนัท dominance |
| device_battery_status | battery_report — หน้าปัดแบต + แบตลอยเหนือหัว (แอปต้องเพิ่ม `level`, `charging` เพราะตอนนี้คืนแค่ข้อความ) |
| ข้อความ (news, ปฏิทิน, สัญญาณกลยุทธ์, SMC Flow, search, …) / อื่นๆ | answer_card — การ์ดยาวโตขึ้นด้านบน ไม่บังหัวหุ่น · ผลเป็นข้อความ (สแกนว่าง/error) ไปการ์ดเสมอ |
| (ระหว่างคำตอบ) | `listening` / `pondering` / `working` — ฉากวน (`"loop"`) จนแอปสั่งหยุด ดู [[Avatar_Mode]] |

- panel ใหม่: `gauge` (หน้าปัดครึ่งวง 3D เข็มสปริง), `price` · `holo_panel.num()` อ่านตัวเลขจากข้อความแบบที่ tool เทรดของแอปคืน ("83806.8000", "0.17%", "N/A")
  → รูปแบบ `{price:price}` `{close:group}` `{change_pct:pct+}` (ต่ำกว่า 1% แสดง 2 ตำแหน่ง ให้ตรงกับการ์ด) · key ที่มีจุด (`MACD.macd`) ใช้ตรงๆ ได้
- ตัวอย่างข้อมูล `data_samples/<tool>.json` (ผลจริงของแต่ละ tool) · `tools/save_samples.gd` ดึงสดทั้งหมดผ่าน `info_sources.gd` (แหล่งเดียวกับแอป:
  Open-Meteo, TradingView scanner, alternative.me, CoinGecko) · แท็บ **ข้อมูล** = ปุ่มต่อ tool (สด) · CLI `--tool=<name> [--data=]`
- บับเบิลยาวเกิน 440 px ตัดบรรทัดเอง (หรือใส่ `\n` เอง — ICU ตัดคำยืม "เทรนด์" ผิด)
- ทดสอบ headless 76/76 (รวมทุก tool ใน registry ชี้ไปฉากที่มีจริง, ตัวเลขจากข้อความ, movers ไม่ซ้ำเหรียญ, ข้อความ → การ์ด)

## รอบ 7 (2026-09-30): ทดสอบบนมือถือจริง (APK แยก ไม่แตะแอป)
ผู้ใช้ถาม "ควรทำฉากต่อ หรือเอารวมกับ app ก่อน" → แนะนำรวมแบบเป็นขั้น: 1) APK แยกทดสอบบนมือถือ 2) ฝังเป็น engine ที่ 4 ใน PetModeScreen (feature flag)
3) bridge + 2–3 tool 4) ที่เหลือ — ผู้ใช้อนุมัติขั้น 1 (รวมดาวน์โหลด export templates 1.28 GB → ติดตั้งเฉพาะ Android, ลบไฟล์ดาวน์โหลดแล้ว)
- `export_presets.cfg` (arm64 debug, `com.skyliner2008.petlab`, แนวตั้ง, immersive, internet, `include_filter="*.json"` — ไฟล์ JSON ไม่ใช่ resource ต้องใส่เอง)
- ETC2/ASTC import เปิด, ไอคอน `icon.png` (หน้าหุ่นยิ้ม), keystore debug Godot สร้างเองใน `%APPDATA%\Godot\keystores`
- กล้องแนวตั้ง `KEEP_WIDTH` 23.14° (= 40° แนวตั้งที่ 9:16) → จอ 19.3:9 ของ S22 Ultra ไม่โดนตัดข้าง · `director.sync_camera_home()` · `zoom` ปรับตามกล้องจริง
- **ผลบน SM-S908E** (Adreno 730, Vulkan Mobile, 1440×3088): APK 43.6 MB, ฟอนต์ไทย/อีโมจิขึ้นเองจากฟอนต์ระบบ Android, ปุ่มข้อมูลสดใช้ได้ผ่านเน็ตมือถือ
  (BTC $85,268.70 +1.92%) · ครั้งแรก 120 fps แต่ตกถึง 17–48 fps ตอนเอฟเฟกต์โผล่ครั้งแรก, VRAM ~420 MB
- แก้: จำกัด 60 fps บนมือถือ (`run/max_fps.mobile`), 3D 75% (`scaling_3d/scale.mobile`), `_warm_up()` สร้างเอฟเฟกต์/การ์ดทุกแบบจิ๋วๆ หลังหุ่นตอนเปิด
  → **ทุก 21 ฉาก 55–60 fps**, VRAM ~350 MB · PERF log ทุก 2 วิใน logcat
- กับดัก: `am start --esa command_line_params` ส่ง args ไม่ถึงเกม → ใช้ `adb shell input tap` · ปุ่มใน TabContainer กดผ่าน adb ได้ปกติ

## รอบ 8 (2026-10-01): เข้าแอปเป็น Engine เดียว — Avatar mode
ผู้ใช้: "ลบ engine เดิมทั้งหมด ให้มี godot แค่ engine" + พื้นหลังหลักสีดำ + Pet mode → **Avatar mode** ไม่มีปุ่ม แต่ยังตอบสนอง
กล้อง ไจโร เขย่า จิ้ม · Overlay/Drive ใช้ภาพจากหุ่น Godot — รายละเอียดสถาปัตยกรรมอยู่ที่ [[Avatar_Mode]]
- **Engine แบบ remote ของ Godot 4.7**: `GodotService` ใน process `:avatar` + `RemoteGodotFragment` (SurfaceControlViewHost)
  เพราะ Godot สร้าง engine ใหม่ใน process เดิมไม่ได้ (ปิด = `destroyAndKillProcess`) — แยก process จึงเปิด/ปิดได้ไม่จำกัด
- ฝั่ง Godot: `scripts/app_bridge.gd` (คำสั่ง/เหตุการณ์ JSON, โซนการแตะ, เอียงตามแรงโน้มถ่วง), `pet_character.gd` เพิ่ม
  `speaking`/`voice_level`/`sway`/`bump()`/`reset_eyes()`, `scene_player.gd` เพิ่ม `say()`/`effect()`/`emote()`/`shake()` ใช้นอกฉากได้,
  `main.gd` โหมดแอป + `--app-sim` + `--sprites` (หน้าหุ่น 27 ภาพ + boot splash)
- project: `quit_on_go_back=false`, `enable_gravity=true`, boot splash = หน้าหุ่น, **frame pacing (Swappy) ปิด**
  (Swappy ขอค่าจาก Activity ที่ service ไม่มี → SIGABRT), เงาทิศทาง `SHADOW_ORTHOGONAL` 14 m + soft filter บนมือถือ (เดิมเงาเป็นบล็อก)
- ผลบน SM-S908E: เปิดโหมด→เห็นหุ่น ~3.5 วิ (process 1.1 + โหลด 2.2, ระหว่างนั้นเห็นหน้าหุ่นจาก boot splash), 60 fps
  (ต้องขอ `preferredRefreshRate=60` เอง — ไม่งั้นจอ Samsung ลดเหลือ 30 Hz), จิ้มหน้าผาก→สมองเลือก LOVE→หุ่นชุดชมพูตาหัวใจ,
  ละครปาร์ตี้, ปุ่มย้อนกลับปิดโหมด+process จบ, engine ถูกฆ่า→เปิดใหม่เอง 4 วิ, อยู่เบื้องหลัง 30 วิ→คืนหน่วยความจำ (~1.2 GB)
- ทดสอบ: `tools/scene_test.gd` เพิ่มเช็ก app bridge (state/gear/backdrop/voice/รอฉากจบ/โซนแตะ/ขนาดกล่องคำพูด) · แอป 526 เทสต์ผ่าน
- ตรวจซ้ำ (ผู้ใช้: "ตรวจสอบความถูกต้อง"): **ทิศเอียงกลับด้าน** — `Input.get_gravity()` ของ Godot คือค่า Android กลับเครื่องหมาย
  (ตั้งตรง = (0, -9.8, 0)) → มุม = `atan2(g.x, -g.y)` · `{"t":"stop"}` รับ `id` หยุดเฉพาะละครนั้น (ฉากข้อมูลของ tool ที่เข้ามาแทนเล่นต่อ)
  · scene_test เพิ่มเช็กทั้งสองอย่าง · ฝั่งแอปแก้อีก 4 จุด ดู [[Avatar_Mode]] "ข้อควรระวัง"

## ยังไม่ได้ทำ
- เชื่อมสมอง AI: แอปส่งอารมณ์/สถานการณ์ → LLM ตอบ `{"play": id}` หรือ `{"scene": {...}}` ตาม `scenes/SCHEMA.md` → bridge ส่งเข้า `director`
- ฉากข้อมูลเพิ่ม: ราคาหุ้น/คริปโตพร้อมกราฟเส้น, พอร์ต (pie สินทรัพย์), สัญญาณเทรด/อินดิเคเตอร์ (kpi + แท่ง) — ใช้ panel ที่มีอยู่แล้ว
- Custom prop (SVG ที่ AI วาด) ยังไม่ขึ้นบนหุ่น Godot (โหลดด้วย `Image.load_svg_from_string` แล้วติดเป็น Sprite3D ตามตำแหน่ง)
- เปิดโหมดเร็วขึ้น (ตอนนี้ ~3.5 วิ): เก็บ engine ไว้ระหว่างเปิด/ปิดบ่อยๆ หรือ warm-up ให้เบาลง
- แนวนอน: ขนาด surface ของ engine ตั้งครั้งเดียว — ตอนนี้ล็อกแนวตั้งระหว่าง Avatar mode
- ของถือ (ดาบ ไม้กายสิทธิ์ ตะหลิว) ใช้ `PR_HandSocket*` ได้ แต่ยังไม่ได้ทำโมเดล