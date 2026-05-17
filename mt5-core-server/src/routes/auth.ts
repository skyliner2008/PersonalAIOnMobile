import { Router } from 'express';
import crypto from 'crypto';
import { z } from 'zod';
import { config } from '../config.js';
import { getDb } from '../db.js';
import {
  createClientToken,
  getTokenUsage,
  listTokens,
  requireAdminToken,
  requireClientToken,
  revokeToken,
  setTokenActive,
} from '../services/tokenAuth.js';
import { autoTradingService } from '../services/autoTradingService.js';
import { unwrapData } from '../services/auto/utils.js';
import { callBridge } from '../services/bridgeClient.js';

const router = Router();
const db = getDb();

function sideTh(side: string): string {
  const s = String(side || '').toUpperCase();
  if (s === 'BUY') return 'ซื้อ';
  if (s === 'SELL') return 'ขาย';
  if (s === 'SKIP') return 'ข้าม';
  return s || '-';
}

function strategyTh(strategy: string): string {
  const m: Record<string, string> = {
    SCALPING: 'สเกลป์',
    SWING: 'สวิง',
    GRID: 'กริด',
    TRAILING: 'เทรลกำไร',
    TREND_FOLLOW: 'ตามเทรนด์',
    MEAN_REVERSION: 'กลับสู่ค่าเฉลี่ย',
    BREAKOUT: 'เบรกเอาท์',
    RANGE: 'เทรดกรอบ',
    HOLD_CASH: 'ถือเงินสด',
    BACKFILL: 'ซิงก์ย้อนหลัง',
  };
  const key = String(strategy || '').toUpperCase();
  return m[key] ?? (strategy || '-');
}

function regimeTh(regime: string): string {
  const m: Record<string, string> = {
    TRENDING_UP: 'ขาขึ้น',
    TRENDING_DOWN: 'ขาลง',
    RANGING: 'แกว่งในกรอบ',
    VOLATILE_BREAKOUT: 'ผันผวนสูง/เบรกเอาท์',
    QUIET: 'ตลาดเงียบ',
    UNKNOWN: 'ไม่ชัดเจน',
  };
  const key = String(regime || '').toUpperCase();
  return m[key] ?? (regime || '-');
}

function outcomeTh(outcome: string): string {
  const m: Record<string, string> = {
    OPEN: 'เปิดอยู่',
    WIN: 'กำไร',
    LOSS: 'ขาดทุน',
    BE: 'เสมอทุน',
    CANCELLED: 'ยกเลิก',
    PAPER: 'ทดสอบ',
  };
  const key = String(outcome || '').toUpperCase();
  return m[key] ?? (outcome || '-');
}

function toThaiText(input: string): string {
  let t = String(input || '');
  const rules: Array<[RegExp, string]> = [
    [/\bMulti[- ]?Timeframe analysis\b/gi, 'การวิเคราะห์หลายช่วงเวลา'],
    [/\bstrong bearish bias\b/gi, 'แนวโน้มขาลงที่แข็งแกร่ง'],
    [/\bstrong bullish bias\b/gi, 'แนวโน้มขาขึ้นที่แข็งแกร่ง'],
    [/\bconfirms?\b/gi, 'ยืนยันว่า'],
    [/\bPrice is consistently below key moving averages\b/gi, 'ราคาอยู่ต่ำกว่าค่าเฉลี่ยเคลื่อนที่สำคัญอย่างต่อเนื่อง'],
    [/\bPrice is consistently above key moving averages\b/gi, 'ราคาอยู่สูงกว่าค่าเฉลี่ยเคลื่อนที่สำคัญอย่างต่อเนื่อง'],
    [/\bRSI is bearish\b/gi, 'RSI อยู่ในภาวะขาลง'],
    [/\bRSI is bullish\b/gi, 'RSI อยู่ในภาวะขาขึ้น'],
    [/\bindicating sustained downward momentum\b/gi, 'บ่งชี้ถึงโมเมนตัมขาลงที่ต่อเนื่อง'],
    [/\bindicating sustained upward momentum\b/gi, 'บ่งชี้ถึงโมเมนตัมขาขึ้นที่ต่อเนื่อง'],
    [/\bprimary directive is to protect capital first\b/gi, 'หลักการสำคัญคือปกป้องเงินทุนก่อน'],
    [/\bwithout introducing new risk\b/gi, 'โดยไม่เพิ่มความเสี่ยงใหม่'],
    [/\bletting winners run\b/gi, 'ปล่อยให้ไม้กำไรวิ่งต่อ'],
    [/\bVOLATILE_BREAKOUT\b/g, 'ผันผวนสูง/เบรกเอาท์'],
    [/\bTRENDING_DOWN\b/g, 'ขาลงชัดเจน'],
    [/\bTRENDING_UP\b/g, 'ขาขึ้นชัดเจน'],
    [/\bRANGING\b/g, 'แกว่งในกรอบ'],
    [/\bConfluence\b/g, 'การบรรจบกัน'],
    [/\bFitness\b/g, 'ความเหมาะสม'],
    [/\bRationale\b/g, 'เหตุผล'],
    [/\bGate\b/g, 'เงื่อนไข'],
    [/\bmax open positions reached\b/gi, 'ถึงจำนวนตำแหน่งเปิดสูงสุดแล้ว'],
  ];
  for (const [pattern, replacement] of rules) t = t.replace(pattern, replacement);
  return t;
}

