# JARVIS Global Insights & Modern Technical Analysis (V16.0)

หน้านี้รวบรวมรายละเอียดของระบบวิเคราะห์ข้อมูลจากปัจจัยภายนอก (News & Macro) และการวิเคราะห์ทางเทคนิคระดับสากลที่เพิ่มเข้ามาในเวอร์ชัน V16.0

## 1. Modern Technical Suite
เราก้าวข้ามการใช้สูตรคำนวณแบบเดิม (Legacy) ไปสู่การหา **Confluence** ของหลายหลักการ:

### Harmonic Patterns
- **รูปแบบที่รองรับ**: Bat, Gartley, Butterfly, Crab
- **PRZ Focused**: ระบบจะเน้นการหาจุดกลับตัว (Potential Reversal Zone) โดยเฉพาะจุดที่ซ้อนทับกับ **SMC Order Blocks**
- **Confluence Score**: ให้คะแนน 0-5 ตามระดับความแม่นยำและการยืนยันจากปัจจัยอื่น

### Modern Elliot Wave
- **Wave Impulse Score**: วิเคราะห์ว่าปัจจุบันราคาอยู่ในสภาวะ **Impulse (คลื่นส่ง)** หรือ **Corrective (คลื่นพัก)** โดยใช้ความชันของ Momentum และ Volume Ratio
- **Market Stages**: ACCUMULATION (สะสม), IMPULSE (ส่งตัว), CORRECTIVE (พักฐาน)

### Fibonacci Golden Clusters
- ค้นหาโซนราคาที่มีการซ้อนทับกันของระดับ Fibonacci (0.382, 0.5, 0.618, 0.786) จากหลาย Swing ใหญ่ เพื่อระบุแนวรับ/ต้านที่แข็งแรงที่สุด

---

## 2. AI-Driven News & Macro Engine

### Smart Financial News (`trading_news`)
- **Sources**: Reuters, CoinDesk, MarketWatch, Yahoo Finance
- **Filtering**: ระบบ Keyword Mapping อัจฉริยะ กรองข่าวตามความเกี่ยวข้องของสินทรัพย์ (เช่น ทอง -> Fed, Inflation, Bullion)
- **AI Analysis**: 
    - **Bias Score**: ให้คะแนนความรู้สึกของข่าวในช่วง -10 ถึง 10
    - **Risk/Opportunity Details**: สรุปนัยสำคัญที่กระทบต่อราคา

### Macro Economic Calendar (`trading_macro_calendar`)
- **Forex Factory Integration**: ดึงตารางปฏิทินเศรษฐกิจรายสัปดาห์
- **AI Strategic Preview**: AI จะวิเคราะห์ว่าตัวเลขไหนคือ "ตัวเปลี่ยนเกม" และควรเฝ้าระวังตัวไหนเป็นพิเศษ

---

## 3. Automation Suggestions
ในเวอร์ชันนี้ AI (JARVIS) สามารถแนะนำการสร้าง **Cron Jobs** ได้เอง:
- หากพบนัยสำคัญของข่าวระดับสูง (High Impact) หรือตัวเลขเศรษฐกิจสีแดง
- JARVIS จะแนะนำให้เรียก `automation_manage_alerts` เพื่อติดตามราคาสินทรัพย์นั้นๆ ในช่วงเวลาที่ข่าวออก

---

## 4. วิธีการใช้งาน (Tool Usage)
- `@JARVIS ตอนนี้มี Harmonic pattern สวยๆ ใน XAUUSD ไหม?`
- `@JARVIS วิเคราะห์ข่าว Bitcoin ล่าสุดพร้อมประเมิน Bias Score ให้หน่อย`
- `@JARVIS สัปดาห์นี้มีตัวเลขเศรษฐกิจอะไรที่ต้องระวังบ้าง?`

คราบ!
