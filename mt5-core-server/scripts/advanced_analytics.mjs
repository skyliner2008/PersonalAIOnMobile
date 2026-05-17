import Database from 'better-sqlite3';
import { readFileSync, existsSync } from 'fs';
import { join } from 'path';

// การใช้งาน: node scripts/advanced_analytics.mjs [YYYY-MM-DD]
// ถ้าไม่ระบุวันที่ จะวิเคราะห์ข้อมูลทั้งหมดในระบบ

const targetDate = process.argv[2] || null;
const dbPath = join(process.cwd(), 'data', 'mt5-core.db');

if (!existsSync(dbPath)) {
  console.error(`❌ ไม่พบฐานข้อมูลที่: ${dbPath}`);
  process.exit(1);
}

const db = new Database(dbPath, { readonly: true });

console.log('==================================================');
console.log(`📊 ADVANCED TRADING ANALYTICS REPORT`);
console.log(`📅 Date: ${targetDate ? targetDate : 'ALL TIME'}`);
console.log('==================================================\n');

// 1. ดึงข้อมูล Journal จาก SQLite
let journalQuery = `
  SELECT 
    mt5_ticket, symbol, side, strategy, rrr, 
    outcome, profit, profit_r, close_reason,
    created_at
  FROM auto_trading_journal 
  WHERE mt5_ticket IS NOT NULL 
    AND outcome IN ('WIN', 'LOSS', 'BE')
`;

const params = [];
if (targetDate) {
  // แปลง YYYY-MM-DD เป็น timestamp
  const startTs = new Date(`${targetDate}T00:00:00Z`).getTime();
  const endTs = new Date(`${targetDate}T23:59:59Z`).getTime();
  journalQuery += ` AND created_at >= ? AND created_at <= ?`;
  params.push(startTs, endTs);
}

journalQuery += ` ORDER BY created_at ASC`;

const trades = db.prepare(journalQuery).all(...params);

// สถิติรวม
let totalTrades = trades.length;
let wins = 0, losses = 0, bes = 0;
let grossProfit = 0, grossLoss = 0;
let totalR = 0;

let peak = 0;
let runningBalance = 0;
let maxDrawdown = 0;

let shortTrades = 0, shortWins = 0;
let longTrades = 0, longWins = 0;
let largestProfit = 0, largestLoss = 0;
let currentConsecutiveWins = 0, currentConsecutiveLosses = 0;
let maxConsecutiveWins = 0, maxConsecutiveLosses = 0;
let currentConsecutiveWinAmt = 0, currentConsecutiveLossAmt = 0;
let maxConsecutiveWinAmt = 0, maxConsecutiveLossAmt = 0;
let winStreaks = 0, lossStreaks = 0;

const strategyStats = {};

trades.forEach(t => {
  const pnl = t.profit || 0;
  runningBalance += pnl;
  if (runningBalance > peak) peak = runningBalance;
  const drawdown = peak - runningBalance;
  if (drawdown > maxDrawdown) maxDrawdown = drawdown;

  if (t.side === 'SELL') { shortTrades++; if (pnl > 0) shortWins++; }
  if (t.side === 'BUY') { longTrades++; if (pnl > 0) longWins++; }

  if (pnl > largestProfit) largestProfit = pnl;
  if (pnl < largestLoss) largestLoss = pnl;

  if (pnl > 0) {
    if (currentConsecutiveLosses > 0) { lossStreaks++; currentConsecutiveLosses = 0; currentConsecutiveLossAmt = 0; }
    currentConsecutiveWins++;
    currentConsecutiveWinAmt += pnl;
    if (currentConsecutiveWins > maxConsecutiveWins) maxConsecutiveWins = currentConsecutiveWins;
    if (currentConsecutiveWinAmt > maxConsecutiveWinAmt) maxConsecutiveWinAmt = currentConsecutiveWinAmt;
  } else if (pnl < 0) {
    if (currentConsecutiveWins > 0) { winStreaks++; currentConsecutiveWins = 0; currentConsecutiveWinAmt = 0; }
    currentConsecutiveLosses++;
    currentConsecutiveLossAmt += pnl;
    if (currentConsecutiveLosses > maxConsecutiveLosses) maxConsecutiveLosses = currentConsecutiveLosses;
    if (currentConsecutiveLossAmt < maxConsecutiveLossAmt) maxConsecutiveLossAmt = currentConsecutiveLossAmt;
  }

  if (t.outcome === 'WIN') { wins++; grossProfit += pnl; }
  else if (t.outcome === 'LOSS') { losses++; grossLoss += Math.abs(pnl); }
  else if (t.outcome === 'BE') { bes++; }

  totalR += (t.profit_r || 0);

  if (!strategyStats[t.strategy]) {
    strategyStats[t.strategy] = { count: 0, wins: 0, losses: 0, profit: 0 };
  }
  strategyStats[t.strategy].count++;
  if (t.outcome === 'WIN') strategyStats[t.strategy].wins++;
  if (t.outcome === 'LOSS') strategyStats[t.strategy].losses++;
  strategyStats[t.strategy].profit += (t.profit || 0);
});
if (currentConsecutiveWins > 0) winStreaks++;
if (currentConsecutiveLosses > 0) lossStreaks++;

