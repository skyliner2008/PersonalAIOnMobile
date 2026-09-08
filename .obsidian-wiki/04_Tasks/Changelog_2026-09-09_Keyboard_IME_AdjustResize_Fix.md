---
title: Changelog 2026-09-09 Keyboard IME AdjustResize & Inset Fix
category: Tasks
tags:
  - bugfix
  - android
  - compose
  - ui
  - ime
  - insets
sources:
  - "[[Current_Tasks]]"
created: 2026-09-09
updated: 2026-09-09
---

# Changelog: 2026-09-09 — Keyboard IME `adjustResize` & Edge-to-Edge Inset Fix

## 1. ปัญหาที่พบ (Issue Description)
- เมื่อนำแอปไปติดตั้งบนมือถือเครื่องอื่น (เช่น **HONOR MTN-NX1** ที่ใช้การนำทางแบบ 3 ปุ่ม / 3-button navigation ร่วมกับ SwiftKey Keyboard)
- เมื่อผู้ใช้แตะที่ช่องพิมพ์ข้อความ ("พิมพ์ข้อความ...") เพื่อจะพิมพ์แชท:
  - ช่องแชท (`ChatInputBar`) และเนื้อหาแอปจะถูกดีด/เลื่อนขึ้นไปจนสุดขอบบนของหน้าจอ (ทับแถบ Status Bar, นาฬิกา, แบตเตอรี่)
  - เกิดช่องว่างสีมืดขนาดมหึมา (Black Void) กั้นอยู่ระหว่างกล่องข้อความกับคีย์บอร์ด
  - ในขณะที่เครื่องอื่น (เช่น Samsung Galaxy S22 Ultra ที่ใช้ Gesture Navigation) และแอปอื่นๆ ในเครื่องเดียวกันทำงานเป็นปกติ

---

## 2. สาเหตุที่แท้จริง (Root Cause Analysis)

### 2.1 ขาดการระบุ `android:windowSoftInputMode="adjustResize"` ใน `AndroidManifest.xml`
- แท็ก `<activity android:name=".MainActivity">` ไม่ได้ระบุ `android:windowSoftInputMode`
- ระบบ Android จึงใช้ค่าเริ่มต้นเป็น `adjustUnspecified`
- บนอุปกรณ์บางแบรนด์ (โดยเฉพาะ **Huawei / Honor EMUI / MagicOS**, Xiaomi MIUI/HyperOS) เมื่อเปิดใช้งานแถบนำทางแบบ 3 ปุ่ม (3-button navigation):
  - ระบบจะเลือกโหมด **`adjustPan`** (แทนที่จะเป็น `adjustResize`)
  - ภายใต้ `adjustPan`: WindowManager ของ Android จะสั่ง "เลื่อนหน้าต่างทั้งบานขึ้น" (Window Pan) ตามความสูงของคีย์บอร์ด (~1,100px) เพื่อพยายามไม่ให้ช่องป้อนข้อมูลถูกคีย์บอร์ดบัง

### 2.2 การทับซ้อนของ Inset ใน Compose (`Additive Inset Stacking`)
- ใน `ChatInputBar.kt` มีการเขียน Inset Modifiers ซ้อนกัน:
  ```kotlin
  Column(
      modifier = Modifier
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .navigationBarsPadding()
          .imePadding()
  )
  ```
- ใน Compose ตัว Modifier `.navigationBarsPadding()` และ `.imePadding()` ทำงานแบบ **บวกเพิ่มต่อเนื่อง (Additive)**:
  - `navigationBarsPadding()` เพิ่มความสูงแถบปุ่มนำทาง (~128px / 48dp)
  - `imePadding()` เพิ่มความสูงของคีย์บอร์ด (~1,100px / 300dp)
- **ผลรวมที่เกิดขึ้นบนเครื่อง Honor (3-button nav + adjustPan):**
  1. WindowManager เลื่อนหน้าต่างทั้งแอปขึ้นไป **~1,100px** (`adjustPan`)
  2. Compose บวกความสูงคีย์บอร์ดอีก **~1,100px** (`imePadding()`)
  3. Compose บวกความสูงแถบปุ่มนำทางอีก **~128px** (`navigationBarsPadding()`)
  4. **รวมระยะที่กล่องข้อความถูกดันขึ้น = ~2,328px!** (เกือบเท่าความสูงทั้งจอ 2,640px)
  5. กล่องข้อความจึงลอยขึ้นไปชนขอบบนสุดของจอ และพื้นหลัง `JarvisTheme.Surface` ของ `ChatInputBar` ถูกยืดออกเป็นช่องว่างสีมืดขนาดใหญ่ระหว่างกล่องข้อความกับคีย์บอร์ด

---

## 3. วิธีการแก้ไข (Solution)

### 3.1 บังคับใช้ `windowSoftInputMode="adjustResize"` ใน `AndroidManifest.xml`
```xml
<activity
    android:exported="true"
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:windowSoftInputMode="adjustResize"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|keyboard">
```
- บังคับให้ WindowManager ไม่เลื่อนหน้าต่างขึ้น (`adjustPan`) แต่ให้หน้าต่างคงที่อยู่กับที่ แล้วส่งสัญญาณ Inset ให้ Jetpack Compose จัดการการขยับและ Layout เองอย่างลื่นไหล

### 3.2 ใช้ `WindowInsets.safeDrawing.only(Bottom + Horizontal)` ใน `ChatInputBar.kt`
- แทนที่การเรียก `.navigationBarsPadding().imePadding()` แบบบวกซ้อนกัน ด้วย:
```kotlin
Column(
    modifier = Modifier
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        .padding(horizontal = 12.dp, vertical = 10.dp)
)
```
- `WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)` จะทำการคำนวณ `max(navigationBars.bottom, ime.bottom)`:
  - **เมื่อคีย์บอร์ดปิดอยู่**: ใช้ความสูงของ `navigationBars.bottom` กล่องข้อความจะลอยอยู่เหนือแถบปุ่มนำทาง 3 ปุ่มพอดี
  - **เมื่อคีย์บอร์ดเปิดขึ้น**: ใช้ความสูงของ `ime.bottom` กล่องข้อความจะลอยอยู่เหนือคีย์บอร์ดพอดีโดยไม่มีช่องว่างหรือการบวกซ้ำซ้อนกับปุ่มนำทาง
  - **แนวนอน (Horizontal)**: ป้องกันไม่ให้ตกขอบในโหมดหน้าจอนอนหรือมี Display Cutout

---

## 4. ผลการทดสอบ (Verification)
- ทดสอบ Build และติดตั้งลงบนเครื่อง HONOR MTN-NX1
- สั่งตรวจสอบด้วย ADB ตรวจสอบ Window Attributes ยืนยันสถานะ `adjust=resize`
- ตรวจสอบการกดแป้นพิมพ์ SwiftKey กล่องข้อความลอยขึ้นมาแนบติดชิดอยู่เหนือคีย์บอร์ดพอดี ไม่เด้งขึ้นไปบนสุด และไม่มีช่องว่างสีมืด
