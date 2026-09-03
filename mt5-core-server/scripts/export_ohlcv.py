#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MT5 OHLCV exporter for the Strategy Lab.

Pulls as much broker history as the terminal provides via the MetaTrader5
Python package (same mechanism as bridge/mt5_bridge.py, but paged backwards
with copy_rates_from_pos so the 5000-bar/request cap does not limit depth).

Output CSV columns match the Strategy Lab loader (tools/tradingview_strategy_lab.py):
    timestamp (epoch seconds, MT5 server time), open, high, low, close, volume (tick volume)

Usage (run with the Python that has MetaTrader5 installed, MT5 terminal running):
    python mt5-core-server/scripts/export_ohlcv.py --symbol XAUUSD \
        --timeframes M1,M5,M15,H1 --out ../../strategy_lab_data_mt5
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import MetaTrader5 as mt5
import numpy as np
import pandas as pd

TF_MAP = {
    "M1": mt5.TIMEFRAME_M1, "M5": mt5.TIMEFRAME_M5, "M15": mt5.TIMEFRAME_M15,
    "M30": mt5.TIMEFRAME_M30, "H1": mt5.TIMEFRAME_H1, "H4": mt5.TIMEFRAME_H4,
    "D1": mt5.TIMEFRAME_D1,
}
PAGE = 5000
LAB_SUFFIX = {"M1": "1m", "M5": "5m", "M15": "15m", "M30": "30m", "H1": "1h", "H4": "4h", "D1": "1d"}


def resolve_symbol(want: str) -> str:
    if mt5.symbol_select(want, True):
        info = mt5.symbol_info(want)
        if info is not None:
            return want
    names = [s.name for s in mt5.symbols_get()]
    for n in names:
        if n.upper().startswith(want.upper()):
            mt5.symbol_select(n, True)
            return n
    raise RuntimeError(f"symbol not found: {want} (broker has e.g. {names[:10]}...)")


def fetch_all(symbol: str, tf) -> pd.DataFrame:
    frames = []
    pos = 0
    while True:
        rates = None
        for attempt in range(3):
            rates = mt5.copy_rates_from_pos(symbol, tf, pos, PAGE)
            if rates is not None:
                break
            time.sleep(0.5 * (attempt + 1))
        if rates is None or len(rates) == 0:
            break
        frames.append(pd.DataFrame(rates))
        pos += len(rates)
        print(f"    ... {pos:,} bars", flush=True)
        if len(rates) < PAGE:
            break
    if not frames:
        raise RuntimeError(f"no rates returned for {symbol}")
    df = pd.concat(frames, ignore_index=True)
    df = df.drop_duplicates("time").sort_values("time").reset_index(drop=True)
    out = pd.DataFrame({
        "timestamp": df["time"].astype(np.int64),
        "open": df["open"], "high": df["high"], "low": df["low"],
        "close": df["close"], "volume": df["tick_volume"].astype(np.int64),
    })
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Export MT5 OHLCV history to Strategy Lab CSVs")
    ap.add_argument("--symbol", default="XAUUSD")
    ap.add_argument("--timeframes", default="M1,M5,M15,H1")
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[2] / "strategy_lab_data_mt5"))
    args = ap.parse_args()

    if not mt5.initialize():
        print(f"MT5 initialize failed: {mt5.last_error()}", file=sys.stderr)
        return 1
    try:
        sym = resolve_symbol(args.symbol)
        print(f"symbol: {sym}")
        out_dir = Path(args.out); out_dir.mkdir(parents=True, exist_ok=True)
        for name in [t.strip().upper() for t in args.timeframes.split(",") if t.strip()]:
            tf = TF_MAP.get(name)
            if tf is None:
                print(f"  {name}: unsupported timeframe, skipped"); continue
            print(f"  {name}: fetching ...", flush=True)
            df = fetch_all(sym, tf)
            path = out_dir / f"{args.symbol.lower()}_{LAB_SUFFIX[name]}.csv"
            df.to_csv(path, index=False)
            first = pd.to_datetime(df.timestamp.iloc[0], unit="s")
            last = pd.to_datetime(df.timestamp.iloc[-1], unit="s")
            print(f"  {name}: {len(df):,} bars | {first} -> {last} -> {path}")
    finally:
        mt5.shutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
