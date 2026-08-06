/**
 * vertexai.ts — Google Cloud Vertex AI Provider
 *
 * ใช้ Application Default Credentials (ADC) สำหรับ authentication
 * Endpoint: https://{LOCATION}-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/{LOCATION}/publishers/google/models/{MODEL}
 *
 * ตั้งค่า ADC:
 *   - Development: `gcloud auth application-default login`
 *   - Production: ใช้ Service Account ผ่าน GOOGLE_APPLICATION_CREDENTIALS
 *
 * Environment Variables (ทั้งหมดเป็น optional — auto-detect จาก ADC):
 *   - VERTEX_PROJECT_ID: GCP Project ID (override; ปกติอ่านจาก ADC ได้อัตโนมัติ)
 *   - VERTEX_LOCATION: Region (default: us-central1)
 *
 * 2026-05-13 — skyliner.jojo@gmail.com
 */

import { GoogleAuth } from 'google-auth-library';
import {
  ProviderClient,
  ProviderModel,
  GenerateOptions,
  GenerateResult,
  EmbedResult,
  PingResult,
  fitDimensions,
  fetchWithTimeout,
} from './types.js';
import { atLog, atError } from '../utils.js';

// ─── ADC Token Manager ──────────────────────────────────────────────────────

const auth = new GoogleAuth({
  scopes: ['https://www.googleapis.com/auth/cloud-platform'],
});

/** Cache the access token with its expiry so we don't re-fetch every call. */
let cachedToken: { token: string; expiresAt: number } | null = null;

