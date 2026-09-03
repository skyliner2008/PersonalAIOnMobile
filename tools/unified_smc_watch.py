#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalAIBot — Unified SMC WATCH-mode signal runner.

Runs the V5 basin configuration on the latest data and emits ONE signal
(BUY / SELL / HOLD) for the most recent CLOSED M15 bar. It never places
orders — output is a JSON decision record for the WATCH pipeline:

  1) offline  : read strategy_lab_data_mt5 CSVs (default; good for testing)
  2) --poll   : refresh CSVs from the MT5 bridge (http://127.0.0.1:5001/candles,
                no auth) so the signal is as of the latest closed bar
  3) --post   : POST the decision JSON to the mt5-core-server WATCH endpoint
                (POST /api/mt5/auto/external-signal, x-client-token auth)

Research/WATCH only. promotion_ready was FALSE in backtest; this pipeline
exists to collect forward samples, not to trade.

Examples:
  python tools/unified_smc_watch.py                       # offline, print JSON
  python tools/unified_smc_watch.py --poll                # refresh + signal
  python tools/unified_smc_watch.py --poll --post http://127.0.0.1:8090 --token XXX
"""
from __future__ import annotations

import argparse
import json
import pickle
import sys
import time
import urllib.request
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent))
import unified_smc_lab as U  # noqa: E402
import tradingview_strategy_lab as lab  # noqa: E402

sys.modules["__main__"].Features = U.Features  # feature cache was pickled under __main__

ROOT = Path(__file__).resolve().parents[1]
DD = ROOT / "strategy_lab_data_mt5"

# V5 robust-basin representative (see .obsidian-wiki/07_Trading_Intelligence/Strategy_Lab_TradingView.md)
BASIN_PARAMS = {
    "adx_th": 18, "body_min": 0.5, "entry_mode": "limit", "fill_win": 6,
    "margin": 0.2, "min_rr": 1.5, "regime": "trend_only", "rej_min": 0.25,
    "score_th": 0.65, "session": False, "setup_win": 4, "sl_buf": 0.25,
    "swing_L": 5, "tp_r": 3.0, "use_bos": True, "use_idm": True, "use_wick": False,
    "weights": U.WEIGHTS,
}

BRIDGE_TF = {"1h": ("xauusd_1h.csv", "H1"), "15m": ("xauusd_15m.csv", "M15"), "5m": ("xauusd_5m.csv", "M5")}


def poll_bridge(base_url: str, symbol: str = "XAUUSD") -> None:
    """Append fresh bars from the MT5 bridge into the local CSV cache."""
    for fn, tf in BRIDGE_TF.values():
        path = DD / fn
        df = lab.load_csv(str(path))
        last_ts = df.timestamp.iloc[-1]
        url = f"{base_url}/candles?symbol={symbol}&timeframe={tf}&count=5000"
        with urllib.request.urlopen(url, timeout=30) as r:
            payload = json.loads(r.read().decode())
        rows = payload.get("data") or []
        if not rows:
            print(f"[poll] {tf}: bridge returned no data", file=sys.stderr)
            continue
        new = pd.DataFrame({"timestamp": pd.to_datetime([x["t"] for x in rows], unit="s", utc=True),
                            "open": [x["o"] for x in rows], "high": [x["h"] for x in rows],
                            "low": [x["l"] for x in rows], "close": [x["c"] for x in rows],
                            "volume": [x["v"] for x in rows]})
        new = new[new.timestamp > last_ts]
        if new.empty:
            print(f"[poll] {tf}: up to date ({last_ts})")
            continue
        merged = pd.concat([df, new]).drop_duplicates("timestamp").sort_values("timestamp")
        merged.assign(timestamp=merged.timestamp.astype("int64") // 10**9).to_csv(path, index=False)
        print(f"[poll] {tf}: +{len(new)} bars -> {merged.timestamp.iloc[-1]}")
        cache = DD / "_feat_cache_L5.pkl"
        if cache.exists():
            cache.unlink()  # force feature rebuild on new data


def build_features() -> tuple[pd.DataFrame, U.Features]:
    dfs = {"1h": lab.load_csv(str(DD / "xauusd_1h.csv")),
           "15m": lab.load_csv(str(DD / "xauusd_15m.csv")),
           "5m": lab.load_csv(str(DD / "xauusd_5m.csv"))}
    dfs["4h"] = U.resample_tf(dfs["1h"], 240)
    dfs["30m"] = U.resample_tf(dfs["15m"], 30)
    start = max(df.timestamp.iloc[0] for df in dfs.values())
    base = dfs["15m"][dfs["15m"].timestamp >= start].reset_index(drop=True)
    dfs["15m"] = base
    return base, U.Features(dfs, 5)


def current_signal(base: pd.DataFrame, feat: U.Features, p: dict) -> dict:
    sig = U.score_signals(feat, p)
    i = int((sig != 0).to_numpy().nonzero()[0][-1]) if (sig != 0).any() else -1
    last_dir = int(sig.iloc[-1])
    now = {"direction": {1: "BUY", -1: "SELL", 0: "HOLD"}[last_dir],
           "bar_time": str(base.timestamp.iloc[-1]),
           "regime": {"h1_adx": round(float(feat.h1_adx[-1]), 2),
                      "h1_vol_pct": round(float(feat.h1_volpct[-1]), 3),
                      "h1_trend": int(feat.h1_tr[-1]), "h4_trend": int(feat.h4_tr[-1]),
                      "m30_trend": int(feat.m30_tr[-1])}}
    out = {"strategy": "UNIFIED_SMC_V5_BASIN", "symbol": "XAUUSD", "timeframe_setup": "15m",
           "generated_at": pd.Timestamp.now("UTC").isoformat(), **now}
    if last_dir == 0 and i >= 0:
        out["note"] = f"HOLD now; last signal was bar {base.timestamp.iloc[i]}"
        return out
    j = i if last_dir != 0 else i
    s = int(sig.iloc[j]) if j >= 0 else 0
    if s == 0:
        return out
    a = feat.m15_atr[j]
    ref = feat.m15_fvg_bull_mid[j] if s == 1 else feat.m15_fvg_bear_mid[j]
    market = not np.isfinite(ref)
    if market:
        ref = float(base.close.iloc[j])
    sl_struct = (feat.m15_last_sl[j] - p["sl_buf"] * a) if s == 1 else (feat.m15_last_sh[j] + p["sl_buf"] * a)
    if not np.isfinite(sl_struct) or (s == 1 and sl_struct >= ref) or (s == -1 and sl_struct <= ref):
        sl = ref - 1.2 * a if s == 1 else ref + 1.2 * a
    else:
        sl = sl_struct
    risk = (ref - sl) if s == 1 else (sl - ref)
    opp = feat.m15_last_sh[j] if s == 1 else feat.m15_last_sl[j]
    tp = opp if np.isfinite(opp) and ((opp - ref) if s == 1 else (ref - opp)) >= p["min_rr"] * risk \
        else (ref + p["tp_r"] * risk if s == 1 else ref - p["tp_r"] * risk)
    out.update({"direction": "BUY" if s == 1 else "SELL",
                "signal_bar": str(base.timestamp.iloc[j]),
                "entry_type": "market_next_open" if market else "limit_fvg_mid",
                "entry": round(float(ref), 3), "sl": round(float(sl), 3), "tp": round(float(tp), 3),
                "risk_dist": round(float(risk), 3),
                "rr": round(abs(float(tp - ref) / risk), 2) if risk > 0 else None,
                "fill_valid_bars": p["fill_win"]})
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Unified SMC WATCH-mode signal runner")
    ap.add_argument("--poll", metavar="BRIDGE_URL", nargs="?", const="http://127.0.0.1:5001",
                    help="refresh CSVs from the MT5 bridge first")
    ap.add_argument("--post", metavar="SERVER_URL", help="POST decision JSON to mt5-core-server")
    ap.add_argument("--token", default=None, help="x-client-token for --post")
    args = ap.parse_args()

    if args.poll:
        poll_bridge(args.poll)
    base, feat = build_features()
    decision = current_signal(base, feat, BASIN_PARAMS)
    txt = json.dumps(decision, ensure_ascii=False, indent=2)
    print(txt)

    out_log = ROOT / "strategy_lab_output" / "watch_log.jsonl"
    out_log.parent.mkdir(parents=True, exist_ok=True)
    with open(out_log, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(decision, ensure_ascii=False) + "\n")

    if args.post:
        url = args.post.rstrip("/") + "/api/mt5/auto/external-signal"
        req = urllib.request.Request(url, data=txt.encode(), method="POST",
                                     headers={"Content-Type": "application/json",
                                              **({"x-client-token": args.token} if args.token else {})})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                print(f"[post] {r.status} {r.read().decode()[:200]}")
        except Exception as e:
            print(f"[post] FAILED: {e} (decision still logged to {out_log})", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
