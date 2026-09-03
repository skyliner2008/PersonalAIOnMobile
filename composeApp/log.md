## 2026-08-19 藝艦 Recovered Development Log 
- Recovered historical log.md from Git commit 7afae61 because a previous write operation accidentally overwrote the working log. 
- Restored full historical entries before adding current development entries. 
- Going forward log updates are append-only and must preserve all previous entries. 
 
## 2026-08-19 藝艦 Typed Tool Events Test Fix 
- Fixed TypedToolLifecycleIntegrationTest to match actual ChatStreamEvent.ToolResult contract. 
- gradlew.bat :composeApp:test: BUILD SUCCESSFUL.
 
## 2026-08-19 - Trading Intelligence Review Continuation 
- Reviewed the current implementation after Serena reconnect. 
- Fixed Chart/SMC event propagation: ChatStreamEvent.ToolResult now carries isError and GeminiService forwards ToolExecutor error state. 
- Fixed apply/persistence accounting so applied counters increment only when saveStrategyTuning/saveEntryTuning succeeds. 
- Verified the project test suite: gradlew.bat :composeApp:test --quiet = SUCCESS, return code 0. 
- composeApp/log.md is the only development log for this work. .obsidian-wiki/00_System/log.md was not modified by this operation.
`r`n## 2026-08-19 藝艦 Live long-task backtest_evolve quota hardening`r`n- Root cause: GeminiService.generateResponse classified GenerateRequestsPerDayPerProjectPerModel-FreeTier 429 as per-minute because it only looked for per_day text. RetryInfo then caused 28篓C59s waits and repeated 429s instead of rotating immediately.`r`n- Fix: parse Gemini quotaId/GenerateRequestsPerDay... as daily/project-model hard quota; mark credential cooldown long-term and skip timed retry; rotate key/model immediately.`r`n- Long-task protection: BacktestEvolution now caps AI reflection calls at 8 per evolve task; remaining rounds continue with heuristic evolution, preventing strategy=all (8 strategies 藝脕 8 rounds) from exhausting chat free-tier request quota.`r`n- Verification: gradlew.bat :composeApp:test --quiet PASS; gradlew.bat :composeApp:compileDebugKotlinAndroid --quiet PASS; no diagnostics in modified Kotlin files.`r`n
`r`n## 2026-08-22 P1 incremental architecture`r`n- Extracted Mt5RiskGate from TradingToolExecutor into dedicated Mt5RiskGate.kt.`r`n- Added TradingToolRouter domain boundary for MARKET/TECHNICAL/SENTIMENT/RESEARCH/SMC/MT5/MT5_INTELLIGENCE.`r`n- TradingToolExecutor now rejects unknown trading tools through the router before legacy dispatch; existing tool names/handlers remain backward-compatible.`r`n- Added router classification test.`r`n- Verification: :composeApp:compileDebugKotlinAndroid + :composeApp:testDebugUnitTest = BUILD SUCCESSFUL (1m 48s).`r`n
## 2026-08-22 藝艦 P1.2 Backtest Tool Boundary
- Extracted BacktestToolHandler as an incremental boundary for backtest / optimize / evolve orchestration.
- Preserved existing TradingToolExecutor tool names and execution behavior.
- Verification: compileDebugKotlinAndroid PASS; testDebugUnitTest PASS.


## 2026-08-22 鈥?P1.3-P1.6 Trading Tool Architecture
- P1.3: extracted MT5 routing into Mt5ToolHandler; MT5 methods remain behavior-compatible and preserve Mt5RiskGate.
- P1.4: extracted market/technical/sentiment routing into MarketTechnicalToolHandler.
- P1.5: extracted research/backtest routing into ResearchToolHandler; BacktestToolHandler remains the backtest implementation boundary.
- P1.6: TradingToolExecutor.execute() now dispatches by TradingToolRouter domain instead of a large tool-name switch; SMC flow behavior preserved.
- Added TradingToolDomainRoutingTest.
- Verification: :composeApp:compileDebugKotlinAndroid and :composeApp:testDebugUnitTest PASS.


## 2026-08-22 鈥?P1.7 Physical Trading Executor Extraction

- Extracted the legacy trading implementation from `TradingToolExecutor` into `TradingToolExecutionBackend`.
- Reduced `TradingToolExecutor` to a stable public facade delegating to the backend.
- Updated Market/Technical, MT5, and Research handlers to depend on the backend boundary.
- Preserved existing tool routing and MT5 risk-gate path.
- Verified Android compilation: `:composeApp:compileDebugKotlinAndroid` 鈥?BUILD SUCCESSFUL.
- Verified unit tests: `:composeApp:testDebugUnitTest` 鈥?BUILD SUCCESSFUL.
- Refactor performed incrementally; no tool names or public executor constructor changed.
 
## 2026-08-22 - P1.8 Backend Hardening / Verification 
- Hardened TradingToolExecutionBackend as the single domain-dispatch boundary; normalized tool names and rejected blank names before dispatch. 
- Preserved Backtest, MT5, Market, Research handler boundaries and MT5 Risk Gate. 
- Added blank-tool routing regression test. 
- Verification: testDebugUnitTest + compileDebugKotlinAndroid - BUILD SUCCESSFUL (14s).
[P2.13 Indicator Intelligence] TradingView multi-timeframe AI indicator matrix expanded: EMA20/50/200, RSI14, MACD, Bollinger, ATR, ADX/+DI/-DI, Stochastic, CCI, MFI, VWAP, volume ratio, OBV, Ichimoku, pivots S/R, Supertrend; raw OHLCV is not exposed in signal output. Added snapshot regression tests. Android compile and debug unit tests PASS.

