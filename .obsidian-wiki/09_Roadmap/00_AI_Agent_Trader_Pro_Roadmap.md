# PersonalAIBot → AI Agent Trader Pro — Roadmap

> **Audit date:** 2026-04-24
> **Scope:** วิเคราะห์สถานะปัจจุบัน + แผนยกระดับเป็น "AI Agent Trader Pro"
> **Target audience:** เจ้าของโปรเจค (solo dev)
> **อ้างอิง:** Explore audit + memory index + อ่านโค้ดจริง

---

## 0. Executive Summary

PersonalAIBot วันนี้มีรากฐานที่แข็งแกร่งมาก 3 เสา — Gemini multimodal orchestration, 4-layer memory, และ MT5 full-control trading (19 tools + SMC v16-17 + AutoTradingEngine 6-phase) — แต่ยังไม่ใช่ "AI Agent Trader Pro" ด้วยเหตุผลหลัก 4 ข้อ ได้แก่ (1) test coverage ใกล้ 0 ทำให้แก้บัคแล้วไม่มี regression net, (2) decision transparency ต่ำ — agent ตัดสินใจอะไรไม่มี audit trail ที่ reproducible, (3) intelligence layer ยัง single-strategy (SMC-heavy) ขาด ensemble + regime detection, และ (4) ไม่มี backtesting / walk-forward harness ทำให้วัดผลการเปลี่ยนแปลงเชิง quantitative ไม่ได้

แผนนี้แบ่งเป็น 4 Phase ที่สะสมความสามารถทีละชั้น — Phase 1 หา floor ให้เสถียร (testing + observability), Phase 2 ขยาย intelligence (ensemble + backtest), Phase 3 เปิด multi-agent + self-evolution จริง, Phase 4 ยกระดับเป็น production-grade (multi-broker + dashboard + safety)

---

## 1. สถานะปัจจุบัน (Snapshot 2026-04-24)

### 1.1 Strengths ที่ใช้เป็นฐานต่อได้

เสา AI orchestration แข็งแรงระดับดี JarvisOrchestrator + JarvisPlanner + ToolRegistry + LiveToolBridge + AutomationManager ครอบคลุม text/voice/vision ผ่าน Gemini 3.1 Pro (Live + Text) พร้อม function calling ครบลูป เสา memory ถือว่าเกินค่าเฉลี่ย app ปกติมาก มี 4 layers (Core/Working/Archival/GraphRAG) + sleep cycle consolidation เสา trading มี MT5 bridge 3 ชั้น (Kotlin → Node Express:8090 → Python mt5_bridge) เสถียรพอใช้งานจริง และมี AutoTradingEngine 6-phase (analyze → strategize → execute → manage → record → learn) ที่ผ่านการ harden ไปหลาย iteration (v17.0 hard cap + cooldown + backfill) SMC v16-17 มี 5 tools ครอบคลุม OB/FVG/BOS/CHoCH/Liquidity/Sweeps + Confluence stars

### 1.2 Gaps ที่ขวางการก้าวไป Pro

**A. Observability & Reliability**
- Test coverage ~0% (`composeApp/src/commonTest/` มีแค่ 1 smoke test)
- ไม่มี structured logging / trace id ข้าม layers (Kotlin → Node → Python) ทำให้ debug auto-trade incidents ต้องไล่ manual
- ไม่มี metrics endpoint / Prometheus / Grafana — ไม่รู้ p99 latency, error rate, quota consumption
- Gemini rate limit ไม่มี backpressure / circuit breaker visible

**B. Intelligence Gaps**
- Strategy แคบ — SMC-dominant, ยังขาด momentum/mean-reversion/volatility-breakout modes
- ไม่มี regime detection (trending/ranging/volatility spike) — strategy เดียว fire ทุก regime
- Economic Calendar + Correlation Radar (DXY/US10Y/SPX) มีใน wiki แต่ยังไม่ implement
- Sentiment/news scrapers อยู่ใน Tool Map V12.5 แต่ไม่มีโค้ดจริง
- ไม่มี backtesting harness — ทุก change ที่ใส่เข้า prod เป็นการเดิมพัน

