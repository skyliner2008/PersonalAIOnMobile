# mt5-core-server

Trading Engine Hub & Multi-Agent Reasoning System.

## Current Runtime: Simplified M15 Wall Scalping (2026-05-30)

The default auto-trading runtime is now `engineMode: M15_WALL_SCALPING`.

- Keeps only `IndicatorPipeline` + `PriceMap` wall analysis as the decision core.
- Aggregates M1/M5/M15/M30/H1/H4 walls into an M15-centered short-term map.
- Uses only the `SCALPING` strategy for initial execution.
- Disables the legacy AI, V25 shadow, manage, learn, multi-strategy, and gate-cascade loops while this mode is active.
- New planner lives in `src/services/auto/core/SimpleScalpingEngine.ts`.

## Features
- **4-Layer Reasoning**: Advanced trade filtering using Gemini AI (Narrative, Technicals, Context, Risk)
- **FIFO Bridge Execution**: Thread-safe communication with MetaTrader 5 via Python bridge
- **Episodic Memory**: RAG-enhanced reasoning using `gemini-embedding-2` for past trade retrieval
- **Observability & Resilience**:
    - **Structured Logging**: Production-grade JSON logs using `Pino`.
    - **Trace ID Propagation**: End-to-end request tracking across Node.js and Python layers.
    - **Unit & Integration Testing**: 30+ tests covering critical trading logic and security.
- **SQLite Persistence**: Local data storage for snapshots, analytics, and knowledge vault.

## Project Roadmap

### ✅ Wave 1: Foundation (Completed)
- [x] Basic MT5 Bridge connectivity.
- [x] Simple risk management (DD, Daily Loss).
- [x] AI reasoning integration (Gemini).

### 🛡️ Wave 2: Testing Net (Completed 2026-04-24)
- [x] **Connectivity**: CORS support for Mobile/ngrok access.
- [x] **Resilience**: FIFO Mutex and Smart Symbol Resolver (BTC -> XBTUSD mapping).
- [x] **Parsing**: Resilient JSON Unwrapping for Mobile tools in `TradingToolExecutor`.
- [x] **Stability**: Fixed ReferenceErrors and centralized logging utilities.
- [x] **Infrastructure**: Vitest fully integrated for CI.

### 🚀 Wave 3: Scale & Refactor (Next)
- [x] **Decoupling**: Separate AutoTradingService into specialized sub-services.
- [ ] **Config Hot-Reload**: Update risk parameters without restart.
- [ ] **Enhanced RAG**: Integration with deeper market history.

### 🎯 Wave 4: V24.0 Fade-the-Level (2026-05-07)
- [x] **Proximity Gate** (`auto/core/ProximityGate.ts`): เข้าออเดอร์เฉพาะเมื่อ
  ราคาใกล้ S/R wall (≤100 pip) — fade ไม่ chase
- [x] **Sequential Entry Gate**: 1 ไม้/symbol → เปิดเพิ่มได้เมื่อ TRAIL ทำงานหรือ
  ขาดทุน ≥25% ของ SL
- [x] **Basket BE Guard**: ไม่ปิดไม้ขาดทุนแล้วเรียก "BE" ถ้า basket รวมยังลบ
- [x] **RRR float-safe compare**: แก้ `1.4999... < 1.5` ใน V21.0 EA Fallback
  และ V23.0 EA-Only paths
- รายละเอียด: [`07_Trading_Intelligence/23_FadeTheLevel_V240.md`](../.obsidian-wiki/07_Trading_Intelligence/23_FadeTheLevel_V240.md)

### ☁️ Wave 5: Google Cloud Vertex AI Integration (2026-05-13)
- [x] **Vertex AI Provider** (`providers/vertexai.ts`): ใช้ ADC (Application Default Credentials) สำหรับ enterprise-grade Gemini access
- [x] **Mobile Proxy** (`routes/vertexProxy.ts`): ให้ mobile app เรียก Vertex AI ผ่าน server (ADC ไม่ทำงานบนมือถือ)
- [x] **Registry Integration**: ลงทะเบียนใน provider system ข้างๆ Gemini/OpenAI/Claude

