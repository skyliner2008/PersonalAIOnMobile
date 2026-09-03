# 65 — AI Strategy Supervisor + MarketContextDigest 5TF (2026-08-29)

ต่อยอดจาก [[64_Unified_SMC_App_Integration]] — เปลี่ยนบทบาท AI จาก "อ่านสรุปสัญญาณ" เป็น **Strategy Supervisor**
ที่มี gate ตัดสินก่อนแจ้งผู้ใช้ ตามสเปกผู้ใช้ 2026-08-28: รวมจุดเด่นทุกกลยุทธ์เป็น pipeline เดียวแบบมีลำดับ
(ไม่ใช่ edge/weight voting) และให้ AI มองภาพรวมโครงสร้างตลาด 5TF แทนการเดาเอง

## สถาปัตยกรรม

```
MARKET DATA 5TF (ดึงเสมอ: H1→resample H4, M15, M5, M1)
        │
        ▼
Unified SMC Engine (deterministic) ──► SIGNAL + MarketContextDigest (deterministic)
        │
        ▼
AI SUPERVISOR (Gemini, prompt โครงสร้างเข้ม 5 บรรทัด)
   DECISION: APPROVE | VETO | ADJUST   + ADJUST_SL/TP + CONFIDENCE + REASON_TH
        │
   VETO  → ไม่แจ้งผู้ใช้, shadow-record (delivery=ai_veto) เพื่อเก็บ track record เปรียบเทียบ
   ADJUST → validate แล้วค่อยแทน SL/TP (ไม่ผ่าน → fallback APPROVE ค่าเดิม)
   APPROVE → flow เดิม
        ▼
Risk/Notification/Chat/Voice (ของเดิม)
```

## ชิ้นส่วนใหม่

### 1. `MarketContextDigest.kt` (commonMain/automation/smc) — โครงสร้างตลาด 5TF แบบ deterministic
ลดภาระ AI: คำนวณจากแท่งเทียนล้วนๆ (causal — ใช้แท่งปิดล่าสุด `m15[n-2]`, swing ยืนยันแล้วเท่านั้น)
- per-TF line: H4/H1/M15/M5/M1 → trend, event ล่าสุด (BOS/CHoCH), RSI14, เทียบ EMA50, ATR, swing H/L
- แนวรับ/ต้านใกล้สุด 3 ระดับ/ฝั่ง: swing H/L (H1+M15), EQH/EQL pools, FVG mid, Demand/Supply OB (SmcEngine)
- Premium/Discount จาก H1 100 แท่ง · Volume Profile ย่อ (M15 200 แท่ง, 24 bins → POC/VAH/VAL)
- `keyZoneHit`: ราคาอยู่ใน 0.3×ATR ของ level สำคัญ → แจ้ง AI/ผู้ใช้ได้ (alert field `signal_keyzone`)
- บรรทัดอ้างอิง SL/TP เชิงโครงสร้าง (BUY: SL ใต้ Demand, TP ใต้ Supply / SELL กลับด้าน)

### 2. M1 confirmation layer ใน `UnifiedSmcSignals.evaluate(h1, m15, m5, m1)`
- `aggregateLtfInto` merge BOS/CHoCH + body ของ M1 เข้า bin M15 เดียวกับ M5 (OR กัน), น้ำหนัก W_CONF เท่าเดิม
- ⚠️ M1 เป็น **additive post-research refinement** — ไม่ได้อยู่ใน backtest V5 (ห้ามอ้างว่าผ่าน gate)

### 3. AI Supervisor ใน `JarvisAutomationService.fireJobAlert`
- ทำงานเฉพาะ signal ที่ strategy มี "Unified SMC" และ delivery=ai (โหมด direct ไม่เรียก AI ตามดีไซน์)
- prompt: สัญญาณ + digest 5TF + กติกา VETO/ADJUST/APPROVE → ตอบ 5 บรรทัด (parse ด้วย regex เพราะ
  `stripCodeFences` บีบ whitespace เป็นบรรทัดเดียว)
- **ADJUST validation**: BUY ต้อง SL<entry<TP (SELL กลับด้าน), RR ≥ 1.0, risk 0.2–6×ATR, TP ≤ 10×ATR
- ค่าที่ปรับไหลเข้า `recordSignalAlert` / การ์ดแชท / เสียงพูดอัตโนมัติ (rename data→effData ใน fireJobAlert)
- supervisor note ใช้เป็น user-facing summary เลย (ไม่เรียก AI ซ้ำ — ประหยัดโทเคน)

### 4. Keyzone watch (ราคาแตะจุดสำคัญ ยังไม่มีสัญญาณ)
- Provider keys ใหม่: `signal_keyzone` ("1"/"0"), `signal_keyzone_desc`, `signal_mtf_context` (ทั้ง 2 branch return)
- ผู้ใช้ตั้ง alert `signal_keyzone == 1` ได้ → fireJobAlert มี branch พิเศษ: prompt/การ์ด/เสียงแบบ "จับตา" ไม่ใช่ "เข้าเทรด"

## สถานะความเชื่อมั่น

- ทุกอย่างยังเป็น **forward-test**: V5 basin ผ่าน 3/5 gates, promotion_ready=false (ดู note 64)
- VETO ถูก shadow-record ด้วย `delivery="ai_veto"` + strategy tag `[AI-VETO xx%]` → วัดภายหลังว่า
  สัญญาณที่โดนคัดออกแย่จริงไหม (เปรียบเทียบ approved vs vetoed ผ่าน SignalAlertRecord)

## Build / ตรวจจาก logcat

- `compileDebugKotlinAndroid` + `assembleDebug` BUILD SUCCESSFUL (2026-08-29) — APK ~105MB
- Log ใหม่: `🧑‍✈️ Supervisor APPROVE/VETO/ADJUST ...` ต่อสัญญาณ Unified SMC

หมายเหตุทางเทคนิค: K2 inference พังกับ `mapIndexedNotNull`+destructuring บน primitive array ใน
commonMain (eqPools) — แก้ด้วย explicit loop แทน
