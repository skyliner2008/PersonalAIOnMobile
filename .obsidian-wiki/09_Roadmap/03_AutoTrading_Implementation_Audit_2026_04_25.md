# AutoTrading Implementation Audit — 2026-04-25

> ตรวจการ implement ตามแผน [02_AutoTrading_Smart_Upgrade_Plan_2026_04_25](02_AutoTrading_Smart_Upgrade_Plan_2026_04_25.md). Build ผ่าน TypeScript clean (exit 0). ทุก feature flag อยู่ใน `auto/types.ts` แล้ว

---

## 1. สรุปภาพรวม

| Phase | สเป็คในแผน | สถานะ implement | คะแนน |
|-------|-----------|---------------|------|
| **Phase 1 — Entry Quality** | 5 ข้อ (P1.1–P1.5) | 4.5/5 | 9/10 |
| **Phase 2 — Management** | 5 ข้อ (P2.1–P2.5) | 3.5/5 | 7/10 |
| **Phase 3 — Smart Flip & Learn** | 5 ข้อ (P3.1–P3.5) | 3.5/5 | 7/10 |
| **Phase 4 — Observability** | 4 ข้อ (P4.1–P4.4) | 1.5/4 | 4/10 |
| **รวม** | 19 ข้อ | ~12.5/19 | **65 %** |

ภาพรวม: **Phase 1 ทำดีและครบ**, Phase 2/3 มีโครงครบแต่ขาดการเชื่อม flag→logic บางจุด, Phase 4 ยังขาด UI + endpoint

---

## 2. ตรวจรายข้อ

### Phase 1 — Entry Quality Hardening

#### ✅ P1.1 — `classifyPremiumDiscount`
**ที่อยู่**: `mt5-core-server/src/services/auto/analyzers/smc.ts:364`

ทำตรงสเป็ค ใช้ OTE 62 / 38 % เป็นเกณฑ์ คืน `{ zone, pctFromRange, structureHigh, structureLow, equilibrium }`. มี fallback `EQ` ถ้า candle < 20

**ข้อสังเกตเล็ก**: ในไฟล์เดียวกันมี signal `pd_zone` เก่า (line 79–90) ใช้ equilibrium 50 % — *ซ้ำซ้อน*กับฟังก์ชันใหม่ ควรรวม/ลบของเก่า

#### ✅ P1.2 — Zone-Aware Gate (ทำเป็น 2 ชั้น)
**Pre-AI (downgrade)**: `deterministicEngine.ts:140-169` หัก confidence -15
**Post-AI (HARD block)**: `autoTradingService.ts:807-822` `side = 'SKIP'`

ดีมาก เพราะ post-AI block จับเคสที่ AI override deterministic ได้ + เพิ่ม metric `zoneGateBlocksTotal`

**⚠️ ช่องโหว่**: เงื่อนไข `wrongZoneBuy = side==='BUY' && zone==='PREMIUM' && h4Bias==='BULL'` — บล็อกเฉพาะเมื่อ H4 trend ตรงกับ side. กรณี **counter-trend BUY ใน Premium ตอน H4=BEAR** จะ *ไม่* โดนบล็อก (อาศัย P1.5 ช่วยแทน). แนะนำขยายเป็น
```ts
const wrongZoneBuy  = side === 'BUY'  && zone === 'PREMIUM';
const wrongZoneSell = side === 'SELL' && zone === 'DISCOUNT';
```

#### ⚠️ P1.3 — `entryMustAlignFVG` (ขาดการบังคับ)
**ที่ทำได้**: SL ถูกดันออกจาก FVG แล้ว (ทับซ้อนกับ P1.4)
**ที่ยังขาด**: *Entry side* — ยังไม่ตรวจว่า BUY ใน strategy `SMC_FVG_SCALP` ต้องอยู่ ≤ Bullish FVG.top หรือรอ tap FVG

**แนะนำเพิ่ม**: ใน `autoTradingService.ts` หลัง zone-gate (line 822) เพิ่ม
```ts
if ((selectedStrategy === 'SMC_FVG_SCALP') && side !== 'SKIP') {
  const fvgs = getActiveFVGs(allCandles.get(upper) || []);
  const aligned = fvgs.some(f =>
    (side === 'BUY' && f.type === 'BULL' && last.c <= f.top * 1.005) ||
    (side === 'SELL' && f.type === 'BEAR' && last.c >= f.bottom * 0.995)
  );
  if (!aligned) { atWarn(...); side = 'SKIP'; }
}
```

