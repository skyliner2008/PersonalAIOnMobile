---
name: Tool/Provider Audit 2026-05-03
description: ตรวจสอบ tool inventory + แก้ provider compatibility (Claude tool_use streaming, OpenAI/Claude/OpenRouter/LiteLLM tools in non-stream, multi-round message format)
type: project
originSessionId: 7a62e686-75b2-4315-80dd-58879a656700
---
ตรวจ Agent tools ครบ 9 หมวดของ user → coverage 100% (50+ tools ครบ)

**Why:** user ต้องการให้ทุก provider ใช้ tool list 9 หมวดได้

**How to apply:** เมื่อเพิ่ม tool ใหม่ต่อไป จำต้อง register ที่ 3 ที่ — `ToolRegistry.supportedMt5ToolNames` (ถ้าเป็น MT5), `GeminiService.mt5OnlyTradingFunctionNames`/`tvOnlyTradingFunctionNames`, และ `ToolExecutor.enrichMt5Args` (สำหรับ endpoint inference)

Bugs ที่แก้:
1. `trading_mt5_analyze` มี executor + definition แต่ไม่ register → AI มองไม่เห็น (แก้แล้วใน 3 ที่)
2. `trading_mt5_symbol_search` ไม่อยู่ใน GeminiService whitelist → เพิ่มแล้ว
3. ClaudeLlmProvider streaming ไม่ parse `tool_use` content blocks → เพิ่ม handler ของ `content_block_start/delta/stop` + `input_json_delta` accumulation
4. OpenAI/Claude/OpenRouter/LiteLLM `generate()` (non-stream) ไม่ส่ง tools/systemPrompt และไม่ parse tool_calls → เพิ่มครบ
5. LiteLLM ไม่รองรับ tools เลย → เพิ่ม OpenAI-compatible tool format
6. JarvisOrchestrator เคย sanitize messages ครั้งเดียวก่อน loop → ทำให้ round 2+ ไม่เห็น tool turns; แก้เป็น sanitize per-round
7. Provider message serialization ไม่ส่ง assistant `tool_calls` กลับให้ provider → เพิ่ม `putOpenAiMessage` (OpenAI-compat) และ `appendAnthropicMessages` (Claude tool_use/tool_result blocks)
