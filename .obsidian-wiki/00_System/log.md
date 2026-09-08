## 2026-09-09 — Gemini 3.1 Flash Live Primary Model & Spontaneous Model Switch Fix
- **Establish `gemini-3.1-flash-live-preview` as Primary Live Model (`ModelConfig.kt`, `SettingsController.kt`, `LiveGeminiService.kt`, `JarvisViewModel.kt`)**:
  - **ปัญหาที่พบ**: ผู้ใช้ทดสอบพบว่า `gemini-3.1-flash-live-preview` ทำงานได้เร็วที่สุด (~835ms), สำเนียงไทยเป็นธรรมชาติ และเรียก Native Tools แม่นยำ แต่ในบางครั้งระบบกลับสลับไปใช้ `gemini-2.5-flash-native-audio-preview-09-2025` เองโดยอัตโนมัติ ทั้งที่ผู้ใช้เลือก 3.1 ไว้
  - **สาเหตุเชิงลึก**:
    1. `"gemini-3.1-flash-live-preview"` เคยถูกบันทึกไว้ใน `deprecatedLiveModels` ของ `SettingsController.kt` ทำให้ทุกครั้งที่เปิดแอปใหม่ (`loadPersistedSettings`) โค้ดจะมองว่า 3.1 ตกยุค และ migrate กลับไปเป็น `09-2025` ใน SQLite
    2. `JarvisViewModel.kt` มีการเชื่อมต่อ `orchestrator.onLiveModelChanged` ไปยัง `settings.updateLiveModelSilently(winningModel)` ซึ่งเมื่อเกิด runtime fallback ชั่วคราว (เช่น เกิดความล่าช้าบนเครือข่าย) จะนำโมเดล fallback ไปเซฟทับฐานข้อมูลจริงอย่างถาวร
    3. `LiveGeminiService.kt` ไม่ได้รีเซ็ตตัวแปร `liveModelName` กลับไปเป็นโมเดลที่ผู้ใช้เลือกไว้เมื่อเริ่ม session ใหม่ ทำให้โมเดล fallback ค้างข้ามรอบการสนทนา
    4. `setupWatchdog` ตั้งเวลา timeout ไว้เพียง 3500ms ซึ่งสั้นเกินไปสำหรับเครือข่ายมือถือบางช่วงเวลา เมื่อเกิด timeout จะติด penalty นานถึง 15 นาที และข้าม 3.1 ไปใช้ fallback ตัวอื่นทันที
  - **แนวทางการแก้ไข**:
    1. ตั้ง `DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"` และจัดให้อยู่อันดับ 1 ใน `liveCandidates` และ `SEED_LIVE_MODELS`
    2. ลบ 3.1 ออกจาก `deprecatedLiveModels` และใส่ `09-2025` เข้าไปแทนเพื่อ auto-migrate ฐานข้อมูลเก่าที่เคยถูกเขียนทับกลับมาเป็น 3.1 ทันที
    3. ยกเลิกการเขียนทับฐานข้อมูลใน `JarvisViewModel.kt` ตอน runtime fallback
    4. รีเซ็ต `liveModelName = configuredLiveModelName` เสมอใน `LiveGeminiService.kt` ทุกครั้งที่เริ่มคุยรอบใหม่
    5. เพิ่ม watchdog timeout เป็น 6000ms และลดเวลา penalty เหลือ 60 วินาที
    6. อัปเดต Unit Test `DynamicModelTest.kt` ให้รองรับและผ่านทั้งหมด 100%

## 2026-09-08 — Keyguard Lockscreen Overlay & Normal Mode Screen Sleep Fix
- **Fix MainActivity Displaying over Lockscreen in Normal Mode (`AndroidManifest.xml`, `MainActivity.kt`, `AlwaysLiveManager.kt`, `FloatingWidgetService.kt`)**:
  - **ปัญหาที่พบ**: เมื่อเปิดแอปค้างไว้ในโหมดปกติ และปล่อยให้มือถือดับหน้าจอ/ล็อกหน้าจอ เมื่อกดปุ่ม Power ให้หน้าจอสว่างขึ้นมา แทนที่จะติดหน้าล็อก (ใส่รหัส PIN/สแกนนิ้ว) กลับแสดงหน้าแอปทับหน้าล็อก ทำให้ใช้งานแอปได้แต่ไปหน้าโฮมหรือแอปอื่นไม่ได้ จนกว่าจะกดปิดแอปจึงจะโผล่ไปยังหน้าปลดล็อก
  - **สาเหตุเชิงลึก**:
    1. `AndroidManifest.xml` ประกาศ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` แบบ Static บนแท็ก `<activity android:name=".MainActivity">` ส่งผลให้ Window Manager ของระบบบังคับเรนเดอร์ Activity เหนือ Keyguard เสมอ
    2. `AlwaysLiveManager.wakeScreen()` สั่ง `turnScreenOnTemporarily()` โดยไม่ตรวจสอบสถานะ ทำให้มีการเซ็ต `setShowWhenLocked(true)` และ `FLAG_SHOW_WHEN_LOCKED` แม้ผู้ใช้อยู่ในโหมดปกติ
    3. ขาดการล้าง Flag ใน Lifecycle ของ `MainActivity.kt` (`onCreate`, `onResume`, `onStop`) เมื่ออยู่ในสถานะ `AlwaysLiveState.OFF`
    4. `FloatingWidgetService.kt` มีการใส่ `FLAG_KEEP_SCREEN_ON` ใน WindowParams ของ Overlay
  - **แนวทางการแก้ไข**:
    1. ลบ `android:turnScreenOn="true"` และ `android:showWhenLocked="true"` ออกจาก `AndroidManifest.xml` อย่างถาวร และเปลี่ยนไปใช้ Dynamic Runtime API ควบคุมเฉพาะโหมด Always Live เท่านั้น
    2. ใส่เงื่อนไข `if (_state.value == AlwaysLiveState.FULL_SCREEN)` ใน `AlwaysLiveManager.wakeScreen()` ก่อนเรียก `turnScreenOnTemporarily()`
    3. เพิ่ม `clearScreenFlags()` ใน `onCreate()`, `onResume()`, และ `onStop()` ใน `MainActivity.kt` เพื่อล้าง `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, และ `FLAG_KEEP_SCREEN_ON` ออกจาก Window
    4. ลบ `FLAG_KEEP_SCREEN_ON` ออกจาก `FloatingWidgetService.kt`

## 2026-09-08 — Screen Wakeup Lifecycle & Normal Mode Auto-Sleep Enforcement
- **Enforce Screen Sleep in Normal Mode (`App.kt`, `MainActivity.kt`, `AlwaysLiveManager.kt`)**:
  - **ปัญหาที่พบ**: เมื่อเข้าโหมดควบคุม (Always Live) แล้วสั่งปิด (`disable() → OFF`) หน้าจอยังคงติดสว่างค้างตลอดเวลา ไม่พักหน้าจอตามเวลา Display Timeout ของระบบ จนกว่าผู้ใช้จะกดปิดแอป (Kill task)
  - **สาเหตุเชิงลึก**:
    1. `MainActivity.wakeAndTurnScreenOn()` เรียก `window.addFlags(FLAG_KEEP_SCREEN_ON)` ค้างไว้บน Activity Window โดยไม่มีการ Clear Flags เมื่อออกจากโหมด Always Live
    2. `AlwaysLiveManager.wakeScreen()` เรียก `acquireScreenBrightLock()` ซึ่งถือ `SCREEN_BRIGHT_WAKE_LOCK` นานถึง 4 ชั่วโมง ส่งผลให้หน้าจอติดสว่างค้างแม้จะไม่ได้อยู่ในโหมดควบคุม
    3. `App.kt` ผูก `onKeepScreenOn(isListening || showAlwaysLive)` ทำให้เมื่อมีการฟังเสียงค้างอยู่ หน้าจอจะไม่ยอมพัก
    4. เมื่อ `closeAlwaysLive()` ถูกเรียก ไม่ได้สั่งหยุด `viewModel.stopVoiceInput()`
  - **แนวทางการแก้ไข**:
    1. เพิ่มฟังก์ชัน `clearScreenFlags()` ใน `MainActivity.kt` เพื่อล้าง `FLAG_KEEP_SCREEN_ON`, `FLAG_TURN_SCREEN_ON`, `FLAG_SHOW_WHEN_LOCKED`, `setShowWhenLocked(false)`, และ `setTurnScreenOn(false)` ทุกครั้งที่ออกจากโหมด Always Live
    2. ปรับปรุง `AlwaysLiveManager.wakeScreen()` ให้ใช้ Temporary WakeLock (10 วินาที) สำหรับปลุกหน้าจอชั่วคราวเท่านั้น และจะไม่ถือ `SCREEN_BRIGHT_WAKE_LOCK` หากไม่ได้อยู่ในสถานะ `FULL_SCREEN` หรือ `MINI_FLOATING`
    3. ปรับ `App.kt` ให้ `onKeepScreenOn(showAlwaysLive)` เท่านั้น แยก `isListening` ออกจากการเปิดหน้าจอค้าง เพื่อให้ในโหมดปกติหน้าจอดับพักได้ตามปกติ 100%
    4. ผูก `viewModel.stopVoiceInput()` เมื่อปิด Always Live ผ่าน `registerCloseAlwaysLive`

## 2026-09-08 — Anticipation Alert Card UI Refinement (Confidence & Price Clean Row, Redundancy Elimination)
- **AnticipationAlertCard3D UI Layout & Text Squeezing Fix (`MessageBubble.kt`)**:
  - **แก้ไขปัญหาตัวเลข % เบียดตกขอบแนวตั้ง**: นำตัวเลขความเชื่อมั่น (`${meta.confidence}%`) ออกจากแถว Header (บรรทัดที่ 1) ทำให้ส่วนแสดงผล Badge คาดการณ์ `⚡ คาดการณ์ SELL/BUY`, ชื่อคู่เงิน `XAUUSD`, และ Badge Timeframe `15M` มีพื้นที่กว้างขวางเต็มที่ ไม่ถูกบีบตัวอักษรแนวตั้งอีกต่อไป
  - **บรรทัดที่ 2: แสดงเฉพาะ % ความเชื่อมั่น และราคาปัจจุบัน**: จัดวางให้อยู่ในแถวที่ 2 โดยเฉพาะ (`ความเชื่อมั่น 76%` และ `ราคา 4405.06`) ด้วยระยะห่างซ้าย-ขวาอย่างสมดุล (SpaceBetween) ตัวหนังสืออ่านง่ายชัดเจน
  - **กำจัดการแสดงผลซ้ำซ้อน (Redundancy Elimination)**: ตัดการแสดงผล `meta.zone` (เช่น `EMA Convergence: 4409.31 → 4408.98`) ออกจากบรรทัดที่ 2 ซึ่งเดิมข้อความมีความยาวจนถูกตัดท้าย `....` และซ้ำซ้อนกับรายการ `ปัจจัยที่เกิด` ด้านล่าง ช่วยให้การ์ดกระชับ สะอาดตา และไม่อึดอัด
- **Fallback Markdown Card Synchronization (`JarvisAutomationService.kt`)**:
  - ปรับปรุง `buildAnticipationChatCard` ให้บรรทัดที่ 2 แสดงผล `ความเชื่อมั่น: $conf% • ราคา: $close` ตรงกันกับการ์ด 3D ไม่ให้มีข้อความโซนซ้ำซ้อนกับปัจจัยที่เกิด

## 2026-09-07 — Gemini Live Voice & Connection Stability Optimization
- **Multi-Tier Automated Fallback Chain (`ModelConfig.kt`, `LiveGeminiService.kt`)**:
  - รองรับการสลับโมเดล Live อัตโนมัติ: `gemini-3.1-flash-live-preview` -> `gemini-2.5-flash-native-audio-preview-12-2025` -> `gemini-2.5-flash-native-audio-latest` เมื่อเกิดข้อผิดพลาดในการ Setup หรือการเชื่อมต่อ โดยไม่ต้องให้ผู้ใช้เข้าไปกดเปลี่ยนโมเดลเอง
  - ระบบคัดกรอง Blacklisted / Dead models ออกจาก Chain อัตโนมัติ
- **7-Second Setup Watchdog (`LiveGeminiService.kt`)**:
  - Coroutine watchdog คอยจับเวลาการตอบกลับ `setupComplete` จาก Server ภายใน 7 วินาที หากเกินเวลาจะปิด socket และ rotate ไปยัง fallback model ถัดไปทันที ป้องกันการค้างรอนาน 15–30 วินาที
- **Acoustic Synthesizer Purity & Native Thai Accent Shield (`VoiceController.kt`, `JarvisPersona.kt`)**:
  - เปลี่ยน Realtime Greeting Input จาก `"สวัสดีJARVIS พร้อมคุยไหม"` เป็นภาษาไทยล้วน `"สวัสดีจาวิส พร้อมคุยไหม"` ป้องกัน Acoustic Decoder ของ Gemini สลับไปใช้สำเนียงและ Prosody ภาษาอังกฤษ
  - ล็อกประโยคทักทายตายตัว (Fixed Greeting): `"สวัสดีค่ะนายท่าน จาวิสพร้อมคุยแล้วค่ะ มีอะไรให้จาวิสช่วยวันนี้ดีคะ"` (ผูกกับ `userCallName` และ gender particle) คำต่อคำ
  - ลบ Vibe บัตเลอร์อังกฤษออกจากค่าตั้งต้น และห้ามแต่งประโยคเรื่องตลาดหุ้น/การเทรดในคำทักทายเริ่มต้น เพื่อให้น้ำเสียง อารมณ์ และสำเนียงนุ่มนวล ชัดเจน เหมือนกันทุกครั้ง

