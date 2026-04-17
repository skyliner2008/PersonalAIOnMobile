# 🗣️ 05_Sentiment & News (Fundamental Bias)

กราฟไม่ได้วิ่งด้วยเทคนิคเพียงอย่างเดียว "อารมณ์" (Emotion) และ "ข่าว" (Story) คือเชื้อเพลิงที่เร่งความแรงของกราฟ

---

## 🎭 1. Market Sentiment (Reddit & Social)
การใช้อารมณ์ของผู้เล่น "ส่วนใหญ่" (Retail) มาเป็นอินดิเคเตอร์เชิงอ้อม

- **Bullish Sentiment (Extreme)**: ทุกคนเชียร์ขึ้น มั่นใจเกินเหตุ (ระวังการตกจากที่สูง - Distribution)
- **Bearish Sentiment (Extreme)**: ทุกคนกลัว ทิ้งของ (เป็นโอกาสในเขตสะสม - Accumulation)
- **Tool**: `trading_sentiment` (เช็ค r/wallstreetbets และ r/stocks)

## 📰 2. Financial News (The Catalyst)
ข่าวคือเหตุผลที่ทำให้สถาบัน "เปิดหน้าชก" ในเวลาที่ต้องการ

- **News Impact**: ข่าวดีในเทรนขาขึ้น (BOS UP) = ดันต่อแรงขึ้น, ข่าวดีในเทรนขาลง (Markdown) = อาจเป็นกับดักคลายเส้น (Fake Recovery)
- **Tool**: `trading_news` (ดึงข่าวล่าสุดแยกตาม Symbol)

## 💎 3. The Combined Intelligence
การใช้ "ตาที่สาม" ในการมองตลาด

- **Tool**: `trading_combined` (TA + Sentiment + News)
- **Strategy**: เมื่อ TA เป็น Bullish + ข่าวเป็นบวก + Sentiment เป็นบวก = **Strong High Probability Signal**

---
> [!NOTE]
> **JARVIS Rule**: หาก Sentiment เป็น Extreme Bullish (ทุกคนมั่นใจมาก) ให้เพิ่มความระมัดระวังเป็นพิเศษในการเปิด Long เพราะอาจเป็นสัญญาณของจบคิวกระจายของ (Distribution)

**Next**: [[06_The_Ultimate_Checklist_V12.5]]

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[00_Tool_to_Strategy_Map]]
