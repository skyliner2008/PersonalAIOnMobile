# WakeTrigger Architecture V28 — ระบบปลุก AI (2026-09-18)

> **เจตนาของระบบ**
> AI นั่งเฝ้ากราฟแบบเรียลไทม์ตลอดเวลาไม่ได้ เพราะเปลืองโทเคน
> จึงใช้ **ระบบตรวจจับสัญญาณใน TF ต่างๆ เป็นตัวกระตุ้นให้ AI ตื่นมาทำงาน**
>
> - ปัจจัยต่างๆ = อินดิเคเตอร์ที่คอยแจ้งเตือน AI ให้ตื่น
> - snapshot 5TF = ภาพที่ AI ตื่นมาเห็น แล้วดูกราฟใน TF ต่างๆ
> - **ปัจจัยไม่ใช่ข้อสรุป** — เกิด "EMA ตัดกัน" แต่ AI อาจวิเคราะห์เจอ LQ ก็ได้
> - ปัจจัยมีได้ถึง 100+ ตัว แล้วให้ระบบ Learning คัดว่าตัวไหนใช้ไม่ได้

---

## 1. ปัญหาแกนกลางของระบบเดิม

```
เจตนา:
  [ปัจจัย = นาฬิกาปลุก] ──ปลุก──> [AI ตื่น] ──ดู──> [snapshot 5TF] ──> AI วิเคราะห์เอง

โค้ดเดิม:
  [ปัจจัย = เทรดเดอร์] ──ตัดสิน BUY/SELL + คำนวณ Entry/SL/TP1/TP2/TP3/RR/confidence──>
       [AI ตื่นมาเจอแผนเทรดสำเร็จรูป] ──> ถูกถามแค่ "APPROVE / VETO / ADJUST"
```

หลักฐานชัดที่สุดคือ prompt เดิม:
```kotlin
appendLine("ทิศทางคาดการณ์: $side | ขั้นตอน: $stage")
appendLine("Execution Rails คาดหมาย: Entry $entry | SL $sl | TP1 $tp1 | TP2 $tp2")
appendLine("แนวทางตัดสิน: - VETO เมื่อ... - ADJUST เมื่อ... - APPROVE เมื่อ...")
```

ตัวเลขเหล่านั้นมาจากไหน? กรณี `FAST_RSI_REVERSAL` คือ `entry × 0.95` / `entry × 1.10`
(พารามิเตอร์คริปโตจาก `ABQ1.txt`) → **ทอง 4000 ได้ SL 3800 / TP 4400**
แล้วบอก AI ว่านี่คือ "Execution Rails คาดหมาย"

> AI ไม่ได้ตื่นมามองกราฟ — มันตื่นมาเห็นแผนเทรดที่ RSI(5) แต่งไว้ แล้วถูกถามว่าเห็นด้วยไหม

---

## 2. สถาปัตยกรรมใหม่

```
┌──── WakeTrigger (automation/wake/) ────────────────────────┐
│  interface WakeTrigger {                                    │
│      val id: String                                         │
│      val evidenceGroup: EvidenceGroup                       │
│      val kind: TriggerKind          // EVENT | STATE        │
│      fun detect(ctx: WakeContext): TriggerEvent?            │
│  }                                                          │
│                                                             │
│  data class TriggerEvent(                                   │
│      triggerId, tf, what, direction, referencePrice         │
│  )   ◄── ไม่มี entry / sl / tp / confidence โดยเจตนา        │
└─────────────────────────────────────────────────────────────┘
                          │ scan()
                          ▼
┌──── WakeContext (ครบ 5 TF ตามสเปก) ────────────────────────┐
│  m1  → ราคาปัจจุบัน                                         │
│  m5  → ยืนยัน / คัดกรองการหลอก                              │
│  m15 → setup ระยะสั้น                                       │
│  h1  → บริบทระยะกลาง                                        │
│  h4  → บริบทระยะยาว                                         │
│  + MarketContextDigest (ภาพที่ AI จะเห็น)                   │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
         EVENT เกิด → ปลุก AI    STATE → เป็นบริบท ไม่ปลุกซ้ำ
                          │
                          ▼
┌──── prompt ที่ AI ได้รับ ───────────────────────────────────┐
│  เหตุการณ์ที่ปลุก: <รายการ>                                 │
│  ⚠️ ปัจจัยที่ชี้ทางตรงข้าม: <รายการ>   ◄── ไม่ซ่อนอีกแล้ว   │
│  โครงสร้างตลาด 5 ไทม์เฟรม: <digest>                        │
│                                                             │
│  "ปัจจัยที่ปลุกอาจไม่ใช่สิ่งสำคัญที่สุด —                  │
│   ถ้าเห็นอย่างอื่นสำคัญกว่าจากโครงสร้าง ให้ยึดสิ่งนั้น"     │
│                                                             │
│  DECISION: NOTIFY | SKIP                                    │
│  BIAS / LEVEL_SL / LEVEL_TP  ◄── AI กำหนดเองจากโครงสร้าง   │
└─────────────────────────────────────────────────────────────┘
```