async function getAccessToken(): Promise<string> {
  if (cachedToken && Date.now() < cachedToken.expiresAt - 30_000) {
    return cachedToken.token;
  }
  const client = await auth.getClient();
  const tokenResp = await client.getAccessToken();
  const token = tokenResp.token ?? tokenResp.res?.data?.access_token;
  if (!token) throw new Error('vertexai: failed to obtain ADC access token');
  // Default expiry = 1 hour; refresh 30s early
  cachedToken = { token, expiresAt: Date.now() + 3_600_000 };
  return token;
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Cache resolved project ID so we only call ADC once. */
let resolvedProjectId: string | null = null;

/**
 * Resolve GCP Project ID — ลำดับ:
 *   1. VERTEX_PROJECT_ID env var (explicit override)
 *   2. Auto-detect จาก ADC (application_default_credentials.json → quota_project_id / project_id)
 */
async function getProjectId(): Promise<string> {
  if (resolvedProjectId) return resolvedProjectId;

  // 1. Explicit env override
  const envPid = process.env.VERTEX_PROJECT_ID;
  if (envPid) {
    resolvedProjectId = envPid;
    return envPid;
  }

  // 2. Auto-detect from ADC
  const adcPid = await auth.getProjectId();
  if (adcPid) {
    resolvedProjectId = adcPid;
    return adcPid;
  }

  throw new Error(
    'Vertex AI: ไม่สามารถหา Project ID ได้ — ลอง:\n' +
    '  1. ตั้ง VERTEX_PROJECT_ID ใน .env\n' +
    '  2. หรือรัน: gcloud config set project YOUR_PROJECT_ID\n' +
    '  3. หรือรัน: gcloud auth application-default login --project YOUR_PROJECT_ID'
  );
}

function getLocation(): string {
  return process.env.VERTEX_LOCATION || 'us-central1';
}

function baseUrl(): string {
  const loc = getLocation();
  return `https://${loc}-aiplatform.googleapis.com/v1`;
}

async function modelPath(model: string): Promise<string> {
  const project = await getProjectId();
  const loc = getLocation();
  return `projects/${project}/locations/${loc}/publishers/google/models/${model}`;
}

function classify(name: string): ProviderModel['role'] {
  const lower = name.toLowerCase();
  if (lower.includes('embedding')) return 'embedding';
  if (lower.includes('vision') || lower.includes('image')) return 'vision';
  return 'text';
}

// ─── Vertex AI Provider ──────────────────────────────────────────────────────

class VertexAIProvider implements ProviderClient {
  readonly id = 'vertexai' as const;
  readonly displayName = 'Google Vertex AI';
  readonly supportsEmbeddings = true;

  /**
   * List models available in the project.
   * The `apiKey` param is unused (ADC handles auth) but kept for interface compat.
   */
  async listModels(_apiKey: string): Promise<ProviderModel[]> {
    const token = await getAccessToken();
    const project = await getProjectId();
    const loc = getLocation();
    const url = `${baseUrl()}/projects/${project}/locations/${loc}/publishers/google/models`;
    const res = await fetchWithTimeout(url, {
      headers: { Authorization: `Bearer ${token}` },
      timeoutMs: 15_000,
    });
    if (!res.ok) {
      // Vertex AI list models can return 403 if API not enabled
      const detail = await res.text().catch(() => '');
      throw new Error(`vertexai list HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: any[] = Array.isArray(json?.models) ? json.models : [];
    return raw.map((m: any) => {
      const fullName: string = String(m.name || '');
      // Extract short model ID from "publishers/google/models/gemini-2.0-flash"
      const shortName = fullName.includes('/models/')
        ? fullName.split('/models/').pop() || fullName
        : fullName;
      return {
        id: shortName,
        displayName: String(m.displayName || shortName),
        description: String(m.description || ''),
        role: classify(shortName),
        contextLength: typeof m.inputTokenLimit === 'number' ? m.inputTokenLimit : undefined,
      };
    });
  }

  async ping(_apiKey: string, model: string): Promise<PingResult> {
    const t0 = Date.now();
    try {
      const token = await getAccessToken();
      const url = `${baseUrl()}/${await modelPath(model)}:generateContent`;
      const res = await fetchWithTimeout(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'ping' }] }] }),
        timeoutMs: 12_000,
      });
      return { ok: res.ok, latencyMs: Date.now() - t0, detail: res.ok ? 'ok' : `HTTP ${res.status}` };
    } catch (err: any) {
      return { ok: false, latencyMs: Date.now() - t0, detail: String(err?.message || err) };
    }
  }

  async generate(
    _apiKey: string,
    model: string,
    prompt: string,
    options: GenerateOptions = {},
  ): Promise<GenerateResult> {
    const token = await getAccessToken();
    const body: any = {};

    if (options.messages && options.messages.length > 0) {
      body.contents = options.messages.map((msg: any) => {
        // Vertex AI only accepts 'user' and 'model' as roles.
        // Tool responses (role 'tool') must be sent as 'user' role.
        const role = msg.role === 'assistant' || msg.role === 'model' ? 'model' : 'user';

        // Extract parts array directly from client if available, or fallback to content
        let parts = msg.parts;
        if (!Array.isArray(parts) || parts.length === 0) {
          parts = [];
          if (msg.content) {
            parts.push({ text: msg.content });
          }
          if (msg.toolCalls && msg.toolCalls.length > 0) {
            msg.toolCalls.forEach((tc: any) => {
              parts.push({
                functionCall: {
                  name: tc.name,
                  args: typeof tc.arguments === 'string' ? JSON.parse(tc.arguments) : tc.arguments
                }
              });
            });
          }
        }

        // Clean parts to ensure no empty text parts reach Vertex AI (which causes 400 Model input cannot be empty)
        parts = parts.filter((p: any) => {
          if (p.text !== undefined && p.text !== null) {
            return p.text.trim() !== '';
          }
          return true;
        });

        // Vertex AI requires at least one part per message. If empty, provide a fallback.
        if (parts.length === 0) {
          parts = [{ text: ' ' }];
        }

        return {
          role,
          parts
        };
      });
    } else {
      body.contents = [{ role: 'user', parts: [{ text: prompt && prompt.trim() !== '' ? prompt : ' ' }] }];
    }

    if (options.systemPrompt && options.systemPrompt.trim() !== '') {
      body.systemInstruction = { parts: [{ text: options.systemPrompt }] };
    }

    if (options.tools && options.tools.length > 0) {
      body.tools = [{
        functionDeclarations: options.tools.map((t: any) => {
          const hasParams = t.parameters && typeof t.parameters === 'object' && Object.keys(t.parameters).length > 0;
          return {
            name: t.name,
            description: t.description,
            parameters: hasParams ? t.parameters : undefined
          };
        })
      }];
    }

    body.generationConfig = {
      temperature: options.temperature ?? 0.4,
      maxOutputTokens: options.maxOutputTokens,
      responseMimeType: options.jsonMode ? 'application/json' : undefined,
    };
    const url = `${baseUrl()}/${await modelPath(model)}:generateContent`;
    atLog(`[vertexai] generate URL: ${url}`);
    atLog(`[vertexai] generate body: ${JSON.stringify(body, null, 2)}`);
    const res = await fetchWithTimeout(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      timeoutMs: options.timeoutMs ?? 60_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`vertexai ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const parts = json?.candidates?.[0]?.content?.parts || [];
    const text = parts.map((p: any) => p?.text || '').join('') || '';
    const functionCalls = parts
      .filter((p: any) => p?.functionCall)
      .map((p: any) => ({
        id: `call_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
        name: p.functionCall.name,
        arguments: typeof p.functionCall.args === 'object' ? JSON.stringify(p.functionCall.args) : p.functionCall.args
      }));

    return {
      text,
      modelUsed: model,
      promptTokens: json?.usageMetadata?.promptTokenCount,
      completionTokens: json?.usageMetadata?.candidatesTokenCount,
      functionCalls: functionCalls.length > 0 ? functionCalls : undefined
    };
  }

  async embed(_apiKey: string, model: string, text: string): Promise<EmbedResult> {
    const token = await getAccessToken();
    const url = `${baseUrl()}/${await modelPath(model)}:predict`;
    const res = await fetchWithTimeout(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        instances: [{ content: text }],
      }),
      timeoutMs: 30_000,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`vertexai embed ${model} HTTP ${res.status} ${detail.slice(0, 240)}`);
    }
    const json: any = await res.json();
    const raw: number[] = json?.predictions?.[0]?.embeddings?.values || [];
    if (raw.length === 0) throw new Error(`vertexai embed ${model} returned empty vector`);
    return { vector: fitDimensions(raw), modelUsed: model, nativeDims: raw.length };
  }
}

export const vertexAIProvider = new VertexAIProvider();