## 2026-09-06 — Always AI Live Mode (Full-Screen, Mini Robot Overlay & Background Wake-on-Voice)
- **Always AI Live Mode Architecture (`AlwaysLiveManager.kt`, `AlwaysLiveScreen.kt`, `FloatingWidgetService.kt`, `HotwordDetector.kt`, `JarvisAvatar.kt`)**:
  - **Full-Screen Live Mode**: หน้าจอแสดงผล JARVIS Robot Avatar 3D-styled แบบเต็มจอ พร้อมวงแหวน Audio Visualizer 36 แท่งที่ตอบสนองต่อระดับเสียงไมโครโฟน, แสงพื้นหลัง Dynamic Ambient Gradient ที่เปลี่ยนโทนสีตามอารมณ์ของ AI, ป้ายแสดงสถานะ Live, และแถบควบคุม (ไมค์, กล้องสลับเลนส์หน้า-หลัง, ย่อเป็น Floating Widget, วางสาย)
  - **Mini Robot Overlay (`FloatingWidgetService.kt`)**: อัปเกรดจาก Text Bubble เดิม สู่ Animated Mini Robot Avatar (~80dp) ที่ลอยทับแอปอื่นผ่าน ComposeView บน Foreground Service (พร้อม `ServiceLifecycleOwner`), รองรับการลากย้ายและ Snap to Edge อัตโนมัติ, แตะเพื่อเข้าแอป, แตะสองครั้ง (Double-tap) เพื่อขยายเต็มจอ (Expand), และแตะค้างเพื่อเปิด/ปิดเสียง
  - **Background Wake-on-Call / Hotword (`HotwordDetector.kt`)**: โหมดรับฟังคำสั่งเสียงแม้ขณะจอดับหรือพักหน้าจอ โดยใช้ low-power AudioRecord 8 kHz Mono แบบ Duty-cycle (ฟัง 2 วิ พัก 1 วิ) ร่วมกับ RMS energy VAD ปลุกเครื่องอัตโนมัติ (`ACQUIRE_CAUSES_WAKEUP`, `turnScreenOn`, `showWhenLocked`) และเปิดหน้าจอ Always Live ทับ Lockscreen
  - **JARVIS 3D-Styled Robot Canvas Avatar (`JarvisAvatar.kt`)**: วาดหุ่นยนต์แอนิเมชันด้วย Compose Canvas แสดงอารมณ์ 10 สถานะ (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `HAPPY`, `EXCITED`, `SAD`, `ANGRY`, `LOVE`, `SLEEPING`) พร้อมระบบ micro-animations (การหายใจ, กะพริบตา, โยกหัว, คลื่นปากพูด, แสงเสาอากาศ, หัวใจลอย, ตัวอักษร Zzz)
  - **State Machine Central Coordinator (`AlwaysLiveManager.kt`)**: บริหารสถานะระหว่าง `OFF`, `FULL_SCREEN`, `MINI_FLOATING`, `BACKGROUND_LISTEN`, จัดการ WakeLock และ BroadcastReceiver สลับโหมดอัตโนมัติตามสถานะหน้าจอเปิด/ปิด
  - **Unit Test Coverage (`AlwaysLiveTest.kt`)**: ทดสอบครอบคลุม Enum ทั้ง 10 อารมณ์, State Machine transitions, Keyword Sentiment Analysis, ค่าตั้งต้น และ Immutability ผ่าน 100%

## 2026-09-06 — JARVIS Full Mobile Device Control via Voice & Accessibility Service
- **Full Mobile Device Control Architecture (`JarvisAccessibilityService.kt`, `DeviceControlExecutor.kt`, `DeviceControlHandler.kt`, `DeviceToolDefinitions.kt`)**:
  - **ยกระดับ JARVIS สู่การควบคุมมือถือทั้งเครื่อง**: รองรับการสั่งงานด้วยเสียงแบบ Real-time (Gemini Live) หรือ Text Chat ในการควบคุมระบบและแอปพลิเคชันอื่นบนมือถือแบบไร้สัมผัส (Hands-free Full Device Automation)
  - **17 เครื่องมือใหม่ในหมวด `📱 Device Control` (`DeviceToolDefinitions.kt`)**:
    - **Hardware**: `device_flashlight` (เปิด/ปิดไฟฉาย), `device_volume` (ปรับระดับเสียงทุก stream), `device_brightness` (ปรับความสว่างจอ/Auto), `device_media_control` (เล่น/หยุด/ข้ามเพลง)
    - **App Launcher**: `device_open_app` (เปิดแอป 40+ ตัวหรือค้นหาในเครื่อง), `device_navigate` (นำทาง Google Maps), `device_send_email` (ร่างอีเมล), `device_add_calendar` (เพิ่มนัดในปฏิทิน), `device_make_call` (โทรศัพท์), `device_send_sms` (ร่างข้อความ), `device_set_alarm` (ตั้งปลุก), `device_open_url` / `device_search_web` (เบราว์เซอร์และการค้นหา)
    - **Screen Interaction & Accessibility**: `device_read_screen` (สแกนองค์ประกอบ UI และข้อความบนจอ), `device_tap` (คลิกปุ่มตามข้อความหรือพิกัด), `device_type_text` (พิมพ์ข้อความลงในช่องที่โฟกัส), `device_scroll` (เลื่อนจอขึ้น/ลง), `device_press_button` (ปุ่ม Back/Home/Recents/Notifications/Quick Settings/Screenshot/Lock), `device_get_app_info` (ดูแอปที่กำลังเปิดอยู่)
    - **System Info**: `device_battery_status` (เช็คแบตเตอรี่และการชาร์จ), `device_wifi_status` (เช็คสถานะ WiFi และ SSID)
  - **KMP Pure Kotlin Decoupling**: สร้าง `DeviceControlHandler` ใน `commonMain` เพื่อให้ `ToolExecutor` สามารถ route คำสั่งไปยัง `DeviceControlExecutor` ใน `androidMain` ได้อย่างสมบูรณ์โดยไม่มีปัญหา Kotlin Multiplatform dependency
  - **Accessibility Setup Integration (`MainActivity.kt`, `AndroidManifest.xml`, `accessibility_service_config.xml`)**:
    - ลงทะเบียน `JarvisAccessibilityService` ใน Manifest พร้อมสิทธิ์ `CALL_PHONE`, `SEND_SMS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `SET_ALARM`, `WRITE_SETTINGS`
    - เพิ่มรายการเปิดใช้งาน Accessibility ใน Setup Checklist ของ Settings Dialog ให้ผู้ใช้แตะเปิดได้ในคลิกเดียว
  - **Persona Rules (`JarvisPersona.kt`)**: เพิ่ม `DEVICE_CONTROL_RULES` ในทั้ง `CHAT_SYSTEM_PROMPT` และ `LIVE_SYSTEM_PROMPT` กำชับ AI ให้ตอบสนองและเรียกใช้ Tool ควบคุมอุปกรณ์ทันที
  - **Unit Test Coverage (`DeviceControlTest.kt`)**: ทดสอบความครบถ้วนของนิยามเครื่องมือ, การลงทะเบียนใน ToolRegistry, และการส่งคำสั่งผ่าน ToolExecutor ผ่าน 100%

## 2026-09-05 — Anticipation Card UI Text Squeezing Fix, Timeframe Display Badge & Model Fallback Optimization
- **AnticipationAlertCard3D UI Layout & Price Squeezing Fix (`MessageBubble.kt`)**:
  - **ปัญหาที่ตรวจพบจาก Screenshot**: ในการ์ดคาดการณ์ 3D แถบ `📍 EMA Convergence: 4438.38 → 4436.73` กินพื้นที่เกือบเต็มความกว้างของ Row ทำให้ข้อความราคา `@ 4424.04` ทางขวาสุดเหลือพื้นที่กว้างเพียงไม่กี่พิกเซล และถูก Compose บีบตัวอักษร wrap แนวตั้งทีละ 1 ตัวอักษรลงมาตามขอบการ์ด (`.`, `@`, `4`, `4`, `2`, `4`, `.`, `0`, `4`) ดูคล้ายข้อความเสียหาย
  - **การแก้ไข (Layout Constraints)**:
    - นำ `TextOverflow` มาใช้งานร่วมกับ `Modifier.weight(1f, fill = false)`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis` สำหรับ `meta.zone`
    - กำหนดให้ราคา `@ ${meta.price}` เป็น `softWrap = false`, `maxLines = 1` และจัดวางแบบ `Arrangement.SpaceBetween`
    - ทำให้ราคาคำนวณขนาดกว้างเต็มที่ก่อนเสมอ และส่วนชื่อโซนจะย่อหรือตัดท้ายด้วย `...` อย่างสวยงาม ไม่มีการบีบตัวอักษรเป็นแถวแนวตั้งอีกต่อไป
- **Timeframe Chip Display & Lifecycle Consistency (`MessageBubble.kt`, `JarvisOrchestrator.kt`, `ToolExecutor.kt`, `JarvisAutomationService.kt`)**:
  - **ปัญหาความสับสน**: ใน Log และเสียง AI แจ้งว่า *"ตั้งแจ้งเตือนที่ไทม์เฟรม 1 ชั่วโมง เรียบร้อย"* แต่บนการ์ดแชทเดิมแสดงเพียงชื่อ `XAUUSD` โดดๆ ไม่มี Timeframe ทำให้ผู้ใช้ตรวจสอบไม่ได้ว่าการ์ดที่เด้งขึ้นมาเป็นการคาดการณ์ของ TF ใด
  - **การแก้ไข**:
    - เพิ่ม Badge Timeframe แบบโปร่งแสง (`[1H]`, `[15M]`, `[4H]` ฯลฯ) ใน Header ของทั้ง `AnticipationAlertCard3D` และ `SignalAlertCard3D` ติดข้างชื่อคู่เงิน
    - ใช้ `IndicatorAlertProvider.splitSymbolAndTf` ในการแยก Symbol และ Timeframe (หากไม่มี suffix จะแสดง `1H` อัตโนมัติ)
    - ปรับ `ToolExecutor.kt` และ `JarvisOrchestrator.kt` ให้เก็บ Symbol ในรูปแบบ `XAUUSD@1h` และชื่อ Alert `Anticipation XAUUSD [1H]` สำหรับ Anticipation Alerts
    - ปรับ `buildAnticipationSpeech` และ `buildAnticipationChatCard` ใน `JarvisAutomationService.kt` ให้ระบุและพูด Timeframe เช่น *"คาดการณ์ ทองคำ ไทม์เฟรม 1 ชั่วโมง..."*
    - เพิ่มการตรวจจับ Edge-triggered bar timestamp สำหรับ `signal_anticipation` และ `signal_anticipation_id` ใน `JarvisAutomationService.kt`
- **Gemini Model Fallback & Latency Optimization (`ModelConfig.kt`)**:
  - **ปัญหาจาก Log**: โมเดลหลักเดิม `gemini-3.1-pro` คืนค่า 404 (not found in v1beta), `gemini-2.5-flash` คืนค่า 404 (deprecated for new users), `gemini-3.5-flash-lite` ติด timeout 8s ส่งผลให้ AI summary เสียเวลารอ cascade fallback นานถึง 13.9 วินาที
  - **การแก้ไข**: ปรับ `DEFAULT_MAIN_MODEL = "gemini-3.6-flash"` ตามคำแนะนำของ Google API และเรียง `GEMINI_FALLBACK_MODELS` นำโมเดลที่เสถียรและเร็ว (`gemini-3.6-flash`, `gemini-3.1-flash-lite`) ขึ้นลำดับแรก
- **Unit Test Coverage (`SignalAnticipationTest.kt`)**:
  - เพิ่มการทดสอบ `testAnticipationTimeframeDisplayAndParsing` ยืนยันการแยกและแสดง Timeframe ถูกต้อง 100%

## 2026-09-05 — Dedicated Signal Anticipation Tool (`trading_signal_anticipation`), 10 Curated Factors & Multi-Factor Confluence
- **Dedicated Signal Anticipation Tool Architecture (`TradingToolDefinitions.kt`, `ToolRegistry.kt`, `ToolExecutor.kt`, `JarvisPersona.kt`)**:
  - **ความยืดหยุ่นที่เพิ่มขึ้นตามคำขอของผู้ใช้**:
    - สร้าง Tool ใหม่ `trading_signal_anticipation` โดยตรงสำหรับ AI (ทั้งในโหมด Text Chat และ Live Voice)
    - รองรับคำสั่งเสียง/ข้อความ เช่น *"ใช้ tool คาดการณ์ล่วงหน้า ทองคำ"* หรือ *"ตั้งแจ้งเตือนคาดการณ์ XAUUSD"*
    - ฟังก์ชันภายในผูกกับการสร้าง Alert อัตโนมัติ (`trading_signal_alert`, `field = signal_anticipation`, `op = >=`, `value = 1`) โดย AI ดำเนินการให้ทันทีโดยไม่ถามย้อนให้ผู้ใช้สับสน
    - **ป้องกันความสับสนใน UI**: นำ Preset การตั้งค่า Anticipation ด้วยตนเองออกจาก UI (`AutomationScreen.kt`) ให้การตั้งค่าคาดการณ์ล่วงหน้าถูกจัดการผ่าน AI Tool เท่านั้นตามความต้องการของผู้ใช้
