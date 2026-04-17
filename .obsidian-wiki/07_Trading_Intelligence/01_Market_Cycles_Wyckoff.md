# 🔄 01_Market_Cycles (Wyckoff Methodology)

การมี **"แผนที่" (Map)** คือสิ่งสำคัญที่สุด การเทรดแบบมืออาชีพต้องระบุให้ได้ว่าตลาดอยู่ในช่วงไหน (Phase) เพื่อลดความเสี่ยงจากการเทรดสวนทาง "สถาบัน"

---

## 🏛️ 1. วัฏจักร 4 ระยะ (The 4 Phases)

1.  **Accumulation (การสะสมของ)**: ช่วงที่ราคาแกว่งในกรอบ (Sideways) หลังการร่วงหนัก สถาบันเริ่มเก็บของทีละน้อย 
    *   *Indicator*: Volume ลดลงและยืนพื้นได้
    *   *Action*: เตรียมแผน Long (มองหาจุดกลับตัว)
2.  **Markup (การดันราคา)**: การทำลายกรอบสะสมเดิม (Breakout) และเกิดเทรนขาขึ้นชัดเจน
    *   *Tool*: `trading_volume_breakout`, `trading_smc_structure` (BOS UP)
    *   *Action*: Buy on Retest
3.  **Distribution (การกระจายของ)**: ราคาเข้าสู่โซนสูง สถาบันเริ่มเทขายของให้ "เม่า" (Retail)
    *   *Indicator*: RSI Overbought ตลอดเวลา, ข่าวดีล้นตลาด
    *   *Tool*: `trading_overbought_scan`, `trading_sentiment` (Extreme Bullish)
    *   *Action*: เตรียมแผน Short (หาจุดสิ้นสุดเทรน)
4.  **Markdown (การทุบราคา)**: ราคาหลุดกรอบกระจายของและดิ่งลงอย่างรวดเร็ว
    *   *Action*: Short only / Stay out

## 🛡️ 2. กลไกราคาที่ควรรู้ (Logic)

- **Spring**: ราคาหลุดแนวรับหลอกๆ เพื่อกวาด Stop Loss (SSL) ก่อนจะพุ่งขึ้น (เป็นสัญญาณ Long ที่แม่นยำที่สุด)
- **Upthrust (UTAD)**: ราคาหลุดแนวต้านหลอกๆ เพื่อกวาด Liquidity (BSL) ก่อนจะทุบลง (เป็นสัญญาณ Short ที่แม่นยำที่สุด)

---
> [!TIP]
> **JARVIS Bias Rule**: หาก Daily Chart อยู่ในระยะ **Markdown** ห้ามรัน `trading_oversold_scan` เพื่อหาจุด Long ใน TF เล็กโดยเด็ดขาด เพราะราคาอาจร่วงต่อเนื่องได้ (Catching a falling knife)

**Next**: [[02_Institutional_Mechanics_ICT]]

---
**Links**: [[index]] | [[Trading_Intelligence_MOC]] | [[00_Tool_to_Strategy_Map]]
