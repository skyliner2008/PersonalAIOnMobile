# TradingView Strategy Lab

## Purpose

Independent research harness สำหรับตรวจสอบว่า Strategy แต่ละตัวมี edge จริงหรือเพียงได้ผลจาก parameter ที่ไม่เหมาะสม/overfit

> **Research-only:** ผลจาก Lab ห้ามนำ parameter ไปเขียน production โดยตรง

## Tool

`tools/tradingview_strategy_lab.py`

รองรับ 2 แหล่งข้อมูล:

1. TradingView โดยตรงผ่าน `tvdatafeed`
2. CSV ที่ export จาก TradingView

Strategies ที่ตรวจพร้อมกัน:

- MOM
- TR
- REV
- DC
- 52H
- E
- UT
- 3BR

## Protocol

Historical data ถูกแบ่งตามเวลาแบบไม่ shuffle:

- Train 60% — parameter discovery
- Validation 20% — model/parameter selection
- Holdout OOS 20% — ห้ามใช้เลือก parameter

ทุก candidate รายงาน:

- trade count
- Profit Factor
- expectancy (R)
- win rate
- net P/L
- max drawdown
- Sharpe-like trade score
- TP/SL count
- long/short expectancy
- parameter-neighborhood stability

Backtest ของ Lab ใช้ closed-bar signal และเข้า **แท่งถัดไปที่ open** เพื่อหลีกเลี่ยงการให้ signal รู้อนาคตจากราคาปิดของแท่งเดียวกัน

## Parameter discovery

Lab ใช้ strategy-specific grids แทน grid เดียวทั้งระบบ และตรวจ local neighborhood ของ candidate ที่ดีที่สุด เพื่อแยก:

- **robust basin** — ค่าข้างเคียงยังให้ผลดี
- **parameter cliff** — ดีเฉพาะจุดเดียว มีความเสี่ยง overfit

ดังนั้นค่าที่ PF สูงสุดเพียงจุดเดียวไม่ถือว่าเป็นหลักฐานว่า Strategy ดี

## Run

```text
python tools/tradingview_strategy_lab.py --symbol OANDA:XAUUSD --interval 15m --bars 5000
```

ถ้าใช้ CSV:

```text
python tools/tradingview_strategy_lab.py --csv xauusd_15m.csv --symbol XAUUSD --interval 15m
```

Smoke test:

```text
python tools/tradingview_strategy_lab.py --symbol OANDA:XAUUSD --interval 15m --bars 1500 --quick
```

ติดตั้ง dependency สำหรับดึง TradingView โดยตรง:

```text
pip install tvdatafeed pandas numpy
```

## Outputs

ค่าเริ่มต้นจะสร้าง `strategy_lab_output/`:

- `strategy_summary.csv` — best candidate ของแต่ละ Strategy พร้อม IS/Validation/OOS
- `candidate_results.csv` — ทุก candidate ใน grid
- `run_metadata.json` — dataset range, source, split, cost assumptions และ Lab version

## Interpretation

อย่าตัดสิน Strategy จาก Train PF อย่างเดียว

ให้ดูตามลำดับ:

1. Validation expectancy/PF ยังเป็นบวกหรือไม่
2. Holdout OOS ยังเป็นบวกหรือไม่
3. OOS drawdown อยู่ในระดับรับได้หรือไม่
4. Long/Short มี asymmetry ผิดปกติหรือไม่
5. Neighborhood stability สูงหรือไม่
6. ผลยังอยู่เมื่อเปลี่ยนช่วงข้อมูลหรือ regime หรือไม่

Strategy ที่ Train ดี แต่ OOS แย่ หรือ stability ต่ำ ให้ถือว่า **ยังไม่มีหลักฐานเพียงพอ**

## Important limitation

Lab นี้เป็น **independent research implementation** ไม่ได้อ้างว่าแทน `SignalMarkerProvider`/`SignalAlertProvider` production ได้ 100% ในรอบแรก เป้าหมายคือสร้าง external baseline เพื่อค้นหา parameter/edge ที่ production optimizer ควรอธิบายได้ หากผลต่างกันมากระหว่าง Lab กับ App ต้องทำ parity review ก่อนใช้ผลใดเป็นเกณฑ์ตัดสิน

## Strategy Forensics — ผล R1-R5 (2026-08-27)

