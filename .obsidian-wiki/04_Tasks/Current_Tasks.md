# Current Tasks

สถานะงานของโปรเจค `JARVIS / PersonalAIBot` ณ วันที่ **2026-09-06**

---

## 🟢 Active — กำลังดำเนินการ / สิ่งที่ต้องทำต่อ (Prioritized Next Steps)

### 1. mt5-core-server & Research Integration (High Priority)
- [ ] **Wire `POST /api/mt5/auto/external-signal`**: เพิ่ม endpoint ใน `src/routes/autoTrading.ts` ตามแผน `[[63_Unified_SMC_Watch_Mode_Plan]]` เพื่อรับ Paper/Watch signals จาก Python lab
- [ ] **Implement Shadow P/L Resolver**: คำนวณผลจำลอง (Fill / Win / Loss / Expired) สำหรับ external signals โดยไม่แตะ live order paths
- [ ] **Bridge Health Check / Watchdog**: ตรวจสอบสถานะและ ping Python bridge (:5001) พร้อม auto-reconnect ป้องกัน silent failure
- [ ] **Unified SMC Forward Testing**: รัน `tools/unified_smc_watch.py` สะสม shadow sample size ให้ครบ $\ge 30$ ไม้เพื่อปลดล็อก Gate สำหรับ Promotion

### 2. Mobile App (PersonalAIBot)
- [ ] **Rich Chat Rendering**: ยกระดับ Chat Bubble ให้รองรับการเรนเดอร์ Markdown Tables, Infographics, และ Summary Cards สวยงามในห้องแชทโดยตรง (ต่อยอดจาก XLSX/CSV export)
- [ ] **System Background Notifications**: ส่ง Push/Local Notification แจ้งเตือน Trade Executed / Alert Triggered เมื่อปิดหน้าจอหรือแอปอยู่เบื้องหลัง
- [ ] **Offline Status & Reconnect Banner**: ปรับปรุง UI แถบสถานะการเชื่อมต่อ MT5 Server / Network ให้ชัดเจนเมื่อสัญญาณหลุด

---

## ✅ Recently Completed (สิงหาคม – กันยายน 2026)

### 2026-09-09: Gemini 3.1 Flash Live Primary Model & Automatic Model Switch Fix
- [x] **Set `gemini-3.1-flash-live-preview` as Default Primary Live Model (`ModelConfig.kt`)**: กำหนด `DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"` พร้อมจัดอันดับให้อยู่ลำดับ 1 ใน `SEED_LIVE_MODELS` และ `liveCandidates` เพื่อความเร็วการเชื่อมต่อ (~835ms), ความเป็นธรรมชาติของเสียงพูดภาษาไทย, และความแม่นยำในการเรียก Native Tools
- [x] **Remove from `deprecatedLiveModels` & Add Old Model Auto-Migration (`SettingsController.kt`)**: ลบ `"gemini-3.1-flash-live-preview"` ออกจากลิสต์โมเดลที่ถูกมองว่าตกยุค และใส่ `"gemini-2.5-flash-native-audio-preview-09-2025"` แทน เพื่อ auto-migrate อุปกรณ์ที่เคยถูกบังคับเซฟโมเดลเก่าไว้ใน SQLite ให้กลับมาใช้ 3.1 ทันทีเมื่อเปิดแอป
- [x] **Prevent Silent DB Overwrite on Runtime Fallback (`JarvisViewModel.kt`)**: ตัดการเรียก `settings.updateLiveModelSilently(winningModel)` ออกจากการทำงานของ `orchestrator.onLiveModelChanged` ป้องกันไม่ให้การ fallback ชั่วคราวไปเขียนทับค่าที่ผู้ใช้ตั้งไว้ในฐานข้อมูลอย่างถาวร
- [x] **Stateful Session Reset & Network Tolerance (`LiveGeminiService.kt`)**: รีเซ็ต `liveModelName = configuredLiveModelName` เสมอก่อนเริ่ม Session ใหม่ เพื่อให้การกดไมค์ทุกครั้งเริ่มต้นด้วยโมเดลที่ผู้ใช้เลือกเสมอ พร้อมขยาย Watchdog Timeout จาก 3500ms เป็น 6000ms ป้องกันการตัด timeout ก่อนเวลาอันควรบนเน็ตมือถือ และลด Penalty จาก 15 นาที เหลือ 60 วินาที
- [x] **Unit Tests Passed (`DynamicModelTest.kt`)**: เพิ่ม `@BeforeTest`/`@AfterTest` และยืนยัน Default Live Model เป็น 3.1
- [x] **Wiki Architecture Document**: บันทึกสถาปัตยกรรมฉบับสมบูรณ์ที่ `[[Changelog_2026-09-09_Gemini_3_1_Live_Model_Primary]]`

