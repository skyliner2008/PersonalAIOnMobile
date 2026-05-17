import Database from 'better-sqlite3';
const db = new Database('data/mt5-core.db', { readonly: true });

// Convert dates to timestamps (local time assumed or UTC depending on DB)
const startTs = new Date('2026-05-15T00:00:00Z').getTime();
const endTs = new Date('2026-05-16T23:59:59Z').getTime();

const trades = db.prepare(`
  SELECT outcome, profit, profit_r
  FROM auto_trading_journal 
  WHERE mt5_ticket IS NOT NULL 
    AND outcome IN ('WIN', 'LOSS', 'BE')
    AND created_at >= ? AND created_at <= ?
`).all(startTs, endTs);

let wins = 0, losses = 0, bes = 0;
let grossProfit = 0, grossLoss = 0;

trades.forEach(t => {
  if (t.outcome === 'WIN') { wins++; grossProfit += (t.profit || 0); }
  else if (t.outcome === 'LOSS') { losses++; grossLoss += Math.abs(t.profit || 0); }
  else if (t.outcome === 'BE') { bes++; }
});

const totalTrades = wins + losses + bes;
const winRate = totalTrades > 0 && (wins + losses) > 0 ? ((wins / (wins + losses)) * 100).toFixed(2) : 0;
const netProfit = (grossProfit - grossLoss).toFixed(2);

console.log('--- STATS FOR 2026-05-15 to 2026-05-16 ---');
console.log(`Total Trades Executed : ${totalTrades}`);
console.log(`Wins                  : ${wins}`);
console.log(`Losses                : ${losses}`);
console.log(`Break Even (BE)       : ${bes}`);
console.log(`Win Rate (excl. BE)   : ${winRate}%`);
console.log(`Net Profit            : $${netProfit}`);