Dataset: OANDA:XAUUSD 15m, 5,753 bars (2026-06-01 → 2026-08-27), 302 candidates, split 60/20/20
Report: `strategy_lab_output/xauusd_15m_r1_r5/r1_r5_report.json`

### Verdict ต่อ Strategy (expectancy R: train / validation / holdout)

| Strategy | Train | Val | Holdout | Holdout PF | Stability | Verdict |
|---|---|---|---|---|---|---|
| REV | +0.22 | +0.24 | +0.24 | 1.49 | 0.60 | **ตัวเต็ง** — บวกครบ 3 phase เดียวในระบบ |
| MOM | +0.21 | +0.08 | +0.13 | 1.24 | 0.33 | บวกครบแต่ decay + stability ต่ำ → evidence component |
| E | +0.18 | -0.22 | +0.28 | 1.59 | 1.00 | ผันผวนตาม regime รุนแรง → evidence เท่านั้น |
| 3BR | +0.18 | -0.45 | +0.27 | 1.55 | 1.00 | validation พัง → evidence เท่านั้น |
| TR | +0.14 | +0.14 | -0.04 | 0.92 | 0.20 | ตัดออก (fail holdout + stability ต่ำสุด) |
| DC | +0.09 | +0.01 | -0.03 | 0.94 | — | ตัดออก (ไม่มี edge) |
| 52H | +0.03 | +0.11 | -0.01 | 0.98 | 0.50 | ตัดออก (ไม่มี edge) |
| UT | -0.03 | -0.25 | +0.05 | 1.08 | 0.50 | ตัดออก (ไม่มี edge สม่ำเสมอ) |

### สถานะ REV (ตัวที่ถูกเลือกโดย R1-R5 gates)

- Final gate: `promotion_ready = false`
- ผ่าน: holdout บวก, WFO consistency (3/4 windows), adverse-cost บวก
- ไม่ผ่าน: holdout เพียง 17 trades (< 30), permutation p = 0.45 (ยังแยกจากความฟลุ๊กไม่ได้)
- ข้อควรระวัง: holdout เป็น SELL 100% (long expectancy = 0) → edge อาจผูกกับ regime ช่วงข้อมูลนี้
- Monte Carlo p05 = -3R → downside มีจริง

### บทสรุปการยุบรวม

ยุบเหลือ **1 Unified Strategy Engine** โดยมี REV (RSI+Bollinger mean reversion) เป็น core setup ที่มีหลักฐานดีที่สุด แต่**ยังไม่ promote เป็น production** จนกว่า sample จะพอและ p-value < 0.05 — ต้องขยายข้อมูล (bars เพิ่ม + หลาย TF) ก่อน

## Cross-TF Forensics (2026-08-27, frozen datasets)

Datasets ใน `strategy_lab_data/`: M15 = 5,761 bars (3 เดือน), M5 = 6,337 bars (1 เดือน), H1 = 9,765 bars (20 เดือน) — ดึงผ่าน tvdatafeed แบบ nologin (จำกัด history)
Reports: `strategy_lab_output/xauusd_{15m_r1_r5_frozen,5m_r1_r5,1h_r1_r5}/`
เครื่องมือ: `strategy_research_r1_r5.py` รองรับ `--csv`, `--strategies` (part files ต่อ strategy, resume ได้), `--shard s/k` (แบ่ง grid ของ strategy เดียวเป็นชิ้น)

### Holdout expectancy (R) ของ best eligible candidate ต่อ strategy × TF

| Strategy | M15 | M5 | H1 | สรุป |
|---|---|---|---|---|
| REV | **+0.235** | -0.093 | +0.068 | ดีเฉพาะ M15, ไม่ replicate ข้าม TF → regime-bound |
| MOM | **+0.244** | -0.043 | +0.094 | บวก 2/3 TF แต่ไม่เสถียร |
| 52H | -0.007 | +0.167 | +0.167* | *H1 เพียง 4 trades — เชื่อไม่ได้ |
| DC | -0.113 | -0.066 | +0.219 | ดีเฉพาะ H1 |
| E | +0.216 | -0.378 | -0.061 | ผันผวนรุนแรง |
| TR | -0.017 | +0.044 | -0.023 | ≈ 0 ทุก TF |
| 3BR | — | — | +0.061 | ไม่มีหลักฐานพอ |

### บทสรุป Cross-TF

