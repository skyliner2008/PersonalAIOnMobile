---
title: "68_Stock_Fundamental_Financial_Engine"
category: "Trading Intelligence"
tags:
  - fundamental-analysis
  - balance-sheet
  - valuation-multiples
  - tradingview-scanner
  - gemini-ai
sources:
  - "TradingApiService.kt"
  - "ResearchToolHandler.kt"
  - "StockFundamentalModels.kt"
  - "TradingToolDefinitions.kt"
created: "2026-09-22"
updated: "2026-09-22"
---

# 🏛️ 68_Stock_Fundamental_Financial_Engine

## 1. บทนำและแรงจูงใจ (Overview & Motivation)
ก่อนหน้านี้เครื่องมือ `trading_fundamental_analysis` ในระบบ `PersonalAIBot` เป็นเพียง Mock / News Dummy ที่ดึงเพียงหัวข้อข่าว 10 ข่าวแล้วส่งให้ AI วิเคราะห์โดยไม่มีตัวเลขปัจจัยพื้นฐานจริง งบดุล (Balance Sheet), งบกำไรขาดทุน (Income Statement), โครงสร้างเงินทุน (Capital Structure), สัดส่วนผู้ถือหุ้น (Free Float), หรือมัลติเปิลประเมินมูลค่า (Valuation Multiples)

ในเวอร์ชันนี้ ระบบได้รับการยกเครื่องใหม่ทั้งหมด โดยดึงข้อมูลตัวเลขทางการเงินและงบการเงินจริงระดับสถาบันจาก **TradingView Scanner API** (ครอบคลุมตลาดไทย `SET`/`MAI` และตลาดสหรัฐฯ/สากล `NASDAQ`/`NYSE`) และจัดโครงสร้างการแสดงผลเทียบเคียงหน้า Company Profile / Financial Overview ของ TradingView ควบคู่กับบทวิเคราะห์เชิงลึก 6 มิติของ Gemini AI

---

## 2. ข้อมูล 6 หมวดหมู่หลักตามแบบ TradingView

### 1. ข้อเท็จจริงที่มีนัยยะ (Key Metrics)
- **มูลค่าตามราคาตลาด (Market Cap)**: มูลค่ารวมของบริษัทคำนวณจากราคาหุ้นปัจจุบัน × จำนวนหุ้นทั้งหมด
- **อัตราส่วนราคาต่อกำไรสุทธิ (P/E TTM)**: Price-to-Earnings ย้อนหลัง 12 เดือน
- **อัตราผลตอบแทนเงินปันผล (Dividend Yield % & DPS)**: คำนวณจากเงินปันผลต่อหุ้นล่าสุด
- **กำไรต่อหุ้นขั้นพื้นฐาน (Basic EPS TTM)**: กำไรสุทธิต่อหุ้นรอบ 12 เดือน
- **Price-to-Sales (P/S)** และ **Price-to-Book (P/B)**
- **Beta (ความผันผวน 1 ปี)**, **52-Week Range (High/Low)**, และผลตอบแทนสะสม 1 ปี / YTD

### 2. ความเป็นเจ้าของ & สัดส่วนผู้ถือหุ้น (Ownership Breakdown)
- **จำนวนหุ้นทั้งหมด (Total Shares Outstanding)**
- **หุ้นที่กระจายสู่รายย่อย (Free Float Shares & %)**
- **หุ้นที่ถือโดยเฉพาะกลุ่ม / ผู้ถือหุ้นใหญ่ (Strategic / Insider Shares & %)**
- **Visual Float Bar**: แสดงสัดส่วนรายย่อย vs รายใหญ่แบบกราฟแท่ง `[████████░░]`

