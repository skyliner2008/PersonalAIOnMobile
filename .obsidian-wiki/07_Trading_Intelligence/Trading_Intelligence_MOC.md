# Trading Intelligence MOC

Map of Content สำหรับสาย `Trading Intelligence` ของ JARVIS  
ใช้เพื่อพาอ่านตั้งแต่แนวคิดตลาด ไปจนถึง implementation ล่าสุดด้าน `Auto Trading V20.0`

> อัพเดท: 2026-05-26

---

## 🛤️ เส้นทางอ่านหลัก

### 1️⃣ Foundation — แนวคิดพื้นฐาน
- [[00_Tool_to_Strategy_Map]] — ดูว่าแต่ละ tool สนับสนุนกลยุทธ์แบบใด
- [[01_Market_Cycles_Wyckoff]] — โครงสร้างวัฏจักรตลาด
- [[02_Institutional_Mechanics_ICT]] — มุมมองสภาพคล่องและกลไกฝั่งสถาบัน
- [[03_Execution_SMC_V10]] — Execution logic แบบ Smart Money Concepts

### 2️⃣ Analysis Layers — ชั้นวิเคราะห์
- [[04_Momentum_and_Scanners]] — Momentum, scan, trigger discovery
- [[05_Sentiment_Analysis_Logic]] — Sentiment และบริบทข่าว
- [[06_The_Ultimate_Checklist_V12.5]] — Checklist เชิงปฏิบัติ
- [[07_Advanced_Analysis_V12.5]] — Deep analysis และการรวม confluence

### 3️⃣ Modern Trading Stack — ระบบเทรดสมัยใหม่
- [[08_Universal_Trading_Unity_V14.4]] — การรวม price source และ analysis layer
- [[09_TradingView_Continuity_V15.0]] — Native bridge และ continuity ฝั่ง TradingView
- [[10_Global_Insights_V16.0]] — Modern technical + macro + global intelligence
- [[11_MT5_Full_Agent_Control_V17]] — Broker-first architecture และ full agent control

### 4️⃣ Auto Trading Engine — เทรดอัตโนมัติ
- [[12_AutoTrading_Remote_Engine]] — AutoTrading Remote Engine design
- [[13_AI_Pro_Trader_Roadmap]] — AI Pro Trader Roadmap
- [[15_Cluster_Trading_Logic_V17.2]] — ⭐ Cluster Trading, Risk Management, BE/Trail logic
- [[16_Complete_Code_Logic_V19.6]] — ⭐ Complete Code Logic — source of truth

### 5️⃣ V20.0 Architecture — สถาปัตยกรรมล่าสุด
- [[17_PerAgent_ModelRanking_V20]] — ⭐ Per-Agent Model Ranking, Time-Based Blacklist, Smart Fallback
- [[18_V20_Agent_Coordination_Audit]] — ⭐ ตรวจสอบ Flow ทั้งระบบ, Agent Interaction Matrix
- [[Smart_Fallback_and_Token_Management]] — Smart Free Fallback, Token Cost Optimization