### 🛠️ Wave 6: V26.1 Structural TP & EA Stability (2026-05-15)
- [x] **Structural TP Cap** (`auto/analyzers/smc/priceMapBuilder.ts`): แก้ไข Bug ที่ TP กะโดดข้ามกำแพง 4★ — เพิ่มระบบ `targetWallStars` เพื่อ Cap TP ให้แม่นยำขึ้น
- [x] **EA-ONLY Hard-Skip**: ปรับปรุงให้ `slTpAnalystAgent` ข้ามการเรียก AI (LLM) 100% เมื่อปิดโหมด AI เพื่อประหยัด API และเพิ่มความเสถียร
- [x] **Bridge Synchronization**: ขยาย Window การดึง History เป็น 60 วัน (UTC-Safe) แก้ไขปัญหาออเดอร์ค้างใน Journal เนื่องจากการต่างกันของ Timezone
- รายละเอียด: [`07_Trading_Intelligence/32_TP_Structural_Wall_Correction_V261.md`](../.obsidian-wiki/07_Trading_Intelligence/32_TP_Structural_Wall_Correction_V261.md)
- [x] **ReferenceError Fix** (`autoTradingService.ts`): แก้ไขบั๊ก `allCandles is not defined` ที่ทำให้ Market Cycle ล้มเหลววนลูป
- [x] **Event-Driven MTF Sync**: ปรับปรุงการประกาศตัวแปร `allCandles` ให้รองรับการประมวลผล Multi-Timeframe แบบ Real-time
- รายละเอียด: [`07_Trading_Intelligence/33_AutoEngine_Runtime_Stability_V262.md`](../.obsidian-wiki/07_Trading_Intelligence/33_AutoEngine_Runtime_Stability_V262.md)

### Wave 7: V26.4 Zone-Aware Gate MTF Local Override (2026-05-18)
- [x] **MTF Zone Context** (`auto/core/ZoneAwareGate.ts`): H4/H1 are treated as major/context layers while M30/M15/M5/active TF can provide execution-zone pullback alignment.
- [x] **Fresh LTF FVG Override**: `SMC_FVG_SCALP` now checks aligned FVGs across H1/M30/M15/M5/active TF with ATR tolerance, so H4 discount alone does not veto every trend-follow SELL.
- [x] **Stale-Stop R Consistency**: stale-position time stop now uses broker `priceOpen` first, matching the manager audit R calculation.
- Details: [`07_Trading_Intelligence/34_ZoneAwareGate_MTF_LocalOverride_V264.md`](../.obsidian-wiki/07_Trading_Intelligence/34_ZoneAwareGate_MTF_LocalOverride_V264.md)

### Wave 8: V26.6 Scalping Routing (2026-05-18)
- [x] **Executable SCALPING Route** (`auto/deterministicEngine.ts`): valid M5/M15 `SCALPING` analysis can now become the top-level strategy instead of being forced into `SMC_FVG_SCALP`.
- [x] **Scalp-Aware EA Fallback RRR** (`autoTradingService.ts`): EA fallback uses `strategy.scalpingRRR` for scalp-like strategies, reducing false blocks such as `RRR 1.26 < 1.5`.
- [x] **Type Sync** (`auto/types.ts`): `AnalysisSummary.candles` is declared for SignalDetector and MTF execution gates.
- Details: [`07_Trading_Intelligence/36_Scalping_Routing_V266.md`](../.obsidian-wiki/07_Trading_Intelligence/36_Scalping_Routing_V266.md)

### Wave 9: V26.7 Scalp TP/SL Bracket Rebalance (2026-05-18)
- [x] **SMC Scalp Reward Floor** (`autoTradingService.ts`): `SMC_FVG_SCALP` now targets at least `1.35R`.
- [x] **Normal-Flow Compact Bracket**: compact scalp bracket now runs outside V25-only mode and tightens oversized SL before accepting narrow PriceMap-capped TP.
- [x] **XAUUSD Risk Default** (`PersistenceService.ts`): compact scalp default risk tightened to `3.0` points with `0.16 * ATR` max-risk guidance.
- Details: [`07_Trading_Intelligence/37_Scalp_TpSl_Bracket_V267.md`](../.obsidian-wiki/07_Trading_Intelligence/37_Scalp_TpSl_Bracket_V267.md)

### Wave 10: V26.8 SMC FVG Scalp Proof Gate (2026-05-18)
- [x] **No Synthetic FVG Strategy** (`auto/deterministicEngine.ts`): ranging fallback uses `MEAN_REVERSION`; `SMC_FVG_SCALP` is no longer created without FVG evidence.
- [x] **Sweep Relabel** (`auto/core/SignalDetector.ts`): raw MTF liquidity sweeps are generic `SCALPING`; only `FVG_FILL` keeps the `SMC_FVG_SCALP` strategy label.
- [x] **Strict Fill Gate** (`auto/core/ZoneAwareGate.ts`, `autoTradingService.ts`): final scalp execution requires price inside an aligned active FVG with at least `50%` fill.
- Details: [`07_Trading_Intelligence/38_SmcFvgScalp_ProofGate_V268.md`](../.obsidian-wiki/07_Trading_Intelligence/38_SmcFvgScalp_ProofGate_V268.md)

