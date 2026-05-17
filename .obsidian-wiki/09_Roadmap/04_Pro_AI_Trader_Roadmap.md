# 🚀 แผนยกระดับระบบ JARVIS สู่ Pro AI Agent Trader
*วันที่อัปเดต: 2026-04-25*

เอกสารนี้สรุปข้อบกพร่องที่พบจากการตรวจสอบระบบ (Audit) และแผนการพัฒนา (Roadmap) เพื่อยกระดับ PersonalAIBot ไปสู่ระบบเทรดอัตโนมัติระดับสถาบัน (Institutional Grade) แบบเต็มรูปแบบ

---

## 🐛 1. Critical Bug Fixes (ปัญหาที่ต้องแก้ไขด่วน)
ปัญหาเหล่านี้อยู่ใน `mt5_bridge.py` ซึ่งอาจทำให้คำสั่งเทรดล้มเหลวหรือถูก Broker แบนบัญชีได้

- [ ] **แก้ปัญหา Float Comparison ใน `modify_position`**
  - **ปัญหา:** การใช้ `if new_sl == cur_sl and new_tp == cur_tp:` ทำให้เกิดปัญหา Precision และเกิดการส่งคำสั่ง Modify ซ้ำซาก (Spamming)
  - **วิธีแก้:** เปลี่ยนไปใช้ `math.isclose(new_sl, cur_sl, rel_tol=1e-5)` ใน Python แทน
- [ ] **เพิ่ม Volume Clamping ใน `close_position`**
  - **ปัญหา:** หาก AI สั่ง `volume_override` สูงกว่า Position จริงที่ถืออยู่ คำสั่งจะถูกปัดตกทันที (Invalid Volume) ทำให้ระบบแก้พอร์ตพัง
  - **วิธีแก้:** จำกัดเพดาน Volume อัตโนมัติ: `volume = min(float(volume_override), float(pos["volume"]))`
- [ ] **ยกเลิก Hardcoded Deviation**
  - **ปัญหา:** ค่า `"deviation": 20` ถูกล็อคตายตัว ซึ่งไม่ยืดหยุ่นสำหรับคู่เงินที่ต่างกัน (เช่น Gold vs Forex ธรรมดา)
  - **วิธีแก้:** คำนวณค่า Deviation แบบไดนามิกจาก Node.js โดยอิงจากความผันผวน (ATR) หรือ Contract size ของแต่ละ Symbol แล้วส่งเป็น Payload

---

## ⚠️ 2. Architectural Bottlenecks (การปรับปรุงโครงสร้างเพื่อประสิทธิภาพ)
แก้ไขคอขวดที่ทำให้ระบบทำงานช้าลงเมื่อต้องรองรับข้อมูลปริมาณมาก

- [ ] **ทำ Bridge Batching (Node.js ➔ Python)**
  - **ปัญหา:** Queue ใน `bridgeClient.ts` (`QUEUE_MAX_WAIT_MS = 60_000`) ทำให้เกิดคอขวดเมื่อ Market Scanner ดึงข้อมูลหลายคู่เงินพร้อมกัน
  - **วิธีแก้:** สร้าง Endpoint แบบ Batch (`/api/mt5/snapshots_bulk`) ใน `mt5_bridge.py` เพื่อรับ Array ของ Symbols และดึงข้อมูลกลับมาใน Request เดียว ลด Latency ลง 80%
- [ ] **กำหนด Absolute Risk Caps (ป้องกัน AI Hallucination)**
  - **ปัญหา:** ตรรกะ `allowOverLimitDefense: isAiDefense` ยอมให้ AI ฝ่าฝืนกฎ Risk Limit ได้เมื่อมั่นใจมาก ซึ่งเสี่ยงมากหาก AI เกิดอาการหลอน (Hallucinate)
  - **วิธีแก้:** ต้องสร้างตัวแปร `ABSOLUTE_MAX_POSITIONS` แบบ Hard-code ในระดับ Base class ของ Node.js ที่ข้ามไม่ได้เด็ดขาด ไม่ว่าค่า Confidence จะสูงแค่ไหน

---

## 🚀 3. Pro-Level Roadmap (แผนการพัฒนาระยะยาว)
มุ่งหน้าสู่สถาปัตยกรรม Swarm AI และ Zero-Latency

- [ ] **Phase 1: Zero-Latency Execution (แก้ปัญหาความหน่วงแบบ Tick-by-Tick)**
  - ย้ายตรรกะ Trade Management ที่อาศัยความเร็วเสี้ยววินาที (เช่น Trailing Stop, Break-Even, Hedging) ออกจาก Node.js
  - ไปเขียนเป็นรัน Async Loop อัตโนมัติใน Python Bridge หรือแปลงเป็น MQL5 (Expert Advisor) โดยตรงเพื่อให้จับจังหวะแบบ Tick-by-tick
- [ ] **Phase 2: Swarm Architecture Consensus**
  - เปลี่ยนจากการให้ JARVIS คิดคนเดียว เป็นการแบ่งหน้าที่ AI Agent (Swarm):
    1. **Macro Analyst (Gemini Flash):** ตีความข่าวและเศรษฐกิจเพื่อหา Bias
    2. **SMC Sniper (Gemini Pro):** สแกนหา FVG, Liquidity Sweeps, Order Blocks
    3. **Risk Officer (Code Logic):** อนุมัติความเสี่ยงและ Margin
  - **เป้าหมาย:** สั่งเทรดได้ก็ต่อเมื่อ "Macro + SMC เห็นตรงกัน" และ "Risk Officer อนุมัติ" (กลไก Consensus)
- [ ] **Phase 3: Predictive RAG & Reinforcement Journaling**
  - นำ Vector Database มาใช้เก็บ **Market Context** เต็มรูปแบบ ณ วินาทีที่กดเทรด (RSI=70, H4 Trend=UP, News Event=NFP)
  - ระบบ Predictive: ก่อนที่ AI จะเทรดไม้ใหม่ ให้ระบบดึง RAG มาเทียบกับประวัติว่า *"Setup หน้าตาแบบนี้ในอดีต Win Rate เป็นอย่างไร"* เพื่อคัดกรองไม้ที่ได้เปรียบที่สุด
