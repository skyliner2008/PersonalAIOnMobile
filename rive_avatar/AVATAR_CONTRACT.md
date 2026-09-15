# rive_avatar — data contract (v6)

Artboard **RobotFace** 500×500 · state machine **State Machine 1** · view model **Avatar**

`scene.rml` and `presets.json` are **generated** by `tools/build_scene.py`.
`presets.json` is the source of truth for every index below — read names from
it rather than hard-coding them.

| property | type | range | what it drives |
|---|---|---|---|
| `face` | number | 0–42 | eyes (+ the odd brow / mouth) — one expression |
| `prop` | number | 0–55 | the prop / sticker at face level |
| `bg` | number | 0–10 | background scene, behind everything |
| `fg` | number | 0–11 | foreground overlay, in front of everything |
| `eyeAct` | number | 0–6 | what the eyes are *doing* (squint / wide / pop / twinkle / closed) |
| `state` | number | 0–19 | **what the robot is doing** — an operational loop |
| `react` | number | 0–8 | **what just happened to it** — a touch or a shock (one-shot) |
| `seq` | number | 0–100 | **a story** — one-shot; non-zero overrides everything below it |
| `item` | number | 0–4 | which food / drink / device an animated scene uses (see below) |
| `gazeX` / `gazeY` | number | −1…1 | 2.5D head turn (camera face tracking) |
| `tiltX` / `tiltY` | number | −1…1 | gyro — whole face slides ±26 / ±20 px and banks ∓0.06 rad |
| `audioLevel` | number | 0…1 | mic / TTS level — mouth ×0.94…×1.22, voice bar ×1…×2.2 |
| `isSpeaking` | boolean | | talking: voice bar + head bob (+ mouth pump if the face has one) |
| `clockD0..clockD3` | number | 0–9 | the four digits `StandbyClock` shows (HH:MM), default 12:00 |
| `mouthOpen` | number | 0…1 | reserved, not bound (use `audioLevel`) |

### Priority

`react` **>** `seq` **>** `state` **>** `face`/`prop`/`bg`/`fg`/`eyeAct`.
`gazeX/Y`, `tiltX/Y`, `audioLevel` and `isSpeaking` are outside that stack and
keep working under any story, state or reaction.

## Lifecycle rules the host must follow

- **`seq` and `react` play once and HOLD their last frame.** The host writes
  the index, waits `durationMs` (from `presets.json`), then writes `0`. Being
  late is harmless — nothing replays.
- **Re-firing restarts from the beginning.** Every channel state has
  `reset="true"`; to replay the same story write `0` first, then the index
  again on a later frame.
- **Clearing is always clean.** A `RestPose` layer returns every property a
  story / state / reaction animated to its rest value, so cutting a story off
  mid-way never leaves the eyes converged or the head tilted.
- **`state` loops forever** until the host changes it.
- **Entrances play when an item appears**, not on a global clock: sunglasses
  drop, the chyron slides in, bullet holes punch, the flood rises, curtains
  close — whether shown via `prop`/`fg` directly or by a story beat.

## Channel values

**face** — 0 Neutral, 1 Happy, 2 Angry, 3 Sleepy, 4 Curious, 5 Wink, 6 Dead,
7 Laughing, 8 Evil, 9 Focused, 10 Excited, 11 Shy, 12 Shock, 13 Disgusted,
14 Love, 15 Crying, 16 Sad, 17 Thinking, 18 Listening, 19 Confused, 20 Pout,
21 Dizzy, 22 Bored, 23 Scared, 24 Tired, 25 Blank, 26 Money, 27 Grin, 28 Smug,
29 Determined, 30 Calm, 31 Rage, **32 OneEye** (right eye replaced by a prop),
**33 Recognized** (ring eyes)