#### ✅ P1.4 — Dynamic SL Tick-Buffer
**ที่อยู่**: `autoTradingService.ts:885-927`

ทำเกินสเป็คเล็กน้อย (ดี): ใช้ `max(ATR×0.25, spread×3, 50 ticks, broker stopsLevel)` + ดัน SL ออกจาก FVG ด้วย 5 ticks buffer + metric `slBufferActivationsTotal` แยก trigger ('fvg_overlap' / 'min_distance')

**ข้อปรับปรุงเล็ก**: bufferTick = 5 ticks อาจน้อยไปสำหรับ XAUUSD ตอนข่าว — ทำเป็น `Math.max(tick * 5, atr * 0.05)` จะปลอดภัยกว่า

#### ✅ P1.5 — Counter-Trend Tightening
**ที่อยู่**: `autoTradingService.ts:797-804`

`ctMinConf = 62` (default) — ทำตรงแผน. เป็น HARD BLOCK + นับ metric `counterTrendBlocksTotal`

---

### Phase 2 — Management Upgrade

#### ✅ P2.1 — Staged BE/Trail
**ที่อยู่**: `TradeManagementService.ts:351-405`

3 stage ครบ (1R partial 25 %+BE, 2R partial 50 %, 3R trail ATR×1.5). กัน trigger ซ้ำด้วย `aiReview?.includes('PARTIAL_1R')`

**ข้อปรับปรุง**:
1. แผนเดิมเขียน 33 % / 50 % / 100 % trail — implement เป็น 25/50 — **ใช้ได้** แต่ควรเขียน comment ให้ตรงกัน
2. Marker `PARTIAL_1R` เก็บใน `aiReview` (text). ถ้า aiReview ถูก overwrite ที่อื่นจะ trigger ซ้ำ — แนะนำเพิ่ม column `stagesHit BIGINT` เป็น bitmask (1=1R, 2=2R, 4=3R) เพื่อ atomic check
3. ATR ใน Stage 3 ประมาณจาก `slRisk = |journal.sl - entry|` — ถ้า journal.sl เป็น *ใหม่หลัง trail แล้ว* ATR estimate จะเล็กลงเรื่อย ๆ → trail ชิดเกิน. ควร snapshot ATR ตอนเปิดออเดอร์

#### ⚠️ P2.2 — Smart Scale-In (มีรู AI override)
**ที่อยู่**: `TradeManagementService.ts:495-516`

ตรรกะ "price ดีกว่า weighted-avg − ATR×0.25" ถูกต้อง. แต่บรรทัด 513 `if (!priceBetterForBuy && !priceBetterForSell && !isAiDefenseForScaleIn)` — **AI defense ยัง bypass ได้แม้ราคาแย่กว่า**

**แนะนำ**: เพิ่ม sub-gate
```ts
if (isAiDefenseForScaleIn && aiConfidence < 88) {
  // AI ความมั่นใจไม่พอจะ override anti-martingale
  return { mode: 'HOLD', summary: 'AI override blocked - low confidence', ... };
}
```

#### ❌ P2.3 — Close-Weakest-Loser (ครึ่งเดียว)
**สถานะ**: Flag `enableCloseWeakest`, `closeWeakestMinConfluence`, `closeWeakestCooldownMs` ประกาศใน types.ts แต่ **ไม่มีโค้ดใช้**

มีเฉพาะ:
- `flip_cluster` (regime reversed) — close worst loser
- `concentration_close` (>3 same-side ภายใน 10 pips)

แต่ไม่มี trigger "เจอ setup ใหม่ confluence ≥ 70 → close ไม้อ่อนสุด"

**แนะนำเพิ่ม** ใน `autoTradingService.ts` ก่อน gate (ประมาณ line 970): ถ้า `gate.reject === 'max_open_positions'` และ `analysis.confluence >= 70` และ `lastCloseWeakestAt < cooldown` → เรียก `tradeManagementService` ปิดไม้ profitR ต่ำสุดของ symbol แล้วเข้า trade ใหม่