function extractConfidence(raw: string): number | null {
  const m = String(raw || '').match(/confidence\s*[:=]?\s*(\d{1,3})/i);
  if (!m) return null;
  const v = Number(m[1]);
  return Number.isFinite(v) ? v : null;
}

function buildLatestThaiLog(d: any): string {
  if (d.translatedTh) return d.translatedTh;

  const symbol = String(d.symbol || '-');
  const sideEn = String(d.side || 'SKIP').toUpperCase();
  const side = sideTh(sideEn);
  const conf = extractConfidence(String(d.rationale || ''));
  const confText = conf === null ? '-' : String(conf);
  const rationale = toThaiText(String(d.rationale || '-'));
  
  // Compact fallback if no pre-translated Th
  return `🎯 AI แนะนำ: ${side} (มั่นใจ: ${confText}%)\n💬 เหตุผล: ${rationale}\n📊 กลยุทธ์: ${strategyTh(String(d.strategy || ''))}`;
}

function buildHistoryThaiLog(row: any): string {
  if (row.translatedTh) return row.translatedTh;

  const sideEn = String(row.side || 'SKIP').toUpperCase();
  const symbol = String(row.symbol || '-');
  const confluence = Number(row.confluenceScore ?? row.confluence ?? 0);
  const aiOrClose = String(row.aiReview || row.closeReason || '-');
  
  return `🕒 ประวัติ: ${sideTh(sideEn)} ${symbol} | สภาวะ: ${regimeTh(String(row.regime || ''))}\n` +
         `📝 เหตุผล: ${toThaiText(aiOrClose)}\n` +
         `📊 ผลลัพธ์: ${outcomeTh(String(row.outcome || 'OPEN'))} | Conf: ${confluence.toFixed(1)}`;
}

function hashToken(token: string): string {
  return crypto.createHash('sha256').update(`${config.tokenPepper}:${token}`).digest('hex');
}

function tokenPrefix(token: string): string {
  return token.slice(0, 8);
}

const createTokenSchema = z.object({
  label: z.string().max(120).optional(),
  expiresAt: z.string().datetime().optional(),
});

const toggleSchema = z.object({
  active: z.boolean(),
});

const pairRequestSchema = z.object({
  token: z.string().min(16).max(512),
  deviceId: z.string().max(160).optional(),
  deviceName: z.string().max(160).optional(),
  appVersion: z.string().max(64).optional(),
});

router.post('/login', (req, res) => {
  const { id, password } = req.body || {};
  if (!id || !password) {
    return res.status(400).json({ success: false, error: 'ID and Password required' });
  }
  
  const hash = crypto.createHash('sha256').update(password).digest('hex');
  const user = db.prepare(`SELECT id, username, role FROM users WHERE username = ? AND password_hash = ?`).get(id, hash) as { id: number, username: string, role: string } | undefined;
  
  if (user) {
    const created = createClientToken(`session-${user.username}`, null);
    return res.json({ success: true, token: created.token });
  }
  
  res.status(401).json({ success: false, error: 'Invalid ID or Password' });
});

