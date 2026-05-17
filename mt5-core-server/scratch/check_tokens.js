import sqlite from 'better-sqlite3';
const db = new sqlite('data/trading_v3.db');
const tokens = db.prepare('SELECT id, label, is_active, expires_at FROM api_tokens').all();
console.log(JSON.stringify(tokens, null, 2));
