import Database from 'better-sqlite3';
const db = new Database('data/mt5-core.db', { readonly: true });
const rows = db.prepare(`
    SELECT mt5_ticket, symbol, side, strategy, 
           datetime(created_at/1000, 'unixepoch', 'localtime') as time, 
           entry, sl, tp, rrr, profit, profit_r, close_reason, regime, signals_json 
    FROM auto_trading_journal 
    WHERE outcome='LOSS' AND symbol='XAUUSD' 
    ORDER BY created_at DESC LIMIT 5
`).all();

rows.forEach(r => { 
    console.log(`[${r.time}] Ticket: ${r.mt5_ticket} | ${r.side} ${r.symbol} | PnL: ${r.profit} (${r.profit_r}R) | Reason: ${r.close_reason}`); 
    console.log(`Entry: ${r.entry}, SL: ${r.sl}, TP: ${r.tp}, RRR: ${r.rrr}`); 
    console.log(`Regime: ${r.regime}`); 
    console.log(`Signals: ${r.signals_json}\n`); 
});