### EVENT vs STATE — ทำไมต้องแยก

| | ลักษณะ | ตัวอย่าง | ใช้ทำอะไร |
|---|---|---|---|
| **EVENT** | เกิดครั้งเดียวแล้วจบ | EMA ตัดกัน, sweep, BOS | **ปลุก AI** |
| **STATE** | เป็นจริงต่อเนื่องหลายแท่ง | BB บีบตัว, RSI อยู่ในโซน, M5 ขัด M15 | **เป็นบริบท** ไม่ปลุกซ้ำ |

ระบบเดิมนับรวมกัน → state ที่ค้าง 10 แท่งบวกคะแนนให้ทุกแท่ง ทำให้ confidence สูงลอย
ทั้งที่ไม่มีเหตุการณ์ใหม่เกิดขึ้นจริง

### ชุดเริ่มต้น 12 ตัว (ครบทุก evidence group)

| Group | Trigger | Kind |
|---|---|---|
| STRUCTURE | `STRUCTURE_BREAK` (BOS/CHoCH) | EVENT |
| KEY_LEVEL | `KEY_LEVEL_TOUCH` (OB/FVG/swing/EQ) | STATE |
| LIQUIDITY_SWEEP | `LIQUIDITY_SWEEP_REJECTION` | EVENT |
| TREND_MOMENTUM | `EMA_CROSS`, `MACD_HISTOGRAM_TURN` | EVENT |
| OSCILLATOR_EXTREME | `RSI_EXIT_EXTREME` | EVENT |
| VOLATILITY_SQUEEZE | `VOLATILITY_SQUEEZE` | STATE |
| VOLUME | `VOLUME_ABSORPTION` | EVENT |
| INSTITUTIONAL | `INSTITUTIONAL_SHIFT` (Veyra 6 เสา) | STATE |
| SESSION | `SESSION_LEVEL_SWEEP` (ASIA/LONDON/NY จริง) | EVENT |
| MTF_DIVERGENCE | **`M5_CONFIRM_DIVERGENCE`**, `HTF_CONFLICT` | STATE |

**เพิ่มปัจจัยใหม่** = เขียน object เดียว → ใส่ใน `ALL` → ได้สถิติรายตัวอัตโนมัติ
ไม่ต้องแก้ engine / prompt / ตาราง DB

---

## 3. Learning แบบมีเงื่อนไข

### ปัญหาเดิม 3 ข้อ

| ปัญหา | ผลที่เกิด |
|---|---|
| เก็บใน `mutableMapOf` ระดับ object | **หายทุกครั้งที่ปิดแอป** และสร้างใหม่ไม่ได้ |
| 10/13 ปัจจัยฝัง confidence เป็นเลขดิบ | การเรียนรู้ไม่มีผลกับมันเลย |
| หลายปัจจัย → บันทึกเป็น `ANTICIPATION_CONFLUENCE` | **ปัจจัยที่ร่วมยิงไม่เคยได้เครดิต/ถูกตำหนิ** |

ข้อ 3 คือข้อที่ร้ายที่สุด — เป็นข้อมูลชิ้นเดียวที่จำเป็นต่อการ "ตัดปัจจัยที่ใช้ไม่ได้ออก"

### โครงสร้างใหม่ — `AnticipationFactorOutcome` (migration 13)

1 สัญญาณ → N แถว (แถวละปัจจัย) พร้อม context bucket:

