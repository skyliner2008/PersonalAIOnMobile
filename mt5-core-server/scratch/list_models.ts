import { GoogleGenerativeAI } from '@google/generative-ai';
import Database from 'better-sqlite3';
import path from 'path';

async function test() {
    const dbPath = path.resolve('data/mt5-core.db');
    const db = new Database(dbPath);
    const row = db.prepare("SELECT api_key FROM auto_trading_config WHERE id = 1").get();
    db.close();

    if (!row || !row.api_key) {
        console.log("No API key found.");
        return;
    }

    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${row.api_key}`);
        const data = await response.json();
        console.log("Available Models:", JSON.stringify(data, null, 2));
    } catch (e) {
        console.error("Fetch failed:", e);
    }
}

test();
