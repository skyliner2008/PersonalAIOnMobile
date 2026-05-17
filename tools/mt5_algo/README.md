# MT5 Algorithmic Trading Starter

This folder is a production-oriented starter kit for building an MT5 algo system.

## What is included

- `MQL5/Experts/JarvisAlgoStarter.mq5`
  - Example EA with:
    - EMA trend filter (fast/slow)
    - ATR-based stop loss and take profit
    - Risk-based position sizing
    - Max spread filter
    - One-position-per-symbol guard

## Quick start

1. Copy `MQL5/Experts/JarvisAlgoStarter.mq5` to your MT5 data folder:
   - `MQL5/Experts/`
2. Open MetaEditor and compile the EA.
3. In MT5 Strategy Tester:
   - Select symbol and timeframe.
   - Run backtest with "Every tick based on real ticks".
   - Start with default inputs.

## Recommended development process

1. Validate strategy logic on one symbol and one timeframe.
2. Add hard risk controls first:
   - Daily loss cap
   - Max consecutive losses
   - News blackout window
3. Optimize in small ranges only (avoid overfitting).
4. Walk-forward test with out-of-sample periods.
5. Run forward demo for at least 2-4 weeks before live.

## Next upgrades

- Multi-symbol portfolio mode
- Session filter (London/NY overlap)
- Economic calendar blocker
- Trade journaling to CSV
- Python analytics pipeline (equity curve, Monte Carlo, risk-of-ruin)