- **Curated 10-Factor Whitelist & Dynamic Configuration (`AnticipationConfigManager.kt`)**:
  - สร้างคลังปัจจัยมาตรฐาน 10 ปัจจัยที่ผ่านการพิสูจน์ทางคณิตศาสตร์/เทคนิคอล ป้องกันผู้ใช้หรือ AI ระบุปัจจัยมั่ว/ผิดพลาด:
    1. `KEYZONE_PROXIMITY` (Core Default): แตะโซน Demand/Supply OB, FVG, Swing Liquidity (0.3x ATR)
    2. `WICK_SWEEP_REJECTION` (Core Default): กวาด Low/High 10 แท่งแล้วทิ้งไส้เทียนปฏิเสธราคา (Wick >= 1.5x Body)
    3. `RSI_EXTREME` (Core Default): RSI14 Oversold (<=28) หรือ Overbought (>=72)
    4. `EMA_NEAR_CROSS` (Core Default): EMA 14/60 Dynamic Convergence บีบตัวเข้าหากันในระยะกระชั้นชิด
    5. `BOLLINGER_SQUEEZE` (Extended): Bollinger Bandwidth แคบผิดปกติ (<= 2.2x ATR) สะสมพลังเตรียม Breakout
    6. `MACD_HISTOGRAM_TURN` (Extended): MACD Histogram หดตัวกลับทิศใกล้เส้น 0
    7. `VOLUME_ABSORPTION` (Extended): ปริมาณ Volume สูง 1.8x แต่ Spread แคบ (ซุ่มเก็บของหรือรับแรงเทขาย)
    8. `FIBONACCI_GOLDEN_POCKET` (Extended): แตะระดับ Golden Pocket 0.618 - 0.650
    9. `STOCHASTIC_OVERSOLD_TURN` (Extended): Stochastic %K/%D ตัดขึ้นจาก <20 หรือตัดลงจาก >80
    10. `SESSION_OPEN_SWEEP` (Extended): กวาด High/Low ของ Session ก่อนหน้า (เช่น Asia High/Low Sweep)
  - AI สามารถดึงดูรายการ (`list_factors`), แก้ไขเพิ่ม/ลดปัจจัย (`config`), รีเซ็ต (`reset`), และแนะนำปัจจัยที่เหมาะสมตามประเภทสินทรัพย์ (`recommend`) ได้
- **Multi-Factor Confluence Synthesis & Confidence Boosting (`SignalAlertProvider.kt`)**:
  - เมื่อราคาเข้าเงื่อนไขหลายปัจจัยในทิศทางเดียวกัน ระบบจะรวม Confluence เข้าด้วยกัน (เช่น Keyzone + Wick Sweep + RSI + EMA)
  - ปรับสเกลความเชื่อมั่นแบบไดนามิก: 1 ปัจจัย = ค่าเริ่มต้น, 2 ปัจจัย = 80-85%, 3 ปัจจัย = 88-92%, 4+ ปัจจัย = 95-96%
  - สรุปเหตุผลรวมในการ์ดแชท 3D และส่งเสียงสรุปผ่าน Live Voice ชัดเจน
- **Compact 3D Card Layout & Triggered-Factors-Only Display (`MessageBubble.kt`, `JarvisAutomationService.kt`)**:
  - ปรับการ์ด 3D คาดการณ์ในแชท (`AnticipationAlertCard3D`) ให้กะทัดรัด ไม่กินพื้นที่หน้าจอมือถือ
  - **แสดงเฉพาะปัจจัยที่เกิด (Triggered Factors Only)**: ตัดแถวข้อมูลซ้ำซ้อน (ทิศทาง, โครงสร้างตลาด 5TF กล่องใหญ่) ออก เหลือเพียง Badge หัวการ์ด, โซนสำคัญ/ราคา และรายการปัจจัยที่ตรวจพบจริงในสไตล์ Bullet/Tag ชัดเจน
  - ปรับปรุง `buildAnticipationChatCard` ใน fallback Markdown ให้แสดงเฉพาะข้อมูลกระชับและปัจจัยที่เกิด
- **AI Live Voice Summary Optimization — Bias & Key Watch Points (`JarvisPersona.kt`, `JarvisAutomationService.kt`)**:
  - **ไม่ต้องบอกค่าทางเทคนิคมากมาย**: ห้ามอ่านตัวเลขทศนิยมยิบย่อย, ค่า RSI ละเอียด, สเปรด หรือสูตรคำนวณออกเสียง
  - **เน้นสรุปแนวโน้มทิศทาง และสิ่งที่ต้องจับตามองเป็นหลัก**: สรุปว่ากำลังลุ้นกลับตัวขึ้นหรือลงที่แนวรับ/ต้าน และเตือนให้ผู้ใช้จับตาดูการปิดแท่งเทียนยืนยันก่อนเข้าออเดอร์
  - เพิ่มกฎข้อ 11 ใน `LIVE_RULES` ของ `JarvisPersona.kt` กำชับ AI ให้พูดสั้น กระชับ ชัดเจน 1-2 ประโยค จบสมบูรณ์ ลงท้ายด้วย 'ค่ะ' เสมอ
- **Unit Test Verification (`AnticipationConfigManagerTest.kt`, `SignalAnticipationTest.kt`)**:
  - ทดสอบการจัดการ Whitelist, การเพิ่ม/ลดปัจจัย, การบล็อกปัจจัยที่ไม่ได้อยู่ในคลัง, การคำนวณ Confluence และการตรวจสอบความพร้อมของ Tool ผ่าน 100%

## 2026-09-04 — SignalTracker Duplication Fix & TradingView WebSocket Timeout Optimization
- **SignalOutcomeTracker Deduplication & Lifecycle Fix (`SignalAlertProvider.kt`, `SignalOutcomeTracker.kt`, `JarvisDatabase.sq`)**:
  - **Verified in Production Log**: ใน Log ล่าสุด (18:53:04 - 19:29:48) เมื่อเกิดสัญญาณใหม่ `XAUUSD_1h_SELL_1788519600000` ที่เวลา 19:08:12 ระบบบันทึก `SignalTracker: Recorded signal ...` **เพียงครั้งเดียวถ้วน** และไม่พ่นซ้ำหรือบันทึกทับในรอบถัดไปอีกเลยตลอด 21+ นาทีที่เหลือ
- **TradingView WebSocket Host-Level Circuit Breaker & Cascade Abort (`SmcApiService.kt`)**:
  - **Root Cause Identified**: เดิมการตั้ง backoff เป็นระดับ Symbol + Interval (`"$sym|$interval"`) ทำให้เมื่อเกิดเน็ตเวิร์ก timeout ไปยัง `data.tradingview.com` แต่ละ Timeframe (1h, 15m, 5m, 1m) ต่างคนต่างรอ timeout 7s ทีละตัว (รวม 28 วินาทีในรอบเดียว)
  - **Remediation & Host-Level Circuit Breaker**:
    - เพิ่ม `tvHostFailureSkipUntil` ใน Companion Object: เมื่อเกิด Connection Timeout หรือล้มเหลวที่ระดับ Host จะตั้ง Circuit Breaker พักทั้ง Host 60 วินาที
    - ทำให้ Timeframe อื่นๆ (15m, 5m, 1m) ในรอบนั้นดึงจาก SQLite DB Cache (`TV:DB`) ที่มีอยู่แล้ว 300-500 แท่งทันทีใน 1ms โดยไม่ต้องเสียเวลารอ timeout 7s ซ้ำๆ
    - เมื่อเชื่อมต่อสำเร็จจะ reset `tvHostFailureSkipUntil = 0L` ทันที

## 2026-09-04 — EMA 14/60 Near-Cross (Convergence) & Confirmed Cross Detection System
- **EMA 14 / EMA 60 Dynamic Convergence & Cross Engine (`SignalAlertProvider.kt`, `SmcFlowAlertProvider.kt`, `AutomationModels.kt`, `AutomationScreen.kt`)**:
  - **ความสามารถที่พัฒนาขึ้นตามคำขอของผู้ใช้**:
    1. **การตรวจจับระยะเกือบตัดกัน (Near-Cross / Convergence)**:
       - คำนวณระยะห่าง (Spread) แบบไดนามิกเทียบความผันผวนของราคา: `nearCrossThreshold = max(atr14 * 0.35, close * 0.0012)` ร่วมกับการตรวจสอบว่าเส้นกำลังบีบตัวแคบลงจริง (`spreadNow < spreadPrev`)
       - ตรวจทิศทางการพุ่งเข้าหากัน (Directional Velocity):
         - `BUY Anticipation`: เมื่อ EMA14 < EMA60 แต่วิ่งเงยหัวขึ้นเข้าหา EMA60 (`efNow >= efPrev`) $\to$ คาดการณ์ล่วงหน้าเตรียมเกิด Golden Cross (Confidence 76%)
         - `SELL Anticipation`: เมื่อ EMA14 > EMA60 แต่วิ่งปักหัวลงเข้าหา EMA60 (`efNow <= efPrev`) $\to$ คาดการณ์ล่วงหน้าเตรียมเกิด Death Cross (Confidence 76%)
       - ส่งออกฟิลด์ `ema14_60_near_cross` ("1"/"0"), `ema14_60_near_cross_side` ("BUY"/"SELL"/"NONE") และบรรจุลงใน Setup 4 ของ `detectAnticipation`
    2. **การตรวจจับการตัดกันยืนยัน (Confirmed Cross)**:
       - ตรวจสอบ `GOLDEN_CROSS` (`e14Prev <= e60Prev && e14 > e60`) และ `DEATH_CROSS` (`e14Prev >= e60Prev && e14 < e60`)
       - ส่งออกฟิลด์ `ema14_60_cross` (`GOLDEN_CROSS`/`DEATH_CROSS`/`NONE`), `ema14_60_spread`, `ema14_60_state` (`BULLISH`/`BEARISH`), `ema14`, `ema60`
       - ปล่อยสัญญาณซื้อขายยืนยัน (Confirmed Signal Event) ฝั่ง BUY/SELL ผ่าน marker edge `E14/60▲` และ `E14/60▼` ทันทีเมื่อแท่งปิดยืนยันการตัด
    3. **Alert Catalog & One-Click Presets**:
       - เพิ่มฟิลด์ใหม่ทั้งหมดใน `AlertFieldCatalog.SIGNAL_ALERT` และ `AlertFieldCatalog.SMC_FLOW`
       - เพิ่ม 3 Presets สำเร็จรูปใน `AutomationScreen.kt`:
         - `⚡ EMA 14/60 เกือบตัดกัน (เตือนก่อนตัด)` (`ema14_60_near_cross == 1`)
         - `🎯 EMA 14/60 Golden Cross (ตัดขึ้น)` (`ema14_60_cross == GOLDEN_CROSS`)
         - `🎯 EMA 14/60 Death Cross (ตัดลง)` (`ema14_60_cross == DEATH_CROSS`)
  - **Unit Test Verification (`SignalAnticipationTest.kt`, `SignalAlertProviderTest.kt`)**:
    - เพิ่มการทดสอบ `testDetectAnticipation_emaNearGoldenCrossProducesBuyAnticipation`, `testDetectAnticipation_emaNearDeathCrossProducesSellAnticipation` และ `testAlertCatalogAndPresets_supportEma14_60FieldsAndPresets` ผ่าน 100%

## 2026-09-04 — Anticipation & Keyzone 3D Chat Alert Card Architecture & Content Fallback
- **Anticipation & Keyzone Alert Card Incompleteness Fix (`JarvisAutomationService.kt`, `MessageBubble.kt`)**:
  - **Root Cause Identified**: การ์ดแจ้งเตือนในแชทแสดงผลเฉพาะข้อความ "signal_anticipation >= 1" และค่าปัจจุบัน "1" โดยไม่แสดงรายละเอียด เกิดจาก 3 สาเหตุ:
    1. ฟิลด์ทิศทางของ Anticipation อยู่ใน `signal_anticipation_side` (ยังไม่ใช่ `signal_side` เนื่องจากเป็น pre-signal ก่อนแท่งปิดยืนยัน) ทำให้ `JarvisAutomationService` ตีความเป็น Alert ทั่วไป (`kind = "alert"`) แทนที่จะเป็นการ์ด Signal
    2. เมทาดาทาของ Alert ทั่วไปเดิมบันทึกเฉพาะ `name`, `symbol`, `condition`, `current` ขาดฟิลด์เชิงโครงสร้าง (`side`, `zone`, `desc`, `confidence`, `price`, `mtf`)
    3. `MessageBubble.kt` มีเฉพาะ `SignalAlertCard3D` และ `GenericAlertCard` ซึ่งเมื่อเป็น `GenericAlertCard` จะแสดงเฉพาะเงื่อนไขและค่าปัจจุบัน โดยละทิ้งข้อความเนื้อหาทั้งหมดใน `message.content` ทิ้งไป
  - **Remediation & Architecture**:
    - **`JarvisAutomationService.kt`**:
      - แยก Routing เฉพาะสำหรับ Anticipation Alert (`isAnticipationAlert`) และ Keyzone Watch (`isKeyzoneOnly`)
      - สร้าง `anticipationChatMeta` และ `keyzoneChatMeta` บรรจุข้อมูลครบถ้วน: `side`, `zone`, `desc`, `confidence`, `price`, `mtf`, `summary`, `voice`
      - สร้าง `buildAnticipationChatCard` และ `buildAnticipationSpeech`
    - **`MessageBubble.kt`**:
      - เพิ่ม `AlertCardMeta.Anticipation` และ `AlertCardMeta.Keyzone` ใน Sealed Class
      - พัฒนา Composable `AnticipationAlertCard3D`: การ์ด 3D สวยงามสไตล์ Glassmorphism พร้อม Badge ไฟฟ้า `⚡ คาดการณ์ BUY` (เขียว) หรือ `⚡ คาดการณ์ SELL` (แดง), โซนสำคัญ, ราคาปัจจุบัน, ความเชื่อมั่น %, เหตุผลการวิเคราะห์ และโครงสร้างตลาด 5TF
      - พัฒนา Composable `KeyzoneAlertCard3D`: การ์ด 3D ธีม Amber สำหรับจุดสัมผัสโครงสร้างสำคัญ
      - พัฒนา **Content Fallback**: สำหรับประวัติแชทเดิมในฐานข้อมูลที่บันทึกเป็น `kind = "alert"` และมี `condition` มีคำว่า `signal_anticipation` จะแปลงร่างเป็นการ์ด Anticipation 3D อัตโนมัติ พร้อมดึงข้อมูลจาก `message.content` มาแสดง และปรับ `GenericAlertCard` ให้แสดง `detailText` เสมอ ไม่ปล่อยให้การ์ดว่างเปล่า
    - **Unit Tests (`SignalAnticipationTest.kt`)**:
      - เพิ่ม 4 ชุดการทดสอบทดสอบการแปลง `parseAlertCardMeta` ครบทุกประเภท (Anticipation, Keyzone, Legacy Fallback, Generic Content Preservation) รันผ่าน 100%

