# 🗺️ Tool-to-Strategy Map (V12.5)

หน้ารวมการเชื่อมโยงระหว่าง **20+ เครื่องมือ** ในโปรเจค JARVIS เข้ากับกลยุทธ์การเทรดสากล เพื่อเป็นคู่มือให้ AI เลือกใช้เครื่องมือได้ถูกต้องตามสถานการณ์

---

## 🏗️ 1. Market Context & Sentiment (The Foundation)
ใช้เพื่อหา "สภาพอากาศ" ของตลาดก่อนเริ่มวิเคราะห์กราฟ:

| Tool | Strategic Use Case | Logic / Indicator |
| :--- | :--- | :--- |
| `trading_sentiment` | วัดอารมณ์เม่า (Retail Sentiment) | Reddit scraping (r/wallstreetbets, etc.) |
| `trading_news` | ตรวจสอบข่าวเด่นที่อาจกระทบราคา | RSS Feeds (Yahoo Finance, etc.) |
| `trading_market_snapshot` | ดูภาพรวมทุกอุตสาหกรรมในตลาด | Sector Heatmap / Industry View |
| `trading_combined` | วิเคราะห์แบบองค์รวม (Sentiment + News + TA) | Confluence Dashboard |

## 📊 2. High Timeframe (HTF) Analysis
ใช้เพื่อหาทิศทางหลัก (Bias) ของราคา:

| Tool | Strategic Use Case | Logic / Indicator |
| :--- | :--- | :--- |
| `trading_multi_timeframe` | ตรวจสอบความสอดคล้องของเทรนทุก TF | 1W, 1D, 4h, 1h Analysis |
| `trading_price` | ตรวจสอบราคา Real-time และ 52W High/Low | Yahoo / Binance API |

## 📈 3. Smart Money Concepts (SMC/ICT)
ใช้เพื่อมองหาพฤติกรรมเจ้ามือ และจุดกลับตัวสำคัญ:

| Tool | Strategic Use Case | Logic / Indicator |
| :--- | :--- | :--- |
| `trading_smc_structure` | หาจุดเปลี่ยนโครงสร้าง (CHoCH) และ BOS | Swing detection (5-bar lookback) |
| `trading_smc_orderblocks` | หาโซน Supply/Demand (Order Blocks + FVG) | Institutional Candles + Displacement |
| `trading_smc_sweeps` | ตรวจการ "กวาด" สภาพคล่องข้าม TF | Wick Reclaim (>50% body) |
| `trading_smc_liquidity` | หาจุดที่มีแรงซื้อ/ขายสะสมหนาแน่น (Stars ★) | Equal Highs/Lows (0.1% thr) |
| `trading_smc_analysis` | แดชบอร์ดวิเคราะห์ SMC ทุกมิติ | Comprehensive SMC Suite |

## 🚀 4. Momentum & Scanners
ใช้เพื่อคัดหุ้น/เหรียญที่กำลังจะระเบิด (High Momentum):

| Tool | Strategic Use Case | Logic / Indicator |
| :--- | :--- | :--- |
| `trading_bollinger_scan` | หาตัวที่สะสมพลังพร้อมระเบิด (Squeeze) | BB.width < 0.04 |
| `trading_volume_breakout` | หาตัวที่มีวอลุ่มเจ้าเข้าผิดปกติ | Change > 3% + Vol > 100k + Rel Vol |
| `trading_oversold_scan` | ค้นหาจุดกลับตัวจากสภาวะขายมากเกินไป | RSI < 30 |
| `trading_top_gainers` | ติดตามตัวที่มี Momentum แรงที่สุดในวัน | Standard Exchange Change % |

## 🏛️ 5. Institutional Logic (Advanced V12.5)
ใช้สำหรับการยืนยันขั้นสุดท้าย (Triple Confirmation) ด้วยระบบอัจฉริยะ:

| Tool | Strategic Use Case | Logic / Indicator |
| :--- | :--- | :--- |
| `trading_deep_analysis_suite` | วิเคราะห์ 5 มิติแบบสถาบัน (LSD+OF+Fibo) | HMA Trends + Delta Volume + Weighted Fibo |

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[06_The_Ultimate_Checklist_V12.5]] | [[07_Advanced_Analysis_V12.5]]