1. **ไม่มี strategy ใดบวกครบทั้ง 3 TF** และ **permutation p < 0.05 ไม่มีเลยทุก TF** → ยืนยันสิ่งที่สงสัย: ไม่มี standalone strategy ที่มี edge พิสูจน์ได้ ณ ตอนนี้
2. `promotion_ready = false` ทุก TF ทุก strategy — gates ทำงานถูกต้อง ป้องกัน false promotion
3. REV@M15 ยังเป็น single-TF candidate ที่ดีสุด แต่การไม่ replicate ข้าม TF แปลว่าต้องใช้เป็น **evidence component** ไม่ใช่ core ที่ตัดสินเอง
4. ข้อจำกัดข้อมูล: nologin จำกัด history — ถ้าต้องการ sample มากกว่านี้ให้ export จาก MT5 (mt5-core-server) หรือใช้ TradingView login
5. ทิศทางที่หลักฐานสนับสนุน: **ยุบเป็น Unified Engine ที่ strategy ทั้งหมดเป็น evidence, ไม่มีสิทธิ์ตัดสิน BUY/SELL เอง** ตามข้อเสนอ Unified V3

## Unified SMC MTF Strategy V1 (2026-08-27)

Tool: `tools/unified_smc_lab.py` — strategy ใหม่ที่รวมทุก TF เป็น setup เดียว (ไม่แข่งกันทีละ TF):

- **H4/H1 → Market Context**, **M30/H1 → Trend Context**, **M15 → Setup**, **M5 → Confirmation/Entry Timing** (M1 slot พร้อม แต่ nologin ดึงย้อนหลังได้แค่ ~3.5 วัน จึงใช้ M5 แทนชั่วคราว)
- Building blocks (causal ทั้งหมด): confirmed swing H/L, BOS/CHoCH(SMS), EQH/EQL liquidity pools, IDM (minor-swing inducement), FVG (active until mitigated), body/wick quality
- **SMC sequence:** sweep/tap zone (FVG/EQL/IDM) + displacement (CHoCH/BOS) ภายใน window เดียวกัน (`setup_win` 3–6 แท่ง) — ออกแบบตามหลัก sweep → displacement → entry
- **Hard rule:** context/trend อย่างเดียวไม่มีสิทธิ์เทรด — ต้องมี setup บน M15 เสมอ; ไม่มี confluence = HOLD (0)
- MTF join ใช้ `merge_asof` บน available_at = open + TF duration → ไม่มี look-ahead; signal ปิดแท่ง M15 → เข้า open แท่งถัดไป
- Scoring: `0.30*context + 0.20*trend + 0.30*setup + 0.20*confirmation` เทียบ threshold + margin (กัน long/short ชนกัน)

### ผลรอบแรก (overlap window 2026-07-27 → 2026-08-27, 2,113 M15 bars — M5 เป็นตัวจำกัด)

Chosen config (swing_L=5, setup_win=3, score_th=0.5, ไม่ใช้ IDM/wick):

| Phase | Trades | Expectancy R | PF |
|---|---|---|---|
| Train | 23 | +0.116 | 1.21 |
| Validation | 8 | +0.750 | 3.88 |
| Holdout | 9 | +0.296 | 1.64 |

- บวกครบ 3 phase ใน engine เดียว (ต่างจาก 8 strategies เดิมที่ไม่มีตัวไหนทำได้ข้าม TF)
- **แต่ `promotion_ready = false`**: holdout 9 trades (< 30), permutation p = 0.498 → ยังเป็น noise ได้

### ผลรอบสอง — MT5 data จริง 17 เดือน (2025-03-21 → 2026-08-27, 33,340 M15 bars)

Export ผ่าน `mt5-core-server/scripts/export_ohlcv.py` (MetaTrader5 package, page 5,000 bars × N, cap broker: M5/M1 ≈ 100,000 bars) → `strategy_lab_data_mt5/xauusd_{1m,5m,15m,1h}.csv`

| Phase | Trades | Expectancy R | PF |
|---|---|---|---|
| Train | 249 | +0.139 | 1.21 |
| Validation | 48 | +0.067 | — |
| Holdout | 57 | **−0.018** | 0.96 |

