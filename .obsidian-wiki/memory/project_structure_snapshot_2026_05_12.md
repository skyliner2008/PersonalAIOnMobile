# Project Structure Snapshot - 2026-05-12

## Purpose

PersonalAIBot is a Kotlin Multiplatform Compose app plus a TypeScript MT5 trading server. The project combines a Jarvis-style chat assistant, tool execution, live/vision/voice features, trading terminal UI, MT5 bridge control, auto-trading logic, model/provider fallback, local memory, and an Obsidian wiki knowledge base.

## Root Layout

- `composeApp/`: Kotlin Multiplatform app module for Android and iOS.
- `iosApp/`: Xcode wrapper for the shared Compose app.
- `mt5-core-server/`: Express/TypeScript trading server, WebSocket hub, MT5 bridge supervisor, auto-trading engine, V25 real-time wall engine.
- `.obsidian-wiki/`: Project encyclopedia, architecture notes, roadmap, trading intelligence specs, memory records.
- `brain/`: Project knowledge/material area.
- `custom_agent_tools/`: Agent/tooling experiments or extensions.
- `Pine Script/`: TradingView/Pine scripts.
- `tools/`: Utility scripts, including MT5 algo notes.
- `scratch/`: Local diagnostics, generated lists, ad hoc experiments.
- `SS/`: Screenshots/reference images.

## App Module

Build stack:
- Kotlin `2.1.20`, Compose Multiplatform `1.7.3`, AGP `8.8.2`.
- Targets: Android, iOS arm64/simulator.
- Key libraries: Ktor client, SQLDelight, kotlinx serialization/coroutines, CameraX, compose-webview, ONNX Runtime Android.

