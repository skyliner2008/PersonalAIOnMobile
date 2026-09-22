# OHLCV & Indicator Foundation V27 (2026-09-18)

> **หลักการที่ขับเคลื่อนงานนี้**
> ไม่เน้นให้ AI คำนวณเอง เพราะอาจ hallucination แล้วมั่วข้อมูลผิดๆ ให้ user
> **ระบบดึงค่า → คำนวณ → ส่งค่าจริงให้ AI วิเคราะห์เท่านั้น**
>
> ผลตรวจพบว่าปัญหาไม่ใช่ AI มั่ว แต่เป็น **ระบบป้อนตัวเลขปลอมให้ AI** — ซึ่งร้ายกว่า
> เพราะ AI ไม่มีทางรู้ว่าค่าที่ได้รับเป็นของจริงหรือ fallback

เอกสารตัวเต็มของ Phase 0–4 ดูสรุปย่อได้ที่ [[../00_System/log]]

---

## 1. สถาปัตยกรรม OHLCV ใหม่

```
                    ┌──────────────────────────────────┐
                    │  TaIndicators.TIMEFRAMES         │
                    │  (single source of truth)        │
                    │  canonical / MT5 / TV / millis   │
                    └────────────┬─────────────────────┘
                                 │ ใช้ร่วมกันทุกชั้น
      ┌──────────────────────────┼──────────────────────────┐
      ▼                          ▼                          ▼
┌───────────┐          ┌──────────────────┐        ┌────────────────┐
│ TvCandle  │◄────────►│ SmcApiService    │───────►│ OhlcvCentral   │
│ (SQLite)  │  persist │ fetchCandles     │  cache │ Store (memory) │
│           │          │ WithSource       │        │ LRU 64 series  │
│ UNIQUE:   │          └────────┬─────────┘        │ cap 1200 bars  │
│ symbol +  │                   │                  └────────────────┘
│ interval +│         ┌─────────┴──────────┐
│ source +  │         ▼                    ▼
│ ts        │   ┌───────────┐      ┌──────────────┐
│ is_closed │   │TradingView│      │Binance       │
│           │   │(หลัก)     │      │(คริปโตแท้    │
│ retention │   │OANDA →    │      │ เท่านั้น)     │
│ 6,000 แท่ง│   │FX_IDC →   │      └──────────────┘
└───────────┘   │TVC        │
                └───────────┘
                 ✂ Yahoo ถูกถอดออกจากเส้นทางนี้แล้ว
                   (ยังใช้กับราคา/fundamental ที่ TradingApiService)
```

### ลำดับการดึงข้อมูล
1. อ่านจาก DB — ซีรีส์ของ **source เดียว** ตามลำดับความน่าเชื่อถือ ไม่แตะเน็ตถ้าสดพอ
2. เติมส่วนที่ขาดจาก TV แบบ incremental (`computeDeltaFetchBars`)
3. ประวัติยังไม่ลึกพอ → `backfillHistory` ดึงย้อนหลังแล้ว persist (guard 30 นาที/ซีรีส์)
4. Binance fallback — เฉพาะคู่ที่ `isPossibleBinanceSymbol` ผ่าน

---

## 2. ต้องใช้กี่แท่งถึงจะพอ — `TaIndicators.Warmup`

อินดิเคเตอร์แบบ recursive smoothing (EMA, Wilder RSI/ATR/ADX) ให้ค่าออกมาตั้งแต่แท่งที่ `period`
แต่ค่านั้น **ยังเอียงตาม seed** ต้องเดินต่ออีก ~4 เท่าของ period จึงลู่เข้าค่าจริง (คลาดเคลื่อน < 0.1%)
ส่วน SMA/Donchian/Stochastic ใช้หน้าต่างตายตัว ต้องการแค่ period พอดี

| ชุด | period ยาวสุด | ขั้นต่ำที่ emit ได้ | **ค่าที่ระบบใช้** | constant |
|---|---|---|---|---|
| RSI14 / ATR14 / ADX14 / BB20 / Stoch / CCI | 20 | 29 | **150** | `FAST_SET` |
| MACD(12,26,9) | 35 | 35 | **200** | `MACD_SET` |
| Ichimoku(9,26,52+26) | 78 | 104 | **250** | `ICHIMOKU_SET` |
| **EMA200 / SMA200** | 200 | 200 | **800** | `FULL_SET` ← default |
| 52W High/Low (1D) | 252 | 252 | **300** | `YEARLY_SET` |
| Backtest | — | — | **5,000** | `BACKTEST_SET` |

