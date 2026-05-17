/**
 * Provider abstraction — every LLM/embedding source the auto-trade engine
 * can call (Gemini, OpenAI, Claude, OpenRouter, Ollama) implements this
 * interface so agents stay provider-agnostic.
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

export type ProviderId = 'gemini' | 'openai' | 'claude' | 'openrouter' | 'ollama' | 'minimax' | 'native' | 'vertexai';

export interface ProviderModel {
  id: string;            // raw model id (e.g. "gpt-4o-mini", "claude-haiku-4-5")
  displayName: string;
  description?: string;
  role: 'text' | 'embedding' | 'vision' | 'other';
  contextLength?: number;
  /** Per-million-token pricing. Only providers that expose pricing (OpenRouter)
   *  populate this; cloud providers without public list-pricing leave it undefined. */
  pricing?: { prompt: number; completion: number };
  /** True when the model is completely free (prompt + completion = 0).
   *  Ollama models are always free. OpenRouter populates from its API. */
  isFree?: boolean;
}

export interface GenerateOptions {
  systemPrompt?: string;
  /** Higher-temperature outputs are noisier — agents default to 0.4 */
  temperature?: number;
  /** Cap response tokens; useful for small JSON responses. */
  maxOutputTokens?: number;
  /** Hard wall-clock timeout. Default 60s. */
  timeoutMs?: number;
  /** Force the model to output a JSON object (if supported by provider) */
  jsonMode?: boolean;
}

export interface GenerateResult {
  text: string;
  modelUsed: string;
  promptTokens?: number;
  completionTokens?: number;
}

export interface EmbedResult {
  /** L2-normalized vector, truncated to 768 dims to fit our HNSW index. */
  vector: number[];
  modelUsed: string;
  nativeDims: number;
}

export interface PingResult {
  ok: boolean;
  latencyMs: number;
  detail?: string;
}

export interface ProviderClient {
  readonly id: ProviderId;
  readonly displayName: string;

  /** List models the supplied API key can call. */
  listModels(apiKey: string, baseUrl?: string): Promise<ProviderModel[]>;

  /** Smoke-test: round-trip a tiny prompt through `model`. */
  ping(apiKey: string, model: string, baseUrl?: string): Promise<PingResult>;

  /**
   * Run a chat/text-completion. `prompt` is a single string for simplicity —
   * if the underlying API needs multi-turn, the implementation is responsible
   * for splitting on a sentinel.
   */
  generate(apiKey: string, model: string, prompt: string, options?: GenerateOptions, baseUrl?: string): Promise<GenerateResult>;

  /** Get a 768-dim L2-normalized embedding (Matryoshka-truncate when needed). */
  embed(apiKey: string, model: string, text: string, baseUrl?: string): Promise<EmbedResult>;

  /** True when this provider supports embeddings at all. */
  readonly supportsEmbeddings: boolean;
}

/** Universal target dimensionality for our HNSW index. */
export const EMBEDDING_TARGET_DIMS = 768;

/** Helper: Matryoshka-truncate (or pad) + L2 norm so every provider returns a uniform vector. */
export function fitDimensions(vec: number[], target: number = EMBEDDING_TARGET_DIMS): number[] {
  const sliced = vec.length > target
    ? vec.slice(0, target)
    : (vec.length < target ? [...vec, ...new Array(target - vec.length).fill(0)] : vec.slice());
  let sumSq = 0;
  for (const v of sliced) sumSq += v * v;
  const norm = Math.sqrt(sumSq);
  if (!isFinite(norm) || norm === 0) return sliced;
  for (let i = 0; i < sliced.length; i++) sliced[i] /= norm;
  return sliced;
}

/** Helper: AbortController-backed fetch with a timeout. */
export async function fetchWithTimeout(url: string, init: RequestInit & { timeoutMs?: number } = {}): Promise<Response> {
  const { timeoutMs = 60_000, ...rest } = init;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...rest, signal: controller.signal });
  } finally {
    clearTimeout(t);
  }
}
