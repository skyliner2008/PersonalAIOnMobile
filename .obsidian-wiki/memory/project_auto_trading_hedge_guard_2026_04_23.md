---
name: Auto-Trading hedge guard 2026-04-23
description: 5 server-side fixes — cooldown, hard cap, ticket parser, TF-switch guard, audit log — in mt5-core-server AutoTradingService
type: project
originSessionId: ad6e443c-5527-418f-ac5d-f87745a33775
---
Added 5 defensive guards to `mt5-core-server/src/services/autoTradingService.ts` (+ new adaptive config fields in `auto/types.ts`):

1. **Hedge/scale-in cooldown** — `adaptive.hedgeCooldownMs` (default 180_000) and `scaleInCooldownMs` (300_000). Tracked by `lastHedgeAt` / `lastScaleInAt` maps (stamped only on successful fill). Prevents stacking defense orders every cycle.
2. **Hard cap** — `adaptive.hardCapPositionMultiplier` (2.0 × maxOpenPositions across account) and `hardCapPerSymbolMultiplier` (1.5 × maxSameSymbolPositions). Past the cap, HEDGE/SCALE_IN are refused; only shrink actions (CLOSE/REDUCE/PARTIAL_CLOSE) proceed. Evaluated at top of `planMarketAwareManagement`.
3. **Ticket-parser broadening + SKIP-branch execution** — regex now accepts `ticket|position|order|pos.|#` and close-verbs (clos|terminat|liquidat|exit|cut|remove|drop). If AI outputs `action=SKIP` but management is CLOSE/REDUCE/PARTIAL_CLOSE, the run-cycle loop now executes that management before the `continue`, logs `🩺 SKIP-branch …`, and records a `MGMT-SKIP-*` row.
4. **TF-switch guard** — `adaptive.mtfDowngradeBlock=true`, `mtfDowngradeConfluenceMin=80`. When H4 and H1 agree on a non-neutral bias with confluence ≥ threshold, any AI suggestion to switch to a sub-H1 timeframe (M30/M15/M5/M1) is ignored — logs `🛑 TF-switch BLOCKED`.
5. **Trade Manager audit log** — `runManage` now prints a per-position audit line (symbol/ticket/side/vol/pnl/r/sl, BE✓/BE<trigger, TRAIL✓/TRAIL<trigger) and explicitly states when `enableLiveTrading=false` suppresses the BE/Trail phase.

**Why:** Log review on 2026-04-23 showed cycles #218→#221: AI SKIP wanting to close ticket #142539069 was lost, then M15 LTF downgrade enabled counter-trend BUY hedges while H4/H1 were BEAR@95, stacking positions 13→15 past maxOpen=5 and eroding equity.

**How to apply:** Any future work on server-side auto-trading defense logic should respect these guard flags. When adding a new action that opens a position, check `atHardCap` and the relevant cooldown; stamp the cooldown only on confirmed fill. Config defaults live in `defaultConfig.adaptive` inside `autoTradingService.ts`.

---

**Regression patches (same day, cycles #420→#421 log):**

6. **Fix A — hard-cap leak via HOLD/defense-override fall-through.** `atHardCap` originally only short-circuited inside the HEDGE/SCALE_IN branches. When those refused, plan fell through to the final HOLD return which still set `allowOverLimitDefense: adaptive.allowOverLimitDefense !== false && isAiDefense`, and the main-cycle gate then happily opened an AUTO_BREAKOUT SELL at 14→15 positions. Introduced `const defOverride = (flag) => atHardCap ? false : flag` helper and wrapped every `allowOverLimitDefense:` assignment in HEDGE, SCALE_IN, and HOLD branches. CLOSE/REDUCE/PARTIAL_CLOSE intentionally NOT wrapped (they shrink exposure and must still bypass gates).

7. **Fix B — TF-switch guard too strict (AND→OR of dominant H1).** Original guard required `h4.confluence >= confMin && h1.confluence >= confMin`. In the log H1 was 95 but H4 74 (< 80) → guard didn't trip → M15 switch was honoured. Replaced with `htfAligned = bothAligned || h1Dominant` where `h1Dominant = h1.confluence >= max(confMin, 85) && (no h4 OR h4 neutral OR h4 same bias)`. Log now prints which trigger fired (`H4+H1 both strong` vs `H1 dominant (…≥85)`).

8. **Fix C — `account.openPositions=0` fallback.** Broker occasionally returns `positions_total=0` while there are clearly N live tickets, which silently disabled the account-level hard cap. Added `totalOpenPositions: number = 0` param to `planMarketAwareManagement`; caller at `runCycle` passes `positions.length`. `effectiveOpenPositions = Math.max(account.openPositions || 0, totalOpenPositions || 0, cluster.positions.length || 0)` — account cap can no longer be smuggled past by a stale /account payload.

**Why (A/B/C):** The 2026-04-23 follow-up log proved Fix 2/3/4 worked individually but the cap still leaked: (A) HOLD fall-through, (B) AND condition missed an H1-dominant scenario, (C) broker returned 0 for openPositions so `atHardCapAccount` was always false.

**How to apply (A/B/C):** Defense code must now go through `defOverride()` for any `allowOverLimitDefense` assignment — never set the flag raw. TF-switch logic must accept the H1-dominant branch (don't add back a strict AND). When checking account-level caps, always use `effectiveOpenPositions` — never trust `account.openPositions` alone.

**Known sandbox limitation:** `npx tsc --noEmit` from the Linux workspace sandbox returns false positives on unrelated files (db.ts:216, agentOrchestrator.ts:151, risk.ts:49, types.ts:168, vectorStore.ts:84, bridgeClient.ts:109, autoTradingService.ts:888) due to stale-mount truncation — verify on Windows Read instead of the bash sandbox.

---

**Follow-up polish (cycles #437→#438 log):**

9. **Log message consistency.** HEDGE/SCALE_IN "🚫 refused … hard cap" messages were still using `account.openPositions` while HOLD used `effectiveOpenPositions` — reading the log was confusing (one said `acct=0/10` next to another saying `acct=10/10`). All three refuse/HOLD messages now use `effectiveOpenPositions` uniformly.

10. **NO_JOURNAL_MATCH backfill (Fix 6).** Orphan broker positions (opened before engine start, or whose journal row was lost) previously logged `NO_JOURNAL_MATCH` and got skipped entirely by BE/Trail — so the most profitable legacy tickets never received management. `runManage` now detects orphans (positions whose ticket has no matching `openJournal` row) and inserts a synthetic OPEN row using broker `priceOpen`/`sl` before the audit pass. Strategy tagged `BACKFILL`, decisionId `BACKFILL-<ticket>-<timestamp>`. Refuses to backfill when broker `sl=0` (would invent a fake risk unit and make BE trigger on fabricated r) — logs `⚠️ Cannot backfill journal for orphan …`. Emitted line `🔧 Backfilled journal for orphan #… entry=… sl=… tp=… (risk=…)` confirms success.

**Why (9/10):** Cycle #437/#438 log proved A/B/C working but surfaced two residual issues: (9) inconsistent hard-cap counts in the log made it read like the cap was partly broken; (10) 3 profitable legacy XAUUSD SELL tickets (pnl 120-140 each) kept showing `NO_JOURNAL_MATCH` → no BE/Trail → exposed to full retracement without protection.

**How to apply (9/10):** When adding new hard-cap log messages, always use `effectiveOpenPositions` (never `account.openPositions` raw). When extending Trade Manager, run the backfill pass before any audit/BE/Trail logic so orphan positions participate in management.
