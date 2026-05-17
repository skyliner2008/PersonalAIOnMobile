# AutoTrading Smart Upgrade Plan — 2026-04-25

> อ้างอิง log AutoEngine วันที่ 2026-04-24 (ประมาณ 1,739 cycles, XAUUSD, 10:04–17:04) เพื่อวิเคราะห์จุดอ่อนของระบบ และเสนอแผนปรับปรุงแบบเป็นขั้น (Phase 1–4)

---

## 1. สรุปพฤติกรรมที่เห็นจาก Log (What went wrong)

ระบบเดินครบ 6 phase ได้จริง (analyze → strategize → execute → manage → record → learn) แต่พบปัญหาเชิงคุณภาพของ **การตัดสินใจ** 3 ชั้น คือ (ก) **จุดเข้า**, (ข) **การบริหารไม้**, และ (ค) **ตรรกะสลับทิศ/ปิดพร้อมกัน**

### 1.1 ปัญหาจุดเข้า (Entry)

| # | อาการใน log | สาเหตุเชิงตรรกะ |
|---|-------------|----------------|
| E1 | BUY ถูกยิงในโซน Premium (เหนือ EMA20 H4 ~4727) | ไม่มี gate `premium/discount zone` ต่อ regime — ยอมให้ BUY ใน Premium ได้หาก confluence ≥ 45 |
| E2 | SELL ถูกยิงในโซน Discount (ใกล้ low H4 ~4668) | เหมือนกัน แต่ฝั่ง SELL — ยิง SELL หลัง liquidity sweep ไปแล้ว |
| E3 | BUY *เหนือ* Bullish FVG แทนที่จะ *ลงมาเติม FVG ก่อน* | SMC analyzer detect FVG ได้ แต่ strategy `SMC_FVG_SCALP` ไม่ได้บังคับว่า **entry ต้องอยู่ภายใน/ใกล้ FVG zone** |
| E4 | SL วางอยู่ **ภายใน FVG** ทำให้ stop-hunt ง่าย + bridge reject 10016 "Invalid stops" | ไม่มี buffer ที่บังคับให้ SL อยู่ **นอก** FVG/OB เสมอ + ไม่ check minimum ATR distance |
| E5 | Counter-trend BUY ช่วง H4:BEAR:80 ผ่าน gate เพราะ confluence = 51–54 | เกณฑ์ soft-confluence ต่ำเกินไปสำหรับ counter-trend (แก้ไขล่าสุด 60→45 ทำให้ไม้ไหลเข้ามากเกินไป) |

### 1.2 ปัญหาการบริหารไม้ (Management)

| # | อาการใน log | สาเหตุเชิงตรรกะ |
|---|-------------|----------------|
| M1 | ไม้ที่ไปถึง +1R–2R กลับปิดที่ 0R จำนวนมาก | `trailAfterR = 1.5` + BE aggressive — ดึง SL ชิดเกินไป, ราคาแกว่งเล็กน้อยก็เด้ง BE |
| M2 | Scale-in "BUY 0.05 เพิ่ม" ที่ราคา *สูงกว่า* weighted-average entry ของ cluster BUY | ไม่มี rule `scale_in only at better price` — ถัวขาดทุนขึ้นไปเรื่อย ๆ |
| M3 | ถึง `maxOpenPositions = 5` แล้วข้ามสัญญาณคุณภาพสูงที่เกิดขึ้นใหม่ | ไม่มีตรรกะ "ปิดไม้อ่อนสุด ยอมรับไม้ดีกว่า" (close-weakest-loser) |
| M4 | Anti-hedge gate บล็อก protective SELL ระหว่าง BUY drawdown | Rule `allowOverLimitDefense` ไม่ active เพราะ heat < trigger + confidence AI ต่ำ |
| M5 | Defense override ถูก *suppress* เมื่อ cluster heat ≥ 1.01R | ตรง ๆ กับ memory `hedge_guard` — กัน over-hedge แต่กลับปิด safety วินาทีที่ต้องการมากที่สุด |
| M6 | "ปิดพร้อมกัน" ของ cluster เมื่อ net BE — ทำให้ **ไม้กำไรถูกปิดก่อน** ไม้ขาดทุนที่ถ่วง | Rule `hedged book at net-BE` ปัจจุบันปิด *loser leg* ก่อน แต่ใน log บ่อยครั้งปิด **ทั้ง cluster** (ดูเหมือนอีกสาขาถูก trigger พร้อมกัน) |

