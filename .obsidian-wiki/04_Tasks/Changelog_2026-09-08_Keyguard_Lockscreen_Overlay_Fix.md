---
title: Changelog 2026-09-08 Keyguard Lockscreen Overlay & Screen Sleep Fix
category: bugfix
tags: [android, keyguard, lockscreen, lifecycle, window-flags, always-live]
sources: [AndroidManifest.xml, MainActivity.kt, AlwaysLiveManager.kt, FloatingWidgetService.kt]
created: 2026-09-08
updated: 2026-09-08
---

# Changelog 2026-09-08: Keyguard Lockscreen Overlay & Normal Mode Screen Sleep Fix

## ปัญหาที่พบ (Problem Statement)
ผู้ใช้รายงานอาการผิดปกติขณะใช้งานใน **โหมดปรกติ (Normal Mode)**:
1. เปิดแอปทิ้งไว้แล้วพักหน้าจอ (จอดับ)
2. เมื่อกดปุ่ม Power เพื่อเปิดหน้าจอขึ้นมา ระบบควรแสดงหน้า **ล็อกหน้าจอ (Keyguard)** ให้ใส่ PIN / สแกนนิ้ว
3. **แอปกลับแสดงขึ้นมาทับหน้าปลดล็อกหน้าจอ** ทันที ทำให้ผู้ใช้เห็นหน้าแอปและใช้งานแอปได้ แต่ไม่สามารถเข้าหน้าโฮม หรือสลับไปแอปอื่นได้ จนกว่าจะกดปิดแอปทิ้ง จึงจะโผล่ไปยังหน้าปลดล็อกหน้าจอ

จาก Logcat:
```text
2026-09-08 17:51:36.784 com.example.personalaibot D WindowStopped on com.example.personalaibot.MainActivity set to true
...
2026-09-08 17:52:40.085 com.example.personalaibot D WindowStopped on com.example.personalaibot.MainActivity set to false
2026-09-08 17:52:40.131 com.example.personalaibot I Relayout returned: ... res=0x3 s={true ...}
2026-09-08 17:52:40.174 com.example.personalaibot I onDisplayChanged oldDisplayState=4 newDisplayState=2
```
Window ของ MainActivity ถูกเรนเดอร์ขึ้นมาทับ Lock Screen โดยตรง

---

## สาเหตุหลัก (Root Cause Analysis)

1. **Static Declaration ใน AndroidManifest.xml**:
   - ใน `<activity android:name=".MainActivity">` มีการประกาศ attribute:
     ```xml
     android:turnScreenOn="true"
     android:showWhenLocked="true"
     ```
   - ทำให้ระบบ Window Manager ของ Android บังคับให้ `MainActivity` แสดงอยู่เหนือหน้าจอ Lock Screen เสมอตลอดเวลา ไม่ว่าแอปจะอยู่ในสถานะใด (แม้จะอยู่ในโหมดปรกติ)

2. **AlwaysLiveManager.wakeScreen() เรียก turnScreenOnTemporarily() โดยไม่ตรวจสอบสถานะ**:
   - เมื่อมีการปลุกจอ เช่น เกิด Alert หรือระบบเรียก `wakeScreen()`, ฟังก์ชันเรียก `MainActivity.instance?.turnScreenOnTemporarily()`
   - ซึ่งภายในมีการเซ็ต `setShowWhenLocked(true)` และ `FLAG_SHOW_WHEN_LOCKED` แม้ว่าผู้ใช้จะปิด Always Live Mode ไปแล้ว (`state == OFF`)

3. **ขาดการเคลียร์ Window Flags ใน Activity Lifecycle**:
   - ใน `MainActivity.kt` ไม่มีคำสั่ง `clearScreenFlags()` ใน `onCreate()`, `onResume()`, และ `onStop()` เพื่อยืนยันว่าเมื่ออยู่ในโหมดปรกติ (`AlwaysLiveState.OFF`) ต้องไม่มี flag ใดๆ ที่แสดงทับ Keyguard

4. **FloatingWidgetService ใช้ FLAG_KEEP_SCREEN_ON**:
   - หน้าต่าง Overlay ของ Floating Widget ใส่ flag `FLAG_KEEP_SCREEN_ON` ทำให้หน้าจอไม่ยอมพัก

---

## วิธีการแก้ไข (Implemented Solutions)

