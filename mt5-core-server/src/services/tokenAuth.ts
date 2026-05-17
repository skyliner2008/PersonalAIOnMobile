import crypto from 'crypto';
import type { Request, Response, NextFunction } from 'express';
import { config } from '../config.js';
import { getDb } from '../db.js';

type TokenRow = {
  id: number;
  label: string | null;
  token_hash: string;
  token_prefix: string;
  is_active: number;
  expires_at: string | null;
};

import { atWarn } from './auto/utils.js';

const db = getDb();

function hashToken(token: string): string {
  return crypto.createHash('sha256').update(`${config.tokenPepper}:${token}`).digest('hex');
}

function tokenPrefix(token: string): string {
  return token.slice(0, 8);
}

function extractClientToken(req: Request): string | null {
  const auth = String(req.headers.authorization || '');
  if (auth.toLowerCase().startsWith('bearer ')) {
    const v = auth.slice(7).trim();
    if (v) return v;
  }
  const x = String(req.headers['x-client-token'] || '').trim();
  if (x) return x;
  const queryToken = typeof req.query.token === 'string' ? req.query.token.trim() : '';
  if (queryToken) return queryToken;
  return null;
}

function extractAdminToken(req: Request): string {
  const x = String(req.headers['x-admin-token'] || '').trim();
  if (x) return x;
  const auth = String(req.headers.authorization || '');
  if (auth.toLowerCase().startsWith('bearer ')) return auth.slice(7).trim();
  return '';
}

function isExpired(expiresAt: string | null): boolean {
  if (!expiresAt) return false;
  const t = Date.parse(expiresAt);
  if (!Number.isFinite(t)) return false;
  return t <= Date.now();
}

function findActiveTokenByRawToken(rawToken: string): TokenRow | null {
  const token = String(rawToken || '').trim();
  if (!token) return null;
  const h = hashToken(token);
  const row = db
    .prepare(`SELECT id, label, token_hash, token_prefix, is_active, expires_at FROM api_tokens WHERE token_hash = ? LIMIT 1`)
    .get(h) as TokenRow | undefined;
  if (!row || row.is_active !== 1 || isExpired(row.expires_at)) return null;
  return row;
}

export function resolveClientToken(rawToken: string): { id: number; label: string | null; prefix: string } | null {
  const row = findActiveTokenByRawToken(rawToken);
  if (!row) return null;
  return {
    id: row.id,
    label: row.label,
    prefix: row.token_prefix,
  };
}

export function createClientToken(label?: string, expiresAt?: string | null): { id: number; token: string } {
  const raw = crypto.randomBytes(24).toString('hex');
  const h = hashToken(raw);
  const info = db
    .prepare(
      `INSERT INTO api_tokens (label, token_hash, token_prefix, is_active, expires_at, created_at)
       VALUES (?, ?, ?, 1, ?, datetime('now'))`
    )
    .run(label || null, h, tokenPrefix(raw), expiresAt || null);
  return { id: Number(info.lastInsertRowid), token: raw };
}

export function requireAdminToken(req: Request, res: Response, next: NextFunction): void {
  const got = extractAdminToken(req);
  if (!got || got !== config.adminToken) {
    res.status(401).json({ success: false, error: 'Admin token required' });
    return;
  }
  next();
}

export function requireClientToken(req: Request, res: Response, next: NextFunction): void {
  const got = extractClientToken(req);
  if (!got) {
    res.status(401).json({ success: false, error: 'Client token required' });
    return;
  }
  const row = findActiveTokenByRawToken(got);
  if (!row) {
    atWarn(`[auth] 403 Forbidden: Token invalid, inactive, or expired. Prefix: ${got.slice(0, 8)}... | Path: ${req.path}`);
    res.status(403).json({ success: false, error: 'Token invalid or expired' });
    return;
  }

  db.prepare(`UPDATE api_tokens SET last_used_at = datetime('now') WHERE id = ?`).run(row.id);
  db.prepare(
    `INSERT INTO api_token_usage (token_id, path, method, ip, user_agent, created_at)
     VALUES (?, ?, ?, ?, ?, datetime('now'))`
  ).run(
    row.id,
    req.path,
    req.method,
    req.ip || '',
    String(req.headers['user-agent'] || '').slice(0, 300)
  );

  (req as any).clientToken = {
    id: row.id,
    label: row.label,
    prefix: row.token_prefix,
  };
  next();
}

export function listTokens() {
  return db
    .prepare(
      `SELECT id, label, token_prefix, is_active, last_used_at, created_at, expires_at
       FROM api_tokens
       ORDER BY id DESC`
    )
    .all();
}

export function revokeToken(id: number): void {
  db.prepare(`DELETE FROM api_tokens WHERE id = ?`).run(id);
}

export function setTokenActive(id: number, active: boolean): void {
  db.prepare(`UPDATE api_tokens SET is_active = ? WHERE id = ?`).run(active ? 1 : 0, id);
}

export function getTokenUsage(limit: number) {
  return db
    .prepare(
      `SELECT u.id, u.token_id, t.label, t.token_prefix, u.path, u.method, u.ip, u.user_agent, u.created_at
       FROM api_token_usage u
       JOIN api_tokens t ON t.id = u.token_id
       ORDER BY u.id DESC
       LIMIT ?`
    )
    .all(limit);
}