### 6️⃣ V21–V25 — Tactical Layer ปัจจุบัน
- [[21_PriceMap_V211]] — MTF Wall Map + Path Analysis
- [[22_IndicatorConfluence_V220]] — Active Indicator Decision Layer
- [[23_FadeTheLevel_V240]] — Proximity Gate + Sequential Entry (กำลังจะถูก replace)
- [[25_RealTimeWallEngine_V25]] — ⭐ **Tick-Level Watchtower (DESIGN, 2026-05-10)**
- [[25_TradeFlow_Diagrams]] — ASCII flowcharts ของ V25
- [[34_ZoneAwareGate_MTF_LocalOverride_V264]] - V26.4 Zone-Aware Gate MTF local execution override
- [[35_StaleStop_RsiDivergence_V265]] - V26.5 stale-stop guard and RSI divergence scalp signal
- [[36_Scalping_Routing_V266]] - V26.6 executable SCALPING routing and scalp-aware fallback RRR
- [[37_Scalp_TpSl_Bracket_V267]] - V26.7 tighter SL and better reward floor for scalp brackets
- [[38_SmcFvgScalp_ProofGate_V268]] - V26.8 requires real FVG fill proof for SMC_FVG_SCALP
- [[39_WallFvgMagnetScalp_V269]] - V26.9 wall reclaim to unmitigated M15 FVG magnet scalp
- [[40_MeanReversion_OrderGuard_V2610]] - V26.10 caps MEAN_REVERSION TP and slows duplicate entries
- [[41_M15_ScalpPressureGuard_V2611]] - V26.11 blocks generic A_SCALPING into opposing M15 SMC pressure
- [[42_DecisionGate_AnalyticsObservability_V2612]] - V26.12 makes analyze gate/block reports readable
- [[43_PriceMap_WallAnchorStability_V2613]] - V26.13 keeps structural wall anchors stable while context adds confluence
- [[44_WallBreakScalp_FreshDecisionPrice_V2614]] - V26.14 adds M1+M5 wall-break scalp and fresh AutoEngine decision price
- [[47_BreakoutLocalConfirmationGuard_V2617]] - V26.17 waits on HTF breakout when M15/M5 oppose the side
- [[48_ExecutionAwareMtfWeighting_V2618]] - V26.18 reduces H4/H1 dominance and requires M15/M5 execution agreement
- [[49_UnifiedV25DecisionPath_V2619]] - V26.19 routes V25/PB candidates through the single AutoEngine execution path
- [[50_PriceMapExecutionTightening_V2620]] - V26.20 tightens final RRR, V25 freshness, breakout proximity, and early trade management
- [[51_EaOnlyExecutionHardening_V2621]] - V26.21 hardens EA-only entries with local M1/M5 guard, post-TP cooldown, MR proximity discipline, and live early invalidation
- [[52_PriceMapWallOverlayStability_V2622]] - V26.22 keeps near-touch walls visible and anchors PriceMap to structural TF overlay instead of tick/context drift
- [[53_EntryDriftSyncedDecisionPrice_V2623]] - V26.23 makes Entry Drift compare against the synced live decision price instead of stale candle close
- [[54_GateStrategyRuntimeControls_V2624]] - V26.24 Gate Strategy Runtime Controls and operator-level toggles
- [[55_UnifiedZoneExecutionQuality_V2625]] - V26.25 Unified Zone & Execution Quality Gate Simplification, Adaptive Weights, and Scalping TP Cap SL Scaling
- [[45_SlippageGuard_AnalyticsImprovement_V2615]] - V26.15 Entry Drift/Slippage Guard, UNKNOWN analytics fix, MEAN_REVERSION tuning, RANGE disable
- [[56_ModularCodebaseRefactoring_V2626]] - V26.26 Core AutoTradingService deep modular refactor and verification pass
- [[57_DeepDecoupling_AutoTradingService_V2627]] - V26.27 Sub-Engine Separation, modular run cycle downsizing, zero breaking changes delegator structure
- [[58_Scalping_FVG_Unlocking_V2630]] - V26.30 FVG-Fill minFillPct relaxation, Wall Break zone requirements, dynamic RRR Floors
- [[59_PriceMap_ProximitySuppression_V2631]] - V26.31 PriceMap proximity wall consolidation and institutional wall stars cap
- [[60_MultiScale_StrategyDecoupling_V2635]] - V26.35 Multi-scale strategy zone decoupling and HTF trend pullback entry override system
- [[61_TradingView_SMC_Pine_V83_Hardening]] - TradingView Pine SMC V8.5 M15 multi-TF wall map and MTF liquidity merge hardening
- [[62_M15_Wall_Scalping_Simplification]] - Simplified runtime: Pipeline/PriceMap only, M15 wall merge, SCALPING only, stable-wall display with stale-anchor execution block, M1/M5 recent-touch reclaim with at least one LTF micro-break confirmation, distance-first gate logging, simple same-day loss guard
- [[Strategy_Lab_TradingView]] - ⭐ External research harness: 8-strategy forensics → Unified SMC MTF engine (V1–V5), MT5 data pipeline, robust basin พร้อม forward test
- [[63_Unified_SMC_Watch_Mode_Plan]] - ⭐ WATCH-mode production plan ของ Unified SMC V5 basin (shadow P/L, ไม่แตะ order path)
- [[64_Unified_SMC_App_Integration]] - ⭐ Unified SMC V5 ฝังใน app มือถือเป็น engine หลัก (Kotlin port) — ตัด TR/DC/52H/E/UT/3BR เก็บ MOM+REV
- [[65_AI_Strategy_Supervisor]] - ⭐ AI Supervisor (APPROVE/VETO/ADJUST gate) + MarketContextDigest 5TF + M1 confirmation + keyzone watch — ใน app มือถือ