### Wave 11: V26.9 Wall-to-FVG Magnet Scalp (2026-05-18)
- [x] **New Playbook** (`auto/core/SignalDetector.ts`): `SMC_FVG_MAGNET_SCALP` requires a 3+ star wall near price, an unmitigated M15 FVG target, and M1/M5 reclaim or micro-break confirmation.
- [x] **HTF Context Bypass** (`autoTradingService.ts`): this playbook skips HTF bias/context and Zone-Aware Gate vetoes, while still keeping wall proximity, spread, sequential, and hard-risk protection.
- [x] **FVG-Target Bracket** (`autoTradingService.ts`): TP is locked at FVG edge minus spread and SL is recalculated from the remaining TP distance using the scalp RRR formula.
- [x] **Bangkok-Day Analytics** (`scripts/advanced_analytics.mjs`): date-specific reports now use the Asia/Bangkok trading day so `npm run analyze YYYY-MM-DD` matches decision JSONL logs.
- Details: [`07_Trading_Intelligence/39_WallFvgMagnetScalp_V269.md`](../.obsidian-wiki/07_Trading_Intelligence/39_WallFvgMagnetScalp_V269.md)

### Wave 12: V26.10 Mean Reversion Guard (2026-05-19)
- [x] **Audit Finding** (`scripts/advanced_analytics.mjs`): 2026-05-18 `MEAN_REVERSION` was the main losing bucket (`14` trades, net `-$42.75`) despite the day ending green.
- [x] **TP Cap** (`autoTradingService.ts`): `MEAN_REVERSION` TP is capped to `adaptive.meanReversionMaxRrr` (`1.8R` default), so range fades do not inherit 4R-6R swing-like targets.
- [x] **Duplicate Cooldown** (`autoTradingService.ts`, `TradingExecutionService.ts`): `MEAN_REVERSION` uses `adaptive.meanReversionDuplicateIntentTtlMs` (`900_000ms` default) while normal entries keep the existing 5-minute TTL.
- Details: [`07_Trading_Intelligence/40_MeanReversion_OrderGuard_V2610.md`](../.obsidian-wiki/07_Trading_Intelligence/40_MeanReversion_OrderGuard_V2610.md)

### Wave 13: V26.11 M15 Scalp Pressure Guard (2026-05-19)
- [x] **Latest Order Audit** (`trade_decision_logs/2026-05-19.jsonl`): `A_SCALPING_XAUUSD` opened BUY while M15 had opposing Bearish OB pressure, then SELL was later blocked by anti-hedge because the BUY was open.
- [x] **Generic Scalp Guard** (`autoTradingService.ts`, `ZoneAwareGate.ts`): exact `SCALPING` entries now block when M15 shows opposing OB/FVG or sweep+FVG pressure.
- [x] **Config Toggle** (`PersistenceService.ts`, `auto/types.ts`): `adaptive.enableScalpM15PressureGuard` defaults to `true` and can be disabled if needed.
- Details: [`07_Trading_Intelligence/41_M15_ScalpPressureGuard_V2611.md`](../.obsidian-wiki/07_Trading_Intelligence/41_M15_ScalpPressureGuard_V2611.md)

### Wave 14: V26.12 Decision Gate Analytics Observability (2026-05-19)
- [x] **Analyze Runtime Fix** (`scripts/advanced_analytics.mjs`): restored the missing `existsSync` import so `npm run analyze YYYY-MM-DD` works again.
- [x] **Clear Block Categories** (`autoTradingService.ts`): future decision-feed rows classify `Decision Side Guard`, `M15 SMC Pressure Guard`, `Anti-Hedge`, and `Fitness Below Minimum` separately.
- [x] **Historical Report Normalization** (`scripts/advanced_analytics.mjs`, `scripts/deep_analysis.py`): reports reclassify those gate reasons from `risk_gate`, so today's/previous rows are readable without rewriting DB history.
- Details: [`07_Trading_Intelligence/42_DecisionGate_AnalyticsObservability_V2612.md`](../.obsidian-wiki/07_Trading_Intelligence/42_DecisionGate_AnalyticsObservability_V2612.md)

