---
name: MT5 Modify Position + Break-Even (2026-04-21)
description: Added full-stack `/modify` endpoint to change SL/TP of open positions without closing them — unblocks the Edit and Break-Even buttons that were previously stubs
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
สิ่งที่เพิ่ม: เปิดทางให้ mobile app แก้ SL/TP ของ position ที่เปิดอยู่ได้ (ก่อนหน้านี้ไม่มี endpoint — ปุ่ม Edit/Break-Even เป็น stub โชว์ toast ว่า "ยังไม่รองรับ")

**Why:** ผู้ใช้สั่งขยาย agent capability MT5 แบบไร้ขีดจำกัดเพื่อช่วยเทรด + รายงานว่าปุ่มบางปุ่มกดไม่ได้ — Edit/Break-Even คือ 2 ปุ่มหลักที่พัง

**How to apply:** ถ้าเพิ่ม tool MT5 ตัวใหม่ในอนาคต ให้ทำครบทั้ง 4 ชั้น:
1. `mt5_bridge.py` — เพิ่ม function + route dispatch ใน `do_POST`
2. `mt5-core-server/src/routes/mt5.ts` — zod schema + `router.post('/xxx', ...)` พร้อม `callBridge()` fallback paths
3. `TradingToolExecutor.kt` — `executeXxx()` + เพิ่มใน `execute()` switch
4. `TradingToolDefinitions.kt` + `ToolRegistry.kt` — declare tool + register name
5. `ToolExecutor.kt` — อัปเดต `enrichMt5Args()` ให้ตั้ง endpoint ที่ถูกต้อง
6. `JarvisViewModel.kt` — wrapper function เรียก `ToolExecutor.execute(ToolCall(...))`
7. UI layer — wire callback

**Key endpoints/paths:**
- Python bridge: `POST /modify` → ใช้ `mt5.TRADE_ACTION_SLTP` (ไม่ใช่ DEAL — SLTP เป็น action code สำหรับแก้ SL/TP)
- Node proxy: `POST /api/mt5/modify`, default port 8090
- Tool name: `trading_mt5_modify_position` (alias: `mt5_modify_position`)

**Behavior notes:**
- `sl` หรือ `tp` ว่างเปล่า = คงค่าเดิม (Python ใช้ `_coerce(value, default=cur_value)`)
- Break-Even: filter `profit > 0 && priceOpen > 0`, แล้วเรียก modify ด้วย `sl = priceOpen` (ไม่ส่ง tp)
- Dialog UI อยู่ใน `TradingTerminalScreen` เอง (ไม่ใช่ App.kt) — local state `editTarget: Mt5TradeItem?`
- Callback signature เปลี่ยนจาก `onEditPosition: (symbol, ticket) -> Unit` เป็น `(symbol, ticket, sl, tp) -> Unit`