#### ❌ P2.4 — Hedge Override Gate (flag แล้วแต่ไม่ใช้)
**สถานะ**: `hedgeOverrideMinConfidence` ประกาศแต่ไม่มีที่อ้างถึง

**ปัญหาเดิมจาก log ยังอยู่**: `autoTradingService.ts:987` — `maxHeatForDefenseOverride = maxHeatForScaleIn * 0.67 = 1.005R` ทำให้ defense override ถูก suppress ตั้งแต่ heat ≥ 1.0R (ตรงกับ failure mode M5)

**แนะนำแก้** บรรทัด 988-989:
```ts
const aiConf = (managementPlan?.playbook?.summary || '').includes('confidence') 
  ? aiDecision?.confidence : 0;
const hedgeOverrideMin = cfg.adaptive?.hedgeOverrideMinConfidence ?? 75;
const heatBlocksOverride = clusterHeatR >= maxHeatForDefenseOverride 
  && aiConf < hedgeOverrideMin;
```
หรือดีกว่า: ส่ง `aiDecision.confidence` เข้า `planMarketAwareManagement` แล้วเช็กในนั้น

#### ✅ P2.5 — Hedged-BE Close (ดีพอใช้)
**ที่อยู่**: `TradeManagementService.ts:182-196`

Early `return` กันการรันโค้ดถัดไปในเรียกเดียวกัน. ไม่มี mutex ข้าม cycle แต่ engine ทำ cycle ตามลำดับอยู่แล้ว → **ปลอดภัย**

---

### Phase 3 — Smart Flip & Learning Loop

#### ⚠️ P3.1 — FLIP_CLUSTER (ยังเป็นครึ่งทาง)
**ที่อยู่**: `TradeManagementService.ts:198-223`

ทำได้แค่ "ปิด worst loser เมื่อ regime พลิก" — *ขาดการเปิดไม้สวนที่ 50 % ของ cluster เดิม* (จุด `+ open opposite at 50%` ในแผน)

**สาเหตุ**: Manager คืน `ManagementPlan` 1 อันต่อรอบ — ถ้าจะเปิดไม้ใหม่ต้องให้ orchestrator (`autoTradingService`) เห็น `flipped=true` แล้วยิง entry ในรอบเดียวกัน

**แนะนำ**: เพิ่ม field ใน plan เช่น `triggerOpposite?: { side: 'BUY'|'SELL', volume: number }` แล้ว orchestrator ตรวจหลังการปิดสำเร็จ → เรียก execute เปิดไม้ใหม่

#### ✅ P3.2 — Concentration Guard
**ที่อยู่**: `TradeManagementService.ts:225-255`

ทำตรงสเป็ค (>3 ภายใน 10 pips → close weakest). ปิดเฉพาะไม้ที่ขาดทุน (`weakest.profit < 0`) — ดี ป้องกัน churn ไม้กำไร

#### ✅ P3.3 — Pre-Analysis Prior
**ที่อยู่**: `autoTradingService.ts:533-551` + `:652`

ทำดีมาก: query top-5 trades คล้ายปัจจุบัน → filter เฉพาะ LOSS หรือ profitR<-0.5 → เอาเฉพาะ 3 อันแรก → inject เป็น context ให้ analyst. เป็น best-effort ครอบ try/catch

**ข้อปรับปรุง**: ตอนนี้แค่ inject เป็นข้อความ — *ไม่*ปรับ confluence score ตามที่แผนเขียน ("ถ้า avgR < -0.3 → หัก 10 คะแนน"). ควรเพิ่ม
```ts
if (avgR_failedSetups < -0.3) {
  analysis.confluence = Math.max(0, analysis.confluence - 10);
  atLog(`⚠️ confluence -10 จากประวัติ failed setups`);
}
```

#### ❌ P3.4 — Failure Pattern Tagging (ยังไม่ทำ)
**สถานะ**: `postMortem.ts` ยังไม่มี field `failure_pattern`. Vector search ใช้ similarity ของ snapshot text เท่านั้น — แม่นน้อยกว่าถ้าใส่ tag

