import Database from 'better-sqlite3';
import path from 'path';

const dbPath = path.resolve('data/mt5-core.db');
const db = new Database(dbPath);

const row = db.prepare("SELECT config_json FROM auto_trading_config WHERE id = 1").get();
if (row) {
    const config = JSON.parse(row.config_json);
    console.log("Current DB Config adaptive:", config.adaptive);
} else {
    console.log("Config row not found.");
}
db.close();
