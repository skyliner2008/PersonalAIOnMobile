# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 15. SHY ------------------------------------------------------------------
MOOD_STORIES["MoodShy"] = (MD, [
    (0, "Bashful", MOOD_SHY, G_NONE, F_NONE),
    (MD, "Bashful", MOOD_SHY, G_NONE, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (189, 0)] + osc(190, 300, 10, -26, 26) + [(306, 0), (MD, 0)]),
    ("blinkR", 13, [(0, 0), (189, 0)] + osc(190, 300, 10, -26, 26) + [(306, 0), (356, 0), (370, 26), (MD, 26)]),
    ("blinkL", 15, [(0, 0), (40, .18), (MD, .18)]),
    ("blinkR", 15, [(0, 0), (40, -.18), (MD, -.18)]),
] + _eyes(16, [(0, 1), (40, .62), (MD, .62)]) + _eyes(17, [(0, 1), (40, .5), (MD, .5)]) +
    _eyes(14, [(0, 0), (40, 24), (MD, 24)]) + [
    ("face", 17, [(0, 1), (180, 1), (210, .8), (300, .8), (330, 1), (MD, 1)]),
    ("face", 14, [(0, 0), (180, 0), (210, 50), (300, 50), (330, 20), (350, 10), (MD, 10)]),
    (_mp("MoodShy", "Blush"), 18, [(0, 0), (30, 0), (50, 1), (MD, 1)]),
    (_mp("MoodShy", "HandL"), 14, [(0, 460), (300, 460), (330, 0), (MD, 0)]),
    (_mp("MoodShy", "HandR"), 14, [(0, 460), (300, 460), (330, 0), (MD, 0)]),
    (_mp("MoodShy", "HandR"), 13, [(0, 0), (356, 0), (370, 58), (MD, 58)]),
])