### 2026-09-09: Keyboard IME AdjustResize & Edge-to-Edge Inset Fix (Honor / Multi-OEM)
- [x] **Enforce `adjustResize` (`AndroidManifest.xml`)**: กำหนด `android:windowSoftInputMode="adjustResize"` และ `keyboard` ใน `configChanges` ให้กับ `MainActivity` ป้องกันไม่ให้ WindowManager ของระบบในบางแบรนด์ (เช่น Huawei / Honor / MagicOS) สลับไปใช้ `adjustPan` ซึ่งจะเลื่อนหน้าต่างทั้งบานขึ้นไปจนชนขอบบนจอเมื่อเปิดคีย์บอร์ด
- [x] **Consolidate Additive Insets (`ChatInputBar.kt`)**: แทนที่การใช้ `.navigationBarsPadding().imePadding()` ที่บวก Inset ซ้อนกัน จนเกิดช่องว่างสีมืดขนาดใหญ่ (Void Gap) ด้วย `windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))` ซึ่งคำนวณ `max(navigationBars, ime)` อัตโนมัติ ทำให้กล่องข้อความลอยแนบติดชิดอยู่เหนือคีย์บอร์ดพอดีทุกโหมดการนำทาง (Gesture / 3-button nav)
- [x] **Wiki Architecture Document**: บันทึกสถาปัตยกรรมฉบับสมบูรณ์ที่ `[[Changelog_2026-09-09_Keyboard_IME_AdjustResize_Fix]]`

### 2026-09-08: Keyguard Lockscreen Overlay & Control Mode Wake Fix
- [x] **Remove Static Lockscreen Overlays (`AndroidManifest.xml`)**: นำ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` ออกจากแท็ก `MainActivity` ใน Manifest ป้องกันไม่ให้แอปแสดงทับหน้า Keyguard (หน้าใส่ PIN/สแกนนิ้ว) ตลอดเวลาในโหมดปกติ
- [x] **Control Mode Wake Over Lockscreen (`AlwaysLiveManager.kt`, `MainActivity.kt`, `DeviceControlExecutor.kt`)**: แก้ปัญหาในโหมดควบคุมเมื่อสั่ง "เปิดหน้าจอ" หรือกด Power ปลุกเครื่องแล้วติดหน้าล็อก PIN โดย:
  - นำ `requestDismissKeyguard()` ออกจาก `turnScreenOnTemporarily()` เพื่อไม่ให้ระบบเด้งหน้าต่างกรอก PIN บังหน้าแอป
  - ส่ง Intent `HOTWORD_WAKE` พร้อม Flags `NEW_TASK | SINGLE_TOP | REORDER_TO_FRONT` เพื่อยก `MainActivity` ขึ้นมาแสดงผลทับเหนือหน้าจอล็อก (Show When Locked) โดยตรงแบบ Hands-free
  - Re-assert `turnScreenOnTemporarily()` และ `setKeepScreenOn(true)` ใน `onResume()` และ `onNewIntent()` หากอยู่ในโหมดควบคุม
  - เพิ่มการรองรับปุ่ม `wake` ใน `device_press_button` สำหรับคำสั่งเสียง "เปิดหน้าจอ", "เปิดจอ", "ปลุกหน้าจอ", "ตื่น"
- [x] **Strict Window Flags Lifecycle Management (`MainActivity.kt`)**: เพิ่มการเรียก `clearScreenFlags()` ใน `onCreate()`, `onResume()`, และ `onStop()` เมื่ออยู่ในโหมดปรกติ (`state == OFF`) เพื่อล้าง `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, และ `FLAG_KEEP_SCREEN_ON` ออกจาก Window อย่างเด็ดขาด
- [x] **Remove KeepScreenOn from Overlay Widget (`FloatingWidgetService.kt`)**: ลบ `FLAG_KEEP_SCREEN_ON` ออกจาก LayoutParams ของ Floating Widget เพื่อให้หน้าจอเข้าสู่โหมดพัก (Sleep) ได้ตามปกติ
- [x] **Wiki Architecture Document**: บันทึกสถาปัตยกรรมฉบับสมบูรณ์ที่ `[[Changelog_2026-09-08_Keyguard_Lockscreen_Overlay_Fix]]`