### 1.3 ปัญหาตรรกะสลับทิศ / กลับหน้าเล่น

| # | อาการใน log | สาเหตุเชิงตรรกะ |
|---|-------------|----------------|
| D1 | ไม่มีการ "flip direction" อัตโนมัติ แม้ H4 regime จะพลิกชัด | Manager มีแต่ HEDGE/HOLD/BE/TRAIL/SCALE_IN — ขาด action `FLIP` (close cluster + open opposite) |
| D2 | Counter-trend cluster ต่อยอดต่อไปเรื่อย ๆ เพราะ scale-in rule หลวม | Scale-in ไม่ตรวจ regime alignment ปัจจุบัน ใช้แค่ profit + heat |
| D3 | Post-mortem ไม่ feed กลับ pre-analysis — ระบบไม่จำว่า setup คล้าย ๆ นี้เคยขาดทุน | Vector store + knowledge graph มี แต่ pre-analysis ไม่ query similar-failed setups ก่อนให้ LLM ตัดสิน |

---

## 2. ข้อเสนอเชิงแนวคิด (Design directions)

ปรับระบบให้ "ฉลาดขึ้น" ในทั้ง 3 จุด โดยยึดหลัก:

1. **จุดเข้าเลือกได้ดีขึ้น** — ใช้ zone-aware gate + FVG-alignment rule
2. **บริหารไม้เลือกเลิกได้ดีขึ้น** — BE/Trail แบบ staged + smart-scale + close-weakest
3. **กลับหน้าได้เมื่อจำเป็น** — ใส่ FLIP action + regime-reversal trigger

และเพิ่ม **learning loop ที่ปิด** โดย embed โปรไฟล์ setup ของดีล *ขาดทุน* ย้อนมาเป็น prior ให้ pre-analysis ทุก cycle

---

## 3. Work Plan แบ่งเป็น 4 เฟส

### Phase 1 — Entry Quality Hardening (สัปดาห์ 1)

เป้า: ลด false entry ในโซน Premium/Discount และแก้ Invalid-stops

| Item | ไฟล์หลัก | รายละเอียด |
|------|--------|----------|
| P1.1 | `auto/analyzers/smc.ts` | เพิ่ม helper `classifyPremiumDiscount(candlesH4)` คืน `{ zone: 'PREMIUM'\|'EQ'\|'DISCOUNT', pctFromRange }` — ใช้ OTE 62–79% เป็นเกณฑ์ |
| P1.2 | `auto/analysis.ts` หรือ `deterministicEngine.ts` | **Zone-aware gate**: หาก H4 regime = BULL → ห้าม BUY ใน Premium (reject/downgrade); BEAR → ห้าม SELL ใน Discount |
| P1.3 | `auto/analyzers/smc.ts` | เพิ่ม `entryMustAlignFVG(side, fvg, price)` — สำหรับ strategy `SMC_FVG_SCALP` ต้อง (BUY: ราคาอยู่ ≤ fvg.top หรือรอ tap FVG; SELL: ราคาอยู่ ≥ fvg.bottom) |
| P1.4 | `auto/core/TradingExecutionService.ts` | **Dynamic SL tick-buffer**: SL ต้อง *นอก* FVG zone + ห่าง ≥ `max(ATR(M15)*0.25, 10 ticks)`; clamp เทียบ `SYMBOL_TRADE_STOPS_LEVEL` ก่อน send |
| P1.5 | `auto/analysis.ts` | **Tighten counter-trend soft-confluence**: เปลี่ยน threshold 45 → 62 เมื่อ HTF bias สวนกับ side; คง 45 ไว้สำหรับ align กับ bias |

**ผลที่คาดหวัง**: ลด Invalid-stops ของ bridge 10016 → ≈ 0, ลด BUY-in-Premium / SELL-in-Discount > 80%

### Phase 2 — Management Upgrade (สัปดาห์ 2)

เป้า: รักษากำไรที่เคยวิ่งไปถึง 1R+ ไว้ได้ และห้ามถัวขาดทุน

