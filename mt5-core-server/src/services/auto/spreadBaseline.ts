/**
 * SpreadBaseline — tracks each symbol's "normal" spread by rolling median
 * over the last N observed ticks. The risk gate then judges by % deviation
 * from baseline rather than a single hard-coded pts cap that can't possibly
 * fit XAUUSD (30 pts), XAGUSD (41 pts), and XBTUSD (~2600 pts) at the same time.
 *
 * 2026-04-26 — skyliner.jojo@gmail.com
 */

const SAMPLE_WINDOW = 30;       // last N ticks counted into baseline
const MIN_SAMPLES   = 5;        // need this many samples before baseline is "trusted"

interface History {
  samples: number[];            // ring buffer of recent spreads
  cursor: number;               // next write position
  count: number;                // total writes (caps at SAMPLE_WINDOW for read)
}

class SpreadBaselineService {
  private store = new Map<string, History>();

  /** Record an observed spread sample (in broker pts) for a symbol. */
  public observe(symbol: string, spread: number): void {
    if (!isFinite(spread) || spread < 0) return;
    const key = symbol.toUpperCase();
    let h = this.store.get(key);
    if (!h) {
      h = { samples: new Array(SAMPLE_WINDOW).fill(0), cursor: 0, count: 0 };
      this.store.set(key, h);
    }
    h.samples[h.cursor] = spread;
    h.cursor = (h.cursor + 1) % SAMPLE_WINDOW;
    h.count = Math.min(h.count + 1, SAMPLE_WINDOW);
  }

  /**
   * Median of recent samples for a symbol. `null` until we have MIN_SAMPLES
   * observations — caller should treat that as "no opinion yet".
   */
  public baseline(symbol: string): number | null {
    const h = this.store.get(symbol.toUpperCase());
    if (!h || h.count < MIN_SAMPLES) return null;
    const view = h.samples.slice(0, h.count).filter((v) => v > 0).sort((a, b) => a - b);
    if (view.length === 0) return null;
    const mid = Math.floor(view.length / 2);
    return view.length % 2 === 0 ? (view[mid - 1] + view[mid]) / 2 : view[mid];
  }

  /**
   * Decide whether a current spread looks abnormal.
   *
   * Returns a verdict + the values it used so the caller can log it cleanly.
   * Logic:
   *  - If we have an absolute override (config.risk.maxSpreadOverride[sym])
   *    and the current spread blows past it, hard reject — that's the user's
   *    explicit safety ceiling for this symbol.
   *  - Else, use baseline*(1 + pct/100) as the dynamic cap.
   *  - Until baseline is established, fall back to a 5x bootstrapping cap so
   *    we don't reject every trade in the warm-up window. We still cap at
   *    the absolute override if present.
   */
  public verdict(
    symbol: string,
    currentSpread: number,
    opts: {
      absoluteOverride?: number;     // hard ceiling (per-symbol or global default)
      maxIncreasePct?: number;       // 100 = reject when spread > baseline*2
    } = {},
  ): { ok: boolean; reason: string; baseline: number | null; cap: number; pctVsBase: number | null } {
    const base = this.baseline(symbol);
    const pct = opts.maxIncreasePct ?? 100;
    const abs = opts.absoluteOverride;
    if (abs !== undefined && abs > 0 && currentSpread > abs) {
      return {
        ok: false,
        reason: `spread ${currentSpread} > absolute cap ${abs}`,
        baseline: base,
        cap: abs,
        pctVsBase: base ? ((currentSpread - base) / base) * 100 : null,
      };
    }
    if (base === null) {
      // Bootstrapping window — just enforce the absolute cap if present.
      return { ok: true, reason: 'baseline warming up', baseline: null, cap: abs ?? Number.POSITIVE_INFINITY, pctVsBase: null };
    }
    const dynamicCap = base * (1 + pct / 100);
    const pctVs = ((currentSpread - base) / base) * 100;
    if (currentSpread > dynamicCap) {
      return {
        ok: false,
        reason: `spread ${currentSpread} > baseline ${base.toFixed(0)} +${pct}% (cap ${dynamicCap.toFixed(0)}, +${pctVs.toFixed(0)}%)`,
        baseline: base,
        cap: dynamicCap,
        pctVsBase: pctVs,
      };
    }
    return {
      ok: true,
      reason: `spread ${currentSpread} within baseline +${pctVs.toFixed(0)}%`,
      baseline: base,
      cap: dynamicCap,
      pctVsBase: pctVs,
    };
  }

  /** For the dashboard: get { baseline, samples } per symbol for display. */
  public snapshot(): Record<string, { baseline: number | null; samples: number }> {
    const out: Record<string, { baseline: number | null; samples: number }> = {};
    for (const [key, h] of this.store.entries()) {
      out[key] = { baseline: this.baseline(key), samples: h.count };
    }
    return out;
  }
}

export const spreadBaseline = new SpreadBaselineService();