router.get('/verify', requireClientToken, (req, res) => {
  const token = (req as any).clientToken;
  res.json({
    success: true,
    token: {
      id: token?.id,
      label: token?.label || null,
      prefix: token?.prefix || null,
    },
    time: new Date().toISOString(),
  });
});

router.post('/pair/request', (req, res) => {
  try {
    const payload = pairRequestSchema.parse(req.body || {});
    const rawToken = payload.token.trim();
    const tokenHash = hashToken(rawToken);
    const prefix = tokenPrefix(rawToken);
    const deviceId = payload.deviceId?.trim() || null;
    const deviceName = payload.deviceName?.trim() || null;
    const appVersion = payload.appVersion?.trim() || null;

    const existingToken = db
      .prepare(`SELECT id, is_active, expires_at FROM api_tokens WHERE token_hash = ? LIMIT 1`)
      .get(tokenHash) as { id: number; is_active: number; expires_at: string | null } | undefined;

    if (existingToken && existingToken.is_active === 1) {
      return res.json({
        success: true,
        status: 'APPROVED',
        approved: true,
        tokenPrefix: prefix,
        note: 'Token already approved',
      });
    }

    db.prepare(
      `INSERT INTO pairing_requests (
         device_id, device_name, app_version, token_hash, token_prefix, status, request_count, requested_at, last_seen_at
       )
       VALUES (?, ?, ?, ?, ?, 'PENDING', 1, datetime('now'), datetime('now'))
       ON CONFLICT(token_hash) DO UPDATE SET
         device_id = excluded.device_id,
         device_name = excluded.device_name,
         app_version = excluded.app_version,
         status = CASE WHEN pairing_requests.status = 'APPROVED' THEN 'APPROVED' ELSE 'PENDING' END,
         request_count = pairing_requests.request_count + 1,
         last_seen_at = datetime('now')`
    ).run(deviceId, deviceName, appVersion, tokenHash, prefix);

    const row = db
      .prepare(
        `SELECT id, status, request_count, requested_at, last_seen_at
         FROM pairing_requests
         WHERE token_hash = ?
         LIMIT 1`
      )
      .get(tokenHash) as any;

    // eslint-disable-next-line no-console
    console.log(
      `[pair-request] prefix=${prefix} device=${deviceName || 'unknown'} id=${deviceId || '-'} status=${row?.status || 'PENDING'}`
    );

    res.json({
      success: true,
      status: row?.status || 'PENDING',
      approved: row?.status === 'APPROVED',
      tokenPrefix: prefix,
      requestId: row?.id || null,
      requestCount: row?.request_count || 1,
      requestedAt: row?.requested_at || null,
      lastSeenAt: row?.last_seen_at || null,
    });
  } catch (err: any) {
    res.status(400).json({ success: false, error: String(err?.message || err) });
  }
});

router.get('/pair/status', (req, res) => {
  const token = String(req.query.token || '').trim();
  if (!token) {
    return res.status(400).json({ success: false, error: 'token is required' });
  }
  const tokenHash = hashToken(token);
  const pair = db
    .prepare(`SELECT id, status, approved_at, last_seen_at FROM pairing_requests WHERE token_hash = ? LIMIT 1`)
    .get(tokenHash) as any;
  const activeToken = db
    .prepare(`SELECT id, is_active FROM api_tokens WHERE token_hash = ? LIMIT 1`)
    .get(tokenHash) as any;

  const approved = !!activeToken && Number(activeToken.is_active) === 1;
  res.json({
    success: true,
    status: approved ? 'APPROVED' : pair?.status || 'UNKNOWN',
    approved,
    requestId: pair?.id || null,
    approvedAt: pair?.approved_at || null,
    lastSeenAt: pair?.last_seen_at || null,
  });
});

