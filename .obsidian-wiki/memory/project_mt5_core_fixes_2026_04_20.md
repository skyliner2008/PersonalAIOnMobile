---
name: mt5-core-server fixes 2026-04-20
description: Fixed 8 bugs between Kotlin client (PersonalAIBot) and Node/Python mt5-core-server to make trading actually work end-to-end.
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
Fixed the mt5-core-server integration with PersonalAIBot (Kotlin/KMP app).

**Key contract decisions (non-obvious, worth remembering):**

1. `/api/mt5/order` accepts BOTH `side` and legacy `action` fields, case-insensitive, with `long→buy` / `short→sell` aliases. Reason: the Kotlin client at `TradingToolExecutor.executeMt5Order` still sends `{"action":"BUY"}` (uppercase). The Node route normalizes to lowercase `side` in the zod transform and forwards both fields to the Python bridge so either side of the wire is satisfied. Don't tighten `orderSchema` back to `z.enum(['buy','sell'])` without also updating Kotlin.

2. `/api/mt5/close` allows empty `ticket` as long as `symbol` is present (refine rule). Kotlin sends `ticket: ""` when closing by symbol.

3. The Node server (`mt5SnapshotHub.normalizeTradeSide`) and the Python bridge (`_enrich_trade_row`) both add a readable `side: "BUY"|"SELL"` field to every position/order/deal row. MT5 natively returns `type: 0/1`, which made the Kotlin UI display "0"/"1". Both layers do this defensively; removing one still leaves the other.

4. `ensure_mt5()` now caches init state with a lock (`_MT5_STATE_LOCK`, TTL 30s on success / 3s on fail). Do NOT revert this; `mt5.initialize()` on every request was causing lag under WS fan-out.

5. Python bridge `send_order`/`close_position` use `_try_order_send_with_filling()` to cycle `ORDER_FILLING_IOC → FOK → RETURN`. Retcode 10030 = "Unsupported filling mode" is the signal to try the next mode. Broker-specific — don't hardcode a single filling mode.

6. Added `/candles` + `/rates` route in the Python bridge (`list_candles` using `mt5.copy_rates_from_pos`). Before this, `/api/mt5/tracking/run-once` and `/api/mt5/candles` always returned 502.

**Why:** Orders from the mobile app were silently failing with 400 zod errors; tracking was broken because the bridge had no candles endpoint.

**How to apply:** When adding new trading tools, keep the Node zod schema tolerant of Kotlin-style camelCase/uppercase fields and always verify the Python bridge has a matching route handler. Run `./node_modules/.bin/tsc -p tsconfig.json` and `python3 -m py_compile bridge/mt5_bridge.py` before claiming a fix is done.

**Follow-up fix (same day): BUY-shows-as-SELL in mobile UI.**
`Mt5TerminalService.parseTrades` was reading `str(o, "type", "side")` — `type` was tried *before* `side`, and MT5 returns `type` as integer (`0` for BUY / `1` for SELL). The Kotlin `str()` helper just stringified it, so every position landed with `side="0"` or `side="1"`. The new UI's `row.side.equals("BUY", ignoreCase=true)` check then fell through to the SELL branch for everything. Replaced with a dedicated `resolveSide(o)` helper that prefers `side` (string), then `action`, then numeric `type` parity (even=BUY, odd=SELL — covers pending order codes 2..5 too). The DB schema uses `INSERT OR REPLACE` keyed on `(record_type, ticket, event_time)`, so old "0"/"1" rows get overwritten on the next sync — no migration needed.
