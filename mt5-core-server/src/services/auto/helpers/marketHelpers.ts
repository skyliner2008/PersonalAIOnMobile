import { callBridge } from '../../bridgeClient.js';
import { atLog, atWarn, asRecord, unwrapData } from '../utils.js';
import type { AutoTradingConfig } from '../types.js';

// เก็บ Cache สถานะความพร้อมเทรดของคู่เงินต่างๆ ในระดับโมดูลเพื่อลดการเรียก API ซ้ำซ้อน
const tradableCache = new Map<string, { at: number; tradable: boolean; reason: string }>();
const TRADABLE_TTL_MS = 60_000;

/**
 * ล้างข้อมูล Cache สถานะความพร้อมเทรดของคู่เงินทั้งหมด (ใช้สำหรับทดสอบหรือรีเซ็ต)
 */
export function clearTradableCache(): void {
  tradableCache.clear();
}

/**
 * ตรวจสอบว่าคู่เงิน (Symbol) นั้นๆ พร้อมสำหรับส่งออเดอร์หรือไม่
 * โดยดูจาก trade_mode และความสดใหม่ของ tick ล่าสุดจาก broker bridge
 */
export async function isSymbolTradable(symbol: string): Promise<{ tradable: boolean; reason: string }> {
  const upper = String(symbol || '').toUpperCase();
  if (!upper) return { tradable: false, reason: 'empty symbol' };
  const hit = tradableCache.get(upper);
  if (hit && Date.now() - hit.at < TRADABLE_TTL_MS) {
    return { tradable: hit.tradable, reason: hit.reason };
  }
  let tradable = true;
  let reason = 'tradable';
  try {
    const result = await callBridge('GET', ['/symbol_info'], { query: { symbol: upper }, timeoutMs: 5000 });
    const outer = asRecord(result.data);
    const inner = asRecord((outer as any).data ?? unwrapData(result.data));
    const info = Object.keys(inner).length > 0 ? inner : outer;
    const mode = Number((info as any).trade_mode ?? 4);
    if (mode === 0) {
      tradable = false;
      reason = 'trade_mode=0 (disabled)';
    } else {
      const tickT = Number((info as any).time ?? 0);
      if (tickT > 0) {
        const ageSec = (Date.now() / 1000) - tickT;
        if (ageSec > 600) {
          tradable = false;
          reason = `stale tick (${Math.round(ageSec)}s old)`;
        }
      }
    }
  } catch (err) {
    reason = 'bridge probe failed — assuming tradable';
  }
  tradableCache.set(upper, { at: Date.now(), tradable, reason });
  return { tradable, reason };
}

/**
 * ตรวจสอบว่าตลาดเปิดทำการอยู่หรือไม่ โดยดูจากคู่เงินใน watchlist อย่างน้อยหนึ่งตัวที่พร้อมเทรด
 */
export async function isMarketOpen(config: AutoTradingConfig): Promise<boolean> {
  const watchlist = config?.watchlist ?? [];
  if (watchlist.length === 0) return true;
  const results = await Promise.all(
    watchlist.map(sym => isSymbolTradable(sym).catch(() => ({ tradable: true, reason: '' }))),
  );
  return results.some(r => r.tradable);
}
