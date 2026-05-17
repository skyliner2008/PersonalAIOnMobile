import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fitDimensions, fetchWithTimeout } from './types.js';

const DEFAULT_BASE = 'https://api.minimax.io/v1';

function classify(id: string): ProviderModel['role'] {
  const lower = id.toLowerCase();
  if (lower.includes('embedding')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  // minimax-m2.7 / minimax-m2.5 / abab* are text completions
  if (lower.includes('minimax') || lower.includes('abab')) return 'text';
  return 'other';
}

class MinimaxProvider implements ProviderClient {
  readonly id = 'minimax' as const;
  readonly displayName = 'MiniMax';
  readonly supportsEmbeddings = true;

  async listModels(apiKey: string, baseUrl: string = DEFAULT_BASE): Promise<ProviderModel[]> {
    const hardcodedModels: ProviderModel[] = [
      { id: 'MiniMax-M2.7', displayName: 'MiniMax-M2.7', role: 'text', description: 'MiniMax M2.7 flagship' },
      { id: 'MiniMax-M2.7-highspeed', displayName: 'MiniMax-M2.7-highspeed', role: 'text', description: 'MiniMax M2.7 high-speed' },
      { id: 'MiniMax-M2.5', displayName: 'MiniMax-M2.5', role: 'text', description: 'MiniMax M2.5 flagship' },
      { id: 'MiniMax-M2.5-highspeed', displayName: 'MiniMax-M2.5-highspeed', role: 'text', description: 'MiniMax M2.5 high-speed' },
      { id: 'abab6.5s-chat', displayName: 'abab6.5s-chat', role: 'text', description: 'MiniMax legacy fast model' },
      { id: 'embo-01', displayName: 'embo-01', role: 'embedding', description: 'MiniMax embedding model' },
    ];

    try {
      const res = await fetchWithTimeout(`${baseUrl}/models`, {
        headers: { Authorization: `Bearer ${apiKey}` },
        timeoutMs: 15_000,
      });
      
      if (!res.ok) {
        return hardcodedModels;
      }
      
      const json: any = await res.json();
      const raw: any[] = Array.isArray(json?.data) ? json.data : [];
      
      if (raw.length === 0) {
        // MiniMax API sometimes returns 200 but { data: null }. 
        // If empty, yield the validated hardcoded list so UI is usable.
        return hardcodedModels;
      }

      return raw.map((m: any) => ({
        id: String(m.id),
        displayName: String(m.id),
        description: m.owned_by ? `owner=${m.owned_by}` : '',
        role: classify(String(m.id)),
      }));
    } catch (err) {
      return hardcodedModels;
    }
  }

  async ping(apiKey: string, model: string, baseUrl: string = DEFAULT_BASE): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const res = await fetchWithTimeout(`${baseUrl}/chat/completions`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, messages: [{ role: 'user', content: 'ping' }], max_tokens: 1 }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(apiKey: string, model: string, prompt: string, options: GenerateOptions = {}, baseUrl: string = DEFAULT_BASE): Promise<GenerateResult> {
    const messages: any[] = [];
    if (options.systemPrompt) messages.push({ role: 'system', content: options.systemPrompt });
    messages.push({ role: 'user', content: prompt });
    const body: any = {
      model,
      messages,
      temperature: options.temperature ?? 0.4,
    };
    const baseLimit = options.maxOutputTokens ?? 2048;
    // 2026-05-10: M2 series are reasoning models. Their lengthy <think> trace consumes
    // token budget. Floor at 4096 to ensure final JSON payload is not truncated.
    // MiniMax API prefers `max_completion_tokens`. We supply both for robustness.
    const finalLimit = (model.includes('M2.') && baseLimit < 4096) ? 4096 : baseLimit;
    body.max_tokens = finalLimit;
    body.max_completion_tokens = finalLimit;
    
    // MiniMax specific logic could go here if needed (e.g. reasoning_split)

    const res = await fetchWithTimeout(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 180_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`minimax ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const text = json?.choices?.[0]?.message?.content || '';
    return {
      text,
      modelUsed: model,
      promptTokens: json?.usage?.prompt_tokens,
      completionTokens: json?.usage?.completion_tokens,
    };
  }

  async embed(apiKey: string, model: string, text: string, baseUrl: string = DEFAULT_BASE): Promise<EmbedResult> {
    const res = await fetchWithTimeout(`${baseUrl}/embeddings`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, input: text }),
      timeoutMs: 30_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`minimax embed ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: number[] = json?.data?.[0]?.embedding || [];
    if (raw.length === 0) throw new Error(`minimax embed ${model} returned empty vector`);
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const minimaxProvider = new MinimaxProvider();