### 2026-09-08: Live Voice Connection Latency Optimization & Dynamic Self-Healing Fix
- [x] **Sub-2s Instant Live Voice Connection (`ModelConfig.kt`, `SettingsController.kt`)**: แก้ปัญหาการเชื่อมต่อ Live Voice ที่บางครั้งรอนานถึง 37 วินาที โดยตั้ง `gemini-2.5-flash-native-audio-preview-09-2025` ซึ่งต่อติดเร็วสุด (~1.5s) เป็นโมเดลหลักอันดับ 1 แทน preview models เดิมที่ค้าง พร้อม auto-migrate การตั้งค่าเก่าใน SQLite อัตโนมัติ
- [x] **Immediate WebSocket Close Abort on Timeout (`LiveGeminiService.kt`)**: ลด Setup Watchdog เหลือ 3500ms และสั่งยกเลิก coroutine scope (`cancel`) ทันทีที่หมดเวลา ตัดระยะเวลารอ 2-way close handshake ของ Ktor (7.2 วินาที) เหลือ <1ms ทำให้สลับ fallback ไปโมเดลถัดไปได้ทันที
- [x] **Self-Healing Model Promotion & Temporary Penalty (`ModelConfig.kt`, `JarvisOrchestrator.kt`, `JarvisViewModel.kt`)**: เมื่อโมเดลใดเชื่อมต่อสำเร็จ (`setupComplete`) จะถูก Promote ขึ้นเป็นอันดับ 1 ของ fallback chain และบันทึกลงฐานข้อมูลทันที และหากโมเดลใด timeout จะติด penalty ชั่วคราว 15 นาที ถูกดันไปท้ายคิว ไม่ให้เสียเวลารอซ้ำ
- [x] **Unit Tests Passed 100% (`DynamicModelTest.kt`)**: ชุดทดสอบครอบคลุม Default Live Model, Model Promotion, และ Penalize Demotion

### 2026-09-08: Screen Wakeup Lifecycle & Normal Mode Auto-Sleep Fix
- [x] **Enforce Auto-Sleep in Normal Mode (`App.kt`, `MainActivity.kt`, `AlwaysLiveManager.kt`)**: แก้ปัญหาหน้าจอติดสว่างค้างตลอดเวลาหลังจากปิดโหมดควบคุม (Always Live) โดยเพิ่ม `clearScreenFlags()` ล้าง `FLAG_KEEP_SCREEN_ON` และ flags ที่เกี่ยวข้องออกจาก Window, ปรับ `AlwaysLiveManager.wakeScreen()` ไม่ให้ถือ bright wake lock ค้าง, และปรับ `App.kt` ให้ `onKeepScreenOn` ทำงานเฉพาะเมื่ออยู่ในโหมด Always Live เท่านั้น

### 2026-09-08: Anticipation Alert Card UI Refinement (Confidence & Price Clean Layout)
- [x] **AnticipationAlertCard3D Header & Second Row Refinement (`MessageBubble.kt`)**: นำตัวเลข % ออกจากแถว Header ด้านบน ป้องกันไม่ให้ตัวเลข % และเครื่องหมาย % เบียดตกขอบหน้าจอเป็นแนวตั้ง (เช่น 7 \n 6 \n %) พร้อมย้ายมาแสดงในบรรทัดที่ 2 คู่กับราคาปัจจุบัน (`ความเชื่อมั่น 76%` และ `ราคา 4405.06`)
- [x] **Redundancy Elimination between Line 2 and Factors**: ตัดการแสดงผล `meta.zone` (เช่น EMA Convergence) ออกจากบรรทัดที่ 2 เพื่อไม่ให้ซ้ำซ้อนกับรายการ `ปัจจัยที่เกิด` ด้านล่าง และปรับปรุง `buildAnticipationChatCard` ใน fallback markdown ให้สอดคล้องกัน

