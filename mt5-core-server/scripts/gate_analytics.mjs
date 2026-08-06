import Database from 'better-sqlite3';
import { resolve, join } from 'path';

const DB_PATH = resolve(process.cwd(), 'data/mt5-core.db');
const db = new Database(DB_PATH, { readonly: true });

const args = process.argv.slice(2);
const targetCategory = args[0] ? args[0].toUpperCase() : null;

console.log('================================================================');
console.log('                 🔍 GATE DIAGNOSTICS & ANALYTICS                ');
console.log('================================================================');

if (!targetCategory) {
  // --- MODE: SUMMARY ---
  console.log('\n📊 SUMMARY OF ALL BLOCKS (Last 7 Days)');
  console.log('Run `npm run analytics:gate <CATEGORY>` for detailed order breakdown.\n');

  const rows = db.prepare(`
    SELECT block_category, COUNT(*) as count
    FROM auto_trading_decision_feed
    WHERE decision_type = 'ORDER_BLOCKED'
      AND block_category IS NOT NULL
      AND created_at >= (strftime('%s', 'now') - 7*86400)*1000
    GROUP BY block_category
    ORDER BY count DESC
  `).all();

  console.table(rows.reduce((acc, row) => {
    acc[row.block_category] = { 'Blocked Orders': row.count };
    return acc;
  }, {}));

} else {
  // --- MODE: DETAIL ---
  console.log(`\n📋 DETAILED BLOCKS FOR CATEGORY: ${targetCategory}`);
  
  const rows = db.prepare(`
    SELECT at_iso, symbol, timeframe, side, strategy, risk_gate, gate_trace_json
    FROM auto_trading_decision_feed
    WHERE decision_type = 'ORDER_BLOCKED'
      AND block_category = ?
    ORDER BY created_at DESC
    LIMIT 20
  `).all(targetCategory);

  if (rows.length === 0) {
    console.log(`\nNo blocked orders found for category: ${targetCategory}`);
  }

  rows.forEach((row, i) => {
    console.log(`\n[${i + 1}] ${row.at_iso} | ${row.symbol} | ${row.side} | ${row.strategy}`);
    console.log(`Reason: ${row.risk_gate}`);
    
    try {
      const trace = JSON.parse(row.gate_trace_json || '[]');
      // Find the specific step that blocked it
      const blockStep = trace.find(s => ['BLOCK', 'SKIP'].includes(s.status));
      if (blockStep && blockStep.data) {
        console.log(`Data Snapshot:`);
        Object.entries(blockStep.data).forEach(([key, val]) => {
            if (typeof val === 'object' && val !== null) {
                console.log(`  - ${key}: ${JSON.stringify(val)}`);
            } else {
                console.log(`  - ${key}: ${val}`);
            }
        });
      } else {
        console.log(`Data Snapshot: (No structured trace data available for this block)`);
      }
    } catch (e) {
      console.log(`Data Snapshot: (Error parsing trace json)`);
    }
  });
  
  console.log(`\nShowing up to 20 most recent blocks for ${targetCategory}.`);
}
