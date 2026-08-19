# ⬡ บันทึกการเปลี่ยนแปลง (JARVIS Wiki Log) ⬡

บันทึกเหตุการณ์และการเปลี่ยนแปลงสำคัญของโปรเจคในรูปแบบ Chronological

## [2026-08-03] 📰 ข่าวรอบ 2 — ทดสอบจริงผ่าน + เก็บงาน feed ตาย/HTML หลุด
- **ผลเทส (news_check2)**: ถาม "ข่าวที่มีผลต่อทองคำ" ได้ข่าวทองจริงจาก Yahoo Finance/Fortune/SD Bullion/gold.org/KITCO + AI วิเคราะห์ +8/10 Bullish + เสียงพูดครบ (audio 1.5MB) ✅
- **ผลเทสรอบ 3 (news_check3)**: ผ่านสมบูรณ์ — ไม่มี feed error, ไม่มี HTML หลุด, เสียงพูดภาษาไทยธรรมชาติครบ 7+ ประโยค (audio 1.7MB) ✅ — เก็บงานเพิ่ม: description ของ Google News ซ้ำ title+source → ลบออกถ้าไม่มีเนื้อหาเพิ่ม
- **ปัญหาที่เก็บงาน**:
  - CoinDesk feed 404, MarketWatch 403 → เปลี่ยนเป็น `feeds.content.dowjones.io/public/rss/mw_topstories` (MarketWatch ใหม่) และ `cointelegraph.com/rss` (แทน CoinDesk)
  - Description มี `<a href=...>` หลุด → Google News เข้ารหัส HTML เป็น `&lt;` — สลับลำดับ clean(): unescape entities ก่อน แล้วค่อย strip tag
- คอมไพล์ผ่าน

## [2026-08-03] 📰 ระบบข่าว trading_news — ขยาย 3 → 6 แหล่ง + ค้นตรงสินทรัพย์
- **อาการ** (จาก log news_check): ถาม "ข่าวทองคำ" ได้ข่าวหุ้นมั่ว (Sprouts/BYD/Tesla) — `getFinancialNews` มีแค่ Yahoo/CoinDesk/MarketWatch 3 feeds แบบ general, filter keyword ไม่เจอ → fallback เป็นข่าวทั่วไปที่ไม่เกี่ยว
- **แก้ใน `TradingApiService.kt`**:
  - เพิ่ม **Google News RSS search** เป็นแหล่งหลักต่อ symbol (ฟรี ไม่ต้อง API key, ค้นฝั่ง server ตรงสินทรัพย์) — `getAssetQuery()` map symbol → query (XAU→"gold price OR XAUUSD", BTC→"bitcoin price", forex 6 ตัวอักษร → pair, ฯลฯ)
  - General feeds ขยายเป็น 5 แหล่ง: Yahoo, **CNBC (ใหม่)**, MarketWatch, **Investing.com (ใหม่)**, CoinDesk — ดึงขนานกันด้วย `async` + `fetchFeed()` helper (timeout 10 วิ ล้มเหลวเงียบ + logDebug)
  - `parseRssItems()` รองรับ **CDATA** + strip HTML/entities + แยก source จริงจาก suffix " - SourceName" ของ Google News
- คอมไพล์ผ่าน — ลองถาม "ข่าวทองคำ" อีกรอบควรได้ข่าวทองจริงจากหลายแหล่ง

## [2026-08-03] 🎚️ Live Voice Tuning — คำตอบเสียงสั้นเกินไป (แก้ overcorrection จาก F4)
- **อาการ** (จาก log round5): เสียงทำงานเต็มแล้ว (audio 849KB ครบ turn) แต่ VOICE RULE "2-4 ประโยค ห้ามอ่านตัวเลข" ทำให้ model ตอบกระชับเกิน — ไม่เล่า RSI/ตัวเลขเศรษฐกิจที่ดึงมาเลย
- **แก้ 3 จุดให้สมดุล**: VOICE RULE ใหม่ (ทั้ง `sendBridgeToolResult` และ Path A suffix) = พูด 5-8 ประโยค ครอบคลุม ผลสรุปหลัก + เหตุผล/ตัวเลขสำคัญ 2-4 จุด เล่าเป็นประโยคธรรมชาติ (เช่น "RSI อยู่ที่ 45 แสดงว่าโมเมนตัมยังอ่อนแอ") + จุดที่ควรระวัง — ยังคงห้ามอ่านตาราง/markdown
- **LIVE_RULES ข้อ 3** ปรับจาก "พูดสรุปสั้นๆ" เป็น "พูดสรุปอย่างมีสาระ ไม่สั้นเกินไปจนไม่มีเนื้อหา" — คอมไพล์ผ่าน

## [2026-08-03] 📚 Strategy Library — ผูกคลังกลยุทธ์ Quantpedia 60 แบบเข้าระบบ tool
- **ต้นทาง**: user เพิ่มโฟลเดอร์ `strategies/` (60 ไฟล์ .py, ~9,272 บรรทัด) — Quantpedia/QuantConnect reference implementations (momentum, reversal, value, carry, seasonality, volatility, pairs, macro, crypto)
- **สร้าง `scripts/generate_strategy_index.py`**: parse header comment → description + จัด 9 หมวดจากชื่อไฟล์ → สร้าง `index.json` + copy .py เข้า `composeApp/src/commonMain/composeResources/files/strategies/` (61 ไฟล์ถูก pack ยืนยันแล้ว) — รันใหม่เมื่อเพิ่ม/แก้ไฟล์
- **โค้ดใหม่ `tools/strategy/`**: `StrategyLibrary.kt` (อ่าน resource ผ่าน `Res.readBytes`, cache index, fuzzy name match), `StrategyToolDefinitions.kt`, `StrategyToolExecutor.kt`
- **3 tools ใหม่ (offline)**: `strategy_list` (หมวด+รายชื่อ), `strategy_search` (keyword scoring), `strategy_explain` (คำอธิบาย + โค้ดเต็ม — reference ไม่ได้รันบนเครื่อง)
- **Register**: ToolRegistry (`_strategyTools`, category "📚 Strategy Library", isStrategyTool) + ToolExecutor routing — tool รวม 87 ตัว (README อัปเดตตารางแล้ว)
- **Result**: คอมไพล์ผ่าน — รอเทสบนเครื่อง: "มีกลยุทธ์ momentum อะไรบ้าง", "อธิบาย fx carry trade"

## [2026-07-30] 🔊 Live Voice Fix รอบ 2 (หมวด 7) — แก้เสียงตอบไม่หมด/เงียบ 5 จุด (F1-F5)
- **วินิจฉัย** (จาก log ทดสอบ 4 รอบ + เอกสาร Live API guide): อาการเสียงขาด/เงียบมี 3 ต้นตอ — (1) generation ถูก VAD ตัดกลางทางแต่ไม่ handle `interrupted` (2) instruction ส่งผล tool กลับขัดกับ LIVE_RULES → model สลับไปตอบ text/markdown (3) ไม่มี fallback เมื่อไม่มีเสียง
- **F1**: setup ไม่ได้ขอ `output_audio_transcription`/`input_audio_transcription` (ต้องส่ง `{}` ตาม Live API guide ถึงจะได้ transcript) → เพิ่มใน `LiveSetup` + ส่งตอน connect — แชท/DB persistence พึ่ง transcript ทั้งหมด
- **F2**: handle `content.interrupted` → callback `onInterrupted` → `PcmAudioEngine.stopPlaying()` flush คิวเสียงค้าง + `stopPlaying()` เพิ่ม `play()` หลัง flush (เดิม pause ค้าง chunk ถัดไปเงียบ); reset `audioBytesThisTurn`/`pendingModelTextParts`
- **F3**: `_audioOutputFlow` เพิ่ม `extraBufferCapacity=128` — แยก WebSocket read ออกจาก `AudioTrack.write()` ที่ blocking (เดิม emit suspend รอ playback → เฟรมค้างทั้งระบบ)
- **F4**: `sendBridgeToolResult` เปลี่ยนจาก "นำเสนออย่างละเอียด อย่าสรุปสั้น" (ขัด LIVE_RULES) เป็น `[VOICE RULE]` พูดสรุปสั้น 2-4 ประโยค + ผล tool ทั่วไปใน LiveToolBridge Path A แนบ VOICE RULE ต่อท้ายด้วย
- **F5**: turn จบโดย `audioBytesThisTurn == 0` → callback `onTurnWithoutAudio(text)` → ViewModel ใช้ `VoiceManager.speak()` (Android TTS) พูดแทน — กันเงียบเฉย
- **Wiring**: Orchestrator เพิ่ม `setLiveInterruptionHandler`/`setLiveNoAudioFallback`; ViewModel ตั้ง handlers ใน `startVoiceInput`; `PcmAudioEngine.playAudio` กัน write throw
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — รอเทสเครื่องจริง: เสียงตอบครบทุก turn, แชทมี transcript, barge-in ไม่เล่นเสียงเก่าทับ

## [2026-07-30] 📋 สร้าง App Review Checklist — แบ่งแอปเป็น 15 หมวด
- สร้าง [[App_Review_Checklist]] (04_Tasks) แบ่งโครงสร้างแอปตาม package จริงเป็น 15 หมวด พร้อมขอบเขตไฟล์ + ประเด็นตรวจย่อย + ตารางสถานะ
- ติ๊กเสร็จ 4 หมวดที่ทำไปแล้ว: (1) Provider (2) System Prompt/ตัวตน AI+CORE_IDENTITY (3) Trading Tool (79 tools + FRED + ปฏิทินเศรษฐกิจ) (4) Memory 4 Layers + Local Embedding
- หมวดที่รอตรวจ (เรียงความสำคัญ): Tool System/สร้าง Tool, Orchestrator/Intent, Live Mode/Voice (bug เสียงตอบไม่ครบ), Scheduled Tasks, File Tools/Security, System Tools, Camera/Vision, UI/UX, Database/Storage, mt5-core-server, Diagnostic/Logging
- อัปเดต [[index]] (Sitemap 04 Tasks) ลิงก์เข้าเช็คลิสต์

## [2026-07-30] 🧠 Memory System Fix Pack — แก้ 7 จุด (B1-B4 + E1-E3) + Local Embedding
- **B1**: KnowledgeEdge ไม่มี UNIQUE → migration `2.sqm` (v3): DELETE ซ้ำ + `CREATE UNIQUE INDEX idx_knowledgeedge_unique(source_id,target_id,relation)`; index ใส่ใน .sq ด้วยสำหรับ fresh install — ยืนยัน generated schema v3 (create+migrate มี index)
- **B2**: `extractAndUpdateCoreMemory` ตัด heuristic `user_name`/`language` ออก (ชน CORE_IDENTITY — เดิม regex ทับ "บอส" ได้) + สกัด occupation/interests เฉพาะ userMessage; ลบ extractName/detectLanguagePreference/nameBlacklist (dead)
- **B3**: GraphRAG ไม่ write-only อีกต่อไป — เพิ่ม `getGraphContext(query)` (match keywords→nodes→edges เรียง weight) wire เข้า `onRecallMemory` (แสดง semantic facts + ความสัมพันธ์ graph)
- **B4**: auto sleep cycle — `sendMessage` เช็ค `getMessageCount() >= 200` → `triggerSleepCycle()` (เดิมไม่มี caller เลย); เพิ่ม `getMessageCount()` ใน MemoryManager; ลบ `getConversationContext` (dead)
- **E1 (Local/Cloud Embedding bug ใหญ่)**: `GeminiEmbeddingProvider` capture apiKey="" ตอน init ตลอดกาล → ทำ `currentApiKey` เป็น @Volatile var + `updateApiKey()`; registry เพิ่ม `updateGeminiKey()`; `Orchestrator.updateConfig` เรียกใช้ — เดิม cloud embedding ไม่เคยทำงานหลัง user ใส่ key ทีหลัง
- **E2**: `LocalKotlinTokenizer` เขียนใหม่ — รองรับ **Unigram (SentencePiece) ด้วย Viterbi best-path** (paraphrase-multilingual-MiniLM-L12-v2 เป็น Unigram — เดิม parse แบบ BPE merges ทำให้ tokenize เป็น char-level embedding พัง โดยเฉพาะไทย); BPE path เก็บไว้เป็น fallback
- **E3**: `AndroidLocalOnnxManager.runInference` สร้าง inputs แบบ dynamic ตาม `session.inputNames` (เติม `token_type_ids` zeros ถ้าโมเดลต้องการ — เดิมส่งแค่ 2 inputs อาจ throw เงียบๆ)
- **Minor**: auto-backfill embeddings เปิดกลับมา (delay 15 วิ + runCatching + ใช้ `registry.resolve()` — เดิม comment ไว้เพราะส่ง `getGeminiService()` ผิด type compile ไม่ผ่าน); `LiveGeminiService.emitTextToChat` truncate tool dump >2000 chars ก่อนเก็บ working memory (กัน history bloat)
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — รอทดสอบเครื่องจริง (โดยเฉพาะ local ONNX หลังดาวน์โหลดโมเดล + recall_memory ที่ต้องโชว์ graph context)

## [2026-07-30] 🧠 Audit: Memory System 4 Layers — พบปัญหา 4 จุดหลัก (ยังไม่แก้โค้ด)
- **🔴 B1 — KnowledgeEdge ไม่มี UNIQUE constraint**: schema ไม่มี UNIQUE(source_id,target_id,relation) แต่ query ใช้ `INSERT OR IGNORE` → **edge ซ้ำสะสมทุกข้อความที่สนทนา** (updateKnowledgeGraph ทำงานทุก turn) — DB โตไร้ขีดจำกัด + weight increment กระจายไปทุก row ซ้ำ
- **🔴 B2 — Identity key ชนกัน**: heuristic `extractAndUpdateCoreMemory` เขียน key `user_name` (regex จับชื่อ) — **key เดียวกับระบบ CORE_IDENTITY (JarvisPersona.toCoreMemoryMap)** → ผู้ใช้พิมพ์ติด pattern เช่น "เรา ไปกินข้าว" จะทับชื่อ "บอส" ที่ตั้งไว้ทันที; key `language` ก็ถูกเขียนทุกข้อความ (detectLanguagePreference คืนค่าเสมอ)
- **🟠 B3 — GraphRAG เป็น write-only**: nodes/edges ถูกสร้างทุก turn แต่**ไม่เคยถูกอ่าน** (`getEdgesFrom`/`getAllNodes` ไม่มี caller ใน Kotlin) → Layer 4 ไม่มีผลต่อคำตอบของ AI เลย
- **🟠 B4 — Sleep Cycle (Dream Engine) ไม่มีทางถูกเรียก**: `triggerSleepCycle` ไม่มี UI/caller → ChatMessage โตไม่จำกัด, consolidation ไม่เคยรัน
- **🟡 Minor**: `getConversationContext` dead code (buildHistorySnapshot ใช้แทน); `updateArchivalAccess`/`getArchivalRecent` dead queries (access_count ไม่เคยถูกใช้); occupation/interests สกัดจาก `userMessage+aiResponse` รวมกัน → คำของ AI อาจไปเซ็ตอาชีพผู้ใช้; tool result dumps เข้า working memory → history bloat; auto-backfill embeddings ถูก comment ไว้ (ตั้งใจ)
- **ส่วนที่ดี/ถูกต้อง**: Core Memory upsert + always-in-context ทำงาน; Working Memory store/snapshot ถูก flow (user→store→snapshot→model→store); Embedding normalize dim (`fitToTargetDimension`) + cosine search + fallback top-importance ถูก; Sleep cycle มี archive transcript ก่อนลบ (กันข้อมูลหาย); KnowledgeNode มี UNIQUE(name) dedupe ถูก

## [2026-07-30] 🚀 Automation 2.0 — AI Wake-up, Scheduled Tasks, Tools ใหม่ (ตามแผน audit 5 ข้อ + บั๊กใหญ่ 1 จุด)
- **🐛 บั๊กวิกฤตที่พบระหว่างทำ**: `JarvisAutomationService` เปิด DB ไฟล์ **`jarvis_bot.db`** แต่แอปหลักใช้ `jarvis.db` → alert ที่ AI สร้าง**ไม่เคยถูก background loop เช็คเลย** — แก้ให้ใช้ไฟล์เดียวกัน (`jarvis.db`)
- **#1 Trading tools**: `trading_fear_greed` เขียนใหม่ใช้ **alternative.me** (ดัชนีจริง 0-100 + อนุกรม 7 วัน + ตีความไทย — เดิมดึง metrics ภายใน MT5 server แล้วให้ AI เดา); tool ใหม่ **`trading_crypto_overview`** (CoinGecko ฟรี: market cap รวม, BTC/ETH dominance, เหรียญ trending, F&G ประกอบ); `trading_deep_analysis_suite` เพิ่ม **local fallback** (AdvancedTradingEngine บนเครื่อง เมื่อ bridge ล้ม — ตรงกับที่ background ใช้)
- **#2 Background coverage**: `checkJob` รองรับ tool เพิ่ม (trading_fear_greed → field value, trading_crypto_overview → btc_dominance/market_cap_change_24h) + log เตือนเมื่อ tool ไม่รองรับ; `automation_manage_alerts` เพิ่ม action **`list`** (AI ดู alert ที่ active ได้)
- **#3 AI Wake-up**: เมื่อ alert เข้าเงื่อนไข → service เรียก Gemini (key/model จาก AppSetting) สรุปบริบทเป็นภาษาไทย → notification แบบ BigTextStyle; ตั้งค่าได้ 2 ตัวใน AppSetting: `alert_ai_summary` (default เปิด), `alert_voice` (พูดเตือนผ่าน Android TTS ภาษาไทย, default ปิด); fallback เป็น notification ดิบถ้าไม่มี key/เรียกไม่สำเร็จ
- **#4 Scheduled Tasks**: ตาราง `ScheduledTask` ใหม่ (DB **v2**, migration `1.sqm`) — one_time (run_at epoch / in_minutes) + daily (time_hhmm กันยิงซ้ำด้วย last_fired_date); service ยิงเมื่อถึงเวลา → ปลุก AI ทำตาม prompt → แจ้งเตือน/พูด; tool ใหม่ **`automation_manage_schedule`** (create/delete/list) ผ่าน SideEffectDelegate.onManageSchedule → Orchestrator
- **#5 Boot receiver**: `BootReceiver` + `RECEIVE_BOOT_COMPLETED` — ตื่น service หลังรีบูท (guard กัน FGS deny อยู่แล้วใน service)
- **UI**: AutomationScreen เพิ่มการ์ดตั้งค่า (toggle AI สรุป / เสียงพูด) + section Scheduled Tasks (แสดง/ลบ); settings persist ผ่าน JarvisViewModel → AppSetting
- **Result**: คอมไพล์ผ่าน, schema v2 ยืนยัน (create+migrate มี ScheduledTask) — **84 tools** (trading 23→25) — รอทดสอบเครื่องจริง: สร้าง alert/schedule แล้วดู notification + เสียง

## [2026-07-30] 🔍 Audit: Trading Tools ครบวงจร + Scheduled Tasks System Review (ยังไม่แก้โค้ด)
- **Trading Tools (23)**: ทุก tool มี definition (TradingToolDefinitions) + handler (TradingToolExecutor) + service implementation ครบ ไม่มี stub/TODO — แหล่งข้อมูล: Yahoo (price), TradingView Scanner (snapshot/gainers/losers/TA/MTF/scans), Reddit (sentiment), RSS (news), ForexFactory (calendar ✅ ทดสอบแล้ว), FRED (economic_data ✅ ทดสอบแล้ว)
- **ปัญหาที่พบ**:
  1. `trading_fear_greed` — ชื่อ/คำอธิบายบอก "ดัชนีความกลัวโลภ (Crypto)" แต่ implementation ดึง metrics ภายในจาก MT5 server (`127.0.0.1:8090/api/mt5/auto/analytics`) แล้วให้ AI เดา — ไม่ใช่ Fear & Greed Index จริง, พังถ้า server ออฟไลน์ → ควรเปลี่ยนเป็น alternative.me (ฟรี ไม่ต้อง key)
  2. `trading_deep_analysis_suite` — เรียกผ่าน MT5 bridge endpoint เท่านั้น (ไม่มี local fallback) ขณะที่ JarvisAutomationService ใช้ AdvancedTradingEngine local — พฤติกรรมไม่ตรงกัน 2 จุด
- **Scheduled Tasks ที่มีอยู่**: AlertJob (SQLDelight) + AutomationManager + JarvisAutomationService (foreground service, poll 60s, per-job interval, condition GT/LT/GTE/LTE/EQ/CONTAINS, edge-trigger notification + auto-reset) + AutomationScreen (list/delete/edit interval) + tool `automation_manage_alerts` (AI สร้าง/ลบได้) — โครงตรงแนวคิดผู้ใช้แล้ว
- **Gap ของ Scheduled Tasks**:
  1. เมื่อเงื่อนไขตรง → ส่งแค่ system notification ดิบ ไม่มีการ "ปลุก AI" มาตรวจสอบ/สรุป/พูด (ต้องการ: notification → wake AI → AI แจ้งผู้ใช้แบบ msg หรือเสียง ตามตั้งค่า)
  2. `checkJob` รองรับแค่ 4 tool_name (price/technical_analysis/sentiment/deep_analysis_suite) — job ที่ AI สร้างด้วย tool อื่นจะไม่มีวัน fire
  3. ไม่มี action `list` ใน automation_manage_alerts — AI ไม่รู้ว่ามี alert อะไรอยู่
  4. ไม่มี one-shot/time-based schedule (เช่น "เตือน 20:00", "เช็คข่าวทุกเช้า")
  5. ไม่มี BOOT_COMPLETED receiver — reboot เครื่องแล้ว alert หยุดจนกว่าจะเปิดแอป
- **Tool ที่ยังขาด (เสนอ)**: crypto overview (CoinGecko ฟรี), fear_greed จริง (alternative.me ฟรี), OHLCV candle fetch สำหรับสัญลักษณ์ทั่วไป (มี TvHistoryBridge อยู่ภายในแต่ไม่มี tool เปิดให้ AI)

## [2026-07-30] 📅 getEconomicCalendar เขียนใหม่ — เปลี่ยน FXStreet (ตาย) → ForexFactory
- **Trigger**: ต้องการ "ปฏิทินเรียลไทม์ แบบฟรี ไม่ยุ่งยาก" — ทดสอบ curl พบแหล่งเดิม FXStreet (`calendar-api.fxsstatic.com`) **ตายแล้ว (401)**
- **Design decision**: ใช้ **ForexFactory** `https://nfs.faireconomy.media/ff_calendar_thisweek.xml` — ฟรี, ไม่ต้อง API Key, ครอบคลุมเหตุการณ์ทั้งสัปดาห์ (ทดสอบ curl = 200, ~32KB)
- **Implementation**:
  - **[TradingApiService]** `getEconomicCalendar` ใหม่ทั้งก้อน — GET ด้วย `User-Agent: curl/8.0` (CDN กรอง UA เหมือน FRED), parse `<event>` ด้วย Regex (รองรับ CDATA)
  - **แปลงเวลา ET (New York) → เวลาไทย** ด้วย `TimeZone.of("America/New_York")` → `Asia/Bangkok` (`formatEventTimeThai`), มี `date_sort` สำหรับเรียงลำดับ
  - **impact_score** (high=3/medium=2/low=1) — เรียงข่าวสำคัญสุดก่อน แล้วตามเวลา
  - **[TradingToolExecutor]** label รายงานเปลี่ยนเป็น "Economic Calendar สัปดาห์นี้ (ForexFactory — Real-time, เวลาไทย)"
- **Result**: คอมไพล์ผ่าน — จำนวน tools ไม่เปลี่ยน (82, เป็นการแก้ tool `trading_macro_calendar` เดิม) — รอทดสอบเครื่องจริง

## [2026-07-30] 🔧 Hotfix#2: FRED — HTTP/2 stream reset + UA filter
- **Trigger**: ทดสอบบนเครื่องจริงยังล้มเหลว — log tag TradingApi แสดง `stream was reset: INTERNAL_ERROR` ทุก series
- **Root cause (2 ชั้น)**: (1) CDN ของ FRED **reset HTTP/2 stream** — OkHttp ต่อด้วย h2 จึงถูกปฏิเสธ (curl HTTP/1.1 ผ่าน) (2) CDN กรอง User-Agent — ทดสอบ curl: `ktor-client`/ไม่มี UA = stall, `Mozilla/5.0` = stall, **`curl/8.0` = 200 OK เท่านั้น**
- **Fix**:
  - **[Platform.android]** OkHttp config บังคับ `protocols(HTTP_1_1)` — แก้ stream reset (endpoint อื่นใช้ 1.1 ได้ปกติ, WebSocket ใช้ 1.1 upgrade อยู่แล้ว)
  - **[TradingApiService]** FRED requests (CSV + JSON API) ใส่ `User-Agent: curl/8.0`
- **Result**: คอมไพล์ผ่าน — รอทดสอบเครื่องจริง

## [2026-07-30] 🔧 Hotfix: FRED fetch ล้มเหลวทุก series (Browser UA ถูก CDN block)
- **Trigger**: log ทดสอบ Live (`fred_test_022325.txt`) — tool `trading_economic_data({series=overview})` ถูกเรียกถูกต้องแต่ทุก series คืน "ดึงข้อมูลไม่สำเร็จ"
- **Root cause**: `getFredViaCsv` ใส่ header `User-Agent: Mozilla/5.0` — CDN ของ FRED **block/stall request ที่มี browser UA** (ทดสอบด้วย curl: ไม่ใส่ UA = 200 OK ได้ข้อมูลจริง, ใส่ Mozilla/5.0 = connection stall/timeout); Ktor default UA (ktor-client) ผ่านปกติ
- **Fix**: ลบ browser UA ออกจาก `getFredViaCsv` + log HTTP status/message เมื่อดึงไม่สำเร็จเพื่อ debug รอบถัดไป
- **Result**: คอมไพล์ผ่าน — รอทดสอบบนเครื่องจริงอีกครั้ง

## [2026-07-30] 🇺🇸 Tool ใหม่: trading_economic_data (FRED — ตัวเลขเศรษฐกิจสหรัฐฯ)
- **Trigger**: ต้องการ tool ดึงตัวเลขเศรษฐกิจสหรัฐฯ (ผู้ใช้เสนอ FRED / Trading Economics / FMP)
- **Design decision**: เลือก FRED เป็นหลักเพราะมี endpoint สาธารณะ `fredgraph.csv` ที่ **ไม่ต้องใช้ API Key** (ใช้ได้ทันที) — รองรับ FRED JSON API (`api.stlouisfed.org/fred/series/observations`) เมื่อผู้ใช้มี key (optional arg `api_key`, fallback CSV อัตโนมัติ); Trading Economics/FMP ต้องใช้ key เสียเงิน ยังไม่ใส่
- **Implementation**:
  - **[TradingApiService]** `getFredSeriesObservations(seriesId, apiKey?)` — JSON API (มี key) → fallback fredgraph.csv; กรองค่า missing (`.`)
  - **[TradingToolDefinitions/Registry]** tool `trading_economic_data` (args: series, limit, api_key) เพิ่มใน supportedTradingToolNames
  - **[TradingToolExecutor]** `executeEconomicData` — 16 presets (gdp, gdp_growth, cpi, core_cpi, pce, unemployment, nfp, fedfunds, 10y, 2y, m2, retail, housing, sentiment, indpro, claims) + รองรับ FRED series id ตรง + โหมด `overview` สรุป 4 ตัวชี้วัดหลัก (GDP growth/CPI/ว่างงาน/Fed Funds) — รายงานแสดงค่าล่าสุด, เปลี่ยนจากช่วงก่อน (+%), กรอบต่ำสุด-สูงสุด, อนุกรมย้อนหลัง
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — trading tools 22 → 23 (รวม 82 tools)

## [2026-07-30] 🔇 Live Voice Fix — เสียงตอบไม่หมด/เงียบ (Text-only Response)
- **Trigger**: ทดสอบ Live mode 4 รอบ (log: `00_System/live_test_logs/`) — ข้อความในแชทมาครบ แต่เสียงพูดตอบไม่หมด บางรอบเงียบเลย บางรอบพูดครบ (เช่น "วิเคราะห์ภาพรวมตลาดหุ้นอเมริกา", "ข่าวน้ำมัน")
- **Root cause (จาก log)**: รอบที่เสียงครบ transcript ไหลมาทีละคำต่อเนื่อง 20-30 วิ (audio จริง) — รอบที่เสียงหาย transcript มาเป็น **ก้อนเดียวยาวแล้ว Turn Complete ภายใน 8ms** = model ตอบเป็น **markdown text แทนเสียง** (Live API ไม่ synthesize ส่วนที่เป็น text part) — ล่อมาจาก (1) tool results เป็น markdown หนัก (2) LIVE_RULES เดิมสั่งให้ "วิเคราะห์เชิงลึกด้วยเสียง" โดยไม่ห้าม markdown (3) tool `analyze_and_display_report` มี handler ใน LiveToolBridge แต่**ไม่ได้ประกาศใน ToolRegistry** → model ไม่มีช่องทางส่งรายละเอียดลงแชท จึงพยายามพูด/เขียนตารางยาวๆ ออกมา
- **Implementation**:
  - **[JarvisPersona LIVE_RULES]** เพิ่ม "กฎเสียงพูด (VOICE OUTPUT)": ห้าม markdown/หัวข้อ/ตาราง/bullet/อีโมจิในคำตอบเด็ดขาด ต้องพูดเป็นประโยคสนทนาสั้นๆ ไล่เรียงกัน เล่าตัวเลขเป็นประโยค + กฎ REPORT TOOL: รายละเอียดยาวให้เรียก `analyze_and_display_report` ลงแชท แล้วพูดสรุป 2-4 ประโยค ห้ามอ่านตารางออกเสียง
  - **[ToolRegistry]** ประกาศ `analyze_and_display_report` (detailed_markdown + voice_summary) → ใช้ได้ทั้ง Live path (LiveToolBridge case เดิม) และ text chat path
  - **[ToolExecutor]** handler text path: ส่ง markdown ผ่าน `SideEffectDelegate.onDisplayReport` → Orchestrator → callback ใหม่ `setDisplayReportHandler` → JarvisViewModel append ข้อความรายงานเข้าแชท
  - **[LiveGeminiService]** capture text parts ใน modelTurn ที่เดิมถูกทิ้ง (emit เข้าแชท + persist DB เป็น fallback กันข้อความหาย) + นับ audio bytes ต่อ turn — turn ที่จบโดยไม่มีเสียงจะ log "🔇 Turn Complete with NO AUDIO" ไว้ debug ต่อได้
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — built-in tools 16 → 17 (รวม 81 tools)

## [2026-07-30] 🛠️ Tool System Audit + Agent Tool Creation Fix
- **Trigger**: ตรวจสอบ tool ทั้งหมด (~79 ตัว) ว่าเรียกใช้ได้จริง และแก้ระบบสร้าง tool ที่ AI สั่งสร้างแล้วไม่เกิดอะไรขึ้น
- **Audit พบ 7 จุดพัง**:
  1. `system_run_diagnostics` / `system_check_connectivity` / `system_create_agent_tool` — `SystemToolExecutor` มี handler ครบแต่ **ไม่เคยถูก wire เข้า ToolExecutor** → ตกไป executeCustomSkill → "ไม่พบ skill" (สาเหตุหลักที่สร้าง tool ไม่ได้)
  2. `trading_correlation_matrix` — มี declaration แต่ TradingToolExecutor ไม่มี case → "Unknown trading tool"
  3. `trading_position_sizing` — เหมือนข้อ 2
  4. `automation_manage_alerts` — เหมือนข้อ 2 (ต้องใช้ AutomationManager แต่ TradingToolExecutor ไม่มี)
  5. `vision_activate` / `vision_deactivate` / `voice_get_profiles` / `voice_set_profile` — text path route ไป CameraToolExecutor ซึ่งไม่รองรับ → "Unknown camera tool" (ทำงานได้เฉพาะ Live path)
  6. `executeCustomSkill` — placeholder คืน "กำลังดึงข้อมูล..." → custom tool รันไม่ได้จริง
  7. ไม่มี loader โหลด `custom_agent_tools/*.json` กลับเข้า ToolRegistry ตอน app start → tool ที่สร้างหายเมื่อปิดแอป
- **Implementation**:
  - **[ToolExecutor]** wire `SystemToolExecutor` (initSystemExecutor) + route `system_*`; intercept `automation_manage_alerts` และกลุ่ม vision/voice ก่อน trading/camera branch; `executeCustomSkill` คืน `systemPromptAddon` ของ tool เข้า tool loop ให้ model ทำตามขั้นตอน (เรียก tool จริงประกอบได้) แทน placeholder
  - **[SystemToolExecutor]** `system_create_agent_tool`: sanitize ชื่อ (บังคับ custom_ prefix, a-z0-9_), สร้าง JSON ด้วย kotlinx.serialization (กัน quote/newline injection), **register เข้า ToolRegistry ทันที** (FunctionDeclaration + SkillDescriptor) ใช้ได้เลยไม่ต้อง restart
  - **[JarvisOrchestrator]** init SystemToolExecutor ด้วย diagnosticManager + `loadCustomTools()` อ่าน custom_agent_tools ผ่าน fileHandler (file_list → file_read → parse) register กลับเข้า registry; implement `onManageAlerts` (create/delete ผ่าน AutomationManager + AutomationCondition/ConditionOperator)
  - **[SideEffectDelegate]** เพิ่ม `onManageAlerts(args)` (Orchestrator เป็น implementer เดียว)
  - **[TradingToolExecutor]** implement `trading_position_sizing` (pure math: units = risk$ ÷ |entry−SL|, notional, leverage, ทิศทาง) และ `trading_correlation_matrix` (Yahoo chart API daily closes → daily returns → Pearson matrix ตาราง markdown)
  - **[JarvisViewModel]** เรียก `orchestrator.loadCustomTools()` ตอน app start
- **ผลตอนนี้**: ทุก tool ที่ declare มี handler จริงครบ (built-in 16 / trading 22 / MT5 21 / SMC 5 / file 7 / camera+voice 9); AI สร้าง tool ใหม่ได้จริง end-to-end: สร้าง → บันทึกไฟล์ → register ทันที → เรียกใช้ได้ในแชทเดียวกัน → โหลดกลับอัตโนมัติทุกครั้งที่เปิดแอป
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`)

## [2026-07-30] 🪪 Customizable Identity System (Agent + User)
- **Trigger**: ต้องการ CORE_IDENTITY แบบปรับแต่งได้ — ผู้ใช้แก้เองผ่าน Settings และ AI แก้เองได้เมื่อถูกสั่ง
- **Implementation**:
  - **[JarvisPersona]** เพิ่ม `IdentityConfig` (agent: name/creature/vibe/gender — user: name/call_name/notes) + defaults; `CORE_IDENTITY`/`CHAT`/`EXTERNAL`/`LIVE` prompts เปลี่ยนเป็น computed getter → build จาก config ปัจจุบันเสมอ เปลี่ยนค่าแล้วมีผลทันทีทุก provider path (GeminiService/LiveGeminiService ใช้ getter เช่นกัน)
  - **[Persistence]** เก็บใน Core Memory table (keys: agent_name, agent_creature, agent_vibe, agent_gender, user_name, user_call_name, user_notes) — `JarvisViewModel.loadSettings()` โหลดเข้า JarvisPersona ทุกครั้งที่เปิดแอป
  - **[Settings UI]** หัวข้อใหม่ "Identity (ตัวตน AI & ผู้ใช้)" — 7 input fields แบ่งกลุ่ม AGENT/USER บันทึกพร้อมปุ่ม Save หลักผ่าน `viewModel.updateIdentity()`
  - **[AI self-edit]** tool ใหม่ `identity_update` (target: agent/user, field, value) — AI เปลี่ยนชื่อ/บุคลิก/การเรียกผู้ใช้ได้เมื่อถูกสั่ง เช่น "เรียกฉันว่าบอส", "เปลี่ยนชื่อเป็น..."; route ผ่าน SideEffectDelegate → Orchestrator → JarvisPersona + persist Core Memory
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`)

