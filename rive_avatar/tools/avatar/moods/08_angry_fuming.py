# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 8. ANGRY: FUMING ---------------------------------------------------------
MOOD_STORIES["MoodFuming"] = (MD, [
    (0, "Fume", MOOD_FUME, G_NONE, F_NONE),
    (300, "Rage", MOOD_FUME, G_NONE, F_NONE),
    (MD, "Rage", MOOD_FUME, G_NONE, F_NONE),
], _eyes(17, [(0, 1), (30, .45), (180, .45), (290, 1.25), (300, 1.25)] + osc(303, MD, 6, .9, 1.1)) +
   _eyes(14, [(0, 0), (30, 32), (180, 32), (290, 0), (MD, 0)]) + [
    ("face", 13, [(0, 0), (179, 0)] + osc(180, 298, 4, -3, 3) + osc(303, MD, 3, -12, 12)),
    ("face", 16, [(0, 1), (180, 1), (290, 1.3), (300, 1.3), (312, 1), (MD, 1)]),
    ("face", 17, [(0, 1), (180, 1), (290, 1.3), (300, 1.3), (312, 1), (MD, 1)]),
    (_mp("MoodFume", "Heat"), 18, [(0, 0), (300, 0), (306, .45)] + osc(316, MD, 10, .25, .45)),
] + [e for nm in ("JetL", "JetR") for e in (
    (_mp("MoodFume", nm), 18, [(0, 0), (100, 0), (108, 1), (MD, 1)]),
    (_mp("MoodFume", nm), 14, [(0, 0), (99, 0)] + osc(100, MD, 20, 0, -24)),
)] + _sc("MoodFume", "JetL", [(0, 0), (100, 0), (120, 1), (180, .8), (290, 1.6), (300, .4), (310, 2.2), (MD, 2.2)]) +
    _sc("MoodFume", "JetR", [(0, 0), (100, 0), (120, 1), (180, .8), (290, 1.6), (300, .4), (310, 2.2), (MD, 2.2)]))