**C. Agent Transparency**
- Decision log มี audit ใน `autoTradingService.ts` แต่ยังไม่เปิดเป็น reasoning chain ให้ user ตรวจ ("ทำไม AI เลือก BUY ไม่เลือก SELL ที่จุดนี้")
- ไม่มี counterfactual replay ("ถ้าข้าม trade นี้ EV จะเท่าไร")
- No eval suite สำหรับ prompt/agent performance — prompt drift ไม่มี early warning

**D. Security & Ops**
- `.env` ของ mt5-core-server มี broker credentials — ต้อง verify `.gitignore`
- SQLite memory DB ไม่ encrypted
- Gemini API key ส่งผ่าน settings แต่ไม่มี rotation/expiry policy
- ไม่มี CI/CD pipeline visible — build/test/deploy เป็น manual

**E. Data & Infra**
- OHLCV cache มี (`OhlcvCentralStore`) แต่ไม่มี snapshot/versioning ของ market data สำหรับ reproducible backtest
- ไม่มี experiment tracking (MLflow/W&B) สำหรับ strategy iteration

---

## 2. Vision — "AI Agent Trader Pro"

เป้าหมายปลายทางที่ roadmap นี้ชี้ไป คือระบบที่ (ก) มี **ensemble agents** หลายตัวคิดแข่งกัน แล้ว meta-agent ตัดสินใจ (ข) **ตัดสินใจแบบอธิบายได้** ทุก trade มี reasoning chain + evidence pack (ค) **Self-evolve** จากผลการเทรดจริง — แพ้ซ้ำจุดเดิมต้อง auto-adjust weight/threshold (ง) **Backtest-first** ทุก change ผ่าน walk-forward validation ก่อน ship (จ) **Multi-broker ready** ไม่ผูกกับ MT5 เดียว (ฉ) **Production-grade safety** — kill switch, daily loss limit, anomaly detection, secrets rotation

---

## 3. แผนพัฒนา 4 Phases

### Phase 1 — Stabilize & Observe (0-2 สัปดาห์) — *ไม่เติมฟีเจอร์ใหม่*

เหตุผลที่ต้องทำก่อนฟีเจอร์ใหม่ คือทุก feature ที่ต่อยอดจากระบบที่ไม่มี test/observability จะสะสมหนี้ทวีคูณ

**1.1 Test harness ฐาน**
- ตั้ง `composeApp/src/commonTest/` ให้ครอบคลุมอย่างน้อย AdvancedTradingEngine (score calc, confluence), SMC tools (OB detection, sweep logic), AutoTradingEngine phases แบบ pure-function test ด้วย mock inputs
- ฝั่ง `mt5-core-server` ใช้ Jest หรือ Vitest ทดสอบ `orderSchema` parsing, `autoTradingService` state transitions, risk guards (hard cap, cooldown, TF-switch guard regression A/B/C จาก memory 2026-04-23)
- Target: coverage 40%+ สำหรับ strategy/risk code (ส่วนที่เสียแล้วเจ็บที่สุด)

**1.2 Structured logging + trace propagation**
- แทน `println`/`console.log` ด้วย structured JSON logs (timestamp, trace_id, span, level, tool, ticket)
- Propagate trace_id: Kotlin tool call → HTTP header `X-Trace-Id` → Node → Python bridge
- Log to both stdout + rolling file (เก็บ 7 วัน)

**1.3 Metrics endpoint**
- เพิ่ม `/metrics` ใน mt5-core-server (Prometheus format) นับ: trade_placed_total, trade_rejected_total (reason label), gemini_calls_total, gemini_errors_total, loop_latency_ms histogram
- Dashboard ง่าย ๆ ด้วย Grafana local container

**1.4 Security hardening**
- ตรวจ `mt5-core-server/.gitignore` ให้แน่ใจว่า `.env` + credentials ถูกยกเว้น
- ย้าย secrets ไป env var จริง (ไม่มี fallback default ในโค้ด)
- ใส่ SQLCipher หรือ platform keystore wrapper ให้ SQLite memory DB

**Deliverable:** test report + metrics dashboard screenshot + security audit checklist (checked)

---

### Phase 2 — Intelligence Expansion (2-6 สัปดาห์)

