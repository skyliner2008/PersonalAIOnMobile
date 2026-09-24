# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 16. EMBARRASSED ----------------------------------------------------------
MOOD_STORIES["MoodEmbarrassed"] = (MD, [
    (0, "Fluster", MOOD_EMBARRASSED, G_NONE, F_NONE),
    (MD, "Fluster", MOOD_EMBARRASSED, G_NONE, F_NONE),
], _eyes(14, [(0, 0), (20, -10), (60, 120), (200, 120), (220, 170), (250, 120), (270, 170), (300, 150), (390, 200), (MD, 200)]) +
   _eyes(17, [(0, 1), (60, .65), (70, .8), (180, .7), (300, .7), (390, .12), (MD, .12)]) +
   _eyes(16, [(0, 1), (60, 1.08), (180, 1.05), (300, 1.05), (390, 1.12), (MD, 1.12)]) +
   _eyes(13, [(0, 0), (299, 0)] + osc(300, 390, 3, -3, 3) + [(394, 0), (MD, 0)]) + [
    (_mp("MoodEmbarrassed", "Sweat"), 18, [(0, 0), (40, 0), (46, 1), (170, 1), (180, 0), (MD, 0)]),
    (_mp("MoodEmbarrassed", "Sweat"), 13, [(0, 0), (45, 0)] + osc(46, 180, 10, -10, 10) + [(MD, 0)]),
    (_mp("MoodEmbarrassed", "Wall"), 14, [(0, 320), (180, 320), (210, -12), (220, 0), (300, 0), (330, 320), (MD, 320)]),
    (_mp("MoodEmbarrassed", "Dim"), 18, [(0, 0), (300, 0), (330, .4), (MD, .4)]),
    (_mp("MoodEmbarrassed", "Puddle"), 18, [(0, 0), (360, 0), (390, 1), (MD, 1)]),
    (_mp("MoodEmbarrassed", "Puddle"), 16, [(0, .3), (360, .3), (400, 1), (MD, 1)]),
])