### 2026-09-08: Voice Alert Delivery & Background Screen Wakeup Architecture
- [x] **Alert Voice Default Enabled & Persistence (`AlertController.kt`, `JarvisAutomationService.kt`)**: แก้ปัญหาการแจ้งเตือนเข้าเฉพาะแชทแต่ไม่มีเสียงพูด (`voice=false/device`) โดยปรับค่าเริ่มต้นของ `_alertVoiceEnabled` เป็น `true` ทั้งใน StateFlow และ Fallback ของฐานข้อมูล SQLite `AppSetting` เพื่อให้ผู้ช่วยส่วนตัวพูดเตือนทันทีโดยไม่ต้องไปกดเปิดสวิตช์เอง
- [x] **Automatic Voice Activation on Alert Creation (`JarvisOrchestrator.kt`, `ToolExecutor.kt`, `AutomationManager.kt`)**: เมื่อผู้ใช้สั่งสร้าง Alert ผ่านคำสั่งเสียง Live Voice หรือผ่านเครื่องมือ `trading_signal_anticipation` ระบบจะส่งพารามิเตอร์ `voice=true` และเปิดใช้งานเสียงพูดในระบบ Automation ให้โดยอัตโนมัติ พร้อมแสดงข้อความยืนยันสถานะเปิดเสียง
- [x] **Background Speech & Screen Wakeup (`JarvisAutomationService.kt`, `AlwaysLiveManager.kt`)**: ปลุกหน้าจอมือถืออัตโนมัติ (`wakeScreen()`) เมื่อมี Alert เข้า เพื่อให้ผู้ใช้มองเห็นการ์ดแจ้งเตือนและได้ยินเสียงพูดทันที แม้หน้าจอดับหรือเครื่องล็อกอยู่ พร้อมขอ `PARTIAL_WAKE_LOCK` ชั่วคราว (30 วินาที) ป้องกัน CPU เข้าสู่โหมดหลับ (Doze) ระหว่างเล่นเสียง
- [x] **Android TTS Priority AudioAttributes & Ready Watchdog**: กำหนด `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` และ `CONTENT_TYPE_SPEECH` ให้กับ Android TTS เพื่อให้เสียงพูดดังชัดเจนในทุกสถานะ พร้อมระบบรอ `ttsReady` สูงสุด 1,500ms ป้องกันการ Drop ข้อความเสียงทิ้ง
- [x] **Dynamic Live Voice Fallback Chain**: ปรับปรุง `liveVoiceChain()` ให้เชื่อมต่อกับ `ModelConfig.getLiveFallbackChain()` แบบ Dynamic อัตโนมัติ แทนรายการโมเดลที่เคย Hardcode ไว้

### 2026-09-07: Dynamic Gemini Model Registry & Self-Healing Model Discovery
- [x] **Dynamic Google Gemini Model Synchronization (`ModelConfig.kt`)**: ยกเลิกระบบ Hardcoded Model Names โดยเปลี่ยนเป็น Dynamic Registry ที่ดึงและกรองรายชื่อโมเดลจริงจาก Google API (`/v1beta/models?key=$apiKey`) สดๆ แบบ Real-time
- [x] **Smart Capability Filtering & Flash Priority Ranking**: กรองเฉพาะโมเดลที่รองรับ `generateContent` ตัดโมเดล Embedding / Imagen / Robotics ออกอัตโนมัติ และจัดลำดับโมเดลตระกูล Flash / Flash-Lite ที่ Latency ต่ำสุดและ Quota ดีที่สุดขึ้นเป็น Priority แรก เรียงตาม Semantic Version ใหม่ล่าสุด
- [x] **Instant 404 Blacklisting & Auto-Healing (`GeminiService.kt`, `SettingsController.kt`)**: เมื่อโมเดลใดส่งกลับ HTTP 404 NOT_FOUND ระบบจะขึ้นบัญชีดำ (`markModelDead`) ทันที ตัดออกจาก Fallback Chain, เรียก Google API อัปเดตรายชื่อใหม่ (`refreshModels()`), และทำการ Auto-Migrate การตั้งค่าใน SQLite Database ให้ข้ามไปใช้ Flash Model ล่าสุดที่ใช้งานได้จริง
- [x] **Voice & Tool Latency Optimization (`MarketTechnicalToolHandler.kt`)**: ลด Timeout การวิเคราะห์ปฏิทินเศรษฐกิจ `trading_macro_calendar` เหลือ 12 วินาที และปิด Retry ซ้ำ 45 วินาที เพื่อไม่ให้ขัดจังหวะการสนทนาด้วยเสียง
- [x] **Unit Test Passed 100% (`DynamicModelTest.kt`)**: ชุดทดสอบครอบคลุม Dynamic Filtering, Flash Priority, Dead Model Blacklist, และ Fallback Chain
- [x] **Wiki Architecture Document**: บันทึกสถาปัตยกรรมฉบับสมบูรณ์ที่ `[[Changelog_2026-09-07_Dynamic_Gemini_Model_Registry]]`
- [x] **Full Mobile Device Control Architecture (`JarvisAccessibilityService.kt`, `DeviceControlExecutor.kt`, `DeviceControlHandler.kt`, `DeviceToolDefinitions.kt`)**: พัฒนาระบบควบคุมมือถือทั้งเครื่องผ่านคำสั่งเสียง Real-time (Gemini Live) และ Text Chat โดยมี 18 เครื่องมือแบ่งเป็น 4 หมวด:
  - **Hardware Controls**: ไฟฉาย (`device_flashlight`), ปรับระดับเสียง (`device_volume`), ปรับความสว่างจอ (`device_brightness`), ควบคุมเพลง (`device_media_control`), เปิด/ปิดโหมด Always ควบคุม (`device_always_live`)
  - **App Launcher**: เปิดแอปตามชื่อ (`device_open_app`), นำทาง/ดูพิกัด Google Maps (`device_navigate`), ร่างอีเมล (`device_send_email`), เพิ่มนัดปฏิทิน (`device_add_calendar`), โทรศัพท์ (`device_make_call`), SMS (`device_send_sms`), ตั้งปลุก (`device_set_alarm`), เปิดเว็บ/ค้นหา (`device_open_url`, `device_search_web`)
  - **Screen Interaction & Accessibility**: อ่านจอ (`device_read_screen`), แตะปุ่ม (`device_tap`), พิมพ์ข้อความ (`device_type_text`), เลื่อนจอ (`device_scroll`), ปุ่มระบบ Back/Home/Recents/Screenshot/Lock (`device_press_button`), ข้อมูลแอปปัจจุบัน (`device_get_app_info`)
  - **System Info**: ตรวจสอบแบตเตอรี่ (`device_battery_status`), ตรวจสอบ WiFi (`device_wifi_status`)
