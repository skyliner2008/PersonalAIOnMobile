import Database from 'better-sqlite3';
import path from 'path';

const dbPath = path.resolve(process.cwd(), './data/mt5-core.db');
const db = new Database(dbPath);

console.log(`Schema for auto_trading_runtime:`);
const info = db.prepare(`PRAGMA table_info(auto_trading_runtime)`).all();
console.log(JSON.stringify(info, null, 2));
