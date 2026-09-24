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

for _i, (_fn, _el, _er, _br, _mo, _nb) in enumerate(FACES):
    if _fn in MORPH_FACE:
        FACES[_i] = (_fn, V_MORPH, V_MORPH, _br, _mo, _nb)
