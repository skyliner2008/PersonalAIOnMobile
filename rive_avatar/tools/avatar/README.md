# Avatar generator (`tools/avatar/`)

Builds `rive_avatar/scene.rml` + `presets.json`, which `rive . --once` compiles into
`build/rive_avatar.riv` (copy it to `composeApp/src/androidMain/res/raw/avatar.riv`).

```
python tools/avatar/build.py            # from rive_avatar/  (tools/build_scene.py still works)
rive . --verify                         # 0 errors / 0 warnings
rive . --screenshot=build/x.png --data=face=16 --advance=40   # one face
rive . --screenshot=build/x.png --data=seq=86 --advance=250   # a story frame
rive . --once                           # write build/rive_avatar.riv
```

## Layout

The files in `ORDER.txt` run **in that order into one shared namespace** (like the old
single 5,100-line script, so everything a later file uses is already defined).

| Folder | What |
|---|---|
| `core/` | palette, ids, `w()`, keyframes (`kf`, `EASES`), paint and geometry helpers, layout |
| `parts/` | eye / brow / mouth variants, **`morph.py` (the morphing eye)**, props, backgrounds |
| `faces/` | the face table, `morph_faces.py` (which faces use the morph eye), eye acts, safety guards |
| `motion/` | acting helpers: `tremble`, `lagged`, `sniffs`, `vertex_quiver` |
| `stories/` | auto stories, `assemble.py` (mood presets, sequence list) |
| `scenes/` | one file per scene story (`01_eating.py` ...) |
| `moods/` | one file per mood story (`05_sad.py` ...) |
| `machine/` | states, reactions, the document (rig), animations, state machine |

## Eye Lab: a design table, not the app (`tools/eyelab/`)

```
python rive_avatar/tools/eyelab/server.py      # from the repo root, then open http://localhost:8765
```

A local page (Python standard library only) where the user and Claude design the eyes and
their stories together. It is a **workspace**, separate from the app:

1. The user sketches on the page (shapes, rig, a timeline of keys as a guide, notes).
2. Claude reads it and refines **in code**, usually as its own version of a story
   (`tools/eyelab/labstory.py`: `fork`, `add_key`, `set_eyes`, `shape`, `save`), and answers
   in `claudeNote` (story, key or shape).
3. The page polls the files and shows Claude's change within ~2 s; "เทียบต้นแบบ" draws the
   sketch it came from as dashed outlines over Claude's version.
4. Repeat. When the user is happy, Claude ships it to the app (only then).

| workspace (page + Claude) | app (the generator reads only these) |
|---|---|
| `tools/eyelab/data/eye_lab.json` | `tools/avatar/data/eye_app.json` |
| `tools/eyelab/data/stories/<name>.json` | `tools/avatar/data/stories/<name>.json` (+ `target`) |

```
python rive_avatar/tools/eyelab/ship.py status                  # what the app does not have yet (the page shows it too)
python rive_avatar/tools/eyelab/ship.py check [--shots]         # build the WORKSPACE into a temp project (stories use
                                                                # their intent), rive --verify, warnings, Rive shots per key
python rive_avatar/tools/eyelab/ship.py eye [shapes faces motion rig]
python rive_avatar/tools/eyelab/ship.py story <name> <MoodTarget> [--mute-cues|--keep-cues]
python rive_avatar/tools/eyelab/ship.py unship <name>           # also restores that mood's old sound cues
python rive_avatar/tools/eyelab/ship.py build                   # build.py + rive --once + copy the .riv
```

Before shipping, run `check`: it prints every placement warning (eyes touching, past the
artboard, past the portrait screen -- `eye_fit_warnings` in `faces/guards.py`, the same rule
the page shows live) and a story whose length differs from the mood it replaces. `story`
warns when the Lab's rig / motion differ from the app's (the story was designed with them).

**Sounds.** The app plays timed sound cues with every mood story (`RiveMoodStories.kt`),
written for the old story. A key's `sound` (a `RobotSound` name) becomes that mood's cue
when the story ships; the old cues are kept in the shipped file (`oldCues`) and come back
on `unship`. A story without sounds must choose `--mute-cues` or `--keep-cues`.

`eye_lab.json` / `eye_app.json` hold:

- `shapes`: 8 points `[x, y, smoothness]` for the left eye (right eye mirrored unless
  `mirror: false`), colour, the LOOI sheet cell to compare with, `note` / `claudeNote`
- `faces`: app face -> shape (faces not listed keep their old discrete eyes)
- `motion`: morph time, right-eye lag, pop strength, tremble / quiver amounts
- `rig`: eye spacing / size, back-layer offset, aura; `rig.head` = the 3D head model (sphere radius,
  max yaw / pitch, tilt while turning, pupil lead, perspective, `core/head.py`) from which
  `machine/poses.py` builds the Rive look poses; `rig.eyeRot` = a fixed X/Y/Z turn per eye (`EyeTilt`)

