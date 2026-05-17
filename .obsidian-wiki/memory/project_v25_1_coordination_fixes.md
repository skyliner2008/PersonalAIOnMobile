---
name: V25.1 Real-Time Coordination Fixes 2026-05-10
description: 7 patches for V25 wall-state churn + slTpAgent wrong-side + AI pipeline mid-channel waste (driver log XBTUSD 13:05)
type: project
originSessionId: e3a6ebe4-dc68-4a7d-8d0d-4e7aa96e98cd
---
V25.1 patched 2026-05-10 (driver: log XBTUSD 13:05–13:21 — state machine churn, PB1 SKIP loop, slTpAgent rejected every cycle, ~168s AI pipeline running on mid-channel).

**Why:** V25 design was correct but coordination layer leaked — state machine flip-flopped between two close walls, PlaybookSelector picked nearest wall regardless of stars, slTpAgent received `biasSide` instead of FINAL `side`, AI pipeline ran in full even when no wall in REACT/CONFIRM.

**What V25.1 changes:**
1. `WallStateMachine.ts` — REACT no longer exits via tick-distance (only via BREAK_OUT or M1 bar-close CONFIRM), APPROACH→IDLE hysteresis 1.5x→2.0x + dwell ≥1.5s, wall-identity swap refuses weaker wall when current wall still close, churn telemetry warns >6 transitions/10s
2. `WallProximityIndex.ts` — new `queryStrongest(price, radius, minStars)` returns max-stars wall in radius
3. `playbooks/index.ts` — if nearest wall fails minWallStars, fallback to `queryStrongest()` within 2.5× reactZone
4. `slTpAnalyst` call site (`autoTradingService.ts:1845`) — use FINAL `side` not `biasSide` (logs warning when differs)
5. `autoTradingService.ts:1820` — ATR fallback `rawSl/rawTp` uses `rawDirSide = side ?? biasSide`
6. `autoTradingService.ts:1106` — **V25 Gate**: skip entire AI pipeline (Analyst+RiskOfficer+Execution) when no wall in REACT/CONFIRM. Saves ~150s + LLM tokens. Honors `cfg.adaptive.v25.skipMidChannelLLM` (default true)
7. `autoTradingService.ts:1803` — log `⚠️ COUNTER-TREND override` + tag `[COUNTER_TREND]` when Final Side opposes deterministic Bias

**How to apply (when verifying):** restart server with all patches, watch ≥30min log; check `/api/v25/health` `wallStateMachine` for fewer transitions; check `/api/v25/candidates/XBTUSD` for actual PB1 plans (not "stars 1 < min 2"); confirm mid-channel cycles show `[V25 Gate] AI pipeline skipped` instead of 168s pipelines; confirm bias contradictions surface as warning.

**Rollback:** patch #6 toggleable via `adaptive.v25.skipMidChannelLLM=false`; patches #1-5 require git revert.

**Docs updated:** `.obsidian-wiki/07_Trading_Intelligence/25_RealTimeWallEngine_V25.md` §V25.1 + `README.md` §V25.1.
