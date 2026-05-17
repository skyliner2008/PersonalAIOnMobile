---
name: Local Model UI Bugfix 2026-05-03
description: แก้บั๊กปุ่ม Download Local Model ใน Settings — fire-and-forget, ไม่ persist Ready, ตั้ง 2f โดยไม่ verify
type: project
originSessionId: f3b88c4a-dd4b-4c64-9d45-ea856b6f1c56
---
อาการ: กด Download Local Model แล้ว UI ไม่อัพเดต + เปิดปิด app ใหม่ปุ่มยังเป็น "Download" เหมือนเดิม แม้ไฟล์โมเดลมีในเครื่อง

**Why:** 3 บั๊กซ้อนกัน

**How to apply:** เวลาแก้ปุ่ม / progress flow ของ Local ONNX อย่าลืมเรื่อง 3 จุดนี้ — (1) suspend lambda contract ระหว่าง JarvisVM ↔ MainActivity, (2) startup persistence ของ progress flag, (3) verify `isAvailable()` ไม่ใช่ตั้ง 2f blind

Fixes:
1. `MainActivity.onDownloadLocalModel` (line ~148) — เปลี่ยน `lifecycleScope.launch { ... }` → `kotlinx.coroutines.coroutineScope { ... }` เพื่อให้ suspend lambda รอจริง (เดิม launch แล้ว return ทันที → caller เห็น "complete" ใน 5ms ก่อน ONNX จะเริ่มโหลด 32 วินาที)
2. `JarvisViewModel.init` (line ~142) — หลัง `onDownloadLocalModel(checkOnly=true)` เพิ่ม check `if (localOnnx.isAvailable()) _modelDownloadProgress.value = 2f` (เดิมไม่อ่านผลกลับ → cold start เห็น progress = -1f เสมอ)
3. `JarvisViewModel.downloadLocalModel` (line ~2062) — ตั้ง `2f` ก็ต่อเมื่อ `localOnnx.isAvailable() == true` ไม่งั้นกลับ -1f (เดิมตั้ง 2f blind ทันทีหลัง downloader return)

หมายเหตุ: `LocalOnnxEmbeddingProvider.isAvailable()` คืน `modelLoaded && inferenceDelegate != null` ซึ่ง `modelLoaded` ถูกตั้ง true ใน `setInferenceDelegate()` ที่ `AndroidLocalOnnxManager.loadModel()` เรียกหลัง OrtSession.create สำเร็จ — เป็น source of truth ที่ปลอดภัยที่สุด

Log ที่ confirm บั๊ก #1:
```
15:03:11.978 JarvisVM: Starting model download via platform downloader
15:03:11.978 JarvisVM: Model download complete                          ← !!! ก่อน ONNX
15:03:11.983 AndroidONNX: Downloading Multilingual model: ...
15:03:43.180 AndroidONNX: Multilingual model loaded successfully.       ← จริง 32 วิ
```
