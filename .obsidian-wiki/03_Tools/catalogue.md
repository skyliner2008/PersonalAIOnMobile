# JARVIS Tool Catalogue

สรุปรายการเครื่องมือที่ agent สามารถเรียกใช้ได้จากโค้ดปัจจุบัน โดยอ้างอิงจาก `ToolRegistry`, `TradingToolDefinitions`, `SmcToolDefinitions` และ `FileToolDefinitions`

## ภาพรวมหมวดเครื่องมือ
- Built-in และ Logic Tools: 13
- Trading Tools: 22
- MT5 Bridge Tools: 19
- SMC Tools: 5
- File Management Tools: 7
- Camera and Vision Tools: 9

> หมายเหตุ: จำนวนด้านบนอ้างอิงจากชุด declaration ที่ลงทะเบียนอยู่ในโค้ดปัจจุบัน บางรายการเป็น legacy tool ที่ยังคงถูก register ไว้เพื่อความเข้ากันได้ย้อนหลัง

## 1. Built-in และ Logic Tools
- `get_current_datetime` - คืนวันที่และเวลาปัจจุบัน
- `calculate` - คำนวณนิพจน์ทางคณิตศาสตร์
- `remember_fact` - บันทึกข้อมูลลง long-term memory
- `recall_memory` - ดึงข้อมูลจาก memory
- `convert_units` - แปลงหน่วย
- `set_reminder` - ตั้ง reminder หรือ TODO
- `translate_text` - แปลภาษา
- `summarize_text` - สรุปข้อความ
- `search_web` - ค้นข้อมูลล่าสุดจากอินเทอร์เน็ต
- `system_run_diagnostics` - รัน health check และ diagnostic report
- `system_check_connectivity` - ตรวจการเชื่อมต่อกับ network และ API หลัก
- `mt5_place_order` - legacy bridge สำหรับส่งคำสั่ง BUY/SELL ไปยัง MT5
- `mt5_close_position` - legacy bridge สำหรับปิด position

## 2. Trading Tools
- `trading_price` - ราคาปัจจุบันของสินทรัพย์
- `trading_market_snapshot` - ภาพรวมตลาด
- `trading_top_gainers` - รายการสินทรัพย์ที่บวกแรง
- `trading_top_losers` - รายการสินทรัพย์ที่ลบแรง
- `trading_technical_analysis` - TA หลัก เช่น RSI, MACD, EMA, BB
- `trading_multi_timeframe` - วิเคราะห์หลาย timeframe
- `trading_bollinger_scan` - สแกน Bollinger squeeze
- `trading_oversold_scan` - สแกน RSI oversold
- `trading_overbought_scan` - สแกน RSI overbought
- `trading_sentiment` - รวมศูนย์ Sentiment ทุกมิติเป็นหนึ่งเดียว (JARVIS Master All-in-One Sentiment Engine: คำนวณ Composite Sentiment Index 0–100 พร้อม Visual Meter Bar, 4 เสาหลัก News, Market F&G, Binance Derivatives Positioning, Technical Consensus และ AI Contrarian Squeeze Signal รองรับทั้งภาพรวมโลก Global Macro และเจาะจงรายสินทรัพย์)
- `trading_news` - ข่าวการเงินและข่าวรายสัญลักษณ์ พร้อม AI Sentiment Bias Score (-10 ถึง 10)
- `trading_combined` - รวม Confluence ทุกมิติ: TA + Multi-Source Sentiment + Positioning + Financial News
- `trading_fundamental_analysis` - วิเคราะห์ปัจจัยพื้นฐาน, งบดุล (Balance Sheet), งบการเงิน และมัลติเปิลประเมินมูลค่า (P/E, P/S, P/B, EV, Debt, Cash, Free Float) พร้อมบทวิเคราะห์ AI เชิงลึก 6 มิติ รองรับหุ้นไทย (SET/MAI) และสากล (US/Global)
- `trading_fear_greed` - ดัชนีความกลัวและความโลภ (Dual-Market: Crypto alternative.me และ CNN US Stock Market พร้อม 7 Sub-indicators เช่น VIX, Put/Call)
- `trading_macro_calendar` - ปฏิทินเศรษฐกิจ
- `trading_correlation_matrix` - ความสัมพันธ์ระหว่างสินทรัพย์
- `trading_position_sizing` - คำนวณขนาด position
- `automation_manage_alerts` - จัดการ price/rule alert
- `trading_deep_analysis_suite` - ชุดวิเคราะห์เชิงลึก
- `trading_harmonic_scan` - สแกน harmonic pattern
- `trading_elliot_modern_analysis` - วิเคราะห์ Elliott Wave สมัยใหม่ (Swing-based Wave Counting + Momentum Stage + Graduated Confidence, รองรับ interval parameter)

