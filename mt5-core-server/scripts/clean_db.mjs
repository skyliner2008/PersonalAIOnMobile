import Database from 'better-sqlite3';

const db = new Database('data/mt5-core.db');
const cutoffDate = new Date('2026-05-15T00:00:00Z').getTime();

const tables = db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(t => t.name);
console.log("Tables found:", tables);

tables.forEach(table => {
  if (table === 'sqlite_sequence') return;
  try {
    // Check if the table has created_at column
    const columns = db.prepare(`PRAGMA table_info(${table})`).all().map(c => c.name);
    
    if (columns.includes('created_at')) {
      const stmt = db.prepare(`DELETE FROM ${table} WHERE created_at < ?`);
      const info = stmt.run(cutoffDate);
      console.log(`Deleted ${info.changes} old records from ${table}`);
    } else if (columns.includes('timestamp')) {
      const stmt = db.prepare(`DELETE FROM ${table} WHERE timestamp < ?`);
      const info = stmt.run(cutoffDate);
      console.log(`Deleted ${info.changes} old records from ${table}`);
    } else {
      console.log(`Skipping ${table} (no created_at/timestamp column)`);
    }
  } catch (err) {
    console.error(`Error cleaning ${table}:`, err.message);
  }
});

// Also truncate trade_decision_logs if they exist
console.log("Cleanup complete!");
