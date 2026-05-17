# mt5-core-server

Trading Engine Hub & Multi-Agent Reasoning System.

## Features
- **4-Layer Reasoning**: Advanced trade filtering using Gemini AI (Narrative, Technicals, Context, Risk)
- **FIFO Bridge Execution**: Thread-safe communication with MetaTrader 5 via Python bridge
- **Episodic Memory**: RAG-enhanced reasoning using `gemini-embedding-2` for past trade retrieval
- **Observability & Resilience**:
    - **Structured Logging**: Production-grade JSON logs using `Pino`.
    - **Trace ID Propagation**: End-to-end request tracking across Node.js and Python layers.
    - **Unit & Integration Testing**: 30+ tests covering critical trading logic and security.
- **SQLite Persistence**: Local data storage for snapshots, analytics, and knowledge vault.

## Project Roadmap

### ✅ Wave 1: Foundation (Completed)
- [x] Basic MT5 Bridge connectivity.
- [x] Simple risk management (DD, Daily Loss).
- [x] AI reasoning integration (Gemini).

### 🛡️ Wave 2: Testing Net (Completed 2026-04-24)
- [x] **Connectivity**: CORS support for Mobile/ngrok access.
- [x] **Resilience**: FIFO Mutex and Smart Symbol Resolver (BTC -> XBTUSD mapping).
- [x] **Parsing**: Resilient JSON Unwrapping for Mobile tools in `TradingToolExecutor`.
- [x] **Stability**: Fixed ReferenceErrors and centralized logging utilities.
- [x] **Infrastructure**: Vitest fully integrated for CI.

### 🚀 Wave 3: Scale & Refactor (Next)
- [ ] **Decoupling**: Separate AutoTradingService into specialized sub-services.
- [ ] **Config Hot-Reload**: Update risk parameters without restart.
- [ ] **Enhanced RAG**: Integration with deeper market history.

### 🎯 Wave 4: V24.0 Fade-the-Level (2026-05-07)
- [x] **Proximity Gate** (`auto/core/ProximityGate.ts`): เข้าออเดอร์เฉพาะเมื่อ
  ราคาใกล้ S/R wall (≤100 pip) — fade ไม่ chase
- [x] **Sequential Entry Gate**: 1 ไม้/symbol → เปิดเพิ่มได้เมื่อ TRAIL ทำงานหรือ
  ขาดทุน ≥25% ของ SL
- [x] **Basket BE Guard**: ไม่ปิดไม้ขาดทุนแล้วเรียก "BE" ถ้า basket รวมยังลบ
- [x] **RRR float-safe compare**: แก้ `1.4999... < 1.5` ใน V21.0 EA Fallback
  และ V23.0 EA-Only paths
- รายละเอียด: [`07_Trading_Intelligence/23_FadeTheLevel_V240.md`](../.obsidian-wiki/07_Trading_Intelligence/23_FadeTheLevel_V240.md)

### ☁️ Wave 5: Google Cloud Vertex AI Integration (2026-05-13)
- [x] **Vertex AI Provider** (`providers/vertexai.ts`): ใช้ ADC (Application Default Credentials) สำหรับ enterprise-grade Gemini access
- [x] **Mobile Proxy** (`routes/vertexProxy.ts`): ให้ mobile app เรียก Vertex AI ผ่าน server (ADC ไม่ทำงานบนมือถือ)
- [x] **Registry Integration**: ลงทะเบียนใน provider system ข้างๆ Gemini/OpenAI/Claude

### 🛠️ Wave 6: V26.1 Structural TP & EA Stability (2026-05-15)
- [x] **Structural TP Cap** (`auto/analyzers/smc/priceMapBuilder.ts`): แก้ไข Bug ที่ TP กะโดดข้ามกำแพง 4★ — เพิ่มระบบ `targetWallStars` เพื่อ Cap TP ให้แม่นยำขึ้น
- [x] **EA-ONLY Hard-Skip**: ปรับปรุงให้ `slTpAnalystAgent` ข้ามการเรียก AI (LLM) 100% เมื่อปิดโหมด AI เพื่อประหยัด API และเพิ่มความเสถียร
- [x] **Bridge Synchronization**: ขยาย Window การดึง History เป็น 60 วัน (UTC-Safe) แก้ไขปัญหาออเดอร์ค้างใน Journal เนื่องจากการต่างกันของ Timezone
- รายละเอียด: [`07_Trading_Intelligence/32_TP_Structural_Wall_Correction_V261.md`](../.obsidian-wiki/07_Trading_Intelligence/32_TP_Structural_Wall_Correction_V261.md)
- [x] **ReferenceError Fix** (`autoTradingService.ts`): แก้ไขบั๊ก `allCandles is not defined` ที่ทำให้ Market Cycle ล้มเหลววนลูป
- [x] **Event-Driven MTF Sync**: ปรับปรุงการประกาศตัวแปร `allCandles` ให้รองรับการประมวลผล Multi-Timeframe แบบ Real-time
- รายละเอียด: [`07_Trading_Intelligence/33_AutoEngine_Runtime_Stability_V262.md`](../.obsidian-wiki/07_Trading_Intelligence/33_AutoEngine_Runtime_Stability_V262.md)

### Wave 7: V26.4 Zone-Aware Gate MTF Local Override (2026-05-18)
- [x] **MTF Zone Context** (`auto/core/ZoneAwareGate.ts`): H4/H1 are treated as major/context layers while M30/M15/M5/active TF can provide execution-zone pullback alignment.
- [x] **Fresh LTF FVG Override**: `SMC_FVG_SCALP` now checks aligned FVGs across H1/M30/M15/M5/active TF with ATR tolerance, so H4 discount alone does not veto every trend-follow SELL.
- [x] **Stale-Stop R Consistency**: stale-position time stop now uses broker `priceOpen` first, matching the manager audit R calculation.
- Details: [`07_Trading_Intelligence/34_ZoneAwareGate_MTF_LocalOverride_V264.md`](../.obsidian-wiki/07_Trading_Intelligence/34_ZoneAwareGate_MTF_LocalOverride_V264.md)

## Quick Start
1. `npm install`
2. Configure `.env` (CORS_ORIGIN, etc. Secrets are auto-generated in the database)
3. `npm run dev` or `start.bat`
4. Login with default credentials: ID `admin` / Password `admin`
5. Run tests: `npm test`

### Vertex AI Setup (Optional)
1. ตั้งค่า GCP: `gcloud auth application-default login`
2. เพิ่มใน `.env`:
   ```
   VERTEX_PROJECT_ID=your-project-id
   VERTEX_LOCATION=us-central1
   ```
3. เลือก "Vertex AI" เป็น provider ใน Dashboard Settings
