# Pet scenes: format and beat vocabulary

A scene is a short skit (8-10 s) for the pet robot, written as data. `scripts/scene_player.gd` (the
"director") plays it. The format is plain JSON so that an AI brain can do either of these:

- **pick** a ready-made scene: `director.list()` returns `{id, title, mood, tags, description, duration, needs}`
  for every file in this folder, then `director.play(id)` plays one;
- **write** a new scene: generate JSON in this format and call `director.play_json(text)`.

**Info scenes** answer a question with real data, such as "what's the weather today?" or "how does
MSFT's balance sheet look?". They show the data on 3D hologram panels (weather card, price card,
balance sheet, dials, pie, bars, numbers, markdown text). See [Info scenes](#info-scenes-data-and-panels) below.

The app plays them with **`director.play_tool(tool_name, result)`**:

1. The app's AI calls a tool, for example `trading_price`.
2. The app passes on exactly what that tool returned: a map, a list of rows, or text.
3. `tool_scenes.json` names the scene for that tool, so every tool always gets the same presentation.
4. A tool without a scene of its own gets the markdown text card (`"*"`).

| Tool | Scene | Shows |
|---|---|---|
| `device_weather` | `weather_report` | weather card with a 3D sky icon, a matching backdrop, rain or snow over the stage |
| `trading_fundamental_analysis` | `stock_balance` | balance-sheet columns → capital-structure donut → revenue/income/FCF bars → summary |
| `trading_price` | `price_report` | big price, day move in green/red, previous close, 52-week range marker |
| `trading_technical_analysis` | `technical_report` | rating dial (strong sell…strong buy) → RSI dial with 30/70 zones → MACD/ADX/ATR/RSI/BB/close tiles |
| `trading_deep_analysis_suite` | `deep_analysis_report` | score dial 0–100 coloured by the LSD trend's side → LSD trend / orderflow / momentum / Fibo hot zone tiles → Fibonacci strength bars (gold = hot) |
| `trading_multi_timeframe` | `mtf_report` | a bar per timeframe (−1 sell … +1 buy) → signal + RSI tiles; a hop when most timeframes buy |
| `trading_sentiment` | `sentiment_report` | sentiment dial 0–100 (five zones) → the 4 pillars as bars → retail / top-trader long % and taker buy/sell, a contrarian warning |
| `trading_smc_analysis` / `trading_smc_structure` | `smc_report` | where the price sits in its structure range (discount ≤ 25 %, premium ≥ 75 %, the range's low/high at the dial's ends) → trend, BOS/CHoCH, premium / equilibrium / discount, FVG / OB → the nearest liquidity above and below |
| `trading_market_snapshot` | `market_snapshot_report` | S&P500 / NASDAQ / DOW / VIX / oil tiles with today's % → % bars |
| `trading_top_gainers` / `trading_top_losers`, the scans `trading_overbought_scan` / `trading_oversold_scan` / `trading_volume_breakout` / `trading_bollinger_scan` | `market_movers` | bars of the top 5 coins by % change (RSI for the RSI scans; one per coin, USDT pairs); a reaction per list (`react`) |
| `trading_fear_greed` | `fear_greed_report` | 0–100 dial with five zones + the last 7 days |
| `trading_crypto_overview` | `crypto_overview` | market cap / 24 h change / volume / coins tiles → BTC/ETH/others dominance donut |
| `device_battery_status` | `battery_report` | battery dial + battery gauge over the head; charging / low / full reactions |
| `trading_news`, `trading_macro_calendar`, `trading_strategy_signal`, `trading_smc_flow` (a short `{title, markdown}` the tool makes for the card), `search_web`, `calculate`, the other analyses, any other | `answer_card` | the tool's text as a markdown card (the app shortens it: `avatar/AvatarCards.kt`) |

A tool that answers with text instead of its data (an empty scan, an error) gets the text card, whatever
its own scene is.

**Between answers** (Avatar mode, the app's `avatar/AvatarActivity.kt`): looping scenes that play until the
app stops them or a tool's scene takes over.

| Scene | When | Shows |
|---|---|---|
| `listening` | the user talks to the AI | leans in, sound waves leave both sides of the head, a voice meter under the pet follows the user's voice (`hear`) |
| `pondering` | the user has finished, the AI hasn't answered yet | looks up from side to side, three lights chase each other round a ring over the head |
| `working` | a tool runs (and until the AI talks about its result) | steps to the presenting spot and holds up a progress card: spinner, the tool's `task` ("กำลังวิเคราะห์ 5 มิติ"), the call's `detail` ("BTCUSDT · 1h"), a scanning light; data `{tool, args}` |

**The robot's own life** (Avatar mode, the app's `pet/PetSelfCare.kt`): when nobody talks to it or touches it.

| Scene | When | Shows |
|---|---|---|
| `eat` | 30 s of quiet, hungry | "หิวจัง…", a snack pops up in front of the face and is eaten in three bites (the mouth snaps, crumbs fly); data `{item}`: `donut` (default) `burger` `pizza` `cake` `ice_cream` `popcorn` |
| `bath` | 30 s of quiet, dirty | a shower head pours water on it, soap foam on the head, bubbles rising, a spin, then it shakes itself dry (splash) and sparkles |
| `play_ball` | 30 s of quiet (or `idle_play`) | a beach ball bounced on the head, the last header goes at the lens: bonk, the screen shakes, a laugh |
| `drowsy` | 60 s of quiet, until it sleeps or wakes (`max_loop` 0) | nodding off: the head drops, the eyes close, it jerks awake and droops again |
| `sleep` | 120 s of quiet (`keep_pose`, `max_loop` 0) | lies down on its side, eyes shut, night, Zzz and a snot bubble — under the app's shut eyelids |
| `wake_up` | woken by the user's voice or a touch (`keep_pose`) | gets up from where it lay, opens its eyes, morning, stretches: "ตื่นแล้วฮับ!" |

When a scene ends, the director brings the pet home: back to the centre, facing the camera, with its
previous outfit, emotion `normal` and a suit colour that follows emotion again. A scene doesn't need
to clean up after itself. A scene with `"keep_pose": true` starts from wherever the last one left the pet
instead (it gets up from where it lay); between the two the app's look is not put back.

## File

The comments below are only notes; real scene files are plain JSON.

```jsonc
{
  "id": "angry",                         // unique, file name = <id>.json
  "title": "โกรธ",                        // shown on the lab's ละคร tab
  "mood": "angry",                       // one word, what the pet feels
  "tags": ["angry", "poked", "tsundere"],// what situations it fits (for choosing)
  "description": "…",                    // one or two sentences in plain words (for choosing)
  "duration": 10.0,                      // seconds; the scene stops here
  "loop": 1.0,                           // optional: at the end, go back to this time (until stopped, at most 120 s)
  "max_loop": 0,                         // optional: seconds a looping scene goes on by itself (0 = until stopped)
  "keep_pose": true,                     // optional: start where the last scene left the pet, not at home
  "beats": [ {"t": 0.0, "do": "caption", "text": "…", "dur": 3.0}, … ]
}
```

Each beat runs once, when the scene clock passes `t` (seconds). Beats with the same `t` start
together. Most beats take `dur` (seconds, default 0.6) and some take `ease`. In a looping scene the beats
from `loop` on run again every round (the intro before it once).

## Stage

Units are metres. `x` is right (+) and left (−), `y` is up, and `z` comes towards the camera (+) or goes
away from it (−). The pet's origin is at its feet. At rest it floats with the head centre about 1.55
above the origin. The camera is at `(0, 1.35, 6.8)` and looks at `(0, 0.95, 0)`. The portrait screen
is narrow (9:16).

| Where | Position | Use |
|---|---|---|
| Home | `[0, 0, 0]` | whole body in view, room above for emotes and bubbles |
| Safe sideways | `x` −0.5 … 0.5 at `z` 0 | whole body stays in view (arms reach about 0.9 to each side) |
| Edge of the screen | `x` ±1.25 … 1.35 | half out of view: peeking, bumping into the edge |
| Close-up | `[0, -0.15, 2.6]` | head and chest fill the screen |
| Face on the glass | `[0, -0.4, 3.6 … 4.0]` | the face fills the screen (knocking, poking the lens) |
| Far away | `z` −1.5 … −3 | small, in the background |
| Behind (orbit) | `orbit` with `center [0, -1]`, `radius` 0.8 … 1.2 | goes round the back and returns |

`y` below 0 sinks the pet (tired, sad); up to about 0.4 lifts it. Flying (`fly on`) already adds about 0.8.

## Beats

### Text

| do | Fields | What it does |
|---|---|---|
| `caption` | `text`, `dur` (3) | meme caption at the top of the screen (big white text, black outline) |
| `say` | `text`, `dur` (1.8) | speech bubble that follows the head |

Keep captions short (up to about 25 Thai characters). A caption at `t` 0 sets up the joke.

### Face and colour

| do | Fields | What it does |
|---|---|---|
| `emotion` | `name` | clip, eyes, eye colour and suit colour together: `normal` `happy` `sad` `wink` `love` `surprised` `sleepy` `angry` `talk` |
| `eyes` | `name`, `dur` (0 = until the next emotion) | eyes only: every emotion's eyes plus `dizzy` (@ @ spinning), `cry` (T T with running tears), `squint` (> <) |
| `suit` | `color` (`"#RRGGBB"` or `"auto"`) | colour of the blue suit parts; `auto` follows the emotion again |
| `look` | `x`, `y` (−1 … 1) | where the eyes (and a little the body) look; 0,0 = at the viewer |
| `head_nod` | `deg`, `dur`, `ease` | tip the face down (+) as it nods off; 0 = level (stays until the next `head_nod`) |
| `blink` | `shut` (0 open … 1 shut; −1 = blinking by itself again), `dur` | hold the eyelids: closing slowly while drowsy, shut asleep |

### Moving

| do | Fields | What it does |
|---|---|---|
| `move` | `to` `[x,y,z]` or `by` `[dx,dy,dz]`, `dur`, `ease`, `face` | glide there. `face: "travel"` turns towards where it's going (side or back view), `face: "camera"` turns to the viewer |
| `orbit` | `center` `[x,z]`, `radius`, `from`/`to` (degrees, 0 = front), `y`, `dur`, `ease`, `face` (`travel` by default) | circle round a point. 0 → 360 goes round the back and returns |
| `turn` | `yaw` (degrees, 0 = facing the camera, 180 = back to the camera) or `by` (relative; 360, 720 … spins), `dur`, `ease` (`back`) | turn about the vertical axis. `yaw` takes the short way |
| `roll` | `deg` or `by`, `dur`, `ease` | tip sideways about the body centre (90 = lying on its side, 180 = upside down, 360 = a cartwheel) |
| `pitch` | `deg` or `by`, `dur`, `ease` | tip forwards (+) or backwards (−) about the body centre (nodding off, a somersault with `by` 360) |
| `lean` | `deg`, `dur` | lean the body sideways (stays until the next `lean`; 0 = upright) |
| `tilt_head` | `deg`, `dur` | tilt the head (stays until the next `tilt_head`) |
| `hop` | `height` (0.25), `dur` | a jump with a squash on take-off and a bounce on landing |
| `squash` | `amount` (−0.2) | a springy squash (−) or stretch (+) |
| `vibrate` | `amount` (0.05), `dur` | shake from side to side (anger, buzzing) |
| `fidget` | `name` | a ready-made cheeky move: `hop` `spin` `tilt` `wiggle` `peek` `cheeky` |

After a `move` or `orbit` with `face: "travel"` the pet turns back to the camera by itself. Add
`"then": "keep"` when another travelling move follows straight away.

`ease`: `linear` `sine` `quad` `cubic` `back` `bounce` `elastic` `expo`, optionally with `_in`,
`_out` or `_in_out` (for example `quad_in` speeds up into a landing). Without a suffix, `back` `bounce`
`elastic` and `expo` ease out and the others ease in and out.

### Gear

| do | Fields | What it does |
|---|---|---|
| `wear` | `item` | put an item on (one per slot; a new one replaces the old) |
| `take_off` | `item` | take it off |
| `tool` | `side` (`L` / `R`) | use the tool in that hand: the cannon fires a rocket, the laser fires a beam, the flashlight and the shield switch on and off |
| `fly` | `on` (true / false) | jetpack flight (puts the jetpack on if needed) |

Items (slot):
- hats: `crown` `party_hat` `wizard_hat` `chef_hat` `propeller_cap` `cat_ears` `halo` `devil_horns` `viking_helmet` `astronaut_helmet` `fedora`;
- face: `sunglasses` `vr_headset`;
- ears: `headphones`;
- back: `backpack` `jetpack`;
- cape: `cape`;
- hands: `cannon_L/R` `laser_L/R` `flashlight_L/R` `shield_L/R`.

### Comic layer and camera

| do | Fields | What it does |
|---|---|---|
| `emote` | `kind`, `count` (3), `spread` (0.45), `rise` (0.7), `dur` (1.3), `size` (1) | floating symbols over the head: `heart` `question` `exclaim` `zzz` `note` `sparkle` `star` `dots` `bolt` `anger` (throbbing 💢) `sweat` |
| `effect` | `name`, `dur`, … | a prop, see below |
| `shake` | `amount` (0.06), `dur` | camera shake (landing, bumping, explosions) |
| `zoom` | `fov` (40 = normal; smaller = closer), `dur`, `ease` | camera zoom |
| `background` | `name` | `studio` `night` `sunny` `cyber` `sakura` `space` `underwater` `city` `sunset` `snow` `candy` `hangar` |

Effects:
- `confetti`: a burst of coloured paper.
- `bulb`: an idea light bulb over the head (`dur`).
- `snot_bubble`: a sleeping bubble that grows with each breath and pops (`dur`).
- `big_heart`: a heart thrown at the viewer that fills the screen.
- `dizzy_stars`: stars circling the head (`dur`).
- `rain_cloud`: a little rain cloud that follows the pet (`dur`).
- `battery`: a battery gauge over the head (`from`, `to` 0 … 1, `dur`, `hold`); below 0.2 it blinks red.
- `rocket_at_viewer`: a rocket from the hand (`side`) straight at the lens, which bursts into a screen flash.
- `flash`: a small explosion in front of the pet.
- `rain` / `snow`: rain or snow over the whole stage (`dur`, `amount`).
- `snack`: a snack in front of the face, eaten in three bites a second apart (1, 2, 3 s after it appears): the mouth opens
  and snaps shut, crumbs fly, it shrinks, then the mouth chews (`kind`: donut / burger / pizza / cake / ice_cream /
  popcorn; anything else is a donut). `scripts/care_fx.gd`
- `bubbles`: soap foam piled on the head and bubbles rising round the body (`dur`).
- `shower`: a chrome shower head over the pet pouring water on it (`dur`).
- `splash`: water flying off the pet as it shakes itself dry.
- `ball`: a beach ball dropped on the head, bounced `bounces` − 1 times (0.85 s apart), the last header at the lens: it
  bonks the glass and drops away (put a `shake` beat there).
- `listen`: sound waves from both sides of the head and a voice meter under the pet, following the user's
  voice (the app's `hear`; without it a talking rhythm). Lasts until the scene ends (`scripts/activity_fx.gd`).
- `think`: three lights chasing each other round a tilted ring over the head. Lasts until the scene ends.

## Info scenes: data and panels

An info scene has `"needs"` (the kind of data it shows) and is played with data, usually through
`director.play_tool(tool, result, extra)`. That call turns a list of rows into `{"items": […]}` and
text into `{"markdown": …}`, adds the tool's `extra` from `tool_scenes.json`, then lays the caller's
`extra` on top (e.g. `{"advice": "…"}` from the AI). `director.play(id, data)` works too, and the data
can also sit in the scene JSON under `"data"`.

The app's trading tools return **text values** such as `"83806.8000"`, `"0.17%"` or `"N/A"`. Formats,
conditions and panels read the number out of such text, and `N/A` counts as missing.

### Filling text from the data

Any string in a beat can use `{path}` or `{path:format}`. The path can go into nested objects and
lists, for example `{daily.0.max}`. Keys that contain dots work as they are: `{MACD.macd:2}`. A
string that is only `"{path}"` keeps the value's own type, so a number stays a number and a list stays
a list (use this for chart values and `"bars": "{movers}"`). A missing value shows as `–`.

| format | Example |
|---|---|
| (none) | `29`, `28.5`, or the text as it came |
| `0` / `int`, `1`, `2` | `{temp:0}` → `29`, `{debtToEquity:2}` → `0.29` |
| `money` | `{totalAssets:money}` → `$758.4B` (the currency symbol comes from `currency`) |
| `price` / `group` | `{price:price}` → `$83,806.80`, `{close:group}` → `83,806.80` |
| `pct` / `pct+` | `{netMarginTtm:pct}` → `40.3%`, `{change_pct:pct+}` → `+0.17%` (two decimals under 1 %) |

### Conditions

A beat with `"if"` runs only when the condition holds. Conditions are plain data and are never run as code.

- a value is set and not empty/zero: `"advice"`; negation: `"!advice"`
- comparisons: `"temp >= 33"`, `"kind == thunder"`, `"situation != nice"`
- a list: `"kind in [rain, drizzle]"`
- combine with ` and ` / ` or ` (`and` binds tighter)

### Fields worked out from the data

The director adds these before the scene starts (`holo_panel.gd` → `derive`):

- **weather** (when the data has `weather_code`):
  - `kind`: `clear` `partly` `cloudy` `fog` `drizzle` `rain` `thunder` `snow`;
  - `desc` (Thai, unless the app sent its own) and `night`;
  - `hot` (≥ 33 °C), `cold` (≤ 18 °C), `rainy`;
  - `situation`: the one thing worth reacting to, first match wins: `thunder` `rain` `snow` `hot` `cold` `rain_likely` (≥ 50 %) `fog` `nice` `cloudy`;
  - `backdrop`: `night` `snow` `sunny` or `studio`.
- **stock** (when the data has `totalAssets`):
  - `cashAndEquivalents` (worked out from `totalDebt − netDebt` if missing);
  - `otherAssets`, `otherLiabilities`;
  - `liabilitiesPct`, `equityPct`, `netCash`, `hasNetCash`;
  - `currencySymbol`.
- **price** (`price` + `change_pct`):
  - `up` and `move` (the day's % as a number);
  - `has_range` (both 52-week values are known).
- **technical** (`recommend_score` or `RSI`):
  - `score` (−1 … 1), `signal_th` (ซื้อแรง … ขายแรง);
  - `rsi_zone`: `oversold` / `neutral` / `overbought`;
  - `macd_bull`, `macd_word`, `trend_strong` (ADX ≥ 25), `trend_word`, `day_up`.
- **fear & greed** (`value` + `classification`):
  - `fg` (number), `fg_th` (Thai);
  - `fg_zone`: `extreme_fear` `fear` `neutral` `greed` `extreme_greed`;
  - `fg_days`: bars of the last days, oldest first.
- **movers** (`items` rows with `change`):
  - `movers`: bars for the top 5 coins, one per coin, USDT pairs first (`bar_field: "RSI"` in the tool's extra: the RSI instead of the %);
  - `best_symbol`, `best_change`, `up`;
  - `mover_tiles` (every row as a kpi tile: price, today's %), `ups`, `downs`, `mostly_up`.
- **deep analysis** (`deep_score`):
  - `deep_word` (อ่อน / ปานกลาง / แข็งแรง), `deep_strong` (≥ 70), `deep_dir` (`up` / `down` / `flat` from the LSD trend), `deep_zones` (the dial's zones in that side's colours);
  - `lsd_th`, `lsd_good`, `flow_th`, `flow_good`, `momentum_th`, `momentum_sub`, `momentum_good` (`good` = true / false / "" for a kpi tile);
  - `fibo_bars`, `hot_count`, `fibo_hot`, `deep_say`.
- **multi-timeframe** (`timeframes`):
  - `tf_bars` (score per timeframe), `tf_tiles` (signal + RSI), `align_dir` (`up` / `down` / `flat`), `align_th`.
- **sentiment** (`sentiment` + `pillars`):
  - `senti_zone` / `senti_word` (like fear & greed), `pillar_bars`, `has_positioning`, `taker_good`, `contrarian_short`.
- **SMC** (`smc_zone`):
  - `pos_gauge` (0 … 100), `pos_text`, `zone_th`, `trend_th`, `trend_good`, `event_th`;
  - `smc_setup`: `long` (bullish + discount), `short` (bearish + premium), `caution` (the two disagree), `wait`; `setup_th`;
  - `fvg_text`, `ob_text`, `eqh_text`, `eql_text`.
- **crypto overview** (`btc_dominance`):
  - `dominance` slices (BTC / ETH / others);
  - `up`: the 24 h change is not negative.
- **battery** (`level`):
  - `low` (< 20), `full` (≥ 95), `level01` (0 … 1);
  - `charging_th`.

### Data the app sends

It sends what the tool returns, with the same names. Each tool's builder in the app already makes this
map before turning it into text for the AI, so the bridge only has to pass that map on.

| needs | Shape (same names as the app's tools) |
|---|---|
| `weather` | `location, temp, feels_like, humidity, precipitation, weather_code (WMO), wind, is_day, max, min, rain_prob` + optional `desc`, `advice` (short markdown from the AI), `source`, `fetched_at` (Open-Meteo, as in `DeviceControlExecutor.executeWeather`) |
| `stock` | `StockFundamentalData` as JSON: `symbol, name, currency, closePrice, changePct, marketCap, peTtm, totalDebt, netDebt, cashAndEquivalents, totalAssets, totalLiabilities, totalEquity, debtToEquity, totalRevenueTtm, netIncomeTtm, freeCashFlowTtm, netMarginTtm, returnOnEquity` + optional `summary` (markdown), `source`, `fetched_at` |
| `price` | `getBestEffortPrice`: `symbol, price, change, change_pct, prev_close, high_52w, low_52w, currency, market_state, direction, source` (text values) |
| `technical` | `getTechnicalAnalysis`: `symbol, close, change, volume, RSI, MACD.macd, MACD.signal, BB.basis, BB.width, ATR, ADX, Recommend.All, buy/sell/neutral_signals, signal, recommend_score` (+ `interval` as extra) |
| `movers` | the rows of `getTopGainers` / `getTopLosers`: `symbol, name, close, change, volume, RSI, …` |
| `fear_greed` | `getFearGreedIndex`: `value, classification, trend` ("71,73,…", newest first) |
| `crypto` | `getCryptoGlobal`: `total_market_cap_usd, total_volume_24h_usd, btc_dominance, eth_dominance, market_cap_change_24h, active_cryptocurrencies, markets` |
| `battery` | `level` (0-100), `charging` (true/false) (`DeviceControlExecutor.executeBatteryStatus`) |
| `deep` | `runLocalDeepSuite`: `symbol, interval, price, deep_score (0-100), lsd_state, lsd_tf, flow_label, flow_delta, flow_cum, poc, momentum (EXPANSION / SQUEEZE / REVERSAL), momentum_up, squeeze, fibo [{label, level, score 1-10, hot}]` |
| `mtf` | `executeMultiTimeframe`: `symbol, alignment, buy_count, sell_count, timeframes [{tf, signal, rsi, score}]` |
| `sentiment` | `executeSentiment`: `symbol, sentiment (0-100), sentiment_label, sentiment_th, pillars [{name, short, weight, score}], retail_long, top_long, taker_ratio, contrarian` |
| `smc` | `SmcToolExecutor.publishSmc`: `symbol, interval, price, smc_trend, smc_zone, smc_event, range_high, range_low, range_pos (% of the range), premium_from, equilibrium, discount_to, bull_ob, bear_ob, bull_fvg, bear_fvg, eqh, eql` |
| `task` | the app's tool call for `working`: `tool, args`; or `task, detail` as they are |
| `answer` | `title, markdown, say`, or the text of any tool |

`data_samples/<tool>.json` holds a saved result for each tool (and `answer_card.json`). To refresh
them from the live sources, run `Godot --headless --script res://tools/save_samples.gd`.

### Panel beats

| do | Fields | What it does |
|---|---|---|
| `panel` | `id`, `type`, `at` or `pos [x,y,z]`, `title`, `subtitle`, `size [w,h]`, `beam` (`L`/`R`), `anchor` (`bottom`: `pos` is the bottom edge, a long text card grows upwards), plus the type's fields | a hologram card materialises (from the bottom up, a projector beam from the hand); a new panel with the same `id` replaces the old one |
| `panel_close` | `id` (or `all`) | the card folds away |
| `present` | `side` (`R`), `on` (true/false) | the pet raises an arm up towards the panel, like showing it off |
| `hold` | | everything is shown: when the app asked for a hold (`play_tool(…, hold)`), the scene's clock stops here until `release()` (at most 60 s) and the rest (the arm going down) plays after; otherwise nothing. Every info scene has one, just before its outro |
| `say` | `at`: `top` / `right` / `left` | with a panel up, the bubble goes beside the head by itself |

`at`: `top` (the main place: the pet steps to `[-0.6, -1.1, -1.0]`, lower left), `upper`, `middle`,
`bottom`, `left`, `right`, `bottom_right` (a small text card next to the pet).

Panel types (every field can be filled from the data):

- `weather`: reads the weather fields itself. Shows the place, a big temperature, `desc`, high/low and feels-like / humidity / wind / rain chances, with a 3D icon for `kind` (sun, moon, clouds, rain, thunder, snow, fog).
- `balance`: reads the stock fields itself. Assets (cash + other) as one 3D column, liabilities (debt + other) + equity as the other, with a legend of amounts and shares.
- `pie`: `slices: [{label, value, color?, text?}]`, `center` (text in the hole), `money` (false for plain numbers). A 3D donut that sweeps in; the biggest slice stands out.
- `bars`: `bars: [{label, value, color?, text?}]`, `money`. 3D bars that grow; negative values hang below the line.
- `kpi`: `items: [{label, value, sub?, good?}]`, `columns`. Tiles of numbers; `good` colours the value green or red.
- `text`: `text` (markdown: `#` headings, `**bold**`, `*italic*`, `-` bullets, `>` quote, `` `code` ``, and +/−% coloured). The card fits its height to the text.
- `gauge`: `value`, `min`, `max`, `zones: [{to, color, label?}]`, `text` (instead of the number), `label` (line under it), `min_text` / `max_text`. A 3D half-dial whose needle springs to the value. Used for the rating, RSI, fear & greed and battery.
- `price`: reads the `trading_price` fields itself. Shows the big price (it counts up), the day's move with ▲/▼ in green/red, the previous close, and a 52-week range bar with a marker when both ends are known.
- `progress`: `task`, `detail`. A job under way: a spinner, the task with "…" growing after it, the detail, a light sweeping a track.

A list field that isn't there (a `"{ref}"` to a missing field) draws an empty chart instead of failing.

## Writing a good skit

1. Open with a caption that sets up the joke: "เมื่อ…", "วันที่ 47 ของ…".
2. Give it a turn: shock, then a twist or payoff (angry, then a rocket at the screen, then sulking).
3. Use the whole stage. Come close to the camera, run round the back, flip, bump into the edge.
4. Use one or two comic symbols at a time, and put a `say` bubble on the punchline.
5. Keep it between 8 and 10 s, with the last second free for the trip home.

## For an AI brain (LLM)

The app sends the model its mood and the situation (for example "the user just came back after two
days" or "the user poked the screen five times"). The model answers in one of two ways:

- `{"play": "<id>"}`: choose from the catalogue (`list()`: `id`, `mood`, `tags`, `description`).
- `{"scene": { … }}`: a new scene in this format. Only the verbs, items, emotions and effects on
  this page are valid, positions stay inside the stage table, and `duration` is at most 10
  (20 for an info scene).

For a question that needs data (weather, a price, a stock's balance sheet, a short explanation), the model
doesn't choose a scene. It calls the tool as usual, and the app then calls
`director.play_tool(tool, result, {"advice": "…"})`. The tool decides the scene. The model only adds the
optional `advice` / `summary` / `say` text. The numbers on the panels always come from the tool,
never from the model.

Unknown verbs are skipped with a warning, so a partly wrong scene still plays.

## App bridge (Avatar mode)

Inside the JARVIS app the engine runs in its own `:avatar` process and the app's Godot plugin
`JarvisAvatar` relays JSON both ways (`scripts/app_bridge.gd`; app side: `avatar/AvatarStage.kt`).
The app's pet brain owns the mood; the robot shows it, moves its body and reports touches.

Commands (the plugin's `command` signal):

| `t` | Fields | What happens |
|---|---|---|
| `state` | `emotion` (9 bodies), `eyes` ("" = the emotion's), `items` (gear, one per slot), `background` ("" = black), `emote` (a symbol that keeps coming: heart, question, exclaim, zzz, note, sparkle, star, dots, bolt, sweat, anger, or dizzy / battery_low / rain_cloud) | the whole look; waits while a scene plays and comes back when it ends |
| `look` | `x`, `y` (-1..1, y up) | gaze (face tracking); after 2.5 s without one the robot looks around by itself |
| `voice` | `speaking`, `level` (0..1) | the mouth moves with the voice, whatever the emotion |
| `react` | `name`: shake / heavy_shake / boat / thump / face_up / loud / poke | a body reaction (wobble, stars, jump, screen rattle); the emotion that follows comes in the next `state` |
| `tool` | `tool`, `result` (map / rows / text), `extra`, `hold` | `play_tool`: the tool's info scene; `hold`: it waits on its last picture (its `hold` beat) while the AI talks about it |
| `release` | | the AI finished talking about the held scene: it plays to its end |
| `scene` | `id`, `data` | a skit or a looping scene (`listening`, `pondering`, `working`); the same `id` again with `data` while it plays gives it the new data (the working card shows the next tool) instead of starting over |
| `hear` | `level` (0..1) | the user's voice while they talk to the AI: the `listen` effect's meter (about 12 a second) |
| `tasks` | `items`: `[{id, tool, args, question, state}]` | the jobs the user still waits to hear about, as pills in the lower right corner (`scripts/task_hud.gd`, at most 3 + "+N งาน"): `running` (handed to the assistant, amber, pulsing) "กำลังทำ", `ready` (done, waiting to be told, green) "เสร็จ รอเล่า", `telling` (cyan, pulsing) "กำลังเล่า"; labelled with the user's question, or the tool's `task` and symbol. The same `id` keeps its pill from running to told; an empty list clears them |
| `stop` | `id` (optional) | ends that scene if it is still the one playing (a tool's scene that took over keeps going); no `id`: whatever plays |
| `say` | `text`, `secs`, `at` | a speech bubble outside scenes |
| `effect` | `name` (+ that effect's fields) | one effect: confetti, flash, big_heart, battery, bulb … |
| `fidget` | `name`: hop / spin / tilt / wiggle / peek / cheeky / nod | a quick move (the AI's face gestures; `nod` = two nods, the AI agreeing) |
| `eyelids` | `open` (1 none … 0 shut), `secs`; or `drowsy` (seconds) | the screen's eyelids (`scripts/eyelids.gd`): dark lids from the top and the bottom with an eye-shaped opening, over everything. `drowsy`: heavy blinks, each a little more shut, ending nearly shut (90–120 s of quiet); `open 0` asleep, `open 1` over 2.2 s waking up. While they show, any touch anywhere is a `tap` (zone `screen` off the pet) — the app wakes the robot with it |

Events (`plugin.sendEvent(name, json)`): `hello` (listening: the plugin sends what waited), `ready`
(warmed up), `touch {kind, zone}` (kind: tap / double / stroke / scratch / hold / drag; zone: forehead,
left_cheek, right_cheek, chin, face, body — left/right as the viewer sees them), `scene {id, state:
started|finished}`, `back` (Android back: the app closes Avatar mode; `quit_on_go_back` is off).

The phone's tilt comes from Godot's own gravity sensor (`input_devices/sensors/enable_gravity`): the
body leans to stay upright in the room. On the desktop `--app-sim=<file.json>` plays the same commands
from `[{"at": s, "cmd": {…}}, {"at": s, "touch": [x, y], "kind": "double"}]` and prints the events.
