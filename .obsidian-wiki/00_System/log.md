# ⬡ บันทึกการเปลี่ยนแปลง (JARVIS Wiki Log) ⬡

บันทึกเหตุการณ์และการเปลี่ยนแปลงสำคัญของโปรเจคในรูปแบบ Chronological

## [2026-05-08] 🧱 V24.2.0 Context Levels (Pivot + VWAP + Fib)
- **Trigger**: Indicator audit (2026-05-08) พบช่องว่าง 30%+ — Pro retail ใช้ Daily Pivot Points, Session VWAP, Fibonacci levels เป็น standard SR confluence แต่ระบบมีแค่ SMC + classical (RSI/SMA/EMA/ATR/MACD/Stoch/BB) → entries และ TP มักวางที่ "SMC OB เท่านั้น" ข้าม pivots/fibs ที่ market ใช้จริง
- **Implementation** (~250 LOC, 1 ไฟล์ใหม่ + 4 ไฟล์แก้):
  - `mt5-core-server/src/services/auto/analyzers/levels.ts` (NEW) — calculator pure functions:
    - `computePivotLevels()` — Standard Floor Pivot P/R1-3/S1-3
    - `deriveDailyOhlcFromIntraday()` — derive D1 OHLC จาก H1 24-bar (ไม่ต้อง fetch D1 จาก MT5 เพิ่ม)
    - `computeSessionVwap(anchorHour=13UTC)` — NY equity-open anchored VWAP + ±1σ
    - `computeFibLevels()` — 0.236/0.382/0.5/0.618/0.786 + extensions 1.272/1.618
    - `buildContextLevels()` — aggregator → ContextLevel[] พร้อม weight 1-3
  - `analyzers/smc/types.ts` — extend `WallSourceType` ด้วย `'PIVOT' | 'VWAP' | 'FIB'`; `WallSource` รับ optional `label?` + `weight?`
  - `analyzers/smc/priceMapBuilder.ts` — เพิ่ม `ExtraContextLevel` type; `buildPriceMap()` รับ `extraLevels[]`; `buildWall()` +1★ ถ้า cluster มี high-weight context (Pivot R1/S1/P, VWAP, Fib 0.618)
  - `analyzers/smc/index.ts` — re-export `ExtraContextLevel`
  - `core/IndicatorPipeline.ts` — `buildContextLevelsFor()` คำนวณทุก cycle + log diagnostic: `Context levels — Pivot P=4714.67 R1=4746.33 S1=4689.33 | VWAP=4719.30 dev=+1.85 (47 bars) | Fib swing=[4683, 4740] 0.618=4704.77`
- **ผลกระทบ chain**:
  - PriceMap: walls แข็งขึ้น (+1★ เมื่อตรงกับ context สูงน้ำหนัก)
  - ProximityGate / ZoneAwareGate: detect "near support" ได้แม่นกว่า
  - V24.1.3 TP cap: suggestedTP (before first 3★+ wall) ใช้ context-stars layer ใหม่ → TP realistic กว่าเดิม
  - Stale-Stop: identify position camping near Pivot/VWAP ได้ไว
- **Coverage หลัง V24.2**: ระบบครอบคลุม indicator stack ที่ pro retail ใช้จริง ~85% (เดิม ~70%) — gap ที่เหลือคือ Volume Profile, Cumulative Delta, Funding Rate, On-chain
- **Files Changed**: ดู memory `project_v24_2_context_levels.md`

