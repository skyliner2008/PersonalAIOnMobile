import Database from 'better-sqlite3';
import path from 'path';

async function listModels() {
    const dbPath = path.resolve('data/mt5-core.db');
    const db = new Database(dbPath);
    const row = db.prepare("SELECT api_key FROM auto_trading_config WHERE id = 1").get();
    db.close();

    if (!row || !row.api_key) return;

    try {
        const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${row.api_key}`;
        const response = await fetch(url);
        const data = await response.json();
        if (data.models) {
            console.log("Names of available models:");
            data.models.forEach((m: any) => console.log(m.name));
        } else {
            console.log("No models found or error:", data);
        }
    } catch (e) {
        console.error("Fetch failed:", e);
    }
}

listModels();