### 3. โครงสร้างเงินทุน & สภาพคล่องงบดุล (Capital Structure & Solvency)
- **มูลค่ากิจการ (Enterprise Value - EV)**: คำนวณจาก Market Cap + หนี้สินสุทธิ
- **หนี้สินรวม (Total Debt)**: หนี้สินที่มีภาระดอกเบี้ยและหนี้สินทางการเงินทั้งหมด
- **เงินสดและรายการเทียบเท่า (Cash & Equivalents)**: คำนวณจาก `Total Debt - Net Debt`
- **หนี้สินสุทธิ (Net Debt)**: ภาระหนี้สินสุทธิหลังหักเงินสด
- **ส่วนของผู้ถือหุ้น (Total Shareholders' Equity)**
- **สินทรัพย์รวม (Total Assets)** และ **หนี้สินรวม (Total Liabilities)**
- **อัตราส่วนหนี้สินต่อทุน (Debt to Equity - D/E)**

### 4. ผลการดำเนินงาน & ความสามารถในการทำกำไร (Financials & Profitability)
- **รายได้รวม (Total Revenue)**: เปรียบเทียบ TTM, ไตรมาสล่าสุด (FQ) และรอบปีงบประมาณ (FY)
- **กำไรสุทธิ (Net Income)**: เปรียบเทียบ TTM, ไตรมาสล่าสุด (FQ) และรอบปีงบประมาณ (FY)
- **กระแสเงินสดอิสระ (Free Cash Flow - FCF)**
- **อัตรากำไรจากการดำเนินงาน (Operating Margin %)** และ **อัตรากำไรสุทธิ (Net Margin %)**
- **ผลตอบแทนต่อส่วนของผู้ถือหุ้น (ROE %)**, **ผลตอบแทนต่อสินทรัพย์ (ROA %)**, และ **ผลตอบแทนจากเงินลงทุน (ROIC %)**

### 5. มุมมองนักวิเคราะห์ & โมเมนตัมราคา (Consensus & Target Price)
- **ราคาเป้าหมายเฉลี่ย (Target Price Average)** พร้อมกรอบ สูงสุด - ต่ำสุด
- **Upside / Downside %**: ส่วนต่างระหว่างราคาตลาดปัจจุบันกับราคาเป้าหมายเฉลี่ย
- **Overall Recommendation Index (Recommend.All)**

---

## 3. สถาปัตยกรรม AI Multi-Dimensional Analysis (6 มิติ)

Gemini AI นำตัวเลขโครงสร้างทางการเงินข้างต้นมาประมวลผลผ่าน Analytical Framework 6 ด้าน:
1. 📊 **Valuation & Multiples**: วิเคราะห์ความถูก/แพงของหุ้นเทียบกับ P/E, P/B, P/S, EV/Revenue และ Upside จากนักวิเคราะห์
2. 🏛️ **Capital Structure & Solvency**: วิเคราะห์ภาระหนี้สิน อัตราส่วน D/E สภาพคล่องเงินสดในมือ และความเสี่ยงต่อฐานะทางการเงิน
3. 💰 **Profitability & Cash Flow**: วิเคราะห์คุณภาพของกำไร อัตรากำไรสุทธิ ROE/ROA และการแปลงกำไรเป็นเงินสดอิสระ (FCF Conversion)
4. 🎁 **Dividend Quality & Sustainability**: วิเคราะห์ความยั่งยืนของเงินปันผล Yield และ Payout Ratio จาก EPS และ FCF
5. 👥 **Ownership & Market Dynamics**: วิเคราะห์อิทธิพลของ Free Float ต่อสภาพคล่องและการเก็งกำไรในตลาด
6. 🎯 **Fundamental Health Score & Strategic Outlook**: สรุปคะแนน Fundamental Health Score (0-100), ปัจจัยหนุน (Bullish Drivers), ความเสี่ยงสำคัญ (Bearish Risks) และกลยุทธ์การลงทุนที่เหมาะสม

---

## 4. การแสดงผลแบบ Visual ใน TradingView Dashboard (`financials.html`)

นอกจากการวิเคราะห์ผ่าน Chat Card แล้ว ระบบยังผสานรวม **TradingView Financials Widget** ลงในหน้าจอ `TradingChartScreen.kt` โดยตรง:
- **3 โหมดการแสดงผลในหน้าจอชาร์ต**:
  1. `📊 Dashboard`: Lightweight Charts multi-pane (offline engine ทำงานในเครื่อง เร็ว รองรับวาด SMC/อินดิเคเตอร์)
  2. `🌐 TradingView`: TradingView Advanced Chart widget (online engine กราฟเทคนิคอลเต็มรูปแบบ)
  3. `🏛️ การเงิน / งบดุล` (`viewMode = "financials"`): TradingView Company Financials widget แสดงผลแบบเดียวกับหน้า Overview/Financials ของ TradingView (แท็บภาพรวม, งบการเงิน, สถิติ, เงินปันผล, ผลประกอบการ, แผนภูมิโดนัท Ownership, กราฟ Waterfall โครงสร้างเงินทุน และ Time Series การประเมินมูลค่า)
- **Asset Files**:
  - `composeApp/src/androidMain/assets/chart_widget/financials.html`
  - `composeApp/src/commonMain/resources/assets/chart_widget/financials.html`
- **Thai Stock Symbol Resolver (`toTvSymbol`)**:
  - แคตตาล็อกหุ้นไทยยอดนิยม (`SCB`, `PTT`, `KBANK`, `CPALL`, `AOT`, `DELTA`, ฯลฯ) ถูกแมปเข้า `SET:...` โดยอัตโนมัติ ทำให้ผู้ใช้ไม่ต้องพิมพ์ prefix `SET:` ด้วยตนเอง
- **Dynamic Symbol Sync & Quick Selector**:
  - เมื่อสั่งวิเคราะห์พื้นฐานผ่านเสียง/แชท `ChartStateManager.updateSymbol(fundamental.ticker)` จะซิงค์สัญลักษณ์หุ้นเข้าสู่หน้าต่างชาร์ตทันที
  - ในหน้าต่างตั้งค่า (`WidgetSettingsDialog`) มีช่องพิมพ์ชื่อสัญลักษณ์หุ้น พร้อมปุ่มลัด (Presets) เลือกหุ้นไทย/สหรัฐ/คริปโต/ทองคำได้ในคลิกเดียว

---

## 5. ไฟล์ที่เกี่ยวข้องในระบบ (System Code References)
- [[TradingApiService.kt]]: ฟังก์ชัน `getStockFundamentals(rawSymbol, exchange)` รองรับการ Query ตลาดไทยและสากลผ่าน TradingView Scanner API
- [[StockFundamentalModels.kt]]: Data Model `StockFundamentalData` และฟังก์ชัน Formatting ตัวเลขการเงิน
- [[ResearchToolHandler.kt]]: ฟังก์ชัน `executeFundamentalAnalysis(args)` ประสานงาน AI Multi-Dimensional Analysis
- [[TradingChartScreen.kt]]: คอมโพเนนต์ `TradingChartScreen`, `FinancialsWebView` และ `WidgetSettingsDialog`
- [[ChartController.kt]]: ควบคุมโหมดกราฟ (`setChartViewMode`, `applyChartControl`) รองรับโหมด `"financials"`
- [[TradingToolDefinitions.kt]]: Declaration ของ `trading_fundamental_analysis`
- [[ToolRegistry.kt]]: Declaration ของ `chart_dashboard_control` รองรับ view parameter `financials`
- [[catalogue.md]]: รายการเครื่องมือในสมองส่วนนอกของระบบ