The page has three areas: tools by category on the left (library shapes, base rig, 3D head,
eye rotation, ellipse, props, motion, app faces), the view on the right (drag the 8 points,
reference sheet, eye colour, drag the prop) and the **timeline** at the bottom.

What the view edits (the switch above the view):

- **แก้รูปในคลัง** -- a library shape. The app's faces use it, and here the eye rotation is
  the app's fixed one (`rig.eyeRot`); the head pose is only a try-out; no props.
- **จัดท่าสำหรับไทม์ไลน์** -- a scratch frame (kept in the browser) for composing keys. It never
  touches the library or the app's values; clicking a library shape loads it into the frame.
  "＋ เพิ่มท่านี้เป็นคีย์" snapshots it after the last key and leaves it as it is: adjust a
  little, add again.
- **a key** (click it on the timeline) -- edits go to that key; "จัดท่าต่อจากคีย์นี้" copies it
  back into the scratch frame.

Pressing / dragging on the ruler, and stopping playback, show the story at that moment
(read-only). The warning line under the view uses the same placement rule as the generator.
Props are drawn over the eyes, as in the app. `window.eyelabDebug` (storyFrame, frameBoxes,
render) lets Claude compare the page with `check --shots`.
"จอมือถือ" draws a rough guide of the phone screen (portrait: pet area on top, status panel
below; landscape: full screen), placed with the `RIVE_*` constants of `PetModeScreen.kt` at a
16:9 screen. Phones differ in shape, so it is an approximate area, not an exact frame.

### Timeline stories (`stories/lab.py`)

Compose a moment in 2.5D (eye shapes, head pose, per-eye rotation, one library prop),
press "เพิ่มท่านี้เป็นคีย์", adjust, add again. Each key stores the whole frame plus how it
moves on (ease: jelly / smooth / soft / linear / hold) and its effects (pop on arrival,
trembling and lower-lid quiver through the segment) and an optional sound. Undo covers key
edits, adding, deleting and re-timing. Play / stop / loop / scrub preview the story. The story
format is documented at the top of `tools/eyelab/labstory.py`; `intent` is the mood story the
user means it for (the page shows when the app plays it and its current sounds).

A shipped story (with `target`) replaces that mood story (for example `MoodSad`, 7 s).
`stories/lab.py` maps a key onto the rig with the same maths as the page:

| lab | rig |
|---|---|
| eye outline | `vtxL:i` / `vtxR:i` (front + back vertices, always keyed) |
| head yaw / pitch / roll | `blinkL/R` x, y (eye offset from the centre), `sblinkL/R` scale, `face` x, y, rotation, `irisL/R` (pupil lead) |
| eye rotation X / Y / Z | `tiltL/R` scaleY = cos x, scaleX = cos y, rotation = z |
| pop | `popL/R` (`POP_KEYS`, right eye `POP_LAG` later) |
| prop | the beat's prop + `props` (PropsSolo) x, y, rotation, scale, opacity about the prop's centre |
| colour | `lab_color_frames` in `machine/animations.py` (crossfade over `MORPH_F` before the next key) |

Beats use the face `LabMorph` (the morph eye with no shape of its own). Prop centres come
from `tools/eyelab/props/props.json`, written by `tools/eyelab/make_prop_thumbs.py`
(renders every prop from the real `.riv`; rerun it after changing props). One library prop
per key; a prop moves / scales / turns / fades but cannot change shape. A story may also be
shipped by hand-writing a mood file (`moods/*.py`) when it needs more than keys can say.

## The morph eye (`parts/morph.py`)

Each eye's front and back layer is one closed path of 8 cubic vertices. Every shape in
`MORPH_SHAPES` gives the same 8 points (top, then clockwise) + a smoothness per point, so
any two shapes blend vertex by vertex:

- a **face change** (Face layer) mixes them over `FACE_MIX_MS` with an ease
- a **story beat** onto a morph face reshapes with the jelly ease over `MORPH_F` frames
- every face change / beat also plays the **pop** (squash, overshoot, settle) on the
  `EyePop` nodes, the right eye `POP_LAG` frames later and softer

Faces in `MORPH_FACE` (`faces/morph_faces.py`) use it: Neutral, Listening, Happy, Sad,
Crying, Angry. The others still swap discrete variants (cross, chevron, spiral...).

To add a shape: add 8 points (left eye, clockwise from the top) to `MORPH_SHAPES`, then map
a face to it in `MORPH_FACE`. The eye-gap guard (`V_MORPH` in `faces/guards.py`) measures the
widest inner side of all shapes, and every shape is checked against the screen at rest.

## Acting rules for stories

- Start on the normal eye and end on a calm shape: **enter -> act -> exit**.
- Never give both eyes the same keys: use different seeds (`tremble`), `lagged(...)`, or
  `eye_pair(key, left, right)`.
- No clean sine for emotion: `tremble` makes irregular steps and sizes with an `envelope`.
- The outline can act too: `vertex_quiver("L", (3, 4, 5), shapes, windows, seed)` shakes
  the lower lid. Keep windows out of the `MORPH_F` frames after a shape change.
