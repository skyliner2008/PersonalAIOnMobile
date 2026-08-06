/**
 * V26.25 — Enhanced Gate Analytics Script
 * 
 * วิเคราะห์ประสิทธิภาพของแต่ละ Gate:
 * - Block count per gate
 * - Block-to-Pass ratio  
 * - Overlap detection (gates ที่ block เดียวกัน)
 * - Quality analysis: ออเดอร์ที่ผ่านได้ win/loss ratio เป็นอย่างไร
 *
 * Usage:
 *   node scripts/gate_flow_audit.mjs [date]
 *   node scripts/gate_flow_audit.mjs 2026-05-22
 *   node scripts/gate_flow_audit.mjs   (default: last 7 days)
 */
import Database from 'better-sqlite3';
import { resolve } from 'path';

const DB_PATH = resolve(process.cwd(), 'data/mt5-core.db');
const db = new Database(DB_PATH, { readonly: true });

const dateArg = process.argv[2];
let dateFilter = '';
let dateLabel = 'Last 7 Days';

if (dateArg) {
  const d = dateArg.replace(/-/g, '');
  const startMs = new Date(dateArg + 'T00:00:00Z').getTime();
  const endMs = startMs + 86400000;
  dateFilter = `AND created_at >= ${startMs} AND created_at < ${endMs}`;
  dateLabel = dateArg;
} else {
  dateFilter = `AND created_at >= (strftime('%s', 'now') - 7*86400)*1000`;
}

console.log('================================================================');
console.log('          🔍 V26.25 GATE FLOW AUDIT & ANALYTICS');
console.log(`          📅 Period: ${dateLabel}`);
console.log('================================================================');

