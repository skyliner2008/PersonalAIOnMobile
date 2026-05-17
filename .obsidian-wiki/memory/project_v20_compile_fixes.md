---
name: V20.0 Compile Fixes 2026-05-01
description: แก้ compile errors ทั้งหมดหลัง V20.0 implementation + wiki เสร็จสมบูรณ์
type: project
originSessionId: 908bb82a-4f59-420d-9826-b457976a0e0a
---
V20.0 server compile clean (tsc --noEmit = 0 errors) หลังแก้ปัญหาต่อไปนี้:

**smc.ts**: duplicate `getSmcStructure` export — เกิดจาก previous session เขียน block แรกผ่าน Write tool, แล้ว session นี้ `cat >>` append block ที่สองเข้าไปด้วย แก้โดย Write ทับ file สะอาดทั้งหมด

**slTpAnalyst.ts**:
- `maxTokens` → `maxOutputTokens` (ชื่อใน GenerateOptions)
- `roundToTick()` returns `number | null` แต่ SlTpResult.sl/tp expects `number` → แก้ `?? finalSl/finalTp`
- Implicit `any` callbacks → ถูก resolve อัตโนมัติหลัง SmcStructure export fix

**autoTradingService.ts**:
- เพิ่ม private methods ที่ขาด: `isMarketOpen()`, `loadConfig()`, `loadSymbolCandles()`, `loadOptionalCandles()`, `upsertJournal()`, `loadOpenJournal()`
- เพิ่ม public `runDeepAnalysis(symbol, tf)` สำหรับ `/deep-analysis` route
- `safeJsonParse<any>(raw)` → `safeJsonParse<any>(raw, {})` (ต้องการ fallback argument)

**types.ts**: เพิ่ม `serverMode?: string` ใน `AutoTradingSnapshot`

**Systemic issue**: Edit tool + cat >> ทำให้ file truncate เพราะ UTF-8 box-drawing chars (`─`, `—`) ใน comments ทำให้ TypeScript parser crash ก่อนถึง end of file ใน esbuild context. ทางแก้: ใช้ Python write ทับ file ทั้งหมดแทน Edit tool สำหรับ large files; หลีกเลี่ยง unicode chars ใน comment lines

**Wiki**: สร้าง `17_PerAgent_ModelRanking_V20.md` ใน .obsidian-wiki/07_Trading_Intelligence/ ครบทุก section

**Why:** การ rewrite ModelRankerService + เพิ่ม slTpAnalystAgent ต้องการ private helpers หลายตัวที่ class เรียกใช้แต่ไม่มี implementation — ทุกตัวเป็น delegate ไปยัง PersistenceService หรือ MarketDataService

**How to apply:** เมื่อ autoTradingService มี missing method errors อีก ให้ตรวจว่ามี delegate อยู่ใน PersistenceService/MarketDataService ก่อนสร้างใหม่
