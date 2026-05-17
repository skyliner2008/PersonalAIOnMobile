import Database from 'better-sqlite3';
import path from 'path';

const dbPath = path.resolve('data/mt5-core.db');
const db = new Database(dbPath);

const row = db.prepare("SELECT config_json FROM auto_trading_config WHERE id = 1").get();
if (row) {
    const config = JSON.parse(row.config_json);
    if (!config.adaptive) config.adaptive = {};
    config.adaptive.useMultiAgent = true;
    db.prepare("UPDATE auto_trading_config SET config_json = ? WHERE id = 1").run(JSON.stringify(config));
    console.log("Successfully enabled useMultiAgent in DB.");
} else {
    console.log("Config row not found.");
}
db.close();
