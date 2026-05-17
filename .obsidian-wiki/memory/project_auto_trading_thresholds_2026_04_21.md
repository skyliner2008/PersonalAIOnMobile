---
name: AutoTrading threshold tuning 2026-04-21
description: ปรับ thresholds ของ Auto-Trading ให้พ้นปากโซน "SKIP หมด" — ลด minConfluence + bias diff + เพิ่ม SKIP rationale วินิจฉัยได้
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
Auto-Trading Engine รันครบ flow (analyze→strategize→execute) แต่ทุก cycle ออก SKIP "พักดูตลาด" ทั้งๆ ที่ enableLiveTrading = ON และพอร์ต demo $300K

**Root cause:** default thresholds เข้มเกินไปสำหรับตลาดปกติ
- `minConfluence = 60` → สูงไป (เทียบน้ำหนัก aligned analyzers × 0.7 + coverage × 30)
- `overallBias` ต้องการ `bullVotes - bearVotes >= 2` → เข้ม เพราะ 10 analyzers มีหลายตัว (news, sentiment, macro, fundamental) ที่มักให้ NEUTRAL → net votes มักไม่ถึง 2 → bias = NEUTRAL → strategies คืน null

**แก้ 3 จุด:**
1. `AutoTradingEngine.Config.minConfluence`: 60.0 → 45.0
2. `CompositeAnalysis.overallBias`: diff threshold 2 → 1 (ที่ AutoTypes.kt)
3. `AutoTradingEngine` SKIP rationale: เพิ่ม vote breakdown + top-3 strategy fitness เพื่อวินิจฉัยได้ว่าติดที่อะไร ("SKIP bias=... conf=X/Y votes=B/S/N regime=... | top3: TF=38, SWING=35, SCALP=30")

**Why:** เพื่อให้ strategy มีโอกาสเสนอ plan — ถ้า user อยาก strict กลับมา สามารถปรับ "Min Confluence" slider ที่ UI ขึ้นได้

**How to apply:** เวลาลูปเดินแต่ไม่ออกออเดอร์ ให้ดู rationale ของ SKIP ใน DecisionList UI:
- ถ้า bias = NEUTRAL → analyzer voting ไม่ชัด → ต้องลด diff หรือ re-weight analyzers
- ถ้า conf < minConfluence → confluence ต่ำ → ลด minConfluence slider
- ถ้า top3 fitness ต่ำ (< minFitness 40) → strategies ไม่ fit regime → รอ regime เปลี่ยน

**Files changed:**
- composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/auto/AutoTradingEngine.kt
- composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/auto/AutoTypes.kt