## 3. MT5 Bridge Tools

### Core Execution
- `trading_mt5_order` - ส่งคำสั่งเปิด order
- `trading_mt5_close_position` - ปิด position
- `trading_mt5_modify_position` - แก้ไข `SL` และ `TP`

### Core Broker Queries
- `trading_mt5_account_info` - ข้อมูลบัญชี เช่น equity, balance, margin
- `trading_mt5_list_positions` - รายการ positions ที่เปิดอยู่
- `trading_mt5_list_orders` - รายการ pending orders
- `trading_mt5_list_history` - ประวัติ deal หรือ order
- `trading_mt5_candles` - OHLCV จาก broker
- `trading_mt5_symbol_info` - ข้อมูล symbol เช่น spread และ digits
- `trading_mt5_close_all` - ปิดทุก position
- `trading_mt5_break_even_all` - เลื่อน stop loss เป็น break-even
- `trading_mt5_snapshot` - snapshot ของ account + positions + orders
- `trading_mt5_trade_actions` - audit trail ของ action ฝั่ง MT5

### Advanced Intelligence
- `trading_mt5_market_scanner` - market scanner จากข้อมูล broker
- `trading_mt5_correlation_radar` - วิเคราะห์ correlation ของ watchlist
- `trading_mt5_sentiment_gauge` - sentiment gauge สำหรับมุมมอง execution
- `trading_mt5_institutional_flow` - ภาพรวม institutional flow
- `trading_mt5_economic_radar` - economic risk mapping
- `trading_mt5_trade_journal` - trade journal พร้อม AI scoring

## 4. SMC Tools
- `trading_smc_analysis` - dashboard SMC แบบเต็ม
- `trading_smc_sweeps` - ตรวจจับ liquidity sweep หลาย timeframe
- `trading_smc_liquidity` - liquidity zones และ confluence
- `trading_smc_orderblocks` - order blocks พร้อม FVG confirmation
- `trading_smc_structure` - market structure เช่น `BOS` และ `CHoCH`

## 5. File Management Tools
- `file_list` - list ไฟล์ใน path
- `file_read` - อ่านไฟล์ข้อความ
- `file_write` - สร้างหรือเขียนไฟล์
- `file_delete` - ลบไฟล์
- `file_analyze` - วิเคราะห์ไฟล์ `PDF`, `Office`, และภาพด้วย OCR
- `file_move` - ย้ายหรือจัดระเบียบไฟล์
- `file_search` - ค้นหาไฟล์ตามชื่อหรือเงื่อนไข

## 6. Camera and Vision Tools
- `vision_activate` - เปิด live vision
- `vision_deactivate` - ปิด live vision
- `camera_analyze_scene` - วิเคราะห์ภาพ snapshot
- `camera_detect_objects` - ตรวจจับวัตถุ
- `camera_read_text` - OCR จากกล้อง
- `camera_switch_provider` - เปลี่ยน vision provider
- `camera_switch_mode` - เปลี่ยนโหมดการทำงานของกล้อง
- `voice_get_profiles` - ดูรายการเสียงที่รองรับ
- `voice_set_profile` - เปลี่ยน voice profile