**2.1 Regime Detection Module**
สร้าง `RegimeDetector.kt` ใน package `tools.trading.regime` รับ H1/H4 candles คำนวณ 3 indicators: ADX (trend strength), BB Width percentile (volatility), HV (historical vol) → classify เป็น `TRENDING_STRONG | TRENDING_WEAK | RANGING | VOLATILITY_SPIKE` จาก state นี้ AutoTradingEngine เลือก strategy weight ต่างกัน (SMC weight สูงตอน trending, mean-reversion เพิ่มตอน ranging, ลด size + ขยาย SL ตอน vol spike)

**2.2 Strategy Ensemble**
ปัจจุบัน AdvancedTradingEngine = 5-dim score รวมเป็น 0-100 เสนอให้แบ่งเป็น 3 "agents" ย่อย:
- **TrendAgent** — LSD + HMA + MTF bias
- **SMCAgent** — OB/FVG/Liquidity sweep entry
- **MomentumAgent** — BB Squeeze + AO + RSI divergence

แต่ละ agent ให้ `Signal(side, confidence, evidence[])` แยก meta-agent (`StrategyArbiter.kt`) ชั่ง signals โดยใช้ regime weights + historical edge ของ agent นั้นใน regime นั้น (เก็บใน SQLite `agent_performance` table)

**2.3 Correlation Radar + Economic Calendar**
- สร้าง `CorrelationRadarService` ดึง DXY/US10Y/SPX500 เป็น dependent streams ผ่าน broker (ถ้า symbol มี) หรือ public API → คำนวณ rolling corr 1h/4h → เตือนเมื่อ correlation shift abrupt (ปกติเป็น setup confluence เพิ่ม)
- `EconomicCalendarService` — scrape ForexFactory/Investing.com (หรือ API) ผ่าน WebFetch → block entries ±15 นาที รอบ High-impact events ของ currency ใน symbol

**2.4 Backtesting Harness**
สร้าง `backtest/` module ที่ replay OHLCV จาก `OhlcvCentralStore` feed ผ่าน AutoTradingEngine (เปิด "dry-run mode" ที่ไม่ส่ง order จริง เขียนผล equity curve ลง SQLite) พร้อม metrics: Sharpe, Sortino, Max DD, Profit Factor, Win Rate, Expectancy, Avg R:R per trade

รองรับ walk-forward: แบ่ง data เป็น train/validate windows — strategy ที่ overfit จะโชว์ชัดที่ validate window

**2.5 Sentiment Pulse (ถ้าพอมีเวลา)**
Scraper เบา ๆ ดึง headline จาก Reuters/Bloomberg/Reddit r/Forex ผ่าน WebFetch → Gemini classify sentiment → inject เป็น `extra_context` ให้ SMCAgent (ลด bias conviction เมื่อ sentiment ขัด)

**Deliverable:** Regime-aware trading + backtest report เปรียบเทียบ "pre-Phase-2 baseline" vs "post-Phase-2" บนชุดข้อมูล Q1 2026 + agent edge matrix

---

### Phase 3 — Multi-Agent & Self-Evolution (6-12 สัปดาห์)

**3.1 Multi-Agent Orchestration (MAO)**
ยกระดับจาก "3 sub-strategies" เป็น full multi-agent framework แบบ specialized roles:
- **ResearcherAgent** — รับ news/econ + ทำ premise ("EURUSD มีโอกาสลงจาก DXY strength + ECB dovish")
- **TechnicalAgent** — เช็ค technical confirmation (SMC/regime)
- **RiskAgent** — ตรวจ exposure, correlation risk, daily loss budget
- **ExecutionAgent** — place order เมื่อทั้ง 3 ผ่าน
- **CriticAgent** — ทบทวน rationale ของคนอื่น หาช่องโหว่ก่อน fire

ใช้ pattern แบบ Anthropic "orchestrator-worker" — orchestrator คือ JarvisOrchestrator, workers ส่ง intermediate messages ผ่าน in-memory bus (เริ่มไม่ต้อง distributed)

**3.2 Reasoning Chain & Decision Audit**
ทุก auto-trade ต้องผลิต `DecisionTrace` (JSON) ที่ commit ลง `decision_log` table:
```
{
  "trace_id": "...",
  "symbol": "EURUSD",
  "side": "BUY",
  "entry_price": 1.0823,
  "regime": "TRENDING_STRONG",
  "agents": {
    "researcher": {"thesis": "...", "evidence": [...]},
    "technical": {"score": 78, "confluences": [...]},
    "risk": {"exposure_pct": 1.2, "cleared": true},
    "critic": {"challenges": [...], "mitigated": [...]}
  },
  "final_conviction": 0.72,
  "counterfactual_skip_ev": -0.3
}
```
เปิด UI tab "Reasoning" ใน TradingTerminalScreen ให้ user เลื่อนดู decision ใด ๆ แล้ว drill-down