router.get('/pair/requests', requireAdminToken, (req, res) => {
  const status = String(req.query.status || 'PENDING').toUpperCase();
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 500);
  const rows =
    status === 'ALL'
      ? db
          .prepare(
            `SELECT id, device_id, device_name, app_version, token_prefix, status, request_count, requested_at, last_seen_at, approved_at, approved_by
             FROM pairing_requests
             ORDER BY id DESC
             LIMIT ?`
          )
          .all(limit)
      : db
          .prepare(
            `SELECT id, device_id, device_name, app_version, token_prefix, status, request_count, requested_at, last_seen_at, approved_at, approved_by
             FROM pairing_requests
             WHERE status = ?
             ORDER BY id DESC
             LIMIT ?`
          )
          .all(status, limit);
  res.json({ success: true, rows, count: rows.length });
});

router.get('/pair/requests/public', (req, res) => {
  const status = String(req.query.status || 'PENDING').toUpperCase();
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 500);
  const rows =
    status === 'ALL'
      ? db
          .prepare(
            `SELECT id, device_id, device_name, app_version, token_prefix, status, request_count, requested_at, last_seen_at
             FROM pairing_requests
             ORDER BY id DESC
             LIMIT ?`
          )
          .all(limit)
      : db
          .prepare(
            `SELECT id, device_id, device_name, app_version, token_prefix, status, request_count, requested_at, last_seen_at
             FROM pairing_requests
             WHERE status = ?
             ORDER BY id DESC
             LIMIT ?`
          )
          .all(status, limit);
  res.json({ success: true, rows, count: rows.length });
});

router.post('/pair/requests/:id/approve', requireAdminToken, (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ success: false, error: 'Invalid id' });
  }
  const row = db
    .prepare(
      `SELECT id, device_id, device_name, app_version, token_hash, token_prefix, status
       FROM pairing_requests
       WHERE id = ?
       LIMIT 1`
    )
    .get(id) as any;
  if (!row) {
    return res.status(404).json({ success: false, error: 'Pair request not found' });
  }

  const existingToken = db
    .prepare(`SELECT id FROM api_tokens WHERE token_hash = ? LIMIT 1`)
    .get(row.token_hash) as any;

  if (!existingToken) {
    const label = [row.device_name || 'mobile', row.device_id || '', row.app_version || '']
      .map((x: string) => String(x || '').trim())
      .filter(Boolean)
      .join(' | ')
      .slice(0, 120);
    db.prepare(
      `INSERT INTO api_tokens (label, token_hash, token_prefix, is_active, created_at)
       VALUES (?, ?, ?, 1, datetime('now'))`
    ).run(label || 'paired-device', row.token_hash, row.token_prefix);
  } else {
    db.prepare(`UPDATE api_tokens SET is_active = 1 WHERE id = ?`).run(existingToken.id);
  }

  db.prepare(
    `UPDATE pairing_requests
     SET status = 'APPROVED',
         approved_at = datetime('now'),
         approved_by = 'admin',
         last_seen_at = datetime('now')
     WHERE id = ?`
  ).run(id);

  // eslint-disable-next-line no-console
  console.log(`[pair-approve] requestId=${id} prefix=${row.token_prefix} device=${row.device_name || 'unknown'}`);

  res.json({ success: true, requestId: id, tokenPrefix: row.token_prefix, status: 'APPROVED' });
});

router.post('/pair/requests/:id/reject', requireAdminToken, (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ success: false, error: 'Invalid id' });
  }
  const row = db
    .prepare(`SELECT id, token_prefix FROM pairing_requests WHERE id = ? LIMIT 1`)
    .get(id) as any;
  if (!row) {
    return res.status(404).json({ success: false, error: 'Pair request not found' });
  }
  db.prepare(
    `UPDATE pairing_requests
     SET status = 'REJECTED',
         approved_by = 'admin',
         last_seen_at = datetime('now')
     WHERE id = ?`
  ).run(id);
  res.json({ success: true, requestId: id, tokenPrefix: row.token_prefix, status: 'REJECTED' });
});

router.get('/tokens', requireAdminToken, (_req, res) => {
  res.json({ success: true, rows: listTokens() });
});

router.post('/tokens', requireAdminToken, (req, res) => {
  try {
    const payload = createTokenSchema.parse(req.body || {});
    const created = createClientToken(payload.label, payload.expiresAt || null);
    res.json({
      success: true,
      token: created.token,
      id: created.id,
      note: 'Show once. Store securely.',
    });
  } catch (err: any) {
    res.status(400).json({ success: false, error: String(err?.message || err) });
  }
});

