# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 9. ANGRY: FURIOUS GLITCH -------------------------------------------------
_gf = (10, 34, 52, 70, 140, 160, 200, 230, 280, 316, 350, 390)
MOOD_STORIES["MoodGlitch"] = (MD, [
    (0, "Rage", MOOD_GLITCH, G_NONE, F_NONE),
    (312, "Rage", MOOD_GLITCH, G_NONE, F_CRACKS),
    (MD, "Rage", MOOD_GLITCH, G_NONE, F_CRACKS),
], [
    ("blinkL", 13, osc(0, 176, 4, -6, 6) + [(180, 0), (220, 55), (300, 55), (330, 0)] + osc(334, MD, 5, -4, 4)),
    ("blinkR", 13, osc(0, 176, 4, 6, -6) + [(180, 0), (220, -55), (300, -55), (330, 0)] + osc(334, MD, 5, 4, -4)),
] + _eyes(16, [(0, 1), (80, 1), (90, .05), (112, .05), (124, 1), (180, 1), (220, .8), (300, .8), (330, 1), (MD, 1)]) +
    _eyes(17, [(0, 1), (80, 1), (90, .05), (112, .05), (124, 1.1), (134, 1), (MD, 1)]) + [
    ("face", 16, [(0, 1), (300, 1), (312, 1.6), (322, .85), (334, 1.05), (344, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (300, 1), (312, 1.6), (322, .85), (334, 1.05), (344, 1), (MD, 1)]),
    ("face", 14, [(0, 0), (300, 0), (312, -20), (322, 10), (334, 0), (MD, 0)]),
    (_mp("MoodGlitch", "GhostR"), 18, _pulse_op(_gf, 3, .9)),
    (_mp("MoodGlitch", "GhostC"), 18, _pulse_op([f + 2 for f in _gf], 3, .8)),
    (_mp("MoodGlitch", "GhostR"), 13, osc(0, MD, 5, -6, 14)),
    (_mp("MoodGlitch", "Scan"), 14, osc(0, MD, 6, -20, 20)),
    (_mp("MoodGlitch", "Scan"), 18, osc(0, MD, 9, .2, 1)),
    (_mp("MoodGlitch", "Pixels"), 18, [(0, 0), (86, 0), (90, 1), (112, 1), (124, 0), (MD, 0)]),
] + _sc("MoodGlitch", "Pixels", [(0, .6), (86, .6), (90, 1), (104, 2.2), (118, .8), (124, .3), (MD, .3)]) +
    _sc("MoodGlitch", "Bang", [(0, 0), (180, 0), (192, 1.3), (202, 1), (300, 1), (310, 0), (MD, 0)]))
