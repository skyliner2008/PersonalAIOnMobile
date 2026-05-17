import { Counter, Histogram, register } from 'prom-client';
export { register };

// Clean up previous registration if any (useful for hot-reload in dev)
register.clear();

/**
 * Total number of trades attempted (placed via MT5).
 */
export const tradesPlacedTotal = new Counter({
  name: 'mt5_trades_placed_total',
  help: 'Total number of trades successfully placed on MT5 terminal',
  labelNames: ['symbol', 'side', 'strategy'],
});

/**
 * Total number of trades rejected (by risk engine or AI).
 */
export const tradesRejectedTotal = new Counter({
  name: 'mt5_trades_rejected_total',
  help: 'Total number of trades rejected by risk gate or AI logic',
  labelNames: ['symbol', 'reason'],
});

/**
 * Duration of a complete market analysis cycle.
 */
export const cycleDurationSeconds = new Histogram({
  name: 'mt5_cycle_duration_seconds',
  help: 'Duration of the market analysis cycle in seconds',
  buckets: [1, 5, 10, 30, 60, 120, 300],
});

/**
 * Duration of calls to the Python bridge.
 */
export const bridgeCallDurationSeconds = new Histogram({
  name: 'mt5_bridge_call_duration_seconds',
  help: 'Latency of calls to the Python MT5 bridge in seconds',
  labelNames: ['method', 'path', 'status'],
  buckets: [0.1, 0.5, 1, 2, 5, 10],
});

// --- Phase 4: Entry Quality & Management Observability ---

/**
 * P4.1: Trades blocked by Zone-Aware Gate (BUY in PREMIUM, SELL in DISCOUNT).
 */
export const zoneGateBlocksTotal = new Counter({
  name: 'mt5_zone_gate_blocks_total',
  help: 'Trades blocked by Premium/Discount zone-aware gate',
  labelNames: ['symbol', 'side', 'zone'],
});

/**
 * P4.1: SL buffer activations (prevented 10016 Invalid Stops).
 */
export const slBufferActivationsTotal = new Counter({
  name: 'mt5_sl_buffer_activations_total',
  help: 'SL positions adjusted by dynamic FVG/ATR buffer (prevented Invalid Stops)',
  labelNames: ['symbol', 'trigger'],  // trigger: 'fvg_overlap' | 'min_distance' | 'stops_level'
});

/**
 * P4.1: Counter-trend blocks (P1.5).
 */
export const counterTrendBlocksTotal = new Counter({
  name: 'mt5_counter_trend_blocks_total',
  help: 'Counter-trend trades blocked due to low confluence',
  labelNames: ['symbol', 'side'],
});

/**
 * P4.1: Cluster management events (flip, concentration close, smart scale-in reject).
 */
export const managementEventsTotal = new Counter({
  name: 'mt5_management_events_total',
  help: 'Management events triggered by smart upgrade logic',
  labelNames: ['symbol', 'event'],  // event: 'flip_cluster' | 'concentration_close' | 'staged_partial' | 'smart_scalein_reject'
});

/**
 * P4.1: Entry zone classification distribution.
 */
export const entryZoneDistribution = new Counter({
  name: 'mt5_entry_zone_distribution_total',
  help: 'Distribution of entry zone classifications',
  labelNames: ['symbol', 'zone'],  // zone: 'PREMIUM' | 'EQ' | 'DISCOUNT'
});
