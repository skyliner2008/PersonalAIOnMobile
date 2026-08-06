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
