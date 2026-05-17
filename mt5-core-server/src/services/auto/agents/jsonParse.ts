/**
 * Robust JSON parser for LLM responses.
 *
 * The Gemini family loves to wrap JSON in ```json fences, prefix with prose,
 * or emit multiple objects. The naive `text.match(/\{.*\}/s)?.[0]` we used
 * everywhere would silently swallow malformed responses → caller saw `{}`
 * → action defaulted to SKIP with no visibility.
 *
 * This parser:
 *   1. Strips ```json / ``` fences.
 *   2. Picks the last balanced top-level object (often the most complete).
 *   3. Throws a typed error so callers can log + fall back instead of
 *      pretending an empty object came back successfully.
 *
 * 2026-04-25 — skyliner.jojo@gmail.com
 */

export class LlmJsonError extends Error {
  constructor(public readonly raw: string, msg: string) {
    super(msg);
    this.name = 'LlmJsonError';
  }
}

const FENCE = /```(?:json|JSON)?\s*([\s\S]*?)\s*```/g;
const THINK_TAG = /<think>[\s\S]*?(?:<\/think>|$)/i;

function stripThinking(text: string): string {
  return text.replace(THINK_TAG, '').trim();
}

function stripFences(text: string): string {
  const fenced: string[] = [];
  let m: RegExpExecArray | null;
  FENCE.lastIndex = 0;
  while ((m = FENCE.exec(text)) !== null) fenced.push(m[1]);
  return fenced.length > 0 ? fenced.join('\n') : text;
}

/** Find the last balanced {...} block in `text`. */
function lastBalancedObject(text: string): string | null {
  let depth = 0;
  let start = -1;
  let lastWhole: string | null = null;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '{') {
      if (depth === 0) start = i;
      depth++;
    } else if (c === '}') {
      depth--;
      if (depth === 0 && start >= 0) {
        lastWhole = text.slice(start, i + 1);
        start = -1;
      }
    }
  }
  return lastWhole;
}

/**
 * Attempts to repair truncated JSON by appending missing closing characters.
 */
function repairTruncatedJson(text: string): string {
  let balanced = text.trim();
  if (balanced.startsWith('{') && !balanced.endsWith('}')) {
    // Basic heuristic: check if we are inside an object but it's not closed
    let depth = 0;
    let inString = false;
    for (let i = 0; i < balanced.length; i++) {
      if (balanced[i] === '"' && balanced[i - 1] !== '\\') inString = !inString;
      if (inString) continue;
      if (balanced[i] === '{' || balanced[i] === '[') depth++;
      else if (balanced[i] === '}' || balanced[i] === ']') depth--;
    }
    // If it ends mid-field like "key": "val, close the string first
    if (inString) balanced += '"';
    // Append missing closures
    while (depth > 0) {
      balanced += '}'; // We assume objects are more common for our schema
      depth--;
    }
  }
  return balanced;
}

/**
 * Parse the JSON payload from an LLM response. Returns `defaultValue` when
 * `allowEmpty: true`; otherwise throws LlmJsonError.
 */
export function parseLlmJson<T = any>(
  text: string,
  options: { defaultValue?: T; allowEmpty?: boolean } = {}
): T {
  const noThoughts = stripThinking(text || '');
  const cleaned = stripFences(noThoughts).trim();
  const balanced = lastBalancedObject(cleaned);
  let candidate = balanced ?? cleaned;

  // P4.2: Try to repair truncated JSON if balanced version failed
  if (!balanced && candidate.startsWith('{')) {
    candidate = repairTruncatedJson(candidate);
  }

  if (!candidate || candidate === '{}') {
    if (options.allowEmpty) return (options.defaultValue ?? ({} as T));
    throw new LlmJsonError(text || '', 'no JSON object found in LLM response');
  }
  try {
    return JSON.parse(candidate) as T;
  } catch (err: any) {
    if (options.allowEmpty) return (options.defaultValue ?? ({} as T));
    throw new LlmJsonError(text || '', `JSON.parse failed: ${err?.message || err}`);
  }
}