---

## 🔗 Cross-References

| หมวด | เอกสารที่เกี่ยว |
|------|----------------|
| **Architecture** | [[overview]] → [[04_AutoTrading_Workflow]] → [[multi_provider_architecture]] |
| **Tasks** | [[Current_Tasks]] → [[2026-04-21_Auto_Trading_Engine_Update]] |
| **Roadmap** | [[00_AI_Agent_Trader_Pro_Roadmap]] → [[02_AutoTrading_Smart_Upgrade_Plan_2026_04_25]] |
| **Mobile App** | [[Changelog_2026-05-01_Mobile_App_Complete]] → [[Mobile_App_Improvement_Plan_2026-04-30]] |
| **Tools** | [[catalogue]] → [[00_Tool_to_Strategy_Map]] |

---

## วิธีใช้ MOC นี้
- **เข้าใจแนวคิด**: เริ่มจาก `Wyckoff` → `ICT` → `SMC`
- **เข้าใจ tool**: เริ่มที่ [[00_Tool_to_Strategy_Map]] → [[catalogue]]
- **เข้าใจระบบเทรดล่าสุด**: เริ่มที่ [[16_Complete_Code_Logic_V19.6]] → [[17_PerAgent_ModelRanking_V20]]
- **ตรวจสอบ flow**: [[18_V20_Agent_Coordination_Audit]]

