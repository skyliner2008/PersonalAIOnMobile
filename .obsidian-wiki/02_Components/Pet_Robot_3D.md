# Pet Robot 3D — ตัวหุ่นจาก Blender + ตาสดจาก Canvas

> **ถอดออกจากแอปแล้ว (2026-10-01)** — ภาพ 49 เฟรมและโค้ดถูกลบ หุ่นตัวเดียวกันนี้ไปอยู่ใน Godot แทน
> (โมเดล Blender ยังเป็นต้นทางของ `pet_godot/assets/robot.glb`) ดู [[Avatar_Mode]] · หน้านี้เก็บไว้เป็นประวัติ

Engine ที่ 3 ของโหมดสัตว์เลี้ยง (`AvatarEngineType.ROBOT_3D`, เลือกใน PetSettingsDialog "🤖 หุ่น 3D")
ตัวหุ่นตามชีทของผู้ใช้ (`rive_avatar/blender/reference/robot_model_sheet.webp`): หัวขาวจอดำ หูฟ้า + เสาอากาศใบไม้ ตัวฟ้า-ไข่ขาว แกนไฟ cyan ที่อก แขนลอย
เพิ่มเมื่อ 2026-09-29 (ดู [[log]]) · อีกสอง engine: [[Pet_Avatar_Canvas]], [[Rive_Avatar_Engine]]

## หลักการ
- **ตัวหุ่นเรนเดอร์ล่วงหน้าใน Blender** เป็นภาพโปร่งใส ไม่มีตา (จอดำว่าง): 7 มุมหัน (−30°…+30° ทีละ 10°) × 7 ท่าแขน = 49 ภาพ WebP 640 px (~900 KB รวม)
- **ตาเป็นของ Canvas engine เดิมทั้งหมด** — `PetRobotHeadAvatar(COMPOSE_CANVAS)` ถูกวาดลงกล่องขนาดจอของหุ่น (`robot3dEyeLayout`) จึงได้ทุกอารมณ์/กะพริบ/มองตาม/eye trick โดยไม่ต้องทำใหม่
- gazeX → หมุนหุ่น (spring) · crossfade 2 เฟรมที่ใกล้ที่สุด · กล่องตาย้ายตามจุดกลางจอของเฟรม + `rotationY` = มุมหัน
- ลอยขึ้นลง ±7 dp เหนือเงาวงรี · gesture TILT เอียงทั้งตัว

## ไฟล์
| ไฟล์ | หน้าที่ |
|---|---|
| `rive_avatar/blender/robot_build.py` | **แหล่งความจริง**: สร้างโมเดลทั้งตัวด้วยโค้ด (superellipsoid + boolean), วัสดุตามพาเลตชีท, ไฟ/ฉาก cyclorama, `POSES` ท่าแขน, `render_views()` ภาพรีวิว 4 มุม, `render_sprites()` เฟรมแอป + เจน Kotlin |
| `rive_avatar/blender/pet_robot.blend` | ไฟล์ Blender ที่เซฟจากสคริปต์ (เปิดดู/ปรับมือได้ แต่ของจริงอยู่ในสคริปต์) |
| `rive_avatar/blender/renders/` | ภาพรีวิว front/side/three_quarter/back + `robot_views_sheet.png` |
| `composeResources/files/pet3d/{pose}_{m30..p30}.webp` | เฟรมที่แอปใช้ (**เจน ห้ามแก้มือ**) |
| `ui/component/avatar/PetRobot3dFrames.kt` | ตาราง YAWS / POSES / จุดกลางจอต่อมุม / ขนาดจอ (**เจน**) |
| `ui/component/avatar/PetRobot3dAvatar.kt` | composable + `robot3dPoseFor` + `robot3dYawBlend` + `robot3dEyeLayout` + โหลดเฟรม (เก็บแค่ท่าปัจจุบัน + idle ~23 MB) |
| `commonTest/.../PetRobot3dAvatarTest.kt` | 7 เคส: blend, clamp, จอตามมุม, ทุกท่ามีเฟรม, อารมณ์→ท่า, ตาอยู่ในจอ |