**ก่อนแก้**: `recommendedMinBars` = 150 (TF ≤ 1h) / 300 (4h+)
→ EMA200 คำนวณไม่ได้ + `ema()` fallback เป็นราคาปิด = **"EMA200 = close" ทุกครั้ง**

**ขนาดจัดเก็บ**: 6,000 แท่ง × 8 TF × 20 symbol × ~60 bytes ≈ 58 MB — รับได้บนมือถือ

---

## 3. กติกา fail-closed (บังคับทั้งระบบ)

| สถานการณ์ | ต้องทำ | ห้ามทำ |
|---|---|---|
| แท่งไม่ครบ period | คืน `null` / ไม่ใส่ field ลง map | คืนราคาปิด, 0.0, หรือค่ากลาง |
| ดึงข้อมูลไม่ได้ | คืน error พร้อม `source` + `bars_used` | คืนค่าจาก source อื่นโดยไม่บอก |
| แท่งล่าสุดยังก่อตัว | `isClosed = false` และไม่นำไปคำนวณ | คิดเป็นแท่งสมบูรณ์ |
| indicator คำนวณไม่ได้ | ละ field นั้นไป | ใส่ default เช่น `rsi ?: 50.0` |
| snapshot ที่ส่งให้ AI | คืน null ทั้งก้อนถ้าตัวใดคำนวณไม่ได้ | ส่ง snapshot ที่มีค่าปลอมปนอยู่ |

ตัวอย่างที่ยังผิดกติกาอยู่ (อยู่ใน MT5 ที่พักงานไว้):
```kotlin
// Mt5ToolHandler.kt — ค่าพวกนี้ปลอมทั้งหมดเมื่อคำนวณไม่ได้
val rsi = TaIndicators.rsi(closes, 14) ?: 50.0
val adx = TaIndicators.adx(...) ?: AdxResult(15.0, 20.0, 20.0)
val st  = TaIndicators.supertrend(...) ?: SupertrendResult(currentPrice, true)
```

---

## 4. ทะเบียน field: ระบบ ↔ AI ต้องตรงกันเสมอ

```
IndicatorAlertProvider.SUPPORTED_FIELDS  ←──┐
              ▲                             │  ล็อกด้วย
              │ ต้องเป็น superset           │  IndicatorFieldContractTest
              │                             │
AlertFieldCatalog.INDICATORS.fields  ───────┘
              │
              ├──► describeForAi()          → tool description ที่ AI เห็น
              └──► isFieldSupported()       → validator ตอนสร้าง alert
                                              (JarvisOrchestrator.kt:1010)
```

**บัคเดิม**: catalogue โฆษณา `adx14` / `resistance1` / `donchian_upper` / `obv` / `mfi14` / `ichimoku_*`
แต่ provider ส่งออก `adx` / `r1` / `donchian20_high` และไม่มี 3 ตัวหลังเลย
→ `AutomationEvaluator.evaluate` ทำ `data[field] ?: return false` — **เงียบสนิท**
ผู้ใช้ตั้ง alert เห็นว่า active แต่ไม่มีวันยิงและไม่มีใครบอก

---

## 5. อินดิเคเตอร์ที่รองรับ (หลัง Phase 2)

| กลุ่ม | รายการ |
|---|---|
| MA | ema7/9/14/20/21/50/100/200, sma20/50/100/200, ema_cross_state, ema50_200_spread, ema20_50_spread |
| Momentum | rsi7/14/21, rsi14_prev, macd(+signal/hist), stoch_k/d, cci20, ao, **roc**, **williams_r**, **mfi14** |
| Trend/Vol | **adx14**/di_plus/di_minus, atr7/14/21, **atr_pct**, bb_upper/basis/lower/width/percent_b, supertrend(+direction) |
| Volume | **vwap (รายเซสชัน)**, vwap_distance_pct, **volume_sma20**, volume_ratio20, **obv**, **obv_slope20** |
| Levels | pivot, **resistance1-3**, **support1-3**, **donchian_upper/mid/lower** |
| Ichimoku | **tenkan, kijun, cloud_top, cloud_bottom, chikou** |
| Fibonacci | **fib_236/382/500/618/786**, swing_high, swing_low |