- **ผลบวกจากรอบแรกพิสูจน์แล้วว่าเป็น noise** — พอ sample ใหญ่จริง expectancy decay: train +0.14 → val +0.07 → holdout ≈ 0
- Best survivors (val>0, holdout>0, ho≥30 trades): holdout สูงสุด +0.018R / PF 1.02 ≈ เท่าทุนหลัง cost
- `promotion_ready = false`, permutation p = 0.92 → **V1 ยังไม่มี edge ที่พิสูจน์ได้**
- บทเรียนสำคัญ: gates + sample ใหญ่คือสิ่งที่กัน false promotion — กระบวนการวิจัยทำงานถูกต้อง
- ทิศปรับปรุง V2: session/regime filter, SL/TP ตาม structure (สวน swing) แทน ATR คงที่, entry แบบ limit ที่ FVG retrace แทน market open, M1 entry timing (มีข้อมูล 3.3 เดือนแล้ว)

### ผล V2 — structure SL/TP + FVG limit + session filter (2026-08-27, MT5 33,340 M15 bars, 64 candidates)

เปลี่ยน execution เป็น SMC-native (`simulate_v2`): SL สวน last confirmed swing ± 0.25 ATR buffer, TP ที่ opposing swing (ถ้า RR ≥ 1.5) หรือ R-multiple, entry แบบ limit ที่ FVG midpoint (fill ภายใน 6 แท่ง) หรือ market, session filter London/NY 07–16 UTC

- **15/64 candidates บวกครบ 3 phases** (V1 ทำได้ 0) — train expectancy พุ่งจาก +0.14 → +0.44..+0.75R ด้วย structure SL/TP
- Balanced ที่สุด: market entry, score_th 0.6 → train +0.60 (107 trades) / val +0.12 (31) / **holdout +0.36, PF 1.55** (30)
- แต่ chosen-by-score ยัง decay (train +0.75 → val +0.04 → ho +0.09), permutation p = 0.90, holdout median R ติดลบ (กำไรจาก right tail), max consecutive loss = 9
- `promotion_ready = false` — **ทิศทางเป็นบวกครั้งแรก (gradient ถูก) แต่ edge ยังเล็กและยังพิสูจน์ significance ไม่ได้**
- บทเรียน: selection ของ V2 ใช้ train+val score ล้วน ไม่มี neighborhood stability → ชอบหยิบตัว overfit; V3 ต้องใส่ stability + WFO ในการเลือก candidate

### ผล V3 — robust selection (2026-08-27, ข้อมูลชุดเดิม, 64 candidates)

Selection = `0.6*train + 0.4*validation + 8*stability` (perturb ทุก param 1 ระดับ ประเมินบน validation), eligibility เพิ่ม `stability >= 0.5`, chosen ผ่าน WFO 4 windows + cost stress (spread×1.5, adverse slippage) + Monte Carlo + permutation

Chosen (market entry, score_th 0.7, tp_r 3.0, no idm/session):

| Test | ผล | ผ่าน? |
|---|---|---|
| Holdout | +0.089R, PF 1.10, 19 trades | บวก แต่ sample < 30 ❌ |
| Walk-forward | w1 +0.71 / w2 +0.79 / w3 −0.27 / w4 −0.65 | **2/4 — edge หายในช่วงหลัง** ❌ |
| Cost stress (adverse) | +0.082R, PF 1.09 | ✅ |
| Permutation | p = 0.90 | ❌ |
| Monte Carlo | p50 +1.7R, p05 −11.2R, p95 DD 13.3R | เสี่ยงสูง |

- `promotion_ready = false` — **edge มีจริงในบาง regime (W1–W2 แรง) แต่ decay ตามเวลา → ขาด regime filter โดยแท้**
- ทิศ V4 ชัดเจนจากหลักฐาน: ต้องมี **Regime Detector** (ตามข้อเสนอ Unified V3 เดิม) คัดกรองว่าตลาดช่วงไหน engine นี้ควรทำงาน — เช่น ให้เทรดเฉพาะเมื่อ H1 structure trend ชัด + volatility อยู่ในกรอบ ไม่ฝืนใน CHAOTIC/range แคบ

### ผล V4 — Regime Detector Layer A (2026-08-27, ข้อมูลชุดเดิม, 384 candidates)

Regime gate บน H1: TREND (ADX ≥ threshold + structure trend ชัด) / RANGE / CHAOTIC (ATR percentile > p90 หรือ < p10 → ห้ามเทรด); simulate ปรับเป็น sparse-signal iteration (เร็วขึ้นมาก รองรับ grid ใหญ่)

