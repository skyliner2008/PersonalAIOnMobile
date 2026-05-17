import type { JournalRow } from './types.js';
import { asNumber, asString, round2, moneyPerPriceUnit } from './utils.js';

export type TradeOutcome = 'WIN' | 'LOSS' | 'BE';

/**
 * Compute R-multiple from journal data.
 * If priceOpen (broker actual fill) is provided, use it as base instead of
 * journal.entry to eliminate slippage bias in profitR calculation.
 */
export function profitToR(
  entry: number | null,
  sl: number | null,
  closePrice: number | null,
  side: string,
  priceOpen?: number | null
): number | null {
  if (entry === null || sl === null || closePrice === null) return null;
  const baseEntry = (priceOpen && priceOpen > 0) ? priceOpen : entry;
  const risk = Math.abs(baseEntry - sl);
  if (risk <= 0) return null;
  const reward = side === 'BUY' ? closePrice - baseEntry : baseEntry - closePrice;
  return round2(reward / risk);
}

export function profitCashToR(profit: number, entry: number | null, sl: number | null, volume: number, symbol: string): number | null {
  if (entry === null || sl === null || volume <= 0) return null;
  const riskPerUnit = Math.abs(entry - sl);
  if (riskPerUnit <= 0) return null;
  const multiplier = moneyPerPriceUnit(symbol);
  const totalRiskCash = riskPerUnit * multiplier * volume;
  if (totalRiskCash <= 0) return null;
  return round2(profit / totalRiskCash);
}