## 2026-09-04 — Live Voice Speech Cutoff Fix & Signal Observability Optimization
- **Live Alert Voice Speech Truncation Fix (`JarvisAutomationService.kt`)**:
  - **Root Cause Identified**: ตรวจพบสาเหตุที่ AI พูดไม่จบประโยคและถูกตัดหยุดกลางคัน เกิดจาก `liveVoiceSummaryCharCap = 120` ทำงานแบบ Hard Guillotine ตัดการเชื่อมต่อ WebSocket ทันทีที่ข้อความสะสมเกิน 120 ตัวอักษร (`RESPONSE_LENGTH_CAP 120 chars → close`) ทำให้ข้อความภาษาไทยที่มีตัวอักษร Unicode และสระ/วรรณยุกต์หนาแน่นถูกตัดขาดช่วงวินาทีที่ 2.7 ก่อนที่โมเดลจะส่ง `turnComplete = true` และก่อนที่จะกล่าวถึง Entry, SL, TP หรือลงท้ายคำว่า "ค่ะ"
  - **Remediation**:
    - ปรับ `liveVoiceSummaryCharCap` จาก 120 เป็น **350** ตัวอักษร เพื่อทำหน้าที่เป็น Runaway Circuit Breaker อย่างแท้จริง โดยปล่อยให้การตอบกลับตามปกติ (1-2 ประโยค ~120-180 ตัวอักษร) จบลงอย่างสมบูรณ์และเป็นธรรมชาติด้วยอีเวนต์ `turnComplete == true` จากเซิร์ฟเวอร์
    - ปรับ `liveVoiceSessionTimeoutMs` จาก 22,000ms เป็น **35,000ms** เพื่อรองรับ Latency การส่งมอบ Audio ก้อนแรก (~8.5 วินาที) ร่วมกับการสตรีมเสียงความยาว 12-18 วินาทีได้อย่างปลอดภัย
    - ปรับ System Instruction สำหรับ `LIVE SIGNAL ALERT MODE` ให้ออกคำสั่งสรุปกระชับ 1-2 ประโยค พูดให้จบประโยคอย่างสมบูรณ์ และลงท้ายด้วย "ค่ะ" เสมอ
- **Signal Observability & Multi-TF Deduplication (`SignalAlertProvider.kt`)**:
  - รวม Logcat `SignalDataSource` เหลือ **1 บรรทัดต่อ Symbol** โดยใช้ State-change Throttling
  - กำจัดการ Fetch และ Query แท่งเทียน `1h` ซ้ำ 2 ครั้งในรอบเดียวกัน โดยการ Reuse แท่งเทียน Base Candle ของ Job เข้าสู่ Unified SMC ทันที

## 2026-09-04 — Mobile AI Trading Intelligence & Closed-Loop Reinforcement Architecture (ระบบปัญญาประดิษฐ์เทรดบนมือถือ, การเตือนล่วงหน้า และการเรียนรู้ปรับตัวแบบ Closed-Loop)
- **Architectural Deliverables (ผลการยกระดับ 4 เสาหลักบนระบบมือถือ `composeApp`)**:
  - **Pillar 1: Real-Time Multi-Timeframe TradingView Fusion (`TradingViewSignalIntelligence.kt`)**:
    - ผสานการวิเคราะห์ข้อมูล Multi-Timeframe (15m, 30m, 1h, 4h) ร่วมกับ SMC Score และ Dynamic Technical Indicators บนมือถือโดยตรง ปราศจากการพึ่งพา Vendor ภายนอก
  - **Pillar 2: Predictive Pre-Signal & Signal Anticipation Engine (`SignalAlertProvider.kt`)**:
    - เพิ่ม `detectAnticipation`: วิเคราะห์ Intra-bar dynamics ตรวจสอบ Keyzone Proximity (0.3×ATR จาก Order Blocks, Fair Value Gaps, Swing Liquidity ใน 5TF Market Context Digest), ตรวจจับ Intra-bar Wick Sweep Rejection ที่กวาดสภาพคล่องแล้วทิ้งไส้ย้อนกลับ, และ RSI Extreme/Divergence Setups
    - ระบบ Dual-Stage Signaling: ส่งออก `signal_stage = "ANTICIPATION"` พร้อม `signal_anticipation_desc` และ `signal_anticipation_zone` แจ้งเตือนผู้ใช้ล่วงหน้า โดยล็อกไม่ให้ Auto-Execution บอทยิงออเดอร์ก่อนเวลาจนกว่าจะเกิดแท่งยืนยัน `signal_stage = "CONFIRMED"`
  - **Pillar 3: Adaptive Self-Learning Backtest Integration (`BacktestToolHandler.kt`, `SignalOutcomeTracker.kt`)**:
    - รองรับการปรับจูนพารามิเตอร์และจำลองผลย้อนหลังเพื่อวิวัฒนาการกลยุทธ์
  - **Pillar 4: Closed-Loop Signal Outcome Tracker & Strategy Reinforcement (`SignalOutcomeTracker.kt`, `StrategyConfirmationGate.kt`)**:
    - พัฒนา SQLite Schema `SignalTrackingRecord` (migration `10.sqm`) บันทึกทุก Signal ที่ปล่อยออกไปสู่ตลาด
    - ติดตามผลลัพธ์จากแท่งเทียนราคาตลาดจริง คำนวณ MFE (Maximum Favorable Excursion), MAE (Maximum Adverse Excursion), R-multiple Realized PnL, สถานะการปิดไม้ (`WIN`, `LOSS`, `EXPIRED`)
    - เชื่อมโยงผลลัพธ์เข้ากับ `StrategyConfirmationGate`: เพิ่มฟังก์ชัน Reinforcement Feedback ปรับ Confidence Boost (+0.10 ถึง +0.20) สำหรับกลยุทธ์ที่ชนะต่อเนื่อง และปรับลด (-0.15 ถึง -0.25) พร้อมบล็อกสัญญาณอ่อนแอหากอยู่ในสภาวะตลาดที่ไม่เหมาะสม
  - **Mobile UI & Alert Integration (`AutomationScreen.kt`, `AutomationModels.kt`, `TerminalEventsTab.kt`, `ResearchToolHandler.kt`)**:
    - เพิ่ม Preset ลัด `⚡ คาดการณ์ Signal ล่วงหน้า (Anticipation)` ใน `AutomationScreen.kt` ให้ผู้ใช้สร้าง Alert เฝ้าระวังได้ในคลิกเดียว
    - บรรจุฟิลด์ `signal_anticipation`, `signal_stage`, `signal_anticipation_side`, `signal_anticipation_zone` ลงใน `AlertFieldCatalog.SIGNAL_ALERT`
    - ฝังการ์ด `🧠 Closed-Loop Signal Outcomes` ในแท็บ Events ของหน้า Trading Terminal แสดงผลลัพธ์ไม้จริง (WIN/LOSS, R-multiple, MFE) แบบ Live
    - ขยายเครื่องมือ `trading_signal_stats` ใน `ResearchToolHandler.kt` แสดง Closed-Loop Strategy Reinforcement Status (Win Rate, Avg R, MFE, MAE, และ Confidence Boost/Penalty)
- **Verification & Test Coverage**:
  - สร้าง `SignalAnticipationTest.kt`: ทดสอบ Demand Keyzone Buy, Supply Keyzone Sell, Wick Sweep Rejection, Normal Candle, และ Alert Catalog / Preset integration
  - สร้าง `SignalOutcomeTrackerTest.kt`: ทดสอบ Buy/Sell TP/SL Collision, Conservative Dual-hit Resolution, 50-bar Expiry Timeout, และ Dynamic Confidence Reinforcement
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 1m 12s** (ผ่าน 100% ครบทั้ง 130 รายการทดสอบ)

## 2026-09-04 — Full Non-Trading Tools Audit & Flexibility Upgrades (การตรวจสอบและยกระดับ Tools หมวดอื่นๆ นอกเหนือจากการเทรด)
- **Root Cause & Inflexibility Issues Identified (การตรวจสอบข้อจำกัดในหมวดหมู่อื่นๆ)**:
  - **Date & Time Tool (`get_current_datetime`)**:
    - เดิมแสดงเพียงวันที่แบบ ISO (`2026-09-04`) และชื่อวันภาษาอังกฤษ (`Friday`) โดยไม่มีชื่อวันภาษาไทย, ชื่อเดือนภาษาไทย, ปี พ.ศ. และวินาที ส่งผลให้ AI ในโหมดภาษาไทยตอบวัน/เดือน/ปีสับสนในบางบริบท
  - **Unit Converter Tool (`convert_units`)**:
    - เดิมรองรับเฉพาะชื่อหน่วยภาษาอังกฤษล้วน (`km`, `meter`, `celsius`, `kg`, `rai`) หากผู้ใช้พูดหรือพิมพ์หน่วยเป็นภาษาไทย (เช่น "กิโลเมตร", "เซนติเมตร", "ไร่", "ตารางวา", "ตารางเมตร", "เซลเซียส", "กิโลกรัม") หรือส่ง Argument เป็น `amount`/`from`/`to` จะคืนค่าว่าไม่รองรับหน่วยทันที
    - ขาดหน่วยไทยยอดนิยม เช่น "วา" (2 เมตร), "งาน" (400 ตร.ม.), "ตารางวา" (4 ตร.ม.)
  - **Web Search, Translation & Summarization (`search_web`, `translate_text`, `summarize_text`)**:
    - ใน Text Chat Mode (`GeminiService.kt`) ไม่มีการ Intercept คำขอ `WEB_SEARCH_REQUEST::`, `TRANSLATE_REQUEST::`, `SUMMARIZE_REQUEST::` ทำให้ Gemini ในโหมดข้อความได้รับ Tool Response เป็น String ดิบของ Request แทนที่จะได้รับผลการค้นหาเว็บจริง (ผ่าน Google Search Grounding) หรือผลการแปล/สรุป
  - **Camera & Vision Tools (`CameraToolExecutor.kt`)**:
    - `camera_switch_provider` และ `camera_switch_mode`: บังคับ string แบบ Exact Match เช่น `"openai_gpt4o"`, `"live_stream"` หาก AI ส่ง `"gpt-4o"`, `"claude"`, `"gemini"`, `"stream"`, `"photo"` จะ error ไม่รองรับ
  - **File Management Tools (`FileToolExecutor.kt`)**:
    - `WRITABLE_EXTENSIONS` จำกัดเฉพาะไฟล์ text พื้นฐาน ขาดนามสกุล source code ยอดนิยม (C/C++, Dart, Go, Rust, Swift, PHP, SVG, .env)
  - **Automation & Scheduling (`onManageAlerts`)**:
    - `timeframe` กำหนดตายตัวเฉพาะ `1m..1d` หากส่ง format MT5 (`m15`, `h1`, `d1`) หรือ weekly (`1w`) จะถูกปฏิเสธว่าไม่รองรับ
- **Implemented Solutions**:
  - **Enhanced Thai DateTime (`ToolExecutor.kt`)**: เพิ่มวันภาษาไทย ("วันศุกร์"), วันที่, เดือนภาษาไทย ("กันยายน"), ปี ค.ศ./พ.ศ. ("พ.ศ. 2569"), เวลาถึงระดับวินาที และ Timezone Identifier
  - **Intelligent Unit Normalizer (`ToolExecutor.kt`)**: เพิ่ม `normalizeUnitName` แปลงหน่วยภาษาไทยและชื่อย่อทั้งหมด ("กิโลเมตร" $\to$ `km`, "ไร่" $\to$ `rai`, "ตารางวา" $\to$ `sqwa`, "วา" $\to$ `wa`, "เซลเซียส" $\to$ `celsius`), เพิ่มหน่วยไทย "วา", "งาน", "ตารางวา" ใน Map, รองรับคีย์พารามิเตอร์ `amount`, `from`, `to`
  - **Chat Mode Grounding & Text Pipeline (`GeminiService.kt`)**: เพิ่ม Interceptor สำหรับ `WEB_SEARCH_REQUEST::`, `TRANSLATE_REQUEST::`, `SUMMARIZE_REQUEST::` ใน `generateResponseWithTools` ให้รัน Google Search Grounding และ AI Translation/Summarization โดยอัตโนมัติทั้งใน Live Voice และ Text Chat
  - **Fuzzy Parsing in Camera Tools (`CameraToolExecutor.kt`)**: ปรับปรุงให้รองรับ substring matching ("gpt-4o", "gpt4o", "openai", "claude", "gemini", "stream", "photo", "object", "ar")
  - **Expanded File Extensions (`FileToolExecutor.kt`)**: เพิ่ม `c`, `cpp`, `h`, `hpp`, `dart`, `go`, `rs`, `swift`, `php`, `svg`, `env`
  - **Timeframe Normalization (`JarvisOrchestrator.kt`)**: นำ `TaIndicators.normalizeTimeframe` มาใช้ใน `onManageAlerts` รองรับทั้ง MT5 และ Weekly
