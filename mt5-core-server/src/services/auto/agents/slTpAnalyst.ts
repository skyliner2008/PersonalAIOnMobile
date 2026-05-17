/**
 * SlTpAnalystAgent — V20.0 SMC-Aware SL/TP Placement
 *
 * Replaces the pure ATR-formula approach with a two-stage pipeline:
 *   Stage 1 (Deterministic): Extract SMC structural levels from multi-TF candles
 *            — Active FVGs, Order Blocks, Swing Highs/Lows.
 *            Pick the best candidate SL/TP anchors based on side.
 *   Stage 2 (LLM): Send the structural snapshot to the slTpAgent model for
 *            final confirmation, refinement, or override.
 *            Falls back to Stage-1 result if LLM fails or returns degenerate values.
 *
 * SL/TP Rules (per spec §V20.0):
 *   BUY  : TP = just below nearest resistance / bear OB top / bear FVG top
 *          SL = just below nearest support / bull FVG bottom / bull OB bottom
 *   SELL : TP = just above nearest support / bull OB bottom / bull FVG bottom
 *          SL = just above nearest resistance / bear FVG top / bear OB top
 *
 * 2026-05-01 — skyliner.jojo@gmail.com
 */

import { AutoTradingConfig, AnalysisSummary } from '../types.js';
import { generateForRole } from '../providers/dispatcher.js';
import { modelRankerService } from '../core/ModelRankerService.js';
import { parseLlmJson, LlmJsonError } from './jsonParse.js';
import { getSmcStructure, SmcStructure, FVG, OrderBlock } from '../analyzers/smc.js';
import { parseCandles } from '../../tracking.js';
import { roundToTick, tickSize } from '../utils.js';

// ─── Public Types ─────────────────────────────────────────────────────────────

export interface SlTpInput {
  symbol: string;
  side: 'BUY' | 'SELL';
  entry: number;
  atr: number;
  analyses: Record<string, AnalysisSummary>;           // multi-TF analysis summaries
  rawCandles: Record<string, ReturnType<typeof parseCandles>>;  // multi-TF parsed candles
  strategy?: string;
  targetRrr?: number;                                  // minimum RRR to enforce
  enableAiMode?: boolean;                              // if false, skip LLM refinement stage
}

export interface SlTpResult {
  sl: number;
  tp: number;
  confidence: number;      // 0-100, reflects how many structural confluences were found
  rationale: string;
  source: 'smc_ai' | 'smc_deterministic' | 'atr_fallback';
  modelUsed?: string;
}

// ─── Internal Helpers ─────────────────────────────────────────────────────────

/** Pick the nearest level above entry from a sorted list */
function nearestAbove(entry: number, levels: number[], bufferPts = 0): number | null {
  const above = levels.filter(l => l > entry + 0.0001).sort((a, b) => a - b);
  return above.length > 0 ? above[0] + bufferPts : null;
}

/** Pick the nearest level below entry from a sorted list */
function nearestBelow(entry: number, levels: number[], bufferPts = 0): number | null {
  const below = levels.filter(l => l < entry - 0.0001).sort((a, b) => b - a);
  return below.length > 0 ? below[0] - bufferPts : null;
}

/** Extract all candidate resistance price levels from SMC structure */
function extractResistanceLevels(smc: SmcStructure): number[] {
  const levels: number[] = [];
  // Bear OB tops and bottoms (price tends to stall at OB tops when approaching from below)
  for (const ob of smc.bearOBs) {
    levels.push(ob.top);
    if (ob.hasFVG) levels.push((ob.top + ob.bottom) / 2); // FVG midpoint = extra resistance
  }
  // Bear FVG tops (unfilled gap acts as ceiling)
  for (const fvg of smc.activeFVGs.filter(f => f.type === 'BEAR')) {
    levels.push(fvg.top);
  }
  // Swing highs
  levels.push(...smc.swingHighs);
  // Structure high
  if (smc.structureHigh > 0) levels.push(smc.structureHigh);
  return levels.filter(l => l > 0);
}

