# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 5. SAD -------------------------------------------------------------------
_drop_y = [(0, 0), (300, 0), (360, 180), (362, 0), (394, 180), (MD, 180)]
_drop_o = [(0, 0), (300, 0), (304, 1), (352, 1), (360, 0), (364, 1), (388, 1), (394, 0), (MD, 0)]
MOOD_STORIES["MoodSad"] = (MD, [
    (0, "Neutral", MOOD_SAD, G_NONE, F_NONE),
    (70, "Weepy", MOOD_SAD, G_NONE, F_NONE),
    (MD, "Weepy", MOOD_SAD, G_NONE, F_NONE),
], _eyes(17, [(0, 1), (60, 1), (120, .78), (300, .78)] + osc(308, 372, 8, .25, .78) + [(384, .78), (MD, .78)]) +
   _eyes(14, [(0, 0), (120, 10), (MD, 10)]) + [
    ("mouth", 13, [(0, 0), (119, 0)] + osc(120, 300, 4, -2, 2) + [(304, 0), (MD, 0)]),
    ("face", 14, [(0, 0), (120, 8), (MD, 8)]),
    ("face", 15, [(0, 0), (120, .04), (MD, .04)]),
] + [e for nm in ("PoolL", "PoolR") for e in (
    (_mp("MoodSad", nm), 17, [(0, 0), (180, 0), (290, 1), (MD, 1)]),
    (_mp("MoodSad", nm), 18, [(0, 0), (178, 0), (184, 1), (MD, 1)]),
    (_mp("MoodSad", nm), 13, [(0, 0), (199, 0)] + osc(200, MD, 20, -4, 4)),
)] + [e for nm in ("DropL", "DropR") for e in (
    (_mp("MoodSad", nm), 14, _drop_y, "easeIn"),
    (_mp("MoodSad", nm), 18, _drop_o),
)])
