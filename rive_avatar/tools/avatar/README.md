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
python rive_avatar/tools/eyelab/ship.py eye [shapes faces motion rig]
python rive_avatar/tools/eyelab/ship.py story <name> <MoodTarget>   # replaces that mood story
python rive_avatar/tools/eyelab/ship.py unship <name>
python rive_avatar/tools/eyelab/ship.py build                   # build.py + rive --once + copy the .riv
```

`eye_lab.json` / `eye_app.json` hold:

- `shapes`: 8 points `[x, y, smoothness]` for the left eye (right eye mirrored unless
  `mirror: false`), colour, the LOOI sheet cell to compare with, `note` / `claudeNote`
- `faces`: app face -> shape (faces not listed keep their old discrete eyes)
- `motion`: morph time, right-eye lag, pop strength, tremble / quiver amounts
- `rig`: eye spacing / size, back-layer offset, aura; `rig.head` = the 3D head model (sphere radius,
  max yaw / pitch, tilt while turning, pupil lead, perspective, `core/head.py`) from which
  `machine/poses.py` builds the Rive look poses; `rig.eyeRot` = a fixed X/Y/Z turn per eye (`EyeTilt`)

The page has three areas: tools by category on the left (library shapes, base rig, 3D head,
eye rotation, ellipse, props, motion defaults, app faces), the view on the right (drag the 8
points, reference sheet, eye colour, drag the prop) and the **timeline** at the bottom.
"จอมือถือ" frames the view as the phone shows it (9:16 portrait / 16:9 landscape), using
the Rive placement constants of `PetModeScreen.kt` (`RIVE_*`): portrait = the pet area is the
top 54% of the screen (9 : 8.64), artboard side 1.10 x width, eye row at 40% of the area;
landscape = full screen, side 1.15 x height, eye row at 42%. Change both together.

### Timeline stories (`stories/lab.py`)

Compose a moment in 2.5D (eye shapes, head pose, per-eye rotation, one library prop),
press "เพิ่มท่านี้เป็นคีย์", adjust, add again. Each key stores the whole frame plus how it
moves on (ease: jelly / smooth / soft / linear / hold) and its effects (pop on arrival,
trembling and lower-lid quiver through the segment). With a key selected, every edit goes
to that key. Play / stop / loop / scrub preview the story. The story format is documented
at the top of `tools/eyelab/labstory.py`; `intent` is the mood story the user means it for.

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

To add a shape: add 8 points (left eye, clockwise from the top) to `MORPH_SHAPES`, keep
its half-width within the round eye's (`V_MORPH` in `faces/guards.py`), then map a face
to it in `MORPH_FACE`.

## Acting rules for stories

- Start on the normal eye and end on a calm shape: **enter -> act -> exit**.
- Never give both eyes the same keys: use different seeds (`tremble`), `lagged(...)`, or
  `eye_pair(key, left, right)`.
- No clean sine for emotion: `tremble` makes irregular steps and sizes with an `envelope`.
- The outline can act too: `vertex_quiver("L", (3, 4, 5), shapes, windows, seed)` shakes
  the lower lid. Keep windows out of the `MORPH_F` frames after a shape change.
