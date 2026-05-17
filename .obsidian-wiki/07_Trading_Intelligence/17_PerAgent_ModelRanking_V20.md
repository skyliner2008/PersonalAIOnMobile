# Per-Agent Model Ranking & SMC SL/TP Agent (V20.0)

> อัปเดต: 2026-05-04 | Phase 20.7 Cross-Role Toxic Ban + Fallback Hardening
> สถานะ: Production (mt5-core-server)

---

## บทสรุป (What Changed)

V20.0 แก้ปัญหาหลัก 6 ข้อของ Smart Free Fallback ที่มีมาตั้งแต่ V19.5:

| # | ปัญหาเดิม | วิธีแก้ใหม่ |
|---|-----------|------------|
| 1 | Scoring/Penalty ไม่แม่นพอ | Per-agent score pool แยกตาม role |
| 2 | ไม่มี round-robin สำหรับโมเดลที่ยังไม่เคยลอง | Explore-every-N=5 rotation |
| 3 | Blacklist อยู่แค่ใน session-memory | Time-based blacklist ใน SQLite (2h/4h/12h) |
| 4 | AI Model Leaderboard Dashboard พัง | แก้ให้ query จาก `ai_model_agent_stats` |
| 5 | SL/TP ผิดผ่านไปโดยไม่ถูก detect | SlTpAnalystAgent + sanity checks |
| 6 | ทุก agent ใช้ pool เดียว | แยก `(model_id, agent_role)` pair |

---

## 1. ตาราง DB ใหม่: `ai_model_agent_stats`

```sql
CREATE TABLE ai_model_agent_stats (
  model_id               TEXT    NOT NULL,
  provider_id            TEXT    NOT NULL DEFAULT 'openrouter',
  agent_role             TEXT    NOT NULL DEFAULT 'reasoning',
  total_calls            INTEGER DEFAULT 0,
  success_calls          INTEGER DEFAULT 0,
  error_calls            INTEGER DEFAULT 0,
  timeout_calls          INTEGER DEFAULT 0,
  content_fail_calls     INTEGER DEFAULT 0,
  consecutive_failures   INTEGER DEFAULT 0,
  avg_latency_ms         REAL    DEFAULT 0,
  total_tokens           INTEGER DEFAULT 0,
  total_profit_r         REAL    DEFAULT 0,
  win_count              INTEGER DEFAULT 0,
  loss_count             INTEGER DEFAULT 0,
  sl_tp_score            REAL    DEFAULT 0,    -- slTpAgent accuracy (±0.1 per close)
  blacklisted_until      INTEGER DEFAULT 0,    -- unix ms; 0 = not blacklisted
  last_used_at           INTEGER DEFAULT 0,
  updated_at             INTEGER DEFAULT 0,
  PRIMARY KEY (model_id, agent_role)
);
```

- ตารางเดิม `ai_model_stats` ยังคงอยู่เพื่อ backward-compat (legacy fallback)
- Indexes: `(agent_role, blacklisted_until, total_calls)` สำหรับ getTopModels, `(agent_role, last_used_at)` สำหรับ round-robin

---

## 2. ModelRankerService V20.0

**File:** `src/services/auto/core/ModelRankerService.ts`

### AgentRole types
```typescript
type AgentRole =
  | 'analyst'
  | 'riskOfficer'
  | 'executionTrader'
  | 'postMortem'
  | 'reasoning'
  | 'embedding'
  | 'slTpAgent'   // V20.0 - SMC-aware SL/TP placement
```

### Scoring Formula
```
score = (winRate * 35)
      + (successRate * 25)
      + MIN(20, profitR * 2)
      + MAX(0, 5 - latencyMs/2000)          -- latency bonus
      + (slTpScore * 2)                     -- only for slTpAgent role
      - (errorRate * 20)
      - (consecutiveFailures * 10)
      - (contentFailCalls * 3)
```