## [2026-07-29] 🎭 Provider System + Persona Unification
- **Trigger**: ตรวจสอบระบบ provider (model listing/selection/switching/free filter), system prompt ที่ไม่นิ่ง (AI สับสนตัวตน), และ Settings UX
- **Implementation**:
  - **[Persona]** สร้าง `ai/JarvisPersona.kt` — CORE_IDENTITY ก้อนเดียว (ชื่อ JARVIS, บุคลิก, ภาษา, ห้ามอ้างตัวเป็น ChatGPT/Claude/Gemini) ใช้ร่วมกันทุก path: Gemini chat (CHAT_SYSTEM_PROMPT), External providers (EXTERNAL_SYSTEM_PROMPT — เดิมได้ prompt แค่ 3 บรรทัดจนสับสนตัวตน), Live voice (LIVE_SYSTEM_PROMPT) — ลบ prompt กระจายใน GeminiService/LiveGeminiService/JarvisOrchestrator
  - **[Claude]** `listModels` เปลี่ยนจาก hardcode static list → ดึงจาก Anthropic Models API (`GET /v1/models`) จริง + fallback static list เมื่อ API ไม่พร้อม
  - **[OpenRouter]** อ่าน capability จริงจาก API: `supported_parameters` (tools) + `architecture.input_modalities` (vision) แทนการเดาจากชื่อ model
  - **[Settings UX]** (1) สลับ provider แล้ว auto-sync model ตัวแรกของ provider นั้น กัน mismatch เงียบๆ (2) free-only filter reset อัตโนมัติเมื่อออกจาก OpenRouter (3) Live model ดึงจาก Gemini เสมอ (Live รองรับเฉพาะ Gemini) พร้อมข้อความอธิบาย (4) ปุ่ม ↻ refresh model list (5) search filter เมื่อ models > 8 ตัว (สำหรับ OpenRouter 200+ models) (6) badge 🔧 tools / 👁 vision / FREE ในรายการ model (7) empty-state บอกสาเหตุและวิธีแก้ (ไม่มี key / โหลดไม่สำเร็จ / ADK ใช้ผ่าน server)
  - **[Maintenance]** เพิ่มหมายเหตุใน GeminiLlmProvider ว่า preview models ที่ inject เป็น hardcode ต้องตรวจสอบเป็นระยะ
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — ตัวตน AI นิ่งข้ามทุก provider, model list สดจาก API จริง, ตั้งค่า provider ง่ายและกันผิดพลาด

## [2026-07-29] 🧠 AI Subsystem Hardening (Mobile App — KMP)
- **Trigger**: Code review ระบบ AI ฝั่งมือถือตาม `01_Architecture/ai_subsystem_review_2026-07-29.md` (Findings F1–F10)
- **Implementation**:
  - **[F1 Security]** `FileToolExecutor` ใส่ path guard ครบทุก op — `file_write`/`file_read`/`file_list`/`file_analyze` ต้องอยู่ใน allowed roots เท่านั้น + จำกัดนามสกุลที่ write ได้ (text-based) + canonicalize กัน path traversal
  - **[F2 Robustness]** สร้าง `tools/ToolArgParser` ตัวกลาง — Orchestrator external path คืน error เข้า tool loop เมื่อ args JSON เพี้ยน (ไม่ execute ด้วย args ว่างอีกต่อไป); GeminiService ใช้ parser ตัวเดียวกัน
  - **[F3 Accuracy]** `IntentClassifier` เปลี่ยนเป็น word-boundary regex (ASCII) + weighted scoring (keyword ยาว/เฉพาะเจาะจง = น้ำหนักสูง) แก้ false positive เช่น "import" ⊂ "important"
  - **[F4]** `LiveToolBridge` Path B เปลี่ยนจาก regex parse เป็น kotlinx.serialization (รองรับ nested args/number/array)
  - **[F5]** `recall_memory` route ผ่าน `SideEffectDelegate.onRecallMemory` (embedding semantic search) เป็นหลัก — เดิมใช้ substring match ธรรมดา
  - **[F6/F10 Refactor]** สร้าง `ai/TradingToolPolicy` รวม trading tool filter logic ที่เคยซ้ำ 3 จุด — Orchestrator + GeminiService ใช้ policy เดียวกัน (tool spec filter, strict MT5 suppression, policy label)
  - **[F7]** `ToolRegistry` custom tool/skill maps เปลี่ยนเป็น copy-on-write (กัน race ตอนอ่านพร้อมกัน)
  - **[F8]** `JarvisMemoryManager`: ลบ dead code, เพิ่ม name blacklist กัน extractName false positive, SleepCycle archive raw transcript ก่อนลบ messages (กันข้อมูลดิบหายถาวร)
  - **[F9]** ลบ `JarvisPlanner.kt` (dead code — สร้างใน Orchestrator แต่ไม่มี call site)
  - **[Long-term]** สร้าง `tools/trading/TaIndicators` (SMA/EMA/RSI/ATR/Bollinger/Stdev) เป็น TA library กลาง; `AdvancedTradingEngine` ใช้ร่วมแล้ว
- **Result**: คอมไพล์ผ่าน (`:composeApp:compileDebugKotlinAndroid`) — ระบบ AI ปลอดภัยขึ้น (file guard), เสถียรขึ้น (arg parser กลาง), ซ้ำซ้อนน้อยลง (policy/TA library กลาง)

## [2026-05-26] TradingView SMC Pine V8.3 Hardening
- **Trigger**: ตรวจสอบ `Pine Script/SMC & Multi-TF Order Blocks Sweeps V8.3.txt` สำหรับใช้งานบน TradingView Pine Script v6 พบว่ามีค่าหลายจุด hardcode และสคริปต์สร้าง `box` จำนวนมากโดยไม่ได้ประกาศ `max_boxes_count`
- **Implementation**:
  - เพิ่ม `max_boxes_count=500` และจำกัด FVG lookback ให้เหมาะกับเพดาน box ของ TradingView
  - เปิด input สำหรับ FVG, Liquidity, MTF Sweeps และ Premium/Discount zone เพื่อ tune จาก UI ได้โดยไม่แก้โค้ด
  - เพิ่ม guard สำหรับ OB scan depth, จำนวน Order Blocks/Liquidity Zones และ loop หา equal levels/MTF liquidity เพื่อหลีกเลี่ยงการ iterate ผิดทิศหรือกินทรัพยากรเกินจำเป็น
- **Verification**: ตรวจ quote balance, bracket/parenthesis balance และ diff เฉพาะไฟล์ Pine; ต้องนำไป paste/compile ใน TradingView Pine Editor เพื่อยืนยัน compiler จริง
- **Docs**: [[61_TradingView_SMC_Pine_V83_Hardening]]

## [2026-05-23] 🧱 V26.27 Deep Decoupling of AutoTradingService
- **Trigger**: ไฟล์ประสานงานหลัก `autoTradingService.ts` มีขนาดใหญ่เกินไป (5,429 บรรทัด) ซึ่งเป็นความเสี่ยงต่อการบำรุงรักษาและการทดสอบในระยะยาว จำเป็นต้องแยกตรรกะประมวลผลการทำงานหลักที่หนาแน่นออกไปเป็นโมดูลเฉพาะทางย่อย (Sub-Engines)
- **Implementation**:
  - สร้างโมดูลย่อย 3 ตัวใน `src/services/auto/core/` เพื่อแยกย้าย 4 เมธอดขนาดยักษ์:
    - `MarketCycleEngine.ts` — รับช่วงต่อ `runCycle` (ประมวลผลข้อมูลแท่งเทียน, สร้างแผนที่ SMC Walls, ตรวจสอบด่านกั้นตัดสินใจ, และส่งคำสั่งเทรด)
    - `PositionManagerEngine.ts` — รับช่วงต่อ `runManage` และ `executeManagementPlan` (จัดการและเฝ้าระวังออเดอร์, ขยับ BE/Trail, ดำเนินการ Early Invalidation/Proof Failure)
    - `LearningEngine.ts` — รับช่วงต่อ `runLearn` (รวบรวมสถิติการเทรดรายวัน และส่งต่อประสานการรวมหน่วยความจำ AI)
  - ปรับปรุง `autoTradingService.ts` ให้ทำหน้าที่เป็น Coordinator หลัก โดยยุบเหลือเพียง **777 บรรทัด** (ลดลง 85.6%) และสร้าง Wrapper methods เรียกผ่านตัวจับเวลา Loops ส่งมอบการทำงานไปให้ Sub-Engines พร้อมส่งผ่านบริบท `this` เพื่อความเข้ากันได้ย้อนหลัง 100% (Zero Breaking Changes)
  - แก้ไขปัญหา Circular Dependency โดยใช้การดึง Type-only (`import type`) สำหรับการอ้างอิงเชิงลึกในฝั่งเครื่องยนต์ย่อย
- **ผลลัพธ์และการรับรอง (Verification)**:
  - การรันคอมไพล์ TypeScript (`npm run build`) ผ่าน 100% ไร้ข้อผิดพลาด
  - ยูนิตเทสของระบบ (Vitest) จำนวน 103/103 เคส ผ่านครบถ้วนโดยสมบูรณ์
  - สคริปต์รายงานวิเคราะห์พอร์ต (`npm run analyze`) ทำงานดึงข้อมูลพอร์ตได้ครบถ้วนตามปกติ
- **Docs**: [[57_DeepDecoupling_AutoTradingService_V2627]]

## [2026-05-23] 🧱 V26.25 Unified Zone & Execution Quality Gate Simplification
- **Trigger**: โครงสร้างระบบ Trading Gates มีความซับซ้อนเกินไป (21 Gates) ทำให้เกิดกระบวนการปฏิเสธสัญญาณเทรดซ้ำซ้อน (Over-filtering) โดยเฉพาะการบล็อกซ้ำกันระหว่างพารามิเตอร์โซนราคากับด่านกั้นความเสี่ยง ณ จุดส่งคำสั่ง (Win Rate แข็งแกร่งแต่ Rejection Rate สูงถึง 98.8%)
- **Implementation**:
  - **Unified Zone Gate**: รวม `preAiMtfZoneGate` และ `zoneAwareGate` เข้าด้วยกันเพื่อพิจารณาโซนราคา HTF/LTF ในด่านเดียวอย่างมีประสิทธิภาพสูงสุด
  - **Execution Quality Gate**: รวม `entryDriftGate` และ `hardRiskGate` เพื่อความแม่นยำในการวัดความผันผวนและสเกลความคุ้มค่า ณ จุดเข้าทำจริง
  - **Backward Compatibility**: พัฒนาระบบ Mapping ย้อนหลังใน `strategySelector.ts` เพื่อแปลงค่าพารามิเตอร์เกตแบบดั้งเดิมไปหาเกตแบบบูรณาการใหม่ได้อย่างปลอดภัย 100% ป้องกันปัญหาระบบหยุดชะงัก (Zero Disruptions)
  - **Web UI & Backend Integration**: อัปเดต `dashboard.js`, `types.ts`, และ `MarketCycleEngine.ts` ให้ระบบ Dashboard และตัวประมวลผลหลักรันผ่านโมเดลควบคุมตัวใหม่นี้ทันที
- **ผลลัพธ์และการรับรอง (Verification)**:
  - ยูนิตเทส (Vitest) ทั้งหมด 103 เคสผ่านครบถ้วนสมบูรณ์ 100%
  - ระบบคอมไพล์ TypeScript (`npm run build`) บิวด์สำเร็จสมบูรณ์โดยไม่มี Error ใดๆ
  - ระบบรายงานพอร์ตและสคริปต์วิเคราะห์พอร์ต `npm run analyze` ทำงานอย่างถูกต้องปกติ
- **Docs**: [[55_UnifiedZoneExecutionQuality_V2625]]

## [2026-05-08] 🧱 V24.2.0 Context Levels (Pivot + VWAP + Fib)
- **Trigger**: Indicator audit (2026-05-08) พบช่องว่าง 30%+ — Pro retail ใช้ Daily Pivot Points, Session VWAP, Fibonacci levels เป็น standard SR confluence แต่ระบบมีแค่ SMC + classical (RSI/SMA/EMA/ATR/MACD/Stoch/BB) → entries และ TP มักวางที่ "SMC OB เท่านั้น" ข้าม pivots/fibs ที่ market ใช้จริง
- **Implementation** (~250 LOC, 1 ไฟล์ใหม่ + 4 ไฟล์แก้):
  - `mt5-core-server/src/services/auto/analyzers/levels.ts` (NEW) — calculator pure functions:
    - `computePivotLevels()` — Standard Floor Pivot P/R1-3/S1-3
    - `deriveDailyOhlcFromIntraday()` — derive D1 OHLC จาก H1 24-bar (ไม่ต้อง fetch D1 จาก MT5 เพิ่ม)
    - `computeSessionVwap(anchorHour=13UTC)` — NY equity-open anchored VWAP + ±1σ
    - `computeFibLevels()` — 0.236/0.382/0.5/0.618/0.786 + extensions 1.272/1.618
    - `buildContextLevels()` — aggregator → ContextLevel[] พร้อม weight 1-3
  - `analyzers/smc/types.ts` — extend `WallSourceType` ด้วย `'PIVOT' | 'VWAP' | 'FIB'`; `WallSource` รับ optional `label?` + `weight?`
  - `analyzers/smc/priceMapBuilder.ts` — เพิ่ม `ExtraContextLevel` type; `buildPriceMap()` รับ `extraLevels[]`; `buildWall()` +1★ ถ้า cluster มี high-weight context (Pivot R1/S1/P, VWAP, Fib 0.618)
  - `analyzers/smc/index.ts` — re-export `ExtraContextLevel`
  - `core/IndicatorPipeline.ts` — `buildContextLevelsFor()` คำนวณทุก cycle + log diagnostic: `Context levels — Pivot P=4714.67 R1=4746.33 S1=4689.33 | VWAP=4719.30 dev=+1.85 (47 bars) | Fib swing=[4683, 4740] 0.618=4704.77`
- **ผลกระทบ chain**:
  - PriceMap: walls แข็งขึ้น (+1★ เมื่อตรงกับ context สูงน้ำหนัก)
  - ProximityGate / ZoneAwareGate: detect "near support" ได้แม่นกว่า
  - V24.1.3 TP cap: suggestedTP (before first 3★+ wall) ใช้ context-stars layer ใหม่ → TP realistic กว่าเดิม
  - Stale-Stop: identify position camping near Pivot/VWAP ได้ไว
- **Coverage หลัง V24.2**: ระบบครอบคลุม indicator stack ที่ pro retail ใช้จริง ~85% (เดิม ~70%) — gap ที่เหลือคือ Volume Profile, Cumulative Delta, Funding Rate, On-chain
- **Files Changed**: ดู memory `project_v24_2_context_levels.md`

## [2026-05-08] 🎯 V24.1.3 PriceMap-Aware TP Cap
- **Trigger**: XAUUSD live trade — entry 4719.15, TP=4766.42 (RRR 7.84) แต่ระหว่างทางมี 4★ wall ที่ 4734.47 (M5+M15+M30+H1+H4) + 3-stack 4727/4731/4732 → TP ที่ตั้งไว้ไกลเกินไป ราคาน่าจะ reject ที่ 4734 ก่อนถึง TP ทำให้ position stall
- **Root Cause**: `autoTradingService.ts:1402-1414` หา TP จาก `smcSnap.bearOBs` (institutional Order Blocks) เท่านั้น — ข้าม swing/liquidity walls ที่อยู่ระหว่าง entry กับ OB ที่ใกล้สุด แม้ walls นั้นจะแข็ง 3-4★ ผ่านหลาย TFs
- **Fix**: ใช้ `analyzePath()` (มีอยู่แล้วใน `auto/analyzers/smc/priceMapBuilder.ts`) cap eaTp ให้อยู่ก่อน wall 3★+ แรก:
  - หลังคำนวณ `eaTp` จาก bearOBs → เรียก `analyzePath(eaPriceMap, last.c, 'UP'|'DOWN')`
  - ถ้า `eaTp > path.suggestedTP` (overshoot) → replace ด้วย suggestedTP
  - Log: `TP capped by PriceMap: 4766.42 → 4722.50 suggested=4722.50 conservative=4716.30 (obstacle 3★ @ 4722.95)`
- **Files Changed**: `mt5-core-server/src/services/autoTradingService.ts` (import + EA-Only TP block)
- **คาด**: TP realistic หลายเท่า → BE/Trail trigger ได้จริง → win rate เพิ่ม

## [2026-05-08] 🩹 V24.1.2 Dynamic BE Buffer (spread-aware)
- **Trigger**: หลัง V24.1.1 deploy — XAUUSD spread=32pts (0.32 USD) แต่ BE buffer hardcoded 0.5 USD = แค่ 1.56× spread → BE ขยับ SL ใกล้ entry มาก (4719.44 → 4719.65 = 21 cents) เสี่ยงโดน spread widening เคาะปิดทันที กำไรเหลือศูนย์หรือติดลบ
- **Root Cause**: `buffer = position.symbol.includes('XAU') ? 0.5 : 0` ไม่สนใจ spread จริงของ broker — symbol เดียวกันแต่ broker คนละแบบ spread ต่างกัน 5-10 เท่า
- **Fix**: ใช้ `spreadBaseline.baseline(symbol)` ที่ track median spread มาคำนวณ dynamic buffer:
  - `buffer = max(2.5 × baseline_spread_price, fallback)` ใน BE block และ CPP block
  - `spreadBuffer (10016 guard) = max(2.0 × baseline_spread_price, fallback)`
  - `trailDistance (ATR fallback) = max(legacy, 3.0 × baseline_spread_price)` กัน trail tight เกิน
  - BE log แสดง `buffer= spreadBaseline=Xpts=Y.YY` ให้ตรวจสอบ math ได้
- **Math example** (XAU spread=32pts, tick=0.01): baseline_price = 0.32 USD → buffer = max(2.5×0.32, 0.5) = **0.80 USD** → SL = entry+0.80 → lock net profit ≥0.48 USD หลัง spread cost
- **Files Changed**: `mt5-core-server/src/services/autoTradingService.ts` (BE block, CPP block, ATR trail block, BE log message)

## [2026-05-08] 🩹 V24.1.1 Stale-Stop Hotfix (post-deploy)
- **Trigger**: Live log หลัง V24.1 deploy แสดง Stale-Stop block ไม่ทำงาน — XBT#151440996 ค้างที่ R=-0.07 อายุ 173 นาที ไม่ถูกปิด ขณะที่ patches อื่น (BE/TRAIL 0.5/0.6, SMC-Trail, MTF-aware exemption, ProximityGate hardened) ทำงานครบ
- **Root Causes** (2):
  1. **Clock skew**: `p.openedAtMs` จาก MT5 broker อาจสูงกว่า server `Date.now()` (UTC offset / network delay) → `nowMs - openedAt` เป็นค่า **ลบ** → เงื่อนไข `ageMs >= staleMaxAge` false ตลอด → gate disabled แบบเงียบ. ปัญหาเดียวกับที่ orphan-backfill เคย comment ไว้ที่ L2677
  2. **Log invisibility**: ใช้ `atWarn(...)` ส่ง Stale log แต่ user filter อาจ strip warn → ผม/ผู้ใช้ debug ไม่เห็น
- **Fixes**:
  - `mt5-core-server/src/services/autoTradingService.ts` Stale-Stop block:
    - ใช้ `Math.min(p.openedAtMs, j.createdAt)` (timestamp เก่ากว่า) แทน `??` chain
    - `ageMs = Math.max(0, nowMs - openedAt)` clamp ค่าลบ
    - เปลี่ยน `atWarn` → `atLog` (stale-exit ปกติไม่ใช่ warning)
    - เพิ่ม per-cycle summary `Stale-Stop swept N pos → candidates=X closed=Y (maxAge=Mm, deadZone=[a,b])` เพื่อ confirm gate ทำงานทุกรอบแม้ไม่มี candidate
- **Impact คาด**: รอบถัดไป log จะเห็น `Stale-Stop swept 3 pos → candidates=2 closed=2` และ position ค้างใน R∈[-0.25, +0.30] นาน >45m จะถูกปิดจริง
- **Docs**: memory `project_v24_ea_manager_overhaul.md` (V24.1.1 section)

## [2026-05-08] 🚑 V24.1 EA-Only Manager Overhaul (8 fixes)
- **Trigger**: Live log analysis (08-05 12:15–12:37, 22 cycles) แสดงพฤติกรรมขัดแย้งกับกราฟ XAUUSD/XBTUSD — Bias=BEAR ค้าง 22 cycles แม้ M15 chart BMS bullish; BE/TRAIL ไม่เคย activate; SELL position #151307441 ขาดทุน -0.6R โดยไม่มี exit; ProximityGate BYPASSED ใน RANGING regime
- **Root Causes พบ**:
  - `computeSmcTrailTargets` + `detectRegimeFlipAction` import แล้วไม่เคยถูกเรียก (dead code)
  - `breakEvenTriggerR=1.0R` / `trailAfterR=1.0R` สูงกว่า R-distribution จริง (สูงสุด +0.1R)
  - Counter-trend gate ใช้ M15 bias เดียว ไม่สนใจ H4 → block valid reversal
  - ProximityGate bypass สำหรับ BREAKOUT strategy แม้ H4=RANGING (false breakout)
  - ไม่มี time-based exit สำหรับ position ค้างใน mid-zone
- **Fixes (Files Changed)**:
  1. `mt5-core-server/src/services/auto/core/PersistenceService.ts` — `breakEvenTriggerR: 0.5`, `trailAfterR: 0.6`, +`stalePositionMaxAgeMs/MinR/MaxR`, migration ลด config เก่า ≥1.0 → defaults ใหม่
  2. `mt5-core-server/src/services/auto/types.ts` — เพิ่ม `stalePositionMaxAgeMs?`, `stalePositionMinR?`, `stalePositionMaxR?` ใน `AutoTradingConfig`
  3. `mt5-core-server/src/services/autoTradingService.ts`:
     - Manager audit: BE/Trail trigger scaled (scalp 0.3R / swing 0.5R) + position age (`age=Nm`)
     - Stale-Position Time Stop block: ปิด position ค้างใน R∈[-0.25, +0.30] นานกว่า 45 นาที
     - SMC-Aware Trail block: เรียก `computeSmcTrailTargets()` + `getSmcSnapshotV2()` ทุก cycle ต่อ symbol
     - Regime-Flip Exit block: เรียก `detectRegimeFlipAction()` ปิด losing leg เมื่อ CHoCH against cluster
     - ATR fallback trail skip ถ้า `smcAlreadyTrailed=true`
     - ProximityGate bypass: ต้อง H4=TRENDING_*/VOLATILE_BREAKOUT เท่านั้น (ไม่ bypass ใน RANGING)
     - Counter-trend gate (deterministic + post-AI): MTF-aware — ยกเว้นถ้า `analyses['H4'].bias` agree กับ intended side
- **Impact คาด**:
  - Position ที่ค้างไม่ขยับ → ปิดอัตโนมัติภายใน 45 นาที (ลด opportunity cost)
  - กำไรเล็กน้อยถูก lock เร็วขึ้น (BE/TRAIL ที่ 0.5/0.6R)
  - SL trail ไปขอบ OB/FVG จริง (ไม่ใช่สูตร ATR คงที่)
  - Regime flip บน H4 → ปิด losing leg ก่อน SL hit
  - BUY-on-pullback ใน H4=BULL ไม่ถูกตีเป็น counter-trend
- **Verify**: bash mount เป็น snapshot frozen ที่ session start → run `npx tsc --noEmit` บน Windows host เพื่อ verify
- **Docs**: [[01_Architecture/04_AutoTrading_Workflow]] (TBA), memory `project_v24_ea_manager_overhaul.md`

## [2026-05-03] 🔧 OpenAI Model Listing Filter Fix
- **Action**: เปลี่ยน OpenAI `listModels()` จาก allowlist-by-prefix เป็น blocklist — exclude non-chat models แทน
- **Root Cause**: Filter เดิมกรองเฉพาะ `gpt*|o1*|o3*|chatgpt*` แต่ OpenAI มี models ใหม่ (`o4-mini`, `codex-*`, `gpt-5*`) ที่หลุด filter → โมเดลไม่ขึ้นในหน้า Settings
- **Files Changed**: `OpenAILlmProvider.kt`
- **Context**: Providers อื่น (OpenRouter, Claude, Gemini) ไม่มี filter เข้มจึงไม่เจอปัญหา

## [2026-05-03] 🔧 Streaming Tool-Calls Delta Accumulation Fix
- **Action**: แก้ 3 providers (OpenRouter, OpenAI, LiteLLM) — streaming `delta.tool_calls` ถูกสร้างเป็น `LlmToolCall` ใหม่ทุก SSE chunk แทนที่จะสะสมตาม `index`
- **Root Cause**: OpenAI streaming ส่ง tool_calls เป็น delta chunks หลายอัน (chunk แรก = id+name, chunks ต่อมา = arguments ทีละส่วน) — โค้ดเดิมสร้าง object ใหม่ทุก chunk → Orchestrator concat ทั้งหมด → serialize กลับ API มี `{id:'', type:'function'}` ไม่มี `function` field → 400 Bad Request
- **Fix**: เพิ่ม `toolCallAccum: MutableMap<Int, Triple<String, String, StringBuilder>>` สะสม deltas ตาม index, emit batch เดียวหลัง stream จบ
- **Files Changed**: `OpenRouterLlmProvider.kt`, `OpenAILlmProvider.kt`, `LiteLlmProvider.kt`
- **Context**: พบจาก model `tencent/hy3-preview` ผ่าน SiliconFlow provider ใน OpenRouter

## [2026-05-03] 📦 ONNX Runtime 1.19.2 → 1.23.0 (16 KB Page Size)
- **Action**: อัปเกรด `onnxruntime-android` เพื่อ comply กับ Google Play 16 KB page size requirement (บังคับ Nov 2025)
- **Files Changed**: `composeApp/build.gradle.kts`, `.obsidian-wiki/02_Components/LocalEmbeddingSystem.md`
- **Context**: `libonnxruntime.so` + `libonnxruntime4j_jni.so` ใช้ 4 KB LOAD alignment ใน v1.19.2; v1.23.0+ build ด้วย 16 KB alignment

## [2026-05-01] 🔍 V20.0 Agent Coordination Audit & Wiki Restructure
- **Action**: ตรวจสอบ flow ทั้งระบบ Agent 4 ตัว, สร้าง Interaction Matrix, ค้นหา Bug
- **Result**: พบและแก้ไข slTpAgent double-record ใน ModelRanker
- **Result**: แก้ modelUsed format (ไม่มี provider prefix ปน), แก้ agents ทุกตัวที่ใช้ split(' ')
- **Result**: เพิ่ม fallback → Settings auto-persist ให้ Dashboard สะท้อนโมเดลที่ใช้งานจริง
- **Result**: ปรับ Wiki ทั้งหมด — index, overview, MOC, Current_Tasks ให้เชื่อมโยงกัน
- **Docs**: [[18_V20_Agent_Coordination_Audit]], [[17_PerAgent_ModelRanking_V20]]
- **Context**: V20.0 เสถียร, ระบบเทรดทำงาน 24/7, 8+ positions managed

## [2026-05-01] 🛡️ V20.0 Per-Agent Model Ranking Stabilization
- **Action**: Hard Risk Gate fix (float-safe RRR compare + 1-tick TP buffer)
- **Action**: Toxic Model Protection (streak≥5 exempt from force-reset)
- **Action**: slTpAgent inheritance from reasoning model (prevent 403)
- **Action**: slTpAgent เพิ่มใน Dashboard Settings UI
- **Result**: ระบบ AI Pipeline ทำงานเสถียร ไม่มี false-positive RRR rejection
- **Result**: Smart Fallback rotate โมเดลอัตโนมัติเมื่อเจอ 429/403
- **Docs**: [[17_PerAgent_ModelRanking_V20]], [[Smart_Fallback_and_Token_Management]]
- **Context**: แก้ไข bugs ที่ทำให้ trades ถูก reject ผิดพลาด และ model loop ซ้ำ


## [2026-04-15] 🚀 เริ่มต้นระบบ Obsidian LLM-Wiki
- **Action**: ติดตั้งโครงสร้างไดเรกทอรีพื้นฐาน `.obsidian-wiki/`
- **Result**: สร้างหน้า `index.md`, `log.md`, และ `schema.md` สำเร็จ
- **Context**: เริ่มเปลี่ยนจากการใช้ไฟล์คู่มือแบบ Single-file (`jarvis_vision_manual.md`) มาเป็นระบบ Wikibase กระจายศูนย์ตามแนวคิดของ Karpathy

## [2026-04-15] 📥 Ingest Android Skills from GitHub
- **Action**: ดึงข้อมูลจาก `android/skills` และกระจายลงในโฟลเดอร์ `05_Android_Skills/`
- **Result**: เพิ่มหน้ารวม 6 Skills สำคัญ (AGP 9, Compose Migration, Navigation 3, R8, Play Billing, Edge-to-Edge)
- **Context**: ยกระดับความรู้ในระบบให้เป็นมาตรฐานล่าสุดของ Google เพื่อลดความเสี่ยงในการเขียนโค้ดที่ผิดพลาด


## [2026-04-15] 🌌 Ingest Gemini Skills from GitHub
- **Action**: ดึงข้อมูลจาก `google-gemini/gemini-skills` และติดตั้งใน `06_Gemini_Skills/`
- **Result**: เพิ่ม 2 Skills สำคัญ: `gemini-api-dev` และ `gemini-live-api-dev` (โมเดลตระกูล 3.1)
- **Context**: ยกระดับระบบ JARVIS ให้รองรับ SDK ล่าสุด และมาตรฐาน Gemini 3.1 เพื่อประสิทธิภาพการตอบสนองที่ล้ำสมัยที่สุด


## [2026-04-15] 📈 Upgrade to Trading Intelligence V10.0 (The Bible)
- **Action**: บูรณาการเครื่องมือเทรดทั้ง 18 ชนิด เข้ากับกลยุทธ์ Wyckoff, ICT และ SMC V10
- **Result**: สร้าง 7 ไฟล์ความรู้ใหม่ในหมวด `07_Trading_Intelligence/` พร้อม Checklist SOP
- **Context**: ยกระดับ JARVIS จากผู้ช่วยทั่วไปสู่ "นักวิเคราะห์สถาบัน" ที่ใช้กระบวนการวิเคราะห์แบบ Top-Down ขั้นสูง

---
> [!TIP]
> รูปแบบการบันทึก: `## [YYYY-MM-DD] | ประเภทกิจกรรม | หัวข้อเรื่อง`

## [2026-04-27] 🛠️ Deep Analysis Stability & Recursive Loop Fix
- **Action**: อัปเกรด `GeminiService.kt` เพื่อเสถียรภาพในการวิเคราะห์ข้อมูลซับซ้อน (เช่น ทองคำ/XAUUSD)
- **Result**: ขยาย `maxRounds` จาก 5 เป็น 10 รอบ เพื่อรองรับการเรียก Tool ต่อเนื่องหลายขั้นตอน
- **Result**: เพิ่มระบบ **Force Final Summary** — หากถึงขีดจำกัดรอบ (Max Rounds) ระบบจะบังคับให้ AI สรุปผลลัพธ์ทันที แทนการหยุดทำงานแบบดื้อๆ
- **Result**: เพิ่มระบบ **Context Truncation** สำหรับ Tool Results (>10k chars) ป้องกันข้อผิดพลาด "Request Entity Too Large"
- **Context**: แก้ไขปัญหา "0 chars response" และ "Internal Server Error" เมื่อ AI พยายามวิเคราะห์ข้อมูลจำนวนมากพร้อมกัน

## [2026-04-27] 🛠️ Advanced Analysis Suite & News Fallback Patch
- **Action**: อัปเกรด `TradingToolExecutor.kt` เพื่อรองรับเครื่องมือวิเคราะห์ขั้นสูงครบวงจร (V16.0+)
- **Result**: เพิ่มการรองรับ `trading_fundamental_analysis`, `trading_fear_greed`, และ `trading_mt5_trade_journal` ใน Mobile App
- **Result**: Patch ระบบ News Fallback สำหรับ XAUUSD (Gold) ให้สลับไปดึงข้อมูล Global Macro อัตโนมัติหากไม่พบข่าวเจาะจง
- **Result**: เพิ่ม Smart Base URL Detection ใน `executeFearGreed` เพื่อรองรับการสลับระหว่าง MT5 และ Auto-Trading endpoints
- **Context**: แก้ไขปัญหา "No news found" และ "No response" ที่พบในการทดสอบจริง เพื่อเสถียรภาพสูงสุดของ Deep Analysis Suite

## [2026-04-25] 🛡️ Institutional Defense Upgrade (V24.0)
- **Action**: Implement ระบบ **Zone-Aware Gate** (Premium/Discount blocking) และ **Dynamic SL Buffer**
- **Action**: อัปเกรดระบบ **Staged BE/Trail** (3 Stages) และ **Smart Scale-In Guard**
- **Action**: ติดตั้งระบบ **Pre-Analysis Prior** เชื่อมต่อ Vector Store เพื่อเรียนรู้จาก Setup ที่เคยผิดพลาดในอดีต
- **Result**: ระบบเทรดมีความแม่นยำระดับสถาบัน (Institutional Grade) และมีกลไกป้องกันพอร์ตเชิงรุก
- **Result**: เพิ่ม Metrics ข้อมูลเชิงลึก (`mt5_zone_gate_blocks_total` ฯลฯ) สำหรับการมอนิเตอร์ผ่าน Grafana และ Mobile UI
- **Context**: ยกระดับ JARVIS จากผู้ช่วยเทรดสู่ระบบ Semi-Autonomous Trader ที่มีความสามารถในการเรียนรู้และป้องกันความเสี่ยงอัตโนมัติ

## [2026-04-16] Fix: Wiki-guided Stability Hardening
- **Action**: Reviewed `.obsidian-wiki` architecture/component notes (`01_Architecture`, `02_Components`) and aligned code with the documented routing + persistence intent.
- **Result**: Patched Tool lifecycle so System diagnostics receives the latest side-effect delegate (prevents stale/null delegate after init order changes).
- **Result**: Added safety guards for file mutations (`file_delete`, `file_move`) to block unsafe root-level or out-of-scope path operations.
- **Context**: Based on `Architecture_Overview` (dual-path reliability) and `Memory_Strategy` emphasis on safe persistence behavior.

## [2026-04-16] Knowledge Base: Lightweight Charts Docs Ingested
- **Action**: Curated `.obsidian-wiki/08_lightweight-charts-docs-api` into a reusable knowledge hub.
- **Result**: Added `Knowledge_Hub.md` with integration rules, wrapper strategy, and implementation checklist.
- **Context**: This hub is now the reference for chart integration decisions in PersonalAIBot.

## [2026-08-04] 🔔 Alert System V2 — Actionable Notifications + Field Catalog
- **Action**: Notification ของ alert มีปุ่ม "🛑 หยุดแจ้งเตือน" / "🔁 แจ้งเตือนซ้ำ" (BroadcastReceiver ใน JarvisAutomationService) แทน msg box ที่ Android ไม่อนุญาตจาก background
- **Action**: หน้า Automation แสดงสถานะชัดขึ้น — "TRIGGERED · แจ้งแล้ว" / "ACTIVE · เฝ้าดูอยู่" พร้อมแสดงเงื่อนไขบนการ์ด
- **Action**: แก้ไขค่าเปรียบเทียบ (ราคาเป้าหมาย) + ความถี่ ได้แล้วผ่าน AlertEditDialog และ AI action `update` (query ใหม่ updateAlertJobCondition, ไม่มี schema change)
- **Action**: สร้าง AlertFieldCatalog (AutomationModels.kt) — dropdown สร้าง alert เลือกได้เฉพาะ tool/field ที่ background ดึงค่าได้จริง + validate ฝั่ง AI (automation_manage_alerts) กันตั้งเงื่อนไขมั่ว เช่น EMA cross (ยังไม่รองรับ)
- **Context**: ทดสอบจริง XAU 12:09-12:28 พบ alert ยิง 2 ครั้ง (Repeat mode ทำงานถูกต้อง) แต่ UI/AI ยังขาดการควบคุมและข้อมูล field

## [2026-08-04] ⚡ Alert System V2.1 — Adaptive Interval + Manifest Receiver + Async AI
- **Fix**: ปุ่ม notification กดไม่ได้ — ย้ายจาก dynamic receiver (ตายตาม service) ไปเป็น AlertActionReceiver ที่ manifest-declared + explicit PendingIntent ทำงานได้แม้แอปถูกฆ่า
- **Action**: Adaptive Interval — tick หลัก 30 วิ + เร่งเช็คอัตโนมัติเมื่อราคาใกล้เป้า (<0.1% → 30 วิ, <0.5% → 1 นาที, ไกล → ตามที่ตั้ง) รองรับทองที่วิ่งแรง
- **Fix**: แยก AI summary (Gemini ~10 วิ) ออกจาก loop เป็น scope.launch ขนาน — job ถัดไปไม่ถูกบล็อกเหมือนใน log 15:10

