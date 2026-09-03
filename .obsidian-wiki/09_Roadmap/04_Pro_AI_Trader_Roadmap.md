# 🚀 แผนยกระดับระบบ JARVIS สู่ Pro AI Agent Trader
*วันที่อัปเดต: 2026-04-25*

เอกสารนี้สรุปข้อบกพร่องที่พบจากการตรวจสอบระบบ (Audit) และแผนการพัฒนา (Roadmap) เพื่อยกระดับ PersonalAIBot ไปสู่ระบบเทรดอัตโนมัติระดับสถาบัน (Institutional Grade) แบบเต็มรูปแบบ

---

## 🐛 1. Critical Bug Fixes (ปัญหาที่ต้องแก้ไขด่วน)
ปัญหาเหล่านี้อยู่ใน `mt5_bridge.py` ซึ่งอาจทำให้คำสั่งเทรดล้มเหลวหรือถูก Broker แบนบัญชีได้

- [x] **แก้ปัญหา Float Comparison ใน `modify_position`** (เสร็จแล้ว)
  - **วิธีแก้:** เปลี่ยนไปใช้ `math.isclose(new_sl, cur_sl, rel_tol=1e-5)` ใน Python เรียบร้อยแล้ว
- [x] **เพิ่ม Volume Clamping ใน `close_position`** (เสร็จแล้ว)
  - **วิธีแก้:** จำกัดเพดาน Volume อัตโนมัติ: `volume = min(float(volume_override), float(pos["volume"]))` เรียบร้อยแล้ว
- [x] **ยกเลิก Hardcoded Deviation** (เสร็จแล้ว)
  - **วิธีแก้:** รองรับ dynamic deviation ผ่าน payload: `int(payload.get("deviation", 20))`
 
---
 
## ⚠️ 2. Architectural Bottlenecks (การปรับปรุงโครงสร้างเพื่อประสิทธิภาพ)
แก้ไขคอขวดที่ทำให้ระบบทำงานช้าลงเมื่อต้องรองรับข้อมูลปริมาณมาก
 
- [x] **ทำ Bridge Batching (Node.js ➔ Python)** (เสร็จแล้ว)
  - **วิธีแก้:** สร้าง Endpoint แบบ Batch (`/api/mt5/snapshots_bulk` และ `/ticks_lite`) ใน `mt5_bridge.py` แล้ว
- [x] **กำหนด Absolute Risk Caps (ป้องกัน AI Hallucination)** (เสร็จแล้ว)
  - **วิธีแก้:** มี `ABSOLUTE_MAX_POSITIONS` ใน `MarketCycleEngine.ts` ป้องกัน defense override เรียบร้อยแล้ว

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