## [2026-05-08] 🎯 V24.1.3 PriceMap-Aware TP Cap
- **Trigger**: XAUUSD live trade — entry 4719.15, TP=4766.42 (RRR 7.84) แต่ระหว่างทางมี 4★ wall ที่ 4734.47 (M5+M15+M30+H1+H4) + 3-stack 4727/4731/4732 → TP ที่ตั้งไว้ไกลเกินไป ราคาน่าจะ reject ที่ 4734 ก่อนถึง TP ทำให้ position stall
- **Root Cause**: `autoTradingService.ts:1402-1414` หา TP จาก `smcSnap.bearOBs` (institutional Order Blocks) เท่านั้น — ข้าม swing/liquidity walls ที่อยู่ระหว่าง entry กับ OB ที่ใกล้สุด แม้ walls นั้นจะแข็ง 3-4★ ผ่านหลาย TFs
- **Fix**: ใช้ `analyzePath()` (มีอยู่แล้วใน `auto/analyzers/smc/priceMapBuilder.ts`) cap eaTp ให้อยู่ก่อน wall 3★+ แรก:
  - หลังคำนวณ `eaTp` จาก bearOBs → เรียก `analyzePath(eaPriceMap, last.c, 'UP'|'DOWN')`
  - ถ้า `eaTp > path.suggestedTP` (overshoot) → replace ด้วย suggestedTP
  - Log: `TP capped by PriceMap: 4766.42 → 4722.50 suggested=4722.50 conservative=4716.30 (obstacle 3★ @ 4722.95)`
- **Files Changed**: `mt5-core-server/src/services/autoTradingService.ts` (import + EA-Only TP block)
- **คาด**: TP realistic หลายเท่า → BE/Trail trigger ได้จริง → win rate เพิ่ม

## [2026-05-08] 🩹 V24.1.2 Dynamic BE Buffer (spread-aware)
- **Trigger**: หลัง V24.1.1 deploy — XAUUSD spread=32pts (0.32 USD) แต่ BE buffer hardcoded 0.5 USD = แค่ 1.56× spread → BE ขยับ SL ใกล้ entry มาก (4719.44 → 4719.65 = 21 cents) เสี่ยงโดน spread widening เคาะปิดทันที กำไรเหลือศูนย์หรือติดลบ
- **Root Cause**: `buffer = position.symbol.includes('XAU') ? 0.5 : 0` ไม่สนใจ spread จริงของ broker — symbol เดียวกันแต่ broker คนละแบบ spread ต่างกัน 5-10 เท่า
- **Fix**: ใช้ `spreadBaseline.baseline(symbol)` ที่ track median spread มาคำนวณ dynamic buffer:
  - `buffer = max(2.5 × baseline_spread_price, fallback)` ใน BE block และ CPP block
  - `spreadBuffer (10016 guard) = max(2.0 × baseline_spread_price, fallback)`
  - `trailDistance (ATR fallback) = max(legacy, 3.0 × baseline_spread_price)` กัน trail tight เกิน
  - BE log แสดง `buffer= spreadBaseline=Xpts=Y.YY` ให้ตรวจสอบ math ได้
- **Math example** (XAU spread=32pts, tick=0.01): baseline_price = 0.32 USD → buffer = max(2.5×0.32, 0.5) = **0.80 USD** → SL = entry+0.80 → lock net profit ≥0.48 USD หลัง spread cost
- **Files Changed**: `mt5-core-server/src/services/autoTradingService.ts` (BE block, CPP block, ATR trail block, BE log message)

## [2026-05-08] 🩹 V24.1.1 Stale-Stop Hotfix (post-deploy)
- **Trigger**: Live log หลัง V24.1 deploy แสดง Stale-Stop block ไม่ทำงาน — XBT#151440996 ค้างที่ R=-0.07 อายุ 173 นาที ไม่ถูกปิด ขณะที่ patches อื่น (BE/TRAIL 0.5/0.6, SMC-Trail, MTF-aware exemption, ProximityGate hardened) ทำงานครบ
- **Root Causes** (2):
  1. **Clock skew**: `p.openedAtMs` จาก MT5 broker อาจสูงกว่า server `Date.now()` (UTC offset / network delay) → `nowMs - openedAt` เป็นค่า **ลบ** → เงื่อนไข `ageMs >= staleMaxAge` false ตลอด → gate disabled แบบเงียบ. ปัญหาเดียวกับที่ orphan-backfill เคย comment ไว้ที่ L2677
  2. **Log invisibility**: ใช้ `atWarn(...)` ส่ง Stale log แต่ user filter อาจ strip warn → ผม/ผู้ใช้ debug ไม่เห็น
