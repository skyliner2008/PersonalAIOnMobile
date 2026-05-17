import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fetchWithTimeout } from './types.js';

const BASE = 'https://openrouter.ai/api/v1';

function classify(id: string): ProviderModel['role'] {
  const lower = id.toLowerCase();
  if (lower.includes('embedding') || lower.includes('embed')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  return 'text';
}

class OpenRouterProvider implements ProviderClient {
  readonly id = 'openrouter' as const;
  readonly displayName = 'OpenRouter';
  // OpenRouter exposes embeddings only through certain models; we still set
  // `true` so the dashboard offers it — the per-model `role` filter does the
  // actual gating in the picker UI.
  readonly supportsEmbeddings = true;

  private headers(apiKey: string): Record<string, string> {
    return {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
      'HTTP-Referer': 'https://mt5-core-server.local',
      'X-Title': 'mt5-core-server',
    };
  }

  async listModels(apiKey: string): Promise<ProviderModel[]> {
    const res = await fetchWithTimeout(`${BASE}/models`, {
      headers: this.headers(apiKey),
      timeoutMs: 15_000,
    });
    if (!res.ok) throw new Error(`openrouter list HTTP ${res.status}`);
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.data) ? json.data : [];
    return raw.map((m: any) => {
      const promptPrice = parseFloat(m.pricing?.prompt || '0');
      const completionPrice = parseFloat(m.pricing?.completion || '0');
      return {
        id: String(m.id),
        displayName: String(m.name || m.id),
        description: String(m.description || ''),
        role: classify(String(m.id)),
        contextLength: typeof m.context_length === 'number' ? m.context_length : undefined,
        pricing: { prompt: promptPrice, completion: completionPrice },
        isFree: promptPrice === 0 && completionPrice === 0,
      };
    });
  }

  async ping(apiKey: string, model: string): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const res = await fetchWithTimeout(`${BASE}/chat/completions`, {
        method: 'POST',
        headers: this.headers(apiKey),
        body: JSON.stringify({ model, messages: [{ role: 'user', content: 'ping' }], max_tokens: 1 }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(apiKey: string, model: string, prompt: string, options: GenerateOptions = {}): Promise<GenerateResult> {
    const messages: any[] = [];
    if (options.systemPrompt) messages.push({ role: 'system', content: options.systemPrompt });
    messages.push({ role: 'user', content: prompt });
    const body: any = { model, messages, temperature: options.temperature ?? 0.4 };
    // Safety cap: default to 4096 if not specified, to prevent runaway 65k token outputs observed in logs.
    body.max_tokens = options.maxOutputTokens || 4096;

    if (options.jsonMode) {
      body.response_format = { type: 'json_object' };
    }

    const res = await fetchWithTimeout(`${BASE}/chat/completions`, {
      method: 'POST',
      headers: this.headers(apiKey),
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 60_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`openrouter ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
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

  async embed(apiKey: string, model: string, text: string): Promise<EmbedResult> {
    // OpenRouter routes embeddings the same way as OpenAI — use /embeddings.
    const res = await fetchWithTimeout(`${BASE}/embeddings`, {
      method: 'POST',
      headers: this.headers(apiKey),
      body: JSON.stringify({ model, input: text }),
      timeoutMs: 30_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`openrouter embed ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: number[] = json?.data?.[0]?.embedding || [];
    if (raw.length === 0) throw new Error(`openrouter embed ${model} returned empty vector`);
    const { fitDimensions } = await import('./types.js');
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const openRouterProvider = new OpenRouterProvider();