const winRate = totalTrades > 0 ? ((wins / (wins + losses)) * 100).toFixed(2) : 0;
const netProfit = grossProfit - grossLoss;
const profitFactor = grossLoss > 0 ? (grossProfit / grossLoss).toFixed(2) : (grossProfit > 0 ? '∞' : '0');
const expectedPayoff = totalTrades > 0 ? (netProfit / totalTrades).toFixed(2) : 0;
const recoveryFactor = maxDrawdown > 0 ? (netProfit / maxDrawdown).toFixed(2) : (netProfit > 0 ? '∞' : '0');
const avgProfit = wins > 0 ? (grossProfit / wins).toFixed(2) : 0;
const avgLoss = losses > 0 ? (-grossLoss / losses).toFixed(2) : 0;
const avgConsWins = winStreaks > 0 ? Math.round(wins / winStreaks) : 0;
const avgConsLosses = lossStreaks > 0 ? Math.round(losses / lossStreaks) : 0;

const shortWinPct = shortTrades > 0 ? ((shortWins / shortTrades) * 100).toFixed(2) : 0;
const longWinPct = longTrades > 0 ? ((longWins / longTrades) * 100).toFixed(2) : 0;
const winPct = totalTrades > 0 ? ((wins / totalTrades) * 100).toFixed(2) : 0;
const lossPct = totalTrades > 0 ? ((losses / totalTrades) * 100).toFixed(2) : 0;

const fmt = (lbl, val) => lbl ? `${lbl.padStart(30)}: ${String(val).padEnd(20)}` : `                                ${String(val).padEnd(20)}`;

console.log('📈 [1] DETAILED PERFORMANCE OVERVIEW');
console.log(`${fmt('Total Net Profit', '$'+netProfit.toFixed(2))}${fmt('Gross Profit', '$'+grossProfit.toFixed(2))}${fmt('Gross Loss', '$-'+grossLoss.toFixed(2))}`);
console.log(`${fmt('Profit Factor', profitFactor)}${fmt('Expected Payoff', '$'+expectedPayoff)}`);
console.log(`${fmt('Recovery Factor', recoveryFactor)}`);
console.log();
console.log(`${fmt('Balance Drawdown Maximal', '$'+maxDrawdown.toFixed(2))}`);
console.log(`-----------------------------------------------------------------------------------------------------------------`);
console.log(`${fmt('Total Trades', totalTrades)}${fmt('Short Trades (won %)', `${shortTrades} (${shortWinPct}%)`)}${fmt('Long Trades (won %)', `${longTrades} (${longWinPct}%)`)}`);
console.log(`${fmt('', '')}${fmt('Profit Trades (% of total)', `${wins} (${winPct}%)`)}${fmt('Loss Trades (% of total)', `${losses} (${lossPct}%)`)}`);
console.log(`${fmt('', '')}${fmt('Largest profit trade', '$'+largestProfit.toFixed(2))}${fmt('Largest loss trade', '$'+largestLoss.toFixed(2))}`);
console.log(`${fmt('', '')}${fmt('Average profit trade', '$'+avgProfit)}${fmt('Average loss trade', '$'+avgLoss)}`);
console.log(`${fmt('', '')}${fmt('Maximum consecutive wins ($)', `${maxConsecutiveWins} ($${maxConsecutiveWinAmt.toFixed(2)})`)}${fmt('Max consecutive losses ($)', `${maxConsecutiveLosses} ($${maxConsecutiveLossAmt.toFixed(2)})`)}`);
console.log(`${fmt('', '')}${fmt('Average consecutive wins', avgConsWins)}${fmt('Average consecutive losses', avgConsLosses)}`);
console.log(`-----------------------------------------------------------------------------------------------------------------\n`);