| factor_id | mtf_align | adx_bucket | vol_bucket | session | forward_r |
|---|---|---|---|---|---|
| EMA_CROSS | ALIGNED | TREND | NORMAL | LONDON | +1.8 |
| KEY_LEVEL_TOUCH | ALIGNED | TREND | NORMAL | LONDON | +1.8 |
| EMA_CROSS | CONFLICT | RANGE | LOW | ASIA | −0.2 |

**วัดด้วย forward return 12 แท่ง หน่วย ATR** ไม่ใช่ SL/TP ที่ปัจจัยแต่งเอง
→ ตอบตรงคำถามว่า "ปลุกแล้วมีอะไรเกิดขึ้นจริงไหม"
→ เทียบข้าม symbol/TF ได้

### ความเชื่อมั่น = shrinkage

```
effective = prior + (observed − prior) × n/(n+20)
observed  = 50 + avgR × 25
```

- n = 0 → ใช้ค่าเริ่มต้น (ปัจจัยใหม่ใช้งานได้ทันที ตามที่ต้องการ "ใช้เต็มที่ตามงบโทเคน")
- n = 20 → ขยับครึ่งทางไปหาค่าจริง
- n มาก → เชื่อข้อมูลจริงเกือบเต็ม

**ไม่มี state ให้ desync** — คำนวณสดจาก SQL ทุกครั้ง จึงไม่หายตอนรีสตาร์ท

---

## 4. Token economy