**prop** — 0 None, 1 Zzz, 2 Question, 3 Burger, 4 Beer, 5 Sparkle,
6 Headphones, 7 VR, 8 Snorkel, 9 Devil, 10 Sparkles, 11 Blush, 12 Exclaim,
13 Trash, 14 Camera, 15 AngryMark, 16 Hearts, 17 Tears, 18 ThinkDots,
19 SoundWave, 20 Questions, 21 Steam, 22 DizzyStars, 23 Sweat, 24 Shiver,
25 Medal, 26 BatteryLow, 27 Mic, 28 Sunglasses, 29 Missiles, 30 SLTag,
31 Turrets, 32 Snore, 33 HoloPat, 34 HoloPokeL, 35 HoloPokeR, 36 HoloChin,
37 Bulb, 38 Bolt, 39 Barcode, 40 Gears, 41 UpdateArrows, 42 Magnifier,
43 SignalBars, 44 PointHand, 45 FaceBrackets, 46 Sun, 47 RainCloud,
48 AlarmClock, 49 Calendar, 50 Clock, 51 Pencil, 52 Warning, 53 Curtains,
54 BigBattery, 55 Cracked

**bg** — 0 None, 1 Grid, 2 BokehHearts, 3 ChartUp, 4 ChartDown, 5 RedAlert,
6 GoldGlow, 7 BurstRays, 8 ShatterRed, 9 DeepBlue, 10 Spotlight

**fg** — 0 None, 1 Water, 2 Explosion, 3 Cracks, 4 LowerThird, 5 Confetti,
6 ArrowUp, 7 ArrowDown, 8 RedFlash, 9 MoneyRain, 10 Smoke, 11 BulletHoles

**eyeAct** — 0 Auto (blink layer runs), 1 Squint (ตาหยี — eyes become ^ ^),
2 Wide (ตาโต), 3 Pop (ตาปิ๊ง), 4 Twinkle (ตาวิ้ง), 5 Closed (a visible line),
6 SlowBlink

**seq** — 0 None, then `presets[i].seq`. 1–44 as before (Normal … Party);
**45–64 are the LOOI status set**: Idea, Disappointed, Pained, Scanning,
Processing, Charging, SoftwareUpdate, BatteryEmpty, SignalSearch, Searching,
GestureInput, FaceRecognized, WeatherSunny, WeatherRainy, Alarm, Calendar,
StandbyClock, DrawingMode, InvalidCommand, SystemSecured.
**65–81 are the animated pet scenes** (script: `.obsidian-wiki/02_Components/Pet_Scene_Scripts.md`),
7.5–9 s each, drawing their own `Scene*` props (prop indices 56–72):
65 SceneEating (8.0 s), 66 SceneDrinking (7.5), 67 SceneBath (8.0), 68 SceneGaming (8.5),
69 SceneStudy (8.0), 70 SceneRain (8.0), 71 SceneThug (7.5), 72 SceneRich (8.0),
73 SceneRoyal (8.0), 74 SceneFire (7.5), 75 SceneThunder (7.5), 76 SceneSoulOut (8.0),
77 SceneSuperLove (8.0), 78 SceneCry (8.5), 79 SceneCelebrate (8.5), 80 SceneVR (9.0),
81 SceneMusic (8.5). The angry-missile scene reuses 33 AngryMissile.

**item** — picks the variant inside a scene: food 0 Burger, 1 Pizza, 2 Cake,
3 IceCream, 4 Popcorn · drink 0 Coffee, 1 Boba, 2 Tea, 3 Beer · study 0 Laptop,
1 Book. Write it before (or with) `seq`.

**face 34 Starry** — star eyes used by the eating / gaming / royal scenes.

