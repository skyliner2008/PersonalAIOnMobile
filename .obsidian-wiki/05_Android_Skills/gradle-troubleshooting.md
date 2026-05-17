# การแก้ปัญหา Gradle ใน IDE (Troubleshooting)

## ปัญหา: Specified initialization script does not exist

### อาการ
ในหน้า "Problems" ของ IDE (VS Code) แสดงข้อความผิดพลาด:
`Could not run phased build action using connection to Gradle distribution ... The specified initialization script '.../redhat.java/.../init.gradle' does not exist.`

### สาเหตุ
เกิดจาก Java Language Server (JDT.LS) พยายามเรียกใช้ script `init.gradle` ที่อยู่ใน cache ของ extension แต่หาไฟล์ไม่เจอ ซึ่งอาจเกิดจากการอัพเดท extension หรือ cache ของ workspace เสียหาย

### วิธีแก้ไข
1. **Clean Language Server Workspace**:
   - กด `Ctrl + Shift + P` (หรือ `Cmd + Shift + P` บน Mac)
   - พิมพ์และเลือกคำสั่ง **"Java: Clean Language Server Workspace"**
   - เลือก **"Reload and Delete"** เมื่อมีหน้าต่างยืนยันปรากฏขึ้น
2. **ตรวจสอบการตั้งค่า `.vscode/settings.json`**:
   - ตรวจสอบว่ามีการตั้งค่า `"java.import.gradle.wrapper.enabled": true` เพื่อใช้ Gradle Wrapper ของโปรเจกต์
3. **ตรวจสอบผ่าน Terminal**:
   - ลองรันคำสั่ง `.\gradlew help` หากรันผ่านแสดงว่าปัญหาอยู่ที่ตัว IDE ไม่ใช่ที่โปรเจกต์

---
**Last Updated**: 2026-04-22
**Status**: Resolved by Environment Reset