### Wave 15: V26.13 PriceMap Wall Anchor Stability (2026-05-19)
- [x] **Structural Anchor Priority** (`auto/analyzers/smc/priceMapBuilder.ts`): when a cluster contains OB/FVG/liquidity/structure/swing plus context, the structural sources set `price`, `priceTop`, and `priceBottom`.
- [x] **Context as Confluence**: Pivot/VWAP/Fib still add labels and stars, but no longer move a real wall just because a dynamic reference level drifts inside the ATR cluster.
- [x] **Tests** (`priceMapBuilder.test.ts`): covers structural wall + moving context and context-only wall behavior.
- Details: [`07_Trading_Intelligence/43_PriceMap_WallAnchorStability_V2613.md`](../.obsidian-wiki/07_Trading_Intelligence/43_PriceMap_WallAnchorStability_V2613.md)

### Wave 16: V26.14 Wall Break Scalp + Fresh Decision Price (2026-05-19)
- [x] **New Local Playbook** (`auto/core/SignalDetector.ts`): `SMC_WALL_BREAK_SCALP` can fire without M15 FVG when a 3+ star wall has both M1 and M5 break/reclaim confirmation.
- [x] **Execution PD Filter**: BUY requires M1 and M5 in `DISCOUNT`; SELL requires M1 and M5 in `PREMIUM`, keeping the non-FVG scalp local and selective.
- [x] **Fresh Decision Price** (`autoTradingService.ts`): AutoEngine syncs `last.c` to V25 tick mid or broker symbol price before deterministic strategy selection and gates, avoiding stale H1 close decisions near fast wall touches.
- [x] **Tests** (`SignalDetector.test.ts`): covers BUY, SELL, and M5-not-confirmed rejection for the new playbook.
- Details: [`07_Trading_Intelligence/44_WallBreakScalp_FreshDecisionPrice_V2614.md`](../.obsidian-wiki/07_Trading_Intelligence/44_WallBreakScalp_FreshDecisionPrice_V2614.md)

### Wave 17: V26.15 Slippage Guard & Analytics Improvement (2026-05-19)
- [x] **Entry Drift/Slippage Guard** (`autoTradingService.ts`): blocks orders when price drifts >15% of ATR from decision price and RRR degrades below minimum (Scalp 1.0R, others 1.2R). Prevents the $-39.05 loss pattern where delayed entry inflates SL and shrinks TP.
- [x] **UNKNOWN Block Category Fix** (`autoTradingService.ts`, `advanced_analytics.mjs`): all 1,134 UNKNOWN records were `risk_gate="market_closed"` — added `MARKET_CLOSED`, `ENTRY_DRIFT`, and `RSI_EXTREME` to block category classifier and analytics SQL.
- [x] **MEAN_REVERSION Tuning** (`PersistenceService.ts`): `meanReversionMaxRrr` lowered from 1.8 to 1.5 — analytics showed 16 trades with 62.5% WR but net -$34.25 due to oversized losses.
- [x] **RANGE Strategy Disabled** (`analysis.ts`): 0% WR, -$10.88 from 2 trades — RANGE conditions now route to `MEAN_REVERSION` with proper TP cap.
- Details: [`07_Trading_Intelligence/45_SlippageGuard_AnalyticsImprovement_V2615.md`](../.obsidian-wiki/07_Trading_Intelligence/45_SlippageGuard_AnalyticsImprovement_V2615.md)

### Wave 19: V26.17 Breakout Local Confirmation Guard (2026-05-20)
- [x] **HTF-Only Breakout Guard** (`auto/deterministicEngine.ts`): deterministic `BREAKOUT` now waits when M15 and M5 both oppose the HTF breakout side.
- [x] **Wrong-Zone Confirmation**: SELL in `DISCOUNT` or BUY in `PREMIUM` needs M15/M5 confirmation before becoming actionable.
- [x] **Cleaner Logs**: repeated `BREAKOUT SELL` attempts that would later hit M15 SMC Pressure Guard now become `SKIP` with `Breakout wait` rationale at strategy-selection time.
- [x] **Tests** (`deterministicEngine.test.ts`): covers local-opposition skip and confirmed-breakout pass.
- Details: [`07_Trading_Intelligence/47_BreakoutLocalConfirmationGuard_V2617.md`](../.obsidian-wiki/07_Trading_Intelligence/47_BreakoutLocalConfirmationGuard_V2617.md)