## จุดเปลี่ยนของแต่ละเวอร์ชัน
| Version | จุดเปลี่ยน |
|---------|-----------|
| `V14.4` | รวม framework การวิเคราะห์ให้เป็นระบบเดียว |
| `V15.0` | Continuity ของข้อมูลและ native bridge ฝั่ง TradingView |
| `V16.0` | Global insights, macro context, modern technical tools |
| `V17.0` | MT5 broker-first architecture |
| `V17.2` | Cluster Trading, Unified BE, Sequential Protection |
| `V19.6` | Complete Code Logic — deterministic + AI pipeline |
| `V20.0` | **Per-Agent Model Ranking, SMC SL/TP, Toxic Model Protection** |
| `V21.1` | PriceMap MTF aggregator + Path Analysis |
| `V22.0` | Indicator Confluence (active decision layer) |
| `V23.0` | EA-Only Mode toggle |
| `V24.x` | Fade-the-Level + Sequential Entry + Basket BE |
| `V25.0` | **Real-Time Wall Engine — tick + bar-close hybrid, Wall State Machine, 5 playbooks** |
| `V26.4` | Zone-Aware Gate uses H4/H1 as context and M30/M15/M5 as execution-zone override |
| `V26.5` | Stale-stop market-closed guard + RSI divergence signal |
| `V26.6` | Valid M5/M15 SCALPING can become executable top-level strategy; EA fallback uses scalpingRRR |
| `V26.7` | SMC scalp bracket tightens SL first and targets at least 1.35R |
| `V26.8` | SMC_FVG_SCALP requires real aligned FVG fill proof; generic sweeps are SCALPING |
| `V26.9` | SMC_FVG_MAGNET_SCALP trades wall reclaim toward unmitigated M15 FVG while bypassing HTF zone/context vetoes |
| `V26.10` | MEAN_REVERSION caps TP to 1.8R and uses 15-minute duplicate-intent TTL |
| `V26.11` | Generic A_SCALPING blocks BUY/SELL when M15 SMC pressure is opposite |
| `V26.12` | Analyze reports split Decision Side Guard and M15 SMC Pressure Guard into explicit block categories |
| `V26.13` | PriceMap context levels strengthen walls without moving structural wall anchors |
| `V26.14` | SMC_WALL_BREAK_SCALP uses M1+M5 break plus local PD zone; AutoEngine uses fresh tick/broker decision price |
| `V26.17` | BREAKOUT requires local M15/M5 confirmation when HTF side is in a wrong zone or locally opposed |
| `V26.18` | Deterministic scoring rebalances H4/H1/M30/M15/M5/M1 and blocks HTF-selected strategies when M15+M5 oppose |
| `V26.19` | V25/PB1 candidates are local-proof inputs to AutoEngine; `V25 Only` is no longer the default live execution path |
| `V26.20` | Final execution guard requires PriceMap-capped V25/scalp plans to keep executable RRR, PB1 candidates expire after 30s, H4 ranging breakouts keep Proximity Gate, and XAU uses early BE/invalidation management |
| `V26.21` | EA-only deterministic entries require higher confidence, M1/M5 local agreement, post-TP cooldown, MEAN_REVERSION wall proximity, live early invalidation, and stable per-ticket partial markers |
| `V26.22` | PriceMap keeps near-touch walls, uses structural TF weighted anchors, stabilizes resistance/support lists, and caps context-only overlap |
| `V26.23` | Entry Drift uses the synced live decision price, preventing false drift blocks caused by stale candle close after wall-touch setups |
| `V26.24` | **Gate Strategy Runtime Controls**: Added adaptive.gateToggles and strategyToggles with deep-merge support and Auto Trade UI settings |
| `V26.25` | **Unified Zone & Execution Quality & Performance Fix**: Simplifies 21 gates to principal ones, adds regime-adaptive scoring, scales SL proportional to TP structural caps, and optimizes scalping Trade Management |
| `V26.26` | **Modular Codebase Refactoring**: Refactored core autoTradingService.ts to extract helper functions, market checkers, and closed trades AI review into modular helper files |
| `V26.27` | **Deep Decoupling**: Separates massive run methods into MarketCycleEngine, PositionManagerEngine, and LearningEngine, downsizing coordinator by 85% with zero behavior changes |
| `V26.30` | **Scalping & FVG Optimization**: Relaxes Wall Break zone requirement, reduces FVG minFillPct to 15% for fast fills, implements dynamic RRR floor, and bypasses M15 Pressure Guard for high-confluence scalps |
| `V26.31` | **PriceMap Proximity Suppression & Stars Cap**: Consolidates parallel walls within 0.75 * ATR to reduce clutter, and caps context-only walls at 3★ to avoid false high-star anchors |
| `V26.35` | **Multi-Scale Strategy Decoupling & HTF Pullback**: Bypasses major HTF zone checks for LTF scalps, and unlocks pullback overrides for TREND_FOLLOW when local execution TF pulls back to favorable zones |
| `TV-SMC V8.5` | M15-first TradingView wall map: tighter MTF liquidity merge, 5 levels per TF, and TF-count stars for overlapping M1/M5/M15/M30/H1/H4 walls |
| `V26.36` | M15 Wall Scalping simple-mode closed MT5 history sync, broker-fill R calculation, stable wall registry, HTF wall/LTF confirmation split, M15 FVG magnet playbook, mitigated-OB wall score hardening, counter-context scalp guard, ambiguous BOTH wall guard, and 2026-06-01 loss audit notes |
| `V26.15` | **Entry Drift/Slippage Guard blocks degraded RRR from price drift; UNKNOWN block_category fix (1,134→MARKET_CLOSED); MEAN_REVERSION maxRrr 1.8→1.5; RANGE strategy disabled** |
| `V26.16` | **Logging Observability Audit**: Added `peakProfitR`/`maxDrawdownR` tracking, structured `zone_aware_gate` traces, precise slippage/RRR comparison, and standalone `gate_analytics.mjs` diagnostic script. |

---

## Related Notes
- [[index]] — Wiki home
- [[catalogue]] — Tool catalogue
- [[Current_Tasks]] — งานปัจจุบัน
- [[overview]] — Architecture

**Links**: [[index]] | [[catalogue]] | [[11_MT5_Full_Agent_Control_V17]] | [[Current_Tasks]]