- **Fixes**:
  - `mt5-core-server/src/services/autoTradingService.ts` Stale-Stop block:
    - ใช้ `Math.min(p.openedAtMs, j.createdAt)` (timestamp เก่ากว่า) แทน `??` chain
    - `ageMs = Math.max(0, nowMs - openedAt)` clamp ค่าลบ
    - เปลี่ยน `atWarn` → `atLog` (stale-exit ปกติไม่ใช่ warning)
    - เพิ่ม per-cycle summary `Stale-Stop swept N pos → candidates=X closed=Y (maxAge=Mm, deadZone=[a,b])` เพื่อ confirm gate ทำงานทุกรอบแม้ไม่มี candidate
- **Impact คาด**: รอบถัดไป log จะเห็น `Stale-Stop swept 3 pos → candidates=2 closed=2` และ position ค้างใน R∈[-0.25, +0.30] นาน >45m จะถูกปิดจริง
- **Docs**: memory `project_v24_ea_manager_overhaul.md` (V24.1.1 section)

## [2026-05-08] 🚑 V24.1 EA-Only Manager Overhaul (8 fixes)
- **Trigger**: Live log analysis (08-05 12:15–12:37, 22 cycles) แสดงพฤติกรรมขัดแย้งกับกราฟ XAUUSD/XBTUSD — Bias=BEAR ค้าง 22 cycles แม้ M15 chart BMS bullish; BE/TRAIL ไม่เคย activate; SELL position #151307441 ขาดทุน -0.6R โดยไม่มี exit; ProximityGate BYPASSED ใน RANGING regime
- **Root Causes พบ**:
  - `computeSmcTrailTargets` + `detectRegimeFlipAction` import แล้วไม่เคยถูกเรียก (dead code)
  - `breakEvenTriggerR=1.0R` / `trailAfterR=1.0R` สูงกว่า R-distribution จริง (สูงสุด +0.1R)
  - Counter-trend gate ใช้ M15 bias เดียว ไม่สนใจ H4 → block valid reversal
  - ProximityGate bypass สำหรับ BREAKOUT strategy แม้ H4=RANGING (false breakout)
  - ไม่มี time-based exit สำหรับ position ค้างใน mid-zone
- **Fixes (Files Changed)**:
  1. `mt5-core-server/src/services/auto/core/PersistenceService.ts` — `breakEvenTriggerR: 0.5`, `trailAfterR: 0.6`, +`stalePositionMaxAgeMs/MinR/MaxR`, migration ลด config เก่า ≥1.0 → defaults ใหม่
  2. `mt5-core-server/src/services/auto/types.ts` — เพิ่ม `stalePositionMaxAgeMs?`, `stalePositionMinR?`, `stalePositionMaxR?` ใน `AutoTradingConfig`
  3. `mt5-core-server/src/services/autoTradingService.ts`:
     - Manager audit: BE/Trail trigger scaled (scalp 0.3R / swing 0.5R) + position age (`age=Nm`)
     - Stale-Position Time Stop block: ปิด position ค้างใน R∈[-0.25, +0.30] นานกว่า 45 นาที
     - SMC-Aware Trail block: เรียก `computeSmcTrailTargets()` + `getSmcSnapshotV2()` ทุก cycle ต่อ symbol
     - Regime-Flip Exit block: เรียก `detectRegimeFlipAction()` ปิด losing leg เมื่อ CHoCH against cluster
     - ATR fallback trail skip ถ้า `smcAlreadyTrailed=true`
     - ProximityGate bypass: ต้อง H4=TRENDING_*/VOLATILE_BREAKOUT เท่านั้น (ไม่ bypass ใน RANGING)
     - Counter-trend gate (deterministic + post-AI): MTF-aware — ยกเว้นถ้า `analyses['H4'].bias` agree กับ intended side
