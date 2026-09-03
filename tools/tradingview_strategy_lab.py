#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalAIBot — TradingView Strategy Lab

Research-only harness. It NEVER writes live/production strategy parameters.

Purpose:
  1) Acquire historical OHLCV from TradingView (optional tvdatafeed dependency)
     or load a TradingView-exported CSV.
  2) Run the current strategy families independently with parameter grids.
  3) Evaluate IS / validation / OOS, costs, drawdown, expectancy, PF, Sharpe,
     trade count, long/short asymmetry, regime slices and parameter stability.
  4) Produce machine-readable CSV/JSON so Evolution/Optimization can be judged
     against independent evidence instead of optimizing a single in-app metric.

Examples:
  python tools/tradingview_strategy_lab.py --symbol OANDA:XAUUSD --interval 15m --bars 5000
  python tools/tradingview_strategy_lab.py --csv xauusd_15m.csv --symbol XAUUSD --interval 15m
  python tools/tradingview_strategy_lab.py --symbol OANDA:XAUUSD --interval 15m --bars 5000 --quick

Optional dependency for direct TradingView acquisition:
  pip install tvdatafeed pandas numpy

CSV accepted columns (case-insensitive):
  timestamp/time/datetime, open, high, low, close, volume
Timestamp may be epoch seconds/milliseconds or an ISO datetime.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Callable, Iterable

import numpy as np
import pandas as pd


LAB_VERSION = "TV-Strategy-Lab-1.0"


@dataclass(frozen=True)
class Config:
    initial_balance: float = 10_000.0
    risk_pct: float = 0.01
    spread: float = 0.20
    commission_pct: float = 0.0
    slippage: float = 0.0
    min_trades: int = 30


@dataclass(frozen=True)
class Param:
    name: str
    value: float | int


@dataclass
class Trade:
    side: str
    entry_i: int
    exit_i: int
    entry: float
    exit: float
    pnl: float
    pnl_r: float
    reason: str


# --------------------------- indicators ---------------------------------

def ema(s: pd.Series, n: int) -> pd.Series:
    return s.ewm(span=n, adjust=False, min_periods=n).mean()


def rsi(s: pd.Series, n: int) -> pd.Series:
    d = s.diff()
    up = d.clip(lower=0).ewm(alpha=1/n, adjust=False, min_periods=n).mean()
    dn = (-d.clip(upper=0)).ewm(alpha=1/n, adjust=False, min_periods=n).mean()
    rs = up / dn.replace(0, np.nan)
    return 100 - (100 / (1 + rs))


def atr(df: pd.DataFrame, n: int = 14) -> pd.Series:
    prev = df.close.shift(1)
    tr = pd.concat([
        df.high - df.low,
        (df.high - prev).abs(),
        (df.low - prev).abs(),
    ], axis=1).max(axis=1)
    return tr.ewm(alpha=1/n, adjust=False, min_periods=n).mean()


def bollinger(s: pd.Series, n: int, mult: float):
    mid = s.rolling(n, min_periods=n).mean()
    sd = s.rolling(n, min_periods=n).std(ddof=0)
    return mid + mult * sd, mid - mult * sd


def adx(df: pd.DataFrame, n: int = 14):
    up = df.high.diff()
    dn = -df.low.diff()
    plus = pd.Series(np.where((up > dn) & (up > 0), up, 0.0), index=df.index)
    minus = pd.Series(np.where((dn > up) & (dn > 0), dn, 0.0), index=df.index)
    a = atr(df, n)
    pdi = 100 * plus.ewm(alpha=1/n, adjust=False).mean() / a.replace(0, np.nan)
    mdi = 100 * minus.ewm(alpha=1/n, adjust=False).mean() / a.replace(0, np.nan)
    dx = 100 * (pdi - mdi).abs() / (pdi + mdi).replace(0, np.nan)
    return dx.ewm(alpha=1/n, adjust=False, min_periods=n).mean()


# ------------------------- signal families -------------------------------
# Signals are evaluated on the CLOSED bar and executed on the next bar open.
# This is deliberately conservative and avoids look-ahead from close execution.


