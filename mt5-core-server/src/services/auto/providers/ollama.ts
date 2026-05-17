import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fitDimensions, fetchWithTimeout } from './types.js';

const DEFAULT_BASE = 'http://localhost:11434';

function classify(name: string): ProviderModel['role'] {
  const lower = name.toLowerCase();
  if (lower.includes('embed')) return 'embedding';
  if (lower.includes('vision') || lower.includes('llava')) return 'vision';
  return 'text';
}

// Ollama runs locally — `apiKey` is unused but kept in the signature so the
// dispatcher can call every provider uniformly. `baseUrl` defaults to the
// stock localhost daemon but the dashboard exposes it for users that proxy
// Ollama from another machine.
class OllamaProvider implements ProviderClient {
  readonly id = 'ollama' as const;
  readonly displayName = 'Ollama (local)';
  readonly supportsEmbeddings = true;

  private base(baseUrl?: string): string { return (baseUrl || DEFAULT_BASE).replace(/\/+$/, ''); }

  async listModels(_apiKey: string, baseUrl?: string): Promise<ProviderModel[]> {
    const res = await fetchWithTimeout(`${this.base(baseUrl)}/api/tags`, { timeoutMs: 8_000 });
    if (!res.ok) throw new Error(`ollama list HTTP ${res.status}`);
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.models) ? json.models : [];
    return raw.map((m: any) => ({
      id: String(m.name),
      displayName: String(m.name),
      description: m.details?.parameter_size ? `${m.details.parameter_size}` : '',
      role: classify(String(m.name)),
      contextLength: m.details?.context_length,
      isFree: true, // local models are always free
    }));
  }

  async ping(_apiKey: string, model: string, baseUrl?: string): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const res = await fetchWithTimeout(`${this.base(baseUrl)}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, prompt: 'ping', stream: false, options: { num_predict: 1 } }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(_apiKey: string, model: string, prompt: string, options: GenerateOptions = {}, baseUrl?: string): Promise<GenerateResult> {
    const body: any = {
      model,
      prompt,
      stream: false,
      options: { temperature: options.temperature ?? 0.4 },
    };
    if (options.systemPrompt) body.system = options.systemPrompt;
    if (options.maxOutputTokens) body.options.num_predict = options.maxOutputTokens;
    if (options.jsonMode) body.format = 'json';

    const res = await fetchWithTimeout(`${this.base(baseUrl)}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 120_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`ollama ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    return {
      text: String(json?.response || ''),
      modelUsed: model,
      promptTokens: json?.prompt_eval_count,
      completionTokens: json?.eval_count,
    };
  }

  async embed(_apiKey: string, model: string, text: string, baseUrl?: string): Promise<EmbedResult> {
    // Ollama v0.5+ prefers /api/embed with `input` field;
    // older versions use /api/embeddings with `prompt` field.
    // We try /api/embed first, fallback to /api/embeddings.
    let res = await fetchWithTimeout(`${this.base(baseUrl)}/api/embed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, input: text }),
      timeoutMs: 30_000,
    }).catch(() => null);

    if (!res || res.status === 404) {
      // Fallback to legacy /api/embeddings endpoint
      res = await fetchWithTimeout(`${this.base(baseUrl)}/api/embeddings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, prompt: text }),
        timeoutMs: 30_000,
      });
    }

    if (!res!.ok) {
      const detail = await res!.text().catch(() => '');
      throw new Error(`ollama embed ${model} HTTP ${res!.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res!.json();
    // /api/embed returns { embeddings: [[...]] }, /api/embeddings returns { embedding: [...] }
    const raw: number[] = json?.embeddings?.[0] || json?.embedding || [];
    if (raw.length === 0) throw new Error(`ollama embed ${model} returned empty vector`);
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const ollamaProvider = new OllamaProvider();
