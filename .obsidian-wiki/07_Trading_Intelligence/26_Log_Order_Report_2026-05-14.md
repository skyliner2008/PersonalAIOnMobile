# Log Order Report 2026-05-14

Source: `C:\Users\JOJO\AndroidStudioProjects\PersonalAIBot\log.txt`

ช่วงข้อมูลใน log: `2026-05-13 23:10:05` ถึง `2026-05-14 01:26:30`

## Executive Summary

- จำนวนรอบ `AutoEngine Cycle`: 97 รอบ
- เปิดออเดอร์สำเร็จ: 3 รอบ / 3 orders
- ถูกบล็อกหรือ skip ก่อนส่ง order: 94 รอบ
- ไม่พบ broker rejection หลัง `Sending Order`; ทุก `Order Result` ที่ถูกส่งออกไปสำเร็จด้วย `success=true`, `retcode=10009`
- พบ `Closed Trade` ที่ระบบ classify แล้ว: 9 tickets, รวมสุทธิ `+45.45`
- ใน log ไม่มี label ปิดออเดอร์แบบ `(SL)` โดยตรง มีเฉพาะ `(closed)` และ `(BE)` จึงแยกผลตาม tag ที่ log ระบุจริง และระบุ loss/stop-side แยกเป็นข้อสังเกต

## Orders That Were Opened

| Time | Ticket | Side | Fill | Vol | SL | TP | RRR | Situation | Status in log |
|---|---:|---|---:|---:|---:|---:|---:|---|---|
| 2026-05-13 23:12:56 | 154708894 | SELL | 4699.66 | 0.05 | 4704.68 | 4694.25 | 1.50 | RANGING / RANGE | Later disappeared from open positions, but no usable closing history found |
| 2026-05-14 00:40:50 | 154745446 | SELL | 4692.13 | 0.05 | 4695.37 | 4680.66 | 2.50 | TRENDING_DOWN / TREND_FOLLOW | BE set at 4691.38, later no usable closing history found |
| 2026-05-14 01:09:59 | 154754840 | SELL | 4690.59 | 0.05 | 4712.37 | 4637.46 | 2.50 | TRENDING_DOWN / TREND_FOLLOW | Still shown in manager audit at end of log |

### Opened Order Notes

- `154708894`: min floating P/L in audit `-21.60`, max `+2.90`, last audit before disappearing `-21.60`. From `2026-05-13 23:24:10` onward the manager repeatedly says no usable closing history was found, so realized P/L is not available in this log.
- `154745446`: min floating P/L `-10.85`, max `+33.15`. Break-even was set at `2026-05-14 00:54:26` to `4691.38`. Last audit before disappearing was `+11.35`, but realized P/L is not available because closing history was not found.
- `154754840`: min floating P/L `-16.20`, max `+6.10`, last audit at log end `-0.60`; no close classification yet.

## Blocked / Skipped Signals By Cause

| Cause | Count | Situation |
|---|---:|---|
| AI unavailable + EA fallback conditions not met | 37 | Mostly `RANGING/RANGE` and `TRENDING_DOWN/TREND_FOLLOW`; no actionable deterministic fallback |
| V25-only direct order disabled: V25 state stale | 25 | V24 plan delegated to V25, but V25 resistance state was too old |
| MTF zone gate veto: BUY blocked in PREMIUM zone | 15 | BUY was blocked because H4/H1 were both `PREMIUM` |
| Sequential entry blocked: near duplicate entry | 12 | New SELL entry too close to existing open position; min spacing required was 5 |
| V25-only direct order disabled: APPROACH distance too far | 2 | V25 approach distance exceeded threshold |
| V25-only direct order disabled: state IDLE / not qualified | 2 | V25 state was not `REACT`, `CONFIRM`, `RETEST`, or qualified `APPROACH` |
| V25-only direct order disabled: wall stars too low | 1 | Resistance wall stars `1 < 3` |

Total skipped by AutoEngine gate: 94.

## Blocked / Skipped Signals By Market Situation

