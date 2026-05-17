import sqlite from 'better-sqlite3';
import path from 'path';
const dbPath = path.resolve(process.cwd(), './data/mt5-core.db');
const db = new sqlite(dbPath);
const tokens = db.prepare('SELECT id, label, is_active, expires_at FROM api_tokens').all();
console.log(JSON.stringify(tokens, null, 2));