### Time-Based Blacklist Tiers (2026-05-04 Escalating)
| consecutive_failures | blacklist duration | หมายเหตุ |
|---------------------|-------------------|----------|
| 1 (content fail) | 15 นาที | single parse/format fail |
| 1 (hard error) | 10 นาที | network/timeout |
| 2+ | 1 ชั่วโมง | consecutive failures |
| 4+ | 4 ชั่วโมง | repeated failures |
| 6+ | 12 ชั่วโมง | persistent bad model |
| 8+ | 24 ชั่วโมง | nearly toxic |
| **10+** | **30 วัน (permanent)** | **toxic model — effectively banned** |

> ⚠️ **2026-05-04 Fix**: เดิม streak สูงสุดแค่ 4h blacklist → โมเดลอย่าง `liquid/lfm-2.5` (streak=118) วนกลับมาทุก 4h แม้ไม่เคยให้ค่าถูกต้อง ตอนนี้ streak ≥ 10 = ban 30 วัน

**Toxic Model Protection**: โมเดลที่ `consecutive_failures ≥ 5` จะ **ไม่ถูก force-reset** แม้ว่าจะเป็นโมเดลเดียวใน role — ปล่อยให้ dispatcher ใช้ fallback chain แทน ป้องกันวนลูป invoke → fail → blacklist → reset → invoke

### Round-Robin Exploration
- ทุก `EXPLORE_EVERY_N = 5` calls → เลือกโมเดลที่ใช้น้อยที่สุด (oldest `last_used_at`) สำหรับ role นั้น
- ป้องกัน cold-start monopoly: โมเดลใหม่จะได้รับการทดสอบก่อน rank ถูกตัดสิน

### Key Methods
```typescript
getTopModels(providerId, limit, agentRole)       // เลือก top model สำหรับ role นี้
getAllRankings(agentRole?)                        // สำหรับ Leaderboard UI
recordExecution(modelId, providerId, latency, tokens, success, agentRole?)
recordContentFailure(modelId, providerId, agentRole)
recordTradeOutcome(modelId, profitR, agentRole)
recordSlTpAccuracy(modelId, deltaScore)          // V20.0 specific
syncDiscoveredModels(models, providerId)         // bulk upsert จาก provider discovery
cleanupLegacyStats()                             // migrate ai_model_stats → ai_model_agent_stats
isModelBlacklisted(modelId, agentRole)           // V20.7 public check สำหรับ dispatcher
checkCrossRoleToxic(modelId, providerId, now)    // V20.7 cross-role ban
```

### Fix #7: Cross-Role Toxic Model Ban (2026-05-04)
เมื่อ model มี `content_fail_calls` รวมทุก role ≥ 10 → blacklist **ทุก role** 30 วัน

**ปัญหาเดิม**: `liquid/lfm` ถูก blacklist เฉพาะ `reasoning` role แต่ยังถูกเลือกสำหรับ `analyst`/`riskOfficer`/`executionTrader` เพราะ per-role stats แยกกัน

```sql
-- นับ content_fail_calls รวมทุก role
SELECT SUM(content_fail_calls) as total_cf
FROM ai_model_agent_stats WHERE model_id = ?
-- ถ้า total_cf >= 10 → UPDATE ALL roles: blacklisted_until = +30 days
```

### Fix #8: Fallback Blacklist Check (2026-05-04)
เดิม dispatcher retry fallback โดยไม่เช็ค blacklist → `llama-3.3-70b` (streak=150+, permanently rate-limited HTTP 429) ถูก retry ทุก cycle เสียเวลา 1-2s

**Fix**: เพิ่ม `isModelBlacklisted()` check ก่อน retry แต่ละ fallback model

### Fix #9: Risk Officer RRR Hallucination Guard (2026-05-04)
โมเดลเล็ก ๆ มักหลอนคณิตศาสตร์ (เช่น เห็น RRR=1.50 พอดีเป๊ะ แต่มองว่า < 1.5 แล้ว SKIP ออร์เดอร์ทิ้ง)