/** Extract all candidate support price levels from SMC structure */
function extractSupportLevels(smc: SmcStructure): number[] {
  const levels: number[] = [];
  // Bull OB tops and bottoms
  for (const ob of smc.bullOBs) {
    levels.push(ob.bottom);
    if (ob.hasFVG) levels.push((ob.top + ob.bottom) / 2);
  }
  // Bull FVG bottoms (unfilled gap acts as floor)
  for (const fvg of smc.activeFVGs.filter(f => f.type === 'BULL')) {
    levels.push(fvg.bottom);
  }
  // Swing lows
  levels.push(...smc.swingLows);
  // Structure low
  if (smc.structureLow > 0) levels.push(smc.structureLow);
  return levels.filter(l => l > 0);
}

/**
 * Deterministic SL/TP picker based on nearest SMC structural level.
 * Returns null for SL or TP if no structural level found (caller applies ATR fallback).
 */
export function pickSlTpDeterministic(
  input: SlTpInput,
  structures: Record<string, SmcStructure>,
): { sl: number | null; tp: number | null; confidence: number; rationale: string } {
  const { side, entry, atr, symbol } = input;
  const isXau = symbol.toUpperCase().includes('XAU') || symbol.toUpperCase().includes('GOLD');
  const buffer = isXau ? 0.5 : 0;  // buffer = 0.5 pts for gold, 0 for forex

  // Merge all timeframes — higher TF levels take priority by appearing first
  const allResistance: number[] = [];
  const allSupport: number[]    = [];

  // Priority order: H4 → H1 → current TF
  const tfOrder = ['H4', 'H1', 'M15', 'M5'];
  for (const tf of tfOrder) {
    if (structures[tf]) {
      allResistance.push(...extractResistanceLevels(structures[tf]));
      allSupport.push(...extractSupportLevels(structures[tf]));
    }
    // V20.0 — Merge structural levels from Python-based smartmoneyconcepts library
    const pySmc = input.analyses[tf]?.smc;
    if (pySmc) {
      if (Array.isArray(pySmc.ob)) {
        for (const ob of pySmc.ob) {
          if (ob.OB === 1) allSupport.push(ob.Bottom, ob.Top);
          else if (ob.OB === -1) allResistance.push(ob.Top, ob.Bottom);
        }
      }
      if (Array.isArray(pySmc.fvg)) {
        for (const fvg of pySmc.fvg) {
          if (fvg.FVG === 1) allSupport.push(fvg.Bottom);
          else if (fvg.FVG === -1) allResistance.push(fvg.Top);
        }
      }
      if (Array.isArray(pySmc.choch)) {
        for (const c of pySmc.choch) {
          if (c.CHOCH === 1) allSupport.push(c.Level);
          else if (c.CHOCH === -1) allResistance.push(c.Level);
        }
      }
    }
  }

  let sl: number | null = null;
  let tp: number | null = null;
  const notes: string[] = [];
  let confluenceCount = 0;

  if (side === 'BUY') {
    // SL: just below nearest support level below entry
    sl = nearestBelow(entry, allSupport, buffer);
    if (sl !== null) { notes.push(`SL anchored below support @${sl.toFixed(2)}`); confluenceCount++; }

    // TP: just below nearest resistance level above entry (avoid OB top = likely rejection)
    tp = nearestAbove(entry, allResistance, -buffer);
    if (tp !== null) { notes.push(`TP placed below resistance @${tp.toFixed(2)}`); confluenceCount++; }

  } else { // SELL
    // SL: just above nearest resistance level above entry
    sl = nearestAbove(entry, allResistance, buffer);
    if (sl !== null) { notes.push(`SL anchored above resistance @${sl.toFixed(2)}`); confluenceCount++; }

    // TP: just above nearest support level below entry
    tp = nearestBelow(entry, allSupport, -buffer);
    if (tp !== null) { notes.push(`TP placed above support @${tp.toFixed(2)}`); confluenceCount++; }
  }

  // Validate minimum SL distance (ensure it's not dangerously tight)
  // V25: Don't use 1.0 high-TF ATR as it ruins micro-breakout scalps.
  const minSlBuffer = isXau ? 2.5 : atr * 0.15; // 25 pips on gold, or 15% ATR
  if (sl !== null && Math.abs(entry - sl) < minSlBuffer) {
    notes.push(`SL too close (${Math.abs(entry - sl).toFixed(2)} < min buffer) — widened slightly`);
    sl = side === 'BUY' ? entry - minSlBuffer : entry + minSlBuffer;
  }

  // Validate minimum TP distance (only extend if we are lacking structure)
  if (tp !== null && sl !== null) {
    const slDist = Math.abs(entry - sl);
    const tpDist = Math.abs(tp - entry);
    const minRrr = input.targetRrr ?? 1.2;
    // V25: We no longer blindly push TP to meet RRR. If it's a valid structural TP, we keep it.
    // If RRR is poor, the Risk Gate will correctly block the trade.
    // However, if the TP is absurdly close (e.g. < 0.5R), maybe structure gave us a bad level.
    if (tpDist < slDist * 0.5) {
      notes.push(`TP absurdly close (RRR ${(tpDist / slDist).toFixed(2)}) — extended to 1.0R`);
      tp = side === 'BUY' ? entry + slDist * 1.0 : entry - slDist * 1.0;
    }
  }

  const confidence = Math.min(90, confluenceCount * 35 + 20);
  return { sl, tp, confidence, rationale: notes.join('; ') || 'no structural levels found' };
}

