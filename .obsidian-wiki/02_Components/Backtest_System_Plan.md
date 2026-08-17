# 📋 แผนระบบ Backtest Strategies บนมือถือ (2026-08-17)

> เป้าหมาย: ดึงแท่งเทียนย้อนหลังจากวันที่ระบุถึงปัจจุบัน → รัน backtest กลยุทธ์ทั้ง 8 ตัวของ Signal system → วัดผล/หา params ที่ดีที่สุด/ตรวจ overfitting → ป้อนผลกลับเข้าระบบเทรดจริงให้แม่นขึ้น
> แหล่งอ้างอิงโค้ดตัวอย่าง: `OLD_Code/ai-trading-agent-main` (backtest suite ครบ) + `OLD_Code/moss-trade-bot-skills-main` (evolution loop + regime)

---

## 1. สิ่งที่มีอยู่แล้วในแอป (รีใช้ได้ทันที)

| ชิ้น | ไฟล์ | ใช้ต่ออย่างไร |
|---|---|---|
| สัญญาณ 8 กลยุทธ์ (pure function) | `SignalMarkerProvider.compute(candles, maxPerKind)` | ใช้ generate signals บนชุดข้อมูลย้อนหลังขนาดเท่าไรก็ได้ ไม่ต้องแก้ |
| สูตร TP/SL เฉพาะกลยุทธ์ | `SignalAlertProvider.computeTpSl()` | ใช้เป็น exit model ของ backtest engine |
| Mini-backtest (300 แท่ง, R-multiple) | `SignalAlertProvider.fetchStats()` | เป็น prototype กติกา (SL ชนก่อน=แพ้, ชนทั้งคู่ถือแพ้, timeout คิด R จากปิดสุดท้าย) — engine ใหม่ต้องสอดคล้องกติกานี้ |
| ผลจริงหน้างาน | ตาราง `SignalAlertRecord` + `trackSignalOutcomes()` | ใช้เทียบ backtest vs live (forward validation) |
| ดึงแท่งเทียน TV | `SmcApiService` (websocket `fetchTvHistoryBars` สูงสุด 5,000 แท่ง/ครั้ง) | ต่อยอดเป็น history paging |
| กราฟในแอป | Chart Dashboard (Lightweight Charts v5.1) | วาด equity curve / drawdown |
| การ์ดแชท 3D | `MessageBubble` (metadata kind=signal/alert) | เพิ่ม kind=backtest |
| AI สรุป/วิเคราะห์ | `generateAiText` fallback chain | ใช้ใน evolution reflection |

## 2. สิ่งที่ศึกษาจาก OLD_Code

### ai-trading-agent-main/backend-app/backtest/ (Python ~1,150 บรรทัด — port ได้ตรงๆ)
- **engine.py** — bar-by-bar, ถือทีละ 1 position, เช็ก SL ก่อน TP (conservative), คิด spread/commission, equity curve, metrics: win-rate / profit factor / maxDD / Sharpe
- **optimizer.py** — grid search (≤500 combos), composite score = Sharpe × PF × winRate, กรอง min trades
- **walk_forward.py** — anchored/sliding windows, IS vs OOS Sharpe, overfitting ratio, bootstrap 95% CI, param stability (CV)
- **monte_carlo.py** — shuffle ลำดับ trades 1,000 รอบ → probability of ruin, p95 maxDD
- **statistical_tests.py** — permutation test (shuffle signals 500 รอบ → p-value ว่าชนะ random จริงไหม) — ส่วน cointegration ข้าม (ต้อง statsmodels)
- **overfitting.py** — composite overfitting score 0–100% (weights: WF 40% / permutation 25% / param stability 20% / MC 15%) + grade healthy/moderate/overfit + auto param grid