## 7. Strategy Library Tools (Quantpedia knowledge base — offline)
- `strategy_list` - ดูหมวด/รายชื่อกลยุทธ์ 60 แบบ (9 หมวด: Momentum, Value, Volatility, Seasonality, Reversal, Pairs, Macro, Carry, Crypto)
- `strategy_search` - ค้นหากลยุทธ์จาก keyword (ชื่อ/หมวด/คำอธิบาย)
- `strategy_explain` - ดูคำอธิบาย + โค้ด QuantConnect (Lean) เต็มของกลยุทธ์ — reference เท่านั้น ไม่ได้รันบนเครื่อง

## แนวทางการเลือกใช้ Tool
- ใช้กลุ่ม `trading_*` เมื่อต้องการ market analysis จากแหล่งข้อมูลภายนอกหรือ layer วิเคราะห์
- ใช้กลุ่ม `trading_mt5_*` เมื่อต้องการข้อมูลหรือ action ที่ผูกกับบัญชี broker จริง
- ใช้กลุ่ม `trading_smc_*` เมื่อต้องการโฟกัส Smart Money Concepts โดยเฉพาะ
- ใช้กลุ่ม `file_*` สำหรับงานเอกสาร ไฟล์ และ OCR
- ใช้กลุ่ม `vision_*` และ `camera_*` เมื่อต้องการข้อมูลจากภาพหรือกล้อง
- ใช้กลุ่ม `strategy_*` เมื่อต้องการความรู้กลยุทธ์เชิงวิชาการ (อธิบาย/เปรียบเทียบ/ปรับใช้) จากคลัง Quantpedia ในเครื่อง

## ข้อสังเกตสำคัญ
- เอกสารเวอร์ชันเก่าหลายแห่งยังระบุจำนวน tool เป็น `71` แต่จาก registry ปัจจุบันมีจำนวนมากกว่านั้น
- ระบบ MT5 ตอนนี้เป็นสถาปัตยกรรม `broker-first` และมี separation ชัดเจนจากฝั่ง `trading_*`
- Auto-trading รุ่นล่าสุดพึ่งพาทั้ง `trading_*`, `trading_smc_*` และ `trading_mt5_*` ร่วมกัน

**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[00_Tool_to_Strategy_Map]] | [[11_MT5_Full_Agent_Control_V17]]

## อัปเดต 2026-09-18 — Wake Engine Live + Backup V29

ดู [[../07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29]]
- **`trading_signal_anticipation`** = ระบบปลุก AI (แยกจาก signal alert) — action: create / scan / list_factors / config
  (factors, add_factors, remove_factors, hourly_budget, daily_budget, cooldown_bars) / learning / inspect / recommend / status / summary