- [x] **Google Maps Smart Location View vs Navigation**: แยกโหมด `action="view"` (เปิดดูพิกัด/สถานที่ทั่วโลกด้วย `geo:0,0?q=...` ป้องกัน error "ไม่พบเส้นทาง" สำหรับสถานที่ต่างประเทศ เช่น ญี่ปุ่น นิวยอร์ก) ออกจาก `action="navigate"` (โหมดนำทางเลี้ยวต่อเลี้ยวด้วย `google.navigation:q=...`) พร้อม Prompt Rules ข้อ 13 ใน `JarvisPersona.kt`
- [x] **KMP Architecture Decoupling & Pure Kotlin Interface**: แยก `DeviceControlHandler` ใน `commonMain` สำหรับ `ToolExecutor` และ implement ใน `DeviceControlExecutor` (`androidMain`)
- [x] **Accessibility Setup & Settings Integration (`MainActivity.kt`, `AndroidManifest.xml`)**: ลงทะเบียน `JarvisAccessibilityService` ใน Manifest และเพิ่มรายการเปิดใช้งานใน Settings Checklist
- [x] **Persona Rules Integration (`JarvisPersona.kt`)**: เพิ่ม `DEVICE_CONTROL_RULES` ในทั้ง `CHAT_SYSTEM_PROMPT` และ `LIVE_SYSTEM_PROMPT`
- [x] **Unit Test Passed 100% (`DeviceControlTest.kt`)**: ชุดทดสอบครอบคลุม Tool Definitions, Registry, และ Executor Routing ผ่าน 100%
- [x] **Wiki Architecture Document**: บันทึกรายงานการพัฒนาฉบับสมบูรณ์ที่ `[[Changelog_2026-09-06_JARVIS_Device_Control]]`

### 2026-09-04: SignalTracker Deduplication & TradingView Timeout Optimization
- [x] **SignalOutcomeTracker Deduplication**: แก้ปัญหา `Recorded signal ...` ถูกบันทึกและพ่น Log ซ้ำทุก 30 วินาที โดยบันทึกเฉพาะแท่งสัญญาณใหม่ (`isNewSignalBar`), เปลี่ยน SQL เป็น `INSERT OR IGNORE`, และเพิ่มการตรวจ `getSignalTrackingRecordById` ป้องกันการ overwrite ค่า `bars_held` และ `mfe`
- [x] **TradingView WebSocket Connect Timeout & Network Cascade Fix**: ปรับ Timeout เป็น 7 วินาที, ตัดจบการ cascade symbol ซ้ำเมื่อ host มีปัญหาทางเน็ตเวิร์ก, และเพิ่ม 60s Backoff เมื่อมีแคชในฐานข้อมูล (`TV:DB`)

