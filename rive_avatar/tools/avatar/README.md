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

## Eye Lab: editing the shapes together (`tools/eyelab/`)

```
python rive_avatar/tools/eyelab/server.py      # from the repo root, then open http://localhost:8765
```

A local page (no internet data, Python standard library only) that edits
`tools/avatar/data/eye_lab.json` -- the **source of truth** for the morph eye:

- `shapes`: 8 points `[x, y, smoothness]` for the left eye (right eye mirrored unless
  `mirror: false`), colour, the LOOI sheet cell to compare with, and a `note` for Claude
- `faces`: app face -> shape (faces not listed keep their old discrete eyes)
- `motion`: morph time, right-eye lag, pop strength, tremble / quiver amounts
- `rig`: eye spacing / size, back-layer offset, aura; `rig.head` = the 3D head model (sphere radius,
  max yaw / pitch, tilt while turning, pupil lead, perspective) from which `machine/poses.py` builds the
  Rive look poses; `rig.eyeRot` = a fixed X/Y/Z turn per eye (the `EyeTilt` node)

The page has three areas: tools by category on the left (library shapes, base rig, 3D head,
eye rotation, ellipse, props, motion defaults, app faces), the view on the right (drag the 8
points, reference sheet, eye colour, drag the prop) and the **timeline** at the bottom.

### Timeline stories (`data/stories/<name>.json` -> `stories/lab.py`)

Compose a moment in 2.5D (eye shapes, head pose, per-eye rotation, one library prop),
press "เพิ่มท่านี้เป็นคีย์", adjust, add again. Each key stores the whole frame plus how it
moves on (ease: jelly / smooth / soft / linear / hold) and its effects (pop on arrival,
trembling and lower-lid quiver through the segment). With a key selected, every edit goes
to that key. Play / stop / loop / scrub preview the story.

A story whose "ใช้แทน" (target) is set replaces that mood story (for example `MoodSad`,
7 s) when the generator runs; without a target it stays a draft. `stories/lab.py` maps a
key onto the rig with the same maths as the page:

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
per key; a prop moves / scales / turns / fades but cannot change shape.

The page polls the file every 1.5 s, so an edit Claude makes to the JSON shows up live.
"สร้างไฟล์ Rive" runs `build.py` + `rive . --once` and copies the `.riv` into the app
(reinstall the app to see it on the phone). `parts/morph.py` and `faces/morph_faces.py`
read the JSON; the tables in them are only the fallback without the file.

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