**3.3 Self-Evolution Loop**
- Post-trade ทุกเคส → คำนวณ realized R/R, MFE/MAE, slippage
- Feed เข้า `LearningLedger` เก็บ per-agent-per-regime edge
- Nightly "reflection" job (cron) — ถ้า agent X ใน regime Y มี rolling edge < 0 ติดกัน N trades → auto-tune (ลด weight 20%, raise threshold) + log incident
- ครั้งที่ tune แล้วไม่ดีขึ้น 2 รอบ → freeze agent นั้นใน regime นั้น แจ้ง user ผ่าน chat

**3.4 Eval Suite สำหรับ Agent Prompts**
สร้าง dataset ~50 market scenarios (snapshot OHLCV + context) ที่ label "correct action" แล้ว run agent ensemble ผ่าน scenarios หลัง prompt changes ทุกครั้ง — ถ้า accuracy/F1 ลด ต้อง block merge

**Deliverable:** Multi-agent production + Reasoning tab UI + self-evolution nightly job + eval CI gate

---

### Phase 4 — Production-Grade Ops (12+ สัปดาห์)

**4.1 Multi-Broker Abstraction**
สกัด `BrokerAdapter` interface ที่ MT5 เป็น 1 implementation เพิ่ม OandaAdapter / IBAdapter ได้ — เป้าไม่ใช่ให้ support หลาย broker ทันที แต่เพื่อ decouple trading logic ออกจาก MT5 quirks (filling mode, symbol naming) ทำให้ unit test ง่ายขึ้นด้วย

**4.2 Live Dashboard**
Compose Desktop app แสดง real-time:
- Equity curve + open P&L
- Agent scoreboard (edge ของแต่ละ agent × regime)
- Trade journal filterable
- Kill switch + daily loss bar
- Gemini quota consumption

**4.3 Safety & Compliance**
- Kill switch (hard stop ทุก auto-trade) ที่เรียกได้จาก UI + REST endpoint + voice ("Jarvis, halt all trading")
- Daily loss limit — cumulative loss > X% ของ equity → auto-disable until next session
- Anomaly detection — spread ผิดปกติ, quote freeze, bridge unreachable → pause + alert
- Secrets rotation policy — Gemini key + broker token quarterly

**4.4 CI/CD Pipeline**
GitHub Actions หรือ GitLab CI: build matrix (Android/Desktop/iOS) + test + lint + security scan (`npm audit`, `trivy` สำหรับ Docker ถ้าเอา mt5-core-server ลง Docker) + eval suite + release artifacts

**4.5 Experiment Tracking**
Integration ง่าย ๆ กับ MLflow local server เก็บ (a) strategy param sets (b) backtest results (c) live A/B runs — reproducibility + comparability

**Deliverable:** dashboard deployed + multi-broker PoC + CI green pipeline + operations runbook

---

## 4. Priority Matrix

ถ้ามีเวลาจำกัด ให้เลือกตามลำดับต่อไปนี้ (ผลกระทบสูง × effort ต่ำ-กลาง ก่อน):

อันดับ 1 (High impact, low effort) คือ structured logging + trace id, test harness สำหรับ risk guards regression, `.gitignore` + secrets audit — ทำภายในสัปดาห์แรกได้ ป้องกัน incident ใหญ่ในอนาคต

อันดับ 2 (High impact, medium effort) คือ Backtesting harness — เป็น foundation ของทุกเรื่องที่ตามมา ไม่มี harness = ทำ ensemble/regime/self-evolve แบบเดินตาบอด

อันดับ 3 (Medium impact, medium effort) คือ Regime detector + Strategy ensemble — ทำให้ edge กระจาย ไม่ผูกอยู่กับ SMC ตัวเดียว

อันดับ 4 (High impact, high effort) คือ Multi-agent + Reasoning tab + Self-evolution — value สูงสุด แต่ต้องมี 1-3 รองรับก่อนไม่งั้นเดี๋ยว refactor รอบใหญ่

