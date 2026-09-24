# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 8. RICH ------------------------------------------------------------------
SCENE_STORIES["SceneRich"] = (480, [
    (0, "Neutral", RICH, G_NONE, F_NONE),
    (36, "Confused", RICH, G_NONE, F_NONE),
    (84, "Shock", RICH, G_NONE, F_NONE),
    (144, "Money", RICH, G_GOLDGLOW, F_NONE),
    (192, "Money", RICH, G_GOLDGLOW, F_MONEY),
    (480, "Money", RICH, G_GOLDGLOW, F_MONEY),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (40, -18), (84, -18), (96, 0), (480, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (40, -18), (84, -18), (96, 0), (480, 0)]),
    ("blinkL", 13, [(0, 0), (191, 0)] + osc(192, 372, 30, -14, 14) + [(390, 0), (480, 0)]),
    ("blinkR", 13, [(0, 0), (191, 0)] + osc(192, 372, 30, -14, 14) + [(390, 0), (480, 0)]),
    ("face", 13, [(0, 0), (191, 0)] + osc(192, 372, 24, -10, 10) + [(390, 0), (480, 0)]),
    ("face", 14, [(0, 0), (144, 0), (154, -18), (166, 4), (176, 0), (480, 0)]),
    (_sp("SceneRich", "Coin0"), 14, [(0, -520), (36, -520), (70, 0), (78, -12), (86, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Coin1"), 14, [(0, -520), (84, -520), (116, 0), (124, -12), (132, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Coin2"), 14, [(0, -520), (96, -520), (128, 0), (136, -12), (144, 0), (480, 0)], "easeIn"),
    (_sp("SceneRich", "Gold"), 14, [(0, 160), (200, 160), (260, 0), (480, 0)]),
    (_sp("SceneRich", "Sparkles"), 16, [(0, 0), (386, 0), (396, 1), (480, 1)]),
])
