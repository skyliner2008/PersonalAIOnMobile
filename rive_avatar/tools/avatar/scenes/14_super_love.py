# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 14. SUPER LOVE -----------------------------------------------------------
_beat = [(0, 0), (126, 0), (140, 1)] + osc(150, 378, 15, 1, 1.15) + [(390, 1.4), (398, 0), (480, 0)]
SCENE_STORIES["SceneSuperLove"] = (480, [
    (0, "Neutral", LOVE, G_NONE, F_NONE),
    (36, "Shy", LOVE, G_NONE, F_NONE),
    (84, "Listening", LOVE, G_NONE, F_NONE),
    (132, "Love", LOVE, G_BOKEH, F_NONE),
    (384, "Happy", LOVE, G_BOKEH, F_NONE),
    (480, "Happy", LOVE, G_BOKEH, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (30, 0), (44, 14), (80, 14), (90, 0), (480, 0)]),
    ("blinkR", 13, [(0, 0), (30, 0), (44, 14), (80, 14), (90, 0), (480, 0)]),
    ("blinkL", 14, [(0, 0), (30, 0), (44, 16), (80, 16), (90, -16), (128, -16), (136, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, 16), (80, 16), (90, -16), (128, -16), (136, 0), (480, 0)]),
    ("blink", 16, [(0, 1), (131, 1)] + osc(132, 372, 15, 1, 1.08) + [(384, 1), (480, 1)]),
    ("blink", 17, [(0, 1), (131, 1)] + osc(132, 372, 15, 1, 1.12) + [(384, 1), (480, 1)]),
    ("face", 15, [(0, 0), (167, 0)] + osc(168, 372, 36, -.05, .05) + [(384, 0), (480, 0)]),
    (_sp("SceneLove", "Blush"), 18, [(0, 0), (30, 0), (42, 1), (132, 1), (150, 0), (480, 0)]),
    (_sp("SceneLove", "Arrow"), 13, [(0, -440), (84, -440), (112, 0), (480, 0)], "easeIn"),
    (_sp("SceneLove", "Arrow"), 18, [(0, 1), (378, 1), (396, 0), (480, 0)]),
] + _scale("SceneLove", "BigHeart", _beat) + [
    (_sp("SceneLove", "Small"), 18, [(0, 0), (160, 0), (176, 1), (380, 1), (396, 0), (480, 0)]),
] + _scale("SceneLove", "Burst", [(0, 0), (392, 0), (400, .6), (440, 1.4), (460, 0), (480, 0)]))