## [2026-08-04] 🔧 Alert System V2.2 — Data Source Audit + TA Fallback
- **Fix**: TA ของทอง 404 — TVC:XAUUSD ไม่มีใน TradingView scanner (ทดสอบ curl จริง) เพิ่ม fetchTechnicalAnalysisWithFallback ลอง OANDA → FX_IDC → TVC ใน service
- **Audit ทุก source (curl จริง)**: TV scanner (OANDA/FX_IDC/BINANCE) ✅, alternative.me F&G ✅, CoinGecko ✅, Reddit ❌ 403 (mark ⚠️ ใน AlertFieldCatalog + tool description ให้ AI หลีกเลี่ยง)
- **Fix**: ปุ่ม notification เปลี่ยนจาก icon=0 เป็น drawable จริง (ic_menu_close_clear_cancel / ic_menu_rotate)
- **Build**: assembleDebug ผ่าน → composeApp/build/outputs/apk/debug/composeApp-debug.apk (ต้องติดตั้ง APK ใหม่ก่อน ปุ่ม/ฟีเจอร์ถึงจะทำงาน)

## [2026-08-04] 🧪 Alert System V2.3 — Field Audit (curl จริง) + WS Fix + Operator Validation
- **Audit field TA จริง**: BB.width / buy_signals / sell_signals / neutral_signals คืน null เสมอจาก scanner → ตัดออกจาก AlertFieldCatalog + tool descriptions
- **Fix**: service client ไม่ได้ install WebSockets → SmcApiService (TV websocket) timeout ค้าง ~13 วิ × 3 exchange = ~40 วิ/เช็คสำหรับ deep_analysis_suite — เพิ่ม install(WebSockets)
- **Action**: เพิ่ม isNumeric ใน AlertFieldOption — field ข้อความ (direction/signal/lsdState/deltaLabel/momentum/sentiment_label/classification) บังคับ operator เฉพาะ == / contains ทั้ง UI dropdown และ AI validation (กันเคส lsdState >= 4000 ใน log)

## [2026-08-04] 🧮 Alert System V2.4 — IndicatorAlertProvider (คำนวณเองจากแท่งเทียน)
- **ปัญหา**: TV scanner ให้ค่าจำกัด (EMA/Stoch/CCI/BB upper-lower = null) แต่มีแท่งเทียน 300 แท่ง cache ใน DB จาก SmcApiService
- **Action**: สร้าง automation/IndicatorAlertProvider.kt — คำนวณเอง: EMA20/50/200, EMA cross state (GOLDEN_CROSS/DEATH_CROSS/BULLISH/BEARISH), EMA spreads, RSI14, MACD+signal+hist, Stoch K/D, CCI20, BB upper/basis/lower/width, ATR14 (สูตร Wilder/มาตรฐาน TV)
- **Action**: tool_name ใหม่ "trading_indicators" — เลือก timeframe ได้ด้วย suffix symbol@TF เช่น XAUUSD@15m (1m/5m/15m/30m/1h/4h/1D, default 1h)
- **Action**: ลงทะเบียนใน AlertFieldCatalog (INDICATORS เป็นอันดับแรก) + tool descriptions ทั้ง 2 จุด ให้ AI ใช้เป็นตัวหลัก
- **Fix**: MACD NaN propagation — slice series จาก index 25 ก่อนคำนวณ signal line

## [2026-08-04] 🧠 Alert System V2.5 — SmcAlertProvider + ยืนยัน incremental candles
- **ยืนยันแล้ว**: แท่งเทียนเป็น incremental จริง (fetchCandlesWithSource: cache DB → estimateMissingBars → ดึงเฉพาะ delta → merge → save → trim) เรียกถี่ได้ไม่เปลือง
- **Action**: สร้าง automation/SmcAlertProvider.kt — tool_name "trading_smc" แปลง SmcAnalysisResult เป็น alert fields: smc_zone (PREMIUM/DISCOUNT), smc_zone_pct, smc_trend, smc_last_event (BOS/CHOCH), structure high/low, equilibrium, premium_bot/discount_top, bull_ob_dist, bear_ob_dist, fvg_dist, liq_above/below_dist+stars, attack_force, atr
- **Action**: ใช้ strictTvSource=false ใน alert path (ยอม fallback Yahoo/Binance ไม่ให้ alert error เมื่อ TV ws ล่ม) + ลงทะเบียน catalog + tool descriptions

## [2026-08-04] 🎨 Alert Create Dialog Redesign
- **Fix**: ช่อง "ตรวจสอบทุกกี่นาที" ถูกตัดทำให้กรอกไม่ได้ — เนื้อหา dialog ทั้งหมดใส่ verticalScroll แล้ว
- **Action**: จัด layout กระชับ: symbol+ชื่อแถวเดียว, operator+ค่าแถวเดียว, spacing 6dp, เอา description ยาวออกเหลือ hint สั้น, dropdown สูงสุด 420dp + scroll (field SMC มี 18 ตัว)
- **Action**: default tool เป็น trading_indicators (คำนวณเอง แม่นสุด) + auto-fill ตัวอย่างสำหรับ field ใหม่ (rsi14, smc_zone_pct, ema_cross_state)

## [2026-08-04] 🐛 Fix Dropdown Crash ในหน้าสร้าง Alert
- **สาเหตุ**: ใส่ verticalScroll + heightIn บน DropdownMenu — popup วัดความสูงด้วย infinity constraint → scrollable child crash ทันทีที่กดเปิด (แอปเด้ง)
- **Fix**: เอา modifier ทั้งสองออก — DropdownMenu มี scroll ในตัวอยู่แล้วเมื่อรายการยาว

## [2026-08-04] 🧪 Alert Auto Test + Preset Chips
- **Action**: ปุ่ม "🧪 ทดสอบ" ในหน้า Automation — AlertDataTester ไล่ดึงข้อมูลทุก tool (price, TA, indicators, smc, deep_suite, fear_greed, crypto_overview, sentiment) พร้อม symbol ทดสอบ XAUUSD แสดงสถานะสดในแอป + log ละเอียด tag AlertDataTest (✅/❌ ต่อ tool พร้อมจำนวน field และตัวอย่างค่า)
- **Action**: แถว Preset ลัดในหน้าสร้าง Alert 11 แบบ (ราคาถึงเป้า / RSI OB-OS / Golden-Death Cross / โซน Discount-Premium / Bollinger Squeeze / ADX แรง / Extreme Fear / High Confluence) กดแล้วเติม tool+field+op+value ให้อัตโนมัติ

## 2026-08-05 (เช้า) — แก้ช่องว่าง service self-stop
- พบปัญหา: JarvisAutomationService stopSelf() เมื่อไม่มีงาน 5 รอบ แต่ไม่มีกลไกปลุกกลับตอนสร้าง alert ใหม่ (MainActivity start service เฉพาะตอนเปิดแอป) → alert ที่สร้างขณะ service ดับจะไม่ถูกเช็ค
- แก้: เพิ่ม expect/actual `wakeupAutomationService()` (automation/AutomationServiceWakeup.kt) + AndroidContextHolder ตั้งค่าจาก MainActivity.onCreate
- AutomationManager เรียก wakeup ใน registerJob, registerScheduledTask, setJobActive(true), resetTrigger, updateCondition
- Build :composeApp:assembleDebug ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-05 (เช้า) — แก้ชื่อ Alert ได้แล้ว
- ปัญหา: ชื่อ job (เช่น "xxx") ถูกใช้ในหัว notification "Jarvis Alert: xxx" และ AI พูดชื่อนั้นตอนแจ้งเตือน
- เพิ่ม query `updateAlertJobName` (JarvisDatabase.sq) + `AutomationManager.renameJob()`
- AlertEditDialog เพิ่มช่องแก้ชื่อ (บันทึกพร้อมค่า/interval ในปุ่มเดียว), wire onRename ใน App.kt
- Build ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-05 (เช้า) — rename ผ่าน AI + deep suite 5 มิติ + @TF ทุก tool วิเคราะห์
- AI manage_alerts เพิ่ม action `rename` (JarvisOrchestrator + declarations ทั้ง 2 จุด) — สั่ง "เปลี่ยนชื่อ alert ID 5 เป็น ..." ได้
- Deep suite เดิม map แค่ 3-5 ฟิลด์และ hardcode TF 1h → ตอนนี้ครบ 9 ฟิลด์ (summaryScore, lsdState, lsdConfluenceTF, deltaLabel, deltaValue, fiboScore, momentum, isSqueeze, close) และ parse @TF จาก symbol เหมือน indicators/smc
- พบบั๊ก: TradingApiService.getTechnicalAnalysis รับ interval แต่ไม่เคยใช้ → alert TA ติด 1h เสมอ; แก้ด้วย suffix คอลัมน์ TV scanner (RSI|15 ฯลฯ) + fetchTechnicalAnalysisWithFallback parse @TF
- อัปเดต AlertFieldCatalog + AlertDataTester ให้ตรงกัน
- Build ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-08 — UI หน้าสร้าง Alert ใหม่ + preset deep suite
- จัด layout เป็น 3 ส่วนชัดเจน: 1) สิ่งที่เฝ้าดู (Symbol/ชื่อ) 2) เงื่อนไข (tool/field/op/ค่า) 3) ความถี่ — หัวข้อสี cyan, spacing สม่ำเสมอ, dropdown ตัดข้อความยาว ellipsis กันกล่องยืด
- เพิ่ม preset: LSD ขาขึ้น, Squeeze Breakout, แรงซื้อนำ (Delta) — auto-fill เพิ่ม lsdState/isSqueeze
- Build ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-08 — แก้ toggle ไม่จำค่า + ย่อการ์ดตั้งค่าแจ้งเตือน
- บั๊ก: setAlertVoiceEnabled/AI summary เขียนลง AppSetting แต่ loadSettings() ไม่เคยอ่านกลับ → toggle เด้งเป็นค่า default ทุกครั้งที่เปิดแอปใหม่; เพิ่มโหลด alert_ai_summary/alert_voice ใน loadSettings
- AlertSettingsCard จากการ์ดใหญ่ 2 แถว → แถวเดียวกะทัดรัด (ไอคอน ⚙️ + CompactToggle 2 ตัว, switch scale 0.7)
- Build ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-08 — หมวด 5 Tool System / การสร้าง Tool ✅
- **AI สร้าง tool เองไม่ได้ (ข้าม session)** — root cause: `JarvisOrchestrator.loadCustomTools()` ไม่มี caller เลย; tool ที่สร้างผ่าน system_create_agent_tool ถูกบันทึกลง custom_agent_tools/ แต่ไม่เคยโหลดกลับ → เพิ่มเรียกใน JarvisViewModel init (IO dispatcher)
- Thread safety: เพิ่ม @Volatile ให้ _customTools/_skills ใน ToolRegistry (copy-on-write เดิมกัน corruption แล้ว แต่ขาด visibility guarantee)
- JSON injection: ToolArgParser ปลอดภัยอยู่แล้ว — parse fail คืน null และ Orchestrator คืน error เข้า tool loop ถูกต้อง
- Audit การลงทะเบียน: พบ `trading_mt5_trade_journal` เปิดใช้ใน registry + มี executor แต่ **ไม่มี declaration** → Gemini มองไม่เห็น; เพิ่ม declaration แล้ว (type: decision/management/performance)
- นับ tool: declared 45, registry active 46, executor ครบ (automation_manage_* route ผ่าน delegate โดยตั้งใจ)
- Build ผ่าน — ต้องติดตั้ง APK ใหม่

## 2026-08-08 — หมวด 5 เทสเครื่องจริงรอบ 1: พบบั๊ก Duplicate declaration
- เทสจริง: สร้าง custom_gold_check สำเร็จ แต่ API Round 2 ตาย 400 "Duplicate function declaration found: custom_gold_check"
- root cause: registerCustomTool + registerSkill คู่กัน แล้ว getGeminiTool() ส่งทั้ง _customTools และ _skills ทำชื่อซ้ำ
- แก้: getGeminiTool ส่งเฉพาะ skill ที่ไม่มีชื่อใน _customTools
- สร้าง tool สำเร็จและ persist ลง /storage/emulated/0/custom_agent_tools/ ยืนยันแล้ว
- Build ผ่าน — รอเทสรอบ 2: สร้าง tool ใหม่แล้วคุยต่อต้องไม่ 400 + ปิดเปิดแอปแล้ว tool ยังอยู่

## 2026-08-08 — Custom Tool CRUD ครบวงจรผ่าน AI (หมวด 5 ต่อ)
- เพิ่ม `system_list_agent_tools` (ดูรายการ+logic ข้างใน) และ `system_delete_agent_tool` (ลบทั้งไฟล์+registry)
- แก้ไข tool: ใช้ `system_create_agent_tool` ชื่อเดิมเขียนทับ (แนบ hint ใน description แล้ว)
- SideEffectDelegate + JarvisOrchestrator: `onReadAgentTool`/`onDeleteAgentTool` ผ่าน fileHandler (file_read/file_delete ใน custom_agent_tools/)
- ToolRegistry: declarations ใหม่ 2 ตัว (routing ผ่าน isSystemTool `startsWith("system_")` อยู่แล้ว)
- Live mode: ยืนยัน custom tools ถูกส่งเข้า session ตอน connect (startLiveVoiceSessionWithMemory → LiveGeminiService.connectAndListen tools=...) — tool ที่สร้าง/ลบกลาง live session มีผลหลัง reconnect; chat mode เห็นทันทีทุก request
- Build :composeApp:assembleDebug SUCCESS — รอ user เทสบนเครื่องจริง (หมวด 5 ยัง 🔶)

## 2026-08-08 — แก้ deep suite ngrok + MT5 mode false positive
- ปัญหา: custom_gold_check เรียก trading_deep_analysis_suite แล้วได้ ngrok offline page (ERR_NGROK_3200) แทน fallback local engine
- สาเหตุ 1: bridge ngrok ตอบ error page โดยไม่ throw → เพิ่มตรวจ response.status + marker (ERR_NGROK / ngrok offline / HTML) แล้ว throw เพื่อตก fallback คำนวณจาก TV
- สาเหตุ 2: model ส่ง interval= แต่โค้ดอ่าน timeframe= → รองรับทั้งสอง key (แก้บั๊ก TF ติด H1 เสมอ)
- สาเหตุ 3: isMt5Prompt keyword กว้างเกิน (order/position/history/balance/snapshot) → โหมด MT5-only ติดเองตอนคุย SMC (order blocks) แล้ว suppress TV tools — ตัดเหลือเฉพาะคำชี้ broker account ชัดๆ
- เพิ่ม note ใน description ของ deep suite ว่ามี TV local fallback อัตโนมัติ
- Build SUCCESS — รอ user เทส custom_gold_check อีกรอบ

## 2026-08-08 — Circuit breaker สำหรับ deep suite bridge
- ปัญหา: bridge (ngrok) offline ค้าง แต่ทุก tool call ยังยิง HTTP ไปโดน 404 ทุกครั้งก่อน fallback (เสีย 0.5-2 วิ/ครั้ง หลาย TF ก็หลายรอบ)
- แก้: @Volatile deepSuiteBridgeDownUntilMs — bridge ล่มแล้วเปิด circuit 10 นาที รอบถัดไปข้าม HTTP ไป local engine (TV) ตรงๆ; bridge ตอบปกติเมื่อไหร่ reset circuit ทันที
- Refactor: แยก runLocalDeepSuite(symbol, tf, reason) ใช้ร่วมกันทั้ง circuit-open และ catch path
- Build SUCCESS — รอ user เทส: รอบแรกจะยังเห็น bridge failed 1 ครั้ง รอบถัดไปควรเห็น "circuit OPEN — skip HTTP"

## 2026-08-08 — หมวด 6: Orchestrator / Intent Routing
- JarvisPlanner: ไม่พบไฟล์/reference ใน codebase แล้ว (dead code ถูกลบไปก่อนหน้า) — ปิดประเด็น
- IntentClassifier tie-break: deterministic อยู่แล้ว (maxByOrNull บน LinkedHashMap ตามลำดับ enum — มี comment ยืนยันในโค้ด)
- รวม keyword ซ้ำซ้อน: ลบ trading keywords (mt5/xauusd/ทอง/สถานะตลาด ฯลฯ) ออกจาก ANALYSIS pattern — classify() boost ANALYSIS +3 ผ่าน TradingIntentUtility.isTradingPrompt ตัวเดียว (single source)
- อัปเดต ANALYSIS addon ที่เก่า: "[MT5/Trading Analysis Protocol]" บังคับ trading_mt5_analyze → เปลี่ยนเป็น TV-first protocol (deep suite/TA/SMC หลาย TF) ใช้ MT5 เฉพาะเมื่อ user ระบุชัด
- Tool loop ตรวจแล้ว: maxRounds=10, SSE error recovery, buffer text กัน hallucination, suppression wired ทั้ง GeminiService + Orchestrator external path; LiveToolBridge ไม่มี loop (Live API จัดการ function call เอง)
- Build SUCCESS — 🔶 รอ user เทสเครื่องจริง (วิเคราะห์ทองในแชท ควรยังได้ prompt ANALYSIS + ไม่เด้งไป MT5)

## 2026-08-08 — หมวด 9: File Tools / Security + ระบบแนบไฟล์ในแชท
- Review File Tools: path guard ครบทุก mutation (read/write/delete/move/analyze), canonicalize กัน traversal, extension allowlist สำหรับ write, JSON injection แก้ไปแล้ว (buildJsonObject)
- Hardening เพิ่ม: file_read จำกัด 200K ตัวอักษร (กัน context บวม), ห้าม file_delete โฟลเดอร์สาธารณะมาตรฐานทั้งโฟลเดอร์ (Download/Documents/DCIM/Pictures)
- ระบบแนบไฟล์ใหม่: ChatAttachment (common) + expect/actual rememberAttachmentPicker (Android: OpenMultipleDocuments + contentResolver + base64; iOS: stub no-op)
- UI ChatInputBar: ปุ่ม 📎 + chips แสดงไฟล์ที่เลือก (ลบทีละไฟล์ได้, สูงสุด 5 ไฟล์) ส่งได้แม้ไม่พิมพ์ข้อความ
- Wire ครบ: App.kt → ViewModel.sendMessage(text, attachments) → Orchestrator.chatWithHistory(attachments) → GeminiService.generateResponseWithTools(initialFiles) seed pendingFiles
- Text files (txt/md/csv/code ฯลฯ) ฝังเข้า prompt ตรงๆ (จำกัด 100K ตัวอักษร/ไฟล์); binary (รูป/PDF/DOCX สูงสุด 15MB) ส่งเป็น inline_data ให้ Gemini วิเคราะห์ native
- External provider path: attachments ถูกข้ามพร้อม log เตือน (ยังไม่รองรับ inline files)
- Build SUCCESS — 🔶 รอ user เทส: แนบรูป/PDF/txt แล้วถามสรุป

## 2026-08-08 — Review หมวด 10-15 ครบทุกหมวด
- หมวด 10 System Tools: ตรวจแล้ว — diagnostics/connectivity/agent-tool CRUD ทำงานถูก; เพิ่มเติม DiagnosticManager ตรวจจริง (เดิม automation check เป็น placeholder PASS เสมอ)
- หมวด 11 Camera/Vision: เพิ่ม vision provider sync — updateSettings เปลี่ยนโมเดลหลักแล้วกล้องสลับ provider ตามอัตโนมัติ (openai→GPT4O, claude→SONNET, อื่นๆ→GEMINI_LIVE)
- หมวด 12 UI/UX: ตรวจแล้ว state ผ่าน StateFlow/collectAsStateWithLifecycle ถูกต้อง ไม่พบ leak; ChatInputBar เพิ่งปรับใหม่พร้อม attach files
- หมวด 13 Database: แก้ dead queries — wire updateArchivalAccess เข้า searchRelevantFacts (นับ facts ที่ถูก recall), ใช้ getArchivalRecent ใน DiagnosticManager.checkDatabaseIntegrity
- หมวด 14 mt5-core-server: ทดสอบรัน scripts/advanced_analytics.mjs (npm run analyze) ผ่าน — ออกรายงาน gate analysis ครบ (15,796 cycles, top reject reasons); scripts อื่น: analytics:gate, analytics:audit, analyze:logs, analyze:deep พร้อมใช้
- หมวด 15 Diagnostic/Logging: ตรวจ sensitive data — API key ไม่หลุดเข้า log (log เฉพาะ length/isNotBlank), tool results ผ่าน sanitizeToolResultForLog; diagnostics ตอนนี้รายงานจำนวน jobs/tasks/archival จริง
- Build SUCCESS — 🔶 รอ user เทสเครื่องจริงรวม

## 2026-08-08 — แก้ AI มองไม่เห็นรูปแนบ (เรียก camera tool ผิดตัว)
- อาการ: แนบรูป (binary=1 ส่งถูกแล้ว) แต่ model เรียก camera_analyze_scene แทนการดู inline_data → error "camera is not active" แล้วตอบมั่ว
- แก้ 2 ชั้นใน GeminiService.generateResponseWithTools:
  1. effectiveIntentAddon — แนบ [USER ATTACHMENTS] note: ไฟล์อยู่ใน inline_data แล้ว วิเคราะห์ตรงๆ ห้ามเรียก camera tools
  2. excludeCameraTools — ซ่อน camera tools ออกจาก tool spec เมื่อมีไฟล์แนบ (buildRequestJson filter ผ่าน ToolRegistry.isCameraTool)
- Build SUCCESS — รอ user เทสแนบรูปอีกรอบ

## 2026-08-08 รอบ 2 — Model fallback + sanitize key + diagnostics
- Logger.kt: เพิ่ม sanitizeSensitive() (ลบ ?key=/Bearer token ออกจากข้อความ) + Logger.android.kt sanitize message/stacktrace
- ModelConfig: เพิ่ม GEMINI_FALLBACK_MODELS = [gemini-2.5-flash, gemini-2.5-flash-lite, gemini-3.5-flash-lite, gemini-3.1-flash, gemini-3.1-pro]
- GeminiService.generateResponseWithTools: สลับโมเดลอัตโนมัติเมื่อ 429/500/503/timeout (runtime-only ไม่ persist), emit แจ้งผู้ใช้ทุกครั้งที่สลับ, log SSE chunk ที่ไม่มี candidates + empty response พร้อม model/finishReason
- GeminiService.generateResponse (nested AI summaries): เพิ่ม timeout 20s (เดิมไม่มี ค้าง 90s ทำ custom tool ช้า) + sanitize error message
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL
- หมายเหตุความปลอดภัย: API key เต็มเคยหลุดลง logcat ผ่าน ktor exception — แก้ sanitize แล้ว แต่แนะนำ user rotate key

## 2026-08-08 รอบ 3 — Mask key หัว-ท้าย + fallback chain ตามโควต้าจริง
- Logger.kt: แยก 2 ระดับ — sanitizeSensitive (ลบทิ้ง สำหรับแชท/tool result/log ใน app) vs maskSensitiveForLogcat (โชว์หัว 8 ท้าย 4 เช่น key=AIzaSyBQ***ElLY) + maskApiKey()
- Logger.android.kt: logcat ใช้ mask หัว-ท้าย (user จงใจดู key ใน logcat สำหรับ debug)
- JarvisViewModel: log [Chat] Sending/Empty response แสดง apiKey แบบ mask + model ที่ใช้
- ModelConfig: fallback chain เรียงตามโควต้า free tier จริง — gemini-2.5-flash(RPD20) → 3.5-flash-lite(RPD500) → 3.1-flash-lite(RPD500) → 3-flash(RPD20) → 2.5-flash-lite(RPD20) → 3.5-flash(RPD20)
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 4 — Rework LLM Providers + Fallback Settings UI
- สร้าง OpenAiCompatLlmProvider (generic OpenAI-compatible) + GroqLlmProvider (api.groq.com/openai/v1, default llama-3.1-8b-instant RPD 14.4K, ตัด whisper/prompt-guard/tts) + NvidiaNimLlmProvider (integrate.api.nvidia.com/v1, default meta/llama-3.1-8b-instruct, ตัด embed/rerank/audio/image-gen)
- Settings UI: เอา OpenAI/Claude/Vertex ADK ออกจาก ProviderMetas เหลือ Gemini/OpenRouter/Groq/NVIDIA NIM/MiniMax; key flows ใหม่ groq_api_key, nvidia_nim_api_key (DB + ViewModel + orchestrator wiring ครบ)
- หน้า Settings เพิ่ม Section "Gemini Fallback Models": แก้ลำดับ chain เอง (1 บรรทัด=1 โมเดล) เก็บ setting gemini_fallback_models, GeminiService.fallbackModelsOverride, empty=default; ปุ่ม Reset default/ล้าง
- Free filter: ใช้ได้กับ openrouter/groq/nvidia_nim (freeFilterProviders); OpenRouter isFree เพิ่มเช็ค suffix ":free" กัน model ฟรีหลุด filter
- ApiKeyTester: validate gsk_/nvapi- prefix + test listModels ของ groq/nim
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 5 — Gemini Multi API Key (free tier หลายเมล์)
- ViewModel: geminiApiKeys StateFlow + CRUD (add/remove/edit) persist setting "gemini_api_keys" (newline-separated); merge primary key เป็นหัว rotation chain เสมอ
- Orchestrator.updateGeminiApiKeys → GeminiService.apiKeysOverride
- GeminiService: key rotation ก่อน model fallback — 429/503/timeout → ลอง key ถัดไป (โมเดลเดิม, emit แจ้ง masked key) → key หมดค่อยสลับโมเดล; runtime-only ไม่ persist
- Settings UI: ไอคอนกุญแจเล็กข้างชื่อ Gemini card (สีเขียวเมื่อมี key สำรอง) เปิด GeminiKeysDialog — list keys แบบ mask หัว-ท้าย, tag PRIMARY, ปุ่ม edit (inline textfield + ✓/✗) / delete / add ต่อแถว; persist ทันทีไม่ต้องกด Save
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 6 — Cross-provider fallback (ชั้นสุดท้าย)
- ตรวจสอบยืนยัน: LiveToolBridge execute tools เองผ่าน ToolExecutor ตรงๆ ไม่ผ่าน chat model → Live mode (voice) ไม่กระทบเมื่อสลับ chat provider; external provider path (chatWithExternalProvider) ส่ง tools ผ่าน ToolRegistry อยู่แล้ว → Groq/NIM/OpenRouter ใช้ tools ของเราได้
- GeminiService.lastFatalError: เซ็ตเมื่อ Gemini ตายทั้ง chain (ทุก key + ทุกโมเดล) reset ทุกรอบใหม่
- Orchestrator.chatWithHistory: gemini branch ห่อ flow — หลัง collect จบถ้า fatal → chatWithCrossProviderFallback ไล่ Groq → NIM → OpenRouter → MiniMax; เลือก model จาก listModels(freeOnly) ที่ supportsFunctions; buffer ก่อน emit (chunk มี ⚠️ = fail ไล่ต่อ); emit แจ้งผู้ใช้ทุกขั้น
- chatWithExternalProvider เพิ่ม modelOverride; OpenAiCompatLlmProvider.listModels ใช้ key ตัวเองก่อน (กัน registry ส่ง gemini key มาผิด)
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 7 — แก้ Groq/NIM ใช้ไม่ได้ (จาก log จริง 13:04-13:09)
- ปัญหา: Groq 413 (payload 13K tokens > TPM 8K free tier), NIM 404 (models ที่ list ได้แต่ account ไม่มีสิทธิ์), tools ไม่ยิง
- chatWithExternalProvider: maxCoreContextChars 15000→6000, history 20→10 turns, tools จำกัด 12 ตัว + description ตัด 200 ตัวอักษร
- Degraded retry: รอบ 1 เจอ error (ขึ้นต้น ⚠️) หรือ timeout → ลองใหม่ครั้งเดียวแบบไม่ส่ง tools + history เหลือ 3 turns (แจ้งผู้ใช้ "ลองใหม่แบบประหยัดโควต้า")
- Cross-provider fallback: ไล่สูงสุด 3 candidate models ต่อ provider (จัดลำดับ preferred: llama-3.3-70b/gpt-oss-120b/compound/70b/120b/llama/qwen) กัน NIM 404 ตัวแรกแล้วตายทั้ง provider
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 8 — แก้ Groq tool loop / 429 transient / empty round (จาก log 13:33-13:38)
- Groq excludeModelPattern เพิ่ม orpheus|canopylabs (TTS ต้อง accept terms) + safeguard (safety classifier วน tool จนตอบว่าง)
- Tool-call loop detection: signature เดิมซ้ำ 2 รอบติด → บังคับสรุปโดย tools=null รอบถัดไป (เก็บผล tool ใน messages) + ส่ง tool result "ห้ามเรียกซ้ำ" — เคสจริง gpt-oss เรียก search_web 5 รอบจน 0 chars
- 429 transient backoff: parse "try again in X s" → delay X+1s ลองรอบเดิมสูงสุด 2 ครั้ง ก่อน degrade (Groq TPM reset ทุก 1-5 วิ)
- Empty round (0 chars ไม่มี tool) → degraded retry ครั้งเดียว กัน Empty response เงียบ (เคส qwen3.6-27b)
- Build: :composeApp:assembleDebug BUILD SUCCESSFUL

## 2026-08-08 รอบ 9 — Auto-test โมเดล Groq/NIM + log เนื้อคำตอบ AI
- JarvisVM: log [Chat] Response complete แสดง preview 800 ตัวอักษรจริง + masked key (เดิมโชว์แค่จำนวน chars)
- OpenAiCompatLlmProvider.generate(): ส่ง tools ด้วย (เดิมส่งเฉพาะ stream)
- ใหม่ ModelAutoTester: เทส 2 ขั้นต่อโมเดล (chat "Reply with exactly: OK" → tool test get_current_time) persist "model_caps_v1" (format "provider/model|flags|latency")
- Settings: ปุ่ม ✓ เขียวข้าง Main Model (เฉพาะ groq/nvidia_nim) เรียก autoTestProviderModels; โมเดล chat-fail ถูกซ่อน, tools-fail แก้ tag 🔧 ตามผลจริง
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 10 — Fallback ใช้ผล auto-test จริง
- JarvisOrchestrator.updateModelCaps(caps): เก็บ providerId → set โมเดล chat-ok / tools-ok
- chatWithCrossProviderFallback: ถ้า provider เคย auto-test → ใช้เฉพาะโมเดลที่เทสผ่าน (tools-ok ก่อน) แทน heuristic เดิมที่ชอบ gpt-oss-120b/compound (เทสจริง chat ไม่ผ่านทั้งคู่)
- heuristic สำรอง (ยังไม่เคยเทส) อัปเดตตามผลเทส: llama-3.3-70b-versatile, qwen3.6, nemotron-3-ultra/super-120b/nano-30b, llama-3.3-nemotron-super-49b, deepseek-v4-flash, llama-3.2-11b-vision, nemotron-nano-12b-v2, llama-3.1-8b
- JarvisViewModel ส่ง caps เข้า orchestrator ทั้งตอน startup และหลัง auto-test จบ
- ผลเทสของ user: Groq tools ผ่าน 3/8 (llama-3.1-8b-instant, llama-3.3-70b-versatile, qwen3.6-27b) / NIM tools ผ่าน 11/87 (nemotron-3-ultra-550b, super-120b, nano-30b, llama-3.3-nemotron-super-49b, deepseek-v4-flash ฯลฯ)
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 11 — ตัด NIM + auto-test OpenRouter free + กัน search_web หลุด
- ตัด NVIDIA NIM ออกจาก UI (ProviderMetas/freeFilterProviders) และ cross-provider fallback order เหลือ groq → openrouter → minimax (user: โมเดล NIM ช้า/404 เยอะ)
- ModelAutoTester.testAllModels เพิ่ม freeOnly param; JarvisViewModel.autoTestProviderModels เปลี่ยนจาก nvidia_nim → openrouter (freeOnly=true) — ปุ่ม ✓ ใน Settings ใช้กับ groq/openrouter
- heuristic fallback อัปเดต: llama-3.3-70b-versatile, qwen3.6, llama-3.3-70b, qwen3, deepseek, gemini, llama, qwen, mistral
- แก้ bug สำคัญจาก log: llama-3.3-70b/qwen บน Groq ถามราคา XAUUSD แล้วหลุดเรียก search_web ซ้ำแทน trading tools → ตอนนี้ซ่อน search_web ออกจาก tool spec เมื่อ prompt เป็น trading context (TradingToolPolicy.isTradingContext)
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 11b — ผล auto-test OpenRouter free จาก user
- เทส 17 โมเดลฟรี: ผ่าน 6/17 (chat+tools ครบ)
  ✅ inclusionai/ling-3.0-tiny (2.1s), poolside/laguna-s-2.1 (3.4s), nvidia/nemotron-3-ultra-550b (3.7s), nvidia/nemotron-3-nano-omni-30b-reasoning (2.2s), google/gemma-4-26b-a4b-it (3.1s), nvidia/nemotron-3-super-120b (2.7s)
- caps โหลดเข้า orchestrator ถูกต้อง: {groq=3, nvidia_nim=11, openrouter=6} — fallback จะเลือกเฉพาะตัวเทสผ่าน
- สังเกต: โมเดลที่ fail เร็วผิดปกติ (~140-150ms) หลายตัวน่าจะ 429 burst ตอนเทสถี่ ไม่ใช่โมเดลเสียถาวร

## 2026-08-08 รอบ 12 — แก้ Groq ใช้ trading tools ไม่ได้ (root cause)
- ROOT CAUSE: chatWithExternalProvider ส่ง tools แค่ take(12) ตามลำดับ registry — builtin tools (calculate/recall_memory/...) มาก่อน trading tools ถูกตัดทิ้งทั้งหมด → model ไม่เห็น trading_price จนหลุดเรียก recall_memory/calculate หรือตอบ "ไม่มีข้อมูล"
- Fix 1: trading context → sort trading tools ขึ้นก่อน take(12)
- Fix 2: system prompt addon เมื่อ trading context — แจ้งชัดว่ามี trading_price/trading_technical_analysis/trading_indicators/trading_smc_analysis ฯลฯ ใช้ได้โดยไม่ต้อง MT5 ห้ามตอบ "ไม่มีข้อมูล" ถ้ายังไม่เรียก tool
- Fix 3: ตัด MT5 tools (21 ตัว) ออกจาก spec เมื่อไม่ใช่ strict MT5 mode — ลด schema bloat + กัน model หลุดเรียก trading_mt5_analyze ตอนไม่ได้ pair
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 13 — ตามผลเทส Groq/OpenRouter ของ user
- ยืนยัน fix รอบ 12 สำเร็จ: ทั้ง 3 โมเดล Groq เรียก trading_price ได้แล้ว (🔔 [TOOL]: trading_price)
- llama-3.3-70b ตอบราคาผิด (1,950 vs 4,341 จริง) — เพิ่ม log "Tool result [name] ... 300 chars preview" เพื่อดีบักรอบหน้าว่า tool คืนอะไร
- llama-3.1-8b-instant ติด TPM 429 ของ Groq (limit 6000/min) ไม่ใช่ bug app
- ling-3.0-tiny:free ติด 429 upstream Novita ของ OpenRouter ไม่ใช่ bug app
- แก้ "log ยาวมาก": ตัด logDebug("Stream line: ...") ทุก SSE chunk ใน OpenRouterLlmProvider (reasoning models ทำ logcat พุ่ง 462KB/คำถาม)
- แก้ "Show free models only ไม่จำค่า": persist setting show_free_models_only + โหลดกลับตอน startup
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 14 — ยืนยัน OpenRouter 6/6 ใช้ได้ + แก้ timeout
- ยืนยัน: tool result preview ทำงาน — trading_price คืน 4341.9350 ถูกทุกตัว → เคส llama-3.3-70b ตอบ 1,950 คือโมเดลหลอน (tool ให้ค่าถูก)
- อธิบาย user: Round 1 = โมเดลขอ tool, Round 2 = โมเดลสรุปจากผล tool — เป็นปกติของ tool calling ไม่ใช่ทำงานซ้ำ
- nemotron-3-super-120b Round 2 เรียก trading_price ซ้ำ args เดิม → loop guard บังคับสรุป (ทำงานถูก)
- nemotron-3-ultra-550b Round 2 ชน request timeout 60s (reasoning คิดนาน) ตอบขาดกลางประโยค → เพิ่ม LlmOptions.timeoutMs default 60s → 120s
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 รอบ 15 — อัปเดต App_Review_Checklist.md
- หมวด 1 Provider: ✅ revamp 2026-08-08 (เหลือ Gemini/OpenRouter/Groq/MiniMax, ตัด NIM, multi-key, cross-provider fallback + auto-test, ผลเทสจริง)
- หมวด 6 Orchestrator: ✅ เทสเครื่องจริงผ่าน (root cause take(12) + 3 fixes) — เหลือประเด็นเดิม JarvisPlanner dead code/IntentClassifier เปิดไว้
- หมวด 15 Diagnostic/Logging: ✅ (preview คำตอบ/tool result, ตัด SSE log, mask key)
- ตารางสรุปอัปเดตตาม — เหลือ 🔶 5 หมวด: 9, 10, 11, 12, 13