| Item | ไฟล์หลัก | รายละเอียด |
|------|--------|----------|
| P2.1 | `core/TradeManagementService.ts` | **Staged BE/Trail**: 1R → partial close 33%; 2R → BE + trail ATR×1.0; 3R → trail ATR×1.5; ห้ามขยับ SL ลงต่ำกว่า BE เคยถึง (monotonic) |
| P2.2 | `core/TradeManagementService.ts` | **Smart scale-in**: อนุญาตเฉพาะเมื่อ (BUY) price < weightedAvgEntry − 0.25×ATR, และ cluster netR ≥ 0, และ regime align; ปิดสิทธิ์ถ้า netR < 0 |
| P2.3 | `core/TradeManagementService.ts` | **Close-weakest-loser**: ถ้าถึง `maxOpenPositions` และเจอ setup ใหม่ที่ `confluence ≥ 70 & regime align` → ปิดไม้ที่ `profitR ต่ำสุด` ก่อนเปิดไม้ใหม่ (จำกัด 1 ครั้ง/ชม. ป้องกัน churn) |
| P2.4 | `core/TradeManagementService.ts` | **Hedge override gate**: suppress hedge เฉพาะ heat ≥ 1.5R *และ* AI confidence < 0.6; ถ้า confidence ≥ 0.75 → อนุญาต hedge แม้ heat สูง (เพราะคือสัญญาณ protective ที่มั่นใจ) |
| P2.5 | `core/TradeManagementService.ts` | **Hedged-BE close rule**: ปิด loser leg เสมอก่อน winner leg (log ปัจจุบันบางครั้งปิดคู่ — อาจเป็น bug ที่ trigger 2 branch ไล่กัน) — เพิ่ม mutex/guard ต่อ cluster/cycle |

**ผลที่คาดหวัง**: ค่าเฉลี่ย avgR ของไม้ที่เคย ≥ 1R กลับมาจาก ~0.1R → ≥ 0.6R

### Phase 3 — Smart Flip & Learning Loop (สัปดาห์ 3)

เป้า: ให้ระบบ "กลับหน้า" ได้เมื่อตลาดพลิก, และจำของเก่าได้

| Item | ไฟล์หลัก | รายละเอียด |
|------|--------|----------|
| P3.1 | `core/TradeManagementService.ts` + `agents/riskOfficer.ts` | เพิ่ม action **`FLIP_CLUSTER`**: ถ้า H4 regime พลิกสวน cluster + LTF confirm ด้วย BOS + cluster heat ≥ 0.8R → ปิด cluster และเปิดไม้ตามทิศใหม่ขนาด 50% ของ cluster เดิม |
| P3.2 | `core/TradeManagementService.ts` | **Cluster concentration guard**: ห้ามเปิดไม้ฝั่งเดียวกัน > 3 ไม้ภายใน 10 pips — ลดการกระจุกตัวที่เป็นต้นเหตุ "ต้องแก้ไม้" |
| P3.3 | `auto/analysis.ts` + `vectorStore.ts` | **Pre-analysis prior**: query top-5 closed trades ที่ setup คล้ายปัจจุบัน (symbol + regime + strategy); ถ้า avgR < −0.3 → หัก 10 คะแนนจาก confluence |
| P3.4 | `auto/agents/postMortem.ts` | เพิ่ม field `failure_pattern` (เช่น `BUY_IN_PREMIUM`, `SL_INSIDE_FVG`, `SCALE_UP_LOSER`) ใส่ใน embedding payload เพื่อ query ตรงกับสาเหตุ |
| P3.5 | `agents/analyst.ts` (prompt) | inject "similar-failed setups" เป็น context + "avoid these patterns" checklist ก่อน LLM ตัดสิน |

**ผลที่คาดหวัง**: ลดการถือ cluster ที่สวน H4 regime > 2 ชม. โดยไม่มี action, lift win rate strategy ที่เคยขาดทุนซ้ำ ๆ

### Phase 4 — Observability & KPI Dashboard (สัปดาห์ 4)

เป้า: วัดผลทุกข้อด้านบน ไม่อ้างความรู้สึก

| Item | ไฟล์หลัก | รายละเอียด |
|------|--------|----------|
| P4.1 | `core/PersistenceService.ts` + `metrics.ts` | บันทึก event ใหม่: `zone_at_entry`, `fvg_aligned`, `scale_in_better_price`, `flipped`, `close_weakest` |
| P4.2 | UI Kotlin (`auto/AutoTradingViewModel.kt` + TradingTerminalScreen Tab) | เพิ่มการ์ด "Cycle Quality": % entry ใน FVG, % entry align zone, avgR ของ winners, count hedge/flip ต่อวัน |
| P4.3 | `autoTradingService.ts` | expose endpoint `/metrics/quality` สำหรับ KPI board |
| P4.4 | wiki | อัปเดต `12_AutoTrading_Remote_Engine.md` ให้สอดคล้องกับ feature flag ใหม่ + เพิ่ม runbook สำหรับแต่ละ alert |