def strategy_signal(df: pd.DataFrame, kind: str, p: dict) -> pd.Series:
    c, h, l = df.close, df.high, df.low
    out = pd.Series(0, index=df.index, dtype=int)
    k = kind.upper()

    if k == "MOM":
        n = int(p["lookback"]); threshold = float(p["threshold"])
        roc = c.pct_change(n) * 100
        out[roc > threshold] = 1
        out[roc < -threshold] = -1

    elif k == "TR":
        fast = int(p["fast"]); slow = int(p["slow"]); adx_n = int(p["adx"]); min_adx = float(p["min_adx"])
        ef, es = ema(c, fast), ema(c, slow)
        a = adx(df, adx_n)
        out[(ef > es) & (a >= min_adx)] = 1
        out[(ef < es) & (a >= min_adx)] = -1

    elif k == "REV":
        n = int(p["rsi"]); low_r = float(p["low"]); high_r = float(p["high"]); bb_n = int(p["bb"]); mult = float(p["mult"])
        rr = rsi(c, n); upper, lower = bollinger(c, bb_n, mult)
        out[(rr < low_r) & (c <= lower)] = 1
        out[(rr > high_r) & (c >= upper)] = -1

    elif k == "DC":
        n = int(p["lookback"])
        upper = h.shift(1).rolling(n, min_periods=n).max()
        lower = l.shift(1).rolling(n, min_periods=n).min()
        out[c > upper] = 1
        out[c < lower] = -1

    elif k == "52H":
        n = int(p["lookback"]); prox_buy = float(p["buy"]); prox_sell = float(p["sell"])
        hi = h.shift(1).rolling(n, min_periods=n).max()
        lo = l.shift(1).rolling(n, min_periods=n).min()
        out[c / hi >= prox_buy] = 1
        out[c / lo <= prox_sell] = -1

    elif k == "E":
        n = int(p["ema"]); atr_n = int(p["atr"]); mult = float(p["range_mult"])
        e = ema(c, n); a = atr(df, atr_n)
        rng = h - l
        out[(c > e) & (rng > a * mult)] = 1
        out[(c < e) & (rng > a * mult)] = -1

    elif k == "UT":
        atr_n = int(p["atr"]); mult = float(p["mult"])
        a = atr(df, atr_n)
        mid = (h + l) / 2
        upper = mid + mult * a
        lower = mid - mult * a
        # Simple closed-bar trend state; deliberately transparent for research.
        state = pd.Series(0, index=df.index, dtype=int)
        for i in range(1, len(df)):
            if not np.isfinite(upper.iloc[i]) or not np.isfinite(lower.iloc[i]):
                continue
            if c.iloc[i] > upper.iloc[i-1]: state.iloc[i] = 1
            elif c.iloc[i] < lower.iloc[i-1]: state.iloc[i] = -1
            else: state.iloc[i] = state.iloc[i-1]
        out[(state == 1) & (state.shift(1) != 1)] = 1
        out[(state == -1) & (state.shift(1) != -1)] = -1

    elif k == "3BR":
        n = int(p["lookback"]); body = float(p["body"])
        rng = (h - l).replace(0, np.nan)
        bullish = (c > df.open) & ((c - df.open) / rng >= body)
        bearish = (c < df.open) & ((df.open - c) / rng >= body)
        bull_run = bullish.rolling(n, min_periods=n).sum() == n
        bear_run = bearish.rolling(n, min_periods=n).sum() == n
        out[bull_run] = 1
        out[bear_run] = -1

    else:
        raise ValueError(f"Unknown strategy: {kind}")
    return out


def compose_signal(df, mom_params, e_params, mode):
    mom = strategy_signal(df, "MOM", mom_params)
    e = strategy_signal(df, "E", e_params)
    mode = mode.upper()
    if mode == "MOM": return mom
    if mode == "E": return e
    if mode == "HYBRID_MOM_LONG_E_SHORT":
        out = pd.Series(0, index=df.index, dtype=int)
        out[(mom == 1) & (e != -1)] = 1
        out[(e == -1) & (mom != 1)] = -1
        return out
    raise ValueError(f"Unknown composer mode: {mode}")


