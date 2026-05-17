import { ProviderClient, ProviderModel, GenerateOptions, GenerateResult, EmbedResult, PingResult, fetchWithTimeout } from './types.js';

const BASE = 'https://api.anthropic.com/v1';
const ANTHROPIC_VERSION = '2023-06-01';

// Anthropic doesn't ship an embedding endpoint — agents that need embeddings
// must pick a different provider for the `embedding` role.
class ClaudeProvider implements ProviderClient {
  readonly id = 'claude' as const;
  readonly displayName = 'Anthropic Claude';
  readonly supportsEmbeddings = false;

  async listModels(apiKey: string): Promise<ProviderModel[]> {
    const res = await fetchWithTimeout(`${BASE}/models`, {
      headers: { 'x-api-key': apiKey, 'anthropic-version': ANTHROPIC_VERSION },
      timeoutMs: 15_000,
    });
    if (!res.ok) throw new Error(`claude list HTTP ${res.status}`);
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.data) ? json.data : [];
    return raw.map((m: any) => ({
      id: String(m.id),
      displayName: String(m.display_name || m.id),
      description: '',
      role: 'text' as const,
    }));
  }

  async ping(apiKey: string, model: string): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const res = await fetchWithTimeout(`${BASE}/messages`, {
        method: 'POST',
        headers: { 'x-api-key': apiKey, 'anthropic-version': ANTHROPIC_VERSION, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, max_tokens: 1, messages: [{ role: 'user', content: 'ping' }] }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(apiKey: string, model: string, prompt: string, options: GenerateOptions = {}): Promise<GenerateResult> {
    const body: any = {
      model,
      max_tokens: options.maxOutputTokens ?? 2048,
      temperature: options.temperature ?? 0.4,
      messages: [{ role: 'user', content: prompt }],
    };
    if (options.systemPrompt) body.system = options.systemPrompt;
    const res = await fetchWithTimeout(`${BASE}/messages`, {
      method: 'POST',
      headers: { 'x-api-key': apiKey, 'anthropic-version': ANTHROPIC_VERSION, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 60_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`claude ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const text = (json?.content || []).map((b: any) => b?.text || '').join('') || '';
    return {
      text,
      modelUsed: model,
      promptTokens: json?.usage?.input_tokens,
      completionTokens: json?.usage?.output_tokens,
    };
  }

  async embed(): Promise<EmbedResult> {
    throw new Error('Claude does not support embeddings — pick Gemini, OpenAI, or Ollama for the embedding role');
  }
}

export const claudeProvider = new ClaudeProvider();
