import { initDb } from './src/db.js';

try {
    const db = initDb();
    console.log("Database initialized successfully.");
    
    const query = `SELECT config_json, ai_model, api_key, agent_prompt, notification_settings_json FROM auto_trading_config WHERE id = 1`;
    const stmt = db.prepare(query);
    console.log("Query 'SELECT' on auto_trading_config prepared successfully.");
    
    const row = stmt.get();
    console.log("Row fetched successfully:", row ? "Row exists" : "No row yet");
} catch (error) {
    console.error("Verification FAILED:", error);
    process.exit(1);
}
