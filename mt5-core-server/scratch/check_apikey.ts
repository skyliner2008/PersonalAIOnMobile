import Database from 'better-sqlite3';
import path from 'path';

const dbPath = path.resolve('data/mt5-core.db');
const db = new Database(dbPath);

const row = db.prepare("SELECT api_key FROM auto_trading_config WHERE id = 1").get();
if (row) {
    console.log("Current DB API Key:", row.api_key ? (row.api_key.substring(0, 5) + "...") : "null");
} else {
    console.log("Config row not found.");
}
db.close();
