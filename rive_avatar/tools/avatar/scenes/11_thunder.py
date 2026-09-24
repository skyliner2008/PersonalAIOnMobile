# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 11. THUNDER --------------------------------------------------------------
SCENE_STORIES["SceneThunder"] = (450, [
    (0, "Neutral", THUNDER, G_NONE, F_NONE),
    (36, "Neutral", THUNDER, G_DEEPBLUE, F_NONE),
    (96, "Confused", THUNDER, G_DEEPBLUE, F_NONE),
    (144, "Shock", THUNDER, G_DEEPBLUE, F_NONE),
    (168, "Dizzy", THUNDER, G_DEEPBLUE, F_NONE),
    (450, "Dizzy", THUNDER, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -20), (144, -20), (150, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -20), (144, -20), (150, 0), (450, 0)]),
    ("face", 13, [(0, 0), (167, 0)] + osc(168, 276, 3, -7, 7) + [(284, 0), (450, 0)]),
    ("face", 15, [(0, 0), (299, 0)] + osc(300, 440, 40, -.06, .06) + [(450, 0)]),
    ("face", 14, [(0, 0), (144, 0), (150, 18), (176, 8), (450, 8)]),
    (_sp("SceneThunder", "Cloud"), 13, [(0, -400), (30, -400), (80, 0), (450, 0)]),
    (_sp("SceneThunder", "Glow"), 18, [(0, 0), (96, 0), (100, 1), (106, 0), (112, 1), (118, 0), (140, 0), (144, 1), (160, 0), (450, 0)], "hold"),
    (_sp("SceneThunder", "Bolt"), 17, [(0, 0), (140, 0), (146, 1), (162, 1), (170, 0), (450, 0)]),
    (_sp("SceneThunder", "Flash"), 18, [(0, 0), (144, 0), (146, .9), (162, 0), (450, 0)]),
    (_sp("SceneThunder", "Sparks"), 18, [(0, 0), (160, 0), (166, 1), (280, 1), (290, 0), (450, 0)]),
    (_sp("SceneThunder", "Soot"), 18, [(0, 0), (296, 0), (312, 1), (450, 1)]),
])