## 2026-08-23 P0-P3 final hardening pass
- Re-verified P0 trading intelligence baseline: reproducible backtest metadata/tests and MT5 risk gate remain green.
- Re-verified P1 strategy/decision plane and hardened TradingView MTF fusion: normalize active timeframe weights, incorporate normalized indicator matrix score, normalize SMC contributors, and remove duplicate history output.
- Re-verified P1 tool architecture: TradingToolRouter + domain handlers + backend facade remain behavior-compatible.
- Re-verified P2 Live Voice/Conversation path from latest runtime log: tool response succeeds and first model audio starts 624ms after SMC tool response; voice presentation policy remains intentionally comprehensive per product decision.
- Re-verified P2 memory/context and orchestrator code paths compile cleanly.
- Re-verified P3 mobile trading UI/decision feed surfaces compile cleanly; no new blocking diagnostics found.
- Verification: :composeApp:compileDebugKotlinAndroid + :composeApp:testDebugUnitTest = BUILD SUCCESSFUL; 25 tests completed.
 
## 2026-08-23 P2.2 Live READY UI hardening 
- Added an explicit Live connection status pill to LiveModePanel: READY is green only after ConnectionState.Connected from the real Live setup handshake. 
- Exposed liveConnectionState through VoiceController and JarvisViewModel; App now passes it into the Live panel. 
- Preserved event-driven Auto greeting on READY; this UI change makes readiness visible without relying on logcat or waiting for the greeting audio. 
- Verification: :composeApp:compileDebugKotlinAndroid + :composeApp:testDebugUnitTest = BUILD SUCCESSFUL (1m 40s).


## 2026-08-24 鈥?P6.2f Unified Broker-Aware Risk Boundary
- Added `TradingAccount.toRiskAccount()` so Demo/MT5 providers share the same P6 RiskEngine account model.
- Risk sizing now accepts round-trip transaction cost per lot; spread/commission/rebate assumptions are included in the risk budget.
- Added `DemoTradingEngine.evaluateRisk()` using symbol-specific tick/value/volume constraints.
- Verification: `:composeApp:test` PASS (56 tests) and `:composeApp:compileDebugKotlinAndroid` PASS.

## 2026-08-24 鈥?P6.2g鈥揚6.2n Risk/Execution Safety
- Enforced Demo execution through RiskEngine with mandatory SL by account policy.
- Fixed floating P/L to symbol tick size/value and removed spread double-count from net accounting.
- Added kill switch, quote-aware spread/slippage hooks, BrokerParity and Mobile RiskStatusCard.
- Added regression coverage for execution gate, valuation and kill switch.
- 2026-08-24 09:05 鈥?P6.2m: 喔涏福喔编笟 Mobile Auto-Trading UI 喙冟斧喙夃箒喔笖喔?DEMO / PAPER 鈫?MT5 LIVE 喔娻副喔斷箑喔堗笝, 喙€喔炧复喙堗浮 confirmation 喔佮箞喔笝喙€喔涏复喔?MT5 LIVE 喙佮弗喔班弗喙囙腑喔佮竵喔侧福喔弗喔编笟喙傕斧喔∴笖喔傕笓喔?engine 喔椸赋喔囙覆喔? persisted 喔溹箞喔侧笝 auto_trading.enable_live 喙€喔斷复喔?

- 2026-08-24 09:24 鈥?P6.2 mobile hardening: `updateEnableLive()` now uses server-confirmed state, blocks changes while engine runs, and restores the previous confirmed mode on failure. Android compile + unit tests PASS.
- 2026-08-24 — P6.3: ????? Mobile Execution & Account Status ???? DEMO/MT5 LIVE, engine state, MT5 connection, Risk Gate ??? LIVE account telemetry (balance/equity/margin/free margin/leverage/margin level). compile + unit tests PASS.


## 2026-08-24 — Documentation Synchronization Audit
- Reconciled recent mobile development with source code and project history.
- Recorded P3.1 Fixture Harness, P5.2-P5.5 robustness pipeline, Live READY/session-resumption hardening and P6.2/P6.3 execution work.
- Verification: current P6.3 Android compile and unit tests PASS.

## 2026-08-25 — Signal Alert Reliability Hardening
- ตรวจ `logcat.txt` เต็มล่าสุด: พบ signal ใหม่บน XAUUSD/1m ยิงถี่ผ่าน job-level dedup และ Gemini alert timeout/long-retry ทำให้ latency สูง
- เพิ่ม 90s symbol+TF+side throttle, AI summary mutex, alert Gemini timeout 8s + no long retry + max 4 fallback models
- Live voice mutex เดิมคงไว้เพื่อกัน session/PCM overlap
- ยังไม่ได้ compile ใหม่ตามสถานะที่ผู้ใช้แจ้ง; รอ build/diagnostics หลัง patch ชุดนี้
