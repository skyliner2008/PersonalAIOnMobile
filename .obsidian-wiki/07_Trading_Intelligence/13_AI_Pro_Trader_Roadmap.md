# 🧠 V20: AI Pro-Trader Agent — Roadmap

> **Status: 🟢 Phase 1, 2 & 3 Complete — 🟢 Phase 4-A Complete — 🟡 Phase 4-B In Progress** | **Target Release: 2026-Q3** | **Last Updated: 2026-04-23**

เอกสารนี้เป็นแผนพัฒนาที่ยกระดับ `mt5-core-server` จาก "defensive auto-trader" ไปเป็น **AI Pro-Trader Agent** — ระบบที่คิดและตัดสินใจเหมือนเทรดเดอร์มืออาชีพ: อ่านตลาดจากหลายมุม, วัดความเสี่ยงระดับพอร์ต, แตกการตัดสินใจเป็น specialist agents, และเรียนรู้จากตัวเองหลังปิดทุกไม้

## 📐 ปรัชญาการออกแบบ (Design Philosophy)

1. **Defense-first always**: Guard rails ปัจจุบัน (hard cap, cooldown, TF-guard, journal backfill) ห้ามถูกลดความเข้มงวด ทุกฟีเจอร์ใหม่ต้องเพิ่มเติมไม่ทับซ้อน
2. **Specialist over generalist**: แตก monolithic LLM call ออกเป็น agents เฉพาะทาง เพื่อให้ debug ได้ + swap model ได้อิสระ + test แต่ละชั้นได้
3. **Evidence-based self-evolution**: การปรับ prompt/threshold ทุกครั้งต้องผ่าน replay backtest ก่อน commit — ห้ามเปลี่ยนโดยไม่มีหลักฐาน
4. **Observability before automation**: ก่อนเพิ่ม autonomy ใหม่ต้องมี metrics/logging ให้ตรวจย้อนหลังได้เสมอ

## 🗺️ ภาพรวมทั้ง 5 เฟส

| เฟส | ชื่อเฟส | ระยะเวลา | สถานะ |
|-----|---------|----------|-------|
| 1 | Market Intelligence Layer | 2-3 สัปดาห์ | ✅ สำเร็จ |
| 2 | Multi-Agent Decomposition | 3-4 สัปดาห์ | ✅ สำเร็จ |
| 3 | Adaptive Risk & Position Sizing | 2-3 สัปดาห์ | ✅ สำเร็จ |
| 4 | Episodic Memory & Self-Evolution | 4-6 สัปดาห์ | 🟡 กำลังทำ (4A: Vector/RAG สำเร็จ) |
| 5 | Operational Excellence | ขนานตลอด | ⚪ รอเริ่ม |

---

## ⚙️ เฟส 1 — Market Intelligence Layer

**เป้าหมาย**: ให้ AI "เห็น" ตลาดแบบเทรดเดอร์จริง ไม่ใช่แค่ดู candle ของคู่เงินตัวเดียว

### สถานะความคืบหน้า (Audit ณ 2026-04-23)

| Item | ไฟล์ | Status | หมายเหตุ |
|------|------|--------|---------|
| Session Analyzer | `analyzers/session.ts` | ✅ ครบ | เชื่อมเข้า `inferAnalysis` และ `computeVolume` แล้ว |
| SMC Analyzer | `analyzers/smc.ts` | ✅ ครบ | FVG + Order Block เข้าร่วม confluence แล้ว |
| Cross-Asset Analyzer | `analyzers/crossAsset.ts` | ✅ ครบ | XAUUSD ดึง DXY/US10Y/SPX500 เรียบร้อย |
| News Gate | `risk.ts` | ✅ ครบ | `isBlockedByNews` บล็อกการเทรดช่วงข่าวแรงแล้ว |
| News Feed | `analyzers/news.ts` | ✅ ครบ | เชื่อม ForexFactory Weekly JSON จริงแล้ว |
| Session Volume Throttle | `ExecutionService.ts`| ✅ ครบ | Asian session × 0.5, Quiet session = 0 |
| Config Schema Fix | `types.ts` | ✅ ครบ | `maxCorrelation` และ `newsRisk` อยู่ใน schema & default แล้ว |

### งานที่เหลือในเฟส 1

**(ก) Wire real news feed** — เปลี่ยน `NewsAnalyzer.fetchUpcomingEvents` ให้เรียก ForexFactory weekly JSON (free, no key) หรือ Finnhub (ต้องมี API key แยก) map data → `EconomicEvent[]` แล้ว cache 15 นาทีเหมือนเดิม

**(ข) Dynamic quote currency** — ปัจจุบัน `updateNewsRisk('USD', ...)` hardcoded ต้องแยกตาม symbol: XAUUSD/EURUSD/GBPUSD → gate ต่อ USD, EURUSD เพิ่ม EUR, USDJPY เพิ่ม JPY

**(ค) Session volume throttle** — เพิ่มใน `computeVolume` หรือใน risk.ts: Asian session × 0.5 volume, Quiet session refuse outright ยกเว้น defense override

