# 📊 Cluster Trading Logic V17.3 (Capital Preservation Edition)

ระบบจัดการออเดอร์แบบ "กลุ่มก้อน" (Cluster-Aware) ออกแบบมาเพื่อยกระดับความปลอดภัยของพอร์ตโฟลิโอ โดยเน้นการปกป้องทุน (Capital Preservation) และการจัดการไม้แก้พอร์ตอย่างมีระบบ

> **V17.3 Update (2026-04-30)** — เพิ่ม Hard Stop, Anti-Martingale, 3-Tier Recovery Decay, Golden Trail Tightening และปรับ Sequential Offset ให้ค้นหาคู่ที่ดีที่สุด

---

## 🛠️ Key Components

### 1. Sequential Protection (การป้องกันการออกไม้ซ้อน)
- **กฎพื้นฐาน**: ในหนึ่ง Symbol ระบบจะอนุญาตให้ออกไม้ใหม่ (Scale-in) ได้ก็ต่อเมื่อไม้ล่าสุด (Last Position) อยู่ในสถานะ **SAFE** เท่านั้น
- **นิยามของ SAFE**:
    - Stop Loss ถูกขยับมาที่จุดคุ้มทุน (Breakeven) หรือกำไรแล้ว
    - เป็นการล็อคกำไรเพื่อลดความเสี่ยงรวมของ Cluster ก่อนจะเพิ่มความเสี่ยงใหม่
- **ข้อยกเว้น (Recovery Mode)**: หากไม้ล่าสุดติดลบเกิน `recoveryTriggerPct` (Default 20% ของระยะ SL) ระบบจะอนุญาตให้ออกไม้แก้ (DCA/Recovery) ได้เพื่อเฉลี่ยทุน

### 2. Sequential Offset (การปิดรวบอัจฉริยะ) — V17.3 ปรับใหม่
- **Logic**: ระบบจะค้นหา **คู่ที่ดีที่สุด**: `worst-active` (ไม้ขาดทุนหนักสุด) × `best-non-golden-safe` (ไม้กำไรมากสุดที่ยังไม่ Golden)
- **Trigger**: เมื่อผลรวมกำไรขาดทุนของคู่นั้น (Net PnL) มีค่า >= 0 ระบบจะส่งคำสั่ง **ปิดรวบทั้งคู่ทันที**
- **เป้าหมาย**: เพื่อเคลียร์ไม้ที่เสียเปรียบออกโดยไม่กระทบต่อเงินทุนหลัก (Zero-cost exit for bad positions)
- **เปลี่ยนแปลงจาก V17.2**: เดิมเทียบเฉพาะ `latestActive` × `latestSafe` ตัวล่าสุด ทำให้พลาดโอกาสเมื่อ safe ตัวล่าสุดเป็น Golden

### 3. Golden Position Guard (การปกป้องไม้รันเทรนด์)
- **นิยามของ Golden**: ไม้ที่มีค่า R-value (Profit/Risk) >= `goldenThresholdR` (Default 2.0R)
- **ความสามารถ**:
    - ยกเว้นจากกฎ Sequential Offset (ไม่ถูกใช้เป็น buffer ปิดคู่)
    - **Golden Trail Tightening (V17.3 ใหม่)**: เมื่อ R >= Golden + 1 (=3R) ระบบจะปรับ trailing SL ให้ใกล้ราคาขึ้น (`goldenTrailMultiplier` default 0.7× ของ SL distance) เพื่อล็อคกำไรส่วนใหญ่
- **เหตุผล**: ปล่อย Run Trend ต่อแต่ลดความเสี่ยงที่กำไรจะย้อนกลับ

### 4. Recovery Time Decay (การจำกัดเวลาไม้แก้) — V17.3 ปรับเป็น 3 Tier
- **Tier 1** (>= `recoveryMaxAgeMs`, default 6h): ปิดยกกลุ่มถ้า Net PnL ใกล้ BE
- **Tier 2** (>= `recoveryTier2AgeMs`, default 12h): ปิดถ้า Net อยู่ในช่วง ±2× netBeProfitBand (-4..+4 USD)
- **Tier 3** (>= `recoveryTier3AgeMs`, default 24h): บังคับปิดไม่ว่า Net เท่าไร — ปลดทุนคืนเสมอ
- **เป้าหมาย**: ป้องกันเงินทุนจมนานเกินไป + บังคับยกเลิกไม้แก้ที่ไม่ฟื้น

### 5. Hard Cluster Loss Stop (V17.3 ใหม่ — Emergency Cut)
- **Trigger**: ฟ้าผ่าหั่นทุน เมื่อ:
    - Cluster heat >= `clusterMaxHeatR` (default 3.0R), หรือ
    - Floating loss >= `clusterMaxLossPct` ของ Equity (default 5%)
- **Action**: ปิดไม้ที่ขาดทุนหนักสุดทันที ไม่ว่าจะมีกฎ offset/hedge อื่นที่ไม่ทริก
- **เหตุผล**: backstop ขั้นสุดท้ายเพื่อหยุดการไหลออกของทุน — เป็นเส้นแดงที่ขีดไว้

