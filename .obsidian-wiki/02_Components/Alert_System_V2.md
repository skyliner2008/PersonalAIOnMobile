# ระบบ Alert V2 — เงื่อนไข แจ้งเตือน และสถานะ (2026-08-04)

บันทึกการอัปเกรดระบบเฝ้าดูตลาด (AlertJob / Automation) ตามผลทดสอบจริง (XAU 12:09–12:28)

## 1. Notification แบบกดได้ (แทน msg box)

Android ไม่อนุญาตให้ background service เด้ง dialog ลอยได้จริง — จึงใช้ **notification พร้อม action buttons** แทน:

- 🛑 **หยุดแจ้งเตือน** → `automationManager.setJobActive(id, false)` (is_active = 0, หายจากรายการ ACTIVE)
- 🔁 **แจ้งเตือนซ้ำ** → `automationManager.resetTrigger(id)` → รอบเช็คถัดไปประเมินเงื่อนไขใหม่และแจ้งซ้ำได้

Implementation: `JarvisAutomationService.sendJobAlertNotification()` + `alertActionReceiver` (BroadcastReceiver, register ใน `onCreate`, `RECEIVER_NOT_EXPORTED` บน API 33+)

## 2. สถานะ ACTIVE / TRIGGERED (Repeat mode)

พฤติกรรมปัจจุบัน (ตั้งใจไว้): โหมด Repeat — เข้าเงื่อนไข → แจ้ง → `is_triggered = 1` → เมื่อราคาหลุดเงื่อนไข → reset อัตโนมัติ → เข้าเงื่อนไขอีกครั้ง → แจ้งใหม่

การแสดงผลในหน้า Automation:
- `ACTIVE · เฝ้าดูอยู่` (เขียว) — กำลังเฝ้าดู
- `TRIGGERED · แจ้งแล้ว` (แดง) — แจ้งไปแล้ว แต่ job ยัง active และจะแจ้งซ้ำเมื่อเข้าเงื่อนไขใหม่

หมายเหตุ: `TRIGGERED` ไม่ใช่สถานะจบ — งานยังทำงานต่อ (Repeat) จนกว่าผู้ใช้จะกด 🛑 หรือลบ

## 3. แก้ไข Alert ได้

- แก้ได้: **ค่าเปรียบเทียบ** (เช่น ราคาเป้าหมาย) และ **ความถี่ (นาที)** ผ่าน `AlertEditDialog`
- แก้ไม่ได้: field/operator (ลบแล้วสร้างใหม่แทน)
- แก้ค่าแล้วระบบรีเซ็ต `is_triggered = 0, last_value = NULL` ให้เริ่มเฝ้าดูใหม่ทันที
- SQLDelight query ใหม่: `updateAlertJobCondition` (ไม่มี schema change, ไม่ต้อง migration)
- AI แก้ให้ได้ผ่าน `automation_manage_alerts` action `update` (alert_id + condition_value)

## 4. AlertFieldCatalog — เงื่อนไขที่ทำได้จริง (single source of truth)

ไฟล์: `automation/AutomationModels.kt` → `AlertFieldCatalog`

ใช้ทั้งใน UI (dropdown ตอนกด "สร้าง Alert") และเป็นรายการอ้างอิงให้ AI (`automation_manage_alerts` description + validation ใน `JarvisOrchestrator.onManageAlerts` — ถ้า AI ตั้ง field มั่วจะตอบกลับ ❌ พร้อมรายการที่รองรับ)