### 2026-09-04: EMA 14/60 Near-Cross (Convergence) & Confirmed Cross Detection
- [x] **EMA 14/60 Near-Cross & Convergence Detection (`SignalAlertProvider.kt`, `SmcFlowAlertProvider.kt`)**: พัฒนาระบบตรวจจับการเคลื่อนที่เข้าหากันของ EMA 14 และ EMA 60 ด้วยสูตร Dynamic Spread Threshold `max(atr14 * 0.35, close * 0.0012)` พร้อมตรวจทิศทางการเงยหัว/ปักหัว (Directional Velocity) แจ้งเตือน Anticipation BUY (ก่อน Golden Cross) และ Anticipation SELL (ก่อน Death Cross) ล่วงหน้าด้วยความเชื่อมั่น 76%
- [x] **Confirmed Golden Cross & Death Cross Signals**: ตรวจสอบการตัดกันยืนยันเมื่อปิดแท่งเทียน พร้อมปล่อยสัญญาณเทรด BUY (`E14/60▲`) และ SELL (`E14/60▼`) พร้อม Entry, SL, TP อัตโนมัติ
- [x] **Alert Catalog & Automation Presets (`AutomationModels.kt`, `AutomationScreen.kt`)**: เพิ่มฟิลด์ `ema14_60_cross`, `ema14_60_near_cross`, `ema14_60_near_cross_side`, `ema14_60_spread`, `ema14_60_state`, `ema14`, `ema60` และ 3 Alert Presets สำเร็จรูปในหน้า Automation
- [x] **Unit Tests Passed 100% (`SignalAnticipationTest.kt`, `SignalAlertProviderTest.kt`)**: ชุดการทดสอบครอบคลุมทั้ง Buy/Sell Near-Cross, Confirmed Cross, และ Catalog Support

### 2026-09-04: Anticipation & Keyzone 3D Chat Alert Card Architecture & Content Fallback
- [x] **Anticipation & Keyzone Alert Card Incompleteness Fix (`JarvisAutomationService.kt`, `MessageBubble.kt`)**: แก้ปัญหาการ์ดแจ้งเตือนในแชทแสดงเพียงข้อความ "signal_anticipation >= 1" โดยแยก routing เฉพาะสำหรับ Anticipation & Keyzone Alert, ส่ง structured metadata ครบทุกมิติ, สร้างการ์ด 3D สวยงาม `AnticipationAlertCard3D` (Badge ⚡ คาดการณ์ BUY/SELL, โซน, ราคา, ความเชื่อมั่น, โครงสร้างตลาด) และ `KeyzoneAlertCard3D`, พร้อมระบบ Content Fallback แปลงการ์ดประวัติเดิมในแชทให้แสดงรายละเอียดสมบูรณ์
- [x] **Unit Test Suites Expansion (`SignalAnticipationTest.kt`)**: เพิ่มชุดการทดสอบ `parseAlertCardMeta` ครบทุกประเภท (Anticipation, Keyzone, Legacy Fallback, Generic) ผ่าน 100%

### 2026-09-04: Live Voice Speech Truncation Fix & Signal Observability Optimization
- [x] **Live Alert Voice Speech Truncation Fix (`JarvisAutomationService.kt`)**: แก้ปัญหาเสียง AI พูดไม่จบประโยค โดยขยาย `liveVoiceSummaryCharCap` จาก 120 เป็น 350 เพื่อให้จบประโยคสมบูรณ์ด้วย `turnComplete == true`, ขยาย `liveVoiceSessionTimeoutMs` เป็น 35s, และปรับ System Instruction ให้ออกเสียงจบประโยคด้วย "ค่ะ" เสมอ
- [x] **Signal Observability & Multi-TF Deduplication (`SignalAlertProvider.kt`)**: รวม Logcat `SignalDataSource` เหลือ 1 บรรทัดต่อ Symbol และแก้ปัญหาดึงแท่งเทียน `1h` ซ้ำ 2 ครั้งในรอบเดียวกัน