## 2026-08-08 รอบ 16 — checklist: หมวด 13 Database ✅ (user เทสผ่าน)
- เหลือ 🔶 4 หมวด: 9 File Tools, 10 System Tools, 11 Camera/Vision, 12 UI/UX

## 2026-08-08 รอบ 17 — หมวด 11 Camera/Vision ✅ + fix เปิดตาแล้วเงียบ
- user เทส: vision ตอบถูกต้อง แต่เงียบหลังเปิดกล้อง ต้องถามรอบ 2 → root cause: Live API ไม่เริ่ม turn จาก video stream เอง
- Fix: LiveGeminiService.turnCompleteFlow + sendClientText; LiveToolBridge รอ turn แรกจบ (timeout 15s) แล้วส่ง auto-prompt ให้สรุปภาพ + เรียก vision_deactivate — รอ user ยืนยันรอบหน้า
- Build: BUILD SUCCESSFUL — user ติดตั้ง APK เอง

## 2026-08-08 — Voice Profile ↔ Identity (หมวด 7 เสริม)
- แก้ AI เงียบหลังเปลี่ยนเสียง: หลัง reconnect ส่ง sendLiveClientText trigger ให้พูดยืนยันเสียงใหม่ + ทำงานค้างต่อ
- ผูกเสียงเข้า identity: applyVoiceIdentity() ตั้ง gender(หญิง/ชาย) + vibe ตาม tone ของเสียง persist ลง Core Memory (จำข้าม session)
- CORE_IDENTITY เพิ่มกฎคำลงท้าย: เสียงหญิง=ค่ะ / ชาย=ครับ (speechParticle)
- Settings > Identity เพิ่ม dropdown Voice Profile 30 เสียง (♀/♂ + tone) เลือกแล้ว sync gender/vibe อัตโนมัติ
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## 2026-08-09 — แก้บัค Live Voice 2 จุด (ตามรายงาน user)
- ลบระบบ resume-task หลังเปลี่ยนเสียงทั้งหมด (pendingCommandAfterVoiceChange) — เดิม AI วิ่งไปทำงานเก่าซ้ำ เช่น SMC ทองคำ ตอนนี้เปลี่ยนเสียงแล้วพูดยืนยันเสียงใหม่ 1 ประโยคอย่างเดียว
- ซ่อน transcription ฝั่ง user ของ live voice ตอนโหลดประวัติแชท (filter metadata live_voice ใน loadHistory) — เดิมเปิดแอปใหม่แล้วคำพูดตัวเองโผล่เป็นตัวหนังสือทั้งหมด
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## 2026-08-09 — แก้ AI อ้างเปลี่ยนเสียงโดยไม่เรียก tool
- อาการ: AI พูดว่า "เปลี่ยนเป็น Leda แล้ว" แต่ log ไม่มี voice_set_profile — model bluff (hallucinate success)
- เพิ่ม LIVE_RULES ข้อ 7: ห้ามอ้างเปลี่ยนเสียงสำเร็จหากยังไม่ได้เรียก voice_set_profile เด็ดขาด ต้องเรียก tool ใน turn เดียวกัน
- เสริม description ของ voice_set_profile ใน ToolRegistry บังคับเรียก tool เมื่อ user ขอเปลี่ยนเสียง
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## 2026-08-09 — Voice change round 3: greeting-on-ready + identity clobber fix
- แก้ user identity ถูกรีเซ็ตเป็น "ผู้ใช้": applyVoiceIdentity persist เฉพาะ agent_gender/agent_vibe ไม่เขียน map ทั้งก้อนทับ user fields
- Greeting หลังเปลี่ยนเสียงผูกกับ event setupComplete (pendingGreetingOnReady ใน LiveGeminiService) แทน timer 3.5s ที่ไม่ทำงาน — AI พูดยืนยันเสียงใหม่ทันทีที่ READY ไม่ต้องรอ user พูดก่อน
- LIVE_RULES ข้อ 7 เพิ่ม: โจทย์กว้างต้องเสนอเสียง+รอยืนยันก่อนเรียก tool / ระบุชื่อชัด=เปลี่ยนทันที / ห้ามเปลี่ยนกลางบทสนทนาที่ยังไม่จบ
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ (user ต้องตั้ง user identity ใหม่ใน Settings 1 ครั้งเพราะถูกทับไปแล้วรอบก่อน)

## 2026-08-09 — Voice greeting round 4: realtimeInput text
- พิสูจน์แล้ว: clientContent text ถูกส่งถึง model แต่ไม่ trigger generation ขณะ audio streaming (model เก็บเป็น context เฉยๆ ตอบรวมกับ user turn ถัดไป)
- เพิ่ม sendRealtimeText() ใช้ realtimeInput.text — ถูกปฏิบัติเหมือน user พูดเข้ามาจริง trigger turn ได้
- pendingGreetingOnReady เปลี่ยนมาใช้ sendRealtimeText แทน sendClientText
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## 2026-08-09 — ✅ หมวด 7 Live/Voice เสร็จสมบูรณ์ (user ยืนยัน "ทำงานได้อย่างลงตัว")
- Voice change flow ครบวงจร: tool call → reconnect → greeting ด้วยเสียงใหม่อัตโนมัติ (realtimeInput.text)
- อัปเดต App_Review_Checklist.md หมวด 7 เรียบร้อย — ความรู้สำคัญ: clientContent ไม่ trigger generation ขณะ audio streaming ต้องใช้ realtimeInput.text

## 2026-08-09 — หมวด 9 File Tools: file_write เสริม verify + media scan
- เคส: AI อ่านสลิปแล้วเขียน bill.txt ลง Download — log ขึ้นสำเร็จแต่ user ไม่เห็นไฟล์
- file_write เพิ่ม verify หลังเขียน (exists+size) ถ้าเขียนไม่ติดจริงจะคืน error แทนหลอกว่าสำเร็จ
- เพิ่ม MediaScannerConnection.scanFile หลังเขียนไฟล์ เพื่อให้ file manager เห็นไฟล์ใหม่ทันที
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## 2026-08-09 — หมวด 9: file_write รองรับ .xlsx (Excel จริง)
- user ยืนยัน media scan fix ใช้ได้: เห็นไฟล์ bill1.txt ใน Download แล้ว
- สร้าง XlsxWriter.kt (androidMain): เขียน xlsx จริงด้วย zip+XML ไม่พึ่ง POI (เบา ~0 dep) — inline strings, numeric cell อัตโนมัติ, รองรับ quoted CSV
- file_write: path ลงท้าย .xlsx → content เป็น CSV (บรรทัดละ row) แปลงเป็น Excel; description อัปเดตให้ AI รู้วิธีใช้
- Use case: อ่านบิล/สลิปหลายใบ → สร้างตารางรายรับ-รายจ่าย .xlsx ลง Download
- Build assembleDebug ผ่าน — รอ user ติดตั้ง APK ทดสอบ

## แผนอนาคต (บันทึกจาก user 2026-08-09): Rich Chat Rendering
- แสดงตาราง / รูป / กราฟ / infographic ในแชทได้ — ยกระดับ UX/UI
- แนวทาง: markdown table renderer ใน chat bubble, image thumbnail จาก file path, chart จาก chart library (เช่น Vico/MPAndroidChart), report card ที่มีอยู่ต่อยอด
- จัดอยู่ใน roadmap หมวด 12 (UI/UX)

## 2026-08-10 — README.md อัปเดตให้เป็นปัจจุบันทั้งฉบับ
- เขียนใหม่จากเดิมที่ค้างข้อมูลเก่า (Multi-Provider ยังเขียน GPT-4o/Claude, tool นับ 84/87, ไม่มี Alert V2/Voice profiles/fallback systems)
- แก้: Providers ปัจจุบัน = Gemini/OpenRouter/Groq/MiniMax, tool catalogue นับใหม่จาก declarations จริง = 88 ตัว (+ system_list/delete_agent_tools, voice_summary)
- เพิ่ม: Alert System V2, Provider fallback/multi-key/auto-test, Custom tool CRUD, File attachments+xlsx, Voice Profile↔Identity, ผล auto-test Groq 3/8 OpenRouter 6/17
- ย่อประวัติ mt5-core-server V17-V24 ชี้ไป wiki แทน เก็บ V25/V26/M15 Wall Scalping ไว้ครบ + เพิ่ม Rich Chat Rendering ใน Roadmap + หน้าเอกสารอ้างอิง wiki

## 2026-08-10 — สร้าง project_progress_summary.md
- สรุปภาพรวมการพัฒนาโปรเจคทั้งหมด (เม.ย.-ส.ค. 2026) จัดกลุ่มตามระบบ: รากฐาน/Live Voice/Alert V2/Providers/Custom Tools/File Tools/ระบบที่ผ่านเทส/สถานะ checklist/Roadmap
- ที่อยู่: .obsidian-wiki/00_System/project_progress_summary.md — ใช้คู่กับ README.md (GitHub) และ log.md (รายวัน)

## 2026-08-10 — ฟีเจอร์ "รีวิวตัวเอง" + Narration Mode (พูดยาวไม่จำกัด)
- user ขอ: สั่ง "รีวิวตัวเองให้ฟังหน่อย" แล้ว AI อ่านสรุป README ให้ฟังทั้งหมด + อยากให้พูดยาวขึ้นรองรับเนื้อหายาวอนาคต
- README อยู่บน PC ไม่ใช่มือถือ → bundle `composeResources/files/self_review.md` (เขียนแบบเล่าเรื่อง: ฉันคือใคร + ความสามารถ 8 ด้าน + ตัวเลข + roadmap)
- tool ใหม่ `system_self_review`: declaration (ToolRegistry) + executor อ่าน Res.readBytes + แนบคำสั่ง [NARRATION MODE]
- LiveToolBridge: ยกเว้น [VOICE RULE] 5-8 ประโยคสำหรับ tool นี้ → ใช้ [VOICE RULE - NARRATION] เล่าครบทุกหัวข้อ ไม่จำกัดความยาว ห้ามหยุดกลางทาง
- JarvisPersona LIVE_RULES ข้อ 8 (โหมดเล่ายาว): เรียก tool ทันทีเมื่อขอรีวิว/แนะนำตัว / เล่ายาวได้เต็มที่ / ห้ามใช้ analyze_and_display_report (user ต้องการฟัง ไม่ใช่อ่าน) / ห้าม markdown ออกเสียง
- Build assembleDebug SUCCESS — ต้องติดตั้ง APK ใหม่แล้วลองสั่ง "รีวิวตัวเองให้ฟังหน่อย" ทั้งโหมดแชทและ live

## 2026-08-11 — ✅ หมวด 9 File Tools ผ่าน + system_self_review ผ่าน (user ยืนยัน)
- user เทสเครื่องจริง: หมวด 9 (attachments/xlsx/file_write) ผ่าน และ "รีวิวตัวเอง" (system_self_review + narration mode) ผ่าน
- อัปเดต App_Review_Checklist หมวด 9 → ✅ (2026-08-11)
- สถานะรวม: ผ่าน 14/15 หมวด — เหลือ หมวด 10 (System Tools) และ หมวด 12 (UI/UX) รอเทสเครื่องจริง

## 2026-08-11 — ออกแบบชุดทดสอบหมวด 10 (System Tools) 10 ข้อ
- ครอบคลุม: diagnostics (แชท+live), connectivity, custom tool CRUD แบบเร็ว, persistence ข้าม session, edge case tool ไม่มีจริง
- ไฟล์: .obsidian-wiki/04_Tasks/Test_Plan_Section10_SystemTools.md (ตารางติ๊กผล + เกณฑ์ผ่าน)

## 2026-08-11 — เทสหมวด 10 รอบ 1: ผ่าน 7/10 (จาก logcat 00:47-00:52)
- ผ่าน: diagnostics (แชท+live), connectivity, สร้าง/ลิสต์/แก้/ใช้ custom tool **ใน Live** ครบ (btc_quick_check + MACD)
- ค้างเทส: ข้อ 8 ลบ tool, ข้อ 9 persistence ปิด/เปิดแอป, ข้อ 10 ลบ tool ที่ไม่มีจริง
- พบเพิ่ม (minor): (1) log "Empty model response in round 2" เขียนผิดเงื่อนไข — คำตอบจริงมาครบ 690 chars (2) Diagnostic หมวด Trading WARNING ดึงข้อมูลเปรียบเทียบไม่ได้ ไม่กระทบใช้งาน

## 2026-08-11 — เทสหมวด 10 รอบ 2: ข้อ 8 ผ่าน / ข้อ 10 พบบั๊ก + แก้ 3 จุด
- ข้อ 8 (ลบ btc tool ใน live): ผ่าน — ลบไฟล์+ถอด registry+พูดยืนยัน
- ข้อ 10 (ลบ tool ไม่มีจริง): พบบั๊ก — AI พูด "สักครู่ เดี๋ยวเช็คให้" แล้วจบเทิร์นโดยไม่เรียก tool (promise-then-stall) ต้องถามซ้ำ 2 รอบถึงเรียก system_list แล้วตอบไม่มี tool นี้
- Fix 1: LIVE_RULES ข้อ 9 (NO EMPTY PROMISES) — คำขอที่ต้องใช้ tool ต้องเรียกใน turn เดียวกัน ห้ามพูด "สักครู่/เดี๋ยวเช็คให้" แล้วจบเทิร์นเด็ดขาด
- Fix 2: log "Empty model response" หลอก — Round 2+ stream text ตรงไม่ผ่าน textBuffer → เพิ่ม emittedAnyText flag (GeminiService)
- Fix 3: Diagnostic Trading WARNING บอกแหล่งที่ตายชัดเจน (Yahoo GC=F vs OANDA) พร้อมค่าที่ดึงได้ แทนข้อความกว้างๆ (DiagnosticManager)
- Build assembleDebug SUCCESS — รอ user เทสข้อ 10 อีกรอบ + ข้อ 9 (persistence) ปิดหมวด 10

## 2026-08-11 — ข้อ 10 ผ่านหลัง fix (logcat 01:27)
- สั่ง "ลบ Tool ชื่อ Not Exit Tool" → เรียก system_delete_agent_tool ทันที 0.006s ในเทิร์นเดียว ตอบถูก (ไม่พบ + บอก tool ที่มี) ไม่ต้องถามซ้ำ — LIVE_RULES ข้อ 9 ทำงาน
- เหลือข้อ 9 (persistence ปิด/เปิดแอป) ข้อเดียวก่อนปิดหมวด 10

## 2026-08-11 — ✅ หมวด 10 System Tools ผ่านครบ 10/10 (user ยืนยัน)
- ข้อ 9 persistence ผ่าน — ปิด/เปิดแอปแล้ว custom tools โหลดกลับใช้งานได้ทันที
- อัปเดต App_Review_Checklist หมวด 10 → ✅
- สถานะรวม: ผ่าน 15 หมวดยกเว้น หมวด 12 (UI/UX) — เหลือหมวดเดียว

## 2026-08-11 — รีวิว Sleep Cycle (Memory Consolidation): engine สมบูรณ์ แต่ trigger หลุด → wire กลับ
- ตรวจตามคำขอ user: performSleepCycle ครบถ้วนถูกต้อง (ดึง 100 ข้อความเก่าสุด → LLM consolidation JSON → archive summary+facts พร้อม embedding → upsert graph nodes/edges → archive raw transcript ก่อนลบ → ลบข้อความที่ประมวลผล)
- 🔴 พบ: triggerSleepCycle/getMessageCount ไม่มี caller เลยในโค้ดปัจจุบัน (B4 เคย wire ใน sendMessage แต่หลุดตอน refactor) → Sleep Cycle ไม่เคยรันอัตโนมัติ ChatMessage โตไม่จำกัด
- Fix: wire กลับใน sendMessage — หลัง storeMessage เช็ค getMessageCount() >= 200 → triggerSleepCycle() (runCatching กันกระทบแชท)
- หมายเหตุ minor: live voice turns persist ผ่าน LiveGeminiService ไม่ผ่าน sendMessage — trigger จะเช็คในข้อความแชทถัดไป; node insert ยังไม่ใช้ weight_delta เริ่มต้น (รู้ตัวตาม comment)
- Build assembleDebug SUCCESS — ต้องติดตั้ง APK ใหม่

## 2026-08-11 — เพิ่มสถานะ Sleep Cycle ใน Diagnostics
- เพิ่ม category ที่ 5 "Memory" ใน DiagnosticManager: แสดงจำนวนข้อความใน working memory (trigger ที่ 200) + เวลา consolidation ล่าสุด
- WARNING เมื่อข้อความ >= 200 (รอ sleep cycle รันในแชทถัดไป), PASS เมื่อปกติ
- JarvisMemoryManager.performSleepCycle บันทึก setting `last_sleep_cycle_at` หลัง consolidate เสร็จ
- Build :composeApp:assembleDebug ผ่าน (แก้ import kotlinx.datetime ขาดหาย 1 จุด)

## 2026-08-11 — แก้ empty response + timeout จาก log ทดสอบ
- GeminiService: response ว่าง (finishReason=STOP, parts=0, ไม่มี tool call) ถือเป็น failure → เข้า fallback chain อัตโนมัติ (key ถัดไป → โมเดลถัดไป) แทนการแสดงแชทว่าง
- generateResponse (nested AI summary เช่น calendar preview): timeout 20s → retry อัตโนมัติ 1 ครั้งด้วย 45s ก่อนแจ้ง error
- Diagnostics หมวด Memory ทำงานถูกต้อง (user ยืนยันจาก log: PASS, messages=8, last consolidation=ยังไม่เคยรัน)
- Build :composeApp:assembleDebug ผ่าน

## 2026-08-11 — แก้ log ตัดกลางข้อความ + MALFORMED_FUNCTION_CALL
- Logger.android: logDebug แบ่ง chunk 3500 chars อัตโนมัติ ([part x/y]) กัน logcat จำกัด ~4000 bytes/บรรทัด — log ยาวไม่ถูกตัดกลางข้อความอีก
- JarvisVM: "Response complete" log เนื้อคำตอบเต็ม (เดิม take(800))
- GeminiService: finishReason=MALFORMED_FUNCTION_CALL ไม่โชว์ข้อความดิบ "⚠️ Response interrupted" ในแชทอีก — ถ้ายังไม่ได้ตอบอะไรเลยจะถือเป็น failure เข้า fallback chain อัตโนมัติ; ถ้าตอบไปแล้วจะ suppress เฉยๆ (log ไว้ตรวจได้)
- Build :composeApp:assembleDebug ผ่าน

## 2026-08-11 — Persist key/โมเดลที่ fallback ใช้ได้จริงลง settings
- GeminiService: เพิ่ม callback onWorkingConfigChanged — เมื่อ fallback สลับ key/โมเดลแล้วตอบสำเร็จ จะแจ้ง caller (ยิงครั้งเดียวต่อ request)
- JarvisViewModel: wire callback → insertSetting model_name/api_key + อัปเดต StateFlow ทันที
- ผล: แชทถัดไป/เปิดแอปใหม่เริ่มจาก key+โมเดลที่ใช้งานได้จริง ไม่วนกลับไปเริ่มตัวที่ติดลิมิต (แก้เคส user เจอ: สลับ key รอบแรกแต่รอบ 2-3 กลับมาใช้ key เดิม)
- Build :composeApp:assembleDebug ผ่าน

## 2026-08-11 — ปรับความยาวคำตอบ/เสียงพูดหลังรายงาน
- ToolExecutor analyze_and_display_report: เดิมสั่ง "สรุปสั้นๆ เท่านั้น" → แชทตอบนิดเดียว (650 chars) ทั้งที่ข้อมูลเยอะ; เปลี่ยนเป็นสรุปครบ ผลหลัก+ตัวเลขสำคัญ 3-5 จุด+จุดระวัง รวม 6-10 ประโยค (ไม่อ่านตารางซ้ำ)
- VOICE RULE (LiveToolBridge + LiveGeminiService): 5-8 ประโยค → 8-12 ประโยค, ตัวเลขสำคัญ 2-4 → 3-5 จุด "เล่าให้ครบทุกส่วนสำคัญ" (user: live พูดน้อยเกินทั้งที่ข้อมูลเยอะ)
- Build :composeApp:assembleDebug ผ่าน

## 2026-08-11 — ออกแบบชุดทดสอบหมวด 12 (UI/UX)
- สร้าง .obsidian-wiki/04_Tasks/Test_Plan_Section12_UIUX.md — 41 ข้อ 8 กลุ่ม (หน้าแชท, TopBar, Settings persist, หน้าสร้าง Alert, Automation, Tool/Trading screens, Live panel, Responsive)
- รวม regression ทุกบั๊ก UI ที่เคยแก้: dropdown crash, toggle ไม่จำค่า, input เลื่อนไม่ได้, เมนูใหญ่เกิน, live เปลี่ยนเสียงเงียบ
- ข้อ 3 (ตารางในแชท) จดเป็น input ให้ roadmap Rich Chat Rendering ไม่ถือ fail

## 2026-08-11 — ปิดหมวด 12 UI/UX → checklist ครบ 15/15 🎉
- user เทสเครื่องจริงผ่านทั้ง 8 กลุ่ม (A–H) ครบ 41 ข้อ ตาม Test_Plan_Section12_UIUX.md
- มีจุดปรับปรุงเล็กๆ น้อยๆ — user สั่งข้ามไปก่อน
- App_Review_Checklist.md อัปเดต: หมวด 12 = ✅ เสร็จสมบูรณ์ ทุกหมวดแล้ว

## 2026-08-11 — Chart Dashboard (multi-pane LWC) + Rich Chat Rendering V1
- Phase 1: asset ใหม่ chart_dashboard/ (index.html + dashboard_engine.js) — Lightweight Charts v5.1 multi-pane offline: layouts single/rsi/macd/rsi_macd/volume/full, overlays EMA20/50/200 + Bollinger, subpanes RSI(14)/MACD(12,26,9)/Volume/ATR, SMC zones (OB/FVG), header ราคา+%change, screenshot bridge
- TradingChartScreen V2: สลับโหมด Dashboard ↔ TradingView ได้ด้วย chips, เลือก layout/overlay จาก control bar, persist chart_view_mode/chart_layout/chart_overlays ลง settings
- JarvisViewModel: refreshChartCandles() ดึงแท่งเทียนผ่าน SmcApiService (incremental cache) + SMC analysis best-effort; sync ChartStateManager อยู่แล้ว
- AI tool ใหม่ chart_dashboard_control (open/close/set_layout/set_symbol/set_interval/set_overlay/set_view) — AI ปรับ layout กราฟเองได้ทั้งแชทและ Live (LIVE_RULES ข้อ 10, CHAT_RULES ข้อ 6)
- Phase 2: MessageBubble ใหม่ — parse markdown table → ตารางจริง (header สี cyan, zebra, scroll แนวนอน), ```chart fence → ChartCard แตะเปิดกราฟเต็มจอด้วย config นั้น (openChartWithConfig)
- Build :composeApp:assembleDebug ผ่าน

## 2026-08-11 — Chart Dashboard: pageReady fix + Phase 3 mini-chart ในแชท
- แก้บั๊ก dashboard ไม่มีแท่งเทียน (เหลือแต่กล่อง FVG): `evaluateJavaScript` ยิงก่อน WebView โหลดเสร็จเลยถูกทิ้งเงียบๆ → guard ทุก LaunchedEffect ด้วย `LoadingState.Finished` ใน TradingChartScreen.DashboardWebView
- Phase 3: ฝัง live mini-chart (Lightweight Charts engine เดียวกับ dashboard, สูง 220dp) ลงใน chart card ในแชทเลย ไม่ใช่แค่การ์ดลิงก์
  - MessageBubble รับ `liveChartSymbol`/`liveChartCandles` จาก App.kt (viewModel.chartCandles/chartSymbol)
  - symbol ตรงกัน (normalize: ตัด prefix/exchange, `=X`) + มี candles → แสดงกราฟสด + badge LIVE; ไม่ตรง → fallback การ์ดลิงก์แบบเดิม
  - mini chart guard pageReady เช่นกัน + แตะเปิดเต็มจอ
- Build: `:composeApp:assembleDebug` BUILD SUCCESSFUL

## 2026-08-12 — แก้ overlay เกินบน chart dashboard/mini-chart
- ปัญหา: สั่ง "เปิดกราฟ XAUUSD 1h ใส่ RSI กับ MACD" แต่กราฟมี FVG + EMA50 ติดมาด้วย
- สาเหตุ: (1) parseChartConfig default overlays=ema50 (2) dashboard วาด SMC ทุกครั้งที่มี smcResult (3) action open ไม่รีเซ็ต overlay ค้างจากครั้งก่อน
- แก้: default overlays = ว่าง (ทั้ง ViewModel, chart card, tool open ไม่ระบุ=ปิดทั้งหมด), FVG/OB กลายเป็น overlay "smc" ต้องเปิดเอง (chip SMC บน control bar, set_overlay รองรับ smc), refreshChartCandles ดึง SMC เฉพาะตอนเปิด smc, mini-chart ในแชทวาด SMC เฉพาะเมื่อ config ขอ
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-12 — ขยายการวาด SMC: Liquidity + Premium/Discount/Equilibrium
- helper ใหม่ `ui/components/SmcZonesJson.kt` (buildSmcZonesJson) ใช้ร่วมกันทั้ง dashboard เต็มจอและ mini-chart ในแชท
- วาดเพิ่มจากเดิม (OB/FVG): Liquidity zones เป็นเส้น dashed EQL(เขียว)/EQH(แดง) พร้อม ★ ตาม confluence (สูงสุด 8 เส้น), เส้น Premium ≥ (แดงจาง), EQ (ส้ม), Discount ≤ (เขียวจาง)
- dashboard_engine.js: drawSMCInternal รองรับ zone แบบ line (line=true + price)
- แสดงเฉพาะตอนเปิด overlay smc เหมือนเดิม; asset copy ไป commonMain resources แล้ว
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-12 — Tool ใหม่ trading_smc_flow (port จาก TV "SMC Flow System v2") + overlay EMA14/60
- SmcFlowAlertProvider.kt (ใหม่): port ส่วนสัญญาณของ Pine — UT Bot (key=2.0, ATR6, trailing stop), EMA14/60 cross + แท่งยืนยัน, 3-Bar Reversal, SMC Confluence (trigger≤3แท่ง + structure bias จาก SmcApiService + recipe A=OB / B=FVG+OB / C=Fib golden 0.618-0.786), Auto Fib levels จาก swing structureHigh/Low (คำนวณอย่างเดียว ไม่วาด)
- ใช้ได้ 3 ทาง: (1) AI chat tool trading_smc_flow (registry+definition+executor) (2) alert background (JarvisAutomationService dispatch) (3) ปุ่ม Auto Test + preset ลัด 5 อัน (SMC Flow BUY/SELL, EMA14/60 ตัดขึ้น/ลง, เข้า Fib Golden Zone)
- AlertFieldCatalog เพิ่ม SMC_FLOW (18 fields) + AI tool description อัปเดต (กันตั้งเงื่อนไขมั่ว)
- กราฟ: เพิ่ม overlay ema14 (เหลือง) / ema60 (ฟ้า) — JS OVERLAY_DEFS + chips + valid sets + chart_dashboard_control enum (+param overlays สำหรับ open)
- แหล่งอ้างอิง Pine: .obsidian-wiki/05_Refs/SMC_Flow_System_v2.pine
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-12 — Chart open = full reset + mini-chart auto-load + persona chart rules
- ปัญหา 1: "เปิดกราฟ XAUUSD 1h ใส่ smc" แต่กราฟค้าง RSI+MACD จากครั้งก่อน → แก้ action=open รีเซ็ตทั้งจอ: ไม่ระบุ layout=single, ไม่ระบุ overlays=ปิดหมด (เดิมรีเซ็ตแค่ overlay)
- ปัญหา 2: chart card ในแชทเป็นแค่ข้อความ → AI ใช้ set_overlay แทน open และไม่แนบ chart fence → แก้ persona ทั้ง CHAT_RULES#6 และ LIVE_RULES#10: "เปิดกราฟ ... ใส่ X" = open เสมอ (map rsi/macd→layout, ema*/bb/smc→overlays), ทุกคำตอบเกี่ยวกับกราฟ/ผลวิเคราะห์ต้องแนบ chart fence เสมอ
- เพิ่ม ensureChartData(symbol, interval) ใน ViewModel + onChartCardShown ใน MessageBubble — การ์ดปรากฏแต่ข้อมูลไม่ตรง → โหลด candles เงียบๆ ให้ mini-chart ขึ้นเอง (ไม่เปิดหน้ากราฟ)
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-12 — เติมค่า null ของ trading_technical_analysis จากแท่งเทียน local
- ปัญหา (log 09:00): TV scanner คืน null เพียบ (EMA20/50/200, MACD hist, Stoch, CCI, AO, BB upper/lower/width, RSI prev, +DI/-DI) และ TF 1D ได้ N/A ทั้งแถว — เดิมทราบตั้งแต่ 2026-08-04 แต่ยังไม่ได้เติม
- IndicatorAlertProvider เพิ่มคำนวณ: rsi14_prev, AO (SMA5-34 ของ median), ADX/+DI/-DI (Wilder 14)
- TradingToolExecutor.executeTechnicalAnalysis: fillTaFromLocal — key ที่ scanner null/N/A เติมด้วยค่า local (RSI, MACD ครบ, Stoch, CCI, AO, EMA, BB ครบ, ATR, ADX/DI, close) — signal/score ของ scanner คงเดิม
- JarvisAutomationService.fetchTechnicalAnalysisWithFallback: เติม null หลัง scanner สำเร็จ + ถ้า scanner ล้มทุก exchange ใช้ local ทั้งชุด (alert ที่อิง TA จะไม่ ERR อีก)
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-12 — แก้ 3 บั๊กจากการทดสอบ Chart/Rich Chat
- chart_dashboard_control ไม่สลับหน้าจอไป Chart Dashboard อัตโนมัติอีกต่อไป (open/set_layout/set_symbol/set_interval/set_overlay/set_view) — กราฟแสดงเป็นการ์ด mini-chart ในแชท ผู้ใช้แตะการ์ดเองเพื่อเปิดเต็มจอ (JarvisViewModel.applyChartControl; openChart/openChartWithConfig ยังเปิดเต็มจอตามเดิมเมื่อผู้ใช้กดเอง)
- แก้ Live mode: custom tool (เช่น custom_gold_check) ตอบกลับแล้วโมเดลพูด "รอสักครู่" แล้วจบ turn โดยไม่เรียก tool ต่อ → เพิ่มข้อความ STRICT ท้าย executeCustomSkill ห้ามพูดก่อน/ห้ามจบ turn จนกว่าจะเรียก tool ครบ (ToolExecutor.kt)
- แชทตอบสั้นเกิน (ก้อนเดียว) หลังอัปเดต Rich Chat → เสริม CHAT_RULES#5 สั่งตอบยาวครบทุกหัวข้อหลายย่อหน้า ห้ามสรุปย่อหน้าเดียวสำหรับงานวิเคราะห์ (JarvisPersona.kt)
- LIVE_RULES#10 ปรับข้อความยืนยันกราฟเป็น "แตะการ์ดกราฟในแชทเพื่อดูเต็มจอ" ให้ตรงพฤติกรรมใหม่
- Build: assembleDebug ผ่าน

## 2026-08-12 — แก้ User Identity กลับเป็นค่า default "ผู้ใช้"
- Root cause 1: JarvisPersona.loadFromCoreMemory ไม่มี caller เลย → เปิดแอปใหม่ identity เป็น default เสมอ (AI ดูเหมือนจำได้เพราะ core memory context ถูก inject เข้า prompt แยกต่างหาก) → เพิ่มโหลดจาก Core Memory ตอนท้าย loadSettings (JarvisViewModel)
- Root cause 2: Settings save → JarvisViewModel.updateIdentity แก้เฉพาะ in-memory ไม่ persist → เพิ่ม persist ลง Core Memory ทุก key ผ่าน memoryManager.setCoreMemory
- ผลกระทบเดิม: ถ้า AI เรียก identity_update หลัง restart (persona ยังเป็น default) onUpdateIdentity จะเขียน map ทั้งก้อนทับ user_name ที่ตั้งไว้ใน DB ด้วย "ผู้ใช้" — แก้ต้นทางด้วย startup load แล้ว
- Build: assembleDebug ผ่าน

## 2026-08-12 — แก้ live mode สั่งเปิดกราฟแล้วไม่มีการ์ดกราฟในแชท
- ต้นเหตุ: โมเดล live ตอบเป็นเสียง ห้าม markdown (LIVE_RULES) จึงไม่มี ```chart fence ให้ MessageBubble สร้างการ์ด
- แก้: setChartControlHandler ใน JarvisViewModel เมื่อ action=open ระหว่าง live session (_isListening) ให้ append Message("model", chart fence) เองตาม state กราฟปัจจุบัน — chat mode ไม่ซ้ำเพราะโมเดลแนบ fence มาเอง
- ยืนยันจาก log: custom_gold_check ใน live ทำงานครบ (deep suite + smc + TA ทุก TF → analyze_and_display_report → Turn Complete ปกติ)
- Build: assembleDebug ผ่าน

## 2026-08-12 — แก้ mini-chart การ์ดเก่าในแชทเปลี่ยน/หายตามการ์ดใหม่
- ต้นเหตุ: การ์ดทุกใบอ่าน candles/smc จาก state กลาง (liveChartCandles/liveChartSmcResult) และเช็คแค่ symbol (ไม่เช็ค interval) → พอสั่งเปิดกราฟใหม่ การ์ดเก่า recompose ด้วยข้อมูลใหม่
- แก้: เพิ่ม chartCardCache ใน JarvisViewModel แยกข้อมูลตาม key "SYMBOL/interval" + ensureChartCardData(symbol, interval, needsSmc) โหลด candles+SMC เฉพาะการ์ดนั้น ไม่แตะ state กราฟหลัก
- MessageBubble: เปลี่ยน param จาก liveChart* เป็น chartCardCache map; ChartCard อ่าน entry ของตัวเอง; App.kt wire ใหม่
- Chart Dashboard เต็มจอ (openChartWithConfig) ไม่เปลี่ยน — สลับการ์ดไปมายังถูกต้องตามเดิม
- Build: assembleDebug ผ่าน

## 2026-08-12 — แก้ mini-chart จาก live mode หายหลังปิด/เปิดแอป
- ต้นเหตุ: การ์ดกราฟที่ระบบสร้างให้ตอน live (chart fence) append เข้า _messages ใน memory อย่างเดียว ไม่ได้ storeMessage ลง DB → ประวัติแชทโหลดจาก DB ตอนเปิดแอปใหม่จึงไม่มีการ์ด
- แก้: persist fence ลง DB ผ่าน memoryManager.storeMessage ด้วย (JarvisViewModel chart control handler)
- Build: assembleDebug ผ่าน

