import Database from 'better-sqlite3';
import path from 'path';

// Based on config.ts: path.resolve(process.cwd(), process.env.DB_PATH || './data/mt5-core.db')
const dbPath = path.resolve(process.cwd(), './data/mt5-core.db');
console.log(`Using DB path: ${dbPath}`);
const db = new Database(dbPath);

const tables = ['auto_trading_config', 'auto_trading_journal'];

for (const table of tables) {
    console.log(`Schema for ${table}:`);
    const info = db.prepare(`PRAGMA table_info(${table})`).all();
    console.log(JSON.stringify(info, null, 2));
}
