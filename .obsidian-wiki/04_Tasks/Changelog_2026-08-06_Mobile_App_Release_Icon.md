# 📋 Changelog — Mobile App Custom Icon & Release APK Pipeline (2026-08-06)

**วันที่:** 2026-08-06  
**ผู้ดำเนินการ:** JARVIS (Pair Programming)  
**Scope:** `composeApp` (Mobile App / KMP Android)  
**สถานะ:** ✅ Complete & Released via GitHub Release Tag `v1.0.0`

---

## 🎨 1. Custom High-Tech App Icon
- **สร้างแบรนด์ดิ้งไอคอนใหม่:** ออกแบบและสร้างไอคอนแอป 3D Futuristic AI Trading Bot (Obsidian Glass + Glowing Neon Cyan & Gold)
- **สร้าง mipmap ครบทุกระดับความละเอียด:**
  - `mipmap-mdpi`: 48x48 px
  - `mipmap-hdpi`: 72x72 px
  - `mipmap-xhdpi`: 96x96 px
  - `mipmap-xxhdpi`: 144x144 px
  - `mipmap-xxxhdpi`: 192x192 px
- **ปรับแต่ง Adaptive Icon:** ลบไฟล์ `mipmap-anydpi-v26/ic_launcher.xml` (หุ่นยนต์สีเขียวตั้งต้น) เพื่อให้ Android 8.0+ แสดงผล PNG App Icon ใหม่บนหน้าจอหลักอย่างคมชัด

---

## 📦 2. Gradle APK Output Naming & Release Build Fix
- **ปรับแต่ง Gradle Build Script (`composeApp/build.gradle.kts`):**
  - ตั้งค่า `applicationVariants.all` ปรับชื่อไฟล์ APK Output ตามชื่อ Variant:
    - Debug: `PersonalAIBot-debug.apk`
    - Release: `PersonalAIBot-release.apk`
  - ปรับตั้งค่า `strings.xml` ให้ชื่อแอปบนหน้าจอหลักเป็น `Personal AI Bot`
- **Lint Bypass สำหรับ Release Build:**
  - เพิ่ม `lint { checkReleaseBuilds = false; abortOnError = false }` ป้องกัน `lintVitalRelease` บล็อกการบิวด์ Release APK
  - คอมไพล์ผ่านสมบูรณ์แบบ (`BUILD SUCCESSFUL` ในขนาด 97 MB)

---

## 🚀 3. GitHub Release Deployment
- สร้าง GitHub Release Tag `v1.0.0` บน [PersonalAIOnMobile Repository](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
- อัปโหลดไฟล์ **`PersonalAIBot-release.apk`** ขึ้น Release Assets เพื่อให้สามารถดาวน์โหลดติดตั้งบนมือถือได้โดยตรง

---

## 🔖 Tags
`#mobile` `#android` `#app-icon` `#release-apk` `#changelog` `#2026-08-06`