## 2026-08-12 — Strategy Signals v1: แปลงกลยุทธ์จาก Strategy Library (Quantpedia) มาคำนวณในเครื่อง
- ใหม่: automation/StrategySignalProvider.kt — 5 กลยุทธ์จาก OHLCV ล้วน (TV candles 300 แท่ง): tsmom (ROC20), trend (EMA50/200+ราคา vs EMA200), reversal (RSI14+BB20,2), donchian (breakout ช่อง 20 แท่ง), w52high (proximity สูงสุดของข้อมูล) + consensus_signal/score
- Tool ใหม่ trading_strategy_signal (symbol, interval, strategy=all|tsmom|trend|reversal|donchian|w52high) — wire: ToolRegistry (2 list), TradingToolDefinitions (declaration + alert docs 2 จุด + tool_name param), TradingToolExecutor, JarvisAutomationService dispatch, AlertDataTester step 4.6
- Alert: AlertFieldCatalog.STRATEGY (18 fields) + presets 4 อัน (Consensus STRONG_BUY/STRONG_SELL, Donchian Breakout ขึ้น/ลง)
- Chart: overlay ใหม่ "donchian" (DC20 upper/mid/lower) ใน dashboard engine JS (+copy ไป commonMain resources), chip DC20 ใน TradingChartScreen, valid sets ทุกจุด (JarvisViewModel x4, ToolRegistry enum, loadSettings), mini-chart (MessageBubble)
- Persona: CHAT_RULES#7 รู้จัก trading_strategy_signal + map overlay donchian; LIVE_RULES#10 เพิ่ม donchian
- Build: assembleDebug ผ่าน

## 2026-08-12 — ทดสอบ trading_strategy_signal ผ่านทั้ง chat + live
- ผู้ใช้ทดสอบ "ขอสัญญาณกลยุทธ์ xauusd 15m strategy all" ทั้ง 2 โหมด — tool คืนค่าครบ 5 กลยุทธ์ + consensus (chat ตอบ 1309 chars, live เรียก native tool + Turn Complete ปกติ)

## 2026-08-12 Signal Markers (overlay "signals")
- เพิ่ม SignalMarkerProvider (automation/) คำนวณ marker ย้อนหลัง edge-triggered 8 ชนิด: MOM(ROC20 flip), TR(EMA50/200 cross), REV(RSI+BB), DC(Donchian breakout), 52H, E(EMA14/60), UT(UT Bot flip), 3BR — จำกัด 12/ชนิด ไม่ทำ SMC Confluence (หนักเกิน)
- JS engine: overlay "signals" + state.signalMarkers + applyMarkers() ผ่าน LightweightCharts v5 createSeriesMarkers + bridge drawMarkers(json) + legend "◆ Signals N"
- ViewModel: chartSignalMarkers StateFlow, refreshChartCandles คำนวณเมื่อเปิด signals, toggle/set_overlay ดึงอัตโนมัติ, chartCardCache เปลี่ยน Pair→Triple(candles, smc, markers) + ensureChartCardData(needsMarkers)
- UI: chip SIG ใน TradingChartScreen, DashboardWebView+MiniChart ยิง drawMarkers, App.kt wire chartSignalMarkers
- ToolRegistry enum overlay +signals, persona CHAT#6/LIVE#10 รู้จัก signals
- Build assembleDebug ผ่าน

## 2026-08-13 Signal Alert System (ชั้น 1-3)
- SignalAlertProvider (ใหม่): ตรวจ edge สัญญาณใหม่เฉพาะ "แท่งปิดล่าสุด" (n-2) จาก 8 กลยุทธ์ โดย reuse SignalMarkerProvider.compute() (refactor fetch→compute แชร์โค้ดกัน) — payload: signal_buy/signal_sell (0/1), signal_event, strategy, side, entry/sl/tp/rr, reason (ไทย), context snapshot (trend/RSI/BB/ATR/DC20)
- TP/SL เฉพาะกลยุทธ์: MOM/TR/E=2xATR14→3xATR14, UT=2xATR6→2R, DC=1.5→2.5xATR14, REV=1xATR→BB basis, 3BR=จุดสุดแท่งกลาง→2R, 52H=1.5→2xATR14
- Job type trading_signal_alert: เชื่อม checkJob ใน JarvisAutomationService + AlertFieldCatalog.SIGNAL_ALERT (UI dropdown อัตโนมัติ + AI create ผ่าน automation_manage_alerts validate ผ่าน)
- fireJobAlert แยก prompt พิเศษสำหรับ signal alert: ส่ง payload ครบให้ AI quick-check (เฉพาะข้อมูลที่ให้ ไม่วิเคราะห์เพิ่ม ~10 วิ) fallback body แสดง entry/SL/TP เองถ้า AI ปิด
- Dedup ใช้กลไก is_triggered เดิม (edge อยู่ 1 แท่ง → reset อัตโนมัติเมื่อแท่งใหม่ไม่มีสัญญาณ)
- Build assembleDebug ผ่าน

## 2026-08-13 Signal Stats + Alert 2 โหมดส่ง
- SignalMarkerProvider.compute(candles, maxPerKind) — stats ใช้ uncapped ได้
- SignalAlertProvider.fetchStats(symbol@tf, strategy): backtest สัญญาณย้อนหลังทุกจุด จำลอง TP/SL เฉพาะกลยุทธ์ (ชน SL ก่อน=แพ้ -1R, TP ก่อน=ชนะ +winR, แท่งชนทั้งคู่ถือแพ้ conservative, ค้าง=timeout คิด R จากปิดสุดท้าย) → win-rate/avgR ต่อกลยุทธ์
- Tool ใหม่ trading_signal_stats (def + ToolRegistry 2 จุด + executor) + persona rules #8/#9
- โหมดส่ง alert ต่อ job: AutomationCondition.delivery ("ai" default | "direct") เก็บใน condition_json (ไม่ต้อง migrate DB)
  - direct: ไม่เรียก AI → notification สั้น (เหตุผล/Entry/SL/TP/RR) + insert เข้า ChatMessage โดยตรง (JarvisMemoryManager.storeMessage, metadata type=signal_alert_direct)
  - เชื่อมครบ: Orchestrator onManageAlerts(delivery) → tool def 2 จุด → ViewModel.createAlert → App.kt → AutomationScreen (dropdown ส่วนที่ 4 + hint เมื่อเลือก Signal Alert) + presets 📡 Signal BUY/SELL ใหม่
- Build assembleDebug ผ่าน