- **Impact คาด**:
  - Position ที่ค้างไม่ขยับ → ปิดอัตโนมัติภายใน 45 นาที (ลด opportunity cost)
  - กำไรเล็กน้อยถูก lock เร็วขึ้น (BE/TRAIL ที่ 0.5/0.6R)
  - SL trail ไปขอบ OB/FVG จริง (ไม่ใช่สูตร ATR คงที่)
  - Regime flip บน H4 → ปิด losing leg ก่อน SL hit
  - BUY-on-pullback ใน H4=BULL ไม่ถูกตีเป็น counter-trend
- **Verify**: bash mount เป็น snapshot frozen ที่ session start → run `npx tsc --noEmit` บน Windows host เพื่อ verify
- **Docs**: [[01_Architecture/04_AutoTrading_Workflow]] (TBA), memory `project_v24_ea_manager_overhaul.md`

## [2026-05-03] 🔧 OpenAI Model Listing Filter Fix
- **Action**: เปลี่ยน OpenAI `listModels()` จาก allowlist-by-prefix เป็น blocklist — exclude non-chat models แทน
- **Root Cause**: Filter เดิมกรองเฉพาะ `gpt*|o1*|o3*|chatgpt*` แต่ OpenAI มี models ใหม่ (`o4-mini`, `codex-*`, `gpt-5*`) ที่หลุด filter → โมเดลไม่ขึ้นในหน้า Settings
- **Files Changed**: `OpenAILlmProvider.kt`
- **Context**: Providers อื่น (OpenRouter, Claude, Gemini) ไม่มี filter เข้มจึงไม่เจอปัญหา

## [2026-05-03] 🔧 Streaming Tool-Calls Delta Accumulation Fix
- **Action**: แก้ 3 providers (OpenRouter, OpenAI, LiteLLM) — streaming `delta.tool_calls` ถูกสร้างเป็น `LlmToolCall` ใหม่ทุก SSE chunk แทนที่จะสะสมตาม `index`
- **Root Cause**: OpenAI streaming ส่ง tool_calls เป็น delta chunks หลายอัน (chunk แรก = id+name, chunks ต่อมา = arguments ทีละส่วน) — โค้ดเดิมสร้าง object ใหม่ทุก chunk → Orchestrator concat ทั้งหมด → serialize กลับ API มี `{id:'', type:'function'}` ไม่มี `function` field → 400 Bad Request
- **Fix**: เพิ่ม `toolCallAccum: MutableMap<Int, Triple<String, String, StringBuilder>>` สะสม deltas ตาม index, emit batch เดียวหลัง stream จบ
- **Files Changed**: `OpenRouterLlmProvider.kt`, `OpenAILlmProvider.kt`, `LiteLlmProvider.kt`
- **Context**: พบจาก model `tencent/hy3-preview` ผ่าน SiliconFlow provider ใน OpenRouter

## [2026-05-03] 📦 ONNX Runtime 1.19.2 → 1.23.0 (16 KB Page Size)
- **Action**: อัปเกรด `onnxruntime-android` เพื่อ comply กับ Google Play 16 KB page size requirement (บังคับ Nov 2025)
- **Files Changed**: `composeApp/build.gradle.kts`, `.obsidian-wiki/02_Components/LocalEmbeddingSystem.md`
- **Context**: `libonnxruntime.so` + `libonnxruntime4j_jni.so` ใช้ 4 KB LOAD alignment ใน v1.19.2; v1.23.0+ build ด้วย 16 KB alignment

## [2026-05-01] 🔍 V20.0 Agent Coordination Audit & Wiki Restructure
- **Action**: ตรวจสอบ flow ทั้งระบบ Agent 4 ตัว, สร้าง Interaction Matrix, ค้นหา Bug
- **Result**: พบและแก้ไข slTpAgent double-record ใน ModelRanker
- **Result**: แก้ modelUsed format (ไม่มี provider prefix ปน), แก้ agents ทุกตัวที่ใช้ split(' ')
- **Result**: เพิ่ม fallback → Settings auto-persist ให้ Dashboard สะท้อนโมเดลที่ใช้งานจริง
- **Result**: ปรับ Wiki ทั้งหมด — index, overview, MOC, Current_Tasks ให้เชื่อมโยงกัน
- **Docs**: [[18_V20_Agent_Coordination_Audit]], [[17_PerAgent_ModelRanking_V20]]
- **Context**: V20.0 เสถียร, ระบบเทรดทำงาน 24/7, 8+ positions managed

