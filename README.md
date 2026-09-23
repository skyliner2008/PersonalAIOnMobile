# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Chat Message Timestamp (LINE Style) & 24-Hour Active History Retention Filter (2026-09-23):**
> - **ตัวแสดงเวลาส่งข้อความมุมขวาล่างสไตล์ LINE**: บับเบิลข้อความทั้งฝั่งผู้ใช้และ AI มีตัวแสดงเวลาส่งข้อความ (`HH:mm` เช่น `14:32`) ขนาดตัวเล็กกระชับ (`fontSize = 9.5.sp`, Monospace) จัดวางชิดมุมขวาล่าง (`Alignment.End`) สวยงาม เป็นระเบียบ ไม่เกะกะสายตา
> - **ตัวกรองประวัติแชทไม่เกิน 24 ชั่วโมง**: ช่องแชทกำหนดเงื่อนไขเวลาแสดงเฉพาะข้อความที่ยังไม่ครบ 24 ชั่วโมง (`< 24 ชม.`) เมื่อข้อความมีอายุครบ 24 ชั่วโมง จะไม่แสดงในช่องแชท (แต่ยังคงถูกเก็บรักษาไว้ในคลังความรู้และประวัติฐานข้อมูลอย่างปลอดภัย) พร้อมระบบ Real-time ticker ที่ตัดข้อความเก่าออกอัตโนมัติขณะเปิดแอปทิ้งไว้ — โหลดสูงสุด 300 ข้อความล่าสุดในช่วง 24 ชม. และมี index `idx_chat_message_ts` (migration `18.sqm`) กันการ์ดระบบปลุก AI ที่เข้ามาถี่ทำให้เปิดแอปช้า (ตรวจ query ที่ generate ทุกตัวด้วย `python tools/verify_sqldelight_queries.py` หลังแก้ไฟล์ .sq)
>
> **Mobile Chat Layout & Markdown Engine Upgrade + Message Persistence Fix (2026-09-23):**
> - **แก้ปัญหาหน้าแชทตัวหนังสือติดกัน/อ่านยาก และบับเบิลข้อความแคบเกินไป**:
>   - ขยายความกว้าง Message Bubble ฝั่งผู้ช่วยให้ตอบสนองพื้นที่หน้าจอมือถือจริง (`Modifier.weight(1f, fill = false).widthIn(max = 640.dp)`) แทนค่าคงที่ 320dp เดิม
>   - อัปเกรดตัวประมวลผล Markdown ในแชท (`MessageBubble.kt`) รองรับการแยกบล็อก: **Headings (`#`, `##`, `###`)**, **Dividers (`---`, `===`)**, **Paragraph Spacing** บนบรรทัดว่าง, **Bullet & Numbered Lists (`-`, `*`, `1.`)** พร้อมไอคอน Bullet สี Cyan และการเยื้องย่อหน้า
>   - รองรับ **Inline Code Badges (`` `code` ``)** ด้วย Monospace font แก้ปัญหาสัญลักษณ์กราฟิก/แท่งเกจวัด (`[████░░]`) แสดงผลเป็นกล่องสี่เหลี่ยมกลวง `▯` บนสมาร์ตโฟนบางรุ่น
> - **แก้ปัญหาข้อความหายหรือแสดงไม่ครบเมื่อเปิดแอปใหม่**:
>   - ยกเลิกการตัดทอนข้อความ `text.take(2000)` ใน `LiveGeminiService.emitTextToChat` ทำให้บทวิเคราะห์และรายงานขนาดยาวถูกบันทึกลง SQLite Database แบบเต็ม 100%
>   - เพิ่มปริมาณการโหลดประวัติแชทเริ่มต้น (`ChatController.loadHistory`) จาก 20 ข้อจำเป็น 100 ข้อความ ป้องกันประวัติแชทเก่าถูกกลืนหายหลังใช้งานคำสั่งเสียง (ต่อมาถูกแทนที่ด้วยตัวกรอง 24 ชม. ด้านบน)
>
> **JARVIS Master All-in-One Sentiment Engine & Composite Index (2026-09-22):**
> - **รวมศูนย์ Sentiment ทุกมิติเป็นเครื่องยนต์หลักหนึ่งเดียว (`trading_sentiment`)**:
>   - รวมทุกองค์ประกอบ (News Bias, Market Fear & Greed, Binance Futures Positioning, Technical Indicator Consensus) เข้าสู่ **JARVIS Composite Sentiment Index (0–100)**
>   - แสดงผลผ่าน Visual Meter Bar `[████████░░]` พร้อมจำแนก 5 ระดับอารมณ์สากล: Extreme Fear (0-24), Fear (25-44), Neutral (45-55), Greed (56-75), Extreme Greed (76-100)
>   - **4 เสาหลัก (4-Pillars Framework)**:
>     1. *News & Social Bias (30%)*: ข่าวสารเจาะจงรายสินทรัพย์ (Google News, Yahoo Finance, CoinDesk RSS) พร้อมคะแนน Sentiment Bias (-1.0 ถึง +1.0)
>     2. *Market Fear & Greed (25%)*: Alternative.me (คริปโต) / CNN Fear & Greed 7 ตัวชี้วัดย่อยระดับสถาบัน (หุ้นสหรัฐฯ, ทองคำ, ดัชนี)
>     3. *Derivatives Positioning (30%)*: สัดส่วนบัญชีรายย่อย Long/Short vs พอร์ตเจ้ามือ/Top Traders จาก Binance Futures และ Taker Volume Ratio
>     4. *Technical Consensus (15%)*: ฉันทามติอินดิเคเตอร์เทคนิค 1D (TradingView Recommend Score)
>   - **Global Macro Mode**: สรุปภาพรวมความเสี่ยงโลก (เมื่อไม่ระบุ symbol หรือส่ง "all") โดยคำนวณจาก CNN Stock F&G (60%) + Crypto F&G (40%)
>   - **Contrarian Alert Detector**: แจ้งเตือนสภาวะผิดปกติ เช่น Short Squeeze (รายย่อยแห่ Short แต่เจ้ามือถือ Long หนาแน่น) หรือ Bull Trap ทันที
>   - **AI Behavioral Economics Synthesis**: Gemini AI วิเคราะห์ระยะจิตวิทยาฝูงชน (Crowd Psychology Phase) และสรุปกลยุทธ์เชิงพฤติกรรมศาสตร์
> - รายละเอียด: [.obsidian-wiki/07_Trading_Intelligence/05_Sentiment_Analysis_Logic.md](.obsidian-wiki/07_Trading_Intelligence/05_Sentiment_Analysis_Logic.md)
>
> **Stock Fundamental & Financial Analysis Engine + TradingView Financials Visual Dashboard (2026-09-22):**
> - **ยกระดับ `trading_fundamental_analysis` สู่ Financial Analysis Engine เต็มรูปแบบ** — เชื่อมต่อ TradingView Scanner API ดึงตัวเลขจริงครบ 50+ ตัวชี้วัดสำคัญ ครอบคลุมทั้งหุ้นไทย (SET/MAI เช่น SCB, PTT, CPALL) และหุ้นสหรัฐฯ/สากล (NASDAQ/NYSE เช่น AAPL, NVDA, TSLA)
> - **การแสดงผลระดับสถาบัน 6 หมวดหมู่เทียบเคียง TradingView**:
>   1. **ข้อเท็จจริงที่มีนัยยะ (Key Metrics)**: Market Cap, P/E (TTM), Basic EPS, P/S, P/B, P/FCF, Dividend Yield, DPS, 52W Range, Beta 1Y
>   2. **ความเป็นเจ้าของ (Ownership & Float)**: Total Shares, Free Float %, Closely-held %, Visual Float Bar `[████████░░]`
>   3. **โครงสร้างเงินทุน & งบดุล (Capital Structure & Solvency)**: Enterprise Value (EV), Total Debt, Cash & Equivalents, Net Debt, Total Equity, Total Assets, Debt-to-Equity (D/E)
>   4. **ผลการดำเนินงาน & ความสามารถทำกำไร (Financials & Profitability)**: Revenue (TTM/FQ/FY), Net Income (TTM/FQ/FY), Free Cash Flow (FCF), Operating Margin, Net Margin, ROE, ROA, ROIC
>   5. **มุมมองนักวิเคราะห์ & โมเมนตัมราคา (Consensus & Targets)**: Target Price (Avg/High/Low), Upside %, 1Y & YTD Performance
>   6. **AI Multi-Dimensional Analysis (6 มิติ)**: วิเคราะห์เจาะลึก Valuation, Solvency, Profitability, Dividend Safety, Ownership และสรุป Fundamental Health Score (0-100) พร้อม Bullish Factors และ Bearish Risks
> - **TradingView Financials Visual Dashboard (โหมดการเงินในหน้าจอชาร์ต)**:
>   - เพิ่มโหมดที่ 3 `🏛️ การเงิน / งบดุล` (`viewMode = "financials"`) ใน `TradingChartScreen.kt` ฝังวิดเจ็ต TradingView Financials แท้ (`financials.html`) แสดงผลแท็บ ภาพรวม, งบการเงิน, สถิติ, เงินปันผล, ผลประกอบการ, Donut Chart ความเป็นเจ้าของ และ Waterfall Chart โครงสร้างเงินทุน ตรงตามหน้า TradingView
>   - รองรับการแปลงชื่อย่อหุ้นไทยอัตโนมัติ (`toTvSymbol`) สลับสัญลักษณ์หุ้นแบบ Dynamic และ Quick Preset Chips (SET:SCB, PTT, KBANK, CPALL, AAPL, NVDA, XAUUSD, BTCUSDT)
> - รายละเอียด: [.obsidian-wiki/07_Trading_Intelligence/68_Stock_Fundamental_Financial_Engine.md](.obsidian-wiki/07_Trading_Intelligence/68_Stock_Fundamental_Financial_Engine.md)
>
> **AI Wake Engine Goes Live, OHLCV Pruning & Backup/Restore (2026-09-18):**
> - **ระบบปลุก AI (คาดการณ์ล่วงหน้า) ทำงานจริงแล้ว และแยกจาก Signal Alert** — alert job `trading_anticipation` (field `wake`) เฝ้า 5TF ด้วยปัจจัย ~115 ตัว 15 หมวด; เมื่อเกิดเหตุการณ์ AI ดู snapshot 5TF แล้วตัดสินเอง **NOTIFY / SKIP** (ไม่มี Entry/SL/TP สำเร็จรูปจากปัจจัย) และคำตัดสินถูกบันทึกลงการเรียนรู้เพื่อวัดว่า AI ถูกแค่ไหน. job คาดการณ์เดิมถูกย้ายให้อัตโนมัติ
> - **ประหยัดโทเคน Live** — การวิเคราะห์ใช้ Flash Lite แบบ stateless; Live แค่พูดสรุปสั้นที่ AI เขียนแล้ว
> - **ติดตามผลมุมมองของ AI** (`AiViewTracker`, ตาราง `AiWakeView`) — ทุกการปลุกถูกติดตามว่าชน TP หรือ SL ก่อน (หน่วย R) หรือหมดเวลา; SKIP วัดว่าราคาวิ่งไปเท่าไร. มุมมองล่าสุด 24 ชม. + สถิติ 30 วันถูกส่งกลับเข้า prompt ครั้งถัดไป (ห้ามแจ้งทิศเดิมซ้ำ, กลับทิศต้องบอกเหตุผล). ตลาดปิด → ข้ามการดึงแท่ง/สแกน/เรียก AI ทั้งหมด, ไม่ปลุก 60 นาทีก่อนปิดสิ้นสัปดาห์, บังคับ RR ≥ 1:1
> - **Order Block ใช้งานได้จริงแล้ว** — เดิม `FvgDetection` หา FVG แค่ 30 แท่งท้ายขณะที่ `OrderBlockDetection` ค้นทั้ง 300 แท่ง (OB = 0 เสมอ) และนับ mitigation ตั้งแต่แท่ง impulse ที่สร้าง OB เอง; แก้ทั้งสองจุดแล้ว OB ที่ยัง active ขึ้นจาก 1/24 เป็น 17/24 หน้าต่างบนแท่งจริง
> - **ปัจจัย × TF (P16)** — ปัจจัยที่ใช้ได้ทุก TF (~90 ตัว) ถูกประเมินบน M1/M5/M15/H1/H4 ทุกนาที และเรียนรู้แยกตาม TF (วัดผล 12 แท่งของ TF นั้น) เพื่อรู้ว่าปัจจัยไหนเหมาะกับ TF ไหน; ปลุก AI เฉพาะปัจจัย × TF ที่เหมาะ (`WakeTfProfile`) แล้วลด/เลื่อนชั้นตามผลจริง. ปลุกได้ทุกนาที (เว้น 1 นาที, 40/ชม., 3,000/วัน) และเหตุการณ์ที่ติดระยะห่างถูกพกไปการปลุกถัดไป — จำลองจาก BTC จริง ≈14 ครั้ง/ชม.
> - **ภาพตลาด D1 + 5TF ที่ละเอียดขึ้น** — ทุก TF มีบรรทัด "โครงสร้าง" (ลำดับ swing, swing H/L + ระยะ ATR, ตำแหน่งในกรอบ 50 แท่ง, EMA 20/50/200) และ "แรง/แท่ง" (ทิศ RSI, divergence, เคลื่อนกี่ ATR ใน 12 แท่ง, ATR percentile, ลักษณะแท่งล่าสุด) + แนวรับ/ต้าน H4, PDH/PDL, PWH/PWL. **ห้ามแจ้งสวนเทรนด์ที่ H4/H1/M15 เรียงกัน** (prompt + ตาข่ายในโค้ดเปลี่ยนเป็น SKIP)
> - **ตรวจค่าปัจจัยปลุกทั้ง 115 ตัวด้วยการเล่นซ้ำ** — `python tools/wake_factor_replay.py BTCUSDT` รันโค้ดจริงของแอปบนแท่งจากมือถือย้อนหลังทีละแท่ง แล้วเทียบกับสูตรอิสระ (อินดิเคเตอร์ 5TF, โครงสร้าง, 30 ปัจจัย ทั้งตอนเกิดและไม่เกิด). แก้ภาพ 5TF ที่เคยบอก AI ว่า `ล่าสุด=CHoCH↑` ทุก TF ทุกครั้ง
> - **OHLCV store ไม่บวมอีก** — ≈1 MB/ซีรีส์, ≈6–8 MB/สินทรัพย์ที่เฝ้า; ลบซีรีส์ที่ไม่ได้ใช้เกิน 30 วัน (ยกเว้นสินทรัพย์ที่มี alert และตลาดที่เกี่ยวข้อง) วันละครั้ง + ลบการเรียนรู้เก่ากว่า 1 ปี
> - **สำรอง / กู้คืน (Settings)** — ส่งออก/นำเข้า **การเรียนรู้** เป็น JSON (ย้ายเครื่อง A → B, นำเข้าแบบรวม) และสำรอง/กู้คืน **ฐานข้อมูลทั้งหมด** เป็น zip (ตรวจไฟล์ก่อนกู้คืน, เก็บของเดิม 1 ชุด). เลือกเก็บในเครื่องหรือ **Google Drive** ผ่านหน้าต่างเลือกไฟล์ของระบบ
> - **Review รอบ 2**: แก้บัคที่ทำให้ alert เงียบหลังสแกนจากแชท, เรียก AI เกินงบ, งบรีเซ็ตเมื่อรีสตาร์ท, สถิติการเรียนรู้/ความแม่นของ AI วัดผิด, แนวรับ-ต้าน FX ถูกรวมเป็นก้อนเดียว, เวลา session ไม่รองรับ DST — และตัด system prompt แชทออกจากการวิเคราะห์ (จากประมาณ 14,000 ตัวอักษร เหลือไม่ถึง 600)
> - รายละเอียด: [.obsidian-wiki/07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29.md](.obsidian-wiki/07_Trading_Intelligence/66_WakeEngine_Live_Backup_V29.md)

> **Free Tier Model Policy, Live Modes & Agent Multi-Session (2026-09-16):** the app now matches what the project's free tier actually offers.
> - **Chat runs on flash-lite only** (`gemini-3.5-flash-lite` → `gemini-3.1-flash-lite`, 500 requests a day each). The 20-a-day flash models are out of the chat path, a saved flash model is migrated automatically, and a per-day 429 pushes that model to the back of the chain until the Pacific-midnight reset.
> - **Live picks its own model**: seeds are `gemini-3.8-live`, `gemini-3.1-flash-live-preview`, `gemini-3.8-live-extended-thinking` and native audio dialog as the last resort, but after the model list syncs the newest bidirectional model wins — so new Live models are used without a code change. Transcribe and translate models are kept out of the assistant path.
> - **Modes are roles, not capabilities**: assistant, drive and pet are the same assistant with the same tools. Only drive mode may drive the phone itself (read screen, tap, type, scroll, buttons, launch apps, wake/sleep the screen), blocked both at session setup and at execution.
> - **Live is the main agent**: `agent_task_start` hands long work to a background chat session and the voice conversation continues immediately; when the work finishes the result lands in chat and comes back through Live to be spoken. `agent_task_list` / `status` / `cancel` round it out, with at most 3 concurrent tasks.
>
> - **Meeting and Translate are their own modes now**: two toolbar buttons (two-people icon, translate icon) open dedicated screens that run a separate Live session on the specialist models. Meeting transcribes speech live in 85+ languages, auto-renews the session every 8 minutes (the model caps a session at 10), and on stop summarises the notes into chat. Translate streams speech-to-speech in 70+ languages with the heard text and the translation side by side. Live speaker separation is not offered because the streaming model does not support it.
>
> Details: `.obsidian-wiki/04_Tasks/Changelog_2026-09-16_Free_Tier_Model_Policy_And_Agent_Tasks.md`, `.obsidian-wiki/02_Components/Meeting_And_Translate_Modes.md`.

> **Live Voice Review & Fixes (2026-09-16):** a full review of the phone's Live voice path, with every finding fixed.
> - **Commands:** on-device voice shortcuts now run once per finished sentence instead of on every partial transcript, so notification replies are no longer sent in fragments. Separately, "เปิดโหมด…" and "เปิดกล้อง" were being read as *close* commands; that is fixed.
> - **API keys:** quota key rotation now really uses the next key, because the WebSocket URL is rebuilt on every attempt.
> - **Barge-in:** a turn counter (audio epoch) drops buffered speech after the user talks over the AI.
> - **Tool calls:** calls run in parallel and can be cancelled (`toolCallCancellation`), and array arguments no longer drop the whole frame.
> - **Connection:** session resumption is enabled from the first setup and switches itself off if the server rejects it. Restarting the session (for example after a voice change) no longer races with the old socket.
> - **Other flows:** alerts speak through the active Live session instead of opening a second one, and Pet persona survives minimize and screen-off.
> - **Guards:** price questions are no longer redirected to navigation, and D1 is allowed when the user asks for it.
>
> - **Second pass:** Pet mode now ships a smaller tool set (no trading/MT5/SMC/file tools), switching persona or voice restarts the session so the new system prompt, tools and voice actually apply, the wake word is confirmed by a short transcription instead of any loud sound, sliding-window context compression is on (and disables itself if the server rejects it), and a dead session releases the mic and says so in chat.
>
> New testable helpers: `LiveProtocol`, `LiveIntentMatchers`, `LiveLocalCommandParser`, `LiveSessionBridge`, `WakeWordMatcher`. Details: `.obsidian-wiki/04_Tasks/Changelog_2026-09-16_Live_Voice_Review_Fixes.md`.

> **Virtual 3D Robot Head Kinematics & Dual-Circle Pupil Separation (2026-09-17):** upgraded the Compose Canvas avatar engine to simulate the physical kinematics of a 3D LOOI Robot head without drawing the head — two equal-sized circles per eye with independent head socket vs pupil motion:
> - **Equal-Diameter Discs (วงกลม 2 วงขนาดเท่ากัน 100%):** Front disc (pupil) and back disc (socket) have identical diameter (`discSize`), eliminating flat 2D canvas perspective shear distortion (no skewed egg shapes).
> - **Separated Head vs Pupil Kinematics:** The front disc acts as the agile eyeball/pupil (glancing widely and rapidly with saccades), while the back disc acts as the eye socket fixed to the virtual robot head, moving only when the robot turns its head (Yaw), tilts up/down (Pitch), rolls, nods, or shakes.
> - **3D Spherical Head Surface:** When turning sideways, eye spacing compresses following spherical curvature ($\cos(\text{yaw} \times 0.35f)$) with realistic 3D depth parallax.
> - **Rule of Thirds Alignment (จุดตัด 9 ช่อง):** In portrait mode, eye line rests at 40% Y, with centers at 1/3 and 2/3 width, placing both eye centers squarely on the upper intersection points of the 9-grid; in landscape mode, eye line rests at 42% Y centered in the face bounding box. Ambient aura is aligned to the eye line in all engines.
>
> **Softer, Livelier Eyes (2026-09-15):** round eyes are now soft vertical ovals; each eye is a bright front layer over a darker back layer, and the front shifts toward where the pet looks (from camera gaze and from every story look), so the back's rim shows on the opposite side instead of always one side. The gleam dot is gone. Eye/head keyframes use a small-overshoot `jelly` ease, breathing is a squash-and-stretch, and gaze/gyro tilt follow an under-damped spring.

> **Demo Button & Voice Scenes Play to the End (2026-09-15):** in Pet mode the Demo button (and saying/typing "เดโม่") now plays all 37 animated scenes — 18 pet scenes then 19 mood stories — one after another, each to its end, labelled "🎬 n/37". Voice/tool scene commands reach every scene (mood stories too), wait for the pet screen, and report the scene actually played. Fixed keyword bugs ("อาบน้ำ"/"ราชา" playing the drinking scene, `royal`/`soul_out` not found) and scenes are no longer interrupted by touches or the idle loop.

> **Prop Placement & Size Pass (2026-09-15):** all 91 Rive props re-checked on the eye box (y 163–297): head-top props sit above the eyes, every eyewear prop (sunglasses, pixel shades, mood shades) is taller and wider than an eye, and small icons grow about their own centre (`PROP_FIT` / `fit()`) so they read on a phone. The Bulb (which replaces the right eye) now switches the face to `OneEye`.

> **Jelly Mood Stories (2026-09-15):**
> - 19 seven-second mood stories in `avatar.riv` (`seq` 82–100): idle ball / yo-yo / star counting, shocked, sad, curious, three kinds of angry (missiles, fuming, glitch), thinking, happy, in love, glad, awesome, shy, embarrassed, show-off, listening and arrogant — eyes squash, stretch, fall, melt and merge with props and timed sound. Script: `.obsidian-wiki/02_Components/Pet_Mood_Scripts.md`.
> - `RivePetAvatar` plays one when the pet's mood changes (random variant, waits for touch reactions, 20 s repeat cooldown, stops instantly on a new mood) and an idle story after 30–50 s of idling; never during a pet scene, the missile barrage or speech.

> **Animated Pet Scenes with Sound (2026-09-15):**
> - Every pet scene is now a 7.5–9 s story in `avatar.riv` (`seq` 65–81): the eyes notice, props enter and act, a peak moment, then a settle — e.g. VR: look down, the headset is lifted up and worn, isometric cards glide from the bottom corners toward the top centre. Script: `.obsidian-wiki/02_Components/Pet_Scene_Scripts.md`.
> - New scenes `VR_MODE` and `MUSIC`; the food / drink / device variant is chosen with the new `item` channel.
> - `PetSceneSpec.cues` play timed sound effects, with 15 new synthesized sounds (WHOOSH, POP, SLURP, COIN, ZAP, SIZZLE, RAIN, GAME_BLIP, TYPING, SOB, FANFARE, POWER_UP, MELODY, HEARTBEAT, GHOST).
> - Replaying a scene restarts its story (`sceneId`); a newer scene cancels the old cues and timer.