- **action `summary`** (2026-10-04, `automation/wake/WakeVerdictSummary`) = ผลการคาดการณ์จากตาราง `AiWakeView` (1 แถวต่อการปลุก):
  ปลุกกี่ครั้ง · แจ้งเตือน (NOTIFY) กี่ครั้ง แยก BUY/SELL พร้อมเหตุผลและผลจริง (ชน TP / SL / หมดเวลา / ยังติดตาม, รวม R) · ไม่แจ้ง (SKIP)
  กี่ครั้ง AI เอียงทิศไหน หมวดเหตุผล (TF ขัดแย้ง · ตลาดเงียบ/ไร้ทิศทาง · สภาพคล่อง · ผันผวน · RR · มุมมองเดิมยังเปิด · ระบบตัดเพราะสวน
  เทรนด์) + ประโยคที่ AI ใช้บ่อย + SKIP ที่ราคาปิดห่างเกิน 3 ATR เมื่อครบ 12 แท่ง (ไม่ใช่จุดสุดโต่งระหว่างทาง — แกว่งเกิน 2 ATR ใน
  12 แท่งเป็นเรื่องปกติ) · ชื่อสินทรัพย์มีชื่อไทยกำกับทุกที่ ("BTCUSDT (บิทคอยน์)") · `period` = today / 24h (ค่าเริ่มต้น) / 7d / 30d / all
  (หรือ Nh / Nd) · ไม่ระบุ `symbol` = ทุกสินทรัพย์ ("ทอง" → XAUUSD, "BTC" → BTCUSDT) · ข้อความเป็นบรรทัด ไม่มีตาราง ให้ Live เล่าต่อ
  · Live ทุกโหมด (CONTROL / DRIVE / PET) ได้ tool นี้ (`ToolRegistry.getLiveGeminiTool` ตัดเฉพาะ tool ควบคุมหน้าจอนอกโหมดขับรถ)
  และไม่ถูกส่งไปทำเบื้องหลัง — ผลกลับในเทิร์นเดียวกัน · กฎใน `JarvisPersona` แชทข้อ 10 และ Live ข้อ 11
  · log: ผลทั้งก้อนทีละบรรทัดใต้ tag `WakeSummary` (log ของ Live ตัดผล tool ที่ 200 ตัวอักษร) และ `AiViewTracker: … ปิดผลมุมมอง AI N
  รายการ — แจ้งเตือน: BUY@84,844 → SL -1.00R (1 แท่ง)` บอกผลของการแจ้งเตือนแต่ละครั้ง
  · ทดสอบจริง 2026-10-04 13:35 (Live โหมด CONTROL): "ผลคาดการณ์วันนี้มีอะไรบ้าง" → `summary(period=today)` ตอบใน 31 ms พูดภายใน 2 วิ
  ตัวเลขตรง (ปลุก 239 · แจ้ง 3 · SKIP 236) แต่เรียก BTC ว่า "ทองคำ" → ใส่ชื่อไทยของสินทรัพย์ในผล + กฎ Live "เรียกสินทรัพย์ตามชื่อในผล"
- alert job ของระบบนี้ใช้ `tool_name = trading_anticipation`, field `wake >= 1` (ไม่ใช่ `trading_signal_alert` / `signal_anticipation` อีกแล้ว)
- `automation_manage_alerts` ที่ขอ anticipation ด้วยชื่อเก่าจะถูกแปลงเป็น `trading_anticipation`/`wake` อัตโนมัติ
- สำรอง/กู้คืน (การเรียนรู้ + ฐานข้อมูล) อยู่ใน Settings — ไม่ใช่ tool ของ AI (กันการกู้คืนทับข้อมูลโดยไม่ตั้งใจ)

## อัปเดต 2026-09-18 — Foundation V27

**เปลี่ยนแปลงที่กระทบ catalogue โดยตรง** (ดู [[../07_Trading_Intelligence/69_OHLCV_Indicator_Foundation_V27]]):

- **จำนวน declaration ลดลง 2 รายการ** — `automation_manage_alerts` และ `automation_manage_schedule`
  เคยประกาศซ้ำอย่างละ 2 ครั้ง (`associateBy` เก็บแค่ตัวหลัง) ตอนนี้เหลืออย่างละ 1
- **`trading_elliot_modern_analysis` ใช้งานได้จริงแล้ว** — เดิม handler ผูกกับชื่อ branch `trading_elliot_wave`
  ที่ไม่มีใครเรียก ทำให้ tool คืน `"Unknown market/technical tool"` ทุกครั้งแม้ implementation จะพร้อม
- **`trading_crypto_overview` / `trading_economic_data` / `automation_manage_schedule` กลับมาเรียกได้**
  — 3 ตัวนี้ไม่อยู่ใน `tvOnlyTradingFunctionNames` และ `mt5OnlyTradingFunctionNames`
  ทำให้ `TradingToolPolicy.isToolAllowed` คืน false ทุกบริบท
