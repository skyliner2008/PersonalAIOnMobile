/**
 * modelRegistry — fetches the list of generation/embedding models that the
 * provided Gemini API key can actually call. Cached for 5 min so repeated
 * dashboard hits don't waste quota.
 *
 * Returned shape is intentionally simple — the dashboard just needs:
 *   - id (e.g. "models/gemini-2.5-flash-lite")
 *   - shortName (e.g. "gemini-2.5-flash-lite") — what we feed to the SDK
 *   - displayName, description (for the picker UI)
 *   - role hint (text / embedding / vision) so the UI can group/filter
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

import { atWarn } from './utils.js';

export interface RegistryModel {
  id: string;
  shortName: string;
  displayName: string;
  description: string;
  inputTokenLimit?: number;
  outputTokenLimit?: number;
  supportedActions: string[];
  role: 'text' | 'embedding' | 'vision' | 'other';
}

interface CacheEntry {
  at: number;
  models: RegistryModel[];
}

const CACHE_TTL_MS = 5 * 60_000;
const cache = new Map<string, CacheEntry>();

function classifyRole(actions: string[], name: string): RegistryModel['role'] {
  const lower = name.toLowerCase();
  if (lower.includes('embedding')) return 'embedding';
  if (actions.includes('embedContent')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  if (actions.includes('generateContent')) return 'text';
  return 'other';
}

export async function listModels(apiKey: string, force = false): Promise<RegistryModel[]> {
  if (!apiKey) return [];
  const fingerprint = apiKey.slice(0, 12);
  const hit = cache.get(fingerprint);
  if (!force && hit && Date.now() - hit.at < CACHE_TTL_MS) return hit.models;

  const url = `https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=${encodeURIComponent(apiKey)}`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 15_000);
  try {
    const res = await fetch(url, { method: 'GET', signal: controller.signal });
    clearTimeout(t);
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`HTTP ${res.status} — ${body.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.models) ? json.models : [];
    const models: RegistryModel[] = raw.map((m: any) => {
      const id: string = String(m.name || ''); // e.g. "models/gemini-2.5-flash-lite"
      const shortName = id.startsWith('models/') ? id.slice('models/'.length) : id;
      const actions: string[] = Array.isArray(m.supportedGenerationMethods) ? m.supportedGenerationMethods : [];
      return {
        id,
        shortName,
        displayName: String(m.displayName || shortName),
        description: String(m.description || ''),
        inputTokenLimit: typeof m.inputTokenLimit === 'number' ? m.inputTokenLimit : undefined,
        outputTokenLimit: typeof m.outputTokenLimit === 'number' ? m.outputTokenLimit : undefined,
        supportedActions: actions,
        role: classifyRole(actions, shortName),
      };
    });
    cache.set(fingerprint, { at: Date.now(), models });
    return models;
  } catch (err: any) {
    clearTimeout(t);
    atWarn(`[modelRegistry] list failed: ${String(err?.message || err)}`);
    throw err;
  }
}

/** Fast smoke test — sends a single token to verify the key works. */
export async function pingApiKey(apiKey: string, model = 'gemini-2.5-flash-lite'): Promise<{ ok: boolean; latencyMs: number; detail?: string }> {
  if (!apiKey) return { ok: false, latencyMs: 0, detail: 'no api key' };
  const t0 = Date.now();
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 12_000);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'ping' }] }] }),
      signal: controller.signal,
    });
    clearTimeout(t);
    const ok = res.ok;
    const detail = ok ? 'ok' : `HTTP ${res.status}`;
    return { ok, latencyMs: Date.now() - t0, detail };
  } catch (err: any) {
    clearTimeout(t);
    return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
  }
}
