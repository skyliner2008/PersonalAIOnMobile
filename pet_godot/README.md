# Pet Robot Lab (Godot 4.7)

A design table for the 3D pet robot as a game character: bouncy floating animation, live screen
eyes, an emotion-coloured suit, armour-style gear (hand tools, jetpack, cape), headgear and
backgrounds. It is also **the app's only avatar engine**: Avatar mode in the JARVIS app runs this
project (the old Canvas / Rive / pre-rendered 3D engines are gone). Design here, then pack it into the
app with `tools/export_to_app.ps1`.

## In the app (Avatar mode)

```bat
powershell -ExecutionPolicy Bypass -File pet_godot\tools\export_to_app.ps1     &:: engine library + exported project + faces
gradlew :composeApp:installDebug
```

- **How it runs:** Godot 4.7's remote engine. `GodotService` runs in the app's own `:avatar` process and
  draws into a `SurfaceControlViewHost`; `RemoteGodotFragment` shows it over the app's Compose screen
  (`avatar/AvatarStageHost.kt`). Closing Avatar mode ends that process, so every visit starts a fresh
  engine (Godot can't be restarted inside one process). Needs Android 11+ and an arm64 phone.
- **Talking to the app:** the app's plugin `JarvisAvatar` (`avatar/AvatarGodotPlugin.kt`, in the engine's
  process) passes JSON over a Messenger link to `AvatarLinkService` in the app. Here the other end is
  `scripts/app_bridge.gd`; the commands and events are in `scenes/SCHEMA.md` ("App bridge").