**82–100 are the jelly mood stories** (script: `.obsidian-wiki/02_Components/Pet_Mood_Scripts.md`),
7 s each, with their own `Mood*` props (prop 73–91) and faces 35–42 (Weepy, Quizzical, Fume, Beam,
Glad, Bashful, Fluster, Haughty): 82 MoodIdleBall, 83 MoodYoYo, 84 MoodStars, 85 MoodShocked,
86 MoodSad, 87 MoodCurious, 88 MoodAngryMissile, 89 MoodFuming, 90 MoodGlitch, 91 MoodThinking,
92 MoodHappy, 93 MoodInLove, 94 MoodGlad, 95 MoodAwesome, 96 MoodShy, 97 MoodEmbarrassed,
98 MoodShowOff, 99 MoodListening, 100 MoodArrogant. The app plays them on a mood change
(`RiveMoodStories`), holds the last frame 1.5 s, then clears `seq`.

**state** — 0 Off, 1 Idle, 2 Ready, 3 Listening, 4 Thinking, 5 Speaking,
6 Detected, 7 Scanning, 8 Standby, 9 WaitCmd, 10 Working, 11 TiltL, 12 TiltR,
13 Dizzy, 14 PlayBounce, 15 PlayChase, 16 PlaySpin, 17 PlayPeek,
18 PlayWiggle, 19 Asleep

**react** — 0 None, 1 HeadPat, 2 PokeL, 3 PokeR, 4 PokeAnnoyed, 5 PokeAngry,
6 ChinScratch, 7 Startled, 8 ShakeAngry

## Style (LOOI)

The eyes carry the emotion. They are big (rendered radius ≈ 67 px, centres at
x = 154 / 346, y = 230) and most faces have **no brow and no mouth**; a brow or
mouth survives only where the eyes alone would be ambiguous (Thinking,
Confused, Disgusted, Crying, Pout, Tired, Smug, Grin, Calm, Determined) or as
a detail (Angry / Evil fang). One colour per face: red brows and mouth only
ever sit over red eyes (Rage).

A face with no mouth still *talks*: while `isSpeaking` is true a short neon
voice bar fades in under the eyes and stretches with `audioLevel`, and the
head bobs on the syllables.

## States

| state | kind | what it does |
|---|---|---|
| 0 **Off** | — | keys nothing; the manual channels own the face |
| 1 **Idle** | motion only | gaze drifts left, holds, drifts right, two blinks per 8 s |
| 2 **Ready** | motion only | eyes 5 % bigger, quick small darts |
| 3 **Listening** | pins face | still stare that widens in pulses + equaliser bars |
| 4 **Thinking** | pins face | eyes roll up and sweep sideways + chasing dots |
| 5 **Speaking** | motion only | head nods, mouth pumps (× `audioLevel`) |
| 6 **Detected** | pins face | eyes pop, head recoils, settles into a smile |
| 7 **Scanning** | pins face | wedge eyes sweep left/right on a scan grid |
| 8 **Standby** | motion only | lids at half, head sags, one very slow blink per 10 s |
| 9 **WaitCmd** | pins face | head tilts and holds + question mark |
| 10 **Working** | pins face | focused eyes pulse over the grid + chasing dots |
| 11 / 12 **TiltL / TiltR** | pins face | เอียงฟัง — head tilts, near eye grows |
| 13 **Dizzy** | pins face | head sways and rolls, eyes counter-drift under stars |
| 14–18 **Play\*** | motion only | self-play set (`selfPlay` in presets.json) |
| 19 **Asleep** | pins face | flat eyes, head sagging, Zzz and a snot bubble |

**Motion-only states blink through their own `StateBlink` nodes.** A face
marked `noblink` (Happy arcs, Dead crosses, hearts, dollars, spirals, …)
vetoes those blinks through the `BlinkGate` layer, so `state = Idle` never
squashes a Dead face's crosses into lines.

## Reactions

