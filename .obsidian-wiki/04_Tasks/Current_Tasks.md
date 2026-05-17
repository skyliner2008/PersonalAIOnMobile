# Current Tasks

สถานะงานของโปรเจค `JARVIS / PersonalAIBot` ณ วันที่ **2026-05-01**

---

## 🟢 Active — กำลังดำเนินการ

### Auto Trading Engine (V20.0)
- [x] Per-Agent Model Ranking — แต่ละ agent มี ranking แยก
- [x] Smart Fallback Chain — auto rotate เมื่อ model fail/429
- [x] Toxic Model Protection — streak≥5 ไม่ force-reset
- [x] Hard Risk Gate — float-safe RRR compare + tick buffer
- [x] slTpAgent Dashboard Settings — เพิ่มใน UI
- [x] Fallback → Settings auto-persist — Dashboard สะท้อนทันที
- [x] modelUsed format fix — ไม่มี provider prefix ปน
- [x] `recordSlTpAccuracy()` — feedback loop สำหรับ SL/TP accuracy
- [ ] Monitor Ranker Health — สังเกต model scoring หลัง deploy

### Mobile App
- [x] Trading Terminal Screen — real-time chart + positions
- [x] AI Dashboard — settings, agent model selection
- [x] Edge-to-edge display support
- [ ] Push notification สำหรับ trade executed/closed
- [ ] Offline mode — cached data when server unreachable

### mt5-core-server
- [x] Multi-Agent Pipeline — Analyst → RiskOfficer → ExecutionTrader → slTpAgent
- [x] Circuit Breaker — equity drawdown guard
- [x] Sequential Protection — block new trades เมื่อ last trade ไม่ safe
- [x] News Intelligence — HIGH impact event blocking
- [x] WebSocket push to mobile — real-time position updates
- [ ] Backtest mode — replay historical data through pipeline

---

## ✅ Recently Completed (2026-04-30 → 2026-05-01)
- [x] แก้ dispatcher.ts duplicate code block
- [x] TP rescue rounding bug (RRR 1.4999 → 1.50)
- [x] slTpAgent inherit reasoning model (prevent 403)
- [x] Toxic Model Protection implementation
- [x] Agent Coordination Audit — [[18_V20_Agent_Coordination_Audit]]
- [x] Obsidian Wiki restructure — V20.0 docs + MOC update
- [x] Mobile App Improvement Plan — [[Mobile_App_Improvement_Plan_2026-04-30]]
- [x] Mobile App Complete Changelog — [[Changelog_2026-05-01_Mobile_App_Complete]]
- [x] แก้ไขบั๊ก Compile Error ของ Compose App (SettingsDialog, TradingTerminalScreen, BrokerSymbolCache, Mt5LocalCache) (2026-05-02)
- [x] **Phase 5.2 — API Provider Connectivity Fix** (2026-05-02) — [[Phase_5.2_API_Provider_Connectivity_Fix_2026-05-02]]
  - แก้ Gemini ไม่แสดงรายการโมเดล (listModels ไม่ใช้ apiKey parameter)
  - แก้ OpenRouter/OpenAI/Claude/LiteLLM SSE streaming (byte-by-byte → preparePost+readUTF8Line)
  - เพิ่ม temp provider fallback ใน LlmProviderRegistry สำหรับ Settings UI
  - เพิ่ม apiKeyOverride flow ทั้ง chain (SettingsDialog → ViewModel → Orchestrator → Registry)
- [x] **Phase 6.1 — Implement recordSlTpAccuracy()** (2026-05-15)
  - เพิ่ม logic การให้คะแนน slTpAgent แบบละเอียด (TP Bullseye, Profit achieved, Tight SL punishment, BE reward)
  - เพิ่ม logging สำหรับ accuracy update ใน detectAndReviewClosedTrades
- [x] **Phase 6.2 — WebSocket Push Optimization** (2026-05-15)
  - เพิ่ม `triggerImmediatePush()` ใน `mt5RealtimeHub`
  - เชื่อมต่อ `invalidateSnapshots()` ใน `MarketDataService` ให้ trigger push ทันทีหลังเปิด/ปิด/แก้ไข trade
  - ช่วยให้ Mobile App เห็นการเปลี่ยนแปลงของ Position แบบ Real-time ทันทีไม่ต้องรอ 1s loop

---

## ⚠️ Current Risks
- [ ] OpenRouter free tier rate limits (429) — ใช้ Smart Fallback chain จัดการ
- [ ] DB model_id อาจมี legacy rows ผิด format — cleanup script พร้อมใช้
- [x] `sl_tp_score` — implement แล้วใน Phase 6.1
- [ ] Bridge Python process อาจ crash silently — need health check ping

---

## 🎯 Suggested Next Focus
1. **Backtest mode** — replay historical candles through pipeline
2. **Dashboard analytics** — model performance chart, win rate by agent
3. **Obsidian Wiki** — อัพเดทเมื่อมีการเปลี่ยนแปลงสำคัญ

---

## 📎 Related Docs
- [[index]] — Wiki home
- [[Trading_Intelligence_MOC]] — เส้นทางอ่านสาย Trading
- [[17_PerAgent_ModelRanking_V20]] — V20.0 spec
- [[18_V20_Agent_Coordination_Audit]] — Agent audit
- [[00_AI_Agent_Trader_Pro_Roadmap]] — Roadmap หลัก
- [[Changelog_2026-05-01_Mobile_App_Complete]] — Mobile changelog
