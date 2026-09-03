# 🤖 PersonalAIBot — JARVIS for Android

> **Full Non-Trading Tools Audit & Flexibility Upgrades (2026-09-04):**
> - **Thai Date & Time Context (`get_current_datetime`)**: Upgraded to provide complete Thai day-of-week ("วันศุกร์"), Thai month names ("กันยายน"), Buddhist Era year ("พ.ศ. 2569"), seconds, and timezone identifier (`Asia/Bangkok`), eliminating date/time ambiguity in Thai conversation.
> - **Thai Unit Normalization (`convert_units`)**: Added `normalizeUnitName` supporting Thai unit names and abbreviations (e.g. "กิโลเมตร" $\to$ `km`, "ไร่" $\to$ `rai`, "ตารางวา" $\to$ `sqwa`, "วา" $\to$ `wa`, "เซลเซียส" $\to$ `celsius`), expanded traditional Thai land units (1 ไร่ = 4 งาน = 400 ตารางวา = 1,600 ตารางเมตร), and made input parameters flexible (`value`/`amount`, `from_unit`/`from`, `to_unit`/`to`).
> - **Full Grounding & Web Search in Text Chat (`GeminiService.kt`)**: Added interceptor in `generateResponseWithTools` for `search_web`, `translate_text`, and `summarize_text`, executing Google Search Grounding and direct translation in standard text chat without returning raw request markers.
> - **Fuzzy Matching for Camera & Vision Tools (`CameraToolExecutor.kt`)**: Replaced rigid exact string matches with flexible substring parsing for providers (`"gpt-4o"`, `"gpt4o"`, `"claude"`, `"gemini"`) and operating modes (`"stream"`, `"photo"`, `"object"`, `"ar"`).
> - **Expanded File Format Permissions (`FileToolExecutor.kt`)**: Expanded writable code extensions to include C/C++, Dart, Go, Rust, Swift, PHP, SVG, and .env files.
> - **Universal Timeframe Normalization for Alerts (`JarvisOrchestrator.kt`)**: Integrated `TaIndicators.normalizeTimeframe` into `onManageAlerts` to support MT5 formats (`m15`, `h1`, `d1`) and weekly (`1w`) without rejection. Tested with 100% passing tests (`NonTradingToolsTest.kt`).
>
> **Comprehensive Tool Audit & Flexible Unconstrained Tool Creation System (2026-09-04):**
> - **Unconstrained Dynamic Tool Creation (`system_create_agent_tool`)**:
>   - Lifted rigid naming restrictions (no longer strictly forced to start with `custom_` unless colliding with built-in tools).
>   - Added full dynamic parameter schema support (`parameters` / `parametersJson`), allowing newly created tools to define arbitrary arguments in Full JSON schema, Short JSON format, or comma-separated lists (`"symbol, timeframe, risk_pct"`).
>   - Stored and restored parameter definitions across restarts via `custom_agent_tools/*.json` and `JarvisOrchestrator.kt`, ensuring custom tools permanently retain native Gemini function-calling capabilities.
> - **Template Placeholder Interpolation & Native Formula Execution Engine**:
>   - In `executeCustomSkill`, added template argument interpolation supporting `{{param}}`, `{param}`, and whole-word regex variable matching.
>   - Introduced native `formula` execution mode: automatically computes mathematical calculations (e.g. dynamic Lot size, Risk-to-Reward ratio, Pivot points) via `evalMath` and returns calculated values with step-by-step transparency.
> - **Unrestricted Trading & SMC Tool Schemas**:
>   - Relaxed overly rigid parameter requirements (`exchange` is now optional across `trading_technical_analysis`, `trading_multi_timeframe`, `trading_combined` with auto-resolution).
>   - Expanded timeframe enums across `trading_combined`, `trading_harmonic_scan`, `trading_elliot_modern_analysis`, `trading_smc_analysis`, `trading_smc_orderblocks`, and `trading_smc_structure` to support all standard intervals (`1m`, `5m`, `15m`, `30m`, `1h`, `4h`, `1D`, `1W`) and MT5 formats (`m1`, `m5`, `m15`, `m30`, `h1`, `h4`, `d1`, `w1`). Verified with 100% passing tests (`DynamicToolCreationTest.kt`).
>
> **ForexFactory Economic Calendar Timezone Accuracy & Remaining Events Prioritization (2026-09-04):**
> - **Accurate Timezone Conversion (EDT/UTC to Asia/Bangkok)**: Switched from ambiguous XML parsing to ForexFactory's official JSON feed (`ff_calendar_thisweek.json`) with ISO-8601 offsets (`2026-09-03T10:00:00-04:00`), and fixed XML fallback to use `TimeZone.UTC`. Corrected the previous 4-hour offset error that shifted events across midnight into the next day (e.g. ISM Services PMI is now properly displayed as Thursday 9:00 PM / 21:00 น. Thai time, matching the ForexFactory web interface).
> - **Smart Remaining Events Prioritization & Filtering**: Resolved the issue where High-impact events from earlier in the week crowded out upcoming events. Events are now dynamically partitioned into `[รอประกาศ / UPCOMING]` (sorted chronologically so the next event appears first) and `[ประกาศแล้ว / PASSED]`. When users ask for remaining events this week ("ที่เหลือของสัปดาห์นี้"), upcoming events like Friday Non-Farm Payrolls are prioritized.
> - **Thai Day-of-Week & Date Context in AI Analysis**: Formats day-of-week and dates in Thai (`วันพฤหัสบดี พฤ. 03 ก.ย. 21:00 น.`) and injects current Thailand date/time into the Gemini macro prompt, ensuring the AI never mistakes past events for upcoming ones. Verified with 100% passing test suite (`EconomicCalendarTimeTest.kt`).
>
> **Live Voice Greeting Stability & Thai Phonetics (2026-09-04):**
> - **Echo Loop & Double-Speech Elimination**: Implemented automatic microphone muting during local Android TTS fallback playback, cutting off the acoustic feedback loop that previously caused the AI to transcribe its own greeting speaker output and reply a second time.
> - **Natural Pure-Thai Greeting Trigger**: Eliminated synthetic `[SYSTEM]` meta-prompts with English tokens on `realtimeInput`, replacing them with natural conversational Thai triggers (`สวัสดี$agentName พร้อมคุยไหม`). This prevents cold-start language confusion, eliminates NO-AUDIO text-only fallback drops, and guarantees consistent native audio generation.
> - **Thai Phonetics & Articulation Guardrails**: Added strict pronunciation rules to `LIVE_RULES` preventing dipthong/glide distortion on final consonants (e.g. ensuring "เจ้านาย" is articulated crisply as แม่เกย without elongating into "เจ้านาว").
>
> **Dynamic Indicator Overlays (EMA, SMA, BB, DC, Any Period) & Chart Controller Flexibility (2026-09-04):**
> - **Flexible Indicator Periods**: Enabled dynamic overlay support for arbitrary periods (e.g. `ema8`, `ema9`, `ema21`, `ema89`, `sma50`, `sma200`, `dc55`, `bb_20_2`) on charts and chat mini-cards, lifting the previous hardcoded limitation that only permitted `ema14`, `ema20`, `ema50`, `ema60`, `ema200`.
> - **Universal Chart Normalization**: Integrated `TaIndicators.normalizeTimeframe` into `ChartController` so that MT5 intervals (`m1`, `m5`, `m15`, `h1`, `h4`, `d1`, `w1`) work seamlessly without defaulting to 1h.
> - **Dynamic Toolbar & LWC V5 Engine**: Updated `dashboard_engine.js` with dynamic period parsing, deterministic HSL color assignment, and automated custom overlay chip rendering in `TradingChartScreen.kt`. Verified with 100% passing tests (`ChartDynamicIndicatorTest.kt`).
>
> **Trading Backend Modularization & Architecture Decoupling (2026-09-04 — v1.1.0):**
> - **God-Class Decoupling**: Replaced the 2,060-line monolithic `TradingToolExecutionBackend.kt` with a clean, ~80-line coordinator that delegates directly to specialized domain handlers via `TradingToolRouter.domainOf(toolName)`.
> - **Extracted Domain Handlers**:
>   - [`Mt5ToolHandler.kt`](composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/Mt5ToolHandler.kt): MT5 broker operations, order/position tracking, MT5-native technical analysis, market scanner, correlation radar, and institutional flow.
>   - [`MarketTechnicalToolHandler.kt`](composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/MarketTechnicalToolHandler.kt): Real-time market snapshots, price lookups, technical indicators, multi-timeframe scans, macro calendars, and AI financial news synthesis.
>   - [`ResearchToolHandler.kt`](composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/ResearchToolHandler.kt): Strategy signal generation, backtest delegation, live signal statistics, position sizing, correlation matrix, FRED economic data, crypto overview, and deep analysis suite.
> - **100% Backward Compatibility & Zero Regression**: Preserved all existing tool contracts and verified with 100% passing test suites across both unit tests and domain routing tests (`:composeApp:testDebugUnitTest`).
>
> **Trading Tools, Indicator Accuracy & Universal Timeframe (2026-09-04):**
> - **Universal Timeframe Support (m1, m5, m15, h1, h4, D1, W1)**: Standardized timeframe normalization across all trading tools, engines, scanners, and bridges (`TaIndicators.normalizeTimeframe()`, `toMt5Timeframe()`, `toTvResolution()`). Fixed critical bug where standard MT5 format strings (`m1`, `m5`, `m15`, `h1`, `h4`, `d1`, `w1`) silently evaluated false against TradingView-only checks and fell back to 1-hour candles.
> - **Accurate Technical Indicators**: Upgraded indicator math in `TaIndicators` and `IndicatorAlertProvider` (Wilder's RMA ATR, MACD with EMA-9 Signal & Histogram, Slow Stochastic %K/%D 14-3-3, Bollinger Bands %b/width, ADX +DI/-DI, CCI-20, Supertrend). Lowered candle threshold to 35 for D1/W1 to guarantee reliable execution on higher timeframes.
> - **MT5 Bridge & Tools Synchronization**: Extended MT5 Python bridge with `1d`, `1w`, `d`, `w` aliases and updated parameter enums for `trading_technical_analysis`, `trading_mt5_broker_ta`, `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_institutional_flow`, and `trading_signal_alert`. Verified with 100% passing test suite (`TradingIndicatorTimeframeTest.kt`).
>
> **Gemini Provider & Live Model Hardening & Dynamic Listing (2026-09-04):**
> - **Live Model Filtering Fix**: Fixed critical bug in `SettingsController.getLiveCapableModels()` which filtered by `supportsVision` instead of `supportsLive`, eliminating chat models (`gemini-2.5-pro`, `gemini-1.5-pro`, etc.) from Live Voice dropdown that caused instant WebSocket connection failure.
> - **Future-Proof Tool Capability**: Updated `supportsNativeTools()` to automatically recognize all modern Gemini 1.5, 2.x, 3.x+ multimodal models and major open weights without version hardcoding.
> - **Live Model Synchronization**: Injected full preview suite (`gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.0-flash-exp`) across `GeminiLlmProvider`, `LiveGeminiService`, and `JarvisOrchestrator`.
> - **Standalone Provider Registration**: Completed `registerGemini()` and `createTempProvider()` in `LlmProviderRegistry` to handle dynamic model querying seamlessly.
>
> **Mobile Architecture Modularization & Clean Separation (2026-09-03):** 
> - **Trading Terminal Modularization**: Refactored monolithic `TradingTerminalScreen.kt` (reduced from 2,269 lines to 474 lines, 79% reduction) into clean dedicated components: `TerminalOverviewTab.kt`, `TerminalTradeTab.kt`, `TerminalHistoryTab.kt`, `TerminalEventsTab.kt`, `TerminalSettingsTab.kt`, `TerminalConnectionTab.kt`, `TerminalEditPositionDialog.kt`, and `TerminalFormatting.kt`.
> - **Backtest & Evolution Extraction**: Extracted ~950 lines of backtest execution, parameter optimization, genetic/evolution simulation, and mix signal engines from `TradingToolExecutionBackend.kt` into dedicated `BacktestToolHandler.kt` (reduced from 3,008 lines to 2,060 lines).
> - **Automated Test Coverage**: Added comprehensive test suites (`AutomationEvaluatorTest.kt`, `IntentClassifierTest.kt`, `SignalAlertProviderTest.kt`, expanded `TradingToolDomainRoutingTest.kt`) with 100% unit test pass rate across 24 test suites (`BUILD SUCCESSFUL`).
>
> **AI Strategy Supervisor + 5TF Market Context (2026-08-29):** AI ไม่ได้แค่อ่านสรุปสัญญาณอีกต่อไป — เป็น Supervisor ที่ตัดสิน APPROVE / VETO / ADJUST ก่อนแจ้งผู้ใช้ โดยอ่าน `MarketContextDigest` (โครงสร้างตลาด 5TF แบบ deterministic: trend/BOS/CHoCH, แนวรับ-ต้าน swing/EQH/EQL/FVG/OB, Premium-Discount, Volume Profile POC/VAH/VAL) แทนการเดาเอง. ADJUST ปรับ SL/TP ตามโครงสร้าง (ผ่าน validation: RR≥1, risk 0.2–6×ATR) ส่วน VETO ไม่แจ้งผู้ใช้แต่ shadow-record ไว้เทียบผล. เพิ่ม M1 confirmation layer (additive, นอก backtest V5) และ keyzone watch (`signal_keyzone` — แจ้งเมื่อราคาแตะจุดสำคัญ). รายละเอียด: `.obsidian-wiki/07_Trading_Intelligence/65_AI_Strategy_Supervisor.md`. Build: `assembleDebug` SUCCESS.
>
> **Unified SMC V5 ฝังใน app มือถือ (2026-08-27):** Unified SMC Multi-TF เป็น engine หลักของ signal alert แล้ว — Kotlin port ที่ `composeApp/.../automation/smc/UnifiedSmcSignals.kt` (H4/H1 context, M30/H1 trend, M15 setup, M5 confirmation; ประเมินเฉพาะ job 15m, edge-triggered, causal HTF). ตัด TR/DC/52H/E/UT/3BR ออกจาก live pipeline ตาม cross-TF forensics — เก็บ MOM+REV (`SignalMarkerProvider.ENABLED_KINDS`, re-enable จุดเดียว; forensics ย้อนหลังยังทำได้ผ่าน `kindsOverride`). สถานะ: research/forward-test (promotion_ready=false — เก็บ shadow P/L ผ่าน trackSignalOutcomes). รายละเอียด: `.obsidian-wiki/07_Trading_Intelligence/64_Unified_SMC_App_Integration.md`. Build: `compileDebugKotlinAndroid` SUCCESS.
>
> **TradingView Strategy Lab (2026-08-27):** Added `tools/tradingview_strategy_lab.py` as an independent research harness. It can pull historical TradingView OHLCV (or read TradingView CSV exports), run MOM/TR/REV/DC/52H/E/UT/3BR across strategy-specific parameter grids, and report Train/Validation/Holdout OOS, drawdown, expectancy, PF, long/short asymmetry and parameter-neighborhood stability. Research output is not promoted directly to production.
>
> **Unified SMC WATCH-mode plan + runner (2026-08-27):** Added `tools/unified_smc_watch.py` — emits one BUY/SELL/HOLD decision per closed M15 bar from the V5 basin config (polls fresh bars from the MT5 bridge, logs to `watch_log.jsonl`, optional POST to server). Integration plan for the `POST /api/mt5/auto/external-signal` endpoint (decision_feed/journal as PAPER, never touches order paths) and shadow-P/L resolver: `.obsidian-wiki/07_Trading_Intelligence/63_Unified_SMC_Watch_Mode_Plan.md`. Server wiring is pending a maintenance window.
>
> **Unified SMC V5 — robust basin found (2026-08-27):** Zoomed grid (1,152 candidates, sharded runs + feature cache) around the `trend_only` regime cluster found a robust basin: `adx_th=18, limit entry, idm on, tp_r 2–3, setup_win 3–4` — 13 candidates with stability ≥ 0.5 positive across all 3 phases. Basin rep passes 3/5 gates (holdout +0.49R PF 1.73, WFO 3/4 with the latest window strongest +0.99R, adverse-cost positive); it fails only on holdout sample size (16 < 30) and permutation significance — a structural limit of a selective engine on 17 months. Next step is a WATCH-mode forward test, not more grid search. New tool: `tools/unified_smc_gatecheck.py` runs the full gate suite on any candidate.
>
> **Unified SMC V4 — Regime Detector (2026-08-27):** Added Layer A regime gate (H1 ADX + structure trend + ATR volatility percentile; CHAOTIC never trades) and sparse-signal simulation. On 384 candidates: 75 positive across all 3 phases, and the entire top cluster is `regime=trend_only` with holdout +0.63..+0.77R / PF 2.1–2.4 — but samples are 11–15 trades with low neighborhood stability, so promotion_ready stays false. V5 should zoom the grid around the trend_only cluster to find a robust basin.
>
> **Unified SMC V3 — robust selection (2026-08-27):** Candidate selection now uses `0.6*train + 0.4*validation + 8*neighborhood_stability` with walk-forward, cost-stress, Monte Carlo and permutation gates on the chosen config. Result: the edge is real only in some regimes (WFO windows 1–2 strong +0.7R, windows 3–4 negative), adverse-cost positive, but `promotion_ready=false` — the next blocker is a Regime Detector (V4) so the engine only trades when market regime fits.
>
> **Unified SMC V2 (2026-08-27):** `tools/unified_smc_lab.py` upgraded to SMC-native execution (`simulate_v2`): structure-based SL (beyond confirmed swing + 0.25 ATR buffer), liquidity/R-multiple TP, FVG-midpoint limit entries (6-bar fill window), and London/NY session filter. On 17 months of MT5 data (33,340 M15 bars, 64 candidates), 15/64 candidates are positive across train/validation/holdout (best balanced: holdout +0.36R, PF 1.55), versus zero for V1 — but no permutation significance yet and promotion_ready remains false. V3 must add neighborhood stability + walk-forward to candidate selection.
>
> **MT5 OHLCV Exporter + Unified SMC real-data run (2026-08-27):** Added `mt5-core-server/scripts/export_ohlcv.py` — pages `copy_rates_from_pos` backwards so broker history (not the 5,000-bar bridge cap) lands in Strategy Lab CSV format (`strategy_lab_data_mt5/`). First full run of Unified SMC V1 on 17 months / 33,340 M15 bars: expectancy decays train +0.14R → validation +0.07R → holdout ≈ 0R (PF 0.96, permutation p = 0.92, promotion_ready=false). The earlier 1-month positive result is confirmed as noise; the gates prevented a false promotion. V2 directions recorded in the wiki.
>
> **Unified SMC MTF Strategy V1 (2026-08-27):** Added `tools/unified_smc_lab.py` — one signal engine combining EQH/EQL, swing structure, BOS/CHoCH(SMS), IDM, FVG and body/wick quality across the TF stack H4/H1 (context) → M30/H1 (trend) → M15 (setup) → M5 (confirmation/entry). A single score emits BUY/SELL/HOLD; context/trend alone can never trade and no confluence means HOLD. First run is positive in train/validation/holdout, but sample size is too small for significance (promotion_ready=false) until deeper M5/M1 history is exported from MT5.
>
> **Cross-TF Strategy Forensics (2026-08-27):** `tools/strategy_research_r1_r5.py` now supports `--csv`, resumable per-strategy part files (`--strategies`) and grid sharding (`--shard s/k`). Ran 8 strategies × 3 timeframes (XAUUSD M15/M5/H1, frozen CSVs in `strategy_lab_data/`). Result: no strategy has a positive holdout on all 3 TFs and no permutation p < 0.05 anywhere; `promotion_ready = false` everywhere. Conclusion: consolidate into one Unified Strategy Engine where strategies act as evidence components, not standalone signal generators. Details: `.obsidian-wiki/07_Trading_Intelligence/Strategy_Lab_TradingView.md`.
>
> **Strategy Optimization V2 (2026-08-26):** `trading_backtest_optimize` now uses joint Entry + strategy-native SL/TP optimization with Train/Validation/Holdout OOS and parameter-neighborhood stability analysis. It is discovery-only; production promotion remains behind Evolution/Risk Gate.
>
> **Live Alert Scheduler (2026-08-26):** Signal Alert voice delivery now uses a centralized priority scheduler. Audio remains serialized to prevent overlap, while queued M1/M5 signals are prioritized ahead of slower timeframe and scheduled announcements. Queue diagnostics are emitted as `Live Scheduler ENQUEUE/DISPATCH`.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**JARVIS** (PersonalAIBot) 喔勦阜喔福喔班笟喔氞笢喔灌箟喔娻箞喔о涪 AI 喔箞喔о笝喔氞父喔勦竸喔ム福喔班笖喔编笟喔腹喔?(Personal AI Assistant) 喔椸傅喙堗腑喔竵喙佮笟喔氞浮喔侧箑喔炧阜喙堗腑喙€喔涏箛喔權笚喔编箟喔囙箑喔炧阜喙堗腑喔權竸喔灌箞喔勦复喔斷箒喔ム赴喔權副喔佮抚喔脆箑喔勦福喔侧赴喔箤喔佮覆喔｀箑喔囙复喔權腑喔编笀喔夃福喔脆涪喔?喔傕副喔氞箑喔勦弗喔粪箞喔笝喔斷箟喔о涪 **Google Gemini 3 Series (Free-Tier Optimized)** + 喔｀赴喔氞笟 **Multi-Provider Fallback**, 喔勦抚喔侧浮喔堗赋 6 喔娻副喙夃笝 (GraphRAG & Obsidian Wiki), 喔｀赴喔氞笟喙€喔澿箟喔侧笗喔脆笖喔曕覆喔∴笗喔ム覆喔斷腑喔编笗喙傕笝喔∴副喔曕复 (Alert System V2) 喙佮弗喔班福喔班笟喔?**MT5 Full Agent Control** 喔赋喔福喔编笟喙€喔椸福喔斷箒喔氞笟喔勦福喔氞抚喔囙笀喔?
- **馃攲 Multi-Provider + Fallback 喔弗喔侧涪喔娻副喙夃笝** 鈥?Gemini (Multi API Key + Model Fallback Chain) 鈫?Groq 鈫?OpenRouter 鈫?MiniMax 喔弗喔编笟喔副喔曕箓喔權浮喔编笗喔脆箑喔∴阜喙堗腑喔曕复喔?limit 喔炧福喙夃腑喔?Auto-Test 喔勦副喔斷箑喔夃笧喔侧赴喙傕浮喙€喔斷弗喔椸傅喙堗箖喔娻箟 tool 喙勦笖喙夃笀喔｀复喔?- **馃敂 Alert System V2** 鈥?喔曕副喙夃竾喙€喔囙阜喙堗腑喔權箘喔傕箑喔澿箟喔侧福喔侧竸喔?喔复喔權笖喔脆箑喔勦箑喔曕腑喔｀箤/SMC/**喔副喔嵿笉喔侧笓喔佮弗喔⑧父喔椸笜喙?(Signal Alert)** 喙勦笖喙夃笀喔｀复喔?喙佮笀喙夃竾喙€喔曕阜喔笝喔炧福喙夃腑喔∴笡喔膏箞喔?"馃棏 喔ム笟 / 馃攣 喔嬥箟喔? + 喔佮覆喔｀箤喔?3D 喙冟笝喙佮笂喔?+ 喙€喔傅喔⑧竾喔炧腹喔斷箒喔堗箟喔囙箑喔曕阜喔笝喙€喔ム阜喔竵 engine 喙勦笖喙?(AI Live / 喙€喔勦福喔粪箞喔竾) 喙佮弗喔?Adaptive Interval 喙€喔｀箞喔囙箑喔娻箛喔勦箑喔∴阜喙堗腑喔｀覆喔勦覆喙冟竵喔ム箟喙€喔涏箟喔?- **馃帣锔?Live Voice + Vision** 鈥?喔勦父喔⑧釜喔斷竵喔编笟 Gemini Live, 喙€喔ム阜喔竵喙€喔傅喔⑧竾喙勦笖喙?30 喙傕笡喔｀箘喔熰弗喙?(喔溹腹喔佮笗喔编抚喔曕笝/喔勦赋喔ム竾喔椸箟喔侧涪喔副喔曕箓喔權浮喔编笗喔?, 喙€喔涏复喔?"喔曕覆" 喙冟斧喙?AI 喔∴腑喔囙笢喙堗覆喔權竵喔ム箟喔竾喔炧福喙夃腑喔?AR Overlay
- **馃摬 Mobile Android App (Compose Multiplatform)** 鈥?喔斷傅喙勦笅喔權箤喔炧福喔掂箑喔∴傅喔⑧浮, 喙佮笝喔氞箘喔熰弗喙?喔｀腹喔?PDF 喙冟笝喙佮笂喔椸箖喔箟 AI 喔о复喙€喔勦福喔侧赴喔箤, 喔福喙夃覆喔囙箘喔熰弗喙?Excel 喔堗福喔脆竾, Symbol Catalogue, Decision Feed, Auto Trading Controls 喙佮弗喔?**Setup Checklist** 6 喔傕箟喔箖喔?Settings (喙佮笀喙夃竾喙€喔曕阜喔笝/喙勦浮喔勦箤/喔佮弗喙夃腑喔?Overlay/All-files/Battery 鈥?喔涏父喙堗浮喔炧覆喙勦笡喔笝喙夃覆喔曕副喙夃竾喔勦箞喔侧福喔班笟喔氞笗喔｀竾喔堗父喔?

---

## 馃専 喔｀赴喔氞笟喔弗喔编竵 (Core Systems)

### 馃帣锔?1. Live Voice & Voice Profile 鈫?Identity

- **Gemini Live API** 鈥?喔笝喔椸笝喔侧釜喔斷笖喙夃抚喔⑧箑喔傅喔⑧竾 (PCM 16kHz) latency 喔曕箞喔?喔｀腑喔囙福喔编笟喔佮覆喔｀競喔编笖喔堗副喔囙斧喔о赴 (barge-in) 喙佮弗喔?flush 喔勦复喔о箑喔傅喔⑧竾喔副喔曕箓喔權浮喔编笗喔?- **30 Voice Profiles** 鈥?喙€喔傅喔⑧竾喔笉喔脆竾/喔娻覆喔?喔弗喔侧涪喔權箟喔赤箑喔傅喔⑧竾喙佮弗喔班腑喔侧福喔∴笓喙?喙€喔ム阜喔竵喙勦笖喙夃笀喔侧竵 Settings 喔福喔粪腑喔副喙堗竾喔斷箟喔о涪喙€喔傅喔⑧竾 ("喙€喔涏弗喔掂箞喔⑧笝喙€喔傅喔⑧竾喙€喔涏箛喔?Leda") 鈥?喙€喔涏弗喔掂箞喔⑧笝喙佮弗喙夃抚 AI 喔椸副喔佮笚喔侧涪喔⑧阜喔權涪喔编笝喔斷箟喔о涪喙€喔傅喔⑧竾喙冟斧喔∴箞喔椸副喔權笚喔?- **喙€喔傅喔⑧竾喔溹腹喔佮竵喔编笟喔曕副喔о笗喔?* 鈥?喙€喔傅喔⑧竾喔笉喔脆竾喔炧腹喔斷弗喔囙笚喙夃覆喔?"喔勦箞喔? / 喙€喔傅喔⑧竾喔娻覆喔?"喔勦福喔编笟" 喔副喔曕箓喔權浮喔编笗喔?喔堗赋喔傕箟喔侧浮 session 喔溹箞喔侧笝 Core Memory
- **Vision (喔曕覆喔傕腑喔?JARVIS)** 鈥?喙€喔涏复喔斷竵喔ム箟喔竾喙冟斧喙?AI 喔∴腑喔囙箓喔ム竵喔堗福喔脆竾 喔炧腹喔斷釜喔｀父喔涏釜喔脆箞喔囙笚喔掂箞喙€喔箛喔權笚喔编笝喔椸傅喙傕笖喔⑧箘喔∴箞喔曕箟喔竾喔栢覆喔∴笅喙夃赋, OCR 喔箞喔侧笝喔傕箟喔竸喔о覆喔? Object Detection (AR Overlay), 喔弗喔编笟 vision provider 喔曕覆喔∴箓喔∴箑喔斷弗喔弗喔编竵喔副喔曕箓喔權浮喔编笗喔?- **Live Tool Bridge** 鈥?喙傕浮喙€喔斷弗 Live 喔副喙堗竾 tool 喔溹箞喔侧笝 chat model 喔傅喔佮笂喔编箟喔?(Live API 喙勦浮喙堗箑喔｀傅喔⑧竵 tool 喙€喔竾) 喔椸赋喙冟斧喙夃箑喔傅喔⑧竾喙勦浮喙堗笘喔灌竵喔佮福喔班笚喔氞箑喔∴阜喙堗腑喔弗喔编笟 chat provider

### 馃 2. Advanced 6-Layer Memory Engine

- **Layer 1: Core Memory** 鈥?喔堗赋喔傕箟喔浮喔灌弗喔曕副喔о笗喔權笢喔灌箟喙冟笂喙?AI (identity 喔堗副喔斷竵喔侧福喔溹箞喔侧笝 tool/Settings 喙€喔椸箞喔侧笝喔编箟喔?喔佮副喔?heuristic 喔椸副喔?
- **Layer 2: Working Memory** 鈥?喔氞副喔權笚喔多竵喔涏福喔班抚喔编笗喔脆竵喔侧福喔勦父喔⑧弗喔?SQLite 喔椸副喔權笚喔?(Context Tracking)
- **Layer 3: Archival Memory** 鈥?Semantic Search / Vector Embeddings (Local ONNX 喔福喔粪腑 Gemini Cloud 鈥?auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** 鈥?喙傕竸喔｀竾喔傕箞喔侧涪喔勦抚喔侧浮喔副喔∴笧喔编笝喔樴箤喙佮笝喔о竸喔脆笖 + retrieval 喔堗福喔脆竾喔溹箞喔侧笝 `recall_memory`
- **Layer 5: Memory Consolidation** 鈥?"Sleep Cycle" 喔福喔膏笡喙佮弗喔班涪喙夃覆喔⑧竸喔о覆喔∴笀喔赤福喔班涪喔班釜喔编箟喔權箘喔涏福喔班涪喔班涪喔侧抚 (auto-trigger 喙€喔∴阜喙堗腑喙佮笂喔椸釜喔班釜喔?200 喔傕箟喔竸喔о覆喔?
- **Layer 6: LLM-Wiki (Obsidian)** 鈥?喔｀赴喔氞笟 "喔浮喔竾喔箞喔о笝喔權腑喔? 喔椸傅喙?AI 喙佮弗喔班浮喔權父喔┼涪喙屶笀喔编笖喔佮覆喔｀福喙堗抚喔∴竵喔编笝喔溹箞喔侧笝 Markdown

### 馃搳 3. Trading Intelligence 喔氞笝喔∴阜喔笘喔粪腑 (TV-Powered)

- **Indicator Alert Provider** 鈥?喔勦赋喔權抚喔?EMA20/50/200, EMA Cross (Golden/Death), RSI14, MACD, Stoch, CCI, Bollinger Bands, ATR 喙€喔竾喔堗覆喔佮箒喔椸箞喔囙箑喔椸傅喔⑧笝 cache (incremental fetch 喙勦浮喙堗笖喔多竾喙冟斧喔∴箞喔椸副喙夃竾喔娻父喔? 喙佮浮喙堗笝喔佮抚喙堗覆 TradingView scanner
- **Strategy Signal Provider** 鈥?5 喔佮弗喔⑧父喔椸笜喙屶笀喔侧竵喔勦弗喔编竾 Quantpedia 喔勦赋喔權抚喔撪箖喔權箑喔勦福喔粪箞喔竾喔堗覆喔?OHLCV 喔ム箟喔о笝 (TSMOM, Trend EMA50/200, Reversal RSI+BB, Donchian Breakout, 52-Week High) + consensus score
- **馃幆 Signal Alert Provider** 鈥?喔曕福喔о笀 edge 喔副喔嵿笉喔侧笓喔嬥阜喙夃腑/喔傕覆喔⑧箖喔浮喙堗箑喔夃笧喔侧赴喙佮笚喙堗竾喔涏复喔斷弗喙堗覆喔父喔斷笀喔侧竵 **8 喔佮弗喔⑧父喔椸笜喙?* (5 喔傕箟喔侧竾喔曕箟喔?+ EMA Cross, UT Bot, 3-Bar Reversal) 喔炧福喙夃腑喔?payload 喔勦福喔? Entry/SL/TP/RR 喙€喔夃笧喔侧赴喔佮弗喔⑧父喔椸笜喙?(喔勦赋喔權抚喔撪笀喔侧竵 ATR) + 喙€喔笗喔膏笢喔ム笭喔侧俯喔侧箘喔椸涪 + context snapshot 鈥?baseline 喔佮副喔權釜喔编笉喔嵿覆喔撪箑喔佮箞喔侧箑喔斷箟喔囙笗喔笝喔福喙夃覆喔?alert 喔副喔曕箓喔權浮喔编笗喔?喙佮弗喔班笟喔编笝喔椸付喔佮笚喔膏竵喔副喔嵿笉喔侧笓喔ム竾 DB 喙€喔炧阜喙堗腑喔曕复喔斷笗喔侧浮喔溹弗 TP/SL 喔堗福喔脆竾 (win-rate/avgR)
- **SMC Alert Provider** 鈥?喙佮笡喔ム竾 SMC analysis 喙€喔涏箛喔?field 喙€喔澿箟喔侧笗喔脆笖喔曕覆喔∴箘喔斷箟 18 喔曕副喔? zone (Premium/Discount), BOS/CHoCH, Order Blocks, FVG, Liquidity zones + 喔斷覆喔?- **Deep Analysis Suite 喔勦福喔?5 喔∴复喔曕复** 鈥?LSD state + confluence, Orderflow Delta, Fibo Score, Momentum, Squeeze (9 fields) 喔炧福喙夃腑喔?TV local fallback + circuit breaker 喙€喔∴阜喙堗腑 bridge 喔ム箞喔?- **Multi-Timeframe 喔椸父喔?tool** 鈥?喔｀赴喔氞父 TF 喙勦笖喙夃笖喙夃抚喔?suffix `symbol@TF` 喙€喔娻箞喔?`XAUUSD@15m` (1m/5m/15m/30m/1h/4h/1D)
- **Chart Dashboard (Lightweight Charts v5.1, offline)** 鈥?喔佮福喔侧笩 multi-pane 喙冟笝喔曕副喔о箒喔笡: layouts (single / RSI / MACD / RSI+MACD / Volume / Full), overlays EMA20/50/200 + Bollinger Bands + Donchian (DC20) + **Signal Markers (SIG)** 喔堗父喔斷釜喔编笉喔嵿覆喔撪涪喙夃腑喔權斧喔ム副喔?8 喔娻笝喔脆笖喔氞笝喔佮福喔侧笩, 喔о覆喔?SMC zones (OB/FVG), 喔傕箟喔浮喔灌弗喙佮笚喙堗竾喙€喔椸傅喔⑧笝喔堗覆喔?TV incremental cache (market-closed backoff 喔佮副喔權笖喔多竾喔嬥箟喔赤笗喔笝喔曕弗喔侧笖喔涏复喔?, 喔弗喔编笟喙勦笡 TradingView widget 喙勦笖喙夃笚喔膏竵喙€喔∴阜喙堗腑 鈥?**AI 喔涏福喔编笟 layout/indicator 喙€喔竾喔溹箞喔侧笝 tool `chart_dashboard_control`** 喔椸副喙夃竾喙佮笂喔椸箒喔ム赴 Live ("喙€喔涏复喔斷竵喔｀覆喔熰笚喔竾喔勦赋 1 喔娻副喙堗抚喙傕浮喔?喙€喔炧复喙堗浮 RSI 喔佮副喔?MACD")
- **Rich Chat Rendering** 鈥?喔曕覆喔｀覆喔?markdown 喙佮釜喔斷竾喙€喔涏箛喔權笗喔侧福喔侧竾喔堗福喔脆竾 (header 喔傅 + scroll 喙佮笝喔о笝喔笝) 喙佮弗喔?AI 喙佮笝喔?**chart card** 喙冟笝喙佮笂喔椸箘喔斷箟 (```chart fence) 喙佮笗喔班竵喔侧福喙屶笖喙€喔炧阜喙堗腑喙€喔涏复喔斷竵喔｀覆喔熰箑喔曕箛喔∴笀喔笖喙夃抚喔?config 喔權副喙夃笝喔椸副喔權笚喔?
### 馃敂 4. Alert System V2 (喔｀赴喔氞笟喙€喔澿箟喔侧笗喔脆笖喔曕覆喔∴笗喔ム覆喔?

- **馃幆 Signal Alert (喙冟斧喔∴箞)** 鈥?job type `trading_signal_alert` 喙€喔澿箟喔侧釜喔编笉喔嵿覆喔撪笅喔粪箟喔?喔傕覆喔⑧笀喔侧竵 8 喔佮弗喔⑧父喔椸笜喙?edge-triggered; payload 喔勦福喔?Entry/SL/TP/RR/ATR + 喙€喔笗喔膏笢喔ム箘喔椸涪; **re-arm 喔副喔曕箓喔權浮喔编笗喔?*喔椸父喔佮箒喔椸箞喔囙箖喔浮喙?(喙勦浮喙堗笗喙夃腑喔囙竵喔斷笅喙夃赋) 喙佮弗喔班釜喔｀箟喔侧竾喔溹箞喔侧笝喙€喔傅喔⑧竾/喙佮笂喔?UI 喙勦笖喙?(AI 喙佮笡喔ム竾 `signal contains BUY` 喙冟斧喙夃箑喔涏箛喔?signal alert 喔副喔曕箓喔權浮喔编笗喔?喔椸赋喔囙覆喔權笗喔｀竾喔佮副喔權笚喔膏竵喙€喔勦福喔粪箞喔竾)
- **Alert Lifecycle 喔娻副喔斷箑喔堗笝** 鈥?alert 喔椸副喙堗抚喙勦笡 (喔｀覆喔勦覆/indicator) 喔椸傅喙?TRIGGERED 喙佮弗喙夃抚 **喔腑喔佮笀喔侧竵 job loop 喔椸副喔權笚喔?* 喙勦浮喙堗抚喔權箑喔娻箛喔佮箑喔涏弗喔粪腑喔囙笚喔｀副喔炧涪喔侧竵喔? re-arm 喔椸覆喔囙箑喔斷傅喔⑧抚喔勦阜喔笡喔膏箞喔?**馃攣 喙佮笀喙夃竾喙€喔曕阜喔笝喔嬥箟喔?* (喔氞笝 notification 喙佮弗喔班箖喔權斧喔權箟喔?Cron Jobs) 喔福喔粪腑 **馃棏 喔ム笟喙佮笀喙夃竾喙€喔曕阜喔笝** (喔ム笟喔腑喔佮笀喔侧竵 list 喔堗福喔脆竾 喔涏福喔班抚喔编笗喔脆涪喔编竾喙€喔佮箛喔?
- **2 喙傕斧喔∴笖喔箞喔?(delivery)** 鈥?`ai`: AI quick-check 喔佮箞喔笝喙佮笀喙夃竾 (fallback chain 喔弗喔侧涪喙傕浮喙€喔斷弗) | `direct`: 喙佮笀喙夃竾喔曕福喔囙笚喔编笝喔椸傅喙勦浮喙堗箑喔｀傅喔⑧竵 AI 鈥?喔椸副喙夃竾喔腑喔囙箓喔浮喔斷釜喙堗竾喙€喔傕箟喔侧箒喔娻笚喔炧福喙夃腑喔?notification
- **喔佮覆喔｀箤喔?3D 喙冟笝喙佮笂喔?* 鈥?喔椸父喔?alert 喙佮釜喔斷竾喙€喔涏箛喔權竵喔侧福喙屶笖喔堗副喔斷福喔班箑喔氞傅喔⑧笟 (喔涏箟喔侧涪 馃煝BUY/馃敶SELL + gradient/shadow + 喔曕覆喔｀覆喔?Entry/TP/SL/RR/ATR 喙€喔夃笧喔侧赴 field 喔椸傅喙堗笀喔赤箑喔涏箛喔? 喔炧福喙夃腑喔?footer 喔氞腑喔?engine 喙€喔傅喔⑧竾喔椸傅喙堗箖喔娻箟喔堗福喔脆竾; 喙€喔佮箞喔侧箑喔涏箛喔?markdown table 喔⑧副喔囙箑喔涏复喔斷笖喔灌箘喔斷箟 (backward compatible)
- **馃攰 喙€喔傅喔⑧竾喙佮笀喙夃竾喙€喔曕阜喔笝喙€喔ム阜喔竵 engine 喙勦笖喙?* 鈥?`鈿?喙€喔勦福喔粪箞喔竾` (Android TTS 喔椸副喔權笚喔?喙勦浮喙堗笀喔赤竵喔编笖 鈥?default) | `鉁?AI Live` (streaming 喔溹箞喔侧笝 Live API 喙勦笖喙夃涪喔脆笝喔曕副喙夃竾喙佮笗喙?chunk 喙佮福喔? 鈥?chain 喙€喔｀复喙堗浮喔堗覆喔?**喙傕浮喙€喔斷弗 Live 喔椸傅喙堗箑喔ム阜喔竵喙冟笝 Settings** 喙€喔浮喔?鈫?fallback 喙傕浮喙€喔斷弗 Live 喔傅喔佮笗喔编抚 鈫?Android TTS; 喙冟笂喙?voice profile + identity 喙€喔斷傅喔⑧抚喔佮副喔?Live mode
- **Actionable Notifications** 鈥?喔涏父喙堗浮 "馃棏 喔ム笟喙佮笀喙夃竾喙€喔曕阜喔笝" / "馃攣 喙佮笀喙夃竾喙€喔曕阜喔笝喔嬥箟喔? (manifest receiver 喔椸赋喔囙覆喔權箘喔斷箟喙佮浮喙夃箒喔笡喔栢腹喔佮竼喙堗覆) + 喔笘喔侧笝喔班笂喔编笖喙€喔堗笝 "ACTIVE 路 喙€喔澿箟喔侧笖喔灌腑喔⑧腹喙? / "TRIGGERED 路 喔｀腑喙€喔ム阜喔竵 喔ム笟/喔嬥箟喔?
- **Adaptive Interval** 鈥?tick 喔弗喔编竵 30 喔о复喔權覆喔椸傅 + 喙€喔｀箞喔囙箑喔娻箛喔勦腑喔编笗喙傕笝喔∴副喔曕复喙€喔∴阜喙堗腑喔｀覆喔勦覆喙冟竵喔ム箟喙€喔涏箟喔?(<0.1% 鈫?30 喔о复, <0.5% 鈫?1 喔權覆喔椸傅, 喙勦竵喔?鈫?喔曕覆喔∴笚喔掂箞喔曕副喙夃竾) 喔｀腑喔囙福喔编笟喔椸腑喔囙竸喔赤笚喔掂箞喔о复喙堗竾喙佮福喔?- **AlertFieldCatalog** 鈥?dropdown 喔曕腑喔權釜喔｀箟喔侧竾 alert 喙€喔ム阜喔竵喙勦笖喙夃箑喔夃笧喔侧赴 tool/field 喔椸傅喙堗笖喔多竾喔勦箞喔侧箘喔斷箟喔堗福喔脆竾 + validate operator (field 喔傕箟喔竸喔о覆喔∴笟喔编竾喔勦副喔?`==`/`contains`) 喔佮副喔權笗喔编箟喔囙箑喔囙阜喙堗腑喔權箘喔傕浮喔编箞喔?喔椸副喙夃竾喔澿副喙堗竾 UI 喙佮弗喔班笣喔编箞喔?AI
- **14+ Preset 喔ム副喔?* 鈥?喔｀覆喔勦覆喔栢付喔囙箑喔涏箟喔? RSI Overbought/Oversold, Golden/Death Cross, Discount/Premium Zone, Bollinger Squeeze, ADX 喙佮福喔? Extreme Fear, High Confluence, LSD 喔傕覆喔傕付喙夃笝, Squeeze Breakout, 馃摗 Signal BUY/SELL, Consensus STRONG_BUY/SELL, Donchian Breakout 喔弗喔?- **馃И Auto-Test** 鈥?喔涏父喙堗浮喙€喔斷傅喔⑧抚喙勦弗喙堗笚喔斷釜喔笟喔斷付喔囙競喙夃腑喔∴腹喔ム笚喔膏竵 tool 喔椸傅喙?alert 喙冟笂喙?喙佮釜喔斷竾喔笘喔侧笝喔班釜喔斷箖喔權箒喔笡
- **喙佮竵喙夃箘喔傕竸喔｀笟喔椸父喔佮笂喙堗腑喔?* 鈥?喔娻阜喙堗腑, 喔勦箞喔侧箑喔涏箟喔侧斧喔∴覆喔? 喔勦抚喔侧浮喔栢傅喙? 喙傕斧喔∴笖喔箞喔?喔溹箞喔侧笝 UI 喔福喔粪腑喔副喙堗竾 AI (`automation_manage_alerts`: create/update/rename/delete/list)
- **Scheduled Tasks** 鈥?`automation_manage_schedule` 喔囙覆喔權笗喔侧浮喙€喔о弗喔?(one-time/daily) 喔栢付喔囙箑喔о弗喔侧笡喔ム父喔?AI 喔∴覆喔椸赋喔曕覆喔?prompt 喙佮弗喙夃抚喔箞喔囙笢喔ム箑喔傕箟喔侧箒喔娻笚喔斷箟喔о涪
- **馃攳 Pipeline Trace Log** 鈥?log 喔ム赴喙€喔傅喔⑧笖喔椸父喔佮競喔编箟喔權笗喔笝喙佮笀喙夃竾喙€喔曕阜喔笝 (馃敂 FIRE config 鈫?馃 AI summary 喔曕箞喔箓喔∴箑喔斷弗 鈫?馃攰 voice engine/fallback 鈫?馃挰 push 喙佮笂喔? debug 喔堗覆喔?logcat 喙勦笖喙夃笀喔氞箖喔權笚喔掂箞喙€喔斷傅喔⑧抚

### 馃攲 5. Provider System 鈥?Multi-Key, Fallback & Auto-Test

- **Providers 喔涏副喔堗笀喔膏笟喔编笝**: Gemini / OpenRouter / Groq / MiniMax (喔曕副喔?OpenAI, Claude, Vertex, LiteLLM, NVIDIA NIM 喔腑喔?鈥?喙勦浮喙堗浮喔?free tier 喔椸傅喙堗箖喔娻箟喔囙覆喔?tool 喙勦笖喙夃笀喔｀复喔?
- **Gemini Multi API Key** 鈥?喙€喔炧复喙堗浮/喙佮竵喙?喔ム笟 key 喙勦笖喙夃斧喔ム覆喔⑧腑喔编笝 (Free tier 喔弗喔侧涪喙€喔∴弗喙? rotation 喔副喔曕箓喔權浮喔编笗喔脆箑喔∴阜喙堗腑 429/503 喔炧福喙夃腑喔?UI 喔堗副喔斷竵喔侧福喙佮笟喔?mask 喔副喔?喔椸箟喔侧涪
- **Model Fallback Chain** 鈥?喙€喔｀傅喔⑧竾喔曕覆喔∴箓喔勦抚喔曕箟喔?free tier 喔堗福喔脆竾 喔涏福喔编笟喔ム赋喔斷副喔氞箑喔竾喙勦笖喙夃箖喔?Settings; key 喔浮喔斷竵喙堗腑喔權竸喙堗腑喔⑧釜喔ム副喔氞箓喔∴箑喔斷弗
- **Cross-Provider Fallback** 鈥?Gemini 喔曕覆喔⑧笚喔编箟喔?chain 鈫?Groq 鈫?OpenRouter (喙€喔ム阜喔竵喙€喔夃笧喔侧赴喙傕浮喙€喔斷弗喔椸傅喙?auto-test 喔溹箞喔侧笝) 鈥?喔溹弗喙€喔椸釜喔堗福喔脆竾: Groq 喔溹箞喔侧笝 3/8, OpenRouter free 喔溹箞喔侧笝 6/17
- **Model Auto-Tester** 鈥?喙€喔椸釜 chat + tool calling 喔椸父喔佮箓喔∴箑喔斷弗 喔氞副喔權笚喔多竵喔溹弗 (`model_caps_v1`) 喔嬥箞喔笝喙傕浮喙€喔斷弗喔椸傅喙堗箖喔娻箟喙勦浮喙堗箘喔斷箟喔副喔曕箓喔權浮喔编笗喔?- **Hardening** 鈥?tool-loop detection (喔佮副喔?model 喙€喔｀傅喔⑧竵 tool 喔嬥箟喔赤箘喔∴箞喔堗笟), 429 backoff 喔曕覆喔∴箑喔о弗喔侧笚喔掂箞 API 喔氞腑喔? empty-round retry, timeout 120s, trading-context 喔氞副喔囙竸喔编笟 sort trading tools 喔傕付喙夃笝喔佮箞喔笝喙佮弗喔班笅喙堗腑喔?search_web, sanitize/mask API key 喙冟笝 log

### 馃 6. Custom Tools 鈥?AI 喔福喙夃覆喔?喙佮竵喙?喔ム笟喙€喔勦福喔粪箞喔竾喔∴阜喔笗喔编抚喙€喔竾

- **CRUD 喔勦福喔氞抚喔囙笀喔｀笢喙堗覆喔?AI** 鈥?`system_create_agent_tool` (喔福喙夃覆喔?喙佮竵喙夃箘喔傕箑喔傕傅喔⑧笝喔椸副喔?, `system_list_agent_tools` (喔斷腹喔｀覆喔⑧竵喔侧福+logic), `system_delete_agent_tool`
- **Persist 喔傕箟喔侧浮 session** 鈥?喔氞副喔權笚喔多竵喔ム竾 `custom_agent_tools/*.json` 喙傕斧喔ム笖喔佮弗喔编笟喔副喔曕箓喔權浮喔编笗喔脆笗喔笝喙€喔涏复喔斷箒喔笡 喙冟笂喙夃箘喔斷箟喔椸副喙夃竾喙傕斧喔∴笖 chat 喙佮弗喔?live
- **喔涏弗喔笖喔犩副喔?* 鈥?sanitize 喔娻阜喙堗腑, JSON 喔溹箞喔侧笝 kotlinx.serialization, register 喙€喔傕箟喔?registry 喔椸副喔權笚喔掂箓喔斷涪喙勦浮喙堗笂喔?declaration 喙€喔斷复喔?
### 馃搧 7. File Tools & Attachments

- **喙佮笝喔氞箘喔熰弗喙屶箖喔權箒喔娻笚** 鈥?喔涏父喙堗浮 馃搸 喙佮笝喔氞箘喔斷箟喔腹喔囙釜喔膏笖 5 喙勦笩喔ム箤 (喔｀腹喔?PDF/DOCX 喔腹喔囙釜喔膏笖 15MB 喔箞喔?inline 喙冟斧喙?Gemini 喔о复喙€喔勦福喔侧赴喔箤 native; 喙勦笩喔ム箤 text 喔澿副喔囙箑喔傕箟喔?prompt)
- **file_write 喔｀腑喔囙福喔编笟 .xlsx 喔堗福喔脆竾** 鈥?喙€喔傕傅喔⑧笝 Excel 喔斷箟喔о涪 zip+XML 喙冟笝喔曕副喔?(喙勦浮喙堗笧喔多箞喔?POI) 鈥?喙€喔娻箞喔權腑喙堗覆喔權釜喔ム复喔涏斧喔ム覆喔⑧箖喔?鈫?喔福喙夃覆喔囙笗喔侧福喔侧竾喔｀覆喔⑧福喔编笟-喔｀覆喔⑧笀喙堗覆喔⑧弗喔?Download
- **verify + media scan** 鈥?喔曕福喔о笀喔弗喔编竾喙€喔傕傅喔⑧笝喔о箞喔侧箘喔熰弗喙屶腑喔⑧腹喙堗笀喔｀复喔?+ MediaScanner 喙冟斧喙?file manager 喙€喔箛喔權笚喔编笝喔椸傅
- **Security** 鈥?path guard 喔椸父喔?op (read/write/delete/move/analyze), canonicalize 喔佮副喔?traversal, extension allowlist, 喔堗赋喔佮副喔?read 200K 喔曕副喔о腑喔编竵喔┼福, 喔箟喔侧浮喔ム笟喙傕笩喔ム箑喔斷腑喔｀箤喔覆喔樴覆喔｀笓喔班笚喔编箟喔囙竵喙夃腑喔?
### 馃彞 8. JARVIS Diagnostic Engine (Self-Healing)

- **Autonomous Health Verification** 鈥?喔曕福喔о笀喔腑喔氞笗喔權箑喔竾喔副喔曕箓喔權浮喔编笗喔?(API Connectivity, Data Accuracy, Database Integrity 喔堗福喔脆竾 喙勦浮喙堗箖喔娻箞 placeholder)
- **Price Source Sync** 鈥?喙€喔涏福喔掂涪喔氞箑喔椸傅喔⑧笟喔｀覆喔勦覆喔堗覆喔佮斧喔ム覆喔⑧箒喔弗喙堗竾 (Yahoo, OANDA, TV) 喔｀赴喔氞父喔勦抚喔侧浮喙€喔弗喔粪箞喔浮喙佮弗喔?Delay
- **Obsidian Wiki Reporting** 鈥?喔福喔膏笡喔溹弗喔佮覆喔｀笗喔｀抚喔堗釜喔笟喔｀赴喔氞笟喙€喔涏箛喔權箘喔熰弗喙?Markdown 喔副喔曕箓喔權浮喔编笗喔?- **Log Hygiene** 鈥?mask API key 喔副喔?喔椸箟喔侧涪喙冟笝 logcat, sanitize key 喔腑喔佮笀喔侧竵喔傕箟喔竸喔о覆喔?error, log preview 喔勦赋喔曕腑喔?AI + 喔溹弗 tool 喔赋喔福喔编笟 debug

---

## 馃搳 9. mt5-core-server 鈥?Trading Intelligence Engine (Node.js)

> **Status: Production = V26.26 (Modular Codebase & Verification Pass 2026-05-23)**
> **Runtime 喔涏副喔堗笀喔膏笟喔编笝: `engineMode: M15_WALL_SCALPING` 鈥?decision path 喔ム笖喔｀腹喔涏箑喔弗喔粪腑 IndicatorPipeline + PriceMap wall detection**

- **M15 Wall Scalping Simplification (2026-05-30 鈫?06-02)** 鈥?喔佮弗喔⑧父喔椸笜喙屶箑喔｀复喙堗浮喔曕箟喔權箑喔夃笧喔侧赴 `SCALPING`; wall registry 喔勦竾 identity 喔傕箟喔侧浮 cycle; M15/M30/H1/H4 喙€喔涏箛喔?anchor, M1/M5 喙€喔涏箛喔?confirmation (喔曕箟喔竾 touch/reclaim + directional body + micro-structure break); 喔涪喔膏笖喙€喔傕箟喔侧箑喔∴阜喙堗腑喔栢付喔?daily/consecutive loss limit; sync closed history 喔佮弗喔编笟喙€喔傕箟喔?journal 喔副喔曕箓喔權浮喔编笗喔?(`SimpleScalpingEngine.ts`)
- **V26.26 Modular Codebase (2026-05-23)** 鈥?喙佮涪喔?helpers 喔堗覆喔?`autoTradingService.ts` (~6,124 喔氞福喔｀笚喔编笖) 喙€喔涏箛喔?`decisionLogger.ts` + `strategySelector.ts` 鈥?喔溹箞喔侧笝 Unit Tests 103/103
- **V26.24鈥?6.25 Runtime Controls** 鈥?喙€喔涏复喔?喔涏复喔?gate 喙佮弗喔?strategy 喔｀覆喔⑧笗喔编抚喔溹箞喔侧笝 `adaptive.gateToggles`/`strategyToggles` 喔氞笝 UI; Unified Zone Gate + Execution Quality Gate; Regime-Adaptive Scoring; SL Proportional Scaling on TP Cap
- **V26.18鈥?6.23 Execution Hardening** 鈥?MTF weighting 喙冟斧喔∴箞 (H4 22/H1 23/M30 15/M15 18/M5 15/M1 7), Breakout 喔曕箟喔竾喔∴傅 M15/M5 confirm, wall-break scalp playbook, fresh decision price sync, EA-only confidence 55%, post-TP cooldown
- **V25 Real-Time Wall Engine** 鈥?7-layer event-driven + Wall State Machine (IDLE鈫扐PPROACH鈫扲EACT鈫扖ONFIRM鈫扖OMMITTED) + 5 playbooks (PB1 Wall-Touch Scalp default) 鈥?喔佮笌喙€喔弗喙囙竵 "no wall, no trade" + 7 coordination patches (V25.1)
- **Context Levels (V24.2)** 鈥?Daily Pivot Points, Session VWAP (NY open anchored), Auto-Fibonacci, Confluence Boost (+1鈽? 鈥?coverage ~85% 喔傕腑喔?indicator 喔椸傅喙?pro retail 喙冟笂喙?- **Analytics Scripts** 鈥?`npm run analyze [YYYY-MM-DD]` (Bangkok-day), `advanced_analytics.mjs`, `analytics:gate`, `analytics:audit`, `analyze:logs`, `analyze:deep` (喔溹箞喔侧笝喔佮覆喔｀笚喔斷釜喔笟喔堗福喔脆竾: 15,796 cycles, top reject reasons)
- **TradingView Pine V8.5** 鈥?SMC & Multi-TF Order Blocks Sweeps 喔赋喔福喔编笟喔斷腹 Wall Map 喔氞笝 M15 喔炧福喙夃腑喔?MTF confluence 喔斷覆喔о笅喙夃腑喔?+ toggle 喔勦福喔?- **喔⑧箟喔笝喔弗喔编竾 V17鈥揤24** 鈥?Institutional SMC (`smartmoneyconcepts`), Pruning Gate (喔ム笖 token ~70%), Structurally-Aware SL/TP, Dynamic MTF Zone Gates, Cluster Trading Logic, Sequential Offset Recovery, Outcome Attribution Loop, AI Model Leaderboard 鈥?喔｀覆喔⑧弗喔班箑喔傅喔⑧笖喙€喔曕箛喔∴箖喔?`.obsidian-wiki/07_Trading_Intelligence/`

### 馃敆 MT5 Full Agent Control (Broker-First)

> **Status: 馃煝 Stable (21 MT5 Tools)** 鈥?Mobile (KMP) 鈫?mt5-core-server (Node.js:8090) 鈫?Python Bridge 鈫?MetaTrader5 API

- **Broker-First Data** 鈥?喔｀覆喔勦覆, volume, positions, OHLCV 喔堗覆喔?MT5 Broker 喙傕笖喔⑧笗喔｀竾 (喙冟笂喙夃箑喔夃笧喔侧赴喙€喔∴阜喙堗腑 user 喔｀赴喔氞父喔娻副喔?鈥?喔勦箞喔侧箑喔｀复喙堗浮喔曕箟喔權抚喔脆箑喔勦福喔侧赴喔箤喔溹箞喔侧笝 TradingView)
- **Full Account Control** 鈥?equity, balance, margin, P&L, symbols, positions, orders, history, batch close/break-even
- **Advanced Intelligence Suite** 鈥?Market Scanner, Correlation Radar, Sentiment Gauge, Institutional Flow, Economic Radar, Trade Journal (AI scoring)
- **Audit Trail** 鈥?喔氞副喔權笚喔多竵喔椸父喔?trade action 喔椸傅喙?AI 喔副喙堗竾 喔炧福喙夃腑喔∴竸喔班箒喔權笝喔勦父喔撪笭喔侧笧

---

## 馃洜锔?Prerequisites (喔傕箟喔竵喔赤斧喔權笖喔炧阜喙夃笝喔愢覆喔?

- **Java JDK 21** (喔∴傅喔∴覆喔炧福喙夃腑喔?Android Studio 喙冟笝喙傕笩喔ム箑喔斷腑喔｀箤 `jbr`)
- **Android Studio** 喙€喔о腑喔｀箤喔娻副喔權弗喙堗覆喔父喔?- **Node.js** 鈥?喔赋喔福喔编笟喔｀副喔?`mt5-core-server`
- **Python 3.10+** 鈥?喔赋喔福喔编笟 `mt5_bridge.py`

---

## 馃洜锔?Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3 Series (Free-Tier Optimized, Multi-Key) | Active |
| AI Providers | Gemini / OpenRouter / Groq / MiniMax (+ cross-provider fallback) | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Embedding (Mobile) | LocalOnnx (paraphrase-multilingual-MiniLM-L12-v2, ~117MB offline) / Gemini Cloud cascade | Active |
| Background Service | Android Foreground (DataSync) + Manifest Alert Receivers | Active |
| Logic Controller | JarvisOrchestrator (Multi-Provider + Fallback) | Active |
| MT5 Core Server | Node.js + Express (port 8090) 鈥?M15_WALL_SCALPING | Active |
| MT5 Python Bridge | Python + MetaTrader5 API | Active |

---

## 馃摝 Tool Catalogue (Total: 91 Tools)

### 馃 BUILT-IN & SYSTEM TOOLS (21 tools)
- `calculate`: 喔勦赋喔權抚喔撪笝喔脆笧喔堗笝喙屶竸喔撪复喔曕辅喔侧釜喔曕福喙?- `get_current_datetime`: 喔傕箟喔浮喔灌弗喔о副喔權箑喔о弗喔侧箒喔ム赴喔涏笍喔脆笚喔脆笝
- `remember_fact`: 喔氞副喔權笚喔多竵喔傕箟喔浮喔灌弗喔ム竾喔勦抚喔侧浮喔堗赋喔｀赴喔⑧赴喔⑧覆喔?- `recall_memory`: Semantic search 喔堗覆喔佮笎喔侧笝喔勦抚喔侧浮喔｀腹喙?- `convert_units`: 喙佮笡喔ム竾喔笝喙堗抚喔⑧釜喔侧竵喔?- `set_reminder`: 喔曕副喙夃竾喔佮覆喔｀箑喔曕阜喔笝/TODO
- `format_json`: 喔堗副喔斷福喔灌笡喙佮笟喔?JSON
- `translate_text`: 喙佮笡喔ム笭喔侧俯喔?- `summarize_text`: 喔福喔膏笡喔傕箟喔竸喔о覆喔∴涪喔侧抚
- `search_web`: 喔勦箟喔權斧喔侧箑喔о箛喔?(喔栢腹喔佮笅喙堗腑喔權腑喔编笗喙傕笝喔∴副喔曕复喙冟笝 trading context)
- `identity_update`: AI 喔涏福喔编笟喙佮笗喙堗竾喔曕副喔о笗喔?喔傕箟喔浮喔灌弗喔溹腹喙夃箖喔娻箟喙€喔∴阜喙堗腑喔栢腹喔佮釜喔编箞喔?- `analyze_and_display_report`: 喔箞喔囙福喔侧涪喔囙覆喔權涪喔侧抚喔ム竾喙佮笂喔椸箒喔ム箟喔о笧喔灌笖喔福喔膏笡
- `chart_dashboard_control`: 馃搳 AI 喔勦抚喔氞竸喔膏浮喔笝喙夃覆喔佮福喔侧笩喙€喔竾 鈥?喙€喔涏复喔?喔涏复喔斷竵喔｀覆喔? 喙€喔涏弗喔掂箞喔⑧笝 symbol/timeframe, 喔涏福喔编笟 layout (single/rsi/macd/rsi_macd/volume/full), 喙€喔涏复喔?喔涏复喔?indicator (EMA20/50/200, Bollinger), 喔弗喔编笟 Dashboard鈫擳radingView
- `system_run_diagnostics`: 喔曕福喔о笀喔父喔傕笭喔侧笧喔｀赴喔氞笟 (Self-healing)
- `system_check_connectivity`: 喔曕福喔о笀喔佮覆喔｀箑喔娻阜喙堗腑喔∴笗喙堗腑 API
- `system_create_agent_tool`: 馃 AI 喔福喙夃覆喔?喙佮竵喙夃箘喔?tool 喙€喔竾 (persist 喔傕箟喔侧浮 session)
- `system_list_agent_tools`: 喔斷腹喔｀覆喔⑧竵喔侧福 custom tools + logic 喔傕箟喔侧竾喙冟笝
- `system_delete_agent_tool`: 喔ム笟 custom tool (喙勦笩喔ム箤+registry)
- `voice_summary`: 喔福喔膏笡喙€喔夃笧喔侧赴喔箞喔о笝喔椸傅喙堗笧喔灌笖喙冟笝喙傕斧喔∴笖喙€喔傅喔⑧竾
- `mt5_place_order` / `mt5_close_position`: alias 喔箞喔?喔涏复喔斷腑喔箑喔斷腑喔｀箤 MT5

### 馃搳 TRADING TOOLS (28 tools)
- `trading_price`: 喔｀覆喔勦覆 Real-time (Stocks/Crypto/Forex/Gold 鈥?TV primary + fallback)
- `trading_market_snapshot`: 喔犩覆喔炧福喔о浮喔曕弗喔侧笖喔曕覆喔∴竵喔ム父喙堗浮喔父喔曕釜喔侧斧喔佮福喔｀浮
- `trading_top_gainers` / `trading_top_losers`: 喔曕副喔о笧喔膏箞喔?喔斷复喙堗竾喙佮福喔囙釜喔膏笖
- `trading_technical_analysis`: TA (RSI, MACD, BB, EMA) 喔｀腑喔囙福喔编笟 @TF + OANDA/FX_IDC fallback
- `trading_multi_timeframe`: 喔勦抚喔侧浮喔腑喔斷竸喔ム箟喔竾喔椸父喔?TF (W 鈫?15m)
- `trading_bollinger_scan` / `trading_oversold_scan` / `trading_overbought_scan` / `trading_volume_breakout`: Scanners
- `trading_sentiment`: 喔覆喔｀浮喔撪箤喔曕弗喔侧笖喔堗覆喔?Reddit 鈿狅笍 (upstream 403 喙€喔涏箛喔權竸喔｀副喙夃竾喔勦福喔侧抚)
- `trading_news`: 喔傕箞喔侧抚喔佮覆喔｀箑喔囙复喔?6 喙佮斧喔ム箞喔?(Google News + Yahoo/CNBC/MarketWatch/Investing.com/CoinDesk)
- `trading_combined`: TA + News + Sentiment
- `trading_fundamental_analysis`: 喔涏副喔堗笀喔编涪喔炧阜喙夃笝喔愢覆喔?- `trading_fear_greed`: 馃尅锔?Crypto Fear & Greed (alternative.me)
- `trading_macro_calendar`: 馃搮 喔涏笍喔脆笚喔脆笝喙€喔ㄠ福喔┼笎喔佮复喔?(ForexFactory, 喙€喔о弗喔侧箘喔椸涪)
- `trading_economic_data`: 馃嚭馃嚫 FRED (GDP, CPI, 喔о箞喔侧竾喔囙覆喔? 喔斷腑喔佮箑喔氞傅喙夃涪 Fed 鈥?喙勦浮喙堗笗喙夃腑喔?API Key)
- `trading_correlation_matrix`: Correlation 喔｀赴喔抚喙堗覆喔囙釜喔脆笝喔椸福喔编笧喔⑧箤
- `trading_position_sizing`: 喔勦赋喔權抚喔撪競喔權覆喔斷箘喔∴箟
- `trading_crypto_overview`: 馃獧 CoinGecko (Market Cap, Dominance, Trending)
- `trading_deep_analysis_suite`: 喔о复喙€喔勦福喔侧赴喔箤 5 喔∴复喔曕复 (LSD, Orderflow, Fibo, Momentum, Squeeze) + TV local fallback
- `trading_harmonic_scan`: Harmonic Patterns (Gartley, Bat, Butterfly)
- `trading_elliot_modern_analysis`: Elliott Wave 喙佮笟喔?Modern
- `trading_strategy_signal`: 喔副喔嵿笉喔侧笓喔堗覆喔?5 喔佮弗喔⑧父喔椸笜喙?Quantpedia 喔勦赋喔權抚喔撪箖喔權箑喔勦福喔粪箞喔竾 (TSMOM/Trend/Reversal/Donchian/52W-High) + consensus
- `trading_signal_stats`: 喔笘喔脆笗喔脆釜喔编笉喔嵿覆喔?鈥?backtest 喔⑧箟喔笝喔弗喔编竾 (win-rate/avgR) 喔福喔粪腑喔溹弗 TP/SL 喔堗福喔脆竾喔堗覆喔?signal 喔椸傅喙堗涪喔脆竾喙勦笡喙佮弗喙夃抚 (source=live)
- `automation_manage_alerts`: 喔福喙夃覆喔?喙佮竵喙? rename /喔ム笟/list alerts (validate 喔斷箟喔о涪 AlertFieldCatalog)
- `automation_manage_schedule`: 鈴?喔囙覆喔權笗喔侧浮喙€喔о弗喔?(one-time/daily)

### 馃敆 MT5 BRIDGE TOOLS (21 tools)
**Core Actions:** `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_modify_position`

**Core Agent (Broker 喙傕笖喔⑧笗喔｀竾):** `trading_mt5_account_info`, `trading_mt5_list_positions`, `trading_mt5_list_orders`, `trading_mt5_list_history`, `trading_mt5_candles`, `trading_mt5_symbol_info`, `trading_mt5_symbol_search`, `trading_mt5_analyze`, `trading_mt5_close_all` 鈿狅笍, `trading_mt5_break_even_all` 鈿狅笍, `trading_mt5_snapshot`, `trading_mt5_trade_actions`

**Advanced Intelligence:** `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_sentiment_gauge`, `trading_mt5_institutional_flow`, `trading_mt5_economic_radar`, `trading_mt5_trade_journal`

### 馃搱 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard (喔｀腑喔囙福喔编笟 @TF)
- `trading_smc_sweeps`: MTF Sweep Detection
- `trading_smc_liquidity`: MTF Liquidity Zones & Stars
- `trading_smc_orderblocks`: Order Blocks + FVG
- `trading_smc_structure`: Market Structure (BOS/CHoCH, Premium/Discount)

### 馃摎 STRATEGY LIBRARY (3 tools 鈥?offline)
- `strategy_list` / `strategy_search` / `strategy_explain`: 喔勦弗喔编竾喔佮弗喔⑧父喔椸笜喙?Quantpedia 60 喙佮笟喔?9 喔浮喔о笖 喔炧福喙夃腑喔∴箓喔勦箟喔?QuantConnect

### 馃搧 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write` (喔｀腑喔囙福喔编笟 **.xlsx 喔堗福喔脆竾**), `file_delete`, `file_analyze` (OCR/PDF), `file_move`, `file_search`

### 馃摲 CAMERA, VISION & VOICE (9 tools)
- `vision_activate` / `vision_deactivate`: 喙€喔涏复喔?喔涏复喔斷笗喔?AI
- `camera_analyze_scene`: 喔о复喙€喔勦福喔侧赴喔箤喔犩覆喔?- `camera_detect_objects`: 喔曕福喔о笀喔堗副喔氞抚喔编笗喔栢父 (AR Overlay)
- `camera_read_text`: OCR
- `camera_switch_provider` / `camera_switch_mode`
- `voice_get_profiles`: 喔斷腹喙€喔傅喔⑧竾 30 喙傕笡喔｀箘喔熰弗喙?- `voice_set_profile`: 喙€喔涏弗喔掂箞喔⑧笝喙€喔傅喔⑧竾 (喔溹腹喔佮笗喔编抚喔曕笝/喔勦赋喔ム竾喔椸箟喔侧涪喔副喔曕箓喔權浮喔编笗喔?

---

## 馃殌 Roadmap

1. **Rich Chat Rendering** 鈥?喙佮釜喔斷竾喔曕覆喔｀覆喔?/ 喔｀腹喔?/ 喔佮福喔侧笩 / infographic 喙冟笝喙佮笂喔?(markdown table renderer, chart library)
2. **Multi-Agent Orchestration (Swarm)** 鈥?喔佮福喔班笀喔侧涪喔囙覆喔權箖喔箟 AI Agent 喙€喔夃笧喔侧赴喔椸覆喔囙笚喔赤竾喔侧笝喔炧福喙夃腑喔∴竵喔编笝
3. **Portfolio Hub** 鈥?喔曕复喔斷笗喔侧浮喔炧腑喔｀箤喔曕竵喔侧福喔ム竾喔椸父喔?+ P&L Analytics
4. **Strategy Backtester** 鈥?喔椸笖喔腑喔氞竵喔ム涪喔膏笚喔樴箤喙佮笟喔氞竸喔｀笟喔о竾喔堗福
5. **Hardware Extension** 鈥?Smart Home / Wearables

---

## 馃摎 喙€喔竵喔覆喔｀腑喙夃覆喔囙腑喔脆竾 (Obsidian Wiki)

- `.obsidian-wiki/01_Architecture/` 鈥?喔笘喔侧笡喔编笗喔⑧竵喔｀福喔?+ AI subsystem reviews
- `.obsidian-wiki/02_Components/Alert_System_V2.md` 鈥?喔｀赴喔氞笟 Alert + AlertFieldCatalog
- `.obsidian-wiki/04_Tasks/App_Review_Checklist.md` 鈥?checklist 喔椸笖喔腑喔?15 喔浮喔о笖 (喔溹箞喔侧笝 13/15)
- `.obsidian-wiki/07_Trading_Intelligence/` 鈥?喔涏福喔班抚喔编笗喔?mt5-core-server V17鈥揤26 喔夃笟喔编笟喙€喔曕箛喔?- `.obsidian-wiki/00_System/log.md` 鈥?喔氞副喔權笚喔多竵喔佮覆喔｀笧喔编笒喔權覆喔椸副喙夃竾喔浮喔?


### P6.2d Demo Recovery + Market Price Model
- Mobile Demo positions/balance can be restored from SQLDelight after app restart.
- Demo quotes use executable Bid/Ask; BUY marks/exits on Bid and SELL on Ask.
- DemoMarketPriceModel supports explicit quotes or deterministic mid+spread generation for replay/backtest.
- Position restore recalculates used margin and sequence state without MT5/server dependency.
- Demo remains isolated from MT5 Live execution and uses the same RiskEngine boundary.


### P6.2f Unified Broker-Aware Risk Boundary
- Unified `TradingAccount` can be converted directly into the deterministic P6 `RiskEngine` account model for both Demo and MT5 providers.
- Risk sizing now optionally includes round-trip transaction cost per lot, so spread/commission/rebate assumptions affect the risk budget instead of sizing from stop distance alone.
- `DemoTradingEngine.evaluateRisk()` uses the same P6 risk boundary and symbol-specific tick/value/lot constraints as execution.
- Added regression coverage for cost-aware Demo risk sizing.


### P6.2g–P6.2n Risk & Execution Safety
- Demo order execution is enforced through the unified P6 RiskEngine and account protection policy.
- Broker accounting uses symbol tick size/value for floating P/L, leverage-aware margin, commission/rebate and quote-aware spread handling without double-counting spread.
- Added kill-switch protection, quote-aware spread/slippage hooks, BrokerParity verification and Mobile `RiskStatusCard`.
- Added regression coverage for execution blocking, P/L valuation and account protection.
`P6.2m`: Mobile Trading Execution Mode แสดง `DEMO / PAPER ↔ MT5 LIVE` ชัดเจน พร้อม confirmation ก่อน Live และห้ามสลับขณะ Auto-Trading ทำงาน. 2026-08-24 09:05
`P6.2 mobile hardening`: Live Mode เปลี่ยนสถานะตาม server confirmation เท่านั้น; หาก update ล้มเหลวจะคง mode ล่าสุดที่ยืนยันแล้ว. 2026-08-24 09:24
- 2026-08-24 — P6.3: เพิ่ม Mobile Execution & Account Status แสดง execution mode, engine state, MT5 connection, Risk Gate และ LIVE account telemetry (login/server/balance/equity/margin/free margin/leverage/margin level). compile + unit tests PASS.

### P6.4 System Validation Harness — Optimization/Evolution Promotion Boundary
- `trading_backtest_optimize` is now discovery-only: candidate search, robustness evidence and optimization memory are allowed; production parameter writes are not.
- `trading_backtest_evolve` is the sole production promotion path and requires explicit `apply=on` plus full-dataset and holdout validation.
- This prevents Optimization from bypassing the Evolution/Risk promotion boundary.

### P6.4 System Validation Harness
- Added deterministic `SystemValidationHarnessTest` for core RiskEngine and MT5 execution-safety paths.
- Added `composeApp/docs/P6.4_SYSTEM_VALIDATION.md` with automated/manual gates G0–G6, failure-injection cases and evidence rules.
- Persisted `risk_gate_eligible` for Strategy/Entry tuning; legacy tuning is ineligible by default.
- Signal execution accepts tuned parameters only from explicitly promoted Evolution results (`source=evolve`, eligible, non-overfit).
- Live-money execution remains blocked until the validation gates pass on MT5 Demo.

### Recent 2026-08-24 — P3.1 Fixture Harness
- Added `PineLiquidityFixtureHarness` for empirical Pine V11.29 liquidity lifecycle validation.
- Replays candle prefixes against expected Pine-visible snapshots and reports bar-level mismatches.
- Fixture contract/template is documented under `composeApp/docs/fixtures/`.

### Recent 2026-08-24 — P5.2–P5.5 Backtest Robustness + Strategy Ranking
- Walk-forward robustness and parameter-stability analysis are implemented.
- Monte Carlo provides deterministic simulations, ruin/profit probability and drawdown percentiles.
- Permutation testing evaluates whether strategy edge is statistically significant.
- `OverfittingScore` combines walk-forward, permutation, parameter stability and Monte Carlo evidence.
- `RobustnessAnalyzer` classifies robustness; `StrategyRanking` produces PASS/HOLD and `riskGateEligible`.
- Optimization wires WalkForward → Monte Carlo → Permutation → OverfittingScore, with Risk Gate eligibility based on robustness/significance/ruin constraints.

### Recent 2026-08-24 — Live READY / Session Resumption Hardening
- Live UI exposes explicit `READY` so the user can see when the AI is ready to converse.
- Auto greeting is triggered by the actual READY event, once per WebSocket lifecycle, rather than by a fixed timer.
- Session resumption handle is maintained across controlled WebSocket resets.
- Pre-READY audio handling prevents stale audio crossing session boundaries.

### Documentation Synchronization Policy
When the active project is `PersonalAIBot`, documentation has two official update targets:
- `.obsidian-wiki/00_System/log.md` — the single authoritative development timeline.
- `README.md` — user-visible capabilities and newly added features for the GitHub project.

`composeApp/log.md` and `log.txt` are not used for the `PersonalAIBot` project timeline.
`n### Recent 2026-08-25 • Signal Alert Data-Source Hardening`n- Signal alerts now resolve candle source centrally: DEMO/PAPER=TradingView only; LIVE+MT5 connected=MT5; LIVE+offline/unavailable=TradingView.`n- Added runtime source provenance fields `signal_data_source` / `signal_data_source_reason` and explicit source-selection logs.`n- Live Tool Guard prevents premature `trading_mt5_symbol_search` during Signal Alert requests.`n- Native Live tool-response logs now include `callId` for reliable correlation.`n


### Recent 2026-08-25 • Signal Alert Snapshot Consistency
- `trading_signal_alert` BUY/SELL jobs sharing the same `symbol@TF` now reuse one per-cycle market-data snapshot, preventing duplicate TradingView fetch/strategy-gate evaluation while retaining signal-id edge deduplication.


### 2026-08-25 Alert observability update
`SignalAlertProvider` now deduplicates Strategy Gate and tuned-parameter diagnostic logs per closed candle while continuing to evaluate the Gate on every automation cycle. This reduces repeated M15 `Gate WEAK` output without changing signal, risk-gate, or automation execution semantics.
\n### Recent 2026-08-25 — Alert System V2 Live Voice Concurrency\n- Reviewed the complete captured `logcat.txt` runtime window for Signal Alert V2. Signal edge-dedup, FIRE/RESET transitions, DEMO TradingView source policy, Risk Gate blocking and AI/Live delivery were consistent with the intended design.\n- Added per-service Live voice serialization: only Gemini Live WebSocket/audio sessions are queued; signal detection, AI summaries and notifications remain concurrent so a new signal is not blocked by an earlier voice session.\n- Added observable Live queue-wait logging and hardened Android logging for local JVM tests.\n- Validation: `:composeApp:compileDebugKotlinAndroid` PASS and `:composeApp:testDebugUnitTest` PASS (86/86).\n

### Recent 2026-08-25 • Signal Alert Reliability Hardening
- เพิ่ม 90s symbol/TF/side signal throttle แยกจาก job-level `signal_id` dedup เพื่อป้องกัน alert ถี่จากหลาย strategy
- เพิ่ม AI summary serialization และ alert-specific Gemini fail-fast (8s, no 45s long retry, max 4 fallback models)
- Live voice ยังคงใช้ mutex ป้องกัน WebSocket/PCM overlap


### Live Alert Voice Reliability (2026-08-26)
- Alert Live summary prompt ใช้ **1-2 ประโยค / 20-35 คำ / ~120 chars** โดยให้ Signal/Strategy และ Entry/SL/TP มี priority สูงสุด เพื่อบังคับ short-form response และลด audio duration
- Signal polling log เป็น compact operational log: ไม่แสดง cache HIT/MISS รายรายการและไม่พิมพ์ `SUPPRESS_ALREADY_TRIGGERED` ซ้ำทุก cycle; ใช้ `SIGNAL_CACHE` summary ต่อ cycle และ deduplicate signal actions ตาม state
- Signal action diagnostics ใช้รูปแบบ event เดียว `SIGNAL_EVENT` สำหรับ FIRE/RESET/SUPPRESS เพื่อให้ AI วิเคราะห์ state transition ได้ง่ายและลด log noise โดยไม่เปลี่ยน execution semantics
- เพิ่ม response circuit breaker ที่ **180 chars** เมื่อมี audio แล้ว และลด one-shot session hard ceiling เป็น **22s**; แยก Signal Alert ให้เป็น short-form จาก conversational Live
- ลด audio-stream idle watchdog เป็น **10s**; timeout/error ถูกจัดเป็น cause ก่อน `AUDIO_SUCCESS` เพื่อให้ diagnostics ไม่รายงาน success หลอก
- ถ้า session มี audio แล้วแต่ถูก watchdog/cap ปิด จะไม่ fallback ไปพูดซ้ำด้วย TTS; เก็บ cause จริงไว้ใน RESULT log
- ยังคง chain แบบ 2.5 Native → 3.1 พร้อม key rotation และ cooldown ของโมเดลที่ตอบ transcript แต่ไม่มี audio

### Live Alert Voice Reliability (2026-08-25)
- Signal Alert voice ใช้ Live model chain พร้อม fast-failover เมื่อ setup หรือ first-audio ผิดปกติ เพื่อไม่ให้แจ้งเตือนค้างนาน
- Live voice queue ของ Signal Alert เป็น serialized queue รอได้ถึง **90s** ทุก timeframe เพื่อไม่ให้ 1m/5m ตกไป TTS เพียงเพราะ Live session ก่อนหน้ายังพูดอยู่; ยังคงมีกลไก no-overlap กับ Android TTS
- หลัง audio เริ่ม ระบบมี audio-stream idle watchdog 12 วินาที; ถ้าไม่มี audio/transcript progress ต่อเนื่องจะจัดเป็น `AUDIO_STREAM_IDLE_TIMEOUT` และ fallback Android TTS
- Live diagnostic logs แยก phase, latency, API error และ WebSocket close reason เพื่อระบุได้ว่า timeout เกิดก่อน setup, ระหว่าง generation/audio หรือจาก app-side queue/close lifecycle
- หลัง audio chunk แรกเริ่มแล้ว ระบบไม่ใช้ first-audio timeout ตัด response และรอ `turnComplete` ตามธรรมชาติ
- Live output transcript ถูกสะสมทุก chunk เพื่อให้ Chat summary และ diagnostics สะท้อนคำตอบจริง

### Live Alert Failover Cancellation Hardening (2026-08-25)
- setup/audio watchdog ของ Gemini Live ผูกกับ WebSocket coroutine โดยตรง เพื่อให้ timeout ยกเลิก session ได้จริง
- ลดกรณี first-audio timeout เกิดแล้วแต่ WebSocket ค้างต่อ ทำให้ fallback/Android TTS ล่าช้าหลายสิบวินาที


## Live Voice Diagnostics
- Live alert voice now records queue wait, setup/READY latency, request-to-output latency, first-audio/first-transcript timing, turn completion, API error state, and classified fallback cause.
- Separate watchdogs cover queue, setup, first output, and total session duration; Android TTS remains the final fallback.