| Situation | Count | Result |
|---|---:|---|
| TRENDING_DOWN / TREND_FOLLOW / SKIP | 43 | Blocked/skipped |
| RANGING / RANGE / SKIP | 31 | Blocked/skipped |
| TRENDING_DOWN / SMC_FVG_CONTINUATION / SKIP | 13 | Blocked/skipped |
| RANGING / SMC_FVG_CONTINUATION / SKIP | 7 | Blocked/skipped |
| TRENDING_DOWN / TREND_FOLLOW / SELL | 2 | Opened |
| RANGING / RANGE / SELL | 1 | Opened |

## V25 PlaybookSelector Skips

These are V25 wall/playbook skip events outside the main `AutoEngine Cycle` count.

| Cause | Count |
|---|---:|
| Playbook filters failed: not sweep pattern, missing OB+FVG, RRR below min, no fresh opposite FVG, target/stop not executable, or TF insufficient | 4 |
| AI Bias Gate: V25 BUY blocked because AI pipeline says SKIP while bias is BEAR / regime TRENDING_DOWN | 2 |
| Decision gate: no directional mandate stale | 1 |
| Decision gate: AutoEngine SKIP gate / MTF zone gate veto | 1 |
| Plan Risk Gate: SL distance below XAUUSD minimum | 1 |
| Missed by drift: live RRR fell below minimum after price drift | 1 |

Total V25 PlaybookSelector skips: 10.

## Closed Trade P/L

The following `Closed Trade` records were classified in this log. Some tickets were already closed before this captured window and only their post-mortem/classification appears here.

### BE Tagged

| Time | Ticket | Result | Profit | Net | R | Tag |
|---|---:|---|---:|---:|---:|---|
| 2026-05-13 23:44:14 | 154593120 | WIN | 4.00 | 4.00 | 0.12 | BE |
| 2026-05-14 00:05:47 | 154615087 | WIN | 10.80 | 10.80 | 0.53 | BE |
| 2026-05-14 00:31:52 | 154638894 | LOSS | -14.20 | -14.20 | -1.00 | BE |
| 2026-05-14 00:40:53 | 154642596 | WIN | 4.00 | 4.00 | 0.24 | BE |
| 2026-05-14 01:20:30 | 154676387 | WIN | 4.00 | 4.00 | 0.17 | BE |

BE subtotal: 5 trades, 4 WIN / 1 LOSS, net `+8.60`, total R `+0.06`.

### Closed Tagged

| Time | Ticket | Result | Profit | Net | R | Tag |
|---|---:|---|---:|---:|---:|---|
| 2026-05-13 23:32:12 | 154541965 | LOSS | -21.55 | -21.55 | -0.22 | closed |
| 2026-05-13 23:35:12 | 154570739 | WIN | 45.90 | 45.90 | 2.50 | closed |
| 2026-05-14 00:18:19 | 154628834 | WIN | 15.20 | 15.20 | 1.22 | closed |
| 2026-05-14 00:18:19 | 154622482 | LOSS | -2.70 | -2.70 | 0.21 | closed |

Closed subtotal: 4 trades, 2 WIN / 2 LOSS, net `+36.85`, total R `+3.71`.

## SL / Stop-Side Observation

- No explicit `(SL)` close tag was found in this log.
- If filtering by negative `Profit` as stop-side or loss exits, loss tickets are:
  - `154541965`: `-21.55`, R `-0.22`, tag `closed`
  - `154622482`: `-2.70`, R `0.21`, tag `closed`
  - `154638894`: `-14.20`, R `-1.00`, tag `BE`
- Loss subtotal: 3 trades, net `-38.45`.
- Because the log does not include the broker close reason/deal reason, these should not be labeled as confirmed SL unless the MT5 history deal reason is available.

## Key Findings

1. There was no order-send failure. The system either opened successfully or blocked before sending.
2. The biggest blocker was model/fallback availability: `AI unavailable and EA Fallback conditions not met`, 37 times.
3. The second biggest blocker was V25-only gating, especially stale V25 state, 25 times.
4. The zone gate prevented 15 BUY attempts in PREMIUM H4/H1 context, which is consistent with avoiding buys into premium.
5. Sequential entry protection blocked 12 scale-in attempts because entries were too close to existing positions.
6. Two opened tickets, `154708894` and `154745446`, later disappeared from open positions but did not get usable closing history in the captured log; realized P/L for those opened orders cannot be trusted from this file alone.
7. The only opened ticket still visible at the end was `154754840`, floating about `-0.60` in the last audit.