### 1. นำ Attribute แสดงทับ Lockscreen ออกจาก AndroidManifest.xml
- `composeApp/src/androidMain/AndroidManifest.xml`:
  - ลบ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` ออกจากแท็ก `<activity android:name=".MainActivity">`
  - ปล่อยให้การแสดงผลเหนือ Lock Screen จัดการผ่าน Runtime API อย่างไดนามิกเฉพาะเมื่อผู้ใช้เปิดใช้งานโหมด Always Live (Control Mode) หรือ Hotword Wake เท่านั้น

### 2. กำหนดเงื่อนไขใน AlwaysLiveManager.kt
- ใน `wakeScreen()`:
  - ตรวจสอบ `if (_state.value == AlwaysLiveState.FULL_SCREEN)` ก่อนเรียก `MainActivity.instance?.turnScreenOnTemporarily()`
  - หากอยู่ในโหมดปรกติ (`AlwaysLiveState.OFF`) จะทำการปลุกจอด้วย WakeLock ชั่วคราว 10 วินาทีเท่านั้น โดย**ไม่**เซ็ต flag แสดงทับหน้าจอล็อก

### 3. ควบคุม Lifecycle ใน MainActivity.kt
- เพิ่มการเรียก `clearScreenFlags()` ใน:
  - `onCreate()`: ล้าง flag ตกค้างตั้งแต่เปิดแอป
  - `onResume()`: ตรวจสอบหาก `alwaysLiveManager.state.value == AlwaysLiveState.OFF` ให้เรียก `clearScreenFlags()` ทันที
  - `onStop()`: เมื่อหน้าจอดับและแอปเข้าสู่สถานะ `onStop()` ในโหมดปรกติ ให้เรียก `clearScreenFlags()` เพื่อให้ Keyguard แสดงผลตามปกติ 100%
- ฟังก์ชัน `clearScreenFlags()`:
  ```kotlin
  fun clearScreenFlags() {
      runOnUiThread {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              setShowWhenLocked(false)
              setTurnScreenOn(false)
          }
          @Suppress("DEPRECATION")
          window.clearFlags(
              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
              WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
              WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          )
      }
  }
  ```

### 4. ปลด FLAG_KEEP_SCREEN_ON ใน FloatingWidgetService.kt
- ลบ `FLAG_KEEP_SCREEN_ON` ออกจาก `WindowManager.LayoutParams` ของ Floating Widget เพื่อให้ระบบสามารถเข้าสู่โหมดพักหน้าจอได้ตามปกติ

---

## การทดสอบและตรวจสอบความถูกต้อง (Verification)
1. **Unit Tests**:
   - รัน `./gradlew testDebugUnitTest` ผ่านสำเร็จ 100% (30 actionable tasks)
2. **Device Inspection**:
   - ติดตั้ง Debug APK ลงเครื่อง `SM-S908E` ผ่าน `./gradlew installDebug`
   - รันคำสั่ง `dumpsys window` ตรวจสอบสถานะ Window ของ `com.example.personalaibot/.MainActivity`:
     - `showForAllUsers=false` (ไม่แสดงทับ Lock Screen อีกต่อไป)
     - `mHoldScreenWindow=null` (ไม่มีการค้างหน้าจอเปิดในโหมดปรกติ)
     - เมื่อกด Power ปิดหน้าจอ และกดเปิดใหม่ เครื่องจะเข้าสู่หน้าใส่ PIN / สแกนลายนิ้วมือตามมาตรฐานความปลอดภัยของ Android

---

## ส่วนที่ 2: ปัญหาและแก้ไขในโหมดควบคุม (Control Mode / Always Live Mode)

### ปัญหาที่พบ (Issue in Control Mode)
1. ในโหมดควบคุม (`FULL_SCREEN`) ผู้ใช้สั่งเสียง "พักหน้าจอ" → หน้าจอดับและเข้าสู่ `BACKGROUND_LISTEN` ถูกต้อง
2. แต่เมื่อสั่ง "เปิดหน้าจอ" หรือกด Power ปลุกเครื่อง → จอสว่างขึ้น แต่**ติดหน้าล็อกหน้าจอ (Keyguard)** ให้ใส่รหัสผ่าน/PIN
3. **สิ่งที่ควรจะเป็น**: ในโหมดควบคุม เมื่อปลุกจอ ตัวแอป `MainActivity` ต้องถูกเรียกให้แสดงผล**ทับเหนือหน้าจอล็อก (Show When Locked)** ทันทีโดยไม่ต้องปลดล็อกรหัสผ่าน เพื่อให้ผู้ใช้สั่งงานต่อเนื่องแบบ Hands-free ได้

### สาเหตุหลัก (Root Cause)
1. **การเรียก requestDismissKeyguard()**:
   - ใน `turnScreenOnTemporarily()` มีการเรียก `KeyguardManager.requestDismissKeyguard()` ซึ่งบน Android 8+ หากเครื่องตั้ง PIN/Pattern ไว้ ระบบจะบังคับเด้งหน้า Keyguard Bouncer (แป้นพิมพ์รหัสผ่าน) ขึ้นมาแทนที่จะให้ Activity แสดงผลทับเงียบๆ
2. **AlwaysLiveManager ขาดการ Start Activity เมื่อปลุกจอ**:
   - ใน `wakeScreen()` มีการปลุกจอด้วย WakeLock แต่ไม่ได้ยิง Intent เรียก `MainActivity` ขึ้นมา Foreground เหนือ Lock Screen
3. **onResume() และ onNewIntent() ไม่ได้ Assert Window Flags**:
   - เมื่อ Activity ถูกดึงขึ้นมาหลังจากหน้าจอดับ หากอยู่ในโหมดควบคุม ไม่ได้มีการเรียก `turnScreenOnTemporarily()` และ `setKeepScreenOn(true)` ซ้ำ
4. **device_press_button ขาด action ปลุกจอ**:
   - คำสั่ง "เปิดหน้าจอ" จากเสียงผู้ใช้ ขาดการแมปปุ่ม `wake` ใน `device_press_button` และ `DeviceToolDefinitions`

### วิธีการแก้ไข (Fix Implemented)
1. **ลบ requestDismissKeyguard ออกจาก `turnScreenOnTemporarily()`**:
   - คงไว้เฉพาะ `setShowWhenLocked(true)`, `setTurnScreenOn(true)` และ Window Flags `FLAG_TURN_SCREEN_ON or FLAG_SHOW_WHEN_LOCKED or FLAG_KEEP_SCREEN_ON` ทำให้ Activity แสดงทับ Lock Screen ได้ทันทีโดยไม่เด้งถาม PIN
2. **ยิง Intent `HOTWORD_WAKE` จาก `AlwaysLiveManager`**:
   - ใน `wakeScreen()`, `enable()`, และ `onScreenOn()` เมื่ออยู่ในโหมดควบคุม ให้ยิง Intent พร้อม flags `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_REORDER_TO_FRONT` เพื่อยก `MainActivity` ขึ้นมาหน้า Lock Screen
3. **อัปเดต `onResume()` และ `onNewIntent()` ใน `MainActivity`**:
   - หาก `alwaysLiveManager.state != OFF` ให้ re-assert `turnScreenOnTemporarily()` และ `setKeepScreenOn(true)` ทันที
4. **เพิ่ม `wake` ใน `device_press_button`**:
   - เพิ่มปุ่ม `wake` รองรับคำสั่ง "เปิดหน้าจอ", "เปิดจอ", "ปลุกหน้าจอ", "ตื่น" โดยจะปลุกจอและขยายเข้าสู่ Control Mode ทันที

---

## ส่วนที่ 3: ปัญหา Loop รัวๆ ระหว่าง FULL_SCREEN และ MINI_FLOATING

### ปัญหาที่พบ (Symptom)
เมื่อเปิดโหมดควบคุม (Always Live) เกิดอาการ Log เด้งรัวๆ สลับไปมาระหว่าง:
`FULL_SCREEN` ⇄ `MINI_FLOATING` ทุกๆ ~15ms แบบ Infinite Loop

### สาเหตุหลัก (Root Cause)
1. `enable()` หรือ `wakeScreen()` เรียก `context.startActivity(intent)` ไปยัง `MainActivity` ซึ่งกำลังเปิดอยู่แล้วใน Foreground
2. การเริ่ม Activity ซ้ำ ส่งผลให้ Android เรียก `MainActivity.onPause()`
3. ใน `onPause()` มีเงื่อนไขว่าถ้าสถานะเป็น `FULL_SCREEN` ให้เรียก `alwaysLiveManager.minimize()` เพื่อสลับไปเป็น `MINI_FLOATING`
4. จากนั้น Intent `HOTWORD_WAKE` ถูกส่งเข้า `MainActivity.onNewIntent()` ซึ่งเรียก `onStartAlwaysLive()` → เรียก `enable()` ซ้ำ
5. `enable()` เปลี่ยนกลับเป็น `FULL_SCREEN` และเรียก `startActivity(intent)` อีกครั้ง วนลูปไม่รู้จบ

### วิธีการแก้ไข (Solution)
1. **เพิ่มตัวแปร `isActivityResumed` ใน `MainActivity`**:
   - เซ็ต `true` ใน `onResume()` และ `false` ใน `onPause()` / `onDestroy()`
2. **เงื่อนไขใน `AlwaysLiveManager` (`enable()`, `expand()`, `wakeScreen()`, `onScreenOn()`)**:
   - ใน `enable()`: ตรวจสอบถ้า `_state.value == AlwaysLiveState.FULL_SCREEN` ให้ข้าม (`return`) ทันที
   - ไม่เรียก `context.startActivity(intent)` หาก `MainActivity.isActivityResumed == true` (เนื่องจากแอปแสดงอยู่บนหน้าจออยู่แล้ว)
3. **เงื่อนไขใน `MainActivity.onPause()`**:
   - เพิ่มการตรวจสอบ `km?.isKeyguardLocked != true && !isFinishing` เพื่อไม่ให้เรียก `minimize()` ระหว่างที่เครื่องกำลังจะล็อกหน้าจอหรือกำลังเปลี่ยนสถานะ