- **`trading_position_sizing` รับ param เพิ่ม**: `symbol`, `contract_size`, `lot_step`
  → คืนขนาดเป็น **lot** ที่ใช้กับ MT5 ได้จริง ไม่ใช่แค่ units ลอยๆ
- **`trading_indicators` (ผ่าน `automation_manage_alerts`) รองรับ field เพิ่มมาก**:
  OBV, MFI14, Ichimoku 5 ค่า, ROC, Williams %R, Donchian (มี mid), Pivot R1-R3/S1-S3,
  Fibonacci levels, atr_pct, volume_sma20, ema spreads — และแก้ชื่อที่เคยไม่ตรงกับที่ระบบส่งออกจริง
  (`adx` → `adx14`, `r1/s1` → `resistance1/support1`, `donchian20_high` → `donchian_upper`)

**กติกาใหม่ของทุก tool result**: ค่าที่คำนวณไม่ได้เพราะแท่งเทียนไม่พอจะ **ไม่ถูกส่งออกมาเลย**
ไม่ใช่ส่งค่า default ปลอม — ทุก payload ติด `source` / `timeframe` / `bars_used` ให้ AI ประเมินความน่าเชื่อถือเองได้

## อัปเดต 2026-10-03 — ตรวจผลของ tool ด้วยข้อมูลจริง

ผู้ใช้: "ตรวจสอบความถูกต้อง ของการเรียกใช้ tool ต่างๆ ว่าได้ผลลัพธ์จากข้อมูลจริง และถูกต้องหรือไม่"

**วิธีตรวจ (รันซ้ำได้)**: `python tools/tool_accuracy_audit.py` — เรียก 32 tool จริงผ่าน `AvatarSceneDataLiveDump`
แล้วเทียบกับแหล่งอิสระ: Binance (ราคา + แท่งเทียน → คำนวณอินดิเคเตอร์ใหม่แบบ TradingView), gold-api, Yahoo, alternative.me,
CoinGecko, ForexFactory · `--skip-gradle` ใช้ผล tool เดิม · ผลหลังแก้: ผ่าน 56 · ไม่ผ่าน 0

**กติกาสำคัญของ TradingView scanner** (`TvScannerColumns`): คอลัมน์ไม่มีต่อท้าย = **ค่ารายวัน**, `|60` = 1h, `|240` = 4h,
`|15` `|5` `|1` `|30` `|120` `|1W` `|1M` — มีแค่นี้ (`|3` `|360` `|480` `|720` `|1D` ได้ null) · ไม่มีคอลัมน์ `BB.width`,
`buy_signals`, `sell_signals`, `neutral_signals` · `change` = เปอร์เซ็นต์, `change_abs` = ราคา

