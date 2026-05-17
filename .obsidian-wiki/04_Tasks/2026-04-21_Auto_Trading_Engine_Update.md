# 2026-04-21 Auto Trading Engine Update

สรุปการเปลี่ยนแปลงสำคัญของระบบ `MT5` และ `AutoTradingEngine` จากบันทึกโปรเจคล่าสุด เพื่อใช้เป็นเอกสารอ้างอิงใน wiki

## เป้าหมายของรอบนี้
- ลดปัญหา token ไม่ถูกส่งต่อไปยัง MT5 bridge
- ลด race condition ระหว่าง `Node bridge` และ `Python MT5 bridge`
- ทำให้ routing ระหว่าง `trading_*` และ `trading_mt5_*` ชัดเจนขึ้น
- วางโครง `AutoTradingEngine` สำหรับ autonomous loop แบบหลายช่วง

## ปัญหาที่แก้

### 1. Token Forwarding
- ปรับฝั่ง `TradingToolExecutor` ให้ส่ง token ไปยัง MT5 request อย่างสม่ำเสมอ
- รองรับการส่งผ่าน header ที่ bridge ต้องใช้ เช่น `Authorization` และ `X-Client-Token`
- ลดอาการ `Client token required` ระหว่างที่ agent เรียก MT5 tools

### 2. Bridge Concurrency
- ฝั่ง `Node` มีการ serialize การเรียก bridge เพื่อลดการชนกันของคำสั่ง
- ฝั่ง `Python` ใช้ lock เพื่อป้องกัน MT5 operation ซ้อนกัน
- แนวทางนี้ช่วยให้ execution path เสถียรขึ้นเมื่อมีหลาย request ต่อเนื่อง

### 3. Tool Routing
- แยกหน้าที่ชัดเจนระหว่าง
- `trading_*` สำหรับ market analysis และข้อมูลเชิงวิเคราะห์
- `trading_mt5_*` สำหรับ broker state, account data, และ execution จริง
- ลดความสับสนของ agent เวลาเลือก tool สำหรับงานเทรด

## AutoTradingEngine

### วงจรหลัก
- `ANALYZING`
- `STRATEGIZING`
- `EXECUTING`
- `MANAGING`
- `LEARNING`
- `IDLE`

### Loop ที่ตั้งใจไว้
- cycle loop ประมาณทุก `60s`
- manage loop ประมาณทุก `30s`
- learning loop ประมาณทุก `6h`

### Config สำคัญ
- `watchlist`
- `timeframe`
- `confluenceThreshold`
- `riskPerTradePct`
- `maxOpenPositions`
- `minRRR`
- `breakEvenTriggerR`
- `enableLiveTrading`
- `symbolBlacklist`

### แหล่งข้อมูลที่ engine ใช้
- `trading_price`
- `trading_smc_analysis`
- `trading_multi_timeframe`
- `trading_mt5_candles`
- `trading_mt5_list_positions`
- `trading_mt5_modify_position`
- `trading_mt5_trade_journal`

## สิ่งที่ยังต้องต่อ
- inject `AutoTradingEngine` เข้า `ViewModel`
- สร้าง UI สำหรับดูสถานะ engine และ decision log
- เก็บ config แบบ persistent
- ทดสอบกับ bridge จริงและบัญชีจริงอย่างระมัดระวัง

## หมายเหตุด้านความปลอดภัย
- ค่าเริ่มต้นควรเป็น `paper` หรือ `enableLiveTrading = false`
- ทุกการส่ง order จริงควรผ่าน risk guard และ confirmation layer
- ควรแยก environment สำหรับ test กับ live ให้ชัด

**Links**: [[Current_Tasks]] | [[Trading_Intelligence_MOC]] | [[11_MT5_Full_Agent_Control_V17]] | [[catalogue]]