- **Who does what:** the app's pet brain (`PetModeController`) still owns the mood — touches, the camera's
  face tracking, shakes, the AI's faces. The app maps its 58 emotions + props + backdrops to this robot
  (`avatar/AvatarLook.kt`); the robot moves its body at once (bounces, wobbles, leans with the phone's tilt),
  plays skits and shows tool results on the info scenes.
- **Faces for places without the engine** (the floating overlay bubble, the Drive screen, Android 10):
  `--sprites=<dir>` renders 27 webp faces of this robot (every emotion + blink + 3 talking mouths) on a
  transparent background; the export script writes them to `composeApp/.../composeResources/files/robot/`.
- Desktop check of app mode: `--app-sim=<commands.json>` plays app commands and prints the events.

## Run

- `PetLab.bat` runs the lab, `PetLabEditor.bat` opens it in the Godot editor
  (Godot 4.7.2 portable at `C:\Godot`).
- Move the mouse: the pet looks at it. Click: it gets startled. The tabs at the bottom:
  **อารมณ์** (emotion + animation + suit colour), **หัว** (headgear), **ตัว** (back gear),
  **มือ** (tools per hand), **ฉาก** (12 backgrounds), **แอ็กชัน** (use left/right hand, fly, a cheeky
  move, suit colour follows emotion / random), **ละคร** (the skits below, or "▶ เล่นทุกฉาก"),
  **ข้อมูล** (each tool's result on its scene: live from the same source as the app's tool, or saved samples).

## On a phone (standalone APK, not the app)

```bat
C:\Godot\Godot_v4.7.2-stable_win64_console.exe --headless --path . --export-debug Android build\PetLab.apk
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r build\PetLab.apk
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe logcat -s godot     &:: PERF fps / draw calls / VRAM every 2 s
```

- **Build setup:**
  - `export_presets.cfg` builds an arm64 debug APK: `com.skyliner2008.petlab`, portrait, immersive, internet on.
  - The `*.json` scenes and samples are included; `tools/` is left out.
  - The Android export templates (4.7.2, Android files only) are in `%APPDATA%\Godot\export_templates`.
  - Godot generated the debug keystore itself.
- **Phone-only settings (`.mobile` overrides):**
  - 60 fps cap, because a pet doesn't need 120 Hz and it saves battery;
  - 3D renders at 75 % (the S22 Ultra is 600 dpi);
  - at start, `main.gd` → `_warm_up()` builds every effect and panel once, tiny and behind the pet, so the first use doesn't hitch.
- **Tall phones:** the portrait camera keeps the width (`KEEP_WIDTH`, 23.14° horizontal = 40° vertical at 9:16), so a 19.5:9 screen shows more above and below and never cuts the sides.
- **Tested on the SM-S908E** (Snapdragon 8 Gen 1, Adreno 730, Vulkan Mobile, 1440×3088):

  | | Before | After |
  |---|---|---|
  | Frame rate, all 21 scenes | lab 120 fps; dips to 17–48 fps when effects first appear | 55–60 fps, capped |
  | VRAM | ≈ 420 MB | ≈ 350 MB |

  - The APK is 43.6 MB.
  - Thai and emoji text renders with Android's own fonts.
  - The live data buttons work over the phone's connection.
  - `adb am start --esa command_line_params` does not reach the game; drive it with `adb shell input tap`.

## Info scenes: every tool's result gets its own scene

When the app's AI calls a tool, the app passes the result it got back to
`director.play_tool(tool, result)`. [`scenes/tool_scenes.json`](scenes/tool_scenes.json) names the
scene for each tool, so a tool always looks the same. The numbers always come from the tool. The AI
only adds an optional short markdown note (`advice` / `summary`). A tool without a scene of its own
shows its text on the markdown card.

| Tool | Scene | What it shows |
|---|---|---|
| `device_weather` | `weather_report` | weather card: place, temperature, sky, high/low, feels-like, humidity, wind, rain chance, and a 3D icon (sun / moon / clouds / rain / thunder / snow / fog). The backdrop follows day or night, rain or snow falls over the stage, and the pet reacts (hot → sunglasses, rain likely → a small rain cloud + "พกร่ม") |
| `trading_fundamental_analysis` | `stock_balance` | balance sheet as two 3D columns (assets = liabilities + equity), a 3D donut of the capital structure, 3D bars of revenue / net income / free cash flow (TTM), and a markdown summary. The pet wears a fedora |
| `trading_price` | `price_report` | a large price that counts up, the day's move with ▲/▼ in green or red, the previous close, a 52-week range marker. The pet cheers a jump ≥ 3 % and sweats a drop ≤ −3 % |
| `trading_technical_analysis` | `technical_report` | a 3D rating dial (strong sell … strong buy), an RSI dial with 30/70 zones, and tiles for MACD / ADX / ATR / RSI / BB / close. The pet wears headphones |
| `trading_deep_analysis_suite` | `deep_analysis_report` | the "5 dimensions": a 0–100 score dial coloured by the LSD trend's side, tiles for LSD trend / orderflow / momentum / Fibo hot zones, then Fibonacci strength bars (gold = hot). Stars for a strong score |
| `trading_multi_timeframe` | `mtf_report` | a bar per timeframe (−1 sell … +1 buy), then signal + RSI tiles. The pet hops when most timeframes buy |
| `trading_sentiment` | `sentiment_report` | a 0–100 sentiment dial, the 4 pillars as bars, retail vs top-trader long % and taker buy/sell; the contrarian alert in a bubble |
| `trading_smc_analysis` / `_structure` | `smc_report` | where the price sits in its structure range (discount / premium zones, the range's low and high at the ends), trend + BOS/CHoCH + levels + FVG/OB tiles, the nearest liquidity. A sweat drop when the trend and the zone disagree |
| `trading_market_snapshot` | `market_snapshot_report` | S&P500 / NASDAQ / DOW / VIX / oil tiles with today's %, then % bars |
| `trading_top_gainers` / `_losers`, scans (`overbought` / `oversold` / `volume_breakout` / `bollinger`) | `market_movers` | 3D bars of the top coins by % change (RSI for the RSI scans); a reaction per list: confetti, a sweat drop, "ระวังย่อ", "ลุ้นเด้ง", bolts, a squash |
| `trading_fear_greed` | `fear_greed_report` | a 0–100 dial in five zones plus the last 7 days as bars. The pet shivers at extreme fear and puts on sunglasses at extreme greed |
| `trading_crypto_overview` | `crypto_overview` | tiles for market cap, 24 h change, volume and coins, then a BTC/ETH/others dominance donut. The pet wears the astronaut helmet |
| `device_battery_status` | `battery_report` | a battery dial and the battery gauge over the head. Charging → bolts, low → sleepy |
| text tools (`trading_news`, `trading_macro_calendar`, `trading_strategy_signal`, `search_web`, `calculate`, …) | `answer_card` | a markdown card (headings, bold, bullets, quote) that the pet holds up; long cards grow upwards so they never cover its head |

**The jobs the user waits for** (Avatar mode's boss/worker flow: a question handed to the assistant is told
later): `scripts/task_hud.gd` shows each one as a pill in the lower right corner — running, done and waiting
to be told, being told — labelled with the user's own question (app command `tasks`, `scenes/SCHEMA.md`).

**Between answers** the app plays looping scenes (`avatar/AvatarActivity.kt` in the app): `listening`
while the user talks (sound waves from the head, a meter that follows the user's voice), `pondering` until
the AI answers (lights circling over the head), `working` while a tool runs (a progress card with the
tool's task — "กำลังวิเคราะห์ 5 มิติ… BTCUSDT · 1h" — from `task` in `tool_scenes.json`).

Panels (`scripts/holo_panel.gd`): `weather`, `price`, `balance`, `gauge`, `pie`, `bars`, `kpi`, `text` (markdown), `progress`.
Filling, conditions, the data each tool sends and panel beats are in `scenes/SCHEMA.md` ("Info scenes").

```bat
C:\Godot\Godot_v4.7.2-stable_win64_console.exe --headless --path . --script res://tools/save_samples.gd   &:: refresh data_samples\ (live)
C:\Godot\Godot_v4.7.2-stable_win64_console.exe --path . -- --tool=trading_price                          &:: a tool's saved result on its scene
C:\Godot\Godot_v4.7.2-stable_win64_console.exe --path . -- --tool=trading_price --data=my_result.json
powershell -File tools\tool_frames.ps1 -Data <dir of <tool>.json> -Out <dir>                       &:: every result → contact sheets
powershell -File tools\scene_frames.ps1 -Out <dir> -Scenes listening,pondering,working -StopAt 5   &:: looping scenes
```

Real results for every tool come from the app: `RUN_LIVE_TESTS=1 SCENE_SAMPLES_DIR=<dir> gradlew
:composeApp:testDebugUnitTest --tests "*AvatarSceneDataLiveDump*"` calls the tools for real and saves what
Avatar mode would send the robot.

## Scenes (skits)

Short meme-style skits (8-10 s) that use the whole stage: 360° turns and flips, running round the
back, coming up to the lens and backing away. Each one is a JSON file in `scenes/`; the format and
every beat verb are in [`scenes/SCHEMA.md`](scenes/SCHEMA.md), written so that an AI brain can pick
a scene (`director.list()` / `play(id)`) or write a new one (`play_json(text)`).

| id | Caption (setup) | Payoff |
|---|---|---|
| `idle_play` | เมื่อไม่มีใครเล่นด้วย… | propeller cap, runs round the stage, winks at the lens |
| `bored` | วันที่ 47 ของการรอคำสั่ง… | floats upside down, bonks the glass (> <), "มีงานให้ทำมั้ย~" |
| `angry` | เมื่อมีคนจิ้มหน้าจอรัวๆ | red suit, 💢, cannon: two rockets, the third at the screen (ตูม!!), sulks |
| `love` | เมื่อคุณกลับมาหา | heart eyes, comes close, throws a heart at the screen, cat ears |
| `thinking` | กำลังคิดว่าจะกินอะไรดี | paces (side/back views), ???, idea bulb: "กินทุกอย่างเลย!" |
| `curious` | นั่นอะไรน่ะ?? | face on the glass, pokes the lens, flees, peeks from the edge |
| `sleepy` | ประชุมบ่ายโมง | z Z Z, snot bubble, nods off, "ไม่ได้หลับนะ!", sleeps on its side |
| `sad` | เมื่อคุณพิมพ์ว่า "ไปก่อนนะ" | rain cloud, T T tears, "…แต่พรุ่งนี้มาใหม่นะ" → "สัญญานะ!" |
| `low_battery` | แบตเหลือ 1% | blinking battery, sinks, plugs in (⚡), 100%, zooms round |
| `party` | เมื่อเพลงโปรดขึ้น ♪ | party hat, dance, confetti, sunglasses "เท่ปะล่ะ", moonwalk |
| `hero` | ภารกิจ: ปกป้องโลก (อีกแล้ว) | cape + jetpack, flies round the city, superhero landing, shield |
| `dizzy` | หลังนั่งรถไฟเหาะ | 1080° spin, @ @ eyes, circling stars, bumps the edge, "ไหวๆ!" |

Check and record them:

```bat
powershell -File tools\scene_frames.ps1 -Out C:\tmp\scenes            &:: a frame a second + a contact sheet per scene
powershell -File tools\record_scenes.ps1 -Out C:\tmp\clips -Reel      &:: <id>.mp4 per scene (576x1024) + all_scenes.mp4
```

`record_scenes.ps1` records with Godot's movie writer (`--write-movie`, fixed 30 fps) and converts
the AVIs to H.264 MP4 with Blender (`tools/avi_to_mp4.py`, Blender has FFmpeg built in). The
recording window has to fit on the screen (see Rules).

## Where things come from

| Path | Made by |
|---|---|
| `assets/robot.glb` | `rive_avatar/blender/export_game.py` (model `robot_build.py`, pivots + 9 clips `robot_game.py`) |
| `assets/accessories/*.glb` | same export: 7 wearables (`robot_game.py`) + 18 gear items (`robot_gear.py`), one file each |
| `assets/robot_meta.json` | clips, item -> pivot / slot / app `PropType` / kind / muzzle / nozzles, pivot rest positions, screen size |
| `scripts/pet_character.gd` | the character and its API (below) |
| `scripts/effects.gd` | rockets + explosions, laser, flashlight beam, energy shield, jet flames |
| `scripts/scene_player.gd` | the director: plays `scenes/*.json`, meme caption + speech bubble, camera shake / zoom, brings the pet home afterwards |
| `scripts/emotes.gd` | comic layer: floating symbols (♥ ? ! Z ♪ ✦ ★ … ⚡ 💢 sweat), idea bulb, confetti, snot bubble, big heart, dizzy stars, rain cloud, battery gauge |
| `scripts/care_fx.gd` | the robot's own care (the app's PetSelfCare): a snack eaten in three bites (donut, burger, pizza, cake, ice cream, popcorn), soap foam and bubbles, a shower, a shake-dry splash, a beach ball for keepy-uppy |
| `scripts/eyelids.gd` + `shaders/eyelids.gdshader` | the screen's eyelids as the robot falls asleep in the app (heavy blinks, shut, opening slowly) |
| `scripts/holo_panel.gd` | hologram info panels (weather, balance, pie, bars, kpi, markdown text), number formats, derived data (`derive`) |
| `scripts/info_sources.gd` | lab only: live tool results from the same sources as the app's tools (Open-Meteo, TradingView scanner, alternative.me, CoinGecko), in the same shape |
| `data_samples/<tool>.json` | a saved result per tool (what the app would pass to `play_tool`), `answer_card.json`; refreshed by `tools/save_samples.gd` |
| `scenes/tool_scenes.json` | which scene shows which app tool (`play_tool`) |
| `scripts/main.gd` | the lab: stage, camera, lights, tabbed UI, scripted screenshots, scene playlist; app mode and the sprite render |
| `scripts/app_bridge.gd` | app mode (inside JARVIS): the app's commands (look, voice, reactions, tools, scenes), touch zones, the phone's tilt |
| `tools/export_to_app.ps1` | packs the robot into the app: engine AAR, exported project (`godot-assets/`), sprite faces |
| `shaders/robot_eyes.gdshader` | live eyes on the `PR_Screen` mesh (11 expressions incl. dizzy @ @, cry T T, squint > <; blink, gaze, talking mouth, UI corners) |
| `shaders/emote_mark.gdshader` | camera-facing marks: anger 💢, sweat drop, battery |
| `shaders/holo_card.gdshader` | hologram glass card: rounded rim, scanlines, sheen, bottom-up materialise, optional SubViewport content |
| `shaders/backgrounds.gdshader` | 12 backdrops: studio, night, sunny, cyber, sakura, space, underwater, city, sunset, snow, candy, hangar |
| `shaders/cape_wave.gdshader`, `light_cone.gdshader`, `energy_shield.gdshader`, `blob_shadow.gdshader` | cape wind, flashlight cone, shield, contact shadow |
| `tools/inspect.gd` | headless check of where the body parts and every item end up |
| `tools/capture.ps1` | batch screenshots with a timeout per run (a run can hang on exit) |
| `tools/scene_frames.ps1` | plays scenes, saves a frame every N seconds, builds a contact sheet per scene |
| `tools/sleep_frames.gd` | frames of falling asleep and waking up the app's way (drowsy, eyelids, shut, `wake_up`): `--script res://tools/sleep_frames.gd -- --out=<dir>` |
| `tools/scene_test.gd` | headless director test (`--headless --script res://tools/scene_test.gd`): catalogue, cutting a scene off, an AI-written scene with an unknown verb, the pet back home afterwards |
| `tools/record_scenes.ps1`, `tools/avi_to_mp4.py` | scene videos (movie writer → Blender → MP4) |
| `tools/save_samples.gd` | headless: refreshes `data_samples/` with live results of every mapped tool |
| `tools/info_live_test.gd` | headless check that the live weather and stock sources still answer |

## Character API (`pet_character.gd`)

`set_emotion(name)` · `wear(name)` / `take_off(name)` / `toggle(name)` · `use_tool("L"|"R")` ·
`toggle_fly()` · `set_suit_color(color)` + `suit_follows_emotion` · `look(Vector2)` · `poke()` ·
`fidget(name := "")` (hop / spin / tilt / wiggle / peek / cheeky, and nod — asked for by name only) · `bump(zone)` (a touch lands; the emotion
stays) · `speaking` + `voice_level` (the mouth follows a voice, any emotion) · `sway` (lean with the phone's tilt)

For scenes: `set_eyes(name, dur)` (`EYES`, incl. dizzy / cry / squint) · `hop` · `lean` · `tilt_head` ·
`set_fly` · `body_roll` / `body_pitch` (flips about the body centre) · `head_top()` · `muzzle_position(side)` ·
`present(side, on)` (arm raised towards a panel, `PRESENT_POSE`) ·
`scene_active` (stops idle fidgets and gaze wander while a scene runs)

- Clips play through the AnimationPlayer in manual mode; every frame a procedural layer goes on
  top: squash & stretch from the float speed, a springy head and antennas, whole-body hop / spin /
  lean, jetpack lift, and the arm raised to aim while a tool is on (`TOOL_AIM`).
- Emotions pick the clip, the eye expression and colour, and the suit colour (`EMOTIONS`).
- Idle fidgets start on their own every 5-9 s when nothing else is going on.

## Rebuild the assets

```bat
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b --factory-startup --python rive_avatar/blender/export_game.py -- pet_godot/assets
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b --factory-startup --python rive_avatar/blender/check_arms.py
C:\Godot\Godot_v4.7.2-stable_win64_console.exe --headless --path pet_godot --import
```

Screenshots (after `--`): `--shot=out.png --state=<emotion>,<item+item>,<bg>[,<action+action>] --wait=1.5 --hide-ui [--turn=180]`,
actions `fire_L`, `fire_R`, `fly`, `fidget_<name>`, `poke`. Or `tools\capture.ps1 -Out dir -Shots @(@{name=..; state=..; wait=..})`.
Scenes: `--scene=<id> [--data=<file.json>] [--quit-at-end] [--frames=<dir> --every=1] [--stop-at=<s>]` (info scenes
take their tool's `data_samples/` file when `--data` is not given; `--stop-at` ends a looping scene); tools: `--tool=<name> [--data=<result.json>]`.

## Rules

- Items follow a pivot node (`PR_HeadPivot` / `PR_BodyPivot` / `PR_ArmL|R`); the game places each one
  at `-pivots_rest[pivot]` under that pivot. One item per slot (hat, face, ears, back, cape, hand_l, hand_r).
- A portrait phone screen is narrow: tools aim towards the viewer, rockets arc up over the pet and
  beams aim at a point in front of it (`BEAM_TARGET`) -- anything shot sideways leaves the screen at once.
  The energy shield is held across the front (arm aimed in, shield along the arm, not out of the forearm).
- Scene positions: see the stage table in `scenes/SCHEMA.md` (close-up `[0,-0.15,2.6]`, face on the
  glass `[0,-0.4,3.6..4.0]`); flying already adds ~0.8 height, so flight paths use a low `y`.
- `--write-movie` ignores `--resolution` while the project has a window size override; the record
  script writes a temporary `override.cfg` instead (never leave one in the project). The window must
  fit on the screen: at 720x1280 on a 1080p screen Windows shrank it to 720x1061, the canvas became
  868 wide, and bubbles were cut off in the video. The script now records borderless 576x1024.
- Info panels: text is at least 0.07 m tall at `top` (≈ 22 px on a 720-wide screen); the card is
  94 % opaque so bright backdrops (the moon) don't show through the numbers.
- Text on panels uses SystemFont `Leelawadee UI` → `Noto Sans Thai` → `sans-serif`. Windows has the first
  and Android the last two. Emoji and symbols fall back to `Segoe UI Emoji` / the system emoji font.
- Arm aim is "out to the side, then forward": `Qx(forward) * Qz(out)` (the other order barely moves
  a forward-pointing arm out).
- `check_arms.py` must stay "clear" after changing clips or the arm shape (arms never go into the head).
- The Mobile renderer is used on purpose (what Android will run). Avoid `sin()`-based hashes in
  shaders (they turn into blocks at mobile precision).
- `application/config/quit_on_go_back` is off: inside the app, Android back must close Avatar mode, not
  the engine. The boot splash is the robot's face on black (`assets/boot_splash.png`, made by
  `--sprites`), so the load looks like the robot waking up. Gravity sensing is on (the tilt lean).
- `Input.get_gravity()` is Android's reading negated: upright it is (0, -9.8, 0), and turned clockwise
  by θ it is 9.8·(sin θ, -cos θ). The lean that keeps the body upright is `atan2(x, -y)`; the sign was
  backwards once, so `scene_test.gd` checks it.
- Frame pacing (Swappy) is off: it reads its settings from an Activity, and the engine inside the app
  runs in a service with none (it crashed with SIGABRT).
- A Label that was never laid out wraps at width 0; set its size before `reset_size()` of its container
  (the first speech bubble outside a scene came out screen-high).
