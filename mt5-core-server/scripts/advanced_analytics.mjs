import Database from 'better-sqlite3';
import { existsSync } from 'fs';
import { join } from 'path';

// การใช้งาน: node scripts/advanced_analytics.mjs [YYYY-MM-DD]
// ถ้าไม่ระบุวันที่ จะวิเคราะห์ข้อมูลทั้งหมดในระบบ

const targetDate = process.argv[2] || null;
const dbPath = join(process.cwd(), 'data', 'mt5-core.db');
const REPORT_TIMEZONE = 'Asia/Bangkok';

function localDayRangeMs(dateKey) {
  // Trading logs are keyed by Asia/Bangkok local day, so date-specific
  // analytics must use the same operational day instead of UTC midnight.
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) {
    throw new Error(`Invalid date format: ${dateKey}. Expected YYYY-MM-DD`);
  }
  return {
    startTs: new Date(`${dateKey}T00:00:00.000+07:00`).getTime(),
    endTs: new Date(`${dateKey}T23:59:59.999+07:00`).getTime(),
  };
}

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
  const { startTs, endTs } = localDayRangeMs(targetDate);
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
  const { startTs, endTs } = localDayRangeMs(targetDate);
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

// 3. วิเคราะห์อัตราการถูกปฏิเสธ (SKIP/BLOCK) จาก DB (auto_trading_decision_feed)
//    ใช้ DB แทน JSONL → เร็วกว่า, แม่นยำกว่า, รองรับ All-Time
{
  console.log('🛑 [4] DECISION GATE ANALYSIS (DB-POWERED)');

  const CATEGORY_LABELS = {
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
    'MARKET_CLOSED': 'Market Closed / Off-Hours',
    'ENTRY_DRIFT': 'Entry Drift / Slippage Guard',
    'RSI_EXTREME': 'RSI Overbought/Oversold Gate',
    'SKIP_OTHER': 'Other Skip',
  };

  let totalQuery = `SELECT COUNT(*) AS cnt FROM auto_trading_decision_feed`;
  const normalizedCategoryExpr = `
    CASE
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%MARKET_CLOSED%' THEN 'MARKET_CLOSED'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%ENTRY_DRIFT%' OR UPPER(COALESCE(risk_gate, '')) LIKE '%SLIPPAGE%' THEN 'ENTRY_DRIFT'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%NO_DETERMINISTIC_SIGNAL%' THEN 'NO_DETERMINISTIC_SIGNAL'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%EA_FALLBACK_CONFIDENCE_LOW%' THEN 'EA_FALLBACK_CONFIDENCE_LOW'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%DECISION SIDE GUARD%' THEN 'DECISION_SIDE_GUARD'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%M15 SMC PRESSURE GUARD%' THEN 'M15_SMC_PRESSURE'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%ANTI-HEDGE%' OR UPPER(COALESCE(risk_gate, '')) LIKE '%ANTI HEDGE%' THEN 'ANTI_HEDGE'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%FITNESS%BELOW MIN%' THEN 'FITNESS_GATE'
      WHEN UPPER(COALESCE(risk_gate, '')) LIKE '%RSI%OVERBOUGHT%' OR UPPER(COALESCE(risk_gate, '')) LIKE '%RSI%OVERSOLD%' THEN 'RSI_EXTREME'
      ELSE COALESCE(block_category, 'UNKNOWN')
    END
  `;

  let skipQuery = `
    SELECT ${normalizedCategoryExpr} AS cat, COUNT(*) AS cnt
    FROM auto_trading_decision_feed
    WHERE (decision_type = 'ORDER_BLOCKED' OR side = 'SKIP')
  `;
  let skipTotalQuery = `
    SELECT COUNT(*) AS cnt FROM auto_trading_decision_feed
    WHERE (decision_type = 'ORDER_BLOCKED' OR side = 'SKIP')
  `;

  const gateParams = [];
  if (targetDate) {
    const { startTs, endTs } = localDayRangeMs(targetDate);
    const dateFilter = ` AND created_at >= ? AND created_at <= ?`;
    totalQuery += ` WHERE created_at >= ? AND created_at <= ?`;
    skipQuery += dateFilter;
    skipTotalQuery += dateFilter;
    gateParams.push(startTs, endTs);
  }
  skipQuery += ` GROUP BY cat ORDER BY cnt DESC`;

  const totalCycles = db.prepare(totalQuery).get(...gateParams)?.cnt ?? 0;
  const totalSkips = db.prepare(skipTotalQuery).get(...gateParams)?.cnt ?? 0;
  const skipRows = db.prepare(skipQuery).all(...gateParams);

  console.log(`  Total Market Cycles : ${totalCycles}`);
  console.log(`  Trades Skipped      : ${totalSkips} (${totalCycles > 0 ? ((totalSkips/totalCycles)*100).toFixed(1) : 0}% rejection rate)`);
  console.log(`  Top Reject Reasons  :`);
  skipRows.slice(0, 10).forEach(r => {
    const pct = totalSkips > 0 ? ((r.cnt / totalSkips) * 100).toFixed(1) : '0.0';
    const label = CATEGORY_LABELS[r.cat] || r.cat;
    console.log(`    - ${String(r.cnt).padStart(5)}x (${pct.padStart(5)}%) : ${label}`);
  });
}

console.log('\n==================================================');
console.log('✨ Analysis Complete.');
