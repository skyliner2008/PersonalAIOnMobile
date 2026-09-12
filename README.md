# 🤖 PersonalAIBot — JARVIS for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-orange.svg?style=flat)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Gemini](https://img.shields.io/badge/AI-Gemini%203%20Series-green.svg?style=flat&logo=google-gemini)](https://ai.google.dev/)
[![Release APK](https://img.shields.io/github/v/release/skyliner2008/PersonalAIOnMobile?color=brightgreen&label=Download%20Release%20APK&logo=android)](https://github.com/skyliner2008/PersonalAIOnMobile/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

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
- **🔔 Alert System V2 & Signal Anticipation** — แจ้งเตือนสัญญาณเทรดล่วงหน้า (Anticipation) และสัญญาณยืนยัน (Confirmed) ด้วยการ์ด 3D พร้อมระบบเสียงพูดแจ้งเตือน (Voice Alert Delivery)
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

### 🧠 3. Advanced 6-Layer Memory Engine
- **Layer 1: Core Memory** — จำข้อมูลตัวตนผู้ใช้/AI (identity จัดการผ่าน tool/Settings เท่านั้น กัน heuristic ทับ)
- **Layer 2: Working Memory** — บันทึกประวัติการคุยลง SQLite ทันที (Context Tracking)
- **Layer 3: Archival Memory** — Semantic Search / Vector Embeddings (Local ONNX หรือ Gemini Cloud — auto-backfill)
- **Layer 4: GraphRAG Knowledge Graph** — โครงข่ายความสัมพันธ์แนวคิด + retrieval จริงผ่าน `recall_memory`
- **Layer 5: Memory Consolidation** — "Sleep Cycle" สรุปและย้ายความจำระยะสั้นไประยะยาว (auto-trigger เมื่อแชทสะสม 200 ข้อความ)
- **Layer 6: LLM-Wiki (Obsidian)** — ระบบ "สมองส่วนนอก" ที่ AI และมนุษย์จัดการร่วมกันผ่าน Markdown

### 📊 4. Trading Intelligence บนมือถือ (TV-Powered & Unified SMC)
- **Dual-Stage Signal Engine (Anticipation → Confirmed)** — ตรวจจับการตั้งเค้าของราคาล่วงหน้า (Anticipation) ด้วย 10 ปัจจัยเทคนิคอล และส่งสัญญาณยืนยัน (Confirmed) เมื่อแท่งเทียนปิด
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

### 📱 DEVICE CONTROL TOOLS (18 tools)
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
- `device_read_screen`: อ่านข้อมูลหน้าจอปัจจุบันผ่าน Accessibility Service
- `device_tap`: แตะปุ่มหรือพิกัดบนหน้าจอ
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
- `trading_signal_anticipation`: ⚡ คาดการณ์สัญญาณเทรดล่วงหน้าด้วย 10 ปัจจัยคอนฟลูเอนซ์ (Keyzone Proximity, Wick Sweep, RSI Extreme, EMA Near-Cross ฯลฯ)
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
- `.obsidian-wiki/04_Tasks/` — บันทึก Changelog และแผนงานรายวัน
- `.obsidian-wiki/07_Trading_Intelligence/` — รายละเอียดระบบการเทรด mt5-core-server และ Unified SMC