| react | trigger | what it does |
|---|---|---|
| 1 **HeadPat** | touch above the eyes (`y < 160`) | holo hand strokes the head, happy arcs, bokeh |
| 2 / 3 **PokeL / PokeR** | touch left `x < 140` / right `x > 360` at eye height | holo finger prods the cheek twice, head is pushed on each contact |
| 4 **PokeAnnoyed** | repeated pokes, level 1 | pout, head shakes off the finger, steam |
| 5 **PokeAngry** | repeated pokes, level 2 | red eyes converge, red alert, shaking |
| 6 **ChinScratch** | touch below the eyes (`y > 330`) | holo hand under the chin, eyes melt into hearts |
| 7 **Startled** | too loud | recoils with a red flash, then sweats |
| 8 **ShakeAngry** | phone shaken | dizzy spirals, then angry |

Escalation (PokeL/R → PokeAnnoyed → PokeAngry) is the host's job.

## Engine rules the generator enforces

The build **fails** (`SystemExit`) if any of these break:

1. **Every `cubic` keyframe carries a `CubicEaseInterpolator`.** Without one
   Rive does not ease at all. All keyframes go through `kf()`, which attaches
   the curve (`cubic` = ease-in-out, plus `easeIn`, `easeOut`, `soft`).
2. **Every channel-layer state has `reset="true"`**, and every story and
   reaction is `oneShot`.
3. **The eyes never touch**, converging or widening (`max_converge`,
   `max_eye_scale`, `check_eye_scale`), measured on the *rendered* (scaled)
   eye widths.
4. **A brow never reads as a second pair of eyes** (`check_faces`).

And by construction:

- `RestPose` (first layer) keys the rest value of every property any story,
  state, reaction, eye act or the Speech layer animates, except those an
  always-running layer already owns (Blink, Breathe, PropLoops).
- Entrance motion is registered with `intro()` and baked into the channel
  animation that shows the item **and** into every story beat that switches
  to it. Looping motion is registered with `track()` and tiled into
  `PropLoops`.
- Movers draw back-to-front like props (last declared = in front).

## Layer order (later wins)

`RestPose` → `Blink` → `Breathe` → `Face` → `EyeAct` → `Prop` → `Bg` → `Fg`
→ `Clock0..3` → `PropLoops` → `State` → `BlinkGate` → `Seq` → `React` →
`GazeYaw` → `GazePitch` → `Speech`

## Working on it

```bat
python tools\build_scene.py scene.rml   REM also writes presets.json
rive . --verify                          REM must be 0 errors
rive inspect . --summary                 REM must be "problems": []
rive . --screenshot=build/x.png --data=seq=3 --advance=300
..\scripts\build_rive_avatar.ps1         REM all of the above + copy to res/raw
```

- A new expression = one row appended to `FACES`
- A new sticker = one `elif` in `emit_prop` + a name appended to `P_NAMES`;
  looping motion via `track()`, entrance motion via `intro()`
- A new operational state = one row in `STATES`; leave its beat list empty and
  it becomes motion-only
- A new story = an entry in `HAND_STORIES`, or extra motion for an automatic
  story in `PRESET_EXTRAS`
- Beats are `(frame, faceName, prop, bg, fg)`; extras are
  `(nodeKey, propertyKey, keyframes[, interp])` with `nodeKey` one of
  `face` / `mouth` / `blink` / `blinkL` / `blinkR` / `prop:<idx>:<moverName>`

**Anchors.** Rotation and scale on a Node happen about its own origin:
`mover(..., anchor=(x, y))` parks the mover on the artwork's centre. Every
mover with a rotation (15) or scale (16/17) track needs an anchor.

**Positioning.** Keep props out of the eyes (`x 87–413`, `y 163–297`). Free
zones: the corners `CORNER_R (428, 100)` / `CORNER_L (72, 100)`, the top strip
`y < 150`, the bottom strip `y > 330`, the side margins `x < 80` / `x > 420`.
Props that *replace* the eyes (VR, snorkel, sunglasses, barcode, gears, sun,
clock, …) pair with `Blank`.

## Wiring it from Android (rive-android 11.x)

