import path from 'path';
import fs from 'fs';
import crypto from 'crypto';
import dotenv from 'dotenv';
import Database from 'better-sqlite3';

dotenv.config();

const dbPath = path.resolve(process.cwd(), process.env.DB_PATH || './data/mt5-core.db');

// สร้างโฟลเดอร์สำหรับ DB หากยังไม่มี
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

// เปิดการเชื่อมต่อ DB ชั่วคราวเพื่ออ่าน/เขียน Config
const db = new Database(dbPath);

function getOrGenerateSetting(key: string, length = 32): string {
  try {
    db.exec(`CREATE TABLE IF NOT EXISTS system_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)`);
    const row = db.prepare(`SELECT value FROM system_settings WHERE key = ?`).get(key) as { value: string } | undefined;
    if (row) return row.value;
    
    // สร้างค่าสุ่มใหม่
    const secret = crypto.randomBytes(Math.max(16, length / 2)).toString('hex');
    db.prepare(`INSERT INTO system_settings (key, value) VALUES (?, ?)`).run(key, secret);
    console.log(`[config] Auto-generated and saved setting '${key}' to database.`);
    return secret;
  } catch (err) {
    console.error(`[config] Failed to get/generate setting ${key}:`, err);
    return crypto.randomBytes(Math.max(16, length / 2)).toString('hex'); // Fallback in memory
  }
}

const bridgeStartCmd = String(process.env.BRIDGE_START_CMD || '').trim();
const bridgeAutoStartRaw = String(process.env.BRIDGE_AUTO_START ?? '1').trim().toLowerCase();
const bridgeAutoStart = bridgeAutoStartRaw !== '0' && bridgeAutoStartRaw !== 'false' && bridgeAutoStartRaw !== 'no';

const corsOriginRaw = String(process.env.CORS_ORIGIN || '').trim();
const corsOrigins = corsOriginRaw
  ? corsOriginRaw.split(',').map((s) => s.trim()).filter(Boolean)
  : [];

export const config = {
  port: Number(process.env.PORT || 8090),
  mt5BridgeUrl: String(process.env.MT5_BRIDGE_URL || 'http://127.0.0.1:5001').replace(/\/+$/, ''),
  mt5BridgeToken: String(process.env.MT5_BRIDGE_TOKEN || ''),
  bridgeStartCmd,
  bridgeAutoStart,
  dbPath,
  // ใช้ค่าจาก DB แทน .env
  adminToken: getOrGenerateSetting('ADMIN_TOKEN', 24),
  tokenPepper: getOrGenerateSetting('TOKEN_PEPPER', 24),
  dbEncryptionKey: getOrGenerateSetting('DB_ENCRYPTION_KEY', 32),
  corsOrigins,
  env: process.env.NODE_ENV || 'development',
};

// ปิดการเชื่อมต่อ DB ชั่วคราว
db.close();