> **Pet System Review & Fixes, verified on a Galaxy S22 Ultra (2026-09-15):**
> - **Reactions stick now.** Voice-session states (IDLE / LISTENING flickering with the mic) were overwriting touch reactions within ~1 s (a 30 s ANGRY vanished), the pet's own update echoing back cleared its ownership, and an earlier poke's timer ended the next reaction. Fixed with emotion ownership + echo detection in `AlwaysLiveScreen` and generation tokens on revert timers.
> - **Touch works on the Rive engine** (`touchPassThrough = true`; `RiveAnimationView` was swallowing every touch).
> - **Sensors handled once**: shake / face-down / face-up went both through `AlwaysLiveManager` (writing the avatar directly) and `PetMotionBridge` → state machine; the direct write overwrote the state machine's result. The bridge is now the single path. Loud-noise detection is a sudden spike with an 8 s cooldown (normal talking used to stack rage to a missile barrage).
> - **Needs math**: energy recovers while asleep, app-closed time counts as sleep with hunger/dirt floors (205 min away used to mean 0% satiety, 59% stress), yawning and hunger-anger no longer repeat every few seconds, passive moods are not logged as "pats", annoying pokes and refused feeds/plays give no rewards, gesture/fortune/tab moods return to idle.
> - **Tools**: new `device_pet_care` (feed / clean / play / sleep / wake / status on the real needs system), `device_custom_prop` finally declared (it was unreachable), avatar enums cover all 58 emotions / 17 eye styles / 20 backgrounds.
> - **Verified**: all unit tests + `PetNeedsLogicTest`; on device (Rive): poke → PokeR, poke spam → PokeAngry then ANGRY held, chat "open pet mode then feed the pet a burger" → both tools in one round with live stats. Shake / flip / loud-noise still need a hands-on test.
>
> **Rive Avatar v7: Pet Mode Runs on Either Engine (2026-09-15):**
> - **The Rive pet now obeys the same conditions as the Compose Canvas pet.** Both read one `AvatarState` and the same touch / sensor events; `RiveAvatarBinding.kt` (`RiveAvatarMapper.plan`) maps all 58 emotions (with the shared `effectiveEmotion()` eye-style override), AI props, backgrounds and foregrounds, eye tricks, gestures, speaking, dizzy and sleep onto the Rive channels.
> - **Nothing is lost when switching engines**: anything Rive has no equivalent for (e.g. chef hat, laptop, sakura background) keeps being drawn by the existing Compose `PetPropsOverlay` / `PetBackgroundLayer` / `PetForegroundLayer` over and under the now-transparent Rive artboard.
> - **Touch & sensor reactions**: holographic-hand touches become `react` (HeadPat, PokeL/R escalating to PokeAnnoyed / PokeAngry exactly like `PetStateMachine`, ChinScratch); a surprise with no touch plays Startled, a shake ending in anger plays ShakeAngry, a returning face plays Detected, and the fight-back barrage plays the AngryMissile story before `onMissileBarrageFinished`.
> - **`RiveAvatarView.android.kt` rewritten**: `autoBind = true`, cached view-model properties with change-only writes, gyro → `tiltX/tiltY`, and automatic fallback to Canvas (`RiveRuntime.failed`) if the native library cannot load. The chosen engine is now persisted (`pet.avatar_engine`).
> - **Verified**: `compileDebugKotlinAndroid` BUILD SUCCESSFUL, `RiveAvatarBindingTest` 8/8, all unit-test suites green, every Kotlin index cross-checked against `presets.json`. Not yet run on a physical device.
>
> **Rive Avatar v6: Engine Fixes, LOOI Style & the Status Moodset (2026-09-15):**
> - **Motion actually eases now.** All 3,960 `cubic` keyframes had no `CubicEaseInterpolator`, which Rive treats as *no easing* — every bounce, blink and overshoot was linear. Every keyframe now goes through `kf()`, which attaches the curve, and the generator fails the build if one is ever missing.
> - **Stories and reactions play once and hold.** `seq`/`react` are `oneShot` and every channel state has `reset="true"`, so a host that clears late no longer replays the story, and re-firing starts from frame 0 instead of wherever it was cut.
> - **No more frozen poses.** A new first layer, `RestPose`, keys the rest value of every property any story/state/reaction animates (computed automatically), so clearing Angry mid-way no longer leaves the eyes converged.
> - **Entrances sync to the moment an item appears** (`intro()`): sunglasses drop, chyron slide-in, bullet-hole punches, the flood rising, curtains closing, turrets rising, poke-finger contacts — no longer driven by the shared `PropLoops` clock.
> - **`BlinkGate`**: motion-only states blink through `StateBlink` nodes that a `noblink` face vetoes, so `state = Idle` no longer squashes Dead's crosses or heart eyes. **Speech is visible on mouth-less faces**: a neon `VoiceBar` stretches with `audioLevel` and the head bobs; `VoiceIdle` no longer pins the mouth.
> - **Drawing fixes**: continuous zigzag mouth, mover draw order (camera lens, trash ribs, medal star, chyron text were hidden), burger/beer moved off the eyes, 💢 anger vein, popcorn bucket, tinted snorkel glass, real snot bubble, radiating impact cracks, props kept on-screen.
> - **LOOI style**: eyes 24 % bigger and centred, brows/mouths removed from most faces, one colour per face (Rage is red all over, Angry/Evil get a fang), Squint is `^ ^`, wider gaze.
> - **LOOI status set (IDs 21–40)**: faces `OneEye`, `Recognized`; props 37–55 (Bulb, Bolt, Barcode, Gears, UpdateArrows, Magnifier, SignalBars, PointHand, FaceBrackets, Sun, RainCloud, AlarmClock, Calendar, 7-segment Clock driven by `clockD0..3`, Pencil, Warning, Curtains+Lock, BigBattery, Cracked); presets/seq 45–64.
> - **`scripts/build_rive_avatar.ps1`** now regenerates, verifies, checks `inspect` problems and exit codes, and only then replaces `res/raw/avatar.riv`. Contract: `rive_avatar/AVATAR_CONTRACT.md`. (Wired into Pet Mode — see v7 above.)
> - **Verified**: 0 errors, `problems: []`, 5,327 eased keyframes all with interpolators, `.riv` 230 KB, ~450 headless frames (every face, prop, bg/fg, state, reaction and all 64 stories) captured and eyeballed.
>
> **Rive Avatar v5: Touch Reactions, Self-Play & Sensor States (2026-09-14):**
> - **New `react` channel (0–8) for things that happen *to* it**, declared last of all content layers so it overrides even a running story — poke the cheek mid-story and it flinches, which is the whole point of a pet. `1` HeadPat (ลูบหัว) · `2`/`3` PokeL/R (จิ้มแก้ม) · `4` PokeAnnoyed · `5` PokeAngry · `6` ChinScratch (เกาคาง) · `7` Startled (เสียงดังเกิน) · `8` ShakeAngry (เขย่ามือถือ). Full priority is now **`react` > `seq` > `state` > manual channels**. Poke escalation is the host's job: count taps in a window and pick the rung.
> - **Holographic hand props**: `HoloPat` (33), `HoloPokeL` (34), `HoloPokeR` (35), `HoloChin` (36) — a low-alpha cyan hand with separated fingers under a bright edge and **no shadow plate**, so it reads as something reaching *into* the scene rather than something the robot is wearing. The pat hand strokes across the top of the head, the poke finger comes in from the screen edge and prods the cheek twice, the chin hand wiggles underneath.
> - **Nine more states (11–19)**: `TiltL`/`TiltR` (เอียงฟัง — head tilts toward the sound, that side's eye grows to 1.26 while the far one shrinks to 0.86), `Dizzy` (เวียนหัว from sustained swaying), five `Play*` self-play loops for the 10-second idle timeout — Bounce (flies edge to edge and squashes on every wall), Chase (eyes dart after an invisible fly), Spin, Peek (dives off the bottom edge and pops back wide), Wiggle — and `Asleep`. All five Play loops are motion-only, so they play with whatever expression the robot is already wearing; `presets.json` exports their indices as `selfPlay` for the host to shuffle.
> - **Fixed a second half of the anchor bug**: once a mover no longer sits at 0,0, keyframing x/y **replaces** its value instead of adding to it — so every translation keyframe aimed at an anchored mover was throwing the artwork into the top-left corner. That was the flood water, the smoke, the confetti, the money rain and all four new hands. `rebase()` now offsets those keyframes onto the anchor in one place, shared by `PropLoops` and the story/state/react timelines.
> - **New guard — eyes must not touch when *widened* either.** Blowing both eyes up is a convergence, just a symmetrical one. `max_eye_scale()` gives the ceiling (1.13 for the wide Shock eyes, 1.31 for plain round ones) and `check_eye_scale()` fails the build past it. It caught Startled at 1.42, Detected at 1.28 and PortBlown at 1.24; all three moved the drama into vertical stretch and recoil, which reads better anyway.
> - **Touch zones** (artboard 500×500, scale to the view): `y < 160` pat · `x < 140` / `x > 360` at eye height poke that cheek · `y > 330` chin scratch.
> - **Verified**: 0 errors, `problems: []`, `.riv` 136 KB, every reaction, state and repaired overlay captured headless and eyeballed.
>
> **Rive Avatar v4: The `state` Channel, Gyro Tilt & Mic-Driven Mouth (2026-09-14):**
> - **`state` (0–10) is the plain face at work.** `seq` answers *"what is it feeling?"*; `state` answers *"what is it doing right now?"* — and they are different shapes of animation, which is why they are different channels: a story is a one-shot arc the host fires and clears, a state is an indefinite loop that must be swappable on any frame while the sensors keep driving gaze underneath it. `0` Off · `1` Idle · `2` Ready · `3` Listening · `4` Thinking · `5` Speaking · `6` Detected · `7` Scanning · `8` Standby · `9` WaitCmd · `10` Working. Priority is **`seq` > `state` > `face`/`prop`/`bg`/`fg`**.
> - **Half the states pin nothing.** Idle, Ready, Speaking and Standby key movement only — no Solo keys at all — so they compose with whatever `face`/`prop`/`bg` the host has set: `state = Idle` + `face = Happy` is a *happy* face idling, not a neutral one. Listening, Thinking, Detected, Scanning, WaitCmd and Working pin the expression, because there the state *is* the look. `presets.json` marks which with `states[].pinsFace`.
> - **Sensor inputs**: `tiltX`/`tiltY` (−1…1, gyro) slide the whole face ±26/±20 px and bank it ∓0.06 rad through a new `FaceTilt` node above the gaze rig — plain data binds, so they never collide with the two blend-state layers; `audioLevel` (0…1, mic) stretches the mouth ×0.92…×1.34 through a new `MouthAudio` node, composing with whatever the Speech layer is playing.
> - **Fixed a rig-wide bug: mover anchors.** Rotation and scale on a Rive `Node` happen about that node's *own* origin, so every `mover()` parked at 0,0 with artwork at absolute coordinates was flinging that artwork across the artboard whenever it scaled or spun — the equaliser bars scattered around the eyes, the bullet holes flew in from the corner, the sparkles drifted. `mover(..., anchor=(x, y))` now parks the mover on the artwork's centre and undoes the offset on an inner node. Applied to all 17 affected props, backgrounds and overlays.
> - **New readability guard**: a bar brow over a slab eye with **no mouth at all** is four bars floating in the dark, so `check_faces()` now fails the build on it. Focused dropped its brow and took a flat mouth; Determined kept its brows and took a grin.
> - **`presets.json` gained `durationMs`** per preset and per state, so the host knows when to clear `seq` back to 0. A state needs no timer — it loops until changed.
> - **Verified**: `rive . --verify` 0 errors, `rive inspect . --summary` `problems: []`, `.riv` 121 KB, all 11 states plus tilt/audio extremes captured headless with `--data=state=N --data=tiltX=… --data=audioLevel=…`.
>
> **Rive Avatar v3: Every Mood Is a Story (2026-09-14):**
> - **`seq` is now the primary channel.** All 44 presets have a hand-timed or generated story of at least ~5.5 s — `seq = presetIndex + 1` (see the `seq` field in `rive_avatar/presets.json`); `seq = 0` returns control to the manual `face`/`prop`/`bg`/`fg` channels. A mood is a timeline, not a pose.
> - **Seven hand-written stories**: **Sleepy** (8 s) eyelids sag and snap open three times with the head nodding lower each round → eyes shut → Zzz → snore bubble. **Angry** (9 s) stern cyan wedge eyes → eyes turn red → they slant inward and slide toward each other under a red-alert wash → gun turrets rise from the bottom of the screen and overshoot → barrels sweep back and forth, muzzle flashes strobing, bullet holes punching into the screen one by one, the whole face recoiling. Plus **AngryMissile**, **Crying**, **CryFlood**, **Interview**, **PortBlown**.
> - **`auto_story()` for the rest**: settle on a lead-in expression → scene fades in → the expression lands with an eye-squeeze beat and a head pop → prop arrives → overlay → hold. A `LEADIN` table gives each expression a natural predecessor (Angry ← Determined, Crying ← Sad, Love ← Shy, Excited ← Shock).
> - **Sequences can stage prop motion**, not just swap props: the Seq layer is declared after `PropLoops`, and a story addresses any prop's mover node as `prop:<propIndex>:<moverName>` — that is what raises the turrets, sweeps their barrels and strobes the muzzle flashes. `blinkL`/`blinkR` are addressable separately, which is how the angry eyes converge.
> - **New assets**: `Turrets` and `Snore` props, `BulletHoles` foreground, plus `osc()` / `pulse()` helpers for sweeps and strobes.
> - **Verified**: 0 errors, `problems: []`, `.riv` 114 KB, story beats captured frame by frame with `--data=seq=N --advance=<frame>`.
>
> **Rive Avatar v2: Channel Rig, 2.5D Invisible Head, Story Sequences & Meme Set (2026-09-14):**
> - **Six mixable channels replace the single `emotion`**: `face` (0–31, eyes+brow+mouth), `prop` (0–30 stickers), `bg` (0–10 background scenes), `fg` (0–10 overlays), `eyeAct` (0–6: blink / squint / wide / pop / twinkle / closed / slow-blink) and `seq` (0–8 story timelines). Any face pairs with any prop and any background — one Angry face now serves a dozen different scenes. `rive_avatar/presets.json` ships 44 ready-made combinations so the host can keep a mood-style API on top.
> - **2.5D "invisible head" rig**: yaw and pitch are separate node chains (`FaceYaw > FacePitch > FaceBreathe > {BrowYaw/Pitch, LEyeYaw/Pitch, REyeYaw/Pitch, MouthYaw/Pitch}`). Two `BlendState1DViewModel` layers drive them from `gazeX`/`gazeY` (−1…1 through a `DataConverterRangeMapper`). The split is load-bearing: two layers cannot write the same property, so yaw takes only x/scaleX/rotation and pitch only y/scaleY. Turning right squashes the far eye to scaleX 0.80, widens the near one, and moves brows and mouth by different amounts.
> - **Eyes, brows and mouth are three independent Solos** — a face can have no brow, no mouth, or both, and speech scales the mouth directly (the old bottom LED bar is gone; it read as a second mouth).
> - **Story sequences**: a `Seq` layer declared after every channel layer, so a running story owns the face. `CryStory` = sad droop → two sniffle squeezes with the head shaking → crying arcs → tears falling. Also `WakeUp`, `RageBuild`, `IdeaSpark`, `Boot`, `LaughFit`, `TradeWin`, `TradeLoss`.
> - **Meme + trading set**: angry firing missiles with explosions and a red-alert wash, crying flooding the screen, reporter mic with a "BREAKING" chyron, MindBlown, DealWithIt, MoneyRain, and the trading ones — MarketUp / MarketCrash (animated candle charts + arrows), PortBlown (shattered screen + cracks), StopLoss (SL tag + line over a falling chart).
> - **Prop animation is separate from face animation**: all prop/bg/fg motion is collected into one 360-frame `PropLoops` timeline; each track's natural cycle is snapped to a divisor of 360 and tiled, so short cycles (tears, hearts) keep animating for the whole loop instead of freezing after their first pass, and nothing pops at the seam.
> - **Blink gating per face**: a face marked `noblink` pins the eye scale, and because the Face layer is declared after the Blink layer the pin wins — Dead/Sleepy/Dizzy hold still while Neutral/Angry/Shy blink.
> - **Prop positioning contract**: since any prop can pair with any face, props stay out of brows `y 118–160`, eyes `y 160–270`, mouth `y 300–360` within `x 100–400`. Props that deliberately cover the face (VR visor, snorkel mask, sunglasses) pair with the `Blank` face.
> - **Verified**: `rive . --verify` 0 errors, `rive inspect . --summary` `problems: []`, `.riv` 87 KB, and every face / act / sequence beat captured headless with `--data=<channel>=N --screenshot`.
>
> **Rive Avatar Rewrite: LOOI 32-Moodset, Generated Scene & View Model Data Binding (2026-09-14):**
> - **`scene.rml` is now generated**: All scene markup is produced by `rive_avatar/tools/build_scene.py` from a single `MOODS` table (~2,850 lines out). Ids, animations, states and transitions are allocated by the generator — **do not hand-edit `scene.rml`**. The previous hand-written scene is kept at `rive_avatar/scene_v1_backup.rml.txt`.
> - **32 Moods with props baked into the `.riv`**: LOOI 20-moodset (`0` Normal … `19` CameraMode) plus 12 core emotions (`20` Love, `21` Crying, `22` Sad, `23` Thinking, `24` Listening, `25` Confused, `26` Pout, `27` Dizzy, `28` Bored, `29` Scared, `30` Proud, `31` LowBattery). 17 eye-shape variants and 28 prop groups (burger, beer, headphones, VR visor + popcorn, snorkel mask, devil horns, synthwave grid, hearts, tears, medal, battery …) all live inside the binary — no Compose overlay needed.
> - **Solo-based shape switching**: Each eye is a `<Solo>`; each mood keys `Solo.activeComponentId` (propertyKey **296**) with a `KeyFrameId` hold frame, switching left eye, right eye and prop group in one 1-frame timeline. Selected by `AnyState` → 32 `AnimationState`s gated on `emotion`.
> - **LOOI neon look**: every shape is a crisp cyan body with a `Stroke` + `Feather` halo, backed by a deep-blue shape offset (+4, +9). Note `Feather` inside a `Fill` renders nothing in Rive — it must sit inside a `Stroke`.
> - **⚠️ Control surface changed from State Machine Inputs to View Model Data Binding**: `State Machine 1` no longer declares `StateMachineNumber` / `StateMachineBool`. Values now live on the **`Avatar` view model** (`emotion` 0–31, `gazeX` **−1…1**, `gazeY` **−1…1**, `isSpeaking`, `mouthOpen`), with gaze driven through `DataConverterRangeMapper` into `EyesRoot.x/y`. The existing `setNumberState(...)` calls in `RiveAvatarView.android.kt` are guarded by `inputNames.contains(...)`, so they will **not crash — they will silently do nothing**. Migrate to `view.controller.activeArtboard?.viewModelInstance?.getNumberProperty("emotion")?.value = …` (rive-android 11.12 supports this). Also note `gazeX/gazeY` are no longer 0–100 with 50 as center.
> - **Verified per-mood**: `rive . --verify` → 0 errors, `rive inspect . --summary` → `problems: []`, and all 32 moods captured headless via `rive . --screenshot --data=emotion=N --advance=20` into `rive_avatar/build/shots/`.
> - **Docs**: data contract at `rive_avatar/AVATAR_CONTRACT.md`, architecture at `.obsidian-wiki/02_Components/Rive_Avatar_Engine.md`.
>
> **Architecture Upgrade: Rive Native 6-Layer State Machine & 1D BlendStates (2026-09-14):**
> - **Native 6-Layer Parallel Execution**: Upgraded `scene.rml` to use standard, hardware-accelerated Rive keyframed animations and `BlendState1DInput` running natively in C++ on Android without Luau runtime bottlenecks:
>   - **Layer 1 (Blink)**: Automatic natural eye blink cycle (`AnimBlink`, 210 frames / 3.5s loop, `scaleY`: 1.0 -> 0.08 -> 1.0) on both eyes.
>   - **Layer 2 (Breathe)**: Organic cyber-breathing mouth movement (`AnimBreathe`, 120 frames / 2.0s loop, `scaleY`, `scaleX`, `y`).
>   - **Layer 3 (MicroSaccades)**: Continuous organic micro-saccades (`AnimMicroSaccade`, 180 frames / 3.0s loop) adding subtle lifelike pupil jitter.
>   - **Layer 4 (GazeHorizontal)**: Native 1D BlendState on `gazeX` (0..100, 50=center) blending `LookLeft`, `LookCenterH`, `LookRight` poses across pupil containers and eye containers.
>   - **Layer 5 (GazeVertical)**: Native 1D BlendState on `gazeY` (0..100, 50=center) blending `LookUp`, `LookCenterV`, `LookDown` poses.
>   - **Layer 6 (Speech)**: Dynamic talking mouth (`AnimTalking`, `scaleY` up to 2.6) transitioned reactively via `isSpeaking` boolean input.
> - **Proper Layer Draw Order**: Aligned with Rive's *"The first sibling draws on top"* rule — specular highlight glints draw on pupils, and dark navy pupils draw on top of the cyan outer squircle.
> - **Input Range Normalization**: Mapped Android Compose inputs from [-1f, 1f] to [0f, 100f] (50f center) in `RiveAvatarView.android.kt`.
>
> **Defect Fix: Rive StateMachineInputException & Defensive Input Guarding (2026-09-14):**
> - **Explicit StateMachine Inputs**: Added `<StateMachineNumber>` (`gazeX`, `gazeY`, `emotion`, `mouthOpen`) and `<StateMachineBool>` (`isSpeaking`) to `State Machine 1` in `scene.rml`, recompiled `avatar.riv` (8402 bytes), resolving `StateMachineInputException: No StateMachineInput found with name gazeX`.
> - **Input Name Pre-Validation**: Wrapped all `setNumberState` and `setBooleanState` calls in `RiveAvatarView.android.kt` with `sm.inputNames.contains(...)` checks to guarantee non-existent inputs are never queued into Rive's internal render thread.
>
> **Defect Fix: Rive UnsatisfiedLinkError & Native JNI Initialization Lifecycle (2026-09-14):**
> - **Synchronous JNI Loading**: Fixed `UnsatisfiedLinkError: No implementation found for long FileAssetLoader.constructor()` caused by asynchronous initialization in `LaunchedEffect`. Moved `System.loadLibrary("rive-android")` and `Rive.init(applicationContext)` to synchronous execution in `MainActivity.onCreate()` and `AndroidView.factory`.
> - **Error Boundary & Zero-Crash Fallback**: Wrapped native `RiveAnimationView` creation with defensive try-catch. If native .so loading fails on any unsupported ABI/device, it seamlessly falls back to `PetRobotHeadAvatar` (Compose Canvas) with 0 crash.
> - **Full ABI Support & Verified APK**: Verified `PersonalAIBot-debug.apk` includes `librive-android.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` and embedded `res/raw/avatar.riv`.
>
> **LOOI Moodset Precision Kinetic & Structural Enhancements (Pages 12, 15, 16, 19, 23, 49 - 2026-09-14):**
> - **Page 12 (VR Mode - Faint Eyes in Visor)**: Rendered subtle, translucent cyan eyes visible behind the dark Apple Vision Pro glass visor with gaze tracking.
> - **Page 15 (Focused - Sweeping Scan Grid & Laser)**: Added active vertical laser beam sweeping up and down across the visor and eyes, with perspective grid line traveling highlights and tilting scan offset.
> - **Page 16 (Excited - Yellow Circle to Half-Circle Morph)**: Yellow eyes smoothly morph from full round yellow circles into flat-bottom half-ovals/semi-circles with spinning sparkle stars.
> - **Page 19 (Disgusted - Inward Squeezing Eyes)**: Both `> <` eyes gradually slide inward toward each other into a tight disgusted cringe squeeze with subtle shudder.
> - **Page 23 (Rich - Eyes with Golden $$)**: Added robot squircle eye contours with visor depth and neon cyan rims, nesting the bright golden `$$` dollar signs directly inside the eyes.
> - **Page 49 (Warrior - Dual-Wielding Swords on Left & Right)**: Repositioned swords from crossed-center to dual-wielded on the left and right sides, gripped in cyber robot paws with combat breathing sway and shining glints.
>
> **Defect Rectification: Double Eye Layer Elimination, Background Sensor Leak Fix & LOOI 25-Moodset Visual Enhancements (2026-09-14):**
> - **Elimination of Double Eye Layer Overlay**:
>   - Gated `drawParametricEye` with `emotion !in emotionsWithDedicatedEyeRenderer`. Squircle/parametric base eyes are now strictly rendered for foundational standby states (`IDLE`, `SPEAKING`, `LISTENING`), allowing all custom and expressive emotions to render solely through dedicated vector paths without shape distortion or ghost layer stacking.
> - **Background Sensor & Audio Leak Fix**:
>   - Hardened `PetMotionDetector` with lifecycle run checks, unregistering listeners and clearing detector hooks upon stop.
>   - Cancelled delayed sound jobs (`faceDownSoundJob`, `faceUpSoundJob`, `shakeSoundJob`) in `AlwaysLiveManager` when exiting pet mode, stopping background services, or on screen off.
>   - Added `BackHandler` in `AlwaysLiveScreen` to ensure pressing the device Back key cleanly exits live mode and terminates background audio.
> - **LOOI Moodset 25-Page Detail Enhancements (Matching Reference Sheets 1 & 2)**:
>   - *Page 2 (Happy)*: Clean upward curve eyes `⌒ ⌒` (no double eye) + upward smile mouth `◡`.
>   - *Page 3 (Angry)*: Lowered mouth position + red grimace outline with white fangs `v v`.
>   - *Page 4 (Sleeping)*: Snoring mouth `o` expanding/contracting with breathing + Zzz.
>   - *Page 8 (Wink)*: Animated double wink cycle ("ขยิบตา 2 ที") + pop star sparkle ✦.
>   - *Pages 10 & 19 (Laughing & Disgusted)*: Clean `> <` chevrons (Page 19 inward squint cringe + trash prop).
>   - *Page 11 (Music)*: 4 animated floating musical notes (`♪`, `♫`, `♩`) bobbing around avatar.
>   - *Page 17 (Shy)*: Blush slashes moved down onto cheeks below eyes.
>   - *Page 18 (Surprised)*: Large (48dp) bouncy scaling exclamation mark `!`.
>   - *Page 20 (Camera Mode)*: Camera aperture iris lens on left eye, viewfinder reticle on right.
>   - *Page 23 (Rich)*: Bright golden yellow dollar sign eyes ($$) with deep gold shadows.
>   - *Page 24 (Love)*: Full pink heart eyes ♥ ♥ (`drawHeart`) pulsing with cardiac rhythm.
>   - *Page 25 (Crying)*: Clean dome eyes without squircle overlay + rushing water tears.
>   - *Page 27 (Reading)*: Clean reading scanning eyes + glasses + open book.
>   - *Page 28 (Gaming)*: Enlarged gamepad controller (84x48dp) with clear D-pad & action buttons.
>   - *Page 33 (Detective)*: Panning magnifying glass smoothly scanning left to right.
>   - *Page 36 (Space)*: Enlarged UFO spaceship (48x18dp) with cockpit dome and underbeam dots.
>   - *Page 37 (Party)*: Clean upward arch eyes `⌒ ⌒` + party hat + extending party horn.
>   - *Page 38 (Dreaming)*: Clean closed sleeping eyes `— —` + moon + clouds.
>   - *Page 39 (Exhausted)*: Enlarged panting pink tongue (18x20dp) with cleft line + frown mouth arc.
>   - *Page 42 (Romantic)*: Heart eyes with flirty single-eye wink and gold sparkle star.
>
> **Bug Fix: Infinite Sleep Trigger Loop & High-Energy Inactivity Guards (2026-09-14):**
> - **One-Shot Edge-Triggered GazeStabilizer**: Replaced level-triggered continuous `SLEEPING` flood with an edge-triggered state machine that notifies once per absence period, eliminating frame-by-frame emotion overrides.
> - **Absence Timer Reset**: Touching the screen (petting, tickling, scratching, poking), talking to the assistant, or caring for the pet resets `GazeStabilizer`'s absence timer via `PetVisionBridge.resetAbsenceTimer()`.
> - **High-Energy Inactivity Guards**: Pets with $>30\%$ energy will not be forced into deep sleep from camera face loss or brief idle periods; they transition to `BORED` or play screensaver tricks (`PING_PONG_BOUNCE`, `TIRED_BOUNCE`, `SNOOKER_SHOT`), reserving deep sleep for low battery/energy ($\le 30\%$) or prolonged 10-minute absence.
> - **Active Conversation Protection**: Face absence timeouts are silenced while the assistant is speaking, listening, or executing smart scenes.
>
> **LOOI Robot: 2.5D Spherical Eye Depth & 100% Parametric Emotion Morphing (2026-09-14):**
> - **Authentic LOOI Hardware Visor Lighting (2.5D Spherical Eye Depth)**:
>   - Multi-layer physically plausible lighting inside the visor screen: Top-left light tint (`getEyeHighlightColor`), core radial gradient, deep bottom-right crescent shadow (`getEyeDeepShadowColor`), soft specular glint, and outer neon bloom halo.
>   - Deep crescent shadow matches physical device hue: Cyan `#00F5FF` $\rightarrow$ Deep Indigo `#071952`, Red `#FF3B5C` $\rightarrow$ Deep Burgundy `#42000E`, Pink `#FF4081` $\rightarrow$ Deep Plum `#450624`, Gold `#FFD700` $\rightarrow$ Deep Bronze `#522800`.
> - **Full-Spectrum Parametric Eye Morphing (All 44 Emotions)**:
>   - Zero emotions return `null`. Every single `AvatarEmotion` entry maps to `EyeShapeParams` (`heartAmount`, `starAmount`, `crossAmount`, `domeAmount`, `innerPinch`), morphing smoothly and continuously over 350ms `FastOutSlowInEasing` with no crossfade ghosting.
>   - Physical LOOI Love Mode: Cyan dome eyes (`⌒` with flat bottom: `domeAmount = 0.95f`, `curvature = 0.35f`) with floating pink hearts.
>   - Full Heart Eyes: `AvatarEmotion.ROMANTIC` (`heartAmount = 1.0f`).
>   - Dead Eyes: `AvatarEmotion.DEAD` (`crossAmount = 1.0f`).
>   - Excited Eyes: `AvatarEmotion.EXCITED` (`starAmount = 1.0f`).
> - **Simple Mode vs Rich Mode (`AvatarDetailLevel`)**:
>   - `SIMPLE`: True OLED pitch black (`#000000`) background without pulse aura, with clean iconic props (omits robot claws, food crumbs, and beverage foam bubbles).
>   - `RICH`: Full procedural ambient particles, eating crumbs, and foaming beer bubbles.
>
> **LOOI Robot: 2.5D Face-Tracking Avatar + Smooth Emotion Morphing (2026-09-14):**
> - **Unified Layout & Centralized Geometry (`AvatarLayout.kt`)**:
>   - Unified screen orientation (`isLandscape`) and all face geometry (`eyeDiameter`, `cX`, `cY`, `foreheadY`, `mouthY`, `chinY`, `leftTempleX`, `rightTempleX`) into a centralized `CompositionLocal` (`LocalAvatarLayout`) and layout calculator.
>   - Guarantees identical coordinate space across all rendering layers (`PetBackgroundLayer`, `PetRobotHeadAvatar`, `PetPropsOverlay`, `PetForegroundLayer`), eliminating props and accessory misalignment.
> - **Smooth Face-Tracking & Distance Filter (`GazeStabilizer.kt`)**:
>   - *Adaptive Exponential Moving Average (EMA)*: Filters ±3% camera frame jitter noise while responding instantaneously to intentional head movements (>15%).
>   - *Face Distance Scaling (`faceScaleFactor`)*: Computes user distance ratio to dynamically scale eyes (1.0x $\rightarrow$ 1.22x) with curiosity when user approaches the camera.
>   - *Absence Watchdog Timeout*: Automatically transitions pet state to `BORED` (>12s) and `SLEEPING` (>25s) when the user walks away from the desk.
> - **2.5D Layering Parallax System (`PetModeScreen.kt`)**:
>   - *Background Layer*: Subtle counter-translation (`-gazeX * 14.dp`, `-gazeY * 10.dp`) creating the sensation of scenery deep behind the screen.
>   - *Robot Head Avatar*: 3D perspective rotation (`rotationY` max 35°, `rotationX` max 20°, `cameraDistance = 14f * density`).
>   - *Props Overlay*: Aligned with head 3D yaw/pitch rotation plus forward parallax (`+gazeX * 6.dp`).
>   - *Foreground Layer*: Accelerated translation (`+gazeX * 24.dp`, `+gazeY * 18.dp`) producing floating dust and lens depth in front of the screen.
>
> **LOOI Robot: Page 8 (Wink) Dynamic In-and-Out Gesture with Pop Sparkle Stars (2026-09-14):**
> - **Continuous In-and-Out Dynamic Wink**:
>   - Transformed the static Wink expression (Page 8 / `AvatarEmotion.WINK`) into a procedural 5-phase kinetic in-and-out wink cycle (~2200ms) with organic eye physics:
>     - *Phase 1 (Rest Open)*: Both eyes open wide as squircle cyan 2D depth eyes.
>     - *Phase 2 (Squash & Close)*: Right eye smoothly squashes down (`winkEyeScaleY: 1.0f -> 0.06f`) with volume-conserving horizontal widening (`squashX`).
>     - *Phase 3 (Wink Shut & Star Burst)*: Right eye turns into a clean neon cyan curved sleeping bar (`drawSleepingEye`), while dual golden sparkle stars ✦ (`#FFD700` and `#FFEA00`) pop out (1.35x) and rotate above the closed eye corner. Left eye gives a subtle sympathetic reaction (+4%).
>     - *Phase 4 (Spring Re-open)*: Right eye springs back open into a full round squircle with elastic bounce.
>     - *Phase 5 (Rest)*: Brief pause before the next organic wink cycle.
>
> **LOOI Robot: Comprehensive Living Gestures & Dynamic Emotion Kinetics (2026-09-14):**
> - **Purposeful Motion Over Static Floating**:
>   - Eliminated static, floating icons across all 50 LOOI Robot moodsets. Every expression is paired with expressive procedural kinetic motions that intuitively convey actions and inner thoughts.
> - **Highlights of Living Gestures**:
>   - *Page 3 (Angry)*: High-frequency rage micro-tremors (`angerJitterX/Y`), rhythmic brow slant twitching (-20° to -28° scowl), violently throbbing red anger vein 💢, and clamped white fangs.
>   - *Page 4 (Sleeping)*: Slow nod-off droop with sudden startle recovery (`nodOffOffsetY`) and corkscrew drifting Zzz bubbles.
>   - *Page 8 (Wink)*: Procedural 5-phase in-and-out wink cycle (~2200ms) with right eye squashing into a sleeping bar and dual golden stars ✦ bursting & rotating above.
>   - *Page 10 (Laughing)*: Belly-laugh vertical bouncing (`laughBounceY`), expanding/squinting `> <` eyes, and popping joy sparkle particles.
>   - *Page 14 (Evil)*: Asymmetric brow cocking (`leftSlant` vs `rightSlant`) with bobbing and pulsing purple devil 😈 icon.
>   - *Page 17 (Shy)*: Timid gaze-avoidance saccades (`shyGazeX/Y`) with glowing blush cheek pulses.
>   - *Page 18 (Surprised/Shock)*: Startled micro-jitter on dilated eyes and vibrating red exclamation mark !.
>   - *Page 24 (Love)*: Authentic double-beat cardiac rhythm (Lub-Dub `heartbeatScale`: 1.0 $\rightarrow$ 1.25 $\rightarrow$ 1.12 $\rightarrow$ 1.32 $\rightarrow$ 1.0) directly scaling heart eyes.
>   - *Page 27 (Reading)*: Sawtooth line-by-line eye scanning (`readingScanX`) across book pages.
>   - *Page 37 (Party)*: Horn blower unrolling dynamically from 16dp to 54dp with uncoiling paper tip and confetti bursts.
>   - *Page 39 (Exhausted)*: Heavy respiratory chest heave (`pantHeaveY`) with flapping panting tongue (`pantCycle`).
>   - *Page 40 (Electric)*: Arcade stepped jitter on lightning bolt eyes.
>   - *Page 45 (Magic)*: Dynamic wave-casting arc (`wandArcAngle`) with starburst pulse at the peak of the flick.
>   - *Page 48 (Scared)*: Terror shivering (`shiverFastX/Y`), chattering teeth, and eyes nervously tracking swooping ghosts.
>   - *Page 49 (Warrior)*: Sharp 4-point glint star slide along crossed katana blades (`bladeShineProg`).
>   - *Page 50 (Low Battery)*: Dim cyan brownout flickering and fluttering eye droop (`brownoutAlpha`).
>
> **LOOI Robot: Page 6 (Eating) & Page 7 (Drinking) Living Gestures: Lift-Bite & Head-Tilt Drink (2026-09-14):**
> - **Page 6 (Eating - Burger)**:
>   - Procedural 4-phase feeding gesture: slides up from resting position (+36dp below mouth) to mouth level, chomps down with bread squash and crumb scattering while eyes squint in delight, lowers burger back down, and rhythmically chews happily.
> - **Page 7 (Drinking - Beer)**:
>   - Realistic head and glass tilt gesture: eyes and head tilt back (-7.5°), beer glass raises and tips into the mouth (+24°) pivoting directly at the upper rim at mouth level. Features a double swallow throat pulse with eyes closed in bliss, before lowering the glass upright to rest.
> - **Zero Inter-ocular Obstruction**:
>   - Items stay naturally positioned at mouth/chin level without overlapping between the eyes like a nose.
>
> **LOOI Robot: Page 6 (Eating) & Page 7 (Drinking) Natural Mouth-Level Alignment (2026-09-14):**
> - **Accurate Mouth-Level Positioning**:
>   - *Page 6 (Eating)*: Repositioned the burger down to `eyeCenterY + baseEyeH * 0.68f` with compact width `0.58f`, cleanly resting in the mouth area with the top bun tangent to the lower curve of the eyes and falling crumbs. Leaves the inter-ocular space completely unobstructed.
>   - *Page 7 (Drinking)*: Repositioned the beer glass down to `eyeCenterY + baseEyeH * 0.84f` with width `0.46f`, placing the frothy foam head at the bottom edge of the eyes and extending the glass body downwards. Eyes look downward and inward (`gazeX = ±0.18f`, `gazeY = 0.42f`).
>   - Completely eliminates the "nose-like" appearance between the eyes, matching reference cells 6 & 7 in LOOI Reference Sheet 1.
>
> **Pet Mode Portrait Dialogue Layout: Non-Obstructive Overlay & Bottom Status Dimming (2026-09-14):**
> - **Zero Face Obstruction in Portrait**:
>   - In vertical/portrait orientation (`!isLandscape`), the Pet Robot face remains centered and 100% full scale in the upper half of the screen without being squeezed, shifted, or obstructed.
> - **Dimmed Status Panel & Layered Message Overlay**:
>   - When a dialogue card appears, the bottom status panel (Tamagotchi dashboard) smoothly dims to 8% opacity with a 78% dark backdrop scrim (`tween(320)`).
>   - The dialogue card floats directly on top of the dimmed status panel with a crisp neon cyan border, deep elevation shadow, and backdrop dismiss click listener.
>   - Action buttons (Feed, Clean, Play, Sleep) are temporarily disabled while dialogue is active.
> - **Smooth Auto-Restore**:
>   - Once the dialogue message is dismissed or times out, the bottom status panel smoothly animates back to full 100% brightness.
>
> **LOOI Robot: "Neon Cyan Style Moodset" 2-Layer Flat Glow & Morph Engine (2026-09-14):**
> - **2-Layer Flat Neon-Glow Vector Architecture (`PetRobotNeonDraw.kt`)**:
>   - Shifted entirely to the authentic "Looi Robot: Neon Cyan Style Moodset" aesthetic, completely removing radial gradient fills and white specular highlight dots.
>   - *Dual-Layer Structure*:
>     - *Glow Layer*: Expanded outline/shape (~15–20% larger than core), alpha ~0.4–0.6 with multi-pass soft GPU bloom, matching accent color.
>     - *Core Layer*: Crisp, flat solid fill or stroke (`StrokeCap.Round`, `StrokeJoin.Round`), zero gradients, 100% opaque core.
>   - *Dynamic Accent Colors*: `NeonCyan` (`#00F5FF`) default, `NeonRed` (`#FF3B5C`) for Angry/Evil, `NeonPink` (`#FF4081`) for Love/Shy, `NeonGold` (`#FFD700`) for Rich/Wink, `NeonMint` (`#64FFDA`) for Sick, `NeonGrey` (`#90A4AE`) for Sleeping/Dead.
> - **Morph Animation on Blink & Emotion Switch (`PetRobotHeadAvatar.kt`)**:
>   - *Blink Morph*: 3800ms periodic natural blink with `FastOutSlowInEasing` morphing `scaleY` down to flat slit (0.06f in 170ms) and bouncing smoothly open in 120ms.
>   - *Emotion Switch Morph*: When emotion changes, smoothly squashes down to 0.08f flat slit in 160ms, swaps displayed emotion state, and springs open with bouncy damping (`Spring.DampingRatioLowBouncy`).
> - **Decoupled Out-of-Sync Particle Motion Layer (`rememberPetParticleMotionState()`)**:
>   - Floating hearts, crying tears, rising Zzz streams, rotating star sparkles, dizzy spirals, and sweat drops have dedicated out-of-sync phases for left vs. right (e.g. 1900ms vs 2300ms) for organic lifelike motion.
> - **Preserved Outer 3D Perspective Rotation**:
>   - Outer `graphicsLayer` rotation (`rotationY`, `rotationX`) and spherical offset projection remain active as the outermost camera layer, maintaining the "invisible 3D head" depth while rendering flat neon vector icons inside.
> - **100% KMP & Mobile 60fps Performance**:
>   - Pure Compose Canvas vector math in `commonMain` compatible across Android and iOS without JVM-only dependencies.
>
> **LOOI Robot: True 3D Spherical Face Projection (2026-09-13):**
> - **Native 3D Perspective via Compose `graphicsLayer`**:
>   - The entire Pet Avatar canvas now utilizes `rotationY` and `rotationX` driven by `gazeX` (Yaw) and `gazeY` (Pitch). This produces flawless parallax, natively enlarging the leading eye and foreshortening (squashing) the trailing eye, generating an ultra-convincing "invisible 3D head" illusion.
>   - *Spherical Positional Projection*: Instead of simple 2D translation, the eyes, mouth, and props track along a curved spherical offset formula (`sphereRadius * sin(yaw)`). Center coordinates (`faceCenterX`, `faceCenterY`) correctly pull all props in line with the 3D rotation matrix.
>
> **LOOI Robot: Dynamic Background & Foreground 2.5D Depth Engine (2026-09-13):**
> - **2.5D Layered Atmospheric Depth & Parallax**:
>   - Paired all 50 LOOI Moodsets with tailored Dynamic Backgrounds and Foreground Lens/Atmospheric effects to eliminate visual flatness while preserving the iconic high-contrast dark OLED (#000000) aesthetic.
>   - *Layer Architecture*: Layer 0 (Dynamic Background Theme) $\rightarrow$ Layer 1 (Ambient Aura) $\rightarrow$ Layer 2 (Robot Head Avatar & Living Physics) $\rightarrow$ Layer 3 (Props & Weapons) $\rightarrow$ Layer 3.7 (Dynamic Foreground Lens & Atmospheric Layer) $\rightarrow$ Layer 4+ (Dialogue Cards & UI Controls).
> - **12 New Dynamic Vector Background Themes (`PetBackgroundLayer.kt`, `RobotFaceState.kt`)**:
>   - *Organic Breathing Halo*: Soft radial cyan `#00F5FF` pulse on pure OLED black during idle normal standby.
>   - *12 New Presets*: `CYBER_GRID` (perspective synthwave grid & data streams), `SPACE_NEBULA` (deep cosmic dust & shooting stars), `MAGIC_MYSTIC` (rotating arcane runes & magic rings), `CINEMA_COZY` (warm ambient cinema lighting), `WINTER_BLIZZARD` (swirling snowflakes with diagonal wind gusts), `SUMMER_HEAT` (sunburst rays & rising heatwaves), `WARRIOR_DOJO` (ominous red temple aura), `PARTY_CONFETTI` (sweeping colorful disco cone spotlights), `GOLDEN_VAULT` (rich gold radial glow), `SICK_LAB` (pulsing emerald chemical fume clouds), `SPORTS_ARENA` (stadium floodlights), and `LOW_POWER_CRT` (pulsing crimson emergency warning grid).
> - **17 Dynamic Foreground Lens & Atmospheric Effects (`PetForegroundLayer.kt`)**:
>   - Full-screen procedural Compose Canvas vector effects creating authentic glass/lens depth in front of the avatar: `CYBER_HUD` (sci-fi targeting reticles & scan brackets), `STAR_DUST` (floating bokeh orbs), `MAGIC_SPARKLES` (twinkling 4-point arcane stars), `LENS_REFLECTION` (smooth visor glare sweep), `FROST_VIGNETTE` (crystalline ice spikes along screen borders), `HEAT_DISTORTION` (wobbly vertical heat waves), `RAIN_CONDENSATION` (droplets beading on the glass), `ELECTRIC_SPARKS` (crackling lightning arcs), `FALLING_PETALS` (cherry blossom petals drifting in the wind), `CONFETTI_TUMBLE` (3D tumbling party confetti), `GOLDEN_SHINE` (gleaming gold crosses), `HEART_ORBS` (floating glowing love orbs), `BUBBLE_FLOAT` (rising aquatic/chemical bubbles), `ANAMORPHIC_FLARE` (horizontal anamorphic sci-fi lens streak), `CRT_SCANLINES` (retro 80s phosphor scanlines & vignette), and `CAMERA_VIEWFINDER` (DSLR corner guides & autofocus brackets).
> - **Automated Moodset Catalog Mapping (`LooiMoodsetCatalog.kt`, `JarvisViewModel.kt`)**:
>   - `resolveDefaultBackground()` and `resolveDefaultForeground()` automatically configure each of the 50 moodsets with its matching atmospheric environment.
> - **100% KMP & Mobile 60fps Performance**:
>   - Pure Compose Canvas vector math in `commonMain`, zero external raster bitmaps, lightweight particle pools, battery-friendly on mobile OLED displays. Verified on Samsung Galaxy S22 Ultra.
>
> **LOOI Robot: 50 Moodsets Full Stylistic Parity & On-Device ADB Verification (2026-09-13):**
> - **100% Visual Fidelity against Reference Sheets (Sheet 1: 1–20, Sheet 2: 21–50)**:
>   - *Page 6 (Eating)*: Burger is nested between the inner curves of the neon cyan eyes (`burgerW = 0.95f`, `burgerY = 0.22f`), eyes squint down in contentment; extraneous hands and mouth lines removed to match the original clean LOOI design.
>   - *Page 7 (Drinking)*: Conical pint tumbler glass standing centered right between eyes with golden amber brew, rising carbonation bubbles, frothy foam crown, and inward focused eye gaze (`gazeX = ±0.20f`, `gazeY = 0.25f`).
>   - *Page 12 (VR Mode)*: Compact cinema popcorn bucket (`popW = 0.46f`) anchored cleanly at the bottom-right corner of the Vision Pro VR headset.
>   - *Page 21 (Confused)*: Increased wavy eye stroke to 11dp (15dp depth shadow) for striking neon cyan presence with inverted `¿` and `??`.
>   - *Page 45 (Magic)*: Wizard hat, star-tipped magic wand, and confident smiling mouth arc (`drawSmileArc`).
>   - *Page 46 (Sporty)*: Classic black-and-white soccer ball (⚽) with pentagon panels and seams, tricolor headband, smiling mouth arc.
>   - *Page 47 (Scientist)*: Dual round goggle frames with nose bridge and side straps, bubbling chemical flask, smiling mouth arc.
>   - *Page 49 (Warrior)*: Samurai Kabuto Kuwagata golden V-horns crest, red headband, crossed katanas, angled warrior eyes, smiling mouth arc.
>   - *Page 50 (Low Battery)*: Drooping dimmed cyan eyes, pulsing red battery indicator with exclamation mark, and fading dark grey sparkle `✦` above left eye.
> - **100% Pure Vector Canvas Rendering**: Zero PNGs, zero raster bitmaps, pure math paths in KMP `commonMain`.
> - **Verified On-Device**: Verified via ADB screencaps on Samsung Galaxy S22 Ultra (`R5CT42YEMMM`).
>
> **LOOI Robot: Food & Drink Living Gestures Upgrade (🍔 Burger, 🍺 Beer, 🍿 Popcorn - 2026-09-13):**
> - **Substantial Size Enlargement (2.0x – 2.5x Readability Boost)**:
>   - Replaced tiny static stickers (24–40dp) with prominent, high-detail handcrafted vector models:
>     - 🍔 *Burger* (80dp width): Toasted sesame bun, ruffled crisp lettuce, sliced ripe tomatoes, dripping melted cheddar cheese, flame-grilled patty, bite mark cutout, and rising steam wisps.
>     - 🍺 *Beer Stein* (66dp x 84dp): Heavy European glass stein, rich golden amber brew with rising streaming bubbles, thick overflowing foamy head, and sturdy handle.
>     - 🍿 *Popcorn Bucket* (62dp x 74dp): Classic cinema red-and-white vertical stripes, gold top rim, and heaping golden buttery popcorn kernels.
> - **Living Procedural Action Gestures (`AvatarLivingEngine.kt`, `PetRobotHeadAvatar.kt`)**:
>   - 🍔 *Eating Gesture (Page 6)*: Dual metallic cyber paws (`drawRobotPaw`) hold the burger up to the mouth. Procedural chewing mouth (`drawChewingMouth`) alternates between chomping bite and rhythmic chewing munch (เคี้ยวแก้มตุ่ย) driven by `chewCycle` (850ms), accompanied by glowing rosy cheeks (`#FF80AB`) and falling golden bread crumbs. The robot's eyes squint in delicious contentment (`⌒ ⌒`) focused on the meal.
>   - 🍺 *Drinking Gesture (Page 7)*: Beer stein is tilted at a realistic drinking angle (`-28° to -35°`) gripped by a cyber robot paw. Liquid surface tilts dynamically with effervescent carbonation. The robot's mouth gulps at the glass rim with an animated throat/chin swallowing pulse (`gulpCycle` 1300ms), a cute white foam mustache on the lip, and blissful closed eyes (`⌒ ⌒`) flashing with satisfaction sparkles (`✦`).
>   - 🍿 *VR Popcorn Munching (Page 12)*: Popcorn kernels burst and travel along a parabolic arc trajectory from the bucket into the mouth (`popPhase` 700ms), while the mouth actively chews popcorn under the futuristic Apple Vision Pro VR visor.
> - **Pet Mode Props Synchronization (`PetPropsOverlay.kt`)**:
>   - Fully updated `BurgerProp()`, `BeerProp()`, and `PopcornProp()` with identical enhanced sizes, robot paws, cheeks, crumbs, and gestures when equipped in Pet Mode.
>
> **LOOI Robot: 50 Moodsets Living Procedural Motion Engine (2026-09-13):**
> - **Procedural Canvas Living Physics Over Rive**:
>   - Directly built inside Compose Canvas without requiring external `.riv` asset authoring or heavyweight runtime dependencies, running natively on Android and iOS (100% KMP compatible).
> - **Respiration & Organic Head Sway (`AvatarLivingEngine.kt`)**:
>   - *Volume-Conserving Breathing*: Soft whole-face respiration scaling (`scaleX = 0.985f..1.015f`, `scaleY = 0.98f..1.02f`) preserves physical mass while inhaling/exhaling along with vertical breathing bobbing.
>   - *Organic Micro-Roll*: Natural subtle head sway (`±1.8°` rolling on a 2800ms sine cycle) keeps the robot organically moving even when idle.
> - **Saccadic AI Eye Darting & Micro-Blinks**:
>   - *Saccadic Focus Jumps*: Autonomous micro-saccade eye darts (`±6dp`) fire every 2.5–4.2s to simulate an alert, conscious AI scanning its environment.
>   - *Natural Micro-Blinks*: Subtle eyelid twitches and micro-blinks across all moodsets.
> - **Dynamic Continuous Motion across all 50 Moodsets (`PetRobotMoodsetDraw.kt`, `PetRobotHeadAvatar.kt`)**:
>   - *Every Moodset is Alive*: Shivering thermometers & pulsing mercury (Sick), falling coins & twinkling stars (Rich), flowing water tears & falling droplets (Crying), eye reading scan & page fluttering (Reading), bouncing gaming headphones & flashing D-pad buttons (Gaming), swinging compass needle & fluttering hat (Traveling), rising coffee steam puffs & blinking code cursor (Working), teeth-chattering jitter & falling snowflakes (Cold), radiant pulsing sun rays & dripping sweat (Hot), gliding lens glint (Detective), jumping sizzle food & steam (Cooking), pulsing paint spots & tilting beret (Art), 3D perspective elliptical orbiting stars (Space), twisting/falling confetti & party blower stretch (Party), bobbing cloud & bezier floating Zzz (Dreaming), panting bouncing tongue (Exhausted), crackling lightning bolts (Electric), darting glances (Sneaky), golden mask sparkles (Hero), jumping scanlines & RGB chromatic twitch (Glitched), rotating wand sparkle rays (Magic), spinning basketball seams (Sporty), rising bubbling chemical flasks (Scientist), trembling pupils & ghost wisps (Scared), fluttering headband ribbons & katana steel glint (Warrior), pulsing low-battery warning (Low Battery), swaying rose in mouth & pulsing blushing cheeks (Romantic), undulating wavy eyes `~ ~` with wobbling question marks (Confused), dancing music headphones (Music), floating VR headset glint (VR Mode), and rising diving bubbles (Diving).
>
> **LOOI Robot: 50 Moodset Voice Navigation, Display Rendering & Verification System (2026-09-13):**
> - **Display Rendering & Speaking Override Fix (`AlwaysLiveScreen.kt`, `JarvisViewModel.kt`)**:
>   - *Speaking Emotion Preservation*: When AI responds verbally ("แสดงหน้าที่ 10 ให้บอสตรวจสอบแล้วค่ะ"), the avatar face preserves the requested moodset emotion on screen and renders audio waveform mouth animation over it, rather than overwriting it with a generic speaking face.
>   - *Decay Timing Sync*: Moodset stays active during AI speech and holds for 2.5s after speech completes before auto-reverting to standby IDLE.
>   - *Dual StateFlow Synchronization*: Synchronizes both `testEmotionOverride` and `testFaceStateOverride` with `PetModeController.activeInstance` to ensure exact face rendering across all composables.
>   - *SFX De-duplication*: Prevents rapid duplicate sound effects when both voice transcript and tool call fire simultaneously.
> - **Page 4 (Sleepy) Infinite Wake-Up Storm Fix (`PetModeController.kt`, `AlwaysLiveScreen.kt`, `PetRobotHeadAvatar.kt`)**:
>   - *Wake-Up Loop Elimination*: Removed spurious `wakeUp()` invocation from `notifyInteraction()`. Touch and sleep/wake transitions are cleanly resolved through `PetStateMachine` without audio-level interference.
>   - *Catalog Test Immunity*: Guarded `wakeUp()` and `applyStateMachineResult()` from overriding active test emotions while running catalog inspection (`🎭`).
>   - *Mouth Waveform on Sleep*: Added speech waveform animation to `AvatarEmotion.SLEEPING` when AI speaks.
> - **Direct Voice Commands for all 50 Pages (`LooiMoodsetCatalog.kt`, `VoiceController.kt`, `ChatController.kt`)**:
>   - Users can now test and inspect all 50 moodsets using natural voice commands: `"หน้าที่ 1"` through `"หน้าที่ 50"` (also supports Thai words `"หน้าที่หนึ่ง".."หน้าที่ห้าสิบ"`, Thai numerals `"หน้าที่ ๑".."หน้าที่ ๕๐"`, and chat commands `/avatar 1..50`).
>   - Loops all pages consecutively with `"หน้าทั้งหมด"`, `"เล่นทุกหน้า"`, `"แสดง moodset ทั้งหมด"`, or `"all moods"` (`/avatar all`).
> - **Gemini Live WebSocket Tool Bridge & Persona Voice Sync (`LiveToolBridge.kt`, `DeviceControlExecutor.kt`, `JarvisPersona.kt`)**:
>   - *Zero-Hallucination Argument Interception*: `LiveToolBridge.kt` parses user speech like `"หน้าที่ 10"` or `"หน้าที่ 11"`, overriding hallucinated Gemini function calls with `action=page, page=...` to ensure Page 10 reliably displays Laughing (> < eyes) and Page 11 displays Music (Over-ear headphones), Page 12 displays VR Mode (Vision Pro visor + popcorn), etc.
>   - *Anti-10-Face Hallucination Voice Rules*: System prompts and runtime voice rules enforce knowledge of all 50 LOOI Moodset pages, strictly forbidding claiming page 11 doesn't exist or saying there are only 10 faces.
>   - *Hardware Control Execution*: `DeviceControlExecutor.kt` and `App.kt` route `PAGE|$page` and `ALL` commands directly to `showMoodsetPage` and `playAllMoodsets`.
> - **3-5 Second Verification Pacing & Auto-Revert (`JarvisViewModel.kt`)**:
>   - Individual moodsets display for **4.5 seconds** (3-5s range) accompanied by characteristic sound effects and live status before seamlessly returning to standby.
>   - Full sequence inspection cycles each page for **4.0 seconds** per moodset.
> - **Full Catalog Documentation with 100% Verification**:
>   - Sheet 1: 20 Moodsets (Pages 1-20) from "LOOI 20 MOODSET (NEON CYAN STYLE)".
>   - Sheet 2: 30 Additional Moodsets (Pages 21-50) from "LOOI 30 ADDITIONAL MOODSET (NEON CYAN STYLE)".
>   - Every single entry is documented and verified in source code with explicit page numbers and visual specifications.
>
> **LOOI Robot: 30 Additional Moodset (Neon Cyan Grid Expansion - 2026-09-13):**
> - **Full Expansion to 56 Avatar Emotions & 96 Interactive Props**:
>   - Implemented 27 new distinct emotions based on the reference asset sheet: `SICK`, `RICH`, `CRYING`, `READING`, `GAMING`, `TRAVELING`, `WORKING`, `COLD`, `HOT`, `DETECTIVE`, `COOKING`, `ART_MODE`, `SPACE`, `PARTY`, `DREAMING`, `EXHAUSTED`, `ELECTRIC`, `SNEAKY`, `ROMANTIC`, `HERO`, `GLITCHED`, `MAGIC`, `SPORTY`, `SCIENTIST`, `SCARED`, `WARRIOR`, `LOW_BATTERY`.
>   - Enhanced existing emotions: `CONFUSED` (wavy squircle eyes `~ ~` & dual `??` with inverted `¿`), `LOVE` (radiant pink heart eyes `♥♥`), `ROMANTIC` (blushing cheeks `///`, kiss mouth `3`, and red rose in mouth `🌹`), and `THINKING` (thought bubble `💭`).
>   - Added 34 new specialized props in `PropType` with Thai and English keyword voice parsing.
> - **Modular Canvas Drawing Architecture (`PetRobotMoodsetDraw.kt`)**:
>   - Clean separation of drawing functions keeping code modular and performant.
>   - Pure 2D graphic assets on solid OLED black (`#000000`) with dual-layer shallow depth (`#00F5FF` front + `#004D6B` shadow offset Y = +5dp).
>   - Full integration with smooth continuous anticipation squash & stretch and dual-layer cross-fade transitions.
>
> **LOOI Robot: 20 Moodset (Neon Cyan Style) & Smooth Continuous Emotion Morphing (2026-09-13):**
> - **2D Vector Minimalist Neon Cyan Style (`PetRobotHeadAvatar.kt`)**:
>   - 100% faithful replication of the reference LOOI robot asset sheet on solid OLED black (`#000000`).
>   - Neon cyan squircle eyes (`#00F5FF`) with dark cyan shadow layer (`#004D6B`) offset Y = +5dp providing subtle 2D shallow depth, soft edge glows, and zero 3D overhead.
> - **12 New LOOI Moods & 7 Specialized Props**:
>   - Added `DEAD` (X X cyan cross eyes), `LAUGHING` (> < minimalist laughing eyes), `MUSIC` (eyes + over-ear headphones), `VR_MODE` (Apple Vision Pro curved visor + mini popcorn bucket), `DIVING` (snorkel mask + breathing tube + bubbles), `EVIL` (fiery red slanted eyes + fangs + purple devil icon), `FOCUSED` (sharp wedge eyes + laser synthwave grid), `SHY` (blushing cheeks /// ///), `DISGUSTED` (open trash bin + trash icon), `CAMERA_MODE` (orange DSLR camera icon), `EATING` (mini layered burger 🍔), and `DRINKING` (frothy cold beer mug 🍺).
>   - Expands `AvatarEmotion` to 29 states and `PropType` to 62 built-in props.
> - **Continuous Motion & Seamless Emotion Transitions**:
>   - Eliminates all hard cuts and visual snapping between emotions.
>   - *Disney Anticipation Physics*: 350ms squash-and-stretch curve (`1f - 0.08f * sin(p * π)`) where eyes compress slightly before springing into new emotional expressions.
>   - *Progressive Parameter Morphing*: Gradual eye slanting (`eyeSlantProgress`), sliding fangs (`fangsProgress`), inflating anger veins (`angerVeinProgress`), drooping sad tilt (`sadProgress`), and smooth color interpolation (`lerp`).
>   - *Dual-Layer Cross-Fade*: Interpolates previous emotion alpha `(1 - p)` and target emotion alpha `p` concurrently when shapes morph between completely distinct contours.
>
> **Pet Mode Visual Polish & Facial Geometry Anchoring (55 Alive Props & Clumsy Pet Personality - 2026-09-13):**
> - **Facial Geometry Spatial Anchor (`calculateGeometry`)**:
>   - Anchors top headwear and shelters (e.g. `UMBRELLA` 175dp wide, `CROWN` 94dp wide) *directly above the robot's eyes* (`foreheadY = cY - eyeDiameter * 0.65f`) rather than floating near screen bezels across all screen aspect ratios.
>   - Centers food, drinks, and handhelds at `mouthY = cY + eyeDiameter * 0.58f`, and accessories at temple points (`leftTempleX`/`rightTempleX`).
> - **Living Physics & Animation Overhaul across 55 Built-in Props**:
>   - Animated effects: steam wisps on burgers and hot coffee, dripping gooey stretchy cheese on pizza, melting drops on ice cream, popcorn actively popping in parabolic arcs, boba pearls swishing, effervescent beer bubbles, rain splashes on umbrella canopy, and spinning glints on jewels.
> - **Clumsy Pet Comedy Personality ("นึกว่าบอสอยากกินอันนี้... เผลอหยิบผิดถัง!")**:
>   - When teased or corrected by the user ("นี่มันป๊อปคอร์นไม่ใช่เบอร์เกอร์", "หยิบผิดแล้ว"), the pet acts adorable, confused, and embarrassed (`CONFUSED` / `SWEAT_DROP` / `TILT_LEFT`) and playfully switches to the right item.
> - **Rain Umbrella Archetype (`RAIN_UMBRELLA`) & Precise Prop Detection**:
>   - Added 16th scene archetype `RAIN_UMBRELLA` with sheltering umbrella and raindrops.
>   - Automatic NLP prop detection (`detectSpecificPropFromText`) extracts exact items from speech and feeds them directly to the scene engine.
>
> **Smart Scene Archetype Engine & 55 Built-in Handcrafted Props (2026-09-13):**
> - **15 Smart Scene Archetypes across 4 Categories (`PetSceneEngine.kt`)**:
>   - *Activity*: `EATING`, `DRINKING`, `BATH_CLEAN`, `PLAY_GAMING`, `STUDY_WORK`
>   - *Viral Meme*: `MEME_THUG_LIFE` (Deal With It Sunglasses), `MEME_RICH` (Floating Crypto Gold Coins), `MEME_ROYAL` (Golden Crown)
>   - *Comedy*: `COMEDY_FIRE` (Running on fire), `COMEDY_THUNDER` (Electrocuted spiral eyes), `COMEDY_SOUL_OUT` (Exhausted floating skull)
>   - *Dramatic Emotion*: `ANGRY_MISSILE` (Furious 5-missile barrage), `SUPER_LOVE` (Surging hearts), `DRAMATIC_CRY` (Heartbroken tears), `CELEBRATION` (Party poppers & balloons)
> - **Dual Invocation Strategy (Smart Random vs. Specific Voice Override)**:
>   - *Default Smart Random*: Quick care taps (e.g. 🍖 Feed Pet) or generic requests ("กินข้าว") randomly pick from item pools (Burger, Pizza, Cake, Ice Cream, Popcorn) + time-aware environment.
>   - *Specific Voice Override*: Exact user voice requests ("ขอดื่มกาแฟหน่อย", "ใส่แว่นตาหน่อย") immediately trigger target scenes with the exact requested prop (`COFFEE`, `SUNGLASSES`).
> - **Time-Aware Environmental Lighting (`resolveSmartBackground`)**:
>   - Automatically shifts background ambiance based on current time: Morning/Day (06:00-16:59 `SUNNY`), Sunset (17:00-19:59 `SAKURA`), Night/Midnight (20:00-05:59 `NIGHT`).
> - **3-Act Animation Pacing (3.8s – 5.0s)**:
>   - Re-engineered pacing to give every scene a clear 3-act narrative: [Act 1: Anger/Build-up] $\rightarrow$ [Act 2: Visible 3D Action] $\rightarrow$ [Act 3: Impact & Settle] with auto-revert timer to calm idle.
> - **Deprecating Dynamic SVG in Favor of 55 Handcrafted Canvas Props**:
>   - Dynamic SVG generation deprecated and replaced by 100% Canvas-rendered handcrafted props for crisp, pixel-perfect, and high-framerate rendering on mobile.
>
> **Always_AI_Live_Mode Overhaul: Driving Safety, Smart Parking Memory & Low-Glare Night Mode (2026-09-13):**
> - **Speed Limit Alert & Over-Speed Monitoring (`DriveBridge.kt`, `DriveModeController.kt`, `DriveModeScreen.kt`)**:
>   - Added configurable `speedLimitKmh` (default 120 km/h) and `isSpeeding` telemetry tracking.
>   - Interactive speed limit badge on HUD allowing drivers to cycle speed caps (80 $\rightarrow$ 90 $\rightarrow$ 100 $\rightarrow$ 110 $\rightarrow$ 120 km/h).
>   - Visual speeding alert: Speedometer switches to bright Crimson Red with a 2dp highlighted border and displays a high-visibility warning banner (`⚠️ ขับขี่เกินความเร็วที่กำหนด`).
> - **Smart Parking Location Memory (`DriveBridge.kt`, `DriveModeController.kt`, `DriveModeScreen.kt`, `LiveToolBridge.kt`)**:
>   - Persistent `ParkingLocation(latitude, longitude, address, timestamp)` model and StateFlow.
>   - One-tap "🅿️ จำจุดจอดรถ" quick save button on the media control panel.
>   - In-car parking status card showing current parked address, a one-tap "🗺️ นำทางไปรถ" Google Maps navigation launcher, and a clear button.
>   - Voice Intent Support: Natural speech queries ("รถจอดอยู่ที่ไหน", "จำที่จอดรถตรงนี้", "where did I park") are intelligently intercepted and answered without hallucination.
> - **Low-Glare Night Driving Mode (`DriveBridge.kt`, `DriveModeScreen.kt`, `LiveToolBridge.kt`)**:
>   - Dedicated "🌙 / 🕶️" night mode toggle on HUD and voice control ("เปิดโหมดกลางคืน", "ลดแสงสะท้อน").
>   - Screen background shifts to Pure OLED Pitch Black (`#000000`), visualizer aura drops to 0.04f (reducing glare by 80%), and cards switch to deep dark translucent surfaces to preserve driver night vision.
> - **God Object Decomposition (`AlwaysLiveScreen.kt`, `PetModeScreen.kt`, `DriveModeScreen.kt`, `AvatarEmotionColors.kt`)**:
>   - Monolithic 2,471-line `AlwaysLiveScreen.kt` decomposed into `PetModeScreen.kt` (~800 lines), `DriveModeScreen.kt` (~570 lines), and `AvatarEmotionColors.kt` (~80 lines).
>   - `AlwaysLiveScreen.kt` reduced down to ~730 lines as a clean profile router.
> - **Intelligent Voice Intent Routing & Structured Sentiment (`LiveToolBridge.kt`, `AlwaysLiveManager.kt`)**:
>   - Intercepts music playback, navigation, notifications, speed/location, parking, and night mode queries, safely redirecting to native tools.
>   - Full bracketed emotion tag extraction `[HAPPY]`, `[LOVE]`, `[SAD]` and pipe syntax `EMOTION|...` support.
> - **KMP/iOS Readiness & Background Power Management**:
>   - All `System.currentTimeMillis()` calls in `commonMain` converted to `Clock.System.now().toEpochMilliseconds()`.
>   - Pauses hidden camera background vision while pet sleeps or when no processor is active.
> - **Unit Testing**:
>   - Complete coverage in `DriveModeTest.kt` (100% pass across `AlwaysLiveTest`, `PetModeTest`, `DriveModeTest`).
>
> **Pet Vision: Robust 2-Turn Real-Time Vision, Cyber Circular Radar Eyes & Portrait UI Polish (2026-09-13):**
> - **Robust 2-Turn Real-Time Vision Pipeline (`LiveToolBridge.kt`, `JarvisPersona.kt`)**:
>   - *Eliminates Turn-1 Hallucination*: When opening camera via `vision_activate`, AI speaks a 1-sentence intro acknowledgment without guessing while the camera hardware focuses and streams 2-3 live frames.
>   - *Real-Time Trigger via `sendRealtimeText`*: As soon as Turn 1 finishes, system triggers Turn 2 using `realtimeInput.text` to synthesize the user's question, immediately prompting Gemini Live to analyze the clear live camera image and answer accurately.
>   - *Turn 2 Auto-Close Fallback*: Once the real visual answer finishes speaking, if the model omits `vision_deactivate`, an automatic 1000ms watchdog safely powers down the camera and folds down the eye visor.
> - **Eye-Overlay Circular Viewfinder & Procedural Cyber Radar Audio (`AlwaysLiveScreen.kt`, `RobotSoundEngine.kt`)**:
>   - Circular camera view (`CircleShape`) overlay directly onto the robot's physical eye:
>     - Right Eye: Live optical camera viewfinder with cyan neon glow, rotating aperture ring, and AR bounding boxes.
>     - Left Eye: 360° sweeping holographic radar scanner, target reticle, and detection blips.
>   - Pure 16-bit procedural PCM `SCAN_RADAR` SFX (1400Hz $\rightarrow$ 2600Hz sweep + 35Hz sinusoidal FM modulation) with zero external assets.
> - **Portrait Layout & Navigation Polish (`AlwaysLiveScreen.kt`)**:
>   - Added a pinned `X` exit button on top-right in portrait mode to ensure instant exit capability from Pet mode at all times.
>   - Streamlined the driving/control mode toggle into a clean, compact single car icon matching the other control buttons.
>
> **Pet System: Motion Sickness, Table Thump/Audio Reactions & Enraged Fight-Back Mode (2026-09-12):**
> - **Motion Sickness & Disturbance Matrix (`PetMotionDetector.kt`, `PetMotionBridge.kt`, `PetStateMachine.kt`)**:
>   - *Phone Shake*: Light shake induces dizziness (`AvatarEmotion.DIZZY`, spiral eyes, wobbly mouth). Heavy or rapid shake (>3 shakes in 4s) makes the pet furious (`AvatarEmotion.ANGRY`) and charges its Rage meter (+35f).
>   - *Boat Rocking / Seasick*: Alternating roll tilt under low-G (<1.6G) mimics boat motion on ocean waves; pet gets seasick and dizzy (`AvatarEmotion.DIZZY` with nauseated SFX).
>   - *Table Thump Shock*: Detects sharp shock impulses ($\Delta G > 1.25G$) when the resting phone's surface is banged; pet jumps with shock (`AvatarEmotion.SURPRISED`).
> - **Acoustic Disturbance & Yelling Reaction (`AlwaysLiveScreen.kt`, `PetStateMachine.kt`)**:
>   - Evaluates mic input spikes (`audioLevel > 0.68f`) during user speech. Initial loud shouting startles the pet (`AvatarEmotion.SURPRISED`). Persistent yelling causes the pet to cower and weep (`AvatarEmotion.SAD`, glowing teardrops).
> - **Enraged Mode & Fight-Back Missile Barrage (`PetNeedsState.kt`, `PetRobotHeadAvatar.kt`, `MissileBarrageOverlay.kt`, `RobotSoundEngine.kt`)**:
>   - *Rage Meter*: Full rage (100%) triggers `isEnraged = true` (Mood: `ENRAGED` "😡💥 โกรธจัด!").
>   - *Fierce Battle Visor*: Fiery red glaring eyes (`Color(0xFFFF1744)`), deep 24° V-eyebrows, sharp 6-serration clenched zigzag mouth, rising steam puffs, and fiery aura.
>   - *Cartoon Missile Barrage & Screen Explosions*: Procedural vector rendering of 5 cartoon rockets styled after reference artwork (white aerodynamic fuselage, red pointed nosecone, red tail fins, cyan window, thruster flames) curving towards the user screen, scaling from 0.4x to 1.9x upon impact. Triggers expanding fireballs, shockwave blast rings, 45-particle spark shrapnel, screen flash, and dynamic screen shake.
>   - *Pure PCM Procedural Audio*: Synthesizes `MISSILE_LAUNCH` (pitch sweep 300Hz $\rightarrow$ 2000Hz + rocket burn white noise) and `EXPLOSION` (sub-bass punch 85Hz $\rightarrow$ 25Hz + exponential noise decay) in real-time with zero external audio assets.
>
> **Pet System Overhaul: State Machine Matrix, Holographic Hand Overlay, Persistent Pet Memory & Slide-out Sidebar Panel (2026-09-12):**
> - **Pet State Machine Matrix (`PetStateMachine.kt`)**: Advanced emotion resolution engine linking physical needs (satiety, energy, hygiene) and interaction frequency to emotional states (`HAPPY`, `LOVE`, `EXCITED`, `ANGRY`, `SAD`, `SURPRISED`, `BORED`, `POUT`, `DIZZY`). Features anti-spam ring buffer tracking rapid pokes, tickles, and strokes with natural decay.
> - **Holographic Hand Overlay (`HolographicHandOverlay.kt`)**: Procedural Canvas-rendered Sci-Fi glowing cyan (`#00F0FF`) holographic hand overlay with fingertip sparkle trails. Animates context-aware gestures (`STROKE` for forehead pet, `POKE` for cheeks, `CHIN_SCRATCH` for chin, `TICKLE` for double-tap cheek, `PAT` for center taps).
> - **Slide-out Pet Needs Sidebar (`PetNeedsSidebarPanel.kt`)**: Replaced hidden settings dialog tab with an always-accessible side drawer sliding from the right edge with spring physics. Displays animated color-coded needs bars, quick care action buttons (feed, bathe, play, sleep), mood badge with live emoji, and memory statistics.
> - **Persistent Pet Memory (`PetMemory.kt`)**: Dedicated SQLite-backed memory store recording pet lifetime metrics (age in days, total feeds/cleans/plays, emotional streaks, learned favorite interactions). Includes 60s periodic auto-save and state restoration on app launch.
> - **New Emotions & Visor Expressions (`AvatarEmotion.kt`, `PetRobotHeadAvatar.kt`)**: Added `SURPRISED` (1.25x expanded electric white-cyan eyes, arched brows, open gasp 'O' mouth) and `BORED` (0.42x squashed slate gray eyes, drooping brows, lazy tilt, flat sigh mouth).
>
> **Autonomous Contextual Prop Selection, Dynamic SVG Magic Creator & Permanent SQLite Persistence (2026-09-12):**
> - **Autonomous Contextual Prop Selection (`JarvisPersona.kt`, `DeviceToolDefinitions.kt`)**: The AI Pet proactively and autonomously evaluates conversation themes, thoughts, and emotions to equip fitting props and stickers (e.g. morning coffee, crypto gold coins, rainy umbrellas, celebration poppers) without waiting for explicit user commands.
> - **Autonomous Dynamic SVG Creation & Reuse (`PetModeController.kt`, `DeviceControlExecutor.kt`)**: When no built-in prop fits the conversation (e.g. pirate stories, chef cooking, detective mysteries), the AI Pet autonomously dreams up and generates standard SVG paths via `device_custom_prop`. When reusing a previously created prop, the AI can simply reference it by `name` without resending heavy SVG strings.
> - **Permanent SQLite Persistence (`PetCustomPropStore.kt`)**: Implemented a cross-platform singleton repository persisting custom props into SQLite via SQLDelight (`AppSetting` table, key `"pet.custom_props"`). Custom props survive app reboots and remain permanently available in the pet's wardrobe.
> - **Facial Spatial Intelligence & Auto Eye-Fit 1:1 (`DynamicPropRenderer.kt`)**: Synchronized facial coordinates and dynamic eye diameter (`minOf(H * 0.52f, W * 0.28f)` on landscape, `minOf(W * 0.38f, H * 0.24f)` on portrait). When `size=0` on `LEFT_EYE` or `RIGHT_EYE`, accessories like monocles, glasses, and pirate eyepatches automatically scale 1:1 to match the robot's physical eye diameter.
> - **Settings Showcase Vault UI (`PetSettingsDialog.kt`)**: Integrated a reactive persistent vault view under the SVG Vector tab, listing all permanently stored custom props with live wear/remove toggles and delete-from-vault capabilities.
>
> **Pet Settings Showcase Catalog (8 Themes, 55 Props) & Dynamic SVG Vector Parser Architecture (2026-09-12):**
> - **Interactive Showcase Catalog (`PetSettingsDialog.kt`)**: Added a 4th dedicated tab `"🎨 พร็อพ & ธีม"` to Pet Settings, allowing users to browse, test, and customize all visual elements:
>   - *8 Background Themes (`BackgroundTheme`)*: `DEFAULT` (OLED Dark Visor), `RAINY` (Rain particles), `SUNNY` (Warm sun pulse), `NIGHT` (Starlit sky), `SAKURA` (Falling cherry blossoms), `MATRIX` (Digital cyber rain), `LOVE_BG` (Floating hearts), `THUNDER` (Lightning flashes) with real-time preview swatches and instant tap-to-switch.
>   - *55 Built-in Props & Stickers (`PropType`)*: Categorized 2-column card grid with category filter chips (All, Mood 17, Food/Daily 13, Nature/Weather 10, Tech/Tools 15), Thai descriptions, live active status badges, multi-prop equipping, and one-tap "Clear All" action.
>   - *Dynamic SVG Vector Parser & Presets*: Explains input conditions and facial positioning constraints. Features 5 instant-test presets (👑 Golden Crown, 🕶️ Cyber Neon Visor, 🩹 Cute Bandage, ⚡ Neon Bolt, 🤿 Diving Mask) and lists active custom props with individual delete buttons.
> - **Dynamic SVG Vector Parser Architecture (`DynamicPropRenderer.kt`, `DynamicVectorProp.kt`)**:
>   - *Input Format*: Standard SVG Path data string `d="..."` (`M`, `L`, `C`, `Q`, `A`, `Z`).
>   - *Auto-Fit & Normalization*: Measures path geometry via `Path.getBounds()` and automatically scales matrix to `sizeDp` without coordinate system or viewBox restrictions.
>   - *7 Anchor Positions*: `FOREHEAD`, `LEFT_EYE`, `RIGHT_EYE`, `CHEEKS`, `CHIN`, `FLOATING_LEFT`, `FLOATING_RIGHT`.
>   - *5 Animation Types*: `STATIC`, `FLOAT_BOB` (gentle float), `PULSE` (breathing/beat), `ROTATE_CONTINUOUS` (360° spin), `SWAY` (pendulum tilt).
>   - *Lifecycle & Persistence*: Managed in runtime face state memory (`RobotFaceState.customProps: List<DynamicVectorProp>`) for continuous live display across face interactions.
>
> **Pet Avatar Eye Scaling, True Center Gaze (Desk Elevation Calibration) & Pure OLED Visor Cleanup (2026-09-12):**
> - **Enlarged Eye Dimensions (`PetRobotHeadAvatar.kt`)**: Significantly increased eye sizes on both landscape and portrait screens. Landscape eye diameter now reaches `minOf(canvasH * 0.52f, canvasW * 0.28f)` (over 50% of visor height, ~65% larger than previous cap), while portrait diameter reaches `minOf(canvasW * 0.38f, canvasH * 0.24f)`. Eyes dominate the visor with expressive companion robot presence matching Eilik and LOOI. Proportional 30% eye gap is maintained via `baseSpacing = eyeDiameter * 0.65f`.
> - **True Center Gaze Fix (Eliminate Upward Eye Bias)**:
>   - *Avatar Concentric Neutral Eye Alignment*: Removed `baseDepthY = radius * 0.08f` offset from `drawDualCircleEye` in `PetRobotHeadAvatar.kt`. `backOffsetY` now strictly equals `-gazeY * maxShift * 0.35f`. When looking straight ahead (`gazeX = 0f, gazeY = 0f`), the front cyan circle and rear deep blue circle are 100% concentric with zero upward drift.
>   - *Desk Camera Elevation Calibration (`PetVisionDetector.kt`)*: Calibrated the front camera line of sight for phones resting on desks/stands. Since user faces naturally sit in the upper 20-35% of the frame (`rawNormY ≈ -0.45f` to `-0.75f`), added `deskNeutralBiasY = -0.45f` and deadzone filtering (`|X| < 0.12f, |Y| < 0.15f -> 0f`) so normal sitting in front of the phone produces dead-center gaze (`0f, 0f`) with direct eye contact.
>   - *Face Lost Auto-Reset*: Automatically dispatches `onGazeDetected(0f, 0f)` to center the robot's eyes when no face has been seen for >1000ms.
> - **Pure OLED Visor Cleanup**: Removed the center radial breathing glow aura, delivering pure pitch-black OLED (`#000000`) between the eyes for maximum contrast and zero visual haze.
> - **Dynamic Mouth Vertical Rebalancing**: In idle mode with no speech/mouth, eyes sit at exact screen centerY. When a mouth appears (AI speaking waveform or emotional expressions like happy, pouting, speaking), eyes smoothly shift upward (`eyeDiameter * 0.085f` ~17dp) using spring physics, and the mouth anchors below at `eyeCenterY + baseEyeH * 0.75f`, maintaining perfect vertical facial harmony centered on the display.
>
> **Dedicated Weather Tool, Pet Mode Dialogue Auto-Dismiss, Floating Props & Eilik Face Redesign (2026-09-12):**
> - **Dedicated Weather Tool (`device_weather`)**: Added custom GPS/City weather lookup tool using Open-Meteo REST API. Eliminates reliance on `search_web` for weather queries. Retrieves device coordinates from `LocationProvider`, translates WMO codes to Thai forecasts, changes avatar props/backgrounds (`RAINY` / `SUNNY`), and triggers expressive sound effects.
> - **Pet Mode Dialogue Card Auto-Dismiss & Tap-to-Dismiss (`AlwaysLiveScreen.kt`)**: Automatically dismisses the dialogue card 10 seconds after AI completes speaking when user is not touching the screen. Tapping anywhere on the display immediately closes the card.
> - **Floating Props & 12s Auto-Decay (`PetPropsOverlay.kt`, `RobotFaceState.kt`)**: Added `GOLD_COIN` (for finance/trading) and `RAIN_DROPS` (for rain weather) alongside existing catalog. Transient props automatically decay back to normal after 12 seconds.
> - **Virtual Desk Pet: Care-Specific Procedural SFX & Portrait Split-Screen Dashboard (2026-09-12)**:
>   - **Procedural Care Audio (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`)**: Added 3 zero-asset, ultra-low latency (<10ms) 16-bit PCM procedural sounds: `CRUNCH_EAT` (3 crispy crunchy chewing chomps when feeding 🍖), `BUBBLE_POP` (5 sparkling water droplet bubble chirps when bathing 🧼), and `BELL_TOY` (bright two-tone metallic chime G6+C7 when playing 🎾).
>   - **Portrait Split-Screen Dashboard (`AlwaysLiveScreen.kt`, `PetNeedsSidebarPanel.kt`)**: In portrait mode (`!isLandscape`), the display cleanly splits into two balanced zones: Top half (~52%) presents the living Pet Robot Head Avatar with full touch gestures, props, and holographic hands; Bottom half (~48%) permanently embeds the Pet Status Dashboard with live Needs gauges, Mood badge, Care buttons, and scrollable Pet Memory stats.
>   - **Landscape Mode (`isLandscape`)**: Preserves full-screen desk companion pet face with the slide-out drawer panel toggled via "🐾 สถานะ" button or right-edge swipe.
> - **Virtual Desk Pet: Auto AI Camera Scan on Voice Intent & Circular Eye Viewfinder (2026-09-13)**:
>   - **Hands-Free Voice & AI Vision Trigger (`VoiceController.kt`, `LiveToolBridge.kt`)**: Automatically activates the camera when speaking natural phrases (e.g. *"นี่คืออะไร"*, *"ดูนี่หน่อย"*, *"ช่วยดู"*, *"เปิดกล้อง"*, *"what is this"*) or when Gemini Live invokes `vision_activate`.
>   - **Eye-Overlay Circular Viewfinder (`PetEyeScannerOverlay` in `AlwaysLiveScreen.kt`)**: Completely transforms the camera feed from a disconnected corner rectangle into an embedded cyber lens directly on the Pet's eyes in both Portrait and Landscape orientations:
>     - **Right Eye**: High-resolution hardware camera stream clipped to `CircleShape` with glowing Cyan neon bezel, animated rotating aperture ticks, vertical scanline, and AR Target Lock bounding boxes.
>     - **Left Eye**: Synchronized holographic radar scanner with 360° sweeping beam, concentric targeting rings, dynamic detection blips, and digital `[SCANNING...]` / `[🔒 LOCKED N]` status.
>   - **Procedural Cyber Radar SFX (`RobotSoundEngine.kt`)**: Real-time 16-bit PCM dual-ping frequency sweep (1400Hz $\rightarrow$ 2600Hz) with 35Hz sinusoidal FM modulation and high-tech echo decay pulse (`SCAN_RADAR`).
>   - **Smart Auto-Close Controller**: Tracks AI speaking state (`isSpeaking`). Once Gemini completes explaining what it sees, the eye waits 2.0 seconds and automatically closes back to cute pet eyes, with a 25s safety timeout.
>
>
> **Pet Mode Stability, String Format Crash & Tool Hallucination Safeguards (2026-09-12):**
> - **Java Formatter Crash Resolved (`PetVisionDetector.kt`)**: Fixed repeating `UnknownFormatConversionException: Flags = ' ('` camera frame error caused by unescaped percent symbols in detection labels (e.g. `Boss Smile 😊 85%`). Replaced format calls with safe Kotlin string interpolation throughout detection and gaze trackers.
> - **Thai Unicode Substring Trap Solved ("เปิด" vs "ปิด")**: In Thai orthography, `"เปิด"` (Sara E + Po Pla + Sara I + Do Dek) literally contains the substring `"ปิด"` (Po Pla + Sara I + Do Dek) starting at character index 1. As a result, standard `.contains("ปิด")` checks were evaluating to `true` on `"เปิดโหมดสัตว์เลี้ยง"`, misclassifying activations as shutdown requests. Sanitized `"เปิด"` prior to scanning for Thai close keywords in `LiveToolBridge.kt` and `ChatController.kt`.
> - **Gemini Tool Hallucination Guards (`LiveToolBridge.kt`, `JarvisPersona.kt`)**:
>   - *AlwaysLive Off Guard*: Blocks unprompted `device_always_live(action=off)` calls triggered by misheard greetings or names ("ดาวิด", "จาวิส"), preventing sudden screen closures and voice disconnects.
>   - *Vision Guard*: Blocks accidental `vision_activate` triggers during casual conversation, preserving device battery and LLM token quota.
>   - *Voice Profile Guard*: Protects against name-to-voice confusion when the user speaks words phonetically resembling voice names without intending a profile switch.
>
> **Always Live & Pet Mode Voice Activation Bug Fix (2026-09-12):**
> - **Accidental Disable & Session Disconnect Resolved (`DeviceControlExecutor.kt`, `JarvisViewModel.kt`)**: Fixed critical bug where speaking `"เปิดโหมดสัตว์เลี้ยง"` ("Open Pet Mode") during a Live Voice conversation caused `AlwaysLiveManager.disable()` to execute and disconnect the WebSocket/microphone. 
>   - Hardened `executeAlwaysLive()` to strictly separate explicit off commands from mode-switching/activation commands. When `isPetMode` or `isDriveMode` is specified, the system unconditionally expands into full-screen and switches to the target profile (`PET` or `DRIVE`), preventing accidental execution of `closeAlwaysLive()`.
>   - Replaced disruptive `voice.restartVoiceSession()` during profile changes with in-session `orchestrator.sendLiveRealtimeText(...)` prompt injection. Voice audio streaming, WebSocket connection, and microphone remain 100% uninterrupted while the AI persona smoothly adopts the cute pet identity.
> - **Debounced Fast-Path Execution (`VoiceController.kt`)**: Implemented a 1500ms debounce interval preventing rapid progressive transcriptions from firing duplicate local tool calls concurrently.
> - **Atomic Profile Sync (`MainActivity.kt`)**: Extended `expandAlwaysLive(targetProfile)` to immediately synchronize `alwaysLiveManager.setProfile(profile)` and notify Compose state on the UI thread prior to opening `AlwaysLiveScreen`.
>
> **Live Gemini WebSocket Resilience & State Synchronization Fix (2026-09-12):**
> - **Audio Streaming State Synchronization (`LiveGeminiService.kt`)**: Immediate detection when `webSocketSession.isActive == false` in `sendAudioChunk()` and `sendIfReady()`. Automatically sets `isSetupComplete = false` and diverts ongoing mic frames into `preReadyAudioBuffer`, preventing desynchronized `send skipped — session ไม่พร้อม` log spam during connection dropouts.
> - **Graceful Remote Socket Closure (`EOFException`) Recovery**: Enhanced classification via `isRemoteSocketCloseException()` across Kotlin Multiplatform targets. Detects remote TCP EOF and socket reset without logging false-positive error traces. Preserves reconnect retry budget (`attempt = 1`) on established sessions and resets `sessionResumptionHandle` on repeat failures to prevent reconnect loops.
>
> **Virtual Desk Pet Revolution: Jelly Physics & Clean Face, Settings Dialog, 5-Slot Face Recognition & Tamagotchi Engine (2026-09-12):**
> - **1. Jelly Physics & Clean Idle Face (Squash & Stretch, Conditional Brows, Mouth Shapes & Screensaver Eye Tricks)**:
>   - **Rubber Ball / Jelly Dynamics (`PetRobotHeadAvatar.kt`)**: Implemented squash and stretch physics (`squashX`, `squashY`) with dual specular reflections (glossy rounded pill top-left + sparkle dot bottom-right), transforming the robot head into an ultra-cute, bouncy companion.
>   - **Clean Idle State**: In standby/idle mode, eyebrows and mouth are completely hidden. Only big curious squircle neon eyes scan, explore, and blink naturally without visual clutter.
>   - **Conditional Eyebrows**: Brows are rendered ONLY in expressive emotional states (`THINKING`, `ANGRY`, `CONFUSED`, `SAD`, `LISTENING`), remaining hidden during `IDLE`, `HAPPY`, `LOVE`, `WINK`, and `SLEEPING`.
>   - **Dynamic Mouth Expressions**: Dynamic 5-bar audio waveform when speaking, cute pucker 'O' mouth (`drawPuckerMouth`) on `POUT`, expressive smile arc on `HAPPY`/`LOVE`, flat bar on `SAD`/`ANGRY`, and cleanly hidden when idle.
>   - **Screensaver Eye Tricks (`EyeTrickState`)**: After inactivity (15s/25s/45s/60s configurable delay), the pet performs playful tricks:
>     - 🏓 `PING_PONG_BOUNCE`: Eyes bounce off screen borders like a ping-pong ball.
>     - 💧 `TIRED_BOUNCE`: Eyes droop and squish heavily with jelly physics before bouncing back.
>     - 🎱 `SNOOKER_SHOT`: Left eye shoots across the screen and strikes the right eye like a billiard ball!
> - **2. Clean UI & In-App Settings Dialog (`PetSettingsDialog.kt`, `AlwaysLiveScreen.kt`)**:
>   - **Zero Clutter Debug HUD**: Status badges (`🐾`, `🎭`, `🌀`, `👀`) hidden by default behind `showDebugHud == true`.
>   - **Interactive Settings Modal (`⚙️ ตั้งค่า`)**: 3 dedicated tabs:
>     - 🛠️ **General / Debug**: Toggle Debug HUD, choose Screensaver Inactivity Delay (15s, 25s, 45s, 60s), physics & sound notes.
>     - 👤 **Face Manager**: 5 enrollment slots for family/friends with custom nicknames ("บอส", "แม่"), live enrollment from camera feed, rename, and delete.
>     - 🍖 **Tamagotchi Care**: Real-time progress bars for Satiety, Energy, Hygiene, Happiness, Stress, Affection Level (Lv 1–10), Personality Traits (Hyper vs Calm, Clingy vs Independent), and Quick Care action buttons (Feed 🍖, Clean 🧼, Play 🎾, Sleep 💤).
> - **3. 5-Slot Face Recognition & 7 Hand Gesture Detections (`PetFaceProfile.kt`, `PetVisionDetector.kt`, `PetGesture.kt`)**:
>   - **5-Slot On-Device Face Recognition**: Extracts normalized facial landmark ratios (eye distance, nose-to-mouth ratio, mouth aspect ratio, jaw contour) and matches using Euclidean distance metrics without sending biometric images to the cloud.
>   - **Personalized Greetings**: Recognizes family members by name (e.g. "สวัสดีครับคุณบอส!") driven by `JarvisPersona.kt` (Rule 12).
>   - **7 Interactive Hand Gestures & Multi-Frame Edge Latch**: On-device computer vision heuristic detector for `HIGH_FIVE`, `OK`, `BYE`, `NO`, `V_SIGN`, `THUMBS_UP`, `THUMBS_DOWN`, triggering custom robot SFX, facial expressions, and Tamagotchi affection boosts. Enhanced with 3-frame confirmation (~240ms), single-fire edge latch (prevents rapid-fire repeating while hand is held), and phone-holding / neck exclusion filters.
> - **4. Tamagotchi Psychology & Physical Needs Engine (`PetNeedsState.kt`, `PetModeController.kt`)**:
>   - Autonomous periodic decay loop simulating biological & emotional needs.
>   - Care interactions directly influence long-term relationship traits: Affection Level (1-10: Stranger $\rightarrow$ Best Friend $\rightarrow$ Soulmate), Obedience, Activity Level, and Sociability.
>   - Integrated with Gemini Live persona (Rule 13) to naturally mention pet hunger, sleepiness, or request playtime during conversations.
>
> **Pet Mode Tool-Only Dialogue Card, Auto-Decay to Dark OLED & Ambient Sound Loop Fix (2026-09-12):**
> - **Tool-Only Adaptive Dialogue Card (`AlwaysLiveScreen.kt`, `LiveToolBridge.kt`)**:
>   - In Virtual Desk Pet Mode, the dialogue card (`PetDialogueCard`) now appears **only** when a tool is executing or presenting functional results (GPS, Web Search, Trading Analysis, Device Control, Weather, etc.) or during Emotion Showcase Demos.
>   - For general everyday conversations (AI speaking, listening, standby), the dialogue card is completely hidden. The living robot face stays centered, large, and distraction-free, animating its mouth waveform in sync with voice audio.
> - **Auto-Decay to Normal IDLE (`JarvisViewModel.kt`)**:
>   - Temporary emotions, props, and dynamic backgrounds now automatically decay back to `IDLE` with `BackgroundTheme.DEFAULT` after AI finishes speaking (+2.5s cooldown).
>   - Cleared residual debug strings (`"🧪 [Face]..."`) preventing accidental persistent message states.
> - **Pure Dark OLED Normal State & Silent Ambient Engine (`PetBackgroundLayer.kt`, `AmbientSoundEngine.kt`)**:
>   - Replaced `DefaultBackground()` purple gradient and dust motes with pitch-black OLED (`Color(0xFF000000)`), eliminating all CPU animation overhead during standby.
>   - `AmbientSoundEngine` immediately halts all ambient loops on `BackgroundTheme.DEFAULT`, ensuring zero unwanted sound repetitions.
>
> **Dynamic SVG Path Parser System — Runtime Vector Prop & Magic Creator (2026-09-12):**
> - **Runtime Dynamic SVG Path Parser (`DynamicVectorProp.kt`, `DynamicPropRenderer.kt`)**:
>   - Empowers Gemini AI (Live Voice, Tool Calling, or Chat) to dynamically design, customize, and render custom vector accessories on the fly without recompiling the application.
>   - Native cross-platform parsing via `androidx.compose.ui.graphics.vector.PathParser().parsePathString().toNodes().toPath()` cached in Compose memory (`remember(prop.svgPath)`) to ensure zero performance overhead on 60/120 FPS loops.
>   - Automatic bounding box computation (`path.getBounds()`) and matrix scale normalizer (`targetSizePx / max(width, height)`) fitting any SVG coordinate system seamlessly to `sizeDp`.
>   - Supports 7 facial anchor positions (`FOREHEAD`, `LEFT_EYE`, `RIGHT_EYE`, `CHEEKS`, `CHIN`, `FLOATING_LEFT`, `FLOATING_RIGHT`) and 5 reactive animations (`FLOAT_BOB`, `PULSE`, `ROTATE_CONTINUOUS`, `SWAY`, `STATIC`).
> - **Device Tool & AI Persona Integration (`device_custom_prop`, `JarvisPersona.kt`)**:
>   - New tool declaration `device_custom_prop` with `action` (`add`, `remove`, `clear`), `name`, `svg_path`, `color`, `position`, and `animation`.
>   - Extended `PET_LIVE_SYSTEM_PROMPT` (Rule 11: Dynamic Vector Props & Magic Creator): AI can invent any custom prop (cowboy hat, diving mask, crown, angel wings, mustache, magic wand) whenever requested by the user and wear it instantly.
>
> **LOOI Robot Face Evolution ("The Phone IS the Head"), True Fullscreen & Adaptive Split-Screen Dialogue (2026-09-12):**
> - **"The Phone IS the Head" (Edge-to-Edge Pure OLED Visor Face Plate)**:
>   - Adapted design philosophy from LOOI Robot (`GrinZero/super-looi`): removed all simulated ceramic chassis, visor frames, and artificial metallic ears. The physical mobile device becomes the robot's living head.
>   - Giant squircle glowing neon eyes with ambient outer radial glow, specular top sheen, and gaze-driven 3D perspective distortion (the eye on the glance side narrows while the opposite eye expands).
>   - Dynamic tilted eyebrows responding to cognitive and emotional states (Thinking, Angry, Sad, Listening).
>   - 5-bar animated audio equalizer waveform mouth driven by live microphone and speech volume levels.
> - **True Fullscreen Immersive Mode**:
>   - Automatically hides Android Status Bar and Navigation Bar in Pet Mode (`WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
>   - Removed status bar insets in Pet Mode so dark OLED backgrounds and ambient weather effects span 100% of the display.
> - **Adaptive Split-Screen Dialogue Layout**:
>   - **Landscape Mode**: When speech or status messages arrive, the living face slides smoothly to the left (`-0.24f` offset, `0.82` scale) via physics spring animations, opening space on the right for the frosted acrylic `PetDialogueCard`.
>   - **Portrait Mode**: The living face slides up (`-0.19f` offset, `0.84` scale), presenting `PetDialogueCard` at the bottom.
>   - **Idle / Clear**: Face glides back to center at 100% scale.
>   - **Dynamic Touch Mapping**: Touch interaction coordinates (gaze tracking, forehead pet, cheek poke, tickle) follow the face's shifted center in real time.
> - **52-Prop Vector Catalog**:
>   - Expanded `PropType` to 52 procedural vector props across 4 categories (Emotions, Food & Daily Life, Weather & Nature, Tools & Tech) scaling and moving together with the face.
>
> **Pet Mode Tool Calling Unlock & GPS Nearby Places Search & Knowledge Integration (2026-09-12):**
> - **Unlocked Full Tool Access in Pet Mode (`LiveGeminiService.kt`, `ToolRegistry.kt`)**:
>   - Removed legacy `tools = null` restriction in `LiveGeminiService.kt` when `isPetMode` is true. Desk Pet Robot mode now possesses full access to all function tools (`device_location`, `search_web`, `device_navigate`, `trading_smc_analysis`, `device_avatar_emotion`, etc.).
>   - Maintained user memory context by transmitting `coreContext` in `LiveSystemInstruction` during Pet Mode.
> - **Empowered Pet Persona & System Prompt (`JarvisPersona.kt`, `LiveToolBridge.kt`)**:
>   - Updated `PET_LIVE_SYSTEM_PROMPT` (Rule 10: PET SUPERPOWERS & TOOLS CALLING):
>     - **GPS Location & Nearby Places**: Automatically queries `device_location(action="get_current", query="ร้านอาหาร")` to find restaurants, cafes, and gas stations in the current neighborhood and recommend top spots cheerfully, offering maps navigation.
>     - **Recipes & General Knowledge**: Explains cooking recipes (e.g. grilled pork marinade) directly or fetches up-to-date facts via `search_web`.
>     - **Trading & SMC Gold Analysis**: Empowers the pet robot to analyze charts/crypto/stocks/gold via `trading_smc_analysis(symbol="XAUUSD")` without refusing or claiming inability, reporting Order Blocks and support levels in its cute, playful pet voice.
>   - Cleaned up voice presentation rules in `LiveToolBridge.kt` and eliminated any spoken robot sound words ("ปิ๊บๆ").
> - **GPS Nearby Places Search with Google Grounding (`DeviceControlExecutor.kt`, `DeviceToolDefinitions.kt`, `LiveToolBridge.kt`)**:
>   - Added optional `query` parameter to `device_location` tool declaration.
>   - When `query` is provided, `DeviceControlExecutor` combines real GPS coordinates + subdistrict/district address and dispatches `NEARBY_SEARCH_REQUEST`.
>   - `LiveToolBridge` intercepts `NEARBY_SEARCH_REQUEST` and runs Google Search Grounding (`enableGrounding = true`) to fetch verified, open venues and signatures in that exact Thai neighborhood, returning grounded data to Gemini Live.
>
> **Camera FOV 1:1 Alignment (9:16 Portrait & 16:9 Landscape) & Stabilized Object Target Locking (2026-09-12):**
> - **1:1 Camera FOV & Exact Aspect Ratio Match (`AlwaysLiveScreen.kt`, `CameraPreviewView.android.kt`)**:
>   - Solved camera preview cropping by replacing hardcoded 4:3 box with dynamic orientation-aware PIP sizing:
>     - **Portrait (แนวตั้ง)**: `144.dp × 256.dp` (exact 9:16 aspect ratio).
>     - **Landscape (แนวนอน)**: `240.dp × 135.dp` (exact 16:9 aspect ratio).
>   - Applied `PreviewView.ScaleType.FIT_CENTER` with dynamic `targetRotation` matching device orientation, providing **100% identical, zero-crop field of view between what user sees in PIP and what AI receives via Gemini Live**.
>   - Fixed UI layout collision between top action controls (`[🧪 เดโม]`, `[👁️ ลืมตา]`) and status badges in portrait mode.
> - **Stabilized Target Locking & Noise Filtering Engine (`PetVisionTargetTracker.kt`)**:
>   - Cross-platform tracking and smoothing engine in `commonMain`:
>     - **Clutter & Background Noise Suppression**: Filters room walls (`Place / Scenery 🏢`) and small background items (pipes, sockets).
>     - **Head & Neck Exclusion Zone**: Prevents false object detections from overlaying user's face, mouth, or glasses.
>     - **Priority-Based Selection**: Locks onto 1) Boss Face $\rightarrow$ 2) Hand/Finger Gestures $\rightarrow$ 3) Foreground Object (capped at 3 targets max).
>     - **IOU Tracking + EMA Smoothing ($\alpha = 0.40$)**: Eliminates all bounding box jitter, jumping, and target swapping.
>     - **350ms Hysteresis Persistence**: Prevents boxes from flickering on dropped frames.
>     - **Lock Indicator**: Highlights targets locked for $\ge 3$ consecutive frames with Sci-Fi brackets, `🔒` label tags, and counter badge.
>
> **Gemini 3.1 Live Protocol Fix & 8-Scene Living Avatar Showcase System (2026-09-12):**
> - **Gemini 3.1 Live WebSocket Protocol Alignment (`LiveGeminiService.kt`)**:
>   - Resolved `Session closed: NOT_CONSISTENT — realtime_input.media_chunks is deprecated`. Google Live API requires direct `audio`, `video`, or `text` fields under `realtime_input`.
>   - Migrated audio streaming to `LiveRealtimeInputData(audio = LiveBlob("audio/pcm;rate=16000", ...))` and video streaming to `LiveRealtimeInputData(video = LiveBlob("image/jpeg", ...))`. Restored 100% stability for `gemini-3.1-flash-live-preview`.
> - **Interactive 8-Scene "ทดสอบเดโม" (Demo Showcase) System (`JarvisViewModel.kt`, `AlwaysLiveScreen.kt`)**:
>   - Complete automated walkthrough demonstrating all 8 dynamic atmospheric backgrounds, reactive body gestures, floating animated props, procedural robot SFX chirps, and continuous looping ambient audio:
>     - ☀️ **Scene 1 (Sunny)**: `SUNNY` | `JUMP` gesture | `MUSIC_NOTES`, `SPARKLES` props | `CHIRP_START` SFX | 432Hz harmonic drone + bird warbles loop
>     - 🌧️ **Scene 2 (Rainy)**: `RAINY` | `TILT_LEFT` gesture | `UMBRELLA`, `SWEAT_DROP` props | `ACKNOWLEDGE` SFX | Pink-noise rainfall + glass drops loop
>     - 🌸 **Scene 3 (Sakura)**: `SAKURA` | `WOBBLE` gesture | `SPARKLES` props | `SPARKLE` SFX | Sweeping blossom wind loop
>     - 💖 **Scene 4 (Love)**: `LOVE_BG` | `BOUNCE` gesture | `HEARTS` props | `PURR` SFX | 528Hz Solfeggio warm chord pulse loop
>     - ⚡ **Scene 5 (Thunder)**: `THUNDER` | `SHAKE` gesture | `FIRE`, `EXCLAMATION` props | `ALARM` SFX | Deep thunder rolling swell loop
>     - 🟩 **Scene 6 (Matrix)**: `MATRIX` | `TILT_RIGHT` gesture | `QUESTION_MARK` prop | `CONFUSED` SFX | 60Hz server hum + digital pulse loop
>     - 🌌 **Scene 7 (Night)**: `NIGHT` | `NOD` gesture | `ZZZZZ` prop | `CHIRP_END` SFX | 55Hz sub-bass + cricket ambiance loop
>     - 🤖 **Scene 8 (Default)**: `DEFAULT` | `IDLE` gesture | Visor clean | `CHIRP_START` SFX | Cybernetic room tone loop
>   - Accessible via **Voice** (*"ทดสอบเดโม"*, *"เดโม"*, *"demo"*), **Chat** (`/demo`, `ทดสอบเดโม`), **UI Button** (`[🧪 ทดสอบเดโม]` / `[⏹️ หยุดเดโม]`), or **AI Tool** (`device_avatar_emotion`).
>
> **Real Procedural Robot SFX & Looping Ambient Sound FX Engine (2026-09-11):**
> - **Procedural Robot Speech Cadence SFX (`RobotSoundPlayer.kt`, `RobotSoundEngine.kt`, `VoiceController.kt`)**:
>   - Completely eliminated AI speaking robotic sound words ("ปิ๊บๆ", "บี๊บๆ") as human speech text by updating `PET_LIVE_SYSTEM_PROMPT` (Rule 2: STRICT NO SPOKEN SOUND WORDS).
>   - Generates authentic hardware-synthesized sine wave PCM audio effects:
>     - `CHIRP_START`: Two-tone rising chirp (1200Hz $\rightarrow$ 1800Hz, 85ms) played on the very first incoming speech audio chunk.
>     - `CHIRP_END`: Soft falling sine trail with pitch glide (1600Hz $\rightarrow$ 900Hz, 120ms) played when the sentence speech finishes.
>     - `ACKNOWLEDGE` & `SPARKLE`: Instant responsive SFX for pet touch, face interactions, and state updates.
> - **Continuous Looping Ambient Sound FX Engine (`AmbientSoundPlayer.kt`, `AmbientSoundEngine.kt`)**:
>   - Synthesizes seamless background audio loops using Android `AudioTrack` (`MODE_STATIC` + `setLoopPoints` zero-CPU hardware looping) with 50ms circular crossfades.
>   - 8 Immersive Atmospheric Themes matching `BackgroundTheme`:
>     - `RAINY`: Filtered continuous pink-noise rainfall with random water drops.
>     - `NIGHT`: Deep sub-bass resonance (55Hz) with gentle evening crickets.
>     - `SUNNY`: Warm harmonic sine drone (432Hz) with periodic bird warbles.
>     - `SAKURA`: Soft sweeping wind breeze across spring petals.
>     - `MATRIX`: Cybernetic 60Hz server hum with digital pulses.
>     - `LOVE_BG`: Uplifting 528Hz Solfeggio warm chord pulses.
>     - `THUNDER`: Deep atmospheric low rumble with distant storm swells.
>     - `DEFAULT`: Peaceful cybernetic room tone.
> - **Dynamic Audio Ducking**:
>   - Automatically attenuates ambient sound from `0.18f` to `0.04f` during active AI speech to keep conversations crystal-clear, restoring volume smoothly when the AI stops speaking.
>
> **Layer-based Living Robot Avatar System (2026-09-11):**
> - **4-Layer Composition Architecture (`AlwaysLiveScreen.kt`)**:
>   - **Layer 0 (`PetBackgroundLayer.kt`)**: Dynamic animated backgrounds across 8 atmospheric themes (`DEFAULT`, `RAINY`, `SUNNY`, `NIGHT`, `SAKURA`, `MATRIX`, `LOVE_BG`, `THUNDER`) with particle effects and smooth `Crossfade` transitions.
>   - **Layer 1**: Dynamic ambient radial aura glow pulsing with emotion-driven color themes.
>   - **Layer 2 (`PetRobotHeadAvatar.kt` + `PetGestureAnimations.kt`)**: 3D ceramic chassis and glossy visor rendering 11 LED dot matrix expressions with natural breathing, auto-blink, and reactive body language gestures (`BOUNCE`, `JUMP`, `WOBBLE`, `SHAKE`, `NOD`, `TILT_LEFT`, `TILT_RIGHT`).
>   - **Layer 3 (`PetPropsOverlay.kt`)**: Canvas-drawn floating accessories & sticker overlay (`UMBRELLA`, `QUESTION_MARK`, `SWEAT_DROP`, `HEARTS`, `MUSIC_NOTES`, `SPARKLES`, `ZZZZZ`, `EXCLAMATION`, `FIRE`, `SNOW`) with animated pop-in and floating physics.
> - **AI-Driven State Management via JSON (`RobotFaceState.kt`)**:
>   - Serialized data models supporting full emotional styling directly from Gemini Live: `{ "emotion", "eye_style", "background", "props", "gesture", "speech_text" }`.
>   - Extended `device_avatar_emotion` tool parameters and updated `PET_LIVE_SYSTEM_PROMPT` (Rule 9) enabling the AI to naturally trigger matching backgrounds, props, and gestures during conversations.
>
> **Fix CameraX Video Encoding, AR Overlay Visibility, and Gemini Live Voice Native Audio (2026-09-11):**
> - **Eliminated Glitched Video & Vision Blindness**:
>   - Replaced manual contiguous YUV plane byte-copying with native CameraX `imageProxy.toBitmap()` (powered by Google's native libyuv) to properly handle UV plane row/pixel strides and rotation, providing crystal-clear images without green distortion or horizontal striping artifacts.
> - **Native Gemini Live Multimodal MediaChunks**:
>   - Corrected WebSocket payload schema from invalid `{ video: ... }` to official Gemini Multimodal Live `{ mediaChunks: [ { mimeType: "...", data: "..." } ] }`.
>   - Corrected voice configuration serialization `@SerialName("voiceName")`.
> - **Natural Voice Generation Without TTS Fallback**:
>   - Streamlined voice selection to use production-tested voice profiles (such as `Aoede`) coupled with on-device hardware DSP pitch shifting (`pitch = 1.28f, speed = 1.04f` with Ring Modulation), ensuring Gemini Live always generates native streaming audio in Thai and never drops to robotic Android TTS.
>   - Throttled video streaming strictly to when Camera PIP ("ดวงตาสัตว์เลี้ยง") is open to conserve network bandwidth and preserve audio response latency.
>
> **Multimodal Pet Vision & ML Kit Multi-Object / Hand Detection (2026-09-11):**
> - **Gemini Live Multimodal Video Streaming**:
>   - Streams throttled 1 FPS (~900ms) JPEG frames directly from `CameraPreviewView` to Gemini Live WebSocket (`realtimeInput.video`).
>   - Added Anti-Hallucination rule (Rule 8) to `PET_LIVE_SYSTEM_PROMPT` instructing the robot pet to observe camera frames truthfully and answer questions about visible hands, fingers, and desk items.
> - **ML Kit Multi-Object & Skin-Tone Hand / Finger Detection (`PetVisionDetector.kt`)**:
>   - Integrated `com.google.mlkit:object-detection:17.0.2` in `STREAM_MODE` for multi-object classification (food, devices, plants, accessories, packages).
>   - Built-in on-device Computer Vision Skin-Tone Clustering algorithm outside face bounds to detect:
>     - `Finger / Point ☝️` (1 finger pointing up)
>     - `Fingers / Peace ✌️` (2 fingers / peace sign)
>     - `Hand / Palm 🖐️` (open 5-finger palm)
>     - `Hand ✋` (raised hand)
>   - Distinct AR Bounding Box colors: Cyan for faces, Pink for smiles, Gold for winks, Orange for hands/fingers, and Green/Purple for objects.
> - **Voice Responsiveness & Speech Cadence Fixes**:
>   - Added explicit `languageCodes = ["th-TH", "en-US"]` in `inputAudioTranscription`.
>   - Adjusted VAD `silenceDurationMs = 1200` and `prefixPaddingMs = 300` in `LiveGeminiService.kt` to prevent premature sentence cutoffs in Thai speech cadence.
>   - Expanded `micChannel` buffer from 50 to 100 chunks in `VoiceController.kt`.
>
> **Virtual Desk Pet Vision: Live Detection HUD, Camera Eye PIP & Bounding Box Overlay (2026-09-11):**
> - **Live Detection Status HUD (`AlwaysLiveScreen.kt`)**:
>   - Displays real-time sensor & vision detection status badges at the top-left corner:
>     - 🐾 `[Touch]`: Screen touch actions (Pet head, Cheek poke, Tickle, Gaze drag, Screen tap) with bright green glow.
>     - 🎭 `[LISTENING]`: Audio mic status, speaking level %, and AI speech response state.
>     - 🌀 `[Shake]`: Accelerometer shake detection (`Shake detected! @_@`) with bright orange pulse.
>     - 👀 `[Face tracked]`: ML Kit face tracking coordinates, smile %, and wink detection with cyan/pink glow.
> - **Camera Eye PIP Preview Window ("ลืมตา / หลับตา / เปิดกล้อง / ปิดกล้อง")**:
>   - Toggle via corner button `[👁️ ลืมตา]` / `[👁️ หลับตา]` or natural voice commands ("ลืมตา", "เปิดกล้อง", "มองหน่อย", "หลับตา", "ปิดกล้อง").
>   - Floating PIP window displays what the AI sees with real-time front/back camera flipping (`🔄`) and close control (`❌`).
> - **Real-Time AR Bounding Box & Label Overlay (`AROverlayEngine.kt`, `PetVisionDetector.kt`, `PetVisionBridge.kt`)**:
>   - Renders animated pulsating bounding boxes with corner brackets around tracked faces and objects.
>   - Displays dynamic labels & color tags: Pink for smiles (`Boss Smile 😊 X%`), Gold for winks (`Boss Wink 😉`), and Cyan for normal faces (`Boss Face #ID`).
>
> **Persona & Mode Isolation Architecture (2026-09-11):**
> - **Clean Mode Separation & Zero Persona Bleed**:
>   - Completely isolated Gemini Live WebSocket setup between **Assistant / Control Mode** (`AlwaysLiveProfile.CONTROL`) and **Virtual Desk Pet Mode** (`AlwaysLiveProfile.PET`).
>   - Reset session resumption tokens (`sessionResumptionHandle = null`) across mode transitions so Google Gemini Live starts fresh without retaining prior system instructions or turn history.
>   - Disabled native tools (`tools = null`) in Pet Mode to eliminate unwanted background trading actions or spontaneous UI mode transitions.
>   - Isolated history snapshots: stripped mode-switching commands and passed an empty history in Pet Mode.
>   - Automatically re-established WebSocket connections on profile switch to apply the exact voice model (`Puck` vs configured assistant voice), system instructions, and audio DSP configurations.
>
> **Dual-Layer Robot Voice Engine, DSP Filter & Desk Pet Persona (2026-09-11):**
> - **Dual-Layer Robot Voice Engine (`PcmAudioEngine.android.kt`, `VoiceController.kt`, `JarvisPersona.kt`)**:
>   - **Layer 1: Real-Time Hardware & DSP Audio Pipeline**:
>     - Dynamic `PlaybackParams`: When in Pet Mode (`isRobotVoiceEnabled = true`), dynamically shifts hardware audio playback to cute robot pet pitch (`pitch = 1.28f`, `speed = 1.04f`).
>     - Real-Time 16-Bit PCM DSP Filter (`applyRobotDsp`): 72Hz Ring Modulation (metallic synthesizer timbre) + 48-sample (~500Hz) Comb Filter (chassis acoustic resonance) + Soft Analog Saturation.
>     - Opening Robot Chirp: Automatically plays a cheerful procedural robot chirp sound effect (`RobotSoundPlayer.playHappy()`) right as AI begins each speech response.
>   - **Layer 2: Virtual Desk Pet Persona & Prompt Switching**:
>     - Automatic Prompt Switch: Switches to `PET_LIVE_SYSTEM_PROMPT` in Pet Mode — a cute, playful desktop companion robot that speaks in short (1-2 sentences), sweet Thai phrases, uses robot sound words ("ปิ๊บๆ!", "บี๊บๆ!", "งุ้ยย~", "แง้วว~"), and avoids adult formal tone or stock market analysis.
>     - Playful Prebuilt Voice: Uses Gemini Live's energetic and cheerful `Puck` voice profile.
>     - On-Device TTS Fallback: Offline TTS pitch dynamically elevated to `1.35f` with `1.15f` speed in Pet Mode.
>
> **Virtual Desk Pet Mode, Procedural Robot Sound FX & Motion Gestures (2026-09-11):**
> - **Virtual Desk Pet Mode (`AlwaysLiveProfile.PET`, `PetModeController.kt`)**:
>   - Inspired by LOOI Robot: Added an engaging "โหมดสัตว์เลี้ยง" (Virtual Desk Pet) profile accessible via Always Live screen alongside Control and Drive modes.
>   - Features 4 sub-feature tabs:
>     - `🐾 เล่น` (Play & Touch): Direct tactile touch interactions (pet head, poke cheek, tickle, wake up).
>     - `🛡️ เฝ้าโต๊ะ` (Desk Sentry): Autonomous desktop surveillance detecting intruders and sounding alarms.
>     - `⏱️ โฟกัส` (Focus Buddy): Pomodoro timer (25m / 5m / 50m) encouraging user productivity with cheerful robot sound effects.
>     - `🎲 เซียมซี` (Fortune Oracle): Playful fortune teller drawing random daily insights and uplifting quotes.
> - **Procedural Robot Sound FX (`RobotSoundEngine.kt`, `RobotSoundPlayer.kt`)**:
>   - 100% on-device mathematical sound wave synthesis (16-bit PCM AudioTrack) with zero asset bundle weight.
>   - 8 procedural robot sound effects: Happy Chirp, Purr, Surprise, Confused, Alarm Siren, Yawn, Giggle, and Wake Up.
> - **Interactive Touch, Gaze Tracking & Motion Sensors (`JarvisAvatar.kt`, `PetMotionDetector.kt`)**:
>   - Expressive Avatar updates: dynamic gaze follow on touch/drag, lifelike idle gaze wander, yawning after 60s inactivity, deep sleep after 150s.
>   - Tap to poke (smile), double-tap to tickle (giggle), long-press to pet head (purr & love hearts).
>   - Accelerometer shake detection (> 2.2G) triggers dizzy swirl eyes (`@_@`) and confused chirp.
>   - Face-down desk flip triggers sleep (Zzz) and waking up when lifted.
> - **100% Free & Zero Token Cost**:
>   - All pet behaviors, gaze wandering, touches, and sensor triggers run entirely on-device with zero Gemini Live tokens used.
>
> **Trading Signal Anticipation 13-Factor Default, Market Feature Snapshot & Historical Audit (2026-09-11):**
> - **13-Factor Anticipation Default (`AnticipationConfigManager.kt`)**:
>   - Upgraded Anticipation engine to enable **all 13 curated whitelist factors as default** for every asset (Keyzone, Wick Sweep, RSI Extreme, EMA Convergence, Bollinger Squeeze, MACD Turn, Volume Absorption, Fibonacci Golden Pocket, Stoch Turn, Session Sweep, Veyra Shift, BB KC Squeeze, Fast RSI Reversal).
> - **25+ Candlestick & Market Genome Feature Snapshot (`SignalAlertProvider.kt`, `SignalOutcomeTracker.kt`)**:
>   - When an Anticipation alert triggers, captures a comprehensive 25-dimensional market snapshot (`SignalFeatureExtractor`) alongside Anticipation context (`setup_type`, `factor_id`, `confidence`, `stage`, `reason`, `zone`) into SQLite `SignalTrackingRecord.features_json`.
> - **Anticipation Inspection & Audit Tool (`trading_signal_anticipation`)**:
>   - Added `action="inspect"` / `"history"` / `"records"` with optional `limit` parameter to `trading_signal_anticipation`, allowing users and AI to inspect past anticipation triggers, execution rails (Entry, SL, TP), outcome R, and underlying market regime features (H4/H1 trend, Squeeze, Veyra score, RSI, Fast RSI, ADX, ATR) to verify rationale and validity.
> - **SQLite Schema Migration (`DatabaseDriverFactory.kt`, `11.sqm`)**:
>   - Fixed `table SignalTrackingRecord has no column named features_json` SQLITE_ERROR on existing user databases by adding additive `ALTER TABLE SignalTrackingRecord ADD COLUMN features_json TEXT` migration and updating schema definition.
> - **Dataset Export & Bollinger Squeeze Accuracy**:
>   - Updated `SignalDatasetManager.exportDataset()` to support exporting anticipation records (`strategy="anticipation"`), and refined Bollinger Squeeze band tolerance to eliminate false breakout signals during flat consolidation.
> **Real-Time Intra-Bar Live Bar Stitching & 1-Minute Anticipation Radar Observability (2026-09-11):**
> - **Intra-Bar Live Bar Stitching (`SignalAlertProvider.kt`, `SmcApiService.kt`)**:
>   - Solved the higher-timeframe frozen bar issue where 15m/1h candles from cache remained static during minutes 1–14 of the bar.
>   - Implemented `stitchLiveBar(candles, m1Candles, tf)` which continuously synthesizes the latest 1m candles into the live forming candle (`liveIdx = n - 1`), updating real-time High, Low, Close, and Volume every 60 seconds.
>   - Enables all 13 Anticipation factors (Wick Sweep Rejection, Keyzone Proximity, RSI Extreme, EMA Convergence, etc.) to evaluate actual intra-bar market dynamics in real time.
>   - Preserves strict separation between Confirmed Signals (which evaluate strictly on closed bar `sigIdx = n - 2` to prevent repaint) and Anticipation (which evaluates on the live forming bar `liveIdx = n - 1` to alert before the bar closes).
> - **Anticipation Radar Heartbeat & Observability (`SignalAlertProvider.kt`)**:
>   - Added minute-by-minute heartbeat logging: `📡 $symbol/$tf Anticipation radar: live=... (13 factors active) → IDLE` confirming the active 1-minute scanning cycle.
>   - Instant `⚡ $symbol/$tf ANTICIPATION RADAR: live=...` logs when a setup triggers.
>   - Made `SmcApiService.intervalToMillis` a public companion function and optimized `SignalAlertProvider.fetch()` to reuse 1m candles across both live stitching and Unified SMC multi-TF confirmation.
>
> **Virtual Desk Pet (โหมดสัตว์เลี้ยง) & Drive/Control Mode Clean Separation (2026-09-11):**
> - **Clean Mode Separation (แยก 2 โหมดอิสระ ชัดเจน ไม่สับสน)**:
>   - **โหมดขับขี่ / โหมดควบคุม (Drive & Control Mode)**: รวมเป็นโหมดเดียวกัน (`AlwaysLiveProfile.CONTROL`) แสดงผลด้วย **3D Pearlescent Clay Robot Avatar** (`JarvisAvatar`) พร้อมเครื่องมือ Live เต็มรูปแบบ ควบคุม Google Maps, YouTube, รับสาย, สั่งงานเครื่อง Hands-Free บุคลิกและเสียงตาม Persona ที่ผู้ใช้ตั้งไว้ ไม่มีเสียงร้องเจื้อยแจ้วและไม่มี Gesture สัมผัสกวนใจ
>   - **โหมดสัตว์เลี้ยงตั้งโต๊ะ (Virtual Desk Pet)**: โหมดสัตว์เลี้ยงตัวจริง (`AlwaysLiveProfile.PET`) แสดงผลด้วย **Fullscreen Living Robot Head Avatar** (`PetRobotHeadAvatar`) เฉพาะส่วนหัวหุ่นยนต์มินิมอลสุดน่ารัก ดีไซน์พรีเมียมตัวเรือนเซรามิกขาวเงา หูโลหะ Slate-Blue กระจกหน้ากากโค้งดำเงาพร้อมแถบสะท้อนแสง Arc สะท้อนความมันวาว และหน้าจอเรืองแสง Digital Pixel Dot Matrix (11 อารมณ์)
> - **Zero Button Clutter Philosophy**:
>   - ตัดปุ่มควบคุม แผงแท็บ ชิป และปุ่มกดยิบย่อยทั้งหมดออกจากหน้าจอโหมดสัตว์เลี้ยง คงเหลือไว้เพียงหัวหุ่นยนต์มีชีวิตที่ตอบสนองแบบ 100% Autonomous ผ่านการสัมผัส เซนเซอร์ และกล้อง AI
> - **Orientation-Aware Touch Gestures (Portrait & Landscape)**:
>   - ลูบหน้าผากลง (Forehead Swipe Down) $\rightarrow$ หัวใจขึ้นตา (LOVE) + ส่งเสียงครางเพลิน (Purr)
>   - เกาคางขึ้น (Chin Scratch Up) $\rightarrow$ ตาหัวใจ (LOVE) + เสียง Purr
>   - จิ้มแก้ม (Cheek Poke) $\rightarrow$ ร้องส่งเสียงทักทายสดใส (Happy Chirp)
>   - จิ้มสองครั้งที่แก้ม (Cheek Double-Tap / Tickle) $\rightarrow$ หัวเราะชอบใจ (Giggle) + ตาหยีขยับสั่น
>   - ลากนิ้วบนจอ $\rightarrow$ ตาสัตว์เลี้ยงขยับกลอกตามตำแหน่งนิ้วแบบ Real-time
> - **Motion Sensor Physical Reactions (Accelerometer/Gyroscope)**:
>   - เขย่าเครื่อง (Shake Detection) $\rightarrow$ ตาลายเวียนหัวหมุนวนรูปก้นหอย (`@_@`, DIZZY) + ส่งเสียงสับสนมึนงง
>   - คว่ำหน้าจอบนโต๊ะ (Desk Face-Down) $\rightarrow$ เข้าสู่โหมดหลับพักผ่อน (SLEEPING) พร้อมตัวหนังสือ `z z z` ลอย และเสียงกรนฟี้ๆ (Procedural Snore)
>   - หงายหน้าจอขึ้น (Desk Face-Up) $\rightarrow$ ส่งเสียงกระดิ่งปลุกตื่น (Wake-Up) พร้อมยืดเส้นยืดสายสดใส
> - **On-Device Vision (Google ML Kit Face Detection — 0 Token Cost)**:
>   - **Gaze Tracking**: ตาสัตว์เลี้ยงมองตามตำแหน่งใบหน้าจริงของผู้ใช้หน้าโต๊ะทำงาน
>   - **Desk Sentry**: สายตรวจเฝ้าโต๊ะ ส่งเสียงไซเรนเตือนภัยพร้อมตาแดงดุเมื่อมีคนเดินเข้ามาหน้าโต๊ะ
>   - **Copycat Face Mimic Game**: ท้าทายผู้ใช้ยิ้มกว้าง หรือขยิบตาข้างเดียว แข่งกับน้อง ตรวจจับผ่าน ML Kit Classification และเฉลิมฉลองเมื่อทำสำเร็จ
> - **Procedural Robot Sound FX Synthesizer (`RobotSoundEngine.kt`)**:
>   - สังเคราะห์เสียงเอฟเฟกต์หุ่นยนต์ระดับมิลลิวินาที (Happy, Purr, Surprise, Confused, Alarm, Yawn, Giggle, Wake-Up, และ Snore) ด้วย 16-bit PCM AudioTrack โดยไม่ต้องโหลดไฟล์เสียง
>
> **Driving Mode Enhancement: Smart Notifications, GPS Context & Media Control (2026-09-11):**
> - **Smart Notifications & Quick Reply (`JarvisNotificationListener`, `NotificationBridge`)**:
>   - Implemented Android `NotificationListenerService` (`JarvisNotificationListener.kt`) to capture incoming messages from LINE, SMS, WhatsApp, Messenger, Telegram, and Discord.
>   - Added hands-free Voice Announcements: When in Always Live / Driving Mode (`device_always_live`), JARVIS automatically announces incoming messages out loud ("มีข้อความใหม่ใน LINE จากคุณ... ว่า...").
>   - Added `device_notification_read` tool: Read latest messages on-demand with optional app filter.
>   - Added `device_notification_reply` tool: Send text replies via Android `RemoteInput` without opening the messaging app, with voice fast-path triggers ("ตอบว่า...", "ตอบไลน์ว่า...").
> - **GPS Location & Navigation Context (`LocationProvider.kt`, `device_location`)**:
>   - Built zero-dependency location provider using Android standard `LocationManager` and `Geocoder` with reverse-geocoding (subdistrict, district, province).
>   - Added `device_location` tool (`action="get_current"|"status"`) for AI location grounding during driving and navigation.
>   - Added fine and coarse location permissions with toggles in Setup Checklist.
> - **Enhanced Media Control & Now Playing (`MediaInfoProvider.kt`, `device_media_control`)**:
>   - Upgraded `device_media_control` with `now_playing` to read live track metadata (title, artist, album, player status) via `MediaSessionManager`.
>   - Added `search_play` action with multi-app search intents for YouTube, YouTube Music (`com.google.android.apps.youtube.music`), and Spotify (`spotify:search:...`).
>   - Added targeted transport controls (`play`, `pause`, `next`, `prev`) routed directly to active media sessions before falling back to hardware key events.
>
> **Physical Grounding & 3D Avatar Emotion Voice Control Integration (2026-09-11):**
> - **Avatar Physical Grounding (`JarvisPersona.kt`)**: Imbued JARVIS with self-awareness of its on-screen 3D Pearlescent Clay Robot Avatar body (sky-blue headphones, digital cyan eyes, expressive mouth, 10 emotional states), eliminating AI self-denial responses like "ฉันเป็น AI ไม่มีหน้าตา".
> - **Avatar Emotion Device Tool (`device_avatar_emotion`)**: Added native function declaration in `DeviceToolDefinitions.kt` (`action="demo"|"set"|"reset"`, `emotion="..."`) routed via `DeviceControlExecutor.kt` and `MainActivity.triggerTestEmotion`.
> - **Semantic Disambiguation & Interception Guard (`LiveToolBridge.kt`)**: Fixed Thai language semantic collision where "อารมณ์" (emotion vs market sentiment) caused Gemini Live to mistakenly invoke `trading_fear_greed` or `trading_sentiment` when asked to demonstrate avatar facial expressions. Intercepts and redirects calls to `device_avatar_emotion`.
> - **Zero-Latency Local Fast-Path (`VoiceController.kt`, `ChatController.kt`)**: Instant local UI facial expression triggering on user speech recognition ("เดโม่อารมณ์", "แสดงอารมณ์ทั้งหมด", "ทำหน้าดีใจหน่อย") before network roundtrips.
> - **Always Live Control & Driving Mode Triggers (`device_always_live`)**: Added native tool mapping for "โหมดควบคุม", "โหมดขับขี่", and "โหมดรถยนต์" across speech, text chat, persona rules, and Live WebSocket bridge with optional mode parameter (`mode="control"|"drive"|"car"`).
>
> **Trading Intelligence Decoupling, 25+ Feature Snapshot & AI/ML Dataset Tools (2026-09-10):**
> - **Clean Decoupling (แยก 2 ระบบชัดเจน)**:
>   - **Market Radar (Anticipation)**: เฝ้าระวังภาพรวมตลาดล่วงหน้า (M15, H1, H4) ผ่าน 13 ปัจจัยมาตรฐาน โดยไม่มีการเปิด mock/dummy trade order ใดๆ ใน Trade Tracker
>   - **Signal Alert Engine**: ตรวจจับสัญญาณจุดเข้าทำกำไรจริง ครอบคลุม 8 กลยุทธ์ Classic + Unified SMC + 3 Pine Script Engines (`VEYRA`, `BBSQ`, `FRSI`) พร้อมรองรับ Multi-timeframe (M5, M15, M30, H1, H4)
> - **25+ Candlestick & Market Feature Snapshot (`SignalFeatureExtractor.kt`)**: บันทึกภาพถ่ายลักษณะตลาดและแท่งเทียนกว่า 25 ตัวแปร (Body/Wick ratio, Volume impulse, RSI14, Fast RSI5, Stoch, MACD, EMA spread, ADX, ATR, BB/KC Squeeze, MTF Trend, Keyzone, Veyra score) ลงใน SQLite (`SignalTrackingRecord.features_json`)
> - **Forward Paper Trading Simulation (`SignalOutcomeTracker.kt`)**: ติดตามการวิ่งจริงของกราฟไปข้างหน้า บันทึก Win, Loss, MFE, MAE, R-multiple, และ Bars Held
> - **AI/ML Dataset Tools**:
>   - `trading_signal_data_export`: ส่งออก Dataset สัญญาณพร้อม Features ในรูปแบบ JSON / CSV สำหรับ AI ภายนอกหรือ ML Model นำไปค้นหาความสัมพันธ์และจูนพารามิเตอร์
>   - `trading_signal_config_import`: นำเข้าค่าพารามิเตอร์กลยุทธ์ที่ผ่านการวิเคราะห์/ปรับปรุงแล้วกลับสู่ SQLite (`StrategyTuning`, `EntryTuning`)
>
> **Trading Analysis Closed-Loop Flow: Detection ➔ Analysis ➔ Alerting ➔ Learning & Pine Script Porting (2026-09-10):**
> - **Pine Script Institutional Strategy Porting (`composeApp/.../strategy`)**:
>   - **`VeyraShiftEngine.kt`** (*Veyra Shift Ledger*): 6 Institutional pillars (Trend/Regime 21/55/200, Signed Pressure Oscillator, VWAP Auction Value Dev, Structure BOS/Sweep/FVG, Volatility Compression/Expansion, HTF Filter), Shift Score (0-100), and Execution Rails (Entry, SL, TP1: 1.0R, TP2: 2.0R, TP3: 3.2R).
>   - **`BBSqueezeTrendEngine.kt`** (*BBSqueezeTrend*): Bollinger Bands (29, 1.82) vs Keltner Channels (29, 1.56) Squeeze On / Squeeze Fired + LinReg Slope (11) + ADX (14) >= 19.11 + Dynamic Execution Rails.
>   - **`FastRsiEngine.kt`** (*ABQ1*): Fast RSI(5) momentum thrust crossover 35/75 with early exit triggers (< 10).
> - **Closed-Loop 4-Stage Workflow (`trading_signal_anticipation`)**:
>   - **ตรวจจับ (Detection)**: Instant on-demand scanning via `action="scan"` / `"analyze"` & 24/7 background detection in `SignalAlertProvider.detectAnticipation()` with 13-factor curated whitelist.
>   - **วิเคราะห์ (Analysis)**: Readiness stages (`PRE_SETUP`, `TRIGGER_READY`, `CONFIRMING`) + AI Strategy Supervisor (`runAnticipationSupervisor()`) in `TradingAlertEvaluator.kt`.
>   - **แจ้งเตือน (Alerting)**: Enriched `buildAnticipationChatCard()` with Stage badge and Execution Rails table (Entry, SL, TP1, TP2, TP3) + synthesized voice alert (`buildAnticipationSpeech()`).
>   - **เรียนรู้ (Learning)**: Persistent tracking in SQLite via `SignalOutcomeTracker.kt` (`recordAnticipation()`, `evaluateOpenSignals()`), dynamic factor reinforcement learning (+2% WIN / -2% LOSS confidence adjustments), and performance reporting (`action="learning"` / `"performance"`).
>
> **3D Robot Clay Avatar, Dedicated Logcat & Anti-Flapping Audio Engine (2026-09-10):**
> - **3D Pearlescent Clay Robot Avatar (`JarvisAvatar.kt`)**: Re-sculpted in Jetpack Compose Canvas with radial highlights, soft depth shadows, sky-blue 3D headphone earcups (`#29B6F6`), and 10 animated facial expressions. Removed obsolete halo ring and visor scanlines.
> - **Anti-Flapping Speech Hysteresis (`App.kt`, `VoiceController.kt`)**: 850ms audio chunk hangover and 700ms user speech hold window eliminate status pill and ambient color fluttering between "Speaking" and "Listening".
> - **Dedicated Logcat Tag `JarvisAvatar`**: Filter live state transitions directly via `adb logcat -s JarvisAvatar`.
> - **10-Emotion Multi-Channel Testing**: Test all 10 expressions & color palettes via chat (`/avatar demo`, `/avatar <emotion>`), voice triggers ("ทำหน้าดีใจ", "เดโม่อารมณ์"), or ADB broadcast (`adb shell am broadcast -a com.skyliner2008.jarvis.TEST_EMOTION --es emotion "HAPPY"`).
> - **God Service Decomposition & Production Namespace**: Split monolithic 150KB `JarvisAutomationService.kt` into 4 decoupled components and migrated codebase to `com.skyliner2008.jarvis`.
>
> **Gemini 3.1 Flash Live Primary Model & Automatic Model Switch Fix (2026-09-09):**
> - **Primary Default Live Model (`gemini-3.1-flash-live-preview`)**:
>   - Established `gemini-3.1-flash-live-preview` as the primary default Live model (`DEFAULT_LIVE_MODEL` and index 0 in `SEED_LIVE_MODELS` and `liveCandidates`). It delivers the lowest latency (~835ms READY), natural Thai prosody, and the most reliable native tool calling (`device_always_live`, `trading_smc_analysis`).
> - **Root-Cause Resolution of Spontaneous Model Switching**:
>   - **Removed from `deprecatedLiveModels`**: Removed `gemini-3.1-flash-live-preview` from `deprecatedLiveModels` in `SettingsController.kt`. Previously, cold-start boot mistakenly detected it as deprecated and overwrote SQLite with the older `09-2025` fallback.
>   - **Prevented Silent SQLite Overwrite on Transient Fallback**: Removed `settings.updateLiveModelSilently(winningModel)` from `JarvisViewModel.kt`. Temporary runtime fallbacks during brief network hiccups no longer permanently overwrite the user's manual setting in the database.
>   - **Clean Session Start**: Configured `LiveGeminiService.kt` to always reset `liveModelName` to the user's configured model at the start of each user-initiated conversation, preventing fallback models from sticking across sessions.
>   - **Expanded Setup Watchdog & Reduced Penalty**: Increased `setupWatchdog` delay from 3500ms to 6000ms to accommodate mobile data handshake latencies without premature timeouts, and reduced `penalizeLiveModel` duration from 15 minutes to 60 seconds.
>
> **Keyboard IME AdjustResize & Inset Fix (2026-09-09):**
> - **Enforce `adjustResize` in `AndroidManifest.xml`**:
>   - Declared `android:windowSoftInputMode="adjustResize"` on `MainActivity`, preventing OEM ROMs (e.g. Huawei/Honor EMUI/MagicOS, Xiaomi) using 3-button navigation from falling back to `adjustPan`. Eliminates the severe bug where tapping the text input panned the entire window off-screen to the top status bar.
> - **Consolidated Additive Insets in `ChatInputBar.kt`**:
>   - Replaced stacked `.navigationBarsPadding().imePadding()` with `windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))`, calculating `max(navigationBars, ime)` to ensure the input bar rests cleanly right above the soft keyboard without giant black voids or double-insets.
>
> **Screen Wakeup Lifecycle, Keyguard Overlay & Normal Mode Auto-Sleep Fix (2026-09-08):**
> - **Elimination of Lock Screen Keyguard Overlay (`AndroidManifest.xml`)**:
>   - Removed static `android:showWhenLocked="true"` and `android:turnScreenOn="true"` from `MainActivity` in `AndroidManifest.xml`. When the phone sleeps in normal mode and wakes, Android displays the standard lock screen with PIN/fingerprint instead of trapping the user in the app above the keyguard.
> - **Gated Temporary Wake Flags (`AlwaysLiveManager.kt`)**:
>   - Gated `turnScreenOnTemporarily()` in `wakeScreen()` behind `_state.value == AlwaysLiveState.FULL_SCREEN`. In normal mode, screen wake for trade alerts uses only the 10-second `WakeLock` without permanently applying `FLAG_SHOW_WHEN_LOCKED` to `MainActivity`.
> - **Window Flags Lifecycle Cleanup (`MainActivity.kt`)**:
>   - Added `clearScreenFlags()` in `onCreate()`, `onResume()`, and `onStop()` whenever in normal mode (`AlwaysLiveState.OFF`), ensuring `setShowWhenLocked(false)`, `setTurnScreenOn(false)`, and window flags are actively cleared.
> - **Screen Sleep Only in Control Mode**:
>   - Strictly enforced that the screen only stays awake when in active Control Mode (`AlwaysLiveState.FULL_SCREEN` or `MINI_FLOATING`). In normal chat mode, the screen sleeps normally based on Android system display timeout.
>
> **Voice Alert Delivery & Anticipation Alert UI (2026-09-08):**
> - **Default-Enabled Voice Alerts**:
>   - Converted `alert_voice` default from `false` to `true` across `AlertController.kt`, `JarvisAutomationService.kt`, and SQLite `AppSetting` fallback, ensuring voice alerts trigger out of the box.
> - **Anticipation Alert Card UI Refinement (`MessageBubble.kt`)**:
>   - Clean Line 2 for Confidence & Price (`ความเชื่อมั่น 76%` • `ราคา 4405.06`) and removed redundant zone strings from headers.
> - **Background Screen Wakeup & CPU WakeLock**:
>   - Automatic screen wakeup (`AlwaysLiveManager.wakeScreen()`) upon alert firing so users see and hear notifications even when locked.
>   - Temporary 30-second `PARTIAL_WAKE_LOCK` prevents CPU Doze suspension during audio synthesis and playback.
>
> **Dynamic Gemini Model Registry & Self-Healing (2026-09-07):**
> - **Zero Hardcoded Model Lock-in**: Dynamic registry (`ModelConfig.kt`) synchronized with Google's API (`ModelService.ListModels`).
> - **Self-Healing on 404 NOT_FOUND**: Instantly blacklists dead/deprecated models upon receiving HTTP 404, excises them from active fallback chains, and auto-migrates database preferences to healthy models.
>
> **Always AI Live Mode — 3D Robot Avatar, Mini Floating Overlay & Wake-on-Voice (2026-09-06):**
> - **Full-Screen Live Mode (`AlwaysLiveScreen.kt`)**: Full-screen ambient AI companion with 3D-styled animated robot avatar (`JarvisAvatar.kt`) and 36-bar circular audio visualizer ring.
> - **Mini Floating Robot Overlay (`FloatingWidgetService.kt`)**: Animated mini robot avatar (~80dp) running Jetpack Compose inside a Foreground Service with touch drag gestures, physics snap-to-edge, and quick voice toggle.
> - **Background Wake-on-Call / Hotword Engine (`HotwordDetector.kt`)**: Energy-efficient voice detection using AudioRecord with duty-cycle sampling (2s listen, 1s sleep).
> - **Central State Machine (`AlwaysLiveManager.kt`)**: Coordinates states (`OFF`, `FULL_SCREEN`, `MINI_FLOATING`, `BACKGROUND_LISTEN`), auto-recovers on screen on/off, and maps real-time speech sentiment to 10 avatar emotion states.
>
> **JARVIS Full Mobile Device Control via Voice & Accessibility Service (2026-09-06):**
> - **Hands-Free Full Mobile Automation**: Operate other apps and hardware via real-time voice commands (Gemini Live) and text chat.
> - **18 Native Tools in `📱 Device Control` Category (`DeviceToolDefinitions.kt`)**:
>   - Hardware: `device_flashlight`, `device_volume`, `device_brightness`, `device_media_control`, `device_always_live` (โหมดควบคุม).
>   - App & Navigation: `device_open_app`, `device_navigate`, `device_send_email`, `device_add_calendar`, `device_make_call`, `device_send_sms`, `device_set_alarm`, `device_open_url`, `device_search_web`.
>   - Screen & UI Automation: `device_read_screen`, `device_tap`, `device_type_text`, `device_scroll`, `device_press_button` (Back, Home, Recents, Lock Screen, Wake Screen), `device_get_app_info`.
>   - System Status: `device_battery_status`, `device_wifi_status`.
>
> **Dedicated Signal Anticipation Tool & Curated 10-Factor Confluence Engine (2026-09-05):**
> - Native tool `trading_signal_anticipation` with 10-factor whitelist (`KEYZONE_PROXIMITY`, `WICK_SWEEP_REJECTION`, `RSI_EXTREME`, `EMA_NEAR_CROSS`, `BOLLINGER_SQUEEZE`, `MACD_HISTOGRAM_TURN`, `VOLUME_ABSORPTION`, `FIBONACCI_GOLDEN_POCKET`, `STOCHASTIC_OVERSOLD_TURN`, `SESSION_OPEN_SWEEP`).
> - Dual-stage signal engine (`ANTICIPATION` → `CONFIRMED`) and closed-loop outcome evaluation (`SignalOutcomeTracker.kt`) feeding back into `StrategyConfirmationGate.kt`.

---

**JARVIS** (PersonalAIBot) คือระบบผู้ช่วย AI ส่วนบุคคลระดับสูง (Personal AI Assistant) ที่ออกแบบมาเพื่อเป็นทั้งเพื่อนคู่คิดและนักวิเคราะห์การเงินอัจฉริยะ ขับเคลื่อนด้วย **Google Gemini 3 Series (Free-Tier Optimized)** + ระบบ **Multi-Provider Fallback**, ความจำ 6 ชั้น (GraphRAG & Obsidian Wiki), ระบบเฝ้าติดตามตลาดอัตโนมัติ (Alert System V2), ระบบควบคุมเครื่องมือถือด้วยเสียง (**Device Control & Always AI Live**) และระบบ **MT5 Full Agent Control** สำหรับเทรดแบบครบวงจร

- **🔌 Multi-Provider + Fallback หลายชั้น** — Gemini (Multi API Key + Model Fallback Chain) → Groq → OpenRouter → MiniMax สลับอัตโนมัติเมื่อติด limit พร้อม Auto-Test คัดเฉพาะโมเดลที่ใช้ tool ได้จริง
- **🎙️ Live Voice + Vision** — คุยสดกับ Gemini Live (`gemini-3.1-flash-live-preview`), เลือกเสียงได้ 30 โปรไฟล์ (ผูกตัวตน/คำลงท้ายอัตโนมัติ), เปิด "ตา" ให้ AI มองผ่านกล้องพร้อม AR Overlay
- **📱 Always AI Live & Device Control** — โหมดควบคุมเครื่องเต็มรูปแบบ สั่งเปิดแอพ, นำทาง Google Maps, ปรับเสียง/ความสว่าง, พักหน้าจอ/ปลุกหน้าจอ, อ่านหน้าจอ และแตะปุ่มอัตโนมัติผ่าน Accessibility Service
- **🔔 Alert System V2 & ระบบปลุก AI** — Signal Alert (สัญญาณยืนยันจากกลยุทธ์) และระบบปลุก AI (คาดการณ์ล่วงหน้า ~115 ปัจจัย → AI วิเคราะห์ 5TF เอง) ด้วยการ์ด 3D พร้อมเสียงพูดแจ้งเตือน
- **💾 Backup & Restore** — สำรอง/กู้คืนการเรียนรู้และฐานข้อมูลทั้งหมด เก็บในเครื่องหรือ Google Drive
- **📲 Mobile Android App (Compose Multiplatform)** — ดีไซน์พรีเมียม, แนบไฟล์/รูป/PDF ในแชทให้ AI วิเคราะห์, สร้างไฟล์ Excel จริง, Symbol Catalogue, Decision Feed และ Auto Trading Controls

---

## 🌟 ระบบหลัก (Core Systems)

### 🎙️ 1. Live Voice & Voice Profile ↔ Identity
- **Gemini Live API** — สนทนาสดด้วยเสียง (PCM 16kHz) latency ต่ำมาก (~835ms) ด้วยโมเดลหลัก `gemini-3.1-flash-live-preview` รองรับการขัดจังหวะ (barge-in) และ flush คิวเสียงอัตโนมัติ
- **30 Voice Profiles** — เสียงหญิง/ชาย หลายน้ำเสียงและอารมณ์ เลือกได้จาก Settings หรือสั่งด้วยเสียง ("เปลี่ยนเสียงเป็น Leda") — เปลี่ยนแล้ว AI ทักทายยืนยันด้วยเสียงใหม่ทันที
- **เสียงผูกกับตัวตน** — เสียงหญิงพูดลงท้าย "ค่ะ" / เสียงชาย "ครับ" อัตโนมัติ จำข้าม session ผ่าน Core Memory
- **Vision (ตาของ JARVIS)** — เปิดกล้องให้ AI มองโลกจริง พูดสรุปสิ่งที่เห็นทันทีโดยไม่ต้องถามซ้ำ, OCR อ่านข้อความ, Object Detection (AR Overlay), สลับ vision provider ตามโมเดลหลักอัตโนมัติ
- **Live Tool Bridge** — โมเดล Live สั่ง tool ผ่าน native function calling ตรง และประสานงานกับเครื่องมือภายนอกได้อย่างแม่นยำ

### 📱 2. Always AI Live Mode & Device Control (โหมดควบคุม)
- **Full-Screen Live Mode (`AlwaysLiveScreen.kt`)** — อวตารหุ่นยนต์ 3D เคลื่อนไหวตามอารมณ์ 10 สถานะ พร้อมวงแหวน Audio Visualizer 36 แท่ง สลับกล้องหน้า/หลัง และย่อเป็นมินิวิจเจ็ตได้
- **Mini Floating Robot Overlay (`FloatingWidgetService.kt`)** — อวตารมินิ (~80dp) ลอยบนหน้าจอทุกแอป แตะลากย้ายตำแหน่งอิสระ ดูดติดขอบจอ และแตะสองครั้งเพื่อขยายเต็มจอ
- **Hands-Free Full Mobile Automation** — รองรับคำสั่งเสียงควบคุมฮาร์ดแวร์ ปรับเสียง, ไฟฉาย, ความสว่าง, มีเดีย, ล็อกหน้าจอ, ปลุกหน้าจอ, เปิดแอป และนำทาง Google Maps
- **UI Automation (Android Accessibility Service)** — อ่านโครงสร้างหน้าจอ (`device_read_screen`), แตะปุ่ม (`device_tap`), พิมพ์ข้อความ (`device_type_text`) และเลื่อนหน้าจอ (`device_scroll`)
  - `device_read_screen` อ่านทุกหน้าต่างที่โต้ตอบได้ (รวม dialog/popup ขอสิทธิ์) รวมถึงปุ่มไอคอนที่ไม่มีข้อความ แต่ละรายการมีพิกัดกึ่งกลาง `@(x,y)` ให้ส่งต่อเป็น `device_tap(x, y)` ได้ทันที และจำกัดไว้ 80 รายการต่อครั้ง (`ScreenSnapshotFormatter.kt`)
  - gesture (tap/scroll) มี timeout 3 วินาที — ไม่ค้าง Live session แม้ระบบปฏิเสธ gesture
  - `device_screenshot` จับภาพหน้าจอจริง (API 30+) ส่งเข้า Gemini Live ผ่านท่อเดียวกับกล้อง — อ่านแผนที่/รูป/กราฟ/WebView ที่ a11y tree อ่านไม่ออกได้
  - `device_gesture` (กดค้าง, ปัด 4 ทิศ, ลาก) และ `device_type_text(submit=true)` กดส่ง/ค้นหาบนคีย์บอร์ดได้จริง
  - `device_open_app` รอหน้าจอเปลี่ยนแล้วแนบสรุปหน้าจอแรกกลับมาให้ AI ทันที

### 🧠 3. Advanced 6-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลตัวตนผู้ใช้/AI (identity จัดการผ่าน tool/Settings เท่านั้น กัน heuristic ทับ)
- **Layer 2: Working Memory** — บันทึกประวัติการคุยลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — Semantic Search / Vector Embeddings (Local ONNX หรือ Gemini Cloud — auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** — โครงข่ายความสัมพันธ์แนวคิด + retrieval จริงผ่าน `recall_memory`
- **Layer 5: Memory Consolidation** — "Sleep Cycle" สรุปและย้ายความจำระยะสั้นไประยะยาว (auto-trigger เมื่อแชทสะสม 200 ข้อความ)
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown

### 📊 4. Trading Intelligence บนมือถือ (TV-Powered & Unified SMC)
- **ระบบปลุก AI (คาดการณ์ล่วงหน้า)** — ปัจจัย ~115 ตัว 15 หมวดเป็น "นาฬิกาปลุก" เมื่อเกิดเหตุการณ์ AI ดูภาพ 5TF (M1/M5/M15/H1/H4 + DXY/US10Y หรือ BTC/BTC.D + ข่าว) แล้วตัดสินเองว่าควรแจ้งไหม; ระบบเรียนรู้ผลของทุกปัจจัยตามสภาพแวดล้อมและลดชั้นตัวที่ไร้ประโยชน์เอง — แยกจาก Signal Alert (สัญญาณยืนยันเมื่อแท่งปิด)
- **Indicator Alert Provider** — คำนวณ EMA20/50/200, EMA 14/60 Near-Cross & Golden/Death Cross, RSI14, MACD, Stoch, CCI, Bollinger Bands, ATR เองจากแท่งเทียน cache แม่นกว่า TradingView scanner
- **SMC Alert Provider** — ตรวจสอบโครงสร้างตลาด 5 มิติ: Premium/Discount zones, BOS/CHoCH, Order Blocks, FVG และ Liquidity Sweeps
- **Deep Analysis Suite ครบ 5 มิติ** — LSD state + confluence, Orderflow Delta, Fibo Score, Momentum, Squeeze (9 fields) พร้อม TV local fallback
- **Multi-Timeframe ทุก tool** — ระบุ TF ได้ด้วย suffix `symbol@TF` เช่น `XAUUSD@15m` (1m/5m/15m/30m/1h/4h/1D)
- **Chart Dashboard (Lightweight Charts v5.1, offline)** — กราฟ multi-pane ในตัวแอป, overlays EMA/SMA/DC/BB ทุกช่วงพีเรียด, แสดงโซน SMC และจุดสัญญาณ SIG ย้อนหลัง

### 🔔 5. Alert System V2 (ระบบเฝ้าติดตามตลาด)
- **Actionable Notifications** — แจ้งเตือนพร้อมปุ่ม **"🛑 หยุดแจ้งเตือน" / "🔁 แจ้งเตือนซ้ำ"** (manifest receiver ทำงานได้แม้แอปถูกฆ่า)
- **Voice Alert Delivery** — เปิดระบบเสียงพูดแจ้งเตือนเป็นค่าเริ่มต้น พร้อมปลุกหน้าจอขึ้นมาแจ้งเตือนอัตโนมัติแม้ปิดหน้าจออยู่
- **Adaptive Interval** — tick หลัก 30 วินาที + เร่งเช็คอัตโนมัติเมื่อราคาใกล้เป้า (<0.1% → 30 วิ, <0.5% → 1 นาที)
- **AlertFieldCatalog** — dropdown ตอนสร้าง alert เลือกได้เฉพาะ tool/field ที่ดึงค่าได้จริง ป้องกันการตั้งเงื่อนไขผิดพลาด
- **สำรอง/กู้คืน & ดูแลพื้นที่** — Settings → สำรอง / กู้คืนข้อมูล (การเรียนรู้ .json, ฐานข้อมูล .zip, Google Drive) และลบ OHLCV ที่ไม่ได้ใช้เกิน 30 วันอัตโนมัติ
- **14+ Preset ลัด** — ราคาถึงเป้า, RSI Overbought/Oversold, Golden/Death Cross, Discount/Premium Zone, Bollinger Squeeze, EMA 14/60 Convergence ฯลฯ

### 🔌 6. Provider System — Multi-Key, Fallback & Auto-Test
- **Providers ปัจจุบัน**: Gemini / OpenRouter / Groq / MiniMax
- **Gemini Multi API Key** — เพิ่ม/แก้/ลบ key ได้หลายอัน หมุนเวียนอัตโนมัติเมื่อติด Limit 429/503
- **Dynamic Model Fallback Chain** — ดึงโมเดลจริงจาก API Google เรียงลำดับตามความเร็ว Flash models และคัดกรองโมเดล 404 ออกอัตโนมัติ
- **Cross-Provider Fallback** — สลับข้ามค่ายไป Groq / OpenRouter ทันทีเมื่อ Gemini ติดโควต้า
- **Model Auto-Tester** — ทดสอบ chat + tool calling ทุกโมเดลอัตโนมัติ

### 🤖 7. Custom Tools — AI สร้าง/แก้/ลบเครื่องมือตัวเอง
- **CRUD ครบวงจรผ่าน AI** — `system_create_agent_tool` (สร้าง/แก้ไข), `system_list_agent_tools` (ดูรายการ), `system_delete_agent_tool` (ลบ)
- **Persist ข้าม session** — บันทึกลง `custom_agent_tools/*.json` โหลดกลับอัตโนมัติตอนเปิดแอป ใช้ได้ทั้งโหมด chat และ live
- **Template Argument Interpolation & Formula Mode** — คำนวณสูตรคณิตศาสตร์และแทนที่ค่าตัวแปรแม่นยำ

### 📁 8. File Tools & Attachments
- **แนบไฟล์ในแชท** — ปุ่ม 📎 แนบได้สูงสุด 5 ไฟล์ (รูป/PDF/DOCX ส่ง inline ให้ Gemini วิเคราะห์; ไฟล์ text ฝังเข้า prompt)
- **file_write รองรับ .xlsx จริง** — เขียน Excel ด้วย zip+XML ในตัว ไม่ต้องพึ่งไลบรารีภายนอก
- **Security Guard** — ป้องกัน path traversal, ควบคุมนามสกุลไฟล์ที่อนุญาต และจำกัดการอ่านไม่เกิน 200K ตัวอักษร

### 🏥 9. JARVIS Diagnostic Engine (Self-Healing)
- **Autonomous Health Verification** — ตรวจสอบการเชื่อมต่อ API, ความถูกต้องของข้อมูล และความสมบูรณ์ของฐานข้อมูลอัตโนมัติ
- **Price Source Sync** — เปรียบเทียบราคาจากหลายแหล่ง (Yahoo, OANDA, TV) เพื่อตรวจสอบ Delay
- **Log Hygiene** — ปิดบัง API key (masking) ใน logcat ป้องกันข้อมูลรั่วไหล

---

## 📊 10. mt5-core-server — Trading Intelligence Engine (Node.js)

> **Status: Production = V26.26 (Modular Codebase & Verification Pass 2026-05-23)**  
> **Runtime ปัจจุบัน: `engineMode: M15_WALL_SCALPING` — decision path ลดรูปเหลือ IndicatorPipeline + PriceMap wall detection**

- **M15 Wall Scalping Simplification** — กลยุทธ์เริ่มต้นเฉพาะ `SCALPING`; wall registry คง identity ข้าม cycle; M15/M30/H1/H4 เป็น anchor, M1/M5 เป็น confirmation
- **V26.26 Modular Codebase** — แยก helpers จาก `autoTradingService.ts` เป็นโมดูลย่อย `decisionLogger.ts` + `strategySelector.ts`
- **Context Levels & Pine Script V8.5** — Daily Pivot Points, Session VWAP, Auto-Fibonacci และ Pine Script สำหรับดู Wall Map บน TradingView
- **Analytics Scripts** — คำสั่งวิเคราะห์ประสิทธิภาพการเทรด:
  ```bash
  # แบบที่ 1: วิเคราะห์แบบระบุวันที่เฉพาะเจาะจง (เช่น วันนี้)
  npm run analyze 2026-09-09

  # แบบที่ 2: วิเคราะห์ภาพรวมทั้งหมด (All Time)
  npm run analyze
  ```

### 🔗 MT5 Full Agent Control (Broker-First)

> **Status: 🟢 Stable (21 MT5 Tools)** — Mobile (KMP) → mt5-core-server (Node.js:8090) → Python Bridge → MetaTrader5 API

- **Broker-First Data** — ราคา, volume, positions, OHLCV จาก MT5 Broker โดยตรง
- **Full Account Control** — equity, balance, margin, P&L, symbols, positions, orders, history, batch close/break-even
- **Advanced Intelligence Suite** — Market Scanner, Correlation Radar, Sentiment Gauge, Institutional Flow, Economic Radar, Trade Journal (AI scoring)
- **Audit Trail** — บันทึกทุก trade action ที่ AI สั่ง พร้อมคะแนนคุณภาพ

---

## 🛠️ Prerequisites (ข้อกำหนดพื้นฐาน)

- **Java JDK 21** (มีมาพร้อม Android Studio ในโฟลเดอร์ `jbr`)
- **Android Studio** เวอร์ชันล่าสุด
- **Node.js** (v18+) — สำหรับรัน `mt5-core-server`
- **Python 3.10+** — สำหรับ `mt5_bridge.py`

---

## 🛠️ Tech Stack (v2026)

| Layer | Technology | Status |
|-------|-----------|--------|
| Language | Kotlin 2.0 (KMP) | Stable |
| UI Framework | Jetpack Compose Multiplatform | Stable |
| Primary Brain | Gemini 3 Series (Free-Tier Optimized, Multi-Key) | Active |
| Primary Live Model | `gemini-3.1-flash-live-preview` (Native Audio, Low Latency) | Active |
| AI Providers | Gemini / OpenRouter / Groq / MiniMax (+ cross-provider fallback) | Active |
| Multimodal | Live Stream (PCM 16kHz + JPEG) | Active |
| Database | SQLDelight + SQLite Persistence | Active |
| Embedding (Mobile) | LocalOnnx (paraphrase-multilingual-MiniLM-L12-v2, ~117MB offline) / Gemini Cloud cascade | Active |
| Background Service | Android Foreground (DataSync) + Accessibility Service + Manifest Alert Receivers | Active |
| Logic Controller | JarvisOrchestrator (Multi-Provider + Fallback) | Active |
| MT5 Core Server | Node.js + Express (port 8090) — M15_WALL_SCALPING | Active |
| MT5 Python Bridge | Python + MetaTrader5 API | Active |

---

## 📦 Tool Catalogue (Total: 100+ Tools)

### 📱 DEVICE CONTROL TOOLS (31 tools)
- `device_flashlight`: ควบคุมไฟฉาย (ON, OFF, TOGGLE)
- `device_volume`: ปรับระดับเสียง (UP, DOWN, MUTE, UNMUTE, SET %, STATUS) ทุกสตรีม
- `device_brightness`: ปรับความสว่างหน้าจอ (SET %, AUTO)
- `device_media_control`: ควบคุมการเล่นเพลง/สื่อ (PLAY, PAUSE, NEXT, PREV, STOP)
- `device_always_live`: สั่งเปิด/ปิดโหมด Always AI Live (โหมดควบคุม)
- `device_open_app`: เปิดแอปพลิเคชันใดๆ ในเครื่องด้วยชื่อภาษาไทยหรืออังกฤษ
- `device_navigate`: เปิด Google Maps ดูสถานที่หรือเริ่มนำทาง Turn-by-turn
- `device_send_email`: เปิดหน้าต่างเขียนอีเมลพร้อมผู้รับ หัวข้อ และเนื้อหา
- `device_add_calendar`: บันทึกนัดหมายลง Google Calendar
- `device_make_call` & `device_send_sms`: โทรออกและส่งข้อความ SMS
- `device_set_alarm`: ตั้งนาฬิกาปลุกในระบบ Android
- `device_open_url` & `device_search_web`: เปิดเว็บเบราว์เซอร์หรือค้นหา Google
- `device_read_screen`: อ่านข้อมูลหน้าจอปัจจุบันผ่าน Accessibility Service (ทุกหน้าต่าง + พิกัด `@(x,y)` ต่อ element)
- `device_screenshot`: จับภาพหน้าจอจริงส่งให้ AI ดู (Android 11+)
- `device_tap`: แตะปุ่มด้วยข้อความ, view id (แบบสั้นได้) หรือพิกัดจาก `device_read_screen`
- `device_gesture`: กดค้าง / ปัดซ้าย-ขวา-บน-ล่าง / ลากวัตถุ
- `device_type_text`: พิมพ์ข้อความลงในช่องที่โฟกัสอยู่
- `device_scroll`: เลื่อนหน้าจอขึ้นหรือลง
- `device_press_button`: สั่งปุ่มระบบ (Back, Home, Recents, Notifications, Screenshot, Lock Screen, Wake Screen)
- `device_get_app_info`: ตรวจสอบชื่อแอปและหน้าต่างที่กำลังเปิดใช้งานอยู่
- `device_battery_status` & `device_wifi_status`: ตรวจสอบสถานะแบตเตอรี่และเครือข่าย WiFi

### 🧠 BUILT-IN & SYSTEM TOOLS (21 tools)
- `calculate`: คำนวณนิพจน์คณิตศาสตร์
- `get_current_datetime`: ข้อมูลวันเวลาและปฏิทินไทย (วันในสัปดาห์, เดือนไทย, ปี พ.ศ., เวลา Asia/Bangkok)
- `remember_fact`: บันทึกข้อมูลลงความจำระยะยาว
- `recall_memory`: Semantic search จากฐานความรู้
- `convert_units`: แปลงหน่วยสากลและหน่วยไทย (ไร่, งาน, ตารางวา)
- `set_reminder`: ตั้งการเตือน/TODO
- `format_json`: จัดรูปแบบ JSON
- `translate_text`: แปลภาษา
- `summarize_text`: สรุปข้อความยาว
- `search_web`: ค้นหาเว็บ
- `identity_update`: AI ปรับแต่งตัวตน/ข้อมูลผู้ใช้เมื่อถูกสั่ง
- `analyze_and_display_report`: ส่งรายงานยาวลงแชทแล้วพูดสรุป
- `chart_dashboard_control`: ควบคุมหน้ากราฟ เปิด/ปิดกราฟ, ปรับ layout และ indicators
- `system_run_diagnostics`: ตรวจสุขภาพระบบ (Self-healing)
- `system_check_connectivity`: ตรวจการเชื่อมต่อ API
- `system_create_agent_tool`: AI สร้าง/แก้ไข tool เอง (persist ข้าม session)
- `system_list_agent_tools`: ดูรายการ custom tools
- `system_delete_agent_tool`: ลบ custom tool
- `voice_summary`: สรุปเฉพาะส่วนที่พูดในโหมดเสียง
- `mt5_place_order` / `mt5_close_position`: alias ส่ง/ปิดออเดอร์ MT5

### 📊 TRADING TOOLS (29 tools)
- `trading_signal_anticipation`: ⏰ ระบบปลุก AI (คาดการณ์ล่วงหน้า) — create / scan / list_factors / config (เปิด-ปิดปัจจัย, งบการปลุก) / learning / inspect / recommend / status
- `trading_price`: ราคา Real-time (Stocks/Crypto/Forex/Gold — TV primary + fallback)
- `trading_market_snapshot`: ภาพรวมตลาดตามกลุ่มอุตสาหกรรม
- `trading_top_gainers` / `trading_top_losers`: หุ้น/สินทรัพย์ที่พุ่ง/ดิ่งแรงสุด
- `trading_technical_analysis`: TA (RSI, MACD, BB, EMA) รองรับทุกช่วงเวลา @TF
- `trading_multi_timeframe`: ความสอดคล้องทุก TF (W → 15m)
- `trading_bollinger_scan` / `trading_oversold_scan` / `trading_overbought_scan` / `trading_volume_breakout`: Scanners
- `trading_sentiment`: อารมณ์ตลาด
- `trading_news`: ข่าวการเงิน 6 แหล่ง
- `trading_combined`: TA + News + Sentiment
- `trading_fundamental_analysis`: ปัจจัยพื้นฐาน
- `trading_fear_greed`: 🌡️ Crypto Fear & Greed Index
- `trading_macro_calendar`: 📅 ปฏิทินเศรษฐกิจ ForexFactory แปลงเวลาไทยแม่นยำ
- `trading_economic_data`: 🇺🇸 ข้อมูลเศรษฐกิจสหรัฐฯ จาก FRED
- `trading_correlation_matrix`: Correlation ระหว่างสินทรัพย์
- `trading_position_sizing`: คำนวณขนาดไม้ตามความเสี่ยง
- `trading_crypto_overview`: ข้อมูลตลาด Crypto จาก CoinGecko
- `trading_deep_analysis_suite`: วิเคราะห์ 5 มิติ (LSD, Orderflow, Fibo, Momentum, Squeeze)
- `trading_harmonic_scan`: Harmonic Patterns
- `trading_elliot_modern_analysis`: Elliott Wave แบบ Modern
- `trading_strategy_signal`: สัญญาณจากกลยุทธ์ Quantpedia คำนวณในเครื่อง
- `trading_signal_stats`: สถิติผลการเทรดย้อนหลังและผลจริง (Win rate / R-multiple)
- `automation_manage_alerts`: จัดการแจ้งเตือนเงื่อนไขราคาและสัญญาณเทรด
- `automation_manage_schedule`: จัดการงานอัตโนมัติตามเวลา

### 🔗 MT5 BRIDGE TOOLS (21 tools)
- **Core Actions:** `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_modify_position`
- **Core Agent:** `trading_mt5_account_info`, `trading_mt5_list_positions`, `trading_mt5_list_orders`, `trading_mt5_list_history`, `trading_mt5_candles`, `trading_mt5_symbol_info`, `trading_mt5_symbol_search`, `trading_mt5_analyze`, `trading_mt5_close_all`, `trading_mt5_break_even_all`, `trading_mt5_snapshot`, `trading_mt5_trade_actions`
- **Advanced Intelligence:** `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_sentiment_gauge`, `trading_mt5_institutional_flow`, `trading_mt5_economic_radar`, `trading_mt5_trade_journal`

### 📈 SMC TOOLS (5 tools)
- `trading_smc_analysis`: Full SMC Dashboard
- `trading_smc_sweeps`: ตรวจจับการกวาดสภาพคล่อง (MTF Liquidity Sweeps)
- `trading_smc_liquidity`: โซนสภาพคล่อง MTF พร้อมดาวระดับความสำคัญ
- `trading_smc_orderblocks`: ตรวจจับ Order Blocks และ Fair Value Gaps (FVG)
- `trading_smc_structure`: ตรวจสอบ Market Structure (BOS / CHoCH, Premium/Discount)

### 📚 STRATEGY LIBRARY (3 tools)
- `strategy_list` / `strategy_search` / `strategy_explain`: คลังกลยุทธ์ Quantpedia 60 แบบ พร้อมคำอธิบายและแนวคิด

### 📁 FILE MANAGEMENT (7 tools)
- `file_list`, `file_read`, `file_write` (รองรับ **.xlsx จริง**), `file_delete`, `file_analyze` (OCR/PDF), `file_move`, `file_search`

### 📷 CAMERA, VISION & VOICE (7 tools)
- `vision_activate` / `vision_deactivate`: เปิด/ปิดตา AI
- `camera_analyze_scene`: วิเคราะห์ภาพจากกล้อง
- `camera_detect_objects`: ตรวจจับวัตถุ (AR Overlay)
- `camera_read_text`: สแกนอ่านตัวหนังสือ (OCR)
- `camera_switch_provider` / `camera_switch_mode`: สลับผู้ให้บริการกล้องและโหมดการทำงาน
- `voice_get_profiles` & `voice_set_profile`: จัดการและเปลี่ยนโปรไฟล์เสียง AI (30 เสียง)

---

## 🚀 Roadmap

1. **Rich Chat Rendering** — แสดงตาราง / รูป / กราฟ / infographic ในแชท
2. **Multi-Agent Orchestration (Swarm)** — กระจายงานให้ AI Agent เฉพาะทางทำงานร่วมกัน
3. **Portfolio Hub** — ติดตามพอร์ตการลงทุนและวิเคราะห์ P&L แบบละเอียด
4. **Strategy Backtester & Evolution** — พัฒนาและค้นหาพารามิเตอร์กลยุทธ์ด้วยระบบพันธุกรรม (Genetic Evolution)
5. **Hardware Extension** — เชื่อมต่อ Smart Home / อุปกรณ์สวมใส่ (Wearables)

---

## 📚 เอกสารอ้างอิง (Obsidian Wiki)

- `.obsidian-wiki/00_System/index.md` — สารบัญใหญ่ของระบบ
- `.obsidian-wiki/00_System/log.md` — บันทึกการพัฒนาทั้งหมด
- `.obsidian-wiki/01_Architecture/` — สถาปัตยกรรมระบบมือถือและ AI
- `.obsidian-wiki/02_Components/Always_AI_Live_Mode.md` — เอกสารระบบโหมดควบคุมและอวตาร 3D
- `.obsidian-wiki/02_Components/Rive_Avatar_Engine.md` — ระบบ Rive Avatar Engine และ 6-Layer Parallel State Machine
- `.obsidian-wiki/04_Tasks/` — บันทึก Changelog และแผนงานรายวัน
- `.obsidian-wiki/07_Trading_Intelligence/` — รายละเอียดระบบการเทรด mt5-core-server และ Unified SMC
