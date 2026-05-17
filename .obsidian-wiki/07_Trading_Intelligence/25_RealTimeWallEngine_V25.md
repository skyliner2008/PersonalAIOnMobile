# V25.0 — Real-Time Wall Engine (Tick-Level Watchtower)

> **Version:** V25.0
> **Date:** 2026-05-10
> **Status:** 🟢 **Phase A + B + C + D ALL IMPLEMENTED (shadow mode, opt-in via `V25_SHADOW=true`)** · ✅ Phase D (LLM-gating + V24 Sunset) Completed!
> **Replaces:** V24.0 FadeTheLevel แบบ cycle-based → V25 ทำงาน tick-level + bar-close event-driven
> **Driver:** Log 2026-05-09 14:09–14:29 (cycle #9563–#9576): SKIP ติด loop เพราะ entry กลางช่อง 80113↔80483, SL ไกล, TP ถูก cap → RRR<0.5 ทุก cycle

## V25.4 — M1 Wall Gap Analysis Fixes (2026-05-11)

> **Driver:** Gap Analysis ระหว่างแนวคิด PriceMap M1 ของ User กับ V25 Code เดิมที่ ReactionDetector ดูแค่ Candle Pattern และละเลย M1 Wall

### 3 Phases Implemented

1. **Phase 1: M1 Wall Feed (แก้ไข `REACT` rate ต่ำ)**
   - Wire `M1MicroWallBuilder` เข้า `WallProximityIndex` ตรงๆ (สร้าง `M1_MICRO` wall เสมือนแบบ 1★)
   - ปรับ `ReactionDetector` ให้ฉลาดขึ้น (`detectWallAware`) หากชน Wall 4★+ และทิศทางตรง HTF Bias จะผ่อนปรนเกณฑ์ Reversal
   - หากเจอ Wall แบบ M1_MICRO คั่นกลางก่อนถึง Wall หลัก ระบบจะยอมรับจุดนั้นเป็น Wall เช่นกัน

2. **Phase 2: Wall Break & Reform Detection**
   - **M1 Hot-Refresh:** ปกติ PriceMap จะรีเฟรชเฉพาะ M5/M15 แต่ตอนนี้หาก State Machine อยู่ในโหมด `REACT` หรือ `BREAK_OUT` จะเร่งรีเฟรช PriceMap ทุกๆ ครั้งที่ **M1 ปิดแท่ง**
   - **BreakoutWallPromoter:** เมื่อเกิด Breakout ระบบจะสร้าง Flipped Wall ทันที (Resistance → Support) เพื่อรอสัญญาณ `RETEST`
   - **Wall Strength Decay:** เพิ่มกลไก "บั่นทอนความแข็งแรง" หากราคาชน Wall เกิน 1 ครั้ง (เข้าสู่ REACT state) จำนวน ★ จะลดลง

3. **Phase 3: Dynamic Entry Point**
   - เพิ่มตัวแปร `touchCount` ติดตามว่า Wall นี้ถูกทดสอบมาแล้วกี่ครั้ง
   - แก้ `PB1_WallTouchScalp` ให้**ยอมรับ 1★ Wall ได้** หาก Wall นั้นเป็น `M1_MICRO` และมี `touchCount >= 2` (การเด้งย้ำๆ ที่ M1 ถือเป็นจุดเข้าที่ valid แม้ไม่ถึง Wall ใหญ่)

---

## Implementation Status (2026-05-11)

| Phase | Module | LOC | Status |
|-------|--------|----:|:------:|
| A | `services/auto/v25/types.ts` | 160 | ✅ |
| A | `services/auto/v25/TickBuffer.ts` + Registry | 161 | ✅ |
| A | `services/auto/v25/V25EventBus.ts` (dedicated bus) | 87 | ✅ |
| A | `services/auto/v25/TickGateway.ts` (poll `/symbol_info`, dedupe, stale guard) | 174 | ✅ |
| A | `services/auto/v25/BarCloseEventBus.ts` (clock-aligned per TF) | 129 | ✅ |
| A | `services/auto/v25/M1MicroWallBuilder.ts` (rolling micro-pivot) | 132 | ✅ |
| B | `services/auto/v25/state/PriceMapSnapshotProvider.ts` (read V21 PriceMap) | 51 | ✅ |
| B | `services/auto/v25/state/WallProximityIndex.ts` (sorted+binary search+micro walls+decay) | 210 | ✅ |
| B | `services/auto/v25/state/FvgFreshTracker.ts` (drop-on-fill) | 95 | ✅ |
| B | `services/auto/v25/state/ReactionDetector.ts` (M1 engulf/pinbar/FVG-fill+wall-aware) | 108 | ✅ |
| B | `services/auto/v25/state/WallStateMachine.ts` (7-state FSM × side + decay + promoter) | 330 | ✅ |
| B | `services/auto/v25/state/BreakoutWallPromoter.ts` (Flip broken walls) | 18 | ✅ |
| C | `services/auto/v25/playbooks/types.ts` + `PB1_WallTouchScalp.ts` | 166 | ✅ |
| D | `services/auto/v25/playbooks/PB2_FvgFillReversal.ts` | 77 | ✅ |
| D | `services/auto/v25/playbooks/PB3_SweepReversal.ts` | 77 | ✅ |
| D | `services/auto/v25/playbooks/PB4_RangeFade.ts` | 72 | ✅ |
| D | `services/auto/v25/playbooks/PB5_BreakoutRetest.ts` | 73 | ✅ |
| D | `services/auto/v25/playbooks/index.ts` (PlaybookSelector cascade) | 215 | ✅ |
| A–D | `services/auto/v25/index.ts` (`bootstrapV25Shadow()` + health) | 204 | ✅ |
| A–D | `routes/v25.ts` (health/ticks/state/candidates) | 54 | ✅ |
| **Total** | | **~2,600** | |
| ✅ | LLM gating (slTpAgent only on REACT/CONFIRM) + ExecutionService handoff (`shadowOnly:false`) | — | ✅ |
| ✅ | V24 sunset toggle (`enableV25Only`) | — | ✅ |

**Compile check:** all V25 files pass strict tsc (target ES2022/NodeNext, --strict). Full project `tsc --noEmit` shows zero V25-related errors (4 pre-existing V24 errors unrelated). Verified 2026-05-11.

**Activation:** set `V25_SHADOW=true` in env. Default symbols `XBTUSD,XAUUSD`. Override via `V25_SYMBOLS` (csv). Tick poll interval `V25_TICK_POLL_MS=1000`. Stale guard `V25_STALE_MS=5000`. Verbose ticks `V25_VERBOSE_TICK=true`.

**Diagnostics:**
- `GET /api/v25/health` — counters, gateway/state/playbook stats
- `GET /api/v25/ticks/:symbol?limit=N` — raw tick buffer
- `GET /api/v25/state/:symbol` — wall proximity, fresh FVGs, state machine, recent candidates
- `GET /api/v25/candidates/:symbol` — last 20 trade plans + last 10 skip reasons

## V25.1 — Real-Time Coordination Fixes (2026-05-10)

> **Driver:** Log XBTUSD 13:05–13:21 — V25 ABOVE/BELOW state churn ทุก 1–3s, PB1 SKIP ติดลูป, slTpAgent คืนค่า SL/TP ผิดฝั่งตลอด, AI pipeline 168s ต่อรอบทั้งที่ไม่มี wall touch จริง

### 6 Patches Applied

| # | Module | ปัญหาเดิม (จาก log) | แก้ |
|---|--------|---------------------|-----|
| 1 | `WallStateMachine.ts` | APPROACH↔REACT↔IDLE ทุก 1-3s รอบ wall เดิม → log noise + กระตุ้น CONFIRM ผิดเวลา | (a) REACT ไม่ fall-back IDLE จาก tick distance อีกแล้ว (exit เฉพาะผ่าน BREAK_OUT หรือ M1 bar-close CONFIRM); (b) APPROACH→IDLE ใช้ hysteresis 2.0× (เดิม 1.5×) + dwell ≥ 1.5s; (c) wall identity swap: ถ้า wall ใหม่ "ดาวน้อยกว่า" และยังอยู่ใน 2× reactZone ของ wall เดิม → คงตัวเดิม; (d) churn telemetry: เตือนเมื่อ >6 transitions/10s |
| 2 | `WallProximityIndex.ts` | `query()` คืน wall ใกล้สุด — มักเป็น 1★ wall ขณะที่ 5★ wall อยู่ห่าง 30-50 pts → PB1 skip "stars 1 < min 2" | เพิ่ม `queryStrongest(price, radius, minStars)` — สแกน wall ทุกตัวภายใน radius และคืน "ดาวเยอะสุด" |
| 3 | `playbooks/index.ts` (PlaybookSelector) | ใช้ `query()` อย่างเดียว → ติดลูป `PB1:wall stars 1 < min 2` | ถ้า nearest wall < minWallStars → fallback ไป `queryStrongest()` ภายใน 2.5× reactZone — ใช้ wall ที่แข็งกว่า ระบบถึงจะมี trade plan |
| 4 | `slTpAnalyst.ts` (call site `autoTradingService.ts:1845`) | ส่ง `biasSide` (จาก analysis.bias) แทน `side` (สุดท้าย) — bias=BEAR + AI override BUY → smcSl อยู่ "เหนือ entry" → discarded + LLM token เปล่า | เลือก `slTpSide = side ?? biasSide` — pass FINAL side ให้ slTpAgent + log warning เมื่อ side != biasSide |
| 5 | `autoTradingService.ts:1820` (rawSl/rawTp ATR fallback) | bug เดียวกันกับ #4 (ATR fallback ใช้ biasSide) | เพิ่ม `rawDirSide = side ?? biasSide` |
| 6 | `autoTradingService.ts:1106` (AI pipeline gate) | รัน Analyst+RiskOfficer+Execution (3×LLM ~168s) แม้ไม่มี wall ใน REACT/CONFIRM = mid-channel | เพิ่ม **V25 Gate**: ถ้า `wallStateMachine.status()` ของ symbol ไม่มี state=REACT/CONFIRM → ตั้ง aiDecision=SKIP ทันที (`_source: 'v25_gate'`) **ข้าม AI pipeline** ประหยัด ~150s + token (ใช้ flag `adaptive.v25.skipMidChannelLLM` เดิม) |
| 7 | `autoTradingService.ts:1803` (bias contradiction tag) | log `Final Side=BUY \| Bias=BEAR` ไม่มีคำเตือน | เพิ่ม `atWarn(⚠️ COUNTER-TREND override)` + tag `[COUNTER_TREND]` ท้าย Strategy |

### V25.1 รักษา Hard Rules ไว้ครบ
- **R1 (no mid-channel):** patch #6 บังคับ — AI ไม่ทำงานเลยถ้าไม่มี wall touch
- **R2 (SL ≤ 1×ATR):** ไม่แตะ — กลไกเดิมยัง enforce
- **R3 (TP at opposite wall):** ไม่แตะ — PB1 ใช้ `oppositeWall.price - tickSize - spread` เดิม
- **R4 (compute on bar-close):** patch #1 ทำให้ REACT ไม่ขยับเอง = state stable ระหว่าง bar
- **R5 (LLM only on REACT):** patch #6 = ทำให้ R5 มีผลจริง (ก่อนหน้านี้แค่ slTpAgent ที่ gate)

### Verification Plan
1. Restart server กับ patch ทั้งหมด — ดูล็อกอย่างน้อย 30 นาที
2. ตรวจ counter ใน `/api/v25/health`: `wallStateMachine.recentTransitions` ลดลง
3. ตรวจ `/api/v25/candidates/XBTUSD`: เมื่อราคา touch 5★ wall (80664.78) ที่ใหม่ — selector ควรออก plan PB1 (ไม่ใช่ skip "stars 1 < min 2")
4. กรณี mid-channel: log ใหม่ควรเห็น `[V25 Gate] AI pipeline skipped` แทนที่จะเป็น `Analyst (MiniMax-M2.7) finished in 78.2s`
5. ตรวจ AI override: เมื่อ Final Side ตรงข้าม Bias → ต้องเห็น `⚠️ COUNTER-TREND override`

### Rollback
- Patch #6 ปิดได้ทันทีผ่าน `cfg.adaptive.v25.skipMidChannelLLM = false` (แก้ผ่าน UI/Config)
- Patches #1–#5 ฝังในโค้ด — rollback ผ่าน `git revert` ของ commit V25.1

## V25.3 — AI Bias Alignment Gate (2026-05-11)

> **Driver:** Cycle #10003/#10004 — AI Pipeline (Deterministic + MiniMax Analyst + Risk Officer + Execution Trader) ทุกตัวสั่ง **BUY** ใน VOLATILE_BREAKOUT regime แต่ V25 PlaybookSelector กลับยิง **SELL** สวนเทรนด์ ดักจับ "wall rejection" ที่เป็น breakout จริงๆ ส่งผลให้เปิด SELL ผิดตำแหน่ง

### สาเหตุ

PlaybookSelector ทำงานแบบ **wall mechanics ล้วนๆ** — `ev.side === 'ABOVE'` → สั่ง SELL (คาดว่าราคาจะเด้งลงจาก resistance) โดย **ไม่เคยตรวจสอบ AI Pipeline bias** เลย ทำให้:
1. AI Stack สั่ง BUY ใน breakout ขาขึ้น (Confluence=65.7, Fitness=84.6)
2. V25 เห็นราคาชน wall 81434 (1★) แล้ว swap ไปใช้ wall 81474 (2★) → ยิง SELL
3. ผลลัพธ์: **SELL สวนเทรนด์** ในภาวะ VOLATILE_BREAKOUT

### แก้ไข — AI Bias Gate

เพิ่ม gate ใน `playbooks/index.ts` ก่อน Playbook cascade:

```ts
const lastDecisions = persistenceService.loadLastDecisions();
const lastForSymbol = lastDecisions.find(d => d.symbol === upper);
if (lastForSymbol) {
  const isBiasConflict =
    (side === 'SELL' && (aiBias === 'BULL' || aiSide === 'BUY')) ||
    (side === 'BUY'  && (aiBias === 'BEAR' || aiSide === 'SELL'));
  if (isBiasConflict) → SKIP + log reason
}
```

**กฎ:** ถ้า V25 จะยิง SELL แต่ AI Pipeline ล่าสุดบอก BUY (หรือ Bias=BULL) → **บล็อกทันที** (และในทางกลับกัน)

### ผลลัพธ์ที่คาดหวัง
- V25 จะ SKIP ออเดอร์สวนเทรนด์ทั้งหมดใน TRENDING/BREAKOUT regime
- V25 ยังคงทำงานได้ปกติเมื่อ AI bias เป็น NEUTRAL หรือตรงทิศทางเดียวกัน
- Log จะแสดง `PlaybookSelector SKIP: AI Bias Gate: V25 SELL blocked — AI pipeline says BUY (bias=BULL, regime=VOLATILE_BREAKOUT)`

### V25.3 Breakout Bypass (2026-05-11)

> **Driver:** Cycle #10016–#10029 — 3-Way Deadlock: EA บอก BUY 80% conf แต่ `enableV25Only` ไม่ให้ EA ยิงเอง → delegate ให้ V25 → V25 ไม่มี BELOW wall CONFIRM (ราคาขาขึ้น) → V25 เห็นเฉพาะ ABOVE wall (SELL) → AI Bias Gate บล็อก SELL (ถูกต้อง!) → **ไม่มีใครยิง BUY ได้เลย**

**สาเหตุ:** V25 ออกแบบมาสำหรับ **wall-touch reversal** (range-bound) ไม่ใช่ **breakout continuation**:
- ในภาวะ VOLATILE_BREAKOUT ราคาวิ่งทะลุ wall ขึ้นไปเรื่อยๆ
- V25 จะเห็นแต่ "ชน resistance → SELL" ซึ่งสวนเทรนด์ทุกครั้ง
- EA (Deterministic) เป็นตัวที่ถูกต้องสำหรับ breakout entry แต่โดนปิดกั้นด้วย `enableV25Only`

**แก้ไข 3 จุด:**
1. **`enableV25Only` bypass**: เมื่อ regime เป็น `BREAKOUT` → ให้ EA ยิงได้โดยตรง ไม่ต้อง delegate ให้ V25
2. **PB1 R2 relaxation**: ผ่อน SL distance cap จาก 1.0× เป็น 2.0× ATR(M15) ใน TRENDING regime
3. **Cross-Role Ban fix**: ตรวจจาก model ID pattern แทน providerId (เพราะ hardcoded 'openrouter' ทุกที่)

**ไฟล์ที่แก้:**
- `autoTradingService.ts:2201` — bypass enableV25Only สำหรับ BREAKOUT regime
- `PB1_WallTouchScalp.ts:103` — R2 SL cap 2.0× ใน TRENDING
- `ModelRankerService.ts:269` — isPaidDirectModel() + cross-role guard

### V25.3.1 Opposition Wall Safety Check (2026-05-11)

> **Driver:** Cycle #10032 — EA ยิง BUY ที่ 81449 ใต้ 4★ resistance @ 81474 แค่ 25 pts! M15 แสดง triple-top rejection — ราคาเทส 3 รอบแล้วไม่ผ่าน

**สาเหตุ:** V25.3 Breakout Bypass ข้ามการตรวจ wall awareness ทั้งหมด:
- Proximity Gate **BYPASSED** สำหรับ BREAKOUT strategy
- `enableV25Only` **BYPASSED** สำหรับ BREAKOUT regime
- ผลลัพธ์ = ซื้อตรงเพดาน resistance โดยไม่มีการตรวจ opposing wall เลย

**แก้ไข:** เพิ่ม **Opposition Wall Check** ใน breakout bypass path:
- สำหรับ BUY: ตรวจ resistance wall ≥3★ ที่อยู่เหนือ entry ภายใน 0.5×ATR
- สำหรับ SELL: ตรวจ support wall ≥3★ ที่อยู่ใต้ entry ภายใน 0.5×ATR
- ถ้าเจอ → **SKIP** (ไม่ยิง) → รอราคาทะลุ wall หรือลงมาที่ support ก่อน

**ตัวอย่างจาก live case:**
```
Entry=81449, ATR≈230, 0.5×ATR=115
Resistance @ 81474 (4★) → dist=25 < 115 → BLOCKED ✅
→ ถ้าราคาลงมาที่ support 81309 (4★) แล้วเด้ง = BUY ถูกตำแหน่ง
```

### V25.1.1 Hotfix 2026-05-10 — TDZ on `selectedStrategy`

**ปัญหาที่เจอใน first run:** `[V25 Gate] AI pipeline skipped` log ออกมาแล้วระบบ throw `ReferenceError: Cannot access 'selectedStrategy' before initialization` (`autoTradingService.ts:1122`) — Patch #6 ใช้ `selectedStrategy` ใน `aiDecision.strategy` แต่ตัวแปรนั้นถูกประกาศที่ line 1535 (หลัง AI pipeline) ทำให้ชน TDZ

**Fix:** เปลี่ยนไปใช้ `primaryAnalysis?.strategy` (ซึ่ง init ก่อนแล้ว line 689) — fallback `'TREND_FOLLOW'`:
```ts
const fallbackStrategy = (primaryAnalysis?.strategy as any) || 'TREND_FOLLOW';
aiDecision = { action: 'SKIP', strategy: fallbackStrategy, _source: 'v25_gate', ... };
```

ผลลัพธ์ (log 13:34:35 → cycle 9941): V25 Gate ทำงาน, ไม่ throw, fall-back ลง EA technical rules ปกติ + SL/TP ตอนนี้กลับมาออกถูกฝั่ง (BUY: SL=80348, TP=81398, RRR=2.50)

**ไฟล์ที่แก้:** `autoTradingService.ts` (1 บรรทัด in V25 Gate block)

---

## Hotfix 2026-05-10 — log spam guard (when market closed)

**ปัญหาที่เจอใน first run** (`V25_SHADOW=true`, XAUUSD ปิด): TickGateway poll `/symbol_info` ทุก 1s → emit `V25_STALE_TICK` ทุก 1s → WallStateMachine log `state machine frozen for XAUUSD ...` ทุก 1s → log รั่ว ~60 บรรทัด/นาที

**Fix:**
1. **TickGateway throttle V25_STALE_TICK** — emit ได้ at most 1/30s ต่อ symbol (`staleEmitMinIntervalMs: 30_000`)
2. **TickGateway adaptive back-off** — เห็น stale 5 ครั้งติดต่อกัน → ขยาย poll interval เป็น `1000 × 30 = 30s` (cap 60s) จนกว่าจะเจอ tick fresh แล้วค่อยกลับมา 1s
3. **WallStateMachine dedup frozen log** — log "frozen" แค่ตอน transition false→true; log "resumed for X (bar close M5)" ตอนปลด freeze หลัง bar close
4. ผลลัพธ์: market closed = ~2 บรรทัด/30s แทน 60/นาที

**ไฟล์ที่แก้:** `TickGateway.ts` (+~40 LOC), `WallStateMachine.ts` (+~5 LOC) — ทั้งคู่ compile clean

---

## 0) Problem Statement (จาก log จริง)

ใน 14 นาที ระบบรัน 14 cycle แต่ **executed = 0** ติดลูป SKIP รูปแบบเดียวกัน:

```
Entry: 80146–80278   (กลางช่องระหว่าง wall 5★ 80113 ↔ 80483)
SL   : 80300–80551   (AI เลือก wall ถัดไป — ไกลจาก entry 200–400 pip)
TP   : 80119–80126   (Global TP cap จาก wall เล็ก 1–2★ ขวางหน้า)
RRR  : 0.12–0.53     (SL/TP กลับด้านกัน → SKIP risk_params_gate)
LLM  : slTpAgent ถูกเรียกทุก cycle (เปลือง token)
```

### ปัญหาเชิงโครงสร้าง 4 ข้อ

1. **Cycle-based แทน event-based** — รันทุก 60–90s ไม่ดูราคาระหว่างรอบ ทำให้ context ล้าสมัย
2. **Entry กลางช่อง (no-man's-land)** — Proximity Gate ของ V24 บอก "near 5★ resistance @ 80483 (337 pips)" แต่ 337 pip ของ XBTUSD เกินขีด `proximityMaxPip:100` ของ XAUUSD — gate ทำงานผิดสำหรับ symbol ที่ pip ใหญ่
3. **SL chosen by farthest-strong-wall** — slTpAgent (LLM) ชอบเลือก wall ดาวเยอะที่ห่างมาก ทำให้ SL distance ใหญ่
4. **TP capped by tiniest obstacle** — Global TP cap ดึง TP มาใกล้ entry เพราะ wall 1–2★ บน path → RRR กลับด้านโดยอัตโนมัติ

> **Insight ของผู้ใช้:** "ผมไม่เปิดออเดอร์กลางช่อง — ผมรอราคา **แตะ wall** แล้วค่อยเข้า เข้า**ใกล้ wall** SL ใต้ swing TP **ใต้ wall ตรงข้าม**"

V25 = ระบบที่เลียนแบบสายตา trader คนนั้น ดูทุก tick แต่คำนวณเฉพาะตอนแท่งใหม่ปิด

---

## 1) Core Philosophy

> **No Wall, No Trade.**  ระบบจะ **ไม่** trigger pipeline ใด ๆ ถ้าราคาไม่อยู่ใน Reaction-Zone ของ wall ที่กำหนด

### กฎเหล็ก 5 ข้อ

| # | กฎ | สาเหตุ |
|---|-----|--------|
| R1 | Entry **ต้อง** อยู่ภายใน Reaction-Zone (ATR×k, k=0.5–1.5 ตาม playbook) ของ wall ≥ 2★ | กันเข้ากลางช่อง |
| R2 | SL ตั้งใต้/เหนือ swing **เลย wall** (ไม่ใช่ wall ถัดไป) ระยะไม่เกิน 1.0×ATR(M15) | กัน SL กระโดด |
| R3 | TP คือ **ใต้/เหนือ wall ตรงข้าม** (หัก spread+1tick) ใน path เดียวกัน — ไม่ลด TP ลงเพราะ obstacle เล็ก | กัน TP cap reverse RRR |
| R4 | คำนวณ Indicator/SMC/Wall **เฉพาะตอนแท่งใหม่ปิด** ของ TF นั้น (event-driven) — แต่ติดตามราคาแบบ **ทุก tick** | ลด CPU/token, ไม่พลาด trigger |
| R5 | LLM (slTpAgent/Analyst) จะถูกเรียก **เฉพาะเมื่อ Wall State Machine = REACT** เท่านั้น | กันเปลือง token (ตอนนี้เรียกทุก cycle) |

---

## 2) System Architecture — 7 Layers

```
┌──────────────────────────────────────────────────────────────────┐
│  L6  EXECUTION & MANAGEMENT  (existing AutoTradingEngine)        │
│       ↑ trade plan                                                │
├──────────────────────────────────────────────────────────────────┤
│  L5  PLAYBOOK SELECTOR    เลือก 1 ใน 5 playbook ตาม context     │
├──────────────────────────────────────────────────────────────────┤
│  L4  WALL STATE MACHINE   IDLE→APPROACH→REACT→CONFIRM→COMMITTED │
├──────────────────────────────────────────────────────────────────┤
│  L3  PRICE MAP (V21.1)    aggregator → walls + path              │
├──────────────────────────────────────────────────────────────────┤
│  L2  BAR-CLOSE CALCULATORS   (event-driven per TF)               │
│      M1 / M5 / M15 / M30 / H1 / H4 — แต่ละตัวรันเมื่อแท่งปิด     │
├──────────────────────────────────────────────────────────────────┤
│  L1  TICK STREAM   (real-time, 100–500ms throttle)               │
│      → in-memory ring buffer ของ tick + คำนวณ M1 wall ทุก 5tick │
├──────────────────────────────────────────────────────────────────┤
│  L0  MT5 BRIDGE   ws://tick + REST/candles                       │
└──────────────────────────────────────────────────────────────────┘
```

### Data Flow ต่อ tick

```
TICK arrives
  → L1: bid/ask, hi/lo running, throttle 200ms
       └─ update LiveBar(M1) running, every 5 ticks compute "micro-wall"
  → L4: Wall State Machine evaluate proximity to nearest walls (above & below)
       └─ if state changes (e.g. APPROACH→REACT) → emit event
  → L5: Playbook Selector (ถ้า REACT) → choose entry plan
  → L6: Execution (ผ่าน Hard Risk Gate ของเดิม)

BAR CLOSE (M1/M5/M15/M30/H1/H4)
  → L2: ของ TF นั้นเท่านั้นรัน — RSI/ATR/SMC/OB/FVG ใหม่
  → L3: PriceMap rebuild (เฉพาะถ้า M5/M15/M30/H1 แท่งใหม่)
  → L4: refresh wall list & invalidate stale state
```

> **กุญแจสำคัญ:** L1 รันทุก tick (cheap), L2/L3 รันเมื่อแท่งปิดเท่านั้น (expensive), L4 รันทุกครั้งที่ L1 หรือ L2/3 update

---

## 3) L1 — Tick Stream & M1 Micro-Wall

### 3.1 Tick Buffer

```ts
interface Tick { ts: number; bid: number; ask: number; mid: number; }
class TickBuffer {
  private ring: Tick[] = []; // size 600 (~5 min)
  push(t: Tick): void;       // throttled 200ms (drop intra-batch)
  lastN(n: number): Tick[];
  high(n: number): number;
  low(n: number): number;
}
```

### 3.2 M1 Micro-Wall (ตอบข้อ 1 ของ user — m1 5 แท่ง = wall ของ m5)

ทุก ๆ **5 tick** หรือ **1s** (อะไรมาก่อน) คำนวณ **rolling micro-pivot** จาก tick buffer:

```
microPivotHigh = max(high of last 5 M1 candles)   // = wall ของ M5
microPivotLow  = min(low  of last 5 M1 candles)
```

ส่งเข้า **PriceMap** เป็น level พิเศษ tag = `M1_MICRO` (★ = 1, ใช้เพื่อเฝ้า bounce/break แบบเรียลไทม์)

### 3.3 Throttle & Cost

- Tick stream: ws://mt5-core-server/tick/:symbol (push)
- Throttle 200ms → ~5 update/sec (พอสำหรับ scalp)
- M1 micro-wall: rebuild ทุก 1s → cheap (5–10 candle scan)

---

## 4) L2 — Bar-Close Calculators (event-driven)

| TF  | คำนวณตอน | Output |
|-----|---------|--------|
| M1  | M1 close | RSI(14), micro-FVG, micro-OB |
| M5  | M5 close | RSI/ATR/MACD/Stoch, FVG, OB, Sweep |
| M15 | M15 close | full SMC (FVG/OB/Sweep/CHoCH/BOS) — **TF หลักของ user** |
| M30 | M30 close | SMC + structure |
| H1  | H1 close | bias (BULL/BEAR) + key levels |
| H4  | H4 close | regime + macro bias |

### Trigger

```ts
EventBus.on('BAR_CLOSE', ({ symbol, tf, bar }) => {
  if (tf === 'M1') runM1Calc(symbol, bar);          // 60s
  else if (tf === 'M5') runM5Calc(symbol, bar);     // 5min
  // ... etc
});
```

> **ห้ามรันทุก TF พร้อมกันทุก cycle เหมือนเดิม** — ประหยัด CPU 80% ทันที

---

## 5) L3 — PriceMap (V21.1 ของเดิม + ส่วนเสริม)

**คงเดิม** จาก V21.1 — แต่เพิ่ม 3 source ใหม่:

| Source | Tag | ★ initial |
|--------|-----|----------|
| M1 Micro-Wall | `M1` | 1★ |
| Fresh-FVG (M5/M15) | `FVG_FRESH` | +1★ bonus |
| Recent Sweep High/Low | `SWEEP_RAW` | 2★ |

PriceMap rebuild **เฉพาะ** เมื่อ M5/M15/M30/H1 แท่งใหม่ปิด (ไม่ใช่ทุก tick)

---

## 6) L4 — Wall State Machine (หัวใจของ V25)

ทุก symbol มี **2 state machine** พร้อมกัน — `wallAbove` (ใช้สำหรับ SELL/REJECT-up) และ `wallBelow` (BUY/REJECT-down)

### 6.1 States

```
       ┌────────────┐
       │   IDLE     │   ราคาห่าง wall > 2.0×ATR(M15)
       └──────┬─────┘
              │ price ↘ (SELL setup)
              ▼
       ┌────────────┐
       │  APPROACH  │   ราคา ≤ 1.5×ATR ของ wall  — เริ่มเฝ้า
       └──────┬─────┘
              │ price within 0.5×ATR + tick-velocity drop
              ▼
       ┌────────────┐
       │   REACT    │   *** เรียก Playbook Selector ที่นี่ ***
       └──┬─────┬───┘
         BREAK  REJECT
          │      │
          ▼      ▼
   ┌─────────┐  ┌─────────┐
   │BREAK_OUT│  │ CONFIRM │  M1 close ตรงข้าม wall + reversal pattern
   └────┬────┘  └────┬────┘
        │ retest      │
        ▼             ▼
   ┌─────────┐  ┌─────────┐
   │RETEST   │  │COMMITTED│  ออกออเดอร์, lock state จนกว่าจะ exit
   └─────────┘  └─────────┘
```

### 6.2 Transition Rules

| From | To | Trigger |
|------|-----|---------|
| IDLE | APPROACH | `|price − wall| ≤ 1.5×ATR(M15)` (ตอบข้อ 3 — เข้าใกล้ wall) |
| APPROACH | REACT | `|price − wall| ≤ 0.5×ATR` AND tick-velocity ลดลง 30% (ตอบข้อ 3) |
| REACT | CONFIRM | M1 close + reversal pattern (engulf / pinbar / FVG-fill bounce) (ตอบข้อ 4, 6) |
| REACT | BREAK_OUT | M1 close ผ่าน wall + body ≥ 60% ของแท่ง (ตอบข้อ 3, 7) |
| BREAK_OUT | RETEST | ราคา pull-back กลับมาแตะ wall (ใช้เป็น new wall) (ตอบข้อ 7) |
| CONFIRM | COMMITTED | order ส่งสำเร็จ |
| any | IDLE | wall stale (รื้อ PriceMap หรือ time-stop 15min ไม่ progress) |

### 6.3 Indicator Inputs ต่อ State

```ts
type WallStateContext = {
  walPrice: number;
  wallStars: number;
  wallTFs: string[];        // ['M5','M15','H1']
  hasOB: boolean;
  hasFVG: boolean;
  // tick-level
  distancePips: number;
  velocityPipPerSec: number;
  // bar-close (M1)
  m1LastClose: number;
  m1IsReversal: boolean;
  // M15 context
  m15FVGOppositeFresh: boolean;  // ตอบข้อ 5
  m15Bias: 'BULL'|'BEAR';
  m1Bias: 'BULL'|'BEAR';
  // path
  pathToOppositeWall: PathAnalysis;
};
```

---

## 7) L5 — Playbook Selector (5 strategies, 1 active per state)

ตอบข้อ 8 — ไม่ใช่ SMC อย่างเดียว

### 7.1 Playbook Matrix

| ID | ชื่อ | Trigger State | TF | RRR target | Hold time |
|----|-----|--------------|-----|-----------|-----------|
| **PB1** | Wall-Touch Scalp ⭐ (default ของ user) | REACT → CONFIRM | M15 plan / M1 trigger | 1.5–3 | 5–30 min |
| **PB2** | FVG Fill Reversal | REACT (ถ้า wall มี OB+FVG fresh) | M15 | 2–4 | 15–60 min |
| **PB3** | Liquidity Sweep Reversal | REACT (price wicked beyond wall + closed back) | M15 | 2–5 | 30–120 min |
| **PB4** | Range Boundary Fade | REACT in RANGING regime | M30 | 1.5–2.5 | 30–60 min |
| **PB5** | Breakout-Retest Continuation | RETEST after BREAK_OUT | M5 | 2–3 | 15–45 min |

### 7.2 Selector Logic (priority order)

```ts
function selectPlaybook(ctx: WallStateContext, regime: Regime): Playbook | null {
  // PB3: sweep ก่อน — RRR สูงสุด
  if (ctx.recentSweep && ctx.m1IsReversal) return 'PB3_SWEEP_REVERSAL';

  // PB2: FVG fresh + OB
  if (ctx.hasOB && ctx.hasFVG && ctx.m15FVGOppositeFresh) return 'PB2_FVG_FILL';

  // PB5: retest after break
  if (ctx.state === 'RETEST') return 'PB5_BREAKOUT_RETEST';

  // PB4: ranging regime
  if (regime === 'RANGING') return 'PB4_RANGE_FADE';

  // PB1: default — wall touch
  if (ctx.wallStars >= 2 && ctx.m1IsReversal) return 'PB1_WALL_TOUCH';

  return null;  // ไม่เข้าเทรด
}
```

### 7.3 PB1 — Wall-Touch Scalp (ของ user, ตอบข้อ 9)

**Pre-conditions** (ALL required):
- Wall ใกล้ที่สุด **ตรงข้ามทิศที่จะเข้า**: stars ≥ 2★ AND TFs include M15
- Distance: `|price − wall| ≤ 0.5 × ATR(M15)`
- M1 reversal: engulf / pinbar / FVG-fill (ตอบข้อ 6)
- Opposite-side **fresh** FVG ที่ยังไม่เก็บ (ตอบข้อ 5)
- M15 micro-bias ไม่ขัดทิศ (อนุโลมถ้า H1=opposite trend)

**Entry**:
```
SELL: entry = wall − 1tick   (≈ที่ wall ตรงข้าม-up)
BUY : entry = wall + 1tick
```

**SL** (ตอบข้อ 9 ของ user):
```
SELL: SL = max(swingHigh_M5_last5, wall) + (1×ATR(M1) buffer)
BUY : SL = min(swingLow_M5_last5,  wall) − (1×ATR(M1) buffer)
```
Limit: SL distance ≤ 1.0 × ATR(M15) — ถ้าเกิน → reject playbook (ไม่ใช่ wall-touch แล้ว)

**TP** (ตอบข้อ 9 ของ user):
```
SELL: TP = oppositeWallBelow.price + 1tick  − spread  (= ใต้ wall ตรงข้าม)
BUY : TP = oppositeWallAbove.price − 1tick  − spread
Path obstacles: ไม่ดึง TP ใกล้ — ผ่าน obstacle 1★ ได้ (R3)
```

**Min RRR**: 1.5 (XBTUSD), 1.8 (XAUUSD)

### 7.4 ตัวอย่างเดียวกับที่ user ให้

```
  80483.36 ★★★★  ← wall ตรงข้าม (SELL TP target)
  ...
── PRICE: 80386.74 ──────────────
  80361.87 ★★★    ← wall ใกล้ (BUY entry zone)
  80300.52 ★★★
```

PB1 BUY:
- Entry  = 80361.87 + 1tick      (= 80361.88)
- SL     = swingLow(M5_last5) − 1×ATR(M1)   (e.g. 80335)
- TP     = 80483.36 − 1tick − spread        (e.g. 80477)
- Risk   = 27 pip, Reward = 115 pip → RRR = 4.26 ✅

> **ผลลัพธ์ที่คาดหวัง:** trade เดียวกันที่ V24 จะ "SKIP RRR 0.16" — V25 จะคำนวณได้ RRR 4.26 และเข้าได้

---

## 8) Detection Modules (ตอบข้อ 1–7 ของ user)

| # | ความต้องการของ user | Module | ทำงานเมื่อ |
|---|-------------------|---------|-----------|
| 1 | ติดตามราคา realtime + M1 wall | `TickBuffer` + `M1MicroWallBuilder` | ทุก 200ms |
| 2 | Mark wall ที่ราคาจะกระทำ | `WallProximityIndex` (sorted array, binary search nearest) | ทุก L1 update |
| 3 | ตรวจ "ใกล้ wall" + "ทะลุ wall" | `WallStateMachine` (state IDLE→APPROACH / REACT→BREAK_OUT) | ทุก L1 update |
| 4 | ตรวจเด้งกลับเมื่อใกล้/ทะลุ wall | `ReactionDetector` (M1 reversal pattern + tick velocity) | M1 close + tick velocity |
| 5 | FVG ที่เกิดใหม่ | `FvgFreshTracker` (ไม่เก็บ FVG ที่ filled แล้ว) | M5/M15 close |
| 6 | Confirm reversal ใน TF เล็ก M1 | `M1ReversalConfirmer` (engulf/pinbar/FVG-fill) | M1 close |
| 7 | Wall ใหม่หลังทะลุ | `BreakoutWallPromoter` (broken wall → new opposite wall, retest setup) | M1 close หลัง BREAK_OUT |

### 8.1 ReactionDetector (ข้อ 4)

```ts
function isM1Reversal(side: 'BUY'|'SELL', m1: Candle): boolean {
  if (side === 'BUY') {
    const bullEngulf = m1.close > m1.open && m1.close > prev.high && m1.open <= prev.close;
    const pinbar = (m1.low === Math.min(...) ) && (m1.close > m1.open) && (lowerWick / range > 0.6);
    const fvgFill = lastM1FvgBear && m1.high >= lastM1FvgBear.top && m1.close < lastM1FvgBear.top;
    return bullEngulf || pinbar || fvgFill;
  }
  // mirror for SELL
}
```

### 8.2 FvgFreshTracker (ข้อ 5)

```ts
class FvgFreshTracker {
  private bullFvgs: FVG[] = [];  // ราคาลงไปเก็บ → mark filled, drop
  private bearFvgs: FVG[] = [];

  onTick(price: number) {
    this.bullFvgs = this.bullFvgs.filter(f => price > f.bottom);  // ยังไม่ลงไปเก็บ
    this.bearFvgs = this.bearFvgs.filter(f => price < f.top);
  }
  hasFreshOpposite(side: 'BUY'|'SELL'): boolean {
    return side === 'BUY' ? this.bearFvgs.length > 0 : this.bullFvgs.length > 0;
  }
}
```

### 8.3 BreakoutWallPromoter (ข้อ 7)

ทันทีที่ M1 close ทะลุ wall (body ≥ 60% beyond):
1. mark wall เดิมเป็น `polarity=FLIP` (resistance → support หรือ vice versa)
2. promote เป็น candidate retest level
3. WallStateMachine: REACT → BREAK_OUT → RETEST (รอ pull-back)

---

## 9) Configuration Defaults (V25 patch)

```ts
adaptive: {
  // V24 เดิม - ตัด proximityMaxPip ทิ้ง (ใช้ ATR-based แทน)
  // proximityMaxPip: 100,             // ❌ DROP — ใช้ ATR-relative
  // proximityUnconditionalStars: 3,
  // proximityConditionalStars: 2,

  // V25 ใหม่
  v25: {
    enable: true,
    tickThrottleMs: 200,
    m1MicroWallSampleTicks: 5,
    approachAtrMultiplier: 1.5,
    reactAtrMultiplier: 0.5,
    breakBodyPercent: 60,
    swingLookback: 5,             // M5 last 5 candles for SL anchor
    slBufferAtrMul: 1.0,          // SL = swing ± 1×ATR(M1)
    slMaxAtrM15: 1.0,             // SL distance must ≤ 1×ATR(M15) else reject
    minRrrXBT: 1.5,
    minRrrXAU: 1.8,
    minWallStarsScalp: 2,
    minWallStarsSwing: 3,
    requireFreshOppositeFvg: true,
    skipMidChannelLLM: true,      // ห้ามเรียก slTpAgent ตอน IDLE/APPROACH
    barCloseEventBus: true,       // L2 ต้อง subscribe BAR_CLOSE event
  }
}
```

---

## 10) Code Structure (mt5-core-server)

```
mt5-core-server/src/services/auto/
├── tick/                            # NEW V25
│   ├── TickBuffer.ts
│   ├── TickGateway.ts               # ws subscriber from MT5 bridge
│   └── M1MicroWallBuilder.ts
├── state/                           # NEW V25
│   ├── WallStateMachine.ts
│   ├── WallProximityIndex.ts
│   └── ReactionDetector.ts
├── playbooks/                       # NEW V25 (one file per playbook)
│   ├── index.ts                     # PlaybookSelector
│   ├── PB1_WallTouchScalp.ts
│   ├── PB2_FvgFillReversal.ts
│   ├── PB3_SweepReversal.ts
│   ├── PB4_RangeFade.ts
│   └── PB5_BreakoutRetest.ts
├── analyzers/smc/                   # existing — เพิ่ม FvgFreshTracker
│   ├── FvgFreshTracker.ts           # NEW
│   └── BreakoutWallPromoter.ts      # NEW
├── core/
│   ├── IndicatorPipeline.ts         # MOD: subscribe BAR_CLOSE per TF (event-driven)
│   ├── SignalDetector.ts            # MOD: delegate to PlaybookSelector
│   └── BarCloseEventBus.ts          # NEW
└── AutoTradingEngine.ts             # MOD: replace cycle loop with state-machine react
```

### Bridge changes

```
mt5-core-server/src/bridge/
└── mt5Bridge.ts                     # MOD: เพิ่ม WS endpoint /ws/tick/:symbol
```

---

## 11) Migration Plan (4 phases)

### Phase A — Foundation (1 sprint)
- L0/L1: Tick gateway + TickBuffer + M1MicroWallBuilder
- L2: BarCloseEventBus + per-TF calculator subscription
- (no behavior change yet — running in parallel with V24)

### Phase B — Wall Engine (1 sprint)
- L4: WallProximityIndex + WallStateMachine
- ReactionDetector + FvgFreshTracker
- emit `WALL_REACT` events to existing AutoTradingEngine (still V24 deciding)

### Phase C — Playbook Switch (1 sprint)
- L5: PlaybookSelector with PB1 only (wall-touch scalp)
- ปิด V24 deterministic engine ระหว่าง market hours, ใช้ PB1
- A/B compare: V24 vs V25 PB1

### Phase D — Full Playbook & LLM Gating (1 sprint)
- เพิ่ม PB2–PB5
- Wire `skipMidChannelLLM` — slTpAgent ถูกเรียกเฉพาะ REACT state
- Sunset V24 FadeTheLevel

---

## 12) Expected KPI

| Metric | V24 (log จริง) | V25 target |
|--------|---------------|-----------|
| Cycles per executed trade | ∞ (0/14 cycles) | ≤ 6 |
| LLM calls (slTpAgent) per executed trade | ~14+ | ≤ 2 |
| Avg RRR at execution | n/a (ทุก SKIP) | ≥ 2.0 |
| SL/TP balance | TP cap < SL dist | TP > SL dist (R3 enforced) |
| False mid-channel signals blocked | 0 (ผ่านหมด) | 100% (no wall, no trade) |

---

## 13) Open Questions — ✅ Resolved (2026-05-10)

**Q1. Tick latency budget**
- Target: **p95 ≤ 300ms** end-to-end (MT5 bridge → state machine emit)
- Hard threshold: **1000ms** — เกินนี้ TickGateway จะ emit `STALE_TICK` warning + WallStateMachine freeze (ไม่ transition state) จนกว่า tick จะกลับมา fresh
- Measurement: ทุก tick มี `srcTs` (จาก MT5) + `recvTs` (จาก gateway) — Prometheus histogram `v25_tick_latency_ms`

**Q2. Multi-symbol throttle**
- Single Node event loop + **per-symbol async queue** (`Map<symbol, AsyncQueue>`) — ไม่ใช้ worker thread (overkill สำหรับ 2–5 symbol)
- ทุก tick enqueue เข้า queue ของ symbol นั้น → process แบบ FIFO sequential ภายใน symbol — แต่ขนานข้าม symbol (Node loop จัดให้)
- เหตุผล: throttle 200ms × 5 symbol = 25 process/sec → Node handle ได้สบาย ๆ; ไม่ต้องกัง race condition ภายใน symbol

**Q3. Persistence ของ Wall State**
- **Cold start นโยบาย** — restart = state reset ที่ `IDLE` ทุก symbol
- ตอน boot: replay last 60min ของ M1/M5/M15 candles ผ่าน REST `/candles` → rebuild PriceMap แล้วค่อย subscribe tick stream
- เหตุผล: state machine มี horizon 5–30 นาที — ถ้า restart >5 นาที แสดงว่ายุค context เปลี่ยนแล้ว replay tick log มีโอกาส mislead มากกว่าประโยชน์
- COMMITTED state (มีออเดอร์เปิด) — recover จาก MT5 broker positions list (ของเดิม) ไม่ใช่จาก state machine

**Q4. ATR multiplier ต่อ symbol**
ใช้ตาราง override (`v25.symbolProfiles`):

| Symbol | approachAtrMul | reactAtrMul | slBufferAtrMul | minRrr | minWallStars |
|--------|--------------:|------------:|---------------:|-------:|-------------:|
| XBTUSD | 1.5 | 0.5 | 1.0 | 1.5 | 2 |
| XAUUSD | 1.2 | 0.4 | 0.8 | 1.8 | 2 |
| Forex (default) | 1.0 | 0.3 | 0.6 | 1.8 | 3 |
| Indices (US100/US500) | 1.3 | 0.4 | 0.7 | 1.6 | 2 |

เหตุผล: BTC volatility ใหญ่ → zone กว้างขึ้น (1.5×ATR) ก่อนเข้า APPROACH; XAU/Forex ราคา fine-grained → zone เล็กกว่า + ต้องการ confluence stars สูงกว่า

> ค่าเหล่านี้เปิด config hot-reload ได้ผ่าน `PersistenceService` (เหมือน V24)

---

## 14) Related Docs

- [[21_PriceMap_V211]] — base PriceMap structure (คงไว้)
- [[22_IndicatorConfluence_V220]] — Indicator scoring (คงไว้, ใช้ใน L2)
- [[23_FadeTheLevel_V240]] — predecessor (จะถูก deprecate หลัง Phase D)
- [[01_Architecture/04_AutoTrading_Workflow]] — ต้องอัปเดตเป็น V25 หลัง Phase B
- [[25_TradeFlow_Diagrams]] — ASCII diagrams สำหรับการ pitch (แยกไฟล์)

---
**Author:** Jarvis design pass 2026-05-10
**Reviewed log:** 2026-05-09 14:09–14:29 XBTUSD (cycle #9563–#9576)