GRIDS: dict[str, dict[str, list]] = {
    "MOM": {"lookback": [5, 8, 10, 14, 20, 30], "threshold": [0.15, 0.25, 0.40, 0.60, 0.90]},
    "TR": {"fast": [10, 20, 30], "slow": [50, 100, 200], "adx": [14, 20], "min_adx": [18, 22, 25, 30]},
    "REV": {"rsi": [7, 10, 14], "low": [20, 25, 30], "high": [70, 75, 80], "bb": [14, 20], "mult": [1.8, 2.0, 2.2]},
    "DC": {"lookback": [10, 20, 30, 50, 80]},
    "52H": {"lookback": [250, 500, 1000, 1500, 2000], "buy": [0.985, 0.99, 0.995], "sell": [0.985, 0.99, 0.995]},
    "E": {"ema": [20, 50, 100], "atr": [14, 20], "range_mult": [1.0, 1.2, 1.5, 2.0]},
    "UT": {"atr": [7, 10, 14, 20], "mult": [1.0, 1.5, 2.0, 2.5, 3.0]},
    "3BR": {"lookback": [2, 3, 4], "body": [0.45, 0.55, 0.65, 0.75]},
}


# ------------------------------ backtest ---------------------------------

def simulate(df: pd.DataFrame, sig: pd.Series, cfg: Config, start: int, end: int,
             sl_atr: float, tp_atr: float) -> list[Trade]:
    a = atr(df, 14)
    trades: list[Trade] = []
    balance = cfg.initial_balance
    i = max(start, 1)
    while i < end - 1:
        s = int(sig.iloc[i])
        if s == 0 or not np.isfinite(a.iloc[i]):
            i += 1; continue
        entry = float(df.open.iloc[i + 1]) + (cfg.spread/2 if s == 1 else -cfg.spread/2)
        risk_dist = float(a.iloc[i] * sl_atr)
        if risk_dist <= 0 or not np.isfinite(risk_dist):
            i += 1; continue
        sl = entry - risk_dist if s == 1 else entry + risk_dist
        tp = entry + float(a.iloc[i] * tp_atr) if s == 1 else entry - float(a.iloc[i] * tp_atr)
        risk_money = balance * cfg.risk_pct
        qty = risk_money / risk_dist
        j = i + 1
        exit_price = float(df.close.iloc[end-1]); reason = "TIMEOUT"
        while j < end:
            row = df.iloc[j]
            hit_sl = row.low <= sl if s == 1 else row.high >= sl
            hit_tp = row.high >= tp if s == 1 else row.low <= tp
            if hit_sl or hit_tp:
                if hit_sl:
                    # Conservative gap-through handling.
                    exit_price = min(sl, row.open) if s == 1 else max(sl, row.open)
                    reason = "SL"
                else:
                    exit_price = tp; reason = "TP"
                break
            j += 1
        gross = qty * (exit_price - entry if s == 1 else entry - exit_price)
        commission = cfg.commission_pct * qty * (entry + exit_price)
        pnl = gross - commission - cfg.slippage * qty
        pnl_r = pnl / risk_money if risk_money else 0.0
        trades.append(Trade("BUY" if s == 1 else "SELL", i + 1, j, entry, exit_price, pnl, pnl_r, reason))
        balance += pnl
        i = j + 1
    return trades


