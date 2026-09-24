# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 13. GLAD -----------------------------------------------------------------
MOOD_STORIES["MoodGlad"] = (MD, [
    (0, "Glad", MOOD_GLAD, G_NONE, F_NONE),
    (190, "Glad", MOOD_GLAD, G_NONE, F_CONFETTI),
    (MD, "Glad", MOOD_GLAD, G_NONE, F_CONFETTI),
], [
    ("blinkL", 14, [(0, 0), (299, 0)] + osc(300, MD, 15, -10, 10)),
    ("blinkR", 14, [(0, 0), (307, 0)] + osc(308, MD, 15, -10, 10)),
    ("blinkL", 15, [(0, 0), (299, 0)] + osc(300, MD, 15, -.12, .12)),
    ("blinkR", 15, [(0, 0), (299, 0)] + osc(300, MD, 15, .12, -.12)),
] + _eyes(17, [(0, .7), (16, 1.12), (28, 1), (189, 1)] + osc(190, 298, 6, 1, .2) + [(304, 1), (MD, 1)]) + [
    ("face", 14, [(0, 10), (16, -10), (30, 0), (189, 0)] + osc(190, 298, 15, 0, -16) + [(304, 0), (MD, 0)]),
    ("face", 13, [(0, 0), (299, 0)] + osc(300, MD, 30, -6, 6)),
    (_mp("MoodGlad", "PopL"), 13, [(0, -130), (170, -130), (186, 0), (190, -10), (198, 0), (MD, 0)]),
    (_mp("MoodGlad", "PopR"), 13, [(0, 130), (170, 130), (186, 0), (190, 10), (198, 0), (MD, 0)]),
    (_mp("MoodGlad", "Burst"), 18, [(0, 0), (188, 0), (190, 1), (240, 0), (MD, 0)]),
] + _sc("MoodGlad", "Gleam", osc(0, MD, 20, .6, 1.1)) +
    _sc("MoodGlad", "Burst", [(0, 0), (188, 0), (196, 1.2), (230, 1.8), (MD, 1.8)], "easeOut"))