/** Format SMC structure into a compact string for the LLM prompt */
function formatStructureForPrompt(tf: string, smc: SmcStructure, entry: number, pySmc?: any): string {
  const lines: string[] = [`[${tf}]`];

  // 1. Heuristic results (TS)
  const nearFVGs = smc.activeFVGs
    .filter(f => Math.abs((f.top + f.bottom) / 2 - entry) / entry < 0.05) // within 5%
    .slice(-3);
  if (nearFVGs.length > 0) {
    lines.push(`  FVGs (TS): ${nearFVGs.map(f => `${f.type} ${f.bottom.toFixed(2)}-${f.top.toFixed(2)}`).join(', ')}`);
  }

  // 2. Python-based Professional SMC levels
  if (pySmc) {
    if (pySmc.fvg?.length) {
      const f = pySmc.fvg[pySmc.fvg.length - 1];
      lines.push(`  FVG (PY): ${f.FVG > 0 ? 'Bull' : 'Bear'} @${f.Bottom}-${f.Top}`);
    }
    if (pySmc.ob?.length) {
      const o = pySmc.ob[pySmc.ob.length - 1];
      lines.push(`  OB (PY): ${o.OB > 0 ? 'Bull' : 'Bear'} @${o.Bottom}-${o.Top}`);
    }
    if (pySmc.choch?.length) {
      const c = pySmc.choch[pySmc.choch.length - 1];
      lines.push(`  CHoCH (PY): ${c.CHOCH > 0 ? 'Bull' : 'Bear'} @${c.Level}`);
    }
  }

  const nearBullOB = smc.bullOBs
    .filter(ob => Math.abs((ob.top + ob.bottom) / 2 - entry) / entry < 0.05)
    .slice(-2);
  if (nearBullOB.length > 0) {
    lines.push(`  BullOB: ${nearBullOB.map(ob => `${ob.bottom.toFixed(2)}-${ob.top.toFixed(2)}${ob.hasFVG ? '+FVG' : ''}`).join(', ')}`);
  }

  const nearBearOB = smc.bearOBs
    .filter(ob => Math.abs((ob.top + ob.bottom) / 2 - entry) / entry < 0.05)
    .slice(-2);
  if (nearBearOB.length > 0) {
    lines.push(`  BearOB: ${nearBearOB.map(ob => `${ob.bottom.toFixed(2)}-${ob.top.toFixed(2)}${ob.hasFVG ? '+FVG' : ''}`).join(', ')}`);
  }

  if (smc.swingHighs.length > 0) {
    const nearHighs = smc.swingHighs.filter(h => Math.abs(h - entry) / entry < 0.05);
    if (nearHighs.length > 0) lines.push(`  SwingHighs: ${nearHighs.map(h => h.toFixed(2)).join(', ')}`);
  }
  if (smc.swingLows.length > 0) {
    const nearLows = smc.swingLows.filter(l => Math.abs(l - entry) / entry < 0.05);
    if (nearLows.length > 0) lines.push(`  SwingLows: ${nearLows.map(l => l.toFixed(2)).join(', ')}`);
  }

  return lines.join('\n');
}

