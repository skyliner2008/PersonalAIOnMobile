type Candle = { t: number; o: number; h: number; l: number; c: number; v: number };

export function parseCandles(rows: unknown[]): Candle[] {
  return rows.map(parseCandle).filter((x): x is Candle => x !== null);
}

function parseCandle(row: unknown): Candle | null {
  if (Array.isArray(row)) {
    const t = toNumber(row[0]);
    const o = toNumber(row[1]);
    const h = toNumber(row[2]);
    const l = toNumber(row[3]);
    const c = toNumber(row[4]);
    const v = toNumber(row[5] ?? 0) ?? 0;
    if (t === null || o === null || h === null || l === null || c === null) return null;
    return { t: normalizeEpoch(t), o, h, l, c, v };
  }
  if (row && typeof row === 'object') {
    const bag = row as Record<string, unknown>;
    const t = toNumber(bag.t ?? bag.time ?? bag.timestamp ?? bag.date);
    const o = toNumber(bag.o ?? bag.open);
    const h = toNumber(bag.h ?? bag.high);
    const l = toNumber(bag.l ?? bag.low);
    const c = toNumber(bag.c ?? bag.close);
    const v = toNumber(bag.v ?? bag.volume ?? 0) ?? 0;
    if (t === null || o === null || h === null || l === null || c === null) return null;
    return { t: normalizeEpoch(t), o, h, l, c, v };
  }
  return null;
}

function normalizeEpoch(ts: number): number {
  return ts > 1_000_000_000_000 ? Math.floor(ts / 1000) : Math.floor(ts);
}

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function sma(values: number[], period: number): number | null {
  if (values.length < period) return null;
  const slice = values.slice(values.length - period);
  return slice.reduce((a, b) => a + b, 0) / period;
}

function ema(values: number[], period: number): number | null {
  if (values.length < period) return null;
  const k = 2 / (period + 1);
  let current = values.slice(0, period).reduce((a, b) => a + b, 0) / period;
  for (let i = period; i < values.length; i += 1) {
    current = values[i] * k + current * (1 - k);
  }
  return current;
}

function rsi(values: number[], period: number): number | null {
  if (values.length <= period) return null;
  let gains = 0;
  let losses = 0;
  for (let i = 1; i <= period; i += 1) {
    const diff = values[i] - values[i - 1];
    if (diff >= 0) gains += diff;
    else losses += Math.abs(diff);
  }
  let avgGain = gains / period;
  let avgLoss = losses / period;
  for (let i = period + 1; i < values.length; i += 1) {
    const diff = values[i] - values[i - 1];
    const gain = diff > 0 ? diff : 0;
    const loss = diff < 0 ? Math.abs(diff) : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
  }
  if (avgLoss === 0) return 100;
  const rs = avgGain / avgLoss;
  return 100 - 100 / (1 + rs);
}

/**
 * ATR using Wilder's RMA (Smoothed Moving Average) — matches MT5/TradingView standard.
 * Previously used SMA which caused ATR to be more reactive than broker values.
 */
function rmaATR(candles: Candle[], period: number): number | null {
  if (candles.length <= period) return null;
  // Compute True Range for all candles
  const trs: number[] = [];
  for (let i = 1; i < candles.length; i += 1) {
    const prevClose = candles[i - 1].c;
    const tr = Math.max(
      candles[i].h - candles[i].l,
      Math.abs(candles[i].h - prevClose),
      Math.abs(candles[i].l - prevClose)
    );
    trs.push(tr);
  }
  if (trs.length < period) return null;
  // Seed = SMA of first `period` TR values (Wilder's initialization)
  let atr = trs.slice(0, period).reduce((a, b) => a + b, 0) / period;
  // Wilder's RMA: atr = (prev * (n-1) + current) / n
  for (let i = period; i < trs.length; i += 1) {
    atr = (atr * (period - 1) + trs[i]) / period;
  }
  return atr;
}

