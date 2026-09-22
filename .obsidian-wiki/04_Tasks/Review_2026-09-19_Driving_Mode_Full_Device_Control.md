---
title: Review — โหมดขับขี่ / การควบคุมมือถือ 100%
category: Tasks
tags: [review, driving-mode, device-control, accessibility, android]
sources: [JarvisAccessibilityService.kt, DeviceControlExecutor.kt, DeviceToolDefinitions.kt, accessibility_service_config.xml, AndroidManifest.xml, DriveModeScreen.kt, DriveBridge.kt, DriveModeController.kt, AlwaysLiveManager.kt, LiveToolBridge.kt, JarvisPersona.kt, LocationProvider.kt]
created: 2026-09-19
updated: 2026-09-19
---

# 🚗 Review — "โหมดขับขี่" ที่ควบคุมมือถือได้ 100%

ขอบเขต: (1) เปิดแอป (2) ควบคุมแอป (3) รู้ว่าอยู่หน้าไหน (4) รู้ว่าปุ่มอยู่ตรงไหน
+ UI โหมดขับขี่ในภาพ (`DriveModeScreen.kt` / `DriveModeContent`)

> ⚠️ ฉบับแรกของโน้ตนี้บอกว่า "UI ในภาพยังไม่มีในโค้ด" — **ผิดแล้ว** ตอนนี้มี
> `DriveModeScreen.kt` (782 บรรทัด), `DriveBridge`, `JarvisNotificationListener`,
> `LocationProvider` และสิทธิ์ `ACCESS_FINE_LOCATION` ครบ โน้ตนี้เขียนใหม่ทั้งหมดจากโค้ดปัจจุบัน

## สรุปคะแนน

| แกน | สถานะ | เหตุผลหลัก |
|---|---|---|
| เปิดแอป | 🟡 70% | ทำงานได้ แต่ map แอปแบบ hardcode ใช้กับ Samsung/OEM ที่ไม่ใช่ Pixel ไม่ได้ (§2.4) |
| ควบคุมแอป | 🟢 80% (หลังเฟส B) | กด/พิมพ์+ส่ง/เลื่อน/กดค้าง/ปัด/ลาก ครบ เหลือ pinch-zoom |
| รู้ว่าอยู่หน้าไหน | 🟢 85% (หลังเฟส B) | a11y tree ทุกหน้าต่าง + ภาพหน้าจอจริงผ่าน `device_screenshot` |
| รู้ว่าปุ่มอยู่ตรงไหน | 🟢 85% (หลังเฟส A) | ทุก element มีพิกัด `@(x,y)` รวมปุ่มไอคอน |
| UI โหมดขับขี่ | 🟢 75% | ครบตามภาพ มีบั๊ก layout + parking ไม่ถาวร + GPS หลุดเมื่อไป background |

## 1. บั๊กร้ายแรง (แก้ก่อน)

### 1.1 🔴 AI ไม่รู้พิกัดปุ่ม — `toSummary()` ทิ้ง `bounds`
`JarvisAccessibilityService.kt:61` — `traverseNode` อ่าน `getBoundsInScreen()` (`:227`)
แต่ `toSummary()` ไม่ใส่พิกัด AI ได้แค่ `ยืนยัน [คลิกได้] (id:…)` → `device_tap(x,y)` ใช้ไม่ได้จริง
**แก้:** ต่อท้าย `@(cx,cy)` จาก `bounds.centerX()/centerY()`

### 1.2 🔴 ปุ่มไอคอนหายจากสายตา AI — `textOnly = true`
`readScreen(textOnly = true)` (`:166`, `:178`) ทิ้ง node ที่ไม่มี text/contentDescription
ปุ่ม play, ปุ่มส่ง, ไอคอนแผนที่จำนวนมากจึงไม่อยู่ในผลลัพธ์
**แก้:** เก็บถ้า `hasContent || isClickable || isEditable` (+ ใส่ class สั้นๆ เช่น `ImageButton`)

