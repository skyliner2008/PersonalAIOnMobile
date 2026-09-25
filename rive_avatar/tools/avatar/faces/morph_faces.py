# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= MORPH FACES
# Faces whose eyes are the morph eye (parts/morph.py): changing between any two of
# them reshapes the eyes instead of swapping drawings. Face name -> shape name.
# Faces not listed keep their discrete eye variants.
MORPH_FACE = {
    "Neutral": "round",
    "Listening": "round",
    "Happy": "arc",
    "Sad": "droop",
    "Crying": "melt",
    "Angry": "angry",
}

# the Eye Lab's face -> shape table replaces the list above when the file exists
if EYE_LAB.get("faces"):
    MORPH_FACE = {f: s for f, s in EYE_LAB["faces"].items() if s in MORPH_SHAPES}

for _i, (_fn, _el, _er, _br, _mo, _nb) in enumerate(FACES):
    if _fn in MORPH_FACE:
        FACES[_i] = (_fn, V_MORPH, V_MORPH, _br, _mo, _nb)

# the Eye Lab timeline stories (stories/lab.py) key every vertex themselves: their
# beats use this face, which shows the morph eye without a shape of its own
_nrow = FACES[F_INDEX["Neutral"]]
FACES.append(("LabMorph", V_MORPH, V_MORPH, _nrow[3], _nrow[4], False))
F_INDEX["LabMorph"] = len(FACES) - 1