/**
 * MACD (12, 26, 9) — proper implementation using EMA cross.
 * Returns macdLine, signalLine (EMA-9 of MACD), and histogram.
 */
function macd(
  closes: number[],
  fastPeriod = 12,
  slowPeriod = 26,
  signalPeriod = 9
): { macdLine: number | null; signalLine: number | null; histogram: number | null } {
  if (closes.length < slowPeriod + signalPeriod) {
    return { macdLine: null, signalLine: null, histogram: null };
  }
  const kFast = 2 / (fastPeriod + 1);
  const kSlow = 2 / (slowPeriod + 1);
  const kSig  = 2 / (signalPeriod + 1);

  // Seed EMA fast & slow
  let emaFast = closes.slice(0, fastPeriod).reduce((a, b) => a + b, 0) / fastPeriod;
  let emaSlow = closes.slice(0, slowPeriod).reduce((a, b) => a + b, 0) / slowPeriod;

  // Warm-up emaFast from fastPeriod → slowPeriod
  for (let i = fastPeriod; i < slowPeriod; i += 1) {
    emaFast = closes[i] * kFast + emaFast * (1 - kFast);
  }

  // Accumulate MACD history from slowPeriod onward
  const macdValues: number[] = [];
  for (let i = slowPeriod; i < closes.length; i += 1) {
    emaFast = closes[i] * kFast + emaFast * (1 - kFast);
    emaSlow = closes[i] * kSlow + emaSlow * (1 - kSlow);
    macdValues.push(emaFast - emaSlow);
  }

  if (macdValues.length === 0) return { macdLine: null, signalLine: null, histogram: null };

  const macdLine = macdValues[macdValues.length - 1];
  if (macdValues.length < signalPeriod) {
    return { macdLine, signalLine: null, histogram: null };
  }

  // Signal line = EMA(9) of MACD values
  let signalLine = macdValues.slice(0, signalPeriod).reduce((a, b) => a + b, 0) / signalPeriod;
  for (let i = signalPeriod; i < macdValues.length; i += 1) {
    signalLine = macdValues[i] * kSig + signalLine * (1 - kSig);
  }

  return { macdLine, signalLine, histogram: macdLine - signalLine };
}

/**
 * Stochastic Oscillator %K/%D (14, 3).
 * %K = (close - lowestLow) / (highestHigh - lowestLow) × 100
 * %D = SMA(3) of %K
 */
function stochastic(
  candles: Candle[],
  period = 14,
  smooth = 3
): { k: number | null; d: number | null } {
  if (candles.length < period + smooth) return { k: null, d: null };

  const kValues: number[] = [];
  for (let i = period - 1; i < candles.length; i += 1) {
    const slice = candles.slice(i - period + 1, i + 1);
    const high  = Math.max(...slice.map((c) => c.h));
    const low   = Math.min(...slice.map((c) => c.l));
    const close = candles[i].c;
    kValues.push(high === low ? 50 : ((close - low) / (high - low)) * 100);
  }

  const k = kValues[kValues.length - 1];
  if (kValues.length < smooth) return { k, d: null };

  const d = kValues.slice(-smooth).reduce((a, b) => a + b, 0) / smooth;
  return { k, d };
}

export function indicators(candles: Candle[]): Record<string, number | null> {
  const closes = candles.map((c) => c.c);
  const { macdLine, signalLine: macdSignal, histogram: macdHistogram } = macd(closes);
  const { k: stochK, d: stochD } = stochastic(candles);
  return {
    sma20:         sma(closes, 20),
    sma50:         sma(closes, 50),
    ema20:         ema(closes, 20),
    rsi14:         rsi(closes, 14),
    atr14:         rmaATR(candles, 14),   // Wilder's RMA — matches MT5 standard
    macdLine,
    macdSignal,
    macdHistogram,
    stochK,
    stochD,
  };
}
