/**
 * providerRegistry — map of ProviderId → ProviderClient.
 * Caches `listModels` per (providerId, key fingerprint) for 5 min so the
 * Settings UI doesn't waste quota on repeat refreshes.
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

import { atWarn } from '../utils.js';
import { ProviderClient, ProviderId, ProviderModel } from './types.js';
import { geminiProvider } from './gemini.js';
import { openAIProvider } from './openai.js';
import { claudeProvider } from './claude.js';
import { openRouterProvider } from './openrouter.js';
import { ollamaProvider } from './ollama.js';
import { minimaxProvider } from './minimax.js';
import { nativeLocalProvider } from './native.js';
import { vertexAIProvider } from './vertexai.js';

const providers: Record<ProviderId, ProviderClient> = {
  gemini: geminiProvider,
  openai: openAIProvider,
  claude: claudeProvider,
  openrouter: openRouterProvider,
  ollama: ollamaProvider,
  minimax: minimaxProvider,
  native: nativeLocalProvider,
  vertexai: vertexAIProvider,
};

export function getProvider(id: ProviderId): ProviderClient {
  const p = providers[id];
  if (!p) throw new Error(`unknown provider: ${id}`);
  return p;
}

export function listProviderIds(): ProviderId[] {
  return Object.keys(providers) as ProviderId[];
}

interface CacheEntry { at: number; models: ProviderModel[]; }
const modelCache = new Map<string, CacheEntry>();
const TTL_MS = 5 * 60_000;

export async function listModelsCached(
  providerId: ProviderId,
  apiKey: string,
  baseUrl?: string,
  force = false,
): Promise<ProviderModel[]> {
  const fp = `${providerId}|${(apiKey || 'no-key').slice(0, 12)}|${baseUrl || ''}`;
  const hit = modelCache.get(fp);
  if (!force && hit && Date.now() - hit.at < TTL_MS) return hit.models;
  try {
    const models = await getProvider(providerId).listModels(apiKey, baseUrl);
    modelCache.set(fp, { at: Date.now(), models });
    return models;
  } catch (err: any) {
    atWarn(`[providerRegistry] listModels(${providerId}) failed: ${String(err?.message || err)}`);
    throw err;
  }
}

export function invalidateModelCache(providerId?: ProviderId): void {
  if (!providerId) { modelCache.clear(); return; }
  for (const k of Array.from(modelCache.keys())) {
    if (k.startsWith(`${providerId}|`)) modelCache.delete(k);
  }
}

export const PROVIDER_DEFAULTS: Record<ProviderId, { generationModel: string; embeddingModel: string | null; baseUrl?: string; }> = {
  gemini:     { generationModel: 'gemini-2.5-flash-lite', embeddingModel: 'gemini-embedding-001' },
  openai:     { generationModel: 'gpt-4o-mini',           embeddingModel: 'text-embedding-3-small' },
  claude:     { generationModel: 'claude-haiku-4-5-20251001', embeddingModel: null },
  openrouter: { generationModel: 'openai/gpt-4o-mini',    embeddingModel: 'openai/text-embedding-3-small' },
  // Ollama: qwen3-embedding = best Thai MTEB score. Alternative: bge-m3 (hybrid dense/sparse, 1024d)
  ollama:     { generationModel: 'llama3.2',              embeddingModel: 'qwen3-embedding', baseUrl: 'http://localhost:11434' },
  minimax:    { generationModel: 'MiniMax-M2.7',         embeddingModel: 'embo-01' },
  native:     { generationModel: 'none',                  embeddingModel: 'Xenova/bge-m3' },
  vertexai:   { generationModel: 'gemini-2.5-flash-lite', embeddingModel: 'text-embedding-005' },
};
