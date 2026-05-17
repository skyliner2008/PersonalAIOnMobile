import Database from 'better-sqlite3';
import path from 'path';

async function test() {
    const dbPath = path.resolve('data/mt5-core.db');
    const db = new Database(dbPath);
    const row = db.prepare("SELECT api_key FROM auto_trading_config WHERE id = 1").get();
    db.close();

    if (!row || !row.api_key) return;

    try {
        const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent?key=${row.api_key}`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: "Hello" }] }]
            })
        });
        const data = await response.json();
        console.log("Raw API Response (Gemini 2.5 Pro):", JSON.stringify(data, null, 2));
    } catch (e) {
        console.error("Fetch failed:", e);
    }
}

test();
