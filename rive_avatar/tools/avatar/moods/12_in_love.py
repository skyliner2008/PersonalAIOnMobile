# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 12. IN LOVE --------------------------------------------------------------
_beat = []
for _b in (0, 60, 120):
    _beat += [(_b, 1), (_b + 6, 1.14), (_b + 12, 1), (_b + 18, 1.1), (_b + 26, 1)]
MOOD_STORIES["MoodInLove"] = (MD, [
    (0, "Love", MOOD_LOVE, G_BOKEH, F_NONE),
    (MD, "Love", MOOD_LOVE, G_BOKEH, F_NONE),
], [
    ("face", 16, _beat + [(180, 1), (210, 1.35), (300, 1.35), (320, 1), (MD, 1)]),
    ("face", 17, _beat + [(180, 1), (210, 1.35), (300, 1.35), (320, 1), (MD, 1)]),
    ("face", 15, [(0, 0), (329, 0)] + osc(330, MD, 40, -.06, .06)),
    ("blinkL", 13, [(0, 0), (300, 0), (330, 28), (MD, 28)]),
    ("blinkR", 13, [(0, 0), (300, 0), (330, -28), (MD, -28)]),
    ("blinkL", 15, [(0, 0), (300, 0), (330, .3)] + osc(360, MD, 30, .18, .3)),
    ("blinkR", 15, [(0, 0), (300, 0), (330, -.3)] + osc(360, MD, 30, -.18, -.3)),
] + _eyes(14, [(0, 0), (180, 0), (210, -12), (300, -12), (320, 0), (MD, 0)]) +
    _eyes(17, [(0, 1), (210, 1)] + osc(220, 300, 30, 1.1, 1) + [(MD, 1)]) + [
    (_mp("MoodLove", "Bubbles"), 14, [(0, 0), (180, 0), (MD, -470)], "linear"),
    (_mp("MoodLove", "Bubbles"), 18, [(0, 0), (180, 0), (190, 1), (400, 1), (MD, 0)]),
    (_mp("MoodLove", "Blush"), 18, [(0, 0), (300, 0), (316, 1), (MD, 1)]),
])