Important app files:
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/App.kt`: top-level Compose app shell.
- `composeApp/src/commonMain/kotlin/com/example/personalaibot/JarvisViewModel.kt`: main state orchestration for chat, providers, camera, voice, chart, MT5 terminal, realtime channel, AI tracking, and local model download.
- `composeApp/src/commonMain/sqldelight/com/example/personalaibot/db/JarvisDatabase.sq`: local database schema.

Important app packages:
- `ai/`: intent classification, Jarvis orchestration/planning, live tool bridge, trading intent helpers.
- `automation/`: automation service models and integration.
- `camera/`: camera state, platform abstraction, frame analysis.
- `data/`: Gemini service, live Gemini service, model config, symbol/category logic.
- `data/providers/`: Gemini, Claude, OpenAI, OpenRouter, LiteLLM, Minimax provider implementations and API key testing.
- `data/embedding/`: local embedding support.
- `db/`: database driver factory and database wiring.
- `diagnostic/`: diagnostic helpers.
- `memory/`: Jarvis memory manager.
- `tools/`: tool registry, system/file/trading tools, side effects.
- `tools/trading/`: MT5 terminal service, trading API service, TradingView/chart bridges, SMC API and tool execution, broker cache, AI tracking models.
- `ui/screen/`: main screens/dialogs including trading terminal, chart screen, auto-trading screen, settings, tool list, live mode panel.
- `ui/theme/`: Jarvis theme.
- `voice/`: voice input/output abstractions.

## MT5 Core Server

Build stack:
- Node/TypeScript ESM, Express, `better-sqlite3`, `ws`, `pino`, `prom-client`, `zod`, Vitest.
- Scripts: `npm run dev`, `npm run build`, `npm start`, `npm test`, `npm run test:coverage`.

Entrypoints:
- `mt5-core-server/src/index.ts`: bootstraps bridge readiness, DB, Express, CORS, token auth, rate limits, routes, WebSocket `/ws/mt5`, V25 shadow mode, graceful shutdown.
- `mt5-core-server/src/config.ts`: env config and required secrets (`ADMIN_TOKEN`, `TOKEN_PEPPER`, `DB_ENCRYPTION_KEY`).
- `mt5-core-server/bridge/mt5_bridge.py`: Python bridge to MetaTrader 5.
- `mt5-core-server/mql5_experts/JARVIS_GlobalManager.mq5`: MQL5 expert integration.

Routes:
- `routes/auth.ts`: client/admin token and auth management.
- `routes/health.ts`: health diagnostics.
- `routes/mt5.ts`: MT5 account, symbols, candles, positions, orders, modification, bridge operations.
- `routes/autoTrading.ts`: auto-trading control, status, analytics, settings.
- `routes/v25.ts`: V25 diagnostics.

Core services:
- `autoTradingService.ts`: large legacy orchestration class. Handles start/stop/runOnce, cycle loop, manage loop, learn loop, analytics, persistence, trade review, MT5 fetch helpers, deep analysis.
- `services/auto/*`: modular auto-trading system under active refactor.
- `bridgeClient.ts` and `bridgeSupervisor.ts`: bridge communication and auto-start supervision.
- `mt5RealtimeHub.ts` and `mt5SnapshotHub.ts`: realtime/snapshot broadcasting.
- `tokenAuth.ts`, `encryption.ts`, `logger.ts`, `metrics.ts`, `tracking.ts`, `mt5Clients.ts`, `eaConfigService.ts`: security, observability, clients, and support systems.

Modular auto-trading areas:
- `auto/agents/`: Analyst, RiskOfficer, ExecutionTrader, SL/TP analyst, post-mortem, JSON parsing.
- `auto/analyzers/`: technical, SMC, news, session, levels, range, momentum, mean-reversion, breakout, correlation, cross-asset, multi-agent.
- `auto/analyzers/smc/`: order blocks, FVG, liquidity zones, market structure, sweeps, price map.
- `auto/core/`: EventBus, AgentCoordinator, MarketDataService, PersistenceService, IndicatorPipeline, SignalDetector, ProximityGate, SmcTrailManager, TradeManagementService, TradingExecutionService.
- `auto/providers/`: Gemini, OpenAI, Claude, OpenRouter, LiteLLM/Ollama/Minimax/native dispatch and registry.
- `auto/risk/`: circuit breaker and Kelly sizing.
- `auto/v25/`: real-time wall engine with tick gateway, tick buffer, bar close bus, micro-wall builder, wall state machine, proximity index, reaction detection, breakout wall promotion, and 5 playbooks.

## Knowledge Base

Important wiki areas:
- `00_System/`: index, prompt, schema, log.
- `01_Architecture/`: overview, auto-trading workflow, memory strategy, provider architecture, vision system.
- `02_Components/`: component notes for Gemini, LiveGemini, LiveToolBridge, JarvisViewModel, LocalEmbedding.
- `03_Tools/`: tool catalogue.
- `04_Tasks/`: active tasks and changelogs.
- `05_Android_Skills/`: Android/Gradle migration notes.
- `06_Gemini_Skills/`: Gemini API/live API notes.
- `07_Trading_Intelligence/`: trading strategy and engine specs from V15 through V25.
- `08_lightweight-charts-docs-api/`: chart docs.
- `09_Roadmap/`: product and architecture roadmap.
- `memory/`: task-specific memory records.

Current memory highlights:
- Auto-trading evolved through V20 model ranking/fallback, V24 fade-the-level, and V25 real-time wall engine.
- V25 Phase A-D is documented as complete in shadow mode, pending LLM gating, execution handoff, and V24 sunset.
- Current task notes still mention pending `recordSlTpAccuracy()`, real-time mobile WebSocket push, backtest mode, dashboard analytics, and bridge health checks.

## Development Notes

- Treat `mt5-core-server/src/services/autoTradingService.ts` as high-risk because it is very large and central. Prefer small changes or moving behavior into existing modular services when practical.
- Do not edit generated/runtime outputs unless explicitly needed: `build/`, `.gradle/`, `.kotlin/`, `mt5-core-server/dist/`, `mt5-core-server/coverage/`, `mt5-core-server/data/`, `node_modules/`.
- Keep `.env`, DB files, screenshots, and user scratch files untouched unless the task explicitly targets them.
- For app work, check both `App.kt` and `JarvisViewModel.kt` first, then the specific package (`tools/trading`, `data/providers`, `ui/screen`, etc.).
- For server work, check route entrypoint, service implementation, and matching tests before editing.
- For trading logic, cross-check `.obsidian-wiki/07_Trading_Intelligence/` and `.obsidian-wiki/memory/` to preserve strategy intent.
