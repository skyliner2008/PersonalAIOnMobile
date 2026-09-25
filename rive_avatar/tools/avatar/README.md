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
