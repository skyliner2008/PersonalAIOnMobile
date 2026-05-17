import sqlite3
import json
import os
import sys
from datetime import datetime

# ==============================================================================
# 📊 Analyze Logs (Refactored for High Precision)
# เปลี่ยนมาดึงข้อมูลจาก Database (mt5-core.db) และ JSONL แทนการงมจาก log.txt
# ==============================================================================

# รับค่าวันที่จาก Command Line, ถ้าไม่ระบุใช้วันนี้
target_date = sys.argv[1] if len(sys.argv) > 1 else datetime.now().strftime('%Y-%m-%d')

db_path = os.path.join('..', 'data', 'mt5-core.db')
jsonl_path = os.path.join('..', 'data', 'trade_decision_logs', f'{target_date}.jsonl')
out_report = os.path.join(os.path.dirname(__file__), 'analysis_report.json')

if not os.path.exists(db_path):
    print(f"❌ Error: Database not found at {db_path}")
    sys.exit(1)

executed_orders = []
blocked_orders = []
closed_trades = []

# 1. ดึง Executed และ Closed trades จาก SQLite (แม่นยำ 100%)
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Start/End timestamp of the target date
dt_start = int(datetime.strptime(f"{target_date} 00:00:00", "%Y-%m-%d %H:%M:%S").timestamp() * 1000)
dt_end = int(datetime.strptime(f"{target_date} 23:59:59", "%Y-%m-%d %H:%M:%S").timestamp() * 1000)

cursor.execute('''
    SELECT * FROM auto_trading_journal 
    WHERE created_at >= ? AND created_at <= ? AND mt5_ticket IS NOT NULL
''', (dt_start, dt_end))

for row in cursor.fetchall():
    executed_orders.append({
        'time': datetime.fromtimestamp(row['created_at']/1000).isoformat(),
        'ticket': row['mt5_ticket'],
        'symbol': row['symbol'],
        'side': row['side'],
        'strategy': row['strategy'],
        'entry': row['entry'],
        'sl': row['sl'],
        'tp': row['tp'],
        'rrr': row['rrr']
    })

    if row['outcome'] in ('WIN', 'LOSS', 'BE'):
        closed_trades.append({
            'time': datetime.fromtimestamp(row['updated_at']/1000).isoformat(),
            'ticket': row['mt5_ticket'],
            'symbol': row['symbol'],
            'outcome': row['outcome'],
            'profit': row['profit'],
            'profit_r': row['profit_r']
        })

conn.close()

# 2. ดึง Blocked Signals (SKIP) จาก JSONL
if os.path.exists(jsonl_path):
    with open(jsonl_path, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip(): continue
            try:
                j = json.loads(line)
                if j.get('side') == 'SKIP' or j.get('decisionType') == 'ORDER_BLOCKED':
                    # Parse context from JSON
                    det = {}
                    try:
                        det = json.loads(j.get('deterministicJson', '{}'))
                    except:
                        pass
                        
                    blocked_orders.append({
                        'time': j.get('atLocal', j.get('atIso', '')),
                        'symbol': j.get('symbol', ''),
                        'entry': j.get('entry'),
                        'sl': j.get('sl'),
                        'tp': j.get('tp'),
                        'reason': j.get('riskGate', 'Unknown Block Reason'),
                        'strategy': j.get('strategy'),
                        'deterministic': det
                    })
            except Exception as e:
                pass
else:
    print(f"⚠️ Warning: JSONL file {jsonl_path} not found for skipped signals.")

report = {
    'date': target_date,
    'executed': executed_orders,
    'blocked': blocked_orders,
    'closed': closed_trades
}

with open(out_report, 'w', encoding='utf-8') as f:
    json.dump(report, f, indent=2, ensure_ascii=False)

print(f"✅ Analysis complete for {target_date}.")
print(f"   Executed : {len(executed_orders)}")
print(f"   Blocked  : {len(blocked_orders)}")
print(f"   Closed   : {len(closed_trades)}")
print(f"   Saved to : {out_report}")
