# Phase 5.1: Compose App Compile Error Fixes

**วันที่:** 2026-05-02
**ผู้จัดทำ:** JARVIS

## ⚠️ ปัญหาที่พบจากการ Compile
ผู้ใช้พบปัญหา Compile Error ในแอปมือถือ (Kotlin Multiplatform / Jetpack Compose) หลายจุดด้วยกัน ทำให้ไม่สามารถบิวด์และรันแอปได้ ดังนี้:

1. **`BrokerSymbolCache.kt`**: `public` function ไปเผลอเปิดเผย type ของ `private-in-class` (`CacheEntry`)
2. **`Mt5LocalCache.kt`**: มีการเรียกใช้ Suspension function (`putSyncMs`) ในฟังก์ชันปกติ (`persistTrades`)
3. **`SettingsDialog.kt`**:
   - หา `ExposedDropdownMenu` ไม่พบ (Unresolved reference)
   - มีการเรียกใช้ `@Composable` ในฟังก์ชันธรรมดา
4. **`TradingTerminalScreen.kt`**:
   - เผลอใส่ Annotation `@Composable` ให้กับ `enum class`
   - หา `heightIn` ไม่พบ (Unresolved reference)
   - ปัญหาตัวอักษร Escape Sequence ผิดพลาด (`'\'`) และ Incorrect character literal

---

## ✅ แนวทางการแก้ไข และผลลัพธ์ (ดำเนินการแล้วเสร็จ)

### Fix 1: แก้ไข `BrokerSymbolCache.kt`
- **การแก้ไข:** ทำการเปลี่ยน return type ของฟังก์ชัน `invalidate` และ `invalidateAll` แบบ Implicit เป็น Explicit Unit โดยการครอบด้วย `{ ... }` ให้ชัดเจน
- **ผลลัพธ์:** ป้องกันไม่ให้ Kotlin compiler ตีความ return type ไปเป็น `CacheEntry` ซึ่งเป็น private-in-class

### Fix 2: แก้ไข `Mt5LocalCache.kt`
- **การแก้ไข:** เติม `suspend` keyword ให้กับฟังก์ชันย่อย `persistTrades` เนื่องจากในบล็อกนี้มีการไปเรียกใช้ `putSyncMs` ซึ่งเป็นฟังก์ชัน suspend เช่นกัน (และบริบทที่ห่อหุ้มอยู่คือ `withContext(Dispatchers.IO)` ซึ่งรองรับ suspend อยู่แล้ว)
- **ผลลัพธ์:** สามารถบันทึกข้อมูล Trades (ตำแหน่ง, ออเดอร์, ประวัติ) ลง SQLDelight และเรียกใช้งาน `putSyncMs` ได้โดยไม่เกิดปัญหา

### Fix 3: แก้ไข `SettingsDialog.kt`
- **การแก้ไข:** ลบ `androidx.compose.material3.` ที่นำหน้า `ExposedDropdownMenu(...)` ออกทั้งหมด
- **ผลลัพธ์:** เนื่องจาก `ExposedDropdownMenu` ถูกใช้ในบริบทของ `ExposedDropdownMenuBoxScope` มันจึงทำหน้าที่เป็น extension function อยู่แล้ว การระบุ package แบบเต็มทำให้ compiler หาไม่เจอและมองเป็นฟังก์ชันปกติ ส่งผลให้เกิด Error สองต่อ การลบ package prefix ช่วยแก้ปัญหา `Unresolved reference` และทำให้สถานะ `@Composable` กลับมาถูกต้อง

### Fix 4: แก้ไข `TradingTerminalScreen.kt`
- **การแก้ไข:**
  1. ลบ `@Composable` ออกจาก `private enum class PeriodFilter`
  2. เพิ่ม Import สำหรับ `androidx.compose.foundation.layout.heightIn`
  3. แก้ไข `substringAfterLast('\')` เป็น `substringAfterLast('\\')` 
- **ผลลัพธ์:** โค้ดกลับมาถูกต้องตามไวยากรณ์ Kotlin ปัญหาเรื่องตัวอักษรพิเศษหายไป และคอมไพล์ผ่านเรียบร้อย

---
**สถานะการคอมไพล์ล่าสุด:** `BUILD SUCCESSFUL` ใน Task `:composeApp:compileDebugKotlinAndroid`
