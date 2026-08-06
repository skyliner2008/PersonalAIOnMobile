#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🧠 Deep Analysis — DB-Direct Blocked Signal Analyzer
อ่านจาก auto_trading_decision_feed โดยตรง (ไม่พึ่ง analysis_report.json)

Usage:
  python scripts/deep_analysis.py [YYYY-MM-DD]
  ถ้าไม่ระบุวันที่จะวิเคราะห์ทุกวัน (All Time)
"""
import sqlite3
import json
import os
import sys
from collections import defaultdict, Counter
from datetime import datetime, timezone, timedelta

# ==============================================================================
# Fix Windows console encoding
# ==============================================================================
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

# ==============================================================================
# Constants
# ==============================================================================
TZ_BKK = timezone(timedelta(hours=7))
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DB_PATH = os.path.join(PROJECT_ROOT, 'data', 'mt5-core.db')

target_date = sys.argv[1] if len(sys.argv) > 1 else None

CATEGORY_LABELS = {
    'ZONE_GATE': 'MTF Zone Gate / Premium-Discount Block',
    'RISK_GATE': 'Risk Parameters Gate',
    'PROXIMITY_GATE': 'Zone-Aware Guard (Wait for Better Edge)',
    'EA_FALLBACK_CONFIDENCE_LOW': 'AI Offline / EA-Only Fallback Guard',
    'DECISION_SIDE_GUARD': 'Decision Side Guard / Technical Signal Conflict',
    'M15_SMC_PRESSURE': 'M15 SMC Pressure Guard',
    'ANTI_HEDGE': 'Anti-Hedge / Opposite Position Guard',
    'FITNESS_GATE': 'Fitness Below Minimum',
    'V25_ONLY_GATE': 'V25 Wall Engine Veto',
    'SEQUENTIAL_DUPLICATE': 'Sequential Entry / Duplicate Block',
    'RRR_OR_REWARD_RISK': 'RRR / Reward-Risk Filter',
    'FVG_ALIGNMENT': 'FVG Alignment Block',
    'NO_DETERMINISTIC_SIGNAL': 'No Deterministic Signal',
    'ORDER_SEND_FAILED': 'Order Send Failed',
    'SKIP_OTHER': 'Other Skip',
}


def local_day_range_ms(date_str):
    dt_start = datetime.strptime(f"{date_str} 00:00:00", "%Y-%m-%d %H:%M:%S").replace(tzinfo=TZ_BKK)
    dt_end   = datetime.strptime(f"{date_str} 23:59:59", "%Y-%m-%d %H:%M:%S").replace(tzinfo=TZ_BKK)
    return int(dt_start.timestamp() * 1000), int(dt_end.timestamp() * 1000) + 999


def normalized_block_category(row):
    gate = (row['risk_gate'] or '').upper()
    if 'NO_DETERMINISTIC_SIGNAL' in gate:
        return 'NO_DETERMINISTIC_SIGNAL'
    if 'EA_FALLBACK_CONFIDENCE_LOW' in gate:
        return 'EA_FALLBACK_CONFIDENCE_LOW'
    if 'DECISION SIDE GUARD' in gate:
        return 'DECISION_SIDE_GUARD'
    if 'M15 SMC PRESSURE GUARD' in gate:
        return 'M15_SMC_PRESSURE'
    if 'ANTI-HEDGE' in gate or 'ANTI HEDGE' in gate:
        return 'ANTI_HEDGE'
    if 'FITNESS' in gate and 'BELOW MIN' in gate:
        return 'FITNESS_GATE'
    return row['block_category'] or 'UNKNOWN'


# ==============================================================================
# Main
# ==============================================================================
if not os.path.exists(DB_PATH):
    print(f"[ERROR] Database not found at {DB_PATH}")
    sys.exit(1)

conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row

date_label = target_date if target_date else 'ALL TIME'

# ---------------------------------------------------------
# 1. Blocked Signals
# ---------------------------------------------------------
blocked_query = '''
    SELECT * FROM auto_trading_decision_feed
    WHERE (decision_type = 'ORDER_BLOCKED' OR side = 'SKIP')
'''
params = []
if target_date:
    start_ms, end_ms = local_day_range_ms(target_date)
    blocked_query += ' AND created_at >= ? AND created_at <= ?'
    params = [start_ms, end_ms]
blocked_query += ' ORDER BY created_at ASC'

blocked_rows = conn.execute(blocked_query, params).fetchall()

# ---------------------------------------------------------
# 2. Executed Trades
# ---------------------------------------------------------
exec_query = '''
    SELECT * FROM auto_trading_journal
    WHERE mt5_ticket IS NOT NULL AND outcome IN ('WIN', 'LOSS', 'BE')
'''
exec_params = []
if target_date:
    exec_query += ' AND created_at >= ? AND created_at <= ?'
    exec_params = [start_ms, end_ms]
exec_query += ' ORDER BY created_at ASC'

executed_rows = conn.execute(exec_query, exec_params).fetchall()

# ---------------------------------------------------------
# 3. Total Decisions
# ---------------------------------------------------------
total_query = 'SELECT COUNT(*) FROM auto_trading_decision_feed'
total_params = []
if target_date:
    total_query += ' WHERE created_at >= ? AND created_at <= ?'
    total_params = [start_ms, end_ms]

total_decisions = conn.execute(total_query, total_params).fetchone()[0]

# =============================================================
# SECTION 1: BLOCKED SIGNAL CATEGORIZATION (by block_category)
# =============================================================
categories = defaultdict(list)
for row in blocked_rows:
    cat = normalized_block_category(row)
    categories[cat].append(dict(row))

rejection_pct = f"{len(blocked_rows)/total_decisions*100:.1f}%" if total_decisions > 0 else "N/A"

print("=" * 90)
print(f"  DEEP ANALYSIS: {len(blocked_rows)} BLOCKED SIGNALS ({date_label})")
print(f"  Total Decisions: {total_decisions} | Rejection Rate: {rejection_pct}")
print("=" * 90)

for cat, sigs in sorted(categories.items(), key=lambda x: -len(x[1])):
    label = CATEGORY_LABELS.get(cat, cat)
    pct = len(sigs) / len(blocked_rows) * 100 if blocked_rows else 0

    print(f"\n{'=' * 70}")
    print(f"  Category : {label}")
    print(f"  DB Key   : {cat} | Count: {len(sigs)} ({pct:.1f}%)")
    print(f"{'=' * 70}")

    # Time range
    times = sorted([str(s.get('at_iso', '')) for s in sigs if s.get('at_iso')])
    if times:
        print(f"  Time Range  : {times[0]} -> {times[-1]}")

    # Entry range
    entries = [float(s['entry']) for s in sigs if s.get('entry') is not None]
    if entries:
        print(f"  Entry Range : {min(entries):.2f} -> {max(entries):.2f}")

    # Strategy distribution
    strats = Counter(s.get('strategy', '?') for s in sigs)
    print(f"  Strategies  : {dict(strats.most_common(5))}")

    # Unique risk_gate variants
    unique_gates = Counter(s.get('risk_gate', '') for s in sigs)
    print(f"  Gate Variants: {len(unique_gates)} unique")
    for gate, count in unique_gates.most_common(3):
        short = (gate or '?')[:100]
        print(f"    [{count:>4}x] {short}")

    # Examples
    print(f"  Examples:")
    for s in sigs[:2]:
        t = s.get('at_iso', '?')
        sym = s.get('symbol', '?')
        e = s.get('entry')
        sl = s.get('sl')
        tp = s.get('tp')
        print(f"    {t} | {sym} | Entry={e} SL={sl} TP={tp}")

# =============================================================
# SECTION 2: DETERMINISTIC CONTEXT ANALYSIS
# =============================================================
print("\n" + "=" * 90)
print("  DETERMINISTIC LOGIC & CONFLICT ANALYSIS")
print("=" * 90)

det_sides = []
det_zones = []
det_mgmts = []
confidences = {'BUY': [], 'SELL': []}
strategies_blocked = Counter()

for row in blocked_rows:
    det = {}
    try:
        det = json.loads(row['deterministic_json'] or '{}')
    except Exception:
        pass
    if not det:
        continue

    action = det.get('action')
    if action:
        det_sides.append(action)

    zone = det.get('zoneAtEntry')
    if zone:
        det_zones.append(zone)

    mgmt = det.get('management')
    if mgmt:
        det_mgmts.append(mgmt)

    conf = det.get('confidence')
    if conf and action in confidences:
        try:
            confidences[action].append(float(conf))
        except (ValueError, TypeError):
            pass

    strat = det.get('strategy') or row['strategy']
    if strat:
        strategies_blocked[strat] += 1

print("\n--- Intended Actions Before Blocks ---")
for side, cnt in Counter(det_sides).most_common():
    print(f"  {side}: {cnt}")

print("\n--- Market Zones During Blocks ---")
for zone, cnt in Counter(det_zones).most_common():
    print(f"  {zone}: {cnt}")

print("\n--- Management Intentions ---")
for mgmt, cnt in Counter(det_mgmts).most_common():
    print(f"  {mgmt}: {cnt}")

print("\n--- Confidence Distribution ---")
for side in ['BUY', 'SELL']:
    confs = confidences[side]
    if confs:
        print(f"  {side}: avg={sum(confs)/len(confs):.1f}%, "
              f"min={min(confs):.1f}%, max={max(confs):.1f}%, count={len(confs)}")

print("\n--- Strategies Blocked (Top 10) ---")
for strat, cnt in strategies_blocked.most_common(10):
    print(f"  {strat}: {cnt}")

# =============================================================
# SECTION 3: GATE TRACE ANALYSIS
# =============================================================
print("\n" + "=" * 90)
print("  GATE TRACE ANALYSIS")
print("=" * 90)

gate_pass_count = Counter()
gate_block_count = Counter()

for row in blocked_rows:
    try:
        traces = json.loads(row['gate_trace_json'] or '[]')
    except Exception:
        continue
    for gate in traces:
        name = gate.get('gate') or gate.get('name') or '?'
        status = str(gate.get('status', '')).upper()
        if status in ('PASS', 'OK', 'APPROVED'):
            gate_pass_count[name] += 1
        elif status in ('BLOCK', 'FAILED', 'SKIP', 'VETO'):
            gate_block_count[name] += 1

print("\n--- Gates That Block Most Often ---")
for gate, cnt in gate_block_count.most_common(10):
    total = gate_pass_count.get(gate, 0) + cnt
    block_rate = cnt / total * 100 if total > 0 else 0
    print(f"  {gate:40s} : {cnt:>5} blocks / {total:>5} total ({block_rate:.0f}% block rate)")

print("\n--- Gates That Pass Most Often ---")
for gate, cnt in gate_pass_count.most_common(10):
    total = cnt + gate_block_count.get(gate, 0)
    pass_rate = cnt / total * 100 if total > 0 else 0
    print(f"  {gate:40s} : {cnt:>5} passes / {total:>5} total ({pass_rate:.0f}% pass rate)")

# =============================================================
# SECTION 4: EXECUTED TRADES SUMMARY
# =============================================================
print("\n" + "=" * 90)
print(f"  EXECUTED TRADES SUMMARY: {len(executed_rows)} closed trades")
print("=" * 90)

total_profit = 0
total_r = 0
for e in executed_rows:
    profit = e['profit'] or 0
    profit_r = e['profit_r'] or 0
    total_profit += profit
    total_r += profit_r
    side = e['side']
    sym = e['symbol']
    strat = e['strategy']
    outcome = e['outcome']
    marker = '+' if outcome == 'WIN' else '-' if outcome == 'LOSS' else '~'
    print(f"  [{marker}] {outcome:4s} | {sym} {side:4s} | {strat:20s} | "
          f"PnL: ${profit:>8.2f} | R: {profit_r:>+6.2f}")

if executed_rows:
    wins = sum(1 for e in executed_rows if e['outcome'] == 'WIN')
    losses = sum(1 for e in executed_rows if e['outcome'] == 'LOSS')
    wr = wins / (wins + losses) * 100 if (wins + losses) > 0 else 0
    print(f"\n  Total PnL: ${total_profit:.2f} | Total R: {total_r:+.2f} | "
          f"WR: {wr:.1f}% ({wins}W / {losses}L)")

conn.close()

print("\n" + "=" * 90)
print("  Analysis Complete.")
print("=" * 90)