### 2026-09-04: Mobile AI Trading Intelligence & Closed-Loop Reinforcement Architecture
- [x] **Pillar 1: Multi-Timeframe TradingView Fusion (`TradingViewSignalIntelligence.kt`)**: วิเคราะห์ข้อมูล MTF (15m, 30m, 1h, 4h) ร่วมกับ SMC Score และ Dynamic Indicators บนมือถือโดยตรง
- [x] **Pillar 2: Predictive Pre-Signal & Signal Anticipation Engine (`SignalAlertProvider.kt`)**: ตรวจจับ Keyzone Proximity (0.3×ATR), Intra-bar Wick Sweep Rejection, และ RSI Extreme/Divergence ปล่อยสัญญาณเตือนล่วงหน้า `signal_stage = "ANTICIPATION"`
- [x] **Pillar 3: Adaptive Self-Learning Backtest Integration (`BacktestToolHandler.kt`)**: รองรับ Parameter Optimization และ Genetic/Mix Strategy Simulation
- [x] **Pillar 4: Closed-Loop Signal Outcome Tracker (`SignalOutcomeTracker.kt`)**: สร้าง Schema `SignalTrackingRecord` (migration `10.sqm`), บันทึกทุก Signal และวัดผลราคาจริง (Hit TP, Hit SL, MFE, MAE, R-multiple, 50-bar Expiry)
- [x] **Closed-Loop Strategy Reinforcement Gate (`StrategyConfirmationGate.kt`)**: ปรับเพิ่ม Confidence Boost (+0.10 ถึง +0.20) สำหรับกลยุทธ์ชนะต่อเนื่อง และปรับลด (-0.15 ถึง -0.25) พร้อมสกัดกั้นสัญญาณอ่อนแอในตลาดที่ไม่เหมาะสม
- [x] **Mobile UI & Alert Catalog Integration (`AutomationScreen.kt`, `AutomationModels.kt`)**: เพิ่ม Preset ลัด `⚡ คาดการณ์ Signal ล่วงหน้า (Anticipation)` และลงทะเบียนฟิลด์ `signal_anticipation` ใน Alert Catalog
- [x] **Terminal Outcomes & Strategy Reinforcement Stats (`TerminalEventsTab.kt`, `ResearchToolHandler.kt`)**: การ์ด `🧠 Closed-Loop Signal Outcomes` ใน Terminal Events และสรุปผล AI Confidence ใน `trading_signal_stats`
- [x] **Unit Test Suites**: สร้าง `SignalAnticipationTest.kt` และ `SignalOutcomeTrackerTest.kt` ผ่าน 100% ครบ 130 รายการทดสอบ
- [x] **Wiki Architecture Document**: บันทึกแผนงานและสถาปัตยกรรมฉบับสมบูรณ์ที่ `[[08_Mobile_AI_Trading_Intelligence_Plan]]`

### 2026-09-04: Non-Trading Tools Flexibility & Thai Localization
- [x] Thai Date & Time Context (`ToolExecutor.kt`): วัน/เดือนภาษาไทย, ปี พ.ศ. 2569, วินาที และ Timezone `Asia/Bangkok`
- [x] Thai Unit Normalization: แปลงหน่วยไทย (กิโลเมตร, ไร่, ตารางวา, วา, เซลเซียส) และรองรับพารามิเตอร์ยืดหยุ่น
- [x] Full Grounding & Web Search in Text Chat (`GeminiService.kt`): รองรับ Google Grounding, Translation, Summarization อัตโนมัติ
- [x] Camera & Vision Fuzzy Matching (`CameraToolExecutor.kt`): รองรับ substring ("gpt-4o", "gemini", "stream", "photo")
- [x] Expanded File Extensions: เพิ่ม c, cpp, dart, go, rust, swift, php, svg, env ใน `FileToolExecutor.kt`
- [x] Timeframe Normalization for Alerts: รองรับ MT5 (`m15`, `h1`, `d1`) และ Weekly ใน `JarvisOrchestrator.kt`
- [x] ชุดทดสอบ `NonTradingToolsTest.kt`: ผ่าน 100%

### 2026-09-04: Unconstrained Dynamic Tool Creation
- [x] ปลดล็อกการบังคับคำนำหน้า `custom_` ยกเว้นกรณีชื่อชนกับ Built-in Tools
- [x] รองรับ Parameter Schema ทั้ง Full JSON Schema, Short JSON, และ Comma-separated list
- [x] Template Argument Interpolation (`{{param}}`, `{param}`, `\bparam\b`)
- [x] Formula Execution Mode: คำนวณสูตรคณิตศาสตร์ (Lot Size, RRR, Pivot) คืนผลลัพธ์และขั้นตอนคำนวณผ่าน `evalMath`
- [x] Persistent Tool Schema Loading ข้าม session จาก `custom_agent_tools/*.json`
- [x] ปลดล็อก Required `exchange` และขยาย Timeframe Enums ใน Trading/SMC Tools ทั้งหมด
- [x] ชุดทดสอบ `DynamicToolCreationTest.kt`: ผ่าน 100%

### 2026-09-04: Economic Calendar Timezone & Priority
- [x] แปลงเวลา ForexFactory Feed แบบ ISO-8601 UTC $\to$ Asia/Bangkok (+7) แก้บั๊กเลื่อนเวลา +4 ชั่วโมงข้ามวัน
- [x] จัดลำดับข่าวแบบ UPCOMING [รอประกาศ] ขึ้นก่อนตามลำดับเวลา เพื่อไม่ให้ข่าวปลายสัปดาห์ตกหล่น
- [x] ระบุเวลาไทยและเวลา New York คู่กัน พร้อม Prompt วันที่ปัจจุบันป้องกัน AI ตอบสับสน
- [x] ชุดทดสอบ `EconomicCalendarTimeTest.kt`: ผ่าน 100%