## อารมณ์ → ท่าแขน (ตามชีท Expression Key)
| ท่า | อารมณ์ |
|---|---|
| happy (ชูแขน) | HAPPY, LAUGHING, EXCITED, RICH |
| sad (แขนตก) | SAD, CRYING, SICK, BORED |
| hug (กอดหน้าอก — ชีท HEART) | LOVE, ROMANTIC, SHY |
| surprised (กางแขน) | SURPRISED |
| wave_a ↔ wave_b ทุก 320 ms (ชีท WINK) | WINK |
| idle | อื่นๆ |

## วิธีแก้หุ่น / เพิ่มท่า
1. เปิด Blender 5.2 (add-on MCP เปิด Online Access แล้ว) แก้ตัวเลขใน `robot_build.py` (สัดส่วน `HEAD`, `TORSO`, `BELLY`, `POSES`…)
2. รันใน Blender (ผ่าน Blender MCP หรือ Text editor):
   ```python
   ns = {}; exec(open(r"<repo>/rive_avatar/blender/robot_build.py", encoding="utf-8").read(), ns)
   ns["build"](); ns["render_views"](r"<repo>/rive_avatar/blender/renders")          # รีวิว
   ns["render_sprites"](r"<repo>/composeApp/src/commonMain/composeResources/files/pet3d",
                        r"<repo>/composeApp/src/commonMain/kotlin/com/skyliner2008/jarvis/ui/component/avatar/PetRobot3dFrames.kt")
   ```
   `render_sprites` ใช้เวลาหลายนาที (49 เฟรม EEVEE) — MCP อาจ timeout แต่ Blender ยังเรนเดอร์ต่อ ให้นับไฟล์ใน `pet3d/`
3. เพิ่มท่าใหม่ = แถวใน `POSES` + ค่าใน `Robot3dPose` + map ใน `robot3dPoseFor` (เทสต์จะล้มถ้าท่าไม่มีเฟรม)

## กฎ / ข้อควรระวัง
- ตาของหุ่นในไฟล์ Blender (`PR_Eyes`) ใช้ในภาพรีวิวเท่านั้น — `render_sprites` ซ่อนเสมอ (แอปวาดตาเอง)
- `SPRITE_CAMERA` เปลี่ยน = จุดจอใน `PetRobot3dFrames.kt` เปลี่ยน → ต้องเจนใหม่ทั้งชุดพร้อมกัน
- จอใช้วัสดุดำด้าน (`PR_Screen` coat ต่ำ) เพื่อให้ตาที่วาดทับดูเป็นจอเดียวกัน

> 2026-09-30: โมเดลใน `robot_build.py` เปลี่ยนเป็นรุ่น v2 (ตามชีท, แขนบ่ากาง) แล้ว แต่ภาพ sprite ในแอปยังเป็นรุ่นเดิมจนกว่าจะรัน `render_sprites` ใหม่
> ทิศทางต่อไปคือตัวละครเกมใน Godot — ดู [[Pet_Godot_Lab]]

## ยังไม่ได้ทำ / ต้องตัดสินใจ
- ยังไม่ได้ดูบนเครื่องจริง (ทิศ `rotationY` ของกล่องตาเทียบกับเฟรมต้องยืนยันด้วยตา)
- พร็อพ/ฉากหลังของ `PetPropsOverlay` ยังวางตามตำแหน่งหน้า OLED เดิม — หมวก/แว่นไม่ได้อยู่บนหัวหุ่น
- จุดสัมผัส (ลูบหัว/จิ้มแก้ม/เกาคาง) ยังคิดจากกรอบหัว 1.32:1 เดิม ไม่ใช่รูปหุ่น
- gazeY (ก้ม/เงย) ยังไม่มีเฟรม — มีแค่ตาในจอที่มองขึ้นลง
