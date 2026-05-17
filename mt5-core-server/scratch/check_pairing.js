import sqlite from 'better-sqlite3';
import path from 'path';
const dbPath = path.resolve(process.cwd(), './data/mt5-core.db');
const db = new sqlite(dbPath);
const requests = db.prepare('SELECT id, device_name, status, requested_at FROM pairing_requests').all();
console.log(JSON.stringify(requests, null, 2));