- **75/384 candidates บวกครบ 3 phases** และ **top cluster ทั้งหมดเป็น `regime=trend_only`**: holdout **+0.63..+0.77R, PF 2.09–2.35** — Regime Layer ยืนยันว่าช่วยจริงและเป็น component ที่ถูกต้อง
- **แต่** cluster นั้น holdout เพียง 11–15 trades และ neighborhood stability ต่ำ (0.11–0.33) → gate ที่ stability ≥ 0.5 เลยยังเลือกตัว regime=off (stab 0.56) ที่ holdout อ่อนกว่า (+0.089R)
- WFO ของ chosen ยัง decay (w3/w4 ติดลบ), permutation p = 0.90, `promotion_ready = false`
- สรุป: **trend_only regime + execution แบบ V2 คือ core ที่ใกล้ที่สุด** — ขาดเพียง robust basin: V5 ควร zoom grid รอบ cluster trend_only (score_th/setup_win/tp_r/adx_th ละเอียดขึ้น) เพื่อหา parameter ที่ holdout สูง AND stability ≥ 0.5 พร้อมกัน

### ผล V5 — เจอ robust basin แล้ว (2026-08-27, 1,152 candidates, shard×4 + feature cache)

Zoom grid รอบ trend_only: `adx_th 18–30 × score_th 0.55–0.75 × setup_win 2–6 × tp_r 2–4 × entry × session × idm`
เครื่องมือเพิ่ม: `--shard s/k` + feature pickle cache (fingerprint กัน stale) + `tools/unified_smc_gatecheck.py` สำหรับรัน gates ครบชุดบน candidate ใดก็ได้

**Robust basin พบที่:** `adx_th=18, entry=limit, use_idm=true, tp_r=2–3, setup_win 3–4, score_th 0.55–0.75, session=false`
→ 13 candidates ที่ stability ≥ 0.5 AND บวกครบ 3 phases พร้อมกัน

Basin representative (`adx_th 18, score_th 0.65, setup_win 4, tp_r 3.0, limit, idm on`):

| Test | ผล | ผ่าน? |
|---|---|---|
| Holdout | **+0.49R, PF 1.73** (16 trades) | บวก ✅ แต่ n < 30 ❌ |
| Walk-forward | w1 −0.09 / w2 +0.70 / w3 +0.06 / **w4 +0.99** | **3/4 ✅ (window ล่าสุดแรงสุด — ต่างจาก V3/V4 ที่ decay)** |
| Cost stress (adverse) | +0.488R, PF 1.72 | ✅ |
| Permutation | p = 0.38 | ❌ |
| Monte Carlo | p50 +7.9R, p05 −4.2R | — |

- **ผ่าน 3/5 gates** — ดีที่สุดตั้งแต่เริ่มวิจัย; ที่ตกคือ sample size และ significance
- สาเหตุโครงสร้าง: engine ที่ selective (regime + confluence + limit fill) เทรดแค่ ~16 ไม้ / holdout 3.4 เดือน → permutation ไม่มีทางผ่านกับ sample ขนาดนี้
- ข้อสรุปเชิงตัดสินใจ: **basin นี้คือ candidate สำหรับ forward test (paper/WATCH mode)** เพื่อสะสม live sample — ไม่ใช่เพิ่ม grid อีก เพราะจะเสี่ยง curve-fit บนข้อมูลเท่าที่มี
- หมายเหตุ: ตัวที่ selection_score สูงสุดยัง fail holdout (−0.34R) — ย้ำว่า score-based selection หลอกได้เสมอ, basin + gates เท่านั้นที่เชื่อได้

## Next stage

ขั้นตอน Forensics ข้าม TF เสร็จแล้ว (ผลข้างบน) — ขั้นต่อไปคือออกแบบ Unified Strategy Engine:

```text
Cross-TF evidence (เสร็จแล้ว)
        ↓
Regime Detector (TREND/RANGE/BREAKOUT/CHAOTIC)
        ↓
Evidence Matrix (REV/MOM/DC/52H เป็น component, ตัด TR/E/UT/3BR ออกจาก signal generation)
        ↓
Single Decision Gate → BUY / SELL / HOLD (HOLD = first-class)
        ↓
ปรับจูนผ่าน Lab protocol เดิม (60/20/20 + permutation + WFO)
```