**บั๊กที่เจอและแก้**
| tool | อาการ (ข้อมูลจริง 2026-10-03) | สาเหตุ |
|---|---|---|
| `trading_technical_analysis` (default 1h) | RSI 63.9 / MACD 1944 / ADX 41.7 / ATR 2199 / ค่ากลาง BB ต่ำกว่าเส้นล่าง / สัญญาณ STRONG BUY — ค่าจริง 1h: RSI 49.7, MACD −73, ADX 14, ATR 251, BUY | 1h ส่งคอลัมน์ไม่มีต่อท้าย = ค่ารายวัน ปนกับค่า 1h ที่คำนวณเอง |
| `trading_multi_timeframe` | แถว 1h เป็นค่ารายวัน แถว 1D ว่าง (RSI N/A, HOLD) | เดียวกัน + `|1D` ไม่มีจริง |
| `trading_sentiment` เสาเทคนิค | HOLD (Score 0) ทุกครั้ง | `|1D` + ส่ง "BTC" ให้ scanner (ต้อง BINANCE:BTCUSDT) |
| `trading_sentiment` เสาข่าว | ข่าวบวก 4 ลบ 5 แต่ bias +0.31 | คะแนนข่าวผสม positioning ของ futures ซึ่งมีเสาของตัวเองอยู่แล้ว (นับซ้ำ) → `news_score` |
| `trading_price` (ทอง/FX/คริปโตผ่าน TV) | วันเสาร์ทอง "UP 0.01% · Market LIVE" — ศุกร์ลง −0.88% และตลาดปิด | ขอ TF 1m (`change|1` = เปลี่ยนแปลงในนาทีล่าสุด) + `market_state` ตายตัว "LIVE" → ใช้คอลัมน์รายวัน + `MarketHours` |
| `trading_fundamental_analysis` | AAPL "+1.02, +3.37%" (จริง +3.37 USD, +1.02%) | `change`/`change_abs` สลับกัน |
| `trading_correlation_matrix` | BTCUSDT,ETHUSDT,… ล้มทุกครั้ง · คริปโตเทียบหุ้นจับคู่ผิดวัน | รับแค่สัญลักษณ์ Yahoo + จับคู่ด้วยลำดับ → `YahooSeries.symbolFor` + จับคู่ตามวันที่ (`gmtoffset`) |
| `trading_bollinger_scan` | "No data" ทุกครั้ง | กรอง/เรียงด้วย `BB.width` ที่ไม่มีจริง → คำนวณจาก upper/lower/basis ใน 150 คู่ที่ซื้อขายมากสุด |
| `trading_combined` | `MACD hist: null`, `EMA20: null` | คอลัมน์ไม่ได้ขอ (ตอนนี้ขอทั้งชุดใน request เดียว) |
| สแกนคริปโต (gainers/losers/overbought/oversold/volume) | เหรียญเดียวซ้ำ 5 คู่ (USDT, .P, USDC, FDUSD, TRY), คู่ IDR/JPY, EURUSDT · % ไม่มีหน่วย | กรอง USDT spot ไม่รวม stablecoin/fiat (`STABLE_OR_FIAT_BASES`) + แสดง % |
| `trading_macro_calendar` | ข่าวที่ประกาศแล้วมีแค่ค่าคาดการณ์ — เสี่ยง AI พูดเป็นผลจริง | ForexFactory ฟรีไม่มี actual (และไม่มีสัปดาห์หน้า) → บอกในผลชัดๆ ให้ค้นเว็บ |
| `calculate` | `62.75000000000091` | ทศนิยมฐานสอง → `formatCalcResult` |
| `device_weather` | "โอกาสฝน 100% (ปริมาณ 0.0 มม.)" | ค่าสูงสุดของวัน คู่กับฝนตอนนี้ → เขียนแยกให้ชัด · WMO 2 = เมฆบางส่วน |

**ตรงแล้ว (ไม่ได้แก้)**: ราคา BTC/ทอง (≤ 0.05%), S&P/NASDAQ/DOW/VIX/น้ำมัน (ราคา + % ตรง Yahoo), Crypto F&G, CoinGecko
(market cap, dominance), position sizing, เวลา, แปลงหน่วย, AAPL งบการเงิน/ราคาเป้าหมาย

**ข้อจำกัดที่ยังอยู่**: tool SMC/Elliott เป็นโหมด TV-only — ถ้า TradingView ไม่ตอบและยังไม่มีแท่งในฐานข้อมูล
จะตอบ `❌ Strict TV candle source violation …` (บน PC เจอบ่อยเพราะไม่มีฐานข้อมูลแท่ง; บนมือถือเจอเฉพาะซีรีส์ใหม่ตอน TV ล่ม) ·
ข่าวจาก Google News มีหัวข้อที่ไม่ใช่ข่าวปนบ้าง (เช่น "Bitcoin Futures (CME Group)") ·
EV ของ `trading_fundamental_analysis` เป็นค่า ณ สิ้นไตรมาส (`enterprise_value_fq`) จึงอาจต่ำกว่า market cap ปัจจุบัน
