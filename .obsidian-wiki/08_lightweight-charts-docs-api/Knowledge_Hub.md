# Lightweight Charts Knowledge Hub

Status: curated
Updated: 2026-04-16

## Purpose
Use this folder as the canonical knowledge base for TradingView Lightweight Charts integration in PersonalAIBot.

## Source Notes
- [[Lightweight Charts]]: Full API index (v5.1)
- [[Getting started  Lightweight Charts]]: Installation, core lifecycle, performance guidance
- [[Android wrapper  Lightweight Charts]]: Android wrapper entry (WebView-based rendering)
- [[iOS wrapper  Lightweight Charts]]: iOS wrapper entry (WebView-based rendering)
- [[Financial Widgets Collection]]: TradingView embeddable widgets catalogue

## Key Rules (Implementation)
1. Runtime model
- Lightweight Charts is client-side JS (ES2020 target), not server-side runtime.

2. Data lifecycle
- Use `setData()` for initial or full replacement only.
- Use `update()` for realtime updates of last/new bar (preferred for performance).

3. Series model
- Create series via `chart.addSeries(...)`.
- Do not convert one series type into another; recreate series when type changes.

4. Wrapper strategy (mobile)
- Android/iOS wrappers are WebView-based integrations.
- Keep chart interaction API boundaries clear between native layer and JS bridge.

5. Compliance
- Add required TradingView attribution and NOTICE where applicable.

## Recommended Mapping for This Project
- Compose UI layer -> chart container screen
- Platform bridge (`WebViewHelper` / JS bridge) -> chart commands (`setData`, `update`, options)
- Trading tools output -> normalized OHLC/time points for candlestick/line series

## Practical Next Tasks
1. Define a stable `ChartDataPoint` model for OHLC + time in commonMain.
2. Create platform-specific bridge contracts for Android/iOS WebView wrappers.
3. Add realtime update path using `update()` (avoid full `setData()` refresh loop).
4. Add attribution surface in app settings/about.

---
**Links**: [[index]] | [[catalogue]] | [[Lightweight Charts]]
