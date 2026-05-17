import json
import os
from collections import defaultdict, Counter

# ==============================================================================
# 🧠 Deep Analysis (Refactored)
# อ่านจาก analysis_report.json ที่แม่นยำสูง และสรุปพฤติกรรมของ AutoEngine
# ==============================================================================

report_path = os.path.join(os.path.dirname(__file__), 'analysis_report.json')

if not os.path.exists(report_path):
    print(f"❌ Error: Report file {report_path} not found. Please run analyze_logs.py first.")
    exit(1)

with open(report_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

blocked = data['blocked']
executed = data['executed']
date = data.get('date', 'Unknown')

# ---------------------------------------------------------
# 1. Categorize Blocked Signals
# ---------------------------------------------------------
categories = defaultdict(list)

for sig in blocked:
    reason = str(sig.get('reason', ''))
    
    if 'MTF zone gate veto' in reason:
        categories['MTF_ZONE_GATE_VETO'].append(sig)
    elif 'Sequential Entry blocked: NEAR_DUPLICATE_ENTRY' in reason:
        categories['NEAR_DUPLICATE_ENTRY'].append(sig)
    elif 'Sequential Entry blocked' in reason:
        categories['SEQUENTIAL_ENTRY_BLOCKED'].append(sig)
    elif 'RSI oversold gate' in reason or 'RSI overbought gate' in reason:
        categories['RSI_EXTREME_GATE'].append(sig)
    elif 'AI unavailable' in reason:
        categories['AI_UNAVAILABLE_NO_FALLBACK'].append(sig)
    elif '[V25]' in reason and 'wall stars' in reason.lower():
        categories['V25_WALL_STARS_LOW'].append(sig)
    elif '[V25]' in reason and 'BLOCKED' in reason:
        categories['V25_BLOCKED_BY_SUPPORT_RESISTANCE'].append(sig)
    elif '[V25]' in reason:
        categories['V25_OTHER_FILTER'].append(sig)
    elif '[EA-ONLY]' in reason:
        categories['EA_ONLY_GATE'].append(sig)
    else:
        categories['OTHER'].append(sig)

print("=" * 80)
print(f"DEEP ANALYSIS OF {len(blocked)} BLOCKED SIGNALS ({date})")
print("=" * 80)

for cat, sigs in sorted(categories.items(), key=lambda x: -len(x[1])):
    print(f"\n{'='*60}")
    print(f"Category: {cat} | Count: {len(sigs)}")
    print(f"{'='*60}")
    
    times = sorted([str(s.get('time', '')) for s in sigs if s.get('time')])
    if times:
        print(f"  Time Range: {times[0]} → {times[-1]}")
    
    entries = [float(s['entry']) for s in sigs if s.get('entry')]
    if entries:
        print(f"  Entry Range: {min(entries):.2f} → {max(entries):.2f}")
    
    # Show unique reason variants
    unique_reasons = set(s.get('reason', '') for s in sigs)
    print(f"  Unique Reason Variants: {len(unique_reasons)}")
    for r in sorted(unique_reasons):
        count = sum(1 for s in sigs if s.get('reason') == r)
        print(f"    [{count}x] {r[:100]}...")
    
    # Examples
    print(f"  Examples:")
    for s in sigs[:2]:
        print(f"    {s.get('time')} | {s.get('symbol')} | Entry={s.get('entry')} SL={s.get('sl')} TP={s.get('tp')}")

# ---------------------------------------------------------
# 2. Deterministic Context Distribution
# ---------------------------------------------------------
print("\n" + "=" * 80)
print("DETERMINISTIC LOGIC & CONFLICT ANALYSIS")
print("=" * 80)

det_sides = []
det_zones = []
det_mgmts = []
confidences = {'BUY': [], 'SELL': []}

for sig in blocked:
    det = sig.get('deterministic', {})
    if not det: continue
    
    action = det.get('action')
    if action: det_sides.append(action)
    
    zone = det.get('zoneAtEntry')
    if zone: det_zones.append(zone)
    
    mgmt = det.get('management')
    if mgmt: det_mgmts.append(mgmt)
    
    conf = det.get('confidence')
    if conf and action in confidences:
        try:
            confidences[action].append(float(conf))
        except:
            pass

print("\n--- Intended Actions Before Blocks ---")
for side, cnt in Counter(det_sides).most_common():
    print(f"  {side}: {cnt}")

print("\n--- Market Zones During Blocks ---")
for zone, cnt in Counter(det_zones).most_common():
    print(f"  {zone}: {cnt}")

print("\n--- Confidence Analysis ---")
for side in ['BUY', 'SELL']:
    confs = confidences[side]
    if confs:
        print(f"  {side} Confidence: avg={sum(confs)/len(confs):.1f}%, min={min(confs):.1f}%, max={max(confs):.1f}%")

print("\n" + "=" * 80)
print(f"SUMMARY OF EXECUTED TRADES: {len(executed)}")
print("=" * 80)
for e in executed:
    print(f"  {e.get('time')} | {e.get('ticket')} | {e.get('side')} {e.get('symbol')} @ {e.get('entry')} (RRR {e.get('rrr')})")