### 2026-09-04: Live Voice Stability & Thai Articulation
- [x] Mute Mic อัตโนมัติระหว่าง Android TTS Fallback ป้องกัน Acoustic Echo Loop พูดซ้ำ 2 รอบ
- [x] Conversational Pure-Thai Greeting Trigger (`สวัสดี$agentName พร้อมคุยไหม`)
- [x] Thai Phonetics Guardrails ใน `LIVE_RULES`: กำหนดการออกเสียงสระ/ตัวสะกดแม่เกย (คำว่า "เจ้านาย" ชัดเจน ห้ามเพี้ยนเป็น "เจ้านาว")

### 2026-09-04: Dynamic Indicator Overlays & Timeframe Normalization
- [x] คำนวณ Dynamic Overlay ได้ทุกคาบเวลา (`ema8`, `ema21`, `sma50`, `sma200`, `dc55`, `bb_20_2`) บน Lightweight Charts V5
- [x] ปรับ `TradingChartScreen` และ `dashboard_engine.js` ให้แสดง Toolbar Chips สี HSL สวยงามตามคาบเวลา
- [x] ชุดทดสอบ `ChartDynamicIndicatorTest.kt`: ผ่าน 100%

### 2026-09-03 – 2026-09-04: Backend Modularization & Terminal Separation
- [x] Decouple `TradingToolExecutionBackend.kt` (2,060 บรรทัด $\to$ ~80 บรรทัด Coordinator) ผ่าน `TradingToolRouter`
- [x] แยก Domain Handlers: `Mt5ToolHandler.kt`, `MarketTechnicalToolHandler.kt`, `ResearchToolHandler.kt`, `BacktestToolHandler.kt`
- [x] แยก `TradingTerminalScreen.kt` ออกเป็น 8 แท็บย่อย (`Overview`, `Trade`, `History`, `Events`, `Settings`, `Connection`, `EditDialog`, `Formatting`)
- [x] Unit test suites ทั้ง 24 ชุดบน Android ผ่าน 100%

### 2026-08-15 – 2026-08-29: AI Supervisor & Trading Intelligence
- [x] AI Strategy Supervisor + 5TF Market Context Digest (`MarketContextDigest.kt`)
- [x] Unified SMC V5 Basin Optimization ใน `tools/unified_smc_lab.py` & Kotlin Port ใน `UnifiedSmcSignals.kt`
- [x] Signal Alert System V2 (3D Chat Cards, Priority Live Voice Queue, Repeat/Delete Controls)
- [x] Backtesting Engine & Optimization V2 (Monte Carlo, Walk-Forward, Permutation Test)

---

## 🧪 Test Status Summary (สถานะการทดสอบล่าสุด)
- **composeApp (Mobile)**: 24 test suites ผ่านครบ 100% (`:composeApp:testDebugUnitTest` ผ่านสมบูรณ์)
- **mt5-core-server**: 21 test files (124 tests) ผ่านครบ 100% (`npm test` ผ่านสมบูรณ์)
- **Analytics Scripts**: `npm run analyze`, `npm run analytics:gate`, `npm run analytics:help` ทำงานได้สมบูรณ์

---

## 🎯 Next Recommended Sprint
1. **Sprint 1 (Server & Lab Integration)**:
   - เพิ่ม route `/api/mt5/auto/external-signal` ใน `mt5-core-server`
   - เชื่อมต่อ `tools/unified_smc_watch.py` เข้ากับ Cron/Task Scheduler เพื่อรัน Paper Forward Test
2. **Sprint 2 (Mobile UI/UX)**:
   - ออกแบบ Markdown Table & Visual Card Renderer ใน `MessageBubble.kt` สำหรับผลการวิเคราะห์/ตาราง
   - เพิ่ม Local Push Notifications ขณะแอปทำงานใน Background

---

## 📎 Related Docs
- [[index]] — Wiki Home
- [[App_Review_Checklist]] — เช็คลิสต์ 15 หมวดของแอปมือถือ
- [[63_Unified_SMC_Watch_Mode_Plan]] — แผนเชื่อมต่อ Python Watcher สู่ Server
- [[64_Unified_SMC_App_Integration]] — Unified SMC ในแอป
- [[65_AI_Strategy_Supervisor]] — AI Strategy Supervisor 5TF
- [[00_AI_Agent_Trader_Pro_Roadmap]] — แผนพัฒนาใหญ่ Pro AI Trader
- [[log]] — บันทึก Changelog ละเอียดรายวัน

