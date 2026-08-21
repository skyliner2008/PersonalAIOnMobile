# Pine Script V6 — 8 กลยุทธ์ (Mirror ของ PersonalAIBot)

สคริปต์ชุดนี้ทำหน้าที่ **ตรวจสอบด้วยสายตา** ว่าจุดเข้า / SL / TP ของแต่ละกลยุทธ์ในแอป
ถูกต้องตามหลักการจริงหรือไม่ โดย mirror ตรรกะจากโค้ด Kotlin เป๊ะ ๆ
( semantics ใหม่ของ strategy-native SL/TP ที่ปรับเมื่อ 2026-08-20 )

## วิธีใช้

1. เปิด TradingView → Pine Editor → วางโค้ดทีละไฟล์ → Add to chart
2. ทุกไฟล์เป็น `strategy()` — จะเห็น ลูกศรจุดเข้า + เส้น Entry (ฟ้า) / SL (แดง) / TP (เขียว) + แดชบอร์ดมุมขวาบน
3. ปรับ input ได้ทุกตัว (lookback, buffer, RR) เพื่อเทียบกับผล optimize ในแอป

## ไฟล์รวม (แนะนำเริ่มที่ไฟล์นี้)

`00_AllStrategies_Combined.pine` — 8 กลยุทธ์ในสคริปต์เดียว เปิด/ปิดแยกทีละตัวใน settings
(group "เปิด/ปิดกลยุทธ์") แต่ละกลยุทธ์ถือ position ของตัวเอง (entry id แยก, 10% equity/ไม้)
และแดชบอร์ดแสดงสถิติแยกรายกลยุทธ์ (Trades / Win% / PF / Net) จาก closed trades จริง
เหมาะสำหรับเทียบพฤติกรรมทุกกลยุทธ์บนกราฟเดียวกัน; ไฟล์ 01–08 ไว้ดูทีละตัวแบบละเอียด

## Mapping กลยุทธ์ ↔ โค้ด Kotlin

| # | ไฟล์ | Kind | Entry (SignalMarkerProvider.kt) | SL/TP (StrategyParams.kt) |
|---|------|------|----------------------------------|---------------------------|
| 01 | `01_TSMOM_Momentum.pine` | MOM | ROC 20 พลิกเครื่องหมายข้าม 0 (edge) | swing 10 + 0.5×ATR14, clamp [0.3,3.5], TP=2R |
| 02 | `02_TrendFollowing_EMA.pine` | TR | EMA 50/200 cross | เหมือน MOM |
| 03 | `03_ShortTermReversal.pine` | REV | RSI14 < 30 + แตะ BB(20,2) lower, edge | swing 10 + 0.5×ATR (cap 2.5), TP = BB basis |
| 04 | `04_DonchianBreakout.pine` | DC | close ทะลุ Donchian 20 (ไม่รวมแท่งปัจจุบัน), state-change | exit band 10 + 0.5×ATR, TP=2R |
| 05 | `05_52WeekHigh.pine` | 52H | close / rolling high 2000 แท่ง ≥ 0.98 / ≤ 0.90, warmup 100 | เหมือน MOM |
| 06 | `06_EMA1460.pine` | E | EMA 14/60 cross | เหมือน MOM |
| 07 | `07_UTBot.pine` | UT | UT Bot flip key 2.0 × ATR6 | SL 2×ATR6, TP 4×ATR6 |
| 08 | `08_3BarReversal.pine` | 3BR | pattern 3 แท่ง (แท่ง2→แท่ง1→แท่ง0) | SL จุดสุดแท่งกลาง ∓ 0.2×ATR, floor 0.5×ATR, TP=2R |

## ไฟล์ทดลอง (experiment)

`01b_TSMOM_FlipExit.pine` — MOM แบบ exit ตามหลัก TSMOM ดั้งเดิม (ไม่มี TP ตายตัว)
เกิดจากผลทดสอบ 2026-08-20: 01 เดิม win ~35% แต่ RR จริง ~1.3 (TP 2R แทบไม่ถึง,
ไม้ถูกทางถูกปิดกลางทางตอน ROC พลิกกลับ) — เลือก exit mode ได้ 3 แบบ:
FLIP (ถือจน ROC พลิก + SL กันหายนะ) / TRAIL (chandelier 3×ATR) / PARTIAL (ปิดครึ่งที่ 1.5R + trail)
Entry เหมือน 01 เป๊ะ — แปะคู่กับ 01 บนกราฟเดียวกันเพื่อเทียบ PF ได้ตรงๆ

## ความต่างที่รู้ตัว (TV vs แอป)

- **EMA seeding**: Kotlin seed ด้วย SMA ของ `period` แท่งแรก, TV `ta.ema` seed ตั้งแต่แท่งแรกของชาร์ต
  → ค่า EMA จะต่างกันเล็กน้อยช่วงต้นกราฟ แต่ converge ไว
- **Fill**: ทุกสคริปต์ตั้ง `process_orders_on_close=true` (เข้าที่ปิดแท่งสัญญาณ) ให้ใกล้ engine ของแอป,
  commission = 0 — ถ้าต้องการเทียบ net จริงให้ใส่ค่าคอมของโบรกเอง
- **52H rolling high**: Kotlin ใช้ sliding-max deque บนหน้าต่าง `[i-L, i-1]` clamp ตามข้อมูลที่มี,
  Pine ใช้ `ta.highest(high[1], min(L, bar_index))` — พฤติกรรมเท่ากัน
- **ATR**: Kotlin ใช้ Wilder smoothing ซึ่งตรงกับ `ta.atr` ของ TV (RMA)
- **BB**: TV `ta.bb` ใช้ population σ (biased) — ตรงกับ Kotlin ที่หารด้วย N

## ขั้นต่อไป

เทียบสายตาเสร็จแล้ว → รัน backtest/optimize ในแอปทีละกลยุทธ์บน semantics ใหม่
เพื่อตัดสินว่ากลยุทธ์ไหนใช้ได้จริง (PF, win rate, avgR)