### 1.3 🔴 Coroutine ค้างถาวร — tap พิกัด / scroll
`DeviceControlExecutor.kt:1245` และ `:1274` ใช้ `suspendCancellableCoroutine` รอ callback
แต่ `tapAtPosition()` / `swipe()` (`:292`, `:317`) **คืน `false` โดยไม่เรียก callback** เมื่อ
`dispatchGesture()` ล้มเหลว — และ `LiveToolBridge.kt:500` เรียก `ToolExecutor.execute` **ไม่มี timeout**
→ Live session ค้าง AI เงียบกลางการขับรถ
**แก้:** `if (!svc.tapAtPosition(...) { … }) cont.resume(false)` + ห่อ `withTimeoutOrNull(3000)`

### 1.4 🔴 Parking ใช้งานผ่านเสียงได้เฉพาะตอน AI "เรียก tool ผิด"
การบันทึก/ถามจุดจอดด้วยเสียงอยู่ใน guard ของ `LiveToolBridge.kt:316` ซึ่งทำงานเฉพาะเมื่อ
`canRedirectMisroutedTool()` = true คือ **Gemini ต้องเรียก trading tool ผิดก่อน** (`LiveIntentMatchers.kt:220`)
ไม่มี tool `device_parking` จริง → ถ้า AI ตอบเองโดยไม่เรียก tool จะไม่บันทึกอะไรเลย
และ `DriveBridge._parkingLocation` อยู่ใน memory อย่างเดียว — **ปิดแอป/process ตาย = หาย**
ปุ่ม "บันทึกจุดจอด" ยังบันทึก `0.0,0.0` ได้ถ้า GPS ยังไม่ fix

## 2. ช่องโหว่ด้าน "ควบคุม 100%"

1. **ไม่เห็นภาพหน้าจอจริง** — `takeScreenshot()` (`:141`) คือ `GLOBAL_ACTION_TAKE_SCREENSHOT`
   (เซฟลงแกลเลอรี) ไม่คืนภาพ ควรใช้ `AccessibilityService.takeScreenshot(displayId, executor, cb)`
   (API 30+) แล้วส่งเข้า pipeline ภาพเดิมของกล้อง → อ่าน Maps/WebView/Canvas ได้
2. **อ่านได้ window เดียว** — ใช้ `rootInActiveWindow` ทุกที่ ไม่มี `flagRetrieveInteractiveWindows`
   ใน `accessibility_service_config.xml` และไม่เรียก `getWindows()` → dialog/permission popup หลุด
3. **พิมพ์ได้แต่ส่งไม่ได้** — ไม่มี `ACTION_IME_ENTER` (API 30+); ควรเพิ่ม arg `submit` ใน `device_type_text`
4. **map แอป hardcode ชนะการค้นจริง** — `resolveAppPackage` (`:980`) คืน package จาก map ทันที
   เช่น `กล้อง → com.android.camera`, `นาฬิกา → com.google.android.deskclock` ซึ่งไม่มีบน Samsung One UI (เครื่องผู้ใช้ SM-S908E ใช้ `com.sec.android.app.camera`)
   → `launchByPackage` ล้ม และ **ไม่ fallback ไปค้นจาก label ที่ติดตั้งจริง**
   และการค้น label ใช้ `getInstalledApplications` (รวม system app ที่เปิดไม่ได้) + `contains` แบบหลวม
   ควรใช้ `queryIntentActivities(ACTION_MAIN/LAUNCHER)` แทน
5. **ไม่รอหน้าจอเปลี่ยน** — `device_open_app` คืนทันทีหลัง `startActivity()`; read_screen ต่อทันทีได้หน้าเดิม
   ควรรอ `TYPE_WINDOW_STATE_CHANGED` ของ package ใหม่ (timeout ~2 วิ) แล้วแนบสรุปหน้าจอในผลลัพธ์เลย
6. **ผลลัพธ์ read_screen ไม่มีเพดาน** — feed ยาวได้หลายร้อยบรรทัด → กิน context Live + latency
7. **ชุดคำสั่งไม่ครบ** — ไม่มี long-press, swipe ซ้าย/ขวา, scroll ใน node (`ACTION_SCROLL_FORWARD`),
   รายชื่อแอปที่ติดตั้ง
8. **Persona ไม่สอน loop** — `JarvisPersona.kt:174-177` map คำสั่งทีละ 1 tool ไม่มีกติกา
   "open_app → read_screen → tap → read_screen ยืนยัน" AI จึงไม่ทำงานหลายขั้นเอง