## 2026-08-13 Review การคำนวณกลยุทธ์ทั้งหมด
- StrategySignalProvider (live state tool): ถูกต้องตามหลัก ไม่แก้ (TSMOM ROC20, Trend close>EMA200+EMA50>EMA200, Reversal RSI<30+BB lower, Donchian ไม่รวมแท่งปัจจุบัน, W52 proximity, consensus STRONG >= +3)
- SignalMarkerProvider แก้ 3 จุด:
  - REV: level-triggered (mark ทุกแท่ง RSI<30+close<=BB lower) -> edge (bullNow && !bullPrev) กัน marker ซ้ำรัว/alert ผิดจังหวะ
  - DC: lastSide อัปเดตเฉพาะตอน mark ทำ breakout ซ้ำฝั่งเดิมหลุด -> sides[] ทุกแท่ง + state-change edge
  - 52H: state-change edge เหมือน DC + สี SELL แยก (#FF8A65)
- TP/SL SignalAlertProvider.computeTpSl review ผ่าน ไม่แก้ (MOM/TR/E=2xATR14->3x, UT=2xATR6->2R, DC=1.5->2.5xATR, REV=1xATR->BB basis, 3BR=จุดสุดแท่งกลาง-0.2ATR min 0.5ATR->2R, 52H=1.5->2xATR)
- BUILD SUCCESSFUL 1m49s

## 2026-08-13 แก้ Signal Alert เด้งทันทีตอนสร้าง
- ปัญหา: สร้าง alert ขณะ marker ค้างในแท่งปิดล่าสุด → ยิงแจ้งทันที 1 ครั้งทั้งที่สัญญาณเกิดก่อนตั้ง
- SignalAlertProvider: เพิ่ม field signal_buy_id / signal_sell_id = timestamp(ms) ของแท่งที่เกิด edge ("0"=ไม่มี)
- ตอนสร้าง alert (ทั้ง 2 เส้นทาง: JarvisViewModel.createAlert และ Orchestrator automation_manage_alerts) แปลง signal_buy/sell >= 1 → signal_*_id > <เวลาที่สร้าง ms> อัตโนมัติ → ยิงเฉพาะสัญญาณใหม่หลังสร้าง
- อัปเดต hint ใน AlertFieldCatalog
- Alert เก่าที่สร้างก่อนแพตช์นี้ยังใช้ semantics เดิม — ลบแล้วสร้างใหม่ถ้าต้องการพฤติกรรมใหม่
- BUILD SUCCESSFUL 2m11s

## 2026-08-13 ระบบบันทึก Signal + สถิติ TP/SL จริง + UI delivery
- UI แก้ไข Alert (AlertEditDialog): เพิ่มตัวเลือกโหมดส่ง "AI วิเคราะห์ก่อนแจ้ง" / "แจ้งตรง" (OutlinedButton 2 ทางเลือก + hint)
- DB: ตาราง SignalAlertRecord (job_id/symbol/side/strategy/reason/entry/sl/tp/rr/bar_time/delivery/outcome OPEN|TP|SL/hit_at/hit_price/result_r) + migration 3.sqm + queries (insert/dup-check/open/close/since)
- AutomationManager: recordSignalAlert (กันซ้ำด้วย job_id+bar_time), getOpenSignalAlerts, closeSignalAlert, getSignalAlertsSince
- JarvisAutomationService: fireJobAlert บันทึก signal ทุกครั้งที่ยิง (ทั้งโหมด ai/direct); trackSignalOutcomes() ทุก cycle — ไล่แท่งหลังจุดสัญญาณ ชน SL ก่อน=SL(-1R), ชน TP=+RR, แท่งชนทั้งคู่ถือ SL, cache candles ต่อ symbol/cycle
- Tool trading_signal_stats: เพิ่ม source=live (range=today/7d/all) อ่านสถิติจริงจาก DB — รายการ signal + ผล TP/SL + สรุปรวม/รายกลยุทธ์; executor ใช้ JarvisDatabaseHolder
- อัปเดต persona rule #8 ให้ AI รู้จัก source=live
- BUILD SUCCESSFUL 2m8s

## 2026-08-14 — Setup Checklist
- เปลี่ยน section Permissions ใน SettingsDialog เป็น Setup Checklist 6 ข้อ: การแจ้งเตือน / ไมค์ / กล้อง / Overlay / All-files / Battery optimization
- data class SetupCheckItem (commonMain) รับสถานะ+onFix จาก MainActivity; สถานะอัปเดตทุก onResume ผ่าน mutableStateMap
- ปุ่ม "ตั้งค่า" พาไปหน้าระบบที่เกี่ยวข้อง (APP_NOTIFICATION_SETTINGS, runtime permission, OVERLAY, ALL_FILES, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
- BUILD SUCCESSFUL

## 2026-08-14 — Normalize signal alert ให้ตรงกันทุกเครื่อง
- สาเหตุ 2 เครื่องต่างกัน: AI เครื่อง B เลือก tool trading_technical_analysis field signal contains BUY (TA signal) แทน trading_signal_alert → 🎯 ต่างกัน + notification สั้น (ไม่มี Entry/SL/TP/RR/ATR เพราะ payload ครบเฉพาะ trading_signal_alert)
- Fix: onManageAlerts create แปลงอัตโนมัติ trading_technical_analysis.signal contains/== BUY|SELL → trading_signal_alert signal_buy/signal_sell >= 1 (ได้ baseline กันสัญญาณเก่าเด้งด้วย)
- BUILD SUCCESSFUL

## 2026-08-14 — แก้ Setup Checklist: ปุ่ม Battery Optimization กดแล้วไม่ไปหน้าตั้งค่า
- สาเหตุ: AndroidManifest ไม่ได้ประกาศ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` → intent `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ถูกระบบเมินเงียบๆ (ไม่ throw ทำให้ catch/fallback เดิมไม่ทำงาน) — พบบนเครื่อง Honor
- Fix 1: เพิ่ม `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` ใน AndroidManifest.xml
- Fix 2: MainActivity buildSetupChecks (รายการ battery) — เช็ก `resolveActivity` ก่อนสั่ง startActivity และไล่ fallback 3 ระดับ: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS → IGNORE_BATTERY_OPTIMIZATION_SETTINGS → APPLICATION_DETAILS_SETTINGS
- BUILD SUCCESSFUL (debug APK 19:39)

## 2026-08-14 — แก้ Alert 2 ข้อ: โหมด AI ไม่ส่งเข้าแชท + เสียงแจ้งเตือนแบบหุ่นยนต์
- Fix #1 (ไม่เข้าแชท): JarvisAutomationService.fireJobAlert โหมด delivery="ai" เดิมส่งแค่ notification — เพิ่ม storeMessage เข้าแชทเหมือนโหมด direct (metadata type=signal_alert_ai) → เปิดแชทเห็นเหตุผล/Entry/SL/TP/RR
- Fix #2 (เสียงหุ่นยนต์/สะกดคำอังกฤษ): เดิมใช้ Android TextToSpeech th-TH ที่สะกดคำอังกฤษทีละตัว (R-e-v-e-r-s-a-l) — เพิ่มเส้นทางเสียงแบบคนด้วย Gemini TTS:
  - speakAlert(): sanitizeForSpeech (ตัด emoji/markdown) → geminiTtsPcm (model gemini-2.5-flash-preview-tts, voiceName=Aoede, responseModalities=AUDIO → PCM16 mono 24kHz) → playPcmBlocking (AudioTrack stream รอจนจบ + timeout 60s) → fallback Android TTS ถ้าไม่มี api_key/ล้มเหลว
  - เปลี่ยนจุดเรียก speak() ทั้ง 3 แห่ง (fireScheduledTask / direct / ai) เป็น speakAlert()
- แก้ compile: import kotlinx.serialization.json.* + io.ktor.http.* และใช้ playbackHeadPosition (เดิมพิมพ์ playHeadPosition)
- BUILD SUCCESSFUL (debug APK 23:05)

## 2026-08-15 — แก้ Alert ไม่เข้าแชท (root cause ตัวจริง) + วินิจฉัย notification 2 เครื่องต่างกัน
- Root cause แชท: service แทรกข้อความลง SQLite เท่านั้น แต่หน้าแชทอ่าน DB ครั้งเดียวตอน loadHistory() ตอนเปิดแอป — ข้อความที่มาระหว่างเปิดแชทอยู่ไม่แสดง
- Fix: เพิ่ม AlertChatBus (commonMain, MutableSharedFlow in-process) — service push ข้อความเข้า bus + persist DB พร้อมกัน ผ่าน helper pushToChat(); JarvisViewModel collect แล้ว append เข้า _messages ทันที
- ครอบคลุม 3 จุด: fireJobAlert โหมด ai, โหมด direct, fireScheduledTask (เดิม scheduled task ไม่ลงแชทเลย)
- วินิจฉัย notification ยาว/สั้น 2 เครื่อง (จาก screenshot 01:10): เครื่อง A = AI quick-check สำเร็จ (ข้อความสนทนาไทย), เครื่อง B = fallback body สั้น (generateAiText คืน null) — ไม่ใช่ APK ต่างกัน; สาเหตุที่เป็นไปได้: toggle alert_ai_summary ปิดอยู่บน B หรือ AI call ล้มเหลว (model_name=gemini-3.1-pro อาจใช้ generateContent ไม่ได้/quota) — debug ด้วย logcat grep "AI wake-up"
- BUILD SUCCESSFUL (debug APK)

## 2026-08-15 — การ์ด Signal ในแชท + แก้ AI wake-up ไม่ fallback (404) + ลดดีเลย์เสียง
- การ์ดแชท: fireJobAlert ส่งข้อความแชทเป็น markdown card (MessageBubble รองรับ table/bold อยู่แล้ว) — header 🟢BUY/🔴SELL + symbol, กลยุทธ์, ตาราง Entry/TP/SL/RR/ATR14, เหตุผล, ท้ายการ์ดมี "JARVIS quick-check" (เฉพาะโหมด ai ที่ AI ตอบสำเร็จ) — ใช้ทั้งโหมด ai/direct; SignalAlertProvider เพิ่ม field signal_atr
- Fix 404 ไม่ fallback: generateAiText เดิมเรียก model_name เดียว (เครื่อง B ตั้ง gemini-3.1-pro → 404 "not supported for generateContent" → notification สั้น) — ใหม่ไล่ ModelConfig.GEMINI_FALLBACK_MODELS จนสำเร็จ และ persist โมเดลที่ใช้ได้จริงกลับลง settings
- ลดดีเลย์เสียงแจ้งเตือน: เพิ่ม buildSignalSpeech() — พูดเฉพาะประโยคสั้น (ฝั่งซื้อ/ขาย + symbol ภาษาไทย + TF + Entry/SL/TP) แทนข้อความยาวทั้งก้อน ทำให้ Gemini TTS synthesize เร็วขึ้นมาก; notification/แชทยังใช้ข้อความเต็มเหมือนเดิม
- หมายเหตุเรื่องดีเลย์: Live mode (gemini-3.1-flash-live) เป็น streaming realtime จึงไม่ดีเลย์ ส่วน alert voice เป็น one-shot cloud TTS ต้องรอ synthesize ครบก่อนเล่น → ดีเลย์ 2-5 วิเป็นปกติ; Android TTS ไม่ดีเลย์เพราะ synthesize ในเครื่อง
- BUILD SUCCESSFUL (debug APK)

## 2026-08-15 — การ์ดในแชทสำหรับ Alert ทุกประเภท (ไม่ใช่แค่ signal)
- เพิ่ม buildAlertChatCard() ใน JarvisAutomationService — alert ทั่วไป (ราคา/indicator/SMC/sentiment ฯลฯ) ส่งเข้าแชทเป็นการ์ด: header 🎯 + ชื่อ alert + symbol, ตาราง เงื่อนไข (parse จาก condition_json แปลง GT/GTE → > / >=) / ค่าปัจจุบัน / ข้อมูลประกอบจาก provider (สูงสุด 8 แถว ตัด error/ค่าว่าง), ท้ายการ์ด JARVIS quick-check (เฉพาะโหมด ai ที่ AI ตอบสำเร็จ)
- ใช้ทั้งโหมด ai และ direct — เดิม alert ที่ไม่ใช่ signal ส่ง text ยาวติดกันอ่านยาก
- Scheduled task ในแชทมี header ⏰ **ชื่อ task** นำหน้า
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — ตัวเลือกเสียงแจ้งเตือน AI/เครื่อง + การ์ด 3D ในแชท
- เสียงแจ้งเตือนเลือก engine ได้ (สาเหตุ: ทดสอบ 2 เครื่องดีเลย์ 10-20 วิ — log เครื่อง B แสดง chain = generateAiText timeout 20s→retry 45s บนเน็ตช้า + TTS cloud ต่อท้าย):
  - setting ใหม่ `alert_voice_engine` = "ai" (Gemini TTS เสียงเหมือนคน มีดีเลย์) | "device" (Android TTS ทันที ไม่มีดีเลย์) — default "ai"
  - speakAlert() เช็ก setting ต้นฟังก์ชัน: device → Android TTS ทันที ข้าม cloud; ai → เส้นทาง Gemini TTS เดิม (fallback Android TTS เมื่อล้มเหลว)
  - ViewModel: _alertVoiceEngine StateFlow + setAlertVoiceEngine() (validate ai/device + persist) + โหลดกลับตอน init
  - UI: AutomationScreen AlertSettingsCard เพิ่มแถว chip 2 ตัว (✨ เสียง AI (สวย/ช้านิด) / ⚡ เสียงเครื่อง (ทันที)) แสดงเมื่อเปิดเสียงพูด — plumb ผ่าน App.kt
  - คำอธิบายที่ตอบผู้ใช้: Live mode (gemini-3.1-flash-live) เป็น websocket streaming realtime รับ text/image/audio/video และส่งเสียงออกได้จริง แต่เปิด session ต่อ alert หนักกว่า one-shot TTS จึงยังไม่เปลี่ยน
- การ์ด alert ในแชทแบบ 3D (ผู้ใช้ว่าตาราง markdown เรียบไป + field เยอะเกิน):
  - AlertChatBus.tryEmit รับ metadata (data class ChatPush) — pushToChat ส่ง metadata เข้า bus ด้วย (เดิมส่งแค่ DB)
  - Message data class เพิ่ม field metadata; loadHistory + collector map metadata มาด้วย
  - JarvisAutomationService เพิ่ม conditionText() (ใช้ร่วมกัน) + signalChatMeta()/alertChatMeta() สร้าง JSON {kind:"signal"|"alert", side/symbol/entry/tp/sl/rr/atr/reason/summary หรือ name/condition/current}
  - buildAlertChatCard ตัด provider dump 8 แถวออก เหลือเฉพาะ เงื่อนไข + ค่าปัจจุบัน (field เฉพาะจำเป็นต่อเงื่อนไข)
  - MessageBubble: parse metadata ก่อน render — kind=signal → SignalAlertCard3D (ป้ายเขียว BUY/แดง SELL + gradient + shadow 12dp + field Entry/TP/SL/RR/ATR14 + เหตุผล + quick-check), kind=alert → GenericAlertCard (accent cyan + เงื่อนไข/ค่าปัจจุบัน); ไม่มี metadata → markdown เดิม (backward compatible กับข้อความเก่า)
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — Pipeline trace log ละเอียดของ AutomationService + แก้ AI summary แถม ```chart fence
- ผู้ใช้พบเครื่อง B: เลือกเสียง Gemini TTS แต่ได้ยินเสียง Android TTS (คือ fallback path — TTS ล้มเหลว แต่ log เดิมไม่บอกขั้นไหนพัง) และ quick-check มีข้อความดิบ ```chart {...}``` หลุดมาในการ์ด (โมเดลแหกคำสั่งห้ามใช้ markdown)
- เพิ่ม pipeline trace log ครบทุกขั้นตอนตั้งแต่เงื่อนไขถูกต้อง (grep "AutomationService" แล้วดู emoji นำหน้า):
  - 🔔 FIRE: ชื่อ job / tool / symbol / ค่าที่ trigger / mode (ai|direct) / toggle aiSummary / voice on-off + engine / โมเดลหลัก — บรรทัดเดียวเห็น config ทั้ง chain
  - 🧠 AI summary: start (fallback chain ที่จะไล่), OK (model ที่สำเร็จจริง + เวลา ms + ความยาว), FAILED ต่อโมเดล (เวลา + error), skipped (toggle ปิด / ไม่มี api_key), FAILED ทุกโมเดล → ใช้ template
  - 🔊 speakAlert: engine ที่เลือก (ai|device) + ttsReady + ความยาวข้อความ, ผลลัพธ์ชัดเจน: "→ Android TTS (device mode)", "→ Gemini TTS OK N bytes (N ms)", "Gemini TTS FAILED ... → fallback Android TTS", "ไม่มี api_key → Android TTS"
  - 💬 pushToChat: kind/metadata + busEmitted (false = แชทไม่ได้เปิดอยู่ แต่ persist DB ปกติ) + bodyLen
- แก้ข้อความผิดปกติ: เพิ่ม stripCodeFences() — ตัด ```...``` ทุกก้อนออกจาก aiText ก่อนใช้ (การ์ด/notification/เสียง) + เสริม intentAddon "ห้ามใส่ code block หรือ chart"
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — ประหยัดโควต้า Gemini TTS (จำกัดปริมาณการใช้)
- ผู้ใช้ชี้ประเด็น Gemini TTS จำกัดปริมาณการใช้ (free tier ต่ำ / tier 1 ~15 RPM) — log จริงเครื่อง B: TTS ข้อความ 204 ตัวอักษร = audio 918 KB (~19 วิ) synthesize 14 วิ ต่อ alert เดียว
- แก้ 3 จุด:
  1. alert ทั่วไป (โหมด ai) เดิมพูด aiText เต็ม ~200+ ตัวอักษร → เปลี่ยนเป็น buildAlertSpeech() template สั้น ~40-60 ตัวอักษร ("แจ้งเตือน X ทองคำ เข้าเงื่อนไขแล้ว ค่าปัจจุบัน Y") — ลดต้นทุนเสียง ~70% ต่อ alert (quick-check เต็มยังอยู่ในการ์ดแชท/notification)
  2. default alert_voice_engine เปลี่ยนจาก "ai" → "device" ทั้ง 4 จุด (ViewModel field, init load, speakAlert ifBlank, UI param) — ผู้ใช้ใหม่ได้เสียงทันทีไม่จำกัด; ค่าที่เคยเลือกไว้ (ai) ไม่ถูกทับ เพราะ persist ใน settings แล้ว
  3. label chip ใน AutomationScreen บอก trade-off ชัด: "⚡ เครื่อง (ทันที/ไม่จำกัด)" ขึ้นก่อน, "✨ AI (สวย/จำกัดโควต้า)"
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — เสียงแจ้งเตือนผ่าน Gemini Live API (ทดสอบเทียบ TTS vs Live)
- ผู้ใช้ขอเพิ่ม gemini-3.1-flash-live + gemini-2.5-flash-native-audio-dialog เป็น engine เสียงแจ้งเตือน เพื่อเทียบดีเลย์/ความถูกต้องกับ Gemini TTS one-shot — และ Live ให้พูดข้อความยาวเต็ม (ไม่ใช้ template สั้นประหยัดโควต้า)
- alert_voice_engine รองรับ 4 ค่า: "device" (Android TTS ทันที/ไม่จำกัด — default), "ai" (Gemini TTS one-shot REST + ข้อความสั้น), "live31" (gemini-3.1-flash-live), "live25" (gemini-2.5-flash-native-audio-dialog)
- speakAlert(shortText, fullText): live* ใช้ fullText (AI quick-check เต็ม / body เต็ม), device/ai ใช้ shortText — จุดเรียกทั้งโหมด direct/ai ส่งทั้งคู่; scheduled task ใช้ body ทั้งคู่
- speakViaLive() ใหม่ใน JarvisAutomationService — one-shot Live API: เปิด websocket BidiGenerateContent (reuse wire format Live* classes จาก LiveGeminiService) → setup (model + responseModalities=AUDIO + voice Aoede + system prompt "อ่านข้อความตรงๆ ห้ามตอบโต้") → รอ setupComplete → ส่ง clientContent(turnComplete=true, encodeDefaults=true) → รับ audio chunk เล่นทันทีแบบ streaming (LinkedBlockingQueue + AudioTrack MODE_STREAM บน Dispatchers.IO — ได้ยินตั้งแต่ chunk แรก ไม่รอ synthesize ครบเหมือน TTS) → turnComplete → close; timeout 90s; ล้มเหลว/ไม่ได้เสียง → fallback Android TTS
- log เทียบประสิทธิภาพ: "Live session READY (N ms)", "Live first audio chunk (N ms)", "→ Live (model) OK N bytes (total, first chunk)" — เทียบกับ "Gemini TTS OK N bytes (N ms)" ได้ตรงๆ
- UI AutomationScreen: chip "✨ AI" เลือกแล้วมี sub-chips 3 ตัว: 🗣 TTS / 🎙 Live 3.1 / 🎙 Live 2.5 Native
- แก้ compile: import io.ktor.websocket.* / plugins.websocket.* แบบ wildcard (member close/readText ไม่ resolve ด้วย import เดี่ยว) และ break ใน let-lambda ไม่ได้ก่อน Kotlin 2.2 → ใช้ flag shouldClose
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — ผลทดสอบเสียง 4 แบบ + แก้ชื่อโมเดล Live ผิด
- ผลทดสอบจริงเครื่อง B (13:02-13:07):
  - live31/live25: พังเงียบๆ ใน ~0.4 วิ → fallback Android TTS (ไม่มี error log) — root cause: ชื่อโมเดลผิด ใช้ "gemini-3.1-flash-live" แทนที่ถูก "gemini-3.1-flash-live-preview" (เทียบ DEFAULT_LIVE_MODEL ใน ModelConfig ที่ Live mode ใช้จริง) และ native-audio ตัวเก่า "gemini-2.5-flash-preview-native-audio-dialog" ถูก deprecate แล้ว
  - Gemini TTS (ข้อความสั้น 67 ตัวอักษร): OK แต่ยัง 14,058ms — พิสูจน์ว่าดีเลย์ TTS เป็น fixed overhead (synthesize+download) ไม่ได้แปรตามความยาวข้อความ
  - device: ทันที (2ms หลัง push)
  - AI summary: 2.7-5.2 วิ (gemini-2.5-flash)
- แก้: live31 → gemini-3.1-flash-live-preview, live25 → gemini-2.5-flash-native-audio-preview-12-2025 (ตัวปัจจุบัน)
- เพิ่ม log "Live ws closed: code=... reason=..." ทุกครั้งที่ websocket ปิด — กันเคส server ปิดเงียบๆ แล้ว debug ไม่เจออีก
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — รวมระบบเสียงแจ้งเตือน: ตัด Gemini TTS, chain Live เดียว, footer การ์ดบอก engine
- หลังทดสอบ 4 engines จริง: ผู้ใช้ตัดสินใจตัด Gemini TTS one-shot ออก (ติดโควต้า + ดีเลย์คงที่ ~14 วิ) และรวม Live 3.1/Live 2.5 เป็นตัวเลือกเดียว
- alert_voice_engine เหลือ 2 ค่า: "device" (Android TTS ทันที/ไม่จำกัด — default) | "live" (chain: Live 2.5 Native [gemini-2.5-flash-native-audio-preview-12-2025] → Live 3.1 [gemini-3.1-flash-live-preview] → Android TTS)
- migrate ค่าเก่าที่ persist ไว้ (ai/live31/live25) → "live" ผ่าน JarvisViewModel.normalizeAlertVoiceEngine; ฝั่ง service ก็ถือค่า legacy เป็น live เช่นกัน
- ลบ geminiTtsPcm/playPcmBlocking (dead code) ออกจาก JarvisAutomationService
- แก้เสียงแจ้งเตือนไม่ตรง persona Live mode (ผู้ใช้สังเกตบุคลิกต่างกัน): speakViaLive เดิม hardcode voice Aoede + system prompt สั้น → ใหม่ใช้ voice_name จาก Settings + JarvisPersona.CORE_IDENTITY เหมือน Live mode + กฎ "อ่านข้อความตรงๆ ห้ามตอบโต้"
- การ์ดแชทมี footer "🔊 เสียง: <engine>" ล่างการ์ด — ระบบรู้ engine จริงจาก callback onVoiceStart ใน speakAlert (ยิงครั้งเดียวด้วย AtomicBoolean): Live เมื่อ first audio chunk มาถึง, Android TTS เมื่อพูด, มี suffix "(fallback)" ถ้าหล่น chain
- เปลี่ยนลำดับ deliver: fireJobAlert ทั้ง 2 โหมดใช้ deliverChatAndVoice() — เปิดเสียง → พูดก่อน การ์ด push ตอนเสียงเริ่มจริง (Live ~2-5 วิ, เครื่อง ทันที); ปิดเสียง → push ทันทีไม่มี footer; กันพลาด: callback ไม่ถูกเรียก → push แบบไม่มี footer
- UI AutomationScreen เหลือ 2 chip: ⚡ เครื่อง (ทันที/ไม่จำกัด) | ✨ AI Live (สวย/จำกัดโควต้า) — เอา sub-chips ทดสอบออก
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — เสียงแจ้งเตือนเริ่ม chain จากโมเดล Live ที่ผู้ใช้เลือกใน Settings
- ผู้ใช้ขอไม่ให้สับสน: โมเดลเสียงแจ้งเตือนตัวแรกต้องตรงกับโมเดล Live ที่เลือกใน Settings (live_model_name) เสมอ — เลือก Live 3.1 → chain: 3.1 → 2.5 Native → Android TTS; เลือก Live 2.5 → chain: 2.5 → 3.1 → Android TTS
- แก้ JarvisAutomationService: LIVE_VOICE_CHAIN คงที่ → liveVoiceChain() อ่าน setting live_model_name (default ModelConfig.DEFAULT_LIVE_MODEL) ดันขึ้นต้น chain + ตัวที่เหลือตามหลัง (dedup); โมเดลนอกลิสต์รองรับด้วย (label = ชื่อดิบ)
- log เพิ่ม "🔊 Live chain: X → Y (ตามโมเดล Live ที่เลือกใน Settings)" ทุกครั้งก่อนพูด — เช็กลำดับ chain จาก logcat ได้
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — Alert ทั่วไปหยุดวน loop หลัง TRIGGERED จนกว่าผู้ใช้จะเลือก หยุด/ซ้ำ
- ปัญหา: alert ที่ไม่ใช่ signal (เช่น price alert "Alert XAUUSD" GTE 4370) หลังยิงแจ้งเตือนแล้ว ระบบยังวนเช็กเงื่อนไขทุก cycle (log "met=true" ซ้ำทุก ~1-2 นาที) ถึงไม่ยิง notification ซ้ำ แต่เปลืองทรัพยากรและสถานะค้าง — ผู้ใช้ต้องการให้ "พัก" จนกว่าจะกด 🛑 หยุดแจ้งเตือน / 🔁 แจ้งเตือนซ้ำ จาก notification
- กลไกเดิมที่ค้นพบ: signal alert (trading_signal_alert) re-arm อัตโนมัติเพราะ SignalAlertProvider คืน signal_buy_id/signal_sell_id เฉพาะเมื่อมี edge ที่ "แท่งปิดล่าสุด" พอแท่งผ่านไปคืน 0 → met=false → resetTrigger (ห้ามแตะพฤติกรรมนี้); ส่วน alert ทั่วไป reset เมื่อเงื่อนไขกลับเป็น false (ราคาหลุด) เท่านั้น
- แก้ JarvisAutomationService.checkJob: ถ้า is_triggered=1 และ tool_name != trading_signal_alert → ข้ามการ evaluate ทันที (log "⏸ พัก '<ชื่อ>' — TRIGGERED แล้ว รอผู้ใช้เลือก หยุด/ซ้ำ") — re-arm ทางเดียวคือปุ่ม 🔁 (AlertActionReceiver reset trigger เดิม) หรือ toggle ปิด/เปิด job
- แก้บั๊กแถม: Auto High Confluence (deep_analysis_suite score≥85) เดิมยิง fireJobAlert โดยไม่ markTriggered → ยิงซ้ำทุก cycle ตราบ score ค้าง — เพิ่ม markTriggered ก่อนยิง + return (กัน notification เบิ้ลกับเงื่อนไขหลักในรอบเดียวกัน)
- แก้ updateAlertJobStatus (.sq): เปลี่ยน is_active จะ reset is_triggered=0, last_value=NULL ด้วยเสมอ — กันเคสผู้ใช้ปิด/เปิด job ที่ TRIGGERED ค้างแล้วถูกพักถาวร (notification เดิมอาจถูกปัดทิ้งไปแล้ว); updateAlertJobCondition reset trigger อยู่แล้ว
- AutomationScreen: label สถานะแยกตามชนิด — signal alert: "TRIGGERED · รอสัญญาณใหม่" | alert ทั่วไป: "TRIGGERED · รอเลือก หยุด/ซ้ำ"
- ไฟล์: JarvisAutomationService.kt, JarvisDatabase.sq, AutomationScreen.kt
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — แก้ TV ดึงแท่งเทียนซ้ำทุก cycle ตอนตลาดปิด (เสาร์-อาทิตย์)
- อาการ: log "TV incremental refresh XAUUSD/15m: missingBars=46-47 fetchDelta=52-53" + "TV bars loaded: 52" ซ้ำตัวเลขเดิมทุก cycle ทั้งที่ตลาดทองปิด ไม่มีแท่งใหม่
- Root cause: estimateMissingBars() ใน SmcApiService คำนวณ (currentBucketStart - latestBarTs) / tfMs โดยสมมติตลาดเปิดตลอด — ช่วง weekend แท่งสุดท้ายค้างจากศุกร์ค่ำ ทำให้ "ขาด" หลอก ~46 แท่ง (15m) ตลอด → ดึง delta ทุก cycle, TV ส่งแท่งชุดเดิมกลับมา, merge แล้ว latestTs ไม่ขยับ → วนซ้ำไม่จบ + เขียน DB ซ้ำทุกรอบ
- Fix (SmcApiService.fetchCandlesWithSource): ถ้า incremental refresh ตอบกลับแต่ไม่มีแท่งใหม่กว่า latestTs ใน DB เลย → ถือว่าตลาดปิด เก็บ streak ต่อ sym|interval แล้วข้ามการดึง 1→2→3→4 buckets (เพดาน 4) — เคลียร์ streak ทันทีเมื่อมีแท่งใหม่จริง (ตลาดเปิด); ข้าม saveTvCandlesToDb/trim ด้วยเมื่อไม่มีแท่งใหม่ (ลด DB write เปล่าๆ); network fail (candles ว่าง) ไม่นับ streak
- ผล: ช่วงตลาดปิดเหลือดึงทดสอบ ~1 ครั้ง/ชั่วโมง (จากเดิมทุก cycle ~1-2 นาที × จำนวน job); ตลาดเปิดพฤติกรรมเหมือนเดิมเพราะแท่งใหม่มาเกือบทุก bucket
- log ใหม่: "TV incremental skip sym/tf: ไม่มีแท่งใหม่ (ตลาดปิด?) — ใช้ DB N แท่งต่อ" และ "TV no-new-bars sym/tf: streak=K → ข้าม K bucket(s) ถัดไป"
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — TRIGGERED job ออกจาก loop ทันที + ปุ่ม notification เป็น "ลบ" + ปุ่ม 🔁 ในหน้า list
- ปรับตามดีไซน์ผู้ใช้: alert ทั่วไปที่ TRIGGERED แล้วไม่ควรถูกรันใน job loop เลย (ถ้ามี 10 job ค้าง log จะเต็มไปด้วย ⏸ ทุก cycle) — notification มีทางเลือกอยู่แล้ว
- เพิ่ม query `getRunnableJobs` (JarvisDatabase.sq): `is_active = 1 AND (is_triggered = 0 OR tool_name = 'trading_signal_alert')` — AutomationService.runOneCycle เปลี่ยนมาใช้ตัวนี้ → job TRIGGERED ไม่เข้า loop ไม่มี log ซ้ำ; signal alert ยังอยู่เพราะ re-arm เอง; UI list ยังใช้ getAllActiveJobs เห็นครบ
- ปุ่ม notification "🛑 หยุดแจ้งเตือน" → "🗑 ลบแจ้งเตือน": AlertActionReceiver เปลี่ยนจาก updateAlertJobStatus(0) เป็น deleteAlertJob (ลบออกจาก list จริง — SignalAlertRecord ประวัติยังเก็บ) + ข้อความยืนยัน "ลบแจ้งเตือนแล้ว — สร้างใหม่ได้ในหน้า Automation"
- AlertActionReceiver ACTION_ALERT_REPEAT เพิ่ม wakeupAutomationService() — จำเป็นเพราะถ้าเหลือแต่ job TRIGGERED ค้าง service จะ stopSelf ไปแล้ว กดซ้ำต้องปลุกกลับมา
- หน้า Cron Jobs (AutomationScreen): เพิ่มปุ่ม 🔁 (Refresh icon สีเขียว) ในการ์ด job ที่ TRIGGERED ค้างและไม่ใช่ signal alert — กรณีผู้ใช้ปัด notification ทิ้งก็ยัง re-arm ได้จาก list (wire onRepeatAlert → automationManager.resetTrigger ผ่าน App.kt)
- label สถานะปรับเป็น "TRIGGERED · รอเลือก ลบ/ซ้ำ"
- ไฟล์: JarvisDatabase.sq, JarvisAutomationService.kt, AlertActionReceiver.kt, AutomationScreen.kt, App.kt
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — แก้ Live API ส่ง tool arg key พิมพ์ใหญ่ (condition_VALUE) ทำสร้าง alert ไม่สำเร็จ
- อาการ (log 18:22): สั่งด้วยเสียงผ่าน Live "ตั้งแจ้งเตือนสัญญาณทอง M15" → โมเดลส่ง `condition_VALUE=1` (พิมพ์ใหญ่) → handler หา `condition_value` ไม่เจอ → ❌ ทั้ง 2 job, JARVIS ต้องถามกลับ
- Root cause: parser อ่าน key ตรงตัว (case-sensitive) แต่โมเดล Live บางรอบส่ง key พิมพ์ใหญ่ปน
- Fix ที่จุดเดียวครอบคลุมทุก tool ทุกช่องทาง: ToolExecutor.execute() (funnel กลางที่ทั้งแชทและ Live [ผ่าน LiveToolBridge] วิ่งผ่าน) — ถ้า arg key มีตัวพิมพ์ใหญ่ ให้ normalize เป็นตัวเล็กทั้งหมดก่อน dispatch (tool definitions ทั้งระบบใช้ snake_case ตัวเล็กอยู่แล้ว จึงปลอดภัย) + log "Normalize arg keys เป็นตัวเล็ก: <tool> <keys>" ไว้ตรวจย้อนหลัง
- ไฟล์: tools/ToolExecutor.kt
- BUILD SUCCESSFUL (debug APK)


## 2026-08-15 — ทดสอบจริงผ่านทั้งหมด + ย้าย TV backoff เป็นระดับ process
- ผู้ใช้ทดสอบจริง: ปุ่ม 🗑 ลบ / 🔁 ซ้ำ (notification + หน้า Cron Jobs) ถูกต้อง, ไม่มี job วนซ้ำ, สั่งเสียงผ่าน Live สร้าง signal alert สำเร็จแล้ว (fix normalize key)
- ตรวจ log backoff ตลาดปิด: ทำงานถูก — fetch 17:15 (streak=2) → รอ 30 นาที → fetch 17:45 (streak=3) → รอ 45 นาที; แต่พบช่องโหว่: SmcApiService มีถึง 9 instances (AutomationService, ViewModel, TradingToolExecutor×5, TradingApiService, SmcToolExecutor, AlertDataTester) state backoff เป็น per-instance → แต่ละ instance ต้อง fetch อย่างน้อย 1 ครั้งก่อนเรียนรู้ (เห็นใน log: fetch 18:46:54 จาก tester แล้ว service ยัง fetch ซ้ำ 18:48:06 / 18:22:16 มี 2 instance ดึง 15m พร้อมกัน)
- Fix: ย้าย tvNoNewDataStreak/tvNoNewDataSkipUntil จาก instance field → companion object (แชร์ทั้ง process) — instance ไหนเจอ "ไม่มีแท่งใหม่" ก่อน ทุก instance ข้ามตาม
- ไฟล์: SmcApiService.kt
- BUILD SUCCESSFUL (debug APK)

## 2026-08-15 — เพิ่ม log path 'TV cache fresh' (BTC M15 ไม่ดึงแท่งเทียน = ไม่ใช่บั๊ก)
**อาการ:** ผู้ใช้สร้าง alert signal BTCUSDT@15m ผ่านเสียง (ตลาด crypto ไม่ปิด) แต่ไม่เห็น log ดึงแท่งเทียนหลัง full load ครั้งแรก
**สาเหตุ (ไม่ใช่บั๊ก):** รอบแรก full fetch 300 แท่งสำเร็จ (19:00:32) รอบถัดไป `estimateMissingBars`=0 เพราะแท่ง bucket 19:00 มีใน DB แล้ว → เข้า branch `missingBars <= 0` ซึ่ง return DB เงียบๆ ไม่มี log เลยดูเหมือนไม่ทำงาน fetch ครั้งถัดไปจะเกิดตอน bucket ขยับ (19:15) นอกช่วง log ที่ส่งมา — พร้อมกันนั้น baseline กัน signal เก่าทำงานถูกต้อง (sell id แท่ง 18:45 ถูก GT baseline กัน met=false)
**แก้ไข:** `SmcApiService.kt` branch `missingBars <= 0` เพิ่ม logDebug "TV cache fresh $sym/$interval: DB N แท่งทันปัจจุบัน (missing=0) — ไม่ต้องดึง รอ bucket ใหม่" ให้เห็นเส้นทาง cache สดชัดเจนเท่า path skip/backoff
**ไฟล์:** `composeApp/src/commonMain/kotlin/com/example/personalaibot/tools/trading/SmcApiService.kt`
**Build:** assembleDebug ผ่าน

## 2026-08-15 — อัปเดต README.md ให้ทันงาน 12–15 ส.ค.

- หัวข้อ bullet หลัก: Alert System V2 เพิ่ม Signal Alert + ปุ่ม ลบ/ซ้ำ + การ์ด 3D + เสียงเลือก engine; Mobile App เพิ่ม Setup Checklist 6 ข้อ
- Trading Intelligence: เพิ่ม Strategy Signal Provider (5 กลยุทธ์ Quantpedia) + Signal Alert Provider (8 กลยุทธ์ edge-triggered, Entry/SL/TP/RR, baseline กันเด้ง, บันทึกผล TP/SL จริง); Chart Dashboard เพิ่ม overlay Donchian + Signal Markers (SIG) + หมายเหตุ market-closed backoff
- Alert System V2 (section 4) เขียนใหม่ทั้งชุด: Signal Alert re-arm อัตโนมัติ, lifecycle TRIGGERED ออกจาก loop + ปุ่ม 🗑/🔁, โหมดส่ง ai/direct, การ์ด 3D + footer engine, เสียง 2 engine (เครื่อง default / AI Live chain ตาม Settings), presets 14+, pipeline trace log
- Tool Catalogue: 89 → 91 (เพิ่ม trading_strategy_signal, trading_signal_stats ใน TRADING TOOLS 26 → 28)

## 2026-08-17 — แผนระบบ Backtest Strategies (ศึกษา OLD_Code แล้ว)

- ศึกษา OLD_Code 2 โปรเจกต์: ai-trading-agent (backtest suite: engine/optimizer/walk_forward/monte_carlo/permutation/overfitting — port ได้ตรง) + moss-trade-bot (เอาแนวคิด evolution loop, regime classifier, local costs; backtest.py ผูก Hyperliquid ไม่ port)
- ทรัพยากรรีใช้ในแอป: SignalMarkerProvider.compute (signals 8 กลยุทธ์), computeTpSl, fetchStats (mini-backtest 300 แท่ง), SignalAlertRecord (ผลจริง), TV fetch 5,000 แท่ง/ครั้ง, การ์ด 3D, Lightweight Charts
- ประเด็นสำคัญ: DB ปัจจุบัน trim เหลือ ~300 แท่ง → ต้องแยกตาราง BacktestDataset + ทำ history paging ย้อนหลัง
- แผน 4 phases: (1) Engine+ข้อมูลย้อนหลัง+tool trading_backtest (2) Optimizer+Robustness (3) AI Evolution+Regime (4) ป้อน tuned params กลับเข้า SignalAlertProvider
- เอกสารแผนฉบับเต็ม: .obsidian-wiki/02_Components/Backtest_System_Plan.md

## 2026-08-17 — Backtest System Phase 1: Engine + ข้อมูลย้อนหลัง + tool trading_backtest

**แก้บั๊กแฝงที่พบระหว่างทำ (สำคัญ):** marker label "52H▲" filter เฉพาะตัวอักษรเหลือ "H" และ "3BR▲" เหลือ "BR" — ทำ 52W High กับ 3-Bar Reversal หลุดจาก stats เงียบๆ และ live alert ได้ชื่อกลยุทธ์/เหตุผล/TP-SL ผิด (ตก else branch) — เพิ่ม helper `signalKindOf()` แทน `label.filter{isLetter}` ทั้ง 5 จุดใน SignalAlertProvider

**ข้อมูลย้อนหลัง:** `SmcApiService.fetchBacktestCandles(symbol, interval)` — ดึง TV websocket 5,000 แท่งคงที่ (15m≈52 วัน, 1h≈7 เดือน, 4h≈2.3 ปี, 1D≈13 ปี) แยกจาก TvCandle DB เด็ดขาด (กัน trim 300) + in-memory cache ระดับ process 10 นาที + fallback Binance 1,000 แท่ง (crypto)

**BacktestEngine (ใหม่, automation/backtest/):** port จาก OLD_Code ai-trading-agent engine.py ปรับเข้าระบบ Signal — สัญญาณจาก SignalMarkerProvider.compute, SL/TP จาก computeTpSl (สูตรเดียวกับ live alert), กติกาเดียวกับ fetchStats (เข้าปิดแท่งสัญญาณ, ชนทั้งคู่ถือแพ้, ค้าง=TIMEOUT) + ถือทีละ 1 ไม้ (สัญญาณชนไม้ค้าง=skipped) + ต้นทุน spread/commission 4.5bps + money model เสี่ยง 1%/ไม้ เลเวอเรจ ≤10x → win-rate, PF, maxDD, Sharpe, expectancy R, equity curve, แยกตามกลยุทธ์

**Tool trading_backtest:** symbol/interval/strategy/costs → ผล markdown (ภาพรวม + ตารางแยกกลยุทธ์ + 5 ไม้ล่าสุด) — wire: TradingToolDefinitions, TradingToolExecutor (executeBacktest + formatBacktestResult), ToolRegistry 2 จุด, persona rule #8; computeTpSl/strategyName ปรับเป็น internal

**ต่างจากแผนเล็กน้อย:** การ์ด 3D/หน้าจอ Backtest + กราฟ equity curve เลื่อนไป Phase 2 (Phase 1 ใช้ markdown table ที่ render อยู่แล้ว)

**Build:** assembleDebug ผ่าน (3m36s)

## 2026-08-17 Backtest System Phase 2-4 (Optimizer / Evolution / Regime / Tuning DB)

- Phase 2: StrategyParams.kt (TpSlParams defaults ตรง computeTpSl, grid 25 combos, clampDrift ±30%, clampStep ±10%), ParamOptimizer (score=sharpe*PF*winRate, min 5 trades), WalkForward (anchored 5 splits 70/30, markers recompute ต่อ slice กัน lookahead), MonteCarlo (shuffle 1,000 รอบ), PermutationTest (200 รอบ), OverfittingScore (weights 40/25/20/15)
- Phase 3: RegimeClassifier (BULL/BEAR/SIDEWAYS ด้วย ADX14 + EMA50 slope) + statsByRegime ต่อท้ายผล trading_backtest; BacktestEvolution 8 ช่วง, AI reflection ผ่าน GeminiService (fallback heuristic) ; tool trading_backtest_evolve
- Phase 4: ตาราง StrategyTuning (UNIQUE(symbol,interval,kind)) + migration 4.sqm + AutomationManager CRUD; SignalAlertProvider ใช้ tuned params เมื่อ grade != overfit; tool trading_backtest_optimize (apply=on บันทึกเฉพาะไม่ overfit)
- แก้ compile error: nullable smart-cast ใน BacktestEvolution, break/continue ใน inline lambda (getOrElse -> getOrNull) ใน TradingToolExecutor
- Build: assembleDebug BUILD SUCCESSFUL
- ยังไม่ทำ: UI หน้าจอ Backtest + กราฟ equity curve (ผลลัพธ์เป็น markdown ในแชท), ทดสอบ performance จริงบนมือถือ

## 2026-08-17 Port SMC Engine จาก mt5-core-server เข้าแอป (Backtest + Live Signal)

- Phase A: automation/smc/ — SmcTypes, SmcUtils (ATR Wilder/swings), MarketStructure (BOS/CHoCH/SMS/BMS + IDM), FvgDetection, OrderBlocks, LiquidityZones (+confluence stars), SweepDetection (+AttackForce), SmcEngine.buildSnapshot
- Phase B: SmcSignalDetector — 5 เงื่อนไขเข้า (OB_BOUNCE, LIQ_SWEEP single-TF, STRUCTURE_BREAK, FVG_FILL, RSI_DIVERGENCE) กรอง RRR>=1.2 + confluence>=2 ดาว; bias proxy จาก structure+SMA20/50 แทน MTF; TP จาก findNearestLevel แทน PriceMap; SmcSignals.generate เดิน bar-by-bar window 300 แท่งกัน lookahead + edge-dedupe ด้วย signal key; kind "SMC" เข้า trading_backtest (strategy=smc หรือ all) SL/TP ตามโครงสร้างตลาดของสัญญาณเอง
- Phase C: SignalAlertProvider รวมสัญญาณ SMC เข้า job trading_signal_alert เดิม (edge ที่แท่งปิดล่าสุด, payload เพิ่ม signal_stars, strategy/reason จาก triggers) — ผู้ใช้ไม่ต้องสร้าง alert ใหม่
- ตัดออกจากรอบนี้: PriceMap/WallRegistry (~780 บรรทัด), MAGNET/WALL_BREAK scalp, V25 playbooks PB1-5, Kelly/circuit breaker, SimpleScalpingEngine
- อัปเดต AutomationModels label + TradingToolDefinitions (trading_backtest enum เพิ่ม smc)
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-17 แก้บั๊ก SL/TP ผิดฝั่งใน SMC backtest

- พบจาก log จริง: ไม้ BUY ชน "SL" เหนือราคาเข้าแต่กำไร (SL/TP สลับฝั่งจากสัญญาณ STRUCTURE_BREAK ที่ level ข้ามฝั่งราคา)
- แก้ 2 ชั้น: SmcSignalDetector กรองสัญญาณที่ SL/TP ผิดฝั่งทิ้งตั้งแต่ต้นทาง + BacktestEngine เช็ก directionValid ก่อนเปิดไม้ (กันพลาดทุกกลยุทธ์)
- ผลทดสอบผู้ใช้ก่อนแก้: SMC 15m ขาดทุน (PF 0.54) แต่ 1h กำไร +14.5% (win 61.1%, PF 1.14) — SMC ทำงานดีใน BULL regime (win 70%) แพ้ใน SIDEWAYS
- สังเกต: Live เรียก trading_backtest_optimize ซ้ำ 2 ครั้ง (09:40:27 และ 09:40:40) — ยังไม่แก้ ดูก่อนว่าเกิดจากฝั่ง Live tool-call dedupe
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-17 SMC quality gates (ทดแทน MT5 Gates ที่ไม่ได้ port)

- หลังแก้บั๊ก SL/TP ผิดฝั่ง ผลจริงของ SMC เปลือยๆ ยังขาดทุน (1h: win 28.7%, PF 0.56) — ผล +14.5% รอบแรกเป็นไม้ SL ผิดฝั่งที่กำไรเทียม
- ใส่ฟิลเตอร์ 4 ชั้นใน SmcSignalDetector: (1) minRRR 1.2→1.8 (2) SL/TP validity (3) เทรดตามทิศ structure เท่านั้น (4) Premium/Discount — reversal BUY เฉพาะ DISCOUNT / SELL เฉพาะ PREMIUM (ยกเว้น CONTINUATION)
- SmcSignals.generate เพิ่ม cooldown 10 แท่งต่อ strategy+side (แทน duplicate guard)
- ผู้ใช้ยืนยันความต่าง chat vs voice: ตัวเลขเหมือนกันเป๊ะ (cache hit) ต่างแค่รูปแบบนำเสนอ — ตามดีไซน์
- Build: assembleDebug BUILD SUCCESSFUL — รอผู้ใช้ทดสอบ backtest smc เทียบ 15m/1h/4h

## 2026-08-17 Backtest All-TF (interval=all)

- trading_backtest รองรับ interval=all → รัน 3 TF (15m/1h/4h) ในคำสั่งเดียว + ตารางเทียบผล (ไม้/Win%/PF/Expectancy/กำไร/MaxDD) แล้วตามด้วยผลละเอียดแยกแต่ละ TF
- ใช้ได้กับทุก strategy (all หรือเจาะจง เช่น smc) — แยก executeBacktest → runBacktestOne คืน (text, result, error)
- อัปเดต TradingToolDefinitions interval enum เพิ่ม "all"
- Build: assembleDebug BUILD SUCCESSFUL

## 2026-08-17 แก้ Live ช้า/ socket ตายตอน backtest all-TF
- อาการ: กด live หลายรอบไม่ READY + สั่ง `backtest smc ทอง all` ผ่านเสียงแล้วรันนาน ~6 นาที (15m 2m22s / 1h 2m13s / 4h 1m14s sequential) จน Live websocket โดน SocketTimeoutException (write) ต้อง reconnect
- สาเหตุ: executeBacktest รันบน dispatcher เดิม (Dispatchers.IO ของ LiveToolBridge) แบบ sequential + CPU-bound ยาว ทำ OkHttp ping/write ขาดช่วง → socket โดนตัด (ระบบ reconnect + ส่ง tool response ซ้ำทำงานถูกอยู่แล้ว)
- แก้ใน `TradingToolExecutor.kt`:
  1. `executeBacktest` / `executeBacktestOptimize` ห่อด้วย `withContext(Dispatchers.Default)` — แยกงาน CPU หนักออกจาก IO pool ที่ Live/websocket ใช้
  2. all-TF เปลี่ยนจาก for-loop sequential เป็น `coroutineScope { tfs.map { async { runBacktestOne } }.awaitAll() }` รัน 15m/1h/4h พร้อมกัน (ผลยังเรียงตาม TF เดิม) — คาดว่าจาก ~6 นาที เหลือราวเวลา TF ที่ช้าสุด (~2.5 นาที)
  3. `runBacktestOne` skip การคำนวณ classic markers ทั้ง 8 กลยุทธ์เมื่อ strategy=smc (เดิมคำนวณทิ้งเปล่า) + log เวลา fetch/คำนวณต่อ TF (tag Backtest)
- BUILD SUCCESSFUL; ยังไม่ commit (รอผู้ใช้สั่ง)
- หมายเหตุ: อาการกด live รัวๆ แล้วไม่ READY 3 รอบแรก ยังไม่ยืนยันสาเหตุ — น่าจะโดน throttle จากการเปิด session ถี่เกิน แนะนำรอ READY ก่อนกดซ้ำ

## 2026-08-17 ยืนยันผลแก้ Live+backtest (ทดสอบจริง PID 32182)
- กด live ครั้งเดียว READY ใน 1.7 วิ (11:27:41 → 11:27:42.8) ไม่มีอาการกดรัวแล้วเงียบ
- สั่งเสียง backtest smc ทอง all → fetch 3 TF ขนานกัน (11:28:00-02) → ส่ง tool response 11:28:18 = รวม ~31 วิ (จากเดิม ~6 นาที)
- ไม่มี SocketTimeoutException / ไม่มี reconnect ระหว่าง tool ทำงาน → โมเดลพูดสรุปต่อได้ทันที
- ผลลัพธ์ all-TF ครบ 3 TF ถูกต้อง (15m PF 0.49 / 1h PF 0.45 / 4h PF 0.64 — SMC ยังขาดทุน ตามที่ทราบ)

## 2026-08-17 Audit ระบบ Signal/Backtest ทั้งหมด + แก้บั๊ก 3 จุด
ตรวจ: SignalMarkerProvider, SignalAlertProvider, StrategySignalProvider, computeTpSl/parameterizedTpSl, BacktestEngine, SmcSignals/SmcSignalDetector, ParamOptimizer, WalkForward, PermutationTest, MonteCarlo, OverfittingScore, BacktestEvolution
- สรุปความถูกต้อง: ทุกกลยุทธ์ edge-triggered, ไม่มี lookahead ในตัวหลัก (indicator ณ แท่ง i ใช้ข้อมูล ≤ i เท่านั้น), computeTpSl ตรง parameterizedTpSl เป๊ะ, SMC กัน lookahead ด้วย window 300 แท่งต่อ snapshot, live ใช้แท่ง n-2 (กันแท่งยังไม่ปิด)
- BUG A (แก้แล้ว): BacktestEngine หัก entry commission ซ้ำ 2 รอบ (balance -= entryCommission ตอนเข้า + หักอีกใน pnl ตอนปิด) → equity curve/balance ต่ำกว่าความจริงและไม่ตรง finalBalance — ลบบรรทัดหักตอนเข้าออก (ผล backtest ทุกครั้งที่เปิด costs จะดีขึ้นเล็กน้อยและสม่ำเสมอ)
- BUG B (แก้แล้ว): WalkForward คำนวณ OOS markers บน test slice แยก → indicator reseed (EMA200=NaN ช่วงต้น slice, ATR backward-fill) ทำสัญญาณ OOS เพี้ยน — เปลี่ยนเป็นคำนวณบน prefix (anchor 0) แล้ว filter เฉพาะโซนทดสอบ + BacktestEngine เพิ่มพารามิเตอร์ startIndex (วัดผลเฉพาะช่วง OOS แต่ ATR warm จาก prefix); BacktestEvolution ใช้ startIndex ด้วย
- BUG C (แก้แล้ว): 52H ยิงสัญญาณมั่วช่วง ~100 แท่งแรกของชุดข้อมูล (runHigh เพิ่งเริ่มสะสม prox=1.0 ตลอด) — เพิ่ม warmup skip i<100
- จดไว้ (ไม่แก้ เป็น design choice): Sharpe ใช้ √252 ทุก TF (ระบุว่าคร่าวๆ), W52H = high ของข้อมูลที่โหลด (ไม่ใช่ 52 สัปดาห์จริง), LIQ_SWEEP ต้องตรงทิศ structure ทำให้ sweep reversal ต้นน้ำถูกกรองทิ้ง, SMC TP=nearest level ทำ RRR จำกัด (รอ PriceMap)
- BUILD SUCCESSFUL; ยังไม่ commit

## 2026-08-17 Adaptive Optimize (เรียนรู้จากประวัติ + auto-apply)
- ตารางใหม่ OptimizationTrial (5.sqm + JarvisDatabase.sq + DatabaseDriverFactory additive) — บันทึก params/score/delta_vs_baseline ทุกครั้งที่ลองจูน ต่อ symbol/TF/กลยุทธ์ (เก็บ 200 รายการล่าสุด)
- AdaptiveOptimizer.kt: candidates = grid 25 + mutation รอบ params ปัจจุบัน (±10-25%) + mutation รอบ top-3 params ในประวัติ; hard bound กว้าง SL 0.3-6×/TP 0.5-10× (ไม่จำกัด ±30% เหมือน evolution เพราะทุกตัววัดจริง); เรียนรู้ direction effect (SL กว้าง/แคบ, TP ไกล/ใกล้ แล้วดี/แย่) ใช้เป็น tie-breaker + แสดง insight ในรายงาน; บันทึก baseline+top-5 กลับเข้าความจำทุกรอบ
- executeBacktestOptimize เขียนใหม่: baseline = tuned ปัจจุบันหรือ default → adaptive run → wf/perm/mc บน params ที่ชนะ → AUTO-APPLY เมื่อ score ดีกว่าเดิม ≥2% และเกรดไม่ overfit (apply=off = dry-run); tool definition อัปเดตให้โมเดลเข้าใจพฤติกรรมใหม่
- BUILD SUCCESSFUL; ยังไม่ commit

## 2026-08-17 Mix Strategies (โหวตหลายกลยุทธ์เป็น 1 signal) — เสร็จ + build ผ่าน

**MixSignalEngine.kt (ใหม่):** state-based voting — แต่ละกลยุทธ์โหวต +1/-1/0 จาก state ณ แท่งนั้น (MOM=sign ROC20, TR=EMA50/200, E=EMA14/60, UT=เหนือ/ใต้ trailing stop, REV=RSI<30/>70, DC/52H=sticky breakout state, 3BR=โหวตค้าง 5 แท่ง) — edge เมื่อ score ข้ามเกณฑ์ minVotes ครั้งแรก → marker MIX▲/MIX▼ (ไม่ lookahead, warmup 210 แท่ง); mixMarkers รับ cache=SeriesCache ได้ (กันคำนวณซ้ำตอน live)

**Backtest:** trading_backtest strategy=mix + params mix_strategies ("tsmom,trend,donchian,utbot") / mix_min_votes (default=ครึ่งจำนวนกลยุทธ์ปัดขึ้น) — ใช้ได้กับ interval=all; SL/TP MIX=1.5/2.5 ATR14 (StrategyParams.defaultsFor + computeTpSl + parameterizedTpSl else-branch)

**Live alert:** SignalAlertProvider.fetch อ่าน mix config (CoreMemory key mixcfg|SYMBOL|TF) → คำนวณ score ที่แท่งปิดล่าสุด + รวม edge MIX เข้า edges ปกติ; fields ใหม่ signal_mix_score / signal_mix_votes (รายละเอียดโหวตแต่ละตัว)

**Tool ใหม่ trading_mix_config** (set/show/clear ต่อ symbol+TF) ลงทะเบียน ToolRegistry 2 จุด + executor dispatch + persona item 8 อัปเดต

**Build:** :composeApp:assembleDebug SUCCESSFUL 2m47s

## 2026-08-17 Backtest Lab — หน้าจอผล backtest แบบกราฟ (UI แยกจากแชท)

**BacktestResultStore.kt (ใหม่):** singleton in-memory เก็บผล backtest 20 runs ล่าสุด (StateFlow) — TradingToolExecutor.runBacktestOne push ผลเข้าทุกครั้งที่รันสำเร็จ (รวม interval=all ทั้ง 3 TF)

**BacktestScreen.kt (ใหม่):** หน้าจอ "Backtest Lab" เปิดจากไอคอนกราฟแท่ง 📊 บนแถบบน
- chips เลือก run (symbol/TF ล่าสุด 8 รายการ)
- แท็บ "ภาพรวม" + แท็บแยกตามกลยุทธ์ เรียงตามคะแนน avgR อัตโนมัติ (ดีสุดอยู่แท็บแรก) มี ▲/▼ คะแนนบนแท็บ
- ภาพรวม: equity curve (Canvas เส้น+fill+เส้นทุนประ), metric cards 2 คอลัมน์ (กำไรสุทธิ/Win%/PF/Expectancy/MaxDD/Sharpe) แบบการ์ดมิติ shadow+gradient, donut pie ชนะ/แพ้/ค้าง, 8 ไม้ล่าสุด
- แท็บกลยุทธ์: กราฟสะสม R เฉพาะกลยุทธ์ + metric cards (สัญญาณ/ไม้/Win%/avgR/PF) + pie + ไม้ล่าสุดของตัวเอง
- chat ยังแสดงตารางสรุปเหมือนเดิม + เพิ่มบรรทัดชี้ไปหน้า Backtest Lab

**ข้อจำกัด:** ผลเก็บ in-memory หายเมื่อปิดแอป (ยังไม่ persist); กราฟแท็บกลยุทธ์เป็น cumulative R ไม่ใช่ balance จริง (engine ไม่ได้ track equity แยกตามกลยุทธ์)

**Build:** :composeApp:assembleDebug SUCCESSFUL (แก้ compile error 3 จุด: runningFold type param, Path.addPath คืน Unit, import toLocalDateTime)

## 2026-08-17 Multi-Session LongTask + Live READY Greeting

**ปัญหา 1 — backtest บล็อก AI หลายนาที ผู้ใช้ไม่รู้ว่าค้างหรือทำงานอยู่:**
- LongTaskRunner.kt (ใหม่, commonMain): scope แยก SupervisorJob + Dispatchers.Default, launch() คืน task id ทันที, มี StateFlow running + SharedFlow completions
- TradingToolExecutor: executeBacktest / executeBacktestOptimize / executeBacktestEvolve แปลงเป็น thin launcher — ตอบ ack ทันที ("รับคำสั่งแล้ว กำลังทำในเบื้องหลัง") งานจริงย้ายไป runBacktestTask/runOptimizeTask/runEvolveTask (คืน Pair<chatBody, speechSummary>)
- tool descriptions อัปเดตบอกโมเดลว่าห้ามสรุปผลจาก ack (ผลจะมาทีหลังอัตโนมัติ)
- JarvisViewModel collect completions:
  - live เปิดอยู่ → การ์ดเต็มลงแชท (storeMessage + AlertChatBus) + sendLiveClientText ให้ live model พูดสรุปเอง
  - live ปิดอยู่ → announceLongTaskCompletion() (expect/actual ตาม pattern wakeupAutomationService) → JarvisAutomationService ACTION_LONGTASK_ANNOUNCE → sendNotification + deliverChatAndVoice (reuse alert voice chain Live 3.1→2.5→Android TTS + การ์ดแชทพร้อม footer engine เสียง)

**ปัญหา 2 — กด live แล้วไม่รู้ว่า READY หรือยัง (READY ช้าหลายวิ ผู้ใช้พูดไปก่อน AI เงียบ):**
- reuse กลไก pendingGreetingOnReady ที่มีอยู่ (เดิมใช้เฉพาะยืนยันเปลี่ยนเสียง): startVoiceInput ตั้ง greeting "ทักผู้ใช้สั้นๆ 1 ประโยค" ทุกครั้งที่เปิด live — AI จะพูดทักเองทันทีที่ session READY ผู้ใช้รู้ว่าคุยได้แล้ว
- เพิ่ม orchestrator.setLiveGreetingOnReadyIfAbsent() กันทับ greeting ยืนยันเปลี่ยนเสียง

**Build:** :composeApp:assembleDebug SUCCESSFUL 1m30s

## 2026-08-17 fix: LongTask live announce ใช้ realtimeInput แทน clientContent + ACK voice rule สั้น
- **ปัญหา:** ทดสอบ multi-session รอบแรก — เปิด live ค้างไว้ สั่ง backtest ผ่านเสียง → backtest รันเสร็จจริง (log `📦 LongTask เสร็จ ... live=true`) แต่ไม่มีเสียงสรุปตามมา นิ่งไปเลย
- **Root cause:** `JarvisViewModel` collector `LongTaskRunner.completions` ใช้ `orchestrator.sendLiveClientText` (clientContent) — ตามคอมเมนต์ใน `LiveGeminiService.kt` พิสูจน์แล้วว่าขณะ audio streaming clientContent เป็นแค่ context ให้ model ไม่กระตุ้นให้ตอบเอง → ต้องใช้ realtimeInput
- **แก้ 3 จุด:**
  1. `JarvisOrchestrator.kt` — เพิ่ม `suspend fun sendLiveRealtimeText(text) = liveService.sendRealtimeText(text)`
  2. `JarvisViewModel.kt` — collector สาขา live on เปลี่ยนจาก sendLiveClientText → `sendLiveRealtimeText` (ส่งสรุปผลเข้า live เป็น realtime input ให้ model พูดตอบ)
  3. `LiveToolBridge.kt` — voiceRule แปลงเป็น when 3 กรณี: system_self_review เดิม, **isLongTaskAck** (`trading_backtest`/`trading_backtest_optimize`/`trading_backtest_evolve` → "[VOICE RULE - ACK] ตอบสั้น 1-2 ประโยค ห้ามสรุปยาว" แก้อาการ ack พูดรัวยาว 8-12 ประโยค), else rule เดิม
- **Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL — รอผู้ใช้ทดสอบว่า live พูดสรุปผล backtest แล้วหรือไม่

## 2026-08-17 เพิ่ม: log ผล Backtest ละเอียดลง logcat (ตรวจสอบตัวเลขได้โดยไม่ต้องเปิดแชท)
- **ทดสอบรอบ 2 (live off) ผ่าน:** สั่ง backtest BTC แล้วปิด live → `📦 LongTask เสร็จ (live=false)` → `📣 LONGTASK_ANNOUNCE` → notification + เสียงผ่าน Live 3.1 chain + การ์ดเข้าแชท (`busEmitted=true`) — ครบทั้ง 3 ช่องทาง
- **เพิ่มใน `TradingToolExecutor.runBacktestOne`:** หลัง engine รันเสร็จ log `═══ ผล Backtest SYMBOL/TF ═══` พร้อม: จำนวนแท่ง + ts range + source, ไม้/W/L/T, Win%, PF, Expectancy, กำไรสุทธิ%, MaxDD, Sharpe, ตารางย่อย per-strategy (สัญญาณ/เข้า/ข้าม/Win%/avgR/PF), และ 3 ไม้ล่าสุด (side/entry/exit/R)
- ครอบคลุมทั้งโหมด TF เดียวและ `interval=all` (เพราะ all เรียก runBacktestOne ทีละ TF อยู่แล้ว)
- **Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL

## 2026-08-17 fix: log ผล Backtest ไม่ขึ้นใน logcat ของผู้ใช้ — ย้ายไป tag JarvisVM
- **สาเหตุ:** tag `Backtest` (TradingToolExecutor) และ `LongTask` (LongTaskRunner "▶ เริ่มงานพื้นหลัง") ไม่เคยปรากฏใน log ที่ผู้ใช้ส่งมาเลยตั้งแต่เช้า ทั้งที่ code path รันจริง (ผลลัพธ์ออกถูก) → capture ของผู้ใช้ filter เฉพาะบาง tag (SmcApiService/JarvisVM/LiveGemini/AutomationService ฯลฯ ขึ้นปกติ)
- **แก้:** ใน `JarvisViewModel` completions collector หลังบรรทัด `📦 LongTask เสร็จ` เพิ่ม `logDebug("JarvisVM", "📦 ผลลัพธ์เต็ม [title]:\n<chatBody>")` — tag JarvisVM ผู้ใช้จับได้แน่, logDebug แบ่ง chunk 3500 bytes อัตโนมัติ body ยาว (เช่น 7KB) ก็ครบ
- log เดิมใต้ tag Backtest ยังเก็บไว้ (มีประโยชน์ตอน capture แบบไม่ filter)
- **Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL

## 2026-08-17 fix: ตรวจผล backtest/optimize/evolve จาก log เต็ม → แก้ 5 จุด
- **ยืนยันระบบทำงานถูก:** ผลลัพธ์เต็มขึ้น logcat (tag JarvisVM, แบ่ง part 1/3) ครบ, ตัวเลขตารางเทียบ 3 TF ตรงกับรายละเอียดย่อย, optimize auto-apply 5 กลยุทธ์ตามเกณฑ์ถูกต้อง
- **Fix 1 — float รก:** `TpSlParams.round2()` ปัด 2 ตำแหน่งใน clampDrift/clampStep + mutation ของ AdaptiveOptimizer (แก้ 2.4000000000000004 / 1.2100000000000002)
- **Fix 2 — evolve ต่อเนื่องจาก optimize:** `BacktestEvolution.evolve()` รับ `initialOverride` — runEvolveTask โหลด `getStrategyTuning(symbol, interval, kind)` เป็นฐานแทนค่า default เสมอ + แสดง "(ต่อจาก tuning ล่าสุด)/(ค่า default)" ในผล (เดิม optimize apply 2.5/2.4 แล้วแต่ evolve เริ่มจาก 2.0/3.0)
- **Fix 3 — heuristic 0 ไม้:** reflect() short-circuit เมื่อ totalTrades==0 → "ไม่มีไม้ในช่วงนี้ ข้อมูลไม่พอประเมิน → คง params" (ไม่เรียก AI กัน reflection มั่ว), guard เพิ่มใน ruleBasedAdjust/ruleNote, noChangeStreak นับเฉพาะรอบที่มีไม้จริง (เดิม 52H ได้ 0 ไม้ 4 รอบติดแต่บอก "โครงสร้างสุขภาพดี")
- **Fix 4 — สรุปเสียง evolve ตรงข้อมูล + ทาง apply:** executeBacktestEvolve parse ชื่อกลยุทธ์ที่ 📈ดีขึ้น/📉แย่ลง จากผลจริงใส่ speech (เดิม model พูดมั่วว่า UT/Donchian ดีขึ้นทั้งที่แย่ลง ตัวที่ดีจริงคือ Trend Following) + บอกจำนวนที่บันทึกเมื่อ apply=on / ชวนสั่ง apply เมื่อมีตัวดีขึ้น + tool description ระบุชัด: ผู้ใช้สั่ง "เอาไปใช้/บันทึก" → เรียก evolve apply=on เท่านั้น ห้าม remember_fact (เดิม AI เรียก remember_fact แทน → params ไม่ถูก apply จริง)
- **Fix 5 — SMC ขาดทุนทุก TF:** root cause หลัก = MIN_RRR ถูกเข้มจาก 1.2 (ต้นฉบับ MT5) เป็น 1.8 → selection bias เหลือเฉพาะสัญญาณ TP ไกล → win rate ต่ำ + SL structure แคบ (5-9 จุด) ทำ notional ใหญ่ commission กิน ~0.6-0.7R/ไม้ (อธิบาย pnlR ติดลบเกิน -1R เช่น -1.68R) → กลับมา MIN_RRR 1.2 ตรงต้นฉบับ (กระทบทั้ง backtest และ live SMC alert ให้ตรงกับ MT5 engine)
- **Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL — รอผู้ใช้ทดสอบ: backtest smc ทองคำ 1h (ดูว่า SMC ดีขึ้นไหม) + optimize แล้ว evolve ต่อ (ดู "(ต่อจาก tuning ล่าสุด)")

## 2026-08-17 แก้บั๊ก strategy=smc + ข้อความ learning history (รอบ log 18:49)

**ตรวจ log ทดสอบ 18:49–18:52 พบ:**
1. ✅ apply=on บันทึก tuning จริง (💾 บันทึก tuning แล้ว 3 กลยุทธ์), model เรียก tool ถูก ไม่ใช้ remember_fact
2. ✅ "(ต่อจาก tuning ล่าสุด)" ขึ้นทุกกลยุทธ์, float ใหม่สะอาด (2.7/4.4, 0.72/0.96)
3. ⚠️ บั๊ก: `trading_backtest_optimize/evolve({strategy=smc})` ตก else → รัน all 8 classic เงียบๆ (SMC ใช้ SL/TP จาก structure ไม่ใช่ ATR mult → tune ไม่ได้อยู่แล้ว)
4. ⚠️ ข้อความ "เรียนจากประวัติ 6 ครั้ง: ยังไม่มีประวัติพอเรียนรู้" ขัดกันเอง
5. ⚠️ "เดิม: TP 2.4000000000000004×" — float รกจากค่า tuning เก่าใน DB (โหลดมาแสดงตรงๆ)

**แก้ไข:**
- `TradingToolExecutor.kt` runOptimizeTask + runEvolveTask: guard ก่อนดึงแท่งเทียน — strategy=smc → คืนข้อความอธิบายว่า SMC tune ไม่ได้ (structure-based SL/TP) พร้อม list 8 กลยุทธ์ที่ใช้ได้; strategy ไม่รู้จัก → ❌ แจ้งชื่อที่เลือกได้ ไม่รัน all เงียบๆ อีก
- `TradingToolExecutor.kt` runEvolveTask: โหลด tuning baseline ผ่าน `TpSlParams.round2()` ก่อนใช้/แสดง (แก้ 2.4000000000000004)
- `AdaptiveOptimizer.kt` insightOf(): fallback "ยังสรุปทิศทางไม่ได้ (ต้องมี trial ทั้งสองทิศ ≥2 ครั้ง/ทิศ)"; executor แสดง "ยังไม่มีประวัติการจูน" เมื่อ learnedFromTrials == 0
- Build: `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL

**ค้าง (รู้แล้ว ยังไม่แก้):** สัญญาณ SMC 57 vs เช้า 174 บน XAUUSD 1h ชุดเดียวกันหลังปรับ MIN_RRR 1.8→1.2 (คาดว่าสัญญาณควรเพิ่ม) — ไฟล์ uncommitted ไม่มี history เทียบ อาจมีการแก้ filter อื่นระหว่างวัน; backtest SMC 1h ล่าสุด: 42 ไม้ win 23.8% PF 0.52 — ทำกำไรเฉพาะตลาด SIDEWAYS (+0.24 avgR) แต่ขาดทุนหนักใน BULL/BEAR

## 2026-08-17 ตรวจ log ทดสอบ 19:36 + แก้ 429 storm / commission model

**สิ่งที่ทำงานถูก (ยืนยันจาก log):**
- Live greeting ทำงาน: "สวัสดีค่ะเจ้านาย พร้อมคุยแล้วค่ะ" หลัง session READY ~1 วิ (รอบแรกโดน VAD แทรก → TTS fallback ทำงานถูก)
- LongTask multi-session ครบ: backtest all (47s) → optimize (20s) → evolve ×3 — ระหว่างรอ live คุยต่อได้, เสร็จแล้วแจ้งเข้า live อัตโนมัติ
- evolve 3 รอบมาจากผู้ใช้สั่ง 3 ครั้งจริง (ไม่ใช่บั๊กเรียกซ้ำ) — apply=off ไม่บันทึก, apply=on บันทึก 1 กลยุทธ์ ถูกต้อง
- heuristic fallback ของ evolution ทำงาน — task จบ ok=true แม้ AI reflection ตาย

**ปัญหาที่พบและแก้:**
1. **429 storm**: evolve ยิง gemini-3.5-flash-lite 294 ครั้งใน ~6 วิ (64 reflection calls รัวๆ ไม่มี backoff) → เพิ่ม circuit breaker ใน BacktestEvolution: เจอ 429/quota ครั้งแรกปิด AI ทั้ง task เหลือใช้ heuristic + delay(400) หลัง AI call สำเร็จ กัน burst (instance ใหม่ทุก task = reset อัตโนมัติ)
2. **Commission model ผิดสเกล**: BacktestConfig.commissionPct=0.00045 (4.5 bps/ข้าง จาก moss ซึ่งเป็นสเกล crypto) — กับทอง ~4380 กลายเป็น ~0.3–0.6R ต่อไม้ ทำไม้แพ้ลึกเกิน −1R (เช่น SMC avgR −1.41) บิดผลทุกกลยุทธ์ → เปลี่ยน default เป็น 0.0 (CFD/forex ต้นทุนอยู่ใน spread 0.2 อยู่แล้ว) ⚠️ ผล backtest/tuning หลังจากนี้จะสูงกว่าก่อนหน้าเล็กน้อยทุกกลยุทธ์ เทียบกับประวัติ trial เก่าไม่ได้ตรงๆ
3. **SMC backtest 1h: 57 สัญญาณ → 3 ไม้ 0% win** (จาก 42 ไม้ 23.8% ตอน 18:49) — ยังหา root cause ไม่เจอ: โค้ด backtest path ไม่ได้เปลี่ยนระหว่าง 2 รอบ สันนิษฐานข้อมูลแท่งเทียนเปลี่ยน (ตลาดเปิดจันทร์ bars ใหม่เข้ามา structure SL/TP ขยับ) ตอนนี้มี git history แล้ว เทียบย้อนได้ครั้งหน้า

Build: `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL

## 2026-08-17 ตรวจ log ทดสอบ 23:46 (หลังแก้ 429/commission)

**ผลยืนยัน:**
- ✅ Circuit breaker ทำงาน — 429 เหลือแค่ 5 บรรทัด (จาก 294) evolve จบเร็ว ไม่ยิงซ้ำ
- ✅ Commission fix มีผลจริง — SMC avgR ดีขึ้นทุก TF (15m: −1.41→−0.81, 1h: −1.06→−1.00, 4h: PF 0.33→0.50) ยืนยันว่า 4.5bps บิดผลมาก่อน
- ✅ LongTask + live + สรุปเสียง ครบทุกรอบ; optimize auto-apply 4 กลยุทธ์; evolve heuristic ปรับ params สมเหตุสมผล
- ✅ Backtest ครั้งนี้ดึง 5000 แท่ง (1h)

**แก้เพิ่มรอบนี้:**
- เพิ่ม SMC trade forensics: dump ทุกไม้ SMC (entry/SL/TP/ระยะ/RR/exit/pnlR) ใต้ tag JarvisVM — เพราะ dump เดิมอยู่ใต้ tag "Backtest" ที่ไม่อยู่ใน filter ที่ผู้ใช้ capture จึงไม่เห็นใน log
- แก้อักษรจีนหลงในข้อความ UI "微调" → "ปรับเล็กน้อย" (ruleNote + comments)

**ค้าง — SMC 0% win 15m/1h (3-7 ไม้จาก 37-57 สัญญาณ):** ไม่ใช่ต้นทุนแล้ว (avgR 1h = −1.00 เป๊ะ = โดน SL ล้วน) สมมติฐานถัดไป: entry-at-close ไม่ตรงจุดเข้าจริงของ SMC (live เข้าตอนราคาแตะโซน OB/FVG ไม่ใช่ปิดแท่งสัญญาณ) — รอ forensics dump จากรอบทดสอบหน้ามายืนยัน

Build: BUILD SUCCESSFUL

## 2026-08-18 Forensics SMC — พบ root cause: SL ติดจมูก + filter SL ขั้นต่ำ

**Forensics dump (รอบ 00:16) เปิดความจริง:**
- 15m: SL dist 0.6 / 2.4 / 3.9 / 8.1 จุด ทั้งที่ ATR(15m) ~3-5 → ต่ำกว่า noise 1 แท่ง โดนกวาด 100% ก่อน TP
- 1h: SL dist 42-113 (สมเหตุสมผล) แต่ 3 ไม้ก็แพ้หมด, 4h: 4 ไม้ ชนะ 1 (TP +1.52R)
- ไม้ SELL SL=0.6 แพ้แค่ −0.14R เพราะ leverage clamp 10x บังคับ qty เล็กลง — engine คำนวณถูกแล้ว
- สรุป: ไม่ใช่ entry ผิดจุด/ไม่ใช่ต้นทุน แต่ detector ปล่อยสัญญาณที่ structure SL แคบระดับ noise → แพ้ทิ้งทันที

**แก้ไข — SmcSignalDetector.kt:**
- เพิ่มชั้นกรองที่ 5: `MIN_SL_ATR = 0.75` — ตัดสัญญาณที่ |entry−SL| < 0.75×ATR14 (computeAtr จาก SmcUtils) ใช้ได้ทั้ง live alert และ backtest เพราะอยู่ใน detect() ตรงกลาง
- ผลข้างเคียง: live signal alert ของ SMC จะเด้งน้อยลง (ตัดสัญญาณขยะทิ้ง) — เป็นพฤติกรรมที่ตั้งใจ

**อื่นๆ ใน log 00:16:** 429 เหลือ 4 บรรทัด (breaker ปกติ), evolve/optimize/backtest จบครบ, เสียงสรุปปกติ

Build: BUILD SUCCESSFUL

## 2026-08-18 แก้ non-stream generateResponse ไม่มี fallback (429 ซ้ำทุกรอบ)

**ปัญหาที่ผู้ใช้รายงาน:** 429 ที่ gemini-3.5-flash-lite ขึ้นซ้ำทุกรอบ ทั้งที่มีระบบ fallback ทั้ง key และโมเดล
**Root cause:** path streaming (generateResponseFlow) มี key rotation + model chain + persist ครบ แต่ path non-stream (generateResponse — ที่ evolution reflection และ nested AI summaries ใช้) ยิงโมเดลเดียวครั้งเดียวแล้วคืน "⚠️ Error 429" ไม่เคยสลับ

**แก้ไข — GeminiService.generateResponse():**
- เพิ่ม fallback เทียบ streaming path: 429/500/503 → หมุน API key ถัดไปใน apiKeysOverride ก่อน (โควต้าแยกต่อ key) → ถ้าหมดค่อยสลับโมเดลตาม fallbackModelsOverride/ModelConfig.GEMINI_FALLBACK_MODELS
- สำเร็จด้วยค่าที่สลับแล้ว → onWorkingConfigChanged persist ลง settings (รอบถัดไปเริ่มที่ค่าที่ใช้ได้ ไม่กลับไปชนลิมิตเดิม)
- exception/timeout: retry 1 ครั้งที่ 45s (พฤติกรรมเดิม) แล้วค่อยหมุน key/โมเดล
- เมื่อ chain หมด → คืน "⚠️ Error 429 (ลองทุก key+โมเดลแล้ว)" ยังมี "429" ในข้อความ → circuit breaker ของ BacktestEvolution ยังทำงานถูก

Build: BUILD SUCCESSFUL

## 2026-08-18 ตรวจ log 00:50 — fallback non-stream ใช้งานจริง + SMC filter มีผล

**ยืนยันจาก log:**
- ✅ Non-stream fallback ทำงาน: "API key rotation → ..." + "Non-stream fallback works — persist" (หมุน 3 keys สลับกันเมื่อโควต้าเต็ม โมเดลคง flash-lite เพราะ key ใหม่หลุดลิมิต) — 429 เหลือ 18 บรรทัดจาก ~192 reflection calls (interval=all = 3TF×8กลยุทธ์×8รอบ)
- ✅ AI reflection กลับมาทำงาน 16/16 รอบ (0 heuristic) — ก่อนหน้าหลุด heuristic หมดเพราะ 429
- ✅ SMC MIN_SL_ATR filter มีผล: สัญญาณ 15m 37→20, ไม้ 7→3 (ไม้ SL=0.6/2.4/3.9 หายหมด)
- ✅ Classic strategies สุขภาพดีหลัง commission fix: 15m รวม +32.7%, EMA14/60 PF 4.60, 52W PF 5.02, ไม้แพ้ = −1.00R เป๊ะ (ไม่มีต้นทุนบิด)

**สถานะ SMC ล่าสุด:** 15m 0% (3 ไม้), 1h 0% (3 ไม้), 4h 50% (+0.26R, PF 1.44) — sample เล็กมาก TF ใหญ่ดูมีหวัง TF เล็กยังแพ้ ต้องดูข้อมูลเพิ่มหลัง filter สะสมสัญญาณ

Build: BUILD SUCCESSFUL (ก่อนหน้า), log นี้จาก build ที่มีทุก fix วันนี้

## 2026-08-18 บั๊กใหญ่: optimize/evolve interval=all เซฟ tuning ใต้ key "all" — live ไม่เคยใช้จริง

**พบจาก log 00:53:** "AUTO-APPLY แล้ว 7 กลยุทธ์ — signal alert ของ XAUUSD **all**" → interval=all ไม่ขยายเป็น 3 TF เหมือน backtest แต่ถูกส่งตรงๆ เป็น interval "all": จูนบนข้อมูลชุดเดียว (fetchBacktestCandles fallback TF ใด TF หนึ่ง) แล้วเซฟ tuning ใต้ getStrategyTuning(symbol, "all", kind) — live alert lookup ด้วย 15m/1h/4h เลยไม่เคยอ่านเจอ = **จูนมาทั้งหมดไม่มีผลกับ live แม้แต่ครั้งเดียว** ทั้งที่ log บอก AUTO-APPLY แล้ว

**แก้ไข — TradingToolExecutor.kt:**
- executeBacktestOptimize + executeBacktestEvolve: interval=all ขยายเป็น listOf("15m","1h","4h") รันทีละ TF รวมผล (เทียบ executeBacktest เดิม) — tuning เซฟใต้ TF จริง live ใช้ได้ทันที
- สรุปเสียงรวมจำนวน applied ข้าม TF และบอก "all TF (15m/1h/4h)"

**บริบทคำถามผู้ใช้ "กลยุทธ์อื่นคะแนนยังไม่ดี":** ผลล่าสุด (หลัง commission fix) แสดง pattern ชัด — ไม่มีกลยุทธ์ไหนดีทุก TF: EMA14/60 เด่น 15m (PF 4.60), 52W เด่น 15m (5.02) แต่ 1h แย่ (0.41), 3BR เด่น 1h (3.24), Trend เด่น 4h (2.72), UT Bot แย่ทุก TF — แนวทางถัดไป: per-TF strategy selection / mix voting แยก TF มากกว่าบังคับทุกกลยุทธ์ทุก TF

Build: BUILD SUCCESSFUL

## 2026-08-18 Per-TF Strategy Gate (บล็อก alert ของกลยุทธ์ที่แพ้ใน TF นั้น)

**สาเหตุ:** ข้อมูลพิสูจน์ว่าไม่มีกลยุทธ์ไหนดีทุก TF (EMA14/60 เด่น 15m แต่ 1h ธรรมดา, 3BR เด่น 1h แต่ 15m แพ้, UT Bot แพ้ทุก TF) → ยิง alert เฉพาะคู่กลยุทธ์×TF ที่ผ่านเกณฑ์

**สถาปัตยกรรม:**
1. ตารางใหม่ `StrategyHealth` (migration 6.sqm): symbol/interval/kind → PF, avgR, winRate, trades, bars, updated_at (UNIQUE ต่อคู่)
2. `AutomationManager`: saveStrategyHealth / getStrategyHealths / isStrategyGated(minPf=1.0, minTrades=5)
3. `TradingToolExecutor.runBacktestOne`: หลัง backtest เสร็จ → เซฟ health ของทุกกลยุทธ์ที่รันลง DB อัตโนมัติ
4. `SignalAlertProvider.fetch`: ก่อนยิง alert — กรอง edges (classic/MIX) และ smcNew ด้วย isStrategyGated; โดนบล็อก → log "⛔ Gate บล็อก ..." + ส่ง signal_gated กลับใน payload

**กติกา:** บล็อกเมื่อมีข้อมูล backtest และไม้ ≥5 และ PF < 1.0 เท่านั้น — กลยุทธ์ที่ยังไม่เคย backtest = ผ่าน (ไม่บล็อกมั่ว)
**หมายเหตุ:** gate เริ่มมีผลหลังรัน backtest ครั้งแรกบน build นี้ (seed health ลง DB); tuning เก่าที่เซฟใต้ interval "all" (บั๊กก่อนหน้า) ยังค้างใน DB แต่ไม่ถูกอ่าน ไม่กระทบ

Build: BUILD SUCCESSFUL

## 2026-08-18 ตรวจ log 01:38 (build มี gate) + ย้าย log gate ไป JarvisVM

**ผลรอบนี้:**
- ทุก task จบ ok=true: backtest all ×2, optimize tsmom 1h (ไม่มีค่าดีกว่า คงเดิม), evolve all 1h, evolve threebar apply=on (บันทึก)
- 429 หลุด 5 ครั้งแต่ key rotation รับต่อได้
- ⚠️ ยืนยันการทำงานของ gate จาก log ไม่ได้ — "💾 StrategyHealth saved" อยู่ใต้ tag Backtest และ "⛔ Gate บล็อก" ใต้ tag SignalAlert ซึ่งไม่อยู่ใน filter ที่ผู้ใช้ capture → ย้ายทั้ง 2 ไป tag JarvisVM แล้ว (build ผ่าน)
- สังเกต: evolve all 1h รอบนี้ MOM/TR แย่ลง — ผู้ใช้สั่ง "บันทึกค่า" model เลือก apply เฉพาะ threebar (กลยุทธ์เดียวที่ดีขึ้น) ถือว่าถูกต้อง

Build: BUILD SUCCESSFUL

## 2026-08-18 แก้ 429 ซ้ำทุก task — Key Health Registry + 404 fallback + เพิ่มโมเดล 3.6/3.7

**สาเหตุที่ผู้ใช้เห็น 429 เป็นโมเดลเดิมทุกครั้ง (ไม่ใช่บั๊ก fallback):** persist ทำงานถูกแล้ว (log: "Non-stream fallback works — persist" + "JarvisVM: Persist working fallback config" ทุกครั้ง) แต่ evolve ยิง reflection ~64 calls/TF ทำ key ที่เพิ่ง persist ติดลิมิตอีก → task ถัดไปเริ่มด้วย key ที่ตายแล้ว → 429 รอบแรกเสมอ → หมุน key → persist วนลูป และโมเดลไม่เคยสลับเพราะ key rotation (3 keys โควต้าแยกกัน) สำเร็จก่อนเสมอ

**แก้ไข — GeminiService.kt:**
1. companion object `keyDeadUntilMs` (แชร์ข้ามทุก instance/task) — key ที่ติด 429 ถูก mark พัก: per-minute quota = 90s, per-day (parse "per_day" จาก error body) = 12 ชม.
2. ทั้ง streaming (generateResponseWithTools) และ non-stream (generateResponse): ก่อนยิง request แรก ถ้า key เริ่มต้นยังอยู่ในช่วงพัก → ข้ามไป key ที่มีชีวิตทันที (persist เดิมยังทำงาน อัปเดต settings ตาม)
3. trySwitchFallbackKey/switchKey: เลือก key ที่ไม่ติด cooldown ก่อน เผื่อตายหมดค่อยกลับมาลอง key ที่พักอยู่
4. เพิ่ม 404 → fallback: เดิม 404 (โมเดลไม่มีจริง) return error ทันทีไม่ลองโมเดลอื่น (เคส gemini-3.1-pro) — ตอนนี้ 404 สลับโมเดลถัดไปโดยไม่เผา key rotation (modelNotFound flag)
5. log error body 300 → 700 chars เพื่อเห็นชนิดโควต้า (per-minute vs per-day) ใน logcat

**ModelConfig.kt:** เพิ่ม gemini-3.6-flash, gemini-3.7-flash ท้าย chain ตาม list โมเดลที่ผู้ใช้ยืนยันใช้ได้ (chain เดิม 6 ตัว → 8 ตัว); ถ้า id ไม่ตรงจริง 404 fallback ใหม่จะข้ามให้อัตโนมัติ

Build: BUILD SUCCESSFUL

## 2026-08-18 รีวิวความถูกต้องทุก strategy (เทียบต้นฉบับ)

**สรุปการตรวจ (code review เทียบ mt5-core-server + OLD_Code):**
- SignalMarkerProvider.compute (8 กลยุทธ์): ทุกตัว point-in-time (ใช้ข้อมูล ≤ แท่ง i เท่านั้น) + edge-triggered — ไม่มี lookahead: TSMOM(ROC20 flip)✓, Trend(EMA50/200 cross)✓, REV(RSI14+BB20 edge)✓, DC(Donchian20 ไม่รวมแท่งปัจจุบัน)✓, 52H(running high, warmup 100 แท่ง)✓, E(EMA14/60)✓, UT(trailing stop สูตรตรง Pine ต้นฉบับ key=2×ATR6)✓, 3BR(pattern 3 แท่ง)✓
- BacktestEngine: SL ก่อน TP เมื่อชนทั้งคู่ (conservative)✓, เข้าที่ close แท่งสัญญาณ (ตรง fetchStats)✓, 1 ไม้ต่อครั้ง✓, commission หักครั้งเดียวตอนปิด (fix ไปแล้ว)✓, backtest ใช้ compute(maxPerKind=Int.MAX_VALUE) ไม่ถูก cap 12 จุด✓
- SmcSignalDetector เทียบ SignalDetector.ts (964 บรรทัด): minRRR=1.2✓ minConfluenceStars=2✓ obProximity=0.15%✓ SL=ob.bottom−tolerance×2✓ FVG fill 50%/SL=size×0.5✓ RSI Wilder✓ — adaptation ที่ต่างจากต้นฉบับถูกบันทึกไว้ใน comment ครบ (bias proxy แทน MTF, findNearestLevel แทน PriceMap, filter 5 ชั้นเพิ่ม)
- SmcSignals.newSignalsAt: window subList(0..idx+1) ต่อแท่ง — กัน lookahead✓

**สถิติล่าสุด (XAUUSD 5000 แท่ง, ต้นทุน on) — ยืนยัน pattern per-TF:**
- 15m: EMA14/60 PF 4.60, 52W 5.02 ดี / UT 0.95, TR 0.72, 3BR 0.57, SMC 0.00 แพ้
- 1h: 3BR 3.24, DC 1.80, TR 1.60 ดี / UT 0.61, REV 0.52, 52W 0.41, SMC 0.00 แพ้
- 4h: TR 2.72, MOM 1.65, SMC 1.44 ดี / 52W 0.65, UT 0.86 แพ้
→ Per-TF Strategy Gate (seed "💾 StrategyHealth saved: 9 kinds" ยืนยันใน log แล้วทั้ง 3 TF) ครอบคลุมเคสนี้พอดี — กลยุทธ์ที่แพ้ใน TF นั้นถูกบล็อกจาก live alert อัตโนมัติ
- SMC 15m/1h ยัง 0/3 ไม้ (filter SL≥0.75×ATR ตัดสัญญาณติดจมูกไปแล้ว 17/20 แต่ที่เหลือยังโดนกวาด) — sample เล็กมาก (2-3 ไม้/TF) สรุปไม่ได้ 100% ต้องสะสมข้อมูล; 4h เริ่มนิ่ง (PF 1.44)

Build: BUILD SUCCESSFUL

## 2026-08-18 แก้ 429 รอบ 2 (แก้ที่ root cause จริง): รอตาม hint "Please retry in Xs" แทนการหมุน key

**ค้นพบสำคัญจาก log 02:49 (build มี Key Health แล้ว):** error body เต็มเผยว่า 429 คือ **per-minute limit 15 RPM ต่อ key สำหรับ gemini-3.5-flash-lite** ("Quota exceeded ... limit: 15 ... Please retry in 2-54s") — โควต้ารีเซ็ตในไม่กี่วินาที ไม่ใช่รายวัน การหมุน key จึงไม่ช่วยระยะยาว: evolve ยิง reflection เร็วเกิน (interval ~2-4s = ~15-25 RPM) ทำ key ตายทีละตัวจนครบ (ElLY→VEj8→qgCA→ULGY) แล้ววนซ้ำ และ cooldown 90s แบบเดิมพัก key นานเกินจริง (key ฟื้นใน ~4s)

**แก้ไข:**
1. GeminiService (ทั้ง streaming + non-stream): 429 ที่มี hint "Please retry in Xs" (≤60s) → **delay ตาม hint แล้วลอง key/โมเดลเดิมซ้ำ** (สูงสุด 3 ครั้ง/การเรียก) ก่อนค่อยหมุน key — ทำตามที่ API แนะนำตรงๆ ไม่เผา key อื่น
2. markKeyDead: per-minute พักตาม hint+1s (ไม่ใช่ 90s ตายตัว), ไม่มี hint = 30s, per-day = 12 ชม.
3. BacktestEvolution: throttle reflection 400ms → 2000ms และย้ายมา delay ทุกครั้งหลังเรียก AI (เดิม delay เฉพาะตอนสำเร็จ) → interval รวม ~4-6s/req ใกล้เพดาน 15 RPM
4. parseRetryAfterMs รองรับทั้ง "Please retry in Xs" และ RetryInfo.retryDelay

**ผลที่คาด:** evolve จะช้าลงเล็กน้อยแต่แทบไม่เห็น 429 ใน log อีก และไม่วนเปลี่ยน key/โมเดลโดยไม่จำเป็น

Build: BUILD SUCCESSFUL

## 2026-08-18 แก้ Evolution ไม่เรียนรู้ (forensics: evolved แพ้ baseline 8/8 กลยุทธ์แบบเป็นระบบ)

**หลักฐานจาก log 03:10 (evolve XAUUSD 1h):** ทุกกลยุทธ์ evolved แย่กว่า baseline (MOM -4.78R, REV -13.19R, DC -18.06R, 3BR -14.67R ฯลฯ) — AI reflection ทุกรอบแนะนำเหมือนเดิม "ขยาย SL + ลด TP" เพราะ prompt เดิมเขียนนำทางเดียว ("SL เยอะ = SL แคบเกิน / ค้างเยอะ = TP ไกลเกิน") → RR พังต่ำกว่า 1 (เช่น MOM 2.0/3.0→3.8/3.23 = RR 0.85) → แพ้โดยโครงสร้าง ไม่มีหน่วยความจำข้ามรอบ/ข้ามรัน สถิติต่อรอบ 2-10 ไม้ = noise และเกณฑ์ apply เดิม (gain>0 บน adaptive path) เสี่ยงเซฟค่าฟลุ๊ค

**แก้ไข 3 ข้อ:**
1. **Apply gate บนข้อมูลเต็ม** (TradingToolExecutor.runEvolveTask): finalParams ต้อง full-backtest 5000 แท่งแล้วชนะ initial ทั้ง expectancyR และ PF≥1.0 และไม้≥10 ถึงเซฟ — แสดงบรรทัด "🔎 ผลเต็ม N แท่ง: เดิม PF/avgR → ใหม่ PF/avgR → APPLY/ไม่ apply" ทุกกลยุทธ์
2. **Reflection Memory**: ใช้ตาราง OptimizationTrial ที่มีอยู่ — evolve บันทึก trial ทุกรอบ (source=evolve) และดึง 5 รายการล่าสุดใส่ prompt ("ทิศที่เคยปรับแล้วแย่ลง ห้ามทำซ้ำ")
3. **แก้เข็มทิศ RR** (BacktestEvolution + StrategyParams): prompt ใหม่มีกฎเหล็ก 4 ข้อ (ห้าม RR<1.2, winRate สูงแต่ PF ต่ำ = RR ต่ำเกินห้ามลด TP, ห้ามขยาย SL ซ้ำถ้ารอบก่อนไม่ดีขึ้น) + ปฏิเสธคำแนะนำ AI ที่ RR<1.2 ระดับโค้ด + `TpSlParams.enforceRrFloor` clamp ผลลัพธ์สุดท้าย (REV ยกเว้นเพราะ TP=BB basis, 3BR floor ที่ tpMult) + min-trades gate 0→5 ไม้/รอบ (น้อยกว่านี้ = noise คง params ไม่เรียก AI)

Build: BUILD SUCCESSFUL

## 2026-08-18 Entry Params Tuning (ข้อ 4 — จูนจุดเข้าได้แล้ว ไม่ใช่แค่ SL/TP)

**ปัญหาเดิม:** edge ของกลยุทธ์อยู่ที่ "จุดเข้า" แต่ optimize/evolve จูนได้แค่ SL/TP — params จุดเข้า (ROC 20, EMA 50/200, RSI 30/70, Donchian 20, W52 0.98/0.90, EMA 14/60, UT key=2.0 ATR6) เป็น hard-code

**สิ่งที่ทำ:**
1. **`EntryParams.kt` (ใหม่)** — data class รวม params จุดเข้าทุก kind + `gridFor(kind)` grid เล็ก 4-12 combos (MOM lookback 10-40, TR 5 คู่ EMA, REV RSI threshold 9 ชุด, DC period 10-55, 52H proximity 9 ชุด, E 4 คู่, UT key×ATR 12 ชุด) + serialize/deserialize "k=v;k=v"; default ทุก field = ค่าคงที่เดิมเป๊ะ → พฤติกรรมเดิมไม่เปลี่ยน; 3BR เป็น pattern ล้วนไม่มีอะไรจูน
2. **`SignalMarkerProvider.compute`** รับ `entryParams: Map<String, EntryParams>` แทนค่าคงที่ทุก kind (ค่า default = ค่าเดิม call site เก่าไม่พัง)
3. **DB 7.sqm** — ตาราง `EntryTuning(symbol, interval, kind, params_json, score, expectancy_r, profit_factor, trades, grade, source, updated_at)` + queries + wrapper `AutomationManager.saveEntryTuning/getEntryTuning/getTunedEntryParams` (โหลดเป็น Map<kind, EntryParams> ข้าม grade=overfit)
4. **Optimize เพิ่ม `scope`**: `sltp` (default เดิม) | `entry` (จูนจุดเข้าอย่างเดียว โดย SL/TP คงค่าปัจจุบัน) | `both` (จูนจุดเข้าก่อน แล้วจูน SL/TP ต่อบนจุดเข้าใหม่ — recompute markers + WalkForward รับ entryParams ด้วย); apply gate เดียวกับ evolve fix: วัดบนข้อมูลเต็ม + expectancy ดีกว่า + PF≥1.0 + ไม้≥10 ถึงเซฟ; tool definition เพิ่ม arg scope
5. **Live ใช้ค่าจูนทุกจุด**: SignalAlertProvider.fetch (signal alert จริง), history stats, StrategySignalProvider.fetch (5 กลยุทธ์ classic ใช้ tuned params), runBacktestTask/runEvolveTask/optimize baseline, chart markers (SignalMarkerProvider.fetch) — ทุกจุดโหลด EntryTuning ก่อน compute → backtest วัดบนจุดเข้าเดียวกับ live เสมอ
6. **ข้อจำกัดที่ทราบ:** MixSignalEngine (MIX voting) ยังใช้ค่าคงที่เดิม (series cache ของตัวเอง) — entry tuning ยังไม่มีผลกับ MIX; SMC structure-based ไม่เกี่ยว

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รอผู้ใช้ทดสอบ)

## 2026-08-18 Review ระบบ signal/backtest/optimize/evolve — แก้ 6 จุด

ตรวจทุกไฟล์หลัก (BacktestEngine, ParamOptimizer, AdaptiveOptimizer, WalkForward, MonteCarlo, PermutationTest, OverfittingScore, BacktestEvolution, StrategyParams, SignalAlertProvider, SignalMarkerProvider, AutomationManager) — พบและแก้:

1. **BacktestEngine — gap-through-SL**: เดิมออกที่ราคา SL เป๊ะทุกครั้ง แม้แท่งเปิดกระโดดเลย SL → ประเมินดีเกินจริงในช่วงข่าว/ตลาดเปิด; แก้เป็น SL exit = min(SL, open) สำหรับ BUY / max(SL, open) สำหรับ SELL (TP ยัง fill ที่ TP เพราะเป็น limit order)
2. **BacktestEngine — Sharpe annualization**: เดิม × √252 ตายตัว (สมมติ daily) → เทียบข้าม TF ไม่ได้; แก้เป็นคำนวณ bars/ปี จากระยะห่างแท่งจริง (data-driven, ทองปิดเสาร์อาทิตย์ปรับเองอัตโนมัติ) — ⚠️ score ใน OptimizationTrial เก่าอยู่คนละสเกล แต่ direction learning ใช้ delta ภายในรันเดียวกันจึงยังใช้ได้
3. **PermutationTest — null distribution เบี้ยว**: run ที่พังถูกแทนด้วย 0.0 ใน null distribution → realSharpe ชนะง่ายเกินจริง; แก้ตัด run พังทิ้ง + p-value ใช้ +1 correction ((beat+1)/(n+1)) กัน p=0.000 ที่เป็นไปไม่ได้ทางสถิติ
4. **OverfittingScore — walk_forward หายเมื่อ IS Sharpe ≤ 0**: เดิมข้าม component ทำกลยุทธ์ขาดทุน in-sample ได้เกรด healthy (เช่น REV IS=-0.13 ได้ 16% healthy); แก้ IS≤0 → walk_forward=100 (แย่สุด)
5. **AdaptiveOptimizer — improved threshold พังเมื่อ baseline ติดลบ**: ×1.02 ของเลขติดลบทำ threshold ต่ำลง → ค่าแย่กว่านิดเดียวผ่านเกณฑ์; แก้เป็น additive margin max(2%|baseline|, 0.005) + ไม่บันทึก baseline trial ที่ score=-999 (ขยะในความจำ)
6. **Optimize AUTO-APPLY gate หลวม**: เดิมขอแค่ improved + ไม่ overfit → params ของกลยุทธ์ PF<1 ก็ถูก apply ได้; เพิ่ม PF≥1.0 และไม้≥10 (เกณฑ์เดียวกับ evolve/entry gate)
7. **reasonFor/strategyName ตายตัว**: ข้อความเหตุผล alert hard-code "ROC 20 แท่ง", "EMA50/200" ฯลฯ → ผิดเมื่อ entry ถูกจูน; แก้ reasonFor รับ EntryParams แล้วแสดงค่าจริงที่ใช้, strategyName ของ TR/E เป็นแบบไม่ผูกตัวเลข

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รอทดสอบ)

## 2026-08-18 แก้ Live ช้า / พูดแล้ว AI ไม่ได้ยิน (pre-READY audio buffer)

**อาการจาก log 2026-08-18 11:19–11:23:** ผู้ใช้กด Live → ไมค์เปิดทันที (MIC_STARTED) แต่ gemini-3.1-flash-live-preview ใช้เวลา READY 7–15 วินาที → เสียงที่พูดช่วงนั้นหายหมด AI จึงเงียบ ผู้ใช้กด stop ซ้ำหลายรอบ; พอสลับไป gemini-2.5-flash-native-audio-preview-12-2025 READY เร็ว 1–2 วิ อาการหาย

**Root cause:** `LiveGeminiService.sendIfReady()` ทิ้ง audio chunk เงียบๆ เมื่อ `!isSetupComplete` (ไม่มี log ไม่มี buffer) + `connectionState` ไม่เคยถูกเอาไปแสดงใน UI → ผู้ใช้ไม่รู้ว่ายังไม่ READY

**สิ่งที่แก้:**
1. **Pre-READY audio buffer** (LiveGeminiService) — `sendAudioChunk` ช่วงยังไม่ READY เก็บเข้า ring buffer 250 chunk (~5 วิ @16kHz/20ms) ด้วย Mutex แทนการทิ้ง; ตอน `setupComplete` flush ทั้งหมดเข้า session ทันที + log `🎤 Flushing N pre-READY audio chunks (dropped oldest=M)`; ล้าง buffer ใน finally ตอน session จบ กันเสียงเก่าไหลไป session ถัดไป
2. **สถานะในแชท** (JarvisViewModel.startVoiceInput) — ถ้า 2.5 วิแล้วยังไม่ Connected แสดงข้อความ "⏳ กำลังเชื่อมต่อ Live session… เมื่อ AI ทักกลับมาแปลว่าพร้อมแล้ว (เสียงที่พูดระหว่างนี้ถูกเก็บไว้ให้อัตโนมัติ)" เป็นกล่อง static; เพิ่ม `JarvisOrchestrator.liveConnectionState` pass-through จาก `LiveGeminiService.connectionState`

**ข้อจำกัดที่ทราบ:** ความช้า 7–15 วิของ 3.1-flash-live-preview มาจากฝั่ง server แก้จากแอปไม่ได้ — ถ้าต้องการ READY เร็วให้ใช้ 2.5 native audio เป็นค่าเริ่มต้น; buffer จำกัด 5 วินาที พูดยาวกว่านั้นก่อน READY จะตัดหัวทิ้ง (เก็บท้ายสุด); เคส model ตอบเป็น text อังกฤษแทนเสียง (พบใน 2.5 native) มี fallback `onTurnWithoutAudio` อยู่แล้ว

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รวมกับ review fixes 6 จุดก่อนหน้า รอผู้ใช้ทดสอบ)

## 2026-08-18 แก้ Live เสียงผู้ใช้ถึง server ช้า 48 วิ (mic send queue)

**อาการจาก log 11:49–11:50:** session READY ไปแล้ว (turn ก่อนจบ 11:49:31) แต่ transcript ผู้ใช้โผล่ 11:50:19 — ห่าง 48 วิ ทั้งที่ผู้ใช้พูดหลัง 11:49:31 ทันที; buffer fix รอบแรกทำงานถูก ("Flushing 113 pre-READY audio chunks" ที่ 11:47:18) จึงไม่ใช่ปัญหา connect ช้า

**Root cause:** mic callback ทุก chunk (~50/วิ) ทำ `viewModelScope.launch(Dispatchers.IO)` ใหม่ทุกครั้ง → coroutine สะสมเป็นพันเมื่อ send ไม่ทัน (websocket ช้า/IO pool แชร์กับ backtest/TV fetch) → เสียงเข้าคิวใน memory ไหลไป server ช้าลงเรื่อยๆ จนตกค้างสูงสุด 48 วิ

**สิ่งที่แก้ (JarvisViewModel.startVoiceInput/stopVoiceInput):**
1. เปลี่ยนเป็น `Channel<String>(capacity=50)` (~1 วิ) + sender coroutine ตัวเดียววน `for (chunk in channel)` — ส่งตามลำดับ ไม่มี coroutine pile-up
2. คิวเต็ม (`trySend` fail) → `tryReceive()` ทิ้งเสียงเก่าสุดแล้วใส่ตัวล่าสุด — จำกัด latency ไม่เกิน ~1 วิ เสียงที่ server ได้รับเป็นเสียงปัจจุบันเสมอ
3. เพิ่ม heartbeat log `🎤 Mic streaming alive (frame #N, droppedOld=M)` ทุก 250 เฟรม (~5 วิ) — ตรวจจาก log ได้ว่าไมค์ส่งจริงและมี drop ไหม
4. stopVoiceInput ปิด channel ก่อน cancel job

**ยืนยันแล้วจาก log รอบก่อน:** pre-READY buffer ทำงานถูกต้อง (113 chunks flush ครบ dropped=0, READY เร็ว 4.6 วิในรอบนั้น), greeting ทักทายทำงาน, 2 โมเดลอาการเดียวกันเพราะเป็นปัญหา client-side send queue ไม่ใช่โมเดล

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รวมกับ fixes ก่อนหน้า รอผู้ใช้ทดสอบ)

## 2026-08-18 แก้ Live session ตายเงียบจาก GoAway → AI ไม่รายงานผลงานพื้นหลัง (เครื่อง B)

**อาการจาก log เครื่อง B 12:01–12:18:** สั่ง `trading_backtest_evolve all TF` 12:03:38 → รัน 162 AI reflection rounds นาน 11.5 นาที → LongTask เสร็จ 12:15:18 การ์ดลงแชทครบ + log แสดง "⬆ Sent realtime text" แต่ AI เงียบ ไม่พูดรายงานผล

**Root cause:** บรรทัด 12:11:48 `Session closed: VIOLATED_POLICY — client failed to close the connection after receiving a GoAway signal` — Live API จำกัดอายุ session (~10-15 นาที) ส่ง GoAway แล้วปิด; โค้ดเดิมหลัง websocket ปิดปกติจะ `break` ทันที (reconnect เฉพาะตอน exception) → session ตายตั้งแต่ 12:11 แต่ `_isListening` ยัง true → ตอนงานเสร็จ 12:15 `sendRealtimeText` เข้า `sendIfReady` ที่ return เงียบๆ (session=null) แต่ log "⬆ Sent" พิมพ์อยู่นอก sendIfReady จึงดูเหมือนส่งสำเร็จ → ผลหายไปเฉยๆ

**สิ่งที่แก้ (LiveGeminiService + JarvisViewModel):**
1. **Auto-reconnect เมื่อ server ปิด session** — เพิ่ม flag `userRequestedDisconnect` (set ใน disconnect() เท่านั้น) + `sessionWasReady`; หลัง websocket ปิดถ้าไม่ใช่ผู้ใช้กดหยุด → วน reconnect อัตโนมัติ; session ที่เคย READY แล้วถูก server ตัด (timeout ไม่ใช่ config พัง) จะ reset retry counter (attempt=1) ไม่เสีย quota 3 ครั้ง; reconnect สำเร็จจะมี greeting แจ้ง "เชื่อมต่อใหม่แล้ว" ให้ผู้ใช้รู้
2. **sendIfReady return Boolean + skip log** — เดิม return เงียบๆ ตอน session ไม่พร้อม ตอนนี้ log `⚠️ send skipped — session ไม่พร้อม` ทุกครั้ง (audio chunk ไม่ผ่านจุดนี้แล้วเพราะมี buffer/channel คั่น ไม่สแปม)
3. **log "⬆ Sent realtime text" ย้ายเข้าเส้นทางส่งสำเร็จจริง** — sendRealtimeText return Boolean ด้วย
4. **Fallback ประกาศผล LongTask** (JarvisViewModel completions collector) — ถ้า live เปิดอยู่แต่ส่งเข้า session ไม่สำเร็จ → fallback ไป `announceLongTaskCompletion` (notification + เสียง + การ์ดแชท) ผลงานไม่มีทางหายเงียบๆ อีก

**หมายเหตุ:** evolve all TF ใช้เวลา ~11.5 นาที (162 รอบ × ~4 วิ) — เป็นพฤติกรรมปกติของ all×all ไม่ใช่ค้าง; ระหว่างนั้นคุยต่อได้ตามปกติ

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รวม fixes ทั้งวัน รอผู้ใช้ทดสอบ)

## 2026-08-18 Review ความถูกต้อง backtest/optimize/evolve จาก log เครื่อง B (12:01-12:18) — แก้ gate 3 ชั้น

**สิ่งที่ตรวจพบจาก log จริง:**
1. **Backtest pipeline ถูกต้อง** — ผล 3 TF สม่ำเสมอ (15m PF 1.05 / 1h PF 0.96 / 4h PF 1.13), per-strategy ตารางครบ, SMC ไม้น้อยตามจริง
2. **⚠️ พบช่องโหว่ใหญ่: AUTO-APPLY หลวม** — 15m มี 4 กลยุทธ์ที่ **OOS Sharpe ติดลบ** (DC -1.58, E -1.40, 3BR -3.70, UT -0.70) แต่ถูก apply เข้า signal alert จริง เพราะ gate เดิมเช็กแค่ improved + grade≠overfit + PF≥1.0 + ไม้≥10 (grade "ปานกลาง" หลุดผ่าน); บางตัว permutation p≥0.08 (❌ ไม่ต่างจากสุ่ม) ก็ยัง apply
3. **Entry tuning ไม่มี OOS validation เลย** — grid เลือกจากข้อมูลเต็ม (in-sample) แล้ว apply ทันที ถึง SL/TP stage จะมี walk-forward แต่ entry ถูกเซฟไปก่อนแล้ว
4. **Evolve apply gate** มีแค่ full-data expectancy/PF/ไม้ — in-sample ล้วนเช่นกัน
5. Evolve ทำงานครบ 162 รอบ/11.5 นาที ปกติ (apply=off ไม่เซฟตามดีไซน์); reflection แกว่ง ±10% ไม่ converging — ข้อจำกัดที่รู้กัน

**สิ่งที่แก้ (TradingToolExecutor.kt — build ผ่าน):**
1. **SL/TP auto-apply เพิ่ม hard blocks**: OOS Sharpe ≤ 0 → บล็อกเด็ดขาด; permutation p ≥ 0.10 → บล็อก; ข้อความรายงานแสดงเหตุบล็อกชัด ("🚫 บล็อก: OOS Sharpe -1.58 ≤ 0")
2. **Entry tuning เพิ่ม holdout 30% ท้าย**: หลัง grid เลือก entry ใหม่ ให้รันเทียบ baseline บน 30% ท้ายของข้อมูล (ผ่าน startIndex ของ BacktestEngine — indicator warm จาก prefix) — ถ้า tail expectancy แพ้ค่าเดิมหรือ PF<1 (และมีไม้≥5) → บล็อกไม่ apply และไม่ใช้ใน SL/TP stage ต่อด้วย
3. **Evolve apply เพิ่ม holdout 30% ท้าย** เช่นเดียวกัน — finalParams ต้องชนะ initial ทั้งข้อมูลเต็มและช่วงท้าย รายงาน 🔎 แสดงผล holdout เมื่อถูกบล็อก

**⚠️ สิ่งที่ผู้ใช้ต้องรู้:** params ที่ถูก apply ไปแล้วจากรอบ 12:03 (เช่น DC/E/UT/3BR 15m ที่ OOS ติดลบ) ยังค้างใน DB ของเครื่อง B — ควรรัน optimize ใหม่หลังอัปเดต APK นี้ (gate ใหม่จะคัดทิ้ง) หรือรีเซ็ต tuning ของ 15m

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit)


