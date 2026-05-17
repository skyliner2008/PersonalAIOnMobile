/**
 * agentDispatcher — turn an `AgentModelChoice` (provider + model) into an
 * actual generate/embed call. Handles credential lookup and ensures the
 * system strictly follows user-configured models across multiple providers.
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

import type { AutoTradingConfig, AgentModelChoice } from '../types.js';
import {
  resolveProviderCredentials,
  resolveAgentChoice,
  AgentRole,
} from '../modelResolver.js';
import { getProvider, listModelsCached } from './registry.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { GenerateOptions, GenerateResult, EmbedResult } from './types.js';
import { atWarn } from '../utils.js';

export interface AgentInvokeContext {
  cfg: AutoTradingConfig;
  role: AgentRole;
}

export async function generateForRole(
  ctx: AgentInvokeContext,
  prompt: string,
  options?: GenerateOptions,
): Promise<GenerateResult> {
  const choice = resolveAgentChoice(ctx.cfg, ctx.role);
  if (!choice) {
    throw new Error(`No model configured for role "${ctx.role}". Check Settings.`);
  }

  try {
    // 2026-04-30: Check if this agent specifically requested Smart Free Fallback
    if (choice.useSmartFree || ctx.cfg.preferFreeOnly) {
      const topModels = modelRankerService.getTopModels(choice.provider, 1, ctx.role);
      if (topModels.length > 0 && topModels[0].model_id !== choice.model) {
        atWarn(`[Dispatcher] Role "${ctx.role}" using Smart Free Ranker. Swapping "${choice.model}" -> "${topModels[0].model_id}" (Score: ${topModels[0].score.toFixed(1)})`);
        const rankedChoice = { ...choice, model: topModels[0].model_id };
        
        // 2026-05-01: Auto-update the active settings so the Dashboard reflects the new model
        // and it remains the primary choice on the next boot.
        import('../../autoTradingService.js').then(({ autoTradingService }) => {
          const nextAgentModels: any = { ...ctx.cfg.agentModels };
          nextAgentModels[ctx.role] = {
            provider: rankedChoice.provider,
            model: rankedChoice.model,
            useSmartFree: rankedChoice.useSmartFree
          };
          autoTradingService.updateConfig({ agentModels: nextAgentModels } as any);
          if (ctx.cfg.agentModels) ctx.cfg.agentModels[ctx.role] = nextAgentModels[ctx.role];
        }).catch(err => atWarn(`[Dispatcher] Failed to persist swapped model to config: ${err}`));

        const res = await invoke(ctx.cfg, rankedChoice, prompt, options, ctx.role);
        return { ...res, modelUsed: rankedChoice.model };
      }
    }

    const res = await invoke(ctx.cfg, choice, prompt, options, ctx.role);
    return { ...res, modelUsed: choice.model };
  } catch (err) {
    // Universal Smart Fallback (only if preferFreeOnly is enabled)
    if (ctx.cfg.preferFreeOnly) {
      const anyErr = err as any;
      const errorMsg = (anyErr?.message || String(err)).toLowerCase();
      // 2026-04-30 — Better 429 detection (HTTP 429 status or common limit strings)
      const isDailyLimit = errorMsg.includes('free-models-per-day') || 
                           errorMsg.includes('daily limit') || 
                           errorMsg.includes('too many requests') ||
                           anyErr?.status === 429;
      
      atWarn(`[Dispatcher] Primary model "${choice.provider}/${choice.model}" failed. Initiating Smart Fallback (DailyLimit=${isDailyLimit})...`);
      
      if (isDailyLimit) {
        atWarn(`[Dispatcher] Account daily limit reached. Stopping fallback attempts to preserve bandwidth.`);
        throw err;
      }

      const fallbacks: AgentModelChoice[] = [];
      
      // 1. OpenRouter Ranked Fallbacks (Stay within OR if that was the choice)
      if (choice.provider === 'openrouter') {
        try {
          const { apiKey: orKey } = resolveProviderCredentials(ctx.cfg, 'openrouter');
          if (orKey) {
            const allModels = await listModelsCached('openrouter', orKey);

            // 2026-04-30 — drop models that physically cannot do reasoning
            // (OCR / vision / embedding / audio).  Same regex as the ranker.
            const INCAPABLE = /(\b|[-/])(ocr|vl|vision|embed|embedding|whisper|audio|tts|stt|speech|moderation|guard|safety|image|diffusion|sdxl|midjourney)([-/]|\b)/i;
            const freeModels = allModels.filter(m =>
              m.isFree &&
              m.id !== choice.model &&
              !INCAPABLE.test(m.id)
            );

            // 2026-04-30: Dynamic Ranking to avoid hardcode lock-in.
            // The previous version pinned `openrouter/free` as a permanent
            // anchor, but that meta-router returns empty bodies when its
            // internal pool is exhausted — causing the loop seen in the log.
            // Score it like everything else and let it lose rank when it fails.
            const sortedFree = freeModels.sort((a, b) => {
              const getScore = (id: string) => {
                let score = 0;
                const lowId = id.toLowerCase();
                if (lowId.includes('70b') || lowId.includes('120b') || lowId.includes('1t')) score += 50;
                if (lowId.includes('flash') || lowId.includes('exp')) score += 30;
                // 2026-05-05 — boost gemma-4 alongside gemma-3 (proven reliable)
                if (lowId.includes('gemma-4') || lowId.includes('gemma-3') || lowId.includes('llama-3.3')) score += 40;
                if (lowId.includes('instruct')) score += 10;
                // Mild boost for the meta-router but not an absolute anchor
                if (id === 'openrouter/free') score += 5;
                return score;
              };
              return getScore(b.id) - getScore(a.id);
            });

            // 2026-05-05 — เพิ่มจาก 5→8 เพื่อให้มี fallback เพียงพอ
            // เมื่อหลายตัวถูก blacklist (log พบ 5 ตัวถูก skip ทั้งหมด → 0 เหลือ)
            for (const fm of sortedFree.slice(0, 8)) { // Try top 8 dynamic choices
              fallbacks.push({ provider: 'openrouter', model: fm.id });
            }
          }
        } catch { /* ignore */ }
      }
      
      // 2. Gemini Native Fallbacks (Only if using Gemini Native)
      else if (choice.provider === 'gemini') {
        const geminiFallbacks: string[] = [
          'gemini-2.0-flash-lite',
          'gemini-1.5-flash-lite',
          'gemini-1.5-flash'
        ];
        for (const m of geminiFallbacks) {
          if (choice.model === m) continue;
          fallbacks.push({ provider: 'gemini', model: m });
        }
      }

      // Try fallbacks in order
      for (const fb of fallbacks) {
        try {
          // 2026-05-04 Fix #8: Skip fallback models that are blacklisted for this role.
          // e.g. llama-3.3-70b (streak=150+, permanently rate-limited) was retried every
          // cycle wasting 1-2s. Check blacklist before attempting.
          if (modelRankerService.isModelBlacklisted(fb.model, ctx.role)) {
            atWarn(`[Dispatcher] Skipping blacklisted fallback: ${fb.provider}/${fb.model} (role=${ctx.role})`);
            continue;
          }
          atWarn(`[Dispatcher] Retrying with fallback: ${fb.provider}/${fb.model}`);
          const res = await invoke(ctx.cfg, fb, prompt, options, ctx.role);

          // 2026-05-01 — Persist successful fallback to settings so Dashboard
          // reflects the working model and next cycle doesn't retry the broken one.
          import('../../autoTradingService.js').then(({ autoTradingService }) => {
            const nextAgentModels: any = { ...ctx.cfg.agentModels };
            nextAgentModels[ctx.role] = {
              provider: fb.provider,
              model: fb.model,
              useSmartFree: true,
            };
            autoTradingService.updateConfig({ agentModels: nextAgentModels } as any);
            if (ctx.cfg.agentModels) ctx.cfg.agentModels[ctx.role] = nextAgentModels[ctx.role];
            atWarn(`[Dispatcher] Persisted fallback model "${fb.provider}/${fb.model}" to settings for role "${ctx.role}"`);
          }).catch(() => {});

          return { ...res, modelUsed: fb.model };
        } catch (inner: any) {
          const innerMsg = (inner?.message || String(inner)).toLowerCase();
          atWarn(`[Dispatcher] Fallback "${fb.provider}/${fb.model}" failed: ${inner.message || inner}`);
          
          if (innerMsg.includes('free-models-per-day') || innerMsg.includes('daily limit')) {
            atWarn(`[Dispatcher] Account daily limit reached during fallback. Stopping.`);
            break; // Stop trying other models if account limit is hit
          }
        }
      }
    }
    // 2026-04-30 — Log the final error before giving up so we know why it failed
    const anyErr = err as any;
    atWarn(`[Dispatcher] AI reasoning failed after all attempts: ${anyErr?.message || err}`);
    throw err;
  }
}

