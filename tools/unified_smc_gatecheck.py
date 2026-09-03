#!/usr/bin/env python3
"""Run full R-gates for one Unified SMC candidate (e.g. a basin representative)."""
import json
import pickle
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import unified_smc_lab as U  # noqa: E402
import tradingview_strategy_lab as lab  # noqa: E402
from strategy_research_r1_r5 import monte_carlo, permutation, diag  # noqa: E402

sys.modules["__main__"].Features = U.Features  # cache pickled when lab ran as __main__

ROOT = Path(__file__).resolve().parents[1]
DD = ROOT / "strategy_lab_data_mt5"


def main():
    params_json = sys.argv[1]
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else None

    dfs = {"1h": lab.load_csv(str(DD / "xauusd_1h.csv")),
           "15m": lab.load_csv(str(DD / "xauusd_15m.csv")),
           "5m": lab.load_csv(str(DD / "xauusd_5m.csv"))}
    dfs["4h"] = U.resample_tf(dfs["1h"], 240)
    dfs["30m"] = U.resample_tf(dfs["15m"], 30)
    start = max(df.timestamp.iloc[0] for df in dfs.values())
    base = dfs["15m"][dfs["15m"].timestamp >= start].reset_index(drop=True)
    with open(DD / "_feat_cache_L5.pkl", "rb") as fh:
        feat = pickle.load(fh)["feat"]

    n = len(base)
    split = {"train": (0, int(n * .6)), "validation": (int(n * .6), int(n * .8)), "holdout": (int(n * .8), n)}
    cfg = lab.Config(min_trades=10)

    p = json.loads(params_json)
    p.pop("swing_L", None)
    p["weights"] = U.WEIGHTS
    sig = U.score_signals(feat, p)
    trades = U.simulate_v2(base, sig, feat, cfg, *split["holdout"], p)
    rs = [t.pnl_r for t in trades]
    rep = {"params": {k: v for k, v in p.items() if k != "weights"},
           "signals_total": int((sig != 0).sum()),
           "holdout_diag": diag(trades),
           "holdout_trades": len(trades),
           "holdout_mean_r": float(np.mean(rs)) if rs else 0.0,
           "walk_forward": U.wfo_v2(feat, p, cfg),
           "cost_stress": U.stress_v2(feat, p, cfg, split["holdout"]),
           "monte_carlo": monte_carlo(rs),
           "permutation": permutation(rs)}
    wf = rep["walk_forward"]
    adv = [x for x in rep["cost_stress"] if x["scenario"] == "adverse"][0]
    rep["final_gate"] = {
        "holdout_sample_ok": len(trades) >= 30,
        "holdout_positive": bool(rs and np.mean(rs) > 0),
        "wfo_consistency": bool(wf and sum(x["expectancy_r"] > 0 for x in wf) / len(wf) >= 0.75),
        "adverse_cost_positive": bool(adv["expectancy_r"] > 0),
        "permutation_p_lt_0_05": bool(rep["permutation"]["p_value"] < 0.05)}
    rep["promotion_ready"] = bool(all(rep["final_gate"].values()))
    if out_path:
        out_path.write_text(json.dumps(rep, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"wrote {out_path}")
    print(json.dumps({k: rep[k] for k in ("holdout_trades", "holdout_mean_r", "final_gate", "promotion_ready")}, indent=1))
    for w in wf:
        print("  WFO w%d n=%d exp=%+.3f pf=%.2f" % (w["window"], w["trades"], w["expectancy_r"], w["pf"]))
    for s in rep["cost_stress"]:
        print("  stress %-10s exp=%+.3f pf=%.2f" % (s["scenario"], s["expectancy_r"], s["pf"]))
    print("  perm p=%.4f | mc p05=%.1fR p50=%.1fR p95dd=%.1fR" % (
        rep["permutation"]["p_value"], rep["monte_carlo"]["p05_final_r"],
        rep["monte_carlo"]["p50_final_r"], rep["monte_carlo"]["p95_max_dd_r"]))


if __name__ == "__main__":
    main()