### 6. Anti-Martingale Volume Cap (V17.3 ใหม่)
- **กฎ**: Volume ของไม้ Recovery DCA ต้อง **<= ตัวล่าสุดฝั่งเดียวกัน** เสมอ
- **Cap จริง**: `min(latestSameSideVolume, avgSameSideVolume)`
- **เหตุผล**: ห้าม double-down ขนาด — เป็นกฎทองของ risk management ที่กัน Martingale trap
- **ตัวอย่าง**: ถ้ามี SELL 0.03 + 0.02 + 0.01 อยู่ การ DCA ครั้งต่อไปจะถูกจำกัดที่ ≤ 0.01 (= latest) — ไม่ใช่ขยาย

### 7. Per-Symbol Position Cap (V17.3 ใหม่ — Mobile App Setting)
- **การตั้งค่าจาก mobile app**: `maxOpenPositions = N` หมายถึง **ต่อ symbol ไม่เกิน N** (ไม่ใช่ global)
- **Defense Cap**: เมื่อ hedge / scale-in เพื่อแก้ไม้ ระบบขยายเป็น 2N ต่อ symbol
- **ตัวอย่าง**: ตั้ง 5 → ปกติเปิดได้ 5/symbol, แก้ไม้ได้ถึง 10/symbol
- **ลบกฎเดิม**: global aggregate cap ที่หลอกให้ "XAUUSD 4/5, XBTUSD 3/5 (total 7/5)" บล็อกอย่างไม่จำเป็น

### 8. Cluster Profit Protection (CPP) & Unified Break-Even — V19.6 ใหม่ล่าสุด
- **การแยกกลุ่ม (Grouping)**: แบ่งกลุ่มตาม Symbol และ ทิศทาง (Side) เช่น `XAUUSD_BUY` และ `XAUUSD_SELL` แยกกัน
- **การคัดแยก (Exclusion)**: ระบบจะคัดไม้ที่รันกำไรไปจนถึงระดับ Trailing Stop (`trailAfterR`) ออกไปก่อน จะไม่นำมาคิดรวมและไม่ถูกแทรกแซง
- **Activation (เริ่มจดจำ)**: จะเริ่มจดจำยอด Peak Profit ของกลุ่ม "ยังไม่ Trail" เมื่อค่า **Cluster R-Value** ถึง `clusterProfitProtectionActivationR` (เช่น 0.75R) **หรือ** กำไรรวมของกลุ่มสูงกว่า `clusterProfitProtectionMinDollar` (เช่น $20) เพื่อแก้ปัญหา R-Value ต่ำเกินไปจากการตั้ง SL ที่กว้างมาก
- **Trigger (รวบตึง)**: ถ้ายอดกำไรของกลุ่มนี้ ร่วงลงจาก Peak มากกว่าหรือเท่ากับ `clusterProfitProtectionDropPct` (เช่น 50%) ระบบจะยิงคำสั่ง **Unified Cluster Break-Even** ทันที
- **Unified Cluster Break-Even**: แทนที่จะเลื่อน SL ไปที่จุดเข้าของแต่ละไม้ (ซึ่งใน MT5 จะถูกปฏิเสธถ้าราคาออเดอร์นั้นยังขาดทุนอยู่) ระบบจะคำนวณ **Volume-Weighted Average Entry Price** (ราคาต้นทุนเฉลี่ย) ของทั้งกลุ่ม แล้วสั่งเลื่อน SL ของทุกไม้ไปที่ราคาเฉลี่ยนั้นพร้อมกัน ส่งผลให้ทุกไม้ถูกปิดพร้อมกันและกำไรจะหักลบกลบหนี้จนเสมอตัวอย่างสมบูรณ์

### 9. Strict Sequential Protection — V19.6
- **ป้องกัน AI แหกกฎ**: ระบบบังคับใช้กฎ Sequential Protection แบบเด็ดขาดในระดับ Risk Gate
- **เงื่อนไข**: หากจะมีการออกไม้เพิ่ม (Scale-in/Recovery) ไม้ล่าสุดของฝั่งนั้นจะต้อง "ปลอดภัยแล้ว (R >= 0)" หรือ "ลบหนักจนเข้าเกณฑ์ไม้แก้ (R <= recoveryThreshold)" เท่านั้น
- **การยับยั้ง (Veto)**: แม้ AI จะมั่นใจเกิน 70% และเรียกใช้ Defense Override หากผิดกฎนี้ Risk Gate จะ Block ทันที เพื่อป้องกันไม่ให้ออกไม้แทรกซ้อน (เช่น ไม้ 4, 5 ก่อนเวลา) ที่จะไปดึง Average Entry ให้แย่ลง

---

## 🤖 Robust AI Resilience