export async function embedForRole(
  ctx: AgentInvokeContext,
  text: string,
): Promise<EmbedResult> {
  const choice = resolveAgentChoice(ctx.cfg, 'embedding');
  if (!choice) {
     throw new Error(`No embedding model configured. Check Settings.`);
  }
  return await invokeEmbed(ctx.cfg, choice, text);
}

async function invoke(
  cfg: AutoTradingConfig,
  choice: AgentModelChoice,
  prompt: string,
  options?: GenerateOptions,
  agentRole?: AgentRole,
): Promise<GenerateResult> {
  const { apiKey, baseUrl } = resolveProviderCredentials(cfg, choice.provider);
  if (!apiKey && choice.provider !== 'ollama' && choice.provider !== 'native') {
    throw new Error(`No API key configured for provider "${choice.provider}". Set it in dashboard Settings.`);
  }

  const start = Date.now();
  try {
    const res = await getProvider(choice.provider).generate(apiKey, choice.model, prompt, options, baseUrl);
    const latency = Date.now() - start;

    // 2026-04-30 fix — `openrouter/free` and a few other free routers return a
    // 200 with an empty body when their internal pool is exhausted.  The raw
    // generate call succeeds, so we used to count this as success and the
    // model kept its high rank.  Treat empty responses as a content failure
    // so the ranker can blacklist + penalise.
    const rawText = (res?.text ?? '').trim();
    if (rawText.length === 0) {
      modelRankerService.recordContentFailure(choice.model, choice.provider, agentRole);
      throw new Error(`Empty response from ${choice.provider}/${choice.model}`);
    }

    // Async record (don't block) — V20.0: pass agentRole for per-agent stats
    modelRankerService.recordExecution({
      modelId: choice.model,
      providerId: choice.provider,
      agentRole,
      latencyMs: latency,
      success: true,
      isTimeout: false
    });

    return res;
  } catch (err: any) {
    const latency = Date.now() - start;
    const isTimeout = /timeout|abort/i.test(err?.message || String(err));
    const errMsg = err?.message || String(err);

    // 2026-05-05 — HTTP 404 = model removed from provider → permanent ban
    // ไม่มีประโยชน์ retry model ที่ถูกถอดแล้ว (e.g. deepseek/deepseek-chat-v3.1:free)
    const is404 = errMsg.includes('404') || errMsg.includes('No endpoints found');
    if (is404 && agentRole) {
      // Record as content failure with very high streak → 30 day ban
      for (let i = 0; i < 10; i++) {
        modelRankerService.recordContentFailure(choice.model, choice.provider, agentRole);
      }
      atWarn(`[Dispatcher] 🪦 Model "${choice.model}" returned 404 — permanently banned for role=${agentRole}`);
    } else {
      modelRankerService.recordExecution({
        modelId: choice.model,
        providerId: choice.provider,
        agentRole,
        latencyMs: latency,
        success: false,
        isTimeout,
      });
    }

    throw err;
  }
}

async function invokeEmbed(
  cfg: AutoTradingConfig,
  choice: AgentModelChoice,
  text: string,
): Promise<EmbedResult> {
  const { apiKey, baseUrl } = resolveProviderCredentials(cfg, choice.provider);
  const provider = getProvider(choice.provider);
  if (!provider.supportsEmbeddings) {
    throw new Error(`Provider "${choice.provider}" does not support embeddings`);
  }
  if (!apiKey && choice.provider !== 'ollama' && choice.provider !== 'native') {
    throw new Error(`No API key configured for provider "${choice.provider}". Set it in dashboard Settings.`);
  }
  return provider.embed(apiKey, choice.model, text, baseUrl);
}