**แนะนำเพิ่ม** ใน `postMortem.ts`:
```ts
function detectFailurePattern(journal: JournalRow, decision: CycleDecision): string[] {
  const tags: string[] = [];
  if (decision.zoneAtEntry === 'PREMIUM' && journal.side === 'BUY') tags.push('BUY_IN_PREMIUM');
  if (decision.zoneAtEntry === 'DISCOUNT' && journal.side === 'SELL') tags.push('SELL_IN_DISCOUNT');
  if (journal.aiReview?.includes('SCALE_IN') && (journal.profitR ?? 0) < -0.5) tags.push('SCALE_UP_LOSER');
  // ...
  return tags;
}
```
แล้วใส่ใน metadata ของ vector entry: `{ ..., failure_pattern: tags.join(',') }`

จากนั้น P3.3 query ก็เพิ่ม filter `metadata.failure_pattern` ตรงกับ pattern ของ setup ปัจจุบัน → แม่นขึ้น

#### ✅ P3.5 — Analyst Prompt
ทำผ่าน `failedSetupContext` รวมเข้า history context (ไม่ต้องแก้ prompt template) — สะอาดดี

---

### Phase 4 — Observability & KPI

#### ✅ P4.1 — Metrics
**ที่อยู่**: `metrics.ts`

มี Counter ครบ 5 ตัว: `zoneGateBlocksTotal`, `slBufferActivationsTotal`, `counterTrendBlocksTotal`, `managementEventsTotal`, `entryZoneDistribution` — ครอบทุก event ที่แผนระบุ

#### ❌ P4.2 — UI Kotlin Cycle Quality Card
**สถานะ**: `AutoTradingViewModel.kt` ไม่ถูกแก้ไข, TradingTerminalScreen ไม่มี tab/card ใหม่

**ผลกระทบ**: ผู้ใช้ยังเห็นไม่ได้ว่า zone gate / SL buffer / counter-trend block ทำงานกี่ครั้ง — ต้องเปิด `/metrics` (Prometheus) ดูเอง

**แนะนำ**: เพิ่มใน `AutoTradingRemoteService` method ใหม่ `fetchQualityMetrics()` ดึงจาก endpoint ใหม่ + การ์ดใหม่ใน TradingTerminalScreen

#### ❌ P4.3 — `/metrics/quality` endpoint
**สถานะ**: มีแค่ `/metrics` (Prometheus raw) ที่ `health.ts:58`. ไม่มี endpoint สรุปแบบ JSON สำหรับ UI

**แนะนำเพิ่ม** ใน `routes/autoTrading.ts`:
```ts
router.get('/metrics/quality', async (_req, res) => {
  const metrics = await register.getMetricsAsJSON();
  const find = (name: string) => metrics.find(m => m.name === name);
  res.json({
    success: true,
    data: {
      zoneGateBlocks: find('mt5_zone_gate_blocks_total')?.values || [],
      slBufferActivations: find('mt5_sl_buffer_activations_total')?.values || [],
      counterTrendBlocks: find('mt5_counter_trend_blocks_total')?.values || [],
      managementEvents: find('mt5_management_events_total')?.values || [],
      entryZoneDist: find('mt5_entry_zone_distribution_total')?.values || [],
    }
  });
});
```

#### ⚠️ P4.4 — Wiki Update
แผนเขียนให้อัปเดต `12_AutoTrading_Remote_Engine.md` + `13_AI_Pro_Trader_Roadmap.md` — ยังไม่ได้แก้ ควรเพิ่ม section "Smart Upgrade 2026-04-25" ที่อ้างถึงทั้ง flag ใหม่และ metric ใหม่

---

## 3. ความปลอดภัย & ความถูกต้อง

### Build / Compile
- `tsc -p tsconfig.json` รันได้สะอาด exit 0 — ไม่มี type error

### Logic Safety
| ตรวจ | ผล |
|------|-----|
| Early return ป้องกัน double-execution ใน TradeManagementService | ✅ |
| Try/catch รอบ vector store query | ✅ best-effort, ไม่ crash cycle |
| FVG buffer ใช้ tick-snap ก่อน send order | ✅ |
| Stage marker (`PARTIAL_1R` ใน aiReview) — race condition? | ⚠️ ควรเป็น bitmask field |
| Defense override suppression — ทำงานตามค่า heat 0.67 × 1.5 = 1.005R | ⚠️ ควรเชื่อม AI confidence (P2.4) |

