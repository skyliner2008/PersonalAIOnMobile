import Database from 'better-sqlite3';
import path from 'path';

const dbPath = 'C:\\Users\\JOJO\\.gemini\\antigravity\\personal-ai-bot.db';
const db = new Database(dbPath);

const tables = ['auto_trading_config', 'auto_trading_journal'];

for (const table of tables) {
    console.log(`Schema for ${table}:`);
    const info = db.prepare(`PRAGMA table_info(${table})`).all();
    console.log(JSON.stringify(info, null, 2));
}
