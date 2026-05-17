import { readFileSync } from 'fs';

const raw = readFileSync('c:/Users/JOJO/AndroidStudioProjects/PersonalAIBot/mt5-core-server/data/trade_decision_logs/2026-05-15.jsonl', 'utf-8');
const lines = raw.trim().split('\n').filter(l => l.trim());

let total = 0, skips = 0, buys = 0, sells = 0, executed = 0;
const skipCategories = { mtfZone: 0, zoneAware: 0, aiUnavailable: 0, v25: 0, eaOnly: 0, other: 0 };
const skipReasons = {};
const strategies = {};
const executedTrades = [];
let firstTs = null, lastTs = null;
let latestAccount = null;
let latestMtf = null;

for (const line of lines) {
  try {
    const j = JSON.parse(line);
    total++;
    
    if (!firstTs && j.ts) firstTs = j.ts;
    if (j.ts) lastTs = j.ts;
    
    // Parse market snapshot
    let ms = {};
    try { ms = JSON.parse(j.marketSnapshotJson || '{}'); } catch {}
    
    // Strategy
    if (ms.selectedStrategy) {
      strategies[ms.selectedStrategy] = (strategies[ms.selectedStrategy] || 0) + 1;
    }
    
    // Decision
    if (j.side === 'SKIP' || !j.side || j.aiDecision === 'SKIP') {
      skips++;
    } else if (j.side === 'BUY') buys++;
    else if (j.side === 'SELL') sells++;
    
    // Executed?
    let order = {};
    try { order = JSON.parse(j.orderJson || '{}'); } catch {}
    if (Object.keys(order).length > 0) {
      executed++;
      executedTrades.push({
        ts: j.ts,
        symbol: j.symbol,
        side: j.side,
        strategy: ms.selectedStrategy,
        price: ms.price,
        rrr: ms.actualRrr || ms.candidateRrr,
        riskDist: ms.candidateRiskDistance,
      });
    }
    
    // Gate trace
    let gt = [];
    try { gt = JSON.parse(j.gateTraceJson || '[]'); } catch {}
    const last = gt[gt.length - 1];
    if (last && last.status === 'SKIP') {
      const r = last.reason || '';
      const shortR = r.substring(0, 80);
      skipReasons[shortR] = (skipReasons[shortR] || 0) + 1;
      
      if (r.includes('MTF zone gate')) skipCategories.mtfZone++;
      else if (r.includes('Zone-Aware Gate')) skipCategories.zoneAware++;
      else if (r.includes('AI_UNAVAILABLE')) skipCategories.aiUnavailable++;
      else if (r.includes('[V25]')) skipCategories.v25++;
      else if (r.includes('EA-ONLY')) skipCategories.eaOnly++;
      else skipCategories.other++;
    }
    
    // Latest context
    try {
      const ctx = JSON.parse(j.contextJson || '{}');
      if (ctx.account) latestAccount = ctx.account;
      if (ctx.mtf) latestMtf = ctx.mtf;
    } catch {}
    
  } catch (e) {}
}

console.log('============================');
console.log('  TRADE DECISION LOG ANALYSIS');
console.log('  2026-05-15');
console.log('============================\n');

console.log(`Period: ${firstTs} → ${lastTs}`);
console.log(`Total cycles: ${total}`);
console.log(`SKIPs: ${skips} (${(skips/total*100).toFixed(1)}%)`);
console.log(`BUYs: ${buys}`);
console.log(`SELLs: ${sells}`);
console.log(`Executed orders: ${executed}`);

console.log('\n=== SKIP CATEGORIES ===');
console.log(`MTF Zone Gate veto:        ${skipCategories.mtfZone} (${(skipCategories.mtfZone/skips*100).toFixed(1)}%)`);
console.log(`Zone-Aware Gate:           ${skipCategories.zoneAware} (${(skipCategories.zoneAware/skips*100).toFixed(1)}%)`);
console.log(`AI Unavailable + RRR low:  ${skipCategories.aiUnavailable} (${(skipCategories.aiUnavailable/skips*100).toFixed(1)}%)`);
console.log(`V25 blocked:               ${skipCategories.v25} (${(skipCategories.v25/skips*100).toFixed(1)}%)`);
console.log(`EA-ONLY blocked:           ${skipCategories.eaOnly} (${(skipCategories.eaOnly/skips*100).toFixed(1)}%)`);
console.log(`Other:                     ${skipCategories.other} (${(skipCategories.other/skips*100).toFixed(1)}%)`);

console.log('\n=== STRATEGIES ===');
Object.entries(strategies).sort((a,b) => b[1]-a[1]).forEach(([k,v]) => console.log(`  ${k}: ${v}`));

console.log('\n=== TOP 15 SKIP REASONS ===');
Object.entries(skipReasons).sort((a,b) => b[1]-a[1]).slice(0,15).forEach(([k,v]) => console.log(`  ${v}x ${k}`));

console.log('\n=== EXECUTED TRADES ===');
executedTrades.forEach(t => console.log(`  ${t.ts || 'N/A'} | ${t.symbol} ${t.side} @ ${t.price} | ${t.strategy} | RRR=${t.rrr} | Risk=${t.riskDist}`));

if (latestAccount) {
  console.log('\n=== LATEST ACCOUNT STATUS ===');
  console.log(`Balance: $${latestAccount.balance?.toLocaleString()}`);
  console.log(`Equity: $${latestAccount.equity?.toLocaleString()}`);
  console.log(`Free Margin: $${latestAccount.freeMargin?.toLocaleString()}`);
  console.log(`Open Positions: ${latestAccount.openPositions}`);
}

if (latestMtf) {
  console.log('\n=== LATEST MTF BIASES ===');
  for (const tf of ['H4','H1','M30','M15','M5']) {
    const d = latestMtf[tf];
    if (d) console.log(`  ${tf}: bias=${d.bias} regime=${d.regime} confluence=${d.confluence} fitness=${d.fitness} rsi=${d.rsi} strategy=${d.strategy}`);
  }
}

// Time distribution
const hourBuckets = {};
const executedHours = {};
for (const line of lines) {
  try {
    const j = JSON.parse(line);
    let gt = [];
    try { gt = JSON.parse(j.gateTraceJson || '[]'); } catch {}
    const at = gt[0]?.at;
    if (at) {
      const h = at.substring(11,13);
      hourBuckets[h] = (hourBuckets[h] || 0) + 1;
    }
    let order = {};
    try { order = JSON.parse(j.orderJson || '{}'); } catch {}
    if (Object.keys(order).length > 0 && gt[0]?.at) {
      const h = gt[0].at.substring(11,13);
      executedHours[h] = (executedHours[h] || 0) + 1;
    }
  } catch {}
}

console.log('\n=== HOURLY DISTRIBUTION (UTC) ===');
Object.keys(hourBuckets).sort().forEach(h => {
  const ex = executedHours[h] || 0;
  const bar = '█'.repeat(Math.ceil(hourBuckets[h]/20));
  console.log(`  ${h}:00 — ${String(hourBuckets[h]).padStart(3)} cycles | ${ex} executed ${bar}`);
});
