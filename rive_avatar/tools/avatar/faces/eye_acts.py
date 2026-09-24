# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# ================================================================= EYE ACTS
# What the eyes are *doing*, independent of which shape they are.
# (name, [(frame, scaleX, scaleY)], loop, eyeOverride)
# eyeOverride swaps the eye SHAPE: a LOOI squint is ^ ^, not a flattened
# circle, and closed eyes are a line you can still see.
EYE_ACTS = [
    ("Auto",     [], "loop", None),                                   # let the Blink layer run
    ("Squint",   [], "loop", V_ARC),
    ("Wide",     [(0, 1.12, 1.14)], "loop", None),
    ("Pop",      [(0, 1.0, 1.0), (8, 1.16, 0.40), (18, 0.94, 1.22),
                  (28, 1.04, 0.97), (40, 1.0, 1.0), (110, 1.0, 1.0)], "loop", None),
    ("Twinkle",  [(0, 1.0, 1.0), (12, 1.10, 1.10), (24, 0.95, 0.95),
                  (36, 1.07, 1.07), (48, 1.0, 1.0)], "loop", None),
    ("Closed",   [], "loop", V_BAR),
    ("SlowBlink", [(0, 1.0, 1.0), (60, 1.0, 1.0), (78, 1.0, 0.08),
                   (96, 1.0, 0.08), (118, 1.0, 1.0), (200, 1.0, 1.0)], "loop", None),
]