- **Verification**:
  - สร้างชุดทดสอบ `NonTradingToolsTest.kt`: ตรวจสอบการแปลงหน่วยภาษาไทย (กิโลเมตร, ไร่, ตารางวา, เซลเซียส), ข้อมูลวัน/เวลาไทย, ความยืดหยุ่นของ Camera Provider/Mode
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ครบทุกชุดทดสอบ

## 2026-09-04 — Comprehensive Tool Audit & Flexible Unconstrained Tool Creation System (ระบบสร้าง Tool ไร้ขีดจำกัด)
- **Root Cause & Limitations Analyzed (การตรวจสอบข้อจำกัดและกรอบเดิมในระบบ Tool)**:
  - **Tool Creation System (`system_create_agent_tool`)**:
    - เดิมบังคับชื่อ tool ต้องขึ้นต้นด้วย `"custom_"` เสมอ (`if (name.startsWith("custom_")) name else "custom_$name"`)
    - ลงทะเบียนด้วย `parameters = null` แบบ Hardcoded ส่งผลให้ Custom Tool ไม่สามารถรับพารามิเตอร์หรือ Argument ใดๆ จากผู้ใช้หรือ AI ได้
    - ใน `ToolExecutor.kt` ฟังก์ชัน `executeCustomSkill` เพิกเฉยต่อ `call.args` ส่งเพียง static prompt addon กลับไป โดยไม่มีการแทนที่ค่าตัวแปรใน Template
    - ใน `JarvisOrchestrator.kt` เมื่อโหลด tool จากไฟล์ JSON ตอนเปิดแอป มีการ hardcode `parameters = null` ทำให้ Schema หายไปหลัง restart แอป
  - **Trading & SMC Tools Overly Restrictive Constraints**:
    - `trading_combined`, `trading_technical_analysis`, `trading_multi_timeframe`: กำหนด `required = listOf("symbol", "exchange")` ทั้งที่ระบบมี Auto Exchange Resolver (XAUUSD $\to$ OANDA, BTC $\to$ BINANCE) ทำให้การเรียกแบบยืดหยุ่นล้มเหลว
    - `trading_combined`, `trading_harmonic_scan`, `trading_elliot_modern_analysis`: กำหนด `interval` `enum = listOf("15m", "1h", "4h", "1D")` ทำให้ไม่รองรับ timeframe ยอดนิยม เช่น 1m, 5m, 30m, 1W หรือ MT5 format (M1, M5, H1, H4)
    - `trading_smc_analysis`, `trading_smc_orderblocks`, `trading_smc_structure`: กำหนด `interval` แบบจำกัด enum ไม่รองรับทั้ง standard และ MT5 format ข้ามแพลตฟอร์ม
- **Comprehensive Solution & Implementation**:
  - **Dynamic Parameter Schema & Flexible Execution Engine (`SystemToolExecutor.kt`, `ToolDefinition.kt`)**:
    - ขยาย `SkillDescriptor` ให้รองรับ `parameters: FunctionParameters?`, `executionType: String` ("prompt", "formula", "chain")
    - รองรับการประกาศ Parameters ได้ทั้งแบบ Full JSON Schema (`{"symbol": {"type": "STRING", "description": "..."}}`), Short JSON (`{"symbol": "คำอธิบาย"}`), และ Comma-separated list (`"symbol, timeframe, risk_pct"`)
    - ปรับปรุงการตั้งชื่อ (`sanitizeToolName`): ไม่บังคับกรอบ `"custom_"` เว้นแต่จะชนกับ Built-in Tool เพื่อป้องกัน Name Collision
    - เก็บ Parameter Schema และ Execution Type ลงไฟล์ JSON ใน `custom_agent_tools/` อย่างสมบูรณ์
  - **Template Interpolation & Dynamic Formula Evaluation (`ToolExecutor.kt`)**:
    - ใน `executeCustomSkill`: รองรับ Template Interpolation ทั้ง `{{param}}`, `{param}` และ Whole-word regex match `\bparam\b`
    - เพิ่มโหมด `formula`: คำนวณนิพจน์คณิตศาสตร์ (เช่น การคำนวณ Lot Size, Risk Ratio, Pivot Point) ผ่าน `evalMath` โดยอัตโนมัติ คืนผลลัพธ์ตัวเลขพร้อมขั้นตอนคำนวณ
    - นำส่ง Arguments ที่ได้รับทั้งหมดเข้าสู่ Tool Loop เพื่อให้โมเดลประมวลผลต่อได้อย่างแม่นยำ
  - **Persistent Tool Loading (`JarvisOrchestrator.kt`)**:
    - ปรับปรุงการโหลด Custom Tools จาก JSON บนเครื่อง ให้ deserialize `parameters` และ `executionType` กลับมาลงทะเบียนเข้า `ToolRegistry` อย่างสมบูรณ์ ทำให้เครื่องมือที่สร้างไว้คงคุณสมบัติ Function Calling ครบถ้วนแม้เปิด-ปิดแอปใหม่
  - **Unrestricted Tool Schemas (`TradingToolDefinitions.kt`, `SmcToolDefinitions.kt`)**:
    - ปรับ `exchange` ให้เป็น Optional ใน `trading_technical_analysis`, `trading_multi_timeframe`, `trading_combined`
    - ขยาย Timeframe Enum ให้ครอบคลุมทุกความต้องการ: `1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W` รวมถึง MT5 formats (`m1, m5, m15, m30, h1, h4, d1, w1`)
- **Verification**:
  - สร้างชุดทดสอบ `DynamicToolCreationTest.kt`:
    - ทดสอบสร้าง Dynamic Formula Tool (`lot_size_calculator`) รับ 3 พารามิเตอร์ (`balance, risk_pct, sl_pips`) และคำนวณผลลัพธ์ผ่าน `ToolExecutor` ได้ค่า `0.5 Lots` ถูกต้อง 100%
    - ทดสอบสร้าง Prompt Template Tool (`gold_scalp_strategy`) และแทนที่ค่า `{{symbol}}`, `{{timeframe}}` ใน Argument สำเร็จ
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 5s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — ForexFactory Economic Calendar Timezone Accuracy & Remaining Events Prioritization
- **Root Cause Analysis (สาเหตุที่วันและเวลาที่แปลงเป็นเวลาไทยผิดเพี้ยน และ AI ไม่รู้วันที่ปัจจุบัน)**:
  - **Issue 1 (Timezone Offset ผิด 4 ชั่วโมง และวันที่เลื่อนไปวันรุ่งขึ้น)**:
    - ใน `TradingApiService.kt` เดิม มีการดึงข้อมูลจาก `ff_calendar_thisweek.xml` โดยสมมุติว่าเวลาใน feed เป็น ET (New York) แล้วใช้ `ldt.toInstant(TimeZone.of("America/New_York"))`
    - แต่ในความเป็นจริง เวลาใน feed XML ของ ForexFactory ถูกแปลงเป็น **UTC** ไว้แล้ว (หรือใน JSON feed ระบุ ISO-8601 offset เป็น EDT เช่น `2026-09-03T10:00:00-04:00` ซึ่งเท่ากับ `14:00:00 UTC` = `21:00:00 ICT` หรือ 3 ทุ่มตรงในไทย)
    - การนำเวลา UTC ไป parse เป็น `America/New_York` (UTC-4) ทำให้เวลาคลาดเคลื่อนไป **+4 ชั่วโมง** ส่งผลให้ข่าวเวลา 21:00 น. หรือ 20:45 น. เลื่อนกลายเป็น 01:00 น. หรือ 00:45 น. ของวันรุ่งขึ้น
  - **Issue 2 (ข่าว ISM Services PMI และข่าวอื่นๆ ไม่แสดง)**:
    - ใน ForexFactory ข่าว `ISM Services PMI` มีระดับความสำคัญเป็น `Medium` (สีส้ม)
    - แต่โค้ดเดิมเรียงตาม `impact_score` จากมากไปน้อยของทั้งสัปดาห์ แล้วตัดเอาแค่ `limit = 10` ทำให้ข่าว `High` impact จากวันจันทร์-อังคาร-พุธ ยึดโควตาไปทั้งหมด 9-10 ข่าว ส่งผลให้ข่าว `Medium` สำคัญและข่าวช่วงท้ายสัปดาห์ถูกตัดทิ้งทั้งหมด
  - **Issue 3 (AI ไม่รู้วันปัจจุบัน ตอบข่าวที่ผ่านไปแล้วเสมือนเป็นข่าวในอนาคต)**:
    - เมื่อผู้ใช้ถาม "ตัวเลขเศรษฐกิจ ที่เหลือของสัปดาห์นี้" ในวันศุกร์ที่ 4 ก.ย. ตัวเครื่องมือส่งรายการข่าวที่เริ่มตั้งแต่วันอังคารที่ 2 ก.ย. โดยไม่มีการระบุ "เวลาปัจจุบันในไทย" ให้กับ Gemini
    - Gemini ไม่รู้ว่าวันนี้คือวันศุกร์ที่ 4 ก.ย. จึงมองเห็นข่าววันที่ 2 ก.ย. อยู่ด้านบนสุด แล้วนำมาตอบว่า "สิ่งที่ต้องจับตาที่สุดคือ ข่าววันที่ 2 กันยายน" ทั้งๆ ที่ข่าวนั้นผ่านไปแล้ว 2 วัน
- **Comprehensive Solution & Implementation**:
  - **Dual-Source Engine (`TradingApiService.kt`)**:
    - สลับมาใช้ `https://nfs.faireconomy.media/ff_calendar_thisweek.json` เป็นช่องทางหลัก ซึ่งมี ISO-8601 offset ชัดเจน เช่น `2026-09-03T10:00:00-04:00` ทำให้แปลงเป็นเวลาไทย (`Asia/Bangkok`) ได้อย่างแม่นยำ 100%
    - ทำ Fallback ไปยัง `ff_calendar_thisweek.xml` โดย parse ด้วย `TimeZone.UTC` อย่างถูกต้อง
  - **Thai Day-of-Week & Formatted Time**:
    - แสดงวันในสัปดาห์เป็นภาษาไทย (จ., อ., พ., พฤ., ศ., ส., อา.) และระบุเวลาไทยและ ET คู่กัน เช่น `วันพฤหัสบดี พฤ. 03 ก.ย. 21:00 น. (ไทย) | 10:00 ET` ตรงกับหน้าเว็บ ForexFactory เป๊ะ
  - **Smart Filtering & Prioritization**:
    - แบ่งข่าวเป็น `UPCOMING [รอประกาศ]` กับ `PASSED [ประกาศแล้ว]` โดยเทียบกับ `Clock.System.now()`
    - ให้ความสำคัญกับข่าวที่กำลังจะมาถึง (`UPCOMING`) เรียงตามลำดับเวลาที่ใกล้จะเกิดขึ้นที่สุดก่อนเสมอ เพื่อไม่ให้ข่าววันศุกร์ (เช่น Non-Farm Payrolls) ถูกเบียดตกขอบ
    - รองรับพารามิเตอร์ `filter` (`upcoming`, `today`, `all`) และ `currency` (`USD`, `EUR`, `GBP`, ฯลฯ)
  - **Current Time & Strict Chronological AI Prompt (`MarketTechnicalToolHandler.kt`)**:
    - ใส่เวลาปัจจุบันของประเทศไทย (เช่น `วันศุกร์ ที่ 4 ก.ย. เวลา 02:00 น. (ไทย)`) ลงในหัวตารางและใน Prompt ของ Gemini ชัดเจน
    - กำหนดคำสั่งให้ AI วิเคราะห์เฉพาะข่าว `[รอประกาศ]` ที่เหลือของสัปดาห์นี้เป็นหลัก และห้ามพูดถึงข่าว `[ประกาศแล้ว]` เสมือนว่ายังไม่เกิดขึ้นเด็ดขาด