**ตัวหนา** = เพิ่มใหม่หรือแก้ชื่อใน Phase 2

---

## 6. เทสต์ที่ล็อกพฤติกรรมไว้

| ไฟล์ | ล็อกอะไร |
|---|---|
| `TradingIndicatorTimeframeTest` | ทุก TF แปลงครบ 4 รูปแบบ ไม่ตกเงียบเป็น 1h; ema/sma คืน null เมื่อแท่งไม่พอ |
| `OhlcvStoreIntegrityTest` | bucket ไม่ผูก epoch (1D/1W); `isClosed` ถูกต้อง; store เลือก source ตาม priority + มีเพดาน |
| `IndicatorFieldContractTest` | ทุก field ที่ประกาศส่งออกได้จริง; แท่งไม่พอต้องละ field ไม่ใช่ปลอมค่า |
| `TradingToolReachabilityTest` | ไม่มี declaration ซ้ำ; ทุก tool มี route; ทุก tool เรียกถึงได้อย่างน้อย 1 บริบท |
| `IndicatorDefinitionParityTest` | EMA/RSI/ATR/ADX/MACD/BB/Supertrend ตรงตามนิยาม ±1e-9; ผลไม่ขึ้นกับ TF; VWAP รีเซ็ตรายเซสชัน |
| `TradingViewIndicatorSnapshotTest` | snapshot ทุกค่าตรงกับ TaIndicators เป๊ะ (กัน implementation ที่ 2 กลับมา) |

รวม **423 tests ผ่านทั้งหมด**

---

## 7. งานที่ยังเหลือ

### MT5 (พักไว้ตามคำสั่ง)
- `Mt5RiskGate` SL/TP wrong-side guard เป็น dead code — `trading_mt5_order` ไม่ประกาศ param `price`
- P6 `RiskEngine` เรียกไม่ถึงเลย — `strategy_gate`/`value_per_price_unit` ไม่อยู่ใน schema
- `validateCloseAll` ไม่ถูกเรียกจาก production — close-all ยิงได้โดยไม่ต้องยืนยัน
- `RiskEngine` บวก risk เข้ากับ margin ที่เป็นคนละหน่วย → exposure cap กัน over-leverage ไม่ได้
- `startOfLocalDay` ตัดวันที่ UTC ทั้งที่ชื่อบอกว่า local → daily-loss limit วัดผิดช่วง

### Indicator — ปิดงานแล้วในรอบ 2 (2026-09-18)
- ~~ADX off-by-one~~ **แก้แล้ว** — DX ตัวแรกที่แท่ง `period` ถูกนับเข้าไปแล้ว
- ~~VWAP session offset~~ **แก้แล้ว** — `sessionOffsetHoursFor()` ตั้งค่าตามประเภทสินทรัพย์
  (FX/โลหะ −2 ชม. = 22:00 UTC, คริปโตใช้ขอบวัน UTC)
- ~~implementation ซ้ำ~~ **รวมเหลือตัวเดียวแล้ว** — `TradingViewIndicatorSnapshot` (ตัวที่ 4 ที่เจอทีหลัง)
  delegate ไป TaIndicators ทั้งหมด ลบ helper ซ้ำ 180 บรรทัด
- **Parity harness เสร็จแล้ว** `IndicatorDefinitionParityTest` — เทียบกับ reference implementation
  ที่เขียนตามสูตรตรงๆ บน 6 ชุดข้อมูล × ทุก period × ทุก TF

### ยังเหลือ
- **Parity เทียบตัวเลขจาก TradingView ของจริง** — ต้องมี fixture ที่ export จาก TV มาเทียบ
  ปัจจุบันยืนยันได้ว่า "ตรงตามนิยามของอินดิเคเตอร์" แต่ยังไม่ได้ยืนยันว่า "ตรงกับที่ TV แสดงบนจอ"
  (สองอย่างนี้ควรตรงกัน แต่ TV มีรายละเอียดเช่น session/holiday handling ที่ต่างออกไปได้)
- VWAP ของหุ้นยังใช้ขอบวัน UTC — ควรใช้เวลาเปิดตลาดของแต่ละ exchange