def metrics(trades: list[Trade], initial: float) -> dict:
    if not trades:
        return {"trades": 0, "pf": 0.0, "expectancy_r": 0.0, "win_rate": 0.0, "net_pnl": 0.0, "max_dd_pct": 0.0, "sharpe": 0.0, "avg_r": 0.0}
    rs = np.array([t.pnl_r for t in trades], dtype=float)
    pnl = np.array([t.pnl for t in trades], dtype=float)
    wins = pnl[pnl > 0].sum(); losses = -pnl[pnl < 0].sum()
    pf = wins / losses if losses > 0 else (99.0 if wins > 0 else 0.0)
    equity = initial + np.cumsum(pnl)
    peak = np.maximum.accumulate(np.r_[initial, equity])
    curve = np.r_[initial, equity]
    dd = (peak - curve) / np.maximum(peak, 1e-9)
    sharpe = float(rs.mean() / rs.std(ddof=1) * math.sqrt(len(rs))) if len(rs) > 1 and rs.std(ddof=1) > 1e-12 else 0.0
    return {
        "trades": len(trades), "pf": float(pf), "expectancy_r": float(rs.mean()),
        "win_rate": float((pnl > 0).mean()), "net_pnl": float(pnl.sum()),
        "max_dd_pct": float(dd.max() * 100), "sharpe": sharpe, "avg_r": float(rs.mean()),
        "tp_count": int(sum(t.reason == "TP" for t in trades)),
        "sl_count": int(sum(t.reason == "SL" for t in trades)),
        "long_expectancy_r": float(np.mean([t.pnl_r for t in trades if t.side == "BUY"])) if any(t.side == "BUY" for t in trades) else 0.0,
        "short_expectancy_r": float(np.mean([t.pnl_r for t in trades if t.side == "SELL"])) if any(t.side == "SELL" for t in trades) else 0.0,
    }


def evaluate_candidate(df, kind, p, cfg, split, sl_atr, tp_atr):
    sig = strategy_signal(df, kind, p)
    parts = {}
    for name, (a, b) in split.items():
        parts[name] = metrics(simulate(df, sig, cfg, a, b, sl_atr, tp_atr), cfg.initial_balance)
    return parts


def param_product(grid: dict[str, list]) -> Iterable[dict]:
    keys = list(grid)
    for vals in __import__("itertools").product(*(grid[k] for k in keys)):
        p = dict(zip(keys, vals))
        if "fast" in p and "slow" in p and p["fast"] >= p["slow"]:
            continue
        if "low" in p and "high" in p and p["low"] >= p["high"]:
            continue
        if "buy" in p and "sell" in p and p["buy"] < p["sell"]:
            # For a proximity-to-high/low model, both are independent but avoid the inverted band.
            continue
        yield p


def score(m: dict) -> float:
    # Robust discovery score, not a production ranking formula.
    if m["trades"] == 0: return -1e9
    return (m["expectancy_r"] * 100 + min(m["pf"], 4) * 8 + min(m["sharpe"], 4) * 3
            - m["max_dd_pct"] * 0.35 + min(m["win_rate"], 0.8) * 5)


def neighborhood_stability(df, kind, best_p, cfg, split, sl_atr, tp_atr):
    # Local one-step perturbation using the existing grid neighborhood.
    grid = GRIDS[kind]
    base = evaluate_candidate(df, kind, best_p, cfg, split, sl_atr, tp_atr)["validation"]
    base_s = score(base)
    vals = []
    for name, options in grid.items():
        if name not in best_p: continue
        idx = min(range(len(options)), key=lambda i: abs(float(options[i]) - float(best_p[name])))
        for j in (idx - 1, idx + 1):
            if 0 <= j < len(options):
                q = dict(best_p); q[name] = options[j]
                if "fast" in q and "slow" in q and q["fast"] >= q["slow"]: continue
                m = evaluate_candidate(df, kind, q, cfg, split, sl_atr, tp_atr)["validation"]
                vals.append(score(m) >= base_s * 0.85)
    return float(sum(vals) / len(vals)) if vals else 0.0, len(vals)


# ------------------------------ data -------------------------------------

