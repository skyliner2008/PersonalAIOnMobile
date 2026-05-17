# V21.0 — Event-Driven Architecture

> **Version:** V21.0  
> **Date:** 2026-05-06  
> **Status:** 🟢 All Phases Complete — Build Passed

---

## สถาปัตยกรรมใหม่

### ก่อน (Polling)
```
ทุก 70 วินาที:
  runCycle() → ดึง candles → inferAnalysis → deterministicDecide → AI pipeline → ส่ง Order
```

### หลัง (Event-Driven)
```
EA Engine (continuous):
  onM1Close → IndicatorPipeline → SMC V2 + Signal Detection → EventBus
  
AI Agents (on-demand):
  EventBus → SIGNAL_ENTRY → AgentCoordinator → AI Approval → Execute
  EventBus → REGIME_CHANGE → Analyst Agent → ปรับ bias
  EventBus → TRADE_CLOSED → Post-Mortem Agent
```

## โครงสร้างไฟล์ใหม่

```
📁 mt5-core-server/src/services/auto/
├── analyzers/smc/              # Phase 1: SMC Engine V2
│   ├── types.ts               # Shared types
│   ├── utils.ts               # ATR, Swing, Candle helpers
│   ├── marketStructure.ts     # BOS/CHoCH/SMS/BMS + IDM
│   ├── fvgDetection.ts        # FVG + ATR filter + mitigation
│   ├── orderBlocks.ts         # OB + FVG confirm + invalidation
│   ├── liquidityZones.ts      # Equal H/L + Swing + Confluence★
│   ├── sweepDetection.ts      # MTF Sweep + Attack Force
│   └── index.ts               # buildSmcSnapshot()
│
├── core/                       # Phase 2-5: Event-Driven Core
│   ├── EventBus.ts            # Typed event system
│   ├── IndicatorPipeline.ts   # SMC V2 + Technical analysis
│   ├── SignalDetector.ts      # EA Brain (4 entry patterns)
│   ├── AgentCoordinator.ts    # AI Approval Mode
│   └── SmcTrailManager.ts     # SMC trailing + regime flip + anti-hedge
│
└── analyzers/smc.ts           # Updated: bridges V1↔V2
```

## Phase 1: SMC Engine V2 (Port จาก Pine Script V8.3)

| Module | Features |
|--------|----------|
| Market Structure | BOS, CHoCH, SMS, BMS + IDM filter |
| FVG Detection | ATR-filtered, mitigation tracking, average lines |
| Order Blocks | FVG-confirmed, mitigation, invalidation, trend filter |
| Liquidity Zones | Equal H/L, Swing, confluence stars 0-5★ |
| Sweep Detection | MTF aggregate (M1→H4), reclaim check |
| Attack Force | High momentum candles (body+volume+ATR) |

## Phase 2: Event Bus

| Event Type | ตัวส่ง | ตัวรับ |
|-----------|--------|--------|
| SIGNAL_ENTRY | SignalDetector | AgentCoordinator |
| SIGNAL_EXIT | TradeManager | SL/TP Agent |
| REGIME_CHANGE | IndicatorPipeline | Analyst Agent |
| TRADE_CLOSED | AutoEngine | Post-Mortem Agent |
| INDICATOR_UPDATE | IndicatorPipeline | (logging) |
| CLUSTER_ALERT | Risk Manager | Risk Officer |

## Phase 3: Signal Detector (EA Brain)

| Entry Pattern | เงื่อนไข | Confidence |
|--------------|----------|-----------|
| OB_BOUNCE | ราคาชนขอบ OB + FVG confirm + HTF aligned | 50-90% |
| LIQ_SWEEP | MTF sweep ≥2 TFs + wick reclaim | 55-85% |
| STRUCTURE_BREAK | CHoCH + IDM swept + OB re-enter | 60-85% |
| FVG_FILL | Price fills 50% FVG + trend aligned | 45-70% |

## Phase 4: Agent Coordinator (AI Approval Mode)

### Decision Flow:
1. EA confidence ≥ 80% + 3★ → **Auto-approve** (ข้าม AI)
2. EA confidence < 80% → **AI Pipeline** (Analyst → Risk → Exec → SL/TP)
3. AI unavailable / rate limited → **EA Fallback** (conf ≥ 65%)
4. AI max 20 calls/hour → **Rate limiter** ป้องกัน 429

## Phase 5: Trade Management Enhancement

| Feature | เดิม | ใหม่ |
|---------|------|------|
| Trail Logic | Fixed ATR distance | SMC zones (OB, FVG, Swing) |
| Anti-Hedge | Block ฝั่งตรงข้าม | ปิด loser ก่อน → เปิดฝั่งใหม่ |
| Regime Flip | ไม่มี | CHoCH against cluster → ปิด losers auto |
| CLP | ปิด BE ซ้ำ | Check สิ่งที่แตกต่างก่อนปิด |

---
**Last Updated:** 2026-05-06  
**Build Status:** 🟢 PASSED (0 errors)

---

## V21.1 — EA Fallback (Critical Fix)

> **Date:** 2026-05-06  
> **ปัญหา:** AI 429 Rate Limit ทำให้ทุก symbol SKIP — engine หยุดเทรด

### ก่อน
```
AI fail (429) → aiDecision = { action: 'SKIP' } → ไม่เทรด → เสียโอกาส
```

### หลัง
```
AI fail (429) → ตรวจ detDecision.confidence ≥ 45% → ใช้ SMC V2 หา SL/TP → ตรวจ RRR ≥ 1.2 → เทรดได้เลย
```

### เงื่อนไข EA Fallback
1. AI return SKIP เพราะ error (ไม่ใช่ analysis)
2. Deterministic มี action (BUY/SELL) + confidence ≥ 45%
3. ไม่ counter-trend ใน VOLATILE_BREAKOUT
4. RRR ≥ 1.2 (จาก SMC zones)

### จุดที่ใส่ (2 ตำแหน่ง)
1. **Multi-agent pipeline** — หลัง Promise.race resolve (graceful SKIP)
2. **Catch block** — เมื่อ AI throw error (429/timeout)