**Fix**: ใน `riskOfficer.ts` ถ้า LLM ตอบ `approved: false` ด้วยเหตุผล `RRR` แต่ระบบเช็คคณิตศาสตร์แล้วพบว่า Actual RRR >= 1.5 (เผื่อ float 2%) ระบบจะ:
1. เขียนทับ reason ว่า `Risk Officer hallucinated RRR violation`
2. ส่ง `recordContentFailure` ทันที เพื่อลงโทษ + นับรวมใน Cross-Role Ban (Fix #7)

### Fix #10: Refined Sequential Protection Risk Gate (2026-05-04)
ปัญหาเดิม: `Sequential Protection` บล็อคออร์เดอร์ใหม่ถ้าออร์เดอร์เก่ายังไม่ "Safe" (`R >= 0`) ทำให้ระบบ Freeze ไปเลยเมื่อเจอตลาดไซด์เวย์ที่ PNL แกว่งแถวๆ Break-even (-0.05R)

**Fix**: ปรับปรุงให้ `isLastSafe = lastPositionR >= -0.10` โดยอนุโลมให้ระยะ -0.10R เป็นโซนใกล้ Break-even / ตลาด Sideways (Noise) เพื่อให้ระบบไม่โดน Freeze นานเกินไป

## 3. dispatcher.ts — Per-Agent Routing

**File:** `src/services/auto/providers/dispatcher.ts`

ทุก `invoke()` call ส่ง `agentRole` ไปด้วยแล้ว:

```typescript
// ก่อน V20.0
const top = modelRankerService.getTopModels(choice.provider, 1);

// V20.0
const top = modelRankerService.getTopModels(choice.provider, 1, ctx.role);
```

- `recordExecution` และ `recordContentFailure` ทุกจุดส่ง `agentRole` ด้วย
- `generateForRole({ cfg, role: 'slTpAgent' }, prompt, opts)` — ใช้สำหรับ slTpAnalystAgent

---

## 4. SlTpAnalystAgent (ใหม่ทั้งหมด)

**File:** `src/services/auto/agents/slTpAnalyst.ts`

### ปัญหาที่แก้
- เดิม: SL/TP คำนวณจาก ATR สูตรล้วน → AI มักส่งค่าผิด (distance แทน price) ผ่านไปโดยไม่ถูก reject
- ใหม่: ดึง SMC structural levels จากกราฟจริง → ใช้ LLM ยืนยัน → fallback hierarchy ชัดเจน
- **2026-05-04**: เพิ่ม auto-correct distance → price level ก่อน reject

### Two-Stage Pipeline

```
Input: { symbol, side, entry, atr, rawCandles (multi-TF), analyses }
         │
         ▼
Stage 1: Deterministic SMC Extraction
  ├── getSmcStructure(candles) สำหรับแต่ละ TF (H4, H1, M15, M5)
  ├── extractResistanceLevels: bearOB tops + bear FVG tops + swingHighs + structureHigh
  ├── extractSupportLevels:   bullOB bottoms + bull FVG bottoms + swingLows + structureLow
  ├── nearestAbove(entry, resistanceLevels) → SL สำหรับ SELL / TP สำหรับ BUY
  ├── nearestBelow(entry, supportLevels)   → SL สำหรับ BUY  / TP สำหรับ SELL
  └── Validate: SL >= 0.5 ATR, RRR >= targetRrr
         │
         ▼
Stage 2: LLM Refinement (generateForRole slTpAgent)
  ├── Prompt ส่ง: entry, side, ATR, SMC structure text, deterministic candidate
  ├── LLM ตอบ: { sl, tp, confidence, rationale }
  ├── Sanity checks:
  │     - sl/tp อยู่ฝั่งถูก (BUY: sl < entry < tp)
  │     - sl/tp ไม่ใช่ distance (0.1% < value/entry < 200%)
  │     - ⭐ ถ้าเป็น distance → ลอง auto-correct เป็น price level ก่อน reject
  │     - ถ้ายังผิด → recordContentFailure + null → fallback to Stage 1
  └── modelUsed บันทึกไว้ใน marketSnapshot.slTpModelId
         │
         ▼
Final Resolution:
  sl = llmSl ?? detResult.sl ?? (entry ± 1.0 ATR)
  tp = llmTp ?? detResult.tp ?? (entry ± 1.5 ATR × targetRrr)
  source = 'smc_ai' | 'smc_deterministic' | 'atr_fallback'
```

### Auto-Correct Distance → Price Level (2026-05-04 NEW)

เมื่อ validator ตรวจพบว่า AI ส่ง SL/TP เป็น distance (เช่น SL=0.44 แทน 4605.63):
```
1. ตรวจจับ: aiSlPctFromEntry < 0.1% หรือ > 200% → isDistance = true
2. แปลง: BUY → sl = entry - |distance|, SELL → sl = entry + |distance|
3. ตรวจสอบ: แปลงแล้วยังอยู่ฝั่งถูก + distance >= minSlDist → ใช้ค่าที่แปลง
4. ล้มเหลว: ถ้าแปลงแล้วยังผิด → reject + fallback to ATR
```
> ช่วยกู้คืน trade intent จาก model ที่ให้ distance แทน price level ได้ ~70% ของ cases

### SL/TP Placement Rules (per SMC spec)
```
BUY trade:
  SL → just BELOW nearest support (bull FVG bottom / bull OB bottom / swing low)
  TP → just BELOW nearest resistance (bear OB top / bear FVG top / swing high)
       (place inside zone, not past it — avoids rejection wick)

SELL trade:
  SL → just ABOVE nearest resistance (bear FVG top / bear OB top / swing high)
  TP → just ABOVE nearest support (bull OB bottom / bull FVG bottom / swing low)
```

### Buffer Logic
- Gold (XAUUSD/GOLD): buffer = 0.5 pts เพื่อ avoid wick
- Forex/Crypto: buffer = 0

### SlTpAccuracy Feedback Loop
เมื่อ trade ปิด ใน `detectAndReviewClosedTrades()`:
```typescript
const snap = safeJsonParse<any>(row.marketSnapshot || '{}', {});
if (snap?.slTpModelId) {
  const delta = profitR > 0 ? +0.1 : profitR < -0.5 ? -0.1 : 0;
  modelRankerService.recordSlTpAccuracy(snap.slTpModelId, delta);
}
```

---

## 5. SMC Structure Export (smc.ts)

**File:** `src/services/auto/analyzers/smc.ts`

ฟังก์ชัน + interface ใหม่ที่เพิ่มเข้าไป:

```typescript
export interface SmcStructure {
  activeFVGs:    FVG[];
  bullOBs:       OrderBlock[];
  bearOBs:       OrderBlock[];
  swingHighs:    number[];   // slice(-5) ล่าสุด
  swingLows:     number[];
  structureHigh: number;
  structureLow:  number;
}

export function getSmcStructure(candles): SmcStructure {
  // ดึง FVGs, OrderBlocks, Swings, Structure ทั้งหมดในขั้นตอนเดียว
}
```

---

## 6. AutoTradingService — Priority Chain

**File:** `src/services/autoTradingService.ts`

ลำดับ SL/TP resolution ใน cycle loop:
```
1. ATR formula (rawSl, rawTp)         ← always computed first as baseline (uses biasSide)
2. slTpAnalystAgent (smcSl, smcTp)    ← V20.0: SMC structural (uses biasSide)
3. AI validator with auto-correct     ← 2026-05-04: try distance→price before reject
4. resolvedAi (resolvedAiSl/Tp)       ← ExecutionTrader / RiskOfficer final confirm
5. Side Validation Gate (NEW)         ← 2026-05-04: validate ALL values against final `side`

Final:
  sl = roundToTick(resolvedAiSl || validSmcSl || validRawSl)
  tp = roundToTick(resolvedAiTp || validSmcTp || validRawTp)
```

### Fix #4: smcSl/smcTp Side Validation (2026-05-04 CRITICAL)

**Bug**: `slTpAgent` ถูกเรียกด้วย `biasSide` (จาก technical analysis) แต่ AI สามารถ override `side` ได้ เช่น:
- bias=BEAR → slTpAgent คำนวณ SL/TP สำหรับ SELL → SL=80098 (เหนือ entry)
- AI override → side=BUY → smcSl=80098 อยู่ผิดฝั่ง!
- `sl = resolvedAiSl || smcSl || rawSl` → ใช้ smcSl=80098 (ผิดฝั่ง) เพราะเป็น truthy
- Sanity check ดัน SL = entry - tick → distance ≈ 0 → Hard Risk Gate SKIP

**Fix**: ตรวจสอบ smcSl/smcTp/rawSl/rawTp ทั้งหมดกับ final `side` ก่อนใช้:
```typescript
if (side === 'BUY' && validSmcSl >= entry) validSmcSl = null;  // discard wrong-side
if (side === 'BUY' && validSmcTp <= entry) validSmcTp = null;
// Also recompute rawSl/rawTp for correct side if biasSide !== side
```

### Fix #5: agentRole Penalty (2026-05-04)
เดิม `recordContentFailure(model, 'openrouter')` ไม่ส่ง `agentRole` → default `'reasoning'` → blacklist เฉพาะ reasoning role → model ยังถูกเลือกสำหรับ analyst/riskOfficer/etc.
แก้: ส่ง `aiPenaltyRole` ที่ตรงกับ pipeline role

### Fix #6: Auto-Correct Distance Cap (2026-05-04)
เดิม auto-correct TP=199126 (≈ entry×2.5) → entry + 199126 = 278781 → ยิ่งผิด
แก้: จำกัด auto-correct เฉพาะค่าที่ < 10% ของ entry (`maxAutoCorrectDist = entry * 0.10`)

### P1.4 FVG SL Push — Side Validation (2026-05-04 FIX)

**Bug**: เมื่อ FVG อยู่เหนือ entry (เช่น BUY entry=79825, FVG=80021-80211) การ push SL ไป `fvg.bottom` ทำให้ SL > entry → sanity check ดัน SL = entry - tick → distance ≈ 0 → Hard Risk Gate SKIP ทุก cycle

**Fix**: ตรวจสอบ pushed SL ยังอยู่ฝั่งถูกก่อนใช้:
```typescript
if (side === 'BUY') {
  const candidate = roundToTick(fvg.bottom - bufferTick, tick);
  if (candidate < entryRounded) sl = candidate;  // ✅ correct side
  else continue;  // ❌ FVG above entry — skip this FVG
}
```

ผลลัพธ์ที่บันทึกใน `marketSnapshot`:
```json
{
  "slTpModelId": "openai/gpt-4o-mini",
  "slTpSource": "smc_ai",
  ...
}
```

### Private Methods เพิ่มใหม่
```typescript
private async isMarketOpen(): Promise<boolean>
// ตรวจว่า watchlist symbol ใดสักตัวเปิดอยู่ (delegrates to isSymbolTradable)

private loadConfig(): AutoTradingConfig
// hot-reload config จาก PersistenceService ทุก 60s

private async loadSymbolCandles(symbol, tf, count): Promise<any[]>
// wrapper → marketDataService.loadSymbolCandles

private async loadOptionalCandles(symbol, tf, count): Promise<any[]>
// wrapper → marketDataService.loadOptionalCandles (never throws)

private upsertJournal(row: JournalRow): void
// wrapper → persistenceService.upsertJournal

private loadOpenJournal(): JournalRow[]
// wrapper → persistenceService.loadOpenJournal

public async runDeepAnalysis(symbol, timeframe): Promise<Record<string, unknown>>
// single-symbol full multi-TF analysis (ใช้โดย /deep-analysis route)
```

---

## 7. AutoTradingConfig — Fields ใหม่

```typescript
agentModels?: {
  analyst?:          AgentModelChoice | string;
  riskOfficer?:      AgentModelChoice | string;
  executionTrader?:  AgentModelChoice | string;
  postMortem?:       AgentModelChoice | string;
  reasoning?:        AgentModelChoice | string;
  embedding?:        AgentModelChoice | string;
  slTpAgent?:        AgentModelChoice | string;  // V20.0
}
```

`AgentModelChoice`:
```typescript
interface AgentModelChoice {
  provider: 'gemini' | 'openai' | 'claude' | 'openrouter' | 'ollama' | 'native';
  model: string;
  useSmartFree?: boolean;
}
```

---

## 8. Files Changed Summary

| File | การเปลี่ยนแปลง |
|------|---------------|
| `src/db.ts` | เพิ่ม `ai_model_agent_stats` table + indexes, migration `ai_model_stats` |
| `src/services/auto/types.ts` | เพิ่ม `slTpAgent` ใน `agentModels`, `serverMode` ใน `AutoTradingSnapshot` |
| `src/services/auto/modelResolver.ts` | เพิ่ม `'slTpAgent'` ใน `AgentRole` union |
| `src/services/auto/core/ModelRankerService.ts` | Rewrite ทั้งหมด — per-agent, time-blacklist, round-robin |
| `src/services/auto/providers/dispatcher.ts` | ส่ง `agentRole` ทุก call |
| `src/services/auto/analyzers/smc.ts` | เพิ่ม `SmcStructure` interface + `getSmcStructure()` export |
| `src/services/auto/agents/slTpAnalyst.ts` | **ใหม่** — SlTpAnalystAgent ทั้งไฟล์ |
| `src/services/autoTradingService.ts` | Wire slTpAgent, เพิ่ม private methods, fix safeJsonParse |

---

## 9. ผลลัพธ์ที่คาดหวัง

- **SL/TP quality**: structural levels แทน ATR formula → SL อยู่ใต้ support จริง ไม่โดน wick
- **Model routing**: Analyst ที่แย่ไม่ลาม block ExecutionTrader (แยก pool)
- **Exploration**: โมเดลใหม่ได้รับโอกาสทดสอบอัตโนมัติ ไม่ต้อง manual config
- **Blacklist persistence**: รีสตาร์ท server ไม่ reset blacklist อีกต่อไป
- **Feedback loop**: slTpScore ดีขึ้นเรื่อยๆ ตามผลการเทรดจริง

---

## 10. MiniMax Provider Integration (V20.5)

ระบบ `ModelRankerService` และ `dispatcher.ts` รองรับการจัดอันดับและสลับโมเดลจาก **MiniMax** (`minimax` provider) เพื่อความหลากหลายและคุ้มค่า:
- **Registry Support:** โมเดลตระกูล `abab` (เช่น `abab6.5s`) จะถูกค้นหาและบันทึกสถิติลง `ai_model_agent_stats` แยกตาม Role
- **Smart Free Compatibility:** สามารถใช้งานร่วมกับระบบ `useSmartFree` ได้เหมือน OpenAI/Claude/Gemini
- **Pricing Awareness:** ระบบคัดแยกโมเดล `isFree` จาก `PROVIDER_DEFAULTS` เพื่อให้ `getTopModels` ทำงานได้ถูกต้อง

---

*ดู spec เพิ่มเติม: [[15_Cluster_Trading_Logic_V17.2]] | [[16_Complete_Code_Logic_V19.6]] | [[Smart_Fallback_and_Token_Management]]*
