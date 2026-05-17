/**
 * vertexProxy.ts — Vertex AI Proxy Route สำหรับ Mobile App
 *
 * ให้ Mobile app เรียก Vertex AI ผ่าน server ที่มี ADC credentials
 * Mobile ไม่ต้องจัดการ GCP auth เอง — แค่ส่ง request มาที่ server
 *
 * Endpoints:
 *   POST /api/vertex/generate    — Text generation
 *   POST /api/vertex/embed       — Embedding
 *   GET  /api/vertex/models      — List models
 *   GET  /api/vertex/status      — Check ADC + project readiness
 *
 * 2026-05-13 — skyliner.jojo@gmail.com
 */

import { Router } from 'express';
import { vertexAIProvider } from '../services/auto/providers/vertexai.js';
import { atLog, atWarn, atError } from '../services/auto/utils.js';

const router = Router();

// ─── Status Check ────────────────────────────────────────────────────────────

router.get('/status', async (_req, res) => {
  try {
    const projectId = process.env.VERTEX_PROJECT_ID;
    const location = process.env.VERTEX_LOCATION || 'us-central1';
    if (!projectId) {
      return res.json({
        success: false,
        available: false,
        error: 'VERTEX_PROJECT_ID not configured',
      });
    }
    // Try to get an ADC token as a quick health check
    const ping = await vertexAIProvider.ping('ADC', 'gemini-2.0-flash-lite');
    return res.json({
      success: true,
      available: ping.ok,
      projectId,
      location,
      latencyMs: ping.latencyMs,
      detail: ping.detail,
    });
  } catch (err: any) {
    return res.json({
      success: false,
      available: false,
      error: String(err?.message || err),
    });
  }
});

// ─── List Models ─────────────────────────────────────────────────────────────

router.get('/models', async (_req, res) => {
  try {
    const models = await vertexAIProvider.listModels('ADC');
    return res.json({ success: true, models });
  } catch (err: any) {
    atWarn(`[vertex-proxy] listModels failed: ${err?.message || err}`);
    return res.status(500).json({ success: false, error: String(err?.message || err) });
  }
});

// ─── Generate ────────────────────────────────────────────────────────────────

router.post('/generate', async (req, res) => {
  const { model, prompt, systemPrompt, temperature, maxOutputTokens, jsonMode } = req.body;
  if (!model || !prompt) {
    return res.status(400).json({ success: false, error: 'model and prompt are required' });
  }
  try {
    atLog(`[vertex-proxy] generate: model=${model}, prompt=${prompt.length} chars`);
    const result = await vertexAIProvider.generate('ADC', model, prompt, {
      systemPrompt,
      temperature,
      maxOutputTokens,
      jsonMode,
    });
    return res.json({ success: true, ...result });
  } catch (err: any) {
    atError(`[vertex-proxy] generate failed: ${err?.message || err}`);
    return res.status(500).json({ success: false, error: String(err?.message || err) });
  }
});

// ─── Embed ───────────────────────────────────────────────────────────────────

router.post('/embed', async (req, res) => {
  const { model, text } = req.body;
  if (!model || !text) {
    return res.status(400).json({ success: false, error: 'model and text are required' });
  }
  try {
    const result = await vertexAIProvider.embed('ADC', model, text);
    return res.json({ success: true, ...result });
  } catch (err: any) {
    atError(`[vertex-proxy] embed failed: ${err?.message || err}`);
    return res.status(500).json({ success: false, error: String(err?.message || err) });
  }
});

export default router;
