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

    const genAI = new GoogleGenerativeAI(row.api_key);
    const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

    try {
        console.log("Testing Gemini API with model gemini-1.5-flash...");
        const result = await model.generateContent("Hello, world!");
        console.log("Response:", result.response.text());
    } catch (e) {
        console.error("Test failed:", e);
    }
}

test();