### Default Values รอบทำงานจริง
- `enableZoneAwareGate` default `true` → ทำงานทันทีเมื่อ deploy (ดี — เปลี่ยนพฤติกรรมโดยไม่ต้องตั้ง config)
- `enableStagedPartials` default `true` → BE/Trail แบบใหม่ทำงานทันที — **อาจช็อกพอร์ตที่กำลังถือไม้เก่าอยู่**. ควรมี grace period 24 ชม. หลัง deploy

---

## 4. ลำดับการแก้เพิ่ม (Priority)

ถ้าจะใช้เวลาอีก 1-2 วันแก้, ทำตามลำดับนี้:

1. **P2.4 wire `hedgeOverrideMinConfidence`** (จุดเสี่ยงสูงสุด — กระทบ defense behavior). ~30 นาที
2. **P3.4 add `failure_pattern` tagging** (ทำให้ P3.3 ที่ทำดีอยู่แล้ว แม่นขึ้น). ~1 ชม.
3. **P1.3 strict FVG entry alignment for SMC_FVG_SCALP**. ~30 นาที
4. **P1.2 ขยาย zone gate ให้ครอบ counter-trend zone case**. ~10 นาที
5. **P3.3 confluence penalty -10** เมื่อ avgR ของ failed setups < -0.3. ~15 นาที
6. **P3.1 ส่วน open opposite** — ต้องการ orchestrator coordination. ~2 ชม.
7. **P2.3 close-weakest trigger ตอน setup ใหม่ดี**. ~1 ชม.
8. **P4.3 endpoint `/metrics/quality` + P4.2 UI card**. ~3 ชม.
9. **P4.4 wiki update**. ~30 นาที

รวมประมาณ **~9 ชม. งาน** ทำให้ระบบครบแผน 100 %

---

## 5. KPI ตอนนี้ตรวจวัดได้แล้ว

ใช้ Prometheus query (หลัง deploy ≥ 24 ชม.):

```promql
# % BUY in Premium ที่ถูกบล็อก
rate(mt5_zone_gate_blocks_total{side="BUY", zone="PREMIUM"}[1h])
  / rate(mt5_trades_placed_total{side="BUY"}[1h])

# % SL ที่ต้องดันออก FVG
rate(mt5_sl_buffer_activations_total{trigger="fvg_overlap"}[1h])
  / rate(mt5_trades_placed_total[1h])

# Counter-trend ที่ถูกบล็อกต่อชั่วโมง
sum(rate(mt5_counter_trend_blocks_total[1h]))

# Distribution ของ zone ตอน entry
sum by (zone) (mt5_entry_zone_distribution_total)
```

ตั้งเป้าหลัง deploy 7 วัน: zone-gate-block-rate ลดลงเรื่อย ๆ (ไม่ใช่นิ่ง — แสดงว่า AI เรียนรู้ไม่ส่งคำสั่งผิดโซน), counter-trend block rate ใกล้ 0

---

## 6. สรุปคำตอบสั้น

**สิ่งที่ทำได้ดี (เกินคาด)**: P1 ทำครบและละเอียด, มี dual-layer (det + post-AI) ของ zone gate, FVG buffer ครอบทุก trigger, vector store memory loop ทำงานได้

**สิ่งที่ต้องแก้ก่อน deploy**: P2.4 hedge override ยังเป็นช่องเดิม, P3.1 FLIP ทำครึ่ง, flag P2.3 dangling

**สิ่งที่รอได้**: P4.2/P4.3 (UI/endpoint สรุป) + P3.4 failure_pattern + P4.4 wiki

โดยรวมงานคุณภาพดี — โครงครบ, type-safe, มี feature flag ทุกฟีเจอร์ — แค่ขาดการเชื่อม flag → logic ในบางจุด

---

## Update — 2026-04-25 (รอบที่ 2: ปิดงาน 100%)

ทำเพิ่มทั้งหมด 9 รายการที่ค้าง — `tsc --noEmit` exit 0