9. **Dead code / leak** — `findNodesByText` / `findNodesById` (`:198`, `:207`) ไม่ recycle และไม่มีใครเรียก;
   `DriveModeController` ทั้งคลาสไม่มีใครใช้ (UI เรียก `DriveBridge` ตรง)

## 3. โหมดขับขี่ (UI + Telemetry)

- **GPS หยุดเมื่อแอปไป background** — `device_open_app` / นำทาง จะ `minimize()` JARVIS
  แต่ manifest ไม่มี `FOREGROUND_SERVICE_LOCATION` และ FGS เป็น `specialUse` → Android 14+
  ไม่ให้อ่าน location จาก background ความเร็ว/ที่อยู่จะค้างค่าเดิมขณะใช้ Maps
- **Polling 4 วิ + Geocoder ทุกรอบ** (`AlwaysLiveManager.kt:306`) — reverse geocode ทุก 4 วิ
  เปลืองแบต/เน็ต ควรใช้ `requestLocationUpdates` ต่อเนื่อง + geocode เมื่อขยับเกิน ~200 ม.
  และ `getLastKnownLocation` อายุ ≤ 2 นาทีถูกนับเป็นค่าปัจจุบัน → ความเร็วอาจเป็นค่าเก่า
- **Layout ในภาพ**
  - ปุ่ม `🗺️ นำทาง Google Maps` (`DriveModeScreen.kt:652`) ตัดบรรทัด + อีโมจิชิดขอบ
    → ใส่ `maxLines = 1`, `overflow = Ellipsis` หรือย่อเป็น `🗺️ นำทาง`
  - ป้ายสถานะ `พร้อมรับฟัง (พูดคุยได้เลย)` (`App.kt:760`) ถูกตัดขวา
  - ที่อยู่ `ตำบล ตันหยงลุโละ, ปั…` ถูกตัด — ควรแสดงเฉพาะ ตำบล/อำเภอ
- **อ่านแจ้งเตือนจากปุ่ม** ใช้ TTS (`VoiceManager.speak`) แยกจาก Gemini Live → เสียงซ้อนได้ถ้า AI กำลังพูด

## 4. ความปลอดภัย (ต้องมีก่อนเปิด "ควบคุม 100%")

- Denylist แอปธนาคาร/วอลเล็ต จาก `device_tap` / `device_type_text`
- ยืนยันด้วยเสียงก่อน action ย้อนกลับไม่ได้ (ส่ง, ลบ, จ่าย, โทร)
- จำกัดจำนวน tool call ต่อเนื่องต่อหนึ่งคำสั่ง + คำสั่งเสียง kill switch
- ถ้า `isMoving` → ห้ามโหมดที่ต้องให้ผู้ใช้แตะจอ

## 5. ลำดับงานแนะนำ

**เฟส A — "เห็นและกดได้จริง" ✅ เสร็จ 2026-09-19 (ดู log.md)**
1. §1.3 timeout gesture 2. §1.1 พิกัดใน summary 3. §1.2 เก็บ node clickable
4. §2.6 เพดาน 80 node 5. §2.2 multi-window
> ⚠️ บนเครื่องจริง SM-S908E ตรวจแล้ว `enabled_accessibility_services = null` —
> tool หน้าจอทั้งหมดใช้ไม่ได้จนกว่าผู้ใช้จะเปิด Accessibility ของ JARVIS เอง

**เฟส B — "ควบคุมได้ครบ" ✅ เสร็จ 2026-09-20 (ดู log.md)**
6. §2.1 screenshot API 30+ → Gemini 7. §2.3 submit/IME_ENTER 8. §2.5 รอ window change
9. §2.7 `device_gesture` 10. §2.4 resolve แอปจาก launcher จริง 11. §2.8 กติกา loop ใน persona
> ยังเหลือใน §2.7: pinch/zoom (ต้อง 2 strokes พร้อมกัน) และ tool ดูรายชื่อแอปที่ติดตั้ง

**เฟส C — ขัดเกลาโหมดขับขี่**
12. §1.4 tool `device_parking` + เก็บถาวร 13. §3 FGS location 14. layout 3 จุด 15. ลบ dead code