// ─── Main Agent Class ─────────────────────────────────────────────────────────

export class SlTpAnalystAgent {

  public async analyze(
    config: AutoTradingConfig,
    input: SlTpInput,
  ): Promise<SlTpResult> {
    const { symbol, side, entry, atr } = input;
    const tick = tickSize(symbol, config.risk?.pointValueOverride || {});

    // ── Stage 1: Deterministic structural analysis ──────────────────────────
    const structures: Record<string, SmcStructure> = {};
    for (const [tf, candles] of Object.entries(input.rawCandles)) {
      if (candles.length >= 20) {
        structures[tf] = getSmcStructure(candles);
      }
    }

    const detResult = pickSlTpDeterministic(input, structures);

    // ── Stage 2: LLM refinement (Skip if EA-ONLY mode) ──────────────────────
    if (input.enableAiMode === false) {
      const finalSl = detResult.sl ?? (side === 'BUY' ? entry - atr : entry + atr);
      const finalTp = detResult.tp ?? (side === 'BUY' ? entry + atr * (input.targetRrr ?? 1.5) : entry - atr * (input.targetRrr ?? 1.5));
      return {
        sl: roundToTick(finalSl, tick) ?? finalSl,
        tp: roundToTick(finalTp, tick) ?? finalTp,
        confidence: detResult.confidence,
        rationale: detResult.rationale,
        source: (detResult.sl !== null && detResult.tp !== null) ? 'smc_deterministic' : 'atr_fallback',
      };
    }

    const structureText = Object.entries(structures)
      .map(([tf, smc]) => formatStructureForPrompt(tf, smc, entry, input.analyses[tf]?.smc))
      .join('\n');

    const primaryAnalysis = input.analyses[config.timeframe || 'H1']
      || Object.values(input.analyses)[0];

    const systemPrompt = `You are a precision SL/TP placement specialist using Smart Money Concepts (SMC).
You MUST respond with valid JSON ONLY. Start with '{', end with '}'. No markdown, no text outside JSON.
Use ONLY ASCII characters.`;

    const userPrompt = `TASK: Place exact SL and TP price levels for a ${side} trade on ${symbol}.

TRADE CONTEXT:
- Entry: ${entry.toFixed(2)}
- Side: ${side}
- Strategy: ${input.strategy || 'TREND_FOLLOW'}
- ATR(current TF): ${atr.toFixed(2)}
- Min RRR: ${input.targetRrr ?? 1.5}
- Regime: ${primaryAnalysis?.regime || 'TRENDING'}
- Bias: ${primaryAnalysis?.bias || side === 'BUY' ? 'BULL' : 'BEAR'}

SMC STRUCTURE (multi-timeframe):
${structureText || 'No structural data available.'}

DETERMINISTIC CANDIDATE:
- SL: ${detResult.sl?.toFixed(2) ?? 'none found'}
- TP: ${detResult.tp?.toFixed(2) ?? 'none found'}
- Rationale: ${detResult.rationale}

PLACEMENT RULES:
${side === 'BUY'
    ? '- SL: place BELOW the nearest key support / bull FVG bottom / bull OB bottom (at least 0.5 ATR from entry)\n- TP: place JUST BELOW the nearest resistance / bear OB top / bear FVG top (target zone, not above it)'
    : '- SL: place ABOVE the nearest key resistance / bear FVG top / bear OB top (at least 0.5 ATR from entry)\n- TP: place JUST ABOVE the nearest support / bull OB bottom / bull FVG bottom (target zone, not below it)'}
- Both SL and TP must be real price levels (not distances). Absolute price in same units as Entry.
- RRR must be >= ${input.targetRrr ?? 1.5}
- Improve on the deterministic candidate if you see a better structural level.

OUTPUT FORMAT (JSON only):
{
  "sl": <absolute price>,
  "tp": <absolute price>,
  "confidence": <0-100>,
  "rationale": "1-sentence English explanation of chosen levels"
}`;

    let llmSl: number | null = null;
    let llmTp: number | null = null;
    let llmConf = 0;
    let llmRationale = '';
    let modelUsed: string | undefined;

    try {
      const result = await generateForRole(
        { cfg: config, role: 'slTpAgent' },
        `${systemPrompt}\n\n${userPrompt}`,
        { maxOutputTokens: 256, temperature: 0.1 },
      );
      modelUsed = result.modelUsed;

      const parsed = parseLlmJson<{ sl: number; tp: number; confidence: number; rationale: string }>(result.text);
      llmSl    = typeof parsed.sl === 'number' ? parsed.sl : null;
      llmTp    = typeof parsed.tp === 'number' ? parsed.tp : null;
      llmConf  = typeof parsed.confidence === 'number' ? parsed.confidence : 50;
      llmRationale = parsed.rationale || '';

      // Sanity checks on LLM output — reject degenerate values
      const slPct = llmSl ? (llmSl / entry) * 100 : 0;
      const tpPct = llmTp ? (llmTp / entry) * 100 : 0;
      const slOnWrongSide = llmSl && ((side === 'BUY' && llmSl >= entry) || (side === 'SELL' && llmSl <= entry));
      const tpOnWrongSide = llmTp && ((side === 'BUY' && llmTp <= entry) || (side === 'SELL' && llmTp >= entry));
      const slIsDistance  = llmSl && (slPct < 0.1 || slPct > 200);
      const tpIsDistance  = llmTp && (tpPct < 0.1 || tpPct > 200);

      if (slOnWrongSide || slIsDistance) {
        const provider = typeof config.agentModels?.slTpAgent === 'object' ? config.agentModels.slTpAgent.provider : 'openrouter';
        modelRankerService.recordContentFailure(modelUsed || '', provider, 'slTpAgent');
        llmSl = null;
      }
      if (tpOnWrongSide || tpIsDistance) {
        const provider = typeof config.agentModels?.slTpAgent === 'object' ? config.agentModels.slTpAgent.provider : 'openrouter';
        modelRankerService.recordContentFailure(modelUsed || '', provider, 'slTpAgent');
        llmTp = null;
      }
    } catch (err: any) {
      if (err instanceof LlmJsonError) {
        modelRankerService.recordContentFailure(modelUsed || '', 'openrouter', 'slTpAgent');
      }
      // Fall through to deterministic result
    }

    // ── Resolve final SL/TP: prefer LLM if sane, else deterministic, else ATR ──
    const finalSl = llmSl ?? detResult.sl ?? (side === 'BUY' ? entry - atr : entry + atr);
    const finalTp = llmTp ?? detResult.tp ?? (side === 'BUY' ? entry + atr * (input.targetRrr ?? 1.5) : entry - atr * (input.targetRrr ?? 1.5));

    const source: SlTpResult['source'] =
      (llmSl !== null && llmTp !== null) ? 'smc_ai' :
      (detResult.sl !== null && detResult.tp !== null) ? 'smc_deterministic' :
      'atr_fallback';

    return {
      sl:   roundToTick(finalSl, tick) ?? finalSl,
      tp:   roundToTick(finalTp, tick) ?? finalTp,
      confidence: llmSl !== null ? llmConf : detResult.confidence,
      rationale:  llmRationale || detResult.rationale,
      source,
      modelUsed,
    };
  }
}


export const slTpAnalystAgent = new SlTpAnalystAgent();
