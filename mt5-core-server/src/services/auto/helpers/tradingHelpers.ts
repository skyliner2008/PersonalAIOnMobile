import type {
  PositionRow,
  PositionCluster,
  MarketRegime,
  Bias,
  StrategyType,
  CycleDecision,
} from '../types.js';
import { round2, clamp } from '../utils.js';
import { tradingExecutionService } from '../core/TradingExecutionService.js';
import { tradeManagementService } from '../core/TradeManagementService.js';

/**
 * จัดกลุ่มโพซิชันตามสัญลักษณ์ (Symbol) เพื่อวิเคราะห์ยอดรวมและทิศทางของพอร์ต
 */
export function buildPositionCluster(symbol: string, positions: PositionRow[]): PositionCluster {
  const clusterPositions = positions.filter((it) => it.symbol === symbol);
  const buyVolume = clusterPositions.filter((it) => it.side === 'BUY').reduce((sum, it) => sum + it.volume, 0);
  const sellVolume = clusterPositions.filter((it) => it.side === 'SELL').reduce((sum, it) => sum + it.volume, 0);
  const netVolume = round2(buyVolume - sellVolume);
  const totalProfit = round2(clusterPositions.reduce((sum, it) => sum + it.profit, 0));
  const avgPrice = clusterPositions.length > 0 
    ? clusterPositions.reduce((sum, it) => sum + it.priceOpen * it.volume, 0) / (buyVolume + sellVolume || 1)
    : 0;
  
  return {
    symbol,
    positions: clusterPositions,
    winningPositions: clusterPositions.filter((it) => it.profit > 0).sort((a, b) => b.profit - a.profit),
    losingPositions: clusterPositions.filter((it) => it.profit < 0).sort((a, b) => a.profit - b.profit),
    buyVolume: round2(buyVolume),
    sellVolume: round2(sellVolume),
    totalVolume: round2(buyVolume + sellVolume),
    netVolume,
    totalProfit,
    avgPrice: round2(avgPrice),
    netSide: netVolume > 0 ? 'BUY' : netVolume < 0 ? 'SELL' : 'FLAT',
    bias: netVolume > 0 ? 'BULL' : netVolume < 0 ? 'BEAR' : 'NEUTRAL',
  };
}

/**
 * ตรวจสอบความถูกต้องของอัตราส่วนขนาดออเดอร์ให้อยู่ในขอบเขตขั้นต่ำและสูงสุด
 */
export function clampFraction(input: number, min: number, max: number, fallback: number): number {
  if (!isFinite(input) || input <= 0) return fallback;
  return clamp(input, min, max);
}

/**
 * ดึงหมายเลข Ticket จากข้อมูลที่ได้จากการเปิดออเดอร์ผ่าน broker bridge
 */
export function extractTicket(input: unknown): number | null {
  return tradingExecutionService.extractTicket(input);
}

/**
 * แปลงหน่วยของ Timeframe (เช่น M5, H1) ไปเป็นมิลลิวินาที
 */
export function getTfMs(tf: string): number {
  const m = tf.match(/([MHD])(\d+)?/);
  if (!m) return 60000;
  const unit = m[1];
  const val = parseInt(m[2] || '1');
  if (unit === 'M') return val * 60000;
  if (unit === 'H') return val * 3600000;
  if (unit === 'D') return val * 86400000;
  return 60000;
}

/**
 * สร้างโครงสร้างผลลัพธ์แบบข้าม (SKIP) สำหรับรอบการวิเคราะห์บอท
 */
export function buildSkipDecision(
  symbol: string,
  regime: MarketRegime,
  bias: Bias,
  confluence: number,
  strategy: StrategyType,
  rationale: string,
  riskGate: string,
  translatedTh: string | null = null
): CycleDecision {
  return tradeManagementService.buildSkipDecision(symbol, regime, bias, confluence, strategy, rationale, riskGate, translatedTh);
}
