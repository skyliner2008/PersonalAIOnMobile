#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
📊 Analyze Logs — DB-First Analysis Report Generator
ดึงข้อมูลจาก auto_trading_decision_feed + auto_trading_journal โดยตรง
ใช้ Asia/Bangkok timezone เพื่อให้ตรงกับ operational day

Usage:
  python scripts/analyze_logs.py [YYYY-MM-DD]
  ถ้าไม่ระบุวันที่จะใช้วันนี้ (Asia/Bangkok)
"""
import sqlite3
import json
import os
import sys
from datetime import datetime, timezone, timedelta

# ==============================================================================
# Fix Windows console encoding (GBK → UTF-8)
# ==============================================================================
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

# ==============================================================================
# Constants
# ==============================================================================
TZ_BKK = timezone(timedelta(hours=7))

# Resolve paths from script location — works from any cwd
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DB_PATH = os.path.join(PROJECT_ROOT, 'data', 'mt5-core.db')
OUT_REPORT = os.path.join(SCRIPT_DIR, 'analysis_report.json')

# Target date (default = today in Bangkok timezone)
target_date = sys.argv[1] if len(sys.argv) > 1 else datetime.now(TZ_BKK).strftime('%Y-%m-%d')


def local_day_range_ms(date_str):
    """Convert YYYY-MM-DD to Bangkok timezone start/end timestamps in ms."""
    dt_start = datetime.strptime(f"{date_str} 00:00:00", "%Y-%m-%d %H:%M:%S").replace(tzinfo=TZ_BKK)
    dt_end   = datetime.strptime(f"{date_str} 23:59:59", "%Y-%m-%d %H:%M:%S").replace(tzinfo=TZ_BKK)
    return int(dt_start.timestamp() * 1000), int(dt_end.timestamp() * 1000) + 999


def fmt_time(ts_ms):
    """Format millisecond timestamp to ISO string in Bangkok timezone."""
    if not ts_ms:
        return None
    return datetime.fromtimestamp(ts_ms / 1000, tz=TZ_BKK).isoformat()


# ==============================================================================
# Main
# ==============================================================================
if not os.path.exists(DB_PATH):
    print(f"[ERROR] Database not found at {DB_PATH}")
    sys.exit(1)

conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row

start_ms, end_ms = local_day_range_ms(target_date)

# ---------------------------------------------------------
# 1. Executed Orders from Journal
# ---------------------------------------------------------
rows = conn.execute('''
    SELECT * FROM auto_trading_journal
    WHERE created_at >= ? AND created_at <= ? AND mt5_ticket IS NOT NULL
''', (start_ms, end_ms)).fetchall()

executed_orders = []
closed_trades = []

for row in rows:
    executed_orders.append({
        'time': fmt_time(row['created_at']),
        'ticket': row['mt5_ticket'],
        'symbol': row['symbol'],
        'side': row['side'],
        'strategy': row['strategy'],
        'entry': row['entry'],
        'sl': row['sl'],
        'tp': row['tp'],
        'rrr': row['rrr'],
    })
    if row['outcome'] in ('WIN', 'LOSS', 'BE'):
        closed_trades.append({
            'time': fmt_time(row['updated_at']),
            'ticket': row['mt5_ticket'],
            'symbol': row['symbol'],
            'outcome': row['outcome'],
            'profit': row['profit'],
            'profit_r': row['profit_r'],
        })

# ---------------------------------------------------------
# 2. Blocked Signals from Decision Feed (DB — not JSONL)
# ---------------------------------------------------------
blocked_rows = conn.execute('''
    SELECT * FROM auto_trading_decision_feed
    WHERE created_at >= ? AND created_at <= ?
      AND (decision_type = 'ORDER_BLOCKED' OR side = 'SKIP')
    ORDER BY created_at ASC
''', (start_ms, end_ms)).fetchall()

blocked_orders = []
for row in blocked_rows:
    det = {}
    try:
        det = json.loads(row['deterministic_json'] or '{}')
    except Exception:
        pass

    blocked_orders.append({
        'time': row['at_iso'] or fmt_time(row['created_at']),
        'symbol': row['symbol'],
        'entry': row['entry'],
        'sl': row['sl'],
        'tp': row['tp'],
        'reason': row['risk_gate'] or 'Unknown Block Reason',
        'strategy': row['strategy'],
        'blockCategory': row['block_category'],
        'decisionType': row['decision_type'],
        'deterministic': det,
    })

# ---------------------------------------------------------
# 3. Total cycle count for rejection rate
# ---------------------------------------------------------
total_cycles = conn.execute('''
    SELECT COUNT(*) FROM auto_trading_decision_feed
    WHERE created_at >= ? AND created_at <= ?
''', (start_ms, end_ms)).fetchone()[0]

conn.close()

# ---------------------------------------------------------
# 4. Build & save report
# ---------------------------------------------------------
report = {
    'date': target_date,
    'timezone': 'Asia/Bangkok',
    'totalCycles': total_cycles,
    'executed': executed_orders,
    'blocked': blocked_orders,
    'closed': closed_trades,
}

with open(OUT_REPORT, 'w', encoding='utf-8') as f:
    json.dump(report, f, indent=2, ensure_ascii=False)

rejection_pct = f"{len(blocked_orders)/total_cycles*100:.1f}%" if total_cycles > 0 else "N/A"

print(f"[OK] Analysis complete for {target_date} (Asia/Bangkok)")
print(f"   Total Cycles : {total_cycles}")
print(f"   Executed     : {len(executed_orders)}")
print(f"   Blocked      : {len(blocked_orders)}")
print(f"   Closed       : {len(closed_trades)}")
print(f"   Rejection    : {rejection_pct}")
print(f"   Saved to     : {OUT_REPORT}")
