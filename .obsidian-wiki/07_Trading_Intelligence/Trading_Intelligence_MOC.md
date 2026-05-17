# Trading Intelligence MOC

Map of Content สำหรับสาย `Trading Intelligence` ของ JARVIS  
ใช้เพื่อพาอ่านตั้งแต่แนวคิดตลาด ไปจนถึง implementation ล่าสุดด้าน `Auto Trading V20.0`

> อัพเดท: 2026-05-01

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

---

## Related Notes
- [[index]] — Wiki home
- [[catalogue]] — Tool catalogue
- [[Current_Tasks]] — งานปัจจุบัน
- [[overview]] — Architecture

**Links**: [[index]] | [[catalogue]] | [[11_MT5_Full_Agent_Control_V17]] | [[Current_Tasks]]