**(ง) Fix config field bug** — `cfg.risk.maxCorrelation` ไม่มีใน `AutoTradingConfig.risk` ต้องเปลี่ยนไปอ้าง field อื่น หรือเพิ่ม `maxCorrelation` เข้าไปใน schema (รวมถึง `default`, migration)

**Deliverables**: 
- `analyzers/news.ts` — real ForexFactory client (with fallback to stub)
- `analyzers/session.ts` — เพิ่ม `getVolumeMultiplier(session): number`
- `risk.ts` — apply session multiplier before sizing
- `autoTradingService.ts` — dynamic quote-currency mapping
- `types.ts` — เพิ่ม `maxCorrelation` หรือ refactor field name

---

## 🤖 เฟส 2 — Multi-Agent Decomposition

**เป้าหมาย**: แตก monolithic `agentOrchestrator.reason()` ออกเป็น specialist agents 4 ตัว ที่คุยกันผ่าน orchestrator กลาง

### สถาปัตยกรรมที่ต้องการ

```
┌───────────────────────────────────┐
│  Cycle Orchestrator               │
│  (runCycle in autoTradingService) │
└─────────┬─────────────────────────┘
          │
    ┌─────┴─────┬──────┬────────────┐
    ▼           ▼      ▼            ▼
┌────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐
│Analyst │ │Risk Off.│ │Exec Tr. │ │Post-Mort.│
│ Agent  │ │  Agent  │ │  Agent  │ │   Agent  │
└────────┘ └─────────┘ └─────────┘ └──────────┘
    ↓           ↓           ↓          ↓
  Market    Approve/     Order     Lessons &
  Narra-    Refuse/     Routing    Knowledge
  tive      Adjust      (limit vs  Graph
                        market)
```

**Analyst Agent** — อ่านตลาดทำ narrative + confidence รวมทั้งเสนอทิศทาง แต่**ไม่ตัดสินใจ** เพียงรายงาน ใช้ LLM: Gemini Pro (ดี)

**Risk Officer Agent** — รับข้อเสนอจาก Analyst + portfolio state แล้วตัดสิน: อนุมัติ/ปฏิเสธ/เสนอ partial มี **veto power** เหนือ Analyst ใช้ LLM: Gemini Flash (ถูก เร็ว rule-heavy)

**Execution Trader Agent** — รับ approved plan แปลงเป็น order type ที่เหมาะ (market vs limit vs stop), เลือก fill strategy (aggressive vs patient), handle partial fills ใช้ LLM: Gemini Flash

**Post-Mortem Agent** — รันหลัง trade ปิดทุกไม้ เขียน lesson learned ลง knowledge graph + vector store — **นี่คือที่มาของ self-evolution จริงๆ** ใช้ LLM: Gemini Pro (คุณภาพสำคัญกว่าความเร็ว)

### Deliverables

- `auto/agents/analyst.ts` — export `AnalystAgent.analyze(symbol, analyses, correlations): Promise<AnalystReport>`
- `auto/agents/riskOfficer.ts` — export `RiskOfficerAgent.review(report, account, positions, config): Promise<Approval>`
- `auto/agents/executionTrader.ts` — export `ExecutionAgent.planOrder(approval, marketDepth): Promise<OrderPlan>`
- `auto/agents/postMortem.ts` — export `PostMortemAgent.review(closedTrade): Promise<Lesson>`
- `auto/agents/types.ts` — contracts (AnalystReport, Approval, OrderPlan, Lesson)
- Refactor `runCycle` → orchestration pipeline

### Backward Compatibility

`agentOrchestrator.reason()` ปัจจุบันถูก deprecate แต่ยังไม่ลบ จนกว่าจะ migrate ครบ — เพิ่ม `config.adaptive.useMultiAgent` flag (default false) เพื่อ rollout ค่อยเป็นค่อยไป

---

## 💰 เฟส 3 — Adaptive Risk & Position Sizing

**เป้าหมาย**: ขจัด fixed-risk sizing — เทรดเดอร์จริงไม่เทรด 0.5% เท่าเดิมทุกไม้

### งาน

1. **Kelly-scaled sizing** — ใช้ win-rate + avg-R ต่อ strategy/regime จาก journal history คำนวณ optimal fraction ต่อไม้ (half-Kelly เพื่อ margin of safety)
2. **Portfolio heat** — track total open risk ใน R units (ไม่ใช่แค่ margin) ถ้า total open risk > 3R ปฏิเสธไม้ใหม่ แม้ AI มั่นใจแค่ไหน
3. **Regime-based throttle** — VOLATILE_BREAKOUT × 0.5 size, QUIET refuse ทั้งหมด
4. **Drawdown circuit breaker** — equity drop 3% ใน 1 วัน → pause engine 24 ชม., drop 5% → STOP manual review เท่านั้น
5. **Session volume cap** — ห้ามเปิดเกิน 3 ไม้ใน Asian session (low liquidity)

