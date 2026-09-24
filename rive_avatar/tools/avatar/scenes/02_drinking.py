# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 2. DRINKING --------------------------------------------------------------
SCENE_STORIES["SceneDrinking"] = (450, [
    (0, "Neutral", DRINK, G_NONE, F_NONE),
    (78, "Excited", DRINK, G_NONE, F_NONE),
    (120, "Happy", DRINK, G_NONE, F_NONE),
    (300, "Wink", DRINK, G_NONE, F_NONE),
    (348, "Calm", DRINK, G_NONE, F_NONE),
    (450, "Calm", DRINK, G_NONE, F_NONE),
], [
    ("blinkL", 13, [(0, 0), (24, 0), (40, 18), (78, 18), (92, 0), (450, 0)]),
    ("blinkR", 13, [(0, 0), (24, 0), (40, 18), (78, 18), (92, 0), (450, 0)]),
    ("blinkL", 14, [(0, 0), (24, 0), (40, 16), (78, 16), (92, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (24, 0), (40, 16), (78, 16), (92, 0), (450, 0)]),
    ("face", 14, [(0, 0), (120, 0), (140, -12), (276, -12), (296, 0), (450, 0)]),
    ("face", 15, [(0, 0), (348, 0), (378, .05), (408, -.05), (438, .03), (450, 0)]),
    (_sp("SceneDrink", "Cup"), 13, [(0, 280), (30, 280), (66, 0), (110, -60), (276, -60), (300, 0), (348, 0), (420, 190), (450, 190)]),
    (_sp("SceneDrink", "Cup"), 14, [(0, 220), (30, 220), (66, 0), (110, -40), (276, -40), (300, 0), (348, 0), (420, 110), (450, 110)]),
    (_sp("SceneDrink", "Cup"), 15, [(0, 0), (78, 0), (110, -.45), (276, -.45), (300, 0), (450, 0)]),
] + _scale("SceneDrink", "Cup", [(0, 1.35), (450, 1.35)]) + [
    (_sp("SceneDrink", "Sparkles"), 18, [(0, 0), (296, 0), (304, 1), (348, 0), (450, 0)]),
])