อันดับ 5 คือ Multi-broker + Dashboard + CI/CD — production hardening สำหรับตอนที่ระบบจะ run 24/7 จริงแบบไม่มีคนดู

---

## 5. Quick Wins — ทำได้ภายในวันนี้/สัปดาห์นี้

- **Trace id propagation** — เพิ่ม `X-Trace-Id` header ใน HTTP client ฝั่ง Kotlin (`BridgeClient`) และ middleware ฝั่ง Node ให้ log พร้อม trace id เดียวกัน ใช้ debug ครั้งเดียวคุ้มทันที
- **`.gitignore` audit** — run `git check-ignore mt5-core-server/.env` ตรวจให้แน่ใจว่าไม่หลุด git history (ถ้าหลุดแล้ว ต้อง rotate broker creds + git filter-repo)
- **Unit tests risk guards** — เขียน 10-15 test cases ครอบ hard cap, cooldown, TF-switch guard, position backfill (regression A/B/C ใน memory 2026-04-23)
- **Kill switch ฉุกเฉิน** — endpoint `/auto/halt` ใน mt5-core-server + ปุ่มแดงใน Auto-Trading tab ของ TradingTerminalScreen
- **Daily loss guard** — ก่อน Phase 1 เสร็จก็ควรมีก่อนเลย cap ที่ -3% equity ต่อวันเป็นค่าเริ่มต้น
- **Decision log snapshot** — เพิ่ม field `decision_snapshot_json` ใน trade table เก็บ state ที่ agent เห็นตอนตัดสินใจ (inputs + intermediate scores) ใช้ backtest/debug ในอนาคต

---

## 6. Risk Register

**R1 — Gemini quota / rate limit** ระบบ auto-trade ลูปต่อเนื่องอาจชน quota — mitigation: rate limiter + local caching + fallback ไป cached decision ถ้าเรียกไม่ทัน

**R2 — Broker divergence bug** ราคา bridge ≠ broker จริง (เคยเกิด filling-mode fallback ใน memory 2026-04-20) — mitigation: ticks สองแหล่งมา cross-check ก่อน entry > threshold

**R3 — Agent over-confidence after lucky streak** self-evolution อาจ reinforce pattern ที่บังเอิญชนะ — mitigation: ใช้ rolling window ≥ 30 trades + regime-normalized edge + require statistical significance ก่อน auto-tune

**R4 — Single point of failure (Python bridge)** ถ้า Python process ตาย = trading หยุด — mitigation: health check + auto-restart + fallback mode (position-close only ถ้า bridge ฟื้นไม่ได้)

**R5 — Prompt drift** Gemini update → behavior เปลี่ยน เงียบ ๆ — mitigation: eval suite ใน CI + nightly canary scenarios

**R6 — Regulatory / operational** auto-trading หลัก ๆ เป็น personal use แต่ถ้า share/commercial ต้องเช็คกฎ broker + กฎ region

---

## 7. สรุปภาพเชิงเปรียบเทียบ

วันนี้ PersonalAIBot = **"Jarvis ที่ตั้งใจดีและเทรดเก่งพอใช้"** — เก่งเพราะมี SMC v17 + risk guards ที่ผ่านสนามมาหลายรอบ แต่ไม่ pro เพราะยังอธิบายไม่ได้ (no reasoning audit) วัดผลเชิงสถิติไม่ได้ (no backtest) และปรับตัวเองไม่ได้ (no learning loop)

เป้าที่ roadmap นี้พาไป = **"AI Agent Trader Pro ที่รู้ตัวเอง"** — มีหลาย agents ที่ถกเถียงกันก่อน fire, log เหตุผลทุกการตัดสินใจให้ audit ได้, วัด edge ต่อ agent ต่อ regime, และเรียนรู้จากผลจริงแบบ principled ไม่ใช่ over-fit รายวัน

ลำดับ 3 อันดับแรก (log/trace + test harness + backtest) เป็น "non-negotiable foundation" ที่ต้องวางก่อน — ส่วนอื่นต่อยอดได้ตามจังหวะชีวิตและ energy ของเจ้าของโปรเจค

---

*ไฟล์นี้เป็นเอกสารที่ edit ต่อได้ — ถ้าเลือกทำ Phase ใด ให้กลับมาอัปเดตส่วน Deliverable + ข้าม Phase ไปใช้เป็น checklist ระหว่างทาง*
