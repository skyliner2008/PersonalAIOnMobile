import Database from 'better-sqlite3';
const db = new Database('data/mt5-core.db', { readonly: true });
const rows = db.prepare(`SELECT mt5_ticket, profit FROM auto_trading_journal WHERE outcome IN ('WIN', 'LOSS', 'BE')`).all();
let sum = 0;
rows.forEach(r => {
    sum += (r.profit || 0);
    console.log(`Ticket ${r.mt5_ticket}: ${r.profit}`);
});
console.log(`DB Total: ${sum.toFixed(2)}`);