**Deliverables**: `auto/risk/kelly.ts`, `portfolioHeat.ts`, `circuitBreaker.ts` — integrate เข้า `risk.ts` gate chain

---

## 🧠 เฟส 4 — Episodic Memory & Self-Evolution

**เป้าหมาย**: ระบบจำเหตุการณ์ได้ เรียนรู้จากมัน และปรับปรุงตัวเองโดยไม่ต้อง reload โค้ด — นี่คือหัวใจของ "Jarvis"

### 4 องค์ประกอบ

1. **Episodic Memory** — เก็บ "ช่วงเวลา" แทน "snapshot เดี่ยว" เช่น "2026-04-22 NY session XAUUSD break 4720 หลัง CPI → trend เปลี่ยน 1.5%" เก็บใน vector store เป็น sequence embedding (window N candles + context) ไม่ใช่ single snapshot

2. **Memory Consolidation Loop** — นอกชั่วโมงเทรด รวบตะกอน journal + episodic → "narrative patterns" เช่น "BREAKOUT ใน VOLATILE_BREAKOUT regime ชนะ 62% แต่ RRR เฉลี่ย 1.3 — ต้อง tighten TP" service `memoryConsolidation` มีโครงแล้ว แค่ต้องเติม consolidation logic

3. **Prompt Self-Rewriting** — ให้ Post-Mortem Agent เขียน delta แก้ `agentPrompt` ได้ **แต่ต้องผ่าน Challenge Test** — replay on last 100 decisions ถ้า new prompt performance ไม่ดีกว่า baseline → reject, เก็บไว้ใน failed_mutations table

4. **Replay Harness** — ดึง journal ย้อนหลังมารัน engine ใหม่ได้ในโหมด paper trade — foundation ของ continuous learning

### Deliverables

- `auto/memory/episodic.ts`
- ขยาย `memoryConsolidation.ts`
- `auto/replay/harness.ts`
- `auto/prompts/versioned.ts` — store prompt versions ใน DB พร้อม performance metrics

---

## 🛠️ เฟส 5 — Operational Excellence (Ongoing)

ส่วนนี้ทำคู่ขนาน ไม่ block เฟสอื่น

### Observability

- **Prometheus metrics**: cycle duration, AI latency, fill rate, drawdown, rolling win-rate, hedge count
- **Grafana dashboards**: equity curve, per-strategy P&L, regime heatmap

### Alerting

- Discord/Telegram webhook: new trade, close, DD threshold, guard trigger, news block
- ตัดการต้องเปิด log ทิ้งไว้

### Killswitch

- ปุ่ม "PANIC CLOSE ALL" ใน Android client → server enforce (ปิดทุกไม้ + pause engine + alert)

### Jarvis UX

- ขยาย Android client ให้คุยกับ engine ได้ เช่น "ทำไม skip ไม้ล่าสุด?", "equity curve 30 วัน" — hook เข้ากับ conversational layer ที่โปรเจคหลักมีอยู่แล้ว
- Voice interface ถ้ามี STT/TTS pipeline พร้อม

### Multi-Account

- Abstract `mt5-core-server` ให้รับหลาย login — paper + live account ขนานกัน

---

## 📊 ลำดับความสำคัญ ROI สูงสุด 3 อย่างแรก

1. **เฟส 1 (ก)+(ข)** — News feed + dynamic quote currency → ลด loss จาก event-driven surprise ที่ระบบมองไม่เห็น
2. **เฟส 3 ข้อ 4** — Drawdown circuit breaker → insurance กัน blow-up ขณะยังเดินเฟสอื่น
3. **เฟส 2 Post-Mortem Agent** — foundation ของทุก self-evolution ทีหลัง เก็บ lesson ไว้ก่อน ค่อยใช้ภายหลัง

## 🔗 ลิงก์ที่เกี่ยวข้อง

- [[12_AutoTrading_Remote_Engine]] — เวอร์ชันปัจจุบัน V19.1 (baseline)
- [[03_Execution_SMC_V10]] — SMC doctrine ที่ analyzer อ้างอิง
- [[10_Global_Insights_V16.0]] — Cross-asset context theory
- [[11_MT5_Full_Agent_Control_V17]] — Multi-agent control design

## 📝 Changelog

| Date | Change |
|------|--------|
| 2026-04-23 | Initial roadmap + Phase 1-3 audit |
| 2026-04-23 | **V21 Milestone**: Gemini 2.5 Upgrade + Multi-Agent Stabilization + Defensive Override Policy implementation |
| 2026-04-25 | **V23 Smart Upgrade — Phase 1-4 (100%)**: Zone-aware + FVG-align entry gate, dynamic SL buffer, staged BE/Trail, smart scale-in, close-weakest-loser, hedge override, FLIP_CLUSTER full (close + open opposite), pre-analysis prior with confluence penalty, failure_pattern tagging, /metrics/quality JSON + Cycle Quality UI card. ดู [[../09_Roadmap/02_AutoTrading_Smart_Upgrade_Plan_2026_04_25]] |