### Wave 20: V26.18 Execution-Aware MTF Weighting (2026-05-20)
- [x] **MTF Weight Rebalance** (`auto/deterministicEngine.ts`): deterministic side scoring now uses H4 `22%`, H1 `23%`, M30 `15%`, M15 `18%`, M5 `15%`, and M1 `7%` instead of letting H4/H1 own 70%.
- [x] **Execution Guard**: `BREAKOUT`, `MEAN_REVERSION`, and `TREND_FOLLOW` now wait when M15 and M5 both oppose the selected side.
- [x] **Scalp Exemption**: local-proof scalp playbooks keep their own evidence path, so `SMC_FVG_MAGNET_SCALP` and `SMC_WALL_BREAK_SCALP` are not re-blocked by generic HTF context.
- [x] **Tests** (`deterministicEngine.test.ts`): covers HTF-selected `MEAN_REVERSION` being skipped when local M15/M5 oppose.
- Details: [`07_Trading_Intelligence/48_ExecutionAwareMtfWeighting_V2618.md`](../.obsidian-wiki/07_Trading_Intelligence/48_ExecutionAwareMtfWeighting_V2618.md)

### Wave 21: V26.19 Unified V25 Decision Path (2026-05-20)
- [x] **Single Execution Authority** (`autoTradingService.ts`): fresh V25/PB candidates are consumed as local-proof deterministic priors by AutoEngine.
- [x] **Candidate-Only V25 Default** (`auto/v25/playbooks/index.ts`): V25 playbooks record candidates for the unified pipeline; direct V25 order sending is legacy opt-in only.
- [x] **Local-Proof V25 Strategies**: `V25_*` candidates bypass generic HTF counter-trend/context gates but still pass shared risk, duplicate, spread, and order preflight gates.
- [x] **Latest Log Fix** (`autoTradingService.ts`, `auto/v25/playbooks/index.ts`): PB candidate SL/TP now survive deterministic fast-path, `V25_*` TP-cap checks use scalp-like RRR handling, and old V25 AI Bias Gate is disabled in unified candidate mode.
- [x] **Dashboard Simplification** (`public/index.html`, `public/dashboard.js`): removed the `V25 Only` toggle; `V25 Adaptive` starts the realtime wall/proof engine.
- Details: [`07_Trading_Intelligence/49_UnifiedV25DecisionPath_V2619.md`](../.obsidian-wiki/07_Trading_Intelligence/49_UnifiedV25DecisionPath_V2619.md)

### Wave 22: V26.20 PriceMap Execution Tightening (2026-05-21)
- [x] **Final RRR Floor** (`autoTradingService.ts`): PriceMap-capped V25/scalp plans must still meet the executable strategy floor; the old `1.0R` scalp escape hatch no longer allows weak final brackets.
- [x] **Fresh V25 Preflight** (`autoTradingService.ts`): unified PB1 candidates expire after `30s` and must pass live tick RRR/SL/TP validation before AutoEngine can consume them.
- [x] **Breakout Proximity Discipline** (`autoTradingService.ts`): breakout/continuation labels no longer bypass Proximity Gate when H4 is `RANGING`.
- [x] **Early Trade Management** (`auto/core/TradeManagementService.ts`): XAU positions can move to BE at `0.4R`; V25/scalp/breakout proof failures reduce at `-0.45R` and close at `-0.65R` after `90s`.
- Details: [`07_Trading_Intelligence/50_PriceMapExecutionTightening_V2620.md`](../.obsidian-wiki/07_Trading_Intelligence/50_PriceMapExecutionTightening_V2620.md)

### Wave 23: V26.21 EA-Only Execution Hardening (2026-05-22)
- [x] **EA-Only Threshold** (`autoTradingService.ts`, `PersistenceService.ts`): deterministic fallback now uses `adaptive.eaOnlyMinConfidence` (`55` default) when AI mode is off.
- [x] **Local Consensus Guard** (`autoTradingService.ts`): EA-only entries are blocked when both M1 and M5 strongly oppose the selected side.
- [x] **MR/TP Re-entry Guard** (`autoTradingService.ts`): `MEAN_REVERSION` must pass Proximity Gate, and same-side re-entry near a recent TP close is blocked by `POST_TP_COOLDOWN`.
- [x] **Live Early Invalidation** (`autoTradingService.ts`): the manager loop now reduces failed proof at `-0.45R` and closes at `-0.65R` instead of relying only on planner-time logic.
- [x] **Stable Partial Markers** (`TradingExecutionService.ts`, `TradeManagementService.ts`): partial closes mark `PARTIAL_1R`/`PARTIAL_2R` per ticket to prevent repeated 1R partials.
- Details: [`07_Trading_Intelligence/51_EaOnlyExecutionHardening_V2621.md`](../.obsidian-wiki/07_Trading_Intelligence/51_EaOnlyExecutionHardening_V2621.md)