def load_csv(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    lower = {c.lower().strip(): c for c in df.columns}
    def col(*names):
        for n in names:
            if n in lower: return lower[n]
        raise ValueError(f"CSV missing column: one of {names}")
    out = pd.DataFrame({
        "timestamp": df[col("timestamp", "time", "datetime", "date")],
        "open": pd.to_numeric(df[col("open")]), "high": pd.to_numeric(df[col("high")]),
        "low": pd.to_numeric(df[col("low")]), "close": pd.to_numeric(df[col("close")]),
        "volume": pd.to_numeric(df[col("volume", "vol")], errors="coerce").fillna(0),
    })
    ts = out.timestamp
    if pd.api.types.is_numeric_dtype(ts):
        unit = "ms" if float(ts.max()) > 10_000_000_000 else "s"
        out.timestamp = pd.to_datetime(ts, unit=unit, utc=True)
    else:
        out.timestamp = pd.to_datetime(ts, utc=True)
    out.timestamp = out.timestamp.dt.as_unit("ns")
    out = out.dropna().sort_values("timestamp").drop_duplicates("timestamp").reset_index(drop=True)
    return out


def load_tv(symbol: str, interval: str, bars: int) -> pd.DataFrame:
    try:
        from tvDatafeed import TvDatafeed, Interval
    except Exception as e:
        raise RuntimeError("Direct TradingView download requires tvdatafeed. Run: pip install tvdatafeed pandas numpy") from e
    mapping = {
        "1m": Interval.in_1_minute, "3m": Interval.in_3_minute, "5m": Interval.in_5_minute,
        "15m": Interval.in_15_minute, "30m": Interval.in_30_minute, "1h": Interval.in_1_hour,
        "2h": Interval.in_2_hour, "4h": Interval.in_4_hour, "1d": Interval.in_daily,
    }
    if interval.lower() not in mapping:
        raise ValueError(f"Unsupported interval for tvdatafeed: {interval}")
    exchange, sym = (symbol.split(":", 1) if ":" in symbol else ("OANDA", symbol))
    tv = TvDatafeed()
    raw = tv.get_hist(sym, exchange, interval=mapping[interval.lower()], n_bars=bars)
    if raw is None or raw.empty:
        raise RuntimeError(f"TradingView returned no data for {exchange}:{sym} {interval}")
    raw = raw.reset_index()
    raw = raw.rename(columns={raw.columns[0]: "timestamp"})
    return load_csv_frame(raw)


def load_csv_frame(df: pd.DataFrame) -> pd.DataFrame:
    lower = {c.lower().strip(): c for c in df.columns}
    def col(*names):
        for n in names:
            if n in lower: return lower[n]
        raise ValueError(f"missing column {names}")
    out = pd.DataFrame({"timestamp": df[col("timestamp", "datetime", "time")],
                        "open": pd.to_numeric(df[col("open")]), "high": pd.to_numeric(df[col("high")]),
                        "low": pd.to_numeric(df[col("low")]), "close": pd.to_numeric(df[col("close")]),
                        "volume": pd.to_numeric(df[col("volume")], errors="coerce").fillna(0) if "volume" in lower else 0})
    out.timestamp = pd.to_datetime(out.timestamp, utc=True)
    return out.dropna().sort_values("timestamp").drop_duplicates("timestamp").reset_index(drop=True)


# -------------------------------- main -----------------------------------

def main():
    ap = argparse.ArgumentParser(description="PersonalAIBot TradingView Strategy Lab")
    ap.add_argument("--symbol", default="OANDA:XAUUSD")
    ap.add_argument("--interval", default="15m")
    ap.add_argument("--bars", type=int, default=5000)
    ap.add_argument("--csv")
    ap.add_argument("--out", default="strategy_lab_output")
    ap.add_argument("--strategies", default=",".join(GRIDS.keys()))
    ap.add_argument("--risk-pct", type=float, default=0.01)
    ap.add_argument("--spread", type=float, default=0.20)
    ap.add_argument("--commission-pct", type=float, default=0.0)
    ap.add_argument("--sl-atr", type=float, default=1.5)
    ap.add_argument("--tp-atr", type=float, default=2.0)
    ap.add_argument("--min-trades", type=int, default=30)
    ap.add_argument("--quick", action="store_true", help="Use a reduced parameter grid for a fast smoke test")
    args = ap.parse_args()

    cfg = Config(risk_pct=args.risk_pct, spread=args.spread, commission_pct=args.commission_pct, min_trades=args.min_trades)
    out_dir = Path(args.out); out_dir.mkdir(parents=True, exist_ok=True)
    df = load_csv(args.csv) if args.csv else load_tv(args.symbol, args.interval, args.bars)
    if len(df) < 500:
        raise RuntimeError(f"Not enough historical bars: {len(df)} (need >= 500)")

    # Chronological split: discovery train / validation / untouched holdout OOS.
    n = len(df); t = int(n * 0.60); v = int(n * 0.80)
    split = {"train": (0, t), "validation": (t, v), "holdout": (v, n)}
    strategies = [x.strip().upper() for x in args.strategies.split(",") if x.strip()]

    rows = []
    best_rows = []
    for kind in strategies:
        if kind not in GRIDS:
            print(f"WARN: unknown strategy {kind}; skipped", file=sys.stderr); continue
        grid = GRIDS[kind]
        if args.quick:
            grid = {k: v[:min(3, len(v))] for k, v in grid.items()}
        candidates = list(param_product(grid))
        best = None
        best_s = -1e18
        for p in candidates:
            parts = evaluate_candidate(df, kind, p, cfg, split, args.sl_atr, args.tp_atr)
            tr, va, ho = parts["train"], parts["validation"], parts["holdout"]
            s = score(tr)
            row = {"strategy": kind, "params": json.dumps(p, sort_keys=True), "train_score": s}
            for phase, m in parts.items():
                for k, val in m.items(): row[f"{phase}_{k}"] = val
            # Audit robustness for every candidate, not only the strategy winner.
            stability, neighborhood = neighborhood_stability(df, kind, p, cfg, split, args.sl_atr, args.tp_atr)
            row["neighborhood_stability"] = stability
            row["neighborhood_tests"] = neighborhood
            rows.append(row)
            if tr["trades"] >= args.min_trades and s > best_s:
                best_s = s; best = (p, parts)
        if best:
            p, parts = best
            stability, neighborhood = neighborhood_stability(df, kind, p, cfg, split, args.sl_atr, args.tp_atr)
            b = {"strategy": kind, "params": json.dumps(p, sort_keys=True), "neighborhood_stability": stability, "neighborhood_tests": neighborhood}
            for phase, m in parts.items():
                for k, val in m.items(): b[f"{phase}_{k}"] = val
            b["promotion_hint"] = "RESEARCH_ONLY"
            best_rows.append(b)

    results = pd.DataFrame(rows)
    best_df = pd.DataFrame(best_rows)
    results.to_csv(out_dir / "candidate_results.csv", index=False, encoding="utf-8-sig")
    best_df.to_csv(out_dir / "strategy_summary.csv", index=False, encoding="utf-8-sig")
    meta = {
        "lab_version": LAB_VERSION, "symbol": args.symbol, "interval": args.interval,
        "bars": len(df), "first": str(df.timestamp.iloc[0]), "last": str(df.timestamp.iloc[-1]),
        "source": "CSV" if args.csv else "TradingView/tvdatafeed", "split": {k: list(v) for k,v in split.items()},
        "config": asdict(cfg), "sl_atr": args.sl_atr, "tp_atr": args.tp_atr,
        "strategies": strategies,
        "warning": "Research output only. Do not copy parameters directly into live trading.",
    }
    (out_dir / "run_metadata.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n=== PersonalAIBot TradingView Strategy Lab ===")
    print(f"Data: {meta['source']} | {args.symbol} | {args.interval} | {len(df)} bars")
    print(f"Range: {meta['first']} -> {meta['last']}")
    if best_df.empty:
        print("No strategy produced enough trades for the minimum-trade filter.")
    else:
        cols = ["strategy", "train_expectancy_r", "validation_expectancy_r", "holdout_expectancy_r",
                "holdout_pf", "holdout_max_dd_pct", "holdout_trades", "neighborhood_stability"]
        print(best_df[cols].to_string(index=False))
    print(f"\nWrote: {out_dir / 'strategy_summary.csv'}")
    print(f"Wrote: {out_dir / 'candidate_results.csv'}")
    print(f"Wrote: {out_dir / 'run_metadata.json'}")


if __name__ == "__main__":
    main()