- **Verification**:
  - สร้างชุดทดสอบ `EconomicCalendarTimeTest.kt` ยืนยันการแปลงเวลาของ ISM Services PMI (3 ก.ย. 21:00 น.), NFP (4 ก.ย. 19:30 น.), ISM Mfg PMI (1 ก.ย. 21:00 น.) และการ parse XML แบบ UTC
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 56s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — Live Voice Greeting Stability & Thai Phonetics (แก้พูด 2 รอบ และเสียงเพี้ยน 'เจ้านาว')
- **Root Cause Analysis (สาเหตุที่ AI พูด 2 รอบ และพูดทักทายเสียงเพี้ยน เช่น 'เจ้านาววว')**:
  - **Issue 1 (ครั้งที่ 1 พูด 2 รอบ จาก Echo Loop ของ Android TTS)**:
    - ในรอบแรก Live Gemini ส่งกลับมาเป็น Text ล้วน (ไม่มี Audio chunk) ส่งผลให้ตัวจับความผิดพลาด `onTurnWithoutAudio` เรียกใช้ Android Local TTS พูดแทน (`สวัสดีค่ะเจ้านาย จาวิสพร้อมรับใช้แล้วค่ะ`)
    - ในขณะที่เสียง TTS ดังออกจากลำโพงเครื่อง ไมโครโฟนยังคงเปิดอัดและสตรีม PCM อยู่ตลอดเวลา ทำให้ไมค์ดูดเสียง TTS ของตัวเองส่งกลับเข้าไปยัง Gemini Live
    - Gemini Live ถอดความเสียงลำโพงว่าเป็นเสียงผู้ใช้ (`🎤 User: สวัสดีค่ะเจ้านายจาวิสพร้อมรับใช้แล้วค่ะ`) แล้วตอบกลับมาอีกครั้ง (`🤖 JARVIS: สวัสดีค่ะเจ้านาย มีอะไรให้จาวิสช่วยวันนี้ดีคะ?`) ส่งผลให้ผู้ใช้ได้ยิน AI พูดทักทายซ้อนกัน 2 รอบ
  - **Issue 2 (ครั้งที่ 3 เสียงเพี้ยนเป็น 'เจ้านาววว' และหลุดเป็น Text-only)**:
    - ข้อความที่ส่งกระตุ้นตอน READY (`pendingGreetingOnReady`) เดิมส่งเป็น meta-prompt ภาษาอังกฤษปนไทย: `"[SYSTEM] Live session เพิ่งพร้อมใช้งาน โปรดพูดทักผู้ใช้สั้นๆ 1 ประโยคเท่านั้น (เช่น 'สวัสดีครับ พร้อมคุยแล้วครับ' หรือทักตามบุคลิกของคุณ) ไม่ต้องทำงานอื่นต่อ"` ผ่านช่องทาง `realtimeInput` (ซึ่งโมเดลถือว่าเป็น User input turn ไม่ใช่ System channel)
    - การมีแท็ก `[SYSTEM]`, คำภาษาอังกฤษ `Live session`, วงเล็บ, เครื่องหมายคำพูด, และข้อความตัวอย่างที่ขัดกับเพศของตนเอง (มี 'ครับ' แต่ persona คือ 'ค่ะ') ทำให้ Cross-attention ของโมเดลเสียง Native Audio ขาด Acoustic Context ของภาษาไทย
    - ตัวถอดรหัสเสียง (Neural Vocoder) จึงสับสนและสังเคราะห์เสียงสระ/ตัวสะกดแม่เกย (ย) ผิดเพี้ยนเป็นสระกึ่งพยัญชนะ [aʊ] / [w] ("เจ้านาย" $\to$ "เจ้านาววว")
    - ในขณะที่บทสนทนาโต้ตอบปกติ ผู้ใช้พูดเสียงภาษาไทยจริงเข้าไมค์ ทำให้ Audio Encoder ล็อก formant และสำเนียงไทยแท้ได้แม่นยำ เสียงจึงชัดเจนเสมอ
- **Comprehensive Solution & Implementation**:
  - **Mute Mic During TTS Playback (`VoiceController.kt`)**:
    - ผูก `_isMuted.value = true` ทันทีก่อนที่ `voiceManager.speak` จะเล่นเสียง และปลดเป็น `false` ใน callback `onDone`/`onError`
    - ป้องกันไม่ให้ไมค์อัดเสียงลำโพงของเครื่องตัวเอง ตัดวงจร Echo Feedback Loop ทำให้ AI ไม่พูดซ้ำ 2 รอบอีกต่อไป
  - **Conversational Pure-Thai Greeting Trigger (`VoiceController.kt` & `JarvisViewModel.kt`)**:
    - ยกเลิกข้อความ meta-instruction ที่มี `[SYSTEM]` และคำภาษาอังกฤษทั้งหมด
    - เปลี่ยนข้อความกระตุ้นทักทายเมื่อ READY เป็นภาษาไทยสนทนาธรรมชาติ: `"สวัสดี$agentName พร้อมคุยไหม"` (เช่น `"สวัสดีจาวิส พร้อมคุยไหม"`) และข้อความเปลี่ยนเสียงเป็น `"เปลี่ยนมาใช้เสียง $newVoice แล้ว ลองทักทายสั้นๆ ด้วยเสียงใหม่นี้"`
    - โมเดลจะตอบรับกลับมาอย่างเป็นธรรมชาติในฐานะคู่สนทนาภาษาไทย ไม่หลุดไปเป็น Text-only และสร้าง Native Audio เสมอ
  - **Thai Phonetics & Articulation Guardrails (`JarvisPersona.kt`)**:
    - เพิ่มกฎเข้มงวดใน `LIVE_RULES`: กำหนดให้ออกเสียงภาษาไทยสำเนียงไทยแท้ ออกเสียงตัวสะกดแม่เกย (คำว่า "เจ้านาย") ให้กระชับชัดเจน ห้ามลากเสียงหรือเพี้ยนเป็น "เจ้านาว"
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 1m 8s** (ผ่าน 100% ทุกชุดทดสอบ)

## 2026-09-04 — Dynamic Indicator Overlays (EMA, SMA, BB, DC, Any Period) & Chart Controller Flexibility
- **Root Cause Analysis (สาเหตุที่ EMA8 วาดไม่ได้ แต่ EMA14, EMA50 วาดได้)**:
  - **Tool Parameter Schema (`ToolRegistry.kt`)**: พารามิเตอร์ `overlay` ใน `chart_dashboard_control` กำหนด `enum = listOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")` ทำให้ LLM ถูกจำกัดการเรียกเฉพาะค่าตายตัว
  - **Controller Validation (`ChartController.kt`)**: มีการ hardcode `val validOverlayNames = setOf("ema14", "ema20", "ema50", "ema60", "ema200", "bb", "smc", "donchian", "signals")` ใน 5 จุด (ทั้ง `open`, `set_overlay`, `toggleChartOverlay`, และการโหลดจาก DB) เมื่อผู้ใช้หรือ AI ส่ง `overlays=ema8` ตัวควบคุมจึงตัดทิ้งทั้งหมดเหลือเป็น `overlays=""`
  - **Screen Serialization (`TradingChartScreen.kt`, `MessageBubble.kt`)**: ใน `LaunchedEffect` มีการ serialize `overlaysJson` โดยระบุเฉพาะ key แบบ hardcode (`put("ema14", ...)` ฯลฯ) ไม่ได้นำ dynamic overlays ใน Set ส่งต่อเข้า WebView
  - **Engine Indicator Resolver (`dashboard_engine.js`)**: กำหนด `OVERLAY_DEFS` แบบ static dictionary และใน `init()` ตรวจสอบเฉพาะ key เดิมที่มีอยู่ใน `state.overlays` ส่งผลให้ indicator คาบอื่นๆ ไม่ถูกสร้างและไม่ถูกคำนวณ
- **Comprehensive Solution & Implementation**:
  - **`ChartController.kt`**:
    - เพิ่ม `isValidOverlay(name: String)`: รองรับ Regex สำหรับ EMA ทุกคาบ (`^ema\d+$`), SMA/MA ทุกคาบ (`^(sma|ma)\d+$`), WMA/HMA (`^(wma|hma)\d+$`), Donchian (`^dc\d+$`), และ Bollinger Bands (`^bb...$`) รวมถึง standard overlays (`smc`, `signals`, `supertrend`, `vwap`)
    - ใช้ `isValidOverlay` ในทุกฟังก์ชันการตรวจสอบและการบันทึก
    - ปรับ `normalizeChartInterval` ให้เรียกใช้ `TaIndicators.normalizeTimeframe` เพื่อให้รับ format `m1`, `m5`, `m15`, `h1`, `h4`, `d1`, `w1` ได้อย่างสมบูรณ์
  - **`ToolRegistry.kt` & `JarvisPersona.kt`**:
    - ปลดล็อก `enum` ใน `overlay`/`overlays` และอัปเดตคำอธิบายพารามิเตอร์และ System Prompt ให้รองรับ EMA และ SMA ทุกคาบตามที่ผู้ใช้สั่ง (เช่น `ema8`, `ema9`, `ema21`, `ema89`, `sma50`, `sma200`)
  - **`TradingChartScreen.kt` & `MessageBubble.kt`**:
    - ปรับ `overlaysJson` ให้ serialize ทุกตัวเลือกที่อยู่ใน `overlays` ส่งต่อให้ JavaScript Engine แบบ Dynamic
    - เพิ่มการแสดง Chip บนแถบ Toolbar กราฟสำหรับ Custom Overlay ที่เปิดใช้งานอยู่ (เช่น ผู้ใช้สั่ง EMA8 จะมี Chip `EMA8` สี Cyan ปรากฏบนจอทันที)
  - **`dashboard_engine.js` (ทั้ง androidMain และ commonMain)**:
    - เพิ่ม `STATIC_OVERLAY_DEFS` พร้อมสีคลาสสิกสำหรับ Fast/Slow EMA
    - เพิ่มฟังก์ชัน `pickColorForPeriod(period, isSma)` ให้สร้างสีแบบ HSL ที่สวยงามและไม่ซ้ำกันสำหรับคาบใดๆ
    - เพิ่ม `getOverlayDef(key)` แยกแยะประเภท indicator และคาบเวลาอัตโนมัติ
    - ปรับปรุง `addOverlaySeries`, `removeOverlaySeries`, `applyAllData`, `updateLegend`, `init`, และ `setOverlay` ให้สร้าง Series และคำนวณค่า EMA/SMA/BB/Donchian จากแท่งเทียนได้ทุกคาบเวลา
- **Verification**:
  - สร้างชุดทดสอบ `ChartDynamicIndicatorTest.kt` ทดสอบความถูกต้องของ `isValidOverlay` กับ EMA/SMA/BB/DC หลากหลายคาบ และทดสอบ Timeframe Normalization
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL in 15s** (ผ่าน 100% ครบทุกชุดทดสอบ)

## 2026-09-04 — Trading Tool Execution Backend Modularization & Architecture Decoupling
- **Decoupled 2,060-line God-Class (`TradingToolExecutionBackend.kt`)**:
  - **`TradingToolExecutionBackend.kt`**: ลดขนาดลงจาก 2,060 บรรทัด เหลือเพียง ~80 บรรทัด ทำหน้าที่เป็น Coordinator ประสานงานผ่าน `TradingToolRouter.domainOf(toolName)` ส่งต่อให้แต่ละ Domain Handler โดยตรง
  - **`Mt5ToolHandler.kt`**: แยกส่วนการทำงาน MT5 Broker Operations & Market Intelligence (~1,120 บรรทัด) เช่น `trading_mt5_order`, `trading_mt5_close_position`, `trading_mt5_candles`, `trading_mt5_analyze`, `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_institutional_flow`
  - **`MarketTechnicalToolHandler.kt`**: แยกส่วนการทำงาน Market, Technical Analysis, Scanning & Sentiment (~460 บรรทัด) เช่น `trading_price`, `trading_market_snapshot`, `trading_technical_analysis`, `trading_multi_timeframe`, `trading_sentiment`, `trading_news`, `trading_macro_calendar`
  - **`ResearchToolHandler.kt`**: แยกส่วนการทำงาน Research, Backtesting Delegation, Strategy Signals & Analytics (~550 บรรทัด) เช่น `trading_strategy_signal`, `trading_signal_stats`, `trading_position_sizing`, `trading_correlation_matrix`, `trading_economic_data` (FRED), `trading_crypto_overview`, `trading_deep_analysis_suite`, `trading_fundamental_analysis`
  - **Zero Regression**: รักษา Backward Compatibility ของ Public/Internal API เดิม 100%
- **Version Bump**:
  - อัปเดต `versionCode = 2`, `versionName = "1.1.0"` ใน `composeApp/build.gradle.kts`
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ทุกชุดทดสอบ

## 2026-09-04 — Trading Tools, Indicator Calculation Accuracy & Universal Timeframe (m1, m5, m15, h1, h4, D1, W1)
- **Root Cause Analysis (สาเหตุที่อินดิเคเตอร์แสดงค่าผิด และ Timeframe ตกหล่น)**:
  - **Issue 1 (Timeframe Fallback to 1h)**: ในหลายโมดูล (`IndicatorAlertProvider`, `TradingApiService`, `SmcApiService`, `TradingToolExecutionBackend`, `mt5_bridge.py`) มีการใช้ `when(interval.lowercase())` ตรวจสอบเฉพาะรูปแบบ TradingView เช่น `"1m"`, `"15m"`, `"1d"`, `"1w"` โดยไม่มีการรองรับรูปแบบมาตรฐาน MT5 (`"m1"`, `"m5"`, `"m15"`, `"h1"`, `"h4"`, `"d1"`, `"w1"`) ส่งผลให้ทุกครั้งที่ผู้ใช้หรือ AI ระบุ TF รูปแบบ MT5 ระบบจะตกลงที่บล็อก `else -> "1h"` ทันที ทำให้ค่าอินดิเคเตอร์ทั้งหมดถูกคำนวณจากแท่งเทียน 1 ชั่วโมงเสมอ!
  - **Issue 2 (Candidate Candle Threshold)**: ใน `IndicatorAlertProvider` เดิมกำหนดเงื่อนไข `candles.size < 220` ส่งผลให้ Timeframe ระดับสูงอย่าง D1 (วัน) และ W1 (สัปดาห์) ซึ่ง TradingView ส่งกลับมาประมาณ 100-180 แท่ง ล้มเหลวและไม่สามารถคำนวณอินดิเคเตอร์ได้เลย
  - **Issue 3 (ATR & Stochastic Calculation)**: `SmcApiService.calcATR` เดิมใช้ Simple Average (SMA ของ TR) แทนที่จะเป็น Wilder's Smoothing RMA ตามมาตรฐาน TradingView/MT5 และ Stochastic เดิมคำนวณแบบ Unsmoothed Fast %K แทน Slow %K(14, 3, 3)
  - **Issue 4 (MT5 Python Bridge)**: `mt5_bridge.py` เดิมแปลง alias เฉพาะ key ที่ขึ้นต้นด้วย `"m"` หรือ `"h"` ทำให้ `"1d"` และ `"1w"` ไม่ถูกแปลงลงใน `_TIMEFRAME_MAP` และโยน error `unsupported timeframe`