console.log('🎯 [2] STRATEGY BREAKDOWN');
Object.keys(strategyStats).sort((a,b) => strategyStats[b].count - strategyStats[a].count).forEach(strat => {
  const s = strategyStats[strat];
  const wr = (s.wins + s.losses) > 0 ? ((s.wins / (s.wins + s.losses)) * 100).toFixed(1) : "0.0";
  console.log(`  - ${strat.padEnd(20)}: ${s.count} trades | WR: ${wr.padStart(5)}% | PnL: $${s.profit.toFixed(2)}`);
});
console.log();

// 2. ดึงข้อมูล Management Action
let mgmtQuery = `
  SELECT mode, status, COUNT(*) as count 
  FROM auto_trading_management_journal 
  WHERE status = 'EXECUTED'
`;
const mgmtParams = [];
if (targetDate) {
  const startTs = new Date(`${targetDate}T00:00:00Z`).getTime();
  const endTs = new Date(`${targetDate}T23:59:59Z`).getTime();
  mgmtQuery += ` AND created_at >= ? AND created_at <= ?`;
  mgmtParams.push(startTs, endTs);
}
mgmtQuery += ` GROUP BY mode, status ORDER BY count DESC`;

const mgmtActions = db.prepare(mgmtQuery).all(...mgmtParams);

console.log('🛡️ [3] TRADE MANAGEMENT ACTIONS');
if (mgmtActions.length > 0) {
  mgmtActions.forEach(m => {
    console.log(`  - ${m.mode.padEnd(15)} : ${m.count} times executed`);
  });
} else {
  console.log('  No management actions executed in this period.');
}
console.log();

// 3. วิเคราะห์อัตราการถูกปฏิเสธ (SKIP/BLOCK) จาก JSONL Log
if (targetDate) {
  const logPath = join(process.cwd(), 'data', 'trade_decision_logs', `${targetDate}.jsonl`);
  if (existsSync(logPath)) {
    console.log('🛑 [4] DECISION GATE ANALYSIS (SKIP REASONS)');
    const raw = readFileSync(logPath, 'utf-8');
    const lines = raw.trim().split('\n').filter(l => l.trim());
    
    let totalCycles = 0;
    let totalSkips = 0;
    const skipCategories = {};

    lines.forEach(line => {
      try {
        const j = JSON.parse(line);
        totalCycles++;
        if (j.side === 'SKIP' || j.decisionType === 'ORDER_BLOCKED') {
          totalSkips++;
          let reason = j.riskGate || 'Unknown';
          // Clean up reason text for aggregation
          if (reason.includes('MTF zone gate')) reason = 'MTF Zone Gate / Premium-Discount Block';
          else if (reason.includes('Zone-Aware Gate')) reason = 'Zone-Aware Guard (Wait for better edge)';
          else if (reason.includes('AI_UNAVAILABLE')) reason = 'AI Offline / EA-Only Fallback Guard';
          else if (reason.includes('[V25]')) reason = 'V25 Wall Engine Veto';
          else if (reason.includes('EA-ONLY')) reason = 'EA-Only Strict Policy';
          else if (reason.includes('recent opposite signal')) reason = 'Opposite Signal Cooldown';
          else reason = reason.substring(0, 60) + '...';

          skipCategories[reason] = (skipCategories[reason] || 0) + 1;
        }
      } catch (e) {}
    });

    console.log(`  Total Market Cycles : ${totalCycles}`);
    console.log(`  Trades Skipped      : ${totalSkips} (${totalCycles > 0 ? ((totalSkips/totalCycles)*100).toFixed(1) : 0}% rejection rate)`);
    console.log(`  Top Reject Reasons  :`);
    Object.keys(skipCategories).sort((a,b) => skipCategories[b] - skipCategories[a]).slice(0, 7).forEach(r => {
      const pct = ((skipCategories[r] / totalSkips) * 100).toFixed(1);
      console.log(`    - ${String(skipCategories[r]).padStart(4)}x (${pct.padStart(4)}%) : ${r}`);
    });
  } else {
    console.log(`🛑 [4] DECISION GATE ANALYSIS`);
    console.log(`  No JSONL log file found for ${targetDate} at ${logPath}`);
  }
} else {
  console.log('🛑 [4] DECISION GATE ANALYSIS');
  console.log('  Please specify a date (YYYY-MM-DD) to analyze cycle log skips.');
}

console.log('\n==================================================');
console.log('✨ Analysis Complete.');
