# 🧠 Local Embedding System (Multilingual)

ระบบการสร้าง Vector Embeddings ภายในตัวเครื่อง (On-device) เพื่อรองรับการค้นหาความจำแบบ Offline และเพิ่มความส่วนตัวของข้อมูล

## 📋 ข้อมูลทางเทคนิค (Technical Specs)

- **Model**: `paraphrase-multilingual-MiniLM-L12-v2`
- **Format**: ONNX (Quantized q4)
- **Size**: ~117 MB
- **Native Dimensions**: 384 (raw model output หลัง mean pooling)
- **Output Dimensions**: **768** — pad + L2-normalize ผ่าน `fitToTargetDimension(768)` ใน `LocalOnnxEmbeddingProvider.embed()` เพื่อให้ vector interchangeable กับ Cloud providers (Gemini) ใน HNSW เดียวกัน
- **Language Support**: 50+ ภาษารวมถึง **ภาษาไทย (Native Support)**
- **Context Length**: 512 Tokens
- **Runtime**: ONNX Runtime Android (v1.23.0) — upgraded from 1.19.2 for 16 KB page size alignment (Google Play requirement Nov 2025)

## 🏗️ สถาปัตยกรรม (Architecture)

ระบบถูกออกแบบมาให้เป็น **Zero-Dependency** บน Native Library อื่นๆ นอกเหนือจากตัว Runtime หลัก:
1. **Streaming Download**: ใช้ Ktor `prepareGet` เพื่อดึงข้อมูลแบบ Stream ป้องกันปัญหา OutOfMemory (OOM) บน Android
2. **Pure Kotlin Tokenizer**: ใช้ `LocalKotlinTokenizer` (BPE/WordPiece) ที่เขียนด้วย Kotlin 100% เพื่อหลีกเลี่ยงปัญหา JNI และความไม่เสถียรของ `onnxruntime-extensions`
3. **Mean Pooling**: ระบบคำนวณค่าเฉลี่ยของ Hidden States จากทุก Token เพื่อสร้างผลลัพธ์เป็น Vector เดียวที่แม่นยำ

## 🚀 ประสิทธิภาพ (Performance)

| อุปกรณ์ | เวลาประมวลผล (ต่อ 1 ประโยค) | RAM Usage |
| :--- | :--- | :--- |
| Android (Snapdragon 8 Gen 1) | ~30-50 ms | +~150MB |
| Android (Mid-range) | ~100-200 ms | +~150MB |

## 🛠️ วิธีการบำรุงรักษา (Maintenance)

- **ไฟล์โมเดล**: เก็บอยู่ที่ `context.filesDir/multilingual_mini_lm_q4.onnx`
- **ไฟล์ Tokenizer**: เก็บอยู่ที่ `context.filesDir/multilingual_tokenizer.json`
- **การอัปเดต**: หากต้องการเปลี่ยนโมเดล ให้แก้ไข `modelUrl` และ `tokenizerUrl` ใน `AndroidLocalOnnxManager.kt` และปรับ `rawDimensions` ใน `LocalOnnxEmbeddingProvider.kt` ให้ตรงกับ raw output ใหม่ (`nativeDimensions` ต้องเป็น 768 เสมอเพื่อ contract กับ vectorStore — ถ้าโมเดลใหม่คืน >768 ให้ truncate, ถ้า <768 ให้ pad ผ่าน `fitToTargetDimension(768)`)

## 🔗 ระบบการเชื่อมต่อ (Device Pairing & Approval)

เพื่อให้การทำงานระหว่าง Android และ Core Server มีความปลอดภัย ระบบจึงใช้กลไกการ Pairing:
1. **Request**: แอปมือถือส่งคำขอเชื่อมต่อพร้อม Generated Token ไปยัง Server (`/api/auth/pair/request`)
2. **Approval**: ผู้ใช้ต้องเปิด Dashboard ของ Server ไปที่ Tab **System** เพื่อดูคำขอที่ค้างอยู่ (Pending Requests)
3. **Admin Verification**: การ Approve หรือ Reject ต้องใช้ `ADMIN_TOKEN` ในการยืนยันตัวตนเจ้าของเซิร์ฟเวอร์
4. **Token Issuance**: เมื่อ Approve แล้ว Server จะสร้าง API Token ให้มือถือใช้งานโดยอัตโนมัติ และมือถือจะเปลี่ยนสถานะเป็น `APPROVED`

## ⚠️ ข้อควรระวัง (Warnings)

- **Large Heap**: ต้องเปิดใช้งาน `android:largeHeap="true"` ใน `AndroidManifest.xml` เสมอเพื่อให้ระบบดาวน์โหลดและรันโมเดลได้อย่างราบรื่น
- **Tokenizer Format**: หากเกิดปัญหา `Unexpected JSON token` ให้ตรวจสอบความถูกต้องของ URL ใน HuggingFace (ต้องเป็น Public Repo เท่านั้น)
- **Memory Management**: การใช้โมเดล On-device จะใช้ RAM เพิ่มขึ้นประมาณ 150-200MB ควรตรวจสอบสถานะหน่วยความจำของเครื่องก่อนโหลดเสมอ

## 🐞 Bugfix Log

### 2026-05-03 — ปุ่ม "Download Local Model" ไม่อัพเดตสถานะ
**อาการ**: กดดาวน์โหลดแล้ว Settings UI ไม่ขึ้นว่าสำเร็จ + เปิด-ปิด app ใหม่ ปุ่มยังเป็น "Download Local Model (120MB)" แม้ไฟล์โมเดลมีในเครื่องแล้ว

**Root cause** (3 จุด):
1. **`MainActivity.onDownloadLocalModel`** ห่อด้วย `lifecycleScope.launch { ... }` แล้ว return ทันที → suspend lambda กลับให้ caller (JarvisVM) ก่อน `downloadModelFiles()` จะเริ่ม → JarvisVM ตั้ง progress = 2f ทันที (log: "Model download complete" 5ms ก่อน "Downloading Multilingual model")
2. **`JarvisViewModel.init`** เรียก `onDownloadLocalModel(checkOnly=true)` แต่ไม่ได้ตรวจ `localOnnx.isAvailable()` หลัง init เพื่อมาร์ก progress = 2f → cold start ไม่ persist สถานะ "Ready"
3. **`JarvisViewModel.downloadLocalModel`** ตั้ง `_modelDownloadProgress.value = 2f` แบบ blind ไม่ verify ว่า OrtSession โหลดสำเร็จจริง

**Fix**:
- MainActivity: เปลี่ยน `lifecycleScope.launch` → `coroutineScope { ... }` (suspend ตรงกับ caller's contract)
- JarvisViewModel.init: เพิ่ม `if (localOnnx.isAvailable()) _modelDownloadProgress.value = 2f` หลัง checkOnly
- JarvisViewModel.downloadLocalModel: verify `isAvailable()` หลัง download เสร็จก่อนตั้ง 2f (ไม่งั้น fallback -1f)

---
**Last Updated**: 2026-05-03 (ONNX Runtime 1.19.2→1.23.0 — 16 KB page size compliance)
**Status**: 🟢 Production Ready (Phase 4.6)