## 2026-08-18 — Full Review 3 ส่วนตาม log (Live Voice / AI-Gemini / Trading Intelligence)

ตรวจโค้ดจริงทุกจุดเทียบกับรายการที่เคยปรับปรุง สรุป:

### ส่วน 1: Live Voice — ผ่านเกือบทั้งหมด, พบบั๊กจริง 2 จุด (แก้แล้ว)
- ✅ pre-READY ring buffer 250 chunks + flush ตอน setupComplete + ล้างใน finally (`LiveGeminiService.kt`)
- ✅ liveMicChannel Channel(50) + sender เดียว + drop oldest + heartbeat (`JarvisViewModel.kt`)
- ✅ Live connection state → UI, ข้อความ "⏳ กำลังเชื่อมต่อ" ถ้าไม่ Connected ใน 2.5 วิ
- ✅ auto-reconnect หลัง server close/GoAway (attempt=1 ถ้าเคย READY) + greeting ตอน reconnect
- ✅ sendIfReady/sendRealtimeText คืน Boolean จริง + fallback announceLongTaskCompletion เมื่อ Live ตายระหว่าง LongTask
- ❌ **บั๊ก 1:** `setLiveInterruptionHandler` ถูกสร้างไว้ใน JarvisOrchestrator แต่ **ไม่มีที่ไหนเรียกใช้เลย** → เวลาผู้ใช้พูดแทรก (VAD interrupt) server สั่งยกเลิก generation แต่คิวเสียง AI เก่าใน AudioTrack ไม่ถูก flush เสียงเก่าเล่นต่อทับ turn ใหม่
- ❌ **บั๊ก 2:** `setLiveNoAudioFallback` ก็ไม่เคยถูก wire เช่นกัน → turn ที่ model ตอบเป็น text ล้วน (ไม่มี audio) AI จะเงียบเฉย ทั้งที่ออกแบบไว้ให้ Android TTS พูดแทน
- 🔧 **แก้ทั้งคู่** ใน `JarvisViewModel.startVoiceInput()`: wire interruption → `pcmAudioEngine.stopPlaying()` (pause+flush+play มีอยู่แล้วใน PcmAudioEngine) และ wire no-audio fallback → `voiceManager.speak()`

