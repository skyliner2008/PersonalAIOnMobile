---
name: AutoTrading Modular Build 2026-04-21
description: ระบบ Auto-Trading แบบ modular (Analyzers × Strategies × RiskManager × TradeJournal) ครบ engine/VM/persistence/UI
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
เสร็จสิ้นการรื้อ Auto-Trading เป็น modular ครบ layer:

**Engine** `tools/trading/auto/AutoTradingEngine.kt` — 6-phase loop (analyze→strategize→execute→manage→record→learn) ใช้ `AnalyzerRegistry.defaults` + `StrategyPicker.pickAndPropose()` + `RiskManager.gate()` + `TradeJournalStore.recordDecision/updateOutcome`. รองรับ paper/live toggle, BE/trail, regime detection, auto-tune minConfluence, auto-blacklist after N losses.

**ViewModel** `tools/trading/auto/AutoTradingViewModel.kt` — wrap engine, expose StateFlow (config/state/lastDecisions/lastComposite/learnSummary/openJournal), persist-on-change ผ่าน `AutoTradingConfigStore` (SQLDelight AppSetting key prefix `auto_trading.*`).

**UI** `ui/screen/AutoTradingScreen.kt` — `AutoTradingPanel(vm)` LazyColumn: Hero (Start/Stop/RunOnce/LearnNow) + LiveToggle + WatchlistEditor + StrategyChipRow + ThresholdSection + ManageSection + BlacklistEditor + DecisionList + OpenList + LearnCard.

**Wiring**:
- `JarvisViewModel.autoTrading = AutoTradingViewModel(viewModelScope)`
- `TradingTerminalScreen` เพิ่ม `Mt5Tab.Auto` + parameter `autoTrading: AutoTradingViewModel? = null`
- `App.kt` line ~304 ส่ง `autoTrading = viewModel.autoTrading`

**Why**: ตอบโจทย์ Jarvis ที่ผู้ใช้ต้องการ — Enhancing Custom Indicators (10 analyzers), Diverse Strategies (Scalp/Swing/Grid/Trail), Learn จาก trade_journal, Money Management/Drawdown protection

**How to apply**: แก้ engine behaviour → `auto/AutoTradingEngine.kt`. เพิ่ม config knob → ต้องเพิ่มทั้ง Config data class + ConfigStore save/load + ViewModel setter + UI field. Deprecated เก่า `tools/trading/AutoTradingEngine.kt` เหลือ stub ห้าม import
