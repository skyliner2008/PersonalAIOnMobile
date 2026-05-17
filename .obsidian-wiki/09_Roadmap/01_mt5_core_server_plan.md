# mt5-core-server — Deep Audit & Improvement Plan

> **Audit date:** 2026-04-24
> **Path:** `C:\Users\JOJO\AndroidStudioProjects\PersonalAIBot\mt5-core-server`
> **Stack:** Node.js (ESM) + TypeScript 5.7 strict + Express 4.21 + better-sqlite3 (WAL) + ws + zod + hnswlib-node + Python MT5 bridge

---

## 0. TL;DR

Server ตัวนี้ไม่ใช่ "ตัวเชื่อม MT5 ธรรมดา" — มันคือ **auto-trading brain** จริง ๆ ที่มี 12+ ตารางในฐาน, multi-agent (Analyst/RiskOfficer/Execution), HNSW vector store, knowledge graph (nodes+edges), realtime WebSocket hub, hot-reload config, และ bridge supervisor ที่ auto-start Python ได้ รากฐาน engineering ดีกว่าที่คิด

---

## 1. สถานะปัจจุบัน — สิ่งที่มีจริง (Strengths)

- **Architecture แบบหลายชั้น** ที่ `src/services/auto/` มี agent จริง 3 ตัว พร้อมระบบความจำ (Episodic Memory)
- **Database schema ครบเครื่อง** 12+ ตาราง รองรับทั้ง Audit, Market Cache, และ GraphRAG
- **Bridge integration แข็งแรง** — มี FIFO mutex กัน race condition และระบบ Auto-start/Supervisor
- **Token auth ถูกต้องเชิง crypto** — SHA256 + pepper พร้อมระบบเก็บประวัติการใช้งาน
- **API surface ชัดเจน** — ใช้ Zod ในการควบคุมความถูกต้องของข้อมูลทุก Endpoint

---

## 2. แผนปรับปรุงและสถานะ (Roadmap Status)

### Wave 0 — Emergency Security Patch ✅ **DONE 2026-04-24**
- [x] บังคับใช้ Secrets ผ่าน `.env` (ADMIN_TOKEN, TOKEN_PEPPER)
- [x] **Encryption-at-rest**: เข้ารหัส Gemini API Key ในฐานข้อมูลด้วย AES-256-GCM
- [x] **Rate Limiting**: ป้องกันการยิงถล่มด้วย `express-rate-limit`

### Wave 1 — Observability Foundation ✅ **DONE 2026-04-24**
- [x] **Structured Logging**: ใช้ `pino` ออก JSON logs พร้อมระบบ Trace ID ข้ามเครื่อง
- [x] **Prometheus Metrics**: เปิด Endpoint `/metrics` สำหรับติดตามประสิทธิภาพ
- [x] **Request Audit**: บันทึก Payload ของคำสั่งเทรดที่สำคัญลง Logs อัตโนมัติ

### Wave 2 — Testing Net ✅ **DONE 2026-04-24**
- [x] **Vitest Framework**: ระบบทดสอบอัตโนมัติ 30+ เคส
- [x] **Risk Gate Validation**: ทดสอบตรรกะ Drawdown, Daily Loss, และ RRR Precision
- [x] **Bridge Serialization**: ทดสอบความแม่นยำของลำดับคิวคำสั่ง (FIFO Mutex)
- [x] **Connectivity Hardening**: แก้ไขปัญหา CORS สำหรับการเชื่อมต่อผ่าน ngrok/Mobile
- [x] **Stability Patch**: แก้ไข ReferenceErrors และจัดการ Global Utilities ให้เสถียร
- [x] **Symbol Resolver**: ระบบ Auto-mapping ชื่อ Symbol (BTCUSD -> XBTUSD) และค้นหาในคำอธิบาย
- [x] **JSON Unwrapper**: แก้ปัญหา Double-wrapped JSON ทำให้แอปมือถืออ่านข้อมูลได้แม่นยำ

### Wave 3 — Architecture Refactor (Next Step)
- [ ] **Decoupling**: แยก `AutoTradingService` ออกเป็นหน่วยย่อย (PhaseRunner, MarketGuard)
- [ ] **Dependency Injection**: ใช้ระบบ DI เพื่อให้การขยายตัวและทดสอบทำได้ง่ายขึ้น
- [ ] **Config Hot-Reload**: ปรับค่าความเสี่ยงได้โดยไม่ต้องหยุดการทำงานของ Engine

---

## 4. Key Power Endpoints

### ⚡ `/api/mt5/analyze` (The Intelligence Core)
Endpoint นี้คือหัวใจของหมวด **MT5 Bridge Advanced Tools** บนมือถือ:
- **Capabilities**: คำนวณ Technical Indicators (RSI, ATR, MACD, EMA) และ AI Reasoning (Regime, Bias, Confluence) ในระดับ Server-side
- **Mobile Integration**: แอปมือถือใช้ Endpoint นี้เพื่อขับเคลื่อน:
    - `Market Scanner`: สแกนหาโอกาสเทรดจากหลาย Symbol
    - `Correlation Radar`: วิเคราะห์ความสัมพันธ์ระหว่างคู่เงิน
    - `Institutional Flow`: แกะรอยรายใหญ่จาก Price-Volume
    - `Sentiment Gauge`: วัดสุขภาพพอร์ตจากข้อมูลดิบ

---

### 🛠 Premium Patches (Stable v2.0)
- **Smart Symbol Resolver**: `mt5_bridge.py` ค้นหา Symbol จาก Description และ Alias อัตโนมัติ
- **Resilient Parsing**: `TradingToolExecutor.kt` รองรับโครงสร้าง JSON ซับซ้อนจาก Server
- **Retry Logic**: เพิ่มระบบ Retry 3 รอบเมื่อ MT5 ตอบสนองไม่ทัน (Terminal: Call failed)

---

*อัปเดตเอกสารนี้เมื่อทำแต่ละ wave จบ — บันทึกความสำเร็จและบทเรียน*