### moss-trade-bot-skills-main (เอาเฉพาะแนวคิด ไม่ port โค้ด)
- **evolution_guide.md** — "วิวัฒนาการ = backtest เป็นช่วงๆ + AI สะท้อนผล + ปรับ params": 7 หลักการ (ปรับทีละนิด ≤10%/ครั้ง, ห้าม overreact, ห้ามนิ่งเกิน 3 รอบ) + drift limit ±30% สำหรับ tactical params, ล็อก persona params
- **decision.py** — แนวคิด composite signal แบบถ่วงน้ำหนัก 5 มิติ (trend/momentum/revert/volume/volatility) + threshold — เป็น candidate "กลยุทธ์ที่ 9" แบบปรับน้ำหนักได้
- **regime.py** — จำแนกตลาด BULL/BEAR/SIDEWAYS (ADX+EMA slope / ATR vol / โหวตหลายอินดิเคเตอร์) — ใช้แยกสถิติต่อ regime
- **local_costs.py** — โมเดลค่าธรรมเนียม taker 4.5 bps
- **backtest.py (879 บรรทัด)** — replay-aligned เฉพาะแพลตฟอร์ม Hyperliquid (depth book, funding rate) — **ไม่ port** เฉพาะแพลตฟอร์มเกินไป

## 3. สถาปัตยกรรมที่เสนอ (Kotlin, on-device ทั้งหมด)

```
composeApp/.../automation/backtest/
├── BacktestModels.kt       — TradeRecord, BacktestResult, EquityPoint, RobustnessReport
├── BacktestEngine.kt       — port จาก engine.py (bar-by-bar + costs + equity curve)
├── ParamOptimizer.kt       — port จาก optimizer.py (grid search + composite score)
├── WalkForward.kt          — port จาก walk_forward.py (IS/OOS + overfitting ratio + param stability)
├── MonteCarlo.kt           — port จาก monte_carlo.py (shuffle trades)
├── PermutationTest.kt      — port จาก statistical_tests.py (shuffle signals)
├── OverfittingScore.kt     — port จาก overfitting.py (composite 0-100 + grade)
├── RegimeClassifier.kt     — port v1 (ADX + EMA50 slope) จาก moss regime.py
└── BacktestEvolution.kt    — segment loop + AI reflection (moss evolution concept)
```

**กลยุทธ์ที่ backtest ได้:** 8 ตัวเดิม (MOM/TR/REV/DC/52H/E/UT/3BR) ผ่าน SignalMarkerProvider + อนาคตเพิ่ม "composite weighted" จาก moss decision.py เป็นตัวที่ 9

**Params ที่ปรับได้ต่อกลยุทธ์:** TP/SL multipliers (ATR mult, RR ratio), entry filter (RSI threshold, ROC period, Donchian lookback), น้ำหนัก consensus

## 4. ข้อมูลย้อนหลัง — ประเด็นสำคัญที่สุด

ปัจจุบัน DB เก็บแค่ ~300 แท่ง (trim ตลอด) — backtest ต้องการหลักพันแท่ง ดังนั้น:

1. **แยก dataset backtest ออกจาก cache ปฏิบัติการ** — ตารางใหม่ `BacktestDataset` (symbol, tf, ts, OHLCV) ไม่ trim; ห้ามใช้ TvCandle ร่วมเพราะจะโดน trimTvCandlesByWindow กวาดทิ้ง
2. **History paging** — TV websocket ดึงได้ 5,000 แท่ง/ครั้ง ต้องทำหน้าต่างเลื่อนย้อนหลัง (ดึง 5,000 ล่าสุด → ขอชุดก่อนหน้า ts เก่าสุด → วนจนถึงวันที่ระบุ) — ต้องตรวจ `fetchTvHistoryBars` ว่ารองรับ from/to หรือต้องขยาย (งาน Phase 1)
3. **ความลึกข้อมูลโดยประมาณ (TV free):** 15m ~เดือนละ ~2,900 แท่ง, 1h ~7 เดือน/5,000 แท่ง, 1D หลายปี — แจ้งผู้ใช้ถ้าขอเกินที่ source ให้ได้
4. ตลาดปิด (เสาร์-อาทิตย์/forex) ไม่ใช่ปัญหาเพราะ backtest ใช้ข้อมูลย้อนหลังนิ่ง ไม่ต้อง real-time

