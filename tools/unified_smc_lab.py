#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalAIBot — Unified SMC Multi-TF Strategy (research prototype V1)

One signal engine, not one-signal-per-TF. The TF stack has roles:

    H4, H1  -> Market Context   (structure trend)
    M30, H1 -> Trend Context    (structure trend)
    M15     -> Setup            (FVG tap / EQL sweep / CHoCH / BOS / IDM)
    M5      -> Confirmation     (BOS + body/wick quality)   [M1 slot: plug-in ready]
    entry   -> next M15 bar open (closed-bar signal, no look-ahead)

SMC building blocks (all causal):
  - confirmed swing highs/lows (pivot confirmed only after L right bars)
  - BOS  = break with the current trend;  CHoCH (SMS) = break against it
  - EQH/EQL = consecutive swings within ATR tolerance (liquidity pools)
  - IDM   = minor swing (small L) liquidity inside the current leg
  - FVG   = 3-candle imbalance, active until mitigated
  - body/wick quality for confirmation

No candidate may emit BUY while another emits SELL: the score merges all
evidence into ONE decision; insufficient confluence returns 0 = HOLD.

Run:
  python tools/unified_smc_lab.py --data-dir strategy_lab_data
"""
from __future__ import annotations

import argparse
import json
from itertools import product
from pathlib import Path

import numpy as np
import pandas as pd

import tradingview_strategy_lab as lab
from strategy_research_r1_r5 import monte_carlo, permutation, diag


TF_MIN = {"5m": 5, "15m": 15, "30m": 30, "1h": 60, "4h": 240}


# --------------------------- SMC primitives ------------------------------

def confirmed_swings(df: pd.DataFrame, L: int):
    """Pivot highs/lows confirmed L bars later (causal). Returns boolean arrays
    at confirmation index plus the price series (NaN elsewhere)."""
    rh = df.high.rolling(2 * L + 1, center=True).max()
    rl = df.low.rolling(2 * L + 1, center=True).min()
    ph = (df.high == rh).values
    pl = (df.low == rl).values
    sh_price = pd.Series(np.where(ph, df.high, np.nan), index=df.index).shift(L)
    sl_price = pd.Series(np.where(pl, df.low, np.nan), index=df.index).shift(L)
    return sh_price.values, sl_price.values


def structure(df: pd.DataFrame, L: int):
    """Trend state + BOS/CHoCH events from confirmed swings."""
    sh, sl = confirmed_swings(df, L)
    c = df.close.values
    n = len(df)
    trend = np.zeros(n, dtype=int)
    bos_up = np.zeros(n, bool); bos_dn = np.zeros(n, bool)
    choch_up = np.zeros(n, bool); choch_dn = np.zeros(n, bool)
    last_sh = np.nan; last_sl = np.nan; state = 0
    for i in range(n):
        if np.isfinite(sh[i]): last_sh = sh[i]
        if np.isfinite(sl[i]): last_sl = sl[i]
        if np.isfinite(last_sh) and c[i] > last_sh:
            if state == -1: choch_up[i] = True
            else: bos_up[i] = True
            state = 1; last_sh = np.nan
        elif np.isfinite(last_sl) and c[i] < last_sl:
            if state == 1: choch_dn[i] = True
            else: bos_dn[i] = True
            state = -1; last_sl = np.nan
        trend[i] = state
    return {"trend": trend, "bos_up": bos_up, "bos_dn": bos_dn,
            "choch_up": choch_up, "choch_dn": choch_dn,
            "sh": sh, "sl": sl}


def equal_levels(df: pd.DataFrame, L: int, atr: pd.Series, tol_mult: float = 0.25):
    """EQH/EQL: consecutive confirmed swings within tol. Detect sweep-and-reclaim.
    Returns per-bar boolean flags: eql_sweep_up (bullish), eqh_sweep_dn (bearish)."""
    sh, sl = confirmed_swings(df, L)
    h = df.high.values; l = df.low.values; c = df.close.values; a = atr.values
    n = len(df)
    eql_sweep_up = np.zeros(n, bool); eqh_sweep_dn = np.zeros(n, bool)
    # build EQ pools: pairs of consecutive swings within tolerance
    eqh = []; eql = []  # (confirm_idx, level, swept)
    prev_sh = np.nan; prev_sl = np.nan
    for i in range(n):
        if np.isfinite(sh[i]):
            if np.isfinite(prev_sh) and abs(sh[i] - prev_sh) <= tol_mult * a[i]:
                eqh.append([i, max(sh[i], prev_sh), False])
            prev_sh = sh[i]
        if np.isfinite(sl[i]):
            if np.isfinite(prev_sl) and abs(sl[i] - prev_sl) <= tol_mult * a[i]:
                eql.append([i, min(sl[i], prev_sl), False])
            prev_sl = sl[i]
        # sweep-and-reclaim: wick through the pool, close back inside
        for pool in eql:
            if not pool[2] and pool[0] < i and l[i] < pool[1] and c[i] > pool[1]:
                pool[2] = True; eql_sweep_up[i] = True
        for pool in eqh:
            if not pool[2] and pool[0] < i and h[i] > pool[1] and c[i] < pool[1]:
                pool[2] = True; eqh_sweep_dn[i] = True
    return eql_sweep_up, eqh_sweep_dn


def idm_sweeps(df: pd.DataFrame, L_minor: int = 2):
    """Inducement: sweep of the most recent MINOR swing low/high (small L),
    reclaimed by close. Bullish IDM sweep = minor low taken then close back above."""
    sh, sl = confirmed_swings(df, L_minor)
    l = df.low.values; h = df.high.values; c = df.close.values
    n = len(df)
    up = np.zeros(n, bool); dn = np.zeros(n, bool)
    last_sl = np.nan; last_sh = np.nan; used_sl = np.nan; used_sh = np.nan
    for i in range(n):
        if np.isfinite(sl[i]): last_sl = sl[i]
        if np.isfinite(sh[i]): last_sh = sh[i]
        if np.isfinite(last_sl) and last_sl != used_sl and l[i] < last_sl and c[i] > last_sl:
            up[i] = True; used_sl = last_sl
        if np.isfinite(last_sh) and last_sh != used_sh and h[i] > last_sh and c[i] < last_sh:
            dn[i] = True; used_sh = last_sh
    return up, dn


def fvg_touch(df: pd.DataFrame):
    """Per-bar: close inside an active (not yet mitigated) FVG zone.
    Also returns the midpoint of the newest active zone per side (for limit entries)."""
    h = df.high.values; l = df.low.values; c = df.close.values
    n = len(df)
    in_bull = np.zeros(n, bool); in_bear = np.zeros(n, bool)
    bull_mid = np.full(n, np.nan); bear_mid = np.full(n, np.nan)
    bull = []; bear = []  # [bottom, top]
    for i in range(2, n):
        if l[i] > h[i - 2]: bull.append([h[i - 2], l[i]])        # bullish imbalance
        if h[i] < l[i - 2]: bear.append([l[i - 2], h[i]])        # bearish imbalance
        bull = [z for z in bull if l[i] > z[0]]                  # mitigated if low undercuts bottom
        bear = [z for z in bear if h[i] < z[1]]
        # touch = wick/close enters the zone and price holds inside it
        in_bull[i] = any(z[0] <= c[i] and l[i] <= z[1] for z in bull)
        in_bear[i] = any(c[i] <= z[1] and h[i] >= z[0] for z in bear)
        if bull: bull_mid[i] = 0.5 * (bull[-1][0] + bull[-1][1])
        if bear: bear_mid[i] = 0.5 * (bear[-1][0] + bear[-1][1])
    return in_bull, in_bear, bull_mid, bear_mid


def body_wick(df: pd.DataFrame):
    rng = (df.high - df.low).replace(0, np.nan)
    body = (df.close - df.open).abs() / rng
    up_wick = (df.high - pd.concat([df.close, df.open], axis=1).max(axis=1)) / rng
    dn_wick = (pd.concat([df.close, df.open], axis=1).min(axis=1) - df.low) / rng
    return body.fillna(0), up_wick.fillna(0), dn_wick.fillna(0)


# --------------------------- MTF assembly --------------------------------

def resample_tf(df: pd.DataFrame, minutes: int) -> pd.DataFrame:
    g = df.set_index("timestamp").resample(f"{minutes}min", origin="start_day", label="left")
    out = g.agg({"open": "first", "high": "max", "low": "min", "close": "last", "volume": "sum"}).dropna()
    return out.reset_index()


def map_htf(base_ts: pd.Series, htf: pd.DataFrame, minutes: int, cols: dict) -> pd.DataFrame:
    """As-of join: base bar at open time t may use HTF bars closed at or before t
    (HTF open + duration <= t). Strictly causal."""
    avail = pd.DataFrame({"available_at": htf["timestamp"] + pd.Timedelta(minutes=minutes)})
    for name, vals in cols.items():
        avail[name] = vals
    avail = avail.dropna().sort_values("available_at")
    left = pd.DataFrame({"timestamp": base_ts}).sort_values("timestamp")
    merged = pd.merge_asof(left, avail, left_on="timestamp", right_on="available_at", direction="backward")
    return merged.set_index("timestamp").reindex(base_ts)


class Features:
    """All TF features precomputed once per swing_L; candidate scoring is vectorized."""

    def __init__(self, dfs: dict[str, pd.DataFrame], swing_L: int):
        self.base = dfs["15m"].reset_index(drop=True)          # base timeline
        self.n = len(self.base)
        st = {}
        for tf, df in dfs.items():
            s = structure(df, swing_L)
            a = lab.atr(df, 14)
            eql_up, eqh_dn = equal_levels(df, swing_L, a)
            idm_up, idm_dn = idm_sweeps(df)
            in_bull, in_bear, bull_mid, bear_mid = fvg_touch(df)
            body, up_w, dn_w = body_wick(df)
            st[tf] = {"df": df, "struct": s, "eql_up": eql_up, "eqh_dn": eqh_dn,
                      "idm_up": idm_up, "idm_dn": idm_dn, "in_bull": in_bull,
                      "in_bear": in_bear, "fvg_bull_mid": bull_mid, "fvg_bear_mid": bear_mid,
                      "body": body.values, "up_w": up_w.values,
                      "dn_w": dn_w.values}
        self.st = st
        bts = self.base.timestamp
        # context: H4 + H1 trend; trend ctx: M30 + H1 trend
        self.h4_tr = map_htf(bts, st["4h"]["df"], TF_MIN["4h"], {"t": st["4h"]["struct"]["trend"]}).t.fillna(0).values
        self.h1_tr = map_htf(bts, st["1h"]["df"], TF_MIN["1h"], {"t": st["1h"]["struct"]["trend"]}).t.fillna(0).values
        self.m30_tr = map_htf(bts, st["30m"]["df"], TF_MIN["30m"], {"t": st["30m"]["struct"]["trend"]}).t.fillna(0).values
        # V4 Layer A: regime features from H1 (ADX strength + ATR volatility percentile)
        h1df = st["1h"]["df"]
        adx_h1 = lab.adx(h1df, 14)
        atr_h1 = lab.atr(h1df, 14)
        volpct = atr_h1.rolling(500, min_periods=100).rank(pct=True)
        m = map_htf(bts, h1df, TF_MIN["1h"], {"adx": adx_h1.values, "vp": volpct.values})
        self.h1_adx = m.adx.fillna(0).values
        self.h1_volpct = m.vp.fillna(0.5).values
        # setup features live on the base M15 itself
        m15 = st["15m"]
        self.m15_choch_up = m15["struct"]["choch_up"]; self.m15_choch_dn = m15["struct"]["choch_dn"]
        self.m15_bos_up = m15["struct"]["bos_up"];     self.m15_bos_dn = m15["struct"]["bos_dn"]
        self.m15_fvg_bull = m15["in_bull"];            self.m15_fvg_bear = m15["in_bear"]
        self.m15_eql_up = m15["eql_up"];               self.m15_eqh_dn = m15["eqh_dn"]
        self.m15_idm_up = m15["idm_up"];               self.m15_idm_dn = m15["idm_dn"]
        # V2: execution references on the base TF
        self.m15_fvg_bull_mid = m15["fvg_bull_mid"];   self.m15_fvg_bear_mid = m15["fvg_bear_mid"]
        self.m15_last_sh = pd.Series(m15["struct"]["sh"]).ffill().values
        self.m15_last_sl = pd.Series(m15["struct"]["sl"]).ffill().values
        self.m15_atr = lab.atr(m15["df"], 14).values
        self.base_hour = self.base.timestamp.dt.hour.values
        # confirmation: aggregate M5 bars inside each M15 window [t, t+15m)
        m5df = st["5m"]["df"].copy()
        m5df["bin"] = m5df.timestamp.dt.floor("15min")
        m5df["bos_up"] = st["5m"]["struct"]["bos_up"]; m5df["bos_dn"] = st["5m"]["struct"]["bos_dn"]
        m5df["choch_up"] = st["5m"]["struct"]["choch_up"]; m5df["choch_dn"] = st["5m"]["struct"]["choch_dn"]
        m5df["body"] = st["5m"]["body"]; m5df["up_w"] = st["5m"]["up_w"]; m5df["dn_w"] = st["5m"]["dn_w"]
        m5df["up_bar"] = (m5df.close > m5df.open).astype(float)
        m5df["body_up"] = m5df.body * m5df.up_bar
        m5df["body_dn"] = m5df.body * (1 - m5df.up_bar)
        agg = m5df.groupby("bin").agg(
            m5_bos_up=("bos_up", "max"), m5_bos_dn=("bos_dn", "max"),
            m5_choch_up=("choch_up", "max"), m5_choch_dn=("choch_dn", "max"),
            m5_body_up=("body_up", "max"), m5_body_dn=("body_dn", "max"),
            m5_rej_up=("dn_w", "max"), m5_rej_dn=("up_w", "max"))
        j = self.base[["timestamp"]].merge(agg, left_on="timestamp", right_index=True, how="left")
        for c in agg.columns:
            setattr(self, c, j[c].fillna(0).values.astype(float))


def score_signals(f: Features, p: dict) -> pd.Series:
    ctx_up = 0.5 * (f.h4_tr > 0) + 0.5 * (f.h1_tr > 0)
    ctx_dn = 0.5 * (f.h4_tr < 0) + 0.5 * (f.h1_tr < 0)
    tr_up = 0.5 * (f.m30_tr > 0) + 0.5 * (f.h1_tr > 0)
    tr_dn = 0.5 * (f.m30_tr < 0) + 0.5 * (f.h1_tr < 0)

    W = p["setup_win"]
    def roll(a):  # event happened within the last W bars (inclusive)
        return pd.Series(a.astype(float)).rolling(W, min_periods=1).max().values > 0

    structure_up = f.m15_choch_up | (f.m15_bos_up if p["use_bos"] else False)
    structure_dn = f.m15_choch_dn | (f.m15_bos_dn if p["use_bos"] else False)
    zone_up = f.m15_fvg_bull | f.m15_eql_up | (f.m15_idm_up if p["use_idm"] else False)
    zone_dn = f.m15_fvg_bear | f.m15_eqh_dn | (f.m15_idm_dn if p["use_idm"] else False)
    # SMC sequence: sweep/tap AND displacement within the same short window
    setup_up = roll(zone_up) & roll(structure_up)
    setup_dn = roll(zone_dn) & roll(structure_dn)

    m5_shift_up = (f.m5_bos_up > 0) | (f.m5_choch_up > 0)
    m5_shift_dn = (f.m5_bos_dn > 0) | (f.m5_choch_dn > 0)
    conf_up = m5_shift_up & (f.m5_body_up >= p["body_min"]) & (f.m5_rej_up >= p["rej_min"] if p["use_wick"] else True)
    conf_dn = m5_shift_dn & (f.m5_body_dn >= p["body_min"]) & (f.m5_rej_dn >= p["rej_min"] if p["use_wick"] else True)

    w = p["weights"]
    long_s = w[0] * ctx_up + w[1] * tr_up + w[2] * setup_up + w[3] * conf_up
    short_s = w[0] * ctx_dn + w[1] * tr_dn + w[2] * setup_dn + w[3] * conf_dn

    out = pd.Series(0, index=f.base.index, dtype=int)
    go_long = (long_s >= p["score_th"]) & (long_s - short_s >= p["margin"])
    go_short = (short_s >= p["score_th"]) & (short_s - long_s >= p["margin"])
    if p.get("session", False):
        # XAUUSD liquid sessions only: London + New York (07:00-16:59 UTC)
        ok = (f.base_hour >= 7) & (f.base_hour <= 16)
        go_long = go_long & ok
        go_short = go_short & ok
    # V4 Layer A: Regime Detector — TREND / RANGE allowed per config, CHAOTIC never trades
    reg = p.get("regime", "off")
    if reg != "off":
        chaotic = (f.h1_volpct > 0.90) | (f.h1_volpct < 0.10)
        trend_ok = (f.h1_adx >= p["adx_th"]) & (f.h1_tr != 0)
        allowed = trend_ok if reg == "trend_only" else (trend_ok | (f.h1_adx < p["adx_th"]))
        allowed = allowed & ~chaotic
        go_long = go_long & allowed
        go_short = go_short & allowed
    # HARD RULE: setup must exist — context/trend alone never trades.
    out[go_long & setup_up] = 1
    out[go_short & setup_dn] = -1
    return out


# --------------------------- V2 execution ---------------------------------

def simulate_v2(df: pd.DataFrame, sig: pd.Series, f: Features, cfg: lab.Config,
                start: int, end: int, p: dict) -> list[lab.Trade]:
    """SMC-native execution on the base TF:
    - entry: LIMIT at the tapped FVG midpoint (sweep-only setups fall back to
      next-bar market), valid for fill_win bars, or MARKET at next bar open
    - SL: beyond last confirmed swing (sl_buf x ATR buffer); ATR fallback
    - TP: last confirmed opposing swing if reward:risk >= min_rr, else tp_r x risk
    - conservative intrabar: SL checked before TP, gap-through handling
    """
    o = df.open.values; h = df.high.values; l = df.low.values; c = df.close.values
    a = f.m15_atr
    trades: list[lab.Trade] = []
    balance = cfg.initial_balance
    K = int(p["fill_win"])
    # iterate only bars that carry a signal (sparse) instead of scanning every bar
    sig_idx = np.flatnonzero(sig.values[max(start, 1):end - 1]) + max(start, 1)
    ptr = 0
    while ptr < len(sig_idx):
        i = int(sig_idx[ptr]); ptr += 1
        s = int(sig.iloc[i])
        if not np.isfinite(a[i]) or a[i] <= 0:
            continue
        # --- entry reference
        if p["entry_mode"] == "limit":
            ref = f.m15_fvg_bull_mid[i] if s == 1 else f.m15_fvg_bear_mid[i]
            if not np.isfinite(ref):
                ref = c[i]                     # sweep-only setup -> market at next open
                market = True
            else:
                market = False
        else:
            ref = o[i + 1]; market = True
        # --- structure SL
        sl_struct = (f.m15_last_sl[i] - p["sl_buf"] * a[i]) if s == 1 else (f.m15_last_sh[i] + p["sl_buf"] * a[i])
        if not np.isfinite(sl_struct) or (s == 1 and sl_struct >= ref) or (s == -1 and sl_struct <= ref):
            sl = ref - 1.2 * a[i] if s == 1 else ref + 1.2 * a[i]
        else:
            sl = sl_struct
        risk_dist = (ref - sl) if s == 1 else (sl - ref)
        if risk_dist < 0.3 * a[i] or risk_dist > 4.0 * a[i]:
            continue
        # --- liquidity/R-multiple TP
        opp = f.m15_last_sh[i] if s == 1 else f.m15_last_sl[i]
        if np.isfinite(opp) and ((opp - ref) if s == 1 else (ref - opp)) >= p["min_rr"] * risk_dist:
            tp = opp
        else:
            tp = ref + p["tp_r"] * risk_dist if s == 1 else ref - p["tp_r"] * risk_dist
        # --- fill + manage
        entry = None; j = i + 1
        fill_end = min(i + 1 + (1 if market else K), end)
        while j < fill_end:
            if market:
                entry = o[j]; break
            if s == 1 and l[j] <= ref:
                entry = min(ref, o[j]); break          # gap-through fills at open
            if s == -1 and h[j] >= ref:
                entry = max(ref, o[j]); break
            j += 1
        if entry is None:
            continue                                    # limit never filled -> no trade
        entry_eff = entry + cfg.spread / 2 if s == 1 else entry - cfg.spread / 2
        risk_money = balance * cfg.risk_pct
        qty = risk_money / risk_dist
        exit_price = c[end - 1]; reason = "TIMEOUT"; exit_j = end - 1
        while j < end:
            hit_sl = l[j] <= sl if s == 1 else h[j] >= sl
            hit_tp = h[j] >= tp if s == 1 else l[j] <= tp
            if hit_sl or hit_tp:
                if hit_sl:
                    exit_price = min(sl, o[j]) if s == 1 else max(sl, o[j])
                    reason = "SL"
                else:
                    exit_price = tp; reason = "TP"
                exit_j = j
                break
            j += 1
        gross = qty * (exit_price - entry_eff if s == 1 else entry_eff - exit_price)
        commission = cfg.commission_pct * qty * (entry_eff + exit_price)
        pnl = gross - commission - cfg.slippage * qty
        pnl_r = pnl / risk_money if risk_money else 0.0
        trades.append(lab.Trade("BUY" if s == 1 else "SELL", i + 1, exit_j, entry_eff, exit_price, pnl, pnl_r, reason))
        balance += pnl
        while ptr < len(sig_idx) and sig_idx[ptr] <= exit_j:
            ptr += 1
    return trades


GRID = {
    # V5: zoom around the V4 trend_only cluster
    "regime": ["trend_only"],
    "adx_th": [18, 22, 26, 30],
    "score_th": [0.55, 0.65, 0.75],
    "setup_win": [2, 3, 4, 6],
    "tp_r": [2.0, 3.0, 4.0],
    "entry_mode": ["limit", "market"],
    "session": [False, True],
    "use_idm": [False, True],
    # fixed axes
    "margin": [0.20], "body_min": [0.50], "rej_min": [0.25],
    "use_bos": [True], "use_wick": [False], "swing_L": [5],
    "fill_win": [6], "sl_buf": [0.25], "min_rr": [1.5],
}
WEIGHTS = (0.30, 0.20, 0.30, 0.20)  # ctx, trend, setup, confirmation


# --------------------------- V3 robustness --------------------------------

def neighborhood_stability_v2(f: Features, p: dict, cfg: lab.Config, va_range):
    """One-step perturbation of every tunable param, re-scored on validation.
    A robust candidate keeps >= 85% of its validation score under perturbation."""
    base_m = lab.metrics(simulate_v2(f.base, score_signals(f, p), f, cfg, *va_range, p), cfg.initial_balance)
    base_s = lab.score(base_m)
    vals = []
    for name, options in GRID.items():
        if name not in p or len(options) < 2:
            continue
        for v in options:
            if v == p[name]:
                continue
            q = dict(p); q[name] = v
            m = lab.metrics(simulate_v2(f.base, score_signals(f, q), f, cfg, *va_range, q), cfg.initial_balance)
            vals.append(lab.score(m) >= base_s * 0.85)
    return (float(sum(vals) / len(vals)) if vals else 0.0), len(vals)


def wfo_v2(f: Features, p: dict, cfg: lab.Config, windows: int = 4):
    """Walk-forward on fixed params: train 35% / test 15% sliding windows."""
    n = f.n; train = max(800, int(n * .35)); test = max(400, int(n * .15)); rows = []
    sig = score_signals(f, p)
    for k in range(windows):
        tr_end = k * test + train; b = tr_end + test
        if b > n:
            break
        m = lab.metrics(simulate_v2(f.base, sig, f, cfg, tr_end, b, p), cfg.initial_balance)
        rows.append({"window": k + 1, "test_range": [tr_end, b], **m})
    return rows


def stress_v2(f: Features, p: dict, cfg: lab.Config, ho_range):
    out = []
    for label, mult, slip in [("base", 1.0, 0.0), ("spread_1p5", 1.5, 0.0), ("adverse", 1.5, 0.05)]:
        c2 = lab.Config(initial_balance=cfg.initial_balance, risk_pct=cfg.risk_pct,
                        spread=cfg.spread * mult, commission_pct=cfg.commission_pct,
                        slippage=slip, min_trades=cfg.min_trades)
        m = lab.metrics(simulate_v2(f.base, score_signals(f, p), f, c2, *ho_range, p), c2.initial_balance)
        out.append({"scenario": label, **m})
    return out


# ------------------------------ main --------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Unified SMC Multi-TF Strategy (research V2)")
    ap.add_argument("--data-dir", default="strategy_lab_data_mt5")
    ap.add_argument("--symbol", default="XAUUSD")
    ap.add_argument("--out", default="strategy_lab_output/unified_smc_mt5_v2")
    ap.add_argument("--min-trades", type=int, default=10)
    ap.add_argument("--shard", default=None, help="e.g. 0/4 runs every 4th candidate; parts merge across runs")
    ap.add_argument("--no-feat-cache", action="store_true")
    args = ap.parse_args()

    dd = Path(args.data_dir)
    dfs = {"1h": lab.load_csv(str(dd / "xauusd_1h.csv")),
           "15m": lab.load_csv(str(dd / "xauusd_15m.csv")),
           "5m": lab.load_csv(str(dd / "xauusd_5m.csv"))}
    dfs["4h"] = resample_tf(dfs["1h"], 240)
    dfs["30m"] = resample_tf(dfs["15m"], 30)
    # overlap window = where every TF (esp. M5) actually has data
    start = max(df.timestamp.iloc[0] for df in dfs.values())
    base = dfs["15m"][dfs["15m"].timestamp >= start].reset_index(drop=True)
    if len(base) < 500:
        raise RuntimeError(f"overlap window too small: {len(base)} M15 bars")
    dfs["15m"] = base
    print(f"Overlap window: {base.timestamp.iloc[0]} -> {base.timestamp.iloc[-1]} | {len(base)} M15 bars")

    n = len(base)
    split = {"train": (0, int(n * .60)), "validation": (int(n * .60), int(n * .80)), "holdout": (int(n * .80), n)}
    cfg = lab.Config(min_trades=args.min_trades)

    import pickle
    fingerprint = {tf: (len(df), str(df.timestamp.iloc[-1])) for tf, df in dfs.items()}

    def get_features(L: int) -> Features:
        cache = Path(args.data_dir) / f"_feat_cache_L{L}.pkl"
        if not args.no_feat_cache and cache.exists():
            try:
                with open(cache, "rb") as fh:
                    blob = pickle.load(fh)
                if blob.get("fp") == fingerprint:
                    print(f"loaded cached features (swing_L={L})", flush=True)
                    return blob["feat"]
            except Exception:
                pass
        print(f"building SMC features (swing_L={L}) ...", flush=True)
        feat = Features(dfs, L)
        try:
            with open(cache, "wb") as fh:
                pickle.dump({"fp": fingerprint, "feat": feat}, fh)
        except Exception:
            pass
        return feat

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    parts_dir = out / "parts"; parts_dir.mkdir(exist_ok=True)
    shard = None
    if args.shard:
        s0, k0 = args.shard.split("/"); shard = (int(s0), int(k0))

    feat_cache: dict[int, Features] = {}
    rows = []
    keys = [k for k in GRID]
    all_c = list(product(*(GRID[k] for k in keys)))
    if shard:
        all_c = [c for i, c in enumerate(all_c) if i % shard[1] == shard[0]]
    for vals in all_c:
        p = dict(zip(keys, vals)); p["weights"] = WEIGHTS
        L = p.pop("swing_L")
        if L not in feat_cache:
            feat_cache[L] = get_features(L)
        f = feat_cache[L]
        sig = score_signals(f, p)
        p["swing_L"] = L
        parts = {name: lab.metrics(simulate_v2(base, sig, f, cfg, a, b, p), cfg.initial_balance)
                 for name, (a, b) in split.items()}
        tr, va, ho = parts["train"], parts["validation"], parts["holdout"]
        # V3: selection rewards robustness, not peak train score
        st, nb = neighborhood_stability_v2(f, p, cfg, split["validation"])
        selection = (0.60 * lab.score(tr) + 0.40 * lab.score(va)) + 8.0 * st
        row = {"params": json.dumps({k: v for k, v in p.items() if k != "weights"}, sort_keys=True),
               "selection_score": selection, "neighborhood_stability": st, "neighborhood_tests": nb,
               "score": selection, "signals": int((sig != 0).sum())}
        for phase, m in parts.items():
            for k, v in m.items():
                row[f"{phase}_{k}"] = v
        rows.append(row)

    tag = f"shard_{shard[0]}of{shard[1]}" if shard else "full"
    pd.DataFrame(rows).to_csv(parts_dir / f"{tag}.csv", index=False, encoding="utf-8-sig")
    print(f"[{tag}] {len(rows)} candidates saved -> {parts_dir / (tag + '.csv')}", flush=True)
    frames = [pd.read_csv(f) for f in sorted(parts_dir.glob("*.csv"))] if shard else [pd.DataFrame(rows)]
    cand = pd.concat(frames, ignore_index=True).sort_values("selection_score", ascending=False)
    cand.to_csv(out / "unified_smc_candidates.csv", index=False, encoding="utf-8-sig")

    elig = cand[(cand.validation_expectancy_r > 0) & (cand.validation_trades >= args.min_trades)
                & (cand.neighborhood_stability >= 0.5)]
    chosen = elig.iloc[0].to_dict() if not elig.empty else None
    report = {"lab": "Unified-SMC-MTF-V5", "symbol": args.symbol,
              "tf_stack": {"context": ["4h", "1h"], "trend": ["30m", "1h"], "setup": "15m",
                           "confirmation": "5m", "execution": "FVG-mid limit / market, structure SL, liquidity TP"},
              "window": {"first": str(base.timestamp.iloc[0]), "last": str(base.timestamp.iloc[-1]), "m15_bars": n},
              "split": split, "candidates": len(cand), "eligible": len(elig), "chosen": chosen}
    if chosen:
        p = json.loads(chosen["params"]); L = p.pop("swing_L"); p["weights"] = WEIGHTS
        sig = score_signals(feat_cache[L], p)
        trades = simulate_v2(base, sig, feat_cache[L], cfg, *split["holdout"], p)
        rs = [t.pnl_r for t in trades]
        report["holdout_diag"] = diag(trades)
        report["monte_carlo"] = monte_carlo(rs)
        report["permutation"] = permutation(rs)
        report["walk_forward"] = wfo_v2(feat_cache[L], p, cfg)
        report["cost_stress"] = stress_v2(feat_cache[L], p, cfg, split["holdout"])
        wf = report["walk_forward"]
        adv = [x for x in report["cost_stress"] if x["scenario"] == "adverse"][0]
        report["final_gate"] = {
            "holdout_sample_ok": len(trades) >= 30,
            "holdout_positive": bool(rs and np.mean(rs) > 0),
            "wfo_consistency": bool(wf and sum(x["expectancy_r"] > 0 for x in wf) / len(wf) >= 0.75),
            "adverse_cost_positive": bool(adv["expectancy_r"] > 0),
            "permutation_p_lt_0_05": bool(report["permutation"]["p_value"] < 0.05)}
        report["promotion_ready"] = bool(all(report["final_gate"].values()))
    else:
        report["promotion_ready"] = False
        report["final_gate_reason"] = "No candidate with positive validation expectancy."
    (out / "unified_smc_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    cols = ["params", "signals", "neighborhood_stability", "train_trades", "train_expectancy_r", "validation_trades",
            "validation_expectancy_r", "holdout_trades", "holdout_expectancy_r", "holdout_pf", "holdout_max_dd_pct"]
    print("\n=== Unified SMC MTF — top candidates ===")
    print(cand[cols].head(10).to_string(index=False))
    print(f"\nWrote: {out / 'unified_smc_candidates.csv'}")
    print(f"Wrote: {out / 'unified_smc_report.json'}")
    print(f"promotion_ready = {report['promotion_ready']}")


if __name__ == "__main__":
    main()