### Wave 24: V26.22 PriceMap Wall Overlay Stability (2026-05-22)
- [x] **Near-Touch Wall Retention** (`auto/analyzers/smc/priceMapBuilder.ts`): PriceMap no longer removes raw SMC/context levels just because price is within the old min-distance band.
- [x] **Stable Overlay Lists**: `walls` keeps the full overlay map; printed PriceMap ladders split strictly by current price so visual resistance/support order stays correct.
- [x] **Weighted Structural Anchor**: cluster joins and wall prices use larger TF structural anchors first, reducing M1/context drift and chain-cluster wall shifts.
- [x] **Context Discipline**: Pivot/VWAP/Fib can boost real structural walls, but context-only overlap is capped and cannot become a 5-star wall by itself.
- [x] **Tests** (`priceMapBuilder.test.ts`): covers the `4520.53` near-touch MTF wall, context-only cap, and existing context anchor behavior.
- Details: [`07_Trading_Intelligence/52_PriceMapWallOverlayStability_V2622.md`](../.obsidian-wiki/07_Trading_Intelligence/52_PriceMapWallOverlayStability_V2622.md)

### Wave 25: V26.23 Entry Drift Synced Decision Price (2026-05-22)
- [x] **False Drift Block Fix** (`autoTradingService.ts`): Entry Drift now compares final entry against the already-synced live decision price (`last.c`) instead of the stale pre-sync candle close.
- [x] **Wall-Touch Flow Alignment**: PriceMap proximity and Zone-Aware-approved setups are no longer rejected solely because the old candle close was far from the live tick.
- [x] **Protection Preserved**: real slippage/drift remains blocked when final executable RRR falls below the strategy floor.
- [x] **PriceMap Count Observability** (`IndicatorPipeline.ts`): log output now separates overlay walls, ladder-position counts, and execution-side resistance/support counts.
- Details: [`07_Trading_Intelligence/53_EntryDriftSyncedDecisionPrice_V2623.md`](../.obsidian-wiki/07_Trading_Intelligence/53_EntryDriftSyncedDecisionPrice_V2623.md)

### Wave 26: V26.24 Gate/Strategy Runtime Controls (2026-05-23)
- [x] **Gate Toggle Schema** (`auto/types.ts`, `PersistenceService.ts`): `adaptive.gateToggles` persists per-gate switches and deep-merges single-switch updates.
- [x] **Strategy Toggle Schema** (`auto/types.ts`, `PersistenceService.ts`): `adaptive.strategyToggles` controls each strategy, including V25 playbooks.
- [x] **AutoEngine Wiring** (`autoTradingService.ts`): toggles now affect Pre-AI MTF Zone, EA confidence/RRR, Decision Side, Proximity, Zone-Aware, FVG Fill, Sequential, Entry Drift, Hard Risk, Risk Params, order preflight, and related guards.
- [x] **Dashboard Controls** (`public/index.html`, `public/dashboard.js`): Auto Trade settings now show `Execution Gates` and `Strategy Switches`.
- Details: [`07_Trading_Intelligence/54_GateStrategyRuntimeControls_V2624.md`](../.obsidian-wiki/07_Trading_Intelligence/54_GateStrategyRuntimeControls_V2624.md)

### Wave 27: V26.25 Unified Zone & Execution Quality & Performance Fix (2026-05-23)
- [x] **Unified Zone Gate** (`autoTradingService.ts`): Combined *Pre-AI Zone Gate* and *Zone-Aware Gate* to prevent duplicate pre-AI blocks and evaluate RRR metrics efficiently.
- [x] **Execution Quality Gate**: Integrated *Entry Drift* and *Hard Risk* checks into a single step for checking volatility and slippage.
- [x] **Proportional SL Scaling**: Added logic to automatically shrink Stop Loss (SL) proportionally when Take Profit (TP) is constrained by structural walls, keeping RRR favorable.
- [x] **Database Query Fix**: Fixed SQLite table query bugs in analytics scripts by mapping queries to `auto_trading_journal`.
- [x] **Web UI & Dashboard Alignment** (`dashboard.js`): Updated the `Execution Gates` panel on the Web Dashboard to display the new unified gates (Unified Zone and Execution Quality), ensuring perfect synchronization with the backend and implementing strict backward compatibility mapping in the gateway controller.
- Details: [`07_Trading_Intelligence/55_UnifiedZoneExecutionQuality_V2625.md`](../.obsidian-wiki/07_Trading_Intelligence/55_UnifiedZoneExecutionQuality_V2625.md)

