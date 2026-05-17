/**
 * V25.0 — Phase A health & status endpoints
 *
 * Mounted at /api/v25/* in src/index.ts. Read-only — no mutation.
 *
 * 2026-05-10 — skyliner.jojo@gmail.com
 */

import express from 'express';
import { v25Health, v25SymbolState } from '../services/auto/v25/index.js';
import { tickBuffers } from '../services/auto/v25/TickBuffer.js';
import { playbookSelector } from '../services/auto/v25/playbooks/index.js';

const router = express.Router();

router.get('/health', (_req, res) => {
  res.json({ success: true, data: v25Health() });
});

router.get('/ticks/:symbol', (req, res) => {
  const symbol = String(req.params.symbol || '').toUpperCase();
  if (!symbol) return res.status(400).json({ success: false, error: 'symbol required' });
  const buf = tickBuffers.get(symbol);
  const limit = Math.max(1, Math.min(200, Number(req.query.limit ?? 50)));
  res.json({
    success: true,
    data: {
      symbol,
      stats: buf.stats(),
      ticks: buf.lastN(limit),
    },
  });
});

router.get('/state/:symbol', (req, res) => {
  const symbol = String(req.params.symbol || '').toUpperCase();
  if (!symbol) return res.status(400).json({ success: false, error: 'symbol required' });
  res.json({ success: true, data: v25SymbolState(symbol) });
});

router.get('/candidates/:symbol', (req, res) => {
  const symbol = String(req.params.symbol || '').toUpperCase();
  if (!symbol) return res.status(400).json({ success: false, error: 'symbol required' });
  res.json({
    success: true,
    data: {
      symbol,
      candidates: playbookSelector.recentCandidates(symbol),
      skips: playbookSelector.recentSkips(symbol),
    },
  });
});

export default router;
