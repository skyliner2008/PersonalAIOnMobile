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
- `trading_volume_breakout` - สแกน volume breakout
- `trading_sentiment` - วิเคราะห์ sentiment จาก community
- `trading_news` - ข่าวการเงินและข่าวรายสัญลักษณ์
- `trading_combined` - รวม TA + sentiment + news
- `trading_fundamental_analysis` - วิเคราะห์ปัจจัยพื้นฐาน
- `trading_fear_greed` - Crypto fear and greed
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