จาก [Live API best practices](https://ai.google.dev/gemini-api/docs/live-guide):
> *"billing follows a compounding model based on the active context window"*
> *"Past tokens are re-processed and accounted for in each new turn"*

### วัดจริง

| ส่วน | ~tokens |
|---|---|
| Snapshot 5TF | 284 |
| prompt + คำสั่ง | 481 |
| output | 90 |
| **1 turn** | **~855** |

### ต้นทุนจริงขึ้นกับ context สะสม ไม่ใช่ขนาด payload

| context สะสม | ต้นทุน/ปลุก | ปลุกได้/นาที (TPM 65K) |
|---|---|---|
| ~1K (session สด) | ~1.9K | ~34 |
| ~2K (ตั้ง compression) | ~2.9K | ~22 |
| ~20K (คุยเสียงมาสักพัก) | ~21K | **~3** |
| ~50K (คุยยาว + audio) | ~51K | **~1** |

**แก้แล้ว**: `slidingWindow: {}` เปล่าๆ → `triggerTokens 25K / targetTokens 8K`
พร้อม fallback ไล่ระดับ (มีพารามิเตอร์ → sliding ล้วน → ปิด) ไม่เสียฟีเจอร์ทิ้งทั้งก้อน

### งบที่ใช้ได้จริง

```
TPM 250K ÷ RPM 15 = 16,666 tokens ต่อ 1 request
ใช้อยู่ 765 → เหลือ 95%
```

**แนะนำ**: วิเคราะห์ด้วย Flash Lite (stateless, 855 tokens/ครั้ง) → พูดผลด้วย Live
→ ได้ **1,000 ครั้ง/วัน** (RPD 500 × 2 โมเดล) = เฝ้า 3 สินทรัพย์ ~14 ครั้ง/ชม./ตัว

---

## 5. Snapshot 5TF ที่ขยายแล้ว

`TfLine` เดิมมี: trend / lastEvent / close / rsi / ema50 / atr
**เพิ่ม**: `adx` · `macdHist` · `bbPercentB` · `bbWidthAtr` · `distEma50Atr` · `volumeRatio`

key levels: 3 → **6 ต่อฝั่ง** พร้อมระยะห่างเป็นหน่วย ATR
(AI ประเมินได้ทันทีว่าใกล้พอจะเป็นเป้า/อุปสรรคไหม โดยไม่ต้องคำนวณเอง — สิ่งที่ AI พลาดง่ายที่สุด)

### 5.1 D1 + รายละเอียดต่อ TF (2026-09-20)

ค่าอินดิเคเตอร์บรรทัดเดียวไม่บอกว่าราคาอยู่ตรงไหนของขา วิ่งมาแรงแค่ไหน หรือแท่งล่าสุดปฏิเสธราคาไหม — เพิ่ม:

- **D1** รวมจาก H4 (`resampleDaily`) จัดกลุ่มด้วยจุดกึ่งกลางแท่ง: OANDA วาง H4 ทอง/FX ตาม 17:00 NY
  (ฤดูร้อน 21:00/01:00… UTC) — ถ้าใช้เวลาเปิดกับขอบ 22:00 UTC แท่งแรกของวันจะตกไปวันก่อน
- `TfDetail` ต่อ TF → 2 บรรทัดย่อยใน prompt:
  - `โครงสร้าง:` ลำดับ swing (HH+HL…), swing H/L ล่าสุด + ระยะ ATR, ตำแหน่งในกรอบ 50 แท่ง, EMA 20/50/200 เรียงไหม, ห่าง EMA200
  - `แรง/แท่ง:` RSI 3 แท่งก่อน→ตอนนี้, divergence ปกติ (swing 2 จุดล่าสุด ≤ 30 แท่ง), เคลื่อนกี่ ATR ใน 12 แท่ง,
    แท่งสีเดียวกันติดกัน, ATR percentile 100 แท่ง, ลักษณะแท่งล่าสุด (ไส้ ≥ 50%, ตัวตัน, doji, กลืนกิน)
- แนวรับ/ต้านเพิ่ม swing H4, PDH/PDL, PWH/PWL; แก้ป้าย swing high ใต้ราคาที่เคยติดผิดเป็น "swing L" → "swing H (ทะลุแล้ว)"
- `ล่าสุด=` = เหตุการณ์โครงสร้างใหม่สุดพร้อมอายุ (เดิมเป็น CHoCH↑ ทุกครั้งจากบัค `BooleanArray.any()`)
- ตรวจทั้งหมดด้วย `tools/wake_factor_replay.py` (ส่วน 1b/1c/1d) — BTC และทอง ตรงสูตรอิสระ 100%

### 5.2 กติกาห้ามแจ้งสวนเทรนด์ (2026-09-20)

- `WakeContext.htfAlignment`: H4/H1/M15 เรียงทิศเดียวกัน (M15 CHoCH สวนทางทำให้ trend M15 กลับทิศเอง → ไม่นับว่าเรียง)
- `PREMIUM_DISCOUNT_EXTREME` ในเทรนด์ที่เรียงกัน → ทิศ NEUTRAL (เดิม SELL ทุกครั้งที่อยู่บน 90% ของกรอบ H1 → AI SELL สวนขาขึ้น 8/8 ชน SL)
- prompt: `wake_htf_align` → "⚠️ ห้ามตอบ NOTIFY + SELL/BUY" + กติกาทั่วไป
- ตาข่ายในโค้ด `WakePrompt.enforceTrend`: AI ยังตอบ NOTIFY สวนทาง → เปลี่ยนเป็น SKIP (คงทิศไว้ให้ `AiViewTracker` วัดผล)

---

## 6. งานที่ยังเหลือ

- **ต่อสาย `WakeTriggerRegistry` เข้า `SignalAlertProvider`** — ตอนนี้ registry พร้อมใช้และมีเทสต์ครบ
  แต่ `detectAnticipation` เดิม (13 ปัจจัยแบบ if-chain) ยังทำงานคู่ขนานอยู่
  ต้อง migrate ทีละตัวพร้อมเทียบผลก่อนถอดของเดิมออก
- **Wake budget / cooldown** — ยังไม่มีตัวจำกัดจำนวนการปลุกต่อชั่วโมง
  ปัจจุบัน dedup แค่ 1 ครั้ง/แท่ง/ฝั่ง ซึ่งพอสำหรับ 12 ปัจจัย แต่ไม่พอเมื่อโตถึง 100
- **Batching** — ถ้าหลาย trigger ยิงในช่วงเวลาใกล้กัน ควรรวบเป็นการปลุกครั้งเดียว
- ปัจจัยเดิมที่ยังไม่ได้ย้าย: `FIBONACCI_GOLDEN_POCKET`, `BB_KC_SQUEEZE`, `FAST_RSI_REVERSAL`
  (ตัวหลังต้องแก้พารามิเตอร์คริปโตก่อน หรือตัดทิ้งตามผลการเรียนรู้)
