// Smoke test V25 Phase A — pure unit (no bridge, no network)
import { TickBuffer, TickBufferRegistry } from './src/services/auto/v25/TickBuffer.ts';
import { v25EventBus } from './src/services/auto/v25/V25EventBus.ts';
import { BarCloseDetector } from './src/services/auto/v25/BarCloseEventBus.ts';
import { M1MicroWallBuilder } from './src/services/auto/v25/M1MicroWallBuilder.ts';
import { resolveSymbolProfile } from './src/services/auto/v25/types.ts';

let pass = 0, fail = 0;
const t = (name, cond) => { if (cond) { pass++; console.log('OK', name); } else { fail++; console.log('FAIL', name); } };

// 1. TickBuffer
const tb = new TickBuffer('XBTUSD', 5);
t('empty size', tb.size() === 0);
t('empty last null', tb.last() === null);
for (let i = 0; i < 7; i++) {
  tb.push({ symbol:'XBTUSD', srcTs: i*1000, recvTs: i*1000+50, bid: 100+i, ask: 100+i+0.1, mid: 100+i+0.05, spread: 0.1 });
}
t('capacity capped', tb.size() === 5);
t('newest mid 106.05', Math.abs(tb.last().mid - 106.05) < 0.001);
t('lastN order asc', tb.lastN(3).map(x => x.mid).join(',') === '104.05,105.05,106.05');
t('highN', tb.highN(5) === 106.05);
t('lowN', tb.lowN(5) === 102.05);

// 2. Registry
const reg = new TickBufferRegistry(10);
const a = reg.get('XAUUSD');
const b = reg.get('xauusd'); // case-insensitive
t('registry case-insensitive', a === b);

// 3. Profile
const p1 = resolveSymbolProfile('XBTUSD');
t('XBT profile', p1.approachAtrMul === 1.5 && p1.minRrr === 1.5);
const p2 = resolveSymbolProfile('EURUSD');
t('default fallback', p2.symbol === 'DEFAULT' && p2.minWallStars === 3);

// 4. EventBus
let evtCount = 0;
const off = v25EventBus.on('V25_TICK', () => { evtCount++; });
await v25EventBus.emitTick('XBTUSD', { symbol:'XBTUSD', srcTs:1, recvTs:2, bid:1, ask:1, mid:1, spread:0 });
t('event delivered', evtCount === 1);
off();

// 5. BarClose detector wiring (no start)
const bcd = new BarCloseDetector(['M1', 'M5']);
bcd.trackSymbol('XBTUSD');
const stbcd = bcd.status();
t('bcd symbols', stbcd.symbols.includes('XBTUSD'));

// 6. MicroWallBuilder wiring
const mwb = new M1MicroWallBuilder({ intervalMs: 5000, windowMs: 60000, minTickCount: 5 });
mwb.trackSymbol('XBTUSD');
t('mwb status', mwb.status().symbols.includes('XBTUSD'));

console.log(`\n${pass}/${pass+fail} passed`);
process.exit(fail > 0 ? 1 : 0);