| tool_name | field ที่ใช้ได้ |
|---|---|
| trading_indicators ⭐ (แนะนำ — คำนวณเองจากแท่งเทียน 300 แท่ง, เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m) | close, ema20, ema50, ema200, ema_cross_state (==: GOLDEN_CROSS/DEATH_CROSS/BULLISH/BEARISH), ema50_200_spread, ema20_50_spread, rsi14, macd, macd_signal, macd_hist, stoch_k, stoch_d, cci20, bb_upper, bb_basis, bb_lower, bb_width, atr14 |
| trading_smc ⭐ (Smart Money Concepts — symbol@TF ได้เช่นกัน) | close, smc_zone (==: PREMIUM/DISCOUNT/EQUILIBRIUM), smc_zone_pct, smc_trend (==), smc_last_event (==: BOS_UP/BOS_DOWN/CHOCH_UP/CHOCH_DOWN), smc_structure_high/low, smc_equilibrium, smc_premium_bot, smc_discount_top, bull_ob_dist, bear_ob_dist, fvg_dist, liq_above_dist, liq_below_dist, liq_above_stars, liq_below_stars, attack_force (0/1), atr |
| trading_price | price, change, change_pct, prev_close, high_52w, low_52w, direction (==) |
| trading_technical_analysis (1h, scanner — field จำกัด) | close, RSI, MACD.macd, MACD.signal, BB.basis, ATR, ADX, Recommend.All, recommend_score, signal (==), volume |
| trading_deep_analysis_suite (1h) | summaryScore, fiboScore, lsdState, deltaLabel, momentum |
| trading_sentiment | sentiment_score, bullish_posts, bearish_posts, posts_analyzed, sentiment_label (==) |
| trading_fear_greed | value (0-100), classification (==) |
| trading_crypto_overview | btc_dominance, eth_dominance, market_cap_change_24h, total_market_cap_usd, total_volume_24h_usd, active_cryptocurrencies, markets |

⚠️ ~~EMA Cross ยังทำไม่ได้~~ → **ทำได้แล้วตั้งแต่ V2.4** ผ่าน `trading_indicators` (field `ema_cross_state` / `ema50_200_spread`) — คำนวณจากแท่งเทียนเอง ไม่ต้องพึ่ง scanner
⚠️ Reddit sentiment โดนบล็อก (HTTP 403) ช่วงนี้ — หลีกเลี่ยง tool trading_sentiment

ตัวอย่าง preset ใน UI: RSI >= 70 (Overbought) / RSI <= 30 (Oversold) / Fear&Greed <= 20 / summaryScore >= 85

## 5. Adaptive Interval + Async AI (2026-08-04 รอบ 2)

**ปัญหาที่พบจาก log จริง:** ตอน trigger ระบบเรียก Gemini สรุปแบบ serial ใน loop เดียวกัน → job ถัดไปถูกเช็คช้าไป ~10 วินาที

**การแก้ไข:**
- `fireJobAlert()` ถูกย้ายไป `scope.launch` ขนาน — mark trigger synchronous ก่อนเสมอกันยิงซ้ำ แล้วค่อยเรียก AI แบบไม่บล็อก loop
- **Adaptive Interval** (`effectiveIntervalMs`): tick หลักของ loop ลดเหลือ 30 วินาที (อ่าน SQLite อย่างเดียว ถูกมาก) และปรับความถี่เช็คตามระยะห่างจากเป้า (เฉพาะเงื่อนไขตัวเลข GT/LT/GTE/LTE):
  - ห่างเป้า < 0.1% → เช็คทุก 30 วินาที
  - ห่างเป้า < 0.5% → เช็คทุก ≤ 1 นาที
  - ไกลกว่านั้น → ใช้ interval ที่ผู้ใช้ตั้ง (ไม่เคยช้ากว่าที่ตั้ง)
- ออกแบบสำหรับทอง (XAUUSD) ที่วิ่ง $10-100 ต่อ 15 นาที — เมื่อราคาเข้าใกล้เป้า ระบบเร่งเช็คถี่ขึ้นเองโดยไม่เปลืองตลอดวัน

## 6. AlertActionReceiver (manifest-declared)

เดิม receiver ของปุ่ม notification เป็น **dynamic receiver** ที่ผูกกับ service — เมื่อ service/แอปถูกฆ่า broadcast จะไม่ถูกส่ง (ปุ่มกดแล้วไม่มีอะไรเกิดขึ้น)

แก้โดยสร้าง `service/AlertActionReceiver.kt` ประกาศใน AndroidManifest (`exported=false` + intent-filter 2 actions) และเปลี่ยน PendingIntent เป็น **explicit intent** — ระบบจะปลุก receiver ขึ้นมาทำงานเองแม้แอปไม่ได้รันอยู่ โดยเปิด `jarvis.db` อัปเดตสถานะ job โดยตรง (หยุด → is_active=0, แจ้งซ้ำ → reset trigger)

⚠️ ต้อง build & install APK ใหม่ก่อนปุ่มถึงจะขึ้น (code เดิมในเครื่องยังเป็นเวอร์ชันเก่า)
