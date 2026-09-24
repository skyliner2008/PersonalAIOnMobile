# Part of the avatar generator -- executed in order by tools/avatar/build.py
# into one shared namespace (see tools/avatar/README.md). Not importable on its own.

# 7. THUG LIFE -------------------------------------------------------------
SCENE_STORIES["SceneThug"] = (450, [
    (0, "Neutral", THUG, G_NONE, F_NONE),
    (30, "Smug", THUG, G_NONE, F_NONE),
    (450, "Smug", THUG, G_NONE, F_NONE),
], [
    ("blinkL", 14, [(0, 0), (30, 0), (44, -14), (140, -14), (150, 0), (450, 0)]),
    ("blinkR", 14, [(0, 0), (30, 0), (44, -14), (140, -14), (150, 0), (450, 0)]),
    ("face", 14, [(0, 0), (180, 0), (200, 12), (220, 0), (240, 12), (260, 0), (450, 0)]),
    ("face", 15, [(0, 0), (251, 0)] + osc(252, 444, 48, -.04, .04) + [(450, 0)]),
    (_sp("SceneThug", "Shades"), 14, [(0, -340), (72, -340), (144, -14), (152, 6), (162, 0), (450, 0)], "easeIn"),
    (_sp("SceneThug", "Chain"), 14, [(0, -560), (170, -560), (196, 0), (206, -10), (216, 0), (450, 0)]),
    (_sp("SceneThug", "Spot"), 18, [(0, 0), (250, 0), (272, 1), (450, 1)]),
])