## 5. Phases การทำงาน

### Phase 1 — Engine หลัก + ข้อมูลย้อนหลัง (รากฐาน)
- [ ] `BacktestModels.kt` + `BacktestEngine.kt` (port engine.py, กติกาสอดคล้อง fetchStats เดิม + เพิ่ม costs/equity)
- [ ] ตาราง `BacktestDataset` + history paging จากวันที่ระบุ (chunk 5,000 แท่ง)
- [ ] Tool `trading_backtest` (symbol@tf, strategy, from_date, spread/commission) → ผล: trades, win-rate, PF, maxDD, Sharpe, expectancy, equity curve
- [ ] การ์ดแชท kind=backtest (สรุปผล + ปุ่มดูกราฟ) — build + ทดสอบจริง
- ประมาณ: งานหนักสุดของโครงการ (~60%)

### Phase 2 — Optimizer + Robustness (คุณภาพของคำตอบ)
- [ ] `ParamOptimizer.kt` grid search + composite score
- [ ] `WalkForward.kt` + `MonteCarlo.kt` + `PermutationTest.kt` + `OverfittingScore.kt`
- [ ] Tool `trading_backtest_optimize` → best params + robustness report (overfitting %, grade, p-value, ruin prob)
- [ ] UI หน้าจอ Backtest (เลือก symbol/tf/วันที่/กลยุทธ์, แสดง equity curve บน Lightweight Charts)

### Phase 3 — AI Evolution + Regime (ปรับปรุงความแม่นอัตโนมัติ)
- [ ] `RegimeClassifier.kt` (BULL/BEAR/SIDEWAYS) + สถิติแยกต่อ regime (กลยุทธ์ไหนเกิดใน regime ไหน)
- [ ] `BacktestEvolution.kt` — แบ่งช่วงข้อมูล → รัน → ให้ AI สะท้อนผล (7 หลักการ moss) → ปรับ params ภายใต้ drift limit ±30% → รันต่อ → evolution_log
- [ ] Tool `trading_backtest_evolve`

### Phase 4 — ป้อนกลับเข้าระบบเทรดจริง (ปิดวงจร "แม่นขึ้น")
- [ ] ตาราง `StrategyTuning` (symbol, tf, strategy, tuned params, backtest score, updated_at)
- [ ] `SignalAlertProvider` อ่าน tuned params ต่อ symbol/tf แทนค่า default (TP/SL multipliers, เปิด/ปิดกลยุทธ์ที่ overfit)
- [ ] รายงานเทียบ backtest vs live (`SignalAlertRecord`) — forward validation ว่า tuning ได้ผลจริง
- [ ] (ออปชัน) น้ำหนัก consensus_signal ปรับตาม backtest ranking

## 6. ความเสี่ยง / ข้อจำกัด
- **ความลึกข้อมูล TV free tier** อาจไม่ถึงวันที่ผู้ใช้ขอ (เช่น 15m ย้อนปี) — ต้อง fallback 1h/1D หรือแจ้งขอบเขตชัดเจน
- **Overfitting คือศัตรูหลัก** — ทุกผล optimize ต้องผ่าน walk-forward + permutation ก่อนเสนอใช้จริง (grade overfit = ห้าม apply)
- **Performance บนมือถือ** — grid 500 combos × 5,000 แท่ง ใน Kotlin ทำได้ (วินาที-นาที) แต่ permutation 500 รอบอาจหนัก → ทำ background coroutine + progress + ลดรอบตามขนาดข้อมูล
- **moss backtest.py ไม่ port** — logic ผูก Hyperliquid (funding/depth book) เอาแค่แนวคิด evolution

## 7. ลำดับแนะนำ
Phase 1 ก่อนเสมอ (ไม่มี engine กับข้อมูล ทำอย่างอื่นไม่ได้) → Phase 2 ให้คำตอบ "params ไหนดีและเชื่อได้แค่ไหน" → Phase 3/4 ค่อยปิดวงจรอัตโนมัติ