### Smart 429 Handling
- **Detection**: ระบบสามารถวิเคราะห์ Error 429 (Rate Limit) จาก OpenRouter ได้อย่างแม่นยำ (HTTP status, "free-models-per-day", "daily limit")
- **Cooldown**: เมื่อตรวจพบ Quota เต็ม ระบบจะเข้าสู่โหมด **AI Cooldown (1 ชั่วโมง)** โดยอัตโนมัติ
- **Fallback**: ในช่วง Cooldown ระบบจะสลับไปใช้ **Deterministic Engine (Technical Rules)** เพื่อบริหารจัดการออเดอร์และแก้พอร์ตต่อไปโดยไม่หยุดชะงัก

### Smart Free Ranker (V19.3 + 2026-04-30 fixes)
- **Capability filter**: คัดทิ้งโมเดล OCR / Vision / Embedding / Audio ที่ไม่ทำ JSON reasoning ได้
- **Min-calls gate**: ต้องมี total_calls ≥ 5 ถึงจะขึ้น Top Ranked (กัน "score 99.5 ปลอม")
- **Empty-raw = failure**: response ว่างจาก `openrouter/free` ถูกถือเป็น failure ทันที (กัน loop พังซ้ำ)
- **Session blacklist**: ตรวจ JSON parse error → blacklist ใน session จนกว่าหมุนครบ pool
- **Auto-Persistence on Swap**: เมื่อมีการสลับโมเดลเนื่องจากตัวเก่าคะแนนตกหรือถูกแบน ระบบจะแก้ไขตั้งค่า `AutoTradingConfig` แบบ Dynamic ให้หน้าต่าง Settings โชว์ชื่อโมเดลปัจจุบันเสมอและจำไว้ใช้ในรอบต่อไป

---

## ⚡ Token Efficiency (V17.3)

ระบบเพิ่ม 5 ขั้น short-circuit ก่อนเรียก LLM:

1. **Pre-AI Cap Gate**: per-symbol cap เต็ม + ไม่มี defensive intent → SKIP ไม่เรียก LLM
2. **Pre-AI Zone Gate**: deterministic side ติด Zone-Aware Gate → SKIP (ตัด Execution Trader ทิ้ง)
3. **Strict Cache**: market state hash เหมือน 3 นาทีย้อนหลัง → ใช้ผลเดิม
4. **Loose Cache**: HTF (H4+H1) เหมือน + side+strategy เหมือน → ใช้ผลเดิม
5. **Deterministic Fast-Path**: confidence ≥ 70% (หรือ 50% เมื่อ cap เต็ม) → ใช้ deterministic เป็น aiDecision

ผลรวม: ลดโทเคนได้ ~70-99% เมื่อตลาดไม่เปลี่ยนแปลง / position cap เต็ม

---

## 🛡️ Defense Override Improvements (V17.3)

- **AI confidence threshold**: ลดจาก 75 → **70** (พบ AI conf=74 ตกเส้น 1 หน่วยเป๊ะ ๆ ใน production)
- **Deterministic Defense Fallback**: เมื่อ AI fail หรือ low-conf แต่ MTF analysis confluence ≥ 65 → อนุญาต defense override (รองรับ AI outage)

---

## ⚙️ Configurable Defaults

| Parameter | Default | คำอธิบาย |
|---|---|---|
| `goldenThresholdR` | 2.0 | R cutoff สำหรับ "Golden" |
| `goldenTrailMultiplier` | 0.7 | Trail SL เมื่อ R >= Golden+1 |
| `recoveryTriggerPct` | 0.2 | -20% ของ SL distance = recovery zone |
| `recoveryMaxAgeMs` | 21600000 (6h) | Tier 1 |
| `recoveryTier2AgeMs` | 43200000 (12h) | Tier 2 |
| `recoveryTier3AgeMs` | 86400000 (24h) | Tier 3 (force) |
| `clusterMaxHeatR` | 3.0 | Hard stop heat |
| `clusterMaxLossPct` | 5.0 | Hard stop equity loss % |
| `clusterProfitProtectionActivationR`| 0.75 | R-Value รวมของกลุ่มที่กระตุ้นให้ระบบเริ่มจำ Peak |
| `clusterProfitProtectionDropPct` | 50 | % ที่ร่วงจาก Peak เพื่อบังคับเลื่อน BE ทั้งกลุ่ม |
| `netBeProfitBand` | 2 | ±USD ที่ถือว่า "near BE" |
| `hedgeTriggerHeatR` | 0.6 | เปิด hedge เมื่อ heat ถึง |
| `hedgedExitMaxHeatR` | 0.8 | ปิด hedge เมื่อ heat ลด |
| `hedgeOverrideMinConfidence` | 70 | AI conf ขั้นต่ำเปิด defense override |
| `deterministicHedgeMinConfluence` | 65 | Confluence ขั้นต่ำเมื่อ AI fail |
| `hardCapPerSymbolMultiplier` | 2.0 | per-symbol defense cap = N × 2 |

---

**Last Updated:** 2026-05-01
**Version:** V19.6
**Status:** 🟢 Verified & Stable — Cluster Profit Protection & Strict Sequential Guard Active
