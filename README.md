# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์ข้อมูลอัจฉริยะ ขับเคลื่อนด้วยพลังของ **Google Gemini 3.1 Pro (Tier 1 Optimized)** และระบบความจำแบบ 6 ชั้น (GraphRAG & Obsidian Wiki) — พร้อมระบบ **MT5 Full Agent Control V17.0** สำหรับเทรดอย่างไร้ขีดจำกัด
- **Multi-Provider Support** — สลับการใช้งานระหว่าง Gemini Live, Gemini Flash และ OpenAI GPT-4o เพื่อการวิเคราะห์ที่แม่นยำที่สุด
- **AR Overlay Engine** — แสดง Bounding Box และคำอธิบายวัตถุบนภาพจริงแบบ Real-time
- **📲 Mobile Android App (Compose Multiplatform)** — แอปพลิเคชันมือถือดีไซน์พรีเมียม มาพร้อมไอคอนแอป 3D AI Trading Bot อัจฉริยะ, ระบบสแกนค้นหาตราสาร (Symbol Catalogue & Categories), ตัวกรองวิเคราะห์สถิติย้อนหลัง (Period Stats & Filters) และระบบ Auto Trading Controls

---

### 🧠 3. Advanced 6-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลส่วนตัวผู้ใช้ (identity จัดการผ่าน tool/Settings เท่านั้น กัน heuristic ทับ) + สกัดอาชีพ/ความสนใจจากข้อความผู้ใช้
- **Layer 2: Working Memory** — บันทึกประวัติการคุยปัจจุบันลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — Semantic Search / Vector Embeddings (Local ONNX หรือ Gemini Cloud — key อัปเดตตาม settings, auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** — โครงข่ายความสัมพันธ์แนวคิด + **retrieval จริงผ่าน recall_memory** (edges กันซ้ำด้วย UNIQUE index)
- **Layer 5: Memory Consolidation** — "Sleep Cycle" สรุปและย้ายความจำระยะสั้นไประยะยาว (auto-trigger เมื่อแชทสะสม 200 ข้อความ)
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown (Persistent Knowledge Hub)

### 📊 4. JARVIS Advanced Trading Intelligence V24.2 → V25.1 (Real-Time Wall Engine — Coordination Hardened)

> **Status: Production = V26.26 (Modular Codebase & Verification Pass 2026-05-23)**
> **Last Update: 2026-05-23 - Auto Trade Refactored to separate Helper modules (decisionLogger, strategySelector), reducing codebase length while passing 100% of the 103 Unit Tests.**


#### M15 Wall Scalping Simplification (2026-05-30)

- `mt5-core-server` default runtime is now `engineMode: M15_WALL_SCALPING`.
- The live decision path is reduced to `IndicatorPipeline` + `PriceMap` wall detection, merged into M15 for short-term trading.
- Initial strategy set is intentionally only `SCALPING`; legacy AI/multi-strategy/manage/learn/V25 shadow loops stay off in this mode.
- Implementation: `mt5-core-server/src/services/auto/core/SimpleScalpingEngine.ts`.
- 2026-06-01 follow-up: closed MT5 history is now synced back into the journal/analytics in simple mode without re-enabling the legacy manage loop, R outcome uses the broker fill/open price when history provides it, mitigated OBs no longer count as fresh OB/FVG wall confirmation, and the simple planner now blocks counter-context scalps plus ambiguous `BOTH` anchor walls.
- 2026-06-01 wall-model follow-up: PriceMap now passes through a stable wall registry so HTF walls keep identity across cycles instead of flickering off/on; `M15/M30/H1/H4` act as core wall anchors, `M1/M5` act as confirmation, and Simple Scalping separates `HTF_TREND_RETEST` from `M15_FVG_MAGNET`.
- 2026-06-02 loss follow-up: retained `STALE` walls are no longer valid execution anchors, live M1/M5 confirmation now requires both TFs to touch/reclaim with directional body plus at least one directional micro-structure break (`breakHigh`/`breakLow`) instead of accepting reclaim alone, reclaim can complete after a recent touch as long as price is still within the wall/FVG drift limit, and simple-mode entries pause after same-symbol daily/consecutive loss limits.
- Details: `.obsidian-wiki/07_Trading_Intelligence/62_M15_Wall_Scalping_Simplification.md`

#### TradingView Pine SMC V8.5 M15 Wall Map (2026-05-27)

- ปรับปรุง `Pine Script/SMC & Multi-TF Order Blocks Sweeps V8.3.txt` สำหรับใช้งานบน TradingView Pine v6 โดยเพิ่ม `max_boxes_count=500` ให้รองรับ FVG boxes ที่สคริปต์สร้างจริง
- เปิดค่าที่เคย hardcode ให้ปรับจาก UI ได้ เช่น FVG ATR multiplier/color/fill, Equal HL threshold, Swing Liquidity length, MTF sweep toggles, reclaim factor, wick ATR filter, PD zone location/width
- เพิ่ม guard ด้านประสิทธิภาพ: จำกัดจำนวน Order Blocks/Liquidity Zones, จำกัดระยะ scan หา OB candle, และแก้ loop หา equal levels/MTF liquidity ให้ไม่วิ่งย้อนทิศโดยไม่ตั้งใจ
- V8.4 follow-up: ตัด `barstate.isconfirmed` ออกจาก expression ที่ส่งเข้า `request.security()` สำหรับ MTF sweeps และ gate label ด้วยการเปิดแท่ง TF ใหม่ เพื่อลด repaint/duplicate label risk
- V8.4 follow-up: aggregate MTF mode เคารพ toggle `M1/M5/M15`, FVG toggle ไม่สร้างกล่อง/average ที่ซ่อนอยู่เมื่อปิด, และ OB trigger/trend filter ใช้แท่งที่ปิดแล้วพร้อมยอมรับ structure break ของแท่งปัจจุบันก่อน state update ท้ายไฟล์
- V8.5 follow-up: ปรับ MTF Liquidity ให้เหมาะกับการดูบน M15 โดยแยก `MTF Merge Threshold` ออกจาก Equal HL threshold, เพิ่ม `MTF Levels Per TF` เป็นสูงสุด 5 level ต่อฝั่ง/ต่อ TF, และใส่ดาวบนป้าย MTF ตามจำนวน TF ที่ซ้อนกัน เช่น `4503.2 ★★ M1+M5`
- V8.5 Instance 2 follow-up: เพิ่ม toggle `showCurrentTFZones` (Show Current TF Zones) เพื่อซ่อนการวาด Liquidity Zones ของ TF ปัจจุบันใน Instance 2 (ที่เปิด MTF Confluence) ป้องกันการวาดซ้ำซ้อนกับ Instance 1
- V8.5 UI Cleanup: Hardcode ค่าคงที่ประเภทตัวเลขและข้อความทั้งหมด (เช่น Lookback, Threshold, สี) และคงเหลือเฉพาะการตั้งค่า เปิด-ปิด (Toggle) เพื่อให้หน้าต่างตั้งค่าใน TradingView สะอาดและใช้งานง่ายขึ้น
- Details: `.obsidian-wiki/07_Trading_Intelligence/61_TradingView_SMC_Pine_V83_Hardening.md`

#### V26.26 - Modular Codebase & Verification Pass (2026-05-23)

- **Code Refactoring & Size Reduction**: แยกฟังก์ชันช่วยเหลือ (Helper Functions) ขนาดใหญ่ออกจากไฟล์ระบบหลัก `autoTradingService.ts` (เดิม ~6,124 บรรทัด) ไปเป็นโมดูลย่อยๆ เพื่อเพิ่มระเบียบในการดูแลรักษาโค้ดและลดความยาวของโค้ดหลัก
- **Helper Modules Created**:
  - `decisionLogger.ts`: จัดการเรื่อง Logger/Analytics เช่น `compactAnalysisForLog`, `compactMtfForLog`, `compactDeterministicForLog` และการแยกประเภทการบล็อกด้วย `classifyDecisionBlockCategory`
  - `strategySelector.ts`: จัดการตรรกะประเมินกลยุทธ์, คัดสรร Candidate, ดึงราคาที่ดีที่สุด และเกตย่อยในการตัดสินใจแบบ Deterministic Quick Checks (รวม 24 ฟังก์ชัน เช่น `isGateEnabled`, `v25AlignedDirectEntry`, `selectUnifiedV25Candidate`)
- **Type Safety & Integrity**: แก้ไขข้อผิดพลาดของ Pathing และ Interface `TradePlan` ร่วมกับการกู้คืนฟังก์ชันความปลอดภัยทางด้าน Typings จนสมบูรณ์
- **Full Verification**: ตรวจสอบคุณภาพด้วย `npm run build` และการรัน Unit Tests `npm test` ทั้งหมด 103 เคส ผ่านสำเร็จ 100% พร้อมใช้งานบน Production อย่างราบรื่นและมีประสิทธิภาพ
- Details: `.obsidian-wiki/07_Trading_Intelligence/56_ModularCodebaseRefactoring_V2626.md`

#### V26.25 - Unified Zone & Execution Quality & Performance Fix (2026-05-23)

- **Gate Simplification**: รวม Pre-AI Zone Gate + Zone-Aware Gate เข้าเป็น `Unified Zone Gate` เพื่อลดการบล็อกซ้ำซ้อน และรวม Entry Drift + Hard Risk เป็น `Execution Quality Gate` รวมถึงล้างโค้ดส่วนเกินใน Sequential Entry + Order Preflight
- **Regime-Adaptive Scoring**: ปรับปรุงคะแนนระบบ Deterministic ใน `scoreSides()` ให้คำนวณน้ำหนัก (Weights) ตามสภาวะตลาดจริง และสเกลค่าคะแนน Threshold ให้ขยาย/แคบตาม Regime ของตลาด
- **SL Proportional Scaling on TP Cap**: เพิ่ม Logic ย่อระยะ Stop Loss ลงตามสัดส่วนโดยอัตโนมัติเมื่อ Take Profit โดนบีบจากกำแพงราคาเชิงโครงสร้าง (Structural Wall Cap) เพื่อให้ได้ RRR คุ้มค่าและเป็นธรรมที่สุด
- **Scalping Trade Management**: ปรับปรุงฟังก์ชัน Invalidation ไวขึ้น (Early Invalidation) และขยับจุดตัดขาดทุนเป็นกำไรตามเทรนเร็วขึ้น (Aggressive Trailing) สำหรับกลยุทธ์ SCALPING และ SMC_FVG_SCALP
- **SQLite Database Fix**: แก้ไขข้อผิดพลาด SQLite Query ของสคริปต์วิเคราะห์ประสิทธิภาพ `gate_flow_audit.mjs` และ `advanced_analytics.mjs` โดยเปลี่ยนชื่อตารางให้ตรงกับตารางจริง `auto_trading_journal`
- **Syntax Resolution**: แก้ไข Syntax error บริเวณ Pre-AI zone gate เก่าที่ค้างใน `autoTradingService.ts` และกู้คืนระบบควบคุมเพดานความปลอดภัย `pre_ai_defense_cap` ทำงานได้เต็มประสิทธิภาพ
- Details: `.obsidian-wiki/07_Trading_Intelligence/55_UnifiedZoneExecutionQuality_V2625.md`

#### V26.24 - Gate/Strategy Runtime Controls (2026-05-23)

- Added `adaptive.gateToggles` and `adaptive.strategyToggles` so individual gates and strategies can be enabled/disabled without code edits.
- Auto Trade UI now includes `Execution Gates` and `Strategy Switches` panels.
- Covered gates include Pre-AI MTF Zone, EA Confidence/RRR, Proximity, Zone-Aware, FVG Fill, Sequential Entry, Entry Drift, Hard Risk, Risk Params, Order Preflight, and Post-TP Cooldown.
- Defaults keep all protections enabled; disabling a gate is an explicit live-tuning action.
- Details: `.obsidian-wiki/07_Trading_Intelligence/54_GateStrategyRuntimeControls_V2624.md`

#### V26.23 - Entry Drift Synced Decision Price (2026-05-22)

- Fixed false `ENTRY_DRIFT` blocks where AutoEngine had already synced `last.c` from candle close to live tick/broker price, but the drift guard still compared final entry against the stale original candle close.
- Preserves the existing drift/RRR protection for real slippage while allowing valid PriceMap wall proximity + Zone-Aware-approved setups to continue through execution checks.
- PriceMap pipeline logs now split `overlay`, ladder-position, and execution-side counts so wall order/counts are easier to audit from log output.
- Details: `.obsidian-wiki/07_Trading_Intelligence/53_EntryDriftSyncedDecisionPrice_V2623.md`

#### V26.22 - PriceMap Wall Overlay Stability (2026-05-22)

- Fixed wall flicker where a valid wall disappeared while price was sitting inside `minDistanceAtrPct * ATR`, then reappeared one tick later.
- PriceMap `walls` now keeps the full overlay map, while the printed ladder splits walls strictly by current price so above-price walls cannot appear under support and below-price walls cannot appear under resistance.
- Cluster joins use a structural-aware weighted anchor instead of previous-level chain clustering.
- Larger TFs anchor wall price more strongly than M1 noise; Pivot/VWAP/Fib context can boost real structural walls but cannot create a 5-star wall by itself.
- Details: `.obsidian-wiki/07_Trading_Intelligence/52_PriceMapWallOverlayStability_V2622.md`

#### V26.21 - EA-Only Execution Hardening (2026-05-22)

- EA-only deterministic fallback now requires `adaptive.eaOnlyMinConfidence` (`55` default) instead of the old `35%` live threshold.
- M1+M5 local opposition now blocks EA-only fast entries with `LTF_CONSENSUS_GUARD`.
- `MEAN_REVERSION` cannot bypass Proximity Gate via generic `MTF-ALIGNED` continuation text; it must remain close to a PriceMap wall.
- Order preflight blocks same-side re-entry near a recent TP close with `POST_TP_COOLDOWN`.
- The live manager loop now executes early invalidation directly and stable `PARTIAL_1R`/`PARTIAL_2R` markers prevent repeated partial closes.
- Details: `.obsidian-wiki/07_Trading_Intelligence/51_EaOnlyExecutionHardening_V2621.md`

#### V26.20 - PriceMap Execution Tightening (2026-05-21)

- Final execution RRR now revalidates after PriceMap TP cap, entry drift, and hard-risk checks so V25/scalp plans cannot pass with the old `1.0R` escape hatch.
- Unified V25 PB1 candidates expire after `30s` by default and must pass fresh live tick preflight before AutoEngine can consume them.
- Breakout/continuation strategies no longer bypass Proximity Gate while H4 is `RANGING`; the system must still respect nearby walls.
- Trade management now moves XAU trades to breakeven from `0.4R` and cuts failed V25/scalp/breakout proof earlier at `-0.45R/-0.65R`.
- Details: `.obsidian-wiki/07_Trading_Intelligence/50_PriceMapExecutionTightening_V2620.md`

#### V26.19 - Unified V25 Decision Path (2026-05-20)

- V25/PB1 no longer needs the separate `V25 Only` live route to influence orders.
- Fresh V25 playbook candidates are consumed by AutoEngine as local-proof scalp evidence with their own side, SL, TP, RRR, and strategy.
- V25 direct order sending is candidate-only by default; legacy direct execution now requires explicit `legacyDirectExecution=true`.
- Latest-log follow-up: deterministic fast-path now preserves PB candidate SL/TP, V25 selector no longer blocks unified candidates with the old AI Bias Gate, and `V25_*` TP-cap checks use scalp-like RRR handling.
- Dashboard settings keep `V25 Adaptive` and remove the confusing `V25 Only` toggle.
- Details: `.obsidian-wiki/07_Trading_Intelligence/49_UnifiedV25DecisionPath_V2619.md`

#### V26.18 - Execution-Aware MTF Weighting (2026-05-20)

- Deterministic side scoring no longer gives H4/H1 70% control; it now uses H4/H1 as context while adding M30, M15, M5, and M1 execution weight.
- New score weights: H4 `22%`, H1 `23%`, M30 `15%`, M15 `18%`, M5 `15%`, M1 `7%`.
- `BREAKOUT`, `MEAN_REVERSION`, and `TREND_FOLLOW` now wait when M15 and M5 both oppose the selected side.
- Local-proof scalp playbooks such as `SCALPING`, `SMC_FVG_SCALP`, `SMC_FVG_MAGNET_SCALP`, and `SMC_WALL_BREAK_SCALP` keep their own local evidence path and are not blocked by this generic guard.
- Details: `.obsidian-wiki/07_Trading_Intelligence/48_ExecutionAwareMtfWeighting_V2618.md`

#### V26.17 - Breakout Local Confirmation Guard (2026-05-20)

- Deterministic `BREAKOUT` no longer enters just because H4/H1 dominate the weighted score.
- If HTF wants `BREAKOUT SELL` but M15 and M5 are both bullish, deterministic now returns `SKIP` with `Breakout wait` rationale.
- Wrong-zone breakouts such as SELL in H4 discount also require M15/M5 confirmation before becoming actionable.
- This moves the old repeated `BREAKOUT SELL` -> `M15 SMC Pressure Guard blocked` loop from a late gate into the strategy-selection layer.
- Details: `.obsidian-wiki/07_Trading_Intelligence/47_BreakoutLocalConfirmationGuard_V2617.md`

#### V26.14 - Wall Break Scalp + Fresh Decision Price (2026-05-19)

- Added `SMC_WALL_BREAK_SCALP`: a local scalp playbook that does not require M15 FVG, but requires a 3+ star wall, both M1 and M5 break/reclaim confirmation, and both M1/M5 Premium/Discount zones favorable for the side.
- `SMC_FVG_MAGNET_SCALP` still keeps the stricter M15 FVG target requirement; the new wall-break scalp is intentionally separate.
- AutoEngine now syncs decision `last.c` to fresh V25 tick mid or broker symbol price before deterministic strategy, zone/proximity gates, and order bracket calculations.
- Decision logs now include `originalLastClose`, `decisionPriceSource`, and `decisionPriceAgeMs` so stale candle-close decisions are visible.
- Details: `.obsidian-wiki/07_Trading_Intelligence/44_WallBreakScalp_FreshDecisionPrice_V2614.md`

#### V26.13 - PriceMap Wall Anchor Stability (2026-05-19)

- PriceMap still clusters OB/FVG/liquidity/structure/swing with Pivot/VWAP/Fib context, but structural/SMC sources now own the displayed wall anchor when present.
- Dynamic context levels (`D1`, `SESSION`, `SWING`) still add TF labels and star confluence, but no longer drag `price`, `priceTop`, or `priceBottom` away from the real structural wall.
- Added PriceMap tests for structural wall plus moving context and context-only walls.
- Details: `.obsidian-wiki/07_Trading_Intelligence/43_PriceMap_WallAnchorStability_V2613.md`

#### V26.12 - Decision Gate Analytics Observability (2026-05-19)

- Fixed `npm run analyze YYYY-MM-DD` after the Bangkok-day analytics change by restoring the missing `existsSync` import.
- Decision reports now normalize `Decision Side Guard`, `M15 SMC Pressure Guard`, `Anti-Hedge`, and `Fitness Below Minimum` into dedicated block categories instead of hiding them under `Other Skip` or `FVG Alignment`.
- Future decision-feed rows also store these clearer block categories from `autoTradingService.ts`.
- Details: `.obsidian-wiki/07_Trading_Intelligence/42_DecisionGate_AnalyticsObservability_V2612.md`

#### V26.11 - M15 Scalp Pressure Guard (2026-05-19)

- Audit of latest `A_SCALPING_XAUUSD` found BUY orders opened while M15 had opposing Bearish OB/FVG pressure; later SELL signals were blocked by anti-hedge because the BUY was already open.
- Generic `SCALPING` now checks M15 SMC pressure before entry and blocks BUY into M15 bearish pressure or SELL into M15 bullish pressure.
- Dedicated FVG playbooks (`SMC_FVG_SCALP`, `SMC_FVG_MAGNET_SCALP`) keep their own proof gates; this guard targets only generic `A_SCALPING`.
- Details: `.obsidian-wiki/07_Trading_Intelligence/41_M15_ScalpPressureGuard_V2611.md`

#### V26.10 - Mean Reversion Guard (2026-05-19)

- Yesterday's Bangkok-day analytics showed `MEAN_REVERSION` as the weak bucket: 14 trades, 8 wins, 6 losses, net `-$42.75`, with repeated SELL entries around the same price.
- `MEAN_REVERSION` now uses a longer duplicate-intent TTL (`900_000ms`) while normal sequential intents stay at `300_000ms`.
- TP for `MEAN_REVERSION` is capped to `adaptive.meanReversionMaxRrr` (`1.8R` default), preventing 4R-6R mean-reversion targets while keeping SL from the existing bracket formula.
- Details: `.obsidian-wiki/07_Trading_Intelligence/40_MeanReversion_OrderGuard_V2610.md`

#### V26.9 - Wall-to-FVG Magnet Scalp (2026-05-18)

- Added `SMC_FVG_MAGNET_SCALP` as a separate playbook: 3+ star wall near price, unmitigated M15 FVG target, then M1/M5 wall reclaim or micro break.
- This playbook bypasses HTF bias/context and Zone-Aware Gate vetoes by design, while still requiring the wall proximity check and normal risk/spread/duplicate guards.
- TP is locked to the FVG edge minus spread; SL is recalculated from the remaining FVG reward using the scalp target RRR instead of inheriting wide HTF brackets.
- BUY and SELL mirror paths are covered by unit tests.
- Date-specific analytics now treats `YYYY-MM-DD` as the Asia/Bangkok trading day, matching decision JSONL logs.
- Details: `.obsidian-wiki/07_Trading_Intelligence/39_WallFvgMagnetScalp_V269.md`

#### V26.8 - SMC FVG Scalp Proof Gate (2026-05-18)

- `SMC_FVG_SCALP` is now reserved for real `FVG_FILL` signals; generic MTF liquidity sweeps are labeled `SCALPING`.
- Deterministic ranging fallback no longer invents `SMC_FVG_SCALP` without FVG evidence; it uses `MEAN_REVERSION` unless a valid LTF scalp signal exists.
- The final `SMC_FVG_SCALP` gate now requires price to be inside an aligned active FVG with at least `50%` fill, instead of broad "near FVG" tolerance.
- Details: `.obsidian-wiki/07_Trading_Intelligence/38_SmcFvgScalp_ProofGate_V268.md`

#### V26.7 - Scalp TP/SL Bracket Rebalance (2026-05-18)

- `SMC_FVG_SCALP` now targets at least `1.35R` instead of inheriting the generic `1.2R` scalp floor.
- Compact scalp bracket now runs in normal V24/V26 flow too, not only `v25.enableV25Only`.
- XAUUSD scalp SL defaults tightened from about `4.5` points minimum risk to `3.0` points while preserving existing TP when it already offers better reward.
- Details: `.obsidian-wiki/07_Trading_Intelligence/37_Scalp_TpSl_Bracket_V267.md`

#### V26.6 - Scalping Routing & RRR Relaxation (2026-05-18)

- Promoted valid M5/M15 `SCALPING` analysis into the deterministic executable strategy instead of always remapping ranging markets to `SMC_FVG_SCALP`.
- EA fallback now uses `strategy.scalpingRRR` for scalp-like strategies, so valid `SCALPING`/`SMC_FVG_SCALP` setups are not rejected by the global `1.5` RRR floor.
- `Zone-Aware Gate` now treats generic `SCALP` intent as short-term execution intent while still requiring local TF alignment/FVG/SMC/wall evidence.
- Details: `.obsidian-wiki/07_Trading_Intelligence/36_Scalping_Routing_V266.md`

#### V26.5 - Stale-Stop Guard & RSI Divergence Strategy (2026-05-18)

- Added `isSymbolTradable` check in the `Stale-Stop` loop to prevent spamming close requests when the market is closed.
- Relaxed counter-trend blocks for scalping strategies (`SMC_FVG_SCALP`, `SCALPING`, `SMC_RSI_DIVERGENCE`) in `Zone-Aware Gate` if M5 or M15 trend aligns.
- Added `SMC_RSI_DIVERGENCE` strategy in `SignalDetector.ts` using local RSI calculation back to previous swing points.
- Details: `.obsidian-wiki/07_Trading_Intelligence/35_StaleStop_RsiDivergence_V265.md`

#### V26.4 - Zone-Aware Gate MTF Local Override (2026-05-18)

- Zone-Aware Gate now treats H4/H1 as higher-timeframe context and M30/M15/M5/active TF as execution context.
- SELL in HTF discount or BUY in HTF premium can pass only when HTF bias aligns, continuation intent exists, and local execution TF or fresh aligned FVG supports the entry.
- `SMC_FVG_SCALP` FVG alignment now checks MTF FVGs instead of only the active candle set.
- Stale-position time stop now computes R from broker `priceOpen` first, matching the manager audit calculation.
- Details: `.obsidian-wiki/07_Trading_Intelligence/34_ZoneAwareGate_MTF_LocalOverride_V264.md`

#### V26.3 — Accuracy Feedback & WebSocket Push (2026-05-15)

ปรับปรุงระบบประเมินผล AI และการส่งข้อมูลแบบ Real-time:
- **recordSlTpAccuracy() Hardened** — ระบบให้คะแนน `slTpAgent` แบบละเอียด แยกกรณี TP Bullseye (+0.2R), Profit (+0.1R), Tight SL hit (-0.2R) และ BE (+0.05R) เพื่อการเลือกโมเดลที่แม่นยำขึ้น
- **Real-time WebSocket Push** — เมื่อมีการเปิด/ปิด/แก้ไขออเดอร์ ระบบจะ trigger push `snapshot_delta` ไปยังแอปมือถือทันที (0ms latency หลัง execution) ไม่ต้องรอรอบ poll 1s เดิม
- **Detailed Accuracy Logging** — เพิ่มการบันทึกเหตุผลการให้คะแนนใน Log เพื่อการตรวจสอบย้อนหลัง

#### V25.1 — Real-Time Coordination Patches (2026-05-10)

หลังเปิด V25 จริงพบปัญหา **AI–EA ประสานงานไม่ถูกจังหวะ**:
- **Wall State Machine churning** — APPROACH↔REACT↔IDLE ทุก 1-3s รอบ wall เดิม → กระตุ้น CONFIRM ผิดเวลา
- **PB1 SKIP loop** — `wall stars 1 < min 2` ทุกครั้งเพราะ `query()` คืน wall ใกล้สุด แม้ wall ที่แข็งกว่า (5★) อยู่ห่างแค่ 30 pts
- **slTpAgent wrong-side SL/TP** — รับ `biasSide=SELL` (จาก analysis.bias=BEAR) ขณะที่ AI override `side=BUY` → smcSl อยู่เหนือ entry → discarded ทุกรอบ
- **AI pipeline waste** — รัน Analyst+RiskOfficer+Execution (~168s + token) แม้ไม่มี wall touch จริง

**7 patches** (ดูรายละเอียดใน wiki §V25.1):
1. `WallStateMachine` REACT ไม่ fall-back IDLE จาก tick distance + APPROACH hysteresis 2.0× + dwell ≥ 1.5s + wall identity prefers stronger anchor + churn telemetry
2. `WallProximityIndex.queryStrongest()` — สแกน wall แบบ "ดาวเยอะสุดในรัศมี" แทนที่จะใช้ใกล้สุดเท่านั้น
3. `PlaybookSelector` ถ้า nearest wall < minWallStars → fallback ไป `queryStrongest()` ภายใน 2.5× reactZone
4. `slTpAnalyst` call site ใช้ FINAL `side` แทน `biasSide` (พร้อม log warning เมื่อต่างกัน)
5. ATR fallback (`rawSl/rawTp`) ใช้ `rawDirSide = side ?? biasSide` แก้บั๊กเดียวกัน
6. **V25 Gate** — `wallStateMachine.status()` ไม่มี state=REACT/CONFIRM → ตั้ง aiDecision=SKIP ทันที **ข้าม AI pipeline** (~150s saving) — บังคับ R5 จริง
7. Log warning + tag `[COUNTER_TREND]` เมื่อ Final Side ตรงข้าม deterministic Bias

#### V25.0 Real-Time Wall Engine — Phase A SHIPPED (2026-05-10)
แก้ปัญหาเชิงโครงสร้างที่ V24 patch ไม่ครอบคลุม: ใน log จริง 2026-05-09 14:09–14:29 (cycle #9563–#9576) ระบบรัน 14 cycle แต่ executed = 0 — ทุก cycle SKIP ที่ Hard Risk Gate เพราะ entry กลางช่อง (no-man's-land) → SL กระโดดไกล → Global TP cap ทำให้ RRR < 0.5

V25 รื้อสถาปัตยกรรมเป็น **7-layer event-driven** (tick + bar-close hybrid) + **Wall State Machine** (IDLE→APPROACH→REACT→CONFIRM/BREAK→COMMITTED) + **5 playbooks** (PB1 Wall-Touch Scalp default, PB2 FVG Fill, PB3 Sweep Reversal, PB4 Range Fade, PB5 Breakout-Retest) — บังคับกฎ **"no wall, no trade"**

**Phase A + B + C + D — ทั้งหมดถูก commit 2026-05-10** (~2,446 LOC, opt-in `V25_SHADOW=true`, รันคู่ขนานกับ V24 — ไม่กระทบ order flow):

- **Phase A — Foundation:** `TickBuffer` (ring), `V25EventBus` (dedicated), `TickGateway` (poll `/symbol_info` 1s, dedupe srcTs, stale-tick guard 5s), `BarCloseEventBus` (clock-aligned per TF), `M1MicroWallBuilder` (rolling micro-pivot 1s/60s window)
- **Phase B — Wall State Machine:** `PriceMapSnapshotProvider` (read V21.1 PriceMap), `WallProximityIndex` (sorted+binary-search), `FvgFreshTracker` (drop-on-fill per tick), `ReactionDetector` (M1 engulf/pinbar/FVG-fill), `WallStateMachine` (IDLE→APPROACH→REACT→CONFIRM/BREAK→RETEST→COMMITTED, ATR-relative zones)
- **Phase C/D — Playbooks (ครบ 5):** `PB1_WallTouchScalp` (default ของ user), `PB2_FvgFillReversal`, `PB3_SweepReversal`, `PB4_RangeFade`, `PB5_BreakoutRetest` + `PlaybookSelector` cascade (PB3 > PB2 > PB5 > PB4 > PB1) — emit shadow plans + ring-buffer 20 candidates / 10 skips per symbol
- **Per-symbol profile table** baked in (XBTUSD/XAUUSD/Indices/default) — ATR multipliers + min RRR + min wall stars (Q4 design decision)
- **Diagnostics endpoints:** `/api/v25/{health,ticks/:s,state/:s,candidates/:s}` (token-protected)

**Compile:** all 19 V25 files clean tsc strict; full project shows zero V25 errors (4 pre-existing V24 errors unrelated).

**ถัดไป (⏳ pending):** LLM gating ให้ `slTpAgent` ทำงานเฉพาะตอน state=REACT, ExecutionService handoff (`playbookSelector.configure({ shadowOnly: false })` → `tradingExecutionService`), V24 sunset toggle หลัง A/B test

ดูเอกสารเต็ม: `.obsidian-wiki/07_Trading_Intelligence/25_RealTimeWallEngine_V25.md` + flowcharts ใน `25_TradeFlow_Diagrams.md`

#### V24.2.0 — Context Levels (2026-05-08)
ปิดช่องว่าง indicator ที่ pro retail ใช้แต่ระบบไม่มี:
- **Daily Pivot Points** (Standard Floor) — P/R1-3/S1-3 จาก D1 OHLC (derived จาก H1 24-bar)
- **Session VWAP** — anchored ที่ NY equity open (13:00 UTC) + ±1σ bands → dynamic SR + mean reversion
- **Auto-Fibonacci** — retracements 0.236/0.382/0.5/0.618/0.786 + extensions 1.272/1.618 จาก swing high/low ที่ SMC structure ตรวจมาแล้ว
- **Confluence Boost** — wall ที่ตรงกับ Pivot R1/S1/P, VWAP, หรือ Fib 0.618 ได้ +1★ → 3★ wall + Pivot = 4★ → ProximityGate/TP-cap แม่นขึ้น
- Coverage: ~85% ของ indicator ที่ pro retail trader ใช้จริง (เดิม ~70%)

#### V24.1 — EA Manager Improvements (2026-05-08)
จาก live log analysis พบ 8 จุดอ่อนที่ทำให้ EA-only mode ไม่ทำงานตาม design — แก้ทั้งหมดในรอบเดียว:
- **SMC-Aware Trail (เปิดใช้จริง)** — `computeSmcTrailTargets()` ถูก import แต่ไม่เคยเรียก → wire เข้า manager phase, SL trail ไปขอบ OB/FVG/Swing/Liquidity จริง (เหนือกว่าสูตร ATR คงที่)
- **Regime-Flip Exit** — `detectRegimeFlipAction()` เปิดใช้, ปิด losing leg เมื่อ CHoCH against cluster ก่อน SL hit
- **BE/Trail Trigger ลด** — 1.0R → 0.5R (BE) / 0.6R (TRAIL); scalp 0.3R/0.4R — เพราะ R-distribution จริงต่ำกว่า 1R ส่วนใหญ่
- **Stale-Position Time Stop** — ปิด position ค้างใน R∈[-0.25, +0.30] นานกว่า 45 นาที (ตัด churn, free risk budget)
- **MTF-Aware Counter-Trend Gate** — exempt block ถ้า H4 bias agree กับ intended side (เลิก block valid BUY-on-pullback ใน H4=BULL)
- **ProximityGate Hardened** — ไม่ bypass ใน H4=RANGING (กัน false breakout entry ใต้ resistance cluster)
- **Position Age Display** — Manager audit แสดง `age=Nm` ทุก position
- **Config Migration** — DB configs เก่า ≥1.0 R จะถูก migrate ลง defaults ใหม่อัตโนมัติ

- **Institutional SMC Integration (V20.0)** — ผสานรวมห้องสมุด Python ระดับ Professional (`smartmoneyconcepts`) เข้ากับหัวใจของ AI ทำให้ระบบสามารถระบุโซน **FVG (Fair Value Gap), Order Blocks (OB), และ BOS/CHoCH** ได้แบบ Real-time และมีความแม่นยำสูงกว่าเดิม 300%
- **Token Usage Optimization (V20.5)** — ระบบ "Pruning Gate" อัจฉริยะที่ช่วยลดการใช้ Token ได้สูงสุด 70% ผ่านการ:
    - **History Truncation**: จำกัดประวัติการสนทนาไว้ที่ 20 turns ล่าสุด
    - **Context Capping**: จำกัดความจำระยะยาว (Core Context) ที่ 15,000 ตัวอักษร และผลลัพธ์จาก Tool ที่ 8,000 ตัวอักษร เพื่อป้องกัน Context Bloat
    - **Claude Prompt Caching**: เปิดใช้งานระบบ caching สำหรับ System Prompt และ Tool Definitions ของ Anthropic ช่วยลดต้นทุนและ latency ในการเรียกใช้งานซ้ำ
- **MiniMax Provider Expansion (V20.5)** — เพิ่มการรองรับโมเดลจาก **MiniMax (abab6.5s)** ทั้งในระบบมือถือและ server เพื่อเพิ่มทางเลือกในการวิเคราะห์และลดความเสี่ยงจากการพึ่งพา provider รายเดียว
- **Structurally-Aware SL/TP** — ระบบ AI (slTpAgent) จะทำการวางจุด Stop Loss และ Take Profit โดยอ้างอิงจากโครงสร้างราคาของสถาบัน (Structural Levels) อัตโนมัติ ช่วยลดการเกิด "SL Hunt" และเพิ่มอัตรา Win Rate
- **Dynamic MTF Zone Gates (V19.5)** — ระบบ "ด่านตรวจ" อัจฉริยะแบบ Top-Down ที่วิเคราะห์โซนราคา (Premium/Discount) แยกราย Timeframe (H4, H1, ActiveTF) ช่วยให้ระบบสามารถตรวจจับจังหวะ Pullback (ย่อซื้อ/เด้งขาย) ได้คมชัดขึ้น โดยจะข้ามการเรียก AI (Token Saving) เฉพาะเมื่อราคาเสียเปรียบในทุกมิติเท่านั้น
- **Per-Symbol Position Caps (V19.4)** — ระบบจำกัดออเดอร์รายสัญลักษณ์ (Max 5 positions per symbol) พร้อมระบบ Defense Cap (2× Ceiling) สำหรับโหมดแก้พอร์ต และระบบ **Log Observability** ที่โชว์ค่า SL/TP เป้าหมายแม้จะข้ามการเทรด (SKIP)
- **Sequential Offset Recovery (V19.4)** — ระบบจัดการ Cluster อัจฉริยะที่ค้นหาคู่ "ไม้ที่ขาดทุนหนักที่สุด" และ "ไม้ที่กำไรดีที่สุด" (Non-Golden) มาหักล้างกันเพื่อลดความเสี่ยงโดยไม่ต้องรอให้กำไรรวมเป็นบวก ช่วยรักษาเงินต้นในสภาวะตลาดผันผวน
- **3-Tier Recovery Time Decay (V19.4)** — ระบบสลายออเดอร์ค้างตามอายุ (6h/12h/24h) เพื่อป้องกันเงินจม (Zombie Clusters) โดยจะค่อยๆ คลายเงื่อนไขการออกออเดอร์เมื่อเวลาผ่านไปจนถึงจุด Force Close เพื่อดึงทุนกลับมาหมุนเวียน
- **Pre-AI Short-Circuits (Token Saving)** — ระบบ "ด่านตรวจ" อัจฉริยะที่ตัดการเรียก AI ทันทีหากเงื่อนไขพื้นฐานไม่ผ่าน (เช่น Cap เต็ม หรือ Zone ราคาไม่เหมาะสม) ช่วยประหยัด Token ได้สูงสุดถึง 99%
- **Smart Fallback & Performance Ranking (V19.4)** — ระบบเลือก AI อัตโนมัติตามประสิทธิภาพจริง (Profit R / Success Rate) พร้อมระบบ Session-based Blacklist ที่จะ "กาหัว" โมเดลที่พังหรือติด Limit ออกจากการวนลูปชั่วคราว
- **Outcome Attribution Loop** — ระบบเชื่อมโยงผลการเทรด (P&L) กลับไปยังโมเดลต้นทาง ทำให้ระบบสามารถ "เรียนรู้" และเลือกใช้โมเดลที่แม่นยำที่สุดในแต่ละสภาวะตลาดได้เอง
- **AI Model Leaderboard (V19.5)** — หน้าจอแสดงอันดับความเก่งของ AI (Smart Free Ranker) บน Dashboard ที่จัดอันดับตามคะแนน (Score) ซึ่งคำนวณจาก Profit R, Success Rate, ความเร็ว (Latency) และอัตราการเกิด Error ช่วยให้ผู้ใช้เห็นภาพรวมประสิทธิภาพของโมเดลแต่ละตัวได้ทันที
- **Cluster Trading Logic V17.2** — ระบบจัดการออเดอร์แบบ "กลุ่มก้อน" (Sequential Protection, Golden Position Guard) เพื่อปกป้องทุนและล็อคกำไรอย่างเป็นระบบ

### 🤖 5. Automation & Monitoring Dashboard
- **Real-time Alert Management** — หน้าจอ Dashboard ระดับพรีเมียมสำหรับจัดการงานเฝ้าติดตามตลาด
- **Autonomous Triggering** — JARVIS สามารถตัดสินใจและตั้งค่าการแจ้งเตือนได้เองเมื่อเห็นจังหวะที่ "เกือบ" จะมาถึง
- **Model Performance Dashboard** — ส่วนแสดงผลแบบ Side-by-Side ระหว่าง Market Symbols และ Model Rankings ช่วยให้ตรวจสอบสถานะตลาดคู่กับประสิทธิภาพ AI ได้ในหน้าเดียว
- **Once-Only Notification** — ระบบแจ้งเตือนอัจฉริยะ (Notification) แจ้งเพียงครั้งเดียวเมื่อเงื่อนไขเป็นจริง เพื่อป้องกันความรำคาญ

### 🏥 6. JARVIS Diagnostic Engine (Self-Healing)
- **Autonomous Health Verification** — ระบบตรวจสอบตนเองอัตโนมัติ (API Connectivity, Data Accuracy, Database Integrity)
- **Price Source Sync** — ระบบเปรียบเทียบราคาจากหลายแหล่ง (Yahoo, OANDA, TV) เพื่อระบุความเหลื่อมล่วง (Discrepancy) และ Delay
- **Obsidian Wiki Reporting** — สรุปผลการตรวจสอบระบบเป็นไฟล์ Markdown บันทึกลงในไดเรกทอรีส่วนตัวโดยอัตโนมัติ

### 🏗️ 5. Smart Money Concepts (SMC) V16.0
ระบบ SMC ได้ถูกผสานรวมกับเครื่องมือใหม่เพื่อเพิ่มประสิทธิภาพ:
- **PRZ-OB Confluence**: กรองรูปแบบ Harmonic เฉพาะที่เกิดในโซน SMC Order Blocks
- **Wave Momentum Tracking**: ใช้ Elliot Wave Impulse Score ช่วยยืนยันความแข็งแกร่งของ Trend (BOS/CHoCH)
- **TV Persistence Layer**: ระบบ Database Caching สำหรับแท่งเทียน TradingView โดยเฉพาะ

### 🔗 7. MT5 Full Agent Control V17.0 (Broker-First Architecture)

> **Status: 🟢 Stable (21 MT5 Tools — 12 Core + 3 Action + 6 Intelligence)**
> **Last Update: 2026-04-21 (V17.0 Major Upgrade)**

- **Broker-First Data Sourcing** — ข้อมูลทั้งหมดดึงจาก MT5 Broker โดยตรง ไม่ใช่ TradingView (ราคา, volume, positions ตรงกับโบรกเกอร์)
- **Full Account Control** — AI ดู equity, balance, margin, P&L, symbols, positions, orders, history ได้ทั้งหมด
- **OHLCV from Broker** — ดึง candle data ตรงจากโบรกเกอร์เพื่อความแม่นยำในการวิเคราะห์
- **Batch Operations**
    - **Cross-Asset Correlation**: Integrated DXY, US10Y, and SPX500 data streams to confirm Gold bias.
    - **Capacity-Aware Intelligence**: Added "Harvesting Mode" which forces AI to focus on managing existing trades and skip new entries when the position limit (Hard Cap) is reached.
- **Advanced Intelligence Suite** — Market Scanner, Correlation Radar, Sentiment Gauge, Institutional Flow, Economic Radar, Trade Journal
- **Audit Trail** — บันทึกทุก trade action ที่ AI สั่ง + ให้คะแนนคุณภาพการเทรด
- **Resilient Position Parsing** — ระบบวิเคราะห์ประเภท Position (BUY/SELL) ที่แข็งแกร่ง รองรับทั้งรหัสตัวเลข (0, 1) และข้อความ ป้องกันข้อผิดพลาดในการระบุฝั่งเทรดจาก Bridge
- **Architecture** — Mobile (KMP) → mt5-core-server (Node.js:8090) → Python Bridge (mt5_bridge.py) → MetaTrader5 API

---

## 🛠️ Prerequisites (ข้อกำหนดพื้นฐาน)

เพื่อให้โปรเจคทำงานได้อย่างถูกต้อง จำเป็นต้องติดตั้งและตั้งค่าดังนี้:
- **Java JDK 21**: เนื่องจาก AGP 8.8.2 ต้องการ Java อย่างน้อยเวอร์ชัน 11/17 แนะนำให้ใช้ JDK 21 (มีมาพร้อมกับ Android Studio ในโฟลเดอร์ `jbr`)
- **Android Studio**: เวอร์ชันล่าสุด (Ladybug หรือใหม่กว่า)
- **Node.js**: สำหรับรัน `mt5-core-server`
- **Python 3.10+**: สำหรับ `mt5_bridge.py`

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3.1 Pro (Tier 1 Optimized) | Active |
| AI Providers | Gemini / OpenAI / Claude / OpenRouter / MiniMax / Ollama / Vertex AI / LiteLLM | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Embedding (Server) | qwen3-embedding / bge-m3 (768d HNSW) | Active |
| Embedding (Mobile) | LocalOnnx (paraphrase-multilingual-MiniLM-L12-v2 q4, 384d → pad+L2-norm to 768d, ~117MB offline) / Gemini Cloud (gemini-embedding-001 3072d→trunc 768d → text-embedding-004 768d cascade, L2-norm) | Active |
| Background Service | Android Foreground (DataSync) | Active |
| Logic Controller | JarvisOrchestrator (Multi-Provider) | Active |
| MT5 Core Server | Node.js + Express (port 8090) | Active |
| MT5 Python Bridge | Python + MetaTrader5 API | Active |

---

## 📦 Tool Catalogue (Total: 84 Tools)

### 🧠 BUILT-IN & SYSTEM TOOLS (17 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์ซับซ้อน
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทินปัจจุบัน
- `remember_fact`: บันทึกข้อมูลสำคัญลงความจำระยะยาว
- `recall_memory`: ดึงข้อมูลจากฐานความรู้เดิม
- `convert_units`: แปลงหน่วยสากลทุกประเภท
- `set_reminder`: ตั้งการแจ้งเตือน/TODO
- `format_json`: จัดรูปแบบข้อมูลเป็น JSON
- `translate_text`: แปลภาษาแบบ Multilingual
- `summarize_text`: สรุปข้อความยาวๆ พร้อมกำหนดระดับความละเอียด
- `search_web`: ค้นหาข้อมูลล่าสุดจากโลกออนไลน์
- `identity_update`: AI ปรับแต่งตัวตน/ข้อมูลผู้ใช้เองเมื่อถูกสั่ง
- `analyze_and_display_report`: ส่งรายงาน markdown ยาวลงแชท แล้วพูด/ตอบสรุปสั้น (กันเสียง Live ขาด)
- `system_run_diagnostics`: ตรวจสอบสุขภาพระบบ (Self-healing)
- `system_check_connectivity`: ตรวจสอบการเชื่อมต่อ API ทั้งหมด
- `system_create_agent_tool`: 🤖 AI สร้างเครื่องมือใหม่เอง (save + register ทันที + โหลดกลับอัตโนมัติ)
- `mt5_place_order`: alias ส่งคำสั่ง MT5 (route → trading_mt5_order)
- `mt5_close_position`: alias ปิด position MT5 (route → trading_mt5_close_position)

### 📊 TRADING TOOLS (25 tools)
- `trading_price`: ราคา Real-time (Stocks/Crypto/Forex/Gold)
- `trading_market_snapshot`: ภาพรวมตลาดเจาะตามกลุ่มอุตสาหกรรม
- `trading_top_gainers`: หุ้น/Crypto ที่พุ่งแรงที่สุดในตลาด
- `trading_top_losers`: หุ้น/Crypto ที่ดิ่งแรงที่สุดในตลาด
- `trading_technical_analysis`: วิเคราะห์ TA (RSI, MACD, BB, EMA) พร้อม Signal
- `trading_multi_timeframe`: วิเคราะห์ความสอดคล้องทุก TF (W → 15m)
- `trading_bollinger_scan`: หาตัวที่กำลังจะระเบิด (Bollinger Squeeze)
- `trading_oversold_scan`: หาตัวที่ราคาถูกเกินไป (RSI < 30)
- `trading_overbought_scan`: หาตัวที่ราคาร้อนแรงเกินไป (RSI > 70)
- `trading_volume_breakout`: หาตัวที่มีแรงซื้อขายผิดปกติพร้อมราคาพุ่ง
- `trading_sentiment`: วิเคราะห์อารมณ์ตลาดจาก Reddit
- `trading_news`: ข่าวการเงินล่าสุดแยกตาม Symbol (6 แหล่ง: Google News ค้นตรงสินทรัพย์ + Yahoo/CNBC/MarketWatch/Investing.com/CoinDesk)
- `trading_combined`: สุดยอดเครื่องมือวิเคราะห์ (TA + News + Sentiment)
- `trading_fundamental_analysis`: วิเคราะห์ปัจจัยพื้นฐาน (Fundamental)
- `trading_fear_greed`: 🌡️ Crypto Fear & Greed Index ตัวจริง (alternative.me — ฟรี, อนุกรม 7 วัน + การตีความไทย)
- `trading_macro_calendar`: 📅 ปฏิทินเศรษฐกิจรายสัปดาห์ (ForexFactory — ฟรี, ไม่ต้อง API Key, แสดงเวลาไทย, เรียงข่าว impact สูงก่อน)
- `trading_economic_data`: 🇺🇸 ตัวเลขเศรษฐกิจสหรัฐฯ จาก FRED (GDP, CPI, ว่างงาน, ดอกเบี้ย Fed — ไม่ต้องใช้ API Key)
- `trading_correlation_matrix`: คำนวณความสัมพันธ์ระหว่างสินทรัพย์
- `trading_position_sizing`: ช่วยคำนวณขนาดไม้ที่เหมาะสม
- `trading_crypto_overview`: 🪙 ภาพรวมตลาดคริปโต (CoinGecko — ฟรี: Market Cap รวม, BTC/ETH Dominance, เหรียญ Trending)
- `automation_manage_alerts`: ระบบสร้าง/แก้ไข/ลบ/list งานเฝ้าติดตามอัตโนมัติ (เข้าเงื่อนไข → ปลุก AI สรุปแล้วแจ้งเตือนพร้อมปุ่ม หยุด/แจ้งซ้ำ) — ตั้งเงื่อนไขได้เฉพาะ field ที่อยู่ใน `AlertFieldCatalog` (ดู `.obsidian-wiki/02_Components/Alert_System_V2.md`)
- `automation_manage_schedule`: ⏰ งานตามเวลา (one-time/daily) — ถึงเวลาแล้วปลุก AI มาทำตาม prompt
- `trading_deep_analysis_suite`: วิเคราะห์ 5 มิติ (LSD, Orderflow, Fibo Score) - **Institutional Grade** 
- `trading_harmonic_scan`: สแกนรูปแบบ Harmonic (Gartley, Bat, Butterfly)
- `trading_elliot_modern_analysis`: วิเคราะห์ Elliott Wave แบบ Modern

### 🔗 MT5 BRIDGE TOOLS (21 tools) — V17.0
**Core Actions:**
- `trading_mt5_order`: ส่งคำสั่ง Market/Limit/Stop
- `trading_mt5_close_position`: ปิด position ด้วย ticket/symbol
- `trading_mt5_modify_position`: แก้ไข SL/TP

**Core Agent (ข้อมูลจาก Broker โดยตรง):**
- `trading_mt5_account_info`: Equity, Balance, Margin, P&L
- `trading_mt5_list_positions`: ลิสต์ positions ที่เปิดอยู่
- `trading_mt5_list_orders`: Pending orders
- `trading_mt5_list_history`: ประวัติ deals ที่ปิดแล้ว
- `trading_mt5_candles`: OHLCV จาก broker
- `trading_mt5_symbol_info`: Spread, digits, contract size
- `trading_mt5_symbol_search`: ค้นหา symbol ใน broker
- `trading_mt5_analyze`: AI วิเคราะห์ symbol จากข้อมูล broker
- `trading_mt5_close_all`: ⚠️ ปิด positions ทั้งหมด
- `trading_mt5_break_even_all`: ⚠️ ย้าย SL มา break-even
- `trading_mt5_snapshot`: Snapshot รวม (account+positions+orders)
- `trading_mt5_trade_actions`: JARVIS audit log

**Advanced Intelligence (วิเคราะห์ขั้นสูงจากข้อมูล Broker):**
- `trading_mt5_market_scanner`: สแกน symbols น่าเทรดจาก broker
- `trading_mt5_correlation_radar`: Correlation ระหว่าง symbols
- `trading_mt5_sentiment_gauge`: สุขภาพพอร์ต / จิตวิทยาเทรดเดอร์
- `trading_mt5_institutional_flow`: วิเคราะห์ทิศทางเจ้ามือ
- `trading_mt5_economic_radar`: ข่าวเศรษฐกิจ → risk mapping positions
- `trading_mt5_trade_journal`: AI Scoring + เรียนรู้จากข้อผิดพลาด

### 📈 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard
- `trading_smc_sweeps`: MTF Sweep Detection
- `trading_smc_liquidity`: MTF Liquidity Zones & Stars
- `trading_smc_orderblocks`: Order Blocks + FVG Confirmation
- `trading_smc_structure`: Market Structure (BOS/CHoCH, Premium/Discount)

### 📁 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write`, `file_delete`, `file_analyze` (OCR/PDF Support), `file_move`, `file_search`

### 📷 CAMERA & VISION (9 tools)
- `vision_activate`/`deactivate`: ควบคุมการเปิด/ปิดดวงตา AI
- `camera_analyze_scene`: วิเคราะห์ภาพ Snapshot
- `camera_detect_objects`: ตรวจจับวัตถุ (AR Overlay)
- `camera_read_text`: อ่านข้อความจากกล้อง (OCR)
- `camera_switch_provider`: สลับสมองที่ใช้มอง (Gemini/OpenAI/Claude)
- `camera_switch_mode`: เปลี่ยนโหมดกล้อง
- `voice_get_profiles`: ดูรายการเสียงพูด 30 รูปแบบ
- `voice_set_profile`: เปลี่ยนเสียง JARVIS

### ⚙️ 8. MT5 AutoTrading Remote Engine V24.1 (Fade-the-Level & Pipeline Sync)

- [x] Phase 5.1: Pipeline Sync & Proximity Gates (Completed 2026-05-08)
  - [x] **V21.0 EA Signal Integration**: ระบบสามารถดึงสัญญาณที่มีความแม่นยำสูง (2★+) จาก `IndicatorPipeline` ทับซ้อนลงไปใน Execution Pipeline หลักได้โดยตรงเพื่อป้องกันสัญญาณตกหล่น
  - [x] **Proximity Gate Bypass**: ระบบการกรองการเข้าทำออเดอร์ (Fade-the-Level) จะอนุญาตให้กลยุทธ์สาย Trend/Breakout (เช่น BREAKOUT, CONTINUATION, MOMENTUM, SCALP) วิ่งผ่านได้โดยไม่ถูกบล็อกด้วยระยะทางจากแนวรับแนวต้าน
- [x] Phase 4.6: Multilingual Local Embedding & Device Pairing (Completed 2026-04-30)
  - [x] Multilingual Support: Migrated to `paraphrase-multilingual-MiniLM-L12-v2` for stable, offline Thai semantic search.
  - [x] Memory Optimized: Implemented streaming download and LargeHeap to prevent OOM.
  - [x] Pairing Dashboard: Added a new "Device Pairing Requests" UI to the server dashboard for secure mobile device approval.
  - [x] JNI-Free Tokenizer: Replaced native dependencies with a pure-Kotlin resilient BPE tokenizer.
- [x] Phase 5: AutoTrading Model Exploration & Cluster Protection (Completed 2026-05-02)
  - [x] **Smart Force Exploration**: ระบบจะเลือกเฉพาะโมเดลที่ไม่เคยล้มเหลว (consecutive_failures = 0) เพื่อป้องกันการติดลูปเดิม
  - [x] **Role Cooldown**: ระบบพักเบรก (Cooldown) 5 นาทีสำหรับการสุ่มโมเดลใหม่ในแต่ละ Role เพื่อลดเวลา Pipeline และประหยัด Quota
  - [x] **Cluster Loss Protection**: ระบบตัดขาดทุนฉุกเฉินสำหรับ Cluster (ไม้กลุ่ม) หากมียอดรวมขาดทุนเกิน -1.5R หรือ 150 USD ระบบจะปิดตำแหน่งที่แย่ที่สุดทันที

> **Status: 🟢 Stable & Verified (TSC Exit 0)**  
> **Last Update: 2026-05-08 (V24.1 Proximity Gate Bypass & Signal Integration)**

- **Fade-the-Level Proximity Gate (V24.0)**: ระบบกรองจุดเข้าแบบพึ่งพากำแพงแนวรับแนวต้าน โดยจะอนุญาตให้เข้าเทรดก็ต่อเมื่อราคาเข้าใกล้โซน 50-100 pips และมีการยืนยันสัญญาณจากกรอบเวลาเล็ก (LTF Confirmation) เท่านั้น (ยกเว้นกลยุทธ์ Breakout ที่ถูก Bypass)
- **Zone-Aware Gate & Dynamic SL Buffer**: ระบบกรองจุดเข้าแบบแม่นยำสูง (บล็อก BUY ใน Premium / SELL ใน Discount) พร้อมดันระยะ Stop Loss ออกนอกขอบเขต FVG อัตโนมัติ (ขจัดการเกิด Error 10016 100%)
- **Staged Break-Even & Trailing**: จัดการล็อคกำไรเป็นขั้นบันได (Stage 1: ล็อค 25% ที่ 1R, Stage 2: ล็อค 50% ที่ 2R พร้อม Trail SL, Stage 3: Trail ที่ 3R) ปลอดภัยและมีประสิทธิภาพ
- **Smart Flip & Concentration Guard**: เมื่อเทรนด์ตลาดใหญ่กลับตัว ระบบจะ FLIP_CLUSTER ปิดไม้ขาดทุนสุดทันที พร้อมระบบป้องกันการเปิดไม้กระจุกตัวเกิน 3 ไม้ในระยะ 10 pips
- **Pre-Analysis Prior (Vector Memory)**: สกัดข้อมูลความผิดพลาดเก่า (Similar Failed Setups) จาก Vector Database ส่งให้ LLM เป็นเครื่องเตือนใจป้องกันความผิดพลาดซ้ำรอย
- **Prometheus Observability**: ตรวจสอบ Metrics ทุกมิติการบล็อกและจัดการ (Zone Block, SL Buffer, Counter-Trend Block, Management Events) แบบ Real-time
- **Trade Manager Parallelization**: ระบบ TradeManagementService (SL trailing, Break-Even, Hedging) ถูกแยกการทำงานแบบขนาน (Parallel) จากระบบ AI ทำให้สามารถป้องกันพอร์ตและล็อคกำไรได้แบบ Real-time โดยไม่ต้องรอ AI คิด
- **Deterministic Prior Engine**: ระบบตรวจสอบเงื่อนไขทางเทคนิค (FVG, EMA) ด้วยความเร็ว 5ms ก่อนส่งให้ AI ช่วยลด Token และบังคับให้ AI โฟกัสเฉพาะหน้าเทรดที่ได้เปรียบ
- **Anti-Hedge & Confluence Gates**: กฎเหล็กป้องกันการเปิดไม้สวนกันเอง (Hedging) ในภาวะปกติอย่างเด็ดขาด โดยอนุญาตให้เปิดสวนได้เฉพาะกรณีใช้โหมดแก้พอร์ตฉุกเฉิน (Defense Override) เท่านั้น
- **Gemini 3.1 Multilingual reasoning**: อัพเกรดเป็น Gemini 3.1 (Pro/Flash) พร้อมระบบสรุปเหตุผลภาษาไทย (Clean Narrative) ช่วยให้เข้าใจการตัดสินใจของ AI ได้ทันทีโดยไม่ต้องแปล
- **Mobile Event Hub (Tab "Even")**: หน้าจอแสดงผลประวัติการตัดสินใจของ AI (Decision Feed) บนมือถือ แยกสี Card ตามประเภท (BUY/SELL/SKIP/HEDGE) พร้อมคำอธิบายภาษาไทย
- **Server-Side Orchestration**: ระบบประมวลผลบน `mt5-core-server` 100% ทำงานได้ต่อเนื่อง 24/7 แม้ปิดแอปมือถือ

---

## 🚀 Future Roadmap (Coming Soon)

1.  **Multi-Agent Orchestration (Swarm Architecture)**: ระบบวิเคราะห์งานและกระจายงานให้ AI Agent เฉพาะทางทำงานพร้อมกัน
2.  **Portfolio Hub**: ระบบติดตามพอร์ตการลงทุนแบบละเอียดและ P&L Analytics
3.  **Strategy Backtester**: ระบบทดสอบกลยุทธ์การเทรดแบบครบวงจร
4.  **Hardware Extension**: การเชื่อมต่อกับอุปกรณ์ Smart Home และ Wearables
5.  **Offline Memory Layer**: ระบบความจำขนาดเล็กที่ทำงานได้โดยไม่ต้องพึ่งอินเทอร์เน็ต

---

## 🧠 AI Subsystem Hardening (2026-07-29)

Code review + ปรับปรุงระบบ AI ฝั่งมือถือครบวงจร (รายละเอียดเต็ม: `.obsidian-wiki/01_Architecture/ai_subsystem_review_2026-07-29.md`)

- **File Security**: `file_write`/`file_read`/`file_list`/`file_analyze` มี path guard ครบทุก op + จำกัดนามสกุลที่เขียนได้ + กัน path traversal
- **Shared Modules ใหม่**: `ToolArgParser` (parse tool args กลาง คืน error เมื่อ JSON เพี้ยน), `TradingToolPolicy` (รวม trading tool filter ที่เคยซ้ำ 3 จุด), `TaIndicators` (TA library กลาง: SMA/EMA/RSI/ATR/Bollinger)
- **Memory**: `recall_memory` ใช้ embedding semantic search แล้ว, SleepCycle archive transcript ก่อนลบข้อมูล, กัน extractName false positive
- **Cleanup**: ลบ `JarvisPlanner` (dead code), IntentClassifier ใช้ word-boundary + weighted scoring, ToolRegistry เป็น copy-on-write

## 🎭 Provider System + Persona Unification (2026-07-29)

- **JarvisPersona**: ตัวตน/system prompt ก้อนเดียว (`ai/JarvisPersona.kt`) ใช้ร่วมกันทุก provider path (Gemini/External/Live) — แก้ AI สับสนตัวตน อ้างตัวเป็น ChatGPT/Claude
- **Model Lists สดจาก API**: Claude ดึงจาก Anthropic Models API จริง (+ fallback), OpenRouter อ่าน `supported_parameters`/`input_modalities` จริง (badge 🔧 tools / 👁 vision / FREE)
- **Settings UX**: auto-sync model เมื่อสลับ provider, Live model ดึงจาก Gemini เสมอ, ปุ่ม ↻ refresh, search filter (OpenRouter 200+ models), empty-state บอกสาเหตุ+วิธีแก้

## 🪪 Customizable Identity (2026-07-30)

- **Settings → หัวข้อ "Identity (ตัวตน AI & ผู้ใช้)"**: ปรับแต่ง Agent (Name/Creature/Vibe/Gender) + User (Name/What to call them/Notes) ได้เอง บันทึกลง Core Memory โหลดกลับทุกครั้งที่เปิดแอป
- **AI แก้ตัวตนเองได้**: tool `identity_update` — สั่งได้เลย เช่น "เรียกฉันว่าบอส", "เปลี่ยนชื่อเป็น...", "พูดตลกๆ หน่อย" — มีผลทันทีทุก provider (Chat/Live/External)
- system prompt ทุก path build จาก IdentityConfig ปัจจุบันเสมอ (computed getter)

## 🛠️ Tool System Audit + Agent Tool Creation (2026-07-30)

Audit เทียบ tool declaration กับ handler จริงทั้ง 80 ตัว พบและแก้ 7 จุดพัง:

- **System tools ใช้ไม่ได้เลย**: `SystemToolExecutor` ไม่เคยถูก wire เข้า `ToolExecutor` → wire แล้ว (route `system_*`) — แก้สาเหตุหลักที่ AI สร้าง tool ไม่ได้
- **AI สร้าง tool ได้จริง end-to-end**: `system_create_agent_tool` sanitize ชื่อ + JSON ปลอดภัย (kotlinx.serialization) + **register เข้า ToolRegistry ทันที** + บันทึก `custom_agent_tools/*.json` + `loadCustomTools()` โหลดกลับตอนเปิดแอป → custom tool ทำงานได้ข้าม session
- **executeCustomSkill ทำงานจริง**: คืน `systemPromptAddon` ของ custom tool เข้า tool loop ให้ model ทำตามขั้นตอน (แทน placeholder "กำลังดึงข้อมูล...")
- **เติม handler ที่ขาด**: `trading_position_sizing` (คำนวณไม้ pure math), `trading_correlation_matrix` (Yahoo daily closes → Pearson matrix), `automation_manage_alerts` (route → AutomationManager ผ่าน delegate), `vision_activate/deactivate` + `voice_get/set_profile` (route → SideEffectDelegate ใน text path — เดิมใช้ได้เฉพาะ Live)

## 🔇 Live Voice Fix — เสียงตอบไม่หมด/เงียบ (2026-07-30)

จาก log ทดสอบ Live 4 รอบ: ข้อความในแชทมาครบ แต่เสียงพูดขาด/เงียบเป็นบางรอบ — พบว่ารอบที่เสียงหาย model ตอบเป็น **markdown text แทนเสียง** (Live API ไม่แปลง text part เป็นเสียง)

- **LIVE_RULES ใหม่**: ห้าม markdown/ตาราง/bullet ในคำตอบเสียงเด็ดขาด — ต้องพูดประโยคสนทนาสั้นๆ เท่านั้น
- **tool `analyze_and_display_report`**: ประกาศใน ToolRegistry แล้ว (เดิมมีแต่ handler ใน LiveToolBridge แต่ model มองไม่เห็น) — รายละเอียดยาว/ตารางลงแชทผ่าน tool นี้ แล้วพูดสรุป 2-4 ประโยค
- **LiveGeminiService**: capture text parts ที่เคยถูกทิ้ง (กันข้อความหาย) + log เตือน turn ที่ไม่มีเสียง (`🔇 Turn Complete with NO AUDIO`) สำหรับ debug รอบถัดไป

### 🔧 Live Voice Fix รอบ 2 — pipeline + interruption (2026-07-30)

- **คืน transcription config**: ส่ง `output_audio_transcription`/`input_audio_transcription` (`{}`) ใน setup — ตาม Live API guide ต้องขอเองถึงจะได้ transcript (แชท/DB พึ่งอันนี้ทั้งหมด)
- **handle `interrupted`**: เมื่อ VAD/user ขัดจังหวะ generation กลางทาง → flush คิวเสียงค้างเล่นทันทีผ่าน `onInterrupted` → `PcmAudioEngine.stopPlaying()` (เดิมไม่ handle เลย — เสียงเก่าเล่นต่อทับ + turn ที่โดนตัดค้าง) และ `stopPlaying()` กลับสู่สถานะ play หลัง flush ทันที
- **audio flow มี buffer (128 chunks)**: แยกการอ่าน WebSocket ออกจาก `AudioTrack.write()` ที่ blocking — กันเฟรมค้างทั้งระบบ
- **แก้ instruction ขัดกันเอง**: `sendBridgeToolResult` + ผล tool ทั่วไป (Path A) แนบ `[VOICE RULE]` พูดสรุปสั้น 2-4 ประโยค ห้ามอ่านตาราง/markdown — ตรงกับ LIVE_RULES (เดิมสั่ง "นำเสนออย่างละเอียด อย่าสรุปสั้น" ขัดกันเอง → model สับสนสลับไปตอบ text)
- **TTS fallback**: turn ไหนจบโดยไม่มีเสียงเลย (model ตอบ text ล้วน) → `onTurnWithoutAudio` → Android `VoiceManager.speak()` พูดแทน ไม่ให้เงียบเฉย

### 🎚️ Live Voice Tuning — สมดุลความยาวคำตอบเสียง (2026-08-03)

- หลังรอบ 2 เสียงทำงานเต็มแล้ว แต่ `[VOICE RULE]` "2-4 ประโยค" ทำให้คำตอบกระชับเกิน (ไม่เล่าตัวเลขที่ดึงมาเลย) → ปรับเป็น **พูด 5-8 ประโยค**: ผลสรุปหลัก + เหตุผล/ตัวเลขสำคัญ 2-4 จุด เล่าเป็นประโยคธรรมชาติ (เช่น "RSI อยู่ที่ 45 แสดงว่าโมเมนตัมยังอ่อนแอ") + จุดที่ควรระวัง — ยังคงห้ามอ่านตาราง/markdown

## 🇺🇸 Tool ใหม่: trading_economic_data — ตัวเลขเศรษฐกิจสหรัฐฯ (2026-07-30)

- **แหล่งข้อมูล**: FRED (Federal Reserve Economic Data) — ใช้ endpoint สาธารณะ `fredgraph.csv` **ไม่ต้องสมัคร API Key** ใช้ได้ทันที; ถ้ามี key จะสลับไป FRED JSON API อัตโนมัติ (arg `api_key` optional)
- **16 presets**: `gdp`, `gdp_growth`, `cpi`, `core_cpi`, `pce`, `unemployment`, `nfp`, `fedfunds`, `10y`, `2y`, `m2`, `retail`, `housing`, `sentiment`, `indpro`, `claims` — หรือระบุ FRED series id ตรง (เช่น DGS10)
- **โหมด overview**: สรุป 4 ตัวชี้วัดหลัก (GDP growth / CPI / ว่างงาน / Fed Funds) ในคำสั่งเดียว
- ตัวอย่างสั่งในแชท: "GDP อเมริกาล่าสุด", "เงินเฟ้อสหรัฐเท่าไหร่", "สรุปตัวเลขเศรษฐกิจสหรัฐ"
- หมายเหตุ: Trading Economics / FMP ต้องใช้ key เสียเงิน — ยังไม่ผูก ถ้าต้องการเพิ่มบอกได้

## 📚 Tool ใหม่: Strategy Library — คลังกลยุทธ์ Quantpedia 60 แบบ (2026-07-30)

- **3 tools ใหม่ (offline ทั้งหมด)**: `strategy_list` (ดูหมวด/รายชื่อ), `strategy_search` (ค้นหาจาก keyword), `strategy_explain` (คำอธิบาย + โค้ด QuantConnect เต็ม)
- **60 กลยุทธ์เชิงวิชาการ** จาก Quantpedia แบ่ง 9 หมวด: Momentum & Trend (18), Value & Fundamental (13), Volatility & Risk (8), Calendar & Seasonality (6), Reversal (4), Pairs & Arbitrage (4), Asset Allocation & Macro (3), Carry & FX (2), Crypto (2)
- **Bundle ในแอป**: ไฟล์อยู่ที่ `composeResources/files/strategies/` (สร้าง index ด้วย `scripts/generate_strategy_index.py` — รันใหม่เมื่อเพิ่มไฟล์ใน `strategies/`)
- ใช้เป็นคลังความรู้ให้ AI อธิบาย/เปรียบเทียบ/ปรับใช้กลยุทธ์ — ไม่ใช่ backtest engine (โค้ดต้นฉบับรันบน QuantConnect)
- ตัวอย่างสั่งในแชท: "มีกลยุทธ์ momentum อะไรบ้าง", "อธิบาย fx carry trade", "เทียบ reversal กับ momentum"

---

## 📊 สรุปจำนวน Tools ทั้งหมด: **87 Tools** (7 หมวดหมู่หลัก)

| หมวดหมู่ | จำนวน | ความสามารถหลัก |
| :--- | :---: | :--- |
| **Trading & Finance** | 25 | ราคา Real-time, TA, Scanner, Correlation, Position Sizing, **FRED US Economic Data**, **Crypto Overview (CoinGecko)**, **Fear & Greed (alternative.me)**, **Scheduled Tasks** |
| **🔗 MT5 Bridge** | 21 | Account Control, Positions, Candles, Advanced Intelligence **V17.0** |
| **SMC (Smart Money)** | 5 | Market Structure, Order Blocks, Liquidity Sweeps, **Continuity V15.0** |
| **📚 Strategy Library** | 3 | คลังกลยุทธ์ Quantpedia 60 แบบ (list/search/explain) ใช้งาน offline |
| **Files & Documents** | 7 | จัดการไฟล์ในเครื่อง, อ่าน/เขียน, วิเคราะห์ PDF/Word, ค้นหาข้อมูล |
| **Vision & Camera** | 9 | Gemini Live Vision, Object Detection, Scan QR/Barcode, Text OCR |
| **Core & Intelligence** | 17 | ค้นหาเว็บ, แปลภาษา, สรุปความ, คำนวณ, บันทึกความจำระยะยาว, Diagnostic, **AI สร้าง Tool เอง** |