**ผลที่คาดหวัง**: ทีม/ผู้ใช้เห็นผลปรับปรุงแต่ละสัปดาห์เป็นตัวเลข, rollback รายฟีเจอร์ได้ง่าย

---

## 4. KPI วัดผลหลังปรับปรุง (ทั้งระบบ)

ต้องวัดก่อน-หลังบน XAUUSD, 7 วันเต็ม

| KPI | ฐาน (เอาจาก log 2026-04-24) | เป้า Phase 1 | เป้า Phase 2 | เป้า Phase 3 |
|-----|-----------------------------|--------------|--------------|--------------|
| Invalid-stops rejections (10016) | หลายครั้ง/ชม. | 0 | 0 | 0 |
| % BUY in Premium zone | ~35–40% | ≤ 10% | ≤ 5% | ≤ 5% |
| % SELL in Discount zone | ~25–30% | ≤ 10% | ≤ 5% | ≤ 5% |
| Winners reaching 1R → closed ≤ 0.2R | > 40% | — | ≤ 20% | ≤ 15% |
| Avg R of closed winners | ~0.3R | — | ≥ 0.8R | ≥ 1.0R |
| Cluster held > 2h against H4 regime | บ่อย | — | — | 0 |
| Scale-in at worse price | หลายครั้ง/วัน | 0 | 0 | 0 |

---

## 5. ลำดับความสำคัญ (เริ่มอะไรก่อน)

ถ้ามีเวลาจำกัด แนะนำเรียงนี้ — P1.4 ลดปัญหา "bridge reject" ที่กัดเวลารอบ cycle ได้ทันที, P2.1 รักษากำไรจริง, P3.1 ป้องกันหลุมขาดทุนหนัก:

```
P1.4 (SL buffer + tick-snap)    ← แก้ด่วน, quick win
P1.2 (zone-aware gate)          ← แก้ entry คุณภาพต่ำที่พบมากสุด
P2.1 (staged BE/Trail)          ← คืนกำไรที่เคยมี
P2.2 (smart scale-in)           ← หยุดถัวขาดทุน
P3.1 (FLIP_CLUSTER)             ← สำหรับเคสตลาดพลิก
P3.3/P3.4/P3.5 (learning loop)  ← ทำได้แล้ววนกลับปรับ weights
อื่น ๆ                          ← เก็บปลาย
```

---

## 6. ความเสี่ยง / ข้อควรระวัง

- **R1** การแก้ scale-in rule (P2.2) อาจทำให้ cluster ที่กำไรอยู่แล้วเพิ่มไม้ไม่ได้เลยถ้า ATR ใหญ่ → ควรมี fallback "if netR ≥ 2, allow scale at market"
- **R2** FLIP_CLUSTER (P3.1) ถ้า trigger บ่อยเกินจะเป็น "churn" → ใส่ cooldown ≥ 2 ชม. ต่อ symbol ต่อ direction
- **R3** Close-weakest (P2.3) ต้องไม่ปิดไม้ที่เพิ่งเปิด < 15 นาที (กันไม้เพิ่งยิงโดนทิ้ง)
- **R4** Pre-analysis prior ด้วย embeddings (P3.3) ต้องมี cache — ห้าม query DB ทุก cycle ถ้า 60 วินาที/cycle
- **R5** การแก้ minConfluence สวน bias ขึ้น 62 (P1.5) จะลด signal density — ต้องดูว่า watchlist ยังคงมีงานพอ

---

## 7. Checklist ก่อน deploy แต่ละเฟส

- [ ] unit test ต่อฟีเจอร์ใหม่ (smc zone classifier, staged BE, FLIP)
- [ ] replay log เก่าผ่านชุดกฎใหม่ → เทียบว่า decision จริงเปลี่ยนกี่ cycle และเปลี่ยนไปทางที่ดีขึ้นจริง
- [ ] canary บน `enableLiveTrading=false` อย่างน้อย 48 ชม. ก่อนเปิด live
- [ ] feature flag ต่อ item (P1.1, P1.4, P2.1, …) เพื่อ rollback แบบ granular
- [ ] update `.obsidian-wiki/12_AutoTrading_Remote_Engine.md` + `13_AI_Pro_Trader_Roadmap.md`