router.patch('/tokens/:id', requireAdminToken, (req, res) => {
  try {
    const payload = toggleSchema.parse(req.body || {});
    const id = Number(req.params.id);
    if (!Number.isFinite(id)) {
      return res.status(400).json({ success: false, error: 'Invalid id' });
    }
    setTokenActive(id, payload.active);
    res.json({ success: true });
  } catch (err: any) {
    res.status(400).json({ success: false, error: String(err?.message || err) });
  }
});

router.delete('/tokens/:id', requireAdminToken, (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ success: false, error: 'Invalid id' });
  }
  revokeToken(id);
  res.json({ success: true });
});

router.get('/usage', requireAdminToken, (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit || 100), 1), 1000);
  const rows = getTokenUsage(limit);
  res.json({ success: true, rows, count: rows.length });
});

router.get('/admin/auto-snapshot', requireAdminToken, (_req, res) => {
  try {
    res.json({ success: true, data: autoTradingService.snapshot() });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/account', requireAdminToken, async (_req, res) => {
  try {
    const result = await callBridge('GET', ['/account', '/mt5/account', '/mt5/accountInfo']);
    res.json({ success: true, data: unwrapData(result.data) });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/positions', requireAdminToken, async (req, res) => {
  try {
    const symbol = typeof req.query.symbol === 'string' ? req.query.symbol : undefined;
    const result = await callBridge('GET', ['/positions', '/mt5/positions', '/api/mt5/positions'], {
      query: { symbol },
    });
    res.json({ success: true, data: unwrapData(result.data) });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/auto-analytics', requireAdminToken, async (_req, res) => {
  try {
    const data = await autoTradingService.getPerformanceAnalytics();
    res.json({ success: true, data });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/auto-management-journal', requireAdminToken, async (req, res) => {
  try {
    const limit = Math.min(Math.max(Number(req.query.limit || 40), 1), 200);
    const data = await autoTradingService.getManagementJournal(limit);
    res.json({ success: true, data });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/auto-journal', requireAdminToken, async (req, res) => {
  try {
    const limit = Math.min(Math.max(Number(req.query.limit || 120), 1), 400);
    const data = await autoTradingService.getDecisionJournal(limit);
    res.json({ success: true, data });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

router.get('/admin/decision-feed', requireAdminToken, async (req, res) => {
  try {
    const limit = Math.min(Math.max(Number(req.query.limit || 120), 20), 500);
    const includeBackfill = req.query.includeBackfill === 'true' || req.query.includeBackfill === '1';
    const snapshot = autoTradingService.snapshot();
    const latest = (snapshot.lastDecisions || []).map((d) => ({
      kind: 'latest',
      symbol: d.symbol,
      side: d.side,
      strategy: d.strategy,
      regime: d.regime,
      confluence: d.confluence,
      fitness: (d as any).fitness ?? 0,
      entry: d.entry,
      sl: d.sl,
      tp: d.tp,
      volume: d.volume,
      rrr: d.rrr,
      riskGate: d.riskGate,
      rationale: d.rationale,
      decisionId: d.decisionId,
      at: d.at,
      translatedTh: buildLatestThaiLog(d),
    }));

    const rows = await autoTradingService.getDecisionFeed(limit * 3);
    const latestDecisionIds = new Set(latest.map((d) => String(d.decisionId)));
    const filtered = rows.filter((r) => {
      if (latestDecisionIds.has(String(r.decisionId))) return false;
      return includeBackfill || String(r.strategy || '').toUpperCase() !== 'BACKFILL';
    });
    const seen = new Set<string>();
    const history = filtered
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
      .filter((r) => {
        const key = String(r.decisionId || `${r.symbol}-${r.createdAt}-${r.side}`);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
        })
        .slice(0, limit)
        .map((r) => ({
          ...r,
          translatedTh: buildHistoryThaiLog(r),
      }));

    res.json({
      success: true,
      data: {
        latest,
        history,
      },
    });
  } catch (err: any) {
    const message = String(err?.message || err || 'unknown');
    res.status(500).json({ success: false, error: message });
  }
});

export default router;