export function detectCloseReason(deal: Record<string, unknown> | undefined): string {
  if (!deal) return 'closed';
  const reasonRaw = deal.reason ?? deal.close_reason ?? deal.closeReason ?? deal.exit_reason ?? deal.exitReason;
  const reason = asString(reasonRaw).trim().toLowerCase();
  const comment = asString(deal.comment ?? deal.external_id ?? deal.externalId).trim().toLowerCase();
  const reasonText = JSON.stringify(deal).toLowerCase();
  const reasonCode = asNumber(reasonRaw, Number.NaN);
  const hasExplicitBreakEven =
    /\b(break[_ -]?even|breakeven|be_close|sl_to_be)\b/.test(reason) ||
    /\b(break[_ -]?even|breakeven|be_close|sl_to_be)\b/.test(comment) ||
    /\b(break[_ -]?even|breakeven|be_close|sl_to_be)\b/.test(reasonText);

  if (hasExplicitBreakEven) return 'BE';

  // MT5 numeric deal reasons: 4 = SL, 5 = TP.
  if (reasonCode === 5) return 'TP';
  if (reasonCode === 4) return 'SL';
  // TP detection
  if (
    reason === 'tp' ||
    reason.includes('take_profit') ||
    reason.includes('take profit') ||
    /\[tp\b|\btp\b/.test(comment) ||
    reasonText.includes('take_profit')
  ) return 'TP';
  // SL detection
  if (
    reason === 'sl' ||
    reason.includes('stop_loss') ||
    reason.includes('stop loss') ||
    /\[sl\b|\bsl\b/.test(comment) ||
    reasonText.includes('stop_loss')
  ) return 'SL';
  if (reasonText.includes('manual')) return 'MANUAL';
  if (reasonText.includes('partial')) return 'PARTIAL';
  return 'closed';
}

function hasExitMarker(deal: Record<string, unknown>): boolean {
  const entryValue = deal.entry ?? deal.deal_entry ?? deal.entry_type ?? deal.dealEntry ?? deal.type_entry ?? deal.typeEntry;
  const entry = asString(entryValue).toUpperCase();
  if (entry.includes('OUT') || entry.includes('CLOSE') || entry.includes('CLOSED') || entry.includes('EXIT')) return true;
  const entryCode = asNumber(entryValue, -1);
  return entryCode === 1 || entryCode === 3;
}

function hasCloseReasonMarker(deal: Record<string, unknown>): boolean {
  const reason = asString(deal.reason ?? deal.close_reason ?? deal.closeReason ?? deal.exit_reason ?? deal.exitReason).toUpperCase();
  return (
    reason.includes('TP') ||
    reason.includes('SL') ||
    reason.includes('STOP') ||
    reason.includes('TAKE') ||
    reason.includes('MANUAL') ||
    reason.includes('CLOSE') ||
    reason.includes('EXIT')
  );
}

function hasMeaningfulProfit(deal: Record<string, unknown>): boolean {
  return Math.abs(extractDealProfit(deal)) > 0.005;
}

export function extractDealProfit(deal: Record<string, unknown> | undefined): number {
  if (!deal) return 0;
  const grossCandidates = [
    deal.profit,
    deal.pl,
    deal.pnl,
    deal.p_l,
    deal.realized_profit,
    deal.realizedProfit,
  ];
  const swap = asNumber(deal.swap, 0);
  const commission = asNumber(deal.commission, 0);
  const fee = asNumber(deal.fee, 0);
  for (const candidate of grossCandidates) {
    const value = asNumber(candidate, Number.NaN);
    if (Number.isFinite(value)) return round2(value + swap + commission + fee);
  }
  const netCandidates = [
    deal.net,
    deal.net_profit,
    deal.netProfit,
    deal.profitNet,
    deal.netProfitRealized,
  ];
  for (const candidate of netCandidates) {
    const value = asNumber(candidate, Number.NaN);
    if (Number.isFinite(value)) return round2(value);
  }
  return 0;
}

export function extractDealClosePrice(deal: Record<string, unknown> | undefined): number | null {
  if (!deal) return null;
  for (const key of ['price', 'close', 'close_price', 'closePrice', 'price_close', 'priceClose', 'exit_price', 'exitPrice']) {
    const value = asNumber((deal as any)[key], Number.NaN);
    if (Number.isFinite(value) && value > 0) return value;
  }
  return null;
}

export function classifyOutcome(profit: number, profitR: number | null): TradeOutcome {
  const cashEpsilon = 0.005;
  const rEpsilon = 0.05;
  if (profit > cashEpsilon) return 'WIN';
  if (profit < -cashEpsilon) return 'LOSS';
  if (profitR !== null && profitR > rEpsilon) return 'WIN';
  if (profitR !== null && profitR < -rEpsilon) return 'LOSS';
  return 'BE';
}

export function matchHistoryDeal(row: JournalRow, history: Record<string, unknown>[]): Record<string, unknown> | undefined {
  const ticket = row.mt5Ticket;
  const matches = history.filter((deal) => {
    const candidates = [
      asNumber(deal.ticket, -1),
      asNumber(deal.deal, -1),
      asNumber(deal.deal_ticket, -1),
      asNumber(deal.dealTicket, -1),
      asNumber(deal.position, -1),
      asNumber(deal.position_ticket, -1),
      asNumber(deal.positionTicket, -1),
      asNumber(deal.position_id, -1),
      asNumber(deal.positionId, -1),
      asNumber(deal.positionID, -1),
      asNumber(deal.order, -1),
      asNumber(deal.order_ticket, -1),
      asNumber(deal.orderTicket, -1),
    ];
    if (ticket !== null && candidates.includes(ticket)) return true;
    const symbolMatches = asString(deal.symbol).toUpperCase() === row.symbol.toUpperCase();
    const commentMatches = JSON.stringify(deal).includes(row.decisionId);
    return symbolMatches && commentMatches;
  });
  if (matches.length === 0) return undefined;

  // MT5 history often contains both entry and exit deals for the same order/position.
  // Prefer the closing deal; otherwise a zero-profit entry deal makes learning label every close as BE.
  const newestFirst = matches.slice().reverse();
  const closingDeals = newestFirst.filter((deal) =>
    hasExitMarker(deal) || hasCloseReasonMarker(deal) || hasMeaningfulProfit(deal)
  );

  return (
    closingDeals.find((deal) => hasExitMarker(deal) && hasMeaningfulProfit(deal)) ||
    closingDeals.find((deal) => hasExitMarker(deal)) ||
    closingDeals.find((deal) => hasMeaningfulProfit(deal)) ||
    closingDeals.find((deal) => hasCloseReasonMarker(deal))
  );
}

export function aggregateDealProfits(row: JournalRow, history: Record<string, unknown>[]): number {
  const ticket = row.mt5Ticket;
  const matches = history.filter((deal) => {
    const candidates = [
      asNumber(deal.ticket, -1),
      asNumber(deal.deal, -1),
      asNumber(deal.deal_ticket, -1),
      asNumber(deal.position, -1),
      asNumber(deal.position_id, -1),
      asNumber(deal.order, -1),
    ];
    if (ticket !== null && candidates.includes(ticket)) return true;
    return false;
  });

  const closingDeals = matches.filter((deal) =>
    hasExitMarker(deal) || hasCloseReasonMarker(deal) || hasMeaningfulProfit(deal) || (asString(deal.entry) === '1') || (asNumber(deal.deal_entry, -1) === 1) || asString(deal.entry).toUpperCase().includes('OUT')
  );

  let totalProfit = 0;
  for (const deal of closingDeals) {
    totalProfit += extractDealProfit(deal);
  }
  
  // If no specific closing deals found but matches exist, sum all profits (MT5 returns net profit per deal)
  if (closingDeals.length === 0 && matches.length > 0) {
      for (const deal of matches) {
          totalProfit += extractDealProfit(deal);
      }
  }

  return round2(totalProfit);
}