- **Comprehensive Solution & Implementation**:
  - **Unified `TaIndicators`**:
    - เพิ่มระบบแปลง Timeframe สากล: `normalizeTimeframe()` (แปลงทั้ง MT5/TradingView formats เข้าสู่มาตรฐาน `"1m"`, `"5m"`, `"15m"`, `"1h"`, `"4h"`, `"1D"`, `"1W"`), `toMt5Timeframe()`, และ `toTvResolution()`
    - เพิ่มและปรับปรุงคลังฟังก์ชันคำนวณ Indicator สากล (pure Kotlin KMP): `rsi` (Wilder's), `macd` (EMA Fast/Slow + EMA-9 Signal + Hist), `stochastic` (Slow %K & Slow %D 14, 3, 3), `atr` (Wilder's RMA), `bollingerBands` (basis, upper, lower, width, %b), `cci` (20), `adx` (Wilder's RMA +DI, -DI), `supertrend` (ATR-based bands)
  - **IndicatorAlertProvider**:
    - แก้ไข `splitSymbolAndTf` ให้เรียกใช้ `TaIndicators.normalizeTimeframe`
    - ลดเงื่อนไข Candle count สำหรับ TF D1/W1 ลงเหลือ 35 แท่ง เพื่อให้คำนวณได้ทันทีแม้ประวัติสัปดาห์จะไม่ถึง 220 สัปดาห์
    - ผูกค่า Supertrend และ Slow Stochastic(14,3,3) เข้าในผลลัพธ์การวิเคราะห์
  - **Trading Tool Definitions & Execution Backend**:
    - อัปเดต enum และ description ของ `trading_technical_analysis`, `trading_mt5_broker_ta`, `trading_mt5_market_scanner`, `trading_mt5_correlation_radar`, `trading_mt5_institutional_flow`, และ `trading_signal_alert` ให้รองรับ TF ครบทั้ง: `m1, m5, m15, h1, h4, D1, W1` (และ aliases `1m, 5m, 15m, 1h, 4h, 1D, 1W`)
    - อัปเดต `fillTaFromLocal` และ `executeTechnicalAnalysis` ให้ normalize timeframe ก่อนส่งไปยัง TradingView Scanner API และ Local Indicator Engine
  - **SmcApiService & MT5 Python Bridge**:
    - แก้ไข `tvResolution` ใน `SmcApiService` ให้รองรับ `"m1", "m5", "m15", "h1", "h4", "d1", "w1"`
    - เพิ่ม `"D1" to "1d"` ลงใน MTF Liquidity matrix
    - แก้ไข `mt5_bridge.py` ให้ alias `"1d"`, `"1w"`, `"d"`, `"w"` เข้าสู่ `TIMEFRAME_D1` และ `TIMEFRAME_W1`
- **Verification**:
    - สร้างชุดทดสอบ `TradingIndicatorTimeframeTest.kt` ทดสอบความถูกต้องของ Timeframe Normalization ทุกตัว และทดสอบ Indicator Math 8 ชนิด
    - รัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 100% ครบทุกชุดทดสอบ

## 2026-09-04 (ก่อนหน้า) — Gemini Provider & Live Model Hardening & Dynamic Listing
- **Fix Live Model Filtering (SettingsController.kt)**:
  - แก้ไขจุดบกพร่องสำคัญใน `SettingsController.getLiveCapableModels()` ที่เดิมฟิลเตอร์ด้วย `it.supportsVision` ทำให้โมเดลแชททั่วไป (`gemini-2.5-pro`, `gemini-1.5-pro`, `gemini-2.5-flash`) หลุดเข้าไปอยู่ใน Dropdown ของ **Live Model** และเมื่อผู้ใช้เลือกจะเชื่อมต่อ WebSocket ล้มเหลวทันที
  - เปลี่ยนมาฟิลเตอร์ด้วย `it.supportsLive` อย่างถูกต้อง พร้อมปรับ heuristic fallback และ fallback list ให้คืนค่าโมเดล Live ที่แท้จริง (`gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.0-flash-exp`)
- **Future-Proof Model Capabilities (ModelConfig.kt)**:
  - ปรับปรุง `supportsNativeTools()` ให้รองรับโมเดล Gemini ยุคใหม่ทุกรุ่นโดยอัตโนมัติ (1.5, 2.x, 3.x+) โดยไม่จำเป็นต้องฮาร์ดโค้ดเลขเวอร์ชันในอนาคต (ยกเว้นโมเดลที่ไม่ใช่ text/multimodal เช่น embeddings/imagen)
  - อัปเดต `isLiveModel()` ให้รองรับคีย์เวิร์ด `native-audio` และ `realtime` เพิ่มเติม
- **Synchronize Live Models Across Services**:
  - อัปเดต `GeminiLlmProvider.kt` เพิ่ม `gemini-2.5-flash-native-audio-preview-12-2025` และ `gemini-2.0-flash-exp` เข้าใน `knownPreviews` เพื่อให้ UI แสดงรายการโมเดล Live ครบถ้วนแม้ Google API จะซ่อน unlisted preview models
  - ซิงค์ `liveModelChain` ใน `LiveGeminiService.kt` และ `JarvisOrchestrator.kt` ให้มี `gemini-2.0-flash-exp` ในสาย fallback เมื่อโควต้าเต็ม
- **Register Gemini in LlmProviderRegistry**:
  - เติมการทำงานใน `LlmProviderRegistry.registerGemini()` และเพิ่มเคส `"gemini"` ใน `createTempProvider()` ให้สมบูรณ์ ป้องกันกรณีค้นหา provider ไม่พบบน standalone registry
- **Verification**:
  - รัน `:composeApp:testDebugUnitTest` ผ่านฉลุย 100% (24 test suites)

## 2026-09-03 — Mobile Modularization, Backtest Handler, Test Suites & Project Review
- **Modularize Trading Terminal Screen (Mobile App)**:
  - แยก `TradingTerminalScreen.kt` จาก monolithic 2,269 บรรทัด ให้เหลือเพียง 474 บรรทัด (ลดลง 79%!) โดยแยก component ย่อยออกเป็น:
    - `TerminalOverviewTab.kt`: Performance stats, Win Rate, Daily/Weekly PnL hero cards, Risk metrics, Quick switches
    - `TerminalTradeTab.kt`: Order entry card, Quick symbol chips, Volume stepper, Dynamic order placement (BUY/SELL), Open positions card
    - `TerminalHistoryTab.kt`: Trade closed deals, PnL badge, Historical trade timeline, Period filter
    - `TerminalEventsTab.kt`: Live AI Decision feed, Execution events timeline
    - `TerminalSettingsTab.kt`: Risk gate parameters, Auto trading switches, Max daily loss, Trailing stops
    - `TerminalConnectionTab.kt`: Bridge WebSocket & HTTP endpoint, Client ping, Account status
    - `TerminalEditPositionDialog.kt`: Dedicated modal dialog สำหรับปรับ SL/TP ตำแหน่งเปิด
    - `TerminalFormatting.kt`: Single source of truth สำหรับ UI Theme colors, Money/Price formatters, Period filter models, Shared mini-widgets (`StatMini`, `MiniLabel`, `EmptyState`, `JarvisTextField`, `SoftButton`)
- **Extract Backtest & Evolution Engine (Mobile App)**:
  - แยกโค้ดส่วน Backtest execution, Parameter Optimization (V1/V2), Genetic Evolve, Mix Signal Engine และ Dataset fingerprinting (~950 บรรทัด) ออกจาก `TradingToolExecutionBackend.kt` เข้าสู่ `BacktestToolHandler.kt`
  - ลดขนาด `TradingToolExecutionBackend.kt` จาก 3,008 บรรทัด ลงเหลือ 2,060 บรรทัด โดยคง Public/Internal API เดิมไว้อย่างสมบูรณ์
- **Add Mobile Automated Test Suites**:
  - เพิ่ม `AutomationEvaluatorTest.kt`: ทดสอบ Condition parsing (GT, LT, GTE, LTE, EQ), formatted price stripping, currency signs
  - เพิ่ม `IntentClassifierTest.kt`: ทดสอบ Natural language classification สำหรับคำสั่งเทรด, ข้อมูลตลาด, AI chat
  - เพิ่ม `SignalAlertProviderTest.kt`: ทดสอบ `signalKindOf` normalization (ป้องกัน regression ตัวเลขหลุดจากกลยุทธ์ 52H/3BR), ทดสอบ `computeTpSl` logical risk/reward levels, และทดสอบ `TradingSignalMarketDataRouter` source switching ระหว่าง Demo และ Live
  - ขยาย `TradingToolDomainRoutingTest.kt`: ครอบคลุม 100% ของ domain tool routing
  - ผลการรัน `:composeApp:testDebugUnitTest`: **BUILD SUCCESSFUL** ผ่าน 24 test suites รวดเร็วภายใน 8 วินาที
- **Project Hygiene & Server Verification**:
  - ลบไฟล์ orphan/ขยะที่ตกค้าง: `composeApp/0)`, `composeApp/0.0`, `stash_smc.diff`, `README.head.tmp`, `GeminiService.HEAD.kt`, `composeApp/p4_build*.log`
  - ย้าย patch scripts เก่าเข้า `scripts/patches/` (`clean_vm.py`, `fix_*.py`, `commit_smc.kt`, `stash_smc.kt`)
  - ตรวจสอบโฟลเดอร์ `strategies/` (61 ไฟล์ QuantPedia) และอัปเดต `.gitignore`
  - Rebuild `better-sqlite3` รองรับ Node.js v24 บน `mt5-core-server` และทดสอบ `npm run analyze` สำเร็จ 100% (158 trades, Net Profit $104.26, 15,796 decision cycles)
  - ตรวจสอบ TypeScript compilation ของ server: `npm run build` (`tsc`) ผ่าน 0 errors
  - ตรวจสอบความพร้อมของ MT5 Python Bridge: ยืนยันการทำงานของ `math.isclose()` ใน `modify_position`, volume clamping ใน `close_position`, และ `snapshots_bulk` endpoint
  - อัปเดตสถานะใน `.obsidian-wiki/09_Roadmap/04_Pro_AI_Trader_Roadmap.md`

## 2026-08-27 — TradingView Strategy Lab
- เพิ่ม `tools/tradingview_strategy_lab.py` เป็น external research harness สำหรับดึง historical OHLCV จาก TradingView หรืออ่าน CSV export แล้วรัน MOM/TR/REV/DC/52H/E/UT/3BR แยกกัน
- แบ่ง Train 60% / Validation 20% / Holdout OOS 20%; รายงาน PF, expectancy R, win rate, DD, Sharpe-like score, long/short asymmetry และ parameter-neighborhood stability
- ใช้ closed-bar signal → next-bar-open entry เพื่อลด look-ahead และบันทึก dataset/source/cost assumptions ใน `run_metadata.json`
- เพิ่ม `.obsidian-wiki/07_Trading_Intelligence/Strategy_Lab_TradingView.md`; ผลเป็น research-only และยังไม่ promote parameter เข้า production

## 2026-08-26 (ล่าสุด) — Strategy Optimization V2
- เพิ่ม `StrategyOptimizationV2`: optimize EntryParams + strategy-native TpSlParams ร่วมกัน แทนการหา SL/TP ที่ดีบน entry แบบคงที่
- แบ่งข้อมูลเป็น Train 60% / Validation 20% / Holdout OOS 20%; candidate ต้องผ่าน evidence gate ทุกช่วงก่อนเป็น candidate
- เพิ่ม `Parameter Neighborhood Stability` เพื่อกัน parameter cliff และให้ความสำคัญกับ robust region มากกว่า single best point
- เปลี่ยน `trading_backtest_optimize` ให้ใช้ V2 เป็น discovery path; ไม่เขียน production params และ promotion ยังเป็นหน้าที่ของ Evolution/Risk Gate
- รองรับ strategy-specific grid สำหรับ MOM/TR/REV/DC/52H/E/UT และ 3BR ใช้ pattern entry โดยไม่มี entry grid
- เพิ่ม `composeApp/.../automation/backtest/StrategyOptimizationV2.kt`

## 2026-08-26 (ก่อนหน้า) — Live Alert Scheduler
- จาก logcat พบ 15m/30m signal พร้อมกันทำให้ `liveVoiceMutex` ตัวเดิมมีการรอคิว ~19.7s และออกแบบ queue/fallback ซับซ้อนเกินไป
- เพิ่ม `Live Alert Scheduler` กลาง: serialize เฉพาะ audio output, เก็บ request เป็น priority queue และให้ M1/M5 signal แซงงาน TF ใหญ่/งานทั่วไปที่รออยู่
- ตัด queue timeout 90s ออกจาก Live session path; `speakAlertNow()` ไม่รอ mutex อีกต่อไป จึงไม่เกิด `APP_QUEUE_BUSY` จากการแข่งขันของ alert ภายใน service
- เพิ่ม diagnostic `Live Scheduler ENQUEUE/DISPATCH` เพื่อวัด queue depth และลำดับ dispatch จริง
- คง no-overlap audio และ Live chain/fallback semantics เดิม; Signal detection/Risk/notification ไม่ถูกเปลี่ยน

## 2026-08-26 (ก่อนหน้า) — Live Signal Alert: short-form + serialized voice queue
- จาก logcat 11:40 พบ 5m Live ใช้ generation ~33s, cap 300 chars แล้ว close ช้า; ระหว่างนั้น 1m signal เข้าคิวและเกิด `APP_QUEUE_BUSY` ก่อน fallback TTS ทำให้ delivery path ซับซ้อนเกินจำเป็น
- ลด Signal Alert response cap **300 → 180 chars** และ one-shot Live ceiling **35 → 22s**; prompt บังคับ **2-3 ประโยค / 35-55 คำ / ~15s**
- เปลี่ยน Live voice queue ของ Signal Alert เป็น **serialized 90s ทุก timeframe** ไม่แยก 1m/5m กับ TF ใหญ่ เพื่อให้ alert ใหม่รอ session ก่อนหน้าจบแทนการ fallback เร็วเกินไป
- ลด transcript chunk logging เป็น progress checkpoints เพื่อให้ `logcat.txt` อ่านง่ายขึ้น โดยยังเก็บ transcript เต็มสำหรับ diagnostics/pushToChat
- คง timeout cause precedence และ no-overlap TTS safety; เป้าหมายคือ Live session เดียวต่อครั้ง, ไม่พูดซ้อน, และ fallback เฉพาะเมื่อ Live chain ล้มจริง

## 2026-08-26 (บ่าย) — Live Signal Alert: graceful completion + timing diagnostics
- จาก logcat 11:54 พบว่า Signal Alert ถูก cap ที่ 180 chars จริง แต่ audio ยาว ~17s และ session ยังไม่มี `turnComplete` ทำให้ total alert latency ~31.5s; queue serialization ทำงานแล้วและไม่มี `APP_QUEUE_BUSY`
- ลด short-form cap **180 → 120 chars** และ prompt เป็น 1–2 ประโยค / 20–35 คำ โดยให้ Signal/Strategy + Entry/SL/TP มี priority สูงสุด เพื่อลดระยะเวลาเสียงจริง
- แก้ timing log ให้แยก `WS_CONNECTED`, `READY total/afterWs`, `REQUEST_SENT total/afterReady` ไม่ใช้ตัวเลข `after connect` ที่อ้างอิงผิดจุด
- เปลี่ยน log `Live OK` เป็น `AUDIO_DELIVERED` เพื่อแยกการส่งเสียงสำเร็จออกจาก `RESULT cause` เช่น `AUDIO_RESPONSE_LENGTH_CAP`
- คง serialized Live voice queue, signal dedup/cache และ timeout cause precedence เดิม
- ปรับ Signal Alert diagnostics ให้ใช้ event format เดียว `SIGNAL_EVENT` สำหรับ FIRE/RESET/SUPPRESS ลดข้อความซ้ำและทำให้ AI อ่าน state transition ได้ทันที โดยไม่เปลี่ยน execution semantics

## 2026-08-26 (บ่าย) — Live Signal Alert: response cap + timeout classification
- จาก logcat 10:26 และ 10:46: 2.5 Native ได้ audio จริง แต่ไม่ส่ง `turnComplete`; watchdog ปิดที่ ~29–29s แล้ว result เดิมถูกจัดเป็น `AUDIO_SUCCESS` ทั้งที่สาเหตุจริงคือ `AUDIO_STREAM_IDLE_TIMEOUT` และมี audio buffer ค้าง ทำให้ generation รวม ~39–43s
- ลด hard session ceiling 90s → **35s** และ audio-idle watchdog 12s → **10s**
- ลด prompt Live Signal Alert เป็น **2–3 ประโยค / 40–60 คำ / ~240 chars** และเพิ่ม circuit breaker ที่ 300 chars เมื่อมี audio แล้ว เพื่อหยุด runaway generation
- แก้ cause precedence ให้ timeout/error มาก่อน `AUDIO_SUCCESS` ทำให้ diagnostics ตรงกับเหตุการณ์จริง ขณะที่ session ที่มี audio แล้วไม่ถูก fallback ไปพูดซ้ำ
- compile verification ทำต่อหลังแพตช์นี้

## 2026-08-26 (สาย) — ปรับ Compact Signal Polling Log + ลดความยาว Live Signal Alert
- ปรับ `JarvisAutomationService` ไม่ให้พิมพ์ `Signal snapshot cache HIT/MISS` ราย symbol ทุก 30s; รวมเป็น `SIGNAL_CACHE` summary ต่อ cycle เพื่อให้ AI วิเคราะห์ log ได้เร็วและลด noise
- เพิ่ม dedup ของ signal action log ตาม `symbol + condition.field + signal_id + action` เพื่อไม่แสดง `SUPPRESS_ALREADY_TRIGGERED` ซ้ำทุก polling cycle; state transition สำคัญยังถูกเก็บ
- คง logic cache/dedup เดิม ไม่ลดความปลอดภัยของ signal execution
- Live Signal Alert prompt อยู่ที่ 2-3 ประโยค / 40-60 คำ / ~240 chars พร้อม circuit breaker 300 chars

## 2026-08-26 (สาย) — ลดความยาว Live Signal Alert + แก้ READY greeting ให้ตรง Persona
- จาก logcat 06:30:43: 2.5 Native ได้ audio สำเร็จ แต่ response ใช้เวลารวม ~43s และ audio stream ยาว 1.68MB ก่อน idle watchdog ปิด session; สาเหตุหลักคือ prompt 100-150 คำยังยาวเกินสำหรับ alert ที่ต้องการความเร็ว
- ปรับ Live Signal Alert prompt เป็น **3-4 ประโยค / 60-90 คำ / เป้าหมาย ~20s** โดยยังคงข้อมูล Signal/Strategy, Entry, SL/TP และ risk/context 1 ประเด็น
- แก้ UI ข้อความ `LIVE READY` จาก `ครับ` เป็น `ค่ะ` ให้สอดคล้องกับ JARVIS female persona
- compile verification จะทำหลังแพตช์ชุดนี้

## 2026-08-26 (เช้า) — สลับ Alert Voice chain: 2.5 Native ขึ้นก่อน 3.1
- จาก logcat 06:24–10:03: **Live voice 14/14 = 100%** (แพตช์ก่อนหน้าสมบูรณ์: key rotation ทำงาน, TF-aware queue รับ alert ซ้อน 07:30 รอ 43.3s แล้วได้ Live, watchdog 14s ชุบ 2.5 ที่ firstAudio 11–12s)
- พบว่า 3.1 TRANSCRIPT_ONLY 12/14 (85%) — ทุก alert เสีย ~5–7s กับ 3.1 สองรอบก่อนจบที่ 2.5 เสมอ ส่วน 2.5 Native สำเร็จ 11/11 เมื่อได้ลอง → สลับ `liveVoiceChain()` ให้ 2.5 Native พูดก่อน 3.1 คงเป็น fallback (ตัด latency ~6s/alert)
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-26 (ดึก) — Live Voice: หมุน API key ต่อ attempt + ปรับ prompt ความยาวให้ตรงกัน
- จาก logcat 01:50–02:04: พบเคส "3.1 มั่ว 2 รอบติด + 2.5 ตาย" บน key เดียวกันหมด → เพิ่ม **key rotation ต่อ attempt** ใน chain เสียงแจ้งเตือน (อ่าน pool จาก setting `gemini_api_keys` + primary `api_key`) และ log ท้าย key 4 ตัวใน WS connected เพื่อ debug ต่อได้
- แก้ **prompt ขัดกันเอง**: system instruction เคยสั่ง ≤4 ประโยค/80 คำ แต่ payload สั่ง 8–12 ประโยค/180–300 คำ → ปรับทั้งสองฝั่งให้ตรงกันที่ 4–6 ประโยค / 100–150 คำ (ฟังจบใน ~30s) ลด generation 28–63s ลง
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-26 — แก้เสียงซ้อน + alert TF ใหญ่ตก TTS (JarvisAutomationService.kt)
- ตรวจ logcat 22:29–00:04 หลังแพตช์ก่อนหน้า: Live voice 6/8, chain พัง 0 ครั้ง — ที่เหลือคือ APP_QUEUE_BUSY 2 ครั้ง (22:31, 23:30) ซึ่งทำให้ **Live (15m) + Android TTS (30m) พูดทับกันพร้อมกัน 2 เสียง** (คนละสัญญาณแต่ BUY เหมือนกัน เลยฟังเหมือนซ้ำ)
- **Fix 4 — TF-aware queue timeout**: parse TF จาก symbol ("XAUUSD@15m"); TF ≥ 15m รอคิว Live นานสุด 95s (สัญญาณไม่สตเพิ่ล), TF เล็กคง 4s — alert 30m ทั้ง 2 เคสจะได้ Live แทน TTS
- **Fix 5 — กันเสียงซ้อนถาวร**: เคส queue busy ที่ต้อง fallback TTS จะรอ lock (cap 95s) ให้ session ที่พูดอยู่จบก่อน ค่อยพูด TTS — ไม่มี 2 เสียงทับกันอีก
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-25 (บ่าย) — แก้ Live Alert Voice จาก logcat จริง (JarvisAutomationService.kt)
- วิเคราะห์ logcat 17:24–18:05: Live voice สำเร็จแค่ 4/8 alert — 3.1 ตอบ TRANSCRIPT_ONLY 5 ครั้ง, 2.5 ติด FIRST_AUDIO_TIMEOUT_NO_OUTPUT 3 ครั้ง, queue busy 1 ครั้ง (fallback Android TTS ครบทุกเคส ไม่มี alert เงียบ)
- **Fix 1 — first-output watchdog 8s → 14s**: พบว่า 2.5 Native ที่สุขภาพดีส่ง first audio ช้า 7.5–8.8s หลัง READY (17:34=7.5s, 17:47=7.8s รอดหวุดหวิด) ส่วนเคสที่ตาย ตายที่ 8.0–8.3s พอดีเป๊ะ → watchdog เดิมฆ่า session ที่กำลังจะตอบ
- **Fix 2 — retry โมเดลเดิมอีก 1 ครั้งเมื่อ TRANSCRIPT_ONLY**: 3.1 ตอบ text ล้วนแบบมั่วราย turn (config เดียวกันเป๊ะ สลับสำเร็จ/ไม่สำเร็จ) → retry session ใหม่ก่อนค่อย fallback โมเดลถัดไป (TRANSCRIPT_ONLY จบไว ~3s จึง retry ได้ถูก)
- **Fix 3 — log setupJson จริงทุก session** (ตัดย่อ 600 chars) เก็บหลักฐาน config ที่ส่งไป ป้องกัน debug มืด
- ยืนยันกับ official Live API reference (ai.google.dev/api/live): realtimeInput.text / clientContent turnComplete ถูกต้องตาม spec ทั้งคู่ → สาเหตุหลักคือ preview model flaky ราย turn + watchdog แคบเกิน ไม่ใช่ wire format
- compile ผ่าน: `:composeApp:compileDebugKotlinAndroid` ✅

## 2026-08-25 — Live Voice timeout/queue diagnostics
- ปรับ Live Voice ให้แยก queue / setup / first-output / session timeout ชัดเจน และ log latency ของ request→output พร้อม cause classification
- เพิ่ม RESULT log: AUDIO_SUCCESS, TRANSCRIPT_ONLY, API_ERROR, FIRST_AUDIO_TIMEOUT_* และสถานะ fallback
- Queue acquisition log แสดงแม้รอ 0ms เพื่อยืนยันว่า queue ไม่ใช่คอขวด
## 2026-09-05 — ปรับ Timeframe คาดการณ์เริ่มต้นเป็น 15m (m15) และรองรับหลาย Timeframe (m5–h4 / all)
- **Default Timeframe Policy (15m)**: ปรับแก้เกณฑ์การสร้าง Alert คาดการณ์ล่วงหน้า (`signal_anticipation` / `trading_signal_anticipation`) หากผู้ใช้ไม่ได้ระบุ Timeframe ให้เริ่มต้นที่ **`15m`** (m15) เสมอ (แทนที่ 1h เดิม) ซึ่งสอดคล้องกับหลักการวิเคราะห์ SMC และ Intra-day Confluence
- **รองรับช่วง Timeframe กว้าง (m5–h4)**: อนุญาตให้คาดการณ์ได้ตั้งแต่ `5m`, `15m`, `30m`, `1h`, ถึง `4h` สอดคล้องกับระบบ Market Structure Ingestion ที่ดึงแท่งเทียน 5 TF (1m, 5m, 15m, 1h, 4h) อยู่ตลอดเวลา
- **รองรับ Multi-TF & All**: ผู้ใช้สามารถสั่งให้ AI คาดการณ์หลาย TF เช่น `15m,1h` หรือสั่ง *"ทุก TF"* / `"all"` ซึ่งระบบจะขยายเป็น 5 Timeframe หลัก และสร้าง Alert Job แยกอิสระให้ทุก TF อัตโนมัติ
- **อัปเดตไฟล์ระบบ**:
  - `JarvisOrchestrator.kt`: แยก logic `isAnticipationAlert` กำหนด default `15m` และขยาย `"all"` เป็น `[5m, 15m, 30m, 1h, 4h]`
  - `ToolExecutor.kt`: ฟังก์ชัน `executeSignalAnticipation` ใช้ default `15m`
  - `TradingToolDefinitions.kt`: อัปเดต Parameter Documentation
  - `JarvisPersona.kt`: อัปเดต AI Prompt Rule 9 และ 10 กำหนดชัดเจนว่า default คือ 15m และช่วง m5-h4
  - `SignalAnticipationTest.kt`: เพิ่ม Unit Test `testAnticipationDefaultTimeframeIs15mAndSupportsMultiTf` ทดสอบครอบคลุมทั้ง default 15m, explicit 5m, และ all (ผ่าน 100%)
