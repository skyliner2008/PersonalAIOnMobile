# JARVIS Wiki Index

ศูนย์รวมเอกสารของโปรเจค `PersonalAIBot (JARVIS)` — Professional AI Trading Platform แบบ Self-Hosted บน Android/iOS  
ใช้เป็น **สมองส่วนนอก (External Brain)** สำหรับทั้งการพัฒนาแอปและระบบเทรดอัตโนมัติ

> **อัพเดทล่าสุด**: 2026-05-23 — V26.27 Deep Decoupling of AutoTradingService (Sub-Engines)

---

## 🗂️ Sitemap

### 00 System — ระบบเอกสาร
| ไฟล์ | หน้าที่ |
|------|--------|
| [[index]] | จุดเริ่มต้นของ wiki (หน้านี้) |
| [[log]] | บันทึกการเปลี่ยนแปลงและ timeline สำคัญ |
| [[schema]] | โครงสร้างและแนวทางการจัดหมวดหมู่เอกสาร |
| [[prompt]] | บริบทและแนวทาง prompt/system behavior |

### 01 Architecture — สถาปัตยกรรม
| ไฟล์ | หน้าที่ |
|------|--------|
| [[overview]] | ภาพรวมสถาปัตยกรรมแอป (Android + mt5-core-server) |
| [[04_AutoTrading_Workflow]] | Workflow ของ Auto Trading Engine |
| [[multi_provider_architecture]] | Multi-Provider AI Architecture (OpenRouter, Gemini, Ollama) |
| [[memory_strategy]] | แนวทางจัดการ memory หลายชั้น (Vector, Graph, Episodic) |
| [[vision_system]] | ระบบภาพ กล้อง และ provider ของ vision |

### 02 Components — ส่วนประกอบหลัก
| ไฟล์ | หน้าที่ |
|------|--------|
| [[GeminiService]] | การเชื่อมต่อโมเดลหลักและ orchestration |
| [[LiveGeminiService]] | Live session และการสื่อสารแบบ real-time |
| [[LiveToolBridge]] | Bridge ระหว่าง live interaction กับ tools |
| [[JarvisViewModel]] | State orchestration ฝั่ง UI |
| [[LocalEmbeddingSystem]] | Embedding ฝั่ง local สำหรับ vector memory |

### 03 Tools — เครื่องมือ
| ไฟล์ | หน้าที่ |
|------|--------|
| [[catalogue]] | สารบัญเครื่องมือทั้งหมดที่เปิดให้ agent ใช้งาน (75+ tools) |

### 04 Tasks — งานและสถานะ
| ไฟล์ | หน้าที่ |
|------|--------|
| [[Current_Tasks]] | งานปัจจุบัน สถานะ และ next steps |
| [[2026-04-21_Auto_Trading_Engine_Update]] | สรุปอัปเดต MT5 และ auto-trading engine |
| [[Changelog_2026-05-01_Mobile_App_Complete]] | Changelog มือถือ — Trading Terminal, Dashboard |
| [[Mobile_App_Improvement_Plan_2026-04-30]] | แผนปรับปรุง Mobile App |

### 05 Android Skills — ทักษะ Android
- [[agp-9-upgrade]] | [[edge-to-edge]] | [[gradle-troubleshooting]]
- [[migrate-xml-views-to-jetpack-compose]] | [[navigation-3]]
- [[play-billing-library-version-upgrade]] | [[r8-analyzer]]

### 06 Gemini Skills — ทักษะ Gemini API
- [[gemini-api-dev]] — Gemini API Development Guide
- [[gemini-live-api-dev]] — Gemini Live API (WebSocket, Streaming)

### 07 Trading Intelligence — ระบบวิเคราะห์การเทรด
> 📚 เริ่มจาก [[Trading_Intelligence_MOC]] เพื่อดูเส้นทางอ่านทั้งหมด

**Foundation**: [[01_Market_Cycles_Wyckoff]] → [[02_Institutional_Mechanics_ICT]] → [[03_Execution_SMC_V10]]

**Analysis**: [[04_Momentum_and_Scanners]] → [[05_Sentiment_Analysis_Logic]] → [[06_The_Ultimate_Checklist_V12.5]] → [[07_Advanced_Analysis_V12.5]]

