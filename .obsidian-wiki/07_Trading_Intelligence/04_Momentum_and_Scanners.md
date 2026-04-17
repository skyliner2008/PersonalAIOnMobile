# 🚀 04_Momentum (Scanners & Trend Tracking)

การหา "ม้าแข่ง" ที่พร้อมจะวิ่งคือหัวใจของการเทรดสไตล์ Momentum เครื่องมือสแกนจะช่วยให้เราไม่ต้องเฝ้าหน้าจอตลอด 24 ชม.

---

## 💥 1. Bollinger Squeeze (การสะสมพลังงาน)
เมื่อราคาบีบตัวแคบ (Low Volatility) มงคลว่ามันกำลัง "เลือกทาง" (Expansion) 

- **Indicator**: BB.width < 0.04
- **Tool**: `trading_bollinger_scan` (ค้นหาเหรียญ/หุ้นที่พร้อมระเบิด)
- **Strategy**: เมื่อเบรกขอบบน BB พร้อม Volume ให้พิจารณา Long

## 🔋 2. RSI (สภาวะร้อนแรง/ถูกเกินไป)
ใช้สำหรับหาจุดกลับตัวที่รุนแรง (Mean Reversion) หรือความต่อเนื่องของเทรน

- **Oversold (<30)**: ราคาถูกเกินไป (มักใช้ร่วมกับ Bullish Divergence ใน TF เล็ก)
- **Overbought (>70)**: ราคาร้อนแรงเกินไป (ระวังการเทขายทำกำไร)
- **Tool**: `trading_oversold_scan`, `trading_overbought_scan`

## 📈 3. Volume Breakout (รอยเท้าสถาบัน)
เมื่อราคาวิ่งพร้อมวอลุ่มมหาศาล (Displacement) นั่นคือจุดเริ่มต้นของเทรนใหม่

- **Indicator**: ปริมาณการซื้อขายสูงกว่าค่าเฉลี่ย 10 วัน (Relative Volume) + ราคาบวก > 3%
- **Tool**: `trading_volume_breakout`

---
> [!TIP]
> **Momentum Confluence**:
> ชุดสัญญาณที่มีคุณภาพที่สุดคือ: **Bollinger Squeeze** (สะสม) -> **Volume Breakout** (ระเบิด) -> **BOS** (ยืนยัน) 

**Next**: [[05_Sentiment_Analysis_Logic]] | [[06_The_Ultimate_Checklist_V12.5]]

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[00_Tool_to_Strategy_Map]]