```kotlin
view.setRiveResource(R.raw.avatar, stateMachineName = "State Machine 1", autoBind = true)
val vmi = view.controller.stateMachines.first().viewModelInstance ?: return
vmi.getNumberProperty("seq").value = 3f          // throws ViewModelException if the name is wrong
vmi.getNumberProperty("gazeX").value = gazeX.coerceIn(-1f, 1f)
vmi.getNumberProperty("audioLevel").value = rms.coerceIn(0f, 1f)
vmi.getBooleanProperty("isSpeaking").value = isSpeaking
```

`autoBind = true` is required — without it the view model is never bound and
every write is ignored. Look properties up once and cache them.

**In this app** the view model is written by `RiveAvatarView.android.kt`
(cached properties, change-only writes, gyro → `tiltX/tiltY`). What to write
comes from `RiveAvatarBinding.kt` (`RiveAvatarMapper.plan`) and the timers in
`RivePetAvatar` — see *Pet mode binding* below.

## Pet mode binding (Compose ↔ Rive)

The Rive pet obeys exactly the same conditions as the Compose Canvas pet: both
read `AvatarState` (emotion, `faceState.eyeStyle` override via
`effectiveEmotion()`, props / background / foreground, eye tricks, dizzy,
speaking, gaze) and the same touch / sensor events from `PetModeScreen`.

| Canvas signal | Rive channel |
|---|---|
| `effectiveEmotion()` (58 emotions) | `face` + default `prop` / `bg` / `fg` (`RiveAvatarMapper.lookOf`) |
| emotion accessories Rive lacks (laptop, chef hat, …) | drawn by `PetPropsOverlay` on top |
| `faceState.props` | first prop Rive has → `prop`; the rest stay in `PetPropsOverlay` |
| `faceState.backgroundTheme` / `foregroundEffect` | Rive `bg`/`fg` when it has one, otherwise `PetBackgroundLayer` / `PetForegroundLayer` (the artboard is transparent) |
| LISTENING / THINKING / speaking / SLEEPING / dizzy / BORED | `state` 3 / 4 / 5 / 19 / 13 / 8 |
| eye tricks PING_PONG / TIRED / SNOOKER | `state` PlayBounce / PlayPeek / PlayChase |
| gesture TILT_LEFT / TILT_RIGHT | `state` TiltL / TiltR |
| holographic hand: stroke·pat / poke·tickle / chin | `react` HeadPat / PokeL·R (→ PokeAnnoyed on POUT, PokeAngry on ANGRY) / ChinScratch |
| SURPRISED without a touch (loud noise, thump) | `react` Startled |
| shake ending in ANGRY / ENRAGED | `react` ShakeAngry |
| face reappears after > 10 s | `state` Detected (while Idle) |
| missile barrage (fight back) | `seq` AngryMissile, then `onMissileBarrageFinished` |
| `faceScaleFactor` | view scale |

With Rive active the Canvas-only overlays (holographic hand, missile barrage)
are not drawn. The chosen engine is persisted (`pet.avatar_engine`). If the
native library fails, `RiveRuntime.failed` flips and everything falls back to
Canvas, including the full prop / background lists.

### Prop placement rules (checked 2026-09-15)
Eyes: centres (154, 230) / (346, 230), rendered radius 67 → the eye box is y 163–297.
- **Head-top props** (horns, crowns, hats, halos, Zzz, status icons in the corners) sit entirely above y 163, or outside the eye columns.
- **Eyewear** (sunglasses, pixel shades, VR visor, snorkel mask) is at least as tall and wide as an eye: lenses ≥ 150 px tall, ≥ 170 px wide, covering both eyes.
- **Readable on a phone**: a main icon is ≥ ~70 px across, decorations ≥ ~30 px. Small corner icons grow about their own centre through `PROP_FIT` / `fit()` in `build_scene.py`, always staying inside the 500 × 500 artboard.
- Props that *replace* an eye (Bulb, Barcode, Gears, Magnifier, Sun…) pair with the `OneEye` / `Blank` face; the app switches to `OneEye` whenever it shows the Bulb.