### ส่วน 2: AI / Gemini — ผ่านทุกจุด
- ✅ Key Health Registry (`GeminiService` companion): key → deadUntilMs แชร์ข้าม instance, กัน task ใหม่ชน key ที่เพิ่งติด 429
- ✅ cooldown แยกประเภท quota: per-minute พักตาม hint, daily พักยาว
- ✅ parse "Please retry in X.XXs" จาก 429 body + RetryInfo.retryDelay → รอตาม hint (สูงสุด 60s, ไม่เกิน 3 ครั้ง/รอบ) ก่อนหมุน key/โมเดล
- ✅ ทั้ง stream และ non-stream path มี key+model fallback เหมือนกัน (non-stream เดิมไม่มีเลย — เคยทำให้ evolution reflection error ซ้ำโมเดลเดิม)
- ✅ chain fallback ครบตาม list ผู้ใช้ยืนยัน: 2.5-flash → 3.5-flash-lite → 3.1-flash-lite → 3-flash → 2.5-flash-lite → 3.5-flash → **3.6-flash → 3.7-flash** (`ModelConfig.GEMINI_FALLBACK_MODELS`)
- ✅ throttle AI reflection ทุกครั้งหลังเรียก AI ใน BacktestEvolution (free tier 15 RPM/key) + ปิด AI ทั้ง run เมื่อ quota หมด ใช้กฎ heuristic ต่อ

### ส่วน 3: Trading Intelligence — ผ่านทุกจุด
- ✅ SMC `MIN_SL_ATR = 0.75` + `MIN_RRR = 1.2` (กลับตามต้นฉบับ MT5) ใน SmcSignalDetector
- ✅ Per-TF Strategy Gate: `saveStrategyHealth`/`isStrategyGated` (PF<1.0, ไม้≥5 → บล็อก) ใช้จริงใน SignalAlertProvider ก่อนยิง alert ทุกครั้ง
- ✅ interval=all ขยายเป็น 15m/1h/4h จริงทั้ง backtest/optimize/evolve (กัน tuning เซฟใต้ key "all")
- ✅ Entry params tuning ใช้ใน signal จริง: SignalAlertProvider อ่าน getTunedEntryParams ทั้ง detect และ reason
- ✅ Apply gates: full-backtest + holdout 30% ท้าย + hard block OOS Sharpe≤0 / permutation p≥0.10 (ทั้ง entry และ evolve)
- ✅ Reflection Memory + RR floor 1.2 (ปฏิเสธ AI proposal ที่ RR<1.2)
- ✅ gap-through-SL (ได้ราคาแย่กว่า SL เมื่อแท่งเปิดทะลุ), Sharpe annualized แบบ data-driven (bars/ปีจากระยะแท่งจริง), permutation p = (beat+1)/(trials+1), OverfittingScore ให้คะแนนแย่สุดเมื่อ IS Sharpe≤0, AdaptiveOptimizer margin additive

### จุดเสี่ยงที่ยังค้าง (ไม่ใช่บั๊กโค้ด แต่ต้องทำต่อ)
1. **params ตัวแย่ค้างใน DB เครื่อง B** จากรอบ optimize ก่อนมี gate ใหม่ — ควรรัน optimize ใหม่หลังอัปเดต APK นี้
2. **evolve reflection แกว่งไม่ converge** (รู้กันแล้ว) — AI ปรับไปมาโดยไม่มีทิศ ยังเป็นขีดจำกัดของ reflection-based tuning
3. **MixSignalEngine ยังไม่ใช้ tuned entry params** — สัญญาณ mix ยังคำนวณด้วย entry params default

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รอผู้ใช้สั่ง)


## 2026-08-18 — แก้ AI แนบการ์ดกราฟพ่วงติดคำตอบ tool ที่ไม่เกี่ยว

**อาการ:** ผู้ใช้สั่ง tool ที่ไม่เกี่ยวกับกราฟ (เช่น backtest/optimize/alert) แต่ AI แนบการ์ดกราฟใส่แชทให้เองทุกครั้ง เหมือนพ่วงติดกัน

**Root cause:** กฎใน `JarvisPersona.CHAT_RULES` ข้อ 6 เขียนกว้างเกิน — "ทุกครั้งที่ตอบเกี่ยวกับกราฟหรือผลวิเคราะห์ของ symbol ใดๆ ... ให้แนบการ์ดกราฟท้ายคำตอบเสมอ" ทำให้ทุกคำตอบที่กล่าวถึง symbol (รวมผล backtest/optimize/stats) ถูกแนบ ```chart fence ตามมาด้วย

**ยืนยันแล้วว่า:** ไม่มี tool executor ไหนแทรก chart fence อัตโนมัติ — การ์ดกราฟมาจาก 2 ทางเท่านั้น: (1) โมเดลแนบเองตามกฎ prompt (ตัวการ), (2) Live mode auto-card เมื่อสั่ง chart_dashboard_control action=open (พฤติกรรมที่ตั้งใจไว้ ไม่แตะ)

**Fix:** แก้กฎข้อ 6 ให้แคบลง — แนบการ์ดเฉพาะเมื่อผู้ใช้ขอดู/เปิดกราฟ หรือคำตอบเป็นการวิเคราะห์ราคา/เทคนิคอลเป็นประเด็นหลัก + 🚫 ห้ามแนบเมื่อตอบผล trading_backtest / optimize / evolve / signal_stats / mix_config / automation_manage_alerts (มีรูปแบบแสดงผลของตัวเอง) + จำกัดสูงสุด 1 การ์ดต่อคำตอบ

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit)


## 2026-08-19 — แก้ 3 จุดบกพร่อง Adaptive Trading Intelligence (จากการวิเคราะห์ของผู้ใช้ ยืนยันตรงกับโค้ดทั้ง 3 ข้อ)

### ข้อ 1: Objective Function ใหม่ของ Optimizer (ParamOptimizer.score)
**เดิม:** `Sharpe × PF(cap 10) × WinRate` — ไม่มี expectancy/DD, คูณปัจจัย correlate ซ้ำซ้อน, PF 15 กับ 50 เท่ากัน
**ใหม่ (expectancy-first):**
- Hard constraints (ตกทันที): trades < minTrades | PF < 1.0 | expectancyR ≤ 0 | DD > 35%
- `score = expectancyR × pfWeight(cap 3) × sharpeWeight(±) × ddPenalty(1/(1+DD/20)) × tradeConfidence(เต็มที่เมื่อ ≥20 ไม้)`
- ผู้ใช้งานอัตโนมัติ: gridSearch + AdaptiveOptimizer (ใช้ score เดียวกัน)

### ข้อ 2: Evolution causal delta + hill-climbing (BacktestEvolution)
**เดิม:** ทุกรอบเทียบ params กับ `initial` → `deltaVsBaseline` ไม่ใช่ causal delta ของ mutation ล่าสุด → Reflection Memory เรียนรู้ผิดทิศ (B ดีกว่า A แต่แย่กว่า default ถูกบันทึกว่า "แย่") — น่าจะเป็น root cause ของ "evolve ยิ่งทำยิ่งแย่"
**ใหม่:**
- ทุกรอบรัน `prevParams` บน segment เดียวกันเพิ่ม → ได้ `deltaVsPrev` ที่ causal จริง (ReflectionTrial มี field ใหม่)
- **Hill-climbing:** proposal ใหม่ต้องรันเทียบ params ปัจจุบันบน segment เดียวกัน ถ้าแพ้ → 🚫 ปฏิเสธ คง params เดิม (mutation แย่ไม่สะสมข้ามรอบอีก) + noChangeStreak ทำงานร่วมกระตุ้นปรับทิศใหม่
- persist ลง OptimizationTrial ใช้ causal delta เป็นหลัก (fallback delta เทียบ initial ในรอบแรก) — memory lines ที่ AI อ่านจึงถูกทิศจริง
- ต้นทุนเพิ่ม: ~2 backtest/รอบ (segment ขนาดเล็ก รับได้)

### ข้อ 3: Per-TF Gate ยกระดับเป็น 4 ระดับ (AutomationManager)
**เดิม:** boolean PF-only (`trades≥5 && PF<1.0 → block`) — PF 1.02 / DD 18% ผ่าน gate
**ใหม่:** `StrategyGateLevel` จาก StrategyHealth (PF, avgR, winRate, trades):
- `UNKNOWN` = ข้อมูลไม่พอ → ผ่าน (หลักการเดิม: ไม่มีข้อมูลไม่ block)
- `BLOCK` = PF < 1.0 หรือ avgR ≤ 0 → ห้ามยิง alert
- `WEAK` = PF < 1.25 หรือ avgR < 0.1R หรือ WR < 35% → ยิงได้แต่ติดป้าย `signal_weak` ใน metadata + log ⚠️
- `STRONG` = PF ≥ 1.8, avgR ≥ 0.25R, WR ≥ 40%, ไม้ ≥ 20
- `NORMAL` = ระหว่างกลาง
- `isStrategyGated()` คง signature เดิม (wrapper = BLOCK เท่านั้น) — caller เก่าไม่พัง

**Build:** `:composeApp:assembleDebug` BUILD SUCCESSFUL (ยังไม่ commit — รอผู้ใช้สั่ง)
**หมายเหตุ:** ค่าเกณฑ์ WEAK/STRONG เป็น heuristic เริ่มต้น — ควรทบทวนหลังมีข้อมูล backtest สะสมพอ

## 2026-08-19 Refactor JarvisViewModel → Controllers (Phase 1-5)
**สาเหตุ:** God ViewModel (2,950 บรรทัด) coupling สูง — test ยาก, regression ง่าย, feature ใหม่กระทบ feature เก่า
**สิ่งที่ทำ:** แยก JarvisViewModel ออกเป็น 6 controllers ใน `controller/` โดย VM คง public API/StateFlow ชื่อเดิมทั้งหมดเป็น forwarders — UI (App.kt, screens) ไม่ต้องแก้เลย
- **Phase 1 — Mt5Controller (1,220 บรรทัด):** MT5 Terminal + AI Tracking ทั้งหมด (state, connect/pair, realtime WS, snapshot cache, order actions, ema/rsi) + `Mt5ClientRuntimeInfo` ย้ายพร้อม typealias คง import เดิมของ UI
- **Phase 2 — ChartController (390 บรรทัด):** chart state, dashboard controls, chartCardCache, applyChartControl, ChartStateManager sync collectors, chart settings load
- **Phase 3 — VoiceController (214 บรรทัด):** Live voice session (mic Channel bounded, audio out, mute, error) + volume bridge; coupling กับแชท/หน่วยความจำผ่าน lambda (coreContextProvider, messages flow, onUserSpeakingChanged)
- **Phase 4 — ChatController (191 บรรทัด):** _messages/_isTyping, sendMessage stream pipeline, loadHistory/clearChat, memory post-processing (storeMessage/KG/coreMemory/sleep trigger)
- **Phase 5 — SettingsController (514) + AlertController (176):** API keys/โมเดล/เสียง/identity/provider keys + alert settings/createAlert/createScheduledTask/companion normalizeAlertVoiceEngine (ไม่มี caller ภายนอก)
**ผล:** JarvisViewModel 2,950 → 701 บรรทัด (ลด 76%) — เหลือเฉพาะ wiring, forwarders, camera, LongTask/AlertChatBus collectors, sleep cycle
**Verify:** `:composeApp:assembleDebug` ผ่านทุก phase (5 รอบ) — build สุดท้าย BUILD SUCCESSFUL; grep ไม่พบ private state เก่าหลงเหลือใน VM
**หมายเหตุ:** backup `.backup/` ลบแล้วตามที่สั่ง; จุดเสี่ยงที่ต้องทดสอบบนเครื่องจริง — voice greeting/voice-change restart, chart fence ตอน live, LongTask announce (ทุกจุดผ่าน forwarder/lambda ตรงตามเดิม)

## 2026-08-20 Dataset Fingerprint — ปิดประเด็น Backtest Reproducibility (ตาม Evolution Audit)
**บริบท:** จากประวัติแชท 19/08 — ผล backtest/evolve สวิง (M15/H4 ดีสุด → H1 ดีสุด ทั้งที่ symbol/แท่งเดียวกัน) เพราะไม่มีทางพิสูจน์ว่าสองรันใช้ dataset ชุดเดียวกัน (cache 2 ชม. หมด → refetch หน้าต่างใหม่)
**สิ่งที่ทำ (TradingToolExecutor.kt):**
- เพิ่ม `datasetTag(candles)` — FNV-1a 64-bit hash จากจำนวนแท่ง + timestamp + close (sample 64 จุดกระจายทั้งชุด) + ช่วงเวลา UTC รูปแบบ `DS#xxxxxxxx · N แท่ง · ต้น → ท้าย UTC`
- แสดง tag ในผล **backtest** (chat + logcat), **optimize** (header), **evolve** (header) — รันไหน tag ตรงกัน = dataset เดียวกันเป๊ะ เทียบผลได้ตรง; tag ต่าง = ข้อมูลขยับ ห้ามเทียบตรง
**สถานะที่ตรวจแล้วว่าดีอยู่แล้ว (ไม่ต้องแก้):**
- Engine deterministic (ไม่มี Random/Clock ใน BacktestEngine)
- backtest/optimize/evolve ใช้ snapshot เดียวผ่าน `fetchBacktestCandles` cache 2 ชม.
- Apply gate ของ evolve: full-run + holdout 30% + expectancy/PF/DD/ไม้≥10 ครบ
- Champion selection: re-rank ทุก visited params บน training set เต็ม (ไม่ใช้ผล mutation สุดท้าย)
- `compileDebugKotlinAndroid` ผ่าน (JBR ของ Android Studio)
**ขั้นต่อไปที่ยังค้าง:** ทดสอบบนเครื่องจริง — สั่ง backtest → evolve → backtest ซ้ำ แล้วเทียบ DS# tag ต้องตรงกัน (ภายใน 2 ชม.); ถ้าจะให้ข้ามวันได้ต้อง persist snapshot ลง disk (ยังไม่ทำ)

## 2026-08-20 (รอบ 2) ทดสอบจริง + แยกเหตุผล Apply Gate ของ Evolution
**ผลทดสอบจาก logcat จริง (00:31-00:32):**
- ✅ DS# tag ตรงกันครบทั้ง 3 TF ระหว่าง backtest → evolve (15m=4a1d5b5b, 1h=7af7b6dc, 4h=3b4b05c4) → reproducibility ใน session พิสูจน์แล้ว
- ✅ Hill-climbing ACCEPT/REJECT + FINAL CHAMPION re-rank ทำงานถูก (MOM champion 0.74/5.0 จาก visited=7)
- ✅ Apply gate บล็อกถูกต้องทุกเคส รวมถึงเคส champion แพ้บน full set (PF 2.05→1.78) — หลักฐานว่า gate กันค่า overfit ได้จริง
**บั๊ก UX ที่พบ:** ข้อความ "⛔ ไม่ apply (ใหม่ไม่ชนะบนข้อมูลเต็ม)" กว้างเกิน — เคส PF 1.47→1.50, avgR +0.43→+0.49 (ดีขึ้นทั้งคู่) ถูกบล็อกเพราะ MaxDD แย่ลงเกิน +1% แต่ผู้ใช้อ่านไม่ออก
**แก้:** แยกเงื่อนไข gate เป็น condExpectancy/condPf/condTrades/condDd → รายงานข้อที่ fail ตรงๆ พร้อมตัวเลข DD ทั้งสองฝั่ง; ข้อความ holdout block เพิ่ม PF/DD ประกอบ (เดิมโชว์แค่ avgR ทำให้เคส avgR ดีกว่าแต่โดนบล็อกดูขัดแย้ง)
**Build:** compileDebugKotlinAndroid ผ่าน

## 2026-08-20 (รอบ 3) ขยาย Evolution Candidate Fan + Segment Cache
**วินิจฉัยจาก logcat รอบ 2 (00:42):** reproducibility สมบูรณ์ (DS# ตรงรอบแรกทุก TF, champion/ fitness ซ้ำเป๊ะ) แต่ผลไม่คุ้ม apply — สาเหตุเชิงระบบ: candidate fan แคบ (5 ตัว ±5% ทิศเดียว) + clampDrift ±30% จาก initial → hill-climb ติด local optimum รอบค่า default ที่ดีอยู่แล้ว; gate บล็อกของเสือกถูกต้องทุกเคส (2/24 ผ่านเกณฑ์จริงในโหมด dry-run)
**สิ่งที่ทำ (BacktestEvolution.kt):**
- Candidate fan 5 → 11 ตัว/รอบ: เพิ่ม ±10% (ขอบ clampStep) + joint moves (SL↓TP↑ / SL↑TP↓) — ขอบเขตความเสี่ยงเดิม (clampStep ±10%/รอบ, clampDrift ±30%, rolling validation gate, full+holdout apply gate) ไม่เปลี่ยน
- Segment result cache (`segCache: HashMap<Pair<TpSlParams,Int>, BacktestResult?>`) — params ซ้ำข้ามรอบไม่ต้องรัน engine ใหม่ รองรับ fan ที่กว้างขึ้นโดย runtime ไม่พุ่ง (deterministic ต่อ (params, segment) จึง cache ปลอดภัย)
**สรุปเชิงความจริง:** ค่า default ของ XAUUSD ใกล้ local optimum อยู่แล้ว — evolution จะให้ gain จำกัดโดยธรรมชาติ; ถ้าต้องการกระโดดไกลกว่านี้ให้ใช้ trading_backtest_optimize (grid 25 combos + walk-forward + permutation + Monte Carlo) ซึ่งเป็นเครื่องมือ global search ของระบบ
**Build:** compileDebugKotlinAndroid ผ่าน (ยังไม่ commit)

## 2026-08-20 (รอบ 4) ทดสอบครบ chain backtest → optimize → evolve
**ผลจาก logcat จริง (00:54-00:56, PID เปลี่ยน = รีสตาร์ทแอป):**
- ✅ DS# ทำหน้าที่เป๊ะ: 1h/4h tag คงเดิม (แท่งปิดล่าสุดยังไม่ขยับ → ข้อมูลเหมือนเดิมหลัง refetch) ส่วน 15m เปลี่ยนเป็น DS#92bb9e88 เพราะแท่ง 15m ปิดใหม่ 1 แท่ง (17:30→17:45) — fingerprint จับการขยับของหน้าต่างข้อมูลได้ถูกต้องแม้ cache หายจากการรีสตาร์ท
- ✅ optimize scope=entry ได้ AUTO-APPLY จริงครั้งแรก: TR entry `trSlow 200→100` avgR −0.16 → +0.03 (PF 1.05, 36 ไม้) บน XAUUSD 15m
- ✅ evolve (fan ใหม่) เร็วขึ้นมาก (~6 วิ/TF vs ~11 วิ ก่อนมี segment cache) และมี 3 กลยุทธ์ผ่าน full+holdout ครบ (แสดง 🚫 dry-run เพราะ apply=off)
**บั๊ก UX ที่พบ+แก้:** holdout ที่มีไม้ <10 ทั้งสองฝั่งพิมพ์ "avgR +0.00 PF 0.00" ดูเหมือนบั๊ก → เปลี่ยนเป็นรายงานตรงๆ ว่า "holdout มีไม้ไม่พอประเมิน (เดิม X ไม้ / ใหม่ Y ไม้) → ไม่เสี่ยงเซฟ"
**Build:** compileDebugKotlinAndroid ผ่าน (ยังไม่ commit)
**ค้างต่อไป:** persist backtest snapshot ลง disk (กัน cache หายตอนรีสตาร์ทแอปสำหรับ TF เล็ก) / พิจารณา multi-start evolve

## 2026-08-20 (รอบ 5) เข้ารหัสดุลยพินิจ "คุ้ม" ลง Apply Gate (MIN_EVOLVE_GAIN_R)
**บริบท:** logcat 01:10 — 4 กลยุทธ์ผ่าน gate แต่ gain จิ๋ว (+0.01~+0.09R บน ~100 ไม้) ผู้ใช้ตัดสินเองว่า "ไม่คุ้มบันทึก" ถูกต้องตามสถิติ: gain ระดับนั้นคือ noise ของ sample size
**สิ่งที่ทำ (TradingToolExecutor.kt):**
- เพิ่ม `MIN_EVOLVE_GAIN_R = 0.05` (companion) — condExpectancy เปลี่ยนจาก `>` เป็น `>= เดิม + 0.05R` → ผ่าน gate = คุ้มจริงโดยนิยาม ไม่ต้องมาตัดสินเองทุกรอบ
- ข้อความบล็อกเปลี่ยนเป็น "expectancy ดีขึ้นไม่ถึงเกณฑ์คุ้ม (+0.04R < +0.05R)" — โปร่งใสว่าตัดสินด้วยเกณฑ์ไหน
- ผลข้างเคียง: เซฟ compute เพราะ holdout validation จะรันเฉพาะ candidate ที่ผ่านเกณฑ์คุ้มก่อนแล้ว
**หมายเหตุเชิงกลยุทธ์:** หลักฐาน 4 รอบชี้ว่า default params ของ XAUUSD ใกล้ local optimum สำหรับ 8 กลยุทธ์ classic — evolve ให้ gain จำกัดโดยธรรมชาติ; ตัวที่ทำเงินได้จริงคือ entry tuning (optimize scope=entry → TR 15m −0.16R→+0.03R auto-applied)
**Build:** compileDebugKotlinAndroid ผ่าน (ยังไม่ commit)
