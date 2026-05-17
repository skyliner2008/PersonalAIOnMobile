# JARVIS V15.0: TradingView Continuity & Native Bridge

ระบบ V15.0 มุ่งเน้นไปที่ความต่อเนื่องของข้อมูล (Data Continuity) และความเสถียรของแหล่งข้อมูลจาก TradingView (TV Source) เพื่อให้การวิเคราะห์ SMC ในระดับ Institutional มีความแม่นยำสูงสุด

## 🚀 1. Native TradingView Bridge (Android)
- **WebSocket Engine**: พัฒนาขึ้นใหม่โดยใช้ OkHttp (Android) และ Ktor (Common)
- **Resilient Parser**: รองรับ Payload หลายรูปแบบ
    - **Format A**: ชุดข้อมูลแบบ Series ({s: [...]})
    - **Format B**: ชุดข้อมูลแบบแยกแกน (t, o, h, l, c, v arrays)
- **Auto-Handshake**: ระบบตอบสนอง Ping-Pong (~h~) อัตโนมัติเพื่อรักษา Session

## 💾 2. Smart Persistence (TvCandle)
ระบบใช้ฐานข้อมูล SQLDelight เพื่อเป็น Cache ถาวร:
- **Table**: `TvCandle` (Symbol, Interval, TS, OHLCV)
- **Incremental Sync**: เมื่อ JARVIS ต้องการข้อมูล จะเช็กใน DB ก่อน
    - หากขาดข้อมูล (Missing Bars) จะดึงเฉพาะส่วนที่ขาดจาก TV
    - ทำการ Merge ข้อมูลเก่าและใหม่เข้าด้วยกันแบบไร้รอยต่อ
- **Auto-Pruning**: ระบบจะลบข้อมูลเก่าที่เกินหน้าต่างการวิเคราะห์ออกมาโดยอัตโนมัติเพื่อประหยัดพื้นที่

## ⏱ 3. Parallel Multi-TF Analysis
- **Concurrency**: ใช้ `coroutineScope` และ `async/awaitAll` ในการดึงข้อมูล 5-6 Timeframes พร้อมกัน
- **Timing Logs**: ทุกการวิเคราะห์จะมีการบันทึกเวลา (ms) เพื่อตรวจสอบประสิทธิภาพ
- **Source Integrity**: มีระบบ `StrictSourceMismatchException` เพื่อป้องกันการใช้ข้อมูลข้ามแหล่ง (เช่น เอาราคา Binance มาวิเคราะห์บนแท่งเทียน Yahoo)

## 🛡 4. AI Trading Policy
JARVIS ถูกประกาศใช้นโยบายความปลอดภัยใหม่:
- **Strict TV Flow**: บังคับใช้เฉพาะ `trading_smc_*` และ `trading_price`
- **Allowed Tools**: จำกัดเครื่องมือเฉพาะที่ผ่านการรับรอง V15.0 เพื่อลดโอกาส Hallucination
- **Source Labeling**: ทุกรายงานต้องระบุ `candle_source` และ `price_source` ให้ชัดเจน

---
**Status**: [ACTIVE]  
**Version**: 15.0  
**Updated**: 2026-04-18  
คราบ
