# JARVIS Wiki Index

ศูนย์รวมเอกสารของโปรเจค `PersonalAIBot (JARVIS)` สำหรับใช้งานใน Obsidian โดยจัดโครงสร้างให้ค้นหาง่าย อ่านต่อได้เป็นเส้นทาง และใช้เป็น knowledge base สำหรับทั้งการพัฒนาแอปและระบบวิเคราะห์การเทรด

## เป้าหมายของ Wiki
- เก็บภาพรวมสถาปัตยกรรม ระบบความจำ เครื่องมือ และ workflow ของโปรเจค
- อธิบายความสามารถจริงที่มีอยู่ในโค้ด ณ เวลาปัจจุบัน
- เชื่อมเอกสารเชิงแนวคิดกับ implementation ที่ใช้งานจริง
- รองรับการต่อยอดด้าน `MT5`, `Trading Intelligence`, `Auto Trading`, และ `Gemini Live`

## Sitemap

### 00 System
- [[index]] - จุดเริ่มต้นของ wiki
- [[log]] - บันทึกการเปลี่ยนแปลงและ timeline สำคัญ
- [[schema]] - โครงสร้างและแนวทางการจัดหมวดหมู่เอกสาร
- [[prompt]] - บริบทและแนวทาง prompt/system behavior

### 01 Architecture
- [[overview]] - ภาพรวมสถาปัตยกรรมของแอปและ service หลัก
- [[memory_strategy]] - แนวทางจัดการ memory หลายชั้น
- [[vision_system]] - ระบบภาพ กล้อง และ provider ของ vision

### 02 Components
- [[GeminiService]] - การเชื่อมต่อโมเดลหลักและ orchestration
- [[LiveGeminiService]] - live session และการสื่อสารแบบ real-time
- [[LiveToolBridge]] - bridge ระหว่าง live interaction กับ tools
- [[JarvisViewModel]] - state orchestration ฝั่ง UI

### 03 Tools
- [[catalogue]] - สารบัญเครื่องมือทั้งหมดที่เปิดให้ agent ใช้งาน

### 04 Tasks
- [[Current_Tasks]] - งานปัจจุบัน สถานะ และ roadmap
- [[2026-04-21_Auto_Trading_Engine_Update]] - สรุปอัปเดตล่าสุดของ MT5 และ auto-trading engine

### 05 Android Skills
- [[agp-9-upgrade]]
- [[edge-to-edge]]
- [[migrate-xml-views-to-jetpack-compose]]
- [[navigation-3]]
- [[play-billing-library-version-upgrade]]
- [[r8-analyzer]]

### 06 Gemini Skills
- [[gemini-api-dev]]
- [[gemini-live-api-dev]]

### 07 Trading Intelligence
- [[Trading_Intelligence_MOC]] - จุดเริ่มอ่านเอกสารสายเทรด
- [[00_Tool_to_Strategy_Map]] - จับคู่ tool กับแนวคิดเชิงกลยุทธ์
- [[01_Market_Cycles_Wyckoff]]
- [[02_Institutional_Mechanics_ICT]]
- [[03_Execution_SMC_V10]]
- [[04_Momentum_and_Scanners]]
- [[05_Sentiment_Analysis_Logic]]
- [[06_The_Ultimate_Checklist_V12.5]]
- [[07_Advanced_Analysis_V12.5]]
- [[08_Universal_Trading_Unity_V14.4]]
- [[09_TradingView_Continuity_V15.0]]
- [[10_Global_Insights_V16.0]]
- [[11_MT5_Full_Agent_Control_V17]]

### 08 Charts Hub
- [[Knowledge_Hub]] - เอกสารอ้างอิง Lightweight Charts

## Suggested Reading Paths

### ถ้าต้องการเข้าใจระบบทั้งโปรเจค
1. [[overview]]
2. [[JarvisViewModel]]
3. [[GeminiService]]
4. [[catalogue]]

### ถ้าต้องการเข้าใจระบบเทรด
1. [[Trading_Intelligence_MOC]]
2. [[00_Tool_to_Strategy_Map]]
3. [[10_Global_Insights_V16.0]]
4. [[11_MT5_Full_Agent_Control_V17]]
5. [[2026-04-21_Auto_Trading_Engine_Update]]

### ถ้าต้องการเข้าใจ memory และ wiki strategy
1. [[memory_strategy]]
2. [[schema]]
3. [[log]]

## สถานะปัจจุบัน
- Wiki กำลังถูกปรับให้สะท้อนโค้ดล่าสุดของ `ToolRegistry`, `TradingToolDefinitions`, `SmcToolDefinitions` และระบบ `MT5`
- หมวด `obsidian-wiki` ใช้เป็นแหล่งอ้างอิงระยะยาวของ agent และทีมพัฒนา
- เอกสารบางส่วนเก่ายังคงเก็บไว้เพื่อรักษาประวัติแนวคิด แต่ควรอ้างอิงไฟล์เวอร์ชันล่าสุดเป็นหลัก

**Links**: [[catalogue]] | [[overview]] | [[Trading_Intelligence_MOC]] | [[Current_Tasks]]