// ============================================================================
// 1. GATE BLOCK SUMMARY
// ============================================================================
console.log('\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('  📊 SECTION 1: GATE BLOCK FREQUENCY');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

const blockRows = db.prepare(`
  SELECT block_category, COUNT(*) as block_count
  FROM auto_trading_decision_feed
  WHERE decision_type = 'ORDER_BLOCKED'
    AND block_category IS NOT NULL
    ${dateFilter}
  GROUP BY block_category
  ORDER BY block_count DESC
`).all();

const totalBlocked = blockRows.reduce((s, r) => s + r.block_count, 0);
const totalDecisions = db.prepare(`
  SELECT COUNT(*) as c FROM auto_trading_decision_feed
  WHERE decision_type IN ('ORDER_BLOCKED', 'ORDER_EXECUTED', 'NO_SIGNAL')
    ${dateFilter}
`).pluck().get() || 0;
const totalExecuted = db.prepare(`
  SELECT COUNT(*) as c FROM auto_trading_decision_feed
  WHERE decision_type = 'ORDER_EXECUTED'
    ${dateFilter}
`).pluck().get() || 0;
const totalNoSignal = db.prepare(`
  SELECT COUNT(*) as c FROM auto_trading_decision_feed
  WHERE decision_type = 'NO_SIGNAL'
    ${dateFilter}
`).pluck().get() || 0;

console.log(`Total Decisions: ${totalDecisions}`);
console.log(`  → Executed: ${totalExecuted} (${totalDecisions > 0 ? ((totalExecuted/totalDecisions)*100).toFixed(1) : 0}%)`);
console.log(`  → Blocked:  ${totalBlocked} (${totalDecisions > 0 ? ((totalBlocked/totalDecisions)*100).toFixed(1) : 0}%)`);
console.log(`  → No Signal: ${totalNoSignal} (${totalDecisions > 0 ? ((totalNoSignal/totalDecisions)*100).toFixed(1) : 0}%)\n`);

if (blockRows.length > 0) {
  console.log('Gate Block Breakdown:');
  console.log('─'.repeat(70));
  console.log(`${'Gate'.padEnd(35)} ${'Count'.padStart(7)} ${'% of Blocks'.padStart(12)}`);
  console.log('─'.repeat(70));
  for (const row of blockRows) {
    const pct = totalBlocked > 0 ? ((row.block_count / totalBlocked) * 100).toFixed(1) : '0.0';
    console.log(`${String(row.block_category).padEnd(35)} ${String(row.block_count).padStart(7)} ${(pct + '%').padStart(12)}`);
  }
  console.log('─'.repeat(70));
}

// ============================================================================
// 2. GATE TRACE ANALYSIS — which gates actually fired
// ============================================================================
console.log('\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('  📊 SECTION 2: GATE TRACE DEEP DIVE');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

const traceRows = db.prepare(`
  SELECT gate_trace_json
  FROM auto_trading_decision_feed
  WHERE decision_type = 'ORDER_BLOCKED'
    AND gate_trace_json IS NOT NULL
    ${dateFilter}
`).all();

const gateHits = {};
const gateBlockReasons = {};

for (const row of traceRows) {
  try {
    const trace = JSON.parse(row.gate_trace_json || '[]');
    for (const step of trace) {
      if (!step.gate) continue;
      if (!gateHits[step.gate]) gateHits[step.gate] = { block: 0, allow: 0, skip: 0, fail: 0 };
      if (step.status === 'BLOCK') {
        gateHits[step.gate].block++;
        // Track reasons
        if (!gateBlockReasons[step.gate]) gateBlockReasons[step.gate] = {};
        const shortReason = String(step.reason || '').substring(0, 60);
        gateBlockReasons[step.gate][shortReason] = (gateBlockReasons[step.gate][shortReason] || 0) + 1;
      }
      else if (step.status === 'ALLOW') gateHits[step.gate].allow++;
      else if (step.status === 'SKIP') gateHits[step.gate].skip++;
      else gateHits[step.gate].fail++;
    }
  } catch { /* ignore parse errors */ }
}

const gateEntries = Object.entries(gateHits).sort((a, b) => b[1].block - a[1].block);
if (gateEntries.length > 0) {
  console.log('Per-Gate Trace Statistics (from gateTrace JSON):');
  console.log('─'.repeat(80));
  console.log(`${'Gate'.padEnd(30)} ${'BLOCK'.padStart(7)} ${'ALLOW'.padStart(7)} ${'SKIP'.padStart(7)} ${'Block Rate'.padStart(12)}`);
  console.log('─'.repeat(80));
  for (const [gate, stats] of gateEntries) {
    const total = stats.block + stats.allow;
    const rate = total > 0 ? ((stats.block / total) * 100).toFixed(1) + '%' : 'N/A';
    console.log(`${gate.padEnd(30)} ${String(stats.block).padStart(7)} ${String(stats.allow).padStart(7)} ${String(stats.skip).padStart(7)} ${rate.padStart(12)}`);
  }
  console.log('─'.repeat(80));

  // Top block reasons per gate
  console.log('\nTop Block Reasons per Gate:');
  for (const [gate, reasons] of Object.entries(gateBlockReasons)) {
    const sorted = Object.entries(reasons).sort((a, b) => b[1] - a[1]).slice(0, 3);
    if (sorted.length > 0) {
      console.log(`\n  ${gate}:`);
      for (const [reason, count] of sorted) {
        console.log(`    (${count}×) ${reason}`);
      }
    }
  }
}

// ============================================================================
// 3. EXECUTED ORDER QUALITY
// ============================================================================
console.log('\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('  📊 SECTION 3: EXECUTED ORDER QUALITY');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

const closedRows = db.prepare(`
  SELECT symbol, strategy, side, profit, profit_r, rrr
  FROM auto_trading_journal
  WHERE outcome IN ('WIN', 'LOSS', 'BE')
    AND was_executed = 1
    ${dateFilter.replace(/created_at/g, 'created_at')}
`).all();

if (closedRows.length > 0) {
  const wins = closedRows.filter(r => r.profit > 0);
  const losses = closedRows.filter(r => r.profit < 0);
  const be = closedRows.filter(r => r.profit === 0);
  const avgWin = wins.length > 0 ? wins.reduce((s, r) => s + r.profit, 0) / wins.length : 0;
  const avgLoss = losses.length > 0 ? losses.reduce((s, r) => s + r.profit, 0) / losses.length : 0;
  const totalPnl = closedRows.reduce((s, r) => s + (r.profit || 0), 0);
  const avgRR = closedRows.length > 0 ? closedRows.reduce((s, r) => s + (r.profit_r || 0), 0) / closedRows.length : 0;

  console.log(`Closed Trades: ${closedRows.length}`);
  console.log(`  → Wins:   ${wins.length} (${((wins.length/closedRows.length)*100).toFixed(1)}%)`);
  console.log(`  → Losses: ${losses.length} (${((losses.length/closedRows.length)*100).toFixed(1)}%)`);
  console.log(`  → BE:     ${be.length}`);
  console.log(`  → Avg Win:  $${avgWin.toFixed(2)}`);
  console.log(`  → Avg Loss: $${avgLoss.toFixed(2)}`);
  console.log(`  → Win/Loss Ratio: ${avgLoss !== 0 ? (Math.abs(avgWin/avgLoss)).toFixed(2) : 'N/A'}`);
  console.log(`  → Net PnL: $${totalPnl.toFixed(2)}`);
  console.log(`  → Avg R:   ${avgRR.toFixed(3)}R`);

  // Per-strategy breakdown
  const strategies = {};
  for (const r of closedRows) {
    const s = r.strategy || 'UNKNOWN';
    if (!strategies[s]) strategies[s] = { wins: 0, losses: 0, pnl: 0, count: 0 };
    strategies[s].count++;
    strategies[s].pnl += r.profit || 0;
    if (r.profit > 0) strategies[s].wins++;
    else if (r.profit < 0) strategies[s].losses++;
  }

  console.log('\nPer-Strategy Breakdown:');
  console.log('─'.repeat(75));
  console.log(`${'Strategy'.padEnd(28)} ${'Count'.padStart(6)} ${'Win%'.padStart(6)} ${'PnL'.padStart(10)} ${'Avg R'.padStart(8)}`);
  console.log('─'.repeat(75));
  for (const [strat, data] of Object.entries(strategies).sort((a, b) => b[1].count - a[1].count)) {
    const winRate = data.count > 0 ? ((data.wins / data.count) * 100).toFixed(0) : '0';
    const stratRows = closedRows.filter(r => r.strategy === strat);
    const stratAvgR = stratRows.length > 0 ? stratRows.reduce((s, r) => s + (r.profit_r || 0), 0) / stratRows.length : 0;
    console.log(`${strat.padEnd(28)} ${String(data.count).padStart(6)} ${(winRate + '%').padStart(6)} ${('$' + data.pnl.toFixed(2)).padStart(10)} ${stratAvgR.toFixed(3).padStart(8)}`);
  }
  console.log('─'.repeat(75));
} else {
  console.log('No closed trades found in this period.');
}

// ============================================================================
// 4. GATE OVERLAP DETECTION
// ============================================================================
console.log('\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('  📊 SECTION 4: GATE OVERLAP DETECTION');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

// Find decisions where multiple gates would have blocked (recorded in trace)
const overlapTraces = db.prepare(`
  SELECT gate_trace_json, risk_gate, symbol, at_iso
  FROM auto_trading_decision_feed
  WHERE decision_type = 'ORDER_BLOCKED'
    AND gate_trace_json IS NOT NULL
    ${dateFilter}
`).all();

const overlapPairs = {};
let multiBlockCount = 0;
for (const row of overlapTraces) {
  try {
    const trace = JSON.parse(row.gate_trace_json || '[]');
    const blockGates = trace.filter(s => s.status === 'BLOCK').map(s => s.gate);
    if (blockGates.length >= 2) {
      multiBlockCount++;
      for (let i = 0; i < blockGates.length; i++) {
        for (let j = i + 1; j < blockGates.length; j++) {
          const pair = [blockGates[i], blockGates[j]].sort().join(' ↔ ');
          overlapPairs[pair] = (overlapPairs[pair] || 0) + 1;
        }
      }
    }
  } catch { /* ignore */ }
}

console.log(`Decisions blocked by 2+ gates simultaneously: ${multiBlockCount} / ${overlapTraces.length} (${overlapTraces.length > 0 ? ((multiBlockCount/overlapTraces.length)*100).toFixed(1) : 0}%)`);

const sortedPairs = Object.entries(overlapPairs).sort((a, b) => b[1] - a[1]);
if (sortedPairs.length > 0) {
  console.log('\nMost Common Overlapping Gate Pairs:');
  console.log('─'.repeat(60));
  for (const [pair, count] of sortedPairs.slice(0, 10)) {
    console.log(`  (${count}×) ${pair}`);
  }
  console.log('─'.repeat(60));
  console.log('\n⚠️  Overlapping pairs indicate potential gate consolidation targets.');
}

console.log('\n\n✅ Analysis complete.');
db.close();
