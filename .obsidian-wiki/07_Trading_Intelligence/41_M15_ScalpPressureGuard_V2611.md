# V26.11 M15 Scalp Pressure Guard

Date: 2026-05-19

## Audit

Latest `A_SCALPING_XAUUSD` order:

- `2026-05-19 00:30:08` Bangkok time
- BUY `SCALPING`, ticket `157438171`
- Engine entry reference: `4549.73`
- Broker fill: `4553.97`
- SL `4546.16`, TP `4556.44`

The decision was not an `SMC_FVG_SCALP` or `SMC_FVG_MAGNET_SCALP` proof trade. It was generic MTF `SCALPING` from the deterministic score:

- `buy=42`
- `sell=23.3`
- `strategy=SCALPING`

M15 context at the same time still showed price inside opposing Bearish OB pressure:

- `Price testing Bearish OB (4529.88-4555.08)`
- M30 also showed Bearish OB/FVG above price.

After the BUY was open, later cycles began producing SELL intent, but the risk gate blocked it:

- `anti-hedge: cannot open SELL while holding BUY`

## Fix

Generic `SCALPING` now checks M15 SMC pressure before it can open a market order:

- BUY is blocked when M15 has Bearish OB/FVG pressure or bearish sweep+FVG pressure.
- SELL is blocked when M15 has Bullish OB/FVG pressure or bullish sweep+FVG pressure.

The dedicated FVG playbooks are not changed:

- `SMC_FVG_SCALP` still uses the strict FVG-fill proof gate.
- `SMC_FVG_MAGNET_SCALP` still uses wall reclaim toward unmitigated M15 FVG.

Config toggle:

- `adaptive.enableScalpM15PressureGuard`
- Default: `true`

## Files

- `mt5-core-server/src/services/autoTradingService.ts`
- `mt5-core-server/src/services/auto/core/ZoneAwareGate.ts`
- `mt5-core-server/src/services/auto/core/ZoneAwareGate.test.ts`
- `mt5-core-server/src/services/auto/types.ts`
- `mt5-core-server/src/services/auto/core/PersistenceService.ts`
- `README.md`
- `mt5-core-server/README.md`
- `.obsidian-wiki/07_Trading_Intelligence/Trading_Intelligence_MOC.md`
