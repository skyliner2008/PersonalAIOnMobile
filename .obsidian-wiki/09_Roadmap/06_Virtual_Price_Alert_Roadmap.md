# Roadmap: Hybrid Context-Aware Execution (Virtual Watchlist & Price Alerts)

## 📌 ภาพรวม (Overview)
ปัจจุบันระบบ **PersonalAIBot (Agent Trading Engine V20.0)** สามารถวางแผนล่วงหน้าผ่านการส่งคำสั่งเป็น `LIMIT` หรือ `STOP` เข้าไปยัง MT5 ได้โดยตรง (Native MT5 Pending Orders) ซึ่งมีข้อดีคือมีความแม่นยำสูงและได้ราคาเป๊ะๆ ไม่พลาดแม้กราฟจะสะบัด (Wick) 

อย่างไรก็ตาม การตั้ง Limit Order เป็นแบบ **"Blind Execution" (หลับตาเทรด)** คือถ้าราคากระชากลงมาชนแนวรับด้วยโมเมนตัมข่าวที่รุนแรง ระบบก็จะทำการเปิดออเดอร์ทันทีโดยไม่ประเมินสถานการณ์ (Context) หน้างาน ณ วินาทีนั้น

ดังนั้น Roadmap นี้จึงเสนอให้พัฒนาระบบ **Hybrid Model** ที่เพิ่ม "Virtual Price Alert" เข้ามาทำงานร่วมกัน เพื่อให้ Agent มีความฉลาดและปลอดภัยมากขึ้น

## 🎯 รูปแบบการทำงาน (The Hybrid Model)
Agent (`AnalystAgent` / `ExecutionTrader`) จะสามารถวิเคราะห์และเลือกวิธีการเข้าเทรดล่วงหน้าได้ 2 แบบตามสถานการณ์:

### 1. แบบ Native MT5 Limit Order (มีใช้งานแล้ว)
- **สถานการณ์ที่เหมาะสม:** สภาวะตลาดปกติ (Ranging/Consolidation), โซน Demand/Supply ที่แข็งแกร่งมาก, หรือเทรดใน Timeframe เล็กๆ ที่ต้องการความแม่นยำระดับ Pip 
- **การทำงาน:** AI ส่งคำสั่ง Buy Limit / Sell Limit เข้า MT5 ไปรอเลย ถ้าราคามาแตะก็ Match ได้ทันที

### 2. แบบ Virtual Price Alert / Cron Watchlist (แผนพัฒนาระบบใหม่)
- **สถานการณ์ที่เหมาะสม:** การรอ Breakout, ช่วงที่มีข่าวแรง, โซนราคาที่กว้าง หรือโครงสร้างตลาดมีความเสี่ยง (Trending แรงๆ แล้วสวนเทรนด์)
- **การทำงาน:**
  1. AI ตั้งค่าแจ้งเตือน (Virtual Alert) ไว้ในหน่วยความจำของเซิร์ฟเวอร์ (เช่น รอทะลุ 4584) แทนที่จะโยน Order เข้า MT5
  2. เมื่อกราฟวิ่งมาถึงจุดที่กำหนด (Triggered) ระบบเสมือนจะทำงานคล้าย Cron Job
  3. ระบบจะยิงแจ้งเตือน (Intelligence Alert) ไปที่ผู้ใช้
  4. **[สำคัญ]** ระบบจะปลุก AI ขึ้นมา (Force Run Cycle/Pipeline) เพื่อ **วิเคราะห์โครงสร้างตลาด ณ วินาทีนั้นอีกครั้ง**
  5. หากโมเมนตัมปลอดภัย AI จะสั่ง `MARKET` Order เพื่อเข้าเทรดทันที แต่ถ้าแท่งเทียนทิ้งตัวอันตรายเกินไป AI จะสั่ง "ยกเลิกแผน" หรือ "รอก่อน" (Skip)

## 🛠️ แผนการพัฒนา (Implementation Steps)

### Phase 1: Virtual Alert State Management
1. เพิ่ม Array/Map หรือ Database Table สำหรับเก็บ `VirtualAlerts` ใน `AutoTradingService`
2. กำหนดโครงสร้างข้อมูล: `{ id, symbol, targetPrice, side, intent, createdAt }`
3. สอน AI (`ExecutionTrader`) ผ่าน Prompt ให้สามารถส่งคำสั่งประเภท `VIRTUAL_ALERT` ผ่านช่องทาง `fillStrategy` หรือ `type` แทนคำว่า `LIMIT` ปกติ

### Phase 2: Core Loop Integration
1. ปรับปรุง `runManageLoop()` หรือ `runCycle()` เพื่อทำการตรวจสอบราคาปัจจุบัน (Current Price) เทียบกับ `VirtualAlerts` ตลอดเวลา (Every Tick หรือ Every Cycle)
2. เมื่อราคาตัดผ่านเป้าหมาย ให้ลบ Alert นั้นออกจากคิว

### Phase 3: Trigger & Re-Evaluation
1. เมื่อ Alert ทำงาน ให้ยิง Broadcast ทะลุเข้า Mobile UI ทันทีว่า *"ถึงโซนราคาเป้าหมายแล้ว กำลังวิเคราะห์สถานการณ์..."*
2. เรียกฟังก์ชันดึงแท่งเทียนล่าสุดแบบด่วน (Fetch Live Candles)
3. ยิงข้อมูลล่าสุดเข้าไปที่ `agentOrchestrator.reason()` หรือ `AnalystAgent` เพื่อประเมินสภาวะตลาด
4. ส่งคำสั่ง `MARKET` ทันทีหากผ่านเกณฑ์ หรือ `SKIP` หากเสี่ยงเกินไป

## 🚀 ผลลัพธ์ที่คาดหวัง (Expected Outcomes)
- ป้องกันการรับมีด (Catching a falling knife) จากแท่งเทียนข่าว หรือความผันผวนรุนแรง
- รักษาวินัยแบบ Context-Aware ตลอดเวลา 
- ตอบโจทย์สไตล์เทรดเดอร์มืออาชีพที่ "รอให้กราฟมาถึงโซน แล้วค่อยดูกริยา (Price Action) ก่อนเข้าเทรด" ได้อย่างสมบูรณ์แบบ