### Wave 28: V26.26 Modular Codebase Refactoring (2026-05-23)
- [x] **Backup Established**: Created a safe snapshot at `autoTradingService.ts.bak` for easy fallback.
- [x] **Decoupled Helpers** (`src/services/auto/helpers/`):
  - `decisionLogger.ts`: Extracted log converters and decision block classification logic.
  - `strategySelector.ts`: Decoupled strategy selection, candidate vetting, RRR calculation, price resolution, and gate helpers (including `isGateEnabled`).
- [x] **Type Integrity & Compilation**: Fixed complex imports like `TradePlan` and synced types, leading to a 100% successful build (`npm run build`) and Vitest verification pass (103/103 tests passed).
- Details: [Walkthrough](file:///C:/Users/JOJO/.gemini/antigravity-ide/brain/810a9586-c5e4-4a19-8b25-7877e700d231/walkthrough.md)

### Wave 29: Deep Decoupling of AutoTradingService (2026-05-23)
- [x] **Sub-Engine Separation**: แยก 4 เมธอดการทำงานหลักขนาดยักษ์ (`runCycle`, `runManage`, `runLearn` และ `executeManagementPlan`) ออกจากไฟล์ประสานงานหลักไปรันบนโมดูลเฉพาะทางย่อยใน `src/services/auto/core/` (MarketCycleEngine.ts, PositionManagerEngine.ts, LearningEngine.ts)
- [x] **Codebase Downsizing**: ลดขนาดความหนาแน่นของไฟล์ประสานงานหลัก `autoTradingService.ts` ลงกว่า 85% จาก 5,429 บรรทัดเหลือเพียง 777 บรรทัด เพิ่มความปลอดภัยในการบำรุงรักษา
- [x] **Zero Breaking Changes**: รักษาความเข้ากันได้ย้อนหลัง 100% ผ่าน Wrapper delegator methods โดยส่งผ่านบริบท `this` ไปยัง Sub-Engines เพื่อรักษาโครงสร้างพฤติกรรมเดิม
- [x] **Verification**: ยืนยันความถูกต้องด้วยการคอมไพล์ผ่าน 100% (`npm run build`) และผ่าน Unit Tests ทั้งหมด 103/103 เคสสำเร็จ พร้อมทั้งรันสคริปต์รายงานวิเคราะห์พอร์ต `npm run analyze` ได้ครบถ้วนปกติ
- รายละเอียด: [`07_Trading_Intelligence/57_DeepDecoupling_AutoTradingService_V2627.md`](../.obsidian-wiki/07_Trading_Intelligence/57_DeepDecoupling_AutoTradingService_V2627.md)

### Wave 30: V26.30 Scalping & FVG Playbooks Optimization (2026-05-23)
- [x] **Wall Break Price-Zone Relaxation** (`auto/core/SignalDetector.ts`): ปรับปรุง `SMC_WALL_BREAK_SCALP` ให้ยอมรับโซน `DISCOUNT` หรือ `EQ` ใน M1/M5 แก้วิกฤตตรรกะชนกันเชิงโครงสร้าง (เดิมบังคับเฉพาะ DISCOUNT เท่านั้น)
- [x] **FVG-Fill Gate Mitigation** (`auto/core/MarketCycleEngine.ts`): ผ่อนปรน `minFillPct` ในด่าน FVG-Fill จากเดิม `0.5` เหลือ `0.15` (15%) สำหรับกลยุทธ์ `SMC_FVG_SCALP` ช่วยให้เข้าออกไม้ได้ทันท่วงที
- [x] **Dynamic RRR & Stops Level Guard** (`auto/core/MarketCycleEngine.ts`): พัฒนาระบบคำนวณ Dynamic RRR Floor ปรับลด RRR Floor เหลือ `1.1` หรือ `1.2` ชั่วคราวเมื่อระยะ TP แคบ เพื่อให้ได้ระยะ SL ที่ปลอดภัยและไม่ขัดแย้ง Stops Level ของโบรกเกอร์
- [x] **M15 Pressure Guard Bypass for Scalpers** (`auto/core/MarketCycleEngine.ts`): เพิ่มเงื่อนไขพิเศษให้กลยุทธ์ `SCALPING` สามารถข้ามการบล็อกจาก M15 SMC Pressure Guard ได้ หากได้รับสัญญาณร่วม (Confluence >= 65) สนับสนุนทิศทางเดียวกันในระดับ M1/M5
- รายละเอียด: [`07_Trading_Intelligence/58_Scalping_FVG_Unlocking_V2630.md`](../.obsidian-wiki/07_Trading_Intelligence/58_Scalping_FVG_Unlocking_V2630.md)

### Wave 31: V26.31 PriceMap Proximity Suppression & Stars Scoring Cap (2026-05-23)
- [x] **Proximity Suppression Filter** (`auto/analyzers/smc/priceMapBuilder.ts`): พัฒนาตรรกะกรองยุบแนวรับ/แนวต้านระยะประชิดฝั่งเดียวกัน ที่อยู่ในรัศมีต่ำกว่า `0.75 * ATR` เพื่อป้องกันการเกิดแนวราคาซ้อนทับกันถี่ยิบเกินไปและเปิดความลื่นไหลในการเคลื่อนที่ของบอททองคำ XAUUSD
- [x] **Institutional Wall Stars Cap** (`auto/analyzers/smc/priceMapBuilder.ts`): กำหนดกฎจำกัดคะแนนความแข็งแกร่งของกำแพงราคา หากไม่มีสัญญาณคอนฟลูเอนซ์ร่วมจากระดับเวลาขนาดใหญ่ (HTF: `H1`, `H4`, `D1`, `SWING`) ให้สะกดคะแนนไว้ไม่เกิน 3★ เพื่อป้องกันกำแพงดาวเยอะเทียมดักราคารายทาง
- [x] **Zero Regression Testing**: ติดตั้งและทวนสอบการทดสอบ Vitest เพิ่มขึ้น 7/7 และการทดสอบรวมระบบ 106/106 เคสผ่านฉลุยพร้อมคำสั่ง build และวิเคราะห์สำเร็จสมบูรณ์
- รายละเอียด: [`07_Trading_Intelligence/59_PriceMap_ProximitySuppression_V2631.md`](../.obsidian-wiki/07_Trading_Intelligence/59_PriceMap_ProximitySuppression_V2631.md)

### Wave 32: V26.35 Multi-Scale Strategy Decoupling & HTF Pullback System (2026-05-23)
- [x] **LTF Scalping Decoupling** (`auto/core/ZoneAwareGate.ts`, `auto/deterministicEngine.ts`): แยกเกตกรองโซนกลยุทธ์เล่นสั้นออกจากแนวกรอบ HTF ขนาดใหญ่ ทำให้บอทเทรดสั้นระดับ M15/M5/M1 สามารถออกออเดอร์เก็บกำไรในกรอบแกว่งตัวกว้าง 10,000 จุดของ XAUUSD ได้อย่างเป็นอิสระ โดยไม่ถูก H4 Discount บล็อก
- [x] **HTF-Aligned LTF Pullback System** (`auto/helpers/strategySelector.ts`, `auto/core/MarketCycleEngine.ts`): พัฒนาระบบปลดล็อกโซนย่อตัว (Pullback Override) สำหรับกลยุทธ์ตามเทรนหลัก `TREND_FOLLOW` โดยจำกัดให้บอทออกออเดอร์เฉพาะฝั่งตามแนวโน้มใหญ่ (เช่น SELL ในแนวโน้ม BEAR) แต่สามารถ SELL ใน H4 Discount ได้ หากราคาในระดับย่อย (M15/M5) มีการดีดกลับ (Pullback) ขึ้นมาในโซนได้เปรียบระดับเล็กรอบย่อย (Local Premium) สำเร็จ
- [x] **Zero Regression Testing**: ทวนสอบระบบผ่าน Vitest ทั้งหมด 107/107 เคส ผ่านสมบูรณ์ 100% พร้อมการคอมไพล์สำเร็จและการรันวิเคราะห์ข้อมูลทำงานปกติ
- รายละเอียด: [`07_Trading_Intelligence/60_MultiScale_StrategyDecoupling_V2635.md`](../.obsidian-wiki/07_Trading_Intelligence/60_MultiScale_StrategyDecoupling_V2635.md)


## Quick Start
1. `npm install`
2. Configure `.env` (CORS_ORIGIN, etc. Secrets are auto-generated in the database)
3. `npm run dev` or `start.bat`
4. Login with default credentials: ID `admin` / Password `admin`
5. Run tests: `npm test`

### Vertex AI Setup (Optional)
1. ตั้งค่า GCP: `gcloud auth application-default login`
2. เพิ่มใน `.env`:
   ```
   VERTEX_PROJECT_ID=your-project-id
   VERTEX_LOCATION=us-central1
   ```
3. เลือก "Vertex AI" เป็น provider ใน Dashboard Settings
