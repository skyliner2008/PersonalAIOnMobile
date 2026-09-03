# PersonalAIBot — Project Progress Summary

> Canonical status snapshot rebuilt on 2026-08-24 from the recent project work, current code, existing wiki and verification results. Daily change history remains in `00_System/log.md`.

## 2026-08-24 — Recent Mobile / Trading Intelligence Work

### P3.1 — Pine V11.29 Fixture Harness
- Added `PineLiquidityFixtureHarness` under `composeApp/commonTest` as an empirical-parity boundary.
- Fixture contract captures exact candles plus the Pine-visible liquidity snapshot after each bar.
- Harness replays candle prefixes through the Kotlin detector and reports bar-level expected-vs-actual mismatches.
- Fixture template/usage documentation exists under `composeApp/docs/fixtures/`.
- This is validation infrastructure; strict 1:1 Pine parity still requires real fixtures.

### P5 — Backtest Robustness Pipeline
- Walk-forward analysis is implemented and used for IS/OOS robustness and parameter stability.
- Monte Carlo simulation is implemented with deterministic seed, final-balance percentiles, drawdown percentiles, probability of ruin and probability of profit.
- Permutation test is implemented for statistical significance of strategy edge.
- `OverfittingScore` combines walk-forward, permutation, parameter stability and Monte Carlo evidence.
- `RobustnessAnalyzer` classifies results as ROBUST / ACCEPTABLE / FRAGILE / INSUFFICIENT.
- `StrategyRanking` ranks strategies and marks PASS/HOLD plus `riskGateEligible`.
- Backtest optimization execution wires WalkForward → Monte Carlo → Permutation → OverfittingScore.
- Strategy ranking is gated by score, robustness, statistical significance and Monte Carlo ruin threshold.

### P6.2 — Mobile Demo / Execution Safety
- Added persistent local Demo Account using SQLDelight, independent from MT5 server.
- Added broker-like Demo cost model: spread, commission, rebate, swap, contract size, tick size/value and leverage.
- Added Demo broker execution with margin/leverage validation and transaction-cost-aware open/close accounting.
- Added Bid/Ask-aware Demo market price model and recovery after app restart.
- Unified Demo/MT5 risk boundary through `TradingAccount.toRiskAccount()` and shared deterministic `RiskEngine` semantics.
- Demo risk sizing includes round-trip transaction costs and symbol-specific tick/value/lot constraints.
- Added mandatory protection/SL enforcement, kill switch, quote-aware spread/slippage hooks, BrokerParity checks and regression tests.
- Mobile execution mode is explicit `DEMO / PAPER ↔ MT5 LIVE`, with confirmation before Live and lockout while Auto-Trading is running.
- Live mode changes are server-confirmed; failed updates restore the last confirmed mode.

### P6.3 — Mobile Execution & Account Status
- Added `Execution & Account Status` card to Auto Trading.
- Displays execution mode, engine state, MT5 connection state and latest Risk Gate result.
- LIVE mode displays MT5 login/server, balance, equity, margin, free margin, leverage and margin level.
- Account telemetry is best-effort and must not block Auto-Trading when MT5 is unavailable.
- Verification: `:composeApp:compileDebugKotlinAndroid` and `:composeApp:testDebugUnitTest` PASS.

### Live Voice Readiness / Session Stability
- Live UI exposes explicit `READY` state.
- Auto greeting is event-driven from the actual Live session READY event rather than a fixed timer.
- Greeting is sent once per WebSocket lifecycle and is cancelled if the lifecycle changes, preventing stale-session speech.
- Session resumption handle is updated and reused across controlled WebSocket resets.
- Pre-READY audio is buffered/handled explicitly; stale audio is dropped when the session lifecycle changes.
- Live connection diagnostics include READY latency and session-resumption events.

## Documentation Synchronization Status

| Artifact | Status on 2026-08-24 | Action |
|---|---|---|
| `.obsidian-wiki/00_System/log.md` | Partially current | Updated with consolidated recent phases |
| `.obsidian-wiki/00_System/project_progress_summary.md` | Previously stale (last stated 2026-08-10) | Rebuilt with P3/P5/P6 + Live status |
| `README.md` | Partially current | Added missing P3/P5/Live history and normalized P6 history |
| `composeApp/log.md` | Missing recent entries / encoding drift | Re-synchronized with recent work and verification |
| `log.txt` | Missing recent entries / encoding drift | Re-synchronized with recent work and verification |

## Rule Going Forward

Every completed development phase must update all four operational records:
1. `.obsidian-wiki/00_System/log.md`
2. `README.md` when architecture/features/status change
3. `composeApp/log.md` for mobile implementation and verification
4. `log.txt` for concise chronological change + build/test result