## [2026-05-01] 🛡️ V20.0 Per-Agent Model Ranking Stabilization
- **Action**: Hard Risk Gate fix (float-safe RRR compare + 1-tick TP buffer)
- **Action**: Toxic Model Protection (streak≥5 exempt from force-reset)
- **Action**: slTpAgent inheritance from reasoning model (prevent 403)
- **Action**: slTpAgent เพิ่มใน Dashboard Settings UI
- **Result**: ระบบ AI Pipeline ทำงานเสถียร ไม่มี false-positive RRR rejection
- **Result**: Smart Fallback rotate โมเดลอัตโนมัติเมื่อเจอ 429/403
- **Docs**: [[17_PerAgent_ModelRanking_V20]], [[Smart_Fallback_and_Token_Management]]
- **Context**: แก้ไข bugs ที่ทำให้ trades ถูก reject ผิดพลาด และ model loop ซ้ำ


## [2026-04-15] 🚀 เริ่มต้นระบบ Obsidian LLM-Wiki
- **Action**: ติดตั้งโครงสร้างไดเรกทอรีพื้นฐาน `.obsidian-wiki/`
- **Result**: สร้างหน้า `index.md`, `log.md`, และ `schema.md` สำเร็จ
- **Context**: เริ่มเปลี่ยนจากการใช้ไฟล์คู่มือแบบ Single-file (`jarvis_vision_manual.md`) มาเป็นระบบ Wikibase กระจายศูนย์ตามแนวคิดของ Karpathy

## [2026-04-15] 📥 Ingest Android Skills from GitHub
- **Action**: ดึงข้อมูลจาก `android/skills` และกระจายลงในโฟลเดอร์ `05_Android_Skills/`
- **Result**: เพิ่มหน้ารวม 6 Skills สำคัญ (AGP 9, Compose Migration, Navigation 3, R8, Play Billing, Edge-to-Edge)
- **Context**: ยกระดับความรู้ในระบบให้เป็นมาตรฐานล่าสุดของ Google เพื่อลดความเสี่ยงในการเขียนโค้ดที่ผิดพลาด


## [2026-04-15] 🌌 Ingest Gemini Skills from GitHub
- **Action**: ดึงข้อมูลจาก `google-gemini/gemini-skills` และติดตั้งใน `06_Gemini_Skills/`
- **Result**: เพิ่ม 2 Skills สำคัญ: `gemini-api-dev` และ `gemini-live-api-dev` (โมเดลตระกูล 3.1)
- **Context**: ยกระดับระบบ JARVIS ให้รองรับ SDK ล่าสุด และมาตรฐาน Gemini 3.1 เพื่อประสิทธิภาพการตอบสนองที่ล้ำสมัยที่สุด


## [2026-04-15] 📈 Upgrade to Trading Intelligence V10.0 (The Bible)
- **Action**: บูรณาการเครื่องมือเทรดทั้ง 18 ชนิด เข้ากับกลยุทธ์ Wyckoff, ICT และ SMC V10
- **Result**: สร้าง 7 ไฟล์ความรู้ใหม่ในหมวด `07_Trading_Intelligence/` พร้อม Checklist SOP
- **Context**: ยกระดับ JARVIS จากผู้ช่วยทั่วไปสู่ "นักวิเคราะห์สถาบัน" ที่ใช้กระบวนการวิเคราะห์แบบ Top-Down ขั้นสูง

---
> [!TIP]
> รูปแบบการบันทึก: `## [YYYY-MM-DD] | ประเภทกิจกรรม | หัวข้อเรื่อง`

