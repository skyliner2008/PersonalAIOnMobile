# ⬡ บันทึกการเปลี่ยนแปลง (JARVIS Wiki Log) ⬡

บันทึกเหตุการณ์และการเปลี่ยนแปลงสำคัญของโปรเจคในรูปแบบ Chronological

## [2026-04-15] 🚀 เริ่มต้นระบบ Obsidian LLM-Wiki
- **Action**: ติดตั้งโครงสร้างไดเรกทอรีพื้นฐาน `.obsidian-wiki/`
- **Result**: สร้างหน้า `index.md`, `log.md`, และ `schema.md` สำเร็จ
- **Context**: เริ่มเปลี่ยนจากการใช้ไฟล์คู่มือแบบ Single-file (`jarvis_vision_manual.md`) มาเป็นระบบ Wikibase กระจายศูนย์ตามแนวคิดของ Karpathy

## [2026-04-15] 📥 Ingest Android Skills from GitHub
- **Action**: ดึงข้อมูลจาก `android/skills` และกระจายลงในโฟลเดอร์ `05_Android_Skills/`
- **Result**: เพิ่มหน้ารวม 6 Skills สำคัญ (AGP 9, Compose Migration, Navigation 3, R8, Play Billing, Edge-to-Edge)
- **Context**: ยกระดับความรู้ในระบบให้เป็นมาตรฐานล่าสุดของ Google เพื่อลดความเสี่ยงในการเขียนโค้ดที่ผิดพลาด


## [2026-04-15] 🌌 Ingest Gemini Skills from GitHub
- **Action**: ดึงข้อมูลจาก `google-gemini/gemini-skills` และติดตั้งใน `06_Gemini_Skills/`
- **Result**: เพิ่ม 2 Skills สำคัญ: `gemini-api-dev` และ `gemini-live-api-dev` (โมเดลตระกูล 3.1)
- **Context**: ยกระดับระบบ JARVIS ให้รองรับ SDK ล่าสุด และมาตรฐาน Gemini 3.1 เพื่อประสิทธิภาพการตอบสนองที่ล้ำสมัยที่สุด


## [2026-04-15] 📈 Upgrade to Trading Intelligence V10.0 (The Bible)
- **Action**: บูรณาการเครื่องมือเทรดทั้ง 18 ชนิด เข้ากับกลยุทธ์ Wyckoff, ICT และ SMC V10
- **Result**: สร้าง 7 ไฟล์ความรู้ใหม่ในหมวด `07_Trading_Intelligence/` พร้อม Checklist SOP
- **Context**: ยกระดับ JARVIS จากผู้ช่วยทั่วไปสู่ "นักวิเคราะห์สถาบัน" ที่ใช้กระบวนการวิเคราะห์แบบ Top-Down ขั้นสูง

---
> [!TIP]
> รูปแบบการบันทึก: `## [YYYY-MM-DD] | ประเภทกิจกรรม | หัวข้อเรื่อง`

## [2026-04-16] Fix: Wiki-guided Stability Hardening
- **Action**: Reviewed `.obsidian-wiki` architecture/component notes (`01_Architecture`, `02_Components`) and aligned code with the documented routing + persistence intent.
- **Result**: Patched Tool lifecycle so System diagnostics receives the latest side-effect delegate (prevents stale/null delegate after init order changes).
- **Result**: Added safety guards for file mutations (`file_delete`, `file_move`) to block unsafe root-level or out-of-scope path operations.
- **Context**: Based on `Architecture_Overview` (dual-path reliability) and `Memory_Strategy` emphasis on safe persistence behavior.

## [2026-04-16] Knowledge Base: Lightweight Charts Docs Ingested
- **Action**: Curated `.obsidian-wiki/08_lightweight-charts-docs-api` into a reusable knowledge hub.
- **Result**: Added `Knowledge_Hub.md` with integration rules, wrapper strategy, and implementation checklist.
- **Context**: This hub is now the reference for chart integration decisions in PersonalAIBot.
