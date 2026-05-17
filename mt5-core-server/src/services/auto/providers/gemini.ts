import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fitDimensions, fetchWithTimeout } from './types.js';

const BASE = 'https://generativelanguage.googleapis.com/v1beta';

function classify(name: string, actions: string[]): ProviderModel['role'] {
  const lower = name.toLowerCase();
  if (lower.includes('embedding')) return 'embedding';
  if (actions.includes('embedContent')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  if (actions.includes('generateContent')) return 'text';
  return 'other';
}

class GeminiProvider implements ProviderClient {
  readonly id = 'gemini' as const;
  readonly displayName = 'Google Gemini';
  readonly supportsEmbeddings = true;

  async listModels(apiKey: string): Promise<ProviderModel[]> {
    const res = await fetchWithTimeout(`${BASE}/models?pageSize=200&key=${encodeURIComponent(apiKey)}`, { timeoutMs: 15_000 });
    if (!res.ok) throw new Error(`gemini list HTTP ${res.status}`);
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.models) ? json.models : [];
    return raw.map((m: any) => {
      const id: string = String(m.name || '');
      const shortName = id.startsWith('models/') ? id.slice('models/'.length) : id;
      const actions: string[] = Array.isArray(m.supportedGenerationMethods) ? m.supportedGenerationMethods : [];
      return {
        id: shortName,
        displayName: String(m.displayName || shortName),
        description: String(m.description || ''),
        role: classify(shortName, actions),
        contextLength: typeof m.inputTokenLimit === 'number' ? m.inputTokenLimit : undefined,
      };
    });
  }

  async ping(apiKey: string, model: string): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const res = await fetchWithTimeout(`${BASE}/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'ping' }] }] }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(apiKey: string, model: string, prompt: string, options: GenerateOptions = {}): Promise<GenerateResult> {
    const body: any = {
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
    };
    if (options.systemPrompt) {
      body.systemInstruction = { role: 'system', parts: [{ text: options.systemPrompt }] };
    }
    body.generationConfig = {
      temperature: options.temperature ?? 0.4,
      maxOutputTokens: options.maxOutputTokens,
      response_mime_type: options.jsonMode ? 'application/json' : undefined,
    };
    const res = await fetchWithTimeout(`${BASE}/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 60_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`gemini ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const text = json?.candidates?.[0]?.content?.parts?.map((p: any) => p?.text || '').join('') || '';
    return {
      text,
      modelUsed: model,
      promptTokens: json?.usageMetadata?.promptTokenCount,
      completionTokens: json?.usageMetadata?.candidatesTokenCount,
    };
  }

  async embed(apiKey: string, model: string, text: string): Promise<EmbedResult> {
    const res = await fetchWithTimeout(`${BASE}/models/${encodeURIComponent(model)}:embedContent?key=${encodeURIComponent(apiKey)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: { parts: [{ text }], role: 'user' } }),
      timeoutMs: 30_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`gemini embed ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: number[] = json?.embedding?.values || [];
    if (raw.length === 0) throw new Error(`gemini embed ${model} returned empty vector`);
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const geminiProvider = new GeminiProvider();
