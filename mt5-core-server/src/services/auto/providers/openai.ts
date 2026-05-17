import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fitDimensions, fetchWithTimeout } from './types.js';

const DEFAULT_BASE = 'https://api.openai.com/v1';

function classify(id: string): ProviderModel['role'] {
  const lower = id.toLowerCase();
  if (lower.includes('embedding')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  // gpt-* / o1-* / chatgpt-* are text completions
  if (lower.startsWith('gpt') || lower.startsWith('o1') || lower.startsWith('o3') || lower.startsWith('chatgpt')) return 'text';
  return 'other';
}

class OpenAIProvider implements ProviderClient {
  readonly id = 'openai' as const;
  readonly displayName = 'OpenAI';
  readonly supportsEmbeddings = true;

  async listModels(apiKey: string, baseUrl: string = DEFAULT_BASE): Promise<ProviderModel[]> {
    const res = await fetchWithTimeout(`${baseUrl}/models`, {
      headers: { Authorization: `Bearer ${apiKey}` },
      timeoutMs: 15_000,
    });
    if (!res.ok) throw new Error(`openai list HTTP ${res.status}`);
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.data) ? json.data : [];
    return raw.map((m: any) => ({
      id: String(m.id),
      displayName: String(m.id),
      description: m.owned_by ? `owner=${m.owned_by}` : '',
      role: classify(String(m.id)),
    }));
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
    if (options.maxOutputTokens) body.max_tokens = options.maxOutputTokens;
    if (options.jsonMode) body.response_format = { type: 'json_object' };

    const res = await fetchWithTimeout(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 60_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`openai ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
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
      throw new Error(`openai embed ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: number[] = json?.data?.[0]?.embedding || [];
    if (raw.length === 0) throw new Error(`openai embed ${model} returned empty vector`);
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const openAIProvider = new OpenAIProvider();
