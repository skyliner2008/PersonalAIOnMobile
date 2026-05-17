---
name: Embedding Audit 2026-05-03
description: ตรวจ + แก้ Local Embedding ทั้ง mobile + server (model cascade, dim normalize, vectorStore safety)
type: project
originSessionId: 7a62e686-75b2-4315-80dd-58879a656700
---
ตรวจครบทั้ง mobile (Kotlin) + server (Node/TS) embedding subsystem

**Why:** บางส่วนยังใช้ embedding-001 (404'd), provider คืน raw dims ไม่ uniform ทำให้ cross-provider search คืน 0

**How to apply:** ทุก provider ต้องคืนเวกเตอร์ 768-dim L2-normalized; memory store + vectorStore ต้องปฏิเสธ vector ที่ dim ไม่ตรง

Mobile fixes (8):
1. GeminiEmbeddingProvider — drop deprecated `embedding-001`, ใช้ cascade `gemini-embedding-001` (3072→truncate 768) → `text-embedding-004` (768)
2. nativeDimensions = 768 ทั้ง gemini + local (เพราะคืน fitToTargetDimension(768) แล้ว)
3. LocalOnnxEmbeddingProvider — ใส่ fitToTargetDimension(768) หลัง 384-dim mean pool → pad+L2 norm
4. EmbeddingProviderRegistry.embedWithFallback — defensive re-fit + เพิ่ม top-level `EMBEDDING_TARGET_DIMS = 768`
5. JarvisMemoryManager.searchRelevantFacts — fit query + legacy stored vectors เป็น 768 ก่อน cosine
6. archiveFactWithEmbedding + backfillEmbeddings — fit ก่อน encode (ป้องกัน mixed-dim)
7. GeminiService.embedText (legacy) — cascade เดียวกัน + import fitToTargetDimension
8. MainActivity.onDownloadLocalModel — cache `AndroidLocalOnnxManager` เป็น field (เลิก leak OrtSession)

Server fixes (4):
9. vectorStore.ts — import `EMBEDDING_TARGET_DIMS` จาก providers/types.ts เลิก hardcode 768
10. vectorStore.add() — reject wrong-dim vector + auto resize เมื่อถึง MAX_ELEMENTS
11. vectorStore.search() — reject wrong-dim + zero-vector query
12. vectorStore.init() — try/catch readIndex separately, re-init fresh ถ้า persist file พัง
13. agentOrchestrator.ts — เลิก hardcode `const EMBEDDING_TARGET_DIMS = 768` ใช้ shared import
14. agentOrchestrator.ts — strip trailing NUL bytes ที่ทำให้ tsc error TS1127

Verified: tsc --noEmit ผ่าน 0 errors บน mt5-core-server

หมายเหตุ mount sync: ระหว่าง edit Write tool กับ bash mount มี delay; ถ้า bash เห็น file ถูก truncate ให้เขียนทับผ่าน `cat > FILE << EOF` ตรงๆ