**Modern Stack**: [[08_Universal_Trading_Unity_V14.4]] → [[10_Global_Insights_V16.0]] → [[11_MT5_Full_Agent_Control_V17]]

**V20.0 Architecture**:
- [[17_PerAgent_ModelRanking_V20]] — Per-Agent Model Ranking, Blacklist, Smart Fallback
- [[18_V20_Agent_Coordination_Audit]] — Agent Flow, Interaction Matrix, ปัญหาที่พบ
- [[Smart_Fallback_and_Token_Management]] — Token Cost Optimization

**Modularization & Decoupling**:
- [[55_UnifiedZoneExecutionQuality_V2625]] — การบูรณาการลดรูปเกตควบคุม (Gate Simplification) และปรับปรุงประสิทธิภาพการกรองออเดอร์
- [[56_ModularCodebaseRefactoring_V2626]] — แผนภาพโมดูลย่อยและการย้าย Helpers รอบแรก
- [[57_DeepDecoupling_AutoTradingService_V2627]] — การแยก 4 เมธอดขนาดยักษ์เป็น Sub-Engines เพื่อประสิทธิภาพและความปลอดภัยสูง

### 08 Charts Hub
- เอกสารอ้างอิง Lightweight Charts API

### 09 Roadmap — แผนพัฒนา
| ไฟล์ | หน้าที่ |
|------|--------|
| [[00_AI_Agent_Trader_Pro_Roadmap]] | Roadmap หลักของ AI Trading Platform |
| [[01_mt5_core_server_plan]] | แผนพัฒนา mt5-core-server |
| [[04_Pro_AI_Trader_Roadmap]] | Pro AI Trader Roadmap |
| [[02_AutoTrading_Smart_Upgrade_Plan_2026_04_25]] | แผนอัพเกรด Smart Trading |
| [[03_AutoTrading_Implementation_Audit_2026_04_25]] | Audit ผลการ implement |

---

## 🧭 Suggested Reading Paths

### 🏗️ เข้าใจระบบทั้งโปรเจค
1. [[overview]] → [[multi_provider_architecture]]
2. [[JarvisViewModel]] → [[GeminiService]]
3. [[catalogue]] → [[00_Tool_to_Strategy_Map]]
4. [[Current_Tasks]]

### 📈 เข้าใจระบบเทรดอัตโนมัติ (สำคัญที่สุด)
1. [[Trading_Intelligence_MOC]] — จุดเริ่มต้น
2. [[15_Cluster_Trading_Logic_V17.2]] — Cluster Trading + Risk Management
3. [[16_Complete_Code_Logic_V19.6]] — Code Logic ทั้งระบบ
4. [[17_PerAgent_ModelRanking_V20]] — Per-Agent Model Ranking
5. [[18_V20_Agent_Coordination_Audit]] — Agent Coordination Audit
6. [[57_DeepDecoupling_AutoTradingService_V2627]] — สถาปัตยกรรมแบบ Modular Sub-Engines

### 🧠 เข้าใจ Memory และ AI
1. [[memory_strategy]] → [[LocalEmbeddingSystem]]
2. [[multi_provider_architecture]]
3. [[Smart_Fallback_and_Token_Management]]

### 📱 เข้าใจ Mobile App
1. [[Mobile_App_Improvement_Plan_2026-04-30]]
2. [[Changelog_2026-05-01_Mobile_App_Complete]]
3. [[05_Android_Skills]] — ทักษะ Android ที่ใช้

---

## สถานะปัจจุบัน
- **V20.0** — Per-Agent Model Ranking เสถียร, Smart Fallback ทำงาน 24/7
- **Auto Trading Engine** — Production mode, 8+ positions managed
- **Mobile App** — Trading Terminal, AI Dashboard พร้อมใช้
- **mt5-core-server** — Node.js server เชื่อม MT5 Python Bridge

**Links**: [[catalogue]] | [[overview]] | [[Trading_Intelligence_MOC]] | [[Current_Tasks]] | [[00_AI_Agent_Trader_Pro_Roadmap]]