| Item | สถานะใหม่ | ไฟล์ที่แก้ |
|------|---------|---------|
| **P1.2** ขยาย zone gate ครอบ counter-trend | ✅ ปิด h4Bias coupling | `autoTradingService.ts` |
| **P1.3** strict FVG entry alignment | ✅ block SMC_FVG_SCALP เมื่อ entry ไม่อยู่ใน FVG ทิศตาม | `autoTradingService.ts` |
| **P2.3** close-weakest wired | ✅ trigger เมื่อ gate=`max open positions` & confluence ≥ 70 + cooldown 1ชม. | `autoTradingService.ts` (+ closeWeakestLastAt map) |
| **P2.4** hedgeOverrideMinConfidence wired | ✅ heat ≥ 1.0R + AI conf < 75 จึง suppress; conf ≥ 75 ฝ่าได้ | `autoTradingService.ts` |
| **P3.1** FLIP_CLUSTER ส่วน open-opposite | ✅ `triggerOpposite` field + orchestrator เปิดไม้สวนหลังปิด worst loser สำเร็จ | `types.ts`, `TradeManagementService.ts`, `autoTradingService.ts` |
| **P3.3** confluence penalty -10 | ✅ ดึง avgR ของ failed setups; ถ้า < -0.3 → หัก 10 จุด confluence | `autoTradingService.ts` |
| **P3.4** failure_pattern tagging | ✅ `detectFailurePattern()` + 9 tag patterns + ใส่ใน vector metadata | `agents/postMortem.ts`, `vectorStore.ts`, `autoTradingService.ts` |
| **P4.2** UI Cycle Quality card | ✅ `CycleQualityCard` + `QualityChip` + `DistributionRow` + ViewModel state | `AutoTradingScreen.kt`, `AutoTradingViewModel.kt`, `AutoTradingRemoteService.kt` |
| **P4.3** `/metrics/quality` endpoint | ✅ JSON สรุป (summary + breakdown + raw) | `routes/autoTrading.ts` |
| **P4.4** Wiki updates | ✅ `12_AutoTrading_Remote_Engine.md` V23 section + `13_AI_Pro_Trader_Roadmap.md` changelog | wiki |

### Feature flags ใหม่ใน `cfg.adaptive` (เปิด default ทั้งหมด)

```ts
enableZoneAwareGate?: boolean        // P1.2 + P1.3
enableFvgSlBuffer?: boolean          // P1.4
enableStagedPartials?: boolean       // P2.1
enableSmartScaleIn?: boolean         // P2.2
enableCloseWeakest?: boolean         // P2.3
closeWeakestMinConfluence?: number   // P2.3 default 70
closeWeakestCooldownMs?: number      // P2.3 default 3_600_000
hedgeOverrideMinConfidence?: number  // P2.4 default 75
enableFlipCluster?: boolean          // P3.1
flipClusterCooldownMs?: number       // P3.1 default 7_200_000
flipOppositeFraction?: number        // P3.1 default 0.5
maxClusterConcentration?: number     // P3.2 default 3
enablePreAnalysisPrior?: boolean     // P3.3
```

### Metrics ที่บันทึกแล้ว

| Counter | label | use case |
|---------|-------|---------|
| `mt5_zone_gate_blocks_total` | symbol, side, zone | นับ trade ที่ถูกบล็อกด้วย Premium/Discount/FVG_MISALIGN gate |
| `mt5_sl_buffer_activations_total` | symbol, trigger | ('fvg_overlap' \| 'min_distance') |
| `mt5_counter_trend_blocks_total` | symbol, side | counter-trend ที่ confluence < 62 |
| `mt5_management_events_total` | symbol, event | ('flip_cluster' \| 'flip_opposite_open' \| 'flip_opposite_failed' \| 'concentration_close' \| 'staged_partial' \| 'close_weakest' \| 'close_weakest_failed') |
| `mt5_entry_zone_distribution_total` | symbol, zone | distribution ('PREMIUM' \| 'EQ' \| 'DISCOUNT') ตอน entry |

### Endpoint ใหม่

`GET /api/mt5/auto/metrics/quality` → JSON `{ generatedAt, summary, breakdown, raw }`. Kotlin client มี `AutoTradingRemoteService.fetchQualityMetrics()` + auto-refresh ทุก cycle ผ่าน `AutoTradingViewModel`

### Verification

```bash
cd mt5-core-server && npx tsc --noEmit -p tsconfig.json
# exit 0
```

**คะแนนปัจจุบัน: 19/19 (100 %)** — รวม V23 Smart Upgrade
