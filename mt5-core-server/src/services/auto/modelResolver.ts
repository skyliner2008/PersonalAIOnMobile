/**
 * modelResolver — single source of truth for picking which provider+model
 * each agent uses. Order of precedence:
 *   1. Explicit per-agent override in config.agentModels.{role}
 *      - May be either an AgentModelChoice {provider, model} OR a legacy
 *        bare string (interpreted as {provider:'gemini', model:<string>}).
 *   2. Universal safe default UNIVERSAL_DEFAULT_CHOICE.
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

import type { AutoTradingConfig, AgentModelChoice } from './types.js';
import type { ProviderId } from './providers/types.js';

export const UNIVERSAL_DEFAULT_CHOICE: AgentModelChoice = {
  provider: 'gemini',
  model: 'gemini-2.0-flash-lite',
};

export const UNIVERSAL_EMBEDDING_DEFAULT_CHOICE: AgentModelChoice = {
  provider: 'native',
  model: 'bge-m3',
};

export type AgentRole =
  | 'analyst'
  | 'riskOfficer'
  | 'executionTrader'
  | 'postMortem'
  | 'reasoning'
  | 'embedding'
  | 'slTpAgent';          // V20.0 — SMC-aware SL/TP placement agent

const DEPRECATED: Record<string, string> = {
  'gemini-2.0-flash':         'gemini-2.0-flash',
  'gemini-2.0-flash-lite':    'gemini-2.0-flash-lite',
  'gemini-1.5-flash':         'gemini-1.5-flash',
  'gemini-1.5-flash-lite':    'gemini-1.5-flash-lite',
  'gemini-1.5-pro':           'gemini-1.5-pro',
  'gemini-pro':               'gemini-1.5-flash',
  'gemini-1.0-pro':           'gemini-1.5-flash',
  'gemini-ultra':             'gemini-1.5-pro',
  'gemini-embedding-2':       'text-embedding-004',
};

function rewriteModel(name: string): string {
  const trimmed = (name || '').trim();
  return DEPRECATED[trimmed] ?? trimmed;
}

function asChoice(raw: AgentModelChoice | string | undefined): AgentModelChoice | null {
  if (!raw) return null;
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (trimmed.includes('/')) {
        const parts = trimmed.split('/');
        return { provider: parts[0] as ProviderId, model: rewriteModel(parts.slice(1).join('/')) };
    }
    return { provider: 'gemini', model: rewriteModel(trimmed) };
  }
  return { 
    provider: raw.provider, 
    model: rewriteModel(raw.model), 
    useSmartFree: raw.useSmartFree 
  };
}

export function resolveAgentChoice(
  cfg: AutoTradingConfig | undefined,
  role: AgentRole,
): AgentModelChoice {
  const explicit = asChoice(cfg?.agentModels?.[role]);
  if (explicit) return explicit;

  // 2026-05-01 — slTpAgent inherits from the 'reasoning' agent config when
  // not explicitly configured. This avoids falling back to cfg.aiModel
  // (often Gemini native) which may be 403'd while OpenRouter reasoning works.
  if (role === 'slTpAgent') {
    const reasoningChoice = asChoice(cfg?.agentModels?.reasoning);
    if (reasoningChoice) return { ...reasoningChoice, useSmartFree: reasoningChoice.useSmartFree ?? true };
  }
  
  if (role !== 'embedding' && cfg?.aiModel) {
    const modelStr = cfg.aiModel;
    if (modelStr.includes('/')) {
        const parts = modelStr.split('/');
        const p = parts[0] as ProviderId;
        const m = parts.slice(1).join('/');
        return { provider: p, model: rewriteModel(m) };
    }
    return { provider: 'gemini', model: rewriteModel(modelStr) };
  }
  
  // 2026-04-30 — Default to Universal Choice if nothing configured
  if (role === 'embedding') return UNIVERSAL_EMBEDDING_DEFAULT_CHOICE;
  return UNIVERSAL_DEFAULT_CHOICE;
}

export function resolveModel(
  cfg: AutoTradingConfig | undefined,
  role: AgentRole,
): string {
  return resolveAgentChoice(cfg, role)?.model || '';
}

export function resolveEmbeddingModel(cfg: AutoTradingConfig | undefined): string {
  return resolveAgentChoice(cfg, 'embedding')?.model || '';
}

export function resolveProviderCredentials(
  cfg: AutoTradingConfig | undefined,
  provider: ProviderId,
): { apiKey: string; baseUrl?: string } {
  // Vertex AI uses ADC (Application Default Credentials) — no manual API key needed.
  // Return a placeholder so the dispatcher doesn't block on "missing key".
  if (provider === 'vertexai') {
    return { apiKey: cfg?.providerKeys?.vertexai || 'ADC' };
  }
  const apiKey =
    cfg?.providerKeys?.[provider] ||
    (provider === 'gemini' ? cfg?.apiKey : '') ||
    '';
  const baseUrl =
    provider === 'openai' ? cfg?.providerBaseUrls?.openai :
    provider === 'ollama' ? cfg?.providerBaseUrls?.ollama :
    undefined;
  return { apiKey, baseUrl };
}