## [2026-04-27] 🛠️ Deep Analysis Stability & Recursive Loop Fix
- **Action**: อัปเกรด `GeminiService.kt` เพื่อเสถียรภาพในการวิเคราะห์ข้อมูลซับซ้อน (เช่น ทองคำ/XAUUSD)
- **Result**: ขยาย `maxRounds` จาก 5 เป็น 10 รอบ เพื่อรองรับการเรียก Tool ต่อเนื่องหลายขั้นตอน
- **Result**: เพิ่มระบบ **Force Final Summary** — หากถึงขีดจำกัดรอบ (Max Rounds) ระบบจะบังคับให้ AI สรุปผลลัพธ์ทันที แทนการหยุดทำงานแบบดื้อๆ
- **Result**: เพิ่มระบบ **Context Truncation** สำหรับ Tool Results (>10k chars) ป้องกันข้อผิดพลาด "Request Entity Too Large"
- **Context**: แก้ไขปัญหา "0 chars response" และ "Internal Server Error" เมื่อ AI พยายามวิเคราะห์ข้อมูลจำนวนมากพร้อมกัน

## [2026-04-27] 🛠️ Advanced Analysis Suite & News Fallback Patch
- **Action**: อัปเกรด `TradingToolExecutor.kt` เพื่อรองรับเครื่องมือวิเคราะห์ขั้นสูงครบวงจร (V16.0+)
- **Result**: เพิ่มการรองรับ `trading_fundamental_analysis`, `trading_fear_greed`, และ `trading_mt5_trade_journal` ใน Mobile App
- **Result**: Patch ระบบ News Fallback สำหรับ XAUUSD (Gold) ให้สลับไปดึงข้อมูล Global Macro อัตโนมัติหากไม่พบข่าวเจาะจง
- **Result**: เพิ่ม Smart Base URL Detection ใน `executeFearGreed` เพื่อรองรับการสลับระหว่าง MT5 และ Auto-Trading endpoints
- **Context**: แก้ไขปัญหา "No news found" และ "No response" ที่พบในการทดสอบจริง เพื่อเสถียรภาพสูงสุดของ Deep Analysis Suite

## [2026-04-25] 🛡️ Institutional Defense Upgrade (V24.0)
- **Action**: Implement ระบบ **Zone-Aware Gate** (Premium/Discount blocking) และ **Dynamic SL Buffer**
- **Action**: อัปเกรดระบบ **Staged BE/Trail** (3 Stages) และ **Smart Scale-In Guard**
- **Action**: ติดตั้งระบบ **Pre-Analysis Prior** เชื่อมต่อ Vector Store เพื่อเรียนรู้จาก Setup ที่เคยผิดพลาดในอดีต
- **Result**: ระบบเทรดมีความแม่นยำระดับสถาบัน (Institutional Grade) และมีกลไกป้องกันพอร์ตเชิงรุก
- **Result**: เพิ่ม Metrics ข้อมูลเชิงลึก (`mt5_zone_gate_blocks_total` ฯลฯ) สำหรับการมอนิเตอร์ผ่าน Grafana และ Mobile UI
- **Context**: ยกระดับ JARVIS จากผู้ช่วยเทรดสู่ระบบ Semi-Autonomous Trader ที่มีความสามารถในการเรียนรู้และป้องกันความเสี่ยงอัตโนมัติ

## [2026-04-16] Fix: Wiki-guided Stability Hardening
- **Action**: Reviewed `.obsidian-wiki` architecture/component notes (`01_Architecture`, `02_Components`) and aligned code with the documented routing + persistence intent.
- **Result**: Patched Tool lifecycle so System diagnostics receives the latest side-effect delegate (prevents stale/null delegate after init order changes).
- **Result**: Added safety guards for file mutations (`file_delete`, `file_move`) to block unsafe root-level or out-of-scope path operations.
- **Context**: Based on `Architecture_Overview` (dual-path reliability) and `Memory_Strategy` emphasis on safe persistence behavior.

## [2026-04-16] Knowledge Base: Lightweight Charts Docs Ingested
- **Action**: Curated `.obsidian-wiki/08_lightweight-charts-docs-api` into a reusable knowledge hub.
- **Result**: Added `Knowledge_Hub.md` with integration rules, wrapper strategy, and implementation checklist.
- **Context**: This hub is now the reference for chart integration decisions in PersonalAIBot.
